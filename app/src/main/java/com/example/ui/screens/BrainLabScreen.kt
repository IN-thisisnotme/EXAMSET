package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrainLabActivity
import com.example.model.BrainLabPillar
import com.example.model.BrainLabSettings
import com.example.model.BrainLabStats
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme
import com.example.util.BrainLabQuantScanEngine
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrainLabScreen(
    viewModel: MainViewModel,
    initialActivity: BrainLabActivity? = null,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val stats by viewModel.brainLabStats.collectAsState()
    val settings by viewModel.brainLabSettings.collectAsState()

    var selectedPillar by remember {
        mutableStateOf(initialActivity?.pillar ?: BrainLabPillar.WORKING_MEMORY)
    }
    var activeActivity by remember { mutableStateOf(initialActivity) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // 3–5 minute break budget timer so Brain Lab stays a focused warmup/break rather than procrastination
    var breakBudgetActive by remember { mutableStateOf(false) }
    var budgetRemainingSeconds by remember(settings.preferredBudgetMinutes) {
        mutableIntStateOf(settings.preferredBudgetMinutes * 60)
    }

    LaunchedEffect(breakBudgetActive) {
        while (breakBudgetActive && budgetRemainingSeconds > 0) {
            delay(1000L)
            budgetRemainingSeconds--
        }
    }

    BackHandler {
        when {
            showSettingsDialog -> showSettingsDialog = false
            activeActivity != null -> activeActivity = null
            else -> onDismiss()
        }
    }

    if (showSettingsDialog) {
        BrainLabSettingsDialog(
            settings = settings,
            onUpdateSettings = { transform -> viewModel.updateBrainLabSettings(transform) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("brain_lab_root"),
        color = colors.background
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            containerColor = colors.background,
            topBar = {
                Surface(color = colors.surface, shadowElevation = 1.dp) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (activeActivity != null) activeActivity = null else onDismiss()
                                },
                                modifier = Modifier.size(48.dp).testTag("brain_lab_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (activeActivity != null) "Back to Brain Lab Hub" else "Close Brain Lab",
                                    tint = colors.inkBold
                                )
                            }

                            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                                Text(
                                    text = activeActivity?.title ?: "Brain & Calm Lab",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = activeActivity?.pillar?.title ?: "Evidence-aligned cognitive drills & nervous-system reset",
                                    fontSize = 11.5.sp,
                                    color = colors.teal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (breakBudgetActive) {
                                val m = budgetRemainingSeconds / 60
                                val s = budgetRemainingSeconds % 60
                                val budgetDone = budgetRemainingSeconds <= 0
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (budgetDone) colors.amberLight else colors.tealLight,
                                    border = BorderStroke(1.dp, if (budgetDone) colors.amber else colors.teal),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = if (budgetDone) "Break Done" else String.format("%d:%02d", m, s),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (budgetDone) colors.amber else colors.teal,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier.size(48.dp).testTag("brain_lab_settings_btn")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Brain Lab Settings", tint = colors.inkBold)
                            }
                            if (activeActivity == null) {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(48.dp).testTag("brain_lab_close_btn")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Brain Lab", tint = colors.inkBold)
                                }
                            }
                        }
                        HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val active = activeActivity
                if (active == null) {
                    BrainLabHubContent(
                        stats = stats,
                        settings = settings,
                        selectedPillar = selectedPillar,
                        breakBudgetActive = breakBudgetActive,
                        budgetRemainingSeconds = budgetRemainingSeconds,
                        onSelectPillar = { selectedPillar = it },
                        onToggleBreakBudget = {
                            if (breakBudgetActive) {
                                breakBudgetActive = false
                            } else {
                                budgetRemainingSeconds = settings.preferredBudgetMinutes * 60
                                breakBudgetActive = true
                            }
                        },
                        onLaunchActivity = { activity -> activeActivity = activity }
                    )
                } else {
                    when (active) {
                        BrainLabActivity.N_BACK -> NBackDrillView(
                            stats = stats,
                            settings = settings,
                            onToggleAudio = {
                                viewModel.updateBrainLabSettings { it.copy(audioEnabled = !it.audioEnabled) }
                            },
                            onRecordSession = { tier, accuracy, secs ->
                                viewModel.updateBrainLabStats { cur ->
                                    cur.copy(
                                        bestNBackTier = maxOf(cur.bestNBackTier, tier),
                                        bestNBackAccuracy = maxOf(cur.bestNBackAccuracy, accuracy)
                                    )
                                }
                                viewModel.recordBrainLabActiveSeconds(BrainLabActivity.N_BACK, secs)
                            }
                        )
                        BrainLabActivity.CORSI_SPAN -> CorsiSpanDrillView(
                            stats = stats,
                            onRecordSpan = { span, reverse, secs ->
                                if (span > 0) {
                                    viewModel.updateBrainLabStats { cur ->
                                        if (reverse) {
                                            cur.copy(bestCorsiReverseSpan = maxOf(cur.bestCorsiReverseSpan, span))
                                        } else {
                                            cur.copy(bestCorsiForwardSpan = maxOf(cur.bestCorsiForwardSpan, span))
                                        }
                                    }
                                }
                                viewModel.recordBrainLabActiveSeconds(BrainLabActivity.CORSI_SPAN, secs)
                            }
                        )
                        BrainLabActivity.STROOP_SWITCH -> StroopRuleSwitchView(
                            stats = stats,
                            onRecordRound = { acc, avgRt, secs ->
                                viewModel.updateBrainLabStats { cur ->
                                    val bestRt = if (cur.bestStroopReactionMs <= 0L) avgRt else minOf(cur.bestStroopReactionMs, avgRt)
                                    cur.copy(
                                        bestStroopAccuracy = maxOf(cur.bestStroopAccuracy, acc),
                                        bestStroopReactionMs = bestRt
                                    )
                                }
                                viewModel.recordBrainLabActiveSeconds(BrainLabActivity.STROOP_SWITCH, secs)
                            }
                        )
                        BrainLabActivity.SYLLOGISM -> SyllogismArenaView(
                            stats = stats,
                            onRecordAnswer = { correct, secs ->
                                viewModel.updateBrainLabStats { cur ->
                                    val nextStreak = if (correct) cur.reasoningStreak + 1 else 0
                                    cur.copy(
                                        syllogismSolved = cur.syllogismSolved + 1,
                                        syllogismCorrect = cur.syllogismCorrect + if (correct) 1 else 0,
                                        reasoningStreak = nextStreak,
                                        bestReasoningStreak = maxOf(cur.bestReasoningStreak, nextStreak)
                                    )
                                }
                                viewModel.recordBrainLabActiveSeconds(BrainLabActivity.SYLLOGISM, secs)
                            }
                        )
                        BrainLabActivity.CONSTRAINT_SEATING -> ConstraintSeatingView(
                            stats = stats,
                            onRecordSolved = { correct, secs ->
                                viewModel.updateBrainLabStats { cur ->
                                    val nextStreak = if (correct) cur.reasoningStreak + 1 else 0
                                    cur.copy(
                                        constraintSolved = cur.constraintSolved + if (correct) 1 else 0,
                                        reasoningStreak = nextStreak,
                                        bestReasoningStreak = maxOf(cur.bestReasoningStreak, nextStreak)
                                    )
                                }
                                viewModel.recordBrainLabActiveSeconds(BrainLabActivity.CONSTRAINT_SEATING, secs)
                            }
                        )
                        BrainLabActivity.ARGUMENT_CRITIQUE -> ArgumentCritiqueView(
                            stats = stats,
                            onRecordSolved = { correct, secs ->
                                viewModel.updateBrainLabStats { cur ->
                                    val nextStreak = if (correct) cur.reasoningStreak + 1 else 0
                                    cur.copy(
                                        argumentSolved = cur.argumentSolved + if (correct) 1 else 0,
                                        reasoningStreak = nextStreak,
                                        bestReasoningStreak = maxOf(cur.bestReasoningStreak, nextStreak)
                                    )
                                }
                                viewModel.recordBrainLabActiveSeconds(BrainLabActivity.ARGUMENT_CRITIQUE, secs)
                            }
                        )
                        BrainLabActivity.DATA_SUFFICIENCY -> DataSufficiencyView(
                            stats = stats,
                            onRecordSolved = { correct, secs ->
                                viewModel.updateBrainLabStats { cur ->
                                    val nextStreak = if (correct) cur.reasoningStreak + 1 else 0
                                    cur.copy(
                                        dataSufficiencySolved = cur.dataSufficiencySolved + if (correct) 1 else 0,
                                        reasoningStreak = nextStreak,
                                        bestReasoningStreak = maxOf(cur.bestReasoningStreak, nextStreak)
                                    )
                                }
                                viewModel.recordBrainLabActiveSeconds(BrainLabActivity.DATA_SUFFICIENCY, secs)
                            }
                        )
                        BrainLabActivity.SCHULTE_TRAIL -> SchulteTrailView(
                            stats = stats,
                            onRecordCompletion = { mode, elapsedMs, secs ->
                                viewModel.updateBrainLabStats { cur ->
                                    when (mode) {
                                        BrainLabQuantScanEngine.SchulteMode.SCHULTE_5X5 -> cur.copy(
                                            bestSchulte5x5Ms = if (cur.bestSchulte5x5Ms <= 0L) elapsedMs else minOf(cur.bestSchulte5x5Ms, elapsedMs)
                                        )
                                        BrainLabQuantScanEngine.SchulteMode.SCHULTE_6X6 -> cur.copy(
                                            bestSchulte6x6Ms = if (cur.bestSchulte6x6Ms <= 0L) elapsedMs else minOf(cur.bestSchulte6x6Ms, elapsedMs)
                                        )
                                        BrainLabQuantScanEngine.SchulteMode.TRAIL_5X5 -> cur.copy(
                                            bestTrail5x5Ms = if (cur.bestTrail5x5Ms <= 0L) elapsedMs else minOf(cur.bestTrail5x5Ms, elapsedMs)
                                        )
                                    }
                                }
                                viewModel.recordBrainLabActiveSeconds(BrainLabActivity.SCHULTE_TRAIL, secs)
                            }
                        )
                        BrainLabActivity.MENTAL_QUANT -> MentalQuantBlitzView(
                            stats = stats,
                            onRecordBlitz = { score, secs ->
                                viewModel.updateBrainLabStats { cur ->
                                    cur.copy(bestQuantBlitzScore = maxOf(cur.bestQuantBlitzScore, score))
                                }
                                viewModel.recordBrainLabActiveSeconds(BrainLabActivity.MENTAL_QUANT, secs)
                            }
                        )
                        BrainLabActivity.GUIDED_BREATHING -> GuidedBreathingPacerView(
                            stats = stats,
                            settings = settings,
                            onToggleHaptics = {
                                viewModel.updateBrainLabSettings { it.copy(hapticsEnabled = !it.hapticsEnabled) }
                            },
                            onRecordCalmSession = { secs ->
                                viewModel.updateBrainLabStats { cur ->
                                    cur.copy(calmSessionsCompleted = cur.calmSessionsCompleted + 1)
                                }
                                viewModel.recordBrainLabActiveSeconds(BrainLabActivity.GUIDED_BREATHING, secs)
                            }
                        )
                        BrainLabActivity.VISUAL_SOFT_RESET -> VisualSoftResetView(
                            stats = stats,
                            onRecordCalmSession = { secs ->
                                viewModel.updateBrainLabStats { cur ->
                                    cur.copy(calmSessionsCompleted = cur.calmSessionsCompleted + 1)
                                }
                                viewModel.recordBrainLabActiveSeconds(BrainLabActivity.VISUAL_SOFT_RESET, secs)
                            }
                        )
                        BrainLabActivity.ZEN_LOGIC_GRID -> ZenLogicGridView(
                            stats = stats,
                            onRecordZenCompleted = { secs ->
                                viewModel.updateBrainLabStats { cur ->
                                    cur.copy(zenGridsCompleted = cur.zenGridsCompleted + 1)
                                }
                                viewModel.recordBrainLabActiveSeconds(BrainLabActivity.ZEN_LOGIC_GRID, secs)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrainLabHubContent(
    stats: BrainLabStats,
    settings: BrainLabSettings,
    selectedPillar: BrainLabPillar,
    breakBudgetActive: Boolean,
    budgetRemainingSeconds: Int,
    onSelectPillar: (BrainLabPillar) -> Unit,
    onToggleBreakBudget: () -> Unit,
    onLaunchActivity: (BrainLabActivity) -> Unit
) {
    val colors = ExamTheme.colors
    val activities = remember(selectedPillar) {
        BrainLabActivity.entries.filter { it.pillar == selectedPillar }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick 3–5 Min Break & Personal Bests Banner
        item(key = "header_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quick ${settings.preferredBudgetMinutes}-Min Warmup / Break",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Built-in time budget keeps brain drills purposeful so they never replace core study.",
                                fontSize = 12.sp,
                                color = colors.muted
                            )
                        }
                        Button(
                            onClick = onToggleBreakBudget,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (breakBudgetActive) colors.navyBanner else colors.teal,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("brain_lab_budget_btn")
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (breakBudgetActive) {
                                    "${budgetRemainingSeconds / 60}:${String.format("%02d", budgetRemainingSeconds % 60)}"
                                } else {
                                    "Start ${settings.preferredBudgetMinutes}m"
                                },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatBadge("N-Back: ${stats.bestNBackTier}-Back")
                        StatBadge("Reverse Corsi: ${stats.bestCorsiReverseSpan}")
                        StatBadge("Logic Streak: ${stats.bestReasoningStreak}")
                        StatBadge("Quant Best: ${stats.bestQuantBlitzScore}")
                        StatBadge("Calm Resets: ${stats.calmSessionsCompleted + stats.zenGridsCompleted}")
                    }
                }
            }
        }

        // 4-Pillar Selector
        item(key = "pillar_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BrainLabPillar.entries.forEach { pillar ->
                    val isSelected = selectedPillar == pillar
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPillar(pillar) },
                        label = { Text(pillar.title, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = pillarIcon(pillar),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.tealLight,
                            selectedLabelColor = colors.teal,
                            selectedLeadingIconColor = colors.teal
                        ),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("brain_pillar_${pillar.name}")
                    )
                }
            }
        }

        item(key = "pillar_banner") {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.tealLight.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${selectedPillar.badge}  •  ${selectedPillar.title}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.teal
                    )
                    Text(
                        text = selectedPillar.subtitle,
                        fontSize = 12.5.sp,
                        color = colors.inkBold
                    )
                }
            }
        }

        items(activities, key = { it.name }) { activity ->
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLaunchActivity(activity) }
                    .testTag("brain_activity_${activity.name}")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activity.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(shape = RoundedCornerShape(8.dp), color = colors.surfaceVariant) {
                            Text(
                                text = "${activity.defaultBudgetMinutes} min",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = activity.shortSummary,
                        fontSize = 12.5.sp,
                        color = colors.ink
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Exam transfer: ${activity.examTransfer}",
                            fontSize = 11.5.sp,
                            color = colors.muted,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { onLaunchActivity(activity) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Launch", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(text: String) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(0.8.dp, colors.border)
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.inkBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun pillarIcon(pillar: BrainLabPillar): ImageVector = when (pillar) {
    BrainLabPillar.WORKING_MEMORY -> Icons.Default.Psychology
    BrainLabPillar.CRITICAL_REASONING -> Icons.Default.Bolt
    BrainLabPillar.VISUAL_QUANT -> Icons.Default.Speed
    BrainLabPillar.CALM_RESET -> Icons.Default.SelfImprovement
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrainLabSettingsDialog(
    settings: BrainLabSettings,
    onUpdateSettings: ((BrainLabSettings) -> BrainLabSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Brain & Calm Lab Preferences", fontWeight = FontWeight.Bold, color = colors.inkBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("N-Back Audio Cues", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                        Text("Muted by default for quiet study halls.", fontSize = 11.5.sp, color = colors.muted)
                    }
                    Switch(
                        checked = settings.audioEnabled,
                        onCheckedChange = { enabled -> onUpdateSettings { it.copy(audioEnabled = enabled) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.teal)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Breathing Haptic Pulses", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                        Text("Gentle vibration cue on Inhale / Hold / Exhale.", fontSize = 11.5.sp, color = colors.muted)
                    }
                    Switch(
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { enabled -> onUpdateSettings { it.copy(hapticsEnabled = enabled) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.teal)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Count Calm Resets in Study Timer", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                        Text("Cognitive drills always log; toggle to include Calm/Zen too.", fontSize = 11.5.sp, color = colors.muted)
                    }
                    Switch(
                        checked = settings.countCalmInDailyStudyTime,
                        onCheckedChange = { enabled -> onUpdateSettings { it.copy(countCalmInDailyStudyTime = enabled) } },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.teal)
                    )
                }

                Text("Quick Break Budget", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 4, 5).forEach { mins ->
                        FilterChip(
                            selected = settings.preferredBudgetMinutes == mins,
                            onClick = { onUpdateSettings { it.copy(preferredBudgetMinutes = mins) } },
                            label = { Text("$mins min") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", fontWeight = FontWeight.Bold, color = colors.teal)
            }
        }
    )
}
