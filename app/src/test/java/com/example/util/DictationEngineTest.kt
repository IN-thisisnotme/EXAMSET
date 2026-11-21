package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationEngineTest {

    @Test
    fun `sanitizeForSpeech cleans markdown, arrows, simple LaTeX and replaces complex equations`() {
        val raw = """
            ## Thermodynamics Core
            > **Exam Tip:** See [NCERT Ch. 5](https://example.com) for details.
            - **First Law** -> Energy conservation: ${'$'}\Delta U = Q - W${'$'}
            - Kinetic term: ${'$'}\frac{1}{2} m v^2${'$'}

            ${'$'}${'$'}
            \int_{0}^{\infty} \sum_{n=1}^{\infty} \frac{x^n}{n!} \, dx = \infty
            ${'$'}${'$'}
        """.trimIndent()

        val cleaned = DictationEngine.sanitizeForSpeech(raw)

        assertFalse(cleaned.contains("##"))
        assertFalse(cleaned.contains("**"))
        assertFalse(cleaned.contains("https://"))
        assertFalse(cleaned.contains("->"))
        assertTrue(cleaned.contains("NCERT Ch. 5"))
        assertTrue(cleaned.contains("leads to"))
        assertTrue(cleaned.contains("1 over 2"))
        assertTrue(cleaned.contains("equation shown on screen"))
        assertTrue("Paragraph separator should be preserved", cleaned.contains("\n\n"))
    }

    @Test
    fun `splitPassageIntoPhrases protects abbreviations and decimals and chunks clauses cleanly`() {
        val raw = """
            Under Art. 21 and Sec. 14, citizens enjoy fundamental guarantees, e.g. personal liberty at 9.81 meters per second.

            However, the Supreme Court expanded due process in later landmark constitutional rulings!
        """.trimIndent()

        val phrases = DictationEngine.splitPassageIntoPhrases(raw, speakPunctuationCues = false)
        assertTrue(phrases.isNotEmpty())

        // Abbreviations and decimals must not trigger false single-word sentence breaks
        val firstSentencePhrases = phrases.filter { it.sentenceIndex == 0 }
        assertTrue(firstSentencePhrases.any { it.displayText.contains("Art. 21") })
        assertTrue(firstSentencePhrases.any { it.displayText.contains("e.g.") })
        assertTrue(firstSentencePhrases.any { it.displayText.contains("9.81") })

        // Multiple paragraphs preserved
        assertEquals(0, phrases.first().paragraphIndex)
        assertEquals(1, phrases.last().paragraphIndex)
        assertTrue(phrases.last().isParagraphEnd)

        // Chunk word count stays natural (avoiding giant unchunked blocks or trailing 1-word orphans when mergeable)
        assertTrue(phrases.all { it.wordCount in 1..7 })
    }

    @Test
    fun `splitPassageIntoPhrases appends spoken punctuation and paragraph cues when enabled`() {
        val raw = "First, read the prompt carefully. Next, write your answer cleanly?\n\nFinally summarize key findings."

        val phrases = DictationEngine.splitPassageIntoPhrases(raw, speakPunctuationCues = true)
        assertTrue(phrases.any { it.spokenText.endsWith("comma") })
        assertTrue(phrases.any { it.spokenText.endsWith("full stop") })
        assertTrue(phrases.any { it.spokenText.contains("question mark") })
        assertTrue(phrases.any { it.spokenText.contains("new paragraph") })
    }

    @Test
    fun `computeWritingPauseMs scales inversely with WPM and accounts for word complexity`() {
        val shortPhrase = DictationPhrase(
            index = 0,
            sentenceIndex = 0,
            paragraphIndex = 0,
            displayText = "Cats run fast in sun.",
            spokenText = "Cats run fast in sun",
            wordCount = 5,
            charCount = 17,
            isSentenceEnd = true
        )
        val densePhrase = DictationPhrase(
            index = 1,
            sentenceIndex = 1,
            paragraphIndex = 0,
            displayText = "Electroencephalography demonstrates synchronization phenomena.",
            spokenText = "Electroencephalography demonstrates synchronization phenomena",
            wordCount = 4,
            charCount = 58,
            isSentenceEnd = true
        )

        val slowCfg = DictationConfig(targetWpm = 30)
        val fastCfg = DictationConfig(targetWpm = 90)

        val slowPause = DictationEngine.computeWritingPauseMs(shortPhrase, slowCfg)
        val fastPause = DictationEngine.computeWritingPauseMs(shortPhrase, fastCfg)
        assertTrue("30 WPM should allow significantly longer writing pause than 90 WPM", slowPause > fastPause + 2000L)

        val densePause = DictationEngine.computeWritingPauseMs(densePhrase, slowCfg)
        assertTrue("Longer technical characters should get substantial writing pause", densePause in 300L..20_000L)
        assertTrue(fastPause in 300L..20_000L)
    }

    @Test
    fun `computeNextElevatedWpm steps smoothly and clamps at targetMaxWpm`() {
        val offConfig = DictationConfig(elevatingEnabled = false, startWpm = 35, targetMaxWpm = 75, stepWpm = 5)
        assertEquals(45, DictationEngine.computeNextElevatedWpm(45, offConfig))

        val activeConfig = DictationConfig(elevatingEnabled = true, startWpm = 35, targetMaxWpm = 50, stepWpm = 5)
        assertEquals(40, DictationEngine.computeNextElevatedWpm(35, activeConfig))
        assertEquals(45, DictationEngine.computeNextElevatedWpm(40, activeConfig))
        assertEquals(50, DictationEngine.computeNextElevatedWpm(45, activeConfig))
        assertEquals(50, DictationEngine.computeNextElevatedWpm(50, activeConfig))
    }

    @Test
    fun `extractPassages converts parsed study note blocks into dictatable passages excluding headings`() {
        val raw = """
            ## Constitutional Law
            1. **Article 14** -> Guarantees equality before law and equal protection of laws.
            2. **Basic Structure Doctrine** -> Limits parliamentary amending power under Article 368.
              - Key Takeaway: Judicial review cannot be abrogated.

            > Exam Tip: Mention Kesavananda Bharati (1973) in Mains answers.

            | Provision | Article | Scope |
            | --- | --- | --- |
            | Equality | 14 | All persons |
        """.trimIndent()

        val blocks = StudyNoteParser.parseBlocks(raw)
        val passages = DictationEngine.extractPassages(blocks)

        assertEquals(4, passages.size)
        assertEquals("One-Liner", passages[0].badge)
        assertTrue(passages[0].rawDictationText.contains("Article 14"))
        assertTrue(passages[1].rawDictationText.contains("Key Takeaway:"))
        assertEquals("Callout", passages[2].badge)
        assertEquals("Table", passages[3].badge)
        assertTrue(passages[3].rawDictationText.contains("Provision: Equality"))
    }
}
