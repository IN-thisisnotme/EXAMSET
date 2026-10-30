package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.BatchImportItem
import com.example.model.BatchItemStatus
import com.example.model.BatchValidationSummary
import com.example.model.McqQuestion
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import com.example.model.ValidationIssue
import com.example.model.ValidationSeverity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/**
 * Utility for parsing and bulk validating multiple JSON exam papers simultaneously.
 */
object BatchExamImporter {

    /**
     * Reads a list of Uris via ContentResolver and bulk-validates all exam papers found.
     */
    fun readAndValidateUris(context: Context, uris: List<Uri>): List<BatchImportItem> {
        val results = mutableListOf<BatchImportItem>()

        uris.forEachIndexed { index, uri ->
            val fileName = queryFileName(context, uri) ?: "exam_paper_${index + 1}.json"
            val fileSizeBytes = queryFileSize(context, uri)
            val fileSizeFormatted = formatFileSize(fileSizeBytes)

            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""

                if (jsonString.isBlank()) {
                    results.add(
                        BatchImportItem(
                            id = UUID.randomUUID().toString(),
                            fileName = fileName,
                            fileSizeBytes = fileSizeBytes,
                            fileSizeFormatted = fileSizeFormatted,
                            paper = null,
                            status = BatchItemStatus.INVALID,
                            issues = listOf(
                                ValidationIssue(
                                    severity = ValidationSeverity.ERROR,
                                    message = "File is completely empty."
                                )
                            ),
                            isSelected = false
                        )
                    )
                } else {
                    val parsedItems = validatePaperJson(
                        jsonStr = jsonString,
                        fileName = fileName,
                        fileSizeBytes = fileSizeBytes
                    )
                    results.addAll(parsedItems)
                }
            } catch (e: Exception) {
                results.add(
                    BatchImportItem(
                        id = UUID.randomUUID().toString(),
                        fileName = fileName,
                        fileSizeBytes = fileSizeBytes,
                        fileSizeFormatted = fileSizeFormatted,
                        paper = null,
                        status = BatchItemStatus.INVALID,
                        issues = listOf(
                            ValidationIssue(
                                severity = ValidationSeverity.ERROR,
                                message = "Unable to read file: ${e.localizedMessage ?: "Unknown I/O error"}"
                            )
                        ),
                        isSelected = false
                    )
                )
            }
        }

