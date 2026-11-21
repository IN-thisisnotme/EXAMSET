package com.example.util

import com.example.model.CompiledMixedVocabularyDeck
import com.example.model.DeckSrsStats
import com.example.model.FlashcardSrsState
import com.example.model.FlashcardSrsStatus
import com.example.model.VocabularyCompiledSource
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyExample
import com.example.model.VocabularyLanguageFolderSummary
import com.example.model.VocabularyLanguageVariants
import com.example.model.VocabularyLevel
import com.example.model.VocabularyLevelFolderSummary
import com.example.model.HeadwordQueueItem
import com.example.model.HeadwordFilterResult
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

enum class VocabularyInputFormat(val label: String) {
    JSON("Strict JSON"),
    STRUCTURED_TEXT("Structured Text"),
    UNKNOWN("Auto-Detected")
}

data class VocabularyParseResult(
    val entries: List<VocabularyEntry>,
    val detectedFormat: VocabularyInputFormat,
    val warnings: List<String> = emptyList(),
    val detectedLanguages: List<String> = emptyList(),
    val detectedVariant: String = "",
    val detectedTranslationLanguage: String = ""
)

data class VocabularyRouteBucket(
    val bucketKey: String,
    val language: String,
    val languageVariant: String,
    val translationLanguage: String,
    val levelCode: String,
    val customLevelLabel: String,
    val entries: List<VocabularyEntry>,
    val candidateDecks: List<VocabularyDeck>,
    val recommendedDeckId: String?,
    val titleSuggestions: List<String>,
    val defaultNewTitle: String
)

data class VocabularySmartRouteTargetChoice(
    val bucketKey: String,
    val targetDeckId: String?,
    val customTitle: String,
    val language: String,
    val languageVariant: String,
    val translationLanguage: String,
    val levelCode: String,
    val customLevelLabel: String,
    val domain: String = "General"
)

data class VocabularySmartRouteAnalysis(
    val analysis: VocabularyImportAnalysis,
    val entryBucketById: Map<String, String>
)

enum class VocabularyDuplicateAction(val label: String) {
    MERGE_SEGMENTS("Merge Segments"),
    SKIP("Skip"),
    REPLACE_EXISTING("Replace"),
    KEEP_SEPARATE("Keep Separate")
}

data class VocabularyMergeDecision(
    val action: VocabularyDuplicateAction = VocabularyDuplicateAction.MERGE_SEGMENTS,
    val mergeExamples: Boolean = true,
    val mergeTranslation: Boolean = true,
    val mergeSynonymsAndCollocations: Boolean = true,
    val mergeMeaningAndNote: Boolean = false,
    val mergeLevelAndMeta: Boolean = false
)

data class VocabularyDuplicateItem(
    val id: String,
    val incoming: VocabularyEntry,
    val existing: VocabularyEntry,
    val existingDeckId: String,
    val existingDeckTitle: String,
    val existingLevelTitle: String,
    val isSameDeck: Boolean,
    val isIntraBatch: Boolean
) {
    val hasNewExamples: Boolean
        get() {
            val existingKeys = existing.examples.map { it.normalizedSentenceKey() }.toSet()
            return incoming.examples.any { it.normalizedSentenceKey() !in existingKeys }
        }

    val hasNewTranslation: Boolean
        get() = incoming.translation.isNotBlank() &&
            !incoming.translation.equals(existing.translation, ignoreCase = true)

    val hasNewLexicalLinks: Boolean
        get() {
            val synKeys = existing.synonyms.map { it.lowercase(Locale.ROOT) }.toSet()
            val antKeys = existing.antonyms.map { it.lowercase(Locale.ROOT) }.toSet()
            val colKeys = existing.collocations.map { it.lowercase(Locale.ROOT) }.toSet()
            return incoming.synonyms.any { it.lowercase(Locale.ROOT) !in synKeys } ||
                incoming.antonyms.any { it.lowercase(Locale.ROOT) !in antKeys } ||
                incoming.collocations.any { it.lowercase(Locale.ROOT) !in colKeys }
        }

    val hasDifferentMeaningOrNote: Boolean
        get() = (incoming.meaning.isNotBlank() && !incoming.meaning.equals(existing.meaning, ignoreCase = true)) ||
            (incoming.usageNote.isNotBlank() && !incoming.usageNote.equals(existing.usageNote, ignoreCase = true))

    val hasDifferentLevelOrMeta: Boolean
        get() = !incoming.levelCode.equals(existing.levelCode, ignoreCase = true) ||
            (incoming.partOfSpeech.isNotBlank() && !incoming.partOfSpeech.equals(existing.partOfSpeech, ignoreCase = true)) ||
            (incoming.pronunciation.isNotBlank() && !incoming.pronunciation.equals(existing.pronunciation, ignoreCase = true))
}

data class VocabularyImportAnalysis(
    val uniqueNewEntries: List<VocabularyEntry>,
    val duplicateItems: List<VocabularyDuplicateItem>,
    val totalParsedCount: Int,
    val detectedFormat: VocabularyInputFormat,
    val warnings: List<String> = emptyList()
)

enum class VocabularyAuditStatus(val code: String, val label: String) {
    VERIFIED("VERIFIED", "Verified"),
    CATEGORY_ISSUE("CATEGORY_ISSUE", "Category / POS Fix"),
    LEVEL_MISMATCH("LEVEL_MISMATCH", "Level Mismatch"),
    MEANING_FIX("MEANING_FIX", "Meaning / Translation Fix"),
    EXAMPLE_FIX("EXAMPLE_FIX", "Example Context Fix"),
    DUPLICATE_ALERT("DUPLICATE_ALERT", "Duplicate Overlap");

    companion object {
        fun fromCode(raw: String?): VocabularyAuditStatus {
            val clean = raw?.trim()?.uppercase(Locale.ROOT).orEmpty()
            return entries.firstOrNull { it.code == clean || it.name == clean } ?: when {
                clean.contains("LEVEL") || clean.contains("CEFR") -> LEVEL_MISMATCH
                clean.contains("POS") || clean.contains("CATEG") || clean.contains("TYPE") -> CATEGORY_ISSUE
                clean.contains("MEAN") || clean.contains("DEF") || clean.contains("TRANS") -> MEANING_FIX
                clean.contains("EXAMP") || clean.contains("SENT") -> EXAMPLE_FIX
                clean.contains("DUP") || clean.contains("REPEAT") -> DUPLICATE_ALERT
                clean.contains("OK") || clean.contains("PASS") || clean.contains("VALID") -> VERIFIED
                else -> MEANING_FIX
            }
        }
    }
}

data class VocabularyAuditFinding(
    val entryId: String,
    val currentEntry: VocabularyEntry,
    val status: VocabularyAuditStatus,
    val issueSummary: String,
    val verifiedCategoryType: String = "",
    val suggestedPartOfSpeech: String? = null,
    val suggestedLevelCode: String? = null,
    val suggestedCustomLevelLabel: String? = null,
    val suggestedMeaning: String? = null,
    val suggestedTranslation: String? = null,
    val suggestedUsageNote: String? = null,
    val suggestedExamples: List<VocabularyExample>? = null
) {
    val hasActionableChanges: Boolean
        get() = status != VocabularyAuditStatus.VERIFIED && (
            !suggestedPartOfSpeech.isNullOrBlank() ||
                !suggestedLevelCode.isNullOrBlank() ||
                !suggestedMeaning.isNullOrBlank() ||
                !suggestedTranslation.isNullOrBlank() ||
                !suggestedUsageNote.isNullOrBlank() ||
                !suggestedExamples.isNullOrEmpty()
            )
}

data class VocabularyAuditReport(
    val findings: List<VocabularyAuditFinding>,
    val verifiedCount: Int,
    val flaggedCount: Int,
    val unmatchedWords: List<String> = emptyList()
)

/**
 * Pure Kotlin engine for parsing AI vocabulary outputs (JSON + Structured Text),
 * cross-deck duplicate analysis, granular segment merging, SM-2 deck stats,
 * and Audit Hub verification prompts & report parsing.
 */
object VocabularyEngine {

    fun parseVocabularyInput(
        rawInput: String,
        defaultLevelCode: String = VocabularyLevel.INTERMEDIATE.code,
        defaultCustomLevelLabel: String = "",
        preferredEnglishVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT
    ): VocabularyParseResult {
        val cleaned = stripOuterCodeFences(rawInput)
        if (cleaned.isBlank()) {
            return VocabularyParseResult(emptyList(), VocabularyInputFormat.UNKNOWN, listOf("Input is empty."))
        }

        // 1. Try JSON parser first when input looks like JSON
        val jsonAttempt = tryParseJsonVocabulary(cleaned, defaultLevelCode, defaultCustomLevelLabel)
        if (jsonAttempt != null && jsonAttempt.entries.isNotEmpty()) {
            return enrichParsedResultWithDetection(jsonAttempt, cleaned, preferredEnglishVariant)
        }

        // 2. Fall back to Structured Text parser
        val textResult = parseStructuredTextVocabulary(cleaned, defaultLevelCode, defaultCustomLevelLabel)
        if (textResult.entries.isNotEmpty()) {
            return enrichParsedResultWithDetection(textResult, cleaned, preferredEnglishVariant)
        }

        val jsonWarning = jsonAttempt?.warnings?.firstOrNull()
        return VocabularyParseResult(
            entries = emptyList(),
            detectedFormat = jsonAttempt?.detectedFormat ?: VocabularyInputFormat.UNKNOWN,
            warnings = listOf(
                jsonWarning
                    ?: "Could not detect structured vocabulary entries. Use JSON or labeled blocks (WORD:, MEANING:, EXAMPLES:)."
            )
        )
    }

    private fun normalizeClipboardArtifacts(raw: String): String {
        return raw
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\u2060", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
    }

    private fun stripOuterCodeFences(raw: String): String {
        val trimmed = normalizeClipboardArtifacts(raw).trim()
        val fencedRegex = Regex("```(?:json|text|markdown|md)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val matches = fencedRegex.findAll(trimmed).mapNotNull { m ->
            m.groupValues[1].trim().takeIf { it.isNotBlank() }
        }.toList()
        if (matches.isNotEmpty()) {
            return matches.firstOrNull { block ->
                block.contains("\"word\"", ignoreCase = true) ||
                    block.contains("\"meaning\"", ignoreCase = true) ||
                    block.contains("WORD:", ignoreCase = true)
            } ?: matches.maxByOrNull { it.length }.orEmpty()
        }
        // Also strip an unclosed leading fence (e.g., truncated ChatGPT output starting with ```json)
        val unclosedStart = Regex("^```(?:json|text|markdown|md)?\\s*", RegexOption.IGNORE_CASE)
        if (unclosedStart.containsMatchIn(trimmed)) {
            return trimmed
                .replaceFirst(unclosedStart, "")
                .removeSuffix("```")
                .trim()
        }
        return trimmed
    }

    private fun looksLikeVocabularyJson(cleaned: String): Boolean {
        val t = cleaned.trimStart()
        if (t.startsWith("[") || t.startsWith("{")) return true
        val hasJsonKeys = Regex("\"(?:word|term|headword|meaning|definition|examples|translation|synonyms)\"\\s*:")
            .containsMatchIn(cleaned)
        return hasJsonKeys || (cleaned.contains("{") && cleaned.contains("\":"))
    }

    private fun tryParseJsonVocabulary(
        cleaned: String,
        defaultLevelCode: String,
        defaultCustomLevelLabel: String
    ): VocabularyParseResult? {
        val looksJson = looksLikeVocabularyJson(cleaned)
        var lastJsonError: String? = null

        val arrayCandidate = extractJsonArrayString(cleaned)
        if (arrayCandidate != null) {
            val candidates = buildJsonSanitizedCandidates(arrayCandidate)
            for (candidate in candidates) {
                val parsed = runCatching {
                    val arr = JSONArray(candidate)
                    val entries = mutableListOf<VocabularyEntry>()
                    val warnings = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val entry = parseJsonEntryObject(obj, defaultLevelCode, defaultCustomLevelLabel)
                        if (entry != null) {
                            entries.add(entry)
                        } else {
                            warnings.add("Skipped JSON item #${i + 1} because word or meaning was missing.")
                        }
                    }
                    VocabularyParseResult(
                        entries = entries,
                        detectedFormat = VocabularyInputFormat.JSON,
                        warnings = warnings
                    )
                }.onFailure { err ->
                    if (lastJsonError == null) lastJsonError = err.message
                }.getOrNull()

                if (parsed != null && parsed.entries.isNotEmpty()) {
                    return parsed
                }
            }
        }

        // Salvage pass: recover individual complete {...} vocabulary objects even when outer [ ]
        // is missing, trailing text contains brackets, the tail is truncated, or 1 entry is malformed.
        if (looksJson) {
            for (candidateText in buildJsonSanitizedCandidates(cleaned)) {
                val salvaged = salvageJsonObjects(candidateText, defaultLevelCode, defaultCustomLevelLabel)
                if (salvaged.entries.isNotEmpty()) {
                    return salvaged
                }
            }
            val reason = lastJsonError?.let { " (${it.take(120)})" }.orEmpty()
            return VocabularyParseResult(
                entries = emptyList(),
                detectedFormat = VocabularyInputFormat.JSON,
                warnings = listOf(
                    "Detected JSON format, but could not parse valid vocabulary entries$reason. " +
                        "Ensure at least one complete {\"word\": ..., \"meaning\": ...} object is present."
                )
            )
        }

