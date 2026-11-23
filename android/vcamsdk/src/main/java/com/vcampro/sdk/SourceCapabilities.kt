package com.vcampro.sdk

/**
 * What a source can actually do. Integrators should drive their UI from this
 * object instead of assuming every source supports every operation.
 */
data class SourceCapabilities(
    val canSeek: Boolean = false,
    val canLoop: Boolean = false,
    val canChangeSpeed: Boolean = false,
    val hasDuration: Boolean = false,
    val isLive: Boolean = false,
    val durationUs: Long = 0L,
    val supportedResolutions: List<Resolution> = emptyList()
) {
    data class Resolution(val width: Int, val height: Int) {
        override fun toString(): String = "${width}x$height"
    }
}
