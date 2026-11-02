package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.util.QuestionFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.data.AttemptEntity
import com.example.data.DailyStudyAnalyticsEntity
import com.example.model.ExamConfig
import com.example.model.McqQuestion
import com.example.model.QuestionPaper
import com.example.ui.ExamTimerMode
import com.example.ui.components.StudentPracticeCard
import com.example.ui.components.AttemptHistoryDialog
import com.example.ui.components.LatexText
import com.example.ui.components.ExamPaletteSideSheet
import com.example.ui.components.StudentHistoricalAnalysisDialog
import com.example.ui.theme.ExamTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ReviewFilter { ALL, CORRECT, INCORRECT, UNANSWERED }

enum class ExamInterfaceMode(val title: String, val subtitle: String) {
    TCS_CBT("TCS iON CBT Mode", "Single question with Save & Next latency, Review & Next, and 5-state palette"),
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
    modifier: Modifier = Modifier
) {
    val positiveMarks = remember(config.positiveMarks, paper.metadata.positiveMarks) {
        if (paper.metadata.positiveMarks > 0.0) paper.metadata.positiveMarks else config.positiveMarks
    }
    val negativeMarks = remember(config.negativeMarks, paper.metadata.negativeMarks) {
        if (paper.metadata.negativeMarks >= 0.0) paper.metadata.negativeMarks else config.negativeMarks
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
        StudentHistoricalAnalysisDialog(
            todayTestSeconds = todayTestSeconds,
            todayAnalytics = todayAnalytics,
            historicalRecords = historicalRecords,
            dailyGoalMinutes = dailyGoalMinutes,
            isTestCurrentlyRunning = isExamStarted && !isSubmitted,
            onSetDailyGoalMinutes = onSetDailyGoalMinutes,
            onDismiss = { showHistoricalAnalyticsDialog = false }
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
                timeSpentSeconds = timeSpentSeconds,
                todayTestSeconds = todayTestSeconds,
                onRetakeFull = onResetExam,
                onRetryQuestions = { qNums -> onStartExam(qNums, null) },
                onExitToOverview = onExitToOverview,
                onSelectAnswer = onSelectAnswer,
                onOpenHistory = { showHistoryDialog = true },
                onOpenAnalytics = { showHistoricalAnalyticsDialog = true },
                onOpenPsychometrics = onOpenPsychometrics,
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
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val totalQuestions = paper.questions.size
    val hasUnfinishedSession = answers.isNotEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
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
                                text = paper.metadata.setNumber.ifBlank { "SET 1" },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onOpenPsychometrics) {
                                Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF0284C7))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Item CTT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val hours = todayTestSeconds / 3600
                            val minutes = (todayTestSeconds % 3600) / 60
                            val seconds = todayTestSeconds % 60
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
                        val fraction = if (goalSeconds > 0) (todayTestSeconds.toFloat() / goalSeconds).coerceIn(0f, 1f) else 0f
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
            }
        }

        // 4 Key Exam Metrics
        item {
            val maxMarksVal = totalQuestions * positiveMarks
            val formattedMaxMarks = if (maxMarksVal % 1.0 == 0.0) maxMarksVal.toInt().toString() else String.format(java.util.Locale.US, "%.1f", maxMarksVal)
            val formattedPenalty = if (negativeMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", negativeMarks) else String.format(java.util.Locale.US, "-%.2f", negativeMarks)
            val formattedPos = if (positiveMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "+%.0f", positiveMarks) else String.format(java.util.Locale.US, "+%.2f", positiveMarks)

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

        // Scoring Scheme Info Strip
        item {
            val formattedPenalty = if (negativeMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", negativeMarks) else String.format(java.util.Locale.US, "-%.2f", negativeMarks)
            val formattedPos = if (positiveMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "+%.0f", positiveMarks) else String.format(java.util.Locale.US, "+%.2f", positiveMarks)

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$formattedPos Correct  •  $formattedPenalty Incorrect  •  0.00 Unattended",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.tealLight.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "Min: 0.00",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
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
                            color = if (examInterfaceMode == ExamInterfaceMode.TCS_CBT) Color(0xFFEFF6FF) else colors.tealLight,
                            border = BorderStroke(0.8.dp, if (examInterfaceMode == ExamInterfaceMode.TCS_CBT) Color(0xFF2563EB) else colors.teal)
                        ) {
                            Text(
                                text = if (examInterfaceMode == ExamInterfaceMode.TCS_CBT) "TCS iON CBT" else "Scrollable Paper",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (examInterfaceMode == ExamInterfaceMode.TCS_CBT) Color(0xFF2563EB) else colors.teal,
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
                            color = if (isCbtSelected) Color(0xFFEFF6FF) else colors.surfaceVariant,
                            border = BorderStroke(
                                width = if (isCbtSelected) 2.dp else 1.dp,
                                color = if (isCbtSelected) Color(0xFF2563EB) else colors.border
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
                                        text = "TCS iON CBT",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isCbtSelected) Color(0xFF1E3A8A) else colors.inkBold
                                    )
                                    Icon(
                                        imageVector = if (isCbtSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isCbtSelected) Color(0xFF2563EB) else colors.muted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Single question viewport, Save & Next delay, Review & Next, and 5-state palette.",
                                    fontSize = 11.sp,
                                    color = if (isCbtSelected) Color(0xFF1E40AF) else colors.muted,
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
                                    .size(44.dp)
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
                    InstructionBullet(
                        number = "3",
                        title = "Penalty Scoring Scheme",
                        description = "Correct: $bulletPos mark. Incorrect penalty: $bulletNeg mark. Minimum total score is 0.00 regardless of penalty accumulation."
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
            val formattedMaxScore = if (maxPossibleScore % 1.0 == 0.0) maxPossibleScore.toInt().toString() else String.format(java.util.Locale.US, "%.1f", maxPossibleScore)
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
                        val m = timeSpentSeconds / 60
                        val s = timeSpentSeconds % 60
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
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Text(
                                    text = "Start Fresh",
                                    fontSize = 12.sp,
                                    color = colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = onResumeExam,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Resume Test",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
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
                            maxLines = 1
                        )
                    }

                    // Scan Physical OMR Sheet Button
                    OutlinedButton(
                        onClick = onScanOmr,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("scan_physical_omr_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.2.dp, Color(0xFFBE185D).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFBE185D)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFBE185D)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan Filled OMR Sheet",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            color = Color(0xFFBE185D)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
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
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var interfaceMode by remember { mutableStateOf(initialInterfaceMode) }
    var fontScaleDelta by remember { mutableIntStateOf(0) }
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

    var showSubmitConfirmDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showCbtPaletteSideSheet by remember { mutableStateOf(false) }

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

    // Auto-submission when countdown expires
    LaunchedEffect(timerMode, timeRemainingSeconds) {
        if (timerMode == ExamTimerMode.COUNTDOWN && timeRemainingSeconds <= 0L) {
            onSubmitExam()
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
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = colors.teal) },
            title = { Text("Pause or Exit Test?", fontWeight = FontWeight.Bold) },
            text = { Text("Your saved answers are preserved. You can resume anytime from the exam overview.") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        onExitToOverview()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal)
                ) {
                    Text("Exit to Overview")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Resume Test")
                }
            }
        )
    }

    // Official TCS Exam Summary Submission Modal
    if (showSubmitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = colors.teal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TCS iON Examination Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${paper.metadata.title.ifBlank { "Examination Paper" }} • ${paper.metadata.setNumber}",
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
                            TcsSummaryRow(label = "Answered", value = "$answeredCount", color = Color(0xFF22C55E))
                            TcsSummaryRow(label = "Not Answered", value = "$notAnsweredCount", color = Color(0xFFEF4444))
                            TcsSummaryRow(label = "Marked for Review", value = "$markedOnlyCount", color = Color(0xFF7C3AED))
                            TcsSummaryRow(label = "Ans & Marked for Review", value = "$ansMarkedCount", color = Color(0xFF7C3AED))
                            TcsSummaryRow(label = "Not Visited", value = "$notVisitedCount", color = colors.muted)
                        }
                    }

                    if (notAnsweredCount + notVisitedCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFEE2E2),
                            border = BorderStroke(0.8.dp, Color(0xFFEF4444))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Notice: ${notAnsweredCount + notVisitedCount} question(s) are unattempted.",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB91C1C)
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    modifier = Modifier.testTag("confirm_submit_test_button")
                ) {
                    Text("Yes, Submit Test", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSubmitConfirmDialog = false }) {
                    Text("Resume Test")
                }
            }
        )
    }

    Column(
        modifier = modifier
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
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back button + Title & Progress
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    IconButton(
                        onClick = { showExitConfirmDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit to Overview",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
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
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Right controls: Font Scale (A-, A, A+), Mode Switch (CBT vs Paper), Timer Pill, Fullscreen, Palette
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Font Scale Group (A-, A, A+) matching HTML CBT
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(0.6.dp, Color(0xFF334155))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)) {
                            Text(
                                text = "A-",
                                fontSize = 10.sp,
                                fontWeight = if (fontScaleDelta == -1) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (fontScaleDelta == -1) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                modifier = Modifier
                                    .clickable { fontScaleDelta = -1 }
                                    .padding(horizontal = 5.dp, vertical = 3.dp)
                            )
                            Text(
                                text = "A",
                                fontSize = 10.sp,
                                fontWeight = if (fontScaleDelta == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (fontScaleDelta == 0) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                modifier = Modifier
                                    .clickable { fontScaleDelta = 0 }
                                    .padding(horizontal = 5.dp, vertical = 3.dp)
                            )
                            Text(
                                text = "A+",
                                fontSize = 10.sp,
                                fontWeight = if (fontScaleDelta == 1) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (fontScaleDelta == 1) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                modifier = Modifier
                                    .clickable { fontScaleDelta = 1 }
                                    .padding(horizontal = 5.dp, vertical = 3.dp)
                            )
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
                        shape = RoundedCornerShape(4.dp),
                        color = if (interfaceMode == ExamInterfaceMode.TCS_CBT) Color(0xFF1E3A8A) else Color(0xFF0F766E),
                        border = BorderStroke(0.8.dp, if (interfaceMode == ExamInterfaceMode.TCS_CBT) Color(0xFF3B82F6) else Color(0xFF14B8A6))
                    ) {
                        Text(
                            text = if (interfaceMode == ExamInterfaceMode.TCS_CBT) "Paper" else "CBT",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    // Digital Timer Pill (TCS Style)
                    val formattedTime = if (timerMode == ExamTimerMode.COUNTDOWN) {
                        val h = timeRemainingSeconds / 3600
                        val m = (timeRemainingSeconds % 3600) / 60
                        val s = timeRemainingSeconds % 60
                        if (h > 0) String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
                        else String.format(java.util.Locale.US, "%02d:%02d", m, s)
                    } else {
                        val m = timeSpentSeconds / 60
                        val s = timeSpentSeconds % 60
                        String.format(java.util.Locale.US, "%02d:%02d", m, s)
                    }

                    val isTimerWarning = timerMode == ExamTimerMode.COUNTDOWN && timeRemainingSeconds <= 300L
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

                    // Fullscreen Toggle Button
                    IconButton(
                        onClick = { isFullscreen = !isFullscreen },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen Mode",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Palette Grid Button
                    IconButton(
                        onClick = { showCbtPaletteSideSheet = true },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("cbt_header_palette_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "Open CBT Palette",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
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
                        .padding(horizontal = 10.dp, vertical = 6.dp),
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
                            else -> Pair(Color(0xFFE2E8F0), Color(0xFF334155))
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
                                color = if (isCurrent) Color(0xFF0F172A) else Color.Transparent
                            ),
                            modifier = Modifier.size(32.dp)
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

            // Main Single Question Viewport
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(6.dp)) }

                // Question Meta Tag Bar
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Question No. ${currentQuestion.num}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.inkBold
                        )

                        val posStr = if (positiveMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "+%.0f", positiveMarks) else String.format(java.util.Locale.US, "+%.2f", positiveMarks)
                        val negStr = if (negativeMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", negativeMarks) else String.format(java.util.Locale.US, "-%.2f", negativeMarks)

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFDCFCE7)) {
                                Text(
                                    text = "$posStr Mark",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFEE2E2)) {
                                Text(
                                    text = "$negStr Penalty",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB91C1C),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Question Stem Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.2.dp, colors.border),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
                                        Text("Reason (R): ", fontWeight = FontWeight.Bold, fontSize = qFontSize, color = Color(0xFFD97706))
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

                // Radio Options List (A, B, C, D)
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val rawOptions = listOf(
                            "A" to currentQuestion.options.a,
                            "B" to currentQuestion.options.b,
                            "C" to currentQuestion.options.c,
                            "D" to currentQuestion.options.d
                        )

                        rawOptions.forEach { (key, optText) ->
                            if (optText.isNotBlank()) {
                                val isSelected = pendingOption.equals(key, ignoreCase = true)
                                Surface(
                                    onClick = {
                                        // Tapping radio updates pending draft selection (does NOT save or color green until Save & Next)
                                        pendingOption = key
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFFEFF6FF) else colors.surface,
                                    border = BorderStroke(
                                        width = if (isSelected) 1.8.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF2563EB) else colors.border
                                    ),
                                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("option_${key}_q${currentQuestion.num}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFF2563EB) else colors.muted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        LatexText(
                                            text = "$key. $optText",
                                            fontSize = optFontSize,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color(0xFF1E3A8A) else colors.inkBold,
                                            lineHeight = optLineHeight,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(14.dp)) }
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
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Mark for Review & Next (Purple)
                        OutlinedButton(
                            onClick = {
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
                            },
                            border = BorderStroke(1.2.dp, Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7C3AED)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                        ) {
                            Text(
                                "Review & Next",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Clear Response
                        OutlinedButton(
                            onClick = {
                                pendingOption = ""
                                onSelectAnswer(currentQuestion.num, "")
                                markedForReview.remove(currentQuestion.num)
                            },
                            border = BorderStroke(1.dp, Color(0xFFEA580C)),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEA580C)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(0.9f)
                                .heightIn(min = 40.dp)
                        ) {
                            Text(
                                "Clear",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Save & Next (With simulated TCS server latency of 400ms)
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isSavingNext = true
                                    delay(400) // Simulated TCS network save delay
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
                            },
                            enabled = !isSavingNext,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .heightIn(min = 40.dp)
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
                                    softWrap = false
                                )
                            } else {
                                Text(
                                    text = if (currentIndex == activeQuestions.size - 1) "Save & Submit" else "Save & Next",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
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
                            onClick = {
                                if (currentIndex > 0) {
                                    currentIndex--
                                    if (!visitedQuestions.contains(activeQuestions[currentIndex].num)) {
                                        visitedQuestions.add(activeQuestions[currentIndex].num)
                                    }
                                }
                            },
                            enabled = currentIndex > 0,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 38.dp)
                        ) {
                            Text(
                                "← Previous",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = { showSubmitConfirmDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 38.dp)
                        ) {
                            Text(
                                "Submit Test ($answeredCount/$totalQuestions)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
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
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(activeQuestions, key = { _, q -> "scroll_strip_${q.num}" }) { idx, q ->
                        val isAnswered = answers[q.num]?.isNotBlank() == true
                        val isMarked = markedForReview.contains(q.num)
                        val bg = when {
                            isMarked -> Color(0xFF7C3AED)
                            isAnswered -> Color(0xFF22C55E)
                            else -> Color(0xFFE2E8F0)
                        }
                        val textCol = if (isAnswered || isMarked) Color.White else Color(0xFF334155)

                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    scrollListState.animateScrollToItem(idx)
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = bg,
                            modifier = Modifier.size(32.dp)
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
            LazyColumn(
                state = scrollListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                itemsIndexed(activeQuestions, key = { _, q -> "scroll_q_${q.num}" }) { _, q ->
                    val isMarked = markedForReview.contains(q.num)
                    val selectedAns = answers[q.num] ?: ""

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.2.dp, if (isMarked) Color(0xFF7C3AED) else colors.border),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header: Number + Marks + Review Bookmark Icon
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (selectedAns.isNotBlank()) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                                        border = BorderStroke(1.dp, if (selectedAns.isNotBlank()) Color(0xFF22C55E) else colors.border)
                                    ) {
                                        Text(
                                            text = "Q ${q.num}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedAns.isNotBlank()) Color(0xFF15803D) else colors.inkBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "+${String.format(java.util.Locale.US, "%.0f", positiveMarks)} / -${String.format(java.util.Locale.US, "%.0f", negativeMarks)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.muted
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (markedForReview.contains(q.num)) markedForReview.remove(q.num)
                                        else markedForReview.add(q.num)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = "Bookmark",
                                        tint = if (isMarked) Color(0xFF7C3AED) else colors.muted.copy(alpha = 0.5f),
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

                            // Options List (Instant Tap Response)
                            val options = listOf(
                                "A" to q.options.a,
                                "B" to q.options.b,
                                "C" to q.options.c,
                                "D" to q.options.d
                            )

                            options.forEach { (key, optText) ->
                                if (optText.isNotBlank()) {
                                    val isChosen = selectedAns.equals(key, ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            // Instant selection toggle
                                            onSelectAnswer(q.num, if (isChosen) "" else key)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isChosen) Color(0xFFEFF6FF) else colors.surfaceVariant.copy(alpha = 0.6f),
                                        border = BorderStroke(
                                            width = if (isChosen) 1.6.dp else 0.8.dp,
                                            color = if (isChosen) Color(0xFF2563EB) else colors.border
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isChosen) Color(0xFF2563EB) else Color.White,
                                                border = BorderStroke(1.2.dp, if (isChosen) Color(0xFF2563EB) else colors.border),
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                    Text(
                                                        text = key,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isChosen) Color.White else colors.inkBold
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            LatexText(
                                                text = optText,
                                                fontSize = optFontSize,
                                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isChosen) Color(0xFF1E3A8A) else colors.inkBold,
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

            // Bottom Sticky Bar for Scrollable Paper Mode
            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
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
                            color = Color(0xFF15803D)
                        )
                        Text(
                            text = "Marked: ${markedOnlyCount + ansMarkedCount}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF7C3AED)
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Switch to TCS CBT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showSubmitConfirmDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Submit Test", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
}

@Composable
private fun TcsSummaryRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.5.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
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
    timeSpentSeconds: Long,
    todayTestSeconds: Long = 0L,
    onRetakeFull: () -> Unit,
    onRetryQuestions: (List<Int>?) -> Unit,
    onExitToOverview: () -> Unit,
    onSelectAnswer: (Int, String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAnalytics: () -> Unit = {},
    onOpenPsychometrics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val resultListState = rememberLazyListState()
    var filter by remember { mutableStateOf(ReviewFilter.ALL) }

    // Official one-time evaluation
    val officialAnswers = if (submittedAnswers.isNotEmpty()) submittedAnswers else answers
    val stats = submittedStats ?: com.example.ui.MainViewModel.calculateTestStats(paper, officialAnswers, positiveMarks, negativeMarks)
    val total = stats.total
    val correctCount = stats.correct
    val wrongCount = stats.incorrect
    val unattempted = stats.unanswered
    val percentage = stats.percentage

    // Persistent review logs: questions that were incorrect or unattended at exam submission time.
    // They REMAIN in this log even if the user attends or revises answers in review mode!
    val persistentIncorrect = if (incorrectQuestionLog.isNotEmpty()) {
        incorrectQuestionLog
    } else {
        paper.questions.filter { q ->
            !q.isNeutralized && run {
                val ans = officialAnswers[q.num]
                !ans.isNullOrBlank() && !ans.equals(q.correctAnswer, ignoreCase = true)
            }
        }.map { it.num }.toSet()
    }

    val persistentUnattended = if (unattendedQuestionLog.isNotEmpty()) {
        unattendedQuestionLog
    } else {
        paper.questions.filter { q ->
            !q.isNeutralized && officialAnswers[q.num].isNullOrBlank()
        }.map { it.num }.toSet()
    }

    // Filtered questions based on selected chip
    val filteredQuestions = when (filter) {
        ReviewFilter.ALL -> paper.questions
        ReviewFilter.CORRECT -> paper.questions.filter { it.isNeutralized || (it.num !in persistentIncorrect && it.num !in persistentUnattended) }
        ReviewFilter.INCORRECT -> paper.questions.filter { it.num in persistentIncorrect }
        ReviewFilter.UNANSWERED -> paper.questions.filter { it.num in persistentUnattended }
    }

    LazyColumn(
        state = resultListState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Top Performance Card with One-Time Score & Penalty Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.2.dp, if (percentage >= 50) colors.green.copy(alpha = 0.5f) else colors.red.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Celebration / Status banner
                    val (statusText, statusBg, statusColor) = when {
                        percentage >= 80 -> Triple("🎉 Outstanding Performance! Excellent Work!", colors.greenLight, colors.green)
                        percentage >= 50 -> Triple("👍 Good Effort! Assessment completed.", colors.tealLight, colors.teal)
                        else -> Triple("📚 Keep Practicing! Detailed solutions & review log below.", colors.redLight, colors.red)
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Candidate Identification Badges (Roll No., Booklet No., Set Code)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(0.6.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Roll: ${paper.metadata.rollNumber}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFDF2F8),
                            border = BorderStroke(0.6.dp, Color(0xFFEC4899).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Booklet: ${paper.metadata.bookletNumber}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFBE185D),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.tealLight,
                            border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Set: ${paper.metadata.setNumber}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val maxMarksVal = total * positiveMarks
                    val formattedMaxMarks = if (maxMarksVal % 1.0 == 0.0) maxMarksVal.toInt().toString() else String.format(java.util.Locale.US, "%.1f", maxMarksVal)
                    val formattedPos = if (positiveMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "+%.0f", positiveMarks) else String.format(java.util.Locale.US, "+%.2f", positiveMarks)
                    val formattedPenalty = if (negativeMarks % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f", negativeMarks) else String.format(java.util.Locale.US, "-%.2f", negativeMarks)
                    val pointsCorrect = correctCount * positiveMarks
                    val formattedPointsCorrect = if (pointsCorrect % 1.0 == 0.0) String.format(java.util.Locale.US, "+%.0f", pointsCorrect) else String.format(java.util.Locale.US, "+%.2f", pointsCorrect)

                    Text(
                        text = "Official Score: ${stats.formattedScore} / $formattedMaxMarks ($percentage%)",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (percentage >= 50) colors.green else colors.red
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Penalty Point Accounting Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PenaltyMetricBadge(
                            title = "Correct",
                            points = formattedPointsCorrect,
                            subtitle = "$formattedPos ea",
                            color = colors.green
                        )
                        PenaltyMetricBadge(
                            title = "Incorrect",
                            points = "-${String.format(java.util.Locale.US, "%.2f", stats.penaltyIncorrect)}",
                            subtitle = "$formattedPenalty penalty",
                            color = colors.red
                        )
                        val unattendedPenaltyStr = if (stats.penaltyUnattended > 0.0) {
                            "-${String.format(java.util.Locale.US, "%.2f", stats.penaltyUnattended)}"
                        } else {
                            "0.00"
                        }
                        val unattendedSubtitle = if (stats.penaltyUnattended > 0.0) {
                            val rate = if (stats.unanswered > 0) stats.penaltyUnattended / stats.unanswered else 0.10
                            if (rate % 1.0 == 0.0) String.format(java.util.Locale.US, "-%.0f penalty", rate)
                            else String.format(java.util.Locale.US, "-%.2f penalty", rate)
                        } else {
                            "0.00 penalty"
                        }
                        PenaltyMetricBadge(
                            title = "Unattended",
                            points = unattendedPenaltyStr,
                            subtitle = unattendedSubtitle,
                            color = if (stats.penaltyUnattended > 0.0) colors.red else colors.muted
                        )
                        val h = timeSpentSeconds / 3600
                        val m = (timeSpentSeconds % 3600) / 60
                        val s = timeSpentSeconds % 60
                        val timePoints = if (h > 0) {
                            String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
                        } else {
                            String.format(java.util.Locale.US, "%02d:%02d", m, s)
                        }
                        PenaltyMetricBadge(
                            title = "Time",
                            points = timePoints,
                            subtitle = "Duration",
                            color = colors.teal
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Notice: One-Time Evaluation & Persistent Review
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(0.8.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = colors.teal, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "One-Time Scoring: Official score is fixed (Min: 0). Correcting answers in review mode is for study practice and does not alter this score. Retake the full test to re-evaluate.",
                                fontSize = 11.sp,
                                color = colors.inkBold,
                                lineHeight = 14.5.sp
                            )
                        }
                    }

                    if (todayTestSeconds > 0L) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            onClick = onOpenAnalytics,
                            shape = RoundedCornerShape(8.dp),
                            color = colors.tealLight.copy(alpha = 0.5f),
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = colors.teal,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val tHours = todayTestSeconds / 3600
                                    val tMins = (todayTestSeconds % 3600) / 60
                                    val tSecs = todayTestSeconds % 60
                                    val todayFormatted = if (tHours > 0) {
                                        "${tHours}h ${tMins}m ${tSecs}s"
                                    } else {
                                        "${tMins}m ${tSecs}s"
                                    }
                                    Text(
                                        text = "Today's Test Study: $todayFormatted",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.tealDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Historical Analysis",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.teal,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = colors.teal,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons Row
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRetakeFull,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Retake Full Test", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        // Targeted Retry: ONLY Incorrect
                        if (persistentIncorrect.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    onRetryQuestions(persistentIncorrect.toList())
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, colors.red.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.red),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Retry Errors (${persistentIncorrect.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        // Targeted Retry: ONLY Unanswered
                        if (persistentUnattended.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    onRetryQuestions(persistentUnattended.toList())
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, colors.muted.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.muted),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Retry Skipped (${persistentUnattended.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onExitToOverview) {
                            Text("Exam Overview", fontSize = 12.sp, color = colors.muted)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onOpenPsychometrics) {
                                Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF0284C7))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Item CTT", fontSize = 12.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(onClick = onOpenAnalytics) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.teal)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Analytics", fontSize = 12.sp, color = colors.teal, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(onClick = onOpenHistory) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.muted)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("History", fontSize = 12.sp, color = colors.muted, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Section Title & Filter Options
        item {
            Column {
                Text(
                    text = "Detailed Solutions & Incorrect Review Log",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = "Incorrect questions remain logged for review below even if corrected during study practice.",
                    fontSize = 11.5.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Interactive Filter Chips with Persistent Counts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReviewFilterChip(
                        label = "All",
                        count = total,
                        selected = filter == ReviewFilter.ALL,
                        badgeColor = colors.inkBold,
                        onClick = { filter = ReviewFilter.ALL },
                        modifier = Modifier.weight(1f)
                    )
                    ReviewFilterChip(
                        label = "Incorrect",
                        count = persistentIncorrect.size,
                        selected = filter == ReviewFilter.INCORRECT,
                        badgeColor = colors.red,
                        onClick = { filter = ReviewFilter.INCORRECT },
                        modifier = Modifier.weight(1f)
                    )
                    ReviewFilterChip(
                        label = "Unattended",
                        count = persistentUnattended.size,
                        selected = filter == ReviewFilter.UNANSWERED,
                        badgeColor = colors.muted,
                        onClick = { filter = ReviewFilter.UNANSWERED },
                        modifier = Modifier.weight(1f)
                    )
                    ReviewFilterChip(
                        label = "Correct",
                        count = (total - persistentIncorrect.size - persistentUnattended.size).coerceAtLeast(0),
                        selected = filter == ReviewFilter.CORRECT,
                        badgeColor = colors.green,
                        onClick = { filter = ReviewFilter.CORRECT },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Empty state if filtered question list is empty
        if (filteredQuestions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
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
                                ReviewFilter.INCORRECT -> "🎉 Outstanding! No incorrect answers in this test."
                                ReviewFilter.UNANSWERED -> "👏 Great! All questions were attended in this test."
                                ReviewFilter.CORRECT -> "No correct answers recorded yet."
                                else -> "No questions found."
                            },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.muted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Detailed Solution Cards for each question in filtered set
            items(filteredQuestions, key = { "result_q_${it.num}" }) { q ->
                val isOriginallyIncorrect = q.num in persistentIncorrect
                val isOriginallyUnattended = q.num in persistentUnattended
                val currentAnswer = answers[q.num]
                val isCorrectNow = !currentAnswer.isNullOrBlank() && currentAnswer.equals(q.correctAnswer, ignoreCase = true)

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (q.isNeutralized) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                            color = colors.teal.copy(alpha = 0.12f),
                            border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Question Neutralized: Full marks awarded to all candidates (Examination Board Rule)",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.teal
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                                        "Logged for Review: Originally incorrect in test • Corrected in review practice"
                                    else
                                        "Logged for Review: Incorrect in test (Score penalty: $cardPenaltyStr pts)",
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
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = colors.muted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Logged for Review: Unattended in test (Penalty: -0.10 pts)",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.muted
                                )
                            }
                        }
                    }

                    StudentPracticeCard(
                        question = q,
                        selectedAnswer = officialAnswers[q.num],
                        onSelectAnswer = { },
                        showResult = true
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun PenaltyMetricBadge(
    title: String,
    points: String,
    subtitle: String,
    color: Color
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(0.8.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = points, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(text = title, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
            Text(text = subtitle, fontSize = 9.sp, color = colors.muted)
        }
    }
}

@Composable
private fun ReviewFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        modifier = modifier
            .height(38.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.teal else colors.surface,
        border = BorderStroke(1.dp, if (selected) colors.teal else colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.surface else colors.inkBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(3.dp))
            Surface(
                shape = CircleShape,
                color = if (selected) colors.surface.copy(alpha = 0.25f) else badgeColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "$count",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) colors.surface else badgeColor,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun ScoreStatBadge(
    label: String,
    value: String,
    color: Color
) {
    val colors = ExamTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(text = label, fontSize = 11.sp, color = colors.muted)
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
