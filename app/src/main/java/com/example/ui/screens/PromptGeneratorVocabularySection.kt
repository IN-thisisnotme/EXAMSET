package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VocabularyLanguageVariants
import com.example.model.VocabularyLevel
import com.example.model.VocabularyPromptConfig
import com.example.model.VocabularyUtilityTier
import com.example.ui.theme.ExamColorPalette

const val MAX_INLINE_VOCAB_EXCLUDE_WORDS = 100

class VocabularyPromptState {
    var language by mutableStateOf("English")
    var languageVariant by mutableStateOf(VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT)
    var translationLanguage by mutableStateOf("")
    var levelCode by mutableStateOf(VocabularyLevel.INTERMEDIATE.code)
    var customLevelLabel by mutableStateOf("")
    var domain by mutableStateOf("General")
    var topic by mutableStateOf("")
    var wordCount by mutableFloatStateOf(20f)
    var examplesPerWord by mutableIntStateOf(3)
    var outputFormat by mutableStateOf("Strict JSON")
    var includePronunciation by mutableStateOf(true)
    var includeSynonymsAndCollocations by mutableStateOf(true)
    var includeUsageNote by mutableStateOf(true)
    var excludeAlreadySavedWords by mutableStateOf(true)
    var isHeadwordIndexOnly by mutableStateOf(false)
    var utilityTier by mutableStateOf(VocabularyUtilityTier.EXAM_EDITORIAL)
    var customInstruction by mutableStateOf("")

    fun toConfig(): VocabularyPromptConfig = VocabularyPromptConfig(
        language = language,
        languageVariant = languageVariant,
        translationLanguage = translationLanguage,
        levelCode = levelCode,
        customLevelLabel = customLevelLabel,
        domain = domain,
        wordCount = wordCount.toInt(),
        examplesPerWord = examplesPerWord,
        outputFormat = outputFormat,
        includePronunciation = includePronunciation,
        includeSynonymsAndCollocations = includeSynonymsAndCollocations,
        includeUsageNote = includeUsageNote,
        isHeadwordIndexOnly = isHeadwordIndexOnly,
        utilityTier = utilityTier,
        customInstruction = customInstruction
    ).normalized()

    fun applyConfig(config: VocabularyPromptConfig, preserveTopic: Boolean = true) {
        val c = config.normalized()
        language = c.language
        languageVariant = c.languageVariant
        translationLanguage = c.translationLanguage
        levelCode = c.levelCode
        customLevelLabel = c.customLevelLabel
        domain = c.domain
        if (!preserveTopic) topic = ""
        wordCount = c.wordCount.toFloat()
        examplesPerWord = c.examplesPerWord
        outputFormat = c.outputFormat
        includePronunciation = c.includePronunciation
        includeSynonymsAndCollocations = c.includeSynonymsAndCollocations
        includeUsageNote = c.includeUsageNote
        isHeadwordIndexOnly = c.isHeadwordIndexOnly
        utilityTier = c.utilityTier
        customInstruction = c.customInstruction
    }

    fun reset() {
        language = "English"
        languageVariant = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT
        translationLanguage = ""
        levelCode = VocabularyLevel.INTERMEDIATE.code
        customLevelLabel = ""
        domain = "General"
        topic = ""
        wordCount = 20f
        examplesPerWord = 3
        outputFormat = "Strict JSON"
        includePronunciation = true
        includeSynonymsAndCollocations = true
        includeUsageNote = true
        excludeAlreadySavedWords = true
        isHeadwordIndexOnly = false
        utilityTier = VocabularyUtilityTier.EXAM_EDITORIAL
        customInstruction = ""
    }

