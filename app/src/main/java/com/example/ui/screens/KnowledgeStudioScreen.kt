package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PaperEntity
import com.example.data.StudyNoteEntity
import com.example.model.FolderItem
import com.example.model.KnowledgeCollectionType
import com.example.model.KnowledgeDepth
import com.example.model.KnowledgeFormatRule
import com.example.model.KnowledgeLayerOrder
import com.example.model.KnowledgeTransformAction
import com.example.ui.KnowledgeEditorDraft
import com.example.ui.components.LatexText
import com.example.ui.theme.ExamTheme
import com.example.util.StudyNoteBlock
import com.example.util.StudyNoteParser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KnowledgeStudioEditorScreen(
    draft: KnowledgeEditorDraft,
    folders: List<FolderItem>,
    savedPapers: List<PaperEntity>,
    onSave: (
        id: Long,
        title: String,
        subject: String,
        sourceAi: String,
        rawContent: String,
        folderId: String?,
        collectionType: KnowledgeCollectionType,
        customLabel: String,
        linkedPaperId: Long?,
        formatRule: KnowledgeFormatRule
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val initialNote = draft.note

    val initialRule = remember(draft) {
        initialNote?.parsedFormatRule() ?: KnowledgeFormatRule(collectionType = draft.defaultCollectionType)
    }

    var title by remember(draft) { mutableStateOf(initialNote?.title ?: draft.prefillTitle) }
    var subject by remember(draft) { mutableStateOf(initialNote?.subject ?: draft.defaultSubject) }
    var sourceAi by remember(draft) { mutableStateOf(initialNote?.sourceAi ?: "") }
    var selectedFolderId by remember(draft) { mutableStateOf(initialNote?.folderId ?: draft.defaultFolderId) }
    var collectionType by remember(draft) {
        mutableStateOf(initialNote?.resolvedCollectionType() ?: draft.defaultCollectionType)
    }
    var customLabel by remember(draft) { mutableStateOf(initialNote?.customLabel ?: "") }
    var linkedPaperId by remember(draft) { mutableStateOf(initialNote?.linkedPaperId ?: draft.defaultLinkedPaperId) }
    var rawContent by remember(draft) { mutableStateOf(initialNote?.rawContent ?: draft.prefillContent) }
    var previousContentBeforeTransform by remember(draft) { mutableStateOf<String?>(null) }

    // Compiled Prompt Composer & Parser Rule state
    var promptTopic by remember(draft) { mutableStateOf(initialNote?.title ?: draft.prefillTitle) }
    var layerOrder by remember(draft) { mutableStateOf(initialRule.layerOrder) }
    var depth by remember(draft) { mutableStateOf(initialRule.depth) }
    var language by remember(draft) { mutableStateOf(initialRule.language) }
    var preserveNestedBullets by remember(draft) { mutableStateOf(initialRule.preserveNestedBullets) }
    var includeLatexMath by remember(draft) { mutableStateOf(initialRule.includeLatexMath) }
    var includeCallouts by remember(draft) { mutableStateOf(initialRule.includeCallouts) }
    var includeComparisonTable by remember(draft) { mutableStateOf(initialRule.includeComparisonTable) }
    var customInstruction by remember(draft) { mutableStateOf(initialRule.customInstruction) }

    var showMetadataPanel by remember(draft) { mutableStateOf(initialNote == null) }
    var showPromptComposer by remember(draft) { mutableStateOf(false) }
    var isPreviewMode by remember(draft) { mutableStateOf(false) }
    var showFolderDropdown by remember { mutableStateOf(false) }
    var showLinkedPaperDropdown by remember { mutableStateOf(false) }

    val activeRule = remember(
        collectionType,
        customLabel,
        layerOrder,
        depth,
        language,
        preserveNestedBullets,
        includeLatexMath,
        includeCallouts,
        includeComparisonTable,
        customInstruction
    ) {
        KnowledgeFormatRule(
            collectionType = collectionType,
            customCollectionLabel = customLabel.trim(),
            layerOrder = layerOrder,
            depth = depth,
            language = language.ifBlank { "English" },
            preserveNestedBullets = preserveNestedBullets,
            includeLatexMath = includeLatexMath,
            includeCallouts = includeCallouts,
            includeComparisonTable = includeComparisonTable,
            customInstruction = customInstruction
        )
    }

    val parsedBlocks = remember(rawContent, activeRule) {
        StudyNoteParser.parseBlocks(rawContent, activeRule)
    }
    val factCount = remember(parsedBlocks) {
        parsedBlocks.count { it is StudyNoteBlock.OneLiner || it is StudyNoteBlock.MultiLinerCard }
    }
    val structuralCount = remember(parsedBlocks) {
        parsedBlocks.count { it is StudyNoteBlock.Heading || it is StudyNoteBlock.Table || it is StudyNoteBlock.Callout }
    }

    BackHandler { onDismiss() }

    // Edge-to-edge full-screen Surface (NO Dialog window) so IME & navigation insets never clip bottom text box
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        color = colors.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Action Header
            Surface(color = colors.surface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close editor", tint = colors.inkBold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (initialNote != null) "Edit Knowledge Set" else "New Knowledge Set",
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (rawContent.isBlank()) "One-liners, Multi-liners & Study Codex"
                                else "$factCount items • $structuralCount headings/tables",
                                fontSize = 11.sp,
                                color = colors.tealDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Button(
                        onClick = {
                            onSave(
                                initialNote?.id ?: 0L,
                                title,
                                subject,
                                sourceAi,
                                rawContent,
                                selectedFolderId,
                                collectionType,
                                customLabel,
                                linkedPaperId,
                                activeRule
                            )
                            onDismiss()
                        },
                        enabled = rawContent.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .heightIn(min = 48.dp)
                            .testTag("save_knowledge_set_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Collapsible Metadata / Rename / Question-Set Linking Accordion
            Surface(
                color = colors.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMetadataPanel = !showMetadataPanel }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = colors.teal, modifier = Modifier.size(16.dp))
                            Text(
                                text = customLabel.trim().ifBlank { collectionType.label } +
                                    if (title.isNotBlank()) " • $title" else " • Setup, Rename & Link Set",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = if (showMetadataPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showMetadataPanel) "Collapse setup" else "Expand setup",
                            tint = colors.inkMuted
                        )
                    }

                    AnimatedVisibility(visible = showMetadataPanel) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Knowledge Set Title") },
                                placeholder = { Text("e.g., Polity Articles & Doctrines") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("study_note_title_input")
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = subject,
                                    onValueChange = { subject = it },
                                    label = { Text("Subject / Topic") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Box {
                                    val selectedFolderName = folders.find { it.id == selectedFolderId }?.name ?: "Root"
                                    OutlinedButton(
                                        onClick = { showFolderDropdown = true },
                                        modifier = Modifier.heightIn(min = 56.dp)
                                    ) {
                                        Text(selectedFolderName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    DropdownMenu(expanded = showFolderDropdown, onDismissRequest = { showFolderDropdown = false }) {
                                        DropdownMenuItem(
                                            text = { Text("All / Root") },
                                            onClick = {
                                                selectedFolderId = null
                                                showFolderDropdown = false
                                            }
                                        )
                                        folders.forEach { folder ->
                                            DropdownMenuItem(
                                                text = { Text(folder.name) },
                                                onClick = {
                                                    selectedFolderId = folder.id
                                                    showFolderDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Collection Type Chips + Custom Rename Label
                            Column {
                                Text(
                                    text = "Collection Format & Custom Name",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkMuted
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    KnowledgeCollectionType.entries.forEach { type ->
                                        FilterChip(
                                            selected = collectionType == type,
                                            onClick = { collectionType = type },
                                            label = { Text(type.label, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = colors.tealLight,
                                                selectedLabelColor = colors.tealDark
                                            )
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = customLabel,
                                    onValueChange = { customLabel = it },
                                    label = { Text("Custom Badge Name") },
                                    placeholder = { Text("e.g., Revision Codex") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = sourceAi,
                                    onValueChange = { sourceAi = it },
                                    label = { Text("Origin Tag (Optional)") },
                                    placeholder = { Text("Auto / ChatGPT / Self") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Link to Question Set
                            Box(modifier = Modifier.fillMaxWidth()) {
                                val linkedPaper = savedPapers.find { it.id == linkedPaperId }
                                OutlinedButton(
                                    onClick = { showLinkedPaperDropdown = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = colors.teal, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (linkedPaper != null) "Linked Question Set: ${linkedPaper.title}" else "Link to a Question Set (Optional)",
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                DropdownMenu(expanded = showLinkedPaperDropdown, onDismissRequest = { showLinkedPaperDropdown = false }) {
                                    DropdownMenuItem(
                                        text = { Text("No Linked Question Set") },
                                        onClick = {
                                            linkedPaperId = null
                                            showLinkedPaperDropdown = false
                                        }
                                    )
                                    savedPapers.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text("${p.title} (${p.setNumber})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            onClick = {
                                                linkedPaperId = p.id
                                                if (subject.isBlank()) subject = p.subject
                                                if (selectedFolderId == null) selectedFolderId = p.folderId
                                                showLinkedPaperDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    HorizontalDivider(color = colors.border.copy(alpha = 0.6f))

                    // Compiled AI Prompt Composer Accordion
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPromptComposer = !showPromptComposer }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = colors.amber, modifier = Modifier.size(16.dp))
                            Text(
                                text = "AI Prompt Composer & Parser Rules (${layerOrder.label})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = if (showPromptComposer) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle AI prompt composer",
                            tint = colors.inkMuted
                        )
                    }

                    AnimatedVisibility(visible = showPromptComposer) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 290.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = promptTopic,
                                    onValueChange = { promptTopic = it },
                                    label = { Text("Prompt Topic / Chapter") },
                                    placeholder = { Text(title.ifBlank { "e.g., Thermodynamics Laws" }) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.4f)
                                )
                                OutlinedTextField(
                                    value = language,
                                    onValueChange = { language = it },
                                    label = { Text("Language") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.9f)
                                )
                            }

                            Text("Layer Order (Fixes Backwards ChatGPT Layers)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.inkMuted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                KnowledgeLayerOrder.entries.forEach { order ->
                                    FilterChip(
                                        selected = layerOrder == order,
                                        onClick = { layerOrder = order },
                                        label = { Text(order.label, fontSize = 10.5.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colors.tealLight,
                                            selectedLabelColor = colors.tealDark
                                        )
                                    )
                                }
                            }

                            Text("Concept Depth", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.inkMuted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                KnowledgeDepth.entries.forEach { d ->
                                    FilterChip(
                                        selected = depth == d,
                                        onClick = { depth = d },
                                        label = { Text(d.label, fontSize = 10.5.sp) }
                                    )
                                }
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = preserveNestedBullets,
                                        onCheckedChange = { preserveNestedBullets = it },
                                        colors = SwitchDefaults.colors(checkedTrackColor = colors.teal)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Multi-Liner Bullets", fontSize = 11.sp, color = colors.inkBold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = includeLatexMath,
                                        onCheckedChange = { includeLatexMath = it },
                                        colors = SwitchDefaults.colors(checkedTrackColor = colors.teal)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("LaTeX Math", fontSize = 11.sp, color = colors.inkBold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = includeCallouts,
                                        onCheckedChange = { includeCallouts = it },
                                        colors = SwitchDefaults.colors(checkedTrackColor = colors.teal)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Exam Tips", fontSize = 11.sp, color = colors.inkBold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = includeComparisonTable,
                                        onCheckedChange = { includeComparisonTable = it },
                                        colors = SwitchDefaults.colors(checkedTrackColor = colors.teal)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Comparison Table", fontSize = 11.sp, color = colors.inkBold)
                                }
                            }

                            OutlinedTextField(
                                value = customInstruction,
                                onValueChange = { customInstruction = it },
                                label = { Text("Custom User Formatting Rule (Optional)") },
                                placeholder = { Text("e.g., Include Hindi keywords & PYQ year tags") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    val topicToUse = promptTopic.ifBlank { title.ifBlank { subject } }
                                    val compiled = StudyNoteParser.compilePrompt(topicToUse, collectionType, activeRule)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("ExamSet Compiled Prompt", compiled))
                                    Toast.makeText(context, "Compiled prompt copied! Paste output from AI back here.", Toast.LENGTH_LONG).show()
                                    showPromptComposer = false
                                    showMetadataPanel = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.navyBanner, contentColor = colors.navyBannerText),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("copy_compiled_prompt_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Compiled Prompt & Apply Rules", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            // Toolbar: Write vs Live Preview + Paste + 1-Tap Transform Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !isPreviewMode,
                            onClick = { isPreviewMode = false },
                            label = { Text("Write & Paste", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.navyBanner,
                                selectedLabelColor = colors.navyBannerText
                            )
                        )
                        FilterChip(
                            selected = isPreviewMode,
                            onClick = { isPreviewMode = true },
                            label = { Text("Preview (${parsedBlocks.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.tealLight,
                                selectedLabelColor = colors.tealDark
                            )
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (previousContentBeforeTransform != null) {
                            OutlinedButton(
                                onClick = {
                                    rawContent = previousContentBeforeTransform!!
                                    previousContentBeforeTransform = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 38.dp)
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = "Undo transform", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Undo", fontSize = 11.sp)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                if (clip.isNotBlank()) {
                                    rawContent = clip
                                    showMetadataPanel = false
                                    showPromptComposer = false
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 38.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // 1-Tap Transform Chips (handles backwards ChatGPT layers, chatter, multi-liner nesting)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KnowledgeTransformAction.entries.forEach { action ->
                        Surface(
                            onClick = {
                                if (rawContent.isNotBlank()) {
                                    previousContentBeforeTransform = rawContent
                                    rawContent = StudyNoteParser.applyTransform(rawContent, action)
                                    Toast.makeText(context, "Applied: ${action.label}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = colors.blueLight,
                            border = BorderStroke(0.7.dp, colors.teal.copy(alpha = 0.35f)),
                            modifier = Modifier.heightIn(min = 34.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = colors.teal, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(action.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.tealDark)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

            // Main Unclipped Workspace (Weight 1f with NO outer verticalScroll -> zero bottom cutoff!)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (!isPreviewMode) {
                    OutlinedTextField(
                        value = rawContent,
                        onValueChange = { rawContent = it },
                        placeholder = {
                            Text(
                                text = "Write or paste any study format here...\n\nSupports:\n• Crisp One-Liners: `1. **Article 32** -> Constitutional Remedies`\n• Multi-Liner Cards with 2-space indented `- sub-points`, `Formula:`, `Example:`\n• Backwards ChatGPT outputs (`Detail -> **Term**` — use Fix Backwards ChatGPT chip above)\n• Markdown comparison tables & `> Exam Tip:` callouts",
                                fontSize = 12.5.sp,
                                color = colors.inkMuted,
                                lineHeight = 18.sp
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.teal,
                            unfocusedBorderColor = colors.border,
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("study_note_content_input")
                    )
                } else {
                    if (parsedBlocks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Switch to Write & Paste to add content and preview live parsed blocks.",
                                fontSize = 13.sp,
                                color = colors.inkMuted
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(parsedBlocks, key = { "preview_${it.blockIndex}" }) { block ->
                                KnowledgeBlockItemView(
                                    block = block,
                                    isStarred = false,
                                    isMastered = false,
                                    isHiddenRecall = false,
                                    flipPair = false,
                                    isExpanded = true,
                                    onReveal = {},
                                    onToggleExpand = {},
                                    onToggleStar = null,
                                    onToggleMastered = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KnowledgeStudioReaderScreen(
    note: StudyNoteEntity,
    linkedPaperTitle: String? = null,
    onToggleBookmark: (StudyNoteEntity, Int) -> Unit,
    onToggleMastered: (StudyNoteEntity, Int) -> Unit,
    onEdit: () -> Unit,
    onOpenLinkedPaper: ((Long) -> Unit)? = null,
    onGenerateMcqDraft: (StudyNoteEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val formatRule = remember(note.formatRuleJson, note.collectionType) { note.parsedFormatRule() }

    var searchQuery by remember { mutableStateOf("") }
    var recallMode by remember { mutableStateOf(false) }
    var flipPair by remember { mutableStateOf(false) }
    var starredOnly by remember { mutableStateOf(false) }
    var hideMastered by remember { mutableStateOf(false) }
    var expandAllMultiLiners by remember { mutableStateOf(true) }
    var expandedCardOverrides by remember(note.id) { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }
    var revealedIndices by remember(note.id, recallMode) { mutableStateOf(setOf<Int>()) }

    val blocks = remember(note.rawContent, formatRule) {
        StudyNoteParser.parseBlocks(note.rawContent, formatRule)
    }
    val bookmarked = remember(note.bookmarkedLinesJson) { note.getBookmarkedIndices() }
    val mastered = remember(note.masteredBlocksJson) { note.getMasteredIndices() }

    val totalStudyItems = remember(blocks) {
        blocks.count { it is StudyNoteBlock.OneLiner || it is StudyNoteBlock.MultiLinerCard }
    }

    val visibleBlocks = remember(blocks, searchQuery, starredOnly, hideMastered, bookmarked, mastered) {
        blocks.filter { block ->
            val isStudyUnit = block is StudyNoteBlock.OneLiner || block is StudyNoteBlock.MultiLinerCard
            val starOk = !starredOnly || (isStudyUnit && block.blockIndex in bookmarked)
            val masteredOk = !hideMastered || !(isStudyUnit && block.blockIndex in mastered)
            val searchOk = searchQuery.isBlank() || when (block) {
                is StudyNoteBlock.Heading -> block.text.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.OneLiner -> block.leadTerm.contains(searchQuery, ignoreCase = true) ||
                    block.detail.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.MultiLinerCard -> block.title.contains(searchQuery, ignoreCase = true) ||
                    block.paragraphs.any { it.contains(searchQuery, ignoreCase = true) } ||
                    block.nestedBullets.any { it.second.contains(searchQuery, ignoreCase = true) } ||
                    block.formula.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.Callout -> block.label.contains(searchQuery, ignoreCase = true) ||
                    block.body.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.Paragraph -> block.text.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.Table -> block.headers.any { it.contains(searchQuery, ignoreCase = true) } ||
                    block.rows.flatten().any { it.contains(searchQuery, ignoreCase = true) }
            }
            starOk && masteredOk && searchOk
        }
    }

    BackHandler { onDismiss() }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = colors.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = colors.surface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.inkBold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.title,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${note.effectiveBadgeLabel()} • ${note.subject.ifBlank { "Knowledge Set" }}",
                                        fontSize = 11.sp,
                                        color = colors.inkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (totalStudyItems > 0) {
                                        Surface(shape = RoundedCornerShape(4.dp), color = colors.greenLight) {
                                            Text(
                                                text = "${mastered.size}/$totalStudyItems mastered",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.green,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onGenerateMcqDraft(note) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Quiz, contentDescription = "Draft Quiz from Knowledge Set", tint = colors.navyBanner)
                            }
                            IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Knowledge Set", tint = colors.inkBold)
                            }
                            IconButton(
                                onClick = { printStudyNoteAsPdf(context, note) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Print A4 PDF", tint = colors.teal)
                            }
                        }
                    }

                    if (!linkedPaperTitle.isNullOrBlank() && note.linkedPaperId != null && onOpenLinkedPaper != null) {
                        Surface(
                            onClick = { onOpenLinkedPaper(note.linkedPaperId) },
                            shape = RoundedCornerShape(8.dp),
                            color = colors.blueLight,
                            border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text("Open Set →", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = recallMode,
                            onClick = { recallMode = !recallMode },
                            leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text("Active Recall", fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.tealLight,
                                selectedLabelColor = colors.tealDark
                            )
                        )
                        FilterChip(
                            selected = flipPair,
                            onClick = { flipPair = !flipPair },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text("Flip Term", fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.blueLight,
                                selectedLabelColor = colors.tealDark
                            )
                        )
                        FilterChip(
                            selected = starredOnly,
                            onClick = { starredOnly = !starredOnly },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text("Starred (${bookmarked.size})", fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.amberLight,
                                selectedLabelColor = colors.amber
                            )
                        )
                        FilterChip(
                            selected = hideMastered,
                            onClick = { hideMastered = !hideMastered },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text("Hide Mastered", fontSize = 11.5.sp) }
                        )
                        if (blocks.any { it is StudyNoteBlock.MultiLinerCard }) {
                            TextButton(
                                onClick = {
                                    expandAllMultiLiners = !expandAllMultiLiners
                                    expandedCardOverrides = emptyMap()
                                }
                            ) {
                                Text(
                                    text = if (expandAllMultiLiners) "Collapse Cards" else "Expand All",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.teal
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search facts, formulas, or sub-points...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleBlocks, key = { "block_${it.blockIndex}" }) { block ->
                    val isStarred = block.blockIndex in bookmarked
                    val isMastered = block.blockIndex in mastered
                    val isHiddenRecall = recallMode &&
                        ((block is StudyNoteBlock.OneLiner && block.hasRecallTarget) || block is StudyNoteBlock.MultiLinerCard) &&
                        block.blockIndex !in revealedIndices
                    val isExpanded = expandedCardOverrides[block.blockIndex] ?: expandAllMultiLiners

                    KnowledgeBlockItemView(
                        block = block,
                        isStarred = isStarred,
                        isMastered = isMastered,
                        isHiddenRecall = isHiddenRecall,
                        flipPair = flipPair,
                        isExpanded = isExpanded,
                        onReveal = { revealedIndices = revealedIndices + block.blockIndex },
                        onToggleExpand = {
                            expandedCardOverrides = expandedCardOverrides + (block.blockIndex to !isExpanded)
                        },
                        onToggleStar = { onToggleBookmark(note, block.blockIndex) },
                        onToggleMastered = { onToggleMastered(note, block.blockIndex) }
                    )
                }
                item { Spacer(modifier = Modifier.height(36.dp)) }
            }
        }
    }
}

@Composable
fun KnowledgeBlockItemView(
    block: StudyNoteBlock,
    isStarred: Boolean,
    isMastered: Boolean,
    isHiddenRecall: Boolean,
    flipPair: Boolean,
    isExpanded: Boolean,
    onReveal: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleStar: (() -> Unit)?,
    onToggleMastered: (() -> Unit)?
) {
    val colors = ExamTheme.colors

    when (block) {
        is StudyNoteBlock.Heading -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.tealLight.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = block.text,
                    fontSize = if (block.level <= 2) 15.5.sp else 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.tealDark,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }

        is StudyNoteBlock.OneLiner -> {
            val lead = if (flipPair && block.leadTerm.isNotBlank()) block.detail else block.leadTerm
            val body = if (flipPair && block.leadTerm.isNotBlank()) block.leadTerm else block.detail

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isMastered -> colors.greenLight.copy(alpha = 0.35f)
                        isStarred -> colors.amberLight.copy(alpha = 0.45f)
                        else -> colors.surface
                    }
                ),
                border = BorderStroke(
                    0.9.dp,
                    when {
                        isMastered -> colors.green.copy(alpha = 0.55f)
                        isStarred -> colors.amber
                        else -> colors.border
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.navyBanner.copy(alpha = 0.1f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = block.displayIndex,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.navyBanner,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        if (lead.isNotBlank()) {
                            LatexText(
                                text = lead,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                        if (isHiddenRecall) {
                            Surface(
                                onClick = onReveal,
                                shape = RoundedCornerShape(8.dp),
                                color = colors.tealLight,
                                border = BorderStroke(0.8.dp, colors.teal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "Tap to reveal recall target",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.tealDark,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            LatexText(
                                text = body,
                                fontSize = 13.5.sp,
                                color = colors.ink
                            )
                        }
                    }
                    if (onToggleStar != null || onToggleMastered != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (onToggleStar != null) {
                                IconButton(onClick = onToggleStar, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = if (isStarred) "Unstar fact" else "Star fact",
                                        tint = if (isStarred) colors.amber else colors.inkMuted
                                    )
                                }
                            }
                            if (onToggleMastered != null) {
                                IconButton(onClick = onToggleMastered, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        imageVector = if (isMastered) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = if (isMastered) "Mark unmastered" else "Mark mastered",
                                        tint = if (isMastered) colors.green else colors.inkMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        is StudyNoteBlock.MultiLinerCard -> {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isMastered -> colors.greenLight.copy(alpha = 0.28f)
                        isStarred -> colors.amberLight.copy(alpha = 0.35f)
                        else -> colors.surface
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    when {
                        isMastered -> colors.green.copy(alpha = 0.55f)
                        isStarred -> colors.amber
                        else -> colors.border
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleExpand() },
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.teal.copy(alpha = 0.14f),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = block.displayIndex,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.tealDark,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (block.badge.isNotBlank()) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = colors.navyBanner.copy(alpha = 0.1f)) {
                                        Text(
                                            text = block.badge,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.navyBanner,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (block.nestedBullets.isNotEmpty()) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = colors.surfaceVariant) {
                                        Text(
                                            text = "${block.nestedBullets.size} sub-points",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.inkMuted,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            LatexText(
                                text = block.title,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }
                        IconButton(onClick = onToggleExpand, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse card" else "Expand card",
                                tint = colors.inkMuted
                            )
                        }
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        if (isHiddenRecall) {
                            Surface(
                                onClick = onReveal,
                                shape = RoundedCornerShape(8.dp),
                                color = colors.tealLight,
                                border = BorderStroke(0.8.dp, colors.teal),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Tap to reveal explanation, formula & sub-points",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.tealDark,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        } else {
                            block.paragraphs.forEach { para ->
                                LatexText(
                                    text = para,
                                    fontSize = 13.sp,
                                    color = colors.ink,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }

                            if (block.formula.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.blueLight,
                                    border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LatexText(
                                        text = block.formula,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.tealDark,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            if (block.nestedBullets.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    block.nestedBullets.forEach { (level, bulletText) ->
                                        val indentDp = ((level - 1).coerceIn(0, 2) * 14).dp
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = indentDp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = if (level <= 1) "•" else "◦",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.teal,
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                            LatexText(
                                                text = bulletText,
                                                fontSize = 12.5.sp,
                                                color = colors.ink,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (onToggleStar != null || onToggleMastered != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = colors.border.copy(alpha = 0.45f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (onToggleStar != null) {
                                    TextButton(onClick = onToggleStar) {
                                        Icon(
                                            imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null,
                                            tint = if (isStarred) colors.amber else colors.inkMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isStarred) "Starred" else "Star",
                                            fontSize = 11.5.sp,
                                            color = if (isStarred) colors.amber else colors.inkMuted
                                        )
                                    }
                                }
                                if (onToggleMastered != null) {
                                    TextButton(onClick = onToggleMastered) {
                                        Icon(
                                            imageVector = if (isMastered) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isMastered) colors.green else colors.inkMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isMastered) "Mastered" else "Mark Mastered",
                                            fontSize = 11.5.sp,
                                            color = if (isMastered) colors.green else colors.inkMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        is StudyNoteBlock.Callout -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.blueLight.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = block.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.tealDark
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    LatexText(text = block.body, fontSize = 13.sp, color = colors.inkBold)
                }
            }
        }

        is StudyNoteBlock.Table -> {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    Row(modifier = Modifier.background(colors.surfaceVariant)) {
                        block.headers.forEach { hdr ->
                            Text(
                                text = hdr,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                modifier = Modifier
                                    .width(140.dp)
                                    .padding(8.dp)
                            )
                        }
                    }
                    block.rows.forEachIndexed { idx, row ->
                        Row(
                            modifier = Modifier.background(
                                if (idx % 2 == 1) colors.background else colors.surface
                            )
                        ) {
                            block.headers.indices.forEach { cIdx ->
                                LatexText(
                                    text = row.getOrNull(cIdx).orEmpty(),
                                    fontSize = 12.sp,
                                    color = colors.ink,
                                    modifier = Modifier
                                        .width(140.dp)
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        is StudyNoteBlock.Paragraph -> {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.6.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                LatexText(
                    text = block.text,
                    fontSize = 13.5.sp,
                    color = colors.ink,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
