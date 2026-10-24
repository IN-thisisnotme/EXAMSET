package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamNumberGeneratorTest {

    @Test
    fun testGenerateRollNumberFormat() {
        val roll = ExamNumberGenerator.generateRollNumber(prefix = "2026", totalLength = 10)
        assertEquals(10, roll.length)
        assertTrue("Roll number should start with prefix 2026", roll.startsWith("2026"))
        assertTrue("All characters must be digits", roll.all { it.isDigit() })
    }

    @Test
    fun testGenerateBookletNumberFormat() {
        val booklet = ExamNumberGenerator.generateBookletNumber(length = 7)
        assertEquals(7, booklet.length)
        assertTrue("All characters must be digits", booklet.all { it.isDigit() })
        assertTrue("First digit should not be 0", booklet[0] in '1'..'9')
    }

    @Test
    fun testSequentialNumberGeneration() {
        val rolls = ExamNumberGenerator.generateSequentialRollNumbers(startNumber = 2026100001L, count = 10, length = 10)
        assertEquals(10, rolls.size)
        assertEquals("2026100001", rolls[0])
        assertEquals("2026100010", rolls[9])

        val booklets = ExamNumberGenerator.generateSequentialBookletNumbers(startNumber = 7100001L, count = 5, length = 7)
        assertEquals(5, booklets.size)
        assertEquals("7100001", booklets[0])
        assertEquals("7100005", booklets[4])
    }

    @Test
    fun testExtractBookletCode() {
        assertEquals("01", ExamNumberGenerator.extractBookletCode("Set A"))
        assertEquals("02", ExamNumberGenerator.extractBookletCode("SET-B"))
        assertEquals("03", ExamNumberGenerator.extractBookletCode("SET C"))
        assertEquals("01", ExamNumberGenerator.extractBookletCode("Set 1"))
        assertEquals("05", ExamNumberGenerator.extractBookletCode("Set 5"))
        assertEquals("15", ExamNumberGenerator.extractBookletCode("Set 15"))
    }

    @Test
    fun testNormalizeSetNumber() {
        assertEquals("01", ExamNumberGenerator.normalizeSetNumber("1"))
        assertEquals("02", ExamNumberGenerator.normalizeSetNumber("Set 2"))
        assertEquals("05", ExamNumberGenerator.normalizeSetNumber("5"))
        assertEquals("15", ExamNumberGenerator.normalizeSetNumber("15"))
        assertEquals("01", ExamNumberGenerator.normalizeSetNumber("Set A"))
        assertEquals("02", ExamNumberGenerator.normalizeSetNumber("B"))
    }

    @Test
    fun testResolveBookletSets() {
        assertEquals(listOf("01", "02", "03", "04"), ExamNumberGenerator.resolveBookletSets("Set 1"))
        assertEquals(listOf("01", "02", "03", "04", "05"), ExamNumberGenerator.resolveBookletSets("Set 5"))
    }

    @Test
    fun testGenerateOmrCohortManifestCsv() {
        val q1 = com.example.model.McqQuestion(
            num = 1,
            question = "Sample Q1",
            options = com.example.model.QuestionOptions(a = "Opt A", b = "Opt B"),
            correctAnswer = "B"
        )
        val q2 = com.example.model.McqQuestion(
            num = 2,
            question = "Sample Q2",
            options = com.example.model.QuestionOptions(a = "Opt A", b = "Opt B"),
            correctAnswer = "A"
        )
        val paper = com.example.model.QuestionPaper(
            metadata = com.example.model.PaperMetadata(
                title = "Biology Assessment",
                subject = "Genetics",
                setNumber = "01",
                rollNumber = "2026100001",
                bookletNumber = "7100001"
            ),
            questions = listOf(q1, q2)
        )
        val csv = ExamNumberGenerator.generateOmrCohortManifestCsv(listOf(paper))
        assertTrue("CSV header must contain Roll_Number", csv.contains("Roll_Number,Booklet_Number,Set_Code"))
        assertTrue("CSV data must contain candidate roll number", csv.contains("\"2026100001\""))
        assertTrue("CSV data must contain booklet number", csv.contains("\"7100001\""))
        assertTrue("CSV data must contain answer key pattern", csv.contains("1:B;2:A"))
    }
}

