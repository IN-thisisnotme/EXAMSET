package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.QuestionPaper
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme
import com.example.util.PsychometricEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standardized Psychometric & Classical Test Theory (CTT) Evaluation Dialog.
 *
 * Dedicated tool for Course Chief Question Paper Setters and Psychometricians to:
 * - Evaluate item difficulty (p-value) and point-biserial discrimination (r_pb).
 * - Detect defective questions, key discrepancies, and non-functioning distractors.
 * - Review student candidate rankings, roll numbers, booklet numbers, and scorecards.
 * - Export standardized CSV analysis reports for exam boards.
 */
@Composable
fun PsychometricAnalysisDialog(
    paper: QuestionPaper,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = ExamTheme.colors

    var useSimulationMode by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("ITEMS") } // "ITEMS" or "STUDENTS"
    var itemFilter by remember { mutableStateOf("ALL") } // "ALL", "FLAGGED", "HARD", "EASY", "DISCRIMINATING"
    var copiedReport by remember { mutableStateOf(false) }
    var selectedCandidateForReportCard by remember { mutableStateOf<PsychometricEngine.CandidateScorecard?>(null) }

    // Generate psychometric report (switches dynamically between scanned data and simulated cohort)
    val report = remember(paper, useSimulationMode) {
        viewModel.generatePsychometricReport(allowSimulationFallback = useSimulationMode)
            ?: viewModel.generatePsychometricReport(allowSimulationFallback = true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.2.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Top Header: Title, Paper Name, and Close Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Psychometric & Item Analysis",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = BorderStroke(0.6.dp, Color(0xFF16A34A))
                                ) {
                                    Text(
                                        text = "CTT ENGINE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF16A34A),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Course Chief Paper Setter Suite • ${paper.metadata.title.ifBlank { "Active Assessment" }}",
                                fontSize = 11.5.sp,
                                color = colors.muted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("psychometrics_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.inkBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(12.dp))

                if (report == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No questions found in active paper to analyze.",
                            fontSize = 13.sp,
                            color = colors.muted
                        )
                    }
                } else {
                    // Executive Metrics Cards Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExecutiveMetricCard(
                            label = "Candidates",
                            value = "${report.candidateCount}",
                            subtext = if (report.candidates.any { it.rollNumber.startsWith("202610") }) "Calibrated Cohort" else "Scanned Students",
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
                        ExecutiveMetricCard(
                            label = "Reliability (α)",
                            value = String.format(Locale.US, "%.2f", report.cronbachAlpha),
                            subtext = if (report.cronbachAlpha >= 0.8f) "High Consistency" else "Moderate",
                            color = if (report.cronbachAlpha >= 0.8f) Color(0xFF059669) else Color(0xFFD97706),
                            modifier = Modifier.weight(1f)
                        )
                        ExecutiveMetricCard(
                            label = "Flagged Items",
                            value = "${report.flaggedItemCount}",
                            subtext = if (report.flaggedItemCount > 0) "Requires Review" else "Clean Instrument",
                            color = if (report.flaggedItemCount > 0) Color(0xFFDC2626) else Color(0xFF059669),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mode Toggle & Tab Navigation Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // View Switcher (Item Analysis vs Student Scorecards)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TabButton(
                                label = "Item Analysis (${report.questionCount})",
                                isSelected = activeTab == "ITEMS",
                                onClick = { activeTab = "ITEMS" }
                            )
                            TabButton(
                                label = "Student Roster (${report.candidateCount})",
                                isSelected = activeTab == "STUDENTS",
                                onClick = { activeTab = "STUDENTS" }
                            )
                        }

                        // Simulation / Real Data Toggle
                        OutlinedButton(
                            onClick = { useSimulationMode = !useSimulationMode },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, colors.border),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (useSimulationMode) "Using Simulated Cohort" else "Simulate 40 Students",
                                fontSize = 11.sp,
                                color = colors.inkBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tab Body
                    when (activeTab) {
                        "ITEMS" -> {
                            ItemAnalysisTabContent(
                                report = report,
                                paper = paper,
                                itemFilter = itemFilter,
                                onSelectFilter = { itemFilter = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        "STUDENTS" -> {
                            StudentScorecardsTabContent(
                                report = report,
                                onSelectCandidate = { selectedCandidateForReportCard = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Footer Action Bar: Copy CTT CSV, Copy Roster CSV, Export All Cards, Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val csv = viewModel.exportPsychometricReportCsv(allowSimulationFallback = useSimulationMode)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Psychometric Report CSV", csv)
                                clipboard.setPrimaryClip(clip)
                                copiedReport = true
                                Toast.makeText(context, "Copied Item Analysis CSV to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copiedReport) Color(0xFF059669) else Color(0xFF0284C7)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (copiedReport) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (copiedReport) "Copied!" else "Item CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val csv = viewModel.exportCandidateScorecardsCsv(allowSimulationFallback = useSimulationMode)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Student Scorecards CSV", csv)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied Student Scorecards CSV to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Roster CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val cardsText = viewModel.exportAllStudentReportCardsText(allowSimulationFallback = useSimulationMode)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("All Student Report Cards", cardsText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied all student report cards to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBE185D)),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("All Cards", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val csv = viewModel.exportPsychometricReportCsv(allowSimulationFallback = useSimulationMode)
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, csv)
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Psychometric Report CSV")
                                context.startActivity(shareIntent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Share", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Student Report Card Modal (Individual Candidate View)
        if (selectedCandidateForReportCard != null) {
            val candidate = selectedCandidateForReportCard!!
            val reportCardText = viewModel.exportSingleStudentReportCard(
                candidate = candidate,
                allowSimulationFallback = useSimulationMode
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
        color = colors.surfaceVariant,
        border = BorderStroke(0.8.dp, colors.border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = colors.muted)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = color, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtext, fontSize = 9.5.sp, color = colors.muted, maxLines = 1)
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF0284C7) else colors.surfaceVariant,
        border = BorderStroke(0.8.dp, if (isSelected) Color(0xFF0284C7) else colors.border),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else colors.inkBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ItemAnalysisTabContent(
    report: PsychometricEngine.CohortPsychometricReport,
    paper: QuestionPaper,
    itemFilter: String,
    onSelectFilter: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors

    val filteredItems = report.items.filter { item ->
        when (itemFilter) {
            "FLAGGED" -> item.isFlagged
            "HARD" -> item.difficultyP <= 0.35f
            "EASY" -> item.difficultyP >= 0.75f
            "DISCRIMINATING" -> item.pointBiserialR >= 0.30f || item.discriminationD >= 0.30f
            else -> true
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Filter Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filters = listOf(
                "ALL" to "All (${report.items.size})",
                "FLAGGED" to "Flagged (${report.flaggedItemCount})",
                "HARD" to "Hard (p≤0.35)",
                "EASY" to "Easy (p≥0.75)",
                "DISCRIMINATING" to "High Disc."
            )
            filters.forEach { (key, label) ->
                val selected = itemFilter == key
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (selected) Color(0xFF0284C7).copy(alpha = 0.15f) else Color.Transparent,
                    border = BorderStroke(0.8.dp, if (selected) Color(0xFF0284C7) else colors.border),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectFilter(key) }
                ) {
                    Box(modifier = Modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Color(0xFF0284C7) else colors.inkMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Table Header
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = colors.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Q# & QUESTION STEM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.muted, modifier = Modifier.weight(2.2f))
                Text("DIFFICULTY (p)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.muted, modifier = Modifier.weight(1.1f))
                Text("DISCRIM. (D / r_pb)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.muted, modifier = Modifier.weight(1.3f))
                Text("DISTRACTORS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.muted, modifier = Modifier.weight(1.2f))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Item List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredItems) { item ->
                val qModel = paper.questions.getOrNull(item.questionNum - 1)
                val officialKey = qModel?.correctAnswer?.uppercase()?.firstOrNull()?.toString() ?: ""

                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.isFlagged) Color(0xFFFEF2F2) else colors.surface
                    ),
                    border = BorderStroke(
                        0.8.dp,
                        if (item.isFlagged) Color(0xFFEF4444) else colors.border
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Question Number and Stem
                            Column(modifier = Modifier.weight(2.2f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = String.format(Locale.US, "Q%03d", item.questionNum),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (item.isFlagged) Color(0xFFDC2626) else colors.inkBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = Color(0xFFF3F4F6)
                                    ) {
                                        Text(
                                            text = "Key: $officialKey",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (qModel != null) {
                                    Text(
                                        text = qModel.question,
                                        fontSize = 11.sp,
                                        color = colors.inkMuted,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Difficulty (p-value)
                            Column(modifier = Modifier.weight(1.1f)) {
                                Text(
                                    text = "p = ${String.format(Locale.US, "%.2f", item.difficultyP)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        item.difficultyP >= 0.75f -> Color(0xFF059669)
                                        item.difficultyP <= 0.30f -> Color(0xFFDC2626)
                                        else -> colors.inkBold
                                    }
                                )
                                Text(item.difficultyLabel, fontSize = 9.5.sp, color = colors.muted)
                            }

                            // Discrimination (D and r_pb)
                            Column(modifier = Modifier.weight(1.3f)) {
                                Text(
                                    text = "r_pb: ${String.format(Locale.US, "%.2f", item.pointBiserialR)}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        item.pointBiserialR >= 0.35f -> Color(0xFF059669)
                                        item.pointBiserialR < 0.0f -> Color(0xFFDC2626)
                                        else -> colors.inkBold
                                    }
                                )
                                Text(
                                    text = "D = ${String.format(Locale.US, "%.2f", item.discriminationD)}",
                                    fontSize = 10.sp,
                                    color = colors.muted
                                )
                            }

                            // Distractor Frequencies
                            Column(modifier = Modifier.weight(1.2f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf('A', 'B', 'C', 'D').forEach { opt ->
                                        val count = item.optionFrequencies[opt] ?: 0
                                        val isCorrect = opt.toString().equals(officialKey, ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = if (isCorrect) Color(0xFFDCFCE7) else Color(0xFFF3F4F6)
                                        ) {
                                            Text(
                                                text = "$opt:$count",
                                                fontSize = 9.sp,
                                                fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCorrect) Color(0xFF15803D) else colors.inkMuted,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Flagged Item Quality Alert
                        if (item.isFlagged && item.flagReason != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(0.6.dp, Color(0xFFF87171))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = item.flagReason,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFB91C1C)
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
private fun StudentScorecardsTabContent(
    report: PsychometricEngine.CohortPsychometricReport,
    onSelectCandidate: (PsychometricEngine.CandidateScorecard) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        // Table Header
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = colors.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RANK & ROLL NUMBER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.muted, modifier = Modifier.weight(1.8f))
                Text("BOOKLET NO. & SET", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.muted, modifier = Modifier.weight(1.4f))
                Text("SCORE / MAX", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.muted, modifier = Modifier.weight(1.1f))
                Text("ACCURACY & REPORT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.muted, modifier = Modifier.weight(1.7f))
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Student List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(report.candidates) { c ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surface,
                    border = BorderStroke(0.6.dp, colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCandidate(c) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rank and Roll Number
                        Row(
                            modifier = Modifier.weight(1.8f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (c.rank <= 3) Color(0xFFFEF3C7) else Color(0xFFF3F4F6),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${c.rank}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (c.rank <= 3) Color(0xFFB45309) else colors.inkBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = c.rollNumber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF0284C7)
                            )
                        }

                        // Booklet Number & Set Code
                        Row(
                            modifier = Modifier.weight(1.4f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = c.bookletNumber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFBE185D)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0xFFFDF2F8)
                            ) {
                                Text(
                                    text = "Set ${c.bookletCode}",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFBE185D),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        // Score
                        Column(modifier = Modifier.weight(1.1f)) {
                            Text(
                                text = "${c.totalScore.toInt()}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = if (c.totalScore > 0) colors.teal else colors.inkBold
                            )
                        }

                        // Accuracy & Report Card Action Badge
                        Row(
                            modifier = Modifier.weight(1.7f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${c.accuracyPercent}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (c.accuracyPercent >= 70) Color(0xFF059669) else colors.inkBold
                                )
                                Text(
                                    text = "+${c.correctCount} -${c.incorrectCount}",
                                    fontSize = 9.sp,
                                    color = colors.muted
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.1f),
                                border = BorderStroke(0.6.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { onSelectCandidate(c) }
                            ) {
                                Text(
                                    text = "Card →",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0284C7),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
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
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.2.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Student Examination Report Card",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Text(
                            text = "Roll: ${candidate.rollNumber} • Booklet: ${candidate.bookletNumber} (Set ${candidate.bookletCode})",
                            fontSize = 11.5.sp,
                            color = colors.muted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.inkBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Monospace Report Card Text
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

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Student Report Card", reportCardText)
                            clipboard.setPrimaryClip(clip)
                            copied = true
                            Toast.makeText(context, "Copied Report Card to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (copied) Color(0xFF059669) else Color(0xFF0284C7)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copied) "Copied!" else "Copy Report Card", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, reportCardText)
                                type = "text/plain"
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Student Report Card")
                            context.startActivity(shareIntent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
