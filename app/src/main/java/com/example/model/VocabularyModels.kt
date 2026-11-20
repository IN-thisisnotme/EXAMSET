package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/**
 * Universal, language-neutral vocabulary proficiency tiers (aligned with CEFR A1-C2
 * while remaining friendly for any language or custom curriculum/exam track).
 */
enum class VocabularyLevel(
    val code: String,
    val shortBadge: String,
    val label: String,
    val description: String,
    val sortRank: Int
) {
    BEGINNER(
        code = "A1",
        shortBadge = "A1",
        label = "Beginner",
        description = "Core everyday survival words & expressions",
        sortRank = 1
    ),
    ELEMENTARY(
        code = "A2",
        shortBadge = "A2",
        label = "Elementary",
        description = "Routine conversations, travel & common topics",
        sortRank = 2
    ),
    INTERMEDIATE(
        code = "B1",
        shortBadge = "B1",
        label = "Intermediate",
        description = "Independent reading, news & everyday fluency",
        sortRank = 3
    ),
    UPPER_INTERMEDIATE(
        code = "B2",
        shortBadge = "B2",
        label = "Upper-Intermediate",
        description = "Nuanced discussion, workplace & standard exams",
        sortRank = 4
    ),
    ADVANCED(
        code = "C1",
        shortBadge = "C1",
        label = "Advanced",
        description = "Academic, editorial, GRE/IELTS/UPSC precision",
        sortRank = 5
    ),
    MASTERY(
        code = "C2",
        shortBadge = "C2",
        label = "Mastery",
        description = "Literary, rare & domain-specialist nuance",
        sortRank = 6
    ),
    MIXED(
        code = "MIXED",
        shortBadge = "MIXED",
        label = "Mixed / Progressive",
        description = "Spans multiple difficulty tiers in one set",
        sortRank = 7
    ),
    CUSTOM(
        code = "CUSTOM",
        shortBadge = "CUSTOM",
        label = "Custom Level",
        description = "Track-specific level (e.g. JLPT N3, HSK 4, GRE)",
        sortRank = 8
    );

    fun displayTitle(customLabel: String = ""): String {
        return when {
            this == CUSTOM && customLabel.isNotBlank() -> customLabel.trim()
            this == MIXED -> label
            this == CUSTOM -> label
            else -> "$label ($shortBadge)"
        }
    }

    fun compactBadge(customLabel: String = ""): String {
        return when {
            this == CUSTOM && customLabel.isNotBlank() -> customLabel.trim().take(12)
            else -> shortBadge
        }
    }

    companion object {
        fun folderSelectableLevels(): List<VocabularyLevel> =
            listOf(BEGINNER, ELEMENTARY, INTERMEDIATE, UPPER_INTERMEDIATE, ADVANCED, MASTERY, CUSTOM)

        fun fromCode(raw: String?): VocabularyLevel {
            if (raw.isNullOrBlank()) return INTERMEDIATE
            val clean = raw.trim().lowercase(Locale.ROOT)
            return entries.firstOrNull {
                it.code.equals(clean, ignoreCase = true) ||
                    it.label.equals(clean, ignoreCase = true) ||
                    it.name.equals(clean, ignoreCase = true)
            } ?: when {
                clean.contains("a1") || clean.contains("begin") || clean.contains("basic") || clean.contains("starter") -> BEGINNER
                clean.contains("a2") || clean.contains("element") -> ELEMENTARY
                clean.contains("b2") || clean.contains("upper") -> UPPER_INTERMEDIATE
                clean.contains("b1") || clean.contains("intermed") -> INTERMEDIATE
                clean.contains("c1") || clean.contains("advanc") -> ADVANCED
                clean.contains("c2") || clean.contains("master") || clean.contains("profic") || clean.contains("native") -> MASTERY
                clean.contains("mix") || clean.contains("progress") -> MIXED
                else -> CUSTOM
            }
        }
    }
}

data class VocabularyExample(
    val context: String = "General",
    val sentence: String,
    val translation: String = ""
) {
    fun normalizedSentenceKey(): String {
        return sentence.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("context", context.trim().ifBlank { "General" })
        put("sentence", sentence.trim())
        if (translation.isNotBlank()) put("translation", translation.trim())
    }

    companion object {
        fun fromJson(obj: JSONObject?): VocabularyExample? {
            if (obj == null) return null
            val sentence = obj.optString("sentence", "").ifBlank {
                obj.optString("example", "").ifBlank { obj.optString("text", "") }
            }.trim()
            if (sentence.isBlank()) return null
            val context = obj.optString("context", "").ifBlank {
                obj.optString("register", "").ifBlank { obj.optString("label", "General") }
            }.trim().ifBlank { "General" }
            val translation = obj.optString("translation", "").ifBlank {
                obj.optString("meaning", "")
            }.trim()
            return VocabularyExample(context = context, sentence = sentence, translation = translation)
        }
    }
}

