package com.example.model

/**
 * Centralized, Immutable OMR Sheet Geometry Specification.
 *
 * Professional Single-Page A4 Examination Answer Sheet (up to 200 MCQs):
 * - Left identification column:
 *   - Roll No. (10 digits with digit boxes and 0-9 bubbles)
 *   - Test Booklet No. (7 digits with digit boxes and 0-9 bubbles)
 *   - Test Booklet Code (Options A, B, C, D)
 *   - L-bracket Answer Sheet No. frame
 *   - Candidate declaration with signature and time
 *   - Candidate's Name (in running handwriting)
 * - Main right question grid:
 *   - Continuous, section-free 4 vertical columns of 50 questions each (001-050, 051-100, 101-150, 151-200)
 *   - Exactly 4 circular response bubbles (1, 2, 3, 4) per question.
 *   - Period-free sequential question numbering (001 to 200) without gaps or section partitions.
 * - Bottom verification area:
 *   - Candidate's Left Hand Thumb Impression box (crisp solid border)
 *   - Signature of Invigilator's with Time (Invigilator 1 and 2)
 *   - STRICTLY ZERO parental fields or signatures (redundant fields removed).
 * - Fixed natural bubble geometry (3.8 mm diameter, 4.8 mm horizontal pitch, 4.1 mm row pitch).
 */
object OmrGeometry {

    // --- Physical ISO A4 Page Geometry ---
    const val PAGE_WIDTH_MM: Float = 210.0f
    const val PAGE_HEIGHT_MM: Float = 297.0f
    const val MARGIN_X_MM: Float = 4.0f
    const val MARGIN_Y_MM: Float = 4.0f

    val PRINTABLE_WIDTH_MM: Float get() = PAGE_WIDTH_MM - (2f * MARGIN_X_MM) // 202.0 mm
    val PRINTABLE_HEIGHT_MM: Float get() = PAGE_HEIGHT_MM - (2f * MARGIN_Y_MM) // 289.0 mm

    // --- Corner Registration Fiducial Anchors (for scanning and perspective correction) ---
    const val ANCHOR_SIZE_MM: Float = 6.0f
    const val ANCHOR_MARGIN_MM: Float = 2.0f

    // --- Layout Proportions ---
    const val HEADER_HEIGHT_MM: Float = 14.0f
    const val BOTTOM_AREA_HEIGHT_MM: Float = 18.0f

    // --- Left Panel Horizontal Budget ---
    const val LEFT_PANEL_WIDTH_MM: Float = 63.0f
    const val PANEL_GUTTER_MM: Float = 3.0f

    // --- Right Question Grid Budget ---
    val RIGHT_GRID_START_X_MM: Float get() = MARGIN_X_MM + LEFT_PANEL_WIDTH_MM + PANEL_GUTTER_MM // 70.0 mm
    val RIGHT_GRID_WIDTH_MM: Float get() = PRINTABLE_WIDTH_MM - LEFT_PANEL_WIDTH_MM - PANEL_GUTTER_MM // 136.0 mm

    // --- 4-Column Continuous Structure (No Section Partitions) ---
    const val NUM_GRID_COLUMNS: Int = 4
    const val ROWS_PER_COLUMN: Int = 50 // Exactly 50 questions per column: 1..50, 51..100, 101..150, 151..200
    const val MAX_SINGLE_PAGE_QUESTIONS: Int = NUM_GRID_COLUMNS * ROWS_PER_COLUMN // 200 questions

    const val COLUMN_WIDTH_MM: Float = 32.5f
    const val COLUMN_GUTTER_MM: Float = 1.5f

    const val ROW_PITCH_MM: Float = 4.1f
    const val COL_HEADER_HEIGHT_MM: Float = 5.4f

    // Grid vertical anchors
    val GRID_TOP_OFFSET_MM: Float get() = MARGIN_Y_MM + HEADER_HEIGHT_MM + 1.0f // 19.0 mm
    val GRID_HEIGHT_MM: Float get() = COL_HEADER_HEIGHT_MM + (ROWS_PER_COLUMN * ROW_PITCH_MM) // 5.4 + 205.0 = 210.4 mm

    // --- Bubble Geometry & Alignment ---
    const val BUBBLE_DIAMETER_MM: Float = 3.8f
    const val BUBBLE_RADIUS_MM: Float = BUBBLE_DIAMETER_MM / 2f
    const val BUBBLE_BORDER_THICKNESS_MM: Float = 0.35f
    const val BUBBLE_PITCH_X_MM: Float = 5.6f // 5.6 - 3.8 = 1.8 mm gap between bubbles
    const val QNUM_WIDTH_MM: Float = 6.0f
    const val QNUM_TO_BUBBLE_GAP_MM: Float = 1.6f

