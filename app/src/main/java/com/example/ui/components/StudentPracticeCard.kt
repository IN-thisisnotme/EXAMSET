package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.McqQuestion
import com.example.model.MsqScoringPolicy
import com.example.ui.theme.ExamTheme
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.util.AnswerKeyMatcher
import com.example.util.QuestionFormatter
import com.example.util.RichTextHelper
import com.example.util.rememberProcessedQuestion

private data class ShuffledOptionDisplayItem(
    val displayLabel: String,
    val originalKey: String,
    val text: String
)

/**
 * Detects whether any option in the question contains cross-referencing or positional language
 * (e.g. "both B and C", "A and B only", "either A or B", "all of the above", "statements 1 and 2").
 * When present, shuffling options is safely bypassed to avoid logical contradictions.
 */
internal fun hasCompoundOrPositionalReference(options: List<Pair<String, String>>): Boolean {
    val positionalRegex = Regex("(?i)\\b(all|none|both|neither|any)\\s+(of\\s+)?(the\\s+)?(above|below)\\b|(?i)\\b(all|none|any)\\s+(of\\s+)?these\\b")
    val compoundLetterRegex = Regex("(?i)\\b(both|either|neither|only)\\s+(options?\\s+)?\\(?[a-e]\\)?\\s*(and|&|or|nor)\\s*\\(?[a-e]\\)?\\b|(?i)\\b\\(?[a-e]\\)?\\s*(and|&|or)\\s*\\(?[a-e]\\)?\\s+(only|both)\\b|(?i)\\boptions?\\s+\\(?[a-e]\\)?\\s*(and|&|,)\\s*\\(?[a-e]\\)?\\b|(?i)\\ball\\s+(of\\s+)?\\(?[a-e]\\)?\\s*,\\s*\\(?[a-e]\\)?\\s*(and|&)\\s*\\(?[a-e]\\)?\\b")
    val exactCombinationRegex = Regex("(?i)^\\s*\\(?[a-e]\\)?\\s*(and|&|,)\\s*\\(?[a-e]\\)?(\\s*(and|&)\\s*\\(?[a-e]\\)?)?\\s*$")
    val statementNumberRegex = Regex("(?i)\\b(statements?|options?)\\s+\\(?([1-5]|i{1,3}|iv|v)\\)?\\b|(?i)\\b(both|either|neither|only)\\s+\\(?([1-5]|i{1,3}|iv|v)\\)?\\s*(and|&|or|nor)\\s*\\(?([1-5]|i{1,3}|iv|v)\\)?\\b|(?i)\\(?([1-5]|i{1,3}|iv|v)\\)?\\s*(and|&|or)\\s*\\(?([1-5]|i{1,3}|iv|v)\\)?\\s+(only|both)\\b|(?i)^\\s*\\(?([1-5]|i{1,3}|iv|v)\\)?\\s*(and|&|,)\\s*\\(?([1-5]|i{1,3}|iv|v)\\)?\\s*$")

    return options.any { (_, text) ->
        val trimmed = text.trim()
        positionalRegex.containsMatchIn(trimmed) ||
        compoundLetterRegex.containsMatchIn(trimmed) ||
        exactCombinationRegex.matches(trimmed) ||
        statementNumberRegex.containsMatchIn(trimmed)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentPracticeCard(
    question: McqQuestion,
    selectedAnswer: String?,
    onSelectAnswer: (String) -> Unit,
    showResult: Boolean,
    modifier: Modifier = Modifier,
    shuffleOptions: Boolean = false,
    sessionSeed: Long = 0L,
    positiveMarks: Double = 4.0,
    negativeMarks: Double = 1.0,
    unattendedMarks: Double = 0.0,
    msqScoringPolicy: MsqScoringPolicy = MsqScoringPolicy.ALL_OR_NOTHING
) {
    val colors = ExamTheme.colors
    val haptic = LocalHapticFeedback.current
    val q = rememberProcessedQuestion(question)
    val isMsq = remember(q) { AnswerKeyMatcher.isMultiSelect(q) }
    val selectedLetters = remember(selectedAnswer) { AnswerKeyMatcher.parseSelectedLetters(selectedAnswer) }
    val expectedLetters = remember(q.correctAnswer) { AnswerKeyMatcher.parseSelectedLetters(q.correctAnswer) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("practice_card_${q.num}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.8.dp, colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Question number, MSQ highlight (only when MSQ), & result status badge in font-scale-safe FlowRow
            val arParts = QuestionFormatter.parseAssertionReason(q.question)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Question ${q.num}",
                    fontFamily = RobotoMonoFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )

                if (isMsq) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.amberLight,
                        border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("msq_question_badge_${q.num}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckBox,
                                contentDescription = null,
                                tint = colors.amber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "MSQ • Select all that apply",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.amber,
                                softWrap = true
                            )
                        }
                    }
                }

                if (!showResult && isMsq && selectedLetters.isNotEmpty()) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectAnswer("")
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(0.9.dp, colors.border),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("msq_clear_button_${q.num}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear MSQ Selection",
                                tint = colors.muted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear (${selectedLetters.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.ink,
                                softWrap = true
                            )
                        }
                    }
                }

                if (showResult) {
                    val gradeResult = AnswerKeyMatcher.scoreQuestion(
                        question = q,
                        userAnswer = selectedAnswer,
                        positiveMarks = positiveMarks,
                        negativeMarks = negativeMarks,
                        unattendedMarks = unattendedMarks,
                        msqPolicy = msqScoringPolicy
                    )
                    val posStr = String.format(java.util.Locale.US, "%.2f", positiveMarks)
                    val negStr = String.format(java.util.Locale.US, "%.2f", negativeMarks)
                    val unkBadgeLabel = if (unattendedMarks > 0.0) {
                        "— Unattended (-${String.format(java.util.Locale.US, "%.2f", unattendedMarks)})"
                    } else {
                        "— Unattended (0.00)"
                    }
                    val (badgeText, badgeBg, badgeTextCol) = when (gradeResult.status) {
                        AnswerKeyMatcher.QuestionGradeStatus.NEUTRALIZED ->
                            Triple("★ Bonus Marks (+$posStr)", colors.teal.copy(alpha = 0.15f), colors.teal)
                        AnswerKeyMatcher.QuestionGradeStatus.CORRECT ->
                            Triple("✓ Correct (+$posStr)", colors.greenLight, colors.green)
                        AnswerKeyMatcher.QuestionGradeStatus.PARTIAL ->
                            Triple(
                                "~ Partial (+${String.format(java.util.Locale.US, "%.2f", gradeResult.marks)})",
                                colors.amberLight,
                                colors.amber
                            )
                        AnswerKeyMatcher.QuestionGradeStatus.INCORRECT ->
                            Triple("✗ Incorrect (-$negStr)", colors.redLight, colors.red)
                        AnswerKeyMatcher.QuestionGradeStatus.UNANSWERED ->
                            Triple(unkBadgeLabel, colors.borderLight, colors.muted)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeBg,
                        border = BorderStroke(1.dp, badgeTextCol.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("practice_result_badge_${q.num}")
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeTextCol,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            softWrap = true,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Question Body: Assertion-Reason structured cards OR standard text
            if (arParts != null) {
                if (!arParts.leadIn.isNullOrBlank()) {
                    Text(
                        text = RichTextHelper.buildRichText(
                            text = arParts.leadIn,
                            baseColor = colors.inkBold,
                            baseFontSize = 16.sp,
                            isBold = false
                        ),
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

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
                        Text(
                            text = RichTextHelper.buildRichText(
                                text = arParts.assertion,
                                baseColor = colors.inkBold,
                                baseFontSize = 16.sp,
                                isBold = false
                            ),
                            lineHeight = 24.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                            text = arParts.reason,
                            color = colors.inkBold,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (!arParts.leadOut.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LatexText(
                        text = arParts.leadOut,
                        color = colors.ink,
                        fontSize = 15.5.sp,
                        lineHeight = 23.sp
                    )
                }
            } else {
                val qLines = q.question.lines()
                if (qLines.size <= 1) {
                    LatexText(
                        text = q.question,
                        color = colors.inkBold,
                        fontSize = 16.5.sp,
                        isBold = true,
                        lineHeight = 25.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        qLines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) {
                                LatexText(
                                    text = trimmed,
                                    color = colors.inkBold,
                                    fontSize = 16.5.sp,
                                    lineHeight = 25.sp
                                )
                            }
                        }
                    }
                }
            }

            // Attached Diagrams, Charts, and Formulas
            if (!q.diagramUrl.isNullOrBlank() || !q.formula.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                QuestionAttachmentView(question = q)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Option items with uniform badge widths and semantic color indicators
            val rawOptions = q.options.asList.filter { it.second.isNotBlank() }
            val optionsToRender = remember(q.num, q.options, shuffleOptions, sessionSeed, showResult) {
                if (shuffleOptions && !showResult && rawOptions.size >= 2) {
                    val hasPositionalReference = hasCompoundOrPositionalReference(rawOptions)
                    if (hasPositionalReference) {
                        // Preserve original order to avoid logical contradictions
                        rawOptions.map { (key, text) ->
                            ShuffledOptionDisplayItem(displayLabel = key, originalKey = key, text = text)
                        }
                    } else {
                        // Deterministic shuffle for this question in this session
                        val random = java.util.Random(sessionSeed + q.num * 37L)
                        val shuffled = rawOptions.shuffled(random)
                        shuffled.mapIndexed { idx, (origKey, text) ->
                            val displayLetter = ('A' + idx).toString()
                            ShuffledOptionDisplayItem(displayLabel = displayLetter, originalKey = origKey, text = text)
                        }
                    }
                } else {
                    rawOptions.map { (key, text) ->
                        ShuffledOptionDisplayItem(displayLabel = key, originalKey = key, text = text)
                    }
                }
            }

            if (optionsToRender.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                optionsToRender.forEach { item ->
                    val optionUpper = item.originalKey.uppercase()
                    val isSelected = if (isMsq) {
                        optionUpper in selectedLetters
                    } else {
                        selectedAnswer.equals(item.originalKey, ignoreCase = true) || optionUpper in selectedLetters
                    }
                    val isCorrect = optionUpper in expectedLetters || q.correctAnswer.equals(item.originalKey, ignoreCase = true)
                    val isMissedCorrect = showResult && isCorrect && !isSelected
                    val isChosenCorrect = showResult && isCorrect && isSelected

                    val (bgColor, borderColor, textColor) = when {
                        showResult && isChosenCorrect -> Triple(colors.greenLight, colors.green, colors.green)
                        showResult && isMissedCorrect -> Triple(
                            if (isMsq) colors.greenLight.copy(alpha = 0.55f) else colors.greenLight,
                            colors.green,
                            colors.green
                        )
                        showResult && isSelected && !isCorrect -> Triple(colors.redLight, colors.red, colors.red)
                        isSelected -> Triple(colors.tealLight, colors.teal, colors.teal)
                        else -> Triple(Color.Transparent, colors.border, colors.ink)
                    }

                    val indicatorShape = if (isMsq) RoundedCornerShape(6.dp) else CircleShape

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = !showResult) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isMsq) {
                                    onSelectAnswer(AnswerKeyMatcher.toggleSelection(selectedAnswer, item.originalKey))
                                } else {
                                    if (isSelected) onSelectAnswer("") else onSelectAnswer(item.originalKey)
                                }
                            }
                            .testTag("practice_option_${item.originalKey}_q${q.num}"),
                        shape = RoundedCornerShape(8.dp),
                        color = bgColor,
                        border = BorderStroke(if (isSelected || (showResult && isCorrect)) 1.2.dp else 0.8.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Circle badge for single-choice MCQ; rounded-square checkbox for MSQ
                            Surface(
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(30.dp),
                                shape = indicatorShape,
                                color = when {
                                    showResult && isMissedCorrect && isMsq -> Color.Transparent
                                    isSelected || (showResult && isCorrect) -> borderColor
                                    else -> Color.Transparent
                                },
                                border = BorderStroke(1.2.dp, borderColor)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    if (showResult && isCorrect) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = if (isMissedCorrect && isMsq) "Missed Correct" else "Correct",
                                            tint = if (isMissedCorrect && isMsq) colors.green else (if (colors.isDark) Color.Black else Color.White),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else if (showResult && isSelected && !isCorrect) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Incorrect",
                                            tint = if (colors.isDark) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else if (isMsq && isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected ${item.displayLabel}",
                                            tint = if (colors.isDark) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Text(
                                            text = item.displayLabel,
                                            fontFamily = RobotoMonoFontFamily,
                                            color = if (isSelected) (if (colors.isDark) Color.Black else Color.White) else colors.muted,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                LatexText(
                                    text = item.text,
                                    color = textColor,
                                    fontSize = 16.sp,
                                    isBold = isSelected || (showResult && isCorrect),
                                    lineHeight = 24.sp
                                )
                                if (showResult && isMsq) {
                                    val stateHint = when {
                                        isChosenCorrect -> "Selected • Correct"
                                        isMissedCorrect -> "Correct (Missed)"
                                        isSelected && !isCorrect -> "Selected • Wrong"
                                        else -> null
                                    }
                                    if (stateHint != null) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = stateHint,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isCorrect) colors.green else colors.red
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(0.8.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Subjective / Multi-Stage Problem",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.tealDark
                            )
                        }
                        if (showResult && !q.correctAnswer.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Key / Solution Summary:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.green
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LatexText(
                                text = q.correctAnswer,
                                fontSize = 15.sp,
                                color = colors.inkBold
                            )
                        }
                    }
                }
            }

            // Reveal explanation in result mode if available
            if (showResult && !q.explanation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(0.8.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                text = "EXPLANATION",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.tealDark,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        StructuredExplanationContent(
                            rawExplanation = q.explanation,
                            correctLetter = AnswerKeyMatcher.normalizeKey(q.correctAnswer).ifBlank { q.correctAnswer },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
