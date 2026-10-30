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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.PopupProperties
import com.example.model.ExamConfig
import com.example.model.FolderItem
import com.example.model.PaperMetadata
import com.example.ui.theme.ExamTheme
import com.example.util.ExamCatalogueRegistry

@Composable
fun EditMetadataDialog(
    initialMetadata: PaperMetadata,
    folders: List<FolderItem> = emptyList(),
    initialConfig: ExamConfig = ExamConfig(),
    onSave: (title: String, subject: String, topic: String, setNumber: String, rollNumber: String, bookletNumber: String) -> Unit,
    onSaveConfig: ((ExamConfig) -> Unit)? = null,
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
    var durationMinutesStr by remember {
        val initialMins = initialConfig.durationMinutes.takeIf { it > 0 }
            ?: (Regex("""\b(\d+)\b""").find(initialConfig.timeAllowed)?.groupValues?.get(1)?.toIntOrNull() ?: 60)
        mutableStateOf(initialMins.toString())
    }
    var positiveMarksStr by remember {
        mutableStateOf(String.format(java.util.Locale.US, "%.1f", initialConfig.positiveMarks))
    }
    var negativeMarksStr by remember {
        mutableStateOf(String.format(java.util.Locale.US, "%.2f", initialConfig.negativeMarks))
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
                        onValueChange = { input ->
                            // Cleanly normalize any / to > without opening focus-stealing popups
                            subject = input.replace("/", " > ")
                        },
                        label = { Text("Subject / Exam Path (e.g. UPSC CSE > Prelims, SSC > CGL)") },
                        placeholder = { Text("e.g. UPSC CSE > Prelims, SSC > CGL, BPSC", fontSize = 12.sp, color = colors.muted) },
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
                        properties = PopupProperties(focusable = false),
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

                // Non-intrusive suggestion chips below text field: fast tap-to-select without dismissing keyboard
                if (subject.isNotBlank() && catalogueSuggestions.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        items(catalogueSuggestions.take(6)) { entry ->
                            val isMatch = entry.fullPath.equals(subject.trim(), ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isMatch) colors.tealLight else colors.surfaceVariant,
                                border = BorderStroke(0.7.dp, if (isMatch) colors.teal else colors.border),
                                modifier = Modifier.clickable {
                                    subject = entry.fullPath
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = colors.teal.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = entry.category,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.teal,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = entry.fullPath.substringAfterLast(">").substringAfterLast("/").trim(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.inkBold
                                    )
                                }
                            }
                        }
                    }
                }

                if (subject.contains(">") || subject.contains("/")) {
                    val pathSegments = subject.split(">", "/").map { it.trim() }.filter { it.isNotBlank() }
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
                                    text = "Hierarchy: ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.tealDark
                                )
                                Text(
                                    text = pathSegments.joinToString(" › "),
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

                Spacer(modifier = Modifier.height(12.dp))

                // Examination Duration & Marking Parameters Section
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Exam Duration & Marking Scheme",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }
                        Text(
                            text = "Sets default examination time and scoring parameters for CBT HTML tests and printed papers.",
                            fontSize = 10.sp,
                            color = colors.muted,
                            lineHeight = 13.sp
                        )

                        // Duration, Positive Marks & Negative Penalty in 3 responsive fields
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = durationMinutesStr,
                                onValueChange = { input ->
                                    durationMinutesStr = input.filter { it.isDigit() }.take(4)
                                },
                                label = { Text("Duration (m)", fontSize = 10.5.sp) },
                                placeholder = { Text("60", fontSize = 10.5.sp) },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = positiveMarksStr,
                                onValueChange = { input ->
                                    if (input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                        positiveMarksStr = input
                                    }
                                },
                                label = { Text("Correct (+)", fontSize = 10.5.sp) },
                                placeholder = { Text("4.0", fontSize = 10.5.sp) },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = negativeMarksStr,
                                onValueChange = { input ->
                                    if (input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                        negativeMarksStr = input
                                    }
                                },
                                label = { Text("Penalty (-)", fontSize = 10.5.sp) },
                                placeholder = { Text("1.0", fontSize = 10.5.sp) },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        // Universal & Regional Scoring Scheme Presets
                        var scoringPresetCategory by remember { mutableStateOf("Universal") }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Marking Presets:", fontSize = 10.sp, color = colors.muted, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Universal", "Regional").forEach { cat ->
                                    val isCatSelected = scoringPresetCategory == cat
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isCatSelected) colors.teal.copy(alpha = 0.2f) else colors.surface,
                                        border = BorderStroke(0.6.dp, if (isCatSelected) colors.teal else colors.border),
                                        modifier = Modifier.clickable { scoringPresetCategory = cat }
                                    ) {
                                        Text(
                                            text = cat,
                                            fontSize = 9.sp,
                                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCatSelected) colors.tealDark else colors.ink,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Presets List
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val activePresets = if (scoringPresetCategory == "Universal") {
                                listOf(
                                    Triple("1 Pt (No Neg)", 1.0, 0.0),
                                    Triple("1 Pt (1/4 Neg)", 1.0, 0.25),
                                    Triple("1 Pt (1/3 Neg)", 1.0, 0.33),
                                    Triple("2 Pts (No Neg)", 2.0, 0.0),
                                    Triple("2 Pts (1/4 Neg)", 2.0, 0.50),
                                    Triple("4 Pts (1/4 Neg)", 4.0, 1.00)
                                )
                            } else {
                                listOf(
                                    Triple("NEET/JEE (+4, -1)", 4.0, 1.0),
                                    Triple("SSC/RRB (+2, -0.5)", 2.0, 0.5),
                                    Triple("UPSC GS (+2, -0.67)", 2.0, 0.67),
                                    Triple("Banking (+1, -0.25)", 1.0, 0.25),
                                    Triple("GATE (+1, -0.33)", 1.0, 0.33)
                                )
                            }
                            items(activePresets) { (label, pos, neg) ->
                                val isSelected = positiveMarksStr == pos.toString() && negativeMarksStr == neg.toString()
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) colors.tealLight else colors.surface,
                                    border = BorderStroke(0.8.dp, if (isSelected) colors.teal else colors.border),
                                    modifier = Modifier.clickable {
                                        positiveMarksStr = pos.toString()
                                        negativeMarksStr = neg.toString()
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) colors.tealDark else colors.ink,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Quick duration preset chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Duration:", fontSize = 10.sp, color = colors.muted, fontWeight = FontWeight.Bold)
                            listOf(30, 45, 60, 90, 120, 180).forEach { mins ->
                                val isSelected = durationMinutesStr == mins.toString()
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) colors.teal else colors.surface,
                                    border = BorderStroke(0.8.dp, if (isSelected) colors.teal else colors.border),
                                    modifier = Modifier.clickable { durationMinutesStr = mins.toString() }
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else colors.ink,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (onEnterQuestionEditMode != null) {
                    OutlinedButton(
                        onClick = {
                            val normalizedSet = com.example.util.ExamNumberGenerator.normalizeSetNumber(setNumber)
                            val cleanSubject = ExamCatalogueRegistry.normalizeFolderPath(subject)
                            val parsedDuration = durationMinutesStr.toIntOrNull()?.coerceIn(5, 360) ?: 60
                            val parsedPosMarks = positiveMarksStr.toDoubleOrNull()?.coerceIn(0.25, 20.0) ?: 4.0
                            val parsedNegMarks = negativeMarksStr.toDoubleOrNull()?.coerceIn(0.0, 10.0) ?: 1.0

                            onSave(title, cleanSubject, topic, normalizedSet, rollNumber, bookletNumber)
                            onSaveConfig?.invoke(
                                initialConfig.copy(
                                    examTitle = title.ifBlank { initialConfig.examTitle },
                                    subject = cleanSubject.ifBlank { initialConfig.subject },
                                    topic = topic,
                                    seriesCode = normalizedSet,
                                    durationMinutes = parsedDuration,
                                    timeAllowed = "$parsedDuration Minutes",
                                    positiveMarks = parsedPosMarks,
                                    negativeMarks = parsedNegMarks,
                                    negativeMarking = String.format(java.util.Locale.US, "%.2f Marks", parsedNegMarks)
                                )
                            )
                            onDismiss()
                            onEnterQuestionEditMode()
                        },
                        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Questions, Keys & Explanations", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, softWrap = true)
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
                            modifier = Modifier
                                .testTag("cancel_metadata_button")
                                .heightIn(min = 44.dp)
                        ) {
                            Text("Cancel")
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = {
                                val normalizedSet = com.example.util.ExamNumberGenerator.normalizeSetNumber(setNumber)
                                val cleanSubject = ExamCatalogueRegistry.normalizeFolderPath(subject)
                                val parsedDuration = durationMinutesStr.toIntOrNull()?.coerceIn(5, 360) ?: 60
                                val parsedPosMarks = positiveMarksStr.toDoubleOrNull()?.coerceIn(0.25, 20.0) ?: 4.0
                                val parsedNegMarks = negativeMarksStr.toDoubleOrNull()?.coerceIn(0.0, 10.0) ?: 1.0

                                onSave(title, cleanSubject, topic, normalizedSet, rollNumber, bookletNumber)
                                onSaveConfig?.invoke(
                                    initialConfig.copy(
                                        examTitle = title.ifBlank { initialConfig.examTitle },
                                        subject = cleanSubject.ifBlank { initialConfig.subject },
                                        topic = topic,
                                        seriesCode = normalizedSet,
                                        durationMinutes = parsedDuration,
                                        timeAllowed = "$parsedDuration Minutes",
                                        positiveMarks = parsedPosMarks,
                                        negativeMarks = parsedNegMarks,
                                        negativeMarking = String.format(java.util.Locale.US, "%.2f Marks", parsedNegMarks)
                                    )
                                )
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.navyBanner,
                                contentColor = colors.navyBannerText
                            ),
                            modifier = Modifier
                                .testTag("save_metadata_button")
                                .heightIn(min = 44.dp)
                        ) {
                            Text("Save Details")
                        }
                    }
                }
            }
        }
    }
}