    const val OPTIONS_PER_QUESTION: Int = 4
    val OPTION_LABELS: List<Char> = listOf('A', 'B', 'C', 'D')
    val OPTION_DIGITS: List<Char> = listOf('1', '2', '3', '4')

    // Authentic drop-out ink palette
    const val THEME_COLOR_HEX: String = "#be185d"
    const val THEME_LIGHT_BG_HEX: String = "#fdf2f8"
    const val THEME_BORDER_HEX: String = "#be185d"

    // Canonical Vision Coordinate Space (10 pixels per mm = 2100 x 2970 px)
    const val CANONICAL_SCALE_PX_PER_MM: Float = 10.0f
    val CANONICAL_WIDTH_PX: Int = (PAGE_WIDTH_MM * CANONICAL_SCALE_PX_PER_MM).toInt() // 2100 px
    val CANONICAL_HEIGHT_PX: Int = (PAGE_HEIGHT_MM * CANONICAL_SCALE_PX_PER_MM).toInt() // 2970 px

    data class OmrLayoutPlan(
        val numColumns: Int,
        val rowsPerColumn: Int,
        val totalQuestions: Int,
        val columnWidthMm: Float,
        val gridWidthMm: Float,
        val totalGridHeightMm: Float
    )

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

    data class BubblePoint(
        val questionNum: Int,
        val optionIndex: Int,
        val optionChar: Char,
        val centerXMm: Float,
        val centerYMm: Float,
        val radiusMm: Float
    )

    data class BubblePixelPoint(
        val questionNum: Int,
        val optionIndex: Int,
        val optionChar: Char,
        val centerXPx: Float,
        val centerYPx: Float,
        val radiusPx: Float
    )

    enum class HeaderFieldType {
        ROLL_NUMBER,
        BOOKLET_NUMBER,
        BOOKLET_CODE
    }

    data class HeaderBubblePoint(
        val fieldType: HeaderFieldType,
        val columnIndex: Int,
        val digitOrChar: Char,
        val centerXMm: Float,
        val centerYMm: Float,
        val radiusMm: Float
    )

    data class HeaderBubblePixelPoint(
        val fieldType: HeaderFieldType,
        val columnIndex: Int,
        val digitOrChar: Char,
        val centerXPx: Float,
        val centerYPx: Float,
        val radiusPx: Float
    )

    /**
     * Resolves the deterministic layout plan based on total questions (strictly single page up to 200).
     */
    fun calculateLayout(totalQuestions: Int): OmrLayoutPlan {
        val count = totalQuestions.coerceIn(1, MAX_SINGLE_PAGE_QUESTIONS)
        return OmrLayoutPlan(
            numColumns = NUM_GRID_COLUMNS,
            rowsPerColumn = ROWS_PER_COLUMN,
            totalQuestions = count,
            columnWidthMm = COLUMN_WIDTH_MM,
            gridWidthMm = RIGHT_GRID_WIDTH_MM,
            totalGridHeightMm = GRID_HEIGHT_MM
        )
    }

