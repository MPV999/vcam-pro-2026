package com.vcampro.sdk

/**
 * Live delivery statistics for a source or for a [VirtualCameraSession].
 */
data class FrameStats(
    val deliveredFrames: Long = 0L,
    val droppedFrames: Long = 0L,
    val measuredFps: Float = 0f,
    val lastFrameTimestampUs: Long = 0L,
    val averageConsumerMillis: Float = 0f,
    val consumerErrors: Long = 0L
) {
    val totalFrames: Long get() = deliveredFrames + droppedFrames

    val dropRate: Float
        get() = if (totalFrames == 0L) 0f else droppedFrames.toFloat() / totalFrames.toFloat()
}
