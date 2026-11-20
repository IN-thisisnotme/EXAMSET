package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyNoteDao {
    @Query("SELECT * FROM study_notes ORDER BY updatedAt DESC, createdAt DESC")
    fun getAllStudyNotes(): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM study_notes ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun getAllStudyNotesOnce(): List<StudyNoteEntity>

    @Query("SELECT * FROM study_notes WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getNotesForFolder(folderId: String): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM study_notes WHERE linkedPaperId = :paperId ORDER BY updatedAt DESC")
    fun getNotesLinkedToPaper(paperId: Long): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM study_notes WHERE linkedPaperId = :paperId")
    suspend fun getNotesLinkedToPaperOnce(paperId: Long): List<StudyNoteEntity>

    @Query("SELECT * FROM study_notes WHERE folderId = :folderId")
    suspend fun getNotesForFolderOnce(folderId: String): List<StudyNoteEntity>

    @Query("SELECT * FROM study_notes WHERE id = :id LIMIT 1")
    suspend fun getStudyNoteById(id: Long): StudyNoteEntity?

    @Query("SELECT * FROM study_notes WHERE syncUid = :syncUid LIMIT 1")
    suspend fun getStudyNoteBySyncUid(syncUid: String): StudyNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyNote(note: StudyNoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyNotes(notes: List<StudyNoteEntity>)

    @Update
    suspend fun updateStudyNote(note: StudyNoteEntity)

    @Delete
    suspend fun deleteStudyNote(note: StudyNoteEntity)

    @Query("DELETE FROM study_notes WHERE id = :id")
    suspend fun deleteStudyNoteById(id: Long)

    @Query("DELETE FROM study_notes")
    suspend fun deleteAllStudyNotes()

    @Query("UPDATE study_notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun assignNoteToFolder(id: Long, folderId: String?, updatedAt: Long = System.currentTimeMillis())
}
