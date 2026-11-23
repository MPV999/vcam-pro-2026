package com.vcampro.sdk

import android.graphics.Bitmap

/**
 * A source fed by the host application: the output of the photo / video studio
 * pipeline, a rendered AR scene, a test pattern, anything that can produce a
 * bitmap or an NV21 buffer.
 *
 * The latest submitted frame is repeated at the configured rate, so a host can
 * submit frames at whatever rate it manages without stalling the engine.
 */
class ProcessedFrameSource(
    override val displayName: String = "Nguồn khung hình đã xử lý"
) : BaseCameraSource() {

    override val id: String = "processed:${System.identityHashCode(this)}"

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
            isLive = true
        )

    override fun prepare(configuration: FrameConfiguration) {
        if (released) return
        clearError()
        this.configuration = configuration
        setState(SourceState.READY)
    }

    /** Submits an already converted NV21 buffer. */
    fun submitNv21(buffer: ByteArray, width: Int = configuration.width, height: Int = configuration.height) {
        configuration = configuration.withSize(width, height)
        frameBuffer = buffer
        if (!playing) start()
    }

    /** Converts and submits a bitmap. */
    fun submitBitmap(bitmap: Bitmap, width: Int = configuration.width, height: Int = configuration.height) {
        configuration = configuration.withSize(width, height)
        frameBuffer = ImageConverter.bitmapToNv21(bitmap, width, height)
        if (!playing) start()
    }

    override fun start() {
        if (released) return
        playing = true
        setState(SourceState.PLAYING)
        if (worker == null) {
            worker = Thread({ emitLoop() }, "vcam-processed-source").also { it.start() }
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
                val buffer = frameBuffer
                if (!playing || buffer == null) {
                    Thread.sleep(8)
                    continue
                }
                emitFrame(buffer)
                Thread.sleep(configuration.frameIntervalUs / 1_000L)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (t: Throwable) {
                reportError("Lỗi phát khung hình đã xử lý", t)
                break
            }
        }
    }
}
