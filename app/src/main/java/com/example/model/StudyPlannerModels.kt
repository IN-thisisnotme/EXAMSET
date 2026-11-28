package com.example.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * minSdk 24-safe date & time calculation utilities (avoids java.time API 26 requirements).
 */
object StudyDateUtils {
    private const val DATE_PATTERN = "yyyy-MM-dd"
    private const val MS_PER_DAY = 86_400_000L
    private const val MS_PER_HOUR = 3_600_000L
    private const val MS_PER_MINUTE = 60_000L
    private val TIME_HM_REGEX = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")

    fun formatDateString(millis: Long): String {
        val sdf = SimpleDateFormat(DATE_PATTERN, Locale.US)
        return sdf.format(Date(millis))
    }

    fun formatDateStringGmt(millis: Long): String {
        val sdf = SimpleDateFormat(DATE_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        return sdf.format(Date(millis))
    }

    fun isValidTimeHm(timeStr: String): Boolean {
        return TIME_HM_REGEX.matches(timeStr.trim())
    }

    fun normalizeTimeHmOrBlank(timeStr: String): String {
        val trimmed = timeStr.trim()
        return if (TIME_HM_REGEX.matches(trimmed)) trimmed else ""
    }

    fun localTimezoneShortLabel(nowMillis: Long = System.currentTimeMillis()): String {
        val tz = TimeZone.getDefault()
        val label = tz.getDisplayName(tz.inDaylightTime(Date(nowMillis)), TimeZone.SHORT, Locale.getDefault())
        return label.ifBlank { "Local" }
    }

    fun startOfDayMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun parseDateToStartOfDayMillis(dateStr: String): Long? {
        return parseDateTimeMillis(dateStr = dateStr, timeStr = "00:00", useGmt = false)
    }

    fun parseDateTimeMillis(dateStr: String, timeStr: String = "00:00", useGmt: Boolean = false): Long? {
        val trimmed = dateStr.trim()
        if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(trimmed)) return null
        val parts = trimmed.split("-")
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null

        val cleanTime = timeStr.trim().ifBlank { "00:00" }
        val timeMatch = TIME_HM_REGEX.matchEntire(cleanTime) ?: return null
        val hour = timeMatch.groupValues[1].toInt()
        val minute = timeMatch.groupValues[2].toInt()

        return runCatching {
            val tz = if (useGmt) TimeZone.getTimeZone("GMT") else TimeZone.getDefault()
            val cal = Calendar.getInstance(tz).apply {
                isLenient = false
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        }.getOrNull()
    }

    fun computeDaysRemaining(targetDateStr: String, nowMillis: Long): Long? {
        val targetStart = parseDateToStartOfDayMillis(targetDateStr) ?: return null
        val todayStart = startOfDayMillis(nowMillis)
        val diffMs = targetStart - todayStart
        // Round to nearest whole day to handle DST 23h/25h transitions accurately
        return if (diffMs >= 0L) {
            (diffMs + MS_PER_DAY / 2L) / MS_PER_DAY
        } else {
            -(((-diffMs) + MS_PER_DAY / 2L) / MS_PER_DAY)
        }
    }

    fun addDaysToDateString(baseMillis: Long, daysToAdd: Int): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = startOfDayMillis(baseMillis)
            add(Calendar.DAY_OF_YEAR, daysToAdd)
        }
        return formatDateString(cal.timeInMillis)
    }

    fun formatHumanDate(dateStr: String): String {
        val millis = parseDateToStartOfDayMillis(dateStr) ?: return dateStr
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
    }

    fun formatHumanDateTimeLocal(epochMillis: Long): String {
        val tzLabel = localTimezoneShortLabel(epochMillis)
        val formatted = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(epochMillis))
        return "$formatted $tzLabel"
    }

    fun formatRemainingDuration(diffMillis: Long, compact: Boolean = false): String {
        if (diffMillis == 0L) return "Now!"
        if (diffMillis > 0L) {
            val days = diffMillis / MS_PER_DAY
            val hours = (diffMillis % MS_PER_DAY) / MS_PER_HOUR
            val mins = (diffMillis % MS_PER_HOUR) / MS_PER_MINUTE
            val secs = (diffMillis % MS_PER_MINUTE) / 1000L
            return when {
                days > 0L -> if (compact) "${days}d ${hours}h left" else "${days}d ${hours}h ${mins}m left"
                hours > 0L -> if (compact) "${hours}h ${mins}m left" else "${hours}h ${mins}m ${secs}s left"
                mins > 0L -> if (compact) "${mins}m left" else "${mins}m ${secs}s left"
                else -> "${secs.coerceAtLeast(1L)}s left"
            }
        }
        val absMs = -diffMillis
        val days = absMs / MS_PER_DAY
        val hours = (absMs % MS_PER_DAY) / MS_PER_HOUR
        val mins = (absMs % MS_PER_HOUR) / MS_PER_MINUTE
        return when {
            days > 0L -> if (compact) "${days}d ago" else "${days}d ${hours}h ago"
            hours > 0L -> "${hours}h ${mins}m ago"
            mins > 0L -> "${mins}m ago"
            else -> "Just passed"
        }
    }
}

