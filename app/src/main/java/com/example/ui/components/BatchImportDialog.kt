package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.BatchImportItem
import com.example.model.BatchImportResult
import com.example.model.BatchItemStatus
import com.example.model.FolderItem
import com.example.model.ValidationIssue
import com.example.model.ValidationSeverity
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme
import kotlinx.coroutines.launch

private enum class FilterTab {
    ALL, READY, WARNINGS, ERRORS
}

private enum class InputMode {
    FILES, PASTE_JSON
}

@Composable
fun BatchImportDialog(
    initialItems: List<BatchImportItem>,
    folders: List<FolderItem>,
    defaultFolderId: String = "all",
    onDismiss: () -> Unit,
    onAddMoreFiles: () -> Unit,
    onImportSuccess: (BatchImportResult) -> Unit,
    viewModel: MainViewModel
) {
    val colors = ExamTheme.colors
    val coroutineScope = rememberCoroutineScope()

    var items by remember { mutableStateOf(initialItems) }
    var activeFilter by remember { mutableStateOf(FilterTab.ALL) }
    var inputMode by remember { mutableStateOf(InputMode.FILES) }
    var pastedJsonText by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    // Target folder selection
    val activeFolderObj = folders.find { it.id == defaultFolderId }
    var selectedTargetFolder by remember {
        mutableStateOf(if (activeFolderObj != null) activeFolderObj.name else "Keep Original Metadata")
    }
    var showFolderDropdown by remember { mutableStateOf(false) }
    var customNewFolderText by remember { mutableStateOf("") }
    var showCustomFolderInput by remember { mutableStateOf(false) }

    // Counts
    val totalFiles = items.size
    val validCount = items.count { it.status == BatchItemStatus.VALID }
    val warningCount = items.count { it.status == BatchItemStatus.WARNING }
    val invalidCount = items.count { it.status == BatchItemStatus.INVALID }
    val selectedCount = items.count { it.isSelected && it.status != BatchItemStatus.INVALID }
    val totalQuestionsSelected = items.filter { it.isSelected && it.paper != null && it.status != BatchItemStatus.INVALID }
        .sumOf { it.paper!!.questions.size }

    val filteredItems = items.filter { item ->
        when (activeFilter) {
            FilterTab.ALL -> true
            FilterTab.READY -> item.status != BatchItemStatus.INVALID
            FilterTab.WARNINGS -> item.status == BatchItemStatus.WARNING
            FilterTab.ERRORS -> item.status == BatchItemStatus.INVALID
        }
    }

    Dialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.background,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.teal.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Batch Import & Bulk Validation",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.teal.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "$totalFiles files",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.teal,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Simultaneously validate schema, questions, keys, and organize into folders",
                                fontSize = 10.5.sp,
                                color = colors.inkMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isImporting,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.inkMuted)
                    }
                }

                HorizontalDivider(thickness = 0.8.dp, color = colors.border)

                // 2. Mode Selector: Files vs Paste JSON
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (inputMode == InputMode.FILES) colors.teal else Color.Transparent,
                            border = if (inputMode == InputMode.FILES) null else BorderStroke(0.8.dp, colors.border),
                            modifier = Modifier.clickable { inputMode = InputMode.FILES }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = if (inputMode == InputMode.FILES) Color.White else colors.inkMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Selected Files ($totalFiles)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (inputMode == InputMode.FILES) Color.White else colors.ink
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (inputMode == InputMode.PASTE_JSON) colors.teal else Color.Transparent,
                            border = if (inputMode == InputMode.PASTE_JSON) null else BorderStroke(0.8.dp, colors.border),
                            modifier = Modifier.clickable { inputMode = InputMode.PASTE_JSON }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PostAdd,
                                    contentDescription = null,
                                    tint = if (inputMode == InputMode.PASTE_JSON) Color.White else colors.inkMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Paste Raw JSON",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (inputMode == InputMode.PASTE_JSON) Color.White else colors.ink
                                )
                            }
                        }
                    }

                    if (inputMode == InputMode.FILES) {
                        OutlinedButton(
                            onClick = onAddMoreFiles,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Add Files", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // 3. Folder Assignment & Global Settings
                Surface(
                    color = colors.surface,
                    border = BorderStroke(0.8.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Target Folder:",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))

                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(0.8.dp, colors.border),
                                        modifier = Modifier.clickable { showFolderDropdown = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = selectedTargetFolder,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.inkBold
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = colors.inkMuted
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showFolderDropdown,
                                        onDismissRequest = { showFolderDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Keep Original Metadata", fontSize = 12.sp) },
                                            onClick = {
                                                selectedTargetFolder = "Keep Original Metadata"
                                                showCustomFolderInput = false
                                                showFolderDropdown = false
                                            }
                                        )
                                        folders.forEach { f ->
                                            DropdownMenuItem(
                                                text = { Text(f.name, fontSize = 12.sp) },
                                                onClick = {
                                                    selectedTargetFolder = f.name
                                                    showCustomFolderInput = false
                                                    showFolderDropdown = false
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("+ Create New Folder...", fontSize = 12.sp, color = colors.teal) },
                                            onClick = {
                                                showCustomFolderInput = true
                                                showFolderDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Bulk Selection Toggles
                            if (inputMode == InputMode.FILES && items.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = {
                                            items = items.map {
                                                if (it.status != BatchItemStatus.INVALID) it.copy(isSelected = true) else it
                                            }
                                        },
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Select Valid", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                                    }
                                    TextButton(
                                        onClick = {
                                            items = items.map { it.copy(isSelected = false) }
                                        },
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Deselect All", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.inkMuted)
                                    }
                                }
                            }
                        }

                        // Custom new folder inline input if chosen
                        if (showCustomFolderInput) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customNewFolderText,
                                    onValueChange = { customNewFolderText = it },
                                    placeholder = { Text("New folder name e.g. Geography Mock Tests", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                                        unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.15f)
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        if (customNewFolderText.isNotBlank()) {
                                            selectedTargetFolder = customNewFolderText.trim()
                                            showCustomFolderInput = false
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Set", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = {
                                        showCustomFolderInput = false
                                        selectedTargetFolder = "Keep Original Metadata"
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // 4. Main Body: Files List vs Paste JSON
                if (inputMode == InputMode.PASTE_JSON) {
                    // Paste JSON View
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Paste ExamSet JSON payload or array of papers:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Text(
                            text = "Accepts both single exam paper objects `{ \"metadata\": ..., \"questions\": [...] }` and arrays `[ {...}, {...} ]`.",
                            fontSize = 10.5.sp,
                            color = colors.inkMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = pastedJsonText,
                            onValueChange = { pastedJsonText = it },
                            placeholder = {
                                Text(
                                    text = "{\n  \"metadata\": { \"title\": \"UPSC Prelims Mock\" },\n  \"questions\": [\n    {\n      \"num\": 1,\n      \"question\": \"...\",\n      \"options\": { \"a\": \"...\", \"b\": \"...\" },\n      \"correctAnswer\": \"A\"\n    }\n  ]\n}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.inkMuted.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = colors.ink
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    if (pastedJsonText.isNotBlank()) {
                                        viewModel.batchValidateJsonString(pastedJsonText, "Pasted_Batch.json") { newItems ->
                                            items = items + newItems
                                            pastedJsonText = ""
                                            inputMode = InputMode.FILES
                                        }
                                    }
                                },
                                enabled = pastedJsonText.isNotBlank(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Validate & Add to Batch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Files List View
                    Column(modifier = Modifier.weight(1f)) {
                        // Filter Pills Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ValidationFilterChip(
                                label = "All",
                                count = totalFiles,
                                isSelected = activeFilter == FilterTab.ALL,
                                badgeColor = colors.teal,
                                onClick = { activeFilter = FilterTab.ALL }
                            )
                            ValidationFilterChip(
                                label = "Ready to Import",
                                count = validCount + warningCount,
                                isSelected = activeFilter == FilterTab.READY,
                                badgeColor = Color(0xFF059669),
                                onClick = { activeFilter = FilterTab.READY }
                            )
                            ValidationFilterChip(
                                label = "Valid",
                                count = validCount,
                                isSelected = activeFilter == FilterTab.ALL && validCount > 0,
                                badgeColor = Color(0xFF16A34A),
                                onClick = { activeFilter = FilterTab.ALL }
                            )
                            if (warningCount > 0) {
                                ValidationFilterChip(
                                    label = "Warnings",
                                    count = warningCount,
                                    isSelected = activeFilter == FilterTab.WARNINGS,
                                    badgeColor = Color(0xFFD97706),
                                    onClick = { activeFilter = FilterTab.WARNINGS }
                                )
                            }
                            if (invalidCount > 0) {
                                ValidationFilterChip(
                                    label = "Errors",
                                    count = invalidCount,
                                    isSelected = activeFilter == FilterTab.ERRORS,
                                    badgeColor = Color(0xFFDC2626),
                                    onClick = { activeFilter = FilterTab.ERRORS }
                                )
                            }
                        }

                        HorizontalDivider(thickness = 0.5.dp, color = colors.border)

                        if (filteredItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = colors.inkMuted.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = if (items.isEmpty()) "No JSON files selected" else "No papers match this filter",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (items.isEmpty()) "Click \"Add Files\" or \"Paste Raw JSON\" to import" else "Try switching to the 'All' filter",
                                        fontSize = 11.5.sp,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredItems, key = { it.id }) { item ->
                                    BatchPaperCard(
                                        item = item,
                                        onToggleSelected = { isSelected ->
                                            items = items.map {
                                                if (it.id == item.id) it.copy(isSelected = isSelected) else it
                                            }
                                        },
                                        onRemove = {
                                            items = items.filter { it.id != item.id }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Footer Import Bar
                Surface(
                    color = colors.surface,
                    border = BorderStroke(0.8.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Selected: $selectedCount of $totalFiles papers",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = if (selectedCount > 0) "$totalQuestionsSelected total questions to import" else "Select valid papers above",
                                fontSize = 10.5.sp,
                                color = colors.inkMuted
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !isImporting,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (selectedCount > 0 && !isImporting) {
                                        isImporting = true
                                        coroutineScope.launch {
                                            val result = viewModel.importValidatedBatch(
                                                items = items,
                                                targetFolder = selectedTargetFolder
                                            )
                                            isImporting = false
                                            onImportSuccess(result)
                                            onDismiss()
                                        }
                                    }
                                },
                                enabled = selectedCount > 0 && !isImporting,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Importing...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectedCount > 0) "Import $selectedCount Paper(s)" else "Import",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
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

@Composable
private fun ValidationFilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    badgeColor: Color,
    onClick: () -> Unit
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) badgeColor.copy(alpha = 0.15f) else colors.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (isSelected) badgeColor else colors.border),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(badgeColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label ($count)",
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) colors.inkBold else colors.ink
            )
        }
    }
}

@Composable
private fun BatchPaperCard(
    item: BatchImportItem,
    onToggleSelected: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    val colors = ExamTheme.colors
    var isExpandedIssues by remember { mutableStateOf(item.status != BatchItemStatus.VALID) }
    var isExpandedQuestions by remember { mutableStateOf(false) }

    val statusColor = when (item.status) {
        BatchItemStatus.VALID -> Color(0xFF059669)
        BatchItemStatus.WARNING -> Color(0xFFD97706)
        BatchItemStatus.INVALID -> Color(0xFFDC2626)
    }

    val statusLabel = when (item.status) {
        BatchItemStatus.VALID -> "VALID"
        BatchItemStatus.WARNING -> "WARNING (${item.warningCount})"
        BatchItemStatus.INVALID -> "INVALID (${item.errorCount})"
    }

    val isCardDisabled = item.status == BatchItemStatus.INVALID

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(
            1.dp,
            if (item.isSelected && !isCardDisabled) statusColor.copy(alpha = 0.5f) else colors.border
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Checkbox, Title/Filename, Status Badge, Remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isSelected && !isCardDisabled,
                    onCheckedChange = { if (!isCardDisabled) onToggleSelected(it) },
                    enabled = !isCardDisabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = statusColor,
                        uncheckedColor = colors.inkMuted
                    ),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.paper?.metadata?.title ?: item.fileName,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.fileName,
                            fontSize = 10.5.sp,
                            color = colors.inkMuted,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.fileSizeFormatted.isNotBlank()) {
                            Text(
                                text = " • ${item.fileSizeFormatted}",
                                fontSize = 10.5.sp,
                                color = colors.inkMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, statusColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (item.status) {
                                BatchItemStatus.VALID -> Icons.Default.CheckCircle
                                BatchItemStatus.WARNING -> Icons.Default.Warning
                                BatchItemStatus.INVALID -> Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusLabel,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Remove from batch",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metrics Row (if paper is present)
            if (item.paper != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Questions: ${item.paper.questions.size}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold
                    )
                    Text(
                        text = "Keys: ${item.paper.quickAnswersCount}/${item.paper.questions.size}",
                        fontSize = 10.5.sp,
                        color = if (item.paper.quickAnswersCount == item.paper.questions.size) Color(0xFF059669) else colors.inkMuted
                    )
                    Text(
                        text = "Explanations: ${item.paper.explanationsCount}/${item.paper.questions.size}",
                        fontSize = 10.5.sp,
                        color = colors.inkMuted
                    )
                    Text(
                        text = "Subject: ${item.paper.metadata.subject}",
                        fontSize = 10.5.sp,
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Collapsible Validation Report (Issues)
            if (item.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpandedIssues = !isExpandedIssues }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (item.errorCount > 0) Icons.Default.Error else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (item.errorCount > 0) Color(0xFFDC2626) else Color(0xFFD97706),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Validation Report (${item.issues.size} notices)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.errorCount > 0) Color(0xFFDC2626) else colors.inkBold
                        )
                    }
                    Icon(
                        imageVector = if (isExpandedIssues) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.inkMuted
                    )
                }

                AnimatedVisibility(
                    visible = isExpandedIssues,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.issues.forEach { issue ->
                            ValidationIssueRow(issue)
                        }
                    }
                }
            }

            // Collapsible Question Preview (first 2 questions)
            if (item.paper != null && item.paper.questions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpandedQuestions = !isExpandedQuestions }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpandedQuestions) "Hide Questions Preview" else "Preview Questions (${item.paper.questions.size})",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.teal
                    )
                    Icon(
                        imageVector = if (isExpandedQuestions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.teal
                    )
                }

                AnimatedVisibility(
                    visible = isExpandedQuestions,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item.paper.questions.take(2).forEach { q ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(0.6.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Q${q.num}. ${q.question}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("(A) ${q.options.a}", fontSize = 10.sp, color = colors.inkMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("(B) ${q.options.b}", fontSize = 10.sp, color = colors.inkMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("(C) ${q.options.c}", fontSize = 10.sp, color = colors.inkMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("(D) ${q.options.d}", fontSize = 10.sp, color = colors.inkMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (q.correctAnswer != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Key: (${q.correctAnswer})" + if (!q.explanation.isNullOrBlank()) " • ${q.explanation}" else "",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF059669),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
private fun ValidationIssueRow(issue: ValidationIssue) {
    val colors = ExamTheme.colors
    val (icon, tint) = when (issue.severity) {
        ValidationSeverity.ERROR -> Icons.Default.Error to Color(0xFFDC2626)
        ValidationSeverity.WARNING -> Icons.Default.Warning to Color(0xFFD97706)
        ValidationSeverity.INFO -> Icons.Default.Info to Color(0xFF0284C7)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = issue.message,
            fontSize = 10.sp,
            color = if (issue.severity == ValidationSeverity.ERROR) Color(0xFFDC2626) else colors.ink,
            lineHeight = 13.sp
        )
    }
}
