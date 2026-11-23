package com.vcampro.sdk

/**
 * Pixel layout of a delivered [VideoFrame].
 */
enum class FrameFormat {
    /** Planar YUV 4:2:0, three planes (Y, U, V), 8 bit per component. */
    YUV_420_888,

    /** Semi planar YUV 4:2:0: full size Y plane followed by interleaved VU. */
    NV21,

    /** Packed RGBA_8888, 4 bytes per pixel, row major. */
    RGBA
}
