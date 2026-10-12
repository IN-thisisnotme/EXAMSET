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
        assertEquals("A", ExamNumberGenerator.extractBookletCode("Set A"))
        assertEquals("B", ExamNumberGenerator.extractBookletCode("SET-B"))
        assertEquals("C", ExamNumberGenerator.extractBookletCode("SET C"))
        assertEquals("P", ExamNumberGenerator.extractBookletCode("Set P"))
        assertEquals("1", ExamNumberGenerator.extractBookletCode("Set 1"))
    }

    @Test
    fun testResolveBookletSets() {
        assertEquals(listOf("A", "B", "C", "D"), ExamNumberGenerator.resolveBookletSets("Set A"))
        assertEquals(listOf("P", "Q", "R", "S"), ExamNumberGenerator.resolveBookletSets("Set P"))
        assertEquals(listOf("1", "2", "3", "4"), ExamNumberGenerator.resolveBookletSets("Set 1"))
        val extended = ExamNumberGenerator.resolveBookletSets("Set F")
        assertTrue("Extended sets should contain F", extended.contains("F"))
    }
}
