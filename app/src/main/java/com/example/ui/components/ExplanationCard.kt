package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.McqQuestion
import com.example.ui.theme.ExamTheme
import com.example.util.McqParser
import com.example.util.QuestionFormatter

/**
 * Detailed Pedagogical Explanation Card:
 * - Displays the question stem, attached diagrams/formulas, and complete options (A)-(D).
 * - Highlights the correct option in green with semantic badges and check indicators.
 * - Single-source canonical representation of the correct answer in the header.
 * - Seamless multi-line aligned option breakdown and detailed pedagogical explanations.
 * - In-place editing capability integrated with the Master Set edit system.
 */
@Composable
fun ExplanationCard(
    question: McqQuestion,
    isEditMode: Boolean = false,
    onUpdateQuestion: ((McqQuestion) -> Unit)? = null,
    onEnterEditMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val rawAnswer = question.correctAnswer?.trim()
    val canonicalLetter = rawAnswer?.let {
        McqParser.canonicalOption(it) ?: it.uppercase().removeSurrounding("(", ")").removeSurrounding("[", "]").removePrefix("OPTION").trim().removeSuffix(".")
    }

    var isEditing by remember { mutableStateOf(false) }
    var showPreviewInEditMode by remember { mutableStateOf(false) }
    var editedExplanation by remember(question.explanation) { mutableStateOf(question.explanation ?: "") }

    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            isEditing = false
            showPreviewInEditMode = false
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.8.dp, if (isEditing) colors.amber else colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Question Number Badge & In-Place Edit Solution Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.teal.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Question ${question.num}",
                        color = colors.tealDark,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                    )
                }

                if (onUpdateQuestion != null) {
                    Surface(
                        onClick = {
                            if (!isEditMode && onEnterEditMode != null) {
                                onEnterEditMode()
                            }
                            isEditing = !isEditing
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isEditing) colors.amberLight else colors.surfaceVariant,
                        border = BorderStroke(0.8.dp, if (isEditing) colors.amber else colors.border),
                        modifier = Modifier.testTag("edit_explanation_btn_${question.num}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (isEditing) "Done Editing" else "Edit Solution",
                                tint = if (isEditing) colors.amber else colors.inkBold,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEditing) "Done" else "Edit Solution",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isEditing) colors.amber else colors.inkBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Question Body, Diagrams, Formulas, and Full Options with highlighted correct answer
            StructuredQuestionContent(
                question = question,
                showAnswerIndicator = true,
                showNumber = false,
                showTypeBadge = false,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(
                color = colors.border.copy(alpha = 0.5f),
                thickness = 0.8.dp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Explanation Section Header Tag & Live Preview Toggle when Editing
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = if (isEditing) colors.amber else colors.teal,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isEditing) "EDIT EXPLANATION & SOLUTION" else "EXPLANATION & SOLUTION",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isEditing) colors.amber else colors.tealDark,
                        letterSpacing = 0.5.sp
                    )
                }

                if (isEditing) {
                    Surface(
                        onClick = { showPreviewInEditMode = !showPreviewInEditMode },
                        shape = RoundedCornerShape(6.dp),
                        color = if (showPreviewInEditMode) colors.teal.copy(alpha = 0.15f) else colors.surfaceVariant,
                        border = BorderStroke(0.6.dp, if (showPreviewInEditMode) colors.teal else colors.border),
                        modifier = Modifier.testTag("toggle_preview_explanation_${question.num}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showPreviewInEditMode) Icons.Default.Edit else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = if (showPreviewInEditMode) colors.tealDark else colors.ink,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showPreviewInEditMode) "Raw Editor" else "Live Preview",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (showPreviewInEditMode) colors.tealDark else colors.ink
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Explanation Body (Interactive Editor or Structured Pedagogical Breakdown)
            if (isEditing && onUpdateQuestion != null && !showPreviewInEditMode) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedExplanation,
                        onValueChange = {
                            editedExplanation = it
                            onUpdateQuestion(question.copy(explanation = it))
                        },
                        label = { Text("Pedagogical Explanation (Supports LaTeX & Tables)", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("explanation_textfield_${question.num}"),
                        minLines = 4,
                        maxLines = 14
                    )
                    Text(
                        text = "Tip: Markdown tables (| Header |) and LaTeX formulas ($...$, \\frac{}{}) are automatically rendered in Live Preview.",
                        fontSize = 10.5.sp,
                        color = colors.muted
                    )
                }
            } else if (!question.explanation.isNullOrBlank() || (isEditing && editedExplanation.isNotBlank())) {
                val currentText = if (isEditing) editedExplanation else (question.explanation ?: "")
                StructuredExplanationContent(
                    rawExplanation = currentText,
                    correctLetter = canonicalLetter ?: question.correctAnswer,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "No detailed pedagogical explanation provided for this question.",
                    color = colors.muted,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}
