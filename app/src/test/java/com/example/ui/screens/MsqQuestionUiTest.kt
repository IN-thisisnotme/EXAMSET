package com.example.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.example.model.ExamConfig
import com.example.model.McqQuestion
import com.example.model.MsqScoringPolicy
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.ui.components.EditMetadataDialog
import com.example.ui.components.StudentPracticeCard
import com.example.ui.theme.ExamSetTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class MsqQuestionUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun singleChoiceMcq_preservesOneTapSwitchingAndDoesNotShowMsqBadge() {
        var selected by mutableStateOf<String?>(null)
        val upscSingleMcq = McqQuestion(
            num = 1,
            question = "Consider the following statements:\n1. Statement one.\n2. Statement two.\nWhich of the statements given above is/are correct?",
            options = QuestionOptions(
                a = "1 only",
                b = "2 only",
                c = "Both 1 and 2",
                d = "Neither 1 nor 2"
            ),
            correctAnswer = "B"
        )

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = false) {
                StudentPracticeCard(
                    question = upscSingleMcq,
                    selectedAnswer = selected,
                    onSelectAnswer = { selected = it },
                    showResult = false
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("msq_question_badge_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("msq_clear_button_1").assertDoesNotExist()

        composeTestRule.onNodeWithTag("practice_option_A_q1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("A", selected)

        composeTestRule.onNodeWithTag("practice_option_C_q1").performClick()
        composeTestRule.waitForIdle()
        assertEquals("C", selected)
    }

    @Test
    fun msqCard_highlightsMsqBadgeTogglesMultiOptionsClearsAndShowsPartialFeedback() {
        var selected by mutableStateOf("")
        var showResult by mutableStateOf(false)
        val msqQuestion = McqQuestion(
            num = 2,
            question = "Which of the following are prime numbers?",
            options = QuestionOptions(
                a = "2",
                b = "4",
                c = "5",
                d = "9"
            ),
            correctAnswer = "A,C",
            questionType = "MSQ"
        )

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = false) {
                StudentPracticeCard(
                    question = msqQuestion,
                    selectedAnswer = selected,
                    onSelectAnswer = { selected = it },
                    showResult = showResult,
                    positiveMarks = 4.0,
                    negativeMarks = 1.0,
                    msqScoringPolicy = MsqScoringPolicy.PARTIAL_STRICT
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("msq_question_badge_2").assertIsDisplayed()
        composeTestRule.onNodeWithText("MSQ • Select all that apply").assertIsDisplayed()

        // Toggle A then C -> accumulates "A,C"
        composeTestRule.onNodeWithTag("practice_option_A_q2").performClick()
        composeTestRule.waitForIdle()
        assertEquals("A", selected)

        composeTestRule.onNodeWithTag("practice_option_C_q2").performClick()
        composeTestRule.waitForIdle()
        assertEquals("A,C", selected)

        // Clear button resets selection
        composeTestRule.onNodeWithTag("msq_clear_button_2").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals("", selected)

        // Pick only A then reveal result under PARTIAL_STRICT -> ~ Partial (+2.00) and missed C hint
        composeTestRule.onNodeWithTag("practice_option_A_q2").performClick()
        composeTestRule.waitForIdle()
        showResult = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("practice_result_badge_2").assertIsDisplayed()
        composeTestRule.onNodeWithText("~ Partial (+2.00)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Selected • Correct").assertIsDisplayed()
        composeTestRule.onNodeWithText("Correct (Missed)").assertIsDisplayed()
    }

    @Test
    fun msqCard_adaptsCleanlyAtHighSystemFontScale() {
        var selected by mutableStateOf("A")
        val msqQuestion = McqQuestion(
            num = 7,
            question = "Select all renewable energy sources from the choices listed below.",
            options = QuestionOptions(
                a = "Solar photovoltaic",
                b = "Anthracite coal",
                c = "Offshore wind",
                d = "Natural gas"
            ),
            correctAnswer = "A,C",
            questionType = "MSQ"
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.6f)) {
                ExamSetTheme(darkTheme = false) {
                    StudentPracticeCard(
                        question = msqQuestion,
                        selectedAnswer = selected,
                        onSelectAnswer = { selected = it },
                        showResult = false
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("msq_question_badge_7").assertIsDisplayed()
        composeTestRule.onNodeWithTag("msq_clear_button_7").assertIsDisplayed()
        composeTestRule.onNodeWithTag("practice_option_C_q7").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals("A,C", selected)
    }

    @Test
    fun editMetadataDialog_adaptsCleanlyAtHighSystemFontScale() {
        var savedConfig by mutableStateOf<ExamConfig?>(null)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.6f)) {
                ExamSetTheme(darkTheme = false) {
                    EditMetadataDialog(
                        initialMetadata = PaperMetadata(
                            id = "paper-7",
                            title = "Large Font MSQ Test",
                            subject = "Physics",
                            msqScoringPolicy = MsqScoringPolicy.ALL_OR_NOTHING
                        ),
                        initialConfig = ExamConfig(msqScoringPolicy = MsqScoringPolicy.ALL_OR_NOTHING),
                        onSave = { _, _, _, _, _, _, _ -> },
                        onSaveConfig = { savedConfig = it },
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_msq_policy_partial_strict")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("top_save_metadata_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(MsqScoringPolicy.PARTIAL_STRICT, savedConfig?.msqScoringPolicy)
    }
}
