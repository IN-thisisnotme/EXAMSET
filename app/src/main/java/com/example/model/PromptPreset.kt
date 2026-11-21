package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PromptPresetMode(val code: String, val label: String) {
    QUESTION_SET("QUESTION_SET", "Question Set"),
    KNOWLEDGE_SET("KNOWLEDGE_SET", "Knowledge Set"),
    FLASHCARD_SET("FLASHCARD_SET", "Flashcard Set"),
    VOCABULARY("VOCABULARY", "Vocabulary");

    companion object {
        fun fromCode(code: String?): PromptPresetMode {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: QUESTION_SET
        }
    }
}

data class QuestionPromptConfig(
    val examType: String = "",
    val subject: String = "",
    val questionCount: Int = 20,
    val difficulty: String = "Moderate",
    val questionType: String = "Mixed Formats",
    val questionLanguage: String = "English",
    val explanationChoice: String = "Same as Question",
    val customExplanationLanguage: String = "",
    val optionCount: Int = 4,
    val explainAllFourOptions: Boolean = true,
    val includeFormulasAndDiagrams: Boolean = true,
    val promptPhasingStyle: String = "Standard",
    val targetOutputFormat: String = "Strict JSON",
    val promptDensity: String = "Concise",
    val enableQuantityCorrectionRule: Boolean = false
) {
    fun normalized(): QuestionPromptConfig = copy(
        examType = examType.trim(),
        subject = subject.trim(),
        questionCount = questionCount.coerceIn(5, 200),
        difficulty = difficulty.trim().ifBlank { "Moderate" },
        questionType = questionType.trim().ifBlank { "Mixed Formats" },
        questionLanguage = questionLanguage.trim().ifBlank { "English" },
        explanationChoice = explanationChoice.trim().ifBlank { "Same as Question" },
        customExplanationLanguage = customExplanationLanguage.trim(),
        optionCount = if (optionCount == 5) 5 else 4,
        promptPhasingStyle = promptPhasingStyle.trim().ifBlank { "Standard" },
        targetOutputFormat = targetOutputFormat.trim().ifBlank { "Strict JSON" },
        promptDensity = promptDensity.trim().ifBlank { "Concise" }
    )

    fun formatBadge(): String = when (targetOutputFormat) {
        "Strict JSON" -> "JSON · ${optionCount}-Opt"
        "Markdown Table" -> "TABLE · ${optionCount}-Opt"
        else -> "PLAIN · ${optionCount}-Opt"
    }

    fun specChips(): List<String> = buildList {
        if (examType.isNotBlank()) add(abbreviateTag(examType, 18))
        if (subject.isNotBlank()) add(abbreviateTag(subject, 18))
        add("${questionCount}Q")
        add(difficulty)
        val langChip = when {
            explanationChoice == "Bilingual" -> "Bilingual"
            !questionLanguage.equals("English", ignoreCase = true) -> questionLanguage
            explanationChoice == "Hindi" -> "Eng+Hin"
            explanationChoice == "Custom" && customExplanationLanguage.isNotBlank() -> customExplanationLanguage
            else -> "English"
        }
        add(abbreviateTag(langChip, 14))
        if (includeFormulasAndDiagrams) add("LaTeX")
        if (enableQuantityCorrectionRule) add("Guard")
    }.take(6)

    fun suggestName(): String {
        val parts = buildList {
            if (examType.isNotBlank()) add(abbreviateTag(examType, 18))
            if (subject.isNotBlank()) add(abbreviateTag(subject, 16))
            add("${questionCount}Q")
            add(
                when (targetOutputFormat) {
                    "Strict JSON" -> "JSON"
                    "Markdown Table" -> "Table"
                    else -> "Plain"
                }
            )
        }
        return parts.joinToString(" • ").ifBlank { "Question Preset ${questionCount}Q" }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("examType", examType)
        put("subject", subject)
        put("questionCount", questionCount)
        put("difficulty", difficulty)
        put("questionType", questionType)
        put("questionLanguage", questionLanguage)
        put("explanationChoice", explanationChoice)
        put("customExplanationLanguage", customExplanationLanguage)
        put("optionCount", optionCount)
        put("explainAllFourOptions", explainAllFourOptions)
        put("includeFormulasAndDiagrams", includeFormulasAndDiagrams)
        put("promptPhasingStyle", promptPhasingStyle)
        put("targetOutputFormat", targetOutputFormat)
        put("promptDensity", promptDensity)
        put("enableQuantityCorrectionRule", enableQuantityCorrectionRule)
    }

    companion object {
        fun fromJson(obj: JSONObject?): QuestionPromptConfig {
            if (obj == null) return QuestionPromptConfig()
            return QuestionPromptConfig(
                examType = obj.optString("examType", ""),
                subject = obj.optString("subject", ""),
                questionCount = obj.optInt("questionCount", 20).coerceIn(5, 200),
                difficulty = obj.optString("difficulty", "Moderate").ifBlank { "Moderate" },
                questionType = obj.optString("questionType", "Mixed Formats").ifBlank { "Mixed Formats" },
                questionLanguage = obj.optString("questionLanguage", "English").ifBlank { "English" },
                explanationChoice = obj.optString("explanationChoice", "Same as Question").ifBlank { "Same as Question" },
                customExplanationLanguage = obj.optString("customExplanationLanguage", ""),
                optionCount = if (obj.optInt("optionCount", 4) == 5) 5 else 4,
                explainAllFourOptions = obj.optBoolean("explainAllFourOptions", true),
                includeFormulasAndDiagrams = obj.optBoolean("includeFormulasAndDiagrams", true),
                promptPhasingStyle = obj.optString("promptPhasingStyle", "Standard").ifBlank { "Standard" },
                targetOutputFormat = obj.optString("targetOutputFormat", "Strict JSON").ifBlank { "Strict JSON" },
                promptDensity = obj.optString("promptDensity", "Concise").ifBlank { "Concise" },
                enableQuantityCorrectionRule = obj.optBoolean("enableQuantityCorrectionRule", false)
            ).normalized()
        }
    }
}

