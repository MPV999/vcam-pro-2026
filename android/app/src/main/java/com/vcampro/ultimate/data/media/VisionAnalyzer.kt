package com.vcampro.ultimate.data.media

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.abs
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class FaceBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val smiling: Boolean,
    val leftEyeOpen: Boolean,
    val rightEyeOpen: Boolean,
    val landmarkCount: Int
)

data class VisionResult(
    val width: Int,
    val height: Int,
    val faceCount: Int,
    val faces: List<FaceBox>,
    val sharpnessScore: Float,
    val lightingScore: Float,
    val qualityScore: Float,
    val averageLuma: Float
)

/**
 * On-device image analysis: ML Kit face detection plus a pure Kotlin quality
 * pass (variance of Laplacian for sharpness, histogram spread for lighting).
 *
 * The face model is bundled in the APK, so analysis works offline and no image
 * ever leaves the device. It does not identify people.
 */
class VisionAnalyzer {

    private val detector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.06f)
                .build()
        )
    }

    suspend fun analyze(bitmap: Bitmap): VisionResult {
        val working = downscale(bitmap, 1280)
        val faces = runCatching { detectFaces(working) }.getOrDefault(emptyList())
        val metrics = measure(working)
        if (working !== bitmap) working.recycle()

        val quality = buildQualityScore(
            sharpness = metrics.first,
            lighting = metrics.second,
            faceCount = faces.size
        )

        return VisionResult(
            width = bitmap.width,
            height = bitmap.height,
            faceCount = faces.size,
            faces = faces.map { face ->
                val bounds = face.boundingBox
                FaceBox(
                    left = bounds.left.toFloat(),
                    top = bounds.top.toFloat(),
                    right = bounds.right.toFloat(),
                    bottom = bounds.bottom.toFloat(),
                    smiling = (face.smilingProbability ?: 0f) > 0.5f,
                    leftEyeOpen = (face.leftEyeOpenProbability ?: 1f) > 0.4f,
                    rightEyeOpen = (face.rightEyeOpenProbability ?: 1f) > 0.4f,
                    landmarkCount = face.allLandmarks.size
                )
            },
            sharpnessScore = metrics.first,
            lightingScore = metrics.second,
            qualityScore = quality,
            averageLuma = metrics.third
        )
    }

    fun close() {
        runCatching { detector.close() }
    }

    private suspend fun detectFaces(bitmap: Bitmap): List<Face> = suspendCancellableCoroutine { continuation ->
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces -> if (continuation.isActive) continuation.resume(faces) }
            .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    }

    /** Returns (sharpness 0..100, lighting 0..100, average luma). */
    private fun measure(bitmap: Bitmap): Triple<Float, Float, Float> {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return Triple(0f, 0f, 0f)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var lumaSum = 0L
        var lumaSquared = 0L
        var laplacianSum = 0L
        var samples = 0
        val step = (width * height / 400_000).coerceAtLeast(1)

        var index = 0
        while (index < pixels.size) {
            val pixel = pixels[index]
            val luma = (((pixel shr 16) and 0xFF) * 77 +
                ((pixel shr 8) and 0xFF) * 150 +
                (pixel and 0xFF) * 29) shr 8
            lumaSum += luma
            lumaSquared += luma.toLong() * luma
            samples += 1
            index += step
        }
        if (samples == 0) return Triple(0f, 0f, 0f)
        val mean = lumaSum.toFloat() / samples
        val variance = (lumaSquared.toFloat() / samples) - mean * mean
        val deviation = kotlin.math.sqrt(abs(variance))

        for (y in 1 until height - 1 step 2) {
            for (x in 1 until width - 1 step 2) {
                val center = lumaAt(pixels, width, x, y)
                val laplacian = (4 * center -
                    lumaAt(pixels, width, x - 1, y) -
                    lumaAt(pixels, width, x + 1, y) -
                    lumaAt(pixels, width, x, y - 1) -
                    lumaAt(pixels, width, x, y + 1))
                laplacianSum += abs(laplacian)
            }
        }
        val laplacianSamples = ((width - 2) / 2).coerceAtLeast(1) * ((height - 2) / 2).coerceAtLeast(1)
        val sharpness = (laplacianSum.toFloat() / laplacianSamples / 12f * 100f).coerceIn(0f, 100f)

        // A well exposed photo has a mean luma around 118 with decent spread.
        val exposurePenalty = (abs(mean - 118f) / 118f).coerceIn(0f, 1f)
        val contrastBonus = (deviation / 70f).coerceIn(0f, 1f)
        val lighting = ((1f - exposurePenalty) * 0.7f + contrastBonus * 0.3f) * 100f

        return Triple(sharpness, lighting.coerceIn(0f, 100f), mean)
    }

    private fun lumaAt(pixels: IntArray, width: Int, x: Int, y: Int): Int {
        val pixel = pixels[y * width + x]
        return (((pixel shr 16) and 0xFF) * 77 +
            ((pixel shr 8) and 0xFF) * 150 +
            (pixel and 0xFF) * 29) shr 8
    }

    private fun buildQualityScore(sharpness: Float, lighting: Float, faceCount: Int): Float {
        val base = sharpness * 0.55f + lighting * 0.45f
        val faceBonus = if (faceCount > 0) 4f else 0f
        return (base + faceBonus).coerceIn(0f, 100f)
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
