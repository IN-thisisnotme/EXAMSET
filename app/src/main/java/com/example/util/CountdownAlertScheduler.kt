package com.example.util

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ExamSetPreferences
import com.example.model.ExamCountdownTarget
import com.example.model.StudyDateUtils

enum class CountdownAlertStage {
    EARLY,
    ZERO,
    SNOOZE
}

data class CountdownAlertReadiness(
    val postNotificationsGranted: Boolean,
    val systemNotificationsEnabled: Boolean,
    val channelEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val hasVibrator: Boolean,
    val blockingIssues: List<String>,
    val advisoryWarnings: List<String>
) {
    val isReadyForAlerts: Boolean
        get() = blockingIssues.isEmpty()
}

data class InAppCountdownAlert(
    val alertKey: String,
    val countdownId: String,
    val title: String,
    val customMessage: String,
    val stage: CountdownAlertStage,
    val leadMinutes: Int,
    val targetUtcMillis: Long,
    val scheduleText: String,
    val vibrate: Boolean,
    val sound: Boolean
)

/**
 * Reliable AlarmManager-backed scheduler, notification dispatcher, and vibration engine
 * for Exam-Day Countdowns.
 *
 * - Uses `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()` is allowed (API 31+) or on API < 31.
 * - Falls back cleanly to `setAndAllowWhileIdle` if exact alarms are restricted, while reporting
 *   transparent readiness issues in the UI so users are never silently misled.
 * - Dispatches distinct hardware vibration waveform + high-priority notification (or silent vibrate-only
 *   channel when sound is toggled off) with custom user reminder text and Snooze / Dismiss actions.
 */
object CountdownAlertScheduler {

    const val ACTION_TRIGGER_COUNTDOWN_ALERT = "com.example.action.TRIGGER_COUNTDOWN_ALERT"
    const val ACTION_SNOOZE_COUNTDOWN_ALERT = "com.example.action.SNOOZE_COUNTDOWN_ALERT"
    const val ACTION_DISMISS_COUNTDOWN_ALERT = "com.example.action.DISMISS_COUNTDOWN_ALERT"

    private const val EXTRA_COUNTDOWN_ID = "extra_countdown_id"
    private const val EXTRA_TITLE = "extra_countdown_title"
    private const val EXTRA_MESSAGE = "extra_countdown_message"
    private const val EXTRA_STAGE = "extra_countdown_stage"
    private const val EXTRA_LEAD_MINS = "extra_countdown_lead_mins"
    private const val EXTRA_TARGET_UTC = "extra_countdown_target_utc"
    private const val EXTRA_VIBRATE = "extra_countdown_vibrate"
    private const val EXTRA_SOUND = "extra_countdown_sound"
    private const val EXTRA_ALERT_KEY = "extra_countdown_alert_key"
    private const val EXTRA_NOTIFICATION_ID = "extra_countdown_notification_id"

    private const val CHANNEL_SOUND_VIBRATE = "countdown_early_alerts_sound_v1"
    private const val CHANNEL_VIBRATE_ONLY = "countdown_early_alerts_vibrate_v1"

    private val ALERT_VIBRATION_WAVEFORM = longArrayOf(0L, 380L, 150L, 380L, 150L, 520L)
    private val ALERT_VIBRATION_AMPLITUDES = intArrayOf(0, 230, 0, 230, 0, 255)
    private const val FOREGROUND_CATCHUP_WINDOW_MS = 15L * 60_000L