object VocabularyLanguageVariants {
    const val DEFAULT_ENGLISH_VARIANT = "UK"
    val ENGLISH_VARIANTS = listOf("UK", "US", "Neutral")

    fun isEnglish(language: String): Boolean {
        val clean = language.trim().lowercase(Locale.ROOT)
        return clean.isBlank() || clean == "english" || clean == "uk english" || clean == "us english" || clean == "british english" || clean == "american english"
    }

    fun normalizeVariant(language: String, raw: String?): String {
        val clean = raw?.trim().orEmpty()
        if (!isEnglish(language)) {
            return clean
        }
        if (clean.isBlank()) return DEFAULT_ENGLISH_VARIANT
        val lower = clean.lowercase(Locale.ROOT)
        return when {
            lower == "uk" || lower == "gb" || lower.contains("british") || lower.contains("oxford") || lower.contains("england") -> "UK"
            lower == "us" || lower == "usa" || lower.contains("american") || lower.contains("merriam") -> "US"
            lower == "neutral" || lower.contains("international") || lower == "any" -> "Neutral"
            else -> clean
        }
    }

    fun shortBadge(variant: String): String {
        val clean = variant.trim()
        return when {
            clean.equals("UK", ignoreCase = true) -> "UK"
            clean.equals("US", ignoreCase = true) -> "US"
            clean.equals("Neutral", ignoreCase = true) -> "Neutral"
            clean.length <= 12 -> clean
            else -> clean.take(11).trimEnd() + "…"
        }
    }

    fun displayLabel(variant: String): String {
        return when (variant.trim().uppercase(Locale.ROOT)) {
            "UK" -> "UK (British / Oxford)"
            "US" -> "US (American)"
            "NEUTRAL" -> "Neutral / International"
            "" -> "Default"
            else -> variant.trim()
        }
    }

    fun promptDirective(language: String, variant: String): String {
        val norm = normalizeVariant(language, variant)
        if (!isEnglish(language)) {
            return if (norm.isBlank()) "" else "Follow $norm regional spelling and natural usage conventions for $language."
        }
        return when (norm) {
            "UK" -> "Strictly follow British / Oxford English spelling (e.g., colour, analyse, programme, centre, judgement), British lexical usage, and UK RP/Oxford IPA pronunciation."
            "US" -> "Strictly follow American English spelling (e.g., color, analyze, program, center, judgment), US lexical usage, and General American IPA pronunciation."
            "Neutral" -> "Use standard international English; note UK/US spelling variants briefly in usageNote when relevant."
            else -> "Follow $norm English spelling, lexical usage, and pronunciation norms."
        }
    }

    fun proficiencyGuideForLanguage(language: String): String {
        return when (language.trim().lowercase(Locale.ROOT)) {
            "japanese" -> "Calibrate Japanese difficulty to real usage & JLPT progression (A1≈JLPT N5 survival, A2≈N4 everyday, B1≈N3 practical fluency, B2≈N2 news/workplace, C1≈N1 formal/abstract, C2≈literary/specialist)."
            "chinese", "mandarin", "mandarin chinese" -> "Calibrate Mandarin difficulty to real usage & HSK progression (A1≈HSK 1, A2≈HSK 2–3, B1≈HSK 4, B2≈HSK 5, C1≈HSK 6, C2≈HSK 7–9/classical)."
            "korean" -> "Calibrate Korean difficulty to TOPIK & register progression (A1≈TOPIK I-1, A2≈TOPIK I-2, B1≈TOPIK II-3, B2≈TOPIK II-4, C1≈TOPIK II-5, C2≈TOPIK II-6/literary)."
            "hindi", "urdu", "bengali", "marathi", "tamil", "telugu", "gujarati", "punjabi" ->
                "Calibrate $language difficulty to authentic native usage & register progression (A1=core everyday spoken words, A2=routine practical expressions, B1=fluent general/news vocabulary, B2=formal/workplace register, C1=editorial/academic/high-register vocabulary, C2=literary/rare/classical nuance)."
            "sanskrit" -> "Calibrate Sanskrit difficulty to textual & lexical progression (A1=foundational roots/pronouns/basic nouns, A2=common everyday & Gita-level verbs/nouns, B1=standard narrative prose/kavya vocabulary, B2=classical compound & philosophical terms, C1=academic/shastric terminology, C2=rare Vedic/kavya/poetic lexicon)."
            "arabic" -> "Calibrate Arabic difficulty to MSA & register progression (A1=core survival MSA, A2=everyday practical MSA, B1=general fluency & media, B2=news/editorial & formal discussion, C1=academic/analytical MSA, C2=classical/literary rhetoric)."
            else -> "Calibrate $language difficulty to CEFR usage frequency & register (A1=Beginner survival, A2=Elementary routine, B1=Intermediate fluency, B2=Upper-Intermediate workplace/discussion, C1=Advanced academic/editorial, C2=Mastery literary/specialist)."
        }
    }
}

