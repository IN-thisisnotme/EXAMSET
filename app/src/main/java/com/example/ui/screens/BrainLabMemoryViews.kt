package com.example.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrainLabSettings
import com.example.model.BrainLabStats
import com.example.model.NBackMode
import com.example.ui.theme.ExamTheme
import com.example.util.BrainLabMemoryEngine
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NBackDrillView(
    stats: BrainLabStats,
    settings: BrainLabSettings,
    onToggleAudio: () -> Unit,
    onRecordSession: (tier: Int, accuracy: Int, activeSeconds: Long) -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current

    var currentN by remember { mutableIntStateOf(stats.bestNBackTier.coerceIn(1, 4)) }
    var mode by remember { mutableStateOf(NBackMode.DUAL) }
    var isRunning by remember { mutableStateOf(false) }
    var trials by remember { mutableStateOf<List<BrainLabMemoryEngine.NBackTrial>>(emptyList()) }
    var currentStep by remember { mutableIntStateOf(-1) }
    var showStimulus by remember { mutableStateOf(false) }
    val posTaps = remember { mutableStateListOf<Int>() }
    val letterTaps = remember { mutableStateListOf<Int>() }
    var evaluation by remember { mutableStateOf<BrainLabMemoryEngine.NBackEvaluation?>(null) }
    var sessionStartMs by remember { mutableLongStateOf(0L) }

    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.US
                ttsReady = true
            }
        }
        instance
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                tts.stop()
                tts.shutdown()
            }
        }
    }

    LaunchedEffect(isRunning, trials) {
        if (!isRunning || trials.isEmpty()) return@LaunchedEffect
        sessionStartMs = System.currentTimeMillis()
        for (i in trials.indices) {
            currentStep = i
            showStimulus = true
            if (settings.audioEnabled && ttsReady && mode != NBackMode.POSITION_ONLY) {
                runCatching {
                    tts.speak(trials[i].letter.toString(), TextToSpeech.QUEUE_FLUSH, null, "nb_$i")
                }
            }
            delay(1300L)
            showStimulus = false
            delay(1100L)
        }
        isRunning = false
        val eval = BrainLabMemoryEngine.evaluateNBackSession(
            currentN = currentN,
            mode = mode,
            trials = trials,
            userPosTaps = posTaps.toSet(),
            userLetterTaps = letterTaps.toSet()
        )
        evaluation = eval
        val elapsedSec = ((System.currentTimeMillis() - sessionStartMs) / 1000L).coerceAtLeast(1L)
        onRecordSession(eval.nextRecommendedN, eval.accuracyPercent, elapsedSec)
        currentN = eval.nextRecommendedN
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
                            text = "Adaptive ${currentN}-Back",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Text(
                            text = "Best: ${stats.bestNBackTier}-Back (${stats.bestNBackAccuracy}%)",
                            fontSize = 12.sp,
                            color = colors.teal
                        )
                    }
                    OutlinedButton(
                        onClick = onToggleAudio,
                        border = BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("nback_audio_toggle")
                    ) {
                        Icon(
                            imageVector = if (settings.audioEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Toggle N-Back Letter Audio",
                            tint = if (settings.audioEnabled) colors.teal else colors.muted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (settings.audioEnabled) "Audio On" else "Muted",
                            color = colors.inkBold,
                            fontSize = 12.sp
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (1..4).forEach { tier ->
                        FilterChip(
                            selected = currentN == tier,
                            onClick = { if (!isRunning) currentN = tier },
                            label = { Text("${tier}-Back") },
                            enabled = !isRunning,
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.tealLight,
                                selectedLabelColor = colors.teal
                            )
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NBackMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { if (!isRunning) mode = m },
                            label = { Text(m.label, softWrap = true) },
                            enabled = !isRunning,
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.tealLight,
                                selectedLabelColor = colors.teal
                            )
                        )
                    }
                }
            }
        }

        if (isRunning && currentStep in trials.indices) {
            val activeTrial = trials[currentStep]
            LinearProgressIndicator(
                progress = { (currentStep + 1).toFloat() / trials.size.coerceAtLeast(1) },
                color = colors.teal,
                trackColor = colors.surfaceVariant,
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).height(6.dp)
            )
            Text(
                text = "Step ${currentStep + 1} / ${trials.size}" +
                    if (currentStep < currentN) "  •  Observe baseline" else "  •  Compare to $currentN steps ago",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.muted
            )

            // 3x3 Visual Grid with cell-width-aware font sizing
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            BoxWithConstraints(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .padding(10.dp)
            ) {
                val tileSideDp = (maxWidth.value - 16f) / 3f
                val letterSp = minOf(26f, (tileSideDp * 0.45f) / fontScale).coerceAtLeast(14f).sp
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (r in 0..2) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (c in 0..2) {
                                val cellId = r * 3 + c
                                val isActive = showStimulus &&
                                    mode != NBackMode.LETTER_ONLY &&
                                    activeTrial.cellIndex == cellId
                                val isCenterLetterOnly = showStimulus &&
                                    mode == NBackMode.LETTER_ONLY &&
                                    cellId == 4
                                val tileColor by animateColorAsState(
                                    targetValue = if (isActive || isCenterLetterOnly) colors.teal else colors.surfaceVariant,
                                    label = "nbackTile"
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = tileColor,
                                    border = BorderStroke(1.dp, colors.border),
                                    modifier = Modifier.weight(1f).fillMaxSize()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if ((isActive || isCenterLetterOnly) && mode != NBackMode.POSITION_ONLY) {
                                            Text(
                                                text = activeTrial.letter.toString(),
                                                fontSize = letterSp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
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

            if (mode != NBackMode.DUAL && mode != NBackMode.LETTER_ONLY) {
                Text(
                    text = "Position-Only Mode Active",
                    fontSize = 12.sp,
                    color = colors.muted
                )
            } else {
                Text(
                    text = if (showStimulus) "Letter: ${activeTrial.letter}" else " ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
            }

            val canTapMatch = currentStep >= currentN
            val posTapped = currentStep in posTaps
            val letTapped = currentStep in letterTaps

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
            ) {
                if (mode != NBackMode.LETTER_ONLY) {
                    Button(
                        onClick = {
                            if (canTapMatch && currentStep !in posTaps) posTaps.add(currentStep)
                        },
                        enabled = canTapMatch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (posTapped) colors.green else colors.navyBanner,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("nback_position_match_btn")
                    ) {
                        Text(
                            text = if (posTapped) "Position Marked ✓" else "Position Match",
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (mode != NBackMode.POSITION_ONLY) {
                    Button(
                        onClick = {
                            if (canTapMatch && currentStep !in letterTaps) letterTaps.add(currentStep)
                        },
                        enabled = canTapMatch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (letTapped) colors.green else colors.teal,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("nback_letter_match_btn")
                    ) {
                        Text(
                            text = if (letTapped) "Letter Marked ✓" else "Letter Match",
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            evaluation?.let { eval ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.tealLight),
                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Session Accuracy: ${eval.accuracyPercent}%  •  ${eval.tierDeltaLabel}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Text(
                            text = "Hits: ${eval.hits}  |  Misses: ${eval.misses}  |  False Alarms: ${eval.falseAlarms}",
                            fontSize = 13.sp,
                            color = colors.ink
                        )
                        Text(
                            text = "Auto-promotes above 85% accuracy and steps down gently below 55%.",
                            fontSize = 11.5.sp,
                            color = colors.muted
                        )
                    }
                }
            }

            Button(
                onClick = {
                    evaluation = null
                    posTaps.clear()
                    letterTaps.clear()
                    trials = BrainLabMemoryEngine.generateNBackSequence(n = currentN, mode = mode)
                    currentStep = -1
                    isRunning = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .heightIn(min = 52.dp)
                    .testTag("nback_start_btn")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start ${currentN}-Back Round", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CorsiSpanDrillView(
    stats: BrainLabStats,
    onRecordSpan: (span: Int, reverseMode: Boolean, activeSeconds: Long) -> Unit
) {
    val colors = ExamTheme.colors
    var gridSize by remember { mutableIntStateOf(4) }
    var span by remember { mutableIntStateOf(4) }
    var reverseMode by remember { mutableStateOf(true) }
    var withAlphanumeric by remember { mutableStateOf(false) }

    var puzzle by remember { mutableStateOf<BrainLabMemoryEngine.CorsiPuzzle?>(null) }
    var phase by remember { mutableStateOf("IDLE") } // IDLE, SHOWING, INPUT, RESULT
    var highlightedCell by remember { mutableIntStateOf(-1) }
    val userSequence = remember { mutableStateListOf<Int>() }
    var isSuccess by remember { mutableStateOf(false) }
    var roundStartMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(puzzle, phase) {
        val p = puzzle ?: return@LaunchedEffect
        if (phase != "SHOWING") return@LaunchedEffect
        highlightedCell = -1
        delay(450L)
        for (cell in p.sequence) {
            highlightedCell = cell
            delay(650L)
            highlightedCell = -1
            delay(250L)
        }
        roundStartMs = System.currentTimeMillis()
        phase = "INPUT"
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
                Text(
                    text = "Corsi Matrix & Reverse Sequence Span",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Text(
                    text = "Best Forward: ${stats.bestCorsiForwardSpan}  •  Best Reverse: ${stats.bestCorsiReverseSpan}",
                    fontSize = 12.sp,
                    color = colors.teal
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = reverseMode,
                        onClick = { if (phase == "IDLE" || phase == "RESULT") reverseMode = true },
                        label = { Text("Reverse Recall") },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealLight, selectedLabelColor = colors.teal)
                    )
                    FilterChip(
                        selected = !reverseMode,
                        onClick = { if (phase == "IDLE" || phase == "RESULT") reverseMode = false },
                        label = { Text("Forward Recall") },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealLight, selectedLabelColor = colors.teal)
                    )
                    FilterChip(
                        selected = gridSize == 4,
                        onClick = { if (phase == "IDLE" || phase == "RESULT") gridSize = 4 },
                        label = { Text("4×4 Board") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = gridSize == 5,
                        onClick = { if (phase == "IDLE" || phase == "RESULT") gridSize = 5 },
                        label = { Text("5×5 Board") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = withAlphanumeric,
                        onClick = { if (phase == "IDLE" || phase == "RESULT") withAlphanumeric = !withAlphanumeric },
                        label = { Text("Alphanumeric Tags") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (4..8).forEach { s ->
                        FilterChip(
                            selected = span == s,
                            onClick = { if (phase == "IDLE" || phase == "RESULT") span = s },
                            label = { Text("Span $s") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }

        val instruction = when (phase) {
            "SHOWING" -> "Watch the sequence carefully (${puzzle?.span ?: span} tiles)…"
            "INPUT" -> if (reverseMode) {
                "Tap the sequence BACKWARDS (last → first): ${userSequence.size}/${puzzle?.expectedInput?.size ?: span}"
            } else {
                "Tap the sequence in the SAME order: ${userSequence.size}/${puzzle?.expectedInput?.size ?: span}"
            }
            "RESULT" -> if (isSuccess) {
                "Clean recall at Span ${puzzle?.span}! Ready to advance."
            } else {
                "Mismatch — expected order was: ${puzzle?.expectedInput?.joinToString(" → ") { "#${it + 1}" }}"
            }
            else -> "Reversing sequences forces active working-memory manipulation rather than passive echo."
        }
        Text(
            text = instruction,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                phase == "RESULT" && isSuccess -> colors.green
                phase == "RESULT" && !isSuccess -> colors.red
                else -> colors.inkBold
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        val activeGrid = puzzle?.gridSize ?: gridSize
        val corsiFontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(colors.surface, RoundedCornerShape(16.dp))
                .padding(10.dp)
        ) {
            val cellSideDp = (maxWidth.value - (activeGrid - 1) * 6f) / activeGrid
            val cellTagSp = minOf(13f, (cellSideDp * 0.34f) / corsiFontScale).coerceAtLeast(9.5f).sp
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until activeGrid) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0 until activeGrid) {
                            val idx = r * activeGrid + c
                            val isLit = highlightedCell == idx
                            val tapOrder = userSequence.indexOf(idx)
                            val cellColor = when {
                                isLit -> colors.teal
                                tapOrder >= 0 -> colors.navyBanner
                                else -> colors.surfaceVariant
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = cellColor,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = phase == "INPUT" && idx !in userSequence) {
                                        val p = puzzle ?: return@clickable
                                        userSequence.add(idx)
                                        if (userSequence.size == p.expectedInput.size) {
                                            val ok = userSequence.toList() == p.expectedInput
                                            isSuccess = ok
                                            phase = "RESULT"
                                            val secs = ((System.currentTimeMillis() - roundStartMs) / 1000L).coerceAtLeast(1L)
                                            onRecordSpan(if (ok) p.span else 0, p.reverseMode, secs)
                                            if (ok && span < 8) span++
                                        }
                                    }
                                    .testTag("corsi_cell_$idx")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val label = when {
                                        tapOrder >= 0 -> "${tapOrder + 1}"
                                        puzzle?.alphanumericLabels?.isNotEmpty() == true -> puzzle!!.alphanumericLabels.getOrElse(idx) { "" }
                                        else -> ""
                                    }
                                    if (label.isNotEmpty()) {
                                        Text(
                                            text = label,
                                            fontSize = cellTagSp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLit || tapOrder >= 0) Color.White else colors.ink,
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

        Button(
            onClick = {
                userSequence.clear()
                puzzle = BrainLabMemoryEngine.generateCorsiPuzzle(
                    gridSize = gridSize,
                    span = span,
                    reverseMode = reverseMode,
                    withAlphanumericLabels = withAlphanumeric
                )
                phase = "SHOWING"
            },
            enabled = phase != "SHOWING" && phase != "INPUT",
            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 52.dp).testTag("corsi_start_btn")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (phase == "RESULT") "Next Round (Span $span)" else "Show Sequence", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StroopRuleSwitchView(
    stats: BrainLabStats,
    onRecordRound: (accuracyPercent: Int, avgReactionMs: Long, activeSeconds: Long) -> Unit
) {
    val colors = ExamTheme.colors
    val totalTrials = 16
    var allowArrows by remember { mutableStateOf(true) }
    var isRunning by remember { mutableStateOf(false) }
    var trialIndex by remember { mutableIntStateOf(0) }
    var currentTrial by remember { mutableStateOf<BrainLabMemoryEngine.StroopTrial?>(null) }
    var correctCount by remember { mutableIntStateOf(0) }
    var totalReactionMs by remember { mutableLongStateOf(0L) }
    var trialShownMs by remember { mutableLongStateOf(0L) }
    var roundSummary by remember { mutableStateOf<String?>(null) }
    var lastFeedback by remember { mutableStateOf<String?>(null) }

    fun mapStroopColor(sc: BrainLabMemoryEngine.StroopColor): Color = when (sc) {
        BrainLabMemoryEngine.StroopColor.RED -> Color(0xFFDC2626)
        BrainLabMemoryEngine.StroopColor.BLUE -> Color(0xFF2563EB)
        BrainLabMemoryEngine.StroopColor.GREEN -> Color(0xFF16A34A)
        BrainLabMemoryEngine.StroopColor.AMBER -> Color(0xFFD97706)
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
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Stroop Impulse & Rule-Switch Arena",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Text(
                    text = "Best Accuracy: ${stats.bestStroopAccuracy}%" +
                        if (stats.bestStroopReactionMs > 0) "  •  Fastest Avg: ${stats.bestStroopReactionMs} ms" else "",
                    fontSize = 12.sp,
                    color = colors.teal
                )
                FilterChip(
                    selected = allowArrows,
                    onClick = { if (!isRunning) allowArrows = !allowArrows },
                    label = { Text("Include Arrow / Opposite-Direction Conflicts") },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        if (isRunning && currentTrial != null) {
            val t = currentTrial!!
            LinearProgressIndicator(
                progress = { (trialIndex + 1).toFloat() / totalTrials },
                color = colors.teal,
                trackColor = colors.surfaceVariant,
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).height(6.dp)
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.amberLight,
                border = BorderStroke(1.dp, colors.amber),
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = t.rule.badge,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.inkBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = t.rule.instruction,
                        fontSize = 12.sp,
                        color = colors.ink,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (t.isArrowMode) {
                        Text(
                            text = t.arrowDirection.symbol,
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.inkBold
                        )
                    } else {
                        Text(
                            text = t.displayWord.label,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            color = mapStroopColor(t.inkColor),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            lastFeedback?.let {
                Text(text = it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.muted)
            }

            val indexedChoices = t.choices.withIndex().toList()
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
            ) {
                indexedChoices.chunked(2).forEach { pair ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        pair.forEach { (idx, label) ->
                            Button(
                                onClick = {
                                    val rt = (System.currentTimeMillis() - trialShownMs).coerceIn(100L, 10_000L)
                                    totalReactionMs += rt
                                    val ok = idx == t.expectedChoiceIndex
                                    if (ok) correctCount++
                                    lastFeedback = if (ok) "Clean (${rt}ms)" else "Inhibition slip (${rt}ms)"
                                    val nextIdx = trialIndex + 1
                                    if (nextIdx >= totalTrials) {
                                        isRunning = false
                                        val acc = ((correctCount.toFloat() / totalTrials) * 100f).toInt()
                                        val avgRt = totalReactionMs / totalTrials
                                        val activeSec = (totalReactionMs / 1000L).coerceAtLeast(1L)
                                        roundSummary = "Accuracy: $acc% ($correctCount/$totalTrials) • Avg Reaction: ${avgRt} ms"
                                        onRecordRound(acc, avgRt, activeSec)
                                    } else {
                                        trialIndex = nextIdx
                                        currentTrial = BrainLabMemoryEngine.generateStroopTrial(
                                            stepIndex = nextIdx,
                                            allowArrowMode = allowArrows,
                                            previousRule = t.rule
                                        )
                                        trialShownMs = System.currentTimeMillis()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant, contentColor = colors.inkBold),
                                border = BorderStroke(1.dp, colors.border),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("stroop_choice_$idx")
                            ) {
                                Text(label, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            roundSummary?.let { summary ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.tealLight),
                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.teal)
                        Text(summary, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                    }
                }
            }

            Button(
                onClick = {
                    correctCount = 0
                    totalReactionMs = 0L
                    trialIndex = 0
                    lastFeedback = null
                    roundSummary = null
                    currentTrial = BrainLabMemoryEngine.generateStroopTrial(0, allowArrows)
                    trialShownMs = System.currentTimeMillis()
                    isRunning = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 52.dp).testTag("stroop_start_btn")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start 16-Trial Conflict Sprint", fontWeight = FontWeight.Bold)
            }
        }
    }
}
