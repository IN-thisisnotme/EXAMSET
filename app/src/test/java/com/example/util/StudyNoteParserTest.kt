package com.example.util

import com.example.data.StudyNoteEntity
import com.example.model.KnowledgeCollectionType
import com.example.model.KnowledgeDepth
import com.example.model.KnowledgeFormatRule
import com.example.model.KnowledgeLayerOrder
import com.example.model.KnowledgeTransformAction
import com.example.model.McqQuestion
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyNoteParserTest {

    @Test
    fun `parseBlocks handles headings, one-liners, multi-liner cards with formulas, callouts and tables`() {
        val raw = """
            ## Thermodynamics Core
            1. **First Law** -> Energy is conserved across thermodynamic processes.
              - Formula: ${'$'}\Delta U = Q - W${'$'}
              - Sign convention: Work done by system is positive.
            2. **Isochoric Process** -> Volume remains constant so work done is zero.

            > Exam Trap: Ideal gas internal energy depends strictly on temperature.

            | Process | Constant Parameter | Work Done |
            | --- | --- | --- |
            | Isothermal | Temperature | nRT ln(V2/V1) |
            | Adiabatic | Entropy / Heat | -Delta U |
        """.trimIndent()

        val blocks = StudyNoteParser.parseBlocks(raw)
        assertTrue(blocks.any { it is StudyNoteBlock.Heading && it.text == "Thermodynamics Core" })

        val multiCard = blocks.filterIsInstance<StudyNoteBlock.MultiLinerCard>().firstOrNull()
        assertTrue("Expected MultiLinerCard for First Law", multiCard != null)
        assertEquals("First Law", multiCard?.title)
        assertTrue(multiCard?.formula?.contains("\\Delta U = Q - W") == true)
        assertTrue(multiCard?.nestedBullets?.any { it.second.contains("Work done by system") } == true)

        val oneLiner = blocks.filterIsInstance<StudyNoteBlock.OneLiner>().firstOrNull()
        assertEquals("Isochoric Process", oneLiner?.leadTerm)

        val callout = blocks.filterIsInstance<StudyNoteBlock.Callout>().firstOrNull()
        assertEquals("Exam Trap", callout?.label)

        val table = blocks.filterIsInstance<StudyNoteBlock.Table>().firstOrNull()
        assertEquals(listOf("Process", "Constant Parameter", "Work Done"), table?.headers)
        assertEquals(2, table?.rows?.size)
    }

    @Test
    fun `fix backwards ChatGPT normalizes trailing concept and strips chatter`() {
        val raw = """
            Sure! Here are your revision notes:
            ```markdown
            1. Emergency provisions empowering federal intervention during state constitutional breakdown -> **Article 356**
            2. Minimum age in years required to contest Presidential elections in India -> **35 Years**
            ```
            Let me know if you need more!
        """.trimIndent()

        val transformed = StudyNoteParser.applyTransform(raw, KnowledgeTransformAction.FIX_BACKWARDS_CHATGPT)
        assertTrue(!transformed.contains("Sure!"))
        assertTrue(!transformed.contains("Let me know"))
        assertTrue(transformed.contains("1. **Article 356** -> Emergency provisions"))
        assertTrue(transformed.contains("2. **35 Years** -> Minimum age"))
    }

    @Test
    fun `swap lead and detail reverses term-explanation pairs cleanly`() {
        val raw = "1. **Mitochondria** -> Powerhouse generating cellular ATP"
        val swapped = StudyNoteParser.applyTransform(raw, KnowledgeTransformAction.SWAP_LEAD_DETAIL)
        assertEquals("1. **Powerhouse generating cellular ATP** -> Mitochondria", swapped)
    }

    @Test
    fun `compilePrompt embeds custom collection label, layer order, and custom instruction`() {
        val rule = KnowledgeFormatRule(
            collectionType = KnowledgeCollectionType.MULTI_LINER,
            customCollectionLabel = "High-Yield Codex",
            layerOrder = KnowledgeLayerOrder.QA_SEPARATE_LINES,
            depth = KnowledgeDepth.DEEP,
            language = "English",
            preserveNestedBullets = true,
            includeLatexMath = true,
            includeCallouts = true,
            includeComparisonTable = true,
            customInstruction = "Include 2 clinical edge cases per topic"
        )

        val prompt = StudyNoteParser.compilePrompt("Renal Physiology", KnowledgeCollectionType.MULTI_LINER, rule)
        assertTrue(prompt.contains("High-Yield Codex"))
        assertTrue(prompt.contains("Renal Physiology"))
        assertTrue(prompt.contains("Q: <concept question>"))
        assertTrue(prompt.contains("Markdown comparison table"))
        assertTrue(prompt.contains("Include 2 clinical edge cases per topic"))
    }

    @Test
    fun `weak area compilation and note-to-mcq conversion work end to end`() {
        val paper = QuestionPaper(
            metadata = PaperMetadata(title = "Polity Mock", subject = "Indian Polity"),
            questions = listOf(
                McqQuestion(
                    num = 1,
                    question = "Which article deals with Right to Equality?",
                    options = QuestionOptions("Article 14", "Article 19", "Article 21", "Article 32"),
                    correctAnswer = "A",
                    explanation = "Article 14 guarantees equality before law."
                ),
                McqQuestion(
                    num = 2,
                    question = "Which schedule lists languages?",
                    options = QuestionOptions("7th", "8th", "9th", "10th"),
                    correctAnswer = "B",
                    explanation = "Eighth Schedule recognizes 22 languages."
                )
            )
        )

        val weakNoteText = StudyNoteParser.buildWeakAreaNoteContent(
            paper = paper,
            incorrectNums = setOf(1),
            unattendedNums = setOf(2),
            answers = mapOf(1 to "C")
        )
        assertTrue(weakNoteText.contains("Q1: Which article deals with Right to Equality?"))
        assertTrue(weakNoteText.contains("(A) Article 14"))
        assertTrue(weakNoteText.contains("Q2: Which schedule lists languages?"))

        val noteEntity = StudyNoteEntity(
            title = "Polity Capsule",
            subject = "Polity",
            rawContent = """
                1. **Article 14** -> Equality before law and equal protection of laws
                2. **Article 21** -> Protection of life and personal liberty
                3. **Article 32** -> Right to constitutional remedies
                4. **8th Schedule** -> Recognizes official languages of India
            """.trimIndent()
        )
        val mcqDraft = StudyNoteParser.convertNoteBlocksToMcqDraft(noteEntity)
        val parsedPaper = McqParser.parse(mcqDraft)
        assertEquals(4, parsedPaper.questions.size)
        assertEquals("A", parsedPaper.questions.first().correctAnswer)
    }

    @Test
    fun `parseBlocks keeps full Q1 stem options and metadata visible for Active Recall`() {
        val paper = QuestionPaper(
            metadata = PaperMetadata(title = "Civil Core", subject = "Civil Engineering"),
            questions = listOf(
                McqQuestion(
                    num = 1,
                    question = "A simply supported beam of span L carries a uniformly distributed load w over the entire span. What is the bending moment ratio?",
                    options = QuestionOptions("1", "1/2", "2", "4"),
                    correctAnswer = "C",
                    explanation = "Point load at mid-span gives WL/4 vs wL^2/8."
                )
            )
        )

        val generated = StudyNoteParser.buildWeakAreaNoteContent(
            paper = paper,
            incorrectNums = emptySet(),
            unattendedNums = setOf(1),
            answers = emptyMap()
        )
        val parsedGenerated = StudyNoteParser.parseBlocks(generated)
        val generatedCard = parsedGenerated.filterIsInstance<StudyNoteBlock.MultiLinerCard>().firstOrNull()
        assertTrue(generatedCard != null)
        assertEquals("Q1", generatedCard?.displayIndex)
        assertTrue(generatedCard?.isQuestionCard == true)
        assertTrue(generatedCard?.title?.contains("simply supported beam of span L") == true)
        assertEquals("Skipped", generatedCard?.statusTag)
        assertEquals(4, generatedCard?.optionsList?.size)
        assertTrue(generatedCard?.correctAnswer?.contains("(C) 2") == true)
        assertTrue(generatedCard?.keyTakeaway?.contains("WL/4") == true)

        // Legacy weak-area markdown without Question Type or Options still retains full stem in title
        val legacyRaw = """
            1. **Q1: A simply supported beam of span L carries a UDL w over the entire span:**
              - Status: Skipped
              - Correct Answer: (C) 2
              - Key Takeaway: Mid-span point load induces twice the bending moment.
        """.trimIndent()
        val parsedLegacy = StudyNoteParser.parseBlocks(legacyRaw)
        val legacyCard = parsedLegacy.filterIsInstance<StudyNoteBlock.MultiLinerCard>().firstOrNull()
        assertTrue(legacyCard != null)
        assertEquals("Q1", legacyCard?.displayIndex)
        assertTrue(legacyCard?.isQuestionCard == true)
        assertTrue(legacyCard?.title?.contains("simply supported beam of span L") == true)
        assertEquals("Skipped", legacyCard?.statusTag)
        assertTrue(legacyCard?.correctAnswer?.contains("(C) 2") == true)
    }

    @Test
    fun `buildWeakAreaNoteContent formats candidate roll, booklet and attempt identification uniquely`() {
        val paper = QuestionPaper(
            metadata = PaperMetadata(
                title = "Thermodynamics Quiz",
                subject = "Mechanical Engineering",
                rollNumber = "2026100123",
                bookletNumber = "0005432",
                setNumber = "Set B"
            ),
            questions = listOf(
                McqQuestion(
                    num = 1,
                    question = "What is the efficiency of a Carnot engine operating between 300K and 600K?",
                    options = QuestionOptions("25%", "50%", "75%", "100%"),
                    correctAnswer = "B",
                    explanation = "Carnot efficiency = 1 - (Tc/Th) = 1 - 300/600 = 0.50 (50%)."
                )
            )
        )

        val noteText = StudyNoteParser.buildWeakAreaNoteContent(
            paper = paper,
            incorrectNums = setOf(1),
            unattendedNums = emptySet(),
            answers = mapOf(1 to "C"),
            rollNumber = "2026100123",
            bookletNumber = "0005432",
            bookletCode = "Set B",
            attemptId = 42L,
            attemptTimestamp = 1700000000000L,
            score = 0.0,
            totalMarks = 4.0
        )

        assertTrue(noteText.contains("Roll No: `2026100123`"))
        assertTrue(noteText.contains("Booklet No: `0005432`"))
        assertTrue(noteText.contains("Series: `Set B`"))
        assertTrue(noteText.contains("Attempt #42"))
        assertTrue(noteText.contains("Score: 0.00 / 4.00"))
        assertTrue(noteText.contains("Candidate Marked: C"))
        assertTrue(noteText.contains("(B) 50%"))
    }

    @Test
    fun `compileSubjectActiveRecallSet aggregates from multiple papers and notes with smart deduplication`() {
        val paper1 = QuestionPaper(
            metadata = PaperMetadata(title = "Polity Test 1", subject = "Indian Polity"),
            questions = listOf(
                McqQuestion(
                    num = 1,
                    question = "Which article guarantees Right to Constitutional Remedies?",
                    options = QuestionOptions("Article 19", "Article 21", "Article 32", "Article 226"),
                    correctAnswer = "C",
                    explanation = "Dr. Ambedkar called Article 32 the heart and soul of the Constitution."
                )
            )
        )
        val paper2 = QuestionPaper(
            metadata = PaperMetadata(title = "Polity Test 2", subject = "Indian Polity"),
            questions = listOf(
                // Identical stem to verify deduplication
                McqQuestion(
                    num = 1,
                    question = "Which article guarantees Right to Constitutional Remedies?",
                    options = QuestionOptions("Article 19", "Article 21", "Article 32", "Article 226"),
                    correctAnswer = "C",
                    explanation = "Article 32 provides writs."
                ),
                // Unique question
                McqQuestion(
                    num = 2,
                    question = "Which schedule contains the Union, State, and Concurrent Lists?",
                    options = QuestionOptions("5th", "6th", "7th", "8th"),
                    correctAnswer = "C",
                    explanation = "The Seventh Schedule delineates the distribution of legislative powers."
                )
            )
        )
        val existingNote = StudyNoteEntity(
            title = "Polity Key Facts",
            subject = "Indian Polity",
            rawContent = """
                1. **Article 21** -> Protection of life and personal liberty
            """.trimIndent()
        )

        val compiled = StudyNoteParser.compileSubjectActiveRecallSet(
            subjectName = "Indian Polity",
            papers = listOf(paper1, paper2),
            existingNotes = listOf(existingNote)
        )

        // Verifies master heading
        assertTrue(compiled.contains("## Master Active Recall: Indian Polity"))
        // Verifies cards included
        assertTrue(compiled.contains("Right to Constitutional Remedies"))
        assertTrue(compiled.contains("Seventh Schedule"))
        assertTrue(compiled.contains("Article 21"))
        // Verifies deduplication: only 1 instance of Article 32 / Constitutional remedies card
        val occurrences = compiled.split("Right to Constitutional Remedies").size - 1
        assertEquals(1, occurrences)
    }

    @Test
    fun `compilePrompt supports exam target, subject, item count, bilingual terms, pyq tags, and sample templates`() {
        val rule = KnowledgeFormatRule(
            collectionType = KnowledgeCollectionType.ONE_LINERS,
            layerOrder = KnowledgeLayerOrder.TERM_FIRST,
            depth = KnowledgeDepth.CRISP,
            language = "Hindi"
        )
        val prompt = StudyNoteParser.compilePrompt(
            topic = "Fundamental Rights",
            collectionType = KnowledgeCollectionType.ONE_LINERS,
            rule = rule,
            examTarget = "UPSC Civil Services",
            subject = "Polity & Governance",
            itemCount = 25,
            includeBilingualTerms = true,
            includePyqTags = true
        )
        assertTrue(prompt.contains("Subject Domain: Polity & Governance"))
        assertTrue(prompt.contains("Target Examination: UPSC Civil Services"))
        assertTrue(prompt.contains("Output Language: Hindi"))
        assertTrue(prompt.contains("Generate exactly 25 ultra-dense exam one-liners"))
        assertTrue(prompt.contains("inside parentheses alongside Hindi explanations"))
        assertTrue(prompt.contains("[PYQ Focus]"))

        val sample = StudyNoteParser.buildSampleTemplate(
            collectionType = KnowledgeCollectionType.FORMULA_SHEET,
            layerOrder = KnowledgeLayerOrder.TERM_FIRST
        )
        assertTrue(sample.contains("First Law of Thermodynamics"))
        assertTrue(sample.contains("Formula:"))
    }

    @Test
    fun `compilePrompt resolves mode conflicts, enforces strict LaTeX & PYQ placement, and embeds 1-shot example`() {
        // 1. ONE_LINERS even with BALANCED + preserveNestedBullets=true enforces single-line with no sub-bullets
        val oneLinerPrompt = StudyNoteParser.compilePrompt(
            topic = "Constitutional Amendments",
            collectionType = KnowledgeCollectionType.ONE_LINERS,
            rule = KnowledgeFormatRule(
                collectionType = KnowledgeCollectionType.ONE_LINERS,
                depth = KnowledgeDepth.BALANCED,
                layerOrder = KnowledgeLayerOrder.AUTO_DETECT,
                preserveNestedBullets = true,
                includeLatexMath = false
            ),
            itemCount = 30
        )
        assertTrue(oneLinerPrompt.contains("NO nested sub-bullets"))
        assertTrue(oneLinerPrompt.contains("Completion Parity"))
        assertTrue(oneLinerPrompt.contains("EXACT OUTPUT EXAMPLE (FOLLOW THIS SYNTAX LINE-FOR-LINE):"))
        assertTrue(oneLinerPrompt.contains("Article 32"))

        // 2. COMPARISON_TABLE >= 25 rows splits across sub-tables, skips redundant table rule, forbids raw pipes
        val tablePrompt = StudyNoteParser.compilePrompt(
            topic = "Mitosis vs Meiosis",
            collectionType = KnowledgeCollectionType.COMPARISON_TABLE,
            rule = KnowledgeFormatRule(
                collectionType = KnowledgeCollectionType.COMPARISON_TABLE,
                layerOrder = KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST,
                includeComparisonTable = true,
                preserveNestedBullets = true
            ),
            itemCount = 40,
            includePyqTags = true
        )
        assertTrue(tablePrompt.contains("split cleanly across 2–5 focused Markdown tables"))
        assertTrue(tablePrompt.contains("NEVER insert nested bullets or raw unescaped `|`"))
        assertTrue(!tablePrompt.contains("Include at least one Markdown comparison table"))
        assertTrue(tablePrompt.contains("| Parameter | Isothermal | Adiabatic |"))

        // 3. BACKWARDS_ANSWER_FIRST + PYQ puts marker before -> (never after trailing **Concept**)
        val backwardsPrompt = StudyNoteParser.compilePrompt(
            topic = "Optics Laws",
            collectionType = KnowledgeCollectionType.MULTI_LINER,
            rule = KnowledgeFormatRule(
                collectionType = KnowledgeCollectionType.MULTI_LINER,
                layerOrder = KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST,
                includeLatexMath = true
            ),
            itemCount = 15,
            includePyqTags = true
        )
        assertTrue(backwardsPrompt.contains("BEFORE `->` or on a sub-bullet—NEVER after trailing `**Concept**`"))
        assertTrue(backwardsPrompt.contains("NEVER use `\$\$...\$\$`"))
        assertTrue(backwardsPrompt.contains("[PYQ Focus] -> **Lenz's Law**"))

        // 4. FORMULA_SHEET pluralization & WEAK_AREA_REVIEW structure
        val formulaPrompt = StudyNoteParser.compilePrompt(
            topic = "Thermodynamics",
            collectionType = KnowledgeCollectionType.FORMULA_SHEET,
            rule = KnowledgeFormatRule(collectionType = KnowledgeCollectionType.FORMULA_SHEET),
            itemCount = 20
        )
        assertTrue(formulaPrompt.contains("20 important laws/formulas"))

        val weakSample = StudyNoteParser.buildSampleTemplate(
            collectionType = KnowledgeCollectionType.WEAK_AREA_REVIEW,
            layerOrder = KnowledgeLayerOrder.TERM_FIRST,
            includeLatexMath = false,
            includeCallouts = false,
            includePyqTags = true
        )
        assertTrue(weakSample.contains("- Trap:"))
        assertTrue(!weakSample.contains("> Exam Tip:"))
        assertTrue(weakSample.contains("[PYQ Focus]"))
    }
}