data class ExamCountdownTarget(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetDateStr: String,
    val subject: String = "",
    val colorHex: String = "#0D9488",
    val isPinned: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val targetTimeStr: String = "",
    val timezoneMode: String = "LOCAL",
    val targetUtcMillis: Long? = null,
    val alertEnabled: Boolean = false,
    val alertLeadMinutes: Int = DEFAULT_ALERT_LEAD_MINUTES,
    val alertMessage: String = "",
    val vibrateOnAlert: Boolean = true,
    val alertSoundEnabled: Boolean = true,
    val alertAtZero: Boolean = true
) {
    fun hasExplicitTime(): Boolean = StudyDateUtils.isValidTimeHm(targetTimeStr)

    fun isGmtAnchored(): Boolean = timezoneMode.equals("GMT", ignoreCase = true)

    fun resolvedTargetMillis(): Long? {
        if (targetUtcMillis != null && targetUtcMillis > 0L) return targetUtcMillis
        return if (hasExplicitTime()) {
            StudyDateUtils.parseDateTimeMillis(targetDateStr, targetTimeStr, useGmt = isGmtAnchored())
        } else {
            StudyDateUtils.parseDateToStartOfDayMillis(targetDateStr)
        }
    }

    fun earlyAlertTriggerMillis(): Long? {
        if (!alertEnabled) return null
        val target = resolvedTargetMillis() ?: return null
        val clampedLead = alertLeadMinutes.coerceIn(MIN_ALERT_LEAD_MINUTES, MAX_ALERT_LEAD_MINUTES)
        return target - clampedLead * 60_000L
    }

    fun remainingMillis(nowMillis: Long): Long? {
        val target = resolvedTargetMillis() ?: return null
        return target - nowMillis
    }

    fun daysRemaining(nowMillis: Long): Long? {
        if (hasExplicitTime() || (targetUtcMillis != null && targetUtcMillis > 0L)) {
            val diff = remainingMillis(nowMillis) ?: return null
            return if (diff >= 0L) diff / 86_400_000L else -((-diff) / 86_400_000L)
        }
        return StudyDateUtils.computeDaysRemaining(targetDateStr, nowMillis)
    }

    fun formattedDaysShort(nowMillis: Long): String {
        if (hasExplicitTime() || (targetUtcMillis != null && targetUtcMillis > 0L)) {
            val diff = remainingMillis(nowMillis) ?: return targetDateStr
            return StudyDateUtils.formatRemainingDuration(diff, compact = true)
        }
        val days = StudyDateUtils.computeDaysRemaining(targetDateStr, nowMillis) ?: return targetDateStr
        return when {
            days > 0L -> "${days}d left"
            days == 0L -> "Today!"
            else -> "${-days}d ago"
        }
    }

    fun formattedCountdownDetailed(nowMillis: Long): String {
        if (hasExplicitTime() || (targetUtcMillis != null && targetUtcMillis > 0L)) {
            val diff = remainingMillis(nowMillis) ?: return targetDateStr
            return StudyDateUtils.formatRemainingDuration(diff, compact = false)
        }
        return formattedDaysShort(nowMillis)
    }

    fun formattedTargetSchedule(): String {
        if (!hasExplicitTime() && (targetUtcMillis == null || targetUtcMillis <= 0L)) {
            return StudyDateUtils.formatHumanDate(targetDateStr)
        }
        val millis = resolvedTargetMillis() ?: return StudyDateUtils.formatHumanDate(targetDateStr)
        val localStr = StudyDateUtils.formatHumanDateTimeLocal(millis)
        val localTz = StudyDateUtils.localTimezoneShortLabel(millis)
        return if (isGmtAnchored() && !localTz.equals("GMT", ignoreCase = true) && !localTz.equals("UTC", ignoreCase = true)) {
            "$localStr (${targetTimeStr.ifBlank { "00:00" }} GMT)"
        } else {
            localStr
        }
    }

    fun formattedBadgeLabel(nowMillis: Long): String {
        return "$title • ${formattedDaysShort(nowMillis)}"
    }

    fun formattedLeadSummary(): String = formatLeadDuration(alertLeadMinutes)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("targetDateStr", targetDateStr)
        put("subject", subject)
        put("colorHex", colorHex)
        put("isPinned", isPinned)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        if (targetTimeStr.isNotBlank()) put("targetTimeStr", targetTimeStr)
        if (timezoneMode.isNotBlank() && timezoneMode != "LOCAL") put("timezoneMode", timezoneMode)
        if (targetUtcMillis != null && targetUtcMillis > 0L) put("targetUtcMillis", targetUtcMillis)
        put("alertEnabled", alertEnabled)
        put("alertLeadMinutes", alertLeadMinutes.coerceIn(MIN_ALERT_LEAD_MINUTES, MAX_ALERT_LEAD_MINUTES))
        if (alertMessage.isNotBlank()) put("alertMessage", alertMessage)
        put("vibrateOnAlert", vibrateOnAlert)
        put("alertSoundEnabled", alertSoundEnabled)
        put("alertAtZero", alertAtZero)
    }

    companion object {
        const val DEFAULT_ALERT_LEAD_MINUTES = 120
        const val MIN_ALERT_LEAD_MINUTES = 1
        const val MAX_ALERT_LEAD_MINUTES = 43200 // 30 days

        fun formatLeadDuration(leadMinutes: Int): String {
            val mins = leadMinutes.coerceIn(MIN_ALERT_LEAD_MINUTES, MAX_ALERT_LEAD_MINUTES)
            val days = mins / (24 * 60)
            val remHours = (mins % (24 * 60)) / 60
            val remMins = mins % 60
            val parts = mutableListOf<String>()
            if (days > 0) parts.add("${days}d")
            if (remHours > 0) parts.add("${remHours}h")
            if (remMins > 0 || parts.isEmpty()) parts.add("${remMins}m")
            return parts.joinToString(" ")
        }

        fun fromJson(obj: JSONObject?): ExamCountdownTarget? {
            if (obj == null) return null
            val title = obj.optString("title", "").trim()
            val dateStr = obj.optString("targetDateStr", "").trim()
            if (title.isBlank() || StudyDateUtils.parseDateToStartOfDayMillis(dateStr) == null) {
                return null
            }
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            val timeStr = StudyDateUtils.normalizeTimeHmOrBlank(obj.optString("targetTimeStr", ""))
            val tzMode = obj.optString("timezoneMode", "LOCAL").trim().uppercase(Locale.US).let {
                if (it == "GMT") "GMT" else "LOCAL"
            }
            val utcMs = obj.optLong("targetUtcMillis", -1L).takeIf { it > 0L }
            return ExamCountdownTarget(
                id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                title = title,
                targetDateStr = dateStr,
                subject = obj.optString("subject", "").trim(),
                colorHex = obj.optString("colorHex", "#0D9488").ifBlank { "#0D9488" },
                isPinned = obj.optBoolean("isPinned", true),
                createdAt = createdAt,
                updatedAt = obj.optLong("updatedAt", createdAt),
                targetTimeStr = timeStr,
                timezoneMode = tzMode,
                targetUtcMillis = utcMs,
                alertEnabled = obj.optBoolean("alertEnabled", false),
                alertLeadMinutes = obj.optInt("alertLeadMinutes", DEFAULT_ALERT_LEAD_MINUTES)
                    .coerceIn(MIN_ALERT_LEAD_MINUTES, MAX_ALERT_LEAD_MINUTES),
                alertMessage = obj.optString("alertMessage", "").trim(),
                vibrateOnAlert = obj.optBoolean("vibrateOnAlert", true),
                alertSoundEnabled = obj.optBoolean("alertSoundEnabled", true),
                alertAtZero = obj.optBoolean("alertAtZero", true)
            )
        }

        fun serializeList(list: List<ExamCountdownTarget>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun deserializeList(raw: String?): List<ExamCountdownTarget> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i -> fromJson(arr.optJSONObject(i)) }
            }.getOrDefault(emptyList())
        }
    }
}

