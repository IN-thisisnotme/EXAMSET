package com.example.model

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.ExamSetPreferences
import com.example.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PromptPresetTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("examset_app_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun serializeAndDeserialize_roundTripsAllModesAndHandlesCorruptJsonSafely() {
        val qPreset = PromptPreset(
            name = "UPSC Prelims 25Q",
            mode = PromptPresetMode.QUESTION_SET,
            isPinned = true,
            questionConfig = QuestionPromptConfig(
                examType = "UPSC Civil Services",
                subject = "Polity & Governance",
                questionCount = 25,
                difficulty = "Hard",
                optionCount = 5,
                targetOutputFormat = "Strict JSON",
                enableQuantityCorrectionRule = true
            )
        )
        val kPreset = PromptPreset(
            name = "Mains Formula Codex",
            mode = PromptPresetMode.KNOWLEDGE_SET,
            knowledgeConfig = KnowledgePromptConfig(
                examTarget = "JEE / Physics",
                subject = "Physics",
                collectionType = KnowledgeCollectionType.FORMULA_SHEET,
                itemCount = 30,
                depth = KnowledgeDepth.DEEP,
                includePyqTags = true,
                customInstruction = "Include SI units for every formula"
            )
        )
        val fPreset = PromptPreset(
            name = "NEET Trap Deck",
            mode = PromptPresetMode.FLASHCARD_SET,
            flashcardConfig = FlashcardPromptConfig(
                examTarget = "NEET / Biology",
                subject = "Organic Chemistry",
                cardStyle = FlashcardPromptStyle.FORMULA_TRAP,
                cardCount = 25,
                includeExamTraps = true
            )
        )

        val serialized = PromptPreset.serializeList(listOf(qPreset, kPreset, fPreset))
        val restored = PromptPreset.deserializeList(serialized)

        assertEquals(3, restored.size)
        assertEquals("UPSC Prelims 25Q", restored[0].name)
        assertTrue(restored[0].isPinned)
        assertEquals(5, restored[0].questionConfig?.optionCount)
        assertEquals("JSON · 5-Opt", restored[0].formatBadge())

        assertEquals(KnowledgeCollectionType.FORMULA_SHEET, restored[1].knowledgeConfig?.collectionType)
        assertEquals("FORMULAS", restored[1].formatBadge())
        assertTrue(restored[1].specChips().contains("PYQ"))
        assertTrue(restored[1].specChips().contains("Custom Rule"))

        assertEquals(FlashcardPromptStyle.FORMULA_TRAP, restored[2].flashcardConfig?.cardStyle)
        assertEquals("FORMULA SRS", restored[2].formatBadge())

        // Module/Paper Code & Long-Answer Word Target round-trip
        val modularQuestion = PromptPreset(
            name = "Course 101 Practice",
            mode = PromptPresetMode.QUESTION_SET,
            questionConfig = QuestionPromptConfig(
                examType = "Term-End Exam",
                subject = "Economics",
                moduleCode = "Course-101 · Unit 2",
                questionCount = 20
            )
        )
        val modularKnowledge = PromptPreset(
            name = "Course 101 Essays",
            mode = PromptPresetMode.KNOWLEDGE_SET,
            knowledgeConfig = KnowledgePromptConfig(
                examTarget = "Term-End Exam",
                subject = "Economics",
                moduleCode = "Paper II",
                collectionType = KnowledgeCollectionType.LONG_ANSWER,
                answerWordTarget = "~250 words"
            )
        )
        val restoredModular = PromptPreset.deserializeList(
            PromptPreset.serializeList(listOf(modularQuestion, modularKnowledge))
        )
        assertEquals("Course-101 · Unit 2", restoredModular[0].questionConfig?.moduleCode)
        assertEquals("Paper II", restoredModular[1].knowledgeConfig?.moduleCode)
        assertEquals("~250 words", restoredModular[1].knowledgeConfig?.answerWordTarget)
        assertTrue(restoredModular[1].specChips().contains("Paper II"))
        assertTrue(restoredModular[1].specChips().contains("~250 words"))

        // Corrupt JSON must never crash
        assertTrue(PromptPreset.deserializeList("{not valid json").isEmpty())
        assertTrue(PromptPreset.deserializeList("").isEmpty())
        assertTrue(PromptPreset.deserializeList(null).isEmpty())
    }

    @Test
    fun uniqueName_preventsDuplicateNamesCleanly() {
        val existing = listOf("UPSC Polity", "UPSC Polity (2)", "UPSC Polity (Copy)")
        assertEquals("UPSC Polity (3)", PromptPreset.uniqueName("UPSC Polity", existing))
        assertEquals("UPSC Polity (Copy 2)", PromptPreset.uniqueName("UPSC Polity", existing, suffixWord = "Copy"))
        assertEquals("SSC Math", PromptPreset.uniqueName("SSC Math", existing))
    }

    @Test
    fun viewModelPresetLifecycle_saveRenameDuplicatePinClearAndPersistAcrossRestart() {
        val vm = MainViewModel(app)

        vm.questionPromptState.examType = "UPSC Civil Services"
        vm.questionPromptState.subject = "Indian Polity"
        vm.questionPromptState.questionCount = 25f
        vm.questionPromptState.difficulty = "Hard"
        vm.questionPromptState.optionCount = 5

        val saved = vm.saveNewPromptPreset(
            mode = PromptPresetMode.QUESTION_SET,
            rawName = "UPSC Polity Hard"
        )
        assertEquals("UPSC Polity Hard", saved.name)
        assertEquals(saved.id, vm.activeQuestionPresetId)

        // Rename
        vm.renamePromptPreset(saved.id, "UPSC Prelims Polity")
        assertEquals("UPSC Prelims Polity", vm.promptPresets.value.first { it.id == saved.id }.name)

        // Duplicate
        val dup = vm.duplicatePromptPreset(saved.id)
        assertNotNull(dup)
        assertEquals("UPSC Prelims Polity (Copy)", dup!!.name)
        assertEquals(2, vm.promptPresets.value.size)

        // Pin toggle
        vm.togglePinPromptPreset(saved.id)
        assertTrue(vm.promptPresets.value.first { it.id == saved.id }.isPinned)

        // Clear detaches active preset and resets form without deleting saved presets
        vm.clearPromptModeState(PromptPresetMode.QUESTION_SET)
        assertNull(vm.activeQuestionPresetId)
        assertEquals("", vm.questionPromptState.examType)
        assertEquals(20f, vm.questionPromptState.questionCount)
        assertEquals(2, vm.promptPresets.value.size)

        // Persisted across fresh ExamSetPreferences instance
        val reloadedPrefs = ExamSetPreferences(app)
        val reloadedPresets = reloadedPrefs.getPromptPresets()
        assertEquals(2, reloadedPresets.size)
        assertTrue(reloadedPresets.any { it.name == "UPSC Prelims Polity" && it.isPinned })

        // Delete removes preset cleanly
        vm.deletePromptPreset(dup.id)
        assertEquals(1, vm.promptPresets.value.size)
        assertFalse(vm.promptPresets.value.any { it.id == dup.id })
    }
}
