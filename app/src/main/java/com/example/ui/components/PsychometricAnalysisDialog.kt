package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.toQuestionPaper
import com.example.model.QuestionPaper
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme
import com.example.util.PsychometricEngine
import java.util.Locale

/**
 * Standardized Psychometric & Classical Test Theory (CTT) Evaluation Page.
 *
 * Designed as an adaptive, font-scale-resilient workbench for:
 * - Item difficulty (p-value), discrimination (D & point-biserial r_pb), and distractor frequency analysis
 * - Detecting defective questions, negative point-biserial flags, or non-functioning distractors
 * - Inspecting candidate rankings, roll/booklet numbers, and individual report cards
 * - Exporting board-ready CSV reports and candidate scorecards
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

    val report = remember(effectivePaper, useSimulationMode, selectedPaperEntityId) {
        if (effectivePaper == null || effectivePaper.questions.isEmpty()) {
            null
        } else {
            viewModel.generatePsychometricReport(
                targetPaperId = selectedPaperEntityId,
                paperOverride = effectivePaper,
                forceSimulation = useSimulationMode,
                allowSimulationFallback = true
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = colors.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 920.dp)
                ) {
                    // 1. Crisp Adaptive Top App Header (Never truncates or wraps badge awkwardly)
                    Surface(
                        color = colors.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.35f)),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Assessment,
                                            contentDescription = null,
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Psychometric & Item Analysis",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 2,
                                        softWrap = true,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 21.sp
                                    )
                                }

                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("psychometrics_close_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = colors.inkBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Engine Badge + Interactive Exam Set Selector in adaptive FlowRow
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF16A34A).copy(alpha = 0.13f),
                                    border = BorderStroke(0.7.dp, Color(0xFF16A34A).copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "CTT ENGINE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (colors.isDark) Color(0xFF4ADE80) else Color(0xFF15803D),
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }

                                Box {
                                    Surface(
                                        onClick = { if (savedPapers.isNotEmpty()) showPaperPickerDropdown = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = colors.surfaceVariant,
                                        border = BorderStroke(0.8.dp, colors.border),
                                        modifier = Modifier.testTag("psychometrics_paper_picker")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                tint = colors.teal,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = effectivePaper?.metadata?.title?.ifBlank { "Choose Exam Set" } ?: "Choose Exam Set",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.inkBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 220.dp)
                                            )
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
                                        expanded = showPaperPickerDropdown,
                                        onDismissRequest = { showPaperPickerDropdown = false },
                                        modifier = Modifier.heightIn(max = 380.dp)
                                    ) {
                                        savedPapers.forEach { entity ->
                                            val isCurrentSelection = entity.id == selectedPaperEntityId ||
                                                (selectedPaperEntityId == null && effectivePaper?.metadata?.title == entity.title)
                                            DropdownMenuItem(
                                                leadingIcon = {
                                                    if (isCurrentSelection) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Selected",
                                                            tint = colors.teal,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.FolderOpen,
                                                            contentDescription = null,
                                                            tint = colors.muted,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
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
                                                    selectedPaperEntityId = entity.id
                                                    showPaperPickerDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colors.border, thickness = 0.8.dp)

                    // 2. Main Body
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
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = colors.surfaceVariant,
                                    modifier = Modifier.size(60.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Assessment,
                                            contentDescription = null,
                                            tint = colors.muted,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "No Questions Available to Analyze",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Select a saved question set above or load the Science Sample to inspect Classical Test Theory item difficulty, discrimination, and candidate scorecards.",
                                    fontSize = 12.5.sp,
                                    color = colors.muted,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                Button(
                                    onClick = { viewModel.loadStarterSample("science") },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Analyze Science Sample", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        val isSimulatedCohort = report.candidates.any { it.rollNumber.startsWith("202610") }

                        // Pinned Segmented Mode Tabs + Full-Label Cohort Switcher (Never truncates to "S")
                        Surface(
                            color = colors.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TabButton(
                                        label = "Item Analysis (${report.questionCount})",
                                        isSelected = activeTab == "ITEMS",
                                        onClick = { activeTab = "ITEMS" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    TabButton(
                                        label = "Student Roster (${report.candidateCount})",
                                        isSelected = activeTab == "STUDENTS",
                                        onClick = { activeTab = "STUDENTS" },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Surface(
                                    onClick = { useSimulationMode = !useSimulationMode },
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.surfaceVariant,
                                    border = BorderStroke(0.8.dp, colors.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("psychometrics_cohort_toggle")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = colors.teal,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isSimulatedCohort) {
                                                    "Cohort: Simulated Calibration (${report.candidateCount})"
                                                } else {
                                                    "Cohort: Real Scanned / Attempts (${report.candidateCount})"
                                                },
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.inkBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (useSimulationMode) "Use Real" else "Simulate 40",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.teal,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = colors.border, thickness = 0.6.dp)

                        // Scrollable Analytical Body (Metrics Grid + Filters + Item / Student Cards)
                        // Putting the 2x2 KPI Grid inside LazyColumn ensures items ALWAYS stay reachable & never vanish at 125%-135% font scale!
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

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Executive Metrics 2x2 Grid
                            item(key = "kpi_grid") {
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
                                            subtext = if (isSimulatedCohort) "Calibrated Cohort" else "Scanned Cohort",
                                            color = Color(0xFF0284C7),
                                            modifier = Modifier.weight(1f)
                                        )
                                        ExecutiveMetricCard(
                                            label = "Mean Score",
                                            value = String.format(Locale.US, "%.1f", report.meanScore),
                                            subtext = "±${String.format(Locale.US, "%.1f", report.standardDeviation)} SD",
                                            color = colors.teal,
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
                                            subtext = if (report.cronbachAlpha >= 0.8f) "High Consistency" else "Moderate",
                                            color = if (report.cronbachAlpha >= 0.8f) Color(0xFF10B981) else Color(0xFFF59E0B),
                                            modifier = Modifier.weight(1f)
                                        )
                                        ExecutiveMetricCard(
                                            label = "Flagged Items",
                                            value = "${report.flaggedItemCount}",
                                            subtext = if (report.flaggedItemCount > 0) "Requires Review" else "Clean Instrument",
                                            color = if (report.flaggedItemCount > 0) Color(0xFFEF4444) else Color(0xFF10B981),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            if (activeTab == "ITEMS") {
                                // Horizontally scrollable single-line Filter Chips (never wrap mid-chip!)
                                item(key = "item_filters_row") {
                                    val filters = listOf(
                                        "ALL" to "All (${report.items.size})",
                                        "FLAGGED" to "Flagged (${report.flaggedItemCount})",
                                        "HARD" to "Hard (p ≤ 0.35)",
                                        "EASY" to "Easy (p ≥ 0.75)",
                                        "DISCRIMINATING" to "High Disc. (r ≥ 0.30)"
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(filters, key = { it.first }) { (key, label) ->
                                            val selected = itemFilter == key
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (selected) Color(0xFF0284C7).copy(alpha = 0.16f) else colors.surface,
                                                border = BorderStroke(
                                                    width = if (selected) 1.2.dp else 0.8.dp,
                                                    color = if (selected) Color(0xFF0284C7) else colors.border
                                                ),
                                                modifier = Modifier.clickable { itemFilter = key }
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (selected) Color(0xFF38BDF8).takeIf { colors.isDark } ?: Color(0xFF0284C7) else colors.inkBold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (filteredItems.isEmpty()) {
                                    item(key = "empty_filter") {
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
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
                                                TextButton(onClick = { itemFilter = "ALL" }) {
                                                    Text("Show All ${report.items.size} Items", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    items(filteredItems, key = { "item_${it.questionNum}" }) { item ->
                                        val qModel = effectivePaper.questions.getOrNull(item.questionNum - 1)
                                        PsychometricItemCard(item = item, qModel = qModel)
                                    }
                                }
                            } else {
                                if (report.candidates.isEmpty()) {
                                    item(key = "empty_students") {
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
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
                                                Text(
                                                    text = "No student scorecards yet",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.inkBold
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(report.candidates, key = { "${it.rank}_${it.rollNumber}_${it.bookletNumber}" }) { c ->
                                        StudentScorecardRowCard(
                                            candidate = c,
                                            onSelectCandidate = { selectedCandidateForReportCard = c }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Slim Responsive Export Bar (Always readable at any font scale)
                        Surface(
                            color = colors.surface,
                            shadowElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    maxItemsInEachRow = 2
                                ) {
                                    Button(
                                        onClick = {
                                            val csv = viewModel.exportPsychometricReportCsv(
                                                paperOverride = effectivePaper,
                                                forceSimulation = useSimulationMode,
                                                allowSimulationFallback = true
                                            )
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Psychometric Report CSV", csv))
                                            copiedReport = true
                                            Toast.makeText(context, "Copied Item Analysis CSV!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (copiedReport) Color(0xFF059669) else Color(0xFF0284C7),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 42.dp)
                                    ) {
                                        Icon(
                                            if (copiedReport) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (copiedReport) "Copied CSV" else "Item CSV",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            softWrap = true,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val csv = viewModel.exportCandidateScorecardsCsv(
                                                paperOverride = effectivePaper,
                                                forceSimulation = useSimulationMode,
                                                allowSimulationFallback = true
                                            )
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Student Scorecards CSV", csv))
                                            Toast.makeText(context, "Copied Student Roster CSV!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF059669),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 42.dp)
                                    ) {
                                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Roster CSV",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            softWrap = true,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val cardsText = viewModel.exportAllStudentReportCardsText(
                                                paperOverride = effectivePaper,
                                                forceSimulation = useSimulationMode,
                                                allowSimulationFallback = true
                                            )
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("All Student Report Cards", cardsText))
                                            Toast.makeText(context, "Copied all report cards!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFBE185D),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 42.dp)
                                    ) {
                                        Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "All Cards",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            softWrap = true,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val csv = viewModel.exportPsychometricReportCsv(
                                                paperOverride = effectivePaper,
                                                forceSimulation = useSimulationMode,
                                                allowSimulationFallback = true
                                            )
                                            val sendIntent = android.content.Intent().apply {
                                                action = android.content.Intent.ACTION_SEND
                                                putExtra(android.content.Intent.EXTRA_TEXT, csv)
                                                type = "text/plain"
                                            }
                                            context.startActivity(
                                                android.content.Intent.createChooser(sendIntent, "Share Psychometric Report CSV")
                                            )
                                        },
                                        shape = RoundedCornerShape(9.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 42.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Share",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            softWrap = true,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedCandidateForReportCard != null && effectivePaper != null) {
            val candidate = selectedCandidateForReportCard!!
            val reportCardText = viewModel.exportSingleStudentReportCard(
                candidate = candidate,
                paperOverride = effectivePaper,
                forceSimulation = useSimulationMode,
                allowSimulationFallback = true
            )
            StudentReportCardModal(
                candidate = candidate,
                reportCardText = reportCardText,
                onDismiss = { selectedCandidateForReportCard = null }
            )
        }
    }
}

@Composable
private fun ExecutiveMetricCard(
    label: String,
    value: String,
    subtext: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        border = BorderStroke(0.8.dp, colors.border),
        modifier = modifier.heightIn(min = 66.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = color,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                fontSize = 10.sp,
                color = colors.muted,
                maxLines = 2,
                softWrap = true,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (isSelected) Color(0xFF0284C7) else colors.surfaceVariant,
        border = BorderStroke(0.8.dp, if (isSelected) Color(0xFF0284C7) else colors.border),
        modifier = modifier
            .heightIn(min = 42.dp)
            .clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else colors.inkBold,
                maxLines = 2,
                softWrap = true,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PsychometricItemCard(
    item: PsychometricEngine.ItemEvaluation,
    qModel: com.example.model.McqQuestion?
) {
    val colors = ExamTheme.colors
    val officialKey = qModel?.correctAnswer?.uppercase()?.firstOrNull()?.toString() ?: ""

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isFlagged) {
                if (colors.isDark) Color(0xFF3F1D24) else Color(0xFFFEF2F2)
            } else {
                colors.surface
            }
        ),
        border = BorderStroke(
            width = if (item.isFlagged) 1.dp else 0.8.dp,
            color = if (item.isFlagged) Color(0xFFEF4444).copy(alpha = 0.7f) else colors.border
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Badges (FlowRow prevents squeezing at 125%-135% font sizes)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = if (item.isFlagged) Color(0xFFDC2626) else Color(0xFF0284C7)
                ) {
                    Text(
                        text = String.format(Locale.US, "Q%03d", item.questionNum),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                if (officialKey.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(0.6.dp, colors.border)
                    ) {
                        Text(
                            text = "Key: $officialKey",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = colors.surfaceVariant
                ) {
                    Text(
                        text = item.difficultyLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                if (item.isFlagged) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0xFFDC2626).copy(alpha = 0.14f),
                        border = BorderStroke(0.6.dp, Color(0xFFEF4444))
                    ) {
                        Text(
                            text = "Flagged",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (colors.isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (qModel != null && qModel.question.isNotBlank()) {
                Text(
                    text = qModel.question,
                    fontSize = 12.5.sp,
                    color = colors.inkBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
            }

            // Metrics Pair (Difficulty & Discrimination)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            text = "Difficulty (p)",
                            fontSize = 10.sp,
                            color = colors.muted,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "p = ${String.format(Locale.US, "%.2f", item.difficultyP)}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                item.difficultyP >= 0.75f -> Color(0xFF10B981)
                                item.difficultyP <= 0.30f -> Color(0xFFEF4444)
                                else -> colors.inkBold
                            }
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            text = "Discrimination",
                            fontSize = 10.sp,
                            color = colors.muted,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "r_pb ${String.format(Locale.US, "%.2f", item.pointBiserialR)} • D ${String.format(Locale.US, "%.2f", item.discriminationD)}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                item.pointBiserialR >= 0.35f -> Color(0xFF10B981)
                                item.pointBiserialR < 0.0f -> Color(0xFFEF4444)
                                else -> colors.inkBold
                            }
                        )
                    }
                }
            }

            // Distractor Frequencies in FlowRow (handles A-D and 5-option A-E gracefully)
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
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (isCorrect) Color(0xFF10B981).copy(alpha = 0.16f) else colors.surfaceVariant,
                        border = if (isCorrect) BorderStroke(0.7.dp, Color(0xFF10B981).copy(alpha = 0.6f)) else null
                    ) {
                        Text(
                            text = if (isCorrect) "$opt:$count ✓" else "$opt:$count",
                            fontSize = 10.5.sp,
                            fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = if (isCorrect) {
                                if (colors.isDark) Color(0xFF6EE7B7) else Color(0xFF047857)
                            } else {
                                colors.inkMuted
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Flagged Quality Reason
            if (item.isFlagged && !item.flagReason.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, Color(0xFFEF4444).copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.flagReason,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (colors.isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                            softWrap = true
                        )
                    }
                }
            }
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
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        border = BorderStroke(0.8.dp, colors.border),
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
                        modifier = Modifier.size(28.dp)
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
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.14f),
                    border = BorderStroke(0.7.dp, Color(0xFF0284C7).copy(alpha = 0.45f)),
                    modifier = Modifier.clickable { onSelectCandidate() }
                ) {
                    Text(
                        text = "Card →",
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
                    text = "Score: ${String.format(Locale.US, "%.1f", candidate.totalScore)}",
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
                    text = "+${candidate.correctCount} / -${candidate.incorrectCount}",
                    fontSize = 11.5.sp,
                    color = colors.muted
                )
            }
        }
    }
}

/**
 * Modal dialog for inspecting and exporting an individual student examination report card.
 */
@Composable
private fun StudentReportCardModal(
    candidate: PsychometricEngine.CandidateScorecard,
    reportCardText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = ExamTheme.colors
    var copied by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.2.dp, colors.border)
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Student Report Card", reportCardText))
                            copied = true
                            Toast.makeText(context, "Copied Report Card to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (copied) Color(0xFF059669) else Color(0xFF0284C7),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (copied) "Copied!" else "Copy Card",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
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
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