    fun inspectReadiness(context: Context): CountdownAlertReadiness {
        val postGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val sysEnabled = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(true)

        var channelEnabled = true
        var soundChannelMuted = false
        var vibChannelMuted = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureChannels(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val soundChannel = nm?.getNotificationChannel(CHANNEL_SOUND_VIBRATE)
            val vibChannel = nm?.getNotificationChannel(CHANNEL_VIBRATE_ONLY)
            soundChannelMuted = soundChannel != null && soundChannel.importance == NotificationManager.IMPORTANCE_NONE
            vibChannelMuted = vibChannel != null && vibChannel.importance == NotificationManager.IMPORTANCE_NONE
            if (soundChannelMuted && vibChannelMuted) {
                channelEnabled = false
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val exactAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager?.canScheduleExactAlarms() == true }.getOrDefault(false)
        } else {
            true
        }

        val hasVibrator = hasHardwareVibrator(context)

        val blocking = buildList {
            if (!postGranted) {
                add("Notification permission is denied — Android will block countdown alerts until you grant it.")
            } else if (!sysEnabled) {
                add("App notifications are turned off in Android System Settings.")
            } else if (!channelEnabled) {
                add("Countdown Alerts notification channel is muted/blocked in Android Settings.")
            }
        }

        val warnings = buildList {
            if (channelEnabled && soundChannelMuted) {
                add("\"Countdown Alerts (Sound & Vibrate)\" channel is muted in Android Settings — sound-enabled alerts will be suppressed.")
            }
            if (channelEnabled && vibChannelMuted) {
                add("\"Countdown Alerts (Vibrate Only)\" channel is muted in Android Settings — vibrate-only alerts will be suppressed.")
            }
            if (!exactAllowed) {
                add("Exact Alarms & Reminders access is off — Android Doze may delay alerts by several minutes.")
            }
            if (!hasVibrator) {
                add("This device reports no hardware vibration motor; alerts will be visual/sound only.")
            }
        }

        return CountdownAlertReadiness(
            postNotificationsGranted = postGranted,
            systemNotificationsEnabled = sysEnabled,
            channelEnabled = channelEnabled,
            exactAlarmsAllowed = exactAllowed,
            hasVibrator = hasVibrator,
            blockingIssues = blocking,
            advisoryWarnings = warnings
        )
    }

