package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StudyNoteEntity
import com.example.model.FlashcardSrsState
import com.example.model.FlashcardSrsStatus
import com.example.model.SrsRating
import com.example.ui.theme.ExamTheme
import com.example.util.SpacedRepetitionEngine
import com.example.util.StudyNoteBlock
import com.example.util.TrustedTimeManager
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlashcardSpacedDeckView(
    note: StudyNoteEntity,
    deckItems: List<StudyNoteBlock>,
    srsMap: Map<Int, FlashcardSrsState>,
    bookmarked: Set<Int>,
    mastered: Set<Int>,
    recallMode: Boolean,
    maskMode: RecallMaskMode,
    flipPair: Boolean,
    showTypeBadges: Boolean,
    showOptionsBeforeReveal: Boolean,
    revealedIndices: Set<Int>,
    selfSelectedOptions: Map<Int, String>,
    linkedPaperTitle: String?,
    onOpenLinkedPaper: ((Long) -> Unit)?,
    onRevealBlock: (Int) -> Unit,
    onHideBlock: (Int) -> Unit,
    onSelectSelfOption: (Int, String) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onToggleMastered: (Int) -> Unit,
    onRateFlashcard: (Int, SrsRating) -> Unit,
    onResetSrs: () -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors

    var cramAll by remember(note.id) { mutableStateOf(false) }
    var shuffleSeed by remember(note.id) { mutableIntStateOf(0) }
    var sessionGeneration by remember(note.id) { mutableIntStateOf(0) }
    var sessionNowMillis by remember(note.id, sessionGeneration) {
        mutableLongStateOf(TrustedTimeManager.getTrustedTimeMillis())
    }

    var sessionQueue by remember(note.id) { mutableStateOf<List<StudyNoteBlock>>(emptyList()) }
    var queueIndex by remember(note.id) { mutableIntStateOf(0) }
    var hintVisible by remember(note.id) { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    var reviewedCount by remember(note.id) { mutableIntStateOf(0) }
    var againCount by remember(note.id) { mutableIntStateOf(0) }
    var hardCount by remember(note.id) { mutableIntStateOf(0) }
    var goodCount by remember(note.id) { mutableIntStateOf(0) }
    var easyCount by remember(note.id) { mutableIntStateOf(0) }

    val stats = remember(deckItems, srsMap, sessionNowMillis) {
        SpacedRepetitionEngine.computeDeckStats(deckItems, srsMap, sessionNowMillis)
    }

    // Rebuild session queue only when deck filter composition, cram toggle, shuffle, or explicit restart changes
    // (NOT on every individual rating, so Again re-insertion stays intact within the session).
    LaunchedEffect(note.id, deckItems, cramAll, shuffleSeed, sessionGeneration) {
        val now = TrustedTimeManager.getTrustedTimeMillis()
        sessionNowMillis = now
        sessionQueue = SpacedRepetitionEngine.buildStudyQueue(
            allCards = deckItems,
            srsMap = srsMap,
            nowMillis = now,
            cramAll = cramAll,
            prioritizeDue = true,
            shuffleSeed = shuffleSeed
        )
        queueIndex = 0
        hintVisible = false
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Spaced Repetition?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "Clear all SM-2 intervals, ease factors, and due timers for \"${note.title}\"? Every card will become New.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetSrs()
                        reviewedCount = 0
                        againCount = 0
                        hardCount = 0
                        goodCount = 0
                        easyCount = 0
                        sessionGeneration++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red, contentColor = Color.White)
                ) {
                    Text("Reset Progress")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // SRS Telemetry & Queue Controls Strip
        Surface(
            color = colors.surface,
            border = BorderStroke(0.6.dp, colors.border.copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SrsMetricBadge(
                        label = "Due",
                        value = "${stats.dueCount}",
                        containerColor = if (stats.dueCount > 0) colors.amberLight else colors.surfaceVariant,
                        textColor = if (stats.dueCount > 0) colors.amber else colors.inkMuted
                    )
                    SrsMetricBadge(
                        label = "New",
                        value = "${stats.newCount}",
                        containerColor = if (stats.newCount > 0) colors.blueLight else colors.surfaceVariant,
                        textColor = if (stats.newCount > 0) colors.tealDark else colors.inkMuted
                    )
                    SrsMetricBadge(
                        label = "Mastered",
                        value = "${stats.masteredCount}/${stats.totalCount}",
                        containerColor = colors.greenLight,
                        textColor = colors.green
                    )
                    if (stats.retentionPercent > 0 || reviewedCount > 0) {
                        val displayRetention = if (reviewedCount > 0) {
                            (((reviewedCount - againCount).toFloat() / reviewedCount.toFloat()) * 100f)
                                .toInt()
                                .coerceIn(0, 100)
                        } else {
                            stats.retentionPercent
                        }
                        SrsMetricBadge(
                            label = "Retention",
                            value = "$displayRetention%",
                            containerColor = colors.tealLight,
                            textColor = colors.tealDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = cramAll,
                        onClick = { cramAll = !cramAll },
                        leadingIcon = { Icon(Icons.Default.AllInclusive, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = {
                            Text(
                                text = if (cramAll) "Cram All ON" else "Practice All (Cram)",
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.tealLight,
                            selectedLabelColor = colors.tealDark
                        ),
                        modifier = Modifier.testTag("srs_cram_toggle_chip")
                    )

                    FilterChip(
                        selected = shuffleSeed != 0,
                        onClick = {
                            shuffleSeed = if (shuffleSeed == 0) (System.currentTimeMillis() % 100_000L).toInt().coerceAtLeast(1) else 0
                        },
                        leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = {
                            Text(
                                text = if (shuffleSeed != 0) "Shuffled" else "Shuffle",
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.blueLight,
                            selectedLabelColor = colors.tealDark
                        ),
                        modifier = Modifier.testTag("srs_shuffle_chip")
                    )

                    if (srsMap.isNotEmpty()) {
                        FilterChip(
                            selected = false,
                            onClick = { showResetConfirm = true },
                            leadingIcon = { Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = {
                                Text(
                                    text = "Reset SRS",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.testTag("srs_reset_button")
                        )
                    }
                }
            }
        }

        if (deckItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No study cards match the active filters.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onResetFilters,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Reset Filters")
                    }
                }
            }
        } else if (sessionQueue.isEmpty() || queueIndex >= sessionQueue.size) {
            // Session Complete or All Caught Up Card
            val sessionRetention = if (reviewedCount > 0) {
                (((reviewedCount - againCount).toFloat() / reviewedCount.toFloat()) * 100f).toInt().coerceIn(0, 100)
            } else {
                stats.retentionPercent
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.green.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("srs_session_complete_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.green,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (reviewedCount > 0) "Session Complete!" else "All Due Cards Caught Up!",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.inkBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (reviewedCount > 0) {
                                "Reviewed $reviewedCount card${if (reviewedCount == 1) "" else "s"} • $sessionRetention% session recall"
                            } else {
                                "Every card in this deck has been scheduled ahead by SM-2. Use Cram All to practice anyway."
                            },
                            fontSize = 12.5.sp,
                            color = colors.inkMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        if (reviewedCount > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SrsMetricBadge("Again", "$againCount", colors.redLight, colors.red)
                                SrsMetricBadge("Hard", "$hardCount", colors.amberLight, colors.amber)
                                SrsMetricBadge("Good", "$goodCount", colors.tealLight, colors.tealDark)
                                SrsMetricBadge("Easy", "$easyCount", colors.greenLight, colors.green)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        val masteredFraction = if (stats.totalCount > 0) {
                            stats.masteredCount.toFloat() / stats.totalCount.toFloat()
                        } else 0f
                        LinearProgressIndicator(
                            progress = { masteredFraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = colors.green,
                            trackColor = colors.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Deck Mastery: ${stats.masteredCount} / ${stats.totalCount} cards",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkMuted
                        )

                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { sessionGeneration++ },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("srs_review_again_button")
                            ) {
                                Text(
                                    text = if (cramAll) "Restart Cram" else "Refresh Queue",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            if (!cramAll) {
                                Button(
                                    onClick = {
                                        cramAll = true
                                        sessionGeneration++
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("srs_start_cram_button")
                                ) {
                                    Text(
                                        text = "Cram All Cards",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val safeQueueIndex = queueIndex.coerceIn(0, sessionQueue.lastIndex)
            val activeBlock = sessionQueue[safeQueueIndex]
            val cardState = srsMap[activeBlock.blockIndex]
            val cardStatus = remember(cardState, sessionNowMillis) {
                SpacedRepetitionEngine.statusOf(cardState, sessionNowMillis)
            }
            val nextIntervalLabels = remember(cardState, sessionNowMillis) {
                SpacedRepetitionEngine.previewNextIntervalLabels(cardState, sessionNowMillis)
            }

            val isStarred = activeBlock.blockIndex in bookmarked
            val isMastered = activeBlock.blockIndex in mastered || SpacedRepetitionEngine.isMasteredBySrs(cardState)
            val isRecallable = (activeBlock is StudyNoteBlock.OneLiner && activeBlock.hasRecallTarget) ||
                activeBlock is StudyNoteBlock.MultiLinerCard
            val isHiddenRecall = recallMode && isRecallable && activeBlock.blockIndex !in revealedIndices

            val cardHintText = remember(activeBlock) { extractCardHint(activeBlock) }

            // Queue progress header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${if (cramAll) "Cram Deck" else "SRS Queue"} • Card ${safeQueueIndex + 1} of ${sessionQueue.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.tealDark
                )
                Text(
                    text = if (recallMode) maskMode.shortLabel else "Full Study View",
                    fontSize = 11.sp,
                    color = colors.inkMuted
                )
            }
            LinearProgressIndicator(
                progress = { (safeQueueIndex + 1).toFloat() / sessionQueue.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = colors.teal,
                trackColor = colors.border.copy(alpha = 0.4f)
            )

            // Active Card Body (scrollable for deep/LaTeX answers)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!linkedPaperTitle.isNullOrBlank() && note.linkedPaperId != null && onOpenLinkedPaper != null) {
                    Surface(
                        onClick = { onOpenLinkedPaper(note.linkedPaperId) },
                        shape = RoundedCornerShape(10.dp),
                        color = colors.blueLight,
                        border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = colors.teal, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Linked Question Set: $linkedPaperTitle",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.tealDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open Set →",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Individual Card SM-2 Telemetry + Hint Peek Row
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(0.6.dp, colors.border.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val (statusBg, statusFg) = when (cardStatus) {
                                FlashcardSrsStatus.NEW -> colors.blueLight to colors.tealDark
                                FlashcardSrsStatus.DUE -> colors.amberLight to colors.amber
                                FlashcardSrsStatus.LEARNING -> colors.tealLight to colors.tealDark
                                FlashcardSrsStatus.MASTERED -> colors.greenLight to colors.green
                            }
                            val statusText = buildString {
                                append(cardStatus.label)
                                if (cardState != null && cardState.intervalDays > 0) {
                                    append(" • ")
                                    append(SpacedRepetitionEngine.formatInterval(cardState.intervalDays, cardState.lastRating))
                                }
                            }
                            Surface(shape = RoundedCornerShape(5.dp), color = statusBg) {
                                Text(
                                    text = statusText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusFg,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            val ease = cardState?.easeFactor ?: SpacedRepetitionEngine.DEFAULT_EASE_FACTOR
                            Surface(shape = RoundedCornerShape(5.dp), color = colors.surface) {
                                Text(
                                    text = String.format(Locale.US, "Ease %.2f", ease),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkMuted,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if ((cardState?.lapses ?: 0) > 0) {
                                Surface(shape = RoundedCornerShape(5.dp), color = colors.redLight) {
                                    Text(
                                        text = "${cardState?.lapses} lapse${if (cardState?.lapses == 1) "" else "s"}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.red,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (isHiddenRecall && cardHintText.isNotBlank()) {
                            TextButton(
                                onClick = { hintVisible = !hintVisible },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag("srs_hint_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = "Toggle hint",
                                    tint = colors.amber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (hintVisible) "Hide Hint" else "Hint",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.amber
                                )
                            }
                        } else if (!isHiddenRecall && recallMode && isRecallable) {
                            TextButton(
                                onClick = {
                                    hintVisible = false
                                    onHideBlock(activeBlock.blockIndex)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.heightIn(min = 36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = "Flip back to front",
                                    tint = colors.inkMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Flip Front",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkMuted
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = hintVisible && isHiddenRecall && cardHintText.isNotBlank(),
                    enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.amberLight,
                        border = BorderStroke(0.8.dp, colors.amber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = colors.amber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cardHintText,
                                fontSize = 12.sp,
                                color = colors.inkBold,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = activeBlock.blockIndex to safeQueueIndex,
                    transitionSpec = {
                        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    },
                    label = "flashcard_transition",
                    modifier = Modifier.testTag("srs_active_card")
                ) { _ ->
                    KnowledgeBlockItemView(
                        block = activeBlock,
                        isStarred = isStarred,
                        isMastered = isMastered,
                        isHiddenRecall = isHiddenRecall,
                        recallMode = recallMode,
                        maskMode = maskMode,
                        flipPair = flipPair,
                        showTypeBadges = showTypeBadges,
                        showOptionsBeforeReveal = showOptionsBeforeReveal,
                        selectedSelfOption = selfSelectedOptions[activeBlock.blockIndex],
                        onSelectSelfOption = { chosen ->
                            onSelectSelfOption(activeBlock.blockIndex, chosen)
                        },
                        isExpanded = true,
                        onReveal = {
                            hintVisible = false
                            onRevealBlock(activeBlock.blockIndex)
                        },
                        onHide = { onHideBlock(activeBlock.blockIndex) },
                        onToggleExpand = {
                            if (isHiddenRecall) {
                                hintVisible = false
                                onRevealBlock(activeBlock.blockIndex)
                            }
                        },
                        onToggleStar = { onToggleBookmark(activeBlock.blockIndex) },
                        onToggleMastered = { onToggleMastered(activeBlock.blockIndex) }
                    )
                }
            }

            // Bottom Pinned SM-2 Action & Rating Dock (all buttons >= 48dp touch target)
            Surface(
                color = colors.surface,
                shadowElevation = 6.dp,
                border = BorderStroke(0.6.dp, colors.border.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("srs_rating_dock")
            ) {
                if (isHiddenRecall) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (safeQueueIndex > 0) {
                                    hintVisible = false
                                    queueIndex = safeQueueIndex - 1
                                }
                            },
                            enabled = safeQueueIndex > 0,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Card", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prev", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        Button(
                            onClick = {
                                hintVisible = false
                                onRevealBlock(activeBlock.blockIndex)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("flashcard_reveal_button")
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Show Answer", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        OutlinedButton(
                            onClick = {
                                if (safeQueueIndex < sessionQueue.lastIndex) {
                                    hintVisible = false
                                    queueIndex = safeQueueIndex + 1
                                }
                            },
                            enabled = safeQueueIndex < sessionQueue.lastIndex,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Skip", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Skip Card", modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    val onSubmitRating: (SrsRating) -> Unit = { rating ->
                        reviewedCount++
                        when (rating) {
                            SrsRating.AGAIN -> againCount++
                            SrsRating.HARD -> hardCount++
                            SrsRating.GOOD -> goodCount++
                            SrsRating.EASY -> easyCount++
                        }
                        onRateFlashcard(activeBlock.blockIndex, rating)
                        onHideBlock(activeBlock.blockIndex)
                        hintVisible = false

                        if (rating == SrsRating.AGAIN) {
                            sessionQueue = SpacedRepetitionEngine.reinsertCardSoon(
                                currentQueue = sessionQueue,
                                currentIndex = safeQueueIndex,
                                card = activeBlock
                            )
                        }
                        queueIndex = safeQueueIndex + 1
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SrsRatingActionButton(
                            label = SrsRating.AGAIN.label,
                            intervalLabel = nextIntervalLabels[SrsRating.AGAIN] ?: "<10m",
                            containerColor = colors.redLight,
                            contentColor = colors.red,
                            borderColor = colors.red.copy(alpha = 0.45f),
                            onClick = { onSubmitRating(SrsRating.AGAIN) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("srs_rate_again")
                        )
                        SrsRatingActionButton(
                            label = SrsRating.HARD.label,
                            intervalLabel = nextIntervalLabels[SrsRating.HARD] ?: "1d",
                            containerColor = colors.amberLight,
                            contentColor = colors.amber,
                            borderColor = colors.amber.copy(alpha = 0.45f),
                            onClick = { onSubmitRating(SrsRating.HARD) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("srs_rate_hard")
                        )
                        SrsRatingActionButton(
                            label = SrsRating.GOOD.label,
                            intervalLabel = nextIntervalLabels[SrsRating.GOOD] ?: "1d",
                            containerColor = colors.tealLight,
                            contentColor = colors.tealDark,
                            borderColor = colors.teal.copy(alpha = 0.55f),
                            onClick = { onSubmitRating(SrsRating.GOOD) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("srs_rate_good")
                        )
                        SrsRatingActionButton(
                            label = SrsRating.EASY.label,
                            intervalLabel = nextIntervalLabels[SrsRating.EASY] ?: "4d",
                            containerColor = colors.greenLight,
                            contentColor = colors.green,
                            borderColor = colors.green.copy(alpha = 0.5f),
                            onClick = { onSubmitRating(SrsRating.EASY) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("srs_rate_easy")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SrsMetricBadge(
    label: String,
    value: String,
    containerColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = "$label: $value",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun SrsRatingActionButton(
    label: String,
    intervalLabel: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = intervalLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

private fun extractCardHint(block: StudyNoteBlock): String {
    return when (block) {
        is StudyNoteBlock.MultiLinerCard -> {
            val taggedHint = block.nestedBullets.firstOrNull { (lvl, txt) ->
                lvl >= 0 && (
                    txt.startsWith("Hint:", ignoreCase = true) ||
                        txt.startsWith("Memory Hook:", ignoreCase = true) ||
                        txt.startsWith("Clue:", ignoreCase = true)
                    )
            }?.second
            when {
                !taggedHint.isNullOrBlank() -> taggedHint
                block.formula.isNotBlank() -> "Formula clue: ${block.formula.take(64)}"
                block.nestedBullets.isNotEmpty() -> {
                    val first = block.nestedBullets.first().second
                    if (first.length > 72) first.take(72) + "…" else first
                }
                block.keyTakeaway.isNotBlank() -> "Takeaway starts with: \"${block.keyTakeaway.take(36)}…\""
                else -> ""
            }
        }
        is StudyNoteBlock.OneLiner -> {
            val words = block.detail.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size >= 3) "Starts with: \"${words.take(3).joinToString(" ")}…\"" else ""
        }
        else -> ""
    }
}
