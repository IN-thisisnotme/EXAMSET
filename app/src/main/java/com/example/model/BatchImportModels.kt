package com.example.model

/**
 * Severity level for validation issues found in exam paper JSON files.
 */
enum class ValidationSeverity {
    ERROR,   // Critical: prevents paper from being imported (e.g. malformed JSON, 0 questions)
    WARNING, // Important: paper can be imported, but has missing keys or options
    INFO     // Informational: minor note (e.g. defaulted title or missing explanations)
}

/**
 * An individual validation issue found in an exam paper or question.
 */
data class ValidationIssue(
    val severity: ValidationSeverity,
    val message: String,
    val questionNumber: Int? = null
)

/**
 * Overall validation status of a batch item.
 */
enum class BatchItemStatus {
    VALID,      // Passes all checks with 0 errors or warnings
    WARNING,    // Minor issues (e.g. missing explanations/answers), but safe to import
    INVALID     // Fatal errors (e.g. malformed JSON or 0 questions), cannot be imported
}

/**
 * Represents a single file or parsed exam paper in the batch import queue.
 */
data class BatchImportItem(
    val id: String,
    val fileName: String,
    val fileSizeBytes: Long = 0L,
    val fileSizeFormatted: String = "",
    val paper: QuestionPaper? = null,
    val status: BatchItemStatus = BatchItemStatus.VALID,
    val issues: List<ValidationIssue> = emptyList(),
    val isSelected: Boolean = true,
    val customSubject: String? = null,
    val rawJsonPreview: String = ""
) {
    val errorCount: Int get() = issues.count { it.severity == ValidationSeverity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == ValidationSeverity.WARNING }
    val infoCount: Int get() = issues.count { it.severity == ValidationSeverity.INFO }
}

/**
 * Summary metrics for bulk validation results across all items.
 */
data class BatchValidationSummary(
    val totalFiles: Int = 0,
    val totalPapers: Int = 0,
    val validCount: Int = 0,
    val warningCount: Int = 0,
    val invalidCount: Int = 0,
    val totalQuestions: Int = 0
)

/**
 * Result returned after batch importing papers into Room database.
 */
data class BatchImportResult(
    val importedCount: Int = 0,
    val totalQuestions: Int = 0,
    val targetFolder: String = "",
    val failedCount: Int = 0
)
