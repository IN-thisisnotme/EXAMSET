package com.example.util

import com.example.model.AuditIssueType
import com.example.model.AuditPhase
import com.example.model.AuditQuestionReport
import com.example.model.AuditSummaryMetrics
import com.example.model.McqQuestion
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import org.json.JSONArray
import org.json.JSONObject

object QuestionAuditHelper {

    private const val SYSTEM_OVERRIDE_HEADER = """
=== CRITICAL SYSTEM OVERRIDE & DIRECTIVE PRECEDENCE ===
- DISREGARD ALL ACCOUNT-LEVEL PERSONALIZATION, CUSTOM INSTRUCTIONS, CHAT PERSONAS, OR PRIOR MEMORY SETTINGS FOR THIS TASK.
- OPERATE STRICTLY AND EXCLUSIVELY AS AN AUTOMATED ACADEMIC PSYCHOMETRIC & QUALITY AUDIT ENGINE.
- DO NOT INCLUDE ANY CONVERSATIONAL GREETINGS, CHATBOT INTRODUCTIONS, COURTESY CLOSINGS, OR POST-ANALYSIS REMARKS.
- OUTPUT RAW, VALID JSON ONLY (ENCLOSED INSIDE A SINGLE ```json ... ``` BLOCK).
- DO NOT USE MARKDOWN HEADINGS OR BULLET LISTS OUTSIDE THE JSON BLOCK.
"""

    /**
     * Phase 1: Answer Key Verification & Flawed Question Flagging.
     * Checks if marked answer key is correct. If mismatched, suggests the true key.
     * Flags which question numbers are fatally flawed (incomplete, ambiguous, missing options or diagrams).
     * Ultra-low token output footprint (sparse JSON return).
     */
    fun generatePhase1KeyAndFlawAuditPrompt(
        paper: QuestionPaper,
        questionsToAudit: List<McqQuestion> = paper.questions
    ): String {
        return buildString {
            appendLine(SYSTEM_OVERRIDE_HEADER.trim())
            appendLine()
            appendLine("=== PHASE 1: ANSWER KEY FACT-CHECK & FLAW FLAGGING MANDATE ===")
            appendLine("ROLE: Senior academic psychometrician and subject matter examiner.")
            appendLine("TASK: Audit the following ${questionsToAudit.size} questions for the examination \"${paper.metadata.title}\" (Subject: ${paper.metadata.subject}, Set: ${paper.metadata.setNumber}).")
            appendLine()
            appendLine("=== OBJECTIVES FOR PHASE 1 ===")
            appendLine("1. FACT-CHECK THE MARKED ANSWER KEY:")
            appendLine("   - For each question, compare the marked key against authoritative scientific & academic standards.")
            appendLine("   - If the marked key is WRONG, record \"status\": \"WRONG_KEY\", specify \"suggestedKey\", and give a brief 1-sentence note in \"flawReason\".")
            appendLine("   - If the marked key is already correct, DO NOT FLAG IT AS WRONG.")
            appendLine()
            appendLine("2. FLAG FATALLY FLAWED QUESTIONS:")
            appendLine("   - Flag questions that have missing choices, identical/duplicate options, ambiguous wording, or impossible premises.")
            appendLine("   - VISUAL DEPENDENCY: If a question references a diagram, figure, or circuit that is absent or unsolvable without image, flag it as \"status\": \"FLAWED\" (Reason: \"Visual dependency: Essential diagram/figure missing\").")
            appendLine("   - Set \"status\": \"FLAWED\". (Replacement questions are generated in Phase 2, so leave \"replacementQuestion\": null for now).")
            appendLine()
            appendLine("=== CRITICAL TOKEN OPTIMIZATION RULE (SPARSE JSON RETURN) ===")
            appendLine("- ONLY return JSON items for questions that have a wrong answer key or are flawed.")
            appendLine("- EXCLUDE completely valid, correct questions from the JSON output array.")
            appendLine("- If all audited questions are completely correct, return an empty array: []")
            appendLine()
            appendLine("=== EXACT JSON SCHEMA FOR PHASE 1 ===")
            appendLine("""
```json
[
  {
    "qNum": 1,
    "status": "WRONG_KEY",
    "currentKey": "A",
    "suggestedKey": "C",
    "flawReason": "Evaluating algebraic identity (a-b)^2 yields 0.25 (Option C), not Option A."
  },
  {
    "qNum": 7,
    "status": "FLAWED",
    "currentKey": "B",
    "suggestedKey": null,
    "flawReason": "Visual dependency: Essential circuit diagram is missing, rendering question unsolvable."
  }
]
```
""".trimIndent())
            appendLine()
            appendLine("=== EXAMINATION QUESTIONS (EXPLICIT STEM & ALL OPTIONS) ===")
            appendQuestionsPayload(paper, questionsToAudit)
        }
    }

