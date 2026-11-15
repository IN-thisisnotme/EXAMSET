package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.toQuestionPaper
import com.example.model.McqQuestion
import com.example.model.QuestionPaper
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme
import com.example.util.PsychometricEngine
import java.util.Locale

/**
 * Standardized Psychometric & Classical Test Theory (CTT) Evaluation Workbench.
 *
 * Rendered inside the Activity window using Material 3 Scaffold & WindowInsets.safeDrawing
 * so status bars and navigation bars never clip or trim the bottom action dock on any device.
 *
 * Adaptive across both Portrait and Landscape / Wide screens:
 * - Portrait: Non-truncating header + paper selector pill + segmented tab switcher + scrollable KPI/items + docked bottom export bar
 * - Landscape / Wide: 2-pane split workbench (Left: Cohort & KPI summary + score meter + exports; Right: Tabs, filters & full-height cards)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PsychometricAnalysisDialog(
    paper: QuestionPaper?,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = ExamTheme.colors
    val savedPapers by viewModel.savedPapers.collectAsState()
    val allAttempts by viewModel.allAttempts.collectAsState()
    val lastOpenedPaper by viewModel.lastOpenedPaper.collectAsState()

    var selectedPaperEntityId by remember {
        mutableStateOf<Long?>(
            viewModel.currentPaperEntityId.value
                ?: lastOpenedPaper?.id
                ?: savedPapers.firstOrNull()?.id
        )
    }
    var showPaperPickerDropdown by remember { mutableStateOf(false) }

    val effectivePaper: QuestionPaper? = remember(paper, selectedPaperEntityId, savedPapers) {
        if (paper != null && (selectedPaperEntityId == null || selectedPaperEntityId == viewModel.currentPaperEntityId.value)) {
            paper
        } else if (selectedPaperEntityId != null) {
            savedPapers.find { it.id == selectedPaperEntityId }?.toQuestionPaper() ?: paper
        } else {
            paper ?: savedPapers.firstOrNull()?.toQuestionPaper()
        }
    }

    var useSimulationMode by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("ITEMS") } // "ITEMS" or "STUDENTS"
    var itemFilter by remember { mutableStateOf("ALL") } // "ALL", "FLAGGED", "HARD", "EASY", "DISCRIMINATING"
    var copiedReport by remember { mutableStateOf(false) }
    var selectedCandidateForReportCard by remember { mutableStateOf<PsychometricEngine.CandidateScorecard?>(null) }

    BackHandler(enabled = true) {
        if (selectedCandidateForReportCard != null) {
            selectedCandidateForReportCard = null
        } else {
            onDismiss()
        }
    }

    val report = remember(effectivePaper, useSimulationMode, selectedPaperEntityId, allAttempts) {
        if (effectivePaper == null || effectivePaper.questions.isEmpty()) {
            null
        } else {
            viewModel.generatePsychometricReport(
                targetPaperId = selectedPaperEntityId,
                paperOverride = effectivePaper,
                forceSimulation = useSimulationMode,
                allowSimulationFallback = false
            )
        }
    }

    val accentBlue = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val accentTeal = if (colors.isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
    val accentEmerald = if (colors.isDark) Color(0xFF34D399) else Color(0xFF059669)
    val accentAmber = if (colors.isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    val accentRose = if (colors.isDark) Color(0xFFF87171) else Color(0xFFDC2626)

    val brandGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF0284C7), Color(0xFF0D9488))
    )

    val isSimulatedCohort = remember(report) {
        report?.candidates?.any { it.rollNumber.startsWith("202610") } == true
    }

    val onCopyItemCsv: () -> Unit = {
        if (effectivePaper != null) {
            val csv = viewModel.exportPsychometricReportCsv(
                paperOverride = effectivePaper,
                forceSimulation = useSimulationMode,
                allowSimulationFallback = false
            )
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Psychometric Report CSV", csv))
            copiedReport = true
            Toast.makeText(context, "Copied Item Analysis CSV!", Toast.LENGTH_SHORT).show()
        }
    }

    val onCopyRosterCsv: () -> Unit = {
        if (effectivePaper != null) {
            val csv = viewModel.exportCandidateScorecardsCsv(
                paperOverride = effectivePaper,
                forceSimulation = useSimulationMode,
                allowSimulationFallback = false
            )
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Student Scorecards CSV", csv))
            Toast.makeText(context, "Copied Student Roster CSV!", Toast.LENGTH_SHORT).show()
        }
    }

    val onCopyAllCards: () -> Unit = {
        if (effectivePaper != null) {
            val cardsText = viewModel.exportAllStudentReportCardsText(
                paperOverride = effectivePaper,
                forceSimulation = useSimulationMode,
                allowSimulationFallback = false
            )
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("All Student Report Cards", cardsText))
            Toast.makeText(context, "Copied all report cards!", Toast.LENGTH_SHORT).show()
        }
    }

    val onShareCsv: () -> Unit = {
        if (effectivePaper != null) {
            val csv = viewModel.exportPsychometricReportCsv(
                paperOverride = effectivePaper,
                forceSimulation = useSimulationMode,
                allowSimulationFallback = false
            )
            val sendIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, csv)
                type = "text/plain"
            }
            context.startActivity(
                android.content.Intent.createChooser(sendIntent, "Share Psychometric Report CSV")
            )
        }
    }

    // Consume background touches so nothing behind the full-screen workbench receives taps
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideLandscape = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = colors.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Surface(
                        color = colors.surface,
                        shadowElevation = if (colors.isDark) 0.dp else 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                        ) {
                            // Subtle energetic top accent bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(brandGradient)
                            )

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 1080.dp)
                                        .padding(
                                            horizontal = 14.dp,
                                            vertical = if (isWideLandscape) 6.dp else 10.dp
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(if (isWideLandscape) 6.dp else 8.dp)
                                ) {
                                    // Row 1: Crisp Un-Truncated Title + Close Button (and Paper Switcher inline in wide landscape)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(brandGradient),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Assessment,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Psychometric & CTT",
                                                    fontSize = 16.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = colors.inkBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = accentEmerald.copy(alpha = 0.14f),
                                                    border = BorderStroke(0.6.dp, accentEmerald.copy(alpha = 0.5f))
                                                ) {
                                                    Text(
                                                        text = "CTT SUITE",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = accentEmerald,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (isWideLandscape && effectivePaper != null) {
                                                Text(
                                                    text = "${effectivePaper.metadata.title.ifBlank { "Assessment" }} • ${effectivePaper.questions.size} Questions",
                                                    fontSize = 11.5.sp,
                                                    color = colors.muted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        if (isWideLandscape) {
                                            PaperPickerDropdownChip(
                                                effectivePaper = effectivePaper,
                                                savedPapers = savedPapers,
                                                selectedPaperEntityId = selectedPaperEntityId,
                                                expanded = showPaperPickerDropdown,
                                                onExpandedChange = { showPaperPickerDropdown = it },
                                                onSelectPaper = { selectedPaperEntityId = it },
                                                maxWidthDp = 240
                                            )
                                        }

                                        IconButton(
                                            onClick = onDismiss,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("psychometrics_close_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = colors.inkBold
                                            )
                                        }
                                    }

                                    // Row 2 (Portrait): Full-Width Exam Set Selector Pill (no truncation!)
                                    if (!isWideLandscape) {
                                        PaperPickerDropdownChip(
                                            effectivePaper = effectivePaper,
                                            savedPapers = savedPapers,
                                            selectedPaperEntityId = selectedPaperEntityId,
                                            expanded = showPaperPickerDropdown,
                                            onExpandedChange = { showPaperPickerDropdown = it },
                                            onSelectPaper = { selectedPaperEntityId = it },
                                            fullWidth = true
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                        }
                    }
                },
                bottomBar = {
                    if (!isWideLandscape && effectivePaper != null && effectivePaper.questions.isNotEmpty() && report != null) {
                        Surface(
                            color = colors.surface,
                            tonalElevation = if (colors.isDark) 2.dp else 4.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            ) {
                                HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CompactExportButton(
                                        label = if (copiedReport) "Copied!" else "Item CSV",
                                        icon = if (copiedReport) Icons.Default.Check else Icons.Default.ContentCopy,
                                        containerColor = if (copiedReport) Color(0xFF059669) else Color(0xFF0284C7),
                                        onClick = onCopyItemCsv,
                                        modifier = Modifier.weight(1.05f)
                                    )
                                    CompactExportButton(
                                        label = "Roster",
                                        icon = Icons.Default.FileDownload,
                                        containerColor = Color(0xFF059669),
                                        onClick = onCopyRosterCsv,
                                        modifier = Modifier.weight(0.95f)
                                    )
                                    CompactExportButton(
                                        label = "Cards",
                                        icon = Icons.Default.Assessment,
                                        containerColor = Color(0xFFBE185D),
                                        onClick = onCopyAllCards,
                                        modifier = Modifier.weight(0.9f)
                                    )
                                    OutlinedButton(
                                        onClick = onShareCsv,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, colors.border),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(0.85f)
                                            .heightIn(min = 44.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Share",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1080.dp)
                    ) {
                        if (effectivePaper == null || effectivePaper.questions.isEmpty() || report == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.widthIn(max = 460.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = accentBlue.copy(alpha = 0.14f),
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Assessment,
                                                contentDescription = null,
                                                tint = accentBlue,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Ready for Psychometric Evaluation",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Choose any saved question set or analyze the built-in Science Sample to explore item difficulty, discrimination indices, and candidate scorecards.",
                                        fontSize = 13.sp,
                                        color = colors.muted,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 19.sp
                                    )
                                    Button(
                                        onClick = { viewModel.loadStarterSample("science") },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0284C7),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Analyze Science Sample", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            val filteredItems = remember(report.items, itemFilter) {
                                report.items.filter { item ->
                                    when (itemFilter) {
                                        "FLAGGED" -> item.isFlagged
                                        "HARD" -> item.difficultyP <= 0.35f
                                        "EASY" -> item.difficultyP >= 0.75f
                                        "DISCRIMINATING" -> item.pointBiserialR >= 0.30f || item.discriminationD >= 0.30f
                                        else -> true
                                    }
                                }
                            }

                            if (isWideLandscape) {
                                // =========================================================
                                // LANDSCAPE / WIDE 2-PANE CTT WORKBENCH
                                // =========================================================
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .width(296.dp)
                                            .fillMaxHeight()
                                            .background(colors.surface.copy(alpha = 0.55f))
                                            .verticalScroll(rememberScrollState())
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CohortSwitcherStrip(
                                            report = report,
                                            isSimulatedCohort = isSimulatedCohort,
                                            useSimulationMode = useSimulationMode,
                                            onToggle = { useSimulationMode = !useSimulationMode }
                                        )

                                        ExecutiveKpiGrid(
                                            report = report,
                                            isSimulatedCohort = isSimulatedCohort,
                                            accentBlue = accentBlue,
                                            accentTeal = accentTeal,
                                            accentEmerald = accentEmerald,
                                            accentAmber = accentAmber,
                                            accentRose = accentRose
                                        )

                                        ScoreDistributionStrip(report = report, maxPossibleMarks = (effectivePaper.questions.size * effectivePaper.metadata.positiveMarks.coerceAtLeast(1.0)).toFloat())

                                        HorizontalDivider(color = colors.border, thickness = 0.6.dp)

                                        Text(
                                            text = "EXPORT & REPORTS",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.7.sp,
                                            color = colors.muted
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            CompactExportButton(
                                                label = if (copiedReport) "Copied" else "Item CSV",
                                                icon = if (copiedReport) Icons.Default.Check else Icons.Default.ContentCopy,
                                                containerColor = if (copiedReport) Color(0xFF059669) else Color(0xFF0284C7),
                                                onClick = onCopyItemCsv,
                                                modifier = Modifier.weight(1f)
                                            )
                                            CompactExportButton(
                                                label = "Roster CSV",
                                                icon = Icons.Default.FileDownload,
                                                containerColor = Color(0xFF059669),
                                                onClick = onCopyRosterCsv,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            CompactExportButton(
                                                label = "All Cards",
                                                icon = Icons.Default.Assessment,
                                                containerColor = Color(0xFFBE185D),
                                                onClick = onCopyAllCards,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedButton(
                                                onClick = onShareCsv,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, colors.border),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 44.dp)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Share", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                            }
                                        }
                                    }

                                    VerticalDivider(color = colors.border, thickness = 0.8.dp)

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        Surface(color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                SegmentedTabSwitcher(
                                                    questionCount = report.questionCount,
                                                    candidateCount = report.candidateCount,
                                                    activeTab = activeTab,
                                                    onSelectTab = { activeTab = it }
                                                )

                                                if (activeTab == "ITEMS") {
                                                    ItemFilterChipsRow(
                                                        report = report,
                                                        itemFilter = itemFilter,
                                                        onSelectFilter = { itemFilter = it }
                                                    )
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = colors.border, thickness = 0.6.dp)

                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (activeTab == "ITEMS") {
                                                if (filteredItems.isEmpty()) {
                                                    item(key = "empty_filter") {
                                                        EmptyItemFilterCard(
                                                            totalCount = report.items.size,
                                                            onShowAll = { itemFilter = "ALL" }
                                                        )
                                                    }
                                                } else {
                                                    items(filteredItems, key = { "item_${it.questionNum}" }) { item ->
                                                        val qModel = effectivePaper.questions.getOrNull(item.questionNum - 1)
                                                        PsychometricItemCard(
                                                            item = item,
                                                            qModel = qModel,
                                                            candidateCount = report.candidateCount
                                                        )
                                                    }
                                                }
                                            } else {
                                                if (report.candidates.isEmpty()) {
                                                    item(key = "empty_students") {
                                                        EmptyStudentRosterCard()
                                                    }
                                                } else {
                                                    items(
                                                        report.candidates,
                                                        key = { "${it.rank}_${it.rollNumber}_${it.bookletNumber}" }
                                                    ) { c ->
                                                        StudentScorecardRowCard(
                                                            candidate = c,
                                                            onSelectCandidate = { selectedCandidateForReportCard = c }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // =========================================================
                                // PORTRAIT ADAPTIVE CTT WORKBENCH
                                // =========================================================
                                Surface(
                                    color = colors.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        SegmentedTabSwitcher(
                                            questionCount = report.questionCount,
                                            candidateCount = report.candidateCount,
                                            activeTab = activeTab,
                                            onSelectTab = { activeTab = it }
                                        )
                                    }
                                }

                                HorizontalDivider(color = colors.border, thickness = 0.6.dp)

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item(key = "cohort_switcher") {
                                        CohortSwitcherStrip(
                                            report = report,
                                            isSimulatedCohort = isSimulatedCohort,
                                            useSimulationMode = useSimulationMode,
                                            onToggle = { useSimulationMode = !useSimulationMode }
                                        )
                                    }

                                    item(key = "kpi_grid") {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            ExecutiveKpiGrid(
                                                report = report,
                                                isSimulatedCohort = isSimulatedCohort,
                                                accentBlue = accentBlue,
                                                accentTeal = accentTeal,
                                                accentEmerald = accentEmerald,
                                                accentAmber = accentAmber,
                                                accentRose = accentRose
                                            )
                                            ScoreDistributionStrip(
                                                report = report,
                                                maxPossibleMarks = (effectivePaper.questions.size * effectivePaper.metadata.positiveMarks.coerceAtLeast(1.0)).toFloat()
                                            )
                                        }
                                    }

                                    if (activeTab == "ITEMS") {
                                        item(key = "item_filters_row") {
                                            ItemFilterChipsRow(
                                                report = report,
                                                itemFilter = itemFilter,
                                                onSelectFilter = { itemFilter = it }
                                            )
                                        }

                                        if (filteredItems.isEmpty()) {
                                            item(key = "empty_filter") {
                                                EmptyItemFilterCard(
                                                    totalCount = report.items.size,
                                                    onShowAll = { itemFilter = "ALL" }
                                                )
                                            }
                                        } else {
                                            items(filteredItems, key = { "item_${it.questionNum}" }) { item ->
                                                val qModel = effectivePaper.questions.getOrNull(item.questionNum - 1)
                                                PsychometricItemCard(
                                                    item = item,
                                                    qModel = qModel,
                                                    candidateCount = report.candidateCount
                                                )
                                            }
                                        }
                                    } else {
                                        if (report.candidates.isEmpty()) {
                                            item(key = "empty_students") {
                                                EmptyStudentRosterCard()
                                            }
                                        } else {
                                            items(
                                                report.candidates,
                                                key = { "${it.rank}_${it.rollNumber}_${it.bookletNumber}" }
                                            ) { c ->
                                                StudentScorecardRowCard(
                                                    candidate = c,
                                                    onSelectCandidate = { selectedCandidateForReportCard = c }
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

        AnimatedVisibility(
            visible = selectedCandidateForReportCard != null && effectivePaper != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (selectedCandidateForReportCard != null && effectivePaper != null) {
                val candidate = selectedCandidateForReportCard!!
                val reportCardText = viewModel.exportSingleStudentReportCard(
                    candidate = candidate,
                    paperOverride = effectivePaper,
                    forceSimulation = useSimulationMode,
                    allowSimulationFallback = false
                )
                StudentReportCardOverlay(
                    candidate = candidate,
                    reportCardText = reportCardText,
                    onDismiss = { selectedCandidateForReportCard = null }
                )
            }
        }
    }
}

@Composable
private fun PaperPickerDropdownChip(
    effectivePaper: QuestionPaper?,
    savedPapers: List<com.example.data.PaperEntity>,
    selectedPaperEntityId: Long?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectPaper: (Long) -> Unit,
    fullWidth: Boolean = false,
    maxWidthDp: Int = 240
) {
    val colors = ExamTheme.colors
    Box(modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier) {
        Surface(
            onClick = { if (savedPapers.isNotEmpty()) onExpandedChange(true) },
            shape = RoundedCornerShape(10.dp),
            color = colors.surfaceVariant,
            border = BorderStroke(0.9.dp, colors.border),
            modifier = (if (fullWidth) Modifier.fillMaxWidth() else Modifier.widthIn(max = maxWidthDp.dp))
                .testTag("psychometrics_paper_picker")
        ) {
            Row(
                modifier = Modifier
                    .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = fullWidth)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val titleStr = effectivePaper?.metadata?.title?.ifBlank { "Select Question Set" } ?: "Select Question Set"
                    val qCountStr = effectivePaper?.let { " • ${it.questions.size} Qs" } ?: ""
                    Text(
                        text = "$titleStr$qCountStr",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (savedPapers.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Switch Exam Set",
                        tint = colors.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.heightIn(max = 360.dp)
        ) {
            savedPapers.forEach { entity ->
                val isCurrentSelection = entity.id == selectedPaperEntityId ||
                    (selectedPaperEntityId == null && effectivePaper?.metadata?.title == entity.title)
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = if (isCurrentSelection) Icons.Default.Check else Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = if (isCurrentSelection) colors.teal else colors.muted,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = entity.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrentSelection) colors.teal else colors.inkBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${entity.setNumber.ifBlank { "01" }} • ${entity.subject} (${entity.questionCount} Qs)",
                                fontSize = 11.sp,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    onClick = {
                        onSelectPaper(entity.id)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun SegmentedTabSwitcher(
    questionCount: Int,
    candidateCount: Int,
    activeTab: String,
    onSelectTab: (String) -> Unit
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(0.8.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TabPillButton(
                label = "Item Analysis ($questionCount)",
                icon = Icons.Default.Assessment,
                isSelected = activeTab == "ITEMS",
                onClick = { onSelectTab("ITEMS") },
                modifier = Modifier.weight(1f)
            )
            TabPillButton(
                label = "Student Roster ($candidateCount)",
                icon = Icons.Default.Groups,
                isSelected = activeTab == "STUDENTS",
                onClick = { onSelectTab("STUDENTS") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabPillButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (isSelected) Color(0xFF0284C7) else Color.Transparent,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier.heightIn(min = 42.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else colors.muted,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) Color.White else colors.inkBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CohortSwitcherStrip(
    report: PsychometricEngine.CohortPsychometricReport,
    isSimulatedCohort: Boolean,
    useSimulationMode: Boolean,
    onToggle: () -> Unit
) {
    val colors = ExamTheme.colors
    val healthMessage = when {
        report.candidateCount == 0 -> "Live Mode • Awaiting Submissions"
        report.cronbachAlpha >= 0.80f -> "Strong Reliability • Well-Calibrated Set"
        report.cronbachAlpha >= 0.65f -> "Balanced Calibration • Ready for Review"
        else -> "Active Calibration Insights (${report.candidateCount} Cohort)"
    }
    val statusTint = when {
        report.candidateCount == 0 -> if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
        report.cronbachAlpha >= 0.80f -> if (colors.isDark) Color(0xFF34D399) else Color(0xFF059669)
        report.cronbachAlpha >= 0.65f -> colors.teal
        else -> if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    }

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = statusTint.copy(alpha = if (colors.isDark) 0.14f else 0.08f),
        border = BorderStroke(0.9.dp, statusTint.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("psychometrics_cohort_toggle")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = statusTint.copy(alpha = 0.16f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = statusTint,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = healthMessage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isSimulatedCohort) {
                            "Calibrated Simulation (${report.candidateCount} Candidates)"
                        } else if (report.candidateCount == 0) {
                            "Live Mode (0 Submissions Recorded)"
                        } else {
                            "Live Student Submissions (${report.candidateCount})"
                        },
                        fontSize = 10.5.sp,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.surface,
                border = BorderStroke(0.8.dp, statusTint.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = statusTint,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (useSimulationMode) "Switch to Live" else "Simulate 40",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusTint,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ExecutiveKpiGrid(
    report: PsychometricEngine.CohortPsychometricReport,
    isSimulatedCohort: Boolean,
    accentBlue: Color,
    accentTeal: Color,
    accentEmerald: Color,
    accentAmber: Color,
    accentRose: Color
) {
    val hasCohort = report.candidateCount > 0
    val alphaHealthy = report.cronbachAlpha >= 0.70f
    val alphaColor = when {
        !hasCohort -> accentBlue
        report.cronbachAlpha >= 0.80f -> accentEmerald
        alphaHealthy -> accentTeal
        else -> accentAmber
    }
    val alphaSub = when {
        !hasCohort -> "Awaiting Cohort"
        report.cronbachAlpha >= 0.80f -> "High Consistency"
        alphaHealthy -> "Good Balance"
        else -> "Moderate Variance"
    }
    val flagClear = report.flaggedItemCount == 0
    val flagColor = when {
        !hasCohort -> accentBlue
        flagClear -> accentEmerald
        else -> accentRose
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExecutiveMetricCard(
                label = "Candidates",
                value = "${report.candidateCount}",
                subtext = if (!hasCohort) "Live Submissions" else if (isSimulatedCohort) "Calibrated Cohort" else "Active Cohort",
                icon = Icons.Default.Groups,
                color = accentBlue,
                modifier = Modifier.weight(1f)
            )
            ExecutiveMetricCard(
                label = "Mean Score",
                value = String.format(Locale.US, "%.2f", report.meanScore),
                subtext = "±${String.format(Locale.US, "%.2f", report.standardDeviation)} SD Spread",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                color = accentTeal,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExecutiveMetricCard(
                label = "Reliability (α)",
                value = String.format(Locale.US, "%.2f", report.cronbachAlpha),
                subtext = alphaSub,
                icon = Icons.Default.Verified,
                color = alphaColor,
                modifier = Modifier.weight(1f)
            )
            ExecutiveMetricCard(
                label = if (flagClear) "Item Quality" else "Review Items",
                value = if (flagClear) "100%" else "${report.flaggedItemCount}",
                subtext = if (!hasCohort) "Items Ready" else if (flagClear) "All Items Healthy" else "Needs Attention",
                icon = if (flagClear) Icons.Default.CheckCircle else Icons.Default.Warning,
                color = flagColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ScoreDistributionStrip(
    report: PsychometricEngine.CohortPsychometricReport,
    maxPossibleMarks: Float
) {
    val colors = ExamTheme.colors
    val ceiling = maxPossibleMarks.toDouble().coerceAtLeast(report.highestScore).coerceAtLeast(1.0)
    val medianRatio = if (report.candidateCount == 0) 0f else (report.medianScore / ceiling).toFloat().coerceIn(0.08f, 1f)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        border = BorderStroke(0.8.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Low: ${String.format(Locale.US, "%.2f", report.lowestScore)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = colors.muted
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.teal.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Median ${String.format(Locale.US, "%.2f", report.medianScore)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = colors.teal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "High: ${String.format(Locale.US, "%.2f", report.highestScore)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = colors.green
                )
            }
            LinearProgressIndicator(
                progress = { medianRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = colors.teal,
                trackColor = colors.surfaceVariant
            )
        }
    }
}

@Composable
private fun ItemFilterChipsRow(
    report: PsychometricEngine.CohortPsychometricReport,
    itemFilter: String,
    onSelectFilter: (String) -> Unit
) {
    val colors = ExamTheme.colors
    val filters = listOf(
        "ALL" to "All (${report.items.size})",
        "FLAGGED" to "Flagged (${report.flaggedItemCount})",
        "HARD" to "Hard ≤0.35",
        "EASY" to "Easy ≥0.75",
        "DISCRIMINATING" to "Strong r≥0.30"
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(filters, key = { it.first }) { (key, label) ->
            val selected = itemFilter == key
            Surface(
                onClick = { onSelectFilter(key) },
                shape = RoundedCornerShape(9.dp),
                color = if (selected) Color(0xFF0284C7) else colors.surface,
                border = BorderStroke(
                    width = if (selected) 0.dp else 0.8.dp,
                    color = if (selected) Color.Transparent else colors.border
                )
            ) {
                Text(
                    text = label,
                    fontSize = 11.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else colors.inkBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyItemFilterCard(
    totalCount: Int,
    onShowAll: () -> Unit
) {
    val colors = ExamTheme.colors
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No items match this filter",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkBold
            )
            TextButton(onClick = onShowAll) {
                Text("Show All $totalCount Items", fontWeight = FontWeight.Bold, color = colors.teal)
            }
        }
    }
}

@Composable
private fun EmptyStudentRosterCard() {
    val colors = ExamTheme.colors
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = colors.teal.copy(alpha = 0.12f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = "Awaiting Live Candidate Submissions",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkBold
            )
            Text(
                text = "No live candidate test attempts recorded for this question set yet. Submissions completed in the Tests tab or scanned via OMR will immediately populate this roster and score matrix.",
                fontSize = 12.sp,
                color = colors.muted,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.widthIn(max = 420.dp)
            )
        }
    }
}

@Composable
private fun CompactExportButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White
        ),
        modifier = modifier.heightIn(min = 44.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ExecutiveMetricCard(
    label: String,
    value: String,
    subtext: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(0.8.dp, color.copy(alpha = 0.28f)),
        shadowElevation = if (colors.isDark) 0.dp else 1.dp,
        modifier = modifier.heightIn(min = 76.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(color.copy(alpha = 0.85f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = color.copy(alpha = 0.14f),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = value,
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Black,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Text(
                        text = subtext,
                        fontSize = 9.5.sp,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PsychometricItemCard(
    item: PsychometricEngine.ItemEvaluation,
    qModel: McqQuestion?,
    candidateCount: Int
) {
    val colors = ExamTheme.colors
    val officialKey = qModel?.correctAnswer?.uppercase()?.firstOrNull()?.toString() ?: ""
    var isExpanded by remember { mutableStateOf(false) }

    val cardAccent = when {
        item.isFlagged -> if (colors.isDark) Color(0xFFF87171) else Color(0xFFDC2626)
        item.pointBiserialR >= 0.35f -> if (colors.isDark) Color(0xFF34D399) else Color(0xFF059669)
        else -> if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isFlagged) {
                if (colors.isDark) Color(0xFF3F1D24) else Color(0xFFFEF2F2)
            } else {
                colors.surface
            }
        ),
        border = BorderStroke(
            width = if (item.isFlagged) 1.dp else 0.8.dp,
            color = if (item.isFlagged) cardAccent.copy(alpha = 0.65f) else colors.border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.isFlagged) Color(0xFFDC2626) else Color(0xFF0284C7)
                    ) {
                        Text(
                            text = String.format(Locale.US, "Q%03d", item.questionNum),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (officialKey.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.badgeSuccessBg,
                            border = BorderStroke(0.6.dp, colors.green.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Key: $officialKey",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.badgeSuccessText,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    val neutralBlue = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (candidateCount == 0) neutralBlue.copy(alpha = 0.13f) else colors.surfaceVariant
                    ) {
                        Text(
                            text = if (candidateCount == 0) "Awaiting Data" else item.difficultyLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (candidateCount == 0) neutralBlue else colors.inkBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    if (candidateCount > 0) {
                        val discColor = when {
                            item.discriminationD < 0f -> colors.red
                            item.discriminationD >= 0.30f -> colors.green
                            else -> colors.teal
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = discColor.copy(alpha = 0.13f)
                        ) {
                            Text(
                                text = item.discriminationLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = discColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (item.isFlagged) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.badgeDangerBg,
                            border = BorderStroke(0.6.dp, colors.red.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "Review",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.badgeDangerText,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse item details" else "Expand item details",
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (qModel != null && qModel.question.isNotBlank()) {
                Text(
                    text = qModel.question,
                    fontSize = 12.5.sp,
                    color = colors.inkBold,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.5.sp
                )
            }

            // 3 Non-Wrapping Metric Tiles with color-coded quality accents
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val neutralBlue = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                val pColor = when {
                    candidateCount == 0 -> neutralBlue
                    item.difficultyP >= 0.75f -> Color(0xFF10B981)
                    item.difficultyP <= 0.30f -> Color(0xFFEF4444)
                    else -> neutralBlue
                }
                val rColor = when {
                    candidateCount == 0 -> neutralBlue
                    item.pointBiserialR >= 0.35f -> Color(0xFF10B981)
                    item.pointBiserialR < 0.0f -> Color(0xFFEF4444)
                    else -> colors.teal
                }
                val dColor = when {
                    candidateCount == 0 -> neutralBlue
                    item.discriminationD >= 0.30f -> Color(0xFF10B981)
                    item.discriminationD < 0.0f -> Color(0xFFEF4444)
                    else -> colors.teal
                }

                ItemMetricTile(
                    label = "Difficulty (p)",
                    value = String.format(Locale.US, "%.2f", item.difficultyP),
                    accentColor = pColor,
                    modifier = Modifier.weight(1f)
                )
                ItemMetricTile(
                    label = "Point-Bis. r_pb",
                    value = String.format(Locale.US, "%.2f", item.pointBiserialR),
                    accentColor = rColor,
                    modifier = Modifier.weight(1f)
                )
                ItemMetricTile(
                    label = "Discrim. (D)",
                    value = String.format(Locale.US, "%.2f", item.discriminationD),
                    accentColor = dColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = if (candidateCount == 0) {
                    "Awaiting Live Submissions (0/0 attempts)"
                } else {
                    "Correct: ${item.correctCount}/$candidateCount • Skip: ${item.unattemptedCount} • ${item.pointBiserialLabel}"
                },
                fontSize = 11.sp,
                color = colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Distractors:",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.muted,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                item.optionFrequencies.keys.sorted().forEach { opt ->
                    val count = item.optionFrequencies[opt] ?: 0
                    val isCorrect = opt.toString().equals(officialKey, ignoreCase = true)
                    val isWeak = opt in item.nonFunctioningDistractors
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isCorrect -> colors.badgeSuccessBg
                            isWeak -> colors.badgeWarningBg
                            else -> colors.surfaceVariant
                        },
                        border = when {
                            isCorrect -> BorderStroke(0.7.dp, colors.green.copy(alpha = 0.55f))
                            isWeak -> BorderStroke(0.6.dp, colors.amber.copy(alpha = 0.45f))
                            else -> null
                        }
                    ) {
                        Text(
                            text = if (isCorrect) "$opt:$count ✓" else "$opt:$count",
                            fontSize = 10.5.sp,
                            fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                isCorrect -> colors.badgeSuccessText
                                isWeak -> colors.badgeWarningText
                                else -> colors.inkBold
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded && qModel != null) {
                if (qModel != null) {
                    val optionTextMap = mapOf(
                        'A' to qModel.options.a,
                        'B' to qModel.options.b,
                        'C' to qModel.options.c,
                        'D' to qModel.options.d,
                        'E' to qModel.options.e
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HorizontalDivider(color = colors.border, thickness = 0.6.dp)
                        item.optionFrequencies.keys.sorted().forEach { opt ->
                            val optText = optionTextMap[opt].orEmpty()
                            if (optText.isNotBlank() || (item.optionFrequencies[opt] ?: 0) > 0) {
                                val count = item.optionFrequencies[opt] ?: 0
                                val fraction = if (candidateCount > 0) (count.toFloat() / candidateCount).coerceIn(0f, 1f) else 0f
                                val pct = (fraction * 100).toInt()
                                val isCorrect = opt.toString().equals(officialKey, ignoreCase = true)
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (optText.isNotBlank()) "$opt. $optText" else "Option $opt",
                                            fontSize = 11.sp,
                                            fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCorrect) colors.green else colors.inkBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "$pct% ($count)",
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCorrect) colors.green else colors.muted
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = if (isCorrect) Color(0xFF10B981) else colors.teal.copy(alpha = 0.7f),
                                        trackColor = colors.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (item.isFlagged && !item.flagReason.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.badgeDangerBg,
                    border = BorderStroke(0.6.dp, colors.red.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = colors.red,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.flagReason,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.badgeDangerText,
                            softWrap = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemMetricTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(0.6.dp, accentColor.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(
                text = label,
                fontSize = 9.5.sp,
                color = colors.muted,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = accentColor,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentScorecardRowCard(
    candidate: PsychometricEngine.CandidateScorecard,
    onSelectCandidate: () -> Unit
) {
    val colors = ExamTheme.colors

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(0.8.dp, colors.border),
        shadowElevation = if (colors.isDark) 0.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectCandidate() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (candidate.rank <= 3) Color(0xFFF59E0B).copy(alpha = 0.18f) else colors.surfaceVariant,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "#${candidate.rank}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (candidate.rank <= 3) Color(0xFFD97706) else colors.inkBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = candidate.rollNumber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Booklet ${candidate.bookletNumber} • Set ${candidate.bookletCode}",
                            fontSize = 11.sp,
                            color = colors.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.14f),
                    border = BorderStroke(0.7.dp, Color(0xFF0284C7).copy(alpha = 0.45f)),
                    modifier = Modifier.clickable { onSelectCandidate() }
                ) {
                    Text(
                        text = "Report Card →",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Score: ${String.format(Locale.US, "%.2f", candidate.totalScore)}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = colors.teal
                )
                Text(
                    text = "Accuracy: ${candidate.accuracyPercent}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (candidate.accuracyPercent >= 70) Color(0xFF10B981) else colors.inkBold
                )
                Text(
                    text = "+${candidate.correctCount} / -${candidate.incorrectCount} / Skip ${candidate.unansweredCount}",
                    fontSize = 11.5.sp,
                    color = colors.muted
                )
            }
        }
    }
}

/**
 * In-activity overlay modal for inspecting and exporting an individual student report card.
 */
@Composable
private fun StudentReportCardOverlay(
    candidate: PsychometricEngine.CandidateScorecard,
    reportCardText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = ExamTheme.colors
    var copied by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.2.dp, colors.border),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Student Examination Report Card",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            maxLines = 2,
                            softWrap = true
                        )
                        Text(
                            text = "Roll: ${candidate.rollNumber} • Booklet: ${candidate.bookletNumber} (Set ${candidate.bookletCode})",
                            fontSize = 11.5.sp,
                            color = colors.muted,
                            maxLines = 2,
                            softWrap = true
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.inkBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant,
                    border = BorderStroke(0.8.dp, colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = reportCardText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.inkBold,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Report Card ${candidate.rollNumber}", reportCardText))
                            copied = true
                            Toast.makeText(context, "Copied Report Card!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (copied) Color(0xFF059669) else Color(0xFF0284C7),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copied) "Copied Card!" else "Copy Card", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, reportCardText)
                                type = "text/plain"
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(sendIntent, "Share Student Report Card")
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Card", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
