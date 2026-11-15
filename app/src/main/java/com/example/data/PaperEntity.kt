package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.PaperMetadata
import com.example.model.QuestionPaper
import com.example.util.ExamNumberGenerator
import com.example.util.McqParser

import com.example.model.PaperCategory
import java.util.Locale

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
    val negativeMarks: Double = 1.0,
    val unattendedMarks: Double = 0.0,
    val manualCategory: String = ""
)

fun inferDefaultPaperCategory(
    title: String,
    subject: String,
    topic: String,
    setNumber: String,
    questionCount: Int
): PaperCategory {
    val text = "$title $subject $topic $setNumber".lowercase(Locale.getDefault())
    return when {
        "pyq" in text || "pyp" in text || "previous year" in text || "official" in text ||
            Regex("""\b20(1\d|2\d)\b""").containsMatchIn(text) -> PaperCategory.PYQ
        questionCount == 0 || "notes" in text || "study note" in text || "one-liner" in text -> PaperCategory.STUDY_NOTES
        "mock" in text || "flt" in text || "full length" in text || "model" in text || "all india" in text -> PaperCategory.MOCK_TEST
        "sectional" in text || "drill" in text || "speed" in text || "chapter" in text || "topic" in text -> PaperCategory.SECTIONAL
        questionCount >= 40 -> PaperCategory.MOCK_TEST
        else -> PaperCategory.MOCK_TEST
    }
}

fun PaperEntity.effectiveCategory(): PaperCategory {
    val explicit = PaperCategory.fromCode(manualCategory)
    if (explicit != PaperCategory.AUTO) return explicit
    return inferDefaultPaperCategory(title, subject, topic, setNumber, questionCount)
}

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
            manualCategory = manualCategory,
            rollNumber = finalRoll,
            bookletNumber = finalBooklet,
            durationMinutes = if (durationMinutes > 0) durationMinutes else 60,
            positiveMarks = if (positiveMarks > 0.0) positiveMarks else 4.0,
            negativeMarks = if (negativeMarks >= 0.0) negativeMarks else 1.0,
            unattendedMarks = if (unattendedMarks >= 0.0) unattendedMarks else 0.0,
            createdAt = createdAt
        )
    )
}
