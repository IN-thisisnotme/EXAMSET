package com.example.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.PromptPresetMode
import com.example.model.SrsRating
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyExample
import com.example.model.VocabularyLevel
import com.example.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VocabularyEngineTest {

    @Test
    fun parseVocabularyInput_parsesFencedJsonWithMultiLanguageAndMultipleContexts() {
        val raw = """
            ```json
            [
              {
                "word": "Ubiquitous",
                "pronunciation": "/juːˈbɪk.wɪ.təs/",
                "partOfSpeech": "Adjective",
                "level": "C1",
                "meaning": "Present, appearing, or found everywhere.",
                "translation": "सर्वव्यापी",
                "examples": [
                  {
                    "context": "Everyday",
                    "sentence": "Smartphones have become ubiquitous on morning commutes.",
                    "translation": "सुबह के सफर में स्मार्टफोन सर्वव्यापी हो गए हैं।"
                  },
                  {
                    "context": "Academic",
                    "sentence": "Microplastics are now ubiquitous across marine ecosystems."
                  }
                ],
                "synonyms": ["omnipresent", "pervasive"],
                "antonyms": ["rare", "scarce"],
                "collocations": ["ubiquitous presence", "become ubiquitous"],
                "usageNote": "Common in formal writing and journalism."
              },
              {
                "word": "Alacrity",
                "partOfSpeech": "Noun",
                "level": "C2",
                "meaning": "Brisk and cheerful readiness.",
                "examples": [
                  {"context": "Workplace", "sentence": "She accepted the fellowship with alacrity."}
                ]
              }
            ]
            ```
        """.trimIndent()

        val result = VocabularyEngine.parseVocabularyInput(raw)
        assertEquals(VocabularyInputFormat.JSON, result.detectedFormat)
        assertEquals(2, result.entries.size)

        val first = result.entries[0]
        assertEquals("Ubiquitous", first.word)
        assertEquals("Adjective", first.partOfSpeech)
        assertEquals("C1", first.levelCode)
        assertEquals("सर्वव्यापी", first.translation)
        assertEquals(2, first.examples.size)
        assertEquals("Everyday", first.examples[0].context)
        assertEquals("Academic", first.examples[1].context)
        assertEquals(listOf("omnipresent", "pervasive"), first.synonyms)
        assertEquals(listOf("ubiquitous presence", "become ubiquitous"), first.collocations)

        val deck = VocabularyDeck(title = "Editorial Core", language = "English", entries = result.entries)
        val sorted = deck.sortedEntriesAscending()
        assertEquals(listOf("Alacrity", "Ubiquitous"), sorted.map { it.word })
    }

    @Test
    fun parseVocabularyInput_parsesStructuredTextBlocks() {
        val raw = """
            WORD: Mitigate
            PRONUNCIATION: /ˈmɪt.ɪ.ɡeɪt/
            POS: Verb
            LEVEL: B2
            MEANING: To make something less severe, harmful, or painful.
            TRANSLATION: कम करना
            EXAMPLES:
            - [Workplace] Clear protocols help mitigate operational risk. | स्पष्ट नियम जोखिम कम करने में मदद करते हैं।
            - [Academic] Coastal wetlands mitigate storm surge impact.
            SYNONYMS: alleviate, reduce
            ANTONYMS: aggravate
            COLLOCATIONS: mitigate risk, mitigate impact
            USAGE: Often paired with risk, harm, or impact.
            ---
            WORD: Pragmatic
            POS: Adjective
            LEVEL: C1
            MEANING: Dealing with things sensibly and realistically.
            EXAMPLES:
            - [Everyday] We took a pragmatic approach to the budget.
        """.trimIndent()

        val result = VocabularyEngine.parseVocabularyInput(raw)
        assertEquals(VocabularyInputFormat.STRUCTURED_TEXT, result.detectedFormat)
        assertEquals(2, result.entries.size)
        assertEquals("Mitigate", result.entries[0].word)
        assertEquals("Verb", result.entries[0].partOfSpeech)
        assertEquals("B2", result.entries[0].levelCode)
        assertEquals("कम करना", result.entries[0].translation)
        assertEquals(2, result.entries[0].examples.size)
        assertEquals("Workplace", result.entries[0].examples[0].context)
        assertEquals("स्पष्ट नियम जोखिम कम करने में मदद करते हैं।", result.entries[0].examples[0].translation)
        assertEquals(listOf("alleviate", "reduce"), result.entries[0].synonyms)
    }

    @Test
    fun analyzeVocabularyImport_detectsSameDeckCrossDeckAndIntraBatchDuplicatesAndMergesGranularly() {
        val existingDeck = VocabularyDeck(
            id = "deck-en-1",
            title = "Core C1",
            language = "English",
            levelCode = VocabularyLevel.ADVANCED.code,
            entries = listOf(
                VocabularyEntry(
                    id = "entry-1",
                    word = "Resilient",
                    partOfSpeech = "Adjective",
                    levelCode = "B2",
                    meaning = "Able to recover quickly from difficulties.",
                    examples = listOf(VocabularyExample("Everyday", "Children are remarkably resilient.")),
                    synonyms = listOf("tough")
                )
            )
        )
        val otherLangDeck = VocabularyDeck(
            id = "deck-es-1",
            title = "Spanish Basics",
            language = "Spanish",
            levelCode = VocabularyLevel.BEGINNER.code,
            entries = listOf(
                VocabularyEntry(
                    id = "entry-es",
                    word = "Real",
                    meaning = "Royal or real."
                )
            )
        )

        val parsed = VocabularyParseResult(
            entries = listOf(
                // Duplicate of existing English deck (different case & extra context/translation)
                VocabularyEntry(
                    word = "resilient",
                    partOfSpeech = "Adjective",
                    levelCode = "C1",
                    meaning = "Capable of withstanding shock without permanent deformation.",
                    translation = "लचीला",
                    examples = listOf(
                        VocabularyExample("Everyday", "Children are remarkably resilient."), // identical sentence -> deduped
                        VocabularyExample("Workplace", "We need a resilient supply chain.")
                    ),
                    synonyms = listOf("tough", "adaptable")
                ),
                // Same word as Spanish deck -> should NOT be flagged as English duplicate
                VocabularyEntry(
                    word = "Real",
                    meaning = "Actually existing as a thing or occurring in fact."
                ),
                // Brand new word + intra-batch duplicate
                VocabularyEntry(
                    word = "Lucid",
                    meaning = "Expressed clearly; easy to understand.",
                    examples = listOf(VocabularyExample("Academic", "Her lucid explanation settled the debate."))
                ),
                VocabularyEntry(
                    word = "Lucid",
                    meaning = "Clear and coherent.",
                    examples = listOf(VocabularyExample("Everyday", "He gave a lucid account of the trip."))
                )
            ),
            detectedFormat = VocabularyInputFormat.JSON
        )

        val analysis = VocabularyEngine.analyzeVocabularyImport(
            parseResult = parsed,
            targetDeck = null,
            targetLanguage = "English",
            allDecks = listOf(existingDeck, otherLangDeck),
            checkAcrossSameLanguage = true
        )

        assertEquals(2, analysis.uniqueNewEntries.size) // Real + first Lucid
        assertEquals(2, analysis.duplicateItems.size) // cross-deck resilient + intra-batch Lucid

        val crossDeckDup = analysis.duplicateItems.first { !it.isIntraBatch }
        assertFalse(crossDeckDup.isSameDeck)
        assertEquals("deck-en-1", crossDeckDup.existingDeckId)
        assertTrue(crossDeckDup.hasNewExamples)
        assertTrue(crossDeckDup.hasNewTranslation)

        val merged = VocabularyEngine.mergeVocabularyEntry(
            existing = crossDeckDup.existing,
            incoming = crossDeckDup.incoming,
            decision = VocabularyMergeDecision(
                action = VocabularyDuplicateAction.MERGE_SEGMENTS,
                mergeExamples = true,
                mergeTranslation = true,
                mergeSynonymsAndCollocations = true,
                mergeMeaningAndNote = false,
                mergeLevelAndMeta = false
            )
        )
        assertEquals("Able to recover quickly from difficulties.", merged.meaning) // preserved!
        assertEquals("लचीला", merged.translation) // added!
        assertEquals(2, merged.examples.size) // deduplicated identical sentence, appended Workplace example!
        assertEquals(listOf("tough", "adaptable"), merged.synonyms)
        assertEquals("B2", merged.levelCode) // preserved!
    }

    @Test
    fun auditHub_buildsPromptParsesFindingsAndAppliesCategoryAndLevelFixes() {
        val entry = VocabularyEntry(
            id = "e-audit-1",
            word = "Ephemeral",
            partOfSpeech = "Noun", // intentionally wrong POS!
            levelCode = "A2", // intentionally wrong level!
            meaning = "Lasting for a very short time.",
            examples = listOf(VocabularyExample("Academic", "Fame on social media is often ephemeral."))
        )
        val deck = VocabularyDeck(
            id = "deck-audit",
            title = "Academic Core",
            language = "English",
            levelCode = "C1",
            entries = listOf(entry)
        )

        val prompt = VocabularyEngine.buildVocabularyAuditPrompt(deck)
        assertTrue(prompt.contains("CATEGORY / WORD CLASS ACCURACY"))
        assertTrue(prompt.contains("Ephemeral"))

        val aiResponse = """
            ```json
            [
              {
                "word": "Ephemeral",
                "status": "CATEGORY_ISSUE",
                "verifiedCategoryType": "Formal Adjective · Academic / Literary",
                "issueSummary": "Ephemeral is an adjective (not a noun) and belongs at C1/C2 level.",
                "suggestedPartOfSpeech": "Adjective",
                "suggestedLevel": "C1",
                "suggestedUsageNote": "Usually modifies abstract nouns like fame, trends, or pleasure."
              }
            ]
            ```
        """.trimIndent()

        val report = VocabularyEngine.parseVocabularyAuditReport(aiResponse, deck)
        assertEquals(0, report.verifiedCount)
        assertEquals(1, report.flaggedCount)
        val finding = report.findings.single()
        assertEquals(VocabularyAuditStatus.CATEGORY_ISSUE, finding.status)
        assertTrue(finding.hasActionableChanges)

        val fixed = VocabularyEngine.applyAuditFinding(entry, finding)
        assertEquals("Adjective", fixed.partOfSpeech)
        assertEquals("C1", fixed.levelCode)
        assertTrue(fixed.usageNote.contains("abstract nouns"))
    }

    @Test
    fun mainViewModel_commitsImportPersistsSrsRatesAuditFixesAndSavesVocabPreset() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("examset_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = MainViewModel(app)

        val parse = VocabularyEngine.parseVocabularyInput(
            """
            [
              {
                "word": "Zealous",
                "partOfSpeech": "Adjective",
                "level": "C1",
                "meaning": "Having or showing great energy or enthusiasm.",
                "examples": [{"context": "Everyday", "sentence": "She is a zealous advocate for literacy."}]
              },
              {
                "word": "Adept",
                "partOfSpeech": "Noun",
                "level": "B2",
                "meaning": "Very skilled or proficient at something.",
                "examples": [{"context": "Workplace", "sentence": "He is adept at negotiating complex contracts."}]
              }
            ]
            """.trimIndent()
        )
        val analysis = VocabularyEngine.analyzeVocabularyImport(
            parseResult = parse,
            targetDeck = null,
            targetLanguage = "English",
            allDecks = vm.vocabularyDecks.value,
            checkAcrossSameLanguage = true
        )
        val deck = vm.commitVocabularyImport(
            targetDeckId = null,
            title = "Editorial Mastery",
            language = "English",
            translationLanguage = "Hindi",
            levelCode = "C1",
            customLevelLabel = "",
            domain = "Editorial / News",
            analysis = analysis,
            decisions = emptyMap()
        )

        // Ascending alphabetical order verified
        assertEquals(listOf("Adept", "Zealous"), deck.entries.map { it.word })
        assertEquals(listOf("Adept", "Zealous"), vm.existingVocabularyWordsForLanguage("English"))

        // SM-2 Active Recall rating updates stats
        val adeptId = deck.entries.first().id
        val beforeStats = VocabularyEngine.computeDeckSrsStats(deck, vm.vocabularySrsByDeck.value[deck.id].orEmpty())
        assertEquals(2, beforeStats.newCount)
        vm.rateVocabularyEntry(deck.id, adeptId, SrsRating.GOOD)
        val afterSrs = vm.vocabularySrsByDeck.value[deck.id].orEmpty()
        assertTrue(afterSrs.containsKey(adeptId))
        val queueDueAndNew = VocabularyEngine.buildRecallQueue(deck, afterSrs, cramAll = false)
        assertEquals(1, queueDueAndNew.size) // Only Zealous remains new; Adept scheduled in future!
        assertEquals("Zealous", queueDueAndNew.single().word)

        // Apply audit fix via ViewModel
        val report = VocabularyEngine.parseVocabularyAuditReport(
            """[{"word":"Adept","status":"CATEGORY_ISSUE","suggestedPartOfSpeech":"Adjective","issueSummary":"Used as adjective here."}]""",
            deck
        )
        vm.applyVocabularyAuditFixes(deck.id, report.findings)
        val updatedAdept = vm.vocabularyDecks.value.first { it.id == deck.id }.entries.first { it.id == adeptId }
        assertEquals("Adjective", updatedAdept.partOfSpeech)

        // Save Vocabulary Prompt Preset and reload
        vm.vocabularyPromptState.language = "Japanese"
        vm.vocabularyPromptState.levelCode = VocabularyLevel.CUSTOM.code
        vm.vocabularyPromptState.customLevelLabel = "JLPT N3"
        val preset = vm.saveNewPromptPreset(PromptPresetMode.VOCABULARY, "JLPT N3 Daily")
        assertEquals(PromptPresetMode.VOCABULARY, preset.mode)
        assertEquals("Japanese", preset.vocabularyConfig?.language)
        assertEquals("JLPT N3", preset.vocabularyConfig?.customLevelLabel)
    }

    @Test
    fun parseVocabularyInput_handlesTruncatedTailMissingOuterBracketsTrailingCommasAndSmartQuotes() {
        val messyChatGptOutput = """
            ```json
            [
              {
                "word": "aberration",
                "pronunciation": "/ˌæb.əˈreɪ.ʃən/",
                "partOfSpeech": "noun",
                "level": "C2",
                "meaning": "A departure from what is normal, usual, or expected.",
                "translation": "अपवाद; असामान्य विचलन",
                "examples": [
                  {
                    "context": "Academic",
                    "sentence": "Researchers questioned whether the result was a genuine discovery or merely an aberration.",
                    "translation": "शोधकर्ताओं ने सवाल उठाया कि परिणाम वास्तविक खोज था या केवल एक असामान्य विचलन।"
                  },
                ],
                "synonyms": ["anomaly", "deviation",],
                "usageNote": "Often implies a temporary deviation.",
              },
              {
                "word": "incongruous",
                "pronunciation": "/ɪnˈkɒŋɡruəs/",
                "partOfSpeech": "adjective",
                "level": "C2",
                "meaning": "Not in harmony or agreement with the surrounding circumstances.",
                "translation": "बेमेल; असंगत",
                "examples": [
                  {
                    "context": "Everyday",
                    "sentence": "His bright red shoes looked incongruous with the formal outfit.",
                    "translation": "उसके चमकीले लाल जूते औपचारिक पहनावे के साथ बेमेल लग रहे थे।"
                  }
                ],
                "synonyms": ["incompatible", "discordant"],
                "antonyms": ["consistent", "harmonious"],
                "collocations": ["incongruous combination"],
                "usageNote": "Highlights a mismatch between things."
              },
              {
                "word": "ubiquitous",
                "meaning": "Present everywhere"
        """.trimIndent()

        val result = VocabularyEngine.parseVocabularyInput(messyChatGptOutput)
        assertEquals(VocabularyInputFormat.JSON, result.detectedFormat)
        assertEquals(2, result.entries.size)
        assertEquals(listOf("aberration", "incongruous"), result.entries.map { it.word })
        assertEquals("अपवाद; असामान्य विचलन", result.entries[0].translation)
        assertEquals(1, result.entries[0].examples.size)
        assertTrue(result.warnings.any { it.contains("Recovered 2 entries") })

        // Also verify missing outer [ ] with smart quotes and trailing commentary containing brackets [1]
        val noBracketsWithSmartQuotes = """
            “word”: “ephemeral”,
            “meaning”: “Lasting for a very short time.”,
            “examples”: [{“context”: “Academic”, “sentence”: “Trends are ephemeral.”}]
            }
            {
            “word”: “lucid”,
            “meaning”: “Expressed clearly.”
            }
            [Note: Generated 2 words]
        """.trimIndent()
        val wrappedObjects = "{\n$noBracketsWithSmartQuotes"
        val res2 = VocabularyEngine.parseVocabularyInput(wrappedObjects)
        assertEquals(VocabularyInputFormat.JSON, res2.detectedFormat)
        assertEquals(listOf("ephemeral", "lucid"), res2.entries.map { it.word })

        // Verify broken JSON with zero recoverable entries gives a JSON-specific diagnostic
        val brokenJson = "[{\"word\": \"incomplete\", \"meaning\":"
        val res3 = VocabularyEngine.parseVocabularyInput(brokenJson)
        assertTrue(res3.entries.isEmpty())
        assertEquals(VocabularyInputFormat.JSON, res3.detectedFormat)
        assertTrue(res3.warnings.first().contains("Detected JSON format"))
    }
}

