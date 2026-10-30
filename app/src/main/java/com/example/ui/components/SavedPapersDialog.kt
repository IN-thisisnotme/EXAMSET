package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.PaperEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedPapersDialog(
    papers: List<PaperEntity>,
    viewModel: MainViewModel? = null,
    onSelectPaper: (PaperEntity) -> Unit,
    onDeletePaper: (PaperEntity) -> Unit,
    onEditPaper: ((PaperEntity) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    // Selection mode state for bulk export/sharing
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    var showExportMenu by remember { mutableStateOf(false) }
    var pendingExportData by remember { mutableStateOf<String?>(null) }
    var pendingExportMimeType by remember { mutableStateOf("application/json") }

    // Storage Access Framework launcher to save file directly to device File Manager
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(pendingExportMimeType)
    ) { uri: Uri? ->
        if (uri != null && pendingExportData != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.bufferedWriter(Charsets.UTF_8).write(pendingExportData!!)
                }
                Toast.makeText(context, "Saved file successfully to device!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to write file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Open multiple documents launcher to concurrently import files
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty() && viewModel != null) {
            viewModel.batchValidateUris(uris) { batchItems ->
                coroutineScope.launch {
                    val res = viewModel.importValidatedBatch(batchItems)
                    Toast.makeText(context, "Imported ${res.importedCount} question set(s)!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun triggerExportToFile(data: String, defaultName: String, mime: String) {
        pendingExportData = data
        pendingExportMimeType = mime
        createDocLauncher.launch(defaultName)
    }

    fun triggerShare(data: String, title: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, data)
            putExtra(Intent.EXTRA_SUBJECT, title)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Question Sets"))
    }

    // Extract categories (subjects)
    val categories = remember(papers) {
        listOf("All") + papers.map { it.subject.trim() }.filter { it.isNotBlank() }.distinct()
    }
    var selectedCategory by remember { mutableStateOf("All") }
    var paperToDelete by remember { mutableStateOf<PaperEntity?>(null) }

    val filteredPapers = remember(papers, selectedCategory) {
        if (selectedCategory == "All") {
            papers
        } else {
            papers.filter { it.subject.trim().equals(selectedCategory, ignoreCase = true) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSelectionMode) "Select Question Sets" else "Saved Question Sets",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.muted)
                    }
                }

                // Action Bar: Selection Mode Toggle, Select All, and Import Launcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                isSelectionMode = !isSelectionMode
                                if (!isSelectionMode) selectedIds = emptySet()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isSelectionMode) "Done" else "Select",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (isSelectionMode && filteredPapers.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    selectedIds = if (selectedIds.size == filteredPapers.size) {
                                        emptySet()
                                    } else {
                                        filteredPapers.map { it.id }.toSet()
                                    }
                                },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (selectedIds.size == filteredPapers.size) "Deselect All" else "Select All (${filteredPapers.size})",
                                    fontSize = 11.sp,
                                    color = colors.teal
                                )
                            }
                        }
                    }

                    // Concurrent Bulk Import button
                    OutlinedButton(
                        onClick = {
                            importFilesLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Category Chips (Subject/Topic Categorisation)
                if (categories.size > 2) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = {
                                    val count = if (cat == "All") papers.size else papers.count { it.subject.trim().equals(cat, ignoreCase = true) }
                                    Text("$cat ($count)", fontSize = 11.sp)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.teal,
                                    selectedLabelColor = Color.White,
                                    containerColor = colors.surfaceVariant,
                                    labelColor = colors.ink
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedCategory == cat,
                                    borderColor = colors.border,
                                    selectedBorderColor = colors.teal
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (papers.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = colors.muted.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No saved question sets yet.",
                            color = colors.muted,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Add or paste your question sets to access them here.",
                            color = colors.muted.copy(alpha = 0.7f),
                            fontSize = 11.5.sp
                        )
                    }
                } else if (filteredPapers.isEmpty()) {
                    Text(
                        text = "No papers found in category '$selectedCategory'.",
                        fontSize = 12.sp,
                        color = colors.muted,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPapers, key = { it.id }) { item ->
                            val isSelected = item.id in selectedIds

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelectionMode) {
                                            selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                        } else {
                                            onSelectPaper(item)
                                            onDismiss()
                                        }
                                    }
                                    .testTag("saved_paper_item_${item.id}"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) colors.tealLight.copy(alpha = 0.5f) else colors.surfaceVariant
                                ),
                                border = BorderStroke(if (isSelected) 1.2.dp else 0.8.dp, if (isSelected) colors.teal else colors.border)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = colors.teal),
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Set Number Tag
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = colors.teal.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = item.setNumber.ifBlank { "SET A" }.uppercase(),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.tealDark,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }

                                            Text(
                                                text = item.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.inkBold,
                                                maxLines = 2,
                                                softWrap = true,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.subject,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.ink,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )

                                            if (item.topic.isNotBlank()) {
                                                Text("•", fontSize = 10.sp, color = colors.muted)
                                                Text(
                                                    text = item.topic,
                                                    fontSize = 11.sp,
                                                    color = colors.tealDark,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${item.questionCount} Questions",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.tealDark
                                            )
                                            Text(
                                                text = dateFormat.format(Date(item.createdAt)),
                                                fontSize = 9.5.sp,
                                                color = colors.muted
                                            )
                                            if (item.rollNumber.isNotBlank()) {
                                                Text("•", fontSize = 9.5.sp, color = colors.muted)
                                                Text(
                                                    text = "Roll: ${item.rollNumber}",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = colors.tealDark
                                                )
                                            }
                                        }
                                    }

                                    if (!isSelectionMode) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (onEditPaper != null) {
                                                IconButton(
                                                    onClick = {
                                                        onEditPaper(item)
                                                        onDismiss()
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Details",
                                                        tint = colors.teal,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { paperToDelete = item },
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .testTag("delete_saved_paper_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete",
                                                    tint = colors.red.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Export Bar (Export Selected or Export All)
                if (papers.isNotEmpty() && viewModel != null) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isSelectionMode && selectedIds.isNotEmpty()) {
                                Button(
                                    onClick = { showExportMenu = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export Selected (${selectedIds.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { showExportMenu = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.navyBanner),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export All Sets (${papers.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Export Options Dropdown
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            val isSubset = isSelectionMode && selectedIds.isNotEmpty()
                            val count = if (isSubset) selectedIds.size else papers.size

                            DropdownMenuItem(
                                text = { Text("Save as JSON File (.json)") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showExportMenu = false
                                    val json = if (isSubset) {
                                        viewModel.exportSelectedPapersAsJson(selectedIds)
                                    } else {
                                        viewModel.exportAllPapersAsJson()
                                    }
                                    if (!json.isNullOrBlank()) {
                                        val fileName = if (isSubset) "examset_selected_${count}_papers.json" else "examset_all_${count}_papers.json"
                                        triggerExportToFile(json, fileName, "application/json")
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Save as Text Summary (.txt)") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showExportMenu = false
                                    val txt = if (isSubset) {
                                        viewModel.exportPapersAsFormattedText(selectedIds)
                                    } else {
                                        viewModel.exportPapersAsFormattedText()
                                    }
                                    if (!txt.isNullOrBlank()) {
                                        val fileName = if (isSubset) "examset_selected_${count}_papers.txt" else "examset_all_${count}_papers.txt"
                                        triggerExportToFile(txt, fileName, "text/plain")
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Share via Apps...") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showExportMenu = false
                                    val json = if (isSubset) {
                                        viewModel.exportSelectedPapersAsJson(selectedIds)
                                    } else {
                                        viewModel.exportAllPapersAsJson()
                                    }
                                    if (!json.isNullOrBlank()) {
                                        triggerShare(json, "ExamSet ($count Questions Sets)")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog before deleting paper
    if (paperToDelete != null) {
        val target = paperToDelete!!
        AlertDialog(
            onDismissRequest = { paperToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = colors.red
                )
            },
            title = {
                Text(
                    text = "Delete Question Set?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${target.title.ifBlank { "Untitled Set" }}\"? This will permanently remove the question set and cannot be undone.",
                    fontSize = 12.5.sp,
                    color = colors.ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDel = target
                        paperToDelete = null
                        onDeletePaper(toDel)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red),
                    modifier = Modifier.testTag("confirm_delete_saved_paper_button")
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { paperToDelete = null }) {
                    Text("Cancel", color = colors.ink)
                }
            }
        )
    }
}
