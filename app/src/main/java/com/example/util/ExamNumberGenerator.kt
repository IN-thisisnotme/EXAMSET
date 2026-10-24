package com.example.util

import java.util.Random

/**
 * Standardized candidate Roll Number and Test Booklet Number generator for competitive OMR exams.
 * Ensures consistent 10-digit Roll Numbers and 7-digit Test Booklet Numbers suitable for
 * optical mark recognition, psychometric analysis, and candidate cohort tracking.
 */
object ExamNumberGenerator {

    private val random = Random()

    /**
     * Generates a 10-digit candidate Roll Number.
     * Default prefix: current year e.g. "2026" followed by 6 random digits.
     */
    fun generateRollNumber(prefix: String = "2026", totalLength: Int = 10): String {
        val cleanPrefix = prefix.filter { it.isDigit() }
        val remainingLength = (totalLength - cleanPrefix.length).coerceAtLeast(1)
        val sb = StringBuilder(cleanPrefix)
        repeat(remainingLength) {
            sb.append(random.nextInt(10))
        }
        return sb.toString().take(totalLength).padStart(totalLength, '0')
    }

    /**
     * Generates a 7-digit Test Booklet Number.
     * e.g., "7104523"
     */
    fun generateBookletNumber(length: Int = 7): String {
        val firstDigit = random.nextInt(9) + 1 // 1..9 (non-zero leading)
        val sb = StringBuilder().append(firstDigit)
        repeat(length - 1) {
            sb.append(random.nextInt(10))
        }
        return sb.toString()
    }

    /**
     * Generates a batch of sequential 10-digit roll numbers for a student cohort.
     */
    fun generateSequentialRollNumbers(
        startNumber: Long = 2026100001L,
        count: Int = 50,
        length: Int = 10
    ): List<String> {
        return (0 until count).map { i ->
            (startNumber + i).toString().padStart(length, '0').takeLast(length)
        }
    }

    /**
     * Generates a batch of sequential 7-digit booklet numbers for examination sets.
     */
    fun generateSequentialBookletNumbers(
        startNumber: Long = 7100001L,
        count: Int = 10,
        length: Int = 7
    ): List<String> {
        return (0 until count).map { i ->
            (startNumber + i).toString().padStart(length, '0').takeLast(length)
        }
    }

    /**
     * Normalizes a user-input or imported roll number into a strict 10-digit string.
     */
    fun normalizeRollNumber(raw: String, length: Int = 10): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= length) {
            digits.take(length)
        } else {
            digits.padStart(length, '0')
        }
    }

    /**
     * Normalizes a user-input or imported booklet number into a strict 7-digit string.
     */
    fun normalizeBookletNumber(raw: String, length: Int = 7): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= length) {
            digits.take(length)
        } else {
            digits.padStart(length, '0')
        }
    }

    /**
     * Extracts or derives a numeric booklet set code from set title or metadata.
     */
    fun extractBookletCode(raw: String): String {
        return normalizeSetNumber(raw)
    }

    /**
     * Normalizes any set reference into a strict 2-digit numerical string from "00" to "99".
     * E.g. "1" -> "01", "01" -> "01", "Set 2" -> "02", "15" -> "15", "Set A" -> "01", "B" -> "02".
     */
    fun normalizeSetNumber(raw: String): String {
        val trimmed = raw.trim().uppercase()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isNotBlank()) {
            val num = digits.toIntOrNull() ?: 1
            val clamped = num.coerceIn(0, 99)
            return String.format(java.util.Locale.US, "%02d", clamped)
        }
        val clean = if (trimmed.startsWith("SET ")) trimmed.substring(4).trim()
            else if (trimmed.startsWith("SET-")) trimmed.substring(4).trim()
            else if (trimmed.startsWith("SET")) trimmed.substring(3).trim()
            else trimmed
        if (clean.length == 1 && clean[0] in 'A'..'Z') {
            val letterNum = (clean[0] - 'A' + 1).coerceIn(1, 99)
            return String.format(java.util.Locale.US, "%02d", letterNum)
        }
        return "01"
    }

    /**
     * Derives a dynamic list of numeric booklet set codes (e.g. ["01", "02", "03", "04"]).
     */
    fun resolveBookletSets(activeSetCode: String): List<String> {
        val numStr = normalizeSetNumber(activeSetCode)
        val num = numStr.toIntOrNull() ?: 1
        val maxNum = maxOf(4, num)
        return (1..minOf(99, maxNum)).map { String.format(java.util.Locale.US, "%02d", it) }
    }

    /**
     * Generates a standardized OMR Candidate Manifest CSV for psychometricians and chief paper setters.
     * Contains Roll Number, Booklet Number, Set Code, Subject, Exam Title, Total Questions, and Answer Keys.
     */
    fun generateOmrCohortManifestCsv(papers: List<com.example.model.QuestionPaper>): String {
        val sb = StringBuilder()
        sb.append("Roll_Number,Booklet_Number,Set_Code,Subject,Exam_Title,Total_Questions,Answer_Keys\n")
        papers.forEach { p ->
            val roll = if (p.metadata.rollNumber.isNotBlank()) normalizeRollNumber(p.metadata.rollNumber, 10) else "0000000001"
            val booklet = if (p.metadata.bookletNumber.isNotBlank()) normalizeBookletNumber(p.metadata.bookletNumber, 7) else "0000001"
            val set = extractBookletCode(p.metadata.setNumber)
            val subject = p.metadata.subject.replace("\"", "\"\"")
            val title = p.metadata.title.replace("\"", "\"\"")
            val count = p.questions.size
            val keyString = p.questions.joinToString(";") { q ->
                "${q.num}:${q.correctAnswer ?: "-"}"
            }.replace("\"", "\"\"")
            sb.append("\"$roll\",\"$booklet\",\"$set\",\"$subject\",\"$title\",$count,\"$keyString\"\n")
        }
        return sb.toString()
    }
}

