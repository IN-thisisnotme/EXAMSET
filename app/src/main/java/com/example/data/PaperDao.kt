package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PaperDao {
    @Query("SELECT * FROM question_papers ORDER BY updatedAt DESC, createdAt DESC")
    fun getAllPapers(): Flow<List<PaperEntity>>

    @Query("SELECT * FROM question_papers WHERE id = :id")
    suspend fun getPaperById(id: Long): PaperEntity?

    @Query("SELECT * FROM question_papers WHERE rawText = :rawText LIMIT 1")
    suspend fun findPaperByRawText(rawText: String): PaperEntity?

    @Query("SELECT * FROM question_papers WHERE title = :title AND subject = :subject AND setNumber = :setNumber LIMIT 1")
    suspend fun findPaperByMetadata(title: String, subject: String, setNumber: String): PaperEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaper(paper: PaperEntity): Long

    @Update
    suspend fun updatePaper(paper: PaperEntity)

    @Query("UPDATE question_papers SET title = :title, subject = :subject, topic = :topic, setNumber = :setNumber, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMetadata(id: Long, title: String, subject: String, topic: String, setNumber: String, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deletePaper(paper: PaperEntity)

    @Query("DELETE FROM question_papers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: AttemptEntity): Long

    @Update
    suspend fun updateAttempt(attempt: AttemptEntity)

    @Query("SELECT * FROM test_attempts ORDER BY createdAt DESC")
    fun getAllAttempts(): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM test_attempts WHERE paperId = :paperId ORDER BY createdAt DESC")
    fun getAttemptsForPaper(paperId: Long): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM test_attempts WHERE id = :attemptId")
    suspend fun getAttemptById(attemptId: Long): AttemptEntity?

    @Query("SELECT * FROM test_attempts WHERE paperId = :paperId AND isSubmitted = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestUnsubmittedAttempt(paperId: Long): AttemptEntity?

    @Query("SELECT * FROM daily_study_analytics WHERE dateString = :dateString LIMIT 1")
    fun getDailyAnalyticsFlow(dateString: String): Flow<DailyStudyAnalyticsEntity?>

    @Query("SELECT * FROM daily_study_analytics WHERE dateString = :dateString LIMIT 1")
    suspend fun getDailyAnalytics(dateString: String): DailyStudyAnalyticsEntity?

    @Query("SELECT * FROM daily_study_analytics ORDER BY dateString DESC")
    fun getAllDailyAnalytics(): Flow<List<DailyStudyAnalyticsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyAnalytics(analytics: DailyStudyAnalyticsEntity)

    @Query("DELETE FROM daily_study_analytics WHERE dateString = :dateString")
    suspend fun deleteDailyAnalytics(dateString: String)
}
