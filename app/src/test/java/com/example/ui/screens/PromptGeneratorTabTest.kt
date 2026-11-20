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
}