    /**
     * Phase 2: Targeted Replacements for Flawed Questions.
     * Generates complete, high-quality replacement MCQs for only the flawed questions flagged in Phase 1.
     * Preserves question numbering and total question count.
     */
    fun generatePhase2FlawedReplacementPrompt(
        paper: QuestionPaper,
        flawedReports: List<AuditQuestionReport>
    ): String {
        return buildString {
            appendLine(SYSTEM_OVERRIDE_HEADER.trim())
            appendLine()
            appendLine("=== PHASE 2: FLIPPED IN-PLACE REPLACEMENT FOR FLAWED QUESTIONS ===")
            appendLine("ROLE: Senior academic question author and subject curriculum specialist.")
            appendLine("TASK: Provide complete, self-contained, brand new replacement MCQs for the following ${flawedReports.size} flawed questions in \"${paper.metadata.title}\".")
            appendLine()
            appendLine("=== REQUIREMENTS FOR EACH REPLACEMENT MCQ ===")
            appendLine("1. SAME TOPIC & DIFFICULTY: Must strictly cover the same syllabus topic, standard, and cognitive difficulty.")
            appendLine("2. FULLY TEXT-SOLVABLE: Must NOT depend on unseen diagrams or external images.")
            appendLine("3. COMPLETE MCQ PACKAGE: Must include:")
            appendLine("   - \"question\": Clear, unambiguous question stem.")
            appendLine("   - \"options\": Four distinct, valid choices {\"a\": \"...\", \"b\": \"...\", \"c\": \"...\", \"d\": \"...\"}.")
            appendLine("   - \"correctAnswer\": Exact correct key letter (A, B, C, or D).")
            appendLine("   - \"explanation\": Clear, pedagogical step-by-step solution explaining why the key is correct.")
            appendLine()
            appendLine("=== EXACT JSON SCHEMA FOR PHASE 2 ===")
            appendLine("""
```json
[
  {
    "qNum": 7,
    "replacementQuestion": {
      "question": "What is the SI unit of magnetic flux density?",
      "options": {
        "a": "Tesla",
        "b": "Weber",
        "c": "Henry",
        "d": "Lumen"
      },
      "correctAnswer": "A",
      "explanation": "The SI unit of magnetic flux density (B) is the Tesla (T). Weber is the unit of magnetic flux, and Henry is the unit of inductance."
    }
  }
]
```
""".trimIndent())
            appendLine()
            appendLine("=== FLAWED QUESTIONS REQUIRING REPLACEMENT ===")
            flawedReports.forEach { rep ->
                val q = rep.originalQuestion ?: paper.questions.find { it.num == rep.qNum }
                appendLine("Question Q${rep.qNum}:")
                if (q != null) {
                    appendLine("   Original Stem: ${q.question}")
                    appendLine("   Original Options: A) ${q.options.a} | B) ${q.options.b} | C) ${q.options.c} | D) ${q.options.d}")
                    if (q.options.e.isNotBlank()) appendLine("   Original Option E: ${q.options.e}")
                }
                appendLine("   Flaw Diagnosis: ${rep.flawReason ?: "Flawed question requires complete replacement"}")
                appendLine()
            }
            appendLine("=== END OF FLAWED QUESTIONS ===")
        }
    }

