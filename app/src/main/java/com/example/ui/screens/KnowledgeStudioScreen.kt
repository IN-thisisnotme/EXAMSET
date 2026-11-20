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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

enum class RecallMaskMode(
    val shortLabel: String,
    val title: String,
    val subtitle: String
) {
    ANSWER_AND_EXPLANATION(
        shortLabel = "Answer & Solution",
        title = "Hide Answer & Explanation",
        subtitle = "Question stem, type badge & options remain visible; recall the right answer and reasoning."
    ),
    EXPLANATION_ONLY(
        shortLabel = "Explanation Only",
        title = "Hide Explanation & Formula",
        subtitle = "Question & correct answer remain visible; recall the underlying formula, rule, or why distractors fail."
    ),
    PURE_FREE_RECALL(
        shortLabel = "Free Recall",
        title = "Hide Options, Answer & Solution",
        subtitle = "Show only the question stem and type without MCQ choices; test pure memory recall."
    ),
    REVERSE_PROMPT(
        shortLabel = "Reverse Clue",
        title = "Reverse (Answer/Clue → Question)",
        subtitle = "See the key answer/takeaway first and recall the concept term or question scenario."
    )
}

enum class RecallTypeFilter(val label: String) {
    ALL("All Items"),
    INCORRECT("Incorrect"),
    SKIPPED("Skipped"),
    MCQ("MCQ Cards"),
    CONCEPTS("Concepts & Formulas"),
    ONE_LINERS("One-Liners"),
    QA_CARDS("Q & A")
}

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
        initialNote?.parsedFormatRule()
            ?: draft.defaultFormatRule
            ?: KnowledgeFormatRule(collectionType = draft.defaultCollectionType)
    }

    var title by remember(draft) { mutableStateOf(initialNote?.title ?: draft.prefillTitle) }
    var subject by remember(draft) { mutableStateOf(initialNote?.subject ?: draft.defaultSubject) }
    var sourceAi by remember(draft) { mutableStateOf(initialNote?.sourceAi ?: "") }
    var selectedFolderId by remember(draft) { mutableStateOf(initialNote?.folderId ?: draft.defaultFolderId) }
    var collectionType by remember(draft) {
        mutableStateOf(initialNote?.resolvedCollectionType() ?: draft.defaultCollectionType)
    }
    var customLabel by remember(draft) {
        mutableStateOf(initialNote?.customLabel ?: initialRule.customCollectionLabel)
    }
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

