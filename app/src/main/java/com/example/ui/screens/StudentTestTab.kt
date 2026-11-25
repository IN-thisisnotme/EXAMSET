package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import com.example.util.ExamNumberGenerator
import com.example.util.QuestionFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AttemptEntity
import com.example.data.AttemptPacingSummary
import com.example.data.DailyStudyAnalyticsEntity
import com.example.data.QuestionPacingAnalyzer
import com.example.model.ExamConfig
import com.example.model.McqQuestion
import com.example.model.MsqScoringPolicy
import com.example.model.QuestionPaper
import com.example.ui.ExamTimerMode
import com.example.ui.components.StudentPracticeCard
import com.example.ui.components.AttemptHistoryDialog
import com.example.ui.components.LatexText
import com.example.ui.components.ExamPaletteSideSheet
import com.example.ui.components.PerQuestionPacingCard
import com.example.ui.components.StudentHistoricalAnalysisDialog
import com.example.ui.theme.ExamTheme
import com.example.util.AnswerKeyMatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ReviewFilter { ALL, CORRECT, PARTIAL, INCORRECT, UNANSWERED }

enum class ResultHubTab(val label: String) {
    SCORECARD("Scorecard"),
    SOLUTIONS("Solutions"),
    DIAGNOSTICS("Diagnostics")
}

enum class ReviewSolutionsMode { LIST, SINGLE_QUESTION }

enum class ExamInterfaceMode(val title: String, val subtitle: String) {
    TCS_CBT("Standard CBT Mode", "Single question with Save & Next latency, Review & Next, and 5-state palette"),
    SCROLLABLE_PAPER("Scrollable Paper Mode", "Continuous scrollable exam paper with instant option selection")
}

@Composable
fun StudentTestTab(
    paper: QuestionPaper,
    answers: Map<Int, String>,
    config: ExamConfig = ExamConfig(),
    submittedStats: com.example.ui.MainViewModel.Companion.TestStats? = null,
    submittedAnswers: Map<Int, String> = emptyMap(),
    incorrectQuestionLog: Set<Int> = emptySet(),
    unattendedQuestionLog: Set<Int> = emptySet(),
    isExamStarted: Boolean,
    isSubmitted: Boolean,
    retryQuestionNumbers: List<Int>?,
    timeSpentSeconds: Long,
    timeRemainingSeconds: Long,
    timerMode: ExamTimerMode,
    customTimeLimitMinutes: Int,
    attempts: List<AttemptEntity>,
    todayTestSeconds: Long = 0L,
    todayAnalytics: DailyStudyAnalyticsEntity? = null,
    historicalRecords: List<DailyStudyAnalyticsEntity> = emptyList(),
    dailyGoalMinutes: Int = 30,
    onSetDailyGoalMinutes: (Int) -> Unit = {},
    onSetTimerMode: (ExamTimerMode) -> Unit,
    onSetCustomTimeLimit: (Int) -> Unit,
    onStartExam: (List<Int>?, Int?) -> Unit,
    onResumeExam: () -> Unit,
    onExitToOverview: () -> Unit,
    onSelectAnswer: (Int, String) -> Unit,
    onSubmitExam: () -> Unit,
    onResetExam: () -> Unit,
    onLoadAttempt: (AttemptEntity) -> Unit,
    shuffleOptions: Boolean = false,
    sessionSeed: Long = 0L,
    onToggleShuffleOptions: (Boolean) -> Unit = {},
    onScanOmr: () -> Unit = {},
    onOpenPsychometrics: () -> Unit = {},
    onSaveWeakAreasToKnowledgeSet: ((QuestionPaper, Set<Int>, Set<Int>, Map<Int, String>) -> Unit)? = null,
    timeSpentSecondsFlow: StateFlow<Long>? = null,
    timeRemainingSecondsFlow: StateFlow<Long>? = null,
    todayTestSecondsFlow: StateFlow<Long>? = null,
    questionTimes: Map<Int, Long> = emptyMap(),
    questionTimesFlow: StateFlow<Map<Int, Long>>? = null,
    onActiveQuestionChanged: (Int?) -> Unit = {},
    cbtFontScaleStep: Int = 0,
    onSetCbtFontScaleStep: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val positiveMarks = remember(config.positiveMarks, paper.metadata.positiveMarks) {
        if (paper.metadata.positiveMarks > 0.0) paper.metadata.positiveMarks else config.positiveMarks
    }
    val negativeMarks = remember(config.negativeMarks, paper.metadata.negativeMarks) {
        if (paper.metadata.negativeMarks >= 0.0) paper.metadata.negativeMarks else config.negativeMarks
    }
    val unattendedMarks = remember(config.unattendedMarks, paper.metadata.unattendedMarks) {
        if (paper.metadata.unattendedMarks >= 0.0) paper.metadata.unattendedMarks else config.unattendedMarks
    }
    val durationMinutes = remember(config.durationMinutes, paper.metadata.durationMinutes) {
        if (paper.metadata.durationMinutes > 0) paper.metadata.durationMinutes else config.durationMinutes
    }

    var showHistoryDialog by remember { mutableStateOf(false) }
    var showHistoricalAnalyticsDialog by remember { mutableStateOf(false) }
    var examInterfaceMode by remember { mutableStateOf(ExamInterfaceMode.TCS_CBT) }

    // Active questions: targeted retry set or full paper
    val activeQuestions = remember(paper.questions, retryQuestionNumbers) {
        if (retryQuestionNumbers != null) {
            paper.questions.filter { it.num in retryQuestionNumbers }
        } else {
            paper.questions
        }
    }

    if (showHistoryDialog) {
        AttemptHistoryDialog(
            attempts = attempts,
            onDismiss = { showHistoryDialog = false },
            onLoadAttempt = {
                showHistoryDialog = false
                onLoadAttempt(it)
            }
        )
    }

    if (showHistoricalAnalyticsDialog) {
        val analyticsQuestionTimes = questionTimesFlow?.collectAsState()?.value ?: questionTimes
        val pacingSummaryForAnalytics: AttemptPacingSummary? = remember(
            paper,
            submittedAnswers,
            answers,
            isSubmitted,
            analyticsQuestionTimes,
            attempts
        ) {
            when {
                analyticsQuestionTimes.values.any { it > 0L } -> {
                    val evalAnswers = if (isSubmitted && submittedAnswers.isNotEmpty()) submittedAnswers else answers
                    QuestionPacingAnalyzer.computeSummary(paper, evalAnswers, analyticsQuestionTimes)
                }
                else -> {
                    val latestWithTiming = attempts.firstOrNull {
                        it.isSubmitted && it.getQuestionTimesMap().values.any { t -> t > 0L }
                    }
                    latestWithTiming?.let {
                        QuestionPacingAnalyzer.computeSummary(paper, it.getAnswersMap(), it.getQuestionTimesMap())
                    }
                }
            }
        }
        StudentHistoricalAnalysisDialog(
            todayTestSeconds = todayTestSeconds,
            todayAnalytics = todayAnalytics,
            historicalRecords = historicalRecords,
            dailyGoalMinutes = dailyGoalMinutes,
            isTestCurrentlyRunning = isExamStarted && !isSubmitted,
            onSetDailyGoalMinutes = onSetDailyGoalMinutes,
            onDismiss = { showHistoricalAnalyticsDialog = false },
            todayTestSecondsFlow = todayTestSecondsFlow,
            pacingSummary = pacingSummaryForAnalytics
        )
    }

    when {
        isSubmitted -> {
            ExamResultAndReviewView(
                paper = paper,
                answers = answers,
                submittedStats = submittedStats,
                submittedAnswers = submittedAnswers,
                incorrectQuestionLog = incorrectQuestionLog,
                unattendedQuestionLog = unattendedQuestionLog,
                positiveMarks = positiveMarks,
                negativeMarks = negativeMarks,
                unattendedMarks = unattendedMarks,
                timeSpentSeconds = timeSpentSeconds,
                todayTestSeconds = todayTestSeconds,
                onRetakeFull = onResetExam,
                onRetryQuestions = { qNums -> onStartExam(qNums, null) },
                onExitToOverview = onExitToOverview,
                onSelectAnswer = onSelectAnswer,
                onOpenHistory = { showHistoryDialog = true },
                onOpenAnalytics = { showHistoricalAnalyticsDialog = true },
                onOpenPsychometrics = onOpenPsychometrics,
                onSaveWeakAreasToKnowledgeSet = onSaveWeakAreasToKnowledgeSet,
                timeSpentSecondsFlow = timeSpentSecondsFlow,
                todayTestSecondsFlow = todayTestSecondsFlow,
                questionTimes = questionTimes,
                questionTimesFlow = questionTimesFlow,
                modifier = modifier
            )
        }
        isExamStarted -> {
            ActiveExamView(
                paper = paper,
                activeQuestions = activeQuestions,
                answers = answers,
                positiveMarks = positiveMarks,
                negativeMarks = negativeMarks,
                timeSpentSeconds = timeSpentSeconds,
                timeRemainingSeconds = timeRemainingSeconds,
                timerMode = timerMode,
                retryQuestionNumbers = retryQuestionNumbers,
                todayTestSeconds = todayTestSeconds,
                shuffleOptions = shuffleOptions,
                sessionSeed = sessionSeed,
                initialInterfaceMode = examInterfaceMode,
                onSelectAnswer = onSelectAnswer,
                onSubmitExam = onSubmitExam,
                onExitToOverview = onExitToOverview,
                onSwitchToFull = { onStartExam(null, null) },
                onOpenAnalytics = { showHistoricalAnalyticsDialog = true },
                timeSpentSecondsFlow = timeSpentSecondsFlow,
                timeRemainingSecondsFlow = timeRemainingSecondsFlow,
                todayTestSecondsFlow = todayTestSecondsFlow,
                onActiveQuestionChanged = onActiveQuestionChanged,
                cbtFontScaleStep = cbtFontScaleStep,
                onSetCbtFontScaleStep = onSetCbtFontScaleStep,
                modifier = modifier
            )
        }
        else -> {
            ExamStartOverviewView(
                paper = paper,
                answers = answers,
                timeSpentSeconds = timeSpentSeconds,
                timerMode = timerMode,
                customTimeLimitMinutes = customTimeLimitMinutes,
                positiveMarks = positiveMarks,
                negativeMarks = negativeMarks,
                unattendedMarks = unattendedMarks,
                attempts = attempts,
                todayTestSeconds = todayTestSeconds,
                dailyGoalMinutes = dailyGoalMinutes,
                shuffleOptions = shuffleOptions,
                examInterfaceMode = examInterfaceMode,
                onSelectExamInterfaceMode = { examInterfaceMode = it },
                onToggleShuffleOptions = onToggleShuffleOptions,
                onSetTimerMode = onSetTimerMode,
                onSetCustomTimeLimit = onSetCustomTimeLimit,
                onStartExam = { onStartExam(null, customTimeLimitMinutes) },
                onResumeExam = onResumeExam,
                onOpenHistory = { showHistoryDialog = true },
                onOpenAnalytics = { showHistoricalAnalyticsDialog = true },
                onOpenPsychometrics = onOpenPsychometrics,
                onLoadAttempt = onLoadAttempt,
                onScanOmr = onScanOmr,
                timeSpentSecondsFlow = timeSpentSecondsFlow,
                todayTestSecondsFlow = todayTestSecondsFlow,
                modifier = modifier
            )
        }
    }
}

/**
 * Screen 1: Exam Landing Overview & Instructions.
 * Displays exam details and prevents the test from starting until user clicks "Start Test".
 */