    fun openAppNotificationSettings(context: Context): Boolean {
        return runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openExactAlarmSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun rescheduleAll(
        context: Context,
        countdowns: List<ExamCountdownTarget>,
        nowMillis: Long = TrustedTimeManager.getTrustedTimeMillis()
    ) {
        val appCtx = context.applicationContext ?: context
        val prefs = ExamSetPreferences(appCtx)
        val firedKeys = prefs.getFiredCountdownAlertKeys()
        countdowns.forEach { target ->
            scheduleForCountdown(appCtx, target, nowMillis, firedKeys)
        }
    }

    fun scheduleForCountdown(
        context: Context,
        target: ExamCountdownTarget,
        nowMillis: Long = System.currentTimeMillis(),
        firedKeys: Set<String> = ExamSetPreferences(context.applicationContext ?: context).getFiredCountdownAlertKeys()
    ) {
        val appCtx = context.applicationContext ?: context
        cancelForCountdown(appCtx, target.id)
        if (!target.alertEnabled) return

        val targetMillis = target.resolvedTargetMillis() ?: return
        val leadMins = target.alertLeadMinutes.coerceIn(
            ExamCountdownTarget.MIN_ALERT_LEAD_MINUTES,
            ExamCountdownTarget.MAX_ALERT_LEAD_MINUTES
        )
        val earlyTriggerMillis = targetMillis - leadMins * 60_000L

        if (earlyTriggerMillis > nowMillis) {
            val earlyKey = buildAlertKey(target.id, CountdownAlertStage.EARLY, earlyTriggerMillis)
            if (earlyKey !in firedKeys) {
                scheduleSingleAlarm(
                    context = appCtx,
                    requestCode = requestCodeFor(target.id, CountdownAlertStage.EARLY),
                    triggerAtMillis = earlyTriggerMillis,
                    countdownId = target.id,
                    title = target.title,
                    customMessage = target.alertMessage,
                    stage = CountdownAlertStage.EARLY,
                    leadMinutes = leadMins,
                    targetUtcMillis = targetMillis,
                    vibrate = target.vibrateOnAlert,
                    sound = target.alertSoundEnabled,
                    alertKey = earlyKey
                )
            }
        }

        if (target.alertAtZero && targetMillis > nowMillis) {
            val zeroKey = buildAlertKey(target.id, CountdownAlertStage.ZERO, targetMillis)
            if (zeroKey !in firedKeys) {
                scheduleSingleAlarm(
                    context = appCtx,
                    requestCode = requestCodeFor(target.id, CountdownAlertStage.ZERO),
                    triggerAtMillis = targetMillis,
                    countdownId = target.id,
                    title = target.title,
                    customMessage = target.alertMessage,
                    stage = CountdownAlertStage.ZERO,
                    leadMinutes = leadMins,
                    targetUtcMillis = targetMillis,
                    vibrate = target.vibrateOnAlert,
                    sound = target.alertSoundEnabled,
                    alertKey = zeroKey
                )
            }
        }
    }

    fun scheduleSnoozeAlert(
        context: Context,
        countdownId: String,
        title: String,
        customMessage: String,
        targetUtcMillis: Long,
        vibrate: Boolean,
        sound: Boolean,
        snoozeMinutes: Int = 10
    ) {
        val appCtx = context.applicationContext ?: context
        val triggerAt = System.currentTimeMillis() + snoozeMinutes.coerceIn(1, 120) * 60_000L
        val key = buildAlertKey(countdownId, CountdownAlertStage.SNOOZE, triggerAt)
        scheduleSingleAlarm(
            context = appCtx,
            requestCode = requestCodeFor(countdownId, CountdownAlertStage.SNOOZE),
            triggerAtMillis = triggerAt,
            countdownId = countdownId,
            title = title,
            customMessage = customMessage,
            stage = CountdownAlertStage.SNOOZE,
            leadMinutes = snoozeMinutes,
            targetUtcMillis = targetUtcMillis,
            vibrate = vibrate,
            sound = sound,
            alertKey = key
        )
    }

    fun cancelForCountdown(context: Context, countdownId: String) {
        val appCtx = context.applicationContext ?: context
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        CountdownAlertStage.entries.forEach { stage ->
            val intent = Intent(appCtx, CountdownAlertReceiver::class.java).apply {
                action = ACTION_TRIGGER_COUNTDOWN_ALERT
            }
            val pi = PendingIntent.getBroadcast(
                appCtx,
                requestCodeFor(countdownId, stage),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    private fun scheduleSingleAlarm(
        context: Context,
        requestCode: Int,
        triggerAtMillis: Long,
        countdownId: String,
        title: String,
        customMessage: String,
        stage: CountdownAlertStage,
        leadMinutes: Int,
        targetUtcMillis: Long,
        vibrate: Boolean,
        sound: Boolean,
        alertKey: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, CountdownAlertReceiver::class.java).apply {
            action = ACTION_TRIGGER_COUNTDOWN_ALERT
            putExtra(EXTRA_COUNTDOWN_ID, countdownId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_MESSAGE, customMessage)
            putExtra(EXTRA_STAGE, stage.name)
            putExtra(EXTRA_LEAD_MINS, leadMinutes)
            putExtra(EXTRA_TARGET_UTC, targetUtcMillis)
            putExtra(EXTRA_VIBRATE, vibrate)
            putExtra(EXTRA_SOUND, sound)
            putExtra(EXTRA_ALERT_KEY, alertKey)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }

        if (canExact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                return
            } catch (_: SecurityException) {
                // Fall back to inexact Doze-safe alarm below
            }
        }
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    /**
     * Checks whether any enabled countdown has entered its alert window while the app is open
     * (within a 15-minute catch-up window) and hasn't fired yet.
     */
    fun checkDueForegroundAlerts(
        context: Context,
        prefs: ExamSetPreferences,
        countdowns: List<ExamCountdownTarget>,
        nowMillis: Long
    ): InAppCountdownAlert? {
        val fired = prefs.getFiredCountdownAlertKeys()
        for (target in countdowns) {
            if (!target.alertEnabled) continue
            val targetMillis = target.resolvedTargetMillis() ?: continue
            val leadMins = target.alertLeadMinutes.coerceIn(
                ExamCountdownTarget.MIN_ALERT_LEAD_MINUTES,
                ExamCountdownTarget.MAX_ALERT_LEAD_MINUTES
            )
            val earlyTrigger = targetMillis - leadMins * 60_000L

            if (nowMillis in earlyTrigger..(earlyTrigger + FOREGROUND_CATCHUP_WINDOW_MS) && nowMillis < targetMillis) {
                val earlyKey = buildAlertKey(target.id, CountdownAlertStage.EARLY, earlyTrigger)
                if (earlyKey !in fired) {
                    prefs.markCountdownAlertFired(earlyKey)
                    dispatchAlertFeedback(
                        context = context,
                        countdownId = target.id,
                        title = target.title,
                        customMessage = target.alertMessage,
                        stage = CountdownAlertStage.EARLY,
                        leadMinutes = leadMins,
                        targetUtcMillis = targetMillis,
                        vibrate = target.vibrateOnAlert,
                        sound = target.alertSoundEnabled,
                        postNotification = true
                    )
                    return InAppCountdownAlert(
                        alertKey = earlyKey,
                        countdownId = target.id,
                        title = target.title,
                        customMessage = target.alertMessage,
                        stage = CountdownAlertStage.EARLY,
                        leadMinutes = leadMins,
                        targetUtcMillis = targetMillis,
                        scheduleText = target.formattedTargetSchedule(),
                        vibrate = target.vibrateOnAlert,
                        sound = target.alertSoundEnabled
                    )
                }
            }

            if (target.alertAtZero && nowMillis in targetMillis..(targetMillis + FOREGROUND_CATCHUP_WINDOW_MS)) {
                val zeroKey = buildAlertKey(target.id, CountdownAlertStage.ZERO, targetMillis)
                if (zeroKey !in fired) {
                    prefs.markCountdownAlertFired(zeroKey)
                    dispatchAlertFeedback(
                        context = context,
                        countdownId = target.id,
                        title = target.title,
                        customMessage = target.alertMessage,
                        stage = CountdownAlertStage.ZERO,
                        leadMinutes = leadMins,
                        targetUtcMillis = targetMillis,
                        vibrate = target.vibrateOnAlert,
                        sound = target.alertSoundEnabled,
                        postNotification = true
                    )
                    return InAppCountdownAlert(
                        alertKey = zeroKey,
                        countdownId = target.id,
                        title = target.title,
                        customMessage = target.alertMessage,
                        stage = CountdownAlertStage.ZERO,
                        leadMinutes = leadMins,
                        targetUtcMillis = targetMillis,
                        scheduleText = target.formattedTargetSchedule(),
                        vibrate = target.vibrateOnAlert,
                        sound = target.alertSoundEnabled
                    )
                }
            }
        }
        return null
    }

    fun testAlertNow(
        context: Context,
        title: String,
        customMessage: String,
        leadMinutes: Int,
        vibrate: Boolean,
        sound: Boolean
    ): String {
        val readiness = inspectReadiness(context)
        if (vibrate) vibrateAlertPattern(context)
        if (sound) playAlertChime(context)
        val displayTitle = title.trim().ifBlank { "Sample Exam Countdown" }
        val displayMsg = customMessage.trim().ifBlank {
            "Early alert test (${ExamCountdownTarget.formatLeadDuration(leadMinutes)} ahead)"
        }
        val posted = if (readiness.postNotificationsGranted && readiness.systemNotificationsEnabled && readiness.channelEnabled) {
            postCountdownNotification(
                context = context,
                countdownId = "test-preview",
                title = displayTitle,
                customMessage = displayMsg,
                stage = CountdownAlertStage.EARLY,
                leadMinutes = leadMinutes,
                targetUtcMillis = System.currentTimeMillis() + leadMinutes * 60_000L,
                vibrate = vibrate,
                sound = sound
            )
            true
        } else {
            false
        }
        return when {
            readiness.blockingIssues.isNotEmpty() ->
                readiness.blockingIssues.first()
            posted && vibrate ->
                "Test alert & vibration triggered — notification posted."
            posted ->
                "Test alert notification posted (vibration off)."
            else ->
                "Test triggered, but check notification permissions above."
        }
    }

    internal fun handleReceiverTrigger(context: Context, intent: Intent) {
        val countdownId = intent.getStringExtra(EXTRA_COUNTDOWN_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "Exam Countdown"
        val customMessage = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val stageName = intent.getStringExtra(EXTRA_STAGE) ?: CountdownAlertStage.EARLY.name
        val stage = runCatching { CountdownAlertStage.valueOf(stageName) }.getOrDefault(CountdownAlertStage.EARLY)
        val leadMinutes = intent.getIntExtra(EXTRA_LEAD_MINS, ExamCountdownTarget.DEFAULT_ALERT_LEAD_MINUTES)
        val targetUtcMillis = intent.getLongExtra(EXTRA_TARGET_UTC, System.currentTimeMillis())
        val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)
        val sound = intent.getBooleanExtra(EXTRA_SOUND, true)
        val alertKey = intent.getStringExtra(EXTRA_ALERT_KEY) ?: ""

        val prefs = ExamSetPreferences(context.applicationContext ?: context)
        if (alertKey.isNotBlank()) {
            if (stage != CountdownAlertStage.SNOOZE && alertKey in prefs.getFiredCountdownAlertKeys()) {
                return
            }
            prefs.markCountdownAlertFired(alertKey)
        }

        dispatchAlertFeedback(
            context = context,
            countdownId = countdownId,
            title = title,
            customMessage = customMessage,
            stage = stage,
            leadMinutes = leadMinutes,
            targetUtcMillis = targetUtcMillis,
            vibrate = vibrate,
            sound = sound,
            postNotification = true
        )
    }

    internal fun dispatchAlertFeedback(
        context: Context,
        countdownId: String,
        title: String,
        customMessage: String,
        stage: CountdownAlertStage,
        leadMinutes: Int,
        targetUtcMillis: Long,
        vibrate: Boolean,
        sound: Boolean,
        postNotification: Boolean
    ) {
        if (vibrate) vibrateAlertPattern(context)
        if (sound) playAlertChime(context)
        if (postNotification) {
            postCountdownNotification(
                context = context,
                countdownId = countdownId,
                title = title,
                customMessage = customMessage,
                stage = stage,
                leadMinutes = leadMinutes,
                targetUtcMillis = targetUtcMillis,
                vibrate = vibrate,
                sound = sound
            )
        }
    }

    fun hasHardwareVibrator(context: Context): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.hasVibrator() == true
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.hasVibrator() == true
            }
        }.getOrDefault(false)
    }

