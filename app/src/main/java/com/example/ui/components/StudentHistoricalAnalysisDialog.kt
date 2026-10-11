package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.DailyStudyAnalyticsEntity
import com.example.ui.theme.ExamTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun StudentHistoricalAnalysisDialog(
    todayTestSeconds: Long,
    todayAnalytics: DailyStudyAnalyticsEntity?,
    historicalRecords: List<DailyStudyAnalyticsEntity>,
    dailyGoalMinutes: Int,
    isTestCurrentlyRunning: Boolean,
    onSetDailyGoalMinutes: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current

    // Pulsing indicator for currently active exam session
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Merge today's live ticking seconds with today's entity
    val activeTodayEntity = remember(todayTestSeconds, todayAnalytics, dailyGoalMinutes) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val base = todayAnalytics ?: DailyStudyAnalyticsEntity(dateString = todayStr)
        base.copy(
            totalTestTimeSeconds = todayTestSeconds,
            dailyGoalSeconds = dailyGoalMinutes * 60L
        )
    }

    // Prepare complete historical list including today
    val fullHistoryList = remember(activeTodayEntity, historicalRecords) {
        val todayStr = activeTodayEntity.dateString
        val filtered = historicalRecords.filter { it.dateString != todayStr }
        listOf(activeTodayEntity) + filtered
    }

    // Calculate all-time aggregate stats
    val totalAllTimeSeconds = remember(fullHistoryList) {
        fullHistoryList.sumOf { it.totalTestTimeSeconds }
    }
    val totalAllTimeTests = remember(fullHistoryList) {
        fullHistoryList.sumOf { it.testsCompletedCount }
    }
    val totalAllTimeQuestions = remember(fullHistoryList) {
        fullHistoryList.sumOf { it.questionsAttemptedCount }
    }
    val totalAllTimeCorrect = remember(fullHistoryList) {
        fullHistoryList.sumOf { it.correctAnswersCount }
    }
    val allTimeAccuracy = remember(totalAllTimeQuestions, totalAllTimeCorrect) {
        if (totalAllTimeQuestions > 0) {
            (totalAllTimeCorrect.toFloat() / totalAllTimeQuestions) * 100f
        } else 0f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            border = BorderStroke(1.2.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.tealLight,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Study & Test Analytics",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Daily timer & historical progress",
                                fontSize = 11.5.sp,
                                color = colors.muted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_analytics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Analytics",
                            tint = colors.inkBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // --- SECTION 1: TODAY'S LIVE TIMER CARD ---
                    item {
                        TodayLiveTimerCard(
                            todaySeconds = todayTestSeconds,
                            todayEntity = activeTodayEntity,
                            dailyGoalMinutes = dailyGoalMinutes,
                            isCurrentlyActive = isTestCurrentlyRunning,
                            pulseAlpha = pulseAlpha,
                            onSetDailyGoal = onSetDailyGoalMinutes
                        )
                    }

                    // --- SECTION 2: LAST 7 DAYS PERFORMANCE CHART ---
                    item {
                        Last7DaysPerformanceCard(
                            historyList = fullHistoryList,
                            dailyGoalMinutes = dailyGoalMinutes
                        )
                    }

                    // --- SECTION 3: ALL-TIME INSIGHTS METRICS ---
                    item {
                        AllTimeSummaryCard(
                            totalSeconds = totalAllTimeSeconds,
                            totalTests = totalAllTimeTests,
                            totalQuestions = totalAllTimeQuestions,
                            overallAccuracy = allTimeAccuracy,
                            activeDaysCount = fullHistoryList.count { it.totalTestTimeSeconds > 0 }
                        )
                    }

                    // --- SECTION 4: HISTORICAL DAILY LOGS LIST ---
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily History Log",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "${fullHistoryList.size} recorded day(s)",
                                fontSize = 11.5.sp,
                                color = colors.muted
                            )
                        }
                    }

                    items(fullHistoryList) { record ->
                        DailyHistoryRecordItem(
                            record = record,
                            isToday = record.dateString == activeTodayEntity.dateString
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Bar: Share Study Report & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val report = buildStudyReportText(
                                activeToday = activeTodayEntity,
                                totalSeconds = totalAllTimeSeconds,
                                totalTests = totalAllTimeTests,
                                totalQuestions = totalAllTimeQuestions,
                                overallAccuracy = allTimeAccuracy
                            )
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Study Analytics Report", report))
                            Toast.makeText(context, "Study analytics report copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_study_report_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.teal)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy Report",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.teal
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dismiss_analytics_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal)
                    ) {
                        Text(
                            text = "Done",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hero Card: Today's Live Study Timer, Target Progress & Daily Stats
 */
@Composable
private fun TodayLiveTimerCard(
    todaySeconds: Long,
    todayEntity: DailyStudyAnalyticsEntity,
    dailyGoalMinutes: Int,
    isCurrentlyActive: Boolean,
    pulseAlpha: Float,
    onSetDailyGoal: (Int) -> Unit
) {
    val colors = ExamTheme.colors
    val goalSeconds = dailyGoalMinutes * 60L
    val progressFraction = if (goalSeconds > 0) {
        (todaySeconds.toFloat() / goalSeconds).coerceIn(0f, 1f)
    } else 0f
    val isGoalAchieved = todaySeconds >= goalSeconds && goalSeconds > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        border = BorderStroke(1.2.dp, if (isGoalAchieved) colors.green else colors.teal.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Live Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCurrentlyActive) colors.green.copy(alpha = pulseAlpha)
                                else colors.teal
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCurrentlyActive) "LIVE TEST SESSION TICKING" else "TODAY'S APP TEST TIMER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentlyActive) colors.green else colors.teal
                    )
                }

                if (isGoalAchieved) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.greenLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = colors.green,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Goal Reached!",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.green
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Big Digital Timer Display
            val hours = todaySeconds / 3600
            val minutes = (todaySeconds % 3600) / 60
            val seconds = todaySeconds % 60
            val formattedTime = if (hours > 0) {
                String.format(Locale.getDefault(), "%02dh : %02dm : %02ds", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02dm : %02ds", minutes, seconds)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = formattedTime,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = colors.inkBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Total student test & practice time today",
                        fontSize = 11.5.sp,
                        color = colors.muted
                    )
                }

                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Goal Progress Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val spentMins = todaySeconds / 60
                    Text(
                        text = "Daily Goal: ${spentMins}m / ${dailyGoalMinutes}m",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold
                    )
                    val percent = (progressFraction * 100).toInt()
                    Text(
                        text = "$percent%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGoalAchieved) colors.green else colors.teal
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isGoalAchieved) colors.green else colors.teal,
                    trackColor = colors.border
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Goal Presets Selector Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Goal:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.width(8.dp))
                val presets = listOf(15, 30, 45, 60, 90)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets) { mins ->
                        val isSelected = dailyGoalMinutes == mins
                        Surface(
                            modifier = Modifier.clickable { onSetDailyGoal(mins) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) colors.tealLight else colors.surface,
                            border = BorderStroke(1.dp, if (isSelected) colors.teal else colors.border)
                        ) {
                            Text(
                                text = "${mins}m",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) colors.teal else colors.inkBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Today's 4 Sub-Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniMetricTile(
                    label = "Tests Done",
                    value = "${todayEntity.testsCompletedCount}",
                    icon = Icons.Default.Quiz,
                    tint = colors.teal,
                    modifier = Modifier.weight(1f)
                )
                MiniMetricTile(
                    label = "Questions",
                    value = "${todayEntity.questionsAttemptedCount}",
                    icon = Icons.Default.HourglassTop,
                    tint = colors.teal,
                    modifier = Modifier.weight(1f)
                )
                MiniMetricTile(
                    label = "Correct",
                    value = "${todayEntity.correctAnswersCount}",
                    icon = Icons.Default.CheckCircle,
                    tint = colors.green,
                    modifier = Modifier.weight(1f)
                )
                MiniMetricTile(
                    label = "Accuracy",
                    value = if (todayEntity.questionsAttemptedCount > 0) {
                        "${todayEntity.accuracyPercentage.toInt()}%"
                    } else "—",
                    icon = Icons.Default.EmojiEvents,
                    tint = if (todayEntity.accuracyPercentage >= 70f) colors.green else colors.teal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Visual 7-Day Performance Bar Chart
 */
@Composable
private fun Last7DaysPerformanceCard(
    historyList: List<DailyStudyAnalyticsEntity>,
    dailyGoalMinutes: Int
) {
    val colors = ExamTheme.colors
    val goalSeconds = dailyGoalMinutes * 60L

    // Build the last 7 calendar days
    val last7Days = remember(historyList) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayLabelFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val cal = Calendar.getInstance()
        val list = mutableListOf<DayChartItem>()

        for (i in 6 downTo 0) {
            val dateCal = Calendar.getInstance()
            dateCal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(dateCal.time)
            val dayLabel = if (i == 0) "Today" else dayLabelFormat.format(dateCal.time)

            val matchedRecord = historyList.find { it.dateString == dateStr }
            val seconds = matchedRecord?.totalTestTimeSeconds ?: 0L

            list.add(
                DayChartItem(
                    dateString = dateStr,
                    dayLabel = dayLabel,
                    seconds = seconds,
                    isToday = i == 0
                )
            )
        }
        list
    }

    val maxSecondsInWeek = remember(last7Days, goalSeconds) {
        val peak = last7Days.maxOfOrNull { it.seconds } ?: 0L
        maxOf(peak, goalSeconds, 1800L) // at least 30m scale
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekly Activity (Last 7 Days)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Text(
                    text = "Goal: ${dailyGoalMinutes}m/day",
                    fontSize = 11.5.sp,
                    color = colors.muted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bars Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                last7Days.forEach { dayItem ->
                    val barFraction = if (maxSecondsInWeek > 0) {
                        (dayItem.seconds.toFloat() / maxSecondsInWeek).coerceIn(0.06f, 1f)
                    } else 0.06f

                    val isMetGoal = dayItem.seconds >= goalSeconds && goalSeconds > 0

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Time label above bar
                        val mins = dayItem.seconds / 60
                        Text(
                            text = if (mins > 0) "${mins}m" else "0m",
                            fontSize = 9.5.sp,
                            fontWeight = if (dayItem.isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (dayItem.isToday) colors.teal else colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Bar Column
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((80 * barFraction).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    when {
                                        dayItem.isToday -> colors.teal
                                        isMetGoal -> colors.green
                                        dayItem.seconds > 0 -> colors.teal.copy(alpha = 0.5f)
                                        else -> colors.border.copy(alpha = 0.6f)
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Day label below bar
                        Text(
                            text = dayItem.dayLabel,
                            fontSize = 11.sp,
                            fontWeight = if (dayItem.isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (dayItem.isToday) colors.teal else colors.inkBold
                        )
                    }
                }
            }
        }
    }
}

private data class DayChartItem(
    val dateString: String,
    val dayLabel: String,
    val seconds: Long,
    val isToday: Boolean
)

/**
 * All-Time Overall Analytics Summary
 */
@Composable
private fun AllTimeSummaryCard(
    totalSeconds: Long,
    totalTests: Int,
    totalQuestions: Int,
    overallAccuracy: Float,
    activeDaysCount: Int
) {
    val colors = ExamTheme.colors
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val formattedLifetime = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Lifetime Study Highlights",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LifetimeStatCard(
                    title = "Total Time",
                    value = formattedLifetime,
                    subtitle = "$activeDaysCount active day(s)",
                    modifier = Modifier.weight(1f)
                )
                LifetimeStatCard(
                    title = "Tests Solved",
                    value = "$totalTests",
                    subtitle = "$totalQuestions questions",
                    modifier = Modifier.weight(1f)
                )
                LifetimeStatCard(
                    title = "Avg Accuracy",
                    value = if (totalQuestions > 0) "${overallAccuracy.toInt()}%" else "—",
                    subtitle = "Historical score",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LifetimeStatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 11.sp, color = colors.muted, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 10.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Individual Historical Record Item
 */
@Composable
private fun DailyHistoryRecordItem(
    record: DailyStudyAnalyticsEntity,
    isToday: Boolean
) {
    val colors = ExamTheme.colors
    val formattedDate = formatHistoryDate(record.dateString, isToday)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) colors.tealLight.copy(alpha = 0.4f) else colors.surface
        ),
        border = BorderStroke(1.dp, if (isToday) colors.teal else colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedDate,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    if (isToday) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.teal
                        ) {
                            Text(
                                text = "TODAY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${record.testsCompletedCount} test(s) • ${record.questionsAttemptedCount} Qs attempted",
                    fontSize = 11.5.sp,
                    color = colors.muted
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surfaceVariant,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = record.formatDuration(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                    }
                }

                if (record.questionsAttemptedCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val acc = record.accuracyPercentage.toInt()
                    Text(
                        text = "$acc% accuracy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (acc >= 70) colors.green else colors.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
            Text(label, fontSize = 9.5.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatHistoryDate(dateStr: String, isToday: Boolean): String {
    if (isToday) return "Today"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = parser.parse(dateStr)
        if (date != null) {
            val formatter = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
            formatter.format(date)
        } else {
            dateStr
        }
    } catch (e: Exception) {
        dateStr
    }
}

private fun buildStudyReportText(
    activeToday: DailyStudyAnalyticsEntity,
    totalSeconds: Long,
    totalTests: Int,
    totalQuestions: Int,
    overallAccuracy: Float
): String {
    val todayHours = activeToday.totalTestTimeSeconds / 3600
    val todayMinutes = (activeToday.totalTestTimeSeconds % 3600) / 60
    val todaySeconds = activeToday.totalTestTimeSeconds % 60
    val todayFormatted = "${todayHours}h ${todayMinutes}m ${todaySeconds}s"

    val totalHours = totalSeconds / 3600
    val totalMins = (totalSeconds % 3600) / 60

    return buildString {
        appendLine("📊 *ExamSet Student Test & Study Report*")
        appendLine("📅 Date: ${activeToday.dateString}")
        appendLine("⏱️ Today's Test Time: $todayFormatted")
        appendLine("📝 Tests Completed Today: ${activeToday.testsCompletedCount}")
        appendLine("⚡ Questions Solved Today: ${activeToday.questionsAttemptedCount}")
        appendLine("🎯 Today's Accuracy: ${activeToday.accuracyPercentage.toInt()}%")
        appendLine("---------------------------------")
        appendLine("📈 All-Time Test Practice Time: ${totalHours}h ${totalMins}m")
        appendLine("📚 All-Time Tests Completed: $totalTests")
        appendLine("💡 All-Time Questions Practiced: $totalQuestions")
        appendLine("🏆 All-Time Overall Accuracy: ${overallAccuracy.toInt()}%")
    }
}
