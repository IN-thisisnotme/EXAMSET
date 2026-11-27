package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.KnowledgeCollectionType
import com.example.model.KnowledgeFormatRule
import org.json.JSONArray
import java.util.UUID

@Entity(tableName = "study_notes")
data class StudyNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderId: String? = null,
    val title: String,
    val subject: String = "",
    val course: String = "",
    val sourceAi: String = "One-Liners",
    val rawContent: String,
    val bookmarkedLinesJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val collectionType: String = KnowledgeCollectionType.ONE_LINERS.code,
    val customLabel: String = "",
    val linkedPaperId: Long? = null,
    val formatRuleJson: String = "{}",
    val masteredBlocksJson: String = "[]",
    val syncUid: String = UUID.randomUUID().toString()
) {
    fun getBookmarkedIndices(): Set<Int> {
        return parseIntSet(bookmarkedLinesJson)
    }

    fun withBookmarkedIndices(indices: Set<Int>): StudyNoteEntity {
        return copy(
            bookmarkedLinesJson = serializeIntSet(indices),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun getMasteredIndices(): Set<Int> {
        return parseIntSet(masteredBlocksJson)
    }

    fun withMasteredIndices(indices: Set<Int>): StudyNoteEntity {
        return copy(
            masteredBlocksJson = serializeIntSet(indices),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun parsedFormatRule(): KnowledgeFormatRule {
        return KnowledgeFormatRule.fromJson(formatRuleJson)
    }

    fun resolvedCollectionType(): KnowledgeCollectionType {
        return KnowledgeCollectionType.fromCode(collectionType)
    }

    fun effectiveBadgeLabel(): String {
        if (customLabel.isNotBlank()) return customLabel.trim()
        val resolved = resolvedCollectionType()
        return if (resolved != KnowledgeCollectionType.ONE_LINERS || sourceAi.isBlank()) {
            resolved.label
        } else {
            sourceAi
        }
    }

    fun effectiveCourseLabel(fallbackFolderRoot: String = ""): String {
        val explicit = course.trim()
        if (explicit.isNotBlank()) return explicit
        if (subject.contains(">")) {
            val prefix = subject.substringBefore(">").trim()
            if (prefix.isNotBlank()) return prefix
        }
        return fallbackFolderRoot.trim().ifBlank { "General Studies" }
    }

    fun effectiveSubjectLabel(): String {
        val raw = subject.trim()
        val clean = if (raw.contains(">")) raw.substringAfterLast(">").trim() else raw
        return clean.ifBlank { "General Notes" }
    }

    companion object {
        private fun parseIntSet(json: String): Set<Int> {
            return try {
                val arr = JSONArray(json)
                buildSet {
                    for (i in 0 until arr.length()) {
                        add(arr.getInt(i))
                    }
                }
            } catch (_: Exception) {
                emptySet()
            }
        }

        private fun serializeIntSet(indices: Set<Int>): String {
            val arr = JSONArray()
            indices.sorted().forEach { arr.put(it) }
            return arr.toString()
        }
    }
}
