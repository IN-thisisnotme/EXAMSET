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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.model.VoiceAnalysisResult
import com.example.model.VoiceAnalysisUiState
import com.example.model.VoicePracticeMode
import com.example.model.VoicePromptItem
import com.example.model.VoiceSourceFilter
import com.example.ui.components.FontSizeSelectorDialog
import com.example.ui.theme.ExamTheme
import com.example.util.FirebaseVoiceAiAnalyzer
import com.example.util.FolderPathHelper
import com.example.util.VoiceFluencyEngine
import com.example.util.rememberVoiceCaptureController
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()

    val captureController = rememberVoiceCaptureController()
    val aiAnalyzer = remember { FirebaseVoiceAiAnalyzer() }

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
    var practiceMode by remember { mutableStateOf(VoicePracticeMode.EXPLAIN_FROM_MEMORY) }
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

    // Optional offline SpeechRecognizer quick check (never runs concurrently with AudioRecord)
    var isOfflineSttListening by remember { mutableStateOf(false) }
    var offlineLiveTranscript by remember { mutableStateOf("") }
    var offlineSttStartMs by remember { mutableStateOf(0L) }
    val sttSupported = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }

    val speechRecognizer = remember(context) {
        if (sttSupported) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else null
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
        }
    }

    DisposableEffect(view, captureController.isRecording, isOfflineSttListening) {
        view.keepScreenOn = captureController.isRecording || isOfflineSttListening
        onDispose {
            view.keepScreenOn = false
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

    val folderResult = remember(selectedFolderId, folders, studyNotes, papers, sourceFilter) {
        VoiceFluencyEngine.buildPromptsForFolder(
            selectedFolderId = selectedFolderId,
            folders = folders,
            studyNotes = studyNotes,
            papers = papers,
            sourceFilter = sourceFilter
        )
    }

    val orderedPrompts = remember(folderResult.prompts, shuffleNonce) {
        if (shuffleNonce == 0) folderResult.prompts else folderResult.prompts.shuffled()
    }

    LaunchedEffect(orderedPrompts.size, selectedFolderId, sourceFilter) {
        promptIndex = 0
        revealReference = false
    }

    val currentPrompt: VoicePromptItem? = orderedPrompts.getOrNull(
        promptIndex.coerceIn(0, (orderedPrompts.size - 1).coerceAtLeast(0))
    )

    LaunchedEffect(currentPrompt?.id, selectedFolderId, sourceFilter) {
        if (isOfflineSttListening) {
            runCatching { speechRecognizer?.stopListening() }
            isOfflineSttListening = false
        }
        offlineLiveTranscript = ""
        if (captureController.isRecording || captureController.hasRecording()) {
            captureController.clearRecording()
        }
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

    fun stopOfflineStt() {
        if (isOfflineSttListening) {
            runCatching { speechRecognizer?.stopListening() }
            isOfflineSttListening = false
        }
    }

    fun startRecordingAttempt() {
        stopOfflineStt()
        offlineLiveTranscript = ""
        if (captureController.startRecording()) {
            analysisState = VoiceAnalysisUiState.Recording
        } else {
            analysisState = VoiceAnalysisUiState.Error(
                captureController.lastError ?: "Could not access microphone."
            )
        }
    }

    fun stopRecordingAttempt() {
        val file = captureController.stopRecording()
        val duration = captureController.elapsedMs
        if (duration >= 1000L) {
            onLogStudySeconds((duration / 1000L).coerceAtLeast(1L))
        }
        analysisState = if (file != null) {
            VoiceAnalysisUiState.RecordedReady(duration)
        } else {
            VoiceAnalysisUiState.Error("Recording was too short. Please speak for at least 1 second.")
        }
    }

    fun runAiAudioAnalysis() {
        val prompt = currentPrompt ?: return
        val audioBytes = captureController.readRecordedAudioBytes()
        if (audioBytes == null || audioBytes.size <= 44) {
            analysisState = VoiceAnalysisUiState.Error("No recorded audio found. Tap Record and speak first.")
            return
        }
        val durationMs = captureController.elapsedMs.coerceAtLeast(1000L)
        val rmsSnapshot = captureController.rmsHistory.toList()
        analysisState = VoiceAnalysisUiState.Analyzing

        scope.launch {
            val outcome = aiAnalyzer.analyzeAudio(
                audioBytes = audioBytes,
                durationMs = durationMs,
                rmsSamples = rmsSnapshot,
                prompt = prompt,
                mode = practiceMode
            )
            analysisState = outcome.fold(
                onSuccess = { VoiceAnalysisUiState.Success(it) },
                onFailure = { err ->
                    VoiceAnalysisUiState.Error(
                        err.message ?: "Unable to analyze audio. You can still replay your recording or use Offline Quick Check."
                    )
                }
            )
        }
    }

    fun startOfflineQuickStt() {
        val prompt = currentPrompt ?: return
        val recognizer = speechRecognizer ?: return
        captureController.clearRecording()
        offlineLiveTranscript = ""
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
                if (offlineLiveTranscript.isNotBlank()) {
                    val dur = (System.currentTimeMillis() - offlineSttStartMs).coerceAtLeast(1000L)
                    val metrics = VoiceFluencyEngine.computeMetrics(
                        durationMs = dur,
                        rmsSamples = emptyList(),
                        transcript = offlineLiveTranscript,
                        referenceSummary = prompt.referenceSummary,
                        mode = practiceMode
                    )
                    analysisState = VoiceAnalysisUiState.Success(
                        VoiceAnalysisResult(
                            verbatimTranscript = offlineLiveTranscript,
                            annotatedSegments = VoiceFluencyEngine.tokenizeTranscriptForHighlight(offlineLiveTranscript),
                            metrics = metrics,
                            topicAccuracyPercent = metrics.keywordCoveragePercent,
                            strengths = listOf("Captured offline using device speech recognition."),
                            coachingTips = listOf("Note: Android's offline speech recognizer may skip short 'em'/'um' sounds—use Record + Analyze for verbatim filler detection."),
                            usedAiAudioAnalysis = false,
                            engineLabel = "Offline Device Speech-to-Text"
                        )
                    )
                } else {
                    analysisState = VoiceAnalysisUiState.Error(
                        "Offline speech recognizer returned no text (code $error). Try Record + Analyze instead."
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                isOfflineSttListening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                val finalTranscript = text.ifBlank { offlineLiveTranscript }.trim()
                val dur = (System.currentTimeMillis() - offlineSttStartMs).coerceAtLeast(1000L)
                if (dur >= 1000L) {
                    onLogStudySeconds((dur / 1000L).coerceAtLeast(1L))
                }
                val metrics = VoiceFluencyEngine.computeMetrics(
                    durationMs = dur,
                    rmsSamples = emptyList(),
                    transcript = finalTranscript,
                    referenceSummary = prompt.referenceSummary,
                    mode = practiceMode
                )
                analysisState = VoiceAnalysisUiState.Success(
                    VoiceAnalysisResult(
                        verbatimTranscript = finalTranscript,
                        annotatedSegments = VoiceFluencyEngine.tokenizeTranscriptForHighlight(finalTranscript),
                        metrics = metrics,
                        topicAccuracyPercent = metrics.keywordCoveragePercent,
                        strengths = listOf("Offline quick speech check completed."),
                        coachingTips = listOf("For full verbatim 'em', 'um', and pause tracking, use Record & Analyze."),
                        usedAiAudioAnalysis = false,
                        engineLabel = "Offline Device Speech-to-Text"
                    )
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        runCatching { recognizer.startListening(intent) }
            .onFailure {
                isOfflineSttListening = false
                analysisState = VoiceAnalysisUiState.Error("Offline Speech-to-Text could not start.")
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
            stopOfflineStt()
            captureController.shutdown()
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
            // Top Bar
            Surface(color = colors.surface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            stopOfflineStt()
                            captureController.shutdown()
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
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "Speak to Test",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.inkBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selectedFolderLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.tealDark,
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
                        // Parent > Child > Sub-Child Folder & Source Card
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
                                        modifier = Modifier.widthIn(min = 240.dp, max = 360.dp)
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

                                // Source Filter Chips (wraps safely at large font sizes)
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
                                }

                                Text(
                                    text = buildString {
                                        append("${orderedPrompts.size} subject prompts ready")
                                        if (folderResult.skippedFormulaItems > 0) {
                                            append(" • ${folderResult.skippedFormulaItems} formula-heavy items skipped")
                                        } else {
                                            append(" • Formulas excluded automatically")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.inkMuted
                                )
                            }
                        }

                        // Practice Mode Chips
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
                                        text = "Add Study Notes or MCQs to this folder (or select a Parent folder or All Subject Folders) to practice speaking from your material.",
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
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
                                            color = colors.inkMuted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                                            textAlign = TextAlign.End
                                        )
                                    }

                                    Text(
                                        text = currentPrompt.promptQuestion,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold
                                    )

                                    if (practiceMode == VoicePracticeMode.READ_ALOUD) {
                                        HorizontalDivider(color = colors.borderLight)
                                        Text(
                                            text = currentPrompt.referenceSummary,
                                            style = MaterialTheme.typography.bodyLarge,
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
                                            Text(
                                                text = currentPrompt.referenceSummary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.ink
                                            )
                                        }
                                    }

                                    // Prompt Navigation Strip
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
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
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
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
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Prompts", modifier = Modifier.size(18.dp))
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
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
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

                val recorderAndResultsPane: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Microphone Recorder & Live Waveform Card
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(
                                1.dp,
                                if (captureController.isRecording || isOfflineSttListening) colors.red else colors.border
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when {
                                            captureController.isRecording -> "Listening & Recording..."
                                            isOfflineSttListening -> "Offline Speech-to-Text..."
                                            captureController.hasRecording() -> "Recording Ready"
                                            else -> "Ready to Practice"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (captureController.isRecording || isOfflineSttListening) colors.red else colors.inkBold
                                    )
                                    Text(
                                        text = formatDurationMs(captureController.elapsedMs),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.tealDark
                                    )
                                }

                                // Live Waveform Canvas
                                LiveVoiceWaveformBar(
                                    samples = captureController.rmsHistory,
                                    liveLevel = captureController.liveRmsLevel,
                                    isRecording = captureController.isRecording,
                                    activeColor = if (captureController.isRecording) colors.red else colors.teal,
                                    trackColor = colors.surfaceVariant
                                )

                                if (isOfflineSttListening && offlineLiveTranscript.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = offlineLiveTranscript,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.inkBold,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }

                                if (micPermissionDenied) {
                                    Text(
                                        text = "Microphone permission was denied. Tap Record to allow access so you can test your speaking.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.red,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Main Record / Stop Button
                                val micScale by animateFloatAsState(
                                    targetValue = if (captureController.isRecording) 1.06f else 1f,
                                    animationSpec = spring(),
                                    label = "mic_pulse"
                                )
                                Button(
                                    onClick = {
                                        if (captureController.isRecording) {
                                            stopRecordingAttempt()
                                        } else if (isOfflineSttListening) {
                                            stopOfflineStt()
                                        } else {
                                            runWithMicPermission { startRecordingAttempt() }
                                        }
                                    },
                                    enabled = currentPrompt != null && analysisState != VoiceAnalysisUiState.Analyzing,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (captureController.isRecording || isOfflineSttListening) colors.red else colors.teal,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .size((72 * micScale).dp)
                                        .testTag("voice_record_toggle_btn")
                                ) {
                                    Icon(
                                        imageVector = if (captureController.isRecording || isOfflineSttListening) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = if (captureController.isRecording) "Stop Recording" else "Start Recording",
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Text(
                                    text = if (captureController.isRecording) {
                                        "Tap Stop when finished"
                                    } else {
                                        "Tap Mic to speak naturally (includes em, um & pauses)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.inkMuted,
                                    textAlign = TextAlign.Center
                                )

                                // Playback & Analysis Action Row
                                if (!captureController.isRecording && !isOfflineSttListening) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (captureController.hasRecording()) {
                                            OutlinedButton(
                                                onClick = { captureController.togglePlayback() },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("voice_playback_btn")
                                            ) {
                                                Icon(
                                                    imageVector = if (captureController.isPlayingBack) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (captureController.isPlayingBack) "Stop Audio" else "Play My Voice",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Button(
                                                onClick = { runAiAudioAnalysis() },
                                                enabled = currentPrompt != null && analysisState != VoiceAnalysisUiState.Analyzing,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = colors.navyBanner,
                                                    contentColor = colors.navyBannerText
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("voice_analyze_btn")
                                            ) {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Analyze em / um & Clarity",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    captureController.clearRecording()
                                                    analysisState = VoiceAnalysisUiState.Idle
                                                },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = "Clear Recording",
                                                    tint = colors.inkMuted
                                                )
                                            }
                                        }

                                        if (sttSupported && !captureController.hasRecording()) {
                                            OutlinedButton(
                                                onClick = { runWithMicPermission { startOfflineQuickStt() } },
                                                enabled = currentPrompt != null,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("voice_offline_stt_btn")
                                            ) {
                                                Text(
                                                    text = "Offline Speech-to-Text Check",
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Results Section
                        VoicePracticeResultsSection(
                            uiState = analysisState,
                            onRetryAnalyze = { runAiAudioAnalysis() }
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
                            recorderAndResultsPane()
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
                            recorderAndResultsPane()
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveVoiceWaveformBar(
    samples: List<Float>,
    liveLevel: Float,
    isRecording: Boolean,
    activeColor: Color,
    trackColor: Color
) {
    val barCount = 36
    val displaySamples = remember(samples.size, liveLevel) {
        if (samples.isEmpty()) {
            List(barCount) { 0.08f }
        } else {
            val tail = samples.takeLast(barCount)
            val pad = List((barCount - tail.size).coerceAtLeast(0)) { 0.08f }
            pad + tail.mapIndexed { idx, v ->
                val boosted = if (isRecording && idx == tail.lastIndex) maxOf(v * 3.2f, liveLevel) else v * 3.2f
                boosted.coerceIn(0.08f, 1f)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = trackColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            val count = displaySamples.size.coerceAtLeast(1)
            val slotW = size.width / count
            val barW = (slotW * 0.56f).coerceAtLeast(2f)
            val centerY = size.height / 2f

            displaySamples.forEachIndexed { idx, amp ->
                val h = (amp * size.height).coerceIn(4f, size.height)
                val x = idx * slotW + (slotW - barW) / 2f
                val y = centerY - h / 2f
                drawRoundRect(
                    color = activeColor.copy(alpha = if (amp > 0.14f) 0.95f else 0.35f),
                    topLeft = Offset(x, y),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                )
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
