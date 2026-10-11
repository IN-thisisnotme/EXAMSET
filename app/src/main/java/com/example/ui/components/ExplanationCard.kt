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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 */
@Composable
fun ExplanationCard(
    question: McqQuestion,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val rawAnswer = question.correctAnswer?.trim()
    val canonicalLetter = rawAnswer?.let {
        McqParser.canonicalOption(it) ?: it.uppercase().removeSurrounding("(", ")").removeSurrounding("[", "]").removePrefix("OPTION").trim().removeSuffix(".")
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.8.dp, colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Question Number Badge (Type Badge and Correct Option Badge commented out per design review)
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

                // Question type tag commented out per user instruction; uncomment if type badge is needed in explanation view:
                /*
                val qType = QuestionFormatter.displayType(question)
                if (!qType.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(0.6.dp, colors.border)
                    ) {
                        Text(
                            text = qType,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                */

                // Correct option badge commented out to prevent narrow-screen wrapping and eliminate redundancy with the highlighted option below:
                /*
                if (!canonicalLetter.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.greenLight,
                        border = BorderStroke(0.8.dp, colors.green.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colors.green,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Correct: Option ($canonicalLetter)",
                                color = colors.green,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                */
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

            // Explanation Section Header Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "EXPLANATION & SOLUTION",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.tealDark,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Explanation Body (Structured Pedagogical Breakdown)
            if (!question.explanation.isNullOrBlank()) {
                StructuredExplanationContent(
                    rawExplanation = question.explanation,
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
