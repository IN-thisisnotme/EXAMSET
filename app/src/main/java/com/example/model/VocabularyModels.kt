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

    data class LanguageLevelDescriptor(
        val title: String,
        val shortBadge: String,
        val description: String
    )

    fun descriptorForLanguage(language: String, customLabel: String = ""): LanguageLevelDescriptor {
        val norm = language.trim().lowercase(Locale.ROOT)
        if (this == CUSTOM && customLabel.isNotBlank()) {
            return LanguageLevelDescriptor(
                title = customLabel.trim(),
                shortBadge = customLabel.trim().take(10),
                description = "Custom Track Focus: ${customLabel.trim()}"
            )
        }
        return when {
            norm == "japanese" || norm == "ja" -> when (this) {
                BEGINNER -> LanguageLevelDescriptor("JLPT N5 (Basic Survival)", "JLPT N5", "Everyday survival Hiragana/Katakana & foundational vocabulary")
                ELEMENTARY -> LanguageLevelDescriptor("JLPT N4 (Everyday Routine)", "JLPT N4", "Everyday practical conversations, travel & routine dialogues")
                INTERMEDIATE -> LanguageLevelDescriptor("JLPT N3 (Practical Fluency)", "JLPT N3", "Independent comprehension, practical fluency & general news")
                UPPER_INTERMEDIATE -> LanguageLevelDescriptor("JLPT N2 (News & Workplace)", "JLPT N2", "Newspaper articles, nuanced discussion & workplace Japanese")
                ADVANCED -> LanguageLevelDescriptor("JLPT N1 (Formal & Abstract)", "JLPT N1", "Formal editorials, abstract concepts & academic Japanese")
                MASTERY -> LanguageLevelDescriptor("JLPT N1+ (Literary & Nuanced)", "JLPT N1+", "Classical idioms, four-character idioms (yojijukugo) & literary nuance")
                MIXED -> LanguageLevelDescriptor("Mixed / Progressive", "MIXED", "Progressive JLPT difficulty across levels")
                CUSTOM -> LanguageLevelDescriptor(customLabel.ifBlank { "Custom Track" }, "CUSTOM", "Custom Japanese track")
            }
            norm == "chinese" || norm == "mandarin" || norm == "mandarin chinese" || norm == "zh" -> when (this) {
                BEGINNER -> LanguageLevelDescriptor("HSK 1 (Foundational Survival)", "HSK 1", "Foundational Pinyin and essential survival vocabulary")
                ELEMENTARY -> LanguageLevelDescriptor("HSK 2–3 (Routine Dialogue)", "HSK 2-3", "Routine everyday dialogues, shopping & practical phrases")
                INTERMEDIATE -> LanguageLevelDescriptor("HSK 4 (General Fluency)", "HSK 4", "Conversational fluency across general topics and media")
                UPPER_INTERMEDIATE -> LanguageLevelDescriptor("HSK 5 (News & Professional)", "HSK 5", "Newspapers, films, professional & workplace discussions")
                ADVANCED -> LanguageLevelDescriptor("HSK 6 (Advanced Academic)", "HSK 6", "Formal prose, expressive idioms (chengyu) & high-register essays")
                MASTERY -> LanguageLevelDescriptor("HSK 7–9 (Classical & Mastery)", "HSK 7-9", "Classical nuances (wenyan), specialized academic & literary mastery")
                MIXED -> LanguageLevelDescriptor("Mixed / Progressive", "MIXED", "Progressive HSK difficulty across levels")
                CUSTOM -> LanguageLevelDescriptor(customLabel.ifBlank { "Custom Track" }, "CUSTOM", "Custom Mandarin track")
            }
            norm == "korean" || norm == "ko" -> when (this) {
                BEGINNER -> LanguageLevelDescriptor("TOPIK I-1 (Beginner Survival)", "TOPIK 1", "Basic survival Hangul, greetings and core everyday items")
                ELEMENTARY -> LanguageLevelDescriptor("TOPIK I-2 (Routine Expressions)", "TOPIK 2", "Daily telephone, shopping, public transport and routine situations")
                INTERMEDIATE -> LanguageLevelDescriptor("TOPIK II-3 (Practical Fluency)", "TOPIK 3", "General social conversations, common news & familiar issues")
                UPPER_INTERMEDIATE -> LanguageLevelDescriptor("TOPIK II-4 (Workplace & Formal)", "TOPIK 4", "Workplace register, news articles & social/cultural topics")
                ADVANCED -> LanguageLevelDescriptor("TOPIK II-5 (Editorial & Analytical)", "TOPIK 5", "Professional research, editorial debates & analytical discourse")
                MASTERY -> LanguageLevelDescriptor("TOPIK II-6 (Literary & Specialist)", "TOPIK 6", "Specialist fields, nuanced idioms (sajaseong-eo) & literary style")
                MIXED -> LanguageLevelDescriptor("Mixed / Progressive", "MIXED", "Progressive TOPIK difficulty across levels")
                CUSTOM -> LanguageLevelDescriptor(customLabel.ifBlank { "Custom Track" }, "CUSTOM", "Custom Korean track")
            }
            norm == "sanskrit" || norm == "sa" -> when (this) {
                BEGINNER -> LanguageLevelDescriptor("Prathamā • प्रथमा (Roots & Gita Basics)", "प्रथमा", "Foundational dhatu roots, pronouns & basic Gita shlokas")
                ELEMENTARY -> LanguageLevelDescriptor("Madhyamā • मध्यमा (Prose & Subhashita)", "मध्यमा", "Common narrative prose (Panchatantra, Hitopadesha) & subhashitas")
                INTERMEDIATE -> LanguageLevelDescriptor("Shāstrī • शास्त्री (Narrative & Drama)", "शास्त्री", "Standard classical kavya, nataka (Kalidasa) & narrative fluency")
                UPPER_INTERMEDIATE -> LanguageLevelDescriptor("Kāvya • काव्य (Classical Literature)", "काव्य", "Complex samasa compounds, figures of speech & classical poetry")
                ADVANCED -> LanguageLevelDescriptor("Āchārya • आचार्य (Darshana & Shastra)", "आचार्य", "Shastric debates, Upanishadic & philosophical terminology")
                MASTERY -> LanguageLevelDescriptor("Vaidika • वैदिक (Vedic & Arsha Nuances)", "वैदिक", "Arsha prayoga, Vedic accents & rare classical nuances")
                MIXED -> LanguageLevelDescriptor("Mixed / Progressive", "MIXED", "Progressive Sanskrit textual progression")
                CUSTOM -> LanguageLevelDescriptor(customLabel.ifBlank { "Custom Track" }, "CUSTOM", "Custom Sanskrit track")
            }
            norm in listOf("hindi", "hi", "urdu", "ur", "bengali", "bn", "marathi", "mr", "tamil", "ta", "telugu", "te", "gujarati", "gu", "punjabi", "pa", "kannada", "kn", "malayalam", "ml", "odia") -> when (this) {
                BEGINNER -> LanguageLevelDescriptor("Everyday Spoken • बोलचाल (A1)", "बोलचाल", "Core spoken vocabulary used in immediate daily interactions")
                ELEMENTARY -> LanguageLevelDescriptor("Routine Practical • दैनिक (A2)", "दैनिक", "Practical neighborhood, market, travel & routine sentences")
                INTERMEDIATE -> LanguageLevelDescriptor("General Fluency • सामान्य (B1)", "सामान्य", "Standard conversational fluency across general life and media")
                UPPER_INTERMEDIATE -> LanguageLevelDescriptor("Formal & Workplace • दफ्तरी (B2)", "दफ्तरी", "Formal register, workplace communication & standard journalism")
                ADVANCED -> LanguageLevelDescriptor("Editorial & High • संपादकीय (C1)", "संपादकीय", "Editorial columns, academic analysis & literary essays")
                MASTERY -> LanguageLevelDescriptor("Literary & Nuanced • साहित्यिक (C2)", "साहित्यिक", "Classical idioms, poetic nuance, high-register and rare vocabulary")
                MIXED -> LanguageLevelDescriptor("Mixed / Progressive", "MIXED", "Progressive register difficulty across levels")
                CUSTOM -> LanguageLevelDescriptor(customLabel.ifBlank { "Custom Track" }, "CUSTOM", "Custom register track")
            }
            norm == "arabic" || norm == "ar" || norm == "persian" || norm == "fa" -> when (this) {
                BEGINNER -> LanguageLevelDescriptor("Survival MSA • تواصل أولي (A1)", "أولي", "Core survival phrases, greetings and foundational words")
                ELEMENTARY -> LanguageLevelDescriptor("Everyday Practical • يومي عملي (A2)", "عملي", "Everyday practical conversations, directions & routine topics")
                INTERMEDIATE -> LanguageLevelDescriptor("General Media • إعلامي عام (B1)", "إعلامي", "Mainstream news broadcasts, articles & independent fluency")
                UPPER_INTERMEDIATE -> LanguageLevelDescriptor("Formal & Editorial • رسمي وتحليلي (B2)", "رسمي", "Formal political/economic discourse & opinion editorials")
                ADVANCED -> LanguageLevelDescriptor("Academic & Analytical • أكاديمي (C1)", "أكاديمي", "Scholarly publications, intellectual debates & literary nuance")
                MASTERY -> LanguageLevelDescriptor("Classical & Balaaghah • بلاغة وتراثي (C2)", "بلاغة", "Classical language (Fus'ha Turathi), rhetoric & eloquent lexicon")
                MIXED -> LanguageLevelDescriptor("Mixed / Progressive", "MIXED", "Progressive register difficulty across levels")
                CUSTOM -> LanguageLevelDescriptor(customLabel.ifBlank { "Custom Track" }, "CUSTOM", "Custom Arabic track")
            }
            else -> when (this) {
                BEGINNER -> LanguageLevelDescriptor("Beginner (A1)", "A1", "Core everyday survival words & expressions")
                ELEMENTARY -> LanguageLevelDescriptor("Elementary (A2)", "A2", "Routine conversations, travel & common topics")
                INTERMEDIATE -> LanguageLevelDescriptor("Intermediate (B1)", "B1", "Independent reading, news & everyday fluency")
                UPPER_INTERMEDIATE -> LanguageLevelDescriptor("Upper-Intermediate (B2)", "B2", "Nuanced discussion, workplace & standard exams")
                ADVANCED -> LanguageLevelDescriptor("Advanced (C1)", "C1", "Academic, editorial & high-precision vocabulary")
                MASTERY -> LanguageLevelDescriptor("Mastery (C2)", "C2", "Literary, rare & domain-specialist nuance")
                MIXED -> LanguageLevelDescriptor("Mixed / Progressive", "MIXED", "Spans multiple difficulty tiers")
                CUSTOM -> LanguageLevelDescriptor(customLabel.ifBlank { "Custom Level" }, "CUSTOM", "Track-specific level")
            }
        }
    }

    fun displayTitleForLanguage(language: String, customLabel: String = ""): String =
        descriptorForLanguage(language, customLabel).title

    fun compactBadgeForLanguage(language: String, customLabel: String = ""): String =
        descriptorForLanguage(language, customLabel).shortBadge

    fun descriptionForLanguage(language: String): String =
        descriptorForLanguage(language).description

    fun displayTitle(customLabel: String = ""): String =
        displayTitleForLanguage("", customLabel)

    fun compactBadge(customLabel: String = ""): String =
        compactBadgeForLanguage("", customLabel)

    companion object {
        fun folderSelectableLevels(): List<VocabularyLevel> =
            listOf(BEGINNER, ELEMENTARY, INTERMEDIATE, UPPER_INTERMEDIATE, ADVANCED, MASTERY)

        fun promptSelectableLevels(): List<VocabularyLevel> =
            listOf(BEGINNER, ELEMENTARY, INTERMEDIATE, UPPER_INTERMEDIATE, ADVANCED, MASTERY, MIXED, CUSTOM)

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

    fun effectiveLevelBadge(lang: String = this.language): String {
        val lvl = resolvedLevel()
        return if (lvl == VocabularyLevel.CUSTOM && customLevelLabel.isNotBlank()) {
            customLevelLabel.trim().take(12)
        } else {
            lvl.compactBadgeForLanguage(lang.ifBlank { this.language }, customLevelLabel)
        }
    }

    fun effectiveLevelTitle(lang: String = this.language): String =
        resolvedLevel().displayTitleForLanguage(lang.ifBlank { this.language }, customLevelLabel)

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
            val trimmed = raw.trim()
                .replace("\u00AD", "")
                .replace(Regex("[\u2018\u2019\u02BC]"), "'")
                .replace(Regex("[\u2010\u2011\u2012\u2013\u2014]"), "-")
                .replace(Regex("\\s+"), " ")
            if (trimmed.isBlank()) return ""
            val nfkd = Normalizer.normalize(trimmed, Normalizer.Form.NFKD)
            val strippedCombining = nfkd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            return strippedCombining
                .lowercase(Locale.ROOT)
                .replace(Regex("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$"), "")
        }

        /**
         * Secondary comparison key that collapses internal hyphens and spaces for Latin-script
         * spelling variants (e.g., "psycho-pathic" vs "psychopathic") without altering non-Latin terms.
         */
        fun canonicalSpellingMatchKey(raw: String): String {
            val base = normalizeVocabularyWordKey(raw)
            if (base.isBlank()) return ""
            val hasAsciiLetter = base.any { it in 'a'..'z' }
            return if (hasAsciiLetter) {
                base.replace(Regex("[-\\s']+"), "")
            } else {
                base
            }
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

    fun effectiveLevelTitle(lang: String = this.language): String =
        resolvedLevel().displayTitleForLanguage(lang.ifBlank { this.language }, customLevelLabel)

    fun effectiveLevelBadge(lang: String = this.language): String =
        resolvedLevel().compactBadgeForLanguage(lang.ifBlank { this.language }, customLevelLabel)

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

enum class VocabularyUtilityTier(val title: String, val subtitle: String) {
    DAILY_HIGH_FREQUENCY("Daily High-Frequency", "Core high-yield words used in daily life, conversations, and media"),
    EXAM_EDITORIAL("Competitive Exam Editorial", "High-impact analytical vocabulary from editorials (The Hindu, Indian Express, UPSC, SSC, Banking)"),
    LITERARY_PRECISION("Literary & Formal Precision", "Formal, expressive words for academic papers, essays, and literature"),
    GENERAL_BALANCED("General Balanced", "Balanced practical vocabulary across multiple domains")
}

data class HeadwordQueueItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val word: String,
    val language: String = "English",
    val levelCode: String = "B2",
    val customLevelLabel: String = "",
    val partOfSpeech: String = "",
    val domain: String = "Competitive Exam Editorial",
    val utilityReason: String = "",
    val addedAt: Long = System.currentTimeMillis()
) {
    fun normalizedWordKey(): String = VocabularyEntry.normalizeVocabularyWordKey(word)

    fun resolvedLevel(): VocabularyLevel = VocabularyLevel.fromCode(levelCode)

    fun effectiveLevelBadge(lang: String = this.language): String {
        val lvl = resolvedLevel()
        return if (lvl == VocabularyLevel.CUSTOM && customLevelLabel.isNotBlank()) {
            customLevelLabel.trim().take(12)
        } else {
            lvl.compactBadgeForLanguage(lang.ifBlank { this.language }, customLevelLabel)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("word", word.trim())
        put("language", language.trim())
        put("levelCode", levelCode.trim())
        put("customLevelLabel", customLevelLabel.trim())
        put("partOfSpeech", partOfSpeech.trim())
        put("domain", domain.trim())
        put("utilityReason", utilityReason.trim())
        put("addedAt", addedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject?): HeadwordQueueItem? {
            if (obj == null) return null
            val word = obj.optString("word", "").trim()
            if (word.isBlank()) return null
            return HeadwordQueueItem(
                id = obj.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                word = word,
                language = obj.optString("language", "English").trim().ifBlank { "English" },
                levelCode = obj.optString("levelCode", "B2").trim().ifBlank { "B2" },
                customLevelLabel = obj.optString("customLevelLabel", "").trim(),
                partOfSpeech = obj.optString("partOfSpeech", "").trim(),
                domain = obj.optString("domain", "General").trim().ifBlank { "General" },
                utilityReason = obj.optString("utilityReason", "").trim(),
                addedAt = obj.optLong("addedAt", System.currentTimeMillis())
            )
        }
    }
}

data class HeadwordFilterResult(
    val addedItems: List<HeadwordQueueItem>,
    val skippedAlreadyInDeckCount: Int,
    val skippedAlreadyInQueueCount: Int,
    val skippedDismissedCount: Int,
    val totalProcessedCount: Int,
    val skippedOffLevelCount: Int = 0
)

