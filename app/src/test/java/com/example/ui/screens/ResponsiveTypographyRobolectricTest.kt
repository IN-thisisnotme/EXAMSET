package com.example.ui.screens

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppFontScaleMode
import com.example.data.ExamSetPreferences
import com.example.data.PaperEntity
import com.example.model.ExamConfig
import com.example.model.McqQuestion
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import com.example.ui.ExamTimerMode
import com.example.ui.components.FontSizeSelectorDialog
import com.example.ui.components.SavedPapersDialog
import com.example.ui.theme.ExamSetTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ResponsiveTypographyRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildSamplePaper(): QuestionPaper = QuestionPaper(
        id = 101L,
        metadata = PaperMetadata(
            title = "UPSC Prelims Full Mock 2026",
            subject = "General Studies",
            topic = "Polity & Economy",
            setNumber = "SET-A"
        ),
        questions = listOf(
            McqQuestion(
                num = 1,
                question = "Which Article of the Constitution provides for the Finance Commission?",
                options = QuestionOptions(
                    a = "Article 275",
                    b = "Article 280",
                    c = "Article 300A",
                    d = "Article 324"
                ),
                correctAnswer = "B",
                explanation = "Article 280 establishes the Finance Commission every five years."
            ),
            McqQuestion(
                num = 2,
                question = "Which body regulates monetary policy in India?",
                options = QuestionOptions(
                    a = "SEBI",
                    b = "NITI Aayog",
                    c = "Reserve Bank of India",
                    d = "NABARD"
                ),
                correctAnswer = "C"
            )
        ),
        rawText = ""
    )

    private fun buildSamplePaperEntity(): PaperEntity = PaperEntity(
        id = 101L,
        title = "UPSC Prelims Full Mock 2026",
        subject = "General Studies",
        topic = "Polity & Economy",
        setNumber = "SET-A",
        exam = "UPSC CSE",
        questionCount = 2,
        answerKeyCount = 2,
        explanationCount = 1,
        rawText = "1. Sample\n(a) A\n(b) B\n(c) C\n(d) D\nAnswer: B",
        bookletNumber = "000101"
    )

    @Test
    fun preferences_defaultToFollowSystemAndPersistCbtFontScaleStep() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("examset_app_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = ExamSetPreferences(context)

        assertEquals(AppFontScaleMode.FOLLOW_SYSTEM, prefs.getFontScaleMode())
        assertEquals(0, prefs.getCbtFontScaleStep())

        prefs.setCbtFontScaleStep(1)
        assertEquals(1, prefs.getCbtFontScaleStep())

        prefs.setCbtFontScaleStep(-1)
        assertEquals(-1, prefs.getCbtFontScaleStep())

        // Out-of-range values should safely clamp to [-1, 1]
        prefs.setCbtFontScaleStep(5)
        assertEquals(1, prefs.getCbtFontScaleStep())
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-port", sdk = [36])
    fun fontSizeSelectorDialog_rendersFollowSystemAnd48dpDoneAt160PercentScaleOnCompactPortrait() {
        composeTestRule.setContent {
            ExamSetTheme(darkTheme = false) {
                val baseDensity = LocalDensity.current
                val updatedConfig = Configuration(LocalConfiguration.current).apply {
                    fontScale = 1.6f
                }
                CompositionLocalProvider(
                    LocalConfiguration provides updatedConfig,
                    LocalDensity provides Density(density = baseDensity.density, fontScale = 1.6f)
                ) {
                    FontSizeSelectorDialog(
                        currentMode = AppFontScaleMode.FOLLOW_SYSTEM,
                        onModeSelected = {},
                        onDismissRequest = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Typography & Scaling").assertIsDisplayed()
        composeTestRule.onNodeWithTag("font_mode_follow_system")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Adapts automatically with Android OS font size settings (0.85x–1.60x).")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_font_size_dialog_button")
            .performScrollTo()
            .assertIsDisplayed()

        val doneBounds = composeTestRule
            .onNodeWithTag("close_font_size_dialog_button")
            .fetchSemanticsNode()
            .boundsInRoot
        with(composeTestRule.density) {
            assertTrue(
                "Done button touch target must be >= 48dp",
                doneBounds.height.toDp() >= 47.dp
            )
        }
    }

    @Test
    @Config(qualifiers = "w720dp-h360dp-land", sdk = [36])
    fun savedPapersDialog_fitsLandscapePhoneWithAccessibleHeaderActions() {
        val sampleEntity = buildSamplePaperEntity()
        composeTestRule.setContent {
            ExamSetTheme(darkTheme = false) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = baseDensity.density, fontScale = 1.35f)
                ) {
                    SavedPapersDialog(
                        papers = listOf(sampleEntity),
                        viewModel = null,
                        onSelectPaper = {},
                        onDeletePaper = {},
                        onEditPaper = null,
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Saved Question Sets").assertIsDisplayed()
        composeTestRule.onNodeWithText("UPSC Prelims Full Mock 2026").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()

        val closeBounds = composeTestRule
            .onNodeWithContentDescription("Close")
            .fetchSemanticsNode()
            .boundsInRoot
        with(composeTestRule.density) {
            assertTrue(
                "Close button touch target must be >= 48dp",
                closeBounds.height.toDp() >= 47.dp && closeBounds.width.toDp() >= 47.dp
            )
        }
    }

    @Test
    fun printablePaperTab_showsAllFourActionButtonsAcrossCompactLandscapeAndTabletAtHighFontScale() {
        val paper = buildSamplePaper()
        val fontScales = listOf(0.85f, 1.0f, 1.35f, 1.6f)
        val viewports = listOf(
            360.dp to 640.dp,
            720.dp to 360.dp,
            960.dp to 720.dp
        )

        var selectedViewport by mutableStateOf(viewports.first())
        var activeFontScale by mutableStateOf(fontScales.last())

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = false) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = baseDensity.density, fontScale = activeFontScale)
                ) {
                    Box(modifier = Modifier.size(selectedViewport.first, selectedViewport.second)) {
                        PrintablePaperTab(
                            paper = paper,
                            config = ExamConfig(),
                            onOpenConfig = {}
                        )
                    }
                }
            }
        }

        for (viewport in viewports) {
            for (scale in fontScales) {
                selectedViewport = viewport
                activeFontScale = scale
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithTag("configure_exam_button").assertIsDisplayed()
                composeTestRule.onNodeWithTag("quick_save_pdf_button").assertIsDisplayed()
                composeTestRule.onNodeWithTag("quick_save_html_button").assertIsDisplayed()
                composeTestRule.onNodeWithTag("quick_print_button").assertIsDisplayed()
            }
        }
    }

    @Test
    fun studentTestTab_cbtHeaderPersistsFontScaleStepAndMaintains48dpTargetsAt160PercentScale() {
        val paper = buildSamplePaper()
        var currentStep by mutableIntStateOf(0)

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = false) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = baseDensity.density, fontScale = 1.6f)
                ) {
                    Box(modifier = Modifier.size(360.dp, 640.dp)) {
                        StudentTestTab(
                            paper = paper,
                            answers = mapOf(1 to "B"),
                            isExamStarted = true,
                            isSubmitted = false,
                            retryQuestionNumbers = null,
                            timeSpentSeconds = 25L,
                            timeRemainingSeconds = 0L,
                            timerMode = ExamTimerMode.STOPWATCH,
                            customTimeLimitMinutes = 60,
                            attempts = emptyList(),
                            onSetTimerMode = {},
                            onSetCustomTimeLimit = {},
                            onStartExam = { _, _ -> },
                            onResumeExam = {},
                            onExitToOverview = {},
                            onSelectAnswer = { _, _ -> },
                            onSubmitExam = {},
                            onResetExam = {},
                            onLoadAttempt = {},
                            cbtFontScaleStep = currentStep,
                            onSetCbtFontScaleStep = { currentStep = it }
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("A+").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, currentStep)

        composeTestRule.onNodeWithTag("cbt_header_palette_button").assertIsDisplayed()
        val paletteBounds = composeTestRule
            .onNodeWithTag("cbt_header_palette_button")
            .fetchSemanticsNode()
            .boundsInRoot
        with(composeTestRule.density) {
            assertTrue(
                "Palette icon button must be >= 48dp",
                paletteBounds.height.toDp() >= 47.dp && paletteBounds.width.toDp() >= 47.dp
            )
        }
    }
}
