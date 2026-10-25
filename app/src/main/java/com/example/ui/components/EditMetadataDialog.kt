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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.model.FolderItem
import com.example.model.PaperMetadata
import com.example.ui.theme.ExamTheme
import com.example.util.ExamCatalogueRegistry

@Composable
fun EditMetadataDialog(
    initialMetadata: PaperMetadata,
    folders: List<FolderItem> = emptyList(),
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        DisposableEffect(view, colors.isDark) {
            var parent = view.parent
            while (parent != null && parent !is DialogWindowProvider) {
                parent = parent.parent
            }
            (parent as? DialogWindowProvider)?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !colors.isDark
                insetsController.isAppearanceLightNavigationBars = !colors.isDark
            }
            onDispose {}
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                // Header
                Surface(
                    color = colors.surfaceVariant,
                    tonalElevation = 2.dp,
                    border = BorderStroke(0.5.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                            Column {
                                Text(
                                    text = "Edit Question Set Details",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Update title, subject, set number and OMR codes",
                                    fontSize = 11.sp,
                                    color = colors.muted
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.muted)
                        }
                    }
                }

                // Scrollable Form Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

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

                // Subject Input with Exam Catalogue Autocomplete & Hierarchy
                var isSubjectDropdownOpen by remember { mutableStateOf(false) }
                val catalogueSuggestions = remember(subject, folders) {
                    ExamCatalogueRegistry.searchCatalogue(subject, folders, maxResults = 12)
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = {
                            subject = it
                            isSubjectDropdownOpen = true
                        },
                        label = { Text("Subject / Exam Path (e.g. UPSC CSE / Prelims, SSC / CGL)") },
                        placeholder = { Text("e.g. UPSC CSE / Prelims, SSC / CGL, BPSC", fontSize = 12.sp, color = colors.muted) },
                        singleLine = true,
                        colors = textFieldColors,
                        trailingIcon = {
                            IconButton(
                                onClick = { isSubjectDropdownOpen = !isSubjectDropdownOpen },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Subject & Exam Autocomplete",
                                    tint = colors.muted
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_subject_field"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    DropdownMenu(
                        expanded = isSubjectDropdownOpen && catalogueSuggestions.isNotEmpty(),
                        onDismissRequest = { isSubjectDropdownOpen = false },
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        catalogueSuggestions.forEach { entry ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = colors.teal.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = entry.category,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.teal,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                },
                                text = {
                                    Text(
                                        text = entry.fullPath,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    subject = entry.fullPath
                                    isSubjectDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                if (subject.contains("/") || subject.contains(">")) {
                    val pathSegments = subject.split("/", ">").map { it.trim() }.filter { it.isNotBlank() }
                    if (pathSegments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.teal.copy(alpha = 0.08f),
                            border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SubdirectoryArrowRight,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Auto-Hierarchy: ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.tealDark
                                )
                                Text(
                                    text = pathSegments.joinToString(" ↳ "),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

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
                    label = { Text("Set No. (e.g. 01, 02)") },
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

                }

                // Pinned Bottom Action Bar
                Surface(
                    color = colors.surfaceVariant,
                    tonalElevation = 3.dp,
                    border = BorderStroke(0.5.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                val normalizedSet = com.example.util.ExamNumberGenerator.normalizeSetNumber(setNumber)
                                onSave(title, subject, topic, normalizedSet, rollNumber, bookletNumber)
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
}
