package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.AppFontScaleMode
import com.example.data.PaperEntity
import com.example.data.StudyNoteEntity
import com.example.data.toQuestionPaper
import com.example.model.FolderItem
import com.example.model.QuestionPaper
import com.example.model.VocabularyLanguageVariants
import com.example.model.VoiceAnalysisUiState
import com.example.model.VoiceComparisonTolerance
import com.example.model.VoiceFolderPromptResult
import com.example.model.VoicePracticeMode
import com.example.model.VoicePromptItem
import com.example.model.VoiceSourceFilter
import com.example.ui.components.FontSizeSelectorDialog
import com.example.ui.components.LatexText
import com.example.ui.components.VoiceLanguageSelectorBar
import com.example.ui.components.VoiceLocalTakesPlayerCard
import com.example.ui.theme.ExamTheme
import com.example.util.DEFAULT_MAX_VOICE_TAKES_IN_SET
import com.example.util.FolderPathHelper
import com.example.util.VoiceFluencyEngine
import com.example.util.VoiceLanguageHelper
import com.example.util.VoiceLocalAudioStore
import com.example.util.VoiceMicSessionStatus
import com.example.util.VoiceSpeechEngineMode
import com.example.util.rememberVoiceAudioPlayer
import com.example.util.rememberVoiceSpeechSessionController
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private data class VoicePromptLoadState(
    val isLoading: Boolean = true,
    val result: VoiceFolderPromptResult = VoiceFolderPromptResult(
        prompts = emptyList(),
        skippedFormulaItems = 0,
        matchedFolderCount = 0
    )
)

