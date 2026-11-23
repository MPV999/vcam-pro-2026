package com.vcampro.sdk

/**
 * A single decoded (and optionally processed) frame handed to a [FrameConsumer].
 *
 * [data] is owned by the frame. Consumers must not modify it in place; the
 * engine reuses no buffers between frames so a consumer may keep the frame as
 * long as it needs it.
 */
class VideoFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val format: FrameFormat,
    /** Monotonic timestamp in microseconds taken when the frame was produced. */
    val timestampUs: Long,
    /** Increasing counter per source, useful for dropping diagnostics. */
    val sequence: Long
) {
    val size: Int get() = data.size

    /** Bytes per pixel row of the luma plane. */
    val lumaRowStride: Int get() = width

    fun toConfiguration(): FrameConfiguration = FrameConfiguration(
        width = width,
        height = height,
        format = format
    )

    override fun toString(): String =
        "VideoFrame(${width}x$height, $format, seq=$sequence, ts=$timestampUs, ${data.size}B)"
}
