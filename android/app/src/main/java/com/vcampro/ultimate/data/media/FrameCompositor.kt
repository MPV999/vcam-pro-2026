package com.vcampro.ultimate.data.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.vcampro.sdk.ImageConverter
import com.vcampro.sdk.VideoFrame
import com.vcampro.ultimate.core.overlay.OverlayParams

/**
 * Draws the overlay layer (a logo, a product shot, any still image) onto a frame
 * of the pipeline, at the position the floating bubble asks for.
 *
 * The result is a normal NV21 frame, so it can be delivered by the SDK and
 * recorded like any other frame. Nothing here touches another application's
 * camera: it only edits frames this app produces.
 */
class FrameCompositor {

    fun compose(frame: VideoFrame, layer: Bitmap?, params: OverlayParams): ByteArray {
        if (layer == null || !params.enabled || layer.isRecycled) return frame.data
        return runCatching {
            val base = ImageConverter.nv21ToBitmap(frame.data, frame.width, frame.height)
            drawLayer(base, layer, params)
            val nv21 = ImageConverter.bitmapToNv21(base, frame.width, frame.height)
            base.recycle()
            nv21
        }.getOrDefault(frame.data)
    }

    private fun drawLayer(base: Bitmap, layer: Bitmap, params: OverlayParams) {
        val canvas = Canvas(base)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (params.alpha.coerceIn(0f, 1f) * 255f).toInt()
        }

        val layerWidth = (base.width * params.scale.coerceIn(0.05f, 1f)).coerceAtLeast(16f)
        val ratio = layer.height.toFloat() / layer.width.toFloat().coerceAtLeast(1f)
        val layerHeight = (layerWidth * ratio).coerceAtLeast(16f)

        val centerX = base.width / 2f + params.offsetX.coerceIn(-0.5f, 0.5f) * base.width
        val centerY = base.height / 2f + params.offsetY.coerceIn(-0.5f, 0.5f) * base.height

        val destination = RectF(
            centerX - layerWidth / 2f,
            centerY - layerHeight / 2f,
            centerX + layerWidth / 2f,
            centerY + layerHeight / 2f
        )
        canvas.drawBitmap(layer, null, destination, paint)
    }
}
