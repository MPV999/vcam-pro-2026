package com.vcampro.ultimate.data.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * On-device video pipeline.
 *
 * - Trim and merge use MediaExtractor + MediaMuxer and copy compressed samples,
 *   so they are near instant and lossless.
 * - Resize, rotate, speed change and compression use Media3 Transformer
 *   (MediaCodec under the hood) with real progress reporting.
 *
 * Nothing is ever loaded fully into RAM: samples stream through a fixed 2 MB
 * direct buffer.
 */
class VideoEditorEngine(private val context: Context) {

    data class VideoInfo(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val rotationDegrees: Int,
        val mimeType: String,
        val bitrate: Int
    ) {
        val durationLabel: String
            get() {
                val totalSeconds = durationMs / 1000
                return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
            }
    }

    data class ExportOptions(
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L,
        val targetWidth: Int = 0,
        val targetHeight: Int = 0,
        val rotationDegrees: Int = 0,
        val speed: Float = 1f,
        val muteAudio: Boolean = false,
        val videoBitrate: Int = 0
    )

    @Volatile
    private var activeTransformer: Transformer? = null

    /** Cancels the export in progress, if any. Must be called from the main thread. */
    fun cancel() {
        runCatching { activeTransformer?.cancel() }
        activeTransformer = null
    }

