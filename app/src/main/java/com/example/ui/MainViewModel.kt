package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AttemptEntity
import com.example.data.BackupSnapshotEntity
import com.example.data.CloudAuthStore
import com.example.data.DailyStudyAnalyticsEntity
import com.example.data.ExamSetPreferences
import com.example.data.PaperEntity
import com.example.data.StudyNoteEntity
import com.example.data.effectiveCategory
import com.example.data.inferDefaultPaperCategory
import com.example.data.toQuestionPaper
import com.example.data.SampleData
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import com.example.data.ColorSchemePreset
import com.example.data.AppFontScaleMode
import com.example.model.AutoBackupSchedule
import com.example.model.AutoSaveStatus
import com.example.model.BackupOperationState
import com.example.model.BatchImportItem
import com.example.model.BatchImportResult
import com.example.model.BatchItemStatus
import com.example.model.CloudAccountStatus
import com.example.model.CloudProviderType
import com.example.model.ExamConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.model.FlashcardPromptConfig
import com.example.model.FlashcardSrsState
import com.example.model.FolderItem
import com.example.model.KnowledgeCollectionType
import com.example.model.KnowledgeFormatRule
import com.example.model.KnowledgePromptConfig
import com.example.model.LinkedCloudFileStatus
import com.example.model.HeadwordQueueItem
import com.example.model.HeadwordFilterResult
import com.example.model.McqQuestion
import com.example.model.MsqScoringPolicy
import com.example.model.PaperCategory
import com.example.model.PaperMetadata
import com.example.model.ParseResult
import com.example.model.PromptPreset
import com.example.model.PromptPresetMode
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import com.example.model.QuestionPromptConfig
import com.example.model.RemoteCloudBackupFile
import com.example.model.RestoreMode
import com.example.model.RestorePreview
import com.example.model.SrsRating
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyLanguageVariants
import com.example.model.VocabularyLevel
import com.example.model.VocabularyPromptConfig
import com.example.ui.components.computeNextSetForSubject
import com.example.ui.components.isSetNumberMatch
import com.example.ui.components.paperBelongsToDestination
import com.example.ui.components.resolveFolderBySubjectPath
import com.example.ui.screens.FlashcardPromptState
import com.example.ui.screens.KnowledgePromptState
import com.example.ui.screens.PromptBuilderMode
import com.example.ui.screens.QuestionPromptState
import com.example.ui.screens.VocabularyPromptState
import com.example.util.AnswerKeyMatcher
import com.example.util.CloudBackupClients
import com.example.util.McqParser
import com.example.util.BatchExamImporter
import com.example.util.ExamJsonHelper
import com.example.util.ExamNumberGenerator
import com.example.util.OmrVisionProcessor
import com.example.util.PaperLanguageDetector
import com.example.util.PsychometricEngine
import com.example.util.SpacedRepetitionEngine
import com.example.util.VocabularyAuditFinding
import com.example.util.VocabularyDuplicateAction
import com.example.util.VocabularyEngine
import com.example.util.VocabularyImportAnalysis
import com.example.util.VocabularyMergeDecision
import com.example.util.VocabularySmartRouteTargetChoice
import com.example.util.WordOfTheDayService
import com.example.util.WordOfTheDayUiState
import android.os.SystemClock
import com.example.data.AttemptPacingSummary
import com.example.data.QuestionPacingAnalyzer
import com.example.model.DueSuggestionCategory
import com.example.model.ExamCountdownTarget
import com.example.model.PomodoroDndMode
import com.example.model.PomodoroPhase
import com.example.model.PomodoroPreset
import com.example.model.StudyDateUtils
import com.example.model.StudyDueSuggestion
import com.example.model.StudyTodoItem
import com.example.model.TodoLinkType
import com.example.model.TodoPriority
import com.example.util.FocusPomodoroController
import com.example.util.PomodoroUiState
import com.example.util.StudyNoteBlock
import com.example.util.StudyNoteParser
import com.example.util.TrustedTimeManager
import com.example.util.VaultArchiveEngine
import com.example.util.VaultAutoBackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class KnowledgeEditorDraft(
    val note: StudyNoteEntity? = null,
    val defaultFolderId: String? = null,
    val defaultCourse: String = "",
    val defaultSubject: String = "",
    val defaultLinkedPaperId: Long? = null,
    val prefillTitle: String = "",
    val prefillContent: String = "",
    val defaultCollectionType: KnowledgeCollectionType = KnowledgeCollectionType.ONE_LINERS,
    val defaultFormatRule: KnowledgeFormatRule? = null
)

data class LibraryIndexState(
    val attemptsByPaperId: Map<Long, List<AttemptEntity>> = emptyMap(),
    val completedPaperIds: Set<Long> = emptySet(),
    val inProgressPaperIds: Set<Long> = emptySet(),
    val latestSubmittedAttemptByPaperId: Map<Long, AttemptEntity> = emptyMap(),
    val folderDirectCounts: Map<String, Int> = emptyMap(),
    val folderTotalCounts: Map<String, Int> = emptyMap(),
    val folderCompletedCounts: Map<String, Int> = emptyMap(),
    val folderMonthlyCounts: Map<String, Int> = emptyMap(),
    val folderNotesCounts: Map<String, Int> = emptyMap(),
    val folderLanguages: Map<String, List<String>> = emptyMap(),
    val paperLanguagesById: Map<Long, List<String>> = emptyMap(),
    val suggestedNextPaper: PaperEntity? = null,
    val attemptedPapers: List<PaperEntity> = emptyList(),
    val weakAccuracyPapers: List<PaperEntity> = emptyList(),
    val mockPapers: List<PaperEntity> = emptyList(),
    val pyqPapers: List<PaperEntity> = emptyList()
)

enum class ExamTimerMode {
    COUNTDOWN,
    STOPWATCH
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val paperDao = db.paperDao()
    private val studyNoteDao = db.studyNoteDao()
    private val backupSnapshotDao = db.backupSnapshotDao()
    private val themePreferences = ThemePreferences(application)
    private val appPreferences = ExamSetPreferences(application)
    private val cloudAuthStore = CloudAuthStore(application)

    // Dark Mode preference
    private val _themeMode = MutableStateFlow(themePreferences.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Color Scheme preset preference
    private val _colorSchemePreset = MutableStateFlow(themePreferences.getColorSchemePreset())
    val colorSchemePreset: StateFlow<ColorSchemePreset> = _colorSchemePreset.asStateFlow()

    // Question card layout style: true for rounded cards, false for borderless exam paper
    private val _isCardBoxed = MutableStateFlow(true)
    val isCardBoxed: StateFlow<Boolean> = _isCardBoxed.asStateFlow()

    val savedPapers: StateFlow<List<PaperEntity>> = paperDao.getAllPapers()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allAttempts: StateFlow<List<AttemptEntity>> = paperDao.getAllAttempts()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val studyNotes: StateFlow<List<StudyNoteEntity>> = studyNoteDao.getAllStudyNotes()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allDailyAnalytics: StateFlow<List<DailyStudyAnalyticsEntity>> = paperDao.getAllDailyAnalytics()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val backupSnapshots: StateFlow<List<BackupSnapshotEntity>> = backupSnapshotDao.getAllSnapshots()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _backupOpState = MutableStateFlow<BackupOperationState>(BackupOperationState.Idle)
    val backupOpState: StateFlow<BackupOperationState> = _backupOpState.asStateFlow()

    private val _restorePreview = MutableStateFlow<RestorePreview?>(null)
    val restorePreview: StateFlow<RestorePreview?> = _restorePreview.asStateFlow()

    private val _driveStatus = MutableStateFlow(cloudAuthStore.buildAccountStatus(CloudProviderType.GOOGLE_DRIVE))
    val driveStatus: StateFlow<CloudAccountStatus> = _driveStatus.asStateFlow()

    private val _oneDriveStatus = MutableStateFlow(cloudAuthStore.buildAccountStatus(CloudProviderType.ONEDRIVE))
    val oneDriveStatus: StateFlow<CloudAccountStatus> = _oneDriveStatus.asStateFlow()

    private val _linkedCloudFileStatus = MutableStateFlow(cloudAuthStore.getLinkedCloudFileStatus())
    val linkedCloudFileStatus: StateFlow<LinkedCloudFileStatus> = _linkedCloudFileStatus.asStateFlow()

    val isNetworkTimeSynced: StateFlow<Boolean> = TrustedTimeManager.isNetworkTimeSynced

    private val _remoteCloudBackups = MutableStateFlow<List<RemoteCloudBackupFile>>(emptyList())
    val remoteCloudBackups: StateFlow<List<RemoteCloudBackupFile>> = _remoteCloudBackups.asStateFlow()

    private val _autoBackupSchedule = MutableStateFlow(cloudAuthStore.getAutoBackupSchedule())
    val autoBackupSchedule: StateFlow<AutoBackupSchedule> = _autoBackupSchedule.asStateFlow()

    // Hoisted AI Prompt Builder state + saved presets (persisted safely in ExamSetPreferences)
    var promptBuilderMode by mutableStateOf(PromptBuilderMode.QUESTION_SET)

    private val _promptPresets = MutableStateFlow(appPreferences.getPromptPresets())
    val promptPresets: StateFlow<List<PromptPreset>> = _promptPresets.asStateFlow()

    var activeQuestionPresetId by mutableStateOf<String?>(
        appPreferences.getActivePromptPresetId(PromptPresetMode.QUESTION_SET)?.takeIf { savedId ->
            _promptPresets.value.any { it.id == savedId && it.mode == PromptPresetMode.QUESTION_SET }
        }
    )
    var activeKnowledgePresetId by mutableStateOf<String?>(
        appPreferences.getActivePromptPresetId(PromptPresetMode.KNOWLEDGE_SET)?.takeIf { savedId ->
            _promptPresets.value.any { it.id == savedId && it.mode == PromptPresetMode.KNOWLEDGE_SET }
        }
    )
    var activeFlashcardPresetId by mutableStateOf<String?>(
        appPreferences.getActivePromptPresetId(PromptPresetMode.FLASHCARD_SET)?.takeIf { savedId ->
            _promptPresets.value.any { it.id == savedId && it.mode == PromptPresetMode.FLASHCARD_SET }
        }
    )
    var activeVocabularyPresetId by mutableStateOf<String?>(
        appPreferences.getActivePromptPresetId(PromptPresetMode.VOCABULARY)?.takeIf { savedId ->
            _promptPresets.value.any { it.id == savedId && it.mode == PromptPresetMode.VOCABULARY }
        }
    )

    val questionPromptState = QuestionPromptState().apply {
        appPreferences.getQuestionPromptDraft()?.let { applyConfig(it, preserveTopic = false) }
    }
    val knowledgePromptState = KnowledgePromptState().apply {
        appPreferences.getKnowledgePromptDraft()?.let { applyConfig(it, preserveTopic = false) }
    }
    val flashcardPromptState = FlashcardPromptState().apply {
        appPreferences.getFlashcardPromptDraft()?.let { applyConfig(it, preserveTopic = false) }
    }
    val vocabularyPromptState = VocabularyPromptState().apply {
        val draft = appPreferences.getVocabularyPromptDraft()
        if (draft != null) {
            applyConfig(draft, preserveTopic = false)
        } else {
            languageVariant = appPreferences.getPreferredEnglishVariant()
        }
    }

    // Dedicated Vocabulary Hub state (completely separate from Knowledge Sets)
    private val _vocabularyDecks = MutableStateFlow(appPreferences.getVocabularyDecks())
    val vocabularyDecks: StateFlow<List<VocabularyDeck>> = _vocabularyDecks.asStateFlow()

    private val _vocabularySrsByDeck = MutableStateFlow(appPreferences.getAllVocabularySrsMaps())
    val vocabularySrsByDeck: StateFlow<Map<String, Map<String, FlashcardSrsState>>> = _vocabularySrsByDeck.asStateFlow()

    private val _preferredEnglishVariant = MutableStateFlow(appPreferences.getPreferredEnglishVariant())
    val preferredEnglishVariant: StateFlow<String> = _preferredEnglishVariant.asStateFlow()

    private val _vocabularyReminderEnabled = MutableStateFlow(appPreferences.isVocabularyReminderEnabled())
    val vocabularyReminderEnabled: StateFlow<Boolean> = _vocabularyReminderEnabled.asStateFlow()

    // Curated Headword Inbox & Deduplication State (Stage-1 Headword Pipeline)
    private val _headwordInbox = MutableStateFlow(appPreferences.getHeadwordInbox())
    val headwordInbox: StateFlow<List<HeadwordQueueItem>> = _headwordInbox.asStateFlow()

    private val _dismissedWordKeys = MutableStateFlow(appPreferences.getDismissedWordKeys())
    val dismissedWordKeys: StateFlow<Set<String>> = _dismissedWordKeys.asStateFlow()
    private val dismissedScopeByWordKey = mutableMapOf<String, Pair<String, String>>()

    private var activeWotdLanguage: String? = null
    private val _wordOfTheDayState = MutableStateFlow<WordOfTheDayUiState>(
        WordOfTheDayUiState.Hidden(language = "", currentUniqueWords = 0)
    )
    val wordOfTheDayState: StateFlow<WordOfTheDayUiState> = _wordOfTheDayState.asStateFlow()

    var showVocabularyHub by mutableStateOf(false)
        private set
    var vocabularyHubInitialLanguage by mutableStateOf<String?>(null)
        private set

    var showAppGuide by mutableStateOf(false)
        private set
    var showStudyPlanner by mutableStateOf(false)
        private set
    var showHeadwordInboxOverlay by mutableStateOf(false)
        private set
    var headwordInboxInitialLanguage by mutableStateOf<String?>(null)
        private set
    var showBrainLab by mutableStateOf(false)
        private set
    var brainLabInitialActivity by mutableStateOf<com.example.model.BrainLabActivity?>(null)
        private set
    var showVoicePractice by mutableStateOf(false)
        private set

    fun openVocabularyHub(languageFilter: String? = null) {
        val cleanLang = languageFilter?.trim()?.ifBlank { null }?.takeUnless { it.equals("All", ignoreCase = true) }
        vocabularyHubInitialLanguage = cleanLang
        showVocabularyHub = true
        loadWordOfTheDayForLanguage(cleanLang, cycleNext = false)
    }

    fun closeVocabularyHub() {
        showVocabularyHub = false
    }

    fun openAppGuide() {
        showAppGuide = true
    }

    fun closeAppGuide() {
        showAppGuide = false
    }

    fun openStudyPlanner() {
        focusPomodoroController.refreshDndAccessStatus()
        showStudyPlanner = true
    }

    fun closeStudyPlanner() {
        showStudyPlanner = false
    }

    fun openHeadwordInboxOverlay(languageFilter: String? = null) {
        headwordInboxInitialLanguage = languageFilter?.trim()?.ifBlank { null }
        showHeadwordInboxOverlay = true
    }

    fun closeHeadwordInboxOverlay() {
        showHeadwordInboxOverlay = false
    }

    fun openBrainLab(initialActivity: com.example.model.BrainLabActivity? = null) {
        brainLabInitialActivity = initialActivity
        showBrainLab = true
    }

    fun closeBrainLab() {
        showBrainLab = false
        brainLabInitialActivity = null
    }

    fun openVoicePractice() {
        showVoicePractice = false
        selectTab(7)
    }

    fun closeVoicePractice() {
        showVoicePractice = false
    }

    fun setVocabularyReminderEnabled(enabled: Boolean) {
        appPreferences.setVocabularyReminderEnabled(enabled)
        _vocabularyReminderEnabled.value = enabled
        com.example.util.VocabularyDailyReminderWorker.syncSchedule(getApplication(), enabled)
        _statusMessage.value = if (enabled) {
            "Daily vocabulary & Word of the Day reminder enabled"
        } else {
            "Daily vocabulary reminder turned off"
        }
    }

    fun onAppResumed() {
        com.example.util.TrustedTimeManager.syncWithNetworkAsync(viewModelScope)
        initTodayStudyAnalytics()
        refreshActiveWordOfTheDay()
        _countdownNowMillis.value = TrustedTimeManager.getTrustedTimeMillis()
        focusPomodoroController.restoreDndIfOrphaned()
        focusPomodoroController.refreshDndAccessStatus()
        syncExamTickerWithClock()
    }

    fun setPreferredEnglishVariant(variant: String) {
        val norm = VocabularyLanguageVariants.normalizeVariant("English", variant)
        if (_preferredEnglishVariant.value == norm) return
        appPreferences.setPreferredEnglishVariant(norm)
        _preferredEnglishVariant.value = norm
        if (VocabularyLanguageVariants.isEnglish(vocabularyPromptState.language)) {
            vocabularyPromptState.languageVariant = norm
            appPreferences.saveVocabularyPromptDraft(vocabularyPromptState.toConfig())
        }
        refreshActiveWordOfTheDay()
    }

    fun loadWordOfTheDayForLanguage(language: String?, cycleNext: Boolean = false) {
        val cleanLang = language?.trim()?.ifBlank { null }?.takeUnless { it.equals("All", ignoreCase = true) }
        activeWotdLanguage = cleanLang
        _wordOfTheDayState.value = WordOfTheDayService.resolveForLanguage(
            prefs = appPreferences,
            decks = _vocabularyDecks.value,
            language = cleanLang,
            preferredEnglishVariant = _preferredEnglishVariant.value,
            cycleNext = cycleNext
        )
    }

    private fun refreshActiveWordOfTheDay() {
        val lang = activeWotdLanguage
        if (lang == null) {
            _wordOfTheDayState.value = WordOfTheDayUiState.Hidden(language = "", currentUniqueWords = 0)
            return
        }
        _wordOfTheDayState.value = WordOfTheDayService.resolveForLanguage(
            prefs = appPreferences,
            decks = _vocabularyDecks.value,
            language = lang,
            preferredEnglishVariant = _preferredEnglishVariant.value,
            cycleNext = false
        )
    }

    fun existingVocabularyWordsForLanguage(language: String): List<String> {
        val clean = language.trim().lowercase().ifBlank { "english" }
        return _vocabularyDecks.value
            .filter { it.language.trim().lowercase() == clean }
            .flatMap { it.entries }
            .map { it.word.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    fun existingVocabularyAndQueuedWordsForLanguage(language: String): List<String> {
        val clean = language.trim().lowercase().ifBlank { "english" }
        val deckWords = existingVocabularyWordsForLanguage(language)
        val inboxWords = _headwordInbox.value
            .filter { it.language.trim().lowercase() == clean }
            .map { it.word.trim() }
            .filter { it.isNotBlank() }
        val dismissed = _dismissedWordKeys.value.filter { rawKey ->
            val norm = VocabularyEntry.normalizeVocabularyWordKey(rawKey)
            val scope = dismissedScopeByWordKey[norm]
            scope == null || scope.first == clean
        }
        return (deckWords + inboxWords + dismissed)
            .distinctBy { VocabularyEntry.normalizeVocabularyWordKey(it) }
            .sortedBy { it.lowercase() }
    }

    /**
     * Filters existing saved vocabulary, queued headwords, and level-tagged dismissed words
     * strictly to the requested (language, levelCode) pair unless [levelCode] is MIXED.
     */
    fun existingVocabularyAndQueuedWords(
        language: String,
        levelCode: String,
        customLevelLabel: String = ""
    ): List<String> {
        val targetLevel = VocabularyLevel.fromCode(levelCode)
        if (targetLevel == VocabularyLevel.MIXED) {
            return existingVocabularyAndQueuedWordsForLanguage(language)
        }
        val cleanLang = language.trim().lowercase().ifBlank { "english" }
        val cleanCustom = customLevelLabel.trim()

        val deckWords = _vocabularyDecks.value
            .filter { it.language.trim().lowercase() == cleanLang }
            .flatMap { deck ->
                deck.entries.filter { entry ->
                    val effectiveLevel = VocabularyLevel.fromCode(entry.levelCode.ifBlank { deck.levelCode })
                    if (effectiveLevel != targetLevel) return@filter false
                    if (targetLevel == VocabularyLevel.CUSTOM && cleanCustom.isNotBlank()) {
                        val effectiveCustom = entry.customLevelLabel.ifBlank { deck.customLevelLabel }.trim()
                        effectiveCustom.equals(cleanCustom, ignoreCase = true)
                    } else {
                        true
                    }
                }
            }
            .map { it.word.trim() }
            .filter { it.isNotBlank() }

        val inboxWords = _headwordInbox.value
            .filter { item ->
                if (item.language.trim().lowercase() != cleanLang) return@filter false
                if (item.resolvedLevel() != targetLevel) return@filter false
                if (targetLevel == VocabularyLevel.CUSTOM && cleanCustom.isNotBlank()) {
                    item.customLevelLabel.trim().equals(cleanCustom, ignoreCase = true)
                } else {
                    true
                }
            }
            .map { it.word.trim() }
            .filter { it.isNotBlank() }

        val scopedDismissed = _dismissedWordKeys.value.filter { rawKey ->
            val norm = VocabularyEntry.normalizeVocabularyWordKey(rawKey)
            val scope = dismissedScopeByWordKey[norm]
            scope != null && scope.first == cleanLang && scope.second == targetLevel.code
        }

        return (deckWords + inboxWords + scopedDismissed)
            .distinctBy {
                VocabularyEntry.canonicalSpellingMatchKey(it)
                    .ifBlank { VocabularyEntry.normalizeVocabularyWordKey(it) }
            }
            .sortedBy { it.lowercase() }
    }

    fun commitVocabularyImport(
        targetDeckId: String?,
        title: String,
        language: String,
        translationLanguage: String,
        levelCode: String,
        customLevelLabel: String,
        domain: String,
        analysis: VocabularyImportAnalysis,
        decisions: Map<String, VocabularyMergeDecision>,
        languageVariant: String = ""
    ): VocabularyDeck {
        val now = System.currentTimeMillis()
        val decksMutable = _vocabularyDecks.value.toMutableList()
        val cleanLang = language.trim().ifBlank { "English" }
        val safeLevel = VocabularyLevel.fromCode(levelCode)
            .takeUnless { it == VocabularyLevel.MIXED }
            ?: VocabularyLevel.INTERMEDIATE
        val safeCustom = if (safeLevel == VocabularyLevel.CUSTOM) customLevelLabel.trim() else ""
        val resolvedVariant = languageVariant.trim().ifBlank {
            if (VocabularyLanguageVariants.isEnglish(cleanLang)) _preferredEnglishVariant.value else ""
        }

        // Ensure target deck exists (consolidating into matching canonical level deck if no explicit target ID)
        val resolvedTargetId: String
        var targetIndex = if (targetDeckId != null) {
            decksMutable.indexOfFirst { it.id == targetDeckId }
        } else {
            decksMutable.indexOfFirst { deck ->
                deck.language.trim().equals(cleanLang, ignoreCase = true) &&
                    deck.resolvedLevel() == safeLevel &&
                    (safeLevel != VocabularyLevel.CUSTOM || deck.customLevelLabel.trim().equals(safeCustom, ignoreCase = true))
            }
        }
        if (targetIndex < 0) {
            val newDeck = VocabularyDeck(
                title = title.trim().ifBlank { "$cleanLang • ${safeLevel.displayTitle(safeCustom)}" },
                language = cleanLang,
                languageVariant = resolvedVariant,
                translationLanguage = translationLanguage.trim(),
                levelCode = safeLevel.code,
                customLevelLabel = safeCustom,
                domain = domain.trim().ifBlank { "General" },
                entries = emptyList(),
                createdAt = now,
                updatedAt = now
            )
            decksMutable.add(0, newDeck)
            targetIndex = 0
            resolvedTargetId = newDeck.id
        } else {
            resolvedTargetId = decksMutable[targetIndex].id
        }

        // 1. Add all unique new entries to target deck
        val newEntriesToAdd = analysis.uniqueNewEntries.toMutableList()

        // 2. Apply user's audit-style decisions for each duplicate item
        analysis.duplicateItems.forEach { dup ->
            val decision = decisions[dup.id] ?: VocabularyMergeDecision(action = VocabularyDuplicateAction.SKIP)
            when (decision.action) {
                VocabularyDuplicateAction.SKIP -> Unit
                VocabularyDuplicateAction.KEEP_SEPARATE -> {
                    newEntriesToAdd.add(dup.incoming.copy(id = java.util.UUID.randomUUID().toString(), createdAt = now, updatedAt = now))
                }
                VocabularyDuplicateAction.MERGE_SEGMENTS,
                VocabularyDuplicateAction.REPLACE_EXISTING -> {
                    if (dup.isIntraBatch) {
                        val batchIdx = newEntriesToAdd.indexOfFirst { it.id == dup.existing.id }
                        if (batchIdx >= 0) {
                            newEntriesToAdd[batchIdx] = VocabularyEngine.mergeVocabularyEntry(
                                existing = newEntriesToAdd[batchIdx],
                                incoming = dup.incoming,
                                decision = decision
                            )
                        }
                    } else {
                        val hostIdx = decksMutable.indexOfFirst { it.id == dup.existingDeckId }
                        if (hostIdx >= 0) {
                            val hostDeck = decksMutable[hostIdx]
                            val updatedEntries = hostDeck.entries.map { existingEntry ->
                                if (existingEntry.id == dup.existing.id) {
                                    VocabularyEngine.mergeVocabularyEntry(existingEntry, dup.incoming, decision)
                                } else existingEntry
                            }
                            decksMutable[hostIdx] = hostDeck.copy(entries = updatedEntries, updatedAt = now)
                        }
                    }
                }
            }
        }

        // Re-locate target index safely and append sorted unique entries
        val finalTargetIdx = decksMutable.indexOfFirst { it.id == resolvedTargetId }
        val updatedTargetDeck = decksMutable[finalTargetIdx].let { deck ->
            val combined = (deck.entries + newEntriesToAdd).sortedWith(
                compareBy<VocabularyEntry>(
                    { it.word.trim().lowercase() },
                    { it.partOfSpeech.trim().lowercase() }
                )
            )
            deck.copy(entries = combined, updatedAt = now)
        }
        decksMutable[finalTargetIdx] = updatedTargetDeck

        _vocabularyDecks.value = decksMutable
        appPreferences.saveVocabularyDecks(decksMutable)
        graduateHeadwordsForEntries(newEntriesToAdd + analysis.duplicateItems.map { it.incoming }, cleanLang)
        refreshActiveWordOfTheDay()
        _statusMessage.value = "Saved \"${updatedTargetDeck.title}\" (${updatedTargetDeck.entries.size} words)"
        return updatedTargetDeck
    }

    /**
     * Atomically routes parsed vocabulary across multiple `(language, level)` destination folders/sets
     * according to user-confirmed bucket targets while enforcing duplicate & merge decisions.
     */
    fun commitSmartRoutedVocabularyImport(
        targets: List<VocabularySmartRouteTargetChoice>,
        entryBucketById: Map<String, String>,
        analysis: VocabularyImportAnalysis,
        decisions: Map<String, VocabularyMergeDecision>
    ): List<VocabularyDeck> {
        if (targets.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val decksMutable = _vocabularyDecks.value.toMutableList()
        val deckIdByBucketKey = mutableMapOf<String, String>()
        val addedEntriesByDeckId = mutableMapOf<String, MutableList<VocabularyEntry>>()

        val targetByBucketKey = targets.associateBy { it.bucketKey }
        // 1. Resolve or consolidate every target bucket into its canonical (language, level) folder deck
        targets.forEach { target ->
            val cleanLang = target.language.trim().ifBlank { "English" }
            val safeLevel = VocabularyLevel.fromCode(target.levelCode)
                .takeUnless { it == VocabularyLevel.MIXED }
                ?: VocabularyLevel.INTERMEDIATE
            val safeCustom = if (safeLevel == VocabularyLevel.CUSTOM) target.customLevelLabel.trim() else ""

            val explicitIdx = target.targetDeckId?.let { id -> decksMutable.indexOfFirst { it.id == id } } ?: -1
            val existingIdx = if (explicitIdx >= 0) {
                explicitIdx
            } else {
                decksMutable.indexOfFirst { deck ->
                    deck.language.trim().equals(cleanLang, ignoreCase = true) &&
                        deck.resolvedLevel() == safeLevel &&
                        (safeLevel != VocabularyLevel.CUSTOM || deck.customLevelLabel.trim().equals(safeCustom, ignoreCase = true))
                }
            }
            val resolvedId = if (existingIdx >= 0) {
                val existingDeck = decksMutable[existingIdx]
                val cleanTrans = target.translationLanguage.trim()
                if (existingDeck.translationLanguage.isBlank() && cleanTrans.isNotBlank()) {
                    decksMutable[existingIdx] = existingDeck.copy(
                        translationLanguage = cleanTrans,
                        updatedAt = now
                    )
                }
                existingDeck.id
            } else {
                val newDeck = VocabularyDeck(
                    title = target.customTitle.trim().ifBlank { "$cleanLang • ${safeLevel.displayTitle(safeCustom)}" },
                    language = cleanLang,
                    languageVariant = target.languageVariant.trim(),
                    translationLanguage = target.translationLanguage.trim(),
                    levelCode = safeLevel.code,
                    customLevelLabel = safeCustom,
                    domain = target.domain.trim().ifBlank { "General" },
                    entries = emptyList(),
                    createdAt = now,
                    updatedAt = now
                )
                decksMutable.add(0, newDeck)
                newDeck.id
            }
            deckIdByBucketKey[target.bucketKey] = resolvedId
            addedEntriesByDeckId.getOrPut(resolvedId) { mutableListOf() }
        }

        val fallbackDeckId = deckIdByBucketKey.values.first()

        // 2. Route unique new entries into their respective bucket decks
        analysis.uniqueNewEntries.forEach { entry ->
            val bucketKey = entryBucketById[entry.id]
            val targetId = (bucketKey?.let { deckIdByBucketKey[it] }) ?: fallbackDeckId
            val targetChoice = bucketKey?.let { targetByBucketKey[it] }
            val stampedEntry = if (targetChoice != null) {
                entry.copy(
                    language = targetChoice.language.trim().ifBlank { entry.language },
                    languageVariant = targetChoice.languageVariant.trim().ifBlank { entry.languageVariant },
                    translationLanguage = targetChoice.translationLanguage.trim().ifBlank { entry.translationLanguage }
                )
            } else entry
            addedEntriesByDeckId.getOrPut(targetId) { mutableListOf() }.add(stampedEntry)
        }

        // 3. Apply duplicate/merge decisions across buckets & existing decks
        analysis.duplicateItems.forEach { dup ->
            val decision = decisions[dup.id] ?: VocabularyMergeDecision(action = VocabularyDuplicateAction.SKIP)
            when (decision.action) {
                VocabularyDuplicateAction.SKIP -> Unit
                VocabularyDuplicateAction.KEEP_SEPARATE -> {
                    val bucketKey = entryBucketById[dup.incoming.id]
                    val targetId = (bucketKey?.let { deckIdByBucketKey[it] }) ?: fallbackDeckId
                    addedEntriesByDeckId.getOrPut(targetId) { mutableListOf() }.add(
                        dup.incoming.copy(id = java.util.UUID.randomUUID().toString(), createdAt = now, updatedAt = now)
                    )
                }
                VocabularyDuplicateAction.MERGE_SEGMENTS,
                VocabularyDuplicateAction.REPLACE_EXISTING -> {
                    if (dup.isIntraBatch) {
                        var updatedInBatch = false
                        addedEntriesByDeckId.values.forEach { bucketList ->
                            val idx = bucketList.indexOfFirst { it.id == dup.existing.id }
                            if (idx >= 0) {
                                bucketList[idx] = VocabularyEngine.mergeVocabularyEntry(bucketList[idx], dup.incoming, decision)
                                updatedInBatch = true
                            }
                        }
                        if (!updatedInBatch) {
                            val hostIdx = decksMutable.indexOfFirst { it.id == dup.existingDeckId }
                            if (hostIdx >= 0) {
                                val hostDeck = decksMutable[hostIdx]
                                val updatedEntries = hostDeck.entries.map { existingEntry ->
                                    if (existingEntry.id == dup.existing.id) {
                                        VocabularyEngine.mergeVocabularyEntry(existingEntry, dup.incoming, decision)
                                    } else existingEntry
                                }
                                decksMutable[hostIdx] = hostDeck.copy(entries = updatedEntries, updatedAt = now)
                            }
                        }
                    } else {
                        val hostIdx = decksMutable.indexOfFirst { it.id == dup.existingDeckId }
                        if (hostIdx >= 0) {
                            val hostDeck = decksMutable[hostIdx]
                            val updatedEntries = hostDeck.entries.map { existingEntry ->
                                if (existingEntry.id == dup.existing.id) {
                                    VocabularyEngine.mergeVocabularyEntry(existingEntry, dup.incoming, decision)
                                } else existingEntry
                            }
                            decksMutable[hostIdx] = hostDeck.copy(entries = updatedEntries, updatedAt = now)
                        }
                    }
                }
            }
        }

        // 4. Sort and finalize every touched deck
        val touchedDecks = mutableListOf<VocabularyDeck>()
        val targetDeckIds = deckIdByBucketKey.values.toSet()
        for (i in decksMutable.indices) {
            val deck = decksMutable[i]
            val incomingList = addedEntriesByDeckId[deck.id].orEmpty()
            if (deck.id in targetDeckIds || incomingList.isNotEmpty()) {
                val combined = (deck.entries + incomingList).sortedWith(
                    compareBy<VocabularyEntry>(
                        { it.word.trim().lowercase() },
                        { it.partOfSpeech.trim().lowercase() }
                    )
                )
                val updated = deck.copy(entries = combined, updatedAt = now)
                decksMutable[i] = updated
                if (deck.id in targetDeckIds) touchedDecks.add(updated)
            }
        }

        _vocabularyDecks.value = decksMutable
        appPreferences.saveVocabularyDecks(decksMutable)
        val routedEntries = addedEntriesByDeckId.values.flatten() + analysis.duplicateItems.map { it.incoming }
        graduateHeadwordsForEntries(routedEntries, fallbackLanguage = "")
        refreshActiveWordOfTheDay()
        val totalAdded = addedEntriesByDeckId.values.sumOf { it.size }
        _statusMessage.value = "Routed $totalAdded word(s) across ${touchedDecks.size} set(s)"
        return touchedDecks
    }

    fun renameVocabularyDeck(
        deckId: String,
        newTitle: String,
        language: String,
        translationLanguage: String,
        levelCode: String,
        customLevelLabel: String,
        languageVariant: String = ""
    ) {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isBlank()) return
        val now = System.currentTimeMillis()
        val updated = _vocabularyDecks.value.map { deck ->
            if (deck.id == deckId) {
                val cleanLang = language.trim().ifBlank { deck.language }
                deck.copy(
                    title = cleanTitle,
                    language = cleanLang,
                    languageVariant = languageVariant.trim(),
                    translationLanguage = translationLanguage.trim(),
                    levelCode = levelCode,
                    customLevelLabel = customLevelLabel.trim(),
                    updatedAt = now
                )
            } else deck
        }
        _vocabularyDecks.value = updated
        appPreferences.saveVocabularyDecks(updated)
        refreshActiveWordOfTheDay()
    }

    fun deleteVocabularyDeck(deckId: String) {
        val updated = _vocabularyDecks.value.filterNot { it.id == deckId }
        _vocabularyDecks.value = updated
        appPreferences.saveVocabularyDecks(updated)
        val updatedSrs = _vocabularySrsByDeck.value.toMutableMap().apply { remove(deckId) }
        _vocabularySrsByDeck.value = updatedSrs
        appPreferences.saveAllVocabularySrsMaps(updatedSrs)
        refreshActiveWordOfTheDay()
    }

    fun toggleVocabularyEntryBookmark(deckId: String, entryId: String) {
        val updated = _vocabularyDecks.value.map { deck ->
            if (deck.id != deckId) deck else deck.copy(
                entries = deck.entries.map { e ->
                    if (e.id == entryId) e.copy(isBookmarked = !e.isBookmarked) else e
                },
                updatedAt = System.currentTimeMillis()
            )
        }
        _vocabularyDecks.value = updated
        appPreferences.saveVocabularyDecks(updated)
    }

    fun deleteVocabularyEntry(deckId: String, entryId: String) {
        val now = System.currentTimeMillis()
        val updated = _vocabularyDecks.value.mapNotNull { deck ->
            if (deck.id != deckId) {
                deck
            } else {
                val remaining = deck.entries.filterNot { it.id == entryId }
                if (remaining.isEmpty()) null else deck.copy(entries = remaining, updatedAt = now)
            }
        }
        _vocabularyDecks.value = updated
        appPreferences.saveVocabularyDecks(updated)
        val deckStillExists = updated.any { it.id == deckId }
        val allSrs = _vocabularySrsByDeck.value.toMutableMap()
        if (!deckStillExists) {
            if (allSrs.remove(deckId) != null) {
                _vocabularySrsByDeck.value = allSrs
                appPreferences.saveAllVocabularySrsMaps(allSrs)
            }
        } else {
            val deckSrs = allSrs[deckId]?.toMutableMap()
            if (deckSrs != null && deckSrs.remove(entryId) != null) {
                if (deckSrs.isEmpty()) allSrs.remove(deckId) else allSrs[deckId] = deckSrs
                _vocabularySrsByDeck.value = allSrs
                appPreferences.saveAllVocabularySrsMaps(allSrs)
            }
        }
        refreshActiveWordOfTheDay()
    }

    fun rateVocabularyEntry(deckId: String, entryId: String, rating: SrsRating) {
        val now = com.example.util.TrustedTimeManager.getTrustedTimeMillis()
        val allSrs = _vocabularySrsByDeck.value.toMutableMap()
        val deckSrs = (allSrs[deckId] ?: emptyMap()).toMutableMap()
        val nextState = SpacedRepetitionEngine.computeNextState(
            current = deckSrs[entryId],
            blockIndex = 0,
            rating = rating,
            nowMillis = now
        )
        deckSrs[entryId] = nextState
        allSrs[deckId] = deckSrs
        _vocabularySrsByDeck.value = allSrs
        appPreferences.saveAllVocabularySrsMaps(allSrs)
    }

    fun resetVocabularyDeckSrs(deckId: String) {
        val allSrs = _vocabularySrsByDeck.value.toMutableMap()
        allSrs.remove(deckId)
        _vocabularySrsByDeck.value = allSrs
        appPreferences.saveAllVocabularySrsMaps(allSrs)
    }

    fun resetVocabularyLevelSrs(
        language: String,
        levelCode: String,
        customLevelLabel: String = ""
    ) {
        val cleanLang = language.trim().ifBlank { "English" }
        val targetLevel = VocabularyLevel.fromCode(levelCode)
            .takeUnless { it == VocabularyLevel.MIXED }
            ?: VocabularyLevel.INTERMEDIATE
        val cleanCustom = if (targetLevel == VocabularyLevel.CUSTOM) customLevelLabel.trim() else ""

        val allSrs = _vocabularySrsByDeck.value.toMutableMap()
        var changed = false
        _vocabularyDecks.value
            .filter { it.language.trim().ifBlank { "English" }.equals(cleanLang, ignoreCase = true) }
            .forEach { deck ->
                val deckSrs = allSrs[deck.id]?.toMutableMap() ?: return@forEach
                val matchingIds = deck.entries.filter { entry ->
                    val (entryLvl, entryCustom) = VocabularyEngine.resolveEffectiveEntryLevel(entry, deck)
                    if (targetLevel == VocabularyLevel.CUSTOM) {
                        entryLvl == VocabularyLevel.CUSTOM && entryCustom.equals(cleanCustom, ignoreCase = true)
                    } else {
                        entryLvl == targetLevel
                    }
                }.map { it.id }
                matchingIds.forEach { id ->
                    if (deckSrs.remove(id) != null) changed = true
                }
                if (deckSrs.isEmpty()) allSrs.remove(deck.id) else allSrs[deck.id] = deckSrs
            }
        if (changed) {
            _vocabularySrsByDeck.value = allSrs
            appPreferences.saveAllVocabularySrsMaps(allSrs)
        }
        _statusMessage.value = "Reset spaced repetition progress for $cleanLang • ${targetLevel.displayTitle(cleanCustom)}"
    }

    fun deleteVocabularyLevelFolder(
        language: String,
        levelCode: String,
        customLevelLabel: String = ""
    ) {
        val cleanLang = language.trim().ifBlank { "English" }
        val targetLevel = VocabularyLevel.fromCode(levelCode)
            .takeUnless { it == VocabularyLevel.MIXED }
            ?: VocabularyLevel.INTERMEDIATE
        val cleanCustom = if (targetLevel == VocabularyLevel.CUSTOM) customLevelLabel.trim() else ""
        val now = System.currentTimeMillis()
        val allSrs = _vocabularySrsByDeck.value.toMutableMap()
        var removedCount = 0

        val updatedDecks = _vocabularyDecks.value.mapNotNull { deck ->
            if (!deck.language.trim().ifBlank { "English" }.equals(cleanLang, ignoreCase = true)) {
                deck
            } else {
                val removedIds = mutableListOf<String>()
                val kept = deck.entries.filter { entry ->
                    val (entryLvl, entryCustom) = VocabularyEngine.resolveEffectiveEntryLevel(entry, deck)
                    val matches = if (targetLevel == VocabularyLevel.CUSTOM) {
                        entryLvl == VocabularyLevel.CUSTOM && entryCustom.equals(cleanCustom, ignoreCase = true)
                    } else {
                        entryLvl == targetLevel
                    }
                    if (matches) {
                        removedIds.add(entry.id)
                        false
                    } else {
                        true
                    }
                }
                if (removedIds.isEmpty()) {
                    deck
                } else {
                    removedCount += removedIds.size
                    val deckSrs = allSrs[deck.id]?.toMutableMap()
                    if (deckSrs != null) {
                        removedIds.forEach { deckSrs.remove(it) }
                        if (deckSrs.isEmpty()) allSrs.remove(deck.id) else allSrs[deck.id] = deckSrs
                    }
                    if (kept.isEmpty()) null else deck.copy(entries = kept, updatedAt = now)
                }
            }
        }

        if (removedCount > 0) {
            _vocabularyDecks.value = updatedDecks
            appPreferences.saveVocabularyDecks(updatedDecks)
            _vocabularySrsByDeck.value = allSrs
            appPreferences.saveAllVocabularySrsMaps(allSrs)
            refreshActiveWordOfTheDay()
            _statusMessage.value = "Removed $removedCount word(s) from $cleanLang • ${targetLevel.displayTitle(cleanCustom)}"
        }
    }

    fun resetVocabularyLanguageSrs(language: String) {
        val cleanLang = language.trim().ifBlank { "English" }
        val langDeckIds = _vocabularyDecks.value
            .filter { it.language.trim().ifBlank { "English" }.equals(cleanLang, ignoreCase = true) }
            .map { it.id }
            .toSet()
        if (langDeckIds.isEmpty()) return
        val allSrs = _vocabularySrsByDeck.value.toMutableMap()
        langDeckIds.forEach { allSrs.remove(it) }
        _vocabularySrsByDeck.value = allSrs
        appPreferences.saveAllVocabularySrsMaps(allSrs)
        _statusMessage.value = "Reset spaced repetition progress for $cleanLang"
    }

    fun applyVocabularyAuditFixes(deckId: String, findings: List<VocabularyAuditFinding>) {
        if (findings.isEmpty()) return
        val findingByEntryId = findings.associateBy { it.entryId }
        val now = System.currentTimeMillis()
        val updated = _vocabularyDecks.value.map { deck ->
            if (deck.id != deckId) deck else deck.copy(
                entries = deck.entries.map { entry ->
                    val f = findingByEntryId[entry.id]
                    if (f != null) VocabularyEngine.applyAuditFinding(entry, f) else entry
                },
                updatedAt = now
            )
        }
        _vocabularyDecks.value = updated
        appPreferences.saveVocabularyDecks(updated)
        refreshActiveWordOfTheDay()
        _statusMessage.value = "Applied ${findings.size} vocabulary audit fix(es)"
    }

    fun applyMixedVocabularyAuditFixes(
        originDeckIdByEntryId: Map<String, String>,
        findings: List<VocabularyAuditFinding>
    ) {
        if (findings.isEmpty()) return
        val findingsByDeck = findings.groupBy { finding ->
            originDeckIdByEntryId[finding.entryId] ?: ""
        }.filterKeys { it.isNotBlank() }
        if (findingsByDeck.isEmpty()) return
        val now = System.currentTimeMillis()
        val updated = _vocabularyDecks.value.map { deck ->
            val deckFindings = findingsByDeck[deck.id] ?: return@map deck
            val findingByEntryId = deckFindings.associateBy { it.entryId }
            deck.copy(
                entries = deck.entries.map { entry ->
                    val f = findingByEntryId[entry.id]
                    if (f != null) VocabularyEngine.applyAuditFinding(entry, f) else entry
                },
                updatedAt = now
            )
        }
        _vocabularyDecks.value = updated
        appPreferences.saveVocabularyDecks(updated)
        refreshActiveWordOfTheDay()
        _statusMessage.value = "Applied ${findings.size} vocabulary audit fix(es)"
    }

    fun importHeadwordsJson(
        rawJson: String,
        defaultLanguage: String = "English",
        requiredLevelCode: String? = null,
        requiredCustomLevelLabel: String = ""
    ): HeadwordFilterResult {
        val parsed = VocabularyEngine.parseHeadwordsJson(rawJson, defaultLanguage)
        val filterResult = VocabularyEngine.filterNewHeadwords(
            incoming = parsed,
            existingDecks = _vocabularyDecks.value,
            existingQueue = _headwordInbox.value,
            dismissedKeys = _dismissedWordKeys.value,
            requiredLevelCode = requiredLevelCode,
            requiredCustomLevelLabel = requiredCustomLevelLabel
        )
        if (filterResult.addedItems.isNotEmpty()) {
            val updatedQueue = _headwordInbox.value + filterResult.addedItems
            _headwordInbox.value = updatedQueue
            appPreferences.saveHeadwordInbox(updatedQueue)
        }
        val skippedTotal = filterResult.skippedAlreadyInDeckCount +
            filterResult.skippedAlreadyInQueueCount +
            filterResult.skippedDismissedCount +
            filterResult.skippedOffLevelCount
        val msg = if (filterResult.addedItems.isNotEmpty()) {
            "Added ${filterResult.addedItems.size} headword(s) to inbox" +
                if (skippedTotal > 0) " (Skipped $skippedTotal duplicate/off-level/dismissed)" else ""
        } else {
            "No new headwords added" +
                if (skippedTotal > 0) " (Skipped $skippedTotal duplicate/off-level/dismissed)" else ""
        }
        _statusMessage.value = msg
        return filterResult
    }

    fun addQuickHeadwordsToInbox(
        rawInput: String,
        defaultLanguage: String = "English",
        defaultLevelCode: String = "B2"
    ): HeadwordFilterResult {
        val parsed = VocabularyEngine.parseQuickCaptureHeadwords(
            rawInput = rawInput,
            defaultLanguage = defaultLanguage,
            defaultLevelCode = defaultLevelCode
        )
        val filterResult = VocabularyEngine.filterNewHeadwords(
            incoming = parsed,
            existingDecks = _vocabularyDecks.value,
            existingQueue = _headwordInbox.value,
            dismissedKeys = _dismissedWordKeys.value
        )
        if (filterResult.addedItems.isNotEmpty()) {
            val updatedQueue = _headwordInbox.value + filterResult.addedItems
            _headwordInbox.value = updatedQueue
            appPreferences.saveHeadwordInbox(updatedQueue)
        }
        val skippedTotal = filterResult.skippedAlreadyInDeckCount +
            filterResult.skippedAlreadyInQueueCount +
            filterResult.skippedDismissedCount
        _statusMessage.value = if (filterResult.addedItems.isNotEmpty()) {
            "Stored ${filterResult.addedItems.size} headword(s) silently in inbox" +
                if (skippedTotal > 0) " (Skipped $skippedTotal duplicate/saved/ignored)" else ""
        } else {
            "No new headwords stored" +
                if (skippedTotal > 0) " (Skipped $skippedTotal duplicate/saved/ignored)" else ""
        }
        return filterResult
    }

    fun dismissHeadword(queueItemId: String) {
        val item = _headwordInbox.value.firstOrNull { it.id == queueItemId } ?: return
        val norm = item.normalizedWordKey()
        dismissedScopeByWordKey[norm] = item.language.trim().lowercase().ifBlank { "english" } to item.resolvedLevel().code
        val updatedQueue = _headwordInbox.value.filterNot { it.id == queueItemId }
        _headwordInbox.value = updatedQueue
        appPreferences.saveHeadwordInbox(updatedQueue)

        val updatedDismissed = _dismissedWordKeys.value + norm
        _dismissedWordKeys.value = updatedDismissed
        appPreferences.saveDismissedWordKeys(updatedDismissed)

        _statusMessage.value = "Dismissed '${item.word}' (will not be suggested again)"
    }

    fun removeHeadwordFromInbox(queueItemId: String) {
        val updatedQueue = _headwordInbox.value.filterNot { it.id == queueItemId }
        _headwordInbox.value = updatedQueue
        appPreferences.saveHeadwordInbox(updatedQueue)
        _statusMessage.value = "Removed headword from inbox"
    }

    fun clearHeadwordInbox() {
        _headwordInbox.value = emptyList()
        appPreferences.saveHeadwordInbox(emptyList())
        _statusMessage.value = "Headword inbox cleared"
    }

    fun generateEnrichmentPromptForBatch(
        language: String,
        maxCount: Int = 15,
        translationLanguage: String = vocabularyPromptState.translationLanguage,
        levelCodeFilter: String? = null,
        categoryFilter: String? = null
    ): String {
        val cleanLevel = levelCodeFilter?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("All", ignoreCase = true)
        }
        val cleanCategory = categoryFilter?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("All", ignoreCase = true)
        }
        val langItems = _headwordInbox.value.filter { item ->
            val matchLang = item.language.equals(language, ignoreCase = true)
            val matchLevel = cleanLevel == null || item.resolvedLevel().code.equals(cleanLevel, ignoreCase = true)
            val matchCat = cleanCategory == null || item.domain.equals(cleanCategory, ignoreCase = true)
            matchLang && matchLevel && matchCat
        }
        val batch = langItems.take(maxCount)
        return VocabularyEngine.buildStage2EnrichmentPrompt(
            items = batch,
            targetLanguage = language,
            nativeExplanationLanguage = translationLanguage,
            preferredVariant = _preferredEnglishVariant.value
        )
    }

    fun importEnrichedStage2CardsFromInbox(
        rawInput: String,
        defaultLanguage: String = "English"
    ): Int {
        val cleanLang = defaultLanguage.trim().ifBlank { "English" }
        val parsed = VocabularyEngine.parseVocabularyInput(
            rawInput = rawInput,
            preferredEnglishVariant = _preferredEnglishVariant.value
        )
        if (parsed.entries.isEmpty()) {
            _statusMessage.value = parsed.warnings.firstOrNull()
                ?: "No complete vocabulary cards found. Ensure meanings are included."
            return 0
        }

        val stampedEntries = parsed.entries.map { entry ->
            val resolvedLang = entry.language.trim().ifBlank { cleanLang }
            val resolvedVariant = entry.languageVariant.trim().ifBlank {
                if (VocabularyLanguageVariants.isEnglish(resolvedLang)) _preferredEnglishVariant.value else ""
            }
            entry.copy(language = resolvedLang, languageVariant = resolvedVariant)
        }
        val stampedParse = parsed.copy(entries = stampedEntries)
        val buckets = VocabularyEngine.buildSmartRoutingPlan(
            parseResult = stampedParse,
            allDecks = _vocabularyDecks.value,
            preferredEnglishVariant = _preferredEnglishVariant.value
        )
        val targets = buckets.map { bucket ->
            VocabularySmartRouteTargetChoice(
                bucketKey = bucket.bucketKey,
                targetDeckId = bucket.recommendedDeckId,
                customTitle = bucket.defaultNewTitle,
                language = bucket.language,
                languageVariant = bucket.languageVariant,
                translationLanguage = bucket.translationLanguage,
                levelCode = bucket.levelCode,
                customLevelLabel = bucket.customLevelLabel
            )
        }
        val routeAnalysis = VocabularyEngine.analyzeSmartRoutedImport(
            parseResult = stampedParse,
            buckets = buckets,
            choices = targets.associateBy { it.bucketKey },
            allDecks = _vocabularyDecks.value,
            checkAcrossSameLanguage = true
        )
        val autoMergeDecisions = routeAnalysis.analysis.duplicateItems.associate { dup ->
            dup.id to VocabularyMergeDecision(
                action = VocabularyDuplicateAction.MERGE_SEGMENTS,
                mergeExamples = true,
                mergeTranslation = true,
                mergeSynonymsAndCollocations = true,
                mergeMeaningAndNote = true,
                mergeLevelAndMeta = true
            )
        }
        commitSmartRoutedVocabularyImport(
            targets = targets,
            entryBucketById = routeAnalysis.entryBucketById,
            analysis = routeAnalysis.analysis,
            decisions = autoMergeDecisions
        )
        val graduatedCount = stampedEntries.size
        _statusMessage.value = "Enriched & graduated $graduatedCount word(s) into Vocabulary Hub"
        return graduatedCount
    }

    private fun graduateHeadwordsForEntries(
        entries: List<VocabularyEntry>,
        fallbackLanguage: String
    ) {
        if (_headwordInbox.value.isEmpty() || entries.isEmpty()) return
        val completedPairs = entries.mapNotNull { entry ->
            val key = entry.normalizedWordKey()
            if (key.isBlank() || (entry.meaning.isBlank() && entry.translation.isBlank())) {
                null
            } else {
                val langKey = entry.language.trim().ifBlank { fallbackLanguage.trim().ifBlank { "English" } }
                    .lowercase()
                langKey to key
            }
        }.toSet()
        if (completedPairs.isEmpty()) return

        val remaining = _headwordInbox.value.filterNot { item ->
            val itemKey = item.normalizedWordKey()
            val itemLang = item.language.trim().ifBlank { "English" }.lowercase()
            (itemLang to itemKey) in completedPairs
        }
        if (remaining.size != _headwordInbox.value.size) {
            _headwordInbox.value = remaining
            appPreferences.saveHeadwordInbox(remaining)
        }
    }

    fun promoteEnrichedEntryToDeck(entry: VocabularyEntry, queueItemId: String? = null) {
        val (targetDeck, updatedDecks) = VocabularyEngine.findOrCreateTargetDeck(
            currentDecks = _vocabularyDecks.value,
            language = entry.language,
            levelCode = entry.levelCode,
            customLevelLabel = entry.customLevelLabel,
            variant = entry.languageVariant
        )
        val withEntry = targetDeck.copy(
            entries = (targetDeck.entries + entry).sortedWith(
                compareBy<VocabularyEntry>(
                    { it.word.trim().lowercase() },
                    { it.partOfSpeech.trim().lowercase() }
                )
            ),
            updatedAt = TrustedTimeManager.getTrustedTimeMillis()
        )
        val finalDecks = updatedDecks.map { if (it.id == withEntry.id) withEntry else it }
        _vocabularyDecks.value = finalDecks
        appPreferences.saveVocabularyDecks(finalDecks)

        if (!queueItemId.isNullOrBlank()) {
            removeHeadwordFromInbox(queueItemId)
        } else {
            graduateHeadwordsForEntries(listOf(entry), entry.language)
        }
        refreshActiveWordOfTheDay()
    }

    fun getActivePromptPreset(mode: PromptPresetMode): PromptPreset? {
        val id = when (mode) {
            PromptPresetMode.QUESTION_SET -> activeQuestionPresetId
            PromptPresetMode.KNOWLEDGE_SET -> activeKnowledgePresetId
            PromptPresetMode.FLASHCARD_SET -> activeFlashcardPresetId
            PromptPresetMode.VOCABULARY -> activeVocabularyPresetId
        } ?: return null
        return _promptPresets.value.firstOrNull { it.id == id && it.mode == mode }
    }

    fun setActivePromptPresetId(mode: PromptPresetMode, presetId: String?) {
        when (mode) {
            PromptPresetMode.QUESTION_SET -> activeQuestionPresetId = presetId
            PromptPresetMode.KNOWLEDGE_SET -> activeKnowledgePresetId = presetId
            PromptPresetMode.FLASHCARD_SET -> activeFlashcardPresetId = presetId
            PromptPresetMode.VOCABULARY -> activeVocabularyPresetId = presetId
        }
        appPreferences.setActivePromptPresetId(mode, presetId)
    }

    fun persistPromptDraft(mode: PromptPresetMode) {
        when (mode) {
            PromptPresetMode.QUESTION_SET -> appPreferences.saveQuestionPromptDraft(questionPromptState.toConfig())
            PromptPresetMode.KNOWLEDGE_SET -> appPreferences.saveKnowledgePromptDraft(knowledgePromptState.toConfig())
            PromptPresetMode.FLASHCARD_SET -> appPreferences.saveFlashcardPromptDraft(flashcardPromptState.toConfig())
            PromptPresetMode.VOCABULARY -> appPreferences.saveVocabularyPromptDraft(vocabularyPromptState.toConfig())
        }
    }

    fun selectPromptPreset(preset: PromptPreset, applyToMainBuilder: Boolean = true) {
        val now = System.currentTimeMillis()
        val updatedList = _promptPresets.value.map {
            if (it.id == preset.id) it.copy(lastUsedAt = now) else it
        }
        _promptPresets.value = updatedList
        appPreferences.savePromptPresets(updatedList)
        setActivePromptPresetId(preset.mode, preset.id)
        if (applyToMainBuilder) {
            when (preset.mode) {
                PromptPresetMode.QUESTION_SET -> preset.questionConfig?.let {
                    questionPromptState.applyConfig(it, preserveTopic = true)
                    appPreferences.saveQuestionPromptDraft(questionPromptState.toConfig())
                }
                PromptPresetMode.KNOWLEDGE_SET -> preset.knowledgeConfig?.let {
                    knowledgePromptState.applyConfig(it, preserveTopic = true)
                    appPreferences.saveKnowledgePromptDraft(knowledgePromptState.toConfig())
                }
                PromptPresetMode.FLASHCARD_SET -> preset.flashcardConfig?.let {
                    flashcardPromptState.applyConfig(it, preserveTopic = true)
                    appPreferences.saveFlashcardPromptDraft(flashcardPromptState.toConfig())
                }
                PromptPresetMode.VOCABULARY -> preset.vocabularyConfig?.let {
                    vocabularyPromptState.applyConfig(it, preserveTopic = true)
                    appPreferences.saveVocabularyPromptDraft(vocabularyPromptState.toConfig())
                }
            }
        }
    }

    fun saveNewPromptPreset(
        mode: PromptPresetMode,
        rawName: String,
        questionConfig: QuestionPromptConfig? = null,
        knowledgeConfig: KnowledgePromptConfig? = null,
        flashcardConfig: FlashcardPromptConfig? = null,
        vocabularyConfig: VocabularyPromptConfig? = null
    ): PromptPreset {
        val existingNames = _promptPresets.value.filter { it.mode == mode }.map { it.name }
        val resolvedName = PromptPreset.uniqueName(rawName, existingNames)
        val now = System.currentTimeMillis()
        val newPreset = PromptPreset(
            name = resolvedName,
            mode = mode,
            createdAt = now,
            updatedAt = now,
            lastUsedAt = now,
            questionConfig = if (mode == PromptPresetMode.QUESTION_SET) {
                (questionConfig ?: questionPromptState.toConfig()).normalized()
            } else null,
            knowledgeConfig = if (mode == PromptPresetMode.KNOWLEDGE_SET) {
                (knowledgeConfig ?: knowledgePromptState.toConfig()).normalized()
            } else null,
            flashcardConfig = if (mode == PromptPresetMode.FLASHCARD_SET) {
                (flashcardConfig ?: flashcardPromptState.toConfig()).normalized()
            } else null,
            vocabularyConfig = if (mode == PromptPresetMode.VOCABULARY) {
                (vocabularyConfig ?: vocabularyPromptState.toConfig()).normalized()
            } else null
        )
        val updated = _promptPresets.value + newPreset
        _promptPresets.value = updated
        appPreferences.savePromptPresets(updated)
        setActivePromptPresetId(mode, newPreset.id)
        return newPreset
    }

    fun updatePromptPreset(
        presetId: String,
        newName: String? = null,
        questionConfig: QuestionPromptConfig? = null,
        knowledgeConfig: KnowledgePromptConfig? = null,
        flashcardConfig: FlashcardPromptConfig? = null,
        vocabularyConfig: VocabularyPromptConfig? = null
    ) {
        val now = System.currentTimeMillis()
        var targetMode: PromptPresetMode? = null
        val updated = _promptPresets.value.map { existing ->
            if (existing.id != presetId) {
                existing
            } else {
                targetMode = existing.mode
                existing.copy(
                    name = newName?.trim()?.takeIf { it.isNotBlank() } ?: existing.name,
                    updatedAt = now,
                    lastUsedAt = now,
                    questionConfig = if (existing.mode == PromptPresetMode.QUESTION_SET) {
                        (questionConfig ?: existing.questionConfig ?: questionPromptState.toConfig()).normalized()
                    } else existing.questionConfig,
                    knowledgeConfig = if (existing.mode == PromptPresetMode.KNOWLEDGE_SET) {
                        (knowledgeConfig ?: existing.knowledgeConfig ?: knowledgePromptState.toConfig()).normalized()
                    } else existing.knowledgeConfig,
                    flashcardConfig = if (existing.mode == PromptPresetMode.FLASHCARD_SET) {
                        (flashcardConfig ?: existing.flashcardConfig ?: flashcardPromptState.toConfig()).normalized()
                    } else existing.flashcardConfig,
                    vocabularyConfig = if (existing.mode == PromptPresetMode.VOCABULARY) {
                        (vocabularyConfig ?: existing.vocabularyConfig ?: vocabularyPromptState.toConfig()).normalized()
                    } else existing.vocabularyConfig
                )
            }
        }
        _promptPresets.value = updated
        appPreferences.savePromptPresets(updated)
        targetMode?.let { setActivePromptPresetId(it, presetId) }
    }

    fun renamePromptPreset(presetId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        val now = System.currentTimeMillis()
        val updated = _promptPresets.value.map { preset ->
            if (preset.id == presetId) preset.copy(name = clean, updatedAt = now) else preset
        }
        _promptPresets.value = updated
        appPreferences.savePromptPresets(updated)
    }

    fun duplicatePromptPreset(presetId: String): PromptPreset? {
        val source = _promptPresets.value.firstOrNull { it.id == presetId } ?: return null
        val existingNames = _promptPresets.value.filter { it.mode == source.mode }.map { it.name }
        val copyName = PromptPreset.uniqueName(source.name, existingNames, suffixWord = "Copy")
        val now = System.currentTimeMillis()
        val duplicated = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = copyName,
            isPinned = false,
            createdAt = now,
            updatedAt = now,
            lastUsedAt = now
        )
        val updated = _promptPresets.value + duplicated
        _promptPresets.value = updated
        appPreferences.savePromptPresets(updated)
        return duplicated
    }

