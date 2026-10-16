package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.McqQuestion
import com.example.ui.theme.ExamTheme

private enum class MasterFilterMode {
    ALL,
    MISSING_KEY,
    MISSING_EXP,
    MISSING_OPTION,
    MODIFIED
}

/**
 * Slide-Over Exam Question Palette & Content Navigator.
 *
 * Tailored specifically for large question sets (100–150+ questions for UPSC, State PCS, JE/AE).
 * - Slides smoothly from the right edge with a backdrop scrim.
 * - Supports Master Navigator mode (editorial health: complete keys, explanations, diffs).
 * - Supports CBT Exam mode (TCS iON standard: Answered, Not Answered, Marked for Review, Not Visited).
 * - Full multi-column vertical grid for effortless one-handed thumb navigation.
 */
@Composable
fun ExamPaletteSideSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    questions: List<McqQuestion>,
    currentQuestionNum: Int? = null,
    // Master View options:
    paperTitle: String = "Question Paper",
    setNumber: String = "Set A",
    isEditMode: Boolean = false,
    modifiedQuestionNums: Set<Int> = emptySet(),
    onQuestionClick: (questionIndex: Int, questionNum: Int) -> Unit,
    onScrollToTop: (() -> Unit)? = null,
    onScrollToAnswerKey: (() -> Unit)? = null,
    // CBT Exam options:
    isCbtMode: Boolean = false,
    answers: Map<Int, String> = emptyMap(),
    markedForReview: Set<Int> = emptySet(),
    visitedQuestions: Set<Int> = emptySet(),
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val colors = ExamTheme.colors
    BackHandler(enabled = isOpen) { onDismiss() }

    var masterFilter by remember { mutableStateOf(MasterFilterMode.ALL) }

    val filteredQuestionsWithOriginalIndex = remember(questions, masterFilter, modifiedQuestionNums) {
        questions.mapIndexed { idx, q -> Pair(idx, q) }.filter { (_, q) ->
            when (masterFilter) {
                MasterFilterMode.ALL -> true
                MasterFilterMode.MISSING_KEY -> q.correctAnswer.isNullOrBlank()
                MasterFilterMode.MISSING_EXP -> q.explanation.isNullOrBlank()
                MasterFilterMode.MISSING_OPTION -> q.options.hasMissingOptions
                MasterFilterMode.MODIFIED -> modifiedQuestionNums.contains(q.num)
            }
        }
    }

    // Stats calculations
    val totalCount = questions.size
    val missingKeyCount = remember(questions) { questions.count { it.correctAnswer.isNullOrBlank() } }
    val missingExpCount = remember(questions) { questions.count { it.explanation.isNullOrBlank() } }
    val missingOptionCount = remember(questions) { questions.count { it.options.hasMissingOptions } }
    val completeCount = remember(questions) { questions.count { !it.correctAnswer.isNullOrBlank() && !it.explanation.isNullOrBlank() } }

    val answeredCount = remember(questions, answers) { questions.count { answers.containsKey(it.num) } }
    val markedCount = remember(questions, markedForReview) { questions.count { markedForReview.contains(it.num) } }
    val visitedCount = remember(questions, visitedQuestions) { questions.count { visitedQuestions.contains(it.num) } }
    val notAnsweredCount = (visitedCount - answeredCount).coerceAtLeast(0)
    val notVisitedCount = (totalCount - visitedCount).coerceAtLeast(0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("exam_palette_side_sheet_container")
        ) {
        // Scrim backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        // Slide-out panel aligned to the right edge
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 16.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .widthIn(min = 300.dp, max = 360.dp)
                .fillMaxWidth(0.86f)
                .fillMaxHeight()
                .testTag("exam_palette_side_sheet_panel")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = colors.teal.copy(alpha = 0.15f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isCbtMode) Icons.Default.Quiz else Icons.Default.FilterList,
                                        contentDescription = null,
                                        tint = colors.teal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCbtMode) "Exam Question Palette" else "Question Navigator",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }
                        Text(
                            text = if (isCbtMode) "$totalCount Questions • TCS iON CBT Mode" else "$setNumber • $totalCount Questions",
                            fontSize = 11.5.sp,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 36.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("side_palette_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Palette",
                            tint = colors.muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.border, thickness = 0.6.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Mode-specific Overview / Legend Section
                if (isCbtMode) {
                    // CBT Exam Legend & Real-Time Counters (TCS iON Standard)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CbtLegendPill(
                                color = Color(0xFF10B981),
                                label = "Answered",
                                count = answeredCount,
                                colors = colors
                            )
                            CbtLegendPill(
                                color = Color(0xFFEF4444),
                                label = "Not Answered",
                                count = notAnsweredCount,
                                colors = colors
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CbtLegendPill(
                                color = Color(0xFF8B5CF6),
                                label = "Review",
                                count = markedCount,
                                colors = colors
                            )
                            CbtLegendPill(
                                color = Color(0xFF94A3B8),
                                label = "Not Visited",
                                count = notVisitedCount,
                                colors = colors
                            )
                        }
                    }
                } else {
                    // Master View Diagnostic Filter Chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = masterFilter == MasterFilterMode.ALL,
                                onClick = { masterFilter = MasterFilterMode.ALL },
                                label = { Text("All ($totalCount)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.teal.copy(alpha = 0.15f),
                                    selectedLabelColor = colors.tealDark
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = masterFilter == MasterFilterMode.MISSING_KEY,
                                onClick = { masterFilter = MasterFilterMode.MISSING_KEY },
                                label = { Text("No Key ($missingKeyCount)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFEE2E2),
                                    selectedLabelColor = Color(0xFFDC2626)
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = masterFilter == MasterFilterMode.MISSING_EXP,
                                onClick = { masterFilter = MasterFilterMode.MISSING_EXP },
                                label = { Text("No Exp ($missingExpCount)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFEF3C7),
                                    selectedLabelColor = Color(0xFFD97706)
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = masterFilter == MasterFilterMode.MISSING_OPTION,
                                onClick = { masterFilter = MasterFilterMode.MISSING_OPTION },
                                label = { Text("No Option ($missingOptionCount)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.amberLight,
                                    selectedLabelColor = colors.amber
                                )
                            )
                        }
                        if (isEditMode && modifiedQuestionNums.isNotEmpty()) {
                            item {
                                FilterChip(
                                    selected = masterFilter == MasterFilterMode.MODIFIED,
                                    onClick = { masterFilter = MasterFilterMode.MODIFIED },
                                    label = { Text("Edited (${modifiedQuestionNums.size})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.amberLight,
                                        selectedLabelColor = colors.amber
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Master View Quality Status Legend
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ready", fontSize = 10.5.sp, color = colors.muted)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFF59E0B), CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("No Exp", fontSize = 10.5.sp, color = colors.muted)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFEF4444), CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("No Key", fontSize = 10.5.sp, color = colors.muted)
                        }
                        if (isEditMode) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(colors.amber, CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edited", fontSize = 10.5.sp, color = colors.muted)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.border, thickness = 0.6.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Multi-Column Vertical Question Matrix (4-5 Columns)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("side_palette_grid")
                ) {
                    itemsIndexed(filteredQuestionsWithOriginalIndex) { _, (origIdx, q) ->
                        val isCurrent = currentQuestionNum == q.num

                        val (bubbleBg, bubbleBorder, bubbleTextColor) = if (isCbtMode) {
                            // CBT State
                            val isAnswered = answers.containsKey(q.num)
                            val isMarked = markedForReview.contains(q.num)
                            val isVisited = visitedQuestions.contains(q.num)

                            when {
                                isAnswered -> Triple(
                                    colors.badgeSuccessBg,
                                    colors.green,
                                    colors.badgeSuccessText
                                )
                                isMarked -> Triple(
                                    if (colors.isDark) Color(0xFF3B1D54) else Color(0xFFF3E8FF),
                                    if (colors.isDark) Color(0xFFA855F7) else Color(0xFF9333EA),
                                    if (colors.isDark) Color(0xFFD8B4FE) else Color(0xFF6B21A8)
                                )
                                isVisited -> Triple(
                                    colors.badgeDangerBg,
                                    colors.red,
                                    colors.badgeDangerText
                                )
                                else -> Triple(
                                    colors.surfaceVariant,
                                    colors.border,
                                    colors.muted
                                )
                            }
                        } else {
                            // Master Editorial State
                            val hasKey = !q.correctAnswer.isNullOrBlank()
                            val hasExp = !q.explanation.isNullOrBlank()
                            val hasMissingOption = q.options.hasMissingOptions
                            val isModified = modifiedQuestionNums.contains(q.num)

                            when {
                                isModified -> Triple(
                                    colors.badgeWarningBg,
                                    colors.amber,
                                    colors.badgeWarningText
                                )
                                !hasKey -> Triple(
                                    colors.badgeDangerBg,
                                    colors.red,
                                    colors.badgeDangerText
                                )
                                hasMissingOption -> Triple(
                                    colors.badgeWarningBg,
                                    colors.amber,
                                    colors.badgeWarningText
                                )
                                !hasExp -> Triple(
                                    colors.badgeWarningBg,
                                    colors.amber,
                                    colors.badgeWarningText
                                )
                                else -> Triple(
                                    colors.badgeSuccessBg,
                                    colors.green,
                                    colors.badgeSuccessText
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                onQuestionClick(origIdx, q.num)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = bubbleBg,
                            border = BorderStroke(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) colors.teal else bubbleBorder
                            ),
                            shadowElevation = if (isCurrent) 3.dp else 0.dp,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("side_palette_bubble_${q.num}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${q.num}",
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isCurrent) colors.tealDark else bubbleTextColor
                                )
                            }
                        }
                    }
                }

                // Footer Quick-Jump Actions (Master Mode)
                if (!isCbtMode) {
                    HorizontalDivider(color = colors.border, thickness = 0.6.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onScrollToTop != null) {
                            TextButton(
                                onClick = {
                                    onScrollToTop()
                                    onDismiss()
                                }
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.teal)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Top", fontSize = 12.sp, color = colors.teal, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (onScrollToAnswerKey != null) {
                            TextButton(
                                onClick = {
                                    onScrollToAnswerKey()
                                    onDismiss()
                                }
                            ) {
                                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.inkBold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Answer Key", fontSize = 12.sp, color = colors.inkBold, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun CbtLegendPill(
    color: Color,
    label: String,
    count: Int,
    colors: com.example.ui.theme.ExamColorPalette
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(colors.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$label: $count",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = colors.inkBold
        )
    }
}
