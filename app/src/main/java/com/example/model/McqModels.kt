package com.example.model

import com.example.util.ExamNumberGenerator

/**
 * Options A, B, C, D for a Multiple Choice Question.
 */
data class QuestionOptions(
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val d: String = "",
    val e: String = ""
) {
    operator fun get(letter: String): String = when (letter.uppercase()) {
        "A" -> a
        "B" -> b
        "C" -> c
        "D" -> d
        "E" -> e
        else -> ""
    }

    val asList: List<Pair<String, String>>
        get() = if (e.isNotBlank()) {
            listOf("A" to a, "B" to b, "C" to c, "D" to d, "E" to e)
        } else {
            listOf("A" to a, "B" to b, "C" to c, "D" to d)
        }

    val missingOptions: List<String>
        get() = buildList {
            if (a.isBlank()) add("A")
            if (b.isBlank()) add("B")
            if (c.isBlank()) add("C")
            if (d.isBlank() && e.isBlank()) add("D")
        }

    val hasMissingOptions: Boolean
        get() = missingOptions.isNotEmpty()

    val isValid: Boolean
        get() = a.isNotBlank() && b.isNotBlank() && c.isNotBlank() && (d.isNotBlank() || e.isNotBlank())
}

/**
 * Individual parsed MCQ item.
 */
data class McqQuestion(
    val num: Int,
    val question: String,
    val options: QuestionOptions,
    val correctAnswer: String? = null,
    val explanation: String? = null,
    val questionType: String? = null,
    val diagramUrl: String? = null,
    val diagramLabel: String? = null,
    val formula: String? = null,
    val isNeutralized: Boolean = false,
    val isVerified: Boolean = false
) {
    val hasMissingOptions: Boolean
        get() = options.hasMissingOptions
}

/**
 * Explicit user-assignable or inferred classification for a question paper or study asset.
 */
enum class PaperCategory(val code: String, val label: String) {
    AUTO("AUTO", "Auto Detect"),
    MOCK_TEST("MOCK_TEST", "Mock Test"),
    PYQ("PYQ", "Previous Year (PYQ)"),
    SECTIONAL("SECTIONAL", "Sectional / Drill"),
    STUDY_NOTES("STUDY_NOTES", "Study Notes");

    companion object {
        fun fromCode(code: String?): PaperCategory {
            val normalized = code?.trim()?.uppercase() ?: return AUTO
            return entries.firstOrNull { it.code == normalized } ?: AUTO
        }
    }
}

/**
 * Grading policy applied to Multiple-Selection Questions (MSQ) with 2+ correct options.
 */
enum class MsqScoringPolicy(val code: String, val label: String, val shortLabel: String) {
    ALL_OR_NOTHING(
        code = "ALL_OR_NOTHING",
        label = "All-or-Nothing (Full Marks Only)",
        shortLabel = "All-or-Nothing"
    ),
    PARTIAL_STRICT(
        code = "PARTIAL_STRICT",
        label = "Partial Credit (No Wrong Choices)",
        shortLabel = "Partial Strict"
    );

    companion object {
        fun fromCode(code: String?): MsqScoringPolicy {
            val normalized = code?.trim()?.uppercase() ?: return ALL_OR_NOTHING
            return entries.firstOrNull { it.code == normalized || it.name == normalized } ?: ALL_OR_NOTHING
        }
    }
}

/**
 * Extracted header metadata.
 */
