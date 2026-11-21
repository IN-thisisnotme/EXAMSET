package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.FolderItem
import org.json.JSONArray
import org.json.JSONObject

class ExamSetPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("examset_app_prefs", Context.MODE_PRIVATE)

    fun getLastActivePaperId(): Long {
        return prefs.getLong(KEY_LAST_ACTIVE_PAPER_ID, -1L)
    }

    fun setLastActivePaperId(id: Long) {
        prefs.edit().putLong(KEY_LAST_ACTIVE_PAPER_ID, id).apply()
    }

    fun getDraftRawText(): String {
        return prefs.getString(KEY_DRAFT_RAW_TEXT, "") ?: ""
    }

    fun setDraftRawText(text: String) {
        prefs.edit().putString(KEY_DRAFT_RAW_TEXT, text).apply()
    }

    fun getActiveTabIndex(): Int {
        return prefs.getInt(KEY_ACTIVE_TAB_INDEX, 0)
    }

    fun setActiveTabIndex(index: Int) {
        prefs.edit().putInt(KEY_ACTIVE_TAB_INDEX, index).apply()
    }

    fun getCustomFolders(): List<FolderItem> {
        val jsonStr = prefs.getString(KEY_CUSTOM_FOLDERS, null)
        if (jsonStr.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<FolderItem>()
            val legacyNames = setOf("Science & Emerging Tech", "UPSC Civil Services / PSC", "SSC CGL / Banking")
            val legacyIds = setOf("f-science", "f-upsc", "f-ssc")
            var hadLegacy = false
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                if (id in legacyIds || name in legacyNames) {
                    hadLegacy = true
                    continue
                }
                val parentId = if (obj.has("parentId") && !obj.isNull("parentId")) {
                    obj.getString("parentId").ifBlank { null }
                } else null

                list.add(
                    FolderItem(
                        id = id,
                        name = name,
                        color = obj.optString("color", "#0284C7"),
                        parentId = parentId,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            if (hadLegacy) {
                saveCustomFolders(list)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomFolders(folders: List<FolderItem>) {
        val arr = JSONArray()
        folders.forEach { f ->
            val obj = JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("color", f.color)
                if (f.parentId != null) {
                    put("parentId", f.parentId)
                }
                put("createdAt", f.createdAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_FOLDERS, arr.toString()).apply()
    }

    fun getDailyGoalMinutes(): Int {
        return prefs.getInt(KEY_DAILY_GOAL_MINUTES, 30)
    }

    fun setDailyGoalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DAILY_GOAL_MINUTES, minutes.coerceIn(5, 360)).apply()
    }

    fun getFontSizeScale(): Float {
        return prefs.getFloat(KEY_FONT_SIZE_SCALE, 1.0f)
    }

    fun setFontSizeScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT_SIZE_SCALE, scale.coerceIn(0.85f, 1.6f)).apply()
    }

    fun getFontScaleMode(): AppFontScaleMode {
        val saved = prefs.getString(KEY_FONT_SCALE_MODE, AppFontScaleMode.STANDARD_FIXED.name)
        return AppFontScaleMode.fromString(saved)
    }

    fun setFontScaleMode(mode: AppFontScaleMode) {
        prefs.edit()
            .putString(KEY_FONT_SCALE_MODE, mode.name)
            .putFloat(KEY_FONT_SIZE_SCALE, mode.scaleFactor)
            .apply()
    }

    fun getShuffleOptions(): Boolean {
        return prefs.getBoolean(KEY_SHUFFLE_OPTIONS, false)
    }

    fun setShuffleOptions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE_OPTIONS, enabled).apply()
    }

    fun getLastActiveFolderId(): String {
        return prefs.getString(KEY_LAST_ACTIVE_FOLDER_ID, "all") ?: "all"
    }

    fun setLastActiveFolderId(folderId: String) {
        prefs.edit().putString(KEY_LAST_ACTIVE_FOLDER_ID, folderId).apply()
    }

    fun getFlashcardSrsMap(noteId: Long): Map<Int, com.example.model.FlashcardSrsState> {
        if (noteId <= 0L) return emptyMap()
        val raw = prefs.getString("$KEY_SRS_PREFIX$noteId", null) ?: return emptyMap()
        return parseSrsJson(raw)
    }

    fun getAllFlashcardSrsMaps(): Map<Long, Map<Int, com.example.model.FlashcardSrsState>> {
        val result = mutableMapOf<Long, Map<Int, com.example.model.FlashcardSrsState>>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_SRS_PREFIX) && value is String) {
                val noteId = key.removePrefix(KEY_SRS_PREFIX).toLongOrNull()
                if (noteId != null && noteId > 0L) {
                    val parsed = parseSrsJson(value)
                    if (parsed.isNotEmpty()) {
                        result[noteId] = parsed
                    }
                }
            }
        }
        return result
    }

    fun saveFlashcardSrsMap(noteId: Long, map: Map<Int, com.example.model.FlashcardSrsState>) {
        if (noteId <= 0L) return
        val arr = JSONArray()
        map.values.forEach { st ->
            arr.put(
                JSONObject().apply {
                    put("blockIndex", st.blockIndex)
                    put("repetitions", st.repetitions)
                    put("easeFactor", st.easeFactor.toDouble())
                    put("intervalDays", st.intervalDays)
                    put("dueAtMillis", st.dueAtMillis)
                    put("lastReviewedAtMillis", st.lastReviewedAtMillis)
                    put("lapses", st.lapses)
                    put("lastRating", st.lastRating?.name ?: "")
                    put("reviewCount", st.reviewCount)
                    put("correctCount", st.correctCount)
                }
            )
        }
        prefs.edit().putString("$KEY_SRS_PREFIX$noteId", arr.toString()).apply()
    }

    fun clearFlashcardSrsMap(noteId: Long) {
        if (noteId <= 0L) return
        prefs.edit().remove("$KEY_SRS_PREFIX$noteId").apply()
    }

    private fun parseSrsJson(raw: String): Map<Int, com.example.model.FlashcardSrsState> {
        if (raw.isBlank()) return emptyMap()
        return try {
            val arr = JSONArray(raw)
            val out = mutableMapOf<Int, com.example.model.FlashcardSrsState>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val idx = obj.optInt("blockIndex", -1)
                if (idx >= 0) {
                    val ratingName = obj.optString("lastRating", "")
                    val rating = runCatching {
                        if (ratingName.isNotBlank()) com.example.model.SrsRating.valueOf(ratingName) else null
                    }.getOrNull()
                    out[idx] = com.example.model.FlashcardSrsState(
                        blockIndex = idx,
                        repetitions = obj.optInt("repetitions", 0),
                        easeFactor = obj.optDouble("easeFactor", 2.5).toFloat().coerceIn(1.3f, 3.0f),
                        intervalDays = obj.optInt("intervalDays", 0),
                        dueAtMillis = obj.optLong("dueAtMillis", 0L),
                        lastReviewedAtMillis = obj.optLong("lastReviewedAtMillis", 0L),
                        lapses = obj.optInt("lapses", 0),
                        lastRating = rating,
                        reviewCount = obj.optInt("reviewCount", 0),
                        correctCount = obj.optInt("correctCount", 0)
                    )
                }
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getPromptPresets(): List<com.example.model.PromptPreset> {
        val raw = prefs.getString(KEY_PROMPT_PRESETS, null)
        return com.example.model.PromptPreset.deserializeList(raw)
    }

    fun savePromptPresets(presets: List<com.example.model.PromptPreset>) {
        prefs.edit().putString(KEY_PROMPT_PRESETS, com.example.model.PromptPreset.serializeList(presets)).apply()
    }

    fun getPromptPresetsRawJson(): String {
        return prefs.getString(KEY_PROMPT_PRESETS, "[]") ?: "[]"
    }

    fun getQuestionPromptDraft(): com.example.model.QuestionPromptConfig? {
        val raw = prefs.getString(KEY_QUESTION_PROMPT_DRAFT, null) ?: return null
        return runCatching {
            com.example.model.QuestionPromptConfig.fromJson(JSONObject(raw))
        }.getOrNull()
    }

    fun saveQuestionPromptDraft(config: com.example.model.QuestionPromptConfig) {
        prefs.edit().putString(KEY_QUESTION_PROMPT_DRAFT, config.toJson().toString()).apply()
    }

    fun clearQuestionPromptDraft() {
        prefs.edit().remove(KEY_QUESTION_PROMPT_DRAFT).apply()
    }

    fun getKnowledgePromptDraft(): com.example.model.KnowledgePromptConfig? {
        val raw = prefs.getString(KEY_KNOWLEDGE_PROMPT_DRAFT, null) ?: return null
        return runCatching {
            com.example.model.KnowledgePromptConfig.fromJson(JSONObject(raw))
        }.getOrNull()
    }

    fun saveKnowledgePromptDraft(config: com.example.model.KnowledgePromptConfig) {
        prefs.edit().putString(KEY_KNOWLEDGE_PROMPT_DRAFT, config.toJson().toString()).apply()
    }

    fun clearKnowledgePromptDraft() {
        prefs.edit().remove(KEY_KNOWLEDGE_PROMPT_DRAFT).apply()
    }

    fun getFlashcardPromptDraft(): com.example.model.FlashcardPromptConfig? {
        val raw = prefs.getString(KEY_FLASHCARD_PROMPT_DRAFT, null) ?: return null
        return runCatching {
            com.example.model.FlashcardPromptConfig.fromJson(JSONObject(raw))
        }.getOrNull()
    }

    fun saveFlashcardPromptDraft(config: com.example.model.FlashcardPromptConfig) {
        prefs.edit().putString(KEY_FLASHCARD_PROMPT_DRAFT, config.toJson().toString()).apply()
    }

    fun clearFlashcardPromptDraft() {
        prefs.edit().remove(KEY_FLASHCARD_PROMPT_DRAFT).apply()
    }

    fun getVocabularyPromptDraft(): com.example.model.VocabularyPromptConfig? {
        val raw = prefs.getString(KEY_VOCABULARY_PROMPT_DRAFT, null) ?: return null
        return runCatching {
            com.example.model.VocabularyPromptConfig.fromJson(JSONObject(raw))
        }.getOrNull()
    }

    fun saveVocabularyPromptDraft(config: com.example.model.VocabularyPromptConfig) {
        prefs.edit().putString(KEY_VOCABULARY_PROMPT_DRAFT, config.toJson().toString()).apply()
    }

    fun clearVocabularyPromptDraft() {
        prefs.edit().remove(KEY_VOCABULARY_PROMPT_DRAFT).apply()
    }

    fun getVocabularyDecks(): List<com.example.model.VocabularyDeck> {
        val raw = prefs.getString(KEY_VOCABULARY_DECKS, null)
        return com.example.model.VocabularyDeck.deserializeList(raw)
    }

    fun saveVocabularyDecks(decks: List<com.example.model.VocabularyDeck>) {
        prefs.edit().putString(KEY_VOCABULARY_DECKS, com.example.model.VocabularyDeck.serializeList(decks)).apply()
    }

    fun getVocabularyDecksRawJson(): String {
        return prefs.getString(KEY_VOCABULARY_DECKS, "[]") ?: "[]"
    }

    fun getHeadwordInbox(): List<com.example.model.HeadwordQueueItem> {
        val raw = prefs.getString(KEY_HEADWORD_INBOX, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                com.example.model.HeadwordQueueItem.fromJson(arr.optJSONObject(i))
            }
        }.getOrDefault(emptyList())
    }

    fun saveHeadwordInbox(items: List<com.example.model.HeadwordQueueItem>) {
        val arr = JSONArray().apply {
            items.forEach { put(it.toJson()) }
        }
        prefs.edit().putString(KEY_HEADWORD_INBOX, arr.toString()).apply()
    }

    fun getHeadwordInboxRawJson(): String {
        return prefs.getString(KEY_HEADWORD_INBOX, "[]") ?: "[]"
    }

    fun getDismissedWordKeys(): Set<String> {
        val raw = prefs.getString(KEY_DISMISSED_WORD_KEYS, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }.toSet()
        }.getOrDefault(emptySet())
    }

    fun saveDismissedWordKeys(keys: Set<String>) {
        val arr = JSONArray().apply {
            keys.forEach { put(it) }
        }
        prefs.edit().putString(KEY_DISMISSED_WORD_KEYS, arr.toString()).apply()
    }

    fun addDismissedWordKey(key: String) {
        val norm = com.example.model.VocabularyEntry.normalizeVocabularyWordKey(key)
        if (norm.isBlank()) return
        val current = getDismissedWordKeys().toMutableSet()
        current.add(norm)
        saveDismissedWordKeys(current)
    }

    fun getDismissedWordKeysRawJson(): String {
        return prefs.getString(KEY_DISMISSED_WORD_KEYS, "[]") ?: "[]"
    }

    fun getAllVocabularySrsMaps(): Map<String, Map<String, com.example.model.FlashcardSrsState>> {
        val raw = prefs.getString(KEY_VOCABULARY_SRS, null) ?: return emptyMap()
        return parseVocabSrsRootJson(raw)
    }

    fun getVocabularySrsRawJson(): String {
        return prefs.getString(KEY_VOCABULARY_SRS, "{}") ?: "{}"
    }

    fun saveAllVocabularySrsMaps(allMaps: Map<String, Map<String, com.example.model.FlashcardSrsState>>) {
        val root = JSONObject()
        allMaps.forEach { (deckId, entryMap) ->
            if (deckId.isNotBlank() && entryMap.isNotEmpty()) {
                val deckObj = JSONObject()
                entryMap.forEach { (entryId, st) ->
                    if (entryId.isNotBlank()) {
                        deckObj.put(
                            entryId,
                            JSONObject().apply {
                                put("repetitions", st.repetitions)
                                put("easeFactor", st.easeFactor.toDouble())
                                put("intervalDays", st.intervalDays)
                                put("dueAtMillis", st.dueAtMillis)
                                put("lastReviewedAtMillis", st.lastReviewedAtMillis)
                                put("lapses", st.lapses)
                                put("lastRating", st.lastRating?.name ?: "")
                                put("reviewCount", st.reviewCount)
                                put("correctCount", st.correctCount)
                            }
                        )
                    }
                }
                root.put(deckId, deckObj)
            }
        }
        prefs.edit().putString(KEY_VOCABULARY_SRS, root.toString()).apply()
    }

    fun parseVocabSrsRootJson(raw: String): Map<String, Map<String, com.example.model.FlashcardSrsState>> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, Map<String, com.example.model.FlashcardSrsState>>()
            val deckKeys = root.keys()
            while (deckKeys.hasNext()) {
                val deckId = deckKeys.next()
                val deckObj = root.optJSONObject(deckId) ?: continue
                val entryMap = mutableMapOf<String, com.example.model.FlashcardSrsState>()
                val entryKeys = deckObj.keys()
                while (entryKeys.hasNext()) {
                    val entryId = entryKeys.next()
                    val obj = deckObj.optJSONObject(entryId) ?: continue
                    val ratingName = obj.optString("lastRating", "")
                    val rating = runCatching {
                        if (ratingName.isNotBlank()) com.example.model.SrsRating.valueOf(ratingName) else null
                    }.getOrNull()
                    entryMap[entryId] = com.example.model.FlashcardSrsState(
                        blockIndex = 0,
                        repetitions = obj.optInt("repetitions", 0),
                        easeFactor = obj.optDouble("easeFactor", 2.5).toFloat().coerceIn(1.3f, 3.0f),
                        intervalDays = obj.optInt("intervalDays", 0),
                        dueAtMillis = obj.optLong("dueAtMillis", 0L),
                        lastReviewedAtMillis = obj.optLong("lastReviewedAtMillis", 0L),
                        lapses = obj.optInt("lapses", 0),
                        lastRating = rating,
                        reviewCount = obj.optInt("reviewCount", 0),
                        correctCount = obj.optInt("correctCount", 0)
                    )
                }
                if (entryMap.isNotEmpty()) result[deckId] = entryMap
            }
            result
        }.getOrDefault(emptyMap())
    }

    fun getPreferredEnglishVariant(): String {
        val raw = prefs.getString(KEY_PREFERRED_ENGLISH_VARIANT, com.example.model.VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT)
        return com.example.model.VocabularyLanguageVariants.normalizeVariant("English", raw)
    }

    fun setPreferredEnglishVariant(variant: String) {
        val clean = com.example.model.VocabularyLanguageVariants.normalizeVariant("English", variant)
        prefs.edit().putString(KEY_PREFERRED_ENGLISH_VARIANT, clean).apply()
    }

    fun getWordOfTheDayCacheJson(): String? {
        return prefs.getString(KEY_WORD_OF_THE_DAY_CACHE, null)?.ifBlank { null }
    }

    fun saveWordOfTheDayCacheJson(rawJson: String) {
        prefs.edit().putString(KEY_WORD_OF_THE_DAY_CACHE, rawJson).apply()
    }

    fun getLanguageWordOfTheDayStatesJson(): String? {
        return prefs.getString(KEY_WORD_OF_THE_DAY_LANGUAGE_STATES, null)?.ifBlank { null }
    }

    fun saveLanguageWordOfTheDayStatesJson(rawJson: String) {
        prefs.edit().putString(KEY_WORD_OF_THE_DAY_LANGUAGE_STATES, rawJson).apply()
    }

    fun getActivePromptPresetId(mode: com.example.model.PromptPresetMode): String? {
        return prefs.getString("$KEY_ACTIVE_PROMPT_PRESET_PREFIX${mode.code}", null)?.ifBlank { null }
    }

    fun setActivePromptPresetId(mode: com.example.model.PromptPresetMode, presetId: String?) {
        val key = "$KEY_ACTIVE_PROMPT_PRESET_PREFIX${mode.code}"
        if (presetId.isNullOrBlank()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, presetId).apply()
        }
    }

    fun isVocabularyReminderEnabled(): Boolean {
        return prefs.getBoolean(KEY_VOCAB_DAILY_REMINDER_ENABLED, false)
    }

    fun setVocabularyReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOCAB_DAILY_REMINDER_ENABLED, enabled).apply()
    }

    fun hasCleanedLegacySamples(): Boolean {
        return prefs.getBoolean(KEY_LEGACY_SAMPLES_CLEANED, false)
    }

    fun setLegacySamplesCleaned(cleaned: Boolean) {
        prefs.edit().putBoolean(KEY_LEGACY_SAMPLES_CLEANED, cleaned).apply()
    }

    // --- Exam-Day Countdowns ---

    fun getExamCountdowns(): List<com.example.model.ExamCountdownTarget> {
        val raw = prefs.getString(KEY_EXAM_COUNTDOWNS, null)
        return com.example.model.ExamCountdownTarget.deserializeList(raw)
    }

    fun saveExamCountdowns(targets: List<com.example.model.ExamCountdownTarget>) {
        prefs.edit().putString(
            KEY_EXAM_COUNTDOWNS,
            com.example.model.ExamCountdownTarget.serializeList(targets)
        ).apply()
    }

    fun getExamCountdownsRawJson(): String {
        return prefs.getString(KEY_EXAM_COUNTDOWNS, "[]") ?: "[]"
    }

    // --- Study Planner & Pomodoro Focus ---

    fun getStudyTodos(): List<com.example.model.StudyTodoItem> {
        val raw = prefs.getString(KEY_STUDY_TODOS, null)
        return com.example.model.StudyTodoItem.deserializeList(raw)
    }

    fun saveStudyTodos(items: List<com.example.model.StudyTodoItem>) {
        prefs.edit().putString(
            KEY_STUDY_TODOS,
            com.example.model.StudyTodoItem.serializeList(items)
        ).apply()
    }

    fun getStudyTodosRawJson(): String {
        return prefs.getString(KEY_STUDY_TODOS, "[]") ?: "[]"
    }

    fun getPomodoroPresetName(): String {
        return prefs.getString(KEY_POMODORO_PRESET, com.example.model.PomodoroPreset.CLASSIC_25_5.name)
            ?: com.example.model.PomodoroPreset.CLASSIC_25_5.name
    }

    fun setPomodoroPresetName(name: String) {
        prefs.edit().putString(KEY_POMODORO_PRESET, name).apply()
    }

    fun getPomodoroCustomFocusMinutes(): Int {
        return prefs.getInt(KEY_POMODORO_CUSTOM_FOCUS_MINS, 30).coerceIn(5, 180)
    }

    fun setPomodoroCustomFocusMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_POMODORO_CUSTOM_FOCUS_MINS, minutes.coerceIn(5, 180)).apply()
    }

    fun getPomodoroCustomBreakMinutes(): Int {
        return prefs.getInt(KEY_POMODORO_CUSTOM_BREAK_MINS, 5).coerceIn(1, 60)
    }

    fun setPomodoroCustomBreakMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_POMODORO_CUSTOM_BREAK_MINS, minutes.coerceIn(1, 60)).apply()
    }

    fun getPreferredDndMode(): com.example.model.PomodoroDndMode {
        val raw = prefs.getString(KEY_POMODORO_DND_MODE, com.example.model.PomodoroDndMode.PRIORITY_CALLS.name)
        return com.example.model.PomodoroDndMode.fromString(raw)
    }

    fun setPreferredDndMode(mode: com.example.model.PomodoroDndMode) {
        prefs.edit().putString(KEY_POMODORO_DND_MODE, mode.name).apply()
    }

    fun getDndPolicyBackup(): com.example.model.DndPolicyBackup {
        return com.example.model.DndPolicyBackup(
            wasDndManagedByApp = prefs.getBoolean(KEY_DND_MANAGED_BY_APP, false),
            previousInterruptionFilter = prefs.getInt(KEY_DND_PREVIOUS_FILTER, 1),
            managedSessionStartedAt = prefs.getLong(KEY_DND_SESSION_STARTED_AT, 0L)
        )
    }

    /**
     * Synchronous commit so process death never loses the pre-Focus DND state.
     */
    fun saveDndPolicyBackupSync(backup: com.example.model.DndPolicyBackup): Boolean {
        return prefs.edit()
            .putBoolean(KEY_DND_MANAGED_BY_APP, backup.wasDndManagedByApp)
            .putInt(KEY_DND_PREVIOUS_FILTER, backup.previousInterruptionFilter)
            .putLong(KEY_DND_SESSION_STARTED_AT, backup.managedSessionStartedAt)
            .commit()
    }

    fun isFormatTipDismissed(tipId: String): Boolean {
        val dismissed = getDismissedFormatTips()
        return tipId in dismissed
    }

    fun dismissFormatTip(tipId: String) {
        val current = getDismissedFormatTips().toMutableSet()
        if (current.add(tipId)) {
            val arr = JSONArray().apply { current.forEach { put(it) } }
            prefs.edit().putString(KEY_DISMISSED_FORMAT_TIPS, arr.toString()).apply()
        }
    }

    private fun getDismissedFormatTips(): Set<String> {
        val raw = prefs.getString(KEY_DISMISSED_FORMAT_TIPS, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }.toSet()
        }.getOrDefault(emptySet())
    }

    companion object {
        private const val KEY_LAST_ACTIVE_PAPER_ID = "key_last_active_paper_id"
        private const val KEY_DRAFT_RAW_TEXT = "key_draft_raw_text"
        private const val KEY_ACTIVE_TAB_INDEX = "key_active_tab_index"
        private const val KEY_CUSTOM_FOLDERS = "key_custom_folders"
        private const val KEY_DAILY_GOAL_MINUTES = "key_daily_goal_minutes"
        private const val KEY_FONT_SIZE_SCALE = "key_font_size_scale"
        private const val KEY_FONT_SCALE_MODE = "key_font_scale_mode"
        private const val KEY_SHUFFLE_OPTIONS = "key_shuffle_options"
        private const val KEY_LAST_ACTIVE_FOLDER_ID = "key_last_active_folder_id"
        private const val KEY_SRS_PREFIX = "key_flashcard_srs_"
        private const val KEY_PROMPT_PRESETS = "key_prompt_presets_v1"
        private const val KEY_QUESTION_PROMPT_DRAFT = "key_prompt_draft_question_v1"
        private const val KEY_KNOWLEDGE_PROMPT_DRAFT = "key_prompt_draft_knowledge_v1"
        private const val KEY_FLASHCARD_PROMPT_DRAFT = "key_prompt_draft_flashcard_v1"
        private const val KEY_VOCABULARY_PROMPT_DRAFT = "key_prompt_draft_vocabulary_v1"
        private const val KEY_VOCABULARY_DECKS = "key_vocabulary_decks_v1"
        private const val KEY_VOCABULARY_SRS = "key_vocabulary_srs_v1"
        private const val KEY_PREFERRED_ENGLISH_VARIANT = "key_vocab_preferred_english_variant_v1"
        private const val KEY_WORD_OF_THE_DAY_CACHE = "key_vocab_wotd_cache_v1"
        private const val KEY_WORD_OF_THE_DAY_LANGUAGE_STATES = "key_vocab_wotd_lang_states_v1"
        private const val KEY_ACTIVE_PROMPT_PRESET_PREFIX = "key_active_prompt_preset_"
        private const val KEY_VOCAB_DAILY_REMINDER_ENABLED = "key_vocab_daily_reminder_enabled_v1"
        private const val KEY_HEADWORD_INBOX = "key_headword_inbox_v1"
        private const val KEY_DISMISSED_WORD_KEYS = "key_dismissed_word_keys_v1"
        private const val KEY_LEGACY_SAMPLES_CLEANED = "key_legacy_samples_cleaned_v1"
        private const val KEY_EXAM_COUNTDOWNS = "key_exam_countdowns_v1"
        private const val KEY_STUDY_TODOS = "key_study_todos_v1"
        private const val KEY_POMODORO_PRESET = "key_pomodoro_preset_v1"
        private const val KEY_POMODORO_CUSTOM_FOCUS_MINS = "key_pomodoro_custom_focus_v1"
        private const val KEY_POMODORO_CUSTOM_BREAK_MINS = "key_pomodoro_custom_break_v1"
        private const val KEY_POMODORO_DND_MODE = "key_pomodoro_dnd_mode_v1"
        private const val KEY_DND_MANAGED_BY_APP = "key_dnd_managed_by_app_v1"
        private const val KEY_DND_PREVIOUS_FILTER = "key_dnd_previous_filter_v1"
        private const val KEY_DND_SESSION_STARTED_AT = "key_dnd_session_started_at_v1"
        private const val KEY_DISMISSED_FORMAT_TIPS = "key_dismissed_format_tips_v1"
    }
}
