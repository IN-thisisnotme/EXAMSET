package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AuditIssueType
import com.example.model.AuditQuestionReport
import com.example.model.McqQuestion
import com.example.model.QuestionPaper
import com.example.ui.theme.ExamTheme
import com.example.util.QuestionAuditHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuditResolutionHubDialog(
    paper: QuestionPaper,
    initialFeedbackText: String? = null,
    onDismiss: () -> Unit,
    onUpdateQuestion: (McqQuestion) -> Unit,
    onDeleteQuestion: (Int) -> Unit,
    onBatchUpdateKeys: (List<Pair<Int, Pair<String, String>>>) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colors = ExamTheme.colors

    var feedbackInput by remember { mutableStateOf(initialFeedbackText ?: "") }
    var isInputExpanded by remember { mutableStateOf(initialFeedbackText.isNullOrBlank()) }
    var activeTab by remember { mutableStateOf("ALL") } // "ALL", "WRONG_KEYS", "MISSING_KEYS", "FLAWED", "CLEAN"

    // Audit report items mapped to question paper
    var reports by remember {
        mutableStateOf<List<AuditQuestionReport>>(
            if (!initialFeedbackText.isNullOrBlank()) {
                QuestionAuditHelper.parseAuditReport(initialFeedbackText, paper)
            } else {
                QuestionAuditHelper.runHeuristicPreAudit(paper)
            }
        )
    }

    // Safety Confirmation Dialog for deletion
    var questionToDelete by remember { mutableStateOf<McqQuestion?>(null) }

    // Recompute metrics dynamically
    val metrics by remember(reports) {
        derivedStateOf { QuestionAuditHelper.computeMetrics(reports) }
    }

    val unresolvedWrongKeys by remember(reports) {
        derivedStateOf {
            reports.filter {
                (it.status == AuditIssueType.WRONG_KEY || it.status == AuditIssueType.MISSING_KEY) &&
                        !it.suggestedKey.isNullOrBlank() &&
                        !it.isResolved
            }
        }
    }

    val filteredReports by remember(reports, activeTab) {
        derivedStateOf {
            when (activeTab) {
                "WRONG_KEYS" -> reports.filter { it.status == AuditIssueType.WRONG_KEY }
                "MISSING_KEYS" -> reports.filter { it.status == AuditIssueType.MISSING_KEY }
                "FLAWED" -> reports.filter {
                    it.status == AuditIssueType.FLAWED_QUESTION ||
                            it.status == AuditIssueType.VAGUE_OPTIONS ||
                            it.status == AuditIssueType.MISSING_OPTIONS
                }
                "CLEAN" -> reports.filter { it.status == AuditIssueType.CLEAN || it.status == AuditIssueType.MISSING_EXPLANATION }
                else -> reports
            }
        }
    }

    fun parseInput() {
        if (feedbackInput.isBlank()) {
            reports = QuestionAuditHelper.runHeuristicPreAudit(paper)
            Toast.makeText(context, "Running heuristic pre-audit check...", Toast.LENGTH_SHORT).show()
        } else {
            reports = QuestionAuditHelper.parseAuditReport(feedbackInput, paper)
            isInputExpanded = false
            Toast.makeText(context, "Parsed audit feedback for ${reports.size} questions", Toast.LENGTH_SHORT).show()
        }
    }

    fun applyKeyAndExplanationFix(report: AuditQuestionReport) {
        val original = report.originalQuestion ?: paper.questions.find { it.num == report.qNum } ?: return
        val newKey = report.suggestedKey ?: original.correctAnswer ?: "A"
        val newExp = report.recheckedExplanation ?: original.explanation

        val updatedQ = original.copy(
            correctAnswer = newKey,
            explanation = newExp
        )
        onUpdateQuestion(updatedQ)

        // Mark report item as resolved
        reports = reports.map {
            if (it.qNum == report.qNum) it.copy(isResolved = true, status = AuditIssueType.CLEAN) else it
        }
        Toast.makeText(context, "Q${report.qNum}: Updated key to $newKey and rechecked explanation", Toast.LENGTH_SHORT).show()
    }

    fun applyQuestionReplacement(report: AuditQuestionReport) {
        val replacement = report.replacementQuestion ?: return
        // Atomically replace question stem, options, key, and new explanation
        val atomicReplacement = replacement.copy(
            num = report.qNum
        )
        onUpdateQuestion(atomicReplacement)

        reports = reports.map {
            if (it.qNum == report.qNum) it.copy(isResolved = true, status = AuditIssueType.CLEAN) else it
        }
        Toast.makeText(context, "Q${report.qNum}: Replaced with verified question and matching explanation", Toast.LENGTH_SHORT).show()
    }

    fun applyBatchKeyFixes() {
        val fixesToApply = unresolvedWrongKeys.mapNotNull { report ->
            val key = report.suggestedKey ?: return@mapNotNull null
            val exp = report.recheckedExplanation ?: ""
            report.qNum to (key to exp)
        }
        if (fixesToApply.isEmpty()) return

        onBatchUpdateKeys(fixesToApply)
        val resolvedNums = fixesToApply.map { it.first }.toSet()
        reports = reports.map {
            if (it.qNum in resolvedNums) it.copy(isResolved = true, status = AuditIssueType.CLEAN) else it
        }
        Toast.makeText(context, "Batch applied ${fixesToApply.size} verified answer keys & explanations", Toast.LENGTH_LONG).show()
    }

    // Safety Deletion Dialog
    if (questionToDelete != null) {
        val q = questionToDelete!!
        AlertDialog(
            onDismissRequest = { questionToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = colors.red,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Question Q${q.num}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to permanently delete this flawed question from the examination set?",
                        fontSize = 13.sp,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                        border = BorderStroke(0.6.dp, colors.border)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Q${q.num}. ${q.question}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• All remaining questions will be automatically renumbered sequentially (1 to ${paper.questions.size - 1}).\n• The question paper will be automatically saved.",
                        fontSize = 11.5.sp,
                        color = colors.teal,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val numToDelete = q.num
                        questionToDelete = null
                        onDeleteQuestion(numToDelete)
                        reports = reports.filter { it.qNum != numToDelete }
                        Toast.makeText(context, "Question Q$numToDelete deleted and set renumbered", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.red,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_delete_question_button")
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { questionToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_question_button")
                ) {
                    Text("Cancel", color = colors.inkMuted)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .testTag("audit_resolution_hub_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                    imageVector = Icons.Default.FactCheck,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Audit Resolution Hub & Analytics",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "${paper.metadata.title} • ${paper.questions.size} Questions Audited",
                                fontSize = 11.5.sp,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("resolution_hub_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.muted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // KPI Summary Metrics Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KpiCard(
                        title = "Wrong Keys",
                        count = metrics.wrongKeysCount,
                        color = colors.red,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Missing Keys",
                        count = metrics.missingKeysCount,
                        color = Color(0xFFD97706),
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Flawed",
                        count = metrics.flawedQuestionsCount + metrics.missingOptionsCount,
                        color = Color(0xFFEA580C),
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Clean",
                        count = metrics.cleanCount,
                        color = colors.teal,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Collapsible LLM Input Box
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                    border = BorderStroke(0.6.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LLM Audit Feedback Parser",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { isInputExpanded = !isInputExpanded },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isInputExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Toggle input",
                                        tint = colors.muted
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = isInputExpanded) {
                            Column {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Paste the JSON or text audit response from ChatGPT/Gemini below, or click 'Run Heuristic Pre-Check' for instant local analysis:",
                                    fontSize = 11.sp,
                                    color = colors.inkMuted
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedTextField(
                                    value = feedbackInput,
                                    onValueChange = { feedbackInput = it },
                                    placeholder = {
                                        Text(
                                            text = "Paste JSON audit feedback [{\"qNum\": 1, \"status\": \"WRONG_KEY\", ...}] or plain-text notes...",
                                            fontSize = 11.sp,
                                            color = colors.muted
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 80.dp, max = 130.dp)
                                        .testTag("audit_feedback_text_field"),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = clipboard.primaryClip
                                            if (clip != null && clip.itemCount > 0) {
                                                feedbackInput = clip.getItemAt(0).text?.toString() ?: ""
                                                parseInput()
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Paste & Parse", fontSize = 11.5.sp)
                                    }

                                    Button(
                                        onClick = { parseInput() },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.primaryButtonBg,
                                            contentColor = colors.primaryButtonText
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.primaryButtonText)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Analyze", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.primaryButtonText)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Batch Action Banner (If Wrong Keys Exist)
                if (unresolvedWrongKeys.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.teal.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f)),
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
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${unresolvedWrongKeys.size} Answer Key Fixes Available",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold
                                    )
                                    Text(
                                        text = "Updates answer keys & attaches rechecked explanations",
                                        fontSize = 10.sp,
                                        color = colors.teal
                                    )
                                }
                            }

                            Button(
                                onClick = { applyBatchKeyFixes() },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.teal,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("fix_all_keys_button")
                            ) {
                                Text("Fix All Keys", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Filter Category Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryPill(
                        label = "All (${reports.size})",
                        isSelected = activeTab == "ALL",
                        onClick = { activeTab = "ALL" },
                        colors = colors
                    )
                    CategoryPill(
                        label = "Wrong Keys (${metrics.wrongKeysCount})",
                        isSelected = activeTab == "WRONG_KEYS",
                        onClick = { activeTab = "WRONG_KEYS" },
                        colors = colors
                    )
                    CategoryPill(
                        label = "Flawed (${metrics.flawedQuestionsCount + metrics.missingOptionsCount})",
                        isSelected = activeTab == "FLAWED",
                        onClick = { activeTab = "FLAWED" },
                        colors = colors
                    )
                    CategoryPill(
                        label = "Clean (${metrics.cleanCount})",
                        isSelected = activeTab == "CLEAN",
                        onClick = { activeTab = "CLEAN" },
                        colors = colors
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable List of Audit Report Items
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (filteredReports.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = colors.teal,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No items in this category",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredReports, key = { it.qNum }) { report ->
                            AuditReportItemCard(
                                report = report,
                                onApplyKeyFix = { applyKeyAndExplanationFix(report) },
                                onApplyReplacement = { applyQuestionReplacement(report) },
                                onRequestDelete = {
                                    val q = report.originalQuestion ?: paper.questions.find { it.num == report.qNum }
                                    if (q != null) questionToDelete = q
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        border = BorderStroke(0.6.dp, colors.border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = if (count > 0) color else colors.inkMuted
            )
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CategoryPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: com.example.ui.theme.ExamColorPalette
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) colors.primaryButtonBg else colors.surfaceVariant,
        border = BorderStroke(0.6.dp, if (isSelected) colors.primaryButtonBg else colors.border)
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) colors.primaryButtonText else colors.inkMuted,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun AuditReportItemCard(
    report: AuditQuestionReport,
    onApplyKeyFix: () -> Unit,
    onApplyReplacement: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val colors = ExamTheme.colors
    val originalQ = report.originalQuestion

    val statusColor = when (report.status) {
        AuditIssueType.WRONG_KEY -> colors.red
        AuditIssueType.MISSING_KEY -> Color(0xFFD97706)
        AuditIssueType.FLAWED_QUESTION, AuditIssueType.VAGUE_OPTIONS, AuditIssueType.MISSING_OPTIONS -> Color(0xFFEA580C)
        AuditIssueType.CLEAN -> colors.teal
        AuditIssueType.MISSING_EXPLANATION -> colors.teal
    }

    val statusLabel = when (report.status) {
        AuditIssueType.WRONG_KEY -> "WRONG KEY DETECTED"
        AuditIssueType.MISSING_KEY -> "MISSING ANSWER KEY"
        AuditIssueType.FLAWED_QUESTION -> "FATAL FLAW"
        AuditIssueType.VAGUE_OPTIONS -> "VAGUE / DUPLICATE OPTIONS"
        AuditIssueType.MISSING_OPTIONS -> "MISSING OPTIONS"
        AuditIssueType.CLEAN -> "VERIFIED CLEAN"
        AuditIssueType.MISSING_EXPLANATION -> "MISSING EXPLANATION"
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (report.isResolved) colors.teal.copy(alpha = 0.5f) else colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audit_report_item_${report.qNum}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(0.6.dp, colors.border)
                    ) {
                        Text(
                            text = "Q${report.qNum}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, statusColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (report.isResolved) "RESOLVED" else statusLabel,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (report.isResolved) colors.teal else statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Original Question Preview
            if (originalQ != null) {
                Text(
                    text = originalQ.question,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Key Error Comparison Box (If Wrong Key)
            if (report.status == AuditIssueType.WRONG_KEY && !report.suggestedKey.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.red.copy(alpha = 0.08f),
                    border = BorderStroke(0.8.dp, colors.red.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Marked Key: ",
                            fontSize = 11.5.sp,
                            color = colors.inkMuted
                        )
                        Text(
                            text = "${report.currentKey ?: "None"} ",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.red
                        )
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Corrected Key: ",
                            fontSize = 11.5.sp,
                            color = colors.inkMuted
                        )
                        Text(
                            text = "${report.suggestedKey}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.teal
                        )
                    }
                }
            }

            // Flaw Reason Box (If Present)
            if (!report.flawReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• ${report.flawReason}",
                    fontSize = 11.5.sp,
                    color = colors.red.copy(alpha = 0.9f)
                )
            }

            // Rechecked Detailed Explanation Box (Mandatory Requirement)
            if (!report.recheckedExplanation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                    border = BorderStroke(0.6.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Rechecked Detailed Explanation:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = report.recheckedExplanation,
                            fontSize = 11.sp,
                            color = colors.inkMuted,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Suggested Replacement Question Preview (Atomic Package)
            if (report.replacementQuestion != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.teal.copy(alpha = 0.08f)),
                    border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Replacement Question Package:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = report.replacementQuestion.question,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A) ${report.replacementQuestion.options.a}\nB) ${report.replacementQuestion.options.b}\nC) ${report.replacementQuestion.options.c}\nD) ${report.replacementQuestion.options.d}",
                            fontSize = 10.5.sp,
                            color = colors.inkMuted,
                            lineHeight = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Correct: ${report.replacementQuestion.correctAnswer ?: "A"} • Explanation: ${report.replacementQuestion.explanation ?: "Verified"}",
                            fontSize = 10.sp,
                            color = colors.teal,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Action Buttons
            if (!report.isResolved) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete Button (Safety Confirmation)
                    OutlinedButton(
                        onClick = onRequestDelete,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.red),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("delete_flawed_q_${report.qNum}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Question", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Replacement Question Action (Atomic)
                    if (report.replacementQuestion != null) {
                        Button(
                            onClick = onApplyReplacement,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.teal,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("replace_q_${report.qNum}")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Replace Question", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (report.status == AuditIssueType.WRONG_KEY || report.status == AuditIssueType.MISSING_KEY) {
                        // Fix Key & Explanation Action
                        Button(
                            onClick = onApplyKeyFix,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryButtonBg,
                                contentColor = colors.primaryButtonText
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("fix_key_q_${report.qNum}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.primaryButtonText)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fix Key & Explanation", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primaryButtonText)
                        }
                    }
                }
            }
        }
    }
}
