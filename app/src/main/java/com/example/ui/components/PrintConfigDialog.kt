package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ExamConfig
import com.example.model.OmrBubblingMode
import com.example.model.PrintMode
import com.example.model.QuestionPaper
import com.example.util.ExamNumberGenerator
import com.example.util.PrintDocumentHelper
import com.example.ui.theme.ExamTheme

@Composable
fun PrintConfigDialog(
    paper: QuestionPaper,
    initialConfig: ExamConfig,
    onDismiss: () -> Unit,
    onPrintTriggered: (ExamConfig) -> Unit
) {
    val context = LocalContext.current
    val colors = ExamTheme.colors
    var config by remember { mutableStateOf(initialConfig) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Dialog Title Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Print Examination Paper",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.muted)
                    }
                }

                Text(
                    text = "Configure student question set for printing on A4 paper",
                    fontSize = 12.sp,
                    color = colors.muted
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Selector Chips (Student Paper vs Master Booklet)
                Text(
                    text = "Select Document Type",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModeSelectorPill(
                        title = "Student Paper",
                        subtitle = "Exam ready (No ans)",
                        isSelected = config.printMode == PrintMode.STUDENT_PAPER,
                        onClick = { config = config.copy(printMode = PrintMode.STUDENT_PAPER) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeSelectorPill(
                        title = "OMR Only",
                        subtitle = "A4 Answer Sheet",
                        isSelected = config.printMode == PrintMode.OMR_ONLY,
                        onClick = { config = config.copy(printMode = PrintMode.OMR_ONLY) },
                        modifier = Modifier.weight(1f)
                    )
                    ModeSelectorPill(
                        title = "Master Booklet",
                        subtitle = "Full PDF + Sol",
                        isSelected = config.printMode == PrintMode.MASTER_BOOKLET,
                        onClick = { config = config.copy(printMode = PrintMode.MASTER_BOOKLET) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val tfColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.teal,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.inkBold,
                    unfocusedTextColor = colors.ink
                )

                // Institution / Organization Name
                OutlinedTextField(
                    value = config.institutionName,
                    onValueChange = { config = config.copy(institutionName = it) },
                    label = { Text("Institution / Exam Board") },
                    singleLine = true,
                    colors = tfColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("institution_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Exam Title
                OutlinedTextField(
                    value = config.examTitle,
                    onValueChange = { config = config.copy(examTitle = it) },
                    label = { Text("Examination Title") },
                    singleLine = true,
                    colors = tfColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_title_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Series & Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = config.seriesCode,
                        onValueChange = { config = config.copy(seriesCode = it) },
                        label = { Text("Set / Code") },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = config.timeAllowed,
                        onValueChange = { config = config.copy(timeAllowed = it) },
                        label = { Text("Time Allowed") },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Max Marks & Negative Marking Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = config.maxMarks,
                        onValueChange = { config = config.copy(maxMarks = it) },
                        label = { Text("Max Marks") },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = config.negativeMarking,
                        onValueChange = { config = config.copy(negativeMarking = it) },
                        label = { Text("Negative Mark") },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Layout and Print Options
                if (config.printMode == PrintMode.STUDENT_PAPER || config.printMode == PrintMode.MASTER_BOOKLET) {
                    Text(
                        text = if (config.printMode == PrintMode.STUDENT_PAPER) "Examination Paper Options" else "Master Booklet Print Options",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (config.printMode == PrintMode.STUDENT_PAPER) {
                        ConfigSwitchRow(
                            title = "Candidate Details Box",
                            subtitle = "Includes Name & Signature fields (no roll number)",
                            checked = config.includeCandidateDetails,
                            onCheckedChange = { config = config.copy(includeCandidateDetails = it) }
                        )

                        ConfigSwitchRow(
                            title = "General Instructions",
                            subtitle = "Standard examination rules at top",
                            checked = config.includeInstructions,
                            onCheckedChange = { config = config.copy(includeInstructions = it) }
                        )

                        ConfigSwitchRow(
                            title = "Printable OMR Answer Sheet",
                            subtitle = "Authentic UPSC/NEET magenta sheet with continuous 50-row columns",
                            checked = config.includeOmrSheet,
                            onCheckedChange = { config = config.copy(includeOmrSheet = it) }
                        )
                    }

                    ConfigSwitchRow(
                        title = "2-Column Examination Layout",
                        subtitle = "Paper-saving 2-column continuous format for questions",
                        checked = config.twoColumns,
                        onCheckedChange = { config = config.copy(twoColumns = it) }
                    )
                }

                // OMR Sheet Options
                val showOmrOptions = config.printMode == PrintMode.OMR_ONLY ||
                        (config.printMode == PrintMode.STUDENT_PAPER && config.includeOmrSheet)
                if (showOmrOptions) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "OMR Sheet Options",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    ConfigSwitchRow(
                        title = "Autofill Roll Number",
                        subtitle = "Pre-fill roll number digits (leave OFF to use sheet for any question set)",
                        checked = config.omrBubblingMode != OmrBubblingMode.BLANK_CANDIDATE,
                        onCheckedChange = { isChecked ->
                            config = config.copy(
                                omrBubblingMode = if (isChecked) OmrBubblingMode.PRE_PRINTED_DIGITS else OmrBubblingMode.BLANK_CANDIDATE
                            )
                        },
                        modifier = Modifier.testTag("omr_autofill_roll_switch")
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Unified Action Buttons: Save PDF to File Manager and Print via System Spooler
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Direct Save PDF Button
                    OutlinedButton(
                        onClick = {
                            onPrintTriggered(config)
                            PrintDocumentHelper.savePdfDirectly(
                                context = context,
                                paper = paper,
                                config = config,
                                onSuccess = { savedPath ->
                                    android.widget.Toast.makeText(
                                        context,
                                        "Saved to $savedPath",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                },
                                onError = { errorMsg ->
                                    android.widget.Toast.makeText(
                                        context,
                                        errorMsg,
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.inkBold
                        ),
                        border = BorderStroke(1.2.dp, colors.teal),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_pdf_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = "Save PDF",
                            modifier = Modifier.size(18.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save PDF",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                    }

                    // Print via System Spooler Button
                    Button(
                        onClick = {
                            onPrintTriggered(config)
                            PrintDocumentHelper.printQuestionPaper(context, paper, config)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.navyBanner,
                            contentColor = colors.navyBannerText
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("start_print_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Print",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelectorPill(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        modifier = modifier
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) colors.tealLight else Color.Transparent,
        border = BorderStroke(1.2.dp, if (isSelected) colors.teal else colors.border)
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) colors.tealDark else colors.inkBold,
                maxLines = 2,
                softWrap = true,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = colors.muted,
                lineHeight = 13.sp,
                maxLines = 2,
                softWrap = true,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConfigSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = colors.ink)
            Text(text = subtitle, fontSize = 10.5.sp, color = colors.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.teal
            )
        )
    }
}
