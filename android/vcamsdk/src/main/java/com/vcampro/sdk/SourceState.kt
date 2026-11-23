package com.vcampro.sdk

/**
 * Lifecycle of a [CameraSource].
 */
enum class SourceState {
    IDLE,
    PREPARING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
    RELEASED
}
