package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrainLabSettings
import com.example.model.BrainLabStats
import com.example.model.BreathingProtocol
import com.example.ui.theme.ExamTheme
import com.example.util.BrainLabCalmEngine
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidedBreathingPacerView(
    stats: BrainLabStats,
    settings: BrainLabSettings,
    onToggleHaptics: () -> Unit,
    onRecordCalmSession: (activeSeconds: Long) -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current

    var protocol by remember { mutableStateOf(BreathingProtocol.CYCLIC_SIGH) }
    var isRunning by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var lastPhaseIndex by remember { mutableIntStateOf(-1) }
    var sessionRecorded by remember { mutableStateOf(false) }

    val sample = remember(protocol, elapsedMs) {
        BrainLabCalmEngine.sampleBreathingState(protocol, elapsedMs)
    }

    LaunchedEffect(isRunning, protocol) {
        if (!isRunning) return@LaunchedEffect
        var prevFrame = withFrameMillis { it }
        while (isRunning) {
            val now = withFrameMillis { it }
            elapsedMs += (now - prevFrame).coerceIn(0L, 100L)
            prevFrame = now
        }
    }

    LaunchedEffect(isRunning, sample.phaseIndex) {
        if (isRunning && sample.phaseIndex != lastPhaseIndex) {
            lastPhaseIndex = sample.phaseIndex
            if (settings.hapticsEnabled) {
                triggerGentleHaptic(context)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Guided Breathing Pacer",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Text(
                            text = "Calm sessions completed: ${stats.calmSessionsCompleted}",
                            fontSize = 12.sp,
                            color = colors.teal
                        )
                    }
                    OutlinedButton(
                        onClick = onToggleHaptics,
                        border = BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("breathing_haptic_toggle")
                    ) {
                        Icon(
                            Icons.Default.Vibration,
                            contentDescription = "Toggle Haptic Pulse",
                            tint = if (settings.hapticsEnabled) colors.teal else colors.muted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (settings.hapticsEnabled) "Haptics On" else "Haptics Off", fontSize = 12.sp, color = colors.inkBold)
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BreathingProtocol.entries.forEach { p ->
                        FilterChip(
                            selected = protocol == p,
                            onClick = {
                                protocol = p
                                elapsedMs = 0L
                                lastPhaseIndex = -1
                                sessionRecorded = false
                            },
                            label = { Text("${p.title} (${p.ratioLabel})", softWrap = true) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealLight, selectedLabelColor = colors.teal)
                        )
                    }
                }
                Text(
                    text = protocol.description,
                    fontSize = 12.sp,
                    color = colors.muted
                )
            }
        }

        // Smooth 60fps Canvas Pacer Ring with diameter-aware center text scaling
        val breathingFontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            val ringDiameterDp = minOf(maxWidth.value, maxHeight.value)
            val cueSp = minOf(22f, (ringDiameterDp * 0.085f) / breathingFontScale).coerceAtLeast(13f).sp
            val timerSp = minOf(28f, (ringDiameterDp * 0.105f) / breathingFontScale).coerceAtLeast(15f).sp
            val cycleSp = minOf(12f, (ringDiameterDp * 0.055f) / breathingFontScale).coerceAtLeast(9.5f).sp
            val ringAccent = colors.teal
            val ringSoft = colors.tealLight
            val borderCol = colors.border
            Canvas(modifier = Modifier.fillMaxSize()) {
                val minDim = minOf(size.width, size.height)
                val maxR = minDim * 0.45f
                val outerStroke = minDim * 0.03f
                drawCircle(
                    color = borderCol.copy(alpha = 0.35f),
                    radius = maxR,
                    style = Stroke(width = outerStroke, cap = StrokeCap.Round)
                )
                val activeRadius = maxR * sample.ringScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ringAccent.copy(alpha = 0.28f), ringSoft.copy(alpha = 0.65f))
                    ),
                    radius = activeRadius
                )
                drawCircle(
                    color = ringAccent,
                    radius = activeRadius,
                    style = Stroke(width = outerStroke * 1.4f, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isRunning) sample.cue.label else "Ready",
                    fontSize = cueSp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold,
                    textAlign = TextAlign.Center
                )
                if (isRunning) {
                    Text(
                        text = "${sample.secondsRemainingInPhase}s",
                        fontSize = timerSp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.teal,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Cycle ${sample.cycleCount + 1}",
                        fontSize = cycleSp,
                        color = colors.muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Text(
            text = if (isRunning) sample.cue.subCue else "90 seconds is enough to calm pre-mock exam tension.",
            fontSize = 13.sp,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (isRunning) {
                        isRunning = false
                        val secs = elapsedMs / 1000L
                        if (secs >= 10L && !sessionRecorded) {
                            sessionRecorded = true
                            onRecordCalmSession(secs)
                        }
                    } else {
                        sessionRecorded = false
                        isRunning = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 52.dp).testTag("breathing_toggle_btn")
            ) {
                Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isRunning) "Pause & Save" else "Begin Breathing", fontWeight = FontWeight.Bold)
            }

            if (elapsedMs > 0L) {
                OutlinedButton(
                    onClick = {
                        isRunning = false
                        elapsedMs = 0L
                        lastPhaseIndex = -1
                        sessionRecorded = false
                    },
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.inkBold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset", color = colors.inkBold)
                }
            }
        }
    }
}

