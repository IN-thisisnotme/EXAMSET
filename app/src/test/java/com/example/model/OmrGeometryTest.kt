package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.math.hypot

class OmrGeometryTest {

    @Test
    fun testPhysicalA4Dimensions() {
        assertEquals(210.0f, OmrGeometry.PAGE_WIDTH_MM, 0.001f)
        assertEquals(297.0f, OmrGeometry.PAGE_HEIGHT_MM, 0.001f)
        assertEquals(4.0f, OmrGeometry.MARGIN_X_MM, 0.001f)
        assertEquals(4.0f, OmrGeometry.MARGIN_Y_MM, 0.001f)

        assertEquals(202.0f, OmrGeometry.PRINTABLE_WIDTH_MM, 0.001f)
        assertEquals(289.0f, OmrGeometry.PRINTABLE_HEIGHT_MM, 0.001f)
    }

    @Test
    fun testVerticalBudgetAccommodates200QuestionsStrictlyOnSinglePage() {
        val layout = OmrGeometry.calculateLayout(200)
        assertEquals(4, layout.numColumns)
        assertEquals(35, layout.rowsSectionA)
        assertEquals(15, layout.rowsSectionB)
        assertEquals(200, layout.totalQuestions)

        // Total grid height must fit safely within single A4 printable height with margin for headers and footer
        val totalVerticalUsed = OmrGeometry.HEADER_HEIGHT_MM + layout.totalGridHeightMm + OmrGeometry.BOTTOM_AREA_HEIGHT_MM
        assertTrue(
            "Total vertical content ($totalVerticalUsed mm) must fit within printable height (${OmrGeometry.PRINTABLE_HEIGHT_MM} mm)",
            totalVerticalUsed <= OmrGeometry.PRINTABLE_HEIGHT_MM
        )
    }

    @Test
    fun testNeetQuestionMappingSectionsAndColumns() {
        // Subject 1 (Physics): Q 001..050
        val (s1a, isA1, r1) = OmrGeometry.getQuestionMapping(1)
        assertEquals(0, s1a)
        assertTrue(isA1)
        assertEquals(0, r1)

        val (s35a, isA35, r35) = OmrGeometry.getQuestionMapping(35)
        assertEquals(0, s35a)
        assertTrue(isA35)
        assertEquals(34, r35)

        val (s36b, isA36, r36) = OmrGeometry.getQuestionMapping(36)
        assertEquals(0, s36b)
        assertFalse(isA36)
        assertEquals(0, r36)

        val (s50b, isA50, r50) = OmrGeometry.getQuestionMapping(50)
        assertEquals(0, s50b)
        assertFalse(isA50)
        assertEquals(14, r50)

        // Subject 2 (Chemistry): Q 051..100
        val (s51a, isA51, r51) = OmrGeometry.getQuestionMapping(51)
        assertEquals(1, s51a)
        assertTrue(isA51)
        assertEquals(0, r51)

        // Subject 3 (Botany): Q 101..150
        val (s101a, isA101, r101) = OmrGeometry.getQuestionMapping(101)
        assertEquals(2, s101a)
        assertTrue(isA101)
        assertEquals(0, r101)

        // Subject 4 (Zoology): Q 151..200
        val (s151a, isA151, r151) = OmrGeometry.getQuestionMapping(151)
        assertEquals(3, s151a)
        assertTrue(isA151)
        assertEquals(0, r151)

        val (s200b, isA200, r200) = OmrGeometry.getQuestionMapping(200)
        assertEquals(3, s200b)
        assertFalse(isA200)
        assertEquals(14, r200)
    }

