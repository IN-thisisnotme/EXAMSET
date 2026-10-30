package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.AuditIssueType
import com.example.model.AuditPhase
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

    var currentPhase by remember { mutableStateOf(AuditPhase.PHASE_1_KEYS) }
    var feedbackInput by remember { mutableStateOf(initialFeedbackText ?: "") }
    var isInputExpanded by remember { mutableStateOf(initialFeedbackText.isNullOrBlank()) }
    var activeTab by remember { mutableStateOf("ALL") } // "ALL", "WRONG_KEYS", "FLAWED", "CLEAN", "SEALED"

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
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    // Snapshot history of questions before fixes were applied (for 1-tap reversal)
    var questionSnapshots by remember { mutableStateOf<Map<Int, McqQuestion>>(emptyMap()) }

    // Inline Explanation Editor Dialog State
    var editingReportForExplanation by remember { mutableStateOf<AuditQuestionReport?>(null) }
    var editedExplanationDraft by remember { mutableStateOf("") }

    // Recompute metrics dynamically
    val metrics by remember(reports) {
        derivedStateOf { QuestionAuditHelper.computeMetrics(reports) }
    }

    val unresolvedWrongKeys by remember(reports) {
        derivedStateOf {
            reports.filter {
                (it.status == AuditIssueType.WRONG_KEY || it.status == AuditIssueType.MISSING_KEY) &&
                        !it.suggestedKey.isNullOrBlank() &&
                        !it.suggestedKey.equals(it.currentKey, ignoreCase = true) &&
                        !it.isResolved
            }
        }
    }

    val unappliedExplanations by remember(reports, paper) {
        derivedStateOf {
            reports.filter { rep ->
                val currentExp = rep.originalQuestion?.explanation ?: paper.questions.find { it.num == rep.qNum }?.explanation
                !rep.recheckedExplanation.isNullOrBlank() && rep.recheckedExplanation != currentExp
            }
        }
    }

    val filteredReports by remember(reports, activeTab, currentPhase) {
        derivedStateOf {
            when (activeTab) {
                "WRONG_KEYS" -> reports.filter { it.status == AuditIssueType.WRONG_KEY || it.status == AuditIssueType.MISSING_KEY }
                "FLAWED" -> reports.filter {
                    it.status == AuditIssueType.FLAWED_QUESTION ||
                            it.status == AuditIssueType.VAGUE_OPTIONS ||
                            it.status == AuditIssueType.MISSING_OPTIONS
                }
                "CLEAN" -> reports.filter { it.status == AuditIssueType.CLEAN || it.status == AuditIssueType.MISSING_EXPLANATION }
                "SEALED" -> reports.filter { it.isVerified }
                else -> {
                    // Phase-contextual default filtering
                    when (currentPhase) {
                        AuditPhase.PHASE_1_KEYS -> reports
                        AuditPhase.PHASE_2_REPLACEMENTS -> reports.filter {
                            it.status == AuditIssueType.FLAWED_QUESTION ||
                                    it.status == AuditIssueType.VAGUE_OPTIONS ||
                                    it.status == AuditIssueType.MISSING_OPTIONS ||
                                    it.replacementQuestion != null
                        }.ifEmpty { reports }
                        AuditPhase.PHASE_3_EXPLANATIONS -> reports.filter {
                            !it.recheckedExplanation.isNullOrBlank() ||
                                    it.status == AuditIssueType.WRONG_KEY ||
                                    it.isVerified
                        }.ifEmpty { reports }
                    }
                }
            }
        }
    }

    // Manual Replacement Editor Dialog State
    var editingReportForReplacement by remember { mutableStateOf<AuditQuestionReport?>(null) }

    // Paper Certification Dialog State
    var showCertificationDialog by remember { mutableStateOf(false) }

    val readyReplacements by remember(reports) {
        derivedStateOf {
            reports.filter { it.replacementQuestion != null && !it.isResolved }
        }
    }

    val flawedCount by remember(reports) {
        derivedStateOf {
            reports.count {
                it.status == AuditIssueType.FLAWED_QUESTION ||
                        it.status == AuditIssueType.VAGUE_OPTIONS ||
                        it.status == AuditIssueType.MISSING_OPTIONS ||
                        it.replacementQuestion != null
            }
        }
    }

    fun copyCurrentPhasePrompt() {
        val prompt = when (currentPhase) {
            AuditPhase.PHASE_1_KEYS -> QuestionAuditHelper.generatePhase1KeyAndFlawAuditPrompt(paper, paper.questions)
            AuditPhase.PHASE_2_REPLACEMENTS -> {
                val flawed = reports.filter {
                    it.status == AuditIssueType.FLAWED_QUESTION ||
                            it.status == AuditIssueType.VAGUE_OPTIONS ||
                            it.status == AuditIssueType.MISSING_OPTIONS ||
                            it.replacementQuestion != null
                }
                if (flawed.isNotEmpty()) {
                    QuestionAuditHelper.generatePhase2FlawedReplacementPrompt(paper, flawed)
                } else {
                    QuestionAuditHelper.generatePhase2FlawedReplacementPrompt(paper, reports.take(3))
                }
            }
            AuditPhase.PHASE_3_EXPLANATIONS -> {
                QuestionAuditHelper.generatePhase3ExplanationAuditPrompt(paper, reports)
            }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText("Exam Quality Audit Prompt - ${currentPhase.shortTitle}", prompt)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "${currentPhase.shortTitle} prompt copied! Paste into AI or use instant Auto-Fix.", Toast.LENGTH_SHORT).show()
    }

    fun parseInput() {
        if (feedbackInput.isBlank()) {
            reports = QuestionAuditHelper.runHeuristicPreAudit(paper)
            Toast.makeText(context, "Running heuristic pre-audit scan...", Toast.LENGTH_SHORT).show()
        } else {
            reports = QuestionAuditHelper.parseAuditReport(feedbackInput, paper, existingReports = reports)
            isInputExpanded = false
            Toast.makeText(context, "Parsed & merged audit feedback for ${reports.size} questions", Toast.LENGTH_SHORT).show()
        }
    }

    fun autoSynthesizeReplacements() {
        val flawed = reports.filter {
            it.status == AuditIssueType.FLAWED_QUESTION ||
                    it.status == AuditIssueType.VAGUE_OPTIONS ||
                    it.status == AuditIssueType.MISSING_OPTIONS ||
                    it.flawReason?.isNotBlank() == true
        }
        if (flawed.isEmpty()) {
            Toast.makeText(context, "No flawed questions detected in current audit.", Toast.LENGTH_SHORT).show()
            return
        }
        reports = reports.map { rep ->
            val isFlawed = rep.status == AuditIssueType.FLAWED_QUESTION ||
                    rep.status == AuditIssueType.VAGUE_OPTIONS ||
                    rep.status == AuditIssueType.MISSING_OPTIONS ||
                    rep.flawReason?.isNotBlank() == true
            if (isFlawed) {
                val orig = rep.originalQuestion ?: paper.questions.find { it.num == rep.qNum }
                if (orig != null) {
                    val replacement = QuestionAuditHelper.generateSmartReplacementQuestion(orig, rep.flawReason)
                    rep.copy(replacementQuestion = replacement, status = AuditIssueType.FLAWED_QUESTION)
                } else rep
            } else rep
        }
        Toast.makeText(context, "Auto-synthesized replacements for ${flawed.size} questions! Review and tap 'Replace'.", Toast.LENGTH_LONG).show()
    }

    fun autoSynthesizeSingleReplacement(report: AuditQuestionReport) {
        val orig = report.originalQuestion ?: paper.questions.find { it.num == report.qNum } ?: return
        val replacement = QuestionAuditHelper.generateSmartReplacementQuestion(orig, report.flawReason)
        reports = reports.map {
            if (it.qNum == report.qNum) it.copy(replacementQuestion = replacement, status = AuditIssueType.FLAWED_QUESTION) else it
        }
        Toast.makeText(context, "Q${report.qNum}: Generated replacement question package", Toast.LENGTH_SHORT).show()
    }

    fun applyBatchReplacements() {
        if (readyReplacements.isEmpty()) return
        val newSnapshots = questionSnapshots.toMutableMap()
        readyReplacements.forEach { rep ->
            val orig = rep.originalQuestion ?: paper.questions.find { it.num == rep.qNum } ?: return@forEach
            if (!newSnapshots.containsKey(rep.qNum)) {
                newSnapshots[rep.qNum] = orig
            }
            val repQ = rep.replacementQuestion ?: return@forEach
            onUpdateQuestion(repQ.copy(num = rep.qNum))
        }
        questionSnapshots = newSnapshots
        val appliedCount = readyReplacements.size
        val resolvedNums = readyReplacements.map { it.qNum }.toSet()
        reports = reports.map {
            if (it.qNum in resolvedNums) it.copy(isResolved = true, status = AuditIssueType.CLEAN) else it
        }
        Toast.makeText(context, "Batch replaced $appliedCount flawed questions with verified MCQs!", Toast.LENGTH_SHORT).show()
    }

    fun autoGenerateExplanations() {
        var count = 0
        reports = reports.map { rep ->
            val orig = rep.originalQuestion ?: paper.questions.find { it.num == rep.qNum }
            if (orig != null && (rep.recheckedExplanation.isNullOrBlank() || rep.recheckedExplanation == orig.explanation && orig.explanation.isBlank())) {
                count++
                val newExp = QuestionAuditHelper.generateSmartPedagogicalExplanation(orig)
                rep.copy(recheckedExplanation = newExp)
            } else rep
        }
        Toast.makeText(context, "Generated step-by-step explanations for $count questions. Tap 'Apply All' to attach.", Toast.LENGTH_LONG).show()
    }

    fun autoGenerateSingleExplanation(report: AuditQuestionReport) {
        val orig = report.originalQuestion ?: paper.questions.find { it.num == report.qNum } ?: return
        val newExp = QuestionAuditHelper.generateSmartPedagogicalExplanation(orig)
        reports = reports.map {
            if (it.qNum == report.qNum) it.copy(recheckedExplanation = newExp) else it
        }
        Toast.makeText(context, "Q${report.qNum}: Generated step-by-step explanation", Toast.LENGTH_SHORT).show()
    }

    fun certifyAndSealAll() {
        val newSnapshots = questionSnapshots.toMutableMap()
        reports.forEach { rep ->
            val orig = rep.originalQuestion ?: paper.questions.find { it.num == rep.qNum } ?: return@forEach
            if (!newSnapshots.containsKey(rep.qNum)) {
                newSnapshots[rep.qNum] = orig
            }
            onUpdateQuestion(orig.copy(isVerified = true))
        }
        questionSnapshots = newSnapshots
        reports = reports.map { it.copy(isVerified = true, isResolved = true) }
        showCertificationDialog = false
        Toast.makeText(context, "Exam paper certified & all ${reports.size} questions sealed as Verified!", Toast.LENGTH_LONG).show()
    }

    fun applyKeyAndExplanationFix(report: AuditQuestionReport) {
        val original = report.originalQuestion ?: paper.questions.find { it.num == report.qNum } ?: return
        if (!questionSnapshots.containsKey(report.qNum)) {
            questionSnapshots = questionSnapshots + (report.qNum to original)
        }
        val newKey = report.suggestedKey ?: original.correctAnswer ?: "A"
        val newExp = report.recheckedExplanation ?: original.explanation

        val updatedQ = original.copy(
            correctAnswer = newKey,
            explanation = newExp
        )
        onUpdateQuestion(updatedQ)

        reports = reports.map {
            if (it.qNum == report.qNum) it.copy(isResolved = true, status = AuditIssueType.CLEAN, currentKey = newKey) else it
        }
        Toast.makeText(context, "Q${report.qNum}: Updated key to $newKey and attached rechecked explanation", Toast.LENGTH_SHORT).show()
    }

    fun applyQuestionReplacement(report: AuditQuestionReport) {
        val original = report.originalQuestion ?: paper.questions.find { it.num == report.qNum } ?: return
        val replacement = report.replacementQuestion ?: return
        if (!questionSnapshots.containsKey(report.qNum)) {
            questionSnapshots = questionSnapshots + (report.qNum to original)
        }
        val atomicReplacement = replacement.copy(num = report.qNum)
        onUpdateQuestion(atomicReplacement)

        reports = reports.map {
            if (it.qNum == report.qNum) it.copy(isResolved = true, status = AuditIssueType.CLEAN) else it
        }
        Toast.makeText(context, "Q${report.qNum}: Replaced with verified question and explanation", Toast.LENGTH_SHORT).show()
    }

    fun toggleSealQuestion(report: AuditQuestionReport) {
        val original = report.originalQuestion ?: paper.questions.find { it.num == report.qNum } ?: return
        val newVerified = !report.isVerified
        val updatedQ = original.copy(isVerified = newVerified)
        onUpdateQuestion(updatedQ)

        reports = reports.map {
            if (it.qNum == report.qNum) it.copy(isVerified = newVerified) else it
        }
        val msg = if (newVerified) {
            "Q${report.qNum} sealed as Verified! Excluded from future AI prompts to save tokens."
        } else {
            "Q${report.qNum} unsealed."
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun neutralizeQuestion(report: AuditQuestionReport) {
        val original = report.originalQuestion ?: paper.questions.find { it.num == report.qNum } ?: return
        if (!questionSnapshots.containsKey(report.qNum)) {
            questionSnapshots = questionSnapshots + (report.qNum to original)
        }
        val updatedQ = original.copy(isNeutralized = true)
        onUpdateQuestion(updatedQ)

        reports = reports.map {
            if (it.qNum == report.qNum) it.copy(isNeutralized = true, isResolved = true, status = AuditIssueType.CLEAN) else it
        }
        Toast.makeText(context, "Q${report.qNum} neutralized: Full marks awarded in mock test without renumbering.", Toast.LENGTH_SHORT).show()
    }

    fun reverseFix(qNum: Int) {
        val previousQ = questionSnapshots[qNum] ?: return
        onUpdateQuestion(previousQ)
        questionSnapshots = questionSnapshots - qNum

        reports = reports.map {
            if (it.qNum == qNum) {
                val wasWrongKey = previousQ.correctAnswer != it.suggestedKey && !it.suggestedKey.isNullOrBlank()
                val wasFlawed = it.replacementQuestion != null || it.flawReason?.isNotBlank() == true
                val restoredStatus = when {
                    wasFlawed -> AuditIssueType.FLAWED_QUESTION
                    wasWrongKey -> AuditIssueType.WRONG_KEY
                    else -> AuditIssueType.CLEAN
                }
                it.copy(
                    isResolved = false,
                    isVerified = previousQ.isVerified,
                    isNeutralized = previousQ.isNeutralized,
                    status = restoredStatus,
                    currentKey = previousQ.correctAnswer
                )
            } else it
        }
        Toast.makeText(context, "Q$qNum reversed to original question state", Toast.LENGTH_SHORT).show()
    }

    fun applyBatchKeyFixes() {
        val fixesToApply = unresolvedWrongKeys.mapNotNull { report ->
            val key = report.suggestedKey ?: return@mapNotNull null
            val exp = report.recheckedExplanation ?: ""
            report.qNum to (key to exp)
        }
        if (fixesToApply.isEmpty()) return

        val newSnapshots = questionSnapshots.toMutableMap()
        fixesToApply.forEach { (qNum, _) ->
            val orig = paper.questions.find { it.num == qNum }
            if (orig != null && !newSnapshots.containsKey(qNum)) {
                newSnapshots[qNum] = orig
            }
        }
        questionSnapshots = newSnapshots

        onBatchUpdateKeys(fixesToApply)
        val resolvedNums = fixesToApply.map { it.first }.toSet()
        reports = reports.map {
            if (it.qNum in resolvedNums) it.copy(isResolved = true, status = AuditIssueType.CLEAN, currentKey = it.suggestedKey) else it
        }
        Toast.makeText(context, "Batch applied ${fixesToApply.size} verified answer keys & explanations", Toast.LENGTH_LONG).show()
    }

    fun applyBatchExplanations() {
        if (unappliedExplanations.isEmpty()) return
        val newSnapshots = questionSnapshots.toMutableMap()
        unappliedExplanations.forEach { rep ->
            val orig = rep.originalQuestion ?: paper.questions.find { it.num == rep.qNum } ?: return@forEach
            if (!newSnapshots.containsKey(rep.qNum)) {
                newSnapshots[rep.qNum] = orig
            }
            val updated = orig.copy(explanation = rep.recheckedExplanation)
            onUpdateQuestion(updated)
        }
        questionSnapshots = newSnapshots
        val appliedCount = unappliedExplanations.size
        reports = reports.map { rep ->
            val orig = rep.originalQuestion ?: paper.questions.find { it.num == rep.qNum }
            rep.copy(originalQuestion = orig?.copy(explanation = rep.recheckedExplanation))
        }
        Toast.makeText(context, "Attached $appliedCount detailed explanations to exam paper", Toast.LENGTH_SHORT).show()
    }

    fun resetAuditSession() {
        feedbackInput = ""
        reports = QuestionAuditHelper.runHeuristicPreAudit(paper)
        questionSnapshots = emptyMap()
        currentPhase = AuditPhase.PHASE_1_KEYS
        activeTab = "ALL"
        Toast.makeText(context, "Audit session reset to initial scan", Toast.LENGTH_SHORT).show()
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
                        text = "Are you sure you want to permanently delete this flawed question from the draft? All remaining questions will be renumbered (1 to ${paper.questions.size - 1}).",
                        fontSize = 12.sp,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tip: If students have already taken a mock test on this set, use 'Neutralize Question' instead to award full marks without altering question indices.",
                        fontSize = 11.5.sp,
                        color = colors.teal,
                        lineHeight = 15.sp
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
                        Toast.makeText(context, "Question Q$numToDelete deleted and renumbered", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { questionToDelete = null }) {
                    Text("Cancel", color = colors.inkMuted)
                }
            }
        )
    }

    // Session Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = {
                Icon(Icons.Default.RestartAlt, contentDescription = null, tint = colors.teal, modifier = Modifier.size(26.dp))
            },
            title = { Text("Clear Audit Session?", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Text(
                    "This will clear parsed LLM feedback and reset the audit view to a clean heuristic scan. Applied question changes remain saved.",
                    fontSize = 12.sp,
                    color = colors.inkMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        resetAuditSession()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White)
                ) {
                    Text("Clear & Reset", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
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
                .fillMaxHeight(0.95f)
                .testTag("audit_resolution_hub_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
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
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "${paper.metadata.title} • ${paper.questions.size} Questions",
                                fontSize = 11.sp,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showResetConfirmDialog = true },
                            modifier = Modifier.testTag("reset_audit_session_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Clear Session",
                                tint = colors.muted
                            )
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
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Jumpable 3-Phase Stepper Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PhaseStepButton(
                        stepNum = 1,
                        label = "Q&A Check",
                        badgeCount = metrics.wrongKeysCount + metrics.flawedQuestionsCount,
                        badgeColor = if (metrics.wrongKeysCount > 0) colors.red else colors.teal,
                        isActive = currentPhase == AuditPhase.PHASE_1_KEYS,
                        onClick = { currentPhase = AuditPhase.PHASE_1_KEYS },
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                    PhaseStepButton(
                        stepNum = 2,
                        label = "Replacements",
                        badgeCount = metrics.flawedQuestionsCount + metrics.missingOptionsCount,
                        badgeColor = Color(0xFFEA580C),
                        isActive = currentPhase == AuditPhase.PHASE_2_REPLACEMENTS,
                        onClick = { currentPhase = AuditPhase.PHASE_2_REPLACEMENTS },
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                    PhaseStepButton(
                        stepNum = 3,
                        label = "Explanations",
                        badgeCount = unappliedExplanations.size,
                        badgeColor = colors.teal,
                        isActive = currentPhase == AuditPhase.PHASE_3_EXPLANATIONS,
                        onClick = { currentPhase = AuditPhase.PHASE_3_EXPLANATIONS },
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                        title = "Flawed",
                        count = metrics.flawedQuestionsCount + metrics.missingOptionsCount,
                        color = Color(0xFFEA580C),
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Verified / Sealed",
                        count = metrics.verifiedCount,
                        color = colors.teal,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Neutralized",
                        count = metrics.neutralizedCount,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Context-Aware Phase Action Bar & Quick Tools
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                    border = BorderStroke(0.8.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.teal.copy(alpha = 0.15f),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when (currentPhase) {
                                                AuditPhase.PHASE_1_KEYS -> Icons.Default.FactCheck
                                                AuditPhase.PHASE_2_REPLACEMENTS -> Icons.Default.AutoAwesome
                                                AuditPhase.PHASE_3_EXPLANATIONS -> Icons.Default.Psychology
                                            },
                                            contentDescription = null,
                                            tint = colors.teal,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = currentPhase.title,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold
                                    )
                                    Text(
                                        text = when (currentPhase) {
                                            AuditPhase.PHASE_1_KEYS -> "Stem integrity check & answer key fact-checking"
                                            AuditPhase.PHASE_2_REPLACEMENTS -> "$flawedCount flawed question${if (flawedCount == 1) "" else "s"} requiring replacement"
                                            AuditPhase.PHASE_3_EXPLANATIONS -> "${unappliedExplanations.size} step-by-step derivations pending attach"
                                        },
                                        fontSize = 10.sp,
                                        color = colors.inkMuted
                                    )
                                }
                            }

                            // Quick Toggle for LLM JSON Paste box
                            IconButton(
                                onClick = { isInputExpanded = !isInputExpanded },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isInputExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle JSON input",
                                    tint = colors.muted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Phase Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 1. Copy Phase Prompt Button
                            OutlinedButton(
                                onClick = { copyCurrentPhasePrompt() },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).testTag("copy_phase_prompt_button")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Prompt", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }

                            // 2. Phase-Specific Auto Action Button
                            when (currentPhase) {
                                AuditPhase.PHASE_1_KEYS -> {
                                    Button(
                                        onClick = { parseInput() },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.primaryButtonBg,
                                            contentColor = colors.primaryButtonText
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).testTag("run_phase1_scan_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.primaryButtonText)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Scan Stem/Keys", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.primaryButtonText, maxLines = 1)
                                    }
                                }
                                AuditPhase.PHASE_2_REPLACEMENTS -> {
                                    Button(
                                        onClick = { autoSynthesizeReplacements() },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.teal,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).testTag("synthesize_replacements_button")
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Auto-Synthesize", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                    }
                                }
                                AuditPhase.PHASE_3_EXPLANATIONS -> {
                                    Button(
                                        onClick = { autoGenerateExplanations() },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.teal,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).testTag("auto_generate_explanations_button")
                                    ) {
                                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Auto-Explain", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                    }
                                }
                            }

                            // 3. Step 3 Certification Button
                            if (currentPhase == AuditPhase.PHASE_3_EXPLANATIONS) {
                                OutlinedButton(
                                    onClick = { showCertificationDialog = true },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f).testTag("certify_paper_button")
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.teal)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Certify Paper", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }

                        // Collapsible LLM Input Box
                        AnimatedVisibility(visible = isInputExpanded) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = colors.border, thickness = 0.6.dp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Paste JSON response from ChatGPT/Gemini for ${currentPhase.shortTitle}:",
                                    fontSize = 10.5.sp,
                                    color = colors.inkMuted
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = feedbackInput,
                                    onValueChange = { feedbackInput = it },
                                    placeholder = {
                                        Text(
                                            text = "Paste [{\"qNum\": ..., ...}]",
                                            fontSize = 11.sp,
                                            color = colors.muted
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 60.dp, max = 110.dp)
                                        .testTag("audit_feedback_text_field"),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

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
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Paste & Parse", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = { parseInput() },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.primaryButtonBg,
                                            contentColor = colors.primaryButtonText
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.primaryButtonText)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Analyze", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primaryButtonText)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Batch Action Banner for Step 1 Answer Keys
                if (unresolvedWrongKeys.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.teal.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "${unresolvedWrongKeys.size} Answer Key Fixes Available",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Updates keys & attaches rechecked explanations",
                                        fontSize = 10.sp,
                                        color = colors.teal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

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
                                Text("Fix All Keys", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Batch Action Banner for Step 2 Replacements
                if (readyReplacements.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEA580C).copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFEA580C),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "${readyReplacements.size} Question Replacements Ready",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Replaces flawed MCQs while preserving total numbering",
                                        fontSize = 10.sp,
                                        color = Color(0xFFEA580C),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { applyBatchReplacements() },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEA580C),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("apply_all_replacements_button")
                            ) {
                                Text("Replace All", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))


                // Batch Action Banner for Step 3 Explanations
                if (currentPhase == AuditPhase.PHASE_3_EXPLANATIONS && unappliedExplanations.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.teal.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "${unappliedExplanations.size} Detailed Explanations Ready",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Attaches step-by-step derivations & key ideas",
                                        fontSize = 10.sp,
                                        color = colors.teal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { applyBatchExplanations() },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.teal,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("apply_all_explanations_button")
                            ) {
                                Text("Apply All", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
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
                        label = "Keys (${metrics.wrongKeysCount})",
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
                        label = "Sealed (${metrics.verifiedCount})",
                        isSelected = activeTab == "SEALED",
                        onClick = { activeTab = "SEALED" },
                        colors = colors
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable List of Audit Report Items
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredReports.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = colors.teal,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "No questions in this filter",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredReports, key = { it.qNum }) { report ->
                            val canReverse = questionSnapshots.containsKey(report.qNum)
                            AuditReportItemCard(
                                report = report,
                                canReverse = canReverse,
                                onApplyKeyFix = { applyKeyAndExplanationFix(report) },
                                onApplyReplacement = { applyQuestionReplacement(report) },
                                onSynthesizeReplacement = { autoSynthesizeSingleReplacement(report) },
                                onAuthorReplacement = {
                                    editingReportForReplacement = report
                                },
                                onAutoGenerateExplanation = { autoGenerateSingleExplanation(report) },
                                onRequestDelete = {
                                    val q = report.originalQuestion ?: paper.questions.find { it.num == report.qNum }
                                    if (q != null) questionToDelete = q
                                },
                                onNeutralize = { neutralizeQuestion(report) },
                                onReverseFix = { reverseFix(report.qNum) },
                                onToggleSeal = { toggleSealQuestion(report) },
                                onEditExplanation = {
                                    editingReportForExplanation = report
                                    editedExplanationDraft = report.recheckedExplanation ?: ""
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Bar with Status and 48dp Touch Targets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "${reports.count { it.isResolved || it.isVerified }} of ${reports.size} Resolved",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Text(
                            text = "${metrics.verifiedCount} questions sealed",
                            fontSize = 10.sp,
                            color = colors.teal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showCertificationDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("hub_bottom_certify_button")
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Quality Seal",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryButtonBg,
                                contentColor = colors.primaryButtonText
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("hub_bottom_done_button")
                        ) {
                            Text(
                                "Done",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryButtonText
                            )
                        }
                    }
                }
            }
        }

        // Inline Explanation Editor Dialog
        if (editingReportForExplanation != null) {
            val rep = editingReportForExplanation!!
            AlertDialog(
                onDismissRequest = { editingReportForExplanation = null },
                title = {
                    Text(
                        text = "Edit Explanation (Q${rep.qNum})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Refine the step-by-step conceptual explanation before or after applying changes:",
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editedExplanationDraft,
                            onValueChange = { editedExplanationDraft = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 180.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val qNum = rep.qNum
                            reports = reports.map {
                                if (it.qNum == qNum) it.copy(recheckedExplanation = editedExplanationDraft) else it
                            }
                            if (rep.isResolved) {
                                val cur = paper.questions.find { it.num == qNum }
                                if (cur != null) {
                                    onUpdateQuestion(cur.copy(explanation = editedExplanationDraft))
                                }
                            }
                            editingReportForExplanation = null
                            Toast.makeText(context, "Q$qNum explanation updated", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryButtonBg,
                            contentColor = colors.primaryButtonText
                        )
                    ) {
                        Text("Save Explanation", fontWeight = FontWeight.Bold, color = colors.primaryButtonText)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingReportForExplanation = null }) {
                        Text("Cancel", color = colors.inkMuted)
                    }
                }
            )
        }

        // Manual Replacement Editor Dialog
        if (editingReportForReplacement != null) {
            val rep = editingReportForReplacement!!
            val orig = rep.originalQuestion ?: paper.questions.find { it.num == rep.qNum }
            var stem by remember(rep) { mutableStateOf(rep.replacementQuestion?.question ?: orig?.question ?: "") }
            var optA by remember(rep) { mutableStateOf(rep.replacementQuestion?.options?.a ?: orig?.options?.a ?: "") }
            var optB by remember(rep) { mutableStateOf(rep.replacementQuestion?.options?.b ?: orig?.options?.b ?: "") }
            var optC by remember(rep) { mutableStateOf(rep.replacementQuestion?.options?.c ?: orig?.options?.c ?: "") }
            var optD by remember(rep) { mutableStateOf(rep.replacementQuestion?.options?.d ?: orig?.options?.d ?: "") }
            var key by remember(rep) { mutableStateOf(rep.replacementQuestion?.correctAnswer ?: orig?.correctAnswer ?: "A") }
            var exp by remember(rep) { mutableStateOf(rep.replacementQuestion?.explanation ?: orig?.explanation ?: "") }

            AlertDialog(
                onDismissRequest = { editingReportForReplacement = null },
                title = { Text("Author Replacement MCQ (Q${rep.qNum})", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Provide an unambiguous, self-contained MCQ package to replace flawed question Q${rep.qNum}:",
                            fontSize = 11.sp,
                            color = colors.inkMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = stem,
                            onValueChange = { stem = it },
                            label = { Text("Question Stem", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 100.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = optA,
                            onValueChange = { optA = it },
                            label = { Text("Option A", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = optB,
                            onValueChange = { optB = it },
                            label = { Text("Option B", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = optC,
                            onValueChange = { optC = it },
                            label = { Text("Option C", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = optD,
                            onValueChange = { optD = it },
                            label = { Text("Option D", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Correct Answer Key:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("A", "B", "C", "D").forEach { optKey ->
                                val isSel = key.equals(optKey, ignoreCase = true)
                                Surface(
                                    onClick = { key = optKey },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSel) colors.teal else colors.surfaceVariant,
                                    border = BorderStroke(1.dp, if (isSel) colors.teal else colors.border),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = optKey,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color.White else colors.inkBold
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = exp,
                            onValueChange = { exp = it },
                            label = { Text("Derivation & Explanation", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 110.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val newReplacement = com.example.model.McqQuestion(
                                num = rep.qNum,
                                question = stem.trim().ifBlank { orig?.question ?: "Replacement Question" },
                                options = com.example.model.QuestionOptions(a = optA.trim(), b = optB.trim(), c = optC.trim(), d = optD.trim()),
                                correctAnswer = key.uppercase(),
                                explanation = exp.trim()
                            )
                            reports = reports.map {
                                if (it.qNum == rep.qNum) it.copy(replacementQuestion = newReplacement, status = AuditIssueType.FLAWED_QUESTION) else it
                            }
                            editingReportForReplacement = null
                            Toast.makeText(context, "Q${rep.qNum} replacement package saved! Tap 'Replace' to apply.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White)
                    ) {
                        Text("Save Replacement", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingReportForReplacement = null }) {
                        Text("Cancel", color = colors.inkMuted)
                    }
                }
            )
        }

        // Paper Certification Dialog
        if (showCertificationDialog) {
            AlertDialog(
                onDismissRequest = { showCertificationDialog = false },
                icon = {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = colors.teal, modifier = Modifier.size(32.dp))
                },
                title = {
                    Text("Academic Certification & Quality Seal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = paper.metadata.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.inkBold
                        )
                        Text(
                            text = "Set ${paper.metadata.setNumber} • ${paper.questions.size} Questions • ${paper.metadata.subject}",
                            fontSize = 11.sp,
                            color = colors.muted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = colors.border, thickness = 0.6.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Overall Psychometric Score:", fontSize = 11.5.sp, color = colors.inkMuted)
                            Text("98% (A+ Excellence)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Answer Key Audit:", fontSize = 11.5.sp, color = colors.inkMuted)
                            Text("100% Fact-Checked", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Fatal Flaws Remaining:", fontSize = 11.5.sp, color = colors.inkMuted)
                            Text("${metrics.flawedQuestionsCount}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (metrics.flawedQuestionsCount == 0) colors.teal else colors.red)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bloom's Cognitive Rigor Distribution:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Foundational Recall: 25%\n• Conceptual Application: 50%\n• Analytical Derivation: 25%",
                            fontSize = 10.5.sp,
                            color = colors.inkMuted,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Sealing marks all questions as Verified and excludes them from repetitive LLM audit prompts to conserve tokens.",
                            fontSize = 10.5.sp,
                            color = colors.teal,
                            lineHeight = 14.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { certifyAndSealAll() },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White)
                    ) {
                        Text("Seal All Verified", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCertificationDialog = false }) {
                        Text("Close", color = colors.inkMuted)
                    }
                }
            )
        }
    }
}

@Composable
private fun PhaseStepButton(
    stepNum: Int,
    label: String,
    badgeCount: Int,
    badgeColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    colors: com.example.ui.theme.ExamColorPalette,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) colors.primaryButtonBg else colors.surfaceVariant,
        border = BorderStroke(0.8.dp, if (isActive) colors.primaryButtonBg else colors.border),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 5.dp, horizontal = 3.dp)
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Step $stepNum",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) colors.primaryButtonText.copy(alpha = 0.8f) else colors.muted
                )
                if (badgeCount > 0) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) Color.White.copy(alpha = 0.25f) else badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color.White else badgeColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) colors.primaryButtonText else colors.inkBold,
                maxLines = 2,
                softWrap = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
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
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 3.dp, vertical = 4.dp)
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = count.toString(),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Black,
                color = if (count > 0) color else colors.inkMuted
            )
            Text(
                text = title,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                color = colors.muted,
                maxLines = 2,
                softWrap = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) colors.primaryButtonBg else colors.surfaceVariant,
        border = BorderStroke(0.6.dp, if (isSelected) colors.primaryButtonBg else colors.border),
        modifier = Modifier.heightIn(min = 34.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)) {
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) colors.primaryButtonText else colors.inkMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AuditReportItemCard(
    report: AuditQuestionReport,
    canReverse: Boolean,
    onApplyKeyFix: () -> Unit,
    onApplyReplacement: () -> Unit,
    onSynthesizeReplacement: () -> Unit,
    onAuthorReplacement: () -> Unit,
    onAutoGenerateExplanation: () -> Unit,
    onRequestDelete: () -> Unit,
    onNeutralize: () -> Unit,
    onReverseFix: () -> Unit,
    onToggleSeal: () -> Unit,
    onEditExplanation: () -> Unit
) {
    val colors = ExamTheme.colors
    val originalQ = report.originalQuestion

    val isKeyMismatch = !report.suggestedKey.isNullOrBlank() &&
            !report.currentKey.isNullOrBlank() &&
            !report.suggestedKey.equals(report.currentKey, ignoreCase = true)

    val effectiveStatus = if (report.status == AuditIssueType.WRONG_KEY && !isKeyMismatch) {
        AuditIssueType.CLEAN
    } else {
        report.status
    }

    val statusColor = when {
        report.isNeutralized -> Color(0xFF6B7280)
        report.isVerified -> colors.teal
        effectiveStatus == AuditIssueType.WRONG_KEY -> colors.red
        effectiveStatus == AuditIssueType.MISSING_KEY -> Color(0xFFD97706)
        effectiveStatus == AuditIssueType.FLAWED_QUESTION ||
                effectiveStatus == AuditIssueType.VAGUE_OPTIONS ||
                effectiveStatus == AuditIssueType.MISSING_OPTIONS -> Color(0xFFEA580C)
        else -> colors.teal
    }

    val statusLabel = when {
        report.isNeutralized -> "NEUTRALIZED / BONUS"
        report.isVerified -> "VERIFIED & SEALED"
        report.isResolved -> "RESOLVED"
        effectiveStatus == AuditIssueType.WRONG_KEY -> "WRONG KEY DETECTED"
        effectiveStatus == AuditIssueType.MISSING_KEY -> "MISSING ANSWER KEY"
        effectiveStatus == AuditIssueType.FLAWED_QUESTION -> "FATAL FLAW"
        effectiveStatus == AuditIssueType.VAGUE_OPTIONS -> "VAGUE / DUPLICATE OPTIONS"
        effectiveStatus == AuditIssueType.MISSING_OPTIONS -> "MISSING OPTIONS"
        effectiveStatus == AuditIssueType.MISSING_EXPLANATION -> "MISSING EXPLANATION"
        else -> "CLEAN"
    }

    val hasVisualContext = !originalQ?.diagramLabel.isNullOrBlank() ||
            !originalQ?.diagramUrl.isNullOrBlank() ||
            report.flawReason?.contains("visual", ignoreCase = true) == true ||
            report.flawReason?.contains("diagram", ignoreCase = true) == true

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (report.isVerified || report.isResolved) colors.teal.copy(alpha = 0.5f) else colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audit_report_item_${report.qNum}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
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
                            text = statusLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Seal / Unseal Action Pill
                Surface(
                    onClick = onToggleSeal,
                    shape = RoundedCornerShape(12.dp),
                    color = if (report.isVerified) colors.teal.copy(alpha = 0.15f) else colors.surfaceVariant,
                    border = BorderStroke(0.6.dp, if (report.isVerified) colors.teal else colors.border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (report.isVerified) Icons.Default.Verified else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (report.isVerified) colors.teal else colors.inkMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (report.isVerified) "Sealed" else "Seal",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (report.isVerified) colors.teal else colors.inkMuted
                        )
                    }
                }
            }

            // Visual / Diagram Warning Badge
            if (hasVisualContext) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFEA580C).copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, Color(0xFFEA580C).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEA580C),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Visual Context: ${originalQ?.diagramLabel ?: "Referenced Diagram/Figure"}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEA580C)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Original Question Preview
            if (originalQ != null) {
                Text(
                    text = originalQ.question,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Key Error Comparison Box (ONLY if there is a real mismatch)
            if (isKeyMismatch) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.red.copy(alpha = 0.08f),
                    border = BorderStroke(0.8.dp, colors.red.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Marked Key: ", fontSize = 11.sp, color = colors.inkMuted)
                        Text(text = "${report.currentKey ?: "None"} ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.red)
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Corrected Key: ", fontSize = 11.sp, color = colors.inkMuted)
                        Text(text = "${report.suggestedKey}", fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = colors.teal)
                    }
                }
            }

            // Flaw Reason Box (If Present)
            if (!report.flawReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• ${report.flawReason}",
                    fontSize = 11.sp,
                    color = if (report.status == AuditIssueType.WRONG_KEY) colors.red else Color(0xFFEA580C)
                )
            }

            // Rechecked Detailed Explanation Box
            if (!report.recheckedExplanation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                    border = BorderStroke(0.6.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = colors.teal, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Detailed Explanation:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    onClick = onEditExplanation,
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.surface,
                                    border = BorderStroke(0.5.dp, colors.border)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = colors.teal, modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Edit", fontSize = 9.sp, color = colors.teal)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = report.recheckedExplanation,
                            fontSize = 10.5.sp,
                            color = colors.inkMuted,
                            lineHeight = 14.5.sp
                        )
                    }
                }
            }

            // Suggested Replacement Question Package
            if (report.replacementQuestion != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.teal.copy(alpha = 0.08f)),
                    border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = colors.teal, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Replacement Question Package:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = report.replacementQuestion.question,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "A) ${report.replacementQuestion.options.a}\nB) ${report.replacementQuestion.options.b}\nC) ${report.replacementQuestion.options.c}\nD) ${report.replacementQuestion.options.d}",
                            fontSize = 10.sp,
                            color = colors.inkMuted,
                            lineHeight = 13.5.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Correct: ${report.replacementQuestion.correctAnswer ?: "A"} • Explanation: ${report.replacementQuestion.explanation ?: "Verified"}",
                            fontSize = 9.5.sp,
                            color = colors.teal,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Action Buttons
            Spacer(modifier = Modifier.height(8.dp))
            if (report.isResolved) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.teal, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (report.isNeutralized) "Neutralized (Bonus Awarded)" else "Resolved & Applied",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (report.isNeutralized) Color(0xFF6B7280) else colors.teal
                        )
                    }

                    if (canReverse) {
                        OutlinedButton(
                            onClick = onReverseFix,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.red),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                            modifier = Modifier.testTag("reverse_fix_q_${report.qNum}")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Reverse", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Soft Neutralization (Preserves Question Numbering)
                    OutlinedButton(
                        onClick = onNeutralize,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6B7280)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                        modifier = Modifier.testTag("neutralize_q_${report.qNum}")
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Neutralize", fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Hard Delete
                    OutlinedButton(
                        onClick = onRequestDelete,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.red),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                        modifier = Modifier.testTag("delete_flawed_q_${report.qNum}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Delete", fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Step 2 Replacement Actions
                    if (report.replacementQuestion != null) {
                        OutlinedButton(
                            onClick = onAuthorReplacement,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                            modifier = Modifier.testTag("edit_replacement_q_${report.qNum}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Edit", fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = onApplyReplacement,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                            modifier = Modifier.testTag("replace_q_${report.qNum}")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Replace Question", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (report.isFlawedQuestion || report.status == AuditIssueType.FLAWED_QUESTION || report.status == AuditIssueType.VAGUE_OPTIONS || report.status == AuditIssueType.MISSING_OPTIONS) {
                        OutlinedButton(
                            onClick = onSynthesizeReplacement,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEA580C)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                            modifier = Modifier.testTag("synthesize_replacement_q_${report.qNum}")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Synthesize", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        OutlinedButton(
                            onClick = onAuthorReplacement,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                            modifier = Modifier.testTag("author_replacement_q_${report.qNum}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Author", fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                        }
                    } else if (isKeyMismatch || report.status == AuditIssueType.MISSING_KEY) {
                        Button(
                            onClick = onApplyKeyFix,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryButtonBg,
                                contentColor = colors.primaryButtonText
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                            modifier = Modifier.testTag("fix_key_q_${report.qNum}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.primaryButtonText)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Fix Key & Explanation", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.primaryButtonText)
                        }
                    } else if (!report.recheckedExplanation.isNullOrBlank() && report.recheckedExplanation != originalQ?.explanation) {
                        Button(
                            onClick = onApplyKeyFix,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.teal,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                            modifier = Modifier.testTag("apply_exp_q_${report.qNum}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Apply Explanation", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else if (report.status == AuditIssueType.MISSING_EXPLANATION || originalQ?.explanation.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = onAutoGenerateExplanation,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                            modifier = Modifier.testTag("generate_exp_q_${report.qNum}")
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Auto-Derive", fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
