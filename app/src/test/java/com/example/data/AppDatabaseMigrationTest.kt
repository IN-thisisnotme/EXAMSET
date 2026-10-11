package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppDatabaseMigrationTest {

    private lateinit var context: Context
    private val testDbName = "migration_test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dbFile = context.getDatabasePath(testDbName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @After
    fun tearDown() {
        val dbFile = context.getDatabasePath(testDbName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    private fun createRawDatabase(version: Int, onCreate: (SupportSQLiteDatabase) -> Unit): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    onCreate(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper.writableDatabase
    }

    @Test
    fun testSequentialMigration1To5_producesExactRoomSchemaAndPassesValidation() {
        // Step 1: Create a raw v1 database with historical 11-column question_papers (no setNumber)
        val rawDb = createRawDatabase(1) { db ->
            db.execSQL(
                "CREATE TABLE `question_papers` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`subject` TEXT NOT NULL, " +
                    "`topic` TEXT NOT NULL, " +
                    "`exam` TEXT NOT NULL, " +
                    "`questionCount` INTEGER NOT NULL, " +
                    "`answerKeyCount` INTEGER NOT NULL, " +
                    "`explanationCount` INTEGER NOT NULL, " +
                    "`rawText` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL" +
                ")"
            )
            // Insert seed records
            db.execSQL(
                "INSERT INTO `question_papers` (" +
                    "`id`, `title`, `subject`, `topic`, `exam`, `questionCount`, `answerKeyCount`, `explanationCount`, `rawText`, `createdAt`, `updatedAt`" +
                ") VALUES (" +
                    "101, 'Biology Test 1', 'Biology', 'Genetics', 'NEET', 50, 50, 50, 'Q1: What is DNA?', 1000, 2000" +
                ")"
            )
            db.execSQL(
                "INSERT INTO `question_papers` (" +
                    "`id`, `title`, `subject`, `topic`, `exam`, `questionCount`, `answerKeyCount`, `explanationCount`, `rawText`, `createdAt`, `updatedAt`" +
                ") VALUES (" +
                    "102, 'Physics Test 1', 'Physics', 'Mechanics', 'JEE', 30, 30, 30, 'Q1: Force formula?', 3000, 4000" +
                ")"
            )
        }

        // Step 2: Run migrations sequentially: 1->2, 2->3, 3->4, 4->5
        MIGRATION_1_2.migrate(rawDb)
        MIGRATION_2_3.migrate(rawDb)
        MIGRATION_3_4.migrate(rawDb)
        MIGRATION_4_5.migrate(rawDb)
        rawDb.close()

        // Step 3: Open migrated database with Room at Version 5 (triggers RoomOpenDelegate.onValidateSchema)
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

        runBlocking {
            val dao = roomDb.paperDao()
            val paper101 = dao.getPaperById(101)
            assertNotNull("Paper 101 must be preserved", paper101)
            assertEquals("Biology Test 1", paper101?.title)
            assertEquals("Set A", paper101?.setNumber) // Migrated default value
            assertEquals("NEET", paper101?.exam)
            assertEquals(1000L, paper101?.createdAt)
            assertEquals(2000L, paper101?.updatedAt)

            val paper102 = dao.getPaperById(102)
            assertNotNull("Paper 102 must be preserved", paper102)
            assertEquals("Set A", paper102?.setNumber)

            // Verify test_attempts table accepts queries
            val attempts = dao.getAttemptsForPaper(101)
            assertNotNull(attempts)

            // Verify daily_study_analytics table accepts queries
            val analytics = dao.getDailyAnalytics("2026-10-11")
            assertNull("Analytics row should not exist initially", analytics)
        }

        roomDb.close()
    }

    @Test
    fun testUnsupportedSchemaAtVersion1_throwsExceptionAndRollsBack() {
        // Create an incompatible schema (missing rawText and explanationCount)
        val rawDb = createRawDatabase(1) { db ->
            db.execSQL(
                "CREATE TABLE `question_papers` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`subject` TEXT NOT NULL" +
                ")"
            )
        }

        try {
            MIGRATION_1_2.migrate(rawDb)
            fail("Expected IllegalStateException for unsupported schema missing required columns")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Exception message should mention missing required columns",
                e.message?.contains("missing required columns") == true
            )
        } finally {
            rawDb.close()
        }
    }

    @Test
    fun testCascadeDeleteOfAttemptsWhenPaperDeleted() {
        val roomDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        runBlocking {
            val dao = roomDb.paperDao()
            val paperId = dao.insertPaper(
                PaperEntity(
                    id = 1,
                    title = "Chemistry Exam",
                    subject = "Chemistry",
                    topic = "Organic",
                    setNumber = "Set A",
                    exam = "NEET",
                    questionCount = 10,
                    answerKeyCount = 10,
                    explanationCount = 10,
                    rawText = "Raw test content",
                    createdAt = 1000,
                    updatedAt = 1000
                )
            )

            val attemptId = dao.insertAttempt(
                AttemptEntity(
                    id = 10,
                    paperId = paperId,
                    answersJson = "{\"1\":\"A\"}",
                    isSubmitted = false,
                    score = 0.0,
                    correctCount = 0,
                    incorrectCount = 0,
                    unansweredCount = 10,
                    totalQuestions = 10,
                    timeSpentSeconds = 60,
                    createdAt = 1000
                )
            )

            assertNotNull(dao.getAttemptById(attemptId))

            // Delete paper with attempts atomically
            dao.deleteByIdWithAttempts(paperId)

            assertNull("Paper must be deleted", dao.getPaperById(paperId))
            assertNull("Associated attempt must be deleted", dao.getAttemptById(attemptId))
        }
        roomDb.close()
    }

    @Test
    fun testRecordTestStartEvent_incrementsOnlyTestsStarted() {
        val roomDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        runBlocking {
            val dao = roomDb.paperDao()
            val date = "2026-10-11"

            dao.recordTestStartEvent(date, now = 10000L)
            var analytics = dao.getDailyAnalytics(date)
            assertNotNull(analytics)
            assertEquals(1, analytics?.testsStartedCount)
            assertEquals(0, analytics?.testsCompletedCount)
            assertEquals(0, analytics?.questionsAttemptedCount)
            assertEquals(0L, analytics?.totalTestTimeSeconds)

            // Second test start event
            dao.recordTestStartEvent(date, now = 11000L)
            analytics = dao.getDailyAnalytics(date)
            assertEquals(2, analytics?.testsStartedCount)
            assertEquals(0, analytics?.testsCompletedCount)
        }
        roomDb.close()
    }

    @Test
    fun testAddStudyTimeEvent_accumulatesTimeWithoutResetting() {
        val roomDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        runBlocking {
            val dao = roomDb.paperDao()
            val date = "2026-10-11"

            dao.addStudyTimeEvent(date, additionalSeconds = 120L, now = 1000L)
            var analytics = dao.getDailyAnalytics(date)
            assertNotNull(analytics)
            assertEquals(120L, analytics?.totalTestTimeSeconds)

            dao.addStudyTimeEvent(date, additionalSeconds = 300L, now = 2000L)
            analytics = dao.getDailyAnalytics(date)
            assertEquals(420L, analytics?.totalTestTimeSeconds)
        }
        roomDb.close()
    }

    @Test
    fun testSubmitAttemptWithAnalytics_handlesNonexistentAttemptWithoutAlteringAnalytics() {
        val roomDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        runBlocking {
            val dao = roomDb.paperDao()
            val date = "2026-10-11"

            val success = dao.submitAttemptWithAnalytics(
                attemptId = 99999L,
                answersJson = "{\"1\":\"A\"}",
                score = 80.0,
                correctCount = 4,
                incorrectCount = 1,
                unansweredCount = 0,
                timeSpentSeconds = 300L,
                submittedAt = 1000L,
                dateString = date,
                additionalStudyTimeSeconds = 300L,
                answeredQuestionsCount = 5,
                now = 1000L
            )

            assertFalse("Submission of nonexistent attempt must return false", success)
            val analytics = dao.getDailyAnalytics(date)
            assertNull("Analytics must not be created or modified for nonexistent attempt", analytics)
        }
        roomDb.close()
    }

    @Test
    fun testSubmitAttemptWithAnalytics_idempotentAndPreventsDuplicateCounting() {
        val roomDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        runBlocking {
            val dao = roomDb.paperDao()
            val date = "2026-10-11"

            val paperId = dao.insertPaper(
                PaperEntity(
                    id = 1,
                    title = "Mock Exam",
                    subject = "Math",
                    topic = "Algebra",
                    setNumber = "Set A",
                    exam = "JEE",
                    questionCount = 10,
                    answerKeyCount = 10,
                    explanationCount = 10,
                    rawText = "Algebra equations",
                    createdAt = 1000,
                    updatedAt = 1000
                )
            )

            val attemptId = dao.insertAttempt(
                AttemptEntity(
                    id = 1,
                    paperId = paperId,
                    answersJson = "{}",
                    isSubmitted = false,
                    score = 0.0,
                    correctCount = 0,
                    incorrectCount = 0,
                    unansweredCount = 10,
                    totalQuestions = 10,
                    timeSpentSeconds = 0,
                    createdAt = 1000
                )
            )

            // First submission: should succeed and record analytics
            val firstSubmit = dao.submitAttemptWithAnalytics(
                attemptId = attemptId,
                answersJson = "{\"1\":\"A\",\"2\":\"B\"}",
                score = 20.0,
                correctCount = 2,
                incorrectCount = 0,
                unansweredCount = 8,
                timeSpentSeconds = 150L,
                submittedAt = 2000L,
                dateString = date,
                additionalStudyTimeSeconds = 150L,
                answeredQuestionsCount = 2,
                now = 2000L
            )
            assertTrue("First submission must succeed", firstSubmit)

            var analytics = dao.getDailyAnalytics(date)
            assertNotNull(analytics)
            assertEquals(1, analytics?.testsCompletedCount)
            assertEquals(2, analytics?.questionsAttemptedCount)
            assertEquals(2, analytics?.correctAnswersCount)
            assertEquals(150L, analytics?.totalTestTimeSeconds)

            // Second submission of the same attempt: must be rejected and not double-count
            val secondSubmit = dao.submitAttemptWithAnalytics(
                attemptId = attemptId,
                answersJson = "{\"1\":\"A\",\"2\":\"B\"}",
                score = 20.0,
                correctCount = 2,
                incorrectCount = 0,
                unansweredCount = 8,
                timeSpentSeconds = 150L,
                submittedAt = 2500L,
                dateString = date,
                additionalStudyTimeSeconds = 150L,
                answeredQuestionsCount = 2,
                now = 2500L
            )
            assertFalse("Second submission must be rejected as already submitted", secondSubmit)

            // Re-fetch analytics: counts and time must remain exactly as after first submission
            analytics = dao.getDailyAnalytics(date)
            assertNotNull(analytics)
            assertEquals("testsCompletedCount must not double count", 1, analytics?.testsCompletedCount)
            assertEquals("questionsAttemptedCount must not double count", 2, analytics?.questionsAttemptedCount)
            assertEquals("correctAnswersCount must not double count", 2, analytics?.correctAnswersCount)
            assertEquals("totalTestTimeSeconds must not double count", 150L, analytics?.totalTestTimeSeconds)
        }
        roomDb.close()
    }
}
