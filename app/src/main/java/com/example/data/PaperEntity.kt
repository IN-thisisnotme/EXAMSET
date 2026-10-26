package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.PaperMetadata
import com.example.model.QuestionPaper
import com.example.util.ExamNumberGenerator
import com.example.util.McqParser

@Entity(tableName = "question_papers")
data class PaperEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val subject: String,
    val topic: String = "",
    val setNumber: String = "01",
    val exam: String = "",
    val questionCount: Int,
    val answerKeyCount: Int,
    val explanationCount: Int,
    val rawText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val folderId: String? = null,
    val rollNumber: String = "",
    val bookletNumber: String = "",
    val durationMinutes: Int = 60,
    val positiveMarks: Double = 4.0,
    val negativeMarks: Double = 1.0
)

fun PaperEntity.toQuestionPaper(): QuestionPaper {
    val result = McqParser.parseWithResult(rawText)
    val finalRoll = if (rollNumber.isNotBlank()) rollNumber else ExamNumberGenerator.generateRollNumber()
    val finalBooklet = if (bookletNumber.isNotBlank()) bookletNumber else ExamNumberGenerator.generateBookletNumber()
    return result.paper.copy(
        id = id,
        metadata = PaperMetadata(
            id = "paper-$id",
            title = title,
            subject = subject,
            topic = topic,
            setNumber = setNumber.ifBlank { "01" },
            exam = exam,
            folderId = folderId,
            rollNumber = finalRoll,
            bookletNumber = finalBooklet,
            durationMinutes = if (durationMinutes > 0) durationMinutes else 60,
            positiveMarks = if (positiveMarks > 0.0) positiveMarks else 4.0,
            negativeMarks = if (negativeMarks >= 0.0) negativeMarks else 1.0,
            createdAt = createdAt
        )
    )
}
