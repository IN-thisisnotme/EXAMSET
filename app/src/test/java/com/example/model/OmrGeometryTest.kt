package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class OmrGeometryTest {

    @Test
    fun testPhysicalA4Dimensions() {
        assertEquals(210.0f, OmrGeometry.PAGE_WIDTH_MM, 0.001f)
        assertEquals(297.0f, OmrGeometry.PAGE_HEIGHT_MM, 0.001f)
        assertEquals(5.0f, OmrGeometry.MARGIN_X_MM, 0.001f)
        assertEquals(5.0f, OmrGeometry.MARGIN_Y_MM, 0.001f)

        assertEquals(200.0f, OmrGeometry.PRINTABLE_WIDTH_MM, 0.001f)
        assertEquals(287.0f, OmrGeometry.PRINTABLE_HEIGHT_MM, 0.001f)
    }

    @Test
    fun testVerticalBudgetAccommodates200QuestionsStrictlyOnSinglePage() {
        val layout = OmrGeometry.calculateLayout(200)
        assertEquals(4, layout.numColumns)
        assertEquals(50, layout.rowsPerColumn)
        assertEquals(200, layout.totalQuestions)

        // Grid height = 50 rows * 4.05mm = 202.5mm
        assertEquals(202.5f, layout.gridHeightMm, 0.001f)

        // Available height in single A4 page
        assertTrue(
            "Grid height (${layout.gridHeightMm}mm) must fit within available height (${OmrGeometry.GRID_AVAILABLE_HEIGHT_MM}mm)",
            layout.gridHeightMm <= OmrGeometry.GRID_AVAILABLE_HEIGHT_MM
        )
    }

    @Test
    fun testLayoutFixedAcrossQuestionCounts() {
        // Across any question count, columns and rows per column remain fixed
        val plan10 = OmrGeometry.calculateLayout(10)
        assertEquals(4, plan10.numColumns)
        assertEquals(50, plan10.rowsPerColumn)

        val plan25 = OmrGeometry.calculateLayout(25)
        assertEquals(4, plan25.numColumns)
        assertEquals(50, plan25.rowsPerColumn)

        val plan50 = OmrGeometry.calculateLayout(50)
        assertEquals(4, plan50.numColumns)
        assertEquals(50, plan50.rowsPerColumn)

        val plan100 = OmrGeometry.calculateLayout(100)
        assertEquals(4, plan100.numColumns)
        assertEquals(50, plan100.rowsPerColumn)

        val plan200 = OmrGeometry.calculateLayout(200)
        assertEquals(4, plan200.numColumns)
        assertEquals(50, plan200.rowsPerColumn)
    }

    @Test
    fun testDeterministicBubbleCoordinatesRegardlessOfQuestionCount() {
        // Question 1 Option A must have the EXACT SAME physical coordinate whether test has 10, 50, or 200 Qs
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
