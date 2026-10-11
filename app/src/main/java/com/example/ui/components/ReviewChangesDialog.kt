package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.McqQuestion
import com.example.ui.theme.ExamTheme

data class ChangedOptionDiff(
    val letter: String,
    val oldText: String,
    val newText: String
)

data class QuestionDiff(
    val questionNum: Int,
    val questionText: String, // Full question statement (always available)
    val originalQuestion: McqQuestion? = null,
    val currentQuestion: McqQuestion? = null,
    val isNew: Boolean = false,
    val isDeleted: Boolean = false,
    val answerKeyChanged: Boolean = false,
    val oldAnswerKey: String? = null,
    val newAnswerKey: String? = null,
    val oldAnswerText: String? = null,
    val newAnswerText: String? = null,
    val stemChanged: Boolean = false,
    val oldStem: String? = null,
    val newStem: String? = null,
    val optionsChanged: Boolean = false,
    val changedOptions: List<ChangedOptionDiff> = emptyList(),
    val explanationChanged: Boolean = false,
    val oldExplanation: String? = null,
    val newExplanation: String? = null
) {
    val hasChanges: Boolean
        get() = isNew || isDeleted || answerKeyChanged || stemChanged || optionsChanged || explanationChanged
}

fun getOptionTextByKey(q: McqQuestion?, key: String?): String? {
    if (q == null || key == null) return null
    return when (key.trim().uppercase()) {
        "A" -> q.options.a
        "B" -> q.options.b
        "C" -> q.options.c
        "D" -> q.options.d
        else -> null
    }
}

fun computeQuestionDiffs(
    originalList: List<McqQuestion>,
    currentList: List<McqQuestion>
): List<QuestionDiff> {
    val originalMap = originalList.associateBy { it.num }
    val currentMap = currentList.associateBy { it.num }

    val diffs = mutableListOf<QuestionDiff>()

    // Check modified and new questions
    for (current in currentList) {
        val original = originalMap[current.num]
        if (original == null) {
            // New question added
            diffs.add(
                QuestionDiff(
                    questionNum = current.num,
                    questionText = current.question,
                    originalQuestion = null,
                    currentQuestion = current,
                    isNew = true,
                    newAnswerKey = current.correctAnswer,
                    newAnswerText = getOptionTextByKey(current, current.correctAnswer),
                    newStem = current.question,
                    newExplanation = current.explanation
                )
            )
        } else {
            val keyChanged = !original.correctAnswer.equals(current.correctAnswer, ignoreCase = true)
            val stemChanged = original.question.trim() != current.question.trim()
            val expChanged = (original.explanation?.trim().orEmpty()) != (current.explanation?.trim().orEmpty())

            val changedOpts = mutableListOf<ChangedOptionDiff>()
            if (original.options.a.trim() != current.options.a.trim()) {
                changedOpts.add(ChangedOptionDiff("A", original.options.a, current.options.a))
            }
            if (original.options.b.trim() != current.options.b.trim()) {
                changedOpts.add(ChangedOptionDiff("B", original.options.b, current.options.b))
            }
            if (original.options.c.trim() != current.options.c.trim()) {
                changedOpts.add(ChangedOptionDiff("C", original.options.c, current.options.c))
            }
            if (original.options.d.trim() != current.options.d.trim()) {
                changedOpts.add(ChangedOptionDiff("D", original.options.d, current.options.d))
            }

            if (keyChanged || stemChanged || expChanged || changedOpts.isNotEmpty()) {
                diffs.add(
                    QuestionDiff(
                        questionNum = current.num,
                        questionText = current.question,
                        originalQuestion = original,
                        currentQuestion = current,
                        answerKeyChanged = keyChanged,
                        oldAnswerKey = original.correctAnswer,
                        newAnswerKey = current.correctAnswer,
                        oldAnswerText = getOptionTextByKey(original, original.correctAnswer),
                        newAnswerText = getOptionTextByKey(current, current.correctAnswer),
                        stemChanged = stemChanged,
                        oldStem = original.question,
                        newStem = current.question,
                        optionsChanged = changedOpts.isNotEmpty(),
                        changedOptions = changedOpts,
                        explanationChanged = expChanged,
                        oldExplanation = original.explanation,
                        newExplanation = current.explanation
                    )
                )
            }
        }
    }

    // Check deleted questions
    for (original in originalList) {
        if (!currentMap.containsKey(original.num)) {
            diffs.add(
                QuestionDiff(
                    questionNum = original.num,
                    questionText = original.question,
                    originalQuestion = original,
                    currentQuestion = null,
                    isDeleted = true,
                    oldAnswerKey = original.correctAnswer,
                    oldAnswerText = getOptionTextByKey(original, original.correctAnswer),
                    oldStem = original.question
                )
            )
        }
    }

    return diffs.sortedBy { it.questionNum }
}

