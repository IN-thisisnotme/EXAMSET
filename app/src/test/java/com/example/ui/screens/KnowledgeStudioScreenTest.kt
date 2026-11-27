package com.example.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.model.KnowledgeCollectionType
import com.example.model.KnowledgeFormatRule
import com.example.ui.KnowledgeEditorDraft
import com.example.ui.theme.ExamSetTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class KnowledgeStudioScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun knowledgeStudioEditor_twoTabsSwitchAndSyncStateAcrossPromptAndInput() {
        var savedTitle = ""
        var savedSubject = ""
        var savedRawContent = ""
        var savedCollectionType: KnowledgeCollectionType? = null
        var savedRule: KnowledgeFormatRule? = null
        var dismissed = false

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = true) {
                KnowledgeStudioEditorScreen(
                    draft = KnowledgeEditorDraft(),
                    folders = emptyList(),
                    savedPapers = emptyList(),
                    onSave = { _, title, subject, _, rawContent, _, collectionType, _, _, formatRule ->
                        savedTitle = title
                        savedSubject = subject
                        savedRawContent = rawContent
                        savedCollectionType = collectionType
                        savedRule = formatRule
                    },
                    onDismiss = { dismissed = true }
                )
            }
        }

        // Initial state: Tab 0 (Paste / Input) is active and Save button is disabled when empty
        composeTestRule.onNodeWithText("Create Knowledge Set").assertIsDisplayed()
        composeTestRule.onNodeWithTag("knowledge_tab_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("knowledge_tab_prompt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("study_note_content_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_knowledge_set_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("clear_knowledge_text_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("cancel_knowledge_sheet_button").assertIsDisplayed()

        // Switch to Tab 1 (AI Prompt Generator)
        composeTestRule.onNodeWithTag("knowledge_tab_prompt").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("AI Knowledge Prompt Builder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("knowledge_copy_prompt_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("knowledge_switch_to_input_tab_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("knowledge_prompt_open_studio_button").assertDoesNotExist()

        val promptList = composeTestRule.onNodeWithTag("knowledge_sheet_prompt_lazy_column")
        promptList.performScrollToNode(hasText("STUDY SCOPE & TOPIC"))
        composeTestRule.onNodeWithTag("knowledge_prompt_subject_input").performScrollTo().performTextInput("Physics")
        composeTestRule.onNodeWithTag("knowledge_prompt_topic_input").performScrollTo().performTextInput("Electrostatics")

        promptList.performScrollToNode(hasText("COLLECTION FORMAT & DEPTH"))
        composeTestRule.onNodeWithTag("knowledge_type_formula_sheet").performScrollTo().performClick()

        promptList.performScrollToNode(hasText("CONSTRUCTED KNOWLEDGE PROMPT"))
        composeTestRule.onNodeWithText("FORMULAS • 20 Items").performScrollTo().assertIsDisplayed()

        // Click "Paste Response" in bottom bar to switch back to Tab 0 with synced metadata
        composeTestRule.onNodeWithTag("knowledge_switch_to_input_tab_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("study_note_content_input").assertIsDisplayed()
        composeTestRule.onNodeWithText("Formula & Law Sheet • Electrostatics").assertIsDisplayed()

        // Paste/enter study note content and save
        composeTestRule.onNodeWithTag("study_note_content_input").performTextInput(
            "1. **Coulomb's Law** -> Force between point charges\n  - Formula: \$F = k \\frac{q_1 q_2}{r^2}\$"
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("save_knowledge_set_button").assertIsEnabled().performClick()
        composeTestRule.waitForIdle()

        assertEquals("Electrostatics", savedTitle)
        assertEquals("Physics", savedSubject)
        assertEquals(KnowledgeCollectionType.FORMULA_SHEET, savedCollectionType)
        assertEquals(KnowledgeCollectionType.FORMULA_SHEET, savedRule?.collectionType)
        assertTrue(savedRawContent.contains("Coulomb's Law"))
        assertTrue(dismissed)
    }

    @Test
    fun knowledgeStudioReader_flashcardNoteLaunchesDeckModeSupportsRevealAndSrsRating() {
        val flashcardNote = com.example.data.StudyNoteEntity(
            id = 42L,
            title = "Thermodynamics SRS",
            subject = "Physics",
            collectionType = KnowledgeCollectionType.FLASHCARDS.code,
            formatRuleJson = KnowledgeFormatRule(collectionType = KnowledgeCollectionType.FLASHCARDS).toJson(),
            rawContent = """
                ## Core Laws
                Q1: State the First Law of Thermodynamics.
                A: Energy is conserved: ΔU = Q - W.

                Q2: What is the entropy change in a reversible adiabatic process?
                A: Zero (isentropic).
            """.trimIndent()
        )
        val recordedRatings = mutableListOf<Pair<Int, com.example.model.SrsRating>>()

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = true) {
                KnowledgeStudioReaderScreen(
                    note = flashcardNote,
                    srsMap = emptyMap(),
                    onToggleBookmark = { _, _ -> },
                    onToggleMastered = { _, _ -> },
                    onRateFlashcard = { _, blockIdx, rating ->
                        recordedRatings.add(blockIdx to rating)
                    },
                    onEdit = {},
                    onGenerateMcqDraft = {},
                    onDismiss = {}
                )
            }
        }

        // Study-First: Flashcards collection opens in readable List view with answers visible first
        composeTestRule.onNodeWithTag("srs_rating_dock").assertDoesNotExist()
        composeTestRule.onNodeWithTag("study_first_start_deck_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reader_navigator_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reader_jump_chip_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Energy is conserved: ΔU = Q - W.").assertIsDisplayed()

        // Launch SM-2 Deck Studio when ready
        composeTestRule.onNodeWithTag("study_first_start_deck_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("srs_rating_dock").assertIsDisplayed()
        composeTestRule.onNodeWithTag("flashcard_reveal_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Rating buttons appear with predicted intervals
        composeTestRule.onNodeWithTag("srs_rate_again").assertIsDisplayed()
        composeTestRule.onNodeWithTag("srs_rate_hard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("srs_rate_good").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, recordedRatings.size)
        assertEquals(com.example.model.SrsRating.GOOD, recordedRatings.first().second)

        // Second card appears ready to flip -> rate Again -> re-queues card cleanly
        composeTestRule.onNodeWithTag("flashcard_reveal_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("srs_rate_again").performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, recordedRatings.size)
        assertEquals(com.example.model.SrsRating.AGAIN, recordedRatings[1].second)
        // Because card 2 was rated Again, it stays in the current session queue for re-testing!
        composeTestRule.onNodeWithTag("flashcard_reveal_button").assertIsDisplayed()
    }

    @Test
    fun knowledgeStudioEditor_existingNoteDefaultsToPreviewWithItemCountAndHidesEditClutter() {
        val existingNote = com.example.data.StudyNoteEntity(
            id = 77L,
            title = "Weak Areas: Civil Engineering",
            subject = "Civil Engineering",
            collectionType = KnowledgeCollectionType.WEAK_AREA_REVIEW.code,
            rawContent = """
                ## Weak-Area Remediation: Civil Core
                > **Review Identification:** Roll No: `101` • Attempt #1

                1. **Q1: What is the bending moment at midspan of a simply supported beam under UDL?**
                  - (A) wL^2/4
                  - (B) wL^2/8
                  - Correct Answer: (B) wL^2/8

                2. **Q2: Which parameter equals unity in saturated undrained clay?**
                  - (A) Skempton B = 1
                  - (B) Skempton B = 0
                  - Correct Answer: (A) Skempton B = 1
            """.trimIndent()
        )

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = true) {
                KnowledgeStudioEditorScreen(
                    draft = KnowledgeEditorDraft(note = existingNote),
                    folders = emptyList(),
                    savedPapers = emptyList(),
                    onSave = { _, _, _, _, _, _, _, _, _, _ -> },
                    onDismiss = {}
                )
            }
        }

        // Opens in Preview by default with actual item count (2), not 4 (heading+callout+2 items)
        composeTestRule.onNodeWithText("2 items").assertIsDisplayed()
        composeTestRule.onNodeWithText("Preview (2)").assertIsDisplayed()
        // Raw editing Paste button & text field are hidden in Preview mode
        composeTestRule.onNodeWithTag("knowledge_paste_clipboard_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("study_note_content_input").assertDoesNotExist()
        // Callout renders clean label without stray ** asterisks
        composeTestRule.onNodeWithText("Review Identification").assertIsDisplayed()

        // Switching to Write & Paste reveals raw input, Append, and Paste tools
        composeTestRule.onNodeWithTag("knowledge_editor_write_mode_chip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("study_note_content_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("knowledge_paste_clipboard_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("knowledge_editor_append_clipboard_button").assertIsDisplayed()
    }

    @Test
    fun knowledgeStudioReader_appendButtonOpensSheetAndAppendsNewUnit() {
        val baseNote = com.example.data.StudyNoteEntity(
            id = 55L,
            title = "Constitutional Amendments",
            subject = "Polity",
            course = "UPSC CSE",
            collectionType = KnowledgeCollectionType.ONE_LINERS.code,
            rawContent = "1. **42nd Amendment** -> Added Socialist, Secular, Integrity to Preamble."
        )
        var appendedPayload = ""

        composeTestRule.setContent {
            ExamSetTheme(darkTheme = true) {
                KnowledgeStudioReaderScreen(
                    note = baseNote,
                    onToggleBookmark = { _, _ -> },
                    onToggleMastered = { _, _ -> },
                    onEdit = {},
                    onAppendToNote = { rawAppend, _, onDone ->
                        appendedPayload = rawAppend
                        onDone(1, 0)
                    },
                    onGenerateMcqDraft = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("reader_append_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("append_quick_primary_input").performTextInput("44th Amendment")
        composeTestRule.onNodeWithTag("append_quick_secondary_input").performTextInput("Moved Right to Property to Article 300A.")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("append_knowledge_submit_button").assertIsEnabled().performClick()
        composeTestRule.waitForIdle()

        assertTrue(appendedPayload.contains("44th Amendment"))
        assertTrue(appendedPayload.contains("Article 300A"))
    }

    @Test
    fun knowledgeCourseBrowser_navigatesCourseToSubjectToNotesAndShowsSuggestedStudy() {
        val sampleNotes = listOf(
            com.example.data.StudyNoteEntity(
                id = 1L,
                title = "Directive Principles",
                subject = "Polity",
                course = "UPSC CSE",
                rawContent = "1. **Article 40** -> Organisation of village panchayats."
            ),
            com.example.data.StudyNoteEntity(
                id = 2L,
                title = "Thermodynamics",
                subject = "Physics",
                course = "JEE Main",
                rawContent = "1. **Zeroth Law** -> Defines thermal equilibrium."
            )
        )
        var selectedCourse: String? = null
        var selectedSubject: String? = null

        composeTestRule.setContent {
            val cState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            val sState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            ExamSetTheme(darkTheme = true) {
                KnowledgeCourseBrowser(
                    notes = sampleNotes,
                    folders = emptyList(),
                    registeredCoursesMap = mapOf("UPSC CSE" to listOf("Polity")),
                    selectedCourse = cState.value,
                    selectedSubject = sState.value,
                    onSelectCourse = {
                        cState.value = it
                        sState.value = null
                        selectedCourse = it
                        selectedSubject = null
                    },
                    onSelectSubject = {
                        sState.value = it
                        selectedSubject = it
                    },
                    onCreateCourse = {},
                    onAddSubjectToCourse = { _, _ -> },
                    onRenameCourse = { _, _ -> },
                    onRenameSubject = { _, _, _ -> },
                    onAssignNoteCourseSubject = { _, _, _ -> },
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onAddKnowledgeSet = { _, _ -> },
                    onOpenNote = {},
                    onEditNote = {},
                    onDeleteNote = {}
                )
            }
        }

        // Suggested Next Study & Courses are visible at root level
        composeTestRule.onNodeWithTag("suggested_next_knowledge_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("UPSC CSE").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals("UPSC CSE", selectedCourse)
        composeTestRule.onNodeWithText("Polity").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals("Polity", selectedSubject)
        composeTestRule.onNodeWithText("New Knowledge Set in Polity").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Directive Principles").assertCountEquals(2)
    }

    @Test
    fun studyNoteParser_prepareAppendedContentSkipsDuplicatesAndRenumbers() {
        val existing = """
            1. **Article 32** -> Constitutional Remedies
            2. **Article 40** -> Village Panchayats
        """.trimIndent()
        val incoming = """
            1. **Article 32** -> Constitutional Remedies
            2. **Article 44** -> Uniform Civil Code
        """.trimIndent()

        val preview = com.example.util.StudyNoteParser.prepareAppendedContent(
            existingRaw = existing,
            newRaw = incoming,
            skipDuplicates = true
        )

        assertEquals(2, preview.existingUnitCount)
        assertEquals(1, preview.skippedDuplicatesCount)
        assertEquals(1, preview.addedUnitCount)
        assertTrue(preview.appendSegment.contains("3. **Article 44**"))
        assertTrue(preview.combinedContent.contains("1. **Article 32**"))
        assertTrue(preview.combinedContent.contains("3. **Article 44**"))
    }
}
