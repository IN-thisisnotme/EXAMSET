package com.example.model

enum class SrsRating(
    val label: String,
    val quality: Int,
    val subtitle: String
) {
    AGAIN("Again", 1, "Repeat soon"),
    HARD("Hard", 3, "Short step"),
    GOOD("Good", 4, "Standard SM-2"),
    EASY("Easy", 5, "Boost interval")
}

enum class FlashcardSrsStatus(val label: String) {
    NEW("New"),
    LEARNING("Learning"),
    DUE("Due"),
    MASTERED("Mastered")
}

data class FlashcardSrsState(
    val blockIndex: Int,
    val repetitions: Int = 0,
    val easeFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val dueAtMillis: Long = 0L,
    val lastReviewedAtMillis: Long = 0L,
    val lapses: Int = 0,
    val lastRating: SrsRating? = null,
    val reviewCount: Int = 0,
    val correctCount: Int = 0
)

data class DeckSrsStats(
    val totalCount: Int = 0,
    val dueCount: Int = 0,
    val newCount: Int = 0,
    val learningCount: Int = 0,
    val masteredCount: Int = 0,
    val retentionPercent: Int = 0
) {
    val actionableCount: Int
        get() = dueCount + newCount
}

enum class FlashcardPromptStyle(
    val label: String,
    val shortBadge: String,
    val description: String
) {
    QA_RETRIEVAL(
        label = "Q & A Active Recall",
        shortBadge = "Q & A CARDS",
        description = "Crisp question on Front (`Q:`) and direct retrieval answer on Back (`A:`)"
    ),
    TERM_DEFINITION(
        label = "Term -> Definition",
        shortBadge = "TERM DECK",
        description = "Key concept or law on Front (`1. **Term** ->`) with concise core meaning on Back"
    ),
    CLOZE_SCENARIO(
        label = "Scenario / Clue -> Rule",
        shortBadge = "CLUE -> RULE",
        description = "Diagnostic clue or trap symptom on Front leading to the exact target concept"
    ),
    FORMULA_TRAP(
        label = "Formula & Exam Trap",
        shortBadge = "FORMULA SRS",
        description = "Equation name on Front with LaTeX formula, units, and pitfall bullets on Back"
    )
}
