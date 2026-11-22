package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrainLabStats
import com.example.ui.theme.ExamTheme
import com.example.util.BrainLabQuantScanEngine
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchulteTrailView(
    stats: BrainLabStats,
    onRecordCompletion: (mode: BrainLabQuantScanEngine.SchulteMode, elapsedMs: Long, activeSeconds: Long) -> Unit
) {
    val colors = ExamTheme.colors
    var mode by remember { mutableStateOf(BrainLabQuantScanEngine.SchulteMode.SCHULTE_5X5) }
    var board by remember(mode) { mutableStateOf(BrainLabQuantScanEngine.generateSchulteBoard(mode)) }
    var nextStepIdx by remember(board) { mutableIntStateOf(0) }
    var isRunning by remember(board) { mutableStateOf(false) }
    var isCompleted by remember(board) { mutableStateOf(false) }
    var startMs by remember(board) { mutableLongStateOf(0L) }
    var elapsedMs by remember(board) { mutableLongStateOf(0L) }
    var mistakes by remember(board) { mutableIntStateOf(0) }

    LaunchedEffect(isRunning, isCompleted) {
        while (isRunning && !isCompleted) {
            elapsedMs = System.currentTimeMillis() - startMs
            delay(50L)
        }
    }

    val bestLabel = when (mode) {
        BrainLabQuantScanEngine.SchulteMode.SCHULTE_5X5 -> formatMs(stats.bestSchulte5x5Ms)
        BrainLabQuantScanEngine.SchulteMode.SCHULTE_6X6 -> formatMs(stats.bestSchulte6x6Ms)
        BrainLabQuantScanEngine.SchulteMode.TRAIL_5X5 -> formatMs(stats.bestTrail5x5Ms)
    }

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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Schulte & Trail Grid", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                    Text("Best: $bestLabel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.teal)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrainLabQuantScanEngine.SchulteMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.label, softWrap = true) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealLight, selectedLabelColor = colors.teal)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val nextTarget = board.expectedOrder.getOrNull(nextStepIdx) ?: "Done!"
                    Text(
                        text = if (isCompleted) "Completed cleanly!" else "Find Next: $nextTarget",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isCompleted) colors.green else colors.teal
                    )
                    Text(
                        text = "${formatMs(elapsedMs)}  •  Slips: $mistakes",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold
                    )
                }
            }
        }

        // Center gaze dot hint + Grid
        val n = board.gridSize
        val centerIndex = (n * n) / 2
        Box(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(colors.surface, RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (r in 0 until n) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (c in 0 until n) {
                            val idx = r * n + c
                            val label = board.cells[idx]
                            val orderPos = board.expectedOrder.indexOf(label)
                            val alreadyTapped = orderPos in 0 until nextStepIdx
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (alreadyTapped) colors.tealLight else colors.surfaceVariant,
                                border = BorderStroke(
                                    width = if (idx == centerIndex) 1.5.dp else 1.dp,
                                    color = if (idx == centerIndex) colors.teal else colors.border
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isCompleted) {
                                        if (!isRunning) {
                                            isRunning = true
                                            startMs = System.currentTimeMillis()
                                        }
                                        val expected = board.expectedOrder.getOrNull(nextStepIdx)
                                        if (label == expected) {
                                            val next = nextStepIdx + 1
                                            nextStepIdx = next
                                            if (next >= board.expectedOrder.size) {
                                                elapsedMs = System.currentTimeMillis() - startMs
                                                isRunning = false
                                                isCompleted = true
                                                onRecordCompletion(mode, elapsedMs, (elapsedMs / 1000L).coerceAtLeast(1L))
                                            }
                                        } else if (!alreadyTapped) {
                                            mistakes++
                                        }
                                    }
                                    .testTag("schulte_cell_$idx")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (idx == centerIndex && !alreadyTapped) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(colors.teal.copy(alpha = 0.45f), CircleShape)
                                                .align(Alignment.TopEnd)
                                        )
                                    }
                                    Text(
                                        text = label,
                                        fontSize = if (n == 6) 14.sp else 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (alreadyTapped) colors.teal else colors.inkBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = { board = BrainLabQuantScanEngine.generateSchulteBoard(mode) },
            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 48.dp).testTag("schulte_reset_btn")
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Shuffle New Grid", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MentalQuantBlitzView(
    stats: BrainLabStats,
    onRecordBlitz: (score: Int, activeSeconds: Long) -> Unit
) {
    val colors = ExamTheme.colors
    val sprintSeconds = 60
    var isRunning by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableIntStateOf(sprintSeconds) }
    var question by remember { mutableStateOf(BrainLabQuantScanEngine.generateQuantQuestion(1)) }
    var qCount by remember { mutableIntStateOf(1) }
    var score by remember { mutableIntStateOf(0) }
    var attempted by remember { mutableIntStateOf(0) }
    var lastNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        while (isRunning && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
        if (isRunning && remainingSeconds <= 0) {
            isRunning = false
            onRecordBlitz(score, sprintSeconds.toLong())
        }
    }

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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("60s Mental Quant Blitz", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                    Text("Best Score: ${stats.bestQuantBlitzScore}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.teal)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Score: $score / $attempted", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                    Text("${remainingSeconds}s left", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                }
                LinearProgressIndicator(
                    progress = { remainingSeconds.toFloat() / sprintSeconds },
                    color = colors.teal,
                    trackColor = colors.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
            }
        }

        if (isRunning) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight) {
                        Text(
                            text = question.type.badge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.teal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = question.prompt,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                }
            }

            lastNote?.let {
                Text(it, fontSize = 12.sp, color = colors.muted, textAlign = TextAlign.Center)
            }

            question.options.forEachIndexed { idx, opt ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .heightIn(min = 48.dp)
                        .clickable {
                            attempted++
                            val ok = idx == question.correctIndex
                            if (ok) {
                                score++
                                lastNote = "✓ Correct — ${question.explanation}"
                            } else {
                                lastNote = "✗ ${question.options[question.correctIndex]} — ${question.explanation}"
                            }
                            qCount++
                            question = BrainLabQuantScanEngine.generateQuantQuestion(qCount)
                        }
                        .testTag("quant_opt_$idx")
                ) {
                    Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.CenterStart) {
                        Text(opt, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                    }
                }
            }
        } else {
            if (attempted > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.tealLight),
                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Blitz Complete: $score / $attempted correct", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                        lastNote?.let { Text(it, fontSize = 12.sp, color = colors.ink) }
                    }
                }
            }

            Button(
                onClick = {
                    score = 0
                    attempted = 0
                    qCount = 1
                    remainingSeconds = sprintSeconds
                    lastNote = null
                    question = BrainLabQuantScanEngine.generateQuantQuestion(1)
                    isRunning = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 52.dp).testTag("quant_start_btn")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start 60s Mental Quant Blitz", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "--"
    val secs = ms / 1000.0
    return String.format("%.2fs", secs)
}