private enum class VoiceCaptureBehavior(
    val label: String,
    val helperSummary: String
) {
    ZERO_BEEP_STUDIO(
        label = "Zero-Beep Studio",
        helperSummary = "Records cleanly with 0 pause beeps, then reads words from your audio."
    ),
    LIVE_SILENT_MIC(
        label = "Live Silent Mic",
        helperSummary = "Streams live words while locking system earcons muted for the full session."
    ),
    AUDIO_ONLY(
        label = "Audio Only (0 Beeps)",
        helperSummary = "Pure local recording for listen-back & 3-take comparison."
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun VoicePracticeScreen(
    folders: List<FolderItem>,
    studyNotes: List<StudyNoteEntity>,
    papers: List<PaperEntity>,
    initialFolderId: String,
    fontScaleMode: AppFontScaleMode,
    onSelectFontScaleMode: (AppFontScaleMode) -> Unit,
    onLogStudySeconds: (Long) -> Unit = {},
    onParsePaper: (PaperEntity) -> QuestionPaper = { it.toQuestionPaper() },
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    // [DEPRECATED 2026-11-26] Reason: Sound recording/playback (VoiceCaptureController) and online Gemini AI analysis (FirebaseVoiceAiAnalyzer) were excluded to make Speak to Test 100% sound-free and non-AI. Restore if: re-enabling WAV recording, audio playback, or cloud Gemini vocal-filler analysis.
    // val captureController = rememberVoiceCaptureController()
    // val aiAnalyzer = remember { FirebaseVoiceAiAnalyzer() }

    val speechController = rememberVoiceSpeechSessionController()
    val audioPlayer = rememberVoiceAudioPlayer()
    val sttSupported = remember(speechController) { speechController.isRecognitionAvailable }
    val postAudioSttSupported = remember(speechController) { speechController.isPostAudioTranscriptionSupported }
    var captureBehavior by remember(speechController) {
        mutableStateOf(
            when {
                speechController.isPostAudioTranscriptionSupported -> VoiceCaptureBehavior.ZERO_BEEP_STUDIO
                speechController.isRecognitionAvailable -> VoiceCaptureBehavior.LIVE_SILENT_MIC
                else -> VoiceCaptureBehavior.AUDIO_ONLY
            }
        )
    }

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
    var showReadContextDrawer by remember { mutableStateOf(false) }
    var showFolderDropdown by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var selectedLanguageOverride by rememberSaveable { mutableStateOf<String?>(null) }
    var englishVariant by rememberSaveable { mutableStateOf(VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT) }
    var savedClips by remember { mutableStateOf(VoiceLocalAudioStore.loadClips(context)) }
    var lastRegisteredClipPath by remember { mutableStateOf<String?>(null) }

    fun checkMicGranted(): Boolean = runCatching {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    var hasMicPermission by remember { mutableStateOf(checkMicGranted()) }
    var micPermissionDenied by remember { mutableStateOf(false) }
    var micPermanentlyDenied by remember { mutableStateOf(false) }
    var autoPromptedMicOnce by rememberSaveable { mutableStateOf(false) }
    var analysisState by remember { mutableStateOf<VoiceAnalysisUiState>(VoiceAnalysisUiState.Idle) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualSpokenText by remember { mutableStateOf("") }
    var comparisonTolerance by remember { mutableStateOf(VoiceComparisonTolerance.SMART_STT_TOLERANT) }

    LaunchedEffect(Unit) {
        VoiceLocalAudioStore.pruneOrphanWavs(context)
        savedClips = VoiceLocalAudioStore.loadClips(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = checkMicGranted()
                hasMicPermission = nowGranted
                if (nowGranted) {
                    micPermissionDenied = false
                    micPermanentlyDenied = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(view, speechController.status) {
        view.keepScreenOn = speechController.isSessionActive
        onDispose {
            view.keepScreenOn = false
        }
    }

    // Propagate terminal SpeechRecognizer error state cleanly into analysisState once
    LaunchedEffect(speechController.lastErrorMessage) {
        val err = speechController.lastErrorMessage
        if (!err.isNullOrBlank() && !speechController.isSessionActive) {
            showManualInput = true
            analysisState = VoiceAnalysisUiState.Error(err)
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

    val promptLoadState by produceState(
        initialValue = VoicePromptLoadState(isLoading = true),
        selectedFolderId, folders, studyNotes, papers, sourceFilter, includeFormulas
    ) {
        value = value.copy(isLoading = true)
        val built = try {
            withContext(Dispatchers.Default) {
                val buildContext = currentCoroutineContext()
                val promptCap = if (selectedFolderId == "all") 300 else 600
                VoiceFluencyEngine.buildPromptsForFolder(
                    selectedFolderId = selectedFolderId,
                    folders = folders,
                    studyNotes = studyNotes,
                    papers = papers,
                    sourceFilter = sourceFilter,
                    includeFormulas = includeFormulas,
                    maxTotalPrompts = promptCap,
                    maxPromptsPerPaper = 80,
                    maxPromptsPerNote = 100,
                    paperParser = onParsePaper,
                    onItemProcessed = { buildContext.ensureActive() }
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            VoiceFolderPromptResult(
                prompts = emptyList(),
                skippedFormulaItems = 0,
                matchedFolderCount = 0
            )
        }
        value = VoicePromptLoadState(isLoading = false, result = built)
    }

    val isBuildingPrompts = promptLoadState.isLoading
    val folderResult = promptLoadState.result

    val orderedPrompts = remember(folderResult.prompts, shuffleNonce) {
        if (shuffleNonce == 0) folderResult.prompts else folderResult.prompts.shuffled()
    }

    LaunchedEffect(orderedPrompts.size, selectedFolderId, sourceFilter, includeFormulas) {
        promptIndex = 0
        revealReference = false
        showReadContextDrawer = false
    }

    val safePromptIndex = promptIndex.coerceIn(0, (orderedPrompts.size - 1).coerceAtLeast(0))
    val currentPrompt: VoicePromptItem? = orderedPrompts.getOrNull(safePromptIndex)

    val libraryLanguages = remember(studyNotes, orderedPrompts) {
        (studyNotes.mapNotNull { it.parsedFormatRule().language.trim().takeIf { l -> l.isNotBlank() } } +
            orderedPrompts.map { it.language.trim() })
            .filter { it.isNotBlank() }
            .distinct()
    }
    val detectedPromptLanguage = currentPrompt?.language?.takeIf { it.isNotBlank() } ?: "English"
    val effectiveLanguage = remember(detectedPromptLanguage, selectedLanguageOverride) {
        VoiceLanguageHelper.normalizeLanguageDisplayName(
            selectedLanguageOverride?.takeIf { it.isNotBlank() } ?: detectedPromptLanguage
        )
    }
    val effectiveBcp47Tag = remember(effectiveLanguage, englishVariant) {
        VoiceLanguageHelper.resolveBcp47Tag(effectiveLanguage, englishVariant)
    }
    val effectiveLanguageBadge = remember(effectiveLanguage, englishVariant) {
        VoiceLanguageHelper.formatCompactLanguageBadge(effectiveLanguage, englishVariant)
    }

    val readAloudScript = remember(currentPrompt?.id, currentPrompt?.referenceSummary, currentPrompt?.formulaExpressions) {
        currentPrompt?.let { VoiceFluencyEngine.resolveReadAloudPassage(it) }.orEmpty()
    }
    val readAloudWordCount = remember(readAloudScript) {
        if (readAloudScript.isBlank()) 0 else VoiceFluencyEngine.countAlignmentWords(readAloudScript)
    }

    val biasingVocabulary = remember(currentPrompt?.id) {
        currentPrompt?.let { VoiceFluencyEngine.extractBiasingVocabulary(it) }.orEmpty()
    }
    val rerankingReferenceText = remember(currentPrompt?.id, readAloudScript, practiceMode) {
        if (practiceMode == VoicePracticeMode.READ_ALOUD) {
            readAloudScript
        } else {
            currentPrompt?.referenceSummary?.ifBlank { readAloudScript }.orEmpty()
        }
    }

    fun registerRecordedTakeIfAvailable(
        transcript: String,
        durationMs: Long,
        matchPercent: Int?,
        estimatedWpm: Int
    ) {
        val wavPath = speechController.lastRecordedWavPath ?: return
        val prompt = currentPrompt ?: return
        savedClips = VoiceLocalAudioStore.addTakeToRollingSet(
            context = context,
            sourceWavPath = wavPath,
            promptId = prompt.id,
            promptTitle = prompt.title,
            languageLabel = effectiveLanguageBadge,
            durationMs = durationMs,
            transcriptPreview = transcript,
            matchPercent = matchPercent,
            estimatedWpm = estimatedWpm,
            maxTakesInSet = DEFAULT_MAX_VOICE_TAKES_IN_SET
        )
        lastRegisteredClipPath = wavPath
    }

    val analysisErrorFallback = stringResource(R.string.voice_analysis_error)
    fun evaluateSpokenText(
        transcript: String,
        durationMs: Long,
        engineLabel: String,
        toleranceOverride: VoiceComparisonTolerance = comparisonTolerance,
        clipPathOverride: String? = null
    ) {
        val prompt = currentPrompt ?: return
        val cleaned = transcript.trim()
        if (cleaned.isBlank()) {
            analysisState = VoiceAnalysisUiState.Error(
                "No spoken words were detected yet. Tap Start Speaking or enter text below."
            )
            return
        }
        if (durationMs >= 1000L) {
            onLogStudySeconds((durationMs / 1000L).coerceAtLeast(1L))
        }
        runCatching {
            VoiceFluencyEngine.evaluateOfflineSpeechCheck(
                spokenTranscript = cleaned,
                prompt = prompt,
                durationMs = durationMs,
                mode = practiceMode,
                engineLabel = engineLabel,
                tolerance = toleranceOverride
            )
        }.onSuccess { result ->
            analysisState = VoiceAnalysisUiState.Success(result)
            val matchPct = result.metrics.comparison?.matchPercent
            val wpm = result.metrics.estimatedWpm
            val preferredClipPath = clipPathOverride ?: lastRegisteredClipPath
            if (!preferredClipPath.isNullOrBlank() && savedClips.any { it.filePath == preferredClipPath }) {
                savedClips = VoiceLocalAudioStore.updateTakeEvaluation(
                    context = context,
                    filePath = preferredClipPath,
                    transcriptPreview = cleaned,
                    matchPercent = matchPct,
                    estimatedWpm = wpm
                )
            } else if (!speechController.lastRecordedWavPath.isNullOrBlank()) {
                registerRecordedTakeIfAvailable(
                    transcript = cleaned,
                    durationMs = result.metrics.durationMs.coerceAtLeast(durationMs),
                    matchPercent = matchPct,
                    estimatedWpm = wpm
                )
            }
        }.onFailure {
            analysisState = VoiceAnalysisUiState.Error(analysisErrorFallback)
        }
    }

    LaunchedEffect(currentPrompt?.id, selectedFolderId, sourceFilter, includeFormulas) {
        speechController.clearAll()
        manualSpokenText = ""
        lastRegisteredClipPath = null
        showReadContextDrawer = false
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

    fun transcribeSavedClipSilently(
        wavPath: String,
        durationMs: Long
    ) {
        val expectedPromptId = currentPrompt?.id ?: return
        analysisState = VoiceAnalysisUiState.Analyzing
        speechController.transcribeSavedWavFile(
            wavPath = wavPath,
            languageTag = effectiveBcp47Tag,
            languageDisplayName = effectiveLanguage,
            biasingVocabulary = biasingVocabulary,
            referenceTextForReranking = rerankingReferenceText,
            onFinished = { spoken, engineLabel ->
                if (currentPrompt?.id != expectedPromptId) return@transcribeSavedWavFile
                if (spoken.isNotBlank()) {
                    manualSpokenText = spoken
                    speechController.replaceTranscript(spoken)
                    lastRegisteredClipPath = wavPath
                    evaluateSpokenText(
                        transcript = spoken,
                        durationMs = durationMs,
                        engineLabel = engineLabel,
                        clipPathOverride = wavPath
                    )
                } else {
                    analysisState = VoiceAnalysisUiState.RecordedReady(durationMs)
                }
            }
        )
    }

    fun completeRecordedAudioTake(
        wavPath: String?,
        durationMs: Long,
        autoReadWords: Boolean
    ) {
        if (wavPath.isNullOrBlank()) {
            analysisState = VoiceAnalysisUiState.Error(
                "No audio clip could be saved. Ensure microphone access is enabled and try again."
            )
            return
        }
        registerRecordedTakeIfAvailable(
            transcript = "",
            durationMs = durationMs,
            matchPercent = null,
            estimatedWpm = 0
        )
        if (durationMs >= 1000L) {
            onLogStudySeconds((durationMs / 1000L).coerceAtLeast(1L))
        }
        if (autoReadWords && postAudioSttSupported) {
            transcribeSavedClipSilently(wavPath = wavPath, durationMs = durationMs)
        } else {
            analysisState = VoiceAnalysisUiState.RecordedReady(durationMs)
        }
    }

    fun beginMicSession(
        seedExistingText: String = "",
        behaviorOverride: VoiceCaptureBehavior = captureBehavior
    ) {
        audioPlayer.stopAndRelease()
        speechController.cancelWavTranscription()
        lastRegisteredClipPath = null
        val useAudioFirstCapture = behaviorOverride != VoiceCaptureBehavior.LIVE_SILENT_MIC
        val autoReadAfterStop = behaviorOverride == VoiceCaptureBehavior.ZERO_BEEP_STUDIO && postAudioSttSupported
        analysisState = VoiceAnalysisUiState.Recording
        speechController.startSession(
            initialSeedText = seedExistingText,
            languageTag = effectiveBcp47Tag,
            languageDisplayName = effectiveLanguage,
            biasingVocabulary = biasingVocabulary,
            referenceTextForReranking = rerankingReferenceText,
            audioOnly = useAudioFirstCapture,
            onAutoFinishOnSilence = { transcript, durMs, engine ->
                manualSpokenText = transcript
                if (useAudioFirstCapture && transcript.isBlank()) {
                    completeRecordedAudioTake(
                        wavPath = speechController.lastRecordedWavPath,
                        durationMs = durMs,
                        autoReadWords = autoReadAfterStop
                    )
                } else {
                    evaluateSpokenText(transcript, durMs, engine)
                }
            },
            onNoSpeechTimeout = { msg ->
                analysisState = VoiceAnalysisUiState.Error(msg)
            }
        )
    }

    fun finishAndEvaluateMicSession() {
        val wasAudioOnly = speechController.isAudioOnlySession
        val autoReadAfterStop = wasAudioOnly &&
            captureBehavior == VoiceCaptureBehavior.ZERO_BEEP_STUDIO &&
            postAudioSttSupported
        val (transcript, durMs) = speechController.finishSession()
        manualSpokenText = transcript
        if (wasAudioOnly && transcript.isBlank()) {
            completeRecordedAudioTake(
                wavPath = speechController.lastRecordedWavPath,
                durationMs = durMs,
                autoReadWords = autoReadAfterStop
            )
        } else {
            evaluateSpokenText(
                transcript = transcript,
                durationMs = durMs,
                engineLabel = speechController.activeEngineLabel
            )
        }
    }

    // [DEPRECATED 2026-11-26] Reason: Google Voice Input pop-up dialog (ACTION_RECOGNIZE_SPEECH activity) always triggers system earcon beep chimes on open/pause/finish; replaced with Zero-Beep Studio (record cleanly first, then read words via EXTRA_AUDIO_SOURCE) and session-wide muted Live Silent Mic. Restore if: explicitly re-adding system popup dictation modal.
    // val systemSpeechLauncher = rememberLauncherForActivityResult(...) { ... }
    // fun launchSystemVoiceDialog() { ... }

    val settingsErrorFallback = stringResource(R.string.voice_mic_settings_error)
    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                analysisState = VoiceAnalysisUiState.Error(settingsErrorFallback)
            }
    }

    var pendingActionAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val actuallyGranted = granted || checkMicGranted()
        hasMicPermission = actuallyGranted
        micPermissionDenied = !actuallyGranted
        if (actuallyGranted) {
            micPermanentlyDenied = false
            pendingActionAfterPermission?.invoke()
        } else {
            val activity = context.findActivity()
            val canShowRationale = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            micPermanentlyDenied = !canShowRationale
            showManualInput = true
        }
        pendingActionAfterPermission = null
    }

    // Prompt once on opening Speak to Test if microphone permission is not yet granted
    LaunchedEffect(Unit) {
        if (!checkMicGranted() && !autoPromptedMicOnce) {
            autoPromptedMicOnce = true
            runCatching {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }.onFailure {
                micPermissionDenied = true
                showManualInput = true
            }
        }
    }

    fun runWithMicPermission(action: () -> Unit) {
        val grantedNow = checkMicGranted()
        hasMicPermission = grantedNow
        if (grantedNow) {
            micPermissionDenied = false
            micPermanentlyDenied = false
            action()
        } else {
            pendingActionAfterPermission = action
            runCatching {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }.onFailure {
                pendingActionAfterPermission = null
                micPermissionDenied = true
                micPermanentlyDenied = true
                showManualInput = true
            }
        }
    }

    BackHandler {
        when {
            showFontDialog -> showFontDialog = false
            speechController.isSessionActive -> {
                speechController.cancelSession()
                audioPlayer.stopAndRelease()
            }
            else -> {
                audioPlayer.stopAndRelease()
                onDismiss()
            }
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
            // Top Studio Header
            Surface(
                color = colors.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            speechController.cancelSession()
                            audioPlayer.stopAndRelease()
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Speak to Test",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.inkBold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.tealLight
                            ) {
                                Text(
                                    text = "Speech-to-Text Studio",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.tealDark,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = selectedFolderLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted,
                            maxLines = 1,
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
                        // Folder & Filter Controls Card
                        Card(
                            shape = RoundedCornerShape(18.dp),
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SUBJECT FOLDER",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkMuted
                                    )
                                    Text(
                                        text = when {
                                            isBuildingPrompts -> stringResource(R.string.voice_prompts_loading_short)
                                            folderResult.isTruncated -> "${orderedPrompts.size}+ prompts ready"
                                            else -> "${orderedPrompts.size} prompts ready"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.tealDark,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

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
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
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

                                // Horizontally scrollable Source & Formula Filter Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    VoiceSourceFilter.entries.forEach { filter ->
                                        FilterChip(
                                            selected = sourceFilter == filter,
                                            onClick = { sourceFilter = filter },
                                            label = {
                                                Text(
                                                    text = filter.label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            },
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
                                        label = {
                                            Text(
                                                text = "Formulas & Expressions",
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colors.amberLight,
                                            selectedLabelColor = colors.inkBold
                                        ),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("voice_include_formulas_chip")
                                    )
                                }

                                if (folderResult.isTruncated && !isBuildingPrompts) {
                                    Text(
                                        text = stringResource(R.string.voice_prompts_truncated_notice, orderedPrompts.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                        }

                        // Horizontally scrollable Practice Mode Selector (no multi-line stacking)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VoicePracticeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = practiceMode == mode,
                                    onClick = { practiceMode = mode },
                                    label = {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.navyBanner,
                                        selectedLabelColor = colors.navyBannerText
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("voice_mode_${mode.name.lowercase(Locale.ROOT)}")
                                )
                            }
                        }

                        // Active Prompt & Unambiguous "What to Read / Speak" Card
                        if (isBuildingPrompts) {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(0.9.dp, colors.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("voice_prompts_loading_card")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = colors.teal,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.voice_prompts_loading_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = stringResource(R.string.voice_prompts_loading_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.inkMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else if (currentPrompt == null) {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(0.9.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = colors.teal,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Text(
                                        text = "No spoken prompts in this folder yet",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Add Study Notes or Exam MCQs to this folder (or choose All Subject Folders) to practice reading and speaking aloud.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.inkMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(1.2.dp, colors.teal.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("voice_prompt_card")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Horizontally scrollable prompt header pills
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = colors.tealLight) {
                                            Text(
                                                text = "ITEM ${safePromptIndex + 1} OF ${orderedPrompts.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = colors.tealDark,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = colors.surfaceVariant
                                        ) {
                                            Text(
                                                text = currentPrompt.sourceLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.inkMuted,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        if (currentPrompt.language.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = colors.navyBanner
                                            ) {
                                                Text(
                                                    text = currentPrompt.language,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.navyBannerText,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        if (practiceMode == VoicePracticeMode.READ_ALOUD && readAloudWordCount > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = colors.amberLight
                                            ) {
                                                Text(
                                                    text = "$readAloudWordCount words to read",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.amber,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Topic Title
                                    Text(
                                        text = currentPrompt.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.inkBold
                                    )

                                    when (practiceMode) {
                                        VoicePracticeMode.READ_ALOUD -> {
                                            // Crystal-clear Teleprompter / Script Box matching graded text 1-to-1
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = colors.tealLight.copy(alpha = if (colors.isDark) 0.24f else 0.52f),
                                                border = BorderStroke(1.2.dp, colors.teal.copy(alpha = 0.6f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("voice_read_aloud_script_box")
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = colors.teal
                                                        ) {
                                                            Text(
                                                                text = "READ THESE EXACT LINES ALOUD",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color.White,
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = "Checked 1-to-1 word-for-word",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Medium,
                                                            color = colors.tealDark,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }

                                                    HorizontalDivider(color = colors.teal.copy(alpha = 0.2f))

                                                    val scriptLines = remember(readAloudScript) {
                                                        readAloudScript.lines().filter { it.isNotBlank() }
                                                    }
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        scriptLines.forEach { line ->
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalAlignment = Alignment.Top
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .padding(top = 7.dp)
                                                                        .size(6.dp)
                                                                        .clip(CircleShape)
                                                                        .background(colors.teal)
                                                                )
                                                                Box(modifier = Modifier.weight(1f)) {
                                                                    LatexText(
                                                                        text = line,
                                                                        fontSize = 16.5.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = colors.inkBold,
                                                                        isBold = false
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Optional collapsible context (question stem / MCQ options) kept separate so users never read unscored lines by mistake
                                            val hasDistinctContext = currentPrompt.promptQuestion.isNotBlank() &&
                                                currentPrompt.promptQuestion.trim() != readAloudScript.trim()
                                            if (hasDistinctContext || currentPrompt.optionExpressions.isNotEmpty()) {
                                                Surface(
                                                    onClick = { showReadContextDrawer = !showReadContextDrawer },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = colors.surfaceVariant,
                                                    border = BorderStroke(0.8.dp, colors.border),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 48.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "Question Context & Choices (not read aloud)",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = colors.inkMuted,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Icon(
                                                            imageVector = if (showReadContextDrawer) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                            contentDescription = "Toggle context",
                                                            tint = colors.inkMuted
                                                        )
                                                    }
                                                }

                                                AnimatedVisibility(visible = showReadContextDrawer) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        if (hasDistinctContext) {
                                                            LatexText(
                                                                text = currentPrompt.promptQuestion,
                                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                                                color = colors.inkMuted
                                                            )
                                                        }
                                                        VoicePromptFormulaAndOptionsBlock(
                                                            prompt = currentPrompt,
                                                            showFormulas = false,
                                                            showOptions = true
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        VoicePracticeMode.EXPLAIN_FROM_MEMORY -> {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = colors.surfaceVariant,
                                                border = BorderStroke(1.dp, colors.border),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = colors.navyBanner
                                                        ) {
                                                            Text(
                                                                text = "ANSWER FROM MEMORY",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = colors.navyBannerText,
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = "Speak in your own words",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = colors.inkMuted,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                    LatexText(
                                                        text = currentPrompt.promptQuestion,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.inkBold,
                                                        isBold = true
                                                    )
                                                }
                                            }

                                            VoicePromptFormulaAndOptionsBlock(
                                                prompt = currentPrompt,
                                                showFormulas = false,
                                                showOptions = true
                                            )

                                            Surface(
                                                onClick = { revealReference = !revealReference },
                                                shape = RoundedCornerShape(10.dp),
                                                color = colors.surfaceVariant,
                                                border = BorderStroke(0.8.dp, colors.border),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
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
                                                        text = if (revealReference) "Hide Reference Answer" else "Peek Reference Answer",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.inkBold
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(visible = revealReference) {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    VoicePromptFormulaAndOptionsBlock(
                                                        prompt = currentPrompt,
                                                        showFormulas = true,
                                                        showOptions = false
                                                    )
                                                    LatexText(
                                                        text = readAloudScript,
                                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                                        color = colors.ink
                                                    )
                                                }
                                            }
                                        }

                                        VoicePracticeMode.FREE_SUBJECT_SPEAK -> {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = colors.surfaceVariant,
                                                border = BorderStroke(1.dp, colors.border),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = colors.tealDark
                                                        ) {
                                                            Text(
                                                                text = "FREE SPEAK TOPIC",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                color = Color.White,
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = "Pace & subject keywords tracked",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = colors.inkMuted,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                    LatexText(
                                                        text = currentPrompt.promptQuestion,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.inkBold,
                                                        isBold = true
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Horizontally scrollable Prompt Navigation Strip
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                if (safePromptIndex > 0) {
                                                    promptIndex = safePromptIndex - 1
                                                    revealReference = false
                                                }
                                            },
                                            enabled = safePromptIndex > 0,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Prev", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
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
                                            Text("Shuffle", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                if (safePromptIndex < orderedPrompts.lastIndex) {
                                                    promptIndex = safePromptIndex + 1
                                                    revealReference = false
                                                }
                                            },
                                            enabled = safePromptIndex < orderedPrompts.lastIndex,
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("Next", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
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
                        val borderColor by animateColorAsState(
                            targetValue = when (speechController.status) {
                                VoiceMicSessionStatus.LISTENING -> colors.red
                                VoiceMicSessionStatus.PAUSED_BY_USER -> colors.amber
                                VoiceMicSessionStatus.IDLE -> colors.border
                            },
                            label = "mic_card_border"
                        )

                        // Pro Microphone & Live Speech-to-Text Studio Card
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(1.3.dp, borderColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("voice_recorder_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                when (speechController.status) {
                                                    VoiceMicSessionStatus.LISTENING -> colors.redLight.copy(alpha = 0.45f)
                                                    VoiceMicSessionStatus.PAUSED_BY_USER -> colors.amberLight.copy(alpha = 0.45f)
                                                    VoiceMicSessionStatus.IDLE -> colors.tealLight.copy(alpha = if (colors.isDark) 0.14f else 0.28f)
                                                },
                                                colors.surface
                                            )
                                        )
                                    )
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Status & Active Speaking Timer Bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(rememberScrollState()),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (speechController.status) {
                                                        VoiceMicSessionStatus.LISTENING -> colors.red
                                                        VoiceMicSessionStatus.PAUSED_BY_USER -> colors.amber
                                                        VoiceMicSessionStatus.IDLE -> colors.teal
                                                    }
                                                )
                                        )
                                        Text(
                                            text = when {
                                                speechController.isTranscribingWav ->
                                                    "Reading Words Silently ($effectiveLanguageBadge)"
                                                speechController.status == VoiceMicSessionStatus.LISTENING &&
                                                    captureBehavior == VoiceCaptureBehavior.ZERO_BEEP_STUDIO &&
                                                    speechController.isAudioOnlySession ->
                                                    "Zero-Beep Recording ($effectiveLanguageBadge)"
                                                speechController.status == VoiceMicSessionStatus.LISTENING &&
                                                    speechController.isAudioOnlySession ->
                                                    "Recording Local Take (Zero Beeps)"
                                                speechController.status == VoiceMicSessionStatus.LISTENING ->
                                                    "Listening Silently ($effectiveLanguageBadge)"
                                                speechController.status == VoiceMicSessionStatus.PAUSED_BY_USER ->
                                                    "Paused by You"
                                                else -> "Voice Studio Ready"
                                            },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                speechController.isTranscribingWav -> colors.tealDark
                                                speechController.status == VoiceMicSessionStatus.LISTENING -> colors.red
                                                speechController.status == VoiceMicSessionStatus.PAUSED_BY_USER -> colors.amber
                                                else -> colors.inkBold
                                            },
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = colors.surfaceVariant
                                    ) {
                                        Text(
                                            text = formatDurationMs(speechController.activeSpeakingMs),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = colors.tealDark,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                // Live Audio Energy Meter
                                VoiceAudioLevelBars(
                                    level = speechController.normalizedRmsLevel,
                                    status = speechController.status,
                                    activeColor = colors.red,
                                    pausedColor = colors.amber,
                                    idleColor = colors.border
                                )

                                // Multilingual Speech Language & English Accent Variant Picker
                                VoiceLanguageSelectorBar(
                                    detectedPromptLanguage = detectedPromptLanguage,
                                    selectedLanguageOverride = selectedLanguageOverride,
                                    englishVariant = englishVariant,
                                    libraryLanguages = libraryLanguages,
                                    enabled = !speechController.isSessionActive && !speechController.isTranscribingWav,
                                    onSelectLanguageOverride = { selectedLanguageOverride = it },
                                    onSelectEnglishVariant = { englishVariant = it }
                                )

                                // Capture Mode Selector (Zero-Beep Studio / Live Silent Mic / Audio Only)
                                val availableBehaviors = remember(postAudioSttSupported, sttSupported) {
                                    buildList {
                                        if (postAudioSttSupported) add(VoiceCaptureBehavior.ZERO_BEEP_STUDIO)
                                        if (sttSupported) add(VoiceCaptureBehavior.LIVE_SILENT_MIC)
                                        add(VoiceCaptureBehavior.AUDIO_ONLY)
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    availableBehaviors.forEach { behavior ->
                                        FilterChip(
                                            selected = captureBehavior == behavior,
                                            onClick = { captureBehavior = behavior },
                                            enabled = !speechController.isSessionActive && !speechController.isTranscribingWav,
                                            label = {
                                                Text(
                                                    text = behavior.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = if (behavior == VoiceCaptureBehavior.ZERO_BEEP_STUDIO ||
                                                    behavior == VoiceCaptureBehavior.AUDIO_ONLY
                                                ) {
                                                    colors.greenLight
                                                } else {
                                                    colors.tealLight
                                                },
                                                selectedLabelColor = if (behavior == VoiceCaptureBehavior.ZERO_BEEP_STUDIO ||
                                                    behavior == VoiceCaptureBehavior.AUDIO_ONLY
                                                ) {
                                                    colors.green
                                                } else {
                                                    colors.tealDark
                                                }
                                            ),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("voice_capture_behavior_${behavior.name.lowercase(Locale.ROOT)}")
                                        )
                                    }
                                }

                                // Engine Recognition Mode & Silent Mic Guard Strip (horizontally scrollable)
                                if (captureBehavior != VoiceCaptureBehavior.AUDIO_ONLY) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        VoiceSpeechEngineMode.entries.forEach { mode ->
                                            FilterChip(
                                                selected = speechController.engineMode == mode,
                                                onClick = { speechController.engineMode = mode },
                                                enabled = !speechController.isSessionActive && !speechController.isTranscribingWav,
                                                label = {
                                                    Text(
                                                        text = mode.label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = colors.tealLight,
                                                    selectedLabelColor = colors.tealDark
                                                ),
                                                modifier = Modifier.heightIn(min = 48.dp)
                                            )
                                        }
                                        FilterChip(
                                            selected = speechController.suppressSystemBeeps,
                                            onClick = {
                                                speechController.setSystemBeepSuppressionEnabled(!speechController.suppressSystemBeeps)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (speechController.suppressSystemBeeps) {
                                                        Icons.Default.VolumeOff
                                                    } else {
                                                        Icons.Default.VolumeUp
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = "Mute Beeps Guard",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = colors.greenLight,
                                                selectedLabelColor = colors.green
                                            ),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("voice_silent_mic_chip")
                                        )
                                    }
                                }

                                // Word-Comparison Tolerance Selector (protects against recognizer homophones/misspellings)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    VoiceComparisonTolerance.entries.forEach { tol ->
                                        FilterChip(
                                            selected = comparisonTolerance == tol,
                                            onClick = {
                                                comparisonTolerance = tol
                                                val currentSuccess = analysisState as? VoiceAnalysisUiState.Success
                                                val textToRecheck = manualSpokenText.ifBlank { speechController.liveTranscript }
                                                if (currentSuccess != null && textToRecheck.isNotBlank()) {
                                                    evaluateSpokenText(
                                                        transcript = textToRecheck,
                                                        durationMs = currentSuccess.result.metrics.durationMs,
                                                        engineLabel = currentSuccess.result.engineLabel,
                                                        toleranceOverride = tol
                                                    )
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = tol.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = colors.tealLight,
                                                selectedLabelColor = colors.tealDark
                                            ),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("voice_tolerance_${tol.name.lowercase(Locale.ROOT)}")
                                        )
                                    }
                                }

                                // 30-Second Continuous Silence Safety Countdown Banner
                                AnimatedVisibility(visible = speechController.isSilenceCountdownVisible) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.amberLight,
                                        border = BorderStroke(0.9.dp, colors.amber.copy(alpha = 0.55f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Quiet detected — auto-finishing in ${speechController.silenceRemainingSeconds}s. Keep speaking or tap Pause to hold indefinitely.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.amber,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }

                                // Informational Notice (e.g., auto fallback or pause saved state)
                                speechController.statusNotice?.let { notice ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.tealLight,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = notice,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.tealDark,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }

                                // Live Captured Words Box (shown whenever session active or transcript exists)
                                val liveText = speechController.liveTranscript
                                val liveWordCount = remember(liveText) {
                                    if (liveText.isBlank()) 0 else liveText.trim().split(Regex("\\s+")).size
                                }
                                if (speechController.isSessionActive || liveText.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = colors.surfaceVariant,
                                        border = BorderStroke(0.9.dp, colors.border),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("voice_live_transcript_box")
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "CAPTURED LIVE SPEECH",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.tealDark
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "$liveWordCount words",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.inkMuted,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                    if (liveText.isNotBlank() && !speechController.isListening) {
                                                        Surface(
                                                            onClick = {
                                                                manualSpokenText = liveText
                                                                showManualInput = true
                                                            },
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = colors.tealLight,
                                                            modifier = Modifier
                                                                .heightIn(min = 48.dp)
                                                                .testTag("voice_edit_captured_words_btn")
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Edit,
                                                                    contentDescription = "Edit captured words",
                                                                    tint = colors.tealDark,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Text(
                                                                    text = "Edit Words",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = colors.tealDark,
                                                                    maxLines = 1,
                                                                    softWrap = false
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            Text(
                                                text = liveText.ifBlank {
                                                    when {
                                                        speechController.isPaused ->
                                                            "Session paused cleanly — your take is safe. Tap Resume or Finish when ready."
                                                        speechController.isAudioOnlySession &&
                                                            captureBehavior == VoiceCaptureBehavior.ZERO_BEEP_STUDIO ->
                                                            "Recording cleanly with 0 pause beeps. Speak naturally — words are read silently when you tap Finish & Read Words."
                                                        speechController.isAudioOnlySession ->
                                                            "Recording pure local audio (0 beeps). Saved on this device only."
                                                        else ->
                                                            "Speak clearly — system earcons stay muted throughout this live session."
                                                    }
                                                },
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (liveText.isBlank()) colors.inkMuted else colors.inkBold
                                            )
                                        }
                                    }
                                }

                                if (!hasMicPermission) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (micPermissionDenied) colors.redLight.copy(alpha = 0.65f) else colors.amberLight.copy(alpha = 0.65f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (micPermissionDenied) colors.red.copy(alpha = 0.45f) else colors.amber.copy(alpha = 0.45f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("voice_mic_permission_banner")
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = if (micPermanentlyDenied) {
                                                    stringResource(R.string.voice_mic_permission_settings_needed)
                                                } else {
                                                    stringResource(R.string.voice_mic_permission_needed)
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (micPermissionDenied) colors.red else colors.inkBold,
                                                textAlign = TextAlign.Center
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (!micPermanentlyDenied) {
                                                    Button(
                                                        onClick = { runWithMicPermission {} },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = colors.teal,
                                                            contentColor = Color.White
                                                        ),
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier
                                                            .heightIn(min = 48.dp)
                                                            .testTag("voice_grant_mic_btn")
                                                    ) {
                                                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = stringResource(R.string.voice_mic_allow_btn),
                                                            style = MaterialTheme.typography.labelLarge,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                }
                                                OutlinedButton(
                                                    onClick = { openAppSettings() },
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("voice_open_settings_btn")
                                                ) {
                                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = stringResource(R.string.voice_mic_open_settings_btn),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Primary Studio Controls
                                when (speechController.status) {
                                    VoiceMicSessionStatus.IDLE -> {
                                        val micScale by animateFloatAsState(
                                            targetValue = if (currentPrompt != null && !speechController.isTranscribingWav) 1.0f else 0.96f,
                                            animationSpec = spring(),
                                            label = "mic_ready_scale"
                                        )
                                        Button(
                                            onClick = {
                                                runWithMicPermission { beginMicSession() }
                                            },
                                            enabled = currentPrompt != null && !speechController.isTranscribingWav,
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.teal,
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier
                                                .size((76 * micScale).dp)
                                                .testTag("voice_record_toggle_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "Start Speaking",
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }

                                        Text(
                                            text = if (speechController.isTranscribingWav) {
                                                "Reading spoken words silently from your local recording ($effectiveLanguageBadge)..."
                                            } else {
                                                "${captureBehavior.label} ($effectiveLanguageBadge) • ${captureBehavior.helperSummary}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.inkMuted,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    VoiceMicSessionStatus.LISTENING,
                                    VoiceMicSessionStatus.PAUSED_BY_USER -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (speechController.isListening) {
                                                Button(
                                                    onClick = { speechController.pauseByUser() },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = colors.amber,
                                                        contentColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("voice_pause_btn")
                                                ) {
                                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Pause",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        runWithMicPermission { speechController.resumeByUser() }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = colors.teal,
                                                        contentColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("voice_resume_btn")
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (speechController.isAudioOnlySession) "Resume Audio" else "Resume Mic",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = { finishAndEvaluateMicSession() },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = colors.green,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("voice_finish_check_btn")
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = when {
                                                        speechController.isAudioOnlySession &&
                                                            captureBehavior == VoiceCaptureBehavior.ZERO_BEEP_STUDIO &&
                                                            postAudioSttSupported -> "Finish & Read Words"
                                                        speechController.isAudioOnlySession -> "Save Audio Take"
                                                        else -> "Finish & Check"
                                                    },
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    speechController.cancelSession()
                                                    analysisState = VoiceAnalysisUiState.Idle
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("voice_cancel_session_btn")
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                // Secondary Utility Bar (Horizontally Scrollable — zero Google beep popups)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!speechController.isSessionActive &&
                                        !speechController.isTranscribingWav &&
                                        captureBehavior != VoiceCaptureBehavior.AUDIO_ONLY
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                runWithMicPermission {
                                                    beginMicSession(behaviorOverride = VoiceCaptureBehavior.AUDIO_ONLY)
                                                }
                                            },
                                            enabled = currentPrompt != null,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("voice_record_audio_only_btn")
                                        ) {
                                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Record Audio Only (Zero Beeps)",
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    // [DEPRECATED 2026-11-26] Reason: System Dialog (May Beep) button removed so Google Voice pop-up chimes cannot interrupt sessions. Restore if: user requests Google popup modal again.

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
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    if (analysisState != VoiceAnalysisUiState.Idle || liveText.isNotBlank() || speechController.isTranscribingWav) {
                                        OutlinedButton(
                                            onClick = {
                                                speechController.clearAll()
                                                manualSpokenText = ""
                                                analysisState = VoiceAnalysisUiState.Idle
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("voice_reset_btn")
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Reset",
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                softWrap = false
                                            )
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
                                                speechController.replaceTranscript(manualSpokenText)
                                                evaluateSpokenText(
                                                    transcript = manualSpokenText,
                                                    durationMs = speechController.activeSpeakingMs,
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
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        VoicePracticeResultsSection(
                            uiState = analysisState,
                            onRetryAnalyze = {
                                val retrySource = manualSpokenText.ifBlank { speechController.liveTranscript }
                                if (retrySource.isNotBlank()) {
                                    evaluateSpokenText(
                                        transcript = retrySource,
                                        durationMs = speechController.activeSpeakingMs,
                                        engineLabel = "Offline Text Comparison"
                                    )
                                } else {
                                    runWithMicPermission { beginMicSession() }
                                }
                            }
                        )

                        VoiceLocalTakesPlayerCard(
                            clips = savedClips,
                            audioPlayer = audioPlayer,
                            onPlayPauseClip = { clip ->
                                audioPlayer.playOrPause(clip.filePath)
                            },
                            onSeekClip = { posMs ->
                                audioPlayer.seekTo(posMs)
                            },
                            onKeepOnlyFinalClip = { clip ->
                                if (audioPlayer.activeFilePath != clip.filePath) {
                                    audioPlayer.stopAndRelease()
                                }
                                savedClips = VoiceLocalAudioStore.keepOnlyFinalClip(context, clip.id)
                                lastRegisteredClipPath = clip.filePath
                            },
                            onDeleteClip = { clip ->
                                if (audioPlayer.activeFilePath == clip.filePath) {
                                    audioPlayer.stopAndRelease()
                                }
                                savedClips = VoiceLocalAudioStore.deleteClip(context, clip.id)
                                if (lastRegisteredClipPath == clip.filePath) {
                                    lastRegisteredClipPath = null
                                }
                            },
                            onClearAllClips = {
                                audioPlayer.stopAndRelease()
                                VoiceLocalAudioStore.deleteAllRecordings(context)
                                savedClips = emptyList()
                                lastRegisteredClipPath = null
                            },
                            isTranscribing = speechController.isTranscribingWav || speechController.isSessionActive,
                            onTranscribeClip = if (postAudioSttSupported) {
                                { clip ->
                                    audioPlayer.stopAndRelease()
                                    transcribeSavedClipSilently(
                                        wavPath = clip.filePath,
                                        durationMs = clip.durationMs
                                    )
                                }
                            } else {
                                null
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
private fun VoiceAudioLevelBars(
    level: Float,
    status: VoiceMicSessionStatus,
    activeColor: Color,
    pausedColor: Color,
    idleColor: Color
) {
    val animatedLevel by animateFloatAsState(
        targetValue = when (status) {
            VoiceMicSessionStatus.LISTENING -> level.coerceIn(0.08f, 1f)
            VoiceMicSessionStatus.PAUSED_BY_USER -> 0.25f
            VoiceMicSessionStatus.IDLE -> 0.1f
        },
        animationSpec = spring(stiffness = 380f, dampingRatio = 0.7f),
        label = "audio_level_meter"
    )
    val barColor = when (status) {
        VoiceMicSessionStatus.LISTENING -> activeColor
        VoiceMicSessionStatus.PAUSED_BY_USER -> pausedColor
        VoiceMicSessionStatus.IDLE -> idleColor
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 8.dp)
    ) {
        val barCount = 24
        val spacing = size.width / barCount
        val barWidth = (spacing * 0.45f).coerceAtLeast(3.dp.toPx())
        val centerIdx = (barCount - 1) / 2f

        for (i in 0 until barCount) {
            val distanceWeight = 1f - (abs(i - centerIdx) / centerIdx) * 0.65f
            val microWave = if (i % 2 == 0) 1.0f else 0.72f
            val hFraction = (animatedLevel * distanceWeight * microWave).coerceIn(0.14f, 1f)
            val barHeight = size.height * hFraction
            val x = i * spacing + (spacing - barWidth) / 2f
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = barColor.copy(alpha = if (status == VoiceMicSessionStatus.IDLE) 0.45f else 0.9f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun VoicePromptFormulaAndOptionsBlock(
    prompt: VoicePromptItem,
    showFormulas: Boolean = true,
    showOptions: Boolean = true
) {
    val colors = ExamTheme.colors
    val displayFormulas = showFormulas && prompt.formulaExpressions.isNotEmpty()
    val displayOptions = showOptions && prompt.optionExpressions.isNotEmpty()
    if (!displayFormulas && !displayOptions) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (displayFormulas) {
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

        if (displayOptions) {
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
