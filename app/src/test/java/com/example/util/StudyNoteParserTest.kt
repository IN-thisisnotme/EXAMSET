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

    @Test
    fun `parseBlocks keeps multi-paragraph UPSC Mains answers and display LaTeX inside a single MultiLinerCard`() {
        val raw = """
            ## Indian Economy & Fiscal Policy
            Q1: Examine the role of fiscal consolidation under the FRBM framework in sustaining high capital expenditure.
            A: **Introduction:** Fiscal consolidation anchors macroeconomic stability by moderating sovereign debt-to-GDP ratios.

            **Multi-Dimensional Analysis:**
            When inflation (${'$'}\pi${'$'}) rises and ${'$'}r > g${'$'} widens, debt sustainability weakens rapidly.
              - **Capital Multiplier:** Public CapEx crowds in private investment over medium horizons.
              - **Escape Clause:** Allows controlled deviation during structural shocks.

            ${'$'}${'$'}
            \frac{\Delta b}{b} = (r - g) - \frac{pb}{b}
            ${'$'}${'$'}

            **Way Forward:** Pair transparent medium-term debt targets with outcome-budgeted infrastructure spending.

            Q2: What is the significance of Cooperative Federalism in GST governance?
            A: The GST Council institutionalizes shared fiscal sovereignty between the Union and States.
        """.trimIndent()

        val rule = KnowledgeFormatRule(collectionType = KnowledgeCollectionType.LONG_ANSWER)
        val blocks = StudyNoteParser.parseBlocks(raw, rule)
        val cards = blocks.filterIsInstance<StudyNoteBlock.MultiLinerCard>()

        assertEquals(2, cards.size)
        val q1 = cards[0]
        assertTrue(q1.title.contains("FRBM framework"))
        assertEquals("Long Q&A", q1.badge)
        assertTrue("Expected Introduction paragraph", q1.paragraphs.any { it.contains("Introduction:") })
        assertTrue("Expected prose with inline LaTeX kept in paragraphs", q1.paragraphs.any { it.contains("\\pi") && it.contains("r > g") })
        assertTrue("Expected Way Forward paragraph in same card", q1.paragraphs.any { it.contains("Way Forward:") })
        assertTrue("Expected nested bullets preserved", q1.nestedBullets.size == 2)
        assertTrue("Expected display LaTeX preserved in formula", q1.formula.contains("\\frac{\\Delta b}{b}"))

        val q2 = cards[1]
        assertTrue(q2.title.contains("Cooperative Federalism"))
        assertTrue(q2.paragraphs.any { it.contains("GST Council") })
    }

    @Test
    fun `parseBlocks groups numbered LONG_ANSWER multi-paragraph sections and generates rich LaTeX HTML`() {
        val raw = """
            1. **Discuss the doctrine of Basic Structure** -> Evolved in Kesavananda Bharati (1973) to safeguard constitutional identity.

              - Introduction: Limits Parliament's amending power under Article 368.
              - Explanation: Judicial review remains an inviolable pillar.
              - Explanation: Subsequent rulings like Minerva Mills reinforced harmony between FRs and DPSPs.

            The constitutional balance ensures ${'$'}\text{Part III} \leftrightarrow \text{Part IV}${'$'} without legislative absolutism.
        """.trimIndent()

        val rule = KnowledgeFormatRule(collectionType = KnowledgeCollectionType.LONG_ANSWER)
        val blocks = StudyNoteParser.parseBlocks(raw, rule)
        val cards = blocks.filterIsInstance<StudyNoteBlock.MultiLinerCard>()
        assertEquals(1, cards.size)
        val card = cards.first()
        assertEquals("Long-Form Q&A", card.questionType)
        assertTrue(card.keyTakeaway.contains("Minerva Mills"))
        assertTrue(card.keyTakeaway.contains("Judicial review"))
        assertTrue(card.paragraphs.any { it.contains("constitutional balance") })

        val prompt = StudyNoteParser.compilePrompt(
            topic = "Centre-State Relations",
            collectionType = KnowledgeCollectionType.LONG_ANSWER,
            rule = rule.copy(depth = KnowledgeDepth.BALANCED),
            examTarget = "UPSC Civil Services",
            itemCount = 3
        )
        assertTrue(prompt.contains("Generate exactly 3 full-length subjective / long-form analytical Q&A cards"))
        assertTrue(prompt.contains("~250-word examination-grade model answers"))
        assertTrue(prompt.contains("Target Examination: UPSC Civil Services"))
        assertTrue(prompt.contains("\$\$...\$\$"))

        val htmlSource = """
            Q1: **Discuss the doctrine of Basic Structure**
            A: **Constitutional Identity:** Evolved in Kesavananda Bharati (1973) to safeguard core federalism.

            The constitutional balance ensures ${'$'}\text{Part III} \leftrightarrow \text{Part IV}${'$'} without legislative absolutism.

            ${'$'}${'$'}
            \text{Basic Structure} = \text{Supremacy of Constitution}
            ${'$'}${'$'}
        """.trimIndent()
        val note = StudyNoteEntity(
            title = "UPSC Polity Mains",
            subject = "Polity",
            collectionType = KnowledgeCollectionType.LONG_ANSWER.code,
            rawContent = htmlSource
        )
        val html = StudyNoteParser.generatePrintableHtml(note)
        assertTrue(html.contains("Long-Form / Subjective Q&amp;A"))
        assertTrue(html.contains("<strong>"))
        assertTrue(html.contains("Part III ↔ Part IV"))
        assertTrue(html.contains("<div class='formula'>"))
    }

    @Test
    fun `buildSampleTemplate avoids contradicting layerOrder across formula sheet and long answer modes`() {
        val backwardsFormula = KnowledgeFormatRule(
            collectionType = KnowledgeCollectionType.FORMULA_SHEET,
            layerOrder = KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST,
            includeLatexMath = true
        )
        val backwardsPrompt = StudyNoteParser.compilePrompt(
            topic = "Gravitation",
            collectionType = KnowledgeCollectionType.FORMULA_SHEET,
            rule = backwardsFormula,
            itemCount = 5
        )
        assertTrue(backwardsPrompt.contains("-> **First Law of Thermodynamics**"))

        val qaLong = KnowledgeFormatRule(
            collectionType = KnowledgeCollectionType.LONG_ANSWER,
            layerOrder = KnowledgeLayerOrder.QA_SEPARATE_LINES,
            includeLatexMath = true
        )
        val qaPrompt = StudyNoteParser.compilePrompt(
            topic = "Fiscal Federalism",
            collectionType = KnowledgeCollectionType.LONG_ANSWER,
            rule = qaLong,
            itemCount = 3
        )
        assertTrue(qaPrompt.contains("Q1:"))
        assertTrue(qaPrompt.contains("A:"))

        val termLong = qaLong.copy(layerOrder = KnowledgeLayerOrder.TERM_FIRST)
        val termPrompt = StudyNoteParser.compilePrompt(
            topic = "Fiscal Federalism",
            collectionType = KnowledgeCollectionType.LONG_ANSWER,
            rule = termLong,
            itemCount = 3
        )
        assertTrue(termPrompt.contains("1. **Examine the significance"))
    }

    @Test
    fun `compileFlashcardPrompt and buildFlashcardSampleTemplate parse cleanly across all 4 styles`() {
        com.example.model.FlashcardPromptStyle.entries.forEach { style ->
            val prompt = StudyNoteParser.compileFlashcardPrompt(
                topic = "Chemical Equilibrium",
                subject = "Chemistry",
                examTarget = "JEE",
                itemCount = 15,
                style = style,
                depth = KnowledgeDepth.BALANCED,
                includeLatexMath = true,
                includeMnemonics = true,
                includeExamples = true,
                includeExamTraps = true
            )
            assertTrue("Prompt should request 15 cards for $style", prompt.contains("exactly **15 cards**"))
            assertTrue("Prompt should include sample block for $style", prompt.contains("EXACT OUTPUT EXAMPLE"))

            val sample = StudyNoteParser.buildFlashcardSampleTemplate(
                style = style,
                includeLatexMath = true,
                includeMnemonics = true,
                includeExamples = true,
                includeExamTraps = true
            )
            val flashcardRule = KnowledgeFormatRule(
                collectionType = KnowledgeCollectionType.FLASHCARDS,
                layerOrder = if (style == com.example.model.FlashcardPromptStyle.CLOZE_SCENARIO) {
                    KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST
                } else {
                    KnowledgeLayerOrder.AUTO_DETECT
                }
            )
            val blocks = StudyNoteParser.parseBlocks(sample, flashcardRule)
            val studyCards = blocks.filter { it is StudyNoteBlock.OneLiner || it is StudyNoteBlock.MultiLinerCard }
            assertTrue("Expected >= 2 parsed cards for $style, got ${studyCards.size}", studyCards.size >= 2)
        }
    }

    @Test
    fun `parseBlocks strips unmatched bold asterisks from Callout label and body`() {
        val raw = """
            ## Weak-Area Remediation: Civil Core
            > **Review Identification:** Roll No: `1234` • Series: `Set A`
            > **Diagnostic Summary:** 0 Incorrect • 2 Skipped
        """.trimIndent()
        val callout = StudyNoteParser.parseBlocks(raw)
            .filterIsInstance<StudyNoteBlock.Callout>()
            .firstOrNull()
        assertEquals("Review Identification", callout?.label)
        assertTrue(callout?.body?.startsWith("Roll No:") == true)
        assertTrue(callout?.body?.startsWith("*") != true)
    }

    @Test
    fun `parseBlocks keeps ChatGPT Q1-Q5 long-form answers with tables, callouts, numbered sub-lists and escaped markers intact`() {
        val raw = """
            ### Disaster Management: Concepts, Framework and Governance

            Q1: Define disaster management and critically examine its objectives. \[High-Yield\]

            A: Introduction: Disaster management refers to systematic risk reduction.

            Core Analytical Dimensions:

            \- Objectives of Disaster Management:

            * Protect human life and livelihoods.
            * Strengthen community preparedness.

            \- Traditional versus Modern Approach:

            | Parameter | Traditional Approach | Modern Approach |
            | --- | --- | --- |
            | Main focus | Post-disaster relief | Comprehensive risk management |
            | Orientation | Reactive | Proactive |

            \- Principles of Effective Disaster Management:

            * Multi-hazard approach: Address natural and technological hazards.
            * Community participation: Involve local citizens.

            Way Forward: Strengthen local disaster governance.

            > Exam Tip: Remember Prevention -> Mitigation -> Preparedness -> Response -> Recovery.

            ### Constitutional, Legal and Institutional Framework in India

            Q2: Examine the institutional framework of disaster management in India. \[PYQ Focus\]

            A: Introduction: The Disaster Management Act, 2005 provides the statutory basis.

            \- Major Institutional Mechanisms:

            1\. National Disaster Management Authority (NDMA):

            * Chaired by the Prime Minister.

            2\. State Disaster Management Authority (SDMA):

            * Chaired by the Chief Minister.

            | Institution | Leadership | Primary Responsibility |
            | --- | --- | --- |
            | NDMA | Prime Minister | National policy |
            | SDMA | Chief Minister | State planning |

            \- Critical Evaluation:

            * Overlapping departmental responsibilities hinder coordination.

            Way Forward: Strengthen local institutions.

            > Exam Tip: Memorise Section 3 (NDMA) and Section 14 (SDMA).
        """.trimIndent()

        val rule = KnowledgeFormatRule(collectionType = KnowledgeCollectionType.LONG_ANSWER)
        val blocks = StudyNoteParser.parseBlocks(raw, rule)
        val headings = blocks.filterIsInstance<StudyNoteBlock.Heading>()
        val cards = blocks.filterIsInstance<StudyNoteBlock.MultiLinerCard>()
        val topLevelOneLiners = blocks.filterIsInstance<StudyNoteBlock.OneLiner>()
        val topLevelTables = blocks.filterIsInstance<StudyNoteBlock.Table>()
        val topLevelCallouts = blocks.filterIsInstance<StudyNoteBlock.Callout>()

        assertEquals(2, headings.size)
        assertEquals(2, cards.size)
        assertEquals(0, topLevelOneLiners.size)
        assertEquals(0, topLevelTables.size)
        assertEquals(0, topLevelCallouts.size)
        assertEquals(2, StudyNoteParser.countOneLiners(raw, rule))

        val q1 = cards[0]
        assertTrue("Expected unescaped [High-Yield] tag, got: ${q1.title}", q1.title.contains("[High-Yield]") && !q1.title.contains("\\["))
        assertTrue(q1.richSections.any { it is StudyNoteCardSection.Table && it.headers == listOf("Parameter", "Traditional Approach", "Modern Approach") })
        assertTrue(q1.richSections.any { it is StudyNoteCardSection.Callout && it.label == "Exam Tip" })
        assertTrue(q1.richSections.any { it is StudyNoteCardSection.Bullet && it.text.contains("Multi-hazard approach") })
        assertTrue(q1.richSections.any { it is StudyNoteCardSection.Paragraph && it.text.startsWith("Way Forward:") })

        // Order check inside Q1: table appears before Principles bullet, which appears before Way Forward and Exam Tip
        val tableIdx = q1.richSections.indexOfFirst { it is StudyNoteCardSection.Table }
        val principleIdx = q1.richSections.indexOfFirst { it is StudyNoteCardSection.Bullet && it.text.contains("Multi-hazard") }
        val wayForwardIdx = q1.richSections.indexOfFirst { it is StudyNoteCardSection.Paragraph && it.text.startsWith("Way Forward:") }
        val tipIdx = q1.richSections.indexOfFirst { it is StudyNoteCardSection.Callout && it.label == "Exam Tip" }
        assertTrue(tableIdx in 1 until principleIdx)
        assertTrue(principleIdx < wayForwardIdx)
        assertTrue(wayForwardIdx < tipIdx)

        val q2 = cards[1]
        assertTrue(q2.title.contains("[PYQ Focus]"))
        assertTrue(q2.richSections.any { it is StudyNoteCardSection.Paragraph && it.text.startsWith("1. National Disaster Management Authority") })
        assertTrue(q2.richSections.any { it is StudyNoteCardSection.Bullet && it.text.contains("Overlapping departmental") })
        assertTrue(q2.richSections.any { it is StudyNoteCardSection.Table && it.rows.size == 2 })

        val note = StudyNoteEntity(
            title = "Disaster Management",
            subject = "GS3",
            collectionType = KnowledgeCollectionType.LONG_ANSWER.code,
            rawContent = raw
        )
        val html = StudyNoteParser.generatePrintableHtml(note)
        assertTrue("Expected embedded table rendered in printable HTML", html.contains("<table>") && html.contains("Comprehensive risk management"))
        assertTrue("Expected embedded Exam Tip callout rendered in printable HTML", html.contains("Exam Tip:") && html.contains("Prevention"))
    }

    @Test
    fun `parseBlocks parses 5-question ChatGPT Disaster Management structure into exactly 5 cards`() {
        val raw = """
            ### Disaster Management: Concepts, Framework and Governance
            Q1: Define disaster management. \[High-Yield\]
            A: Introduction: Systematic risk reduction.
            \- Objectives:
            * Protect human life.
            | Parameter | Traditional | Modern |
            | --- | --- | --- |
            | Focus | Relief | Risk management |
            Way Forward: Strengthen local governance.
            > Exam Tip: Prevention -> Mitigation -> Preparedness.

            ### Disaster Risk, Vulnerability and Hazard Assessment
            Q2: Explain hazard, vulnerability and risk. \[PYQ Focus\]
            A: Introduction: Risk arises from hazards and vulnerability.
            \- Disaster Risk Relationship:
            Disaster Risk ∝ Hazard × Exposure × Vulnerability ÷ Capacity
            > Exam Tip: Do not confuse hazard with disaster.

            ### Constitutional, Legal and Institutional Framework in India
            Q3: Examine the Disaster Management Act, 2005. \[High-Yield\] \[PYQ Focus\]
            A: Introduction: Statutory framework for disaster risk reduction.
            1\. NDMA:
            * Chaired by PM.
            2\. SDMA:
            * Chaired by CM.
            | Institution | Leadership | Responsibility |
            | --- | --- | --- |
            | NDMA | PM | National policy |

            ### Disaster Preparedness, Mitigation and Emergency Response
            Q4: Discuss preparedness and early warning. \[High-Yield\] \[PYQ Focus\]
            A: Introduction: Preparedness reduces mortality.
            \- Structural mitigation:
            * Earthquake-resistant buildings.
            Early-Warning Process:
            Hazard Monitoring → Forecasting → Warning Dissemination
            | Component | Activities | Outcome |
            | --- | --- | --- |
            | Search | Evacuation | Saving lives |
            > Exam Tip: Distinguish mitigation from preparedness.

            ### Post-Disaster Recovery, Climate Change and Sustainable Development
            Q5: Examine climate change and Sendai Framework. \[High-Yield\] \[PYQ Focus\]
            A: Introduction: Disasters and climate change interact.
            \- Impact on Development:
            Economic Dimension:
            * Destruction of infrastructure.
            1\. Understanding Disaster Risk:
            * Hazard mapping.
            2\. Strengthening Governance:
            * Institutional accountability.
            3\. Investing in Resilience:
            * Early warning.
            4\. Enhancing Preparedness:
            * Build Back Better.
            | Framework | Year | Objective |
            | --- | --- | --- |
            | Sendai | 2015–2030 | Reducing risk |
            > Exam Tip: Remember U-G-I-P.
        """.trimIndent()

        val rule = KnowledgeFormatRule(collectionType = KnowledgeCollectionType.LONG_ANSWER)
        val blocks = StudyNoteParser.parseBlocks(raw, rule)
        val cards = blocks.filterIsInstance<StudyNoteBlock.MultiLinerCard>()

        assertEquals(5, cards.size)
        assertEquals(5, StudyNoteParser.countOneLiners(raw, rule))
        assertEquals(0, blocks.filterIsInstance<StudyNoteBlock.OneLiner>().size)
        assertEquals(0, blocks.filterIsInstance<StudyNoteBlock.Table>().size)
        assertEquals(0, blocks.filterIsInstance<StudyNoteBlock.Callout>().size)

        assertTrue(cards[2].title.contains("[High-Yield] [PYQ Focus]") && !cards[2].title.contains("\\"))
        assertTrue(cards[1].richSections.any { it is StudyNoteCardSection.Paragraph && it.text.contains("Disaster Risk ∝ Hazard") })
        assertTrue(cards[3].richSections.any { it is StudyNoteCardSection.Paragraph && it.text.contains("Hazard Monitoring → Forecasting") })
        assertTrue(cards[4].richSections.any { it is StudyNoteCardSection.Paragraph && it.text.startsWith("4. Enhancing Preparedness:") })
    }
}

