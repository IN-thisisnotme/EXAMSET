package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PrintMode
import com.example.ui.theme.ExamTheme
import com.example.util.ExamExportDestination
import com.example.util.SavedExamExportResult

@Composable
fun ExportDestinationPreviewCard(
    destination: ExamExportDestination,
    printMode: PrintMode? = null,
    showHeader: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("export_destination_preview_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Save destination preview",
                        tint = colors.teal,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SAVE DESTINATION PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.tealDark,
                        maxLines = 1,
                        softWrap = false
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.tealLight
                    ) {
                        Text(
                            text = destination.format.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (printMode != null) {
                        val modeLabel = when (printMode) {
                            PrintMode.STUDENT_PAPER -> "Student Paper"
                            PrintMode.OMR_ONLY -> "OMR Sheet"
                            PrintMode.MASTER_BOOKLET -> "Master Booklet"
                            PrintMode.SOLUTIONS_ONLY -> "Solutions Only"
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.surface
                        ) {
                            Text(
                                text = modeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkMuted,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.border)
            }

            DestinationOrderedRow(
                step = "1.",
                label = "Storage Root",
                value = destination.rootDirectory
            )
            DestinationOrderedRow(
                step = "2.",
                label = "Year & Month",
                value = "${destination.yearFolder}  →  ${destination.monthFolderLabel}"
            )
            DestinationOrderedRow(
                step = "3.",
                label = "Subject Folder",
                value = "${destination.subjectFolder}/"
            )
            DestinationOrderedRow(
                step = "4.",
                label = "File Name",
                value = destination.fileName,
                highlightValue = true
            )

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "5. Full Device Path",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surface,
                    border = BorderStroke(0.8.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = destination.fullRelativePath,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }

            Text(
                text = "Note: If a file with the same name already exists, Android keeps both by auto-numbering the new copy (e.g. (1)).",
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = colors.inkMuted
            )
        }
    }
}

@Composable
private fun DestinationOrderedRow(
    step: String,
    label: String,
    value: String,
    highlightValue: Boolean = false
) {
    val colors = ExamTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$step $label",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.inkMuted,
            modifier = Modifier.width(108.dp)
        )
        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = if (highlightValue) FontWeight.Bold else FontWeight.SemiBold,
            color = if (highlightValue) colors.tealDark else colors.inkBold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SavedExportResultBanner(
    result: SavedExamExportResult,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.greenLight,
        border = BorderStroke(1.dp, colors.green.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("saved_export_result_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "File saved successfully",
                        tint = colors.green,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Saved: ${result.actualFileName}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = result.displayPath,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.ink
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dismiss_saved_export_banner_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss saved notice",
                        tint = colors.inkMuted
                    )
                }
            }

            if (result.contentUriString != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpen,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.green,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("open_saved_export_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onShare,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        border = BorderStroke(1.dp, colors.green),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("share_saved_export_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = colors.green,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Share",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                    }
                }
            }
        }
    }
}
