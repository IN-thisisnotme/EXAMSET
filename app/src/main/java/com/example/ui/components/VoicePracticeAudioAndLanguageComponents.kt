package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.VocabularyLanguageVariants
import com.example.ui.theme.ExamTheme
import com.example.util.DEFAULT_MAX_VOICE_TAKES_IN_SET
import com.example.util.SavedVoiceClip
import com.example.util.VoiceAudioPlayer
import com.example.util.VoiceLanguageHelper
import java.util.Locale

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
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
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                    maxLines = 1,
                    softWrap = false
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

@Composable
fun VoiceLocalTakesPlayerCard(
    clips: List<SavedVoiceClip>,
    audioPlayer: VoiceAudioPlayer,
    onPlayPauseClip: (SavedVoiceClip) -> Unit,
    onSeekClip: (Long) -> Unit,
    onKeepOnlyFinalClip: (SavedVoiceClip) -> Unit,
    onDeleteClip: (SavedVoiceClip) -> Unit,
    onClearAllClips: () -> Unit,
    modifier: Modifier = Modifier,
    maxTakesInSet: Int = DEFAULT_MAX_VOICE_TAKES_IN_SET,
    isTranscribing: Boolean = false,
    onTranscribeClip: ((SavedVoiceClip) -> Unit)? = null
) {
    val colors = ExamTheme.colors

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_local_takes_card")
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "RECORDED TAKES (LISTEN BACK)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.tealDark
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (clips.size >= maxTakesInSet) colors.amberLight else colors.tealLight
                    ) {
                        Text(
                            text = "${clips.size} / $maxTakesInSet takes",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (clips.size >= maxTakesInSet) colors.amber else colors.tealDark,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (clips.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAllClips,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("voice_clips_clear_all_btn")
                        ) {
                            Text(
                                text = "Clear All",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.red,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Strictly local / no-cloud-backup badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.greenLight.copy(alpha = if (colors.isDark) 0.2f else 0.65f),
                border = BorderStroke(0.8.dp, colors.green.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                        text = "Saved locally on this device only • Never uploaded or backed up to cloud",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.green
                    )
                }
            }

            if (clips.isEmpty()) {
                Text(
                    text = "Each live session (Continuous Mic or Record Audio Only) keeps up to $maxTakesInSet takes here so you can listen back, compare versions, and tap \"Keep Only Final\" on the best one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )
            } else {
                val helperNote = when {
                    clips.size == 1 && clips.first().isFinal ->
                        "Final version locked — earlier draft recordings were removed."
                    clips.size >= maxTakesInSet ->
                        "Rolling $maxTakesInSet-take set is full: recording another take overwrites the oldest draft. Tap \"Keep Only Final\" on your preferred version."
                    else ->
                        "Compare takes below without overwriting your previous recording, or tap \"Keep Only Final\" to remove extra drafts."
                }
                Text(
                    text = helperNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )

                audioPlayer.playbackError?.let { playErr ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.redLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = playErr,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.red,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    clips.forEach { clip ->
                        val isActiveClip = audioPlayer.activeFilePath == clip.filePath
                        val isPlayingThis = isActiveClip && audioPlayer.isPlaying
                        val clipTotalMs = if (isActiveClip && audioPlayer.totalDurationMs > 0L) {
                            audioPlayer.totalDurationMs
                        } else {
                            clip.durationMs.coerceAtLeast(1L)
                        }
                        val clipPosMs = if (isActiveClip) audioPlayer.currentPositionMs.coerceIn(0L, clipTotalMs) else 0L

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                clip.isFinal -> colors.greenLight.copy(alpha = if (colors.isDark) 0.18f else 0.4f)
                                isActiveClip -> colors.tealLight.copy(alpha = if (colors.isDark) 0.16f else 0.35f)
                                else -> colors.surfaceVariant
                            },
                            border = BorderStroke(
                                width = if (clip.isFinal || isActiveClip) 1.3.dp else 0.9.dp,
                                color = when {
                                    clip.isFinal -> colors.green
                                    isActiveClip -> colors.teal
                                    else -> colors.border
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("voice_clip_card_${clip.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Title + Take Badge row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (clip.isFinal) colors.green else colors.navyBanner
                                        ) {
                                            Text(
                                                text = if (clip.isFinal) "FINAL • Take #${clip.takeNumber}" else "Take #${clip.takeNumber}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (clip.isFinal) Color.White else colors.navyBannerText,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        if (clip.estimatedWpm > 0) {
                                            Text(
                                                text = "${clip.estimatedWpm} WPM",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.inkMuted,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    Text(
                                        text = formatClipBytes(clip.sizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.inkMuted,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Text(
                                    text = clip.promptTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (clip.transcriptPreview.isNotBlank()) {
                                    Text(
                                        text = "\"${clip.transcriptPreview}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = colors.inkMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Player Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        onClick = { onPlayPauseClip(clip) },
                                        shape = CircleShape,
                                        color = if (isPlayingThis) colors.amber else colors.teal,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("voice_clip_play_${clip.id}")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlayingThis) "Pause Take ${clip.takeNumber}" else "Play Take ${clip.takeNumber}",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Slider(
                                            value = clipPosMs.toFloat(),
                                            onValueChange = { if (isActiveClip) onSeekClip(it.toLong()) },
                                            valueRange = 0f..clipTotalMs.toFloat(),
                                            enabled = isActiveClip,
                                            colors = SliderDefaults.colors(
                                                thumbColor = colors.teal,
                                                activeTrackColor = colors.teal,
                                                inactiveTrackColor = colors.border
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(28.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = formatClipTimeMs(clipPosMs),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.inkMuted
                                            )
                                            Text(
                                                text = formatClipTimeMs(clipTotalMs),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.inkMuted
                                            )
                                        }
                                    }
                                }

                                // Compare & Finalize Controls
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!clip.isFinal || clips.size > 1) {
                                        Button(
                                            onClick = { onKeepOnlyFinalClip(clip) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.green,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("voice_clip_keep_final_${clip.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Keep Only Final",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
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
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.tealDark,
                                                maxLines = 1,
                                                softWrap = false
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
                                            contentDescription = "Delete Take ${clip.takeNumber}",
                                            tint = colors.red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Delete",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.red,
                                            maxLines = 1,
                                            softWrap = false
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
