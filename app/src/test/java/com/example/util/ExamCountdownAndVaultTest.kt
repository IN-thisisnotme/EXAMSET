package com.example.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ExamSetPreferences
import com.example.data.ThemePreferences
import com.example.model.ExamCountdownTarget
import com.example.model.PomodoroDndMode
import com.example.model.PomodoroPreset
import com.example.model.RestoreMode
import com.example.model.StudyDateUtils
import com.example.model.StudyTodoItem
import com.example.model.TodoLinkType
import com.example.model.TodoPriority
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExamCountdownAndVaultTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var appPrefs: ExamSetPreferences
    private lateinit var themePrefs: ThemePreferences
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appPrefs = ExamSetPreferences(context)
        themePrefs = ThemePreferences(context)
        appPrefs.saveExamCountdowns(emptyList())
        appPrefs.saveStudyTodos(emptyList())
        appPrefs.setPomodoroPresetName(PomodoroPreset.CLASSIC_25_5.name)
        appPrefs.setPomodoroCustomFocusMinutes(30)
        appPrefs.setPomodoroCustomBreakMinutes(5)
        appPrefs.setPreferredDndMode(PomodoroDndMode.PRIORITY_CALLS)
        tempDir = File(context.cacheDir, "countdown_vault_test_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun localDateMillis(year: Int, month: Int, day: Int, hour: Int = 15): Long {
        return Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 30)
        }.timeInMillis
    }

    @Test
    fun studyDateUtils_validatesDatesAndComputesAccurateDaysRemaining() {
        assertNotNull(StudyDateUtils.parseDateToStartOfDayMillis("2024-02-29"))
        assertNull(StudyDateUtils.parseDateToStartOfDayMillis("2025-02-29"))
        assertNull(StudyDateUtils.parseDateToStartOfDayMillis("2026-13-01"))
        assertNull(StudyDateUtils.parseDateToStartOfDayMillis("14-11-2026"))

        val baseNow = localDateMillis(2026, 11, 10, hour = 22)
        assertEquals(0L, StudyDateUtils.computeDaysRemaining("2026-11-10", baseNow))
        assertEquals(14L, StudyDateUtils.computeDaysRemaining("2026-11-24", baseNow))
        assertEquals(-3L, StudyDateUtils.computeDaysRemaining("2026-11-07", baseNow))

        val targetFuture = ExamCountdownTarget(id = "c1", title = "UPSC Prelims", targetDateStr = "2026-11-24")
        val targetToday = ExamCountdownTarget(id = "c2", title = "Mock Day", targetDateStr = "2026-11-10")
        val targetPast = ExamCountdownTarget(id = "c3", title = "Done Test", targetDateStr = "2026-11-07")

        assertEquals("14d left", targetFuture.formattedDaysShort(baseNow))
        assertEquals("UPSC Prelims • 14d left", targetFuture.formattedBadgeLabel(baseNow))
        assertEquals("Today!", targetToday.formattedDaysShort(baseNow))
        assertEquals("3d ago", targetPast.formattedDaysShort(baseNow))
    }

    @Test
    fun examCountdownAndTodoJsonSerialization_roundTripsCleanly() {
        val countdowns = listOf(
            ExamCountdownTarget(
                id = "cd_1",
                title = "GATE CS",
                targetDateStr = "2027-02-07",
                subject = "Computer Science",
                colorHex = "#DC2626",
                isPinned = true
            ),
            ExamCountdownTarget(
                id = "cd_2",
                title = "SSC CGL Tier 2",
                targetDateStr = "2027-01-18",
                subject = "Quant & English",
                colorHex = "#0D9488",
                isPinned = false
            )
        )
        val decodedCountdowns = ExamCountdownTarget.deserializeList(ExamCountdownTarget.serializeList(countdowns))
        assertEquals(2, decodedCountdowns.size)
        assertEquals("GATE CS", decodedCountdowns[0].title)
        assertEquals(true, decodedCountdowns[0].isPinned)
        assertEquals(false, decodedCountdowns[1].isPinned)

        val todos = listOf(
            StudyTodoItem(
                id = "td_1",
                title = "Revise OS Deadlocks",
                priority = TodoPriority.HIGH,
                dueDateStr = "2026-11-25",
                isCompleted = false,
                targetPomodoros = 3,
                completedPomodoros = 1,
                linkedType = TodoLinkType.NOTE,
                linkedNoteId = 42L,
                linkedLabel = "OS Short Notes"
            ),
            StudyTodoItem(
                id = "td_2",
                title = "C1 Vocab Sprint",
                priority = TodoPriority.LOW,
                isCompleted = true,
                targetPomodoros = 1,
                completedPomodoros = 1,
                linkedType = TodoLinkType.VOCAB_DECK,
                linkedDeckLanguage = "English",
                linkedLabel = "English Vocab",
                completedAt = 1_795_000_000_000L
            )
        )
        val decodedTodos = StudyTodoItem.deserializeList(StudyTodoItem.serializeList(todos))
        assertEquals(2, decodedTodos.size)
        assertEquals(TodoPriority.HIGH, decodedTodos[0].priority)
        assertEquals(42L, decodedTodos[0].linkedNoteId)
        assertEquals(1, decodedTodos[0].completedPomodoros)
        assertEquals(TodoLinkType.VOCAB_DECK, decodedTodos[1].linkedType)
        assertEquals("English", decodedTodos[1].linkedDeckLanguage)
    }

    @Test
    fun vaultArchiveEngine_roundTripsCountdownsTodosAndPomodoroSettingsAcrossFullAndMergeRestore() = runBlocking {
        val countdown = ExamCountdownTarget(
            id = "cd_upsc",
            title = "UPSC Prelims",
            targetDateStr = "2027-05-24",
            subject = "General Studies",
            isPinned = true
        )
        val todo = StudyTodoItem(
            id = "todo_polity",
            title = "Solve Laxmikanth Mock",
            priority = TodoPriority.HIGH,
            dueDateStr = "2026-12-01",
            targetPomodoros = 4,
            completedPomodoros = 2
        )
        appPrefs.saveExamCountdowns(listOf(countdown))
        appPrefs.saveStudyTodos(listOf(todo))
        appPrefs.setPomodoroPresetName(PomodoroPreset.CUSTOM.name)
        appPrefs.setPomodoroCustomFocusMinutes(45)
        appPrefs.setPomodoroCustomBreakMinutes(12)
        appPrefs.setPreferredDndMode(PomodoroDndMode.ALARMS_ONLY)

        val archiveFile = File(tempDir, "planner_backup.examset.zip")
        VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, archiveFile)
        val preview = VaultArchiveEngine.inspectLocalFile(archiveFile)
        assertTrue(preview.checksumVerified)

        // Wipe local planner settings
        appPrefs.saveExamCountdowns(emptyList())
        appPrefs.saveStudyTodos(emptyList())
        appPrefs.setPomodoroPresetName(PomodoroPreset.CLASSIC_25_5.name)
        appPrefs.setPomodoroCustomFocusMinutes(30)
        appPrefs.setPomodoroCustomBreakMinutes(5)
        appPrefs.setPreferredDndMode(PomodoroDndMode.PRIORITY_CALLS)

        // FULL_REPLACE restore
        VaultArchiveEngine.restoreFromPreview(context, db, appPrefs, themePrefs, preview, RestoreMode.FULL_REPLACE)

        assertEquals(1, appPrefs.getExamCountdowns().size)
        assertEquals("UPSC Prelims", appPrefs.getExamCountdowns().first().title)
        assertEquals(1, appPrefs.getStudyTodos().size)
        assertEquals(2, appPrefs.getStudyTodos().first().completedPomodoros)
        assertEquals(PomodoroPreset.CUSTOM.name, appPrefs.getPomodoroPresetName())
        assertEquals(45, appPrefs.getPomodoroCustomFocusMinutes())
        assertEquals(12, appPrefs.getPomodoroCustomBreakMinutes())
        assertEquals(PomodoroDndMode.ALARMS_ONLY, appPrefs.getPreferredDndMode())

        // SMART_MERGE should keep an added local countdown & todo without duplicating existing IDs
        val localExtraCountdown = ExamCountdownTarget(
            id = "cd_gate",
            title = "GATE Mock",
            targetDateStr = "2027-02-01",
            isPinned = false
        )
        val localExtraTodo = StudyTodoItem(
            id = "todo_vocab",
            title = "Daily German Words",
            priority = TodoPriority.MEDIUM
        )
        appPrefs.saveExamCountdowns(appPrefs.getExamCountdowns() + localExtraCountdown)
        appPrefs.saveStudyTodos(appPrefs.getStudyTodos() + localExtraTodo)

        VaultArchiveEngine.restoreFromPreview(context, db, appPrefs, themePrefs, preview, RestoreMode.SMART_MERGE)

        assertEquals(2, appPrefs.getExamCountdowns().size)
        assertEquals(2, appPrefs.getStudyTodos().size)
        assertTrue(appPrefs.getExamCountdowns().any { it.id == "cd_upsc" })
        assertTrue(appPrefs.getExamCountdowns().any { it.id == "cd_gate" })
    }
}
