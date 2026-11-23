package com.vcampro.sdk

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameConfigurationTest {

    @Test
    fun defaultConfigurationIsValid() {
        val config = FrameConfiguration()
        assertEquals(1280, config.width)
        assertEquals(720, config.height)
        assertEquals(30, config.fps)
        assertEquals(FrameFormat.NV21, config.format)
        assertEquals(1280 * 720 * 3 / 2, config.bufferSize)
    }

    @Test
    fun scaledToKeepsAspectRatioAndEvenDimensions() {
        val config = FrameConfiguration(width = 1920, height = 1080, fps = 30)
        val scaled = config.scaledTo(640, 640)
        assertEquals(640, scaled.width)
        assertEquals(360, scaled.height)
        assertEquals(0, scaled.width % 2)
        assertEquals(0, scaled.height % 2)
    }

    @Test
    fun scaledToLeavesSmallConfigUntouched() {
        val config = FrameConfiguration(width = 640, height = 480)
        assertEquals(config, config.scaledTo(1920, 1080))
    }

    @Test
    fun rotationIsNormalised() {
        assertEquals(270, FrameConfiguration.normalizeRotation(-90))
        assertEquals(90, FrameConfiguration.normalizeRotation(450))
        assertEquals(0, FrameConfiguration.normalizeRotation(360))
    }

    @Test(expected = IllegalArgumentException::class)
    fun oddDimensionsAreRejected() {
        FrameConfiguration(width = 641, height = 481)
    }

    @Test
    fun rgbaBufferSize() {
        val config = FrameConfiguration(width = 128, height = 64, format = FrameFormat.RGBA)
        assertEquals(128 * 64 * 4, config.bufferSize)
    }

    @Test
    fun frameStatsDropRate() {
        val stats = FrameStats(deliveredFrames = 90, droppedFrames = 10)
        assertEquals(100, stats.totalFrames)
        assertEquals(0.1f, stats.dropRate, 0.0001f)
        assertTrue(FrameStats().dropRate == 0f)
    }

    @Test
    fun argbToNv21RoundTripKeepsLuma() {
        val width = 4
        val height = 4
        val pixels = IntArray(width * height) { index ->
            val gray = if (index % 2 == 0) 200 else 40
            (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        val nv21 = YuvConverter.argbToNv21(pixels, width, height)
        assertEquals(YuvConverter.nv21BufferSize(width, height), nv21.size)
        val restored = YuvConverter.nv21ToArgb(nv21, width, height)
        val originalLuma = (pixels[0] shr 16) and 0xFF
        val restoredLuma = (restored[0] shr 16) and 0xFF
        assertTrue("Luma sai lệch quá lớn: $originalLuma vs $restoredLuma", kotlin.math.abs(originalLuma - restoredLuma) <= 6)
    }

    @Test
    fun rotate180RestoresOriginalAfterSecondRotation() {
        val width = 4
        val height = 4
        val pixels = IntArray(width * height) { index -> 0xFF000000.toInt() or (index * 8 shl 8) }
        val nv21 = YuvConverter.argbToNv21(pixels, width, height)
        val rotated = YuvConverter.rotateNv21(nv21, width, height, 180)
        val restored = YuvConverter.rotateNv21(rotated, width, height, 180)
        assertArrayEquals(nv21, restored)
    }

    @Test
    fun scaleNv21ProducesCorrectBufferSize() {
        val source = ByteArray(YuvConverter.nv21BufferSize(64, 32)) { 16 }
        val scaled = YuvConverter.scaleNv21(source, 64, 32, 32, 16)
        assertEquals(YuvConverter.nv21BufferSize(32, 16), scaled.size)
    }
}