data class KnowledgePromptConfig(
    val examTarget: String = "",
    val subject: String = "",
    val collectionType: KnowledgeCollectionType = KnowledgeCollectionType.MULTI_LINER,
    val customCollectionLabel: String = "",
    val itemCount: Int = 20,
    val depth: KnowledgeDepth = KnowledgeDepth.BALANCED,
    val layerOrder: KnowledgeLayerOrder = KnowledgeLayerOrder.AUTO_DETECT,
    val language: String = "English",
    val includeBilingualTerms: Boolean = false,
    val preserveNestedBullets: Boolean = true,
    val includeLatexMath: Boolean = true,
    val includeCallouts: Boolean = true,
    val includeComparisonTable: Boolean = false,
    val includePyqTags: Boolean = false,
    val customInstruction: String = ""
) {
    fun normalized(): KnowledgePromptConfig = copy(
        examTarget = examTarget.trim(),
        subject = subject.trim(),
        customCollectionLabel = customCollectionLabel.trim(),
        itemCount = itemCount.coerceIn(3, 120),
        language = language.trim().ifBlank { "English" },
        customInstruction = customInstruction.trim()
    )

    fun formatBadge(): String {
        return if (collectionType == KnowledgeCollectionType.CUSTOM && customCollectionLabel.isNotBlank()) {
            abbreviateTag(customCollectionLabel.uppercase(), 14)
        } else {
            collectionType.shortBadge
        }
    }

    fun specChips(): List<String> = buildList {
        if (examTarget.isNotBlank()) add(abbreviateTag(examTarget, 18))
        if (subject.isNotBlank()) add(abbreviateTag(subject, 18))
        add("$itemCount Items")
        add(
            when (depth) {
                KnowledgeDepth.CRISP -> "Crisp"
                KnowledgeDepth.BALANCED -> "Balanced"
                KnowledgeDepth.DEEP -> "Deep"
            }
        )
        if (includeBilingualTerms) {
            add("Bilingual")
        } else if (!language.equals("English", ignoreCase = true)) {
            add(abbreviateTag(language, 14))
        }
        if (includePyqTags) add("PYQ")
        if (includeComparisonTable) add("Table")
        if (customInstruction.isNotBlank()) add("Custom Rule")
    }.take(6)

    fun suggestName(): String {
        val parts = buildList {
            if (examTarget.isNotBlank()) add(abbreviateTag(examTarget, 18))
            if (subject.isNotBlank()) add(abbreviateTag(subject, 16))
            add(
                if (collectionType == KnowledgeCollectionType.CUSTOM && customCollectionLabel.isNotBlank()) {
                    abbreviateTag(customCollectionLabel, 16)
                } else {
                    collectionType.label
                }
            )
        }
        return parts.joinToString(" • ").ifBlank { "${collectionType.label} ($itemCount)" }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("examTarget", examTarget)
        put("subject", subject)
        put("collectionType", collectionType.code)
        put("customCollectionLabel", customCollectionLabel)
        put("itemCount", itemCount)
        put("depth", depth.name)
        put("layerOrder", layerOrder.name)
        put("language", language)
        put("includeBilingualTerms", includeBilingualTerms)
        put("preserveNestedBullets", preserveNestedBullets)
        put("includeLatexMath", includeLatexMath)
        put("includeCallouts", includeCallouts)
        put("includeComparisonTable", includeComparisonTable)
        put("includePyqTags", includePyqTags)
        put("customInstruction", customInstruction)
    }

    companion object {
        fun fromJson(obj: JSONObject?): KnowledgePromptConfig {
            if (obj == null) return KnowledgePromptConfig()
            val type = KnowledgeCollectionType.fromCode(obj.optString("collectionType", KnowledgeCollectionType.MULTI_LINER.code))
            val depth = runCatching {
                KnowledgeDepth.valueOf(obj.optString("depth", KnowledgeDepth.BALANCED.name))
            }.getOrDefault(KnowledgeDepth.BALANCED)
            val layerOrder = runCatching {
                KnowledgeLayerOrder.valueOf(obj.optString("layerOrder", KnowledgeLayerOrder.AUTO_DETECT.name))
            }.getOrDefault(KnowledgeLayerOrder.AUTO_DETECT)
            return KnowledgePromptConfig(
                examTarget = obj.optString("examTarget", ""),
                subject = obj.optString("subject", ""),
                collectionType = type,
                customCollectionLabel = obj.optString("customCollectionLabel", ""),
                itemCount = obj.optInt("itemCount", 20).coerceIn(3, 120),
                depth = depth,
                layerOrder = layerOrder,
                language = obj.optString("language", "English").ifBlank { "English" },
                includeBilingualTerms = obj.optBoolean("includeBilingualTerms", false),
                preserveNestedBullets = obj.optBoolean("preserveNestedBullets", true),
                includeLatexMath = obj.optBoolean("includeLatexMath", true),
                includeCallouts = obj.optBoolean("includeCallouts", true),
                includeComparisonTable = obj.optBoolean("includeComparisonTable", false),
                includePyqTags = obj.optBoolean("includePyqTags", false),
                customInstruction = obj.optString("customInstruction", "")
            ).normalized()
        }
    }
}