        return results
    }

    /**
     * Parses and validates raw JSON text.
     * Can contain a single exam paper object or a JSON array of multiple papers.
     */
    fun validatePaperJson(
        jsonStr: String,
        fileName: String = "paper.json",
        fileSizeBytes: Long = 0L
    ): List<BatchImportItem> {
        val trimmed = jsonStr.trim()
        val formattedSize = if (fileSizeBytes > 0) formatFileSize(fileSizeBytes) else formatFileSize(trimmed.toByteArray().size.toLong())

        if (trimmed.isEmpty()) {
            return listOf(
                BatchImportItem(
                    id = UUID.randomUUID().toString(),
                    fileName = fileName,
                    fileSizeBytes = fileSizeBytes,
                    fileSizeFormatted = formattedSize,
                    paper = null,
                    status = BatchItemStatus.INVALID,
                    issues = listOf(
                        ValidationIssue(ValidationSeverity.ERROR, "JSON content is empty.")
                    ),
                    isSelected = false
                )
            )
        }

        // Check if top-level is JSON array of papers
        if (trimmed.startsWith("[")) {
            return try {
                val jsonArray = JSONArray(trimmed)
                if (jsonArray.length() == 0) {
                    listOf(
                        BatchImportItem(
                            id = UUID.randomUUID().toString(),
                            fileName = fileName,
                            fileSizeBytes = fileSizeBytes,
                            fileSizeFormatted = formattedSize,
                            paper = null,
                            status = BatchItemStatus.INVALID,
                            issues = listOf(
                                ValidationIssue(ValidationSeverity.ERROR, "JSON array contains 0 elements.")
                            ),
                            isSelected = false
                        )
                    )
                } else {
                    // Check if elements are questions or paper objects
                    val firstElement = jsonArray.opt(0)
                    if (firstElement is JSONObject && !firstElement.has("questions") && (firstElement.has("question") || firstElement.has("options"))) {
                        // Direct array of questions for a single paper!
                        val paper = parsePaperFromQuestionsArray(jsonArray, fileName)
                        val validated = validateParsedPaper(paper, fileName, formattedSize, fileSizeBytes, trimmed)
                        listOf(validated)
                    } else {
                        // Array of paper objects
                        val items = mutableListOf<BatchImportItem>()
                        for (i in 0 until jsonArray.length()) {
                            val paperObj = jsonArray.optJSONObject(i)
                            if (paperObj != null) {
                                val subName = "${fileName.substringBeforeLast('.')}_part_${i + 1}.json"
                                val paper = parseSinglePaperObject(paperObj, subName)
                                items.add(validateParsedPaper(paper, subName, formattedSize, fileSizeBytes, paperObj.toString(2)))
                            } else {
                                items.add(
                                    BatchImportItem(
                                        id = UUID.randomUUID().toString(),
                                        fileName = "${fileName.substringBeforeLast('.')}_part_${i + 1}.json",
                                        fileSizeBytes = 0L,
                                        fileSizeFormatted = "-",
                                        paper = null,
                                        status = BatchItemStatus.INVALID,
                                        issues = listOf(
                                            ValidationIssue(ValidationSeverity.ERROR, "Array element at index $i is not a valid JSON object.")
                                        ),
                                        isSelected = false
                                    )
                                )
                            }
                        }
                        items
                    }
                }
            } catch (e: JSONException) {
                listOf(
                    BatchImportItem(
                        id = UUID.randomUUID().toString(),
                        fileName = fileName,
                        fileSizeBytes = fileSizeBytes,
                        fileSizeFormatted = formattedSize,
                        paper = null,
                        status = BatchItemStatus.INVALID,
                        issues = listOf(
                            ValidationIssue(ValidationSeverity.ERROR, "Malformed JSON array: ${e.localizedMessage}")
                        ),
                        isSelected = false,
                        rawJsonPreview = trimmed.take(200)
                    )
                )
            }
        }

        // Top-level is single JSON object
        return try {
            val root = JSONObject(trimmed)

            // Check if object is a multi-set bundle
            val bundleSetsArray = when {
                root.has("questionSets") -> root.optJSONArray("questionSets")
                root.has("sets") -> root.optJSONArray("sets")
                else -> null
            }

            if (bundleSetsArray != null && bundleSetsArray.length() > 0) {
                val bundleTitle = root.optString("examTitle", fileName.substringBeforeLast('.'))
                val bundleFolderId = if (root.has("folderId") && !root.isNull("folderId")) root.optString("folderId").ifBlank { null } else null
                val items = mutableListOf<BatchImportItem>()
                for (i in 0 until bundleSetsArray.length()) {
                    val paperObj = bundleSetsArray.optJSONObject(i)
                    if (paperObj != null) {
                        val subName = "${bundleTitle}_set_${i + 1}.json"
                        val paper = parseSinglePaperObject(paperObj, subName)
                        val finalPaper = if (bundleFolderId != null && paper.metadata.folderId.isNullOrBlank()) {
                            paper.copy(metadata = paper.metadata.copy(folderId = bundleFolderId))
                        } else {
                            paper
                        }
                        items.add(validateParsedPaper(finalPaper, subName, formattedSize, fileSizeBytes, paperObj.toString(2)))
                    }
                }
                items
            } else {
                val paper = parseSinglePaperObject(root, fileName)
                val validated = validateParsedPaper(paper, fileName, formattedSize, fileSizeBytes, trimmed)
                listOf(validated)
            }
        } catch (e: JSONException) {
            listOf(
                BatchImportItem(
                    id = UUID.randomUUID().toString(),
                    fileName = fileName,
                    fileSizeBytes = fileSizeBytes,
                    fileSizeFormatted = formattedSize,
                    paper = null,
                    status = BatchItemStatus.INVALID,
                    issues = listOf(
                        ValidationIssue(ValidationSeverity.ERROR, "Malformed JSON object: ${e.localizedMessage}")
                    ),
                    isSelected = false,
                    rawJsonPreview = trimmed.take(200)
                )
            )
        }
    }

    /**
     * Parses a single JSONObject into a QuestionPaper.
     */
    fun parseSinglePaperObject(root: JSONObject, fallbackFileName: String): QuestionPaper {
        val metaObj = root.optJSONObject("metadata")
        val fallbackTitle = fallbackFileName.substringBeforeLast('.').replace("_", " ").replace("-", " ")
            .split(" ").filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            .ifBlank { "Exam Paper" }

        val metadata = if (metaObj != null) {
            val rawRoll = metaObj.optString("rollNumber", "").ifBlank { root.optString("rollNumber", "") }.trim()
            val finalRoll = if (rawRoll.isNotBlank()) rawRoll else ExamNumberGenerator.generateRollNumber()

            val rawBooklet = metaObj.optString("bookletNumber", "").ifBlank { root.optString("bookletNumber", "") }.trim()
            val finalBooklet = if (rawBooklet.isNotBlank()) rawBooklet else ExamNumberGenerator.generateBookletNumber()

            val rawSet = metaObj.optString("setNumber", "").ifBlank { root.optString("setNumber", "01") }.trim()

            val folder = if (metaObj.has("folderId") && !metaObj.isNull("folderId")) {
                metaObj.optString("folderId").ifBlank { null }
            } else if (root.has("folderId") && !root.isNull("folderId")) {
                root.optString("folderId").ifBlank { null }
            } else null

            PaperMetadata(
                id = metaObj.optString("id", ""),
                title = metaObj.optString("title", fallbackTitle).ifBlank { fallbackTitle },
                subject = metaObj.optString("subject", root.optString("subject", "General Knowledge")).ifBlank { "General Knowledge" },
                topic = metaObj.optString("topic", root.optString("topic", "")),
                setNumber = if (rawSet.isNotBlank()) ExamNumberGenerator.normalizeSetNumber(rawSet) else "01",
                exam = metaObj.optString("targetExam", root.optString("exam", "")),
                folderId = folder,
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                createdAt = metaObj.optLong("createdAt", System.currentTimeMillis())
            )
        } else {
            val rawRoll = root.optString("rollNumber", "").trim()
            val finalRoll = if (rawRoll.isNotBlank()) rawRoll else ExamNumberGenerator.generateRollNumber()

            val rawBooklet = root.optString("bookletNumber", "").trim()
            val finalBooklet = if (rawBooklet.isNotBlank()) rawBooklet else ExamNumberGenerator.generateBookletNumber()

            val rawSet = root.optString("setNumber", "01").trim()
            val folder = if (root.has("folderId") && !root.isNull("folderId")) root.optString("folderId").ifBlank { null } else null

            PaperMetadata(
                id = root.optString("id", ""),
                title = root.optString("title", fallbackTitle).ifBlank { fallbackTitle },
                subject = root.optString("subject", "General Knowledge").ifBlank { "General Knowledge" },
                topic = root.optString("topic", ""),
                setNumber = if (rawSet.isNotBlank()) ExamNumberGenerator.normalizeSetNumber(rawSet) else "01",
                exam = root.optString("exam", root.optString("targetExam", "")),
                folderId = folder,
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                createdAt = root.optLong("createdAt", System.currentTimeMillis())
            )
        }

        val qArray = root.optJSONArray("questions") ?: JSONArray()
        val questions = parseQuestionsJsonArray(qArray)

        return QuestionPaper(
            metadata = metadata,
            questions = questions,
            quickAnswersCount = questions.count { it.correctAnswer != null },
            explanationsCount = questions.count { !it.explanation.isNullOrBlank() },
            createdAt = metadata.createdAt
        )
    }

    private fun parsePaperFromQuestionsArray(qArray: JSONArray, fallbackFileName: String): QuestionPaper {
        val fallbackTitle = fallbackFileName.substringBeforeLast('.').replace("_", " ")
        val questions = parseQuestionsJsonArray(qArray)
        val metadata = PaperMetadata(
            title = fallbackTitle.ifBlank { "Exam Paper" },
            subject = "General Knowledge",
            setNumber = "Set A"
        )
        return QuestionPaper(
            metadata = metadata,
            questions = questions,
            quickAnswersCount = questions.count { it.correctAnswer != null },
            explanationsCount = questions.count { !it.explanation.isNullOrBlank() }
        )
    }

    private fun parseQuestionsJsonArray(qArray: JSONArray): List<McqQuestion> {
        val questions = mutableListOf<McqQuestion>()
        for (i in 0 until qArray.length()) {
            val qObj = qArray.optJSONObject(i) ?: continue
            val num = qObj.optInt("num", i + 1)
            val questionText = qObj.optString("question", "").ifBlank {
                qObj.optString("text", "")
            }

            val optObj = qObj.optJSONObject("options")
            val options = if (optObj != null) {
                QuestionOptions(
                    a = optObj.optString("a", optObj.optString("A", "")),
                    b = optObj.optString("b", optObj.optString("B", "")),
                    c = optObj.optString("c", optObj.optString("C", "")),
                    d = optObj.optString("d", optObj.optString("D", ""))
                )
            } else {
                // Check if options are an array: ["opt1", "opt2", "opt3", "opt4"]
                val optArr = qObj.optJSONArray("options")
                if (optArr != null) {
                    QuestionOptions(
                        a = optArr.optString(0, ""),
                        b = optArr.optString(1, ""),
                        c = optArr.optString(2, ""),
                        d = optArr.optString(3, "")
                    )
                } else {
                    QuestionOptions(
                        a = qObj.optString("optionA", qObj.optString("option_a", "")),
                        b = qObj.optString("optionB", qObj.optString("option_b", "")),
                        c = qObj.optString("optionC", qObj.optString("option_c", "")),
                        d = qObj.optString("optionD", qObj.optString("option_d", ""))
                    )
                }
            }

            val ans = when {
                qObj.has("correctAnswer") && !qObj.isNull("correctAnswer") -> qObj.optString("correctAnswer", "").ifBlank { null }
                qObj.has("answer") && !qObj.isNull("answer") -> qObj.optString("answer", "").ifBlank { null }
                qObj.has("key") && !qObj.isNull("key") -> qObj.optString("key", "").ifBlank { null }
                else -> null
            }?.uppercase()?.trim()

            val exp = qObj.optString("explanation", "").ifBlank {
                qObj.optString("solution", "").ifBlank { null }
            }
            val qType = qObj.optString("questionType", "").ifBlank { null }
            val diagramUrl = qObj.optString("diagramUrl", "").ifBlank { null }
            val diagramLabel = qObj.optString("diagramLabel", "").ifBlank { null }
            val formula = qObj.optString("formula", "").ifBlank { null }

            questions.add(
                McqQuestion(
                    num = num,
                    question = questionText,
                    options = options,
                    correctAnswer = ans,
                    explanation = exp,
                    questionType = qType,
                    diagramUrl = diagramUrl,
                    diagramLabel = diagramLabel,
                    formula = formula
                )
            )
        }
        return questions
    }

    /**
     * Runs exhaustive bulk validation rules on a parsed QuestionPaper.
     */
    fun validateParsedPaper(
        paper: QuestionPaper,
        fileName: String,
        fileSizeFormatted: String,
        fileSizeBytes: Long,
        rawJson: String
    ): BatchImportItem {
        val issues = mutableListOf<ValidationIssue>()

        // 1. Metadata Checks
        if (paper.metadata.title.isBlank() || paper.metadata.title.equals("Exam Paper", ignoreCase = true)) {
            issues.add(
                ValidationIssue(
                    severity = ValidationSeverity.INFO,
                    message = "Title defaulted to \"${paper.metadata.title}\"."
                )
            )
        }
        if (paper.metadata.subject.isBlank()) {
            issues.add(
                ValidationIssue(
                    severity = ValidationSeverity.INFO,
                    message = "Subject was empty; defaulted to \"General Knowledge\"."
                )
            )
        }

        // 2. Questions Presence Check
        if (paper.questions.isEmpty()) {
            issues.add(
                ValidationIssue(
                    severity = ValidationSeverity.ERROR,
                    message = "No questions found in this paper."
                )
            )
            return BatchImportItem(
                id = UUID.randomUUID().toString(),
                fileName = fileName,
                fileSizeBytes = fileSizeBytes,
                fileSizeFormatted = fileSizeFormatted,
                paper = paper,
                status = BatchItemStatus.INVALID,
                issues = issues,
                isSelected = false,
                rawJsonPreview = rawJson.take(300)
            )
        }

        // 3. Question-level checks
        val seenNums = mutableSetOf<Int>()
        var emptyQuestionCount = 0
        var missingKeysCount = 0
        var missingExpCount = 0
        var nonStandardKeyCount = 0
        var lowOptionCount = 0

        paper.questions.forEach { q ->
            // Duplicate number check
            if (seenNums.contains(q.num)) {
                issues.add(
                    ValidationIssue(
                        severity = ValidationSeverity.WARNING,
                        message = "Duplicate question number #${q.num} found.",
                        questionNumber = q.num
                    )
                )
            } else {
                seenNums.add(q.num)
            }

            // Question text
            if (q.question.isBlank()) {
                emptyQuestionCount++
                issues.add(
                    ValidationIssue(
                        severity = ValidationSeverity.ERROR,
                        message = "Question #${q.num} has empty question text.",
                        questionNumber = q.num
                    )
                )
            }

            // Options check
            val validOpts = listOf(q.options.a, q.options.b, q.options.c, q.options.d).filter { it.isNotBlank() }
            if (validOpts.size < 2) {
                lowOptionCount++
                issues.add(
                    ValidationIssue(
                        severity = ValidationSeverity.ERROR,
                        message = "Question #${q.num} has fewer than 2 non-empty options.",
                        questionNumber = q.num
                    )
                )
            } else if (validOpts.size < 4) {
                issues.add(
                    ValidationIssue(
                        severity = ValidationSeverity.INFO,
                        message = "Question #${q.num} has ${validOpts.size} options (standard is 4).",
                        questionNumber = q.num
                    )
                )
            }

            // Answer Key check
            if (q.correctAnswer.isNullOrBlank()) {
                missingKeysCount++
            } else {
                val cleanedAns = q.correctAnswer.uppercase()
                if (cleanedAns !in listOf("A", "B", "C", "D", "E")) {
                    nonStandardKeyCount++
                    issues.add(
                        ValidationIssue(
                            severity = ValidationSeverity.WARNING,
                            message = "Question #${q.num} answer key \"${q.correctAnswer}\" is non-standard (expected A, B, C, D, or E).",
                            questionNumber = q.num
                        )
                    )
                }
            }

            // Explanation check
            if (q.explanation.isNullOrBlank()) {
                missingExpCount++
            }
        }

        // Summary warnings for missing keys or explanations
        if (missingKeysCount > 0) {
            issues.add(
                ValidationIssue(
                    severity = ValidationSeverity.WARNING,
                    message = "$missingKeysCount of ${paper.questions.size} questions are missing answer keys."
                )
            )
        }
        if (missingExpCount > 0) {
            issues.add(
                ValidationIssue(
                    severity = ValidationSeverity.INFO,
                    message = "$missingExpCount of ${paper.questions.size} questions lack detailed explanations."
                )
            )
        }

        // Determine final item status
        val hasErrors = issues.any { it.severity == ValidationSeverity.ERROR }
        val hasWarnings = issues.any { it.severity == ValidationSeverity.WARNING }

        val status = when {
            hasErrors -> BatchItemStatus.INVALID
            hasWarnings -> BatchItemStatus.WARNING
            else -> BatchItemStatus.VALID
        }

        return BatchImportItem(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            fileSizeFormatted = fileSizeFormatted,
            paper = paper,
            status = status,
            issues = issues,
            isSelected = !hasErrors, // Automatically selected if valid or warning
            rawJsonPreview = rawJson.take(300)
        )
    }

    /**
     * Computes summary metrics for a list of BatchImportItems.
     */
    fun computeSummary(items: List<BatchImportItem>): BatchValidationSummary {
        val totalFiles = items.size
        val validCount = items.count { it.status == BatchItemStatus.VALID }
        val warningCount = items.count { it.status == BatchItemStatus.WARNING }
        val invalidCount = items.count { it.status == BatchItemStatus.INVALID }
        val totalQuestions = items.filter { it.paper != null }.sumOf { it.paper!!.questions.size }

        return BatchValidationSummary(
            totalFiles = totalFiles,
            totalPapers = items.count { it.paper != null },
            validCount = validCount,
            warningCount = warningCount,
            invalidCount = invalidCount,
            totalQuestions = totalQuestions
        )
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.1f MB", mb)
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        } catch (_: Exception) {
            name = uri.lastPathSegment
        }
        return name
    }

    private fun queryFileSize(context: Context, uri: Uri): Long {
        var size: Long = 0L
        try {
            val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
            // ignore
        }
        return size
    }
}
