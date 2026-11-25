package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import android.widget.Toast
import com.example.model.ExamConfig
import com.example.model.FolderItem
import com.example.model.PaperCategory
import com.example.model.PromptPreset
import com.example.model.PromptPresetMode
import com.example.model.QuestionPromptConfig
import com.example.util.FolderPathHelper
import com.example.data.PaperEntity
import com.example.model.QuestionPaper
import com.example.ui.components.QuestionCard
import com.example.util.CameraCaptureHelper
import com.example.util.FeatureToggles
import com.example.util.ExamJsonHelper
import com.example.util.HandwrittenNotesExtractor
import com.example.util.McqParser
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ui.screens.DismissibleFormatTipBanner
import com.example.ui.theme.ExamTheme

private const val LITERAL_SLASH_PLACEHOLDER = "\u0000LITERAL_SLASH\u0000"

internal fun parseSubjectSegments(raw: String): List<String> {
    val clean = raw.trim()
    if (clean.isBlank()) return emptyList()
    return clean.replace("//", LITERAL_SLASH_PLACEHOLDER)
        .split("/", ">")
        .map { it.replace(LITERAL_SLASH_PLACEHOLDER, "/").trim() }
        .filter { it.isNotBlank() }
}

private fun subjectSegmentsMatch(a: List<String>, b: List<String>): Boolean {
    if (a.size != b.size) return false
    return a.zip(b).all { (segA, segB) -> segA.equals(segB, ignoreCase = true) }
}

internal fun getFolderPathSegments(folderId: String?, folders: List<FolderItem>): List<String> {
    if (folderId.isNullOrBlank() || folderId == "all" || folders.isEmpty()) return emptyList()
    val folderById = folders.associateBy { it.id }
    val chain = mutableListOf<String>()
    var curr = folderById[folderId]
    val visited = mutableSetOf<String>()
    while (curr != null && visited.add(curr.id)) {
        chain.add(0, curr.name.trim())
        curr = curr.parentId?.let { folderById[it] }
    }
    return chain
}

fun resolveFolderBySubjectPath(path: String, folders: List<FolderItem>): FolderItem? {
    val cleanPath = path.trim()
    if (cleanPath.isBlank() ||
        cleanPath.equals("General", ignoreCase = true) ||
        cleanPath.equals("General Studies", ignoreCase = true) ||
        folders.isEmpty()
    ) {
        return null
    }

    // 1. Direct match against full canonical "Parent > Child" folder paths (preserves literal '/' inside folder names)
    val canonicalInput = cleanPath.split(">").joinToString(" > ") { it.trim() }
    val directMatch = folders.find { folder ->
        getFolderPathSegments(folder.id, folders).joinToString(" > ").equals(canonicalInput, ignoreCase = true)
    }
    if (directMatch != null) return directMatch

    // 2. Segment walk supporting '/', '>', and '//'-escaped literal slashes
    val segments = parseSubjectSegments(cleanPath)
    if (segments.isEmpty()) return null

    var currentParentId: String? = null
    var lastFolder: FolderItem? = null
    for (segment in segments) {
        val match = folders.find { f ->
            f.parentId == currentParentId && f.name.equals(segment, ignoreCase = true)
        } ?: return null
        lastFolder = match
        currentParentId = match.id
    }
    return lastFolder
}