    fun buildPrompt(existingWordsToAvoid: List<String> = emptyList()): String {
        val lang = language.trim().ifBlank { "English" }
        val resolvedVariant = VocabularyLanguageVariants.normalizeVariant(lang, languageVariant)
        val variantDirective = VocabularyLanguageVariants.promptDirective(lang, resolvedVariant)
        val transLang = translationLanguage.trim()
        val hasTranslation = transLang.isNotBlank() && !transLang.equals("None", ignoreCase = true)
        val lvl = VocabularyLevel.fromCode(levelCode)
        val cleanCustomTrack = customLevelLabel.trim()
        val levelLabel = lvl.displayTitleForLanguage(lang, cleanCustomTrack)
        val langProficiencyGuide = VocabularyLanguageVariants.proficiencyGuideForLanguage(lang)
        val isMixedOrCustom = lvl == VocabularyLevel.MIXED || lvl == VocabularyLevel.CUSTOM
        val schemaLevelHint = when {
            lvl == VocabularyLevel.MIXED -> "A1 | A2 | B1 | B2 | C1 | C2"
            lvl == VocabularyLevel.CUSTOM -> "A1 | A2 | B1 | B2 | C1 | C2"
            else -> lvl.code
        }
        val domainClean = domain.trim().ifBlank { "General" }
        val topicLine = topic.trim().ifBlank { "High-utility $domainClean vocabulary across everyday and real-world situations" }
        val count = wordCount.toInt().coerceIn(5, 60)
        val exCount = examplesPerWord.coerceIn(1, 4)

        if (isHeadwordIndexOnly) {
            return buildHeadwordIndexPrompt(
                lang = lang,
                resolvedVariant = resolvedVariant,
                variantDirective = variantDirective,
                lvl = lvl,
                levelLabel = levelLabel,
                langProficiencyGuide = langProficiencyGuide,
                schemaLevelHint = schemaLevelHint,
                domainClean = domainClean,
                topicLine = topicLine,
                count = count,
                existingWordsToAvoid = existingWordsToAvoid
            )
        }

        return buildString {
            appendLine("Act as a master lexicographer and multi-language curriculum specialist for $lang.")
            appendLine()
            appendLine("Generate a curated, non-repetitive vocabulary set of EXACTLY $count distinct words/expressions in $lang.")
            appendLine()
            appendLine("### VOCABULARY SPECIFICATION")
            appendLine("- Target Language: $lang")
            if (resolvedVariant.isNotBlank()) {
                appendLine("- Regional Origin / Standard: ${VocabularyLanguageVariants.displayLabel(resolvedVariant)}")
            }
            when (lvl) {
                VocabularyLevel.MIXED -> {
                    appendLine("- Proficiency Spread: Mixed / Progressive (Distribute naturally across beginner A1 to mastery C2 for $lang)")
                }
                VocabularyLevel.CUSTOM -> {
                    appendLine("- Curriculum / Track Focus: ${cleanCustomTrack.ifBlank { "Custom Track" }}")
                    appendLine("- Proficiency Calibration: Assign each word's exact A1–C2 level equivalent for $lang")
                }
                else -> {
                    appendLine("- Target Proficiency Level: $levelLabel (STRICT level code: ${lvl.code} ONLY)")
                }
            }
            appendLine("- Language Calibration: $langProficiencyGuide")
            appendLine("- Domain / Register: $domainClean")
            appendLine("- Focus Theme / Source Context: $topicLine")
            appendLine(
                if (hasTranslation) {
                    "- Translation / Bridge Language: $transLang (provide accurate, natural $transLang translations for each word and example sentence)"
                } else {
                    "- Mode: Monolingual in $lang (clear learner-friendly meanings in $lang; leave translation empty)"
                }
            )
            appendLine("- Examples per Word: EXACTLY $exCount natural example sentences in DIFFERENT real-world contexts (e.g., Everyday, Workplace, Academic, Conversational, News/Editorial).")
            appendLine()
            appendLine("### STRICT QUALITY & ANTI-REPETITION RULES")
            appendLine("1. ZERO REPETITION: Every entry must be a distinct, unique headword. Never repeat the same word or near-identical inflections.")
            appendLine("2. SORTING: Return the final vocabulary list sorted strictly in ASCENDING alphabetical order (A -> Z).")
            when (lvl) {
                VocabularyLevel.MIXED -> {
                    appendLine("3. EXACT PER-WORD LEVEL TAGGING: Calibrate EVERY word individually and set `level` to its exact tier code (`A1`, `A2`, `B1`, `B2`, `C1`, or `C2`) for $lang. NEVER write \"MIXED\", \"Progressive\", or \"CUSTOM\" in `level`. Also label partOfSpeech accurately.")
                }
                VocabularyLevel.CUSTOM -> {
                    appendLine("3. CUSTOM TRACK & LEVEL PRECISION: Align every word with `${cleanCustomTrack.ifBlank { "Custom Track" }}`, label partOfSpeech accurately, and set `level` to its exact CEFR equivalent (`A1`–`C2`).")
                }
                else -> {
                    appendLine("3. STRICT LEVEL FILTER: Generate ONLY ${lvl.code} ($levelLabel) vocabulary in $lang. Do NOT include words from any other proficiency level. Every entry MUST use `\"level\": \"${lvl.code}\"` and accurately label partOfSpeech.")
                }
            }
            if (variantDirective.isNotBlank()) {
                appendLine("4. ORIGIN & SPELLING STANDARD: $variantDirective")
                appendLine("5. CONTEXT VARIETY: Each word's $exCount examples must demonstrate genuinely different contexts/registers so the learner understands real usage nuances.")
            } else {
                appendLine("4. CONTEXT VARIETY: Each word's $exCount examples must demonstrate genuinely different contexts/registers so the learner understands real usage nuances.")
            }
            var nextNum = if (variantDirective.isNotBlank()) 6 else 5
            if (includePronunciation) {
                appendLine("${nextNum++}. PRONUNCIATION / READING: Include clear IPA or romanization/reading guide (e.g., Furigana/Romaji for Japanese, Pinyin for Chinese, IPA for English/European languages).")
            }
            if (includeSynonymsAndCollocations) {
                appendLine("${nextNum++}. LEXICAL NETWORK: Include natural synonyms, antonyms (if applicable), and 2–3 high-frequency collocations/phrases.")
            }
            if (includeUsageNote) {
                appendLine("${nextNum++}. USAGE & NUANCE NOTE: Add a brief 1-sentence note clarifying register, common learner mistake, or subtle difference from close synonyms.")
            }
            if (excludeAlreadySavedWords && existingWordsToAvoid.isNotEmpty()) {
                val scopeLabel = when (lvl) {
                    VocabularyLevel.MIXED -> "ALL $lang LEVELS COMBINED"
                    VocabularyLevel.CUSTOM -> "${cleanCustomTrack.ifBlank { "Custom Track" }} $lang"
                    else -> "${lvl.code} $lang"
                }
                val cappedList = existingWordsToAvoid.take(MAX_INLINE_VOCAB_EXCLUDE_WORDS)
                val capNote = if (existingWordsToAvoid.size > MAX_INLINE_VOCAB_EXCLUDE_WORDS) {
                    ", showing first ${cappedList.size} to protect token limits"
                } else {
                    ""
                }
                appendLine("${nextNum++}. EXCLUDE EXISTING $scopeLabel WORDS (${existingWordsToAvoid.size} already saved/queued in $scopeLabel$capNote): Do NOT repeat or include any of these existing $scopeLabel words: ${cappedList.joinToString(", ")}")
            }
            if (customInstruction.isNotBlank()) {
                appendLine("${nextNum}. CUSTOM RULE: ${customInstruction.trim()}")
            }
            appendLine()

            if (outputFormat == "Structured Text") {
                appendLine("### OUTPUT FORMAT: STRUCTURED TEXT (STRICTLY FOLLOW THIS TEMPLATE)")
                appendLine("Return ONLY the vocabulary blocks below with no greeting or extra commentary. Separate each word with `---`.")
                appendLine()
                appendLine("LANGUAGE: $lang")
                if (resolvedVariant.isNotBlank()) appendLine("VARIANT: $resolvedVariant")
                appendLine("WORD: [Headword]")
                if (includePronunciation) appendLine("PRONUNCIATION: [/IPA/ or romanization]")
                appendLine("POS: [noun | verb | adjective | adverb | idiom | phrasal verb]")
                appendLine("LEVEL: [$schemaLevelHint]")
                appendLine("MEANING: [Clear, crisp definition]")
                if (hasTranslation) appendLine("TRANSLATION: [Natural $transLang meaning]")
                appendLine("EXAMPLES:")
                val sampleContexts = listOf("Everyday", "Workplace", "Academic", "Conversational")
                for (i in 0 until exCount) {
                    val ctx = sampleContexts[i % sampleContexts.size]
                    if (hasTranslation) {
                        appendLine("- [$ctx] Natural sentence using the word in $lang. | Natural $transLang translation.")
                    } else {
                        appendLine("- [$ctx] Natural sentence using the word in $lang.")
                    }
                }
                if (includeSynonymsAndCollocations) {
                    appendLine("SYNONYMS: [syn1, syn2, syn3]")
                    appendLine("ANTONYMS: [ant1, ant2]")
                    appendLine("COLLOCATIONS: [common phrase 1, common phrase 2]")
                }
                if (includeUsageNote) {
                    appendLine("NOTE: [Concise tone, register, or usage warning]")
                }
                appendLine("---")
            } else {
                appendLine("### OUTPUT FORMAT: STRICT JSON ARRAY (ZERO-DRIFT SCHEMA)")
                appendLine("Return ONLY a valid JSON array inside a single ```json code block starting with `[` and ending with `]` (no extra commentary outside the code block).")
                appendLine("Every object MUST use this exact schema:")
                appendLine("[")
                appendLine("  {")
                appendLine("    \"word\": \"headword\",")
                appendLine("    \"language\": \"$lang\",")
                if (resolvedVariant.isNotBlank()) appendLine("    \"languageVariant\": \"$resolvedVariant\",")
                appendLine("    \"pronunciation\": \"${if (includePronunciation) "/IPA/ or romanization" else ""}\",")
                appendLine("    \"partOfSpeech\": \"noun | verb | adjective | adverb | idiom | phrasal verb\",")
                appendLine("    \"level\": \"$schemaLevelHint\",")
                appendLine("    \"meaning\": \"Clear, precise definition\",")
                appendLine("    \"translation\": \"${if (hasTranslation) "Natural $transLang translation" else ""}\",")
                appendLine("    \"examples\": [")
                appendLine("      {")
                appendLine("        \"context\": \"Everyday\",")
                appendLine("        \"sentence\": \"Natural sentence in $lang\",")
                appendLine("        \"translation\": \"${if (hasTranslation) "Sentence translation in $transLang" else ""}\"")
                appendLine("      }")
                appendLine("    ],")
                appendLine("    \"synonyms\": ${if (includeSynonymsAndCollocations) "[\"syn1\", \"syn2\"]" else "[]"},")
                appendLine("    \"antonyms\": ${if (includeSynonymsAndCollocations) "[\"ant1\"]" else "[]"},")
                appendLine("    \"collocations\": ${if (includeSynonymsAndCollocations) "[\"phrase 1\", \"phrase 2\"]" else "[]"},")
                appendLine("    \"usageNote\": \"${if (includeUsageNote) "Brief register/nuance tip" else ""}\"")
                appendLine("  }")
                appendLine("]")
            }
        }.trim()
    }

    private fun buildHeadwordIndexPrompt(
        lang: String,
        resolvedVariant: String,
        variantDirective: String,
        lvl: VocabularyLevel,
        levelLabel: String,
        langProficiencyGuide: String,
        schemaLevelHint: String,
        domainClean: String,
        topicLine: String,
        count: Int,
        existingWordsToAvoid: List<String>
    ): String {
        return buildString {
            appendLine("Act as a senior lexicographer and competitive-exam vocabulary curator for $lang.")
            appendLine()
            appendLine("Generate a lightweight Stage-1 Headword Index of EXACTLY $count high-utility, practical words/expressions in $lang.")
            appendLine("Do NOT generate full definitions or example sentences yet — return ONLY the curated headword index JSON so the learner can filter duplicates and enrich selected words in Stage 2.")
            appendLine()
            appendLine("### SELECTION & UTILITY SPECIFICATION")
            appendLine("- Target Language: $lang")
            if (resolvedVariant.isNotBlank()) {
                appendLine("- Regional Origin / Standard: ${VocabularyLanguageVariants.displayLabel(resolvedVariant)}")
            }
            appendLine("- Utility Tier: ${utilityTier.title} — ${utilityTier.subtitle}")
            when (lvl) {
                VocabularyLevel.MIXED -> {
                    appendLine("- Proficiency Spread: Mixed / Progressive across A1–C2 in $lang")
                }
                VocabularyLevel.CUSTOM -> {
                    appendLine("- Curriculum / Track Focus: ${customLevelLabel.trim().ifBlank { "Custom Track" }}")
                }
                else -> {
                    appendLine("- Target Proficiency Level: $levelLabel (STRICT level code: ${lvl.code} ONLY)")
                }
            }
            appendLine("- Language Calibration: $langProficiencyGuide")
            appendLine("- Domain / Register: $domainClean")
            appendLine("- Focus Theme / Source Context: $topicLine")
            appendLine()
            appendLine("### STRICT CURATION & ANTI-NOISE RULES")
            appendLine("1. PRACTICAL HIGH-UTILITY ONLY: Include only meaningful words genuinely encountered in daily intelligent reading, editorials, literature, and competitive exams. Strictly SKIP archaic trivia, ultra-obscure dictionary curiosities, and redundant filler.")
            appendLine("2. ZERO REPETITION: Every entry must be a unique base headword (lemma). Do not repeat inflected forms.")
            appendLine("3. SORTING: Sort the JSON array strictly in ASCENDING alphabetical order (A -> Z).")
            when (lvl) {
                VocabularyLevel.MIXED -> {
                    appendLine("4. CALIBRATED LEVEL: Set `level` accurately to each word's true tier (`A1`, `A2`, `B1`, `B2`, `C1`, or `C2`).")
                }
                VocabularyLevel.CUSTOM -> {
                    appendLine("4. CUSTOM TRACK LEVEL: Match `${customLevelLabel.trim().ifBlank { "Custom Track" }}` and set `level` to each word's true CEFR equivalent (`A1`–`C2`).")
                }
                else -> {
                    appendLine("4. STRICT LEVEL FILTER: Include ONLY ${lvl.code} ($levelLabel) headwords in $lang. Set `\"level\": \"${lvl.code}\"` on EVERY item—do NOT mix in words from any other proficiency level.")
                }
            }
            var nextNum = 5
            if (variantDirective.isNotBlank()) {
                appendLine("${nextNum++}. SPELLING STANDARD: $variantDirective")
            }
            if (excludeAlreadySavedWords && existingWordsToAvoid.isNotEmpty()) {
                val scopeLabel = when (lvl) {
                    VocabularyLevel.MIXED -> "ALL $lang LEVELS COMBINED"
                    VocabularyLevel.CUSTOM -> "${customLevelLabel.trim().ifBlank { "Custom Track" }} $lang"
                    else -> "${lvl.code} $lang"
                }
                val cappedList = existingWordsToAvoid.take(MAX_INLINE_VOCAB_EXCLUDE_WORDS)
                val capNote = if (existingWordsToAvoid.size > MAX_INLINE_VOCAB_EXCLUDE_WORDS) {
                    ", showing first ${cappedList.size} to protect token limits"
                } else {
                    ""
                }
                appendLine("${nextNum++}. EXCLUDE EXISTING $scopeLabel WORDS (${existingWordsToAvoid.size} already saved/queued/ignored in $scopeLabel$capNote): Do NOT repeat or include any of these $scopeLabel words: ${cappedList.joinToString(", ")}")
            }
            if (customInstruction.isNotBlank()) {
                appendLine("${nextNum}. CUSTOM RULE: ${customInstruction.trim()}")
            }
            appendLine()
            appendLine("### OUTPUT FORMAT: STRICT JSON ARRAY ONLY")
            appendLine("Return ONLY a valid JSON array inside a single ```json code block starting with `[` and ending with `]`:")
            appendLine("[")
            appendLine("  {")
            appendLine("    \"word\": \"headword\",")
            appendLine("    \"language\": \"$lang\",")
            appendLine("    \"level\": \"$schemaLevelHint\",")
            appendLine("    \"pos\": \"noun | verb | adjective | adverb | idiom | phrasal verb\",")
            appendLine("    \"domain\": \"$domainClean\",")
            appendLine("    \"utilityReason\": \"1-line reason why this word is high-value in daily/exam usage\"")
            appendLine("  }")
            appendLine("]")
        }.trim()
    }
}

private val COMMON_VOCAB_LANGUAGES = listOf(
    // Global & European
    "English", "Spanish", "French", "German", "Italian", "Portuguese", "Russian", "Latin",
    // East Asian
    "Japanese", "Chinese", "Korean",
    // South Asian (Indic)
    "Hindi", "Sanskrit", "Urdu", "Bengali", "Marathi", "Tamil", "Telugu", "Gujarati", "Punjabi", "Kannada", "Malayalam",
    // Middle Eastern
    "Arabic", "Persian"
)

private val COMMON_TRANSLATION_LANGUAGES = listOf(
    "" to "Monolingual",
    "English" to "English",
    "Hindi" to "Hindi",
    "Spanish" to "Spanish",
    "French" to "French",
    "German" to "German",
    "Japanese" to "Japanese",
    "Chinese" to "Chinese",
    "Arabic" to "Arabic"
)

private val COMMON_VOCAB_DOMAINS = listOf(
    "General", "Everyday Spoken", "Academic", "Business", "Competitive Exam", "Editorial / News", "Literary"
)

@Composable
private fun vocabFieldColors(colors: ExamColorPalette) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = colors.teal,
    unfocusedBorderColor = colors.border,
    focusedLabelColor = colors.teal,
    unfocusedLabelColor = colors.inkMuted,
    focusedTextColor = colors.inkBold,
    unfocusedTextColor = colors.inkBold,
    focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.25f),
    unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.15f)
)

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.vocabularyPromptGeneratorItems(
    state: VocabularyPromptState,
    colors: ExamColorPalette,
    generatedPrompt: String,
    copied: Boolean,
    existingLanguageWordCount: Int,
    showAdvancedSettings: Boolean = true,
    onExpandAdvancedSettings: () -> Unit = {},
    onCopyPrompt: () -> Unit,
    onOpenVocabularyHub: () -> Unit
) {
    // 1. Language, Translation & Focus Topic Card
    item(key = "vocab_scope_card") {
        val fieldColors = vocabFieldColors(colors)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(0.8.dp, colors.border)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "LANGUAGE & FOCUS THEME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.teal,
                        letterSpacing = 0.5.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.tealLight
                    ) {
                        Text(
                            text = VocabularyLevel.fromCode(state.levelCode).displayTitleForLanguage(state.language, state.customLevelLabel),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                // Stage Toggle: Full Vocabulary Cards vs. Stage-1 Headwords Index (JSON)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        false to ("Full Word Cards" to "Meanings, examples & notes"),
                        true to ("Headword Index (JSON)" to "Stage-1 list · Auto-dedupes")
                    ).forEach { (indexOnly, labelPair) ->
                        val selected = state.isHeadwordIndexOnly == indexOnly
                        Surface(
                            onClick = {
                                state.isHeadwordIndexOnly = indexOnly
                                if (indexOnly) state.outputFormat = "Strict JSON"
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                if (selected) 1.3.dp else 0.7.dp,
                                if (selected) colors.teal else colors.border
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag(if (indexOnly) "vocab_stage_headword_index" else "vocab_stage_full_cards")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = labelPair.first,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) colors.tealDark else colors.inkBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = labelPair.second,
                                    fontSize = 10.sp,
                                    color = colors.inkMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (state.isHeadwordIndexOnly) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Practical Utility Filter",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkMuted
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VocabularyUtilityTier.entries.forEach { tier ->
                                val selected = state.utilityTier == tier
                                Surface(
                                    onClick = { state.utilityTier = tier },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) colors.amberLight else colors.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(
                                        if (selected) 1.2.dp else 0.7.dp,
                                        if (selected) colors.amber else colors.border
                                    ),
                                    modifier = Modifier.testTag("vocab_utility_${tier.name.lowercase()}")
                                ) {
                                    Text(
                                        text = tier.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) colors.amber else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = state.utilityTier.subtitle,
                            fontSize = 10.5.sp,
                            fontStyle = FontStyle.Italic,
                            color = colors.inkMuted
                        )
                    }
                }

                // Target Language quick pills + editable text box
                Text(
                    text = "Target Language (Multi-Language)",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    COMMON_VOCAB_LANGUAGES.forEach { lang ->
                        val selected = state.language.equals(lang, ignoreCase = true)
                        Surface(
                            onClick = {
                                state.language = lang
                                if (VocabularyLanguageVariants.isEnglish(lang) && state.languageVariant.isBlank()) {
                                    state.languageVariant = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT
                                } else if (!VocabularyLanguageVariants.isEnglish(lang)) {
                                    state.languageVariant = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                if (selected) 1.2.dp else 0.7.dp,
                                if (selected) colors.teal else colors.border
                            ),
                            modifier = Modifier.testTag("vocab_lang_chip_${lang.lowercase()}")
                        ) {
                            Text(
                                text = lang,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) colors.tealDark else colors.inkBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // Origin / Spelling Variant selector (wraps cleanly with FlowRow at any system font scale)
                if (VocabularyLanguageVariants.isEnglish(state.language)) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "Origin & Spelling Standard",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkMuted
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VocabularyLanguageVariants.ENGLISH_VARIANTS.forEach { variant ->
                                val selected = state.languageVariant.equals(variant, ignoreCase = true)
                                Surface(
                                    onClick = { state.languageVariant = variant },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(
                                        if (selected) 1.1.dp else 0.7.dp,
                                        if (selected) colors.teal else colors.border
                                    ),
                                    modifier = Modifier.testTag("vocab_origin_chip_${variant.lowercase()}")
                                ) {
                                    Text(
                                        text = VocabularyLanguageVariants.displayLabel(variant),
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) colors.tealDark else colors.inkBold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.language,
                        onValueChange = { state.language = it },
                        label = { Text("Target Language", fontSize = 11.5.sp) },
                        placeholder = { Text("Any language...", fontSize = 11.5.sp) },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vocab_prompt_language_input")
                    )

                    OutlinedTextField(
                        value = state.translationLanguage,
                        onValueChange = { state.translationLanguage = it },
                        label = { Text("Translation (Opt.)", fontSize = 11.5.sp) },
                        placeholder = { Text("Blank = None", fontSize = 11.5.sp) },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("vocab_prompt_translation_input")
                    )
                }

                // Quick translation chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    COMMON_TRANSLATION_LANGUAGES.forEach { (value, label) ->
                        val selected = state.translationLanguage.equals(value, ignoreCase = true)
                        Surface(
                            onClick = { state.translationLanguage = value },
                            shape = RoundedCornerShape(7.dp),
                            color = if (selected) colors.amberLight else colors.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                if (selected) 1.dp else 0.6.dp,
                                if (selected) colors.amber else colors.border
                            )
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) colors.amber else colors.inkMuted,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Focus Topic / Theme (with 1-tap clear icon)
                OutlinedTextField(
                    value = state.topic,
                    onValueChange = { state.topic = it },
                    label = { Text("Theme, Topic or Source Context (Optional)", fontSize = 12.sp) },
                    placeholder = { Text("e.g. News Editorials, Travel, Diplomacy, Persuasive Verbs", fontSize = 11.5.sp) },
                    singleLine = true,
                    trailingIcon = if (state.topic.isNotEmpty()) {
                        {
                            IconButton(onClick = { state.topic = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear vocabulary topic",
                                    tint = colors.inkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else null,
                    colors = fieldColors,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vocab_prompt_topic_input")
                )
            }
        }
    }

    // Compact summary pill when advanced settings are collapsed by a saved preset
    if (!showAdvancedSettings) {
        item(key = "vocab_compact_summary") {
            val lvlTitle = VocabularyLevel.fromCode(state.levelCode).displayTitleForLanguage(state.language, state.customLevelLabel)
            Surface(
                onClick = onExpandAdvancedSettings,
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vocab_expand_settings_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Preset Rules Active (Compact View)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Text(
                            text = "$lvlTitle · ${state.wordCount.toInt()} Words · ${state.examplesPerWord} Ex/Word · ${state.outputFormat}",
                            fontSize = 11.sp,
                            color = colors.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Customize",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.teal
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand vocabulary settings",
                        tint = colors.teal,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // 2. Proficiency Level & Domain Card
    if (showAdvancedSettings) {
        item(key = "vocab_level_card") {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "PROFICIENCY LEVEL & REGISTER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.teal,
                        letterSpacing = 0.5.sp
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VocabularyLevel.promptSelectableLevels().forEach { lvl ->
                            val selected = state.levelCode.equals(lvl.code, ignoreCase = true)
                            Surface(
                                onClick = { state.levelCode = lvl.code },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    if (selected) 1.2.dp else 0.7.dp,
                                    if (selected) colors.teal else colors.border
                                ),
                                modifier = Modifier.testTag("vocab_level_${lvl.code.lowercase()}")
                            ) {
                                Text(
                                    text = lvl.displayTitleForLanguage(state.language),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) colors.tealDark else colors.inkBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    val selectedLvlEnum = VocabularyLevel.fromCode(state.levelCode)
                    if (selectedLvlEnum == VocabularyLevel.CUSTOM) {
                        OutlinedTextField(
                            value = state.customLevelLabel,
                            onValueChange = { state.customLevelLabel = it },
                            label = { Text("Custom Curriculum / Exam Track", fontSize = 11.5.sp) },
                            placeholder = { Text("e.g. JLPT N3, HSK 4, GRE, UPSC", fontSize = 11.5.sp) },
                            singleLine = true,
                            colors = vocabFieldColors(colors),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vocab_custom_level_input")
                        )
                    }

                    val activeDescriptor = selectedLvlEnum
                        .descriptorForLanguage(state.language, state.customLevelLabel)
                    Text(
                        text = activeDescriptor.description,
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = colors.inkMuted
                    )

                    HorizontalDivider(color = colors.borderLight)

                    Text(
                        text = "Domain / Usage Register",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        COMMON_VOCAB_DOMAINS.forEach { dom ->
                            val selected = state.domain.equals(dom, ignoreCase = true)
                            Surface(
                                onClick = { state.domain = dom },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) colors.amberLight else colors.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    if (selected) 1.1.dp else 0.7.dp,
                                    if (selected) colors.amber else colors.border
                                )
                            ) {
                                Text(
                                    text = dom,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) colors.amber else colors.inkBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = colors.borderLight)

                    // Level-scoped Exclude control positioned right below Level & Domain selection
                    val langTitle = state.language.trim().ifBlank { "English" }
                    val levelScopeText = when (selectedLvlEnum) {
                        VocabularyLevel.MIXED -> "All $langTitle levels"
                        VocabularyLevel.CUSTOM -> "${state.customLevelLabel.trim().ifBlank { "Custom" }} $langTitle"
                        else -> "${selectedLvlEnum.compactBadgeForLanguage(langTitle)} $langTitle"
                    }
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (existingLanguageWordCount > 0) {
                            colors.tealLight.copy(alpha = 0.4f)
                        } else {
                            colors.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            0.7.dp,
                            if (existingLanguageWordCount > 0) colors.teal.copy(alpha = 0.35f) else colors.border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (existingLanguageWordCount > 0) {
                                    Modifier.clickable { state.excludeAlreadySavedWords = !state.excludeAlreadySavedWords }
                                } else {
                                    Modifier
                                }
                            )
                            .testTag("vocab_exclude_level_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (existingLanguageWordCount > 0) {
                                Checkbox(
                                    checked = state.excludeAlreadySavedWords,
                                    onCheckedChange = { state.excludeAlreadySavedWords = it },
                                    colors = CheckboxDefaults.colors(checkedColor = colors.teal)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (existingLanguageWordCount > 0) {
                                        "Exclude $existingLanguageWordCount existing $levelScopeText words"
                                    } else {
                                        "0 existing $levelScopeText words to exclude"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkBold
                                )
                                Text(
                                    text = if (existingLanguageWordCount > MAX_INLINE_VOCAB_EXCLUDE_WORDS) {
                                        "Filtered strictly to $levelScopeText (first $MAX_INLINE_VOCAB_EXCLUDE_WORDS listed in prompt to protect token limits; all duplicates filtered on import)"
                                    } else {
                                        "Filtered strictly to your $levelScopeText saved library & inbox (not all levels combined)"
                                    },
                                    fontSize = 10.5.sp,
                                    color = colors.inkMuted,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Volume, Multi-Context Examples & Output Format Card
        item(key = "vocab_format_card") {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (state.isHeadwordIndexOnly) "HEADWORD BATCH VOLUME" else "OUTPUT FORMAT & CARD DEPTH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.teal,
                        letterSpacing = 0.5.sp
                    )

                    if (!state.isHeadwordIndexOnly) {
                        // JSON vs Structured Text toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Strict JSON" to "Recommended • Zero-drift",
                                "Structured Text" to "Easy to read & tweak"
                            ).forEach { (fmt, sub) ->
                                val selected = state.outputFormat == fmt
                                Surface(
                                    onClick = { state.outputFormat = fmt },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(
                                        if (selected) 1.3.dp else 0.7.dp,
                                        if (selected) colors.teal else colors.border
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("vocab_format_${fmt.lowercase().replace(" ", "_")}")
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = fmt,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) colors.tealDark else colors.inkBold
                                        )
                                        Text(
                                            text = sub,
                                            fontSize = 10.sp,
                                            color = colors.inkMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Word count slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.isHeadwordIndexOnly) "Headwords to Index" else "Vocabulary Word Count",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                        Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight) {
                            Text(
                                text = "${state.wordCount.toInt()} Words",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.tealDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Slider(
                        value = state.wordCount,
                        onValueChange = { state.wordCount = it },
                        valueRange = 5f..60f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.teal,
                            activeTrackColor = colors.teal,
                            inactiveTrackColor = colors.border
                        )
                    )

                    if (!state.isHeadwordIndexOnly) {
                        // Context examples per word selector (responsive & scrollable at large font scales)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Context Examples / Word",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1, 2, 3, 4).forEach { count ->
                                    val selected = state.examplesPerWord == count
                                    Surface(
                                        onClick = { state.examplesPerWord = count },
                                        shape = RoundedCornerShape(7.dp),
                                        color = if (selected) colors.teal else colors.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(0.7.dp, if (selected) colors.teal else colors.border)
                                    ) {
                                        Text(
                                            text = "${count}x",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else colors.inkBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = colors.borderLight)

                        // Richness toggles
                        VocabCheckRow(
                            label = "Pronunciation / IPA / Romanization",
                            checked = state.includePronunciation,
                            onCheckedChange = { state.includePronunciation = it },
                            colors = colors
                        )
                        VocabCheckRow(
                            label = "Synonyms, Antonyms & Common Collocations",
                            checked = state.includeSynonymsAndCollocations,
                            onCheckedChange = { state.includeSynonymsAndCollocations = it },
                            colors = colors
                        )
                        VocabCheckRow(
                            label = "Usage, Tone & Nuance Note",
                            checked = state.includeUsageNote,
                            onCheckedChange = { state.includeUsageNote = it },
                            colors = colors
                        )
                    }

                    OutlinedTextField(
                        value = state.customInstruction,
                        onValueChange = { state.customInstruction = it },
                        label = { Text("Custom Instruction (Optional)", fontSize = 11.5.sp) },
                        placeholder = { Text("e.g. Emphasize formal verbs & avoid slang", fontSize = 11.5.sp) },
                        singleLine = true,
                        colors = vocabFieldColors(colors),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // 4. Constructed Vocabulary Prompt Preview + Actions Card
    item(key = "vocab_prompt_preview_card") {
        val lvlBadge = VocabularyLevel.fromCode(state.levelCode).compactBadge(state.customLevelLabel)
        val fmtLabel = when {
            state.isHeadwordIndexOnly -> "INDEX"
            state.outputFormat == "Structured Text" -> "TEXT"
            else -> "JSON"
        }
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(0.9.dp, colors.teal.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = colors.amber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CONSTRUCTED VOCABULARY PROMPT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            letterSpacing = 0.4.sp
                        )
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight) {
                        Text(
                            text = "$fmtLabel · $lvlBadge · ${state.wordCount.toInt()} Words",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(0.7.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = generatedPrompt,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.inkBold,
                        lineHeight = 17.sp,
                        softWrap = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .testTag("vocab_prompt_preview_text")
                    )
                }

                val isLargeFontScale = LocalDensity.current.fontScale >= 1.3f
                if (isLargeFontScale) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCopyPrompt,
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copied) colors.green else colors.navyBanner,
                                contentColor = colors.navyBannerText
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("vocab_prompt_copy_button")
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (copied) "Copied!" else "Copy Prompt",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenVocabularyHub,
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colors.surface,
                                contentColor = colors.tealDark
                            ),
                            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.55f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("vocab_prompt_open_hub_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open Vocab Hub",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onCopyPrompt,
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copied) colors.green else colors.navyBanner,
                                contentColor = colors.navyBannerText
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("vocab_prompt_copy_button")
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (copied) "Copied!" else "Copy Prompt",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenVocabularyHub,
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colors.surface,
                                contentColor = colors.tealDark
                            ),
                            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.55f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("vocab_prompt_open_hub_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open Vocab Hub",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VocabCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: ExamColorPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = colors.teal)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = colors.inkBold,
            modifier = Modifier.weight(1f)
        )
    }
}
