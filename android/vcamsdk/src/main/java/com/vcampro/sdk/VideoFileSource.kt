package com.vcampro.sdk

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

/**
 * Decodes a video file into YUV frames with real-time pacing, timestamp
 * management, looping, seeking and rate control.
 *
 * Only one decoded frame is alive at a time; nothing is buffered in memory, so
 * a 4 GB source plays with the same footprint as a 4 MB one.
 */
class VideoFileSource(
    private val context: Context,
    private val uri: Uri,
    override val displayName: String
) : BaseCameraSource() {

    override val id: String = "video:$uri"

    private val extractor = MediaExtractor()
    private var decoder: MediaCodec? = null
    private var trackIndex = -1
    private var sourceWidth = 0
    private var sourceHeight = 0

    @Volatile
    private var duration = 0L

    @Volatile
    private var position = 0L

    @Volatile
    private var playing = false

    @Volatile
    private var looping = true

    @Volatile
    private var speed = 1f

    @Volatile
    private var seekRequestUs = -1L

    @Volatile
    private var workerRunning = false

    @Volatile
    private var released = false

    private var worker: Thread? = null

    override val capabilities: SourceCapabilities
        get() = SourceCapabilities(
            canSeek = true,
            canLoop = true,
            canChangeSpeed = true,
            hasDuration = duration > 0L,
            isLive = false,
            durationUs = duration
        )

    override fun prepare(configuration: FrameConfiguration) {
        if (released) return
        clearError()
        setState(SourceState.PREPARING)
        try {
            this.configuration = configuration
            extractor.setDataSource(context, uri, null)
            trackIndex = findVideoTrack(extractor)
            if (trackIndex < 0) {
                reportError("Tệp đã chọn không chứa luồng video")
                return
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            sourceWidth = format.optInt(MediaFormat.KEY_WIDTH)
            sourceHeight = format.optInt(MediaFormat.KEY_HEIGHT)
            duration = format.optLong(MediaFormat.KEY_DURATION)
            val mime = format.optString(MediaFormat.KEY_MIME) ?: "video/avc"
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            decoder = codec
            setState(SourceState.READY)
        } catch (t: Throwable) {
            reportError("Không mở được video", t)
        }
    }

    override fun start() {
        if (released || decoder == null) return
        playing = true
        setState(SourceState.PLAYING)
        if (worker == null) {
            workerRunning = true
            worker = Thread({ decodeLoop() }, "vcam-video-source").also {
                it.priority = Thread.NORM_PRIORITY + 1
                it.start()
            }
        }
    }

    override fun pause() {
        if (!playing) return
        playing = false
        if (currentState() == SourceState.PLAYING) setState(SourceState.PAUSED)
    }

    override fun resume() {
        if (released || decoder == null) return
        if (currentState() == SourceState.ENDED) {
            seekRequestUs = 0L
        }
        playing = true
        setState(SourceState.PLAYING)
        if (worker == null) start()
    }

    override fun stop() {
        playing = false
        seekRequestUs = 0L
        if (currentState() != SourceState.ERROR) setState(SourceState.READY)
    }

    override fun seekTo(positionUs: Long) {
        val target = positionUs.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        seekRequestUs = target
        position = target
    }

    override fun setLooping(looping: Boolean) {
        this.looping = looping
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.25f, 4f)
    }

    val isLooping: Boolean get() = looping

    val positionUs: Long get() = position

    val durationUs: Long get() = duration

    val isPlaying: Boolean get() = playing

    val sourceResolution: Pair<Int, Int> get() = sourceWidth to sourceHeight

    override fun release() {
        released = true
        playing = false
        workerRunning = false
        worker?.let { thread ->
            thread.interrupt()
            try {
                thread.join(600)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        worker = null
        try {
            decoder?.stop()
        } catch (ignored: Throwable) {
        }
        try {
            decoder?.release()
        } catch (ignored: Throwable) {
        }
        decoder = null
        try {
            extractor.release()
        } catch (ignored: Throwable) {
        }
        setState(SourceState.RELEASED)
    }

    private fun performSeek(targetUs: Long) {
        try {
            extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            decoder?.flush()
            position = targetUs
        } catch (t: Throwable) {
            reportError("Không tua được video", t)
        }
    }

    private fun decodeLoop() {
        val codec = decoder
        if (codec == null) {
            workerRunning = false
            return
        }
        val info = MediaCodec.BufferInfo()
        var clockOriginNanos = System.nanoTime()
        var presentationOriginUs = position
        var lastPtsUs = -1L
        var inputDone = false

        while (workerRunning && !released) {
            try {
                if (!playing) {
                    Thread.sleep(8)
                    continue
                }
                val requested = seekRequestUs
                if (requested >= 0L) {
                    seekRequestUs = -1L
                    performSeek(requested)
                    clockOriginNanos = System.nanoTime()
                    presentationOriginUs = requested
                    lastPtsUs = -1L
                    inputDone = false
                    continue
                }

                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(5_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        if (buffer == null) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, 0)
                        } else {
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(
                                    inIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inIndex,
                                    0,
                                    size,
                                    if (sampleTime < 0L) 0L else sampleTime,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 5_000)
                if (outIndex >= 0) {
                    val endOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    if (info.size > 0) {
                        val ptsUs = info.presentationTimeUs
                        val image = try {
                            codec.getOutputImage(outIndex)
                        } catch (t: Throwable) {
                            null
                        }
                        if (image != null) {
                            val nv21 = ImageConverter.imageToNv21(image)
                            image.close()
                            val adjustedPts = if (ptsUs > lastPtsUs) {
                                ptsUs
                            } else {
                                lastPtsUs + configuration.frameIntervalUs
                            }
                            lastPtsUs = adjustedPts
                            val factor = speed.coerceIn(0.25f, 4f)
                            val targetUs = ((adjustedPts - presentationOriginUs) / factor).toLong()
                            var remainingUs = targetUs - (System.nanoTime() - clockOriginNanos) / 1_000L
                            while (remainingUs > 1_500L && playing && workerRunning && !released) {
                                Thread.sleep((remainingUs / 1_000L).coerceIn(1L, 20L))
                                remainingUs = targetUs - (System.nanoTime() - clockOriginNanos) / 1_000L
                            }
                            position = adjustedPts
                            emitFrame(nv21)
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (endOfStream) {
                        if (looping && workerRunning && !released) {
                            performSeek(0L)
                            clockOriginNanos = System.nanoTime()
                            presentationOriginUs = 0L
                            lastPtsUs = -1L
                            inputDone = false
                        } else {
                            playing = false
                            setState(SourceState.ENDED)
                        }
                    }
                }
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (t: Throwable) {
                reportError("Lỗi giải mã video", t)
                break
            }
        }
        workerRunning = false
    }

    private companion object {
        fun findVideoTrack(extractor: MediaExtractor): Int {
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.optString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) return index
            }
            return -1
        }

        fun MediaFormat.optInt(key: String): Int =
            if (containsKey(key)) getInteger(key) else 0

        fun MediaFormat.optLong(key: String): Long =
            if (containsKey(key)) getLong(key) else 0L

        fun MediaFormat.optString(key: String): String? =
            if (containsKey(key)) getString(key) else null
    }
}
