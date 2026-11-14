package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.StudyNoteEntity
import com.example.model.FolderItem
import com.example.ui.components.LatexText
import com.example.ui.theme.ExamTheme
import com.example.util.StudyNoteBlock
import com.example.util.StudyNoteParser

fun printStudyNoteAsPdf(context: Context, note: StudyNoteEntity) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
    if (printManager == null) {
        Toast.makeText(context, "Print service unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    val jobName = "${note.title.ifBlank { "Study_Notes" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_Notes"
    val html = StudyNoteParser.generatePrintableHtml(note)
    val webView = WebView(context)
    webView.settings.apply {
        javaScriptEnabled = false
        loadWithOverviewMode = true
        useWideViewPort = true
    }
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val adapter = webView.createPrintDocumentAdapter(jobName)
            val attrs = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
            printManager.print(jobName, adapter, attrs)
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

@Composable
fun StudyNotesSectionItems(
    notes: List<StudyNoteEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onOpenNote: (StudyNoteEntity) -> Unit,
    onEditNote: (StudyNoteEntity) -> Unit,
    onDeleteNote: (StudyNoteEntity) -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val filtered = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) notes
        else notes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.subject.contains(searchQuery, ignoreCase = true) ||
                it.sourceAi.contains(searchQuery, ignoreCase = true) ||
                it.rawContent.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Filter study notes...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search notes", modifier = Modifier.size(16.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.teal,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface
                ),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("add_study_note_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Notes", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (filtered.isEmpty()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matching study notes" else "Study Notes & AI One-Liners",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Paste one-liners, revision tables, or formatted notes generated from Gemini, Claude, or ChatGPT. Supports Active Recall & PDF export.",
                        fontSize = 12.sp,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onAddClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.teal,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste Study Notes", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            filtered.forEach { note ->
                StudyNoteCard(
                    note = note,
                    onOpen = { onOpenNote(note) },
                    onEdit = { onEditNote(note) },
                    onPrint = { printStudyNoteAsPdf(context, note) },
                    onDelete = { onDeleteNote(note) }
                )
            }
        }
    }
}

@Composable
fun StudyNoteCard(
    note: StudyNoteEntity,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = ExamTheme.colors
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val oneLinerCount = remember(note.rawContent) { StudyNoteParser.countOneLiners(note.rawContent) }
    val starredCount = remember(note.bookmarkedLinesJson) { note.getBookmarkedIndices().size }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.8.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("study_note_card_${note.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.tealLight
                    ) {
                        Text(
                            text = note.sourceAi.ifBlank { "Notes" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    if (note.subject.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.surfaceVariant
                        ) {
                            Text(
                                text = note.subject.substringAfterLast(">").trim(),
                                fontSize = 10.sp,
                                color = colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Note", tint = colors.inkMuted, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onPrint, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Print, contentDescription = "Print Note PDF", tint = colors.teal, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Note", tint = colors.red, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text(
                text = note.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (oneLinerCount > 0) "$oneLinerCount Key Facts" else "${note.rawContent.lines().size} Lines",
                    fontSize = 11.5.sp,
                    color = colors.inkMuted,
                    fontWeight = FontWeight.Medium
                )
                if (starredCount > 0) {
                    Text(
                        text = "★ $starredCount Starred",
                        fontSize = 11.5.sp,
                        color = colors.amber,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Open & Recall →",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.teal
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Study Notes?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("Remove \"${note.title}\" permanently?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditStudyNoteDialog(
    initialNote: StudyNoteEntity? = null,
    defaultFolderId: String? = null,
    defaultSubject: String = "",
    folders: List<FolderItem> = emptyList(),
    onSave: (id: Long, title: String, subject: String, sourceAi: String, rawContent: String, folderId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var subject by remember { mutableStateOf(initialNote?.subject ?: defaultSubject) }
    var sourceAi by remember { mutableStateOf(initialNote?.sourceAi ?: "One-Liners") }
    var rawContent by remember { mutableStateOf(initialNote?.rawContent ?: "") }
    var selectedFolderId by remember { mutableStateOf(initialNote?.folderId ?: defaultFolderId) }
    var showFolderDropdown by remember { mutableStateOf(false) }

    val parsedBlocks = remember(rawContent) { StudyNoteParser.parseBlocks(rawContent) }
    val oneLinerCount = parsedBlocks.count { it is StudyNoteBlock.OneLiner }
    val tableOrHeadingCount = parsedBlocks.count { it is StudyNoteBlock.Table || it is StudyNoteBlock.Heading }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler { onDismiss() }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            color = colors.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.inkBold)
                        }
                        Text(
                            text = if (initialNote != null) "Edit Study Notes" else "Add Study Notes",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                    }
                    Button(
                        onClick = {
                            onSave(initialNote?.id ?: 0L, title, subject, sourceAi, rawContent, selectedFolderId)
                            onDismiss()
                        },
                        enabled = rawContent.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .heightIn(min = 48.dp)
                            .testTag("save_study_note_button")
                    ) {
                        Text("Save Notes", fontWeight = FontWeight.Bold)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title (e.g., Fundamental Rights One-Liners)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("study_note_title_input")
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

                    Column {
                        Text("Source Format / Origin", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.inkMuted)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("One-Liners", "Gemini", "Claude", "ChatGPT", "Manual").forEach { src ->
                                FilterChip(
                                    selected = sourceAi == src,
                                    onClick = { sourceAi = src },
                                    label = { Text(src, fontSize = 11.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.tealLight,
                                        selectedLabelColor = colors.tealDark
                                    )
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Paste Notes (Markdown, Tables, One-Liners)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                if (clip.isNotBlank()) {
                                    rawContent = clip
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.heightIn(min = 40.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste", fontSize = 11.5.sp)
                        }
                    }

                    OutlinedTextField(
                        value = rawContent,
                        onValueChange = { rawContent = it },
                        placeholder = {
                            Text(
                                "Paste directly from Gemini, Claude, or ChatGPT...\n\nExample:\n## Fundamental Rights\n1. Article 14 -> Equality before law\n2. **Article 21**: Protection of life and personal liberty\n> Exam Tip: Art 20 & 21 cannot be suspended during emergency.",
                                fontSize = 12.sp,
                                color = colors.inkMuted
                            )
                        },
                        minLines = 10,
                        maxLines = 22,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("study_note_content_input")
                    )

                    if (rawContent.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.tealLight.copy(alpha = 0.6f),
                            border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Detected: $oneLinerCount one-liners/facts • $tableOrHeadingCount headings/tables",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.tealDark,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudyNoteReaderDialog(
    note: StudyNoteEntity,
    onToggleBookmark: (StudyNoteEntity, Int) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var recallMode by remember { mutableStateOf(false) }
    var starredOnly by remember { mutableStateOf(false) }
    var revealedIndices by remember(note.id, recallMode) { mutableStateOf(setOf<Int>()) }

    val blocks = remember(note.rawContent) { StudyNoteParser.parseBlocks(note.rawContent) }
    val bookmarked = remember(note.bookmarkedLinesJson) { note.getBookmarkedIndices() }

    val visibleBlocks = remember(blocks, searchQuery, starredOnly, bookmarked) {
        blocks.filter { block ->
            val starOk = !starredOnly || (block is StudyNoteBlock.OneLiner && block.blockIndex in bookmarked)
            val searchOk = searchQuery.isBlank() || when (block) {
                is StudyNoteBlock.Heading -> block.text.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.OneLiner -> block.leadTerm.contains(searchQuery, ignoreCase = true) ||
                    block.detail.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.Callout -> block.label.contains(searchQuery, ignoreCase = true) ||
                    block.body.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.Paragraph -> block.text.contains(searchQuery, ignoreCase = true)
                is StudyNoteBlock.Table -> block.headers.any { it.contains(searchQuery, ignoreCase = true) } ||
                    block.rows.flatten().any { it.contains(searchQuery, ignoreCase = true) }
            }
            starOk && searchOk
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
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
                                    Text(
                                        text = "${note.sourceAi} • ${note.subject.ifBlank { "Study Notes" }}",
                                        fontSize = 11.sp,
                                        color = colors.inkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Note", tint = colors.inkBold)
                                }
                                IconButton(onClick = { printStudyNoteAsPdf(context, note) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Print, contentDescription = "Print Note", tint = colors.teal)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
                                selected = starredOnly,
                                onClick = { starredOnly = !starredOnly },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                label = { Text("Starred (${bookmarked.size})", fontSize = 11.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.amberLight,
                                    selectedLabelColor = colors.amber
                                )
                            )
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search facts or keywords...", fontSize = 12.sp) },
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
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleBlocks, key = { "block_${it.blockIndex}" }) { block ->
                        when (block) {
                            is StudyNoteBlock.Heading -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.tealLight.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = block.text,
                                        fontSize = if (block.level <= 2) 15.5.sp else 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.tealDark,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            is StudyNoteBlock.OneLiner -> {
                                val isStarred = block.blockIndex in bookmarked
                                val isHidden = recallMode && block.hasRecallTarget && block.blockIndex !in revealedIndices
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isStarred) colors.amberLight.copy(alpha = 0.45f) else colors.surface
                                    ),
                                    border = BorderStroke(0.8.dp, if (isStarred) colors.amber else colors.border),
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
                                            if (block.leadTerm.isNotBlank()) {
                                                Text(
                                                    text = block.leadTerm,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.inkBold
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                            }
                                            if (isHidden) {
                                                Surface(
                                                    onClick = { revealedIndices = revealedIndices + block.blockIndex },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = colors.tealLight,
                                                    border = BorderStroke(0.8.dp, colors.teal),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Tap to reveal answer",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = colors.tealDark,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                                    )
                                                }
                                            } else {
                                                LatexText(
                                                    text = block.detail,
                                                    fontSize = 13.5.sp,
                                                    color = colors.ink
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { onToggleBookmark(note, block.blockIndex) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = if (isStarred) "Remove bookmark" else "Bookmark fact",
                                                tint = if (isStarred) colors.amber else colors.inkMuted
                                            )
                                        }
                                    }
                                }
                            }
                            is StudyNoteBlock.Callout -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.blueLight.copy(alpha = 0.65f),
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
                                                    if (idx % 2 == 1) colors.surfaceVariant.copy(alpha = 0.35f) else colors.surface
                                                )
                                            ) {
                                                block.headers.indices.forEach { colIdx ->
                                                    Text(
                                                        text = row.getOrNull(colIdx) ?: "",
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
                                LatexText(
                                    text = block.text,
                                    fontSize = 13.5.sp,
                                    color = colors.ink,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