internal fun extractSetNumberValue(setNumber: String, title: String = ""): Int? {
    val trimmedSet = setNumber.trim()
    if (trimmedSet.isNotBlank()) {
        val explicitSetNum = Regex("""\b(?:Set|Series|Part)\s*[-:#]?\s*(\d+)\b""", RegexOption.IGNORE_CASE)
            .find(trimmedSet)?.groupValues?.get(1)?.toIntOrNull()
        if (explicitSetNum != null) return explicitSetNum

        val anyDigits = Regex("""(\d+)""").find(trimmedSet)?.groupValues?.get(1)?.toIntOrNull()
        if (anyDigits != null) return anyDigits

        val strippedPrefix = trimmedSet
            .replace(Regex("""^(?:(?:Set|Series|Part)\s*[:\-]?\s*)+""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (strippedPrefix.length == 1) {
            val ch = strippedPrefix[0].uppercaseChar()
            if (ch in 'A'..'Z') return ch - 'A' + 1
        }
        // Set field already has a non-empty value; do not misread years/numbers in title
        return null
    }

    if (title.isBlank()) return null
    val explicitInTitle = Regex("""\b(?:Set|Series|Part)\s*[-:#]?\s*(\d+)\b""", RegexOption.IGNORE_CASE)
        .find(title)?.groupValues?.get(1)?.toIntOrNull()
    if (explicitInTitle != null) return explicitInTitle

    val explicitLetterInTitle = Regex("""\b(?:Set|Series|Part)\s*[-:#]?\s*([A-Z])\b""", RegexOption.IGNORE_CASE)
        .find(title)?.groupValues?.get(1)?.uppercase()?.firstOrNull()
    if (explicitLetterInTitle != null && explicitLetterInTitle in 'A'..'Z') {
        return explicitLetterInTitle - 'A' + 1
    }
    return null
}

internal fun isSetNumberMatch(setA: String, setB: String): Boolean {
    val a = setA.trim()
    val b = setB.trim()
    if (a.isBlank() || b.isBlank()) return false
    if (a.equals(b, ignoreCase = true)) return true
    val numA = extractSetNumberValue(a)
    val numB = extractSetNumberValue(b)
    return numA != null && numB != null && numA == numB
}

internal fun paperBelongsToDestination(
    paper: PaperEntity,
    subject: String,
    targetFolderId: String? = null,
    folders: List<FolderItem> = emptyList()
): Boolean {
    val cleanTargetId = targetFolderId?.trim()?.takeIf { it.isNotBlank() && it != "all" }
    val resolvedFolderId = cleanTargetId ?: if (folders.isNotEmpty()) {
        resolveFolderBySubjectPath(subject, folders)?.id
    } else {
        null
    }

    val subjectSegs = parseSubjectSegments(subject)
    val targetPathSegs = if (resolvedFolderId != null && folders.isNotEmpty()) {
        getFolderPathSegments(resolvedFolderId, folders).ifEmpty { subjectSegs }
    } else {
        subjectSegs
    }

    return when {
        resolvedFolderId != null -> {
            if (paper.folderId == resolvedFolderId) {
                true
            } else if (paper.folderId.isNullOrBlank()) {
                targetPathSegs.isNotEmpty() &&
                    subjectSegmentsMatch(parseSubjectSegments(paper.subject), targetPathSegs)
            } else {
                // Paper explicitly lives in a different folder
                false
            }
        }
        subjectSegs.isNotEmpty() -> {
            val paperFolderId = paper.folderId?.trim()?.takeIf { it.isNotBlank() && it != "all" }
            if (paperFolderId != null && folders.isNotEmpty()) {
                val paperFolderSegs = getFolderPathSegments(paperFolderId, folders)
                if (paperFolderSegs.isNotEmpty()) {
                    // Belongs to an existing folder whose path != subjectSegs
                    subjectSegmentsMatch(paperFolderSegs, subjectSegs)
                } else {
                    subjectSegmentsMatch(parseSubjectSegments(paper.subject), subjectSegs)
                }
            } else {
                subjectSegmentsMatch(parseSubjectSegments(paper.subject), subjectSegs)
            }
        }
        else -> {
            // Unfiled / Root destination
            val pSub = paper.subject.trim()
            paper.folderId.isNullOrBlank() && (
                pSub.isBlank() ||
                    pSub.equals("General", ignoreCase = true) ||
                    pSub.equals("General Studies", ignoreCase = true)
            )
        }
    }
}

fun computeNextSetForSubject(
    subject: String,
    papers: List<PaperEntity>,
    targetFolderId: String? = null,
    folders: List<FolderItem> = emptyList()
): String {
    val relevantPapers = papers.filter { p ->
        paperBelongsToDestination(
            paper = p,
            subject = subject,
            targetFolderId = targetFolderId,
            folders = folders
        )
    }
    if (relevantPapers.isEmpty()) return "01"

    val foundNumbers = relevantPapers.mapNotNull { p ->
        extractSetNumberValue(p.setNumber, p.title)
    }

    val nextNum = if (foundNumbers.isNotEmpty()) {
        (foundNumbers.maxOrNull() ?: 0) + 1
    } else {
        relevantPapers.size + 1
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
    initialSubject: String = "",
    initialFolderId: String? = null,
    savedPapers: List<PaperEntity> = emptyList(),
    folders: List<FolderItem> = emptyList(),
    onDismiss: () -> Unit,
    onGenerate: (
        text: String,
        title: String,
        subject: String,
        topic: String,
        setNumber: String,
        targetFolderId: String?,
        durationMinutes: Int,
        positiveMarks: Double,
        negativeMarks: Double,
        unattendedMarks: Double,
        manualCategory: String
    ) -> Unit,
    onGenerateConfig: ((ExamConfig) -> Unit)? = null,
    onPickFile: () -> Unit,
    onLoadSample: (Int) -> Unit,
    onOpenPromptBuilder: (() -> Unit)? = null,
    promptPresets: List<PromptPreset> = emptyList(),
    activeQuestionPresetId: String? = null,
    onSelectQuestionPreset: (PromptPreset) -> Unit = {},
    onSaveNewQuestionPreset: (String, QuestionPromptConfig) -> Unit = { _, _ -> },
    onUpdateQuestionPreset: (String, String?, QuestionPromptConfig) -> Unit = { _, _, _ -> },
    onRenamePromptPreset: (String, String) -> Unit = { _, _ -> },
    onDuplicatePromptPreset: (String) -> Unit = {},
    onTogglePinPromptPreset: (String) -> Unit = {},
    onDeletePromptPreset: (String) -> Unit = {},
    onClearQuestionPreset: () -> Unit = {},
    showFormatTip: Boolean = false,
    onDismissFormatTip: () -> Unit = {},
    onOpenAppGuide: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = ExamTheme.colors
    val haptic = LocalHapticFeedback.current
    var textInput by remember(initialText) { mutableStateOf(initialText) }
    var inputContentFormat by remember { mutableStateOf("Text / Aiken") }

    // Auto-detect format when input is pasted or updated
    LaunchedEffect(textInput) {
        val trimmed = textInput.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("```json")) {
            inputContentFormat = "JSON Format"
        } else if (trimmed.contains("|") && trimmed.lines().count { it.contains("|") } >= 2) {
            inputContentFormat = "Markdown Table / CSV"
        }
    }

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
    val cleanInitialFolderId = initialFolderId?.trim()?.takeIf { it.isNotBlank() && it != "all" }
    val resolvedInitialSubject = remember(initialSubject, cleanInitialFolderId, folders) {
        initialSubject.trim().ifBlank {
            if (cleanInitialFolderId != null) {
                getFolderPathSegments(cleanInitialFolderId, folders).joinToString(" > ")
            } else ""
        }
    }

    var customTitle by remember { mutableStateOf("") }
    var customSubject by remember(resolvedInitialSubject, cleanInitialFolderId) {
        mutableStateOf(resolvedInitialSubject)
    }
    var customTopic by remember { mutableStateOf("") }
    var hasManuallyEditedSubject by remember(resolvedInitialSubject, cleanInitialFolderId) {
        mutableStateOf(false)
    }
    var hasManuallyEditedSetNumber by remember(resolvedInitialSubject, cleanInitialFolderId) {
        mutableStateOf(false)
    }

    // Always resolve the actual destination folder where this set will be saved
    val resolvedTargetFolderId by remember(customSubject, folders, cleanInitialFolderId, resolvedInitialSubject) {
        derivedStateOf {
            val curSegs = parseSubjectSegments(customSubject)
            val initSegs = parseSubjectSegments(resolvedInitialSubject)
            if (cleanInitialFolderId != null && curSegs.isNotEmpty() && subjectSegmentsMatch(curSegs, initSegs)) {
                cleanInitialFolderId
            } else if (curSegs.isNotEmpty()) {
                resolveFolderBySubjectPath(customSubject, folders)?.id
            } else {
                null
            }
        }
    }

    val recommendedSetNumber by remember(customSubject, savedPapers, resolvedTargetFolderId, folders) {
        derivedStateOf {
            computeNextSetForSubject(
                subject = customSubject,
                papers = savedPapers,
                targetFolderId = resolvedTargetFolderId,
                folders = folders
            )
        }
    }

    var customSetNumber by remember(resolvedInitialSubject, cleanInitialFolderId) {
        mutableStateOf(
            computeNextSetForSubject(
                subject = resolvedInitialSubject,
                papers = savedPapers,
                targetFolderId = cleanInitialFolderId ?: resolveFolderBySubjectPath(resolvedInitialSubject, folders)?.id,
                folders = folders
            )
        )
    }

    LaunchedEffect(recommendedSetNumber, hasManuallyEditedSetNumber) {
        if (!hasManuallyEditedSetNumber) {
            customSetNumber = recommendedSetNumber
        }
    }

    var selectedCategory by remember { mutableStateOf(PaperCategory.AUTO) }
    var customDurationMinutesStr by remember { mutableStateOf("60") }
    var customPositiveMarksStr by remember { mutableStateOf("4.0") }
    var customNegativeMarksStr by remember { mutableStateOf("1.0") }
    var customUnattendedMarksStr by remember { mutableStateOf("0.0") }
    var showCategorizationSection by remember { mutableStateOf(false) }
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
    var localActiveQuestionPresetId by remember(activeQuestionPresetId) { mutableStateOf(activeQuestionPresetId) }
    var clearedSheetPromptUndo by remember { mutableStateOf<Triple<QuestionPromptConfig, String, String?>?>(null) }

    fun currentSheetQuestionConfig(): QuestionPromptConfig = QuestionPromptConfig(
        examType = promptExam,
        subject = promptSubject,
        questionCount = promptMcqCount.toIntOrNull()?.coerceIn(5, 200) ?: 20,
        difficulty = promptDifficulty,
        questionType = promptQuestionType,
        questionLanguage = promptQuestionLanguage,
        explanationChoice = promptExplanationChoice,
        customExplanationLanguage = promptCustomExplanationLanguage,
        explainAllFourOptions = promptExplainAllFourOptions,
        includeFormulasAndDiagrams = promptIncludeFormulasAndDiagrams,
        enableQuantityCorrectionRule = promptEnableQuantityCorrectionRule
    ).normalized()

    fun applySheetQuestionConfig(cfg: QuestionPromptConfig, preserveTopic: Boolean = true) {
        val c = cfg.normalized()
        promptExam = c.examType
        promptSubject = c.subject
        if (!preserveTopic) promptTopic = ""
        promptMcqCount = c.questionCount.toString()
        promptDifficulty = c.difficulty
        promptQuestionType = c.questionType
        promptQuestionLanguage = c.questionLanguage
        promptExplanationChoice = c.explanationChoice
        promptCustomExplanationLanguage = c.customExplanationLanguage
        promptExplainAllFourOptions = c.explainAllFourOptions
        promptIncludeFormulasAndDiagrams = c.includeFormulasAndDiagrams
        promptEnableQuantityCorrectionRule = c.enableQuantityCorrectionRule
    }

    val effectivePromptExplanationLanguage by remember(promptQuestionLanguage, promptExplanationChoice, promptCustomExplanationLanguage) {
        derivedStateOf {
            val q = promptQuestionLanguage.trim().ifBlank { "English" }
            val customLang = promptCustomExplanationLanguage.trim()
            when (promptExplanationChoice) {
                "Same as Question" -> "$q (same language as the questions)"
                "Bilingual" -> {
                    if (q.equals("English", ignoreCase = true)) {
                        if (customLang.isNotBlank()) "Bilingual (English & $customLang)"
                        else "Bilingual (English & Second Language)"
                    } else {
                        val secondLang = customLang.ifBlank { "English" }
                        "Bilingual ($secondLang & $q)"
                    }
                }
                "English" -> "English"
                "Hindi" -> "Hindi"
                "Custom" -> customLang.ifBlank { q }
                else -> promptExplanationChoice
            }
        }
    }

    // Auto-fill suggested metadata when text changes if fields are currently empty.
    // Never override folder-derived Set Number with stale/default metadata from pasted text.
    LaunchedEffect(parsedPreview) {
        parsedPreview?.let { preview ->
            if (customTitle.isBlank() && preview.metadata.title != "MCQ Practice Set") {
                customTitle = preview.metadata.title
            }
            if (customSubject.isBlank() &&
                !hasManuallyEditedSubject &&
                cleanInitialFolderId == null &&
                preview.metadata.subject != "General Studies"
            ) {
                customSubject = preview.metadata.subject
            }
            if (customTopic.isBlank() && preview.metadata.topic.isNotBlank()) {
                customTopic = preview.metadata.topic
            }
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val columnScrollState = rememberScrollState()
    // Measured height of the visible scroll area between TopBar and BottomBar
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

    // Dedicated nested scroll connection on the text field:
    // Scrolling inside the text area scrolls the text smoothly and exposes the floating scrollbar indicator.
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

    val existingPapersInDestination = remember(savedPapers, customSubject, resolvedTargetFolderId, folders) {
        savedPapers.filter { p ->
            paperBelongsToDestination(
                paper = p,
                subject = customSubject,
                targetFolderId = resolvedTargetFolderId,
                folders = folders
            )
        }
    }
    val isSetDuplicate = remember(existingPapersInDestination, customSetNumber) {
        existingPapersInDestination.any { p ->
            isSetNumberMatch(p.setNumber, customSetNumber)
        }
    }
    val canGenerate = textInput.isNotBlank() && !isSetDuplicate

    val parsedCount = promptMcqCount.trim().toIntOrNull() ?: 20
    val fullPrompt = remember(
        parsedCount,
        promptExam,
        promptSubject,
        promptTopic,
        promptQuestionLanguage,
        promptExplanationChoice,
        effectivePromptExplanationLanguage,
        promptDifficulty,
        promptQuestionType,
        promptExplainAllFourOptions,
        promptIncludeFormulasAndDiagrams,
        promptEnableQuantityCorrectionRule
    ) {
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

        buildString {
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
                "Multiple-Select (MSQ)" -> append("2. QUESTION FORMAT FOCUS (STRICT MSQ): All questions MUST be Multiple-Selection Questions with 2+ correct options. Prefix every stem with '[MSQ] ' (or include 'Select all that apply.') and format Phase 2 keys as Q1: (A, C).\n")
                "Mixed Objective (MCQ + MSQ)" -> append("2. QUESTION FORMAT FOCUS (MIXED MCQ + MSQ): Include ~65% single-choice MCQs and ~35% true MSQs (2+ correct options). Tag ONLY MSQ items with '[MSQ] ' and format their keys as Q2: (A, C); keep single-choice MCQs untagged.\n")
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
            append(if (promptQuestionType == "Multiple-Select (MSQ)") "Q1. [MSQ] [Clear question stem with 2+ correct options (Select all that apply)]\n" else "Q1. [Clear, unambiguous question stem or scenario]\n")
            append("(A) [Option A]\n")
            append("(B) [Option B]\n")
            append("(C) [Option C]\n")
            append("(D) [Option D]\n\n")
            append("[Repeat through Q$countVal]\n\n")

            append("Phase 2: Answer Key\n")
            when (promptQuestionType) {
                "Multiple-Select (MSQ)" -> {
                    append("Q1: (A, C)\n")
                    append("Q2: (B, D)\n")
                }
                "Mixed Objective (MCQ + MSQ)" -> {
                    append("Q1: (A)\n")
                    append("Q2: (A, C)\n")
                }
                else -> {
                    append("Q1: (A)\n")
                    append("Q2: (C)\n")
                }
            }
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
    }

    val headerGradient = remember(colors.isDark) {
        Brush.horizontalGradient(
            colors = listOf(colors.teal, colors.navyBanner)
        )
    }

    BackHandler {
        keyboardController?.hide()
        onDismiss()
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
                                    .testTag("close_sheet_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = colors.inkBold
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = "Create Question Set",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Paste text, import file (.txt/.md), or build AI prompt",
                                    fontSize = 11.5.sp,
                                    color = colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                    if (clipText.isNotBlank()) {
                                        activeSheetTab = 0
                                        textInput = clipText
                                        Toast.makeText(context, "Pasted text from clipboard", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("paste_clipboard_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste from clipboard",
                                    tint = colors.teal
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
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                        Column(
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                        ) {
                            if (activeSheetTab == 0) {
                                if (isSetDuplicate) {
                                    Surface(
                                        color = colors.red.copy(alpha = 0.12f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = colors.red,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Set $customSetNumber already exists in ${customSubject.trim().ifBlank { "this folder" }}",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.red,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Edit Set #",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.tealDark,
                                                modifier = Modifier.clickable {
                                                    showCategorizationSection = true
                                                    coroutineScope.launch {
                                                        columnScrollState.animateScrollTo(0)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus(force = true)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                                        border = BorderStroke(1.dp, colors.border),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(0.75f)
                                            .heightIn(min = 48.dp)
                                            .testTag("cancel_sheet_button")
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { textInput = "" },
                                        enabled = textInput.isNotBlank(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                                        border = BorderStroke(1.dp, colors.border),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(0.75f)
                                            .heightIn(min = 48.dp)
                                            .testTag("clear_text_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Clear",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (canGenerate) {
                                                keyboardController?.hide()
                                                focusManager.clearFocus(force = true)
                                                val cleanSubject = if (customSubject.contains("//")) {
                                                    customSubject.trim()
                                                } else {
                                                    FolderPathHelper.normalizeFolderPath(customSubject)
                                                }
                                                val parsedDuration = customDurationMinutesStr.toIntOrNull()?.coerceIn(5, 360) ?: 60
                                                val parsedPosMarks = customPositiveMarksStr.toDoubleOrNull()?.coerceIn(0.25, 20.0) ?: 4.0
                                                val parsedNegMarks = customNegativeMarksStr.toDoubleOrNull()?.coerceIn(0.0, 10.0) ?: 1.0
                                                val parsedUnkMarks = customUnattendedMarksStr.toDoubleOrNull()?.coerceIn(0.0, 10.0) ?: 0.0
                                                val effectiveSet = customSetNumber.trim().ifBlank { recommendedSetNumber }

                                                onGenerate(
                                                    textInput,
                                                    customTitle,
                                                    cleanSubject,
                                                    customTopic,
                                                    effectiveSet,
                                                    resolvedTargetFolderId,
                                                    parsedDuration,
                                                    parsedPosMarks,
                                                    parsedNegMarks,
                                                    parsedUnkMarks,
                                                    selectedCategory.code
                                                )
                                                onDismiss()
                                            }
                                        },
                                        enabled = canGenerate,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.navyBanner,
                                            contentColor = colors.navyBannerText,
                                            disabledContainerColor = colors.surface,
                                            disabledContentColor = colors.muted
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1.5f)
                                            .heightIn(min = 48.dp)
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
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
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
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
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
                        .widthIn(max = 760.dp)
                        .fillMaxSize()
                        .onSizeChanged { scrollViewportPx = it.height }
                        .padding(horizontal = 16.dp)
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
                        modifier = Modifier.testTag("sample_civil_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Engineering,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Engineering", fontSize = 12.sp, color = colors.inkBold, maxLines = 1)
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

            // Categorization, Set No & Exam Parameters Section (Collapsible Summary Bar)
            val resolvedFolderDisplayPath = remember(resolvedTargetFolderId, folders) {
                resolvedTargetFolderId?.let { id ->
                    getFolderPathSegments(id, folders).joinToString(" > ")
                }?.takeIf { it.isNotBlank() }
            }
            val summarySubject = resolvedFolderDisplayPath ?: customSubject.trim().ifBlank { "Root / General" }
            val summarySet = customSetNumber.trim().ifBlank { recommendedSetNumber }
            val summaryDuration = customDurationMinutesStr.ifBlank { "60" }
            val summaryPos = customPositiveMarksStr.ifBlank { "4.0" }
            val summaryNeg = customNegativeMarksStr.ifBlank { "1.0" }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceVariant,
                border = BorderStroke(0.9.dp, if (isSetDuplicate) colors.red else colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { showCategorizationSection = !showCategorizationSection },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isSetDuplicate) Icons.Default.Warning else Icons.Default.Tag,
                                contentDescription = null,
                                tint = if (isSetDuplicate) colors.red else colors.teal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Set Details & Marking",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isSetDuplicate) {
                                        "Duplicate: $summarySubject • Set $summarySet (Tap to change)"
                                    } else {
                                        "$summarySubject • Set $summarySet • ${summaryDuration}m (+$summaryPos / -$summaryNeg)"
                                    },
                                    fontSize = 11.sp,
                                    color = if (isSetDuplicate) colors.red else colors.muted,
                                    fontWeight = if (isSetDuplicate) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (showCategorizationSection) "Hide" else "Edit",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.tealDark
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = if (showCategorizationSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showCategorizationSection) "Collapse setup" else "Expand setup",
                                tint = colors.muted
                            )
                        }
                    }

                    AnimatedVisibility(visible = showCategorizationSection) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            // Title field full width
                            OutlinedTextField(
                                value = customTitle,
                                onValueChange = { customTitle = it },
                                label = { Text("Title", fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .testTag("import_title_field"),
                                shape = RoundedCornerShape(6.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Subject / Folder Path full width (strictly user-folder Parent > Child > Sub-Child suggestions)
                            Box(modifier = Modifier.fillMaxWidth()) {

                                OutlinedTextField(
                                    value = customSubject,
                                    onValueChange = { newSub ->
                                        hasManuallyEditedSubject = true
                                        val normalized = if (newSub.contains("//")) newSub else newSub.replace("/", " > ")
                                        customSubject = normalized
                                    },
                                    label = { Text("Subject / Folder Path", fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    placeholder = { Text("e.g. Parent > Child > Sub-Child", fontSize = 11.5.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true,
                                    colors = fieldColors,
                                    trailingIcon = {
                                        if (folders.isNotEmpty()) {
                                            IconButton(
                                                onClick = { isSubjectDropdownOpen = !isSubjectDropdownOpen },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("import_subject_dropdown_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = "Browse User Folders",
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

                                UserFolderDropdownMenu(
                                    expanded = isSubjectDropdownOpen && folders.isNotEmpty(),
                                    query = customSubject,
                                    folders = folders,
                                    onDismissRequest = { isSubjectDropdownOpen = false },
                                    onSelectEntry = { entry ->
                                        hasManuallyEditedSubject = true
                                        customSubject = entry.fullPath
                                        if (!hasManuallyEditedSetNumber) {
                                            val nextFolderId = resolveFolderBySubjectPath(entry.fullPath, folders)?.id
                                            customSetNumber = computeNextSetForSubject(
                                                subject = entry.fullPath,
                                                papers = savedPapers,
                                                targetFolderId = nextFolderId,
                                                folders = folders
                                            )
                                        }
                                        isSubjectDropdownOpen = false
                                    }
                                )
                            }

                            // [DEPRECATED 2026-11-25] Reason: Auto-appearing chip bar below Subject felt like an unsolicited popup; replaced by on-demand UserFolderDropdownMenu on drop-down icon click. Restore if: Inline suggestion chips while typing are explicitly requested again.
                            /*
                            if (customSubject.isNotBlank() && folderSuggestions.isNotEmpty()) {
                                LazyRow(...) { ... }
                            }
                            */

                            Spacer(modifier = Modifier.height(8.dp))

                            // Topic & Set No in one row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customTopic,
                                    onValueChange = { customTopic = it },
                                    label = { Text("Topic", fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier
                                        .weight(2.5f)
                                        .heightIn(min = 56.dp)
                                        .testTag("import_topic_field"),
                                    shape = RoundedCornerShape(6.dp)
                                )

                                OutlinedTextField(
                                    value = customSetNumber,
                                    onValueChange = { newVal ->
                                        customSetNumber = newVal
                                        hasManuallyEditedSetNumber = newVal.isNotBlank() &&
                                            !isSetNumberMatch(newVal, recommendedSetNumber)
                                    },
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

                            Text(
                                text = "Test Section Category",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
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
                                    val isSelected = selectedCategory == cat
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat.label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colors.tealLight,
                                            selectedLabelColor = colors.tealDark
                                        ),
                                        modifier = Modifier.testTag("import_category_${cat.code.lowercase()}")
                                    )
                                }
                            }

                            if (customSubject.isNotBlank() || resolvedTargetFolderId != null || existingPapersInDestination.isNotEmpty() || isSetDuplicate || hasManuallyEditedSetNumber) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val isExistingFolder = resolvedTargetFolderId != null || existingPapersInDestination.isNotEmpty()
                                val existingCount = existingPapersInDestination.size
                                val destLabel = resolvedFolderDisplayPath ?: customSubject.trim().ifBlank { "Root / General" }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSetDuplicate) colors.red.copy(alpha = 0.1f) else colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, if (isSetDuplicate) colors.red else colors.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
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
                                                    "Set $summarySet already exists in $destLabel. Use Set $recommendedSetNumber or change set number."
                                                } else if (isExistingFolder) {
                                                    "Saving to: $destLabel ($existingCount sets) • Set: $summarySet"
                                                } else {
                                                    "New Folder: $destLabel • Starting Set: $summarySet"
                                                },
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isSetDuplicate) colors.red else colors.inkBold
                                            )
                                        }
                                        if (hasManuallyEditedSetNumber || isSetDuplicate) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = colors.teal.copy(alpha = 0.14f),
                                                border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.5f)),
                                                modifier = Modifier
                                                    .clickable {
                                                        hasManuallyEditedSetNumber = false
                                                        customSetNumber = recommendedSetNumber
                                                    }
                                                    .testTag("auto_set_reset_button")
                                            ) {
                                                Text(
                                                    text = "Auto ($recommendedSetNumber)",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.tealDark,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                val placeholder = "\u0000LITERAL_SLASH\u0000"
                                val normalizedSubject = customSubject.replace("//", placeholder)
                                val hasStructuralHierarchy = normalizedSubject.contains("/") || normalizedSubject.contains(">")

                                if (hasStructuralHierarchy) {
                                    val pathSegments = normalizedSubject.split("/", ">")
                                        .map { it.replace(placeholder, "/").trim() }
                                        .filter { it.isNotBlank() }
                                    if (pathSegments.size > 1) {
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
                                                    text = pathSegments.joinToString(" > "),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.inkBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                } else if (customSubject.contains("//")) {
                                    val literalName = customSubject.replace("//", "/").trim()
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
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = colors.teal,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Literal Folder: ",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.tealDark
                                            )
                                            Text(
                                                text = literalName,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.inkBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Examination Parameters (Duration & Marks)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surfaceVariant,
                                border = BorderStroke(0.6.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = colors.teal,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Exam Parameters (Duration & Marks)",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customDurationMinutesStr,
                                            onValueChange = { input ->
                                                customDurationMinutesStr = input.filter { it.isDigit() }.take(4)
                                            },
                                            label = { Text("Duration (m)", fontSize = 11.sp) },
                                            placeholder = { Text("60", fontSize = 11.sp) },
                                            singleLine = true,
                                            colors = fieldColors,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 52.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        OutlinedTextField(
                                            value = customPositiveMarksStr,
                                            onValueChange = { input ->
                                                if (input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                                    customPositiveMarksStr = input
                                                }
                                            },
                                            label = { Text("Correct (+)", fontSize = 11.sp) },
                                            placeholder = { Text("4.0", fontSize = 11.sp) },
                                            singleLine = true,
                                            colors = fieldColors,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 52.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customNegativeMarksStr,
                                            onValueChange = { input ->
                                                if (input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                                    customNegativeMarksStr = input
                                                }
                                            },
                                            label = { Text("Penalty (-)", fontSize = 11.sp) },
                                            placeholder = { Text("1.0", fontSize = 11.sp) },
                                            singleLine = true,
                                            colors = fieldColors,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 52.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        OutlinedTextField(
                                            value = customUnattendedMarksStr,
                                            onValueChange = { input ->
                                                if (input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                                    customUnattendedMarksStr = input
                                                }
                                            },
                                            label = { Text("Unattended (-)", fontSize = 11.sp) },
                                            placeholder = { Text("0.0", fontSize = 11.sp) },
                                            singleLine = true,
                                            colors = fieldColors,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 52.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                    }

                                    // Universal & Regional Scoring Scheme Presets
                                    var scoringPresetCategory by remember { mutableStateOf("Universal") }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Marking Presets:", fontSize = 9.5.sp, color = colors.muted, fontWeight = FontWeight.Bold)
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
                                            val isSelected = customPositiveMarksStr == pos.toString() && customNegativeMarksStr == neg.toString()
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) colors.tealLight else colors.surface,
                                                border = BorderStroke(0.7.dp, if (isSelected) colors.teal else colors.border),
                                                modifier = Modifier.clickable {
                                                    customPositiveMarksStr = pos.toString()
                                                    customNegativeMarksStr = neg.toString()
                                                }
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) colors.tealDark else colors.ink,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Quick duration chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Duration:", fontSize = 9.5.sp, color = colors.muted, fontWeight = FontWeight.Bold)
                                        listOf(30, 45, 60, 90, 120, 180).forEach { mins ->
                                            val isSelected = customDurationMinutesStr == mins.toString()
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) colors.teal else colors.surface,
                                                border = BorderStroke(0.7.dp, if (isSelected) colors.teal else colors.border),
                                                modifier = Modifier.clickable { customDurationMinutesStr = mins.toString() }
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
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Text Area Input Header + Toolbar + Editor grouped for clean bring-into-view above keyboard
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester)
            ) {
                DismissibleFormatTipBanner(
                    tipId = "input_mcq_formats",
                    title = "Supported Input Formats",
                    body = "Paste numbered MCQs (1. or Q1. with A–D), Aiken format (ANSWER: B), JSON arrays, or Markdown tables. Answers at the bottom are auto-linked!",
                    isDismissed = !showFormatTip,
                    onDismissTip = { onDismissFormatTip() },
                    onOpenGuide = onOpenAppGuide,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

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
                            .heightIn(min = 38.dp)
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
                            .heightIn(min = 38.dp)
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
                                .heightIn(min = 38.dp)
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
                        onClick = {
                            val expanding = !isTextAreaExpanded
                            isTextAreaExpanded = expanding
                            if (expanding) {
                                showCategorizationSection = false
                                coroutineScope.launch {
                                    withFrameNanos { }
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                        border = BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .heightIn(min = 38.dp)
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

                // Maintain generous height when typing; scale up nicely in Full View without squishing to 80dp
                val fieldDensity = LocalDensity.current
                val imeOpen = WindowInsets.ime.getBottom(fieldDensity) > 0
                val viewportDp = with(fieldDensity) { scrollViewportPx.toDp() }
                val desiredFieldHeight = if (isTextAreaExpanded) {
                    if (viewportDp > 0.dp) maxOf(320.dp, (viewportDp - 72.dp).coerceAtLeast(220.dp)) else 360.dp
                } else {
                    210.dp
                }
                val fieldHeight = if (scrollViewportPx == 0) {
                    desiredFieldHeight
                } else {
                    desiredFieldHeight.coerceAtMost((viewportDp - 64.dp).coerceAtLeast(150.dp))
                }
                var mcqFocused by remember { mutableStateOf(false) }

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
                        label = {
                            val lbl = when (inputContentFormat) {
                                "JSON Format" -> if (isTextAreaExpanded) "JSON Array / Paper Object (Expanded)" else "JSON Format Input"
                                "Markdown Table / CSV" -> if (isTextAreaExpanded) "Table / CSV Content (Expanded)" else "Table / CSV Format Input"
                                else -> if (isTextAreaExpanded) "MCQ Practice Text (Expanded View)" else "MCQ Practice Text"
                            }
                            Text(lbl)
                        },
                        placeholder = {
                            val ph = when (inputContentFormat) {
                                "JSON Format" -> "[\n  {\n    \"num\": 1,\n    \"question\": \"Question stem here...\",\n    \"options\": {\n      \"a\": \"Option A\",\n      \"b\": \"Option B\",\n      \"c\": \"Option C\",\n      \"d\": \"Option D\"\n    },\n    \"correctAnswer\": \"A\",\n    \"explanation\": \"Detailed explanation here...\"\n  }\n]"
                                "Markdown Table / CSV" -> "| # | Question | Option A | Option B | Option C | Option D | Answer | Explanation |\n| 1 | Question stem... | Opt A | Opt B | Opt C | Opt D | A | Detailed explanation... |"
                                else -> "1. Question text here...\n(A) Option 1\n(B) Option 2\n(C) Option 3\n(D) Option 4\n\nAnswer Key\n1: A\n\nDetailed Explanations\n1. (A) Reason..."
                            }
                            Text(
                                ph,
                                color = colors.muted.copy(alpha = 0.6f),
                                fontSize = 11.5.sp
                            )
                        },
                        colors = fieldColors,
                        minLines = 1,
                        maxLines = Int.MAX_VALUE,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(textFieldNestedScroll)
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

            Spacer(modifier = Modifier.height(16.dp))
        } else {
            // AI Prompt Generator Tab Content
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

                Spacer(modifier = Modifier.height(12.dp))

                // Saved Question Presets Bar (Shared across AI Prompt Studio & Create Question Set)
                val sheetCurrentConfig = currentSheetQuestionConfig()
                val sheetActivePreset = promptPresets.firstOrNull {
                    it.id == localActiveQuestionPresetId && it.mode == PromptPresetMode.QUESTION_SET
                }
                val sheetIsModified = sheetActivePreset != null && sheetActivePreset.questionConfig?.let { saved ->
                    saved.copy(
                        optionCount = sheetCurrentConfig.optionCount,
                        promptPhasingStyle = sheetCurrentConfig.promptPhasingStyle,
                        targetOutputFormat = sheetCurrentConfig.targetOutputFormat,
                        promptDensity = sheetCurrentConfig.promptDensity
                    ).normalized() != sheetCurrentConfig
                } == true
                val sheetMatchingPreset = promptPresets.firstOrNull { preset ->
                    preset.mode == PromptPresetMode.QUESTION_SET && preset.questionConfig?.let { saved ->
                        saved.copy(
                            optionCount = sheetCurrentConfig.optionCount,
                            promptPhasingStyle = sheetCurrentConfig.promptPhasingStyle,
                            targetOutputFormat = sheetCurrentConfig.targetOutputFormat,
                            promptDensity = sheetCurrentConfig.promptDensity
                        ).normalized() == sheetCurrentConfig
                    } == true
                }
                PromptPresetBar(
                    mode = PromptPresetMode.QUESTION_SET,
                    presets = promptPresets,
                    activePreset = sheetActivePreset,
                    isModified = sheetIsModified,
                    suggestedPresetName = sheetCurrentConfig.suggestName(),
                    matchingConfigPreset = sheetMatchingPreset,
                    canUndoClear = clearedSheetPromptUndo != null,
                    onSelectPreset = { preset ->
                        preset.questionConfig?.let { applySheetQuestionConfig(it, preserveTopic = true) }
                        localActiveQuestionPresetId = preset.id
                        clearedSheetPromptUndo = null
                        onSelectQuestionPreset(preset)
                        Toast.makeText(context, "Loaded preset \"${preset.name}\"", Toast.LENGTH_SHORT).show()
                    },
                    onSaveNewPreset = { name ->
                        onSaveNewQuestionPreset(name, sheetCurrentConfig)
                        Toast.makeText(context, "Saved preset \"$name\"", Toast.LENGTH_SHORT).show()
                    },
                    onUpdateExistingPreset = { presetId, newName ->
                        val existing = promptPresets.firstOrNull { it.id == presetId }?.questionConfig
                        val merged = if (existing != null) {
                            sheetCurrentConfig.copy(
                                optionCount = existing.optionCount,
                                promptPhasingStyle = existing.promptPhasingStyle,
                                targetOutputFormat = existing.targetOutputFormat,
                                promptDensity = existing.promptDensity
                            )
                        } else sheetCurrentConfig
                        onUpdateQuestionPreset(presetId, newName, merged)
                        Toast.makeText(context, "Preset updated", Toast.LENGTH_SHORT).show()
                    },
                    onRenamePreset = { presetId, newName ->
                        onRenamePromptPreset(presetId, newName)
                        Toast.makeText(context, "Renamed to \"$newName\"", Toast.LENGTH_SHORT).show()
                    },
                    onDuplicatePreset = { presetId ->
                        onDuplicatePromptPreset(presetId)
                        Toast.makeText(context, "Preset duplicated", Toast.LENGTH_SHORT).show()
                    },
                    onTogglePinPreset = onTogglePinPromptPreset,
                    onDeletePreset = { presetId ->
                        if (localActiveQuestionPresetId == presetId) localActiveQuestionPresetId = null
                        onDeletePromptPreset(presetId)
                        Toast.makeText(context, "Preset deleted", Toast.LENGTH_SHORT).show()
                    },
                    onClearForm = {
                        clearedSheetPromptUndo = Triple(sheetCurrentConfig, promptTopic, localActiveQuestionPresetId)
                        applySheetQuestionConfig(QuestionPromptConfig(), preserveTopic = false)
                        localActiveQuestionPresetId = null
                        onClearQuestionPreset()
                    },
                    onUndoClear = {
                        clearedSheetPromptUndo?.let { (cfg, prevTopic, prevId) ->
                            applySheetQuestionConfig(cfg, preserveTopic = false)
                            promptTopic = prevTopic
                            localActiveQuestionPresetId = prevId
                            clearedSheetPromptUndo = null
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

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

                        // [DEPRECATED 2026-11-25] Reason: Replaced static examPresets & quickSubjects strips with on-demand UserFolderDropdownMenu using user folders (Parent > Child > Sub-Child > L4+). Restore if: Static preset strips are explicitly requested again.
                        /*
                        val examPresets = listOf(
                            "UPSC Civil Services", "State PCS", "SSC CGL / Banking", "Railways / RRB",
                            "NEET / Biology", "JEE / Physics", "GATE / Engineering", "USMLE / Clinical",
                            "BAR / Law Exam", "SAT / ACT", "School Board"
                        )
                        val quickSubjects = listOf(
                            "General Science", "Modern Indian History", "Polity & Governance",
                            "Geography & Climate", "Quantitative Aptitude", "Clinical Medicine",
                            "Physics & Mechanics", "English Comprehension"
                        )
                        */

                        var isPromptExamDropdownOpen by remember { mutableStateOf(false) }
                        var isPromptSubjectDropdownOpen by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = promptExam,
                                onValueChange = { promptExam = it },
                                label = { Text("Exam", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                placeholder = { Text("e.g., GRE, SAT, Bar Exam, University Finals", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    if (folders.isNotEmpty()) {
                                        IconButton(
                                            onClick = { isPromptExamDropdownOpen = !isPromptExamDropdownOpen },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("sheet_prompt_exam_dropdown_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Choose Exam from User Folders",
                                                tint = colors.muted
                                            )
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth().testTag("prompt_field_exam")
                            )

                            UserFolderDropdownMenu(
                                expanded = isPromptExamDropdownOpen && folders.isNotEmpty(),
                                query = promptExam,
                                folders = folders,
                                onDismissRequest = { isPromptExamDropdownOpen = false },
                                onSelectEntry = { entry ->
                                    val segs = entry.fullPath.split(">").map { it.trim() }.filter { it.isNotEmpty() }
                                    promptExam = segs.firstOrNull() ?: entry.leafName
                                    if (segs.size > 1) {
                                        promptSubject = segs.drop(1).joinToString(" > ")
                                    }
                                    isPromptExamDropdownOpen = false
                                }
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = promptSubject,
                                onValueChange = { newSub ->
                                    promptSubject = if (newSub.contains("//")) newSub else newSub.replace("/", " > ")
                                },
                                label = { Text("Subject / Folder Path", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                placeholder = { Text("e.g., Organic Chemistry or Parent > Child > Sub-Child", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    if (folders.isNotEmpty()) {
                                        IconButton(
                                            onClick = { isPromptSubjectDropdownOpen = !isPromptSubjectDropdownOpen },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("sheet_prompt_subject_dropdown_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Choose Subject from User Folders",
                                                tint = colors.muted
                                            )
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth().testTag("prompt_field_subject")
                            )

                            UserFolderDropdownMenu(
                                expanded = isPromptSubjectDropdownOpen && folders.isNotEmpty(),
                                query = promptSubject,
                                folders = folders,
                                onDismissRequest = { isPromptSubjectDropdownOpen = false },
                                onSelectEntry = { entry ->
                                    promptSubject = entry.fullPath
                                    isPromptSubjectDropdownOpen = false
                                }
                            )
                        }

                        OutlinedTextField(
                            value = promptTopic,
                            onValueChange = { promptTopic = it },
                            label = { Text("Topic", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            placeholder = { Text("e.g., Thermodynamics, Constitutional Law, Calculus", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
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

                            // Slider strictly aligned to multiples of 5 from 5 to 200
                            Slider(
                                value = parsedCount.toFloat().coerceIn(5f, 200f),
                                onValueChange = { newVal ->
                                    val snapped = kotlin.math.round(newVal / 5f) * 5f
                                    val clamped = snapped.toInt().coerceIn(5, 200)
                                    promptMcqCount = clamped.toString()
                                },
                                valueRange = 5f..200f,
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
                                com.example.util.PaperLanguageDetector.PRESET_LANGUAGES.forEach { lang ->
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
                                if (promptCustomExplanationLanguage.isNotBlank()) "Bilingual (Eng & ${promptCustomExplanationLanguage.trim()})"
                                else "Bilingual (Dual)"
                            } else {
                                "Bilingual (Eng & $activeQLang)"
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Explanation Language",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Text(
                                    "Active: $effectivePromptExplanationLanguage",
                                    fontSize = 10.5.sp,
                                    color = colors.teal,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val explanationOptions = listOf(
                                    "Same as Question" to "Same as Question",
                                    "English" to "English",
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

                            if (promptExplanationChoice == "Custom" || (promptExplanationChoice == "Bilingual" && activeQLang.equals("English", ignoreCase = true))) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = promptCustomExplanationLanguage,
                                    onValueChange = { promptCustomExplanationLanguage = it },
                                    placeholder = {
                                        Text(
                                            if (promptExplanationChoice == "Bilingual") "Second language (e.g. Spanish, Hindi, French)..."
                                            else "Language",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
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
                                Text("Question Format & Pattern", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.ink, softWrap = true)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(promptQuestionType, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.teal, softWrap = true, modifier = Modifier.weight(1f, fill = false))
                            }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "Mixed Formats",
                                    "Conceptual & Theory",
                                    "Numerical & Calculation",
                                    "Assertion-Reason & Statement-Based",
                                    "Case Study & Passage-Based",
                                    "Multiple-Select (MSQ)",
                                    "Mixed Objective (MCQ + MSQ)"
                                ).forEach { qType ->
                                    val isSelected = promptQuestionType == qType
                                    Surface(
                                        modifier = Modifier
                                            .heightIn(min = 36.dp)
                                            .clickable { promptQuestionType = qType },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) colors.teal else colors.surfaceVariant,
                                        border = BorderStroke(0.5.dp, colors.border)
                                    ) {
                                        Text(
                                            text = qType,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) androidx.compose.ui.graphics.Color.White else colors.inkBold,
                                            softWrap = true,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
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
                            color = if (promptEnableQuantityCorrectionRule) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(0.8.dp, if (promptEnableQuantityCorrectionRule) colors.teal else colors.border),
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
            }
        }
                Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
