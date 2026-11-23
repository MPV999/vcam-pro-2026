package com.vcampro.ultimate.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun newerPatchIsDetected() {
        assertTrue(UpdateChecker.isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("0.9.9", "1.0.0"))
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertTrue(UpdateChecker.isNewerVersion("1.1", "1.0.9"))
        assertFalse(UpdateChecker.isNewerVersion("1.0", "1.0.1"))
    }

    @Test
    fun largeJumpIsDetected() {
        assertTrue(UpdateChecker.isNewerVersion("2.0.0", "1.9.12"))
    }

    @Test
    fun buildSuffixIsIgnoredForComparison() {
        assertTrue(UpdateChecker.isNewerVersion("1.0.2-debug", "1.0.1"))
    }
}
