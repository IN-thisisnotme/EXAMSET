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
}
