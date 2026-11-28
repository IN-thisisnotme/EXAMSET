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
import com.example.data.AppFontScaleMode
import com.example.data.ColorSchemePreset
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import com.example.model.ExamCountdownTarget
import com.example.model.FlashcardPromptConfig
import com.example.model.FlashcardSrsState
import com.example.model.FolderItem
import com.example.model.KnowledgePromptConfig
import com.example.model.PromptPreset
import com.example.model.PromptPresetMode
import com.example.model.QuestionPromptConfig
import com.example.model.RestoreMode
import com.example.model.SrsRating
import com.example.model.StudyTodoItem
import com.example.model.TodoPriority
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyLevel
import com.example.model.VocabularyPromptConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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

        val noteId = db.studyNoteDao().insertStudyNote(
            StudyNoteEntity(
                folderId = folder.id,
                title = "Kinematics Multi-Liners",
                subject = "Physics",
                course = "JEE Physics",
                sourceAi = "ChatGPT",
                rawContent = "1. **Projectile Range** -> Maximum at 45 degrees.\n  - Formula: ${'$'}R = u^2 \\sin(2\\theta)/g${'$'}",
                collectionType = "MULTI_LINER",
                customLabel = "Formula Codex",
                linkedPaperId = paperId,
                masteredBlocksJson = "[0]",
                syncUid = "uid-kinematics-1"
            )
        )
        appPrefs.saveFlashcardSrsMap(
            noteId,
            mapOf(
                0 to FlashcardSrsState(
                    blockIndex = 0,
                    repetitions = 3,
                    easeFactor = 2.6f,
                    intervalDays = 6,
                    dueAtMillis = 1_790_000_000_000L,
                    lastReviewedAtMillis = 1_789_000_000_000L,
                    lapses = 0,
                    lastRating = SrsRating.GOOD,
                    reviewCount = 4,
                    correctCount = 4
                )
            )
        )

        val vocabDeck = VocabularyDeck(
            id = "deck_en_c1",
            title = "English • C1",
            language = "English",
            languageVariant = "UK",
            levelCode = VocabularyLevel.ADVANCED.code,
            entries = listOf(
                VocabularyEntry(
                    id = "word_ephemeral",
                    word = "Ephemeral",
                    meaning = "Lasting for a very short time",
                    levelCode = VocabularyLevel.ADVANCED.code,
                    language = "English",
                    isBookmarked = true
                ),
                VocabularyEntry(
                    id = "word_lucid",
                    word = "Lucid",
                    meaning = "Expressed clearly and easy to understand",
                    levelCode = VocabularyLevel.ADVANCED.code,
                    language = "English"
                )
            )
        )
        appPrefs.saveVocabularyDecks(listOf(vocabDeck))
        appPrefs.saveAllVocabularySrsMaps(
            mapOf(
                vocabDeck.id to mapOf(
                    "word_ephemeral" to FlashcardSrsState(
                        blockIndex = 0,
                        repetitions = 2,
                        easeFactor = 2.5f,
                        intervalDays = 4,
                        dueAtMillis = 1_790_100_000_000L,
                        lastReviewedAtMillis = 1_789_500_000_000L,
                        reviewCount = 3,
                        correctCount = 3,
                        lastRating = SrsRating.EASY
                    )
                )
            )
        )
        appPrefs.savePromptPresets(
            listOf(
                PromptPreset(
                    id = "preset_vocab_1",
                    name = "Daily C1 Vocab",
                    mode = PromptPresetMode.VOCABULARY,
                    vocabularyConfig = VocabularyPromptConfig(language = "English", wordCount = 15)
                )
            )
        )
        appPrefs.setPreferredEnglishVariant("UK")
        appPrefs.setVocabularyReminderEnabled(true)
        appPrefs.saveHeadwordInbox(
            listOf(
                com.example.model.HeadwordQueueItem(
                    id = "hq_1",
                    word = "Obfuscate",
                    language = "English",
                    levelCode = "C1",
                    utilityReason = "Frequently tested in editorial passages"
                )
            )
        )
        appPrefs.saveDismissedWordKeys(setOf("trivial"))

        val archiveFile = File(tempDir, "test_backup.examset.zip")
        val manifest = VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, archiveFile)

        assertTrue(archiveFile.exists() && archiveFile.length() > 0L)
        assertEquals(1, manifest.counts.papersCount)
        assertEquals(1, manifest.counts.notesCount)
        assertEquals(1, manifest.counts.foldersCount)
        assertEquals(1, manifest.counts.attemptsCount)
        assertEquals(1, manifest.counts.analyticsDaysCount)
        assertEquals(1, manifest.counts.vocabDecksCount)
        assertEquals(2, manifest.counts.vocabWordsCount)
        assertEquals(1, manifest.counts.promptPresetsCount)
        assertTrue(manifest.entryChecksumsSha256.containsKey("papers.ndjson"))
        assertTrue(manifest.entryChecksumsSha256.containsKey("notes.ndjson"))
        assertTrue(manifest.entryChecksumsSha256.containsKey("vocabulary.json"))

        // Inspect archive integrity
        val preview = VaultArchiveEngine.inspectLocalFile(archiveFile)
        assertFalse(preview.isLegacyJson)
        assertTrue("Manifest SHA-256 verification must pass", preview.checksumVerified)
        assertEquals(1, preview.manifest.counts.papersCount)
        assertEquals(1, preview.manifest.counts.notesCount)
        assertEquals(1, preview.manifest.counts.vocabDecksCount)
        assertEquals(2, preview.manifest.counts.vocabWordsCount)

        // Clear all local state and restore with FULL_REPLACE
        db.paperDao().deleteAllAttempts()
        db.paperDao().deleteAllPapers()
        db.studyNoteDao().deleteAllStudyNotes()
        db.paperDao().deleteAllDailyAnalytics()
        appPrefs.saveCustomFolders(emptyList())
        appPrefs.saveVocabularyDecks(emptyList())
        appPrefs.saveAllVocabularySrsMaps(emptyMap())
        appPrefs.savePromptPresets(emptyList())
        appPrefs.clearFlashcardSrsMap(noteId)
        appPrefs.setVocabularyReminderEnabled(false)
        appPrefs.saveHeadwordInbox(emptyList())
        appPrefs.saveDismissedWordKeys(emptySet())

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
        assertEquals(1, restoredCounts.vocabDecksCount)
        assertEquals(2, restoredCounts.vocabWordsCount)

        val restoredPapers = db.paperDao().getAllPapersOnce()
        val restoredNotes = db.studyNoteDao().getAllStudyNotesOnce()
        val restoredAttempts = db.paperDao().getAllAttemptsOnce()
        val restoredFolders = appPrefs.getCustomFolders()
        val restoredVocabDecks = appPrefs.getVocabularyDecks()
        val restoredVocabSrs = appPrefs.getAllVocabularySrsMaps()
        val restoredFlashcardSrs = appPrefs.getFlashcardSrsMap(restoredNotes.first().id)

        assertEquals(1, restoredPapers.size)
        assertEquals("Mechanics Mock 1", restoredPapers.first().title)
        assertEquals(0.25, restoredPapers.first().unattendedMarks, 0.001)
        assertEquals(1, restoredNotes.size)
        assertEquals("Formula Codex", restoredNotes.first().customLabel)
        assertEquals("JEE Physics", restoredNotes.first().course)
        assertEquals(restoredPapers.first().id, restoredNotes.first().linkedPaperId)
        assertEquals(1, restoredAttempts.size)
        assertEquals(restoredPapers.first().id, restoredAttempts.first().paperId)
        assertEquals(1, restoredFolders.size)
        assertEquals("Physics Vault", restoredFolders.first().name)
        assertNotNull(db.paperDao().getDailyAnalytics("2026-11-14"))
        assertEquals(1, restoredVocabDecks.size)
        assertEquals(2, restoredVocabDecks.first().entries.size)
        assertEquals("UK", appPrefs.getPreferredEnglishVariant())
        assertTrue(appPrefs.isVocabularyReminderEnabled())
        assertEquals(3, restoredVocabSrs["deck_en_c1"]?.get("word_ephemeral")?.reviewCount)
        assertEquals(4, restoredFlashcardSrs[0]?.reviewCount)
        assertEquals(1, appPrefs.getPromptPresets().size)
        assertEquals(listOf("Obfuscate"), appPrefs.getHeadwordInbox().map { it.word })
        assertEquals(setOf("trivial"), appPrefs.getDismissedWordKeys())

        // Verify SMART_MERGE does not duplicate already present records and preserves distinct inbox/dismissed items
        appPrefs.saveHeadwordInbox(
            appPrefs.getHeadwordInbox() + com.example.model.HeadwordQueueItem(
                id = "hq_local",
                word = "Obfuscate",
                language = "German",
                levelCode = "B2"
            )
        )
        appPrefs.addDismissedWordKey("localignored")
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
        assertEquals(1, appPrefs.getVocabularyDecks().size)
        assertEquals(2, appPrefs.getVocabularyDecks().first().entries.size)
        assertEquals(2, appPrefs.getHeadwordInbox().size)
        assertEquals(setOf("trivial", "localignored"), appPrefs.getDismissedWordKeys())
    }

    @Test
    fun `smartMergeVocabularyDecks merges new words into existing canonical level deck cleanly`() = runBlocking {
        // Vault contains English C1 with "Aberration" + "Ephemeral" under a different UUID
        val incomingDeck = VocabularyDeck(
            id = "other_device_c1",
            title = "English • C1",
            language = "English",
            levelCode = VocabularyLevel.ADVANCED.code,
            entries = listOf(
                VocabularyEntry(id = "inc_1", word = "Aberration", meaning = "A departure from normal"),
                VocabularyEntry(id = "inc_2", word = "Ephemeral", meaning = "Short-lived", updatedAt = 2000L)
            )
        )
        appPrefs.saveVocabularyDecks(listOf(incomingDeck))
        appPrefs.saveAllVocabularySrsMaps(
            mapOf(
                incomingDeck.id to mapOf(
                    "inc_1" to FlashcardSrsState(blockIndex = 0, repetitions = 2, reviewCount = 2, lastReviewedAtMillis = 1500L)
                )
            )
        )
        val archiveFile = File(tempDir, "vocab_merge.examset.zip")
        VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, archiveFile)
        val preview = VaultArchiveEngine.inspectLocalFile(archiveFile)

        // Local device has English C1 with "Ephemeral" + "Lucid" under local deck ID
        val localDeck = VocabularyDeck(
            id = "local_device_c1",
            title = "English • C1",
            language = "English",
            levelCode = VocabularyLevel.ADVANCED.code,
            entries = listOf(
                VocabularyEntry(id = "loc_ephemeral", word = "Ephemeral", meaning = "Brief", isBookmarked = true, updatedAt = 1000L),
                VocabularyEntry(id = "loc_lucid", word = "Lucid", meaning = "Clear")
            )
        )
        appPrefs.saveVocabularyDecks(listOf(localDeck))
        appPrefs.saveAllVocabularySrsMaps(emptyMap())

        VaultArchiveEngine.restoreFromPreview(
            context = context,
            db = db,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            preview = preview,
            mode = RestoreMode.SMART_MERGE
        )

        val mergedDecks = appPrefs.getVocabularyDecks()
        assertEquals("Same (language, level) deck must consolidate instead of duplicating", 1, mergedDecks.size)
        val mergedWords = mergedDecks.first().entries.map { it.word }
        assertEquals(listOf("Aberration", "Ephemeral", "Lucid"), mergedWords)
        assertTrue(
            "Local bookmark on merged entry must be preserved",
            mergedDecks.first().entries.first { it.word == "Ephemeral" }.isBookmarked
        )
        val mergedSrs = appPrefs.getAllVocabularySrsMaps()
        assertEquals(2, mergedSrs["local_device_c1"]?.get("inc_1")?.reviewCount)
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

    @Test
    fun `syncEverything schema v5 round-trips active presets dismissed tips fontScale wotdCache and validated folder`() = runBlocking {
        val folder = FolderItem(id = "fld_history", name = "History", color = "#0284C7", updatedAt = 5_000L)
        appPrefs.saveCustomFolders(listOf(folder))
        appPrefs.setLastActiveFolderId(folder.id)

        val qPreset = PromptPreset(
            id = "preset_q_1",
            name = "Prelims GS",
            mode = PromptPresetMode.QUESTION_SET,
            updatedAt = 2_000L,
            questionConfig = QuestionPromptConfig(subject = "Modern History", questionCount = 25)
        )
        val kPreset = PromptPreset(
            id = "preset_k_1",
            name = "Revision Notes",
            mode = PromptPresetMode.KNOWLEDGE_SET,
            updatedAt = 2_500L,
            knowledgeConfig = KnowledgePromptConfig(subject = "Constitutional Amendments")
        )
        appPrefs.savePromptPresets(listOf(qPreset, kPreset))
        appPrefs.setActivePromptPresetId(PromptPresetMode.QUESTION_SET, qPreset.id)
        appPrefs.setActivePromptPresetId(PromptPresetMode.KNOWLEDGE_SET, kPreset.id)
        appPrefs.saveQuestionPromptDraft(QuestionPromptConfig(subject = "Draft Q Subject", questionCount = 15))
        appPrefs.saveFlashcardPromptDraft(FlashcardPromptConfig(subject = "Draft FC Subject", cardCount = 20))
        appPrefs.saveDismissedFormatTips(setOf("tip_one_liner", "tip_q_json"))
        appPrefs.saveWordOfTheDayCacheJson("{\"word\":\"Perspicacious\",\"date\":\"2026-11-24\"}")
        appPrefs.setFontScaleMode(AppFontScaleMode.FOLLOW_SYSTEM)
        appPrefs.setFontSizeScale(1.25f)
        themePrefs.setThemeMode(ThemeMode.DARK)
        themePrefs.setColorSchemePreset(ColorSchemePreset.EYE_COMFORT)

        val archiveFile = File(tempDir, "sync_everything_v5.examset.zip")
        val manifest = VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, archiveFile)
        assertEquals(5, manifest.schemaVersion)
        assertTrue(manifest.counts.hasAppSettings)
        assertTrue(manifest.counts.draftsCount >= 2)

        val preview = VaultArchiveEngine.inspectLocalFile(archiveFile)
        assertTrue(preview.checksumVerified)
        assertEquals(5, preview.manifest.schemaVersion)

        // Wipe and change all local settings before Clean Replace restore
        appPrefs.saveCustomFolders(emptyList())
        appPrefs.setLastActiveFolderId("all")
        appPrefs.savePromptPresets(emptyList())
        PromptPresetMode.entries.forEach { appPrefs.setActivePromptPresetId(it, null) }
        appPrefs.saveQuestionPromptDraft(QuestionPromptConfig())
        appPrefs.saveFlashcardPromptDraft(FlashcardPromptConfig())
        appPrefs.saveDismissedFormatTips(emptySet())
        appPrefs.saveWordOfTheDayCacheJson("")
        appPrefs.setFontScaleMode(AppFontScaleMode.STANDARD_FIXED)
        appPrefs.setFontSizeScale(1.0f)
        themePrefs.setThemeMode(ThemeMode.LIGHT)
        themePrefs.setColorSchemePreset(ColorSchemePreset.MINIMAL)

        VaultArchiveEngine.restoreFromPreview(
            context = context,
            db = db,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            preview = preview,
            mode = RestoreMode.FULL_REPLACE,
            mergeAppSettings = true
        )

        assertEquals(folder.id, appPrefs.getLastActiveFolderId())
        assertEquals(qPreset.id, appPrefs.getActivePromptPresetId(PromptPresetMode.QUESTION_SET))
        assertEquals(kPreset.id, appPrefs.getActivePromptPresetId(PromptPresetMode.KNOWLEDGE_SET))
        assertEquals("Draft Q Subject", appPrefs.getQuestionPromptDraft()?.subject)
        assertEquals("Draft FC Subject", appPrefs.getFlashcardPromptDraft()?.subject)
        assertEquals(setOf("tip_one_liner", "tip_q_json"), appPrefs.getDismissedFormatTips())
        assertEquals("{\"word\":\"Perspicacious\",\"date\":\"2026-11-24\"}", appPrefs.getWordOfTheDayCacheJson())
        assertEquals(AppFontScaleMode.FOLLOW_SYSTEM, appPrefs.getFontScaleMode())
        assertEquals(1.25f, appPrefs.getFontSizeScale(), 0.001f)
        assertEquals(ThemeMode.DARK, themePrefs.getThemeMode())
        assertEquals(ColorSchemePreset.EYE_COMFORT, themePrefs.getColorSchemePreset())
    }

    @Test
    fun `smartMerge resolves conflicts by newest updatedAt across folders presets countdowns and todos`() = runBlocking {
        // Incoming archive has newer folder & todo, older preset & countdown
        val incFolder = FolderItem(id = "fld_shared", name = "Renamed Folder V2", color = "#10B981", createdAt = 100L, updatedAt = 5_000L)
        val incPreset = PromptPreset(
            id = "preset_shared",
            name = "Older Preset Name",
            mode = PromptPresetMode.QUESTION_SET,
            createdAt = 100L,
            updatedAt = 1_000L,
            questionConfig = QuestionPromptConfig(subject = "Old Subject")
        )
        val incCountdown = ExamCountdownTarget(
            id = "cd_shared",
            title = "Older Exam Title",
            targetDateStr = "2027-06-01",
            createdAt = 100L,
            updatedAt = 1_000L
        )
        val incTodo = StudyTodoItem(
            id = "td_shared",
            title = "Updated Todo Title",
            priority = TodoPriority.HIGH,
            createdAt = 100L,
            updatedAt = 8_000L
        )

        appPrefs.saveCustomFolders(listOf(incFolder))
        appPrefs.savePromptPresets(listOf(incPreset))
        appPrefs.saveExamCountdowns(listOf(incCountdown))
        appPrefs.saveStudyTodos(listOf(incTodo))

        val archiveFile = File(tempDir, "newest_wins.examset.zip")
        VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, archiveFile)
        val preview = VaultArchiveEngine.inspectLocalFile(archiveFile)

        // Local state has older folder & todo, newer preset & countdown
        appPrefs.saveCustomFolders(listOf(incFolder.copy(name = "Stale Local Folder", updatedAt = 1_000L)))
        appPrefs.savePromptPresets(listOf(incPreset.copy(name = "Newer Local Preset", updatedAt = 6_000L)))
        appPrefs.saveExamCountdowns(listOf(incCountdown.copy(title = "Newer Local Exam", updatedAt = 7_000L)))
        appPrefs.saveStudyTodos(listOf(incTodo.copy(title = "Older Local Todo", updatedAt = 2_000L)))

        VaultArchiveEngine.restoreFromPreview(
            context = context,
            db = db,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            preview = preview,
            mode = RestoreMode.SMART_MERGE,
            mergeAppSettings = true
        )

        assertEquals("Renamed Folder V2", appPrefs.getCustomFolders().first { it.id == "fld_shared" }.name)
        assertEquals("Newer Local Preset", appPrefs.getPromptPresets().first { it.id == "preset_shared" }.name)
        assertEquals("Newer Local Exam", appPrefs.getExamCountdowns().first { it.id == "cd_shared" }.title)
        assertEquals("Updated Todo Title", appPrefs.getStudyTodos().first { it.id == "td_shared" }.title)
    }

    @Test
    fun `smartMerge respects mergeAppSettings flag to preserve local appearance and font scale`() = runBlocking {
        themePrefs.setThemeMode(ThemeMode.DARK)
        themePrefs.setColorSchemePreset(ColorSchemePreset.WARM_PAPER)
        appPrefs.setFontScaleMode(AppFontScaleMode.LARGE)
        appPrefs.setFontSizeScale(1.35f)
        appPrefs.setDailyGoalMinutes(90)
        appPrefs.saveDismissedFormatTips(setOf("tip_cloud"))

        val archiveFile = File(tempDir, "settings_toggle.examset.zip")
        VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, archiveFile)
        val preview = VaultArchiveEngine.inspectLocalFile(archiveFile)

        // Switch local device to Light Minimal 1.0x
        themePrefs.setThemeMode(ThemeMode.LIGHT)
        themePrefs.setColorSchemePreset(ColorSchemePreset.MINIMAL)
        appPrefs.setFontScaleMode(AppFontScaleMode.STANDARD_FIXED)
        appPrefs.setFontSizeScale(1.0f)
        appPrefs.setDailyGoalMinutes(30)
        appPrefs.saveDismissedFormatTips(emptySet())

        // Restore with mergeAppSettings = false
        VaultArchiveEngine.restoreFromPreview(
            context = context,
            db = db,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            preview = preview,
            mode = RestoreMode.SMART_MERGE,
            mergeAppSettings = false
        )

        assertEquals(ThemeMode.LIGHT, themePrefs.getThemeMode())
        assertEquals(ColorSchemePreset.MINIMAL, themePrefs.getColorSchemePreset())
        assertEquals(AppFontScaleMode.STANDARD_FIXED, appPrefs.getFontScaleMode())
        assertEquals(1.0f, appPrefs.getFontSizeScale(), 0.001f)
        assertEquals(30, appPrefs.getDailyGoalMinutes())
        assertTrue(appPrefs.getDismissedFormatTips().isEmpty())

        // Restore again with mergeAppSettings = true
        VaultArchiveEngine.restoreFromPreview(
            context = context,
            db = db,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            preview = preview,
            mode = RestoreMode.SMART_MERGE,
            mergeAppSettings = true
        )

        assertEquals(ThemeMode.DARK, themePrefs.getThemeMode())
        assertEquals(ColorSchemePreset.WARM_PAPER, themePrefs.getColorSchemePreset())
        assertEquals(AppFontScaleMode.LARGE, appPrefs.getFontScaleMode())
        assertEquals(1.35f, appPrefs.getFontSizeScale(), 0.001f)
        assertEquals(90, appPrefs.getDailyGoalMinutes())
        assertEquals(setOf("tip_cloud"), appPrefs.getDismissedFormatTips())
    }

    @Test
    fun `promptPresetsPack export and import merges by updatedAt and restores active selections and drafts`() {
        val presetA = PromptPreset(
            id = "pack_p_a",
            name = "Polity Pack v2",
            mode = PromptPresetMode.QUESTION_SET,
            updatedAt = 5_000L,
            questionConfig = QuestionPromptConfig(subject = "Fundamental Rights", questionCount = 20)
        )
        val presetB = PromptPreset(
            id = "pack_p_b",
            name = "Older Economy Pack",
            mode = PromptPresetMode.KNOWLEDGE_SET,
            updatedAt = 1_000L,
            knowledgeConfig = KnowledgePromptConfig(subject = "Fiscal Policy Old")
        )
        appPrefs.savePromptPresets(listOf(presetA, presetB))
        appPrefs.setActivePromptPresetId(PromptPresetMode.QUESTION_SET, presetA.id)
        appPrefs.saveVocabularyPromptDraft(VocabularyPromptConfig(language = "German", wordCount = 12))

        val exportedJson = VaultArchiveEngine.exportPromptPresetsPackJson(appPrefs)

        // Reset local presets, keep a newer version of presetB locally
        val newerLocalB = presetB.copy(
            name = "Newer Economy Local",
            updatedAt = 9_000L,
            knowledgeConfig = KnowledgePromptConfig(subject = "Monetary Policy New")
        )
        appPrefs.savePromptPresets(listOf(newerLocalB))
        appPrefs.setActivePromptPresetId(PromptPresetMode.QUESTION_SET, null)
        appPrefs.clearVocabularyPromptDraft()

        val summary = VaultArchiveEngine.importPromptPresetsPackJson(exportedJson, appPrefs, overwriteAll = false)
        assertEquals(2, summary.totalPresetsInPack)
        assertEquals(1, summary.addedCount)
        assertEquals(0, summary.updatedCount)
        assertTrue(summary.activeSelectionsRestored >= 1)
        assertTrue(summary.draftsRestored >= 1)

        val finalPresets = appPrefs.getPromptPresets().associateBy { it.id }
        assertEquals("Polity Pack v2", finalPresets["pack_p_a"]?.name)
        assertEquals("Newer Economy Local", finalPresets["pack_p_b"]?.name)
        assertEquals("pack_p_a", appPrefs.getActivePromptPresetId(PromptPresetMode.QUESTION_SET))
        assertEquals("German", appPrefs.getVocabularyPromptDraft()?.language)
    }

    @Test
    fun `inspectLocalFile marks checksumVerified false when required core entry or checksum is omitted`() = runBlocking {
        val archiveFile = File(tempDir, "valid_base.examset.zip")
        VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, archiveFile)

        val strippedFile = File(tempDir, "stripped_checksum.examset.zip")
        ZipFile(archiveFile).use { src ->
            ZipOutputStream(strippedFile.outputStream()).use { zos ->
                src.entries().asSequence().forEach { entry ->
                    if (entry.name == "manifest.json") {
                        val rawManifest = src.getInputStream(entry).bufferedReader().readText()
                        val obj = org.json.JSONObject(rawManifest)
                        obj.getJSONObject("entryChecksumsSha256").remove("papers.ndjson")
                        zos.putNextEntry(ZipEntry(entry.name))
                        zos.write(obj.toString(2).toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(entry.name))
                        zos.write(src.getInputStream(entry).readBytes())
                        zos.closeEntry()
                    }
                }
            }
        }

        val preview = VaultArchiveEngine.inspectLocalFile(strippedFile)
        assertFalse(
            "Archive missing checksum for required core entry papers.ndjson must fail verification",
            preview.checksumVerified
        )
        assertTrue(preview.checksumDetails.contains("papers.ndjson"))
    }

    @Test
    fun `smartMerge clears linkedPaperId when linked paper is absent instead of colliding with local paper id`() = runBlocking {
        // Seed device B with Paper id = 1 and Note linked to paperId = 1
        val paperB = PaperEntity(
            id = 1L,
            title = "Device B Paper",
            subject = "Economics",
            questionCount = 1,
            answerKeyCount = 1,
            explanationCount = 0,
            rawText = "1. GDP stands for?\n(A) Gross Domestic Product\n(B) Other\nAnswer: A"
        )
        db.paperDao().insertPaper(paperB)
        db.studyNoteDao().insertStudyNote(
            StudyNoteEntity(
                syncUid = "note_linked_orphan",
                title = "Note Originally Linked to Device B Paper 1",
                subject = "Economics",
                rawContent = "Inflation targets",
                linkedPaperId = 1L
            )
        )

        val deviceBZip = File(tempDir, "device_b.examset.zip")
        VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, deviceBZip)

        // Reset DB cleanly on Device A with an unrelated paper at id = 1, and modify zip to omit papers.ndjson records
        db.paperDao().deleteAllAttempts()
        db.paperDao().deleteAllPapers()
        db.studyNoteDao().deleteAllStudyNotes()
        val unrelatedLocalPaperId = db.paperDao().insertPaper(
            PaperEntity(
                title = "Unrelated Local Paper on Device A",
                subject = "Biology",
                questionCount = 1,
                answerKeyCount = 1,
                explanationCount = 0,
                rawText = "1. Cell powerhouse?\n(A) Mitochondria\n(B) Nucleus\nAnswer: A"
            )
        )

        // Build archive with 0 papers but note still carrying stale linkedPaperId = unrelatedLocalPaperId
        val emptyPapersZip = File(tempDir, "note_unmapped_link.examset.zip")
        ZipFile(deviceBZip).use { src ->
            ZipOutputStream(emptyPapersZip.outputStream()).use { zos ->
                src.entries().asSequence().forEach { entry ->
                    zos.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "papers.ndjson") {
                        // empty papers list -> paperIdMap will not map id 1
                    } else if (entry.name == "notes.ndjson") {
                        val line = src.getInputStream(entry).bufferedReader().readText()
                            .replace("\"linkedPaperId\":1", "\"linkedPaperId\":$unrelatedLocalPaperId")
                        zos.write(line.toByteArray(Charsets.UTF_8))
                    } else {
                        zos.write(src.getInputStream(entry).readBytes())
                    }
                    zos.closeEntry()
                }
            }
        }

        val preview = VaultArchiveEngine.inspectLocalFile(emptyPapersZip)
        VaultArchiveEngine.restoreFromPreview(
            context = context,
            db = db,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            preview = preview,
            mode = RestoreMode.SMART_MERGE
        )

        val restoredNote = db.studyNoteDao().getStudyNoteBySyncUid("note_linked_orphan")
        assertNotNull(restoredNote)
        assertNull(
            "Unmapped linkedPaperId must become null rather than attaching to an unrelated local paper",
            restoredNote?.linkedPaperId
        )
    }

    @Test
    fun `pruneExportCacheDir removes expired files while keeping excluded and fresh files`() {
        val exportDir = File(context.cacheDir, "vault_export_cache").apply { mkdirs() }
        val staleFile = File(exportDir, "inspect_old.tmp").apply {
            writeText("stale")
            setLastModified(System.currentTimeMillis() - 120_000L)
        }
        val excludedFile = File(exportDir, "inspect_excluded.tmp").apply {
            writeText("keep-excluded")
            setLastModified(System.currentTimeMillis() - 120_000L)
        }
        val freshFile = File(exportDir, "inspect_fresh.tmp").apply {
            writeText("fresh")
            setLastModified(System.currentTimeMillis())
        }

        VaultArchiveEngine.pruneExportCacheDir(
            context = context,
            maxAgeMs = 60_000L,
            excludeFile = excludedFile
        )

        assertFalse("Expired cache file should be pruned", staleFile.exists())
        assertTrue("Excluded file must be preserved", excludedFile.exists())
        assertTrue("Fresh cache file must be preserved", freshFile.exists())
    }

    @Test
    fun `vault archive round-trips knowledgeCoursesJson across Clean Replace and Smart Merge`() = runBlocking {
        appPrefs.saveKnowledgeCoursesMap(
            mapOf(
                "UPSC Prelims" to listOf("Polity", "Economy"),
                "CAT Quant" to listOf("Geometry")
            )
        )

        val archiveFile = File(tempDir, "courses_vault.examset.zip")
        VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, archiveFile)
        val preview = VaultArchiveEngine.inspectLocalFile(archiveFile)
        assertTrue(preview.checksumVerified)

        // Wipe local courses and verify Clean Replace restores exact map
        appPrefs.saveKnowledgeCoursesMap(emptyMap())
        VaultArchiveEngine.restoreFromPreview(
            context = context,
            db = db,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            preview = preview,
            mode = RestoreMode.FULL_REPLACE
        )
        val replaced = appPrefs.getKnowledgeCoursesMap()
        assertEquals(listOf("Polity", "Economy"), replaced["UPSC Prelims"])
        assertEquals(listOf("Geometry"), replaced["CAT Quant"])

        // Seed additional local course/subject and verify Smart Merge unions without losing either
        appPrefs.saveKnowledgeCoursesMap(
            mapOf(
                "UPSC Prelims" to listOf("History"),
                "State PSC" to listOf("Geography")
            )
        )
        VaultArchiveEngine.restoreFromPreview(
            context = context,
            db = db,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            preview = preview,
            mode = RestoreMode.SMART_MERGE
        )
        val merged = appPrefs.getKnowledgeCoursesMap()
        assertEquals(setOf("History", "Polity", "Economy"), merged["UPSC Prelims"]?.toSet())
        assertEquals(listOf("Geometry"), merged["CAT Quant"])
        assertEquals(listOf("Geography"), merged["State PSC"])
    }

    @Test
    fun `smartMerge preserves StudyNote mastery bookmarks and non-blank course across devices`() = runBlocking {
        // Incoming archive has newer StudyNote with blank course, bookmark [2], mastered [1]
        db.studyNoteDao().insertStudyNote(
            StudyNoteEntity(
                syncUid = "uid-shared-note",
                title = "Constitutional Bodies",
                subject = "Polity",
                course = "",
                rawContent = "1. **CAG** -> Art 148\n2. **ECI** -> Art 324\n3. **UPSC** -> Art 315",
                bookmarkedLinesJson = "[2]",
                masteredBlocksJson = "[1]",
                createdAt = 1_000L,
                updatedAt = 5_000L
            )
        )
        appPrefs.saveKnowledgeCoursesMap(mapOf("UPSC GS" to listOf("Polity")))
        val archiveFile = File(tempDir, "note_merge.examset.zip")
        val manifest = VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, archiveFile)
        assertEquals(1, manifest.counts.knowledgeCoursesCount)

        // Local device has older StudyNote with course "UPSC GS", bookmark [0], mastered [0]
        db.studyNoteDao().deleteAllStudyNotes()
        db.studyNoteDao().insertStudyNote(
            StudyNoteEntity(
                syncUid = "uid-shared-note",
                title = "Constitutional Bodies",
                subject = "Polity",
                course = "UPSC GS",
                rawContent = "1. **CAG** -> Art 148",
                bookmarkedLinesJson = "[0]",
                masteredBlocksJson = "[0]",
                createdAt = 1_000L,
                updatedAt = 2_000L
            )
        )

        val preview = VaultArchiveEngine.inspectLocalFile(archiveFile)
        assertTrue(preview.checksumVerified)
        assertEquals(1, preview.manifest.counts.knowledgeCoursesCount)

        VaultArchiveEngine.restoreFromPreview(
            context = context,
            db = db,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            preview = preview,
            mode = RestoreMode.SMART_MERGE
        )

        val mergedNote = db.studyNoteDao().getStudyNoteBySyncUid("uid-shared-note")
        assertNotNull(mergedNote)
        assertEquals("UPSC GS", mergedNote?.course)
        assertEquals(setOf(0, 2), mergedNote?.getBookmarkedIndices())
        assertEquals(setOf(0, 1), mergedNote?.getMasteredIndices())
    }

    @Test
    fun `inspectLocalFile and restoreFromPreview handle Web examset-vault-v5 JSON with SHA-256 verification`() = runBlocking {
        fun sha256(s: String): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val papersSec = """[{"metadata":{"id":"web-p1","title":"Web Polity Set","subject":"Polity","topic":"Parliament","setNumber":"01","targetExam":"UPSC","durationMinutes":45,"positiveMarks":2,"negativeMarks":0.66,"folderId":"fld_web","createdAt":1700000000000,"updatedAt":1700000010000},"questions":[{"num":1,"question":"Which house is the Upper House?","options":{"a":"Lok Sabha","b":"Rajya Sabha","c":"Vidhan Sabha","d":"Gram Sabha"},"correctAnswer":"B","explanation":"Rajya Sabha is the Council of States."}]}]"""
        val foldersSec = """[{"id":"default","name":"My Question Papers","color":"#177E89","parentId":null,"createdAt":1700000000000},{"id":"fld_web","name":"Web UPSC","color":"#0284C7","parentId":null,"createdAt":1700000000000}]"""
        val attemptsSec = """[{"id":"att-1","paperId":"web-p1","score":2,"maxScore":2,"correctCount":1,"partialCount":0,"incorrectCount":0,"unansweredCount":0,"timeSpentSeconds":30,"submittedAnswers":{"1":"B"},"questionTimes":{"1":15},"submittedAt":1700000020000}]"""
        val notesSec = """[{"id":"web-note-1","title":"Directive Principles","course":"UPSC Mains","subject":"Polity","sourceAi":"Web","rawContent":"1. **Art 40** -> Village Panchayats","folderId":"fld_web","collectionType":"ONE_LINERS","customLabel":"","linkedPaperId":"web-p1","formatRule":{},"bookmarkedIndices":[0],"masteredIndices":[0],"srsStates":{"0":{"blockIndex":0,"repetitions":2,"easeFactor":2.5,"intervalDays":6,"dueAtMillis":1790000000000,"lastReviewedAtMillis":1780000000000,"lapses":0,"lastRating":"GOOD","reviewCount":2,"correctCount":2}},"createdAt":1700000000000,"updatedAt":1700000010000}]"""
        val vocabSec = """{"vocabDecks":[{"id":"d-en-b2","title":"English B2","language":"English","languageVariant":"UK","translationLanguage":"","levelCode":"B2","customLevelLabel":"","domain":"General","entries":[{"id":"w1","word":"Candor","pronunciation":"","partOfSpeech":"noun","levelCode":"B2","customLevelLabel":"","language":"English","languageVariant":"UK","meaning":"Frankness","translation":"","translationLanguage":"","examples":[],"synonyms":["frankness"],"antonyms":[],"collocations":[],"usageNote":"","isBookmarked":true,"createdAt":1700000000000,"updatedAt":1700000000000}],"createdAt":1700000000000,"updatedAt":1700000000000}],"vocabSrs":{},"englishVariant":"UK","headwordInbox":[],"dismissedHeadwords":[],"wotdStates":{},"promptPresets":[]}"""
        val plannerSec = """{"countdowns":[{"id":"cd-1","title":"UPSC Prelims","targetDateIso":"2027-05-24","examType":"GS","color":"#0D9488","isPinned":true,"alertEnabled":true,"alertLeadMinutes":150,"alertMessage":"Gate opens","vibrateOnAlert":true,"alertSoundEnabled":false,"alertAtZero":true}],"studyTodos":[{"id":"td-1","title":"Revise Art 40","priority":"HIGH","dueDateStr":"2027-05-01","isCompleted":false,"targetPomodoros":2,"completedPomodoros":1,"linkedType":"NOTE","linkedNoteId":"web-note-1","linkedDeckLanguage":"","linkedLabel":"Directive Principles","createdAt":1700000000000}],"pomodoroConfig":{"preset":"DEEP_50_10","customFocusMinutes":30,"customBreakMinutes":5,"dailyFocusGoalMinutes":90},"dailyAnalytics":{"2026-11-27":{"dateStr":"2026-11-27","testsCompleted":1,"questionsAnswered":1,"correctAnswers":1,"studyTimeSeconds":300}}}"""
        val brainSec = """{"knowledgeCourses":{"UPSC Mains":["Polity"]},"brainLabStats":{"bestNBackTier":3,"totalCognitiveSeconds":420,"lastUpdatedAtMillis":1700000010000},"brainLabSettings":{"audioEnabled":false,"countCalmInDailyStudyTime":false,"preferredBudgetMinutes":5},"dismissedTips":["tip_one"],"themeMode":"DARK","colorPreset":"WARM_PAPER","fontScaleMode":"LARGE"}"""

        val webJson = """{"manifest":{"format":"examset-vault-v5","schemaVersion":5,"appVersion":"web-2.5","createdAt":1700000050000,"deviceName":"Firefox Web","counts":{"papersCount":1,"notesCount":1,"knowledgeCoursesCount":1,"foldersCount":2,"attemptsCount":1,"analyticsDaysCount":1,"vocabDecksCount":1,"vocabWordsCount":1,"promptPresetsCount":0,"todosCount":1,"countdownsCount":1},"entryChecksumsSha256":{"papers":"${sha256(papersSec)}","folders":"${sha256(foldersSec)}","attempts":"${sha256(attemptsSec)}","studyNotes":"${sha256(notesSec)}","vocabAndPresets":"${sha256(vocabSec)}","plannerAndAnalytics":"${sha256(plannerSec)}","brainAndPreferences":"${sha256(brainSec)}"}},"sections":{"papers":$papersSec,"folders":$foldersSec,"attempts":$attemptsSec,"studyNotes":$notesSec,"vocabAndPresets":$vocabSec,"plannerAndAnalytics":$plannerSec,"brainAndPreferences":$brainSec}}"""

        val webFile = File(tempDir, "web_backup.examset.json").apply { writeText(webJson) }
        val preview = VaultArchiveEngine.inspectLocalFile(webFile)
        assertTrue("Web Vault v5 JSON checksum must verify cleanly", preview.checksumVerified)
        assertTrue(preview.isWebVaultJson)
        assertEquals(1, preview.manifest.counts.papersCount)
        assertEquals(1, preview.manifest.counts.knowledgeCoursesCount)

        VaultArchiveEngine.restoreFromPreview(
            context = context,
            db = db,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            preview = preview,
            mode = RestoreMode.FULL_REPLACE
        )

        val restoredPapers = db.paperDao().getAllPapersOnce()
        val restoredNotes = db.studyNoteDao().getAllStudyNotesOnce()
        val restoredAttempts = db.paperDao().getAllAttemptsOnce()
        assertEquals(1, restoredPapers.size)
        assertEquals("Web Polity Set", restoredPapers.first().title)
        assertEquals("fld_web", restoredPapers.first().folderId)
        assertEquals(1, restoredNotes.size)
        assertEquals("UPSC Mains", restoredNotes.first().course)
        assertEquals(restoredPapers.first().id, restoredNotes.first().linkedPaperId)
        assertEquals(1, restoredAttempts.size)
        assertEquals(listOf("Polity"), appPrefs.getKnowledgeCoursesMap()["UPSC Mains"])
        assertEquals(3, appPrefs.getBrainLabStats().bestNBackTier)
        assertEquals(ThemeMode.DARK, themePrefs.getThemeMode())
        assertEquals(1, appPrefs.getVocabularyDecks().size)
        val restoredCountdowns = appPrefs.getExamCountdowns()
        assertEquals(1, restoredCountdowns.size)
        assertTrue(restoredCountdowns.first().alertEnabled)
        assertEquals(150, restoredCountdowns.first().alertLeadMinutes)
        assertEquals("Gate opens", restoredCountdowns.first().alertMessage)
        assertTrue(restoredCountdowns.first().vibrateOnAlert)
        assertFalse(restoredCountdowns.first().alertSoundEnabled)
        assertTrue(restoredCountdowns.first().alertAtZero)

        // Tampered Web JSON must fail verification
        val tamperedFile = File(tempDir, "web_tampered.examset.json").apply {
            writeText(webJson.replace("Rajya Sabha", "Upper Council"))
        }
        val tamperedPreview = VaultArchiveEngine.inspectLocalFile(tamperedFile)
        assertFalse(tamperedPreview.checksumVerified)
    }

    @Test
    fun `ExamCountdownTarget alert serialization and default lead preferences round-trip cleanly`() {
        assertEquals(120, appPrefs.getDefaultCountdownAlertLeadMinutes())
        appPrefs.setDefaultCountdownAlertLeadMinutes(90)
        assertEquals(90, appPrefs.getDefaultCountdownAlertLeadMinutes())

        appPrefs.markCountdownAlertFired("cd-x|EARLY|1000")
        appPrefs.markCountdownAlertFired("cd-y|EARLY|2000")
        appPrefs.clearFiredCountdownAlertsForId("cd-x")
        assertEquals(setOf("cd-y|EARLY|2000"), appPrefs.getFiredCountdownAlertKeys())

        val cd = ExamCountdownTarget(
            id = "cd-alert-1",
            title = "Finals",
            targetDateStr = "2027-06-10",
            targetTimeStr = "09:30",
            timezoneMode = "GMT",
            targetUtcMillis = 1_812_619_800_000L,
            alertEnabled = true,
            alertLeadMinutes = 120,
            alertMessage = "Gate opens soon",
            vibrateOnAlert = true,
            alertSoundEnabled = false,
            alertAtZero = true
        )
        assertEquals(1_812_619_800_000L - 120L * 60_000L, cd.earlyAlertTriggerMillis())
        assertEquals("2h", cd.formattedLeadSummary())

        val decoded = ExamCountdownTarget.fromJson(cd.toJson())
        assertNotNull(decoded)
        assertEquals(cd.alertEnabled, decoded?.alertEnabled)
        assertEquals(cd.alertLeadMinutes, decoded?.alertLeadMinutes)
        assertEquals(cd.alertMessage, decoded?.alertMessage)
        assertEquals(cd.vibrateOnAlert, decoded?.vibrateOnAlert)
        assertEquals(cd.alertSoundEnabled, decoded?.alertSoundEnabled)
        assertEquals(cd.alertAtZero, decoded?.alertAtZero)
    }
}
