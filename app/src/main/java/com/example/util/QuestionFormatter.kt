package com.example.util

import com.example.model.McqQuestion
import com.example.model.QuestionOptions

/**
 * Common question-formatting and question-type detection engine.
 *
 * Ported/adapted from the web questionFormatter.ts.
 *
 * This class does NOT modify the original question text.
 * It only detects and breaks structured questions into reusable parts.
 */
object QuestionFormatter {

    // -------------------------------------------------------------------------
    // Data classes
    // -------------------------------------------------------------------------

    data class AssertionReasonParts(
        val leadIn: String? = null,
        val assertion: String,
        val reason: String,
        val leadOut: String? = null
    )

    data class StatementItem(
        val label: String,
        val text: String
    )

    data class StatementQuestionParts(
        val leadIn: String? = null,
        val statements: List<StatementItem>,
        val leadOut: String? = null
    )

    enum class QuestionKind {
        ASSERTION_REASON,
        MATCH_THE_FOLLOWING,
        STATEMENT,
        PAIR,
        CHRONOLOGICAL,
        STANDARD
    }

    // -------------------------------------------------------------------------
    // Assertion–Reason
    // -------------------------------------------------------------------------

    /**
     * Parses an Assertion–Reason question.
     *
     * Supports examples such as:
     *
     * Assertion: ........
     * Reason: ........
     *
     * Assertion (A): ........
     * Reason (R): ........
     *
     * (A) Assertion: ........
     * (R) Reason: ........
     *
     * Hindi:
     *
     * अभिकथन: ........
     * कारण: ........
     */
    fun parseAssertionReason(
        text: String
    ): AssertionReasonParts? {

        if (text.isBlank()) return null

        val normalized = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()

        val assertionRegex = Regex(
            pattern = """(?im)(?:^|\n)\s*(?:(?:\*\*|__)?\s*)?(?:Assertion\s*(?:\([a-zA-Z]\)|[a-zA-Z])?|अभिकथन\s*(?:\([a-zA-Z\u0900-\u097F]\)|[a-zA-Z\u0900-\u097F])?|\([Aa]\)\s*Assertion)\s*(?:\*\*|__)?\s*[:\-–—]?\s*(?:\*\*|__)?"""
        )

        val reasonRegex = Regex(
            pattern = """(?im)(?:^|\n)\s*(?:(?:\*\*|__)?\s*)?(?:Reason\s*(?:\([a-zA-Z]\)|[a-zA-Z])?|कारण\s*(?:\([a-zA-Z\u0900-\u097F]\)|[a-zA-Z\u0900-\u097F])?|\([Rr]\)\s*Reason)\s*(?:\*\*|__)?\s*[:\-–—]?\s*(?:\*\*|__)?"""
        )

        val assertionMatch = assertionRegex.find(normalized)
            ?: return null

        val reasonMatch = reasonRegex.find(normalized)
            ?: return null

        // Reason must occur after Assertion.
        if (reasonMatch.range.first <= assertionMatch.range.first) {
            return null
        }

        // ---------------------------------------------------------------------
        // Lead-in
        // ---------------------------------------------------------------------

        val rawLeadIn = normalized
            .substring(0, assertionMatch.range.first)
            .trim()

        val cleanLeadIn = rawLeadIn
            .replace(
                Regex(
                    """(?i)^\s*(?:#{1,6}\s*)?(?:Assertion[–\- ]Reason|अभिकथन[–\- ]कारण)\s*[:.\-]?\s*"""
                ),
                ""
            )
            .trim()

        val leadIn = cleanLeadIn
            .takeIf { it.isNotBlank() }

        // ---------------------------------------------------------------------
        // Assertion
        // ---------------------------------------------------------------------

        val assertionStart = assertionMatch.range.last + 1

        val rawAssertion = normalized
            .substring(assertionStart, reasonMatch.range.first)
            .trim()

        // ---------------------------------------------------------------------
        // Reason
        // ---------------------------------------------------------------------

        val reasonStart = reasonMatch.range.last + 1

        val afterReason = normalized
            .substring(reasonStart.coerceAtMost(normalized.length))
            .trim()

        // ---------------------------------------------------------------------
        // Detect trailing instruction / lead-out.
        // ---------------------------------------------------------------------

        val leadOutRegex = Regex(
            pattern = """(?is)(?:^|\n)\s*(?:(?:\*\*|__)?\s*)?(In\s+(?:the\s+)?light\s+of\s+the\s+above\s+statements|Select\s+the\s+correct\s+answer|Choose\s+the\s+correct|Which\s+of\s+the\s+following\s+is\s+correct|उपर्युक्त\s+कथनों\s+के\s+आलोक\s+में|उपरोक्त\s+कथनों\s+के|Codes?\s*[:\-]|कूट\s*[:\-])[\s\S]*$"""
        )

        val leadOutMatch = leadOutRegex.find(afterReason)

        val rawReason: String
        val leadOut: String?

        if (leadOutMatch != null) {
            rawReason = afterReason
                .substring(0, leadOutMatch.range.first)
                .trim()

            leadOut = leadOutMatch.value.trim()
        } else {
            rawReason = afterReason
            leadOut = null
        }

        val assertion = rawAssertion
            .replace(Regex("""^[:\-–—]\s*"""), "")
            .trim()

        val reason = rawReason
            .replace(Regex("""^[:\-–—]\s*"""), "")
            .trim()

        if (assertion.isBlank() || reason.isBlank()) {
            return null
        }

        return AssertionReasonParts(
            leadIn = leadIn,
            assertion = assertion,
            reason = reason,
            leadOut = leadOut
        )
    }

