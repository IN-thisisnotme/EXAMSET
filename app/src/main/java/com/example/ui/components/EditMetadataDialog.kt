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
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.PaperMetadata
import com.example.ui.theme.ExamTheme

@Composable
fun EditMetadataDialog(
    initialMetadata: PaperMetadata,
    onSave: (title: String, subject: String, topic: String, setNumber: String, rollNumber: String, bookletNumber: String) -> Unit,
    onDismiss: () -> Unit,
    onEnterQuestionEditMode: (() -> Unit)? = null
) {
    val colors = ExamTheme.colors
    var title by remember { mutableStateOf(initialMetadata.title) }
    var subject by remember { mutableStateOf(initialMetadata.subject) }
    var topic by remember { mutableStateOf(initialMetadata.topic) }
    var setNumber by remember { mutableStateOf(initialMetadata.setNumber) }
    var rollNumber by remember {
        mutableStateOf(
            if (initialMetadata.rollNumber.isNotBlank()) initialMetadata.rollNumber else com.example.util.ExamNumberGenerator.generateRollNumber()
        )
    }
    var bookletNumber by remember {
        mutableStateOf(
            if (initialMetadata.bookletNumber.isNotBlank()) initialMetadata.bookletNumber else com.example.util.ExamNumberGenerator.generateBookletNumber()
        )
    }

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
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Edit Question Set Details",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

                // OMR Identification & Question Paper Numbering Section
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "OMR Identification & Numbering",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }
                        Text(
                            text = "Assigned directly per question set so printed papers and OMR sheets are permanently synchronized.",
                            fontSize = 10.sp,
                            color = colors.muted,
                            lineHeight = 13.sp
                        )

                        // Roll Number Row with Generate Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = rollNumber,
                                onValueChange = { rollNumber = it.filter { ch -> ch.isDigit() }.take(10) },
                                label = { Text("Roll No (10 digits)") },
                                placeholder = { Text("e.g. 2026104821") },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("edit_roll_number_field"),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    rollNumber = com.example.util.ExamNumberGenerator.generateRollNumber()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.tealLight,
                                    contentColor = colors.tealDark
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("edit_generate_roll_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Booklet Number Row with Generate Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = bookletNumber,
                                onValueChange = { bookletNumber = it.filter { ch -> ch.isDigit() }.take(7) },
                                label = { Text("Booklet No (7 digits)") },
                                placeholder = { Text("e.g. 7104523") },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("edit_booklet_number_field"),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    bookletNumber = com.example.util.ExamNumberGenerator.generateBookletNumber()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.tealLight,
                                    contentColor = colors.tealDark
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("edit_generate_booklet_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (onEnterQuestionEditMode != null) {
                    OutlinedButton(
                        onClick = {
                            onSave(title, subject, topic, setNumber, rollNumber, bookletNumber)
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
                            onSave(title, subject, topic, setNumber, rollNumber, bookletNumber)
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
