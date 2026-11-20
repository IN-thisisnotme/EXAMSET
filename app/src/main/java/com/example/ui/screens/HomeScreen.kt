package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import com.example.data.AttemptEntity
import com.example.data.PaperEntity
import com.example.data.toQuestionPaper
import com.example.util.McqParser
import com.example.util.ExamNumberGenerator
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.EditMetadataDialog
import com.example.ui.components.InputBottomSheet
import com.example.ui.components.OmrScannerDialog
import com.example.ui.components.PrintConfigDialog
import com.example.ui.components.PsychometricAnalysisDialog
import com.example.data.AppFontScaleMode
import com.example.ui.components.FontSizeSelectorDialog
import com.example.ui.components.SavedPapersDialog
import com.example.ui.components.StudentHistoricalAnalysisDialog
import com.example.ui.components.SubjectFolderDialog
import com.example.ui.theme.ExamTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = ExamTheme.colors

    val currentPaper by viewModel.currentPaper.collectAsState()
    val rawInputText by viewModel.rawInputText.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val studentAnswers by viewModel.studentAnswers.collectAsState()
    val isExamSubmitted by viewModel.isExamSubmitted.collectAsState()
    val examConfig by viewModel.examConfig.collectAsState()
    val isExamStarted by viewModel.isExamStarted.collectAsState()
    val savedPapers by viewModel.savedPapers.collectAsState()
    val allAttempts by viewModel.allAttempts.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isCardBoxed by viewModel.isCardBoxed.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val lastActiveFolderId by viewModel.lastActiveFolderId.collectAsState()
    val todayTestSeconds by viewModel.todayTestSeconds.collectAsState()
    val fontScaleMode by viewModel.fontScaleMode.collectAsState()
    val studyNotes by viewModel.studyNotes.collectAsState()
    val promptPresets by viewModel.promptPresets.collectAsState()
    val flashcardSrsByNote by viewModel.flashcardSrsByNote.collectAsState()
    val knowledgeEditorDraft by viewModel.knowledgeEditorDraft.collectAsState()
    val activeKnowledgeReaderNoteId by viewModel.activeKnowledgeReaderNoteId.collectAsState()
    val isEditMetadataOverlayOpen by viewModel.isEditMetadataOverlayOpen.collectAsState()
    val editMetadataEnterQuestionEditAction by viewModel.editMetadataEnterQuestionEditAction.collectAsState()
    val isAddQuestionOverlayOpen by viewModel.isAddQuestionOverlayOpen.collectAsState()

    val isFullScreenTestActive = selectedTab == 2 && isExamStarted && !isExamSubmitted && currentPaper != null

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val masterListState = rememberLazyListState()

    var showInputSheet by remember { mutableStateOf(false) }
    var creationTargetFolderId by remember { mutableStateOf<String?>(null) }
    var pendingFileTargetFolderId by remember { mutableStateOf<String?>(null) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var showOmrScannerDialog by remember { mutableStateOf(false) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showHistoricalAnalyticsDialog by remember { mutableStateOf(false) }
    var showPsychometricDialog by remember { mutableStateOf(false) }
    var showTopBarOverflowMenu by remember { mutableStateOf(false) }
    var selectedSubjectFolder by remember { mutableStateOf<String?>(null) }
    var showConfirmExitDialog by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableStateOf(0L) }

    // Accidental Back Navigation Prevention
    BackHandler {
        when {
            drawerState.isOpen -> {
                coroutineScope.launch { drawerState.close() }
            }
            showPsychometricDialog -> {
                showPsychometricDialog = false
            }
            showHistoricalAnalyticsDialog -> {
                showHistoricalAnalyticsDialog = false
            }
            showConfirmExitDialog -> {
                showConfirmExitDialog = false
            }
            selectedSubjectFolder != null -> {
                selectedSubjectFolder = null
            }
            showInputSheet -> {
                showInputSheet = false
                creationTargetFolderId = null
            }
            showPrintDialog -> {
                showPrintDialog = false
            }
            showOmrScannerDialog -> {
                showOmrScannerDialog = false
            }
            showSavedDialog -> {
                showSavedDialog = false
            }
            showFontSizeDialog -> {
                showFontSizeDialog = false
            }
            isAddQuestionOverlayOpen -> {
                viewModel.closeAddQuestionOverlay()
            }
            isEditMetadataOverlayOpen -> {
                viewModel.closeEditMetadataOverlay()
            }
            selectedTab in 3..6 -> {
                viewModel.selectTab(0)
            }
            currentPaper != null && !isFullScreenTestActive -> {
                showConfirmExitDialog = true
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(context, "Press back again to exit ExamSet", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val targetFolder = pendingFileTargetFolderId
        pendingFileTargetFolderId = null
        if (uri != null) {
            viewModel.loadFromUri(uri, customFolderId = targetFolder)
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isFullScreenTestActive && !showPsychometricDialog && !showInputSheet && knowledgeEditorDraft == null && activeKnowledgeReaderNoteId == null && !isEditMetadataOverlayOpen && !isAddQuestionOverlayOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.surface,
                drawerContentColor = colors.inkBold,
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp)
                ) {
                    // Drawer Branded Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = colors.teal
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ExamSet Studio",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "${savedPapers.size} Sets • ${folders.size} Folders",
                                fontSize = 11.5.sp,
                                color = colors.inkMuted
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.border)

                    Text(
                        text = "PREFERENCES & SCAN",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )

                    val drawerItemColors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = colors.tealLight,
                        selectedTextColor = colors.inkBold,
                        unselectedTextColor = colors.inkBold,
                        selectedIconColor = colors.teal,
                        unselectedIconColor = colors.inkBold
                    )

                    NavigationDrawerItem(
                        label = { Text("Eye-Safe Dark Mode", fontWeight = FontWeight.Medium) },
                        icon = {
                            Icon(
                                imageVector = if (colors.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = if (colors.isDark) colors.teal else colors.inkBold
                            )
                        },
                        badge = {
                            Switch(
                                checked = colors.isDark,
                                onCheckedChange = { viewModel.toggleThemeMode() },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.teal)
                            )
                        },
                        selected = false,
                        onClick = { viewModel.toggleThemeMode() },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_dark_mode")
                    )

                    NavigationDrawerItem(
                        label = { Text("Typography & Font Scale", fontWeight = FontWeight.Medium) },
                        icon = { Icon(Icons.Default.FormatSize, contentDescription = null, tint = colors.teal) },
                        badge = {
                            Text(
                                text = fontScaleMode.title.substringBefore(" "),
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                        },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showFontSizeDialog = true
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_font_scale")
                    )

                    NavigationDrawerItem(
                        label = { Text("Scan Physical OMR", fontWeight = FontWeight.Medium) },
                        icon = {
                            Icon(
                                Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = if (colors.isDark) Color(0xFFF472B6) else Color(0xFFBE185D)
                            )
                        },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showOmrScannerDialog = true
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_omr_scanner")
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.border)

                    Text(
                        text = "STUDIO & TOOLS",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )

                    NavigationDrawerItem(
                        label = { Text("AI Prompt Studio", fontWeight = FontWeight.Medium) },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = colors.teal) },
                        selected = selectedTab == 5,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.selectTab(5)
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_ai_prompt")
                    )

                    NavigationDrawerItem(
                        label = { Text("Exam Creator Tools", fontWeight = FontWeight.Medium) },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = colors.inkBold) },
                        selected = selectedTab == 4,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.selectTab(4)
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_creator_tools")
                    )

                    NavigationDrawerItem(
                        label = { Text("Print / PDF / Offline Web", fontWeight = FontWeight.Medium) },
                        icon = { Icon(Icons.Default.Print, contentDescription = null, tint = colors.inkBold) },
                        selected = selectedTab == 3,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.selectTab(3)
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_print_pdf")
                    )

                    NavigationDrawerItem(
                        label = { Text("Share & Backup", fontWeight = FontWeight.Medium) },
                        icon = { Icon(Icons.Default.Share, contentDescription = null, tint = colors.inkBold) },
                        selected = selectedTab == 6,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            viewModel.selectTab(6)
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_share_backup")
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.border)

                    Text(
                        text = "ANALYTICS",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                    )

                    NavigationDrawerItem(
                        label = { Text("Today Study Tracker & History", fontWeight = FontWeight.Medium) },
                        icon = { Icon(Icons.Default.Timer, contentDescription = null, tint = colors.teal) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showHistoricalAnalyticsDialog = true
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_study_tracker")
                    )

                    NavigationDrawerItem(
                        label = { Text("Psychometric CTT", fontWeight = FontWeight.Medium) },
                        icon = {
                            Icon(
                                Icons.Default.Assessment,
                                contentDescription = null,
                                tint = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            )
                        },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showPsychometricDialog = true
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_psychometrics")
                    )
                }
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideLandscape = maxWidth >= 600.dp && maxWidth > maxHeight
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                    Snackbar(
                        snackbarData = snackbarData,
                        containerColor = if (colors.isDark) colors.surfaceElevated else colors.navyBanner,
                        contentColor = if (colors.isDark) colors.inkBold else colors.navyBannerText,
                        actionColor = colors.teal,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            },
            topBar = {
                if (!isFullScreenTestActive) {
                    Surface(
                        color = colors.surface,
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .heightIn(min = if (isWideLandscape) 48.dp else 56.dp)
                                    .padding(horizontal = 4.dp, vertical = if (isWideLandscape) 2.dp else 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { coroutineScope.launch { drawerState.open() } },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("drawer_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Open Studio Menu",
                                        tint = colors.inkBold
                                    )
                                }
                                if (currentPaper != null) {
                                    IconButton(
                                        onClick = { showConfirmExitDialog = true },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .testTag("exit_paper_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Return to Subject Overview",
                                            tint = colors.inkBold
                                        )
                                    }
                                }

                                val activeFolderName = remember(lastActiveFolderId, folders) {
                                    if (lastActiveFolderId == "all") null
                                    else folders.find { it.id == lastActiveFolderId }?.name
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = "ExamSet",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 20.sp
                                    )
                                    val subtitle = when {
                                        currentPaper != null -> "${ExamNumberGenerator.formatSetDisplayLabel(currentPaper!!.metadata.setNumber)} • ${currentPaper!!.metadata.subject}"
                                        !activeFolderName.isNullOrBlank() -> activeFolderName
                                        else -> "Exam Studio"
                                    }
                                    Text(
                                        text = subtitle,
                                        fontSize = 11.5.sp,
                                        color = if (currentPaper != null || !activeFolderName.isNullOrBlank()) colors.tealDark else colors.muted,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        softWrap = true,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 14.sp
                                    )
                                }

                                // Today's Test Study Timer & Historical Analytics Trigger
                                val isExamRunning = isExamStarted && !isExamSubmitted
                                Surface(
                                    onClick = { showHistoricalAnalyticsDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isExamRunning) colors.tealLight else colors.surfaceVariant,
                                    border = BorderStroke(0.8.dp, if (isExamRunning) colors.teal else colors.border),
                                    modifier = Modifier
                                        .padding(end = 2.dp)
                                        .testTag("topbar_today_analytics_chip")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = "Today Study Timer & Historical Analysis",
                                            tint = if (isExamRunning) colors.teal else colors.inkBold,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val tH = todayTestSeconds / 3600
                                        val tM = (todayTestSeconds % 3600) / 60
                                        val tS = todayTestSeconds % 60
                                        val formattedTime = if (tH > 0) {
                                            String.format(java.util.Locale.US, "%02d:%02d:%02d", tH, tM, tS)
                                        } else {
                                            String.format(java.util.Locale.US, "%02d:%02d", tM, tS)
                                        }
                                        Text(
                                            text = formattedTime,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isExamRunning) colors.tealDark else colors.inkBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                // Quick Search / Saved Papers Modal Trigger
                                IconButton(
                                    onClick = { showSavedDialog = true },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("topbar_search_papers_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search & Open Saved Question Sets",
                                        tint = colors.inkBold
                                    )
                                }

                                if (currentPaper != null) {
                                    Box {
                                        IconButton(
                                            onClick = { showTopBarOverflowMenu = true },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .testTag("topbar_overflow_menu_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Paper options",
                                                tint = colors.inkBold
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showTopBarOverflowMenu,
                                            onDismissRequest = { showTopBarOverflowMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit Paper Details", fontSize = 13.sp) },
                                                onClick = {
                                                    showTopBarOverflowMenu = false
                                                    viewModel.openEditMetadataOverlay()
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Edit, contentDescription = null, tint = colors.teal, modifier = Modifier.size(18.dp))
                                                }
                                            )
                                            val activePaper = currentPaper
                                            if (activePaper != null) {
                                                DropdownMenuItem(
                                                    text = { Text("Share Question Set", fontSize = 13.sp) },
                                                    onClick = {
                                                        showTopBarOverflowMenu = false
                                                        val safeName = activePaper.metadata.title
                                                            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                                                            .take(36)
                                                            .ifBlank { "examset" }
                                                        viewModel.shareTextAsFile(
                                                            context = context,
                                                            content = activePaper.rawText,
                                                            fileName = "${safeName}.txt",
                                                            title = activePaper.metadata.title,
                                                            mimeType = "text/plain"
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Share, contentDescription = null, tint = colors.inkBold, modifier = Modifier.size(18.dp))
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.6.dp)
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!isFullScreenTestActive && selectedTab == 0) {
                    FloatingActionButton(
                        onClick = {
                            creationTargetFolderId = lastActiveFolderId.takeIf { id ->
                                id != "all" && folders.any { it.id == id }
                            }
                            showInputSheet = true
                        },
                        containerColor = colors.navyBanner,
                        contentColor = colors.navyBannerText,
                        shape = CircleShape,
                        modifier = Modifier.testTag("add_paste_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add or Paste MCQ",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            bottomBar = {
                if (!isFullScreenTestActive && !isWideLandscape) {
                    NavigationBar(
                        containerColor = colors.surface,
                        contentColor = colors.inkBold,
                        tonalElevation = 8.dp
                    ) {
                        val navItems = listOf(
                            Triple(0, "Exams", Icons.Default.FolderOpen),
                            Triple(1, "Master", Icons.AutoMirrored.Filled.Assignment),
                            Triple(2, "Test", Icons.Default.School)
                        )

                        navItems.forEach { (index, label, icon) ->
                            val isSelected = selectedTab == index
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (selectedTab == index && index == 1) {
                                        coroutineScope.launch {
                                            masterListState.animateScrollToItem(0)
                                        }
                                    } else {
                                        viewModel.selectTab(index)
                                    }
                                },
                                alwaysShowLabel = true,
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (index == 1 && currentPaper != null && currentPaper!!.questions.isNotEmpty()) {
                                                Badge(containerColor = colors.teal) {
                                                    Text("${currentPaper!!.questions.size}")
                                                }
                                            } else if (index == 0 && savedPapers.isNotEmpty()) {
                                                Badge(containerColor = colors.surfaceVariant) {
                                                    Text("${savedPapers.size}", color = colors.inkBold)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.teal,
                                    selectedTextColor = colors.teal,
                                    unselectedIconColor = colors.muted,
                                    unselectedTextColor = colors.muted,
                                    indicatorColor = colors.tealLight
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            val railNavItems = listOf(
                Triple(0, "Exams", Icons.Default.FolderOpen),
                Triple(1, "Master", Icons.AutoMirrored.Filled.Assignment),
                Triple(2, "Test", Icons.Default.School)
            )
            val mainTabColumn: @Composable (Modifier) -> Unit = { columnModifier ->
            Column(
                modifier = columnModifier
            ) {
                // Creator Tools Sub-Navigation Pills (when opened via Drawer)
                if (!isFullScreenTestActive && selectedTab in 4..6) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val creatorSubTabs = listOf(
                            Triple(4, "Exam Tools", Icons.Default.Tune),
                            Triple(5, "AI Prompt", Icons.Default.AutoAwesome),
                            Triple(6, "Share & Export", Icons.Default.Share)
                        )
                        creatorSubTabs.forEach { (tabIdx, label, icon) ->
                            val isSubSelected = selectedTab == tabIdx
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 48.dp)
                                    .clickable { viewModel.selectTab(tabIdx) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSubSelected) colors.tealLight else colors.surfaceVariant,
                                border = BorderStroke(1.dp, if (isSubSelected) colors.teal else colors.border)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSubSelected) colors.teal else colors.muted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSubSelected) colors.teal else colors.inkBold,
                                        maxLines = 2,
                                        softWrap = true,
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Stats & Chips Row (hidden in active full-screen test mode and post-submission Result Hub)
                if (!isFullScreenTestActive && !(selectedTab == 2 && isExamSubmitted)) {
                    currentPaper?.let { paper ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = colors.navyBanner
                            ) {
                                Text(
                                    text = ExamNumberGenerator.formatSetDisplayLabel(paper.metadata.setNumber).uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.navyBannerText,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = colors.tealLight,
                                border = BorderStroke(0.6.dp, colors.border)
                            ) {
                                Text(
                                    text = "${paper.questions.size} Questions",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (paper.quickAnswersCount > 0) colors.greenLight else colors.amberLight,
                                border = BorderStroke(0.6.dp, colors.border)
                            ) {
                                Text(
                                    text = "${paper.quickAnswersCount} Answer Keys",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (paper.quickAnswersCount > 0) colors.green else colors.amber,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    maxLines = 1
                                )
                            }

                            val expCount = paper.questions.count { !it.explanation.isNullOrBlank() }
                            if (expCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = colors.tealLight.copy(alpha = 0.5f),
                                    border = BorderStroke(0.6.dp, colors.border)
                                ) {
                                    Text(
                                        text = "$expCount Explanations",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.tealDark,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Main Tab Body
                val paper = currentPaper
                when (selectedTab) {
                    0 -> FolderManagerTab(
                        viewModel = viewModel,
                        onNavigateToMaster = { viewModel.selectTab(1) },
                        onNavigateToStudent = { viewModel.selectTab(2) },
                        onNavigateToPrint = { viewModel.selectTab(3) },
                        onCreateNewQuestionSet = { folderId ->
                            creationTargetFolderId = folderId
                            showInputSheet = true
                        }
                    )
                    1 -> MasterViewTab(
                        viewModel = viewModel,
                        listState = masterListState
                    )
                    2 -> if (paper != null && paper.questions.isNotEmpty()) {
                        StudentTestTab(
                            paper = paper,
                            answers = studentAnswers,
                            config = examConfig,
                            submittedStats = viewModel.submittedStats.collectAsState().value,
                            submittedAnswers = viewModel.submittedAnswers.collectAsState().value,
                            incorrectQuestionLog = viewModel.incorrectQuestionLog.collectAsState().value,
                            unattendedQuestionLog = viewModel.unattendedQuestionLog.collectAsState().value,
                            isExamStarted = isExamStarted,
                            isSubmitted = isExamSubmitted,
                            retryQuestionNumbers = viewModel.retryQuestionNumbers.collectAsState().value,
                            timeSpentSeconds = viewModel.timeSpentSeconds.collectAsState().value,
                            timeRemainingSeconds = viewModel.timeRemainingSeconds.collectAsState().value,
                            timerMode = viewModel.timerMode.collectAsState().value,
                            customTimeLimitMinutes = viewModel.customTimeLimitMinutes.collectAsState().value,
                            attempts = viewModel.attempts.collectAsState().value,
                            todayTestSeconds = todayTestSeconds,
                            todayAnalytics = viewModel.todayStudyAnalytics.collectAsState().value,
                            historicalRecords = viewModel.allDailyAnalytics.collectAsState().value,
                            dailyGoalMinutes = viewModel.dailyGoalMinutes.collectAsState().value,
                            onSetDailyGoalMinutes = { viewModel.setDailyGoalMinutes(it) },
                            onSetTimerMode = { viewModel.setTimerMode(it) },
                            onSetCustomTimeLimit = { viewModel.setCustomTimeLimitMinutes(it) },
                            onStartExam = { qNums, mins -> viewModel.startExam(qNums, mins) },
                            onResumeExam = { viewModel.resumeExam() },
                            onExitToOverview = { viewModel.exitToExamOverview() },
                            onSelectAnswer = { qNum, opt -> viewModel.selectAnswer(qNum, opt) },
                            onSubmitExam = { viewModel.submitExam() },
                            onResetExam = { viewModel.resetStudentExam() },
                            onLoadAttempt = { viewModel.loadOldAttempt(it) },
                            shuffleOptions = viewModel.shuffleOptions.collectAsState().value,
                            sessionSeed = viewModel.examSessionSeed.collectAsState().value,
                            onToggleShuffleOptions = { viewModel.setShuffleOptions(it) },
                            onScanOmr = { showOmrScannerDialog = true },
                            onOpenPsychometrics = { showPsychometricDialog = true },
                            onSaveWeakAreasToKnowledgeSet = { p, inc, unatt, ans ->
                                viewModel.createWeakAreaKnowledgeSetFromTest(p, inc, unatt, ans)
                            }
                        )
                } else {
                    val lastPaperForPlaceholder = viewModel.lastOpenedPaper.collectAsState().value
                    NoPaperPlaceholder(
                        title = "Student Test Series",
                        onBrowseFolders = { viewModel.selectTab(0) },
                        onGoToCreator = { viewModel.selectTab(4) },
                        lastOpenedPaper = lastPaperForPlaceholder,
                        onOpenLastPaper = { viewModel.loadSavedPaper(it) }
                    )
                }
                3 -> if (paper != null && paper.questions.isNotEmpty()) {
                    PrintablePaperTab(
                        paper = paper,
                        config = examConfig,
                        onOpenConfig = { showPrintDialog = true }
                    )
                } else {
                    val lastPaperForPlaceholder = viewModel.lastOpenedPaper.collectAsState().value
                    NoPaperPlaceholder(
                        title = "Print & OMR Generator",
                        onBrowseFolders = { viewModel.selectTab(0) },
                        onGoToCreator = { viewModel.selectTab(4) },
                        lastOpenedPaper = lastPaperForPlaceholder,
                        onOpenLastPaper = { viewModel.loadSavedPaper(it) }
                    )
                }
                4 -> CreatorTab(
                    viewModel = viewModel,
                    onNavigateToMaster = { viewModel.selectTab(1) },
                    onNavigateToTests = { viewModel.selectTab(2) },
                    onNavigateToPrint = { viewModel.selectTab(3) },
                    onEditMetadata = { viewModel.openEditMetadataOverlay() },
                    onScanOmr = { showOmrScannerDialog = true }
                )
                5 -> PromptGeneratorTab(
                    viewModel = viewModel,
                    onNavigateToCreator = { viewModel.selectTab(4) },
                    onOpenKnowledgeStudio = { noteTitle, noteSubject, collectionType, rule ->
                        viewModel.openKnowledgeEditor(
                            prefillTitle = noteTitle,
                            defaultSubject = noteSubject,
                            defaultCollectionType = collectionType,
                            defaultFormatRule = rule
                        )
                    },
                    onOpenVocabularyHub = { lang ->
                        viewModel.openVocabularyHub(lang)
                    }
                )
                6 -> ShareSystemTab(
                    viewModel = viewModel,
                    onNavigateToMaster = { viewModel.selectTab(1) },
                    onOpenPsychometrics = { showPsychometricDialog = true }
                )
            }
            }
            }
            if (!isFullScreenTestActive && isWideLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = colors.surface,
                        contentColor = colors.inkBold
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        railNavItems.forEach { (index, label, icon) ->
                            val isSelected = selectedTab == index
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = {
                                    if (selectedTab == index && index == 1) {
                                        coroutineScope.launch {
                                            masterListState.animateScrollToItem(0)
                                        }
                                    } else {
                                        viewModel.selectTab(index)
                                    }
                                },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (index == 1 && currentPaper != null && currentPaper!!.questions.isNotEmpty()) {
                                                Badge(containerColor = colors.teal) {
                                                    Text("${currentPaper!!.questions.size}")
                                                }
                                            } else if (index == 0 && savedPapers.isNotEmpty()) {
                                                Badge(containerColor = colors.surfaceVariant) {
                                                    Text("${savedPapers.size}", color = colors.inkBold)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = colors.teal,
                                    selectedTextColor = colors.teal,
                                    unselectedIconColor = colors.muted,
                                    unselectedTextColor = colors.muted,
                                    indicatorColor = colors.tealLight
                                )
                            )
                        }
                    }
                    VerticalDivider(color = colors.border, thickness = 0.8.dp)
                    mainTabColumn(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                mainTabColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
        }
    }

    // Subject Folder Dialog (opens when user taps any subject folder on dashboard)
    selectedSubjectFolder?.let { subjectName ->
        val allAttempts = viewModel.allAttempts.collectAsState().value
        SubjectFolderDialog(
            subjectName = subjectName,
            papers = savedPapers,
            allAttempts = allAttempts,
            onSelectPaper = { paper ->
                viewModel.loadSavedPaper(paper)
                viewModel.selectTab(1) // Master view
                selectedSubjectFolder = null
            },
            onStartTest = { paper ->
                val paperAttempts = allAttempts.filter { it.paperId == paper.id }
                val isCompleted = paperAttempts.any { it.isSubmitted }
                val isInProgress = !isCompleted && paperAttempts.any { !it.isSubmitted }
                when {
                    isCompleted -> viewModel.reviewCompletedPaperAttempt(paper)
                    isInProgress -> viewModel.loadSavedPaper(paper) {
                        viewModel.resumeExam()
                    }
                    else -> viewModel.loadSavedPaper(paper) {
                        viewModel.startExam()
                    }
                }
                viewModel.selectTab(2) // Student Test tab directly
                selectedSubjectFolder = null
            },
            onDeletePaper = { paper ->
                viewModel.deleteSavedPaper(paper)
            },
            onDismiss = { selectedSubjectFolder = null }
        )
    }

    // Historical Analytics Dialog (available from topbar chip or test views)
    if (showHistoricalAnalyticsDialog) {
        StudentHistoricalAnalysisDialog(
            todayTestSeconds = viewModel.todayTestSeconds.collectAsState().value,
            todayAnalytics = viewModel.todayStudyAnalytics.collectAsState().value,
            historicalRecords = viewModel.allDailyAnalytics.collectAsState().value,
            dailyGoalMinutes = viewModel.dailyGoalMinutes.collectAsState().value,
            isTestCurrentlyRunning = viewModel.isExamStarted.collectAsState().value && !isExamSubmitted,
            onSetDailyGoalMinutes = { viewModel.setDailyGoalMinutes(it) },
            onDismiss = { showHistoricalAnalyticsDialog = false }
        )
    }

    // Confirm Exit Dialog to prevent accidental loss when back is pressed in active paper
    if (showConfirmExitDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmExitDialog = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = colors.teal)
            },
            title = {
                Text(
                    text = "Exit Question Set?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.inkBold
                )
            },
            text = {
                Text(
                    text = "Your progress, selected answers, and elapsed time are automatically saved in your local database. Do you want to return to the Subject Overview dashboard?",
                    fontSize = 13.5.sp,
                    color = colors.ink,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmExitDialog = false
                        viewModel.closeCurrentPaper()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.navyBanner,
                        contentColor = colors.navyBannerText
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Exit to Overview", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmExitDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text("Keep Working")
                }
            },
            containerColor = colors.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Full-screen Activity-hosted Create Question Set Sheet (native status, nav & IME insets)
    AnimatedVisibility(
        visible = showInputSheet,
        enter = slideInVertically(animationSpec = tween(240), initialOffsetY = { it / 8 }) + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200), targetOffsetY = { it / 8 }) + fadeOut(animationSpec = tween(160))
    ) {
        val activeTargetFolderId = creationTargetFolderId?.takeIf { id ->
            id != "all" && folders.any { it.id == id }
        }
        InputBottomSheet(
            initialText = "",
            initialSubject = activeTargetFolderId?.let { viewModel.getFullFolderPath(it) } ?: "",
            initialFolderId = activeTargetFolderId,
            savedPapers = savedPapers,
            folders = folders,
            onDismiss = {
                showInputSheet = false
                creationTargetFolderId = null
            },
            onGenerate = { text, title, subject, topic, setNumber, targetFolderId, duration, posMarks, negMarks, unkMarks, manualCategory ->
                creationTargetFolderId = null
                viewModel.parseAndSetPaper(
                    text = text,
                    customTitle = title,
                    customSubject = subject,
                    customTopic = topic,
                    customSetNumber = setNumber,
                    customFolderId = targetFolderId,
                    customDurationMinutes = duration,
                    customPositiveMarks = posMarks,
                    customNegativeMarks = negMarks,
                    customUnattendedMarks = unkMarks,
                    customManualCategory = manualCategory
                )
            },
            onPickFile = {
                pendingFileTargetFolderId = activeTargetFolderId
                filePickerLauncher.launch(arrayOf("text/plain", "text/markdown", "*/*"))
                showInputSheet = false
                creationTargetFolderId = null
            },
            onLoadSample = { sampleIdx ->
                creationTargetFolderId = null
                viewModel.loadSample(sampleIdx)
            },
            onOpenPromptBuilder = {
                showInputSheet = false
                creationTargetFolderId = null
                viewModel.selectTab(5)
            },
            promptPresets = promptPresets,
            activeQuestionPresetId = viewModel.activeQuestionPresetId,
            onSelectQuestionPreset = { preset -> viewModel.selectPromptPreset(preset, applyToMainBuilder = true) },
            onSaveNewQuestionPreset = { name, cfg ->
                viewModel.saveNewPromptPreset(
                    mode = com.example.model.PromptPresetMode.QUESTION_SET,
                    rawName = name,
                    questionConfig = cfg
                )
            },
            onUpdateQuestionPreset = { id, newName, cfg ->
                viewModel.updatePromptPreset(presetId = id, newName = newName, questionConfig = cfg)
            },
            onRenamePromptPreset = { id, newName -> viewModel.renamePromptPreset(id, newName) },
            onDuplicatePromptPreset = { id -> viewModel.duplicatePromptPreset(id) },
            onTogglePinPromptPreset = { id -> viewModel.togglePinPromptPreset(id) },
            onDeletePromptPreset = { id -> viewModel.deletePromptPreset(id) },
            onClearQuestionPreset = { viewModel.setActivePromptPresetId(com.example.model.PromptPresetMode.QUESTION_SET, null) }
        )
    }

    // Print Configuration Dialog
    if (showPrintDialog && currentPaper != null) {
        PrintConfigDialog(
            paper = currentPaper!!,
            initialConfig = examConfig,
            onDismiss = { showPrintDialog = false },
            onPrintTriggered = { newConfig -> viewModel.updateExamConfig(newConfig) }
        )
    }

    // Optical OMR Scanner Dialog
    if (showOmrScannerDialog) {
        val scannerPaper = currentPaper
            ?: savedPapers.firstOrNull()?.toQuestionPaper()
            ?: viewModel.lastOpenedPaper.collectAsState().value?.toQuestionPaper()
            ?: com.example.model.QuestionPaper(
                metadata = com.example.model.PaperMetadata(
                    title = "Evaluation Assessment Sheet",
                    subject = "Standard Evaluation",
                    setNumber = "Set A"
                ),
                questions = emptyList()
            )

        OmrScannerDialog(
            paper = scannerPaper,
            allPapers = savedPapers,
            allAttempts = allAttempts,
            onSwitchPaper = { matchedEntity ->
                viewModel.loadSavedPaper(matchedEntity)
            },
            onDismiss = { showOmrScannerDialog = false },
            onSubmitResult = { scoringResult ->
                viewModel.submitScannedOmrResult(
                    result = scoringResult,
                    candidateRollNumber = scoringResult.headerResult?.rollNumber,
                    candidateBookletNumber = scoringResult.headerResult?.bookletNumber,
                    candidateBookletCode = scoringResult.headerResult?.bookletCode
                )
                viewModel.selectTab(2) // Jump to Student Test tab to see evaluated result review
            }
        )
    }

    // Psychometric & Classical Test Theory Evaluation Dialog
    if (showPsychometricDialog) {
        PsychometricAnalysisDialog(
            paper = currentPaper,
            viewModel = viewModel,
            onDismiss = { showPsychometricDialog = false }
        )
    }

    // Saved Papers Dialog
    if (showSavedDialog) {
        SavedPapersDialog(
            papers = savedPapers,
            viewModel = viewModel,
            onSelectPaper = { viewModel.loadSavedPaper(it) },
            onDeletePaper = { viewModel.deleteSavedPaper(it) },
            onEditPaper = { paperEntity ->
                viewModel.loadSavedPaper(paperEntity)
                viewModel.openEditMetadataOverlay()
            },
            onDismiss = { showSavedDialog = false }
        )
    }

    // Typography & Font Scale Dialog
    if (showFontSizeDialog) {
        val currentScaleMode by viewModel.fontScaleMode.collectAsState()
        FontSizeSelectorDialog(
            currentMode = currentScaleMode,
            onModeSelected = { mode -> viewModel.setFontScaleMode(mode) },
            onDismissRequest = { showFontSizeDialog = false }
        )
    }

    // Full-screen edge-to-edge Knowledge Sets Studio Reader Overlay (no Dialog clipping)
    activeKnowledgeReaderNoteId?.let { noteId ->
        val liveNote = studyNotes.find { it.id == noteId }
        if (liveNote != null) {
            val linkedPaper = liveNote.linkedPaperId?.let { pid -> savedPapers.find { it.id == pid } }
            KnowledgeStudioReaderScreen(
                note = liveNote,
                linkedPaperTitle = linkedPaper?.title,
                srsMap = flashcardSrsByNote[liveNote.id].orEmpty(),
                onToggleBookmark = { note, idx -> viewModel.toggleStudyNoteLineBookmark(note, idx) },
                onToggleMastered = { note, idx -> viewModel.toggleStudyNoteBlockMastered(note, idx) },
                onRateFlashcard = { note, idx, rating -> viewModel.reviewFlashcard(note, idx, rating) },
                onResetFlashcardSrs = { note -> viewModel.resetFlashcardSrsProgress(note) },
                onEdit = {
                    viewModel.openKnowledgeEditor(
                        note = liveNote,
                        defaultFolderId = liveNote.folderId,
                        defaultSubject = liveNote.subject,
                        defaultLinkedPaperId = liveNote.linkedPaperId
                    )
                },
                onOpenLinkedPaper = { pid ->
                    savedPapers.find { it.id == pid }?.let { target ->
                        viewModel.closeKnowledgeReader()
                        viewModel.loadSavedPaper(target)
                        viewModel.selectTab(1)
                    }
                },
                onGenerateMcqDraft = { note -> viewModel.openMcqDraftFromKnowledgeSet(note) },
                onDismiss = { viewModel.closeKnowledgeReader() }
            )
        }
    }

    // Dedicated Full-screen Vocabulary Hub Overlay (completely separate from Knowledge Sets)
    if (viewModel.showVocabularyHub) {
        val vocabDecks by viewModel.vocabularyDecks.collectAsState()
        val vocabSrs by viewModel.vocabularySrsByDeck.collectAsState()
        val preferredEnglishVariant by viewModel.preferredEnglishVariant.collectAsState()
        val wordOfTheDayState by viewModel.wordOfTheDayState.collectAsState()
        val vocabReminderEnabled by viewModel.vocabularyReminderEnabled.collectAsState()
        VocabularyHubDialog(
            decks = vocabDecks,
            srsByDeck = vocabSrs,
            preferredEnglishVariant = preferredEnglishVariant,
            wordOfTheDayState = wordOfTheDayState,
            vocabularyReminderEnabled = vocabReminderEnabled,
            initialLanguageFilter = viewModel.vocabularyHubInitialLanguage,
            onDismiss = { viewModel.closeVocabularyHub() },
            onOpenAiPromptBuilderForVocab = {
                viewModel.promptBuilderMode = PromptBuilderMode.VOCABULARY
                viewModel.selectTab(5)
            },
            onChangeEnglishVariant = { variant -> viewModel.setPreferredEnglishVariant(variant) },
            onToggleVocabularyReminder = { enabled -> viewModel.setVocabularyReminderEnabled(enabled) },
            onActiveLanguageChanged = { language ->
                viewModel.loadWordOfTheDayForLanguage(language = language, cycleNext = false)
            },
            onCycleNextWordOfTheDay = { language ->
                viewModel.loadWordOfTheDayForLanguage(language = language, cycleNext = true)
            },
            onCommitImport = { targetId, title, lang, transLang, lvlCode, customLvl, dom, analysis, decisions, langVariant ->
                viewModel.commitVocabularyImport(
                    targetDeckId = targetId,
                    title = title,
                    language = lang,
                    translationLanguage = transLang,
                    levelCode = lvlCode,
                    customLevelLabel = customLvl,
                    domain = dom,
                    analysis = analysis,
                    decisions = decisions,
                    languageVariant = langVariant
                )
            },
            onCommitSmartRoutedImport = { targets, entryBucketById, analysis, decisions ->
                viewModel.commitSmartRoutedVocabularyImport(
                    targets = targets,
                    entryBucketById = entryBucketById,
                    analysis = analysis,
                    decisions = decisions
                )
            },
            onRenameDeck = { id, newTitle, lang, trans, lvl, customLvl, langVariant ->
                viewModel.renameVocabularyDeck(id, newTitle, lang, trans, lvl, customLvl, langVariant)
            },
            onDeleteDeck = { id -> viewModel.deleteVocabularyDeck(id) },
            onToggleEntryBookmark = { deckId, entryId -> viewModel.toggleVocabularyEntryBookmark(deckId, entryId) },
            onDeleteEntry = { deckId, entryId -> viewModel.deleteVocabularyEntry(deckId, entryId) },
            onRateEntry = { deckId, entryId, rating -> viewModel.rateVocabularyEntry(deckId, entryId, rating) },
            onResetDeckSrs = { deckId -> viewModel.resetVocabularyDeckSrs(deckId) },
            onResetLanguageSrs = { language -> viewModel.resetVocabularyLanguageSrs(language) },
            onApplyAuditFixes = { deckId, findings -> viewModel.applyVocabularyAuditFixes(deckId, findings) },
            onApplyMixedAuditFixes = { originByEntryId, findings ->
                viewModel.applyMixedVocabularyAuditFixes(originByEntryId, findings)
            }
        )
    }

    // Full-screen edge-to-edge Knowledge Sets Studio Editor Overlay (no Dialog/IME clipping)
    knowledgeEditorDraft?.let { draft ->
        KnowledgeStudioEditorScreen(
            draft = draft,
            folders = folders,
            savedPapers = savedPapers,
            onSave = { id, title, subject, sourceAi, rawContent, folderId, collectionType, customLabel, linkedPaperId, formatRule ->
                viewModel.saveStudyNote(
                    id = id,
                    title = title,
                    subject = subject,
                    sourceAi = sourceAi,
                    rawContent = rawContent,
                    folderId = folderId,
                    collectionType = collectionType,
                    customLabel = customLabel,
                    linkedPaperId = linkedPaperId,
                    formatRule = formatRule
                )
            },
            onDismiss = { viewModel.closeKnowledgeEditor() },
            promptPresets = promptPresets,
            activeKnowledgePresetId = viewModel.activeKnowledgePresetId,
            onSelectKnowledgePreset = { preset -> viewModel.selectPromptPreset(preset, applyToMainBuilder = true) },
            onSaveNewKnowledgePreset = { name, cfg ->
                viewModel.saveNewPromptPreset(
                    mode = com.example.model.PromptPresetMode.KNOWLEDGE_SET,
                    rawName = name,
                    knowledgeConfig = cfg
                )
            },
            onUpdateKnowledgePreset = { id, newName, cfg ->
                viewModel.updatePromptPreset(presetId = id, newName = newName, knowledgeConfig = cfg)
            },
            onRenamePromptPreset = { id, newName -> viewModel.renamePromptPreset(id, newName) },
            onDuplicatePromptPreset = { id -> viewModel.duplicatePromptPreset(id) },
            onTogglePinPromptPreset = { id -> viewModel.togglePinPromptPreset(id) },
            onDeletePromptPreset = { id -> viewModel.deletePromptPreset(id) },
            onClearKnowledgePreset = { viewModel.setActivePromptPresetId(com.example.model.PromptPresetMode.KNOWLEDGE_SET, null) }
        )
    }

    // Full-screen Activity-hosted Edit Question Set Details Sheet (native status, nav & IME insets)
    AnimatedVisibility(
        visible = isEditMetadataOverlayOpen && currentPaper != null,
        enter = slideInVertically(animationSpec = tween(240), initialOffsetY = { it / 8 }) + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200), targetOffsetY = { it / 8 }) + fadeOut(animationSpec = tween(160))
    ) {
        currentPaper?.let { activePaperForEdit ->
            EditMetadataDialog(
                initialMetadata = activePaperForEdit.metadata,
                folders = folders,
                initialConfig = examConfig,
                onSave = { title, subject, topic, setNumber, rollNumber, bookletNumber, manualCategory ->
                    viewModel.updatePaperMetadata(
                        title = title,
                        subject = subject,
                        topic = topic,
                        setNumber = setNumber,
                        rollNumber = rollNumber,
                        bookletNumber = bookletNumber,
                        manualCategory = manualCategory
                    )
                },
                onSaveConfig = { newConfig ->
                    viewModel.updateExamConfig(newConfig)
                },
                onDismiss = { viewModel.closeEditMetadataOverlay() },
                onEnterQuestionEditMode = editMetadataEnterQuestionEditAction
            )
        }
    }

    // Full-screen Activity-hosted Add Questions Sheet (native status, nav & IME insets)
    AnimatedVisibility(
        visible = isAddQuestionOverlayOpen && currentPaper != null,
        enter = slideInVertically(animationSpec = tween(240), initialOffsetY = { it / 8 }) + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200), targetOffsetY = { it / 8 }) + fadeOut(animationSpec = tween(160))
    ) {
        AddQuestionDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeAddQuestionOverlay() }
        )
    }
    }
}

@Composable
private fun EmptyStateView(
    savedPapers: List<PaperEntity>,
    allAttempts: List<AttemptEntity>,
    onPasteClick: () -> Unit,
    onPickFile: () -> Unit,
    onOpenSavedPapers: () -> Unit,
    onLoadSample: (Int) -> Unit,
    onOpenSubject: (String) -> Unit
) {
    val colors = ExamTheme.colors
    val savedPapersCount = savedPapers.size
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (savedPapersCount > 0) Arrangement.Top else Arrangement.Center
    ) {
        if (savedPapersCount > 0) {
            SubjectDashboard(
                savedPapers = savedPapers,
                allAttempts = allAttempts,
                onOpenSubject = onOpenSubject
            )
            Spacer(modifier = Modifier.height(28.dp))
        }

        Surface(
            shape = CircleShape,
            color = colors.tealLight,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Quiz,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Welcome to ExamSet",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.inkBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Create new MCQ question sets or choose from your saved exam folders above.",
            fontSize = 13.sp,
            color = colors.muted,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Primary Action Row: Add / Paste & Add File
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPasteClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.navyBanner,
                    contentColor = colors.navyBannerText
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("empty_state_add_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create / Paste Set", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onPickFile,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("empty_state_file_button")
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.teal)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add File")
            }
        }

        // If saved papers exist, offer direct access
        if (savedPapersCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenSavedPapers,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.tealDark),
                border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("empty_state_saved_papers_button")
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.teal)
                Spacer(modifier = Modifier.width(6.dp))
                Text("View All Question Sets ($savedPapersCount)", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Multilingual & Multi-Format Sample options for convenience
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onLoadSample(0) }) {
                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.teal)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Science (EN)", color = colors.teal, fontSize = 12.sp)
            }

            TextButton(onClick = { onLoadSample(1) }) {
                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.teal)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Hindi (HI)", color = colors.teal, fontSize = 12.sp)
            }

            TextButton(onClick = { onLoadSample(2) }) {
                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.teal)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Korean (KO)", color = colors.teal, fontSize = 12.sp)
            }

            TextButton(onClick = { onLoadSample(3) }) {
                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.teal)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Genetics (EN)", color = colors.teal, fontSize = 12.sp)
            }

            TextButton(onClick = { onLoadSample(4) }) {
                Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.teal)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Multi-Pattern (EN)", color = colors.teal, fontSize = 12.sp)
            }
        }
    }
}

