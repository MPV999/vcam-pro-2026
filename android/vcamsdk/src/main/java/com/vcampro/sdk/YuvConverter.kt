package com.vcampro.sdk

/**
 * Pure Kotlin YUV / ARGB maths. No Android framework types are used here so the
 * whole conversion layer is unit-testable on the JVM.
 */
object YuvConverter {

    fun nv21BufferSize(width: Int, height: Int): Int = width * height * 3 / 2

    /**
     * Converts NV21 (Y + interleaved VU) to packed ARGB pixels.
     */
    fun nv21ToArgb(nv21: ByteArray, width: Int, height: Int, out: IntArray = IntArray(width * height)): IntArray {
        val frameSize = width * height
        require(nv21.size >= frameSize + frameSize / 2) {
            "Bộ đệm NV21 quá nhỏ: ${nv21.size} < ${frameSize + frameSize / 2}"
        }
        require(out.size >= frameSize) { "Bộ đệm ARGB quá nhỏ" }
        var yIndex = 0
        var uvIndex = frameSize
        for (row in 0 until height) {
            val uvRow = uvIndex + (row / 2) * width
            for (col in 0 until width) {
                val y = (nv21[yIndex].toInt() and 0xFF)
                val uvCol = (col / 2) * 2
                val v = (nv21[uvRow + uvCol].toInt() and 0xFF) - 128
                val u = (nv21[uvRow + uvCol + 1].toInt() and 0xFF) - 128
                val c = (y - 16).coerceAtLeast(0)
                val r = ((298 * c + 409 * v + 128) shr 8).coerceIn(0, 255)
                val g = ((298 * c - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
                val b = ((298 * c + 516 * u + 128) shr 8).coerceIn(0, 255)
                out[yIndex] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                yIndex += 1
            }
        }
        return out
    }

    /**
     * Converts packed ARGB pixels to NV21.
     */
    fun argbToNv21(argb: IntArray, width: Int, height: Int): ByteArray {
        val frameSize = width * height
        val out = ByteArray(frameSize + frameSize / 2)
        var yIndex = 0
        for (pixel in argb) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
            out[yIndex] = y.coerceIn(0, 255).toByte()
            yIndex += 1
        }
        var uvIndex = frameSize
        var row = 0
        while (row < height) {
            var col = 0
            while (col < width) {
                var uSum = 0
                var vSum = 0
                var samples = 0
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val x = (col + dx).coerceAtMost(width - 1)
                        val y = (row + dy).coerceAtMost(height - 1)
                        val pixel = argb[y * width + x]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        uSum += ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                        vSum += ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                        samples += 1
                    }
                }
                val u = (uSum / samples).coerceIn(0, 255)
                val v = (vSum / samples).coerceIn(0, 255)
                out[uvIndex] = v.toByte()
                out[uvIndex + 1] = u.toByte()
                uvIndex += 2
                col += 2
            }
            row += 2
        }
        return out
    }

    /**
     * Nearest-neighbour NV21 scaling. Cheap enough for 1080p on low end devices
     * and predictable in memory usage (one output buffer only).
     */
    fun scaleNv21(src: ByteArray, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): ByteArray {
        if (srcWidth == dstWidth && srcHeight == dstHeight) return src
        val dst = ByteArray(nv21BufferSize(dstWidth, dstHeight))
        val srcFrame = srcWidth * srcHeight
        val dstFrame = dstWidth * dstHeight
        for (row in 0 until dstHeight) {
            val srcRow = row * srcHeight / dstHeight
            val srcBase = srcRow * srcWidth
            val dstBase = row * dstWidth
            for (col in 0 until dstWidth) {
                dst[dstBase + col] = src[srcBase + col * srcWidth / dstWidth]
            }
        }
        val srcUvBase = srcFrame
        val dstUvBase = dstFrame
        val srcChromaWidth = srcWidth / 2
        val dstChromaWidth = dstWidth / 2
        val dstChromaHeight = dstHeight / 2
        for (row in 0 until dstChromaHeight) {
            val srcRow = row * (srcHeight / 2) / dstChromaHeight
            val srcBase = srcUvBase + srcRow * srcWidth
            val dstBase = dstUvBase + row * dstWidth
            for (col in 0 until dstChromaWidth) {
                val srcCol = col * srcChromaWidth / dstChromaWidth
                dst[dstBase + col * 2] = src[srcBase + srcCol * 2]
                dst[dstBase + col * 2 + 1] = src[srcBase + srcCol * 2 + 1]
            }
        }
        return dst
    }

    /**
     * Rotates NV21 by 0, 90, 180 or 270 degrees clockwise.
     */
    fun rotateNv21(src: ByteArray, width: Int, height: Int, degrees: Int): ByteArray {
        val rotation = FrameConfiguration.normalizeRotation(degrees)
        if (rotation == 0) return src
        val dstWidth = if (rotation == 180) width else height
        val dstHeight = if (rotation == 180) height else width
        val dst = ByteArray(nv21BufferSize(dstWidth, dstHeight))
        val srcFrame = width * height
        val dstFrame = dstWidth * dstHeight

        for (dstRow in 0 until dstHeight) {
            for (dstCol in 0 until dstWidth) {
                val srcRow: Int
                val srcCol: Int
                when (rotation) {
                    90 -> {
                        srcCol = dstRow
                        srcRow = height - 1 - dstCol
                    }
                    180 -> {
                        srcCol = width - 1 - dstCol
                        srcRow = height - 1 - dstRow
                    }
                    else -> {
                        srcCol = width - 1 - dstRow
                        srcRow = dstCol
                    }
                }
                dst[dstRow * dstWidth + dstCol] = src[srcRow * width + srcCol]
            }
        }

        val srcChromaWidth = width / 2
        val srcChromaHeight = height / 2
        val dstChromaWidth = dstWidth / 2
        val dstChromaHeight = dstHeight / 2
        for (dstRow in 0 until dstChromaHeight) {
            for (dstCol in 0 until dstChromaWidth) {
                val srcRow: Int
                val srcCol: Int
                when (rotation) {
                    90 -> {
                        srcCol = dstRow
                        srcRow = srcChromaHeight - 1 - dstCol
                    }
                    180 -> {
                        srcCol = srcChromaWidth - 1 - dstCol
                        srcRow = srcChromaHeight - 1 - dstRow
                    }
                    else -> {
                        srcCol = srcChromaWidth - 1 - dstRow
                        srcRow = dstCol
                    }
                }
                val srcOffset = srcFrame + srcRow * width + srcCol * 2
                val dstOffset = dstFrame + dstRow * dstWidth + dstCol * 2
                dst[dstOffset] = src[srcOffset]
                dst[dstOffset + 1] = src[srcOffset + 1]
            }
        }
        return dst
    }

    /** Mirrors NV21 horizontally (selfie preview). */
    fun mirrorNv21(src: ByteArray, width: Int, height: Int): ByteArray {
        val dst = ByteArray(src.size)
        val frame = width * height
        for (row in 0 until height) {
            val base = row * width
            for (col in 0 until width) {
                dst[base + col] = src[base + (width - 1 - col)]
            }
        }
        for (row in 0 until height / 2) {
            val base = frame + row * width
            for (col in 0 until width / 2) {
                dst[base + col * 2] = src[base + (width / 2 - 1 - col) * 2]
                dst[base + col * 2 + 1] = src[base + (width / 2 - 1 - col) * 2 + 1]
            }
        }
        return dst
    }

    /** Luma only downsample, used by the quality analyser. */
    fun lumaHistogram(nv21: ByteArray, width: Int, height: Int): IntArray {
        val histogram = IntArray(256)
        val frame = width * height
        val step = (width * height / 65536).coerceAtLeast(1)
        var index = 0
        while (index < frame) {
            histogram[nv21[index].toInt() and 0xFF] += 1
            index += step
        }
        return histogram
    }
}
