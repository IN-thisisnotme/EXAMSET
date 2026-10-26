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

        assertEquals("National Science & Technology Olympiad", paper.metadata.title)
        assertEquals("General Science", paper.metadata.subject)
        assertEquals(10, paper.questions.size)

        // Question 1 verification
        val q1 = paper.questions.first()
        assertEquals(1, q1.num)
        assertTrue(q1.question.contains("chemical compound is commonly known as dry ice"))
        assertEquals("Solid Carbon Dioxide (CO₂)", q1.options.a)
        assertEquals("Solid Nitrogen (N₂)", q1.options.b)
        assertEquals("A", q1.correctAnswer)
        assertNotNull(q1.explanation)
        assertTrue(q1.explanation!!.contains("sublimation") || q1.explanation!!.contains("sublimes"))

        // Question 10 verification
        val q10 = paper.questions.last()
        assertEquals(10, q10.num)
        assertEquals("C", q10.correctAnswer)
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
        assertTrue(html.contains("chemical compound is commonly known as dry ice"))
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

        // Q1 correct is A, Q2 correct is B, Q3 correct is C, Q10 correct is C
        val answers = mapOf(
            1 to "A", // Correct
            2 to "B", // Correct
            3 to "A"  // Incorrect (correct is C)
            // Q4..Q10 are unattempted (7 questions)
        )

        val correctCount = parsed.questions.count { q -> answers[q.num]?.equals(q.correctAnswer, ignoreCase = true) == true }
        val incorrectCount = parsed.questions.count { q ->
            val userAns = answers[q.num]
            userAns != null && q.correctAnswer != null && !userAns.equals(q.correctAnswer, ignoreCase = true)
        }
        val unattemptedCount = parsed.questions.size - answers.size

        assertEquals(2, correctCount)
        assertEquals(1, incorrectCount)
        assertEquals(7, unattemptedCount)

        // Targeted Retry for Incorrect: should isolate question 3
        val incorrectQuestions = parsed.questions.filter { q ->
            val userAns = answers[q.num]
            userAns != null && q.correctAnswer != null && !userAns.equals(q.correctAnswer, ignoreCase = true)
        }.map { it.num }

        assertEquals(listOf(3), incorrectQuestions)

        // Targeted Retry for Unanswered: should isolate questions 4..10
        val unansweredQuestions = parsed.questions.filter { q -> answers[q.num] == null }.map { it.num }
        assertEquals(7, unansweredQuestions.size)
        assertTrue(unansweredQuestions.contains(4))
        assertTrue(unansweredQuestions.contains(10))

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
                negativeMarks = 0.33
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

        // Test minimum max score bound of 0 regardless of how much incorrect/unattended
        val allWrongAnswers = mutableMapOf<Int, String>()
        for (i in 1..20) allWrongAnswers[i] = "B" // all 20 wrong
        val allWrongStats = com.example.ui.MainViewModel.calculateTestStats(paper, allWrongAnswers)
        assertEquals(0, allWrongStats.correct)
        assertEquals(20, allWrongStats.incorrect)
        assertEquals(0.0, allWrongStats.score, 0.001)

        val allUnattendedAnswers = emptyMap<Int, String>()
        val allUnattendedStats = com.example.ui.MainViewModel.calculateTestStats(paper, allUnattendedAnswers)
        assertEquals(0, allUnattendedStats.correct)
        assertEquals(20, allUnattendedStats.unanswered)
        assertEquals(0.0, allUnattendedStats.score, 0.001)

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
}

