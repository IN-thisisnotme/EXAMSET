package com.example.util

import com.example.data.ExamSetPreferences
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyLanguageVariants
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WordOfTheDayHistoryItem(
    val utcDateKey: String,
    val wordKey: String,
    val word: String,
    val partOfSpeech: String = "",
    val pronunciation: String = "",
    val meaning: String = "",
    val translation: String = "",
    val levelCode: String = "",
    val customLevelLabel: String = "",
    val sourceDeckId: String = "",
    val sourceDeckTitle: String = "",
    val shownAtMillis: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("utcDateKey", utcDateKey)
        put("wordKey", wordKey)
        put("word", word)
        put("partOfSpeech", partOfSpeech)
        put("pronunciation", pronunciation)
        put("meaning", meaning)
        put("translation", translation)
        put("levelCode", levelCode)
        put("customLevelLabel", customLevelLabel)
        put("sourceDeckId", sourceDeckId)
        put("sourceDeckTitle", sourceDeckTitle)
        put("shownAtMillis", shownAtMillis)
    }

    companion object {
        fun fromJson(obj: JSONObject?): WordOfTheDayHistoryItem? {
            if (obj == null) return null
            val utcDateKey = obj.optString("utcDateKey", "").trim()
            val wordKey = obj.optString("wordKey", "").trim()
            val word = obj.optString("word", "").trim()
            if (utcDateKey.isBlank() || wordKey.isBlank() || word.isBlank()) return null
            return WordOfTheDayHistoryItem(
                utcDateKey = utcDateKey,
                wordKey = wordKey,
                word = word,
                partOfSpeech = obj.optString("partOfSpeech", "").trim(),
                pronunciation = obj.optString("pronunciation", "").trim(),
                meaning = obj.optString("meaning", "").trim(),
                translation = obj.optString("translation", "").trim(),
                levelCode = obj.optString("levelCode", "").trim(),
                customLevelLabel = obj.optString("customLevelLabel", "").trim(),
                sourceDeckId = obj.optString("sourceDeckId", "").trim(),
                sourceDeckTitle = obj.optString("sourceDeckTitle", "").trim(),
                shownAtMillis = obj.optLong("shownAtMillis", 0L)
            )
        }
    }
}

