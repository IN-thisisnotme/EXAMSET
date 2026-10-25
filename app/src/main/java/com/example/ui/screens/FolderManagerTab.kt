package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

/**
 * Quick filter chips by examination stage and subject discipline (Step 8).
 */
enum class ExamStageFilter(val label: String) {
    ALL("All"),
    PRELIMS_TIER1("Prelims / Tier 1"),
    MAINS_TIER2("Mains / Tier 2"),
    GS_PAPERS("GS Papers"),
    CSAT_APTITUDE("CSAT / Aptitude"),
    TECHNICAL_OPTIONAL("Technical / Optional")
}

/**
 * Checks whether a paper matches the selected exam stage filter.
 */
fun matchesStageFilter(paper: PaperEntity, filter: ExamStageFilter, folderPath: String = ""): Boolean {
    if (filter == ExamStageFilter.ALL) return true
    val combined = "${paper.title} ${paper.subject} ${paper.topic} ${paper.exam} $folderPath".lowercase(Locale.getDefault())
    return when (filter) {
        ExamStageFilter.ALL -> true
        ExamStageFilter.PRELIMS_TIER1 -> {
            combined.contains("prelim") || combined.contains("tier 1") || combined.contains("tier-1") ||
            combined.contains("tier i") || combined.contains("stage 1") || combined.contains("stage-1") ||
            combined.contains("cbt 1") || combined.contains("cbt-1") || combined.contains(" pt ") ||
            combined.contains("pt-") || combined.contains("preliminary") || combined.contains("tier1") ||
            combined.contains("paper 1") || combined.contains("paper i")
        }
        ExamStageFilter.MAINS_TIER2 -> {
            combined.contains("main") || combined.contains("tier 2") || combined.contains("tier-2") ||
            combined.contains("tier ii") || combined.contains("stage 2") || combined.contains("stage-2") ||
            combined.contains("cbt 2") || combined.contains("cbt-2") || combined.contains("tier2") ||
            combined.contains("descriptive")
        }
        ExamStageFilter.GS_PAPERS -> {
            combined.contains("gs") || combined.contains("general studies") ||
            combined.contains("general knowledge") || combined.contains("gk") ||
            combined.contains("general awareness") || combined.contains("ga") ||
            combined.contains("polity") || combined.contains("history") ||
            combined.contains("geography") || combined.contains("economy") ||
            combined.contains("environment")
        }
        ExamStageFilter.CSAT_APTITUDE -> {
            combined.contains("csat") || combined.contains("aptitude") ||
            combined.contains("reasoning") || combined.contains("mental ability") ||
            combined.contains("quant") || combined.contains("english") ||
            combined.contains("comprehension") || combined.contains("verbal")
        }
        ExamStageFilter.TECHNICAL_OPTIONAL -> {
            combined.contains("technical") || combined.contains("optional") ||
            combined.contains("engineering") || combined.contains("civil") ||
            combined.contains("mechanical") || combined.contains("electrical") ||
            combined.contains("electronics") || combined.contains("computer") ||
            combined.contains("gate") || combined.contains("ese") ||
            combined.contains("je") || combined.contains("ae") ||
            combined.contains("science")
        }
    }
}

/**
 * Segregation modes for organizing papers inside folders.
 */
