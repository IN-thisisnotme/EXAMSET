package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.accessibility.AccessibilityManager
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
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt

private const val TAG = "VoiceSpeechSession"
private const val SILENCE_AUTO_FINISH_MS = 30_000L
private const val SILENCE_WARNING_THRESHOLD_MS = 18_000L
private const val RAPID_EMPTY_ERROR_WINDOW_MS = 1_200L
private const val MAX_RAPID_EMPTY_ERRORS_PER_ENGINE = 3
private const val ERROR_LANGUAGE_NOT_SUPPORTED_CODE = 12
private const val ERROR_LANGUAGE_UNAVAILABLE_CODE = 13
private const val STREAM_MUSIC_UNMUTE_SAFETY_MS = 450L
private const val AUDIO_PIPE_SAMPLE_RATE_HZ = 16_000
private const val AUDIO_PIPE_EARLY_FAIL_WINDOW_MS = 1_500L

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
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val accessibilityManager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

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

    private var activeBiasingVocabulary: List<String> = emptyList()
    private var activeReferenceText: String = ""

    private var audioPipeDisabledForSession = false
    private var usingAudioPipeForCurrentPass = false
    private var segmentedSessionActiveForCurrentPass = false

    private var activeAudioRecord: AudioRecord? = null
    private var activeAudioWriteFd: ParcelFileDescriptor? = null
    private var audioPumpStopFlag: AtomicBoolean? = null
    private var audioPumpThread: Thread? = null

    private var didMuteStreamMusic = false
    private val safetyUnmuteRunnable = Runnable {
        restoreMutedStreamMusicIfNeeded()
    }

    private var onSilenceAutoFinish: ((transcript: String, durationMs: Long, engineLabel: String) -> Unit)? = null
    private var onSilenceNoSpeech: ((message: String) -> Unit)? = null

    var status by mutableStateOf(VoiceMicSessionStatus.IDLE)
        private set

    var engineMode by mutableStateOf(VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST)

    var suppressSystemBeeps by mutableStateOf(true)

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

    private fun muteStreamMusicTransientlyIfAllowed() {
        if (!suppressSystemBeeps) return
        if (didMuteStreamMusic) return
        val am = audioManager ?: return
        val touchExploration = runCatching { accessibilityManager?.isTouchExplorationEnabled == true }.getOrDefault(false)
        if (touchExploration) return
        val alreadyMuted = runCatching { am.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(false)
        if (alreadyMuted) return

        val adjusted = runCatching {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            true
        }.getOrDefault(false)
        if (adjusted) {
            didMuteStreamMusic = true
            mainHandler.removeCallbacks(safetyUnmuteRunnable)
            mainHandler.postDelayed(safetyUnmuteRunnable, STREAM_MUSIC_UNMUTE_SAFETY_MS)
        }
    }

    private fun restoreMutedStreamMusicIfNeeded() {
        mainHandler.removeCallbacks(safetyUnmuteRunnable)
        if (!didMuteStreamMusic) return
        didMuteStreamMusic = false
        runCatching {
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }
    }

    private fun stopAudioPipeResources() {
        audioPumpStopFlag?.set(true)
        audioPumpStopFlag = null
        usingAudioPipeForCurrentPass = false

        runCatching { activeAudioRecord?.stop() }
        runCatching { activeAudioRecord?.release() }
        activeAudioRecord = null

        runCatching { activeAudioWriteFd?.close() }
        activeAudioWriteFd = null

        val threadToJoin = audioPumpThread
        audioPumpThread = null
        if (threadToJoin != null && threadToJoin != Thread.currentThread()) {
            runCatching { threadToJoin.join(120L) }
        }
    }

    private val sessionTicker = object : Runnable {
        override fun run() {
            if (status != VoiceMicSessionStatus.LISTENING) return
            val now = System.currentTimeMillis()
            activeSpeakingMs = accumulatedSpeakingMs + (now - unpausedStartWallMs).coerceAtLeast(0L)
            silenceElapsedMs = (now - lastSpeechActivityWallMs).coerceAtLeast(0L)
            if (!usingAudioPipeForCurrentPass) {
                normalizedRmsLevel = (normalizedRmsLevel * 0.84f).let { if (it < 0.03f) 0f else it }
            }

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
                        "Microphone stopped after 30 seconds of silence with no words detected. Tap Start Speaking when ready, or use System Dialog / Type Check."
                    )
                }
                return
            }

            mainHandler.postDelayed(this, 200L)
        }
    }

    fun startSession(
        initialSeedText: String = "",
        biasingVocabulary: List<String> = emptyList(),
        referenceTextForReranking: String = "",
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
            stopAudioPipeResources()
            restoreMutedStreamMusicIfNeeded()

            activeSessionGeneration++
            activeSegmentId++
            retriedClientBusyInSegment = false
            consecutiveRapidEmptyErrors = 0
            audioPipeDisabledForSession = false
            activeBiasingVocabulary = biasingVocabulary.take(96)
            activeReferenceText = referenceTextForReranking

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
            stopAudioPipeResources()
            restoreMutedStreamMusicIfNeeded()

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
            stopAudioPipeResources()
            restoreMutedStreamMusicIfNeeded()

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
        stopAudioPipeResources()
        restoreMutedStreamMusicIfNeeded()
        activeSessionGeneration++
        activeSegmentId++
        consecutiveRapidEmptyErrors = 0
        segmentedSessionActiveForCurrentPass = false
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
        stopAudioPipeResources()
        restoreMutedStreamMusicIfNeeded()
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

    private fun extractRecognizedText(bundle: Bundle?, rerankAgainstReference: Boolean): String {
        if (bundle == null) return ""
        return runCatching {
            val candidates = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .orEmpty()
            if (candidates.isEmpty()) {
                ""
            } else if (rerankAgainstReference && activeReferenceText.isNotBlank()) {
                VoiceFluencyEngine.selectBestRecognitionHypothesis(
                    candidates = candidates,
                    referenceText = activeReferenceText
                )
            } else {
                candidates.first()
            }
        }.getOrDefault("")
    }

    @SuppressLint("MissingPermission")
    private fun prepareAudioRecordPipe(
        generationSnapshot: Long,
        segmentSnapshot: Long
    ): Pair<ParcelFileDescriptor, AudioRecord>? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val minBuf = AudioRecord.getMinBufferSize(
            AUDIO_PIPE_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return null
        val bufferSize = maxOf(minBuf * 2, AUDIO_PIPE_SAMPLE_RATE_HZ / 5 * 2) // >= 200ms PCM 16-bit

        val audioRecord = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                AUDIO_PIPE_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }.getOrNull() ?: return null

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { audioRecord.release() }
            return null
        }

        val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()
        if (pipe == null) {
            runCatching { audioRecord.release() }
            return null
        }

        val readFd = pipe[0]
        val writeFd = pipe[1]
        activeAudioRecord = audioRecord
        activeAudioWriteFd = writeFd
        return readFd to audioRecord
    }

    private fun startAudioPipePump(
        audioRecord: AudioRecord,
        writeFd: ParcelFileDescriptor,
        generationSnapshot: Long,
        segmentSnapshot: Long
    ) {
        val stopFlag = AtomicBoolean(false)
        audioPumpStopFlag = stopFlag
        usingAudioPipeForCurrentPass = true

        audioPumpThread = thread(start = true, isDaemon = true, name = "ExamSetVoicePipe") {
            val frameBytes = 1280 // 40ms @ 16kHz 16-bit mono
            val buffer = ByteArray(frameBytes)
            try {
                audioRecord.startRecording()
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { out ->
                    while (!stopFlag.get() &&
                        generationSnapshot == activeSessionGeneration &&
                        segmentSnapshot == activeSegmentId
                    ) {
                        val readCount = audioRecord.read(buffer, 0, buffer.size)
                        if (readCount > 0) {
                            out.write(buffer, 0, readCount)
                            // Compute normalized RMS level cleanly without allocating per sample
                            var sumSq = 0.0
                            val sampleCount = readCount / 2
                            var idx = 0
                            while (idx + 1 < readCount) {
                                val lo = buffer[idx].toInt() and 0xFF
                                val hi = buffer[idx + 1].toInt()
                                val sample = (hi shl 8) or lo
                                val norm = sample / 32768.0
                                sumSq += norm * norm
                                idx += 2
                            }
                            if (sampleCount > 0) {
                                val rms = sqrt(sumSq / sampleCount).toFloat()
                                val level = (rms * 5.2f).coerceIn(0.05f, 1f)
                                if (rms >= 0.025f) {
                                    lastSpeechActivityWallMs = System.currentTimeMillis()
                                }
                                runOnMain {
                                    if (generationSnapshot == activeSessionGeneration &&
                                        segmentSnapshot == activeSegmentId &&
                                        status == VoiceMicSessionStatus.LISTENING
                                    ) {
                                        normalizedRmsLevel = level
                                        if (rms >= 0.025f) {
                                            silenceElapsedMs = 0L
                                        }
                                    }
                                }
                            }
                        } else if (readCount < 0) {
                            break
                        }
                    }
                    runCatching { out.flush() }
                }
            } catch (_: IOException) {
                // Pipe closed on pause/finish/cancel
            } catch (t: Throwable) {
                Log.w(TAG, "AudioRecord pipe pump terminated", t)
            } finally {
                runCatching { audioRecord.stop() }
                runCatching { audioRecord.release() }
            }
        }
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
        stopAudioPipeResources()
        cancelRecognizerSafely()

        val generationSnapshot = activeSessionGeneration
        val segmentSnapshot = ++activeSegmentId

        val preferOnDevice = !forceStandardFallback
        val recognizer = obtainRecognizer(preferOnDeviceApi = preferOnDevice)
        if (recognizer == null) {
            stopInternalToIdle(clearTranscripts = false)
            lastErrorMessage = "No Speech Recognition service could be initialized on this device. Tap System Dialog or Type Check below."
            return
        }

        val requestOfflineExtra = when (engineMode) {
            VoiceSpeechEngineMode.STRICT_OFFLINE -> true
            VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST -> !forceStandardFallback
        }

        val supportsApi33Features = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        var pendingPipe: Pair<ParcelFileDescriptor, AudioRecord>? = null
        if (supportsApi33Features && !audioPipeDisabledForSession && !forceStandardFallback) {
            pendingPipe = prepareAudioRecordPipe(generationSnapshot, segmentSnapshot)
        }
        val willUseAudioPipe = pendingPipe != null
        val willUseSegmentedSession = supportsApi33Features
        usingAudioPipeForCurrentPass = willUseAudioPipe
        segmentedSessionActiveForCurrentPass = willUseSegmentedSession

        activeEngineLabel = when {
            willUseAudioPipe && recognizerUsingOnDeviceApi -> "Continuous Silent Mic (On-Device)"
            willUseSegmentedSession && recognizerUsingOnDeviceApi -> "Segmented On-Device Speech"
            recognizerUsingOnDeviceApi -> "On-Device Speech-to-Text"
            requestOfflineExtra -> "Offline Speech-to-Text"
            else -> "Device Speech-to-Text"
        }

        val listenerAttached = runCatching {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    runOnMain {
                        restoreMutedStreamMusicIfNeeded()
                    }
                }

                override fun onBeginningOfSpeech() {
                    runOnMain {
                        restoreMutedStreamMusicIfNeeded()
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
                    if (usingAudioPipeForCurrentPass) return
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
                        if (!usingAudioPipeForCurrentPass) {
                            normalizedRmsLevel = 0.08f
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val hypothesis = extractRecognizedText(partialResults, rerankAgainstReference = false)
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

                override fun onSegmentResults(segmentResults: Bundle) {
                    val extracted = extractRecognizedText(segmentResults, rerankAgainstReference = true)
                    runOnMain {
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
                    }
                }

                override fun onEndOfSegmentedSession() {
                    runOnMain {
                        recognizerMayBeActive = false
                        if (generationSnapshot != activeSessionGeneration ||
                            segmentSnapshot != activeSegmentId ||
                            status != VoiceMicSessionStatus.LISTENING
                        ) return@runOnMain
                        val dangling = currentSegmentPartial.trim()
                        if (dangling.isNotBlank()) {
                            committedTranscript = VoiceFluencyEngine.mergeTranscriptSegments(committedTranscript, dangling)
                            currentSegmentPartial = ""
                        }
                        mainHandler.postDelayed({
                            if (generationSnapshot == activeSessionGeneration &&
                                status == VoiceMicSessionStatus.LISTENING
                            ) {
                                launchListeningPass(forceStandardFallback = forceStandardFallback)
                            }
                        }, 120L)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val extracted = extractRecognizedText(results, rerankAgainstReference = true)
                    runOnMain {
                        recognizerMayBeActive = false
                        restoreMutedStreamMusicIfNeeded()
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
                        }, 120L)
                    }
                }

                @SuppressLint("SwitchIntDef")
                override fun onError(error: Int) {
                    runOnMain {
                        recognizerMayBeActive = false
                        restoreMutedStreamMusicIfNeeded()
                        if (generationSnapshot != activeSessionGeneration ||
                            segmentSnapshot != activeSegmentId ||
                            status != VoiceMicSessionStatus.LISTENING
                        ) return@runOnMain

                        val passElapsed = System.currentTimeMillis() - lastPassStartedWallMs

                        // If custom PCM audio pipe failed quickly on this OEM, fall back cleanly to standard recognizer pass.
                        if (usingAudioPipeForCurrentPass &&
                            passElapsed < AUDIO_PIPE_EARLY_FAIL_WINDOW_MS &&
                            (error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_CLIENT)
                        ) {
                            audioPipeDisabledForSession = true
                            stopAudioPipeResources()
                            destroyRecognizer()
                            mainHandler.postDelayed({
                                if (generationSnapshot == activeSessionGeneration &&
                                    status == VoiceMicSessionStatus.LISTENING
                                ) {
                                    launchListeningPass(forceStandardFallback = forceStandardFallback)
                                }
                            }, 160L)
                            return@runOnMain
                        }

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
                                        }, 200L)
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
                                        }, 180L)
                                    }
                                    VoiceSpeechEmptyErrorAction.STOP_WITH_NOTICE -> {
                                        stopInternalToIdle(clearTranscripts = false)
                                        lastErrorMessage = "Live background mic is not receiving speech on this device. Tap System Dialog or Type Check below."
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
                                    }, 240L)
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
            pendingPipe?.let { (readFd, audioRecord) ->
                runCatching { readFd.close() }
                runCatching { audioRecord.release() }
            }
            destroyRecognizer()
            stopInternalToIdle(clearTranscripts = false)
            lastErrorMessage = "Microphone speech listener could not attach on this device. Tap System Dialog or Type Check below."
            return
        }

        val languageTag = Locale.getDefault().toLanguageTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 20_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 12_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
            if (requestOfflineExtra) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (activeBiasingVocabulary.isNotEmpty()) {
                    putStringArrayListExtra(
                        RecognizerIntent.EXTRA_BIASING_STRINGS,
                        ArrayList(activeBiasingVocabulary.take(96))
                    )
                }
                val pipePair = pendingPipe
                if (pipePair != null) {
                    putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipePair.first)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AUDIO_PIPE_SAMPLE_RATE_HZ)
                } else {
                    putExtra(
                        RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS
                    )
                }
            }
        }

        // Only transiently suppress system earcons if the platform recognizer is opening the mic directly
        if (!willUseAudioPipe) {
            muteStreamMusicTransientlyIfAllowed()
        }

        runCatching {
            lastPassStartedWallMs = System.currentTimeMillis()
            recognizerMayBeActive = true
            recognizer.startListening(intent)
            val pipePair = pendingPipe
            val writeFd = activeAudioWriteFd
            if (pipePair != null && writeFd != null) {
                // Close our process copy of readFd after Binder handoff so closing writeFd sends immediate EOF.
                runCatching { pipePair.first.close() }
                startAudioPipePump(
                    audioRecord = pipePair.second,
                    writeFd = writeFd,
                    generationSnapshot = generationSnapshot,
                    segmentSnapshot = segmentSnapshot
                )
            }
        }.onFailure { err ->
            recognizerMayBeActive = false
            restoreMutedStreamMusicIfNeeded()
            pendingPipe?.let { (readFd, _) -> runCatching { readFd.close() } }
            stopAudioPipeResources()
            Log.e(TAG, "SpeechRecognizer.startListening failed", err)
            if (willUseAudioPipe && !audioPipeDisabledForSession) {
                audioPipeDisabledForSession = true
                destroyRecognizer()
                mainHandler.postDelayed({
                    if (generationSnapshot == activeSessionGeneration &&
                        status == VoiceMicSessionStatus.LISTENING
                    ) {
                        launchListeningPass(forceStandardFallback = forceStandardFallback)
                    }
                }, 160L)
            } else if (engineMode == VoiceSpeechEngineMode.AUTO_OFFLINE_FIRST && !forceStandardFallback) {
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
                lastErrorMessage = "Microphone speech service could not start on this device. Tap System Dialog or Type Check below."
            }
        }
    }

    fun shutdown() {
        runOnMain {
            mainHandler.removeCallbacksAndMessages(null)
            stopAudioPipeResources()
            restoreMutedStreamMusicIfNeeded()
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
                "Microphone audio input failed. Close any app using the mic, or use System Dialog below."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission is required. Grant Microphone access in App Settings and try again."
            ERROR_LANGUAGE_UNAVAILABLE_CODE,
            ERROR_LANGUAGE_NOT_SUPPORTED_CODE ->
                if (mode == VoiceSpeechEngineMode.STRICT_OFFLINE) {
                    "Offline speech pack for ${Locale.getDefault().displayLanguage} is not downloaded. Switch to Smart Mic (Offline First) or download the offline voice pack in Android Settings."
                } else {
                    "Speech recognition for ${Locale.getDefault().displayLanguage} is unavailable on this device. Try System Dialog or Type Check."
                }
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER ->
                "Speech service is unreachable right now. Download your device's offline speech pack in Android Settings or use Type Check."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT ->
                "Android Speech Recognizer is busy or unsupported in the background on this device. Tap System Dialog below for instant dictation."
            else ->
                "Speech-to-Text stopped (code $errorCode). Tap Start Speaking again or use System Dialog below."
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
