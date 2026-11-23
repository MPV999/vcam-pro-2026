package com.vcampro.ultimate.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileTest {

    private fun profile(
        ramMb: Long = 6144L,
        totalStorageMb: Long = 128_000L,
        freeStorageMb: Long = 64_000L
    ) = DeviceProfile(
        model = "Pixel",
        manufacturer = "Google",
        androidRelease = "14",
        sdkInt = 34,
        cpuArchitectures = listOf("arm64-v8a"),
        cpuCores = 8,
        totalRamMb = ramMb,
        availableRamMb = ramMb / 2,
        totalStorageMb = totalStorageMb,
        freeStorageMb = freeStorageMb,
        cameraIds = listOf("0", "1"),
        hasFrontCamera = true,
        hasBackCamera = true,
        hasFlash = true,
        maxPhotoWidth = 4000,
        maxPhotoHeight = 3000,
        supportedPhotoSizes = listOf("4000x3000"),
        supportedVideoSizes = listOf("1920x1080"),
        supportedFpsRanges = listOf("30-30"),
        hardwareCodecs = listOf("AVC (c2.android.avc.encoder)"),
        hardwareAcceleration = true,
        recommendedMode = PerformanceMode.QUALITY,
        recommendedWidth = 1920,
        recommendedHeight = 1080,
        recommendedFps = 60,
        supportsHdr = true
    )

    @Test
    fun ramLabelUsesGigabytes() {
        assertEquals("6.0 GB", profile(ramMb = 6144L).ramLabel)
    }

    @Test
    fun tierReflectsMemory() {
        assertEquals("Flagship", profile(ramMb = 8192L).tierLabel)
        assertEquals("High", profile(ramMb = 4096L).tierLabel)
        assertEquals("Mid", profile(ramMb = 3072L).tierLabel)
        assertEquals("Entry", profile(ramMb = 2048L).tierLabel)
    }

    @Test
    fun recommendedResolutionLabelIsComplete() {
        val label = profile().recommendedResolutionLabel
        assertTrue(label.contains("1920x1080"))
        assertTrue(label.contains("60fps"))
    }

    @Test
    fun performanceModeFallsBackToBalanced() {
        assertEquals(PerformanceMode.BALANCED, PerformanceMode.fromKey("unknown"))
        assertEquals(PerformanceMode.QUALITY, PerformanceMode.fromKey("quality"))
    }
}
