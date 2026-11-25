package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PaperEntity
import com.example.data.SampleData
import com.example.model.ExamConfig
import com.example.model.PaperMetadata
import com.example.model.PrintMode
import com.example.util.McqParser
import com.example.util.PrintDocumentHelper
import kotlinx.coroutines.runBlocking
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
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ExamSet", appName)
    }

    @Test
    fun `parser parses science sample questions and answer keys correctly`() {
        val paper = McqParser.parse(SampleData.SAMPLE_SCIENCE_PAPER)

        assertEquals("UPSC CSE CSAT Paper II Diagnostic Assessment", paper.metadata.title)
        assertEquals("Quantitative Aptitude & Analytical Reasoning", paper.metadata.subject)
        assertEquals(5, paper.questions.size)

        // Question 1 verification
        val q1 = paper.questions.first()
        assertEquals(1, q1.num)
        assertTrue(q1.question.contains("compound interest amounts to ₹8,000"))
        assertEquals("20%", q1.options.a)
        assertEquals("25%", q1.options.b)
        assertEquals("B", q1.correctAnswer)
        assertNotNull(q1.explanation)
        assertTrue(q1.explanation!!.contains("Interest earned in the 4th year"))

        // Question 5 verification
        val q5 = paper.questions.last()
        assertEquals(5, q5.num)
        assertEquals("B", q5.correctAnswer)
    }

    @Test
    fun `parser extracts topic and set number metadata properly`() {
        val customText = """
            # Modern Physics Chapter Test
            Subject: Physics
            Topic: Wave-Particle Duality
            Set: B-02
            
            1. What is the speed of light in vacuum?
            (A) 3 x 10^8 m/s
            (B) 3 x 10^6 m/s
            (C) 1.5 x 10^8 m/s
            (D) 9 x 10^8 m/s
            
            Answer Key:
            1: A
            
            Explanations:
            1. (A) Light propagates in vacuum at exactly 299,792,458 m/s.
        """.trimIndent()

        val parsed = McqParser.parse(customText)
        assertEquals("Modern Physics Chapter Test", parsed.metadata.title)
        assertEquals("Physics", parsed.metadata.subject)
        assertEquals("Wave-Particle Duality", parsed.metadata.topic)
        assertEquals("Set B-02", parsed.metadata.setNumber)
        assertEquals(1, parsed.questions.size)
        assertEquals("A", parsed.questions.first().correctAnswer)
    }

    @Test
    fun `room database supports deduplication and metadata updates`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val dao = db.paperDao()

        val raw = "Sample raw text for deduplication test"
        val entity1 = PaperEntity(
            title = "Physics Mock",
            subject = "Physics",
            topic = "Thermodynamics",
            setNumber = "Set 1",
            questionCount = 10,
            answerKeyCount = 10,
            explanationCount = 10,
            rawText = raw
        )

        // Insert first
        val id1 = dao.insertPaper(entity1)
        assertTrue(id1 > 0)

        // Find by raw text to prevent duplicate storage
        val found = dao.findPaperByRawText(raw)
        assertNotNull(found)
        assertEquals(id1, found!!.id)
        assertEquals("Thermodynamics", found.topic)
        assertEquals("Set 1", found.setNumber)

        // Update metadata
        dao.updateMetadata(
            id = id1,
            title = "Advanced Physics Mock",
            subject = "Physics",
            topic = "Statistical Mechanics",
            setNumber = "Set 2"
        )

        val updated = dao.getPaperById(id1)
        assertNotNull(updated)
        assertEquals("Advanced Physics Mock", updated!!.title)
        assertEquals("Statistical Mechanics", updated.topic)
        assertEquals("Set 2", updated.setNumber)

        // Clean up
        dao.deleteById(id1)
    }

    @Test
    fun `printable html generation produces valid student paper with no answer leaks`() {
        val paper = McqParser.parse(SampleData.SAMPLE_SCIENCE_PAPER)
        val config = ExamConfig(
            institutionName = "CENTRAL BOARD OF SECONDARY EDUCATION",
            examTitle = "ALL INDIA SCIENCE OLYMPIAD 2026",
            printMode = PrintMode.STUDENT_PAPER,
            includeCandidateDetails = true,
            includeInstructions = true,
            includeOmrSheet = true
        )

        val html = PrintDocumentHelper.generatePrintHtml(paper, config)

        // Must contain header and candidate boxes
        assertTrue(html.contains("CENTRAL BOARD OF SECONDARY EDUCATION"))
        assertTrue(html.contains("ALL INDIA SCIENCE OLYMPIAD 2026"))
        assertTrue(html.contains("Candidate Name:"))
        assertTrue(html.contains("Candidate Signature:"))
        assertTrue(html.contains("Instructions to Candidates:"))

        // Must contain questions
        assertTrue(html.contains("compound interest amounts to ₹8,000"))
        assertTrue(html.contains("(A)"))
        assertTrue(html.contains("(B)"))

        // Must contain OMR sheet
        assertTrue(html.contains("OMR RESPONSE SHEET"))

        // Must NOT leak answer keys in student paper!
        assertFalse(html.contains("Correct Answer:"))
        assertFalse(html.contains("Answer Key Table"))
    }

    @Test
    fun `student test workflow starts exam, computes scores, and handles targeted retry`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val dao = db.paperDao()

        val parsed = McqParser.parse(SampleData.SAMPLE_SCIENCE_PAPER)
        val entity = PaperEntity(
            title = parsed.metadata.title,
            subject = parsed.metadata.subject,
            topic = parsed.metadata.topic,
            setNumber = parsed.metadata.setNumber,
            questionCount = parsed.questions.size,
            answerKeyCount = parsed.quickAnswersCount,
            explanationCount = parsed.explanationsCount,
            rawText = SampleData.SAMPLE_SCIENCE_PAPER
        )
        val paperId = dao.insertPaper(entity)
        assertTrue(paperId > 0)

        // Q1 correct is B, Q2 correct is B, Q3 correct is B, Q4 correct is A, Q5 correct is B
        val answers = mapOf(
            1 to "B", // Correct
            2 to "B", // Correct
            3 to "A"  // Incorrect (correct is B)
            // Q4..Q5 are unattempted (2 questions)
        )

        val correctCount = parsed.questions.count { q -> answers[q.num]?.equals(q.correctAnswer, ignoreCase = true) == true }
        val incorrectCount = parsed.questions.count { q ->
            val userAns = answers[q.num]
            userAns != null && q.correctAnswer != null && !userAns.equals(q.correctAnswer, ignoreCase = true)
        }
        val unattemptedCount = parsed.questions.size - answers.size

        assertEquals(2, correctCount)
        assertEquals(1, incorrectCount)
        assertEquals(2, unattemptedCount)

        // Targeted Retry for Incorrect: should isolate question 3
        val incorrectQuestions = parsed.questions.filter { q ->
            val userAns = answers[q.num]
            userAns != null && q.correctAnswer != null && !userAns.equals(q.correctAnswer, ignoreCase = true)
        }.map { it.num }

        assertEquals(listOf(3), incorrectQuestions)

        // Targeted Retry for Unanswered: should isolate questions 4..5
        val unansweredQuestions = parsed.questions.filter { q -> answers[q.num] == null }.map { it.num }
        assertEquals(2, unansweredQuestions.size)
        assertTrue(unansweredQuestions.contains(4))
        assertTrue(unansweredQuestions.contains(5))

        // Clean up
        dao.deleteById(paperId)
    }

    @Test
    fun `recalculating overall test status after reviewing incorrect questions maintains total 20 questions`() {
        // Build a mock 20-question paper where correct answer for each is "A"
        val questions = (1..20).map { num ->
            com.example.model.McqQuestion(
                num = num,
                question = "Question $num",
                options = com.example.model.QuestionOptions("Option A", "Option B", "Option C", "Option D"),
                correctAnswer = "A"
            )
        }
        val paper = com.example.model.QuestionPaper(
            metadata = com.example.model.PaperMetadata(
                title = "Mock Exam",
                positiveMarks = 1.0,
                negativeMarks = 0.33,
                unattendedMarks = 0.10
            ),
            questions = questions
        )

        // Initial attempt state:
        // 8 correct (Q1..Q8 answered "A")
        // 7 incorrect (Q9..Q15 answered "B")
        // 5 unanswered (Q16..Q20 unattempted)
        val initialAnswers = mutableMapOf<Int, String>()
        for (i in 1..8) initialAnswers[i] = "A"
        for (i in 9..15) initialAnswers[i] = "B"
        // Q16..Q20 remain unanswered

        val initialStats = com.example.ui.MainViewModel.calculateTestStats(paper, initialAnswers)
        assertEquals(20, initialStats.total)
        assertEquals(8, initialStats.correct)
        assertEquals(7, initialStats.incorrect)
        assertEquals(5, initialStats.unanswered)
        // Score: 8.0 - (7 * 0.33) - (5 * 0.10) = 8.0 - 2.31 - 0.50 = 5.19
        assertEquals(5.19, initialStats.score, 0.001)
        assertEquals(2.31, initialStats.penaltyIncorrect, 0.001)
        assertEquals(0.50, initialStats.penaltyUnattended, 0.001)

        // Exact net negative scoring (no zero-floor clamp)
        val allWrongAnswers = mutableMapOf<Int, String>()
        for (i in 1..20) allWrongAnswers[i] = "B" // all 20 wrong
        val allWrongStats = com.example.ui.MainViewModel.calculateTestStats(paper, allWrongAnswers)
        assertEquals(0, allWrongStats.correct)
        assertEquals(20, allWrongStats.incorrect)
        assertEquals(-6.60, allWrongStats.score, 0.001)

        val allUnattendedAnswers = emptyMap<Int, String>()
        val allUnattendedStats = com.example.ui.MainViewModel.calculateTestStats(paper, allUnattendedAnswers)
        assertEquals(0, allUnattendedStats.correct)
        assertEquals(20, allUnattendedStats.unanswered)
        assertEquals(-2.00, allUnattendedStats.score, 0.001)

        // Review phase:
        // Of the 7 incorrect questions (Q9..Q15):
        // 4 changed to correct: Q9, Q10, Q11, Q12 -> "A"
        // 2 remain incorrect: Q13, Q14 -> "B"
        // 1 becomes unanswered / skipped: Q15 -> cleared ("")
        val reviewedAnswers = initialAnswers.toMutableMap()
        reviewedAnswers[9] = "A"
        reviewedAnswers[10] = "A"
        reviewedAnswers[11] = "A"
        reviewedAnswers[12] = "A"
        reviewedAnswers[13] = "B"
        reviewedAnswers[14] = "B"
        reviewedAnswers.remove(15) // or reviewedAnswers[15] = ""

        val updatedStats = com.example.ui.MainViewModel.calculateTestStats(paper, reviewedAnswers)
        assertEquals(20, updatedStats.total)
        assertEquals(12, updatedStats.correct)
        assertEquals(2, updatedStats.incorrect)
        assertEquals(6, updatedStats.unanswered)

        // Also test when unanswered question has empty string ""
        reviewedAnswers[15] = ""
        val emptyStringStats = com.example.ui.MainViewModel.calculateTestStats(paper, reviewedAnswers)
        assertEquals(20, emptyStringStats.total)
        assertEquals(12, emptyStringStats.correct)
        assertEquals(2, emptyStringStats.incorrect)
        assertEquals(6, emptyStringStats.unanswered)
    }

    @Test
    fun `test surveying questions separation`() {
        val text = """
            85. The instrument commonly used for measuring horizontal and vertical angles in surveying is:
            (A) Chain  (B) Theodolite
            (C) Plane table  (D) Measuring tape

            76. In chain surveying, the principle used is:
            A Work from whole to part
            B Work from part to whole
            C Measure angles only
            D Use only magnetic bearings
        """.trimIndent()
        val paper = McqParser.parse(text)
        println("PARSED QUESTIONS COUNT: " + paper.questions.size)
        for (q in paper.questions) {
            println("Q" + q.num + ": " + q.question)
            println("Options: A=" + q.options.a + ", B=" + q.options.b + ", C=" + q.options.c + ", D=" + q.options.d)
        }
    }

    @Test
    fun `batch validation accurately classifies valid, warning, and invalid exam paper JSONs`() {
        val validJson = """
            {
              "metadata": {
                "title": "Indian History Mock",
                "subject": "History & Culture",
                "setNumber": "Set A"
              },
              "questions": [
                {
                  "num": 1,
                  "question": "Who was the founder of the Maurya Empire?",
                  "options": {
                    "a": "Chandragupta Maurya",
                    "b": "Ashoka",
                    "c": "Bindusara",
                    "d": "Harsha"
                  },
                  "correctAnswer": "A",
                  "explanation": "Chandragupta Maurya founded the Maurya Empire in 322 BCE."
                }
              ]
            }
        """.trimIndent()

        val warningJson = """
            {
              "metadata": {
                "title": "Incomplete Physics Set",
                "subject": "Physics"
              },
              "questions": [
                {
                  "num": 1,
                  "question": "What is the speed of light in vacuum?",
                  "options": {
                    "a": "3x10^8 m/s",
                    "b": "3x10^6 m/s"
                  }
                }
              ]
            }
        """.trimIndent()

        val invalidJson = """
            {
              "metadata": {
                "title": "Broken Set"
              },
              "questions": []
            }
        """.trimIndent()

        val malformedJson = "{ broken json content ..."

        // 1. Test Valid JSON
        val validItems = com.example.util.BatchExamImporter.validatePaperJson(validJson, "history.json")
        assertEquals(1, validItems.size)
        assertEquals(com.example.model.BatchItemStatus.VALID, validItems[0].status)
        assertTrue(validItems[0].isSelected)
        assertEquals("Indian History Mock", validItems[0].paper?.metadata?.title)
        assertEquals(1, validItems[0].paper?.questions?.size)

        // 2. Test Warning JSON (missing answer key & fewer than 4 options)
        val warningItems = com.example.util.BatchExamImporter.validatePaperJson(warningJson, "physics.json")
        assertEquals(1, warningItems.size)
        assertEquals(com.example.model.BatchItemStatus.WARNING, warningItems[0].status)
        assertTrue(warningItems[0].isSelected)
        assertTrue(warningItems[0].warningCount > 0)

        // 3. Test Invalid JSON (0 questions)
        val invalidItems = com.example.util.BatchExamImporter.validatePaperJson(invalidJson, "broken.json")
        assertEquals(1, invalidItems.size)
        assertEquals(com.example.model.BatchItemStatus.INVALID, invalidItems[0].status)
        assertFalse(invalidItems[0].isSelected)
        assertTrue(invalidItems[0].errorCount > 0)

        // 4. Test Malformed JSON
        val malformedItems = com.example.util.BatchExamImporter.validatePaperJson(malformedJson, "malformed.json")
        assertEquals(1, malformedItems.size)
        assertEquals(com.example.model.BatchItemStatus.INVALID, malformedItems[0].status)
        assertFalse(malformedItems[0].isSelected)
    }

    @Test
    fun `batch validation handles JSON array containing multiple exam papers`() {
        val arrayJson = """
            [
              {
                "metadata": { "title": "Paper 1 - Chemistry", "subject": "Chemistry" },
                "questions": [
                  {
                    "num": 1,
                    "question": "What is the atomic number of Hydrogen?",
                    "options": { "a": "1", "b": "2", "c": "3", "d": "4" },
                    "correctAnswer": "A",
                    "explanation": "Hydrogen has 1 proton."
                  }
                ]
              },
              {
                "metadata": { "title": "Paper 2 - Biology", "subject": "Biology" },
                "questions": [
                  {
                    "num": 1,
                    "question": "What is the powerhouse of the cell?",
                    "options": { "a": "Nucleus", "b": "Mitochondria", "c": "Ribosome", "d": "Golgi apparatus" },
                    "correctAnswer": "B",
                    "explanation": "Mitochondria generates ATP."
                  }
                ]
              }
            ]
        """.trimIndent()

        val items = com.example.util.BatchExamImporter.validatePaperJson(arrayJson, "combined.json")
        assertEquals(2, items.size)
        assertEquals("Paper 1 - Chemistry", items[0].paper?.metadata?.title)
        assertEquals("Paper 2 - Biology", items[1].paper?.metadata?.title)
        assertEquals(com.example.model.BatchItemStatus.VALID, items[0].status)
        assertEquals(com.example.model.BatchItemStatus.VALID, items[1].status)

        val summary = com.example.util.BatchExamImporter.computeSummary(items)
        assertEquals(2, summary.totalPapers)
        assertEquals(2, summary.validCount)
        assertEquals(0, summary.invalidCount)
        assertEquals(2, summary.totalQuestions)
    }

    @Test
    fun `parser cleanly separates two questions with horizontal single-spaced options`() {
        val raw = """
            1. What is the capital of India?
            A) Mumbai B) New Delhi C) Kolkata D) Chennai
            2. What is the currency of Japan?
            A) Yen B) Yuan C) Won D) Dollar
        """.trimIndent()

        val paper = McqParser.parse(raw)
        assertEquals("Should parse exactly 2 questions without merging", 2, paper.questions.size)

        val q1 = paper.questions[0]
        assertEquals(1, q1.num)
        assertTrue(q1.question.contains("capital of India"))
        assertEquals("Mumbai", q1.options.a)
        assertEquals("New Delhi", q1.options.b)
        assertEquals("Kolkata", q1.options.c)
        assertEquals("Chennai", q1.options.d)

        val q2 = paper.questions[1]
        assertEquals(2, q2.num)
        assertTrue(q2.question.contains("currency of Japan"))
        assertEquals("Yen", q2.options.a)
        assertEquals("Yuan", q2.options.b)
        assertEquals("Won", q2.options.c)
        assertEquals("Dollar", q2.options.d)
    }

    @Test
    fun `parser separates two questions glued onto the same line`() {
        val raw = "1. First question? (A) OptA (B) OptB (C) OptC (D) OptD 2. Second question? (A) AnsA (B) AnsB (C) AnsC (D) AnsD"
        val paper = McqParser.parse(raw)
        assertEquals("Should split and parse 2 questions from single line", 2, paper.questions.size)
        assertEquals(1, paper.questions[0].num)
        assertEquals(2, paper.questions[1].num)
        assertEquals("OptA", paper.questions[0].options.a)
        assertEquals("AnsA", paper.questions[1].options.a)
    }

    @Test
    fun `parser supports numeric options 1 to 4 and Hindi options`() {
        val rawHindi = """
            1. भारत की राजधानी क्या है?
            (क) मुंबई (ख) नई दिल्ली (ग) कोलकाता (घ) चेन्नई
            2. जापान की मुद्रा क्या है?
            (1) येन (2) युआन (3) वॉन (4) डॉलर
        """.trimIndent()

        val paper = McqParser.parse(rawHindi)
        assertEquals(2, paper.questions.size)
        assertEquals("मुंबई", paper.questions[0].options.a)
        assertEquals("नई दिल्ली", paper.questions[0].options.b)
        assertEquals("येन", paper.questions[1].options.a)
        assertEquals("युआन", paper.questions[1].options.b)
    }

    @Test
    fun `parser separates questions even with bracketed numbers or Q prefix`() {
        val raw = """
            (1) First test question?
            (A) Alpha
            (B) Beta
            (C) Gamma
            (D) Delta
            Q2. Second test question?
            (A) One
            (B) Two
            (C) Three
            (D) Four
        """.trimIndent()

        val paper = McqParser.parse(raw)
        assertEquals(2, paper.questions.size)
        assertEquals(1, paper.questions[0].num)
        assertEquals(2, paper.questions[1].num)
    }

    @Test
    fun `parser parses civil mixed pattern questions answers and explanations with high precision`() {
        val paper = McqParser.parse(SampleData.SAMPLE_CIVIL_MIXED_PATTERN)
        assertEquals("Civil & Structural Engineering Core Assessment", paper.metadata.title)
        assertEquals("Civil & Environmental Engineering", paper.metadata.subject)
        assertEquals(10, paper.questions.size)

        // Verify all 10 answer keys
        val expectedKeys = listOf("C", "B", "A", "A", "B", "B", "A", "B", "B", "C")
        for (i in 0 until 10) {
            val q = paper.questions[i]
            assertEquals(i + 1, q.num)
            assertEquals("Question ${i + 1} answer mismatch", expectedKeys[i], q.correctAnswer)
            assertTrue("Question ${i + 1} explanation missing", !q.explanation.isNullOrBlank())
        }

        // Verify specific math calculations in explanations
        val q1 = paper.questions[0]
        assertTrue(q1.options.c.contains("2"))
        val q3 = paper.questions[2]
        assertTrue(q3.question.contains("1.55 m/s"))
        val q5 = paper.questions[4]
        assertTrue(q5.options.b.contains("0.062"))
        val q10 = paper.questions[9]
        assertTrue(q10.question.contains("3 m"))
        assertTrue(q10.options.c.contains("0.271 m"))
    }

    @Test
    fun `parser parses standard 3-phase psychometric generated mcqs with explanations and memory anchor`() {
        val input = """
            Phase 1: Questions
            Q1. A particle of mass §m§ accelerates uniformly from rest under a constant force §F§. What is the kinetic energy acquired after traversing distance §d§?
            (A) §F \cdot d§
            (B) §\frac{1}{2} F \cdot d§
            (C) §2 F \cdot d§
            (D) §F^2 \cdot d§

            Q2. What is the conjugate base of §H_2SO_4§ in aqueous solution?
            (A) §SO_4^{2-}§
            (B) §HSO_4^-§
            (C) §H_3SO_4^+§
            (D) §H_3O^+§

            Phase 2: Answer Key
            Q1: (A)
            Q2: (B)

            Phase 3: Explanations & Solution
            Q1.
            • Core Concept & Reasoning: The work-energy theorem states that work done by the net force equals change in kinetic energy: §W = \Delta K = F \cdot d§.
            • Option Breakdown:
              - (A) Correct: Direct application of §W = F \cdot d = K§.
              - (B) Incorrect: Erroneously introduces a factor of 1/2 from (1/2)mv^2.
              - (C) Incorrect: Incorrect multiplier.
              - (D) Incorrect: Dimensional inconsistency.
            • Memory Anchor / Key Takeaway: Work done by net force equals kinetic energy gained.

            Q2.
            • Core Concept & Reasoning: A conjugate base is formed when a Bronsted-Lowry acid loses a single proton (§H^+§).
            • Option Breakdown:
              - (A) Incorrect: This is the conjugate base of bisulfate, not sulfuric acid.
              - (B) Correct: Removal of one proton from §H_2SO_4§ yields bisulfate ion §HSO_4^-§.
              - (C) Incorrect: Represents protonation.
              - (D) Incorrect: Hydronium ion is the conjugate acid of water.
            • Memory Anchor / Key Takeaway: Conjugate base = Acid minus §H^+§.
        """.trimIndent().replace("§", "$")

        val paper = McqParser.parse(input)
        assertEquals(2, paper.questions.size)

        val q1 = paper.questions[0]
        assertEquals(1, q1.num)
        assertTrue(q1.question.contains("particle of mass"))
        assertTrue(q1.question.contains("\$m\$"))
        assertEquals("\$F \\cdot d\$", q1.options.a)
        assertEquals("A", q1.correctAnswer)
        assertNotNull(q1.explanation)
        assertTrue(q1.explanation!!.contains("Core Concept & Reasoning"))
        assertTrue(q1.explanation!!.contains("Memory Anchor"))

        val q2 = paper.questions[1]
        assertEquals(2, q2.num)
        assertTrue(q2.question.contains("conjugate base"))
        assertEquals("\$HSO_4^-\$", q2.options.b)
        assertEquals("B", q2.correctAnswer)
        assertNotNull(q2.explanation)
        assertTrue(q2.explanation!!.contains("Removal of one proton"))
    }

    @Test
    fun `parser parses multilingual psychometric mcqs with universal labels`() {
        val input = """
            Phase 1: Questions
            Q1. प्रकाश संश्लेषण के दौरान ऑक्सीजन किस अणु के विघटन से उत्पन्न होती है?
            (A) कार्बन डाइऑक्साइड
            (B) ग्लूकोज
            (C) जल (§H_2O§)
            (D) क्लोरोफिल

            Phase 2: Answer Key
            Q1: (C)

            Phase 3: Explanations & Solution
            Q1.
            • Core Concept & Reasoning: फोटोलाइसिस के दौरान जल के अणुओं का प्रकाश-अपघटन होता है।
            • Option Breakdown:
              - (A) Incorrect: CO2 का उपयोग केल्विन चक्र में होता है।
              - (B) Incorrect: ग्लूकोज अंतिम उत्पाद है।
              - (C) Correct: जल (H2O) के प्रकाश-अपघटन से O2 मुक्त होती है।
              - (D) Incorrect: क्लोरोफिल प्रकाश अवशोषित करता है।
            • Memory Anchor / Key Takeaway: प्रकाश-रासायनिक अभिक्रिया में जल ऑक्सीजन का स्रोत है।
        """.trimIndent().replace("§", "$")

        val paper = McqParser.parse(input)
        assertEquals(1, paper.questions.size)

        val q1 = paper.questions[0]
        assertEquals(1, q1.num)
        assertTrue(q1.question.contains("प्रकाश संश्लेषण"))
        assertEquals("कार्बन डाइऑक्साइड", q1.options.a)
        assertEquals("जल (\$H_2O\$)", q1.options.c)
        assertEquals("C", q1.correctAnswer)
        assertNotNull(q1.explanation)
        assertTrue(q1.explanation!!.contains("फोटोलाइसिस"))
        assertTrue(q1.explanation!!.contains("Memory Anchor"))
    }

    @Test
    fun `candidate identification in AttemptEntity is serialized and recovered cleanly`() {
        val sampleAnswers = mapOf(1 to "B", 2 to "A", 3 to "C", 4 to "A", 5 to "A", 6 to "A")
        val rollNo = "2026928088"
        val bookletNo = "9987078"
        val setCode = "A"

        val answersJson = com.example.data.AttemptEntity.buildAnswersJson(
            map = sampleAnswers,
            rollNumber = rollNo,
            bookletNumber = bookletNo,
            bookletCode = setCode
        )

        val attempt = com.example.data.AttemptEntity(
            id = 1L,
            paperId = 101L,
            answersJson = answersJson,
            isSubmitted = true,
            totalQuestions = 6,
            score = 24.0
        )

        assertEquals(rollNo, attempt.getCandidateRollNumber())
        assertEquals(bookletNo, attempt.getCandidateBookletNumber())
        assertEquals(setCode, attempt.getCandidateBookletCode())

        val recoveredAnswers = attempt.getAnswersMap()
        assertEquals(6, recoveredAnswers.size)
        assertEquals("B", recoveredAnswers[1])
        assertEquals("A", recoveredAnswers[2])
        assertEquals("C", recoveredAnswers[3])
        assertEquals("A", recoveredAnswers[4])
        assertEquals("A", recoveredAnswers[5])
        assertEquals("A", recoveredAnswers[6])
    }

    @Test
    fun `computeNextSetForSubject properly handles existing sets and increments`() {
        val papers = listOf(
            PaperEntity(
                id = 1L,
                title = "Mock Test",
                subject = "Science",
                topic = "Physics",
                setNumber = "01",
                folderId = "folder-123",
                questionCount = 5,
                answerKeyCount = 5,
                explanationCount = 5,
                rawText = "1. Q\nA) 1\nAnswer: A"
            ),
            PaperEntity(
                id = 2L,
                title = "Mock Test 2",
                subject = "Science",
                topic = "Physics",
                setNumber = "02",
                folderId = "folder-123",
                questionCount = 5,
                answerKeyCount = 5,
                explanationCount = 5,
                rawText = "1. Q\nA) 1\nAnswer: A"
            )
        )

        val nextSet = com.example.ui.components.computeNextSetForSubject("Science", papers)
        assertEquals("03", nextSet)

        val nextSetOther = com.example.ui.components.computeNextSetForSubject("Maths", papers)
        assertEquals("01", nextSetOther)
    }

    @Test
    fun `computeNextSetForSubject isolates target folder and prevents cross-folder and title-year mismatches`() {
        val folders = listOf(
            com.example.model.FolderItem(id = "upsc", name = "UPSC CSE", color = "#0284C7", parentId = null),
            com.example.model.FolderItem(id = "upsc-prelims", name = "Prelims", color = "#0284C7", parentId = "upsc"),
            com.example.model.FolderItem(id = "upsc-mocks", name = "Mock Tests", color = "#0284C7", parentId = "upsc-prelims"),
            com.example.model.FolderItem(id = "ssc", name = "SSC CGL", color = "#2563EB", parentId = null),
            com.example.model.FolderItem(id = "ssc-mocks", name = "Mock Tests", color = "#2563EB", parentId = "ssc"),
            com.example.model.FolderItem(id = "literal-slash", name = "UPSC/Interview", color = "#059669", parentId = null)
        )

        val papers = listOf(
            // Two papers in UPSC Mock Tests: one with "Set A" + year 2024 in title, one with "Set02"
            PaperEntity(
                id = 1L,
                title = "UPSC Prelims 2024 Mock",
                subject = "UPSC CSE > Prelims > Mock Tests",
                setNumber = "Set A",
                folderId = "upsc-mocks",
                questionCount = 10,
                answerKeyCount = 10,
                explanationCount = 0,
                rawText = "1. Q\n(A) 1\nAnswer: A"
            ),
            PaperEntity(
                id = 2L,
                title = "Prelims 2025 Revision",
                subject = "Old Subject Name",
                setNumber = "Set02",
                folderId = "upsc-mocks",
                questionCount = 10,
                answerKeyCount = 10,
                explanationCount = 0,
                rawText = "1. Q\n(A) 1\nAnswer: A"
            ),
            // Papers in SSC Mock Tests with high set numbers (should NOT leak into UPSC Mock Tests)
            PaperEntity(
                id = 3L,
                title = "SSC CGL Mock",
                subject = "Mock Tests",
                setNumber = "08",
                folderId = "ssc-mocks",
                questionCount = 10,
                answerKeyCount = 10,
                explanationCount = 0,
                rawText = "1. Q\n(A) 1\nAnswer: A"
            ),
            // Unfiled root paper
            PaperEntity(
                id = 4L,
                title = "General Unfiled 2024",
                subject = "General Studies",
                setNumber = "03",
                folderId = null,
                questionCount = 10,
                answerKeyCount = 10,
                explanationCount = 0,
                rawText = "1. Q\n(A) 1\nAnswer: A"
            )
        )

        // Title year "2024" must not produce "2025", and SSC's "08" must not leak into UPSC's folder
        val nextUpscMock = com.example.ui.components.computeNextSetForSubject(
            subject = "UPSC CSE > Prelims > Mock Tests",
            papers = papers,
            targetFolderId = "upsc-mocks",
            folders = folders
        )
        assertEquals("03", nextUpscMock)

        // Path resolution alone (without passing targetFolderId) resolves 3-level hierarchy accurately
        val resolvedUpsc = com.example.ui.components.resolveFolderBySubjectPath(
            "UPSC CSE / Prelims / Mock Tests",
            folders
        )
        assertEquals("upsc-mocks", resolvedUpsc?.id)

        val resolvedLiteral = com.example.ui.components.resolveFolderBySubjectPath(
            "UPSC//Interview",
            folders
        )
        assertEquals("literal-slash", resolvedLiteral?.id)

        // SSC Mock Tests has 08 -> next should be 09
        val nextSscMock = com.example.ui.components.computeNextSetForSubject(
            subject = "SSC CGL > Mock Tests",
            papers = papers,
            targetFolderId = null,
            folders = folders
        )
        assertEquals("09", nextSscMock)

        // Empty subject (root unfiled) sees unfiled General Studies Set 03 -> next is 04
        val nextRoot = com.example.ui.components.computeNextSetForSubject(
            subject = "",
            papers = papers,
            targetFolderId = null,
            folders = folders
        )
        assertEquals("04", nextRoot)

        // Brand-new folder path with no papers starts at 01
        val nextNewFolder = com.example.ui.components.computeNextSetForSubject(
            subject = "Physics > Optics",
            papers = papers,
            targetFolderId = null,
            folders = folders
        )
        assertEquals("01", nextNewFolder)

        // Custom 3-level folder appears with full Parent > Child > Sub-Child breadcrumb path in FolderPathHelper
        val searchResults = com.example.util.FolderPathHelper.searchUserFolders(
            query = "Mock Tests",
            folders = folders,
            maxResults = 10
        )
        assertTrue(searchResults.any { it.fullPath == "UPSC CSE > Prelims > Mock Tests" && it.levelLabel == "Sub-Child" })
        assertTrue(searchResults.any { it.fullPath == "SSC CGL > Mock Tests" && it.levelLabel == "Child" })
    }

    @Test
    fun `PaperLanguageDetector accurately detects single-language and bilingual question papers`() {
        val bioLangs = com.example.util.PaperLanguageDetector.detectLanguages(
            com.example.data.SampleData.SAMPLE_BIOLOGY_ADVANCED,
            "Advanced Biological Sciences & Genetics Assessment"
        )
        assertEquals(listOf("English"), bioLangs)
        assertEquals(
            "Available in English",
            com.example.util.PaperLanguageDetector.formatAvailableLabel(bioLangs)
        )

        val hindiLangs = com.example.util.PaperLanguageDetector.detectLanguages(
            com.example.data.SampleData.SAMPLE_HINDI_PAPER,
            "उत्तराखण्ड एवं भारत सामान्य ज्ञान प्रश्न पत्र"
        )
        assertEquals(listOf("Hindi"), hindiLangs)
        assertEquals(
            "Available in Hindi",
            com.example.util.PaperLanguageDetector.formatAvailableLabel(hindiLangs)
        )

        val koreanLangs = com.example.util.PaperLanguageDetector.detectLanguages(
            com.example.data.SampleData.SAMPLE_KOREAN_EXAM,
            "한국어 능력 평가 및 어문 규범 모의고사"
        )
        assertEquals(listOf("Korean"), koreanLangs)

        val bilingualText = """
            1. Which article guarantees the Right to Equality under the Indian Constitution?
            भारतीय संविधान के किस अनुच्छेद के अंतर्गत समता का अधिकार प्रदान किया गया है?
            (A) Article 14 / अनुच्छेद 14
            (B) Article 19 / अनुच्छेद 19
            (C) Article 21 / अनुच्छेद 21
            (D) Article 32 / अनुच्छेद 32
        """.trimIndent()
        val bilingualLangs = com.example.util.PaperLanguageDetector.detectLanguages(bilingualText)
        assertEquals(listOf("English", "Hindi"), bilingualLangs)
        assertEquals(
            "Available in English, Hindi",
            com.example.util.PaperLanguageDetector.formatAvailableLabel(bilingualLangs)
        )

        val hindiJson = """
            [{"num":1,"question":"भारत की राजधानी क्या है?","options":{"a":"दिल्ली","b":"मुंबई","c":"कोलकाता","d":"चेन्नई"},"correctAnswer":"A","explanation":"नई दिल्ली भारत की राजधानी है।"}]
        """.trimIndent()
        assertEquals(
            listOf("Hindi"),
            com.example.util.PaperLanguageDetector.detectLanguages(hindiJson)
        )
    }

    @Test
    fun `PaperLanguageDetector disambiguates same-script Latin and Devanagari languages and respects Language headers`() {
        // 1. Explicit Language: headers and JSON metadata override script heuristics
        assertEquals(
            listOf("Marathi"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "Language: Marathi\n1. प्रश्न १?"
            )
        )
        assertEquals(
            listOf("French"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "Language: French\n1. Question un?"
            )
        )
        assertEquals(
            listOf("English", "Marathi"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "Language: Bilingual (English & Marathi)\n1. Sample question"
            )
        )
        assertEquals(
            listOf("Spanish"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                """{"language":"Spanish","questions":[{"num":1,"question":"¿Capital?"}]}"""
            )
        )
        // Prompt-template placeholder should NOT trigger explicit override
        assertEquals(
            listOf("English"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "Language: [Insert Language]\n1. Which of the following statements is correct about the cell?"
            )
        )

        // 2. Devanagari disambiguation (Marathi, Sanskrit, Nepali)
        val marathiPaper = """
            1. खालीलपैकी कोणते शहर महाराष्ट्राची राजधानी आहे आणि तेथे मुख्य कार्यालय आहे?
            (A) पुणे
            (B) मुंबई
            (C) नागपूर
            (D) नाशिक
            स्पष्टीकरण: मुंबई ही महाराष्ट्राची राजधानी आहे आणि ती आर्थिक केंद्र देखील आहे.
        """.trimIndent()
        assertEquals(
            listOf("Marathi"),
            com.example.util.PaperLanguageDetector.detectLanguages(marathiPaper)
        )

        val sanskritPaper = """
            1. अधोलिखितेषु कः प्रश्नः समीचीनम् अस्ति इति कथम् उक्तम् वर्तते?
            (A) रामः वनं गच्छति
            (B) सीता अत्र भवति
            (C) बालकः पठति
            (D) गुरुः वदति
            स्पष्टीकरणम्: अत्र प्रथमः विकल्पः सत्यम् अस्ति यतोहि तस्य प्रयोगः साधुः वर्तते।
        """.trimIndent()
        assertEquals(
            listOf("Sanskrit"),
            com.example.util.PaperLanguageDetector.detectLanguages(sanskritPaper)
        )

        val nepaliPaper = """
            1. निम्नलिखितमध्ये कुन सहर नेपालको राजधानी हो र यसलाई प्रमुख केन्द्र भनिन्छ?
            (A) पोखरा
            (B) काठमाडौँ
            (C) ललितपुर
            (D) विराटनगर
            व्याख्या: काठमाडौँ नेपालको राजधानी हो तर अन्य सहरहरू पनि महत्वपूर्ण छन्।
        """.trimIndent()
        assertEquals(
            listOf("Nepali"),
            com.example.util.PaperLanguageDetector.detectLanguages(nepaliPaper)
        )

        // 3. Latin disambiguation (French, Spanish, German, bilingual English + French, and English with diacritic names)
        val frenchPaper = """
            1. Parmi les propositions suivantes, quelle est la capitale de la France selon la réponse officielle?
            (A) Lyon
            (B) Paris
            (C) Marseille
            (D) Toulouse
            Explication: Paris est la capitale dans cette région pour les citoyens.
        """.trimIndent()
        assertEquals(
            listOf("French"),
            com.example.util.PaperLanguageDetector.detectLanguages(frenchPaper)
        )

        val spanishPaper = """
            1. ¿Cuál de las siguientes opciones es correcta según el análisis del texto?
            (A) Madrid
            (B) Sevilla
            (C) Valencia
            (D) Bilbao
            Explicación: La respuesta correcta para esta pregunta es Madrid por su historia.
        """.trimIndent()
        assertEquals(
            listOf("Spanish"),
            com.example.util.PaperLanguageDetector.detectLanguages(spanishPaper)
        )

        val germanPaper = """
            1. Welche der folgenden Aussagen über die Hauptstadt ist richtig und wird oft genannt?
            (A) München
            (B) Berlin
            (C) Hamburg
            (D) Köln
            Erklärung: Berlin ist die Hauptstadt und nicht nur ein Bundesland für die Bürger.
        """.trimIndent()
        assertEquals(
            listOf("German"),
            com.example.util.PaperLanguageDetector.detectLanguages(germanPaper)
        )

        val englishFrenchBilingual = """
            1. Which of the following statements is the correct answer between these two choices?
            Parmi les réponses suivantes, quelle est la bonne option dans cette question?
            (A) Oxygen
            (B) Nitrogen
            (C) Carbon
            (D) Helium
        """.trimIndent()
        val engFrResult = com.example.util.PaperLanguageDetector.detectLanguages(englishFrenchBilingual)
        assertEquals(setOf("English", "French"), engFrResult.toSet())
        assertEquals(2, engFrResult.size)

        val englishWithDiacritics = """
            1. Which of the following statements is correct about the Schrödinger equation and Ampère's law?
            (A) Both describe quantum states only
            (B) Ampère's law relates magnetic fields to electric current in the circuit
            (C) Neither is used in physics
            (D) Only Schrödinger studied electromagnetism
        """.trimIndent()
        assertEquals(
            listOf("English"),
            com.example.util.PaperLanguageDetector.detectLanguages(englishWithDiacritics)
        )

        // 4. Short 1-question bilingual paper (< 60 Latin letters) still detects both languages
        val shortBilingual = """
            1. What is the capital of India?
            भारत की राजधानी क्या है?
            (A) Delhi / दिल्ली
            (B) Mumbai / मुंबई
        """.trimIndent()
        assertEquals(
            listOf("English", "Hindi"),
            com.example.util.PaperLanguageDetector.detectLanguages(shortBilingual)
        )
    }

    @Test
    fun `PaperLanguageDetector detects expanded scripts, same-script regional languages, and custom headers`() {
        // 1. Additional Unicode scripts
        assertEquals(
            listOf("Greek"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. Ποια είναι η πρωτεύουσα της Ελλάδας; (A) Αθήνα (B) Θεσσαλονίκη"
            )
        )
        assertEquals(
            listOf("Hebrew"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. מהי בירת ישראל על פי השאלה הזו? (A) ירושלים (B) תל אביב"
            )
        )
        assertEquals(
            listOf("Thai"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. เมืองหลวงของประเทศไทยคือข้อใดต่อไปนี้? (A) กรุงเทพมหานคร (B) เชียงใหม่"
            )
        )
        assertEquals(
            listOf("Sinhala"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. ශ්‍රී ලංකාවේ අගනුවර කුමක්ද? (A) කොළඹ (B) මහනුවර"
            )
        )
        assertEquals(
            listOf("Burmese"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. မြန်မာနိုင်ငံ၏ မြို့တော်မှာ အဘယ်နည်း? (A) နေပြည်တော် (B) ရန်ကုန်"
            )
        )
        assertEquals(
            listOf("Georgian"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. რომელია საქართველოს დედაქალაქი? (A) თბილისი (B) ბათუმი"
            )
        )
        assertEquals(
            listOf("Armenian"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. Ո՞րն է Հայաստանի մայրաքաղաքը: (A) Երևան (B) Գյումրի"
            )
        )
        assertEquals(
            listOf("Amharic"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. የኢትዮጵያ ዋና ከተማ ማን ናት? (A) አዲስ አበባ (B) ባህር ዳር"
            )
        )
        assertEquals(
            listOf("Santali"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. ᱵᱷᱟᱨᱚᱛ ᱨᱮᱱᱟᱜ ᱨᱟᱡᱽᱫᱷᱟᱱᱤ ᱪᱮᱫ ᱠᱟᱱᱟ? (A) ᱫᱤᱞᱞᱤ (B) ᱨᱟᱺᱪᱤ"
            )
        )
        assertEquals(
            listOf("Manipuri"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. ꯃꯅꯤꯄꯨꯔꯒꯤ ꯀꯣꯅꯨꯡ ꯀꯔꯤꯅꯣ? (A) ꯏꯝꯐꯥꯜ (B) ꯊꯧꯕꯥꯜ"
            )
        )

        // 2. Arabic-script disambiguation (Arabic vs Urdu vs Persian vs Sindhi)
        assertEquals(
            listOf("Arabic"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. ما هي الإجابة الصحيحة من بين الخيارات التالية في هذا السؤال؟"
            )
        )
        assertEquals(
            listOf("Urdu"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. درج ذیل میں سے کون سا جواب درست ہے اور اس کی وضاحت کیا ہے؟"
            )
        )
        assertEquals(
            listOf("Persian"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. کدام گزینه برای این پرسش پاسخ صحیح است و توضیح آن چیست؟"
            )
        )
        assertEquals(
            listOf("Sindhi"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. سنڌ جي گاديءَ جو هنڌ ڪهڙو شهر آهي ۽ ان جا ٻيا نالا ڇا آهن؟"
            )
        )

        // 3. Bengali vs Assamese disambiguation
        assertEquals(
            listOf("Bengali"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. নিচের কোনটি সঠিক উত্তর এবং এই প্রশ্নের ব্যাখ্যা কী?"
            )
        )
        assertEquals(
            listOf("Assamese"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. তলৰ কোনটো শুদ্ধ উত্তৰ আৰু অসমৰ ৰাজধানী ক'ত অৱস্থিত?"
            )
        )

        // 4. Cyrillic disambiguation (Russian vs Ukrainian vs Serbian vs Bulgarian)
        assertEquals(
            listOf("Russian"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. Какой из следующих ответов является правильным для этого вопроса?"
            )
        )
        assertEquals(
            listOf("Ukrainian"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. Який із наступних варіантів є правильна відповідь для цього питання?"
            )
        )
        assertEquals(
            listOf("Serbian"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. Који од следећих одговора је тачан и које је објашњење?"
            )
        )
        assertEquals(
            listOf("Bulgarian"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. Кой от следните отговори е вярно твърдение и какво е обяснението?"
            )
        )

        // 5. Expanded Devanagari (Maithili, Konkani)
        assertEquals(
            listOf("Maithili"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. निम्नलिखितमे ककर उत्तर सही अछि आ एहि प्रश्नक व्याख्या की छथि?"
            )
        )
        assertEquals(
            listOf("Konkani"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. सकयल दिल्ल्या पर्यायांतलो खंयचो पर्याय बरोबर आसा आनी ताचे कारण कितें?"
            )
        )

        // 6. Expanded Latin languages (Turkish, Indonesian, Vietnamese, Dutch, Polish, Swahili)
        assertEquals(
            listOf("Turkish"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. Aşağıdakilerden hangisi doğru bir açıklama değildir ve bunun için neden nedir?"
            )
        )
        assertEquals(
            listOf("Indonesian"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. Manakah pernyataan berikut yang benar untuk pembahasan jawaban ini?"
            )
        )
        assertEquals(
            listOf("Vietnamese"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. Câu nào sau đây là đáp án đúng và giải thích cho câu hỏi này?"
            )
        )
        assertEquals(
            listOf("Dutch"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. Welke van de volgende uitspraken is juist en wat is het antwoord?"
            )
        )
        assertEquals(
            listOf("Polish"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. Który z poniższych wniosków jest poprawną odpowiedzią oraz jakie jest wyjaśnienie?"
            )
        )
        assertEquals(
            listOf("Swahili"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "1. Kipi kati ya majibu yafuatayo ni jibu sahihi katika swali hili?"
            )
        )

        // 7. Custom unlisted language declared explicitly in Language: header + preset list coverage
        assertEquals(
            listOf("Esperanto"),
            com.example.util.PaperLanguageDetector.detectLanguages(
                "Language: Esperanto\n1. Kio estas la ĉefurbo?"
            )
        )
        assertTrue(com.example.util.PaperLanguageDetector.PRESET_LANGUAGES.size >= 35)
    }
}

