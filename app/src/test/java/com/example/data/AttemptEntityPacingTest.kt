package com.example.data

import com.example.model.McqQuestion
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AttemptEntityPacingTest {

    private fun samplePaper(): QuestionPaper {
        return QuestionPaper(
            id = 10L,
            metadata = PaperMetadata(
                id = "paper_10",
                title = "Pacing Test Mock",
                subject = "General Studies",
                topic = "Mixed",
                setNumber = "01"
            ),
            questions = listOf(
                McqQuestion(
                    num = 1,
                    question = "Question one concise stem",
                    options = QuestionOptions("Opt A", "Opt B", "Opt C", "Opt D"),
                    correctAnswer = "A"
                ),
                McqQuestion(
                    num = 2,
                    question = "Question two tricky numerical stem with long text",
                    options = QuestionOptions("Opt A", "Opt B", "Opt C", "Opt D"),
                    correctAnswer = "B"
                ),
                McqQuestion(
                    num = 3,
                    question = "Question three skipped item",
                    options = QuestionOptions("Opt A", "Opt B", "Opt C", "Opt D"),
                    correctAnswer = "C"
                ),
                McqQuestion(
                    num = 4,
                    question = "Question four neutralized item",
                    options = QuestionOptions("Opt A", "Opt B", "Opt C", "Opt D"),
                    correctAnswer = "D",
                    isNeutralized = true
                ),
                McqQuestion(
                    num = 5,
                    question = "Question five slow incorrect time-sink",
                    options = QuestionOptions("Opt A", "Opt B", "Opt C", "Opt D"),
                    correctAnswer = "A"
                )
            )
        )
    }

    @Test
    fun buildAnswersJson_persistsQuestionTimesWithoutBreakingLegacyKeys() {
        val raw = AttemptEntity.buildAnswersJson(
            map = mapOf(1 to "A", 2 to "C"),
            rollNumber = "20260042",
            bookletNumber = "BK-77",
            bookletCode = "B",
            questionTimes = mapOf(1 to 18L, 2 to 94L, 0 to 12L, 3 to 0L)
        )

        val attempt = AttemptEntity(paperId = 10L, answersJson = raw)
        assertEquals(mapOf(1 to "A", 2 to "C"), attempt.getAnswersMap())
        assertEquals("20260042", attempt.getCandidateRollNumber())
        assertEquals("BK-77", attempt.getCandidateBookletNumber())
        assertEquals("B", attempt.getCandidateBookletCode())
        assertEquals(mapOf(1 to 18L, 2 to 94L), attempt.getQuestionTimesMap())
    }

    @Test
    fun getQuestionTimesMap_handlesLegacyAttemptsAndMalformedJsonGracefully() {
        val legacyAttempt = AttemptEntity(
            paperId = 1L,
            answersJson = """{"1":"A","2":"D","_rollNumber":"12345"}"""
        )
        assertEquals(mapOf(1 to "A", 2 to "D"), legacyAttempt.getAnswersMap())
        assertTrue(legacyAttempt.getQuestionTimesMap().isEmpty())

        val malformedAttempt = AttemptEntity(paperId = 1L, answersJson = "{not-valid-json")
        assertTrue(malformedAttempt.getQuestionTimesMap().isEmpty())
    }

    @Test
    fun computeSummary_calculatesCorrectVsIncorrectAveragesAndTopThreeSlowest() {
        val paper = samplePaper()
        val answers = mapOf(
            1 to "A", // correct (10s)
            2 to "D", // incorrect (60s)
            // 3 skipped (40s)
            4 to "A", // neutralized -> treated as correct (20s)
            5 to "B"  // incorrect (120s)
        )
        val times = mapOf(
            1 to 10L,
            2 to 60L,
            3 to 40L,
            4 to 20L,
            5 to 120L
        )

        val summary = QuestionPacingAnalyzer.computeSummary(
            paper = paper,
            answers = answers,
            questionTimes = times
        )

        assertTrue(summary.hasPacingData)
        assertEquals(5, summary.trackedQuestionsCount)
        assertEquals(250L, summary.totalTrackedSeconds)
        assertEquals(50.0, summary.averageSecondsPerQuestion, 0.001)
        // Correct: Q1 (10s) and Q4 neutralized (20s) -> avg 15.0
        assertEquals(15.0, summary.correctAvgSeconds!!, 0.001)
        // Incorrect: Q2 (60s) and Q5 (120s) -> avg 90.0
        assertEquals(90.0, summary.incorrectAvgSeconds!!, 0.001)

        // Top 3 slowest: Q5 (120s), Q2 (60s), Q3 (40s)
        assertEquals(listOf(5, 2, 3), summary.slowestQuestions.map { it.questionNum })
        assertEquals(false, summary.slowestQuestions[0].isCorrect)
        assertEquals(false, summary.slowestQuestions[1].isCorrect)
        assertNull(summary.slowestQuestions[2].isCorrect)
    }

    @Test
    fun computeSummary_ordersTiedDurationsByAscendingQuestionNumber() {
        val summary = QuestionPacingAnalyzer.computeSummary(
            paper = samplePaper(),
            answers = mapOf(1 to "A", 2 to "B", 3 to "C", 4 to "D"),
            questionTimes = mapOf(4 to 50L, 2 to 50L, 1 to 50L, 3 to 50L)
        )

        assertTrue(summary.hasPacingData)
        assertEquals(listOf(1, 2, 3), summary.slowestQuestions.map { it.questionNum })
    }

    @Test
    fun computeSummary_returnsEmptySummaryWhenNoValidTimesExist() {
        val summary = QuestionPacingAnalyzer.computeSummary(
            paper = samplePaper(),
            answers = mapOf(1 to "A"),
            questionTimes = mapOf(1 to 0L, -2 to 30L)
        )

        assertFalse(summary.hasPacingData)
        assertEquals(0, summary.trackedQuestionsCount)
        assertTrue(summary.slowestQuestions.isEmpty())
        assertNull(summary.correctAvgSeconds)
        assertNull(summary.incorrectAvgSeconds)
    }
}
