package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "daily_study_analytics")
data class DailyStudyAnalyticsEntity(
    @PrimaryKey
    val dateString: String, // format: "yyyy-MM-dd", e.g. "2026-09-24"
    val totalTestTimeSeconds: Long = 0L,
    val testsCompletedCount: Int = 0,
    val testsStartedCount: Int = 0,
    val questionsAttemptedCount: Int = 0,
    val correctAnswersCount: Int = 0,
    val dailyGoalSeconds: Long = 1800L, // 30 minutes default
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
) {
    val accuracyPercentage: Float
        get() = if (questionsAttemptedCount > 0) {
            (correctAnswersCount.toFloat() / questionsAttemptedCount) * 100f
        } else 0f

    val goalProgressFraction: Float
        get() = if (dailyGoalSeconds > 0) {
            (totalTestTimeSeconds.toFloat() / dailyGoalSeconds).coerceIn(0f, 1f)
        } else 0f

    val isGoalAchieved: Boolean
        get() = totalTestTimeSeconds >= dailyGoalSeconds && dailyGoalSeconds > 0

    fun formatDuration(): String {
        val hours = totalTestTimeSeconds / 3600
        val minutes = (totalTestTimeSeconds % 3600) / 60
        val seconds = totalTestTimeSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.getDefault(), "%dm %02ds", minutes, seconds)
            else -> String.format(Locale.getDefault(), "%ds", seconds)
        }
    }

    fun formatDigitalClock(): String {
        val hours = totalTestTimeSeconds / 3600
        val minutes = (totalTestTimeSeconds % 3600) / 60
        val seconds = totalTestTimeSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}
