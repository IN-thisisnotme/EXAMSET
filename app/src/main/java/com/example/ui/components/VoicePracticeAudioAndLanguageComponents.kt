package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.model.VocabularyLanguageVariants
import com.example.ui.theme.ExamTheme
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.util.DEFAULT_MAX_VOICE_TAKES_IN_SET
import com.example.util.SavedVoiceClip
import com.example.util.VoiceAudioPlayer
import com.example.util.VoiceLanguageHelper
import com.example.util.VoiceStackPlaybackQueue
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceLanguageSelectorBar(
    detectedPromptLanguage: String,
    selectedLanguageOverride: String?,
    englishVariant: String,
    libraryLanguages: List<String>,
    enabled: Boolean,
    onSelectLanguageOverride: (String?) -> Unit,
    onSelectEnglishVariant: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    var showPickerDialog by remember { mutableStateOf(false) }

    val effectiveLanguage = remember(detectedPromptLanguage, selectedLanguageOverride) {
        VoiceLanguageHelper.normalizeLanguageDisplayName(
            selectedLanguageOverride?.takeIf { it.isNotBlank() } ?: detectedPromptLanguage
        )
    }
    val compactBadge = remember(effectiveLanguage, englishVariant) {
        VoiceLanguageHelper.formatCompactLanguageBadge(effectiveLanguage, englishVariant)
    }
    val isEnglishActive = remember(effectiveLanguage) {
        VocabularyLanguageVariants.isEnglish(effectiveLanguage)
    }

    if (showPickerDialog) {
        VoiceLanguagePickerDialog(
            detectedPromptLanguage = detectedPromptLanguage,
            selectedLanguageOverride = selectedLanguageOverride,
            englishVariant = englishVariant,
            libraryLanguages = libraryLanguages,
            onSelectOverride = { chosen ->
                onSelectLanguageOverride(chosen)
                showPickerDialog = false
            },
            onDismiss = { showPickerDialog = false }
        )
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = { if (enabled) showPickerDialog = true },
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            color = colors.tealLight,
            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.45f)),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("voice_language_picker_btn")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = "Speech Language",
                    tint = colors.tealDark,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (selectedLanguageOverride == null) {
                        "Auto: $compactBadge"
                    } else {
                        compactBadge
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.tealDark,
                    softWrap = true
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Choose Language",
                    tint = colors.tealDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isEnglishActive) {
            VoiceLanguageHelper.ENGLISH_SPEECH_VARIANTS.forEach { (code, _) ->
                FilterChip(
                    selected = englishVariant.equals(code, ignoreCase = true),
                    onClick = { onSelectEnglishVariant(code) },
                    enabled = enabled,
                    label = {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.navyBanner,
                        selectedLabelColor = colors.navyBannerText
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("voice_english_variant_${code.lowercase(Locale.ROOT)}")
                )
            }
        }
    }
}