    // -------------------------------------------------------------------------
    // Statement questions
    // -------------------------------------------------------------------------

    /**
     * Parses:
     *
     * Statement I: ........
     * Statement II: ........
     *
     * or:
     *
     * कथन I: ........
     * कथन II: ........
     *
     * Multiple statements are supported.
     */
    fun parseStatementQuestion(
        text: String
    ): StatementQuestionParts? {

        if (text.isBlank()) return null

        val normalized = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val statementRegex = Regex(
            pattern = """(?im)(?:^|\n)\s*(?:(?:\*\*|__)?\s*)?(?:Statement\s*(?:[IVX]+|\d+)|कथन\s*(?:[IVX]+|\d+))\s*[:\-–—]\s*(?:\*\*|__)?"""
        )

        val matches = statementRegex.findAll(normalized).toList()

        if (matches.size < 2) {
            return null
        }

        val firstMatch = matches.first()

        val leadIn = normalized
            .substring(0, firstMatch.range.first)
            .trim()
            .takeIf { it.isNotBlank() }

        val statements = mutableListOf<StatementItem>()

        for (index in matches.indices) {

            val current = matches[index]
            val next = matches.getOrNull(index + 1)

            val start = current.range.last + 1
            val end = next?.range?.first ?: normalized.length

            var body = normalized
                .substring(start, end)
                .trim()

            val label = extractStatementLabel(current.value)

            // Last statement may contain a trailing instruction.
            if (index == matches.lastIndex) {

                val leadOutRegex = Regex(
                    pattern = """(?is)(?:^|\n)\s*(?:In\s+(?:the\s+)?light\s+of|Choose\s+the\s+correct|Which\s+of\s+the\s+above)[\s\S]*$"""
                )

                val leadOutMatch = leadOutRegex.find(body)

                if (leadOutMatch != null) {

                    val leadOut = leadOutMatch.value.trim()

                    body = body
                        .substring(0, leadOutMatch.range.first)
                        .trim()

                    statements.add(
                        StatementItem(
                            label = label,
                            text = body
                        )
                    )

                    return StatementQuestionParts(
                        leadIn = leadIn,
                        statements = statements,
                        leadOut = leadOut
                    )
                }
            }

            if (body.isNotBlank()) {
                statements.add(
                    StatementItem(
                        label = label,
                        text = body
                    )
                )
            }
        }

        return if (statements.size >= 2) {
            StatementQuestionParts(
                leadIn = leadIn,
                statements = statements
            )
        } else {
            null
        }
    }

