package com.vcampro.sdk

import android.graphics.Bitmap
import android.media.Image

/**
 * Android-side conversions: camera / codec [Image] objects and [Bitmap]s to the
 * engine's NV21 buffer.
 */
object ImageConverter {

    /**
     * Copies a YUV_420_888 [Image] (as produced by Camera2, CameraX ImageAnalysis
     * and MediaCodec) into a tightly packed NV21 buffer, honouring row and pixel
     * strides so no plane data is lost.
     */
    fun imageToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val out = ByteArray(YuvConverter.nv21BufferSize(width, height))
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val yRowStride = planes[0].rowStride
        val yPixelStride = planes[0].pixelStride
        val uRowStride = planes[1].rowStride
        val uPixelStride = planes[1].pixelStride
        val vRowStride = planes[2].rowStride
        val vPixelStride = planes[2].pixelStride

        var offset = 0
        for (row in 0 until height) {
            var index = row * yRowStride
            for (col in 0 until width) {
                out[offset++] = yBuffer.get(index)
                index += yPixelStride
            }
        }

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            var uIndex = row * uRowStride
            var vIndex = row * vRowStride
            for (col in 0 until chromaWidth) {
                out[offset++] = vBuffer.get(vIndex)
                out[offset++] = uBuffer.get(uIndex)
                uIndex += uPixelStride
                vIndex += vPixelStride
            }
        }
        return out
    }

    /** Copies a [Bitmap] into NV21, scaling first when the size differs. */
    fun bitmapToNv21(bitmap: Bitmap, width: Int = bitmap.width, height: Int = bitmap.height): ByteArray {
        val scaled = if (bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()
        return YuvConverter.argbToNv21(pixels, width, height)
    }

    /** Renders an NV21 buffer to a mutable [Bitmap]. */
    fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap {
        val pixels = YuvConverter.nv21ToArgb(nv21, width, height)
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