enum class TodoPriority(val label: String, val colorHex: String) {
    HIGH("High", "#DC2626"),
    MEDIUM("Medium", "#D97706"),
    LOW("Low", "#0D9488");

    companion object {
        fun fromString(raw: String?): TodoPriority {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEDIUM
        }
    }
}

enum class TodoFilter(val label: String) {
    ALL("All"),
    TODAY("Today"),
    HIGH_PRIORITY("High Priority"),
    COMPLETED("Completed")
}

enum class TodoLinkType {
    NONE,
    PAPER,
    NOTE,
    VOCAB_DECK;

    companion object {
        fun fromString(raw: String?): TodoLinkType {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
        }
    }
}

data class StudyTodoItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val priority: TodoPriority = TodoPriority.MEDIUM,
    val dueDateStr: String = "",
    val isCompleted: Boolean = false,
    val targetPomodoros: Int = 1,
    val completedPomodoros: Int = 0,
    val linkedType: TodoLinkType = TodoLinkType.NONE,
    val linkedPaperId: Long? = null,
    val linkedNoteId: Long? = null,
    val linkedDeckLanguage: String = "",
    val linkedLabel: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val updatedAt: Long = maxOf(completedAt ?: 0L, createdAt)
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("priority", priority.name)
        put("dueDateStr", dueDateStr)
        put("isCompleted", isCompleted)
        put("targetPomodoros", targetPomodoros)
        put("completedPomodoros", completedPomodoros)
        put("linkedType", linkedType.name)
        if (linkedPaperId != null) put("linkedPaperId", linkedPaperId)
        if (linkedNoteId != null) put("linkedNoteId", linkedNoteId)
        put("linkedDeckLanguage", linkedDeckLanguage)
        put("linkedLabel", linkedLabel)
        put("createdAt", createdAt)
        if (completedAt != null) put("completedAt", completedAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject?): StudyTodoItem? {
            if (obj == null) return null
            val title = obj.optString("title", "").trim()
            if (title.isBlank()) return null
            val paperId = if (obj.has("linkedPaperId") && !obj.isNull("linkedPaperId")) {
                obj.optLong("linkedPaperId", -1L).takeIf { it > 0L }
            } else null
            val noteId = if (obj.has("linkedNoteId") && !obj.isNull("linkedNoteId")) {
                obj.optLong("linkedNoteId", -1L).takeIf { it > 0L }
            } else null
            val completedAt = if (obj.has("completedAt") && !obj.isNull("completedAt")) {
                obj.optLong("completedAt", 0L).takeIf { it > 0L }
            } else null
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            val defaultUpdatedAt = maxOf(completedAt ?: 0L, createdAt)

            return StudyTodoItem(
                id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                title = title,
                priority = TodoPriority.fromString(obj.optString("priority")),
                dueDateStr = obj.optString("dueDateStr", "").trim(),
                isCompleted = obj.optBoolean("isCompleted", false),
                targetPomodoros = obj.optInt("targetPomodoros", 1).coerceIn(1, 12),
                completedPomodoros = obj.optInt("completedPomodoros", 0).coerceAtLeast(0),
                linkedType = TodoLinkType.fromString(obj.optString("linkedType")),
                linkedPaperId = paperId,
                linkedNoteId = noteId,
                linkedDeckLanguage = obj.optString("linkedDeckLanguage", "").trim(),
                linkedLabel = obj.optString("linkedLabel", "").trim(),
                createdAt = createdAt,
                completedAt = completedAt,
                updatedAt = obj.optLong("updatedAt", defaultUpdatedAt)
            )
        }

        fun serializeList(list: List<StudyTodoItem>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun deserializeList(raw: String?): List<StudyTodoItem> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i -> fromJson(arr.optJSONObject(i)) }
            }.getOrDefault(emptyList())
        }
    }
}

