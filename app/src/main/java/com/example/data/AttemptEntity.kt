package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
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
        return if (score % 1.0 == 0.0) score.toInt().toString() else String.format(java.util.Locale.US, "%.2f", score)
    }

    fun getAnswersMap(): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        try {
            val json = JSONObject(answersJson)
            json.keys().forEach { key ->
                map[key.toInt()] = json.getString(key)
            }
        } catch (e: Exception) {
            // Ignore
        }
        return map
    }

    companion object {
        fun buildAnswersJson(map: Map<Int, String>): String {
            val json = JSONObject()
            try {
                map.forEach { (k, v) -> json.put(k.toString(), v) }
            } catch (e: Exception) {
                // Ignore
            }
            return json.toString()
        }
    }
}
