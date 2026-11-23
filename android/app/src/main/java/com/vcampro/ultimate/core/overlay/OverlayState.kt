package com.vcampro.ultimate.core.overlay

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Position of the overlay layer that is composited over the background source.
 *
 * Offsets are fractions of the frame size, so the same values look identical on
 * a 720p and a 1080p pipeline. The floating bubble and the in-app sliders both
 * write here, and the compositor reads the latest value for every frame.
 */
data class OverlayParams(
    val enabled: Boolean = true,
    /** -0.45 (far left) .. 0.45 (far right) */
    val offsetX: Float = 0f,
    /** -0.45 (top) .. 0.45 (bottom) */
    val offsetY: Float = 0f,
    /** Layer width as a fraction of the frame width. */
    val scale: Float = 0.35f,
    /** 0.15 .. 1.0 */
    val alpha: Float = 1f
)

object OverlayState {

    private val _params = MutableStateFlow(OverlayParams())
    val params: StateFlow<OverlayParams> = _params.asStateFlow()

    private val _layer = MutableStateFlow<Bitmap?>(null)
    val layer: StateFlow<Bitmap?> = _layer.asStateFlow()

    fun setLayer(bitmap: Bitmap?) {
        _layer.value = bitmap
    }

    fun update(transform: (OverlayParams) -> OverlayParams) {
        _params.value = transform(_params.value)
    }

    /** Moves the layer; used by the bubble and by the arrow buttons. */
    fun nudge(dx: Float, dy: Float) = update {
        it.copy(
            offsetX = (it.offsetX + dx).coerceIn(-0.45f, 0.45f),
            offsetY = (it.offsetY + dy).coerceIn(-0.45f, 0.45f)
        )
    }

    fun changeScale(delta: Float) = update {
        it.copy(scale = (it.scale + delta).coerceIn(0.1f, 1f))
    }

    fun changeAlpha(delta: Float) = update {
        it.copy(alpha = (it.alpha + delta).coerceIn(0.15f, 1f))
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun reset() = update { OverlayParams(enabled = it.enabled) }
}
