package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class McqParserPhasingTest {

    @Test
    fun testMultiPhaseReasoningFirstParsing() {
        val rawInput = """
            # Physics Core Practice Exam
            Subject: Physics
            Set: 01

            Phase 1: Questions

            Q1. What is the unit of electric resistance?
            (A) Volt
            (B) Ampere
            (C) Ohm
            (D) Watt

            Q2. Which law relates current, voltage, and resistance?
            (A) Ohm's Law
            (B) Faraday's Law
            (C) Newton's Law
            (D) Boyle's Law

            Phase 2: Detailed Explanations & Solution

            Q1.
            Ohm (Ω) is the SI unit of electrical resistance, named after Georg Simon Ohm. Volt is for potential, Ampere for current, Watt for power.

            Q2.
            Ohm's law states that the current through a conductor between two points is directly proportional to the voltage across the two points (V = IR).

            Phase 3: Answer Key

            Q1: (C)
            Q2: (A)
        """.trimIndent()

        val paper = McqParser.parse(rawInput)
        assertEquals(2, paper.questions.size)

        val q1 = paper.questions[0]
        assertEquals(1, q1.num)
        assertEquals("C", q1.correctAnswer)
        assertNotNull(q1.explanation)
        assert(q1.explanation!!.contains("SI unit of electrical resistance"))

        val q2 = paper.questions[1]
        assertEquals(2, q2.num)
        assertEquals("A", q2.correctAnswer)
        assertNotNull(q2.explanation)
        assert(q2.explanation!!.contains("Ohm's law states"))
    }

    @Test
    fun testMultiPhaseStandardParsing() {
        val rawInput = """
            # Chemistry Foundation Exam
            Subject: Chemistry
            Set: 02

            Phase 1: Questions

            Q1. What is the chemical symbol for Gold?
            (A) Ag
            (B) Au
            (C) Fe
            (D) Cu

            Phase 2: Answer Key

            Q1: (B)

            Phase 3: Explanations & Solution

            Q1.
            Au comes from the Latin word 'aurum', meaning shining dawn. Ag is silver, Fe is iron, Cu is copper.
        """.trimIndent()

        val paper = McqParser.parse(rawInput)
        assertEquals(1, paper.questions.size)

        val q1 = paper.questions[0]
        assertEquals("B", q1.correctAnswer)
        assertNotNull(q1.explanation)
        assert(q1.explanation!!.contains("Latin word 'aurum'"))
    }

    @Test
    fun testInlineReasoningFirstParsing() {
        val rawInput = """
            Q1. What is the powerhouse of the cell?
            (A) Nucleus
            (B) Ribosome
            (C) Mitochondria
            (D) Golgi body
            Explanation: Mitochondria generate most of the chemical energy needed to power the cell's biochemical reactions (ATP).
            Answer: C

            Q2. What gas do plants absorb during photosynthesis?
            (A) Oxygen
            (B) Carbon Dioxide
            (C) Nitrogen
            (D) Hydrogen
            Explanation: Plants absorb carbon dioxide (CO2) and water, converting them into glucose and oxygen using light energy.
            Answer: B
        """.trimIndent()

        val paper = McqParser.parse(rawInput)
        assertEquals(2, paper.questions.size)

        val q1 = paper.questions[0]
        assertEquals("C", q1.correctAnswer)
        assertNotNull(q1.explanation)
        assert(q1.explanation!!.contains("Mitochondria generate most of the chemical energy"))

        val q2 = paper.questions[1]
        assertEquals("B", q2.correctAnswer)
        assertNotNull(q2.explanation)
        assert(q2.explanation!!.contains("Plants absorb carbon dioxide"))
    }

    @Test
    fun testInlineStandardParsing() {
        val rawInput = """
            Q1. What is the freezing point of water in Celsius?
            (A) 0°C
            (B) 100°C
            (C) -10°C
            (D) 32°C
            Answer: A
            Explanation: At standard atmospheric pressure, pure liquid water freezes into ice at exactly 0 degrees Celsius.
        """.trimIndent()

        val paper = McqParser.parse(rawInput)
        assertEquals(1, paper.questions.size)

        val q1 = paper.questions[0]
        assertEquals("A", q1.correctAnswer)
        assertNotNull(q1.explanation)
        assert(q1.explanation!!.contains("freezes into ice at exactly 0 degrees Celsius"))
    }

    @Test
    fun testStandardAnswerKeyFollowedByDetailedExplanations() {
        val rawInput = """
            1. What is the capital of France?
            (A) Berlin
            (B) Madrid
            (C) Paris
            (D) Rome

            2. What is the largest planet in our solar system?
            (A) Earth
            (B) Mars
            (C) Jupiter
            (D) Saturn

            Answer Key
            1. C
            2. C

            Detailed Explanations
            1. Paris has been the capital of France since 987 CE under Hugh Capet.
            2. Jupiter is by far the largest planet, with a mass more than two and a half times that of all other planets combined.
        """.trimIndent()

        val paper = McqParser.parse(rawInput)
        assertEquals(2, paper.questions.size)

        val q1 = paper.questions[0]
        assertEquals(1, q1.num)
        assertEquals("What is the capital of France?", q1.question.trim())
        assertEquals("Paris", q1.options.c)
        assertEquals("C", q1.correctAnswer)
        assertNotNull(q1.explanation)
        assert(q1.explanation!!.contains("capital of France"))

        val q2 = paper.questions[1]
        assertEquals(2, q2.num)
        assertEquals("What is the largest planet in our solar system?", q2.question.trim())
        assertEquals("Jupiter", q2.options.c)
        assertEquals("C", q2.correctAnswer)
        assertNotNull(q2.explanation)
        assert(q2.explanation!!.contains("Jupiter is by far the largest planet"))
    }

    @Test
    fun testStandardAnswersOnly() {
        val rawInput = """
            1. What is H2O?
            (A) Water
            (B) Salt
            (C) Acid
            (D) Base

            Answer Key
            1: A
        """.trimIndent()

        val paper = McqParser.parse(rawInput)
        assertEquals(1, paper.questions.size)
        assertEquals("A", paper.questions[0].correctAnswer)
        assertEquals("What is H2O?", paper.questions[0].question.trim())
    }

    @Test
    fun testCombinedAnswersAndExplanations() {
        val rawInput = """
            1. What is the speed of light?
            (A) 3 x 10^8 m/s
            (B) 1.5 x 10^8 m/s
            (C) 300 m/s
            (D) 3000 km/h

            Answers & Explanations
            1. Correct Answer: (A)
            Light in a vacuum travels at approximately 299,792,458 meters per second.
        """.trimIndent()

        val paper = McqParser.parse(rawInput)
        assertEquals(1, paper.questions.size)
        assertEquals("A", paper.questions[0].correctAnswer)
        assertNotNull(paper.questions[0].explanation)
        assert(paper.questions[0].explanation!!.contains("299,792,458"))
    }

    @Test
    fun testHindiStandardHeadings() {
        val rawInput = """
            1. भारत की राजधानी क्या है?
            (A) मुंबई
            (B) नई दिल्ली
            (C) कोलकाता
            (D) चेन्नई

            उत्तर तालिका
            1. B

            विस्तृत व्याख्या
            1. नई दिल्ली भारत की राष्ट्रीय राजधानी है।
        """.trimIndent()

        val paper = McqParser.parse(rawInput)
        assertEquals(1, paper.questions.size)
        assertEquals("B", paper.questions[0].correctAnswer)
        assertNotNull(paper.questions[0].explanation)
        assert(paper.questions[0].explanation!!.contains("नई दिल्ली भारत की राष्ट्रीय राजधानी है"))
    }
}
