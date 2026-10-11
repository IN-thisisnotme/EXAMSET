package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    // --- Atomic Helper Queries for Daily Analytics ---

    @Query("UPDATE daily_study_analytics SET testsStartedCount = testsStartedCount + 1, lastUpdatedTimestamp = :now WHERE dateString = :dateString")
    suspend fun incrementTestsStartedAtomic(dateString: String, now: Long): Int

    @Query("UPDATE daily_study_analytics SET totalTestTimeSeconds = totalTestTimeSeconds + :additionalSeconds, lastUpdatedTimestamp = :now WHERE dateString = :dateString")
    suspend fun incrementStudyTimeAtomic(dateString: String, additionalSeconds: Long, now: Long): Int

    @Query("UPDATE daily_study_analytics SET testsCompletedCount = testsCompletedCount + 1, questionsAttemptedCount = questionsAttemptedCount + :questionsAttempted, correctAnswersCount = correctAnswersCount + :correctCount, totalTestTimeSeconds = totalTestTimeSeconds + :additionalSeconds, lastUpdatedTimestamp = :now WHERE dateString = :dateString")
    suspend fun recordCompletionAtomic(
        dateString: String,
        questionsAttempted: Int,
        correctCount: Int,
        additionalSeconds: Long,
        now: Long
    ): Int

    @Query("DELETE FROM test_attempts WHERE paperId = :paperId")
    suspend fun deleteAttemptsByPaperId(paperId: Long)

    // --- Atomic Transaction Methods ---

    @Transaction
    suspend fun recordTestStartEvent(dateString: String, now: Long = System.currentTimeMillis()) {
        val rowsUpdated = incrementTestsStartedAtomic(dateString, now)
        if (rowsUpdated == 0) {
            val initial = DailyStudyAnalyticsEntity(
                dateString = dateString,
                totalTestTimeSeconds = 0,
                testsCompletedCount = 0,
                testsStartedCount = 1,
                questionsAttemptedCount = 0,
                correctAnswersCount = 0,
                dailyGoalSeconds = 1800,
                lastUpdatedTimestamp = now
            )
            upsertDailyAnalytics(initial)
        }
    }

    @Transaction
    suspend fun addStudyTimeEvent(dateString: String, additionalSeconds: Long, now: Long = System.currentTimeMillis()) {
        if (additionalSeconds <= 0) return
        val rowsUpdated = incrementStudyTimeAtomic(dateString, additionalSeconds, now)
        if (rowsUpdated == 0) {
            val initial = DailyStudyAnalyticsEntity(
                dateString = dateString,
                totalTestTimeSeconds = additionalSeconds,
                testsCompletedCount = 0,
                testsStartedCount = 0,
                questionsAttemptedCount = 0,
                correctAnswersCount = 0,
                dailyGoalSeconds = 1800,
                lastUpdatedTimestamp = now
            )
            upsertDailyAnalytics(initial)
        }
    }

    @Transaction
    suspend fun submitAttemptWithAnalytics(
        attemptId: Long,
        answersJson: String,
        score: Double,
        correctCount: Int,
        incorrectCount: Int,
        unansweredCount: Int,
        timeSpentSeconds: Long,
        submittedAt: Long,
        dateString: String,
        additionalStudyTimeSeconds: Long,
        answeredQuestionsCount: Int,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        // Step 1: Check if attempt exists
        val existingAttempt = getAttemptById(attemptId) ?: return false

        // Step 2: Idempotency check - if already submitted, do not re-increment analytics
        if (existingAttempt.isSubmitted) {
            return false
        }

        // Step 3: Update attempt record to submitted
        val updatedAttempt = existingAttempt.copy(
            answersJson = answersJson,
            isSubmitted = true,
            score = score,
            correctCount = correctCount,
            incorrectCount = incorrectCount,
            unansweredCount = unansweredCount,
            timeSpentSeconds = timeSpentSeconds,
            submittedAt = submittedAt
        )
        updateAttempt(updatedAttempt)

        // Step 4: Atomically update daily study analytics
        val rowsUpdated = recordCompletionAtomic(
            dateString = dateString,
            questionsAttempted = answeredQuestionsCount,
            correctCount = correctCount,
            additionalSeconds = maxOf(0L, additionalStudyTimeSeconds),
            now = now
        )
        if (rowsUpdated == 0) {
            val initial = DailyStudyAnalyticsEntity(
                dateString = dateString,
                totalTestTimeSeconds = maxOf(0L, additionalStudyTimeSeconds),
                testsCompletedCount = 1,
                testsStartedCount = 1,
                questionsAttemptedCount = answeredQuestionsCount,
                correctAnswersCount = correctCount,
                dailyGoalSeconds = 1800,
                lastUpdatedTimestamp = now
            )
            upsertDailyAnalytics(initial)
        }
        return true
    }

    @Transaction
    suspend fun deletePaperWithAttempts(paper: PaperEntity) {
        deleteAttemptsByPaperId(paper.id)
        deletePaper(paper)
    }

    @Transaction
    suspend fun deleteByIdWithAttempts(id: Long) {
        deleteAttemptsByPaperId(id)
        deleteById(id)
    }

    @Transaction
    suspend fun importPapersTransaction(papers: List<PaperEntity>): List<Long> {
        val insertedIds = mutableListOf<Long>()
        for (paper in papers) {
            val id = insertPaper(paper)
            insertedIds.add(id)
        }
        return insertedIds
    }
}
