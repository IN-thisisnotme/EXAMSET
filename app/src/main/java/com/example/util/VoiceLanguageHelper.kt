package com.example.util

import com.example.model.VocabularyLanguageVariants
import java.util.Locale

object VoiceLanguageHelper {

    const val DEVICE_DEFAULT_LANGUAGE = "Device Default"

    val ENGLISH_SPEECH_VARIANTS: List<Pair<String, String>> = listOf(
        "US" to "US (en-US)",
        "UK" to "UK (en-GB)",
        "IN" to "India (en-IN)"
    )

    private val LANGUAGE_TO_BCP47: Map<String, String> = mapOf(
        "hindi" to "hi-IN",
        "bengali" to "bn-IN",
        "bangla" to "bn-IN",
        "marathi" to "mr-IN",
        "telugu" to "te-IN",
        "tamil" to "ta-IN",
        "gujarati" to "gu-IN",
        "urdu" to "ur-IN",
        "kannada" to "kn-IN",
        "malayalam" to "ml-IN",
        "odia" to "or-IN",
        "oriya" to "or-IN",
        "punjabi" to "pa-IN",
        "gurmukhi" to "pa-IN",
        "assamese" to "as-IN",
        "sanskrit" to "sa-IN",
        "nepali" to "ne-NP",
        "maithili" to "mai-IN",
        "konkani" to "kok-IN",
        "kashmiri" to "ks-IN",
        "sindhi" to "sd-IN",
        "dogri" to "doi-IN",
        "bodo" to "brx-IN",
        "santali" to "sat-IN",
        "manipuri" to "mni-IN",
        "spanish" to "es-ES",
        "french" to "fr-FR",
        "german" to "de-DE",
        "portuguese" to "pt-BR",
        "italian" to "it-IT",
        "russian" to "ru-RU",
        "arabic" to "ar-SA",
        "persian" to "fa-IR",
        "farsi" to "fa-IR",
        "japanese" to "ja-JP",
        "korean" to "ko-KR",
        "chinese" to "zh-CN",
        "mandarin" to "zh-CN",
        "cantonese" to "yue-Hant-HK",
        "turkish" to "tr-TR",
        "indonesian" to "id-ID",
        "malay" to "ms-MY",
        "vietnamese" to "vi-VN",
        "thai" to "th-TH",
        "dutch" to "nl-NL",
        "polish" to "pl-PL",
        "swedish" to "sv-SE",
        "norwegian" to "nb-NO",
        "danish" to "da-DK",
        "finnish" to "fi-FI",
        "romanian" to "ro-RO",
        "czech" to "cs-CZ",
        "hungarian" to "hu-HU",
        "greek" to "el-GR",
        "ukrainian" to "uk-UA",
        "swahili" to "sw-KE",
        "hebrew" to "he-IL",
        "filipino" to "fil-PH",
        "tagalog" to "fil-PH",
        "bulgarian" to "bg-BG",
        "serbian" to "sr-RS",
        "sinhala" to "si-LK",
        "burmese" to "my-MM",
        "khmer" to "km-KH",
        "lao" to "lo-LA",
        "georgian" to "ka-GE",
        "armenian" to "hy-AM",
        "amharic" to "am-ET"
    )

    private val BCP47_TAG_REGEX = Regex("""^[a-zA-Z]{2,3}(?:-[a-zA-Z0-9]{2,8})*$""")

    fun buildLanguageChoices(
        detectedPromptLanguage: String,
        libraryLanguages: Collection<String> = emptyList()
    ): List<String> {
        val ordered = linkedSetOf<String>()
        if (detectedPromptLanguage.isNotBlank()) {
            ordered.add(normalizeLanguageDisplayName(detectedPromptLanguage))
        }
        ordered.addAll(PaperLanguageDetector.PRESET_LANGUAGES)
        libraryLanguages
            .map { normalizeLanguageDisplayName(it) }
            .filter { it.isNotBlank() && !it.equals(DEVICE_DEFAULT_LANGUAGE, ignoreCase = true) }
            .forEach { ordered.add(it) }
        ordered.add(DEVICE_DEFAULT_LANGUAGE)
        return ordered.toList()
    }

    fun normalizeLanguageDisplayName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "English"
        val presetMatch = PaperLanguageDetector.PRESET_LANGUAGES.firstOrNull {
            it.equals(trimmed, ignoreCase = true)
        }
        if (presetMatch != null) return presetMatch
        return trimmed
    }

    fun resolveEnglishVariantTag(variant: String): String {
        return when (variant.trim().uppercase(Locale.ROOT)) {
            "UK", "GB", "BRITISH" -> "en-GB"
            "IN", "INDIA", "INDIAN" -> "en-IN"
            "US", "USA", "AMERICAN" -> "en-US"
            else -> "en-US"
        }
    }

    private fun isEnglishLike(language: String): Boolean {
        if (VocabularyLanguageVariants.isEnglish(language)) return true
        val lower = language.trim().lowercase(Locale.ROOT)
        return lower == "english (uk)" ||
            lower == "english (us)" ||
            lower == "english (in)" ||
            lower == "english (gb)" ||
            lower.startsWith("english (") ||
            lower.startsWith("english ")
    }

    fun resolveBcp47Tag(
        language: String,
        preferredEnglishVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT,
        deviceDefaultLocale: Locale = Locale.getDefault()
    ): String {
        val clean = language.trim()
        if (clean.isBlank()) {
            return resolveEnglishVariantTag(preferredEnglishVariant)
        }
        if (clean.equals(DEVICE_DEFAULT_LANGUAGE, ignoreCase = true)) {
            return deviceDefaultLocale.toLanguageTag().ifBlank { "en-US" }
        }
        if (isEnglishLike(clean)) {
            val lower = clean.lowercase(Locale.ROOT)
            return when {
                lower.contains("uk") || lower.contains("gb") || lower.contains("british") -> "en-GB"
                lower.contains("us") || lower.contains("american") -> "en-US"
                lower.contains("india") || lower.contains("(in)") || lower.endsWith(" in") -> "en-IN"
                else -> resolveEnglishVariantTag(preferredEnglishVariant)
            }
        }
        val lower = clean.lowercase(Locale.ROOT)
        LANGUAGE_TO_BCP47[lower]?.let { return it }

        for ((key, tag) in LANGUAGE_TO_BCP47) {
            if (lower.contains(key)) return tag
        }

        if (BCP47_TAG_REGEX.matches(clean)) {
            val parsed = Locale.forLanguageTag(clean)
            if (parsed.language.isNotBlank()) {
                return parsed.toLanguageTag()
            }
        }

        return deviceDefaultLocale.toLanguageTag().ifBlank { "en-US" }
    }

    fun formatCompactLanguageBadge(
        language: String,
        preferredEnglishVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT
    ): String {
        val display = normalizeLanguageDisplayName(language)
        if (display.equals(DEVICE_DEFAULT_LANGUAGE, ignoreCase = true)) {
            return "System (${Locale.getDefault().language.uppercase(Locale.ROOT)})"
        }
        val tag = resolveBcp47Tag(display, preferredEnglishVariant)
        return if (isEnglishLike(display)) {
            val variantCode = when (tag) {
                "en-GB" -> "UK"
                "en-IN" -> "IN"
                else -> "US"
            }
            "English ($variantCode)"
        } else {
            "$display ($tag)"
        }
    }
}
