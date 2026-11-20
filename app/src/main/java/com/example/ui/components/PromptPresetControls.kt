package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.DialogProperties
import com.example.model.PromptPreset
import com.example.model.PromptPresetMode
import com.example.ui.theme.ExamColorPalette
import com.example.ui.theme.ExamTheme

class PromptPresetDialogState {
    var showPickerSheet by mutableStateOf(false)
    var showSaveDialog by mutableStateOf(false)
    var renameTarget by mutableStateOf<PromptPreset?>(null)
    var deleteTarget by mutableStateOf<PromptPreset?>(null)
}

@Composable
fun rememberPromptPresetDialogState(): PromptPresetDialogState {
    return remember { PromptPresetDialogState() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PromptPresetBar(
    mode: PromptPresetMode,
    presets: List<PromptPreset>,
    activePreset: PromptPreset?,
    isModified: Boolean,
    suggestedPresetName: String,
    matchingConfigPreset: PromptPreset?,
    canUndoClear: Boolean,
    showAdvancedSettings: Boolean? = null,
    onToggleAdvancedSettings: (() -> Unit)? = null,
    onSelectPreset: (PromptPreset) -> Unit,
    onSaveNewPreset: (name: String) -> Unit,
    onUpdateExistingPreset: (presetId: String, newName: String?) -> Unit,
    onRenamePreset: (presetId: String, newName: String) -> Unit,
    onDuplicatePreset: (presetId: String) -> Unit,
    onTogglePinPreset: (presetId: String) -> Unit,
    onDeletePreset: (presetId: String) -> Unit,
    onClearForm: () -> Unit,
    onUndoClear: () -> Unit,
    onQuickCopyPreset: ((PromptPreset) -> Unit)? = null,
    externalDialogState: PromptPresetDialogState? = null,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    val modePresets = remember(presets, mode) {
        presets
            .filter { it.mode == mode }
            .sortedWith(
                compareByDescending<PromptPreset> { it.isPinned }
                    .thenByDescending { it.lastUsedAt }
                    .thenByDescending { it.updatedAt }
            )
    }

    val localDialogState = rememberPromptPresetDialogState()
    val dialogState = externalDialogState ?: localDialogState

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(
            width = if (activePreset != null) 1.dp else 0.8.dp,
            color = if (activePreset != null) colors.teal.copy(alpha = 0.45f) else colors.border
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("prompt_preset_bar")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Section micro-header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SAVED PRESETS",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 0.6.sp
                )
                if (activePreset != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isModified) colors.amberLight else colors.tealLight,
                        border = BorderStroke(
                            0.6.dp,
                            if (isModified) colors.amber.copy(alpha = 0.45f) else colors.teal.copy(alpha = 0.35f)
                        )
                    ) {
                        Text(
                            text = if (isModified) "Edited" else activePreset.formatBadge(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isModified) colors.amber else colors.tealDark,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else if (modePresets.isNotEmpty()) {
                    Text(
                        text = "${modePresets.size} saved",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkMuted
                    )
                }
            }

            // Dropdown selector trigger
            Surface(
                onClick = {
                    focusManager.clearFocus(force = true)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    dialogState.showPickerSheet = true
                },
                shape = RoundedCornerShape(10.dp),
                color = if (activePreset != null) colors.tealLight.copy(alpha = 0.35f) else colors.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    width = 0.9.dp,
                    color = if (activePreset != null) colors.teal.copy(alpha = 0.55f) else colors.border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("prompt_preset_dropdown_trigger")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (activePreset != null) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = if (activePreset != null) colors.teal else colors.inkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activePreset?.name ?: "No Preset Selected (Custom)",
                            fontSize = 13.sp,
                            fontWeight = if (activePreset != null) FontWeight.Bold else FontWeight.SemiBold,
                            color = colors.inkBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitleText = when {
                            activePreset != null -> activePreset.specChips().take(4).joinToString(" · ")
                            modePresets.isNotEmpty() -> "Tap to load a saved ${mode.label.lowercase()} preset"
                            else -> "Configure below and tap Save to reuse anytime"
                        }
                        if (subtitleText.isNotBlank()) {
                            Text(
                                text = subtitleText,
                                fontSize = 11.sp,
                                color = colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    if (modePresets.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surface,
                            border = BorderStroke(0.6.dp, colors.border)
                        ) {
                            Text(
                                text = modePresets.size.toString(),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Open saved presets",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Clean Action Row: Save / Clear / Optional Collapse Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val saveLabel = when {
                    activePreset != null && isModified -> "Update / Save"
                    activePreset != null -> "Save As"
                    else -> "Save Preset"
                }
                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dialogState.showSaveDialog = true
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isModified) colors.tealLight.copy(alpha = 0.55f) else colors.surface,
                        contentColor = colors.tealDark
                    ),
                    border = BorderStroke(
                        if (isModified) 1.1.dp else 0.8.dp,
                        colors.teal.copy(alpha = if (isModified) 0.7f else 0.45f)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .testTag("prompt_preset_save_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = saveLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClearForm()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.surface,
                        contentColor = colors.inkBold
                    ),
                    border = BorderStroke(0.8.dp, colors.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("prompt_preset_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = "Clear all form fields",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Clear",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                if (showAdvancedSettings != null && onToggleAdvancedSettings != null && activePreset != null) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleAdvancedSettings()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.surfaceVariant.copy(alpha = 0.45f),
                            contentColor = colors.inkBold
                        ),
                        border = BorderStroke(0.8.dp, colors.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .testTag("prompt_preset_collapse_toggle")
                    ) {
                        Icon(
                            imageVector = if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showAdvancedSettings) "Collapse advanced settings" else "Expand advanced settings",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (showAdvancedSettings) "Compact" else "Edit",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Inline Undo banner immediately after Clear so users never fear losing inputs
            AnimatedVisibility(visible = canUndoClear) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(0.7.dp, colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prompt_preset_undo_clear_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Form cleared for a fresh exam",
                            fontSize = 11.5.sp,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUndoClear()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("prompt_preset_undo_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Undo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal
                            )
                        }
                    }
                }
            }
        }
    }

    // When no externalDialogState is hoisted outside LazyColumn, render overlays inline
    if (externalDialogState == null) {
        PromptPresetOverlays(
            dialogState = dialogState,
            mode = mode,
            presets = presets,
            activePreset = activePreset,
            isModified = isModified,
            suggestedPresetName = suggestedPresetName,
            matchingConfigPreset = matchingConfigPreset,
            onSelectPreset = onSelectPreset,
            onSaveNewPreset = onSaveNewPreset,
            onUpdateExistingPreset = onUpdateExistingPreset,
            onRenamePreset = onRenamePreset,
            onDuplicatePreset = onDuplicatePreset,
            onTogglePinPreset = onTogglePinPreset,
            onDeletePreset = onDeletePreset,
            onQuickCopyPreset = onQuickCopyPreset
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PromptPresetOverlays(
    dialogState: PromptPresetDialogState,
    mode: PromptPresetMode,
    presets: List<PromptPreset>,
    activePreset: PromptPreset?,
    isModified: Boolean,
    suggestedPresetName: String,
    matchingConfigPreset: PromptPreset?,
    onSelectPreset: (PromptPreset) -> Unit,
    onSaveNewPreset: (name: String) -> Unit,
    onUpdateExistingPreset: (presetId: String, newName: String?) -> Unit,
    onRenamePreset: (presetId: String, newName: String) -> Unit,
    onDuplicatePreset: (presetId: String) -> Unit,
    onTogglePinPreset: (presetId: String) -> Unit,
    onDeletePreset: (presetId: String) -> Unit,
    onQuickCopyPreset: ((PromptPreset) -> Unit)? = null
) {
    val colors = ExamTheme.colors
    val modePresets = remember(presets, mode) {
        presets
            .filter { it.mode == mode }
            .sortedWith(
                compareByDescending<PromptPreset> { it.isPinned }
                    .thenByDescending { it.lastUsedAt }
                    .thenByDescending { it.updatedAt }
            )
    }

    // Preset Picker Bottom Sheet
    if (dialogState.showPickerSheet) {
        PromptPresetPickerSheet(
            mode = mode,
            presets = modePresets,
            activePresetId = activePreset?.id,
            colors = colors,
            onDismiss = { dialogState.showPickerSheet = false },
            onSelect = { preset ->
                dialogState.showPickerSheet = false
                onSelectPreset(preset)
            },
            onRequestSaveNew = {
                dialogState.showPickerSheet = false
                dialogState.showSaveDialog = true
            },
            onRequestRename = { preset ->
                dialogState.renameTarget = preset
            },
            onDuplicate = { preset ->
                onDuplicatePreset(preset.id)
            },
            onTogglePin = { preset ->
                onTogglePinPreset(preset.id)
            },
            onRequestDelete = { preset ->
                dialogState.deleteTarget = preset
            },
            onQuickCopyPreset = onQuickCopyPreset?.let { copyCb ->
                { preset ->
                    dialogState.showPickerSheet = false
                    copyCb(preset)
                }
            }
        )
    }

    // Save / Update Dialog
    if (dialogState.showSaveDialog) {
        SavePromptPresetDialog(
            mode = mode,
            activePreset = activePreset,
            isModified = isModified,
            existingPresetsForMode = modePresets,
            suggestedName = suggestedPresetName,
            matchingConfigPreset = matchingConfigPreset,
            colors = colors,
            onDismiss = { dialogState.showSaveDialog = false },
            onSaveNew = { name ->
                dialogState.showSaveDialog = false
                onSaveNewPreset(name)
            },
            onUpdateExisting = { presetId, newName ->
                dialogState.showSaveDialog = false
                onUpdateExistingPreset(presetId, newName)
            }
        )
    }

    // Rename Dialog
    dialogState.renameTarget?.let { target ->
        RenamePromptPresetDialog(
            preset = target,
            existingPresetsForMode = modePresets,
            colors = colors,
            onDismiss = { dialogState.renameTarget = null },
            onConfirmRename = { newName ->
                onRenamePreset(target.id, newName)
                dialogState.renameTarget = null
            }
        )
    }

    // Delete Confirmation Dialog
    dialogState.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { dialogState.deleteTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 460.dp),
            containerColor = colors.surface,
            titleContentColor = colors.inkBold,
            textContentColor = colors.ink,
            title = {
                Text(
                    text = "Delete Saved Preset?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "\"${target.name}\" will be removed from your saved presets. Your existing Question Sets and Knowledge Sets will not be affected.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePreset(target.id)
                        dialogState.deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.red,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("prompt_preset_confirm_delete_button")
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogState.deleteTarget = null }) {
                    Text("Cancel", color = colors.inkMuted)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PromptPresetPickerSheet(
    mode: PromptPresetMode,
    presets: List<PromptPreset>,
    activePresetId: String?,
    colors: ExamColorPalette,
    onDismiss: () -> Unit,
    onSelect: (PromptPreset) -> Unit,
    onRequestSaveNew: () -> Unit,
    onRequestRename: (PromptPreset) -> Unit,
    onDuplicate: (PromptPreset) -> Unit,
    onTogglePin: (PromptPreset) -> Unit,
    onRequestDelete: (PromptPreset) -> Unit,
    onQuickCopyPreset: ((PromptPreset) -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val filteredPresets = remember(presets, searchQuery) {
        if (searchQuery.isBlank()) presets else presets.filter { it.matchesSearch(searchQuery) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.inkBold,
        modifier = Modifier.testTag("prompt_preset_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${mode.label} Presets",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Text(
                        text = if (presets.isEmpty()) {
                            "No saved presets yet"
                        } else {
                            "${presets.size} saved • Tap to apply settings"
                        },
                        fontSize = 12.sp,
                        color = colors.inkMuted
                    )
                }
                OutlinedButton(
                    onClick = onRequestSaveNew,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.9.dp, colors.teal.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.tealDark),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("prompt_preset_sheet_save_new_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Current", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Search bar automatically shown when 5+ presets exist (handles 10-40+ presets smoothly)
            if (presets.size >= 5) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search presets by name, exam, subject, format…", fontSize = 12.5.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = colors.inkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.inkBold,
                        unfocusedTextColor = colors.inkBold,
                        focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prompt_preset_search_input")
                )
            }

            if (filteredPresets.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(0.8.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (presets.isEmpty()) {
                                "No saved ${mode.label.lowercase()} presets yet"
                            } else {
                                "No presets match \"$searchQuery\""
                            },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                        Text(
                            text = if (presets.isEmpty()) {
                                "Save your preferred exam, difficulty, language, and output format once to reuse them across chapters."
                            } else {
                                "Try a different keyword or clear the search filter."
                            },
                            fontSize = 12.sp,
                            color = colors.inkMuted,
                            lineHeight = 17.sp
                        )
                        if (presets.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onRequestSaveNew,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.teal,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Save Current Settings", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPresets, key = { it.id }) { preset ->
                        PresetListCard(
                            preset = preset,
                            isActive = preset.id == activePresetId,
                            colors = colors,
                            onSelect = { onSelect(preset) },
                            onRename = { onRequestRename(preset) },
                            onDuplicate = { onDuplicate(preset) },
                            onTogglePin = { onTogglePin(preset) },
                            onDelete = { onRequestDelete(preset) },
                            onQuickCopy = onQuickCopyPreset?.let { cb -> { cb(preset) } }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetListCard(
    preset: PromptPreset,
    isActive: Boolean,
    colors: ExamColorPalette,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onQuickCopy: (() -> Unit)?
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) colors.tealLight.copy(alpha = 0.42f) else colors.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            width = if (isActive) 1.3.dp else 0.75.dp,
            color = if (isActive) colors.teal else colors.border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("prompt_preset_item_${preset.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (preset.isPinned) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Pinned preset",
                        tint = colors.amber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = preset.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isActive) colors.teal else colors.surface,
                    border = BorderStroke(0.6.dp, if (isActive) colors.teal else colors.border)
                ) {
                    Text(
                        text = preset.formatBadge(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color.White else colors.tealDark,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (onQuickCopy != null) {
                    IconButton(
                        onClick = onQuickCopy,
                        modifier = Modifier.testTag("prompt_preset_quick_copy_${preset.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Apply and copy prompt",
                            tint = colors.teal,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("prompt_preset_menu_${preset.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Preset options",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (preset.isPinned) "Unpin" else "Pin to Top") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (preset.isPinned) Icons.Default.StarBorder else Icons.Default.Star,
                                    contentDescription = null,
                                    tint = colors.amber
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onTogglePin()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = colors.inkBold
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = colors.teal
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = colors.red) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = colors.red
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            // Compact spec chips for 1-second recognition (no walls of raw prompt text)
            val chips = preset.specChips()
            if (chips.isNotEmpty() || isActive) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = colors.greenLight,
                            border = BorderStroke(0.5.dp, colors.green.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.green,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Active",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.green
                                )
                            }
                        }
                    }
                    chips.forEach { chip ->
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = colors.surface,
                            border = BorderStroke(0.5.dp, colors.border)
                        ) {
                            Text(
                                text = chip,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.ink,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavePromptPresetDialog(
    mode: PromptPresetMode,
    activePreset: PromptPreset?,
    isModified: Boolean,
    existingPresetsForMode: List<PromptPreset>,
    suggestedName: String,
    matchingConfigPreset: PromptPreset?,
    colors: ExamColorPalette,
    onDismiss: () -> Unit,
    onSaveNew: (name: String) -> Unit,
    onUpdateExisting: (presetId: String, newName: String?) -> Unit
) {
    val canOfferUpdate = activePreset != null && isModified
    var updateCurrent by remember(activePreset?.id, isModified) { mutableStateOf(canOfferUpdate) }
    val initialNewName = remember(suggestedName, existingPresetsForMode) {
        PromptPreset.uniqueName(suggestedName, existingPresetsForMode.map { it.name })
    }
    var nameInput by remember(updateCurrent, activePreset?.id, initialNewName) {
        mutableStateOf(if (updateCurrent && activePreset != null) activePreset.name else initialNewName)
    }

    val trimmedName = nameInput.trim()
    val conflictingPreset = remember(trimmedName, existingPresetsForMode, updateCurrent, activePreset?.id) {
        if (trimmedName.isBlank()) {
            null
        } else {
            existingPresetsForMode.firstOrNull {
                it.name.equals(trimmedName, ignoreCase = true) &&
                    (!updateCurrent || it.id != activePreset?.id)
            }
        }
    }
    val suggestedUniqueFallback = remember(trimmedName, existingPresetsForMode) {
        if (trimmedName.isBlank()) initialNewName
        else PromptPreset.uniqueName(trimmedName, existingPresetsForMode.map { it.name })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .widthIn(max = 460.dp),
        containerColor = colors.surface,
        titleContentColor = colors.inkBold,
        textContentColor = colors.ink,
        title = {
            Text(
                text = if (updateCurrent && activePreset != null) {
                    "Update Saved Preset"
                } else {
                    "Save ${mode.label} Preset"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canOfferUpdate && activePreset != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(true to "Update Current", false to "Save as New").forEach { (isUpdateOpt, label) ->
                            val selected = updateCurrent == isUpdateOpt
                            Surface(
                                onClick = { updateCurrent = isUpdateOpt },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    if (selected) 1.2.dp else 0.7.dp,
                                    if (selected) colors.teal else colors.border
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) colors.tealDark else colors.inkBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Preset Name", fontSize = 12.sp) },
                    placeholder = { Text("e.g. UPSC Prelims 25Q JSON", fontSize = 12.sp) },
                    singleLine = true,
                    trailingIcon = if (nameInput.isNotEmpty()) {
                        {
                            IconButton(onClick = { nameInput = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear preset name",
                                    tint = colors.inkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.teal,
                        unfocusedLabelColor = colors.inkMuted,
                        focusedTextColor = colors.inkBold,
                        unfocusedTextColor = colors.inkBold
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prompt_preset_name_input")
                )

                Text(
                    text = "Saves exam, subject, volume, language, and format rules. Chapter/Topic stays quick to edit per prompt.",
                    fontSize = 11.sp,
                    color = colors.inkMuted,
                    lineHeight = 15.sp
                )

                // Duplicate name conflict resolution
                if (conflictingPreset != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.amberLight,
                        border = BorderStroke(0.7.dp, colors.amber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "A preset named \"${conflictingPreset.name}\" already exists.",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { nameInput = suggestedUniqueFallback },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Use \"$suggestedUniqueFallback\"",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.tealDark
                                    )
                                }
                                TextButton(
                                    onClick = { onUpdateExisting(conflictingPreset.id, conflictingPreset.name) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Overwrite",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.amber
                                    )
                                }
                            }
                        }
                    }
                } else if (matchingConfigPreset != null && (!updateCurrent || matchingConfigPreset.id != activePreset?.id)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.tealLight.copy(alpha = 0.5f),
                        border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Note: Current settings match \"${matchingConfigPreset.name}\". You can still save a separate copy if you prefer.",
                            fontSize = 11.sp,
                            color = colors.tealDark,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (updateCurrent && activePreset != null) {
                        onUpdateExisting(activePreset.id, trimmedName)
                    } else {
                        onSaveNew(trimmedName)
                    }
                },
                enabled = trimmedName.isNotBlank() && conflictingPreset == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.teal,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("prompt_preset_dialog_confirm_save")
            ) {
                Text(
                    text = if (updateCurrent && activePreset != null) "Update" else "Save",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.inkMuted)
            }
        }
    )
}

@Composable
private fun RenamePromptPresetDialog(
    preset: PromptPreset,
    existingPresetsForMode: List<PromptPreset>,
    colors: ExamColorPalette,
    onDismiss: () -> Unit,
    onConfirmRename: (String) -> Unit
) {
    var nameInput by remember(preset.id) { mutableStateOf(preset.name) }
    val trimmed = nameInput.trim()
    val hasConflict = remember(trimmed, existingPresetsForMode, preset.id) {
        trimmed.isNotBlank() && existingPresetsForMode.any {
            it.id != preset.id && it.name.equals(trimmed, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .widthIn(max = 460.dp),
        containerColor = colors.surface,
        titleContentColor = colors.inkBold,
        textContentColor = colors.ink,
        title = {
            Text("Rename Preset", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("New Preset Name", fontSize = 12.sp) },
                    singleLine = true,
                    isError = hasConflict,
                    supportingText = if (hasConflict) {
                        { Text("Another preset in this mode already uses this name.", fontSize = 11.sp) }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.teal,
                        unfocusedLabelColor = colors.inkMuted,
                        focusedTextColor = colors.inkBold,
                        unfocusedTextColor = colors.inkBold
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prompt_preset_rename_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmRename(trimmed) },
                enabled = trimmed.isNotBlank() && !hasConflict,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.teal,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("prompt_preset_rename_confirm")
            ) {
                Text("Rename", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.inkMuted)
            }
        }
    )
}