@Composable
fun VisualSoftResetView(
    stats: BrainLabStats,
    onRecordCalmSession: (activeSeconds: Long) -> Unit
) {
    val colors = ExamTheme.colors
    val steps = BrainLabCalmEngine.SOFT_RESET_STEPS
    var isRunning by remember { mutableStateOf(false) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var stepSecondsLeft by remember { mutableIntStateOf(steps.first().durationSeconds) }
    var completed by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, stepIndex) {
        if (!isRunning) return@LaunchedEffect
        while (isRunning && stepSecondsLeft > 0) {
            delay(1000L)
            stepSecondsLeft--
        }
        if (isRunning && stepSecondsLeft <= 0) {
            if (stepIndex < steps.lastIndex) {
                stepIndex++
                stepSecondsLeft = steps[stepIndex].durationSeconds
            } else {
                isRunning = false
                completed = true
                onRecordCalmSession(60L)
            }
        }
    }

    val currentStep = steps[stepIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (completed) Icons.Default.CheckCircle else Icons.Default.SelfImprovement,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = if (completed) "60-Second Reset Complete" else currentStep.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (completed) {
                        "Your eye muscles and visual focus are refreshed. Calm resets completed: ${stats.calmSessionsCompleted}"
                    } else {
                        currentStep.prompt
                    },
                    fontSize = 13.5.sp,
                    color = colors.ink,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
                if (!completed) {
                    Text(
                        text = "${stepSecondsLeft}s",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.teal
                    )
                    LinearProgressIndicator(
                        progress = {
                            ((stepIndex * 20 + (20 - stepSecondsLeft)).toFloat() / 60f).coerceIn(0f, 1f)
                        },
                        color = colors.teal,
                        trackColor = colors.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                    Text(
                        text = "Phase ${stepIndex + 1} of ${steps.size}",
                        fontSize = 11.5.sp,
                        color = colors.muted
                    )
                }
            }
        }

        Button(
            onClick = {
                if (completed || !isRunning) {
                    completed = false
                    stepIndex = 0
                    stepSecondsLeft = steps.first().durationSeconds
                    isRunning = true
                } else {
                    isRunning = false
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 52.dp).testTag("soft_reset_btn")
        ) {
            Text(
                text = when {
                    completed -> "Run Another 60s Reset"
                    isRunning -> "Pause Reset"
                    else -> "Start 60-Second Soft-Reset"
                },
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZenLogicGridView(
    stats: BrainLabStats,
    onRecordZenCompleted: (activeSeconds: Long) -> Unit
) {
    val colors = ExamTheme.colors
    var gridSize by remember { mutableIntStateOf(4) }
    var puzzle by remember(gridSize) { mutableStateOf(BrainLabCalmEngine.generateZenLogicPuzzle(size = gridSize)) }
    val board = remember(puzzle) { mutableStateListOf<Int>().apply { addAll(puzzle.givens.toList()) } }
    var selectedCell by remember(puzzle) { mutableIntStateOf(-1) }
    var startedAtMs by remember(puzzle) { mutableLongStateOf(System.currentTimeMillis()) }
    var isSolved by remember(puzzle) { mutableStateOf(false) }
    var gentleHintMessage by remember(puzzle) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Untimed Zen Logic Grid",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Completed: ${stats.zenGridsCompleted}", fontSize = 12.sp, color = colors.teal)
                }
                Text(
                    text = "Single-solution grid with no clock, no lives, and no red penalties.",
                    fontSize = 12.sp,
                    color = colors.muted
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = gridSize == 4,
                        onClick = { gridSize = 4 },
                        label = { Text("4×4 Calm Grid") },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealLight, selectedLabelColor = colors.teal)
                    )
                    FilterChip(
                        selected = gridSize == 6,
                        onClick = { gridSize = 6 },
                        label = { Text("6×6 Calm Grid") },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealLight, selectedLabelColor = colors.teal)
                    )
                }
            }
        }

        // Grid Board with cell-width-aware digit scaling
        val n = puzzle.size
        val zenFontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(colors.surface, RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            val cellSideDp = (maxWidth.value - (n - 1) * 4f - 8f) / n
            val preferredSp = if (n == 6) 16f else 20f
            val cellDigitSp = minOf(preferredSp, (cellSideDp * 0.45f) / zenFontScale).coerceAtLeast(11f).sp
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0 until n) {
                    val extraTopGap = if (r > 0 && r % puzzle.boxRows == 0) 4.dp else 0.dp
                    if (extraTopGap > 0.dp) Spacer(modifier = Modifier.height(extraTopGap))
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 0 until n) {
                            if (c > 0 && c % puzzle.boxCols == 0) Spacer(modifier = Modifier.width(4.dp))
                            val idx = r * n + c
                            val isGiven = puzzle.givens[idx] != 0
                            val isSelected = selectedCell == idx
                            val value = board[idx]
                            val bg = when {
                                isSolved -> colors.tealLight
                                isSelected -> colors.tealLight
                                isGiven -> colors.surfaceVariant
                                else -> colors.surface
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = bg,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) colors.teal else colors.border
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isGiven && !isSolved) {
                                        selectedCell = idx
                                        gentleHintMessage = null
                                    }
                                    .testTag("zen_cell_$idx")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (value != 0) {
                                        Text(
                                            text = value.toString(),
                                            fontSize = cellDigitSp,
                                            fontWeight = if (isGiven) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (isGiven) colors.inkBold else colors.teal,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Calm Number Pad
        if (!isSolved) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)
            ) {
                (1..n).forEach { num ->
                    OutlinedButton(
                        onClick = {
                            if (selectedCell in board.indices && puzzle.givens[selectedCell] == 0) {
                                board[selectedCell] = num
                                gentleHintMessage = null
                                if (BrainLabCalmEngine.isZenGridSolvedCorrectly(
                                        board = board.toIntArray(),
                                        size = puzzle.size,
                                        boxRows = puzzle.boxRows,
                                        boxCols = puzzle.boxCols
                                    )
                                ) {
                                    isSolved = true
                                    val secs = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceIn(1L, 1800L)
                                    onRecordZenCompleted(secs)
                                }
                            }
                        },
                        border = BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).testTag("zen_num_$num")
                    ) {
                        Text(num.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (selectedCell in board.indices && puzzle.givens[selectedCell] == 0) {
                            board[selectedCell] = 0
                        }
                    },
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Erase", fontSize = 13.sp, color = colors.muted)
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.tealLight),
                border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.teal)
                    Text(
                        text = "Zen Grid completed smoothly. Take a quiet breath before returning.",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                }
            }
        }

        gentleHintMessage?.let {
            Text(it, fontSize = 12.5.sp, color = colors.teal, textAlign = TextAlign.Center)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isSolved) {
                OutlinedButton(
                    onClick = {
                        val targetIdx = if (selectedCell in board.indices && puzzle.givens[selectedCell] == 0 && board[selectedCell] != puzzle.solution[selectedCell]) {
                            selectedCell
                        } else {
                            board.indices.firstOrNull { board[it] != puzzle.solution[it] } ?: -1
                        }
                        if (targetIdx >= 0) {
                            board[targetIdx] = puzzle.solution[targetIdx]
                            selectedCell = targetIdx
                            gentleHintMessage = "Filled one gentle clue in Row ${targetIdx / n + 1}, Col ${targetIdx % n + 1}."
                            if (BrainLabCalmEngine.isZenGridSolvedCorrectly(board.toIntArray(), puzzle.size, puzzle.boxRows, puzzle.boxCols)) {
                                isSolved = true
                                val secs = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceIn(1L, 1800L)
                                onRecordZenCompleted(secs)
                            }
                        }
                    },
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = colors.teal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gentle Hint", color = colors.inkBold)
                }
            }

            Button(
                onClick = { puzzle = BrainLabCalmEngine.generateZenLogicPuzzle(size = gridSize) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("zen_new_grid_btn")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Zen Grid", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun triggerGentleHaptic(context: Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(32L, 70)
            )
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createOneShot(32L, 70))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(32L)
            }
        }
    }
}