@Composable
fun VoiceLanguagePickerDialog(
    detectedPromptLanguage: String,
    selectedLanguageOverride: String?,
    englishVariant: String,
    libraryLanguages: List<String>,
    onSelectOverride: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    var searchQuery by remember { mutableStateOf("") }

    val allChoices = remember(detectedPromptLanguage, libraryLanguages) {
        VoiceLanguageHelper.buildLanguageChoices(
            detectedPromptLanguage = detectedPromptLanguage,
            libraryLanguages = libraryLanguages
        )
    }

    val filteredChoices = remember(allChoices, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) {
            allChoices
        } else {
            allChoices.filter { lang ->
                val tag = VoiceLanguageHelper.resolveBcp47Tag(lang, englishVariant).lowercase(Locale.ROOT)
                lang.lowercase(Locale.ROOT).contains(q) || tag.contains(q)
            }
        }
    }

    val trimmedQuery = searchQuery.trim()
    val hasExactMatch = filteredChoices.any { it.equals(trimmedQuery, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .testTag("voice_language_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voice Input Language",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.inkBold
                        )
                        Text(
                            text = "Uses Android Speech Service + all app languages",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_language_dialog_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Language Picker",
                            tint = colors.inkMuted
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.inkMuted
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Search language or type code (e.g. Hindi, fr-CA)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_language_search_input")
                )

                // Pinned Auto-Detect Option
                val autoBadge = VoiceLanguageHelper.formatCompactLanguageBadge(detectedPromptLanguage, englishVariant)
                Surface(
                    onClick = { onSelectOverride(null) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedLanguageOverride == null) colors.tealLight else colors.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (selectedLanguageOverride == null) colors.teal else colors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("voice_language_option_auto")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto (Match Current Prompt)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedLanguageOverride == null) colors.tealDark else colors.inkBold
                            )
                            Text(
                                text = "Currently: $autoBadge",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkMuted
                            )
                        }
                        if (selectedLanguageOverride == null) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = colors.tealDark
                            )
                        }
                    }
                }

                HorizontalDivider(color = colors.borderLight)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 310.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (trimmedQuery.isNotBlank() && !hasExactMatch) {
                        item(key = "__custom_entry") {
                            val customTag = VoiceLanguageHelper.resolveBcp47Tag(trimmedQuery, englishVariant)
                            Surface(
                                onClick = { onSelectOverride(trimmedQuery) },
                                shape = RoundedCornerShape(12.dp),
                                color = colors.amberLight,
                                border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("voice_language_option_custom")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Use custom: \"$trimmedQuery\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.surface
                                    ) {
                                        Text(
                                            text = customTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.amber,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(filteredChoices, key = { it }) { lang ->
                        val isSelected = selectedLanguageOverride?.equals(lang, ignoreCase = true) == true
                        val tag = VoiceLanguageHelper.resolveBcp47Tag(lang, englishVariant)
                        Surface(
                            onClick = { onSelectOverride(lang) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) colors.tealLight else colors.surface,
                            border = BorderStroke(0.9.dp, if (isSelected) colors.teal else colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lang,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) colors.tealDark else colors.inkBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.surfaceVariant
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.inkMuted,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = colors.tealDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceLocalTakesPlayerCard(
    clips: List<SavedVoiceClip>,
    selectedClipId: String?,
    audioPlayer: VoiceAudioPlayer,
    onSelectClip: (SavedVoiceClip) -> Unit,
    onPlaySequence: (SavedVoiceClip?) -> Unit,
    onPauseOrResumePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onStopPlayback: () -> Unit,
    onPlayPauseClip: (SavedVoiceClip) -> Unit,
    onSeekClip: (Long) -> Unit,
    onSelectFinalClip: (SavedVoiceClip) -> Unit,
    onClearNonFinalDrafts: () -> Unit,
    onDeleteClip: (SavedVoiceClip) -> Unit,
    onClearAllClips: () -> Unit,
    modifier: Modifier = Modifier,
    stackLabel: String = "Note Stack",
    maxTakesInSet: Int = DEFAULT_MAX_VOICE_TAKES_IN_SET,
    isTranscribing: Boolean = false,
    onTranscribeClip: ((SavedVoiceClip) -> Unit)? = null
) {
    val colors = ExamTheme.colors
    val fontScale = LocalDensity.current.fontScale
    val isLargeFont = fontScale >= 1.28f

    val orderedClips = remember(clips) {
        VoiceStackPlaybackQueue.orderedStackClips(clips)
    }

    val activeOrSelectedClip = remember(
        orderedClips,
        selectedClipId,
        audioPlayer.activeClipId,
        audioPlayer.activeFilePath
    ) {
        orderedClips.firstOrNull {
            it.id == audioPlayer.activeClipId || it.filePath == audioPlayer.activeFilePath
        }
            ?: orderedClips.firstOrNull { it.id == selectedClipId }
            ?: orderedClips.firstOrNull { it.isFinal }
            ?: orderedClips.lastOrNull()
    }

    val hasFinalClip = remember(orderedClips) { orderedClips.any { it.isFinal } }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_local_takes_card")
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useStackedHeader = isLargeFont || maxWidth < 340.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: Editorial Version Stack Identity + Take Count + Clear Stack
                if (useStackedHeader) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VersionStackHeaderTitle(stackLabel = stackLabel)
                        VersionStackHeaderActions(
                            clipCount = orderedClips.size,
                            maxTakesInSet = maxTakesInSet,
                            onClearAllClips = onClearAllClips
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VersionStackHeaderTitle(
                            stackLabel = stackLabel,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        VersionStackHeaderActions(
                            clipCount = orderedClips.size,
                            maxTakesInSet = maxTakesInSet,
                            onClearAllClips = onClearAllClips
                        )
                    }
                }

                // Local-only privacy indicator (restrained tonal surface)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceVariant,
                    border = BorderStroke(0.8.dp, colors.borderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Local-only audio privacy",
                            tint = colors.green,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = stringResource(R.string.voice_version_stack_privacy),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkMuted,
                            softWrap = true
                        )
                    }
                }

                if (orderedClips.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(0.8.dp, colors.borderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "No takes recorded for this note yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Record up to $maxTakesInSet takes for this note without overwriting earlier attempts. Compare them sequentially with Play All and mark your Final version.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted
                            )
                        }
                    }
                } else {
                    audioPlayer.playbackError?.let { playErr ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.redLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = playErr,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.red,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // 1. Version Toggle Tabs (v1 / v2 / v3)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_version_switcher_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        orderedClips.forEach { clip ->
                            val isSelected = activeOrSelectedClip?.id == clip.id
                            val isPlayingThis = (audioPlayer.activeClipId == clip.id ||
                                audioPlayer.activeFilePath == clip.filePath) &&
                                (audioPlayer.isPlaying || audioPlayer.isWaitingBetweenTakes)

                            val tileContainer by animateColorAsState(
                                targetValue = when {
                                    isPlayingThis -> colors.tealLight
                                    isSelected && clip.isFinal -> colors.greenLight
                                    isSelected -> colors.navyBanner
                                    clip.isFinal -> colors.greenLight.copy(alpha = 0.55f)
                                    else -> colors.surfaceVariant
                                },
                                label = "version_tile_bg"
                            )
                            val tileBorder = when {
                                isPlayingThis -> colors.teal
                                clip.isFinal -> colors.green
                                isSelected -> colors.navyBanner
                                else -> colors.border
                            }
                            val tileTitleColor = when {
                                isPlayingThis -> colors.tealDark
                                isSelected && clip.isFinal -> colors.green
                                isSelected -> colors.navyBannerText
                                clip.isFinal -> colors.green
                                else -> colors.inkBold
                            }
                            val tileSubColor = when {
                                isSelected && !clip.isFinal && !isPlayingThis -> colors.navyBannerText.copy(alpha = 0.85f)
                                else -> colors.inkMuted
                            }

                            Surface(
                                onClick = { onSelectClip(clip) },
                                shape = RoundedCornerShape(14.dp),
                                color = tileContainer,
                                border = BorderStroke(if (isSelected || isPlayingThis || clip.isFinal) 1.4.dp else 0.9.dp, tileBorder),
                                modifier = Modifier
                                    .widthIn(min = 96.dp)
                                    .heightIn(min = 52.dp)
                                    .testTag("voice_version_tab_${clip.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (isPlayingThis) {
                                            Icon(
                                                imageVector = Icons.Default.GraphicEq,
                                                contentDescription = "Playing",
                                                tint = colors.tealDark,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        } else if (clip.isFinal) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Final version",
                                                tint = colors.green,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = "v${clip.takeNumber}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontFamily = RobotoMonoFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = tileTitleColor
                                        )
                                        if (clip.isFinal) {
                                            Text(
                                                text = "FINAL",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = colors.green
                                            )
                                        }
                                    }
                                    val matchSuffix = clip.matchPercent?.let { " • $it%" }.orEmpty()
                                    Text(
                                        text = "${formatClipTimeMs(clip.durationMs)}$matchSuffix",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = RobotoMonoFontFamily,
                                        color = tileSubColor
                                    )
                                }
                            }
                        }
                    }

                    // 2. Sequential Playback Controller Deck
                    val isSequenceActive = audioPlayer.isSequenceMode
                    val isAnyPlaybackLoaded = audioPlayer.activeFilePath != null
                    val currentSliderClip = activeOrSelectedClip
                    val activeTrackTotalMs = if (isAnyPlaybackLoaded && audioPlayer.totalDurationMs > 0L) {
                        audioPlayer.totalDurationMs
                    } else {
                        (currentSliderClip?.durationMs ?: 1L).coerceAtLeast(1L)
                    }
                    val activeTrackPosMs = if (isAnyPlaybackLoaded) {
                        audioPlayer.currentPositionMs.coerceIn(0L, activeTrackTotalMs)
                    } else {
                        0L
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceElevated,
                        border = BorderStroke(
                            width = if (isSequenceActive) 1.3.dp else 0.9.dp,
                            color = if (isSequenceActive) colors.teal else colors.border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_sequence_playback_controller")
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when {
                                            audioPlayer.isSequenceMode && audioPlayer.isWaitingBetweenTakes -> {
                                                val nextStep = (audioPlayer.activeSequenceIndex + 2)
                                                    .coerceAtMost(audioPlayer.sequenceClips.size)
                                                "Pause before take $nextStep of ${audioPlayer.sequenceClips.size}…"
                                            }
                                            audioPlayer.isSequenceMode && audioPlayer.isPlaying -> {
                                                val curClip = audioPlayer.sequenceClips.getOrNull(audioPlayer.activeSequenceIndex)
                                                val vLabel = curClip?.let { "v${it.takeNumber}" } ?: ""
                                                "Playing All • $vLabel (${audioPlayer.activeSequenceIndex + 1}/${audioPlayer.sequenceClips.size})"
                                            }
                                            audioPlayer.isSequenceMode -> {
                                                "Sequence Paused (${audioPlayer.activeSequenceIndex + 1}/${audioPlayer.sequenceClips.size})"
                                            }
                                            audioPlayer.isPlaying && currentSliderClip != null -> {
                                                "Listening to v${currentSliderClip.takeNumber}"
                                            }
                                            orderedClips.size > 1 -> {
                                                "Sequential Compare (${orderedClips.size} versions)"
                                            }
                                            else -> {
                                                "Playback Ready (v${orderedClips.first().takeNumber})"
                                            }
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (audioPlayer.isSequenceMode) colors.tealDark else colors.inkBold,
                                        softWrap = true
                                    )
                                    if (orderedClips.size > 1 && !audioPlayer.isSequenceMode) {
                                        Text(
                                            text = "Tap Play All to hear v${orderedClips.first().takeNumber} → v${orderedClips.last().takeNumber} hands-free",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.inkMuted
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.surfaceVariant
                                ) {
                                    Text(
                                        text = "${formatClipTimeMs(activeTrackPosMs)} / ${formatClipTimeMs(activeTrackTotalMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = RobotoMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Slider(
                                value = activeTrackPosMs.toFloat(),
                                onValueChange = { if (isAnyPlaybackLoaded) onSeekClip(it.toLong()) },
                                valueRange = 0f..activeTrackTotalMs.toFloat().coerceAtLeast(1f),
                                enabled = isAnyPlaybackLoaded,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.teal,
                                    activeTrackColor = colors.teal,
                                    inactiveTrackColor = colors.border
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .testTag("voice_sequence_slider")
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Play All / Pause / Resume primary sequence control
                                Button(
                                    onClick = {
                                        if (audioPlayer.isSequenceMode || audioPlayer.isPlaying) {
                                            onPauseOrResumePlayback()
                                        } else {
                                            onPlaySequence(orderedClips.firstOrNull())
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (audioPlayer.isPlaying) colors.amber else colors.primaryButtonBg,
                                        contentColor = colors.primaryButtonText
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("voice_sequence_play_all_btn")
                                ) {
                                    Icon(
                                        imageVector = when {
                                            audioPlayer.isPlaying -> Icons.Default.Pause
                                            audioPlayer.isSequenceMode -> Icons.Default.PlayArrow
                                            else -> Icons.Default.PlaylistPlay
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            audioPlayer.isPlaying -> stringResource(R.string.voice_playback_pause)
                                            audioPlayer.isSequenceMode -> stringResource(R.string.voice_playback_resume)
                                            orderedClips.size > 1 -> "${stringResource(R.string.voice_playback_play_all)} (${orderedClips.size})"
                                            else -> "Play Take"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (orderedClips.size > 1) {
                                    Surface(
                                        onClick = onSkipPrevious,
                                        enabled = audioPlayer.isSequenceMode,
                                        shape = CircleShape,
                                        color = if (audioPlayer.isSequenceMode) colors.surfaceVariant else colors.surfaceVariant.copy(alpha = 0.45f),
                                        border = BorderStroke(0.9.dp, colors.border),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("voice_sequence_prev_btn")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SkipPrevious,
                                                contentDescription = stringResource(R.string.voice_playback_prev),
                                                tint = if (audioPlayer.isSequenceMode) colors.inkBold else colors.inkMuted
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = onSkipNext,
                                        enabled = audioPlayer.isSequenceMode,
                                        shape = CircleShape,
                                        color = if (audioPlayer.isSequenceMode) colors.surfaceVariant else colors.surfaceVariant.copy(alpha = 0.45f),
                                        border = BorderStroke(0.9.dp, colors.border),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("voice_sequence_next_btn")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SkipNext,
                                                contentDescription = stringResource(R.string.voice_playback_next),
                                                tint = if (audioPlayer.isSequenceMode) colors.inkBold else colors.inkMuted
                                            )
                                        }
                                    }
                                }

                                if (isAnyPlaybackLoaded || audioPlayer.isSequenceMode) {
                                    OutlinedButton(
                                        onClick = onStopPlayback,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("voice_sequence_stop_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = stringResource(R.string.voice_playback_stop),
                                            tint = colors.inkBold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.voice_playback_stop),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.inkBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Selected Version Inspector & Finalization Controls
                    activeOrSelectedClip?.let { clip ->
                        val isPlayingThisClip = (audioPlayer.activeClipId == clip.id ||
                            audioPlayer.activeFilePath == clip.filePath) && audioPlayer.isPlaying

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (clip.isFinal) {
                                colors.greenLight.copy(alpha = if (colors.isDark) 0.2f else 0.42f)
                            } else {
                                colors.surfaceVariant.copy(alpha = 0.72f)
                            },
                            border = BorderStroke(
                                width = if (clip.isFinal) 1.2.dp else 0.9.dp,
                                color = if (clip.isFinal) colors.green else colors.border
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("voice_clip_card_${clip.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (clip.isFinal) colors.green else colors.navyBanner
                                    ) {
                                        Text(
                                            text = if (clip.isFinal) {
                                                "FINAL • v${clip.takeNumber}"
                                            } else {
                                                "Version v${clip.takeNumber}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (clip.isFinal) colors.primaryButtonText else colors.navyBannerText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.tealLight
                                    ) {
                                        Text(
                                            text = clip.languageLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.tealDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    clip.matchPercent?.let { pct ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (pct >= 80) colors.greenLight else colors.amberLight
                                        ) {
                                            Text(
                                                text = "$pct% match",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (pct >= 80) colors.green else colors.amber,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    if (clip.estimatedWpm > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = colors.surface
                                        ) {
                                            Text(
                                                text = "${clip.estimatedWpm} WPM",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.inkMuted,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.surface
                                    ) {
                                        Text(
                                            text = formatClipBytes(clip.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = RobotoMonoFontFamily,
                                            color = colors.inkMuted,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = clip.promptTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                AnimatedVisibility(visible = clip.transcriptPreview.isNotBlank()) {
                                    Text(
                                        text = "\"${clip.transcriptPreview}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = colors.inkMuted,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onPlayPauseClip(clip) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("voice_clip_play_${clip.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingThisClip) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = colors.tealDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isPlayingThisClip) "Pause v${clip.takeNumber}" else "Play v${clip.takeNumber}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.tealDark
                                        )
                                    }

                                    Button(
                                        onClick = { onSelectFinalClip(clip) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (clip.isFinal) colors.green else colors.teal,
                                            contentColor = colors.primaryButtonText
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("voice_clip_set_final_${clip.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (clip.isFinal) Icons.Default.Check else Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (clip.isFinal) {
                                                stringResource(R.string.voice_final_selected_btn)
                                            } else {
                                                stringResource(R.string.voice_set_final_btn)
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (hasFinalClip && orderedClips.size > 1) {
                                        OutlinedButton(
                                            onClick = onClearNonFinalDrafts,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("voice_stack_clear_drafts_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CleaningServices,
                                                contentDescription = null,
                                                tint = colors.inkBold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.voice_clear_drafts_btn),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = colors.inkBold
                                            )
                                        }
                                    }

                                    if (onTranscribeClip != null && clip.transcriptPreview.isBlank()) {
                                        OutlinedButton(
                                            onClick = { onTranscribeClip(clip) },
                                            enabled = !isTranscribing,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("voice_clip_transcribe_${clip.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Translate,
                                                contentDescription = null,
                                                tint = colors.tealDark,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Read Words",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = colors.tealDark
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { onDeleteClip(clip) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("voice_clip_delete_${clip.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete v${clip.takeNumber}",
                                            tint = colors.red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Delete",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.red
                                        )
                                    }
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
private fun VersionStackHeaderTitle(
    stackLabel: String,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            tint = colors.teal,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = stringResource(R.string.voice_version_stack_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = colors.tealDark
            )
            Text(
                text = stackLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMuted
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VersionStackHeaderActions(
    clipCount: Int,
    maxTakesInSet: Int,
    onClearAllClips: () -> Unit
) {
    val colors = ExamTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (clipCount >= maxTakesInSet) colors.amberLight else colors.tealLight
        ) {
            Text(
                text = "$clipCount / $maxTakesInSet takes",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = RobotoMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = if (clipCount >= maxTakesInSet) colors.amber else colors.tealDark,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }

        if (clipCount > 0) {
            TextButton(
                onClick = onClearAllClips,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("voice_clips_clear_all_btn")
            ) {
                Text(
                    text = stringResource(R.string.voice_clear_stack_btn),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.red
                )
            }
        }
    }
}

private fun formatClipTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

private fun formatClipBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    return if (kb >= 1024.0) {
        String.format(Locale.US, "%.1f MB", kb / 1024.0)
    } else {
        String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    }
}
