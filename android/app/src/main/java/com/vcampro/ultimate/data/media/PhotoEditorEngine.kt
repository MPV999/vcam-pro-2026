package com.vcampro.ultimate.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A single, reversible editing step. The engine keeps the original bitmap and
 * re-renders the stack, so undo/redo never degrades quality.
 */
sealed interface PhotoOp {
    data class Rotate(val degrees: Int) : PhotoOp
    data class Flip(val horizontal: Boolean) : PhotoOp
    data class Crop(val left: Float, val top: Float, val right: Float, val bottom: Float) : PhotoOp
    data class Resize(val width: Int, val height: Int) : PhotoOp
    data class Adjust(
        val brightness: Float = 0f,
        val contrast: Float = 1f,
        val saturation: Float = 1f,
        val temperature: Float = 0f,
        val exposure: Float = 0f
    ) : PhotoOp

    data class Sharpen(val amount: Float = 0.6f) : PhotoOp
    data class Blur(val radius: Int = 4) : PhotoOp
    data class Filter(val id: String) : PhotoOp
    data class Watermark(val text: String, val alpha: Int = 170) : PhotoOp
}

/**
 * Bitmap pipeline for the Photo Studio. All operations run on IntArray pixels or
 * Canvas so the result is identical on every device and nothing is uploaded.
 */
class PhotoEditorEngine {

