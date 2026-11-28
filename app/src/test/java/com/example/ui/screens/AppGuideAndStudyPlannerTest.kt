package com.example.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.example.data.AttemptPacingSummary
import com.example.data.SlowQuestionPacingEntry
import com.example.model.DueSuggestionCategory
import com.example.model.ExamCountdownTarget
import com.example.model.PomodoroPhase
import com.example.model.StudyDateUtils
import com.example.model.StudyDueSuggestion
import com.example.model.StudyTodoItem
import com.example.model.TodoPriority
import com.example.ui.components.ExamCountdownsDialog
import com.example.ui.components.PerQuestionPacingCard
import com.example.ui.components.PinnedExamCountdownsStrip
import com.example.ui.theme.ExamSetTheme
import com.example.util.PomodoroUiState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class AppGuideAndStudyPlannerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appGuideScreen_searchesFiltersAndNavigatesToDeepLinkDestination() {
        var navigatedDest: GuideDestination? = null
        var dismissed = false

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = false) {
                AppGuideScreen(
                    onDismiss = { dismissed = true },
                    onNavigateDestination = { dest -> navigatedDest = dest }
                )
            }
        }

        composeTestRule.onNodeWithTag("app_guide_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_guide_search_input").performTextInput("pomodoro")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Smart Study Planner & Protected DND Focus Clock").assertIsDisplayed()
        composeTestRule.onNodeWithTag("guide_open_study_planner_dnd").performClick()
        composeTestRule.waitForIdle()

        assertTrue(dismissed)
        assertEquals(GuideDestination.STUDY_PLANNER, navigatedDest)
    }

    @Test
    fun dismissibleFormatTipBanner_hidesAfterClickingDismiss() {
        var isDismissed = false
        var dismissedId = ""

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = false) {
                DismissibleFormatTipBanner(
                    tipId = "home_empty_formats",
                    title = "Supported Import Formats",
                    body = "Paste numbered MCQs or Aiken format.",
                    isDismissed = isDismissed,
                    onDismissTip = { id ->
                        dismissedId = id
                        isDismissed = true
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("format_tip_banner_home_empty_formats").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dismiss_format_tip_home_empty_formats").performClick()
        composeTestRule.waitForIdle()

        assertEquals("home_empty_formats", dismissedId)
    }

    @Test
    fun studyPlannerScreen_rendersClockControlsDndBannerDueQueueAndCreatesCustomTask() {
        val pomodoroFlow = MutableStateFlow(
            PomodoroUiState(
                phase = PomodoroPhase.FOCUS,
                remainingSeconds = 1200L,
                totalPhaseSeconds = 1500L,
                isDndEnabledForSession = true,
                isDndCurrentlyActive = true,
                hasDndPolicyAccess = true,
                linkedTodoTitle = "Polity Revision"
            )
        )
        val dueList = listOf(
            StudyDueSuggestion(
                id = "vocab_English",
                category = DueSuggestionCategory.VOCABULARY,
                title = "English Vocab",
                subtitle = "7 words ready for SRS review",
                count = 7,
                language = "English"
            )
        )
        val todos = mutableListOf(
            StudyTodoItem(
                id = "todo_1",
                title = "Revise Fundamental Rights",
                priority = TodoPriority.HIGH,
                targetPomodoros = 2,
                completedPomodoros = 1
            )
        )
        var turnOffDndClicked = false
        var toggledTodoId = ""
        var addedTodoTitle = ""
        var openedDueId = ""

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = false) {
                StudyPlannerScreen(
                    pomodoroStateFlow = pomodoroFlow,
                    dueSuggestions = dueList,
                    todos = todos,
                    savedPapers = emptyList(),
                    studyNotes = emptyList(),
                    vocabLanguages = listOf("English"),
                    onStartFocus = { _, _, _ -> },
                    onPauseFocus = {},
                    onResumeFocus = {},
                    onSkipToBreak = {},
                    onSkipBreak = {},
                    onStopFocus = {},
                    onSelectPreset = {},
                    onSetCustomDurations = { _, _ -> },
                    onToggleDndPreference = { false },
                    onSetPreferredDndMode = {},
                    onOpenDndSettings = {},
                    onDisableDndImmediately = {
                        turnOffDndClicked = true
                        pomodoroFlow.value = pomodoroFlow.value.copy(
                            isDndEnabledForSession = false,
                            isDndCurrentlyActive = false,
                            hasDndPolicyAccess = false
                        )
                    },
                    onAddTodo = { title, _, _, _, _, _, _, _, _ -> addedTodoTitle = title },
                    onToggleTodoCompleted = { toggledTodoId = it },
                    onDeleteTodo = {},
                    onFocusTodo = {},
                    onOpenDueSuggestion = { openedDueId = it.id },
                    onOpenLinkedTodoResource = {},
                    onDismiss = {}
                )
            }
        }

        // Verify instant DND turn-off control is immediately accessible while DND is active
        composeTestRule.onNodeWithTag("turn_off_dnd_now_button").assertIsDisplayed().performClick()
        assertTrue(turnOffDndClicked)

        // Verify DND toggle triggers permission explainer dialog when access is unavailable
        composeTestRule.onNodeWithTag("dnd_toggle_switch").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dnd_permission_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Use Timer Only (No DND)").performClick()
        composeTestRule.waitForIdle()

        // Verify auto-detected due queue card click
        composeTestRule.onNodeWithTag("study_planner_lazy_column")
            .performScrollToNode(hasTestTag("auto_due_lazy_row"))
        composeTestRule.onNodeWithTag("due_card_vocab_English").performClick()
        assertEquals("vocab_English", openedDueId)

        // Verify existing to-do checkbox interaction
        composeTestRule.onNodeWithTag("study_planner_lazy_column")
            .performScrollToNode(hasTestTag("todo_card_todo_1"))
        composeTestRule.onNodeWithTag("todo_checkbox_todo_1").performClick()
        assertEquals("todo_1", toggledTodoId)

        // Create a new custom task via AddStudyTodoDialog
        composeTestRule.onNodeWithTag("add_todo_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("todo_title_input").performTextInput("Practice CSAT Passage")
        composeTestRule.onNodeWithTag("confirm_add_todo_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Practice CSAT Passage", addedTodoTitle)
    }

    @Test
    fun pinnedExamCountdownsStrip_and_perQuestionPacingCard_renderExpectedData() {
        val nowMs = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 12)
        }.timeInMillis
        val futureDate = StudyDateUtils.addDaysToDateString(nowMs, 14)
        val countdowns = listOf(
            ExamCountdownTarget(id = "p1", title = "UPSC Prelims", targetDateStr = futureDate, isPinned = true)
        )
        val pacing = AttemptPacingSummary(
            hasPacingData = true,
            trackedQuestionsCount = 12,
            totalTrackedSeconds = 360L,
            averageSecondsPerQuestion = 30.0,
            slowestQuestions = listOf(
                SlowQuestionPacingEntry(
                    questionNum = 7,
                    secondsSpent = 85L,
                    isCorrect = false,
                    questionPreview = "Evaluate constitutional amendment procedure"
                )
            ),
            correctAvgSeconds = 22.0,
            incorrectAvgSeconds = 64.0
        )

        var manageClicked = false

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Column {
                    PinnedExamCountdownsStrip(
                        countdowns = countdowns,
                        nowMillis = nowMs,
                        onManageCountdowns = { manageClicked = true }
                    )
                    PerQuestionPacingCard(pacingSummary = pacing)
                }
            }
        }

        composeTestRule.onNodeWithTag("pinned_countdown_p1").assertIsDisplayed()
        composeTestRule.onNodeWithText("UPSC Prelims • 14d left").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manage_exam_countdowns_button").performClick()
        assertTrue(manageClicked)

        composeTestRule.onNodeWithTag("per_question_pacing_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("12 Qs tracked").assertIsDisplayed()
        composeTestRule.onNodeWithText("Q7").assertIsDisplayed()
    }

    @Test
    fun examCountdownsDialog_supportsTimeAndGmtToggle_withoutQuickChips() {
        val nowMs = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 12)
        }.timeInMillis

        var savedTitle = ""
        var savedTime = ""
        var savedTzMode = ""
        var savedAlertEnabled = false
        var savedLeadMinutes = 0

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = false) {
                ExamCountdownsDialog(
                    countdowns = listOf(
                        ExamCountdownTarget(
                            id = "c1",
                            title = "Disaster Management",
                            targetDateStr = "2026-12-02",
                            targetTimeStr = "00:00",
                            timezoneMode = "GMT",
                            targetUtcMillis = StudyDateUtils.parseDateTimeMillis("2026-12-02", "00:00", useGmt = true)
                        )
                    ),
                    nowMillis = nowMs,
                    onSaveCountdown = { _, title, _, timeStr, tzMode, _, _, _, alertEnabled, leadMins, _, _, _, _ ->
                        savedTitle = title
                        savedTime = timeStr
                        savedTzMode = tzMode
                        savedAlertEnabled = alertEnabled
                        savedLeadMinutes = leadMins
                        true
                    },
                    onTogglePin = {},
                    onDelete = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("exam_countdowns_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("countdown_time_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("countdown_tz_local").assertIsDisplayed()
        composeTestRule.onNodeWithTag("countdown_tz_gmt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("countdown_lead_hours_input").assertIsDisplayed()
        composeTestRule.onNodeWithText("+7d").assertDoesNotExist()
        composeTestRule.onNodeWithText("Disaster Management").assertIsDisplayed()

        composeTestRule.onNodeWithTag("countdown_title_input").performTextInput("Term-End Paper I")
        composeTestRule.onNodeWithTag("countdown_tz_gmt").performClick()
        composeTestRule.onNodeWithTag("save_countdown_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("Term-End Paper I", savedTitle)
        assertEquals("00:00", savedTime)
        assertEquals("GMT", savedTzMode)
        assertTrue(savedAlertEnabled)
        assertEquals(120, savedLeadMinutes)
    }

    @Test
    fun examCountdownsDialog_adaptsToHighFontScale_andSupportsCancelEdit() {
        val nowMs = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 10)
            set(Calendar.HOUR_OF_DAY, 12)
        }.timeInMillis

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.6f)
            ) {
                ExamSetTheme(darkTheme = false) {
                    ExamCountdownsDialog(
                        countdowns = listOf(
                            ExamCountdownTarget(
                                id = "c1",
                                title = "Disaster Management",
                                targetDateStr = "2026-12-02",
                                targetTimeStr = "00:00",
                                timezoneMode = "GMT",
                                targetUtcMillis = StudyDateUtils.parseDateTimeMillis("2026-12-02", "00:00", useGmt = true)
                            )
                        ),
                        nowMillis = nowMs,
                        onSaveCountdown = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> true },
                        onTogglePin = {},
                        onDelete = {},
                        onDismiss = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("exam_countdowns_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pin to Library Strip").assertIsDisplayed()
        composeTestRule.onNodeWithText("GMT (UTC)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add").assertIsDisplayed()

        // Enter edit mode and cancel cleanly
        composeTestRule.onNodeWithContentDescription("Edit Countdown").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cancel_countdown_edit_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add Exam Target").assertIsDisplayed()
    }
}
