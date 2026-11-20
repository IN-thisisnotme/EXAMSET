package com.example.ui.screens

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.StudyNoteEntity
import com.example.model.FlashcardSrsState
import com.example.model.KnowledgeCollectionType
import com.example.ui.theme.ExamTheme
import com.example.util.SpacedRepetitionEngine
import com.example.util.StudyNoteBlock
import com.example.util.StudyNoteParser

fun printStudyNoteAsPdf(context: Context, note: StudyNoteEntity) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
    if (printManager == null) {
        Toast.makeText(context, "Print service unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    val jobName = "${note.title.ifBlank { "Knowledge_Set" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_Notes"
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
    onDeleteNote: (StudyNoteEntity) -> Unit,
    linkedPaperTitles: Map<Long, String> = emptyMap(),
    flashcardSrsByNote: Map<Long, Map<Int, FlashcardSrsState>> = emptyMap(),
    onCompileCourseActiveRecall: (() -> Unit)? = null
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val filtered = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) notes
        else notes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.subject.contains(searchQuery, ignoreCase = true) ||
                it.sourceAi.contains(searchQuery, ignoreCase = true) ||
                it.customLabel.contains(searchQuery, ignoreCase = true) ||
                it.effectiveBadgeLabel().contains(searchQuery, ignoreCase = true) ||
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
                placeholder = { Text("Filter knowledge sets...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search knowledge sets", modifier = Modifier.size(16.dp)) },
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
                Text("New Set", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (onCompileCourseActiveRecall != null) {
            androidx.compose.material3.FilledTonalButton(
                onClick = onCompileCourseActiveRecall,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.teal.copy(alpha = 0.14f),
                    contentColor = if (colors.isDark) colors.teal else colors.tealDark
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .testTag("compile_course_active_recall_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = if (colors.isDark) colors.teal else colors.tealDark
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⚡ Compile Course Active Recall Set (From Question Sets & Notes)",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
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
                        text = if (searchQuery.isNotBlank()) "No matching Knowledge Sets" else "Knowledge Sets Studio",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Store one-liners, multi-liner concept cards, formula sheets, and comparison matrices side-by-side with Question Sets. Includes Compiled AI Prompts, Active Recall & Quiz Drafting.",
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
                        ),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Create Knowledge Set", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            filtered.forEach { note ->
                StudyNoteCard(
                    note = note,
                    linkedPaperTitle = note.linkedPaperId?.let { linkedPaperTitles[it] },
                    srsMap = flashcardSrsByNote[note.id].orEmpty(),
                    onOpen = { onOpenNote(note) },
                    onEdit = { onEditNote(note) },
                    onPrint = { printStudyNoteAsPdf(context, note) },
                    onDelete = { onDeleteNote(note) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyNoteCard(
    note: StudyNoteEntity,
    linkedPaperTitle: String? = null,
    srsMap: Map<Int, FlashcardSrsState> = emptyMap(),
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = ExamTheme.colors
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val formatRule = remember(note.formatRuleJson, note.collectionType) { note.parsedFormatRule() }
    val studyUnits = remember(note.rawContent, formatRule) {
        StudyNoteParser.parseBlocks(note.rawContent, formatRule).filter {
            it is StudyNoteBlock.OneLiner || it is StudyNoteBlock.MultiLinerCard
        }
    }
    val itemCount = studyUnits.size
    val starredCount = remember(note.bookmarkedLinesJson) { note.getBookmarkedIndices().size }
    val masteredCount = remember(note.masteredBlocksJson) { note.getMasteredIndices().size }
    val srsStats = remember(studyUnits, srsMap, note.collectionType) {
        if (note.resolvedCollectionType() == KnowledgeCollectionType.FLASHCARDS || srsMap.isNotEmpty()) {
            SpacedRepetitionEngine.computeDeckStats(studyUnits, srsMap, System.currentTimeMillis())
        } else {
            null
        }
    }

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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.tealLight
                    ) {
                        Text(
                            text = note.effectiveBadgeLabel(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    if (srsStats != null && srsStats.totalCount > 0) {
                        val dueCount = srsStats.actionableCount
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (dueCount > 0) colors.amberLight else colors.greenLight
                        ) {
                            Text(
                                text = if (dueCount > 0) "$dueCount due" else "Caught up",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dueCount > 0) colors.amber else colors.green,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
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
                        Icon(Icons.Default.Edit, contentDescription = "Edit Knowledge Set", tint = colors.inkMuted, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onPrint, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Print, contentDescription = "Print Knowledge Set PDF", tint = colors.teal, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Knowledge Set", tint = colors.red, modifier = Modifier.size(18.dp))
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

            if (!linkedPaperTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.blueLight,
                    border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.28f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = colors.teal, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = linkedPaperTitle,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.tealDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (itemCount > 0) "$itemCount Concept Units" else "${note.rawContent.lines().size} Lines",
                    fontSize = 11.5.sp,
                    color = colors.inkMuted,
                    fontWeight = FontWeight.Medium
                )
                if (starredCount > 0) {
                    Text(
                        text = "★ $starredCount",
                        fontSize = 11.5.sp,
                        color = colors.amber,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (masteredCount > 0) {
                    Text(
                        text = "✓ $masteredCount mastered",
                        fontSize = 11.5.sp,
                        color = colors.green,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Open Studio →",
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
            title = { Text("Delete Knowledge Set?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
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