enum class PomodoroPreset(
    val label: String,
    val focusMinutes: Int,
    val breakMinutes: Int
) {
    CLASSIC_25_5("25 / 5 Classic", 25, 5),
    DEEP_50_10("50 / 10 Deep", 50, 10),
    MARATHON_90_15("90 / 15 Mock", 90, 15),
    CUSTOM("Custom", 30, 5);

    companion object {
        fun fromString(raw: String?): PomodoroPreset {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CLASSIC_25_5
        }
    }
}

enum class PomodoroPhase {
    IDLE,
    FOCUS,
    BREAK,
    PAUSED
}

enum class PomodoroDndMode(
    val title: String,
    val description: String
) {
    PRIORITY_CALLS(
        title = "Priority Calls Allowed",
        description = "Silences distracting app notifications while allowing starred contacts, repeat callers & alarms."
    ),
    ALARMS_ONLY(
        title = "Alarms Only (Strict)",
        description = "Silences all calls and notifications except device alarms during Focus blocks."
    );

    companion object {
        fun fromString(raw: String?): PomodoroDndMode {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PRIORITY_CALLS
        }
    }
}

data class DndPolicyBackup(
    val wasDndManagedByApp: Boolean = false,
    val previousInterruptionFilter: Int = 1, // NotificationManager.INTERRUPTION_FILTER_ALL
    val managedSessionStartedAt: Long = 0L
)

enum class DueSuggestionCategory(val badgeLabel: String) {
    FLASHCARDS("SM-2 Flashcards"),
    VOCABULARY("Vocab SRS"),
    HEADWORD_INBOX("Headword Queue"),
    PAUSED_TEST("Paused Test")
}

data class StudyDueSuggestion(
    val id: String,
    val category: DueSuggestionCategory,
    val title: String,
    val subtitle: String,
    val count: Int,
    val paperId: Long? = null,
    val noteId: Long? = null,
    val language: String? = null
)