data class VocabularyEntry(
    val id: String = UUID.randomUUID().toString(),
    val word: String,
    val pronunciation: String = "",
    val partOfSpeech: String = "",
    val levelCode: String = VocabularyLevel.INTERMEDIATE.code,
    val customLevelLabel: String = "",
    val language: String = "",
    val languageVariant: String = "",
    val meaning: String,
    val translation: String = "",
    val translationLanguage: String = "",
    val examples: List<VocabularyExample> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val collocations: List<String> = emptyList(),
    val usageNote: String = "",
    val isBookmarked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun resolvedLevel(): VocabularyLevel = VocabularyLevel.fromCode(levelCode)

    fun effectiveLevelBadge(): String {
        val lvl = resolvedLevel()
        return if (lvl == VocabularyLevel.CUSTOM && customLevelLabel.isNotBlank()) {
            customLevelLabel.trim().take(12)
        } else {
            lvl.compactBadge(customLevelLabel)
        }
    }

    fun effectiveLevelTitle(): String = resolvedLevel().displayTitle(customLevelLabel)

    /**
     * Normalized lookup key for accurate cross-batch and cross-deck duplicate detection.
     * Preserves non-Latin scripts while stripping extra whitespace, outer punctuation, and combining diacritics.
     */
    fun normalizedWordKey(): String = normalizeVocabularyWordKey(word)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("word", word.trim())
        put("pronunciation", pronunciation.trim())
        put("partOfSpeech", partOfSpeech.trim())
        put("levelCode", levelCode)
        put("customLevelLabel", customLevelLabel.trim())
        if (language.isNotBlank()) put("language", language.trim())
        if (languageVariant.isNotBlank()) put("languageVariant", languageVariant.trim())
        put("meaning", meaning.trim())
        put("translation", translation.trim())
        if (translationLanguage.isNotBlank()) put("translationLanguage", translationLanguage.trim())
        put("examples", JSONArray().apply { examples.forEach { put(it.toJson()) } })
        put("synonyms", JSONArray().apply { synonyms.forEach { put(it) } })
        put("antonyms", JSONArray().apply { antonyms.forEach { put(it) } })
        put("collocations", JSONArray().apply { collocations.forEach { put(it) } })
        put("usageNote", usageNote.trim())
        put("isBookmarked", isBookmarked)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun normalizeVocabularyWordKey(raw: String): String {
            val trimmed = raw.trim().replace(Regex("\\s+"), " ")
            if (trimmed.isBlank()) return ""
            val nfkd = Normalizer.normalize(trimmed, Normalizer.Form.NFKD)
            val strippedCombining = nfkd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            return strippedCombining
                .lowercase(Locale.ROOT)
                .replace(Regex("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$"), "")
        }

        fun fromJson(obj: JSONObject?): VocabularyEntry? {
            if (obj == null) return null
            val word = obj.optString("word", "").trim()
            if (word.isBlank()) return null
            val examplesArr = obj.optJSONArray("examples")
            val examples = buildList {
                if (examplesArr != null) {
                    for (i in 0 until examplesArr.length()) {
                        val item = examplesArr.opt(i)
                        when (item) {
                            is JSONObject -> VocabularyExample.fromJson(item)?.let { add(it) }
                            is String -> if (item.isNotBlank()) add(VocabularyExample(sentence = item.trim()))
                        }
                    }
                }
            }
            return VocabularyEntry(
                id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                word = word,
                pronunciation = obj.optString("pronunciation", "").trim(),
                partOfSpeech = obj.optString("partOfSpeech", "").trim(),
                levelCode = obj.optString("levelCode", VocabularyLevel.INTERMEDIATE.code).ifBlank { VocabularyLevel.INTERMEDIATE.code },
                customLevelLabel = obj.optString("customLevelLabel", "").trim(),
                language = obj.optString("language", "").trim(),
                languageVariant = obj.optString("languageVariant", "").trim(),
                meaning = obj.optString("meaning", "").trim(),
                translation = obj.optString("translation", "").trim(),
                translationLanguage = obj.optString("translationLanguage", "").trim(),
                examples = examples,
                synonyms = parseStringList(obj.optJSONArray("synonyms")),
                antonyms = parseStringList(obj.optJSONArray("antonyms")),
                collocations = parseStringList(obj.optJSONArray("collocations")),
                usageNote = obj.optString("usageNote", "").trim(),
                isBookmarked = obj.optBoolean("isBookmarked", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        private fun parseStringList(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "").trim()
                    if (v.isNotBlank()) add(v)
                }
            }
        }
    }
}

