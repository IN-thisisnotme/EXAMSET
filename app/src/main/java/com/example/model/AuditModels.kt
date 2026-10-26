package com.example.model

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
 * mandatory rechecked explanations, and replacement MCQ packages.
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
    val isResolved: Boolean = false
) {
    val isActionable: Boolean
        get() = status != AuditIssueType.CLEAN && !isResolved

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
    val cleanCount: Int = 0
)