    fun togglePinPromptPreset(presetId: String) {
        val updated = _promptPresets.value.map { preset ->
            if (preset.id == presetId) preset.copy(isPinned = !preset.isPinned) else preset
        }
        _promptPresets.value = updated
        appPreferences.savePromptPresets(updated)
    }

    fun deletePromptPreset(presetId: String) {
        val target = _promptPresets.value.firstOrNull { it.id == presetId } ?: return
        val updated = _promptPresets.value.filterNot { it.id == presetId }
        _promptPresets.value = updated
        appPreferences.savePromptPresets(updated)
        if (getActivePromptPreset(target.mode) == null) {
            setActivePromptPresetId(target.mode, null)
        }
    }

    fun clearPromptModeState(mode: PromptPresetMode) {
        setActivePromptPresetId(mode, null)
        when (mode) {
            PromptPresetMode.QUESTION_SET -> {
                questionPromptState.reset()
                appPreferences.clearQuestionPromptDraft()
            }
            PromptPresetMode.KNOWLEDGE_SET -> {
                knowledgePromptState.reset()
                appPreferences.clearKnowledgePromptDraft()
            }
            PromptPresetMode.FLASHCARD_SET -> {
                flashcardPromptState.reset()
                appPreferences.clearFlashcardPromptDraft()
            }
            PromptPresetMode.VOCABULARY -> {
                vocabularyPromptState.reset()
                appPreferences.clearVocabularyPromptDraft()
            }
        }
    }

    // Spaced Repetition (SM-2) progress keyed by StudyNoteEntity.id -> blockIndex
    private val _flashcardSrsByNote = MutableStateFlow(appPreferences.getAllFlashcardSrsMaps())
    val flashcardSrsByNote: StateFlow<Map<Long, Map<Int, FlashcardSrsState>>> = _flashcardSrsByNote.asStateFlow()

    // Full-screen Knowledge Sets Studio navigation states (rendered outside Dialog windows so IME/insets never clip)
    private val _knowledgeEditorDraft = MutableStateFlow<KnowledgeEditorDraft?>(null)
    val knowledgeEditorDraft: StateFlow<KnowledgeEditorDraft?> = _knowledgeEditorDraft.asStateFlow()

    private val _activeKnowledgeReaderNoteId = MutableStateFlow<Long?>(null)
    val activeKnowledgeReaderNoteId: StateFlow<Long?> = _activeKnowledgeReaderNoteId.asStateFlow()

    // Root Knowledge Sets Library Hierarchy Browser State (Course -> Subject -> Notes)
    private val _showRootKnowledgeLibrary = MutableStateFlow(false)
    val showRootKnowledgeLibrary: StateFlow<Boolean> = _showRootKnowledgeLibrary.asStateFlow()

    private val _selectedKnowledgeCourse = MutableStateFlow<String?>(null)
    val selectedKnowledgeCourse: StateFlow<String?> = _selectedKnowledgeCourse.asStateFlow()

    private val _selectedKnowledgeSubject = MutableStateFlow<String?>(null)
    val selectedKnowledgeSubject: StateFlow<String?> = _selectedKnowledgeSubject.asStateFlow()

    private val _knowledgeCoursesMap = MutableStateFlow(appPreferences.getKnowledgeCoursesMap())
    val knowledgeCoursesMap: StateFlow<Map<String, List<String>>> = _knowledgeCoursesMap.asStateFlow()

    // Remembers source Knowledge Set noteId when user clicks "Draft Quiz" so Back returns smoothly
    private val _pendingKnowledgeReturnNoteId = MutableStateFlow<Long?>(null)
    val pendingKnowledgeReturnNoteId: StateFlow<Long?> = _pendingKnowledgeReturnNoteId.asStateFlow()

    // Full-screen Edit Metadata & Add Question overlay states (rendered outside Dialog windows so IME/nav insets never clip)
    private val _isEditMetadataOverlayOpen = MutableStateFlow(false)
    val isEditMetadataOverlayOpen: StateFlow<Boolean> = _isEditMetadataOverlayOpen.asStateFlow()

    private val _editMetadataEnterQuestionEditAction = MutableStateFlow<(() -> Unit)?>(null)
    val editMetadataEnterQuestionEditAction: StateFlow<(() -> Unit)?> = _editMetadataEnterQuestionEditAction.asStateFlow()

    private val _isAddQuestionOverlayOpen = MutableStateFlow(false)
    val isAddQuestionOverlayOpen: StateFlow<Boolean> = _isAddQuestionOverlayOpen.asStateFlow()

    private val _todayStudyAnalytics = MutableStateFlow<DailyStudyAnalyticsEntity?>(null)
    val todayStudyAnalytics: StateFlow<DailyStudyAnalyticsEntity?> = _todayStudyAnalytics.asStateFlow()

    private val _todayTestSeconds = MutableStateFlow(0L)
    val todayTestSeconds: StateFlow<Long> = _todayTestSeconds.asStateFlow()

    private val _dailyGoalMinutes = MutableStateFlow(appPreferences.getDailyGoalMinutes())
    val dailyGoalMinutes: StateFlow<Int> = _dailyGoalMinutes.asStateFlow()

    private val _rawInputText = MutableStateFlow("")
    val rawInputText: StateFlow<String> = _rawInputText.asStateFlow()

    private val _currentPaper = MutableStateFlow<QuestionPaper?>(null)
    val currentPaper: StateFlow<QuestionPaper?> = _currentPaper.asStateFlow()

    private val _currentPaperEntityId = MutableStateFlow<Long?>(null)
    val currentPaperEntityId: StateFlow<Long?> = _currentPaperEntityId.asStateFlow()

    // Live Parser Telemetry & Diagnostics
    private val _parseResult = MutableStateFlow<ParseResult?>(null)
    val parseResult: StateFlow<ParseResult?> = _parseResult.asStateFlow()

    // Autosave tracking
    private val _autoSaveStatus = MutableStateFlow(AutoSaveStatus.IDLE)
    val autoSaveStatus: StateFlow<AutoSaveStatus> = _autoSaveStatus.asStateFlow()

    private val _lastSavedTime = MutableStateFlow("")
    val lastSavedTime: StateFlow<String> = _lastSavedTime.asStateFlow()

    // Folders list
    private val _folders = MutableStateFlow<List<FolderItem>>(appPreferences.getCustomFolders())
    val folders: StateFlow<List<FolderItem>> = _folders.asStateFlow()

    // Preserved active folder location
    private val _lastActiveFolderId = MutableStateFlow<String>(appPreferences.getLastActiveFolderId())
    val lastActiveFolderId: StateFlow<String> = _lastActiveFolderId.asStateFlow()

    val libraryIndex: StateFlow<LibraryIndexState> = combine(
        savedPapers,
        allAttempts,
        _folders,
        studyNotes
    ) { papers, attempts, folderList, notes ->
        val attemptsByPaper = attempts.groupBy { it.paperId }
        val completedIds = mutableSetOf<Long>()
        val inProgressIds = mutableSetOf<Long>()
        val latestSubmitted = mutableMapOf<Long, AttemptEntity>()

        attemptsByPaper.forEach { (paperId, list) ->
            val submitted = list.filter { it.isSubmitted }
            if (submitted.isNotEmpty()) {
                completedIds.add(paperId)
                submitted.maxByOrNull { it.submittedAt ?: it.createdAt }?.let {
                    latestSubmitted[paperId] = it
                }
            } else if (list.any { !it.isSubmitted }) {
                inProgressIds.add(paperId)
            }
        }

        val papersByFolder = papers.groupBy { it.folderId }
        val notesByFolder = notes.groupBy { it.folderId }
        val childrenByParent = folderList.groupBy { it.parentId }
        val oneMonthAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)

        val directCounts = mutableMapOf<String, Int>()
        val totalCounts = mutableMapOf<String, Int>()
        val completedCounts = mutableMapOf<String, Int>()
        val monthlyCounts = mutableMapOf<String, Int>()
        val notesCounts = mutableMapOf<String, Int>()
        val folderLangs = mutableMapOf<String, List<String>>()

        val paperLanguagesById = papers.associate { p ->
            val langs = runCatching {
                PaperLanguageDetector.detectLanguagesCached(p.id, p.updatedAt, p.rawText, p.title)
            }.getOrElse { listOf("English") }
            p.id to langs
        }

        for (folder in folderList) {
            val desc = mutableSetOf(folder.id)
            val queue = ArrayDeque<String>()
            queue.add(folder.id)
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                val children = childrenByParent[cur] ?: continue
                for (child in children) {
                    if (desc.add(child.id)) queue.add(child.id)
                }
            }
            val direct = papersByFolder[folder.id].orEmpty()
            val allInSubtree = desc.flatMap { dId -> papersByFolder[dId].orEmpty() }
            val allNotesInSubtree = desc.sumOf { dId -> notesByFolder[dId]?.size ?: 0 } +
                allInSubtree.count { it.effectiveCategory() == PaperCategory.STUDY_NOTES }