data class VocabularyDeck(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val language: String = "English",
    val languageVariant: String = "",
    val translationLanguage: String = "",
    val levelCode: String = VocabularyLevel.INTERMEDIATE.code,
    val customLevelLabel: String = "",
    val domain: String = "General",
    val entries: List<VocabularyEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun resolvedLevel(): VocabularyLevel = VocabularyLevel.fromCode(levelCode)

    fun effectiveLevelTitle(): String = resolvedLevel().displayTitle(customLevelLabel)

    fun effectiveLevelBadge(): String = resolvedLevel().compactBadge(customLevelLabel)

    fun languageDisplay(): String {
        val src = language.trim().ifBlank { "English" }
        val variantTag = languageVariant.trim()
            .takeIf { it.isNotBlank() && !it.equals("Neutral", ignoreCase = true) }
            ?.let { " · ${VocabularyLanguageVariants.shortBadge(it)}" }
            .orEmpty()
        val tr = translationLanguage.trim()
        return if (tr.isNotBlank() && !tr.equals("None", ignoreCase = true)) {
            "$src$variantTag -> $tr"
        } else {
            "$src$variantTag"
        }
    }

    /**
     * Always returns vocabulary entries sorted cleanly in ascending alphabetical order (A -> Z).
     */
    fun sortedEntriesAscending(): List<VocabularyEntry> {
        return entries.sortedWith(
            compareBy<VocabularyEntry>(
                { it.word.trim().lowercase(Locale.ROOT) },
                { it.partOfSpeech.trim().lowercase(Locale.ROOT) },
                { it.createdAt }
            )
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title.trim())
        put("language", language.trim().ifBlank { "English" })
        if (languageVariant.isNotBlank()) put("languageVariant", languageVariant.trim())
        put("translationLanguage", translationLanguage.trim())
        put("levelCode", levelCode)
        put("customLevelLabel", customLevelLabel.trim())
        put("domain", domain.trim().ifBlank { "General" })
        put("entries", JSONArray().apply { sortedEntriesAscending().forEach { put(it.toJson()) } })
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject?): VocabularyDeck? {
            if (obj == null) return null
            val title = obj.optString("title", "").trim()
            if (title.isBlank()) return null
            val entriesArr = obj.optJSONArray("entries")
            val entries = buildList {
                if (entriesArr != null) {
                    for (i in 0 until entriesArr.length()) {
                        VocabularyEntry.fromJson(entriesArr.optJSONObject(i))?.let { add(it) }
                    }
                }
            }
            return VocabularyDeck(
                id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                title = title,
                language = obj.optString("language", "English").trim().ifBlank { "English" },
                languageVariant = obj.optString("languageVariant", "").trim(),
                translationLanguage = obj.optString("translationLanguage", "").trim(),
                levelCode = obj.optString("levelCode", VocabularyLevel.INTERMEDIATE.code).ifBlank { VocabularyLevel.INTERMEDIATE.code },
                customLevelLabel = obj.optString("customLevelLabel", "").trim(),
                domain = obj.optString("domain", "General").trim().ifBlank { "General" },
                entries = entries,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        fun serializeList(decks: List<VocabularyDeck>): String {
            val arr = JSONArray()
            decks.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun deserializeList(raw: String?): List<VocabularyDeck> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        fromJson(arr.optJSONObject(i))?.let { add(it) }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}

enum class VocabularyRecallDirection(val label: String, val subtitle: String) {
    WORD_TO_MEANING("Word -> Meaning", "Recognition recall"),
    MEANING_TO_WORD("Meaning -> Word", "Active production")
}

data class VocabularyCompiledSource(
    val originDeckId: String,
    val originDeckTitle: String,
    val originLevelCode: String,
    val originCustomLevelLabel: String
)

data class CompiledMixedVocabularyDeck(
    val language: String,
    val virtualDeck: VocabularyDeck,
    val sourceByEntryId: Map<String, VocabularyCompiledSource>,
    val srsByEntryId: Map<String, FlashcardSrsState>,
    val compiledLevelCount: Int,
    val compiledDeckCount: Int
)

data class VocabularyLevelFolderSummary(
    val language: String,
    val levelCode: String,
    val customLevelLabel: String,
    val levelTitle: String,
    val compactBadge: String,
    val sortRank: Int,
    val deckCount: Int,
    val totalWords: Int,
    val uniqueWords: Int,
    val dueCount: Int,
    val masteredCount: Int
)

data class VocabularyLanguageFolderSummary(
    val language: String,
    val deckCount: Int,
    val levelCount: Int,
    val totalWords: Int,
    val uniqueWords: Int,
    val dueCount: Int,
    val masteredCount: Int,
    val wotdUnlocked: Boolean
)
