package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

private const val TAG = "VoiceSpeechSession"
private const val SILENCE_AUTO_FINISH_MS = 30_000L
private const val SILENCE_WARNING_THRESHOLD_MS = 18_000L
private const val RAPID_EMPTY_ERROR_WINDOW_MS = 1_200L
private const val MAX_RAPID_EMPTY_ERRORS_PER_ENGINE = 3
private const val ERROR_LANGUAGE_NOT_SUPPORTED_CODE = 12
private const val ERROR_LANGUAGE_UNAVAILABLE_CODE = 13

enum class VoiceMicSessionStatus {
    IDLE,
    LISTENING,
    PAUSED_BY_USER
}

enum class VoiceSpeechEngineMode(
    val label: String,
    val subtitle: String
) {
    AUTO_OFFLINE_FIRST(
        label = "Smart Mic (Offline First)",
        subtitle = "Prefers on-device voice and falls back seamlessly if the offline language pack is missing."
    ),
    STRICT_OFFLINE(
        label = "Strict Offline Pack",
        subtitle = "Requires an installed offline speech pack and never uses network speech."
    )
}

enum class VoiceSpeechEmptyErrorAction {
    RETRY_SAME_ENGINE,
    FALLBACK_TO_STANDARD,
    STOP_WITH_NOTICE
}

data class VoiceSpeechEmptyErrorDecision(
    val action: VoiceSpeechEmptyErrorAction,
    val nextRapidErrorCount: Int
)

object VoiceSpeechRetryPolicy {
    fun evaluateEmptyError(
        silenceSoFarMs: Long,
        passElapsedMs: Long,
        priorRapidEmptyErrors: Int,
        engineMode: VoiceSpeechEngineMode,
        isStandardFallbackActive: Boolean,
        silenceAutoFinishMs: Long = SILENCE_AUTO_FINISH_MS,
        rapidWindowMs: Long = RAPID_EMPTY_ERROR_WINDOW_MS,
        maxRapidErrors: Int = MAX_RAPID_EMPTY_ERRORS_PER_ENGINE
    ): VoiceSpeechEmptyErrorDecision {
        if (silenceSoFarMs >= silenceAutoFinishMs) {
            return VoiceSpeechEmptyErrorDecision(
                action = VoiceSpeechEmptyErrorAction.STOP_WITH_NOTICE,
                nextRapidErrorCount = 0
            )
        }
        val nextRapidCount = if (passElapsedMs in 0L until rapidWindowMs) {
            priorRapidEmptyErrors + 1
        } else {
            0
        }
        if (nextRapidCount >= maxRapidErrors) {
            return if (engineMode == VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST && !isStandardFallbackActive) {
                VoiceSpeechEmptyErrorDecision(
                    action = VoiceSpeechEmptyErrorAction.FALLBACK_TO_STANDARD,
                    nextRapidErrorCount = 0
                )
            } else {
                VoiceSpeechEmptyErrorDecision(
                    action = VoiceSpeechEmptyErrorAction.STOP_WITH_NOTICE,
                    nextRapidErrorCount = nextRapidCount
                )
            }
        }
        return VoiceSpeechEmptyErrorDecision(
            action = VoiceSpeechEmptyErrorAction.RETRY_SAME_ENGINE,
            nextRapidErrorCount = nextRapidCount
        )
    }
}

