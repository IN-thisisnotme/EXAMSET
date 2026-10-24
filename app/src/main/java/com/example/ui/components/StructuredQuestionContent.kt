package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.McqQuestion
import com.example.ui.theme.ExamTheme
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.util.McqParser
import com.example.util.QuestionFormatter
import com.example.util.RichTextHelper

/**
 * Structured presentation of MCQ questions with consistent typography,
 * enlarged readable font scales, uniform badge widths, and subtle card styling:
 * 1. Assertion–Reason questions (structured dual cards with badges).
 * 2. Multi-line options (stacked full-width rows with uniform 38.dp badges).
 * 3. 2x2 grid for compact single-line options with aligned badges.
 * 4. Diagrams, formulas, and attachments seamlessly integrated.
 */
@Composable
fun StructuredQuestionContent(
    question: McqQuestion,
    showAnswerIndicator: Boolean = false,
    showNumber: Boolean = true,
    showTypeBadge: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val arParts = QuestionFormatter.parseAssertionReason(question.question)

    Column(modifier = modifier) {
        // Question Header: Number + Type Badge (Type badge commented out per user request)
        val qType = if (showTypeBadge) QuestionFormatter.displayType(question) else null
        val shouldShowHeaderRow = showNumber

        if (shouldShowHeaderRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showNumber) {
                    Text(
                        text = "${question.num}.",
                        fontFamily = RobotoMonoFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                }

                // Question type badge (e.g. Conceptual, Numerical, etc.) commented out per user request:
                /*
                if (!qType.isNullOrBlank()) {
                    if (showNumber) {
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            qType.contains("Assertion") -> colors.tealLight
                            qType.contains("Match") -> Color(0xFFFEF3C7).copy(alpha = if (colors.isDark) 0.2f else 1f)
                            qType.contains("Statement") -> Color(0xFFE0E7FF).copy(alpha = if (colors.isDark) 0.2f else 1f)
                            else -> colors.surfaceVariant
                        },
                        border = BorderStroke(
                            0.6.dp,
                            when {
                                qType.contains("Assertion") -> colors.teal.copy(alpha = 0.5f)
                                qType.contains("Match") -> Color(0xFFD97706).copy(alpha = 0.5f)
                                qType.contains("Statement") -> Color(0xFF6366F1).copy(alpha = 0.5f)
                                else -> colors.border
                            }
                        )
                    ) {
                        Text(
                            text = qType,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                qType.contains("Assertion") -> colors.tealDark
                                qType.contains("Match") -> if (colors.isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
                                qType.contains("Statement") -> if (colors.isDark) Color(0xFFA5B4FC) else Color(0xFF4338CA)
                                else -> colors.ink
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                */
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Question Body
        if (arParts != null) {
            AssertionReasonView(parts = arParts)
        } else {
            StandardQuestionBody(questionText = question.question)
        }

        // Attached Diagrams, Charts, and Formulas
        if (!question.diagramUrl.isNullOrBlank() || !question.formula.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            QuestionAttachmentView(question = question)
        }

        Spacer(modifier = Modifier.height(12.dp))

        val hasAnyOptions = question.options.asList.any { it.second.isNotBlank() }
        val hasMissingOptions = question.options.hasMissingOptions
        if (hasAnyOptions || hasMissingOptions) {
            val shouldStack = QuestionFormatter.shouldStackOptions(question) || hasMissingOptions
            if (shouldStack) {
                StackedOptionsList(
                    question = question,
                    showAnswerIndicator = showAnswerIndicator
                )
            } else {
                GridOptionsLayout(
                    question = question,
                    showAnswerIndicator = showAnswerIndicator
                )
            }
        } else if (showAnswerIndicator && !question.correctAnswer.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.greenLight,
                border = BorderStroke(1.dp, colors.green),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Official Solution / Final Key:",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.green
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LatexText(
                        text = question.correctAnswer,
                        fontSize = 15.sp,
                        color = colors.inkBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AssertionReasonView(parts: QuestionFormatter.AssertionReasonParts) {
    val colors = ExamTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!parts.leadIn.isNullOrBlank()) {
            LatexText(
                text = parts.leadIn,
                color = colors.inkBold,
                fontSize = 16.sp,
                isBold = false,
                lineHeight = 24.sp
            )
        }

        // Assertion Box
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = colors.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(0.8.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.teal.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, colors.teal)
                ) {
                    Text(
                        text = "Assertion (A)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.tealDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                LatexText(
                    text = parts.assertion,
                    color = colors.inkBold,
                    fontSize = 16.sp,
                    isBold = false,
                    lineHeight = 24.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Reason Box
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = colors.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(0.8.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFD97706).copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, Color(0xFFD97706))
                ) {
                    Text(
                        text = "Reason (R)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (colors.isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                LatexText(
                    text = parts.reason,
                    color = colors.inkBold,
                    fontSize = 16.sp,
                    isBold = false,
                    lineHeight = 24.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (!parts.leadOut.isNullOrBlank()) {
            LatexText(
                text = parts.leadOut,
                color = colors.ink,
                fontSize = 15.5.sp,
                isBold = false,
                lineHeight = 23.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun StandardQuestionBody(questionText: String) {
    val colors = ExamTheme.colors

    // 1. Check for Dual List Match (List-I & List-II)
    val dualMatch = DualMatchParserHelper.parse(questionText)
    if (dualMatch != null) {
        DualMatchCard(data = dualMatch)
        return
    }

    // 2. Check for Markdown Tables embedded in questionText
    val lines = questionText.lines()
    val tableStartIndex = lines.indexOfFirst { TableParserHelper.isTableRow(it) }

    if (tableStartIndex != -1) {
        // Find end of table block
        var tableEndIndex = tableStartIndex
        while (tableEndIndex < lines.size && (TableParserHelper.isTableRow(lines[tableEndIndex]) || TableParserHelper.isTableSeparator(lines[tableEndIndex]) || lines[tableEndIndex].isBlank())) {
            tableEndIndex++
        }

        // Check if there is an immediately preceding header/title line before table (e.g. ### List-I: ...)
        var title: String? = null
        var preTableEnd = tableStartIndex
        if (tableStartIndex > 0) {
            val prevLine = lines[tableStartIndex - 1].trim()
            if (prevLine.startsWith("#") || prevLine.startsWith("**")) {
                title = prevLine.removePrefix("#").trim()
                preTableEnd = tableStartIndex - 1
            }
        }

        val preTableText = lines.subList(0, preTableEnd).joinToString("\n").trim()
        val tableLines = lines.subList(tableStartIndex, tableEndIndex)
        val postTableText = lines.subList(tableEndIndex, lines.size).joinToString("\n").trim()
        val parsedTable = TableParserHelper.parseTableBlock(tableLines, title)

        if (parsedTable != null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (preTableText.isNotEmpty()) {
                    LatexText(
                        text = preTableText,
                        color = colors.inkBold,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }

                MarkdownTableCard(table = parsedTable)

                if (postTableText.isNotEmpty()) {
                    LatexText(
                        text = postTableText,
                        color = colors.inkBold,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 23.sp
                    )
                }
            }
            return
        }
    }

    // 3. Fallback standard multi-line question body rendered cohesively to preserve multiline LaTeX
    LatexText(
        text = questionText.trim(),
        color = colors.inkBold,
        fontSize = 16.5.sp,
        isBold = false,
        lineHeight = 25.sp
    )
}

@Composable
private fun StackedOptionsList(
    question: McqQuestion,
    showAnswerIndicator: Boolean
) {
    val colors = ExamTheme.colors

    val allOptions = question.options.asList
    if (allOptions.all { it.second.isBlank() }) return

    val canonicalAnswer = question.correctAnswer?.let {
        McqParser.canonicalOption(it) ?: it.trim().uppercase().removeSurrounding("(", ")").removeSurrounding("[", "]").removePrefix("OPTION").trim().removeSuffix(".")
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        allOptions.forEach { (letter, text) ->
            val isBlank = text.isBlank()
            if (isBlank) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.amberLight.copy(alpha = 0.5f),
                    border = BorderStroke(0.8.dp, colors.amber.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.amberLight,
                            border = BorderStroke(0.8.dp, colors.amber),
                            modifier = Modifier.width(34.dp).height(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = letter,
                                    fontFamily = RobotoMonoFontFamily,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.amber,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Missing Option",
                            tint = colors.amber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Option ($letter) is blank or missing",
                            color = colors.amber,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            } else {
                val isCorrect = showAnswerIndicator && (
                    letter.equals(canonicalAnswer, ignoreCase = true) ||
                    question.correctAnswer.equals(letter, ignoreCase = true) ||
                    question.correctAnswer?.split(Regex("[,; ]+"))?.any { token ->
                        val cToken = McqParser.canonicalOption(token) ?: token.trim().uppercase().removeSurrounding("(", ")").removeSurrounding("[", "]")
                        cToken.equals(letter, ignoreCase = true)
                    } == true
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCorrect) colors.greenLight else colors.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(if (isCorrect) 1.dp else 0.8.dp, if (isCorrect) colors.green else colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Uniform Option Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isCorrect) colors.greenLight else colors.tealLight,
                            border = BorderStroke(0.8.dp, if (isCorrect) colors.green else colors.teal),
                            modifier = Modifier.width(34.dp).height(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = letter,
                                    fontFamily = RobotoMonoFontFamily,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCorrect) colors.green else colors.tealDark,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        LatexText(
                            text = text,
                            color = if (isCorrect) colors.green else colors.ink,
                            fontSize = 16.sp,
                            isBold = isCorrect,
                            lineHeight = 24.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridOptionsLayout(
    question: McqQuestion,
    showAnswerIndicator: Boolean
) {
    if (question.options.a.isBlank() && question.options.b.isBlank()) return

    val canonicalAnswer = question.correctAnswer?.let {
        McqParser.canonicalOption(it) ?: it.trim().uppercase().removeSurrounding("(", ")").removeSurrounding("[", "]").removePrefix("OPTION").trim().removeSuffix(".")
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompactOptionItem(
                letter = "A",
                text = question.options.a,
                isCorrect = showAnswerIndicator && (canonicalAnswer == "A" || question.correctAnswer.equals("A", ignoreCase = true)),
                modifier = Modifier.weight(1f)
            )
            CompactOptionItem(
                letter = "B",
                text = question.options.b,
                isCorrect = showAnswerIndicator && (canonicalAnswer == "B" || question.correctAnswer.equals("B", ignoreCase = true)),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompactOptionItem(
                letter = "C",
                text = question.options.c,
                isCorrect = showAnswerIndicator && (canonicalAnswer == "C" || question.correctAnswer.equals("C", ignoreCase = true)),
                modifier = Modifier.weight(1f)
            )
            CompactOptionItem(
                letter = "D",
                text = question.options.d,
                isCorrect = showAnswerIndicator && (canonicalAnswer == "D" || question.correctAnswer.equals("D", ignoreCase = true)),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompactOptionItem(
    letter: String,
    text: String,
    isCorrect: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val isBlank = text.isBlank()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = when {
                isCorrect -> colors.greenLight
                isBlank -> colors.amberLight
                else -> colors.tealLight
            },
            border = BorderStroke(0.6.dp, when {
                isCorrect -> colors.green
                isBlank -> colors.amber
                else -> colors.teal
            }),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = letter,
                    fontFamily = RobotoMonoFontFamily,
                    color = when {
                        isCorrect -> colors.green
                        isBlank -> colors.amber
                        else -> colors.tealDark
                    },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isBlank) {
            Text(
                text = "[Missing]",
                color = colors.amber,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic
            )
        } else {
            LatexText(
                text = text,
                color = if (isCorrect) colors.green else colors.ink,
                fontSize = 15.5.sp,
                isBold = isCorrect,
                lineHeight = 23.sp
            )
        }
    }
}

typealias AssertionReasonParts = QuestionFormatter.AssertionReasonParts

fun parseAssertionReason(text: String): AssertionReasonParts? =
    QuestionFormatter.parseAssertionReason(text)
