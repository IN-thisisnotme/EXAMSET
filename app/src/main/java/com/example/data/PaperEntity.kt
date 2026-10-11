package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "question_papers")
data class PaperEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val subject: String,
    val topic: String = "",
    val setNumber: String = "Set A",
    val exam: String = "",
    val questionCount: Int,
    val answerKeyCount: Int,
    val explanationCount: Int,
    val rawText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
