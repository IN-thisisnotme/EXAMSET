package com.example.util

import android.content.Context
import android.os.Build
import com.example.data.AppDatabase
import com.example.data.AttemptEntity
import com.example.data.ColorSchemePreset
import com.example.data.ExamSetPreferences
import com.example.data.PaperEntity
import com.example.data.StudyNoteEntity
import com.example.data.ThemePreferences
import com.example.data.toQuestionPaper
import com.example.model.BackupCounts
import com.example.model.MsqScoringPolicy
import com.example.model.PaperCategory
import com.example.model.VocabularyDeck
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * Exports Android ExamSet workspace data into Web `.examset.json` v5 (`examset-vault-v5`)
 * with ECMA-262 faithful SHA-256 section checksums so Web `inspectVaultArchiveText`
 * verifies integrity with zero mismatches.
 */
object WebVaultExportBuilder {

    suspend fun createWebVaultJson(
        context: Context,
        db: AppDatabase,
        appPrefs: ExamSetPreferences,
        themePrefs: ThemePreferences,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val paperDao = db.paperDao()
        val noteDao = db.studyNoteDao()

        val folders = appPrefs.getCustomFolders()
        val papers = paperDao.getAllPapersOnce()
        val notes = noteDao.getAllStudyNotesOnce().map { note ->
            if (note.syncUid.isNotBlank()) note else note.copy(syncUid = UUID.randomUUID().toString())
        }
        val attempts = paperDao.getAllAttemptsOnce()
        val analytics = paperDao.getAllDailyAnalyticsOnce()
        val vocabDecks = appPrefs.getVocabularyDecks()
        val promptPresets = appPrefs.getPromptPresets()
        val countdowns = appPrefs.getExamCountdowns()
        val todos = appPrefs.getStudyTodos()
        val knowledgeCourses = appPrefs.getKnowledgeCoursesMap()

        val paperWebIdByRoomId = papers.associate { it.id to "paper-${it.id}" }
        val paperByRoomId = papers.associateBy { it.id }
        val noteWebIdByRoomId = notes.associate { it.id to it.syncUid }

        val foldersArr = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "default")
                put("name", "All Question Sets")
                put("color", "#0284C7")
                put("parentId", JSONObject.NULL)
                put("createdAt", 0L)
            })
            folders.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("color", f.color)
                    put("parentId", f.parentId ?: "default")
                    put("createdAt", f.createdAt)
                })
            }
        }

        val papersArr = JSONArray().apply {
            papers.forEach { entity ->
                put(paperEntityToWebJson(entity, appPrefs.getPaperMsqPolicy(entity.id)))
            }
        }

        val attemptsArr = JSONArray().apply {
            attempts.forEach { att ->
                val paper = paperByRoomId[att.paperId]
                val webPaperId = paperWebIdByRoomId[att.paperId] ?: "paper-${att.paperId}"
                val policy = paper?.let { appPrefs.getPaperMsqPolicy(it.id) } ?: MsqScoringPolicy.ALL_OR_NOTHING
                put(attemptToWebJson(att, paper, webPaperId, policy))
            }
        }

        val countdownsArr = JSONArray().apply {
            countdowns.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("title", c.title)
                    put("targetDateIso", c.targetDateStr)
                    put("examType", c.subject)
                    put("isPinned", c.isPinned)
                    put("color", c.colorHex)
                    if (c.targetTimeStr.isNotBlank()) put("targetTime", c.targetTimeStr)
                    if (c.timezoneMode == "GMT" || c.timezoneMode == "LOCAL") {
                        put("timezoneMode", c.timezoneMode)
                    }
                    if (c.targetUtcMillis != null && c.targetUtcMillis > 0L) {
                        put("targetUtcMillis", c.targetUtcMillis)
                    }
                    put("alertEnabled", c.alertEnabled)
                    put(
                        "alertLeadMinutes",
                        c.alertLeadMinutes.coerceIn(
                            com.example.model.ExamCountdownTarget.MIN_ALERT_LEAD_MINUTES,
                            com.example.model.ExamCountdownTarget.MAX_ALERT_LEAD_MINUTES
                        )
                    )
                    if (c.alertMessage.isNotBlank()) put("alertMessage", c.alertMessage)
                    put("vibrateOnAlert", c.vibrateOnAlert)
                    put("alertSoundEnabled", c.alertSoundEnabled)
                    put("alertAtZero", c.alertAtZero)
                })
            }
        }

        val studyNotesSection = JSONObject().apply {
            put("items", JSONArray().apply {
                notes.forEach { note ->
                    val srsMap = appPrefs.getFlashcardSrsMap(note.id)
                    put(studyNoteToWebJson(note, srsMap, paperWebIdByRoomId))
                }
            })
            put("knowledgeCourses", JSONObject().apply {
                knowledgeCourses.forEach { (course, subs) ->
                    put(course, JSONArray().apply { subs.forEach { put(it) } })
                }
            })
        }

        val vocabDecksArr = JSONArray().apply {
            vocabDecks.forEach { deck -> put(deck.toJson()) }
        }
        val vocabSrsObj = runCatching {
            JSONObject(appPrefs.getVocabularySrsRawJson().ifBlank { "{}" })
        }.getOrDefault(JSONObject())
        val headwordArr = runCatching {
            JSONArray(appPrefs.getHeadwordInboxRawJson().ifBlank { "[]" })
        }.getOrDefault(JSONArray())
        val dismissedArr = runCatching {
            JSONArray(appPrefs.getDismissedWordKeysRawJson().ifBlank { "[]" })
        }.getOrDefault(JSONArray())
        val wotdStatesObj = runCatching {
            JSONObject(appPrefs.getLanguageWordOfTheDayStatesJson().orEmpty().ifBlank { "{}" })
        }.getOrDefault(JSONObject())

        val vocabularySection = JSONObject().apply {
            put("vocabDecks", vocabDecksArr)
            put("vocabSrs", vocabSrsObj)
            put("englishVariant", appPrefs.getPreferredEnglishVariant())
            put("headwordInbox", headwordArr)
            put("dismissedHeadwords", dismissedArr)
            put("wotdStates", wotdStatesObj)
        }

        val presetsSection = JSONObject().apply {
            put("presets", JSONArray().apply { promptPresets.forEach { put(it.toJson()) } })
            put("activePresetIds", JSONObject(appPrefs.getActivePromptPresetIdsRawJson()))
            val draftsObj = JSONObject().apply {
                appPrefs.getQuestionPromptDraft()?.let { put("questionPromptDraft", it.toJson()) }
                appPrefs.getKnowledgePromptDraft()?.let { put("knowledgePromptDraft", it.toJson()) }
                appPrefs.getFlashcardPromptDraft()?.let { put("flashcardPromptDraft", it.toJson()) }
                appPrefs.getVocabularyPromptDraft()?.let { put("vocabularyPromptDraft", it.toJson()) }
            }
            if (draftsObj.length() > 0) put("drafts", draftsObj)
            put("dictationConfig", JSONObject().apply {
                put("targetWpm", 45)
                put("speechBurstRate", 0.96)
                put("speechBurstWpmBaseline", 148)
                put("punctuationMode", "OFF")
                put("elevatingEnabled", false)
                put("startWpm", 35)
                put("targetMaxWpm", 75)
                put("stepWpm", 5)
                put("elevationInterval", "EVERY_60S")
                put("autoAdvancePassages", true)
            })
        }

        val todosArr = JSONArray().apply {
            todos.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("priority", t.priority.name)
                    put("dueDateStr", t.dueDateStr)
                    put("isCompleted", t.isCompleted)
                    put("targetPomodoros", t.targetPomodoros)
                    put("completedPomodoros", t.completedPomodoros)
                    put("linkedType", t.linkedType.name)
                    put("linkedPaperId", t.linkedPaperId?.let { paperWebIdByRoomId[it] } ?: JSONObject.NULL)
                    put("linkedNoteId", t.linkedNoteId?.let { noteWebIdByRoomId[it] } ?: JSONObject.NULL)
                    put("linkedDeckLanguage", t.linkedDeckLanguage)
                    put("linkedLabel", t.linkedLabel)
                    put("createdAt", t.createdAt)
                    put("completedAt", t.completedAt ?: JSONObject.NULL)
                })
            }
        }

        val analyticsArr = JSONArray().apply {
            analytics.forEach { d ->
                put(JSONObject().apply {
                    put("dateStr", d.dateString)
                    put("testsCompleted", d.testsCompletedCount)
                    put("questionsAnswered", d.questionsAttemptedCount)
                    put("correctAnswers", d.correctAnswersCount)
                    put("studyTimeSeconds", d.totalTestTimeSeconds)
                    put("flashcardsReviewed", 0)
                    put("vocabWordsReviewed", 0)
                })
            }
        }

        val plannerSection = JSONObject().apply {
            put("studyTodos", todosArr)
            put("pomodoroConfig", JSONObject().apply {
                put("preset", appPrefs.getPomodoroPresetName())
                put("customFocusMinutes", appPrefs.getPomodoroCustomFocusMinutes())
                put("customBreakMinutes", appPrefs.getPomodoroCustomBreakMinutes())
                put("quietModeEnabled", false)
                put("chimeEnabled", true)
                put("browserNotifyEnabled", false)
                put("dailyFocusGoalMinutes", appPrefs.getDailyGoalMinutes())
            })
            put("dailyAnalytics", analyticsArr)
            put("activeSessions", JSONObject())
        }

        val brainStats = appPrefs.getBrainLabStats()
        val brainSettings = appPrefs.getBrainLabSettings()
        val brainSection = JSONObject().apply {
            put("brainLabStats", brainStats.toJson())
            put("brainLabSettings", JSONObject().apply {
                put("audioEnabled", brainSettings.audioEnabled)
                put("countCalmInDailyStudyTime", brainSettings.countCalmInDailyStudyTime)
                put("preferredBudgetMinutes", brainSettings.preferredBudgetMinutes)
            })
            put("dismissedTips", JSONArray().apply {
                appPrefs.getDismissedFormatTips().forEach { put(it) }
            })
            put("themeMode", themePrefs.getThemeMode().name)
            put("colorPreset", mapAndroidPresetToWeb(themePrefs.getColorSchemePreset()))
            put("fontScaleMode", appPrefs.getFontScaleMode().name)
        }

        val sections = linkedMapOf<String, Any>(
            "papers" to papersArr,
            "folders" to foldersArr,
            "attempts" to attemptsArr,
            "countdowns" to countdownsArr,
            "studyNotes" to studyNotesSection,
            "vocabulary" to vocabularySection,
            "presetsAndDictation" to presetsSection,
            "plannerAndAnalytics" to plannerSection,
            "brainAndPreferences" to brainSection
        )

        val checksums = JSONObject()
        val sectionsObj = JSONObject()
        sections.forEach { (key, sectionVal) ->
            sectionsObj.put(key, sectionVal)
            val compactBytes = WebJsonSerializer.toJsCompactString(sectionVal).toByteArray(Charsets.UTF_8)
            checksums.put(key, VaultArchiveEngine.computeSha256Hex(compactBytes))
        }

        val vocabWordsCount = vocabDecks.sumOf { it.entries.size }
        val countsObj = JSONObject().apply {
            put("papersCount", papers.size)
            put("notesCount", notes.size)
            put("knowledgeCoursesCount", knowledgeCourses.size)
            put("foldersCount", folders.size + 1)
            put("attemptsCount", attempts.size)
            put("analyticsDaysCount", analytics.size)
            put("vocabDecksCount", vocabDecks.size)
            put("vocabWordsCount", vocabWordsCount)
            put("promptPresetsCount", promptPresets.size)
            put("todosCount", todos.size)
            put("countdownsCount", countdowns.size)
        }

        val manifestObj = JSONObject().apply {
            put("format", "examset-vault-v5")
            put("schemaVersion", 5)
            put("appVersion", "1.0")
            put("createdAt", nowMillis)
            put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("counts", countsObj)
            put("entryChecksumsSha256", checksums)
        }

        val doc = JSONObject().apply {
            put("manifest", manifestObj)
            put("sections", sectionsObj)
        }
        return WebJsonSerializer.toJsPrettyString(doc, 2)
    }

    private fun paperEntityToWebJson(
        entity: PaperEntity,
        msqPolicy: MsqScoringPolicy
    ): JSONObject {
        val qp = entity.toQuestionPaper()
        val webCat = mapAndroidCategoryToWeb(entity.manualCategory)
        val metaObj = JSONObject().apply {
            put("id", "paper-${entity.id}")
            put("title", entity.title.ifBlank { "Untitled Exam Set" })
            put("subject", entity.subject.ifBlank { "General Studies" })
            put("topic", entity.topic)
            put("setNumber", entity.setNumber.ifBlank { "01" })
            put("targetExam", entity.exam)
            put("rollNumber", qp.metadata.rollNumber)
            put("bookletNumber", qp.metadata.bookletNumber)
            put("durationMinutes", if (entity.durationMinutes > 0) entity.durationMinutes else 60)
            put("positiveMarks", entity.positiveMarks)
            put("negativeMarks", entity.negativeMarks)
            put("unattendedMarks", entity.unattendedMarks)
            put("msqScoringPolicy", msqPolicy.code)
            if (webCat != null) put("manualCategory", webCat)
            put("createdAt", entity.createdAt)
            put("updatedAt", entity.updatedAt)
            put("folderId", entity.folderId?.takeIf { it.isNotBlank() } ?: "default")
        }
        val questionsArr = JSONArray().apply {
            qp.questions.forEach { q ->
                put(JSONObject().apply {
                    put("id", "q-${entity.id}-${q.num}")
                    put("num", q.num)
                    put("question", q.question)
                    if (!q.questionType.isNullOrBlank()) put("questionType", q.questionType)
                    put("options", JSONObject().apply {
                        put("a", q.options.a)
                        put("b", q.options.b)
                        put("c", q.options.c)
                        put("d", q.options.d)
                        if (q.options.e.isNotBlank()) put("e", q.options.e)
                    })
                    put("correctAnswer", q.correctAnswer ?: JSONObject.NULL)
                    put("explanation", q.explanation ?: JSONObject.NULL)
                    if (!q.diagramUrl.isNullOrBlank()) put("diagramUrl", q.diagramUrl)
                    if (!q.diagramLabel.isNullOrBlank()) put("diagramLabel", q.diagramLabel)
                    if (!q.formula.isNullOrBlank()) put("formula", q.formula)
                    if (q.isNeutralized) put("isNeutralized", true)
                    if (q.isVerified) put("isVerified", true)
                })
            }
        }
        return JSONObject().apply {
            put("metadata", metaObj)
            put("questions", questionsArr)
        }
    }

    private fun attemptToWebJson(
        att: AttemptEntity,
        paper: PaperEntity?,
        webPaperId: String,
        msqPolicy: MsqScoringPolicy
    ): JSONObject {
        val posMarks = paper?.positiveMarks ?: 4.0
        val negMarks = paper?.negativeMarks ?: 1.0
        val unattMarks = paper?.unattendedMarks ?: 0.0
        val totalQ = att.totalQuestions.coerceAtLeast(att.correctCount + att.incorrectCount + att.unansweredCount)
        val attempted = att.correctCount + att.incorrectCount
        val accuracy = if (attempted > 0) (att.correctCount * 100.0) / attempted else 0.0
        val answersMap = att.getAnswersMap()
        val qTimesMap = att.getQuestionTimesMap()
        return JSONObject().apply {
            put("id", "att-${att.id}")
            put("paperId", webPaperId)
            put("paperTitle", paper?.title ?: "Exam Set")
            put("subject", paper?.subject ?: "General")
            put("rollNumber", att.getCandidateRollNumber())
            put("bookletNumber", att.getCandidateBookletNumber())
            put("bookletCode", att.getCandidateBookletCode().ifBlank { "A" })
            put("score", att.score)
            put("maxScore", totalQ * posMarks)
            put("correctCount", att.correctCount)
            put("partialCount", 0)
            put("incorrectCount", att.incorrectCount)
            put("unansweredCount", att.unansweredCount)
            put("accuracyPercent", accuracy)
            put("timeSpentSeconds", att.timeSpentSeconds)
            put("positiveMarks", posMarks)
            put("negativeMarks", negMarks)
            put("unattendedMarks", unattMarks)
            put("msqScoringPolicy", msqPolicy.code)
            put("submittedAnswers", JSONObject().apply {
                answersMap.entries.sortedBy { it.key }.forEach { (qNum, ans) -> put(qNum.toString(), ans) }
            })
            put("questionTimes", JSONObject().apply {
                qTimesMap.entries.sortedBy { it.key }.forEach { (qNum, secs) -> put(qNum.toString(), secs) }
            })
            put("submittedAt", att.submittedAt ?: att.createdAt)
        }
    }

    private fun studyNoteToWebJson(
        note: StudyNoteEntity,
        srsMap: Map<Int, com.example.model.FlashcardSrsState>,
        paperWebIdByRoomId: Map<Long, String>
    ): JSONObject {
        val bookmarked = JSONArray().apply { note.getBookmarkedIndices().sorted().forEach { put(it) } }
        val mastered = JSONArray().apply { note.getMasteredIndices().sorted().forEach { put(it) } }
        val ruleObj = runCatching {
            val parsed = JSONObject(note.formatRuleJson.ifBlank { "{}" })
            if (parsed.length() == 0) note.parsedFormatRule().let { JSONObject(it.toJson()) } else parsed
        }.getOrDefault(JSONObject(note.parsedFormatRule().toJson()))

        val srsObj = JSONObject().apply {
            srsMap.entries.sortedBy { it.key }.forEach { (idx, st) ->
                put(idx.toString(), JSONObject().apply {
                    put("blockIndex", st.blockIndex)
                    put("repetitions", st.repetitions)
                    put("easeFactor", st.easeFactor.toDouble())
                    put("intervalDays", st.intervalDays)
                    put("dueAtMillis", st.dueAtMillis)
                    put("lastReviewedAtMillis", st.lastReviewedAtMillis)
                    put("lapses", st.lapses)
                    put("lastRating", st.lastRating?.name ?: JSONObject.NULL)
                    put("reviewCount", st.reviewCount)
                    put("correctCount", st.correctCount)
                })
            }
        }

        return JSONObject().apply {
            put("id", note.syncUid)
            put("title", note.title)
            put("subject", note.subject)
            put("course", note.course)
            put("sourceAi", note.sourceAi)
            put("rawContent", note.rawContent)
            put("folderId", note.folderId?.takeIf { it.isNotBlank() } ?: "default")
            put("collectionType", note.collectionType)
            put("customLabel", note.customLabel)
            put("linkedPaperId", note.linkedPaperId?.let { paperWebIdByRoomId[it] } ?: JSONObject.NULL)
            put("formatRule", ruleObj)
            put("bookmarkedIndices", bookmarked)
            put("masteredIndices", mastered)
            put("srsStates", srsObj)
            put("createdAt", note.createdAt)
            put("updatedAt", note.updatedAt)
        }
    }

    private fun mapAndroidCategoryToWeb(rawCategory: String): String? {
        return when (PaperCategory.fromCode(rawCategory)) {
            PaperCategory.MOCK_TEST -> "FULL_MOCK"
            PaperCategory.SECTIONAL -> "SUBJECT_TEST"
            PaperCategory.PYQ -> "PYQ"
            PaperCategory.STUDY_NOTES -> "REVISION"
            PaperCategory.AUTO -> null
        }
    }

    internal fun mapAndroidPresetToWeb(preset: ColorSchemePreset): String {
        return when (preset) {
            ColorSchemePreset.WARM_PAPER,
            ColorSchemePreset.EYE_COMFORT,
            ColorSchemePreset.TERRACOTTA_CLAY,
            ColorSchemePreset.STONE_LINEN -> "WARM_PAPER"
            ColorSchemePreset.FOREST_SAGE -> "FOREST_SAGE"
            ColorSchemePreset.ROYAL_INDIGO -> "ROYAL_INDIGO"
            ColorSchemePreset.OLED_OBSIDIAN -> "OLED_OBSIDIAN"
            else -> "NORDIC_SLATE"
        }
    }
}