    fun vibrateAlertPattern(context: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager ?: return
                val vibrator = vm.defaultVibrator
                if (!vibrator.hasVibrator()) return
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(ALERT_VIBRATION_WAVEFORM, ALERT_VIBRATION_AMPLITUDES, -1)
                } else {
                    VibrationEffect.createWaveform(ALERT_VIBRATION_WAVEFORM, -1)
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
                if (!v.hasVibrator()) return
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val effect = if (v.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(ALERT_VIBRATION_WAVEFORM, ALERT_VIBRATION_AMPLITUDES, -1)
                } else {
                    VibrationEffect.createWaveform(ALERT_VIBRATION_WAVEFORM, -1)
                }
                @Suppress("DEPRECATION")
                v.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
                if (!v.hasVibrator()) return
                @Suppress("DEPRECATION")
                v.vibrate(ALERT_VIBRATION_WAVEFORM, -1)
            }
        }
    }

    fun playAlertChime(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = uri?.let { RingtoneManager.getRingtone(context, it) }
            if (ringtone != null) {
                ringtone.play()
                return
            }
        }
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 75)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 420)
        }
    }

    private fun postCountdownNotification(
        context: Context,
        countdownId: String,
        title: String,
        customMessage: String,
        stage: CountdownAlertStage,
        leadMinutes: Int,
        targetUtcMillis: Long,
        vibrate: Boolean,
        sound: Boolean
    ) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }
            ensureChannels(context)

            val channelId = if (sound) CHANNEL_SOUND_VIBRATE else CHANNEL_VIBRATE_ONLY
            val notifId = requestCodeFor(countdownId, stage) and 0x7FFFFFFF
            val scheduleLabel = StudyDateUtils.formatHumanDateTimeLocal(targetUtcMillis)
            val leadLabel = ExamCountdownTarget.formatLeadDuration(leadMinutes)

            val contentTitle = when (stage) {
                CountdownAlertStage.EARLY -> "$title — in $leadLabel"
                CountdownAlertStage.ZERO -> "$title — Starting Now!"
                CountdownAlertStage.SNOOZE -> "Reminder: $title"
            }

            val body = buildString {
                if (customMessage.isNotBlank()) {
                    append(customMessage.trim())
                    append("\n")
                }
                append("Scheduled: ")
                append(scheduleLabel)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPi = PendingIntent.getActivity(
                context,
                notifId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = Intent(context, CountdownAlertReceiver::class.java).apply {
                action = ACTION_SNOOZE_COUNTDOWN_ALERT
                putExtra(EXTRA_COUNTDOWN_ID, countdownId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, customMessage)
                putExtra(EXTRA_TARGET_UTC, targetUtcMillis)
                putExtra(EXTRA_VIBRATE, vibrate)
                putExtra(EXTRA_SOUND, sound)
                putExtra(EXTRA_NOTIFICATION_ID, notifId)
            }
            val snoozePi = PendingIntent.getBroadcast(
                context,
                (notifId + 17) and 0x7FFFFFFF,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = Intent(context, CountdownAlertReceiver::class.java).apply {
                action = ACTION_DISMISS_COUNTDOWN_ALERT
                putExtra(EXTRA_NOTIFICATION_ID, notifId)
            }
            val dismissPi = PendingIntent.getBroadcast(
                context,
                (notifId + 31) and 0x7FFFFFFF,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(contentTitle)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openPi)
                .addAction(0, "Snooze 10m", snoozePi)
                .addAction(0, "Dismiss", dismissPi)
                .setAutoCancel(true)

            if (vibrate) {
                builder.setVibrate(ALERT_VIBRATION_WAVEFORM)
            } else {
                builder.setVibrate(longArrayOf(0L))
            }
            if (!sound) {
                builder.setSilent(true)
            }

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        }
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (nm.getNotificationChannel(CHANNEL_SOUND_VIBRATE) == null) {
            val channel = NotificationChannel(
                CHANNEL_SOUND_VIBRATE,
                "Countdown Alerts (Sound & Vibrate)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Advance and on-time countdown alerts with sound and vibration."
                enableVibration(true)
                vibrationPattern = ALERT_VIBRATION_WAVEFORM
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }

        if (nm.getNotificationChannel(CHANNEL_VIBRATE_ONLY) == null) {
            val channel = NotificationChannel(
                CHANNEL_VIBRATE_ONLY,
                "Countdown Alerts (Vibrate Only)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Advance and on-time countdown alerts with silent vibration."
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = ALERT_VIBRATION_WAVEFORM
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun buildAlertKey(countdownId: String, stage: CountdownAlertStage, triggerMillis: Long): String {
        return "$countdownId|${stage.name}|$triggerMillis"
    }

    private fun requestCodeFor(countdownId: String, stage: CountdownAlertStage): Int {
        return ("$countdownId:${stage.name}").hashCode()
    }
}

class CountdownAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val act = intent?.action ?: return
        when (act) {
            CountdownAlertScheduler.ACTION_TRIGGER_COUNTDOWN_ALERT -> {
                CountdownAlertScheduler.handleReceiverTrigger(context, intent)
            }
            CountdownAlertScheduler.ACTION_SNOOZE_COUNTDOWN_ALERT -> {
                val notifId = intent.getIntExtra("extra_countdown_notification_id", -1)
                if (notifId > 0) {
                    runCatching { NotificationManagerCompat.from(context).cancel(notifId) }
                }
                val countdownId = intent.getStringExtra("extra_countdown_id") ?: return
                val title = intent.getStringExtra("extra_countdown_title") ?: "Exam Countdown"
                val message = intent.getStringExtra("extra_countdown_message") ?: ""
                val targetUtc = intent.getLongExtra("extra_countdown_target_utc", System.currentTimeMillis())
                val vibrate = intent.getBooleanExtra("extra_countdown_vibrate", true)
                val sound = intent.getBooleanExtra("extra_countdown_sound", true)
                CountdownAlertScheduler.scheduleSnoozeAlert(
                    context = context,
                    countdownId = countdownId,
                    title = title,
                    customMessage = message,
                    targetUtcMillis = targetUtc,
                    vibrate = vibrate,
                    sound = sound,
                    snoozeMinutes = 10
                )
            }
            CountdownAlertScheduler.ACTION_DISMISS_COUNTDOWN_ALERT -> {
                val notifId = intent.getIntExtra("extra_countdown_notification_id", -1)
                if (notifId > 0) {
                    runCatching { NotificationManagerCompat.from(context).cancel(notifId) }
                }
            }
        }
    }
}

class CountdownBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val allowed = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        if (!allowed) return
        val appCtx = context.applicationContext ?: context
        val prefs = ExamSetPreferences(appCtx)
        val countdowns = prefs.getExamCountdowns()
        if (countdowns.isNotEmpty()) {
            CountdownAlertScheduler.rescheduleAll(appCtx, countdowns, System.currentTimeMillis())
        }
    }
}
