package com.vcampro.sdk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared plumbing for every source: state machine, frame statistics and the
 * single-consumer contract.
 */
abstract class BaseCameraSource : CameraSource {

    private val _state = MutableStateFlow(SourceState.IDLE)
    override val state: StateFlow<SourceState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(FrameStats())
    override val stats: StateFlow<FrameStats> = _stats.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val delivered = AtomicLong(0L)
    private val dropped = AtomicLong(0L)
    private val consumerErrors = AtomicLong(0L)
    private val sequence = AtomicLong(0L)

    @Volatile
    protected var consumer: FrameConsumer? = null

    @Volatile
    protected var configuration: FrameConfiguration = FrameConfiguration()

    @Volatile
    private var windowStartNanos: Long = 0L

    @Volatile
    private var windowFrames: Int = 0

    @Volatile
    private var measuredFps: Float = 0f

    @Volatile
    private var lastConsumerMillis: Float = 0f

    @Volatile
    private var lastTimestampUs: Long = 0L

    override fun setConsumer(consumer: FrameConsumer?) {
        this.consumer = consumer
    }

    protected fun setState(newState: SourceState) {
        _state.value = newState
    }

    protected fun currentState(): SourceState = _state.value

    protected fun clearError() {
        _lastError.value = null
    }

    protected fun reportError(message: String, cause: Throwable? = null) {
        _lastError.value = if (cause == null) {
            message
        } else {
            "$message (${cause.javaClass.simpleName}: ${cause.message})"
        }
        _state.value = SourceState.ERROR
    }

    protected fun nextSequence(): Long = sequence.incrementAndGet()

    /**
     * Publishes a frame to the consumer. Returns false when the frame was
     * dropped because no consumer is attached or the consumer threw.
     */
    protected fun emitFrame(
        data: ByteArray,
        timestampUs: Long = System.nanoTime() / 1_000L,
        format: FrameFormat = configuration.format
    ): Boolean {
        val target = consumer
        if (target == null) {
            dropped.incrementAndGet()
            publishStats(timestampUs)
            return false
        }
        val frame = VideoFrame(
            data = data,
            width = configuration.width,
            height = configuration.height,
            format = format,
            timestampUs = timestampUs,
            sequence = nextSequence()
        )
        val started = System.nanoTime()
        try {
            target.onFrame(frame)
        } catch (t: Throwable) {
            dropped.incrementAndGet()
            consumerErrors.incrementAndGet()
            publishStats(timestampUs)
            return false
        }
        lastConsumerMillis = (System.nanoTime() - started) / 1_000_000f
        delivered.incrementAndGet()
        publishStats(timestampUs)
        return true
    }

    protected fun emitBitmapFrame(rgba: IntArray, width: Int, height: Int) {
        val nv21 = YuvConverter.argbToNv21(rgba, width, height)
        emitFrame(nv21, format = FrameFormat.NV21)
    }

    private fun publishStats(timestampUs: Long) {
        lastTimestampUs = timestampUs
        windowFrames += 1
        val now = System.nanoTime()
        if (windowStartNanos == 0L) {
            windowStartNanos = now
        }
        val elapsed = now - windowStartNanos
        if (elapsed >= 1_000_000_000L) {
            measuredFps = windowFrames * 1_000_000_000f / elapsed
            windowFrames = 0
            windowStartNanos = now
        }
        _stats.value = FrameStats(
            deliveredFrames = delivered.get(),
            droppedFrames = dropped.get(),
            measuredFps = measuredFps,
            lastFrameTimestampUs = lastTimestampUs,
            averageConsumerMillis = lastConsumerMillis,
            consumerErrors = consumerErrors.get()
        )
    }
}
