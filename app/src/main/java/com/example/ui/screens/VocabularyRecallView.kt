package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.model.FlashcardSrsState
import com.example.model.FlashcardSrsStatus
import com.example.model.SrsRating
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyRecallDirection
import com.example.ui.theme.ExamTheme
import com.example.util.SpacedRepetitionEngine
import com.example.util.VocabularyEngine

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabularyRecallView(
    deck: VocabularyDeck,
    srsMap: Map<String, FlashcardSrsState>,
    onRateEntry: (entryId: String, rating: SrsRating) -> Unit,
    onResetDeckSrs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val haptic = LocalHapticFeedback.current
    val nowMillis = System.currentTimeMillis()
    val stats = remember(deck, srsMap) { VocabularyEngine.computeDeckSrsStats(deck, srsMap, nowMillis) }

    var direction by remember(deck.id) { mutableStateOf(VocabularyRecallDirection.WORD_TO_MEANING) }
    var cramAll by remember(deck.id) { mutableStateOf(stats.actionableCount == 0 && deck.entries.isNotEmpty()) }
    var shuffled by remember(deck.id) { mutableStateOf(false) }
    var sessionNonce by remember(deck.id) { mutableIntStateOf(0) }
    var showResetConfirm by remember { mutableStateOf(false) }

    var sessionQueue by remember(deck.id, cramAll, shuffled, sessionNonce) {
        mutableStateOf(
            VocabularyEngine.buildRecallQueue(
                deck = deck,
                srsMap = srsMap,
                nowMillis = System.currentTimeMillis(),
                cramAll = cramAll,
                shuffle = shuffled
            )
        )
    }
    var currentIndex by remember(deck.id, cramAll, shuffled, sessionNonce) { mutableIntStateOf(0) }
    var isRevealed by remember(deck.id, cramAll, shuffled, sessionNonce, currentIndex) { mutableStateOf(false) }
    var reviewedInSession by remember(deck.id, cramAll, shuffled, sessionNonce) { mutableIntStateOf(0) }

    // Keep entries fresh if user edited a word while in this deck
    val entriesById = remember(deck.entries) { deck.entries.associateBy { it.id } }
    val activeEntry: VocabularyEntry? = sessionQueue.getOrNull(currentIndex)?.let { entriesById[it.id] ?: it }

    LaunchedEffect(deck.entries.size) {
        if (sessionQueue.isEmpty() && deck.entries.isNotEmpty() && cramAll) {
            sessionNonce++
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. SM-2 Deck Telemetry Bar
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(0.8.dp, colors.border)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VocabSrsMetricPill("Due", stats.dueCount.toString(), colors.amber, colors.amberLight, Modifier.weight(1f))
                    VocabSrsMetricPill("New", stats.newCount.toString(), colors.tealDark, colors.tealLight, Modifier.weight(1f))
                    VocabSrsMetricPill("Learning", stats.learningCount.toString(), colors.inkBold, colors.surfaceVariant, Modifier.weight(1f))
                    VocabSrsMetricPill("Mastered", stats.masteredCount.toString(), colors.green, colors.greenLight, Modifier.weight(1f))
                }

                // Controls strip: Direction + Due/All + Shuffle + Reset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            direction = if (direction == VocabularyRecallDirection.WORD_TO_MEANING) {
                                VocabularyRecallDirection.MEANING_TO_WORD
                            } else {
                                VocabularyRecallDirection.WORD_TO_MEANING
                            }
                            isRevealed = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = colors.tealLight,
                        border = BorderStroke(0.9.dp, colors.teal.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("vocab_recall_direction_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = colors.tealDark, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = direction.label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.tealDark
                            )
                        }
                    }

                    Surface(
                        onClick = { cramAll = !cramAll },
                        shape = RoundedCornerShape(8.dp),
                        color = if (cramAll) colors.amberLight else colors.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(0.8.dp, if (cramAll) colors.amber else colors.border),
                        modifier = Modifier.testTag("vocab_recall_cram_toggle")
                    ) {
                        Text(
                            text = if (cramAll) "Mode: All (${deck.entries.size})" else "Mode: Due & New (${stats.actionableCount})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (cramAll) colors.amber else colors.inkBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }

                    Surface(
                        onClick = { shuffled = !shuffled },
                        shape = RoundedCornerShape(8.dp),
                        color = if (shuffled) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(0.8.dp, if (shuffled) colors.teal else colors.border)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle recall queue", tint = if (shuffled) colors.tealDark else colors.inkMuted, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (shuffled) "Shuffled" else "A -> Z",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (shuffled) colors.tealDark else colors.inkBold
                            )
                        }
                    }

                    if (srsMap.isNotEmpty()) {
                        Surface(
                            onClick = { showResetConfirm = true },
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(0.8.dp, colors.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset spaced repetition", tint = colors.inkMuted, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.inkMuted)
                            }
                        }
                    }
                }
            }
        }

        // 2. Active Card or Completion State
        if (deck.entries.isEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No vocabulary entries yet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                    Text(
                        "Import JSON/Text or add words to start SM-2 spaced repetition.",
                        fontSize = 12.5.sp,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (activeEntry == null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.green.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vocab_recall_complete_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.green, modifier = Modifier.size(44.dp))
                    Text(
                        text = if (reviewedInSession > 0) "Recall Session Complete!" else "All Caught Up for Today!",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Text(
                        text = if (reviewedInSession > 0) {
                            "Reviewed $reviewedInSession words • ${stats.masteredCount}/${stats.totalCount} mastered (${stats.retentionPercent}% retention)"
                        } else {
                            "No words are currently due. Practice all words anytime to reinforce retention."
                        },
                        fontSize = 12.5.sp,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                cramAll = true
                                sessionNonce++
                            },
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Practice All Words", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            val totalInQueue = sessionQueue.size.coerceAtLeast(1)
            val progress = ((currentIndex + 1).toFloat() / totalInQueue.toFloat()).coerceIn(0f, 1f)
            val entryState = srsMap[activeEntry.id]
            val srsStatus = SpacedRepetitionEngine.statusOf(entryState, nowMillis)
            val intervalPreviews = remember(entryState) {
                SpacedRepetitionEngine.previewNextIntervalLabels(entryState, nowMillis)
            }

            // Progress strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Card ${currentIndex + 1} of $totalInQueue",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                val (statusBg, statusFg) = when (srsStatus) {
                    FlashcardSrsStatus.NEW -> colors.tealLight to colors.tealDark
                    FlashcardSrsStatus.DUE -> colors.amberLight to colors.amber
                    FlashcardSrsStatus.LEARNING -> colors.surfaceVariant to colors.inkBold
                    FlashcardSrsStatus.MASTERED -> colors.greenLight to colors.green
                }
                Surface(shape = RoundedCornerShape(6.dp), color = statusBg) {
                    Text(
                        text = srsStatus.label,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                color = colors.teal,
                trackColor = colors.borderLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )

            // Main Flip / Recall Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, if (isRevealed) colors.teal.copy(alpha = 0.55f) else colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isRevealed = !isRevealed
                    }
                    .testTag("vocab_recall_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Metadata badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight) {
                                Text(
                                    text = activeEntry.effectiveLevelBadge(),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.tealDark,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            if (activeEntry.partOfSpeech.isNotBlank()) {
                                Surface(shape = RoundedCornerShape(6.dp), color = colors.surfaceVariant) {
                                    Text(
                                        text = activeEntry.partOfSpeech,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.ink,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = direction.subtitle,
                            fontSize = 10.5.sp,
                            color = colors.inkMuted
                        )
                    }

                    // FRONT CUE
                    if (direction == VocabularyRecallDirection.WORD_TO_MEANING) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = activeEntry.word,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.inkBold,
                                textAlign = TextAlign.Center
                            )
                            if (activeEntry.pronunciation.isNotBlank()) {
                                Text(
                                    text = activeEntry.pronunciation,
                                    fontSize = 13.5.sp,
                                    color = colors.teal,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "RECALL THE ${deck.language.uppercase()} WORD",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = activeEntry.meaning,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                lineHeight = 22.sp
                            )
                            if (activeEntry.translation.isNotBlank()) {
                                Text(
                                    text = "Translation: ${activeEntry.translation}",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.amber
                                )
                            }
                        }
                    }

                    if (!isRevealed) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isRevealed = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.navyBanner,
                                contentColor = colors.navyBannerText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("vocab_recall_reveal_button")
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reveal Answer & Context Examples", fontWeight = FontWeight.Bold)
                        }
                    }

                    // REVEALED BACK CONTENT
                    AnimatedVisibility(visible = isRevealed) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HorizontalDivider(color = colors.borderLight)

                            if (direction == VocabularyRecallDirection.MEANING_TO_WORD) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.tealLight.copy(alpha = 0.55f),
                                    border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = activeEntry.word,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = colors.tealDark
                                        )
                                        if (activeEntry.pronunciation.isNotBlank()) {
                                            Text(
                                                text = activeEntry.pronunciation,
                                                fontSize = 12.5.sp,
                                                color = colors.inkMuted
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = activeEntry.meaning,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.inkBold,
                                        lineHeight = 21.sp
                                    )
                                    if (activeEntry.translation.isNotBlank()) {
                                        Text(
                                            text = activeEntry.translation,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.tealDark
                                        )
                                    }
                                }
                            }

                            if (activeEntry.examples.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "CONTEXT EXAMPLES",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkMuted,
                                        letterSpacing = 0.5.sp
                                    )
                                    activeEntry.examples.forEach { ex ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = colors.surfaceVariant.copy(alpha = 0.55f),
                                            border = BorderStroke(0.6.dp, colors.borderLight),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(9.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = ex.context.uppercase(),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.teal
                                                )
                                                Text(
                                                    text = ex.sentence,
                                                    fontSize = 12.5.sp,
                                                    color = colors.inkBold,
                                                    lineHeight = 18.sp
                                                )
                                                if (ex.translation.isNotBlank()) {
                                                    Text(
                                                        text = ex.translation,
                                                        fontSize = 11.5.sp,
                                                        color = colors.inkMuted,
                                                        fontStyle = FontStyle.Italic
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (activeEntry.synonyms.isNotEmpty() || activeEntry.collocations.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    activeEntry.synonyms.forEach { syn ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = colors.tealLight.copy(alpha = 0.45f)
                                        ) {
                                            Text(
                                                text = "≈ $syn",
                                                fontSize = 11.sp,
                                                color = colors.tealDark,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    activeEntry.collocations.forEach { col ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = colors.surfaceVariant
                                        ) {
                                            Text(
                                                text = "• $col",
                                                fontSize = 11.sp,
                                                color = colors.ink,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (activeEntry.usageNote.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.amberLight.copy(alpha = 0.55f),
                                    border = BorderStroke(0.6.dp, colors.amber.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Note: ${activeEntry.usageNote}",
                                        fontSize = 11.5.sp,
                                        color = colors.inkBold,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(9.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SM-2 Rating Buttons (visible after Reveal)
            if (isRevealed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val ratingSpecs = listOf(
                        Triple(SrsRating.AGAIN, colors.red, colors.redLight),
                        Triple(SrsRating.HARD, colors.amber, colors.amberLight),
                        Triple(SrsRating.GOOD, colors.tealDark, colors.tealLight),
                        Triple(SrsRating.EASY, colors.green, colors.greenLight)
                    )
                    ratingSpecs.forEach { (rating, fg, bg) ->
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRateEntry(activeEntry.id, rating)
                                reviewedInSession++
                                if (rating == SrsRating.AGAIN) {
                                    val mutable = sessionQueue.toMutableList()
                                    val insertPos = (currentIndex + 3).coerceAtMost(mutable.size)
                                    mutable.add(insertPos, activeEntry)
                                    sessionQueue = mutable
                                }
                                currentIndex++
                                isRevealed = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = bg,
                            border = BorderStroke(1.dp, fg.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .testTag("vocab_srs_rate_${rating.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = rating.label,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = fg,
                                    maxLines = 1
                                )
                                Text(
                                    text = intervalPreviews[rating] ?: "",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = fg.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 460.dp),
            containerColor = colors.surface,
            titleContentColor = colors.inkBold,
            textContentColor = colors.ink,
            title = { Text("Reset Spaced Repetition?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("All SM-2 review intervals for \"${deck.title}\" will reset to New. Your vocabulary entries stay untouched.", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetDeckSrs()
                        sessionNonce++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red, contentColor = Color.White)
                ) { Text("Reset Progress") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel", color = colors.inkMuted)
                }
            }
        )
    }
}

@Composable
private fun VocabSrsMetricPill(
    label: String,
    value: String,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = fg)
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}
