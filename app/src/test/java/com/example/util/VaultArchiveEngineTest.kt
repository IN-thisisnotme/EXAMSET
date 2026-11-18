package com.example.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AttemptEntity
import com.example.data.DailyStudyAnalyticsEntity
import com.example.data.ExamSetPreferences
import com.example.data.PaperEntity
import com.example.data.StudyNoteEntity
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import com.example.model.FolderItem
import com.example.model.RestoreMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultArchiveEngineTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var appPrefs: ExamSetPreferences
    private lateinit var themePrefs: ThemePreferences
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appPrefs = ExamSetPreferences(context)
        themePrefs = ThemePreferences(context)
        appPrefs.saveCustomFolders(emptyList())
        tempDir = File(context.cacheDir, "vault_test_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `createVaultArchive packs full library with SHA-256 manifest and restores cleanly`() = runBlocking {
        val folder = FolderItem(id = "fld_physics", name = "Physics Vault", color = "#0284C7")
        appPrefs.saveCustomFolders(listOf(folder))
        appPrefs.setDailyGoalMinutes(45)
        themePrefs.setThemeMode(ThemeMode.DARK)

        val paperId = db.paperDao().insertPaper(
            PaperEntity(
                title = "Mechanics Mock 1",
                subject = "Physics",
                topic = "Kinematics",
                setNumber = "01",
                exam = "JEE",
                questionCount = 2,
                answerKeyCount = 2,
                explanationCount = 2,
                rawText = "1. Speed unit?\n(A) m/s\n(B) kg\n(C) N\n(D) J\nAnswer Key:\n1: A",
                folderId = folder.id,
                bookletNumber = "BK-9001",
                unattendedMarks = 0.25
            )
        )

        db.paperDao().insertAttempt(
            AttemptEntity(
                paperId = paperId,
                answersJson = "{\"1\":\"A\"}",
                isSubmitted = true,
                score = 4.0,
                correctCount = 1,
                incorrectCount = 0,
                unansweredCount = 1,
                totalQuestions = 2,
                timeSpentSeconds = 120L
            )
        )

        db.paperDao().upsertDailyAnalytics(
            DailyStudyAnalyticsEntity(
                dateString = "2026-11-14",
                totalTestTimeSeconds = 600L,
                testsCompletedCount = 1,
                testsStartedCount = 1,
                questionsAttemptedCount = 2,
                correctAnswersCount = 1,
                dailyGoalSeconds = 2700L
            )
        )

        db.studyNoteDao().insertStudyNote(
            StudyNoteEntity(
                folderId = folder.id,
                title = "Kinematics Multi-Liners",
                subject = "Physics",
                sourceAi = "ChatGPT",
                rawContent = "1. **Projectile Range** -> Maximum at 45 degrees.\n  - Formula: ${'$'}R = u^2 \\sin(2\\theta)/g${'$'}",
                collectionType = "MULTI_LINER",
                customLabel = "Formula Codex",
                linkedPaperId = paperId,
                masteredBlocksJson = "[0]",
                syncUid = "uid-kinematics-1"
            )
        )

        val archiveFile = File(tempDir, "test_backup.examset.zip")
        val manifest = VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, archiveFile)

        assertTrue(archiveFile.exists() && archiveFile.length() > 0L)
        assertEquals(1, manifest.counts.papersCount)
        assertEquals(1, manifest.counts.notesCount)
        assertEquals(1, manifest.counts.foldersCount)
        assertEquals(1, manifest.counts.attemptsCount)
        assertEquals(1, manifest.counts.analyticsDaysCount)
        assertTrue(manifest.entryChecksumsSha256.containsKey("papers.ndjson"))
        assertTrue(manifest.entryChecksumsSha256.containsKey("notes.ndjson"))

        // Inspect archive integrity
        val preview = VaultArchiveEngine.inspectLocalFile(archiveFile)
        assertFalse(preview.isLegacyJson)
        assertTrue("Manifest SHA-256 verification must pass", preview.checksumVerified)
        assertEquals(1, preview.manifest.counts.papersCount)
        assertEquals(1, preview.manifest.counts.notesCount)

        // Clear and restore with FULL_REPLACE
        db.paperDao().deleteAllAttempts()
        db.paperDao().deleteAllPapers()
        db.studyNoteDao().deleteAllStudyNotes()
        db.paperDao().deleteAllDailyAnalytics()
        appPrefs.saveCustomFolders(emptyList())

        val restoredCounts = VaultArchiveEngine.restoreFromPreview(
            context = context,
            db = db,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            preview = preview,
            mode = RestoreMode.FULL_REPLACE
        )
        assertEquals(1, restoredCounts.papersCount)
        assertEquals(1, restoredCounts.notesCount)

        val restoredPapers = db.paperDao().getAllPapersOnce()
        val restoredNotes = db.studyNoteDao().getAllStudyNotesOnce()
        val restoredAttempts = db.paperDao().getAllAttemptsOnce()
        val restoredFolders = appPrefs.getCustomFolders()

        assertEquals(1, restoredPapers.size)
        assertEquals("Mechanics Mock 1", restoredPapers.first().title)
        assertEquals(0.25, restoredPapers.first().unattendedMarks, 0.001)
        assertEquals(1, restoredNotes.size)
        assertEquals("Formula Codex", restoredNotes.first().customLabel)
        assertEquals(restoredPapers.first().id, restoredNotes.first().linkedPaperId)
        assertEquals(1, restoredAttempts.size)
        assertEquals(restoredPapers.first().id, restoredAttempts.first().paperId)
        assertEquals(1, restoredFolders.size)
        assertEquals("Physics Vault", restoredFolders.first().name)
        assertNotNull(db.paperDao().getDailyAnalytics("2026-11-14"))

        // Verify SMART_MERGE does not duplicate already present records
        VaultArchiveEngine.restoreFromPreview(
            context = context,
            db = db,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            preview = preview,
            mode = RestoreMode.SMART_MERGE
        )
        assertEquals(1, db.paperDao().getAllPapersOnce().size)
        assertEquals(1, db.studyNoteDao().getAllStudyNotesOnce().size)
        assertEquals(1, db.paperDao().getAllAttemptsOnce().size)
    }

    @Test
    fun `inspectLocalFile detects tampered entry checksums and supports legacy JSON`() {
        val archiveFile = File(tempDir, "tamper.examset.zip")
        runBlocking {
            db.paperDao().insertPaper(
                PaperEntity(
                    title = "Integrity Paper",
                    subject = "Chem",
                    topic = "Bonding",
                    setNumber = "01",
                    questionCount = 1,
                    answerKeyCount = 1,
                    explanationCount = 0,
                    rawText = "1. H2O shape?\n(A) Bent\n(B) Linear\nAnswer: A"
                )
            )
            VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, archiveFile)
        }

        // Tamper manifest checksum in a copied zip
        val badZip = File(tempDir, "tampered.examset.zip")
        ZipFile(archiveFile).use { src ->
            java.util.zip.ZipOutputStream(badZip.outputStream()).use { zos ->
                src.entries().asSequence().forEach { entry ->
                    zos.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    val bytes = src.getInputStream(entry).readBytes()
                    if (entry.name == "notes.ndjson") {
                        zos.write("{\"tampered\":true}\n".toByteArray())
                    } else {
                        zos.write(bytes)
                    }
                    zos.closeEntry()
                }
            }
        }
        val tamperedPreview = VaultArchiveEngine.inspectLocalFile(badZip)
        assertFalse("Tampered zip must fail SHA-256 verification", tamperedPreview.checksumVerified)

        // Legacy JSON inspection
        val legacyJsonFile = File(tempDir, "legacy.json")
        legacyJsonFile.writeText(
            """
            [
              {
                "metadata": { "title": "Legacy Polity", "subject": "Polity" },
                "questions": [
                  {
                    "num": 1,
                    "question": "Who is head of state?",
                    "options": { "a": "President", "b": "PM", "c": "CJI", "d": "Speaker" },
                    "correctAnswer": "A"
                  }
                ]
              }
            ]
            """.trimIndent()
        )
        val legacyPreview = VaultArchiveEngine.inspectLocalFile(legacyJsonFile)
        assertTrue(legacyPreview.isLegacyJson)
        assertTrue(legacyPreview.checksumVerified)
        assertEquals(1, legacyPreview.manifest.counts.papersCount)
    }

    @Test
    fun streamBundleToOutputStream_handles25SetsAnd2500LongQuestionsCleanly() {
        val longExplanation = "Detailed analytical explanation covering statutory provisions, constitutional articles, and case law precedent. ".repeat(6)
        val entities = (1..25).map { setIdx ->
            PaperEntity(
                id = setIdx.toLong(),
                title = "UPSC Mock Set $setIdx",
                subject = "General Studies",
                topic = "Mixed",
                setNumber = "Set $setIdx",
                exam = "UPSC CSE",
                questionCount = 100,
                answerKeyCount = 100,
                explanationCount = 100,
                rawText = ""
            )
        }

        val outFile = File(tempDir, "streamed_25_sets.json")
        var progressCalls = 0
        outFile.outputStream().use { fos ->
            ExamJsonHelper.streamBundleToOutputStream(
                examTitle = "UPSC Full Library",
                folderId = null,
                papers = entities,
                outputStream = fos,
                onProgress = { _, _ -> progressCalls++ }
            ) { entity ->
                val qs = (1..100).map { qNum ->
                    com.example.model.McqQuestion(
                        num = qNum,
                        question = "Question $qNum in ${entity.title}: Evaluate statements 1, 2, and 3.",
                        options = com.example.model.QuestionOptions(
                            a = "1 only",
                            b = "2 only",
                            c = "1 and 3 only",
                            d = "1, 2 and 3"
                        ),
                        correctAnswer = "D",
                        explanation = longExplanation
                    )
                }
                com.example.model.QuestionPaper(
                    id = entity.id,
                    metadata = com.example.model.PaperMetadata(
                        id = "paper-${entity.id}",
                        title = entity.title,
                        subject = entity.subject,
                        topic = entity.topic,
                        setNumber = entity.setNumber,
                        exam = entity.exam,
                        rollNumber = "1234567890",
                        bookletNumber = "1234567"
                    ),
                    questions = qs,
                    quickAnswersCount = 100,
                    explanationsCount = 100
                )
            }
        }

        assertEquals(25, progressCalls)
        assertTrue("Streamed JSON file must be non-empty", outFile.length() > 1_000_000L)

        val roundTripped = ExamJsonHelper.bundleFromJson(outFile.readText(Charsets.UTF_8))
        assertEquals(25, roundTripped.size)
        assertEquals(100, roundTripped.first().questions.size)
        assertEquals(100, roundTripped.last().questions.size)
        assertEquals("D", roundTripped.last().questions.last().correctAnswer)
        assertTrue(roundTripped.last().questions.last().explanation!!.contains("constitutional articles"))
    }
}
