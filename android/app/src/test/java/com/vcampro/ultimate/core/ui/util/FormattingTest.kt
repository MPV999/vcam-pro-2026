package com.vcampro.ultimate.core.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun durationUnderOneHour() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:05", formatDuration(5_000L))
        assertEquals("1:05", formatDuration(65_000L))
    }

    @Test
    fun durationOverOneHour() {
        assertEquals("1:00:00", formatDuration(3_600_000L))
        assertEquals("2:03:04", formatDuration(7_384_000L))
    }

    @Test
    fun bytesAreScaled() {
        assertEquals("512 B", formatBytes(512L))
        assertEquals("1 KB", formatBytes(1024L))
        assertEquals("1.0 MB", formatBytes(1024L * 1024L))
        assertEquals("1.00 GB", formatBytes(1024L * 1024L * 1024L))
    }

    @Test
    fun percentRounds() {
        assertEquals("45%", formatPercent(0.451f))
        assertEquals("100%", formatPercent(1f))
    }
}
