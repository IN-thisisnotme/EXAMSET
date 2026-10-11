package com.example.model

/**
 * Immutable Centralized OMR Sheet Geometry Specification.
 *
 * Modeled directly after official competitive examination standards (NEET / CBSE):
 * - Strict single A4 page capacity for up to 200 questions.
 * - Left identification sidebar (Roll No., Test Booklet No., Test Booklet Code, Barcode, Declaration).
 * - Right 4-column answer grid (50 questions per column).
 * - Fixed, natural bubble spacing (3.8 mm diameter, 4.8 mm pitch) across ALL question counts.
 * - Corner fiducial registration markers for flawless optical mark scanning and perspective correction.
 */
object OmrGeometry {

    // --- Page Geometry (ISO A4 Standard) ---
    const val PAGE_WIDTH_MM: Float = 210.0f
    const val PAGE_HEIGHT_MM: Float = 297.0f
    const val MARGIN_X_MM: Float = 5.0f
    const val MARGIN_Y_MM: Float = 5.0f

    val PRINTABLE_WIDTH_MM: Float get() = PAGE_WIDTH_MM - (2f * MARGIN_X_MM) // 200.0 mm
    val PRINTABLE_HEIGHT_MM: Float get() = PAGE_HEIGHT_MM - (2f * MARGIN_Y_MM) // 287.0 mm

    // --- Corner Registration Fiducial Anchors ---
    const val ANCHOR_SIZE_MM: Float = 6.0f
    const val ANCHOR_MARGIN_MM: Float = 2.5f

    // --- Vertical Layout Budget ---
    const val HEADER_HEIGHT_MM: Float = 22.0f
    const val FOOTER_HEIGHT_MM: Float = 30.0f
    const val GRID_TOP_OFFSET_MM: Float = MARGIN_Y_MM + HEADER_HEIGHT_MM // 27.0 mm
    val GRID_AVAILABLE_HEIGHT_MM: Float get() = PRINTABLE_HEIGHT_MM - HEADER_HEIGHT_MM - FOOTER_HEIGHT_MM // 235.0 mm

    // --- NEET 2-Panel Horizontal Budget ---
    const val LEFT_PANEL_WIDTH_MM: Float = 62.0f
    const val PANEL_GUTTER_MM: Float = 3.0f
    val RIGHT_GRID_START_X_MM: Float get() = MARGIN_X_MM + LEFT_PANEL_WIDTH_MM + PANEL_GUTTER_MM // 70.0 mm
    val RIGHT_GRID_WIDTH_MM: Float get() = PRINTABLE_WIDTH_MM - LEFT_PANEL_WIDTH_MM - PANEL_GUTTER_MM // 135.0 mm

    // --- 4-Column Question Grid Dimensions ---
    const val NUM_GRID_COLUMNS: Int = 4
    const val ROWS_PER_COLUMN: Int = 50
    const val MAX_SINGLE_PAGE_QUESTIONS: Int = NUM_GRID_COLUMNS * ROWS_PER_COLUMN // 200 Questions
    const val SECTION_A_ROWS: Int = 35 // Questions 1..35 in each column
    const val SECTION_B_ROWS: Int = 15 // Questions 36..50 in each column

    const val COLUMN_WIDTH_MM: Float = 32.0f
    const val COLUMN_GUTTER_MM: Float = 2.0f
    const val ROW_PITCH_MM: Float = 4.05f
    const val COL_HEADER_HEIGHT_MM: Float = 7.0f

    // --- Natural Exam Bubble Dimensions ---
    const val BUBBLE_DIAMETER_MM: Float = 3.8f
    const val BUBBLE_RADIUS_MM: Float = BUBBLE_DIAMETER_MM / 2f
    const val BUBBLE_BORDER_THICKNESS_MM: Float = 0.35f
    const val BUBBLE_PITCH_X_MM: Float = 4.8f // Gap between bubbles = 4.8 - 3.8 = 1.0 mm (natural spacing)
    const val QNUM_WIDTH_MM: Float = 5.8f
    const val QNUM_TO_BUBBLE_GAP_MM: Float = 1.6f

    const val OPTIONS_PER_QUESTION: Int = 4
    val OPTION_LABELS: List<Char> = listOf('A', 'B', 'C', 'D')
    val OPTION_DIGITS: List<Char> = listOf('1', '2', '3', '4')

    // Official NEET drop-out ink palette
    const val THEME_COLOR_HEX: String = "#be185d" // Drop-out magenta pink
    const val THEME_LIGHT_BG_HEX: String = "#fdf2f8"
    const val THEME_BORDER_HEX: String = "#ec4899"

    // Canonical Vision Coordinate Space (10 pixels per millimeter = 2100 x 2970 px)
    const val CANONICAL_SCALE_PX_PER_MM: Float = 10.0f
    val CANONICAL_WIDTH_PX: Int = (PAGE_WIDTH_MM * CANONICAL_SCALE_PX_PER_MM).toInt() // 2100 px
    val CANONICAL_HEIGHT_PX: Int = (PAGE_HEIGHT_MM * CANONICAL_SCALE_PX_PER_MM).toInt() // 2970 px

    /**
     * Data contract for the layout partitioning of questions on the page.
     */
    data class OmrLayoutPlan(
        val numColumns: Int,
        val rowsPerColumn: Int,
        val totalQuestions: Int,
        val columnWidthMm: Float,
        val gridWidthMm: Float,
        val gridHeightMm: Float
    )