@Composable
private fun ExamStartOverviewView(
    paper: QuestionPaper,
    answers: Map<Int, String>,
    timeSpentSeconds: Long,
    timerMode: ExamTimerMode,
    customTimeLimitMinutes: Int,
    positiveMarks: Double = 4.0,
    negativeMarks: Double = 1.0,
    unattendedMarks: Double = 0.0,
    attempts: List<AttemptEntity>,
    todayTestSeconds: Long,
    dailyGoalMinutes: Int,
    shuffleOptions: Boolean,
    examInterfaceMode: ExamInterfaceMode = ExamInterfaceMode.TCS_CBT,
    onSelectExamInterfaceMode: (ExamInterfaceMode) -> Unit = {},
    onToggleShuffleOptions: (Boolean) -> Unit,
    onSetTimerMode: (ExamTimerMode) -> Unit,
    onSetCustomTimeLimit: (Int) -> Unit,
    onStartExam: () -> Unit,
    onResumeExam: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenPsychometrics: () -> Unit = {},
    onLoadAttempt: (AttemptEntity) -> Unit,
    onScanOmr: () -> Unit = {},
    timeSpentSecondsFlow: StateFlow<Long>? = null,
    todayTestSecondsFlow: StateFlow<Long>? = null,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val totalQuestions = paper.questions.size
    val hasUnfinishedSession = answers.isNotEmpty()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 860.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Header Title Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.tealLight
                        ) {
                            Text(
                                text = ExamNumberGenerator.formatSetDisplayLabel(paper.metadata.setNumber),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val cttTint = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            TextButton(onClick = onOpenPsychometrics) {
                                Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp), tint = cttTint)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Item CTT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cttTint)
                            }
                            if (attempts.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                TextButton(onClick = onOpenHistory) {
                                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("History (${attempts.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = paper.metadata.title.ifBlank { "MCQ Practice Examination" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )

                    val sub = paper.metadata.subject.ifBlank { "General Studies" }
                    val topic = paper.metadata.topic
                    Text(
                        text = if (topic.isNotBlank()) "$sub • $topic" else sub,
                        fontSize = 13.sp,
                        color = colors.muted
                    )
                }
            }
        }

        // Today's Study & Test Timer Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAnalytics() }
                    .testTag("today_timer_overview_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                border = BorderStroke(1.2.dp, colors.teal.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Today's Test Study Tracker",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.tealLight,
                            border = BorderStroke(0.8.dp, colors.teal)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Historical Analysis",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.teal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TodayTrackerTimeLeaf(
                        todayTestSecondsFlow = todayTestSecondsFlow,
                        fallbackSeconds = todayTestSeconds,
                        dailyGoalMinutes = dailyGoalMinutes
                    )
                }
            }
        }

        // 4 Key Exam Metrics
        item {
            val maxMarksVal = totalQuestions * positiveMarks
            val formattedMaxMarks = String.format(java.util.Locale.US, "%.2f", maxMarksVal)
            val formattedPenalty = if (negativeMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", negativeMarks) else String.format(java.util.Locale.US, "-%.2f", negativeMarks)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    label = "Questions",
                    value = "$totalQuestions",
                    icon = Icons.Default.Info,
                    tint = colors.teal,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Marks",
                    value = formattedMaxMarks,
                    icon = Icons.Default.CheckCircle,
                    tint = colors.green,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = if (timerMode == ExamTimerMode.COUNTDOWN) "Limit" else "Mode",
                    value = if (timerMode == ExamTimerMode.COUNTDOWN) "${customTimeLimitMinutes}m" else "Open",
                    icon = if (timerMode == ExamTimerMode.COUNTDOWN) Icons.Default.HourglassBottom else Icons.Default.Timer,
                    tint = colors.teal,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Penalty",
                    value = formattedPenalty,
                    icon = Icons.Default.Remove,
                    tint = colors.red,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Scoring Scheme Info Strip (Font-Scale Safe)
        item {
            val formattedPenalty = if (negativeMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", negativeMarks) else String.format(java.util.Locale.US, "-%.2f", negativeMarks)
            val formattedPos = if (positiveMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "+%.0f", positiveMarks) else String.format(java.util.Locale.US, "+%.2f", positiveMarks)
            val formattedUnk = if (unattendedMarks > 0.0) {
                if (unattendedMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", unattendedMarks)
                else String.format(java.util.Locale.US, "-%.2f", unattendedMarks)
            } else {
                "0.00"
            }
            val msqCount = remember(paper.questions) { paper.questions.count { AnswerKeyMatcher.isMultiSelect(it) } }
            val effectiveMsqPolicy = paper.metadata.msqScoringPolicy

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$formattedPos Correct  •  $formattedPenalty Incorrect  •  $formattedUnk Unattended",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold,
                                softWrap = true
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.tealLight.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "Net Scoring",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.tealDark,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (msqCount > 0) {
                        val policyLabel = if (effectiveMsqPolicy == MsqScoringPolicy.PARTIAL_STRICT) {
                            "Partial Credit (zero wrong selections)"
                        } else {
                            "All-or-Nothing"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.amberLight,
                            border = BorderStroke(0.8.dp, colors.amber.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckBox,
                                    contentDescription = null,
                                    tint = colors.amber,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Contains $msqCount MSQ (Multiple-Select) item${if (msqCount == 1) "" else "s"} • Grading: $policyLabel",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.amber,
                                    softWrap = true
                                )
                            }
                        }
                    }
                }
            }
        }

        // Examination Interface Style Selector: TCS iON CBT Mode vs Scrollable Paper Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.2.dp, colors.teal.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val cbtBg = if (colors.isDark) Color(0xFF172554) else Color(0xFFEFF6FF)
                    val cbtBorder = if (colors.isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
                    val cbtAccent = if (colors.isDark) Color(0xFF93C5FD) else Color(0xFF2563EB)
                    val cbtTitle = if (colors.isDark) Color(0xFFDBEAFE) else Color(0xFF1E3A8A)
                    val cbtSub = if (colors.isDark) Color(0xFFBFDBFE) else Color(0xFF1E40AF)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Exam Interface Style",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (examInterfaceMode == ExamInterfaceMode.TCS_CBT) cbtBg else colors.tealLight,
                            border = BorderStroke(0.8.dp, if (examInterfaceMode == ExamInterfaceMode.TCS_CBT) cbtBorder else colors.teal)
                        ) {
                            Text(
                                text = if (examInterfaceMode == ExamInterfaceMode.TCS_CBT) "Standard CBT Mode" else "Scrollable Paper",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (examInterfaceMode == ExamInterfaceMode.TCS_CBT) cbtAccent else colors.teal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mode Selection Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Option 1: TCS iON CBT Mode
                        val isCbtSelected = examInterfaceMode == ExamInterfaceMode.TCS_CBT
                        Surface(
                            onClick = { onSelectExamInterfaceMode(ExamInterfaceMode.TCS_CBT) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCbtSelected) cbtBg else colors.surfaceVariant,
                            border = BorderStroke(
                                width = if (isCbtSelected) 2.dp else 1.dp,
                                color = if (isCbtSelected) cbtBorder else colors.border
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Standard CBT Mode",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isCbtSelected) cbtTitle else colors.inkBold
                                    )
                                    Icon(
                                        imageVector = if (isCbtSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isCbtSelected) cbtAccent else colors.muted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Single question viewport, Save & Next delay, Review & Next, and 5-state palette.",
                                    fontSize = 11.sp,
                                    color = if (isCbtSelected) cbtSub else colors.muted,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        // Option 2: Scrollable Paper Mode
                        val isScrollSelected = examInterfaceMode == ExamInterfaceMode.SCROLLABLE_PAPER
                        Surface(
                            onClick = { onSelectExamInterfaceMode(ExamInterfaceMode.SCROLLABLE_PAPER) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isScrollSelected) colors.tealLight.copy(alpha = 0.5f) else colors.surfaceVariant,
                            border = BorderStroke(
                                width = if (isScrollSelected) 2.dp else 1.dp,
                                color = if (isScrollSelected) colors.teal else colors.border
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Scrollable Paper",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isScrollSelected) colors.tealDark else colors.inkBold
                                    )
                                    Icon(
                                        imageVector = if (isScrollSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isScrollSelected) colors.teal else colors.muted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Continuous all-in-one question feed with immediate option selection.",
                                    fontSize = 11.sp,
                                    color = if (isScrollSelected) colors.tealDark else colors.muted,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Test Timer & Session Configuration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.2.dp, colors.teal.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (timerMode == ExamTimerMode.COUNTDOWN) Icons.Default.HourglassBottom else Icons.Default.Timer,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Test Timer & Mode Setup",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.tealLight
                        ) {
                            Text(
                                text = if (timerMode == ExamTimerMode.COUNTDOWN) "Timed Countdown" else "Open Stopwatch",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mode Segment Selector: Countdown vs Stopwatch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant, RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSetTimerMode(ExamTimerMode.COUNTDOWN) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (timerMode == ExamTimerMode.COUNTDOWN) colors.surface else Color.Transparent,
                            border = if (timerMode == ExamTimerMode.COUNTDOWN) BorderStroke(1.dp, colors.border) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.HourglassBottom,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (timerMode == ExamTimerMode.COUNTDOWN) colors.teal else colors.muted
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Countdown",
                                    fontSize = 12.sp,
                                    fontWeight = if (timerMode == ExamTimerMode.COUNTDOWN) FontWeight.Bold else FontWeight.Medium,
                                    color = if (timerMode == ExamTimerMode.COUNTDOWN) colors.inkBold else colors.muted,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSetTimerMode(ExamTimerMode.STOPWATCH) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (timerMode == ExamTimerMode.STOPWATCH) colors.surface else Color.Transparent,
                            border = if (timerMode == ExamTimerMode.STOPWATCH) BorderStroke(1.dp, colors.border) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (timerMode == ExamTimerMode.STOPWATCH) colors.teal else colors.muted
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Stopwatch",
                                    fontSize = 12.sp,
                                    fontWeight = if (timerMode == ExamTimerMode.STOPWATCH) FontWeight.Bold else FontWeight.Medium,
                                    color = if (timerMode == ExamTimerMode.STOPWATCH) colors.inkBold else colors.muted,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (timerMode == ExamTimerMode.COUNTDOWN) {
                        // Custom Time Limit Input Field
                        Text(
                            text = "Set Custom Time Limit (in minutes)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        var timeInputText by remember(customTimeLimitMinutes) {
                            mutableStateOf(customTimeLimitMinutes.toString())
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Stepper Decrement
                            IconButton(
                                onClick = {
                                    val newVal = (customTimeLimitMinutes - 5).coerceAtLeast(1)
                                    onSetCustomTimeLimit(newVal)
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease 5m", tint = colors.inkBold)
                            }

                            // Numeric Input Field
                            OutlinedTextField(
                                value = timeInputText,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(3)
                                    timeInputText = filtered
                                    filtered.toIntOrNull()?.let { min ->
                                        if (min in 1..360) {
                                            onSetCustomTimeLimit(min)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    Text("min", fontSize = 12.sp, color = colors.muted, modifier = Modifier.padding(end = 8.dp))
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.teal,
                                    unfocusedBorderColor = colors.border,
                                    focusedTextColor = colors.inkBold,
                                    unfocusedTextColor = colors.inkBold
                                )
                            )

                            // Stepper Increment
                            IconButton(
                                onClick = {
                                    val newVal = (customTimeLimitMinutes + 5).coerceAtMost(360)
                                    onSetCustomTimeLimit(newVal)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase 5m", tint = colors.inkBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Preset Chips
                        Text(
                            text = "Quick Presets:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.muted
                        )
                        Spacer(modifier = Modifier.height(5.dp))

                        val presets = listOf(
                            15 to "15m",
                            30 to "30m",
                            45 to "45m",
                            60 to "60m",
                            120 to "120m",
                            totalQuestions.coerceAtLeast(1) to "Auto ($totalQuestions m)"
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(presets) { (mins, label) ->
                                val isSelected = customTimeLimitMinutes == mins
                                Surface(
                                    modifier = Modifier.clickable { onSetCustomTimeLimit(mins) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) colors.tealLight else colors.surfaceVariant,
                                    border = BorderStroke(1.dp, if (isSelected) colors.teal else colors.border)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) colors.teal else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val pace = (customTimeLimitMinutes.toFloat() / totalQuestions.coerceAtLeast(1))
                        val formattedPace = String.format(java.util.Locale.US, "%.1f", pace)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.tealLight.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = colors.teal, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Estimated pace: ~$formattedPace min/question. Examination auto-submits when countdown reaches 00:00.",
                                    fontSize = 11.sp,
                                    color = colors.teal,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    } else {
                        // Stopwatch Mode Info
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.tealLight.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = colors.teal, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Open stopwatch practice. Time counts up from 00:00 without cutoff pressure until you submit.",
                                    fontSize = 11.5.sp,
                                    color = colors.teal,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Option Shuffling Toggle Row
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (shuffleOptions) colors.tealLight.copy(alpha = 0.5f) else colors.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (shuffleOptions) colors.teal.copy(alpha = 0.6f) else colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = null,
                                        tint = if (shuffleOptions) colors.teal else colors.inkBold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Shuffle Options for Mock Test",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (shuffleOptions)
                                        "Active: Options A–D randomized during test to prevent position memorization. Automatically restores original order on submit so answer keys & solutions match perfectly."
                                        else "Standard: Options presented in original input order (A, B, C, D).",
                                    fontSize = 11.sp,
                                    color = if (shuffleOptions) colors.tealDark else colors.muted,
                                    lineHeight = 15.sp
                                )
                            }

                            Switch(
                                checked = shuffleOptions,
                                onCheckedChange = onToggleShuffleOptions,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.surface,
                                    checkedTrackColor = colors.teal,
                                    uncheckedThumbColor = colors.muted,
                                    uncheckedTrackColor = colors.surfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }

        // Instructions Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Examination Guidelines",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    InstructionBullet(
                        number = "1",
                        title = "Practice at your own pace",
                        description = "Timer starts as soon as you tap 'Start Test'. You can pause or exit anytime with your answers safely preserved."
                    )
                    InstructionBullet(
                        number = "2",
                        title = "Interactive answer selection",
                        description = "Tap Option A, B, C, or D to record an answer. Tap the chosen option again if you wish to clear or skip it."
                    )
                    val bulletPos = if (positiveMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "+%.0f", positiveMarks) else String.format(java.util.Locale.US, "+%.2f", positiveMarks)
                    val bulletNeg = if (negativeMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", negativeMarks) else String.format(java.util.Locale.US, "-%.2f", negativeMarks)
                    val bulletUnk = if (unattendedMarks > 0.0) {
                        if (unattendedMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", unattendedMarks)
                        else String.format(java.util.Locale.US, "-%.2f", unattendedMarks)
                    } else {
                        "0.00"
                    }
                    InstructionBullet(
                        number = "3",
                        title = "Net Scoring & Penalty Scheme",
                        description = "Correct: $bulletPos mark(s). Incorrect: $bulletNeg mark(s). Unattended: $bulletUnk mark(s). Final score reflects exact net marks earned."
                    )
                    InstructionBullet(
                        number = "4",
                        title = "One-Time Scoring & Review Mode",
                        description = "Official test score is evaluated once upon submission. Attending or correcting questions in review mode allows study practice without altering your official score. Retake the full test to re-evaluate."
                    )
                    InstructionBullet(
                        number = "5",
                        title = "Targeted retry & review logs",
                        description = "Incorrect questions are logged for review and remain in the review log even after revision. Launch focused retries without re-doing the entire test."
                    )
                }
            }
        }

        // Previous Attempt Banner if available
        if (attempts.isNotEmpty()) {
            val latest = attempts.first()
            val maxPossibleScore = latest.totalQuestions * positiveMarks
            val pct = if (maxPossibleScore > 0) ((latest.score / maxPossibleScore) * 100).toInt().coerceIn(0, 100) else 0
            val formattedMaxScore = String.format(java.util.Locale.US, "%.2f", maxPossibleScore)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.tealLight),
                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Previous Attempt", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                            Text(
                                "Score: ${latest.getFormattedScore()} / $formattedMaxScore ($pct%)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.inkBold
                            )
                        }
                        OutlinedButton(
                            onClick = { onLoadAttempt(latest) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, colors.teal),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal)
                        ) {
                            Text("Review Solutions", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Unfinished session resume option
        if (hasUnfinishedSession) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.2.dp, colors.teal)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Unfinished Test Session",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.teal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val effectiveResumeSeconds = timeSpentSecondsFlow?.collectAsState()?.value ?: timeSpentSeconds
                        val m = effectiveResumeSeconds / 60
                        val s = effectiveResumeSeconds % 60
                        Text(
                            text = "You have ${answers.size} answered questions (${String.format(java.util.Locale.US, "%02d:%02d", m, s)} elapsed). Would you like to resume or start fresh?",
                            fontSize = 12.sp,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onStartExam,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Text(
                                    text = "Start Fresh",
                                    fontSize = 12.sp,
                                    color = colors.inkBold,
                                    maxLines = 2,
                                    softWrap = true,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = onResumeExam,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primaryButtonBg,
                                    contentColor = colors.primaryButtonText
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Resume Test",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    softWrap = true,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Main Primary Start Test Button & Scan Physical OMR Sheet Button
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartExam,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("start_test_button"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.navyBanner,
                            contentColor = colors.navyBannerText
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (timerMode == ExamTimerMode.COUNTDOWN) Icons.Default.HourglassBottom else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val modePrefix = if (examInterfaceMode == ExamInterfaceMode.TCS_CBT) "Start TCS CBT Exam" else "Start Scrollable Exam"
                        Text(
                            text = if (timerMode == ExamTimerMode.COUNTDOWN) {
                                "$modePrefix (${customTimeLimitMinutes}m)"
                            } else {
                                modePrefix
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            softWrap = true,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Scan Physical OMR Sheet Button
                    val omrPink = if (colors.isDark) Color(0xFFF472B6) else Color(0xFFBE185D)
                    OutlinedButton(
                        onClick = onScanOmr,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("scan_physical_omr_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.2.dp, omrPink.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = omrPink
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = omrPink
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan Filled OMR Sheet",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            softWrap = true,
                            textAlign = TextAlign.Center,
                            color = omrPink
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
    }
}

/**
 * Screen 2: Authentic TCS iON Computer-Based Test (CBT) Active Exam Session.
 * Replicates the exact TCS exam interface:
 * - Single question viewport with high-contrast fonts
 * - Staged pending option selection (does NOT auto-save or turn green on click)
 * - "Save & Next" with realistic simulated local server save latency (400ms spinner)
 * - "Mark for Review & Next", "Clear Response", and "Previous" action buttons
 * - Immersive Full-Screen mode toggle for distraction-free exam simulation
 * - Official TCS 5-State question status logic and geometry
 * - Full TCS Examination Summary modal upon submission
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActiveExamView(
    paper: QuestionPaper,
    activeQuestions: List<McqQuestion>,
    answers: Map<Int, String>,
    positiveMarks: Double = 4.0,
    negativeMarks: Double = 1.0,
    timeSpentSeconds: Long,
    timeRemainingSeconds: Long,
    timerMode: ExamTimerMode,
    retryQuestionNumbers: List<Int>?,
    todayTestSeconds: Long = 0L,
    shuffleOptions: Boolean = false,
    sessionSeed: Long = 0L,
    initialInterfaceMode: ExamInterfaceMode = ExamInterfaceMode.TCS_CBT,
    onSelectAnswer: (Int, String) -> Unit,
    onSubmitExam: () -> Unit,
    onExitToOverview: () -> Unit,
    onSwitchToFull: () -> Unit,
    onOpenAnalytics: () -> Unit = {},
    timeSpentSecondsFlow: StateFlow<Long>? = null,
    timeRemainingSecondsFlow: StateFlow<Long>? = null,
    todayTestSecondsFlow: StateFlow<Long>? = null,
    onActiveQuestionChanged: (Int?) -> Unit = {},
    cbtFontScaleStep: Int = 0,
    onSetCbtFontScaleStep: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var interfaceMode by remember { mutableStateOf(initialInterfaceMode) }
    // [DEPRECATED 2026-11-25] Reason: Session-only fontScaleDelta reset between papers/restarts. Restore if: per-session reset is desired:
    // var fontScaleDelta by remember { mutableIntStateOf(0) }
    var fontScaleDelta by remember(cbtFontScaleStep) {
        mutableIntStateOf(cbtFontScaleStep.coerceIn(-1, 1))
    }
    val scrollListState = rememberLazyListState()

    val qFontSize = when (fontScaleDelta) {
        -1 -> 13.5.sp
        1 -> 18.sp
        else -> 15.5.sp
    }
    val optFontSize = when (fontScaleDelta) {
        -1 -> 12.5.sp
        1 -> 16.5.sp
        else -> 14.5.sp
    }
    val qLineHeight = when (fontScaleDelta) {
        -1 -> 20.sp
        1 -> 27.sp
        else -> 23.sp
    }
    val optLineHeight = when (fontScaleDelta) {
        -1 -> 18.sp
        1 -> 24.sp
        else -> 21.sp
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    val currentQuestion = activeQuestions.getOrNull(currentIndex) ?: activeQuestions.first()

    // Authentic TCS iON 5-State tracking
    val markedForReview = remember { mutableStateListOf<Int>() }
    val visitedQuestions = remember { mutableStateListOf<Int>().apply { add(currentQuestion.num) } }

    // Two-Phase selection: Tapping an option sets pendingOption (draft radio selection).
    // It is ONLY committed to official answers and marked Answered (Green) when candidate clicks "Save & Next"!
    var pendingOption by remember(currentQuestion.num) {
        mutableStateOf(answers[currentQuestion.num] ?: "")
    }

    var isSavingNext by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    val isFreshExamStart = remember(paper.id, retryQuestionNumbers) {
        (timeSpentSecondsFlow?.value ?: timeSpentSeconds) <= 2L && answers.isEmpty()
    }
    var readinessCountdown by remember(paper.id, retryQuestionNumbers) {
        mutableIntStateOf(if (isFreshExamStart) 3 else 0)
    }

    LaunchedEffect(readinessCountdown) {
        if (readinessCountdown > 0) {
            delay(1000L)
            readinessCountdown = (readinessCountdown - 1).coerceAtLeast(0)
        }
    }

    var showSubmitConfirmDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showCbtPaletteSideSheet by remember { mutableStateOf(false) }

    // Per-question pacing telemetry: track visible question and pause on lifecycle/overlay
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isLifecycleResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isLifecycleResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var scrolledQuestionNum by remember(activeQuestions) {
        mutableStateOf(activeQuestions.firstOrNull()?.num)
    }
    LaunchedEffect(scrollListState, activeQuestions) {
        snapshotFlow { scrollListState.firstVisibleItemIndex }
            .collect { firstIdx ->
                if (activeQuestions.isNotEmpty()) {
                    // Item 0 is the top Spacer; activeQuestions start at LazyColumn index 1
                    val qIdx = (firstIdx - 1).coerceIn(0, activeQuestions.lastIndex)
                    scrolledQuestionNum = activeQuestions.getOrNull(qIdx)?.num
                } else {
                    scrolledQuestionNum = null
                }
            }
    }

    LaunchedEffect(
        isLifecycleResumed,
        readinessCountdown,
        showSubmitConfirmDialog,
        showExitConfirmDialog,
        showCbtPaletteSideSheet,
        interfaceMode,
        currentQuestion.num,
        scrolledQuestionNum
    ) {
        val activeNum = if (
            !isLifecycleResumed ||
            readinessCountdown > 0 ||
            showSubmitConfirmDialog ||
            showExitConfirmDialog ||
            showCbtPaletteSideSheet
        ) {
            null
        } else if (interfaceMode == ExamInterfaceMode.TCS_CBT) {
            currentQuestion.num
        } else {
            scrolledQuestionNum
        }
        onActiveQuestionChanged(activeNum)
    }

    DisposableEffect(Unit) {
        onDispose {
            onActiveQuestionChanged(null)
        }
    }

    // Immersive Full-Screen controller
    LaunchedEffect(isFullscreen) {
        activity?.window?.let { window ->
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val totalQuestions = activeQuestions.size
    val answeredCount = activeQuestions.count { answers[it.num]?.isNotBlank() == true && it.num !in markedForReview }
    val notAnsweredCount = activeQuestions.count { it.num in visitedQuestions && answers[it.num].isNullOrBlank() && it.num !in markedForReview }
    val markedOnlyCount = activeQuestions.count { answers[it.num].isNullOrBlank() && it.num in markedForReview }
    val ansMarkedCount = activeQuestions.count { answers[it.num]?.isNotBlank() == true && it.num in markedForReview }
    val notVisitedCount = activeQuestions.count { it.num !in visitedQuestions }

    // Exit / Pause Confirmation
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            containerColor = colors.surface,
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = colors.teal) },
            title = { Text("Pause or Exit Test?", fontWeight = FontWeight.Bold, color = colors.inkBold) },
            text = { Text("Your saved answers are preserved. You can resume anytime from the exam overview.", color = colors.ink) },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        onExitToOverview()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryButtonBg,
                        contentColor = colors.primaryButtonText
                    )
                ) {
                    Text("Exit to Overview", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitConfirmDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.teal)
                ) {
                    Text("Resume Test")
                }
            }
        )
    }

    // Official TCS Exam Summary Submission Modal
    if (showSubmitConfirmDialog) {
        val summaryPurple = if (colors.isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)
        AlertDialog(
            onDismissRequest = { showSubmitConfirmDialog = false },
            containerColor = colors.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = colors.teal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Examination Summary",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.inkBold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${paper.metadata.title.ifBlank { "Examination Paper" }} • ${ExamNumberGenerator.formatSetDisplayLabel(paper.metadata.setNumber)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.muted
                    )

                    // Official TCS Summary Grid
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.border),
                        color = colors.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TcsSummaryRow(label = "Total Questions", value = "$totalQuestions", color = colors.inkBold)
                            TcsSummaryRow(label = "Answered", value = "$answeredCount", color = colors.green)
                            TcsSummaryRow(label = "Not Answered", value = "$notAnsweredCount", color = colors.red)
                            TcsSummaryRow(label = "Marked for Review", value = "$markedOnlyCount", color = summaryPurple)
                            TcsSummaryRow(label = "Ans & Marked for Review", value = "$ansMarkedCount", color = summaryPurple)
                            TcsSummaryRow(label = "Not Visited", value = "$notVisitedCount", color = colors.muted)
                        }
                    }

                    if (notAnsweredCount + notVisitedCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.badgeDangerBg,
                            border = BorderStroke(0.8.dp, colors.red)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = colors.red, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Notice: ${notAnsweredCount + notVisitedCount} question(s) are unattempted.",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.badgeDangerText
                                )
                            }
                        }
                    }

                    Text(
                        text = "Are you sure you want to submit? After submission you will not be able to change your answers.",
                        fontSize = 12.sp,
                        color = colors.ink
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSubmitConfirmDialog = false
                        onSubmitExam()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (colors.isDark) Color(0xFF059669) else Color(0xFF0F172A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_submit_test_button")
                ) {
                    Text("Yes, Submit Test", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSubmitConfirmDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.6f))
                ) {
                    Text("Resume Test")
                }
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isWideLandscape = maxWidth >= 560.dp && maxWidth > maxHeight
    val submitBtnBg = if (colors.isDark) Color(0xFF059669) else Color(0xFF0F172A)
    val selectedOptionBg = if (colors.isDark) Color(0xFF172554) else Color(0xFFEFF6FF)
    val selectedOptionBorder = if (colors.isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val selectedOptionText = if (colors.isDark) Color(0xFFDBEAFE) else Color(0xFF1E3A8A)
    val reviewPurple = if (colors.isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
    val clearOrange = if (colors.isDark) Color(0xFFFB923C) else Color(0xFFEA580C)
    val unvisitedBg = if (colors.isDark) colors.surfaceVariant else Color(0xFFE2E8F0)
    val unvisitedText = if (colors.isDark) colors.inkBold else Color(0xFF334155)
    val currentStripBorder = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0F172A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // --- 1. Top Header Bar (Matching HTML CBT: Font Scaling, Mode Switch, Digital Timer, Palette) ---
        Surface(
            color = Color(0xFF0F172A),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back button + Title & Progress
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = true)
                ) {
                    IconButton(
                        onClick = { showExitConfirmDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit to Overview",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Column {
                        Text(
                            text = paper.metadata.subject.ifBlank { paper.metadata.title.ifBlank { "Examination" } },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (interfaceMode == ExamInterfaceMode.TCS_CBT) "Q ${currentIndex + 1} / $totalQuestions" else "$answeredCount / $totalQuestions answered",
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Right controls: horizontally scrollable so 48dp targets & large font scales never crush the title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // Font Scale Group (A-, A, A+) matching HTML CBT and persisted in preferences
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(0.6.dp, Color(0xFF334155))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf(-1 to "A-", 0 to "A", 1 to "A+").forEach { (step, label) ->
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clickable {
                                            fontScaleDelta = step
                                            onSetCbtFontScaleStep(step)
                                        }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (fontScaleDelta == step) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (fontScaleDelta == step) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    // Mode Switch Button (TCS CBT vs Scrollable Paper)
                    Surface(
                        onClick = {
                            interfaceMode = if (interfaceMode == ExamInterfaceMode.TCS_CBT) {
                                ExamInterfaceMode.SCROLLABLE_PAPER
                            } else {
                                ExamInterfaceMode.TCS_CBT
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = if (interfaceMode == ExamInterfaceMode.TCS_CBT) Color(0xFF1E3A8A) else Color(0xFF0F766E),
                        border = BorderStroke(0.8.dp, if (interfaceMode == ExamInterfaceMode.TCS_CBT) Color(0xFF3B82F6) else Color(0xFF14B8A6)),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (interfaceMode == ExamInterfaceMode.TCS_CBT) "Paper" else "CBT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Digital Timer Pill (leaf-isolated to prevent per-second outer recompositions)
                    ExamHeaderTimerLeaf(
                        timerMode = timerMode,
                        timeSpentSecondsFlow = timeSpentSecondsFlow,
                        timeRemainingSecondsFlow = timeRemainingSecondsFlow,
                        fallbackTimeSpentSeconds = timeSpentSeconds,
                        fallbackTimeRemainingSeconds = timeRemainingSeconds
                    )

                    // Fullscreen Toggle Button
                    IconButton(
                        onClick = { isFullscreen = !isFullscreen },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen Mode",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Palette Grid Button
                    IconButton(
                        onClick = { showCbtPaletteSideSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cbt_header_palette_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "Open CBT Palette",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // =====================================================================
        // MODE A: TCS iON CBT Mode (Authentic single question, Save & Next latency, 5-state palette)
        // =====================================================================
        if (interfaceMode == ExamInterfaceMode.TCS_CBT) {
            // Horizontal Quick Jump Strip with TCS 5-State Color Badges
            Surface(
                color = colors.surface,
                border = BorderStroke(0.6.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = if (isWideLandscape) 4.dp else 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(activeQuestions, key = { _, q -> "cbt_strip_${q.num}" }) { idx, q ->
                        val isCurrent = idx == currentIndex
                        val isAnswered = answers[q.num]?.isNotBlank() == true
                        val isMarked = markedForReview.contains(q.num)
                        val isVisited = visitedQuestions.contains(q.num)
                        val isAnsMarked = isAnswered && isMarked

                        val (bg, textCol) = when {
                            isAnsMarked || isMarked -> Pair(Color(0xFF7C3AED), Color.White)
                            isAnswered -> Pair(Color(0xFF22C55E), Color.White)
                            isVisited -> Pair(Color(0xFFEF4444), Color.White)
                            else -> Pair(unvisitedBg, unvisitedText)
                        }

                        Surface(
                            onClick = {
                                currentIndex = idx
                                if (!visitedQuestions.contains(q.num)) visitedQuestions.add(q.num)
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = bg,
                            border = BorderStroke(
                                width = if (isCurrent) 2.5.dp else 0.8.dp,
                                color = if (isCurrent) currentStripBorder else Color.Transparent
                            ),
                            modifier = Modifier.size(if (isWideLandscape) 28.dp else 32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "${q.num}",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = textCol
                                )
                                if (isAnsMarked) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(2.dp)
                                            .size(6.dp)
                                            .background(Color(0xFF22C55E), CircleShape)
                                            .border(0.5.dp, Color.White, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val posStr = if (positiveMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "+%.0f", positiveMarks) else String.format(java.util.Locale.US, "+%.2f", positiveMarks)
            val negStr = if (negativeMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", negativeMarks) else String.format(java.util.Locale.US, "-%.2f", negativeMarks)
            val isCurrentMsq = remember(currentQuestion) { AnswerKeyMatcher.isMultiSelect(currentQuestion) }

            @OptIn(ExperimentalLayoutApi::class)
            val questionMetaHeader: @Composable () -> Unit = {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Question No. ${currentQuestion.num}",
                        fontSize = if (isWideLandscape) 14.5.sp else 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.inkBold
                    )

                    if (isCurrentMsq) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.amberLight,
                            border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.7f)),
                            modifier = Modifier.testTag("cbt_msq_badge_${currentQuestion.num}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckBox,
                                    contentDescription = null,
                                    tint = colors.amber,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "MSQ • Select all that apply",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.amber,
                                    softWrap = true
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(4.dp), color = colors.badgeSuccessBg) {
                            Text(
                                text = "$posStr Mark",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.badgeSuccessText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(shape = RoundedCornerShape(4.dp), color = colors.badgeDangerBg) {
                            Text(
                                text = "$negStr Penalty",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.badgeDangerText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            val questionStemCard: @Composable () -> Unit = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.2.dp, colors.border),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 1.dp)
                ) {
                    Column(modifier = Modifier.padding(if (isWideLandscape) 12.dp else 16.dp)) {
                        val arParts = QuestionFormatter.parseAssertionReason(currentQuestion.question)
                        if (arParts != null) {
                            if (!arParts.leadIn.isNullOrBlank()) {
                                LatexText(
                                    text = arParts.leadIn,
                                    fontSize = qFontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.muted,
                                    lineHeight = qLineHeight
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surfaceVariant,
                                border = BorderStroke(0.8.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp)) {
                                    Text("Assertion (A): ", fontWeight = FontWeight.Bold, fontSize = qFontSize, color = colors.teal)
                                    LatexText(text = arParts.assertion, fontSize = qFontSize, color = colors.inkBold, lineHeight = qLineHeight)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surfaceVariant,
                                border = BorderStroke(0.8.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp)) {
                                    Text("Reason (R): ", fontWeight = FontWeight.Bold, fontSize = qFontSize, color = colors.amber)
                                    LatexText(text = arParts.reason, fontSize = qFontSize, color = colors.inkBold, lineHeight = qLineHeight)
                                }
                            }
                        } else {
                            LatexText(
                                text = currentQuestion.question,
                                fontSize = qFontSize,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                lineHeight = qLineHeight
                            )
                        }

                        if (!currentQuestion.formula.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surfaceVariant,
                                border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = currentQuestion.formula ?: "",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = colors.teal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            val optionsListColumn: @Composable () -> Unit = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(if (isWideLandscape) 6.dp else 8.dp)
                ) {
                    val rawOptions = listOfNotNull(
                        "A" to currentQuestion.options.a,
                        "B" to currentQuestion.options.b,
                        "C" to currentQuestion.options.c,
                        "D" to currentQuestion.options.d,
                        if (currentQuestion.options.e.isNotBlank()) "E" to currentQuestion.options.e else null
                    )

                    rawOptions.forEach { (key, optText) ->
                        if (optText.isNotBlank()) {
                            val isSelected = if (isCurrentMsq) {
                                AnswerKeyMatcher.containsOption(pendingOption, key)
                            } else {
                                pendingOption.equals(key, ignoreCase = true)
                            }
                            Surface(
                                onClick = {
                                    pendingOption = if (isCurrentMsq) {
                                        AnswerKeyMatcher.toggleSelection(pendingOption, key)
                                    } else {
                                        key
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) selectedOptionBg else colors.surface,
                                border = BorderStroke(
                                    width = if (isSelected) 1.8.dp else 1.dp,
                                    color = if (isSelected) selectedOptionBorder else colors.border
                                ),
                                shadowElevation = if (isSelected && !colors.isDark) 1.dp else 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("option_${key}_q${currentQuestion.num}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = if (isWideLandscape) 12.dp else 14.dp,
                                        vertical = if (isWideLandscape) 9.dp else 12.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isCurrentMsq && isSelected -> Icons.Default.CheckBox
                                            isCurrentMsq -> Icons.Default.CheckBoxOutlineBlank
                                            isSelected -> Icons.Default.RadioButtonChecked
                                            else -> Icons.Default.RadioButtonUnchecked
                                        },
                                        contentDescription = if (isCurrentMsq) {
                                            if (isSelected) "Option $key checked" else "Option $key unchecked"
                                        } else null,
                                        tint = if (isSelected) selectedOptionBorder else colors.muted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    LatexText(
                                        text = "$key. $optText",
                                        fontSize = optFontSize,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) selectedOptionText else colors.inkBold,
                                        lineHeight = optLineHeight,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Main Single Question Viewport (Responsive: 2-Pane Split in Landscape, Vertical in Portrait)
            if (isWideLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.47f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        questionMetaHeader()
                        questionStemCard()
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.53f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        optionsListColumn()
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(6.dp)) }
                    item { questionMetaHeader() }
                    item { questionStemCard() }
                    item { optionsListColumn() }
                    item { Spacer(modifier = Modifier.height(14.dp)) }
                }
            }

            val onMarkReviewNext: () -> Unit = {
                if (pendingOption.isNotBlank()) {
                    onSelectAnswer(currentQuestion.num, pendingOption)
                }
                if (!markedForReview.contains(currentQuestion.num)) {
                    markedForReview.add(currentQuestion.num)
                }
                if (currentIndex < activeQuestions.size - 1) {
                    currentIndex++
                    if (!visitedQuestions.contains(activeQuestions[currentIndex].num)) {
                        visitedQuestions.add(activeQuestions[currentIndex].num)
                    }
                }
            }
            val onClearResponse: () -> Unit = {
                pendingOption = ""
                onSelectAnswer(currentQuestion.num, "")
                markedForReview.remove(currentQuestion.num)
            }
            val onSaveNext: () -> Unit = {
                coroutineScope.launch {
                    isSavingNext = true
                    delay(400)
                    onSelectAnswer(currentQuestion.num, pendingOption)
                    markedForReview.remove(currentQuestion.num)
                    isSavingNext = false

                    if (currentIndex < activeQuestions.size - 1) {
                        currentIndex++
                        if (!visitedQuestions.contains(activeQuestions[currentIndex].num)) {
                            visitedQuestions.add(activeQuestions[currentIndex].num)
                        }
                    } else {
                        showSubmitConfirmDialog = true
                    }
                }
            }
            val onPrevious: () -> Unit = {
                if (currentIndex > 0) {
                    currentIndex--
                    if (!visitedQuestions.contains(activeQuestions[currentIndex].num)) {
                        visitedQuestions.add(activeQuestions[currentIndex].num)
                    }
                }
            }

            // Authentic TCS Bottom Sticky Action Bar
            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                if (isWideLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onPrevious,
                            enabled = currentIndex > 0,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.inkBold,
                                disabledContentColor = colors.muted.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, if (currentIndex > 0) colors.border else colors.border.copy(alpha = 0.4f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(0.85f)
                                .heightIn(min = 40.dp)
                        ) {
                            Text("← Prev", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = onMarkReviewNext,
                            border = BorderStroke(1.2.dp, reviewPurple),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = reviewPurple),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1.05f)
                                .heightIn(min = 40.dp)
                        ) {
                            Text("Review & Next", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = onClearResponse,
                            border = BorderStroke(1.dp, clearOrange),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = clearOrange),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(0.7f)
                                .heightIn(min = 40.dp)
                        ) {
                            Text("Clear", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = onSaveNext,
                            enabled = !isSavingNext,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1.15f)
                                .heightIn(min = 40.dp)
                        ) {
                            if (isSavingNext) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Saving...", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            } else {
                                Text(
                                    text = if (currentIndex == activeQuestions.size - 1) "Save & Submit" else "Save & Next",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = { showSubmitConfirmDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = submitBtnBg,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                        ) {
                            Text(
                                "Submit ($answeredCount/$totalQuestions)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Mark for Review & Next (Purple)
                            OutlinedButton(
                                onClick = onMarkReviewNext,
                                border = BorderStroke(1.2.dp, reviewPurple),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = reviewPurple),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                            ) {
                                Text(
                                    "Review & Next",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    softWrap = true,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Clear Response
                            OutlinedButton(
                                onClick = onClearResponse,
                                border = BorderStroke(1.dp, clearOrange),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = clearOrange),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .heightIn(min = 44.dp)
                            ) {
                                Text(
                                    "Clear",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    softWrap = true,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Save & Next (With simulated TCS server latency of 400ms)
                            Button(
                                onClick = onSaveNext,
                                enabled = !isSavingNext,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .heightIn(min = 44.dp)
                            ) {
                                if (isSavingNext) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Saving...",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = if (currentIndex == activeQuestions.size - 1) "Save & Submit" else "Save & Next",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        softWrap = true,
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onPrevious,
                                enabled = currentIndex > 0,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colors.inkBold,
                                    disabledContentColor = colors.muted.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.dp, if (currentIndex > 0) colors.border else colors.border.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.heightIn(min = 42.dp)
                            ) {
                                Text(
                                    "← Previous",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    softWrap = true
                                )
                            }

                            Button(
                                onClick = { showSubmitConfirmDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = submitBtnBg,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.heightIn(min = 42.dp)
                            ) {
                                Text(
                                    "Submit Test ($answeredCount/$totalQuestions)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        // =====================================================================
        // MODE B: Scrollable Paper Mode (Continuous multi-card feed with instant option tap)
        // =====================================================================
        else {
            // Horizontal Quick Jump Strip for Scrollable Paper
            Surface(
                color = colors.surface,
                border = BorderStroke(0.6.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = if (isWideLandscape) 4.dp else 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(activeQuestions, key = { _, q -> "scroll_strip_${q.num}" }) { idx, q ->
                        val isAnswered = answers[q.num]?.isNotBlank() == true
                        val isMarked = markedForReview.contains(q.num)
                        val bg = when {
                            isMarked -> Color(0xFF7C3AED)
                            isAnswered -> Color(0xFF22C55E)
                            else -> unvisitedBg
                        }
                        val textCol = if (isAnswered || isMarked) Color.White else unvisitedText

                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    scrollListState.animateScrollToItem(idx)
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = bg,
                            modifier = Modifier.size(if (isWideLandscape) 28.dp else 32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "${q.num}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textCol
                                )
                            }
                        }
                    }
                }
            }

            // Continuous Questions Feed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    state = scrollListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 860.dp)
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    itemsIndexed(activeQuestions, key = { _, q -> "scroll_q_${q.num}" }) { _, q ->
                        val isMarked = markedForReview.contains(q.num)
                        val selectedAns = answers[q.num] ?: ""
                        val isCardMsq = remember(q) { AnswerKeyMatcher.isMultiSelect(q) }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(1.2.dp, if (isMarked) reviewPurple else colors.border),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Header: Number + MSQ highlight (only when MSQ) + Marks + Review Bookmark Icon
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (selectedAns.isNotBlank()) colors.badgeSuccessBg else colors.surfaceVariant,
                                            border = BorderStroke(1.dp, if (selectedAns.isNotBlank()) colors.green else colors.border)
                                        ) {
                                            Text(
                                                text = "Q ${q.num}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedAns.isNotBlank()) colors.badgeSuccessText else colors.inkBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        if (isCardMsq) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = colors.amberLight,
                                                border = BorderStroke(0.9.dp, colors.amber.copy(alpha = 0.7f)),
                                                modifier = Modifier.testTag("scroll_msq_badge_${q.num}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckBox,
                                                        contentDescription = null,
                                                        tint = colors.amber,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "MSQ • Select all that apply",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.amber,
                                                        softWrap = true
                                                    )
                                                }
                                            }
                                        }
                                        val scrollPosStr = if (positiveMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "+%.0f", positiveMarks) else String.format(java.util.Locale.US, "+%.2f", positiveMarks)
                                        val scrollNegStr = if (negativeMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", negativeMarks) else String.format(java.util.Locale.US, "-%.2f", negativeMarks)
                                        Text(
                                            text = "$scrollPosStr / $scrollNegStr",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.muted
                                        )
                                        if (isCardMsq && selectedAns.isNotBlank()) {
                                            Surface(
                                                onClick = { onSelectAnswer(q.num, "") },
                                                shape = RoundedCornerShape(6.dp),
                                                color = colors.surfaceVariant,
                                                border = BorderStroke(0.8.dp, colors.border)
                                            ) {
                                                Text(
                                                    text = "Clear",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.ink,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            if (markedForReview.contains(q.num)) markedForReview.remove(q.num)
                                            else markedForReview.add(q.num)
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = "Bookmark",
                                            tint = if (isMarked) reviewPurple else colors.muted.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Question Stem
                                LatexText(
                                    text = q.question,
                                    fontSize = qFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    lineHeight = qLineHeight
                                )

                                if (!q.formula.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.surfaceVariant,
                                        border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = q.formula ?: "",
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 12.5.sp,
                                            color = colors.teal,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Options List (Instant Tap Response; checkbox toggle for MSQ, 1-tap radio for MCQ)
                                val options = listOfNotNull(
                                    "A" to q.options.a,
                                    "B" to q.options.b,
                                    "C" to q.options.c,
                                    "D" to q.options.d,
                                    if (q.options.e.isNotBlank()) "E" to q.options.e else null
                                )

                                options.forEach { (key, optText) ->
                                    if (optText.isNotBlank()) {
                                        val isChosen = if (isCardMsq) {
                                            AnswerKeyMatcher.containsOption(selectedAns, key)
                                        } else {
                                            selectedAns.equals(key, ignoreCase = true)
                                        }
                                        val indicatorShape = if (isCardMsq) RoundedCornerShape(5.dp) else CircleShape
                                        Surface(
                                            onClick = {
                                                if (isCardMsq) {
                                                    onSelectAnswer(q.num, AnswerKeyMatcher.toggleSelection(selectedAns, key))
                                                } else {
                                                    onSelectAnswer(q.num, if (isChosen) "" else key)
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isChosen) selectedOptionBg else colors.surfaceVariant.copy(alpha = 0.6f),
                                            border = BorderStroke(
                                                width = if (isChosen) 1.6.dp else 0.8.dp,
                                                color = if (isChosen) selectedOptionBorder else colors.border
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .padding(vertical = 3.dp)
                                                .testTag("scroll_option_${key}_q${q.num}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = indicatorShape,
                                                    color = if (isChosen) Color(0xFF2563EB) else colors.surface,
                                                    border = BorderStroke(1.2.dp, if (isChosen) Color(0xFF2563EB) else colors.border),
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                        if (isCardMsq && isChosen) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Checked $key",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        } else {
                                                            Text(
                                                                text = key,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isChosen) Color.White else colors.inkBold
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                LatexText(
                                                    text = optText,
                                                    fontSize = optFontSize,
                                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isChosen) selectedOptionText else colors.inkBold,
                                                    lineHeight = optLineHeight,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(14.dp)) }
                }
            }

            // Bottom Sticky Bar for Scrollable Paper Mode
            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                if (isWideLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Answered: $answeredCount/$totalQuestions • Marked: ${markedOnlyCount + ansMarkedCount}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.green,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        OutlinedButton(
                            onClick = {
                                interfaceMode = ExamInterfaceMode.TCS_CBT
                                currentIndex = scrollListState.firstVisibleItemIndex.coerceIn(0, totalQuestions - 1)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                            border = BorderStroke(1.dp, colors.border),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 40.dp)
                        ) {
                            Text("TCS CBT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showSubmitConfirmDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = submitBtnBg,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 40.dp)
                        ) {
                            Text("Submit Test", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Answered: $answeredCount of $totalQuestions",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.green
                            )
                            Text(
                                text = "Marked: ${markedOnlyCount + ansMarkedCount}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = reviewPurple
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    interfaceMode = ExamInterfaceMode.TCS_CBT
                                    currentIndex = scrollListState.firstVisibleItemIndex.coerceIn(0, totalQuestions - 1)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Switch to TCS CBT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showSubmitConfirmDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = submitBtnBg,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Submit Test", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // CBT Exam Question Palette Side Sheet (TCS iON Standard with Candidate Identity Card)
    ExamPaletteSideSheet(
        isOpen = showCbtPaletteSideSheet,
        onDismiss = { showCbtPaletteSideSheet = false },
        questions = activeQuestions,
        currentQuestionNum = currentQuestion.num,
        paperTitle = paper.metadata.title,
        setNumber = paper.metadata.setNumber,
        isCbtMode = true,
        candidateName = paper.metadata.rollNumber.ifBlank { "Candidate" },
        rollNumber = paper.metadata.rollNumber.ifBlank { "0000000001" },
        systemNodeId = "LAB-02 • NODE-94",
        answers = answers,
        markedForReview = markedForReview.toSet(),
        visitedQuestions = visitedQuestions.toSet(),
        onQuestionClick = { index, _ ->
            showCbtPaletteSideSheet = false
            currentIndex = index
            val qNum = activeQuestions[index].num
            if (!visitedQuestions.contains(qNum)) {
                visitedQuestions.add(qNum)
            }
            if (interfaceMode == ExamInterfaceMode.SCROLLABLE_PAPER) {
                coroutineScope.launch { scrollListState.animateScrollToItem(index) }
            }
        }
    )

    // Non-blocking 3-2-1 Readiness Countdown Overlay (instant Start Now bypass)
    if (readinessCountdown > 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xDC0F172A))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.2.dp, Color(0xFF14B8A6).copy(alpha = 0.6f)),
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(0.86f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = 24.dp,
                            vertical = if (isWideLandscape) 16.dp else 28.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = paper.metadata.title.ifBlank { "Practice Examination" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${activeQuestions.size} Questions Ready",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(if (isWideLandscape) 14.dp else 22.dp))
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF0D9488).copy(alpha = 0.2f),
                        border = BorderStroke(3.dp, Color(0xFF14B8A6)),
                        modifier = Modifier.size(if (isWideLandscape) 72.dp else 92.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$readinessCountdown",
                                fontSize = if (isWideLandscape) 32.sp else 40.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF5EEAD4)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(if (isWideLandscape) 10.dp else 14.dp))
                    Text(
                        text = "Starting in ${readinessCountdown}s...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(if (isWideLandscape) 14.dp else 22.dp))
                    Button(
                        onClick = { readinessCountdown = 0 },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0D9488),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("countdown_start_now_button")
                    ) {
                        Text(
                            text = "Start Now →",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun TcsSummaryRow(
    label: String,
    value: String,
    color: Color
) {
    val colors = ExamTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.5.sp, color = colors.ink, fontWeight = FontWeight.Medium)
        Text(text = value, fontSize = 13.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

/**
 * Screen 3: Dedicated Post-Submission Result & Detailed Solution Review.
 * Uses its own scroll state so results ALWAYS start at the very top (index 0).
 * Provides interactive filtering for [All], [Incorrect], [Unanswered], [Correct],
 * and targeted retry actions that isolate only those questions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamResultAndReviewView(
    paper: QuestionPaper,
    answers: Map<Int, String>,
    submittedStats: com.example.ui.MainViewModel.Companion.TestStats?,
    submittedAnswers: Map<Int, String>,
    incorrectQuestionLog: Set<Int>,
    unattendedQuestionLog: Set<Int>,
    positiveMarks: Double = 4.0,
    negativeMarks: Double = 1.0,
    unattendedMarks: Double = 0.0,
    timeSpentSeconds: Long,
    todayTestSeconds: Long = 0L,
    onRetakeFull: () -> Unit,
    onRetryQuestions: (List<Int>?) -> Unit,
    onExitToOverview: () -> Unit,
    onSelectAnswer: (Int, String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAnalytics: () -> Unit = {},
    onOpenPsychometrics: () -> Unit = {},
    onSaveWeakAreasToKnowledgeSet: ((QuestionPaper, Set<Int>, Set<Int>, Map<Int, String>) -> Unit)? = null,
    timeSpentSecondsFlow: StateFlow<Long>? = null,
    todayTestSecondsFlow: StateFlow<Long>? = null,
    questionTimes: Map<Int, Long> = emptyMap(),
    questionTimesFlow: StateFlow<Map<Int, Long>>? = null,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val coroutineScope = rememberCoroutineScope()
    val solutionsListState = rememberLazyListState()
    var activeTab by remember { mutableStateOf(ResultHubTab.SCORECARD) }
    var filter by remember { mutableStateOf(ReviewFilter.ALL) }
    var solutionsMode by remember { mutableStateOf(ReviewSolutionsMode.LIST) }
    var focusedQuestionIdx by remember { mutableIntStateOf(0) }
    var showPaletteDialog by remember { mutableStateOf(false) }
    var isSavedToKnowledge by remember { mutableStateOf(false) }
    val effectiveTimeSpentSeconds = timeSpentSecondsFlow?.collectAsState()?.value ?: timeSpentSeconds
    val effectiveQuestionTimes = questionTimesFlow?.collectAsState()?.value ?: questionTimes

    // Official one-time evaluation (MSQ-aware)
    val officialAnswers = if (submittedAnswers.isNotEmpty()) submittedAnswers else answers
    val effectiveMsqPolicy = submittedStats?.msqScoringPolicy ?: paper.metadata.msqScoringPolicy
    val stats = submittedStats ?: com.example.ui.MainViewModel.calculateTestStats(
        paper = paper,
        answers = officialAnswers,
        positiveMarks = positiveMarks,
        negativeMarks = negativeMarks,
        unattendedMarks = unattendedMarks,
        msqScoringPolicy = effectiveMsqPolicy
    )
    val total = stats.total
    val correctCount = stats.correct
    val partialCount = stats.partial
    val wrongCount = stats.incorrect
    val unattempted = stats.unanswered
    val percentage = stats.percentage
    val paperHasMsq = remember(paper.questions) { paper.questions.any { AnswerKeyMatcher.isMultiSelect(it) } }

    val persistentPartial = remember(paper.questions, officialAnswers, effectiveMsqPolicy) {
        paper.questions.filter { q ->
            AnswerKeyMatcher.scoreQuestion(
                question = q,
                userAnswer = officialAnswers[q.num],
                positiveMarks = positiveMarks,
                negativeMarks = negativeMarks,
                unattendedMarks = unattendedMarks,
                msqPolicy = effectiveMsqPolicy
            ).status == AnswerKeyMatcher.QuestionGradeStatus.PARTIAL
        }.map { it.num }.toSet()
    }

    // Pure incorrect questions (excluding partial MSQs so Correct/Partial/Incorrect/Unattended partition cleanly)
    val persistentIncorrect = remember(incorrectQuestionLog, paper.questions, officialAnswers, effectiveMsqPolicy, persistentPartial) {
        val baseIncorrect = if (incorrectQuestionLog.isNotEmpty()) {
            incorrectQuestionLog
        } else {
            paper.questions.filter { q ->
                AnswerKeyMatcher.scoreQuestion(
                    question = q,
                    userAnswer = officialAnswers[q.num],
                    positiveMarks = positiveMarks,
                    negativeMarks = negativeMarks,
                    unattendedMarks = unattendedMarks,
                    msqPolicy = effectiveMsqPolicy
                ).status == AnswerKeyMatcher.QuestionGradeStatus.INCORRECT
            }.map { it.num }.toSet()
        }
        baseIncorrect - persistentPartial
    }

    val persistentUnattended = remember(unattendedQuestionLog, paper.questions, officialAnswers) {
        if (unattendedQuestionLog.isNotEmpty()) {
            unattendedQuestionLog
        } else {
            paper.questions.filter { q ->
                !q.isNeutralized && officialAnswers[q.num].isNullOrBlank()
            }.map { it.num }.toSet()
        }
    }

    val persistentRetryErrors = remember(persistentIncorrect, persistentPartial) {
        persistentIncorrect + persistentPartial
    }

    val filteredQuestions = remember(filter, paper.questions, persistentIncorrect, persistentPartial, persistentUnattended) {
        when (filter) {
            ReviewFilter.ALL -> paper.questions
            ReviewFilter.CORRECT -> paper.questions.filter {
                it.isNeutralized || (it.num !in persistentIncorrect && it.num !in persistentPartial && it.num !in persistentUnattended)
            }
            ReviewFilter.PARTIAL -> paper.questions.filter { it.num in persistentPartial }
            ReviewFilter.INCORRECT -> paper.questions.filter { it.num in persistentIncorrect }
            ReviewFilter.UNANSWERED -> paper.questions.filter { it.num in persistentUnattended }
        }
    }

    LaunchedEffect(filter, filteredQuestions.size) {
        focusedQuestionIdx = 0
        if (filteredQuestions.isNotEmpty()) {
            solutionsListState.scrollToItem(0)
        }
    }

    val maxMarksVal = total * positiveMarks
    val formattedMaxMarks = String.format(java.util.Locale.US, "%.2f", maxMarksVal)
    val setDisplay = remember(paper.metadata.setNumber) {
        ExamNumberGenerator.formatSetDisplayLabel(paper.metadata.setNumber)
    }

    val pacingSummary = remember(paper, officialAnswers, effectiveQuestionTimes, effectiveMsqPolicy) {
        QuestionPacingAnalyzer.computeSummary(paper, officialAnswers, effectiveQuestionTimes, effectiveMsqPolicy)
    }

    val h = effectiveTimeSpentSeconds / 3600
    val m = (effectiveTimeSpentSeconds % 3600) / 60
    val s = effectiveTimeSpentSeconds % 60
    val durationFormatted = if (h > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    if (showPaletteDialog) {
        ReviewQuestionPaletteDialog(
            questions = filteredQuestions,
            persistentIncorrect = persistentIncorrect,
            persistentPartial = persistentPartial,
            persistentUnattended = persistentUnattended,
            currentFocusedIdx = focusedQuestionIdx.coerceIn(0, (filteredQuestions.size - 1).coerceAtLeast(0)),
            onSelectIndex = { idx ->
                showPaletteDialog = false
                focusedQuestionIdx = idx
                activeTab = ResultHubTab.SOLUTIONS
                if (solutionsMode == ReviewSolutionsMode.LIST) {
                    coroutineScope.launch {
                        solutionsListState.animateScrollToItem(idx)
                    }
                }
            },
            onDismiss = { showPaletteDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Compact Pinned Post-Submission Navigation Hub Header (Font-Scale Safe)
        Surface(
            color = colors.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 860.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (percentage >= 50) colors.greenLight else colors.redLight,
                            border = BorderStroke(
                                0.8.dp,
                                (if (percentage >= 50) colors.green else colors.red).copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = "${stats.formattedScore} / $formattedMaxMarks",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (percentage >= 50) colors.green else colors.red,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.tealLight,
                            border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = setDisplay,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedButton(
                        onClick = onExitToOverview,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("result_exit_overview_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Exam Overview",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Overview",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                TabRow(
                    selectedTabIndex = activeTab.ordinal,
                    containerColor = colors.surface,
                    contentColor = colors.teal,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab.ordinal]),
                            height = 2.5.dp,
                            color = colors.teal
                        )
                    },
                    divider = { HorizontalDivider(color = colors.border) }
                ) {
                    ResultHubTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        val tabIcon = when (tab) {
                            ResultHubTab.SCORECARD -> Icons.Default.Assessment
                            ResultHubTab.SOLUTIONS -> Icons.AutoMirrored.Filled.MenuBook
                            ResultHubTab.DIAGNOSTICS -> Icons.Default.Insights
                        }
                        val tabLabel = when (tab) {
                            ResultHubTab.SCORECARD -> "Scorecard"
                            ResultHubTab.SOLUTIONS -> "Solutions"
                            ResultHubTab.DIAGNOSTICS -> "Diagnostics"
                        }
                        Tab(
                            selected = selected,
                            onClick = { activeTab = tab },
                            selectedContentColor = colors.teal,
                            unselectedContentColor = colors.muted,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("result_tab_${tab.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = tabIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = tabLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tab Body
        when (activeTab) {
            ResultHubTab.SCORECARD -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 860.dp)
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        // Performance Hero Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(
                                    1.2.dp,
                                    if (percentage >= 50) colors.green.copy(alpha = 0.45f)
                                    else colors.red.copy(alpha = 0.45f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val (statusText, statusBg, statusColor) = when {
                                        percentage >= 80 -> Triple("Outstanding Performance • Mastery Achieved", colors.greenLight, colors.green)
                                        percentage >= 50 -> Triple("Good Effort • Review Missed Questions Below", colors.tealLight, colors.teal)
                                        else -> Triple("Needs Focused Practice • Inspect Solutions", colors.redLight, colors.red)
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = statusBg,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = statusText,
                                            color = statusColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Candidate Identification Badges in FlowRow (never wraps awkwardly or truncates)
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val rollBg = if (colors.isDark) Color(0xFF172554) else Color(0xFFEFF6FF)
                                        val rollFg = if (colors.isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
                                        val bookletBg = if (colors.isDark) Color(0xFF500724) else Color(0xFFFDF2F8)
                                        val bookletFg = if (colors.isDark) Color(0xFFF472B6) else Color(0xFFBE185D)

                                        if (paper.metadata.rollNumber.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = rollBg,
                                                border = BorderStroke(0.6.dp, rollFg.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "Roll: ${paper.metadata.rollNumber}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = rollFg,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        if (paper.metadata.bookletNumber.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = bookletBg,
                                                border = BorderStroke(0.6.dp, bookletFg.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "Booklet: ${paper.metadata.bookletNumber}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = bookletFg,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = colors.tealLight,
                                            border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = setDisplay,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.teal,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "OFFICIAL SCORE",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = colors.muted
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${stats.formattedScore} / $formattedMaxMarks ($percentage%)",
                                        fontSize = 21.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (percentage >= 50) colors.green else colors.red,
                                        textAlign = TextAlign.Center
                                    )

                                    // Visual Outcome Breakdown Proportion Bar
                                    if (total > 0) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.surfaceVariant)
                                        ) {
                                            if (correctCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(correctCount.toFloat())
                                                        .fillMaxHeight()
                                                        .background(colors.green)
                                                )
                                            }
                                            if (partialCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(partialCount.toFloat())
                                                        .fillMaxHeight()
                                                        .background(colors.amber)
                                                )
                                            }
                                            if (wrongCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(wrongCount.toFloat())
                                                        .fillMaxHeight()
                                                        .background(colors.red)
                                                )
                                            }
                                            if (unattempted > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(unattempted.toFloat())
                                                        .fillMaxHeight()
                                                        .background(colors.muted.copy(alpha = 0.45f))
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    val formattedPos = if (positiveMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "+%.0f", positiveMarks) else String.format(java.util.Locale.US, "+%.2f", positiveMarks)
                                    val formattedPenalty = if (negativeMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", negativeMarks) else String.format(java.util.Locale.US, "-%.2f", negativeMarks)
                                    val pointsCorrect = correctCount * positiveMarks
                                    val formattedPointsCorrect = if (pointsCorrect % 1.0 == 0.0) String.format(java.util.Locale.US, "+%.0f", pointsCorrect) else String.format(java.util.Locale.US, "+%.2f", pointsCorrect)
                                    val partialPointsTotal = remember(paper.questions, officialAnswers, positiveMarks, negativeMarks, unattendedMarks, effectiveMsqPolicy) {
                                        paper.questions.sumOf { q ->
                                            val r = AnswerKeyMatcher.scoreQuestion(q, officialAnswers[q.num], positiveMarks, negativeMarks, unattendedMarks, effectiveMsqPolicy)
                                            if (r.status == AnswerKeyMatcher.QuestionGradeStatus.PARTIAL) r.marks else 0.0
                                        }
                                    }
                                    val unattendedPenaltyStr = if (stats.penaltyUnattended > 0.0) {
                                        "-${String.format(java.util.Locale.US, "%.2f", stats.penaltyUnattended)}"
                                    } else {
                                        "0.00"
                                    }
                                    val unattendedSubtitle = if (stats.penaltyUnattended > 0.0) {
                                        val rate = if (stats.unanswered > 0) stats.penaltyUnattended / stats.unanswered else unattendedMarks
                                        if (rate % 1.0 == 0.0) String.format(java.util.Locale.US, "$unattempted × -%.0f", rate)
                                        else String.format(java.util.Locale.US, "$unattempted × -%.2f", rate)
                                    } else {
                                        "$unattempted skipped (0.00)"
                                    }

                                    // 2x2 Clickable Stat Grid (Tapping any status jumps straight to Solutions pre-filtered!)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ScoreStatTile(
                                            title = "Correct ($correctCount)",
                                            value = formattedPointsCorrect,
                                            subtitle = "$formattedPos ea • View",
                                            color = colors.green,
                                            onClick = {
                                                filter = ReviewFilter.CORRECT
                                                activeTab = ResultHubTab.SOLUTIONS
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        ScoreStatTile(
                                            title = "Incorrect ($wrongCount)",
                                            value = "-${String.format(java.util.Locale.US, "%.2f", stats.penaltyIncorrect)}",
                                            subtitle = "$formattedPenalty ea • View",
                                            color = colors.red,
                                            onClick = {
                                                filter = ReviewFilter.INCORRECT
                                                activeTab = ResultHubTab.SOLUTIONS
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ScoreStatTile(
                                            title = "Unattended ($unattempted)",
                                            value = unattendedPenaltyStr,
                                            subtitle = "$unattendedSubtitle • View",
                                            color = if (stats.penaltyUnattended > 0.0) colors.red else colors.muted,
                                            onClick = {
                                                filter = ReviewFilter.UNANSWERED
                                                activeTab = ResultHubTab.SOLUTIONS
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (partialCount > 0) {
                                            ScoreStatTile(
                                                title = "Partial MSQ ($partialCount)",
                                                value = "+${String.format(java.util.Locale.US, "%.2f", partialPointsTotal)}",
                                                subtitle = "Proportional • View",
                                                color = colors.amber,
                                                onClick = {
                                                    filter = ReviewFilter.PARTIAL
                                                    activeTab = ResultHubTab.SOLUTIONS
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("partial_score_stat_tile")
                                            )
                                        } else {
                                            ScoreStatTile(
                                                title = "Duration",
                                                value = durationFormatted,
                                                subtitle = if (total > 0) "${effectiveTimeSpentSeconds / total}s avg / Q" else "Exam time",
                                                color = colors.teal,
                                                onClick = onOpenAnalytics,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    if (partialCount > 0) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ScoreStatTile(
                                            title = "Duration",
                                            value = durationFormatted,
                                            subtitle = if (total > 0) "${effectiveTimeSpentSeconds / total}s avg / Q • Tap analytics" else "Exam time",
                                            color = colors.teal,
                                            onClick = onOpenAnalytics,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Primary CTA: Direct 1-Tap Jump to Detailed Solutions
                                    Button(
                                        onClick = {
                                            filter = ReviewFilter.ALL
                                            activeTab = ResultHubTab.SOLUTIONS
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.teal,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("inspect_detailed_solutions_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Inspect Detailed Solutions ($total)",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // One-time scoring policy notice
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = colors.surfaceVariant,
                                        border = BorderStroke(0.8.dp, colors.border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = colors.teal, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Official score is locked at submission. Retake the full test to re-evaluate.",
                                                fontSize = 11.sp,
                                                color = colors.ink,
                                                lineHeight = 14.5.sp
                                            )
                                        }
                                    }

                                    ResultTodayStudyLeaf(
                                        todayTestSecondsFlow = todayTestSecondsFlow,
                                        fallbackSeconds = todayTestSeconds,
                                        onOpenAnalytics = onOpenAnalytics
                                    )
                                }
                            }
                        }

                        // Targeted Practice Actions Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Targeted Practice & Export",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedButton(
                                            onClick = onRetakeFull,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, colors.border),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Retake Full Test", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        }

                                        if (persistentRetryErrors.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = { onRetryQuestions(persistentRetryErrors.toList()) },
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, colors.red.copy(alpha = 0.6f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.red),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                                modifier = Modifier.heightIn(min = 44.dp)
                                            ) {
                                                Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Retry Errors (${persistentRetryErrors.size})", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, softWrap = true)
                                            }
                                        }

                                        if (persistentUnattended.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = { onRetryQuestions(persistentUnattended.toList()) },
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, colors.muted.copy(alpha = 0.6f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                                modifier = Modifier.heightIn(min = 44.dp)
                                            ) {
                                                Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Retry Skipped (${persistentUnattended.size})", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, softWrap = true)
                                            }
                                        }

                                        if (onSaveWeakAreasToKnowledgeSet != null && (persistentRetryErrors.isNotEmpty() || persistentUnattended.isNotEmpty())) {
                                            OutlinedButton(
                                                onClick = {
                                                    if (!isSavedToKnowledge) {
                                                        isSavedToKnowledge = true
                                                        onSaveWeakAreasToKnowledgeSet(
                                                            paper,
                                                            persistentRetryErrors,
                                                            persistentUnattended,
                                                            officialAnswers
                                                        )
                                                    }
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, if (isSavedToKnowledge) colors.green.copy(alpha = 0.8f) else colors.teal.copy(alpha = 0.7f)),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = if (isSavedToKnowledge) colors.green else (if (colors.isDark) colors.teal else colors.tealDark)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 44.dp)
                                                    .testTag("save_weak_areas_knowledge_button")
                                            ) {
                                                Icon(
                                                    if (isSavedToKnowledge) Icons.Default.Check else Icons.AutoMirrored.Filled.MenuBook,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    if (isSavedToKnowledge) "Saved to Knowledge Studio ✓" else "Save Missed to Knowledge Set",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    softWrap = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2x2 Post-Test Navigation Grid (Zero clipped labels!)
                        item {
                            val cttTint = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Reports & Workspace Navigation",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    NavGridActionCard(
                                        title = "Item CTT",
                                        subtitle = "Psychometrics",
                                        icon = Icons.Default.Assessment,
                                        tint = cttTint,
                                        onClick = onOpenPsychometrics,
                                        modifier = Modifier.weight(1f)
                                    )
                                    NavGridActionCard(
                                        title = "Analytics",
                                        subtitle = "Weekly Trends",
                                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                                        tint = colors.teal,
                                        onClick = onOpenAnalytics,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    NavGridActionCard(
                                        title = "History",
                                        subtitle = "Past Attempts",
                                        icon = Icons.Default.History,
                                        tint = colors.amber,
                                        onClick = onOpenHistory,
                                        modifier = Modifier.weight(1f)
                                    )
                                    NavGridActionCard(
                                        title = "Exam Overview",
                                        subtitle = "Paper Config",
                                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                                        tint = colors.inkBold,
                                        onClick = onExitToOverview,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }

            ResultHubTab.SOLUTIONS -> {
                val correctReviewCount = (total - persistentIncorrect.size - persistentPartial.size - persistentUnattended.size).coerceAtLeast(0)
                var showFilterMenu by remember { mutableStateOf(false) }

                val (activeFilterLabel, activeFilterColor) = when (filter) {
                    ReviewFilter.ALL -> "All ($total)" to colors.inkBold
                    ReviewFilter.PARTIAL -> "Partial (${persistentPartial.size})" to colors.amber
                    ReviewFilter.INCORRECT -> "Incorrect (${persistentIncorrect.size})" to colors.red
                    ReviewFilter.UNANSWERED -> "Unattended (${persistentUnattended.size})" to colors.muted
                    ReviewFilter.CORRECT -> "Correct ($correctReviewCount)" to colors.green
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 860.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Single Consolidated Horizontally-Scrollable Toolbar (~48dp height instead of 3 stacked rows!)
                    Surface(
                        color = colors.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Filter Selector Dropdown Pill
                            Box {
                                Surface(
                                    onClick = { showFilterMenu = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = activeFilterColor.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, activeFilterColor.copy(alpha = 0.55f)),
                                    modifier = Modifier.heightIn(min = 38.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = "Filter Solutions",
                                            tint = activeFilterColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = activeFilterLabel,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = activeFilterColor,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = activeFilterColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false }
                                ) {
                                    val filterOptions = buildList {
                                        add(Triple(ReviewFilter.ALL, "All ($total)", colors.inkBold))
                                        if (persistentPartial.isNotEmpty() || paperHasMsq) {
                                            add(Triple(ReviewFilter.PARTIAL, "Partial (${persistentPartial.size})", colors.amber))
                                        }
                                        add(Triple(ReviewFilter.INCORRECT, "Incorrect (${persistentIncorrect.size})", colors.red))
                                        add(Triple(ReviewFilter.UNANSWERED, "Unattended (${persistentUnattended.size})", colors.muted))
                                        add(Triple(ReviewFilter.CORRECT, "Correct ($correctReviewCount)", colors.green))
                                    }
                                    filterOptions.forEach { (itemFilter, text, accentColor) ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = text,
                                                    fontWeight = if (filter == itemFilter) FontWeight.Bold else FontWeight.Medium,
                                                    color = accentColor
                                                )
                                            },
                                            leadingIcon = {
                                                if (filter == itemFilter) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                                }
                                            },
                                            onClick = {
                                                filter = itemFilter
                                                showFilterMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // List / Single Q Switcher
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.surfaceVariant,
                                border = BorderStroke(0.8.dp, colors.border),
                                modifier = Modifier.heightIn(min = 38.dp)
                            ) {
                                Row(modifier = Modifier.padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val isList = solutionsMode == ReviewSolutionsMode.LIST
                                    Surface(
                                        onClick = { solutionsMode = ReviewSolutionsMode.LIST },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isList) colors.surface else Color.Transparent
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ViewAgenda,
                                                contentDescription = null,
                                                tint = if (isList) colors.teal else colors.muted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "List",
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isList) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isList) colors.teal else colors.muted,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    val isSingle = solutionsMode == ReviewSolutionsMode.SINGLE_QUESTION
                                    Surface(
                                        onClick = { solutionsMode = ReviewSolutionsMode.SINGLE_QUESTION },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSingle) colors.surface else Color.Transparent
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ViewCarousel,
                                                contentDescription = null,
                                                tint = if (isSingle) colors.teal else colors.muted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Single Q",
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSingle) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSingle) colors.teal else colors.muted,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            // Question Grid Palette Button
                            if (filteredQuestions.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showPaletteDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.55f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 38.dp)
                                        .testTag("solutions_question_grid_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Open Question Palette Grid",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Grid (${filteredQuestions.size})",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = colors.border)

                    if (filteredQuestions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = when (filter) {
                                            ReviewFilter.PARTIAL -> "No partial-credit MSQ answers recorded in this test."
                                            ReviewFilter.INCORRECT -> "Outstanding! No incorrect answers in this test."
                                            ReviewFilter.UNANSWERED -> "Great! All questions were attended in this test."
                                            ReviewFilter.CORRECT -> "No correct answers recorded yet."
                                            else -> "No questions found."
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.muted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else if (solutionsMode == ReviewSolutionsMode.LIST) {
                        LazyColumn(
                            state = solutionsListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
                        ) {
                            itemsIndexed(filteredQuestions, key = { _, q -> "result_q_${q.num}" }) { _, q ->
                                ReviewQuestionCardBlock(
                                    question = q,
                                    officialAnswer = officialAnswers[q.num],
                                    currentPracticeAnswer = answers[q.num],
                                    isOriginallyIncorrect = q.num in persistentIncorrect,
                                    isOriginallyPartial = q.num in persistentPartial,
                                    isOriginallyUnattended = q.num in persistentUnattended,
                                    positiveMarks = stats.positiveMarks,
                                    negativeMarks = stats.negativeMarks,
                                    unattendedMarks = stats.unattendedMarks,
                                    msqScoringPolicy = effectiveMsqPolicy
                                )
                            }
                        }
                    } else {
                        // Single Question Focused Review Mode with Compact Pinned Prev/Next Dock
                        val safeIdx = focusedQuestionIdx.coerceIn(0, filteredQuestions.lastIndex)
                        val activeQuestion = filteredQuestions[safeIdx]

                        Column(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                ReviewQuestionCardBlock(
                                    question = activeQuestion,
                                    officialAnswer = officialAnswers[activeQuestion.num],
                                    currentPracticeAnswer = answers[activeQuestion.num],
                                    isOriginallyIncorrect = activeQuestion.num in persistentIncorrect,
                                    isOriginallyPartial = activeQuestion.num in persistentPartial,
                                    isOriginallyUnattended = activeQuestion.num in persistentUnattended,
                                    positiveMarks = stats.positiveMarks,
                                    negativeMarks = stats.negativeMarks,
                                    unattendedMarks = stats.unattendedMarks,
                                    msqScoringPolicy = effectiveMsqPolicy
                                )
                            }

                            HorizontalDivider(color = colors.border)
                            Surface(
                                color = colors.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { if (safeIdx > 0) focusedQuestionIdx = safeIdx - 1 },
                                        enabled = safeIdx > 0,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Previous Question",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Prev", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }

                                    Surface(
                                        onClick = { showPaletteDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.surfaceVariant,
                                        border = BorderStroke(0.8.dp, colors.border),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.GridView,
                                                contentDescription = null,
                                                tint = colors.teal,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Q${activeQuestion.num} (${safeIdx + 1}/${filteredQuestions.size})",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.inkBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { if (safeIdx < filteredQuestions.lastIndex) focusedQuestionIdx = safeIdx + 1 },
                                        enabled = safeIdx < filteredQuestions.lastIndex,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.teal,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Next", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Next Question",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ResultHubTab.DIAGNOSTICS -> {
                val attemptedCount = correctCount + wrongCount
                val accuracyOnAttempted = if (attemptedCount > 0) (correctCount * 100) / attemptedCount else 0
                val attemptRate = if (total > 0) (attemptedCount * 100) / total else 0
                val avgPaceSec = if (total > 0) effectiveTimeSpentSeconds / total else 0L

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 860.dp)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(6.dp)) }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Exam Telemetry & Marking Formula",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MetricCard(
                                            label = "Accuracy",
                                            value = "$accuracyOnAttempted%",
                                            icon = Icons.Default.CheckCircle,
                                            tint = colors.green,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MetricCard(
                                            label = "Attempt Rate",
                                            value = "$attemptRate%",
                                            icon = Icons.Default.Assessment,
                                            tint = colors.teal,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MetricCard(
                                            label = "Avg Pace",
                                            value = "${avgPaceSec}s/Q",
                                            icon = Icons.Default.Timer,
                                            tint = colors.amber,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = colors.borderLight)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    TcsSummaryRow(
                                        label = "Correct ($correctCount × +${String.format(java.util.Locale.US, "%.2f", positiveMarks)})",
                                        value = "+${String.format(java.util.Locale.US, "%.2f", correctCount * positiveMarks)}",
                                        color = colors.green
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TcsSummaryRow(
                                        label = "Incorrect ($wrongCount × -${String.format(java.util.Locale.US, "%.2f", negativeMarks)})",
                                        value = "-${String.format(java.util.Locale.US, "%.2f", stats.penaltyIncorrect)}",
                                        color = colors.red
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TcsSummaryRow(
                                        label = "Unattended ($unattempted × -${String.format(java.util.Locale.US, "%.2f", unattendedMarks)})",
                                        value = if (stats.penaltyUnattended > 0.0) "-${String.format(java.util.Locale.US, "%.2f", stats.penaltyUnattended)}" else "0.00",
                                        color = if (stats.penaltyUnattended > 0.0) colors.red else colors.muted
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    HorizontalDivider(color = colors.border)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    TcsSummaryRow(
                                        label = "Net Official Score",
                                        value = "${stats.formattedScore} / $formattedMaxMarks",
                                        color = if (percentage >= 50) colors.green else colors.red
                                    )
                                }
                            }
                        }

                        if (pacingSummary.hasPacingData) {
                            item {
                                PerQuestionPacingCard(pacingSummary = pacingSummary)
                            }
                        }

                        item {
                            val cttTint = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Deep Diagnostics & Study Tools",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                                DiagnosticActionRow(
                                    title = "Psychometric & Item CTT Analysis",
                                    subtitle = "Inspect p-value difficulty, discrimination index & Cronbach alpha",
                                    icon = Icons.Default.Assessment,
                                    tint = cttTint,
                                    onClick = onOpenPsychometrics
                                )
                                DiagnosticActionRow(
                                    title = "Weekly Study Activity & Trends",
                                    subtitle = "Swipe weekly progress bars, daily goals & time logs",
                                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                                    tint = colors.teal,
                                    onClick = onOpenAnalytics
                                )
                                DiagnosticActionRow(
                                    title = "Saved Attempt History",
                                    subtitle = "Compare scores and durations across previous test runs",
                                    icon = Icons.Default.History,
                                    tint = colors.amber,
                                    onClick = onOpenHistory
                                )
                                if (onSaveWeakAreasToKnowledgeSet != null && (persistentRetryErrors.isNotEmpty() || persistentUnattended.isNotEmpty())) {
                                    DiagnosticActionRow(
                                        title = if (isSavedToKnowledge) "Missed Questions Saved ✓" else "Save Missed to Knowledge Studio",
                                        subtitle = if (isSavedToKnowledge) "Active Recall cards saved and uniquely identified in Knowledge Studio" else "Convert ${persistentRetryErrors.size + persistentUnattended.size} missed items into Active Recall cards",
                                        icon = if (isSavedToKnowledge) Icons.Default.Check else Icons.AutoMirrored.Filled.MenuBook,
                                        tint = colors.green,
                                        onClick = {
                                            isSavedToKnowledge = true
                                            onSaveWeakAreasToKnowledgeSet(
                                                paper,
                                                persistentRetryErrors,
                                                persistentUnattended,
                                                officialAnswers
                                            )
                                        }
                                    )
                                }
                                DiagnosticActionRow(
                                    title = "Return to Exam Overview",
                                    subtitle = "Configure marking scheme, timer or test mode",
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    tint = colors.inkBold,
                                    onClick = onExitToOverview
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(28.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreStatTile(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 72.dp),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(0.9.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NavGridActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 66.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = tint.copy(alpha = 0.14f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DiagnosticActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    val colors = ExamTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = tint.copy(alpha = 0.14f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = colors.muted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open",
                tint = colors.muted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ReviewQuestionCardBlock(
    question: McqQuestion,
    officialAnswer: String?,
    currentPracticeAnswer: String?,
    isOriginallyIncorrect: Boolean,
    isOriginallyPartial: Boolean = false,
    isOriginallyUnattended: Boolean,
    positiveMarks: Double,
    negativeMarks: Double,
    unattendedMarks: Double,
    msqScoringPolicy: MsqScoringPolicy = MsqScoringPolicy.ALL_OR_NOTHING
) {
    val colors = ExamTheme.colors
    val isCorrectNow = !currentPracticeAnswer.isNullOrBlank() &&
        AnswerKeyMatcher.areKeysEquivalent(currentPracticeAnswer, question.correctAnswer)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (question.isNeutralized) {
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                color = colors.teal.copy(alpha = 0.12f),
                border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.teal, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Question Neutralized: Full marks awarded to all candidates",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.teal
                    )
                }
            }
        } else if (isOriginallyPartial) {
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                color = colors.amberLight,
                border = BorderStroke(0.8.dp, colors.amber.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckBox, contentDescription = null, tint = colors.amber, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Partial MSQ Credit awarded at submission • Inspect missed option(s) below",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.amber
                    )
                }
            }
        } else if (isOriginallyIncorrect) {
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                color = if (isCorrectNow) colors.greenLight else colors.redLight,
                border = BorderStroke(0.8.dp, if (isCorrectNow) colors.green.copy(alpha = 0.5f) else colors.red.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCorrectNow) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isCorrectNow) colors.green else colors.red,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val cardPenaltyStr = if (negativeMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", negativeMarks) else String.format(java.util.Locale.US, "-%.2f", negativeMarks)
                    Text(
                        text = if (isCorrectNow)
                            "Logged for Review: Originally incorrect • Corrected in review"
                        else
                            "Logged for Review: Incorrect in test (Penalty: $cardPenaltyStr pts)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCorrectNow) colors.green else colors.red
                    )
                }
            }
        } else if (isOriginallyUnattended) {
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                color = colors.muted.copy(alpha = 0.12f),
                border = BorderStroke(0.8.dp, colors.muted.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = colors.muted, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    val unkBannerText = if (unattendedMarks > 0.0) {
                        "Logged for Review: Unattended in test (Penalty: -${String.format(java.util.Locale.US, "%.2f", unattendedMarks)} pts)"
                    } else {
                        "Logged for Review: Unattended in test (Penalty: 0.00 pts)"
                    }
                    Text(
                        text = unkBannerText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.muted
                    )
                }
            }
        }

        StudentPracticeCard(
            question = question,
            selectedAnswer = officialAnswer,
            onSelectAnswer = { },
            showResult = true,
            positiveMarks = positiveMarks,
            negativeMarks = negativeMarks,
            unattendedMarks = unattendedMarks,
            msqScoringPolicy = msqScoringPolicy
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewQuestionPaletteDialog(
    questions: List<McqQuestion>,
    persistentIncorrect: Set<Int>,
    persistentPartial: Set<Int> = emptySet(),
    persistentUnattended: Set<Int>,
    currentFocusedIdx: Int,
    onSelectIndex: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jump to Question (${questions.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Grid", tint = colors.muted)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Color Legend (wraps cleanly on large font scale)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaletteLegendChip("Correct", colors.green)
                    if (persistentPartial.isNotEmpty()) {
                        PaletteLegendChip("Partial", colors.amber)
                    }
                    PaletteLegendChip("Wrong", colors.red)
                    PaletteLegendChip("Skipped", colors.muted)
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(questions.indices.toList()) { idx ->
                        val q = questions[idx]
                        val tileColor = when {
                            q.isNeutralized -> colors.teal
                            q.num in persistentPartial -> colors.amber
                            q.num in persistentIncorrect -> colors.red
                            q.num in persistentUnattended -> colors.muted
                            else -> colors.green
                        }
                        val isCurrent = idx == currentFocusedIdx
                        Surface(
                            onClick = { onSelectIndex(idx) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) tileColor else tileColor.copy(alpha = 0.14f),
                            border = BorderStroke(if (isCurrent) 1.5.dp else 0.8.dp, tileColor),
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${q.num}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) Color.White else tileColor
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold, color = colors.teal)
            }
        }
    )
}

@Composable
private fun PaletteLegendChip(label: String, color: Color) {
    val colors = ExamTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = colors.muted
        )
    }
}


@Composable
private fun MetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
            Text(text = label, fontSize = 10.sp, color = colors.muted)
        }
    }
}

@Composable
private fun InstructionBullet(
    number: String,
    title: String,
    description: String
) {
    val colors = ExamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(20.dp),
            shape = CircleShape,
            color = colors.tealLight
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = number, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
            Text(text = description, fontSize = 11.5.sp, color = colors.muted, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun TodayTrackerTimeLeaf(
    todayTestSecondsFlow: StateFlow<Long>?,
    fallbackSeconds: Long,
    dailyGoalMinutes: Int
) {
    val colors = ExamTheme.colors
    val todaySeconds = todayTestSecondsFlow?.collectAsState()?.value ?: fallbackSeconds

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            val hours = todaySeconds / 3600
            val minutes = (todaySeconds % 3600) / 60
            val seconds = todaySeconds % 60
            val timeStr = if (hours > 0) {
                String.format(java.util.Locale.US, "%dh %02dm %02ds", hours, minutes, seconds)
            } else {
                String.format(java.util.Locale.US, "%dm %02ds", minutes, seconds)
            }
            Text(
                text = timeStr,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.teal,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                text = "Total test practice today • Goal: ${dailyGoalMinutes}m",
                fontSize = 11.5.sp,
                color = colors.muted
            )
        }

        val goalSeconds = dailyGoalMinutes * 60L
        val fraction = if (goalSeconds > 0) (todaySeconds.toFloat() / goalSeconds).coerceIn(0f, 1f) else 0f
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${(fraction * 100).toInt()}% Goal",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (fraction >= 1f) colors.green else colors.teal
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .width(80.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (fraction >= 1f) colors.green else colors.teal,
                trackColor = colors.border
            )
        }
    }
}

@Composable
private fun ExamHeaderTimerLeaf(
    timerMode: ExamTimerMode,
    timeSpentSecondsFlow: StateFlow<Long>?,
    timeRemainingSecondsFlow: StateFlow<Long>?,
    fallbackTimeSpentSeconds: Long,
    fallbackTimeRemainingSeconds: Long
) {
    val timeSpent = timeSpentSecondsFlow?.collectAsState()?.value ?: fallbackTimeSpentSeconds
    val timeRemaining = timeRemainingSecondsFlow?.collectAsState()?.value ?: fallbackTimeRemainingSeconds

    val formattedTime = if (timerMode == ExamTimerMode.COUNTDOWN) {
        val h = timeRemaining / 3600
        val m = (timeRemaining % 3600) / 60
        val s = timeRemaining % 60
        if (h > 0) String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%02d:%02d", m, s)
    } else {
        val m = timeSpent / 60
        val s = timeSpent % 60
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    val isTimerWarning = timerMode == ExamTimerMode.COUNTDOWN && timeRemaining <= 300L
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isTimerWarning) Color(0xFF991B1B).copy(alpha = 0.4f) else Color(0xFF0D9488).copy(alpha = 0.25f),
        border = BorderStroke(1.dp, if (isTimerWarning) Color(0xFFEF4444) else Color(0xFF14B8A6))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.HourglassBottom,
                contentDescription = null,
                tint = if (isTimerWarning) Color(0xFFFCA5A5) else Color(0xFF5EEAD4),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = formattedTime,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isTimerWarning) Color(0xFFFCA5A5) else Color(0xFF5EEAD4),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ResultTodayStudyLeaf(
    todayTestSecondsFlow: StateFlow<Long>?,
    fallbackSeconds: Long,
    onOpenAnalytics: () -> Unit
) {
    val colors = ExamTheme.colors
    val todaySeconds = todayTestSecondsFlow?.collectAsState()?.value ?: fallbackSeconds
    if (todaySeconds <= 0L) return

    Spacer(modifier = Modifier.height(8.dp))
    Surface(
        onClick = onOpenAnalytics,
        shape = RoundedCornerShape(8.dp),
        color = colors.tealLight.copy(alpha = 0.55f),
        border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tHours = todaySeconds / 3600
            val tMins = (todaySeconds % 3600) / 60
            val tSecs = todaySeconds % 60
            val todayFormatted = if (tHours > 0) "${tHours}h ${tMins}m ${tSecs}s" else "${tMins}m ${tSecs}s"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = colors.teal, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Today's Study: $todayFormatted",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (colors.isDark) colors.teal else colors.tealDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open Analytics",
                tint = colors.teal,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