data class FlashcardPromptConfig(
    val examTarget: String = "",
    val subject: String = "",
    val cardStyle: FlashcardPromptStyle = FlashcardPromptStyle.QA_RETRIEVAL,
    val cardCount: Int = 20,
    val depth: KnowledgeDepth = KnowledgeDepth.BALANCED,
    val language: String = "English",
    val includeBilingualTerms: Boolean = false,
    val atomicSrsMode: Boolean = true,
    val includeMnemonics: Boolean = true,
    val includeExamples: Boolean = true,
    val includeLatexMath: Boolean = true,
    val includeExamTraps: Boolean = true,
    val customInstruction: String = ""
) {
    fun normalized(): FlashcardPromptConfig = copy(
        examTarget = examTarget.trim(),
        subject = subject.trim(),
        cardCount = cardCount.coerceIn(5, 100),
        language = language.trim().ifBlank { "English" },
        customInstruction = customInstruction.trim()
    )

    fun formatBadge(): String = cardStyle.shortBadge

    fun specChips(): List<String> = buildList {
        if (examTarget.isNotBlank()) add(abbreviateTag(examTarget, 18))
        if (subject.isNotBlank()) add(abbreviateTag(subject, 18))
        add("$cardCount Cards")
        add(
            when (depth) {
                KnowledgeDepth.CRISP -> "Crisp"
                KnowledgeDepth.BALANCED -> "Balanced"
                KnowledgeDepth.DEEP -> "Deep"
            }
        )
        if (includeBilingualTerms) add("Bilingual")
        if (includeExamTraps) add("Traps")
        if (customInstruction.isNotBlank()) add("Custom Rule")
    }.take(6)

    fun suggestName(): String {
        val parts = buildList {
            if (examTarget.isNotBlank()) add(abbreviateTag(examTarget, 18))
            if (subject.isNotBlank()) add(abbreviateTag(subject, 16))
            add("${cardCount} Cards")
            add(cardStyle.shortBadge)
        }
        return parts.joinToString(" • ").ifBlank { "Flashcards ${cardCount}C" }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("examTarget", examTarget)
        put("subject", subject)
        put("cardStyle", cardStyle.name)
        put("cardCount", cardCount)
        put("depth", depth.name)
        put("language", language)
        put("includeBilingualTerms", includeBilingualTerms)
        put("atomicSrsMode", atomicSrsMode)
        put("includeMnemonics", includeMnemonics)
        put("includeExamples", includeExamples)
        put("includeLatexMath", includeLatexMath)
        put("includeExamTraps", includeExamTraps)
        put("customInstruction", customInstruction)
    }

    companion object {
        fun fromJson(obj: JSONObject?): FlashcardPromptConfig {
            if (obj == null) return FlashcardPromptConfig()
            val style = runCatching {
                FlashcardPromptStyle.valueOf(obj.optString("cardStyle", FlashcardPromptStyle.QA_RETRIEVAL.name))
            }.getOrDefault(FlashcardPromptStyle.QA_RETRIEVAL)
            val depth = runCatching {
                KnowledgeDepth.valueOf(obj.optString("depth", KnowledgeDepth.BALANCED.name))
            }.getOrDefault(KnowledgeDepth.BALANCED)
            return FlashcardPromptConfig(
                examTarget = obj.optString("examTarget", ""),
                subject = obj.optString("subject", ""),
                cardStyle = style,
                cardCount = obj.optInt("cardCount", 20).coerceIn(5, 100),
                depth = depth,
                language = obj.optString("language", "English").ifBlank { "English" },
                includeBilingualTerms = obj.optBoolean("includeBilingualTerms", false),
                atomicSrsMode = obj.optBoolean("atomicSrsMode", true),
                includeMnemonics = obj.optBoolean("includeMnemonics", true),
                includeExamples = obj.optBoolean("includeExamples", true),
                includeLatexMath = obj.optBoolean("includeLatexMath", true),
                includeExamTraps = obj.optBoolean("includeExamTraps", true),
                customInstruction = obj.optString("customInstruction", "")
            ).normalized()
        }
    }
}

