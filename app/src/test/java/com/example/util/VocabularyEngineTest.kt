package com.example.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ExamSetPreferences
import com.example.model.FlashcardSrsState
import com.example.model.HeadwordQueueItem
import com.example.model.PromptPresetMode
import com.example.model.SrsRating
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyExample
import com.example.model.VocabularyLanguageVariants
import com.example.model.VocabularyLevel
import com.example.model.VocabularyPromptConfig
import com.example.model.VocabularyUtilityTier
import com.example.ui.MainViewModel
import com.example.ui.screens.VocabularyPromptState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

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

    @Test
    fun smartRouting_detectsLanguagesVariantsLevelsAndRoutesIntoExistingOrNewSets() {
        val existingC1Deck = VocabularyDeck(
            id = "deck-en-c1",
            title = "Oxford C1 Core",
            language = "English",
            languageVariant = "UK",
            levelCode = VocabularyLevel.ADVANCED.code,
            entries = listOf(
                VocabularyEntry(
                    id = "existing-eloquent",
                    word = "Eloquent",
                    meaning = "Fluent or persuasive in speaking or writing.",
                    levelCode = "C1"
                )
            )
        )

        val mixedBatch = """
            ```json
            [
              {
                "word": "analyse",
                "partOfSpeech": "Verb",
                "level": "B2",
                "meaning": "Examine methodically and in detail.",
                "translation": "विश्लेषण करना",
                "examples": [{"context": "Academic", "sentence": "We will analyse the findings."}]
              },
              {
                "word": "eloquent",
                "partOfSpeech": "Adjective",
                "level": "C1",
                "meaning": "Fluent and persuasive.",
                "translation": "वाक्पटु",
                "examples": [{"context": "Formal", "sentence": "She gave an eloquent speech."}]
              },
              {
                "word": "ephemeral",
                "partOfSpeech": "Adjective",
                "level": "C2",
                "meaning": "Lasting for a very short time.",
                "translation": "क्षणभंगुर",
                "examples": [{"context": "Literary", "sentence": "Social media trends are ephemeral."}]
              },
              {
                "word": "一期一会",
                "partOfSpeech": "Idiom",
                "level": "JLPT N2",
                "meaning": "One time, one meeting; treasure every encounter.",
                "examples": [{"context": "Culture", "sentence": "一期一会の精神を大切にする。"}]
              }
            ]
            ```
        """.trimIndent()

        val parseResult = VocabularyEngine.parseVocabularyInput(
            rawInput = mixedBatch,
            preferredEnglishVariant = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT
        )
        assertEquals(4, parseResult.entries.size)
        assertEquals("UK", parseResult.detectedVariant)
        assertEquals("Hindi", parseResult.detectedTranslationLanguage)
        assertTrue(parseResult.detectedLanguages.containsAll(listOf("English", "Japanese")))

        val buckets = VocabularyEngine.buildSmartRoutingPlan(
            parseResult = parseResult,
            allDecks = listOf(existingC1Deck),
            preferredEnglishVariant = "UK"
        )
        // Expected 4 buckets: English B2, English C1, English C2, Japanese CUSTOM (JLPT N2)
        assertEquals(4, buckets.size)

        val enC1Bucket = buckets.first { it.language == "English" && it.levelCode == "C1" }
        assertEquals("deck-en-c1", enC1Bucket.recommendedDeckId)
        assertTrue(enC1Bucket.titleSuggestions.isNotEmpty())

        val jpBucket = buckets.first { it.language == "Japanese" }
        assertEquals(VocabularyLevel.CUSTOM.code, jpBucket.levelCode)
        assertEquals("JLPT N2", jpBucket.customLevelLabel)
        assertTrue(jpBucket.defaultNewTitle.contains("JLPT N2"))

        // Choose targets (route C1 to existing deck, custom titles for the other 3 new sets)
        val choices = buckets.associate { b ->
            b.bucketKey to VocabularySmartRouteTargetChoice(
                bucketKey = b.bucketKey,
                targetDeckId = b.recommendedDeckId,
                customTitle = b.defaultNewTitle,
                language = b.language,
                languageVariant = b.languageVariant,
                translationLanguage = b.translationLanguage,
                levelCode = b.levelCode,
                customLevelLabel = b.customLevelLabel
            )
        }
        val routeAnalysis = VocabularyEngine.analyzeSmartRoutedImport(
            parseResult = parseResult,
            buckets = buckets,
            choices = choices,
            allDecks = listOf(existingC1Deck),
            checkAcrossSameLanguage = true
        )
        assertEquals(3, routeAnalysis.analysis.uniqueNewEntries.size) // analyse, ephemeral, 一期一会
        assertEquals(1, routeAnalysis.analysis.duplicateItems.size) // eloquent matches existingC1Deck

        // Verify ViewModel commitSmartRoutedVocabularyImport splits entries across sets accurately
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("examset_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = MainViewModel(app)
        val existingCommitted = vm.commitVocabularyImport(
            targetDeckId = null,
            title = "Oxford C1 Core",
            language = "English",
            translationLanguage = "Hindi",
            levelCode = "C1",
            customLevelLabel = "",
            domain = "General",
            analysis = VocabularyEngine.analyzeVocabularyImport(
                parseResult = VocabularyParseResult(
                    entries = existingC1Deck.entries,
                    detectedFormat = VocabularyInputFormat.JSON
                ),
                targetDeck = null,
                targetLanguage = "English",
                allDecks = emptyList()
            ),
            decisions = emptyMap(),
            languageVariant = "UK"
        )
        val updatedBuckets = VocabularyEngine.buildSmartRoutingPlan(parseResult, vm.vocabularyDecks.value, "UK")
        val updatedChoices = updatedBuckets.associate { b ->
            b.bucketKey to VocabularySmartRouteTargetChoice(
                bucketKey = b.bucketKey,
                targetDeckId = b.recommendedDeckId,
                customTitle = b.defaultNewTitle,
                language = b.language,
                languageVariant = b.languageVariant,
                translationLanguage = b.translationLanguage,
                levelCode = b.levelCode,
                customLevelLabel = b.customLevelLabel
            )
        }
        val vmRouteAnalysis = VocabularyEngine.analyzeSmartRoutedImport(
            parseResult = parseResult,
            buckets = updatedBuckets,
            choices = updatedChoices,
            allDecks = vm.vocabularyDecks.value,
            checkAcrossSameLanguage = true
        )
        val mergeDecisions = vmRouteAnalysis.analysis.duplicateItems.associate { dup ->
            dup.id to VocabularyMergeDecision(
                action = VocabularyDuplicateAction.MERGE_SEGMENTS,
                mergeExamples = true,
                mergeTranslation = true
            )
        }
        val touched = vm.commitSmartRoutedVocabularyImport(
            targets = updatedChoices.values.toList(),
            entryBucketById = vmRouteAnalysis.entryBucketById,
            analysis = vmRouteAnalysis.analysis,
            decisions = mergeDecisions
        )
        assertEquals(4, touched.size)
        val reloadedC1 = vm.vocabularyDecks.value.first { it.id == existingCommitted.id }
        assertEquals("वाक्पटु", reloadedC1.entries.first().translation)
        assertTrue(vm.vocabularyDecks.value.any { it.language == "Japanese" && it.entries.any { e -> e.word == "一期一会" } })
    }

    @Test
    fun wordOfTheDayService_enforcesThresholdLanguageIsolationUtcRolloverAndFullCycleHistory() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("examset_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = ExamSetPreferences(app)

        // Prompt config and preferences still default to UK English variant
        val defaultCfg = VocabularyPromptConfig()
        assertEquals("UK", defaultCfg.languageVariant)
        assertEquals("UK", prefs.getPreferredEnglishVariant())
        prefs.setPreferredEnglishVariant("US")
        assertEquals("US", prefs.getPreferredEnglishVariant())

        // Helper to build N unique entries for a language
        fun makeEntries(prefix: String, count: Int, levelCode: String = "B1", lang: String = "English"): List<VocabularyEntry> =
            (1..count).map { idx ->
                VocabularyEntry(
                    id = "${lang}_${prefix}_$idx",
                    word = "${prefix}${idx.toString().padStart(2, '0')}",
                    partOfSpeech = "Noun",
                    levelCode = levelCode,
                    language = lang,
                    meaning = "Meaning for $prefix$idx"
                )
            }

        // Below threshold (< 10 unique words) -> Hidden
        val smallEnglishDeck = VocabularyDeck(
            id = "en_b1",
            title = "English B1 Core",
            language = "English",
            levelCode = "B1",
            entries = makeEntries("Alpha", 9, levelCode = "B1", lang = "English")
        )
        val hiddenState = WordOfTheDayService.resolveForLanguage(
            prefs = prefs,
            decks = listOf(smallEnglishDeck),
            language = "English",
            nowMillis = 1795132799000L // 2026-11-19T23:59:59Z
        )
        assertTrue(hiddenState is WordOfTheDayUiState.Hidden)
        assertEquals(9, (hiddenState as WordOfTheDayUiState.Hidden).currentUniqueWords)
        assertEquals(WordOfTheDayService.MIN_WORDS_THRESHOLD, hiddenState.minThreshold)

        // Cross threshold with 10 unique words across two levels (A1 and B1) + an isolated Hindi deck
        val a1Deck = VocabularyDeck(
            id = "en_a1",
            title = "English A1 Basics",
            language = "English",
            levelCode = "A1",
            entries = makeEntries("Basic", 2, levelCode = "A1", lang = "English")
        )
        val b1Deck = VocabularyDeck(
            id = "en_b1_full",
            title = "English B1 Core",
            language = "English",
            levelCode = "B1",
            entries = makeEntries("Alpha", 8, levelCode = "B1", lang = "English")
        )
        val hindiDeck = VocabularyDeck(
            id = "hi_a1",
            title = "Hindi Foundation",
            language = "Hindi",
            levelCode = "A1",
            entries = makeEntries("संकल्प", 10, levelCode = "A1", lang = "Hindi")
        )
        val allDecks = listOf(b1Deck, a1Deck, hindiDeck)

        val day1BeforeMidnight = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse("2026-11-20T23:59:50Z")!!.time

        val day1Morning = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse("2026-11-20T06:00:00Z")!!.time

        val day2AfterMidnightUtc = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse("2026-11-21T00:00:05Z")!!.time

        // Day 1 Morning: Systematic order picks A1 Basic01 first (not random, not Hindi)
        val enDay1Morning = WordOfTheDayService.resolveForLanguage(
            prefs = prefs,
            decks = allDecks,
            language = "English",
            nowMillis = day1Morning
        ) as WordOfTheDayUiState.Ready
        assertEquals("2026-11-20", enDay1Morning.utcDateKey)
        assertEquals("Basic01", enDay1Morning.entry.word)
        assertEquals("en_a1", enDay1Morning.sourceDeckId)
        assertEquals(1, enDay1Morning.seenInCycleCount)
        assertEquals(10, enDay1Morning.uniqueWordCount)

        // Same UTC day right before midnight (23:59:50 UTC) -> locked to same word
        val enDay1Night = WordOfTheDayService.resolveForLanguage(
            prefs = prefs,
            decks = allDecks,
            language = "English",
            nowMillis = day1BeforeMidnight
        ) as WordOfTheDayUiState.Ready
        assertEquals("Basic01", enDay1Night.entry.word)

        // Crossing 00:00 UTC -> advances systematically to Basic02 and appends to history
        val enDay2 = WordOfTheDayService.resolveForLanguage(
            prefs = prefs,
            decks = allDecks,
            language = "English",
            nowMillis = day2AfterMidnightUtc
        ) as WordOfTheDayUiState.Ready
        assertEquals("2026-11-21", enDay2.utcDateKey)
        assertEquals("Basic02", enDay2.entry.word)
        assertEquals(2, enDay2.seenInCycleCount)
        assertEquals(listOf("Basic02", "Basic01"), enDay2.history.map { it.word })

        // Hindi folder has completely independent WOTD state and history
        val hiDay2 = WordOfTheDayService.resolveForLanguage(
            prefs = prefs,
            decks = allDecks,
            language = "Hindi",
            nowMillis = day2AfterMidnightUtc
        ) as WordOfTheDayUiState.Ready
        assertEquals("Hindi", hiDay2.language)
        assertEquals("संकल्प01", hiDay2.entry.word)
        assertEquals("", hiDay2.variant)
        assertEquals(1, hiDay2.history.size)

        // Complete the remaining 8 English words via cycleNext -> zero skipped words across 10-word cycle
        val seenWords = mutableListOf(enDay1Morning.entry.word, enDay2.entry.word)
        repeat(8) { idx ->
            val stepped = WordOfTheDayService.resolveForLanguage(
                prefs = prefs,
                decks = allDecks,
                language = "English",
                cycleNext = true,
                nowMillis = day2AfterMidnightUtc + (idx + 1) * 1000L
            ) as WordOfTheDayUiState.Ready
            seenWords.add(stepped.entry.word)
        }
        assertEquals(10, seenWords.distinct().size)

        // 11th word wraps cleanly into Cycle 2 without repeating the 10th word immediately
        val cycle2First = WordOfTheDayService.resolveForLanguage(
            prefs = prefs,
            decks = allDecks,
            language = "English",
            cycleNext = true,
            nowMillis = day2AfterMidnightUtc + 20_000L
        ) as WordOfTheDayUiState.Ready
        assertEquals(2, cycle2First.cycleNumber)
        assertEquals(1, cycle2First.seenInCycleCount)
        assertTrue(cycle2First.entry.word != seenWords.last())
        assertTrue(WordOfTheDayService.getLanguageHistory(prefs, "English").size >= 11)
    }

    @Test
    fun mixedVocabularyCompilation_buildsHierarchyAndSyncsRatingsBookmarksAndDeletes() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("examset_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = MainViewModel(app)

        val a1Deck = vm.commitVocabularyImport(
            targetDeckId = null,
            title = "English A1 Core",
            language = "English",
            translationLanguage = "",
            levelCode = "A1",
            customLevelLabel = "",
            domain = "Everyday",
            analysis = VocabularyEngine.analyzeVocabularyImport(
                parseResult = VocabularyParseResult(
                    entries = listOf(
                        VocabularyEntry(id = "e_a1_1", word = "Candid", meaning = "Truthful and straightforward.", levelCode = "A1"),
                        VocabularyEntry(id = "e_a1_2", word = "Serene", meaning = "Calm and peaceful.", levelCode = "A1")
                    ),
                    detectedFormat = VocabularyInputFormat.JSON
                ),
                targetDeck = null,
                targetLanguage = "English",
                allDecks = emptyList()
            ),
            decisions = emptyMap(),
            languageVariant = "UK"
        )

        val c1Deck = vm.commitVocabularyImport(
            targetDeckId = null,
            title = "English C1 Academic",
            language = "English",
            translationLanguage = "",
            levelCode = "C1",
            customLevelLabel = "",
            domain = "Academic",
            analysis = VocabularyEngine.analyzeVocabularyImport(
                parseResult = VocabularyParseResult(
                    entries = listOf(
                        VocabularyEntry(id = "e_c1_1", word = "Cogent", meaning = "Clear, logical, and convincing.", levelCode = "C1"),
                        VocabularyEntry(id = "e_c1_dup", word = "Candid", meaning = "Duplicate across levels.", levelCode = "C1")
                    ),
                    detectedFormat = VocabularyInputFormat.JSON
                ),
                targetDeck = null,
                targetLanguage = "English",
                allDecks = emptyList()
            ),
            decisions = emptyMap(),
            languageVariant = "UK"
        )

        val langSummaries = VocabularyEngine.buildLanguageFolderSummaries(
            decks = vm.vocabularyDecks.value,
            srsByDeck = vm.vocabularySrsByDeck.value
        )
        assertEquals(1, langSummaries.size)
        assertEquals("English", langSummaries.single().language)
        assertEquals(2, langSummaries.single().levelCount)
        assertEquals(3, langSummaries.single().uniqueWords)

        val levelSummaries = VocabularyEngine.buildLevelFolderSummaries(
            language = "English",
            decks = vm.vocabularyDecks.value,
            srsByDeck = vm.vocabularySrsByDeck.value
        )
        assertEquals(listOf("A1", "C1"), levelSummaries.map { it.levelCode })

        val compiled = VocabularyEngine.compileMixedVocabularyDeck(
            language = "English",
            decks = vm.vocabularyDecks.value,
            srsByDeck = vm.vocabularySrsByDeck.value
        )
        assertEquals(3, compiled.virtualDeck.entries.size)
        assertEquals(listOf("Candid", "Cogent", "Serene"), compiled.virtualDeck.entries.map { it.word })

        // Write-back SRS rating & bookmark via originDeckId updates the real underlying decks
        val cogentEntry = compiled.virtualDeck.entries.first { it.word == "Cogent" }
        val cogentOriginDeckId = compiled.sourceByEntryId[cogentEntry.id]!!.originDeckId
        assertEquals(c1Deck.id, cogentOriginDeckId)

        vm.rateVocabularyEntry(cogentOriginDeckId, cogentEntry.id, SrsRating.GOOD)
        vm.toggleVocabularyEntryBookmark(cogentOriginDeckId, cogentEntry.id)

        val updatedCompiled = VocabularyEngine.compileMixedVocabularyDeck(
            language = "English",
            decks = vm.vocabularyDecks.value,
            srsByDeck = vm.vocabularySrsByDeck.value
        )
        assertNotNull(updatedCompiled.srsByEntryId[cogentEntry.id])
        assertTrue(updatedCompiled.virtualDeck.entries.first { it.id == cogentEntry.id }.isBookmarked)
        assertTrue(vm.vocabularyDecks.value.first { it.id == c1Deck.id }.entries.first { it.id == cogentEntry.id }.isBookmarked)

        // Resetting SRS for the language clears both A1 and C1 SRS maps
        vm.rateVocabularyEntry(a1Deck.id, "e_a1_1", SrsRating.EASY)
        vm.resetVocabularyLanguageSrs("English")
        assertTrue(vm.vocabularySrsByDeck.value.isEmpty())
    }

    @Test
    fun trustedTimeManager_persistsSameBootOffsetAndEnforcesMonotonicFloorAcrossReboots() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("examset_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = ExamSetPreferences(app)

        try {
            TrustedTimeManager.resetForTesting(app)
            val futureNetworkEpoch = System.currentTimeMillis() + 5 * 86_400_000L
            val bootElapsedAtSync = 10_000L
            val bootCount = 42

            TrustedTimeManager.recordVerifiedNetworkEpoch(
                estimatedEpochMillis = futureNetworkEpoch,
                elapsedAtSyncMillis = bootElapsedAtSync,
                bootCountOverride = bootCount
            )
            assertTrue(TrustedTimeManager.isNetworkTimeLocked())

            // Simulate process kill & offline relaunch during the SAME device boot (elapsedRealtime advanced)
            TrustedTimeManager.resetForTesting(clearPrefsContext = null)
            assertFalse(TrustedTimeManager.isNetworkTimeLocked())

            val elapsedLaterSameBoot = 70_000L
            TrustedTimeManager.restoreFromPreferences(
                context = app,
                elapsedNowOverride = elapsedLaterSameBoot,
                bootCountOverride = bootCount
            )
            assertTrue(TrustedTimeManager.isNetworkTimeLocked())
            assertTrue(TrustedTimeManager.getTrustedTimeMillis() >= futureNetworkEpoch + 60_000L)

            // Simulate device REBOOT (bootCount incremented, elapsedRealtime reset) while offline with older system clock
            TrustedTimeManager.resetForTesting(clearPrefsContext = null)
            TrustedTimeManager.restoreFromPreferences(
                context = app,
                elapsedNowOverride = 2_000L,
                bootCountOverride = bootCount + 1
            )
            assertFalse(TrustedTimeManager.isNetworkTimeLocked())
            // Monotonic floor still prevents falling back behind the last known trusted UTC timestamp
            assertTrue(TrustedTimeManager.getTrustedTimeMillis() >= futureNetworkEpoch + 60_000L)

            // Verify WordOfTheDayService refuses to regress UTC date backward on clock rollback
            val deck = VocabularyDeck(
                id = "en_floor",
                title = "Floor Check",
                language = "English",
                levelCode = "B1",
                entries = (1..10).map { idx ->
                    VocabularyEntry(
                        id = "fl_$idx",
                        word = "Word${idx.toString().padStart(2, '0')}",
                        levelCode = "B1",
                        language = "English",
                        meaning = "Def $idx"
                    )
                }
            )
            val lockedDayMillis = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse("2026-11-20T12:00:00Z")!!.time

            val initial = WordOfTheDayService.resolveForLanguage(
                prefs = prefs,
                decks = listOf(deck),
                language = "English",
                nowMillis = lockedDayMillis
            ) as WordOfTheDayUiState.Ready
            assertEquals("2026-11-20", initial.utcDateKey)

            val rolledBackMillis = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse("2024-01-01T12:00:00Z")!!.time

            val afterRollback = WordOfTheDayService.resolveForLanguage(
                prefs = prefs,
                decks = listOf(deck),
                language = "English",
                nowMillis = rolledBackMillis
            ) as WordOfTheDayUiState.Ready
            assertEquals("2026-11-20", afterRollback.utcDateKey)
            assertEquals(initial.entry.word, afterRollback.entry.word)
        } finally {
            TrustedTimeManager.resetForTesting(app)
        }
    }

    @Test
    fun parseVocabularyInput_overridesMismatchedScriptTagAndParsesBilingualTags() {
        val mismatchedJson = """
            [
              {
                "word": "संकल्प",
                "language": "English",
                "level": "B2",
                "meaning": "Firm determination or resolve to accomplish a goal.",
                "examples": [{"context": "Everyday", "sentence": "दृढ़ संकल्प से कठिन लक्ष्य भी पूरे होते हैं।"}]
              },
              {
                "word": "一期一会",
                "language": "English",
                "level": "JLPT N2",
                "meaning": "Cherishing each unique encounter as once in a lifetime.",
                "examples": [{"context": "Culture", "sentence": "一期一会の出会いを大切にする。"}]
              },
              {
                "word": "Efímero",
                "language": "Spanish -> English",
                "level": "C1",
                "meaning": "Lasting for a very short time.",
                "examples": [{"context": "Literary", "sentence": "La fama en internet suele ser efímera."}]
              },
              {
                "word": "स्वावलंबी",
                "language": "Hindi / English",
                "level": "B2",
                "meaning": "Self-reliant and independent in work or thought.",
                "examples": [{"context": "Formal", "sentence": "स्वावलंबी समाज आत्मविश्वास से आगे बढ़ता है।"}]
              }
            ]
        """.trimIndent()

        val result = VocabularyEngine.parseVocabularyInput(mismatchedJson)
        assertEquals(4, result.entries.size)

        val hindiFirst = result.entries[0]
        assertEquals("Hindi", hindiFirst.language)
        assertEquals("English", hindiFirst.translationLanguage)

        val jpSecond = result.entries[1]
        assertEquals("Japanese", jpSecond.language)
        assertEquals("English", jpSecond.translationLanguage)

        val esThird = result.entries[2]
        assertEquals("Spanish", esThird.language)
        assertEquals("English", esThird.translationLanguage)

        val hiFourth = result.entries[3]
        assertEquals("Hindi", hiFourth.language)
        assertEquals("English", hiFourth.translationLanguage)

        assertTrue(result.warnings.any { it.contains("Auto-corrected language from English to Hindi") })
        assertTrue(result.warnings.any { it.contains("Auto-corrected language from English to Japanese") })

        // Structured text bilingual header & translation language support
        val structuredBilingual = """
            LANGUAGE: Japanese -> English
            WORD: 木漏れ日
            LEVEL: JLPT N1
            MEANING: Sunlight filtering gently through the leaves of trees.
            EXAMPLES:
            - [Literary] 森の中で木漏れ日を浴びながら歩いた。
        """.trimIndent()
        val structuredResult = VocabularyEngine.parseVocabularyInput(structuredBilingual)
        assertEquals(1, structuredResult.entries.size)
        assertEquals("Japanese", structuredResult.entries.single().language)
        assertEquals("English", structuredResult.entries.single().translationLanguage)
    }

    @Test
    fun smartRouting_detectsMeaningOnlyBilingualEntriesWithoutCrossBleedAndBackfillsExistingDeck() {
        val mixedBilingualBatch = """
            [
              {
                "word": "Pragmatic",
                "partOfSpeech": "Adjective",
                "level": "C1",
                "meaning": "व्यावहारिक और यथार्थवादी दृष्टिकोण अपनाने वाला।",
                "examples": [{"context": "Workplace", "sentence": "We took a pragmatic approach to the project timeline."}]
              },
              {
                "word": "木漏れ日",
                "partOfSpeech": "Noun",
                "level": "JLPT N1",
                "meaning": "The interplay of sunlight filtering through the leaves of trees.",
                "examples": [{"context": "Literary", "sentence": "木漏れ日が小道を柔らかく照らしていた。"}]
              },
              {
                "word": "Efímero",
                "language": "Spanish",
                "partOfSpeech": "Adjective",
                "level": "C1",
                "meaning": "Que dura muy poco tiempo.",
                "examples": [{"context": "Editorial", "sentence": "El éxito mediático suele ser efímero."}]
              }
            ]
        """.trimIndent()

        val parsed = VocabularyEngine.parseVocabularyInput(mixedBilingualBatch, preferredEnglishVariant = "UK")
        assertEquals(3, parsed.entries.size)

        val enEntry = parsed.entries[0]
        assertEquals("English", enEntry.language)
        assertEquals("Hindi", enEntry.translationLanguage)

        val jpEntry = parsed.entries[1]
        assertEquals("Japanese", jpEntry.language)
        assertEquals("English", jpEntry.translationLanguage)

        // Short monolingual Spanish meaning must NOT falsely trigger translationLanguage = "English"
        val esEntry = parsed.entries[2]
        assertEquals("Spanish", esEntry.language)
        assertEquals("", esEntry.translationLanguage)

        // Existing English C1 deck initially has blank translationLanguage
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("examset_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = MainViewModel(app)
        val initialEnDeck = vm.commitVocabularyImport(
            targetDeckId = null,
            title = "Oxford C1 General",
            language = "English",
            translationLanguage = "",
            levelCode = "C1",
            customLevelLabel = "",
            domain = "General",
            analysis = VocabularyEngine.analyzeVocabularyImport(
                parseResult = VocabularyParseResult(
                    entries = listOf(VocabularyEntry(word = "Cogent", meaning = "Clear and convincing.", levelCode = "C1")),
                    detectedFormat = VocabularyInputFormat.JSON
                ),
                targetDeck = null,
                targetLanguage = "English",
                allDecks = emptyList()
            ),
            decisions = emptyMap(),
            languageVariant = "UK"
        )
        assertEquals("", initialEnDeck.translationLanguage)

        val buckets = VocabularyEngine.buildSmartRoutingPlan(parsed, vm.vocabularyDecks.value, "UK")
        assertEquals(3, buckets.size)

        val enBucket = buckets.first { it.language == "English" }
        val jpBucket = buckets.first { it.language == "Japanese" }
        val esBucket = buckets.first { it.language == "Spanish" }

        // Per-bucket translation languages must remain isolated (no bleed of Hindi into Japanese/Spanish)
        assertEquals("Hindi", enBucket.translationLanguage)
        assertEquals("English", jpBucket.translationLanguage)
        assertEquals("", esBucket.translationLanguage)
        assertEquals(initialEnDeck.id, enBucket.recommendedDeckId)

        val choices = buckets.associate { b ->
            b.bucketKey to VocabularySmartRouteTargetChoice(
                bucketKey = b.bucketKey,
                targetDeckId = b.recommendedDeckId,
                customTitle = b.defaultNewTitle,
                language = b.language,
                languageVariant = b.languageVariant,
                translationLanguage = b.translationLanguage,
                levelCode = b.levelCode,
                customLevelLabel = b.customLevelLabel
            )
        }
        val routeAnalysis = VocabularyEngine.analyzeSmartRoutedImport(
            parseResult = parsed,
            buckets = buckets,
            choices = choices,
            allDecks = vm.vocabularyDecks.value
        )
        vm.commitSmartRoutedVocabularyImport(
            targets = choices.values.toList(),
            entryBucketById = routeAnalysis.entryBucketById,
            analysis = routeAnalysis.analysis,
            decisions = emptyMap()
        )

        val updatedEnDeck = vm.vocabularyDecks.value.first { it.id == initialEnDeck.id }
        assertEquals("Hindi", updatedEnDeck.translationLanguage)
        assertTrue(updatedEnDeck.entries.any { it.word == "Pragmatic" && it.translationLanguage == "Hindi" })

        val createdJpDeck = vm.vocabularyDecks.value.first { it.language == "Japanese" }
        assertEquals("English", createdJpDeck.translationLanguage)

        val createdEsDeck = vm.vocabularyDecks.value.first { it.language == "Spanish" }
        assertEquals("", createdEsDeck.translationLanguage)
    }

    @Test
    fun wordOfTheDayService_formatsUtcCountdownAndMergesPersistedStates() {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val ts = formatter.parse("2026-11-20T21:15:00Z")!!.time
        assertEquals("Next in 2h 45m UTC", WordOfTheDayService.formatUtcCountdownLabel(ts))

        val existing = LanguageWordOfTheDayState(
            languageKey = "english",
            activeUtcDateKey = "2026-11-19",
            activeWordKey = "alpha",
            seenWordKeysInCycle = listOf("alpha"),
            cycleNumber = 1,
            history = listOf(
                WordOfTheDayHistoryItem(utcDateKey = "2026-11-19", wordKey = "alpha", word = "Alpha")
            )
        )
        val incoming = LanguageWordOfTheDayState(
            languageKey = "english",
            activeUtcDateKey = "2026-11-20",
            activeWordKey = "beta",
            seenWordKeysInCycle = listOf("alpha", "beta"),
            cycleNumber = 1,
            history = listOf(
                WordOfTheDayHistoryItem(utcDateKey = "2026-11-20", wordKey = "beta", word = "Beta"),
                WordOfTheDayHistoryItem(utcDateKey = "2026-11-19", wordKey = "alpha", word = "Alpha")
            )
        )
        val existingJson = org.json.JSONObject().apply { put("english", existing.toJson()) }.toString()
        val incomingJson = org.json.JSONObject().apply { put("english", incoming.toJson()) }.toString()

        val mergedJson = WordOfTheDayService.mergePersistedLanguageStatesJson(existingJson, incomingJson)
        val mergedMap = WordOfTheDayService.parseStatesJson(mergedJson)
        val en = mergedMap["english"]!!
        assertEquals("2026-11-20", en.activeUtcDateKey)
        assertEquals("beta", en.activeWordKey)
        assertEquals(listOf("alpha", "beta"), en.seenWordKeysInCycle)
        assertEquals(2, en.history.size)
    }

    @Test
    fun vocabularyEngine_compilesAllDueAcrossLanguagesAndFindsGlobalWordHits() {
        val now = 1_700_000_000_000L
        val enDeck = VocabularyDeck(
            id = "en_1",
            title = "English Core",
            language = "English",
            levelCode = "B1",
            entries = listOf(
                VocabularyEntry(id = "e1", word = "Lucid", meaning = "Clear and easy to understand", levelCode = "B1"),
                VocabularyEntry(id = "e2", word = "MasteredWord", meaning = "Already learned", levelCode = "B1")
            )
        )
        val hiDeck = VocabularyDeck(
            id = "hi_1",
            title = "Hindi Essentials",
            language = "Hindi",
            levelCode = "A2",
            entries = listOf(
                VocabularyEntry(id = "h1", word = "स्पष्ट", meaning = "Clear and explicit", translation = "Clear", levelCode = "A2")
            )
        )
        val srsByDeck = mapOf(
            "en_1" to mapOf(
                "e2" to FlashcardSrsState(
                    blockIndex = 0,
                    repetitions = 4,
                    easeFactor = 2.6f,
                    intervalDays = 21,
                    dueAtMillis = now + 7 * 86_400_000L,
                    lastReviewedAtMillis = now - 86_400_000L,
                    reviewCount = 4,
                    correctCount = 4
                )
            )
        )

        val compiledDue = VocabularyEngine.compileAllDueVocabularyDeck(
            decks = listOf(enDeck, hiDeck),
            srsByDeck = srsByDeck,
            nowMillis = now
        )
        assertEquals(2, compiledDue.virtualDeck.entries.size)
        assertEquals("en_1", compiledDue.sourceByEntryId["e1"]?.originDeckId)
        assertEquals("hi_1", compiledDue.sourceByEntryId["h1"]?.originDeckId)
        assertFalse(compiledDue.virtualDeck.entries.any { it.id == "e2" })

        val hits = VocabularyEngine.searchEntriesAcrossDecks(listOf(enDeck, hiDeck), "clear")
        assertEquals(2, hits.size)
    }

    @Test
    fun vocabularyTtsSpeaker_resolvesExpectedLocales() {
        assertEquals(Locale.US, VocabularyTtsSpeaker.resolveLocale("English", "US"))
        assertEquals(Locale.UK, VocabularyTtsSpeaker.resolveLocale("English", "UK"))
        assertEquals(Locale.forLanguageTag("en-IN"), VocabularyTtsSpeaker.resolveLocale("English", "IN"))
        assertEquals(Locale.forLanguageTag("hi-IN"), VocabularyTtsSpeaker.resolveLocale("Hindi"))
        assertEquals(Locale.JAPANESE, VocabularyTtsSpeaker.resolveLocale("Japanese"))
    }

    @Test
    fun promptBuilder_adaptsDifficultyToTargetLanguageAndForbidsLiteralMixedOrCustomLevels() {
        val jpMixedState = VocabularyPromptState().apply {
            language = "Japanese"
            levelCode = VocabularyLevel.MIXED.code
            outputFormat = "Strict JSON"
        }
        val jpPrompt = jpMixedState.buildPrompt()
        assertTrue(jpPrompt.contains("JLPT"))
        assertTrue(jpPrompt.contains("\"level\": \"A1 | A2 | B1 | B2 | C1 | C2\""))
        assertTrue(jpPrompt.contains("NEVER write \"MIXED\""))
        assertFalse(jpPrompt.contains("\"level\": \"MIXED\""))

        val hiCustomState = VocabularyPromptState().apply {
            language = "Hindi"
            levelCode = VocabularyLevel.CUSTOM.code
            customLevelLabel = "UPSC Essay"
            outputFormat = "Structured Text"
        }
        val hiPrompt = hiCustomState.buildPrompt()
        assertTrue(hiPrompt.contains("register progression"))
        assertTrue(hiPrompt.contains("Curriculum / Track Focus: UPSC Essay"))
        assertTrue(hiPrompt.contains("LEVEL: [A1 | A2 | B1 | B2 | C1 | C2]"))
        assertFalse(hiPrompt.contains("LEVEL: [CUSTOM]"))
        assertFalse(hiPrompt.contains("LEVEL: [UPSC Essay]"))
    }

    @Test
    fun parseAndRoute_stripsLiteralMixedOrCustomAndConsolidatesMultiLevelFolders() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("examset_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = MainViewModel(app)

        val aiBatchJson = """
            [
              {"word": "Brisk", "level": "A1", "meaning": "Quick and energetic.", "examples": [{"context": "Everyday", "sentence": "We went for a brisk morning walk."}]},
              {"word": "Ambiguous", "level": "MIXED", "meaning": "Open to more than one interpretation.", "examples": [{"context": "Workplace", "sentence": "The policy wording was ambiguous."}]},
              {"word": "Prudent", "level": "B2", "meaning": "Acting with care and thought for the future.", "examples": [{"context": "Finance", "sentence": "It is prudent to keep an emergency reserve."}]},
              {"word": "Ephemeral", "level": "C1", "meaning": "Lasting for a very short time.", "examples": [{"context": "Editorial", "sentence": "Digital trends are often ephemeral."}]},
              {"word": "Candid", "level": "custom", "meaning": "Truthful and straightforward.", "examples": [{"context": "Conversation", "sentence": "Thank you for your candid feedback."}]}
            ]
        """.trimIndent()

        val parsed = VocabularyEngine.parseVocabularyInput(
            rawInput = aiBatchJson,
            defaultLevelCode = VocabularyLevel.INTERMEDIATE.code,
            preferredEnglishVariant = "UK"
        )
        assertEquals(5, parsed.entries.size)
        assertTrue(parsed.entries.none { it.levelCode.equals("MIXED", ignoreCase = true) })
        assertTrue(parsed.entries.none { it.levelCode.equals("CUSTOM", ignoreCase = true) && it.customLevelLabel.isBlank() })

        val buckets = VocabularyEngine.buildSmartRoutingPlan(parsed, vm.vocabularyDecks.value, "UK")
        assertEquals(listOf("A1", "B1", "B2", "C1"), buckets.map { it.levelCode })
        assertTrue(buckets.none { it.levelCode == VocabularyLevel.MIXED.code })

        val choices = buckets.associate { b ->
            b.bucketKey to VocabularySmartRouteTargetChoice(
                bucketKey = b.bucketKey,
                targetDeckId = b.recommendedDeckId,
                customTitle = b.defaultNewTitle,
                language = b.language,
                languageVariant = b.languageVariant,
                translationLanguage = b.translationLanguage,
                levelCode = b.levelCode,
                customLevelLabel = b.customLevelLabel
            )
        }
        val routeAnalysis = VocabularyEngine.analyzeSmartRoutedImport(
            parseResult = parsed,
            buckets = buckets,
            choices = choices,
            allDecks = vm.vocabularyDecks.value
        )
        vm.commitSmartRoutedVocabularyImport(
            targets = choices.values.toList(),
            entryBucketById = routeAnalysis.entryBucketById,
            analysis = routeAnalysis.analysis,
            decisions = emptyMap()
        )

        val levelSummaries = VocabularyEngine.buildLevelFolderSummaries("English", vm.vocabularyDecks.value, vm.vocabularySrsByDeck.value)
        assertEquals(listOf("A1", "B1", "B2", "C1"), levelSummaries.map { it.levelCode })

        // Subsequent manual import into B2 without targetDeckId consolidates into existing B2 deck rather than spawning a duplicate
        val secondB2Analysis = VocabularyEngine.analyzeVocabularyImport(
            parseResult = VocabularyParseResult(
                entries = listOf(VocabularyEntry(word = "Resilient", meaning = "Able to recover quickly.", levelCode = "B2")),
                detectedFormat = VocabularyInputFormat.STRUCTURED_TEXT
            ),
            targetDeck = null,
            targetLanguage = "English",
            allDecks = vm.vocabularyDecks.value
        )
        vm.commitVocabularyImport(
            targetDeckId = null,
            title = "",
            language = "English",
            translationLanguage = "",
            levelCode = "B2",
            customLevelLabel = "",
            domain = "General",
            analysis = secondB2Analysis,
            decisions = emptyMap(),
            languageVariant = "UK"
        )
        val b2Decks = vm.vocabularyDecks.value.filter { it.language == "English" && it.levelCode == "B2" }
        assertEquals(1, b2Decks.size)
        assertEquals(2, b2Decks.single().entries.size)
    }

    @Test
    fun legacyMixedDeckMigration_splitsEntriesIntoExactLevelFoldersAndWritesBackSafely() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("examset_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = ExamSetPreferences(app)

        val legacyDeck = VocabularyDeck(
            id = "legacy_mixed_1",
            title = "Legacy Mixed Pack",
            language = "English",
            levelCode = VocabularyLevel.MIXED.code,
            entries = listOf(
                VocabularyEntry(id = "lg_a1", word = "Brisk", meaning = "Quick", levelCode = "A1"),
                VocabularyEntry(id = "lg_b2", word = "Prudent", meaning = "Careful", levelCode = "B2"),
                VocabularyEntry(id = "lg_c1", word = "Cogent", meaning = "Convincing", levelCode = "C1"),
                VocabularyEntry(id = "lg_untagged", word = "LegacyWord", meaning = "From older MIXED tag", levelCode = "MIXED")
            )
        )
        val legacySrs = mapOf(
            "legacy_mixed_1" to mapOf(
                "lg_a1" to FlashcardSrsState(blockIndex = 0, repetitions = 3, intervalDays = 10),
                "lg_b2" to FlashcardSrsState(blockIndex = 0, repetitions = 2, intervalDays = 6),
                "lg_c1" to FlashcardSrsState(blockIndex = 0, repetitions = 1, intervalDays = 1)
            )
        )
        prefs.saveVocabularyDecks(listOf(legacyDeck))
        prefs.saveAllVocabularySrsMaps(legacySrs)

        val vm = MainViewModel(app)
        val summaries = VocabularyEngine.buildLevelFolderSummaries(
            language = "English",
            decks = vm.vocabularyDecks.value,
            srsByDeck = vm.vocabularySrsByDeck.value
        )
        // Legacy MIXED deck never appears as a "MIXED" folder; untagged entry routes cleanly into B1
        assertEquals(listOf("A1", "B1", "B2", "C1"), summaries.map { it.levelCode })
        assertTrue(summaries.none { it.levelCode == VocabularyLevel.MIXED.code })

        // Compiled B2 level folder maps back to original legacy_mixed_1 deck and preserves SRS
        val compiledB2 = VocabularyEngine.compileLevelVocabularyDeck(
            language = "English",
            levelCode = "B2",
            decks = vm.vocabularyDecks.value,
            srsByDeck = vm.vocabularySrsByDeck.value
        )
        assertEquals(listOf("Prudent"), compiledB2.virtualDeck.entries.map { it.word })
        assertEquals("legacy_mixed_1", compiledB2.sourceByEntryId["lg_b2"]?.originDeckId)
        assertNotNull(compiledB2.srsByEntryId["lg_b2"])

        // Resetting B2 progress affects ONLY lg_b2 inside legacy_mixed_1
        vm.resetVocabularyLevelSrs("English", "B2")
        val srsAfterB2Reset = vm.vocabularySrsByDeck.value["legacy_mixed_1"].orEmpty()
        assertFalse(srsAfterB2Reset.containsKey("lg_b2"))
        assertTrue(srsAfterB2Reset.containsKey("lg_a1"))
        assertTrue(srsAfterB2Reset.containsKey("lg_c1"))

        // Deleting C1 level folder removes ONLY lg_c1 while keeping A1, B1, B2 in legacy_mixed_1 intact
        vm.deleteVocabularyLevelFolder("English", "C1")
        val remainingEntries = vm.vocabularyDecks.value.first { it.id == "legacy_mixed_1" }.entries
        assertEquals(setOf("lg_a1", "lg_b2", "lg_untagged"), remainingEntries.map { it.id }.toSet())
        assertFalse(vm.vocabularySrsByDeck.value["legacy_mixed_1"].orEmpty().containsKey("lg_c1"))
        assertTrue(vm.vocabularySrsByDeck.value["legacy_mixed_1"].orEmpty().containsKey("lg_a1"))
    }

    @Test
    fun vocabularyLevel_resolvesLanguageSpecificDescriptors() {
        // Japanese (JLPT)
        assertEquals("JLPT N5 (Basic Survival)", VocabularyLevel.BEGINNER.displayTitleForLanguage("Japanese"))
        assertEquals("JLPT N3", VocabularyLevel.INTERMEDIATE.compactBadgeForLanguage("Japanese"))
        assertEquals("JLPT N1+ (Literary & Nuanced)", VocabularyLevel.MASTERY.displayTitleForLanguage("Japanese"))

        // Mandarin Chinese (HSK)
        assertEquals("HSK 1 (Foundational Survival)", VocabularyLevel.BEGINNER.displayTitleForLanguage("Chinese"))
        assertEquals("HSK 4", VocabularyLevel.INTERMEDIATE.compactBadgeForLanguage("Mandarin"))
        assertEquals("HSK 7–9 (Classical & Mastery)", VocabularyLevel.MASTERY.displayTitleForLanguage("Chinese"))

        // Korean (TOPIK)
        assertEquals("TOPIK I-1 (Beginner Survival)", VocabularyLevel.BEGINNER.displayTitleForLanguage("Korean"))
        assertEquals("TOPIK 4", VocabularyLevel.UPPER_INTERMEDIATE.compactBadgeForLanguage("Korean"))

        // Sanskrit
        assertEquals("Prathamā • प्रथमा (Roots & Gita Basics)", VocabularyLevel.BEGINNER.displayTitleForLanguage("Sanskrit"))
        assertEquals("शास्त्री", VocabularyLevel.INTERMEDIATE.compactBadgeForLanguage("Sanskrit"))
        assertEquals("Vaidika • वैदिक (Vedic & Arsha Nuances)", VocabularyLevel.MASTERY.displayTitleForLanguage("Sanskrit"))

        // Hindi / Indic registers
        assertEquals("Everyday Spoken • बोलचाल (A1)", VocabularyLevel.BEGINNER.displayTitleForLanguage("Hindi"))
        assertEquals("सामान्य", VocabularyLevel.INTERMEDIATE.compactBadgeForLanguage("Hindi"))
        assertEquals("Literary & Nuanced • साहित्यिक (C2)", VocabularyLevel.MASTERY.displayTitleForLanguage("Hindi"))

        // Arabic (MSA)
        assertEquals("Survival MSA • تواصل أولي (A1)", VocabularyLevel.BEGINNER.displayTitleForLanguage("Arabic"))
        assertEquals("إعلامي", VocabularyLevel.INTERMEDIATE.compactBadgeForLanguage("Arabic"))

        // European / Universal (English, Spanish, German, etc.)
        assertEquals("Beginner (A1)", VocabularyLevel.BEGINNER.displayTitleForLanguage("English"))
        assertEquals("B1", VocabularyLevel.INTERMEDIATE.compactBadgeForLanguage("Spanish"))
    }

    @Test
    fun promptBuilder_generatesLanguageSpecificTargetProficiency() {
        val jpState = VocabularyPromptState().apply {
            language = "Japanese"
            levelCode = "B1"
            outputFormat = "Strict JSON"
        }
        val jpPrompt = jpState.buildPrompt()
        assertTrue(jpPrompt.contains("Target Proficiency Level: JLPT N3 (Practical Fluency) (standardized level code: B1)"))
        assertTrue(jpPrompt.contains("JLPT progression"))

        val saState = VocabularyPromptState().apply {
            language = "Sanskrit"
            levelCode = "B2"
            outputFormat = "Structured Text"
        }
        val saPrompt = saState.buildPrompt()
        assertTrue(saPrompt.contains("Target Proficiency Level: Kāvya • काव्य (Classical Literature) (standardized level code: B2)"))

        val zhState = VocabularyPromptState().apply {
            language = "Chinese"
            levelCode = "C1"
            outputFormat = "Strict JSON"
        }
        val zhPrompt = zhState.buildPrompt()
        assertTrue(zhPrompt.contains("Target Proficiency Level: HSK 6 (Advanced Academic) (standardized level code: C1)"))
    }

    @Test
    fun vocabularyEngine_buildLevelFolderSummaries_usesLanguageSpecificTitles() {
        val jpDeck = VocabularyDeck(
            id = "jp_core",
            title = "Japanese Core",
            language = "Japanese",
            levelCode = "B1",
            entries = listOf(
                VocabularyEntry(id = "j1", word = "食べる", meaning = "To eat", levelCode = "A1"),
                VocabularyEntry(id = "j2", word = "準備", meaning = "Preparation", levelCode = "B1"),
                VocabularyEntry(id = "j3", word = "抽象的", meaning = "Abstract", levelCode = "C1")
            )
        )
        val summaries = VocabularyEngine.buildLevelFolderSummaries(
            language = "Japanese",
            decks = listOf(jpDeck),
            srsByDeck = emptyMap()
        )
        assertEquals(3, summaries.size)
        assertEquals("JLPT N5 (Basic Survival)", summaries[0].levelTitle)
        assertEquals("JLPT N3 (Practical Fluency)", summaries[1].levelTitle)
        assertEquals("JLPT N1 (Formal & Abstract)", summaries[2].levelTitle)
        assertEquals("JLPT N5", summaries[0].compactBadge)
        assertEquals("JLPT N3", summaries[1].compactBadge)
        assertEquals("JLPT N1", summaries[2].compactBadge)
    }

    @Test
    fun parseQuickCaptureHeadwords_supportsDelimitersAndParenthesisHints() {
        val raw = """
            ephemeral, sanction (penalty); obfuscate | - ubiquitous
            • lucid (clear expression)
        """.trimIndent()

        val items = VocabularyEngine.parseQuickCaptureHeadwords(
            rawInput = raw,
            defaultLanguage = "English",
            defaultLevelCode = "C1"
        )
        assertEquals(5, items.size)
        assertEquals("ephemeral", items[0].word)
        assertEquals("", items[0].utilityReason)
        assertEquals("sanction", items[1].word)
        assertEquals("Context hint: penalty", items[1].utilityReason)
        assertEquals("obfuscate", items[2].word)
        assertEquals("ubiquitous", items[3].word)
        assertEquals("lucid", items[4].word)
        assertEquals("Context hint: clear expression", items[4].utilityReason)
        assertTrue(items.all { it.language == "English" && it.levelCode == "C1" })
    }

    @Test
    fun parseHeadwordsJson_handlesFencedJsonWrappedObjectsAndStringArrays() {
        val fencedJson = """
            ```json
            [
              {"word": "Ephemeral", "level": "C1", "pos": "adjective", "domain": "Editorial", "utilityReason": "Common in trend analysis"},
              {"headword": "Cogent", "levelCode": "C2", "partOfSpeech": "adjective", "language": "English"}
            ]
            ```
        """.trimIndent()
        val parsedFenced = VocabularyEngine.parseHeadwordsJson(fencedJson, defaultLanguage = "English")
        assertEquals(2, parsedFenced.size)
        assertEquals("Ephemeral", parsedFenced[0].word)
        assertEquals("C1", parsedFenced[0].levelCode)
        assertEquals("adjective", parsedFenced[0].partOfSpeech)
        assertEquals("Editorial", parsedFenced[0].domain)
        assertEquals("Cogent", parsedFenced[1].word)
        assertEquals("C2", parsedFenced[1].levelCode)

        val wrappedJson = """{"headwords": ["Pragmatic", "Ambiguous"]}"""
        val parsedWrapped = VocabularyEngine.parseHeadwordsJson(wrappedJson, defaultLanguage = "English")
        assertEquals(listOf("Pragmatic", "Ambiguous"), parsedWrapped.map { it.word })
        assertTrue(parsedWrapped.all { it.language == "English" && it.levelCode == "B2" })
    }

    @Test
    fun filterNewHeadwords_deduplicatesAgainstDecksQueueDismissedAndIntraBatch() {
        val existingDecks = listOf(
            VocabularyDeck(
                id = "en_c1",
                title = "English • C1",
                language = "English",
                levelCode = "C1",
                entries = listOf(
                    VocabularyEntry(id = "e1", word = "Ephemeral", meaning = "Short-lived", levelCode = "C1")
                )
            ),
            VocabularyDeck(
                id = "de_b1",
                title = "German • B1",
                language = "German",
                levelCode = "B1",
                entries = listOf(
                    VocabularyEntry(id = "d1", word = "Gift", meaning = "Poison", levelCode = "B1")
                )
            )
        )
        val existingQueue = listOf(
            HeadwordQueueItem(id = "q1", word = "Lucid", language = "English", levelCode = "B2")
        )
        val dismissed = setOf(VocabularyEntry.normalizeVocabularyWordKey("Obsolete"))

        val incoming = listOf(
            HeadwordQueueItem(word = "ephemeral", language = "English", levelCode = "C1"), // in deck
            HeadwordQueueItem(word = "LUCID", language = "English", levelCode = "B2"),     // in queue
            HeadwordQueueItem(word = "obsolete", language = "English", levelCode = "B2"),  // dismissed
            HeadwordQueueItem(word = "Pragmatic", language = "English", levelCode = "C1"), // new
            HeadwordQueueItem(word = "Pragmatic", language = "English", levelCode = "C1"), // intra-batch duplicate
            HeadwordQueueItem(word = "Gift", language = "English", levelCode = "B1")       // same spelling, DIFFERENT language -> keep!
        )

        val res = VocabularyEngine.filterNewHeadwords(
            incoming = incoming,
            existingDecks = existingDecks,
            existingQueue = existingQueue,
            dismissedKeys = dismissed
        )

        assertEquals(listOf("Pragmatic", "Gift"), res.addedItems.map { it.word })
        assertEquals(1, res.skippedAlreadyInDeckCount)
        assertEquals(2, res.skippedAlreadyInQueueCount) // 1 existing + 1 intra-batch duplicate
        assertEquals(1, res.skippedDismissedCount)
        assertEquals(6, res.totalProcessedCount)
    }

    @Test
    fun vocabularyPromptConfig_andStage1Stage2Prompts_preserveConfigAndExcludeKnownWords() {
        val original = VocabularyPromptConfig(
            language = "English",
            levelCode = "C1",
            wordCount = 20,
            isHeadwordIndexOnly = true,
            utilityTier = VocabularyUtilityTier.LITERARY_PRECISION
        )
        val roundTripped = VocabularyPromptConfig.fromJson(original.toJson())
        assertEquals(true, roundTripped.isHeadwordIndexOnly)
        assertEquals(VocabularyUtilityTier.LITERARY_PRECISION, roundTripped.utilityTier)

        val stage1Prompt = VocabularyPromptState().apply {
            applyConfig(roundTripped)
            topic = "Philosophy & Ethics"
        }.buildPrompt(existingWordsToAvoid = listOf("Ephemeral", "Cogent"))

        assertTrue(stage1Prompt.contains("Stage-1 Headword Index"))
        assertTrue(stage1Prompt.contains(VocabularyUtilityTier.LITERARY_PRECISION.title))
        assertTrue(stage1Prompt.contains(VocabularyUtilityTier.LITERARY_PRECISION.subtitle))
        assertTrue(stage1Prompt.contains("Ephemeral"))
        assertTrue(stage1Prompt.contains("Cogent"))
        assertTrue(stage1Prompt.contains("\"utilityReason\""))

        val stage2Prompt = VocabularyEngine.buildStage2EnrichmentPrompt(
            items = listOf(
                HeadwordQueueItem(
                    word = "Sanction",
                    language = "English",
                    levelCode = "C1",
                    partOfSpeech = "noun/verb",
                    utilityReason = "Context hint: penalty"
                )
            ),
            targetLanguage = "English",
            nativeExplanationLanguage = "Hindi",
            preferredVariant = "UK"
        )
        assertTrue(stage2Prompt.contains("Sanction"))
        assertTrue(stage2Prompt.contains("Context hint: penalty"))
        assertTrue(stage2Prompt.contains("\"level\": \"A1 | A2 | B1 | B2 | C1 | C2\""))
        assertTrue(stage2Prompt.contains("\"translationLanguage\": \"Hindi\""))
    }

    @Test
    fun mainViewModel_headwordPipeline_storesSilentlyGraduatesOnEnrichmentAndStaysIsolatedFromWotd() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("examset_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = MainViewModel(app)

        // 1. Quick-capture + Stage-1 JSON import go silently into headwordInbox, not vocabularyDecks
        val quickRes = vm.addQuickHeadwordsToInbox("ephemeral, sanction (penalty)", "English", "C1")
        assertEquals(2, quickRes.addedItems.size)
        val jsonRes = vm.importHeadwordsJson(
            """[{"word": "Ephemeral", "level": "C1"}, {"word": "Lucid", "level": "B2"}, {"word": "Banal", "level": "C1"}]""",
            defaultLanguage = "English"
        )
        assertEquals(listOf("Lucid", "Banal"), jsonRes.addedItems.map { it.word })
        assertEquals(1, jsonRes.skippedAlreadyInQueueCount)
        assertTrue(vm.vocabularyDecks.value.isEmpty())
        assertEquals(4, vm.headwordInbox.value.size)

        // 2. Dismiss "Banal" permanently -> re-importing skips it
        val banalId = vm.headwordInbox.value.first { it.word == "Banal" }.id
        vm.dismissHeadword(banalId)
        assertEquals(3, vm.headwordInbox.value.size)
        val retryDismissed = vm.importHeadwordsJson("""["Banal"]""", "English")
        assertEquals(1, retryDismissed.skippedDismissedCount)
        assertEquals(0, retryDismissed.addedItems.size)

        // 3. Word of the Day ignores queued headwords & blank-meaning entries
        vm.loadWordOfTheDayForLanguage("English", cycleNext = false)
        assertFalse(vm.wordOfTheDayState.value is WordOfTheDayUiState.Ready)

        // 4. Existing avoidance list combines saved, queued, and dismissed words for that language
        val avoidList = vm.existingVocabularyAndQueuedWordsForLanguage("English").map { it.lowercase() }
        assertTrue("ephemeral" in avoidList)
        assertTrue("sanction" in avoidList)
        assertTrue("lucid" in avoidList)
        assertTrue("banal" in avoidList)

        // 5. Enrich Stage-2 cards directly from Inbox -> routes to (English, C1) & (English, B2) and graduates completed headwords!
        val enrichedJson = """
            [
              {
                "word": "Ephemeral",
                "language": "English",
                "level": "C1",
                "meaning": "Lasting for a very short time.",
                "examples": [{"context": "Editorial", "sentence": "Fame on social media is ephemeral."}]
              },
              {
                "word": "Lucid",
                "language": "English",
                "level": "B2",
                "meaning": "Expressed clearly and easy to understand.",
                "examples": [{"context": "Academic", "sentence": "She gave a lucid explanation."}]
              }
            ]
        """.trimIndent()
        val graduated = vm.importEnrichedStage2CardsFromInbox(enrichedJson, "English")
        assertEquals(2, graduated)

        // Both Ephemeral & Lucid promoted to real decks and removed from Inbox; Sanction stays queued
        assertEquals(listOf("sanction"), vm.headwordInbox.value.map { it.word.lowercase() })
        assertTrue(vm.vocabularyDecks.value.any { it.language == "English" && it.levelCode == "C1" && it.entries.any { e -> e.word == "Ephemeral" } })
        assertTrue(vm.vocabularyDecks.value.any { it.language == "English" && it.levelCode == "B2" && it.entries.any { e -> e.word == "Lucid" } })
    }
}



