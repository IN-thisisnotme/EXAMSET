package com.example.util

import com.example.model.McqQuestion
import com.example.model.MsqScoringPolicy
import kotlin.math.roundToInt

/**
 * Single source of truth for single-choice (MCQ) and multiple-selection (MSQ)
 * answer key normalization, MSQ type detection, and scoring.
 */
object AnswerKeyMatcher {

    enum class QuestionGradeStatus {
        NEUTRALIZED,
        UNANSWERED,
        CORRECT,
        PARTIAL,
        INCORRECT
    }

    data class QuestionScoreResult(
        val status: QuestionGradeStatus,
        val marks: Double,
        val chosenCorrectCount: Int,
        val chosenWrongCount: Int,
        val expectedCount: Int
    ) {
        val isFullyCorrect: Boolean
            get() = status == QuestionGradeStatus.CORRECT || status == QuestionGradeStatus.NEUTRALIZED
    }

    private val VALID_LETTERS = setOf("A", "B", "C", "D", "E")

    private val FILLER_TOKENS = setOf(
        "AND", "OR", "BOTH", "OPTION", "OPTIONS", "OPT", "ANS", "ANSWER", "KEY", "KEYS",
        "CORRECT", "CHOICE", "CHOICES", "ONLY", "NONE", "SELECT", "ALL"
    )

    private val EXPLICIT_MSQ_STEM_REGEX = Regex(
        pattern = """(?i)(\[(MSQ|MULTI[- ]?SELECT)\]|\((MSQ|MULTI[- ]?SELECT)\)|\b(select|choose|pick|mark)\s+all(\s+options|\s+answers|\s+choices|\s+correct|\s+statements)?\s+that\s+apply\b|\bselect\s+all\s+correct(\s+options|\s+answers|\s+choices)?\b|\bchoose\s+all\s+correct(\s+options|\s+answers|\s+choices)?\b|\bmore\s+than\s+one\s+(option|answer|choice)\s+(is|are|may\s+be)\s+correct\b|\bone\s+or\s+more\s+(than\s+one\s+)?(options?|answers?|choices?)\s+(is|are|may\s+be)\s+correct\b|\bmultiple\s+(options?|answers?|choices?)\s+(are|may\s+be)\s+correct\b)"""
    )

    /**
     * Parses any normalized or semi-structured key string (e.g., "c, a", "(A, C)", "A & B", "1, 3", "AC")
     * into a canonically ordered set of uppercase option letters (A..E).
     */
    fun parseSelectedLetters(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()

        var text = raw.trim().uppercase()
        // Strip leading labels if callers pass them through
        text = text.replace(Regex("""^(CORRECT\s+)?(ANSWERS?|ANS|KEYS?|OPTIONS?)\s*[:=\-–—]\s*"""), "")
        text = text.replace(Regex("""\bAND\b"""), ",")

        val tokens = text.split(Regex("""[,;&/+|\s()\[\]{}]+"""))
            .map { it.trim().trimEnd('.', ':') }
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) return emptySet()

        val collected = linkedSetOf<String>()
        for (token in tokens) {
            when {
                token in VALID_LETTERS -> collected.add(token)
                token in FILLER_TOKENS -> continue
                token == "1" -> collected.add("A")
                token == "2" -> collected.add("B")
                token == "3" -> collected.add("C")
                token == "4" -> collected.add("D")
                token == "5" -> collected.add("E")
                token.length in 2..5 &&
                    token.all { it in 'A'..'E' } &&
                    token.toSet().size == token.length -> {
                    token.forEach { ch -> collected.add(ch.toString()) }
                }
                else -> return emptySet()
            }
        }

