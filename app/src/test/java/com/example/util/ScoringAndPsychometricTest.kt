package com.example.util

import com.example.data.AttemptEntity
import com.example.model.McqQuestion
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import com.example.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringAndPsychometricTest {

    private fun samplePaper(
        positiveMarks: Double = 4.0,
        negativeMarks: Double = 1.0,
        unattendedMarks: Double = 0.0,
        count: Int = 5
    ): QuestionPaper {
        val questions = (1..count).map { num ->
            McqQuestion(
                num = num,
                question = "Sample Question $num",
                options = QuestionOptions(
                    a = "Option A",
                    b = "Option B",
                    c = "Option C",
                    d = "Option D"
                ),
                correctAnswer = "A"
            )
        }
        return QuestionPaper(
            metadata = PaperMetadata(
                title = "Scoring & CTT Verification Set",
                subject = "Physics",
                positiveMarks = positiveMarks,
                negativeMarks = negativeMarks,
                unattendedMarks = unattendedMarks
            ),
            questions = questions
        )
    }

    @Test
    fun `calculateTestStats applies configurable unattendedMarks with no zero clamp and 2-decimal formatting`() {
        val paper = samplePaper(
            positiveMarks = 4.0,
            negativeMarks = 1.0,
            unattendedMarks = 0.50,
            count = 5
        )
        // 0 correct, 2 incorrect (-2.00), 3 unanswered (-1.50) => -3.50
        val answers = mapOf(
            1 to "B",
            2 to "C"
        )
        val stats = MainViewModel.calculateTestStats(paper, answers)
        assertEquals(0, stats.correct)
        assertEquals(2, stats.incorrect)
        assertEquals(3, stats.unanswered)
        assertEquals(2.00, stats.penaltyIncorrect, 0.001)
        assertEquals(1.50, stats.penaltyUnattended, 0.001)
        assertEquals(-3.50, stats.score, 0.001)
        assertEquals("-3.50", stats.formattedScore)
        assertEquals(20.0, stats.maxPossibleMarks, 0.001)

        // Default unattendedMarks = 0.0 should not deduct any hidden -0.10 penalty
        val defaultPaper = samplePaper(
            positiveMarks = 1.0,
            negativeMarks = 0.25,
            unattendedMarks = 0.0,
            count = 4
        )
        val defaultStats = MainViewModel.calculateTestStats(defaultPaper, mapOf(1 to "A"))
        assertEquals(1, defaultStats.correct)
        assertEquals(0, defaultStats.incorrect)
        assertEquals(3, defaultStats.unanswered)
        assertEquals(0.0, defaultStats.penaltyUnattended, 0.001)
        assertEquals(1.00, defaultStats.score, 0.001)
        assertEquals("1.00", defaultStats.formattedScore)
    }

    @Test
    fun `AttemptEntity getFormattedScore formats positive zero and negative values to 2 decimals`() {
        val positiveAttempt = AttemptEntity(paperId = 1L, score = 12.5)
        val zeroAttempt = AttemptEntity(paperId = 1L, score = 0.0)
        val negativeAttempt = AttemptEntity(paperId = 1L, score = -4.25)

        assertEquals("12.50", positiveAttempt.getFormattedScore())
        assertEquals("0.00", zeroAttempt.getFormattedScore())
        assertEquals("-4.25", negativeAttempt.getFormattedScore())
    }

    @Test
    fun `PsychometricEngine evaluateCohortWithCandidates handles zero submissions without collapsing items or false outlier flags`() {
        val paper = samplePaper(positiveMarks = 4.0, negativeMarks = 1.0, unattendedMarks = 0.25, count = 4)
        val report = PsychometricEngine.evaluateCohortWithCandidates(emptyList(), paper)

        assertEquals(0, report.candidateCount)
        assertEquals(4, report.questionCount)
        assertEquals(4, report.items.size)
        assertEquals(0, report.flaggedItemCount)
        assertTrue(report.candidates.isEmpty())
        assertEquals(0.0, report.highestScore, 0.001)
        assertEquals(0.0, report.lowestScore, 0.001)
        report.items.forEachIndexed { index, item ->
            assertEquals(index + 1, item.questionNum)
            assertFalse("Zero-submission item must not be flagged", item.isFlagged)
            assertEquals("Awaiting Live Submissions", item.flagReason)
            assertEquals(0, item.totalAttempts)
        }
    }

    @Test
    fun `PsychometricEngine cohort scoring includes unattendedMarks and negative net scores`() {
        val paper = samplePaper(
            positiveMarks = 4.0,
            negativeMarks = 1.0,
            unattendedMarks = 0.50,
            count = 4
        )
        val submissions = listOf(
            // 1 correct (+4), 1 wrong (-1), 2 unattended (-1.0) => 2.00
            PsychometricEngine.CandidateSubmission(
                rollNumber = "2026000001",
                bookletNumber = "7000001",
                bookletCode = "A",
                answers = mapOf(1 to 'A', 2 to 'B')
            ),
            // 0 correct, 2 wrong (-2), 2 unattended (-1.0) => -3.00
            PsychometricEngine.CandidateSubmission(
                rollNumber = "2026000002",
                bookletNumber = "7000002",
                bookletCode = "A",
                answers = mapOf(1 to 'B', 2 to 'C')
            )
        )

        val report = PsychometricEngine.evaluateCohortWithCandidates(submissions, paper)
        assertEquals(2, report.candidateCount)
        assertEquals(2.00, report.highestScore, 0.001)
        assertEquals(-3.00, report.lowestScore, 0.001)
        assertEquals(-0.50f, report.meanScore, 0.001f)
        assertEquals(-0.50f, report.medianScore, 0.001f)

        val firstCard = report.candidates.first()
        val lastCard = report.candidates.last()
        assertEquals(2.00, firstCard.totalScore, 0.001)
        assertEquals(-3.00, lastCard.totalScore, 0.001)
        assertEquals(2, lastCard.unansweredCount)
    }

    @Test
    fun `TrustedTimeManager returns sane epoch millis when unsynced`() {
        val now = TrustedTimeManager.getTrustedTimeMillis()
        assertTrue("Epoch millis must be positive and plausible", now > 1_700_000_000_000L)
    }
}