data class PaperMetadata(
    val id: String = "",
    val title: String = "MCQ Practice Set",
    val subject: String = "Objective Multiple Choice Questions",
    val topic: String = "",
    val setNumber: String = "01",
    val exam: String = "",
    val folderId: String? = null,
    val manualCategory: String = "",
    val rollNumber: String = ExamNumberGenerator.generateRollNumber(),
    val bookletNumber: String = ExamNumberGenerator.generateBookletNumber(),
    val durationMinutes: Int = 60,
    val positiveMarks: Double = 4.0,
    val negativeMarks: Double = 1.0,
    val unattendedMarks: Double = 0.0,
    val msqScoringPolicy: MsqScoringPolicy = MsqScoringPolicy.ALL_OR_NOTHING,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Full Question Paper representation.
 */
data class QuestionPaper(
    val id: Long = 0,
    val metadata: PaperMetadata,
    val questions: List<McqQuestion>,
    val quickAnswersCount: Int = 0,
    val explanationsCount: Int = 0,
    val rawText: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Live parsing telemetry and diagnostic result matching src/types/exam.ts
 */
data class ParseResult(
    val paper: QuestionPaper,
    val rawCount: Int,
    val hasAnswerKeys: Boolean,
    val hasExplanations: Boolean,
    val warnings: List<String> = emptyList()
)

/**
 * Folder item for organizing question papers into categories.
 */
data class FolderItem(
    val id: String,
    val name: String,
    val color: String,
    val parentId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

/**
 * Real-time autosave status
 */
enum class AutoSaveStatus {
    IDLE,
    SAVING,
    SAVED,
    ERROR
}

/**
 * Print & Student Examination Configuration.
 */
enum class PrintMode {
    STUDENT_PAPER,     // Questions only + Candidate details + Instructions + optional OMR (for students)
    OMR_ONLY,          // Standalone single-page A4 OMR Response Sheet
    MASTER_BOOKLET,    // Navy Header + Questions + Answer Key Grid + Detailed Explanations (Full reference)
    SOLUTIONS_ONLY     // Answer Key Table + Detailed Explanations
}

/**
 * OMR Sheet Bubbling Mode for Roll Number and Test Booklet Number
 */
enum class OmrBubblingMode {
    BLANK_CANDIDATE,       // Blank digit boxes, open 0-9 bubbles for candidate to fill with pen
    PRE_PRINTED_DIGITS,    // Digits printed inside top boxes, open 0-9 bubbles
    PRE_DARKENED_BUBBLES   // Digits printed inside top boxes AND darkened bubbles in 0-9 grids
}

data class ExamConfig(
    val institutionName: String = "CENTRAL EXAMINATION BOARD",
    val examTitle: String = "OBJECTIVE PRACTICE & EVALUATION TEST",
    val subject: String = "",
    val topic: String = "",
    val seriesCode: String = "SET - A",
    val timeAllowed: String = "Auto (1 min/question)",
    val durationMinutes: Int = 60,
    val maxMarks: String = "50",
    val positiveMarks: Double = 4.0,
    val negativeMarks: Double = 1.0,
    val unattendedMarks: Double = 0.0,
    val msqScoringPolicy: MsqScoringPolicy = MsqScoringPolicy.ALL_OR_NOTHING,
    val negativeMarking: String = "1.00 Mark Deduction",
    val includeCandidateDetails: Boolean = true,
    val includeInstructions: Boolean = true,
    val includeOmrSheet: Boolean = true,
    val twoColumns: Boolean = true,
    val printMode: PrintMode = PrintMode.STUDENT_PAPER,
    val rollNumber: String = "",
    val bookletNumber: String = "",
    val omrBubblingMode: OmrBubblingMode = OmrBubblingMode.BLANK_CANDIDATE,
    val customInstructions: List<String> = listOf(
        "Check that this question paper contains all pages and questions in sequential order.",
        "Each question carries equal marks (+1.00). Choose the single most appropriate option (A, B, C, or D).",
        "Penalty marking: Deductions for incorrect answers apply as configured. Unattempted questions carry zero penalty unless specified.",
        "Darken the corresponding oval on the OMR Answer Sheet using Blue/Black ballpoint pen.",
        "Rough work may only be done on the designated space provided at the end of this booklet.",
        "Do not leave the examination hall until your answer sheet is collected by the invigilator."
    )
)
