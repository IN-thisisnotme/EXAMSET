package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PaperEntity
import com.example.model.FolderItem
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme
import com.example.util.ExamNumberGenerator
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
    CURATED_SERIES("Test Series", Icons.Default.ViewCarousel),
    ALL("All Sets", Icons.Default.ViewList),
    BY_STAGE("By Stage", Icons.Default.Layers),
    BY_SET_SERIES("By Set Number", Icons.Default.ViewAgenda)
}

/**
 * Dedicated ed-tech / Testbook test series classifications.
 */
enum class ExamTestCategory(
    val label: String,
    val shortBadge: String,
    val icon: ImageVector,
    val primaryColor: Color,
    val lightColor: Color
) {
    ALL("All Categories", "ALL", Icons.Default.ViewList, Color(0xFF0284C7), Color(0xFFE0F2FE)),
    MOCK("Mock Tests (FLT)", "MOCK TEST", Icons.Default.Quiz, Color(0xFF2563EB), Color(0xFFEFF6FF)),
    PYQ("Previous Year Papers", "OFFICIAL PYQ", Icons.Default.History, Color(0xFFD97706), Color(0xFFFEF3C7)),
    SECTIONAL("Sectional Tests", "SECTIONAL", Icons.Default.Category, Color(0xFF059669), Color(0xFFD1FAE5)),
    TOPIC_DRILL("Topic & Speed Drills", "SPEED DRILL", Icons.Default.FlashOn, Color(0xFF7C3AED), Color(0xFFEDE9FE))
}

fun detectPaperTestCategory(paper: PaperEntity): ExamTestCategory {
    val text = "${paper.title} ${paper.topic} ${paper.subject} ${paper.setNumber}".lowercase(Locale.getDefault())
    return when {
        "pyq" in text || "previous year" in text || "official" in text || "2024" in text || "2023" in text || "2022" in text || "2021" in text || "2020" in text -> ExamTestCategory.PYQ
        "mock" in text || "flt" in text || "full length" in text || "model" in text || "all india" in text -> ExamTestCategory.MOCK
        "sectional" in text || "polity" in text || "history" in text || "economy" in text || "geography" in text || "environment" in text || "ecology" in text || "science" in text || "biology" in text -> ExamTestCategory.SECTIONAL
        "drill" in text || "speed" in text || "quiz" in text || "chapter" in text || "topic" in text -> ExamTestCategory.TOPIC_DRILL
        paper.questionCount >= 40 -> ExamTestCategory.MOCK
        else -> ExamTestCategory.SECTIONAL
    }
}

data class TestCategoryBuckets(
    val mocks: List<PaperEntity>,
    val pyqs: List<PaperEntity>,
    val sectionals: List<PaperEntity>,
    val drills: List<PaperEntity>
)

/**
 * Ordering options for child folders and categories (Step 8).
 */
enum class FolderSortOrder(val label: String) {
    DEFAULT("Default"),
    A_TO_Z("A-Z"),
    MOST_SETS("Most Sets")
}

/**
 * Examination folder navigation tabs inspired by reference design.
 */
enum class FolderExamTab(val title: String) {
    MOCKS("Mock Tests"),
    PYPS("PYPs"),
    STUDY_NOTES("Study Notes")
}

