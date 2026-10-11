package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: Pre-validation - Ensure question_papers exists and has required v1 columns
        val cursorInfo = db.query("PRAGMA table_info(`question_papers`)")
        val existingColumns = mutableSetOf<String>()
        try {
            val nameColIdx = cursorInfo.getColumnIndex("name")
            while (cursorInfo.moveToNext()) {
                if (nameColIdx >= 0) {
                    existingColumns.add(cursorInfo.getString(nameColIdx))
                }
            }
        } finally {
            cursorInfo.close()
        }

        val requiredV1Columns = setOf(
            "id", "title", "subject", "topic", "exam",
            "questionCount", "answerKeyCount", "explanationCount",
            "rawText", "createdAt", "updatedAt"
        )
        val missingColumns = requiredV1Columns - existingColumns
        if (missingColumns.isNotEmpty()) {
            throw IllegalStateException(
                "Unsupported database schema at version 1: missing required columns $missingColumns in question_papers. Migration aborted to prevent data loss."
            )
        }

        // If setNumber already exists, skip table rebuild
        if (existingColumns.contains("setNumber")) {
            return
        }

        // Step 2: Record original row count
        val countCursor = db.query("SELECT COUNT(*) FROM `question_papers`")
        var originalRowCount = 0
        try {
            if (countCursor.moveToFirst()) {
                originalRowCount = countCursor.getInt(0)
            }
        } finally {
            countCursor.close()
        }

        // Step 3: Create staging table question_papers_v2 with exact 12-column Room v5 schema (dflt_value = null)
        db.execSQL(
            "CREATE TABLE `question_papers_v2` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`subject` TEXT NOT NULL, " +
                "`topic` TEXT NOT NULL, " +
                "`setNumber` TEXT NOT NULL, " +
                "`exam` TEXT NOT NULL, " +
                "`questionCount` INTEGER NOT NULL, " +
                "`answerKeyCount` INTEGER NOT NULL, " +
                "`explanationCount` INTEGER NOT NULL, " +
                "`rawText` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL" +
            ")"
        )

        // Step 4: Copy data, populating setNumber with 'Set A'
        db.execSQL(
            "INSERT INTO `question_papers_v2` (" +
                "`id`, `title`, `subject`, `topic`, `setNumber`, `exam`, " +
                "`questionCount`, `answerKeyCount`, `explanationCount`, `rawText`, " +
                "`createdAt`, `updatedAt`" +
            ") " +
            "SELECT `id`, `title`, `subject`, `topic`, 'Set A', `exam`, " +
            "`questionCount`, `answerKeyCount`, `explanationCount`, `rawText`, " +
            "`createdAt`, `updatedAt` " +
            "FROM `question_papers`"
        )

        // Step 5: Post-copy invariant checks before dropping original table
        val newCountCursor = db.query("SELECT COUNT(*) FROM `question_papers_v2`")
        var newRowCount = 0
        try {
            if (newCountCursor.moveToFirst()) {
                newRowCount = newCountCursor.getInt(0)
            }
        } finally {
            newCountCursor.close()
        }

        if (newRowCount != originalRowCount) {
            throw IllegalStateException(
                "Migration 1->2 invariant failure: row count mismatch. Expected $originalRowCount, found $newRowCount. Migration aborted to prevent data loss."
            )
        }

        // Verify all IDs preserved
        val missingIdCursor = db.query(
            "SELECT COUNT(*) FROM `question_papers` WHERE `id` NOT IN (SELECT `id` FROM `question_papers_v2`)"
        )
        var missingIdCount = 0
        try {
            if (missingIdCursor.moveToFirst()) {
                missingIdCount = missingIdCursor.getInt(0)
            }
        } finally {
            missingIdCursor.close()
        }

        if (missingIdCount > 0) {
            throw IllegalStateException(
                "Migration 1->2 invariant failure: $missingIdCount records lost during staging copy. Migration aborted to prevent data loss."
            )
        }

        // Step 6: Drop original table and rename staging table
        db.execSQL("DROP TABLE `question_papers`")
        db.execSQL("ALTER TABLE `question_papers_v2` RENAME TO `question_papers`")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `test_attempts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`paperId` INTEGER NOT NULL, " +
                "`answersJson` TEXT NOT NULL, " +
                "`isSubmitted` INTEGER NOT NULL, " +
                "`score` REAL NOT NULL, " +
                "`correctCount` INTEGER NOT NULL, " +
                "`incorrectCount` INTEGER NOT NULL, " +
                "`unansweredCount` INTEGER NOT NULL, " +
                "`totalQuestions` INTEGER NOT NULL, " +
                "`timeSpentSeconds` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`submittedAt` INTEGER, " +
                "FOREIGN KEY(`paperId`) REFERENCES `question_papers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
            ")"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Version 4 maintenance migration - ensure schema integrity for test_attempts
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `test_attempts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`paperId` INTEGER NOT NULL, " +
                "`answersJson` TEXT NOT NULL, " +
                "`isSubmitted` INTEGER NOT NULL, " +
                "`score` REAL NOT NULL, " +
                "`correctCount` INTEGER NOT NULL, " +
                "`incorrectCount` INTEGER NOT NULL, " +
                "`unansweredCount` INTEGER NOT NULL, " +
                "`totalQuestions` INTEGER NOT NULL, " +
                "`timeSpentSeconds` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`submittedAt` INTEGER, " +
                "FOREIGN KEY(`paperId`) REFERENCES `question_papers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
            ")"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_study_analytics` (" +
                "`dateString` TEXT NOT NULL, " +
                "`totalTestTimeSeconds` INTEGER NOT NULL, " +
                "`testsCompletedCount` INTEGER NOT NULL, " +
                "`testsStartedCount` INTEGER NOT NULL, " +
                "`questionsAttemptedCount` INTEGER NOT NULL, " +
                "`correctAnswersCount` INTEGER NOT NULL, " +
                "`dailyGoalSeconds` INTEGER NOT NULL, " +
                "`lastUpdatedTimestamp` INTEGER NOT NULL, " +
                "PRIMARY KEY(`dateString`)" +
            ")"
        )
    }
}

@Database(
    entities = [PaperEntity::class, AttemptEntity::class, DailyStudyAnalyticsEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paperDao(): PaperDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "examset_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
