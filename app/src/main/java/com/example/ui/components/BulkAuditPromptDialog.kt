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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Layers
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.example.model.AuditPhase
import com.example.model.QuestionPaper
import com.example.ui.theme.ExamTheme
import com.example.util.QuestionAuditHelper

@Composable
fun BulkAuditPromptDialog(
    paper: QuestionPaper,
    onDismiss: () -> Unit,
    onOpenResolutionHub: (initialText: String?) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colors = ExamTheme.colors

    var selectedPhase by remember { mutableStateOf(AuditPhase.PHASE_1_KEYS) }
    var auditScope by remember { mutableStateOf("UNVERIFIED") } // "UNVERIFIED" | "ALL" | "PENDING"

    val heuristicReports = remember(paper) {
        QuestionAuditHelper.runHeuristicPreAudit(paper)
    }

    val unverifiedQuestions = remember(paper) {
        paper.questions.filter { !it.isVerified && !it.isNeutralized }
    }

    val pendingQuestions = remember(paper) {
        paper.questions.filter {
            it.correctAnswer.isNullOrBlank() ||
                    it.explanation.isNullOrBlank() ||
                    it.hasMissingOptions ||
                    !it.options.isValid
        }
    }

    val flawedReports = remember(heuristicReports) {
        heuristicReports.filter {
            it.status == AuditIssueType.FLAWED_QUESTION ||
                    it.status == AuditIssueType.VAGUE_OPTIONS ||
                    it.status == AuditIssueType.MISSING_OPTIONS
        }
    }

    val keyCorrectionReports = remember(heuristicReports) {
        heuristicReports.filter {
            it.status == AuditIssueType.WRONG_KEY ||
                    it.status == AuditIssueType.MISSING_KEY ||
                    it.status == AuditIssueType.MISSING_EXPLANATION
        }
    }

    val phase1Questions = remember(paper, auditScope, unverifiedQuestions, pendingQuestions) {
        when (auditScope) {
            "ALL" -> paper.questions
            "PENDING" -> if (pendingQuestions.isNotEmpty()) pendingQuestions else paper.questions
            else -> if (unverifiedQuestions.isNotEmpty()) unverifiedQuestions else paper.questions
        }
    }

    val generatedPrompt = remember(paper, selectedPhase, phase1Questions, flawedReports, keyCorrectionReports) {
        when (selectedPhase) {
            AuditPhase.PHASE_1_KEYS -> QuestionAuditHelper.generatePhase1KeyAndFlawAuditPrompt(paper, phase1Questions)
            AuditPhase.PHASE_2_REPLACEMENTS -> {
                if (flawedReports.isNotEmpty()) {
                    QuestionAuditHelper.generatePhase2FlawedReplacementPrompt(paper, flawedReports)
                } else {
                    QuestionAuditHelper.generatePhase2FlawedReplacementPrompt(
                        paper,
                        heuristicReports.take(3) // Fallback demo questions if paper is clean
                    )
                }
            }
            AuditPhase.PHASE_3_EXPLANATIONS -> {
                QuestionAuditHelper.generatePhase3ExplanationAuditPrompt(paper, heuristicReports)
            }
        }
    }

    var copiedToClipboard by remember { mutableStateOf(false) }

    fun copyPrompt() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Exam Quality Audit Prompt - ${selectedPhase.shortTitle}", generatedPrompt)
        clipboard.setPrimaryClip(clip)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        copiedToClipboard = true
        Toast.makeText(context, "${selectedPhase.shortTitle} prompt copied to clipboard!", Toast.LENGTH_SHORT).show()
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
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.94f)
                .testTag("bulk_audit_prompt_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.tealLight,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "3-Phase Quality Auditor",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.teal.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${paper.questions.size} Questions",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.teal,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${paper.metadata.title} • Set ${paper.metadata.setNumber}",
                                fontSize = 11.5.sp,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("bulk_audit_close_button")
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

                // Phase Stepper Selector Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AuditPhaseTab(
                        phase = AuditPhase.PHASE_1_KEYS,
                        isSelected = selectedPhase == AuditPhase.PHASE_1_KEYS,
                        onClick = {
                            selectedPhase = AuditPhase.PHASE_1_KEYS
                            copiedToClipboard = false
                        },
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                    AuditPhaseTab(
                        phase = AuditPhase.PHASE_2_REPLACEMENTS,
                        isSelected = selectedPhase == AuditPhase.PHASE_2_REPLACEMENTS,
                        onClick = {
                            selectedPhase = AuditPhase.PHASE_2_REPLACEMENTS
                            copiedToClipboard = false
                        },
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                    AuditPhaseTab(
                        phase = AuditPhase.PHASE_3_EXPLANATIONS,
                        isSelected = selectedPhase == AuditPhase.PHASE_3_EXPLANATIONS,
                        onClick = {
                            selectedPhase = AuditPhase.PHASE_3_EXPLANATIONS
                            copiedToClipboard = false
                        },
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Phase Instructions & Scope Bar
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                    border = BorderStroke(0.6.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (selectedPhase) {
                                    AuditPhase.PHASE_1_KEYS -> "Step 1: Only Question & Answer Key check (verifies question stems, options, and marked keys; excludes explanations and key ideas)."
                                    AuditPhase.PHASE_2_REPLACEMENTS -> "Step 2: Generate brand-new MCQ replacements for flawed questions."
                                    AuditPhase.PHASE_3_EXPLANATIONS -> "Step 3: Comprehensive step-by-step explanations, core concepts, key ideas, and pedagogical review."
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                        }

                        if (selectedPhase == AuditPhase.PHASE_1_KEYS) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Audit Scope:",
                                    fontSize = 11.sp,
                                    color = colors.inkMuted
                                )
                                ScopeChip(
                                    label = "Unsealed (${unverifiedQuestions.size})",
                                    isSelected = auditScope == "UNVERIFIED",
                                    onClick = { auditScope = "UNVERIFIED" },
                                    colors = colors
                                )
                                ScopeChip(
                                    label = "All (${paper.questions.size})",
                                    isSelected = auditScope == "ALL",
                                    onClick = { auditScope = "ALL" },
                                    colors = colors
                                )
                                if (pendingQuestions.isNotEmpty()) {
                                    ScopeChip(
                                        label = "Draft Issues (${pendingQuestions.size})",
                                        isSelected = auditScope == "PENDING",
                                        onClick = { auditScope = "PENDING" },
                                        colors = colors
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Prompt Preview Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedPhase.shortTitle} Payload (${generatedPrompt.length} chars):",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold
                    )
                    Text(
                        text = "Raw JSON enclosed",
                        fontSize = 10.sp,
                        color = colors.muted
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = generatedPrompt,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.inkBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { copyPrompt() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bulk_audit_copy_button")
                    ) {
                        Icon(
                            imageVector = if (copiedToClipboard) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (copiedToClipboard) "Copied!" else "Copy Step Prompt",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            onOpenResolutionHub(null)
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryButtonBg,
                            contentColor = colors.primaryButtonText
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("bulk_audit_open_hub_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.primaryButtonText
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Resolution Hub",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryButtonText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditPhaseTab(
    phase: AuditPhase,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: com.example.ui.theme.ExamColorPalette,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) colors.primaryButtonBg else colors.surfaceVariant,
        border = BorderStroke(0.8.dp, if (isSelected) colors.primaryButtonBg else colors.border),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 5.dp, horizontal = 4.dp)
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Step ${phase.stepNumber}",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) colors.primaryButtonText.copy(alpha = 0.8f) else colors.muted
            )
            Text(
                text = when (phase) {
                    AuditPhase.PHASE_1_KEYS -> "Q&A Check"
                    AuditPhase.PHASE_2_REPLACEMENTS -> "Replacements"
                    AuditPhase.PHASE_3_EXPLANATIONS -> "Explanations"
                },
                fontSize = 10.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) colors.primaryButtonText else colors.inkBold,
                maxLines = 2,
                softWrap = true,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ScopeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: com.example.ui.theme.ExamColorPalette
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) colors.teal else colors.surface,
        border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border),
        modifier = Modifier.heightIn(min = 34.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else colors.inkMuted,
                maxLines = 1
            )
        }
    }
}