    /**
     * Maps question number (1..200) to its column (0..3) and row (0..49) within the continuous grid.
     */
    fun getQuestionMapping(q: Int): Pair<Int, Int> {
        val columnIndex = (q - 1) / ROWS_PER_COLUMN // 0, 1, 2, 3
        val rowIndex = (q - 1) % ROWS_PER_COLUMN // 0..49
        return Pair(columnIndex, rowIndex)
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
     * Computes exact sub-millimeter physical centers for all candidate bubbles
     * across questions 1 to minOf(totalQuestions, 200).
     *
     * In this section-free 200-question layout:
     * - Column 0: Questions 001..050
     * - Column 1: Questions 051..100
     * - Column 2: Questions 101..150
     * - Column 3: Questions 151..200
     */
    fun getBubbleCoordinates(totalQuestions: Int): List<BubblePoint> {
        val count = totalQuestions.coerceIn(1, MAX_SINGLE_PAGE_QUESTIONS)
        val bubbles = ArrayList<BubblePoint>(count * OPTIONS_PER_QUESTION)

        val columnsStartX = RIGHT_GRID_START_X_MM

        for (q in 1..count) {
            val (columnIndex, rowIndex) = getQuestionMapping(q)

            val colStartX = columnsStartX + (columnIndex * (COLUMN_WIDTH_MM + COLUMN_GUTTER_MM))
            val rowCenterY = GRID_TOP_OFFSET_MM + COL_HEADER_HEIGHT_MM + (rowIndex * ROW_PITCH_MM) + (ROW_PITCH_MM / 2f)

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
     * Converts physical millimeter bubble coordinates into canonical pixel space (2100 x 2970 px).
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

    /**
     * Computes exact physical millimeter coordinates for Left Panel header bubbles:
     * - Roll Number: 10 columns (0..9) x 10 rows (digits 0..9)
     * - Booklet Number: 7 columns (0..6) x 10 rows (digits 0..9)
     * - Booklet Code: options A, B, C, D (or custom cohort sets)
     */
    fun getHeaderBubbleCoordinates(sets: List<String> = listOf("A", "B", "C", "D")): List<HeaderBubblePoint> {
        val list = ArrayList<HeaderBubblePoint>(200)
        val leftPanelStart = MARGIN_X_MM + 2.0f
        val microRadius = 1.65f // 3.3mm diameter micro bubble

        // 1. Roll Number 10 columns x 10 rows (0..9)
        val rollColPitch = (LEFT_PANEL_WIDTH_MM - 4.0f) / 10f
        val rollTopStart = GRID_TOP_OFFSET_MM + 9.5f
        val rollRowPitch = 3.65f
        for (c in 0 until 10) {
            val cx = leftPanelStart + 2.0f + (c * rollColPitch) + (rollColPitch / 2f)
            for (d in 0..9) {
                val cy = rollTopStart + (d * rollRowPitch) + (rollRowPitch / 2f)
                list.add(
                    HeaderBubblePoint(
                        fieldType = HeaderFieldType.ROLL_NUMBER,
                        columnIndex = c,
                        digitOrChar = ('0' + d),
                        centerXMm = cx,
                        centerYMm = cy,
                        radiusMm = microRadius
                    )
                )
            }
        }

        // 2. Booklet Number 7 columns x 10 rows (0..9)
        val bookletColPitch = (LEFT_PANEL_WIDTH_MM - 6.0f) / 7f
        val bookletTopStart = rollTopStart + (10 * rollRowPitch) + 9.0f
        val bookletRowPitch = 3.65f
        for (c in 0 until 7) {
            val cx = leftPanelStart + 3.0f + (c * bookletColPitch) + (bookletColPitch / 2f)
            for (d in 0..9) {
                val cy = bookletTopStart + (d * bookletRowPitch) + (bookletRowPitch / 2f)
                list.add(
                    HeaderBubblePoint(
                        fieldType = HeaderFieldType.BOOKLET_NUMBER,
                        columnIndex = c,
                        digitOrChar = ('0' + d),
                        centerXMm = cx,
                        centerYMm = cy,
                        radiusMm = microRadius
                    )
                )
            }
        }

        // 3. Set Number / Booklet Code: Strictly 2 numeric columns (Tens & Units), 10 rows (0..9)
        val setColPitch = 6.0f
        val setTopStart = bookletTopStart + (10 * bookletRowPitch) + 9.0f
        val setStartX = leftPanelStart + (LEFT_PANEL_WIDTH_MM - (2 * setColPitch)) / 2f
        for (c in 0 until 2) {
            val cx = setStartX + (c * setColPitch) + (setColPitch / 2f)
            for (d in 0..9) {
                val cy = setTopStart + (d * bookletRowPitch) + (bookletRowPitch / 2f)
                list.add(
                    HeaderBubblePoint(
                        fieldType = HeaderFieldType.BOOKLET_CODE,
                        columnIndex = c,
                        digitOrChar = ('0' + d),
                        centerXMm = cx,
                        centerYMm = cy,
                        radiusMm = microRadius
                    )
                )
            }
        }

        return list
    }

    /**
     * Converts physical millimeter header bubble coordinates into canonical pixel space.
     */
    fun getCanonicalHeaderPixelCoordinates(
        scalePxPerMm: Float = CANONICAL_SCALE_PX_PER_MM
    ): List<HeaderBubblePixelPoint> {
        return getHeaderBubbleCoordinates().map { hp ->
            HeaderBubblePixelPoint(
                fieldType = hp.fieldType,
                columnIndex = hp.columnIndex,
                digitOrChar = hp.digitOrChar,
                centerXPx = hp.centerXMm * scalePxPerMm,
                centerYPx = hp.centerYMm * scalePxPerMm,
                radiusPx = hp.radiusMm * scalePxPerMm
            )
        }
    }
}

