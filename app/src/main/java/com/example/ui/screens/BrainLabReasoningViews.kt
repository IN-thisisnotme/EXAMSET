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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BrainLabStats
import com.example.ui.theme.ExamTheme
import com.example.util.BrainLabReasoningEngine

@Composable
fun SyllogismArenaView(
    stats: BrainLabStats,
    onRecordAnswer: (correct: Boolean, activeSeconds: Long) -> Unit
) {
    val colors = ExamTheme.colors
    var puzzle by remember { mutableStateOf(BrainLabReasoningEngine.generateSyllogismPuzzle()) }
    var selectedAnswer by remember { mutableStateOf<BrainLabReasoningEngine.SyllogismAnswer?>(null) }
    var startedAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

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
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Venn-Verified Syllogism",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Text(
                        text = "Streak: ${stats.reasoningStreak} (Best ${stats.bestReasoningStreak})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.teal
                    )
                }

                Text("PREMISES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.muted)
                puzzle.premises.forEachIndexed { idx, p ->
                    Text(
                        text = "${idx + 1}. ${p.format(puzzle.terms)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("CONCLUSIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.muted)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "I.  ${puzzle.conclusionI.format(puzzle.terms)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkBold
                        )
                        Text(
                            text = "II. ${puzzle.conclusionII.format(puzzle.terms)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkBold
                        )
                    }
                }
            }
        }

        BrainLabReasoningEngine.SyllogismAnswer.entries.forEachIndexed { idx, choice ->
            val isChosen = selectedAnswer == choice
            val isRight = choice == puzzle.verification.answer
            val container = when {
                selectedAnswer == null -> colors.surface
                isRight -> colors.greenLight
                isChosen && !isRight -> colors.redLight
                else -> colors.surface
            }
            val borderCol = when {
                selectedAnswer == null -> colors.border
                isRight -> colors.green
                isChosen && !isRight -> colors.red
                else -> colors.border
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = container,
                border = BorderStroke(1.dp, borderCol),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .heightIn(min = 48.dp)
                    .clickable(enabled = selectedAnswer == null) {
                        selectedAnswer = choice
                        val secs = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceIn(1L, 300L)
                        onRecordAnswer(choice == puzzle.verification.answer, secs)
                    }
                    .testTag("syllogism_option_$idx")
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Text(choice.label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.inkBold)
                }
            }
        }

        selectedAnswer?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (it == puzzle.verification.answer) "Verified Correct" else "Counterexample Breakdown",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (it == puzzle.verification.answer) colors.green else colors.red
                    )
                    Text(
                        text = puzzle.verification.explanation,
                        fontSize = 12.5.sp,
                        color = colors.ink,
                        lineHeight = 18.sp
                    )
                }
            }
            Button(
                onClick = {
                    selectedAnswer = null
                    puzzle = BrainLabReasoningEngine.generateSyllogismPuzzle()
                    startedAtMs = System.currentTimeMillis()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Next Syllogism", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConstraintSeatingView(
    stats: BrainLabStats,
    onRecordSolved: (correct: Boolean, activeSeconds: Long) -> Unit
) {
    val colors = ExamTheme.colors
    var isCircular by remember { mutableStateOf(false) }
    var seatCount by remember { mutableIntStateOf(4) }
    var puzzle by remember(isCircular, seatCount) {
        mutableStateOf(BrainLabReasoningEngine.generateSeatingPuzzle(seatCount = seatCount, isCircular = isCircular))
    }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var startedAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 1-tap mini scratchpad grid state: slotIndex -> assigned person name (or blank)
    val scratchSlots = remember(puzzle.id) {
        mutableStateListOf<String>().apply { repeat(puzzle.people.size) { add("?") } }
    }
    var activeScratchSlot by remember(puzzle.id) { mutableIntStateOf(0) }

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
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Constraint & Seating Puzzle", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                    Text("Solved: ${stats.constraintSolved}", fontSize = 12.sp, color = colors.teal)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isCircular,
                        onClick = { isCircular = false; selectedOption = null },
                        label = { Text("Linear Row") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealLight, selectedLabelColor = colors.teal)
                    )
                    FilterChip(
                        selected = isCircular,
                        onClick = { isCircular = true; selectedOption = null },
                        label = { Text("Circular Table") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealLight, selectedLabelColor = colors.teal)
                    )
                    FilterChip(
                        selected = seatCount == 4,
                        onClick = { seatCount = 4; selectedOption = null },
                        label = { Text("4 Seats") }
                    )
                    FilterChip(
                        selected = seatCount == 5,
                        onClick = { seatCount = 5; selectedOption = null },
                        label = { Text("5 Seats") }
                    )
                }
                puzzle.clues.forEachIndexed { idx, clue ->
                    Text("• Clue ${idx + 1}: $clue", fontSize = 13.sp, color = colors.ink)
                }
                Text(
                    text = puzzle.promptQuestion,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
            }
        }

        // Built-in 1-Tap Mini Scratchpad
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
            border = BorderStroke(1.dp, colors.border),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1-Tap Mini Scratchpad (tap seat, then person)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.muted
                    )
                    Text(
                        text = "Clear",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.teal,
                        modifier = Modifier.clickable {
                            for (i in scratchSlots.indices) scratchSlots[i] = "?"
                        }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    scratchSlots.forEachIndexed { idx, assigned ->
                        val isSelected = activeScratchSlot == idx
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) colors.tealLight else colors.surface,
                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) colors.teal else colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clickable { activeScratchSlot = idx }
                        ) {
                            Column(
                                modifier = Modifier.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("#${idx + 1}", fontSize = 10.sp, color = colors.muted)
                                Text(
                                    text = assigned.take(6),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    puzzle.people.forEach { person ->
                        OutlinedButton(
                            onClick = {
                                scratchSlots[activeScratchSlot] = person
                                activeScratchSlot = (activeScratchSlot + 1) % scratchSlots.size
                            },
                            border = BorderStroke(1.dp, colors.border),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.heightIn(min = 40.dp)
                        ) {
                            Text(person, fontSize = 12.sp, color = colors.inkBold)
                        }
                    }
                }
            }
        }

        // Answer Options
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
        ) {
            puzzle.options.forEachIndexed { idx, opt ->
                val isChosen = selectedOption == idx
                val isRight = idx == puzzle.correctIndex
                val bg = when {
                    selectedOption == null -> colors.surface
                    isRight -> colors.greenLight
                    isChosen && !isRight -> colors.redLight
                    else -> colors.surface
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = bg,
                    border = BorderStroke(1.dp, if (selectedOption != null && isRight) colors.green else colors.border),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable(enabled = selectedOption == null) {
                            selectedOption = idx
                            val secs = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceIn(1L, 300L)
                            onRecordSolved(idx == puzzle.correctIndex, secs)
                        }
                        .testTag("seating_opt_$idx")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                        Text(opt, fontWeight = FontWeight.Bold, color = colors.inkBold)
                    }
                }
            }
        }

        selectedOption?.let {
            Text(
                text = puzzle.explanation,
                fontSize = 12.5.sp,
                color = colors.ink,
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
            )
            Button(
                onClick = {
                    selectedOption = null
                    puzzle = BrainLabReasoningEngine.generateSeatingPuzzle(seatCount = seatCount, isCircular = isCircular)
                    startedAtMs = System.currentTimeMillis()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 48.dp)
            ) {
                Text("Next Seating Puzzle", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ArgumentCritiqueView(
    stats: BrainLabStats,
    onRecordSolved: (correct: Boolean, activeSeconds: Long) -> Unit
) {
    val colors = ExamTheme.colors
    val bank = remember { BrainLabReasoningEngine.getShuffledArgumentBank() }
    var index by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var startedAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val q = bank[index % bank.size]

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
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight) {
                        Text(
                            text = q.categoryTag,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.teal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text("Solved: ${stats.argumentSolved}", fontSize = 12.sp, color = colors.muted)
                }
                Text(q.passage, fontSize = 13.5.sp, color = colors.ink, lineHeight = 20.sp)
                Text(q.prompt, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
            }
        }

        q.options.forEachIndexed { idx, opt ->
            val isChosen = selected == idx
            val isRight = idx == q.correctIndex
            val bg = when {
                selected == null -> colors.surface
                isRight -> colors.greenLight
                isChosen && !isRight -> colors.redLight
                else -> colors.surface
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bg,
                border = BorderStroke(1.dp, if (selected != null && isRight) colors.green else colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .heightIn(min = 48.dp)
                    .clickable(enabled = selected == null) {
                        selected = idx
                        val secs = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceIn(1L, 300L)
                        onRecordSolved(idx == q.correctIndex, secs)
                    }
                    .testTag("argument_opt_$idx")
            ) {
                Text(
                    text = opt,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkBold,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        selected?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
            ) {
                Text(
                    text = q.explanation,
                    fontSize = 12.5.sp,
                    color = colors.ink,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Button(
                onClick = {
                    selected = null
                    index++
                    startedAtMs = System.currentTimeMillis()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 48.dp)
            ) {
                Text("Next Argument", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DataSufficiencyView(
    stats: BrainLabStats,
    onRecordSolved: (correct: Boolean, activeSeconds: Long) -> Unit
) {
    val colors = ExamTheme.colors
    val bank = remember { BrainLabReasoningEngine.getShuffledDataSufficiencyBank() }
    var index by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<BrainLabReasoningEngine.DataSufficiencyAnswer?>(null) }
    var startedAtMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val q = bank[index % bank.size]

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
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.amberLight) {
                        Text(
                            text = q.trapTag,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.amber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text("Solved: ${stats.dataSufficiencySolved}", fontSize = 12.sp, color = colors.muted)
                }
                Text(q.question, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Statement I:  ${q.statementI}", fontSize = 13.5.sp, color = colors.inkBold)
                        Text("Statement II: ${q.statementII}", fontSize = 13.5.sp, color = colors.inkBold)
                    }
                }
            }
        }

        BrainLabReasoningEngine.DataSufficiencyAnswer.entries.forEachIndexed { idx, ans ->
            val isChosen = selected == ans
            val isRight = ans == q.answer
            val bg = when {
                selected == null -> colors.surface
                isRight -> colors.greenLight
                isChosen && !isRight -> colors.redLight
                else -> colors.surface
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bg,
                border = BorderStroke(1.dp, if (selected != null && isRight) colors.green else colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .heightIn(min = 48.dp)
                    .clickable(enabled = selected == null) {
                        selected = ans
                        val secs = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceIn(1L, 300L)
                        onRecordSolved(ans == q.answer, secs)
                    }
                    .testTag("ds_opt_$idx")
            ) {
                Text(
                    text = ans.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkBold,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        selected?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
            ) {
                Text(
                    text = q.explanation,
                    fontSize = 12.5.sp,
                    color = colors.ink,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Button(
                onClick = {
                    selected = null
                    index++
                    startedAtMs = System.currentTimeMillis()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 48.dp)
            ) {
                Text("Next Sufficiency Check", fontWeight = FontWeight.Bold)
            }
        }
    }
}
