package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Speed
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

    val generatedPrompt = remember(paper) {
        QuestionAuditHelper.generateBulkAuditPrompt(paper)
    }

    var copiedToClipboard by remember { mutableStateOf(false) }

    fun copyPrompt() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Exam Quality Audit Prompt", generatedPrompt)
        clipboard.setPrimaryClip(clip)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        copiedToClipboard = true
        Toast.makeText(context, "Audit prompt copied to clipboard! Paste into ChatGPT or Gemini.", Toast.LENGTH_SHORT).show()
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
                .fillMaxHeight(0.92f)
                .testTag("bulk_audit_prompt_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
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
                                    imageVector = Icons.Default.AutoAwesome,
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
                                    text = "AI Bulk Quality Auditor",
                                    fontSize = 16.sp,
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

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Explanatory Workflow Box
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                    border = BorderStroke(0.6.dp, colors.border)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "How to use Bulk AI Audit:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "1. Tap \"Copy Audit Prompt\" below.\n2. Paste into ChatGPT, Gemini, or Claude to audit all questions in one go.\n3. Tap \"Open Resolution Hub\" to paste the feedback, review rechecked explanations, apply answer key fixes, or safely delete flawed questions.",
                                fontSize = 11.sp,
                                color = colors.inkMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Prompt Preview Card
                Text(
                    text = "Prompt Preview (${generatedPrompt.length} characters):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = generatedPrompt,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.inkBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                            text = if (copiedToClipboard) "Copied!" else "Copy Prompt",
                            fontSize = 12.sp,
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
                            .weight(1.3f)
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryButtonText
                        )
                    }
                }
            }
        }
    }
}