    /**
     * Phase 3: Detailed Step-by-Step Explanations for Key Corrections.
     * Generates deep, non-repetitive conceptual/mathematical proofs for questions where keys were corrected.
     */
    fun generatePhase3ExplanationAuditPrompt(
        paper: QuestionPaper,
        keyCorrectedReports: List<AuditQuestionReport>
    ): String {
        return buildString {
            appendLine(SYSTEM_OVERRIDE_HEADER.trim())
            appendLine()
            appendLine("=== PHASE 3: DEEP-DIVE STEP-BY-STEP EXPLANATION PROOF ===")
            appendLine("ROLE: Senior academic professor and master tutor.")
            appendLine("TASK: Provide exhaustive, step-by-step detailed explanations for the following ${keyCorrectedReports.size} questions where answer keys were corrected.")
            appendLine()
            appendLine("=== EXPLANATION CRITERIA ===")
            appendLine("1. RIGOROUS DERIVATION: Step-by-step conceptual logic, mathematical proof, or formula derivation.")
            appendLine("2. DISTRACTOR ANALYSIS: Clearly explain why the originally marked key was incorrect and why the corrected key is indisputably true.")
            appendLine("3. NON-REPETITIVE: Avoid generic boilerplate; tailor specifically to the numbers and terms in the question.")
            appendLine()
            appendLine("=== EXACT JSON SCHEMA FOR PHASE 3 ===")
            appendLine("""
```json
[
  {
    "qNum": 1,
    "correctedKey": "C",
    "recheckedExplanation": "Step 1: Notice that the numerical expression 4.25*4.25 + 3.75*3.75 - 2*4.25*3.75 matches the standard algebraic expansion (a - b)^2 with a = 4.25 and b = 3.75.\nStep 2: Compute the difference: 4.25 - 3.75 = 0.50.\nStep 3: Square the result: (0.50)^2 = 0.25.\nStep 4: Option C matches 0.25 exactly. Marked key A was incorrect because it failed to apply the subtraction before squaring."
  }
]
```
""".trimIndent())
            appendLine()
            appendLine("=== QUESTIONS WITH CORRECTED KEYS ===")
            keyCorrectedReports.forEach { rep ->
                val q = rep.originalQuestion ?: paper.questions.find { it.num == rep.qNum }
                appendLine("Q${rep.qNum}:")
                if (q != null) {
                    appendLine("   Stem: ${q.question}")
                    appendLine("   A) ${q.options.a}")
                    appendLine("   B) ${q.options.b}")
                    appendLine("   C) ${q.options.c}")
                    appendLine("   D) ${q.options.d}")
                    if (q.options.e.isNotBlank()) appendLine("   E) ${q.options.e}")
                }
                appendLine("   Marked Key: ${rep.currentKey ?: q?.correctAnswer ?: "None"}")
                appendLine("   Corrected Key: ${rep.suggestedKey ?: "None"}")
                if (!rep.flawReason.isNullOrBlank()) {
                    appendLine("   Note: ${rep.flawReason}")
                }
                appendLine()
            }
            appendLine("=== END OF QUESTIONS ===")
        }
    }

