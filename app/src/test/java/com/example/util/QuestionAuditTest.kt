package com.example.util

import com.example.model.AuditIssueType
import com.example.model.McqQuestion
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuestionAuditTest {

    private val samplePaper = QuestionPaper(
        id = 1L,
        metadata = PaperMetadata(
            title = "Physics Olympiad Test",
            subject = "Physics",
            setNumber = "Set A"
        ),
        questions = listOf(
            McqQuestion(
                num = 1,
                question = "What is the unit of electric current?",
                options = QuestionOptions(a = "Volt", b = "Ampere", c = "Ohm", d = "Watt"),
                correctAnswer = "B",
                explanation = "Electric current is measured in Amperes (A)."
            ),
            McqQuestion(
                num = 2,
                question = "What is the speed of light in vacuum?",
                options = QuestionOptions(a = "3 x 10^8 m/s", b = "3 x 10^6 m/s", c = "3 x 10^10 m/s", d = "1.5 x 10^8 m/s"),
                correctAnswer = "B", // INTENTIONALLY WRONG (should be A)
                explanation = "Light travels at 3 x 10^8 m/s."
            ),
            McqQuestion(
                num = 3,
                question = "Which law states that pressure is inversely proportional to volume?",
                options = QuestionOptions(a = "Boyle's Law", b = "Charles's Law", c = "Boyle's Law", d = "Avogadro's Law"), // DUPLICATE OPTION A & C
                correctAnswer = "A",
                explanation = null
            ),
            McqQuestion(
                num = 4,
                question = "What is acceleration due to gravity on Earth?",
                options = QuestionOptions(a = "9.8 m/s^2", b = "10.5 m/s^2", c = "", d = ""), // MISSING OPTIONS
                correctAnswer = null, // MISSING KEY
                explanation = null
            )
        )
    )

    @Test
    fun testBulkAuditPromptGeneration() {
        val prompt = QuestionAuditHelper.generateBulkAuditPrompt(samplePaper)

        assertTrue("Prompt should include paper title", prompt.contains("Physics Olympiad Test"))
        assertTrue("Prompt should include subject", prompt.contains("Physics"))
        assertTrue("Prompt should include mandatory rechecked explanation mandate", prompt.contains("MANDATORY RECHECKED EXPLANATION"))
        assertTrue("Prompt should include replacement question mandate", prompt.contains("MANDATORY REPLACEMENT QUESTION"))
        assertTrue("Prompt should list Q1", prompt.contains("Q1. What is the unit of electric current?"))
        assertTrue("Prompt should list Q2", prompt.contains("Q2. What is the speed of light in vacuum?"))
        assertTrue("Prompt should format options", prompt.contains("A) Volt"))
    }

    @Test
    fun testHeuristicPreAuditDetection() {
        val reports = QuestionAuditHelper.runHeuristicPreAudit(samplePaper)
        assertEquals(4, reports.size)

        val q1 = reports.find { it.qNum == 1 }!!
        assertEquals(AuditIssueType.CLEAN, q1.status)

        val q3 = reports.find { it.qNum == 3 }!!
        assertEquals(AuditIssueType.VAGUE_OPTIONS, q3.status)
        assertTrue(q3.flawReason!!.contains("duplicate"))

        val q4 = reports.find { it.qNum == 4 }!!
        assertEquals(AuditIssueType.MISSING_OPTIONS, q4.status)
    }

    @Test
    fun testJsonAuditParsingWithRecheckedExplanation() {
        val jsonFeedback = """
            ```json
            [
              {
                "qNum": 2,
                "status": "WRONG_KEY",
                "currentKey": "B",
                "suggestedKey": "A",
                "flawReason": "Option B is incorrect by a factor of 100.",
                "recheckedExplanation": "Derivation: The speed of light in vacuum c is defined as exactly 299,792,458 m/s, which rounds to 3 x 10^8 m/s (Option A). Option B is off by two orders of magnitude."
              }
            ]
            ```
        """.trimIndent()

        val parsed = QuestionAuditHelper.parseAuditReport(jsonFeedback, samplePaper)
        val q2Report = parsed.find { it.qNum == 2 }!!

        assertEquals(AuditIssueType.WRONG_KEY, q2Report.status)
        assertEquals("B", q2Report.currentKey)
        assertEquals("A", q2Report.suggestedKey)
        assertNotNull(q2Report.recheckedExplanation)
        assertTrue(q2Report.recheckedExplanation!!.contains("Option A"))
    }

    @Test
    fun testAtomicQuestionReplacementParsing() {
        val jsonFeedback = """
            [
              {
                "qNum": 3,
                "status": "FLAWED",
                "flawReason": "Options A and C are identical duplicate distractors.",
                "replacementQuestion": {
                  "question": "Which thermodynamic process occurs at constant volume?",
                  "options": {
                    "a": "Isochoric process",
                    "b": "Isobaric process",
                    "c": "Isothermal process",
                    "d": "Adiabatic process"
                  },
                  "correctAnswer": "A",
                  "explanation": "An isochoric process is a thermodynamic process in which the volume of the closed system remains constant."
                }
              }
            ]
        """.trimIndent()

        val parsed = QuestionAuditHelper.parseAuditReport(jsonFeedback, samplePaper)
        val q3Report = parsed.find { it.qNum == 3 }!!

        assertEquals(AuditIssueType.FLAWED_QUESTION, q3Report.status)
        assertNotNull(q3Report.replacementQuestion)

        val rep = q3Report.replacementQuestion!!
        assertEquals("Which thermodynamic process occurs at constant volume?", rep.question)
        assertEquals("Isochoric process", rep.options.a)
        assertEquals("A", rep.correctAnswer)
        assertEquals("An isochoric process is a thermodynamic process in which the volume of the closed system remains constant.", rep.explanation)
    }

    @Test
    fun testTextReportParsingFallback() {
        val textFeedback = """
            Audit findings:
            Q2: marked B -> should be A. Current key is incorrect.
            Q3: flawed duplicate options, please delete.
        """.trimIndent()

        val parsed = QuestionAuditHelper.parseAuditReport(textFeedback, samplePaper)
        val q2Report = parsed.find { it.qNum == 2 }!!
        assertEquals(AuditIssueType.WRONG_KEY, q2Report.status)
        assertEquals("B", q2Report.currentKey)
        assertEquals("A", q2Report.suggestedKey)

        val q3Report = parsed.find { it.qNum == 3 }!!
        assertEquals(AuditIssueType.FLAWED_QUESTION, q3Report.status)
    }

    @Test
    fun testComputeMetrics() {
        val reports = QuestionAuditHelper.runHeuristicPreAudit(samplePaper)
        val metrics = QuestionAuditHelper.computeMetrics(reports)

        assertEquals(4, metrics.totalAudited)
        assertEquals(2, metrics.cleanCount) // Q1 and Q2 are clean in heuristic pre-audit (Q2's key flaw is factual, found by LLM)
        assertEquals(1, metrics.flawedQuestionsCount) // Q3 (Vague duplicate options)
        assertEquals(1, metrics.missingOptionsCount) // Q4
    }

    @Test
    fun testPhase1PromptStrictlyChecksQuestionAndAnswerOnly() {
        val prompt = QuestionAuditHelper.generatePhase1KeyAndFlawAuditPrompt(samplePaper)
        assertTrue("Phase 1 prompt should mandate MCQ stem check", prompt.contains("QUESTION MCQ STEM & OPTIONS CHECK"))
        assertTrue("Phase 1 prompt should mandate marked answer key verification", prompt.contains("MARKED ANSWER KEY VERIFICATION"))
        assertTrue("Phase 1 prompt should enforce strict scope constraint", prompt.contains("STRICT SCOPE CONSTRAINT"))
        assertTrue("Phase 1 prompt should format options", prompt.contains("A) Volt"))
        assertTrue("Phase 1 prompt must NOT include existing explanation payload", !prompt.contains("Existing Explanation"))
        assertTrue("Phase 1 prompt must NOT mandate detailed explanation review", !prompt.contains("DETAILED EXPLANATION REVIEW & PROOF"))
    }

    @Test
    fun testPhase3PromptContainsDetailedExplanationsAndKeyIdeas() {
        val prompt = QuestionAuditHelper.generatePhase3ExplanationAuditPrompt(samplePaper)
        assertTrue("Phase 3 prompt should include detailed explanation header", prompt.contains("COMPREHENSIVE DETAILED EXPLANATION & PEDAGOGICAL AUDIT"))
        assertTrue("Phase 3 prompt should mandate step-by-step explanations", prompt.contains("STEP-BY-STEP EXPLANATION"))
        assertTrue("Phase 3 prompt should emphasize pedagogical highlights and key ideas", prompt.contains("CORE CONCEPTS & KEY IDEAS"))
        assertTrue("Phase 3 prompt should include existing explanation drafts for refinement", prompt.contains("Existing Explanation"))
    }

    @Test
    fun testPhase3QualitySealPrompt() {
        val prompt = QuestionAuditHelper.generatePhase3QualitySealPrompt(samplePaper)
        assertTrue("Phase 3 quality seal prompt should include quality seal header", prompt.contains("QUALITY SEAL, PSYCHOMETRIC AUDIT SUMMARY & CERTIFICATION"))
        assertTrue("Phase 3 quality seal prompt should mandate quality seal recommendation", prompt.contains("QUALITY SEAL RECOMMENDATION"))
        assertTrue("Phase 3 quality seal prompt should include certification status in schema", prompt.contains("CERTIFIED_READY"))
    }
}

