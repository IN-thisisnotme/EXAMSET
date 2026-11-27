package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class VoiceLanguageHelperTest {

    @Test
    fun resolveBcp47Tag_mapsPresetsEnglishVariantsCustomTagsAndDeviceDefault() {
        assertEquals("hi-IN", VoiceLanguageHelper.resolveBcp47Tag("Hindi"))
        assertEquals("es-ES", VoiceLanguageHelper.resolveBcp47Tag("Spanish"))
        assertEquals("ja-JP", VoiceLanguageHelper.resolveBcp47Tag("Japanese"))
        assertEquals("ar-SA", VoiceLanguageHelper.resolveBcp47Tag("Arabic"))
        assertEquals("bn-IN", VoiceLanguageHelper.resolveBcp47Tag("Bangla"))

        // English variants
        assertEquals("en-US", VoiceLanguageHelper.resolveBcp47Tag("English", preferredEnglishVariant = "US"))
        assertEquals("en-GB", VoiceLanguageHelper.resolveBcp47Tag("English", preferredEnglishVariant = "UK"))
        assertEquals("en-IN", VoiceLanguageHelper.resolveBcp47Tag("English", preferredEnglishVariant = "IN"))
        assertEquals("en-GB", VoiceLanguageHelper.resolveBcp47Tag("English (UK)", preferredEnglishVariant = "US"))

        // Direct BCP-47 tag entry
        assertEquals("fr-CA", VoiceLanguageHelper.resolveBcp47Tag("fr-CA"))

        // Device default
        assertEquals(
            "de-AT",
            VoiceLanguageHelper.resolveBcp47Tag(
                language = VoiceLanguageHelper.DEVICE_DEFAULT_LANGUAGE,
                deviceDefaultLocale = Locale.forLanguageTag("de-AT")
            )
        )
    }

    @Test
    fun buildLanguageChoices_prioritizesDetectedPromptLanguageLibraryAndPresetsWithoutDuplicates() {
        val choices = VoiceLanguageHelper.buildLanguageChoices(
            detectedPromptLanguage = "Hindi",
            libraryLanguages = listOf("Hindi", "Esperanto", "French")
        )
        assertEquals("Hindi", choices.first())
        assertTrue(choices.contains("Esperanto"))
        assertTrue(choices.contains("French"))
        assertEquals(VoiceLanguageHelper.DEVICE_DEFAULT_LANGUAGE, choices.last())
        assertEquals(choices.distinct().size, choices.size)
    }

    @Test
    fun formatCompactLanguageBadge_formatsEnglishAndNonEnglishCleanly() {
        assertEquals("English (UK)", VoiceLanguageHelper.formatCompactLanguageBadge("English", "UK"))
        assertEquals("Hindi (hi-IN)", VoiceLanguageHelper.formatCompactLanguageBadge("Hindi", "US"))
    }
}