data class LanguageWordOfTheDayState(
    val languageKey: String,
    val activeUtcDateKey: String = "",
    val activeWordKey: String = "",
    val seenWordKeysInCycle: List<String> = emptyList(),
    val cycleNumber: Int = 1,
    val history: List<WordOfTheDayHistoryItem> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("languageKey", languageKey)
        put("activeUtcDateKey", activeUtcDateKey)
        put("activeWordKey", activeWordKey)
        put("seenWordKeysInCycle", JSONArray().apply { seenWordKeysInCycle.forEach { put(it) } })
        put("cycleNumber", cycleNumber)
        put("history", JSONArray().apply { history.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(obj: JSONObject?): LanguageWordOfTheDayState? {
            if (obj == null) return null
            val langKey = obj.optString("languageKey", "").trim().lowercase(Locale.ROOT)
            if (langKey.isBlank()) return null

            val seenArr = obj.optJSONArray("seenWordKeysInCycle")
            val seenList = buildList {
                if (seenArr != null) {
                    for (i in 0 until seenArr.length()) {
                        val k = seenArr.optString(i, "").trim()
                        if (k.isNotBlank()) add(k)
                    }
                }
            }

            val histArr = obj.optJSONArray("history")
            val histList = buildList {
                if (histArr != null) {
                    for (i in 0 until histArr.length()) {
                        WordOfTheDayHistoryItem.fromJson(histArr.optJSONObject(i))?.let { add(it) }
                    }
                }
            }

            return LanguageWordOfTheDayState(
                languageKey = langKey,
                activeUtcDateKey = obj.optString("activeUtcDateKey", "").trim(),
                activeWordKey = obj.optString("activeWordKey", "").trim(),
                seenWordKeysInCycle = seenList.distinct(),
                cycleNumber = obj.optInt("cycleNumber", 1).coerceAtLeast(1),
                history = histList
            )
        }
    }
}

sealed class WordOfTheDayUiState {
    data class Hidden(
        val language: String,
        val currentUniqueWords: Int,
        val minThreshold: Int = WordOfTheDayService.MIN_WORDS_THRESHOLD
    ) : WordOfTheDayUiState()

    data class Ready(
        val language: String,
        val entry: VocabularyEntry,
        val utcDateKey: String,
        val dateLabel: String,
        val variant: String,
        val sourceDeckId: String,
        val sourceDeckTitle: String,
        val uniqueWordCount: Int,
        val seenInCycleCount: Int,
        val cycleNumber: Int,
        val history: List<WordOfTheDayHistoryItem>
    ) : WordOfTheDayUiState()
}

private data class LanguageWordCandidate(
    val wordKey: String,
    val entry: VocabularyEntry,
    val sourceDeck: VocabularyDeck
)

/**
 * Offline, language-folder-specific, threshold-gated, UTC 00:00 Word of the Day engine.
 *
 * - Never fetches from external/online dictionaries.
 * - Activates per language folder only once that language has at least [MIN_WORDS_THRESHOLD] unique saved words.
 * - Rolls over cleanly at 00:00 UTC using [TrustedTimeManager].
 * - Progresses systematically (Level sortRank -> A-Z) and tracks seen words in the active cycle
 *   plus chronological history so no saved word is skipped or missed.
 */
object WordOfTheDayService {

    const val MIN_WORDS_THRESHOLD = 10
    private const val MAX_HISTORY_ENTRIES = 365

    private val UTC_TIME_ZONE: TimeZone = TimeZone.getTimeZone("UTC")

    fun utcDateKey(nowMillis: Long = TrustedTimeManager.getTrustedTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = UTC_TIME_ZONE
        return sdf.format(Date(nowMillis))
    }

    fun formattedUtcDateLabel(nowMillis: Long = TrustedTimeManager.getTrustedTimeMillis()): String {
        val sdf = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH)
        sdf.timeZone = UTC_TIME_ZONE
        return sdf.format(Date(nowMillis))
    }

    fun formatUtcDateKeyForDisplay(utcDateKey: String): String {
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = UTC_TIME_ZONE }
            val date = parser.parse(utcDateKey) ?: return utcDateKey
            val formatter = SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH).apply { timeZone = UTC_TIME_ZONE }
            formatter.format(date)
        }.getOrDefault(utcDateKey)
    }

    fun resolveForLanguage(
        prefs: ExamSetPreferences,
        decks: List<VocabularyDeck>,
        language: String?,
        preferredEnglishVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT,
        cycleNext: Boolean = false,
        nowMillis: Long = TrustedTimeManager.getTrustedTimeMillis()
    ): WordOfTheDayUiState {
        val cleanLanguage = language?.trim().orEmpty()
        if (cleanLanguage.isBlank() || cleanLanguage.equals("All", ignoreCase = true)) {
            return WordOfTheDayUiState.Hidden(language = "", currentUniqueWords = 0)
        }

        val candidates = collectOrderedCandidates(
            decks = decks,
            language = cleanLanguage,
            preferredEnglishVariant = preferredEnglishVariant
        )
        if (candidates.size < MIN_WORDS_THRESHOLD) {
            return WordOfTheDayUiState.Hidden(
                language = cleanLanguage,
                currentUniqueWords = candidates.size,
                minThreshold = MIN_WORDS_THRESHOLD
            )
        }

        val normLangKey = cleanLanguage.lowercase(Locale.ROOT)
        val allStates = loadLanguageStateMap(prefs).toMutableMap()
        val existingState = allStates[normLangKey] ?: LanguageWordOfTheDayState(languageKey = normLangKey)

        val rawTodayUtc = utcDateKey(nowMillis)
        val todayUtc = if (existingState.activeUtcDateKey.isNotBlank() && rawTodayUtc < existingState.activeUtcDateKey) {
            existingState.activeUtcDateKey
        } else {
            rawTodayUtc
        }
        val dateLabel = if (todayUtc == rawTodayUtc) {
            formattedUtcDateLabel(nowMillis)
        } else {
            formatUtcDateKeyForDisplay(todayUtc)
        }
        val candidateByKey = candidates.associateBy { it.wordKey }
        val isEnglish = VocabularyLanguageVariants.isEnglish(cleanLanguage)
        val activeVariant = if (isEnglish) {
            VocabularyLanguageVariants.normalizeVariant(cleanLanguage, preferredEnglishVariant)
        } else ""

        // Keep the locked UTC word if we are on the same UTC day, not manually advancing, and the word still exists
        if (!cycleNext && existingState.activeUtcDateKey == todayUtc && existingState.activeWordKey.isNotBlank()) {
            val activeCandidate = candidateByKey[existingState.activeWordKey]
            if (activeCandidate != null) {
                val ensuredSeen = if (activeCandidate.wordKey in existingState.seenWordKeysInCycle) {
                    existingState.seenWordKeysInCycle.filter { candidateByKey.containsKey(it) }
                } else {
                    (existingState.seenWordKeysInCycle + activeCandidate.wordKey).filter { candidateByKey.containsKey(it) }
                }
                val ensuredHistory = ensureTodayHistoryItem(
                    history = existingState.history,
                    todayUtc = todayUtc,
                    candidate = activeCandidate,
                    nowMillis = nowMillis
                )
                if (ensuredSeen != existingState.seenWordKeysInCycle || ensuredHistory != existingState.history) {
                    val repaired = existingState.copy(
                        seenWordKeysInCycle = ensuredSeen,
                        history = ensuredHistory
                    )
                    allStates[normLangKey] = repaired
                    saveLanguageStateMap(prefs, allStates)
                }
                return WordOfTheDayUiState.Ready(
                    language = cleanLanguage,
                    entry = activeCandidate.entry,
                    utcDateKey = todayUtc,
                    dateLabel = dateLabel,
                    variant = activeVariant,
                    sourceDeckId = activeCandidate.sourceDeck.id,
                    sourceDeckTitle = activeCandidate.sourceDeck.title,
                    uniqueWordCount = candidates.size,
                    seenInCycleCount = ensuredSeen.size.coerceAtLeast(1),
                    cycleNumber = existingState.cycleNumber,
                    history = ensuredHistory
                )
            }
        }

        // Advance systematically on a new UTC day (00:00 UTC), manual Next Word tap, or if prior word was deleted
        val validSeen = existingState.seenWordKeysInCycle
            .filter { candidateByKey.containsKey(it) }
            .toMutableList()
        if (existingState.activeWordKey.isNotBlank() &&
            candidateByKey.containsKey(existingState.activeWordKey) &&
            existingState.activeWordKey !in validSeen
        ) {
            validSeen.add(existingState.activeWordKey)
        }

        val unseenCandidates = candidates.filter { it.wordKey !in validSeen }
        val nextCycleNumber: Int
        val updatedSeen: MutableList<String>
        val chosen: LanguageWordCandidate

        if (unseenCandidates.isNotEmpty()) {
            nextCycleNumber = existingState.cycleNumber
            updatedSeen = validSeen
            chosen = unseenCandidates.first()
        } else {
            // Completed 100% of words in this language -> begin next full cycle without immediate repeat
            nextCycleNumber = existingState.cycleNumber + 1
            updatedSeen = mutableListOf()
            chosen = candidates.firstOrNull { it.wordKey != existingState.activeWordKey } ?: candidates.first()
        }

        if (chosen.wordKey !in updatedSeen) {
            updatedSeen.add(chosen.wordKey)
        }

        val newHistoryItem = WordOfTheDayHistoryItem(
            utcDateKey = todayUtc,
            wordKey = chosen.wordKey,
            word = chosen.entry.word,
            partOfSpeech = chosen.entry.partOfSpeech,
            pronunciation = chosen.entry.pronunciation,
            meaning = chosen.entry.meaning,
            translation = chosen.entry.translation,
            levelCode = chosen.entry.levelCode,
            customLevelLabel = chosen.entry.customLevelLabel,
            sourceDeckId = chosen.sourceDeck.id,
            sourceDeckTitle = chosen.sourceDeck.title,
            shownAtMillis = nowMillis
        )

        val updatedHistory = buildList {
            add(newHistoryItem)
            existingState.history.forEach { old ->
                if (!(old.utcDateKey == todayUtc && old.wordKey == chosen.wordKey)) {
                    add(old)
                }
            }
        }.take(MAX_HISTORY_ENTRIES)

        val nextState = LanguageWordOfTheDayState(
            languageKey = normLangKey,
            activeUtcDateKey = todayUtc,
            activeWordKey = chosen.wordKey,
            seenWordKeysInCycle = updatedSeen,
            cycleNumber = nextCycleNumber,
            history = updatedHistory
        )
        allStates[normLangKey] = nextState
        saveLanguageStateMap(prefs, allStates)

        return WordOfTheDayUiState.Ready(
            language = cleanLanguage,
            entry = chosen.entry,
            utcDateKey = todayUtc,
            dateLabel = dateLabel,
            variant = activeVariant,
            sourceDeckId = chosen.sourceDeck.id,
            sourceDeckTitle = chosen.sourceDeck.title,
            uniqueWordCount = candidates.size,
            seenInCycleCount = updatedSeen.size,
            cycleNumber = nextCycleNumber,
            history = updatedHistory
        )
    }

    fun getLanguageHistory(
        prefs: ExamSetPreferences,
        language: String
    ): List<WordOfTheDayHistoryItem> {
        val normKey = language.trim().lowercase(Locale.ROOT)
        if (normKey.isBlank()) return emptyList()
        return loadLanguageStateMap(prefs)[normKey]?.history.orEmpty()
    }

    private fun ensureTodayHistoryItem(
        history: List<WordOfTheDayHistoryItem>,
        todayUtc: String,
        candidate: LanguageWordCandidate,
        nowMillis: Long
    ): List<WordOfTheDayHistoryItem> {
        val alreadyRecorded = history.any { it.utcDateKey == todayUtc && it.wordKey == candidate.wordKey }
        if (alreadyRecorded) return history
        val item = WordOfTheDayHistoryItem(
            utcDateKey = todayUtc,
            wordKey = candidate.wordKey,
            word = candidate.entry.word,
            partOfSpeech = candidate.entry.partOfSpeech,
            pronunciation = candidate.entry.pronunciation,
            meaning = candidate.entry.meaning,
            translation = candidate.entry.translation,
            levelCode = candidate.entry.levelCode,
            customLevelLabel = candidate.entry.customLevelLabel,
            sourceDeckId = candidate.sourceDeck.id,
            sourceDeckTitle = candidate.sourceDeck.title,
            shownAtMillis = nowMillis
        )
        return (listOf(item) + history).take(MAX_HISTORY_ENTRIES)
    }

    private fun collectOrderedCandidates(
        decks: List<VocabularyDeck>,
        language: String,
        preferredEnglishVariant: String
    ): List<LanguageWordCandidate> {
        val isEnglish = VocabularyLanguageVariants.isEnglish(language)
        val normVariant = if (isEnglish) {
            VocabularyLanguageVariants.normalizeVariant(language, preferredEnglishVariant)
        } else ""

        val matchingDecks = decks
            .filter { it.language.trim().ifBlank { "English" }.equals(language, ignoreCase = true) }
            .sortedWith(
                compareBy<VocabularyDeck>(
                    {
                        if (isEnglish && it.languageVariant.equals(normVariant, ignoreCase = true)) 0 else 1
                    },
                    { it.resolvedLevel().sortRank },
                    { -it.updatedAt },
                    { it.title.lowercase(Locale.ROOT) }
                )
            )

        val deduped = linkedMapOf<String, LanguageWordCandidate>()
        matchingDecks.forEach { deck ->
            deck.entries.forEach { entry ->
                if (entry.meaning.isBlank() && entry.translation.isBlank()) return@forEach
                val wordKey = entry.normalizedWordKey()
                if (wordKey.isNotBlank() && !deduped.containsKey(wordKey)) {
                    deduped[wordKey] = LanguageWordCandidate(
                        wordKey = wordKey,
                        entry = entry,
                        sourceDeck = deck
                    )
                }
            }
        }

        return deduped.values.sortedWith(
            compareBy<LanguageWordCandidate>(
                { it.entry.resolvedLevel().sortRank },
                { it.entry.customLevelLabel.trim().lowercase(Locale.ROOT) },
                { it.entry.word.trim().lowercase(Locale.ROOT) },
                { it.entry.partOfSpeech.trim().lowercase(Locale.ROOT) }
            )
        )
    }

    internal fun loadLanguageStateMap(prefs: ExamSetPreferences): Map<String, LanguageWordOfTheDayState> {
        val raw = prefs.getLanguageWordOfTheDayStatesJson() ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val parsed = LanguageWordOfTheDayState.fromJson(root.optJSONObject(key))
                    if (parsed != null) {
                        put(key.lowercase(Locale.ROOT), parsed)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    internal fun saveLanguageStateMap(
        prefs: ExamSetPreferences,
        states: Map<String, LanguageWordOfTheDayState>
    ) {
        val root = JSONObject()
        states.forEach { (langKey, state) ->
            root.put(langKey.lowercase(Locale.ROOT), state.toJson())
        }
        prefs.saveLanguageWordOfTheDayStatesJson(root.toString())
    }

    fun millisUntilNextUtcMidnight(
        nowMillis: Long = TrustedTimeManager.getTrustedTimeMillis()
    ): Long {
        val dayMs = 86_400_000L
        val elapsedInDay = ((nowMillis % dayMs) + dayMs) % dayMs
        return (dayMs - elapsedInDay).coerceIn(0L, dayMs)
    }

    fun formatUtcCountdownLabel(
        nowMillis: Long = TrustedTimeManager.getTrustedTimeMillis()
    ): String {
        val remaining = millisUntilNextUtcMidnight(nowMillis)
        val totalMinutes = (remaining / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0L -> "Next in ${hours}h ${minutes}m UTC"
            minutes > 0L -> "Next in ${minutes}m UTC"
            else -> "Next in <1m UTC"
        }
    }

    fun parseStatesJson(raw: String?): Map<String, LanguageWordOfTheDayState> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val parsed = LanguageWordOfTheDayState.fromJson(root.optJSONObject(key))
                    if (parsed != null) {
                        put(key.lowercase(Locale.ROOT), parsed)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun mergePersistedLanguageStatesJson(existingRaw: String?, incomingRaw: String?): String {
        val existing = parseStatesJson(existingRaw)
        val incoming = parseStatesJson(incomingRaw)
        if (incoming.isEmpty()) return existingRaw ?: "{}"
        if (existing.isEmpty()) return incomingRaw ?: "{}"

        val merged = existing.toMutableMap()
        incoming.forEach { (langKey, inc) ->
            val cur = merged[langKey]
            if (cur == null) {
                merged[langKey] = inc
            } else {
                val keepIncomingActive = inc.activeUtcDateKey > cur.activeUtcDateKey ||
                    (inc.activeUtcDateKey == cur.activeUtcDateKey && cur.activeWordKey.isBlank())
                val mergedHistory = (cur.history + inc.history)
                    .distinctBy { "${it.utcDateKey}::${it.wordKey}" }
                    .sortedByDescending { it.utcDateKey }
                    .take(MAX_HISTORY_ENTRIES)
                merged[langKey] = LanguageWordOfTheDayState(
                    languageKey = cur.languageKey,
                    activeUtcDateKey = if (keepIncomingActive) inc.activeUtcDateKey else cur.activeUtcDateKey,
                    activeWordKey = if (keepIncomingActive) inc.activeWordKey else cur.activeWordKey,
                    seenWordKeysInCycle = (cur.seenWordKeysInCycle + inc.seenWordKeysInCycle).distinct(),
                    cycleNumber = maxOf(cur.cycleNumber, inc.cycleNumber),
                    history = mergedHistory
                )
            }
        }
        val root = JSONObject()
        merged.forEach { (k, v) -> root.put(k, v.toJson()) }
        return root.toString()
    }
}
