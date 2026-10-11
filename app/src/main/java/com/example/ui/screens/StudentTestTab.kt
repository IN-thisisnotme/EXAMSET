package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AttemptEntity
import com.example.data.DailyStudyAnalyticsEntity
import com.example.model.McqQuestion
import com.example.model.QuestionPaper
import com.example.ui.ExamTimerMode
import com.example.ui.components.StudentPracticeCard
import com.example.ui.components.AttemptHistoryDialog
import com.example.ui.components.ExamPaletteSideSheet
import com.example.ui.components.StudentHistoricalAnalysisDialog
import com.example.ui.theme.ExamTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ReviewFilter { ALL, CORRECT, INCORRECT, UNANSWERED }

@Composable
fun StudentTestTab(
    paper: QuestionPaper,
    answers: Map<Int, String>,
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
    modifier: Modifier = Modifier
) {
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showHistoricalAnalyticsDialog by remember { mutableStateOf(false) }

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
                timeSpentSeconds = timeSpentSeconds,
                todayTestSeconds = todayTestSeconds,
                onRetakeFull = onResetExam,
                onRetryQuestions = { qNums -> onStartExam(qNums, null) },
                onExitToOverview = onExitToOverview,
                onSelectAnswer = onSelectAnswer,
                onOpenHistory = { showHistoryDialog = true },
                onOpenAnalytics = { showHistoricalAnalyticsDialog = true },
                modifier = modifier
            )
        }
        isExamStarted -> {
            ActiveExamView(
                paper = paper,
                activeQuestions = activeQuestions,
                answers = answers,
                timeSpentSeconds = timeSpentSeconds,
                timeRemainingSeconds = timeRemainingSeconds,
                timerMode = timerMode,
                retryQuestionNumbers = retryQuestionNumbers,
                todayTestSeconds = todayTestSeconds,
                shuffleOptions = shuffleOptions,
                sessionSeed = sessionSeed,
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
                attempts = attempts,
                todayTestSeconds = todayTestSeconds,
                dailyGoalMinutes = dailyGoalMinutes,
                shuffleOptions = shuffleOptions,
                onToggleShuffleOptions = onToggleShuffleOptions,
                onSetTimerMode = onSetTimerMode,
                onSetCustomTimeLimit = onSetCustomTimeLimit,
                onStartExam = { onStartExam(null, customTimeLimitMinutes) },
                onResumeExam = onResumeExam,
                onOpenHistory = { showHistoryDialog = true },
                onOpenAnalytics = { showHistoricalAnalyticsDialog = true },
                onLoadAttempt = onLoadAttempt,
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
    attempts: List<AttemptEntity>,
    todayTestSeconds: Long,
    dailyGoalMinutes: Int,
    shuffleOptions: Boolean,
    onToggleShuffleOptions: (Boolean) -> Unit,
    onSetTimerMode: (ExamTimerMode) -> Unit,
    onSetCustomTimeLimit: (Int) -> Unit,
    onStartExam: () -> Unit,
    onResumeExam: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onLoadAttempt: (AttemptEntity) -> Unit,
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
                                text = paper.metadata.setNumber.ifBlank { "SET - A" },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (attempts.isNotEmpty()) {
                            TextButton(onClick = onOpenHistory) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("History (${attempts.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    value = "$totalQuestions",
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
                    value = "-0.33",
                    icon = Icons.Default.Remove,
                    tint = colors.red,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Scoring Scheme Info Strip
        item {
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
                            text = "+1.00 Correct  •  -0.33 Incorrect  •  -0.10 Unattended",
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
                    InstructionBullet(
                        number = "3",
                        title = "Penalty Scoring Scheme",
                        description = "Correct: +1.00 mark. Incorrect penalty: -0.33 mark. Unattended penalty: -0.10 mark. Minimum total score is 0.00 regardless of penalty accumulation."
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
            val pct = if (latest.totalQuestions > 0) ((latest.score / latest.totalQuestions.toDouble()) * 100).toInt().coerceIn(0, 100) else 0
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
                                "Score: ${latest.getFormattedScore()} / ${latest.totalQuestions} ($pct%)",
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onStartExam,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, colors.border)
                            ) {
                                Text("Start Fresh", fontSize = 12.sp, color = colors.inkBold)
                            }
                            Button(
                                onClick = onResumeExam,
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume Test", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Main Primary Start Test Button
            item {
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
                    Text(
                        text = if (timerMode == ExamTimerMode.COUNTDOWN) {
                            "Start Timed Exam (${customTimeLimitMinutes}m)"
                        } else {
                            "Start Practice"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

/**
 * Screen 2: Active Exam Session with live timer, question jump palette, and confirmation modal.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActiveExamView(
    paper: QuestionPaper,
    activeQuestions: List<McqQuestion>,
    answers: Map<Int, String>,
    timeSpentSeconds: Long,
    timeRemainingSeconds: Long,
    timerMode: ExamTimerMode,
    retryQuestionNumbers: List<Int>?,
    todayTestSeconds: Long = 0L,
    shuffleOptions: Boolean = false,
    sessionSeed: Long = 0L,
    onSelectAnswer: (Int, String) -> Unit,
    onSubmitExam: () -> Unit,
    onExitToOverview: () -> Unit,
    onSwitchToFull: () -> Unit,
    onOpenAnalytics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val haptic = LocalHapticFeedback.current
    val activeListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val answeredCount = activeQuestions.count { answers.containsKey(it.num) }
    val total = activeQuestions.size
    val unattempted = total - answeredCount

    var showSubmitConfirmDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showCbtPaletteSideSheet by remember { mutableStateOf(false) }

    // Folding toggle state (commented out per user preference; uncomment if folding toggle is needed)
    // var isPaletteFolded by remember { mutableStateOf(false) }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = colors.teal) },
            title = { Text("Pause or Exit Test?", fontWeight = FontWeight.Bold) },
            text = { Text("Your answers are saved. You can resume anytime from the exam overview.") },
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

    if (showSubmitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = if (unattempted > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (unattempted > 0) colors.red else colors.green
                )
            },
            title = {
                Text(
                    text = if (unattempted > 0) "Unattempted Questions!" else "Submit Examination?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    if (unattempted > 0) {
                        Text(
                            text = "You have $unattempted unattended question(s) (-0.10 penalty each).",
                            fontWeight = FontWeight.SemiBold,
                            color = colors.red
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = "Once submitted, your final score and detailed solution explanations will be displayed immediately at the top.",
                        fontSize = 13.sp,
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
                    colors = ButtonDefaults.buttonColors(containerColor = colors.navyBanner),
                    modifier = Modifier.testTag("confirm_submit_test_button")
                ) {
                    Text("Yes, Submit Test", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSubmitConfirmDialog = false }) {
                    Text("Keep Practicing")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Fixed compact Question Bubbles bar at the top with CBT Grid Palette button
        Surface(
            color = colors.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp)
                ) {
                    itemsIndexed(activeQuestions, key = { _, q -> "palette_bubble_${q.num}" }) { index, q ->
                        val isAnswered = answers.containsKey(q.num)
                        Surface(
                            modifier = Modifier
                                .size(34.dp)
                                .clickable {
                                    coroutineScope.launch {
                                        // item 0 is Spacer, item 1 is Header Card, questions begin at index + 2
                                        activeListState.animateScrollToItem(index + 2)
                                    }
                                },
                            shape = CircleShape,
                            color = if (isAnswered) colors.teal else colors.surface,
                            border = BorderStroke(1.dp, if (isAnswered) colors.teal else colors.border)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${q.num}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAnswered) colors.surface else colors.inkBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    onClick = { showCbtPaletteSideSheet = true },
                    shape = RoundedCornerShape(8.dp),
                    color = colors.teal.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("cbt_strip_palette_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "Open Full CBT Palette",
                            tint = colors.teal,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Grid",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark
                        )
                    }
                }
            }
        }

        LazyColumn(
            state = activeListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Live Header Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showExitConfirmDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Exit to Overview",
                                    tint = colors.inkBold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Active Test",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Today's Cumulative Study Timer Chip
                            Surface(
                                onClick = onOpenAnalytics,
                                shape = RoundedCornerShape(14.dp),
                                color = colors.tealLight.copy(alpha = 0.5f),
                                border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .testTag("active_test_today_timer_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "Today's Study Timer",
                                        tint = colors.teal,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    val todayM = todayTestSeconds / 60
                                    val todayS = todayTestSeconds % 60
                                    Text(
                                        text = "Today ${String.format(java.util.Locale.US, "%02d:%02d", todayM, todayS)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.tealDark
                                    )
                                }
                            }

                            // Live timer badge
                        if (timerMode == ExamTimerMode.COUNTDOWN) {
                            val remM = timeRemainingSeconds / 60
                            val remS = timeRemainingSeconds % 60
                            val isUrgent = timeRemainingSeconds <= 60L
                            val isWarning = timeRemainingSeconds in 61L..300L

                            val badgeBg = when {
                                isUrgent -> colors.redLight
                                isWarning -> Color(0xFFFEF3C7)
                                else -> colors.tealLight
                            }
                            val badgeBorder = when {
                                isUrgent -> colors.red
                                isWarning -> Color(0xFFD97706)
                                else -> colors.teal.copy(alpha = 0.4f)
                            }
                            val badgeText = when {
                                isUrgent -> colors.red
                                isWarning -> Color(0xFFB45309)
                                else -> colors.teal
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = badgeBg,
                                border = BorderStroke(1.dp, badgeBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isUrgent) Icons.Default.Warning else Icons.Default.HourglassBottom,
                                        contentDescription = null,
                                        tint = badgeText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format(java.util.Locale.US, "%02d:%02d", remM, remS),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeText
                                    )
                                    if (isUrgent) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "LEFT",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = badgeText
                                        )
                                    }
                                }
                            }
                        } else {
                            val m = timeSpentSeconds / 60
                            val s = timeSpentSeconds % 60
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = colors.tealLight,
                                border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = colors.teal, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format(java.util.Locale.US, "%02d:%02d", m, s),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.teal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { showCbtPaletteSideSheet = true },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("cbt_header_palette_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "Open CBT Palette",
                                tint = colors.teal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$answeredCount of $total Answered",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.teal
                        )
                        Text(
                            text = if (unattempted > 0) "$unattempted Remaining" else "All Attempted! 🎉",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (unattempted > 0) colors.muted else colors.green
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { if (total > 0) answeredCount.toFloat() / total else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = colors.teal,
                        trackColor = colors.borderLight
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(0.6.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Marking: +1.00 Correct • -0.33 Incorrect • -0.10 Unattended",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Min: 0.00",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal
                            )
                        }
                    }

                    // If in focused retry mode, show distinct guidance banner
                    if (retryQuestionNumbers != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.tealLight,
                            border = BorderStroke(0.5.dp, colors.teal.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚡ Focused Retry Session ($total Questions)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.teal
                                )
                                TextButton(
                                    onClick = onSwitchToFull,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 34.dp)
                                ) {
                                    Text("Switch to Full Paper", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                                }
                            }
                        }
                    }
                }
            }
        }



        // Question Practice Cards
        items(activeQuestions, key = { "practice_${it.num}" }) { q ->
            StudentPracticeCard(
                question = q,
                selectedAnswer = answers[q.num],
                onSelectAnswer = { onSelectAnswer(q.num, it) },
                showResult = false,
                shuffleOptions = shuffleOptions,
                sessionSeed = sessionSeed
            )
        }

        // Bottom Submit Button
        item {
            Button(
                onClick = { showSubmitConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.navyBanner,
                    contentColor = colors.navyBannerText
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("submit_exam_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Submit Test ($answeredCount/$total)",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

    // CBT Exam Question Palette Side Sheet (TCS iON Standard)
    ExamPaletteSideSheet(
        isOpen = showCbtPaletteSideSheet,
        onDismiss = { showCbtPaletteSideSheet = false },
        questions = activeQuestions,
        paperTitle = paper.metadata.title,
        setNumber = paper.metadata.setNumber,
        isCbtMode = true,
        answers = answers,
        visitedQuestions = remember(activeQuestions, answers) {
            answers.keys.toSet()
        },
        onQuestionClick = { index, _ ->
            coroutineScope.launch {
                val target = index + 2
                if (kotlin.math.abs(activeListState.firstVisibleItemIndex - target) > 8) {
                    activeListState.scrollToItem(target)
                } else {
                    activeListState.animateScrollToItem(target)
                }
            }
        }
    )
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
    timeSpentSeconds: Long,
    todayTestSeconds: Long = 0L,
    onRetakeFull: () -> Unit,
    onRetryQuestions: (List<Int>?) -> Unit,
    onExitToOverview: () -> Unit,
    onSelectAnswer: (Int, String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAnalytics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val resultListState = rememberLazyListState()
    var filter by remember { mutableStateOf(ReviewFilter.ALL) }

    // Official one-time evaluation
    val officialAnswers = if (submittedAnswers.isNotEmpty()) submittedAnswers else answers
    val stats = submittedStats ?: com.example.ui.MainViewModel.calculateTestStats(paper, officialAnswers)
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
            val ans = officialAnswers[q.num]
            !ans.isNullOrBlank() && !ans.equals(q.correctAnswer, ignoreCase = true)
        }.map { it.num }.toSet()
    }

    val persistentUnattended = if (unattendedQuestionLog.isNotEmpty()) {
        unattendedQuestionLog
    } else {
        paper.questions.filter { q ->
            officialAnswers[q.num].isNullOrBlank()
        }.map { it.num }.toSet()
    }

    // Filtered questions based on selected chip
    val filteredQuestions = when (filter) {
        ReviewFilter.ALL -> paper.questions
        ReviewFilter.CORRECT -> paper.questions.filter { it.num !in persistentIncorrect && it.num !in persistentUnattended }
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Official Score: ${stats.formattedScore} / $total ($percentage%)",
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
                            points = "+${correctCount}.00",
                            subtitle = "+1.00 ea",
                            color = colors.green
                        )
                        PenaltyMetricBadge(
                            title = "Incorrect",
                            points = "-${String.format(java.util.Locale.US, "%.2f", stats.penaltyIncorrect)}",
                            subtitle = "-0.33 penalty",
                            color = colors.red
                        )
                        PenaltyMetricBadge(
                            title = "Unattended",
                            points = "-${String.format(java.util.Locale.US, "%.2f", stats.penaltyUnattended)}",
                            subtitle = "-0.10 penalty",
                            color = colors.muted
                        )
                        val m = timeSpentSeconds / 60
                        val s = timeSpentSeconds % 60
                        PenaltyMetricBadge(
                            title = "Time",
                            points = String.format(java.util.Locale.US, "%02d:%02d", m, s),
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
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                        color = colors.tealDark
                                    )
                                }
                                Text(
                                    text = "Historical Analysis →",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.teal
                                )
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
                        modifier = Modifier.weight(1.1f)
                    )
                    ReviewFilterChip(
                        label = "Unattended",
                        count = persistentUnattended.size,
                        selected = filter == ReviewFilter.UNANSWERED,
                        badgeColor = colors.muted,
                        onClick = { filter = ReviewFilter.UNANSWERED },
                        modifier = Modifier.weight(1.2f)
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
                    if (isOriginallyIncorrect) {
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
                                Text(
                                    text = if (isCorrectNow)
                                        "Logged for Review: Originally incorrect in test • Corrected in review practice"
                                    else
                                        "Logged for Review: Incorrect in test (Score penalty: -0.33 pts)",
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
                        selectedAnswer = answers[q.num],
                        onSelectAnswer = { onSelectAnswer(q.num, it) },
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
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.teal else colors.surface,
        border = BorderStroke(1.dp, if (selected) colors.teal else colors.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.surface else colors.inkBold
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
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
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
