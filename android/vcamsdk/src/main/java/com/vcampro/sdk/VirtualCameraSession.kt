package com.vcampro.sdk

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Session state.
 */
enum class SessionState {
    IDLE,
    CONFIGURED,
    RUNNING,
    PAUSED,
    STOPPED,
    RELEASED,
    ERROR
}

/**
 * The Virtual Camera Engine session.
 *
 * A session owns one active [CameraSource] and fans its frames out to any number
 * of [FrameConsumer]s on a single delivery thread. Backpressure policy: the
 * engine keeps only the newest frame, so a slow consumer loses frames instead of
 * growing a queue or blocking the decoder. Dropped frames are reported in
 * [stats].
 */
class VirtualCameraSession internal constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(FrameStats())
    val stats: StateFlow<FrameStats> = _stats.asStateFlow()

    private val _activeSource = MutableStateFlow<CameraSource?>(null)
    val activeSource: StateFlow<CameraSource?> = _activeSource.asStateFlow()

    private val consumers = ConcurrentHashMap<String, FrameConsumer>()
    private val latestFrame = AtomicReference<VideoFrame?>(null)
    private val delivered = AtomicLong(0L)
    private val dropped = AtomicLong(0L)
    private val consumerErrors = AtomicLong(0L)
    private val droppedSinceWindow = AtomicLong(0L)

    @Volatile
    private var configuration: FrameConfiguration = FrameConfiguration()

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    @Volatile
    private var released = false

    @Volatile
    private var lastConsumerMillis = 0f

    @Volatile
    private var measuredFps = 0f

    private var dispatchThread: Thread? = null

    /** The current configuration, applied to the next prepared source. */
    fun configuration(): FrameConfiguration = configuration

    fun setConfiguration(newConfiguration: FrameConfiguration) {
        configuration = newConfiguration
        _activeSource.value?.let { source ->
            val state = source.state.value
            if (state == SourceState.PLAYING || state == SourceState.PAUSED) {
                source.prepare(newConfiguration)
                if (state == SourceState.PLAYING) source.start()
            }
        }
        if (_state.value == SessionState.IDLE) _state.value = SessionState.CONFIGURED
    }

    /** Replaces the active source, releasing the previous one. */
    fun setSource(source: CameraSource?) {
        val previous = _activeSource.value
        if (previous === source) return
        previous?.setConsumer(null)
        previous?.release()
        _activeSource.value = source
        if (source == null) {
            _state.value = SessionState.CONFIGURED
            return
        }
        source.setConsumer { frame -> latestFrame.set(frame) }
        source.prepare(configuration)
        if (_state.value == SessionState.IDLE) _state.value = SessionState.CONFIGURED
        if (running) source.start()
    }

    fun addConsumer(id: String, consumer: FrameConsumer) {
        consumers[id] = consumer
    }

    fun removeConsumer(id: String) {
        consumers.remove(id)
    }

    fun consumerIds(): List<String> = consumers.keys.toList()

    fun start() {
        if (released) return
        val source = _activeSource.value
        if (source == null) {
            _state.value = SessionState.ERROR
            return
        }
        running = true
        paused = false
        ensureDispatchThread()
        source.start()
        _state.value = SessionState.RUNNING
    }

    fun pause() {
        paused = true
        _activeSource.value?.pause()
        if (_state.value == SessionState.RUNNING) _state.value = SessionState.PAUSED
    }

    fun resume() {
        if (released) return
        paused = false
        ensureDispatchThread()
        _activeSource.value?.resume()
        if (running) _state.value = SessionState.RUNNING
    }

    fun stop() {
        running = false
        paused = false
        _activeSource.value?.stop()
        _state.value = SessionState.STOPPED
    }

    /** Convenience passthroughs for the active source timeline. */
    fun seekTo(positionUs: Long) = _activeSource.value?.seekTo(positionUs) ?: Unit

    fun setLooping(looping: Boolean) = _activeSource.value?.setLooping(looping) ?: Unit

    fun setSpeed(speed: Float) = _activeSource.value?.setSpeed(speed) ?: Unit

    fun release() {
        released = true
        running = false
        consumers.clear()
        _activeSource.value?.setConsumer(null)
        _activeSource.value?.release()
        _activeSource.value = null
        dispatchThread?.interrupt()
        dispatchThread = null
        latestFrame.set(null)
        _state.value = SessionState.RELEASED
    }

    private fun ensureDispatchThread() {
        if (dispatchThread != null) return
        dispatchThread = Thread({ dispatchLoop() }, "vcam-session-dispatch").also { it.start() }
    }

    private fun dispatchLoop() {
        var windowStart = System.nanoTime()
        var windowFrames = 0
        while (!released) {
            try {
                if (paused) {
                    Thread.sleep(10)
                    continue
                }
                val frame = latestFrame.getAndSet(null)
                if (frame == null) {
                    Thread.sleep(2)
                    continue
                }
                val started = System.nanoTime()
                var failures = 0
                for ((id, consumer) in consumers) {
                    try {
                        consumer.onFrame(frame)
                    } catch (t: Throwable) {
                        failures += 1
                        consumerErrors.incrementAndGet()
                        if (consumerErrors.get() % 16L == 0L) {
                            consumers.remove(id, consumer)
                        }
                    }
                }
                lastConsumerMillis = (System.nanoTime() - started) / 1_000_000f
                if (failures == 0) delivered.incrementAndGet() else dropped.incrementAndGet()
                dropped.addAndGet(droppedSinceWindow.getAndSet(0L))
                windowFrames += 1
                val elapsed = System.nanoTime() - windowStart
                if (elapsed >= 1_000_000_000L) {
                    measuredFps = windowFrames * 1_000_000_000f / elapsed
                    windowFrames = 0
                    windowStart = System.nanoTime()
                }
                _stats.value = FrameStats(
                    deliveredFrames = delivered.get(),
                    droppedFrames = dropped.get(),
                    measuredFps = measuredFps,
                    lastFrameTimestampUs = frame.timestampUs,
                    averageConsumerMillis = lastConsumerMillis,
                    consumerErrors = consumerErrors.get()
                )
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (t: Throwable) {
                _state.value = SessionState.ERROR
            }
        }
    }
}
