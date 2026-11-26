package com.example.util

import com.example.data.PaperEntity
import com.example.data.StudyNoteEntity
import com.example.model.FolderItem
import com.example.model.VoiceFillerCategory
import com.example.model.VoicePracticeMode
import com.example.model.VoiceSourceFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFluencyEngineTest {

    private val folders = listOf(
        FolderItem(id = "f-parent", name = "Polity", color = "#0F766E", parentId = null),
        FolderItem(id = "f-child", name = "Parliament", color = "#0F766E", parentId = "f-parent"),
        FolderItem(id = "f-subchild", name = "Money Bills", color = "#0F766E", parentId = "f-child"),
        FolderItem(id = "f-other", name = "Physics", color = "#1D4ED8", parentId = null)
    )

    @Test
    fun collectDescendantFolderIds_includesParentChildAndSubChild() {
        val ids = VoiceFluencyEngine.collectDescendantFolderIds("f-parent", folders)
        assertEquals(setOf("f-parent", "f-child", "f-subchild"), ids)
    }

    @Test
    fun collectDescendantFolderIds_narrowingToChildExcludesParent() {
        val ids = VoiceFluencyEngine.collectDescendantFolderIds("f-child", folders)
        assertEquals(setOf("f-child", "f-subchild"), ids)
        assertFalse("f-parent" in ids)
    }

    @Test
    fun sanitizeNoFormulas_stripsLatexAndFormulaLines() {
        val input = """
            Newton's second law connects net force to acceleration of a body.
            Formula: F = m * a
            The rate of change of momentum is proportional to applied force: ${'$'}F = \frac{dp}{dt}${'$'}.
            CO2 + H2O -> H2CO3
        """.trimIndent()

        val cleaned = VoiceFluencyEngine.sanitizeNoFormulas(input)
        assertTrue(cleaned.contains("Newton's second law"))
        assertTrue(cleaned.contains("rate of change of momentum"))
        assertFalse(cleaned.contains("Formula:"))
        assertFalse(cleaned.contains("\\frac"))
        assertFalse(cleaned.contains("CO2 + H2O"))
    }

    @Test
    fun buildPromptsForFolder_pullsFromParentAndChildrenAndExcludesOtherFolders() {
        val notes = listOf(
            StudyNoteEntity(
                id = 1L,
                folderId = "f-subchild",
                title = "Article 110",
                subject = "Polity > Parliament > Money Bills",
                rawContent = "1. **Money Bill** -> A bill certified by the Speaker dealing exclusively with taxes and public expenditure."
            ),
            StudyNoteEntity(
                id = 2L,
                folderId = "f-other",
                title = "Kinematics",
                subject = "Physics",
                rawContent = "1. **Velocity Formula** -> ${'$'}v = u + at${'$'}"
            )
        )
        val papers = listOf(
            PaperEntity(
                id = 10L,
                folderId = "f-child",
                title = "Parliament Quiz",
                subject = "Polity > Parliament",
                questionCount = 1,
                answerKeyCount = 1,
                explanationCount = 1,
                rawText = """
                    Q1. Who certifies whether a bill is a Money Bill in India?
                    A) Prime Minister
                    B) Speaker of the Lok Sabha
                    C) President
                    D) Finance Minister
                    Answer: B
                    Explanation: Under Article 110, the Speaker of the Lok Sabha holds final authority to certify a Money Bill.
                """.trimIndent()
            )
        )

        val result = VoiceFluencyEngine.buildPromptsForFolder(
            selectedFolderId = "f-parent",
            folders = folders,
            studyNotes = notes,
            papers = papers,
            sourceFilter = VoiceSourceFilter.ALL
        )

        assertEquals(2, result.prompts.size)
        assertTrue(result.prompts.any { it.title == "Money Bill" })
        assertTrue(result.prompts.any { it.promptQuestion.contains("Speaker") || it.promptQuestion.contains("Money Bill") })
    }

    @Test
    fun extractFillersAndHighlight_detectsEmUmUhAndLexicalFillers() {
        val transcript = "[em] The Speaker [um] certifies the bill, you know, under uh Article 110 em."
        val fillers = VoiceFluencyEngine.extractFillersFromTranscript(transcript)
        val byToken = fillers.associateBy { it.token }

        assertEquals(2, byToken["em"]?.count)
        assertEquals(VoiceFillerCategory.VOCAL_DISFLUENCY, byToken["em"]?.category)
        assertEquals(1, byToken["um"]?.count)
        assertEquals(1, byToken["uh"]?.count)
        assertEquals(1, byToken["you know"]?.count)
        assertEquals(VoiceFillerCategory.LEXICAL_FILLER, byToken["you know"]?.category)
    }

    @Test
    fun detectHesitationGaps_identifiesMidSpeechSilencesIgnoringEnds() {
        val rms = listOf(
            0.01f, 0.01f, // leading silence (ignored)
            0.25f, 0.30f, // speaking
            0.01f, 0.01f, 0.01f, 0.01f, 0.01f, 0.01f, 0.01f, // 700ms pause
            0.22f, 0.28f, // speaking
            0.01f, 0.01f  // trailing silence (ignored)
        )
        val gaps = VoiceFluencyEngine.detectHesitationGaps(
            rmsSamples = rms,
            sampleIntervalMs = 100L,
            silenceThreshold = 0.045f,
            minHesitationPauseMs = 650L
        )
        assertEquals(1, gaps.size)
        assertEquals(700L, gaps.first().durationMs)
    }

    @Test
    fun computeMetrics_computesWpmAndCoverage() {
        val rms = List(100) { 0.25f } // 10s steady speech
        val transcript = "The Speaker [um] of Lok Sabha certifies a Money Bill under Article 110."
        val metrics = VoiceFluencyEngine.computeMetrics(
            durationMs = 10_000L,
            rmsSamples = rms,
            transcript = transcript,
            referenceSummary = "Speaker of the Lok Sabha certifies Money Bill under Article 110.",
            mode = VoicePracticeMode.EXPLAIN_FROM_MEMORY
        )
        assertTrue(metrics.estimatedWpm > 50)
        assertEquals(1, metrics.verbatimFillerCount)
        assertTrue((metrics.keywordCoveragePercent ?: 0) >= 80)
    }
}
