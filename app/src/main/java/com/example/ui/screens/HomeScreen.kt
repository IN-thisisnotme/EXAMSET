package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
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
    val savedPapers by viewModel.savedPapers.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isCardBoxed by viewModel.isCardBoxed.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val masterListState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            // Allow expanding, but prevent swiping/dragging down to dismiss so scrolling never closes the sheet
            targetValue != SheetValue.Hidden
        }
    )

    var showInputSheet by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var showOmrScannerDialog by remember { mutableStateOf(false) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showHistoricalAnalyticsDialog by remember { mutableStateOf(false) }
    var showPsychometricDialog by remember { mutableStateOf(false) }
    var selectedSubjectFolder by remember { mutableStateOf<String?>(null) }
    var showConfirmExitDialog by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableStateOf(0L) }

    // Accidental Back Navigation Prevention
    BackHandler {
        when {
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
            showEditDialog -> {
                showEditDialog = false
            }
            currentPaper != null -> {
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
        if (uri != null) {
            viewModel.loadFromUri(uri)
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
            TopAppBar(
                navigationIcon = {
                    if (currentPaper != null) {
                        IconButton(
                            onClick = { showConfirmExitDialog = true },
                            modifier = Modifier.testTag("exit_paper_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Return to Subject Overview",
                                tint = colors.inkBold
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = colors.teal
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ExamSet",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            currentPaper?.let { paper ->
                                Text(
                                    text = "${paper.metadata.setNumber.ifBlank { "Set A" }} • ${paper.metadata.subject}",
                                    fontSize = 10.5.sp,
                                    color = colors.tealDark,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } ?: Text(
                                text = "MCQ Paper Studio",
                                fontSize = 10.5.sp,
                                color = colors.muted,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // Today's Test Study Timer & Historical Analytics Trigger
                    val topTodaySecs = viewModel.todayTestSeconds.collectAsState().value
                    val isExamRunning = viewModel.isExamStarted.collectAsState().value && !isExamSubmitted
                    Surface(
                        onClick = { showHistoricalAnalyticsDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isExamRunning) colors.tealLight else colors.surfaceVariant,
                        border = BorderStroke(0.8.dp, if (isExamRunning) colors.teal else colors.border),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("topbar_today_analytics_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Today Study Timer & Historical Analysis",
                                tint = if (isExamRunning) colors.teal else colors.inkBold,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            val tM = topTodaySecs / 60
                            val tS = topTodaySecs % 60
                            Text(
                                text = String.format(java.util.Locale.US, "%02d:%02d", tM, tS),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isExamRunning) colors.tealDark else colors.inkBold
                            )
                        }
                    }

                    // Dark / Eye-Safety Mode Toggle
                    IconButton(
                        onClick = { viewModel.toggleThemeMode() },
                        modifier = Modifier.testTag("dark_mode_toggle")
                    ) {
                        Icon(
                            imageVector = if (colors.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (colors.isDark) "Switch to Light Mode" else "Switch to Eye-Safe Dark Mode",
                            tint = if (colors.isDark) colors.teal else colors.inkBold
                        )
                    }

                    // Edit Details button (if paper is loaded)
                    if (currentPaper != null) {
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("edit_topbar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Subject, Topic & Set No.",
                                tint = colors.teal
                            )
                        }

                        // Psychometric & Item Difficulty Analysis button
                        IconButton(
                            onClick = { showPsychometricDialog = true },
                            modifier = Modifier.testTag("psychometrics_topbar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Psychometric & Item Difficulty Analysis",
                                tint = Color(0xFF0284C7)
                            )
                        }
                    }

                    // Share paper (if paper loaded)
                    val activePaper = currentPaper
                    if (activePaper != null) {
                        IconButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TITLE, activePaper.metadata.title)
                                    putExtra(Intent.EXTRA_TEXT, activePaper.rawText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Question Set"))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = colors.inkBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface
                )
            )
        },
        floatingActionButton = {
            // Only show the '+' creation overlay on the Folders library tab (tab 0).
            // Hidden on Master (tab 1), Tests (tab 2), Print (tab 3), and Tools (tabs 4-6).
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showInputSheet = true },
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
            NavigationBar(
                containerColor = colors.surface,
                contentColor = colors.inkBold,
                tonalElevation = 8.dp
            ) {
                val navItems = listOf(
                    Triple(0, "Folders", Icons.Default.FolderOpen),
                    Triple(1, "Master", Icons.AutoMirrored.Filled.Assignment),
                    Triple(2, "Tests", Icons.Default.School),
                    Triple(3, "Print", Icons.Default.Print),
                    Triple(4, "Tools", Icons.Default.Tune)
                )

                navItems.forEach { (index, label, icon) ->
                    val isSelected = selectedTab == index || (index == 4 && (selectedTab == 5 || selectedTab == 6))
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
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Creator Tools Sub-Navigation Pills (when in Creator / AI Prompt / Share)
            if (selectedTab in 4..6) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
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
                                .clickable { viewModel.selectTab(tabIdx) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSubSelected) colors.tealLight else colors.surfaceVariant,
                            border = BorderStroke(1.dp, if (isSubSelected) colors.teal else colors.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 7.dp),
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
                                    fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSubSelected) colors.teal else colors.inkBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Quick Stats & Chips Row
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
                    // Set Number Chip
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.navyBanner
                    ) {
                        Text(
                            text = paper.metadata.setNumber.ifBlank { "SET A" }.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.navyBannerText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // Question count chip
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

                    // Answer key chip
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

                    // Edit Details Chip (Commented out per user preference so it does not persist across all tabs)
                    // Surface(
                    //     onClick = { showEditDialog = true },
                    //     shape = RoundedCornerShape(16.dp),
                    //     color = colors.surfaceVariant,
                    //     border = BorderStroke(0.6.dp, colors.border),
                    //     modifier = Modifier.testTag("chip_edit_details")
                    // ) {
                    //     Row(
                    //         modifier = Modifier
                    //             .padding(horizontal = 10.dp, vertical = 4.dp),
                    //         verticalAlignment = Alignment.CenterVertically
                    //     ) {
                    //         Icon(
                    //             imageVector = Icons.Default.Edit,
                    //             contentDescription = null,
                    //             tint = colors.teal,
                    //             modifier = Modifier.size(13.dp)
                    //         )
                    //         Spacer(modifier = Modifier.width(4.dp))
                    //         Text(
                    //             text = "Edit Details",
                    //             fontSize = 11.sp,
                    //             fontWeight = FontWeight.SemiBold,
                    //             color = colors.tealDark
                    //         )
                    //     }
                    // }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Tab Body
            val paper = currentPaper
            when (selectedTab) {
                0 -> FolderManagerTab(
                    viewModel = viewModel,
                    onNavigateToMaster = { viewModel.selectTab(1) },
                    onNavigateToStudent = { viewModel.selectTab(2) }
                )
                1 -> MasterViewTab(
                    viewModel = viewModel,
                    listState = masterListState
                )
                2 -> if (paper != null && paper.questions.isNotEmpty()) {
                    StudentTestTab(
                        paper = paper,
                        answers = studentAnswers,
                        submittedStats = viewModel.submittedStats.collectAsState().value,
                        submittedAnswers = viewModel.submittedAnswers.collectAsState().value,
                        incorrectQuestionLog = viewModel.incorrectQuestionLog.collectAsState().value,
                        unattendedQuestionLog = viewModel.unattendedQuestionLog.collectAsState().value,
                        isExamStarted = viewModel.isExamStarted.collectAsState().value,
                        isSubmitted = isExamSubmitted,
                        retryQuestionNumbers = viewModel.retryQuestionNumbers.collectAsState().value,
                        timeSpentSeconds = viewModel.timeSpentSeconds.collectAsState().value,
                        timeRemainingSeconds = viewModel.timeRemainingSeconds.collectAsState().value,
                        timerMode = viewModel.timerMode.collectAsState().value,
                        customTimeLimitMinutes = viewModel.customTimeLimitMinutes.collectAsState().value,
                        attempts = viewModel.attempts.collectAsState().value,
                        todayTestSeconds = viewModel.todayTestSeconds.collectAsState().value,
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
                        onOpenPsychometrics = { showPsychometricDialog = true }
                    )
                } else {
                    NoPaperPlaceholder(
                        title = "Student Test Series",
                        onGoToCreator = { viewModel.selectTab(4) }
                    )
                }
                3 -> if (paper != null && paper.questions.isNotEmpty()) {
                    PrintablePaperTab(
                        paper = paper,
                        config = examConfig,
                        onOpenConfig = { showPrintDialog = true }
                    )
                } else {
                    NoPaperPlaceholder(
                        title = "Print & OMR Generator",
                        onGoToCreator = { viewModel.selectTab(4) }
                    )
                }
                4 -> CreatorTab(
                    viewModel = viewModel,
                    onNavigateToMaster = { viewModel.selectTab(1) },
                    onNavigateToTests = { viewModel.selectTab(2) },
                    onNavigateToPrint = { viewModel.selectTab(3) },
                    onEditMetadata = { showEditDialog = true }
                )
                5 -> PromptGeneratorTab(
                    viewModel = viewModel,
                    onNavigateToCreator = { viewModel.selectTab(4) }
                )
                6 -> ShareSystemTab(
                    viewModel = viewModel,
                    onNavigateToMaster = { viewModel.selectTab(1) },
                    onOpenPsychometrics = { showPsychometricDialog = true }
                )
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
                viewModel.loadSavedPaper(paper)
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

    // Input Bottom Sheet
    if (showInputSheet) {
        InputBottomSheet(
            initialText = "",
            savedPapers = savedPapers,
            onDismiss = { showInputSheet = false },
            onGenerate = { text, title, subject, topic, setNumber ->
                viewModel.parseAndSetPaper(
                    text = text,
                    customTitle = title,
                    customSubject = subject,
                    customTopic = topic,
                    customSetNumber = setNumber
                )
            },
            onPickFile = {
                filePickerLauncher.launch(arrayOf("text/plain", "text/markdown", "*/*"))
                showInputSheet = false
            },
            onLoadSample = { sampleIdx -> viewModel.loadSample(sampleIdx) },
            sheetState = sheetState,
            onOpenPromptBuilder = {
                showInputSheet = false
                viewModel.selectTab(5)
            }
        )
    }

    // Edit Metadata Dialog (Title, Subject, Topic, Set Number, Roll No, Booklet No)
    if (showEditDialog && currentPaper != null) {
        EditMetadataDialog(
            initialMetadata = currentPaper!!.metadata,
            onSave = { title, subject, topic, setNumber, rollNumber, bookletNumber ->
                viewModel.updatePaperMetadata(title, subject, topic, setNumber, rollNumber, bookletNumber)
            },
            onDismiss = { showEditDialog = false }
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
    if (showOmrScannerDialog && currentPaper != null) {
        OmrScannerDialog(
            paper = currentPaper!!,
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
    if (showPsychometricDialog && currentPaper != null) {
        PsychometricAnalysisDialog(
            paper = currentPaper!!,
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
                showEditDialog = true
            },
            onDismiss = { showSavedDialog = false }
        )
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
private fun NoPaperPlaceholder(
    title: String,
    onGoToCreator: () -> Unit,
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
                    text = "To use the $title, select or import a question set in Folders & Library or Exam Tools.",
                    fontSize = 12.sp,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onGoToCreator,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Go to Exam Tools", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

