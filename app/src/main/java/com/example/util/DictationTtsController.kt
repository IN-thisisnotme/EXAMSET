package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

enum class DictationPlaybackPhase {
    IDLE,
    SPEAKING_PHRASE,
    WRITING_PAUSE,
    PAUSED,
    COMPLETED
}

@Stable
class DictationTtsController(
    context: Context,
    private val initialPassages: List<DictationPassageItem>,
    initialLanguage: String = "English",
    initialEnglishVariant: String = "US",
    private val onLogStudySeconds: (Long) -> Unit = {}
) {
    private val appContext = context.applicationContext ?: context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingUtteranceId: String? = null
    private var pendingUtteranceSignal: CompletableDeferred<Unit>? = null
    private var playbackJob: Job? = null
    private var activeFocusRequest: AudioFocusRequest? = null

    private var passages: List<DictationPassageItem> = initialPassages
    private var unloggedActiveSecondsAccumulator: Long = 0L
    private var secondsIntoCurrentElevationBucket: Int = 0

    var config by mutableStateOf(DictationConfig())
        private set

    var language by mutableStateOf(initialLanguage.ifBlank { "English" })
        private set

    var englishVariant by mutableStateOf(initialEnglishVariant.ifBlank { "US" })
        private set

    var currentPassageIndex by mutableIntStateOf(0)
        private set

    var currentPhraseIndex by mutableIntStateOf(0)
        private set

    var phrases by mutableStateOf<List<DictationPhrase>>(emptyList())
        private set

    var phase by mutableStateOf(DictationPlaybackPhase.IDLE)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var writingPauseTotalMs by mutableLongStateOf(0L)
        private set

    var writingPauseRemainingMs by mutableLongStateOf(0L)
        private set

    var writingPauseProgress by mutableFloatStateOf(0f)
        private set

    var sessionActiveSeconds by mutableLongStateOf(0L)
        private set

    var ttsAvailable by mutableStateOf(true)
        private set

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                mainHandler.post {
                    if (isPlaying) {
                        pause()
                    }
                }
            }
        }
    }

    init {
        rebuildPhrasesForCurrentPassage()
        initTtsEngine()
    }

    private fun initTtsEngine() {
        try {
            val engine = TextToSpeech(appContext) { status ->
                mainHandler.post {
                    ttsReady = (status == TextToSpeech.SUCCESS)
                    ttsAvailable = ttsReady
                }
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        if (utteranceId != null && utteranceId == pendingUtteranceId) {
                            pendingUtteranceSignal?.complete(Unit)
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        if (utteranceId != null && utteranceId == pendingUtteranceId) {
                            pendingUtteranceSignal?.complete(Unit)
                        }
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post {
                        if (utteranceId != null && utteranceId == pendingUtteranceId) {
                            pendingUtteranceSignal?.complete(Unit)
                        }
                    }
                }
            })
            tts = engine
        } catch (_: Exception) {
            ttsReady = false
            ttsAvailable = false
            tts = null
        }
    }

    val currentPassage: DictationPassageItem?
        get() = passages.getOrNull(currentPassageIndex)

    val currentPhrase: DictationPhrase?
        get() = phrases.getOrNull(currentPhraseIndex)

    private fun rebuildPhrasesForCurrentPassage(preserveSentencePosition: Boolean = false) {
        val passage = passages.getOrNull(currentPassageIndex)
        if (passage == null) {
            phrases = emptyList()
            currentPhraseIndex = 0
            return
        }
        val oldIndex = currentPhraseIndex
        val newList = DictationEngine.splitPassageIntoPhrases(
            rawText = passage.rawDictationText,
            punctuationMode = config.effectivePunctuationMode
        )
        phrases = newList
        currentPhraseIndex = if (preserveSentencePosition && newList.isNotEmpty()) {
            oldIndex.coerceIn(0, newList.lastIndex)
        } else {
            0
        }
    }

    fun updateLanguage(newLanguage: String, newEnglishVariant: String = englishVariant) {
        language = newLanguage.trim().ifBlank { "English" }
        englishVariant = newEnglishVariant.trim().ifBlank { "US" }
    }

    fun setTargetWpm(wpm: Int) {
        config = config.copy(targetWpm = wpm).normalized()
    }

    fun adjustWpm(delta: Int) {
        setTargetWpm(config.targetWpm + delta)
    }

    fun setPunctuationMode(mode: DictationPunctuationMode) {
        if (config.effectivePunctuationMode == mode) return
        config = config.copy(
            punctuationMode = mode,
            speakPunctuationCues = mode != DictationPunctuationMode.OFF
        ).normalized()
        rebuildPhrasesForCurrentPassage(preserveSentencePosition = true)
    }

    fun setSpeakPunctuationCues(enabled: Boolean) {
        setPunctuationMode(if (enabled) DictationPunctuationMode.ESSENTIAL else DictationPunctuationMode.OFF)
    }

    fun updateElevatingConfig(
        enabled: Boolean,
        startWpm: Int = config.startWpm,
        targetMaxWpm: Int = config.targetMaxWpm,
        stepWpm: Int = config.stepWpm,
        interval: DictationElevationInterval = config.elevationInterval
    ) {
        val updated = config.copy(
            elevatingEnabled = enabled,
            startWpm = startWpm,
            targetMaxWpm = targetMaxWpm,
            stepWpm = stepWpm,
            elevationInterval = interval
        ).normalized()
        config = if (enabled && !config.elevatingEnabled) {
            updated.copy(targetWpm = updated.startWpm)
        } else {
            updated
        }
        secondsIntoCurrentElevationBucket = 0
    }

    fun playPause() {
        if (isPlaying) {
            pause()
        } else {
            startOrResume()
        }
    }

    fun startOrResume() {
        if (passages.isEmpty()) return
        if (phrases.isEmpty()) {
            rebuildPhrasesForCurrentPassage()
            if (phrases.isEmpty()) return
        }
        if (phase == DictationPlaybackPhase.COMPLETED && currentPhraseIndex >= phrases.lastIndex) {
            currentPhraseIndex = 0
        }
        requestAudioFocus()
        isPlaying = true
        launchPlaybackLoop()
    }

    fun pause() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        stopSpeakingOnly()
        abandonAudioFocus()
        flushUnloggedStudySeconds()
        if (phase != DictationPlaybackPhase.COMPLETED) {
            phase = DictationPlaybackPhase.PAUSED
        }
    }

    fun repeatCurrentPhrase() {
        val wasPlaying = isPlaying
        playbackJob?.cancel()
        playbackJob = null
        stopSpeakingOnly()
        writingPauseRemainingMs = 0L
        writingPauseProgress = 0f
        if (phrases.isEmpty()) return
        currentPhraseIndex = currentPhraseIndex.coerceIn(0, phrases.lastIndex)
        if (wasPlaying) {
            isPlaying = true
            launchPlaybackLoop()
        } else {
            // Speak single phrase once even while paused so user can re-hear a tricky word quickly
            scope.launch {
                val phrase = phrases.getOrNull(currentPhraseIndex) ?: return@launch
                phase = DictationPlaybackPhase.SPEAKING_PHRASE
                speakPhraseAndWait(phrase)
                if (!isPlaying) {
                    phase = DictationPlaybackPhase.PAUSED
                }
            }
        }
    }

    fun rewindOneSentence() {
        if (phrases.isEmpty()) return
        val cur = phrases.getOrNull(currentPhraseIndex) ?: return
        val curStartOfSentence = phrases.indexOfFirst { it.sentenceIndex == cur.sentenceIndex }.coerceAtLeast(0)
        val targetIndex = if (currentPhraseIndex > curStartOfSentence) {
            curStartOfSentence
        } else {
            val prevSentence = (cur.sentenceIndex - 1).coerceAtLeast(0)
            phrases.indexOfFirst { it.sentenceIndex == prevSentence }.coerceAtLeast(0)
        }
        jumpToPhrase(targetIndex)
    }

    fun skipToNextPhrase() {
        if (phrases.isEmpty()) return
        if (currentPhraseIndex < phrases.lastIndex) {
            jumpToPhrase(currentPhraseIndex + 1)
        } else if (currentPassageIndex < passages.lastIndex) {
            selectPassage(currentPassageIndex + 1, autoStart = isPlaying)
        }
    }

    fun jumpToPhrase(targetIndex: Int) {
        if (phrases.isEmpty()) return
        val wasPlaying = isPlaying
        playbackJob?.cancel()
        playbackJob = null
        stopSpeakingOnly()
        writingPauseRemainingMs = 0L
        writingPauseProgress = 0f
        currentPhraseIndex = targetIndex.coerceIn(0, phrases.lastIndex)
        if (wasPlaying) {
            isPlaying = true
            launchPlaybackLoop()
        } else {
            phase = DictationPlaybackPhase.PAUSED
        }
    }

    fun selectPassage(index: Int, autoStart: Boolean = isPlaying) {
        if (passages.isEmpty()) return
        val clamped = index.coerceIn(0, passages.lastIndex)
        playbackJob?.cancel()
        playbackJob = null
        stopSpeakingOnly()
        isPlaying = false
        writingPauseTotalMs = 0L
        writingPauseRemainingMs = 0L
        writingPauseProgress = 0f
        currentPassageIndex = clamped
        rebuildPhrasesForCurrentPassage()
        phase = DictationPlaybackPhase.IDLE
        if (autoStart) {
            startOrResume()
        }
    }

    private fun launchPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            while (isPlaying) {
                val activePhrases = phrases
                if (activePhrases.isEmpty() || currentPhraseIndex >= activePhrases.size) {
                    if (config.autoAdvancePassages && currentPassageIndex < passages.lastIndex) {
                        if (config.elevatingEnabled &&
                            config.elevationInterval == DictationElevationInterval.PER_PASSAGE
                        ) {
                            config = config.copy(
                                targetWpm = DictationEngine.computeNextElevatedWpm(config.targetWpm, config)
                            )
                        }
                        currentPassageIndex++
                        rebuildPhrasesForCurrentPassage()
                        delay(800L)
                        continue
                    } else {
                        isPlaying = false
                        phase = DictationPlaybackPhase.COMPLETED
                        abandonAudioFocus()
                        flushUnloggedStudySeconds()
                        break
                    }
                }

                val phrase = activePhrases[currentPhraseIndex]
                phase = DictationPlaybackPhase.SPEAKING_PHRASE
                val speakStartMs = System.currentTimeMillis()
                speakPhraseAndWait(phrase)
                val speakElapsedMs = (System.currentTimeMillis() - speakStartMs).coerceAtLeast(0L)
                advanceActiveTimer((speakElapsedMs / 1000L))

                if (!isPlaying) break

                phase = DictationPlaybackPhase.WRITING_PAUSE
                val initialPauseMs = DictationEngine.computeWritingPauseMs(phrase, config)
                writingPauseTotalMs = initialPauseMs
                writingPauseRemainingMs = initialPauseMs
                writingPauseProgress = 1f

                var elapsedPauseMs = 0L
                val tickStepMs = 100L
                var secondSubBucketMs = 0L
                while (isPlaying) {
                    val liveTargetPauseMs = DictationEngine.computeWritingPauseMs(phrase, config)
                    writingPauseTotalMs = liveTargetPauseMs
                    val remaining = (liveTargetPauseMs - elapsedPauseMs).coerceAtLeast(0L)
                    writingPauseRemainingMs = remaining
                    writingPauseProgress = if (liveTargetPauseMs > 0L) {
                        (remaining.toFloat() / liveTargetPauseMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    if (remaining <= 0L) break
                    val step = minOf(tickStepMs, remaining)
                    delay(step)
                    elapsedPauseMs += step
                    secondSubBucketMs += step
                    if (secondSubBucketMs >= 1000L) {
                        val wholeSecs = secondSubBucketMs / 1000L
                        secondSubBucketMs %= 1000L
                        advanceActiveTimer(wholeSecs)
                    }
                }

                writingPauseRemainingMs = 0L
                writingPauseProgress = 0f

                if (!isPlaying) break
                currentPhraseIndex++
            }
        }
    }

    private suspend fun speakPhraseAndWait(phrase: DictationPhrase) {
        val expectedMs = DictationEngine.estimateExpectedSpeechMs(phrase, config)
        val engine = tts
        if (engine == null || !ttsReady) {
            // Graceful fallback when running in environments without an active TTS voice (e.g. headless/muted fallback)
            delay(expectedMs)
            return
        }

        val utteranceId = "dict_${UUID.randomUUID()}"
        val doneSignal = CompletableDeferred<Unit>()
        pendingUtteranceId = utteranceId
        pendingUtteranceSignal = doneSignal

        try {
            val locale = VocabularyTtsSpeaker.resolveLocale(language, englishVariant)
            val support = engine.isLanguageAvailable(locale)
            if (support >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = locale
            } else {
                val base = Locale(locale.language)
                engine.language = if (engine.isLanguageAvailable(base) >= TextToSpeech.LANG_AVAILABLE) {
                    base
                } else {
                    Locale.US
                }
            }
            engine.setSpeechRate(config.speechBurstRate)
            engine.speak(phrase.spokenText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (_: Exception) {
            pendingUtteranceSignal?.complete(Unit)
        }

        // Watchdog timeout ensures silent OEM callback drops never stall dictation permanently
        withTimeoutOrNull(expectedMs + 4500L) {
            doneSignal.await()
        }
        if (pendingUtteranceId == utteranceId) {
            pendingUtteranceId = null
            pendingUtteranceSignal = null
        }
    }

    private fun advanceActiveTimer(secondsToAdd: Long) {
        if (secondsToAdd <= 0L) return
        sessionActiveSeconds += secondsToAdd
        unloggedActiveSecondsAccumulator += secondsToAdd
        if (unloggedActiveSecondsAccumulator >= 10L) {
            flushUnloggedStudySeconds()
        }
        if (config.elevatingEnabled && !config.elevationInterval.isPerPassage) {
            secondsIntoCurrentElevationBucket += secondsToAdd.toInt()
            val threshold = config.elevationInterval.intervalSeconds
            if (threshold > 0 && secondsIntoCurrentElevationBucket >= threshold) {
                secondsIntoCurrentElevationBucket %= threshold
                config = config.copy(
                    targetWpm = DictationEngine.computeNextElevatedWpm(config.targetWpm, config)
                )
            }
        }
    }

    private fun flushUnloggedStudySeconds() {
        val delta = unloggedActiveSecondsAccumulator
        if (delta > 0L) {
            unloggedActiveSecondsAccumulator = 0L
            onLogStudySeconds(delta)
        }
    }

    private fun stopSpeakingOnly() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        pendingUtteranceId = null
        pendingUtteranceSignal?.complete(Unit)
        pendingUtteranceSignal = null
    }

    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                activeFocusRequest = req
                manager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
                activeFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(audioFocusListener)
            }
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        flushUnloggedStudySeconds()
        abandonAudioFocus()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
            ttsReady = false
            pendingUtteranceSignal?.complete(Unit)
            pendingUtteranceSignal = null
            scope.cancel()
        }
    }
}

@Composable
fun rememberDictationTtsController(
    passages: List<DictationPassageItem>,
    language: String,
    preferredEnglishVariant: String,
    onLogStudySeconds: (Long) -> Unit
): DictationTtsController {
    val context = LocalContext.current
    val controller = remember(context, passages) {
        DictationTtsController(
            context = context,
            initialPassages = passages,
            initialLanguage = language,
            initialEnglishVariant = preferredEnglishVariant,
            onLogStudySeconds = onLogStudySeconds
        )
    }
    DisposableEffect(controller) {
        onDispose {
            controller.shutdown()
        }
    }
    return controller
}
