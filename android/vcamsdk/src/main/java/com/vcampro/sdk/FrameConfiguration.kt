package com.vcampro.sdk

/**
 * Immutable description of the frames a source produces.
 *
 * The engine never silently changes a configuration: sources either honour it
 * (scaling / letterboxing as needed) or fail with a clear error, so an SDK
 * integrator can rely on [width] / [height] / [fps] for the lifetime of a
 * session.
 */
data class FrameConfiguration(
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT,
    val fps: Int = DEFAULT_FPS,
    val format: FrameFormat = FrameFormat.NV21,
    val rotationDegrees: Int = 0,
    val mirrorHorizontal: Boolean = false
) {
    init {
        require(width > 0 && height > 0) { "Kích thước khung hình không hợp lệ: ${width}x$height" }
        require(width % 2 == 0 && height % 2 == 0) { "Kích thước YUV 4:2:0 phải là số chẵn: ${width}x$height" }
        require(fps in MIN_FPS..MAX_FPS) { "FPS phải nằm trong khoảng $MIN_FPS..$MAX_FPS" }
        require(rotationDegrees % 90 == 0) { "Góc xoay phải là bội số của 90" }
    }

    val aspectRatio: Float get() = width.toFloat() / height.toFloat()

    val frameIntervalUs: Long get() = 1_000_000L / fps

    /** Bytes a single frame of this configuration occupies. */
    val bufferSize: Int
        get() = when (format) {
            FrameFormat.YUV_420_888, FrameFormat.NV21 -> width * height * 3 / 2
            FrameFormat.RGBA -> width * height * 4
        }

    val isPortrait: Boolean get() = height > width

    /**
     * Returns a configuration that fits inside [maxWidth] x [maxHeight] keeping
     * the aspect ratio, with both dimensions forced to even numbers.
     */
    fun scaledTo(maxWidth: Int, maxHeight: Int): FrameConfiguration {
        if (maxWidth <= 0 || maxHeight <= 0) return this
        if (width <= maxWidth && height <= maxHeight) return this
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val targetWidth = (width * ratio).toInt().coerceAtLeast(2)
        val targetHeight = (height * ratio).toInt().coerceAtLeast(2)
        return copy(width = targetWidth.toEven(), height = targetHeight.toEven())
    }

    fun withSize(width: Int, height: Int): FrameConfiguration = copy(width = width, height = height)

    fun withFps(fps: Int): FrameConfiguration = copy(fps = fps.coerceIn(MIN_FPS, MAX_FPS))

    fun withFormat(format: FrameFormat): FrameConfiguration = copy(format = format)

    fun withRotation(degrees: Int): FrameConfiguration = copy(rotationDegrees = normalizeRotation(degrees))

    companion object {
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        const val DEFAULT_FPS = 30
        const val MIN_FPS = 1
        const val MAX_FPS = 120

        fun portrait720p(fps: Int = DEFAULT_FPS): FrameConfiguration =
            FrameConfiguration(width = 720, height = 1280, fps = fps)

        fun landscape720p(fps: Int = DEFAULT_FPS): FrameConfiguration =
            FrameConfiguration(width = 1280, height = 720, fps = fps)

        fun landscape1080p(fps: Int = DEFAULT_FPS): FrameConfiguration =
            FrameConfiguration(width = 1920, height = 1080, fps = fps)

        fun normalizeRotation(degrees: Int): Int {
            val normalized = degrees % 360
            return if (normalized < 0) normalized + 360 else normalized
        }

        private fun Int.toEven(): Int = if (this % 2 == 0) this else this - 1
    }
}
