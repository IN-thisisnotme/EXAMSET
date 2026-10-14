package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PaperEntity
import com.example.model.FolderItem
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FolderManagerTab(
    viewModel: MainViewModel,
    onNavigateToMaster: () -> Unit,
    onNavigateToStudent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val savedPapers by viewModel.savedPapers.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentPaperEntityId by viewModel.currentPaperEntityId.collectAsState()
    val lastOpenedPaper by viewModel.lastOpenedPaper.collectAsState()
    val currentPaper by viewModel.currentPaper.collectAsState()

    var activeFolderId by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderColor by remember { mutableStateOf("#0284C7") }
    var folderToDelete by remember { mutableStateOf<FolderItem?>(null) }
    var paperToAssignFolder by remember { mutableStateOf<PaperEntity?>(null) }

    val filteredPapers = savedPapers.filter { paper ->
        val matchesSearch = searchQuery.isBlank() ||
                paper.title.contains(searchQuery, ignoreCase = true) ||
                paper.subject.contains(searchQuery, ignoreCase = true) ||
                paper.topic.contains(searchQuery, ignoreCase = true) ||
                paper.setNumber.contains(searchQuery, ignoreCase = true)

        val matchesFolder = if (activeFolderId == "all") {
            true
        } else {
            val f = folders.find { it.id == activeFolderId }
            f != null && (
                paper.subject.equals(f.name, ignoreCase = true) ||
                paper.subject.contains(f.name, ignoreCase = true) ||
                paper.topic.contains(f.name, ignoreCase = true) ||
                paper.title.contains(f.name, ignoreCase = true)
            )
        }
        matchesSearch && matchesFolder
    }

    val totalQuestionsCount = savedPapers.sumOf { it.questionCount }
    val totalKeysCount = savedPapers.sumOf { it.answerKeyCount }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Top Action Header
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.teal.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Library & Folders",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Organize, manage, and review assessment sets",
                                fontSize = 11.5.sp,
                                color = colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { showNewFolderDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryButtonBg,
                            contentColor = colors.primaryButtonText
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.primaryButtonText)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = colors.primaryButtonText)
                    }
                }
            }
        }

        // Last Opened Assessment Set Card
        if (lastOpenedPaper != null) {
            val lastPaper = lastOpenedPaper!!
            val isCurrentActive = currentPaperEntityId == lastPaper.id || currentPaper?.metadata?.title == lastPaper.title
            val formattedDate = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(lastPaper.updatedAt))

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentActive) colors.tealLight else colors.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, if (isCurrentActive) colors.teal else colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("last_opened_paper_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isCurrentActive) Color(0xFFDCFCE7) else colors.teal.copy(alpha = 0.15f),
                                border = BorderStroke(0.6.dp, if (isCurrentActive) Color(0xFF16A34A) else colors.teal)
                            ) {
                                Text(
                                    text = if (isCurrentActive) "CURRENTLY ACTIVE SET" else "LAST OPENED SET",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrentActive) Color(0xFF15803D) else colors.tealDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = formattedDate,
                                fontSize = 10.5.sp,
                                color = colors.muted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lastPaper.title,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${lastPaper.setNumber.ifBlank { "Set A" }} • ${lastPaper.subject} • ${lastPaper.questionCount} Questions",
                                    fontSize = 11.5.sp,
                                    color = colors.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Button(
                                onClick = {
                                    viewModel.loadSavedPaper(lastPaper)
                                    onNavigateToMaster()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCurrentActive) colors.teal else colors.navyBanner
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCurrentActive) Icons.Default.Check else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isCurrentActive) "View in Master" else "Open Set",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Folder Pills
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // All Papers pill
                FolderPill(
                    label = "All Papers (${savedPapers.size})",
                    isSelected = activeFolderId == "all",
                    dotColor = Color(0xFF2563EB),
                    onClick = { activeFolderId = "all" }
                )

                folders.forEach { folder ->
                    val color = parseHexColor(folder.color)
                    val count = savedPapers.count {
                        it.subject.equals(folder.name, ignoreCase = true) ||
                        it.subject.contains(folder.name, ignoreCase = true) ||
                        it.topic.contains(folder.name, ignoreCase = true)
                    }
                    FolderPill(
                        label = "${folder.name} ($count)",
                        isSelected = activeFolderId == folder.id,
                        dotColor = color,
                        onClick = { activeFolderId = folder.id },
                        onDelete = { folderToDelete = folder }
                    )
                }
            }
        }

        // Empty state banner if no custom folders exist
        if (folders.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(0.7.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Organize with Custom Folders",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Use 'New Folder' above to group papers by exam or subject.",
                                fontSize = 11.sp,
                                color = colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search papers...",
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = colors.inkMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = colors.teal,
                        modifier = Modifier.size(17.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(15.dp),
                                tint = colors.inkMuted
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.2f),
                    focusedTextColor = colors.inkBold,
                    unfocusedTextColor = colors.ink,
                    cursorColor = colors.teal,
                    focusedBorderColor = colors.teal,
                    unfocusedBorderColor = colors.border
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Stats Overview Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FolderMetricCard(
                    title = "Total Papers",
                    value = "${savedPapers.size}",
                    valueColor = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f)
                )
                FolderMetricCard(
                    title = "Question Pool",
                    value = "$totalQuestionsCount",
                    valueColor = Color(0xFF059669),
                    modifier = Modifier.weight(1f)
                )
                FolderMetricCard(
                    title = "Answer Keys",
                    value = "$totalKeysCount",
                    valueColor = Color(0xFF7C3AED),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Saved Papers List
        if (filteredPapers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surface,
                    border = BorderStroke(0.8.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = colors.inkMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No papers match your search" else "No saved papers in this folder",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import question files in Exam Tools or create questions to populate your library.",
                            fontSize = 11.5.sp,
                            color = colors.inkMuted
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    viewModel.loadStarterSample("science")
                                    onNavigateToMaster()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                                modifier = Modifier.testTag("load_starter_exam_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Load Starter Exam",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.selectTab(4) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("create_paper_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = colors.teal
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Create Questions",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.teal
                                )
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredPapers) { paper ->
                PaperCardItem(
                    paper = paper,
                    isActive = paper.id == currentPaperEntityId,
                    onOpenMaster = {
                        viewModel.loadSavedPaper(paper) {
                            onNavigateToMaster()
                        }
                    },
                    onTakeTest = {
                        viewModel.loadSavedPaper(paper) {
                            viewModel.startExam()
                            onNavigateToStudent()
                        }
                    },
                    onAssignFolder = {
                        paperToAssignFolder = paper
                    },
                    onDelete = { viewModel.deleteSavedPaper(paper) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        val colorOptions = listOf("#0284C7", "#16A34A", "#D97706", "#7C3AED", "#DC2626", "#0D9488")
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder Name") },
                        placeholder = { Text("e.g. UPSC Prelims, Physics 101") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Select Color Tag", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.inkMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colorOptions.forEach { hex ->
                            val col = parseHexColor(hex)
                            Surface(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { newFolderColor = hex },
                                shape = CircleShape,
                                color = col,
                                border = if (newFolderColor == hex) BorderStroke(2.dp, colors.inkBold) else null
                            ) {
                                if (newFolderColor == hex) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            val createdName = newFolderName.trim()
                            viewModel.createFolder(createdName, newFolderColor)
                            if (paperToAssignFolder != null) {
                                viewModel.assignPaperToFolder(paperToAssignFolder!!, createdName)
                                paperToAssignFolder = null
                            }
                            newFolderName = ""
                            showNewFolderDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Folder Confirmation Dialog
    if (folderToDelete != null) {
        val target = folderToDelete!!
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete Folder?", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete the folder \"${target.name}\"? Your saved exam papers will not be deleted.",
                    fontSize = 13.sp,
                    color = colors.ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activeFolderId == target.id) {
                            activeFolderId = "all"
                        }
                        viewModel.deleteFolder(target.id)
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Assign Paper to Folder Dialog
    if (paperToAssignFolder != null) {
        val targetPaper = paperToAssignFolder!!
        AlertDialog(
            onDismissRequest = { paperToAssignFolder = null },
            title = { Text("Assign Paper to Folder", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Select a folder for \"${targetPaper.title}\":",
                        fontSize = 12.sp,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.assignPaperToFolder(targetPaper, "General")
                                paperToAssignFolder = null
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = if (targetPaper.subject.equals("General", ignoreCase = true) || targetPaper.subject.isBlank()) colors.surfaceVariant else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("General / No Folder", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.inkBold)
                        }
                    }

                    folders.forEach { f ->
                        val col = parseHexColor(f.color)
                        val isSelected = targetPaper.subject.equals(f.name, ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.assignPaperToFolder(targetPaper, f.name)
                                    paperToAssignFolder = null
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) col.copy(alpha = 0.15f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(10.dp).background(col, CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = f.name,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) col else colors.inkBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            showNewFolderDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Create New Folder", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { paperToAssignFolder = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun PaperCardItem(
    paper: PaperEntity,
    isActive: Boolean,
    onOpenMaster: () -> Unit,
    onTakeTest: () -> Unit,
    onAssignFolder: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = ExamTheme.colors
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateStr = remember(paper.updatedAt) {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        sdf.format(Date(paper.updatedAt))
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(
            if (isActive) 1.5.dp else 0.8.dp,
            if (isActive) Color(0xFF2563EB) else colors.border
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Category & Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(0.5.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.clickable { onAssignFolder() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = paper.subject.ifBlank { "General" },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(0.5.dp, colors.border)
                    ) {
                        Text(
                            text = paper.setNumber.ifBlank { "Set A" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFD1FAE5),
                            border = BorderStroke(0.5.dp, Color(0xFF10B981))
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF047857),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = dateStr,
                        fontSize = 10.5.sp,
                        color = colors.inkMuted
                    )

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("delete_paper_card_button")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Paper",
                            tint = Color(0xFFEF4444).copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = paper.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (paper.topic.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = paper.topic,
                    fontSize = 11.5.sp,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${paper.questionCount} Questions",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink
                )
                Text(
                    text = "${paper.answerKeyCount} Keys",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (paper.answerKeyCount == paper.questionCount && paper.questionCount > 0) Color(0xFF059669) else Color(0xFFD97706)
                )
                Text(
                    text = "${paper.explanationCount} Explanations",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2563EB)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom actions organized cleanly into responsive primary and secondary rows
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary Action Buttons (Open in Master & Practice Test)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenMaster,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryButtonBg,
                            contentColor = colors.primaryButtonText
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.primaryButtonText)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Master View",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            color = colors.primaryButtonText
                        )
                    }

                    OutlinedButton(
                        onClick = onTakeTest,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF059669)),
                        border = BorderStroke(1.dp, Color(0xFF059669)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF059669))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Practice",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            color = Color(0xFF059669)
                        )
                    }
                }

                // Secondary Actions (Folder assignment - preserved for future use)
                // Row(
                //     modifier = Modifier.fillMaxWidth(),
                //     horizontalArrangement = Arrangement.SpaceBetween,
                //     verticalAlignment = Alignment.CenterVertically
                // ) {
                //     Surface(
                //         onClick = onAssignFolder,
                //         shape = RoundedCornerShape(6.dp),
                //         color = colors.surfaceVariant,
                //         border = BorderStroke(0.8.dp, colors.border)
                //     ) {
                //         Row(
                //             modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                //             verticalAlignment = Alignment.CenterVertically
                //         ) {
                //             Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.teal)
                //             Spacer(modifier = Modifier.width(5.dp))
                //             Text("Move Folder", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = colors.inkBold)
                //         }
                //     }
                // }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color(0xFFEF4444)
                )
            },
            title = {
                Text(
                    text = "Delete Question Paper?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${paper.title.ifBlank { "Untitled Paper" }}\"? This action cannot be undone.",
                    fontSize = 13.5.sp,
                    color = colors.ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FolderPill(
    label: String,
    isSelected: Boolean,
    dotColor: Color,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val colors = ExamTheme.colors
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) colors.inkBold else colors.surface,
        border = BorderStroke(0.8.dp, if (isSelected) colors.inkBold else colors.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) colors.surface else colors.inkBold
            )
            if (onDelete != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Delete folder",
                        tint = if (isSelected) colors.surface.copy(alpha = 0.8f) else colors.inkMuted,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderMetricCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.6.dp, colors.border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = valueColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = title, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = colors.inkMuted)
        }
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = clean.toLong(16)
        if (clean.length == 6) {
            Color(colorInt or 0x00000000FF000000)
        } else {
            Color(colorInt)
        }
    } catch (e: Exception) {
        Color(0xFF0284C7)
    }
}
