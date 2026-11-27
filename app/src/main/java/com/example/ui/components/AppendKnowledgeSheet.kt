package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StudyNoteEntity
import com.example.model.KnowledgeCollectionType
import com.example.ui.theme.ExamTheme
import com.example.util.StudyNoteParser

private enum class QuickKnowledgeItemTemplate(val label: String) {
    ONE_LINER("One-Liner"),
    QA_CARD("Q & A Card"),
    CONCEPT_CARD("Concept Card"),
    FORMULA_CARD("Formula & Law")
}

/**
 * Full-screen Append Knowledge Sheet (mirroring MasterViewTab's AddQuestionDialog).
 * Appends new knowledge units to the end of an existing StudyNoteEntity cleanly while
 * keeping all existing bookmarks, mastery checks, and SM-2 flashcard progress intact.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppendKnowledgeSheet(
    note: StudyNoteEntity,
    existingUnitCount: Int,
    onAppendText: (rawAppendText: String, skipDuplicates: Boolean, onDone: (Int, Int) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val formatRule = remember(note.formatRuleJson, note.collectionType) { note.parsedFormatRule() }

    val defaultTemplate = remember(note.collectionType) {
        when (note.resolvedCollectionType()) {
            KnowledgeCollectionType.FLASHCARDS, KnowledgeCollectionType.LONG_ANSWER -> QuickKnowledgeItemTemplate.QA_CARD
            KnowledgeCollectionType.FORMULA_SHEET -> QuickKnowledgeItemTemplate.FORMULA_CARD
            KnowledgeCollectionType.MULTI_LINER, KnowledgeCollectionType.CONCEPT_CODEX, KnowledgeCollectionType.WEAK_AREA_REVIEW ->
                QuickKnowledgeItemTemplate.CONCEPT_CARD
            else -> QuickKnowledgeItemTemplate.ONE_LINER
        }
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0: Quick Item Form, 1: Paste & Append
    var selectedTemplate by remember { mutableStateOf(defaultTemplate) }
    var optionalHeading by remember { mutableStateOf("") }
    var fieldPrimary by remember { mutableStateOf("") }
    var fieldSecondary by remember { mutableStateOf("") }
    var fieldExtra by remember { mutableStateOf("") }

    var pastedBatchText by remember { mutableStateOf("") }
    var skipDuplicates by remember { mutableStateOf(true) }

    val nextIndex = existingUnitCount + 1

    val formattedSingleSnippet = remember(
        selectedTemplate,
        optionalHeading,
        fieldPrimary,
        fieldSecondary,
        fieldExtra,
        nextIndex
    ) {
        val p1 = fieldPrimary.trim()
        val p2 = fieldSecondary.trim()
        val p3 = fieldExtra.trim()
        if (p1.isBlank() && p2.isBlank()) {
            ""
        } else {
            buildString {
                if (optionalHeading.trim().isNotBlank()) {
                    appendLine("## ${optionalHeading.trim()}")
                }
                when (selectedTemplate) {
                    QuickKnowledgeItemTemplate.ONE_LINER -> {
                        if (p1.isNotBlank() && p2.isNotBlank()) {
                            append("$nextIndex. **$p1** -> $p2")
                        } else {
                            append("$nextIndex. ${p1.ifBlank { p2 }}")
                        }
                    }
                    QuickKnowledgeItemTemplate.QA_CARD -> {
                        appendLine("Q$nextIndex: ${p1.ifBlank { "Concept Question" }}")
                        if (p2.isNotBlank()) {
                            appendLine("A: $p2")
                        }
                        if (p3.isNotBlank()) {
                            append("  - Key Takeaway: $p3")
                        }
                    }
                    QuickKnowledgeItemTemplate.CONCEPT_CARD -> {
                        appendLine("$nextIndex. **${p1.ifBlank { "Concept" }}**")
                        if (p2.isNotBlank()) {
                            appendLine("  - Core Rule: $p2")
                        }
                        p3.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
                            val clean = line.removePrefix("-").removePrefix("•").trim()
                            appendLine("  - $clean")
                        }
                    }
                    QuickKnowledgeItemTemplate.FORMULA_CARD -> {
                        appendLine("$nextIndex. **${p1.ifBlank { "Formula" }}**")
                        if (p2.isNotBlank()) {
                            appendLine("  - Formula: $p2")
                        }
                        if (p3.isNotBlank()) {
                            append("  - Key Takeaway: $p3")
                        }
                    }
                }
            }.trim()
        }
    }

    val batchPreview = remember(pastedBatchText, note.rawContent, formatRule, skipDuplicates) {
        if (pastedBatchText.isBlank()) null
        else StudyNoteParser.prepareAppendedContent(
            existingRaw = note.rawContent,
            newRaw = pastedBatchText,
            rule = formatRule,
            skipDuplicates = skipDuplicates
        )
    }

    val canSubmit = if (activeTab == 0) {
        formattedSingleSnippet.isNotBlank()
    } else {
        batchPreview != null && batchPreview.appendSegment.isNotBlank()
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .testTag("append_knowledge_sheet")
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Surface(
                    color = colors.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("append_knowledge_close_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Append Sheet", tint = colors.inkBold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Append to Knowledge Set",
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${note.title} • Currently $existingUnitCount unit${if (existingUnitCount == 1) "" else "s"}",
                                    fontSize = 11.5.sp,
                                    color = colors.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = colors.surfaceVariant,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.navigationBars
                                    .union(WindowInsets.ime)
                                    .union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                        Row(
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier
                                    .weight(0.8f)
                                    .heightIn(min = 48.dp)
                                    .testTag("append_knowledge_cancel_button")
                            ) {
                                Text("Cancel", fontWeight = FontWeight.SemiBold, color = colors.inkBold)
                            }

                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                    if (activeTab == 0) {
                                        onAppendText(formattedSingleSnippet, skipDuplicates) { added, _ ->
                                            if (added > 0) onDismiss()
                                        }
                                    } else {
                                        onAppendText(pastedBatchText, skipDuplicates) { added, skipped ->
                                            if (added > 0 || skipped > 0) onDismiss()
                                        }
                                    }
                                },
                                enabled = canSubmit,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.teal,
                                    contentColor = Color.White,
                                    disabledContainerColor = colors.border,
                                    disabledContentColor = colors.muted
                                ),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .heightIn(min = 48.dp)
                                    .testTag("append_knowledge_submit_button")
                            ) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                val ctaText = if (activeTab == 0) {
                                    "Append Item #$nextIndex"
                                } else {
                                    val count = batchPreview?.addedUnitCount ?: 0
                                    if (count > 0) "Append $count Item${if (count == 1) "" else "s"}" else "Append Batch"
                                }
                                Text(ctaText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize()
                ) {
                    // 2-Tab Selector matching AddQuestionDialog
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        TabRow(
                            selectedTabIndex = activeTab,
                            containerColor = colors.surfaceVariant,
                            contentColor = colors.inkBold,
                            divider = {},
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier
                                        .tabIndicatorOffset(tabPositions[activeTab])
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                                    color = colors.teal,
                                    height = 3.dp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            Tab(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                modifier = Modifier.testTag("append_tab_quick_item"),
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Quick Item Form", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                            Tab(
                                selected = activeTab == 1,
                                onClick = { activeTab = 1 },
                                modifier = Modifier.testTag("append_tab_paste_batch"),
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Paste & Append", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }
                    }

                    // Non-destructive reassurance banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.tealLight.copy(alpha = 0.45f),
                        border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.teal, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "New items attach cleanly at the end (#$nextIndex onward). Your existing stars, mastery & SRS progress remain untouched.",
                                fontSize = 11.sp,
                                color = colors.tealDark,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (activeTab == 0) {
                            item {
                                Text(
                                    text = "Item Structure",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkMuted
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    QuickKnowledgeItemTemplate.entries.forEach { tpl ->
                                        FilterChip(
                                            selected = selectedTemplate == tpl,
                                            onClick = { selectedTemplate = tpl },
                                            label = { Text(tpl.label, fontSize = 11.5.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = colors.tealLight,
                                                selectedLabelColor = colors.tealDark
                                            )
                                        )
                                    }
                                }
                            }

                            item {
                                OutlinedTextField(
                                    value = optionalHeading,
                                    onValueChange = { optionalHeading = it },
                                    label = { Text("New Section Heading (Optional)") },
                                    placeholder = { Text("e.g., Important Exceptions") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item {
                                val primaryLabel = when (selectedTemplate) {
                                    QuickKnowledgeItemTemplate.ONE_LINER -> "Key Term / Concept *"
                                    QuickKnowledgeItemTemplate.QA_CARD -> "Question / Prompt *"
                                    QuickKnowledgeItemTemplate.CONCEPT_CARD -> "Concept Title *"
                                    QuickKnowledgeItemTemplate.FORMULA_CARD -> "Law / Formula Title *"
                                }
                                OutlinedTextField(
                                    value = fieldPrimary,
                                    onValueChange = { fieldPrimary = it },
                                    label = { Text(primaryLabel) },
                                    placeholder = {
                                        Text(
                                            when (selectedTemplate) {
                                                QuickKnowledgeItemTemplate.ONE_LINER -> "e.g., Article 32"
                                                QuickKnowledgeItemTemplate.QA_CARD -> "e.g., Why is the sky blue?"
                                                QuickKnowledgeItemTemplate.CONCEPT_CARD -> "e.g., Bernoulli's Principle"
                                                QuickKnowledgeItemTemplate.FORMULA_CARD -> "e.g., Faraday's Law"
                                            }
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("append_quick_primary_input")
                                )
                            }

                            item {
                                val secondaryLabel = when (selectedTemplate) {
                                    QuickKnowledgeItemTemplate.ONE_LINER -> "Concise Fact / Answer *"
                                    QuickKnowledgeItemTemplate.QA_CARD -> "Answer / Explanation *"
                                    QuickKnowledgeItemTemplate.CONCEPT_CARD -> "Core Explanation"
                                    QuickKnowledgeItemTemplate.FORMULA_CARD -> "Formula Expression (LaTeX or Text) *"
                                }
                                OutlinedTextField(
                                    value = fieldSecondary,
                                    onValueChange = { fieldSecondary = it },
                                    label = { Text(secondaryLabel) },
                                    minLines = if (selectedTemplate == QuickKnowledgeItemTemplate.ONE_LINER) 2 else 3,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("append_quick_secondary_input")
                                )
                            }

                            if (selectedTemplate != QuickKnowledgeItemTemplate.ONE_LINER) {
                                item {
                                    val extraLabel = when (selectedTemplate) {
                                        QuickKnowledgeItemTemplate.QA_CARD -> "Key Takeaway / Exam Trap (Optional)"
                                        QuickKnowledgeItemTemplate.CONCEPT_CARD -> "Sub-points (One per line, optional)"
                                        QuickKnowledgeItemTemplate.FORMULA_CARD -> "Units, Conditions & Traps (Optional)"
                                        else -> "Extra Note"
                                    }
                                    OutlinedTextField(
                                        value = fieldExtra,
                                        onValueChange = { fieldExtra = it },
                                        label = { Text(extraLabel) },
                                        minLines = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            if (formattedSingleSnippet.isNotBlank()) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                                        border = BorderStroke(0.8.dp, colors.border)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "FORMATTED APPEND PREVIEW",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = colors.tealDark
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = formattedSingleSnippet,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = colors.inkBold
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Paste & Append Batch Tab
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                                if (clip.isNotBlank()) {
                                                    pastedBatchText = clip
                                                } else {
                                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("append_paste_clipboard_button")
                                        ) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("Paste Clipboard", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        if (pastedBatchText.isNotBlank()) {
                                            TextButton(
                                                onClick = { pastedBatchText = "" },
                                                modifier = Modifier.heightIn(min = 48.dp)
                                            ) {
                                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Clear", fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Skip dupes", fontSize = 11.5.sp, color = colors.inkMuted)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Switch(
                                            checked = skipDuplicates,
                                            onCheckedChange = { skipDuplicates = it },
                                            colors = SwitchDefaults.colors(checkedTrackColor = colors.teal)
                                        )
                                    }
                                }
                            }

                            item {
                                OutlinedTextField(
                                    value = pastedBatchText,
                                    onValueChange = { pastedBatchText = it },
                                    placeholder = {
                                        Text(
                                            text = "Paste extra AI output or study points here...\nSupports One-Liners, Q1:/A:, Multi-Liner cards, tables & headings.\nNumbers continue automatically from #$nextIndex.",
                                            fontSize = 12.sp,
                                            color = colors.inkMuted
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.teal,
                                        unfocusedBorderColor = colors.border,
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 200.dp, max = 320.dp)
                                        .testTag("append_batch_text_input")
                                )
                            }

                            if (batchPreview != null) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (batchPreview.addedUnitCount > 0) colors.greenLight.copy(alpha = 0.55f) else colors.amberLight
                                        ),
                                        border = BorderStroke(0.8.dp, colors.border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = if (batchPreview.addedUnitCount > 0) {
                                                    "+${batchPreview.addedUnitCount} New Item${if (batchPreview.addedUnitCount == 1) "" else "s"} Ready (#$nextIndex..#${existingUnitCount + batchPreview.addedUnitCount})"
                                                } else if (batchPreview.structuralBlocksCount > 0) {
                                                    "${batchPreview.structuralBlocksCount} Structural Section(s) Ready"
                                                } else {
                                                    "No New Study Units Detected"
                                                },
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.inkBold
                                            )
                                            if (batchPreview.skippedDuplicatesCount > 0) {
                                                Text(
                                                    text = "${batchPreview.skippedDuplicatesCount} duplicate item(s) matched existing cards and will be skipped.",
                                                    fontSize = 11.sp,
                                                    color = colors.amber
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}