enum class PaperSegregationMode(val label: String, val icon: ImageVector) {
    ALL("All Sets", Icons.Default.ViewList),
    BY_STAGE("By Paper Type", Icons.Default.Layers),
    BY_SET_SERIES("By Set Number", Icons.Default.ViewAgenda)
}

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

    // Active folder state: "all" means root overview, or a folderId
    var activeFolderId by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var segregationMode by remember { mutableStateOf(PaperSegregationMode.ALL) }

    // Selection & Batch Move states
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPaperIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var isDeepSearchAcrossAllFolders by remember { mutableStateOf(false) }
    var selectedStageFilter by remember { mutableStateOf(ExamStageFilter.ALL) }
    var showFolderExportMenu by remember { mutableStateOf(false) }
    var showSelectionExportMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val triggerShare: (String, String) -> Unit = remember(context) {
        { data, title ->
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, data)
                putExtra(Intent.EXTRA_SUBJECT, title)
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(sendIntent, title))
        }
    }

    // Dialog states
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var parentFolderForNew by remember { mutableStateOf<FolderItem?>(null) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderColor by remember { mutableStateOf("#0284C7") }
    var folderToDelete by remember { mutableStateOf<FolderItem?>(null) }
    var paperToAssignFolder by remember { mutableStateOf<PaperEntity?>(null) }

    // Current active folder object (if inside a folder)
    val currentFolder = remember(activeFolderId, folders) {
        if (activeFolderId == "all") null else folders.find { it.id == activeFolderId }
    }

    // Breadcrumb path from root down to active folder
    val breadcrumbPath = remember(activeFolderId, folders) {
        viewModel.getBreadcrumbPath(activeFolderId)
    }

    // Immediate child subfolders for current view
    val immediateSubfolders = remember(activeFolderId, folders) {
        if (activeFolderId == "all") {
            folders.filter { it.parentId == null }
        } else {
            folders.filter { it.parentId == activeFolderId }
        }
    }

    // All descendant folder IDs for recursive matching inside active folder
    val descendantIds = remember(activeFolderId, folders) {
        if (activeFolderId == "all") emptySet()
        else viewModel.getAllDescendantFolderIds(activeFolderId)
    }

    // Filtered papers matching search query, stage filter, and active folder hierarchy
    val filteredPapers = remember(savedPapers, folders, activeFolderId, searchQuery, descendantIds, isDeepSearchAcrossAllFolders, selectedStageFilter) {
        savedPapers.filter { paper ->
            val folderPath = paper.folderId?.let { viewModel.getFullFolderPath(it) } ?: ""
            val matchesStage = matchesStageFilter(paper, selectedStageFilter, folderPath)
            if (!matchesStage) return@filter false

            val matchesSearch = searchQuery.isBlank() ||
                    paper.title.contains(searchQuery, ignoreCase = true) ||
                    paper.subject.contains(searchQuery, ignoreCase = true) ||
                    paper.topic.contains(searchQuery, ignoreCase = true) ||
                    paper.setNumber.contains(searchQuery, ignoreCase = true) ||
                    folderPath.contains(searchQuery, ignoreCase = true)

            val matchesFolder = if (activeFolderId == "all" || (isDeepSearchAcrossAllFolders && searchQuery.isNotBlank())) {
                true
            } else {
                val f = folders.find { it.id == activeFolderId }
                if (f == null) true
                else {
                    (paper.folderId != null && paper.folderId in descendantIds) ||
                    paper.subject.equals(f.name, ignoreCase = true) ||
                    paper.subject.contains(f.name, ignoreCase = true) ||
                    paper.topic.contains(f.name, ignoreCase = true) ||
                    paper.title.contains(f.name, ignoreCase = true)
                }
            }
            matchesSearch && matchesFolder
        }
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
                                    imageVector = if (activeFolderId == "all") Icons.Default.FolderOpen else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = currentFolder?.let { parseHexColor(it.color) } ?: colors.teal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = currentFolder?.name ?: "Library & Folders",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (currentFolder != null) {
                                    val count = filteredPapers.size
                                    val subCount = immediateSubfolders.size
                                    "$count test sets • $subCount sub-folders"
                                } else {
                                    "Multi-level exam hierarchies, UPSC & State PSC folders"
                                },
                                fontSize = 11.5.sp,
                                color = colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(
                                onClick = { showFolderExportMenu = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Export Folder or Library",
                                    tint = colors.inkBold,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showFolderExportMenu,
                                onDismissRequest = { showFolderExportMenu = false }
                            ) {
                                val isFolder = currentFolder != null
                                val targetName = currentFolder?.name ?: "All Sets"
                                DropdownMenuItem(
                                    text = { Text(if (isFolder) "Export '$targetName' (JSON)" else "Backup All Sets (JSON)") },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                    onClick = {
                                        showFolderExportMenu = false
                                        val json = if (isFolder) {
                                            viewModel.exportFolderPapersAsJson(currentFolder!!.id)
                                        } else {
                                            viewModel.exportAllPapersAsJson()
                                        }
                                        if (!json.isNullOrBlank()) {
                                            triggerShare(json, "$targetName Backup (${filteredPapers.size} Sets)")
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isFolder) "Export '$targetName' (Text)" else "Export All Sets (Text)") },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                    onClick = {
                                        showFolderExportMenu = false
                                        val txt = if (isFolder) {
                                            viewModel.exportFolderPapersAsFormattedText(currentFolder!!.id)
                                        } else {
                                            viewModel.exportPapersAsFormattedText()
                                        }
                                        if (!txt.isNullOrBlank()) {
                                            triggerShare(txt, "$targetName Summary (${filteredPapers.size} Sets)")
                                        }
                                    }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                isSelectionMode = !isSelectionMode
                                if (!isSelectionMode) selectedPaperIds = emptySet()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelectionMode) colors.teal else colors.surfaceVariant,
                                contentColor = if (isSelectionMode) colors.surface else colors.inkBold
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 34.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelectionMode) Icons.Default.Check else Icons.Default.Checklist,
                                contentDescription = "Select Sets",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSelectionMode) "Done" else "Select",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                parentFolderForNew = currentFolder
                                newFolderName = ""
                                showNewFolderDialog = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryButtonBg,
                                contentColor = colors.primaryButtonText
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 34.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.primaryButtonText)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (currentFolder != null) "Sub-Folder" else "New Folder",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                color = colors.primaryButtonText
                            )
                        }
                    }
                }
            }
        }

        // Modern Breadcrumb Navigation Bar
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(0.6.dp, colors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Root / All Folders pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (activeFolderId == "all") colors.inkBold else Color.Transparent,
                        modifier = Modifier.clickable { activeFolderId = "all" }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (activeFolderId == "all") colors.surface else colors.inkMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "All Folders",
                                fontSize = 11.sp,
                                fontWeight = if (activeFolderId == "all") FontWeight.Bold else FontWeight.Medium,
                                color = if (activeFolderId == "all") colors.surface else colors.ink
                            )
                        }
                    }

                    // Ancestor Breadcrumbs
                    breadcrumbPath.forEachIndexed { index, crumb ->
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        val isLast = index == breadcrumbPath.lastIndex
                        val crumbColor = parseHexColor(crumb.color)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isLast) crumbColor.copy(alpha = 0.15f) else Color.Transparent,
                            border = if (isLast) BorderStroke(0.8.dp, crumbColor) else null,
                            modifier = Modifier.clickable { activeFolderId = crumb.id }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(crumbColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = crumb.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isLast) crumbColor else colors.inkBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sub-Folders Grid / Panel
        if (immediateSubfolders.isNotEmpty() || currentFolder != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SubdirectoryArrowRight,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentFolder != null) "Sub-Folders in ${currentFolder.name}" else "Main Exam Folders",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.teal.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${immediateSubfolders.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.tealDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                parentFolderForNew = currentFolder
                                newFolderName = ""
                                showNewFolderDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (currentFolder != null) "+ Sub-Folder" else "+ Main Folder",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (immediateSubfolders.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.6.dp, colors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "No sub-folders here yet. Add sets directly or create sub-folders.",
                                    fontSize = 11.5.sp,
                                    color = colors.inkMuted
                                )
                                OutlinedButton(
                                    onClick = {
                                        parentFolderForNew = currentFolder
                                        newFolderName = ""
                                        showNewFolderDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("+ Add Sub-Folder", fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        // Horizontal or wrapped cards for immediate sub-folders
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            immediateSubfolders.forEach { sub ->
                                val subColor = parseHexColor(sub.color)
                                val subChildCount = folders.count { it.parentId == sub.id }
                                val subPapersCount = savedPapers.count { p ->
                                    p.folderId == sub.id ||
                                    p.subject.equals(sub.name, ignoreCase = true) ||
                                    p.topic.contains(sub.name, ignoreCase = true)
                                }

                                Surface(
                                    modifier = Modifier
                                        .width(180.dp)
                                        .clickable { activeFolderId = sub.id },
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.surface,
                                    border = BorderStroke(1.dp, subColor.copy(alpha = 0.4f)),
                                    shadowElevation = 0.5.dp
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = subColor.copy(alpha = 0.15f),
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = null,
                                                        tint = subColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { folderToDelete = sub },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Delete subfolder",
                                                    tint = colors.inkMuted,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = sub.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = "$subPapersCount sets" + if (subChildCount > 0) " • $subChildCount sub" else "",
                                            fontSize = 10.5.sp,
                                            color = colors.inkMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dynamic hierarchy tip when no custom folders exist yet
        if (folders.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(0.7.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dynamic Multi-Level Folders",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Folders are created automatically as you file question papers. Type or select exam paths like 'UPSC CSE / Prelims / GS 1' or 'SSC / CGL' during set creation or import to auto-nest folders on the fly.",
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                        }
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
                                    text = "Set ${lastPaper.setNumber.ifBlank { "01" }} • ${lastPaper.subject} • ${lastPaper.questionCount} Questions",
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

        // Search Bar & Filter Controls
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

        // Deep Search Across All Folders Filter Chip (Step 7)
        if (activeFolderId != "all" || searchQuery.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDeepSearchAcrossAllFolders) colors.teal.copy(alpha = 0.15f) else colors.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(0.6.dp, if (isDeepSearchAcrossAllFolders) colors.teal else colors.border),
                        modifier = Modifier.clickable {
                            isDeepSearchAcrossAllFolders = !isDeepSearchAcrossAllFolders
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (isDeepSearchAcrossAllFolders) colors.teal else colors.inkMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isDeepSearchAcrossAllFolders) "Deep Search Active: All Sub-Folders" else "Deep Search: Search all folders & sub-folders",
                                fontSize = 11.sp,
                                fontWeight = if (isDeepSearchAcrossAllFolders) FontWeight.Bold else FontWeight.Medium,
                                color = if (isDeepSearchAcrossAllFolders) colors.tealDark else colors.ink
                            )
                        }
                    }

                    if (searchQuery.isNotBlank()) {
                        Text(
                            text = "${filteredPapers.size} match${if (filteredPapers.size == 1) "" else "es"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.teal
                        )
                    }
                }
            }
        }

        // Quick Stage / Category Filter Chips (Step 8)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExamStageFilter.values().forEach { filter ->
                    val isSelected = selectedStageFilter == filter
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) colors.teal else colors.surfaceVariant,
                        border = BorderStroke(0.8.dp, if (isSelected) colors.teal else colors.border),
                        modifier = Modifier.clickable { selectedStageFilter = filter }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = filter.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else colors.inkBold
                            )
                        }
                    }
                }
            }
        }

        // Batch Selection, Move & Export Control Bar (Steps 6 & 9)
        if (isSelectionMode) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.teal.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, colors.teal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${selectedPaperIds.size} of ${filteredPapers.size} selected",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.tealDark
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            TextButton(
                                onClick = {
                                    selectedPaperIds = if (selectedPaperIds.size == filteredPapers.size) {
                                        emptySet()
                                    } else {
                                        filteredPapers.map { it.id }.toSet()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (selectedPaperIds.size == filteredPapers.size) "Deselect All" else "Select All",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.tealDark
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                OutlinedButton(
                                    onClick = { showSelectionExportMenu = true },
                                    enabled = selectedPaperIds.isNotEmpty(),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                                    border = BorderStroke(1.dp, if (selectedPaperIds.isNotEmpty()) colors.teal else colors.border),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 32.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Export (${selectedPaperIds.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                                DropdownMenu(
                                    expanded = showSelectionExportMenu,
                                    onDismissRequest = { showSelectionExportMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Export JSON Package (.json)") },
                                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                        onClick = {
                                            showSelectionExportMenu = false
                                            val json = viewModel.exportSelectedPapersAsJson(selectedPaperIds)
                                            if (!json.isNullOrBlank()) {
                                                triggerShare(json, "Exam Package (${selectedPaperIds.size} Sets)")
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export Formatted Text (.txt)") },
                                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                        onClick = {
                                            showSelectionExportMenu = false
                                            val txt = viewModel.exportPapersAsFormattedText(selectedPaperIds)
                                            if (!txt.isNullOrBlank()) {
                                                triggerShare(txt, "Exam Summary (${selectedPaperIds.size} Sets)")
                                            }
                                        }
                                    )
                                }
                            }

                            Button(
                                onClick = { showBatchMoveDialog = true },
                                enabled = selectedPaperIds.isNotEmpty(),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 32.dp)
                            ) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Move to Folder", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Paper Segregation Toolbar (Step 4)
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Segregate Tests By:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted
                        )
                        Text(
                            text = "${filteredPapers.size} sets shown",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.teal
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PaperSegregationMode.values().forEach { mode ->
                            val isSelected = segregationMode == mode
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colors.inkBold else colors.surfaceVariant,
                                border = BorderStroke(0.6.dp, if (isSelected) colors.inkBold else colors.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { segregationMode = mode }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = mode.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) colors.surface else colors.inkMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = mode.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) colors.surface else colors.ink,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
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

        // Empty state if no papers match
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
                            text = "Import question files or move existing papers into this folder.",
                            fontSize = 11.5.sp,
                            color = colors.inkMuted
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.loadStarterSample("science")
                                    onNavigateToMaster()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("load_starter_exam_button")
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
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.selectTab(4) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("create_paper_button")
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
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.teal
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Segregated / Grouped Paper Rendering
            when (segregationMode) {
                PaperSegregationMode.ALL -> {
                    items(filteredPapers, key = { it.id }) { paper ->
                        val paperBreadcrumb = if (isDeepSearchAcrossAllFolders && paper.folderId != null) {
                            viewModel.getFullFolderPath(paper.folderId)
                        } else null

                        PaperCardItem(
                            paper = paper,
                            isActive = paper.id == currentPaperEntityId,
                            isSelectionMode = isSelectionMode,
                            isSelected = paper.id in selectedPaperIds,
                            onToggleSelect = {
                                selectedPaperIds = if (paper.id in selectedPaperIds) {
                                    selectedPaperIds - paper.id
                                } else {
                                    selectedPaperIds + paper.id
                                }
                            },
                            folderBreadcrumb = paperBreadcrumb,
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
                            onExport = {
                                val json = viewModel.exportSinglePaperAsJson(paper)
                                triggerShare(json, "${paper.title} - Set ${paper.setNumber.ifBlank { "01" }}")
                            },
                            onDelete = { viewModel.deleteSavedPaper(paper) }
                        )
                    }
                }

                PaperSegregationMode.BY_STAGE -> {
                    val groupedByStage = filteredPapers.groupBy { getStageCategory(it) }
                    groupedByStage.forEach { (stageName, papersInGroup) ->
                        item(key = "stage_header_$stageName") {
                            SegregationGroupHeader(
                                title = stageName,
                                count = papersInGroup.size,
                                icon = Icons.Default.Layers,
                                color = Color(0xFF2563EB)
                            )
                        }
                        items(papersInGroup, key = { it.id }) { paper ->
                            val paperBreadcrumb = if (isDeepSearchAcrossAllFolders && paper.folderId != null) {
                                viewModel.getFullFolderPath(paper.folderId)
                            } else null

                            PaperCardItem(
                                paper = paper,
                                isActive = paper.id == currentPaperEntityId,
                                isSelectionMode = isSelectionMode,
                                isSelected = paper.id in selectedPaperIds,
                                onToggleSelect = {
                                    selectedPaperIds = if (paper.id in selectedPaperIds) {
                                        selectedPaperIds - paper.id
                                    } else {
                                        selectedPaperIds + paper.id
                                    }
                                },
                                folderBreadcrumb = paperBreadcrumb,
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
                                onExport = {
                                    val json = viewModel.exportSinglePaperAsJson(paper)
                                    triggerShare(json, "${paper.title} - Set ${paper.setNumber.ifBlank { "01" }}")
                                },
                                onDelete = { viewModel.deleteSavedPaper(paper) }
                            )
                        }
                    }
                }

                PaperSegregationMode.BY_SET_SERIES -> {
                    val groupedBySet = filteredPapers.groupBy { getSetSeriesCategory(it) }
                        .toSortedMap()
                    groupedBySet.forEach { (setName, papersInGroup) ->
                        item(key = "set_header_$setName") {
                            SegregationGroupHeader(
                                title = setName,
                                count = papersInGroup.size,
                                icon = Icons.Default.ViewAgenda,
                                color = Color(0xFF0D9488)
                            )
                        }
                        items(papersInGroup, key = { it.id }) { paper ->
                            val paperBreadcrumb = if (isDeepSearchAcrossAllFolders && paper.folderId != null) {
                                viewModel.getFullFolderPath(paper.folderId)
                            } else null

                            PaperCardItem(
                                paper = paper,
                                isActive = paper.id == currentPaperEntityId,
                                isSelectionMode = isSelectionMode,
                                isSelected = paper.id in selectedPaperIds,
                                onToggleSelect = {
                                    selectedPaperIds = if (paper.id in selectedPaperIds) {
                                        selectedPaperIds - paper.id
                                    } else {
                                        selectedPaperIds + paper.id
                                    }
                                },
                                folderBreadcrumb = paperBreadcrumb,
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
                                onExport = {
                                    val json = viewModel.exportSinglePaperAsJson(paper)
                                    triggerShare(json, "${paper.title} - Set ${paper.setNumber.ifBlank { "01" }}")
                                },
                                onDelete = { viewModel.deleteSavedPaper(paper) }
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // New Folder / Sub-Folder Dialog
    if (showNewFolderDialog) {
        val colorOptions = listOf("#0284C7", "#16A34A", "#D97706", "#7C3AED", "#DC2626", "#0D9488")
        val isSubFolder = parentFolderForNew != null

        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = {
                Text(
                    text = if (isSubFolder) "Create Sub-Folder" else "Create New Folder",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    if (isSubFolder) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.tealLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.SubdirectoryArrowRight, contentDescription = null, tint = colors.teal, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Parent: ${parentFolderForNew?.name}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.tealDark
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text(if (isSubFolder) "Sub-Folder Name" else "Folder Name") },
                        placeholder = { Text(if (isSubFolder) "e.g. GS Paper 1, CSAT, Prelims" else "e.g. UPSC CSE, State PSC") },
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
                            val created = viewModel.createFolder(
                                name = createdName,
                                color = newFolderColor,
                                parentId = parentFolderForNew?.id
                            )
                            if (created != null && paperToAssignFolder != null) {
                                val fullPath = viewModel.getFullFolderPath(created.id)
                                viewModel.assignPaperToFolder(paperToAssignFolder!!, created.id, fullPath)
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
                    text = "Are you sure you want to delete \"${target.name}\" and any nested subfolders? Saved test papers will be safely kept and unlinked.",
                    fontSize = 13.sp,
                    color = colors.ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activeFolderId == target.id) {
                            activeFolderId = target.parentId ?: "all"
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

    // Step 5: Hierarchical Move / Assign Paper to Folder Dialog
    if (paperToAssignFolder != null) {
        val targetPaper = paperToAssignFolder!!
        AssignFolderTreeDialog(
            targetPaper = targetPaper,
            folders = folders,
            onAssign = { folderId, displayPath ->
                viewModel.assignPaperToFolder(targetPaper, folderId, displayPath)
                paperToAssignFolder = null
            },
            onCreateNewFolder = { parent ->
                parentFolderForNew = parent
                showNewFolderDialog = true
            },
            onDismiss = { paperToAssignFolder = null }
        )
    }

    // Step 6: Batch Multi-Set Move Dialog
    if (showBatchMoveDialog && selectedPaperIds.isNotEmpty()) {
        AssignFolderTreeDialog(
            targetPaper = null,
            batchCount = selectedPaperIds.size,
            folders = folders,
            onAssign = { folderId, displayPath ->
                viewModel.movePapersToFolder(selectedPaperIds, folderId, displayPath)
                selectedPaperIds = emptySet()
                isSelectionMode = false
                showBatchMoveDialog = false
            },
            onCreateNewFolder = { parent ->
                parentFolderForNew = parent
                showNewFolderDialog = true
            },
            onDismiss = { showBatchMoveDialog = false }
        )
    }
}

/**
 * Segregation Group Header
 */
@Composable
private fun SegregationGroupHeader(
    title: String,
    count: Int,
    icon: ImageVector,
    color: Color
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(0.6.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.18f)
            ) {
                Text(
                    text = "$count ${if (count == 1) "Paper" else "Papers"}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Categorizes a paper into UPSC/Competitive stage or paper type.
 */
private fun getStageCategory(paper: PaperEntity): String {
    val combined = "${paper.title} ${paper.subject} ${paper.topic}".lowercase()
    return when {
        "csat" in combined || "paper 2" in combined || "paper ii" in combined -> "CSAT / Paper II"
        "gs 1" in combined || "gs-1" in combined || "gs i" in combined || "general studies 1" in combined || "general studies i" in combined -> "GS Paper I"
        "gs 2" in combined || "gs-2" in combined || "gs ii" in combined || "general studies 2" in combined -> "GS Paper II"
        "gs 3" in combined || "gs-3" in combined || "gs iii" in combined || "general studies 3" in combined -> "GS Paper III"
        "gs 4" in combined || "gs-4" in combined || "gs iv" in combined || "ethics" in combined -> "GS Paper IV (Ethics)"
        "prelim" in combined || ("pre" in combined && "cse" in combined) -> "Prelims General"
        "mains" in combined || ("main" in combined && "cse" in combined) -> "Mains Core"
        "reasoning" in combined || "logic" in combined || "aptitude" in combined -> "Reasoning & Mental Ability"
        "history" in combined || "polity" in combined || "geography" in combined || "economy" in combined -> "Core Subject Paper"
        paper.subject.isNotBlank() && !paper.subject.equals("General", ignoreCase = true) -> paper.subject
        else -> "General / Mixed Sets"
    }
}

/**
 * Normalizes set numbers into structured 2-digit series (Set 01, Set 02, etc.).
 */
private fun getSetSeriesCategory(paper: PaperEntity): String {
    val raw = paper.setNumber.trim()
    return when {
        raw.isBlank() -> "Set 01 Series"
        raw.startsWith("Set", ignoreCase = true) -> raw.uppercase()
        raw.all { it.isDigit() } -> "Set ${raw.padStart(2, '0')}"
        else -> "Set ${raw.uppercase()}"
    }
}

/**
 * Step 5: Hierarchical Folder Assignment Tree Dialog
 */
@Composable
private fun AssignFolderTreeDialog(
    targetPaper: PaperEntity? = null,
    batchCount: Int = 1,
    folders: List<FolderItem>,
    onAssign: (folderId: String?, displayPath: String) -> Unit,
    onCreateNewFolder: (parent: FolderItem?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val rootFolders = remember(folders) { folders.filter { it.parentId == null } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (targetPaper != null) "Move to Folder" else "Move $batchCount Sets to Folder",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                Text(
                    text = if (targetPaper != null) "Select a folder for \"${targetPaper.title}\":" else "Select destination folder for $batchCount question sets:",
                    fontSize = 12.sp,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Option 1: General / No Folder
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAssign(null, "General") },
                            shape = RoundedCornerShape(8.dp),
                            color = if (targetPaper != null && targetPaper.folderId == null) colors.surfaceVariant else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("General (No Folder)", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.inkBold)
                            }
                        }
                    }

                    // Hierarchical Folder Tree
                    rootFolders.forEach { root ->
                        item {
                            FolderTreeItem(
                                folder = root,
                                depth = 0,
                                isSelected = targetPaper != null && targetPaper.folderId == root.id,
                                onSelect = { onAssign(root.id, root.name) },
                                onAddSub = { onCreateNewFolder(root) }
                            )
                        }

                        // Child sub-folders
                        val children = folders.filter { it.parentId == root.id }
                        children.forEach { child ->
                            item {
                                FolderTreeItem(
                                    folder = child,
                                    depth = 1,
                                    isSelected = targetPaper != null && targetPaper.folderId == child.id,
                                    onSelect = { onAssign(child.id, "${root.name} > ${child.name}") },
                                    onAddSub = { onCreateNewFolder(child) }
                                )
                            }

                            // Grandchildren
                            val grandChildren = folders.filter { it.parentId == child.id }
                            grandChildren.forEach { grand ->
                                item {
                                    FolderTreeItem(
                                        folder = grand,
                                        depth = 2,
                                        isSelected = targetPaper != null && targetPaper.folderId == grand.id,
                                        onSelect = { onAssign(grand.id, "${root.name} > ${child.name} > ${grand.name}") },
                                        onAddSub = { onCreateNewFolder(grand) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = { onCreateNewFolder(null) },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Create New Main Folder", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun FolderTreeItem(
    folder: FolderItem,
    depth: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onAddSub: () -> Unit
) {
    val colors = ExamTheme.colors
    val col = parseHexColor(folder.color)
    val indent = (depth * 18).dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clickable { onSelect() },
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) col.copy(alpha = 0.15f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (depth > 0) {
                Icon(
                    Icons.Default.SubdirectoryArrowRight,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Box(modifier = Modifier.size(9.dp).background(col, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = folder.name,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) col else colors.inkBold,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onAddSub,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add subfolder", tint = colors.inkMuted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun PaperCardItem(
    paper: PaperEntity,
    isActive: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    folderBreadcrumb: String? = null,
    onOpenMaster: () -> Unit,
    onTakeTest: () -> Unit,
    onAssignFolder: () -> Unit,
    onExport: () -> Unit = {},
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
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) colors.teal.copy(alpha = 0.06f) else colors.surface
        ),
        border = BorderStroke(
            if (isSelected) 1.8.dp else if (isActive) 1.5.dp else 0.8.dp,
            if (isSelected) colors.teal else if (isActive) Color(0xFF2563EB) else colors.border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelectionMode) Modifier.clickable { onToggleSelect() } else Modifier
            )
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
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.teal,
                                uncheckedColor = colors.inkMuted
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(0.5.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.clickable(enabled = !isSelectionMode) { onAssignFolder() }
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
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.DriveFileMove,
                                contentDescription = "Move",
                                tint = Color(0xFF2563EB).copy(alpha = 0.7f),
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(0.5.dp, colors.border)
                    ) {
                        Text(
                            text = "Set ${paper.setNumber.ifBlank { "01" }}",
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

                    if (!isSelectionMode) {
                        IconButton(
                            onClick = onExport,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share Paper",
                                tint = colors.teal,
                                modifier = Modifier.size(15.dp)
                            )
                        }

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
            }

            if (folderBreadcrumb != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = colors.teal.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, colors.teal.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SubdirectoryArrowRight,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = folderBreadcrumb,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.tealDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
