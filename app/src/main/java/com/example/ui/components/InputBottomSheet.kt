package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween 
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import com.example.data.PaperEntity
import com.example.model.QuestionPaper
import com.example.ui.components.QuestionCard
import com.example.util.CameraCaptureHelper
import com.example.util.FeatureToggles
import com.example.util.HandwrittenNotesExtractor
import com.example.util.McqParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ui.theme.ExamTheme

fun computeNextSetForSubject(subject: String, papers: List<PaperEntity>): String {
    val cleanSubject = subject.trim()
    if (cleanSubject.isBlank()) return "01"

    val subjectPapers = papers.filter { it.subject.trim().equals(cleanSubject, ignoreCase = true) }
    if (subjectPapers.isEmpty()) return "01"

    val usedNumbers = subjectPapers.mapNotNull { p ->
        val trimmed = p.setNumber.trim()
        val numMatch = Regex("""\b(?:Set|Series)?\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(trimmed)
        if (numMatch != null) {
            numMatch.groupValues[1].toIntOrNull()
        } else {
            val letterMatch = Regex("""\b(?:Set|Series)?\s*([A-Z])\b""", RegexOption.IGNORE_CASE).find(trimmed)
            val char = letterMatch?.groupValues?.get(1)?.uppercase()?.firstOrNull()
            if (char != null && char in 'A'..'Z') {
                char - 'A' + 1
            } else null
        }
    }.toSet()

    var nextNum = 1
    while (nextNum in usedNumbers) {
        nextNum++
    }
    return String.format(java.util.Locale.US, "%02d", nextNum)
}

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun InputBottomSheet(
    initialText: String,
    savedPapers: List<PaperEntity> = emptyList(),
    onDismiss: () -> Unit,
    onGenerate: (text: String, title: String, subject: String, topic: String, setNumber: String) -> Unit,
    onPickFile: () -> Unit,
    onLoadSample: (Int) -> Unit,
    sheetState: SheetState,
    onOpenPromptBuilder: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = ExamTheme.colors
    val haptic = LocalHapticFeedback.current
    var textInput by remember(initialText) { mutableStateOf(initialText) }

    // Debounced background preview parsing metrics to prevent UI freezes on large pastes
    var parsedPreview by remember { mutableStateOf<QuestionPaper?>(null) }
    var isParsingPreview by remember { mutableStateOf(false) }
    var isTextAreaExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(textInput) {
        if (textInput.isBlank()) {
            parsedPreview = null
            isParsingPreview = false
        } else {
            isParsingPreview = true
            delay(280L) // Debounce so typing or pasting massive text is smooth & non-blocking
            val result = withContext(Dispatchers.Default) {
                McqParser.parse(textInput)
            }
            parsedPreview = result
            isParsingPreview = false
        }
    }

    // Categorization & Set No fields
    var customTitle by remember { mutableStateOf("") }
    var customSubject by remember { mutableStateOf("") }
    var customTopic by remember { mutableStateOf("") }
    var customSetNumber by remember { mutableStateOf("01") }
    var showCategorizationSection by remember { mutableStateOf(true) }
    var isSubjectDropdownOpen by remember { mutableStateOf(false) }
    var activeSheetTab by remember { mutableStateOf(0) } // 0: Input/Paste, 1: Prompt Generator

    // AI Prompt Generator Form State
    var promptMcqCount by remember { mutableStateOf("20") }
    var promptExam by remember { mutableStateOf("") }
    var promptSubject by remember { mutableStateOf("") }
    var promptTopic by remember { mutableStateOf("") }
    var promptQuestionLanguage by remember { mutableStateOf("English") }
    var promptExplanationChoice by remember { mutableStateOf("Same as Question") }
    var promptCustomExplanationLanguage by remember { mutableStateOf("") }
    var promptSyllabus by remember { mutableStateOf("based on new syllabus") }
    var promptDifficulty by remember { mutableStateOf("Moderate") }
    var promptQuestionType by remember { mutableStateOf("Mixed Formats") }
    var promptExplainAllFourOptions by remember { mutableStateOf(true) }
    var promptIncludeFormulasAndDiagrams by remember { mutableStateOf(true) }
    // Format & Quantity Correction Rule (Disabled by default: Unrestricted Full Stream)
    var promptEnableQuantityCorrectionRule by remember { mutableStateOf(false) }

    val effectivePromptExplanationLanguage by remember(promptQuestionLanguage, promptExplanationChoice, promptCustomExplanationLanguage) {
        derivedStateOf {
            val q = promptQuestionLanguage.trim().ifBlank { "English" }
            when (promptExplanationChoice) {
                "Same as Question" -> "$q (same language as the questions)"
                "Bilingual" -> {
                    if (q.equals("English", ignoreCase = true)) {
                        "Bilingual (English & Hindi)"
                    } else {
                        "Bilingual (English & $q)"
                    }
                }
                "English" -> "English"
                "Hindi" -> "Hindi"
                "Custom" -> promptCustomExplanationLanguage.trim().ifBlank { q }
                else -> promptExplanationChoice
            }
        }
    }

    val existingSubjectFolders = remember(savedPapers) {
        savedPapers.map { it.subject.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    // Auto-fill suggested metadata when text changes if fields are currently empty
    LaunchedEffect(parsedPreview) {
        parsedPreview?.let { preview ->
            if (customTitle.isBlank() && preview.metadata.title != "MCQ Practice Set") {
                customTitle = preview.metadata.title
            }
            if (customSubject.isBlank() && preview.metadata.subject != "General Studies") {
                customSubject = preview.metadata.subject
                customSetNumber = computeNextSetForSubject(preview.metadata.subject, savedPapers)
            }
            if (customTopic.isBlank() && preview.metadata.topic.isNotBlank()) {
                customTopic = preview.metadata.topic
            }
            if ((customSetNumber == "01" || customSetNumber == "Set 1" || customSetNumber == "Set A") && preview.metadata.setNumber.isNotBlank()) {
                val digits = preview.metadata.setNumber.filter { it.isDigit() }
                customSetNumber = if (digits.isNotBlank() && digits.length <= 4) {
                    val n = digits.toIntOrNull() ?: 1
                    String.format(java.util.Locale.US, "%02d", n)
                } else {
                    preview.metadata.setNumber
                }
            }
        }
    }

    val focusManager = LocalFocusManager.current
    val columnScrollState = rememberScrollState()
    // Measured height of the visible scroll area (screen minus keyboard, nav bar and header)
    var scrollViewportPx by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // Floating scroll indicator for text box: visible only while scrolling, auto-hides after a few moments
    var isTextBoxScrolling by remember { mutableStateOf(false) }
    var textBoxScrollHideJob by remember { mutableStateOf<Job?>(null) }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isOcrScanning by remember { mutableStateOf(false) }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { isSaved ->
        if (isSaved && pendingCameraUri != null) {
            val uriToProcess = pendingCameraUri!!
            isOcrScanning = true
            coroutineScope.launch {
                val result = HandwrittenNotesExtractor.extractFromImageUri(context, uriToProcess)
                isOcrScanning = false
                if (result.isSuccess && result.normalizedText.isNotBlank()) {
                    textInput = if (textInput.isBlank()) result.normalizedText else "$textInput\n\n${result.normalizedText}"
                    Toast.makeText(
                        context,
                        "Recognized ${result.parseResult?.paper?.questions?.size ?: 0} questions from handwritten notes!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        result.errorMessage ?: "Could not recognize handwritten questions. Try clearer lighting.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Intercept residual downward drag/fling so ModalBottomSheet NEVER interprets content scrolling as drag-to-dismiss
    val blockSheetDismissNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // If scrolling down while already at the top, consume it immediately so sheet drag is never triggered
                return if (available.y > 0f && columnScrollState.value == 0) {
                    Offset(0f, available.y)
                } else {
                    Offset.Zero
                }
            }
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // If scrolling down (available.y > 0), consume it so ModalBottomSheet never treats downward drag as dismiss!
                return if (available.y > 0f) {
                    Offset(0f, available.y)
                } else {
                    Offset.Zero
                }
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (available.y > 0f && columnScrollState.value == 0) {
                    Velocity(0f, available.y)
                } else {
                    Velocity.Zero
                }
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return if (available.y > 0f) {
                    Velocity(0f, available.y)
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    // Dedicated nested scroll connection on the text field:
    // Scrolling inside the text area scrolls the text smoothly. When the text reaches its
    // top or bottom edge, extra drag chains to the parent page so the user can easily scroll
    // down to the Generate button even if swiping over the text area.
    val textFieldNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (kotlin.math.abs(available.y) > 0.5f) {
                    isTextBoxScrolling = true
                    textBoxScrollHideJob?.cancel()
                    textBoxScrollHideJob = coroutineScope.launch {
                        delay(1500L)
                        isTextBoxScrolling = false
                    }
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (kotlin.math.abs(consumed.y) > 0.5f || kotlin.math.abs(available.y) > 0.5f) {
                    isTextBoxScrolling = true
                    textBoxScrollHideJob?.cancel()
                    textBoxScrollHideJob = coroutineScope.launch {
                        delay(1500L)
                        isTextBoxScrolling = false
                    }
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isTextBoxScrolling = true
                textBoxScrollHideJob?.cancel()
                textBoxScrollHideJob = coroutineScope.launch {
                    delay(1500L)
                    isTextBoxScrolling = false
                }
                return available
            }
        }
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
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !colors.isDark
                insetsController.isAppearanceLightNavigationBars = !colors.isDark
            }
            onDispose {}
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                ) {
                    // Top App Bar / Sheet Header
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Create Question Set",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Text(
                                    text = "Paste text, import file (.txt/.md), or load sample",
                                    fontSize = 12.sp,
                                    color = colors.muted
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Paste from clipboard button
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                        if (clipText.isNotBlank()) {
                                            textInput = clipText
                                        }
                                    },
                                    modifier = Modifier.testTag("paste_clipboard_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste from clipboard",
                                        tint = colors.teal
                                    )
                                }

                                // Close sheet button
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.testTag("close_sheet_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = colors.muted
                                    )
                                }
                            }
                        }
                    }

                    // Scrollable Content Area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                            .onSizeChanged { scrollViewportPx = it.height }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .verticalScroll(columnScrollState)
                    ) {

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Bar: "Paste / Input MCQs" and "AI Prompt Generator"
            TabRow(
                selectedTabIndex = activeSheetTab,
                containerColor = colors.surfaceVariant,
                contentColor = colors.inkBold,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier
                            .tabIndicatorOffset(tabPositions[activeSheetTab])
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                        color = colors.teal,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = activeSheetTab == 0,
                    onClick = { activeSheetTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = if (activeSheetTab == 0) colors.teal else colors.muted
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Paste / Input",
                                fontSize = 13.sp,
                                fontWeight = if (activeSheetTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeSheetTab == 0) colors.inkBold else colors.muted
                            )
                        }
                    }
                )
                Tab(
                    selected = activeSheetTab == 1,
                    onClick = { activeSheetTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (activeSheetTab == 1) colors.teal else colors.muted
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "AI Prompt Generator",
                                fontSize = 13.sp,
                                fontWeight = if (activeSheetTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeSheetTab == 1) colors.inkBold else colors.muted
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (activeSheetTab == 0) {
                // Action Pills Row: Add File & Load Multilingual Samples
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onPickFile,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.testTag("pick_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add File", fontSize = 12.sp, color = colors.inkBold)
                    }

                    if (FeatureToggles.ENABLE_CAMERA_NOTES_SCANNER) {
                        OutlinedButton(
                            onClick = {
                                val tempUri = CameraCaptureHelper.createTempPictureUri(context)
                                if (tempUri != null) {
                                    pendingCameraUri = tempUri
                                    takePhotoLauncher.launch(tempUri)
                                } else {
                                    Toast.makeText(context, "Unable to access camera storage.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("scan_notes_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = colors.teal
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan Notes", fontSize = 12.sp, color = colors.inkBold)
                        }
                    }

                    OutlinedButton(
                        onClick = { onLoadSample(0); onDismiss() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                        border = BorderStroke(1.dp, colors.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("sample_science_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Science", fontSize = 12.sp, color = colors.inkBold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { onLoadSample(1); onDismiss() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                        border = BorderStroke(1.dp, colors.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("sample_hindi_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hindi", fontSize = 12.sp, color = colors.inkBold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { onLoadSample(2); onDismiss() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                        border = BorderStroke(1.dp, colors.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("sample_korean_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Korean", fontSize = 12.sp, color = colors.inkBold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { onLoadSample(3); onDismiss() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                        border = BorderStroke(1.dp, colors.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("sample_bio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Genetics", fontSize = 12.sp, color = colors.inkBold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { onLoadSample(4); onDismiss() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                        border = BorderStroke(1.dp, colors.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("sample_patterns_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Assignment,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Multi-Pattern", fontSize = 12.sp, color = colors.inkBold, maxLines = 1)
                    }
                }

                if (isOcrScanning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.tealLight,
                        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.teal
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Scanning notes & formatting...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.tealDark,
                                maxLines = 1
                            )
                        }
                    }
                }

            Spacer(modifier = Modifier.height(12.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.teal,
                unfocusedBorderColor = colors.border,
                focusedTextColor = colors.inkBold,
                unfocusedTextColor = colors.ink,
                focusedLabelColor = colors.tealDark,
                unfocusedLabelColor = colors.muted
            )

            // Categorization & Set No Section (Collapsible)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceVariant,
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCategorizationSection = !showCategorizationSection },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Subject, Topic & Set Number",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                        }
                        Icon(
                            imageVector = if (showCategorizationSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = colors.muted
                        )
                    }

                    AnimatedVisibility(visible = showCategorizationSection) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            // Title & Set No in one row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customTitle,
                                    onValueChange = { customTitle = it },
                                    label = { Text("Title", fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier
                                        .weight(2f)
                                        .heightIn(min = 56.dp)
                                        .testTag("import_title_field"),
                                    shape = RoundedCornerShape(6.dp)
                                )

                                OutlinedTextField(
                                    value = customSetNumber,
                                    onValueChange = { customSetNumber = it },
                                    label = { Text("Set", fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 56.dp)
                                        .testTag("import_set_number_field"),
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Subject & Topic in one row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = customSubject,
                                        onValueChange = { newSub ->
                                            customSubject = newSub
                                            if (existingSubjectFolders.any { it.equals(newSub.trim(), ignoreCase = true) }) {
                                                customSetNumber = computeNextSetForSubject(newSub, savedPapers)
                                            }
                                        },
                                        label = { Text("Subject", fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        singleLine = true,
                                        colors = fieldColors,
                                        trailingIcon = {
                                            if (existingSubjectFolders.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { isSubjectDropdownOpen = !isSubjectDropdownOpen },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = "Subject Folders",
                                                        tint = colors.muted
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .testTag("import_subject_field"),
                                        shape = RoundedCornerShape(6.dp)
                                    )

                                    DropdownMenu(
                                        expanded = isSubjectDropdownOpen,
                                        onDismissRequest = { isSubjectDropdownOpen = false }
                                    ) {
                                        val filteredFolders = if (customSubject.isBlank()) {
                                            existingSubjectFolders
                                        } else {
                                            existingSubjectFolders.filter {
                                                it.contains(customSubject, ignoreCase = true)
                                            }.ifEmpty { existingSubjectFolders }
                                        }

                                        filteredFolders.forEach { folder ->
                                            val count = savedPapers.count { it.subject.trim().equals(folder, ignoreCase = true) }
                                            DropdownMenuItem(
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = null,
                                                        tint = colors.teal,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                text = {
                                                    Text(folder, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                },
                                                trailingIcon = {
                                                    Text("$count sets", fontSize = 11.sp, color = colors.muted)
                                                },
                                                onClick = {
                                                    customSubject = folder
                                                    customSetNumber = computeNextSetForSubject(folder, savedPapers)
                                                    isSubjectDropdownOpen = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = customTopic,
                                    onValueChange = { customTopic = it },
                                    label = { Text("Topic", fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 56.dp)
                                        .testTag("import_topic_field"),
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }

                            if (customSubject.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val isExistingFolder = existingSubjectFolders.any { it.equals(customSubject.trim(), ignoreCase = true) }
                                val existingCount = savedPapers.count { it.subject.trim().equals(customSubject.trim(), ignoreCase = true) }
                                
                                val isSetDuplicate = savedPapers.any {
                                    it.subject.trim().equals(customSubject.trim(), ignoreCase = true) &&
                                    it.setNumber.trim().equals(customSetNumber.trim(), ignoreCase = true)
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSetDuplicate) colors.red.copy(alpha = 0.1f) else colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, if (isSetDuplicate) colors.red else colors.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSetDuplicate) Icons.Default.Warning else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (isSetDuplicate) colors.red else colors.teal,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isSetDuplicate) {
                                                "Warning: $customSetNumber already exists in $customSubject. Please change the set number."
                                            } else if (isExistingFolder) {
                                                "Subject Folder: $customSubject ($existingCount sets) • Auto-assigned: $customSetNumber"
                                            } else {
                                                "New Subject Folder: $customSubject • Starting: $customSetNumber"
                                            },
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isSetDuplicate) colors.red else colors.inkBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Text Area Input Header with Stable Height Status Pill (Prevents Screen Jumps)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MCQ Practice Content",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (textInput.isNotBlank()) colors.tealLight else colors.surfaceVariant,
                    border = BorderStroke(0.8.dp, if (textInput.isNotBlank()) colors.teal.copy(alpha = 0.4f) else colors.border)
                ) {
                    Text(
                        text = if (textInput.isNotBlank()) {
                            "${textInput.lines().size} lines • ${textInput.length} chars"
                        } else {
                            "0 lines • Ready"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (textInput.isNotBlank()) colors.tealDark else colors.muted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dedicated Responsive Quick Action Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Paste Button (Replaces content)
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (clipText.isNotBlank()) {
                            textInput = clipText
                            Toast.makeText(context, "Pasted text from clipboard", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                bringIntoViewRequester.bringIntoView()
                            }
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.tealDark),
                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("toolbar_paste_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste from clipboard",
                        modifier = Modifier.size(15.dp),
                        tint = colors.teal
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }

                // Append Button (Appends clipboard content without replacing existing text)
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (clipText.isNotBlank()) {
                            textInput = if (textInput.isBlank()) {
                                clipText
                            } else {
                                "${textInput.trimEnd()}\n\n$clipText"
                            }
                            Toast.makeText(context, "Appended text from clipboard", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                bringIntoViewRequester.bringIntoView()
                            }
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.tealDark),
                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("toolbar_append_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Append from clipboard",
                        modifier = Modifier.size(15.dp),
                        tint = colors.teal
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Append", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }

                if (textInput.isNotBlank()) {
                    // Clear Button
                    OutlinedButton(
                        onClick = { textInput = "" },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.muted),
                        border = BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("toolbar_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear text",
                            modifier = Modifier.size(14.dp),
                            tint = colors.muted
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Top Button
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                columnScrollState.animateScrollTo(0)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                        border = BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("toolbar_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Scroll to top",
                            modifier = Modifier.size(15.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Top", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))

                // Full View / Shrink Button with accessible touch target
                OutlinedButton(
                    onClick = { isTextAreaExpanded = !isTextAreaExpanded },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("toolbar_expand_button")
                ) {
                    Icon(
                        imageVector = if (isTextAreaExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                        contentDescription = if (isTextAreaExpanded) "Collapse text area" else "Expand text area",
                        tint = colors.tealDark,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isTextAreaExpanded) "Shrink" else "Full View",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Height is constrained to the actual available viewport above the on-screen keyboard
            val fieldDensity = LocalDensity.current
            val imeOpen = WindowInsets.ime.getBottom(fieldDensity) > 0
            val viewportDp = with(fieldDensity) { scrollViewportPx.toDp() }
            val normalFieldHeight = if (isTextAreaExpanded) 380.dp else 200.dp
            val maxAvailableFieldHeight = if (viewportDp > 0.dp) {
                // Ensure room remains for header, toolbar, and bottom controls
                (viewportDp - 70.dp).coerceAtLeast(80.dp)
            } else {
                normalFieldHeight
            }
            val targetFieldHeight = if (imeOpen) {
                val ratio = if (isTextAreaExpanded) 0.50f else 0.42f
                (viewportDp * ratio).coerceIn(80.dp, maxAvailableFieldHeight)
            } else {
                normalFieldHeight
            }
            val fieldHeight = if (scrollViewportPx == 0) {
                normalFieldHeight
            } else {
                minOf(normalFieldHeight, targetFieldHeight, maxAvailableFieldHeight)
            }
            var mcqFocused by remember { mutableStateOf(false) }

            // Reliable BringIntoView triggered without arbitrary delays when field gains focus or keyboard/viewport changes
            LaunchedEffect(imeOpen, scrollViewportPx, mcqFocused) {
                if (mcqFocused && scrollViewportPx > 0) {
                    withFrameNanos { }
                    bringIntoViewRequester.bringIntoView()
                }
            }

            // Dynamic Scrollable Text Area Input with Isolated Nested Scrolling, IME bring-into-view and Floating Scroll Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fieldHeight)
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text(if (isTextAreaExpanded) "MCQ Practice Text (Expanded View)" else "MCQ Practice Text") },
                    placeholder = {
                        Text(
                            "1. Question text here...\n(A) Option 1\n(B) Option 2\n(C) Option 3\n(D) Option 4\n\nAnswer Key\n1: A\n\nDetailed Explanations\n1. (A) Reason...",
                            color = colors.muted.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    },
                    colors = fieldColors,
                    minLines = 1,
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(textFieldNestedScroll)
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusEvent { focusState ->
                            mcqFocused = focusState.isFocused
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    withFrameNanos { }
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        }
                        .testTag("mcq_input_field"),
                    shape = RoundedCornerShape(10.dp)
                )

                // Floating scrollbar for text box: visible only while scrolling, auto-hides after a few moments
                val scrollAlpha by animateFloatAsState(
                    targetValue = if (isTextBoxScrolling) 1f else 0f,
                    animationSpec = tween(durationMillis = if (isTextBoxScrolling) 150 else 500),
                    label = "textBoxScrollbarAlpha"
                )
                if (scrollAlpha > 0.01f) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.teal.copy(alpha = 0.85f * scrollAlpha),
                        shadowElevation = (3 * scrollAlpha).dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp, top = 12.dp, bottom = 12.dp)
                            .width(5.dp)
                            .fillMaxHeight(0.55f)
                    ) {}
                }
            }

            // Live Parser Metrics Pill / Progress Indicator
            if (isParsingPreview) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colors.teal
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Analyzing question paper format...",
                            fontSize = 11.5.sp,
                            color = colors.muted
                        )
                    }
                }
            } else {
                parsedPreview?.let { preview ->
                    var isPreviewExpanded by remember { mutableStateOf(false) }
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.tealLight,
                        border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPreviewExpanded = !isPreviewExpanded }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Question Count Pill
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.navyBanner.copy(alpha = 0.12f),
                                    border = BorderStroke(0.8.dp, colors.navyBanner.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Assignment,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = colors.navyBanner
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Detected: ${preview.questions.size} Questions",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold
                                        )
                                    }
                                }

                                // Quick Answers Pill
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.tealLight,
                                    border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = colors.tealDark
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${preview.quickAnswersCount} Answers",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.tealDark
                                        )
                                    }
                                }

                                // Explanations Pill
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.greenLight,
                                    border = BorderStroke(0.8.dp, colors.green.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = colors.green
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${preview.explanationsCount} Explanations",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.green
                                        )
                                    }
                                }

                                // Toggle Preview Pill
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.surface,
                                    border = BorderStroke(0.8.dp, colors.border)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isPreviewExpanded) "Hide Details" else "Preview",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.tealDark
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = if (isPreviewExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Toggle Preview",
                                            tint = colors.tealDark,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (isPreviewExpanded) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    preview.questions.forEach { q ->
                                        QuestionCard(
                                            question = q,
                                            isCardBoxed = true,
                                            showAnswerIndicator = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scrollable Action Row for Tab 0: Cancel, Clear, and Generate & Save
            val isSetDuplicate = savedPapers.any {
                it.subject.trim().equals(customSubject.trim(), ignoreCase = true) &&
                it.setNumber.trim().equals(customSetNumber.trim(), ignoreCase = true)
            }
            val canGenerate = textInput.isNotBlank() && !isSetDuplicate

            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.8.dp, colors.border),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                        border = BorderStroke(1.dp, colors.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(0.7f)
                            .heightIn(min = 44.dp)
                            .testTag("cancel_sheet_button")
                    ) {
                        Text("Cancel", fontSize = 12.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { textInput = "" },
                        enabled = textInput.isNotBlank(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                        border = BorderStroke(1.dp, colors.border),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(0.7f)
                            .heightIn(min = 44.dp)
                            .testTag("clear_text_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Clear", fontSize = 12.sp, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            if (canGenerate) {
                                onGenerate(
                                    textInput,
                                    customTitle,
                                    customSubject,
                                    customTopic,
                                    customSetNumber
                                )
                                onDismiss()
                            }
                        },
                        enabled = canGenerate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.navyBanner,
                            contentColor = colors.navyBannerText,
                            disabledContainerColor = colors.surfaceVariant,
                            disabledContentColor = colors.muted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1.6f)
                            .heightIn(min = 44.dp)
                            .testTag("generate_paper_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isSetDuplicate) {
                                "Duplicate Set"
                            } else if (parsedPreview != null && parsedPreview!!.questions.isNotEmpty()) {
                                "Generate (${parsedPreview!!.questions.size})"
                            } else {
                                "Generate & Save"
                            },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // While the keyboard is open, add scroll room below so the field can actually be lifted above it
            Spacer(modifier = Modifier.height(if (imeOpen) maxOf(viewportDp * 0.8f, 260.dp) else 80.dp))
        } else {
            // AI Prompt Generator Tab Content
            val parsedCount = promptMcqCount.trim().toIntOrNull() ?: 20
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // Explanatory Banner Card with Fullscreen Link
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.tealLight.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.teal.copy(alpha = 0.15f),
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
                                    Text(
                                        text = "AI MCQ Prompt Builder",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colors.inkBold
                                    )
                                    Text(
                                        text = "Zero-drift prompts for Gemini, Claude, and ChatGPT",
                                        fontSize = 11.5.sp,
                                        color = colors.muted
                                    )
                                }
                            }

                            if (onOpenPromptBuilder != null) {
                                Surface(
                                    modifier = Modifier.clickable { onOpenPromptBuilder() },
                                    shape = RoundedCornerShape(20.dp),
                                    color = colors.teal,
                                    contentColor = androidx.compose.ui.graphics.Color.White
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Full Screen", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 1: EXAMINATION & TOPIC PARAMETERS CARD (Matching Tools Tab styling)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(0.8.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "EXAMINATION PARAMETERS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.muted,
                            letterSpacing = 0.5.sp
                        )

                        // Extended Exam Preset Catalog (Synchronized with Tools Tab)
                        val examPresets = listOf(
                            "UPSC Civil Services",
                            "State PCS",
                            "SSC CGL / Banking",
                            "Railways / RRB",
                            "NEET / Biology",
                            "JEE / Physics",
                            "GATE / Engineering",
                            "USMLE / Clinical",
                            "BAR / Law Exam",
                            "SAT / ACT",
                            "School Board"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            examPresets.forEach { preset ->
                                val isSelected = promptExam.equals(preset, ignoreCase = true) || (promptExam.isNotBlank() && promptExam.startsWith(preset.take(6)))
                                Surface(
                                    modifier = Modifier.clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        promptExam = preset
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) colors.teal else colors.surfaceVariant,
                                    border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) colors.tealLight else colors.border)
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) androidx.compose.ui.graphics.Color.White else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = promptExam,
                            onValueChange = { promptExam = it },
                            label = { Text("Exam", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            placeholder = { Text("UPSC", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth().testTag("prompt_field_exam")
                        )

                        // Quick-Pick Subject Suggestions
                        val quickSubjects = listOf(
                            "General Science",
                            "Modern Indian History",
                            "Polity & Governance",
                            "Geography & Climate",
                            "Quantitative Aptitude",
                            "Clinical Medicine",
                            "Physics & Mechanics",
                            "English Comprehension"
                        )
                        Text(
                            text = "Quick Subject Ideas",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.muted
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickSubjects.forEach { quickSub ->
                                val isSubSelected = promptSubject.equals(quickSub, ignoreCase = true)
                                Surface(
                                    modifier = Modifier.clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        promptSubject = quickSub
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSubSelected) colors.teal else colors.surfaceVariant.copy(alpha = 0.6f),
                                    border = BorderStroke(if (isSubSelected) 1.5.dp else 0.5.dp, if (isSubSelected) colors.tealLight else colors.border)
                                ) {
                                    Text(
                                        text = quickSub,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSubSelected) androidx.compose.ui.graphics.Color.White else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = promptSubject,
                            onValueChange = { promptSubject = it },
                            label = { Text("Subject", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            placeholder = { Text("Physics", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth().testTag("prompt_field_subject")
                        )

                        OutlinedTextField(
                            value = promptTopic,
                            onValueChange = { promptTopic = it },
                            label = { Text("Topic", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            placeholder = { Text("Optics", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth().testTag("prompt_field_topic")
                        )

                        // Question Count Selector & Chips
                        val parsedCount = promptMcqCount.trim().toIntOrNull() ?: 20
                        val presetCounts = listOf(5, 10, 25, 50, 100, 180)
                        val isPreset = parsedCount in presetCounts

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Question Count", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = if (isPreset) "Preset" else "Custom",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isPreset) colors.teal else colors.muted
                                    )
                                    Text("$parsedCount MCQs", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Preset Count Chips (5, 10, 25, 50, 100, 180) - synchronized with Tools Tab
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presetCounts.forEach { cnt ->
                                    val isSelected = parsedCount == cnt
                                    Surface(
                                        onClick = { promptMcqCount = cnt.toString() },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) colors.teal else colors.surfaceVariant,
                                        border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                                    ) {
                                        Text(
                                            text = "$cnt Qs",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) androidx.compose.ui.graphics.Color.White else colors.inkBold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Slider strictly aligned to multiples of 5 from 5 to 200 with 38 steps
                            Slider(
                                value = parsedCount.toFloat().coerceIn(5f, 200f),
                                onValueChange = { newVal ->
                                    val snapped = kotlin.math.round(newVal / 5f) * 5f
                                    val clamped = snapped.toInt().coerceIn(5, 200)
                                    promptMcqCount = clamped.toString()
                                },
                                valueRange = 5f..200f,
                                steps = 38,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Syllabus Specification
                        OutlinedTextField(
                            value = promptSyllabus,
                            onValueChange = { promptSyllabus = it },
                            label = { Text("Syllabus", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            placeholder = { Text("NCERT", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.fillMaxWidth().testTag("prompt_field_syllabus"),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                if (promptSyllabus.isNotEmpty()) {
                                    IconButton(onClick = { promptSyllabus = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SECTION 2: LANGUAGE & EXPLANATION SETTINGS (Matching Tools Tab)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(0.8.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "LANGUAGE & EXPLANATIONS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.muted,
                            letterSpacing = 0.5.sp
                        )

                        // Question Language
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Question Language",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Text(
                                    "Any language supported",
                                    fontSize = 10.sp,
                                    color = colors.teal,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                "Universal (A)-(D) option labels are preserved across all languages",
                                fontSize = 10.sp,
                                color = colors.muted
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            OutlinedTextField(
                                value = promptQuestionLanguage,
                                onValueChange = { promptQuestionLanguage = it },
                                placeholder = { Text("English", fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Quick preset language chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "English", "Hindi", "Bengali", "Marathi", "Telugu",
                                    "Tamil", "Gujarati", "Urdu", "Kannada", "Malayalam",
                                    "Odia", "Punjabi", "Sanskrit", "Spanish", "French", "German"
                                ).forEach { lang ->
                                    val isSelected = promptQuestionLanguage.trim().equals(lang, ignoreCase = true)
                                    Surface(
                                        onClick = { promptQuestionLanguage = lang },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) colors.teal else colors.surfaceVariant,
                                        border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                                    ) {
                                        Text(
                                            text = lang,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) androidx.compose.ui.graphics.Color.White else colors.inkBold,
                                            modifier = Modifier.padding(horizontal = 7.5.dp, vertical = 3.5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Explanation Language
                        Column {
                            val activeQLang = promptQuestionLanguage.trim().ifBlank { "English" }
                            val bilingualPresetName = if (activeQLang.equals("English", ignoreCase = true)) {
                                "Bilingual (Eng & Hin)"
                            } else {
                                "Bilingual (Eng & $activeQLang)"
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Explanation Language",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Text(
                                    "Current: $effectivePromptExplanationLanguage",
                                    fontSize = 10.sp,
                                    color = colors.muted,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val explanationOptions = listOf(
                                    "Same as Question" to "Same as Question",
                                    "English" to "English",
                                    "Hindi" to "Hindi",
                                    "Bilingual" to bilingualPresetName,
                                    "Custom" to "Custom..."
                                )

                                explanationOptions.forEach { (key, displayLabel) ->
                                    val isSelected = promptExplanationChoice == key
                                    Surface(
                                        onClick = { promptExplanationChoice = key },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) colors.teal else colors.surfaceVariant,
                                        border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                                    ) {
                                        Text(
                                            text = displayLabel,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) androidx.compose.ui.graphics.Color.White else colors.inkBold,
                                            modifier = Modifier.padding(horizontal = 7.5.dp, vertical = 3.5.dp)
                                        )
                                    }
                                }
                            }

                            if (promptExplanationChoice == "Custom") {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = promptCustomExplanationLanguage,
                                    onValueChange = { promptCustomExplanationLanguage = it },
                                    placeholder = { Text("Language", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 1. Difficulty Level Selector
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Difficulty Level", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                                Text(promptDifficulty, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Easy", "Moderate", "Hard", "Extreme", "Mixed").forEach { diff ->
                                    val label = when(diff) {
                                        "Easy" -> "Easy"
                                        "Moderate" -> "Moderate"
                                        "Hard" -> "Hard"
                                        "Extreme" -> "Extreme (Olympiad)"
                                        else -> "Mixed Difficulty"
                                    }
                                    val isSelected = promptDifficulty == diff
                                    Surface(
                                        modifier = Modifier.clickable { promptDifficulty = diff },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) colors.teal else colors.surfaceVariant,
                                        border = BorderStroke(0.5.dp, colors.border)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) androidx.compose.ui.graphics.Color.White else colors.inkBold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Question Types & Formats Selector
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Question Format & Pattern", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                                Text(promptQuestionType, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "Mixed Formats",
                                    "Conceptual & Theory",
                                    "Numerical & Calculation",
                                    "Assertion-Reason & Statement-Based",
                                    "Case Study & Passage-Based"
                                ).forEach { qType ->
                                    val isSelected = promptQuestionType == qType
                                    Surface(
                                        modifier = Modifier.clickable { promptQuestionType = qType },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) colors.teal else colors.surfaceVariant,
                                        border = BorderStroke(0.5.dp, colors.border)
                                    ) {
                                        Text(
                                            text = qType,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) androidx.compose.ui.graphics.Color.White else colors.inkBold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Option Explanations Checkbox
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = promptExplainAllFourOptions,
                                onCheckedChange = { promptExplainAllFourOptions = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "Explain All 4 Options Individually",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkBold
                                )
                                Text(
                                    text = "Explains why correct option is right and why each distractor is wrong",
                                    fontSize = 10.5.sp,
                                    color = colors.muted
                                )
                            }
                        }

                        // Formulas & Diagrams Checkbox
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = promptIncludeFormulasAndDiagrams,
                                onCheckedChange = { promptIncludeFormulasAndDiagrams = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "Include Formulas & Diagram References",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkBold
                                )
                                Text(
                                    text = "Formats equations and figures so the app renders them visually",
                                    fontSize = 10.5.sp,
                                    color = colors.muted
                                )
                            }
                        }

                        // Format & Quantity Truncation Guard
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (promptEnableQuantityCorrectionRule) androidx.compose.ui.graphics.Color(0xFFEFF6FF) else colors.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(0.8.dp, if (promptEnableQuantityCorrectionRule) androidx.compose.ui.graphics.Color(0xFF93C5FD) else colors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { promptEnableQuantityCorrectionRule = !promptEnableQuantityCorrectionRule }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = if (promptEnableQuantityCorrectionRule) colors.teal else colors.muted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Format & Quantity Truncation Guard",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (promptEnableQuantityCorrectionRule) colors.teal else colors.inkBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (promptEnableQuantityCorrectionRule) {
                                            "ACTIVE: Enforces a strict 2–3 sentence explanation budget to prevent token cutoffs for 20–25+ question sets."
                                        } else {
                                            "DISABLED (Default): Unrestricted full stream. Generates all $parsedCount questions continuously without artificial limits."
                                        },
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp,
                                        color = if (promptEnableQuantityCorrectionRule) colors.tealDark else colors.muted
                                    )
                                }

                                Switch(
                                    checked = promptEnableQuantityCorrectionRule,
                                    onCheckedChange = { promptEnableQuantityCorrectionRule = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                                        checkedTrackColor = colors.teal
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Generated Dynamic Prompt Construction (Senior Psychometrician Standard)
                val countVal = parsedCount
                val topicDisplay = buildString {
                    if (promptSubject.isNotBlank()) {
                        append(promptSubject.trim())
                        if (promptTopic.isNotBlank()) {
                            append(" - ")
                            append(promptTopic.trim())
                        }
                    } else if (promptTopic.isNotBlank()) {
                        append(promptTopic.trim())
                    } else {
                        append("Comprehensive Subject Curriculum")
                    }
                }
                val qLangVal = promptQuestionLanguage.trim().ifBlank { "English" }
                val expLangVal = effectivePromptExplanationLanguage
                val examVal = promptExam.trim().ifBlank { "Competitive Examination" }

                val fullPrompt = buildString {
                    append("Act as a senior psychometrician and chief examination paper setter for $examVal.\n\n")
                    append("Generate exactly $countVal multiple-choice questions (MCQs) for $topicDisplay at $promptDifficulty difficulty (Question Pattern: $promptQuestionType) in $qLangVal.\n\n")

                    append("### 1. LANGUAGE & SCRIPT CONVENTIONS\n")
                    if (expLangVal == qLangVal || promptExplanationChoice == "Same as Question") {
                        append("- Language: Write the question stems, options, and explanations entirely in $qLangVal using natural, academically rigorous terminology.\n")
                    } else {
                        append("- Language: Write question stems and options in $qLangVal, and explanations in $expLangVal, using natural, academically rigorous terminology.\n")
                    }
                    append("- Structural Markers: Regardless of the language used, keep question labels as \"Q1.\", \"Q2.\" and option keys strictly as \"(A)\", \"(B)\", \"(C)\", \"(D)\" for universal OMR and parser compatibility.\n\n")

                    append("### 2. PSYCHOMETRIC & DISTRACTOR RULES\n")
                    when (promptDifficulty) {
                        "Easy" -> append("1. COGNITIVE LEVEL (Easy / Foundational): Direct conceptual recall, core standard definitions, basic formulas, and straightforward identification with unambiguous distractors.\n")
                        "Moderate" -> append("1. COGNITIVE LEVEL (Moderate / Standard): Standard examination caliber requiring multi-step comprehension, application of principles, and analytical deduction.\n")
                        "Hard" -> append("1. COGNITIVE LEVEL (Hard / Advanced): Advanced analytical rigor, intricate edge cases, multi-layered calculations, and trap options diagnosing common misconceptions.\n")
                        "Extreme" -> append("1. COGNITIVE LEVEL (Extreme Olympiad / Rank-Decider): Deep synthesis, Olympiad-level multi-concept integration, subtle distractor nuances, and demanding problem-solving.\n")
                        else -> append("1. COGNITIVE LEVEL (Mixed Difficulty): Balanced distribution spanning ~25% Foundational, ~50% Standard/Moderate, and ~25% High-Rigor/Challenging questions.\n")
                    }
                    when (promptQuestionType) {
                        "Conceptual & Theory" -> append("2. QUESTION FORMAT FOCUS: Strictly conceptual & theoretical questions evaluating core laws, principles, and cause-effect reasoning.\n")
                        "Numerical & Calculation" -> append("2. QUESTION FORMAT FOCUS: Quantitative & numerical problem-solving with exact formulas, numerical options, and clear calculation steps.\n")
                        "Assertion-Reason & Statement-Based" -> append("2. QUESTION FORMAT FOCUS: Deep analytical items including 'Assertion (A) & Reason (R)', 'Statement I & Statement II', and 'Which of the statements given above is/are correct?'.\n")
                        "Case Study & Passage-Based" -> append("2. QUESTION FORMAT FOCUS: Practical context-driven scenarios, experimental setups, clinical/field case studies, or passage evaluation.\n")
                        else -> append("2. QUESTION FORMAT FOCUS: Dynamic mix of direct conceptual items, multi-statement questions, numerical application, and scenario analysis.\n")
                    }
                    append("3. DISTRACTOR HOMOGENEITY: All 4 options must be mutually exclusive and grammatically parallel (all identical parts of speech/sentence structure).\n")
                    append("4. LENGTH CONTROL: The correct option must NOT be the longest option by default. Maintain a tight word-count ratio across all 4 options (no option exceeding 1.25x the length of the shortest).\n")
                    append("5. ORDERING: If options contain numerical values, dates, or quantitative ranges, order them strictly from lowest to highest.\n")
                    append("6. PROHIBITED PHRASES: Never use \"All of the above\", \"None of the above\", or \"Both A and B\".\n")
                    append("7. NEGATIVE STEMS: Avoid negative questions where possible. If unavoidable, highlight the negative operator in uppercase bold (e.g., **NOT**, **EXCEPT**, **LEAST**).\n")
                    if (promptIncludeFormulasAndDiagrams) {
                        append("8. SCIENTIFIC & MATH NOTATION: Enclose all mathematical expressions, chemical equations, units, and formulas in standard LaTeX using single dollar signs (e.g., \$E = mc^2\$, \$H_2SO_4\$, \$9.8\\text{ m/s}^2\$).\n")
                        append("9. DIAGRAMS: If a question references a diagram, chart, or figure, include: Diagram: [Figure title or description | Optional URL].\n")
                    } else {
                        append("8. SCIENTIFIC & MATH NOTATION: Enclose all mathematical expressions, chemical equations, units, and formulas in standard LaTeX using single dollar signs (e.g., \$E = mc^2\$, \$H_2SO_4\$, \$9.8\\text{ m/s}^2\$).\n")
                    }
                    append("\n")

                    append("### 3. STATISTICAL BALANCE & ZERO-DISCREPANCY\n")
                    append("1. BALANCED KEY DISTRIBUTION: Distribute correct answers roughly evenly (~25% each across A, B, C, D). Never allow more than two consecutive identical answer keys (e.g., A, A is allowed; A, A, A is strictly prohibited).\n")
                    append("2. PHASE SYNC: The option declared in Phase 2 MUST match the option identified as \"Correct\" in Phase 3 without exception.\n")
                    append("3. ZERO CHATTER: Output starts immediately with \"Phase 1: Questions\". No introductory remarks, disclaimers, or closing text.\n")
                    append("4. NO DIVIDER LINES: Do NOT use markdown horizontal divider lines (---) anywhere in the questions or explanations.\n\n")

                    append("### EXACT OUTPUT FORMAT TEMPLATE:\n\n")
                    append("Phase 1: Questions\n")
                    append("Q1. [Clear, unambiguous question stem or scenario]\n")
                    append("(A) [Option A]\n")
                    append("(B) [Option B]\n")
                    append("(C) [Option C]\n")
                    append("(D) [Option D]\n\n")
                    append("[Repeat through Q$countVal]\n\n")

                    append("Phase 2: Answer Key\n")
                    append("Q1: (A)\n")
                    append("Q2: (C)\n")
                    append("[Repeat through Q$countVal]\n\n")

                    append("Phase 3: Explanations & Solution\n")
                    if (promptExplainAllFourOptions) {
                        append("Q1.\n")
                        append("• Core Concept & Reasoning: [1-2 concise sentences stating the governing principle, theorem, or calculation path]\n")
                        append("• Option Breakdown:\n")
                        append("  - (A) [Correct/Incorrect]: [Direct explanation with underlying reason]\n")
                        append("  - (B) [Correct/Incorrect]: [Direct explanation with underlying reason]\n")
                        append("  - (C) [Correct/Incorrect]: [Direct explanation with underlying reason]\n")
                        append("  - (D) [Correct/Incorrect]: [Direct explanation with underlying reason]\n")
                        append("• Key Takeaway / Memory Anchor: [Single memorable takeaway, key formula, or mental model]\n\n")
                    } else {
                        append("Q1.\n")
                        append("• Core Concept & Reasoning: [1-2 concise sentences stating the governing principle, theorem, or calculation path]\n")
                        append("• Why Correct: [Direct explanation with underlying reason for the correct option]\n")
                        append("• Distractor Breakdown: [Key distinctions explaining why incorrect options fail]\n")
                        append("• Key Takeaway / Memory Anchor: [Single memorable takeaway, key formula, or mental model]\n\n")
                    }
                    append("[Repeat through Q$countVal without any horizontal divider lines (---)]\n\n")

                    if (promptEnableQuantityCorrectionRule) {
                        append("Phase 4: Format & Quantity Enforcement (Truncation Guard Active)\n")
                        append("1. Strict Quantity Check: Generate exactly all $countVal questions from 1 to $countVal without stopping early or omitting questions.\n")
                        append("2. High-Density Token Budget: To fit all $countVal questions and explanations completely without token limit cutoffs, keep each option's explanation to 2 to 3 concise, high-impact sentences (30–45 words).\n")
                        append("3. Depth Uniformity: Maintain identical pedagogical depth from Question 1 through Question $countVal. Do not rush or shorten later questions.\n\n")
                    }

                    append("Execute the instructions now, starting directly with Phase 1: Questions.")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Generated Copiable Prompt",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Text(
                        text = "Tap box to copy",
                        fontSize = 11.sp,
                        color = colors.teal,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surfaceVariant,
                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = android.content.ClipData.newPlainText("ExamSet Prompt", fullPrompt)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = fullPrompt,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.inkBold,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons Row: Copy Prompt & Switch to Input Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = android.content.ClipData.newPlainText("ExamSet Prompt", fullPrompt)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Prompt copied! Paste it into your AI assistant.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.navyBanner,
                            contentColor = colors.navyBannerText
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("copy_prompt_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy Prompt",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    OutlinedButton(
                        onClick = { activeSheetTab = 0 },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.tealDark,
                            containerColor = colors.surface
                        ),
                        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("switch_to_input_tab_button")
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.teal)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Paste Response",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
}
}
}
}