        return collected.sorted().toCollection(linkedSetOf())
    }

    /**
     * Produces canonical comma-separated sorted key string (e.g., "A,C") or "" if invalid/blank.
     */
    fun normalizeKey(raw: String?): String {
        val letters = parseSelectedLetters(raw)
        return if (letters.isEmpty()) "" else letters.joinToString(",")
    }

    /**
     * Produces human-readable comma+space separated key string (e.g., "A, C") or "".
     */
    fun formatKeyForDisplay(raw: String?): String {
        val letters = parseSelectedLetters(raw)
        return if (letters.isEmpty()) "" else letters.joinToString(", ")
    }

    /**
     * Checks whether [optionLetter] is present in [rawKey].
     */
    fun containsOption(rawKey: String?, optionLetter: String): Boolean {
        val letter = optionLetter.trim().uppercase()
        if (letter !in VALID_LETTERS) return false
        return letter in parseSelectedLetters(rawKey)
    }

    /**
     * Toggles [optionLetter] inside [currentSelection] and returns normalized sorted CSV (or "" if empty).
     */
    fun toggleSelection(currentSelection: String?, optionLetter: String): String {
        val letter = optionLetter.trim().uppercase()
        if (letter !in VALID_LETTERS) return normalizeKey(currentSelection)
        val set = parseSelectedLetters(currentSelection).toMutableSet()
        if (!set.add(letter)) {
            set.remove(letter)
        }
        return if (set.isEmpty()) "" else set.sorted().joinToString(",")
    }

    /**
     * Compares two keys cleanly regardless of ordering, parentheses, or whitespace.
     */
    fun areKeysEquivalent(a: String?, b: String?): Boolean {
        val setA = parseSelectedLetters(a)
        val setB = parseSelectedLetters(b)
        return setA.isNotEmpty() && setA == setB
    }

    /**
     * True only when [questionType] explicitly designates MSQ / multi-select.
     * Deliberately excludes "Multiple Choice", "MCQ", or "Multiple Statements".
     */
    fun isExplicitMsqType(questionType: String?): Boolean {
        if (questionType.isNullOrBlank()) return false
        val norm = questionType.trim().uppercase().replace('_', '-').replace(Regex("\\s+"), " ")
        if (norm.contains("MULTIPLE CHOICE") || norm == "MCQ" || norm.contains("SINGLE") || norm.contains("STATEMENT")) {
            return false
        }
        return norm == "MSQ" ||
            norm.contains("MULTI-SELECT") ||
            norm.contains("MULTI SELECT") ||
            norm.contains("MULTIPLE-SELECT") ||
            norm.contains("MULTIPLE SELECT") ||
            norm.contains("MULTIPLE SELECTION") ||
            norm.contains("MORE THAN ONE") ||
            norm.contains("CHECKBOX")
    }

    /**
     * Detects explicit MSQ cues in question stems without triggering on UPSC single-choice statement MCQs
     * ("Consider the following statements... Which of the statements given above is/are correct?").
     */
    fun hasExplicitMsqStemCue(stem: String): Boolean {
        if (stem.isBlank()) return false
        return EXPLICIT_MSQ_STEM_REGEX.containsMatchIn(stem)
    }

    /**
     * Determines if [question] must behave and render as Multiple-Selection (checkboxes + MSQ badge).
     * Single-choice MCQs (including statement-based single-answer MCQs) always return false.
     */
    fun isMultiSelect(question: McqQuestion): Boolean {
        return parseSelectedLetters(question.correctAnswer).size >= 2 ||
            isExplicitMsqType(question.questionType) ||
            hasExplicitMsqStemCue(question.question)
    }

    /**
     * Grades [userAnswer] against [question] under [msqPolicy], rounding partial/net marks to 2 decimals.
     */
    fun scoreQuestion(
        question: McqQuestion,
        userAnswer: String?,
        positiveMarks: Double,
        negativeMarks: Double,
        unattendedMarks: Double = 0.0,
        msqPolicy: MsqScoringPolicy = MsqScoringPolicy.ALL_OR_NOTHING
    ): QuestionScoreResult {
        val pos = if (positiveMarks > 0.0) positiveMarks else 4.0
        val neg = if (negativeMarks >= 0.0) negativeMarks else 1.0
        val unk = if (unattendedMarks >= 0.0) unattendedMarks else 0.0

        val expected = parseSelectedLetters(question.correctAnswer)
        if (question.isNeutralized) {
            return QuestionScoreResult(
                status = QuestionGradeStatus.NEUTRALIZED,
                marks = roundTwoDecimals(pos),
                chosenCorrectCount = expected.size,
                chosenWrongCount = 0,
                expectedCount = expected.size
            )
        }

        val selected = parseSelectedLetters(userAnswer)
        if (selected.isEmpty()) {
            // Preserve compatibility with existing callers if raw userAnswer was non-blank garbage vs genuinely unanswered
            if (userAnswer.isNullOrBlank()) {
                return QuestionScoreResult(
                    status = QuestionGradeStatus.UNANSWERED,
                    marks = roundTwoDecimals(-unk),
                    chosenCorrectCount = 0,
                    chosenWrongCount = 0,
                    expectedCount = expected.size
                )
            }
            return QuestionScoreResult(
                status = QuestionGradeStatus.INCORRECT,
                marks = roundTwoDecimals(-neg),
                chosenCorrectCount = 0,
                chosenWrongCount = 1,
                expectedCount = expected.size
            )
        }

        if (expected.isEmpty()) {
            return QuestionScoreResult(
                status = QuestionGradeStatus.INCORRECT,
                marks = roundTwoDecimals(-neg),
                chosenCorrectCount = 0,
                chosenWrongCount = selected.size,
                expectedCount = 0
            )
        }

        val chosenCorrect = selected.count { it in expected }
        val chosenWrong = selected.count { it !in expected }

        if (chosenWrong == 0 && chosenCorrect == expected.size) {
            return QuestionScoreResult(
                status = QuestionGradeStatus.CORRECT,
                marks = roundTwoDecimals(pos),
                chosenCorrectCount = chosenCorrect,
                chosenWrongCount = 0,
                expectedCount = expected.size
            )
        }

        val applyPartial = msqPolicy == MsqScoringPolicy.PARTIAL_STRICT &&
            expected.size >= 2 &&
            chosenWrong == 0 &&
            chosenCorrect in 1 until expected.size

        if (applyPartial) {
            val proportionalMarks = roundTwoDecimals(pos * (chosenCorrect.toDouble() / expected.size.toDouble()))
            return QuestionScoreResult(
                status = QuestionGradeStatus.PARTIAL,
                marks = proportionalMarks,
                chosenCorrectCount = chosenCorrect,
                chosenWrongCount = 0,
                expectedCount = expected.size
            )
        }

        return QuestionScoreResult(
            status = QuestionGradeStatus.INCORRECT,
            marks = roundTwoDecimals(-neg),
            chosenCorrectCount = chosenCorrect,
            chosenWrongCount = chosenWrong,
            expectedCount = expected.size
        )
    }

    private fun roundTwoDecimals(value: Double): Double = (value * 100.0).roundToInt() / 100.0
}
