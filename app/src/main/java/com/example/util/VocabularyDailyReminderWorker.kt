package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.ExamSetPreferences
import java.util.concurrent.TimeUnit

class VocabularyDailyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val prefs = ExamSetPreferences(context)
            if (!prefs.isVocabularyReminderEnabled()) {
                return Result.success()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    return Result.success()
                }
            }

            TrustedTimeManager.initPersistence(context)
            val nowMillis = TrustedTimeManager.getTrustedTimeMillis()
            val decks = prefs.getVocabularyDecks()
            if (decks.isEmpty()) return Result.success()

            val srsByDeck = prefs.getAllVocabularySrsMaps()
            val summaries = VocabularyEngine.buildLanguageFolderSummaries(
                decks = decks,
                srsByDeck = srsByDeck,
                nowMillis = nowMillis,
                wotdThreshold = WordOfTheDayService.MIN_WORDS_THRESHOLD
            )
            val totalDue = summaries.sumOf { it.dueCount }
            val unlockedLanguages = summaries.filter { it.wotdUnlocked }.map { it.language }

            val preferredVariant = prefs.getPreferredEnglishVariant()
            val wotdSnippet = unlockedLanguages.firstOrNull()?.let { lang ->
                val state = WordOfTheDayService.resolveForLanguage(
                    prefs = prefs,
                    decks = decks,
                    language = lang,
                    preferredEnglishVariant = preferredVariant,
                    cycleNext = false,
                    nowMillis = nowMillis
                )
                if (state is WordOfTheDayUiState.Ready) {
                    "$lang Word of the Day: ${state.entry.word}"
                } else null
            }

            if (totalDue == 0 && wotdSnippet == null) {
                return Result.success()
            }

            ensureChannel(context)
            val title = if (totalDue > 0) {
                "$totalDue vocabulary word(s) ready for review"
            } else {
                "Daily Vocabulary & Word of the Day"
            }
            val body = buildString {
                if (wotdSnippet != null) append(wotdSnippet)
                if (totalDue > 0) {
                    if (isNotEmpty()) append(" • ")
                    append("${summaries.size} language folder(s) active")
                }
            }.ifBlank { "Open ExamSet Vocabulary Hub to practice today." }

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
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Daily Vocabulary Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily reminders for due SM-2 vocabulary words and 00:00 UTC Words of the Day."
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "examset_vocab_daily_reminder_work"
        private const val CHANNEL_ID = "vocab_daily_reminders"
        private const val NOTIFICATION_ID = 4208

        fun syncSchedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context.applicationContext ?: context)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<VocabularyDailyReminderWorker>(
                24L,
                TimeUnit.HOURS
            ).build()
            wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
