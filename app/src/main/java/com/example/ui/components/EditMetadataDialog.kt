package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.example.model.ExamConfig
import com.example.model.FolderItem
import com.example.model.MsqScoringPolicy
import com.example.model.PaperCategory
import com.example.model.PaperMetadata
import com.example.ui.theme.ExamTheme
import com.example.util.FolderPathHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditMetadataDialog(
    initialMetadata: PaperMetadata,
    folders: List<FolderItem> = emptyList(),
    initialConfig: ExamConfig = ExamConfig(),
    onSave: (title: String, subject: String, topic: String, setNumber: String, rollNumber: String, bookletNumber: String, manualCategory: String) -> Unit,
    onSaveConfig: ((ExamConfig) -> Unit)? = null,
    onDismiss: () -> Unit,
    onEnterQuestionEditMode: (() -> Unit)? = null
) {
    val colors = ExamTheme.colors
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var title by remember { mutableStateOf(initialMetadata.title) }
    var subject by remember { mutableStateOf(initialMetadata.subject) }
    var topic by remember { mutableStateOf(initialMetadata.topic) }
    var setNumber by remember { mutableStateOf(initialMetadata.setNumber) }
    var selectedCategory by remember(initialMetadata.manualCategory) {
        mutableStateOf(PaperCategory.fromCode(initialMetadata.manualCategory))
    }
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
        val pos = initialConfig.positiveMarks
        mutableStateOf(
            if (pos % 1.0 == 0.0) String.format(java.util.Locale.US, "%.1f", pos)
            else String.format(java.util.Locale.US, "%.2f", pos)
        )
    }
    var negativeMarksStr by remember {
        mutableStateOf(String.format(java.util.Locale.US, "%.2f", initialConfig.negativeMarks))
    }
    var unattendedMarksStr by remember {
        val initialUnk = if (initialMetadata.unattendedMarks > 0.0) initialMetadata.unattendedMarks else initialConfig.unattendedMarks
        mutableStateOf(String.format(java.util.Locale.US, "%.2f", initialUnk))
    }
    var msqScoringPolicy by remember(initialMetadata.id, initialMetadata.msqScoringPolicy, initialConfig.msqScoringPolicy) {
        mutableStateOf(
            if (initialMetadata.id.isNotBlank() || initialMetadata.msqScoringPolicy != MsqScoringPolicy.ALL_OR_NOTHING) {
                initialMetadata.msqScoringPolicy
            } else {
                initialConfig.msqScoringPolicy
            }
        )
    }

    fun persistEditsAndClose(enterQuestionEditor: Boolean = false) {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)

        val normalizedSet = com.example.util.ExamNumberGenerator.normalizeSetNumber(setNumber)
        val cleanSubject = FolderPathHelper.normalizeFolderPath(subject)
        val parsedDuration = durationMinutesStr.toIntOrNull()?.coerceIn(5, 360) ?: 60
        val parsedPosMarks = positiveMarksStr.toDoubleOrNull()?.coerceIn(0.25, 20.0) ?: 4.0
        val parsedNegMarks = negativeMarksStr.toDoubleOrNull()?.coerceIn(0.0, 10.0) ?: 1.0
        val parsedUnkMarks = unattendedMarksStr.toDoubleOrNull()?.coerceIn(0.0, 10.0) ?: 0.0

        onSave(title, cleanSubject, topic, normalizedSet, rollNumber, bookletNumber, selectedCategory.code)
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
                unattendedMarks = parsedUnkMarks,
                msqScoringPolicy = msqScoringPolicy,
                negativeMarking = String.format(java.util.Locale.US, "%.2f Marks", parsedNegMarks)
            )
        )
        onDismiss()
        if (enterQuestionEditor) {
            onEnterQuestionEditMode?.invoke()
        }
    }

    BackHandler {
        keyboardController?.hide()
        onDismiss()
    }

    val headerGradient = remember(colors.isDark) {
        Brush.horizontalGradient(
            colors = listOf(colors.teal, colors.navyBanner)
        )
    }

    // Activity-hosted full-screen Material 3 Sheet (NO Dialog window) so status, nav & IME insets never clip
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Surface(
                    color = colors.surfaceVariant,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(headerGradient)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("close_metadata_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = colors.inkBold
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Edit Question Set Details",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Metadata, OMR codes, duration & marking",
                                        fontSize = 11.sp,
                                        color = colors.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Button(
                                onClick = { persistEditsAndClose(enterQuestionEditor = false) },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.teal,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .heightIn(min = 48.dp)
                                    .testTag("top_save_metadata_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Save",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = colors.surfaceVariant,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.navigationBars
                                    .union(WindowInsets.ime)
                                    .union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            )
                    ) {
                        HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    keyboardController?.hide()
                                    onDismiss()
                                },
                                border = BorderStroke(1.dp, colors.border),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .testTag("cancel_metadata_button")
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Cancel", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }

                            Button(
                                onClick = { persistEditsAndClose(enterQuestionEditor = false) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.navyBanner,
                                    contentColor = colors.navyBannerText
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .testTag("save_metadata_button")
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Details", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_title_field"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Subject Input with User Folder Hierarchy Autocomplete (Parent > Child > Sub-Child)
                    var isSubjectDropdownOpen by remember { mutableStateOf(false) }
                    val folderSuggestions = remember(subject, folders) {
                        FolderPathHelper.searchUserFolders(subject, folders, maxResults = 12)
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { input ->
                                subject = input.replace("/", " > ")
                            },
                            label = { Text("Subject / Folder Path (e.g. Parent > Child)") },
                            placeholder = { Text("e.g. Parent > Child > Sub-Child", fontSize = 12.sp, color = colors.muted) },
                            singleLine = true,
                            colors = textFieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            trailingIcon = {
                                if (folderSuggestions.isNotEmpty()) {
                                    IconButton(
                                        onClick = { isSubjectDropdownOpen = !isSubjectDropdownOpen },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "User Folder Path Suggestions",
                                            tint = colors.muted
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_subject_field"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        DropdownMenu(
                            expanded = isSubjectDropdownOpen && folderSuggestions.isNotEmpty(),
                            onDismissRequest = { isSubjectDropdownOpen = false },
                            properties = PopupProperties(focusable = false),
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            folderSuggestions.forEach { entry ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = colors.teal.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = entry.levelLabel,
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
                    if (subject.isNotBlank() && folderSuggestions.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(folderSuggestions.take(6)) { entry ->
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
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = colors.teal.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = entry.levelLabel,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.teal,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = entry.leafName,
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
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.teal.copy(alpha = 0.08f),
                                border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SubdirectoryArrowRight,
                                        contentDescription = null,
                                        tint = colors.teal,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Hierarchy: ",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.tealDark
                                    )
                                    Text(
                                        text = pathSegments.joinToString(" > "),
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

                    // Topic / Unit Input
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Topic / Unit / Chapter (e.g. Thermodynamics)") },
                        singleLine = true,
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_topic_field"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Set Number Input
                    OutlinedTextField(
                        value = setNumber,
                        onValueChange = { setNumber = it },
                        label = { Text("Set No. (e.g. 01, 02)") },
                        singleLine = true,
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_set_number_field"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // OMR Identification & Question Paper Numbering Section
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "OMR Identification & Numbering",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                            }
                            Text(
                                text = "Assigned directly per question set so printed papers and OMR sheets are permanently synchronized.",
                                fontSize = 10.5.sp,
                                color = colors.muted,
                                lineHeight = 14.sp
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
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("edit_roll_number_field"),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Button(
                                    onClick = {
                                        rollNumber = com.example.util.ExamNumberGenerator.generateRollNumber()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.tealLight,
                                        contentColor = colors.tealDark
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("edit_generate_roll_button")
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("edit_booklet_number_field"),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Button(
                                    onClick = {
                                        bookletNumber = com.example.util.ExamNumberGenerator.generateBookletNumber()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.tealLight,
                                        contentColor = colors.tealDark
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("edit_generate_booklet_button")
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Manual Test Classification
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Manual Test Classification",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                PaperCategory.AUTO,
                                PaperCategory.MOCK_TEST,
                                PaperCategory.PYQ,
                                PaperCategory.SECTIONAL
                            ).forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.label, fontSize = 11.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.tealLight,
                                        selectedLabelColor = colors.tealDark
                                    ),
                                    modifier = Modifier.testTag("edit_category_${cat.code.lowercase()}")
                                )
                            }
                        }
                    }

                    // Examination Duration & Marking Parameters Section
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Exam Duration & Marking Scheme",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                            }
                            Text(
                                text = "Sets default examination time and scoring parameters for CBT HTML tests and printed papers.",
                                fontSize = 10.5.sp,
                                color = colors.muted,
                                lineHeight = 14.sp
                            )

                            // Duration, Positive Marks, Negative Penalty & Unattended Penalty in responsive 2x2 grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = durationMinutesStr,
                                    onValueChange = { input ->
                                        durationMinutesStr = input.filter { it.isDigit() }.take(4)
                                    },
                                    label = { Text("Duration (m)", fontSize = 11.sp) },
                                    placeholder = { Text("60", fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = textFieldColors,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = positiveMarksStr,
                                    onValueChange = { input ->
                                        if (input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                            positiveMarksStr = input
                                        }
                                    },
                                    label = { Text("Correct (+)", fontSize = 11.sp) },
                                    placeholder = { Text("4.0", fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = textFieldColors,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = negativeMarksStr,
                                    onValueChange = { input ->
                                        if (input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                            negativeMarksStr = input
                                        }
                                    },
                                    label = { Text("Penalty (-)", fontSize = 11.sp) },
                                    placeholder = { Text("1.00", fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = textFieldColors,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = unattendedMarksStr,
                                    onValueChange = { input ->
                                        if (input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                            unattendedMarksStr = input
                                        }
                                    },
                                    label = { Text("Unattended (-)", fontSize = 11.sp) },
                                    placeholder = { Text("0.00", fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = textFieldColors,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus(force = true)
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("edit_unattended_marks_field"),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            // Universal & Regional Scoring Scheme Presets
                            var scoringPresetCategory by remember { mutableStateOf("Universal") }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Marking Presets:", fontSize = 10.5.sp, color = colors.muted, fontWeight = FontWeight.Bold)
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
                                                fontSize = 9.5.sp,
                                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCatSelected) colors.tealDark else colors.ink,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Presets List
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                    val isSelected = positiveMarksStr.toDoubleOrNull() == pos && negativeMarksStr.toDoubleOrNull() == neg
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) colors.tealLight else colors.surface,
                                        border = BorderStroke(0.8.dp, if (isSelected) colors.teal else colors.border),
                                        modifier = Modifier.clickable {
                                            positiveMarksStr = String.format(java.util.Locale.US, "%.1f", pos)
                                            negativeMarksStr = String.format(java.util.Locale.US, "%.2f", neg)
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) colors.tealDark else colors.ink,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Quick duration preset chips (wraps cleanly under large font sizes)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Duration:",
                                    fontSize = 10.5.sp,
                                    color = colors.muted,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                listOf(30, 45, 60, 90, 120, 180).forEach { mins ->
                                    val isSelected = durationMinutesStr == mins.toString()
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) colors.teal else colors.surface,
                                        border = BorderStroke(0.8.dp, if (isSelected) colors.teal else colors.border),
                                        modifier = Modifier
                                            .heightIn(min = 36.dp)
                                            .clickable { durationMinutesStr = mins.toString() }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = "${mins}m",
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else colors.ink,
                                                softWrap = true
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = colors.border.copy(alpha = 0.6f), thickness = 0.6.dp)

                            // Multi-Select (MSQ) Scoring Policy Picker
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Multi-Select (MSQ) Grading Rule",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    softWrap = true
                                )
                                Text(
                                    text = "Applies only to questions with multiple correct choices. Single-choice MCQs always use full marks or negative penalty.",
                                    fontSize = 10.sp,
                                    color = colors.muted,
                                    lineHeight = 14.sp,
                                    softWrap = true
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    MsqScoringPolicy.entries.forEach { policy ->
                                        val isPolicySelected = msqScoringPolicy == policy
                                        val subtitle = if (policy == MsqScoringPolicy.ALL_OR_NOTHING) {
                                            "Exact option set required"
                                        } else {
                                            "Proportional if 0 wrong choices"
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isPolicySelected) colors.tealLight else colors.surface,
                                            border = BorderStroke(1.dp, if (isPolicySelected) colors.teal else colors.border),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("edit_msq_policy_${policy.code.lowercase()}")
                                                .clickable { msqScoringPolicy = policy }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = policy.label,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isPolicySelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (isPolicySelected) colors.tealDark else colors.inkBold,
                                                    softWrap = true
                                                )
                                                Text(
                                                    text = subtitle,
                                                    fontSize = 10.sp,
                                                    color = if (isPolicySelected) colors.tealDark.copy(alpha = 0.85f) else colors.muted,
                                                    softWrap = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (onEnterQuestionEditMode != null) {
                        OutlinedButton(
                            onClick = { persistEditsAndClose(enterQuestionEditor = true) },
                            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Questions, Keys & Explanations", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, softWrap = true)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
