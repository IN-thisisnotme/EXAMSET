package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ExamSetPreferences
import com.example.model.DndPolicyBackup
import com.example.model.PomodoroDndMode
import com.example.model.PomodoroPhase
import com.example.model.PomodoroPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PomodoroUiState(
    val phase: PomodoroPhase = PomodoroPhase.IDLE,
    val pausedFromPhase: PomodoroPhase? = null,
    val remainingSeconds: Long = 25 * 60L,
    val totalPhaseSeconds: Long = 25 * 60L,
    val preset: PomodoroPreset = PomodoroPreset.CLASSIC_25_5,
    val customFocusMinutes: Int = 30,
    val customBreakMinutes: Int = 5,
    val linkedTodoId: String? = null,
    val linkedTodoTitle: String = "",
    val isDndEnabledForSession: Boolean = false,
    val isDndCurrentlyActive: Boolean = false,
    val hasDndPolicyAccess: Boolean = false,
    val preferredDndMode: PomodoroDndMode = PomodoroDndMode.PRIORITY_CALLS,
    val completedSessionsCount: Int = 0
) {
    val activeFocusMinutes: Int
        get() = if (preset == PomodoroPreset.CUSTOM) customFocusMinutes else preset.focusMinutes

    val activeBreakMinutes: Int
        get() = if (preset == PomodoroPreset.CUSTOM) customBreakMinutes else preset.breakMinutes

    val progressFraction: Float
        get() = if (totalPhaseSeconds > 0L) {
            (remainingSeconds.toFloat() / totalPhaseSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

/**
 * Abstraction for system Do Not Disturb / Notification Policy calls so OEM quirks
 * and Robolectric unit tests can be handled safely and deterministically.
 */
interface DndSystemAdapter {
    fun isPolicyAccessGranted(): Boolean
    fun getCurrentInterruptionFilter(): Int
    fun setInterruptionFilter(filter: Int): Boolean
    fun openPolicyAccessSettings(): Boolean
}

class AndroidDndSystemAdapter(private val context: Context) : DndSystemAdapter {
    private val nm: NotificationManager?
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    override fun isPolicyAccessGranted(): Boolean {
        return try {
            nm?.isNotificationPolicyAccessGranted == true
        } catch (_: Exception) {
            false
        }
    }

    override fun getCurrentInterruptionFilter(): Int {
        return try {
            nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (_: Exception) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
    }

    override fun setInterruptionFilter(filter: Int): Boolean {
        return try {
            if (isPolicyAccessGranted()) {
                nm?.setInterruptionFilter(filter)
                true
            } else {
                false
            }
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    override fun openPolicyAccessSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Timestamp-anchored Focus Pomodoro timer controller with crash-safe Do Not Disturb (DND) management.
 * - Anchors ticks to elapsedRealtime so screen sleep / Doze causes zero timer drift.
 * - Persists DndPolicyBackup synchronously (.commit()) BEFORE enabling DND, and restores it on
 *   pause, break, completion, manual stop, or process restart (restoreDndIfOrphaned).
 * - Batches analytics time flushing to 30-second intervals (and immediately on stop/phase end).
 */
class FocusPomodoroController(
    private val context: Context,
    private val prefs: ExamSetPreferences,
    private val scope: CoroutineScope,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallTimeProvider: () -> Long = { TrustedTimeManager.getTrustedTimeMillis() },
    private val dndAdapter: DndSystemAdapter = AndroidDndSystemAdapter(context),
    private val alertDispatcher: (title: String, body: String) -> Unit = { title, body ->
        dispatchCompletionAlert(context, title, body)
    },
    private val onFlushFocusSeconds: (Long) -> Unit = {},
    private val onFocusSessionCompleted: (linkedTodoId: String?) -> Unit = {},
    private val onStatusMessage: (String) -> Unit = {}
) {
    private val initialPreset = PomodoroPreset.fromString(prefs.getPomodoroPresetName())
    private val initialCustomFocus = prefs.getPomodoroCustomFocusMinutes()
    private val initialCustomBreak = prefs.getPomodoroCustomBreakMinutes()
    private val initialFocusSecs = (if (initialPreset == PomodoroPreset.CUSTOM) initialCustomFocus else initialPreset.focusMinutes) * 60L

    private val _state = MutableStateFlow(
        PomodoroUiState(
            phase = PomodoroPhase.IDLE,
            remainingSeconds = initialFocusSecs,
            totalPhaseSeconds = initialFocusSecs,
            preset = initialPreset,
            customFocusMinutes = initialCustomFocus,
            customBreakMinutes = initialCustomBreak,
            hasDndPolicyAccess = dndAdapter.isPolicyAccessGranted(),
            preferredDndMode = prefs.getPreferredDndMode()
        )
    )
    val state: StateFlow<PomodoroUiState> = _state.asStateFlow()

    private var tickerJob: Job? = null
    private var phaseEndElapsedMs: Long = 0L
    private var lastSyncedElapsedMs: Long = 0L
    private var unbufferedFocusRemainderMs: Long = 0L
    private var pendingAnalyticsFlushSeconds: Long = 0L
    private var lastAnalyticsFlushElapsedMs: Long = 0L

    fun refreshDndAccessStatus() {
        val granted = dndAdapter.isPolicyAccessGranted()
        _state.value = _state.value.copy(hasDndPolicyAccess = granted)
    }

    /**
     * Crash/process-death safety guard: if ExamSet enabled DND in a prior process that died
     * before turning DND back off, immediately restore the user's previous interruption filter.
     */
    fun restoreDndIfOrphaned() {
        refreshDndAccessStatus()
        val backup = prefs.getDndPolicyBackup()
        if (backup.wasDndManagedByApp && _state.value.phase != PomodoroPhase.FOCUS) {
            restoreDndFromBackup(backup)
        }
    }

    fun openDndSettings(): Boolean {
        return dndAdapter.openPolicyAccessSettings()
    }

    fun reloadPreferencesAfterRestore() {
        val restoredPreset = PomodoroPreset.fromString(prefs.getPomodoroPresetName())
        val restoredCustomFocus = prefs.getPomodoroCustomFocusMinutes()
        val restoredCustomBreak = prefs.getPomodoroCustomBreakMinutes()
        val restoredDndMode = prefs.getPreferredDndMode()
        val cur = _state.value
        val focusSecs = (if (restoredPreset == PomodoroPreset.CUSTOM) restoredCustomFocus else restoredPreset.focusMinutes) * 60L
        _state.value = if (cur.phase == PomodoroPhase.IDLE) {
            cur.copy(
                preset = restoredPreset,
                customFocusMinutes = restoredCustomFocus,
                customBreakMinutes = restoredCustomBreak,
                preferredDndMode = restoredDndMode,
                remainingSeconds = focusSecs,
                totalPhaseSeconds = focusSecs
            )
        } else {
            cur.copy(
                preset = restoredPreset,
                customFocusMinutes = restoredCustomFocus,
                customBreakMinutes = restoredCustomBreak,
                preferredDndMode = restoredDndMode
            )
        }
    }

    fun setPreset(preset: PomodoroPreset) {
        prefs.setPomodoroPresetName(preset.name)
        val cur = _state.value
        val focusMins = if (preset == PomodoroPreset.CUSTOM) cur.customFocusMinutes else preset.focusMinutes
        val newTotal = focusMins * 60L
        _state.value = if (cur.phase == PomodoroPhase.IDLE) {
            cur.copy(
                preset = preset,
                remainingSeconds = newTotal,
                totalPhaseSeconds = newTotal
            )
        } else {
            cur.copy(preset = preset)
        }
    }

    fun setCustomDurations(focusMinutes: Int, breakMinutes: Int) {
        val safeFocus = focusMinutes.coerceIn(5, 180)
        val safeBreak = breakMinutes.coerceIn(1, 60)
        prefs.setPomodoroCustomFocusMinutes(safeFocus)
        prefs.setPomodoroCustomBreakMinutes(safeBreak)
        prefs.setPomodoroPresetName(PomodoroPreset.CUSTOM.name)
        val cur = _state.value
        val newTotal = safeFocus * 60L
        _state.value = if (cur.phase == PomodoroPhase.IDLE) {
            cur.copy(
                preset = PomodoroPreset.CUSTOM,
                customFocusMinutes = safeFocus,
                customBreakMinutes = safeBreak,
                remainingSeconds = newTotal,
                totalPhaseSeconds = newTotal
            )
        } else {
            cur.copy(
                preset = PomodoroPreset.CUSTOM,
                customFocusMinutes = safeFocus,
                customBreakMinutes = safeBreak
            )
        }
    }

    fun setPreferredDndMode(mode: PomodoroDndMode) {
        prefs.setPreferredDndMode(mode)
        _state.value = _state.value.copy(preferredDndMode = mode)
        if (_state.value.phase == PomodoroPhase.FOCUS && _state.value.isDndCurrentlyActive) {
            applyTargetDndFilter(mode)
        }
    }

    fun toggleSessionDndPreference(enable: Boolean): Boolean {
        if (enable && !dndAdapter.isPolicyAccessGranted()) {
            refreshDndAccessStatus()
            return false
        }
        val cur = _state.value
        if (cur.phase == PomodoroPhase.FOCUS) {
            if (enable) {
                val activated = activateAppDnd(cur.preferredDndMode)
                _state.value = _state.value.copy(
                    isDndEnabledForSession = activated,
                    isDndCurrentlyActive = activated
                )
            } else {
                restoreDndIfManaged()
                _state.value = _state.value.copy(
                    isDndEnabledForSession = false,
                    isDndCurrentlyActive = false
                )
            }
        } else {
            _state.value = cur.copy(isDndEnabledForSession = enable)
        }
        return true
    }

    fun linkTodo(todoId: String?, todoTitle: String?) {
        _state.value = _state.value.copy(
            linkedTodoId = todoId,
            linkedTodoTitle = todoTitle?.trim().orEmpty()
        )
    }

    fun startFocus(
        todoId: String? = _state.value.linkedTodoId,
        todoTitle: String? = _state.value.linkedTodoTitle,
        enableDnd: Boolean = _state.value.isDndEnabledForSession
    ) {
        flushPendingAnalytics(force = true)
        val cur = _state.value
        val focusSecs = cur.activeFocusMinutes * 60L
        val nowElapsed = elapsedRealtimeProvider()

        val dndActive = if (enableDnd && dndAdapter.isPolicyAccessGranted()) {
            activateAppDnd(cur.preferredDndMode)
        } else {
            restoreDndIfManaged()
            false
        }

        phaseEndElapsedMs = nowElapsed + focusSecs * 1000L
        lastSyncedElapsedMs = nowElapsed
        unbufferedFocusRemainderMs = 0L
        lastAnalyticsFlushElapsedMs = nowElapsed

        _state.value = cur.copy(
            phase = PomodoroPhase.FOCUS,
            pausedFromPhase = null,
            remainingSeconds = focusSecs,
            totalPhaseSeconds = focusSecs,
            linkedTodoId = todoId,
            linkedTodoTitle = todoTitle?.trim().orEmpty(),
            isDndEnabledForSession = enableDnd && dndAdapter.isPolicyAccessGranted(),
            isDndCurrentlyActive = dndActive,
            hasDndPolicyAccess = dndAdapter.isPolicyAccessGranted()
        )
        startTicker()
    }

    fun pause() {
        val cur = _state.value
        if (cur.phase != PomodoroPhase.FOCUS && cur.phase != PomodoroPhase.BREAK) return
        syncTick()
        val updated = _state.value
        if (updated.phase != PomodoroPhase.FOCUS && updated.phase != PomodoroPhase.BREAK) return
        tickerJob?.cancel()
        tickerJob = null
        flushPendingAnalytics(force = true)
        if (updated.isDndCurrentlyActive) {
            restoreDndIfManaged()
        }
        _state.value = updated.copy(
            phase = PomodoroPhase.PAUSED,
            pausedFromPhase = updated.phase,
            isDndCurrentlyActive = false
        )
    }

    fun resume() {
        val cur = _state.value
        if (cur.phase != PomodoroPhase.PAUSED) return
        val targetPhase = cur.pausedFromPhase ?: PomodoroPhase.FOCUS
        val nowElapsed = elapsedRealtimeProvider()
        phaseEndElapsedMs = nowElapsed + (cur.remainingSeconds.coerceAtLeast(1L) * 1000L)
        lastSyncedElapsedMs = nowElapsed
        lastAnalyticsFlushElapsedMs = nowElapsed

        val dndActive = if (targetPhase == PomodoroPhase.FOCUS && cur.isDndEnabledForSession && dndAdapter.isPolicyAccessGranted()) {
            activateAppDnd(cur.preferredDndMode)
        } else false

        _state.value = cur.copy(
            phase = targetPhase,
            pausedFromPhase = null,
            isDndCurrentlyActive = dndActive,
            hasDndPolicyAccess = dndAdapter.isPolicyAccessGranted()
        )
        startTicker()
    }

    fun skipToBreak() {
        val cur = _state.value
        if (cur.phase != PomodoroPhase.FOCUS && !(cur.phase == PomodoroPhase.PAUSED && cur.pausedFromPhase == PomodoroPhase.FOCUS)) {
            return
        }
        flushPendingAnalytics(force = true)
        restoreDndIfManaged()
        beginBreakPhase(incrementCompleted = false)
    }

    fun skipBreak() {
        val cur = _state.value
        if (cur.phase != PomodoroPhase.BREAK && !(cur.phase == PomodoroPhase.PAUSED && cur.pausedFromPhase == PomodoroPhase.BREAK)) {
            return
        }
        stopAndReset()
    }

    fun stopAndReset() {
        tickerJob?.cancel()
        tickerJob = null
        if (_state.value.phase == PomodoroPhase.FOCUS) {
            syncElapsedFocusOnly()
        }
        flushPendingAnalytics(force = true)
        restoreDndIfManaged()
        val cur = _state.value
        val idleSecs = cur.activeFocusMinutes * 60L
        _state.value = cur.copy(
            phase = PomodoroPhase.IDLE,
            pausedFromPhase = null,
            remainingSeconds = idleSecs,
            totalPhaseSeconds = idleSecs,
            isDndCurrentlyActive = false,
            hasDndPolicyAccess = dndAdapter.isPolicyAccessGranted()
        )
    }

    fun disableDndImmediately() {
        restoreDndIfManaged()
        _state.value = _state.value.copy(
            isDndEnabledForSession = false,
            isDndCurrentlyActive = false
        )
        onStatusMessage("Do Not Disturb turned off")
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (_state.value.phase == PomodoroPhase.FOCUS || _state.value.phase == PomodoroPhase.BREAK) {
                delay(1000L)
                syncTick()
            }
        }
    }

    private fun syncElapsedFocusOnly() {
        val nowElapsed = elapsedRealtimeProvider()
        val deltaMs = (nowElapsed - lastSyncedElapsedMs).coerceAtLeast(0L)
        lastSyncedElapsedMs = nowElapsed
        if (deltaMs > 0L) {
            val cappedMs = deltaMs.coerceAtMost(_state.value.remainingSeconds * 1000L + 1000L)
            unbufferedFocusRemainderMs += cappedMs
            val wholeSecs = unbufferedFocusRemainderMs / 1000L
            if (wholeSecs > 0L) {
                unbufferedFocusRemainderMs %= 1000L
                pendingAnalyticsFlushSeconds += wholeSecs
            }
        }
    }

    /**
     * Synchronizes countdown with monotonic elapsedRealtime clock. Safe to call after Doze/resume
     * and directly in deterministic unit tests.
     */
    fun syncTick() {
        val cur = _state.value
        if (cur.phase != PomodoroPhase.FOCUS && cur.phase != PomodoroPhase.BREAK) return

        val nowElapsed = elapsedRealtimeProvider()
        if (cur.phase == PomodoroPhase.FOCUS) {
            val stepMs = (nowElapsed - lastSyncedElapsedMs).coerceAtLeast(0L)
            lastSyncedElapsedMs = nowElapsed
            val remainingDeltaCapMs = (cur.remainingSeconds * 1000L).coerceAtLeast(0L)
            val creditedMs = stepMs.coerceAtMost(remainingDeltaCapMs)
            unbufferedFocusRemainderMs += creditedMs
            val wholeSecs = unbufferedFocusRemainderMs / 1000L
            if (wholeSecs > 0L) {
                unbufferedFocusRemainderMs %= 1000L
                pendingAnalyticsFlushSeconds += wholeSecs
            }
            if (nowElapsed - lastAnalyticsFlushElapsedMs >= 30_000L) {
                flushPendingAnalytics(force = true)
                lastAnalyticsFlushElapsedMs = nowElapsed
            }
        } else {
            lastSyncedElapsedMs = nowElapsed
        }

        val remainingMs = (phaseEndElapsedMs - nowElapsed).coerceAtLeast(0L)
        val remainingSecs = if (remainingMs == 0L) 0L else ((remainingMs + 999L) / 1000L)

        if (remainingSecs <= 0L) {
            if (cur.phase == PomodoroPhase.FOCUS) {
                flushPendingAnalytics(force = true)
                restoreDndIfManaged()
                val taskLabel = cur.linkedTodoTitle.ifBlank { "Focus Session" }
                onFocusSessionCompleted(cur.linkedTodoId)
                alertDispatcher(
                    "Focus Block Complete",
                    "Great work on $taskLabel! Time for a ${cur.activeBreakMinutes}-minute break."
                )
                onStatusMessage("Focus session completed! Break started.")
                beginBreakPhase(incrementCompleted = true)
            } else {
                tickerJob?.cancel()
                tickerJob = null
                alertDispatcher(
                    "Break Complete",
                    "Ready for your next focus block when you are."
                )
                onStatusMessage("Break complete! Ready for next Focus block.")
                val idleSecs = cur.activeFocusMinutes * 60L
                _state.value = cur.copy(
                    phase = PomodoroPhase.IDLE,
                    pausedFromPhase = null,
                    remainingSeconds = idleSecs,
                    totalPhaseSeconds = idleSecs,
                    isDndCurrentlyActive = false
                )
            }
        } else if (remainingSecs != cur.remainingSeconds) {
            _state.value = cur.copy(remainingSeconds = remainingSecs)
        }
    }

    private fun beginBreakPhase(incrementCompleted: Boolean) {
        val cur = _state.value
        val breakSecs = cur.activeBreakMinutes * 60L
        val nowElapsed = elapsedRealtimeProvider()
        phaseEndElapsedMs = nowElapsed + breakSecs * 1000L
        lastSyncedElapsedMs = nowElapsed

        _state.value = cur.copy(
            phase = PomodoroPhase.BREAK,
            pausedFromPhase = null,
            remainingSeconds = breakSecs,
            totalPhaseSeconds = breakSecs,
            isDndCurrentlyActive = false,
            completedSessionsCount = if (incrementCompleted) cur.completedSessionsCount + 1 else cur.completedSessionsCount
        )
        startTicker()
    }

    private fun flushPendingAnalytics(force: Boolean) {
        if (pendingAnalyticsFlushSeconds <= 0L) return
        if (force || pendingAnalyticsFlushSeconds >= 30L) {
            val toFlush = pendingAnalyticsFlushSeconds
            pendingAnalyticsFlushSeconds = 0L
            onFlushFocusSeconds(toFlush)
        }
    }

    private fun activateAppDnd(mode: PomodoroDndMode): Boolean {
        if (!dndAdapter.isPolicyAccessGranted()) return false
        val existingBackup = prefs.getDndPolicyBackup()
        if (!existingBackup.wasDndManagedByApp) {
            val prevFilter = dndAdapter.getCurrentInterruptionFilter()
            val saved = prefs.saveDndPolicyBackupSync(
                DndPolicyBackup(
                    wasDndManagedByApp = true,
                    previousInterruptionFilter = prevFilter,
                    managedSessionStartedAt = wallTimeProvider()
                )
            )
            if (!saved) return false
        }
        val applied = applyTargetDndFilter(mode)
        if (!applied && !existingBackup.wasDndManagedByApp) {
            prefs.saveDndPolicyBackupSync(DndPolicyBackup(wasDndManagedByApp = false))
        }
        return applied
    }

    private fun applyTargetDndFilter(mode: PomodoroDndMode): Boolean {
        val targetFilter = when (mode) {
            PomodoroDndMode.PRIORITY_CALLS -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            PomodoroDndMode.ALARMS_ONLY -> NotificationManager.INTERRUPTION_FILTER_ALARMS
        }
        return dndAdapter.setInterruptionFilter(targetFilter)
    }

    private fun restoreDndIfManaged() {
        val backup = prefs.getDndPolicyBackup()
        if (backup.wasDndManagedByApp) {
            restoreDndFromBackup(backup)
        }
    }

    private fun restoreDndFromBackup(backup: DndPolicyBackup) {
        if (dndAdapter.isPolicyAccessGranted()) {
            val targetFilter = backup.previousInterruptionFilter.takeIf {
                it in NotificationManager.INTERRUPTION_FILTER_ALL..NotificationManager.INTERRUPTION_FILTER_ALARMS
            } ?: NotificationManager.INTERRUPTION_FILTER_ALL
            dndAdapter.setInterruptionFilter(targetFilter)
        }
        prefs.saveDndPolicyBackupSync(
            DndPolicyBackup(
                wasDndManagedByApp = false,
                previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
                managedSessionStartedAt = 0L
            )
        )
    }

    companion object {
        private const val CHANNEL_ID = "focus_pomodoro_alerts"
        private const val NOTIFICATION_ID = 4215

        fun dispatchCompletionAlert(context: Context, title: String, body: String) {
            playGentleCompletionChime(context)
            postCompletionNotification(context, title, body)
        }

        private fun playGentleCompletionChime(context: Context) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, uri)
                if (ringtone != null) {
                    ringtone.play()
                    return
                }
            }
            runCatching {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
                tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 320)
            }
        }

        private fun postCompletionNotification(context: Context, title: String, body: String) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                        val channel = NotificationChannel(
                            CHANNEL_ID,
                            "Focus Pomodoro Alerts",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Completion alerts when Focus or Break intervals finish."
                        }
                        nm.createNotificationChannel(channel)
                    }
                }
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            }
        }
    }
}
