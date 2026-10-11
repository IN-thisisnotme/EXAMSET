package com.example.model

/**
 * Centralized, Immutable OMR Sheet Geometry Specification.
 *
 * Modeled directly after the official NEET A4 examination answer sheet:
 * - Single A4 portrait page capacity strictly accommodating up to 200 MCQs.
 * - Left identification column:
 *   - Roll No. (10 digits with 0-9 bubbles)
 *   - Test Booklet No. (7 digits with 0-9 bubbles)
 *   - Test Booklet Code (Options A, B, C, D)
 *   - L-bracket Answer Sheet No. frame
 *   - Candidate declaration with signature
 *   - Candidate's Name in running handwriting
 * - Main right question grid divided into two distinct sections:
 *   - Section A: Upper grid with 35 questions per subject (Q 001-035, 051-085, 101-135, 151-185)
 *   - Section B: Lower grid with 15 questions per subject (Q 036-050, 086-100, 136-150, 186-200)
 *   - Exactly 4 vertical columns representing Physics, Chemistry, Botany, and Zoology.
 * - Bottom verification area:
 *   - Mother's Name (running handwriting)
 *   - Father's Name (running handwriting)
 *   - Candidate's Left Hand Thumb Impression box (crisp solid border)
 *   - Signature of Invigilator's with Time (Invigilator 1 and 2)
 *   - STRICTLY ZERO parental signature or parental consent fields.
 * - Fixed natural bubble geometry (3.8 mm diameter, 4.8 mm horizontal pitch, 4.0 mm row pitch).
 * - Period-free sequential question numbering (001 to 200) without any trailing dots.
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
    const val BOTTOM_AREA_HEIGHT_MM: Float = 22.0f

    // --- Left Panel Horizontal Budget ---
    const val LEFT_PANEL_WIDTH_MM: Float = 63.0f
    const val PANEL_GUTTER_MM: Float = 2.5f

    // --- Right Question Grid Budget ---
    val RIGHT_GRID_START_X_MM: Float get() = MARGIN_X_MM + LEFT_PANEL_WIDTH_MM + PANEL_GUTTER_MM // 69.5 mm
    val RIGHT_GRID_WIDTH_MM: Float get() = PRINTABLE_WIDTH_MM - LEFT_PANEL_WIDTH_MM - PANEL_GUTTER_MM // 136.5 mm

    // --- NEET 4-Column × 2-Section Structure ---
    const val NUM_GRID_COLUMNS: Int = 4
    const val SECTION_A_ROWS: Int = 35 // Questions 1..35 in each column
    const val SECTION_B_ROWS: Int = 15 // Questions 36..50 in each column
    const val QUESTIONS_PER_SUBJECT: Int = SECTION_A_ROWS + SECTION_B_ROWS // 50 questions
    const val MAX_SINGLE_PAGE_QUESTIONS: Int = NUM_GRID_COLUMNS * QUESTIONS_PER_SUBJECT // 200 questions

    const val SECTION_LABEL_WIDTH_MM: Float = 4.8f
    const val COLUMN_WIDTH_MM: Float = 31.0f
    const val COLUMN_GUTTER_MM: Float = 1.2f

    const val ROW_PITCH_MM: Float = 4.0f
    const val COL_HEADER_HEIGHT_MM: Float = 5.2f
    const val SECTION_GAP_MM: Float = 2.5f

    // Grid vertical anchors
    val GRID_TOP_OFFSET_MM: Float get() = MARGIN_Y_MM + HEADER_HEIGHT_MM + 1.0f // 19.0 mm
    val SECTION_A_GRID_START_Y_MM: Float get() = GRID_TOP_OFFSET_MM
    val SECTION_A_GRID_HEIGHT_MM: Float get() = COL_HEADER_HEIGHT_MM + (SECTION_A_ROWS * ROW_PITCH_MM) // 5.2 + 140.0 = 145.2 mm
    val SECTION_B_GRID_START_Y_MM: Float get() = SECTION_A_GRID_START_Y_MM + SECTION_A_GRID_HEIGHT_MM + SECTION_GAP_MM
    val SECTION_B_GRID_HEIGHT_MM: Float get() = COL_HEADER_HEIGHT_MM + (SECTION_B_ROWS * ROW_PITCH_MM) // 5.2 + 60.0 = 65.2 mm
    val TOTAL_GRID_HEIGHT_MM: Float get() = SECTION_A_GRID_HEIGHT_MM + SECTION_GAP_MM + SECTION_B_GRID_HEIGHT_MM // 212.9 mm

    // --- Bubble Geometry & Alignment ---
    const val BUBBLE_DIAMETER_MM: Float = 3.8f
    const val BUBBLE_RADIUS_MM: Float = BUBBLE_DIAMETER_MM / 2f
    const val BUBBLE_BORDER_THICKNESS_MM: Float = 0.35f
    const val BUBBLE_PITCH_X_MM: Float = 4.8f // 4.8 - 3.8 = 1.0 mm gap between bubbles
    const val QNUM_WIDTH_MM: Float = 5.8f
    const val QNUM_TO_BUBBLE_GAP_MM: Float = 1.4f

    const val OPTIONS_PER_QUESTION: Int = 4
    val OPTION_LABELS: List<Char> = listOf('A', 'B', 'C', 'D')
    val OPTION_DIGITS: List<Char> = listOf('1', '2', '3', '4')

    // Official NEET drop-out ink palette
    const val THEME_COLOR_HEX: String = "#be185d"
    const val THEME_LIGHT_BG_HEX: String = "#fdf2f8"
    const val THEME_BORDER_HEX: String = "#be185d"

    // Canonical Vision Coordinate Space (10 pixels per mm = 2100 x 2970 px)
    const val CANONICAL_SCALE_PX_PER_MM: Float = 10.0f
    val CANONICAL_WIDTH_PX: Int = (PAGE_WIDTH_MM * CANONICAL_SCALE_PX_PER_MM).toInt() // 2100 px
    val CANONICAL_HEIGHT_PX: Int = (PAGE_HEIGHT_MM * CANONICAL_SCALE_PX_PER_MM).toInt() // 2970 px

    data class OmrLayoutPlan(
        val numColumns: Int,
        val rowsSectionA: Int,
        val rowsSectionB: Int,
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

    /**
     * Resolves the deterministic layout plan based on total questions (strictly single page up to 200).
     */
    fun calculateLayout(totalQuestions: Int): OmrLayoutPlan {
        val count = totalQuestions.coerceIn(1, MAX_SINGLE_PAGE_QUESTIONS)
        return OmrLayoutPlan(
            numColumns = NUM_GRID_COLUMNS,
            rowsSectionA = SECTION_A_ROWS,
            rowsSectionB = SECTION_B_ROWS,
            totalQuestions = count,
            columnWidthMm = COLUMN_WIDTH_MM,
            gridWidthMm = RIGHT_GRID_WIDTH_MM,
            totalGridHeightMm = TOTAL_GRID_HEIGHT_MM
        )
    }

    /**
     * Maps question number (1..200) to its subject column and row within Section A or B.
     */
    fun getQuestionMapping(q: Int): Triple<Int, Boolean, Int> {
        val subjectIndex = (q - 1) / QUESTIONS_PER_SUBJECT // 0, 1, 2, 3
        val qInSubject = (q - 1) % QUESTIONS_PER_SUBJECT + 1 // 1..50
        val isSectionA = qInSubject <= SECTION_A_ROWS // 1..35
        val rowIndex = if (isSectionA) (qInSubject - 1) else (qInSubject - SECTION_A_ROWS - 1) // 0..34 or 0..14
        return Triple(subjectIndex, isSectionA, rowIndex)
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
     * In the authentic NEET answer sheet:
     * - Section A:
     *   - Column 0: Questions 001..035 (Physics)
     *   - Column 1: Questions 051..085 (Chemistry)
     *   - Column 2: Questions 101..135 (Botany)
     *   - Column 3: Questions 151..185 (Zoology)
     * - Section B:
     *   - Column 0: Questions 036..050 (Physics)
     *   - Column 1: Questions 086..100 (Chemistry)
     *   - Column 2: Questions 136..150 (Botany)
     *   - Column 3: Questions 186..200 (Zoology)
     */
    fun getBubbleCoordinates(totalQuestions: Int): List<BubblePoint> {
        val count = totalQuestions.coerceIn(1, MAX_SINGLE_PAGE_QUESTIONS)
        val bubbles = ArrayList<BubblePoint>(count * OPTIONS_PER_QUESTION)

        val columnsStartX = RIGHT_GRID_START_X_MM + SECTION_LABEL_WIDTH_MM

        for (q in 1..count) {
            val (subjectIndex, isSectionA, rowIndex) = getQuestionMapping(q)

            val colStartX = columnsStartX + (subjectIndex * (COLUMN_WIDTH_MM + COLUMN_GUTTER_MM))
            val gridStartY = if (isSectionA) SECTION_A_GRID_START_Y_MM else SECTION_B_GRID_START_Y_MM
            val rowCenterY = gridStartY + COL_HEADER_HEIGHT_MM + (rowIndex * ROW_PITCH_MM) + (ROW_PITCH_MM / 2f)

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
}
