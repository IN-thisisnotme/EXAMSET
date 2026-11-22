package com.example.util

import com.example.model.McqQuestion
import com.example.model.MsqScoringPolicy
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import com.example.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnswerKeyMatcherTest {

    private fun mcq(
        num: Int = 1,
        stem: String = "What is the SI unit of force?",
        correctAnswer: String = "B",
        questionType: String? = null
    ) = McqQuestion(
        num = num,
        question = stem,
        options = QuestionOptions(a = "Joule", b = "Newton", c = "Pascal", d = "Watt"),
        correctAnswer = correctAnswer,
        questionType = questionType
    )

    @Test
    fun `parseSelectedLetters and normalizeKey normalize varied key delimiters cleanly`() {
        assertEquals(setOf("A", "C"), AnswerKeyMatcher.parseSelectedLetters("c, a"))
        assertEquals(setOf("A", "C"), AnswerKeyMatcher.parseSelectedLetters("(A, C)"))
        assertEquals(setOf("A", "B"), AnswerKeyMatcher.parseSelectedLetters("A & B"))
        assertEquals(setOf("A", "C"), AnswerKeyMatcher.parseSelectedLetters("1, 3"))
        assertEquals(setOf("A", "C"), AnswerKeyMatcher.parseSelectedLetters("AC"))
        assertEquals("A,C", AnswerKeyMatcher.normalizeKey("(c, a)"))
        assertEquals("A, C", AnswerKeyMatcher.formatKeyForDisplay("c, a"))
        assertEquals("A,B,D", AnswerKeyMatcher.normalizeKey("Ans: d, a and b"))

        assertTrue(AnswerKeyMatcher.containsOption("A,C", "c"))
        assertFalse(AnswerKeyMatcher.containsOption("A,C", "B"))
        assertTrue(AnswerKeyMatcher.areKeysEquivalent("(C, A)", "A,C"))

        var sel = ""
        sel = AnswerKeyMatcher.toggleSelection(sel, "C")
        assertEquals("C", sel)
        sel = AnswerKeyMatcher.toggleSelection(sel, "A")
        assertEquals("A,C", sel)
        sel = AnswerKeyMatcher.toggleSelection(sel, "A")
        assertEquals("C", sel)
        sel = AnswerKeyMatcher.toggleSelection(sel, "C")
        assertEquals("", sel)
    }

    @Test
    fun `isMultiSelect detects MSQs accurately without triggering on UPSC statement single-choice MCQs`() {
        // Explicit UPSC single-answer statement question must stay single-choice MCQ
        val upscStatementMcq = mcq(
            num = 1,
            stem = """
                Consider the following statements:
                1. The Governor holds office during the pleasure of the President.
                2. The Governor is appointed by the Chief Justice of India.
                Which of the statements given above is/are correct?
            """.trimIndent(),
            correctAnswer = "A",
            questionType = "Multiple Statements"
        )
        assertFalse(
            "UPSC single-key statement question must NOT trigger MSQ",
            AnswerKeyMatcher.isMultiSelect(upscStatementMcq)
        )

        val plainMcq = mcq(num = 2, stem = "Capital of France?", correctAnswer = "C", questionType = "MCQ")
        assertFalse(AnswerKeyMatcher.isMultiSelect(plainMcq))

        // MSQ triggered by 2+ answer letters
        val multiKeyMsq = mcq(num = 3, stem = "Which numbers are prime?", correctAnswer = "A,C")
        assertTrue(AnswerKeyMatcher.isMultiSelect(multiKeyMsq))

        // MSQ triggered by explicit questionType
        val explicitTypeMsq = mcq(num = 4, stem = "Pick valid laws:", correctAnswer = "B", questionType = "MSQ")
        assertTrue(AnswerKeyMatcher.isMultiSelect(explicitTypeMsq))

        // MSQ triggered by explicit stem cue
        val cueMsq = mcq(num = 5, stem = "Which reactions are exothermic? (Select all that apply)", correctAnswer = "A")
        assertTrue(AnswerKeyMatcher.isMultiSelect(cueMsq))
    }

    @Test
    fun `scoreQuestion applies ALL_OR_NOTHING vs PARTIAL_STRICT mathematically`() {
        val msqQuestion = mcq(num = 1, stem = "Select all greenhouse gases.", correctAnswer = "A,C,D", questionType = "MSQ")

        // Exact match -> full positive marks under both policies
        val exactAll = AnswerKeyMatcher.scoreQuestion(
            msqQuestion, "d, a, c", positiveMarks = 4.0, negativeMarks = 1.0, msqPolicy = MsqScoringPolicy.ALL_OR_NOTHING
        )
        assertEquals(AnswerKeyMatcher.QuestionGradeStatus.CORRECT, exactAll.status)
        assertEquals(4.0, exactAll.marks, 0.001)

        // Subset under ALL_OR_NOTHING -> incorrect (-1.0)
        val subsetAll = AnswerKeyMatcher.scoreQuestion(
            msqQuestion, "A,C", positiveMarks = 4.0, negativeMarks = 1.0, msqPolicy = MsqScoringPolicy.ALL_OR_NOTHING
        )
        assertEquals(AnswerKeyMatcher.QuestionGradeStatus.INCORRECT, subsetAll.status)
        assertEquals(-1.0, subsetAll.marks, 0.001)

        // Subset with zero wrong under PARTIAL_STRICT -> proportional (4.0 * 2 / 3 = 2.67)
        val subsetPartial = AnswerKeyMatcher.scoreQuestion(
            msqQuestion, "A,C", positiveMarks = 4.0, negativeMarks = 1.0, msqPolicy = MsqScoringPolicy.PARTIAL_STRICT
        )
        assertEquals(AnswerKeyMatcher.QuestionGradeStatus.PARTIAL, subsetPartial.status)
        assertEquals(2.67, subsetPartial.marks, 0.001)
        assertEquals(2, subsetPartial.chosenCorrectCount)
        assertEquals(0, subsetPartial.chosenWrongCount)

        // Any wrong option selected under PARTIAL_STRICT -> full negative (-1.0)
        val wrongPlusCorrectPartial = AnswerKeyMatcher.scoreQuestion(
            msqQuestion, "A,B,C", positiveMarks = 4.0, negativeMarks = 1.0, msqPolicy = MsqScoringPolicy.PARTIAL_STRICT
        )
        assertEquals(AnswerKeyMatcher.QuestionGradeStatus.INCORRECT, wrongPlusCorrectPartial.status)
        assertEquals(-1.0, wrongPlusCorrectPartial.marks, 0.001)
    }

    @Test
    fun `MainViewModel calculateTestStats handles mixed MCQ and MSQ under both policies`() {
        val paper = QuestionPaper(
            metadata = PaperMetadata(
                title = "Mixed Objective Set",
                positiveMarks = 4.0,
                negativeMarks = 1.0,
                unattendedMarks = 0.0,
                msqScoringPolicy = MsqScoringPolicy.PARTIAL_STRICT
            ),
            questions = listOf(
                mcq(num = 1, correctAnswer = "B", questionType = "MCQ"),
                mcq(num = 2, correctAnswer = "A,C", questionType = "MSQ"),
                mcq(num = 3, correctAnswer = "B,D", questionType = "MSQ"),
                mcq(num = 4, correctAnswer = "A", questionType = "MCQ")
            )
        )

        val answers = mapOf(
            1 to "B",   // Correct (+4.0)
            2 to "A",   // Partial under PARTIAL_STRICT (+2.0), Incorrect under ALL_OR_NOTHING (-1.0)
            3 to "A,B"  // Wrong option A included -> Incorrect (-1.0)
            // 4 unattended (0.0)
        )

        val partialStats = MainViewModel.calculateTestStats(paper, answers)
        assertEquals(1, partialStats.correct)
        assertEquals(1, partialStats.partial)
        assertEquals(1, partialStats.incorrect)
        assertEquals(1, partialStats.unanswered)
        assertEquals(5.0, partialStats.score, 0.001)

        val strictStats = MainViewModel.calculateTestStats(
            paper = paper,
            answers = answers,
            msqScoringPolicy = MsqScoringPolicy.ALL_OR_NOTHING
        )
        assertEquals(1, strictStats.correct)
        assertEquals(0, strictStats.partial)
        assertEquals(2, strictStats.incorrect)
        assertEquals(1, strictStats.unanswered)
        assertEquals(2.0, strictStats.score, 0.001)
    }

    @Test
    fun `ExamJsonHelper preserves msqScoringPolicy and parses string and array MSQ keys`() {
        val json = """
            {
              "metadata": {
                "title": "GATE CS Mixed Mock",
                "subject": "Computer Science",
                "msqScoringPolicy": "PARTIAL_STRICT"
              },
              "questions": [
                {
                  "num": 1,
                  "question": "Which protocols operate at the Transport Layer?",
                  "options": { "a": "TCP", "b": "IP", "c": "UDP", "d": "ARP" },
                  "correctAnswer": ["C", "A"]
                },
                {
                  "num": 2,
                  "question": "Single choice check",
                  "options": { "a": "One", "b": "Two", "c": "Three", "d": "Four" },
                  "correctAnswer": "B"
                }
              ]
            }
        """.trimIndent()

        val imported = ExamJsonHelper.fromJson(json)
        assertEquals(MsqScoringPolicy.PARTIAL_STRICT, imported.metadata.msqScoringPolicy)
        assertEquals(2, imported.questions.size)
        assertEquals("A,C", imported.questions[0].correctAnswer)
        assertEquals("MSQ", imported.questions[0].questionType)
        assertEquals("B", imported.questions[1].correctAnswer)

        val reExported = ExamJsonHelper.toJson(imported)
        val roundTripped = ExamJsonHelper.fromJson(reExported)
        assertEquals(MsqScoringPolicy.PARTIAL_STRICT, roundTripped.metadata.msqScoringPolicy)
        assertEquals("A,C", roundTripped.questions[0].correctAnswer)
        assertTrue(AnswerKeyMatcher.isMultiSelect(roundTripped.questions[0]))
        assertFalse(AnswerKeyMatcher.isMultiSelect(roundTripped.questions[1]))
    }

    @Test
    fun `McqParser parses multi-key Answer Key blocks and reconstructToRaw preserves MSQ tags`() {
        val raw = """
            # Mixed MSQ Diagnostic
            Subject: Engineering

            1. Which of the following are vector quantities? (Select all that apply)
            (A) Velocity
            (B) Speed
            (C) Acceleration
            (D) Mass

            2. Consider the following statements:
            1. Sound requires a medium to propagate.
            2. Light requires a medium to propagate.
            Which of the statements given above is/are correct?
            (A) 1 only
            (B) 2 only
            (C) Both 1 and 2
            (D) Neither 1 nor 2

            Answer Key:
            1: (A, C)
            2: A
        """.trimIndent()

        val paper = McqParser.parse(raw)
        assertEquals(2, paper.questions.size)
        assertEquals("A,C", paper.questions[0].correctAnswer)
        assertTrue(AnswerKeyMatcher.isMultiSelect(paper.questions[0]))
        assertEquals("A", paper.questions[1].correctAnswer)
        assertFalse(AnswerKeyMatcher.isMultiSelect(paper.questions[1]))

        val reconstructed = McqParser.reconstructToRaw(paper)
        val reparsed = McqParser.parse(reconstructed)
        assertEquals("A,C", reparsed.questions[0].correctAnswer)
        assertEquals("A", reparsed.questions[1].correctAnswer)
        assertNotNull(reparsed.questions[0].questionType)
    }

    @Test
    fun `PsychometricEngine evaluates mixed MCQ and MSQ cohorts including partial counts and multi-option tallies`() {
        val paper = QuestionPaper(
            metadata = PaperMetadata(
                title = "Psychometric MSQ Cohort",
                positiveMarks = 4.0,
                negativeMarks = 1.0,
                unattendedMarks = 0.0,
                msqScoringPolicy = MsqScoringPolicy.PARTIAL_STRICT
            ),
            questions = listOf(
                mcq(num = 1, correctAnswer = "B", questionType = "MCQ"),
                mcq(num = 2, correctAnswer = "A,C", questionType = "MSQ")
            )
        )

        val submissions = listOf(
            // Candidate 1: Q1 correct (+4), Q2 exact A,C (+4) => 8.0
            PsychometricEngine.CandidateSubmission(
                rollNumber = "2026000001",
                bookletNumber = "7000001",
                bookletCode = "A",
                answers = mapOf(1 to 'B'),
                multiAnswers = mapOf(2 to "A,C")
            ),
            // Candidate 2: Q1 correct (+4), Q2 partial A (+2) => 6.0
            PsychometricEngine.CandidateSubmission(
                rollNumber = "2026000002",
                bookletNumber = "7000002",
                bookletCode = "A",
                answers = mapOf(1 to 'B'),
                multiAnswers = mapOf(2 to "A")
            ),
            // Candidate 3: Q1 wrong (-1), Q2 wrong A,B (-1) => -2.0
            PsychometricEngine.CandidateSubmission(
                rollNumber = "2026000003",
                bookletNumber = "7000003",
                bookletCode = "A",
                answers = mapOf(1 to 'D'),
                multiAnswers = mapOf(2 to "A,B")
            )
        )

        val report = PsychometricEngine.evaluateCohortWithCandidates(submissions, paper)
        assertEquals(3, report.candidateCount)
        assertEquals(8.0, report.highestScore, 0.001)
        assertEquals(-2.0, report.lowestScore, 0.001)

        val partialCandidate = report.candidates.first { it.rollNumber == "2026000002" }
        assertEquals(1, partialCandidate.correctCount)
        assertEquals(1, partialCandidate.partialCount)
        assertEquals(0, partialCandidate.incorrectCount)

        val q2Stats = report.items.first { it.questionNum == 2 }
        assertEquals(3, q2Stats.optionFrequencies['A']) // Selected by all 3 candidates
        assertEquals(1, q2Stats.optionFrequencies['B'])
        assertEquals(1, q2Stats.optionFrequencies['C'])
        assertTrue(q2Stats.discriminationD > 0f)
    }
}
