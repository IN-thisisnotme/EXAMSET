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

    private fun restoreAutosavedState() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastPaperId = appPreferences.getLastActivePaperId()
            var loaded = false

            if (lastPaperId > 0) {
                val entity = paperDao.getPaperById(lastPaperId)
                if (entity != null) {
                    loadSavedPaperInternal(entity)
                    loaded = true
                }
            }

            if (!loaded) {
                // Check draft raw text
                val draft = appPreferences.getDraftRawText()
                if (draft.isNotBlank()) {
                    val result = McqParser.parseWithResult(draft)
                    withContext(Dispatchers.Main) {
                        _rawInputText.value = draft
                        _parseResult.value = result
                        if (result.paper.questions.isNotEmpty()) {
                            _currentPaper.value = result.paper
                            val initialQCount = result.paper.questions.size.coerceAtLeast(1)
                            _examConfig.value = _examConfig.value.copy(
                                examTitle = result.paper.metadata.title,
                                subject = result.paper.metadata.subject,
                                seriesCode = result.paper.metadata.setNumber.ifBlank { "SET - A" },
                                timeAllowed = "$initialQCount Minutes",
                                maxMarks = "${result.paper.questions.size}",
                                rollNumber = result.paper.metadata.rollNumber,
                                bookletNumber = result.paper.metadata.bookletNumber
                            )
                        }
                    }
                    loaded = true
                } else {
                    // Check if any papers already exist in Room database
                    val allSaved = paperDao.getAllPapers().first()
                    if (allSaved.isNotEmpty()) {
                        loadSavedPaperInternal(allSaved.first())
                        loaded = true
                    } else {
                        // First-time cold launch: seed starter sample exam set
                        withContext(Dispatchers.Main) {
                            loadStarterSample("science")
                        }
                        loaded = true
                    }
                }
            }

            // Restore saved tab if valid
            val savedTab = appPreferences.getActiveTabIndex()
            withContext(Dispatchers.Main) {
                if (savedTab in 0..6) {
                    _selectedTab.value = savedTab
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
                        bookletNumber = updatedPaper.metadata.bookletNumber
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
                        bookletNumber = updatedPaper.metadata.bookletNumber
                    )
                    paperDao.insertPaper(newEntity)
                }
                targetId
            }

            appPreferences.setLastActivePaperId(targetId)

            // Recalculate any submitted attempts for this paper so scores/breakdown stay 100% accurate
            recalculateAttemptsForPaper(targetId, updatedPaper)

            withContext(Dispatchers.Main) {
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
        bookletNumber: String? = null
    ) {
        val active = _currentPaper.value ?: return
        val cleanTitle = title.trim().ifBlank { "MCQ Practice Set" }
        val cleanSubject = subject.trim().ifBlank { "General Studies" }
        val cleanTopic = topic.trim()
        val cleanSet = setNumber.trim().ifBlank { "Set A" }
        val cleanRoll = rollNumber?.filter { it.isDigit() }?.take(10)?.ifBlank { null }
            ?: active.metadata.rollNumber.ifBlank { ExamNumberGenerator.generateRollNumber() }
        val cleanBooklet = bookletNumber?.filter { it.isDigit() }?.take(7)?.ifBlank { null }
            ?: active.metadata.bookletNumber.ifBlank { ExamNumberGenerator.generateBookletNumber() }

        val newMetadata = active.metadata.copy(
            title = cleanTitle,
            subject = cleanSubject,
            topic = cleanTopic,
            setNumber = cleanSet,
            rollNumber = cleanRoll,
            bookletNumber = cleanBooklet
        )
        val updatedPaper = active.copy(metadata = newMetadata)

        _examConfig.value = _examConfig.value.copy(
            examTitle = cleanTitle,
            subject = cleanSubject,
            seriesCode = cleanSet,
            rollNumber = cleanRoll,
            bookletNumber = cleanBooklet
        )

        schedulePaperAutoSave(updatedPaper)
        _statusMessage.value = "Updated: $cleanTitle ($cleanSet)"
    }

    // --- Loading & Parsing Papers ---

    fun parseAndSetPaper(
        text: String,
        customTitle: String? = null,
        customSubject: String? = null,
        customTopic: String? = null,
        customSetNumber: String? = null
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val result = McqParser.parseWithResult(text)
            val parsed = result.paper
            val finalMetadata = parsed.metadata.copy(
                title = if (!customTitle.isNullOrBlank()) customTitle.trim() else parsed.metadata.title,
                subject = if (!customSubject.isNullOrBlank()) customSubject.trim() else parsed.metadata.subject,
                topic = if (!customTopic.isNullOrBlank()) customTopic.trim() else parsed.metadata.topic,
                setNumber = if (!customSetNumber.isNullOrBlank()) customSetNumber.trim() else parsed.metadata.setNumber
            )
            val finalPaper = parsed.copy(metadata = finalMetadata)

            val existing = paperDao.findPaperByRawText(text.trim())
            val entityId: Long = if (existing != null) {
                val updated = existing.copy(
                    title = finalMetadata.title,
                    subject = finalMetadata.subject,
                    topic = finalMetadata.topic,
                    setNumber = finalMetadata.setNumber,
                    questionCount = finalPaper.questions.size,
                    answerKeyCount = finalPaper.quickAnswersCount,
                    explanationCount = finalPaper.explanationsCount,
                    folderId = finalMetadata.folderId ?: existing.folderId,
                    rollNumber = if (existing.rollNumber.isNotBlank()) existing.rollNumber else finalMetadata.rollNumber,
                    bookletNumber = if (existing.bookletNumber.isNotBlank()) existing.bookletNumber else finalMetadata.bookletNumber,
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
                    bookletNumber = finalMetadata.bookletNumber
                )
                paperDao.insertPaper(newEntity)
            } else {
                0L
            }

            if (entityId > 0) {
                appPreferences.setLastActivePaperId(entityId)
            }
            appPreferences.setDraftRawText(text)

            withContext(Dispatchers.Main) {
                _currentPaperEntityId.value = if (entityId > 0) entityId else null
                _currentPaper.value = finalPaper.copy(id = entityId)
                _rawInputText.value = text
                _parseResult.value = result
                _studentAnswers.value = emptyMap()
                _isExamSubmitted.value = false
                _isExamStarted.value = false
                _retryQuestionNumbers.value = null
                val autoMinutes = finalPaper.questions.size.coerceAtLeast(1)
                _examConfig.value = _examConfig.value.copy(
                    examTitle = finalMetadata.title,
                    subject = finalMetadata.subject,
                    seriesCode = finalMetadata.setNumber.ifBlank { "SET - A" },
                    timeAllowed = "$autoMinutes Minutes",
                    maxMarks = "${finalPaper.questions.size}",
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
        _currentPaperEntityId.value = entity.id
        // Immediately synchronize timer limits and countdown to prevent lag/stale timer display
        val initialMinutes = entity.questionCount.coerceAtLeast(1)
        _customTimeLimitMinutes.value = initialMinutes
        _timeRemainingSeconds.value = initialMinutes * 60L
        _timerMode.value = ExamTimerMode.COUNTDOWN

        viewModelScope.launch(Dispatchers.Default) {
            loadSavedPaperInternal(entity)
            withContext(Dispatchers.Main) {
                onComplete?.invoke()
            }
        }
    }

    private suspend fun loadSavedPaperInternal(entity: PaperEntity) {
        val result = McqParser.parseWithResult(entity.rawText)
        val finalRoll = if (entity.rollNumber.isNotBlank()) entity.rollNumber else ExamNumberGenerator.generateRollNumber()
        val finalBooklet = if (entity.bookletNumber.isNotBlank()) entity.bookletNumber else ExamNumberGenerator.generateBookletNumber()

        val parsed = result.paper.copy(
            id = entity.id,
            metadata = PaperMetadata(
                id = "paper-${entity.id}",
                title = entity.title,
                subject = entity.subject,
                topic = entity.topic,
                setNumber = entity.setNumber.ifBlank { "Set A" },
                exam = entity.exam,
                folderId = entity.folderId,
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                createdAt = entity.createdAt
            )
        )
        val unsubmittedAttempt = paperDao.getLatestUnsubmittedAttempt(entity.id)
        appPreferences.setLastActivePaperId(entity.id)

        if (entity.rollNumber.isBlank() || entity.bookletNumber.isBlank()) {
            paperDao.updatePaper(
                entity.copy(
                    rollNumber = finalRoll,
                    bookletNumber = finalBooklet
                )
            )
        }

        withContext(Dispatchers.Main) {
            _currentPaperEntityId.value = entity.id
            _currentPaper.value = parsed
            // Keep creator tab clean so opening a paper does not overwrite user's creator draft
            _parseResult.value = result

            // Default countdown timer to Auto (number of questions in paper)
            val autoMinutes = parsed.questions.size.coerceAtLeast(1)
            _customTimeLimitMinutes.value = autoMinutes
            _timeRemainingSeconds.value = autoMinutes * 60L
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
                seriesCode = entity.setNumber.ifBlank { "SET - A" },
                timeAllowed = "$autoMinutes Minutes",
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                maxMarks = "${parsed.questions.size}"
            )
            _autoSaveStatus.value = AutoSaveStatus.SAVED
            updateTimestamp()
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
            val paper = ExamJsonHelper.fromJson(jsonStr)
            val raw = McqParser.reconstructToRaw(paper)
            parseAndSetPaper(raw, paper.metadata.title, paper.metadata.subject, paper.metadata.topic, paper.metadata.setNumber)
            _statusMessage.value = "Imported: ${paper.metadata.title} (${paper.questions.size} questions)"
            true
        } catch (e: Exception) {
            _statusMessage.value = "Import failed: ${e.localizedMessage}"
            false
        }
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
                    setNumber = entity.setNumber.ifBlank { "Set A" },
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
                    setNumber = entity.setNumber.ifBlank { "Set A" },
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
                    val entity = PaperEntity(
                        title = p.metadata.title,
                        subject = p.metadata.subject.ifBlank { "General Knowledge" },
                        topic = p.metadata.topic,
                        setNumber = p.metadata.setNumber.ifBlank { "Set A" },
                        exam = p.metadata.exam,
                        questionCount = p.questions.size,
                        answerKeyCount = p.quickAnswersCount,
                        explanationCount = p.explanationsCount,
                        rawText = rawText,
                        createdAt = if (p.createdAt > 0) p.createdAt else System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        folderId = p.metadata.folderId,
                        rollNumber = if (p.metadata.rollNumber.isNotBlank()) p.metadata.rollNumber else ExamNumberGenerator.generateRollNumber(),
                        bookletNumber = if (p.metadata.bookletNumber.isNotBlank()) p.metadata.bookletNumber else ExamNumberGenerator.generateBookletNumber()
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
        targetFolder: String? = null
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

                    val updatedPaper = p.copy(
                        metadata = p.metadata.copy(subject = finalSubject)
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
                        rollNumber = if (updatedPaper.metadata.rollNumber.isNotBlank()) updatedPaper.metadata.rollNumber else ExamNumberGenerator.generateRollNumber(),
                        bookletNumber = if (updatedPaper.metadata.bookletNumber.isNotBlank()) updatedPaper.metadata.bookletNumber else ExamNumberGenerator.generateBookletNumber()
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

    // --- Folders Management ---

    fun createFolder(name: String, color: String) {
        if (name.isBlank()) return
        val newFolder = FolderItem(
            id = "f-${System.currentTimeMillis().toString(36)}",
            name = name.trim(),
            color = color
        )
        val updated = _folders.value + newFolder
        _folders.value = updated
        appPreferences.saveCustomFolders(updated)
        _statusMessage.value = "Folder created: ${newFolder.name}"
    }

    fun deleteFolder(folderId: String) {
        val updated = _folders.value.filter { it.id != folderId }
        _folders.value = updated
        appPreferences.saveCustomFolders(updated)
        _statusMessage.value = "Folder deleted"
    }

    fun assignPaperToFolder(paper: PaperEntity, folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = paper.copy(
                subject = folderName.trim(),
                updatedAt = System.currentTimeMillis()
            )
            paperDao.updatePaper(updated)
            if (_currentPaperEntityId.value == paper.id) {
                val currentP = _currentPaper.value
                if (currentP != null) {
                    val updatedMeta = currentP.metadata.copy(subject = folderName.trim())
                    _currentPaper.value = currentP.copy(metadata = updatedMeta)
                }
            }
            _statusMessage.value = "Assigned to folder: $folderName"
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
     * updating state flows so student can immediately review question-by-question explanations.
     */
    fun submitScannedOmrResult(result: OmrVisionProcessor.OmrScanOutput) {
        val paperId = _currentPaperEntityId.value ?: return
        val paper = _currentPaper.value ?: return

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
            val answersJson = AttemptEntity.buildAnswersJson(answersSnapshot)
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
                _statusMessage.value = "OMR sheet evaluated & recorded: Score ${stats.score.toInt()} / ${result.maxPossibleScore}"
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
        viewModelScope.launch(Dispatchers.IO) {
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
            val penaltyUnattended: Double = 0.0
        ) {
            val formattedScore: String
                get() = if (score % 1.0 == 0.0) score.toInt().toString() else String.format(java.util.Locale.US, "%.2f", score)

            val percentage: Int
                get() = if (total > 0) ((score / total.toDouble()) * 100.0).toInt().coerceIn(0, 100) else 0
        }

        fun calculateTestStats(paper: QuestionPaper, answers: Map<Int, String>): TestStats {
            var correct = 0
            var incorrect = 0
            paper.questions.forEach { q ->
                val userAns = answers[q.num]
                if (!userAns.isNullOrBlank()) {
                    if (q.correctAnswer.equals(userAns, ignoreCase = true)) {
                        correct++
                    } else {
                        incorrect++
                    }
                }
            }
            val unanswered = paper.questions.size - (correct + incorrect)

            // Penalty points: 0.33 for incorrect, 0.10 for unattended
            val penaltyIncorrect = Math.round(incorrect * 0.33 * 100.0) / 100.0
            val penaltyUnattended = Math.round(unanswered * 0.10 * 100.0) / 100.0
            val rawScore = (correct * 1.0) - penaltyIncorrect - penaltyUnattended
            val rawScoreRounded = Math.round(rawScore * 100.0) / 100.0

            // The minimum score shall be 0 regardless of how many penalties are incurred
            val finalScore = maxOf(0.0, rawScoreRounded)

            return TestStats(
                total = paper.questions.size,
                correct = correct,
                incorrect = incorrect,
                unanswered = unanswered,
                score = finalScore,
                rawScore = rawScoreRounded,
                penaltyIncorrect = penaltyIncorrect,
                penaltyUnattended = penaltyUnattended
            )
        }
    }
}