            directCounts[folder.id] = direct.size
            totalCounts[folder.id] = allInSubtree.size
            completedCounts[folder.id] = allInSubtree.count { it.id in completedIds }
            monthlyCounts[folder.id] = allInSubtree.count { it.updatedAt >= oneMonthAgo }
            notesCounts[folder.id] = allNotesInSubtree
            folderLangs[folder.id] = allInSubtree
                .flatMap { paperLanguagesById[it.id].orEmpty() }
                .distinct()
        }

        val suggested = papers.firstOrNull { it.id in inProgressIds }
            ?: papers.firstOrNull { it.id !in completedIds && it.effectiveCategory() != PaperCategory.STUDY_NOTES }
            ?: papers.firstOrNull()

        val attempted = papers.filter { it.id in completedIds || it.id in inProgressIds }
        val weakPapers = papers.filter { p ->
            val att = latestSubmitted[p.id] ?: return@filter false
            val totalQ = att.totalQuestions.coerceAtLeast(1)
            (att.correctCount.toDouble() / totalQ) < 0.65 || att.incorrectCount >= 3
        }
        val mocks = papers.filter {
            val cat = it.effectiveCategory()
            cat == PaperCategory.MOCK_TEST || cat == PaperCategory.SECTIONAL
        }
        val pyqs = papers.filter { it.effectiveCategory() == PaperCategory.PYQ }

        LibraryIndexState(
            attemptsByPaperId = attemptsByPaper,
            completedPaperIds = completedIds,
            inProgressPaperIds = inProgressIds,
            latestSubmittedAttemptByPaperId = latestSubmitted,
            folderDirectCounts = directCounts,
            folderTotalCounts = totalCounts,
            folderCompletedCounts = completedCounts,
            folderMonthlyCounts = monthlyCounts,
            folderNotesCounts = notesCounts,
            folderLanguages = folderLangs,
            paperLanguagesById = paperLanguagesById,
            suggestedNextPaper = suggested,
            attemptedPapers = attempted,
            weakAccuracyPapers = weakPapers,
            mockPapers = mocks,
            pyqPapers = pyqs
        )
    }.conflate().catch { emit(LibraryIndexState()) }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, LibraryIndexState())

    fun setLastActiveFolderId(folderId: String) {
        _lastActiveFolderId.value = folderId
        appPreferences.setLastActiveFolderId(folderId)
    }

    // Active Tab & History Stack:
    // 0: Library, 1: Master View, 2: Student Portal, 3: Print & OMR, 4: Exam Tools, 5: AI Prompt, 6: Share, 7: Dictate
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _tabBackStack = MutableStateFlow<List<Int>>(emptyList())
    val tabBackStack: StateFlow<List<Int>> = _tabBackStack.asStateFlow()

    // Attempt tracking
    private val _currentAttemptId = MutableStateFlow<Long?>(null)
    val currentAttemptId: StateFlow<Long?> = _currentAttemptId.asStateFlow()

    private val _attempts = MutableStateFlow<List<AttemptEntity>>(emptyList())
    val attempts: StateFlow<List<AttemptEntity>> = _attempts.asStateFlow()

    // Student interactive answers
    private val _studentAnswers = MutableStateFlow<Map<Int, String>>(emptyMap())
    val studentAnswers: StateFlow<Map<Int, String>> = _studentAnswers.asStateFlow()

    private val _timeSpentSeconds = MutableStateFlow(0L)
    val timeSpentSeconds: StateFlow<Long> = _timeSpentSeconds.asStateFlow()

    private val _timerMode = MutableStateFlow(ExamTimerMode.COUNTDOWN)
    val timerMode: StateFlow<ExamTimerMode> = _timerMode.asStateFlow()

    private val _customTimeLimitMinutes = MutableStateFlow(30)
    val customTimeLimitMinutes: StateFlow<Int> = _customTimeLimitMinutes.asStateFlow()

    private val _timeRemainingSeconds = MutableStateFlow(30 * 60L)
    val timeRemainingSeconds: StateFlow<Long> = _timeRemainingSeconds.asStateFlow()

    private var examTimerJob: Job? = null
    private var attemptHistoryJob: Job? = null
    private var loadPaperJob: Job? = null

    private val parsedPaperCache = object : LinkedHashMap<String, ParseResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ParseResult>?): Boolean {
            return size > 16
        }
    }

    private fun parsePaperWithCache(entity: PaperEntity): ParseResult {
        val key = "${entity.id}|${entity.updatedAt}|${entity.rawText.length}|${entity.rawText.hashCode()}"
        synchronized(parsedPaperCache) {
            parsedPaperCache[key]?.let { return it }
        }
        val parsed = McqParser.parseWithResult(entity.rawText)
        synchronized(parsedPaperCache) {
            parsedPaperCache[key] = parsed
        }
        return parsed
    }

    /**
     * Parses a PaperEntity on Dispatchers.Default using the in-memory LRU cache.
     * Prevents synchronous McqParser execution on the main/UI thread in Compose dialogs.
     */
    suspend fun getParsedQuestionPaper(entity: PaperEntity): QuestionPaper = withContext(Dispatchers.Default) {
        val freshEntity = if (entity.id > 0L) paperDao.getPaperById(entity.id) ?: entity else entity
        val result = parsePaperWithCache(freshEntity)
        val savedDuration = if (freshEntity.durationMinutes > 0) freshEntity.durationMinutes else 60
        val savedPositive = if (freshEntity.positiveMarks > 0.0) freshEntity.positiveMarks else 4.0
        val savedNegative = if (freshEntity.negativeMarks >= 0.0) freshEntity.negativeMarks else 1.0
        val savedUnattended = if (freshEntity.unattendedMarks >= 0.0) freshEntity.unattendedMarks else 0.0
        result.paper.copy(
            id = freshEntity.id,
            metadata = PaperMetadata(
                id = "paper-${freshEntity.id}",
                title = freshEntity.title,
                subject = freshEntity.subject,
                topic = freshEntity.topic,
                setNumber = freshEntity.setNumber.ifBlank { "Set 1" },
                exam = freshEntity.exam,
                folderId = freshEntity.folderId,
                manualCategory = freshEntity.manualCategory,
                rollNumber = freshEntity.rollNumber,
                bookletNumber = freshEntity.bookletNumber,
                durationMinutes = savedDuration,
                positiveMarks = savedPositive,
                negativeMarks = savedNegative,
                unattendedMarks = savedUnattended,
                msqScoringPolicy = appPreferences.getPaperMsqPolicy(freshEntity.id),
                createdAt = freshEntity.createdAt
            )
        )
    }

    /**
     * Thread-safe, cache-backed QuestionPaper parser for background prompt builders (e.g. Speak to Test).
     * Call from a background dispatcher (Dispatchers.Default); avoids Room DB queries per item.
     */
    fun parsePaperForVoicePractice(entity: PaperEntity): QuestionPaper {
        val result = parsePaperWithCache(entity)
        val savedDuration = if (entity.durationMinutes > 0) entity.durationMinutes else 60
        val savedPositive = if (entity.positiveMarks > 0.0) entity.positiveMarks else 4.0
        val savedNegative = if (entity.negativeMarks >= 0.0) entity.negativeMarks else 1.0
        val savedUnattended = if (entity.unattendedMarks >= 0.0) entity.unattendedMarks else 0.0
        return result.paper.copy(
            id = entity.id,
            metadata = PaperMetadata(
                id = "paper-${entity.id}",
                title = entity.title,
                subject = entity.subject,
                topic = entity.topic,
                setNumber = entity.setNumber.ifBlank { "Set 1" },
                exam = entity.exam,
                folderId = entity.folderId,
                manualCategory = entity.manualCategory,
                rollNumber = entity.rollNumber,
                bookletNumber = entity.bookletNumber,
                durationMinutes = savedDuration,
                positiveMarks = savedPositive,
                negativeMarks = savedNegative,
                unattendedMarks = savedUnattended,
                msqScoringPolicy = appPreferences.getPaperMsqPolicy(entity.id),
                createdAt = entity.createdAt
            )
        )
    }

    private val _isLoadingPaper = MutableStateFlow(false)
    val isLoadingPaper: StateFlow<Boolean> = _isLoadingPaper.asStateFlow()

    private val _isExamSubmitted = MutableStateFlow(false)
    val isExamSubmitted: StateFlow<Boolean> = _isExamSubmitted.asStateFlow()

    private val _shuffleOptions = MutableStateFlow(appPreferences.getShuffleOptions())
    val shuffleOptions: StateFlow<Boolean> = _shuffleOptions.asStateFlow()

    private val _examSessionSeed = MutableStateFlow(System.currentTimeMillis())
    val examSessionSeed: StateFlow<Long> = _examSessionSeed.asStateFlow()

    // One-Time Official Evaluation Snapshot
    private val _submittedAnswers = MutableStateFlow<Map<Int, String>>(emptyMap())
    val submittedAnswers: StateFlow<Map<Int, String>> = _submittedAnswers.asStateFlow()

    private val _submittedStats = MutableStateFlow<TestStats?>(null)
    val submittedStats: StateFlow<TestStats?> = _submittedStats.asStateFlow()

    // Persistent Review Logs: questions answered incorrectly or unattended at submission time
    // Kept in log even if user attends or revises them in post-exam review
    private val _incorrectQuestionLog = MutableStateFlow<Set<Int>>(emptySet())
    val incorrectQuestionLog: StateFlow<Set<Int>> = _incorrectQuestionLog.asStateFlow()

    private val _unattendedQuestionLog = MutableStateFlow<Set<Int>>(emptySet())
    val unattendedQuestionLog: StateFlow<Set<Int>> = _unattendedQuestionLog.asStateFlow()

    private val _isExamStarted = MutableStateFlow(false)
    val isExamStarted: StateFlow<Boolean> = _isExamStarted.asStateFlow()

    private val _retryQuestionNumbers = MutableStateFlow<List<Int>?>(null)
    val retryQuestionNumbers: StateFlow<List<Int>?> = _retryQuestionNumbers.asStateFlow()

    private val _examConfig = MutableStateFlow(ExamConfig())
    val examConfig: StateFlow<ExamConfig> = _examConfig.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Reading font scale (Standard 100% Fixed, Compact 88%, Large 115%, Extra Large 125%, Follow System)
    private val _fontScaleMode = MutableStateFlow(appPreferences.getFontScaleMode())
    val fontScaleMode: StateFlow<AppFontScaleMode> = _fontScaleMode.asStateFlow()

    private val _fontSizeScale = MutableStateFlow(appPreferences.getFontSizeScale())
    val fontSizeScale: StateFlow<Float> = _fontSizeScale.asStateFlow()

    fun setFontScaleMode(mode: AppFontScaleMode) {
        _fontScaleMode.value = mode
        appPreferences.setFontScaleMode(mode)
        _fontSizeScale.value = mode.scaleFactor
    }

    fun setFontSizeScale(scale: Float) {
        _fontSizeScale.value = scale
        appPreferences.setFontSizeScale(scale)
    }

    // Persisted CBT active reading scale step (-1 = compact, 0 = default, +1 = large)
    private val _cbtFontScaleStep = MutableStateFlow(appPreferences.getCbtFontScaleStep())
    val cbtFontScaleStep: StateFlow<Int> = _cbtFontScaleStep.asStateFlow()

    fun setCbtFontScaleStep(step: Int) {
        val clamped = step.coerceIn(-1, 1)
        _cbtFontScaleStep.value = clamped
        appPreferences.setCbtFontScaleStep(clamped)
    }

    // Autosave debouncing jobs
    private var inputDebounceJob: Job? = null
    private var paperDebounceJob: Job? = null

    private val _lastOpenedPaper = MutableStateFlow<PaperEntity?>(null)
    val lastOpenedPaper: StateFlow<PaperEntity?> = _lastOpenedPaper.asStateFlow()

    // Dismissible contextual format tips
    private val _dismissedFormatTips = MutableStateFlow(
        listOf("input_mcq_formats", "empty_library_welcome", "study_planner_tip")
            .filter { appPreferences.isFormatTipDismissed(it) }
            .toSet()
    )
    val dismissedFormatTips: StateFlow<Set<String>> = _dismissedFormatTips.asStateFlow()

    fun isFormatTipDismissed(tipId: String): Boolean {
        return tipId in _dismissedFormatTips.value || appPreferences.isFormatTipDismissed(tipId)
    }

    fun dismissFormatTip(tipId: String) {
        appPreferences.dismissFormatTip(tipId)
        _dismissedFormatTips.value = _dismissedFormatTips.value + tipId
    }

    // Per-question pacing telemetry (stored in AttemptEntity.answersJson._qTime)
    private val _currentQuestionTimes = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val currentQuestionTimes: StateFlow<Map<Int, Long>> = _currentQuestionTimes.asStateFlow()

    private val questionTimesAccumulator = mutableMapOf<Int, Long>()
    private var activeTrackedQuestionNum: Int? = null
    private var activeQuestionStartedElapsedMs: Long = 0L
    private var activeQuestionRemainderMs: Long = 0L
    private var examTimerLastTickElapsedMs: Long = 0L
    private var examTimerRemainderMs: Long = 0L
    private var examTimerLastRoomFlushElapsedMs: Long = 0L

    // Brain & Calm Lab State & Telemetry
    private val _brainLabStats = MutableStateFlow(appPreferences.getBrainLabStats())
    val brainLabStats: StateFlow<com.example.model.BrainLabStats> = _brainLabStats.asStateFlow()

    private val _brainLabSettings = MutableStateFlow(appPreferences.getBrainLabSettings())
    val brainLabSettings: StateFlow<com.example.model.BrainLabSettings> = _brainLabSettings.asStateFlow()

    fun updateBrainLabSettings(transform: (com.example.model.BrainLabSettings) -> com.example.model.BrainLabSettings) {
        val updated = transform(_brainLabSettings.value)
        _brainLabSettings.value = updated
        appPreferences.saveBrainLabSettings(updated)
    }

    fun updateBrainLabStats(transform: (com.example.model.BrainLabStats) -> com.example.model.BrainLabStats) {
        val now = TrustedTimeManager.getTrustedTimeMillis()
        val updated = transform(_brainLabStats.value).copy(lastUpdatedAtMillis = now)
        _brainLabStats.value = updated
        appPreferences.saveBrainLabStats(updated)
    }

    fun recordBrainLabActiveSeconds(activity: com.example.model.BrainLabActivity, seconds: Long) {
        if (seconds <= 0L) return
        val settings = _brainLabSettings.value
        val countsAsCognitive = activity.countsAsCognitiveStudy
        updateBrainLabStats { cur ->
            if (countsAsCognitive) {
                cur.copy(totalCognitiveSeconds = cur.totalCognitiveSeconds + seconds)
            } else {
                cur.copy(totalCalmSeconds = cur.totalCalmSeconds + seconds)
            }
        }
        if (countsAsCognitive || settings.countCalmInDailyStudyTime) {
            recordStudySeconds(seconds)
        }
    }

    // Exam-Day Countdowns
    private val _examCountdowns = MutableStateFlow(appPreferences.getExamCountdowns())
    val examCountdowns: StateFlow<List<ExamCountdownTarget>> = _examCountdowns.asStateFlow()

    private val _countdownNowMillis = MutableStateFlow(TrustedTimeManager.getTrustedTimeMillis())
    val countdownNowMillis: StateFlow<Long> = _countdownNowMillis.asStateFlow()

    fun addOrUpdateExamCountdown(
        id: String? = null,
        title: String,
        targetDateStr: String,
        subject: String = "",
        colorHex: String = "#0D9488",
        isPinned: Boolean = true,
        targetTimeStr: String = "",
        timezoneMode: String = "LOCAL"
    ): Boolean {
        val cleanTitle = title.trim()
        val cleanDate = targetDateStr.trim()
        val cleanTime = targetTimeStr.trim()
        val normalizedTzMode = if (timezoneMode.equals("GMT", ignoreCase = true)) "GMT" else "LOCAL"
        if (cleanTitle.isBlank() || StudyDateUtils.parseDateToStartOfDayMillis(cleanDate) == null) {
            _statusMessage.value = "Enter a title and valid YYYY-MM-DD date"
            return false
        }
        if (cleanTime.isNotBlank() && !StudyDateUtils.isValidTimeHm(cleanTime)) {
            _statusMessage.value = "Enter a valid 24-hour time (HH:mm)"
            return false
        }
        val resolvedUtcMillis = if (cleanTime.isNotBlank()) {
            StudyDateUtils.parseDateTimeMillis(
                dateStr = cleanDate,
                timeStr = cleanTime,
                useGmt = normalizedTzMode == "GMT"
            )
        } else {
            null
        }
        val now = TrustedTimeManager.getTrustedTimeMillis()
        val list = _examCountdowns.value.toMutableList()
        val existingIdx = if (!id.isNullOrBlank()) list.indexOfFirst { it.id == id } else -1
        if (existingIdx >= 0) {
            list[existingIdx] = list[existingIdx].copy(
                title = cleanTitle,
                targetDateStr = cleanDate,
                subject = subject.trim(),
                colorHex = colorHex,
                isPinned = isPinned,
                updatedAt = now,
                targetTimeStr = cleanTime,
                timezoneMode = normalizedTzMode,
                targetUtcMillis = resolvedUtcMillis
            )
        } else {
            list.add(
                ExamCountdownTarget(
                    title = cleanTitle,
                    targetDateStr = cleanDate,
                    subject = subject.trim(),
                    colorHex = colorHex,
                    isPinned = isPinned,
                    createdAt = now,
                    updatedAt = now,
                    targetTimeStr = cleanTime,
                    timezoneMode = normalizedTzMode,
                    targetUtcMillis = resolvedUtcMillis
                )
            )
        }
        _examCountdowns.value = list
        appPreferences.saveExamCountdowns(list)
        _countdownNowMillis.value = TrustedTimeManager.getTrustedTimeMillis()
        _statusMessage.value = "Saved exam countdown: $cleanTitle"
        return true
    }

    fun deleteExamCountdown(id: String) {
        val updated = _examCountdowns.value.filterNot { it.id == id }
        _examCountdowns.value = updated
        appPreferences.saveExamCountdowns(updated)
    }

    fun togglePinExamCountdown(id: String) {
        val now = TrustedTimeManager.getTrustedTimeMillis()
        val updated = _examCountdowns.value.map {
            if (it.id == id) it.copy(isPinned = !it.isPinned, updatedAt = now) else it
        }
        _examCountdowns.value = updated
        appPreferences.saveExamCountdowns(updated)
    }

    // Smart Study To-Do Planner
    private val _studyTodos = MutableStateFlow(appPreferences.getStudyTodos())
    val studyTodos: StateFlow<List<StudyTodoItem>> = _studyTodos.asStateFlow()

    fun addStudyTodo(
        title: String,
        priority: TodoPriority = TodoPriority.MEDIUM,
        dueDateStr: String = "",
        targetPomodoros: Int = 1,
        linkedType: TodoLinkType = TodoLinkType.NONE,
        linkedPaperId: Long? = null,
        linkedNoteId: Long? = null,
        linkedDeckLanguage: String = "",
        linkedLabel: String = ""
    ): Boolean {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return false
        val newItem = StudyTodoItem(
            title = cleanTitle,
            priority = priority,
            dueDateStr = dueDateStr.trim(),
            targetPomodoros = targetPomodoros.coerceIn(1, 12),
            linkedType = linkedType,
            linkedPaperId = linkedPaperId,
            linkedNoteId = linkedNoteId,
            linkedDeckLanguage = linkedDeckLanguage.trim(),
            linkedLabel = linkedLabel.trim(),
            createdAt = TrustedTimeManager.getTrustedTimeMillis()
        )
        val updated = listOf(newItem) + _studyTodos.value
        _studyTodos.value = updated
        appPreferences.saveStudyTodos(updated)
        return true
    }

    fun toggleTodoCompleted(id: String) {
        val now = TrustedTimeManager.getTrustedTimeMillis()
        val updated = _studyTodos.value.map { item ->
            if (item.id == id) {
                val nextDone = !item.isCompleted
                item.copy(
                    isCompleted = nextDone,
                    completedAt = if (nextDone) now else null,
                    updatedAt = now
                )
            } else item
        }
        _studyTodos.value = updated
        appPreferences.saveStudyTodos(updated)
    }

    fun deleteTodoItem(id: String) {
        val updated = _studyTodos.value.filterNot { it.id == id }
        _studyTodos.value = updated
        appPreferences.saveStudyTodos(updated)
    }

    fun incrementTodoPomodoro(id: String) {
        val now = TrustedTimeManager.getTrustedTimeMillis()
        val updated = _studyTodos.value.map { item ->
            if (item.id == id) {
                item.copy(
                    completedPomodoros = item.completedPomodoros + 1,
                    updatedAt = now
                )
            } else item
        }
        _studyTodos.value = updated
        appPreferences.saveStudyTodos(updated)
    }

    val focusPomodoroController: FocusPomodoroController = FocusPomodoroController(
        context = application,
        prefs = appPreferences,
        scope = viewModelScope,
        onFlushFocusSeconds = { secs -> recordStudySeconds(secs) },
        onFocusSessionCompleted = { todoId ->
            if (todoId != null) {
                incrementTodoPomodoro(todoId)
            }
        },
        onStatusMessage = { msg -> _statusMessage.value = msg }
    )

    val pomodoroState: StateFlow<PomodoroUiState> = focusPomodoroController.state

    fun startFocusForTodo(item: StudyTodoItem) {
        focusPomodoroController.startFocus(
            todoId = item.id,
            todoTitle = item.title
        )
    }

    private data class StudySrsInputs(
        val notes: List<StudyNoteEntity>,
        val flashcardSrs: Map<Long, Map<Int, FlashcardSrsState>>,
        val vocabDecks: List<VocabularyDeck>,
        val vocabSrs: Map<String, Map<String, FlashcardSrsState>>
    )

    private val studySrsInputsFlow = combine(
        studyNotes,
        _flashcardSrsByNote,
        _vocabularyDecks,
        _vocabularySrsByDeck
    ) { notes, fcSrs, vDecks, vSrs ->
        StudySrsInputs(notes, fcSrs, vDecks, vSrs)
    }

    val studyDueSuggestions: StateFlow<List<StudyDueSuggestion>> = combine(
        studySrsInputsFlow,
        _headwordInbox,
        savedPapers,
        libraryIndex
    ) { srsInputs, inbox, papers, libIndex ->
        val now = TrustedTimeManager.getTrustedTimeMillis()
        val suggestions = mutableListOf<StudyDueSuggestion>()

        // 1. Due SM-2 Flashcards
        srsInputs.notes.forEach { note ->
            try {
                val rule = note.parsedFormatRule()
                val blocks = StudyNoteParser.parseBlocks(note.rawContent, rule)
                val cards = blocks.filter { it is StudyNoteBlock.OneLiner || it is StudyNoteBlock.MultiLinerCard }
                if (cards.isNotEmpty()) {
                    val stats = SpacedRepetitionEngine.computeDeckStats(
                        allCards = cards,
                        srsMap = srsInputs.flashcardSrs[note.id].orEmpty(),
                        nowMillis = now
                    )
                    val isFlashcardDeck = note.resolvedCollectionType() == KnowledgeCollectionType.FLASHCARDS
                    val dueOrReady = if (isFlashcardDeck) stats.actionableCount else stats.dueCount
                    if (stats.dueCount > 0 || (isFlashcardDeck && dueOrReady > 0)) {
                        val count = if (stats.dueCount > 0) stats.dueCount else dueOrReady
                        val subtitle = buildString {
                            if (stats.dueCount > 0) append("${stats.dueCount} due")
                            if (stats.dueCount > 0 && stats.newCount > 0) append(" • ")
                            if (stats.newCount > 0) append("${stats.newCount} new")
                            if (note.subject.isNotBlank()) append(" • ${note.subject}")
                        }
                        suggestions.add(
                            StudyDueSuggestion(
                                id = "fc-${note.id}",
                                category = DueSuggestionCategory.FLASHCARDS,
                                title = note.title.ifBlank { "Flashcard Deck" },
                                subtitle = subtitle,
                                count = count,
                                noteId = note.id
                            )
                        )
                    }
                }
            } catch (ce: kotlin.coroutines.cancellation.CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Skip corrupted note instead of dropping all due suggestions
            }
        }

        // 2. Due Vocabulary SRS Decks (per language)
        val vocabSummaries = VocabularyEngine.buildLanguageFolderSummaries(
            decks = srsInputs.vocabDecks,
            srsByDeck = srsInputs.vocabSrs,
            nowMillis = now
        )
        vocabSummaries.filter { it.dueCount > 0 }.forEach { summary ->
            suggestions.add(
                StudyDueSuggestion(
                    id = "vocab-${summary.language}",
                    category = DueSuggestionCategory.VOCABULARY,
                    title = "${summary.language} Vocabulary",
                    subtitle = "${summary.dueCount} word(s) due • ${summary.uniqueWords} total",
                    count = summary.dueCount,
                    language = summary.language
                )
            )
        }

        // 3. Pending Headword Inbox
        if (inbox.isNotEmpty()) {
            val topLang = inbox.groupingBy { it.language.ifBlank { "English" } }
                .eachCount()
                .maxByOrNull { it.value }?.key
            suggestions.add(
                StudyDueSuggestion(
                    id = "inbox-all",
                    category = DueSuggestionCategory.HEADWORD_INBOX,
                    title = "Headword Inbox Queue",
                    subtitle = "${inbox.size} queued word(s) awaiting enrichment",
                    count = inbox.size,
                    language = topLang
                )
            )
        }

        // 4. Paused / In-Progress Mock Tests
        papers.filter { it.id in libIndex.inProgressPaperIds }.forEach { paper ->
            suggestions.add(
                StudyDueSuggestion(
                    id = "paper-${paper.id}",
                    category = DueSuggestionCategory.PAUSED_TEST,
                    title = paper.title.ifBlank { "Saved Test" },
                    subtitle = "${paper.subject.ifBlank { "General" }} • Resume unfinished attempt",
                    count = 1,
                    paperId = paper.id
                )
            )
        }

        suggestions.toList()
    }.conflate().catch { emit(emptyList()) }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        focusPomodoroController.restoreDndIfOrphaned()
        restoreAutosavedState()
        initTodayStudyAnalytics()
        backfillLegacyPaperNumbers()
        com.example.util.TrustedTimeManager.registerNetworkObserver(application)
        com.example.util.TrustedTimeManager.syncWithNetworkAsync(viewModelScope)
        viewModelScope.launch {
            var wasSynced = com.example.util.TrustedTimeManager.isNetworkTimeSynced.value
            com.example.util.TrustedTimeManager.isNetworkTimeSynced.collect { synced ->
                if (synced && !wasSynced) {
                    refreshActiveWordOfTheDay()
                    _countdownNowMillis.value = TrustedTimeManager.getTrustedTimeMillis()
                }
                wasSynced = synced
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        focusPomodoroController.stopAndReset()
    }

    private fun backfillLegacyPaperNumbers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Targeted query: returns 0 rows on normal launches instead of loading all papers' rawText blobs
                val missing = paperDao.getPapersMissingNumbers()
                if (missing.isEmpty()) return@launch
                val toUpdate = missing.map { paper ->
                    val updatedRoll = if (paper.rollNumber.isBlank()) ExamNumberGenerator.generateRollNumber() else paper.rollNumber
                    val updatedBooklet = if (paper.bookletNumber.isBlank()) ExamNumberGenerator.generateBookletNumber() else paper.bookletNumber
                    paper.copy(
                        rollNumber = updatedRoll,
                        bookletNumber = updatedBooklet
                    )
                }
                paperDao.updatePapersBatch(toUpdate)
            } catch (e: Exception) {
                // Ignore transient errors during first launch
            }
        }
    }

    private fun restoreAutosavedState() {
        viewModelScope.launch(Dispatchers.IO) {
            // One-time cleanup of legacy starter samples (avoids scanning every paper's rawText on every cold launch)
            if (!appPreferences.hasCleanedLegacySamples()) {
                try {
                    val allPapers = paperDao.getAllPapersOnce()
                    for (p in allPapers) {
                        val isBoringSample = p.rawText == SampleData.SAMPLE_STARTER_PROMPT ||
                                p.rawText == SampleData.SAMPLE_SCIENCE_PAPER ||
                                p.title == "National Science & Technology Olympiad" ||
                                p.rawText.contains("Which chemical compound is commonly known as dry ice") ||
                                (p.title == "UPSC / SSC CGL Combined General Studies" && p.subject == "General Science")
                        if (isBoringSample) {
                            paperDao.deletePaperWithAttempts(p)
                            if (appPreferences.getLastActivePaperId() == p.id) {
                                appPreferences.setLastActivePaperId(-1L)
                            }
                        }
                    }
                    appPreferences.setLegacySamplesCleaned(true)
                } catch (e: Exception) {
                    // Ignore
                }
            }

            try {
                val lastPaperId = appPreferences.getLastActivePaperId()
                if (lastPaperId > 0) {
                    val entity = paperDao.getPaperById(lastPaperId)
                    if (entity != null) {
                        _lastOpenedPaper.value = entity
                    }
                } else {
                    // Fetch only the single most recently updated paper instead of all papers
                    _lastOpenedPaper.value = paperDao.getLatestPaper()
                }
            } catch (_: Exception) {
                // Ignore transient database errors during startup recovery
            }

            // Continuously synchronize _lastOpenedPaper with Room updates
            launch {
                savedPapers
                    .catch { emit(emptyList()) }
                    .collect { papers ->
                        if (papers.isNotEmpty()) {
                            val activeId = _currentPaperEntityId.value ?: appPreferences.getLastActivePaperId()
                            val matched = if (activeId > 0) papers.find { it.id == activeId } else null
                            val freshest = matched ?: papers.maxByOrNull { it.updatedAt }
                            _lastOpenedPaper.value = freshest
                        } else {
                            _lastOpenedPaper.value = null
                        }
                    }
            }

            // Preserve Set Opening on Minimize, But Not on App Restart / New Process:
            // When the app is minimized (Activity paused/stopped while process is alive),
            // the ViewModel retains in-memory state (_currentPaper stays populated).
            // On fresh process start / cold restart, _currentPaper remains null.
            // The user arrives at the clean Home/Library screen where the "Last Opened Set"
            // banner provides clear visibility and quick one-tap restoration.

            // Restore saved tab if valid, or default to Library (0)
            val savedTab = runCatching { appPreferences.getActiveTabIndex() }.getOrDefault(0)
            withContext(Dispatchers.Main) {
                if (savedTab in 0..7) {
                    _selectedTab.value = savedTab
                } else {
                    _selectedTab.value = 0
                }
            }
        }
    }

    private fun updateTimestamp() {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        _lastSavedTime.value = format.format(Date())
    }

    // --- Autosave Debounce Implementations ---

    /**
     * Debounced live input typing in Creator Tab:
     * Saves raw draft, parses with telemetry, and auto-persists to Room database.
     */
    fun onRawInputChanged(text: String) {
        val paperIdAtSchedule = _currentPaperEntityId.value
        _rawInputText.value = text
        _autoSaveStatus.value = AutoSaveStatus.SAVING

        inputDebounceJob?.cancel()
        inputDebounceJob = viewModelScope.launch(Dispatchers.Default) {
            delay(500) // 500ms debounce
            if (_currentPaperEntityId.value != paperIdAtSchedule) {
                return@launch
            }
            appPreferences.setDraftRawText(text)

            val result = McqParser.parseWithResult(text)
            withContext(Dispatchers.Main) {
                _parseResult.value = result
            }

            if (result.paper.questions.isNotEmpty()) {
                val cleanText = text.trim()
                val existing = paperDao.findPaperByRawText(cleanText)
                val paperId: Long = if (existing != null) {
                    val updated = existing.copy(
                        title = result.paper.metadata.title,
                        subject = result.paper.metadata.subject,
                        topic = result.paper.metadata.topic,
                        setNumber = result.paper.metadata.setNumber,
                        questionCount = result.paper.questions.size,
                        answerKeyCount = result.paper.quickAnswersCount,
                        explanationCount = result.paper.explanationsCount,
                        updatedAt = System.currentTimeMillis()
                    )
                    paperDao.updatePaper(updated)
                    existing.id
                } else {
                    val newEntity = PaperEntity(
                        title = result.paper.metadata.title,
                        subject = result.paper.metadata.subject,
                        topic = result.paper.metadata.topic,
                        setNumber = result.paper.metadata.setNumber,
                        exam = result.paper.metadata.exam,
                        questionCount = result.paper.questions.size,
                        answerKeyCount = result.paper.quickAnswersCount,
                        explanationCount = result.paper.explanationsCount,
                        rawText = cleanText,
                        folderId = result.paper.metadata.folderId,
                        rollNumber = result.paper.metadata.rollNumber,
                        bookletNumber = result.paper.metadata.bookletNumber
                    )
                    paperDao.insertPaper(newEntity)
                }

                if (_currentPaperEntityId.value != paperIdAtSchedule) {
                    return@launch
                }

                appPreferences.setLastActivePaperId(paperId)

                withContext(Dispatchers.Main) {
                    if (_currentPaperEntityId.value == paperIdAtSchedule) {
                        _currentPaperEntityId.value = paperId
                        _currentPaper.value = result.paper.copy(id = paperId)
                        _autoSaveStatus.value = AutoSaveStatus.SAVED
                        updateTimestamp()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (_currentPaperEntityId.value == paperIdAtSchedule) {
                        _autoSaveStatus.value = AutoSaveStatus.SAVED
                        updateTimestamp()
                    }
                }
            }
        }
    }

    /**
     * Debounced autosave when user modifies questions, reorders, or alters paper details.
     */
    private fun schedulePaperAutoSave(updatedPaper: QuestionPaper) {
        val paperIdAtSchedule = _currentPaperEntityId.value
        _currentPaper.value = updatedPaper
        _autoSaveStatus.value = AutoSaveStatus.SAVING

        paperDebounceJob?.cancel()
        paperDebounceJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500) // 500ms debounce

            // Identity Binding Guard:
            // Abort immediately if the active paper changed or was cleared while debounce was waiting
            if (_currentPaperEntityId.value != paperIdAtSchedule) {
                return@launch
            }

            val raw = McqParser.reconstructToRaw(updatedPaper)
            appPreferences.setDraftRawText(raw)

            val targetId: Long = if (paperIdAtSchedule != null && paperIdAtSchedule > 0) {
                val existing = paperDao.getPaperById(paperIdAtSchedule)
                if (existing != null) {
                    val updated = existing.copy(
                        title = updatedPaper.metadata.title,
                        subject = updatedPaper.metadata.subject,
                        topic = updatedPaper.metadata.topic,
                        setNumber = updatedPaper.metadata.setNumber,
                        questionCount = updatedPaper.questions.size,
                        answerKeyCount = updatedPaper.questions.count { it.correctAnswer != null },
                        explanationCount = updatedPaper.questions.count { !it.explanation.isNullOrBlank() },
                        rawText = raw,
                        folderId = updatedPaper.metadata.folderId ?: existing.folderId,
                        manualCategory = updatedPaper.metadata.manualCategory.ifBlank { existing.manualCategory },
                        durationMinutes = updatedPaper.metadata.durationMinutes,
                        positiveMarks = updatedPaper.metadata.positiveMarks,
                        negativeMarks = updatedPaper.metadata.negativeMarks,
                        unattendedMarks = updatedPaper.metadata.unattendedMarks,
                        updatedAt = System.currentTimeMillis()
                    )
                    paperDao.updatePaper(updated)
                    existing.id
                } else {
                    val newEntity = PaperEntity(
                        title = updatedPaper.metadata.title,
                        subject = updatedPaper.metadata.subject,
                        topic = updatedPaper.metadata.topic,
                        setNumber = updatedPaper.metadata.setNumber,
                        exam = updatedPaper.metadata.exam,
                        questionCount = updatedPaper.questions.size,
                        answerKeyCount = updatedPaper.questions.count { it.correctAnswer != null },
                        explanationCount = updatedPaper.questions.count { !it.explanation.isNullOrBlank() },
                        rawText = raw,
                        folderId = updatedPaper.metadata.folderId,
                        manualCategory = updatedPaper.metadata.manualCategory,
                        rollNumber = updatedPaper.metadata.rollNumber,
                        bookletNumber = updatedPaper.metadata.bookletNumber,
                        durationMinutes = updatedPaper.metadata.durationMinutes,
                        positiveMarks = updatedPaper.metadata.positiveMarks,
                        negativeMarks = updatedPaper.metadata.negativeMarks,
                        unattendedMarks = updatedPaper.metadata.unattendedMarks
                    )
                    paperDao.insertPaper(newEntity)
                }
            } else {
                val cleanText = raw.trim()
                val existingByRaw = paperDao.findPaperByRawText(cleanText)
                val existingByMeta = paperDao.findPaperByMetadata(
                    updatedPaper.metadata.title,
                    updatedPaper.metadata.subject,
                    updatedPaper.metadata.setNumber
                )
                val match = existingByRaw ?: existingByMeta
                val targetId: Long = if (match != null) {
                    val updated = match.copy(
                        title = updatedPaper.metadata.title,
                        subject = updatedPaper.metadata.subject,
                        topic = updatedPaper.metadata.topic,
                        setNumber = updatedPaper.metadata.setNumber,
                        questionCount = updatedPaper.questions.size,
                        answerKeyCount = updatedPaper.questions.count { it.correctAnswer != null },
                        explanationCount = updatedPaper.questions.count { !it.explanation.isNullOrBlank() },
                        rawText = raw,
                        folderId = updatedPaper.metadata.folderId ?: match.folderId,
                        manualCategory = updatedPaper.metadata.manualCategory.ifBlank { match.manualCategory },
                        rollNumber = if (match.rollNumber.isNotBlank()) match.rollNumber else updatedPaper.metadata.rollNumber,
                        bookletNumber = if (match.bookletNumber.isNotBlank()) match.bookletNumber else updatedPaper.metadata.bookletNumber,
                        durationMinutes = updatedPaper.metadata.durationMinutes,
                        positiveMarks = updatedPaper.metadata.positiveMarks,
                        negativeMarks = updatedPaper.metadata.negativeMarks,
                        unattendedMarks = updatedPaper.metadata.unattendedMarks,
                        updatedAt = System.currentTimeMillis()
                    )
                    paperDao.updatePaper(updated)
                    match.id
                } else {
                    val newEntity = PaperEntity(
                        title = updatedPaper.metadata.title,
                        subject = updatedPaper.metadata.subject,
                        topic = updatedPaper.metadata.topic,
                        setNumber = updatedPaper.metadata.setNumber,
                        exam = updatedPaper.metadata.exam,
                        questionCount = updatedPaper.questions.size,
                        answerKeyCount = updatedPaper.questions.count { it.correctAnswer != null },
                        explanationCount = updatedPaper.questions.count { !it.explanation.isNullOrBlank() },
                        rawText = raw,
                        folderId = updatedPaper.metadata.folderId,
                        manualCategory = updatedPaper.metadata.manualCategory,
                        rollNumber = updatedPaper.metadata.rollNumber,
                        bookletNumber = updatedPaper.metadata.bookletNumber,
                        durationMinutes = updatedPaper.metadata.durationMinutes,
                        positiveMarks = updatedPaper.metadata.positiveMarks,
                        negativeMarks = updatedPaper.metadata.negativeMarks,
                        unattendedMarks = updatedPaper.metadata.unattendedMarks
                    )
                    paperDao.insertPaper(newEntity)
                }
                targetId
            }

            // Identity Binding Guard after database write
            if (_currentPaperEntityId.value != paperIdAtSchedule) {
                return@launch
            }

            appPreferences.setLastActivePaperId(targetId)

            // Recalculate any submitted attempts for this paper so scores/breakdown stay 100% accurate
            recalculateAttemptsForPaper(targetId, updatedPaper)

            val freshSavedEntity = if (targetId > 0) paperDao.getPaperById(targetId) else null

            withContext(Dispatchers.Main) {
                if (_currentPaperEntityId.value == paperIdAtSchedule) {
                    if (freshSavedEntity != null) {
                        _lastOpenedPaper.value = freshSavedEntity
                    }
                    _currentPaperEntityId.value = targetId
                    _rawInputText.value = raw
                    _autoSaveStatus.value = AutoSaveStatus.SAVED
                    updateTimestamp()
                }
            }
        }
    }

    private suspend fun recalculateAttemptsForPaper(paperId: Long, updatedPaper: QuestionPaper) {
        try {
            val attempts = paperDao.getAttemptsForPaper(paperId).first()
            val submittedAttempts = attempts.filter { it.isSubmitted }
            if (submittedAttempts.isEmpty()) return

            submittedAttempts.forEach { attempt ->
                val answersMap = attempt.getAnswersMap()
                val stats = calculateTestStats(updatedPaper, answersMap)
                if (stats.score != attempt.score ||
                    stats.correct != attempt.correctCount ||
                    stats.incorrect != attempt.incorrectCount ||
                    stats.unanswered != attempt.unansweredCount ||
                    stats.total != attempt.totalQuestions
                ) {
                    val updatedAttempt = attempt.copy(
                        score = stats.score,
                        correctCount = stats.correct,
                        incorrectCount = stats.incorrect,
                        unansweredCount = stats.unanswered,
                        totalQuestions = stats.total
                    )
                    paperDao.updateAttempt(updatedAttempt)

                    // If currently loaded attempt matches this one, update live UI state as well
                    if (_currentAttemptId.value == attempt.id && _isExamSubmitted.value) {
                        withContext(Dispatchers.Main) {
                            _submittedStats.value = stats
                            _incorrectQuestionLog.value = updatedPaper.questions.filter { q ->
                                val grade = AnswerKeyMatcher.scoreQuestion(
                                    question = q,
                                    userAnswer = answersMap[q.num],
                                    positiveMarks = stats.positiveMarks,
                                    negativeMarks = stats.negativeMarks,
                                    unattendedMarks = stats.unattendedMarks,
                                    msqPolicy = updatedPaper.metadata.msqScoringPolicy
                                )
                                grade.status == AnswerKeyMatcher.QuestionGradeStatus.INCORRECT ||
                                    grade.status == AnswerKeyMatcher.QuestionGradeStatus.PARTIAL
                            }.map { it.num }.toSet()
                            _unattendedQuestionLog.value = updatedPaper.questions.filter { q ->
                                answersMap[q.num].isNullOrBlank()
                            }.map { it.num }.toSet()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Non-critical background recalculation safe failure
        }
    }

    // --- Master View Actions with Automatic Debounced Persistence & Duplicate Protection ---

    fun normalizeQuestionText(text: String): String {
        val withoutPrefix = text.trim().replace(Regex("^(?:Q\\s*\\d+|\\d+)[.:)\\-\\s]+", RegexOption.IGNORE_CASE), "")
        return withoutPrefix.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    fun findDuplicateQuestionNumber(questionText: String): Int? {
        val active = _currentPaper.value ?: return null
        val norm = normalizeQuestionText(questionText)
        if (norm.isBlank()) return null
        val match = active.questions.firstOrNull { normalizeQuestionText(it.question) == norm }
        return match?.num
    }

    /**
     * Adds a custom question with duplicate protection.
     * Returns Pair(isSuccess, questionNumberOrDuplicateNumber)
     */
    fun addCustomQuestion(
        questionText: String,
        optionA: String,
        optionB: String,
        optionC: String,
        optionD: String,
        correctAnswer: String?,
        explanation: String?,
        questionType: String = "Direct Fact",
        diagramUrl: String? = null,
        diagramLabel: String? = null,
        formula: String? = null,
        allowDuplicate: Boolean = false,
        optionE: String = ""
    ): Pair<Boolean, Int> {
        val active = _currentPaper.value ?: QuestionPaper(
            metadata = PaperMetadata(title = "Practice Set", subject = "General Knowledge"),
            questions = emptyList()
        )

        if (!allowDuplicate) {
            val duplicateNum = findDuplicateQuestionNumber(questionText)
            if (duplicateNum != null) {
                _statusMessage.value = "Duplicate of Q$duplicateNum detected! Not added."
                return Pair(false, duplicateNum)
            }
        }

        val nextNum = active.questions.size + 1
        val normalizedAnswer = AnswerKeyMatcher.normalizeKey(correctAnswer).takeIf { it.isNotBlank() }
        val resolvedType = when {
            AnswerKeyMatcher.parseSelectedLetters(normalizedAnswer).size >= 2 -> "MSQ"
            AnswerKeyMatcher.isExplicitMsqType(questionType) -> "MSQ"
            else -> questionType.ifBlank { "Direct Fact" }
        }
        val newQ = McqQuestion(
            num = nextNum,
            question = questionText.trim(),
            options = QuestionOptions(
                a = optionA.trim().ifBlank { "Option A" },
                b = optionB.trim().ifBlank { "Option B" },
                c = optionC.trim().ifBlank { "Option C" },
                d = optionD.trim().ifBlank { "Option D" },
                e = optionE.trim()
            ),
            correctAnswer = normalizedAnswer,
            explanation = explanation?.trim()?.takeIf { it.isNotBlank() },
            questionType = resolvedType,
            diagramUrl = diagramUrl?.trim()?.ifBlank { null },
            diagramLabel = diagramLabel?.trim()?.ifBlank { null },
            formula = formula?.trim()?.ifBlank { null }
        )

        val updatedList = active.questions + newQ
        val updatedPaper = active.copy(
            questions = updatedList,
            quickAnswersCount = updatedList.count { it.correctAnswer != null },
            explanationsCount = updatedList.count { !it.explanation.isNullOrBlank() }
        )
        schedulePaperAutoSave(updatedPaper)
        _statusMessage.value = "Added Question Q$nextNum successfully"
        return Pair(true, nextNum)
    }

    /**
     * Parses raw MCQ text and appends new questions to the current paper,
     * automatically filtering out duplicate questions.
     * Returns Pair(addedCount, duplicatesSkipped)
     */
    fun appendQuestionsFromRawText(rawText: String): Pair<Int, Int> {
        val active = _currentPaper.value ?: QuestionPaper(
            metadata = PaperMetadata(title = "Practice Set", subject = "General Knowledge"),
            questions = emptyList()
        )

        val parsed = McqParser.parseWithResult(rawText)
        val newQuestions = parsed.paper.questions
        if (newQuestions.isEmpty()) {
            _statusMessage.value = "No valid MCQ questions found to append"
            return Pair(0, 0)
        }

        val existingNorms = active.questions.map { normalizeQuestionText(it.question) }.filter { it.isNotBlank() }.toMutableSet()
        val questionsToAdd = mutableListOf<McqQuestion>()
        var duplicatesSkipped = 0

        for (q in newQuestions) {
            val norm = normalizeQuestionText(q.question)
            if (norm.isNotBlank() && existingNorms.contains(norm)) {
                duplicatesSkipped++
            } else {
                if (norm.isNotBlank()) existingNorms.add(norm)
                val renumbered = q.copy(num = active.questions.size + questionsToAdd.size + 1)
                questionsToAdd.add(renumbered)
            }
        }

        if (questionsToAdd.isNotEmpty()) {
            val combined = active.questions + questionsToAdd
            val updatedPaper = active.copy(
                questions = combined,
                quickAnswersCount = combined.count { it.correctAnswer != null },
                explanationsCount = combined.count { !it.explanation.isNullOrBlank() }
            )
            schedulePaperAutoSave(updatedPaper)
        }

        val msg = if (duplicatesSkipped > 0) {
            "Added ${questionsToAdd.size} questions (${duplicatesSkipped} duplicates skipped)"
        } else {
            "Added ${questionsToAdd.size} questions"
        }
        _statusMessage.value = msg
        return Pair(questionsToAdd.size, duplicatesSkipped)
    }

    fun updateQuestion(updatedQ: McqQuestion) {
        val active = _currentPaper.value ?: return
        val updatedQuestions = active.questions.map { if (it.num == updatedQ.num) updatedQ else it }
        val updatedPaper = active.copy(
            questions = updatedQuestions,
            quickAnswersCount = updatedQuestions.count { it.correctAnswer != null },
            explanationsCount = updatedQuestions.count { !it.explanation.isNullOrBlank() }
        )
        schedulePaperAutoSave(updatedPaper)
    }

    fun batchUpdateKeysAndExplanations(fixes: List<Pair<Int, Pair<String, String>>>) {
        val active = _currentPaper.value ?: return
        val fixMap = fixes.associate { it.first to it.second }
        val updatedQuestions = active.questions.map { q ->
            val fix = fixMap[q.num]
            if (fix != null) {
                q.copy(
                    correctAnswer = fix.first,
                    explanation = if (fix.second.isNotBlank()) fix.second else q.explanation
                )
            } else q
        }
        val updatedPaper = active.copy(
            questions = updatedQuestions,
            quickAnswersCount = updatedQuestions.count { it.correctAnswer != null },
            explanationsCount = updatedQuestions.count { !it.explanation.isNullOrBlank() }
        )
        schedulePaperAutoSave(updatedPaper)
        _statusMessage.value = "Updated ${fixes.size} answer keys and explanations"
    }

    /**
     * Parses raw pasted answer keys, detailed explanations, or combined tables,
     * and merges them into the current active paper matching by question number.
     * Supports arbitrary question number subsets (e.g. 1..N, middle missing Q25..Q40, etc.).
     * Returns Pair(answersUpdatedCount, explanationsUpdatedCount).
     */
    fun bulkMergeAnswersAndExplanations(rawText: String): Pair<Int, Int> {
        val active = _currentPaper.value ?: return Pair(0, 0)
        val sanitized = McqParser.sanitizePastedText(rawText)
        val (parsedAnswers, parsedExplanations, parsedTypes) = McqParser.parseAnswersAndExplanations(sanitized)
        if (parsedAnswers.isEmpty() && parsedExplanations.isEmpty()) {
            _statusMessage.value = "No answer keys or explanations found in pasted text"
            return Pair(0, 0)
        }

        var ansUpdated = 0
        var expUpdated = 0

        val updatedQuestions = active.questions.map { q ->
            val newAns = parsedAnswers[q.num]
            val newExp = parsedExplanations[q.num]
            val newType = parsedTypes[q.num]
            var qCopy = q

            if (newAns != null) {
                if (qCopy.correctAnswer != newAns) ansUpdated++
                qCopy = qCopy.copy(correctAnswer = newAns)
            }
            if (!newExp.isNullOrBlank()) {
                if (qCopy.explanation != newExp) expUpdated++
                qCopy = qCopy.copy(explanation = newExp)
            }
            if (!newType.isNullOrBlank() && qCopy.questionType == null) {
                qCopy = qCopy.copy(questionType = newType)
            }
            qCopy
        }

        val updatedPaper = active.copy(
            questions = updatedQuestions,
            quickAnswersCount = updatedQuestions.count { it.correctAnswer != null },
            explanationsCount = updatedQuestions.count { !it.explanation.isNullOrBlank() }
        )
        schedulePaperAutoSave(updatedPaper)
        _statusMessage.value = "Bulk merged: $ansUpdated answer keys and $expUpdated explanations updated"
        return Pair(ansUpdated, expUpdated)
    }

    fun deleteQuestion(qNum: Int) {
        val active = _currentPaper.value ?: return
        val filtered = active.questions.filter { it.num != qNum }
        val renumbered = filtered.mapIndexed { idx, q -> q.copy(num = idx + 1) }
        val updatedPaper = active.copy(
            questions = renumbered,
            quickAnswersCount = renumbered.count { it.correctAnswer != null },
            explanationsCount = renumbered.count { !it.explanation.isNullOrBlank() }
        )
        schedulePaperAutoSave(updatedPaper)
        _statusMessage.value = "Question Q$qNum deleted"
    }

    fun duplicateQuestion(q: McqQuestion) {
        val active = _currentPaper.value ?: return
        val newQ = q.copy(
            num = active.questions.size + 1,
            question = "${q.question} (Copy)"
        )
        val updatedList = active.questions + newQ
        val updatedPaper = active.copy(
            questions = updatedList,
            quickAnswersCount = updatedList.count { it.correctAnswer != null },
            explanationsCount = updatedList.count { !it.explanation.isNullOrBlank() }
        )
        schedulePaperAutoSave(updatedPaper)
        _statusMessage.value = "Duplicated as Q${newQ.num}"
    }

    fun moveQuestion(fromIndex: Int, directionUp: Boolean) {
        val active = _currentPaper.value ?: return
        val targetIndex = if (directionUp) fromIndex - 1 else fromIndex + 1
        if (targetIndex < 0 || targetIndex >= active.questions.size) return

        val mutable = active.questions.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(targetIndex, item)

        val renumbered = mutable.mapIndexed { idx, q -> q.copy(num = idx + 1) }
        val updatedPaper = active.copy(questions = renumbered)
        schedulePaperAutoSave(updatedPaper)
    }

    fun restorePaperQuestions(restoredQuestions: List<McqQuestion>) {
        val active = _currentPaper.value ?: return
        val updatedPaper = active.copy(
            questions = restoredQuestions,
            quickAnswersCount = restoredQuestions.count { it.correctAnswer != null },
            explanationsCount = restoredQuestions.count { !it.explanation.isNullOrBlank() }
        )
        schedulePaperAutoSave(updatedPaper)
        _statusMessage.value = "Edits discarded"
    }

    fun saveAllPaperQuestions(savedQuestions: List<McqQuestion>) {
        val active = _currentPaper.value ?: return
        val updatedPaper = active.copy(
            questions = savedQuestions,
            quickAnswersCount = savedQuestions.count { it.correctAnswer != null },
            explanationsCount = savedQuestions.count { !it.explanation.isNullOrBlank() }
        )
        schedulePaperAutoSave(updatedPaper)
        _statusMessage.value = "Changes confirmed & saved"
    }

    fun addNewQuestion() {
        val active = _currentPaper.value ?: QuestionPaper(
            metadata = PaperMetadata(title = "New Assessment", subject = "General Knowledge"),
            questions = emptyList()
        )
        val nextNum = active.questions.size + 1
        val newQ = McqQuestion(
            num = nextNum,
            question = "Enter new question statement here...",
            options = QuestionOptions(
                a = "First option",
                b = "Second option",
                c = "Third option",
                d = "Fourth option"
            ),
            correctAnswer = "A",
            explanation = "Pedagogical explanation for the correct option.",
            questionType = "Direct Fact"
        )
        val updatedList = active.questions + newQ
        val updatedPaper = active.copy(
            questions = updatedList,
            quickAnswersCount = updatedList.count { it.correctAnswer != null },
            explanationsCount = updatedList.count { !it.explanation.isNullOrBlank() }
        )
        schedulePaperAutoSave(updatedPaper)
        _statusMessage.value = "Added Question Q$nextNum"
    }

    fun updatePaperMetadata(
        title: String,
        subject: String,
        topic: String,
        setNumber: String,
        rollNumber: String? = null,
        bookletNumber: String? = null,
        durationMinutes: Int? = null,
        positiveMarks: Double? = null,
        negativeMarks: Double? = null,
        unattendedMarks: Double? = null,
        manualCategory: String? = null,
        msqScoringPolicy: MsqScoringPolicy? = null
    ) {
        val active = _currentPaper.value ?: return
        val cleanTitle = title.trim().ifBlank { "MCQ Practice Set" }
        val cleanSubject = subject.trim().ifBlank { "General Studies" }
        val cleanTopic = topic.trim()
        val rawSet = setNumber.trim().ifBlank { active.metadata.setNumber.ifBlank { "01" } }
        val setDigits = rawSet.filter { it.isDigit() }
        val cleanSet = if (setDigits.isNotEmpty() && setDigits.length <= 4) {
            val n = setDigits.toIntOrNull() ?: 1
            String.format(java.util.Locale.US, "%02d", n)
        } else rawSet
        val cleanRoll = rollNumber?.filter { it.isDigit() }?.take(10)?.ifBlank { null }
            ?: active.metadata.rollNumber.ifBlank { ExamNumberGenerator.generateRollNumber() }
        val cleanBooklet = bookletNumber?.filter { it.isDigit() }?.take(7)?.ifBlank { null }
            ?: active.metadata.bookletNumber.ifBlank { ExamNumberGenerator.generateBookletNumber() }

        val resolvedFolder = findFolderByPath(cleanSubject)
            ?: if (cleanSubject.replace("//", "\u0000").let { it.contains("/") || it.contains(">") }) {
                resolveOrCreateFolderPath(cleanSubject)
            } else null
        val targetFolderId = resolvedFolder?.id ?: active.metadata.folderId

        val finalDuration = durationMinutes ?: active.metadata.durationMinutes
        val finalPosMarks = positiveMarks ?: active.metadata.positiveMarks
        val finalNegMarks = negativeMarks ?: active.metadata.negativeMarks
        val finalUnkMarks = unattendedMarks ?: active.metadata.unattendedMarks
        val finalCategory = manualCategory?.trim() ?: active.metadata.manualCategory
        val finalMsqPolicy = msqScoringPolicy ?: active.metadata.msqScoringPolicy

        val newMetadata = active.metadata.copy(
            title = cleanTitle,
            subject = cleanSubject,
            topic = cleanTopic,
            setNumber = cleanSet,
            rollNumber = cleanRoll,
            bookletNumber = cleanBooklet,
            folderId = targetFolderId,
            manualCategory = finalCategory,
            durationMinutes = finalDuration,
            positiveMarks = finalPosMarks,
            negativeMarks = finalNegMarks,
            unattendedMarks = finalUnkMarks,
            msqScoringPolicy = finalMsqPolicy
        )
        val updatedPaper = active.copy(metadata = newMetadata)

        _examConfig.value = _examConfig.value.copy(
            examTitle = cleanTitle,
            subject = cleanSubject,
            seriesCode = cleanSet,
            rollNumber = cleanRoll,
            bookletNumber = cleanBooklet,
            durationMinutes = finalDuration,
            positiveMarks = finalPosMarks,
            negativeMarks = finalNegMarks,
            unattendedMarks = finalUnkMarks,
            msqScoringPolicy = finalMsqPolicy,
            timeAllowed = "$finalDuration Minutes",
            maxMarks = "${(finalPosMarks * updatedPaper.questions.size).toInt()}"
        )

        if (!_isExamStarted.value) {
            _customTimeLimitMinutes.value = finalDuration
            _timeRemainingSeconds.value = finalDuration * 60L
        }

        val oldTitle = active.metadata.title
        val linkedId = _currentPaperEntityId.value ?: (if (active.id > 0L) active.id else null)
        if (linkedId != null && linkedId > 0L && msqScoringPolicy != null) {
            appPreferences.setPaperMsqPolicy(linkedId, finalMsqPolicy)
        }
        schedulePaperAutoSave(updatedPaper)
        if (linkedId != null && oldTitle.isNotBlank() && oldTitle != cleanTitle) {
            syncLinkedKnowledgeSetTitles(linkedId, oldTitle, cleanTitle)
        }
        _statusMessage.value = "Updated: $cleanTitle ($cleanSet)"
    }

    // --- Loading & Parsing Papers ---

    fun parseAndSetPaper(
        text: String,
        customTitle: String? = null,
        customSubject: String? = null,
        customTopic: String? = null,
        customSetNumber: String? = null,
        customRollNumber: String? = null,
        customBookletNumber: String? = null,
        customFolderId: String? = null,
        customDurationMinutes: Int? = null,
        customPositiveMarks: Double? = null,
        customNegativeMarks: Double? = null,
        customUnattendedMarks: Double? = null,
        customManualCategory: String? = null,
        customMsqScoringPolicy: MsqScoringPolicy? = null
    ) {
        // Immediately cancel any running or scheduled auto-save debounces and decouple active paper ID
        paperDebounceJob?.cancel()
        paperDebounceJob = null
        inputDebounceJob?.cancel()
        inputDebounceJob = null
        _currentPaperEntityId.value = null

        viewModelScope.launch(Dispatchers.Default) {
            val result = McqParser.parseWithResult(text)
            val parsed = result.paper
            val rawCustomSet = if (!customSetNumber.isNullOrBlank()) customSetNumber.trim() else parsed.metadata.setNumber
            val customDigits = rawCustomSet.filter { it.isDigit() }
            val formattedSet = if (customDigits.isNotEmpty() && customDigits.length <= 4) {
                val n = customDigits.toIntOrNull() ?: 1
                String.format(java.util.Locale.US, "%02d", n)
            } else rawCustomSet

            val finalRoll = if (!customRollNumber.isNullOrBlank()) customRollNumber.trim() else parsed.metadata.rollNumber
            val finalBooklet = if (!customBookletNumber.isNullOrBlank()) customBookletNumber.trim() else parsed.metadata.bookletNumber
            val rawSubject = if (!customSubject.isNullOrBlank()) customSubject.trim() else parsed.metadata.subject

            // Automatically resolve single-level or multi-level folder hierarchies (e.g. UPSC CSE > Prelims > GS 1)
            val effectiveFolderId: String? = if (!customFolderId.isNullOrBlank()) {
                customFolderId.trim()
            } else if (rawSubject.isNotBlank() &&
                !rawSubject.equals("General", ignoreCase = true) &&
                !rawSubject.equals("General Studies", ignoreCase = true)
            ) {
                val placeholder = "\u0000LITERAL_SLASH\u0000"
                val normalizedCheck = rawSubject.replace("//", placeholder)
                findFolderByPath(rawSubject)?.id ?: if (normalizedCheck.contains("/") || normalizedCheck.contains(">")) {
                    resolveOrCreateFolderPath(rawSubject)?.id ?: parsed.metadata.folderId
                } else {
                    parsed.metadata.folderId
                }
            } else {
                parsed.metadata.folderId
            }

            val resolvedFolderPath = effectiveFolderId?.let { id ->
                if (_folders.value.any { it.id == id }) getFullFolderPath(id) else null
            }
            val finalSubject = if (customSubject.isNullOrBlank() && !resolvedFolderPath.isNullOrBlank()) {
                resolvedFolderPath
            } else {
                rawSubject
            }

            val existing = paperDao.findPaperByRawText(text.trim())
            val allExistingPapers = if (existing == null && customSetNumber.isNullOrBlank()) {
                paperDao.getAllPapers().first()
            } else {
                emptyList()
            }
            val initialFormattedSet = formattedSet.ifBlank { "01" }
            val resolvedSetForNewPaper = if (existing == null && customSetNumber.isNullOrBlank()) {
                val destPapers = allExistingPapers.filter { p ->
                    paperBelongsToDestination(
                        paper = p,
                        subject = finalSubject,
                        targetFolderId = effectiveFolderId,
                        folders = _folders.value
                    )
                }
                val hasCollision = destPapers.any { isSetNumberMatch(it.setNumber, initialFormattedSet) }
                if (hasCollision || (initialFormattedSet == "01" && destPapers.isNotEmpty())) {
                    computeNextSetForSubject(
                        subject = finalSubject,
                        papers = allExistingPapers,
                        targetFolderId = effectiveFolderId,
                        folders = _folders.value
                    )
                } else {
                    initialFormattedSet
                }
            } else {
                initialFormattedSet
            }

            val effectiveDuration = customDurationMinutes ?: _examConfig.value.durationMinutes.takeIf { it > 0 } ?: 60
            val effectivePositiveMarks = customPositiveMarks ?: _examConfig.value.positiveMarks.takeIf { it > 0.0 } ?: 4.0
            val effectiveNegativeMarks = customNegativeMarks ?: _examConfig.value.negativeMarks.takeIf { it >= 0.0 } ?: 1.0
            val effectiveUnattendedMarks = customUnattendedMarks ?: _examConfig.value.unattendedMarks.takeIf { it >= 0.0 } ?: 0.0
            val effectiveTitle = if (!customTitle.isNullOrBlank()) customTitle.trim() else parsed.metadata.title
            val effectiveTopic = if (!customTopic.isNullOrBlank()) customTopic.trim() else parsed.metadata.topic
            val resolvedCategory = when {
                !customManualCategory.isNullOrBlank() && PaperCategory.fromCode(customManualCategory) != PaperCategory.AUTO ->
                    PaperCategory.fromCode(customManualCategory).code
                parsed.metadata.manualCategory.isNotBlank() -> parsed.metadata.manualCategory
                else -> inferDefaultPaperCategory(
                    title = effectiveTitle,
                    subject = finalSubject,
                    topic = effectiveTopic,
                    setNumber = resolvedSetForNewPaper,
                    questionCount = parsed.questions.size
                ).code
            }

            val resolvedMsqPolicy = customMsqScoringPolicy
                ?: if (existing != null) appPreferences.getPaperMsqPolicy(existing.id)
                else if (parsed.metadata.msqScoringPolicy != MsqScoringPolicy.ALL_OR_NOTHING) parsed.metadata.msqScoringPolicy
                else appPreferences.getDefaultMsqScoringPolicy()

            val finalMetadata = parsed.metadata.copy(
                title = effectiveTitle,
                subject = finalSubject,
                topic = effectiveTopic,
                setNumber = resolvedSetForNewPaper,
                folderId = effectiveFolderId,
                manualCategory = resolvedCategory,
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                durationMinutes = effectiveDuration,
                positiveMarks = effectivePositiveMarks,
                negativeMarks = effectiveNegativeMarks,
                unattendedMarks = effectiveUnattendedMarks,
                msqScoringPolicy = resolvedMsqPolicy
            )
            val finalPaper = parsed.copy(metadata = finalMetadata)

            if (finalPaper.questions.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "No valid questions detected. Please verify your format (JSON array or Q1. (A)..(D))."
                }
                return@launch
            }

            val entityId: Long = if (existing != null) {
                // When an existing set is re-saved or moved to another folder, preserve its existing set number
                // so it remains tightly coupled to booklet and roll numbers
                val preservedSet = if (!customSetNumber.isNullOrBlank()) formattedSet else (if (existing.setNumber.isNotBlank()) existing.setNumber else finalMetadata.setNumber)
                val updated = existing.copy(
                    title = finalMetadata.title,
                    subject = finalMetadata.subject,
                    topic = finalMetadata.topic,
                    setNumber = preservedSet,
                    questionCount = finalPaper.questions.size,
                    answerKeyCount = finalPaper.quickAnswersCount,
                    explanationCount = finalPaper.explanationsCount,
                    folderId = finalMetadata.folderId ?: existing.folderId,
                    manualCategory = if (!customManualCategory.isNullOrBlank()) resolvedCategory else existing.manualCategory.ifBlank { resolvedCategory },
                    rollNumber = if (!customRollNumber.isNullOrBlank()) customRollNumber.trim() else (if (existing.rollNumber.isNotBlank()) existing.rollNumber else finalMetadata.rollNumber),
                    bookletNumber = if (!customBookletNumber.isNullOrBlank()) customBookletNumber.trim() else (if (existing.bookletNumber.isNotBlank()) existing.bookletNumber else finalMetadata.bookletNumber),
                    durationMinutes = effectiveDuration,
                    positiveMarks = effectivePositiveMarks,
                    negativeMarks = effectiveNegativeMarks,
                    unattendedMarks = effectiveUnattendedMarks,
                    updatedAt = System.currentTimeMillis()
                )
                paperDao.updatePaper(updated)
                if (existing.title.isNotBlank() && existing.title != finalMetadata.title) {
                    syncLinkedKnowledgeSetTitles(existing.id, existing.title, finalMetadata.title)
                }
                existing.id
            } else if (finalPaper.questions.isNotEmpty()) {
                val newEntity = PaperEntity(
                    title = finalMetadata.title,
                    subject = finalMetadata.subject,
                    topic = finalMetadata.topic,
                    setNumber = finalMetadata.setNumber,
                    exam = finalMetadata.exam,
                    questionCount = finalPaper.questions.size,
                    answerKeyCount = finalPaper.quickAnswersCount,
                    explanationCount = finalPaper.explanationsCount,
                    rawText = text.trim(),
                    folderId = finalMetadata.folderId,
                    manualCategory = resolvedCategory,
                    rollNumber = finalMetadata.rollNumber,
                    bookletNumber = finalMetadata.bookletNumber,
                    durationMinutes = effectiveDuration,
                    positiveMarks = effectivePositiveMarks,
                    negativeMarks = effectiveNegativeMarks,
                    unattendedMarks = effectiveUnattendedMarks
                )
                paperDao.insertPaper(newEntity)
            } else {
                0L
            }

            if (entityId > 0) {
                appPreferences.setLastActivePaperId(entityId)
                if (customMsqScoringPolicy != null || resolvedMsqPolicy != MsqScoringPolicy.ALL_OR_NOTHING) {
                    appPreferences.setPaperMsqPolicy(entityId, resolvedMsqPolicy)
                }
            }
            appPreferences.setDraftRawText(text)

            val freshSavedEntity = if (entityId > 0) paperDao.getPaperById(entityId) else null

            withContext(Dispatchers.Main) {
                if (freshSavedEntity != null) {
                    _lastOpenedPaper.value = freshSavedEntity
                }
                _currentPaperEntityId.value = if (entityId > 0) entityId else null
                _currentPaper.value = finalPaper.copy(id = entityId)
                _rawInputText.value = text
                _parseResult.value = result
                _studentAnswers.value = emptyMap()
                _isExamSubmitted.value = false
                _isExamStarted.value = false
                _retryQuestionNumbers.value = null
                _customTimeLimitMinutes.value = effectiveDuration
                _timeRemainingSeconds.value = effectiveDuration * 60L
                _timerMode.value = ExamTimerMode.COUNTDOWN
                _examConfig.value = _examConfig.value.copy(
                    examTitle = finalMetadata.title,
                    subject = finalMetadata.subject,
                    seriesCode = finalMetadata.setNumber.ifBlank { "SET - 1" },
                    durationMinutes = effectiveDuration,
                    timeAllowed = "$effectiveDuration Minutes",
                    positiveMarks = effectivePositiveMarks,
                    negativeMarks = effectiveNegativeMarks,
                    unattendedMarks = effectiveUnattendedMarks,
                    msqScoringPolicy = resolvedMsqPolicy,
                    maxMarks = String.format(java.util.Locale.US, "%.2f", effectivePositiveMarks * finalPaper.questions.size),
                    rollNumber = finalMetadata.rollNumber,
                    bookletNumber = finalMetadata.bookletNumber
                )
                _autoSaveStatus.value = AutoSaveStatus.SAVED
                updateTimestamp()
                _statusMessage.value = "Loaded ${finalPaper.questions.size} questions successfully!"
            }
        }
    }

    fun loadSavedPaper(entity: PaperEntity, onComplete: (() -> Unit)? = null) {
        // Cancel pending debounced autosaves and any superseded paper-load job
        paperDebounceJob?.cancel()
        paperDebounceJob = null
        inputDebounceJob?.cancel()
        inputDebounceJob = null
        loadPaperJob?.cancel()

        _isLoadingPaper.value = true
        _currentPaperEntityId.value = entity.id
        // Immediately synchronize timer limits and countdown to prevent lag/stale timer display
        val initialMinutes = if (entity.durationMinutes > 0) entity.durationMinutes else 60
        _customTimeLimitMinutes.value = initialMinutes
        _timeRemainingSeconds.value = initialMinutes * 60L
        _timerMode.value = ExamTimerMode.COUNTDOWN

        val job = viewModelScope.launch(Dispatchers.Default) {
            try {
                loadSavedPaperInternal(entity)
                withContext(Dispatchers.Main) {
                    _isLoadingPaper.value = false
                    onComplete?.invoke()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoadingPaper.value = false
                }
            }
        }
        loadPaperJob = job
    }

    private suspend fun loadSavedPaperInternal(entity: PaperEntity) {
        val freshEntity = if (entity.id > 0) {
            paperDao.getPaperById(entity.id) ?: entity
        } else {
            entity
        }
        val result = parsePaperWithCache(freshEntity)
        val finalRoll = if (freshEntity.rollNumber.isNotBlank()) freshEntity.rollNumber else ExamNumberGenerator.generateRollNumber()
        val finalBooklet = if (freshEntity.bookletNumber.isNotBlank()) freshEntity.bookletNumber else ExamNumberGenerator.generateBookletNumber()
        val savedDuration = if (freshEntity.durationMinutes > 0) freshEntity.durationMinutes else 60
        val savedPositive = if (freshEntity.positiveMarks > 0.0) freshEntity.positiveMarks else 4.0
        val savedNegative = if (freshEntity.negativeMarks >= 0.0) freshEntity.negativeMarks else 1.0
        val savedUnattended = if (freshEntity.unattendedMarks >= 0.0) freshEntity.unattendedMarks else 0.0
        val savedMsqPolicy = appPreferences.getPaperMsqPolicy(freshEntity.id)

        val parsed = result.paper.copy(
            id = freshEntity.id,
            metadata = PaperMetadata(
                id = "paper-${freshEntity.id}",
                title = freshEntity.title,
                subject = freshEntity.subject,
                topic = freshEntity.topic,
                setNumber = freshEntity.setNumber.ifBlank { "Set 1" },
                exam = freshEntity.exam,
                folderId = freshEntity.folderId,
                manualCategory = freshEntity.manualCategory,
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                durationMinutes = savedDuration,
                positiveMarks = savedPositive,
                negativeMarks = savedNegative,
                unattendedMarks = savedUnattended,
                msqScoringPolicy = savedMsqPolicy,
                createdAt = freshEntity.createdAt
            )
        )
        val unsubmittedAttempt = paperDao.getLatestUnsubmittedAttempt(freshEntity.id)
        appPreferences.setLastActivePaperId(freshEntity.id)

        if (freshEntity.rollNumber.isBlank() || freshEntity.bookletNumber.isBlank()) {
            val updatedWithNumbers = freshEntity.copy(
                rollNumber = finalRoll,
                bookletNumber = finalBooklet
            )
            paperDao.updatePaper(updatedWithNumbers)
            _lastOpenedPaper.value = updatedWithNumbers
        }

        withContext(Dispatchers.Main) {
            _lastOpenedPaper.value = freshEntity
            _currentPaperEntityId.value = freshEntity.id
            _currentPaper.value = parsed
            // Keep creator tab clean so opening a paper does not overwrite user's creator draft
            _parseResult.value = result

            _customTimeLimitMinutes.value = savedDuration
            _timeRemainingSeconds.value = savedDuration * 60L
            _timerMode.value = ExamTimerMode.COUNTDOWN

            if (unsubmittedAttempt != null) {
                val loadedQTimes = unsubmittedAttempt.getQuestionTimesMap()
                questionTimesAccumulator.clear()
                questionTimesAccumulator.putAll(loadedQTimes)
                _currentQuestionTimes.value = loadedQTimes
                activeTrackedQuestionNum = null
                _currentAttemptId.value = unsubmittedAttempt.id
                _studentAnswers.value = unsubmittedAttempt.getAnswersMap()
                _timeSpentSeconds.value = unsubmittedAttempt.timeSpentSeconds
                _isExamSubmitted.value = false
                _isExamStarted.value = false
                _retryQuestionNumbers.value = null
            } else {
                questionTimesAccumulator.clear()
                _currentQuestionTimes.value = emptyMap()
                activeTrackedQuestionNum = null
                _currentAttemptId.value = null
                _studentAnswers.value = emptyMap()
                _timeSpentSeconds.value = 0L
                _isExamSubmitted.value = false
                _isExamStarted.value = false
                _retryQuestionNumbers.value = null
            }

            _examConfig.value = _examConfig.value.copy(
                examTitle = entity.title,
                subject = entity.subject,
                seriesCode = entity.setNumber.ifBlank { "SET - 1" },
                durationMinutes = savedDuration,
                positiveMarks = savedPositive,
                negativeMarks = savedNegative,
                unattendedMarks = savedUnattended,
                msqScoringPolicy = savedMsqPolicy,
                timeAllowed = "$savedDuration Minutes",
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                maxMarks = String.format(java.util.Locale.US, "%.2f", savedPositive * parsed.questions.size)
            )
            _autoSaveStatus.value = AutoSaveStatus.SAVED
            val savedTimestamp = if (freshEntity.updatedAt > 0) freshEntity.updatedAt else freshEntity.createdAt
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            _lastSavedTime.value = format.format(Date(if (savedTimestamp > 0) savedTimestamp else System.currentTimeMillis()))
            _statusMessage.value = "Loaded: ${entity.title}"
        }

        loadAttemptHistory(entity.id)
    }

    fun loadStarterSample(key: String) {
        val (text, fallbackSubject) = when (key.lowercase()) {
            "civil", "engineering", "gate" -> Pair(SampleData.SAMPLE_CIVIL_MIXED_PATTERN, "Civil & Environmental Engineering")
            "bio" -> Pair(SampleData.SAMPLE_BIOLOGY_ADVANCED, "Biological Sciences")
            "patterns" -> Pair(SampleData.SAMPLE_NEXT_PATTERN, "Advanced MCQs")
            "hangul", "korean" -> Pair(SampleData.SAMPLE_KOREAN_EXAM, "World Languages")
            "hindi" -> Pair(SampleData.SAMPLE_HINDI_PAPER, "Hindi Literature & Grammar")
            "upsc", "mock" -> Pair(SampleData.SAMPLE_UPSC_GS1_FULL_MOCK_1, "UPSC CSE")
            else -> Pair(SampleData.SAMPLE_CIVIL_MIXED_PATTERN, "Civil & Environmental Engineering")
        }
        parseAndSetPaper(
            text = text,
            customSubject = fallbackSubject
        )
        // Switch immediately to Master View (Tab 1) so loaded questions are visible right away
        _selectedTab.value = 1
    }

    fun loadSample(key: String) = loadStarterSample(key)

    fun loadSample(index: Int) {
        val key = when (index) {
            0 -> "civil"
            1 -> "hindi"
            2 -> "hangul"
            3 -> "bio"
            4 -> "patterns"
            5 -> "upsc"
            else -> "civil"
        }
        loadStarterSample(key)
    }

    fun loadCurrentPaperIntoCreator() {
        val paper = _currentPaper.value ?: return
        val raw = if (paper.rawText.isNotBlank()) paper.rawText else McqParser.reconstructToRaw(paper)
        _rawInputText.value = raw
        _parseResult.value = McqParser.parseWithResult(raw)
        _statusMessage.value = "Loaded active paper into Creator workspace"
    }

    fun startNewBlankDraft() {
        _rawInputText.value = ""
        _parseResult.value = null
        appPreferences.setDraftRawText("")
        _statusMessage.value = "Started new blank creator draft"
    }

    fun loadFromUri(
        uri: Uri,
        customTitle: String? = null,
        customSubject: String? = null,
        customTopic: String? = null,
        customSetNumber: String? = null,
        customFolderId: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                if (content.isNotBlank()) {
                    if (content.trim().startsWith("{") && content.contains("\"questions\"")) {
                        // ExamSet JSON format
                        importPaperFromJson(content)
                    } else {
                        parseAndSetPaper(
                            text = content,
                            customTitle = customTitle,
                            customSubject = customSubject,
                            customTopic = customTopic,
                            customSetNumber = customSetNumber,
                            customFolderId = customFolderId
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Selected file is empty."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Error reading file: ${e.localizedMessage}"
                }
            }
        }
    }

    // --- JSON Import & Export Matching src/types/exam.ts ---

    fun importPaperFromJson(jsonStr: String): Boolean {
        return try {
            val trimmed = jsonStr.trim()
            val isBundleOrArray = trimmed.startsWith("[") || (trimmed.startsWith("{") && (trimmed.contains("\"questionSets\"") || trimmed.contains("\"sets\"") || trimmed.contains("MULTI_SET_EXAM_BUNDLE")))

            if (isBundleOrArray) {
                val papers = ExamJsonHelper.bundleFromJson(trimmed)
                if (papers.isEmpty()) {
                    _statusMessage.value = "No question sets found in JSON"
                    return false
                }
                viewModelScope.launch(Dispatchers.IO) {
                    var importedCount = 0
                    papers.forEach { paper ->
                        val raw = McqParser.reconstructToRaw(paper)
                        val existing = paperDao.findPaperByMetadata(paper.metadata.title, paper.metadata.subject, paper.metadata.setNumber)
                            ?: if (paper.metadata.bookletNumber.isNotBlank()) paperDao.findPaperByBookletNumber(paper.metadata.bookletNumber) else null
                        val entity = PaperEntity(
                            id = existing?.id ?: 0L,
                            title = paper.metadata.title,
                            subject = paper.metadata.subject.ifBlank { "General Knowledge" },
                            topic = paper.metadata.topic,
                            setNumber = paper.metadata.setNumber.ifBlank { "01" },
                            exam = paper.metadata.exam,
                            questionCount = paper.questions.size,
                            answerKeyCount = paper.quickAnswersCount,
                            explanationCount = paper.explanationsCount,
                            rawText = raw,
                            createdAt = if (paper.createdAt > 0) paper.createdAt else (existing?.createdAt ?: System.currentTimeMillis()),
                            updatedAt = System.currentTimeMillis(),
                            folderId = paper.metadata.folderId ?: existing?.folderId,
                            rollNumber = if (paper.metadata.rollNumber.isNotBlank()) paper.metadata.rollNumber else (existing?.rollNumber ?: ExamNumberGenerator.generateRollNumber()),
                            bookletNumber = if (paper.metadata.bookletNumber.isNotBlank()) paper.metadata.bookletNumber else (existing?.bookletNumber ?: ExamNumberGenerator.generateBookletNumber())
                        )
                        val insertedId = paperDao.insertPaper(entity)
                        if (paper.metadata.msqScoringPolicy != MsqScoringPolicy.ALL_OR_NOTHING) {
                            appPreferences.setPaperMsqPolicy(insertedId, paper.metadata.msqScoringPolicy)
                        }
                        importedCount++
                        if (importedCount == 1) {
                            val activePaper = paper.copy(id = insertedId)
                            withContext(Dispatchers.Main) {
                                _currentPaper.value = activePaper
                                _currentPaperEntityId.value = insertedId
                                appPreferences.setLastActivePaperId(insertedId)
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Imported $importedCount question set(s) with Roll, Booklet & Set numbers intact!"
                    }
                }
                true
            } else {
                val paper = ExamJsonHelper.fromJson(trimmed)
                val raw = McqParser.reconstructToRaw(paper)
                parseAndSetPaper(
                    text = raw,
                    customTitle = paper.metadata.title,
                    customSubject = paper.metadata.subject,
                    customTopic = paper.metadata.topic,
                    customSetNumber = paper.metadata.setNumber,
                    customRollNumber = paper.metadata.rollNumber,
                    customBookletNumber = paper.metadata.bookletNumber,
                    customFolderId = paper.metadata.folderId,
                    customDurationMinutes = paper.metadata.durationMinutes,
                    customPositiveMarks = paper.metadata.positiveMarks,
                    customNegativeMarks = paper.metadata.negativeMarks,
                    customUnattendedMarks = paper.metadata.unattendedMarks,
                    customManualCategory = paper.metadata.manualCategory,
                    customMsqScoringPolicy = paper.metadata.msqScoringPolicy
                )
                _statusMessage.value = "Imported: ${paper.metadata.title} (${paper.metadata.setNumber} | Roll: ${paper.metadata.rollNumber} | Booklet: ${paper.metadata.bookletNumber})"
                true
            }
        } catch (e: Exception) {
            _statusMessage.value = "Import failed: ${e.localizedMessage}"
            false
        }
    }

    /**
     * Looks up the best matching saved question set for an OMR sheet based on detected
     * 7-digit booklet number, booklet code (set A/B/C/D), or 10-digit roll number.
     */
    fun findMatchingPaperForScannedSheet(bookletNumber: String, setCode: String, rollNumber: String): PaperEntity? {
        val papers = savedPapers.value
        if (papers.isEmpty()) return null

        // 1. Direct match on 7-digit booklet number
        val cleanBooklet = bookletNumber.filter { it.isDigit() }
        if (cleanBooklet.length >= 6) {
            val byBooklet = papers.firstOrNull { it.bookletNumber.trim() == cleanBooklet }
            if (byBooklet != null) return byBooklet
        }

        // 2. Match on Set code / number (e.g. Set B / 02)
        val cleanCode = setCode.trim().uppercase()
        if (cleanCode.isNotBlank()) {
            val current = _currentPaper.value
            val currentFolder = current?.metadata?.folderId
            val bySetInFolder = papers.firstOrNull { p ->
                (currentFolder == null || p.folderId == currentFolder) &&
                (p.setNumber.contains(cleanCode, ignoreCase = true) ||
                 ExamNumberGenerator.extractBookletCode(p.setNumber).equals(cleanCode, ignoreCase = true))
            }
            if (bySetInFolder != null) return bySetInFolder
        }

        // 3. Match on 10-digit roll number
        val cleanRoll = rollNumber.filter { it.isDigit() }
        if (cleanRoll.length >= 8) {
            val byRoll = papers.firstOrNull { it.rollNumber.trim() == cleanRoll }
            if (byRoll != null) return byRoll
        }

        return null
    }

    /**
     * Exports a scanned OMR evaluation result as a standardized JSON payload for syncing across devices.
     */
    fun exportScannedOmrEvaluationJson(
        result: OmrVisionProcessor.OmrScanOutput,
        paper: QuestionPaper,
        candidateRollNumber: String? = null,
        candidateBookletNumber: String? = null,
        candidateBookletCode: String? = null
    ): String {
        val answersSnapshot = mutableMapOf<Int, String>()
        for ((qNum, qResult) in result.questionResults) {
            val mark = qResult.selectedOption?.toString()
            if (!mark.isNullOrBlank() && !qResult.isMultipleMarked) {
                answersSnapshot[qNum] = mark
            }
        }
        val payload = ExamJsonHelper.OmrExportPayload(
            candidateRollNumber = candidateRollNumber ?: result.headerResult?.rollNumber ?: paper.metadata.rollNumber,
            candidateBookletNumber = candidateBookletNumber ?: result.headerResult?.bookletNumber ?: paper.metadata.bookletNumber,
            candidateSetCode = candidateBookletCode ?: result.headerResult?.bookletCode ?: paper.metadata.setNumber,
            paperTitle = paper.metadata.title,
            paperId = paper.id,
            totalQuestions = result.totalQuestions,
            score = result.exactScore,
            correctCount = result.correctCount,
            incorrectCount = result.incorrectCount + result.multipleMarkedCount,
            unattemptedCount = result.unattemptedCount,
            answers = answersSnapshot
        )
        return ExamJsonHelper.omrEvaluationToJson(payload)
    }

    fun exportCurrentPaperAsJson(): String? {
        val paper = _currentPaper.value ?: return null
        return ExamJsonHelper.toJson(paper)
    }

    fun exportAllPapersAsJson(): String? {
        val papers = savedPapers.value
        if (papers.isEmpty()) return null
        val questionPapers = papers.map(::buildQuestionPaperForExport)
        val firstTitle = questionPapers.firstOrNull()?.metadata?.title ?: "Examination Package"
        val firstFolder = questionPapers.firstOrNull()?.metadata?.folderId
        return ExamJsonHelper.bundleToJson(firstTitle, firstFolder, questionPapers)
    }

    fun exportSelectedPapersAsJson(selectedPaperIds: Set<Long>): String? {
        val papers = savedPapers.value.filter { it.id in selectedPaperIds }
        if (papers.isEmpty()) return null
        val questionPapers = papers.map(::buildQuestionPaperForExport)
        val firstTitle = questionPapers.firstOrNull()?.metadata?.title ?: "Examination Package"
        val firstFolder = questionPapers.firstOrNull()?.metadata?.folderId
        return ExamJsonHelper.bundleToJson(firstTitle, firstFolder, questionPapers)
    }

    /**
     * Exports all papers residing within a folder (and its descendant subfolders) as structured JSON string.
     */
    fun exportFolderPapersAsJson(folderId: String): String? {
        val descendantIds = getAllDescendantFolderIds(folderId)
        val papers = savedPapers.value.filter { it.folderId != null && it.folderId in descendantIds }
        if (papers.isEmpty()) return null
        return exportSelectedPapersAsJson(papers.map { it.id }.toSet())
    }

    /**
     * Exports all papers residing within a folder (and its descendant subfolders) as formatted text summary.
     */
    fun exportFolderPapersAsFormattedText(folderId: String): String? {
        val descendantIds = getAllDescendantFolderIds(folderId)
        val papers = savedPapers.value.filter { it.folderId != null && it.folderId in descendantIds }
        if (papers.isEmpty()) return null
        return exportPapersAsFormattedText(papers.map { it.id }.toSet())
    }

    /**
     * Exports an entire folder subtree including hierarchy metadata and papers as a structured JSON bundle.
     */
    fun exportSubtreeAsJson(folderId: String): String? {
        val rootFolder = _folders.value.find { it.id == folderId } ?: return null
        val descendantIds = getAllDescendantFolderIds(folderId)
        val folderTree = _folders.value.filter { it.id in descendantIds }
        val papers = savedPapers.value.filter { it.folderId != null && it.folderId in descendantIds }

        val questionPapers = papers.map(::buildQuestionPaperForExport)

        return ExamJsonHelper.subtreeBundleToJson(rootFolder, folderTree, questionPapers)
    }

    /**
     * Exports an entire folder subtree as an indented hierarchical outline with question set contents.
     */
    fun exportSubtreeAsFormattedText(folderId: String): String? {
        val rootFolder = _folders.value.find { it.id == folderId } ?: return null
        val descendantIds = getAllDescendantFolderIds(folderId)
        val subtreeFolders = _folders.value.filter { it.id in descendantIds }
        val papers = savedPapers.value.filter { it.folderId != null && it.folderId in descendantIds }

        val dateFormatter = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())

        return buildString {
            append("========================================\n")
            append("FOLDER SUBTREE EXPORT: ${rootFolder.name}\n")
            append("Total Folders: ${subtreeFolders.size} | Total Sets: ${papers.size}\n")
            append("Exported: ${dateFormatter.format(java.util.Date())}\n")
            append("========================================\n\n")

            append("HIERARCHICAL STRUCTURE:\n")
            fun printTree(parentId: String?, indent: String) {
                val children = subtreeFolders.filter { it.parentId == parentId }
                children.forEachIndexed { idx, child ->
                    val isLast = idx == children.size - 1
                    val prefix = if (isLast) "└── " else "├── "
                    val childSetsCount = papers.count { it.folderId == child.id }
                    append("$indent$prefix${child.name} ($childSetsCount sets)\n")
                    val nextIndent = indent + if (isLast) "    " else "│   "
                    printTree(child.id, nextIndent)
                }
            }

            val rootSetsCount = papers.count { it.folderId == rootFolder.id }
            append("📁 ${rootFolder.name} ($rootSetsCount sets)\n")
            printTree(rootFolder.id, "   ")
            append("\n========================================\n\n")

            // Paper contents grouped by folder
            subtreeFolders.forEach { folder ->
                val folderPapers = papers.filter { it.folderId == folder.id }
                if (folderPapers.isNotEmpty()) {
                    val fullPath = getFullFolderPath(folder.id)
                    append(">>> FOLDER: $fullPath (${folderPapers.size} sets)\n")
                    append("----------------------------------------\n\n")
                    folderPapers.forEachIndexed { pIdx, entity ->
                        val parsed = McqParser.parse(entity.rawText)
                        append("[Set ${pIdx + 1}] ${entity.title}\n")
                        append("Set Code: ${entity.setNumber} | Roll: ${entity.rollNumber} | Questions: ${parsed.questions.size}\n")
                        if (entity.topic.isNotBlank()) append("Topic: ${entity.topic}\n")
                        append("\n${entity.rawText.trim()}\n\n")
                    }
                    append("\n")
                }
            }
        }
    }

    /**
     * Exports a single paper entity as structured JSON string.
     */
    fun exportSinglePaperAsJson(paper: PaperEntity): String {
        return ExamJsonHelper.toJson(buildQuestionPaperForExport(paper))
    }

    fun exportPapersAsFormattedText(selectedPaperIds: Set<Long>? = null): String? {
        val papers = if (selectedPaperIds == null) {
            savedPapers.value
        } else {
            savedPapers.value.filter { it.id in selectedPaperIds }
        }
        if (papers.isEmpty()) return null
        return buildString {
            papers.forEachIndexed { index, entity ->
                val parsed = McqParser.parse(entity.rawText)
                append("========================================\n")
                append("EXAM SET ${index + 1}: ${entity.title}\n")
                append("Subject: ${entity.subject} | Set: ${entity.setNumber}\n")
                if (entity.topic.isNotBlank()) append("Topic: ${entity.topic}\n")
                if (entity.exam.isNotBlank()) append("Target Exam: ${entity.exam}\n")
                append("Questions: ${parsed.questions.size}\n")
                append("========================================\n\n")
                append(entity.rawText.trim())
                append("\n\n\n")
            }
        }
    }

    suspend fun importMultiplePapersFromJson(jsonStr: String): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            val items = BatchExamImporter.validatePaperJson(jsonStr)
            val valid = items.filter { it.paper != null && it.status != com.example.model.BatchItemStatus.INVALID }
            var success = 0
            var failed = 0
            valid.forEach { item ->
                try {
                    val p = item.paper!!
                    val rawText = McqParser.reconstructToRaw(p)
                    val existing = paperDao.findPaperByMetadata(p.metadata.title, p.metadata.subject, p.metadata.setNumber)
                        ?: if (p.metadata.bookletNumber.isNotBlank()) paperDao.findPaperByBookletNumber(p.metadata.bookletNumber) else null
                    val entity = PaperEntity(
                        id = existing?.id ?: 0L,
                        title = p.metadata.title,
                        subject = p.metadata.subject.ifBlank { "General Knowledge" },
                        topic = p.metadata.topic,
                        setNumber = p.metadata.setNumber.ifBlank { "01" },
                        exam = p.metadata.exam,
                        questionCount = p.questions.size,
                        answerKeyCount = p.quickAnswersCount,
                        explanationCount = p.explanationsCount,
                        rawText = rawText,
                        createdAt = if (p.createdAt > 0) p.createdAt else (existing?.createdAt ?: System.currentTimeMillis()),
                        updatedAt = System.currentTimeMillis(),
                        folderId = p.metadata.folderId ?: existing?.folderId,
                        rollNumber = if (p.metadata.rollNumber.isNotBlank()) p.metadata.rollNumber else (existing?.rollNumber ?: ExamNumberGenerator.generateRollNumber()),
                        bookletNumber = if (p.metadata.bookletNumber.isNotBlank()) p.metadata.bookletNumber else (existing?.bookletNumber ?: ExamNumberGenerator.generateBookletNumber())
                    )
                    paperDao.insertPaper(entity)
                    success++
                } catch (e: Exception) {
                    failed++
                }
            }
            withContext(Dispatchers.Main) {
                if (success > 0) {
                    _statusMessage.value = "Imported $success question set(s) successfully!"
                } else {
                    _statusMessage.value = "No valid question sets found to import."
                }
            }
            Pair(success, failed)
        }
    }

    // --- Bulk Validation & Batch JSON Import ---

    fun batchValidateUris(uris: List<Uri>, onResult: (List<BatchImportItem>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = BatchExamImporter.readAndValidateUris(getApplication(), uris)
            withContext(Dispatchers.Main) {
                onResult(items)
            }
        }
    }

    fun batchValidateJsonString(jsonStr: String, sourceName: String = "Pasted JSON", onResult: (List<BatchImportItem>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = BatchExamImporter.validatePaperJson(jsonStr, fileName = sourceName)
            withContext(Dispatchers.Main) {
                onResult(items)
            }
        }
    }

    suspend fun importValidatedBatch(
        items: List<BatchImportItem>,
        targetFolder: String? = null,
        sequentialCohortNumbers: Boolean = false,
        startingRollNumber: Long = 2026100001L,
        startingBookletNumber: Long = 7100001L
    ): BatchImportResult {
        return withContext(Dispatchers.IO) {
            var importedCount = 0
            var totalQuestions = 0
            var failedCount = 0

            val validItems = items.filter { it.isSelected && it.paper != null && it.status != BatchItemStatus.INVALID }

            // Auto-create folder hierarchy if a new non-empty custom folder path is provided
            val effectiveTarget = targetFolder?.trim()
            val isExplicitTarget = !effectiveTarget.isNullOrBlank() &&
                !effectiveTarget.equals("all", ignoreCase = true) &&
                !effectiveTarget.equals("Default / From Metadata", ignoreCase = true) &&
                !effectiveTarget.equals("Keep Original", ignoreCase = true) &&
                !effectiveTarget.equals("Keep Original Metadata", ignoreCase = true)
            val resolvedTargetFolder = if (isExplicitTarget) {
                val existingByPath = resolveFolderBySubjectPath(effectiveTarget!!, _folders.value)
                    ?: _folders.value.find { it.name.equals(effectiveTarget, ignoreCase = true) }
                existingByPath ?: withContext(Dispatchers.Main) {
                    resolveOrCreateFolderPath(effectiveTarget)
                }
            } else {
                null
            }

            validItems.forEach { item ->
                try {
                    val p = item.paper!!
                    val finalSubject = if (isExplicitTarget) {
                        effectiveTarget!!
                    } else if (!item.customSubject.isNullOrBlank()) {
                        item.customSubject.trim()
                    } else {
                        p.metadata.subject.ifBlank { "General Knowledge" }
                    }

                    val finalRoll = if (sequentialCohortNumbers) {
                        (startingRollNumber + importedCount).toString().takeLast(10).padStart(10, '0')
                    } else if (p.metadata.rollNumber.isNotBlank()) {
                        ExamNumberGenerator.normalizeRollNumber(p.metadata.rollNumber, 10)
                    } else {
                        ExamNumberGenerator.generateRollNumber()
                    }

                    val finalBooklet = if (sequentialCohortNumbers) {
                        (startingBookletNumber + importedCount).toString().takeLast(7).padStart(7, '0')
                    } else if (p.metadata.bookletNumber.isNotBlank()) {
                        ExamNumberGenerator.normalizeBookletNumber(p.metadata.bookletNumber, 7)
                    } else {
                        ExamNumberGenerator.generateBookletNumber()
                    }

                    val targetFolderId = resolvedTargetFolder?.id ?: p.metadata.folderId
                    val updatedPaper = p.copy(
                        metadata = p.metadata.copy(
                            subject = finalSubject,
                            folderId = targetFolderId,
                            rollNumber = finalRoll,
                            bookletNumber = finalBooklet
                        )
                    )
                    val rawText = McqParser.reconstructToRaw(updatedPaper)

                    val newEntity = PaperEntity(
                        title = updatedPaper.metadata.title,
                        subject = finalSubject,
                        topic = updatedPaper.metadata.topic,
                        setNumber = updatedPaper.metadata.setNumber,
                        exam = updatedPaper.metadata.exam,
                        questionCount = updatedPaper.questions.size,
                        answerKeyCount = updatedPaper.quickAnswersCount,
                        explanationCount = updatedPaper.explanationsCount,
                        rawText = rawText,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        folderId = targetFolderId,
                        rollNumber = finalRoll,
                        bookletNumber = finalBooklet
                    )
                    paperDao.insertPaper(newEntity)
                    importedCount++
                    totalQuestions += updatedPaper.questions.size
                } catch (e: Exception) {
                    failedCount++
                }
            }

            withContext(Dispatchers.Main) {
                if (importedCount > 0) {
                    _statusMessage.value = "Successfully imported $importedCount exam paper(s) ($totalQuestions questions)"
                } else if (failedCount > 0) {
                    _statusMessage.value = "Failed to import selected papers."
                }
            }

            BatchImportResult(
                importedCount = importedCount,
                totalQuestions = totalQuestions,
                targetFolder = effectiveTarget ?: "",
                failedCount = failedCount
            )
        }
    }

    /**
     * Exports a standardized OMR Candidate Manifest CSV for all saved papers or selected IDs.
     * Contains Roll Number, Booklet Number, Set Code, Subject, Exam Title, Total Questions, and Answer Keys.
     */
    fun exportOmrCohortManifestCsv(selectedPaperIds: List<Long>? = null): String {
        val entities = if (selectedPaperIds.isNullOrEmpty()) {
            savedPapers.value
        } else {
            savedPapers.value.filter { it.id in selectedPaperIds }
        }
        val papers = entities.map { entity ->
            val parsed = McqParser.parse(entity.rawText)
            parsed.copy(
                metadata = parsed.metadata.copy(
                    title = entity.title,
                    subject = entity.subject,
                    topic = entity.topic,
                    setNumber = entity.setNumber,
                    rollNumber = entity.rollNumber,
                    bookletNumber = entity.bookletNumber
                )
            )
        }
        return ExamNumberGenerator.generateOmrCohortManifestCsv(papers)
    }

    /**
     * Generates a Classical Test Theory (CTT) psychometric report for the active paper or specified paperId.
     * Evaluates actual student candidate attempts (with roll number, booklet number, and set code).
     * If no student attempts exist or simulation mode is explicitly requested, generates a 40-student
     * calibrated simulation so question paper setters can evaluate predicted item discrimination and distractor curves.
     */
    fun generatePsychometricReport(
        targetPaperId: Long? = null,
        paperOverride: QuestionPaper? = null,
        forceSimulation: Boolean = false,
        allowSimulationFallback: Boolean = true
    ): PsychometricEngine.CohortPsychometricReport? {
        val paper = paperOverride ?: _currentPaper.value ?: run {
            if (targetPaperId != null) {
                savedPapers.value.find { it.id == targetPaperId }?.toQuestionPaper()
            } else null
        } ?: return null

        if (paper.questions.isEmpty()) return null

        val pid = targetPaperId ?: _currentPaperEntityId.value ?: savedPapers.value.find { it.title.equals(paper.metadata.title, ignoreCase = true) }?.id
        val paperAttempts = if (pid != null) allAttempts.value.filter { it.paperId == pid && it.isSubmitted } else emptyList()

        if (forceSimulation) {
            return PsychometricEngine.generateSimulatedCohortCalibration(paper, 40)
        }

        if (paperAttempts.isNotEmpty()) {
            val submissions = paperAttempts.mapIndexed { idx, att ->
                val roll = att.getCandidateRollNumber().ifBlank { String.format(java.util.Locale.US, "202610%04d", idx + 1) }
                val booklet = att.getCandidateBookletNumber().ifBlank { String.format(java.util.Locale.US, "710%04d", idx + 1) }
                val code = att.getCandidateBookletCode().ifBlank { paper.metadata.setNumber.ifBlank { "1" } }
                val answers = att.getAnswersMap().mapValues { it.value.firstOrNull() }
                PsychometricEngine.CandidateSubmission(
                    rollNumber = roll,
                    bookletNumber = booklet,
                    bookletCode = code,
                    answers = answers,
                    submittedAt = att.submittedAt ?: att.createdAt,
                    precomputedScore = att.score
                )
            }
            return PsychometricEngine.evaluateCohortWithCandidates(submissions, paper)
        } else if (allowSimulationFallback) {
            return PsychometricEngine.generateSimulatedCohortCalibration(paper, 40)
        }
        return PsychometricEngine.evaluateCohortWithCandidates(emptyList(), paper)
    }

    fun exportPsychometricReportCsv(
        paperOverride: QuestionPaper? = null,
        forceSimulation: Boolean = false,
        allowSimulationFallback: Boolean = true
    ): String {
        val paper = paperOverride ?: _currentPaper.value ?: return ""
        val report = generatePsychometricReport(
            paperOverride = paper,
            forceSimulation = forceSimulation,
            allowSimulationFallback = allowSimulationFallback
        ) ?: return ""
        return PsychometricEngine.formatPsychometricReportCsv(report, paper.metadata.title)
    }

    fun exportCandidateScorecardsCsv(
        paperOverride: QuestionPaper? = null,
        forceSimulation: Boolean = false,
        allowSimulationFallback: Boolean = true
    ): String {
        val paper = paperOverride ?: _currentPaper.value ?: return ""
        val report = generatePsychometricReport(
            paperOverride = paper,
            forceSimulation = forceSimulation,
            allowSimulationFallback = allowSimulationFallback
        ) ?: return ""
        return PsychometricEngine.formatCandidateScorecardsCsv(report, paper.metadata.title)
    }

    fun getCandidateSubmissions(
        targetPaperId: Long? = null,
        paperOverride: QuestionPaper? = null,
        forceSimulation: Boolean = false,
        allowSimulationFallback: Boolean = true
    ): List<PsychometricEngine.CandidateSubmission> {
        val paper = paperOverride ?: _currentPaper.value ?: run {
            if (targetPaperId != null) {
                savedPapers.value.find { it.id == targetPaperId }?.toQuestionPaper()
            } else null
        } ?: return emptyList()

        val pid = targetPaperId ?: _currentPaperEntityId.value ?: savedPapers.value.find { it.title.equals(paper.metadata.title, ignoreCase = true) }?.id
        val paperAttempts = if (pid != null) allAttempts.value.filter { it.paperId == pid && it.isSubmitted } else emptyList()

        if (!forceSimulation && paperAttempts.isNotEmpty()) {
            return paperAttempts.mapIndexed { idx, att ->
                val roll = att.getCandidateRollNumber().ifBlank { String.format(java.util.Locale.US, "202610%04d", idx + 1) }
                val booklet = att.getCandidateBookletNumber().ifBlank { String.format(java.util.Locale.US, "710%04d", idx + 1) }
                val code = att.getCandidateBookletCode().ifBlank { paper.metadata.setNumber.ifBlank { "1" } }
                val answers = att.getAnswersMap().mapValues { it.value.firstOrNull() }
                PsychometricEngine.CandidateSubmission(
                    rollNumber = roll,
                    bookletNumber = booklet,
                    bookletCode = code,
                    answers = answers,
                    submittedAt = att.submittedAt ?: att.createdAt,
                    precomputedScore = att.score
                )
            }
        } else if (forceSimulation || allowSimulationFallback) {
            return PsychometricEngine.generateSimulatedCohortSubmissions(paper, 40)
        }
        return emptyList()
    }

    fun exportSingleStudentReportCard(
        candidate: PsychometricEngine.CandidateScorecard,
        paperOverride: QuestionPaper? = null,
        forceSimulation: Boolean = false,
        allowSimulationFallback: Boolean = true
    ): String {
        val paper = paperOverride ?: _currentPaper.value ?: return ""
        val submissions = getCandidateSubmissions(
            paperOverride = paper,
            forceSimulation = forceSimulation,
            allowSimulationFallback = allowSimulationFallback
        )
        val answers = submissions.find { it.rollNumber == candidate.rollNumber }?.answers
        val report = generatePsychometricReport(
            paperOverride = paper,
            forceSimulation = forceSimulation,
            allowSimulationFallback = allowSimulationFallback
        )
        val cohortSize = report?.candidateCount ?: 1
        return PsychometricEngine.generateStudentReportCard(
            candidate = candidate,
            paper = paper,
            answers = answers,
            totalCohortSize = cohortSize
        )
    }

    fun exportAllStudentReportCardsText(
        paperOverride: QuestionPaper? = null,
        forceSimulation: Boolean = false,
        allowSimulationFallback: Boolean = true
    ): String {
        val paper = paperOverride ?: _currentPaper.value ?: return ""
        val report = generatePsychometricReport(
            paperOverride = paper,
            forceSimulation = forceSimulation,
            allowSimulationFallback = allowSimulationFallback
        ) ?: return ""
        val submissions = getCandidateSubmissions(
            paperOverride = paper,
            forceSimulation = forceSimulation,
            allowSimulationFallback = allowSimulationFallback
        )
        return PsychometricEngine.formatAllStudentReportCards(
            report = report,
            paper = paper,
            candidateSubmissions = submissions
        )
    }

    // --- Folders Management (Hierarchical & Multi-Level Subfolders) ---

    fun createFolderDirect(name: String, color: String, parentId: String? = null): FolderItem? {
        if (name.isBlank()) return null
        val existing = _folders.value.find { it.parentId == parentId && it.name.equals(name.trim(), ignoreCase = true) }
        if (existing != null) return existing

        val newFolder = FolderItem(
            id = "f-${System.currentTimeMillis().toString(36)}",
            name = name.trim(),
            color = color,
            parentId = parentId
        )
        val updated = _folders.value + newFolder
        _folders.value = updated
        appPreferences.saveCustomFolders(updated)
        val parentName = _folders.value.find { it.id == parentId }?.name
        _statusMessage.value = if (parentName != null) {
            "Sub-folder \"${newFolder.name}\" created in $parentName"
        } else {
            "Folder created: ${newFolder.name}"
        }
        return newFolder
    }

    fun createFolder(name: String, color: String, parentId: String? = null): FolderItem? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null

        val placeholder = "\u0000LITERAL_SLASH\u0000"
        val normalized = cleanName.replace("//", placeholder)

        // If it contains structural unescaped '/' or '>', resolve as hierarchy
        if (normalized.contains("/") || normalized.contains(">")) {
            return resolveOrCreateFolderPath(cleanName, baseParentId = parentId, preferredColor = color)
        }

        val actualName = normalized.replace(placeholder, "/").trim()
        return createFolderDirect(actualName, color, parentId)
    }

    /**
     * Returns immediate child subfolders for a given parent folder ID (or root folders if parentId == null).
     */
    fun getSubfolders(parentId: String?): List<FolderItem> {
        return _folders.value.filter { it.parentId == parentId }
    }

    /**
     * Recursively collects all descendant folder IDs (including folderId itself).
     */
    fun getAllDescendantFolderIds(folderId: String): Set<String> {
        val result = mutableSetOf(folderId)
        val queue = ArrayDeque<String>()
        queue.add(folderId)
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            val children = _folders.value.filter { it.parentId == curr }.map { it.id }
            for (child in children) {
                if (result.add(child)) {
                    queue.add(child)
                }
            }
        }
        return result
    }

    /**
     * Builds breadcrumb chain from root ancestor down to current folder.
     */
    fun getBreadcrumbPath(folderId: String?): List<FolderItem> {
        if (folderId == null || folderId == "all") return emptyList()
        val path = mutableListOf<FolderItem>()
        var curr = _folders.value.find { it.id == folderId }
        val visited = mutableSetOf<String>()
        while (curr != null && visited.add(curr.id)) {
            path.add(0, curr)
            curr = if (curr.parentId != null) _folders.value.find { it.id == curr.parentId } else null
        }
        return path
    }

    /**
     * Formats full path string (e.g., "UPSC CSE > Prelims > CSAT")
     */
    fun getFullFolderPath(folderId: String?): String {
        val crumbs = getBreadcrumbPath(folderId)
        return if (crumbs.isEmpty()) {
            "General"
        } else {
            crumbs.joinToString(" > ") { it.name }
        }
    }

    /**
     * Renames a folder and propagates the updated hierarchical path to all contained papers.
     */
    fun renameFolder(folderId: String, newName: String): Boolean {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return false
        val target = _folders.value.find { it.id == folderId } ?: return false
        if (target.name == cleanName) return true

        val now = TrustedTimeManager.getTrustedTimeMillis()
        val updatedFolders = _folders.value.map {
            if (it.id == folderId) it.copy(name = cleanName, updatedAt = now) else it
        }
        _folders.value = updatedFolders
        appPreferences.saveCustomFolders(updatedFolders)

        val affectedIds = getAllDescendantFolderIds(folderId)
        viewModelScope.launch(Dispatchers.IO) {
            val allPapers = paperDao.getAllPapers().first()
            allPapers.filter { it.folderId in affectedIds }.forEach { paper ->
                val newPath = getFullFolderPath(paper.folderId)
                paperDao.updatePaper(
                    paper.copy(
                        subject = newPath,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        _statusMessage.value = "Renamed folder to \"$cleanName\""
        return true
    }

    /**
     * Updates the color badge for a folder.
     */
    fun recolorFolder(folderId: String, newColor: String) {
        val target = _folders.value.find { it.id == folderId } ?: return
        if (target.color == newColor) return

        val now = TrustedTimeManager.getTrustedTimeMillis()
        val updatedFolders = _folders.value.map {
            if (it.id == folderId) it.copy(color = newColor, updatedAt = now) else it
        }
        _folders.value = updatedFolders
        appPreferences.saveCustomFolders(updatedFolders)
        _statusMessage.value = "Folder color updated"
    }

    /**
     * Re-parents a folder to another parent or root level.
     * Prevents cyclic dependencies (cannot move folder into self or any of its descendants).
     */
    fun reparentFolder(folderId: String, newParentId: String?): Boolean {
        val target = _folders.value.find { it.id == folderId } ?: return false
        if (target.parentId == newParentId) return true

        if (newParentId != null) {
            if (newParentId == folderId) {
                _statusMessage.value = "Cannot move folder into itself"
                return false
            }
            val descendants = getAllDescendantFolderIds(folderId)
            if (newParentId in descendants) {
                _statusMessage.value = "Cannot move folder into its own sub-folder"
                return false
            }
            val parentExists = _folders.value.any { it.id == newParentId }
            if (!parentExists) {
                _statusMessage.value = "Target parent folder not found"
                return false
            }
        }

        val now = TrustedTimeManager.getTrustedTimeMillis()
        val updatedFolders = _folders.value.map {
            if (it.id == folderId) it.copy(parentId = newParentId, updatedAt = now) else it
        }
        _folders.value = updatedFolders
        appPreferences.saveCustomFolders(updatedFolders)

        val affectedIds = getAllDescendantFolderIds(folderId)
        viewModelScope.launch(Dispatchers.IO) {
            val allPapers = paperDao.getAllPapers().first()
            allPapers.filter { it.folderId in affectedIds }.forEach { paper ->
                val newPath = getFullFolderPath(paper.folderId)
                paperDao.updatePaper(
                    paper.copy(
                        subject = newPath,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        val parentName = if (newParentId != null) _folders.value.find { it.id == newParentId }?.name ?: "Parent" else "Root"
        _statusMessage.value = "Moved \"${target.name}\" to $parentName"
        return true
    }

    /**
     * Deletes folder and all nested subfolders, and updates papers in those folders.
     */
    fun deleteFolder(folderId: String) {
        val idsToDelete = getAllDescendantFolderIds(folderId)
        val updatedFolders = _folders.value.filter { it.id !in idsToDelete }
        _folders.value = updatedFolders
        appPreferences.saveCustomFolders(updatedFolders)

        viewModelScope.launch(Dispatchers.IO) {
            val allPapers = paperDao.getAllPapers().first()
            allPapers.filter { it.folderId in idsToDelete }.forEach { paper ->
                paperDao.updatePaper(
                    paper.copy(
                        folderId = null,
                        subject = "General",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        _statusMessage.value = "Folder and subfolders deleted"
    }

    /**
     * Clones/Duplicates a folder and optionally all its nested subfolders and question sets.
     */
    fun duplicateFolder(
        sourceFolderId: String,
        newName: String,
        includePapers: Boolean = true,
        includeSubfolders: Boolean = true
    ): Boolean {
        val source = _folders.value.find { it.id == sourceFolderId } ?: return false
        val cleanName = newName.trim().ifBlank { "${source.name} (Copy)" }

        val idMap = mutableMapOf<String, String>()
        val newRootId = java.util.UUID.randomUUID().toString()
        idMap[source.id] = newRootId

        val clonedFolders = mutableListOf<FolderItem>()
        clonedFolders.add(
            source.copy(
                id = newRootId,
                name = cleanName,
                parentId = source.parentId,
                createdAt = System.currentTimeMillis()
            )
        )

        if (includeSubfolders) {
            val queue = ArrayDeque<String>()
            queue.add(source.id)
            while (queue.isNotEmpty()) {
                val currOldId = queue.removeFirst()
                val children = _folders.value.filter { it.parentId == currOldId }
                for (child in children) {
                    val newChildId = java.util.UUID.randomUUID().toString()
                    idMap[child.id] = newChildId
                    val parentNewId = idMap[currOldId]
                    clonedFolders.add(
                        child.copy(
                            id = newChildId,
                            parentId = parentNewId,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    queue.add(child.id)
                }
            }
        }

        val updatedFolders = _folders.value + clonedFolders
        _folders.value = updatedFolders
        appPreferences.saveCustomFolders(updatedFolders)

        if (includePapers) {
            viewModelScope.launch(Dispatchers.IO) {
                val allPapers = paperDao.getAllPapers().first()
                val targetPapers = allPapers.filter { it.folderId != null && it.folderId in idMap.keys }
                for (paper in targetPapers) {
                    val targetNewFolderId = idMap[paper.folderId]
                    val newSubject = getFullFolderPath(targetNewFolderId)
                    val clonedTitle = if (paper.folderId == source.id && !includeSubfolders) {
                        "${paper.title} (Copy)"
                    } else {
                        paper.title
                    }
                    val clonedPaper = paper.copy(
                        id = 0L,
                        title = clonedTitle,
                        folderId = targetNewFolderId,
                        subject = newSubject,
                        rollNumber = ExamNumberGenerator.generateRollNumber(),
                        bookletNumber = ExamNumberGenerator.generateBookletNumber(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    paperDao.insertPaper(clonedPaper)
                }
            }
        }

        _statusMessage.value = "Duplicated \"${source.name}\" (${clonedFolders.size} folder${if (clonedFolders.size > 1) "s" else ""})"
        return true
    }

    fun assignPaperToFolder(paper: PaperEntity, folderId: String?, displaySubject: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = paper.copy(
                folderId = folderId,
                subject = displaySubject.trim(),
                updatedAt = System.currentTimeMillis()
            )
            paperDao.updatePaper(updated)
            if (_currentPaperEntityId.value == paper.id) {
                val currentP = _currentPaper.value
                if (currentP != null) {
                    val updatedMeta = currentP.metadata.copy(
                        folderId = folderId,
                        subject = displaySubject.trim()
                    )
                    _currentPaper.value = currentP.copy(metadata = updatedMeta)
                }
            }
            _statusMessage.value = "Moved to $displaySubject"
        }
    }

    fun assignPaperToFolder(paper: PaperEntity, folderName: String) {
        val targetFolder = _folders.value.find { it.name.equals(folderName, ignoreCase = true) }
        val targetId = targetFolder?.id
        assignPaperToFolder(paper, targetId, folderName)
    }

    fun movePapersToFolder(paperIds: Set<Long>, folderId: String?, displaySubject: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val allPapers = savedPapers.value
            val targets = allPapers.filter { it.id in paperIds }
            targets.forEach { p ->
                val updated = p.copy(
                    folderId = folderId,
                    subject = displaySubject.trim(),
                    updatedAt = System.currentTimeMillis()
                )
                paperDao.updatePaper(updated)
            }
            val currentId = _currentPaperEntityId.value
            if (currentId != null && currentId in paperIds) {
                val currentP = _currentPaper.value
                if (currentP != null) {
                    val updatedMeta = currentP.metadata.copy(
                        folderId = folderId,
                        subject = displaySubject.trim()
                    )
                    _currentPaper.value = currentP.copy(metadata = updatedMeta)
                }
            }
            _statusMessage.value = "Moved ${targets.size} sets to $displaySubject"
        }
    }

    // --- Manual Paper Category Classification ---

    fun updatePaperCategory(paperId: Long, category: PaperCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            paperDao.updatePaperCategory(paperId, category.code, System.currentTimeMillis())
            if (_currentPaperEntityId.value == paperId) {
                val active = _currentPaper.value
                if (active != null) {
                    val updatedMeta = active.metadata.copy(manualCategory = category.code)
                    withContext(Dispatchers.Main) {
                        _currentPaper.value = active.copy(metadata = updatedMeta)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Assigned category: ${category.label}"
            }
        }
    }

    fun batchUpdatePaperCategory(paperIds: Set<Long>, category: PaperCategory) {
        if (paperIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            paperIds.forEach { id ->
                paperDao.updatePaperCategory(id, category.code, now)
            }
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Updated ${paperIds.size} sets to ${category.label}"
            }
        }
    }

    // --- Knowledge Sets Studio CRUD & Synergy ---

    fun openKnowledgeEditor(
        note: StudyNoteEntity? = null,
        defaultFolderId: String? = null,
        defaultCourse: String = "",
        defaultSubject: String = "",
        defaultLinkedPaperId: Long? = null,
        prefillTitle: String = "",
        prefillContent: String = "",
        defaultCollectionType: KnowledgeCollectionType = KnowledgeCollectionType.ONE_LINERS,
        defaultFormatRule: KnowledgeFormatRule? = null
    ) {
        val resolvedCourse = note?.course?.trim()?.ifBlank {
            defaultCourse.trim().ifBlank { resolveRootFolderName(note.folderId ?: defaultFolderId) }
        } ?: defaultCourse.trim().ifBlank {
            resolveRootFolderName(defaultFolderId)
        }
        _knowledgeEditorDraft.value = KnowledgeEditorDraft(
            note = note,
            defaultFolderId = defaultFolderId,
            defaultCourse = resolvedCourse,
            defaultSubject = defaultSubject,
            defaultLinkedPaperId = defaultLinkedPaperId,
            prefillTitle = prefillTitle,
            prefillContent = prefillContent,
            defaultCollectionType = defaultCollectionType,
            defaultFormatRule = defaultFormatRule
        )
    }

    fun closeKnowledgeEditor() {
        _knowledgeEditorDraft.value = null
    }

    fun openKnowledgeReader(noteId: Long) {
        _activeKnowledgeReaderNoteId.value = noteId
    }

    fun closeKnowledgeReader() {
        _activeKnowledgeReaderNoteId.value = null
    }

    fun openRootKnowledgeLibrary() {
        _showRootKnowledgeLibrary.value = true
    }

    fun closeRootKnowledgeLibrary() {
        _showRootKnowledgeLibrary.value = false
    }

    fun selectKnowledgeCourse(course: String?) {
        _selectedKnowledgeCourse.value = course
        if (course == null) {
            _selectedKnowledgeSubject.value = null
        } else {
            _selectedKnowledgeSubject.value = null
        }
    }

    fun selectKnowledgeSubject(subject: String?) {
        _selectedKnowledgeSubject.value = subject
    }

    fun resolveRootFolderName(folderId: String?): String {
        if (folderId.isNullOrBlank() || folderId == "all") return ""
        val folderMap = _folders.value.associateBy { it.id }
        var cur = folderMap[folderId] ?: return ""
        var guard = 0
        while (cur.parentId != null && guard < 16) {
            cur = folderMap[cur.parentId] ?: break
            guard++
        }
        return cur.name.trim()
    }

    private fun registerCourseSubjectInternal(course: String, subject: String) {
        val cleanCourse = course.trim()
        if (cleanCourse.isBlank()) return
        val cleanSubject = subject.trim()
        val current = _knowledgeCoursesMap.value.toMutableMap()
        val existingSubjects = current[cleanCourse].orEmpty().toMutableList()
        if (cleanSubject.isNotBlank() && existingSubjects.none { it.equals(cleanSubject, ignoreCase = true) }) {
            existingSubjects.add(cleanSubject)
        }
        current[cleanCourse] = existingSubjects
        _knowledgeCoursesMap.value = current
        appPreferences.saveKnowledgeCoursesMap(current)
    }

    fun createKnowledgeCourse(rawCourseName: String): Boolean {
        val clean = rawCourseName.trim()
        if (clean.isBlank()) return false
        val current = _knowledgeCoursesMap.value.toMutableMap()
        val existingKey = current.keys.firstOrNull { it.equals(clean, ignoreCase = true) }
        if (existingKey == null) {
            current[clean] = emptyList()
            _knowledgeCoursesMap.value = current
            appPreferences.saveKnowledgeCoursesMap(current)
            _statusMessage.value = "Created Course: $clean"
        }
        _selectedKnowledgeCourse.value = existingKey ?: clean
        _selectedKnowledgeSubject.value = null
        return true
    }

    fun addSubjectToKnowledgeCourse(courseName: String, rawSubjectName: String): Boolean {
        val cleanCourse = courseName.trim()
        val cleanSubject = rawSubjectName.trim()
        if (cleanCourse.isBlank() || cleanSubject.isBlank()) return false
        registerCourseSubjectInternal(cleanCourse, cleanSubject)
        _selectedKnowledgeCourse.value = cleanCourse
        _selectedKnowledgeSubject.value = cleanSubject
        _statusMessage.value = "Added Subject \"$cleanSubject\" to $cleanCourse"
        return true
    }

    fun renameKnowledgeCourse(oldCourse: String, newCourse: String) {
        val cleanOld = oldCourse.trim()
        val cleanNew = newCourse.trim()
        if (cleanOld.isBlank() || cleanNew.isBlank() || cleanOld == cleanNew) return
        val current = _knowledgeCoursesMap.value.toMutableMap()
        val oldSubs = current.remove(cleanOld).orEmpty()
        val mergedSubs = (current[cleanNew].orEmpty() + oldSubs).distinct()
        current[cleanNew] = mergedSubs
        _knowledgeCoursesMap.value = current
        appPreferences.saveKnowledgeCoursesMap(current)
        if (_selectedKnowledgeCourse.value == cleanOld) {
            _selectedKnowledgeCourse.value = cleanNew
        }
        viewModelScope.launch(Dispatchers.IO) {
            studyNoteDao.renameCourse(cleanOld, cleanNew)
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Renamed Course to \"$cleanNew\""
            }
        }
    }

    fun renameKnowledgeSubject(courseName: String, oldSubject: String, newSubject: String) {
        val cleanCourse = courseName.trim()
        val cleanOld = oldSubject.trim()
        val cleanNew = newSubject.trim()
        if (cleanCourse.isBlank() || cleanOld.isBlank() || cleanNew.isBlank() || cleanOld == cleanNew) return
        val current = _knowledgeCoursesMap.value.toMutableMap()
        val subs = current[cleanCourse].orEmpty().map { if (it == cleanOld) cleanNew else it }.distinct()
        current[cleanCourse] = subs
        _knowledgeCoursesMap.value = current
        appPreferences.saveKnowledgeCoursesMap(current)
        if (_selectedKnowledgeSubject.value == cleanOld) {
            _selectedKnowledgeSubject.value = cleanNew
        }
        viewModelScope.launch(Dispatchers.IO) {
            studyNoteDao.renameSubjectInCourse(cleanCourse, cleanOld, cleanNew)
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Renamed Subject to \"$cleanNew\""
            }
        }
    }

    fun assignNoteCourseAndSubject(noteId: Long, courseName: String, subjectName: String) {
        val cleanCourse = courseName.trim()
        val cleanSubject = subjectName.trim()
        if (noteId <= 0L || cleanSubject.isBlank()) return
        if (cleanCourse.isNotBlank()) {
            registerCourseSubjectInternal(cleanCourse, cleanSubject)
        }
        viewModelScope.launch(Dispatchers.IO) {
            studyNoteDao.assignCourseAndSubject(noteId, cleanCourse, cleanSubject)
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Moved to ${if (cleanCourse.isNotBlank()) "$cleanCourse → " else ""}$cleanSubject"
            }
        }
    }

    fun openEditMetadataOverlay(onEnterQuestionEditMode: (() -> Unit)? = null) {
        _editMetadataEnterQuestionEditAction.value = onEnterQuestionEditMode
        _isEditMetadataOverlayOpen.value = true
    }

    fun closeEditMetadataOverlay() {
        _isEditMetadataOverlayOpen.value = false
        _editMetadataEnterQuestionEditAction.value = null
    }

    fun openAddQuestionOverlay() {
        _isAddQuestionOverlayOpen.value = true
    }

    fun closeAddQuestionOverlay() {
        _isAddQuestionOverlayOpen.value = false
    }

    fun saveStudyNote(
        id: Long = 0L,
        title: String,
        subject: String,
        sourceAi: String,
        rawContent: String,
        folderId: String?,
        collectionType: KnowledgeCollectionType = KnowledgeCollectionType.ONE_LINERS,
        customLabel: String = "",
        linkedPaperId: Long? = null,
        formatRule: KnowledgeFormatRule = KnowledgeFormatRule(collectionType = collectionType),
        course: String = "",
        onSaved: (Long) -> Unit = {}
    ) {
        val cleanContent = rawContent.trim()
        if (cleanContent.isBlank()) {
            _statusMessage.value = "Knowledge set content cannot be empty"
            return
        }
        val cleanTitle = title.trim().ifBlank {
            cleanContent.lineSequence().firstOrNull { it.isNotBlank() }
                ?.replace(Regex("""^[#>*\-\d.\s]+"""), "")
                ?.take(48)
                ?.ifBlank { "Knowledge Set" }
                ?: "Knowledge Set"
        }
        val cleanSource = sourceAi.trim().ifBlank { StudyNoteParser.detectSourceHint(cleanContent) }
        val cleanCourse = course.trim().ifBlank {
            if (subject.contains(">")) subject.substringBefore(">").trim()
            else resolveRootFolderName(folderId)
        }
        val cleanSubject = subject.trim().let {
            if (it.contains(">")) it.substringAfterLast(">").trim() else it
        }.ifBlank {
            if (!folderId.isNullOrBlank()) getFullFolderPath(folderId).substringAfterLast(">").trim()
            else "General Notes"
        }
        if (cleanCourse.isNotBlank()) {
            registerCourseSubjectInternal(cleanCourse, cleanSubject)
        }
        val effectiveRule = formatRule.copy(
            collectionType = collectionType,
            customCollectionLabel = customLabel.trim()
        )

        viewModelScope.launch(Dispatchers.IO) {
            val existing = if (id > 0L) studyNoteDao.getStudyNoteById(id) else null
            val now = System.currentTimeMillis()
            val savedId = if (existing != null) {
                studyNoteDao.updateStudyNote(
                    existing.copy(
                        title = cleanTitle,
                        subject = cleanSubject,
                        course = cleanCourse.ifBlank { existing.course },
                        sourceAi = cleanSource,
                        rawContent = cleanContent,
                        folderId = folderId,
                        collectionType = collectionType.code,
                        customLabel = customLabel.trim(),
                        linkedPaperId = linkedPaperId,
                        formatRuleJson = effectiveRule.toJson(),
                        updatedAt = now
                    )
                )
                existing.id
            } else {
                studyNoteDao.insertStudyNote(
                    StudyNoteEntity(
                        folderId = folderId,
                        title = cleanTitle,
                        subject = cleanSubject,
                        course = cleanCourse,
                        sourceAi = cleanSource,
                        rawContent = cleanContent,
                        collectionType = collectionType.code,
                        customLabel = customLabel.trim(),
                        linkedPaperId = linkedPaperId,
                        formatRuleJson = effectiveRule.toJson(),
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            withContext(Dispatchers.Main) {
                _statusMessage.value = if (existing != null) "Updated Knowledge Set: $cleanTitle" else "Saved Knowledge Set: $cleanTitle"
                onSaved(savedId)
            }
        }
    }

    fun appendToStudyNote(
        noteId: Long,
        rawAppendText: String,
        skipDuplicates: Boolean = true,
        onCompleted: (addedUnits: Int, skippedDuplicates: Int) -> Unit = { _, _ -> }
    ) {
        if (noteId <= 0L || rawAppendText.isBlank()) {
            _statusMessage.value = "Nothing to append"
            onCompleted(0, 0)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val existing = studyNoteDao.getStudyNoteById(noteId)
            if (existing == null) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Knowledge set not found"
                    onCompleted(0, 0)
                }
                return@launch
            }
            val rule = existing.parsedFormatRule()
            val preview = StudyNoteParser.prepareAppendedContent(
                existingRaw = existing.rawContent,
                newRaw = rawAppendText,
                rule = rule,
                skipDuplicates = skipDuplicates
            )
            if (preview.appendSegment.isBlank()) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = if (preview.skippedDuplicatesCount > 0) {
                        "All ${preview.skippedDuplicatesCount} item(s) already exist in this set"
                    } else {
                        "No valid new items found to append"
                    }
                    onCompleted(0, preview.skippedDuplicatesCount)
                }
                return@launch
            }
            studyNoteDao.updateStudyNote(
                existing.copy(
                    rawContent = preview.combinedContent,
                    updatedAt = System.currentTimeMillis()
                )
            )
            withContext(Dispatchers.Main) {
                val added = preview.addedUnitCount.coerceAtLeast(if (preview.structuralBlocksCount > 0) 1 else 0)
                val msg = if (preview.skippedDuplicatesCount > 0) {
                    "Appended $added item(s) (${preview.skippedDuplicatesCount} duplicate(s) skipped)"
                } else {
                    "Appended $added item(s) to ${existing.title}"
                }
                _statusMessage.value = msg
                onCompleted(added, preview.skippedDuplicatesCount)
            }
        }
    }

    fun toggleStudyNoteLineBookmark(note: StudyNoteEntity, blockIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val fresh = studyNoteDao.getStudyNoteById(note.id) ?: note
            val current = fresh.getBookmarkedIndices().toMutableSet()
            if (blockIndex in current) current.remove(blockIndex) else current.add(blockIndex)
            studyNoteDao.updateStudyNote(fresh.withBookmarkedIndices(current))
        }
    }

    fun toggleStudyNoteBlockMastered(note: StudyNoteEntity, blockIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val fresh = studyNoteDao.getStudyNoteById(note.id) ?: note
            val current = fresh.getMasteredIndices().toMutableSet()
            if (blockIndex in current) current.remove(blockIndex) else current.add(blockIndex)
            studyNoteDao.updateStudyNote(fresh.withMasteredIndices(current))
        }
    }

    fun reviewFlashcard(note: StudyNoteEntity, blockIndex: Int, rating: SrsRating) {
        val now = com.example.util.TrustedTimeManager.getTrustedTimeMillis()
        val noteMap = _flashcardSrsByNote.value[note.id].orEmpty()
        val updatedCard = SpacedRepetitionEngine.computeNextState(
            current = noteMap[blockIndex],
            blockIndex = blockIndex,
            rating = rating,
            nowMillis = now
        )
        val updatedNoteMap = noteMap + (blockIndex to updatedCard)
        _flashcardSrsByNote.value = _flashcardSrsByNote.value + (note.id to updatedNoteMap)

        viewModelScope.launch(Dispatchers.IO) {
            appPreferences.saveFlashcardSrsMap(note.id, updatedNoteMap)
            val fresh = studyNoteDao.getStudyNoteById(note.id) ?: note
            val currentMastered = fresh.getMasteredIndices().toMutableSet()
            val shouldBeMastered = rating == SrsRating.EASY || SpacedRepetitionEngine.isMasteredBySrs(updatedCard)
            val changed = if (rating == SrsRating.AGAIN) {
                currentMastered.remove(blockIndex)
            } else if (shouldBeMastered) {
                currentMastered.add(blockIndex)
            } else {
                false
            }
            if (changed) {
                studyNoteDao.updateStudyNote(fresh.withMasteredIndices(currentMastered))
            }
        }
    }

    fun resetFlashcardSrsProgress(note: StudyNoteEntity) {
        _flashcardSrsByNote.value = _flashcardSrsByNote.value - note.id
        viewModelScope.launch(Dispatchers.IO) {
            appPreferences.clearFlashcardSrsMap(note.id)
            val fresh = studyNoteDao.getStudyNoteById(note.id) ?: note
            if (fresh.getMasteredIndices().isNotEmpty()) {
                studyNoteDao.updateStudyNote(fresh.withMasteredIndices(emptySet()))
            }
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Reset spaced repetition schedule for ${note.title}"
            }
        }
    }

    fun deleteStudyNote(note: StudyNoteEntity) {
        _flashcardSrsByNote.value = _flashcardSrsByNote.value - note.id
        viewModelScope.launch(Dispatchers.IO) {
            appPreferences.clearFlashcardSrsMap(note.id)
            studyNoteDao.deleteStudyNote(note)
            withContext(Dispatchers.Main) {
                if (_activeKnowledgeReaderNoteId.value == note.id) {
                    _activeKnowledgeReaderNoteId.value = null
                }
                _statusMessage.value = "Deleted Knowledge Set: ${note.title}"
            }
        }
    }

    fun assignStudyNoteToFolder(noteId: Long, folderId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            studyNoteDao.assignNoteToFolder(noteId, folderId)
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Moved Knowledge Set"
            }
        }
    }

    fun createWeakAreaKnowledgeSetFromTest(
        paper: QuestionPaper,
        incorrectNums: Set<Int>,
        unattendedNums: Set<Int>,
        answers: Map<Int, String>,
        rollNumber: String? = null,
        bookletNumber: String? = null,
        bookletCode: String? = null,
        attemptId: Long? = null
    ) {
        val linkedId = if (paper.id > 0L) paper.id else _currentPaperEntityId.value
        val currentAttempt = (attemptId ?: _currentAttemptId.value)?.let { aId ->
            _attempts.value.find { it.id == aId }
        }

        val resolvedRoll = rollNumber?.takeIf { it.isNotBlank() }
            ?: currentAttempt?.getCandidateRollNumber()?.takeIf { it.isNotBlank() }
            ?: _examConfig.value.rollNumber.takeIf { it.isNotBlank() }
            ?: paper.metadata.rollNumber.takeIf { it.isNotBlank() }
            ?: "UNASSIGNED"

        val resolvedBooklet = bookletNumber?.takeIf { it.isNotBlank() }
            ?: currentAttempt?.getCandidateBookletNumber()?.takeIf { it.isNotBlank() }
            ?: _examConfig.value.bookletNumber.takeIf { it.isNotBlank() }
            ?: paper.metadata.bookletNumber.takeIf { it.isNotBlank() }
            ?: "0000001"

        val resolvedCode = bookletCode?.takeIf { it.isNotBlank() }
            ?: currentAttempt?.getCandidateBookletCode()?.takeIf { it.isNotBlank() }
            ?: _examConfig.value.seriesCode.takeIf { it.isNotBlank() }
            ?: paper.metadata.setNumber.takeIf { it.isNotBlank() }
            ?: "Set A"

        val resolvedAttemptId = attemptId ?: _currentAttemptId.value
        val attemptSuffix = if (resolvedAttemptId != null && resolvedAttemptId > 0L) " #$resolvedAttemptId" else ""

        val noteTitle = "Weak Areas: ${paper.metadata.title} (Roll $resolvedRoll • Bklt $resolvedBooklet • Set $resolvedCode$attemptSuffix)".take(96)

        viewModelScope.launch(Dispatchers.IO) {
            val existingNotes = if (linkedId != null && linkedId > 0L) {
                studyNoteDao.getNotesLinkedToPaperOnce(linkedId)
            } else emptyList()

            // Look for existing weak area note for this candidate / attempt to prevent duplication
            val existingNote = existingNotes.find { note ->
                note.collectionType == KnowledgeCollectionType.WEAK_AREA_REVIEW.code &&
                (note.title == noteTitle ||
                 (note.rawContent.contains("Roll No: `$resolvedRoll`") && note.rawContent.contains("Booklet No: `$resolvedBooklet`")))
            }

            val content = StudyNoteParser.buildWeakAreaNoteContent(
                paper = paper,
                incorrectNums = incorrectNums,
                unattendedNums = unattendedNums,
                answers = answers,
                rollNumber = resolvedRoll,
                bookletNumber = resolvedBooklet,
                bookletCode = resolvedCode,
                attemptId = resolvedAttemptId,
                attemptTimestamp = currentAttempt?.submittedAt ?: currentAttempt?.createdAt,
                score = currentAttempt?.score,
                totalMarks = (paper.questions.size * (paper.metadata.positiveMarks.takeIf { it > 0 } ?: 4.0))
            )

            val targetId = existingNote?.id ?: 0L
            withContext(Dispatchers.Main) {
                saveStudyNote(
                    id = targetId,
                    title = noteTitle,
                    subject = paper.metadata.subject,
                    sourceAi = "Test Review",
                    rawContent = content,
                    folderId = paper.metadata.folderId,
                    collectionType = KnowledgeCollectionType.WEAK_AREA_REVIEW,
                    customLabel = "Weak Areas",
                    linkedPaperId = linkedId,
                    formatRule = KnowledgeFormatRule(
                        collectionType = KnowledgeCollectionType.WEAK_AREA_REVIEW,
                        customCollectionLabel = "Weak Areas",
                        preserveNestedBullets = true
                    ),
                    course = resolveRootFolderName(paper.metadata.folderId),
                    onSaved = { newId ->
                        val action = if (targetId > 0L) "Updated existing" else "Created unique"
                        _statusMessage.value = "$action Weak Areas Knowledge Set: $noteTitle"
                        openKnowledgeReader(newId)
                    }
                )
            }
        }
    }

    /**
     * Synchronizes Knowledge Set titles and content headers when a Question Set is renamed or updated.
     */
    fun syncLinkedKnowledgeSetTitles(paperId: Long, oldTitle: String, newTitle: String) {
        if (paperId <= 0L || oldTitle.isBlank() || newTitle.isBlank() || oldTitle == newTitle) return
        viewModelScope.launch(Dispatchers.IO) {
            val linkedNotes = studyNoteDao.getNotesLinkedToPaperOnce(paperId)
            if (linkedNotes.isEmpty()) return@launch

            linkedNotes.forEach { note ->
                val updatedTitle = if (note.title.contains(oldTitle, ignoreCase = true)) {
                    note.title.replace(oldTitle, newTitle, ignoreCase = true)
                } else if (note.collectionType == KnowledgeCollectionType.WEAK_AREA_REVIEW.code) {
                    note.title.replaceFirst(Regex("""^Weak Areas:\s*[^(\[]+"""), "Weak Areas: $newTitle ")
                } else {
                    note.title
                }

                val updatedRaw = if (note.rawContent.contains(oldTitle, ignoreCase = true)) {
                    note.rawContent.replace(oldTitle, newTitle, ignoreCase = true)
                } else {
                    note.rawContent
                }

                if (updatedTitle != note.title || updatedRaw != note.rawContent) {
                    studyNoteDao.updateStudyNote(
                        note.copy(
                            title = updatedTitle.take(120),
                            rawContent = updatedRaw,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    /**
     * Compiles a unique, master Active Recall Knowledge Set for a given Subject or Course Folder
     * aggregating all question papers (stems, explanations, formulas, answer keys) and study notes in that course.
     */
    fun compileActiveRecallSetForSubjectOrFolder(
        folderId: String?,
        subjectName: String,
        courseName: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val allPapers = paperDao.getAllPapersOnce()
            val coursePapersEntities = allPapers.filter { p ->
                if (!folderId.isNullOrBlank() && folderId != "all") {
                    p.folderId == folderId || (subjectName.isNotBlank() && p.subject.equals(subjectName, ignoreCase = true))
                } else if (subjectName.isNotBlank() && subjectName != "All") {
                    p.subject.equals(subjectName, ignoreCase = true)
                } else {
                    true
                }
            }

            val targetFolderId = if (!folderId.isNullOrBlank() && folderId != "all") folderId else null
            val resolvedCourse = courseName.trim().ifBlank { resolveRootFolderName(targetFolderId) }
            val resolvedSubject = subjectName.trim().ifBlank {
                coursePapersEntities.firstOrNull()?.subject ?: "General Studies"
            }

            val courseNotes = if (!targetFolderId.isNullOrBlank()) {
                studyNoteDao.getNotesForFolderOnce(targetFolderId)
            } else {
                studyNoteDao.getAllStudyNotesOnce().filter {
                    it.subject.equals(resolvedSubject, ignoreCase = true) &&
                        (resolvedCourse.isBlank() || it.effectiveCourseLabel().equals(resolvedCourse, ignoreCase = true))
                }
            }

            if (coursePapersEntities.isEmpty() && courseNotes.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "No study material found in $resolvedSubject to compile."
                }
                return@launch
            }

            val parsedPapers = coursePapersEntities.map { entity ->
                buildQuestionPaperForExport(entity)
            }

            val compiledContent = StudyNoteParser.compileSubjectActiveRecallSet(
                subjectName = resolvedSubject,
                papers = parsedPapers,
                existingNotes = courseNotes,
                courseOrFolderTitle = if (resolvedCourse.isNotBlank()) "$resolvedCourse • $resolvedSubject" else resolvedSubject
            )

            val masterTitle = "Master Active Recall: $resolvedSubject".take(80)

            val existingMasterNote = courseNotes.find { note ->
                note.title.startsWith("Master Active Recall: $resolvedSubject", ignoreCase = true) ||
                (targetFolderId != null && note.folderId == targetFolderId && note.title.startsWith("Master Active Recall", ignoreCase = true))
            }

            val targetNoteId = existingMasterNote?.id ?: 0L
            val now = System.currentTimeMillis()

            val finalNoteId = if (existingMasterNote != null) {
                studyNoteDao.updateStudyNote(
                    existingMasterNote.copy(
                        title = masterTitle,
                        subject = resolvedSubject,
                        course = resolvedCourse.ifBlank { existingMasterNote.course },
                        rawContent = compiledContent,
                        updatedAt = now
                    )
                )
                existingMasterNote.id
            } else {
                studyNoteDao.insertStudyNote(
                    StudyNoteEntity(
                        folderId = targetFolderId,
                        title = masterTitle,
                        subject = resolvedSubject,
                        course = resolvedCourse,
                        sourceAi = "Course Compiler",
                        rawContent = compiledContent,
                        collectionType = KnowledgeCollectionType.MULTI_LINER.code,
                        customLabel = "Course Master",
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }

            withContext(Dispatchers.Main) {
                val action = if (targetNoteId > 0L) "Updated" else "Compiled"
                _statusMessage.value = "$action Master Active Recall for $resolvedSubject (${parsedPapers.size} Sets, ${courseNotes.size} Notes)"
                openKnowledgeReader(finalNoteId)
            }
        }
    }

    fun openMcqDraftFromKnowledgeSet(note: StudyNoteEntity) {
        val draft = StudyNoteParser.convertNoteBlocksToMcqDraft(note)
        if (draft.isBlank()) {
            _statusMessage.value = "Add structured key-value lines or Multi-Liner cards first to generate MCQs."
            return
        }
        _pendingKnowledgeReturnNoteId.value = note.id
        _activeKnowledgeReaderNoteId.value = null
        _knowledgeEditorDraft.value = null
        _rawInputText.value = draft
        _parseResult.value = McqParser.parseWithResult(draft)
        appPreferences.setDraftRawText(draft)
        selectTab(4)
        _statusMessage.value = "Generated MCQ draft from \"${note.title}\" in Exam Tools"
    }

    fun returnFromKnowledgeMcqDraft(): Boolean {
        val noteId = _pendingKnowledgeReturnNoteId.value ?: return false
        _pendingKnowledgeReturnNoteId.value = null
        if (!navigateBackTab()) {
            selectTab(0, pushHistory = false)
        }
        _activeKnowledgeReaderNoteId.value = noteId
        return true
    }

    // --- Backup Vault & Cloud Sync (Google Drive / OneDrive / Streaming .examset.zip) ---

    private fun refreshCloudAccountStatuses() {
        _driveStatus.value = cloudAuthStore.buildAccountStatus(CloudProviderType.GOOGLE_DRIVE)
        _oneDriveStatus.value = cloudAuthStore.buildAccountStatus(CloudProviderType.ONEDRIVE)
        _linkedCloudFileStatus.value = cloudAuthStore.getLinkedCloudFileStatus()
    }

    fun refreshNetworkTimeSync() {
        viewModelScope.launch(Dispatchers.IO) {
            val synced = TrustedTimeManager.syncWithNetwork()
            withContext(Dispatchers.Main) {
                _statusMessage.value = if (synced) {
                    "Online network time synced"
                } else {
                    "Could not reach network time server — check internet connection"
                }
            }
        }
    }

    fun getCloudClientId(provider: CloudProviderType): String = cloudAuthStore.getClientId(provider)

    fun configureCloudClientId(provider: CloudProviderType, clientId: String) {
        cloudAuthStore.setClientId(provider, clientId)
        refreshCloudAccountStatuses()
        _statusMessage.value = "${provider.displayName} Client ID updated"
    }

    fun startCloudOAuth(context: Context, provider: CloudProviderType) {
        val intentRes = CloudBackupClients.buildAuthorizeIntent(provider, cloudAuthStore, context)
        intentRes.onSuccess { authIntent ->
            runCatching { context.startActivity(authIntent) }
                .onFailure { err ->
                    _backupOpState.value = BackupOperationState.Error("Unable to open browser: ${err.localizedMessage}")
                }
        }.onFailure { err ->
            _backupOpState.value = BackupOperationState.Error(err.localizedMessage ?: "OAuth setup required")
        }
    }

    fun handleOAuthRedirectUri(uri: Uri) {
        viewModelScope.launch {
            _backupOpState.value = BackupOperationState.Running("Completing cloud sign-in…", 0.5f)
            val res = CloudBackupClients.handleOAuthRedirect(uri, cloudAuthStore)
            refreshCloudAccountStatuses()
            res.onSuccess { provider ->
                _backupOpState.value = BackupOperationState.Success("Connected to ${provider.displayName}!")
                _statusMessage.value = "Connected to ${provider.displayName}"
                loadRemoteBackups(provider)
            }.onFailure { err ->
                _backupOpState.value = BackupOperationState.Error(err.localizedMessage ?: "Cloud sign-in failed")
            }
        }
    }

    fun disconnectCloud(provider: CloudProviderType) {
        cloudAuthStore.clearTokenBundle(provider)
        _remoteCloudBackups.value = _remoteCloudBackups.value.filter { it.provider != provider }
        refreshCloudAccountStatuses()
        _statusMessage.value = "Disconnected ${provider.displayName}"
    }

    fun setAutoBackupSchedule(schedule: AutoBackupSchedule) {
        _autoBackupSchedule.value = schedule
        cloudAuthStore.setAutoBackupSchedule(schedule)
        VaultAutoBackupWorker.scheduleAutoBackup(getApplication(), schedule)
        _statusMessage.value = "Auto backup: ${schedule.label}"
    }

    fun clearBackupOpState() {
        _backupOpState.value = BackupOperationState.Idle
    }

    fun createLocalVaultSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.Running("Creating verified vault snapshot…", 0.3f)
            try {
                val (snapshot, _) = VaultArchiveEngine.createLocalRollingSnapshot(
                    context = getApplication(),
                    db = db,
                    appPrefs = appPreferences,
                    themePrefs = themePreferences,
                    providerTag = "LOCAL_VAULT",
                    prefix = "vault"
                )
                cloudAuthStore.setLastLocalSnapshotAt(snapshot.createdAt)
                _backupOpState.value = BackupOperationState.Success(
                    "Snapshot saved: ${snapshot.fileName} (${snapshot.details})"
                )
            } catch (e: Exception) {
                _backupOpState.value = BackupOperationState.Error("Snapshot failed: ${e.localizedMessage}")
            }
        }
    }

    fun deleteLocalBackupSnapshot(snapshot: BackupSnapshotEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (snapshot.localPath.isNotBlank()) {
                runCatching { File(snapshot.localPath).delete() }
            }
            backupSnapshotDao.deleteSnapshotById(snapshot.id)
        }
    }

    fun exportVaultToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.Running("Streaming vault archive…", 0.2f)
            try {
                val tempFile = File(VaultArchiveEngine.getExportCacheDir(getApplication()), VaultArchiveEngine.defaultVaultFileName("export"))
                val manifest = VaultArchiveEngine.createVaultArchive(
                    context = getApplication(),
                    db = db,
                    appPrefs = appPreferences,
                    themePrefs = themePreferences,
                    destinationFile = tempFile
                ) { phase, prog ->
                    _backupOpState.value = BackupOperationState.Running(phase, prog)
                }
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(tempFile).use { input ->
                        input.copyTo(out, bufferSize = 16 * 1024)
                    }
                } ?: throw IllegalStateException("Unable to write to chosen destination.")

                backupSnapshotDao.insertSnapshot(
                    BackupSnapshotEntity(
                        provider = "SAF_EXPORT",
                        fileName = uri.lastPathSegment?.substringAfterLast('/') ?: tempFile.name,
                        localPath = "",
                        checksumSha256 = VaultArchiveEngine.computeFileSha256(tempFile),
                        papersCount = manifest.counts.papersCount,
                        notesCount = manifest.counts.notesCount,
                        foldersCount = manifest.counts.foldersCount,
                        attemptsCount = manifest.counts.attemptsCount,
                        sizeBytes = tempFile.length(),
                        status = "VERIFIED",
                        details = VaultArchiveEngine.buildSnapshotDetails(manifest.counts, "System / Cloud Picker")
                    )
                )
                runCatching { tempFile.delete() }
                _backupOpState.value = BackupOperationState.Success(
                    "Vault exported (${VaultArchiveEngine.formatCountsSummary(manifest.counts)})"
                )
            } catch (e: Exception) {
                _backupOpState.value = BackupOperationState.Error("Export failed: ${e.localizedMessage}")
            }
        }
    }

    fun exportWebVaultJsonToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.Running("Building Web .examset.json vault…", 0.35f)
            try {
                val app = getApplication<Application>()
                val rawJson = com.example.util.WebVaultJsonAdapter.createWebVaultJson(
                    context = app,
                    db = db,
                    appPrefs = appPreferences,
                    themePrefs = themePreferences
                )
                val out = app.contentResolver.openOutputStream(uri, "wt")
                    ?: app.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Unable to open destination file for writing.")
                out.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(rawJson)
                    writer.flush()
                }
                val bytes = rawJson.toByteArray(Charsets.UTF_8)
                val sha = VaultArchiveEngine.computeSha256Hex(bytes)
                val fileName = resolveDocumentDisplayName(uri).let {
                    if (it.endsWith(".json", ignoreCase = true)) it else "examset_web_vault.examset.json"
                }
                val paperCount = paperDao.getAllPapersOnce().size
                val noteCount = studyNoteDao.getAllStudyNotesOnce().size
                backupSnapshotDao.insertSnapshot(
                    BackupSnapshotEntity(
                        provider = "SAF_WEB_JSON",
                        fileName = fileName,
                        localPath = "",
                        checksumSha256 = sha,
                        papersCount = paperCount,
                        notesCount = noteCount,
                        foldersCount = appPreferences.getCustomFolders().size,
                        attemptsCount = paperDao.getAllAttemptsOnce().size,
                        sizeBytes = bytes.size.toLong(),
                        status = "VERIFIED",
                        details = "$paperCount sets • $noteCount knowledge • Web .examset.json"
                    )
                )
                _backupOpState.value = BackupOperationState.Success(
                    "Exported Web Vault (.examset.json) with SHA-256 verification!"
                )
            } catch (e: Exception) {
                _backupOpState.value = BackupOperationState.Error("Web JSON export failed: ${e.localizedMessage}")
            }
        }
    }

    private fun launchShareFileIntent(
        context: Context,
        file: File,
        title: String,
        mimeType: String,
        chooserTitle: String = title
    ) {
        runCatching {
            val uri = VaultArchiveEngine.getShareableVaultUri(context, file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                clipData = ClipData.newRawUri(title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
        }.onFailure { err ->
            _statusMessage.value = "Could not open share dialog: ${err.localizedMessage ?: "No compatible app found"}"
        }
    }

    private fun buildQuestionPaperForExport(entity: PaperEntity): QuestionPaper {
        val parsed = McqParser.parse(entity.rawText)
        return parsed.copy(
            id = entity.id,
            metadata = PaperMetadata(
                id = "paper-${entity.id}",
                title = entity.title,
                subject = entity.subject,
                topic = entity.topic,
                setNumber = entity.setNumber.ifBlank { "Set 1" },
                exam = entity.exam,
                folderId = entity.folderId,
                manualCategory = entity.manualCategory,
                rollNumber = if (entity.rollNumber.isNotBlank()) entity.rollNumber else ExamNumberGenerator.generateRollNumber(),
                bookletNumber = if (entity.bookletNumber.isNotBlank()) entity.bookletNumber else ExamNumberGenerator.generateBookletNumber(),
                durationMinutes = if (entity.durationMinutes > 0) entity.durationMinutes else 60,
                positiveMarks = if (entity.positiveMarks > 0.0) entity.positiveMarks else 4.0,
                negativeMarks = if (entity.negativeMarks >= 0.0) entity.negativeMarks else 1.0,
                unattendedMarks = if (entity.unattendedMarks >= 0.0) entity.unattendedMarks else 0.0,
                msqScoringPolicy = appPreferences.getPaperMsqPolicy(entity.id),
                createdAt = entity.createdAt
            )
        )
    }

    fun shareVaultArchive(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.Running("Preparing shareable .examset.zip…", 0.3f)
            try {
                val file = File(VaultArchiveEngine.getExportCacheDir(context), VaultArchiveEngine.defaultVaultFileName("share"))
                val manifest = VaultArchiveEngine.createVaultArchive(
                    context = context,
                    db = db,
                    appPrefs = appPreferences,
                    themePrefs = themePreferences,
                    destinationFile = file
                )
                val subject = "ExamSet Vault Backup (${VaultArchiveEngine.formatCountsSummary(manifest.counts)})"
                withContext(Dispatchers.Main) {
                    _backupOpState.value = BackupOperationState.Idle
                    launchShareFileIntent(
                        context = context,
                        file = file,
                        title = subject,
                        mimeType = "application/zip",
                        chooserTitle = "Share ExamSet Vault"
                    )
                }
            } catch (e: Throwable) {
                _backupOpState.value = BackupOperationState.Error("Share failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Crash-proof file-backed sharing for Question Sets / JSON / Text so Binder limits (~1 MB TransactionTooLargeException)
     * never crash large backups.
     */
    fun shareTextAsFile(context: Context, content: String, fileName: String, title: String, mimeType: String = "application/json") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val file = File(VaultArchiveEngine.getExportCacheDir(context), safeName)
                BufferedWriter(OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8), 32 * 1024).use { writer ->
                    writer.write(content)
                }
                withContext(Dispatchers.Main) {
                    launchShareFileIntent(
                        context = context,
                        file = file,
                        title = title,
                        mimeType = mimeType
                    )
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Unable to share file: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    /**
     * Memory-safe background streaming share for large question libraries (e.g. 23+ sets / 2,000+ long questions).
     * Streams JSON directly to a cached file one set at a time on Dispatchers.IO so Main thread ANRs and OOM crashes never occur.
     */
    fun shareAllPapersAsJsonFile(
        context: Context,
        selectedPaperIds: Set<Long>? = null,
        customTitle: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val papers = if (selectedPaperIds == null) {
                paperDao.getAllPapersOnce()
            } else {
                paperDao.getAllPapersOnce().filter { it.id in selectedPaperIds }
            }
            if (papers.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "No saved question sets to share."
                }
                return@launch
            }
            _backupOpState.value = BackupOperationState.Running("Preparing JSON for ${papers.size} question sets…", 0.1f)
            try {
                val firstPaper = papers.first()
                val title = customTitle ?: "ExamSet Backup (${papers.size} Question Sets)"
                val safeName = "examset_backup_${papers.size}_sets.json"
                val file = File(VaultArchiveEngine.getExportCacheDir(context), safeName)
                FileOutputStream(file).use { fos ->
                    ExamJsonHelper.streamBundleToOutputStream(
                        examTitle = firstPaper.title.ifBlank { "Examination Package" },
                        folderId = firstPaper.folderId,
                        papers = papers,
                        outputStream = fos,
                        onProgress = { idx, total ->
                            _backupOpState.value = BackupOperationState.Running(
                                "Packing set $idx of $total…",
                                (idx.toFloat() / total.coerceAtLeast(1)).coerceIn(0.1f, 0.95f)
                            )
                        },
                        paperBuilder = ::buildQuestionPaperForExport
                    )
                }
                withContext(Dispatchers.Main) {
                    _backupOpState.value = BackupOperationState.Idle
                    launchShareFileIntent(
                        context = context,
                        file = file,
                        title = title,
                        mimeType = "application/json",
                        chooserTitle = "Share Question Sets (.json)"
                    )
                }
            } catch (e: Throwable) {
                _backupOpState.value = BackupOperationState.Error("Share failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Unable to share backup: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    /**
     * Memory-safe background streaming share for formatted plain-text backups of large libraries.
     */
    fun shareAllPapersAsTextFile(
        context: Context,
        selectedPaperIds: Set<Long>? = null,
        customTitle: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val papers = if (selectedPaperIds == null) {
                paperDao.getAllPapersOnce()
            } else {
                paperDao.getAllPapersOnce().filter { it.id in selectedPaperIds }
            }
            if (papers.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "No saved question sets to share."
                }
                return@launch
            }
            _backupOpState.value = BackupOperationState.Running("Preparing text export for ${papers.size} sets…", 0.15f)
            try {
                val title = customTitle ?: "ExamSet Library (${papers.size} Sets)"
                val file = File(VaultArchiveEngine.getExportCacheDir(context), "examset_backup_${papers.size}_sets.txt")
                FileOutputStream(file).use { fos ->
                    streamPapersAsTextToOutputStream(papers, fos)
                }
                withContext(Dispatchers.Main) {
                    _backupOpState.value = BackupOperationState.Idle
                    launchShareFileIntent(
                        context = context,
                        file = file,
                        title = title,
                        mimeType = "text/plain",
                        chooserTitle = "Share Question Sets (.txt)"
                    )
                }
            } catch (e: Throwable) {
                _backupOpState.value = BackupOperationState.Error("Share failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Unable to share text backup: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    fun exportAllPapersToUriAsJson(uri: Uri, selectedPaperIds: Set<Long>? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val papers = if (selectedPaperIds == null) {
                paperDao.getAllPapersOnce()
            } else {
                paperDao.getAllPapersOnce().filter { it.id in selectedPaperIds }
            }
            if (papers.isEmpty()) {
                _backupOpState.value = BackupOperationState.Error("No saved question sets to export.")
                return@launch
            }
            _backupOpState.value = BackupOperationState.Running("Exporting ${papers.size} sets to JSON…", 0.1f)
            try {
                val firstPaper = papers.first()
                val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Unable to open destination file for writing.")
                out.use { stream ->
                    ExamJsonHelper.streamBundleToOutputStream(
                        examTitle = firstPaper.title.ifBlank { "Examination Package" },
                        folderId = firstPaper.folderId,
                        papers = papers,
                        outputStream = stream,
                        onProgress = { idx, total ->
                            _backupOpState.value = BackupOperationState.Running(
                                "Writing set $idx of $total…",
                                (idx.toFloat() / total.coerceAtLeast(1)).coerceIn(0.1f, 0.95f)
                            )
                        },
                        paperBuilder = ::buildQuestionPaperForExport
                    )
                }
                _backupOpState.value = BackupOperationState.Success("Exported ${papers.size} question sets as JSON!")
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Saved ${papers.size} question sets as JSON"
                }
            } catch (e: Throwable) {
                _backupOpState.value = BackupOperationState.Error("JSON export failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    fun exportAllPapersToUriAsText(uri: Uri, selectedPaperIds: Set<Long>? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val papers = if (selectedPaperIds == null) {
                paperDao.getAllPapersOnce()
            } else {
                paperDao.getAllPapersOnce().filter { it.id in selectedPaperIds }
            }
            if (papers.isEmpty()) {
                _backupOpState.value = BackupOperationState.Error("No saved question sets to export.")
                return@launch
            }
            _backupOpState.value = BackupOperationState.Running("Exporting ${papers.size} sets to TXT…", 0.15f)
            try {
                val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Unable to open destination file for writing.")
                out.use { stream ->
                    streamPapersAsTextToOutputStream(papers, stream)
                }
                _backupOpState.value = BackupOperationState.Success("Exported ${papers.size} question sets as TXT!")
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Saved ${papers.size} question sets as TXT"
                }
            } catch (e: Throwable) {
                _backupOpState.value = BackupOperationState.Error("TXT export failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun streamPapersAsTextToOutputStream(papers: List<PaperEntity>, outputStream: java.io.OutputStream) {
        BufferedWriter(OutputStreamWriter(BufferedOutputStream(outputStream, 32 * 1024), Charsets.UTF_8)).use { writer ->
            papers.forEachIndexed { index, entity ->
                writer.write("========================================\n")
                writer.write("EXAM SET ${index + 1}: ${entity.title}\n")
                writer.write("Subject: ${entity.subject} | Set: ${entity.setNumber}\n")
                if (entity.topic.isNotBlank()) writer.write("Topic: ${entity.topic}\n")
                if (entity.exam.isNotBlank()) writer.write("Target Exam: ${entity.exam}\n")
                writer.write("Questions: ${entity.questionCount}\n")
                writer.write("========================================\n\n")
                writer.write(entity.rawText.trim())
                writer.write("\n\n\n")
            }
            writer.flush()
        }
    }

    fun inspectBackupUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.Running("Verifying backup manifest & checksums…", 0.4f)
            try {
                val preview = VaultArchiveEngine.inspectVaultUri(getApplication(), uri)
                _restorePreview.value = preview
                _backupOpState.value = BackupOperationState.Idle
            } catch (e: Exception) {
                _backupOpState.value = BackupOperationState.Error("Unable to inspect backup: ${e.localizedMessage}")
            }
        }
    }

    fun inspectLocalBackupSnapshot(snapshot: BackupSnapshotEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(snapshot.localPath)
                if (!file.exists()) {
                    _backupOpState.value = BackupOperationState.Error("Local snapshot file no longer exists on disk.")
                    return@launch
                }
                val preview = VaultArchiveEngine.inspectLocalFile(file, snapshot.fileName)
                _restorePreview.value = preview
            } catch (e: Exception) {
                _backupOpState.value = BackupOperationState.Error("Unable to read snapshot: ${e.localizedMessage}")
            }
        }
    }

    private fun deletePreviewTempFileIfInExportCache(preview: com.example.model.RestorePreview?) {
        val temp = preview?.tempFile ?: return
        val exportDirPath = runCatching { VaultArchiveEngine.getExportCacheDir(getApplication()).canonicalPath }.getOrNull()
        val tempParentPath = runCatching { temp.parentFile?.canonicalPath }.getOrNull()
        if (exportDirPath != null && tempParentPath == exportDirPath) {
            runCatching { temp.delete() }
        }
    }

    fun dismissRestorePreview() {
        val prev = _restorePreview.value
        _restorePreview.value = null
        deletePreviewTempFileIfInExportCache(prev)
    }

    fun confirmRestore(mode: RestoreMode, mergeAppSettings: Boolean = true) {
        val preview = _restorePreview.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.Running("Restoring library (${mode.label})…", 0.5f)
            try {
                val restored = VaultArchiveEngine.restoreFromPreview(
                    context = getApplication(),
                    db = db,
                    appPrefs = appPreferences,
                    themePrefs = themePreferences,
                    preview = preview,
                    mode = mode,
                    mergeAppSettings = mergeAppSettings
                )
                val refreshedFolders = appPreferences.getCustomFolders()
                val refreshedPresets = appPreferences.getPromptPresets()
                val refreshedVocabDecks = appPreferences.getVocabularyDecks()
                val refreshedVocabSrs = appPreferences.getAllVocabularySrsMaps()
                val refreshedFlashcardSrs = appPreferences.getAllFlashcardSrsMaps()
                val refreshedVariant = appPreferences.getPreferredEnglishVariant()
                val refreshedReminder = appPreferences.isVocabularyReminderEnabled()
                val refreshedQDraft = appPreferences.getQuestionPromptDraft()
                val refreshedKDraft = appPreferences.getKnowledgePromptDraft()
                val refreshedFDraft = appPreferences.getFlashcardPromptDraft()
                val refreshedVDraft = appPreferences.getVocabularyPromptDraft()
                val refreshedQPresetId = appPreferences.getActivePromptPresetId(PromptPresetMode.QUESTION_SET)
                    ?.takeIf { id -> refreshedPresets.any { it.id == id && it.mode == PromptPresetMode.QUESTION_SET } }
                val refreshedKPresetId = appPreferences.getActivePromptPresetId(PromptPresetMode.KNOWLEDGE_SET)
                    ?.takeIf { id -> refreshedPresets.any { it.id == id && it.mode == PromptPresetMode.KNOWLEDGE_SET } }
                val refreshedFPresetId = appPreferences.getActivePromptPresetId(PromptPresetMode.FLASHCARD_SET)
                    ?.takeIf { id -> refreshedPresets.any { it.id == id && it.mode == PromptPresetMode.FLASHCARD_SET } }
                val refreshedVPresetId = appPreferences.getActivePromptPresetId(PromptPresetMode.VOCABULARY)
                    ?.takeIf { id -> refreshedPresets.any { it.id == id && it.mode == PromptPresetMode.VOCABULARY } }
                val refreshedActiveFolderId = appPreferences.getLastActiveFolderId().let { id ->
                    if (id == "all" || refreshedFolders.any { it.id == id }) id else "all"
                }
                val refreshedShuffle = appPreferences.getShuffleOptions()
                val refreshedDismissedTips = appPreferences.getDismissedFormatTips()
                val refreshedKnowledgeCourses = appPreferences.getKnowledgeCoursesMap()
                withContext(Dispatchers.Main) {
                    _folders.value = refreshedFolders
                    _lastActiveFolderId.value = refreshedActiveFolderId
                    _knowledgeCoursesMap.value = refreshedKnowledgeCourses
                    _promptPresets.value = refreshedPresets
                    activeQuestionPresetId = refreshedQPresetId
                    activeKnowledgePresetId = refreshedKPresetId
                    activeFlashcardPresetId = refreshedFPresetId
                    activeVocabularyPresetId = refreshedVPresetId
                    _vocabularyDecks.value = refreshedVocabDecks
                    _vocabularySrsByDeck.value = refreshedVocabSrs
                    _flashcardSrsByNote.value = refreshedFlashcardSrs
                    _preferredEnglishVariant.value = refreshedVariant
                    _vocabularyReminderEnabled.value = refreshedReminder
                    refreshedQDraft?.let { questionPromptState.applyConfig(it, preserveTopic = false) }
                    refreshedKDraft?.let { knowledgePromptState.applyConfig(it, preserveTopic = false) }
                    refreshedFDraft?.let { flashcardPromptState.applyConfig(it, preserveTopic = false) }
                    refreshedVDraft?.let { vocabularyPromptState.applyConfig(it, preserveTopic = false) }
                    com.example.util.VocabularyDailyReminderWorker.syncSchedule(getApplication(), refreshedReminder)
                    refreshActiveWordOfTheDay()
                    _themeMode.value = themePreferences.getThemeMode()
                    _colorSchemePreset.value = themePreferences.getColorSchemePreset()
                    _headwordInbox.value = appPreferences.getHeadwordInbox()
                    _dismissedWordKeys.value = appPreferences.getDismissedWordKeys()
                    _examCountdowns.value = appPreferences.getExamCountdowns()
                    _studyTodos.value = appPreferences.getStudyTodos()
                    focusPomodoroController.reloadPreferencesAfterRestore()
                    _countdownNowMillis.value = TrustedTimeManager.getTrustedTimeMillis()
                    _fontScaleMode.value = appPreferences.getFontScaleMode()
                    _fontSizeScale.value = appPreferences.getFontSizeScale()
                    _cbtFontScaleStep.value = appPreferences.getCbtFontScaleStep()
                    _dailyGoalMinutes.value = appPreferences.getDailyGoalMinutes()
                    _shuffleOptions.value = refreshedShuffle
                    _dismissedFormatTips.value = refreshedDismissedTips
                    _brainLabStats.value = appPreferences.getBrainLabStats()
                    _brainLabSettings.value = appPreferences.getBrainLabSettings()
                    _restorePreview.value = null
                    deletePreviewTempFileIfInExportCache(preview)
                    _backupOpState.value = BackupOperationState.Success(
                        "Restored ${VaultArchiveEngine.formatCountsSummary(restored)}!"
                    )
                    _statusMessage.value = "Vault restore complete!"
                }
            } catch (e: Exception) {
                _backupOpState.value = BackupOperationState.Error("Restore failed: ${e.localizedMessage}")
            }
        }
    }

    /**
     * One-tap "Sync Everything" orchestration: creates a verified local vault snapshot containing
     * all papers, notes, vocab, presets, countdowns, todos, drafts, and settings, then pushes it
     * to every connected sync target (Linked Cloud File, Google Drive, OneDrive).
     */
    fun syncAllConnectedTargets() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val linked = cloudAuthStore.getLinkedCloudFileStatus()
            val driveConnected = cloudAuthStore.buildAccountStatus(CloudProviderType.GOOGLE_DRIVE).isConnected
            val oneDriveConnected = cloudAuthStore.buildAccountStatus(CloudProviderType.ONEDRIVE).isConnected

            _backupOpState.value = BackupOperationState.Running("Packing complete library, presets & settings…", 0.25f)
            try {
                val (snapshot, vaultFile) = VaultArchiveEngine.createLocalRollingSnapshot(
                    context = app,
                    db = db,
                    appPrefs = appPreferences,
                    themePrefs = themePreferences,
                    providerTag = "LOCAL_VAULT",
                    prefix = "sync_all"
                )
                val syncedTargets = mutableListOf("Local Snapshot")
                val targetErrors = mutableListOf<String>()

                if (linked.isLinked) {
                    _backupOpState.value = BackupOperationState.Running("Syncing to ${linked.displayName.ifBlank { "Linked Cloud File" }}…", 0.5f)
                    val uri = Uri.parse(linked.uriString)
                    val displayName = linked.displayName.ifBlank { "ExamSet_Live_Sync.examset.zip" }
                    try {
                        val outStream = app.contentResolver.openOutputStream(uri, "wt")
                            ?: app.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("Cannot open linked file for writing")
                        outStream.use { out ->
                            FileInputStream(vaultFile).use { input ->
                                input.copyTo(out, bufferSize = 16 * 1024)
                            }
                            out.flush()
                        }
                        val syncedAt = TrustedTimeManager.getTrustedTimeMillis()
                        cloudAuthStore.saveLinkedCloudFile(uri.toString(), displayName, syncedAt)
                        syncedTargets += "Cloud File"
                    } catch (e: Exception) {
                        val msg = e.localizedMessage ?: "Linked file write failed"
                        cloudAuthStore.setLinkedCloudFileError(msg)
                        targetErrors += "Cloud File: $msg"
                    }
                }

                if (driveConnected) {
                    _backupOpState.value = BackupOperationState.Running("Uploading to Google Drive…", 0.72f)
                    val res = CloudBackupClients.uploadBackupFile(
                        provider = CloudProviderType.GOOGLE_DRIVE,
                        authStore = cloudAuthStore,
                        vaultFile = vaultFile,
                        context = app
                    )
                    res.onSuccess { remote ->
                        backupSnapshotDao.insertSnapshot(
                            BackupSnapshotEntity(
                                provider = CloudProviderType.GOOGLE_DRIVE.code,
                                fileName = remote.name,
                                localPath = vaultFile.absolutePath,
                                remoteFileId = remote.remoteId,
                                checksumSha256 = snapshot.checksumSha256,
                                papersCount = snapshot.papersCount,
                                notesCount = snapshot.notesCount,
                                foldersCount = snapshot.foldersCount,
                                attemptsCount = snapshot.attemptsCount,
                                sizeBytes = remote.sizeBytes,
                                status = "UPLOADED",
                                details = snapshot.details
                            )
                        )
                        syncedTargets += "Google Drive"
                    }.onFailure { err ->
                        targetErrors += "Drive: ${err.localizedMessage ?: "upload failed"}"
                    }
                }

                if (oneDriveConnected) {
                    _backupOpState.value = BackupOperationState.Running("Uploading to OneDrive…", 0.88f)
                    val res = CloudBackupClients.uploadBackupFile(
                        provider = CloudProviderType.ONEDRIVE,
                        authStore = cloudAuthStore,
                        vaultFile = vaultFile,
                        context = app
                    )
                    res.onSuccess { remote ->
                        backupSnapshotDao.insertSnapshot(
                            BackupSnapshotEntity(
                                provider = CloudProviderType.ONEDRIVE.code,
                                fileName = remote.name,
                                localPath = vaultFile.absolutePath,
                                remoteFileId = remote.remoteId,
                                checksumSha256 = snapshot.checksumSha256,
                                papersCount = snapshot.papersCount,
                                notesCount = snapshot.notesCount,
                                foldersCount = snapshot.foldersCount,
                                attemptsCount = snapshot.attemptsCount,
                                sizeBytes = remote.sizeBytes,
                                status = "UPLOADED",
                                details = snapshot.details
                            )
                        )
                        syncedTargets += "OneDrive"
                    }.onFailure { err ->
                        targetErrors += "OneDrive: ${err.localizedMessage ?: "upload failed"}"
                    }
                }

                refreshCloudAccountStatuses()
                if (driveConnected) loadRemoteBackups(CloudProviderType.GOOGLE_DRIVE)
                if (oneDriveConnected) loadRemoteBackups(CloudProviderType.ONEDRIVE)

                if (targetErrors.isEmpty()) {
                    _backupOpState.value = BackupOperationState.Success(
                        "Synced everything to ${syncedTargets.joinToString(" + ")} (${appPreferences.getPromptPresets().size} presets included)."
                    )
                } else {
                    _backupOpState.value = BackupOperationState.Error(
                        "Synced ${syncedTargets.joinToString(", ")}; errors: ${targetErrors.joinToString(" • ")}"
                    )
                }
            } catch (e: Exception) {
                _backupOpState.value = BackupOperationState.Error("Sync failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    fun exportPromptPresetsPackToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.Running("Exporting Prompt Presets & drafts pack…", 0.4f)
            try {
                val packJson = VaultArchiveEngine.exportPromptPresetsPackJson(appPreferences)
                val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Unable to open destination file.")
                out.bufferedWriter(Charsets.UTF_8).use { it.write(packJson) }
                val count = appPreferences.getPromptPresets().size
                _backupOpState.value = BackupOperationState.Success("Exported $count AI Prompt Presets + active drafts (.json)")
            } catch (e: Exception) {
                _backupOpState.value = BackupOperationState.Error("Preset pack export failed: ${e.localizedMessage}")
            }
        }
    }

    fun importPromptPresetsPackFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.Running("Importing Prompt Presets pack…", 0.4f)
            try {
                val rawJson = getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: throw IllegalStateException("Unable to read selected preset pack file.")
                val summary = VaultArchiveEngine.importPromptPresetsPackJson(rawJson, appPreferences, overwriteAll = false)
                val refreshedPresets = appPreferences.getPromptPresets()
                val refreshedQId = appPreferences.getActivePromptPresetId(PromptPresetMode.QUESTION_SET)
                    ?.takeIf { id -> refreshedPresets.any { it.id == id && it.mode == PromptPresetMode.QUESTION_SET } }
                val refreshedKId = appPreferences.getActivePromptPresetId(PromptPresetMode.KNOWLEDGE_SET)
                    ?.takeIf { id -> refreshedPresets.any { it.id == id && it.mode == PromptPresetMode.KNOWLEDGE_SET } }
                val refreshedFId = appPreferences.getActivePromptPresetId(PromptPresetMode.FLASHCARD_SET)
                    ?.takeIf { id -> refreshedPresets.any { it.id == id && it.mode == PromptPresetMode.FLASHCARD_SET } }
                val refreshedVId = appPreferences.getActivePromptPresetId(PromptPresetMode.VOCABULARY)
                    ?.takeIf { id -> refreshedPresets.any { it.id == id && it.mode == PromptPresetMode.VOCABULARY } }
                val qDraft = appPreferences.getQuestionPromptDraft()
                val kDraft = appPreferences.getKnowledgePromptDraft()
                val fDraft = appPreferences.getFlashcardPromptDraft()
                val vDraft = appPreferences.getVocabularyPromptDraft()

                withContext(Dispatchers.Main) {
                    _promptPresets.value = refreshedPresets
                    activeQuestionPresetId = refreshedQId
                    activeKnowledgePresetId = refreshedKId
                    activeFlashcardPresetId = refreshedFId
                    activeVocabularyPresetId = refreshedVId
                    qDraft?.let { questionPromptState.applyConfig(it, preserveTopic = false) }
                    kDraft?.let { knowledgePromptState.applyConfig(it, preserveTopic = false) }
                    fDraft?.let { flashcardPromptState.applyConfig(it, preserveTopic = false) }
                    vDraft?.let { vocabularyPromptState.applyConfig(it, preserveTopic = false) }
                    _backupOpState.value = BackupOperationState.Success(
                        "Imported presets pack: +${summary.addedCount} added, ${summary.updatedCount} updated (${refreshedPresets.size} total)."
                    )
                    _statusMessage.value = "Prompt presets imported"
                }
            } catch (e: Exception) {
                _backupOpState.value = BackupOperationState.Error("Preset pack import failed: ${e.localizedMessage}")
            }
        }
    }

    fun syncVaultToCloud(provider: CloudProviderType) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.Running("Packing & uploading to ${provider.displayName}…", 0.35f)
            try {
                val (snapshot, file) = VaultArchiveEngine.createLocalRollingSnapshot(
                    context = getApplication(),
                    db = db,
                    appPrefs = appPreferences,
                    themePrefs = themePreferences,
                    providerTag = "LOCAL_VAULT",
                    prefix = "sync"
                )
                _backupOpState.value = BackupOperationState.Running("Uploading to ${provider.displayName}…", 0.7f)
                val uploadRes = CloudBackupClients.uploadBackupFile(
                    provider = provider,
                    authStore = cloudAuthStore,
                    vaultFile = file,
                    context = getApplication()
                )
                refreshCloudAccountStatuses()
                uploadRes.onSuccess { remote ->
                    backupSnapshotDao.insertSnapshot(
                        BackupSnapshotEntity(
                            provider = provider.code,
                            fileName = remote.name,
                            localPath = file.absolutePath,
                            remoteFileId = remote.remoteId,
                            checksumSha256 = snapshot.checksumSha256,
                            papersCount = snapshot.papersCount,
                            notesCount = snapshot.notesCount,
                            foldersCount = snapshot.foldersCount,
                            attemptsCount = snapshot.attemptsCount,
                            sizeBytes = remote.sizeBytes,
                            status = "UPLOADED",
                            details = snapshot.details
                        )
                    )
                    _backupOpState.value = BackupOperationState.Success("Uploaded to ${provider.displayName}: ${remote.name}")
                    loadRemoteBackups(provider)
                }.onFailure { err ->
                    _backupOpState.value = BackupOperationState.Error(err.localizedMessage ?: "Upload failed")
                }
            } catch (e: Exception) {
                _backupOpState.value = BackupOperationState.Error("Cloud sync error: ${e.localizedMessage}")
            }
        }
    }

    fun loadRemoteBackups(provider: CloudProviderType) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = CloudBackupClients.listRemoteBackups(
                provider = provider,
                authStore = cloudAuthStore,
                context = getApplication()
            )
            refreshCloudAccountStatuses()
            res.onSuccess { files ->
                val others = _remoteCloudBackups.value.filter { it.provider != provider }
                _remoteCloudBackups.value = (others + files).sortedByDescending { it.modifiedAt }
            }.onFailure { err ->
                _backupOpState.value = BackupOperationState.Error(err.localizedMessage ?: "Failed to list backups")
            }
        }
    }

    fun restoreFromRemoteBackup(remoteFile: RemoteCloudBackupFile) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.Running("Downloading ${remoteFile.name}…", 0.4f)
            val dlRes = CloudBackupClients.downloadRemoteBackup(getApplication(), remoteFile, cloudAuthStore)
            dlRes.onSuccess { localFile ->
                try {
                    val preview = VaultArchiveEngine.inspectLocalFile(localFile, "${remoteFile.provider.displayName}: ${remoteFile.name}")
                    _restorePreview.value = preview
                    _backupOpState.value = BackupOperationState.Idle
                } catch (e: Exception) {
                    _backupOpState.value = BackupOperationState.Error("Downloaded file invalid: ${e.localizedMessage}")
                }
            }.onFailure { err ->
                _backupOpState.value = BackupOperationState.Error(err.localizedMessage ?: "Cloud download failed")
            }
        }
    }

    fun deleteRemoteBackup(remoteFile: RemoteCloudBackupFile) {
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.Running("Deleting ${remoteFile.name} from ${remoteFile.provider.displayName}…", 0.5f)
            val res = CloudBackupClients.deleteRemoteBackup(getApplication(), remoteFile, cloudAuthStore)
            refreshCloudAccountStatuses()
            res.onSuccess {
                _remoteCloudBackups.value = _remoteCloudBackups.value.filterNot {
                    it.remoteId == remoteFile.remoteId && it.provider == remoteFile.provider
                }
                _backupOpState.value = BackupOperationState.Success(
                    "Deleted \"${remoteFile.name}\" from ${remoteFile.provider.displayName}."
                )
            }.onFailure { err ->
                _backupOpState.value = BackupOperationState.Error(
                    err.localizedMessage ?: "Unable to delete remote backup"
                )
            }
        }
    }

    fun linkAndSyncCloudFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val displayName = resolveDocumentDisplayName(uri)
            _backupOpState.value = BackupOperationState.Running("Linking & syncing $displayName…", 0.25f)
            try {
                // Persist read+write URI grant so 1-tap Sync Now & background WorkManager Auto-Sync keep working across restarts
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }

                writeVaultToLinkedUri(uri, displayName)
            } catch (e: Exception) {
                val msg = "Unable to link cloud file: ${e.localizedMessage}"
                cloudAuthStore.setLinkedCloudFileError(msg)
                refreshCloudAccountStatuses()
                _backupOpState.value = BackupOperationState.Error(msg)
            }
        }
    }

    fun syncLinkedCloudFileNow() {
        val linked = cloudAuthStore.getLinkedCloudFileStatus()
        if (!linked.isLinked) {
            _backupOpState.value = BackupOperationState.Error("No cloud file linked yet. Tap \"Link Cloud File\" to pick a Drive / OneDrive / Files destination.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val uri = Uri.parse(linked.uriString)
            val name = linked.displayName.ifBlank { "ExamSet_Live_Sync.examset.zip" }
            _backupOpState.value = BackupOperationState.Running("Syncing vault to $name…", 0.3f)
            try {
                writeVaultToLinkedUri(uri, name)
            } catch (sec: SecurityException) {
                val msg = "File permission expired for $name. Tap \"Change File\" to re-link your cloud file."
                cloudAuthStore.setLinkedCloudFileError(msg)
                refreshCloudAccountStatuses()
                _backupOpState.value = BackupOperationState.Error(msg)
            } catch (e: Exception) {
                val msg = "Linked cloud sync failed: ${e.localizedMessage}"
                cloudAuthStore.setLinkedCloudFileError(msg)
                refreshCloudAccountStatuses()
                _backupOpState.value = BackupOperationState.Error(msg)
            }
        }
    }

    fun restoreFromLinkedCloudFile() {
        val linked = cloudAuthStore.getLinkedCloudFileStatus()
        if (!linked.isLinked) {
            _backupOpState.value = BackupOperationState.Error("No linked cloud file found.")
            return
        }
        val uri = Uri.parse(linked.uriString)
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.Running("Inspecting ${linked.displayName}…", 0.4f)
            try {
                val preview = VaultArchiveEngine.inspectVaultUri(getApplication(), uri)
                _restorePreview.value = preview
                _backupOpState.value = BackupOperationState.Idle
            } catch (sec: SecurityException) {
                val msg = "Read permission expired for ${linked.displayName}. Tap \"Change File\" or \"Restore Vault / JSON\"."
                cloudAuthStore.setLinkedCloudFileError(msg)
                refreshCloudAccountStatuses()
                _backupOpState.value = BackupOperationState.Error(msg)
            } catch (e: Exception) {
                _backupOpState.value = BackupOperationState.Error("Unable to read linked cloud file: ${e.localizedMessage}")
            }
        }
    }

    fun unlinkCloudFile() {
        val linked = cloudAuthStore.getLinkedCloudFileStatus()
        if (linked.isLinked) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                getApplication<Application>().contentResolver.releasePersistableUriPermission(Uri.parse(linked.uriString), flags)
            }
        }
        cloudAuthStore.clearLinkedCloudFile()
        refreshCloudAccountStatuses()
        _statusMessage.value = "Unlinked cloud sync file"
    }

    private suspend fun writeVaultToLinkedUri(uri: Uri, displayName: String) {
        val app = getApplication<Application>()
        val tempFile = File(VaultArchiveEngine.getExportCacheDir(app), VaultArchiveEngine.defaultVaultFileName("cloudsync"))
        try {
            val manifest = VaultArchiveEngine.createVaultArchive(
                context = app,
                db = db,
                appPrefs = appPreferences,
                themePrefs = themePreferences,
                destinationFile = tempFile
            ) { phase, prog ->
                _backupOpState.value = BackupOperationState.Running(phase, prog * 0.7f)
            }
            _backupOpState.value = BackupOperationState.Running("Writing to $displayName…", 0.85f)
            val outStream = app.contentResolver.openOutputStream(uri, "wt")
                ?: app.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Cannot open linked cloud file for writing. Tap \"Change File\" to re-link.")
            outStream.use { out ->
                FileInputStream(tempFile).use { input ->
                    input.copyTo(out, bufferSize = 16 * 1024)
                }
                out.flush()
            }
            val sha = VaultArchiveEngine.computeFileSha256(tempFile)
            val syncedAt = TrustedTimeManager.getTrustedTimeMillis()
            cloudAuthStore.saveLinkedCloudFile(uri.toString(), displayName, syncedAt)
            backupSnapshotDao.insertSnapshot(
                BackupSnapshotEntity(
                    provider = "CLOUD_FILE",
                    fileName = displayName,
                    localPath = "",
                    checksumSha256 = sha,
                    papersCount = manifest.counts.papersCount,
                    notesCount = manifest.counts.notesCount,
                    foldersCount = manifest.counts.foldersCount,
                    attemptsCount = manifest.counts.attemptsCount,
                    sizeBytes = tempFile.length(),
                    status = "SYNCED",
                    details = VaultArchiveEngine.buildSnapshotDetails(manifest.counts, displayName)
                )
            )
            refreshCloudAccountStatuses()
            _backupOpState.value = BackupOperationState.Success(
                "Synced $displayName (${VaultArchiveEngine.formatCountsSummary(manifest.counts)})"
            )
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun resolveDocumentDisplayName(uri: Uri): String {
        val app = getApplication<Application>()
        runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "ExamSet_Live_Sync.examset.zip"
    }

    fun findFolderByPath(path: String): FolderItem? {
        return resolveFolderBySubjectPath(path, _folders.value)
    }

    /**
     * Dynamically parses a slash-separated or arrow-separated folder path
     * (e.g. "UPSC CSE / Prelims / GS Paper 1" or "SSC / CGL / Tier 1" or "State PSC / Bihar (BPSC) / CCE Prelims"),
     * verifies or creates each folder in the hierarchy on-the-fly, and returns the leaf FolderItem.
     * Supports `//` as an escaped literal slash (e.g. "UPSC//Interview" creates a single folder named "UPSC/Interview").
     */
    fun resolveOrCreateFolderPath(
        path: String,
        baseParentId: String? = null,
        preferredColor: String? = null
    ): FolderItem? {
        val cleanPath = path.trim()
        if (cleanPath.isBlank() || cleanPath.equals("General", ignoreCase = true)) return null

        val placeholder = "\u0000LITERAL_SLASH\u0000"
        val normalized = cleanPath.replace("//", placeholder)

        val segments = normalized.split("/", ">")
            .map { it.replace(placeholder, "/").trim() }
            .filter { it.isNotBlank() }

        if (segments.isEmpty()) return null

        var currentParentId: String? = baseParentId
        var lastFolder: FolderItem? = null

        for (segment in segments) {
            val existing = _folders.value.find { f ->
                f.parentId == currentParentId && f.name.equals(segment, ignoreCase = true)
            }

            if (existing != null) {
                lastFolder = existing
                currentParentId = existing.id
            } else {
                val color = preferredColor ?: pickFolderColorForSegment(segment)
                val created = createFolderDirect(name = segment, color = color, parentId = currentParentId)
                if (created != null) {
                    lastFolder = created
                    currentParentId = created.id
                }
            }
        }

        if (segments.size > 1 && lastFolder != null) {
            val fullPath = getFullFolderPath(lastFolder.id)
            _statusMessage.value = "Created hierarchy: $fullPath"
        }

        return lastFolder
    }

    private fun pickFolderColorForSegment(segment: String): String {
        val s = segment.lowercase()
        return when {
            "upsc" in s || "civil" in s || "ias" in s -> "#0284C7"
            "ssc" in s || "cgl" in s || "chsl" in s -> "#2563EB"
            "rrb" in s || "railway" in s || "ntpc" in s || "alp" in s -> "#D97706"
            "engineering" in s || "je" in s || "ae" in s || "gate" in s || "ese" in s || "pwd" in s -> "#0D9488"
            "state" in s || "psc" in s || "bpsc" in s || "uppsc" in s || "mppsc" in s || "ras" in s || "mpsc" in s || "tnpsc" in s -> "#059669"
            "bank" in s || "ibps" in s || "sbi" in s || "rbi" in s -> "#7C3AED"
            "medical" in s || "neet" in s || "aiims" in s || "norcet" in s -> "#059669"
            "defence" in s || "defense" in s || "nda" in s || "cds" in s || "police" in s || "si" in s -> "#DC2626"
            "teaching" in s || "net" in s || "tet" in s -> "#4F46E5"
            "assignment" in s || "essay" in s || "subjective" in s -> "#7C3AED"
            "module" in s || "unit" in s || "block" in s || "chapter" in s -> "#0D9488"
            "course" in s || "semester" in s || "term" in s || "degree" in s -> "#0284C7"
            "pyq" in s || "previous" in s || "archive" in s -> "#D97706"
            "mock" in s || "test" in s || "quiz" in s -> "#2563EB"
            "revision" in s || "notes" in s || "summary" in s -> "#059669"
            else -> "#0284C7"
        }
    }

    /**
     * Loads a complete Testbook-style curated UPSC CSE General Studies 1 series,
     * organizing mock tests, official previous year papers (PYQ), and sectional tests
     * into a clean multi-level hierarchy: UPSC CSE > Prelims GS 1 > Mocks / PYQs / Sectionals.
     */
    fun loadCuratedUpscGs1Series(onComplete: (folderId: String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Create root folder "UPSC CSE"
                val upscRoot = createFolderDirect("UPSC CSE", "#0284C7", null)
                val upscRootId = upscRoot?.id

                // 2. Create subfolder "Prelims GS 1"
                val gs1Folder = createFolderDirect("Prelims GS 1", "#2563EB", upscRootId)
                val gs1Id = gs1Folder?.id

                // 3. Create classified subfolders under "Prelims GS 1"
                val mockFolder = createFolderDirect("Mock Tests (FLT)", "#2563EB", gs1Id)
                val pyqFolder = createFolderDirect("Previous Year Papers (PYQ)", "#D97706", gs1Id)
                val sectionalFolder = createFolderDirect("Sectional Tests", "#059669", gs1Id)

                // 4. Helper to insert paper if not already present
                suspend fun insertIfMissing(raw: String, folderId: String?) {
                    val parsed = McqParser.parseWithResult(raw)
                    val p = parsed.paper
                    val existing = paperDao.findPaperByMetadata(p.metadata.title, p.metadata.subject, p.metadata.setNumber)
                    if (existing == null) {
                        val newEntity = PaperEntity(
                            title = p.metadata.title,
                            subject = p.metadata.subject,
                            topic = p.metadata.topic,
                            setNumber = p.metadata.setNumber,
                            exam = p.metadata.exam,
                            questionCount = p.questions.size,
                            answerKeyCount = p.questions.count { it.correctAnswer != null },
                            explanationCount = p.questions.count { !it.explanation.isNullOrBlank() },
                            rawText = raw,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            folderId = folderId,
                            rollNumber = ExamNumberGenerator.generateRollNumber(),
                            bookletNumber = ExamNumberGenerator.generateBookletNumber(),
                            durationMinutes = p.metadata.durationMinutes,
                            positiveMarks = p.metadata.positiveMarks,
                            negativeMarks = p.metadata.negativeMarks
                        )
                        paperDao.insertPaper(newEntity)
                    }
                }

                insertIfMissing(SampleData.SAMPLE_UPSC_GS1_FULL_MOCK_1, mockFolder?.id ?: gs1Id)
                insertIfMissing(SampleData.SAMPLE_UPSC_GS1_PYQ_2024, pyqFolder?.id ?: gs1Id)
                insertIfMissing(SampleData.SAMPLE_UPSC_GS1_PYQ_2023, pyqFolder?.id ?: gs1Id)
                insertIfMissing(SampleData.SAMPLE_UPSC_GS1_SECTIONAL_POLITY, sectionalFolder?.id ?: gs1Id)

                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Loaded Curated UPSC CSE GS 1 Series!"
                    if (gs1Id != null) {
                        onComplete(gs1Id)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Error loading UPSC series: ${e.localizedMessage}"
                }
            }
        }
    }

    fun selectTab(index: Int, pushHistory: Boolean = true) {
        val current = _selectedTab.value
        if (current == index) return
        if (pushHistory) {
            val stack = _tabBackStack.value.toMutableList()
            if (stack.lastOrNull() != current) {
                stack.add(current)
            }
            if (stack.size > 20) {
                stack.removeAt(0)
            }
            _tabBackStack.value = stack
        }
        if (index != 4) {
            _pendingKnowledgeReturnNoteId.value = null
        }
        _selectedTab.value = index
        appPreferences.setActiveTabIndex(index)
    }

    fun navigateBackTab(): Boolean {
        val stack = _tabBackStack.value.toMutableList()
        while (stack.isNotEmpty()) {
            val previous = stack.removeAt(stack.lastIndex)
            if (previous != _selectedTab.value) {
                _tabBackStack.value = stack
                selectTab(previous, pushHistory = false)
                return true
            }
        }
        _tabBackStack.value = emptyList()
        return false
    }

    // --- Student Test Interactions & Attempt Persistence ---

    fun selectAnswer(qNum: Int, option: String) {
        val normalized = AnswerKeyMatcher.normalizeKey(option)
        val current = _studentAnswers.value.toMutableMap()
        if (normalized.isBlank()) {
            current.remove(qNum)
        } else {
            current[qNum] = normalized
        }
        _studentAnswers.value = current

        // When in Review Mode (isExamSubmitted == true), user interactions are for review/practice only.
        // Attending or correcting answers in review mode does NOT modify the official frozen exam score.
        if (!_isExamSubmitted.value) {
            saveCurrentAttempt()
        }
    }

    fun toggleMultiAnswerOption(qNum: Int, option: String) {
        val existing = _studentAnswers.value[qNum]
        val next = AnswerKeyMatcher.toggleSelection(existing, option)
        selectAnswer(qNum, next)
    }

    fun updateTimeSpent(seconds: Long) {
        if (_isExamSubmitted.value || _timeSpentSeconds.value == seconds) return
        _timeSpentSeconds.value = seconds
        saveCurrentAttempt()
    }

    fun setTimerMode(mode: ExamTimerMode) {
        _timerMode.value = mode
        if (mode == ExamTimerMode.COUNTDOWN) {
            _timeRemainingSeconds.value = _customTimeLimitMinutes.value * 60L
        }
    }

    fun setCustomTimeLimitMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 360)
        _customTimeLimitMinutes.value = clamped
        if (!_isExamStarted.value || _timerMode.value == ExamTimerMode.COUNTDOWN) {
            _timeRemainingSeconds.value = clamped * 60L
        }
    }

    fun setShuffleOptions(enabled: Boolean) {
        _shuffleOptions.value = enabled
        appPreferences.setShuffleOptions(enabled)
    }

    fun onActiveExamQuestionChanged(qNum: Int?) {
        val nowElapsed = SystemClock.elapsedRealtime()
        commitActiveQuestionElapsed(nowElapsed)
        activeTrackedQuestionNum = qNum
        activeQuestionStartedElapsedMs = nowElapsed
        activeQuestionRemainderMs = 0L
    }

    private fun commitActiveQuestionElapsed(nowElapsed: Long = SystemClock.elapsedRealtime()) {
        val qNum = activeTrackedQuestionNum ?: return
        if (!_isExamStarted.value || _isExamSubmitted.value) return
        val deltaMs = (nowElapsed - activeQuestionStartedElapsedMs).coerceAtLeast(0L)
        activeQuestionStartedElapsedMs = nowElapsed
        if (deltaMs <= 0L) return
        activeQuestionRemainderMs += deltaMs
        val wholeSecs = activeQuestionRemainderMs / 1000L
        if (wholeSecs > 0L) {
            activeQuestionRemainderMs %= 1000L
            val next = (questionTimesAccumulator[qNum] ?: 0L) + wholeSecs
            questionTimesAccumulator[qNum] = next
            _currentQuestionTimes.value = questionTimesAccumulator.toMap()
        }
    }

    fun resolveCurrentPaperPacingSummary(): AttemptPacingSummary? {
        val paper = _currentPaper.value ?: return null
        val activeTimes = _currentQuestionTimes.value
        if (activeTimes.isNotEmpty()) {
            val answers = if (_isExamSubmitted.value && _submittedAnswers.value.isNotEmpty()) {
                _submittedAnswers.value
            } else {
                _studentAnswers.value
            }
            val summary = QuestionPacingAnalyzer.computeSummary(paper, answers, activeTimes)
            if (summary.hasPacingData) return summary
        }
        val latestSubmitted = _attempts.value
            .filter { it.isSubmitted }
            .maxByOrNull { it.submittedAt ?: it.createdAt }
            ?: return null
        return QuestionPacingAnalyzer.computeSummary(paper, latestSubmitted).takeIf { it.hasPacingData }
    }

    fun syncExamTickerWithClock() {
        if (!_isExamStarted.value || _isExamSubmitted.value) return
        val nowElapsed = SystemClock.elapsedRealtime()
        if (examTimerLastTickElapsedMs == 0L) {
            examTimerLastTickElapsedMs = nowElapsed
            activeQuestionStartedElapsedMs = nowElapsed
            return
        }
        val deltaMs = (nowElapsed - examTimerLastTickElapsedMs).coerceAtLeast(0L)
        examTimerLastTickElapsedMs = nowElapsed
        if (deltaMs > 0L) {
            examTimerRemainderMs += deltaMs
            val wholeSecs = examTimerRemainderMs / 1000L
            if (wholeSecs > 0L) {
                examTimerRemainderMs %= 1000L
                _timeSpentSeconds.value += wholeSecs
                _todayTestSeconds.value += wholeSecs

                if (_timerMode.value == ExamTimerMode.COUNTDOWN) {
                    val remaining = (_timeRemainingSeconds.value - wholeSecs).coerceAtLeast(0L)
                    _timeRemainingSeconds.value = remaining
                    if (remaining <= 0L) {
                        commitActiveQuestionElapsed(nowElapsed)
                        submitExam()
                        _statusMessage.value = "Time's up! Examination auto-submitted."
                        return
                    }
                }
            }
        }
        commitActiveQuestionElapsed(nowElapsed)
    }

    private fun startTimerTicker() {
        examTimerJob?.cancel()
        val startElapsed = SystemClock.elapsedRealtime()
        examTimerLastTickElapsedMs = startElapsed
        examTimerRemainderMs = 0L
        examTimerLastRoomFlushElapsedMs = startElapsed
        activeQuestionStartedElapsedMs = startElapsed
        activeQuestionRemainderMs = 0L
        examTimerJob = viewModelScope.launch {
            while (_isExamStarted.value && !_isExamSubmitted.value) {
                delay(1000L)
                if (!_isExamStarted.value || _isExamSubmitted.value) break

                syncExamTickerWithClock()
                if (_isExamSubmitted.value) break

                val nowElapsed = SystemClock.elapsedRealtime()
                // Throttle background Room persistence to every 30s (immediate flushes still happen on pause/exit/submit)
                if (nowElapsed - examTimerLastRoomFlushElapsedMs >= 30_000L) {
                    examTimerLastRoomFlushElapsedMs = nowElapsed
                    saveCurrentAttempt()
                    saveTodayDailyAnalytics()
                }
            }
        }
    }

    private fun stopTimerTicker() {
        examTimerJob?.cancel()
        examTimerJob = null
        if (_isExamStarted.value && !_isExamSubmitted.value) {
            syncExamTickerWithClock()
        }
        activeTrackedQuestionNum = null
        examTimerLastTickElapsedMs = 0L
        saveTodayDailyAnalytics()
    }

    private fun saveCurrentAttempt() {
        val paperId = _currentPaperEntityId.value ?: return
        val attemptId = _currentAttemptId.value
        val paper = _currentPaper.value ?: return
        commitActiveQuestionElapsed()
        val currentTime = _timeSpentSeconds.value
        val answers = _studentAnswers.value.toMap()
        val questionTimesSnapshot = questionTimesAccumulator.toMap()
        val isSubmitted = _isExamSubmitted.value

        // If attempt was already submitted, do not overwrite the official one-time score from review changes
        if (isSubmitted) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val stats = calculateTestStats(paper, answers)
            val answersJson = AttemptEntity.buildAnswersJson(
                map = answers,
                questionTimes = questionTimesSnapshot
            )
            if (attemptId == null) {
                val newAttempt = AttemptEntity(
                    paperId = paperId,
                    answersJson = answersJson,
                    totalQuestions = stats.total,
                    isSubmitted = isSubmitted,
                    score = stats.score,
                    correctCount = stats.correct,
                    incorrectCount = stats.incorrect,
                    unansweredCount = stats.unanswered,
                    timeSpentSeconds = currentTime,
                    submittedAt = if (isSubmitted) com.example.util.TrustedTimeManager.getTrustedTimeMillis() else null
                )
                val newId = paperDao.insertAttempt(newAttempt)
                withContext(Dispatchers.Main) {
                    _currentAttemptId.value = newId
                }
                loadAttemptHistory(paperId)
            } else {
                val existing = paperDao.getAttemptById(attemptId)
                if (existing != null && !existing.isSubmitted) {
                    val updated = existing.copy(
                        answersJson = answersJson,
                        timeSpentSeconds = currentTime,
                        totalQuestions = stats.total,
                        isSubmitted = isSubmitted,
                        score = stats.score,
                        correctCount = stats.correct,
                        incorrectCount = stats.incorrect,
                        unansweredCount = stats.unanswered,
                        submittedAt = if (isSubmitted) (existing.submittedAt ?: com.example.util.TrustedTimeManager.getTrustedTimeMillis()) else null
                    )
                    paperDao.updateAttempt(updated)
                    loadAttemptHistory(paperId)
                }
            }
        }
    }

    fun submitExam() {
        if (_isExamSubmitted.value) return
        stopTimerTicker()
        _isExamSubmitted.value = true
        _retryQuestionNumbers.value = null

        val paperId = _currentPaperEntityId.value ?: return
        val attemptId = _currentAttemptId.value
        val paper = _currentPaper.value ?: return

        val answersSnapshot = _studentAnswers.value.toMap()
        val questionTimesSnapshot = questionTimesAccumulator.toMap()
        _submittedAnswers.value = answersSnapshot

        // One-time official scoring calculation
        val stats = calculateTestStats(paper, answersSnapshot)
        _submittedStats.value = stats

        // Make log for review of all questions answered incorrectly or partially at submission time
        val incorrectSet = paper.questions.filter { q ->
            val grade = AnswerKeyMatcher.scoreQuestion(
                question = q,
                userAnswer = answersSnapshot[q.num],
                positiveMarks = stats.positiveMarks,
                negativeMarks = stats.negativeMarks,
                unattendedMarks = stats.unattendedMarks,
                msqPolicy = paper.metadata.msqScoringPolicy
            )
            grade.status == AnswerKeyMatcher.QuestionGradeStatus.INCORRECT ||
                grade.status == AnswerKeyMatcher.QuestionGradeStatus.PARTIAL
        }.map { it.num }.toSet()
        _incorrectQuestionLog.value = incorrectSet

        val unattendedSet = paper.questions.filter { q ->
            answersSnapshot[q.num].isNullOrBlank()
        }.map { it.num }.toSet()
        _unattendedQuestionLog.value = unattendedSet

        val finalRoll = _examConfig.value.rollNumber.takeIf { it.isNotBlank() }
            ?: paper.metadata.rollNumber.takeIf { it.isNotBlank() }
            ?: ExamNumberGenerator.generateRollNumber()
        val finalBooklet = _examConfig.value.bookletNumber.takeIf { it.isNotBlank() }
            ?: paper.metadata.bookletNumber.takeIf { it.isNotBlank() }
            ?: ExamNumberGenerator.generateBookletNumber()
        val finalCode = _examConfig.value.seriesCode.takeIf { it.isNotBlank() }
            ?: paper.metadata.setNumber.takeIf { it.isNotBlank() }
            ?: "Set A"

        viewModelScope.launch(Dispatchers.IO) {
            val answersJson = AttemptEntity.buildAnswersJson(
                map = answersSnapshot,
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                bookletCode = finalCode,
                questionTimes = questionTimesSnapshot
            )
            val trustedSubmissionTime = com.example.util.TrustedTimeManager.getTrustedTimeMillis()
            val updatedAttempt = if (attemptId != null) {
                paperDao.getAttemptById(attemptId)?.copy(
                    answersJson = answersJson,
                    totalQuestions = stats.total,
                    isSubmitted = true,
                    correctCount = stats.correct,
                    incorrectCount = stats.incorrect,
                    unansweredCount = stats.unanswered,
                    score = stats.score,
                    timeSpentSeconds = _timeSpentSeconds.value,
                    submittedAt = trustedSubmissionTime
                )
            } else {
                AttemptEntity(
                    paperId = paperId,
                    answersJson = answersJson,
                    totalQuestions = stats.total,
                    isSubmitted = true,
                    correctCount = stats.correct,
                    incorrectCount = stats.incorrect,
                    unansweredCount = stats.unanswered,
                    score = stats.score,
                    timeSpentSeconds = _timeSpentSeconds.value,
                    submittedAt = trustedSubmissionTime
                )
            }

            if (updatedAttempt != null) {
                if (updatedAttempt.id == 0L) {
                    val newId = paperDao.insertAttempt(updatedAttempt)
                    withContext(Dispatchers.Main) { _currentAttemptId.value = newId }
                } else {
                    paperDao.updateAttempt(updatedAttempt)
                }
                loadAttemptHistory(paperId)
            }
            recordTestCompletionInDailyAnalytics(
                questionsAttempted = answersSnapshot.size,
                correctCount = stats.correct
            )
        }
    }

    /**
     * Submits and records an optical OMR scan evaluation directly into the attempts history,
     * recording student candidate identification (roll number, booklet number, and set code)
     * and updating state flows so student and psychometrician can immediately review evaluated results.
     */
    fun submitScannedOmrResult(
        result: OmrVisionProcessor.OmrScanOutput,
        candidateRollNumber: String? = null,
        candidateBookletNumber: String? = null,
        candidateBookletCode: String? = null
    ) {
        val finalRoll = candidateRollNumber ?: result.headerResult?.rollNumber ?: ""
        val finalBooklet = candidateBookletNumber ?: result.headerResult?.bookletNumber ?: ""
        val finalCode = candidateBookletCode ?: result.headerResult?.bookletCode ?: ""

        val paper = _currentPaper.value
            ?: findMatchingPaperForScannedSheet(finalBooklet, finalCode, finalRoll)?.let { matched ->
                loadSavedPaper(matched)
                matched.toQuestionPaper()
            }
            ?: savedPapers.value.firstOrNull()?.let { first ->
                loadSavedPaper(first)
                first.toQuestionPaper()
            }
            ?: return
        val paperId = _currentPaperEntityId.value ?: paper.id

        stopTimerTicker()
        _isExamStarted.value = false
        _isExamSubmitted.value = true
        _retryQuestionNumbers.value = null

        // Map detected answers (only valid single marks)
        val answersSnapshot = mutableMapOf<Int, String>()
        for ((qNum, qResult) in result.questionResults) {
            val mark = qResult.selectedOption?.toString()
            if (!mark.isNullOrBlank() && !qResult.isMultipleMarked) {
                answersSnapshot[qNum] = mark
            }
        }

        _studentAnswers.value = answersSnapshot
        _submittedAnswers.value = answersSnapshot

        val pos = if (paper.metadata.positiveMarks > 0.0) paper.metadata.positiveMarks else 4.0
        val neg = if (paper.metadata.negativeMarks >= 0.0) paper.metadata.negativeMarks else 1.0
        val unk = if (paper.metadata.unattendedMarks >= 0.0) paper.metadata.unattendedMarks else 0.0
        val incorrectAll = result.incorrectCount + result.multipleMarkedCount
        val penaltyIncorrect = Math.round(incorrectAll * neg * 100.0) / 100.0
        val penaltyUnattended = Math.round(result.unattemptedCount * unk * 100.0) / 100.0
        val omrScore = if (result.questionMarks.isNotEmpty()) {
            result.exactScore
        } else {
            Math.round(((result.correctCount * pos) - penaltyIncorrect - penaltyUnattended) * 100.0) / 100.0
        }
        val stats = Companion.TestStats(
            total = result.totalQuestions,
            correct = result.correctCount,
            incorrect = incorrectAll,
            unanswered = result.unattemptedCount,
            score = omrScore,
            rawScore = omrScore,
            penaltyIncorrect = penaltyIncorrect,
            penaltyUnattended = penaltyUnattended,
            maxPossibleMarks = result.totalQuestions * pos,
            positiveMarks = pos,
            negativeMarks = neg,
            unattendedMarks = unk
        )
        _submittedStats.value = stats

        val incorrectSet = result.questionResults.values
            .filter { q ->
                val officialQuestion = paper.questions.getOrNull(q.questionNum - 1)
                val isNeutralized = officialQuestion?.isNeutralized == true
                !isNeutralized && (
                    q.isMultipleMarked ||
                        (q.selectedOption != null && !AnswerKeyMatcher.areKeysEquivalent(officialQuestion?.correctAnswer, q.selectedOption.toString()))
                    )
            }
            .map { it.questionNum }
            .toSet()
        _incorrectQuestionLog.value = incorrectSet

        val unattendedSet = result.questionResults.values
            .filter { !it.isMultipleMarked && it.selectedOption == null }
            .map { it.questionNum }
            .toSet()
        _unattendedQuestionLog.value = unattendedSet

        viewModelScope.launch(Dispatchers.IO) {
            val trustedTime = com.example.util.TrustedTimeManager.getTrustedTimeMillis()
            val answersJson = AttemptEntity.buildAnswersJson(
                map = answersSnapshot,
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                bookletCode = finalCode
            )
            val scannedAttempt = AttemptEntity(
                paperId = paperId,
                answersJson = answersJson,
                totalQuestions = stats.total,
                isSubmitted = true,
                correctCount = stats.correct,
                incorrectCount = stats.incorrect,
                unansweredCount = stats.unanswered,
                score = stats.score,
                timeSpentSeconds = _timeSpentSeconds.value.coerceAtLeast(60L),
                submittedAt = trustedTime
            )

            val newId = paperDao.insertAttempt(scannedAttempt)
            withContext(Dispatchers.Main) {
                _currentAttemptId.value = newId
                val rollSnippet = if (finalRoll.isNotBlank()) " | Roll: $finalRoll" else ""
                val maxStr = String.format(java.util.Locale.US, "%.2f", stats.maxPossibleMarks)
                _statusMessage.value = "OMR sheet evaluated$rollSnippet: Score ${stats.formattedScore} / $maxStr"
            }
            loadAttemptHistory(paperId)

            recordTestCompletionInDailyAnalytics(
                questionsAttempted = result.attemptedCount,
                correctCount = stats.correct
            )
        }
    }

    fun startExam(questionNumbers: List<Int>? = null, customMinutes: Int? = null) {
        val totalQuestions = _currentPaper.value?.questions?.size ?: 30
        val targetMinutes = customMinutes ?: if (_customTimeLimitMinutes.value <= 0) totalQuestions.coerceAtLeast(1) else _customTimeLimitMinutes.value
        setCustomTimeLimitMinutes(targetMinutes)
        _retryQuestionNumbers.value = questionNumbers
        if (questionNumbers == null) {
            _studentAnswers.value = emptyMap()
            _submittedAnswers.value = emptyMap()
            _submittedStats.value = null
            _incorrectQuestionLog.value = emptySet()
            _unattendedQuestionLog.value = emptySet()
            _currentAttemptId.value = null
            _examSessionSeed.value = System.currentTimeMillis()
            questionTimesAccumulator.clear()
            _currentQuestionTimes.value = emptyMap()
            activeTrackedQuestionNum = null
        }
        _timeSpentSeconds.value = 0L
        _timeRemainingSeconds.value = _customTimeLimitMinutes.value * 60L
        _isExamSubmitted.value = false
        _isExamStarted.value = true
        recordTestStartInDailyAnalytics()
        saveCurrentAttempt()
        startTimerTicker()
    }

    fun resumeExam() {
        val limitSecs = _customTimeLimitMinutes.value * 60L
        if (_timerMode.value == ExamTimerMode.COUNTDOWN && limitSecs > 0L) {
            _timeRemainingSeconds.value = (limitSecs - _timeSpentSeconds.value).coerceAtLeast(1L)
        }
        _isExamStarted.value = true
        _isExamSubmitted.value = false
        startTimerTicker()
    }

    fun exitToExamOverview() {
        stopTimerTicker()
        _isExamStarted.value = false
        _isExamSubmitted.value = false
        _retryQuestionNumbers.value = null
        saveCurrentAttempt()
        saveTodayDailyAnalytics()
    }

    fun resetStudentExam() {
        stopTimerTicker()
        _studentAnswers.value = emptyMap()
        _submittedAnswers.value = emptyMap()
        _submittedStats.value = null
        _incorrectQuestionLog.value = emptySet()
        _unattendedQuestionLog.value = emptySet()
        questionTimesAccumulator.clear()
        _currentQuestionTimes.value = emptyMap()
        activeTrackedQuestionNum = null
        _timeSpentSeconds.value = 0L
        val autoMins = (_currentPaper.value?.questions?.size ?: 30).coerceAtLeast(1)
        _customTimeLimitMinutes.value = autoMins
        _timeRemainingSeconds.value = autoMins * 60L
        _isExamSubmitted.value = false
        _isExamStarted.value = true
        _retryQuestionNumbers.value = null
        _currentAttemptId.value = null
        saveCurrentAttempt()
        startTimerTicker()
    }

    fun loadAttemptHistory(paperId: Long) {
        attemptHistoryJob?.cancel()
        attemptHistoryJob = viewModelScope.launch(Dispatchers.IO) {
            paperDao.getAttemptsForPaper(paperId).collect { attempts ->
                withContext(Dispatchers.Main) {
                    _attempts.value = attempts
                }
            }
        }
    }

    fun loadOldAttempt(attempt: AttemptEntity) {
        stopTimerTicker()
        _currentAttemptId.value = attempt.id
        val answersMap = attempt.getAnswersMap()
        val loadedQTimes = attempt.getQuestionTimesMap()
        questionTimesAccumulator.clear()
        questionTimesAccumulator.putAll(loadedQTimes)
        _currentQuestionTimes.value = loadedQTimes
        activeTrackedQuestionNum = null
        _studentAnswers.value = answersMap
        _timeSpentSeconds.value = attempt.timeSpentSeconds
        _isExamSubmitted.value = attempt.isSubmitted
        _isExamStarted.value = true
        _retryQuestionNumbers.value = null

        val paper = _currentPaper.value
        if (paper != null && attempt.isSubmitted) {
            _submittedAnswers.value = answersMap
            val stats = calculateTestStats(paper, answersMap)
            _submittedStats.value = stats
            _incorrectQuestionLog.value = paper.questions.filter { q ->
                val grade = AnswerKeyMatcher.scoreQuestion(
                    question = q,
                    userAnswer = answersMap[q.num],
                    positiveMarks = stats.positiveMarks,
                    negativeMarks = stats.negativeMarks,
                    unattendedMarks = stats.unattendedMarks,
                    msqPolicy = paper.metadata.msqScoringPolicy
                )
                grade.status == AnswerKeyMatcher.QuestionGradeStatus.INCORRECT ||
                    grade.status == AnswerKeyMatcher.QuestionGradeStatus.PARTIAL
            }.map { it.num }.toSet()
            _unattendedQuestionLog.value = paper.questions.filter { q ->
                answersMap[q.num].isNullOrBlank()
            }.map { it.num }.toSet()
        } else {
            val limitSecs = _customTimeLimitMinutes.value * 60L
            if (_timerMode.value == ExamTimerMode.COUNTDOWN && limitSecs > 0L) {
                _timeRemainingSeconds.value = (limitSecs - attempt.timeSpentSeconds).coerceAtLeast(1L)
            }
            startTimerTicker()
        }

        _selectedTab.value = 2 // Switch to student test tab
    }

    fun reviewCompletedPaperAttempt(paper: PaperEntity) {
        loadSavedPaper(paper) {
            viewModelScope.launch(Dispatchers.IO) {
                val latest = allAttempts.value
                    .filter { it.paperId == paper.id && it.isSubmitted }
                    .maxByOrNull { it.submittedAt ?: it.createdAt }
                    ?: paperDao.getAttemptsForPaper(paper.id).first()
                        .filter { it.isSubmitted }
                        .maxByOrNull { it.submittedAt ?: it.createdAt }
                withContext(Dispatchers.Main) {
                    if (latest != null) {
                        loadOldAttempt(latest)
                    } else {
                        _isExamStarted.value = false
                        _isExamSubmitted.value = false
                        _selectedTab.value = 2
                    }
                }
            }
        }
    }

    fun updateExamConfig(config: ExamConfig) {
        _examConfig.value = config
        val active = _currentPaper.value
        if (active != null) {
            val updatedMeta = active.metadata.copy(
                durationMinutes = config.durationMinutes,
                positiveMarks = config.positiveMarks,
                negativeMarks = config.negativeMarks,
                unattendedMarks = config.unattendedMarks,
                msqScoringPolicy = config.msqScoringPolicy
            )
            val updatedPaper = active.copy(metadata = updatedMeta)
            _currentPaper.value = updatedPaper
            val linkedId = _currentPaperEntityId.value ?: (if (active.id > 0L) active.id else null)
            if (linkedId != null && linkedId > 0L) {
                appPreferences.setPaperMsqPolicy(linkedId, config.msqScoringPolicy)
            }
            schedulePaperAutoSave(updatedPaper)
        }
        if (!_isExamStarted.value) {
            _customTimeLimitMinutes.value = config.durationMinutes
            _timeRemainingSeconds.value = config.durationMinutes * 60L
        }
    }

    fun deleteSavedPaper(entity: PaperEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            paperDao.deletePaper(entity)
            if (_currentPaperEntityId.value == entity.id) {
                withContext(Dispatchers.Main) {
                    _currentPaperEntityId.value = null
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        themePreferences.setThemeMode(mode)
    }

    fun toggleThemeMode() {
        val nextMode = when (_themeMode.value) {
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        setThemeMode(nextMode)
    }

    fun setColorSchemePreset(preset: ColorSchemePreset) {
        _colorSchemePreset.value = preset
        themePreferences.setColorSchemePreset(preset)
    }

    fun toggleCardBoxed() {
        _isCardBoxed.value = !_isCardBoxed.value
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun closeCurrentPaper() {
        clearCurrentPaper()
        if (_selectedTab.value in listOf(1, 2, 3)) {
            if (!navigateBackTab()) {
                selectTab(0, pushHistory = false)
            }
        }
    }

    fun clearCurrentPaper() {
        stopTimerTicker()
        _currentPaper.value = null
        _currentPaperEntityId.value = null
        _rawInputText.value = ""
        _studentAnswers.value = emptyMap()
        questionTimesAccumulator.clear()
        _currentQuestionTimes.value = emptyMap()
        activeTrackedQuestionNum = null
        _isExamSubmitted.value = false
        _isExamStarted.value = false
        _currentAttemptId.value = null
        appPreferences.setLastActivePaperId(-1L)
        appPreferences.setDraftRawText("")
    }

    // --- Student Test & Study Analytics Persistence ---

    fun getTodayDateString(): String {
        val trustedTime = com.example.util.TrustedTimeManager.getTrustedTimeMillis()
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(trustedTime))
    }

    private fun initTodayStudyAnalytics() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val todayStr = getTodayDateString()
                val existing = paperDao.getDailyAnalytics(todayStr)
                val currentGoalMins = appPreferences.getDailyGoalMinutes()
                if (existing != null) {
                    withContext(Dispatchers.Main) {
                        _todayStudyAnalytics.value = existing
                        _todayTestSeconds.value = existing.totalTestTimeSeconds
                    }
                } else {
                    val newEntity = DailyStudyAnalyticsEntity(
                        dateString = todayStr,
                        totalTestTimeSeconds = 0L,
                        dailyGoalSeconds = currentGoalMins * 60L
                    )
                    paperDao.upsertDailyAnalytics(newEntity)
                    withContext(Dispatchers.Main) {
                        _todayStudyAnalytics.value = newEntity
                        _todayTestSeconds.value = 0L
                    }
                }
            } catch (_: Exception) {
                // Ignore transient startup DB errors
            }
        }
    }

    fun saveTodayDailyAnalytics() {
        val todayStr = getTodayDateString()
        val currentSecs = _todayTestSeconds.value
        val goalMins = _dailyGoalMinutes.value
        viewModelScope.launch(Dispatchers.IO) {
            val trustedNow = com.example.util.TrustedTimeManager.getTrustedTimeMillis()
            val existing = paperDao.getDailyAnalytics(todayStr)
            val updated = if (existing != null) {
                existing.copy(
                    totalTestTimeSeconds = currentSecs,
                    dailyGoalSeconds = goalMins * 60L,
                    lastUpdatedTimestamp = trustedNow
                )
            } else {
                DailyStudyAnalyticsEntity(
                    dateString = todayStr,
                    totalTestTimeSeconds = currentSecs,
                    dailyGoalSeconds = goalMins * 60L,
                    lastUpdatedTimestamp = trustedNow
                )
            }
            paperDao.upsertDailyAnalytics(updated)
            withContext(Dispatchers.Main) {
                _todayStudyAnalytics.value = updated
            }
        }
    }

    fun recordStudySeconds(additionalSeconds: Long) {
        if (additionalSeconds <= 0L) return
        _todayTestSeconds.value += additionalSeconds
        saveTodayDailyAnalytics()
    }

    private fun recordTestCompletionInDailyAnalytics(
        questionsAttempted: Int,
        correctCount: Int
    ) {
        val todayStr = getTodayDateString()
        val currentSecs = _todayTestSeconds.value
        val goalMins = _dailyGoalMinutes.value
        viewModelScope.launch(Dispatchers.IO) {
            val trustedNow = com.example.util.TrustedTimeManager.getTrustedTimeMillis()
            val existing = paperDao.getDailyAnalytics(todayStr)
            val updated = if (existing != null) {
                existing.copy(
                    totalTestTimeSeconds = currentSecs,
                    testsCompletedCount = existing.testsCompletedCount + 1,
                    questionsAttemptedCount = existing.questionsAttemptedCount + questionsAttempted,
                    correctAnswersCount = existing.correctAnswersCount + correctCount,
                    dailyGoalSeconds = goalMins * 60L,
                    lastUpdatedTimestamp = trustedNow
                )
            } else {
                DailyStudyAnalyticsEntity(
                    dateString = todayStr,
                    totalTestTimeSeconds = currentSecs,
                    testsCompletedCount = 1,
                    testsStartedCount = 1,
                    questionsAttemptedCount = questionsAttempted,
                    correctAnswersCount = correctCount,
                    dailyGoalSeconds = goalMins * 60L,
                    lastUpdatedTimestamp = trustedNow
                )
            }
            paperDao.upsertDailyAnalytics(updated)
            withContext(Dispatchers.Main) {
                _todayStudyAnalytics.value = updated
            }
        }
    }

    private fun recordTestStartInDailyAnalytics() {
        val todayStr = getTodayDateString()
        val currentSecs = _todayTestSeconds.value
        val goalMins = _dailyGoalMinutes.value
        viewModelScope.launch(Dispatchers.IO) {
            val trustedNow = com.example.util.TrustedTimeManager.getTrustedTimeMillis()
            val existing = paperDao.getDailyAnalytics(todayStr)
            val updated = if (existing != null) {
                existing.copy(
                    totalTestTimeSeconds = currentSecs,
                    testsStartedCount = existing.testsStartedCount + 1,
                    dailyGoalSeconds = goalMins * 60L,
                    lastUpdatedTimestamp = trustedNow
                )
            } else {
                DailyStudyAnalyticsEntity(
                    dateString = todayStr,
                    totalTestTimeSeconds = currentSecs,
                    testsStartedCount = 1,
                    dailyGoalSeconds = goalMins * 60L,
                    lastUpdatedTimestamp = trustedNow
                )
            }
            paperDao.upsertDailyAnalytics(updated)
            withContext(Dispatchers.Main) {
                _todayStudyAnalytics.value = updated
            }
        }
    }

    fun setDailyGoalMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(5, 360)
        _dailyGoalMinutes.value = clamped
        appPreferences.setDailyGoalMinutes(clamped)
        saveTodayDailyAnalytics()
    }

    companion object {
        data class TestStats(
            val total: Int,
            val correct: Int,
            val incorrect: Int,
            val unanswered: Int,
            val score: Double,
            val rawScore: Double = 0.0,
            val penaltyIncorrect: Double = 0.0,
            val penaltyUnattended: Double = 0.0,
            val maxPossibleMarks: Double = total * 1.0,
            val positiveMarks: Double = 1.0,
            val negativeMarks: Double = 0.0,
            val unattendedMarks: Double = 0.0,
            val partial: Int = 0,
            val msqScoringPolicy: MsqScoringPolicy = MsqScoringPolicy.ALL_OR_NOTHING
        ) {
            val formattedScore: String
                get() = String.format(java.util.Locale.US, "%.2f", score)

            val percentage: Int
                get() = if (maxPossibleMarks > 0) ((score / maxPossibleMarks) * 100.0).toInt().coerceIn(0, 100)
                        else if (total > 0) ((score / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                        else 0
        }

        fun calculateTestStats(
            paper: QuestionPaper,
            answers: Map<Int, String>,
            positiveMarks: Double = paper.metadata.positiveMarks,
            negativeMarks: Double = paper.metadata.negativeMarks,
            unattendedMarks: Double = paper.metadata.unattendedMarks,
            msqScoringPolicy: MsqScoringPolicy = paper.metadata.msqScoringPolicy
        ): TestStats {
            val pos = if (positiveMarks > 0.0) positiveMarks else 4.0
            val neg = if (negativeMarks >= 0.0) negativeMarks else 1.0
            val unk = if (unattendedMarks >= 0.0) unattendedMarks else 0.0

            var correct = 0
            var partial = 0
            var incorrect = 0
            var rawScore = 0.0
            var penaltyIncorrect = 0.0
            var penaltyUnattended = 0.0

            paper.questions.forEach { q ->
                val result = AnswerKeyMatcher.scoreQuestion(
                    question = q,
                    userAnswer = answers[q.num],
                    positiveMarks = pos,
                    negativeMarks = neg,
                    unattendedMarks = unk,
                    msqPolicy = msqScoringPolicy
                )
                rawScore += result.marks
                when (result.status) {
                    AnswerKeyMatcher.QuestionGradeStatus.NEUTRALIZED,
                    AnswerKeyMatcher.QuestionGradeStatus.CORRECT -> correct++
                    AnswerKeyMatcher.QuestionGradeStatus.PARTIAL -> partial++
                    AnswerKeyMatcher.QuestionGradeStatus.INCORRECT -> {
                        incorrect++
                        penaltyIncorrect += neg
                    }
                    AnswerKeyMatcher.QuestionGradeStatus.UNANSWERED -> {
                        penaltyUnattended += unk
                    }
                }
            }
            val unanswered = paper.questions.size - (correct + partial + incorrect)

            val penaltyIncorrectRounded = Math.round(penaltyIncorrect * 100.0) / 100.0
            val penaltyUnattendedRounded = Math.round(penaltyUnattended * 100.0) / 100.0
            val rawScoreRounded = Math.round(rawScore * 100.0) / 100.0
            val maxMarks = Math.round((paper.questions.size * pos) * 100.0) / 100.0

            return TestStats(
                total = paper.questions.size,
                correct = correct,
                incorrect = incorrect,
                unanswered = unanswered,
                score = rawScoreRounded,
                rawScore = rawScoreRounded,
                penaltyIncorrect = penaltyIncorrectRounded,
                penaltyUnattended = penaltyUnattendedRounded,
                maxPossibleMarks = maxMarks,
                positiveMarks = pos,
                negativeMarks = neg,
                unattendedMarks = unk,
                partial = partial,
                msqScoringPolicy = msqScoringPolicy
            )
        }
    }
}