data class VocabularyPromptConfig(
    val language: String = "English",
    val languageVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT,
    val translationLanguage: String = "",
    val levelCode: String = VocabularyLevel.INTERMEDIATE.code,
    val customLevelLabel: String = "",
    val domain: String = "General",
    val wordCount: Int = 20,
    val examplesPerWord: Int = 3,
    val outputFormat: String = "Strict JSON",
    val includePronunciation: Boolean = true,
    val includeSynonymsAndCollocations: Boolean = true,
    val includeUsageNote: Boolean = true,
    val isHeadwordIndexOnly: Boolean = false,
    val utilityTier: VocabularyUtilityTier = VocabularyUtilityTier.EXAM_EDITORIAL,
    val customInstruction: String = ""
) {
    fun normalized(): VocabularyPromptConfig {
        val cleanLang = language.trim().ifBlank { "English" }
        val normVariant = VocabularyLanguageVariants.normalizeVariant(cleanLang, languageVariant)
        return copy(
            language = cleanLang,
            languageVariant = normVariant,
            translationLanguage = translationLanguage.trim(),
            levelCode = VocabularyLevel.fromCode(levelCode).code,
            customLevelLabel = customLevelLabel.trim(),
            domain = domain.trim().ifBlank { "General" },
            wordCount = wordCount.coerceIn(5, 60),
            examplesPerWord = examplesPerWord.coerceIn(1, 4),
            outputFormat = if (isHeadwordIndexOnly) {
                "Strict JSON"
            } else if (outputFormat.equals("Structured Text", ignoreCase = true)) {
                "Structured Text"
            } else {
                "Strict JSON"
            },
            customInstruction = customInstruction.trim()
        )
    }

    fun formatBadge(): String {
        val lvl = VocabularyLevel.fromCode(levelCode).compactBadge(customLevelLabel)
        val fmt = when {
            isHeadwordIndexOnly -> "INDEX"
            outputFormat == "Structured Text" -> "TEXT"
            else -> "JSON"
        }
        return "$fmt · $lvl"
    }

    fun specChips(): List<String> = buildList {
        val langTag = if (languageVariant.isNotBlank() && !languageVariant.equals("Neutral", ignoreCase = true)) {
            "${abbreviateTag(language, 12)} (${VocabularyLanguageVariants.shortBadge(languageVariant)})"
        } else {
            abbreviateTag(language, 14)
        }
        add(langTag)
        if (isHeadwordIndexOnly) {
            add("Stage-1 Index")
            add(abbreviateTag(utilityTier.title, 18))
        } else if (translationLanguage.isNotBlank()) {
            add("-> ${abbreviateTag(translationLanguage, 12)}")
        }
        add(VocabularyLevel.fromCode(levelCode).displayTitle(customLevelLabel))
        add("$wordCount Words")
        if (!isHeadwordIndexOnly) add("${examplesPerWord} Ex/Word")
        if (!domain.equals("General", ignoreCase = true)) add(abbreviateTag(domain, 14))
        if (customInstruction.isNotBlank()) add("Custom Rule")
    }.take(6)

    fun suggestName(): String {
        val lvl = VocabularyLevel.fromCode(levelCode).displayTitle(customLevelLabel)
        val langPart = if (languageVariant.isNotBlank() && !languageVariant.equals("Neutral", ignoreCase = true)) {
            "${abbreviateTag(language, 12)} (${VocabularyLanguageVariants.shortBadge(languageVariant)})"
        } else {
            abbreviateTag(language, 14)
        }
        val parts = buildList {
            add(langPart)
            add(lvl)
            if (isHeadwordIndexOnly) add("Headword Index")
            if (!domain.equals("General", ignoreCase = true)) add(abbreviateTag(domain, 14))
            if (!isHeadwordIndexOnly) add(if (outputFormat == "Structured Text") "Text" else "JSON")
        }
        return parts.joinToString(" • ").ifBlank { "Vocabulary $lvl" }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("language", language)
        put("languageVariant", languageVariant)
        put("translationLanguage", translationLanguage)
        put("levelCode", levelCode)
        put("customLevelLabel", customLevelLabel)
        put("domain", domain)
        put("wordCount", wordCount)
        put("examplesPerWord", examplesPerWord)
        put("outputFormat", outputFormat)
        put("includePronunciation", includePronunciation)
        put("includeSynonymsAndCollocations", includeSynonymsAndCollocations)
        put("includeUsageNote", includeUsageNote)
        put("isHeadwordIndexOnly", isHeadwordIndexOnly)
        put("utilityTier", utilityTier.name)
        put("customInstruction", customInstruction)
    }

    companion object {
        fun fromJson(obj: JSONObject?): VocabularyPromptConfig {
            if (obj == null) return VocabularyPromptConfig()
            val lang = obj.optString("language", "English").ifBlank { "English" }
            val defaultVar = if (VocabularyLanguageVariants.isEnglish(lang)) VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT else ""
            val tierName = obj.optString("utilityTier", VocabularyUtilityTier.EXAM_EDITORIAL.name)
            val tier = runCatching {
                VocabularyUtilityTier.valueOf(tierName)
            }.getOrDefault(VocabularyUtilityTier.EXAM_EDITORIAL)
            return VocabularyPromptConfig(
                language = lang,
                languageVariant = obj.optString("languageVariant", defaultVar),
                translationLanguage = obj.optString("translationLanguage", ""),
                levelCode = obj.optString("levelCode", VocabularyLevel.INTERMEDIATE.code),
                customLevelLabel = obj.optString("customLevelLabel", ""),
                domain = obj.optString("domain", "General").ifBlank { "General" },
                wordCount = obj.optInt("wordCount", 20).coerceIn(5, 60),
                examplesPerWord = obj.optInt("examplesPerWord", 3).coerceIn(1, 4),
                outputFormat = obj.optString("outputFormat", "Strict JSON"),
                includePronunciation = obj.optBoolean("includePronunciation", true),
                includeSynonymsAndCollocations = obj.optBoolean("includeSynonymsAndCollocations", true),
                includeUsageNote = obj.optBoolean("includeUsageNote", true),
                isHeadwordIndexOnly = obj.optBoolean("isHeadwordIndexOnly", false),
                utilityTier = tier,
                customInstruction = obj.optString("customInstruction", "")
            ).normalized()
        }
    }
}

