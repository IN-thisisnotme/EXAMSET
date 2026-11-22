package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.util.AnswerKeyMatcher
import org.json.JSONObject

@Entity(
    tableName = "test_attempts",
    foreignKeys = [
        ForeignKey(
            entity = PaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["paperId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val paperId: Long,
    val answersJson: String = "{}",
    val isSubmitted: Boolean = false,
    val score: Double = 0.0,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val unansweredCount: Int = 0,
    val totalQuestions: Int = 0,
    val timeSpentSeconds: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val submittedAt: Long? = null
) {
    fun getFormattedScore(): String {
        return String.format(java.util.Locale.US, "%.2f", score)
    }

    fun getAnswersMap(): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        try {
            val json = JSONObject(answersJson)
            json.keys().forEach { key ->
                val qNum = key.toIntOrNull()
                if (qNum != null) {
                    map[qNum] = json.getString(key)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return map
    }

    fun getCandidateRollNumber(): String {
        return try {
            JSONObject(answersJson).optString("_rollNumber", "")
        } catch (e: Exception) { "" }
    }

    fun getCandidateBookletNumber(): String {
        return try {
            JSONObject(answersJson).optString("_bookletNumber", "")
        } catch (e: Exception) { "" }
    }

    fun getCandidateBookletCode(): String {
        return try {
            JSONObject(answersJson).optString("_bookletCode", "")
        } catch (e: Exception) { "" }
    }

    fun getQuestionTimesMap(): Map<Int, Long> {
        val map = mutableMapOf<Int, Long>()
        try {
            val json = JSONObject(answersJson)
            val qTimeObj = json.optJSONObject("_qTime") ?: return emptyMap()
            qTimeObj.keys().forEach { key ->
                val qNum = key.toIntOrNull()
                if (qNum != null) {
                    val secs = qTimeObj.optLong(key, 0L)
                    if (secs > 0L) {
                        map[qNum] = secs
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore malformed legacy metadata
        }
        return map
    }

    companion object {
        fun buildAnswersJson(
            map: Map<Int, String>,
            rollNumber: String? = null,
            bookletNumber: String? = null,
            bookletCode: String? = null,
            questionTimes: Map<Int, Long>? = null
        ): String {
            val json = JSONObject()
            try {
                map.forEach { (k, v) -> json.put(k.toString(), v) }
                if (!rollNumber.isNullOrBlank()) json.put("_rollNumber", rollNumber)
                if (!bookletNumber.isNullOrBlank()) json.put("_bookletNumber", bookletNumber)
                if (!bookletCode.isNullOrBlank()) json.put("_bookletCode", bookletCode)
                if (!questionTimes.isNullOrEmpty()) {
                    val qTimeObj = JSONObject()
                    var hasAny = false
                    questionTimes.forEach { (qNum, secs) ->
                        if (qNum > 0 && secs > 0L) {
                            qTimeObj.put(qNum.toString(), secs)
                            hasAny = true
                        }
                    }
                    if (hasAny) {
                        json.put("_qTime", qTimeObj)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            return json.toString()
        }
    }
}

data class SlowQuestionPacingEntry(
    val questionNum: Int,
    val secondsSpent: Long,
    val isCorrect: Boolean?,
    val questionPreview: String
)

data class AttemptPacingSummary(
    val hasPacingData: Boolean = false,
    val trackedQuestionsCount: Int = 0,
    val totalTrackedSeconds: Long = 0L,
    val averageSecondsPerQuestion: Double = 0.0,
    val slowestQuestions: List<SlowQuestionPacingEntry> = emptyList(),
    val correctAvgSeconds: Double? = null,
    val incorrectAvgSeconds: Double? = null
)

object QuestionPacingAnalyzer {
    fun computeSummary(
        paper: com.example.model.QuestionPaper?,
        attempt: AttemptEntity?
    ): AttemptPacingSummary {
        if (attempt == null) return AttemptPacingSummary()
        return computeSummary(
            paper = paper,
            answers = attempt.getAnswersMap(),
            questionTimes = attempt.getQuestionTimesMap()
        )
    }

    fun computeSummary(
        paper: com.example.model.QuestionPaper?,
        answers: Map<Int, String>,
        questionTimes: Map<Int, Long>,
        msqPolicyOverride: com.example.model.MsqScoringPolicy? = null
    ): AttemptPacingSummary {
        val validTimes = questionTimes.filter { (qNum, secs) -> qNum > 0 && secs > 0L }
        if (validTimes.isEmpty()) return AttemptPacingSummary()

        val questionsByNum = paper?.questions?.associateBy { it.num }.orEmpty()
        val totalSecs = validTimes.values.sum()
        val avgSecs = totalSecs.toDouble() / validTimes.size.toDouble()

        val correctTimes = mutableListOf<Long>()
        val incorrectTimes = mutableListOf<Long>()

        val msqPolicy = msqPolicyOverride ?: paper?.metadata?.msqScoringPolicy ?: com.example.model.MsqScoringPolicy.ALL_OR_NOTHING
        val entries = validTimes.map { (qNum, secs) ->
            val q = questionsByNum[qNum]
            val userAns = answers[qNum]
            val isCorrect: Boolean? = if (q != null) {
                when {
                    q.isNeutralized -> true
                    userAns.isNullOrBlank() -> null
                    q.correctAnswer.isNullOrBlank() -> null
                    else -> AnswerKeyMatcher.scoreQuestion(
                        question = q,
                        userAnswer = userAns,
                        positiveMarks = paper?.metadata?.positiveMarks ?: 4.0,
                        negativeMarks = paper?.metadata?.negativeMarks ?: 1.0,
                        unattendedMarks = paper?.metadata?.unattendedMarks ?: 0.0,
                        msqPolicy = msqPolicy
                    ).isFullyCorrect
                }
            } else null

            when (isCorrect) {
                true -> correctTimes.add(secs)
                false -> incorrectTimes.add(secs)
                null -> Unit
            }

            SlowQuestionPacingEntry(
                questionNum = qNum,
                secondsSpent = secs,
                isCorrect = isCorrect,
                questionPreview = q?.question?.replace(Regex("\\s+"), " ")?.trim()?.take(72).orEmpty()
            )
        }

        val slowest = entries
            .sortedWith(compareByDescending<SlowQuestionPacingEntry> { it.secondsSpent }.thenBy { it.questionNum })
            .take(3)

        return AttemptPacingSummary(
            hasPacingData = true,
            trackedQuestionsCount = validTimes.size,
            totalTrackedSeconds = totalSecs,
            averageSecondsPerQuestion = avgSecs,
            slowestQuestions = slowest,
            correctAvgSeconds = if (correctTimes.isNotEmpty()) correctTimes.average() else null,
            incorrectAvgSeconds = if (incorrectTimes.isNotEmpty()) incorrectTimes.average() else null
        )
    }
}