    /**
     * Legacy comprehensive prompt generator (includes full two-step instructions).
     */
    fun generateBulkAuditPrompt(
        paper: QuestionPaper,
        questionsToAudit: List<McqQuestion> = paper.questions
    ): String {
        return buildString {
            appendLine(SYSTEM_OVERRIDE_HEADER.trim())
            appendLine()
            appendLine("=== SENIOR EXAMINATION QUALITY AUDITOR & FACT-CHECK MANDATE ===")
            appendLine("ROLE: You are an expert academic curriculum auditor, psychometrician, and subject matter authority.")
            appendLine("TASK: Thoroughly audit the following ${questionsToAudit.size} multiple-choice questions for the examination \"${paper.metadata.title}\" (Subject: ${paper.metadata.subject}, Set: ${paper.metadata.setNumber}).")
            appendLine()
            appendLine("=== TWO-STEP AUDITING WORKFLOW ===")
            appendLine("STEP 1: ANSWER KEY VERIFICATION & MISMARK CORRECTION")
            appendLine("   - Fact-check every single question against authoritative textbooks and scientific principles.")
            appendLine("   - If the marked answer key is WRONG or MISMARKED, identify the true correct key (A, B, C, D, or E).")
            appendLine("   - MANDATORY RECHECKED EXPLANATION: For ANY wrong or modified key, you MUST provide a detailed, step-by-step rechecked explanation in \"recheckedExplanation\" proving WHY the current key is incorrect.")
            appendLine("   - Set status to \"WRONG_KEY\". If the marked key is already correct, do NOT flag it as wrong.")
            appendLine()
            appendLine("STEP 2: QUESTION INTEGRITY, FATAL FLAWS & VISUAL DEPENDENCIES")
            appendLine("   - Check for vague, ambiguous, or duplicate options.")
            appendLine("   - Check for missing options, truncated sentences, or impossible/contradictory mathematical premises.")
            appendLine("   - VISUAL / DIAGRAM DEPENDENCIES: If a question references an unseen diagram, circuit, graph, or image that is missing or cannot be solved from the text alone, classify it under Step 2 as FLAWED.")
            appendLine("   - MANDATORY REPLACEMENT QUESTION: For ANY question classified as FLAWED, provide a brand-new, self-contained replacement MCQ covering the exact same subject, topic, and difficulty.")
            appendLine()
            appendLine("=== EXACT JSON OUTPUT SCHEMA ===")
            appendLine("""
```json
[
  {
    "qNum": 1,
    "status": "WRONG_KEY",
    "currentKey": "A",
    "suggestedKey": "C",
    "flawReason": "Option A is mathematically incorrect; evaluating yields Option C.",
    "recheckedExplanation": "Step-by-step derivation: The expression evaluates to Option C.",
    "replacementQuestion": null
  }
]
```
""".trimIndent())
            appendLine()
            appendLine("=== EXAMINATION QUESTIONS (EXPLICIT STEM & ALL OPTIONS) ===")
            appendQuestionsPayload(paper, questionsToAudit)
        }
    }

    private fun StringBuilder.appendQuestionsPayload(
        paper: QuestionPaper,
        questions: List<McqQuestion>
    ) {
        appendLine("Paper: ${paper.metadata.title} (Subject: ${paper.metadata.subject}, Set: ${paper.metadata.setNumber})")
        appendLine("Total Questions in this Batch: ${questions.size}")
        appendLine("--------------------------------------------------")
        appendLine()

        questions.forEach { q ->
            appendLine("Q${q.num}. ${q.question}")
            if (!q.diagramLabel.isNullOrBlank()) {
                appendLine("   [Diagram / Figure Reference: ${q.diagramLabel}]")
            }
            if (!q.formula.isNullOrBlank()) {
                appendLine("   [Formula: ${q.formula}]")
            }
            appendLine("   A) ${q.options.a}")
            appendLine("   B) ${q.options.b}")
            appendLine("   C) ${q.options.c}")
            appendLine("   D) ${q.options.d}")
            if (q.options.e.isNotBlank()) {
                appendLine("   E) ${q.options.e}")
            }
            appendLine("   Marked Key: ${q.correctAnswer ?: "[NOT ASSIGNED]"}")
            if (!q.explanation.isNullOrBlank()) {
                appendLine("   Existing Explanation: ${q.explanation}")
            }
            appendLine()
        }
        appendLine("=== END OF QUESTION PAPER ===")
    }

