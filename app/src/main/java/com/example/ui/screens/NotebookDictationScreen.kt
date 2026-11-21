package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StudyNoteEntity
import com.example.ui.theme.ExamTheme
import com.example.util.DictationConfig
import com.example.util.DictationElevationInterval
import com.example.util.DictationEngine
import com.example.util.DictationPlaybackPhase
import com.example.util.DictationPunctuationMode
import com.example.util.StudyNoteBlock
import com.example.util.rememberDictationTtsController
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookDictationScreen(
    note: StudyNoteEntity,
    blocks: List<StudyNoteBlock>,
    preferredEnglishVariant: String = "US",
    onLogStudySeconds: (Long) -> Unit = {},
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val view = LocalView.current

    val formatRule = remember(note.formatRuleJson, note.collectionType) { note.parsedFormatRule() }
    val passages = remember(blocks) { DictationEngine.extractPassages(blocks) }

    val controller = rememberDictationTtsController(
        passages = passages,
        language = formatRule.language.ifBlank { "English" },
        preferredEnglishVariant = preferredEnglishVariant,
        onLogStudySeconds = onLogStudySeconds
    )

    var showProofreadText by remember { mutableStateOf(false) }

    // Keep device screen awake strictly while audio dictation is active
    DisposableEffect(view, controller.isPlaying) {
        view.keepScreenOn = controller.isPlaying
        onDispose {
            view.keepScreenOn = false
        }
    }

    BackHandler {
        controller.shutdown()
        onDismiss()
    }

    val currentPassage = controller.currentPassage
    val phrases = controller.phrases
    val currentPhrase = controller.currentPhrase
    val cfg = controller.config
    val isProofreadVisible = showProofreadText || controller.phase == DictationPlaybackPhase.COMPLETED

    val speechLanguages = remember(formatRule.language) {
        (listOf(formatRule.language.ifBlank { "English" }) +
            listOf("English", "Hindi", "French", "German", "Spanish", "Japanese", "Sanskrit"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("notebook_dictation_screen"),
        color = colors.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Surface(color = colors.surface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            controller.shutdown()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("dictation_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Dictation",
                            tint = colors.inkBold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notebook Dictation",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.inkBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = note.title,
                            fontSize = 11.5.sp,
                            color = colors.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Leaf timer composable isolates 1-second ticks
                    DictationSessionTimerPill(
                        secondsProvider = { controller.sessionActiveSeconds },
                        isPlaying = controller.isPlaying
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        onClick = { showProofreadText = !showProofreadText },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isProofreadVisible) colors.teal else colors.surfaceVariant,
                        border = BorderStroke(1.dp, if (isProofreadVisible) colors.teal else colors.border),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dictation_proofread_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isProofreadVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Proofread Text",
                                tint = if (isProofreadVisible) Color.White else colors.inkBold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isProofreadVisible) "Proofread" else "Concealed",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isProofreadVisible) Color.White else colors.inkBold
                            )
                        }
                    }
                }
            }

            if (passages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No Dictatable Passages Found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Text(
                            text = "Add Q&A blocks, multi-liner notes, or one-liners to this Knowledge Set to start notebook dictation practice.",
                            fontSize = 13.sp,
                            color = colors.inkMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Passage Navigation Card
                    if (passages.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surface,
                            border = BorderStroke(0.9.dp, colors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = {
                                        controller.selectPassage(
                                            (controller.currentPassageIndex - 1).coerceAtLeast(0),
                                            autoStart = controller.isPlaying
                                        )
                                    },
                                    enabled = controller.currentPassageIndex > 0,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ChevronLeft,
                                        contentDescription = "Previous Card",
                                        tint = if (controller.currentPassageIndex > 0) colors.inkBold else colors.inkMuted
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "CARD ${controller.currentPassageIndex + 1} OF ${passages.size} • ${currentPassage?.badge.orEmpty()}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.tealDark
                                    )
                                    Text(
                                        text = currentPassage?.title.orEmpty(),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        controller.selectPassage(
                                            (controller.currentPassageIndex + 1).coerceAtMost(passages.lastIndex),
                                            autoStart = controller.isPlaying
                                        )
                                    },
                                    enabled = controller.currentPassageIndex < passages.lastIndex,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "Next Card",
                                        tint = if (controller.currentPassageIndex < passages.lastIndex) colors.inkBold else colors.inkMuted
                                    )
                                }
                            }
                        }
                    }

                    // Active Paper-First Dictation Hero Stage
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, if (controller.isPlaying) colors.teal else colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dictation_hero_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            colors.tealLight.copy(alpha = if (colors.isDark) 0.25f else 0.45f),
                                            colors.surface
                                        )
                                    )
                                )
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val phaseLabel = when (controller.phase) {
                                    DictationPlaybackPhase.IDLE -> "Ready — Pick up your pen"
                                    DictationPlaybackPhase.SPEAKING_PHRASE -> "Speaking Phrase..."
                                    DictationPlaybackPhase.WRITING_PAUSE -> {
                                        val remSec = (controller.writingPauseRemainingMs + 400L) / 1000L
                                        "Write in Notebook (${remSec}s pause)"
                                    }
                                    DictationPlaybackPhase.PAUSED -> "Paused"
                                    DictationPlaybackPhase.COMPLETED -> "Card Complete — Flip to Proofread"
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (controller.phase) {
                                        DictationPlaybackPhase.SPEAKING_PHRASE -> colors.teal
                                        DictationPlaybackPhase.WRITING_PAUSE -> colors.amber
                                        DictationPlaybackPhase.COMPLETED -> colors.green
                                        else -> colors.surfaceVariant
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (controller.phase) {
                                                DictationPlaybackPhase.SPEAKING_PHRASE -> Icons.AutoMirrored.Filled.VolumeUp
                                                DictationPlaybackPhase.WRITING_PAUSE -> Icons.Default.EditNote
                                                else -> Icons.Default.GraphicEq
                                            },
                                            contentDescription = null,
                                            tint = if (controller.phase == DictationPlaybackPhase.IDLE || controller.phase == DictationPlaybackPhase.PAUSED) {
                                                colors.inkBold
                                            } else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = phaseLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (controller.phase == DictationPlaybackPhase.IDLE || controller.phase == DictationPlaybackPhase.PAUSED) {
                                                colors.inkBold
                                            } else Color.White
                                        )
                                    }
                                }

                                Text(
                                    text = "Phrase ${(controller.currentPhraseIndex + 1).coerceAtMost(phrases.size.coerceAtLeast(1))}/${phrases.size}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkMuted
                                )
                            }

                            if (!currentPassage?.promptQuestion.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            text = "PROMPT / TOPIC",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.tealDark,
                                            letterSpacing = 0.6.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = currentPassage!!.promptQuestion,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold,
                                            lineHeight = 19.sp
                                        )
                                    }
                                }
                            }

                            // Smooth Writing Gap / Passage Progress Bar
                            val animatedPause by animateFloatAsState(
                                targetValue = if (controller.phase == DictationPlaybackPhase.WRITING_PAUSE) {
                                    controller.writingPauseProgress
                                } else if (phrases.isNotEmpty()) {
                                    ((controller.currentPhraseIndex + 1).toFloat() / phrases.size.toFloat()).coerceIn(0f, 1f)
                                } else 0f,
                                animationSpec = spring(),
                                label = "dictation_progress"
                            )
                            LinearProgressIndicator(
                                progress = { animatedPause.coerceIn(0f, 1f) },
                                color = if (controller.phase == DictationPlaybackPhase.WRITING_PAUSE) colors.amber else colors.teal,
                                trackColor = colors.borderLight,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            // Concealed Notebook Focus Mode vs Interactive Proofread View
                            if (!isProofreadVisible) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (currentPhrase != null && controller.isPlaying) {
                                            "${currentPhrase.wordCount} words in current phrase • Listen & write on paper"
                                        } else {
                                            "Text concealed so you write genuinely from audio"
                                        },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.inkMuted,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Tap 'Concealed' above anytime to Proofread against the model text.",
                                        fontSize = 11.sp,
                                        color = colors.inkMuted.copy(alpha = 0.85f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                HorizontalDivider(color = colors.border.copy(alpha = 0.7f))
                                Text(
                                    text = "PROOFREAD SCRIPT (Tap any phrase to jump audio)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.tealDark,
                                    letterSpacing = 0.5.sp
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    phrases.forEach { ph ->
                                        val isActive = ph.index == controller.currentPhraseIndex
                                        Surface(
                                            shape = RoundedCornerShape(7.dp),
                                            color = if (isActive) colors.teal else colors.surfaceVariant.copy(alpha = 0.65f),
                                            border = if (isActive) BorderStroke(1.dp, colors.tealDark) else null,
                                            modifier = Modifier.clickable { controller.jumpToPhrase(ph.index) }
                                        ) {
                                            Text(
                                                text = ph.displayText,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isActive) Color.White else colors.inkBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Oversized Pen-in-Hand Transport Controls
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(0.9.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { controller.rewindOneSentence() },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceVariant)
                                        .testTag("dictation_rewind_sentence_btn")
                                ) {
                                    Icon(
                                        Icons.Default.SkipPrevious,
                                        contentDescription = "Rewind One Sentence",
                                        tint = colors.inkBold,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Sentence -", fontSize = 10.5.sp, color = colors.inkMuted, fontWeight = FontWeight.SemiBold)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(
                                    onClick = { controller.playPause() },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.teal,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .size(72.dp)
                                        .testTag("dictation_play_pause_btn")
                                ) {
                                    Icon(
                                        imageVector = if (controller.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (controller.isPlaying) "Pause Dictation" else "Start Dictation",
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (controller.isPlaying) "Pause" else "Dictate",
                                    fontSize = 11.5.sp,
                                    color = colors.tealDark,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { controller.repeatCurrentPhrase() },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceVariant)
                                        .testTag("dictation_repeat_phrase_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Replay,
                                        contentDescription = "Repeat Current Phrase",
                                        tint = colors.inkBold,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Repeat", fontSize = 10.5.sp, color = colors.inkMuted, fontWeight = FontWeight.SemiBold)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { controller.skipToNextPhrase() },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceVariant)
                                        .testTag("dictation_next_phrase_btn")
                                ) {
                                    Icon(
                                        Icons.Default.SkipNext,
                                        contentDescription = "Skip to Next Phrase",
                                        tint = colors.inkBold,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Skip", fontSize = 10.5.sp, color = colors.inkMuted, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Adjustable WPM Switch Card (Live -5 / +5 WPM & Slider)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(0.9.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = colors.teal, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text(
                                            text = "Handwriting Pace Switch",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold
                                        )
                                        Text(
                                            text = "Crisp speech bursts + smart writing pauses",
                                            fontSize = 11.sp,
                                            color = colors.inkMuted
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.tealLight
                                ) {
                                    Text(
                                        text = "${cfg.targetWpm} WPM",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.tealDark,
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                            .testTag("dictation_current_wpm_label")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { controller.adjustWpm(-DictationConfig.WPM_STEP) },
                                    enabled = cfg.targetWpm > DictationConfig.MIN_WPM,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("dictation_wpm_minus_btn")
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("-5 WPM", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Slider(
                                    value = cfg.targetWpm.toFloat(),
                                    onValueChange = { raw ->
                                        val snapped = ((raw / 5f).roundToInt() * 5)
                                            .coerceIn(DictationConfig.MIN_WPM, DictationConfig.MAX_WPM)
                                        controller.setTargetWpm(snapped)
                                    },
                                    valueRange = DictationConfig.MIN_WPM.toFloat()..DictationConfig.MAX_WPM.toFloat(),
                                    steps = ((DictationConfig.MAX_WPM - DictationConfig.MIN_WPM) / 5) - 1,
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.teal,
                                        activeTrackColor = colors.teal
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedButton(
                                    onClick = { controller.adjustWpm(DictationConfig.WPM_STEP) },
                                    enabled = cfg.targetWpm < DictationConfig.MAX_WPM,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("dictation_wpm_plus_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+5 WPM", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Elevating WPM Mode Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(0.9.dp, if (cfg.elevatingEnabled) colors.amber else colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = colors.amber, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text(
                                            text = "Elevating Speed Mode",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold
                                        )
                                        Text(
                                            text = if (cfg.elevatingEnabled) {
                                                "Auto-steps +${cfg.stepWpm} WPM (${cfg.elevationInterval.label}) up to ${cfg.targetMaxWpm} WPM"
                                            } else {
                                                "Gradually increase speed during practice to build exam stamina"
                                            },
                                            fontSize = 11.sp,
                                            color = colors.inkMuted
                                        )
                                    }
                                }
                                Switch(
                                    checked = cfg.elevatingEnabled,
                                    onCheckedChange = { enabled ->
                                        controller.updateElevatingConfig(enabled = enabled)
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = colors.amber),
                                    modifier = Modifier.testTag("dictation_elevating_switch")
                                )
                            }

                            AnimatedVisibility(visible = cfg.elevatingEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                                    Text(
                                        text = "Start Speed → Target Speed",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.inkMuted
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            30 to 60,
                                            35 to 75,
                                            45 to 90,
                                            60 to 110
                                        ).forEach { (start, max) ->
                                            val active = cfg.startWpm == start && cfg.targetMaxWpm == max
                                            Surface(
                                                onClick = {
                                                    controller.updateElevatingConfig(
                                                        enabled = true,
                                                        startWpm = start,
                                                        targetMaxWpm = max
                                                    )
                                                    controller.setTargetWpm(start)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (active) colors.amber else colors.surfaceVariant,
                                                modifier = Modifier.heightIn(min = 38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                    Text(
                                                        text = "$start → $max WPM",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (active) Color.White else colors.inkBold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = "Elevation Cadence",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.inkMuted
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        DictationElevationInterval.entries.forEach { interval ->
                                            val active = cfg.elevationInterval == interval
                                            Surface(
                                                onClick = {
                                                    controller.updateElevatingConfig(
                                                        enabled = true,
                                                        interval = interval
                                                    )
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (active) colors.teal else colors.surfaceVariant,
                                                modifier = Modifier.heightIn(min = 38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                    Text(
                                                        text = interval.label,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (active) Color.White else colors.inkBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Formatting Cues & Speech Language Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(0.9.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val activePunctMode = cfg.effectivePunctuationMode
                                Text(
                                    text = "Spoken Punctuation & Structure Cues",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Text(
                                    text = activePunctMode.summary,
                                    fontSize = 11.sp,
                                    color = colors.inkMuted
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DictationPunctuationMode.entries.forEach { mode ->
                                        val selected = activePunctMode == mode
                                        val tag = when (mode) {
                                            DictationPunctuationMode.OFF -> "dictation_punctuation_mode_off"
                                            DictationPunctuationMode.ESSENTIAL -> "dictation_punctuation_mode_essential"
                                            DictationPunctuationMode.FULL_VERBATIM -> "dictation_punctuation_mode_verbatim"
                                        }
                                        Surface(
                                            onClick = { controller.setPunctuationMode(mode) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) colors.teal else colors.surfaceVariant,
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (selected) colors.tealDark else colors.border
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag(tag)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = mode.label,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (selected) Color.White else colors.inkBold,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = colors.border.copy(alpha = 0.6f))

                            Text(
                                text = "Dictation Voice Language",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkMuted
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                speechLanguages.forEach { lang ->
                                    val active = controller.language.equals(lang, ignoreCase = true)
                                    Surface(
                                        onClick = { controller.updateLanguage(lang, controller.englishVariant) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (active) colors.teal else colors.surfaceVariant,
                                        modifier = Modifier.heightIn(min = 38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            Text(
                                                text = lang,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (active) Color.White else colors.inkBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DictationSessionTimerPill(
    secondsProvider: () -> Long,
    isPlaying: Boolean
) {
    val colors = ExamTheme.colors
    val secs = secondsProvider()
    val mins = secs / 60L
    val rem = secs % 60L
    val formatted = String.format(java.util.Locale.US, "%02d:%02d", mins, rem)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isPlaying) colors.tealLight else colors.surfaceVariant,
        border = BorderStroke(0.8.dp, if (isPlaying) colors.teal else colors.border),
        modifier = Modifier.widthIn(min = 66.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Timer,
                contentDescription = "Session Study Time",
                tint = if (isPlaying) colors.tealDark else colors.inkMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPlaying) colors.tealDark else colors.inkBold
            )
        }
    }
}
