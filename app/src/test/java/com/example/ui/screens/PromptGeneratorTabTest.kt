package com.example.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamSetTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import androidx.compose.ui.test.performTextInput
import com.example.model.KnowledgeCollectionType
import com.example.model.KnowledgeFormatRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class PromptGeneratorTabTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun promptGeneratorTab_rendersAllSectionsWithoutBlankGapsAndUpdatesDynamicControls() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = true) {
                PromptGeneratorTab(
                    viewModel = viewModel,
                    onNavigateToCreator = {}
                )
            }
        }

        val list = composeTestRule.onNodeWithTag("prompt_lazy_column")

        // Header card & Exam parameters visible
        composeTestRule.onNodeWithText("AI Prompt Builder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("prompt_header_copy_button").assertIsDisplayed()
        list.performScrollToNode(hasText("EXAMINATION PARAMETERS"))
        composeTestRule.onNodeWithText("EXAMINATION PARAMETERS").assertIsDisplayed()

        // Scroll to Question Volume & Pattern card and verify Difficulty, Format, and Option Count
        list.performScrollToNode(hasText("QUESTION VOLUME & PATTERN"))
        composeTestRule.onNodeWithText("QUESTION VOLUME & PATTERN").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Difficulty Level").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Question Format & Pattern").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Option Count per Question").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("5 Options (A–E)").performScrollTo().performClick()

        // Language Settings card
        list.performScrollToNode(hasText("LANGUAGE SETTINGS"))
        composeTestRule.onNodeWithText("LANGUAGE SETTINGS").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Explanation Language").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Active: English (same language as questions)").performScrollTo().assertIsDisplayed()

        // Output Format & Reasoning card reflects 5-Option dynamically
        list.performScrollToNode(hasText("OUTPUT FORMAT & REASONING"))
        composeTestRule.onNodeWithText("JSON Schema (5-Option)").performScrollTo().assertIsDisplayed()

        // Truncation Guard toggle updates prompt badge and prompt content
        composeTestRule.onNodeWithText("Format & Quantity Truncation Guard").performScrollTo().performClick()

        list.performScrollToNode(hasText("CONSTRUCTED AI PROMPT"))
        composeTestRule.onNodeWithText("Guard Active").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun promptGeneratorTab_knowledgeSetModeRendersCardsAndOpensStudioWithConfiguredRule() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)

        var capturedTitle = ""
        var capturedSubject = ""
        var capturedType: KnowledgeCollectionType? = null
        var capturedRule: KnowledgeFormatRule? = null

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = true) {
                PromptGeneratorTab(
                    viewModel = viewModel,
                    onNavigateToCreator = {},
                    onOpenKnowledgeStudio = { title, subject, type, rule ->
                        capturedTitle = title
                        capturedSubject = subject
                        capturedType = type
                        capturedRule = rule
                    }
                )
            }
        }

        // Switch to Knowledge Set mode
        composeTestRule.onNodeWithTag("prompt_mode_knowledge_set").performClick()
        composeTestRule.waitForIdle()

        val list = composeTestRule.onNodeWithTag("prompt_lazy_column")

        list.performScrollToNode(hasText("STUDY SCOPE & TOPIC"))
        composeTestRule.onNodeWithText("STUDY SCOPE & TOPIC").assertIsDisplayed()

        composeTestRule.onNodeWithTag("knowledge_prompt_subject_input").performScrollTo().performTextInput("Physics")
        composeTestRule.onNodeWithTag("knowledge_prompt_topic_input").performScrollTo().performTextInput("Laws of Thermodynamics")

        list.performScrollToNode(hasText("COLLECTION FORMAT & DEPTH"))
        composeTestRule.onNodeWithText("COLLECTION FORMAT & DEPTH").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("knowledge_type_formula_sheet").performScrollTo().performClick()

        list.performScrollToNode(hasText("PARSER RULES & FORMAT SAMPLE"))
        composeTestRule.onNodeWithText("PARSER RULES & FORMAT SAMPLE").performScrollTo().assertIsDisplayed()

        list.performScrollToNode(hasText("CONSTRUCTED KNOWLEDGE PROMPT"))
        composeTestRule.onNodeWithText("CONSTRUCTED KNOWLEDGE PROMPT").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("FORMULAS • 20 Items").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("knowledge_prompt_open_studio_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("Laws of Thermodynamics", capturedTitle)
        assertEquals("Physics", capturedSubject)
        assertEquals(KnowledgeCollectionType.FORMULA_SHEET, capturedType)
        assertEquals(KnowledgeCollectionType.FORMULA_SHEET, capturedRule?.collectionType)
    }

    @Test
    fun promptGeneratorTab_flashcardModeRendersCardsAndOpensFlashcardStudio() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)

        var capturedTitle = ""
        var capturedSubject = ""
        var capturedType: KnowledgeCollectionType? = null
        var capturedRule: KnowledgeFormatRule? = null

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = true) {
                PromptGeneratorTab(
                    viewModel = viewModel,
                    onNavigateToCreator = {},
                    onOpenKnowledgeStudio = { title, subject, type, rule ->
                        capturedTitle = title
                        capturedSubject = subject
                        capturedType = type
                        capturedRule = rule
                    }
                )
            }
        }

        // Switch to Flashcard Set mode
        composeTestRule.onNodeWithTag("prompt_mode_flashcard_set").performClick()
        composeTestRule.waitForIdle()

        val list = composeTestRule.onNodeWithTag("prompt_lazy_column")

        list.performScrollToNode(hasText("FLASHCARD DECK SCOPE"))
        composeTestRule.onNodeWithTag("flashcard_prompt_subject_input").performScrollTo().performTextInput("Chemistry")
        composeTestRule.onNodeWithTag("flashcard_prompt_topic_input").performScrollTo().performTextInput("Chemical Kinetics")

        list.performScrollToNode(hasText("CARD PATTERN & VOLUME"))
        composeTestRule.onNodeWithTag("flashcard_style_formula_trap").performScrollTo().performClick()

        list.performScrollToNode(hasText("CONSTRUCTED FLASHCARD PROMPT"))
        composeTestRule.onNodeWithText("FORMULA SRS • 20 Cards").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("flashcard_prompt_open_studio_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("Chemical Kinetics", capturedTitle)
        assertEquals("Chemistry", capturedSubject)
        assertEquals(KnowledgeCollectionType.FLASHCARDS, capturedType)
        assertEquals(KnowledgeCollectionType.FLASHCARDS, capturedRule?.collectionType)
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
    fun promptGeneratorTab_savesPresetClearsWithUndoAndCollapsesSettingsOnSelect() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("examset_app_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val viewModel = MainViewModel(app)

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = true) {
                PromptGeneratorTab(
                    viewModel = viewModel,
                    onNavigateToCreator = {}
                )
            }
        }

        // Fill exam & subject then save a preset
        composeTestRule.onNodeWithTag("prompt_exam_input").performScrollTo().performTextInput("UPSC Prelims")
        composeTestRule.onNodeWithTag("prompt_subject_input").performScrollTo().performTextInput("Polity")
        composeTestRule.onNodeWithTag("prompt_preset_save_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("prompt_preset_dialog_confirm_save").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, viewModel.promptPresets.value.size)

        // Clear resets fields & shows Undo banner without deleting saved preset
        composeTestRule.onNodeWithTag("prompt_preset_clear_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("", viewModel.questionPromptState.examType)
        assertEquals(1, viewModel.promptPresets.value.size)
        composeTestRule.onNodeWithTag("prompt_preset_undo_clear_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals("UPSC Prelims", viewModel.questionPromptState.examType)

        // Open preset dropdown sheet & select saved preset -> auto-compacts advanced settings
        val savedId = viewModel.promptPresets.value.first().id
        composeTestRule.onNodeWithTag("prompt_preset_dropdown_trigger").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("prompt_preset_item_$savedId").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("question_expand_settings_card").performScrollTo().assertIsDisplayed()
    }
}

