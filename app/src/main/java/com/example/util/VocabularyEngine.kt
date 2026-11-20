package com.example.util

import com.example.model.DeckSrsStats
import com.example.model.FlashcardSrsState
import com.example.model.FlashcardSrsStatus
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyExample
import com.example.model.VocabularyLevel
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
    val warnings: List<String> = emptyList()
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
        defaultCustomLevelLabel: String = ""
    ): VocabularyParseResult {
        val cleaned = stripOuterCodeFences(rawInput)
        if (cleaned.isBlank()) {
            return VocabularyParseResult(emptyList(), VocabularyInputFormat.UNKNOWN, listOf("Input is empty."))
        }

        // 1. Try JSON parser first when input looks like JSON
        val jsonAttempt = tryParseJsonVocabulary(cleaned, defaultLevelCode, defaultCustomLevelLabel)
        if (jsonAttempt != null && jsonAttempt.entries.isNotEmpty()) {
            return jsonAttempt
        }

        // 2. Fall back to Structured Text parser
        val textResult = parseStructuredTextVocabulary(cleaned, defaultLevelCode, defaultCustomLevelLabel)
        if (textResult.entries.isNotEmpty()) {
            return textResult
        }

        return VocabularyParseResult(
            entries = emptyList(),
            detectedFormat = VocabularyInputFormat.UNKNOWN,
            warnings = listOf("Could not detect structured vocabulary entries. Use JSON or labeled blocks (WORD:, MEANING:, EXAMPLES:).")
        )
    }

    private fun stripOuterCodeFences(raw: String): String {
        val trimmed = raw.trim()
        val fencedRegex = Regex("```(?:json|text|markdown|md)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = fencedRegex.find(trimmed)
        return if (match != null && match.groupValues[1].trim().isNotBlank()) {
            match.groupValues[1].trim()
        } else {
            trimmed
        }
    }

    private fun tryParseJsonVocabulary(
        cleaned: String,
        defaultLevelCode: String,
        defaultCustomLevelLabel: String
    ): VocabularyParseResult? {
        val arrayCandidate = extractJsonArrayString(cleaned) ?: return null
        return runCatching {
            val arr = JSONArray(arrayCandidate)
            val entries = mutableListOf<VocabularyEntry>()
            val warnings = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val parsed = parseJsonEntryObject(obj, defaultLevelCode, defaultCustomLevelLabel)
                if (parsed != null) {
                    entries.add(parsed)
                } else {
                    warnings.add("Skipped JSON item #${i + 1} because word or meaning was missing.")
                }
            }
            VocabularyParseResult(
                entries = entries,
                detectedFormat = VocabularyInputFormat.JSON,
                warnings = warnings
            )
        }.getOrNull()
    }

    private fun extractJsonArrayString(cleaned: String): String? {
        if (cleaned.startsWith("[")) {
            val end = cleaned.lastIndexOf(']')
            if (end > 0) return cleaned.substring(0, end + 1)
        }
        if (cleaned.startsWith("{")) {
            val obj = runCatching { JSONObject(cleaned) }.getOrNull()
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
        }
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
        val resolvedLvl = if (rawLevel.isNotBlank()) VocabularyLevel.fromCode(rawLevel) else VocabularyLevel.fromCode(defaultLevelCode)
        val customLvl = if (resolvedLvl == VocabularyLevel.CUSTOM) {
            rawLevel.ifBlank { defaultCustomLevelLabel }
        } else {
            ""
        }

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
            meaning = meaning.ifBlank { translation },
            translation = translation,
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
                val lvl = if (levelRaw.isNotBlank()) VocabularyLevel.fromCode(levelRaw) else VocabularyLevel.fromCode(defaultLevelCode)
                entries.add(
                    VocabularyEntry(
                        word = currentWord.trim(),
                        pronunciation = pronunciation.trim(),
                        partOfSpeech = pos.trim(),
                        levelCode = lvl.code,
                        customLevelLabel = if (lvl == VocabularyLevel.CUSTOM) levelRaw.ifBlank { defaultCustomLevelLabel } else "",
                        meaning = meaning.trim().ifBlank { translation.trim() },
                        translation = translation.trim(),
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
    fun buildVocabularyAuditPrompt(deck: VocabularyDeck): String {
        val entries = deck.sortedEntriesAscending()
        val lang = deck.language.ifBlank { "English" }
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
            appendLine("- $transNote")
            appendLine("- Target Deck Level: ${deck.effectiveLevelTitle()} | Domain: ${deck.domain}")
            appendLine()
            appendLine("For EVERY word below, verify:")
            appendLine("1. CATEGORY / WORD CLASS ACCURACY: Confirm exact part of speech / category (noun, verb, adjective, adverb, idiom, phrasal verb, collocation) and flag if misclassified.")
            appendLine("2. PROFICIENCY LEVEL ACCURACY: Confirm whether its assigned level (A1/A2/B1/B2/C1/C2) accurately reflects real usage frequency and difficulty.")
            appendLine("3. MEANING & TRANSLATION ACCURACY: Catch vague, circular, or misleading definitions or inaccurate translations.")
            appendLine("4. CONTEXT EXAMPLES QUALITY: Verify that example sentences sound natural, match the word's true register, and span distinct contexts.")
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
}
