package com.example.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AppFontScaleMode
import com.example.data.AttemptEntity
import com.example.data.ColorSchemePreset
import com.example.data.DailyStudyAnalyticsEntity
import com.example.data.ExamSetPreferences
import com.example.data.PaperEntity
import com.example.data.StudyNoteEntity
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import com.example.model.ExamCountdownTarget
import com.example.model.FlashcardSrsState
import com.example.model.FolderItem
import com.example.model.HeadwordQueueItem
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
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WebVaultJsonAdapterTest {

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
        tempDir = File(context.cacheDir, "web_vault_test_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun webJsonSerializer_preservesKeyOrderUnescapedSlashesAndExtractsMemberSlices() {
        val sample = JSONObject().apply {
            put("manifest", JSONObject().apply { put("format", "examset-vault-v5") })
            put("sections", JSONObject().apply {
                put("alpha", JSONObject().apply {
                    put("10", "ten")
                    put("2", "two")
                    put("path", "a/b/c")
                    put("whole", 4.0)
                })
                put("beta", JSONArray().apply { put(1); put(2) })
            })
        }
        val pretty = WebJsonSerializer.toJsPrettyString(sample, 2)
        assertTrue("Forward slashes must not be escaped as \\/", pretty.contains("\"a/b/c\""))
        assertTrue("Whole doubles must serialize like JS without .0", pretty.contains("\"whole\": 4"))

        val slices = WebJsonSerializer.extractJsonObjectMemberRawSlices(pretty, "sections")
        assertEquals(setOf("alpha", "beta"), slices.keys)
        val minifiedAlpha = WebJsonSerializer.minifyJsonPreserveRawTokens(slices.getValue("alpha"))
        assertEquals("{\"2\":\"two\",\"10\":\"ten\",\"path\":\"a/b/c\",\"whole\":4}", minifiedAlpha)
    }

    @Test
    fun exportAndInspectWebVaultJson_roundTripsFullWorkspaceAndDetectsTampering() = runBlocking {
        val folder = FolderItem(id = "fld_polity", name = "Polity", color = "#0284C7")
        appPrefs.saveCustomFolders(listOf(folder))
        appPrefs.saveKnowledgeCoursesMap(mapOf("UPSC GS2" to listOf("Polity", "Governance")))
        themePrefs.setThemeMode(ThemeMode.DARK)
        themePrefs.setColorSchemePreset(ColorSchemePreset.FOREST_SAGE)
        appPrefs.setFontScaleMode(AppFontScaleMode.LARGE)

        val paperId = db.paperDao().insertPaper(
            PaperEntity(
                title = "Constitution Mock",
                subject = "Polity",
                topic = "Parliament",
                setNumber = "02",
                exam = "UPSC",
                questionCount = 1,
                answerKeyCount = 1,
                explanationCount = 1,
                rawText = "1. Quorum in Lok Sabha?\n(A) 1/10th\n(B) 1/5th\n(C) 1/3rd\n(D) Half\nAnswer: A\nExplanation: Article 100(3).",
                folderId = folder.id,
                bookletNumber = "BK-7788"
            )
        )
        db.paperDao().insertAttempt(
            AttemptEntity(
                paperId = paperId,
                answersJson = AttemptEntity.buildAnswersJson(
                    map = mapOf(1 to "A"),
                    rollNumber = "0000001234",
                    bookletNumber = "BK-7788",
                    questionTimes = mapOf(1 to 18L)
                ),
                isSubmitted = true,
                score = 4.0,
                correctCount = 1,
                incorrectCount = 0,
                unansweredCount = 0,
                totalQuestions = 1,
                timeSpentSeconds = 18L,
                createdAt = 1_700_000_001_000L,
                submittedAt = 1_700_000_002_000L
            )
        )
        db.paperDao().upsertDailyAnalytics(
            DailyStudyAnalyticsEntity(
                dateString = "2026-11-27",
                totalTestTimeSeconds = 900L,
                testsCompletedCount = 1,
                testsStartedCount = 1,
                questionsAttemptedCount = 1,
                correctAnswersCount = 1,
                dailyGoalSeconds = 1800L
            )
        )
        val noteId = db.studyNoteDao().insertStudyNote(
            StudyNoteEntity(
                folderId = folder.id,
                title = "Article 100 Facts",
                subject = "Polity",
                course = "UPSC GS2",
                sourceAi = "One-Liners",
                rawContent = "1. **Quorum** -> One-tenth of total membership.",
                bookmarkedLinesJson = "[0]",
                masteredBlocksJson = "[0]",
                syncUid = "uid-art-100"
            )
        )
        appPrefs.saveFlashcardSrsMap(
            noteId,
            mapOf(
                0 to FlashcardSrsState(
                    blockIndex = 0,
                    repetitions = 2,
                    easeFactor = 2.5f,
                    intervalDays = 6,
                    dueAtMillis = 1_790_000_000_000L,
                    lastReviewedAtMillis = 1_789_000_000_000L,
                    lastRating = SrsRating.GOOD,
                    reviewCount = 3,
                    correctCount = 3
                )
            )
        )
        appPrefs.saveVocabularyDecks(
            listOf(
                VocabularyDeck(
                    id = "deck_en_c1",
                    title = "English • C1",
                    language = "English",
                    levelCode = VocabularyLevel.ADVANCED.code,
                    entries = listOf(
                        VocabularyEntry(
                            id = "w_quorum",
                            word = "Quorum",
                            meaning = "Minimum members needed to transact business",
                            levelCode = VocabularyLevel.ADVANCED.code,
                            language = "English",
                            isBookmarked = true
                        )
                    )
                )
            )
        )
        appPrefs.saveAllVocabularySrsMaps(
            mapOf("deck_en_c1" to mapOf("w_quorum" to FlashcardSrsState(blockIndex = 0, repetitions = 1, reviewCount = 2)))
        )
        appPrefs.saveHeadwordInbox(listOf(HeadwordQueueItem(id = "h1", word = "Filibuster", language = "English", levelCode = "C1")))
        appPrefs.saveDismissedWordKeys(setOf("skipme"))
        appPrefs.savePromptPresets(
            listOf(
                PromptPreset(
                    id = "p_gs2",
                    name = "GS2 Polity",
                    mode = PromptPresetMode.QUESTION_SET,
                    questionConfig = QuestionPromptConfig(subject = "Polity", questionCount = 10)
                )
            )
        )
        appPrefs.saveExamCountdowns(
            listOf(
                ExamCountdownTarget(
                    id = "cd_1",
                    title = "UPSC Prelims",
                    targetDateStr = "2027-05-25",
                    targetTimeStr = "09:30",
                    timezoneMode = "LOCAL",
                    alertEnabled = true,
                    alertLeadMinutes = 120,
                    alertMessage = "Carry admit card and black ballpoint pen",
                    vibrateOnAlert = true,
                    alertSoundEnabled = false,
                    alertAtZero = true
                )
            )
        )
        appPrefs.saveStudyTodos(
            listOf(StudyTodoItem(id = "td_1", title = "Revise Parliament", priority = TodoPriority.HIGH))
        )

        val webJson = WebVaultJsonAdapter.createWebVaultJson(context, db, appPrefs, themePrefs, 1_700_000_500_000L)
        assertTrue(WebVaultJsonAdapter.isWebVaultV5Json(webJson))

        val jsonFile = File(tempDir, "android_export.examset.json").apply { writeText(webJson, Charsets.UTF_8) }
        val preview = VaultArchiveEngine.inspectLocalFile(jsonFile)
        assertTrue("Web .examset.json must inspect as isWebVaultJson", preview.isWebVaultJson)
        assertFalse(preview.isLegacyJson)
        assertTrue("Web .examset.json SHA-256 checksums must verify cleanly", preview.checksumVerified)
        assertEquals(1, preview.manifest.counts.papersCount)
        assertEquals(1, preview.manifest.counts.notesCount)
        assertEquals(1, preview.manifest.counts.knowledgeCoursesCount)
        assertEquals(1, preview.manifest.counts.vocabWordsCount)

        // Wipe local DB & prefs, then restore FULL_REPLACE from Web .examset.json
        db.paperDao().deleteAllAttempts()
        db.paperDao().deleteAllPapers()
        db.studyNoteDao().deleteAllStudyNotes()
        db.paperDao().deleteAllDailyAnalytics()
        appPrefs.saveCustomFolders(emptyList())
        appPrefs.saveKnowledgeCoursesMap(emptyMap())
        appPrefs.saveVocabularyDecks(emptyList())
        appPrefs.saveAllVocabularySrsMaps(emptyMap())
        appPrefs.savePromptPresets(emptyList())
        appPrefs.saveExamCountdowns(emptyList())
        appPrefs.saveStudyTodos(emptyList())
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

        val restoredPapers = db.paperDao().getAllPapersOnce()
        val restoredNotes = db.studyNoteDao().getAllStudyNotesOnce()
        val restoredAttempts = db.paperDao().getAllAttemptsOnce()
        assertEquals(1, restoredPapers.size)
        assertEquals("Constitution Mock", restoredPapers.first().title)
        assertEquals(1, restoredNotes.size)
        assertEquals("UPSC GS2", restoredNotes.first().course)
        assertEquals(setOf(0), restoredNotes.first().getMasteredIndices())
        assertEquals(setOf(0), restoredNotes.first().getBookmarkedIndices())
        assertEquals(3, appPrefs.getFlashcardSrsMap(restoredNotes.first().id)[0]?.reviewCount)
        assertEquals(1, restoredAttempts.size)
        assertEquals(18L, restoredAttempts.first().getQuestionTimesMap()[1])
        assertEquals(listOf("Polity", "Governance"), appPrefs.getKnowledgeCoursesMap()["UPSC GS2"])
        assertEquals(ThemeMode.DARK, themePrefs.getThemeMode())
        assertEquals(ColorSchemePreset.FOREST_SAGE, themePrefs.getColorSchemePreset())
        assertEquals(AppFontScaleMode.LARGE, appPrefs.getFontScaleMode())

        val restoredCountdowns = appPrefs.getExamCountdowns()
        assertEquals(1, restoredCountdowns.size)
        val restoredCd = restoredCountdowns.first()
        assertTrue(restoredCd.alertEnabled)
        assertEquals(120, restoredCd.alertLeadMinutes)
        assertEquals("Carry admit card and black ballpoint pen", restoredCd.alertMessage)
        assertTrue(restoredCd.vibrateOnAlert)
        assertFalse(restoredCd.alertSoundEnabled)
        assertTrue(restoredCd.alertAtZero)

        // Verify tampering breaks SHA-256 check
        val tamperedJson = webJson.replace("One-tenth of total membership.", "TAMPERED content.")
        val tamperedFile = File(tempDir, "tampered.examset.json").apply { writeText(tamperedJson, Charsets.UTF_8) }
        val tamperedPreview = VaultArchiveEngine.inspectLocalFile(tamperedFile)
        assertTrue(tamperedPreview.isWebVaultJson)
        assertFalse("Tampered .examset.json must fail SHA-256 verification", tamperedPreview.checksumVerified)
    }

    @Test
    fun smartMergeStudyNote_unionsMasteryAndBookmarksAndPreservesNonBlankCourse() = runBlocking {
        // Vault note has newer updatedAt, mastery=[2], bookmark=[1], and BLANK course
        val incomingNote = StudyNoteEntity(
            syncUid = "uid-merge-note",
            title = "Fiscal Federalism",
            subject = "Economy",
            course = "",
            rawContent = "1. **16th FC** -> Constituted under Article 280.",
            bookmarkedLinesJson = "[1]",
            masteredBlocksJson = "[2]",
            updatedAt = 5_000L
        )
        db.studyNoteDao().insertStudyNote(incomingNote)
        val archiveFile = File(tempDir, "note_merge.examset.zip")
        VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, archiveFile)
        val preview = VaultArchiveEngine.inspectLocalFile(archiveFile)
        assertTrue(preview.checksumVerified)

        // Replace local DB with older local note that already has mastery=[0], bookmark=[0], and course="UPSC Economy"
        db.studyNoteDao().deleteAllStudyNotes()
        db.studyNoteDao().insertStudyNote(
            StudyNoteEntity(
                syncUid = "uid-merge-note",
                title = "Fiscal Federalism",
                subject = "Economy",
                course = "UPSC Economy",
                rawContent = "1. **16th FC** -> Constituted under Article 280.",
                bookmarkedLinesJson = "[0]",
                masteredBlocksJson = "[0]",
                updatedAt = 1_000L
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

        val merged = db.studyNoteDao().getStudyNoteBySyncUid("uid-merge-note")
        assertNotNull(merged)
        assertEquals(setOf(0, 2), merged?.getMasteredIndices())
        assertEquals(setOf(0, 1), merged?.getBookmarkedIndices())
        assertEquals("UPSC Economy", merged?.course)
    }
}