data class PromptPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mode: PromptPresetMode,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L,
    val questionConfig: QuestionPromptConfig? = null,
    val knowledgeConfig: KnowledgePromptConfig? = null,
    val flashcardConfig: FlashcardPromptConfig? = null,
    val vocabularyConfig: VocabularyPromptConfig? = null
) {
    fun formatBadge(): String = when (mode) {
        PromptPresetMode.QUESTION_SET -> (questionConfig ?: QuestionPromptConfig()).formatBadge()
        PromptPresetMode.KNOWLEDGE_SET -> (knowledgeConfig ?: KnowledgePromptConfig()).formatBadge()
        PromptPresetMode.FLASHCARD_SET -> (flashcardConfig ?: FlashcardPromptConfig()).formatBadge()
        PromptPresetMode.VOCABULARY -> (vocabularyConfig ?: VocabularyPromptConfig()).formatBadge()
    }

    fun specChips(): List<String> = when (mode) {
        PromptPresetMode.QUESTION_SET -> (questionConfig ?: QuestionPromptConfig()).specChips()
        PromptPresetMode.KNOWLEDGE_SET -> (knowledgeConfig ?: KnowledgePromptConfig()).specChips()
        PromptPresetMode.FLASHCARD_SET -> (flashcardConfig ?: FlashcardPromptConfig()).specChips()
        PromptPresetMode.VOCABULARY -> (vocabularyConfig ?: VocabularyPromptConfig()).specChips()
    }

    fun matchesSearch(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isBlank()) return true
        if (name.lowercase().contains(q)) return true
        if (formatBadge().lowercase().contains(q)) return true
        return specChips().any { it.lowercase().contains(q) }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("mode", mode.code)
        put("isPinned", isPinned)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("lastUsedAt", lastUsedAt)
        questionConfig?.let { put("questionConfig", it.toJson()) }
        knowledgeConfig?.let { put("knowledgeConfig", it.toJson()) }
        flashcardConfig?.let { put("flashcardConfig", it.toJson()) }
        vocabularyConfig?.let { put("vocabularyConfig", it.toJson()) }
    }

    companion object {
        fun fromJson(obj: JSONObject?): PromptPreset? {
            if (obj == null) return null
            val name = obj.optString("name", "").trim()
            if (name.isBlank()) return null
            val mode = PromptPresetMode.fromCode(obj.optString("mode"))
            val id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() }
            return PromptPreset(
                id = id,
                name = name,
                mode = mode,
                isPinned = obj.optBoolean("isPinned", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                lastUsedAt = obj.optLong("lastUsedAt", 0L),
                questionConfig = if (mode == PromptPresetMode.QUESTION_SET) {
                    QuestionPromptConfig.fromJson(obj.optJSONObject("questionConfig"))
                } else null,
                knowledgeConfig = if (mode == PromptPresetMode.KNOWLEDGE_SET) {
                    KnowledgePromptConfig.fromJson(obj.optJSONObject("knowledgeConfig"))
                } else null,
                flashcardConfig = if (mode == PromptPresetMode.FLASHCARD_SET) {
                    FlashcardPromptConfig.fromJson(obj.optJSONObject("flashcardConfig"))
                } else null,
                vocabularyConfig = if (mode == PromptPresetMode.VOCABULARY) {
                    VocabularyPromptConfig.fromJson(obj.optJSONObject("vocabularyConfig"))
                } else null
            )
        }

        fun serializeList(presets: List<PromptPreset>): String {
            val arr = JSONArray()
            presets.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun deserializeList(raw: String?): List<PromptPreset> {
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

        fun uniqueName(baseName: String, existingNames: Collection<String>, suffixWord: String? = null): String {
            val cleanBase = baseName.trim().ifBlank { "Untitled Preset" }
            val normalizedExisting = existingNames.map { it.trim().lowercase() }.toSet()
            val firstCandidate = if (suffixWord.isNullOrBlank()) cleanBase else "$cleanBase ($suffixWord)"
            if (firstCandidate.lowercase() !in normalizedExisting) return firstCandidate
            var counter = 2
            while (true) {
                val candidate = if (suffixWord.isNullOrBlank()) {
                    "$cleanBase ($counter)"
                } else {
                    "$cleanBase ($suffixWord $counter)"
                }
                if (candidate.lowercase() !in normalizedExisting) return candidate
                counter++
            }
        }
    }
}

private fun abbreviateTag(raw: String, maxLen: Int): String {
    val clean = raw.trim()
    return if (clean.length <= maxLen) clean else clean.take(maxLen - 1).trimEnd() + "…"
}