enum class DashboardSortOption(val label: String) {
    RECENT("Recent Usage"),
    MOST_SETS("Most Sets"),
    MOST_ATTEMPTS("Most Attempts"),
    ALPHABETICAL("A - Z")
}

data class SubjectStat(
    val name: String,
    val setCount: Int,
    val attemptCount: Int,
    val latestTimestamp: Long,
    val topics: Set<String>
)

@Composable
fun SubjectDashboard(
    savedPapers: List<PaperEntity>,
    allAttempts: List<AttemptEntity>,
    onOpenSubject: (String) -> Unit
) {
    val colors = ExamTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf(DashboardSortOption.RECENT) }

    val rawStats = remember(savedPapers, allAttempts) {
        savedPapers.groupBy { it.subject.trim().lowercase() }.map { (_, papers) ->
            val paperIds = papers.map { it.id }.toSet()
            val attempts = allAttempts.filter { it.paperId in paperIds }
            val latestPaperTime = papers.maxOfOrNull { it.updatedAt } ?: 0L
            val latestAttemptTime = attempts.maxOfOrNull { it.createdAt } ?: 0L
            val latestTime = maxOf(latestPaperTime, latestAttemptTime)
            val topicList = papers.map { it.topic.trim() }.filter { it.isNotBlank() }.toSet()

            SubjectStat(
                name = papers.first().subject.trim(),
                setCount = papers.size,
                attemptCount = attempts.size,
                latestTimestamp = latestTime,
                topics = topicList
            )
        }
    }

    val filteredStats = remember(rawStats, searchQuery, selectedSort) {
        val filtered = if (searchQuery.isBlank()) {
            rawStats
        } else {
            val q = searchQuery.trim().lowercase()
            rawStats.filter { stat ->
                stat.name.lowercase().contains(q) ||
                stat.topics.any { it.lowercase().contains(q) }
            }
        }

        when (selectedSort) {
            DashboardSortOption.RECENT -> filtered.sortedByDescending { it.latestTimestamp }
            DashboardSortOption.MOST_SETS -> filtered.sortedByDescending { it.setCount }
            DashboardSortOption.MOST_ATTEMPTS -> filtered.sortedByDescending { it.attemptCount }
            DashboardSortOption.ALPHABETICAL -> filtered.sortedBy { it.name.lowercase() }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header with count badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Subject Overview",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.tealLight
                ) {
                    Text(
                        text = "${rawStats.size} Folders",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.teal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "Tap to open",
                fontSize = 11.5.sp,
                color = colors.muted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar for finding exam folders
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search folders...",
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false,
                    color = colors.muted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag("dashboard_search_bar")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter and Sort Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashboardSortOption.values().forEach { option ->
                val selected = selectedSort == option
                FilterChip(
                    selected = selected,
                    onClick = { selectedSort = option },
                    label = { Text(option.label, fontSize = 11.5.sp) },
                    leadingIcon = {
                        val icon = when (option) {
                            DashboardSortOption.RECENT -> Icons.Default.Schedule
                            DashboardSortOption.MOST_SETS -> Icons.AutoMirrored.Filled.MenuBook
                            DashboardSortOption.MOST_ATTEMPTS -> Icons.Default.TrendingUp
                            DashboardSortOption.ALPHABETICAL -> Icons.Default.SortByAlpha
                        }
                        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.tealLight,
                        selectedLabelColor = colors.teal,
                        selectedLeadingIconColor = colors.teal
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredStats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No folders match \"$searchQuery\"",
                        fontSize = 13.sp,
                        color = colors.muted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = { searchQuery = "" }) {
                        Text("Clear Search", color = colors.teal, fontSize = 12.sp)
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredStats, key = { it.name }) { stat ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                        border = BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .width(170.dp)
                            .clickable { onOpenSubject(stat.name) }
                            .testTag("subject_folder_${stat.name}")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = colors.tealLight,
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

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Open Folder",
                                    tint = colors.teal,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = stat.name,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = colors.muted, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${stat.setCount} Sets", fontSize = 12.sp, color = colors.muted)
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = colors.muted, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${stat.attemptCount} Attempts", fontSize = 12.sp, color = colors.muted)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.tealLight.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Open Folder",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.teal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 3.dp)
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
fun NoPaperPlaceholder(
    title: String,
    onBrowseFolders: () -> Unit,
    onGoToCreator: () -> Unit,
    lastOpenedPaper: PaperEntity? = null,
    onOpenLastPaper: ((PaperEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(0.8.dp, colors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No Active Assessment Loaded",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "To use the $title, choose your last opened question set or select an assessment in Folders & Library.",
                    fontSize = 12.sp,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center
                )

                if (lastOpenedPaper != null && onOpenLastPaper != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Last Opened Assessment:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.muted
                                )
                                Text(
                                    text = lastOpenedPaper.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onOpenLastPaper(lastOpenedPaper) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.heightIn(min = 38.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Open Set", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBrowseFolders,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.navyBanner,
                            contentColor = colors.navyBannerText
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Browse Library", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onGoToCreator,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.teal)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exam Tools", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

