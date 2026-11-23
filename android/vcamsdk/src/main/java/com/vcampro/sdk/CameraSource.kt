package com.vcampro.sdk

import kotlinx.coroutines.flow.StateFlow

/**
 * A virtual camera input: a physical camera, a video file, an image, or a
 * frame buffer produced by the host application.
 *
 * A source decodes and paces frames and hands them to exactly one
 * [FrameConsumer]. Fan-out to several consumers is the job of
 * [VirtualCameraSession].
 */
interface CameraSource {

    /** Stable identifier, unique per source instance. */
    val id: String

    /** Human readable name, shown in the studio UI. */
    val displayName: String

    val capabilities: SourceCapabilities

    val state: StateFlow<SourceState>

    val stats: StateFlow<FrameStats>

    /** Last error message, or null. Cleared by the next successful prepare. */
    val lastError: StateFlow<String?>

    /** The consumer receives frames on a background thread. */
    fun setConsumer(consumer: FrameConsumer?)

    /**
     * Opens the source and allocates its decoder. Frames are not produced until
     * [start] is called.
     */
    fun prepare(configuration: FrameConfiguration = FrameConfiguration())

    fun start()

    fun pause()

    fun resume()

    /** Stops producing frames and rewinds to the beginning of the timeline. */
    fun stop()

    /** Frees every native resource. The source cannot be reused afterwards. */
    fun release()

    fun seekTo(positionUs: Long) = Unit

    fun setLooping(looping: Boolean) = Unit

    fun setSpeed(speed: Float) = Unit
}
