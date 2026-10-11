package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.example.model.OmrGeometry
import com.example.model.QuestionPaper
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * High-Precision Computer Vision & Optical Mark Recognition (OMR) Processor.
 *
 * Implements:
 * 1. Corner anchor detection & fiducial localization.
 * 2. 8-DOF Projective Homography matrix solver (Gaussian elimination).
 * 3. Perspective un-warping mapping canonical A4 geometry coordinates back to source photo pixels.
 * 4. Drop-out ink filtering (discriminating blue/black pen marks from magenta #be185d layout ink).
 * 5. Optical density bubble integration & candidate response scoring.
 */
object OmrVisionProcessor {

    data class PointF(val x: Float, val y: Float)

    data class Quadrilateral(
        val topLeft: PointF,
        val topRight: PointF,
        val bottomLeft: PointF,
        val bottomRight: PointF
    )

    enum class BubbleStatus {
        MARKED,
        UNMARKED
    }

    data class BubbleScanResult(
        val questionNum: Int,
        val optionIndex: Int,
        val optionChar: Char,
        val opticalDensity: Float, // 0.0 (pure white paper) to 1.0 (fully saturated mark)
        val isMarked: Boolean
    )

    data class QuestionScanResult(
        val questionNum: Int,
        val selectedOption: Char?, // 'A', 'B', 'C', 'D', or null if unattempted
        val isMultipleMarked: Boolean,
        val bubbleResults: List<BubbleScanResult>
    )

    data class OmrScanOutput(
        val totalQuestions: Int,
        val attemptedCount: Int,
        val correctCount: Int,
        val incorrectCount: Int,
        val multipleMarkedCount: Int,
        val unattemptedCount: Int,
        val totalScore: Int,
        val maxPossibleScore: Int,
        val questionResults: Map<Int, QuestionScanResult>,
        val perspectiveDetected: Boolean
    )

    /**
     * 3x3 Projective Homography Matrix representation.
     */
    class HomographyMatrix(val data: DoubleArray) {
        init {
            require(data.size == 9) { "Homography matrix requires exactly 9 elements" }
        }

        /**
         * Transforms point (x, y) through projective homography:
         * [x'] = H * [x]
         * [y']       [y]
         * [w']       [1]
         * Result: (x'/w', y'/w')
         */
        fun transform(x: Double, y: Double): Pair<Double, Double> {
            val w = data[6] * x + data[7] * y + data[8]
            if (abs(w) < 1e-9) return Pair(0.0, 0.0)
            val px = (data[0] * x + data[1] * y + data[2]) / w
            val py = (data[3] * x + data[4] * y + data[5]) / w
            return Pair(px, py)
        }

        /**
         * Inverts the 3x3 homography matrix using analytic adjugate and determinant.
         */
        fun invert(): HomographyMatrix? {
            val m = data
            val det = m[0] * (m[4] * m[8] - m[5] * m[7]) -
                    m[1] * (m[3] * m[8] - m[5] * m[6]) +
                    m[2] * (m[3] * m[7] - m[4] * m[6])

            if (abs(det) < 1e-12) return null
            val invDet = 1.0 / det

            val inv = DoubleArray(9)
            inv[0] = (m[4] * m[8] - m[5] * m[7]) * invDet
            inv[1] = (m[2] * m[7] - m[1] * m[8]) * invDet
            inv[2] = (m[1] * m[5] - m[2] * m[4]) * invDet

            inv[3] = (m[5] * m[6] - m[3] * m[8]) * invDet
            inv[4] = (m[0] * m[8] - m[2] * m[6]) * invDet
            inv[5] = (m[2] * m[3] - m[0] * m[5]) * invDet

            inv[6] = (m[3] * m[7] - m[4] * m[6]) * invDet
            inv[7] = (m[1] * m[6] - m[0] * m[7]) * invDet
            inv[8] = (m[0] * m[4] - m[1] * m[3]) * invDet

            return HomographyMatrix(inv)
        }
    }

    /**
     * Solves 8-DOF Direct Linear Transformation (DLT) homography mapping
     * 4 source points to 4 destination points.
     */
    fun computeHomography(
        srcCorners: Quadrilateral,
        dstCorners: Quadrilateral
    ): HomographyMatrix? {
        val src = arrayOf(
            srcCorners.topLeft,
            srcCorners.topRight,
            srcCorners.bottomRight,
            srcCorners.bottomLeft
        )
        val dst = arrayOf(
            dstCorners.topLeft,
            dstCorners.topRight,
            dstCorners.bottomRight,
            dstCorners.bottomLeft
        )

        // 8x8 linear system A * h = b where h9 = 1
        val a = Array(8) { DoubleArray(8) }
        val b = DoubleArray(8)

        for (i in 0 until 4) {
            val sx = src[i].x.toDouble()
            val sy = src[i].y.toDouble()
            val dx = dst[i].x.toDouble()
            val dy = dst[i].y.toDouble()

            // Row 2*i: sx*h0 + sy*h1 + h2 - dx*sx*h6 - dx*sy*h7 = dx
            a[2 * i][0] = sx
            a[2 * i][1] = sy
            a[2 * i][2] = 1.0
            a[2 * i][3] = 0.0
            a[2 * i][4] = 0.0
            a[2 * i][5] = 0.0
            a[2 * i][6] = -dx * sx
            a[2 * i][7] = -dx * sy
            b[2 * i] = dx

            // Row 2*i+1: sx*h3 + sy*h4 + h5 - dy*sx*h6 - dy*sy*h7 = dy
            a[2 * i + 1][0] = 0.0
            a[2 * i + 1][1] = 0.0
            a[2 * i + 1][2] = 0.0
            a[2 * i + 1][3] = sx
            a[2 * i + 1][4] = sy
            a[2 * i + 1][5] = 1.0
            a[2 * i + 1][6] = -dy * sx
            a[2 * i + 1][7] = -dy * sy
            b[2 * i + 1] = dy
        }

        val h = solveLinearSystem(a, b) ?: return null
        return HomographyMatrix(
            doubleArrayOf(
                h[0], h[1], h[2],
                h[3], h[4], h[5],
                h[6], h[7], 1.0
            )
        )
    }

    /**
     * Solves linear system A * x = b via Gaussian Elimination with partial pivoting.
     */
    private fun solveLinearSystem(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }

        for (p in 0 until n) {
            var maxRow = p
            var maxVal = abs(m[p][p])
            for (i in p + 1 until n) {
                if (abs(m[i][p]) > maxVal) {
                    maxVal = abs(m[i][p])
                    maxRow = i
                }
            }

            if (maxVal < 1e-12) return null // Singular matrix

            // Swap rows
            val temp = m[p]
            m[p] = m[maxRow]
            m[maxRow] = temp

            // Eliminate lower rows
            for (i in p + 1 until n) {
                val alpha = m[i][p] / m[p][p]
                for (j in p until n + 1) {
                    m[i][j] -= alpha * m[p][j]
                }
            }
        }

        // Back substitution
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var sum = 0.0
            for (j in i + 1 until n) {
                sum += m[i][j] * x[j]
            }
            x[i] = (m[i][n] - sum) / m[i][i]
        }
        return x
    }

    /**
     * Finds corners of an OMR sheet within a bitmap.
     * Searches for dark fiducial registration blocks near the four perimeter corners.
     */
    fun detectCorners(bitmap: Bitmap): Quadrilateral {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // Fallback quadrilateral default (assuming full frame document image)
        val defaultQuad = Quadrilateral(
            topLeft = PointF(w * 0.02f, h * 0.02f),
            topRight = PointF(w * 0.98f, h * 0.02f),
            bottomLeft = PointF(w * 0.02f, h * 0.98f),
            bottomRight = PointF(w * 0.98f, h * 0.98f)
        )

        val marginX = (w * 0.15f).toInt()
        val marginY = (h * 0.15f).toInt()

        val tl = findDarkestCentroidInWindow(bitmap, 0, 0, marginX, marginY) ?: defaultQuad.topLeft
        val tr = findDarkestCentroidInWindow(bitmap, (w - marginX).toInt(), 0, marginX, marginY) ?: defaultQuad.topRight
        val bl = findDarkestCentroidInWindow(bitmap, 0, (h - marginY).toInt(), marginX, marginY) ?: defaultQuad.bottomLeft
        val br = findDarkestCentroidInWindow(bitmap, (w - marginX).toInt(), (h - marginY).toInt(), marginX, marginY) ?: defaultQuad.bottomRight

        return Quadrilateral(
            topLeft = tl,
            topRight = tr,
            bottomLeft = bl,
            bottomRight = br
        )
    }

    private fun findDarkestCentroidInWindow(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int
    ): PointF? {
        val endX = (startX + width).coerceAtMost(bitmap.width)
        val endY = (startY + height).coerceAtMost(bitmap.height)

        var totalWeight = 0.0
        var sumX = 0.0
        var sumY = 0.0

        for (y in startY until endY step 2) {
            for (x in startX until endX step 2) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Drop-out ink filter:
                // Dark ink / pencil has low brightness across all channels.
                // Magenta sheet background (#be185d) has high Red and low Green.
                val brightness = (r * 0.299f + g * 0.587f + b * 0.114f)
                if (brightness < 80.0f) { // Strong dark anchor pixel
                    val weight = (255.0f - brightness).toDouble()
                    totalWeight += weight
                    sumX += x * weight
                    sumY += y * weight
                }
            }
        }

        return if (totalWeight > 200.0) {
            PointF((sumX / totalWeight).toFloat(), (sumY / totalWeight).toFloat())
        } else {
            null
        }
    }

    /**
     * Scans an OMR bitmap using projective homography alignment against canonical NEET geometry.
     *
     * @param bitmap Input image from camera photo or file picker.
     * @param paper Reference question paper containing official answer key.
     * @param customCorners Optional manually adjusted or pre-detected corners.
     */
    fun processOmr(
        bitmap: Bitmap,
        paper: QuestionPaper,
        customCorners: Quadrilateral? = null
    ): OmrScanOutput {
        val totalQuestions = paper.questions.size.coerceIn(1, OmrGeometry.MAX_SINGLE_PAGE_QUESTIONS)

        // 1. Get canonical registration anchors in canonical destination pixel space
        val anchors = OmrGeometry.getAnchors()
        val scale = OmrGeometry.CANONICAL_SCALE_PX_PER_MM
        val canonicalCorners = Quadrilateral(
            topLeft = PointF(anchors[0].centerX * scale, anchors[0].centerY * scale),
            topRight = PointF(anchors[1].centerX * scale, anchors[1].centerY * scale),
            bottomLeft = PointF(anchors[2].centerX * scale, anchors[2].centerY * scale),
            bottomRight = PointF(anchors[3].centerX * scale, anchors[3].centerY * scale)
        )

        // 2. Identify corners in input image
        val detectedCorners = customCorners ?: detectCorners(bitmap)

        // 3. Compute homography mapping canonical destination (u, v) -> source photo (x, y)
        // We compute H(canonical -> source) so that for any canonical bubble coordinate,
        // we can lookup the exact pixel in the input photo.
        val canonicalToSourceH = computeHomography(canonicalCorners, detectedCorners)

        // 4. Sample canonical bubble positions
        val canonicalBubbles = OmrGeometry.getCanonicalPixelCoordinates(totalQuestions)

        // Group bubbles by question
        val bubblesByQuestion = canonicalBubbles.groupBy { it.questionNum }

        val questionResults = mutableMapOf<Int, QuestionScanResult>()
        var attemptedCount = 0
        var correctCount = 0
        var incorrectCount = 0
        var multipleMarkedCount = 0
        var unattemptedCount = 0
        var totalScore = 0

        // Adaptive background baseline estimator: sample reference white paper areas
        val baselineDarkness = estimatePaperBaseline(bitmap, detectedCorners)

        for (qNum in 1..totalQuestions) {
            val questionBubbles = bubblesByQuestion[qNum] ?: emptyList()
            val questionOptResults = mutableListOf<BubbleScanResult>()

            for (bubble in questionBubbles) {
                // Map canonical center to source photo
                val (srcX, srcY) = canonicalToSourceH?.transform(bubble.centerXPx.toDouble(), bubble.centerYPx.toDouble())
                    ?: Pair(
                        (bubble.centerXPx / OmrGeometry.CANONICAL_WIDTH_PX.toFloat()) * bitmap.width.toDouble(),
                        (bubble.centerYPx / OmrGeometry.CANONICAL_HEIGHT_PX.toFloat()) * bitmap.height.toDouble()
                    )

                // Sample darkness within bubble radius
                val density = measureBubbleDarkness(
                    bitmap = bitmap,
                    centerX = srcX.toFloat(),
                    centerY = srcY.toFloat(),
                    radiusPx = (bubble.radiusPx * 0.75f).coerceAtLeast(3f)
                )

                // Normalized optical density against baseline paper background
                val effectiveDensity = (density - baselineDarkness).coerceIn(0.0f, 1.0f)
                val isMarked = effectiveDensity >= 0.28f // 28% optical threshold above baseline

                questionOptResults.add(
                    BubbleScanResult(
                        questionNum = qNum,
                        optionIndex = bubble.optionIndex,
                        optionChar = bubble.optionChar,
                        opticalDensity = effectiveDensity,
                        isMarked = isMarked
                    )
                )
            }

            val markedOptions = questionOptResults.filter { it.isMarked }
            val (selectedOption, isMultiple) = when {
                markedOptions.size == 1 -> Pair(markedOptions.first().optionChar, false)
                markedOptions.size > 1 -> Pair(null, true)
                else -> Pair(null, false)
            }

            // Scoring against QuestionPaper
            val officialQuestion = paper.questions.getOrNull(qNum - 1)
            val officialCorrectOption = officialQuestion?.correctAnswer?.uppercase()?.firstOrNull()

            if (isMultiple) {
                multipleMarkedCount++
                totalScore -= 1 // NEET -1 penalty for faulty multiple mark
            } else if (selectedOption != null) {
                attemptedCount++
                if (officialCorrectOption != null && selectedOption == officialCorrectOption) {
                    correctCount++
                    totalScore += 4 // NEET standard +4 for correct
                } else {
                    incorrectCount++
                    totalScore -= 1 // NEET standard -1 for incorrect
                }
            } else {
                unattemptedCount++
                // 0 marks for skipped
            }

            questionResults[qNum] = QuestionScanResult(
                questionNum = qNum,
                selectedOption = selectedOption,
                isMultipleMarked = isMultiple,
                bubbleResults = questionOptResults
            )
        }

        return OmrScanOutput(
            totalQuestions = totalQuestions,
            attemptedCount = attemptedCount,
            correctCount = correctCount,
            incorrectCount = incorrectCount,
            multipleMarkedCount = multipleMarkedCount,
            unattemptedCount = unattemptedCount,
            totalScore = totalScore,
            maxPossibleScore = totalQuestions * 4,
            questionResults = questionResults,
            perspectiveDetected = canonicalToSourceH != null
        )
    }

    /**
     * Measures normalized optical darkness inside a circular bubble footprint.
     * Evaluates RGB pixels with drop-out ink attenuation.
     */
    fun measureBubbleDarkness(
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        radiusPx: Float
    ): Float {
        val cx = centerX.roundToInt()
        val cy = centerY.roundToInt()
        val r = radiusPx.roundToInt().coerceAtLeast(1)

        val minX = (cx - r).coerceIn(0, bitmap.width - 1)
        val maxX = (cx + r).coerceIn(0, bitmap.width - 1)
        val minY = (cy - r).coerceIn(0, bitmap.height - 1)
        val maxY = (cy + r).coerceIn(0, bitmap.height - 1)

        var sampleCount = 0
        var darknessSum = 0.0

        val rSq = radiusPx * radiusPx

        for (y in minY..maxY) {
            val dy = y - centerY
            for (x in minX..maxX) {
                val dx = x - centerX
                if (dx * dx + dy * dy <= rSq) {
                    val pixel = bitmap.getPixel(x, y)
                    val red = Color.red(pixel)
                    val green = Color.green(pixel)
                    val blue = Color.blue(pixel)

                    // Drop-out magenta ink calculation:
                    // NEET magenta template ink has RGB ~(190, 24, 93) with high Red (R ~ 190).
                    // Graphite pencil and Blue/Black pen have very low Red (R < 50), absorbing red light.
                    // By checking red reflectance (1.0 - R/255), magenta template borders drop out,
                    // while student pencil and pen marks are detected with high contrast.
                    val redReflectance = red / 255.0f
                    val markDarkness = (1.0f - redReflectance).coerceIn(0.0f, 1.0f)

                    darknessSum += markDarkness
                    sampleCount++
                }
            }
        }

        return if (sampleCount > 0) (darknessSum / sampleCount).toFloat() else 0.0f
    }

    /**
     * Estimates background baseline paper darkness from center gutter regions.
     */
    private fun estimatePaperBaseline(bitmap: Bitmap, corners: Quadrilateral): Float {
        val midX = ((corners.topLeft.x + corners.topRight.x) / 2f).roundToInt().coerceIn(0, bitmap.width - 1)
        val midY = ((corners.topLeft.y + corners.bottomLeft.y) / 2f).roundToInt().coerceIn(0, bitmap.height - 1)

        var total = 0f
        var count = 0
        for (dy in -10..10 step 5) {
            for (dx in -10..10 step 5) {
                val x = (midX + dx).coerceIn(0, bitmap.width - 1)
                val y = (midY + dy).coerceIn(0, bitmap.height - 1)
                val p = bitmap.getPixel(x, y)
                val r = Color.red(p)
                val d = 1.0f - (r / 255.0f)
                total += d
                count++
            }
        }
        return if (count > 0) total / count else 0.05f
    }
}
