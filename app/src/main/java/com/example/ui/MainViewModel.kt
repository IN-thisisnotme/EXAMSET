package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AttemptEntity
import com.example.data.DailyStudyAnalyticsEntity
import com.example.data.ExamSetPreferences
import com.example.data.PaperEntity
import com.example.data.toQuestionPaper
import com.example.data.SampleData
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import com.example.model.AutoSaveStatus
import com.example.model.BatchImportItem
import com.example.model.BatchImportResult
import com.example.model.BatchItemStatus
import com.example.model.ExamConfig
import com.example.model.FolderItem
import com.example.model.McqQuestion
import com.example.model.PaperMetadata
import com.example.model.ParseResult
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import com.example.util.McqParser
import com.example.util.BatchExamImporter
import com.example.util.ExamJsonHelper
import com.example.util.ExamNumberGenerator
import com.example.util.OmrVisionProcessor
import com.example.util.PsychometricEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ExamTimerMode {
    COUNTDOWN,
    STOPWATCH
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val paperDao = db.paperDao()
    private val themePreferences = ThemePreferences(application)
    private val appPreferences = ExamSetPreferences(application)

    // Dark Mode preference
    private val _themeMode = MutableStateFlow(themePreferences.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Question card layout style: true for rounded cards, false for borderless exam paper
    private val _isCardBoxed = MutableStateFlow(true)
    val isCardBoxed: StateFlow<Boolean> = _isCardBoxed.asStateFlow()

    val savedPapers: StateFlow<List<PaperEntity>> = paperDao.getAllPapers()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allAttempts: StateFlow<List<AttemptEntity>> = paperDao.getAllAttempts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allDailyAnalytics: StateFlow<List<DailyStudyAnalyticsEntity>> = paperDao.getAllDailyAnalytics()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    // Active Tab:
    // 0: Creator, 1: Master View, 2: Print & OMR, 3: Student Portal, 4: Folders, 5: AI Prompt, 6: Share
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

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

    // Reading font scale (1.0f = Standard, 1.2f = Large, 1.4f = Extra Large)
    private val _fontSizeScale = MutableStateFlow(appPreferences.getFontSizeScale())
    val fontSizeScale: StateFlow<Float> = _fontSizeScale.asStateFlow()

    fun setFontSizeScale(scale: Float) {
        _fontSizeScale.value = scale
        appPreferences.setFontSizeScale(scale)
    }

    // Autosave debouncing jobs
    private var inputDebounceJob: Job? = null
    private var paperDebounceJob: Job? = null

    init {
        restoreAutosavedState()
        initTodayStudyAnalytics()
        backfillLegacyPaperNumbers()
    }

    private fun backfillLegacyPaperNumbers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allPapers = paperDao.getAllPapers().first()
                allPapers.forEach { paper ->
                    val needRoll = paper.rollNumber.isBlank()
                    val needBooklet = paper.bookletNumber.isBlank()
                    if (needRoll || needBooklet) {
                        val updatedRoll = if (needRoll) ExamNumberGenerator.generateRollNumber() else paper.rollNumber
                        val updatedBooklet = if (needBooklet) ExamNumberGenerator.generateBookletNumber() else paper.bookletNumber
                        paperDao.updatePaper(
                            paper.copy(
                                rollNumber = updatedRoll,
                                bookletNumber = updatedBooklet
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore transient errors during first launch
            }
        }
    }

    private val _lastOpenedPaper = MutableStateFlow<PaperEntity?>(null)
    val lastOpenedPaper: StateFlow<PaperEntity?> = _lastOpenedPaper.asStateFlow()

    private fun restoreAutosavedState() {
        viewModelScope.launch(Dispatchers.IO) {
            // Clean up any previously auto-seeded starter paper if present
            try {
                val seededPaper = paperDao.findPaperByMetadata(
                    title = "UPSC / SSC CGL Combined General Studies",
                    subject = "General Science",
                    setNumber = "Set A"
                )
                if (seededPaper != null && seededPaper.rawText == SampleData.SAMPLE_STARTER_PROMPT) {
                    paperDao.deletePaperWithAttempts(seededPaper)
                    if (appPreferences.getLastActivePaperId() == seededPaper.id) {
                        appPreferences.setLastActivePaperId(-1L)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }

            val lastPaperId = appPreferences.getLastActivePaperId()
            if (lastPaperId > 0) {
                val entity = paperDao.getPaperById(lastPaperId)
                if (entity != null) {
                    _lastOpenedPaper.value = entity
                }
            } else {
                // If lastPaperId not set, populate last opened info with most recently updated paper if any exist
                try {
                    val allSaved = paperDao.getAllPapers().first()
                    if (allSaved.isNotEmpty()) {
                        _lastOpenedPaper.value = allSaved.maxByOrNull { it.updatedAt }
                    } else {
                        _lastOpenedPaper.value = null
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            // Continuously synchronize _lastOpenedPaper with Room updates
            launch {
                savedPapers.collect { papers ->
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
            val savedTab = appPreferences.getActiveTabIndex()
            withContext(Dispatchers.Main) {
                if (savedTab in 0..6) {
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
        _rawInputText.value = text
        _autoSaveStatus.value = AutoSaveStatus.SAVING

        inputDebounceJob?.cancel()
        inputDebounceJob = viewModelScope.launch(Dispatchers.Default) {
            delay(500) // 500ms debounce
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

                appPreferences.setLastActivePaperId(paperId)

                withContext(Dispatchers.Main) {
                    _currentPaperEntityId.value = paperId
                    _currentPaper.value = result.paper.copy(id = paperId)
                    _autoSaveStatus.value = AutoSaveStatus.SAVED
                    updateTimestamp()
                }
            } else {
                withContext(Dispatchers.Main) {
                    _autoSaveStatus.value = AutoSaveStatus.SAVED
                    updateTimestamp()
                }
            }
        }
    }

    /**
     * Debounced autosave when user modifies questions, reorders, or alters paper details.
     */
    private fun schedulePaperAutoSave(updatedPaper: QuestionPaper) {
        _currentPaper.value = updatedPaper
        _autoSaveStatus.value = AutoSaveStatus.SAVING

        paperDebounceJob?.cancel()
        paperDebounceJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500) // 500ms debounce
            val raw = McqParser.reconstructToRaw(updatedPaper)
            appPreferences.setDraftRawText(raw)

            val currentId = _currentPaperEntityId.value
            val targetId: Long = if (currentId != null && currentId > 0) {
                val existing = paperDao.getPaperById(currentId)
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
                        durationMinutes = updatedPaper.metadata.durationMinutes,
                        positiveMarks = updatedPaper.metadata.positiveMarks,
                        negativeMarks = updatedPaper.metadata.negativeMarks,
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
                        rollNumber = updatedPaper.metadata.rollNumber,
                        bookletNumber = updatedPaper.metadata.bookletNumber,
                        durationMinutes = updatedPaper.metadata.durationMinutes,
                        positiveMarks = updatedPaper.metadata.positiveMarks,
                        negativeMarks = updatedPaper.metadata.negativeMarks
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
                        rollNumber = if (match.rollNumber.isNotBlank()) match.rollNumber else updatedPaper.metadata.rollNumber,
                        bookletNumber = if (match.bookletNumber.isNotBlank()) match.bookletNumber else updatedPaper.metadata.bookletNumber,
                        durationMinutes = updatedPaper.metadata.durationMinutes,
                        positiveMarks = updatedPaper.metadata.positiveMarks,
                        negativeMarks = updatedPaper.metadata.negativeMarks,
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
                        rollNumber = updatedPaper.metadata.rollNumber,
                        bookletNumber = updatedPaper.metadata.bookletNumber,
                        durationMinutes = updatedPaper.metadata.durationMinutes,
                        positiveMarks = updatedPaper.metadata.positiveMarks,
                        negativeMarks = updatedPaper.metadata.negativeMarks
                    )
                    paperDao.insertPaper(newEntity)
                }
                targetId
            }

            appPreferences.setLastActivePaperId(targetId)

            // Recalculate any submitted attempts for this paper so scores/breakdown stay 100% accurate
            recalculateAttemptsForPaper(targetId, updatedPaper)

            val freshSavedEntity = if (targetId > 0) paperDao.getPaperById(targetId) else null

            withContext(Dispatchers.Main) {
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
                                val userAns = answersMap[q.num]
                                !userAns.isNullOrBlank() && !userAns.equals(q.correctAnswer, ignoreCase = true)
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
        allowDuplicate: Boolean = false
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
        val newQ = McqQuestion(
            num = nextNum,
            question = questionText.trim(),
            options = QuestionOptions(
                a = optionA.trim().ifBlank { "Option A" },
                b = optionB.trim().ifBlank { "Option B" },
                c = optionC.trim().ifBlank { "Option C" },
                d = optionD.trim().ifBlank { "Option D" }
            ),
            correctAnswer = correctAnswer?.trim()?.uppercase()?.takeIf { it in listOf("A", "B", "C", "D") },
            explanation = explanation?.trim()?.takeIf { it.isNotBlank() },
            questionType = questionType.ifBlank { "Direct Fact" },
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
        negativeMarks: Double? = null
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

        val resolvedFolder = if (cleanSubject.contains("/") || cleanSubject.contains(">")) {
            resolveOrCreateFolderPath(cleanSubject)
        } else null
        val targetFolderId = resolvedFolder?.id ?: active.metadata.folderId

        val finalDuration = durationMinutes ?: active.metadata.durationMinutes
        val finalPosMarks = positiveMarks ?: active.metadata.positiveMarks
        val finalNegMarks = negativeMarks ?: active.metadata.negativeMarks

        val newMetadata = active.metadata.copy(
            title = cleanTitle,
            subject = cleanSubject,
            topic = cleanTopic,
            setNumber = cleanSet,
            rollNumber = cleanRoll,
            bookletNumber = cleanBooklet,
            folderId = targetFolderId,
            durationMinutes = finalDuration,
            positiveMarks = finalPosMarks,
            negativeMarks = finalNegMarks
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
            timeAllowed = "$finalDuration Minutes",
            maxMarks = "${(finalPosMarks * updatedPaper.questions.size).toInt()}"
        )

        if (!_isExamStarted.value) {
            _customTimeLimitMinutes.value = finalDuration
            _timeRemainingSeconds.value = finalDuration * 60L
        }

        schedulePaperAutoSave(updatedPaper)
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
        customNegativeMarks: Double? = null
    ) {
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
            val finalSubject = if (!customSubject.isNullOrBlank()) customSubject.trim() else parsed.metadata.subject

            // Automatically resolve slash-separated folder hierarchies (e.g. UPSC CSE / Prelims / GS 1)
            val effectiveFolderId: String? = if (!customFolderId.isNullOrBlank()) {
                customFolderId.trim()
            } else if (finalSubject.isNotBlank() && (finalSubject.contains("/") || finalSubject.contains(">"))) {
                resolveOrCreateFolderPath(finalSubject)?.id ?: parsed.metadata.folderId
            } else {
                parsed.metadata.folderId
            }

            val effectiveDuration = customDurationMinutes ?: _examConfig.value.durationMinutes.takeIf { it > 0 } ?: 60
            val effectivePositiveMarks = customPositiveMarks ?: _examConfig.value.positiveMarks.takeIf { it > 0.0 } ?: 4.0
            val effectiveNegativeMarks = customNegativeMarks ?: _examConfig.value.negativeMarks.takeIf { it >= 0.0 } ?: 1.0

            val finalMetadata = parsed.metadata.copy(
                title = if (!customTitle.isNullOrBlank()) customTitle.trim() else parsed.metadata.title,
                subject = finalSubject,
                topic = if (!customTopic.isNullOrBlank()) customTopic.trim() else parsed.metadata.topic,
                setNumber = formattedSet.ifBlank { "01" },
                folderId = effectiveFolderId,
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                durationMinutes = effectiveDuration,
                positiveMarks = effectivePositiveMarks,
                negativeMarks = effectiveNegativeMarks
            )
            val finalPaper = parsed.copy(metadata = finalMetadata)

            val existing = paperDao.findPaperByRawText(text.trim())
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
                    rollNumber = if (!customRollNumber.isNullOrBlank()) customRollNumber.trim() else (if (existing.rollNumber.isNotBlank()) existing.rollNumber else finalMetadata.rollNumber),
                    bookletNumber = if (!customBookletNumber.isNullOrBlank()) customBookletNumber.trim() else (if (existing.bookletNumber.isNotBlank()) existing.bookletNumber else finalMetadata.bookletNumber),
                    durationMinutes = effectiveDuration,
                    positiveMarks = effectivePositiveMarks,
                    negativeMarks = effectiveNegativeMarks,
                    updatedAt = System.currentTimeMillis()
                )
                paperDao.updatePaper(updated)
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
                    rollNumber = finalMetadata.rollNumber,
                    bookletNumber = finalMetadata.bookletNumber,
                    durationMinutes = effectiveDuration,
                    positiveMarks = effectivePositiveMarks,
                    negativeMarks = effectiveNegativeMarks
                )
                paperDao.insertPaper(newEntity)
            } else {
                0L
            }

            if (entityId > 0) {
                appPreferences.setLastActivePaperId(entityId)
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
                    maxMarks = "${(effectivePositiveMarks * finalPaper.questions.size).toInt()}",
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
        _isLoadingPaper.value = true
        _currentPaperEntityId.value = entity.id
        // Immediately synchronize timer limits and countdown to prevent lag/stale timer display
        val initialMinutes = if (entity.durationMinutes > 0) entity.durationMinutes else 60
        _customTimeLimitMinutes.value = initialMinutes
        _timeRemainingSeconds.value = initialMinutes * 60L
        _timerMode.value = ExamTimerMode.COUNTDOWN

        viewModelScope.launch(Dispatchers.Default) {
            try {
                loadSavedPaperInternal(entity)
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoadingPaper.value = false
                    onComplete?.invoke()
                }
            }
        }
    }

    private suspend fun loadSavedPaperInternal(entity: PaperEntity) {
        val freshEntity = if (entity.id > 0) {
            paperDao.getPaperById(entity.id) ?: entity
        } else {
            entity
        }
        val result = McqParser.parseWithResult(freshEntity.rawText)
        val finalRoll = if (freshEntity.rollNumber.isNotBlank()) freshEntity.rollNumber else ExamNumberGenerator.generateRollNumber()
        val finalBooklet = if (freshEntity.bookletNumber.isNotBlank()) freshEntity.bookletNumber else ExamNumberGenerator.generateBookletNumber()
        val savedDuration = if (freshEntity.durationMinutes > 0) freshEntity.durationMinutes else 60
        val savedPositive = if (freshEntity.positiveMarks > 0.0) freshEntity.positiveMarks else 4.0
        val savedNegative = if (freshEntity.negativeMarks >= 0.0) freshEntity.negativeMarks else 1.0

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
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                durationMinutes = savedDuration,
                positiveMarks = savedPositive,
                negativeMarks = savedNegative,
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
                _currentAttemptId.value = unsubmittedAttempt.id
                _studentAnswers.value = unsubmittedAttempt.getAnswersMap()
                _timeSpentSeconds.value = unsubmittedAttempt.timeSpentSeconds
                _isExamSubmitted.value = false
                _isExamStarted.value = false
                _retryQuestionNumbers.value = null
            } else {
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
                timeAllowed = "$savedDuration Minutes",
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                maxMarks = "${(savedPositive * parsed.questions.size).toInt()}"
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
            "bio" -> Pair(SampleData.SAMPLE_BIOLOGY_ADVANCED, "Samples & Practice")
            "patterns" -> Pair(SampleData.SAMPLE_NEXT_PATTERN, "Samples & Practice")
            "hangul", "korean" -> Pair(SampleData.SAMPLE_KOREAN_EXAM, "Samples & Practice")
            "hindi" -> Pair(SampleData.SAMPLE_HINDI_PAPER, "Samples & Practice")
            "science" -> Pair(SampleData.SAMPLE_SCIENCE_PAPER, "Samples & Practice")
            "civil", "engineering", "gate" -> Pair(SampleData.SAMPLE_CIVIL_MIXED_PATTERN, "Civil & Environmental Engineering")
            else -> Pair(SampleData.SAMPLE_STARTER_PROMPT, "Samples & Practice")
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
            0 -> "science"
            1 -> "hindi"
            2 -> "hangul"
            3 -> "bio"
            4 -> "patterns"
            5 -> "civil"
            else -> "science"
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
        customSetNumber: String? = null
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
                            customSetNumber = customSetNumber
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
                    customFolderId = paper.metadata.folderId
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
            score = result.totalScore.toDouble().coerceAtLeast(0.0),
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
        val questionPapers = papers.map { entity ->
            val parsed = McqParser.parse(entity.rawText)
            parsed.copy(
                id = entity.id,
                metadata = PaperMetadata(
                    id = "paper-${entity.id}",
                    title = entity.title,
                    subject = entity.subject,
                    topic = entity.topic,
                    setNumber = entity.setNumber.ifBlank { "Set 1" },
                    exam = entity.exam,
                    folderId = entity.folderId,
                    rollNumber = if (entity.rollNumber.isNotBlank()) entity.rollNumber else ExamNumberGenerator.generateRollNumber(),
                    bookletNumber = if (entity.bookletNumber.isNotBlank()) entity.bookletNumber else ExamNumberGenerator.generateBookletNumber(),
                    createdAt = entity.createdAt
                )
            )
        }
        val firstTitle = questionPapers.firstOrNull()?.metadata?.title ?: "Examination Package"
        val firstFolder = questionPapers.firstOrNull()?.metadata?.folderId
        return ExamJsonHelper.bundleToJson(firstTitle, firstFolder, questionPapers)
    }

    fun exportSelectedPapersAsJson(selectedPaperIds: Set<Long>): String? {
        val papers = savedPapers.value.filter { it.id in selectedPaperIds }
        if (papers.isEmpty()) return null
        val questionPapers = papers.map { entity ->
            val parsed = McqParser.parse(entity.rawText)
            parsed.copy(
                id = entity.id,
                metadata = PaperMetadata(
                    id = "paper-${entity.id}",
                    title = entity.title,
                    subject = entity.subject,
                    topic = entity.topic,
                    setNumber = entity.setNumber.ifBlank { "Set 1" },
                    exam = entity.exam,
                    folderId = entity.folderId,
                    rollNumber = if (entity.rollNumber.isNotBlank()) entity.rollNumber else ExamNumberGenerator.generateRollNumber(),
                    bookletNumber = if (entity.bookletNumber.isNotBlank()) entity.bookletNumber else ExamNumberGenerator.generateBookletNumber(),
                    createdAt = entity.createdAt
                )
            )
        }
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

        val questionPapers = papers.map { entity ->
            val parsed = McqParser.parse(entity.rawText)
            parsed.copy(
                id = entity.id,
                metadata = PaperMetadata(
                    id = "paper-${entity.id}",
                    title = entity.title,
                    subject = entity.subject,
                    topic = entity.topic,
                    setNumber = entity.setNumber.ifBlank { "Set 1" },
                    exam = entity.exam,
                    folderId = entity.folderId,
                    rollNumber = if (entity.rollNumber.isNotBlank()) entity.rollNumber else ExamNumberGenerator.generateRollNumber(),
                    bookletNumber = if (entity.bookletNumber.isNotBlank()) entity.bookletNumber else ExamNumberGenerator.generateBookletNumber(),
                    createdAt = entity.createdAt
                )
            )
        }

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
        val parsed = McqParser.parse(paper.rawText)
        val questionPaper = parsed.copy(
            id = paper.id,
            metadata = PaperMetadata(
                id = "paper-${paper.id}",
                title = paper.title,
                subject = paper.subject,
                topic = paper.topic,
                setNumber = paper.setNumber.ifBlank { "Set 1" },
                exam = paper.exam,
                folderId = paper.folderId,
                rollNumber = if (paper.rollNumber.isNotBlank()) paper.rollNumber else ExamNumberGenerator.generateRollNumber(),
                bookletNumber = if (paper.bookletNumber.isNotBlank()) paper.bookletNumber else ExamNumberGenerator.generateBookletNumber(),
                createdAt = paper.createdAt
            )
        )
        return ExamJsonHelper.toJson(questionPaper)
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

            // Auto-create folder if a new non-empty custom folder name is provided
            val effectiveTarget = targetFolder?.trim()
            if (!effectiveTarget.isNullOrBlank() &&
                !effectiveTarget.equals("all", ignoreCase = true) &&
                !effectiveTarget.equals("Default / From Metadata", ignoreCase = true) &&
                !effectiveTarget.equals("Keep Original", ignoreCase = true)
            ) {
                val exists = _folders.value.any { it.name.equals(effectiveTarget, ignoreCase = true) }
                if (!exists) {
                    val newFolder = FolderItem(
                        id = "f-${System.currentTimeMillis().toString(36)}",
                        name = effectiveTarget,
                        color = "#0284C7"
                    )
                    withContext(Dispatchers.Main) {
                        val updated = _folders.value + newFolder
                        _folders.value = updated
                        appPreferences.saveCustomFolders(updated)
                    }
                }
            }

            validItems.forEach { item ->
                try {
                    val p = item.paper!!
                    val finalSubject = if (!effectiveTarget.isNullOrBlank() &&
                        !effectiveTarget.equals("all", ignoreCase = true) &&
                        !effectiveTarget.equals("Default / From Metadata", ignoreCase = true) &&
                        !effectiveTarget.equals("Keep Original", ignoreCase = true)
                    ) {
                        effectiveTarget
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

                    val updatedPaper = p.copy(
                        metadata = p.metadata.copy(
                            subject = finalSubject,
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
                        folderId = updatedPaper.metadata.folderId,
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
        return null
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

        val updatedFolders = _folders.value.map {
            if (it.id == folderId) it.copy(name = cleanName) else it
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

        val updatedFolders = _folders.value.map {
            if (it.id == folderId) it.copy(color = newColor) else it
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

        val updatedFolders = _folders.value.map {
            if (it.id == folderId) it.copy(parentId = newParentId) else it
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
            else -> "#0284C7"
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
        appPreferences.setActiveTabIndex(index)
    }

    // --- Student Test Interactions & Attempt Persistence ---

    fun selectAnswer(qNum: Int, option: String) {
        val current = _studentAnswers.value.toMutableMap()
        if (option.isBlank()) {
            current.remove(qNum)
        } else {
            current[qNum] = option
        }
        _studentAnswers.value = current

        // When in Review Mode (isExamSubmitted == true), user interactions are for review/practice only.
        // Attending or correcting answers in review mode does NOT modify the official frozen exam score.
        if (!_isExamSubmitted.value) {
            saveCurrentAttempt()
        }
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

    private fun startTimerTicker() {
        examTimerJob?.cancel()
        examTimerJob = viewModelScope.launch {
            var tick = 0
            while (_isExamStarted.value && !_isExamSubmitted.value) {
                delay(1000L)
                if (!_isExamStarted.value || _isExamSubmitted.value) break

                _timeSpentSeconds.value += 1L
                _todayTestSeconds.value += 1L
                tick++

                if (_timerMode.value == ExamTimerMode.COUNTDOWN) {
                    if (_timeRemainingSeconds.value > 0) {
                        _timeRemainingSeconds.value -= 1L
                        if (_timeRemainingSeconds.value <= 0L) {
                            withContext(Dispatchers.Main) {
                                submitExam()
                                _statusMessage.value = "Time's up! Examination auto-submitted."
                            }
                            break
                        }
                    }
                }

                // Save attempt and daily analytics periodically every 10 seconds to avoid disk thrashing
                if (tick % 10 == 0) {
                    saveCurrentAttempt()
                    saveTodayDailyAnalytics()
                }
            }
        }
    }

    private fun stopTimerTicker() {
        examTimerJob?.cancel()
        examTimerJob = null
        saveTodayDailyAnalytics()
    }

    private fun saveCurrentAttempt() {
        val paperId = _currentPaperEntityId.value ?: return
        val attemptId = _currentAttemptId.value
        val paper = _currentPaper.value ?: return
        val currentTime = _timeSpentSeconds.value
        val answers = _studentAnswers.value
        val isSubmitted = _isExamSubmitted.value

        // If attempt was already submitted, do not overwrite the official one-time score from review changes
        if (isSubmitted) {
            return
        }

        val stats = calculateTestStats(paper, answers)

        viewModelScope.launch(Dispatchers.IO) {
            val answersJson = AttemptEntity.buildAnswersJson(answers)
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
                    submittedAt = if (isSubmitted) System.currentTimeMillis() else null
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
                        submittedAt = if (isSubmitted) (existing.submittedAt ?: System.currentTimeMillis()) else null
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
        _submittedAnswers.value = answersSnapshot

        // One-time official scoring calculation
        val stats = calculateTestStats(paper, answersSnapshot)
        _submittedStats.value = stats

        // Make log for review of all questions answered incorrectly at submission time
        val incorrectSet = paper.questions.filter { q ->
            val userAns = answersSnapshot[q.num]
            !userAns.isNullOrBlank() && !userAns.equals(q.correctAnswer, ignoreCase = true)
        }.map { it.num }.toSet()
        _incorrectQuestionLog.value = incorrectSet

        val unattendedSet = paper.questions.filter { q ->
            answersSnapshot[q.num].isNullOrBlank()
        }.map { it.num }.toSet()
        _unattendedQuestionLog.value = unattendedSet

        viewModelScope.launch(Dispatchers.IO) {
            val answersJson = AttemptEntity.buildAnswersJson(answersSnapshot)
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
                    submittedAt = System.currentTimeMillis()
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
                    submittedAt = System.currentTimeMillis()
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

        val stats = Companion.TestStats(
            total = result.totalQuestions,
            correct = result.correctCount,
            incorrect = result.incorrectCount + result.multipleMarkedCount,
            unanswered = result.unattemptedCount,
            score = result.totalScore.toDouble().coerceAtLeast(0.0)
        )
        _submittedStats.value = stats

        val incorrectSet = result.questionResults.values
            .filter { q ->
                val officialAns = paper.questions.getOrNull(q.questionNum - 1)?.correctAnswer?.uppercase()?.firstOrNull()?.toString()
                q.isMultipleMarked || (q.selectedOption != null && !q.selectedOption.toString().equals(officialAns, ignoreCase = true))
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
                submittedAt = System.currentTimeMillis()
            )

            val newId = paperDao.insertAttempt(scannedAttempt)
            withContext(Dispatchers.Main) {
                _currentAttemptId.value = newId
                val rollSnippet = if (finalRoll.isNotBlank()) " | Roll: $finalRoll" else ""
                _statusMessage.value = "OMR sheet evaluated$rollSnippet: Score ${stats.score.toInt()} / ${result.maxPossibleScore}"
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
                val userAns = answersMap[q.num]
                !userAns.isNullOrBlank() && !userAns.equals(q.correctAnswer, ignoreCase = true)
            }.map { it.num }.toSet()
            _unattendedQuestionLog.value = paper.questions.filter { q ->
                answersMap[q.num].isNullOrBlank()
            }.map { it.num }.toSet()
        }

        _selectedTab.value = 2 // Switch to student test tab
    }

    fun updateExamConfig(config: ExamConfig) {
        _examConfig.value = config
        val active = _currentPaper.value
        if (active != null) {
            val updatedMeta = active.metadata.copy(
                durationMinutes = config.durationMinutes,
                positiveMarks = config.positiveMarks,
                negativeMarks = config.negativeMarks
            )
            val updatedPaper = active.copy(metadata = updatedMeta)
            _currentPaper.value = updatedPaper
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

    fun toggleCardBoxed() {
        _isCardBoxed.value = !_isCardBoxed.value
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun closeCurrentPaper() {
        clearCurrentPaper()
    }

    fun clearCurrentPaper() {
        _currentPaper.value = null
        _currentPaperEntityId.value = null
        _rawInputText.value = ""
        _studentAnswers.value = emptyMap()
        _isExamSubmitted.value = false
        _isExamStarted.value = false
        _currentAttemptId.value = null
        appPreferences.setLastActivePaperId(-1L)
        appPreferences.setDraftRawText("")
    }

    // --- Student Test & Study Analytics Persistence ---

    fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun initTodayStudyAnalytics() {
        viewModelScope.launch(Dispatchers.IO) {
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
        }
    }

    fun saveTodayDailyAnalytics() {
        val todayStr = getTodayDateString()
        val currentSecs = _todayTestSeconds.value
        val goalMins = _dailyGoalMinutes.value
        viewModelScope.launch(Dispatchers.IO) {
            val existing = paperDao.getDailyAnalytics(todayStr)
            val updated = if (existing != null) {
                existing.copy(
                    totalTestTimeSeconds = currentSecs,
                    dailyGoalSeconds = goalMins * 60L,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            } else {
                DailyStudyAnalyticsEntity(
                    dateString = todayStr,
                    totalTestTimeSeconds = currentSecs,
                    dailyGoalSeconds = goalMins * 60L,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            }
            paperDao.upsertDailyAnalytics(updated)
            withContext(Dispatchers.Main) {
                _todayStudyAnalytics.value = updated
            }
        }
    }

    private fun recordTestCompletionInDailyAnalytics(
        questionsAttempted: Int,
        correctCount: Int
    ) {
        val todayStr = getTodayDateString()
        val currentSecs = _todayTestSeconds.value
        val goalMins = _dailyGoalMinutes.value
        viewModelScope.launch(Dispatchers.IO) {
            val existing = paperDao.getDailyAnalytics(todayStr)
            val updated = if (existing != null) {
                existing.copy(
                    totalTestTimeSeconds = currentSecs,
                    testsCompletedCount = existing.testsCompletedCount + 1,
                    questionsAttemptedCount = existing.questionsAttemptedCount + questionsAttempted,
                    correctAnswersCount = existing.correctAnswersCount + correctCount,
                    dailyGoalSeconds = goalMins * 60L,
                    lastUpdatedTimestamp = System.currentTimeMillis()
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
                    lastUpdatedTimestamp = System.currentTimeMillis()
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
            val existing = paperDao.getDailyAnalytics(todayStr)
            val updated = if (existing != null) {
                existing.copy(
                    totalTestTimeSeconds = currentSecs,
                    testsStartedCount = existing.testsStartedCount + 1,
                    dailyGoalSeconds = goalMins * 60L,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            } else {
                DailyStudyAnalyticsEntity(
                    dateString = todayStr,
                    totalTestTimeSeconds = currentSecs,
                    testsStartedCount = 1,
                    dailyGoalSeconds = goalMins * 60L,
                    lastUpdatedTimestamp = System.currentTimeMillis()
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
            val negativeMarks: Double = 0.0
        ) {
            val formattedScore: String
                get() = if (score % 1.0 == 0.0) score.toInt().toString() else String.format(java.util.Locale.US, "%.2f", score)

            val percentage: Int
                get() = if (maxPossibleMarks > 0) ((score / maxPossibleMarks) * 100.0).toInt().coerceIn(0, 100)
                        else if (total > 0) ((score / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                        else 0
        }

        fun calculateTestStats(
            paper: QuestionPaper,
            answers: Map<Int, String>,
            positiveMarks: Double = paper.metadata.positiveMarks,
            negativeMarks: Double = paper.metadata.negativeMarks
        ): TestStats {
            var correct = 0
            var incorrect = 0
            paper.questions.forEach { q ->
                if (q.isNeutralized) {
                    // Standard exam board rule: Neutralized / withdrawn questions award full marks to all candidates
                    correct++
                } else {
                    val userAns = answers[q.num]
                    if (!userAns.isNullOrBlank()) {
                        if (q.correctAnswer.equals(userAns, ignoreCase = true)) {
                            correct++
                        } else {
                            incorrect++
                        }
                    }
                }
            }
            val unanswered = paper.questions.size - (correct + incorrect)

            val pos = if (positiveMarks > 0.0) positiveMarks else 4.0
            val neg = if (negativeMarks >= 0.0) negativeMarks else 1.0

            val penaltyIncorrect = Math.round(incorrect * neg * 100.0) / 100.0
            val penaltyUnattended = if (pos == 1.0 && Math.abs(neg - 0.33) < 0.01) Math.round(unanswered * 0.10 * 100.0) / 100.0 else 0.0
            val rawScore = (correct * pos) - penaltyIncorrect - penaltyUnattended
            val rawScoreRounded = Math.round(rawScore * 100.0) / 100.0

            // The minimum score shall be 0 regardless of how many penalties are incurred
            val finalScore = maxOf(0.0, rawScoreRounded)
            val maxMarks = paper.questions.size * pos

            return TestStats(
                total = paper.questions.size,
                correct = correct,
                incorrect = incorrect,
                unanswered = unanswered,
                score = finalScore,
                rawScore = rawScoreRounded,
                penaltyIncorrect = penaltyIncorrect,
                penaltyUnattended = penaltyUnattended,
                maxPossibleMarks = maxMarks,
                positiveMarks = pos,
                negativeMarks = neg
            )
        }
    }
}
