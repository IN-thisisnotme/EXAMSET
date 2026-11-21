package com.example.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.StudyNoteEntity
import com.example.model.KnowledgeCollectionType
import com.example.ui.theme.ExamSetTheme
import com.example.util.DictationEngine
import com.example.util.DictationPlaybackPhase
import com.example.util.DictationPunctuationMode
import com.example.util.DictationTtsController
import com.example.util.StudyNoteParser
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class NotebookDictationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun notebookDictationScreen_supportsWpmSwitchConcealedProofreadAndElevatingControls() {
        val sampleNote = StudyNoteEntity(
            id = 10L,
            title = "Polity Revision Notes",
            subject = "Indian Polity",
            collectionType = KnowledgeCollectionType.ONE_LINERS.code,
            rawContent = """
                ## Fundamental Rights
                1. **Article 14** -> Equality before law and equal protection of laws within India.
                2. **Article 21** -> Protection of life and personal liberty according to procedure established by law.
            """.trimIndent()
        )
        val blocks = StudyNoteParser.parseBlocks(sampleNote.rawContent)
        var dismissed = false

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = true) {
                NotebookDictationScreen(
                    note = sampleNote,
                    blocks = blocks,
                    preferredEnglishVariant = "US",
                    onLogStudySeconds = {},
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("notebook_dictation_screen").assertIsDisplayed()
        // Default mode conceals full text so learner writes from audio
        composeTestRule.onNodeWithText("Concealed").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dictation_play_pause_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dictation_repeat_phrase_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dictation_rewind_sentence_btn").assertIsDisplayed()

        // Default speed is 45 WPM; test live +5 and -5 WPM switch
        composeTestRule.onNodeWithTag("dictation_current_wpm_label").assertTextEquals("45 WPM")
        composeTestRule.onNodeWithTag("dictation_wpm_plus_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dictation_current_wpm_label").assertTextEquals("50 WPM")

        composeTestRule.onNodeWithTag("dictation_wpm_minus_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dictation_current_wpm_label").assertTextEquals("45 WPM")

        // Toggle Proofread mode to inspect script
        composeTestRule.onNodeWithTag("dictation_proofread_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Proofread").assertIsDisplayed()
        composeTestRule.onNodeWithText("PROOFREAD SCRIPT (Tap any phrase to jump audio)").assertIsDisplayed()

        // Toggle Elevating Speed Mode
        composeTestRule.onNodeWithTag("dictation_elevating_switch").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("35 WPM").assertIsDisplayed()
        composeTestRule.onNodeWithText("30 → 60 WPM").assertIsDisplayed()

        // Verify 3-way Punctuation Mode selector
        composeTestRule.onNodeWithTag("dictation_punctuation_mode_verbatim").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(DictationPunctuationMode.FULL_VERBATIM.summary).assertIsDisplayed()

        // Exit screen triggers onDismiss cleanly
        composeTestRule.onNodeWithTag("dictation_back_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun dictationTtsController_handlesPhraseNavigationPunctuationAndShutdownSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = """
            1. **First Law** -> Energy cannot be created or destroyed. It only transforms between forms.
            2. **Second Law** -> Entropy of an isolated system always increases over time.
        """.trimIndent()
        val passages = DictationEngine.extractPassages(StudyNoteParser.parseBlocks(raw))
        val controller = DictationTtsController(
            context = context,
            initialPassages = passages,
            initialLanguage = "English",
            initialEnglishVariant = "UK",
            onLogStudySeconds = {}
        )

        assertEquals(DictationPlaybackPhase.IDLE, controller.phase)
        assertTrue(controller.phrases.size >= 2)

        controller.adjustWpm(10)
        assertEquals(55, controller.config.targetWpm)
        controller.adjustWpm(-20)
        assertEquals(35, controller.config.targetWpm)

        controller.setSpeakPunctuationCues(true)
        assertTrue(controller.config.speakPunctuationCues)
        assertEquals(DictationPunctuationMode.ESSENTIAL, controller.config.effectivePunctuationMode)
        assertTrue(controller.phrases.any { it.spokenText.contains("full stop") })

        controller.setPunctuationMode(DictationPunctuationMode.FULL_VERBATIM)
        assertEquals(DictationPunctuationMode.FULL_VERBATIM, controller.config.effectivePunctuationMode)
        assertTrue(controller.config.speakPunctuationCues)

        controller.skipToNextPhrase()
        assertEquals(1, controller.currentPhraseIndex)
        controller.rewindOneSentence()
        assertEquals(0, controller.currentPhraseIndex)

        controller.selectPassage(1, autoStart = false)
        assertEquals(1, controller.currentPassageIndex)
        assertEquals(0, controller.currentPhraseIndex)

        controller.updateLanguage("Hindi", "US")
        assertEquals("Hindi", controller.language)

        controller.shutdown()
        assertFalse(controller.isPlaying)
    }
}
