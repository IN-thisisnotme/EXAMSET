package com.example.util

import com.example.model.AuditIssueType
import com.example.model.AuditQuestionReport
import com.example.model.AuditSummaryMetrics
import com.example.model.McqQuestion
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import org.json.JSONArray
import org.json.JSONObject

object QuestionAuditHelper {

    /**
     * Generates a comprehensive, ready-to-run prompt with expert auditing instructions
     * followed by the full question paper.
     */
    fun generateBulkAuditPrompt(paper: QuestionPaper): String {
        return buildString {
            appendLine("=== SENIOR EXAMINATION QUALITY AUDITOR & FACT-CHECK PROMPT ===")
            appendLine("ROLE: You are an expert academic curriculum auditor, psychometrician, and subject matter authority.")
            appendLine("TASK: Thoroughly audit the following ${paper.questions.size} multiple-choice questions for the examination \"${paper.metadata.title}\" (Subject: ${paper.metadata.subject}, Set: ${paper.metadata.setNumber}).")
            appendLine()
            appendLine("=== AUDIT MANDATES & INSTRUCTIONS ===")
            appendLine("1. ANSWER KEY VERIFICATION:")
            appendLine("   - Fact-check every single question against authoritative textbooks and scientific principles.")
            appendLine("   - If the marked answer key is WRONG or MISMARKED, identify the correct key (A, B, C, D, or E).")
            //appendLine("   - MANDATORY RECHECKED EXPLANATION: For ANY wrong or modified key, you MUST provide a detailed, step-by-step rechecked explanation proving WHY the current key is incorrect and showing the exact conceptual or mathematical derivation for why the new key is correct.")
            appendLine()
            appendLine("2. OPTION & QUESTION INTEGRITY:")
            appendLine("   - Check for vague, ambiguous, or duplicate options (e.g. multiple options that mean the same thing).")
            appendLine("   - Check for missing options, truncated sentences, or impossible premises.")
            appendLine("   - If a question is fatally flawed or cannot be salvaged by a simple key fix, classify it as FLAWED.")
            //appendLine("   - MANDATORY REPLACEMENT QUESTION: For any question classified as FLAWED, provide a brand-new replacement MCQ covering the exact same subject, topic, and difficulty. You must include: the new Question stem, Options A-D, the Correct Answer, and a Brand-New Detailed Explanation matching the replacement question.")
            appendLine("   - If a question is classified as FLAWED, explain the issue through its status only. Do not generate a replacement question.")
            appendLine()
            appendLine("3. OUTPUT FORMAT REQUIREMENT:")
            appendLine("   Respond with a valid JSON array enclosed in a ```json ``` block following this exact schema:")
            appendLine("OUTPUT RESTRICTIONS:")
            appendLine("Return only these fields: qNum, status, currentKey, suggestedKey.")
            appendLine("Do not generate replacement questions.")
            appendLine("Do not generate detailed explanations, rechecked explanations, or flaw reasons.")
            appendLine("Do not include replacementQuestion or detailedExplanation in the output.")
            appendLine("If a question is FLAWED, set suggestedKey to null unless a valid corrected key can be established.")
            appendLine("Ensure status and suggestedKey are consistent with the audit findings.")
/*            
            appendLine("""
```json
[
  {
    "qNum": 1,
    "status": "WRONG_KEY", // "WRONG_KEY" | "MISSING_KEY" | "FLAWED" | "VAGUE_OPTIONS" | "VALID"
    "currentKey": "A",
    "suggestedKey": "C",
    //"flawReason": "Explanation of what is wrong with the current key or options",
    //"recheckedExplanation": "Detailed step-by-step derivation proving why C is correct and A was wrong.",
    "replacementQuestion": null
  },
  {
    "qNum": 2,
    "status": "FLAWED",
    "currentKey": "B",
    "suggestedKey": null,
    //"flawReason": "Options B and D are completely identical and stem is mathematically unsolvable.",
    //"recheckedExplanation": null,
    "replacementQuestion": {
      "question": "What is the primary function of ribosomes in cellular biology?",
      "options": {
        "a": "Lipid synthesis",
        "b": "Protein synthesis",
        "c": "ATP generation",
        "d": "DNA storage"
      },
      "correctAnswer": "B",
      "explanation": "Ribosomes are macromolecular machines found within all cells that perform biological protein synthesis."
    }
  }
]
```
""".trimIndent())
*/

            appendLine("""
```json
[
  {
    "qNum": 1,
    "status": "WRONG_KEY",
    "currentKey": "A",
    "suggestedKey": "C",
    "replacementQuestion": null
  },
  {
    "qNum": 2,
    "status": "FLAWED",
    "currentKey": "B",
    "suggestedKey": null,
    "replacementQuestion": {
      "question": "What is the primary function of ribosomes?",
      "options": {
        "a": "Lipid synthesis",
        "b": "Protein synthesis",
        "c": "ATP generation",
        "d": "DNA storage"
      },
      "correctAnswer": "B"
    }
  }
]


""".trimIndent())

            appendLine()
            appendLine("=== EXAMINATION PAPER CONTENT TO AUDIT ===")
            appendLine("Paper Title: ${paper.metadata.title}")
            appendLine("Subject: ${paper.metadata.subject}")
            appendLine("Topic: ${paper.metadata.topic}")
            appendLine("Set Number: ${paper.metadata.setNumber}")
            appendLine("Total Questions: ${paper.questions.size}")
            appendLine("--------------------------------------------------")
            appendLine()

            paper.questions.forEach { q ->
                appendLine("Q${q.num}. ${q.question}")
                appendLine("   A) ${q.options.a}")
                appendLine("   B) ${q.options.b}")
                appendLine("   C) ${q.options.c}")
                appendLine("   D) ${q.options.d}")
                if (q.options.e.isNotBlank()) {
                    appendLine("   E) ${q.options.e}")
                }
                appendLine("   Marked Key: ${q.correctAnswer ?: "[NOT ASSIGNED / MISSING]"}")
                //if (!q.explanation.isNullOrBlank()) {
                //    appendLine("   Existing Explanation: ${q.explanation}")
                //}
                appendLine()
            }
            appendLine("=== END OF QUESTION PAPER ===")
        }
    }

