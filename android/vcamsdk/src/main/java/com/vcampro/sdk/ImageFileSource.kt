package com.vcampro.sdk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri

/**
 * Emits a still image at a fixed rate, as a virtual camera feed.
 */
class ImageFileSource(
    private val context: Context,
    private val uri: Uri,
    override val displayName: String
) : BaseCameraSource() {

    override val id: String = "image:$uri"

    @Volatile
    private var frameBuffer: ByteArray? = null

    @Volatile
    private var playing = false

    @Volatile
    private var released = false

    private var worker: Thread? = null

    override val capabilities: SourceCapabilities
        get() = SourceCapabilities(
            canSeek = false,
            canLoop = true,
            canChangeSpeed = true,
            hasDuration = false,
            isLive = false
        )

    override fun prepare(configuration: FrameConfiguration) {
        if (released) return
        clearError()
        setState(SourceState.PREPARING)
        this.configuration = configuration
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                reportError("Không đọc được thông tin ảnh")
                return
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, configuration.width, configuration.height)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (decoded == null) {
                reportError("Không giải mã được ảnh")
                return
            }
            val rotated = rotateIfNeeded(decoded, configuration.rotationDegrees)
            frameBuffer = ImageConverter.bitmapToNv21(rotated, configuration.width, configuration.height)
            if (rotated !== decoded) decoded.recycle()
            rotated.recycle()
            setState(SourceState.READY)
        } catch (t: Throwable) {
            reportError("Không mở được ảnh", t)
        }
    }

    override fun start() {
        if (released || frameBuffer == null) return
        playing = true
        setState(SourceState.PLAYING)
        if (worker == null) {
            worker = Thread({ emitLoop() }, "vcam-image-source").also {
                it.start()
            }
        }
    }

    override fun pause() {
        playing = false
        if (currentState() == SourceState.PLAYING) setState(SourceState.PAUSED)
    }

    override fun resume() {
        if (released) return
        playing = true
        setState(SourceState.PLAYING)
        if (worker == null) start()
    }

    override fun stop() {
        playing = false
        if (currentState() != SourceState.ERROR) setState(SourceState.READY)
    }

    override fun release() {
        released = true
        playing = false
        worker?.interrupt()
        worker = null
        frameBuffer = null
        setState(SourceState.RELEASED)
    }

    private fun emitLoop() {
        while (!released) {
            try {
                if (!playing) {
                    Thread.sleep(10)
                    continue
                }
                val buffer = frameBuffer
                if (buffer == null) {
                    Thread.sleep(10)
                    continue
                }
                emitFrame(buffer)
                Thread.sleep(configuration.frameIntervalUs / 1_000L)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (t: Throwable) {
                reportError("Lỗi phát ảnh", t)
                break
            }
        }
    }

    private fun sampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }

    private fun rotateIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
        val rotation = FrameConfiguration.normalizeRotation(degrees)
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
