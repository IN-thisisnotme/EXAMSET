package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray

@Entity(tableName = "study_notes")
data class StudyNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderId: String? = null,
    val title: String,
    val subject: String = "",
    val sourceAi: String = "One-Liners",
    val rawContent: String,
    val bookmarkedLinesJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getBookmarkedIndices(): Set<Int> {
        return try {
            val arr = JSONArray(bookmarkedLinesJson)
            buildSet {
                for (i in 0 until arr.length()) {
                    add(arr.getInt(i))
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun withBookmarkedIndices(indices: Set<Int>): StudyNoteEntity {
        val arr = JSONArray()
        indices.sorted().forEach { arr.put(it) }
        return copy(bookmarkedLinesJson = arr.toString(), updatedAt = System.currentTimeMillis())
    }
}