    suspend fun loadBitmap(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxDimension &&
                    bounds.outHeight / (sample * 2) >= maxDimension
                ) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@runCatching null
                val rotation = readExifRotation(context, uri)
                val oriented = if (rotation != 0) {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                        .also { decoded.recycle() }
                } else {
                    decoded
                }
                val scaled = if (oriented.width > maxDimension || oriented.height > maxDimension) {
                    val ratio = minOf(
                        maxDimension.toFloat() / oriented.width,
                        maxDimension.toFloat() / oriented.height
                    )
                    Bitmap.createScaledBitmap(
                        oriented,
                        (oriented.width * ratio).toInt().coerceAtLeast(1),
                        (oriented.height * ratio).toInt().coerceAtLeast(1),
                        true
                    ).also { oriented.recycle() }
                } else {
                    oriented
                }
                scaled
            }.getOrNull()
        }

    /** Renders the operation stack on top of [source]. Never mutates [source]. */
    fun render(source: Bitmap, operations: List<PhotoOp>): Bitmap {
        var current = source.copy(Bitmap.Config.ARGB_8888, true)
        operations.forEach { operation ->
            current = applyOperation(current, operation)
        }
        return current
    }

    private fun applyOperation(input: Bitmap, operation: PhotoOp): Bitmap = when (operation) {
        is PhotoOp.Rotate -> rotate(input, operation.degrees)
        is PhotoOp.Flip -> flip(input, operation.horizontal)
        is PhotoOp.Crop -> crop(input, operation)
        is PhotoOp.Resize -> Bitmap.createScaledBitmap(
            input,
            operation.width.coerceAtLeast(1),
            operation.height.coerceAtLeast(1),
            true
        )

        is PhotoOp.Adjust -> adjust(input, operation)
        is PhotoOp.Sharpen -> sharpen(input, operation.amount)
        is PhotoOp.Blur -> blur(input, operation.radius)
        is PhotoOp.Filter -> applyMatrix(input, filterMatrix(operation.id))
        is PhotoOp.Watermark -> watermark(input, operation)
    }

    fun rotate(input: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return input
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(input, 0, 0, input.width, input.height, matrix, true)
    }

    fun flip(input: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) postScale(-1f, 1f) else postScale(1f, -1f)
        }
        return Bitmap.createBitmap(input, 0, 0, input.width, input.height, matrix, true)
    }

    private fun crop(input: Bitmap, operation: PhotoOp.Crop): Bitmap {
        val left = (operation.left.coerceIn(0f, 0.98f) * input.width).toInt()
        val top = (operation.top.coerceIn(0f, 0.98f) * input.height).toInt()
        val right = (operation.right.coerceIn(operation.left + 0.02f, 1f) * input.width).toInt()
        val bottom = (operation.bottom.coerceIn(operation.top + 0.02f, 1f) * input.height).toInt()
        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)
        return Bitmap.createBitmap(input, left, top, width, height)
    }

    private fun adjust(input: Bitmap, operation: PhotoOp.Adjust): Bitmap {
        val matrix = ColorMatrix()
        matrix.postConcat(ColorMatrix().apply { setSaturation(operation.saturation) })

        val contrast = operation.contrast.coerceIn(0.2f, 3f)
        val translate = (-0.5f * contrast + 0.5f) * 255f
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        val brightnessShift = (operation.brightness + operation.exposure) * 255f
        if (brightnessShift != 0f) {
            matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, brightnessShift,
                        0f, 1f, 0f, 0f, brightnessShift,
                        0f, 0f, 1f, 0f, brightnessShift,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        if (operation.temperature != 0f) {
            val warm = operation.temperature.coerceIn(-1f, 1f)
            matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f + warm * 0.25f, 0f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f - warm * 0.25f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        return applyMatrix(input, matrix)
    }

    fun applyMatrix(input: Bitmap, matrix: ColorMatrix): Bitmap {
        val output = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(input, 0f, 0f, paint)
        return output
    }

    fun filterMatrix(id: String): ColorMatrix = when (id) {
        "vivid" -> ColorMatrix().apply {
            setSaturation(1.5f)
            postConcat(contrastMatrix(1.12f))
        }

        "mono" -> ColorMatrix().apply { setSaturation(0f) }
        "warm" -> ColorMatrix(
            floatArrayOf(
                1.12f, 0f, 0f, 0f, 10f,
                0f, 1.02f, 0f, 0f, 4f,
                0f, 0f, 0.92f, 0f, -6f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        "cool" -> ColorMatrix(
            floatArrayOf(
                0.92f, 0f, 0f, 0f, -6f,
                0f, 1.02f, 0f, 0f, 2f,
                0f, 0f, 1.14f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        "vintage" -> ColorMatrix(
            floatArrayOf(
                0.9f, 0.05f, 0.05f, 0f, 12f,
                0.05f, 0.85f, 0.05f, 0f, 8f,
                0.05f, 0.05f, 0.75f, 0f, 4f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        "cinema" -> ColorMatrix().apply {
            setSaturation(0.85f)
            postConcat(contrastMatrix(1.25f))
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, -8f,
                        0f, 1f, 0f, 0f, -2f,
                        0f, 0f, 1.08f, 0f, 8f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        else -> ColorMatrix()
    }

    private fun contrastMatrix(value: Float): ColorMatrix {
        val translate = (-0.5f * value + 0.5f) * 255f
        return ColorMatrix(
            floatArrayOf(
                value, 0f, 0f, 0f, translate,
                0f, value, 0f, 0f, translate,
                0f, 0f, value, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    fun sharpen(input: Bitmap, amount: Float): Bitmap {
        if (amount <= 0f) return input
        val width = input.width
        val height = input.height
        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = IntArray(pixels.size)
        val center = 1f + 4f * amount
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    output[index] = pixels[index]
                    continue
                }
                var r = 0f
                var g = 0f
                var b = 0f
                val neighbours = intArrayOf(
                    pixels[index - 1],
                    pixels[index + 1],
                    pixels[index - width],
                    pixels[index + width]
                )
                neighbours.forEach { pixel ->
                    r -= ((pixel shr 16) and 0xFF) * amount
                    g -= ((pixel shr 8) and 0xFF) * amount
                    b -= (pixel and 0xFF) * amount
                }
                val centerPixel = pixels[index]
                r += ((centerPixel shr 16) and 0xFF) * center
                g += ((centerPixel shr 8) and 0xFF) * center
                b += (centerPixel and 0xFF) * center
                output[index] = (0xFF shl 24) or
                    (r.toInt().coerceIn(0, 255) shl 16) or
                    (g.toInt().coerceIn(0, 255) shl 8) or
                    b.toInt().coerceIn(0, 255)
            }
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    fun blur(input: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return input
        val width = input.width
        val height = input.height
        var pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)
        repeat(2) {
            pixels = boxBlur(pixels, width, height, radius)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val window = radius * 2 + 1
        val horizontal = IntArray(pixels.size)
        for (y in 0 until height) {
            val base = y * width
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val pixel = pixels[base + i.coerceIn(0, width - 1)]
                a += (pixel ushr 24) and 0xFF
                r += (pixel shr 16) and 0xFF
                g += (pixel shr 8) and 0xFF
                b += pixel and 0xFF
            }
            for (x in 0 until width) {
                horizontal[base + x] = ((a / window) shl 24) or
                    ((r / window) shl 16) or
                    ((g / window) shl 8) or
                    (b / window)
                val outPixel = pixels[base + (x - radius).coerceIn(0, width - 1)]
                val inPixel = pixels[base + (x + radius + 1).coerceIn(0, width - 1)]
                a += ((inPixel ushr 24) and 0xFF) - ((outPixel ushr 24) and 0xFF)
                r += ((inPixel shr 16) and 0xFF) - ((outPixel shr 16) and 0xFF)
                g += ((inPixel shr 8) and 0xFF) - ((outPixel shr 8) and 0xFF)
                b += (inPixel and 0xFF) - (outPixel and 0xFF)
            }
        }
        val vertical = IntArray(pixels.size)
        for (x in 0 until width) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val pixel = horizontal[i.coerceIn(0, height - 1) * width + x]
                a += (pixel ushr 24) and 0xFF
                r += (pixel shr 16) and 0xFF
                g += (pixel shr 8) and 0xFF
                b += pixel and 0xFF
            }
            for (y in 0 until height) {
                vertical[y * width + x] = ((a / window) shl 24) or
                    ((r / window) shl 16) or
                    ((g / window) shl 8) or
                    (b / window)
                val outPixel = horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
                val inPixel = horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                a += ((inPixel ushr 24) and 0xFF) - ((outPixel ushr 24) and 0xFF)
                r += ((inPixel shr 16) and 0xFF) - ((outPixel shr 16) and 0xFF)
                g += ((inPixel shr 8) and 0xFF) - ((outPixel shr 8) and 0xFF)
                b += (inPixel and 0xFF) - (outPixel and 0xFF)
            }
        }
        return vertical
    }

    private fun watermark(input: Bitmap, operation: PhotoOp.Watermark): Bitmap {
        val output = input.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val textSize = (output.width * 0.035f).coerceAtLeast(18f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = operation.alpha.coerceIn(0, 255)
            this.textSize = textSize
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }
        val textWidth = paint.measureText(operation.text)
        val x = output.width - textWidth - textSize * 0.8f
        val y = output.height - textSize
        canvas.drawText(operation.text, x.coerceAtLeast(textSize), y, paint)
        return output
    }

    /** Automatic level stretch plus a light sharpen. */
    fun enhance(input: Bitmap): Bitmap {
        val width = input.width
        val height = input.height
        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)
        val histogram = IntArray(256)
        pixels.forEach { pixel ->
            val luma = (((pixel shr 16) and 0xFF) * 77 + ((pixel shr 8) and 0xFF) * 150 + (pixel and 0xFF) * 29) shr 8
            histogram[luma.coerceIn(0, 255)] += 1
        }
        val total = pixels.size
        var low = 0
        var high = 255
        var accumulated = 0
        for (i in 0..255) {
            accumulated += histogram[i]
            if (accumulated > total * 0.01f) {
                low = i
                break
            }
        }
        accumulated = 0
        for (i in 255 downTo 0) {
            accumulated += histogram[i]
            if (accumulated > total * 0.01f) {
                high = i
                break
            }
        }
        if (high <= low + 4) return sharpen(input, 0.35f)
        val scale = 255f / (high - low)
        val lookup = IntArray(256) { value ->
            (((value - low) * scale).toInt()).coerceIn(0, 255)
        }
        val stretched = IntArray(pixels.size) { index ->
            val pixel = pixels[index]
            val r = lookup[(pixel shr 16) and 0xFF]
            val g = lookup[(pixel shr 8) and 0xFF]
            val b = lookup[pixel and 0xFF]
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val stretchedBitmap = Bitmap.createBitmap(stretched, width, height, Bitmap.Config.ARGB_8888)
        return sharpen(stretchedBitmap, 0.35f)
    }

    suspend fun export(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        quality: Int
    ): Uri? = withContext(Dispatchers.IO) {
        GalleryWriter.saveImage(context, bitmap, displayName, quality)
    }

    suspend fun metadata(context: Context, uri: Uri): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<Pair<String, String>>()
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    fun add(label: String, value: String?) {
                        if (!value.isNullOrBlank()) result.add(label to value)
                    }
                    add("Model", exif.getAttribute(ExifInterface.TAG_MODEL))
                    add("Make", exif.getAttribute(ExifInterface.TAG_MAKE))
                    add("DateTime", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                    add("Exposure", exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME))
                    add("ISO", exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY))
                    add("F-number", exif.getAttribute(ExifInterface.TAG_F_NUMBER))
                    add("Focal length", exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH))
                    add("Flash", exif.getAttribute(ExifInterface.TAG_FLASH))
                    add("Orientation", exif.getAttribute(ExifInterface.TAG_ORIENTATION))
                    add("Size", "${exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)}x${exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)}")
                }
            }
            val cursor = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.MediaStore.MediaColumns.SIZE, android.provider.MediaStore.MediaColumns.MIME_TYPE),
                    null,
                    null,
                    null
                )
            }.getOrNull()
            cursor?.use {
                if (it.moveToFirst()) {
                    result.add("MIME" to (it.getString(1) ?: "-"))
                    result.add("Bytes" to (it.getLong(0).toString()))
                }
            }
            result
        }

    private fun readExifRotation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)
}
