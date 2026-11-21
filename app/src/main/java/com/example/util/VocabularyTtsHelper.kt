package com.example.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Stable
class VocabularyTtsSpeaker(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isInitialized by mutableStateOf(false)
    private var pendingText: String? = null
    private var pendingLocale: Locale? = null

    private var pendingSpeechRate: Float = 1.0f

    var isSpeaking by mutableStateOf(false)
        private set

    var activeUtteranceKey by mutableStateOf<String?>(null)
        private set

    init {
        try {
            val engine = TextToSpeech(appContext) { status ->
                isInitialized = (status == TextToSpeech.SUCCESS)
                if (isInitialized) {
                    val queuedText = pendingText
                    val queuedLocale = pendingLocale
                    val queuedRate = pendingSpeechRate
                    pendingText = null
                    pendingLocale = null
                    pendingSpeechRate = 1.0f
                    if (!queuedText.isNullOrBlank()) {
                        speakInternal(queuedText, queuedLocale ?: Locale.ENGLISH, activeUtteranceKey, queuedRate)
                    }
                }
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        this@VocabularyTtsSpeaker.isSpeaking = true
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        this@VocabularyTtsSpeaker.isSpeaking = false
                        if (this@VocabularyTtsSpeaker.activeUtteranceKey == utteranceId) {
                            this@VocabularyTtsSpeaker.activeUtteranceKey = null
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        this@VocabularyTtsSpeaker.isSpeaking = false
                        if (this@VocabularyTtsSpeaker.activeUtteranceKey == utteranceId) {
                            this@VocabularyTtsSpeaker.activeUtteranceKey = null
                        }
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post {
                        this@VocabularyTtsSpeaker.isSpeaking = false
                        if (this@VocabularyTtsSpeaker.activeUtteranceKey == utteranceId) {
                            this@VocabularyTtsSpeaker.activeUtteranceKey = null
                        }
                    }
                }
            })
            tts = engine
        } catch (_: Exception) {
            isInitialized = false
            tts = null
        }
    }

    fun speak(
        text: String,
        language: String,
        preferredEnglishVariant: String = "US",
        utteranceKey: String? = null,
        speechRate: Float = 1.0f
    ) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        val targetLocale = resolveLocale(language, preferredEnglishVariant)
        val safeRate = speechRate.coerceIn(0.75f, 1.5f)
        activeUtteranceKey = utteranceKey ?: cleanText
        if (!isInitialized) {
            pendingText = cleanText
            pendingLocale = targetLocale
            pendingSpeechRate = safeRate
            return
        }
        speakInternal(cleanText, targetLocale, activeUtteranceKey, safeRate)
    }

    private fun speakInternal(text: String, locale: Locale, utteranceId: String?, speechRate: Float) {
        val engine = tts ?: return
        try {
            val langSupport = engine.isLanguageAvailable(locale)
            if (langSupport >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = locale
            } else {
                val baseLang = Locale(locale.language)
                if (engine.isLanguageAvailable(baseLang) >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = baseLang
                } else {
                    engine.language = Locale.US
                }
            }
            engine.setSpeechRate(speechRate)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId ?: "vocab_tts")
        } catch (_: Exception) {
            isSpeaking = false
            activeUtteranceKey = null
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        } finally {
            isSpeaking = false
            activeUtteranceKey = null
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
            isInitialized = false
            isSpeaking = false
            activeUtteranceKey = null
        }
    }

    companion object {
        fun resolveLocale(language: String, preferredEnglishVariant: String = "US"): Locale {
            val norm = language.trim().lowercase(Locale.ROOT)
            return when {
                norm.startsWith("english") || norm == "en" -> when (preferredEnglishVariant.trim().uppercase(Locale.ROOT)) {
                    "UK", "GB", "BRITISH" -> Locale.UK
                    "IN", "INDIAN" -> Locale("en", "IN")
                    else -> Locale.US
                }
                norm.contains("hindi") || norm == "hi" -> Locale("hi", "IN")
                norm.contains("japanese") || norm == "ja" -> Locale.JAPANESE
                norm.contains("spanish") || norm == "es" -> Locale("es", "ES")
                norm.contains("french") || norm == "fr" -> Locale.FRENCH
                norm.contains("german") || norm == "de" -> Locale.GERMAN
                norm.contains("korean") || norm == "ko" -> Locale.KOREAN
                norm.contains("chinese") || norm.contains("mandarin") || norm == "zh" -> Locale.SIMPLIFIED_CHINESE
                norm.contains("arabic") || norm == "ar" -> Locale("ar")
                norm.contains("portuguese") || norm == "pt" -> Locale("pt", "BR")
                norm.contains("russian") || norm == "ru" -> Locale("ru", "RU")
                norm.contains("italian") || norm == "it" -> Locale.ITALIAN
                norm.contains("sanskrit") || norm == "sa" -> Locale("sa", "IN")
                norm.contains("marathi") || norm == "mr" -> Locale("mr", "IN")
                norm.contains("tamil") || norm == "ta" -> Locale("ta", "IN")
                norm.contains("telugu") || norm == "te" -> Locale("te", "IN")
                norm.contains("bengali") || norm == "bn" -> Locale("bn", "IN")
                norm.contains("gujarati") || norm == "gu" -> Locale("gu", "IN")
                norm.contains("punjabi") || norm == "pa" -> Locale("pa", "IN")
                norm.contains("urdu") || norm == "ur" -> Locale("ur", "IN")
                else -> Locale.ENGLISH
            }
        }
    }
}

@Composable
fun rememberVocabularyTtsSpeaker(): VocabularyTtsSpeaker {
    val context = LocalContext.current
    val speaker = remember(context) { VocabularyTtsSpeaker(context) }
    DisposableEffect(speaker) {
        onDispose {
            speaker.shutdown()
        }
    }
    return speaker
}