    private fun extractStatementLabel(
        raw: String
    ): String {

        val cleaned = raw
            .replace("*", "")
            .replace("_", "")
            .replace(":", "")
            .replace("–", "")
            .replace("—", "")
            .replace("-", "")
            .replace("\n", " ")
            .trim()

        return cleaned
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    // -------------------------------------------------------------------------
    // Match the Following
    // -------------------------------------------------------------------------

    /**
     * Detects Match-the-Following questions.
     *
     * This intentionally detects rather than restructures the question.
     * The original question text remains untouched.
     */
    fun isMatchTheFollowing(
        questionText: String,
        options: QuestionOptions? = null,
        qType: String? = null
    ): Boolean {

        val type = qType.orEmpty().lowercase()

        if (
            type.contains("match") ||
            type.contains("matching") ||
            type.contains("pair")
        ) {
            return true
        }

        val text = questionText.lowercase()

        if (
            text.contains("match the following") ||
            text.contains("match the columns") ||
            text.contains("match column i") ||
            text.contains("match column ii") ||
            text.contains("column i") && text.contains("column ii") ||
            text.contains("list i") && text.contains("list ii") ||
            text.contains("सूची i") && text.contains("सूची ii") ||
            text.contains("कॉलम i") && text.contains("कॉलम ii")
        ) {
            return true
        }

        val optionText = options
            ?.asList
            ?.joinToString(" ")
            ?.lowercase()
            .orEmpty()

        return optionText.contains("match") ||
            optionText.contains("column i") ||
            optionText.contains("column ii")
    }

    // -------------------------------------------------------------------------
    // Pair / chronological detection
    // -------------------------------------------------------------------------

    fun isPairQuestion(
        questionText: String,
        qType: String? = null
    ): Boolean {

        val type = qType.orEmpty().lowercase()
        val text = questionText.lowercase()

        return type.contains("pair") ||
            text.contains("correctly matched pair") ||
            text.contains("correctly matched pairs") ||
            text.contains("incorrectly matched pair") ||
            text.contains("pair correctly")
    }

    fun isChronologicalQuestion(
        questionText: String,
        qType: String? = null
    ): Boolean {

        val type = qType.orEmpty().lowercase()
        val text = questionText.lowercase()

        return type.contains("chronological") ||
            text.contains("chronological order") ||
            text.contains("correct chronological sequence") ||
            text.contains("arrange the following") ||
            text.contains("arranged in chronological")
    }

    // -------------------------------------------------------------------------
    // Assertion–Reason detection
    // -------------------------------------------------------------------------

    fun isAssertionReason(
        questionText: String,
        options: QuestionOptions? = null,
        qType: String? = null
    ): Boolean {

        if (
            qType?.contains(
                "assertion",
                ignoreCase = true
            ) == true
        ) {
            return true
        }

        if (parseAssertionReason(questionText) != null) {
            return true
        }

        val qLower = questionText.lowercase()

        val optLower = options
            ?.asList
            ?.joinToString(" ")
            ?.lowercase()
            .orEmpty()

        return (
            qLower.contains("assertion") &&
                qLower.contains("reason")
            ) ||
            (
                qLower.contains("अभिकथन") &&
                    qLower.contains("कारण")
                ) ||
            optLower.contains("both a and r are true") ||
            optLower.contains("both assertion (a) and reason (r)") ||
            optLower.contains("correct explanation of a") ||
            optLower.contains("r is the correct explanation") ||
            optLower.contains("a is true, but r is false") ||
            optLower.contains("assertion (a) is true")
    }

    // -------------------------------------------------------------------------
    // Statement detection
    // -------------------------------------------------------------------------

    fun isStatementQuestion(
        questionText: String,
        qType: String? = null
    ): Boolean {

        if (
            qType?.contains(
                "statement",
                ignoreCase = true
            ) == true
        ) {
            return true
        }

        return parseStatementQuestion(questionText) != null
    }

    // -------------------------------------------------------------------------
    // Overall question type
    // -------------------------------------------------------------------------

    fun detectQuestionKind(
        question: McqQuestion
    ): QuestionKind {

        if (
            isAssertionReason(
                question.question,
                question.options,
                question.questionType
            )
        ) {
            return QuestionKind.ASSERTION_REASON
        }

        if (
            isMatchTheFollowing(
                question.question,
                question.options,
                question.questionType
            )
        ) {
            return QuestionKind.MATCH_THE_FOLLOWING
        }

        if (
            isStatementQuestion(
                question.question,
                question.questionType
            )
        ) {
            return QuestionKind.STATEMENT
        }

        if (
            isPairQuestion(
                question.question,
                question.questionType
            )
        ) {
            return QuestionKind.PAIR
        }

        if (
            isChronologicalQuestion(
                question.question,
                question.questionType
            )
        ) {
            return QuestionKind.CHRONOLOGICAL
        }

        return QuestionKind.STANDARD
    }

    // -------------------------------------------------------------------------
    // Display helpers
    // -------------------------------------------------------------------------

    /**
     * Returns question text with excessive blank lines removed,
     * while preserving one intentional line break.
     *
     * Example:
     *
     * Assertion: ABC
     *
     *
     * Reason: XYZ
     *
     * becomes:
     *
     * Assertion: ABC
     * Reason: XYZ
     */
    fun normalizeQuestionSpacing(
        text: String
    ): String {

        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n[ \t]+"""), "\n")
            .replace(Regex("""\n{2,}"""), "\n")
            .trim()
    }

    /**
     * Normalizes an individual option without destroying intentional
     * line breaks.
     */
    fun normalizeOptionSpacing(
        text: String
    ): String {

        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n[ \t]+"""), "\n")
            .replace(Regex("""\n{2,}"""), "\n")
            .trim()
    }

    // -------------------------------------------------------------------------
    // Option layout
    // -------------------------------------------------------------------------

    /**
     * Determines whether options should be displayed vertically.
     *
     * Same core rules as the web questionFormatter.ts:
     *
     * - Assertion–Reason → stacked
     * - Match → stacked
     * - Statement → stacked
     * - Pair → stacked
     * - Chronological → stacked
     * - Newline in option → stacked
     * - Long option → stacked
     */
    fun shouldStackOptions(
        qType: String? = null,
        options: QuestionOptions? = null
    ): Boolean {

        if (options == null) {
            return false
        }

        val type = qType.orEmpty().lowercase()

        if (
            type.contains("assertion") ||
            type.contains("match") ||
            type.contains("statement") ||
            type.contains("pair") ||
            type.contains("chronological")
        ) {
            return true
        }

        val allOptions = options.asList
            .map { it.second }
            .filter { it.isNotBlank() }

        return allOptions.any {
            it.contains("\n") ||
                it.length > 30
        }
    }

    /**
     * Type-aware version of shouldStackOptions().
     *
     * This is preferable for new UI code because it does not depend
     * only on the questionType string.
     */
    fun shouldStackOptions(
        question: McqQuestion
    ): Boolean {

        val kind = detectQuestionKind(question)

        if (
            kind == QuestionKind.ASSERTION_REASON ||
            kind == QuestionKind.MATCH_THE_FOLLOWING ||
            kind == QuestionKind.STATEMENT ||
            kind == QuestionKind.PAIR ||
            kind == QuestionKind.CHRONOLOGICAL
        ) {
            return true
        }

        return shouldStackOptions(
            qType = question.questionType,
            options = question.options
        )
    }

    // -------------------------------------------------------------------------
    // Structured detection
    // -------------------------------------------------------------------------

    fun isStructured(
        question: McqQuestion
    ): Boolean {
        return detectQuestionKind(question) != QuestionKind.STANDARD
    }

    /**
     * Returns a human-readable type label for the UI.
     */
    fun displayType(
        question: McqQuestion
    ): String? {

        return when (detectQuestionKind(question)) {

            QuestionKind.ASSERTION_REASON ->
                "Assertion–Reason"

            QuestionKind.MATCH_THE_FOLLOWING ->
                "Match the Following"

            QuestionKind.STATEMENT ->
                "Statement"

            QuestionKind.PAIR ->
                "Pair Based"

            QuestionKind.CHRONOLOGICAL ->
                "Chronological"

            QuestionKind.STANDARD ->
                question.questionType
                    ?.takeIf { it.isNotBlank() }
        }
    }
}