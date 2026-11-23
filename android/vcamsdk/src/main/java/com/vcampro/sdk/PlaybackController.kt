package com.vcampro.sdk

/**
 * Playback-style control exposed by sources that have a timeline
 * (video files, image sequences with a duration).
 */
interface PlaybackController {
    val isPlaying: Boolean
    val positionUs: Long
    val durationUs: Long
    val isLooping: Boolean

    fun play()
    fun pause()
    fun seekTo(positionUs: Long)
    fun setLooping(looping: Boolean)

    /** Playback rate; 1.0 is real time. Values are clamped by the source. */
    fun setSpeed(speed: Float)
}