        return null
    }

    private fun buildJsonSanitizedCandidates(raw: String): List<String> {
        val out = LinkedHashSet<String>()
        out.add(raw)
        val noTrailingComma = stripTrailingCommasOutsideStrings(raw)
        out.add(noTrailingComma)
        val smartQuotesReplaced = replaceCurlyQuotes(raw)
        if (smartQuotesReplaced != raw) {
            out.add(smartQuotesReplaced)
            out.add(stripTrailingCommasOutsideStrings(smartQuotesReplaced))
        }
        return out.toList()
    }

    private fun replaceCurlyQuotes(raw: String): String {
        return raw
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace('\u201E', '"')
            .replace('\u201F', '"')
    }

    private fun stripTrailingCommasOutsideStrings(raw: String): String {
        val sb = StringBuilder(raw.length)
        var inString = false
        var escaped = false
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (inString) {
                sb.append(ch)
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
            } else {
                if (ch == '"') {
                    inString = true
                    sb.append(ch)
                } else if (ch == ',') {
                    var j = i + 1
                    while (j < raw.length && raw[j].isWhitespace()) j++
                    if (j < raw.length && (raw[j] == '}' || raw[j] == ']')) {
                        // Drop trailing comma before closing } or ]
                    } else {
                        sb.append(ch)
                    }
                } else {
                    sb.append(ch)
                }
            }
            i++
        }
        return sb.toString()
    }

    private fun salvageJsonObjects(
        text: String,
        defaultLevelCode: String,
        defaultCustomLevelLabel: String
    ): VocabularyParseResult {
        var scanStart = 0
        val trimmedStart = text.trimStart()
        if (trimmedStart.startsWith("{")) {
            val wrapperMatch = Regex(
                "\"(?:vocabulary|words|entries|items|cards|list|data)\"\\s*:\\s*\\[",
                RegexOption.IGNORE_CASE
            ).find(text)
            if (wrapperMatch != null) {
                scanStart = wrapperMatch.range.last + 1
            }
        }

        val entries = mutableListOf<VocabularyEntry>()
        var skippedMalformed = 0
        var inString = false
        var escaped = false
        var braceDepth = 0
        var objStart = -1

        for (i in scanStart until text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (braceDepth == 0) objStart = i
                    braceDepth++
                }
                '}' -> {
                    if (braceDepth > 0) {
                        braceDepth--
                        if (braceDepth == 0 && objStart >= 0) {
                            val objSlice = text.substring(objStart, i + 1)
                            val parsedObj = runCatching { JSONObject(objSlice) }
                                .recoverCatching { JSONObject(stripTrailingCommasOutsideStrings(objSlice)) }
                                .getOrNull()
                            if (parsedObj != null) {
                                val entry = parseJsonEntryObject(parsedObj, defaultLevelCode, defaultCustomLevelLabel)
                                if (entry != null) {
                                    entries.add(entry)
                                }
                            } else if (objSlice.contains("\"word\"", ignoreCase = true) ||
                                objSlice.contains("\"term\"", ignoreCase = true)
                            ) {
                                skippedMalformed++
                            }
                            objStart = -1
                        }
                    }
                }
            }
        }

        val hasUnclosedTail = braceDepth > 0 && objStart >= 0 &&
            (text.substring(objStart).contains("\"word\"", ignoreCase = true) ||
                text.substring(objStart).contains("\"term\"", ignoreCase = true))

        val warnings = buildList {
            if (skippedMalformed > 0 || hasUnclosedTail) {
                val tailNote = if (hasUnclosedTail) " (1 incomplete trailing entry was skipped)" else ""
                val errNote = if (skippedMalformed > 0) " ($skippedMalformed malformed object(s) skipped)" else ""
                add("Recovered ${entries.size} entries$tailNote$errNote.")
            }
        }

        return VocabularyParseResult(
            entries = entries,
            detectedFormat = VocabularyInputFormat.JSON,
            warnings = warnings
        )
    }

    private fun extractBalancedJsonArray(cleaned: String, startOffset: Int = 0): String? {
        val openIdx = cleaned.indexOf('[', startOffset)
        if (openIdx < 0) return null
        var inString = false
        var escaped = false
        var depth = 0
        for (i in openIdx until cleaned.length) {
            val ch = cleaned[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        return cleaned.substring(openIdx, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun extractJsonArrayString(cleaned: String): String? {
        val trimmed = cleaned.trimStart()
        if (trimmed.startsWith("[")) {
            extractBalancedJsonArray(cleaned)?.let { return it }
        }
        if (trimmed.startsWith("{")) {
            val obj = runCatching { JSONObject(cleaned) }
                .recoverCatching { JSONObject(stripTrailingCommasOutsideStrings(cleaned)) }
                .getOrNull()
            if (obj != null) {
                val keys = listOf("vocabulary", "words", "entries", "items", "cards", "list", "data")
                keys.forEach { k ->
                    val arr = obj.optJSONArray(k)
                    if (arr != null) return arr.toString()
                }
                // Single word JSON object wrapped as a 1-item array
                if (obj.has("word") || obj.has("term")) {
                    return JSONArray().put(obj).toString()
                }
            }
            // Do not accidentally extract an inner "examples": [...] array if outer text is a list of objects
            return null
        }
        extractBalancedJsonArray(cleaned)?.let { return it }
        val firstBracket = cleaned.indexOf('[')
        val lastBracket = cleaned.lastIndexOf(']')
        if (firstBracket >= 0 && lastBracket > firstBracket) {
            return cleaned.substring(firstBracket, lastBracket + 1)
        }
        return null
    }

    private fun parseJsonEntryObject(
        obj: JSONObject,
        defaultLevelCode: String,
        defaultCustomLevelLabel: String
    ): VocabularyEntry? {
        val word = firstNonBlank(obj, "word", "term", "headword", "expression", "phrase", "vocab")
        if (word.isBlank()) return null

        val meaning = firstNonBlank(obj, "meaning", "definition", "gloss", "explanation", "sense")
        val translation = firstNonBlank(obj, "translation", "translated", "nativeMeaning", "bridgeMeaning", "hindi", "glossTranslation")
        if (meaning.isBlank() && translation.isBlank()) return null

        val pronunciation = firstNonBlank(obj, "pronunciation", "ipa", "phonetic", "romanization", "reading", "pinyin", "furigana")
        val pos = firstNonBlank(obj, "partOfSpeech", "pos", "category", "grammarType", "wordClass")
        val rawLevel = firstNonBlank(obj, "level", "cefr", "levelCode", "proficiency", "difficulty")
        val fallbackLvl = VocabularyLevel.fromCode(defaultLevelCode).takeUnless { it == VocabularyLevel.MIXED }
            ?: VocabularyLevel.INTERMEDIATE
        val parsedLvl = if (rawLevel.isNotBlank()) VocabularyLevel.fromCode(rawLevel) else fallbackLvl
        val resolvedLvl = when {
            parsedLvl == VocabularyLevel.MIXED -> fallbackLvl
            parsedLvl == VocabularyLevel.CUSTOM && (rawLevel.isBlank() || rawLevel.equals("custom", ignoreCase = true)) && defaultCustomLevelLabel.isBlank() -> fallbackLvl
            else -> parsedLvl
        }
        val customLvl = if (resolvedLvl == VocabularyLevel.CUSTOM) {
            rawLevel.ifBlank { defaultCustomLevelLabel }
        } else {
            ""
        }
        val explicitLang = firstNonBlank(obj, "language", "lang", "sourceLanguage", "targetLanguage")
        val explicitTransLang = firstNonBlank(obj, "translationLanguage", "bridgeLanguage", "meaningLanguage", "nativeLanguage")
        val explicitVariant = firstNonBlank(obj, "languageVariant", "variant", "origin", "dialect", "englishVariant", "standard")

        val examples = parseJsonExamples(obj)
        val synonyms = parseFlexibleStringList(obj, "synonyms", "similar")
        val antonyms = parseFlexibleStringList(obj, "antonyms", "opposites")
        val collocations = parseFlexibleStringList(obj, "collocations", "phrases", "commonPhrases", "expressions")
        val usageNote = firstNonBlank(obj, "usageNote", "note", "nuance", "tip", "usage", "memoryHook")

        return VocabularyEntry(
            word = word,
            pronunciation = pronunciation,
            partOfSpeech = pos,
            levelCode = resolvedLvl.code,
            customLevelLabel = customLvl,
            language = explicitLang,
            languageVariant = explicitVariant,
            meaning = meaning.ifBlank { translation },
            translation = translation,
            translationLanguage = explicitTransLang,
            examples = examples,
            synonyms = synonyms,
            antonyms = antonyms,
            collocations = collocations,
            usageNote = usageNote
        )
    }

    private fun parseJsonExamples(obj: JSONObject): List<VocabularyExample> {
        val arr = obj.optJSONArray("examples")
            ?: obj.optJSONArray("contextExamples")
            ?: obj.optJSONArray("sentences")
        if (arr != null) {
            return buildList {
                for (i in 0 until arr.length()) {
                    when (val elem = arr.opt(i)) {
                        is JSONObject -> VocabularyExample.fromJson(elem)?.let { add(it) }
                        is String -> parseLabeledExampleLine(elem)?.let { add(it) }
                    }
                }
            }
        }
        val singleExample = firstNonBlank(obj, "example", "exampleSentence", "sentence")
        return if (singleExample.isNotBlank()) listOfNotNull(parseLabeledExampleLine(singleExample)) else emptyList()
    }

    private fun parseFlexibleStringList(obj: JSONObject, vararg keys: String): List<String> {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val arr = obj.optJSONArray(key)
            if (arr != null) {
                val list = buildList {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i, "").trim()
                        if (s.isNotBlank()) add(s)
                    }
                }
                if (list.isNotEmpty()) return list
            } else {
                val str = obj.optString(key, "").trim()
                if (str.isNotBlank() && !str.equals("none", ignoreCase = true) && str != "-") {
                    return str.split(",", ";", "•", "|")
                        .map { it.trim() }
                        .filter { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
                }
            }
        }
        return emptyList()
    }

    private fun firstNonBlank(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val v = obj.optString(key, "").trim()
                if (v.isNotBlank() && !v.equals("null", ignoreCase = true)) return v
            }
        }
        return ""
    }

    private fun parseStructuredTextVocabulary(
        cleaned: String,
        defaultLevelCode: String,
        defaultCustomLevelLabel: String
    ): VocabularyParseResult {
        val lines = cleaned.lines()
        val entries = mutableListOf<VocabularyEntry>()

        var currentWord = ""
        var pronunciation = ""
        var pos = ""
        var levelRaw = ""
        var explicitLang = ""
        var explicitTransLang = ""
        var explicitVariant = ""
        var docHeaderLang = ""
        var docHeaderTransLang = ""
        var docHeaderVariant = ""
        var meaning = ""
        var translation = ""
        val examples = mutableListOf<VocabularyExample>()
        val synonyms = mutableListOf<String>()
        val antonyms = mutableListOf<String>()
        val collocations = mutableListOf<String>()
        var usageNote = ""
        var inExamplesSection = false

        fun flushCurrent() {
            if (currentWord.isNotBlank() && (meaning.isNotBlank() || translation.isNotBlank())) {
                val fallbackLvl = VocabularyLevel.fromCode(defaultLevelCode).takeUnless { it == VocabularyLevel.MIXED }
                    ?: VocabularyLevel.INTERMEDIATE
                val parsedLvl = if (levelRaw.isNotBlank()) VocabularyLevel.fromCode(levelRaw) else fallbackLvl
                val lvl = when {
                    parsedLvl == VocabularyLevel.MIXED -> fallbackLvl
                    parsedLvl == VocabularyLevel.CUSTOM && (levelRaw.isBlank() || levelRaw.equals("custom", ignoreCase = true)) && defaultCustomLevelLabel.isBlank() -> fallbackLvl
                    else -> parsedLvl
                }
                entries.add(
                    VocabularyEntry(
                        word = currentWord.trim(),
                        pronunciation = pronunciation.trim(),
                        partOfSpeech = pos.trim(),
                        levelCode = lvl.code,
                        customLevelLabel = if (lvl == VocabularyLevel.CUSTOM) levelRaw.ifBlank { defaultCustomLevelLabel } else "",
                        language = explicitLang.ifBlank { docHeaderLang }.trim(),
                        languageVariant = explicitVariant.ifBlank { docHeaderVariant }.trim(),
                        meaning = meaning.trim().ifBlank { translation.trim() },
                        translation = translation.trim(),
                        translationLanguage = explicitTransLang.ifBlank { docHeaderTransLang }.trim(),
                        examples = examples.toList(),
                        synonyms = synonyms.distinct(),
                        antonyms = antonyms.distinct(),
                        collocations = collocations.distinct(),
                        usageNote = usageNote.trim()
                    )
                )
            }
            currentWord = ""
            pronunciation = ""
            pos = ""
            levelRaw = ""
            explicitLang = ""
            explicitTransLang = ""
            explicitVariant = ""
            meaning = ""
            translation = ""
            examples.clear()
            synonyms.clear()
            antonyms.clear()
            collocations.clear()
            usageNote = ""
            inExamplesSection = false
        }

        val wordHeaderRegex = Regex("^(?:\\d+[.)]\\s*)?(?:\\*\\*)?(?:WORD|TERM|HEADWORD)\\s*[:\\-]\\s*(.+)$", RegexOption.IGNORE_CASE)
        val numberedBoldRegex = Regex("^\\d+[.)]\\s+\\*\\*([^*]+)\\*\\*(?:\\s*\\(([^)]+)\\))?(?:\\s*[:\\-–—]\\s*(.*))?$")
        val labeledFieldRegex = Regex("^(?:[-•*]\\s*)?(?:\\*\\*)?([A-Za-z /]+?)(?:\\*\\*)?\\s*:\\s*(.*)$")

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank() || line == "---" || line == "***") {
                if (line == "---" || line == "***") flushCurrent()
                continue
            }

            val wordMatch = wordHeaderRegex.matchEntire(line)
            if (wordMatch != null) {
                flushCurrent()
                val rawHeader = wordMatch.groupValues[1].replace("**", "").trim()
                val inlineMeta = extractInlineWordMeta(rawHeader)
                currentWord = inlineMeta.first
                if (inlineMeta.second.isNotBlank()) pronunciation = inlineMeta.second
                if (inlineMeta.third.isNotBlank()) pos = inlineMeta.third
                continue
            }

            val numberedBoldMatch = numberedBoldRegex.matchEntire(line)
            if (numberedBoldMatch != null) {
                flushCurrent()
                currentWord = numberedBoldMatch.groupValues[1].trim()
                val paren = numberedBoldMatch.groupValues[2].trim()
                if (paren.isNotBlank()) {
                    if (paren.contains("/") || paren.startsWith("[")) pronunciation = paren else pos = paren
                }
                val trailingMeaning = numberedBoldMatch.groupValues[3].trim()
                if (trailingMeaning.isNotBlank()) meaning = trailingMeaning
                continue
            }

            if (currentWord.isBlank()) {
                val topField = labeledFieldRegex.matchEntire(line)
                if (topField != null) {
                    val key = topField.groupValues[1].trim().lowercase(Locale.ROOT)
                    val value = topField.groupValues[2].replace("**", "").trim()
                    when (key) {
                        "language", "lang", "target language", "source language" -> if (value.isNotBlank()) { docHeaderLang = value; continue }
                        "translation language", "bridge language", "meaning language" -> if (value.isNotBlank()) { docHeaderTransLang = value; continue }
                        "variant", "origin", "standard", "english variant" -> if (value.isNotBlank()) { docHeaderVariant = value; continue }
                    }
                }
                // Handle simple one-line "Word — Meaning" fallback when no WORD: header exists
                val simpleDash = Regex("^(?:\\d+[.)]\\s*|[-•*]\\s*)?([^:—–\\-]{2,60})\\s*(?:—|–|->|::)\\s*(.{4,})$").matchEntire(line)
                if (simpleDash != null) {
                    val left = simpleDash.groupValues[1].replace("**", "").trim()
                    val right = simpleDash.groupValues[2].trim()
                    if (!left.contains(" ")) {
                        currentWord = left
                        meaning = right
                        flushCurrent()
                    }
                }
                continue
            }

            val fieldMatch = labeledFieldRegex.matchEntire(line)
            if (fieldMatch != null) {
                val key = fieldMatch.groupValues[1].trim().lowercase(Locale.ROOT)
                val value = fieldMatch.groupValues[2].replace("**", "").trim()
                when {
                    key in setOf("language", "lang", "target language", "source language") -> {
                        explicitLang = value
                        inExamplesSection = false
                    }
                    key in setOf("translation language", "bridge language", "meaning language") -> {
                        explicitTransLang = value
                        inExamplesSection = false
                    }
                    key in setOf("variant", "origin", "standard", "dialect", "english variant") -> {
                        explicitVariant = value
                        inExamplesSection = false
                    }
                    key in setOf("pronunciation", "ipa", "phonetic", "romanization", "reading") -> {
                        pronunciation = value
                        inExamplesSection = false
                    }
                    key in setOf("pos", "part of speech", "category", "type", "word class") -> {
                        pos = value
                        inExamplesSection = false
                    }
                    key in setOf("level", "cefr", "difficulty", "proficiency") -> {
                        levelRaw = value
                        inExamplesSection = false
                    }
                    key in setOf("meaning", "definition", "gloss") -> {
                        meaning = value
                        inExamplesSection = false
                    }
                    key in setOf("translation", "translated", "hindi", "bridge") -> {
                        translation = value
                        inExamplesSection = false
                    }
                    key in setOf("examples", "example", "context examples", "sentences") -> {
                        inExamplesSection = true
                        if (value.isNotBlank()) {
                            parseLabeledExampleLine(value)?.let { examples.add(it) }
                        }
                    }
                    key in setOf("synonyms", "similar") -> {
                        synonyms.addAll(splitTagList(value))
                        inExamplesSection = false
                    }
                    key in setOf("antonyms", "opposites") -> {
                        antonyms.addAll(splitTagList(value))
                        inExamplesSection = false
                    }
                    key in setOf("collocations", "phrases", "common phrases") -> {
                        collocations.addAll(splitTagList(value))
                        inExamplesSection = false
                    }
                    key in setOf("note", "usage", "usage note", "nuance", "tip") -> {
                        usageNote = value
                        inExamplesSection = false
                    }
                    inExamplesSection -> {
                        parseLabeledExampleLine(line)?.let { examples.add(it) }
                    }
                }
            } else if (inExamplesSection) {
                parseLabeledExampleLine(line)?.let { examples.add(it) }
            }
        }
        flushCurrent()

        return VocabularyParseResult(
            entries = entries,
            detectedFormat = VocabularyInputFormat.STRUCTURED_TEXT
        )
    }

    private fun extractInlineWordMeta(raw: String): Triple<String, String, String> {
        // Supports formats like "Ubiquitous (/juːˈbɪk.wɪ.təs/) [adj]" or "Ubiquitous | adj"
        var working = raw.trim()
        var pos = ""
        var pron = ""

        val bracketPos = Regex("\\[([^\\]]+)]").find(working)
        if (bracketPos != null) {
            pos = bracketPos.groupValues[1].trim()
            working = working.removeRange(bracketPos.range).trim()
        }
        val slashPron = Regex("\\(?(/[^/]+/)\\)?").find(working)
        if (slashPron != null) {
            pron = slashPron.groupValues[1].trim()
            working = working.removeRange(slashPron.range).trim()
        }
        if (working.contains("|")) {
            val parts = working.split("|").map { it.trim() }
            working = parts[0]
            if (parts.size > 1 && pos.isBlank()) pos = parts[1]
            if (parts.size > 2 && pron.isBlank()) pron = parts[2]
        }
        return Triple(working.trim(), pron, pos)
    }

    private fun parseLabeledExampleLine(raw: String): VocabularyExample? {
        val clean = raw.trim()
            .removePrefix("-")
            .removePrefix("•")
            .removePrefix("*")
            .replace(Regex("^\\d+[.)]\\s*"), "")
            .trim()
        if (clean.isBlank()) return null

        // Pattern: [Everyday] Sentence... (Translation: ...) or Everyday: Sentence...
        val bracketMatch = Regex("^\\[([^\\]]+)]\\s*[:\\-]?\\s*(.+)$").matchEntire(clean)
        val (context, remainder) = if (bracketMatch != null) {
            bracketMatch.groupValues[1].trim() to bracketMatch.groupValues[2].trim()
        } else {
            val colonMatch = Regex("^([A-Za-z &/]{3,24}):\\s+(.+)$").matchEntire(clean)
            if (colonMatch != null) {
                colonMatch.groupValues[1].trim() to colonMatch.groupValues[2].trim()
            } else {
                "Context" to clean
            }
        }

        // Optional inline translation separated by " | " or " => "
        val splitIdx = remainder.indexOf(" | ").takeIf { it > 0 } ?: remainder.indexOf(" => ")
        return if (splitIdx > 0) {
            val sent = remainder.substring(0, splitIdx).trim()
            val trans = remainder.substring(splitIdx + if (remainder.contains(" => ")) 4 else 3).trim()
            if (sent.isBlank()) null else VocabularyExample(context = context, sentence = sent, translation = trans)
        } else {
            VocabularyExample(context = context, sentence = remainder)
        }
    }

    private fun splitTagList(value: String): List<String> {
        return value.split(",", ";", "•", "|")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("none", ignoreCase = true) && it != "-" }
    }

    /**
     * Analyzes incoming parsed entries against:
     * 1) Existing entries in the target deck (if appending to an existing deck)
     * 2) Existing entries across all other decks of the same language (when checkAcrossSameLanguage = true)
     * 3) Intra-batch duplicates within the same paste
     */
    fun analyzeVocabularyImport(
        parseResult: VocabularyParseResult,
        targetDeck: VocabularyDeck?,
        targetLanguage: String,
        allDecks: List<VocabularyDeck>,
        checkAcrossSameLanguage: Boolean = true
    ): VocabularyImportAnalysis {
        val normalizedTargetLang = targetLanguage.trim().lowercase(Locale.ROOT).ifBlank { "english" }

        // Build lookup map of existing entries keyed by normalized word
        data class ExistingRef(
            val entry: VocabularyEntry,
            val deckId: String,
            val deckTitle: String,
            val levelTitle: String,
            val isSameDeck: Boolean
        )

        val existingIndex = LinkedHashMap<String, ExistingRef>()

        // Target deck takes precedence for matching
        if (targetDeck != null) {
            targetDeck.entries.forEach { entry ->
                val key = entry.normalizedWordKey()
                if (key.isNotBlank() && !existingIndex.containsKey(key)) {
                    existingIndex[key] = ExistingRef(
                        entry = entry,
                        deckId = targetDeck.id,
                        deckTitle = targetDeck.title,
                        levelTitle = targetDeck.effectiveLevelTitle(),
                        isSameDeck = true
                    )
                }
            }
        }

        // Cross-deck scan within the same language
        if (checkAcrossSameLanguage) {
            allDecks
                .filter { it.id != targetDeck?.id && it.language.trim().lowercase(Locale.ROOT) == normalizedTargetLang }
                .forEach { deck ->
                    deck.entries.forEach { entry ->
                        val key = entry.normalizedWordKey()
                        if (key.isNotBlank() && !existingIndex.containsKey(key)) {
                            existingIndex[key] = ExistingRef(
                                entry = entry,
                                deckId = deck.id,
                                deckTitle = deck.title,
                                levelTitle = deck.effectiveLevelTitle(),
                                isSameDeck = false
                            )
                        }
                    }
                }
        }

        val uniqueNew = mutableListOf<VocabularyEntry>()
        val batchSeen = LinkedHashMap<String, VocabularyEntry>()
        val duplicates = mutableListOf<VocabularyDuplicateItem>()

        parseResult.entries.forEachIndexed { idx, incoming ->
            val key = incoming.normalizedWordKey()
            if (key.isBlank()) return@forEachIndexed

            val existingMatch = existingIndex[key]
            val intraBatchMatch = batchSeen[key]

            when {
                existingMatch != null -> {
                    duplicates.add(
                        VocabularyDuplicateItem(
                            id = "dup_${idx}_${incoming.id}",
                            incoming = incoming,
                            existing = existingMatch.entry,
                            existingDeckId = existingMatch.deckId,
                            existingDeckTitle = existingMatch.deckTitle,
                            existingLevelTitle = existingMatch.levelTitle,
                            isSameDeck = existingMatch.isSameDeck,
                            isIntraBatch = false
                        )
                    )
                }
                intraBatchMatch != null -> {
                    duplicates.add(
                        VocabularyDuplicateItem(
                            id = "batch_${idx}_${incoming.id}",
                            incoming = incoming,
                            existing = intraBatchMatch,
                            existingDeckId = targetDeck?.id ?: "__NEW_DECK__",
                            existingDeckTitle = targetDeck?.title ?: "Current Import Batch",
                            existingLevelTitle = intraBatchMatch.effectiveLevelTitle(),
                            isSameDeck = true,
                            isIntraBatch = true
                        )
                    )
                }
                else -> {
                    batchSeen[key] = incoming
                    uniqueNew.add(incoming)
                }
            }
        }

        return VocabularyImportAnalysis(
            uniqueNewEntries = uniqueNew,
            duplicateItems = duplicates,
            totalParsedCount = parseResult.entries.size,
            detectedFormat = parseResult.detectedFormat,
            warnings = parseResult.warnings
        )
    }

    private val LATIN_SCRIPT_LANGUAGES = setOf(
        "english", "french", "spanish", "german", "portuguese", "italian",
        "dutch", "polish", "turkish", "indonesian", "malay", "vietnamese",
        "swedish", "norwegian", "danish", "finnish", "romanian", "czech",
        "hungarian", "swahili", "filipino"
    )

    private val ENGLISH_GLOSS_CUES = setOf(
        "the", "a", "an", "to", "of", "in", "for", "from", "with", "on", "by",
        "that", "who", "which", "or", "and", "is", "are", "was", "were", "be",
        "being", "having", "not", "without", "someone", "something", "state",
        "act", "quality", "process", "person", "place", "thing", "very", "more"
    )

    /**
     * Auto-detects per-entry language, regional origin (UK vs US English), and bilingual
     * translation/meaning language from explicit tags + word/sentence/meaning script analysis.
     */
    private fun enrichParsedResultWithDetection(
        rawResult: VocabularyParseResult,
        cleanedSource: String,
        preferredEnglishVariant: String
    ): VocabularyParseResult {
        if (rawResult.entries.isEmpty()) return rawResult

        // Check optional top-level JSON wrapper metadata
        var topLevelRawLang = ""
        var topLevelVariant = ""
        var topLevelRawTransLang = ""
        val trimmedSource = cleanedSource.trimStart()
        if (trimmedSource.startsWith("{")) {
            val wrapperObj = runCatching { JSONObject(cleanedSource) }
                .recoverCatching { JSONObject(stripTrailingCommasOutsideStrings(cleanedSource)) }
                .getOrNull()
            if (wrapperObj != null && !wrapperObj.has("word")) {
                topLevelRawLang = firstNonBlank(wrapperObj, "language", "lang", "targetLanguage", "sourceLanguage")
                topLevelVariant = firstNonBlank(wrapperObj, "languageVariant", "variant", "origin", "standard", "englishVariant")
                topLevelRawTransLang = firstNonBlank(wrapperObj, "translationLanguage", "bridgeLanguage", "meaningLanguage", "nativeLanguage")
            }
        }

        val (topLevelLang, topLevelTagTrans) = parseBilingualLanguageTag(topLevelRawLang)
        val topLevelTransLang = canonicalizeSingleLanguage(topLevelRawTransLang).ifBlank { topLevelTagTrans }

        val mismatchCounts = LinkedHashMap<Pair<String, String>, Int>()

        val enrichedEntries = rawResult.entries.map { entry ->
            val (resolvedLang, mismatchPair) = resolveEntryLanguage(entry, topLevelLang)
            if (mismatchPair != null) {
                mismatchCounts[mismatchPair] = (mismatchCounts[mismatchPair] ?: 0) + 1
            }
            val resolvedVariant = resolveEntryVariant(entry, resolvedLang, topLevelVariant, preferredEnglishVariant)
            val resolvedTransLang = resolveEntryTranslationLanguage(
                entry = entry,
                sourceLanguage = resolvedLang,
                topLevelLangTag = topLevelLang,
                topLevelTransLang = topLevelTransLang
            )
            entry.copy(
                language = resolvedLang,
                languageVariant = resolvedVariant,
                translationLanguage = resolvedTransLang
            )
        }

        val detectedLanguages = enrichedEntries
            .map { it.language.ifBlank { "English" } }
            .distinctBy { it.lowercase(Locale.ROOT) }

        val dominantVariant = enrichedEntries
            .firstOrNull { VocabularyLanguageVariants.isEnglish(it.language) && it.languageVariant.isNotBlank() }
            ?.languageVariant
            ?: topLevelVariant.ifBlank {
                if (detectedLanguages.any { VocabularyLanguageVariants.isEnglish(it) }) {
                    VocabularyLanguageVariants.normalizeVariant("English", preferredEnglishVariant)
                } else ""
            }

        val detectedTranslation = enrichedEntries
            .map { it.translationLanguage }
            .firstOrNull { it.isNotBlank() }
            ?: topLevelTransLang.takeIf { it.isNotBlank() && detectedLanguages.none { dl -> dl.equals(it, ignoreCase = true) } }
            ?: detectTranslationLanguage(enrichedEntries, detectedLanguages.firstOrNull().orEmpty())

        val mismatchWarnings = mismatchCounts.map { (pair, count) ->
            "Auto-corrected language from ${pair.first} to ${pair.second} for $count word(s) based on pasted word script."
        }

        return rawResult.copy(
            entries = enrichedEntries,
            warnings = (rawResult.warnings + mismatchWarnings).distinct(),
            detectedLanguages = detectedLanguages,
            detectedVariant = dominantVariant,
            detectedTranslationLanguage = detectedTranslation
        )
    }

    private fun resolveEntryLanguage(
        entry: VocabularyEntry,
        topLevelLang: String
    ): Pair<String, Pair<String, String>?> {
        val rawExplicit = entry.language.ifBlank { topLevelLang }.trim()
        val (explicitSource, _) = parseBilingualLanguageTag(rawExplicit)

        val nonLatinScriptLang = inferNonLatinWordLanguage(entry)
        val sentenceLatinLang = inferLatinSentenceLanguage(entry)

        if (explicitSource.isNotBlank()) {
            val isExplicitLatin = explicitSource.lowercase(Locale.ROOT) in LATIN_SCRIPT_LANGUAGES
            // If an explicit tag claims a Latin language (e.g. "English") while the actual word/examples
            // are written in a non-Latin script (e.g. Hindi, Japanese, Arabic), trust the pasted word script.
            if (isExplicitLatin && nonLatinScriptLang != null && !nonLatinScriptLang.equals(explicitSource, ignoreCase = true)) {
                return nonLatinScriptLang to (explicitSource to nonLatinScriptLang)
            }
            return explicitSource to null
        }

        if (nonLatinScriptLang != null) {
            return nonLatinScriptLang to null
        }
        if (sentenceLatinLang != null) {
            return sentenceLatinLang to null
        }
        return "English" to null
    }

    private fun inferNonLatinWordLanguage(entry: VocabularyEntry): String? {
        val targetSample = buildString {
            append(entry.word).append(' ')
            entry.examples.forEach { append(it.sentence).append(' ') }
        }.trim()
        val hasNonAsciiLetters = targetSample.any { ch -> ch.code > 127 && Character.isLetter(ch) }
        if (!hasNonAsciiLetters) return null
        val detected = PaperLanguageDetector.detectLanguages(targetSample)
        return detected.firstOrNull { !it.equals("English", ignoreCase = true) && it.lowercase(Locale.ROOT) !in LATIN_SCRIPT_LANGUAGES }
            ?: detected.firstOrNull { !it.equals("English", ignoreCase = true) }
    }

    private fun inferLatinSentenceLanguage(entry: VocabularyEntry): String? {
        val sentenceOnly = entry.examples.joinToString(" ") { it.sentence }.trim()
        if (sentenceOnly.length < 24) return null
        val detected = PaperLanguageDetector.detectLanguages(sentenceOnly)
        return detected.firstOrNull { !it.equals("English", ignoreCase = true) }
    }

    private fun parseBilingualLanguageTag(raw: String): Pair<String, String> {
        val clean = raw.trim()
        if (clean.isBlank()) return "" to ""
        val bilingualMatch = Regex(
            """^(.+?)\s*(?:->|=>|→|/|(?i:\bto\b))\s*(.+)$"""
        ).matchEntire(clean)
        if (bilingualMatch != null) {
            val left = canonicalizeSingleLanguage(bilingualMatch.groupValues[1])
            val right = canonicalizeSingleLanguage(bilingualMatch.groupValues[2])
            if (left.isNotBlank() && right.isNotBlank() && !left.equals(right, ignoreCase = true)) {
                return left to right
            }
        }
        return canonicalizeSingleLanguage(clean) to ""
    }

    private fun canonicalizeSingleLanguage(raw: String): String {
        val clean = raw.trim()
        if (clean.isBlank()) return ""
        val lower = clean.lowercase(Locale.ROOT)
        if (lower == "none" || lower == "monolingual" || lower == "n/a") return ""
        return when {
            lower == "uk english" || lower == "british english" || lower == "us english" || lower == "american english" -> "English"
            else -> {
                val detected = PaperLanguageDetector.detectLanguages("Language: $clean")
                detected.firstOrNull() ?: clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }
    }

    private fun resolveEntryVariant(
        entry: VocabularyEntry,
        language: String,
        topLevelVariant: String,
        preferredEnglishVariant: String
    ): String {
        val explicit = entry.languageVariant.ifBlank { topLevelVariant }.trim()
        if (explicit.isNotBlank()) {
            return VocabularyLanguageVariants.normalizeVariant(language, explicit)
        }
        if (!VocabularyLanguageVariants.isEnglish(language)) {
            return ""
        }
        // Check subtle UK vs US spelling markers in word/meaning/examples if unambiguous
        val sample = buildString {
            append(entry.word).append(' ')
            append(entry.meaning).append(' ')
            entry.examples.forEach { append(it.sentence).append(' ') }
        }.lowercase(Locale.ROOT)
        val ukHits = UK_SPELLING_CUES.count { it.containsMatchIn(sample) }
        val usHits = US_SPELLING_CUES.count { it.containsMatchIn(sample) }
        return when {
            ukHits > usHits && ukHits >= 1 -> "UK"
            usHits > ukHits && usHits >= 1 -> "US"
            else -> VocabularyLanguageVariants.normalizeVariant("English", preferredEnglishVariant)
        }
    }

    private fun resolveEntryTranslationLanguage(
        entry: VocabularyEntry,
        sourceLanguage: String,
        topLevelLangTag: String,
        topLevelTransLang: String
    ): String {
        val explicitEntryTrans = canonicalizeSingleLanguage(entry.translationLanguage)
        if (explicitEntryTrans.isNotBlank() && !explicitEntryTrans.equals(sourceLanguage, ignoreCase = true)) {
            return explicitEntryTrans
        }
        val (_, entryTagTrans) = parseBilingualLanguageTag(entry.language)
        if (entryTagTrans.isNotBlank() && !entryTagTrans.equals(sourceLanguage, ignoreCase = true)) {
            return entryTagTrans
        }
        val (_, topTagTrans) = parseBilingualLanguageTag(topLevelLangTag)
        if (topTagTrans.isNotBlank() && !topTagTrans.equals(sourceLanguage, ignoreCase = true)) {
            return topTagTrans
        }

        // 1. Inspect dedicated translation fields on word + examples
        val transSample = buildString {
            if (entry.translation.isNotBlank()) append(entry.translation).append('\n')
            entry.examples.forEach { ex ->
                if (ex.translation.isNotBlank()) append(ex.translation).append('\n')
            }
        }.trim()
        if (transSample.isNotBlank()) {
            val fromTrans = detectBridgeTextLanguage(transSample, sourceLanguage, strictLatinCheck = false)
            if (fromTrans.isNotBlank() && !fromTrans.equals(sourceLanguage, ignoreCase = true)) {
                return fromTrans
            }
        }

        // 2. Inspect `meaning` when AI placed the other language's meaning directly in `meaning`
        if (entry.meaning.isNotBlank()) {
            val fromMeaning = detectBridgeTextLanguage(entry.meaning, sourceLanguage, strictLatinCheck = true)
            if (fromMeaning.isNotBlank() && !fromMeaning.equals(sourceLanguage, ignoreCase = true)) {
                return fromMeaning
            }
        }

        return topLevelTransLang.takeIf { it.isNotBlank() && !it.equals(sourceLanguage, ignoreCase = true) }.orEmpty()
    }

    private fun detectTranslationLanguage(
        entries: List<VocabularyEntry>,
        sourceLanguage: String = ""
    ): String {
        val explicit = entries.map { it.translationLanguage }.firstOrNull { it.isNotBlank() }
        if (!explicit.isNullOrBlank()) return explicit

        val transSample = buildString {
            entries.forEach { e ->
                if (e.translation.isNotBlank()) append(e.translation).append('\n')
                e.examples.forEach { ex ->
                    if (ex.translation.isNotBlank()) append(ex.translation).append('\n')
                }
            }
        }.trim()
        if (transSample.isNotBlank()) {
            val detected = detectBridgeTextLanguage(transSample, sourceLanguage, strictLatinCheck = false)
            if (detected.isNotBlank() && !detected.equals(sourceLanguage, ignoreCase = true)) return detected
        }

        val meaningSample = entries.mapNotNull { it.meaning.trim().takeIf { m -> m.isNotBlank() } }.joinToString("\n")
        if (meaningSample.isNotBlank() && sourceLanguage.isNotBlank()) {
            val fromMeaning = detectBridgeTextLanguage(meaningSample, sourceLanguage, strictLatinCheck = true)
            if (fromMeaning.isNotBlank() && !fromMeaning.equals(sourceLanguage, ignoreCase = true)) return fromMeaning
        }
        return ""
    }

    private fun detectBridgeTextLanguage(
        sample: String,
        sourceLanguage: String,
        strictLatinCheck: Boolean
    ): String {
        if (sample.isBlank()) return ""
        val hasNonAsciiLetters = sample.any { ch -> ch.code > 127 && Character.isLetter(ch) }
        val nonLatinHit = if (hasNonAsciiLetters) {
            PaperLanguageDetector.detectLanguages(sample)
                .firstOrNull { !it.equals("English", ignoreCase = true) }
        } else null
        if (!nonLatinHit.isNullOrBlank()) {
            return nonLatinHit
        }

        // Latin-script sample: avoid false "English" fallback on short monolingual non-English glosses
        val srcLower = sourceLanguage.trim().lowercase(Locale.ROOT)
        val isSourceNonLatin = srcLower.isNotBlank() && srcLower !in LATIN_SCRIPT_LANGUAGES

        val latinDetected = PaperLanguageDetector.detectLanguages(sample).firstOrNull().orEmpty()
        if (latinDetected.isNotBlank() && !latinDetected.equals("English", ignoreCase = true)) {
            return latinDetected
        }

        val words = Regex("[A-Za-z]+").findAll(sample).map { it.value.lowercase(Locale.ROOT) }.toList()
        if (words.isEmpty()) return ""
        val englishCueHits = words.count { it in ENGLISH_GLOSS_CUES }

        return when {
            !strictLatinCheck -> "English"
            isSourceNonLatin && words.size >= 2 -> "English"
            srcLower.isNotBlank() && srcLower != "english" && englishCueHits >= 2 -> "English"
            else -> ""
        }
    }

    /**
     * Groups parsed vocabulary entries into automatic `(language, level)` destination buckets
     * matched against existing folders/sets in [allDecks], with suggested titles for any new set.
     */
    fun buildSmartRoutingPlan(
        parseResult: VocabularyParseResult,
        allDecks: List<VocabularyDeck>,
        preferredEnglishVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT
    ): List<VocabularyRouteBucket> {
        if (parseResult.entries.isEmpty()) return emptyList()

        data class GroupKey(
            val langNorm: String,
            val levelCode: String,
            val customLabelNorm: String
        )

        val grouped = LinkedHashMap<GroupKey, MutableList<VocabularyEntry>>()
        parseResult.entries.forEach { entry ->
            val lang = entry.language.trim().ifBlank { "English" }
            val rawLvl = VocabularyLevel.fromCode(entry.levelCode)
            val lvl = when {
                rawLvl == VocabularyLevel.MIXED -> VocabularyLevel.INTERMEDIATE
                rawLvl == VocabularyLevel.CUSTOM && entry.customLevelLabel.isBlank() -> VocabularyLevel.INTERMEDIATE
                else -> rawLvl
            }
            val customNorm = if (lvl == VocabularyLevel.CUSTOM) entry.customLevelLabel.trim().lowercase(Locale.ROOT) else ""
            val normalizedEntry = if (lvl.code != entry.levelCode) {
                entry.copy(levelCode = lvl.code, customLevelLabel = if (lvl == VocabularyLevel.CUSTOM) entry.customLevelLabel.trim() else "")
            } else entry
            val key = GroupKey(lang.lowercase(Locale.ROOT), lvl.code, customNorm)
            grouped.getOrPut(key) { mutableListOf() }.add(normalizedEntry)
        }

        val sortedGroups = grouped.entries.sortedWith(
            compareBy<Map.Entry<GroupKey, MutableList<VocabularyEntry>>>(
                { it.key.langNorm },
                { VocabularyLevel.fromCode(it.key.levelCode).sortRank },
                { it.key.customLabelNorm }
            )
        )

        return sortedGroups.map { (key, groupEntries) ->
            val lang = groupEntries.firstOrNull { it.language.isNotBlank() }?.language ?: "English"
            val variant = if (VocabularyLanguageVariants.isEnglish(lang)) {
                groupEntries.firstNotNullOfOrNull { it.languageVariant.takeIf { v -> v.isNotBlank() } }
                    ?: VocabularyLanguageVariants.normalizeVariant("English", preferredEnglishVariant)
            } else {
                groupEntries.firstNotNullOfOrNull { it.languageVariant.takeIf { v -> v.isNotBlank() } }.orEmpty()
            }
            val customLabel = if (key.levelCode == VocabularyLevel.CUSTOM.code) {
                groupEntries.firstNotNullOfOrNull { it.customLevelLabel.takeIf { c -> c.isNotBlank() } }.orEmpty()
            } else ""
            val level = VocabularyLevel.fromCode(key.levelCode)
            val levelTitle = level.displayTitleForLanguage(lang, customLabel)

            // Resolve translation language per bucket first so mixed-language pastes never bleed into sibling buckets
            val transLang = detectTranslationLanguage(groupEntries, lang)
                .takeIf { it.isNotBlank() && !it.equals(lang, ignoreCase = true) }
                ?: parseResult.detectedTranslationLanguage
                    .takeIf {
                        it.isNotBlank() &&
                            !it.equals(lang, ignoreCase = true) &&
                            groupEntries.any { e ->
                                e.translationLanguage.equals(it, ignoreCase = true) ||
                                    e.translation.isNotBlank()
                            }
                    }
                    .orEmpty()

            val matchingDecks = allDecks
                .filter { deck ->
                    deck.language.trim().equals(lang, ignoreCase = true) &&
                        deck.levelCode.equals(level.code, ignoreCase = true) &&
                        (level != VocabularyLevel.CUSTOM || deck.customLevelLabel.trim().equals(customLabel, ignoreCase = true))
                }
                .sortedWith(
                    compareByDescending<VocabularyDeck> { deck ->
                        transLang.isNotBlank() && deck.translationLanguage.equals(transLang, ignoreCase = true)
                    }.thenByDescending { deck ->
                        transLang.isBlank() && deck.translationLanguage.isBlank()
                    }.thenByDescending { deck ->
                        variant.isNotBlank() && deck.languageVariant.equals(variant, ignoreCase = true)
                    }.thenByDescending { deck ->
                        deck.languageVariant.isBlank()
                    }.thenByDescending { it.updatedAt }
                )

            val bucketKey = "${key.langNorm}|${level.code.lowercase(Locale.ROOT)}|${key.customLabelNorm}"
            val variantSuffix = variant.takeIf { it.isNotBlank() && !it.equals("Neutral", ignoreCase = true) }
                ?.let { " (${VocabularyLanguageVariants.shortBadge(it)})" }
                .orEmpty()
            val primaryTitle = if (transLang.isNotBlank()) {
                "$lang$variantSuffix -> $transLang • $levelTitle"
            } else {
                "$lang$variantSuffix • $levelTitle"
            }
            val suggestions = buildList {
                add(primaryTitle)
                if (transLang.isNotBlank()) add("$lang -> $transLang ($levelTitle)")
                add("$lang$variantSuffix • $levelTitle")
                add("$lang • $levelTitle")
                add("$levelTitle Core Lexicon")
            }.distinct()

            VocabularyRouteBucket(
                bucketKey = bucketKey,
                language = lang,
                languageVariant = variant,
                translationLanguage = transLang,
                levelCode = level.code,
                customLevelLabel = customLabel,
                entries = groupEntries.toList(),
                candidateDecks = matchingDecks,
                recommendedDeckId = matchingDecks.firstOrNull()?.id,
                titleSuggestions = suggestions,
                defaultNewTitle = primaryTitle
            )
        }
    }

    /**
     * Runs duplicate & cross-deck detection across all smart-routed buckets together
     * so each unique word routes cleanly while repeats surface in the Duplicate Review Hub.
     */
    fun analyzeSmartRoutedImport(
        parseResult: VocabularyParseResult,
        buckets: List<VocabularyRouteBucket>,
        choices: Map<String, VocabularySmartRouteTargetChoice>,
        allDecks: List<VocabularyDeck>,
        checkAcrossSameLanguage: Boolean = true
    ): VocabularySmartRouteAnalysis {
        val deckById = allDecks.associateBy { it.id }

        data class ExistingRef(
            val entry: VocabularyEntry,
            val deckId: String,
            val deckTitle: String,
            val levelTitle: String,
            val isSameDeck: Boolean
        )

        val uniqueNew = mutableListOf<VocabularyEntry>()
        val duplicates = mutableListOf<VocabularyDuplicateItem>()
        val entryBucketById = mutableMapOf<String, String>()
        // Track intra-batch unique entries per language so identical words in the same paste are caught cleanly
        val batchSeenByLang = LinkedHashMap<String, LinkedHashMap<String, Pair<VocabularyEntry, String>>>()

        var itemIdx = 0
        buckets.forEach { bucket ->
            val choice = choices[bucket.bucketKey]
            val targetDeck = choice?.targetDeckId?.let { deckById[it] }
                ?: bucket.recommendedDeckId?.let { if (choice == null) deckById[it] else null }
            val resolvedTitle = choice?.customTitle?.trim()?.ifBlank { bucket.defaultNewTitle } ?: bucket.defaultNewTitle
            val langKey = (choice?.language ?: bucket.language).trim().lowercase(Locale.ROOT).ifBlank { "english" }

            // Build lookup index for this bucket's target deck + same-language decks
            val existingIndex = LinkedHashMap<String, ExistingRef>()
            if (targetDeck != null) {
                targetDeck.entries.forEach { existing ->
                    val k = existing.normalizedWordKey()
                    if (k.isNotBlank() && !existingIndex.containsKey(k)) {
                        existingIndex[k] = ExistingRef(
                            entry = existing,
                            deckId = targetDeck.id,
                            deckTitle = targetDeck.title,
                            levelTitle = targetDeck.effectiveLevelTitle(),
                            isSameDeck = true
                        )
                    }
                }
            }
            if (checkAcrossSameLanguage) {
                allDecks
                    .filter { it.id != targetDeck?.id && it.language.trim().lowercase(Locale.ROOT) == langKey }
                    .forEach { deck ->
                        deck.entries.forEach { existing ->
                            val k = existing.normalizedWordKey()
                            if (k.isNotBlank() && !existingIndex.containsKey(k)) {
                                existingIndex[k] = ExistingRef(
                                    entry = existing,
                                    deckId = deck.id,
                                    deckTitle = deck.title,
                                    levelTitle = deck.effectiveLevelTitle(),
                                    isSameDeck = false
                                )
                            }
                        }
                    }
            }

            val langBatchSeen = batchSeenByLang.getOrPut(langKey) { LinkedHashMap() }
            bucket.entries.forEach { incoming ->
                val idx = itemIdx++
                entryBucketById[incoming.id] = bucket.bucketKey
                val key = incoming.normalizedWordKey()
                if (key.isBlank()) return@forEach

                val existingMatch = existingIndex[key]
                val intraBatchMatch = langBatchSeen[key]
                when {
                    existingMatch != null -> {
                        duplicates.add(
                            VocabularyDuplicateItem(
                                id = "dup_${idx}_${incoming.id}",
                                incoming = incoming,
                                existing = existingMatch.entry,
                                existingDeckId = existingMatch.deckId,
                                existingDeckTitle = existingMatch.deckTitle,
                                existingLevelTitle = existingMatch.levelTitle,
                                isSameDeck = existingMatch.isSameDeck,
                                isIntraBatch = false
                            )
                        )
                    }
                    intraBatchMatch != null -> {
                        duplicates.add(
                            VocabularyDuplicateItem(
                                id = "batch_${idx}_${incoming.id}",
                                incoming = incoming,
                                existing = intraBatchMatch.first,
                                existingDeckId = targetDeck?.id ?: "__NEW_DECK__",
                                existingDeckTitle = targetDeck?.title ?: resolvedTitle,
                                existingLevelTitle = intraBatchMatch.first.effectiveLevelTitle(),
                                isSameDeck = true,
                                isIntraBatch = true
                            )
                        )
                    }
                    else -> {
                        langBatchSeen[key] = incoming to bucket.bucketKey
                        uniqueNew.add(incoming)
                    }
                }
            }
        }

        return VocabularySmartRouteAnalysis(
            analysis = VocabularyImportAnalysis(
                uniqueNewEntries = uniqueNew,
                duplicateItems = duplicates,
                totalParsedCount = parseResult.entries.size,
                detectedFormat = parseResult.detectedFormat,
                warnings = parseResult.warnings
            ),
            entryBucketById = entryBucketById
        )
    }

    private val UK_SPELLING_CUES = listOf(
        Regex("""\b(?:colour|flavour|honour|favour|behaviour|neighbour|humour|labour|rumour|candour|splendour)\w*\b"""),
        Regex("""\b(?:analyse|paralyse|catalyse|organise|realise|recognise|emphasise|minimise|maximise)\w*\b"""),
        Regex("""\b(?:centre|theatre|metre|litre|fibre|spectre|meagre)\b"""),
        Regex("""\b(?:programme|catalogue|dialogue|judgement|acknowledgement|defence|offence|licence|practise|travelling|cancelled|fulfil)\b""")
    )

    private val US_SPELLING_CUES = listOf(
        Regex("""\b(?:color|flavor|honor|favor|behavior|neighbor|humor|labor|rumor|candor|splendor)\w*\b"""),
        Regex("""\b(?:analyze|paralyze|catalyze|organize|realize|recognize|emphasize|minimize|maximize)\w*\b"""),
        Regex("""\b(?:center|theater|meter|liter|fiber|specter|meager)\b"""),
        Regex("""\b(?:program|catalog|defense|offense|canceled|traveling|judgment|acknowledgment|fulfill)\b""")
    )

    /**
     * Granularly merges only user-selected segments from [incoming] into [existing],
     * preserving existing content and deduplicating sentences/tags automatically.
     */
    fun mergeVocabularyEntry(
        existing: VocabularyEntry,
        incoming: VocabularyEntry,
        decision: VocabularyMergeDecision
    ): VocabularyEntry {
        val now = System.currentTimeMillis()
        if (decision.action == VocabularyDuplicateAction.REPLACE_EXISTING) {
            return incoming.copy(
                id = existing.id,
                isBookmarked = existing.isBookmarked,
                createdAt = existing.createdAt,
                updatedAt = now
            )
        }
        if (decision.action != VocabularyDuplicateAction.MERGE_SEGMENTS) {
            return existing
        }

        val mergedExamples = if (decision.mergeExamples) {
            val seen = existing.examples.map { it.normalizedSentenceKey() }.toMutableSet()
            val combined = existing.examples.toMutableList()
            incoming.examples.forEach { ex ->
                val k = ex.normalizedSentenceKey()
                if (k.isNotBlank() && seen.add(k)) {
                    combined.add(ex)
                }
            }
            combined
        } else {
            existing.examples
        }

        val mergedTranslation = if (decision.mergeTranslation && incoming.translation.isNotBlank()) {
            when {
                existing.translation.isBlank() -> incoming.translation.trim()
                existing.translation.contains(incoming.translation.trim(), ignoreCase = true) -> existing.translation
                else -> "${existing.translation.trim()} / ${incoming.translation.trim()}"
            }
        } else {
            existing.translation
        }

        val mergedSynonyms = if (decision.mergeSynonymsAndCollocations) {
            mergeDistinctCaseInsensitive(existing.synonyms, incoming.synonyms)
        } else existing.synonyms

        val mergedAntonyms = if (decision.mergeSynonymsAndCollocations) {
            mergeDistinctCaseInsensitive(existing.antonyms, incoming.antonyms)
        } else existing.antonyms

        val mergedCollocations = if (decision.mergeSynonymsAndCollocations) {
            mergeDistinctCaseInsensitive(existing.collocations, incoming.collocations)
        } else existing.collocations

        val mergedMeaning = if (decision.mergeMeaningAndNote && incoming.meaning.isNotBlank()) {
            when {
                existing.meaning.isBlank() -> incoming.meaning.trim()
                existing.meaning.equals(incoming.meaning.trim(), ignoreCase = true) -> existing.meaning
                else -> "${existing.meaning.trim()}; ${incoming.meaning.trim()}"
            }
        } else existing.meaning

        val mergedNote = if (decision.mergeMeaningAndNote && incoming.usageNote.isNotBlank()) {
            when {
                existing.usageNote.isBlank() -> incoming.usageNote.trim()
                existing.usageNote.contains(incoming.usageNote.trim(), ignoreCase = true) -> existing.usageNote
                else -> "${existing.usageNote.trim()} | ${incoming.usageNote.trim()}"
            }
        } else existing.usageNote

        val mergedLevelCode = if (decision.mergeLevelAndMeta && incoming.levelCode.isNotBlank()) {
            incoming.levelCode
        } else existing.levelCode

        val mergedCustomLevel = if (decision.mergeLevelAndMeta) {
            incoming.customLevelLabel
        } else existing.customLevelLabel

        val mergedPos = if (decision.mergeLevelAndMeta && incoming.partOfSpeech.isNotBlank()) {
            incoming.partOfSpeech
        } else existing.partOfSpeech.ifBlank { incoming.partOfSpeech }

        val mergedPron = if (decision.mergeLevelAndMeta && incoming.pronunciation.isNotBlank()) {
            incoming.pronunciation
        } else existing.pronunciation.ifBlank { incoming.pronunciation }

        return existing.copy(
            pronunciation = mergedPron,
            partOfSpeech = mergedPos,
            levelCode = mergedLevelCode,
            customLevelLabel = mergedCustomLevel,
            meaning = mergedMeaning,
            translation = mergedTranslation,
            examples = mergedExamples,
            synonyms = mergedSynonyms,
            antonyms = mergedAntonyms,
            collocations = mergedCollocations,
            usageNote = mergedNote,
            updatedAt = now
        )
    }

    private fun mergeDistinctCaseInsensitive(base: List<String>, incoming: List<String>): List<String> {
        val seen = base.map { it.trim().lowercase(Locale.ROOT) }.toMutableSet()
        val out = base.toMutableList()
        incoming.forEach { item ->
            val clean = item.trim()
            if (clean.isNotBlank() && seen.add(clean.lowercase(Locale.ROOT))) {
                out.add(clean)
            }
        }
        return out
    }

    /**
     * Computes SM-2 Spaced Repetition statistics for a VocabularyDeck using its entryId -> FlashcardSrsState map.
     */
    fun computeDeckSrsStats(
        deck: VocabularyDeck,
        srsMap: Map<String, FlashcardSrsState>,
        nowMillis: Long = System.currentTimeMillis()
    ): DeckSrsStats {
        if (deck.entries.isEmpty()) return DeckSrsStats()
        var due = 0
        var fresh = 0
        var learning = 0
        var mastered = 0
        var totalReviews = 0
        var totalCorrect = 0

        deck.entries.forEach { entry ->
            val st = srsMap[entry.id]
            when (SpacedRepetitionEngine.statusOf(st, nowMillis)) {
                FlashcardSrsStatus.DUE -> due++
                FlashcardSrsStatus.NEW -> fresh++
                FlashcardSrsStatus.LEARNING -> learning++
                FlashcardSrsStatus.MASTERED -> mastered++
            }
            if (st != null) {
                totalReviews += st.reviewCount
                totalCorrect += st.correctCount
            }
        }

        val retention = if (totalReviews > 0) {
            ((totalCorrect.toFloat() / totalReviews.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        } else 0

        return DeckSrsStats(
            totalCount = deck.entries.size,
            dueCount = due,
            newCount = fresh,
            learningCount = learning,
            masteredCount = mastered,
            retentionPercent = retention
        )
    }

    /**
     * Builds the SM-2 study queue for a VocabularyDeck (Due first, then New; or all entries in Cram mode).
     */
    fun buildRecallQueue(
        deck: VocabularyDeck,
        srsMap: Map<String, FlashcardSrsState>,
        nowMillis: Long = System.currentTimeMillis(),
        cramAll: Boolean = false,
        shuffle: Boolean = false
    ): List<VocabularyEntry> {
        val sorted = deck.sortedEntriesAscending()
        if (sorted.isEmpty()) return emptyList()

        val dueList = mutableListOf<VocabularyEntry>()
        val newList = mutableListOf<VocabularyEntry>()
        val aheadList = mutableListOf<VocabularyEntry>()

        sorted.forEach { entry ->
            when (SpacedRepetitionEngine.statusOf(srsMap[entry.id], nowMillis)) {
                FlashcardSrsStatus.DUE -> dueList.add(entry)
                FlashcardSrsStatus.NEW -> newList.add(entry)
                FlashcardSrsStatus.LEARNING, FlashcardSrsStatus.MASTERED -> aheadList.add(entry)
            }
        }

        val orderedDue = dueList.sortedBy { srsMap[it.id]?.dueAtMillis ?: 0L }
        val base = if (cramAll) {
            orderedDue + newList + aheadList.sortedBy { srsMap[it.id]?.dueAtMillis ?: Long.MAX_VALUE }
        } else {
            orderedDue + newList
        }
        return if (shuffle && base.size > 1) base.shuffled() else base
    }

    /**
     * Builds a rigorous AI Audit Prompt (modeled after ExamSet's Question Set Audit Hub)
     * that verifies Category/Part-of-Speech accuracy, Proficiency Level (A1-C2/Custom),
     * Meaning & Translation accuracy, Context Examples, and near-duplicates.
     */
    fun buildVocabularyAuditPrompt(
        deck: VocabularyDeck,
        preferredEnglishVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT
    ): String {
        val entries = deck.sortedEntriesAscending()
        val lang = deck.language.ifBlank { "English" }
        val resolvedVariant = deck.languageVariant.ifBlank {
            if (VocabularyLanguageVariants.isEnglish(lang)) preferredEnglishVariant else ""
        }
        val variantNote = if (resolvedVariant.isNotBlank()) {
            "Spelling & Pronunciation Origin Standard: ${VocabularyLanguageVariants.displayLabel(resolvedVariant)} (${VocabularyLanguageVariants.promptDirective(lang, resolvedVariant)})"
        } else ""
        val transNote = if (deck.translationLanguage.isNotBlank()) {
            "Translation Language: ${deck.translationLanguage}"
        } else {
            "Monolingual (no required bridge translation)"
        }
        return buildString {
            appendLine("Act as a senior lexicographer, linguist, and language-assessment auditor for $lang.")
            appendLine()
            appendLine("Audit the following vocabulary set thoroughly:")
            appendLine("- Set Title: ${deck.title}")
            appendLine("- Target Language: $lang")
            if (variantNote.isNotBlank()) appendLine("- $variantNote")
            appendLine("- $transNote")
            appendLine("- Target Deck Level: ${deck.effectiveLevelTitle()} | Domain: ${deck.domain}")
            appendLine()
            appendLine("For EVERY word below, verify:")
            appendLine("1. CATEGORY / WORD CLASS ACCURACY: Confirm exact part of speech / category (noun, verb, adjective, adverb, idiom, phrasal verb, collocation) and flag if misclassified.")
            appendLine("2. PROFICIENCY LEVEL ACCURACY: Confirm whether its assigned level (A1/A2/B1/B2/C1/C2) accurately reflects real usage frequency and difficulty.")
            appendLine("3. MEANING & TRANSLATION ACCURACY: Catch vague, circular, or misleading definitions or inaccurate translations.")
            appendLine("4. CONTEXT EXAMPLES QUALITY: Verify that example sentences sound natural, match the word's true register${if (resolvedVariant.isNotBlank()) " and $resolvedVariant standard" else ""}, and span distinct contexts.")
            appendLine("5. DUPLICATE / NEAR-DUPLICATE ALERT: Flag if two entries repeat the exact same sense unnecessarily.")
            appendLine()
            appendLine("Return ONLY a valid JSON array (no markdown commentary outside the JSON). Each object MUST follow this schema:")
            appendLine("[")
            appendLine("  {")
            appendLine("    \"word\": \"exact word from list\",")
            appendLine("    \"status\": \"VERIFIED | CATEGORY_ISSUE | LEVEL_MISMATCH | MEANING_FIX | EXAMPLE_FIX | DUPLICATE_ALERT\",")
            appendLine("    \"verifiedCategoryType\": \"e.g. Formal Adjective · Academic Register\",")
            appendLine("    \"issueSummary\": \"Short 1-sentence verdict or what needs fixing\",")
            appendLine("    \"suggestedPartOfSpeech\": \"optional corrected POS or empty string\",")
            appendLine("    \"suggestedLevel\": \"optional corrected A1/A2/B1/B2/C1/C2 or empty string\",")
            appendLine("    \"suggestedMeaning\": \"optional improved meaning or empty string\",")
            appendLine("    \"suggestedTranslation\": \"optional improved translation or empty string\",")
            appendLine("    \"suggestedUsageNote\": \"optional concise nuance/usage tip or empty string\",")
            appendLine("    \"suggestedExamples\": [{\"context\": \"Everyday\", \"sentence\": \"...\", \"translation\": \"...\"}]")
            appendLine("  }")
            appendLine("]")
            appendLine()
            appendLine("VOCABULARY LIST TO AUDIT (${entries.size} words):")
            entries.forEachIndexed { idx, e ->
                appendLine("${idx + 1}. WORD: ${e.word} | POS: ${e.partOfSpeech.ifBlank { "Unspecified" }} | LEVEL: ${e.effectiveLevelBadge()}")
                appendLine("   MEANING: ${e.meaning}")
                if (e.translation.isNotBlank()) appendLine("   TRANSLATION: ${e.translation}")
                if (e.examples.isNotEmpty()) {
                    e.examples.forEach { ex ->
                        appendLine("   EXAMPLE [${ex.context}]: ${ex.sentence}")
                    }
                }
            }
        }.trim()
    }

    /**
     * Parses AI Audit JSON output back into an actionable Audit Hub report matched against [deck].
     */
    fun parseVocabularyAuditReport(rawResponse: String, deck: VocabularyDeck): VocabularyAuditReport {
        val cleaned = stripOuterCodeFences(rawResponse)
        val arrayStr = extractJsonArrayString(cleaned)
            ?: return VocabularyAuditReport(emptyList(), 0, 0)

        val entriesByKey = deck.entries.associateBy { it.normalizedWordKey() }
        val findings = mutableListOf<VocabularyAuditFinding>()
        val unmatched = mutableListOf<String>()

        runCatching {
            val arr = JSONArray(arrayStr)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val word = firstNonBlank(obj, "word", "term", "headword")
                if (word.isBlank()) continue
                val matched = entriesByKey[VocabularyEntry.normalizeVocabularyWordKey(word)]
                if (matched == null) {
                    unmatched.add(word)
                    continue
                }
                val status = VocabularyAuditStatus.fromCode(firstNonBlank(obj, "status", "verdict", "issueType"))
                val summary = firstNonBlank(obj, "issueSummary", "summary", "reason", "comment", "feedback")
                    .ifBlank { if (status == VocabularyAuditStatus.VERIFIED) "Category, level, and usage verified." else "Review suggested improvement." }
                val categoryType = firstNonBlank(obj, "verifiedCategoryType", "categoryType", "category")
                val sugPos = firstNonBlank(obj, "suggestedPartOfSpeech", "partOfSpeech", "pos").ifBlank { null }
                val rawSugLvl = firstNonBlank(obj, "suggestedLevel", "level", "cefr")
                val sugLvlCode = if (rawSugLvl.isNotBlank()) VocabularyLevel.fromCode(rawSugLvl).code else null
                val sugCustomLvl = if (rawSugLvl.isNotBlank() && VocabularyLevel.fromCode(rawSugLvl) == VocabularyLevel.CUSTOM) rawSugLvl else null
                val sugMeaning = firstNonBlank(obj, "suggestedMeaning", "meaning").ifBlank { null }
                val sugTrans = firstNonBlank(obj, "suggestedTranslation", "translation").ifBlank { null }
                val sugNote = firstNonBlank(obj, "suggestedUsageNote", "usageNote", "note").ifBlank { null }
                val sugExamplesArr = obj.optJSONArray("suggestedExamples")
                val sugExamples = if (sugExamplesArr != null && sugExamplesArr.length() > 0) {
                    buildList {
                        for (j in 0 until sugExamplesArr.length()) {
                            when (val elem = sugExamplesArr.opt(j)) {
                                is JSONObject -> VocabularyExample.fromJson(elem)?.let { add(it) }
                                is String -> parseLabeledExampleLine(elem)?.let { add(it) }
                            }
                        }
                    }.ifEmpty { null }
                } else null

                findings.add(
                    VocabularyAuditFinding(
                        entryId = matched.id,
                        currentEntry = matched,
                        status = status,
                        issueSummary = summary,
                        verifiedCategoryType = categoryType,
                        suggestedPartOfSpeech = sugPos,
                        suggestedLevelCode = sugLvlCode,
                        suggestedCustomLevelLabel = sugCustomLvl,
                        suggestedMeaning = sugMeaning,
                        suggestedTranslation = sugTrans,
                        suggestedUsageNote = sugNote,
                        suggestedExamples = sugExamples
                    )
                )
            }
        }

        val verified = findings.count { it.status == VocabularyAuditStatus.VERIFIED }
        val flagged = findings.count { it.status != VocabularyAuditStatus.VERIFIED }
        return VocabularyAuditReport(
            findings = findings,
            verifiedCount = verified,
            flaggedCount = flagged,
            unmatchedWords = unmatched
        )
    }

    fun applyAuditFinding(entry: VocabularyEntry, finding: VocabularyAuditFinding): VocabularyEntry {
        return entry.copy(
            partOfSpeech = finding.suggestedPartOfSpeech?.takeIf { it.isNotBlank() } ?: entry.partOfSpeech,
            levelCode = finding.suggestedLevelCode?.takeIf { it.isNotBlank() } ?: entry.levelCode,
            customLevelLabel = finding.suggestedCustomLevelLabel ?: if (finding.suggestedLevelCode != null && finding.suggestedLevelCode != VocabularyLevel.CUSTOM.code) "" else entry.customLevelLabel,
            meaning = finding.suggestedMeaning?.takeIf { it.isNotBlank() } ?: entry.meaning,
            translation = finding.suggestedTranslation?.takeIf { it.isNotBlank() } ?: entry.translation,
            usageNote = finding.suggestedUsageNote?.takeIf { it.isNotBlank() } ?: entry.usageNote,
            examples = finding.suggestedExamples?.takeIf { it.isNotEmpty() } ?: entry.examples,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun mixedVirtualDeckId(language: String): String {
        val clean = language.trim().lowercase(Locale.ROOT).ifBlank { "english" }
        return "mixed_vocab_$clean"
    }

    fun levelVirtualDeckId(
        language: String,
        levelCode: String,
        customLevelLabel: String = ""
    ): String {
        val cleanLang = language.trim().lowercase(Locale.ROOT).ifBlank { "english" }
        val cleanLvl = VocabularyLevel.fromCode(levelCode)
            .takeUnless { it == VocabularyLevel.MIXED }
            ?.code
            ?.lowercase(Locale.ROOT)
            ?: "b1"
        val cleanCustom = customLevelLabel.trim().lowercase(Locale.ROOT)
        return if (cleanLvl == "custom" && cleanCustom.isNotBlank()) {
            "level_vocab_${cleanLang}_custom_${cleanCustom.replace(Regex("[^a-z0-9]+"), "_")}"
        } else {
            "level_vocab_${cleanLang}_$cleanLvl"
        }
    }

    /**
     * Resolves each entry's effective proficiency folder level while keeping full compatibility with
     * legacy MIXED decks or multi-level sets:
     * - Exact entry A1..C2 always wins.
     * - Named entry CUSTOM track wins.
     * - MIXED/unspecified falls back to host deck's non-MIXED level, or Intermediate (B1) safely.
     */
    fun resolveEffectiveEntryLevel(
        entry: VocabularyEntry,
        hostDeck: VocabularyDeck
    ): Pair<VocabularyLevel, String> {
        val entryLvl = VocabularyLevel.fromCode(entry.levelCode)
        if (entryLvl != VocabularyLevel.MIXED && entryLvl != VocabularyLevel.CUSTOM) {
            return entryLvl to ""
        }
        if (entryLvl == VocabularyLevel.CUSTOM && entry.customLevelLabel.isNotBlank()) {
            return VocabularyLevel.CUSTOM to entry.customLevelLabel.trim()
        }
        val hostLvl = hostDeck.resolvedLevel()
        return when {
            hostLvl == VocabularyLevel.CUSTOM && hostDeck.customLevelLabel.isNotBlank() ->
                VocabularyLevel.CUSTOM to hostDeck.customLevelLabel.trim()
            hostLvl != VocabularyLevel.MIXED && hostLvl != VocabularyLevel.CUSTOM ->
                hostLvl to ""
            else -> VocabularyLevel.INTERMEDIATE to ""
        }
    }

    fun buildLanguageFolderSummaries(
        decks: List<VocabularyDeck>,
        srsByDeck: Map<String, Map<String, FlashcardSrsState>>,
        nowMillis: Long = System.currentTimeMillis(),
        wotdThreshold: Int = 10
    ): List<VocabularyLanguageFolderSummary> {
        if (decks.isEmpty()) return emptyList()
        val grouped = decks.groupBy { deck ->
            deck.language.trim().ifBlank { "English" }.lowercase(Locale.ROOT)
        }
        return grouped.values.mapNotNull { langDecks ->
            val totalEntries = langDecks.sumOf { it.entries.size }
            if (totalEntries == 0) return@mapNotNull null
            val displayLanguage = langDecks
                .map { it.language.trim().ifBlank { "English" } }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }?.key ?: "English"
            val compiled = compileMixedVocabularyDeck(displayLanguage, langDecks, srsByDeck)
            val compiledStats = computeDeckSrsStats(compiled.virtualDeck, compiled.srsByEntryId, nowMillis)
            val levelFolders = buildLevelFolderSummaries(displayLanguage, langDecks, srsByDeck, nowMillis)

            VocabularyLanguageFolderSummary(
                language = displayLanguage,
                deckCount = langDecks.count { it.entries.isNotEmpty() },
                levelCount = levelFolders.size,
                totalWords = totalEntries,
                uniqueWords = compiled.virtualDeck.entries.size,
                dueCount = compiledStats.dueCount,
                masteredCount = compiledStats.masteredCount,
                wotdUnlocked = compiled.virtualDeck.entries.size >= wotdThreshold
            )
        }.sortedBy { it.language.lowercase(Locale.ROOT) }
    }

    fun buildLevelFolderSummaries(
        language: String,
        decks: List<VocabularyDeck>,
        srsByDeck: Map<String, Map<String, FlashcardSrsState>>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<VocabularyLevelFolderSummary> {
        val cleanLang = language.trim().ifBlank { "English" }
        val langDecks = decks.filter { it.language.trim().ifBlank { "English" }.equals(cleanLang, ignoreCase = true) }
        if (langDecks.isEmpty()) return emptyList()

        data class LevelBucketAccumulator(
            val level: VocabularyLevel,
            val customLabel: String,
            val contributingDeckIds: MutableSet<String> = mutableSetOf(),
            val uniqueMap: LinkedHashMap<String, Pair<VocabularyEntry, FlashcardSrsState?>> = linkedMapOf(),
            var rawWordCount: Int = 0
        )

        val grouped = linkedMapOf<String, LevelBucketAccumulator>()
        langDecks.sortedByDescending { it.updatedAt }.forEach { deck ->
            val deckSrs = srsByDeck[deck.id].orEmpty()
            deck.entries.forEach { entry ->
                val (lvl, customLabel) = resolveEffectiveEntryLevel(entry, deck)
                val bucketKey = if (lvl == VocabularyLevel.CUSTOM && customLabel.isNotBlank()) {
                    "CUSTOM:${customLabel.lowercase(Locale.ROOT)}"
                } else {
                    lvl.code
                }
                val acc = grouped.getOrPut(bucketKey) {
                    LevelBucketAccumulator(level = lvl, customLabel = customLabel)
                }
                acc.contributingDeckIds.add(deck.id)
                acc.rawWordCount++
                val wordKey = entry.normalizedWordKey().ifBlank { entry.id }
                val existing = acc.uniqueMap[wordKey]
                val incomingSrs = deckSrs[entry.id]
                if (existing == null) {
                    acc.uniqueMap[wordKey] = entry to incomingSrs
                } else if (existing.second == null && incomingSrs != null) {
                    acc.uniqueMap[wordKey] = existing.first to incomingSrs
                }
            }
        }

        return grouped.values.map { acc ->
            val virtualForStats = VocabularyDeck(
                id = levelVirtualDeckId(cleanLang, acc.level.code, acc.customLabel),
                title = acc.level.displayTitleForLanguage(cleanLang, acc.customLabel),
                language = cleanLang,
                levelCode = acc.level.code,
                customLevelLabel = acc.customLabel,
                entries = acc.uniqueMap.values.map { it.first }
            )
            val combinedSrs = acc.uniqueMap.values.mapNotNull { (entry, state) ->
                state?.let { entry.id to it }
            }.toMap()
            val stats = computeDeckSrsStats(virtualForStats, combinedSrs, nowMillis)

            VocabularyLevelFolderSummary(
                language = cleanLang,
                levelCode = acc.level.code,
                customLevelLabel = acc.customLabel,
                levelTitle = acc.level.displayTitleForLanguage(cleanLang, acc.customLabel),
                compactBadge = acc.level.compactBadgeForLanguage(cleanLang, acc.customLabel),
                sortRank = acc.level.sortRank,
                deckCount = acc.contributingDeckIds.size,
                totalWords = acc.rawWordCount,
                uniqueWords = acc.uniqueMap.size,
                dueCount = stats.dueCount,
                masteredCount = stats.masteredCount
            )
        }.sortedWith(
            compareBy<VocabularyLevelFolderSummary>(
                { it.sortRank },
                { it.customLevelLabel.lowercase(Locale.ROOT) },
                { it.levelTitle.lowercase(Locale.ROOT) }
            )
        )
    }

    /**
     * Compiles all vocabulary belonging to a single exact `(language, levelCode, customLevelLabel)`
     * folder across all underlying decks (including legacy MIXED or multi-set decks) with live write-back
     * source tracking so users browse and study Level Folders directly with zero sub-folder clutter.
     */
    fun compileLevelVocabularyDeck(
        language: String,
        levelCode: String,
        customLevelLabel: String = "",
        decks: List<VocabularyDeck>,
        srsByDeck: Map<String, Map<String, FlashcardSrsState>>
    ): CompiledMixedVocabularyDeck {
        val cleanLang = language.trim().ifBlank { "English" }
        val targetLevel = VocabularyLevel.fromCode(levelCode)
            .takeUnless { it == VocabularyLevel.MIXED }
            ?: VocabularyLevel.INTERMEDIATE
        val cleanCustom = if (targetLevel == VocabularyLevel.CUSTOM) customLevelLabel.trim() else ""

        val langDecks = decks
            .filter { it.language.trim().ifBlank { "English" }.equals(cleanLang, ignoreCase = true) }
            .sortedWith(
                compareBy<VocabularyDeck>(
                    { -it.updatedAt },
                    { it.title.lowercase(Locale.ROOT) }
                )
            )

        val dedupedByKey = linkedMapOf<String, Pair<VocabularyEntry, VocabularyCompiledSource>>()
        val srsByEntryId = mutableMapOf<String, FlashcardSrsState>()
        val contributingDecks = mutableSetOf<String>()

        langDecks.forEach { deck ->
            val deckSrs = srsByDeck[deck.id].orEmpty()
            deck.entries.forEach { entry ->
                val (entryLevel, entryCustom) = resolveEffectiveEntryLevel(entry, deck)
                val matchesLevel = if (targetLevel == VocabularyLevel.CUSTOM) {
                    entryLevel == VocabularyLevel.CUSTOM && entryCustom.equals(cleanCustom, ignoreCase = true)
                } else {
                    entryLevel == targetLevel
                }
                if (matchesLevel) {
                    contributingDecks.add(deck.id)
                    val wordKey = entry.normalizedWordKey().ifBlank { entry.id }
                    val existing = dedupedByKey[wordKey]
                    if (existing == null) {
                        dedupedByKey[wordKey] = entry to VocabularyCompiledSource(
                            originDeckId = deck.id,
                            originDeckTitle = deck.title,
                            originLevelCode = entryLevel.code,
                            originCustomLevelLabel = entryCustom
                        )
                        deckSrs[entry.id]?.let { srsByEntryId[entry.id] = it }
                    } else {
                        val existingSrs = srsByEntryId[existing.first.id]
                        val incomingSrs = deckSrs[entry.id]
                        if (existingSrs == null && incomingSrs != null) {
                            srsByEntryId[existing.first.id] = incomingSrs
                        }
                    }
                }
            }
        }

        val sortedEntries = dedupedByKey.values
            .map { it.first }
            .sortedWith(
                compareBy<VocabularyEntry>(
                    { it.word.trim().lowercase(Locale.ROOT) },
                    { it.partOfSpeech.trim().lowercase(Locale.ROOT) },
                    { it.createdAt }
                )
            )
        val sourceMap = dedupedByKey.values.associate { (entry, src) -> entry.id to src }
        val dominantVariant = langDecks
            .mapNotNull { it.languageVariant.trim().ifBlank { null } }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key.orEmpty()
        val levelTitle = targetLevel.displayTitleForLanguage(cleanLang, cleanCustom)

        val virtualDeck = VocabularyDeck(
            id = levelVirtualDeckId(cleanLang, targetLevel.code, cleanCustom),
            title = "$cleanLang • $levelTitle",
            language = cleanLang,
            languageVariant = dominantVariant,
            translationLanguage = "",
            levelCode = targetLevel.code,
            customLevelLabel = cleanCustom,
            domain = "$levelTitle Folder",
            entries = sortedEntries
        )

        return CompiledMixedVocabularyDeck(
            language = cleanLang,
            virtualDeck = virtualDeck,
            sourceByEntryId = sourceMap,
            srsByEntryId = srsByEntryId,
            compiledLevelCount = 1,
            compiledDeckCount = contributingDecks.size
        )
    }

    /**
     * Compiles all vocabulary across every level subfolder of [language] into one
     * deduplicated, live-synced Mixed Vocabulary deck while preserving each entry's
     * origin deck ID for write-back SRS rating, bookmarking, deletion, and auditing.
     */
    fun compileMixedVocabularyDeck(
        language: String,
        decks: List<VocabularyDeck>,
        srsByDeck: Map<String, Map<String, FlashcardSrsState>>
    ): CompiledMixedVocabularyDeck {
        val cleanLang = language.trim().ifBlank { "English" }
        val langDecks = decks
            .filter { it.language.trim().ifBlank { "English" }.equals(cleanLang, ignoreCase = true) }
            .sortedWith(
                compareBy<VocabularyDeck>(
                    { it.resolvedLevel().sortRank },
                    { -it.updatedAt },
                    { it.title.lowercase(Locale.ROOT) }
                )
            )

        val dedupedByKey = linkedMapOf<String, Pair<VocabularyEntry, VocabularyCompiledSource>>()
        val srsByEntryId = mutableMapOf<String, FlashcardSrsState>()
        val levelKeys = mutableSetOf<String>()

        langDecks.forEach { deck ->
            val deckLevel = deck.resolvedLevel()
            val levelKey = if (deckLevel == VocabularyLevel.CUSTOM && deck.customLevelLabel.isNotBlank()) {
                "CUSTOM:${deck.customLevelLabel.trim().lowercase(Locale.ROOT)}"
            } else {
                deckLevel.code
            }
            if (deck.entries.isNotEmpty()) {
                levelKeys.add(levelKey)
            }
            val deckSrs = srsByDeck[deck.id].orEmpty()
            deck.entries.forEach { entry ->
                val wordKey = entry.normalizedWordKey().ifBlank { entry.id }
                val existing = dedupedByKey[wordKey]
                if (existing == null) {
                    dedupedByKey[wordKey] = entry to VocabularyCompiledSource(
                        originDeckId = deck.id,
                        originDeckTitle = deck.title,
                        originLevelCode = deck.levelCode,
                        originCustomLevelLabel = deck.customLevelLabel
                    )
                    deckSrs[entry.id]?.let { srsByEntryId[entry.id] = it }
                } else {
                    // Preserve whichever entry has richer content or existing SRS progress
                    val existingSrs = srsByEntryId[existing.first.id]
                    val incomingSrs = deckSrs[entry.id]
                    if (existingSrs == null && incomingSrs != null) {
                        srsByEntryId[existing.first.id] = incomingSrs
                    }
                }
            }
        }

        val sortedEntries = dedupedByKey.values
            .map { it.first }
            .sortedWith(
                compareBy<VocabularyEntry>(
                    { it.word.trim().lowercase(Locale.ROOT) },
                    { it.partOfSpeech.trim().lowercase(Locale.ROOT) },
                    { it.createdAt }
                )
            )
        val sourceMap = dedupedByKey.values.associate { (entry, src) -> entry.id to src }
        val dominantVariant = langDecks
            .mapNotNull { it.languageVariant.trim().ifBlank { null } }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key.orEmpty()

        val virtualDeck = VocabularyDeck(
            id = mixedVirtualDeckId(cleanLang),
            title = "$cleanLang • Mixed Vocabulary",
            language = cleanLang,
            languageVariant = dominantVariant,
            translationLanguage = "",
            levelCode = VocabularyLevel.MIXED.code,
            customLevelLabel = "",
            domain = "${levelKeys.size.coerceAtLeast(1)} Level(s) Compiled",
            entries = sortedEntries
        )

        return CompiledMixedVocabularyDeck(
            language = cleanLang,
            virtualDeck = virtualDeck,
            sourceByEntryId = sourceMap,
            srsByEntryId = srsByEntryId,
            compiledLevelCount = levelKeys.size,
            compiledDeckCount = langDecks.count { it.entries.isNotEmpty() }
        )
    }

    data class GlobalWordSearchHit(
        val entry: VocabularyEntry,
        val deck: VocabularyDeck
    )

    fun searchEntriesAcrossDecks(
        decks: List<VocabularyDeck>,
        query: String,
        limit: Int = 40
    ): List<GlobalWordSearchHit> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return emptyList()
        val hits = mutableListOf<GlobalWordSearchHit>()
        val seenKeys = mutableSetOf<String>()
        decks.sortedWith(
            compareBy<VocabularyDeck>(
                { it.language.lowercase(Locale.ROOT) },
                { it.resolvedLevel().sortRank },
                { it.title.lowercase(Locale.ROOT) }
            )
        ).forEach { deck ->
            deck.entries.forEach { entry ->
                val matches = entry.word.lowercase(Locale.ROOT).contains(q) ||
                    entry.meaning.lowercase(Locale.ROOT).contains(q) ||
                    entry.translation.lowercase(Locale.ROOT).contains(q)
                if (matches) {
                    val dedupKey = "${deck.language.trim().lowercase(Locale.ROOT)}::${entry.normalizedWordKey()}"
                    if (seenKeys.add(dedupKey)) {
                        hits.add(GlobalWordSearchHit(entry = entry, deck = deck))
                        if (hits.size >= limit) return hits
                    }
                }
            }
        }
        return hits
    }

    fun compileAllDueVocabularyDeck(
        decks: List<VocabularyDeck>,
        srsByDeck: Map<String, Map<String, FlashcardSrsState>>,
        nowMillis: Long = TrustedTimeManager.getTrustedTimeMillis()
    ): CompiledMixedVocabularyDeck {
        val dedupedByKey = linkedMapOf<String, Pair<VocabularyEntry, VocabularyCompiledSource>>()
        val srsByEntryId = mutableMapOf<String, FlashcardSrsState>()
        val languagesSeen = mutableSetOf<String>()
        val sourceDeckIds = mutableSetOf<String>()

        decks.sortedWith(
            compareBy<VocabularyDeck>(
                { it.language.lowercase(Locale.ROOT) },
                { it.resolvedLevel().sortRank },
                { -it.updatedAt }
            )
        ).forEach { deck ->
            val lang = deck.language.trim().ifBlank { "English" }
            val deckSrs = srsByDeck[deck.id].orEmpty()
            deck.entries.forEach { entry ->
                val st = deckSrs[entry.id]
                val status = SpacedRepetitionEngine.statusOf(st, nowMillis)
                if (status == FlashcardSrsStatus.DUE || status == FlashcardSrsStatus.NEW) {
                    val wordKey = "${lang.lowercase(Locale.ROOT)}::${entry.normalizedWordKey().ifBlank { entry.id }}"
                    if (!dedupedByKey.containsKey(wordKey)) {
                        dedupedByKey[wordKey] = entry to VocabularyCompiledSource(
                            originDeckId = deck.id,
                            originDeckTitle = "$lang • ${deck.title}",
                            originLevelCode = deck.levelCode,
                            originCustomLevelLabel = deck.customLevelLabel
                        )
                        st?.let { srsByEntryId[entry.id] = it }
                        languagesSeen.add(lang)
                        sourceDeckIds.add(deck.id)
                    }
                }
            }
        }

        val sortedEntries = dedupedByKey.values
            .map { it.first }
            .sortedWith(
                compareBy<VocabularyEntry>(
                    { srsByEntryId[it.id]?.dueAtMillis ?: 0L },
                    { it.word.trim().lowercase(Locale.ROOT) }
                )
            )
        val sourceMap = dedupedByKey.values.associate { (entry, src) -> entry.id to src }

        val virtualDeck = VocabularyDeck(
            id = "__due_all_languages__",
            title = "Due Today • All Languages",
            language = if (languagesSeen.size == 1) languagesSeen.first() else "Multi-Language",
            languageVariant = "",
            translationLanguage = "",
            levelCode = VocabularyLevel.MIXED.code,
            customLevelLabel = "",
            domain = "${languagesSeen.size.coerceAtLeast(1)} Language(s) Due",
            entries = sortedEntries
        )

        return CompiledMixedVocabularyDeck(
            language = virtualDeck.language,
            virtualDeck = virtualDeck,
            sourceByEntryId = sourceMap,
            srsByEntryId = srsByEntryId,
            compiledLevelCount = languagesSeen.size,
            compiledDeckCount = sourceDeckIds.size
        )
    }

    fun findOrCreateTargetDeck(
        currentDecks: List<VocabularyDeck>,
        language: String,
        levelCode: String,
        customLevelLabel: String = "",
        variant: String = ""
    ): Pair<VocabularyDeck, List<VocabularyDeck>> {
        val cleanLang = language.trim().ifBlank { "English" }
        val safeLevel = VocabularyLevel.fromCode(levelCode)
            .takeUnless { it == VocabularyLevel.MIXED }
            ?: VocabularyLevel.INTERMEDIATE
        val safeCustom = if (safeLevel == VocabularyLevel.CUSTOM) customLevelLabel.trim() else ""
        val cleanVariant = if (VocabularyLanguageVariants.isEnglish(cleanLang)) {
            VocabularyLanguageVariants.normalizeVariant(cleanLang, variant)
        } else variant.trim()

        val existing = currentDecks.firstOrNull { deck ->
            deck.language.trim().equals(cleanLang, ignoreCase = true) &&
                deck.resolvedLevel() == safeLevel &&
                (safeLevel != VocabularyLevel.CUSTOM || deck.customLevelLabel.trim().equals(safeCustom, ignoreCase = true))
        }
        if (existing != null) {
            return existing to currentDecks
        }
        val now = System.currentTimeMillis()
        val levelTitle = safeLevel.displayTitleForLanguage(cleanLang, safeCustom)
        val newDeck = VocabularyDeck(
            title = "$cleanLang • $levelTitle",
            language = cleanLang,
            languageVariant = cleanVariant,
            translationLanguage = "",
            levelCode = safeLevel.code,
            customLevelLabel = safeCustom,
            domain = "General",
            entries = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        return newDeck to (listOf(newDeck) + currentDecks)
    }

    fun parseQuickCaptureHeadwords(
        rawInput: String,
        defaultLanguage: String = "English",
        defaultLevelCode: String = "B2",
        defaultDomain: String = "Quick Capture"
    ): List<HeadwordQueueItem> {
        val cleanLang = defaultLanguage.trim().ifBlank { "English" }
        val safeLevel = VocabularyLevel.fromCode(defaultLevelCode)
            .takeUnless { it == VocabularyLevel.MIXED }
            ?.code ?: "B2"
        val tokens = rawInput
            .split('\n', ',', ';', '|')
            .map { it.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim() }
            .filter { it.isNotBlank() }

        return tokens.mapNotNull { token ->
            // Support optional context hint in parentheses: e.g., "sanction (penalty)"
            val hintMatch = Regex("^(.+?)\\s*\\(([^)]+)\\)$").matchEntire(token)
            val word = (hintMatch?.groupValues?.get(1) ?: token).trim()
            val hint = hintMatch?.groupValues?.get(2)?.trim().orEmpty()
            if (word.isBlank()) return@mapNotNull null
            HeadwordQueueItem(
                word = word,
                language = cleanLang,
                levelCode = safeLevel,
                domain = defaultDomain,
                utilityReason = if (hint.isNotBlank()) "Context hint: $hint" else ""
            )
        }
    }

    fun parseHeadwordsJson(
        rawJson: String,
        defaultLanguage: String = "English"
    ): List<HeadwordQueueItem> {
        val trimmed = stripOuterCodeFences(rawJson)
        if (trimmed.isBlank()) return emptyList()

        for (candidate in buildJsonSanitizedCandidates(trimmed)) {
            val parsed = runCatching {
                val jsonArray = when {
                    candidate.startsWith("[") -> JSONArray(candidate)
                    candidate.startsWith("{") -> {
                        val root = JSONObject(candidate)
                        root.optJSONArray("headwords")
                            ?: root.optJSONArray("words")
                            ?: root.optJSONArray("items")
                            ?: root.optJSONArray("data")
                            ?: JSONArray()
                    }
                    else -> {
                        extractBalancedJsonArray(candidate)?.let { JSONArray(it) } ?: JSONArray()
                    }
                }

                buildList {
                    for (i in 0 until jsonArray.length()) {
                        val rawElem = jsonArray.opt(i)
                        when (rawElem) {
                            is String -> {
                                val word = rawElem.trim()
                                if (word.isNotBlank()) {
                                    add(
                                        HeadwordQueueItem(
                                            word = word,
                                            language = defaultLanguage.trim().ifBlank { "English" },
                                            levelCode = "B2",
                                            domain = "Curated Index"
                                        )
                                    )
                                }
                            }
                            is JSONObject -> {
                                val word = firstNonBlank(rawElem, "word", "headword", "term", "vocab")
                                if (word.isBlank()) continue
                                val lang = firstNonBlank(rawElem, "language", "lang", "targetLanguage")
                                    .ifBlank { defaultLanguage.trim().ifBlank { "English" } }
                                val level = firstNonBlank(rawElem, "level", "levelCode", "cefr", "proficiency")
                                    .ifBlank { "B2" }
                                val resolvedLvl = VocabularyLevel.fromCode(level)
                                    .takeUnless { it == VocabularyLevel.MIXED }
                                    ?: VocabularyLevel.UPPER_INTERMEDIATE
                                val customLevel = if (resolvedLvl == VocabularyLevel.CUSTOM) level else {
                                    rawElem.optString("customLevelLabel", "").trim()
                                }
                                val pos = firstNonBlank(rawElem, "pos", "partOfSpeech", "category", "wordClass")
                                val domain = firstNonBlank(rawElem, "domain", "category", "register")
                                    .ifBlank { "Competitive Exam Editorial" }
                                val reason = firstNonBlank(rawElem, "utilityReason", "reason", "utility", "context", "hint")
                                add(
                                    HeadwordQueueItem(
                                        word = word,
                                        language = lang,
                                        levelCode = resolvedLvl.code,
                                        customLevelLabel = customLevel,
                                        partOfSpeech = pos,
                                        domain = domain,
                                        utilityReason = reason
                                    )
                                )
                            }
                        }
                    }
                }
            }.getOrNull()

            if (!parsed.isNullOrEmpty()) return parsed
        }
        return emptyList()
    }

    fun filterNewHeadwords(
        incoming: List<HeadwordQueueItem>,
        existingDecks: List<VocabularyDeck>,
        existingQueue: List<HeadwordQueueItem>,
        dismissedKeys: Set<String>
    ): HeadwordFilterResult {
        val existingDeckKeysByLang = mutableMapOf<String, MutableSet<String>>()
        existingDecks.forEach { deck ->
            val langKey = deck.language.trim().lowercase(Locale.ROOT)
            val set = existingDeckKeysByLang.getOrPut(langKey) { mutableSetOf() }
            deck.entries.forEach { entry ->
                val k = entry.normalizedWordKey()
                if (k.isNotBlank()) set.add(k)
            }
        }

        val queueKeysByLang = mutableMapOf<String, MutableSet<String>>()
        existingQueue.forEach { item ->
            val langKey = item.language.trim().lowercase(Locale.ROOT)
            val set = queueKeysByLang.getOrPut(langKey) { mutableSetOf() }
            val k = item.normalizedWordKey()
            if (k.isNotBlank()) set.add(k)
        }

        val normalizedDismissed = dismissedKeys.map { VocabularyEntry.normalizeVocabularyWordKey(it) }.toSet()

        val added = mutableListOf<HeadwordQueueItem>()
        var skippedInDeck = 0
        var skippedInQueue = 0
        var skippedDismissed = 0

        incoming.forEach { item ->
            val key = item.normalizedWordKey()
            if (key.isBlank()) return@forEach

            val langKey = item.language.trim().lowercase(Locale.ROOT)
            val deckSet = existingDeckKeysByLang[langKey] ?: emptySet()
            val queueSet = queueKeysByLang.getOrPut(langKey) { mutableSetOf() }

            when {
                key in deckSet -> {
                    skippedInDeck++
                }
                key in queueSet -> {
                    skippedInQueue++
                }
                key in normalizedDismissed -> {
                    skippedDismissed++
                }
                else -> {
                    added.add(item)
                    queueSet.add(key)
                }
            }
        }

        return HeadwordFilterResult(
            addedItems = added,
            skippedAlreadyInDeckCount = skippedInDeck,
            skippedAlreadyInQueueCount = skippedInQueue,
            skippedDismissedCount = skippedDismissed,
            totalProcessedCount = incoming.size
        )
    }

    fun buildStage2EnrichmentPrompt(
        items: List<HeadwordQueueItem>,
        targetLanguage: String,
        nativeExplanationLanguage: String = "English",
        preferredVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT
    ): String {
        val lang = targetLanguage.trim().ifBlank { "English" }
        val resolvedVariant = VocabularyLanguageVariants.normalizeVariant(lang, preferredVariant)
        val variantDirective = VocabularyLanguageVariants.promptDirective(lang, resolvedVariant)
        val transLang = nativeExplanationLanguage.trim()
        val hasTrans = transLang.isNotBlank() && !transLang.equals("None", ignoreCase = true) && !transLang.equals(lang, ignoreCase = true)

        val headwordLines = items.joinToString("\n") { item ->
            val hint = item.utilityReason.takeIf { it.isNotBlank() }?.let { " | Note: $it" }.orEmpty()
            val posHint = item.partOfSpeech.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            "- ${item.word.trim()}$posHint [Target Level: ${item.levelCode}]$hint"
        }

        return buildString {
            appendLine("Act as a chief lexicographer, exam setter, and language pedagogue for $lang.")
            appendLine("Enrich ONLY the following ${items.size} headword(s) into complete vocabulary cards:")
            appendLine(headwordLines)
            appendLine()
            appendLine("### STRICT INSTRUCTIONS:")
            appendLine("1. Generate complete cards ONLY for the exact headwords listed above. Do NOT omit, rename, or substitute words.")
            appendLine("2. LANGUAGE STANDARD: $lang${if (resolvedVariant.isNotBlank()) " (${VocabularyLanguageVariants.displayLabel(resolvedVariant)})" else ""}.")
            if (variantDirective.isNotBlank()) {
                appendLine("3. SPELLING & PRONUNCIATION: $variantDirective")
            }
            appendLine("4. For each word verify/provide: accurate partOfSpeech, calibrated CEFR level (`A1`–`C2`), crisp learner-friendly meaning, IPA/romanization pronunciation, 3 natural example sentences across distinct contexts (Everyday, Workplace/Academic, Editorial/Formal), synonyms, antonyms, collocations, and a 1-sentence usageNote.")
            if (hasTrans) {
                appendLine("5. Provide natural $transLang translations for each definition and example sentence.")
            }
            appendLine()
            appendLine("### OUTPUT FORMAT: STRICT JSON ARRAY")
            appendLine("Return ONLY a valid JSON array inside a single ```json code block starting with `[` and ending with `]`:")
            appendLine("[")
            appendLine("  {")
            appendLine("    \"word\": \"headword\",")
            appendLine("    \"language\": \"$lang\",")
            if (resolvedVariant.isNotBlank()) appendLine("    \"languageVariant\": \"$resolvedVariant\",")
            appendLine("    \"pronunciation\": \"/IPA/ or romanization\",")
            appendLine("    \"partOfSpeech\": \"noun | verb | adjective | adverb | idiom | phrasal verb\",")
            appendLine("    \"level\": \"A1 | A2 | B1 | B2 | C1 | C2\",")
            appendLine("    \"meaning\": \"Clear, precise definition in $lang\",")
            appendLine("    \"translation\": \"${if (hasTrans) "Natural $transLang translation" else ""}\",")
            if (hasTrans) appendLine("    \"translationLanguage\": \"$transLang\",")
            appendLine("    \"examples\": [")
            appendLine("      { \"context\": \"Everyday\", \"sentence\": \"Natural sentence 1\", \"translation\": \"${if (hasTrans) "Translation 1" else ""}\" },")
            appendLine("      { \"context\": \"Workplace/Academic\", \"sentence\": \"Natural sentence 2\", \"translation\": \"${if (hasTrans) "Translation 2" else ""}\" },")
            appendLine("      { \"context\": \"Editorial/Formal\", \"sentence\": \"Natural sentence 3\", \"translation\": \"${if (hasTrans) "Translation 3" else ""}\" }")
            appendLine("    ],")
            appendLine("    \"synonyms\": [\"syn1\", \"syn2\"],")
            appendLine("    \"antonyms\": [\"ant1\"],")
            appendLine("    \"collocations\": [\"collocation 1\", \"collocation 2\"],")
            appendLine("    \"usageNote\": \"1-sentence register/nuance note\"")
            appendLine("  }")
            appendLine("]")
        }.trim()
    }
}
