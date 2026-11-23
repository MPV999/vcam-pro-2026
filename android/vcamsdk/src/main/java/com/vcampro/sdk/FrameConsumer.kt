package com.vcampro.sdk

/**
 * Receives frames from the engine.
 *
 * `onFrame` is invoked on the engine's delivery thread, never on the main
 * thread. Implementations should return quickly: a slow consumer causes the
 * engine to drop frames (the newest frame always wins), it never blocks the
 * producer or grows an unbounded queue.
 */
fun interface FrameConsumer {
    fun onFrame(frame: VideoFrame)
}