    @Test
    fun testDeterministicBubbleCoordinatesRegardlessOfQuestionCount() {
        val bubbles10 = OmrGeometry.getBubbleCoordinates(10)
        val bubbles50 = OmrGeometry.getBubbleCoordinates(50)
        val bubbles200 = OmrGeometry.getBubbleCoordinates(200)

        for (i in 0 until 10 * 4) {
            assertEquals(bubbles200[i].centerXMm, bubbles10[i].centerXMm, 0.001f)
            assertEquals(bubbles200[i].centerYMm, bubbles10[i].centerYMm, 0.001f)
            assertEquals(bubbles200[i].radiusMm, bubbles10[i].radiusMm, 0.001f)
        }

        for (i in 0 until 50 * 4) {
            assertEquals(bubbles200[i].centerXMm, bubbles50[i].centerXMm, 0.001f)
            assertEquals(bubbles200[i].centerYMm, bubbles50[i].centerYMm, 0.001f)
            assertEquals(bubbles200[i].radiusMm, bubbles50[i].radiusMm, 0.001f)
        }
    }

    @Test
    fun testRegistrationAnchors() {
        val anchors = OmrGeometry.getAnchors()
        assertEquals(4, anchors.size)

        val names = anchors.map { it.name }.toSet()
        assertEquals(setOf("TL", "TR", "BL", "BR"), names)

        for (a in anchors) {
            assertEquals(6.0f, a.sizeMm, 0.001f)
            assertTrue("Anchor left within page", a.left >= 0f)
            assertTrue("Anchor top within page", a.top >= 0f)
            assertTrue("Anchor right within page", a.right <= OmrGeometry.PAGE_WIDTH_MM)
            assertTrue("Anchor bottom within page", a.bottom <= OmrGeometry.PAGE_HEIGHT_MM)
        }
    }

    @Test
    fun testBubbleCoordinatesWithinPrintableBounds() {
        val bubbles = OmrGeometry.getBubbleCoordinates(200)
        assertEquals(200 * OmrGeometry.OPTIONS_PER_QUESTION, bubbles.size)

        for (b in bubbles) {
            val left = b.centerXMm - b.radiusMm
            val right = b.centerXMm + b.radiusMm
            val top = b.centerYMm - b.radiusMm
            val bottom = b.centerYMm + b.radiusMm

            assertTrue("Bubble $b must be within left margin", left >= OmrGeometry.MARGIN_X_MM)
            assertTrue("Bubble $b must be within right margin", right <= OmrGeometry.PAGE_WIDTH_MM - OmrGeometry.MARGIN_X_MM)
            assertTrue("Bubble $b must be below header", top >= OmrGeometry.MARGIN_Y_MM)
            assertTrue("Bubble $b must be within page height", bottom <= OmrGeometry.PAGE_HEIGHT_MM - OmrGeometry.MARGIN_Y_MM)
        }
    }

    @Test
    fun testNaturalBubbleSpacingAndNoOverlap() {
        val bubbles = OmrGeometry.getBubbleCoordinates(200)
        val byQuestion = bubbles.groupBy { it.questionNum }
        assertEquals(200, byQuestion.size)

        for ((qNum, qBubbles) in byQuestion) {
            assertEquals(4, qBubbles.size)
            for (i in 0 until qBubbles.size - 1) {
                val b1 = qBubbles[i]
                val b2 = qBubbles[i + 1]
                val dist = hypot(b2.centerXMm - b1.centerXMm, b2.centerYMm - b1.centerYMm)
                // Pitch is 4.8mm, outer diameter 3.8mm, gap is 1.0mm
                assertEquals(4.8f, dist, 0.01f)
                assertTrue(
                    "Distance between bubbles in Q$qNum must exceed diameter (3.8mm)",
                    dist >= OmrGeometry.BUBBLE_DIAMETER_MM
                )
            }
        }
    }

    @Test
    fun testCanonicalPixelCoordinates() {
        val pxBubbles = OmrGeometry.getCanonicalPixelCoordinates(200)
        assertEquals(800, pxBubbles.size)

        assertEquals(2100, OmrGeometry.CANONICAL_WIDTH_PX)
        assertEquals(2970, OmrGeometry.CANONICAL_HEIGHT_PX)

        for (pb in pxBubbles) {
            assertTrue("Pixel X within canonical width", pb.centerXPx in 0f..2100f)
            assertTrue("Pixel Y within canonical height", pb.centerYPx in 0f..2970f)
            assertEquals(19.0f, pb.radiusPx, 0.001f) // 1.9mm * 10 px/mm = 19.0 px
        }
    }
}
