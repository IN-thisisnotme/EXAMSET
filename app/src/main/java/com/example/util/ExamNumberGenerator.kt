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
     * Extracts or derives a single booklet code / number (e.g. "1", "2", "3", "4", "A", "B") from set title or metadata.
     */
    fun extractBookletCode(raw: String): String {
        val trimmed = raw.trim().uppercase()
        if (trimmed.startsWith("SET ")) {
            return trimmed.substring(4).trim().take(4).ifBlank { "1" }
        }
        if (trimmed.startsWith("SET-")) {
            return trimmed.substring(4).trim().take(4).ifBlank { "1" }
        }
        if (trimmed.startsWith("SET")) {
            return trimmed.substring(3).trim().take(4).ifBlank { "1" }
        }
        return trimmed.take(4).ifBlank { "1" }
    }

    /**
     * Derives a dynamic list of booklet set codes (e.g. ["1", "2", "3", "4"], ["A", "B", "C", "D"], or ["P", "Q", "R", "S"])
     * accommodating arbitrary n number of sets beyond 4 or D.
     */
    fun resolveBookletSets(activeSetCode: String): List<String> {
        val code = extractBookletCode(activeSetCode)
        if (code.length == 1 && code[0] in 'A'..'Z') {
            val char = code[0]
            return when {
                char in 'P'..'S' -> listOf("P", "Q", "R", "S")
                char in 'W'..'Z' -> listOf("W", "X", "Y", "Z")
                char > 'D' -> ('A'..minOf('H', char)).map { it.toString() }
                else -> listOf("A", "B", "C", "D")
            }
        }
        if (code.all { it.isDigit() } && code.isNotBlank()) {
            val num = code.toIntOrNull() ?: 1
            val maxNum = maxOf(4, num)
            return (1..maxNum).map { it.toString() }
        }
        return listOf("1", "2", "3", "4")
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