    /**
     * Mathematical anchor point representing a 4-corner fiducial marker.
     */
    data class AnchorPoint(
        val name: String,
        val centerX: Float,
        val centerY: Float,
        val sizeMm: Float
    ) {
        val left: Float get() = centerX - (sizeMm / 2f)
        val top: Float get() = centerY - (sizeMm / 2f)
        val right: Float get() = centerX + (sizeMm / 2f)
        val bottom: Float get() = centerY + (sizeMm / 2f)
    }

    /**
     * Exact sub-millimeter physical position of a bubble.
     */
    data class BubblePoint(
        val questionNum: Int,
        val optionIndex: Int,
        val optionChar: Char,
        val centerXMm: Float,
        val centerYMm: Float,
        val radiusMm: Float
    )

    /**
     * Pixel representation of a bubble in canonical warped space.
     */
    data class BubblePixelPoint(
        val questionNum: Int,
        val optionIndex: Int,
        val optionChar: Char,
        val centerXPx: Float,
        val centerYPx: Float,
        val radiusPx: Float
    )

    /**
     * Resolves the deterministic layout plan based on the actual question count.
     * All counts up to 200 questions fit strictly on a single A4 page.
     * Geometry remains identical and fixed regardless of whether totalQuestions is 10 or 200.
     */
    fun calculateLayout(totalQuestions: Int): OmrLayoutPlan {
        val count = totalQuestions.coerceIn(1, 200)
        return OmrLayoutPlan(
            numColumns = NUM_GRID_COLUMNS,
            rowsPerColumn = ROWS_PER_COLUMN,
            totalQuestions = count,
            columnWidthMm = COLUMN_WIDTH_MM,
            gridWidthMm = RIGHT_GRID_WIDTH_MM,
            gridHeightMm = ROWS_PER_COLUMN * ROW_PITCH_MM
        )
    }

    /**
     * Returns the 4 corner registration anchors in physical millimeter coordinates.
     */
    fun getAnchors(): List<AnchorPoint> {
        val half = ANCHOR_SIZE_MM / 2f
        val left = MARGIN_X_MM + half
        val right = PAGE_WIDTH_MM - MARGIN_X_MM - half
        val top = MARGIN_Y_MM + half
        val bottom = PAGE_HEIGHT_MM - MARGIN_Y_MM - half

        return listOf(
            AnchorPoint("TL", left, top, ANCHOR_SIZE_MM),
            AnchorPoint("TR", right, top, ANCHOR_SIZE_MM),
            AnchorPoint("BL", left, bottom, ANCHOR_SIZE_MM),
            AnchorPoint("BR", right, bottom, ANCHOR_SIZE_MM)
        )
    }

    /**
     * Computes the exact sub-millimeter physical centers for all candidate bubbles
     * across questions 1 to minOf(totalQuestions, 200).
     *
     * Coordinates are strictly deterministic and never shift between test sizes:
     * - Column 1: Questions 1..50
     * - Column 2: Questions 51..100
     * - Column 3: Questions 101..150
     * - Column 4: Questions 151..200
     */
    fun getBubbleCoordinates(totalQuestions: Int): List<BubblePoint> {
        val count = totalQuestions.coerceIn(1, MAX_SINGLE_PAGE_QUESTIONS)
        val bubbles = ArrayList<BubblePoint>(count * OPTIONS_PER_QUESTION)

        val gridStartX = RIGHT_GRID_START_X_MM
        val gridStartY = GRID_TOP_OFFSET_MM + COL_HEADER_HEIGHT_MM

        for (q in 1..count) {
            val colIndex = (q - 1) / ROWS_PER_COLUMN
            val rowIndex = (q - 1) % ROWS_PER_COLUMN

            val colStartX = gridStartX + (colIndex * (COLUMN_WIDTH_MM + COLUMN_GUTTER_MM))
            // Include small separation between Section A (1..35) and Section B (36..50)
            val sectionBOffsetMm = if (rowIndex >= SECTION_A_ROWS) 2.0f else 0.0f
            val rowCenterY = gridStartY + (rowIndex * ROW_PITCH_MM) + sectionBOffsetMm + (ROW_PITCH_MM / 2f)

            val firstBubbleCenterX = colStartX + QNUM_WIDTH_MM + QNUM_TO_BUBBLE_GAP_MM + BUBBLE_RADIUS_MM

            for (optIdx in 0 until OPTIONS_PER_QUESTION) {
                val bubbleCenterX = firstBubbleCenterX + (optIdx * BUBBLE_PITCH_X_MM)
                bubbles.add(
                    BubblePoint(
                        questionNum = q,
                        optionIndex = optIdx,
                        optionChar = OPTION_LABELS[optIdx],
                        centerXMm = bubbleCenterX,
                        centerYMm = rowCenterY,
                        radiusMm = BUBBLE_RADIUS_MM
                    )
                )
            }
        }

        return bubbles
    }

    /**
     * Converts physical millimeter bubble coordinates into canonical pixel space
     * (2100 x 2970 px) for direct sampling by the Computer Vision pipeline.
     */
    fun getCanonicalPixelCoordinates(
        totalQuestions: Int,
        scalePxPerMm: Float = CANONICAL_SCALE_PX_PER_MM
    ): List<BubblePixelPoint> {
        return getBubbleCoordinates(totalQuestions).map { bp ->
            BubblePixelPoint(
                questionNum = bp.questionNum,
                optionIndex = bp.optionIndex,
                optionChar = bp.optionChar,
                centerXPx = bp.centerXMm * scalePxPerMm,
                centerYPx = bp.centerYMm * scalePxPerMm,
                radiusPx = bp.radiusMm * scalePxPerMm
            )
        }
    }
}