@OptIn(ExperimentalLayoutApi::class)
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
    var showSearchBar by remember { mutableStateOf(false) }
    var recallMode by remember { mutableStateOf(false) }
    var maskMode by remember { mutableStateOf(RecallMaskMode.ANSWER_AND_EXPLANATION) }
    var typeFilter by remember { mutableStateOf(RecallTypeFilter.ALL) }
    var starredOnly by remember { mutableStateOf(false) }
    var hideMastered by remember { mutableStateOf(false) }
    var showTypeBadges by remember { mutableStateOf(true) }
    var showOptionsBeforeReveal by remember { mutableStateOf(true) }
    var isDeckMode by remember { mutableStateOf(false) }
    var deckIndex by remember(note.id) { mutableIntStateOf(0) }
    var showRecallSettingsDialog by remember { mutableStateOf(false) }
    var expandAllMultiLiners by remember { mutableStateOf(true) }
    var expandedCardOverrides by remember(note.id) { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }
    var revealedIndices by remember(note.id, recallMode, maskMode) { mutableStateOf(setOf<Int>()) }
    var selfSelectedOptions by remember(note.id, recallMode) { mutableStateOf<Map<Int, String>>(emptyMap()) }

    val flipPair = maskMode == RecallMaskMode.REVERSE_PROMPT

    val blocks = remember(note.rawContent, formatRule) {
        StudyNoteParser.parseBlocks(note.rawContent, formatRule)
    }
    val bookmarked = remember(note.bookmarkedLinesJson) { note.getBookmarkedIndices() }
    val mastered = remember(note.masteredBlocksJson) { note.getMasteredIndices() }

    val studyUnits = remember(blocks) {
        blocks.filter { it is StudyNoteBlock.OneLiner || it is StudyNoteBlock.MultiLinerCard }
    }
    val totalStudyItems = studyUnits.size

    // Available question/item type counts for dynamic filtering
    val incorrectCount = remember(studyUnits) {
        studyUnits.count { it is StudyNoteBlock.MultiLinerCard && it.statusTag.contains("Incorrect", ignoreCase = true) }
    }
    val skippedCount = remember(studyUnits) {
        studyUnits.count { it is StudyNoteBlock.MultiLinerCard && it.statusTag.contains("Skipped", ignoreCase = true) }
    }
    val mcqCardCount = remember(studyUnits) {
        studyUnits.count { it is StudyNoteBlock.MultiLinerCard && it.isQuestionCard }
    }
    val conceptCount = remember(studyUnits) {
        studyUnits.count { it is StudyNoteBlock.MultiLinerCard && !it.isQuestionCard && !it.badge.equals("Q & A", ignoreCase = true) }
    }
    val oneLinerCount = remember(studyUnits) {
        studyUnits.count { it is StudyNoteBlock.OneLiner }
    }
    val qaCardCount = remember(studyUnits) {
        studyUnits.count { it is StudyNoteBlock.MultiLinerCard && it.badge.equals("Q & A", ignoreCase = true) }
    }

    fun matchesTypeFilter(block: StudyNoteBlock): Boolean {
        return when (typeFilter) {
            RecallTypeFilter.ALL -> true
            RecallTypeFilter.INCORRECT -> block is StudyNoteBlock.MultiLinerCard && block.statusTag.contains("Incorrect", ignoreCase = true)
            RecallTypeFilter.SKIPPED -> block is StudyNoteBlock.MultiLinerCard && block.statusTag.contains("Skipped", ignoreCase = true)
            RecallTypeFilter.MCQ -> block is StudyNoteBlock.MultiLinerCard && block.isQuestionCard
            RecallTypeFilter.CONCEPTS -> block is StudyNoteBlock.MultiLinerCard && !block.isQuestionCard && !block.badge.equals("Q & A", ignoreCase = true)
            RecallTypeFilter.ONE_LINERS -> block is StudyNoteBlock.OneLiner
            RecallTypeFilter.QA_CARDS -> block is StudyNoteBlock.MultiLinerCard && block.badge.equals("Q & A", ignoreCase = true)
        }
    }

    val visibleBlocks = remember(blocks, searchQuery, starredOnly, hideMastered, typeFilter, bookmarked, mastered) {
        blocks.filter { block ->
            val isStudyUnit = block is StudyNoteBlock.OneLiner || block is StudyNoteBlock.MultiLinerCard
            val starOk = !starredOnly || (isStudyUnit && block.blockIndex in bookmarked)
            val masteredOk = !hideMastered || !(isStudyUnit && block.blockIndex in mastered)
            val typeOk = if (typeFilter == RecallTypeFilter.ALL) true else (isStudyUnit && matchesTypeFilter(block))
            val searchOk = searchQuery.isBlank() || when (block) {
                is StudyNoteBlock.Heading -> block.text.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.OneLiner -> block.leadTerm.contains(searchQuery, ignoreCase = true) ||
                    block.detail.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.MultiLinerCard -> block.title.contains(searchQuery, ignoreCase = true) ||
                    block.questionType.contains(searchQuery, ignoreCase = true) ||
                    block.statusTag.contains(searchQuery, ignoreCase = true) ||
                    block.correctAnswer.contains(searchQuery, ignoreCase = true) ||
                    block.keyTakeaway.contains(searchQuery, ignoreCase = true) ||
                    block.optionsList.any { it.second.contains(searchQuery, ignoreCase = true) } ||
                    block.paragraphs.any { it.contains(searchQuery, ignoreCase = true) } ||
                    block.nestedBullets.any { it.second.contains(searchQuery, ignoreCase = true) } ||
                    block.formula.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.Callout -> block.label.contains(searchQuery, ignoreCase = true) ||
                    block.body.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.Paragraph -> block.text.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.Table -> block.headers.any { it.contains(searchQuery, ignoreCase = true) } ||
                    block.rows.flatten().any { it.contains(searchQuery, ignoreCase = true) }
            }
            starOk && masteredOk && typeOk && searchOk
        }
    }

    val recallableVisibleUnits = remember(visibleBlocks) {
        visibleBlocks.filter {
            (it is StudyNoteBlock.OneLiner && it.hasRecallTarget) || it is StudyNoteBlock.MultiLinerCard
        }
    }
    val revealedVisibleCount = remember(recallableVisibleUnits, revealedIndices) {
        recallableVisibleUnits.count { it.blockIndex in revealedIndices }
    }

    val deckItems = remember(visibleBlocks) {
        visibleBlocks.filter { it is StudyNoteBlock.OneLiner || it is StudyNoteBlock.MultiLinerCard }
    }
    val safeDeckIndex = if (deckItems.isEmpty()) 0 else deckIndex.coerceIn(0, deckItems.lastIndex)

    BackHandler { onDismiss() }

    if (showRecallSettingsDialog) {
        ActiveRecallSettingsDialog(
            recallMode = recallMode,
            maskMode = maskMode,
            typeFilter = typeFilter,
            showTypeBadges = showTypeBadges,
            showOptionsBeforeReveal = showOptionsBeforeReveal,
            isDeckMode = isDeckMode,
            totalItems = totalStudyItems,
            incorrectCount = incorrectCount,
            skippedCount = skippedCount,
            mcqCount = mcqCardCount,
            conceptCount = conceptCount,
            oneLinerCount = oneLinerCount,
            qaCount = qaCardCount,
            onToggleRecallMode = { recallMode = it },
            onSelectMaskMode = {
                maskMode = it
                if (!recallMode) recallMode = true
            },
            onSelectTypeFilter = { typeFilter = it },
            onToggleShowTypeBadges = { showTypeBadges = it },
            onToggleShowOptionsBeforeReveal = { showOptionsBeforeReveal = it },
            onToggleDeckMode = { isDeckMode = it },
            onRevealAll = {
                revealedIndices = recallableVisibleUnits.map { it.blockIndex }.toSet()
            },
            onHideAll = {
                revealedIndices = emptySet()
                selfSelectedOptions = emptyMap()
            },
            onDismiss = { showRecallSettingsDialog = false }
        )
    }

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
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (showSearchBar && searchQuery.isNotBlank()) {
                                        searchQuery = ""
                                    }
                                    showSearchBar = !showSearchBar
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = if (showSearchBar) "Hide Search" else "Search Notes",
                                    tint = if (showSearchBar || searchQuery.isNotBlank()) colors.teal else colors.inkMuted
                                )
                            }
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

                    // Primary Active Recall Studio Bar (horizontally scrollable fallback for large font scales)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { recallMode = !recallMode },
                            shape = RoundedCornerShape(10.dp),
                            color = if (recallMode) colors.teal else colors.surfaceVariant,
                            border = BorderStroke(1.dp, if (recallMode) colors.teal else colors.border),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .testTag("active_recall_toggle_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (recallMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = if (recallMode) Color.White else colors.teal,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (recallMode) "Recall ON" else "Active Recall",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (recallMode) Color.White else colors.inkBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // List vs Focus Deck Segmented Switcher
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.surfaceVariant,
                            border = BorderStroke(0.9.dp, colors.border),
                            modifier = Modifier.heightIn(min = 38.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { isDeckMode = false },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (!isDeckMode) colors.surface else Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewAgenda,
                                            contentDescription = "List View",
                                            tint = if (!isDeckMode) colors.teal else colors.inkMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "List",
                                            fontSize = 11.5.sp,
                                            fontWeight = if (!isDeckMode) FontWeight.Bold else FontWeight.Medium,
                                            color = if (!isDeckMode) colors.inkBold else colors.inkMuted,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Surface(
                                    onClick = { isDeckMode = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isDeckMode) colors.surface else Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Style,
                                            contentDescription = "Flashcard Deck View",
                                            tint = if (isDeckMode) colors.teal else colors.inkMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Deck",
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isDeckMode) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isDeckMode) colors.inkBold else colors.inkMuted,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // Recall Setup & Filters Button
                        Surface(
                            onClick = { showRecallSettingsDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            color = if (typeFilter != RecallTypeFilter.ALL || recallMode) colors.blueLight else colors.surfaceVariant,
                            border = BorderStroke(1.dp, if (typeFilter != RecallTypeFilter.ALL || recallMode) colors.teal.copy(alpha = 0.5f) else colors.border),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .testTag("recall_setup_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Recall Setup & Filters",
                                    tint = colors.tealDark,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Setup",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.tealDark,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Single-Row Quick Filter Chips (never wraps onto a 2nd sticky row at any font size!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = flipPair,
                            onClick = {
                                maskMode = if (flipPair) RecallMaskMode.ANSWER_AND_EXPLANATION else RecallMaskMode.REVERSE_PROMPT
                                if (!recallMode && !flipPair) recallMode = true
                            },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("Flip Prompt", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.blueLight,
                                selectedLabelColor = colors.tealDark
                            )
                        )
                        FilterChip(
                            selected = starredOnly,
                            onClick = { starredOnly = !starredOnly },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("Starred (${bookmarked.size})", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.amberLight,
                                selectedLabelColor = colors.amber
                            )
                        )
                        FilterChip(
                            selected = hideMastered,
                            onClick = { hideMastered = !hideMastered },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("Hide Mastered", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.greenLight,
                                selectedLabelColor = colors.green
                            )
                        )
                        FilterChip(
                            selected = typeFilter != RecallTypeFilter.ALL,
                            onClick = { showRecallSettingsDialog = true },
                            leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = {
                                Text(
                                    text = if (typeFilter == RecallTypeFilter.ALL) "Type: All" else "Type: ${typeFilter.label}",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.tealLight,
                                selectedLabelColor = colors.tealDark
                            )
                        )
                    }

                    // Active Recall Live Status & Progress Banner (compact 1-row + slim progress bar)
                    AnimatedVisibility(visible = recallMode && recallableVisibleUnits.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.tealLight.copy(alpha = 0.55f),
                            border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${maskMode.shortLabel} • $revealedVisibleCount/${recallableVisibleUnits.size} revealed",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.tealDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = {
                                                revealedIndices = emptySet()
                                                selfSelectedOptions = emptyMap()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.heightIn(min = 30.dp)
                                        ) {
                                            Text("Hide All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.inkBold, maxLines = 1, softWrap = false)
                                        }
                                        TextButton(
                                            onClick = {
                                                revealedIndices = recallableVisibleUnits.map { it.blockIndex }.toSet()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.heightIn(min = 30.dp)
                                        ) {
                                            Text("Reveal All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.tealDark, maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                val frac = if (recallableVisibleUnits.isNotEmpty()) {
                                    revealedVisibleCount.toFloat() / recallableVisibleUnits.size.toFloat()
                                } else 0f
                                LinearProgressIndicator(
                                    progress = { frac.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = colors.teal,
                                    trackColor = colors.surface
                                )
                            }
                        }
                    }

                    // On-demand Collapsible Search Bar (reclaims ~64dp when closed!)
                    AnimatedVisibility(visible = showSearchBar || searchQuery.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search questions, formulas, answers, or types...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        showSearchBar = false
                                    }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search", modifier = Modifier.size(16.dp))
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (isDeckMode) {
                // Focused Flashcard Deck Study Mode
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
                                onClick = {
                                    typeFilter = RecallTypeFilter.ALL
                                    starredOnly = false
                                    hideMastered = false
                                    searchQuery = ""
                                }
                            ) {
                                Text("Reset Filters")
                            }
                        }
                    }
                } else {
                    val activeBlock = deckItems[safeDeckIndex]
                    val isStarred = activeBlock.blockIndex in bookmarked
                    val isMastered = activeBlock.blockIndex in mastered
                    val isHiddenRecall = recallMode &&
                        ((activeBlock is StudyNoteBlock.OneLiner && activeBlock.hasRecallTarget) || activeBlock is StudyNoteBlock.MultiLinerCard) &&
                        activeBlock.blockIndex !in revealedIndices

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Deck progress header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Focus Deck • Card ${safeDeckIndex + 1} of ${deckItems.size}",
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
                            progress = { (safeDeckIndex + 1).toFloat() / deckItems.size.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = colors.teal,
                            trackColor = colors.border.copy(alpha = 0.4f)
                        )

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
                                    selfSelectedOptions = selfSelectedOptions + (activeBlock.blockIndex to chosen)
                                },
                                isExpanded = true,
                                onReveal = { revealedIndices = revealedIndices + activeBlock.blockIndex },
                                onHide = { revealedIndices = revealedIndices - activeBlock.blockIndex },
                                onToggleExpand = {},
                                onToggleStar = { onToggleBookmark(note, activeBlock.blockIndex) },
                                onToggleMastered = { onToggleMastered(note, activeBlock.blockIndex) }
                            )
                        }

                        // Pinned Deck Action Dock (48dp+ touch targets)
                        Surface(
                            color = colors.surface,
                            shadowElevation = 6.dp,
                            border = BorderStroke(0.6.dp, colors.border.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { if (safeDeckIndex > 0) deckIndex = safeDeckIndex - 1 },
                                    enabled = safeDeckIndex > 0,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Card", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Prev", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }

                                if (isHiddenRecall) {
                                    Button(
                                        onClick = { revealedIndices = revealedIndices + activeBlock.blockIndex },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reveal Answer", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (!isMastered) onToggleMastered(note, activeBlock.blockIndex)
                                            if (safeDeckIndex < deckItems.lastIndex) deckIndex = safeDeckIndex + 1
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.green, contentColor = Color.White),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isMastered) "Next Card" else "Got It & Next",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { if (safeDeckIndex < deckItems.lastIndex) deckIndex = safeDeckIndex + 1 },
                                    enabled = safeDeckIndex < deckItems.lastIndex,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Next", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Card", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Linked Question Set Banner scrolls cleanly with content instead of pinning at top
                    if (!linkedPaperTitle.isNullOrBlank() && note.linkedPaperId != null && onOpenLinkedPaper != null) {
                        item(key = "linked_paper_banner") {
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
                    }

                    if (visibleBlocks.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(0.8.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No matching items for the selected filters.",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.inkMuted,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = {
                                            typeFilter = RecallTypeFilter.ALL
                                            starredOnly = false
                                            hideMastered = false
                                            searchQuery = ""
                                        }
                                    ) {
                                        Text("Show All Items")
                                    }
                                }
                            }
                        }
                    } else {
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
                                recallMode = recallMode,
                                maskMode = maskMode,
                                flipPair = flipPair,
                                showTypeBadges = showTypeBadges,
                                showOptionsBeforeReveal = showOptionsBeforeReveal,
                                selectedSelfOption = selfSelectedOptions[block.blockIndex],
                                onSelectSelfOption = { chosen ->
                                    selfSelectedOptions = selfSelectedOptions + (block.blockIndex to chosen)
                                },
                                isExpanded = isExpanded,
                                onReveal = { revealedIndices = revealedIndices + block.blockIndex },
                                onHide = { revealedIndices = revealedIndices - block.blockIndex },
                                onToggleExpand = {
                                    expandedCardOverrides = expandedCardOverrides + (block.blockIndex to !isExpanded)
                                },
                                onToggleStar = { onToggleBookmark(note, block.blockIndex) },
                                onToggleMastered = { onToggleMastered(note, block.blockIndex) }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(36.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveRecallSettingsDialog(
    recallMode: Boolean,
    maskMode: RecallMaskMode,
    typeFilter: RecallTypeFilter,
    showTypeBadges: Boolean,
    showOptionsBeforeReveal: Boolean,
    isDeckMode: Boolean,
    totalItems: Int,
    incorrectCount: Int,
    skippedCount: Int,
    mcqCount: Int,
    conceptCount: Int,
    oneLinerCount: Int,
    qaCount: Int,
    onToggleRecallMode: (Boolean) -> Unit,
    onSelectMaskMode: (RecallMaskMode) -> Unit,
    onSelectTypeFilter: (RecallTypeFilter) -> Unit,
    onToggleShowTypeBadges: (Boolean) -> Unit,
    onToggleShowOptionsBeforeReveal: (Boolean) -> Unit,
    onToggleDeckMode: (Boolean) -> Unit,
    onRevealAll: () -> Unit,
    onHideAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 720.dp)
                .testTag("active_recall_settings_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(shape = RoundedCornerShape(10.dp), color = colors.tealLight) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = colors.tealDark,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Active Recall Studio Setup",
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Choose question types, masking rules & drill mode",
                                fontSize = 11.5.sp,
                                color = colors.inkMuted
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close setup", tint = colors.inkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.border.copy(alpha = 0.6f))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section 1: Master Active Recall Switch
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (recallMode) colors.tealLight.copy(alpha = 0.45f) else colors.surfaceVariant,
                        border = BorderStroke(1.dp, if (recallMode) colors.teal else colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Enable Active Recall Masking",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Text(
                                    text = "Conceals target answers so you actively retrieve knowledge before verifying.",
                                    fontSize = 11.sp,
                                    color = colors.inkMuted
                                )
                            }
                            Switch(
                                checked = recallMode,
                                onCheckedChange = onToggleRecallMode,
                                colors = SwitchDefaults.colors(checkedTrackColor = colors.teal)
                            )
                        }
                    }

                    // Section 2: What Should Active Recall Hide / Prompt?
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "RECALL MASKING STRATEGY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = colors.inkMuted
                        )
                        RecallMaskMode.entries.forEach { mode ->
                            val selected = maskMode == mode
                            Surface(
                                onClick = { onSelectMaskMode(mode) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) colors.tealLight.copy(alpha = 0.55f) else colors.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(if (selected) 1.5.dp else 0.8.dp, if (selected) colors.teal else colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (selected) colors.teal else colors.inkMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) colors.tealDark else colors.inkBold
                                        )
                                        Text(
                                            text = mode.subtitle,
                                            fontSize = 11.sp,
                                            color = colors.inkMuted,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: Filter by Question Type / Remediation Status
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "QUESTION & CONTENT TYPE FILTER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = colors.inkMuted
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val typeOptions = buildList {
                                add(RecallTypeFilter.ALL to totalItems)
                                if (incorrectCount > 0) add(RecallTypeFilter.INCORRECT to incorrectCount)
                                if (skippedCount > 0) add(RecallTypeFilter.SKIPPED to skippedCount)
                                if (mcqCount > 0) add(RecallTypeFilter.MCQ to mcqCount)
                                if (conceptCount > 0) add(RecallTypeFilter.CONCEPTS to conceptCount)
                                if (oneLinerCount > 0) add(RecallTypeFilter.ONE_LINERS to oneLinerCount)
                                if (qaCount > 0) add(RecallTypeFilter.QA_CARDS to qaCount)
                            }
                            typeOptions.forEach { (option, count) ->
                                val isSel = typeFilter == option
                                FilterChip(
                                    selected = isSel,
                                    onClick = { onSelectTypeFilter(option) },
                                    label = {
                                        Text(
                                            text = "${option.label} ($count)",
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.tealLight,
                                        selectedLabelColor = colors.tealDark
                                    )
                                )
                            }
                        }
                    }

                    // Section 4: Card Display & Drill Preferences
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "CARD PRESENTATION & VISIBILITY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = colors.inkMuted
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(0.8.dp, colors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text("Show Question Type & Status Tags", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.inkBold)
                                        Text("Displays MCQ/Formula/Concept type & Incorrect/Skipped tags", fontSize = 10.5.sp, color = colors.inkMuted)
                                    }
                                    Switch(
                                        checked = showTypeBadges,
                                        onCheckedChange = onToggleShowTypeBadges,
                                        colors = SwitchDefaults.colors(checkedTrackColor = colors.teal)
                                    )
                                }
                                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text("Show MCQ Choices Before Reveal", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.inkBold)
                                        Text("Inspect & test options (A–D) before revealing the key", fontSize = 10.5.sp, color = colors.inkMuted)
                                    }
                                    Switch(
                                        checked = showOptionsBeforeReveal,
                                        onCheckedChange = onToggleShowOptionsBeforeReveal,
                                        colors = SwitchDefaults.colors(checkedTrackColor = colors.teal)
                                    )
                                }
                                HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text("Focused Flashcard Deck Mode", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.inkBold)
                                        Text("Study one question at a time with Got It & Prev/Next controls", fontSize = 10.5.sp, color = colors.inkMuted)
                                    }
                                    Switch(
                                        checked = isDeckMode,
                                        onCheckedChange = onToggleDeckMode,
                                        colors = SwitchDefaults.colors(checkedTrackColor = colors.teal)
                                    )
                                }
                            }
                        }
                    }

                    // Quick Batch Reveal / Reset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onHideAll()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Reset Recall", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = {
                                onRevealAll()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Reveal All", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Done & Apply", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KnowledgeBlockItemView(
    block: StudyNoteBlock,
    isStarred: Boolean,
    isMastered: Boolean,
    isHiddenRecall: Boolean,
    recallMode: Boolean = false,
    maskMode: RecallMaskMode = RecallMaskMode.ANSWER_AND_EXPLANATION,
    flipPair: Boolean,
    showTypeBadges: Boolean = true,
    showOptionsBeforeReveal: Boolean = true,
    selectedSelfOption: String? = null,
    onSelectSelfOption: ((String) -> Unit)? = null,
    isExpanded: Boolean,
    onReveal: () -> Unit,
    onHide: (() -> Unit)? = null,
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
            val effectiveFlip = flipPair || (recallMode && maskMode == RecallMaskMode.REVERSE_PROMPT)
            val lead = if (effectiveFlip && block.leadTerm.isNotBlank()) block.detail else block.leadTerm
            val body = if (effectiveFlip && block.leadTerm.isNotBlank()) block.leadTerm else block.detail

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
                        if (showTypeBadges) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.surfaceVariant,
                                modifier = Modifier.padding(bottom = 3.dp)
                            ) {
                                Text(
                                    text = if (effectiveFlip) "One-Liner • Clue First" else "One-Liner Fact",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkMuted,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (lead.isNotBlank()) {
                            LatexText(
                                text = lead,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (isHiddenRecall) {
                            Surface(
                                onClick = onReveal,
                                shape = RoundedCornerShape(8.dp),
                                color = colors.tealLight,
                                border = BorderStroke(0.9.dp, colors.teal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .padding(top = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, tint = colors.tealDark, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (effectiveFlip) "Tap to reveal key term / concept" else "Tap to reveal answer / fact detail",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.tealDark
                                    )
                                }
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
            val effectiveFlip = flipPair || (recallMode && maskMode == RecallMaskMode.REVERSE_PROMPT)
            val isIncorrectStatus = block.statusTag.contains("Incorrect", ignoreCase = true)
            val isSkippedStatus = block.statusTag.contains("Skipped", ignoreCase = true)
            val statusColor = when {
                isIncorrectStatus -> colors.red
                isSkippedStatus -> colors.amber
                else -> colors.tealDark
            }
            val statusBg = when {
                isIncorrectStatus -> colors.redLight
                isSkippedStatus -> colors.amberLight
                else -> colors.tealLight
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isMastered -> colors.greenLight.copy(alpha = 0.25f)
                        isStarred -> colors.amberLight.copy(alpha = 0.30f)
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
                Column(modifier = Modifier.padding(14.dp)) {
                    // Card Header Row: Question/Item Index + Question Type Badge + Status Tag + Expand/Collapse
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleExpand() },
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (block.isQuestionCard) colors.navyBanner else colors.teal.copy(alpha = 0.14f),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = block.displayIndex,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (block.isQuestionCard) colors.navyBannerText else colors.tealDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (showTypeBadges) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(bottom = 5.dp)
                                ) {
                                    val displayType = block.questionType.ifBlank { block.badge.ifBlank { "Concept Card" } }
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = colors.blueLight,
                                        border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = displayType,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.tealDark,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (block.statusTag.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = statusBg,
                                            border = BorderStroke(0.6.dp, statusColor.copy(alpha = 0.45f))
                                        ) {
                                            Text(
                                                text = block.statusTag,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = statusColor,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (!block.isQuestionCard && block.nestedBullets.isNotEmpty()) {
                                        Surface(shape = RoundedCornerShape(5.dp), color = colors.surfaceVariant) {
                                            Text(
                                                text = "${block.nestedBullets.size} sub-points",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.inkMuted,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Prompt Title / Question Stem (visible unless Reverse/Flip mode is active!)
                            if (!effectiveFlip) {
                                LatexText(
                                    text = block.title,
                                    fontSize = if (block.isQuestionCard) 14.5.sp else 14.5.sp,
                                    fontWeight = if (block.isQuestionCard) FontWeight.SemiBold else FontWeight.Bold,
                                    color = colors.inkBold,
                                    lineHeight = 21.sp
                                )
                            } else {
                                // Reverse / Clue-First Prompt Header
                                val reverseClue = when {
                                    block.correctAnswer.isNotBlank() && block.keyTakeaway.isNotBlank() ->
                                        "Answer: ${block.correctAnswer}\nTakeaway: ${block.keyTakeaway.take(180)}${if (block.keyTakeaway.length > 180) "…" else ""}"
                                    block.correctAnswer.isNotBlank() -> "Answer: ${block.correctAnswer}"
                                    block.paragraphs.isNotEmpty() -> block.paragraphs.first()
                                    block.nestedBullets.isNotEmpty() -> block.nestedBullets.joinToString("\n• ", prefix = "• ") { it.second }
                                    else -> "Clue: Recall the concept title"
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.blueLight.copy(alpha = 0.65f),
                                    border = BorderStroke(0.7.dp, colors.teal.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "CLUE / KEY ANSWER",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = colors.tealDark,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        LatexText(
                                            text = reverseClue,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.inkBold,
                                            lineHeight = 19.sp
                                        )
                                    }
                                }
                            }
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
                        val correctLetter = Regex("""^\(?([A-E])\)?""").find(block.correctAnswer.trim().uppercase())?.groupValues?.get(1)
                        val hideOptionsNow = isHiddenRecall && (
                            effectiveFlip ||
                                maskMode == RecallMaskMode.PURE_FREE_RECALL ||
                                !showOptionsBeforeReveal
                            )

                        // Render MCQ Options (visible before reveal when showOptionsBeforeReveal & not PURE_FREE_RECALL)
                        if (block.optionsList.isNotEmpty() && !hideOptionsNow) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                block.optionsList.forEach { (optKey, optText) ->
                                    val isCorrectOpt = correctLetter != null && optKey.equals(correctLetter, ignoreCase = true)
                                    val isSelfPicked = selectedSelfOption.equals(optKey, ignoreCase = true)
                                    val isCandidateWrong = block.candidateAnswer.equals(optKey, ignoreCase = true) && !isCorrectOpt

                                    val optBg = when {
                                        !isHiddenRecall && isCorrectOpt -> colors.greenLight.copy(alpha = 0.7f)
                                        !isHiddenRecall && (isSelfPicked || isCandidateWrong) -> colors.redLight.copy(alpha = 0.65f)
                                        isHiddenRecall && isSelfPicked -> colors.tealLight
                                        else -> colors.surfaceVariant.copy(alpha = 0.55f)
                                    }
                                    val optBorder = when {
                                        !isHiddenRecall && isCorrectOpt -> colors.green
                                        !isHiddenRecall && (isSelfPicked || isCandidateWrong) -> colors.red
                                        isHiddenRecall && isSelfPicked -> colors.teal
                                        else -> colors.border
                                    }
                                    val optTextColor = when {
                                        !isHiddenRecall && isCorrectOpt -> colors.green
                                        !isHiddenRecall && (isSelfPicked || isCandidateWrong) -> colors.red
                                        else -> colors.inkBold
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = optBg,
                                        border = BorderStroke(if (isSelfPicked || (!isHiddenRecall && isCorrectOpt)) 1.1.dp else 0.7.dp, optBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (isHiddenRecall && onSelectSelfOption != null) {
                                                    Modifier.clickable { onSelectSelfOption(optKey) }
                                                } else Modifier
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = optBorder.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    text = optKey,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = optTextColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            LatexText(
                                                text = optText,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (!isHiddenRecall && isCorrectOpt) FontWeight.Bold else FontWeight.Normal,
                                                color = optTextColor,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (!isHiddenRecall && isCorrectOpt) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(5.dp),
                                                    color = colors.green.copy(alpha = 0.16f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.Check, contentDescription = "Correct option", tint = colors.green, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = "Correct",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = colors.green,
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

                        // Explanation-only mode: show Correct Answer when options aren't already highlighting it
                        val optionsHighlightedCorrect = block.optionsList.isNotEmpty() &&
                            !hideOptionsNow &&
                            correctLetter != null &&
                            block.optionsList.any { it.first.equals(correctLetter, ignoreCase = true) }

                        if (isHiddenRecall && maskMode == RecallMaskMode.EXPLANATION_ONLY && block.correctAnswer.isNotBlank() && !optionsHighlightedCorrect) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.greenLight.copy(alpha = 0.65f),
                                border = BorderStroke(0.9.dp, colors.green.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.green, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Correct Answer: ${block.correctAnswer}",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.green
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        if (isHiddenRecall) {
                            val revealHint = when {
                                effectiveFlip -> "Tap to reveal full question stem & context"
                                maskMode == RecallMaskMode.EXPLANATION_ONLY -> "Tap to reveal formula, reasoning & key takeaway"
                                maskMode == RecallMaskMode.PURE_FREE_RECALL && block.optionsList.isNotEmpty() ->
                                    "Tap to reveal options, correct answer & explanation"
                                block.isQuestionCard -> buildString {
                                    append("Tap to reveal ")
                                    val parts = mutableListOf<String>()
                                    if (block.correctAnswer.isNotBlank()) parts.add("answer")
                                    if (block.formula.isNotBlank()) parts.add("formula")
                                    if (block.keyTakeaway.isNotBlank() || block.nestedBullets.isNotEmpty()) parts.add("explanation")
                                    append(if (parts.isEmpty()) "solution" else parts.joinToString(", "))
                                }
                                else -> "Tap to reveal explanation, formula & ${block.nestedBullets.size.coerceAtLeast(1)} points"
                            }
                            Surface(
                                onClick = onReveal,
                                shape = RoundedCornerShape(10.dp),
                                color = colors.tealLight.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, colors.teal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = colors.tealDark,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = revealHint,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.tealDark
                                    )
                                }
                            }
                        } else {
                            // Revealed Answer, Formula & Explanation
                            if (effectiveFlip) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.surfaceVariant,
                                    border = BorderStroke(0.7.dp, colors.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = if (block.isQuestionCard) "QUESTION PROMPT" else "CONCEPT TERM",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = colors.inkMuted
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        LatexText(
                                            text = block.title,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold
                                        )
                                    }
                                }
                            }

                            // Show dedicated Correct Answer box ONLY if not already highlighted inside visible (A)-(E) options
                            if (block.correctAnswer.isNotBlank() && !optionsHighlightedCorrect) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.greenLight.copy(alpha = 0.7f),
                                    border = BorderStroke(0.9.dp, colors.green.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.green, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        LatexText(
                                            text = "Correct Answer: ${block.correctAnswer}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.green
                                        )
                                    }
                                }
                            }

                            block.paragraphs.forEach { para ->
                                LatexText(
                                    text = para,
                                    fontSize = 13.sp,
                                    color = colors.ink,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }

                            if (block.formula.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.blueLight,
                                    border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text(
                                            text = "FORMULA",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = colors.tealDark
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        LatexText(
                                            text = block.formula,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.tealDark
                                        )
                                    }
                                }
                            }

                            if (block.keyTakeaway.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = colors.surfaceVariant.copy(alpha = 0.65f),
                                    border = BorderStroke(0.7.dp, colors.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "KEY TAKEAWAY & SOLUTION",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = colors.tealDark,
                                            letterSpacing = 0.4.sp
                                        )
                                        val cleanTakeawayLines = block.keyTakeaway
                                            .replace(Regex("""\s+-\s+\(([A-Ea-e])\)"""), "\n($1)")
                                            .lines()
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() }
                                        cleanTakeawayLines.forEach { line ->
                                            LatexText(
                                                text = line,
                                                fontSize = 12.5.sp,
                                                color = colors.ink,
                                                lineHeight = 18.5.sp
                                            )
                                        }
                                    }
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

                        if (onToggleStar != null || onToggleMastered != null || (recallMode && !isHiddenRecall && onHide != null)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = colors.border.copy(alpha = 0.45f))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (recallMode && !isHiddenRecall && onHide != null) {
                                    TextButton(onClick = onHide, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Hide Answer", fontSize = 11.5.sp, color = colors.inkMuted, maxLines = 1, softWrap = false)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (onToggleStar != null) {
                                        TextButton(onClick = onToggleStar, modifier = Modifier.heightIn(min = 48.dp)) {
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
                                                color = if (isStarred) colors.amber else colors.inkMuted,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    if (onToggleMastered != null) {
                                        TextButton(onClick = onToggleMastered, modifier = Modifier.heightIn(min = 48.dp)) {
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
                                                color = if (isMastered) colors.green else colors.inkMuted,
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