@Composable
fun ReviewChangesDialog(
    diffs: List<QuestionDiff>,
    onConfirmSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 12.dp)
                .testTag("review_changes_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(colors.teal.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Review Alterations & Save",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "${diffs.size} question${if (diffs.size == 1) "" else "s"} modified • Full question & answer preview enabled",
                                fontSize = 11.5.sp,
                                color = colors.muted
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.muted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // List of only changed questions with complete question context
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(diffs, key = { "diff_${it.questionNum}_${it.isDeleted}" }) { diff ->
                        FullContextDiffCard(diff = diff)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDiscard,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.red),
                        border = BorderStroke(1.dp, colors.red.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("discard_edits_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Discard", fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("keep_editing_button")
                    ) {
                        Text("Keep Editing", fontSize = 12.sp, color = colors.muted)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = onConfirmSave,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("confirm_save_edits_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirm & Save", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullContextDiffCard(diff: QuestionDiff) {
    val colors = ExamTheme.colors
    var showAllOptions by remember { mutableStateOf(true) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (diff.answerKeyChanged) colors.amber.copy(alpha = 0.6f) else colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Card Top Header: Question Number & Change Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = colors.navyBanner
                    ) {
                        Text(
                            text = "Question ${diff.questionNum}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.navyBannerText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (diff.answerKeyChanged) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.amberLight,
                            border = BorderStroke(0.6.dp, colors.amber)
                        ) {
                            Text(
                                text = "Answer Key Changed",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.amber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (diff.stemChanged) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.teal.copy(alpha = 0.15f),
                            border = BorderStroke(0.6.dp, colors.teal)
                        ) {
                            Text(
                                text = "Statement Edited",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (diff.optionsChanged) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.teal.copy(alpha = 0.15f),
                            border = BorderStroke(0.6.dp, colors.teal)
                        ) {
                            Text(
                                text = "Options Updated",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (diff.explanationChanged) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.teal.copy(alpha = 0.15f),
                            border = BorderStroke(0.6.dp, colors.teal)
                        ) {
                            Text(
                                text = "Explanation Updated",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (diff.isNew) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.greenLight,
                        border = BorderStroke(0.6.dp, colors.green)
                    ) {
                        Text(
                            text = "+ New Question",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.green,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (diff.isDeleted) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.redLight,
                        border = BorderStroke(0.6.dp, colors.red)
                    ) {
                        Text(
                            text = "Deleted",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.red,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. THE QUESTION STATEMENT (ALWAYS DISPLAYED - NO BLIND SPOTS)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (diff.stemChanged) "Question Statement (Altered):" else "Question Statement:",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            letterSpacing = 0.4.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (diff.stemChanged) {
                        // Old statement
                        Text(
                            text = "Original: ${diff.oldStem.orEmpty()}",
                            fontSize = 11.5.sp,
                            color = colors.muted,
                            textDecoration = TextDecoration.LineThrough,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                        // New statement
                        Text(
                            text = "Updated: ${diff.newStem.orEmpty()}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                    } else {
                        Text(
                            text = diff.questionText,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                    }
                }
            }

            // 2. ANSWER KEY CHANGE SECTION (WITH FULL OPTION TEXT BEFORE & AFTER)
            if (diff.answerKeyChanged) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.amberLight.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = colors.amber, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Answer Key Alteration",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Previous Answer
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surface,
                                border = BorderStroke(0.8.dp, colors.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "PREVIOUS KEY",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.muted
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = colors.surfaceVariant
                                    ) {
                                        Text(
                                            text = "Option ${diff.oldAnswerKey ?: "None"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.ink,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (!diff.oldAnswerText.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = diff.oldAnswerText,
                                            fontSize = 11.sp,
                                            color = colors.muted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Changed to",
                                tint = colors.teal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // New Answer
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.greenLight,
                                border = BorderStroke(1.dp, colors.green),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "NEW KEY (SAVING)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.green
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = colors.green
                                    ) {
                                        Text(
                                            text = "Option ${diff.newAnswerKey ?: "None"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (!diff.newAnswerText.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = diff.newAnswerText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.green,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. COMPLETE OPTIONS LIST WITH HIGHLIGHTED ANSWER (ELIMINATES BLIND ALTERATIONS)
            val activeQuestion = diff.currentQuestion ?: diff.originalQuestion
            if (activeQuestion != null && !diff.isDeleted) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .border(0.8.dp, colors.border, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAllOptions = !showAllOptions },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FormatListBulleted,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Question Options (Full Context)",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }
                        Icon(
                            imageVector = if (showAllOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (showAllOptions) "Collapse" else "Expand",
                            tint = colors.muted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    AnimatedVisibility(visible = showAllOptions) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            listOf(
                                "A" to activeQuestion.options.a,
                                "B" to activeQuestion.options.b,
                                "C" to activeQuestion.options.c,
                                "D" to activeQuestion.options.d
                            ).forEach { (letter, optText) ->
                                val isSelectedAnswer = activeQuestion.correctAnswer.equals(letter, ignoreCase = true)
                                val isPreviousKey = diff.oldAnswerKey.equals(letter, ignoreCase = true) && diff.answerKeyChanged
                                val isOptionEdited = diff.changedOptions.any { it.letter == letter }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .background(
                                            if (isSelectedAnswer) colors.greenLight else if (isPreviousKey) colors.amberLight.copy(alpha = 0.4f) else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelectedAnswer) colors.green else if (isPreviousKey) colors.amber else colors.surfaceVariant
                                    ) {
                                        Text(
                                            text = letter,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelectedAnswer || isPreviousKey) Color.White else colors.ink,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = optText,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelectedAnswer) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelectedAnswer) colors.green else colors.ink,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelectedAnswer) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = colors.green
                                        ) {
                                            Text(
                                                text = "✓ Key",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    } else if (isPreviousKey) {
                                        Text(
                                            text = "Old Key",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.amber
                                        )
                                    } else if (isOptionEdited) {
                                        Text(
                                            text = "Edited",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.teal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. EXPLANATION DIFF (IF CHANGED OR AVAILABLE)
            if (diff.explanationChanged) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(8.dp))
                        .border(0.8.dp, colors.border, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PostAdd, contentDescription = null, tint = colors.teal, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Explanation Updated",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                    }
                    if (!diff.oldExplanation.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Previous: ${diff.oldExplanation}",
                            fontSize = 11.sp,
                            color = colors.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "New: ${diff.newExplanation.orEmpty().ifBlank { "Explanation cleared" }}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
