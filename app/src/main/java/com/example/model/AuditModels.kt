package com.example.model

/**
 * Three-phase stepped auditing workflow for token-efficient, high-precision review.
 */
enum class AuditPhase(val stepNumber: Int, val title: String, val shortTitle: String) {
    PHASE_1_KEYS(1, "Phase 1: Answer Key & Flaw Flagging", "Step 1: Keys & Flaws"),
    PHASE_2_REPLACEMENTS(2, "Phase 2: Flawed Question Replacements", "Step 2: Replacements"),
    PHASE_3_EXPLANATIONS(3, "Phase 3: Explanation Review & Seal", "Step 3: Explanations")
}

/**
 * Categorization of audit issues detected in a question paper.
 */
enum class AuditIssueType {
    WRONG_KEY,
    MISSING_KEY,
    FLAWED_QUESTION,
    VAGUE_OPTIONS,
    MISSING_OPTIONS,
    MISSING_EXPLANATION,
    CLEAN
}

/**
 * Individual question audit report detailing errors, suggested fixes,
 * mandatory rechecked explanations, replacement MCQ packages, and verification seals.
 */
data class AuditQuestionReport(
    val qNum: Int,
    val status: AuditIssueType,
    val currentKey: String? = null,
    val suggestedKey: String? = null,
    val flawReason: String? = null,
    val recheckedExplanation: String? = null,
    val replacementQuestion: McqQuestion? = null,
    val originalQuestion: McqQuestion? = null,
    val isResolved: Boolean = false,
    val isVerified: Boolean = false,
    val isNeutralized: Boolean = false
) {
    val isActionable: Boolean
        get() = status != AuditIssueType.CLEAN && !isResolved && !isNeutralized

    val isKeyError: Boolean
        get() = status == AuditIssueType.WRONG_KEY || status == AuditIssueType.MISSING_KEY

    val isFlawedQuestion: Boolean
        get() = status == AuditIssueType.FLAWED_QUESTION ||
                status == AuditIssueType.VAGUE_OPTIONS ||
                status == AuditIssueType.MISSING_OPTIONS
}

/**
 * High-level summary metrics of an audit run.
 */
data class AuditSummaryMetrics(
    val totalAudited: Int = 0,
    val wrongKeysCount: Int = 0,
    val missingKeysCount: Int = 0,
    val flawedQuestionsCount: Int = 0,
    val missingOptionsCount: Int = 0,
    val cleanCount: Int = 0,
    val verifiedCount: Int = 0,
    val neutralizedCount: Int = 0
)

