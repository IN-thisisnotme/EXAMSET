package com.example.util

import android.content.Context
import android.content.Intent
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
import java.util.Locale

private const val TAG = "VoiceSpeechSession"
private const val SILENCE_AUTO_FINISH_MS = 30_000L
private const val SILENCE_WARNING_THRESHOLD_MS = 18_000L
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

@Stable
class VoiceSpeechSessionController(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerUsingOnDeviceApi = false

    private var activeSessionGeneration = 0L
    private var activeSegmentId = 0L
    private var retriedClientBusyInSegment = false

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
        mainHandler.removeCallbacksAndMessages(null)
        activeSessionGeneration++
        activeSegmentId++
        retriedClientBusyInSegment = false

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

    fun pauseByUser() {
        if (status != VoiceMicSessionStatus.LISTENING) return
        mainHandler.removeCallbacksAndMessages(null)
        activeSegmentId++

        val now = System.currentTimeMillis()
        accumulatedSpeakingMs += (now - unpausedStartWallMs).coerceAtLeast(0L)
        activeSpeakingMs = accumulatedSpeakingMs
        committedTranscript = VoiceFluencyEngine.mergeTranscriptSegments(committedTranscript, currentSegmentPartial)
        currentSegmentPartial = ""
        silenceElapsedMs = 0L
        normalizedRmsLevel = 0f

        runCatching { speechRecognizer?.cancel() }
        status = VoiceMicSessionStatus.PAUSED_BY_USER
        statusNotice = "Paused cleanly — your captured words are saved. Tap Resume Mic to continue or Finish & Check."
    }

    fun resumeByUser() {
        if (status != VoiceMicSessionStatus.PAUSED_BY_USER) return
        mainHandler.removeCallbacksAndMessages(null)
        retriedClientBusyInSegment = false

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
        stopInternalToIdle(clearTranscripts = true)
        statusNotice = null
        lastErrorMessage = null
    }

    fun appendExternalDictation(text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return
        committedTranscript = VoiceFluencyEngine.mergeTranscriptSegments(committedTranscript, cleaned)
        lastSpeechActivityWallMs = System.currentTimeMillis()
        silenceElapsedMs = 0L
        lastErrorMessage = null
    }

    fun replaceTranscript(text: String) {
        committedTranscript = text.trim()
        currentSegmentPartial = ""
    }

    fun clearAll() {
        stopInternalToIdle(clearTranscripts = true)
        statusNotice = null
        lastErrorMessage = null
    }

    private fun stopInternalToIdle(clearTranscripts: Boolean) {
        mainHandler.removeCallbacksAndMessages(null)
        activeSessionGeneration++
        activeSegmentId++
        runCatching { speechRecognizer?.cancel() }
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
        return created
    }

    private fun destroyRecognizer() {
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
        recognizerUsingOnDeviceApi = false
    }

    private fun launchListeningPass(forceStandardFallback: Boolean) {
        if (status != VoiceMicSessionStatus.LISTENING) return
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

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() {
                if (generationSnapshot != activeSessionGeneration ||
                    segmentSnapshot != activeSegmentId ||
                    status != VoiceMicSessionStatus.LISTENING
                ) return
                lastSpeechActivityWallMs = System.currentTimeMillis()
                silenceElapsedMs = 0L
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (generationSnapshot != activeSessionGeneration ||
                    segmentSnapshot != activeSegmentId ||
                    status != VoiceMicSessionStatus.LISTENING
                ) return
                normalizedRmsLevel = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (generationSnapshot != activeSessionGeneration ||
                    segmentSnapshot != activeSegmentId
                ) return
                normalizedRmsLevel = 0.08f
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (generationSnapshot != activeSessionGeneration ||
                    segmentSnapshot != activeSegmentId ||
                    status != VoiceMicSessionStatus.LISTENING
                ) return
                val hypothesis = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()
                if (hypothesis.isNotBlank() && hypothesis != currentSegmentPartial) {
                    currentSegmentPartial = hypothesis
                    lastSpeechActivityWallMs = System.currentTimeMillis()
                    silenceElapsedMs = 0L
                    statusNotice = null
                }
            }

            override fun onResults(results: Bundle?) {
                if (generationSnapshot != activeSessionGeneration ||
                    segmentSnapshot != activeSegmentId ||
                    status != VoiceMicSessionStatus.LISTENING
                ) return
                val finalChunk = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()
                    .ifBlank { currentSegmentPartial.trim() }

                if (finalChunk.isNotBlank()) {
                    committedTranscript = VoiceFluencyEngine.mergeTranscriptSegments(committedTranscript, finalChunk)
                    lastSpeechActivityWallMs = System.currentTimeMillis()
                    silenceElapsedMs = 0L
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

            override fun onError(error: Int) {
                if (generationSnapshot != activeSessionGeneration ||
                    segmentSnapshot != activeSegmentId ||
                    status != VoiceMicSessionStatus.LISTENING
                ) return

                // Always preserve any words captured in the current segment before handling error recovery.
                if (currentSegmentPartial.isNotBlank()) {
                    committedTranscript = VoiceFluencyEngine.mergeTranscriptSegments(committedTranscript, currentSegmentPartial)
                    currentSegmentPartial = ""
                    lastSpeechActivityWallMs = System.currentTimeMillis()
                }

                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        val silenceSoFar = System.currentTimeMillis() - lastSpeechActivityWallMs
                        if (silenceSoFar < SILENCE_AUTO_FINISH_MS) {
                            mainHandler.postDelayed({
                                if (generationSnapshot == activeSessionGeneration &&
                                    status == VoiceMicSessionStatus.LISTENING
                                ) {
                                    launchListeningPass(forceStandardFallback = forceStandardFallback)
                                }
                            }, 220L)
                        }
                    }

                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER,
                    ERROR_LANGUAGE_NOT_SUPPORTED_CODE,
                    ERROR_LANGUAGE_UNAVAILABLE_CODE -> {
                        if (engineMode == VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST && !forceStandardFallback) {
                            statusNotice = "Offline speech pack unavailable — switched automatically to device Speech Service."
                            mainHandler.postDelayed({
                                if (generationSnapshot == activeSessionGeneration &&
                                    status == VoiceMicSessionStatus.LISTENING
                                ) {
                                    launchListeningPass(forceStandardFallback = true)
                                }
                            }, 180L)
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

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

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
            recognizer.cancel()
            recognizer.startListening(intent)
        }.onFailure { err ->
            Log.e(TAG, "SpeechRecognizer.startListening failed", err)
            if (engineMode == VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST && !forceStandardFallback) {
                mainHandler.postDelayed({
                    if (generationSnapshot == activeSessionGeneration &&
                        status == VoiceMicSessionStatus.LISTENING
                    ) {
                        launchListeningPass(forceStandardFallback = true)
                    }
                }, 180L)
            } else {
                stopInternalToIdle(clearTranscripts = false)
                lastErrorMessage = "Microphone speech service could not start on this device. Tap System Voice Mic or Type Check below."
            }
        }
    }

    fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
        activeSessionGeneration++
        activeSegmentId++
        destroyRecognizer()
        status = VoiceMicSessionStatus.IDLE
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
