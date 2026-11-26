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

    @Test
    fun buildPromptsForFolder_includesFormulasAndMultipleExpressionsByDefault() {
        val physicsNotes = listOf(
            StudyNoteEntity(
                id = 20L,
                folderId = "f-other",
                title = "Laws of Motion",
                subject = "Physics",
                rawContent = """
                    1. **Second Law of Motion**
                       - Net force is proportional to rate of change of momentum.
                       - Formula: ${'$'}F = m \times a${'$'}
                       - Key Takeaway: Impulse equals change in momentum (${'$'}J = \Delta p${'$'}).
                """.trimIndent()
            )
        )
        val physicsPapers = listOf(
            PaperEntity(
                id = 21L,
                folderId = "f-other",
                title = "Kinematics Set",
                subject = "Physics",
                questionCount = 1,
                answerKeyCount = 1,
                explanationCount = 1,
                rawText = """
                    Q1. Which equation gives displacement under uniform acceleration?
                    A) ${'$'}s = ut + \frac{1}{2}at^2${'$'}
                    B) ${'$'}v = u + at${'$'}
                    C) ${'$'}v^2 = u^2 + 2as${'$'}
                    D) ${'$'}F = ma${'$'}
                    Answer: A
                    Explanation: Use ${'$'}s = ut + \frac{1}{2}at^2${'$'} for displacement with time t.
                """.trimIndent()
            )
        )

        val inclusive = VoiceFluencyEngine.buildPromptsForFolder(
            selectedFolderId = "f-other",
            folders = folders,
            studyNotes = physicsNotes,
            papers = physicsPapers,
            sourceFilter = VoiceSourceFilter.ALL
        )

        assertTrue(inclusive.prompts.size >= 2)
        val notePrompt = inclusive.prompts.first { it.id.startsWith("note_") }
        assertTrue(notePrompt.formulaExpressions.any { it.contains("F = m") || it.contains("\\Delta p") })
        assertTrue(notePrompt.referenceSummary.contains("Formula:"))

        val mcqPrompt = inclusive.prompts.first { it.id.startsWith("paper_") }
        assertEquals(4, mcqPrompt.optionExpressions.size)
        assertTrue(mcqPrompt.formulaExpressions.any { it.contains("ut") || it.contains("\\frac") })
    }

    @Test
    fun computeKeywordCoveragePercent_matchesSpokenWordsForMathSymbols() {
        val coverage = VoiceFluencyEngine.computeKeywordCoveragePercent(
            transcript = "Area equals pi times radius squared",
            referenceSummary = "Area = \u03C0 r^2 and radius"
        )
        assertTrue((coverage ?: 0) >= 75)
    }

    @Test
    fun alignSpokenToReference_reportsMatchedMistakesMissedExtraAndRepetitions() {
        val reference = "The Speaker of Lok Sabha certifies a Money Bill."
        val spoken = "The the department of Lok Sabha certifies very important Money"

        val stats = VoiceFluencyEngine.alignSpokenToReference(
            referenceText = reference,
            spokenText = spoken,
            estimatedWpm = 120
        )

        // Matched: The, of, Lok, Sabha, certifies, Money = 6 / 9
        assertEquals(9, stats.totalReferenceWords)
        assertEquals(6, stats.matchedCount)
        assertEquals(1, stats.mistakeCount) // Speaker -> department
        assertEquals("Speaker", stats.mistakePairs.first().expected)
        assertEquals("department", stats.mistakePairs.first().spoken)
        assertEquals(2, stats.missedCount) // "a", "Bill"
        assertTrue(stats.extraCount >= 2) // repeated "the" + "very" + "important"
        assertEquals(1, stats.repetitionCount)
        assertTrue(stats.repeatedWords.any { it.equals("the", ignoreCase = true) })
        assertTrue(stats.extraWords.contains("very"))
    }

    @Test
    fun evaluateOfflineSpeechCheck_alignsFormulasAndLeavesUntimedManualCheckAtZeroWpm() {
        val prompt = com.example.model.VoicePromptItem(
            id = "p1",
            title = "Newton's Second Law",
            promptQuestion = "State Newton's Second Law.",
            referenceSummary = "Force equals mass times acceleration\nFormula: \$F = m \\times a\$",
            sourceLabel = "Note • Physics",
            folderId = "f-other",
            folderPath = "Physics"
        )

        val result = VoiceFluencyEngine.evaluateOfflineSpeechCheck(
            spokenTranscript = "Force equals mass times acceleration F equals m times a",
            prompt = prompt,
            durationMs = 0L,
            mode = VoicePracticeMode.READ_ALOUD
        )

        val comp = result.metrics.comparison!!
        assertEquals(100, comp.matchPercent)
        assertEquals(0, comp.mistakeCount)
        assertEquals(0, comp.missedCount)
        assertEquals(0, result.metrics.estimatedWpm)
        assertFalse(result.usedAiAudioAnalysis)
    }

    @Test
    fun resolveReadAloudPassage_stripsLabelsAndMatchesGradedScriptOneToOne() {
        val prompt = com.example.model.VoicePromptItem(
            id = "p2",
            title = "Kinetic Energy",
            promptQuestion = "Explain Kinetic Energy in your own words.",
            referenceSummary = "Formula: \$K = \\frac{1}{2} m v^2\$\nKey answer: Half of mass times velocity squared.",
            sourceLabel = "MCQ • Physics",
            folderId = "f-other",
            folderPath = "Physics",
            formulaExpressions = listOf("\$K = \\frac{1}{2} m v^2\$")
        )

        val script = VoiceFluencyEngine.resolveReadAloudPassage(prompt)
        assertFalse(script.contains("Formula:"))
        assertFalse(script.contains("Key answer:"))
        assertFalse(script.contains("Explain Kinetic Energy"))

        val result = VoiceFluencyEngine.evaluateOfflineSpeechCheck(
            spokenTranscript = "K equals 1 over 2 m v squared half of mass times velocity squared",
            prompt = prompt,
            durationMs = 6000L,
            mode = VoicePracticeMode.READ_ALOUD
        )
        assertEquals(100, result.metrics.comparison?.matchPercent)
    }

    @Test
    fun mergeTranscriptSegments_andSpokenNumberOrHyphen_matchCleanly() {
        val merged = VoiceFluencyEngine.mergeTranscriptSegments(
            committed = "Word for word check",
            incomingSegment = "with 2 steps"
        )
        assertEquals("Word for word check with 2 steps", merged)

        val stats = VoiceFluencyEngine.alignSpokenToReference(
            referenceText = "Word-for-word check with two steps",
            spokenText = merged,
            estimatedWpm = 125
        )
        assertEquals(100, stats.matchPercent)
        assertEquals(0, stats.mistakeCount)
        assertEquals(0, stats.missedCount)
        assertEquals(0, stats.extraCount)
    }

    @Test
    fun unclosedAndMassiveMathDelimiters_doNotStackOverflowOrHang() {
        val massiveFiller = "alpha beta gamma delta ".repeat(2500) // ~57 KB
        val maliciousInput = """
            ```
            $massiveFiller
            $$ $massiveFiller
            \[ $massiveFiller
            \( $massiveFiller
            Newton's third law states every action has an equal and opposite reaction.
        """.trimIndent()

        val expressions = VoiceFluencyEngine.extractFormulaExpressions(maliciousInput)
        val unclosedNoDollar = VoiceFluencyEngine.extractFormulaExpressions("```$massiveFiller\n\\[$massiveFiller\n\\($massiveFiller")
        val sanitized = VoiceFluencyEngine.sanitizeNoFormulas(maliciousInput)

        assertTrue(unclosedNoDollar.isEmpty())
        assertFalse(expressions.any { it.contains("Newton's third law") })
        assertTrue(sanitized.contains("Newton's third law"))
    }

    @Test
    fun buildPromptsForFolder_capsLargeLibraryBalancedAndIsolatesCorruptPaper() {
        val manyNotes = (1..25).map { idx ->
            StudyNoteEntity(
                id = idx.toLong(),
                folderId = "f-parent",
                title = "Note $idx",
                subject = "Polity",
                rawContent = "1. **Concept $idx** -> Detailed explanation of constitutional provision $idx in Parliament."
            )
        }
        val manyPapers = (1..25).map { idx ->
            PaperEntity(
                id = idx.toLong(),
                folderId = "f-parent",
                title = if (idx == 1) "Corrupt Paper" else "Quiz $idx",
                subject = "Polity",
                questionCount = 1,
                answerKeyCount = 1,
                explanationCount = 1,
                rawText = """
                    Q1. Question number $idx on Parliament procedure?
                    A) Option A
                    B) Option B
                    Answer: A
                """.trimIndent()
            )
        }

        val result = VoiceFluencyEngine.buildPromptsForFolder(
            selectedFolderId = "all",
            folders = folders,
            studyNotes = manyNotes,
            papers = manyPapers,
            sourceFilter = VoiceSourceFilter.ALL,
            maxTotalPrompts = 10,
            maxPromptsPerPaper = 5,
            maxPromptsPerNote = 5,
            paperParser = { entity ->
                if (entity.id == 1L) throw IllegalStateException("Simulated malformed paper")
                com.example.util.McqParser.parse(entity.rawText)
            }
        )

        assertEquals(10, result.prompts.size)
        assertTrue(result.isTruncated)
        assertEquals(50, result.totalMatchingSources)
        assertTrue(result.prompts.any { it.id.startsWith("note_") })
        assertTrue(result.prompts.any { it.id.startsWith("paper_") })
        assertFalse(result.prompts.any { it.id.startsWith("paper_1_") })
    }
}
