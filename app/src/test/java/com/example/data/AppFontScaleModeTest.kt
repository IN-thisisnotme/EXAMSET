package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFontScaleModeTest {

    @Test
    fun testStandardFixedHasExactBaselineScale() {
        val mode = AppFontScaleMode.STANDARD_FIXED
        assertEquals(1.0f, mode.scaleFactor, 0.001f)
        assertFalse("STANDARD_FIXED must not be system dependent", mode.isSystemDependent)
        assertTrue(mode.title.contains("Standard"))
        assertTrue(mode.subtitle.contains("Immune to device system font changes"))
    }

    @Test
    fun testCompactScaleFactor() {
        val mode = AppFontScaleMode.COMPACT
        assertEquals(0.88f, mode.scaleFactor, 0.001f)
        assertFalse(mode.isSystemDependent)
    }

    @Test
    fun testLargeAndExtraLargeScaleFactors() {
        assertEquals(1.15f, AppFontScaleMode.LARGE.scaleFactor, 0.001f)
        assertEquals(1.25f, AppFontScaleMode.EXTRA_LARGE.scaleFactor, 0.001f)
    }

    @Test
    fun testFollowSystemIsSystemDependent() {
        val mode = AppFontScaleMode.FOLLOW_SYSTEM
        assertTrue("FOLLOW_SYSTEM must be marked as system dependent", mode.isSystemDependent)
    }

    @Test
    fun testFromStringParsingWithFallbacks() {
        assertEquals(AppFontScaleMode.STANDARD_FIXED, AppFontScaleMode.fromString(null))
        assertEquals(AppFontScaleMode.STANDARD_FIXED, AppFontScaleMode.fromString(""))
        assertEquals(AppFontScaleMode.STANDARD_FIXED, AppFontScaleMode.fromString("unknown_value"))
        assertEquals(AppFontScaleMode.COMPACT, AppFontScaleMode.fromString("compact"))
        assertEquals(AppFontScaleMode.LARGE, AppFontScaleMode.fromString("LARGE"))
        assertEquals(AppFontScaleMode.EXTRA_LARGE, AppFontScaleMode.fromString("extra_large"))
        assertEquals(AppFontScaleMode.FOLLOW_SYSTEM, AppFontScaleMode.fromString("FOLLOW_SYSTEM"))
    }
}