    suspend fun probe(uri: Uri): VideoInfo? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            VideoInfo(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0,
                rotationDegrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0,
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    ?: "video/mp4",
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull() ?: 0
            )
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Lossless, fast trim: copies the compressed samples between two timestamps. */
    suspend fun trimFast(
        source: Uri,
        startMs: Long,
        endMs: Long,
        displayName: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            require(endMs > startMs) { "Thời điểm kết thúc phải sau thời điểm bắt đầu" }
            val outputFile = File(context.cacheDir, "vcam_trim_${System.currentTimeMillis()}.mp4")
            val extractor = MediaExtractor()
            var muxer: MediaMuxer? = null
            try {
                extractor.setDataSource(context, source, null)
                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val trackMap = HashMap<Int, Int>()
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                    extractor.selectTrack(index)
                    trackMap[index] = muxer.addTrack(format)
                }
                if (trackMap.isEmpty()) throw IllegalStateException("Tệp không có luồng video/audio")
                muxer.start()

                val startUs = startMs * 1000L
                val endUs = endMs * 1000L
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val sampleTime = extractor.sampleTime
                    if (sampleTime > endUs) break
                    val muxTrack = trackMap[extractor.sampleTrackIndex]
                    if (muxTrack != null && sampleTime >= startUs - 200_000L) {
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = (sampleTime - startUs).coerceAtLeast(0L)
                        info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }
                        muxer.writeSampleData(muxTrack, buffer, info)
                    }
                    extractor.advance()
                }
            } finally {
                runCatching { muxer?.stop() }
                runCatching { muxer?.release() }
                runCatching { extractor.release() }
            }
            publish(outputFile, displayName)
        }
    }

    /** Concatenates compatible videos by appending their compressed samples. */
    suspend fun merge(sources: List<Uri>, displayName: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(sources.size >= 2) { "Cần ít nhất hai video để ghép" }
                val outputFile = File(context.cacheDir, "vcam_merge_${System.currentTimeMillis()}.mp4")
                val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024)
                val info = MediaCodec.BufferInfo()
                var muxerStarted = false
                var offsetUs = 0L
                var videoTrack = -1
                var audioTrack = -1
                try {
                    for (uri in sources) {
                        val extractor = MediaExtractor()
                        try {
                            extractor.setDataSource(context, uri, null)
                            val trackMap = HashMap<Int, Int>()
                            var fileDurationUs = 0L
                            for (index in 0 until extractor.trackCount) {
                                val format = extractor.getTrackFormat(index)
                                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                                val isVideo = mime.startsWith("video/")
                                val isAudio = mime.startsWith("audio/")
                                if (!isVideo && !isAudio) continue
                                if (!muxerStarted) {
                                    val added = muxer.addTrack(format)
                                    if (isVideo && videoTrack < 0) videoTrack = added
                                    if (isAudio && audioTrack < 0) audioTrack = added
                                    trackMap[index] = added
                                } else {
                                    val target = if (isVideo) videoTrack else audioTrack
                                    if (target < 0) continue
                                    trackMap[index] = target
                                }
                                extractor.selectTrack(index)
                                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                                    fileDurationUs = maxOf(
                                        fileDurationUs,
                                        format.getLong(MediaFormat.KEY_DURATION)
                                    )
                                }
                            }
                            if (trackMap.isEmpty()) continue
                            if (!muxerStarted) {
                                if (videoTrack < 0) throw IllegalStateException("Không tìm thấy luồng video")
                                muxer.start()
                                muxerStarted = true
                            }
                            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                            var lastPts = 0L
                            while (true) {
                                val size = extractor.readSampleData(buffer, 0)
                                if (size < 0) break
                                val muxTrack = trackMap[extractor.sampleTrackIndex]
                                val pts = extractor.sampleTime
                                if (muxTrack != null && muxTrack >= 0) {
                                    info.offset = 0
                                    info.size = size
                                    info.presentationTimeUs = pts + offsetUs
                                    info.flags =
                                        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                                        } else {
                                            0
                                        }
                                    muxer.writeSampleData(muxTrack, buffer, info)
                                }
                                if (pts > lastPts) lastPts = pts
                                extractor.advance()
                            }
                            offsetUs += if (fileDurationUs > 0L) fileDurationUs else lastPts
                        } finally {
                            runCatching { extractor.release() }
                        }
                    }
                    if (!muxerStarted) throw IllegalStateException("Không có dữ liệu để ghép")
                } finally {
                    runCatching { muxer.stop() }
                    runCatching { muxer.release() }
                }
                publish(outputFile, displayName)
            }
        }

    suspend fun extractFrame(uri: Uri, atMs: Long, displayName: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val bitmap = retriever.getFrameAtTime(
                        atMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: throw IllegalStateException("Không trích xuất được khung hình")
                    val saved = GalleryWriter.saveImage(context, bitmap, displayName, 95)
                        ?: throw IllegalStateException("Không ghi được khung hình")
                    bitmap.recycle()
                    saved
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }

    /**
     * Re-encodes the video with the requested effects using Media3 Transformer.
     * [onProgress] receives 0..1 and is invoked on the main thread.
     */
    suspend fun export(
        source: Uri,
        options: ExportOptions,
        displayName: String,
        onProgress: (Float) -> Unit = {}
    ): Result<Uri> = withContext(Dispatchers.Main) {
        val outputFile = File(context.cacheDir, "vcam_export_${System.currentTimeMillis()}.mp4")
        val deferred = CompletableDeferred<Result<File>>()
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (!deferred.isCompleted) deferred.complete(Result.success(outputFile))
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                if (!deferred.isCompleted) deferred.complete(Result.failure(exportException))
            }
        }
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()
        activeTransformer = transformer

        val mediaItemBuilder = MediaItem.Builder().setUri(source)
        if (options.trimEndMs > options.trimStartMs) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(options.trimStartMs)
                    .setEndPositionMs(options.trimEndMs)
                    .build()
            )
        }

        val videoEffects = mutableListOf<Effect>()
        if (options.targetWidth > 0 && options.targetHeight > 0) {
            videoEffects.add(
                Presentation.createForWidthAndHeight(
                    options.targetWidth,
                    options.targetHeight,
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
            )
        }
        if (options.rotationDegrees != 0) {
            videoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(options.rotationDegrees.toFloat())
                    .build()
            )
        }
        if (options.speed > 0f && kotlin.math.abs(options.speed - 1f) > 0.01f) {
            videoEffects.add(SpeedChangeEffect(options.speed.coerceIn(0.25f, 4f)))
        }

        val editedMediaItem = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setEffects(Effects(emptyList(), videoEffects))
            .setRemoveAudio(options.muteAudio)
            .build()

        val progressJob = launch {
            val holder = ProgressHolder()
            while (true) {
                val state = runCatching { transformer.getProgress(holder) }
                    .getOrDefault(Transformer.PROGRESS_STATE_UNAVAILABLE)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                }
                delay(250)
            }
        }

        try {
            transformer.start(editedMediaItem, outputFile.absolutePath)
        } catch (t: Throwable) {
            progressJob.cancel()
            activeTransformer = null
            outputFile.delete()
            return@withContext Result.failure(t)
        }

        val result = deferred.await()
        progressJob.cancel()
        activeTransformer = null
        result.fold(
            onSuccess = { file ->
                onProgress(1f)
                val savedUri = withContext(Dispatchers.IO) {
                    GalleryWriter.saveVideo(context, file, displayName)
                }
                file.delete()
                if (savedUri != null) {
                    Result.success(savedUri)
                } else {
                    Result.failure(IllegalStateException("Không ghi được video vào thư viện"))
                }
            },
            onFailure = { error ->
                outputFile.delete()
                Result.failure(error)
            }
        )
    }

    private fun publish(file: File, displayName: String): Uri {
        val uri = GalleryWriter.saveVideo(context, file, displayName)
            ?: throw IllegalStateException("Không ghi được video vào thư viện")
        file.delete()
        return uri
    }
}
