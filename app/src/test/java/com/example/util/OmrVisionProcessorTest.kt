package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.model.McqQuestion
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OmrVisionProcessorTest {

    @Test
    fun testHomographyIdentityMatrix() {
        val quad1 = OmrVisionProcessor.Quadrilateral(
            topLeft = OmrVisionProcessor.PointF(0f, 0f),
            topRight = OmrVisionProcessor.PointF(100f, 0f),
            bottomLeft = OmrVisionProcessor.PointF(0f, 100f),
            bottomRight = OmrVisionProcessor.PointF(100f, 100f)
        )
        val quad2 = OmrVisionProcessor.Quadrilateral(
            topLeft = OmrVisionProcessor.PointF(0f, 0f),
            topRight = OmrVisionProcessor.PointF(100f, 0f),
            bottomLeft = OmrVisionProcessor.PointF(0f, 100f),
            bottomRight = OmrVisionProcessor.PointF(100f, 100f)
        )

        val h = OmrVisionProcessor.computeHomography(quad1, quad2)
        assertNotNull(h)
        val matrix = h!!

        val (tx, ty) = matrix.transform(50.0, 50.0)
        assertEquals(50.0, tx, 1e-4)
        assertEquals(50.0, ty, 1e-4)
    }

    @Test
    fun testHomographyPerspectiveTransformationAndInversion() {
        val src = OmrVisionProcessor.Quadrilateral(
            topLeft = OmrVisionProcessor.PointF(10f, 10f),
            topRight = OmrVisionProcessor.PointF(200f, 20f),
            bottomLeft = OmrVisionProcessor.PointF(5f, 290f),
            bottomRight = OmrVisionProcessor.PointF(210f, 280f)
        )
        val dst = OmrVisionProcessor.Quadrilateral(
            topLeft = OmrVisionProcessor.PointF(0f, 0f),
            topRight = OmrVisionProcessor.PointF(2100f, 0f),
            bottomLeft = OmrVisionProcessor.PointF(0f, 2970f),
            bottomRight = OmrVisionProcessor.PointF(2100f, 2970f)
        )

        val h = OmrVisionProcessor.computeHomography(src, dst)
        assertNotNull(h)
        val invH = h!!.invert()
        assertNotNull(invH)

        // Test forward transform of top-left corner
        val (dstX, dstY) = h.transform(10.0, 10.0)
        assertEquals(0.0, dstX, 0.5)
        assertEquals(0.0, dstY, 0.5)

        // Test round-trip via inverse matrix
        val (backX, backY) = invH!!.transform(dstX, dstY)
        assertEquals(10.0, backX, 0.5)
        assertEquals(10.0, backY, 0.5)
    }

    @Test
    fun testDropOutInkFilterDistinguishesPenFromMagenta() {
        // Create 20x20 bitmap with white background, magenta bubble ring, and dark pen mark
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        
        // Fill background white
        for (y in 0 until 20) {
            for (x in 0 until 20) {
                bitmap.setPixel(x, y, Color.WHITE)
            }
        }

        val magentaColor = Color.rgb(190, 24, 93) // Official NEET Magenta #be185d
        val penColor = Color.BLACK // Dark Blue/Black graphite or ballpoint pen

        // Region 1: Clean magenta ring on white paper around (5, 5) with radius 3
        for (y in 2..8) {
            for (x in 2..8) {
                val dx = x - 5
                val dy = y - 5
                if (dx * dx + dy * dy <= 9) {
                    bitmap.setPixel(x, y, magentaColor)
                }
            }
        }

        // Region 2: Filled black mark around (15, 15) with radius 3
        for (y in 12..18) {
            for (x in 12..18) {
                val dx = x - 15
                val dy = y - 15
                if (dx * dx + dy * dy <= 9) {
                    bitmap.setPixel(x, y, penColor)
                }
            }
        }

        val magentaDarkness = OmrVisionProcessor.measureBubbleDarkness(bitmap, 5f, 5f, 3f)
        val penDarkness = OmrVisionProcessor.measureBubbleDarkness(bitmap, 15f, 15f, 3f)

        // Pen mark must have significantly higher optical darkness than magenta template ink
        assertTrue(
            "Pen mark darkness ($penDarkness) should be much higher than magenta ($magentaDarkness)",
            penDarkness > magentaDarkness + 0.35f
        )
    }

    @Test
    fun testProcessOmrFullScoringPipeline() {
        // Build mock 5-question paper
        val questions = listOf(
            McqQuestion(1, "Q1", QuestionOptions("A", "B", "C", "D"), "B", "Explanation"),
            McqQuestion(2, "Q2", QuestionOptions("A", "B", "C", "D"), "C", "Explanation"),
            McqQuestion(3, "Q3", QuestionOptions("A", "B", "C", "D"), "A", "Explanation"),
            McqQuestion(4, "Q4", QuestionOptions("A", "B", "C", "D"), "D", "Explanation"),
            McqQuestion(5, "Q5", QuestionOptions("A", "B", "C", "D"), "B", "Explanation")
        )
        val paper = QuestionPaper(
            metadata = PaperMetadata(title = "Test Paper", subject = "Physics"),
            questions = questions
        )

        // Create synthetic bitmap in canonical aspect ratio
        val width = 420
        val height = 594
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Mark 4 registration anchors at corners
        val anchorPaint = Paint().apply { color = Color.BLACK }
        canvas.drawRect(5f, 5f, 20f, 20f, anchorPaint) // TL
        canvas.drawRect(width - 20f, 5f, width - 5f, 20f, anchorPaint) // TR
        canvas.drawRect(5f, height - 20f, 20f, height - 5f, anchorPaint) // BL
        canvas.drawRect(width - 20f, height - 20f, width - 5f, height - 5f, anchorPaint) // BR

        // Custom corners mapping exactly to this synthetic bitmap
        val corners = OmrVisionProcessor.Quadrilateral(
            topLeft = OmrVisionProcessor.PointF(12.5f, 12.5f),
            topRight = OmrVisionProcessor.PointF(width - 12.5f, 12.5f),
            bottomLeft = OmrVisionProcessor.PointF(12.5f, height - 12.5f),
            bottomRight = OmrVisionProcessor.PointF(width - 12.5f, height - 12.5f)
        )

        val output = OmrVisionProcessor.processOmr(bitmap, paper, corners)

        assertNotNull(output)
        assertEquals(5, output.totalQuestions)
        assertEquals(0, output.attemptedCount)
        assertEquals(5, output.unattemptedCount)
        assertEquals(0, output.totalScore)
        assertEquals(20, output.maxPossibleScore)
    }
}