@Stable
class VoiceSpeechSessionController(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerUsingOnDeviceApi = false
    private var recognizerMayBeActive = false

    private var activeSessionGeneration = 0L
    private var activeSegmentId = 0L
    private var retriedClientBusyInSegment = false
    private var lastPassStartedWallMs = 0L
    private var consecutiveRapidEmptyErrors = 0

    private var unpausedStartWallMs = 0L
    private var accumulatedSpeakingMs = 0L
    private var lastSpeechActivityWallMs = 0L

    private var onSilenceAutoFinish: ((transcript: String, durationMs: Long, engineLabel: String) -> Unit)? = null
    private var onSilenceNoSpeech: ((message: String) -> Unit)? = null

    var status by mutableStateOf(VoiceMicSessionStatus.IDLE)
        private set

    var engineMode by mutableStateOf(VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST)

    var committedTranscript by mutableStateOf("")
        private set

    var currentSegmentPartial by mutableStateOf("")
        private set

    var activeSpeakingMs by mutableLongStateOf(0L)
        private set

    var silenceElapsedMs by mutableLongStateOf(0L)
        private set

    var normalizedRmsLevel by mutableFloatStateOf(0f)
        private set

    var statusNotice by mutableStateOf<String?>(null)
        private set

    var lastErrorMessage by mutableStateOf<String?>(null)
        private set

    var activeEngineLabel by mutableStateOf("On-Device Speech-to-Text")
        private set

    val isListening: Boolean
        get() = status == VoiceMicSessionStatus.LISTENING

    val isPaused: Boolean
        get() = status == VoiceMicSessionStatus.PAUSED_BY_USER

    val isSessionActive: Boolean
        get() = status != VoiceMicSessionStatus.IDLE

    val liveTranscript: String
        get() = VoiceFluencyEngine.mergeTranscriptSegments(committedTranscript, currentSegmentPartial)

    val silenceRemainingSeconds: Int
        get() = ((SILENCE_AUTO_FINISH_MS - silenceElapsedMs).coerceAtLeast(0L) / 1000L).toInt()

    val isSilenceCountdownVisible: Boolean
        get() = status == VoiceMicSessionStatus.LISTENING && silenceElapsedMs >= SILENCE_WARNING_THRESHOLD_MS

    val isRecognitionAvailable: Boolean
        get() {
            val standardAvailable = runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)
            val onDeviceAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)
            } else {
                false
            }
            return standardAvailable || onDeviceAvailable
        }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    private val sessionTicker = object : Runnable {
        override fun run() {
            if (status != VoiceMicSessionStatus.LISTENING) return
            val now = System.currentTimeMillis()
            activeSpeakingMs = accumulatedSpeakingMs + (now - unpausedStartWallMs).coerceAtLeast(0L)
            silenceElapsedMs = (now - lastSpeechActivityWallMs).coerceAtLeast(0L)
            normalizedRmsLevel = (normalizedRmsLevel * 0.84f).let { if (it < 0.03f) 0f else it }

            if (silenceElapsedMs >= SILENCE_AUTO_FINISH_MS) {
                val finalTranscript = VoiceFluencyEngine.mergeTranscriptSegments(committedTranscript, currentSegmentPartial)
                val finalDuration = activeSpeakingMs.coerceAtLeast(1000L)
                val finalEngine = activeEngineLabel
                stopInternalToIdle(clearTranscripts = false)
                if (finalTranscript.isNotBlank()) {
                    statusNotice = "Finished automatically after 30 seconds of silence."
                    onSilenceAutoFinish?.invoke(finalTranscript, finalDuration, finalEngine)
                } else {
                    onSilenceNoSpeech?.invoke(
                        "Microphone stopped after 30 seconds of silence with no words detected. Tap Start Speaking when ready, or use System Voice Mic / Type Check."
                    )
                }
                return
            }

            mainHandler.postDelayed(this, 200L)
        }
    }

    fun startSession(
        initialSeedText: String = "",
        onAutoFinishOnSilence: (transcript: String, durationMs: Long, engineLabel: String) -> Unit,
        onNoSpeechTimeout: (message: String) -> Unit
    ) {
        runOnMain {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                lastErrorMessage = describeSpeechError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, engineMode)
                status = VoiceMicSessionStatus.IDLE
                return@runOnMain
            }

            mainHandler.removeCallbacksAndMessages(null)
            activeSessionGeneration++
            activeSegmentId++
            retriedClientBusyInSegment = false
            consecutiveRapidEmptyErrors = 0

            onSilenceAutoFinish = onAutoFinishOnSilence
            onSilenceNoSpeech = onNoSpeechTimeout

            committedTranscript = initialSeedText.trim()
            currentSegmentPartial = ""
            accumulatedSpeakingMs = 0L
            activeSpeakingMs = 0L
            silenceElapsedMs = 0L
            normalizedRmsLevel = 0f
            statusNotice = null
            lastErrorMessage = null

            val now = System.currentTimeMillis()
            unpausedStartWallMs = now
            lastSpeechActivityWallMs = now
            status = VoiceMicSessionStatus.LISTENING

            mainHandler.post(sessionTicker)
            launchListeningPass(forceStandardFallback = false)
        }
    }

    fun pauseByUser() {
        runOnMain {
            if (status != VoiceMicSessionStatus.LISTENING) return@runOnMain
            mainHandler.removeCallbacksAndMessages(null)
            activeSegmentId++
            consecutiveRapidEmptyErrors = 0

            val now = System.currentTimeMillis()
            accumulatedSpeakingMs += (now - unpausedStartWallMs).coerceAtLeast(0L)
            activeSpeakingMs = accumulatedSpeakingMs
            committedTranscript = VoiceFluencyEngine.mergeTranscriptSegments(committedTranscript, currentSegmentPartial)
            currentSegmentPartial = ""
            silenceElapsedMs = 0L
            normalizedRmsLevel = 0f

            cancelRecognizerSafely()
            status = VoiceMicSessionStatus.PAUSED_BY_USER
            statusNotice = "Paused cleanly — your captured words are saved. Tap Resume Mic to continue or Finish & Check."
        }
    }

    fun resumeByUser() {
        runOnMain {
            if (status != VoiceMicSessionStatus.PAUSED_BY_USER) return@runOnMain
            mainHandler.removeCallbacksAndMessages(null)
            retriedClientBusyInSegment = false
            consecutiveRapidEmptyErrors = 0

            val now = System.currentTimeMillis()
            unpausedStartWallMs = now
            lastSpeechActivityWallMs = now
            silenceElapsedMs = 0L
            lastErrorMessage = null
            statusNotice = null
            status = VoiceMicSessionStatus.LISTENING

            mainHandler.post(sessionTicker)
            launchListeningPass(forceStandardFallback = false)
        }
    }

    fun finishSession(): Pair<String, Long> {
        val now = System.currentTimeMillis()
        val totalDuration = if (status == VoiceMicSessionStatus.LISTENING) {
            accumulatedSpeakingMs + (now - unpausedStartWallMs).coerceAtLeast(0L)
        } else {
            accumulatedSpeakingMs
        }
        val finalTranscript = VoiceFluencyEngine.mergeTranscriptSegments(committedTranscript, currentSegmentPartial)
        stopInternalToIdle(clearTranscripts = false)
        committedTranscript = finalTranscript
        activeSpeakingMs = totalDuration
        return finalTranscript to totalDuration
    }

    fun cancelSession() {
        runOnMain {
            stopInternalToIdle(clearTranscripts = true)
            statusNotice = null
            lastErrorMessage = null
        }
    }

    fun appendExternalDictation(text: String) {
        runOnMain {
            val cleaned = text.trim()
            if (cleaned.isBlank()) return@runOnMain
            committedTranscript = VoiceFluencyEngine.mergeTranscriptSegments(committedTranscript, cleaned)
            lastSpeechActivityWallMs = System.currentTimeMillis()
            silenceElapsedMs = 0L
            lastErrorMessage = null
        }
    }

    fun replaceTranscript(text: String) {
        runOnMain {
            committedTranscript = text.trim()
            currentSegmentPartial = ""
        }
    }

    fun clearAll() {
        runOnMain {
            stopInternalToIdle(clearTranscripts = true)
            statusNotice = null
            lastErrorMessage = null
        }
    }

    private fun cancelRecognizerSafely() {
        if (recognizerMayBeActive) {
            runCatching { speechRecognizer?.cancel() }
            recognizerMayBeActive = false
        }
    }

    private fun stopInternalToIdle(clearTranscripts: Boolean) {
        mainHandler.removeCallbacksAndMessages(null)
        activeSessionGeneration++
        activeSegmentId++
        consecutiveRapidEmptyErrors = 0
        cancelRecognizerSafely()
        status = VoiceMicSessionStatus.IDLE
        normalizedRmsLevel = 0f
        silenceElapsedMs = 0L
        currentSegmentPartial = ""
        if (clearTranscripts) {
            committedTranscript = ""
            accumulatedSpeakingMs = 0L
            activeSpeakingMs = 0L
        }
    }

    private fun obtainRecognizer(preferOnDeviceApi: Boolean): SpeechRecognizer? {
        val canUseOnDeviceApi = preferOnDeviceApi &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)

        val existing = speechRecognizer
        if (existing != null && recognizerUsingOnDeviceApi == canUseOnDeviceApi) {
            return existing
        }

        destroyRecognizer()

        val created = if (canUseOnDeviceApi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext) }.getOrNull()
                ?.also { recognizerUsingOnDeviceApi = true }
        } else {
            null
        } ?: runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
            ?.also { recognizerUsingOnDeviceApi = false }

        speechRecognizer = created
        recognizerMayBeActive = false
        return created
    }

    private fun destroyRecognizer() {
        runCatching {
            if (recognizerMayBeActive) {
                speechRecognizer?.cancel()
            }
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
        recognizerUsingOnDeviceApi = false
        recognizerMayBeActive = false
    }

    private fun extractFirstRecognizedText(bundle: Bundle?): String {
        if (bundle == null) return ""
        return runCatching {
            bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { !it.isNullOrBlank() }
                ?.trim()
                .orEmpty()
        }.getOrDefault("")
    }

    private fun launchListeningPass(forceStandardFallback: Boolean) {
        if (status != VoiceMicSessionStatus.LISTENING) return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopInternalToIdle(clearTranscripts = false)
            lastErrorMessage = describeSpeechError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, engineMode)
            return
        }

        // Cancel any prior listening pass BEFORE attaching a new listener so stale cancel callbacks never race.
        cancelRecognizerSafely()

        val generationSnapshot = activeSessionGeneration
        val segmentSnapshot = ++activeSegmentId

        val preferOnDevice = !forceStandardFallback
        val recognizer = obtainRecognizer(preferOnDeviceApi = preferOnDevice)
        if (recognizer == null) {
            stopInternalToIdle(clearTranscripts = false)
            lastErrorMessage = "No Speech Recognition service could be initialized on this device. Tap System Voice Mic or Type Check below."
            return
        }

        val requestOfflineExtra = when (engineMode) {
            VoiceSpeechEngineMode.STRICT_OFFLINE -> true
            VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST -> !forceStandardFallback
        }
        activeEngineLabel = when {
            recognizerUsingOnDeviceApi -> "On-Device Speech-to-Text"
            requestOfflineExtra -> "Offline Speech-to-Text"
            else -> "Device Speech-to-Text"
        }

        val listenerAttached = runCatching {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() {
                    runOnMain {
                        if (generationSnapshot != activeSessionGeneration ||
                            segmentSnapshot != activeSegmentId ||
                            status != VoiceMicSessionStatus.LISTENING
                        ) return@runOnMain
                        consecutiveRapidEmptyErrors = 0
                        lastSpeechActivityWallMs = System.currentTimeMillis()
                        silenceElapsedMs = 0L
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {
                    runOnMain {
                        if (generationSnapshot != activeSessionGeneration ||
                            segmentSnapshot != activeSegmentId ||
                            status != VoiceMicSessionStatus.LISTENING
                        ) return@runOnMain
                        normalizedRmsLevel = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                    }
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    runOnMain {
                        if (generationSnapshot != activeSessionGeneration ||
                            segmentSnapshot != activeSegmentId
                        ) return@runOnMain
                        normalizedRmsLevel = 0.08f
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val hypothesis = extractFirstRecognizedText(partialResults)
                    if (hypothesis.isBlank()) return
                    runOnMain {
                        if (generationSnapshot != activeSessionGeneration ||
                            segmentSnapshot != activeSegmentId ||
                            status != VoiceMicSessionStatus.LISTENING
                        ) return@runOnMain
                        consecutiveRapidEmptyErrors = 0
                        if (hypothesis != currentSegmentPartial) {
                            currentSegmentPartial = hypothesis
                            lastSpeechActivityWallMs = System.currentTimeMillis()
                            silenceElapsedMs = 0L
                            statusNotice = null
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val extracted = extractFirstRecognizedText(results)
                    runOnMain {
                        recognizerMayBeActive = false
                        if (generationSnapshot != activeSessionGeneration ||
                            segmentSnapshot != activeSegmentId ||
                            status != VoiceMicSessionStatus.LISTENING
                        ) return@runOnMain
                        val finalChunk = extracted.ifBlank { currentSegmentPartial.trim() }

                        if (finalChunk.isNotBlank()) {
                            committedTranscript = VoiceFluencyEngine.mergeTranscriptSegments(committedTranscript, finalChunk)
                            lastSpeechActivityWallMs = System.currentTimeMillis()
                            silenceElapsedMs = 0L
                            consecutiveRapidEmptyErrors = 0
                        }
                        currentSegmentPartial = ""
                        retriedClientBusyInSegment = false

                        // Keep continuous session open across natural mid-sentence pauses until user pauses/stops or 30s silence elapses.
                        mainHandler.postDelayed({
                            if (generationSnapshot == activeSessionGeneration &&
                                status == VoiceMicSessionStatus.LISTENING
                            ) {
                                launchListeningPass(forceStandardFallback = forceStandardFallback)
                            }
                        }, 160L)
                    }
                }

                @SuppressLint("SwitchIntDef")
                override fun onError(error: Int) {
                    runOnMain {
                        recognizerMayBeActive = false
                        if (generationSnapshot != activeSessionGeneration ||
                            segmentSnapshot != activeSegmentId ||
                            status != VoiceMicSessionStatus.LISTENING
                        ) return@runOnMain

                        // Always preserve any words captured in the current segment before handling error recovery.
                        val hadPartialWords = currentSegmentPartial.isNotBlank()
                        if (hadPartialWords) {
                            committedTranscript = VoiceFluencyEngine.mergeTranscriptSegments(committedTranscript, currentSegmentPartial)
                            currentSegmentPartial = ""
                            lastSpeechActivityWallMs = System.currentTimeMillis()
                            consecutiveRapidEmptyErrors = 0
                        }

                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                val now = System.currentTimeMillis()
                                val silenceSoFar = now - lastSpeechActivityWallMs
                                val passElapsed = now - lastPassStartedWallMs
                                val decision = VoiceSpeechRetryPolicy.evaluateEmptyError(
                                    silenceSoFarMs = silenceSoFar,
                                    passElapsedMs = passElapsed,
                                    priorRapidEmptyErrors = consecutiveRapidEmptyErrors,
                                    engineMode = engineMode,
                                    isStandardFallbackActive = forceStandardFallback
                                )
                                consecutiveRapidEmptyErrors = decision.nextRapidErrorCount
                                when (decision.action) {
                                    VoiceSpeechEmptyErrorAction.RETRY_SAME_ENGINE -> {
                                        mainHandler.postDelayed({
                                            if (generationSnapshot == activeSessionGeneration &&
                                                status == VoiceMicSessionStatus.LISTENING
                                            ) {
                                                launchListeningPass(forceStandardFallback = forceStandardFallback)
                                            }
                                        }, 240L)
                                    }
                                    VoiceSpeechEmptyErrorAction.FALLBACK_TO_STANDARD -> {
                                        destroyRecognizer()
                                        statusNotice = "Switched to standard device Speech Service for clearer pickup."
                                        mainHandler.postDelayed({
                                            if (generationSnapshot == activeSessionGeneration &&
                                                status == VoiceMicSessionStatus.LISTENING
                                            ) {
                                                launchListeningPass(forceStandardFallback = true)
                                            }
                                        }, 200L)
                                    }
                                    VoiceSpeechEmptyErrorAction.STOP_WITH_NOTICE -> {
                                        stopInternalToIdle(clearTranscripts = false)
                                        lastErrorMessage = "Live background mic is not receiving speech on this device. Tap System Voice Mic or Type Check below."
                                    }
                                }
                            }

                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                            SpeechRecognizer.ERROR_SERVER,
                            ERROR_LANGUAGE_NOT_SUPPORTED_CODE,
                            ERROR_LANGUAGE_UNAVAILABLE_CODE -> {
                                if (engineMode == VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST && !forceStandardFallback) {
                                    destroyRecognizer()
                                    statusNotice = "Offline speech pack unavailable — switched automatically to device Speech Service."
                                    mainHandler.postDelayed({
                                        if (generationSnapshot == activeSessionGeneration &&
                                            status == VoiceMicSessionStatus.LISTENING
                                        ) {
                                            launchListeningPass(forceStandardFallback = true)
                                        }
                                    }, 200L)
                                } else {
                                    stopInternalToIdle(clearTranscripts = false)
                                    lastErrorMessage = describeSpeechError(error, engineMode)
                                }
                            }

                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                            SpeechRecognizer.ERROR_CLIENT -> {
                                if (!retriedClientBusyInSegment) {
                                    retriedClientBusyInSegment = true
                                    destroyRecognizer()
                                    mainHandler.postDelayed({
                                        if (generationSnapshot == activeSessionGeneration &&
                                            status == VoiceMicSessionStatus.LISTENING
                                        ) {
                                            launchListeningPass(forceStandardFallback = forceStandardFallback)
                                        }
                                    }, 280L)
                                } else {
                                    stopInternalToIdle(clearTranscripts = false)
                                    lastErrorMessage = describeSpeechError(error, engineMode)
                                }
                            }

                            else -> {
                                stopInternalToIdle(clearTranscripts = false)
                                lastErrorMessage = describeSpeechError(error, engineMode)
                            }
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        if (listenerAttached.isFailure) {
            Log.e(TAG, "SpeechRecognizer.setRecognitionListener failed", listenerAttached.exceptionOrNull())
            destroyRecognizer()
            stopInternalToIdle(clearTranscripts = false)
            lastErrorMessage = "Microphone speech listener could not attach on this device. Tap System Voice Mic or Type Check below."
            return
        }

        val languageTag = Locale.getDefault().toLanguageTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8_000L)
            if (requestOfflineExtra) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        runCatching {
            lastPassStartedWallMs = System.currentTimeMillis()
            recognizerMayBeActive = true
            recognizer.startListening(intent)
        }.onFailure { err ->
            recognizerMayBeActive = false
            Log.e(TAG, "SpeechRecognizer.startListening failed", err)
            if (engineMode == VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST && !forceStandardFallback) {
                destroyRecognizer()
                mainHandler.postDelayed({
                    if (generationSnapshot == activeSessionGeneration &&
                        status == VoiceMicSessionStatus.LISTENING
                    ) {
                        launchListeningPass(forceStandardFallback = true)
                    }
                }, 200L)
            } else {
                stopInternalToIdle(clearTranscripts = false)
                lastErrorMessage = "Microphone speech service could not start on this device. Tap System Voice Mic or Type Check below."
            }
        }
    }

    fun shutdown() {
        runOnMain {
            mainHandler.removeCallbacksAndMessages(null)
            activeSessionGeneration++
            activeSegmentId++
            consecutiveRapidEmptyErrors = 0
            destroyRecognizer()
            status = VoiceMicSessionStatus.IDLE
        }
    }

    private fun describeSpeechError(errorCode: Int, mode: VoiceSpeechEngineMode): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO ->
                "Microphone audio input failed. Close any app using the mic, or use System Voice Mic below."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission is required. Grant Microphone access in App Settings and try again."
            ERROR_LANGUAGE_UNAVAILABLE_CODE,
            ERROR_LANGUAGE_NOT_SUPPORTED_CODE ->
                if (mode == VoiceSpeechEngineMode.STRICT_OFFLINE) {
                    "Offline speech pack for ${Locale.getDefault().displayLanguage} is not downloaded. Switch to Smart Mic (Offline First) or download the offline voice pack in Android Settings."
                } else {
                    "Speech recognition for ${Locale.getDefault().displayLanguage} is unavailable on this device. Try System Voice Mic or Type Check."
                }
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER ->
                "Speech service is unreachable right now. Download your device's offline speech pack in Android Settings or use Type Check."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT ->
                "Android Speech Recognizer is busy or unsupported in the background on this device. Tap System Voice Mic below for instant dictation."
            else ->
                "Speech-to-Text stopped (code $errorCode). Tap Start Speaking again or use System Voice Mic below."
        }
    }
}

@Composable
fun rememberVoiceSpeechSessionController(): VoiceSpeechSessionController {
    val context = LocalContext.current
    val controller = remember(context) { VoiceSpeechSessionController(context) }
    DisposableEffect(controller) {
        onDispose {
            controller.shutdown()
        }
    }
    return controller
}