    /**
     * Fast, local heuristic check that runs instantaneously without calling external LLM.
     */
    fun runHeuristicPreAudit(paper: QuestionPaper): List<AuditQuestionReport> {
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
                q.isNeutralized -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.CLEAN,
                        currentKey = q.correctAnswer,
                        suggestedKey = q.correctAnswer,
                        originalQuestion = q,
                        isResolved = true,
                        isNeutralized = true
                    )
                }
                q.isVerified -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.CLEAN,
                        currentKey = q.correctAnswer,
                        suggestedKey = q.correctAnswer,
                        recheckedExplanation = q.explanation,
                        originalQuestion = q,
                        isResolved = true,
                        isVerified = true
                    )
                }
                q.question.isBlank() || q.question.length < 5 -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.FLAWED_QUESTION,
                        currentKey = q.correctAnswer,
                        flawReason = "Question stem is blank or too short (< 5 characters).",
                        originalQuestion = q
                    )
                }
                q.options.hasMissingOptions -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.MISSING_OPTIONS,
                        currentKey = q.correctAnswer,
                        flawReason = "Missing required choices: ${q.options.missingOptions.joinToString(", ")}.",
                        originalQuestion = q
                    )
                }
                hasDuplicateOptions -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.VAGUE_OPTIONS,
                        currentKey = q.correctAnswer,
                        flawReason = "Two or more options contain identical duplicate values.",
                        originalQuestion = q
                    )
                }
                q.correctAnswer.isNullOrBlank() -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.MISSING_KEY,
                        currentKey = null,
                        suggestedKey = null,
                        flawReason = "No answer key has been assigned for this question.",
                        recheckedExplanation = if (!q.explanation.isNullOrBlank()) {
                            "Existing explanation present: \"${q.explanation}\". Review and assign answer key."
                        } else null,
                        originalQuestion = q
                    )
                }
                q.explanation.isNullOrBlank() -> {
                    AuditQuestionReport(
                        qNum = q.num,
                        status = AuditIssueType.MISSING_EXPLANATION,
                        currentKey = q.correctAnswer,
                        suggestedKey = q.correctAnswer,
                        flawReason = "Answer key assigned (${q.correctAnswer}), but detailed explanation is missing.",
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
     * Parses the LLM's feedback (Phase 1, 2, 3 or combined) and maps against the QuestionPaper.
     * Guaranteed: Never flags WRONG_KEY if suggestedKey == currentKey.
     */
    fun parseAuditReport(rawInput: String, paper: QuestionPaper): List<AuditQuestionReport> {
        val qMap = paper.questions.associateBy { it.num }
        val parsedReports = mutableMapOf<Int, AuditQuestionReport>()

        val jsonArray = extractJsonArray(rawInput)
        if (jsonArray != null) {
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val qNum = obj.optInt("qNum", -1)
                if (qNum <= 0) continue

                val originalQ = qMap[qNum]
                val statusStr = obj.optString("status", "").uppercase()
                val currentKey = obj.optString("currentKey", originalQ?.correctAnswer ?: "").ifBlank { originalQ?.correctAnswer }
                val suggestedKey = obj.optString("suggestedKey", obj.optString("correctedKey", "")).ifBlank { null }?.uppercase()
                val flawReason = obj.optString("flawReason", "").ifBlank { null }
                val recheckedExplanation = obj.optString("recheckedExplanation", obj.optString("detailedExplanation", "")).ifBlank { null }

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

                // Strict key-matching check: Never mark WRONG_KEY if suggested == current
                val isKeyIdentical = suggestedKey != null && currentKey != null && suggestedKey.equals(currentKey, ignoreCase = true)
                val isTrueKeyMismatch = suggestedKey != null && currentKey != null && !isKeyIdentical

                val status = when {
                    replacementQ != null -> AuditIssueType.FLAWED_QUESTION
                    statusStr.contains("FLAW") -> AuditIssueType.FLAWED_QUESTION
                    statusStr.contains("VAGUE") -> AuditIssueType.VAGUE_OPTIONS
                    statusStr.contains("MISSING_OPT") -> AuditIssueType.MISSING_OPTIONS
                    statusStr.contains("MISSING_KEY") -> AuditIssueType.MISSING_KEY
                    isTrueKeyMismatch -> AuditIssueType.WRONG_KEY
                    isKeyIdentical && flawReason.isNullOrBlank() -> AuditIssueType.CLEAN
                    isKeyIdentical && flawReason?.contains("flaw", ignoreCase = true) == true -> AuditIssueType.FLAWED_QUESTION
                    statusStr.contains("WRONG") && !isKeyIdentical -> AuditIssueType.WRONG_KEY
                    statusStr.contains("CLEAN") || statusStr.contains("VALID") -> AuditIssueType.CLEAN
                    else -> if (isTrueKeyMismatch) AuditIssueType.WRONG_KEY else AuditIssueType.CLEAN
                }

                parsedReports[qNum] = AuditQuestionReport(
                    qNum = qNum,
                    status = status,
                    currentKey = currentKey,
                    suggestedKey = if (isTrueKeyMismatch) suggestedKey else (if (isKeyIdentical) currentKey else suggestedKey),
                    flawReason = flawReason,
                    recheckedExplanation = recheckedExplanation,
                    replacementQuestion = replacementQ,
                    originalQuestion = originalQ,
                    isVerified = originalQ?.isVerified ?: false,
                    isNeutralized = originalQ?.isNeutralized ?: false
                )
            }
        }

        // Fallback to text parsing if no JSON objects were extracted
        if (parsedReports.isEmpty()) {
            parseTextReport(rawInput, qMap, parsedReports)
        }

        // Fill remaining questions from heuristic pre-audit
        val heuristic = runHeuristicPreAudit(paper).associateBy { it.qNum }
        return paper.questions.map { q ->
            parsedReports[q.num] ?: heuristic[q.num] ?: AuditQuestionReport(
                qNum = q.num,
                status = AuditIssueType.CLEAN,
                currentKey = q.correctAnswer,
                originalQuestion = q,
                isVerified = q.isVerified,
                isNeutralized = q.isNeutralized
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
                // Only mark wrong key if cur != sug
                if (!cur.equals(sug, ignoreCase = true)) {
                    parsedReports[qNum] = AuditQuestionReport(
                        qNum = qNum,
                        status = AuditIssueType.WRONG_KEY,
                        currentKey = cur,
                        suggestedKey = sug,
                        flawReason = remainder,
                        recheckedExplanation = "Rechecked derivation: Option $sug is verified as the correct answer (previously marked as $cur).",
                        originalQuestion = originalQ
                    )
                }
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
     * Aggregates summary statistics for UI KPI cards and stepper badges.
     */
    fun computeMetrics(reports: List<AuditQuestionReport>): AuditSummaryMetrics {
        var wrongKeys = 0
        var missingKeys = 0
        var flawed = 0
        var missingOpts = 0
        var clean = 0
        var verified = 0
        var neutralized = 0

        reports.forEach { r ->
            if (r.isNeutralized) {
                neutralized++
            } else if (r.isVerified) {
                verified++
                clean++
            } else {
                when (r.status) {
                    AuditIssueType.WRONG_KEY -> wrongKeys++
                    AuditIssueType.MISSING_KEY -> missingKeys++
                    AuditIssueType.FLAWED_QUESTION, AuditIssueType.VAGUE_OPTIONS -> flawed++
                    AuditIssueType.MISSING_OPTIONS -> missingOpts++
                    AuditIssueType.CLEAN, AuditIssueType.MISSING_EXPLANATION -> clean++
                }
            }
        }

        return AuditSummaryMetrics(
            totalAudited = reports.size,
            wrongKeysCount = wrongKeys,
            missingKeysCount = missingKeys,
            flawedQuestionsCount = flawed,
            missingOptionsCount = missingOpts,
            cleanCount = clean,
            verifiedCount = verified,
            neutralizedCount = neutralized
        )
    }
}
