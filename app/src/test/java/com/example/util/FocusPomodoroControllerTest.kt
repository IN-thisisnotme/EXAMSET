package com.example.util

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ExamSetPreferences
import com.example.model.DndPolicyBackup
import com.example.model.PomodoroDndMode
import com.example.model.PomodoroPhase
import com.example.model.PomodoroPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusPomodoroControllerTest {

    private class FakeDndAdapter(
        var policyGranted: Boolean = true,
        var currentFilter: Int = NotificationManager.INTERRUPTION_FILTER_ALL,
        var throwSecurityOnSet: Boolean = false
    ) : DndSystemAdapter {
        val setFilterHistory = mutableListOf<Int>()
        var openSettingsCalls = 0

        override fun isPolicyAccessGranted(): Boolean = policyGranted

        override fun getCurrentInterruptionFilter(): Int = currentFilter

        override fun setInterruptionFilter(filter: Int): Boolean {
            if (!policyGranted || throwSecurityOnSet) return false
            currentFilter = filter
            setFilterHistory.add(filter)
            return true
        }

        override fun openPolicyAccessSettings(): Boolean {
            openSettingsCalls++
            return true
        }
    }

    private lateinit var context: Context
    private lateinit var prefs: ExamSetPreferences
    private lateinit var scope: CoroutineScope
    private lateinit var dndAdapter: FakeDndAdapter
    private var fakeElapsedMs = 100_000L
    private var fakeWallMs = 1_795_000_000_000L
    private val flushedSeconds = mutableListOf<Long>()
    private val completedTodoIds = mutableListOf<String?>()
    private val dispatchedAlerts = mutableListOf<Pair<String, String>>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = ExamSetPreferences(context)
        prefs.setPomodoroPresetName(PomodoroPreset.CLASSIC_25_5.name)
        prefs.setPomodoroCustomFocusMinutes(30)
        prefs.setPomodoroCustomBreakMinutes(5)
        prefs.setPreferredDndMode(PomodoroDndMode.PRIORITY_CALLS)
        prefs.saveDndPolicyBackupSync(DndPolicyBackup(wasDndManagedByApp = false))

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        dndAdapter = FakeDndAdapter()
        fakeElapsedMs = 100_000L
        flushedSeconds.clear()
        completedTodoIds.clear()
        dispatchedAlerts.clear()
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun createController(): FocusPomodoroController {
        return FocusPomodoroController(
            context = context,
            prefs = prefs,
            scope = scope,
            elapsedRealtimeProvider = { fakeElapsedMs },
            wallTimeProvider = { fakeWallMs },
            dndAdapter = dndAdapter,
            alertDispatcher = { title, body -> dispatchedAlerts.add(title to body) },
            onFlushFocusSeconds = { secs -> flushedSeconds.add(secs) },
            onFocusSessionCompleted = { todoId -> completedTodoIds.add(todoId) }
        )
    }

    @Test
    fun monotonicElapsedClock_survivesDozeJumpsAndBatchesAnalyticsEvery30Seconds() {
        val controller = createController()
        controller.setCustomDurations(focusMinutes = 5, breakMinutes = 2)
        controller.startFocus(todoId = "todo_1", todoTitle = "Polity Revision", enableDnd = false)

        assertEquals(PomodoroPhase.FOCUS, controller.state.value.phase)
        assertEquals(300L, controller.state.value.remainingSeconds)

        // Advance 20 seconds -> below 30s flush threshold, no analytics flush yet
        fakeElapsedMs += 20_000L
        controller.syncTick()
        assertEquals(280L, controller.state.value.remainingSeconds)
        assertTrue(flushedSeconds.isEmpty())

        // Simulate Doze / screen sleep jump of 65 seconds (total 85s elapsed) -> flushes 85s
        fakeElapsedMs += 65_000L
        controller.syncTick()
        assertEquals(215L, controller.state.value.remainingSeconds)
        assertEquals(85L, flushedSeconds.sum())

        // Pause flushes any accumulated seconds since last flush
        fakeElapsedMs += 10_000L
        controller.pause()
        assertEquals(PomodoroPhase.PAUSED, controller.state.value.phase)
        assertEquals(205L, controller.state.value.remainingSeconds)
        assertEquals(95L, flushedSeconds.sum())

        // Resume and jump straight past remaining 205s to complete Focus phase
        controller.resume()
        assertEquals(PomodoroPhase.FOCUS, controller.state.value.phase)
        fakeElapsedMs += 210_000L
        controller.syncTick()

        // Focus completes -> alert, todo callback, transition to BREAK, total 300s credited
        assertEquals(PomodoroPhase.BREAK, controller.state.value.phase)
        assertEquals(120L, controller.state.value.remainingSeconds)
        assertEquals(1, controller.state.value.completedSessionsCount)
        assertEquals(listOf<String?>("todo_1"), completedTodoIds)
        assertEquals(300L, flushedSeconds.sum())
        assertEquals("Focus Block Complete", dispatchedAlerts.first().first)

        // Complete break -> transitions back to IDLE
        fakeElapsedMs += 120_000L
        controller.syncTick()
        assertEquals(PomodoroPhase.IDLE, controller.state.value.phase)
        assertEquals(300L, controller.state.value.remainingSeconds)
        assertEquals("Break Complete", dispatchedAlerts.last().first)

        controller.stopAndReset()
    }

    @Test
    fun protectedDnd_savesSyncBackupAndRestoresOnPauseBreakStopAndInstantDisable() {
        // Suppose user previously had Priority DND or Normal ALL before starting
        dndAdapter.currentFilter = NotificationManager.INTERRUPTION_FILTER_ALL
        val controller = createController()
        controller.setPreferredDndMode(PomodoroDndMode.ALARMS_ONLY)
        controller.setCustomDurations(focusMinutes = 5, breakMinutes = 2)

        assertTrue(controller.toggleSessionDndPreference(true))
        controller.startFocus(todoId = null, todoTitle = "Deep Focus", enableDnd = true)

        assertTrue(controller.state.value.isDndCurrentlyActive)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, dndAdapter.currentFilter)
        val persistedBackup = prefs.getDndPolicyBackup()
        assertTrue(persistedBackup.wasDndManagedByApp)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, persistedBackup.previousInterruptionFilter)

        // Pause must restore user's original filter
        controller.pause()
        assertFalse(controller.state.value.isDndCurrentlyActive)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, dndAdapter.currentFilter)
        assertFalse(prefs.getDndPolicyBackup().wasDndManagedByApp)

        // Resume re-activates DND
        controller.resume()
        assertTrue(controller.state.value.isDndCurrentlyActive)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, dndAdapter.currentFilter)

        // Instant emergency disable restores DND immediately without stopping the timer
        controller.disableDndImmediately()
        assertFalse(controller.state.value.isDndCurrentlyActive)
        assertFalse(controller.state.value.isDndEnabledForSession)
        assertEquals(PomodoroPhase.FOCUS, controller.state.value.phase)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, dndAdapter.currentFilter)

        // Re-enable mid-session and then skipToBreak -> DND must restore before break starts
        assertTrue(controller.toggleSessionDndPreference(true))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, dndAdapter.currentFilter)
        controller.skipToBreak()
        assertEquals(PomodoroPhase.BREAK, controller.state.value.phase)
        assertFalse(controller.state.value.isDndCurrentlyActive)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, dndAdapter.currentFilter)

        controller.stopAndReset()
    }

    @Test
    fun restoreDndIfOrphaned_restoresPreviousUserFilterAfterSimulatedProcessCrash() {
        // Simulate a dead previous process that had put user's phone in ALARMS_ONLY from PRIORITY
        prefs.saveDndPolicyBackupSync(
            DndPolicyBackup(
                wasDndManagedByApp = true,
                previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                managedSessionStartedAt = 1_700_000_000_000L
            )
        )
        dndAdapter.currentFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS

        val controller = createController()
        controller.restoreDndIfOrphaned()

        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, dndAdapter.currentFilter)
        assertFalse(prefs.getDndPolicyBackup().wasDndManagedByApp)
    }

    @Test
    fun missingDndPermission_neverCrashesAndRunsTimerNormally() {
        dndAdapter.policyGranted = false
        val controller = createController()

        assertFalse(controller.toggleSessionDndPreference(true))
        controller.startFocus(todoId = null, todoTitle = "Unmuted Timer", enableDnd = true)

        assertEquals(PomodoroPhase.FOCUS, controller.state.value.phase)
        assertFalse(controller.state.value.isDndCurrentlyActive)
        assertFalse(controller.state.value.isDndEnabledForSession)
        assertTrue(dndAdapter.setFilterHistory.isEmpty())

        controller.stopAndReset()
    }
}