@Composable
fun FolderManagerTab(
    viewModel: MainViewModel,
    onNavigateToMaster: () -> Unit,
    onNavigateToStudent: () -> Unit,
    onCreateNewQuestionSet: (folderId: String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val savedPapers by viewModel.savedPapers.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val allAttempts by viewModel.allAttempts.collectAsState()
    val currentPaperEntityId by viewModel.currentPaperEntityId.collectAsState()
    val lastOpenedPaper by viewModel.lastOpenedPaper.collectAsState()
    val currentPaper by viewModel.currentPaper.collectAsState()

    // Preserved active folder location state
    val savedActiveFolderId by viewModel.lastActiveFolderId.collectAsState()
    var activeFolderId by remember { mutableStateOf(savedActiveFolderId) }

    var selectedFolderTab by remember { mutableStateOf(FolderExamTab.MOCKS) }
    var selectedPaperFilter by remember { mutableStateOf("All Papers") }
    var showPaperDropdown by remember { mutableStateOf(false) }

    val currentMonthStart = remember {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    val navigateToFolder: (String) -> Unit = remember {
        { targetId ->
            activeFolderId = targetId
            viewModel.setLastActiveFolderId(targetId)
        }
    }

    LaunchedEffect(savedActiveFolderId) {
        if (savedActiveFolderId != activeFolderId) {
            activeFolderId = savedActiveFolderId
        }
    }

    LaunchedEffect(folders, savedActiveFolderId) {
        if (savedActiveFolderId != "all" && folders.isNotEmpty() && folders.none { it.id == savedActiveFolderId }) {
            navigateToFolder("all")
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var segregationMode by remember { mutableStateOf(PaperSegregationMode.CURATED_SERIES) }
    var activeTestCategoryFilter by remember { mutableStateOf(ExamTestCategory.ALL) }
    var includeSubfolderPapers by remember { mutableStateOf(true) }

    // Selection & Batch Move states
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPaperIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var isDeepSearchAcrossAllFolders by remember { mutableStateOf(false) }
    var showFolderExportMenu by remember { mutableStateOf(false) }
    var showSelectionExportMenu by remember { mutableStateOf(false) }

    // Step 8: Folder Quick Management states (Rename, Recolor, Reparent, Sort)
    var folderToRename by remember { mutableStateOf<FolderItem?>(null) }
    var renameFolderName by remember { mutableStateOf("") }
    var folderToRecolor by remember { mutableStateOf<FolderItem?>(null) }
    var recolorFolderHex by remember { mutableStateOf("#0284C7") }
    var folderToReparent by remember { mutableStateOf<FolderItem?>(null) }
    var folderToDuplicate by remember { mutableStateOf<FolderItem?>(null) }
    var showActiveFolderMenu by remember { mutableStateOf(false) }
    var activeMenuFolderId by remember { mutableStateOf<String?>(null) }
    var folderSortOrder by remember { mutableStateOf(FolderSortOrder.DEFAULT) }
    var showQuickJumpDialog by remember { mutableStateOf(false) }

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

    // Folder navigation & dialog dismissal BackHandler (prevents app exit during folder operations)
    BackHandler(
        enabled = isSelectionMode ||
                paperToAssignFolder != null ||
                showBatchMoveDialog ||
                folderToRename != null ||
                folderToRecolor != null ||
                folderToReparent != null ||
                folderToDuplicate != null ||
                showNewFolderDialog ||
                showQuickJumpDialog ||
                activeFolderId != "all"
    ) {
        when {
            showBatchMoveDialog -> showBatchMoveDialog = false
            paperToAssignFolder != null -> paperToAssignFolder = null
            folderToRename != null -> folderToRename = null
            folderToRecolor != null -> folderToRecolor = null
            folderToReparent != null -> folderToReparent = null
            folderToDuplicate != null -> folderToDuplicate = null
            showNewFolderDialog -> showNewFolderDialog = false
            showQuickJumpDialog -> showQuickJumpDialog = false
            isSelectionMode -> {
                isSelectionMode = false
                selectedPaperIds = emptySet()
            }
            activeFolderId != "all" -> {
                val curr = folders.find { it.id == activeFolderId }
                navigateToFolder(curr?.parentId ?: "all")
            }
        }
    }

    // Current active folder object (if inside a folder)
    val currentFolder = remember(activeFolderId, folders) {
        if (activeFolderId == "all") null else folders.find { it.id == activeFolderId }
    }

    // Breadcrumb path from root down to active folder
    val breadcrumbPath = remember(activeFolderId, folders) {
        viewModel.getBreadcrumbPath(activeFolderId)
    }

    // Immediate child subfolders for current view
    val immediateSubfoldersRaw = remember(activeFolderId, folders) {
        if (activeFolderId == "all") {
            folders.filter { it.parentId == null }
        } else {
            folders.filter { it.parentId == activeFolderId }
        }
    }

    // Sorted subfolders according to selected FolderSortOrder
    val immediateSubfolders = remember(immediateSubfoldersRaw, folderSortOrder, savedPapers) {
        when (folderSortOrder) {
            FolderSortOrder.DEFAULT -> immediateSubfoldersRaw
            FolderSortOrder.A_TO_Z -> immediateSubfoldersRaw.sortedBy { it.name.lowercase(Locale.getDefault()) }
            FolderSortOrder.MOST_SETS -> immediateSubfoldersRaw.sortedByDescending { sub ->
                savedPapers.count { p ->
                    p.folderId == sub.id ||
                    p.subject.equals(sub.name, ignoreCase = true) ||
                    p.topic.contains(sub.name, ignoreCase = true)
                }
            }
        }
    }

    // Precomputed subfolder statistics to avoid re-calculating inside scroll loops
    val subFolderStats = remember(immediateSubfolders, folders, savedPapers) {
        immediateSubfolders.associate { sub ->
            val childCount = folders.count { it.parentId == sub.id }
            val directCount = savedPapers.count { p ->
                p.folderId == sub.id ||
                p.subject.equals(sub.name, ignoreCase = true) ||
                p.topic.contains(sub.name, ignoreCase = true)
            }
            val descIds = viewModel.getAllDescendantFolderIds(sub.id)
            val totalSubtree = savedPapers.count { it.folderId in descIds }
            sub.id to Triple(childCount, directCount, totalSubtree)
        }
    }

    // All descendant folder IDs for recursive matching (used for statistics and counting)
    val descendantIds = remember(activeFolderId, folders) {
        if (activeFolderId == "all") emptySet()
        else viewModel.getAllDescendantFolderIds(activeFolderId)
    }

    // Detected papers list for dynamic Paper-1 / Paper-2 selector
    val detectedPapersList = remember(activeFolderId, currentFolder, immediateSubfoldersRaw, savedPapers, descendantIds) {
        if (activeFolderId == "all" || currentFolder == null) {
            emptyList()
        } else {
            val allFolderIds = descendantIds + activeFolderId
            val subtreePapers = savedPapers.filter { it.folderId in allFolderIds }
            val combinedText = (currentFolder.name + " " +
                    immediateSubfoldersRaw.joinToString(" ") { it.name } + " " +
                    subtreePapers.joinToString(" ") { "${it.title} ${it.setNumber}" }).lowercase(Locale.getDefault())

            val hasPaper1 = "paper 1" in combinedText || "paper-1" in combinedText || "paper 01" in combinedText || "paper i" in combinedText || "tier 1" in combinedText || "tier-1" in combinedText || "tier i" in combinedText
            val hasPaper2 = "paper 2" in combinedText || "paper-2" in combinedText || "paper 02" in combinedText || "paper ii" in combinedText || "tier 2" in combinedText || "tier-2" in combinedText || "tier ii" in combinedText

            if (hasPaper1 || hasPaper2) {
                listOf("All Papers", "Paper - 1", "Paper - 2")
            } else {
                emptyList()
            }
        }
    }

    val rootFolders = remember(folders) {
        folders.filter { it.parentId == null }
    }

    val calculateFolderRecursiveSets: (String) -> Pair<Int, Int> = remember(folders, savedPapers, allAttempts) {
        { folderId ->
            val descIds = viewModel.getAllDescendantFolderIds(folderId) + folderId
            val distinctPapers = savedPapers.filter { it.folderId in descIds }.distinctBy { it.id }
            val total = distinctPapers.size
            val completed = distinctPapers.count { paper ->
                allAttempts.any { it.paperId == paper.id && it.isSubmitted }
            }
            Pair(total, completed)
        }
    }

    val calculateFolderMonthlyActivity: (String, Long) -> Int = remember(folders, savedPapers, allAttempts) {
        { folderId, monthStart ->
            val descIds = viewModel.getAllDescendantFolderIds(folderId) + folderId
            val paperIds = savedPapers.filter { it.folderId in descIds }.map { it.id }.toSet()
            allAttempts.count { it.paperId in paperIds && (it.submittedAt ?: it.createdAt) >= monthStart }
        }
    }

    // Subfolder tab matching: in PYPs show previous year subfolders, in Mocks show mock subfolders, in Notes show notes.
    // If no subfolders match the tab keyword, show all immediate subfolders so user never loses access.
    val visibleSubfolders = remember(immediateSubfolders, selectedFolderTab, activeFolderId, searchQuery) {
        if (activeFolderId == "all") {
            if (searchQuery.isBlank()) immediateSubfolders
            else immediateSubfolders.filter { it.name.contains(searchQuery, ignoreCase = true) }
        } else {
            val queryFiltered = if (searchQuery.isBlank()) immediateSubfolders
            else immediateSubfolders.filter { it.name.contains(searchQuery, ignoreCase = true) }

            val tabFiltered = queryFiltered.filter { sub ->
                val name = sub.name.lowercase(Locale.getDefault())
                when (selectedFolderTab) {
                    FolderExamTab.MOCKS -> "mock" in name || "full" in name || "subject" in name || "chapter" in name || "sectional" in name || "drill" in name || "topic" in name || ("test" in name && "pyp" !in name && "previous" !in name && "pyq" !in name)
                    FolderExamTab.PYPS -> "pyp" in name || "pyq" in name || "previous" in name || "official" in name || "year" in name || "202" in name || "201" in name || "similar" in name
                    FolderExamTab.STUDY_NOTES -> "note" in name || "study" in name || "material" in name || "doc" in name || "theory" in name
                }
            }
            if (tabFiltered.isNotEmpty()) tabFiltered else queryFiltered
        }
    }

    // Filtered papers matching unified search query, paper selector, and strict folder hierarchy
    val filteredPapers = remember(savedPapers, folders, activeFolderId, searchQuery, isDeepSearchAcrossAllFolders, selectedFolderTab, selectedPaperFilter) {
        savedPapers.filter { paper ->
            val folderPath = paper.folderId?.let { viewModel.getFullFolderPath(it) } ?: ""

            val matchesSearch = searchQuery.isBlank() ||
                    paper.title.contains(searchQuery, ignoreCase = true) ||
                    paper.subject.contains(searchQuery, ignoreCase = true) ||
                    paper.topic.contains(searchQuery, ignoreCase = true) ||
                    paper.setNumber.contains(searchQuery, ignoreCase = true) ||
                    folderPath.contains(searchQuery, ignoreCase = true)

            val matchesFolder = if (isDeepSearchAcrossAllFolders && searchQuery.isNotBlank()) {
                true
            } else if (activeFolderId == "all") {
                if (searchQuery.isNotBlank()) true
                else paper.folderId == null
            } else {
                // STRICT IMMEDIATE CONTAINMENT:
                // Only display test sets directly contained within this folder.
                // Do not display test sets stored inside nested subfolders.
                paper.folderId == activeFolderId
            }

            val matchesPaperSelector = if (selectedPaperFilter == "All Papers" || activeFolderId == "all") {
                true
            } else {
                val pText = "${paper.title} ${paper.subject} ${paper.topic} ${paper.setNumber}".lowercase(Locale.getDefault())
                when (selectedPaperFilter) {
                    "Paper - 1" -> "paper 1" in pText || "paper-1" in pText || "paper 01" in pText || "paper i" in pText || "tier 1" in pText || "tier-1" in pText || "tier i" in pText
                    "Paper - 2" -> "paper 2" in pText || "paper-2" in pText || "paper 02" in pText || "paper ii" in pText || "tier 2" in pText || "tier-2" in pText || "tier ii" in pText
                    else -> true
                }
            }

            val matchesTab = if (activeFolderId == "all" || searchQuery.isNotBlank()) {
                true
            } else {
                val cat = detectPaperTestCategory(paper)
                when (selectedFolderTab) {
                    FolderExamTab.MOCKS -> cat != ExamTestCategory.PYQ && paper.questionCount > 0
                    FolderExamTab.PYPS -> cat == ExamTestCategory.PYQ || "pyq" in paper.title.lowercase(Locale.getDefault()) || "previous" in paper.title.lowercase(Locale.getDefault())
                    FolderExamTab.STUDY_NOTES -> paper.questionCount == 0 || "notes" in paper.title.lowercase(Locale.getDefault()) || "study" in paper.title.lowercase(Locale.getDefault())
                }
            }

            matchesSearch && matchesFolder && matchesPaperSelector && matchesTab
        }
    }

    // Helper for natural numerical sorting of numbered sets (e.g. Set 1, Set 2, Set 10)
    val sortedFilteredPapers = remember(filteredPapers) {
        filteredPapers.sortedWith(
            compareBy<PaperEntity> { p ->
                val numInSet = Regex("\\d+").find(p.setNumber)?.value?.toIntOrNull()
                if (numInSet != null) numInSet
                else {
                    val numInTitle = Regex("(?:Set|Paper|Mock|Test|PYP|Part|#)\\s*(\\d+)", RegexOption.IGNORE_CASE).find(p.title)?.groupValues?.get(1)?.toIntOrNull()
                    numInTitle ?: Int.MAX_VALUE
                }
            }.thenByDescending { it.updatedAt }
        )
    }

    // Memoized Testbook-style test categorization for zero-lag horizontal and vertical views
    val categorizedPapers = remember(filteredPapers) {
        val mocks = mutableListOf<PaperEntity>()
        val pyqs = mutableListOf<PaperEntity>()
        val sectionals = mutableListOf<PaperEntity>()
        val drills = mutableListOf<PaperEntity>()
        for (p in filteredPapers) {
            when (detectPaperTestCategory(p)) {
                ExamTestCategory.PYQ -> pyqs.add(p)
                ExamTestCategory.MOCK -> mocks.add(p)
                ExamTestCategory.SECTIONAL -> sectionals.add(p)
                ExamTestCategory.TOPIC_DRILL -> drills.add(p)
                ExamTestCategory.ALL -> mocks.add(p)
            }
        }
        TestCategoryBuckets(mocks, pyqs, sectionals, drills)
    }

    val totalQuestionsCount = savedPapers.sumOf { it.questionCount }
    val totalKeysCount = savedPapers.sumOf { it.answerKeyCount }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Contextual Top Action Header (Root vs Inside Folder)
        item {
            if (activeFolderId == "all") {
                // Root Examination Library Header
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(0.8.dp, colors.border)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.teal.copy(alpha = 0.12f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.School,
                                            contentDescription = null,
                                            tint = colors.teal,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Examination Folders",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val rootCount = immediateSubfoldersRaw.size
                                    val totalUniquePapers = savedPapers.distinctBy { it.id }.size
                                    Text(
                                        text = "$rootCount Examination Series • $totalUniquePapers Test Sets",
                                        fontSize = 11.5.sp,
                                        color = colors.inkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showQuickJumpDialog = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = "Quick Jump Tree",
                                        tint = colors.teal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showFolderExportMenu = true },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Export Library",
                                            tint = colors.inkBold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showFolderExportMenu,
                                        onDismissRequest = { showFolderExportMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Backup All Sets (JSON)") },
                                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                            onClick = {
                                                showFolderExportMenu = false
                                                val json = viewModel.exportAllPapersAsJson()
                                                if (!json.isNullOrBlank()) {
                                                    triggerShare(json, "ExamSet Library Backup (${savedPapers.size} Sets)")
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Export All Sets (Text)") },
                                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                            onClick = {
                                                showFolderExportMenu = false
                                                val txt = viewModel.exportPapersAsFormattedText()
                                                if (!txt.isNullOrBlank()) {
                                                    triggerShare(txt, "ExamSet Library Summary (${savedPapers.size} Sets)")
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (savedPapers.isNotEmpty()) {
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
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 38.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelectionMode) Icons.Default.Check else Icons.Default.Checklist,
                                        contentDescription = "Select Sets",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isSelectionMode) "Done" else "Select Sets",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    parentFolderForNew = null
                                    newFolderName = ""
                                    showNewFolderDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primaryButtonBg,
                                    contentColor = colors.primaryButtonText
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 38.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.primaryButtonText)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "+ New Exam Folder",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = colors.primaryButtonText
                                )
                            }
                        }
                    }
                }
            } else {
                // Inside Examination Folder Header (Reference Screenshot Design)
                val totalRecursiveSets = remember(activeFolderId, savedPapers, descendantIds) {
                    val allFolderIds = descendantIds + activeFolderId
                    savedPapers.filter { it.folderId in allFolderIds }.distinctBy { it.id }.size
                }
                val completedRecursiveSets = remember(activeFolderId, savedPapers, descendantIds, allAttempts) {
                    val allFolderIds = descendantIds + activeFolderId
                    val uniquePapers = savedPapers.filter { it.folderId in allFolderIds }.distinctBy { it.id }
                    uniquePapers.count { p -> allAttempts.any { it.paperId == p.id && it.isSubmitted } }
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(0.8.dp, colors.border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        // Row 1: Back Navigation + Folder Name & Emblem Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = {
                                        val parent = currentFolder?.parentId ?: "all"
                                        navigateToFolder(parent)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = colors.inkBold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentFolder?.name ?: "Exam Folder",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        lineHeight = 22.sp
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = Color(0xFF2563EB),
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = "$totalRecursiveSets Total Tests",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                            )
                                        }

                                        if (completedRecursiveSets > 0) {
                                            Text(
                                                text = "$completedRecursiveSets Completed",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF16A34A)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Available in English, Hindi",
                                        fontSize = 11.5.sp,
                                        color = colors.inkMuted
                                    )
                                }
                            }

                            // Emblem Badge Box
                            val folderColor = currentFolder?.let { parseHexColor(it.color) } ?: colors.teal
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = folderColor.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, folderColor.copy(alpha = 0.35f)),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.School,
                                        contentDescription = null,
                                        tint = folderColor,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        // Dynamic Paper Selector (Paper 1 / Paper 2 dropdown)
                        if (detectedPapersList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(0.8.dp, colors.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showPaperDropdown = true }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ": $selectedPaperFilter",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Select Paper",
                                            tint = colors.inkBold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showPaperDropdown,
                                    onDismissRequest = { showPaperDropdown = false }
                                ) {
                                    detectedPapersList.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = option,
                                                    fontWeight = if (selectedPaperFilter == option) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedPaperFilter == option) Color(0xFF2563EB) else colors.inkBold
                                                )
                                            },
                                            onClick = {
                                                selectedPaperFilter = option
                                                showPaperDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Navigation Tabs: Mock Tests, PYPs, Study Notes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            FolderExamTab.values().forEach { tab ->
                                val isTabSelected = selectedFolderTab == tab
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedFolderTab = tab }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text(
                                        text = tab.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isTabSelected) colors.inkBold else colors.inkMuted
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (isTabSelected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.55f)
                                                .height(3.dp)
                                                .background(colors.inkBold, RoundedCornerShape(2.dp))
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(3.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Actions Row: Add Sub-Folder, Add Set, Folder Actions Menu
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    parentFolderForNew = currentFolder
                                    newFolderName = ""
                                    showNewFolderDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.surfaceVariant,
                                    contentColor = colors.inkBold
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.teal)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Sub-Folder", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                            }

                            Button(
                                onClick = { onCreateNewQuestionSet(activeFolderId) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primaryButtonBg,
                                    contentColor = colors.primaryButtonText
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.primaryButtonText)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ New Test Set", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.primaryButtonText)
                            }

                            // Folder More Actions
                            Box {
                                IconButton(
                                    onClick = { showActiveFolderMenu = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Folder Actions",
                                        tint = colors.inkBold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showActiveFolderMenu,
                                    onDismissRequest = { showActiveFolderMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename Folder") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showActiveFolderMenu = false
                                            folderToRename = currentFolder
                                            renameFolderName = currentFolder?.name.orEmpty()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Duplicate Folder") },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showActiveFolderMenu = false
                                            folderToDuplicate = currentFolder
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Change Color") },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(parseHexColor(currentFolder?.color ?: "#0284C7"), CircleShape)
                                            )
                                        },
                                        onClick = {
                                            showActiveFolderMenu = false
                                            folderToRecolor = currentFolder
                                            recolorFolderHex = currentFolder?.color ?: "#0284C7"
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move (Change Parent)") },
                                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showActiveFolderMenu = false
                                            folderToReparent = currentFolder
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Folder", color = Color(0xFFDC2626)) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showActiveFolderMenu = false
                                            folderToDelete = currentFolder
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeFolderId == "all") {
            renderRootExaminationLibrary(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                isSelectionMode = isSelectionMode,
                selectedPaperIds = selectedPaperIds,
                onToggleSelect = { pid ->
                    selectedPaperIds = if (pid in selectedPaperIds) selectedPaperIds - pid else selectedPaperIds + pid
                },
                savedPapers = savedPapers,
                rootFolders = rootFolders,
                folderSortOrder = folderSortOrder,
                onSortOrderToggle = {
                    folderSortOrder = when (folderSortOrder) {
                        FolderSortOrder.DEFAULT -> FolderSortOrder.A_TO_Z
                        FolderSortOrder.A_TO_Z -> FolderSortOrder.MOST_SETS
                        FolderSortOrder.MOST_SETS -> FolderSortOrder.DEFAULT
                    }
                },
                calculateFolderRecursiveSets = { calculateFolderRecursiveSets(it) },
                calculateFolderMonthlyActivity = { fid, ms -> calculateFolderMonthlyActivity(fid, ms) },
                currentMonthStart = currentMonthStart,
                onOpenFolder = { navigateToFolder(it) },
                onRenameFolder = { folder ->
                    folderToRename = folder
                    renameFolderName = folder.name
                },
                onDuplicateFolder = { folder ->
                    folderToDuplicate = folder
                },
                onRecolorFolder = { folder ->
                    folderToRecolor = folder
                    recolorFolderHex = folder.color
                },
                onDeleteFolder = { folder ->
                    folderToDelete = folder
                },
                onCreateNewFolder = {
                    parentFolderForNew = null
                    newFolderName = ""
                    showNewFolderDialog = true
                },
                onOpenMaster = { paper ->
                    viewModel.loadSavedPaper(paper)
                    onNavigateToMaster()
                },
                onTakeTest = { paper ->
                    viewModel.loadSavedPaper(paper) {
                        viewModel.startExam()
                    }
                    onNavigateToStudent()
                },
                onAssignFolder = { paper ->
                    paperToAssignFolder = paper
                },
                onExportPaper = { paper ->
                    val json = viewModel.exportSinglePaperAsJson(paper)
                    triggerShare(json, "${paper.title} - ${ExamNumberGenerator.formatSetDisplayLabel(paper.setNumber)}")
                },
                onDeletePaper = { paper ->
                    viewModel.deleteSavedPaper(paper)
                },
                allAttempts = allAttempts,
                currentPaperEntityId = currentPaperEntityId,
                onBatchExport = {
                    val json = viewModel.exportSelectedPapersAsJson(selectedPaperIds)
                    if (!json.isNullOrBlank()) {
                        triggerShare(json, "Exam Package (${selectedPaperIds.size} Sets)")
                    }
                },
                onBatchMove = { showBatchMoveDialog = true },
                onSelectAll = {
                    selectedPaperIds = if (selectedPaperIds.size == savedPapers.size) emptySet() else savedPapers.map { it.id }.toSet()
                }
            )
        } else {
            renderFolderDetailContents(
                breadcrumbPath = breadcrumbPath,
                onNavigateToFolder = { navigateToFolder(it) },
                onShowQuickJump = { showQuickJumpDialog = true },
                showQuickJumpDialog = showQuickJumpDialog,
                isSelectionMode = isSelectionMode,
                selectedPaperIds = selectedPaperIds,
                onToggleSelect = { pid ->
                    selectedPaperIds = if (pid in selectedPaperIds) selectedPaperIds - pid else selectedPaperIds + pid
                },
                visibleSubfolders = visibleSubfolders,
                calculateFolderRecursiveSets = { calculateFolderRecursiveSets(it) },
                sortedFilteredPapers = sortedFilteredPapers,
                currentPaperEntityId = currentPaperEntityId,
                allAttempts = allAttempts,
                onOpenMaster = { paper ->
                    viewModel.loadSavedPaper(paper)
                    onNavigateToMaster()
                },
                onTakeTest = { paper ->
                    viewModel.loadSavedPaper(paper) {
                        viewModel.startExam()
                    }
                    onNavigateToStudent()
                },
                onAssignFolder = { paper ->
                    paperToAssignFolder = paper
                },
                onExportPaper = { paper ->
                    val json = viewModel.exportSinglePaperAsJson(paper)
                    triggerShare(json, "${paper.title} - ${ExamNumberGenerator.formatSetDisplayLabel(paper.setNumber)}")
                },
                onDeletePaper = { paper ->
                    viewModel.deleteSavedPaper(paper)
                },
                onAddSubFolder = {
                    parentFolderForNew = currentFolder
                    newFolderName = ""
                    showNewFolderDialog = true
                },
                onCreateNewTestSet = {
                    onCreateNewQuestionSet(activeFolderId)
                },
                onBatchExport = {
                    val json = viewModel.exportSelectedPapersAsJson(selectedPaperIds)
                    if (!json.isNullOrBlank()) {
                        triggerShare(json, "Exam Package (${selectedPaperIds.size} Sets)")
                    }
                },
                onBatchMove = { showBatchMoveDialog = true },
                onSelectAll = {
                    selectedPaperIds = if (selectedPaperIds.size == sortedFilteredPapers.size) emptySet() else sortedFilteredPapers.map { it.id }.toSet()
                }
            )
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
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSubFolder) colors.tealLight else colors.blueLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSubFolder) Icons.Default.SubdirectoryArrowRight else Icons.Default.Folder,
                                contentDescription = null,
                                tint = if (isSubFolder) colors.teal else colors.navyBanner,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSubFolder) "Destination: Sub-Folder under ${parentFolderForNew?.name}" else "Destination: Top-Level Folder (Main Category)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSubFolder) colors.tealDark else colors.inkBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text(if (isSubFolder) "Sub-Folder Name" else "Folder Name") },
                        placeholder = { Text(if (isSubFolder) "e.g. GS Paper 1, CSAT or Prelims/CSAT" else "e.g. UPSC CSE, State PSC or UPSC/Prelims") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val delimiterPlaceholder = "\u0000LITERAL_SLASH\u0000"
                    val normalizedPreview = newFolderName.trim().replace("//", delimiterPlaceholder)
                    val hasHierarchy = normalizedPreview.contains("/") || normalizedPreview.contains(">")
                    val hasEscapedSlash = newFolderName.contains("//")
                    val previewSegments = normalizedPreview.split("/", ">")
                        .map { it.replace(delimiterPlaceholder, "/").trim() }
                        .filter { it.isNotBlank() }

                    if (hasHierarchy && previewSegments.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.blueLight,
                            border = BorderStroke(1.dp, colors.navyBanner.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SubdirectoryArrowRight,
                                        contentDescription = null,
                                        tint = colors.navyBanner,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Hierarchy Preview (${previewSegments.size} Levels)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.navyBanner
                                    )
                                }
                                val fullPreviewChain = if (isSubFolder && parentFolderForNew != null) {
                                    "${parentFolderForNew?.name} > " + previewSegments.joinToString(" > ")
                                } else {
                                    previewSegments.joinToString(" > ")
                                }
                                Text(
                                    text = fullPreviewChain,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkBold
                                )
                            }
                        }
                    } else if (hasEscapedSlash && !hasHierarchy && previewSegments.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.tealLight,
                            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.tealDark,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Literal slash: \"${previewSegments.first()}\"",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.tealDark
                                )
                            }
                        }
                    }

                    Text(
                        text = "Tip: Use '/' or '>' to create subfolders (e.g. Prelims/CSAT). Use '//' for literal '/' (e.g. Paper//1).",
                        fontSize = 10.sp,
                        color = colors.inkMuted,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
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
                            navigateToFolder(target.parentId ?: "all")
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

    // Step 8: Rename Folder Dialog
    if (folderToRename != null) {
        val target = folderToRename!!
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            icon = {
                Icon(Icons.Default.Edit, contentDescription = null, tint = colors.navyBanner)
            },
            title = {
                Text("Rename Folder", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Enter a new name for \"${target.name}\". Contained test sets will automatically update.",
                        fontSize = 12.5.sp,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = renameFolderName,
                        onValueChange = { renameFolderName = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameFolderName.isNotBlank()) {
                            viewModel.renameFolder(target.id, renameFolderName.trim())
                            folderToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.navyBanner)
                ) {
                    Text("Rename", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Step 8: Recolor Folder Dialog
    if (folderToRecolor != null) {
        val target = folderToRecolor!!
        val colorOptions = listOf(
            "#0284C7", "#16A34A", "#D97706", "#7C3AED", "#DC2626",
            "#0D9488", "#E11D48", "#2563EB", "#059669", "#475569"
        )
        AlertDialog(
            onDismissRequest = { folderToRecolor = null },
            title = {
                Text("Change Folder Color", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Choose a color badge for \"${target.name}\":",
                        fontSize = 12.5.sp,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        colorOptions.take(5).forEach { hex ->
                            val col = parseHexColor(hex)
                            val isSelected = recolorFolderHex.equals(hex, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { recolorFolderHex = hex },
                                shape = CircleShape,
                                color = col,
                                border = if (isSelected) BorderStroke(2.5.dp, colors.inkBold) else null
                            ) {
                                if (isSelected) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        colorOptions.drop(5).forEach { hex ->
                            val col = parseHexColor(hex)
                            val isSelected = recolorFolderHex.equals(hex, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { recolorFolderHex = hex },
                                shape = CircleShape,
                                color = col,
                                border = if (isSelected) BorderStroke(2.5.dp, colors.inkBold) else null
                            ) {
                                if (isSelected) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
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
                        viewModel.recolorFolder(target.id, recolorFolderHex)
                        folderToRecolor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.navyBanner)
                ) {
                    Text("Apply Color", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRecolor = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Step 8: Reparent Folder Dialog
    if (folderToReparent != null) {
        val target = folderToReparent!!
        val targetDescendants = remember(target, folders) {
            viewModel.getAllDescendantFolderIds(target.id)
        }
        ReparentFolderDialog(
            folderToReparent = target,
            folders = folders,
            descendantIds = targetDescendants,
            onReparent = { newParentId ->
                viewModel.reparentFolder(target.id, newParentId)
                folderToReparent = null
            },
            onDismiss = { folderToReparent = null }
        )
    }

    // Step 9: Duplicate Folder Dialog
    if (folderToDuplicate != null) {
        val target = folderToDuplicate!!
        val hasChildren = remember(target, folders) {
            folders.any { it.parentId == target.id }
        }
        DuplicateFolderDialog(
            folder = target,
            hasSubfolders = hasChildren,
            onDuplicate = { newName, includePapers, includeSubfolders ->
                viewModel.duplicateFolder(target.id, newName, includePapers, includeSubfolders)
                folderToDuplicate = null
            },
            onDismiss = { folderToDuplicate = null }
        )
    }

    // Step 10: Folder Hierarchy Quick Search & Quick-Jump Dialog
    if (showQuickJumpDialog) {
        FolderQuickJumpDialog(
            currentFolderId = activeFolderId,
            folders = folders,
            papers = savedPapers,
            viewModel = viewModel,
            onSelectFolder = { selectedId ->
                navigateToFolder(selectedId)
                showQuickJumpDialog = false
            },
            onDismiss = { showQuickJumpDialog = false }
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
    val clean = ExamNumberGenerator.formatSetDisplayLabel(paper.setNumber)
    return "$clean Series"
}

private data class FlattenedFolderNode(
    val folder: FolderItem,
    val depth: Int,
    val fullPath: String
)

private fun flattenFolderHierarchy(folders: List<FolderItem>): List<FlattenedFolderNode> {
    val result = mutableListOf<FlattenedFolderNode>()
    val visited = mutableSetOf<String>()
    fun traverse(parentId: String?, currentDepth: Int, currentPathPrefix: String) {
        val children = folders.filter { it.parentId == parentId }
        for (child in children) {
            if (!visited.add(child.id)) continue
            val path = if (currentPathPrefix.isEmpty()) child.name else "$currentPathPrefix > ${child.name}"
            result.add(FlattenedFolderNode(child, currentDepth, path))
            if (currentDepth < 25) {
                traverse(child.id, currentDepth + 1, path)
            }
        }
    }
    traverse(null, 0, "")
    // Any remaining folders not visited (e.g. cycle or orphaned parentId)
    for (f in folders) {
        if (visited.add(f.id)) {
            result.add(FlattenedFolderNode(f, 0, f.name))
        }
    }
    return result
}

/**
 * Step 5 & 6: Hierarchical Folder Assignment Tree Dialog
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
    val flattenedFolders = remember(folders) { flattenFolderHierarchy(folders) }

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

                    // Hierarchical Folder Tree for any arbitrary nesting depth
                    flattenedFolders.forEach { node ->
                        item(key = node.folder.id) {
                            FolderTreeItem(
                                folder = node.folder,
                                depth = node.depth,
                                isSelected = targetPaper != null && targetPaper.folderId == node.folder.id,
                                onSelect = { onAssign(node.folder.id, node.fullPath) },
                                onAddSub = { onCreateNewFolder(node.folder) }
                            )
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

/**
 * Step 8: Re-parenting Dialog to move folder in hierarchy without cycle loops
 */
@Composable
private fun ReparentFolderDialog(
    folderToReparent: FolderItem,
    folders: List<FolderItem>,
    descendantIds: Set<String>,
    onReparent: (targetParentId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    var selectedParentId by remember { mutableStateOf<String?>(folderToReparent.parentId) }

    // Exclude folder itself and all of its descendants from destinations
    val invalidIds = remember(folderToReparent, descendantIds) {
        descendantIds + folderToReparent.id
    }

    val candidates = remember(folders, invalidIds) {
        flattenFolderHierarchy(folders).filter { it.folder.id !in invalidIds }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Move Folder",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Text(
                    text = "Select a new parent folder for \"${folderToReparent.name}\":",
                    fontSize = 12.5.sp,
                    color = colors.inkMuted
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Option 1: Root / Top-Level
                    item {
                        val isSelected = selectedParentId == null
                        val isCurrentParent = folderToReparent.parentId == null
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedParentId = null },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) colors.navyBanner.copy(alpha = 0.12f) else colors.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSelected) BorderStroke(1.5.dp, colors.navyBanner) else BorderStroke(0.5.dp, colors.border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) colors.navyBanner else colors.inkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Root Level (Top Level / Main Exam)",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) colors.navyBanner else colors.inkBold
                                    )
                                    if (isCurrentParent) {
                                        Text(
                                            text = "Current Parent",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.tealDark
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = colors.navyBanner,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Candidate destination folders
                    candidates.forEach { node ->
                        item(key = node.folder.id) {
                            val isSelected = selectedParentId == node.folder.id
                            val isCurrentParent = folderToReparent.parentId == node.folder.id
                            val nodeColor = parseHexColor(node.folder.color)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedParentId = node.folder.id },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colors.navyBanner.copy(alpha = 0.12f) else colors.surface,
                                border = if (isSelected) BorderStroke(1.5.dp, colors.navyBanner) else BorderStroke(0.5.dp, colors.border.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = (10 + (node.depth * 14)).dp,
                                            end = 10.dp,
                                            top = 8.dp,
                                            bottom = 8.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (node.depth > 0) {
                                        Icon(
                                            imageVector = Icons.Default.SubdirectoryArrowRight,
                                            contentDescription = null,
                                            tint = colors.inkMuted,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(nodeColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = node.folder.name,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) colors.navyBanner else colors.inkBold
                                        )
                                        if (node.depth > 0) {
                                            Text(
                                                text = node.fullPath,
                                                fontSize = 9.5.sp,
                                                color = colors.inkMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (isCurrentParent) {
                                            Text(
                                                text = "Current Parent",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.tealDark
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = colors.navyBanner,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onReparent(selectedParentId) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.navyBanner)
            ) {
                Text("Move Here", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Step 9: Duplicate Folder Dialog
 * Enables cloning a folder branch with option to duplicate all contained question sets and subfolders.
 */
@Composable
private fun DuplicateFolderDialog(
    folder: FolderItem,
    hasSubfolders: Boolean,
    onDuplicate: (newName: String, includePapers: Boolean, includeSubfolders: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    var newName by remember { mutableStateOf("${folder.name} (Copy)") }
    var includePapers by remember { mutableStateOf(true) }
    var includeSubfolders by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Duplicate Folder", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Create a full copy of \"${folder.name}\":",
                    fontSize = 13.sp,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Cloned Folder Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.teal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Checkbox: Include question sets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includePapers = !includePapers }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includePapers,
                        onCheckedChange = { includePapers = it },
                        colors = CheckboxDefaults.colors(checkedColor = colors.teal)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Include all question sets", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.inkBold)
                        Text("Duplicates contained exam papers with new set bindings", fontSize = 11.sp, color = colors.inkMuted)
                    }
                }

                // Checkbox: Include nested subfolders (if any exist)
                if (hasSubfolders) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeSubfolders = !includeSubfolders }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeSubfolders,
                            onCheckedChange = { includeSubfolders = it },
                            colors = CheckboxDefaults.colors(checkedColor = colors.teal)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Include nested subfolders", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.inkBold)
                            Text("Recursively clones child hierarchy and branch structure", fontSize = 11.sp, color = colors.inkMuted)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onDuplicate(newName.trim(), includePapers, includeSubfolders)
                    }
                },
                enabled = newName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.navyBanner)
            ) {
                Text("Duplicate", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Step 10: Folder Hierarchy Quick Search & Quick-Jump Dialog
 * Enables 1-tap navigation directly to any nested folder across the entire hierarchy with instant live search and folder set counts.
 */
@Composable
private fun FolderQuickJumpDialog(
    currentFolderId: String,
    folders: List<FolderItem>,
    papers: List<PaperEntity>,
    viewModel: MainViewModel,
    onSelectFolder: (folderId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    val allNodes = remember(folders) { flattenFolderHierarchy(folders) }

    val filteredNodes = remember(searchQuery, allNodes) {
        if (searchQuery.isBlank()) {
            allNodes
        } else {
            val q = searchQuery.trim().lowercase()
            allNodes.filter { it.folder.name.lowercase().contains(q) || it.fullPath.lowercase().contains(q) }
        }
    }

    val totalLibraryPapers = remember(papers) { papers.size }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Library Quick-Jump", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Jump directly to any folder across the tree", fontSize = 11.sp, color = colors.inkMuted)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search folders or path...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border,
                        focusedLabelColor = colors.teal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Option 0: Root / All Sets
                    if (searchQuery.isBlank() || "all folders".contains(searchQuery.lowercase()) || "root".contains(searchQuery.lowercase())) {
                        item(key = "root-all") {
                            val isSelected = currentFolderId == "all"
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectFolder("all")
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colors.navyBanner.copy(alpha = 0.12f) else colors.surface,
                                border = if (isSelected) BorderStroke(1.2.dp, colors.navyBanner) else BorderStroke(0.5.dp, colors.border.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = colors.inkBold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("All Folders (Root)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                                        Text("Complete library overview", fontSize = 10.sp, color = colors.inkMuted)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.surfaceVariant
                                    ) {
                                        Text(
                                            text = "$totalLibraryPapers sets",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.inkMuted,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Nodes
                    filteredNodes.forEach { node ->
                        item(key = node.folder.id) {
                            val isSelected = currentFolderId == node.folder.id
                            val nodeColor = parseHexColor(node.folder.color)
                            val directSets = remember(node.folder.id, papers) {
                                papers.count { it.folderId == node.folder.id }
                            }
                            val descendantIds = remember(node.folder.id, folders) {
                                viewModel.getAllDescendantFolderIds(node.folder.id)
                            }
                            val totalSubtreeSets = remember(descendantIds, papers) {
                                papers.count { it.folderId in descendantIds }
                            }
                            val hasChildren = descendantIds.size > 1

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectFolder(node.folder.id)
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colors.navyBanner.copy(alpha = 0.12f) else colors.surface,
                                border = if (isSelected) BorderStroke(1.2.dp, colors.navyBanner) else BorderStroke(0.5.dp, colors.border.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = (8 + (node.depth * 14)).dp,
                                            end = 10.dp,
                                            top = 7.dp,
                                            bottom = 7.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (node.depth > 0) {
                                        Icon(
                                            imageVector = Icons.Default.SubdirectoryArrowRight,
                                            contentDescription = null,
                                            tint = colors.inkMuted,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(nodeColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = node.folder.name,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) colors.navyBanner else colors.inkBold
                                        )
                                        if (node.depth > 0) {
                                            Text(
                                                text = node.fullPath,
                                                fontSize = 9.sp,
                                                color = colors.inkMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) colors.navyBanner.copy(alpha = 0.15f) else colors.surfaceVariant
                                        ) {
                                            Text(
                                                text = if (hasChildren && totalSubtreeSets > directSets) "$directSets direct • $totalSubtreeSets total" else "$directSets sets",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) colors.navyBanner else colors.inkMuted,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active Folder",
                                                tint = colors.navyBanner,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
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
                            text = ExamNumberGenerator.formatSetDisplayLabel(paper.setNumber),
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

@Composable
private fun TestSeriesCarouselHeader(
    title: String,
    subtitle: String,
    count: Int,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = color.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "$count",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = color,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TestSeriesHorizontalCard(
    paper: PaperEntity,
    category: ExamTestCategory,
    isActive: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    folderBreadcrumb: String? = null,
    onToggleSelect: () -> Unit,
    onOpenMaster: () -> Unit,
    onTakeTest: () -> Unit,
    onAssignFolder: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) colors.tealLight.copy(alpha = 0.45f) else colors.surface
        ),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 0.8.dp,
            color = if (isActive) colors.teal else colors.border
        ),
        modifier = modifier
            .width(285.dp)
            .clickable {
                if (isSelectionMode) onToggleSelect() else onOpenMaster()
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Category Badge + Active Pill + Menu / Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = category.lightColor,
                        border = BorderStroke(0.5.dp, category.primaryColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = category.primaryColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.5.dp))
                            Text(
                                text = category.shortBadge,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = category.primaryColor
                            )
                        }
                    }
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.tealDark
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.teal,
                            uncheckedColor = colors.inkMuted
                        ),
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = colors.inkMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open Master Editor", fontSize = 12.5.sp) },
                                onClick = {
                                    showMenu = false
                                    onOpenMaster()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move / Change Folder", fontSize = 12.5.sp) },
                                onClick = {
                                    showMenu = false
                                    onAssignFolder()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export JSON / Share", fontSize = 12.5.sp) },
                                onClick = {
                                    showMenu = false
                                    onExport()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Test Set", fontSize = 12.5.sp, color = Color(0xFFEF4444)) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Test Title
            Text(
                text = paper.title.ifBlank { "Untitled Test Paper" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle / Subject / Folder Location
            val subtitle = paper.topic.ifBlank { paper.subject.ifBlank { "UPSC CSE GS Paper 1" } }
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!folderBreadcrumb.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = colors.inkMuted,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = folderBreadcrumb,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Spec & Verification Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surfaceVariant
                ) {
                    Text(
                        text = "${paper.questionCount} Qs",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surfaceVariant
                ) {
                    Text(
                        text = "${paper.durationMinutes} min",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surfaceVariant
                ) {
                    val computedMarks = (paper.questionCount * paper.positiveMarks).toInt()
                    Text(
                        text = "$computedMarks Marks",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Verification indicator
                val hasKeys = paper.answerKeyCount >= paper.questionCount && paper.questionCount > 0
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (hasKeys) Color(0xFFD1FAE5) else Color(0xFFFEF3C7)
                ) {
                    Text(
                        text = if (hasKeys) "✓ ${paper.answerKeyCount} Keys" else "${paper.answerKeyCount}/${paper.questionCount} Keys",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasKeys) Color(0xFF047857) else Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Take Test (Primary) + Master (Secondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTakeTest,
                    colors = ButtonDefaults.buttonColors(containerColor = category.primaryColor),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Take Test",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = onOpenMaster,
                    border = BorderStroke(0.8.dp, colors.border),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = colors.ink,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Master",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink
                    )
                }
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
