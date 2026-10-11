package com.example.model

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
    val formula: String? = null
)

/**
 * Extracted header metadata.
 */
data class PaperMetadata(
    val id: String = "",
    val title: String = "MCQ Practice Set",
    val subject: String = "Objective Multiple Choice Questions",
    val topic: String = "",
    val setNumber: String = "Set A",
    val exam: String = "",
    val folderId: String? = null,
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
    val createdAt: Long = System.currentTimeMillis()
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

data class ExamConfig(
    val institutionName: String = "CENTRAL EXAMINATION BOARD",
    val examTitle: String = "OBJECTIVE PRACTICE & EVALUATION TEST",
    val subject: String = "",
    val topic: String = "",
    val seriesCode: String = "SET - A",
    val timeAllowed: String = "60 Minutes",
    val maxMarks: String = "50",
    val negativeMarking: String = "0.33 Marks (-0.10 Unattended)",
    val includeCandidateDetails: Boolean = true,
    val includeInstructions: Boolean = true,
    val includeOmrSheet: Boolean = true,
    val twoColumns: Boolean = true,
    val printMode: PrintMode = PrintMode.STUDENT_PAPER,
    val customInstructions: List<String> = listOf(
        "Check that this question paper contains all pages and questions in sequential order.",
        "Each question carries equal marks (+1.00). Choose the single most appropriate option (A, B, C, or D).",
        "Penalty marking: -0.33 mark deducted for each incorrect answer, -0.10 mark for each unattended question.",
        "Darken the corresponding oval on the OMR Answer Sheet using Blue/Black ballpoint pen.",
        "Rough work may only be done on the designated space provided at the end of this booklet.",
        "Do not leave the examination hall until your answer sheet is collected by the invigilator."
    )
)