    /**
     * Performs a fast, local heuristic scan of the paper to immediately flag
     * missing keys, missing options, duplicate options, and blank stems.
     */
    fun runHeuristicPreAudit(paper: QuestionPaper): List<AuditQuestionReport> {
        val qMap = paper.questions.associateBy { it.num }
        return paper.questions.map { q ->
            val optionsList = listOfNotNull(
                if (q.options.a.isNotBlank()) q.options.a.trim() else null,
                if (q.options.b.isNotBlank()) q.options.b.trim() else null,
                if (q.options.c.isNotBlank()) q.options.c.trim() else null,
                if (q.options.d.isNotBlank()) q.options.d.trim() else null
            )
            val distinctCount = optionsList.map { it.lowercase() }.distinct().size
            val hasDuplicateOptions = optionsList.size >= 2 && distinctCount < optionsList.size

            when {
                q.question.isBlank() || q.question.length < 5 -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.FLAWED_QUESTION,
                        currentKey = q.correctAnswer,
                        flawReason = "Question stem is blank or too short (< 5 characters).",
                        recheckedExplanation = null,
                        originalQuestion = q
                    )
                }
                q.options.hasMissingOptions -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.MISSING_OPTIONS,
                        currentKey = q.correctAnswer,
                        flawReason = "Missing required choices: ${q.options.missingOptions.joinToString(", ")}.",
                        recheckedExplanation = null,
                        originalQuestion = q
                    )
                }
                hasDuplicateOptions -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.VAGUE_OPTIONS,
                        currentKey = q.correctAnswer,
                        flawReason = "Two or more options contain identical duplicate values.",
                        recheckedExplanation = null,
                        originalQuestion = q
                    )
                }
                q.correctAnswer.isNullOrBlank() -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.MISSING_KEY,
                        currentKey = null,
                        suggestedKey = null, // Default suggestion for creator review
                        flawReason = "No answer key has been assigned for this question.",
                        recheckedExplanation = if (!q.explanation.isNullOrBlank()) {
                            "Existing explanation is present: \"${q.explanation}\". Verify answer key assignment."
                        } else {
                            "Assign the correct answer key and generate a detailed conceptual explanation."
                        },
                        originalQuestion = q
                    )
                }
                q.explanation.isNullOrBlank() -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.MISSING_EXPLANATION,
                        currentKey = q.correctAnswer,
                        suggestedKey = q.correctAnswer,
                        flawReason = "Answer key is assigned (${q.correctAnswer}), but no detailed explanation is provided.",
                        recheckedExplanation = "Provide a step-by-step conceptual explanation validating option ${q.correctAnswer}.",
                        originalQuestion = q
                    )
                }
                else -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.CLEAN,
                        currentKey = q.correctAnswer,
                        suggestedKey = q.correctAnswer,
                        recheckedExplanation = q.explanation,
                        originalQuestion = q
                    )
                }
            }
        }
    }

    /**
     * Parses the LLM's feedback text (either JSON array or key-value summary)
     * and maps it against the current QuestionPaper.
     */
    fun parseAuditReport(rawInput: String, paper: QuestionPaper): List<AuditQuestionReport> {
        val qMap = paper.questions.associateBy { it.num }
        val parsedReports = mutableMapOf<Int, AuditQuestionReport>()

        // 1. Try parsing JSON array from the input
        val jsonArray = extractJsonArray(rawInput)
        if (jsonArray != null) {
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val qNum = obj.optInt("qNum", -1)
                if (qNum <= 0) continue

                val originalQ = qMap[qNum]
                val statusStr = obj.optString("status", "").uppercase()
                val currentKey = obj.optString("currentKey", originalQ?.correctAnswer ?: "").ifBlank { originalQ?.correctAnswer }
                val suggestedKey = obj.optString("suggestedKey", "").ifBlank { null }?.uppercase()
                val flawReason = obj.optString("flawReason", "").ifBlank { null }
                val recheckedExplanation = obj.optString("recheckedExplanation", "").ifBlank { null }

                val replacementObj = obj.optJSONObject("replacementQuestion")
                val replacementQ = if (replacementObj != null) {
                    val repStem = replacementObj.optString("question", "")
                    val repOptionsObj = replacementObj.optJSONObject("options")
                    val repOptions = if (repOptionsObj != null) {
                        QuestionOptions(
                            a = repOptionsObj.optString("a", ""),
                            b = repOptionsObj.optString("b", ""),
                            c = repOptionsObj.optString("c", ""),
                            d = repOptionsObj.optString("d", ""),
                            e = repOptionsObj.optString("e", "")
                        )
                    } else QuestionOptions()
                    val repKey = replacementObj.optString("correctAnswer", "").uppercase().ifBlank { null }
                    val repExp = replacementObj.optString("explanation", "").ifBlank { null }
                    if (repStem.isNotBlank()) {
                        McqQuestion(
                            num = qNum,
                            question = repStem,
                            options = repOptions,
                            correctAnswer = repKey,
                            explanation = repExp
                        )
                    } else null
                } else null

                val status = when {
                    statusStr.contains("WRONG") -> AuditIssueType.WRONG_KEY
                    statusStr.contains("MISSING_KEY") -> AuditIssueType.MISSING_KEY
                    statusStr.contains("FLAW") -> AuditIssueType.FLAWED_QUESTION
                    statusStr.contains("VAGUE") -> AuditIssueType.VAGUE_OPTIONS
                    statusStr.contains("MISSING_OPT") -> AuditIssueType.MISSING_OPTIONS
                    suggestedKey != null && currentKey != null && suggestedKey != currentKey -> AuditIssueType.WRONG_KEY
                    replacementQ != null -> AuditIssueType.FLAWED_QUESTION
                    statusStr.contains("VALID") || statusStr.contains("CLEAN") -> AuditIssueType.CLEAN
                    else -> if (suggestedKey != null && suggestedKey != currentKey) AuditIssueType.WRONG_KEY else AuditIssueType.CLEAN
                }

                parsedReports[qNum] = AuditQuestionReport(
                    qNum = qNum,
                    status = status,
                    currentKey = currentKey,
                    suggestedKey = suggestedKey,
                    flawReason = flawReason,
                    recheckedExplanation = recheckedExplanation,
                    replacementQuestion = replacementQ,
                    originalQuestion = originalQ
                )
            }
        }

        // 2. If JSON didn't catch all, fallback to line-by-line regex parsing
        if (parsedReports.isEmpty()) {
            parseTextReport(rawInput, qMap, parsedReports)
        }

        // 3. For any questions not covered in the input, fill from heuristic pre-audit
        val heuristic = runHeuristicPreAudit(paper).associateBy { it.qNum }
        return paper.questions.map { q ->
            parsedReports[q.num] ?: heuristic[q.num] ?: AuditQuestionReport(
                qNum = q.num,
                status = AuditIssueType.CLEAN,
                currentKey = q.correctAnswer,
                originalQuestion = q
            )
        }
    }

    private fun extractJsonArray(text: String): JSONArray? {
        val trimmed = text.trim()
        val jsonPattern = Regex("""```(?:json)?\s*(\[\s*\{.*?\}\s*\])\s*```""", RegexOption.DOT_MATCHES_ALL)
        val match = jsonPattern.find(trimmed)
        val candidate = match?.groupValues?.get(1) ?: trimmed

        return try {
            val startIdx = candidate.indexOf('[')
            val endIdx = candidate.lastIndexOf(']')
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                JSONArray(candidate.substring(startIdx, endIdx + 1))
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTextReport(
        text: String,
        qMap: Map<Int, McqQuestion>,
        parsedReports: MutableMap<Int, AuditQuestionReport>
    ) {
        val lines = text.lines()
        val qRegex = Regex("""(?:^|\b)(?:Q|Question)\s*(\d+)[:\.\-\s]+(.*)""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val match = qRegex.find(line) ?: continue
            val qNum = match.groupValues[1].toIntOrNull() ?: continue
            val remainder = match.groupValues[2].trim()
            val originalQ = qMap[qNum] ?: continue

            val keyRegex = Regex("""\b([A-E])\b.*?(?:->|to|should be|correct|instead of).*?\b([A-E])\b""", RegexOption.IGNORE_CASE)
            val keyMatch = keyRegex.find(remainder)

            if (keyMatch != null) {
                val cur = keyMatch.groupValues[1].uppercase()
                val sug = keyMatch.groupValues[2].uppercase()
                parsedReports[qNum] = AuditQuestionReport(
                    qNum = qNum,
                    status = AuditIssueType.WRONG_KEY,
                    currentKey = cur,
                    suggestedKey = sug,
                    flawReason = remainder,
                    recheckedExplanation = "Rechecked derivation: Option $sug is verified as the correct answer (previously marked as $cur).",
                    originalQuestion = originalQ
                )
            } else if (remainder.contains("flaw", ignoreCase = true) || remainder.contains("vague", ignoreCase = true) || remainder.contains("delete", ignoreCase = true)) {
                parsedReports[qNum] = AuditQuestionReport(
                    qNum = qNum,
                    status = AuditIssueType.FLAWED_QUESTION,
                    currentKey = originalQ.correctAnswer,
                    flawReason = remainder,
                    recheckedExplanation = null,
                    originalQuestion = originalQ
                )
            }
        }
    }

    /**
     * Aggregates summary statistics for the UI cards.
     */
    fun computeMetrics(reports: List<AuditQuestionReport>): AuditSummaryMetrics {
        var wrongKeys = 0
        var missingKeys = 0
        var flawed = 0
        var missingOpts = 0
        var clean = 0

        reports.forEach { r ->
            when (r.status) {
                AuditIssueType.WRONG_KEY -> wrongKeys++
                AuditIssueType.MISSING_KEY -> missingKeys++
                AuditIssueType.FLAWED_QUESTION, AuditIssueType.VAGUE_OPTIONS -> flawed++
                AuditIssueType.MISSING_OPTIONS -> missingOpts++
                AuditIssueType.CLEAN, AuditIssueType.MISSING_EXPLANATION -> clean++
            }
        }

        return AuditSummaryMetrics(
            totalAudited = reports.size,
            wrongKeysCount = wrongKeys,
            missingKeysCount = missingKeys,
            flawedQuestionsCount = flawed,
            missingOptionsCount = missingOpts,
            cleanCount = clean
        )
    }
}
