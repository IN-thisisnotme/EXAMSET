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

    @Query("SELECT * FROM study_notes WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getNotesForFolder(folderId: String): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM study_notes WHERE id = :id LIMIT 1")
    suspend fun getStudyNoteById(id: Long): StudyNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyNote(note: StudyNoteEntity): Long

    @Update
    suspend fun updateStudyNote(note: StudyNoteEntity)

    @Delete
    suspend fun deleteStudyNote(note: StudyNoteEntity)

    @Query("DELETE FROM study_notes WHERE id = :id")
    suspend fun deleteStudyNoteById(id: Long)

    @Query("UPDATE study_notes SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun assignNoteToFolder(id: Long, folderId: String?, updatedAt: Long = System.currentTimeMillis())
}
