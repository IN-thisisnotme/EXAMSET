package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.PaperMetadata
import com.example.ui.theme.ExamTheme

@Composable
fun EditMetadataDialog(
    initialMetadata: PaperMetadata,
    onSave: (title: String, subject: String, topic: String, setNumber: String) -> Unit,
    onDismiss: () -> Unit,
    onEnterQuestionEditMode: (() -> Unit)? = null
) {
    val colors = ExamTheme.colors
    var title by remember { mutableStateOf(initialMetadata.title) }
    var subject by remember { mutableStateOf(initialMetadata.subject) }
    var topic by remember { mutableStateOf(initialMetadata.topic) }
    var setNumber by remember { mutableStateOf(initialMetadata.setNumber) }

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
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Edit Question Set Details",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.muted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val textFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.teal,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.inkBold,
                    unfocusedTextColor = colors.ink,
                    focusedLabelColor = colors.tealDark,
                    unfocusedLabelColor = colors.muted
                )

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Exam Title / Heading") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_title_field"),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Subject Input
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (e.g. Science, Mathematics, History)") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_subject_field"),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Topic / Unit Input
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic / Unit / Chapter (e.g. Thermodynamics)") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_topic_field"),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Set Number Input
                OutlinedTextField(
                    value = setNumber,
                    onValueChange = { setNumber = it },
                    label = { Text("Set No. (e.g. Set A, Set 1, Series B)") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_set_number_field"),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Permanent Identifiers (Immutable for authentic hand-circling practice)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = colors.muted,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Permanent Identifiers (For OMR Circling Practice)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }
                        Text(
                            text = "Assigned permanently per set to ensure authentic bubbling practice without accidental edits.",
                            fontSize = 10.sp,
                            color = colors.muted,
                            lineHeight = 13.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surface,
                                border = BorderStroke(0.8.dp, colors.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(7.dp)) {
                                    Text("Roll No (10-Digit)", fontSize = 9.5.sp, color = colors.muted, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = initialMetadata.rollNumber.ifBlank { "Unassigned" },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.teal
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surface,
                                border = BorderStroke(0.8.dp, colors.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(7.dp)) {
                                    Text("Test Booklet No (7-Digit)", fontSize = 9.5.sp, color = colors.muted, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = initialMetadata.bookletNumber.ifBlank { "Unassigned" },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.teal
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (onEnterQuestionEditMode != null) {
                    OutlinedButton(
                        onClick = {
                            onSave(title, subject, topic, setNumber)
                            onDismiss()
                            onEnterQuestionEditMode()
                        },
                        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Questions, Keys & Explanations", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, colors.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                        modifier = Modifier.testTag("cancel_metadata_button")
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            onSave(title, subject, topic, setNumber)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.navyBanner,
                            contentColor = colors.navyBannerText
                        ),
                        modifier = Modifier.testTag("save_metadata_button")
                    ) {
                        Text("Save Details")
                    }
                }
            }
        }
    }
}
