package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.AppFontScaleMode
import com.example.data.PaperEntity
import com.example.data.StudyNoteEntity
import com.example.model.FolderItem
import com.example.model.VoiceAnalysisUiState
import com.example.model.VoicePracticeMode
import com.example.model.VoicePromptItem
import com.example.model.VoiceSourceFilter
import com.example.ui.components.FontSizeSelectorDialog
import com.example.ui.components.LatexText
import com.example.ui.theme.ExamTheme
import com.example.util.FolderPathHelper
import com.example.util.VoiceFluencyEngine
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoicePracticeScreen(
    folders: List<FolderItem>,
    studyNotes: List<StudyNoteEntity>,
    papers: List<PaperEntity>,
    initialFolderId: String,
    fontScaleMode: AppFontScaleMode,
    onSelectFontScaleMode: (AppFontScaleMode) -> Unit,
    onLogStudySeconds: (Long) -> Unit = {},
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val view = LocalView.current

    // [DEPRECATED 2026-11-26] Reason: Sound recording/playback (VoiceCaptureController) and online Gemini AI analysis (FirebaseVoiceAiAnalyzer) were excluded to make Speak to Test 100% offline, sound-free, and non-AI. Restore if: re-enabling WAV recording, audio playback, or cloud Gemini vocal-filler analysis.
    // val captureController = rememberVoiceCaptureController()
    // val aiAnalyzer = remember { FirebaseVoiceAiAnalyzer() }

    var selectedFolderId by remember(initialFolderId) {
        mutableStateOf(
            if (initialFolderId.isNotBlank() && (initialFolderId == "all" || folders.any { it.id == initialFolderId })) {
                initialFolderId
            } else {
                "all"
            }
        )
    }
    var sourceFilter by remember { mutableStateOf(VoiceSourceFilter.ALL) }
    var includeFormulas by remember { mutableStateOf(true) }
    var practiceMode by remember { mutableStateOf(VoicePracticeMode.READ_ALOUD) }
    var promptIndex by remember { mutableIntStateOf(0) }
    var shuffleNonce by remember { mutableIntStateOf(0) }
    var revealReference by remember { mutableStateOf(false) }
    var showFolderDropdown by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var micPermissionDenied by remember { mutableStateOf(false) }
    var analysisState by remember { mutableStateOf<VoiceAnalysisUiState>(VoiceAnalysisUiState.Idle) }

    // 100% offline on-device Speech-to-Text + manual/keyboard dictation fallback
    var isOfflineSttListening by remember { mutableStateOf(false) }
    var offlineLiveTranscript by remember { mutableStateOf("") }
    var offlineSttStartMs by remember { mutableLongStateOf(0L) }
    var elapsedListeningMs by remember { mutableLongStateOf(0L) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualSpokenText by remember { mutableStateOf("") }

    val sttSupported = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    val speechRecognizer = remember(context) {
        if (sttSupported) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
        }
    }

    DisposableEffect(view, isOfflineSttListening) {
        view.keepScreenOn = isOfflineSttListening
        onDispose {
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(isOfflineSttListening) {
        if (isOfflineSttListening) {
            while (isOfflineSttListening) {
                elapsedListeningMs = (System.currentTimeMillis() - offlineSttStartMs).coerceAtLeast(0L)
                delay(200L)
            }
        }
    }

    val folderSuggestions = remember(folders) {
        FolderPathHelper.buildUserFolderSuggestions(folders)
    }
    val folderIdByPath = remember(folders) {
        folders.associateBy { folder ->
            FolderPathHelper.getFolderHierarchyPath(folder.id, folders).ifBlank { folder.name.trim() }
        }
    }

    val folderResult = remember(selectedFolderId, folders, studyNotes, papers, sourceFilter, includeFormulas) {
        VoiceFluencyEngine.buildPromptsForFolder(
            selectedFolderId = selectedFolderId,
            folders = folders,
            studyNotes = studyNotes,
            papers = papers,
            sourceFilter = sourceFilter,
            includeFormulas = includeFormulas
        )
    }

    val orderedPrompts = remember(folderResult.prompts, shuffleNonce) {
        if (shuffleNonce == 0) folderResult.prompts else folderResult.prompts.shuffled()
    }

    LaunchedEffect(orderedPrompts.size, selectedFolderId, sourceFilter, includeFormulas) {
        promptIndex = 0
        revealReference = false
    }

    val currentPrompt: VoicePromptItem? = orderedPrompts.getOrNull(
        promptIndex.coerceIn(0, (orderedPrompts.size - 1).coerceAtLeast(0))
    )

    fun evaluateSpokenText(transcript: String, durationMs: Long, engineLabel: String) {
        val prompt = currentPrompt ?: return
        val cleaned = transcript.trim()
        if (cleaned.isBlank()) {
            analysisState = VoiceAnalysisUiState.Error(
                "No spoken words were detected. Try speaking clearly or type your text below to compare offline."
            )
            return
        }
        if (durationMs >= 1000L) {
            onLogStudySeconds((durationMs / 1000L).coerceAtLeast(1L))
        }
        analysisState = VoiceAnalysisUiState.Success(
            VoiceFluencyEngine.evaluateOfflineSpeechCheck(
                spokenTranscript = cleaned,
                prompt = prompt,
                durationMs = durationMs,
                mode = practiceMode,
                engineLabel = engineLabel
            )
        )
    }

    LaunchedEffect(currentPrompt?.id, selectedFolderId, sourceFilter, includeFormulas) {
        if (isOfflineSttListening) {
            runCatching { speechRecognizer?.cancel() }
            isOfflineSttListening = false
        }
        offlineLiveTranscript = ""
        elapsedListeningMs = 0L
        manualSpokenText = ""
        if (analysisState != VoiceAnalysisUiState.Idle) {
            analysisState = VoiceAnalysisUiState.Idle
        }
    }

    val selectedFolderLabel = remember(selectedFolderId, folders) {
        if (selectedFolderId == "all") {
            "All Subject Folders"
        } else {
            FolderPathHelper.getFolderHierarchyPath(selectedFolderId, folders)
                .ifBlank { folders.firstOrNull { it.id == selectedFolderId }?.name ?: "Selected Folder" }
        }
    }

    fun stopOfflineStt(evaluatePartialIfAny: Boolean = true) {
        if (!isOfflineSttListening) return
        runCatching { speechRecognizer?.stopListening() }
        isOfflineSttListening = false
        val dur = (System.currentTimeMillis() - offlineSttStartMs).coerceAtLeast(1000L)
        elapsedListeningMs = dur
        if (evaluatePartialIfAny && offlineLiveTranscript.isNotBlank()) {
            manualSpokenText = offlineLiveTranscript
            evaluateSpokenText(
                transcript = offlineLiveTranscript,
                durationMs = dur,
                engineLabel = "Offline Speech-to-Text Match"
            )
        }
    }

    fun startOfflineSpeechCheck() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            showManualInput = true
            analysisState = VoiceAnalysisUiState.Error(
                "On-device Speech Recognizer is unavailable on this device. Use the offline text/keyboard-mic box below instead."
            )
            return
        }
        offlineLiveTranscript = ""
        elapsedListeningMs = 0L
        offlineSttStartMs = System.currentTimeMillis()
        isOfflineSttListening = true
        analysisState = VoiceAnalysisUiState.Recording

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                isOfflineSttListening = false
            }

            override fun onError(error: Int) {
                isOfflineSttListening = false
                val dur = (System.currentTimeMillis() - offlineSttStartMs).coerceAtLeast(1000L)
                elapsedListeningMs = dur
                if (offlineLiveTranscript.isNotBlank()) {
                    manualSpokenText = offlineLiveTranscript
                    evaluateSpokenText(
                        transcript = offlineLiveTranscript,
                        durationMs = dur,
                        engineLabel = "Offline Speech-to-Text Match"
                    )
                } else {
                    showManualInput = true
                    analysisState = VoiceAnalysisUiState.Error(
                        "Offline Speech-to-Text did not capture words (code $error). Check your device's offline language pack or use Type / Dictate Check below."
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                isOfflineSttListening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                val finalTranscript = text.ifBlank { offlineLiveTranscript }.trim()
                val dur = (System.currentTimeMillis() - offlineSttStartMs).coerceAtLeast(1000L)
                elapsedListeningMs = dur
                manualSpokenText = finalTranscript
                evaluateSpokenText(
                    transcript = finalTranscript,
                    durationMs = dur,
                    engineLabel = "Offline Speech-to-Text Match"
                )
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) {
                    offlineLiveTranscript = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        runCatching { recognizer.startListening(intent) }
            .onFailure {
                isOfflineSttListening = false
                showManualInput = true
                analysisState = VoiceAnalysisUiState.Error(
                    "Could not start on-device Speech-to-Text. Use the offline text/keyboard-mic box below."
                )
            }
    }

    var pendingActionAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        micPermissionDenied = !granted
        if (granted) {
            pendingActionAfterPermission?.invoke()
        } else {
            showManualInput = true
        }
        pendingActionAfterPermission = null
    }

    fun runWithMicPermission(action: () -> Unit) {
        if (hasMicPermission) {
            action()
        } else {
            pendingActionAfterPermission = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler {
        if (showFontDialog) {
            showFontDialog = false
        } else {
            stopOfflineStt(evaluatePartialIfAny = false)
            onDismiss()
        }
    }

    if (showFontDialog) {
        FontSizeSelectorDialog(
            currentMode = fontScaleMode,
            onModeSelected = onSelectFontScaleMode,
            onDismissRequest = { showFontDialog = false }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("voice_practice_screen"),
        color = colors.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar (fully responsive to system font size)
            Surface(color = colors.surface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            stopOfflineStt(evaluatePartialIfAny = false)
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_practice_back_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Speak to Test",
                            tint = colors.inkBold
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Speak to Test",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.inkBold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.tealLight
                            ) {
                                Text(
                                    text = "Offline Check",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.tealDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = selectedFolderLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { showFontDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_practice_font_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Adjust Text Size",
                            tint = colors.teal
                        )
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val isWide = maxWidth >= 720.dp

                val folderAndPromptPane: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Parent > Child > Sub-Child Folder & Filters Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(0.9.dp, colors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "SUBJECT FOLDER (PARENT & CHILD)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkMuted
                                )

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        onClick = { showFolderDropdown = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = colors.surfaceVariant,
                                        border = BorderStroke(1.dp, colors.border),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("voice_folder_picker_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                tint = colors.teal,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = selectedFolderLabel,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.inkBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                Icons.Default.ExpandMore,
                                                contentDescription = "Choose Folder",
                                                tint = colors.inkMuted
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showFolderDropdown,
                                        onDismissRequest = { showFolderDropdown = false },
                                        modifier = Modifier.widthIn(min = 240.dp, max = 380.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "All Subject Folders",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (selectedFolderId == "all") FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                selectedFolderId = "all"
                                                showFolderDropdown = false
                                            }
                                        )
                                        folderSuggestions.forEach { suggestion ->
                                            val resolvedFolder = folderIdByPath[suggestion.fullPath]
                                            if (resolvedFolder != null) {
                                                val indentPrefix = "  ".repeat((suggestion.depth - 1).coerceAtLeast(0))
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(
                                                                text = "$indentPrefix${suggestion.leafName}",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = if (selectedFolderId == resolvedFolder.id) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                            Text(
                                                                text = "${suggestion.levelLabel} • ${suggestion.fullPath}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = colors.inkMuted
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedFolderId = resolvedFolder.id
                                                        showFolderDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Source & Formula Filter Chips
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    VoiceSourceFilter.entries.forEach { filter ->
                                        FilterChip(
                                            selected = sourceFilter == filter,
                                            onClick = { sourceFilter = filter },
                                            label = { Text(filter.label, style = MaterialTheme.typography.labelLarge) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = colors.tealLight,
                                                selectedLabelColor = colors.tealDark
                                            ),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        )
                                    }
                                    FilterChip(
                                        selected = includeFormulas,
                                        onClick = { includeFormulas = !includeFormulas },
                                        label = { Text("Formulas & Expressions", style = MaterialTheme.typography.labelLarge) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colors.amberLight,
                                            selectedLabelColor = colors.inkBold
                                        ),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("voice_include_formulas_chip")
                                    )
                                }

                                Text(
                                    text = buildString {
                                        append("${orderedPrompts.size} subject prompts ready")
                                        if (includeFormulas) {
                                            append(" • Formulas & expressions included")
                                        } else if (folderResult.skippedFormulaItems > 0) {
                                            append(" • ${folderResult.skippedFormulaItems} formula-heavy items skipped")
                                        } else {
                                            append(" • Formula filter active")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.inkMuted
                                )
                            }
                        }

                        // Practice Mode Selector
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            VoicePracticeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = practiceMode == mode,
                                    onClick = { practiceMode = mode },
                                    label = { Text(mode.label, style = MaterialTheme.typography.labelLarge) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.navyBanner,
                                        selectedLabelColor = colors.navyBannerText
                                    ),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }

                        // Active Subject Prompt Card
                        if (currentPrompt == null) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(0.9.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = colors.teal,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "No spoken prompts in this folder yet",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Add Study Notes or MCQs to this folder (or select a Parent folder or All Subject Folders) to practice speaking and checking against your material.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.inkMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("voice_prompt_card")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    colors.tealLight.copy(alpha = if (colors.isDark) 0.22f else 0.42f),
                                                    colors.surface
                                                )
                                            )
                                        )
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = colors.tealLight) {
                                            Text(
                                                text = "PROMPT ${promptIndex + 1} OF ${orderedPrompts.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.tealDark,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Text(
                                            text = currentPrompt.sourceLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.inkMuted
                                        )
                                    }

                                    LatexText(
                                        text = currentPrompt.promptQuestion,
                                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        isBold = true
                                    )

                                    if (practiceMode == VoicePracticeMode.READ_ALOUD) {
                                        HorizontalDivider(color = colors.borderLight)
                                        VoicePromptFormulaAndOptionsBlock(prompt = currentPrompt)
                                        LatexText(
                                            text = currentPrompt.referenceSummary,
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                            color = colors.ink
                                        )
                                    } else if (practiceMode == VoicePracticeMode.EXPLAIN_FROM_MEMORY) {
                                        Surface(
                                            onClick = { revealReference = !revealReference },
                                            shape = RoundedCornerShape(10.dp),
                                            color = colors.surfaceVariant,
                                            border = BorderStroke(0.8.dp, colors.border),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (revealReference) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = colors.teal,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = if (revealReference) "Hide Reference Notes" else "Peek Reference Answer",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.inkBold
                                                )
                                            }
                                        }
                                        AnimatedVisibility(visible = revealReference) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                VoicePromptFormulaAndOptionsBlock(prompt = currentPrompt)
                                                LatexText(
                                                    text = currentPrompt.referenceSummary,
                                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                                    color = colors.ink
                                                )
                                            }
                                        }
                                    }

                                    // Prompt Navigation Strip (wraps safely on large system fonts)
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                if (promptIndex > 0) {
                                                    promptIndex--
                                                    revealReference = false
                                                }
                                            },
                                            enabled = promptIndex > 0,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Prev", style = MaterialTheme.typography.labelLarge)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                shuffleNonce++
                                                promptIndex = 0
                                                revealReference = false
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Prompts", modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Shuffle", style = MaterialTheme.typography.labelLarge)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                if (promptIndex < orderedPrompts.lastIndex) {
                                                    promptIndex++
                                                    revealReference = false
                                                }
                                            },
                                            enabled = promptIndex < orderedPrompts.lastIndex,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Next", style = MaterialTheme.typography.labelLarge)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val checkAndResultsPane: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Offline Speech-to-Text & Manual Comparison Card (Sound-free, No AI)
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(
                                1.dp,
                                if (isOfflineSttListening) colors.red else colors.border
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("voice_recorder_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OfflinePin,
                                            contentDescription = null,
                                            tint = if (isOfflineSttListening) colors.red else colors.teal,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (isOfflineSttListening) "Listening Offline..." else "On-Device Speech Check",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOfflineSttListening) colors.red else colors.inkBold
                                        )
                                    }
                                    Text(
                                        text = formatDurationMs(elapsedListeningMs),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.tealDark
                                    )
                                }

                                if (isOfflineSttListening) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = colors.surfaceVariant,
                                        border = BorderStroke(0.8.dp, colors.border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = offlineLiveTranscript.ifBlank { "Speak the displayed text clearly..." },
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (offlineLiveTranscript.isBlank()) colors.inkMuted else colors.inkBold,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }

                                if (micPermissionDenied) {
                                    Text(
                                        text = "Microphone permission was denied. You can grant access anytime or use the offline text input below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.red,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                val micScale by animateFloatAsState(
                                    targetValue = if (isOfflineSttListening) 1.06f else 1f,
                                    animationSpec = spring(),
                                    label = "stt_mic_pulse"
                                )
                                Button(
                                    onClick = {
                                        if (isOfflineSttListening) {
                                            stopOfflineStt(evaluatePartialIfAny = true)
                                        } else {
                                            runWithMicPermission { startOfflineSpeechCheck() }
                                        }
                                    },
                                    enabled = currentPrompt != null && sttSupported,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isOfflineSttListening) colors.red else colors.teal,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .size((72 * micScale).dp)
                                        .testTag("voice_record_toggle_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isOfflineSttListening) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = if (isOfflineSttListening) "Stop Listening" else "Speak Now",
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Text(
                                    text = when {
                                        !sttSupported -> "Device Speech Recognizer unavailable — use Type / Dictate Check below."
                                        isOfflineSttListening -> "Tap Stop when finished to check matches & mistakes"
                                        else -> "Tap Mic to speak aloud (100% offline, no sound saved)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.inkMuted,
                                    textAlign = TextAlign.Center
                                )

                                // Secondary Offline Actions
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showManualInput = !showManualInput },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("voice_manual_toggle_btn")
                                    ) {
                                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (showManualInput) "Hide Text Input" else "Type / Dictate Check",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }

                                    if (analysisState != VoiceAnalysisUiState.Idle) {
                                        OutlinedButton(
                                            onClick = {
                                                offlineLiveTranscript = ""
                                                manualSpokenText = ""
                                                elapsedListeningMs = 0L
                                                analysisState = VoiceAnalysisUiState.Idle
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Reset", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = showManualInput) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = manualSpokenText,
                                            onValueChange = { manualSpokenText = it },
                                            label = {
                                                Text(
                                                    "Spoken / Dictated Text",
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            },
                                            placeholder = {
                                                Text(
                                                    "Use keyboard mic or type what you recited...",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            minLines = 3,
                                            maxLines = 6,
                                            textStyle = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("voice_manual_input")
                                        )
                                        Button(
                                            onClick = {
                                                evaluateSpokenText(
                                                    transcript = manualSpokenText,
                                                    durationMs = elapsedListeningMs,
                                                    engineLabel = "Offline Text Comparison"
                                                )
                                            },
                                            enabled = currentPrompt != null && manualSpokenText.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.navyBanner,
                                                contentColor = colors.navyBannerText
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("voice_manual_check_btn")
                                        ) {
                                            Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Compare Against Shown Text",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        VoicePracticeResultsSection(
                            uiState = analysisState,
                            onRetryAnalyze = {
                                val retrySource = manualSpokenText.ifBlank { offlineLiveTranscript }
                                if (retrySource.isNotBlank()) {
                                    evaluateSpokenText(
                                        transcript = retrySource,
                                        durationMs = elapsedListeningMs,
                                        engineLabel = "Offline Text Comparison"
                                    )
                                } else if (sttSupported) {
                                    runWithMicPermission { startOfflineSpeechCheck() }
                                } else {
                                    showManualInput = true
                                }
                            }
                        )
                    }
                }

                if (isWide) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            folderAndPromptPane()
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            checkAndResultsPane()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 640.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            folderAndPromptPane()
                            checkAndResultsPane()
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoicePromptFormulaAndOptionsBlock(prompt: VoicePromptItem) {
    val colors = ExamTheme.colors
    if (prompt.formulaExpressions.isEmpty() && prompt.optionExpressions.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (prompt.formulaExpressions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.amberLight.copy(alpha = if (colors.isDark) 0.22f else 0.55f),
                border = BorderStroke(0.9.dp, colors.amber.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_formula_box")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "FORMULAS & EXPRESSIONS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.amber
                    )
                    prompt.formulaExpressions.forEach { expr ->
                        LatexText(
                            text = expr,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            color = colors.inkBold,
                            fontWeight = FontWeight.SemiBold,
                            isBold = true
                        )
                    }
                }
            }
        }

        if (prompt.optionExpressions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceVariant,
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_options_box")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "OPTIONS & EXPRESSIONS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted
                    )
                    prompt.optionExpressions.forEach { opt ->
                        LatexText(
                            text = opt,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            color = colors.ink
                        )
                    }
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
