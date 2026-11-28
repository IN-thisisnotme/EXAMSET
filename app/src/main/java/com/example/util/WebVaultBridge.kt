package com.example.util

import android.content.Context
import androidx.room.withTransaction
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
import com.example.model.BackupCounts
import com.example.model.BackupManifest
import com.example.model.BrainLabSettings
import com.example.model.BrainLabStats
import com.example.model.ExamCountdownTarget
import com.example.model.FlashcardSrsState
import com.example.model.FolderItem
import com.example.model.MsqScoringPolicy
import com.example.model.PromptPreset
import com.example.model.PromptPresetMode
import com.example.model.RestoreMode
import com.example.model.RestorePreview
import com.example.model.SrsRating
import com.example.model.StudyTodoItem
import com.example.model.TodoLinkType
import com.example.model.TodoPriority
import com.example.model.VocabularyDeck
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Bidirectional bridge for inspecting and restoring Web `examset-vault-v5` (.examset.json) vaults on Android.
 */
internal object WebVaultBridge {

    private val REQUIRED_WEB_V5_SECTIONS = listOf(
        "papers",
        "folders",
        "attempts",
        "studyNotes",
        "vocabAndPresets",
        "plannerAndAnalytics",
        "brainAndPreferences"
    )

    fun isWebVaultV5Root(obj: JSONObject): Boolean {
        val manifest = obj.optJSONObject("manifest") ?: return false
        return obj.has("sections") && manifest.optString("format", "") == "examset-vault-v5"
    }

    fun inspectWebVaultJson(
        rawJson: String,
        rootObj: JSONObject,
        sourceLabel: String,
        tempFile: File?
    ): RestorePreview {
        val manifestObj = rootObj.getJSONObject("manifest")
        val countsObj = manifestObj.optJSONObject("counts") ?: JSONObject()
        val checksumsObj = manifestObj.optJSONObject("entryChecksumsSha256") ?: JSONObject()
        val checksumMap = buildMap<String, String> {
            checksumsObj.keys().forEach { key -> put(key, checksumsObj.optString(key, "")) }
        }

        val rawSlices = extractObjectChildRawSlices(rawJson, "sections")
        val sectionsObj = rootObj.optJSONObject("sections") ?: JSONObject()

        var mismatchCount = 0
        val checked = mutableListOf<String>()
        val missingOrFailed = mutableListOf<String>()

        for (sec in REQUIRED_WEB_V5_SECTIONS) {
            if (!sectionsObj.has(sec)) {
                mismatchCount++
                missingOrFailed += "$sec (missing section)"
                continue
            }
            val expected = checksumMap[sec]?.trim().orEmpty()
            if (expected.isBlank()) {
                mismatchCount++
                missingOrFailed += "$sec (missing checksum)"
                continue
            }
            val rawSlice = rawSlices[sec]
            val actualHash = if (rawSlice != null) {
                sha256Utf8(rawSlice)
            } else {
                sha256Utf8(sectionsObj.get(sec).toString())
            }
            if (!actualHash.equals(expected, ignoreCase = true)) {
                mismatchCount++
                missingOrFailed += "$sec (hash mismatch)"
            } else {
                checked += sec
            }
        }

        checksumMap.forEach { (key, expected) ->
            if (key in REQUIRED_WEB_V5_SECTIONS) return@forEach
            if (!sectionsObj.has(key) || expected.isBlank()) {
                mismatchCount++
                missingOrFailed += "$key (incomplete)"
            } else {
                val rawSlice = rawSlices[key]
                val actualHash = if (rawSlice != null) sha256Utf8(rawSlice) else sha256Utf8(sectionsObj.get(key).toString())
                if (!actualHash.equals(expected, ignoreCase = true)) {
                    mismatchCount++
                    missingOrFailed += "$key (hash mismatch)"
                } else {
                    checked += key
                }
            }
        }

        val verified = mismatchCount == 0 && checked.size >= REQUIRED_WEB_V5_SECTIONS.size
        val details = if (verified) {
            "Verified Web Vault v5 (${checked.size} SHA-256 sections OK)"
        } else {
            "Warning: Web Vault v5 integrity check failed (${missingOrFailed.joinToString(", ")})"
        }

        val brainObj = sectionsObj.optJSONObject("brainAndPreferences")
        val coursesCountFromSection = brainObj?.optJSONObject("knowledgeCourses")?.length() ?: 0
        val counts = BackupCounts(
            papersCount = countsObj.optInt("papersCount", sectionsObj.optJSONArray("papers")?.length() ?: 0),
            notesCount = countsObj.optInt("notesCount", sectionsObj.optJSONArray("studyNotes")?.length() ?: 0),
            knowledgeCoursesCount = countsObj.optInt("knowledgeCoursesCount", coursesCountFromSection),
            foldersCount = countsObj.optInt("foldersCount", sectionsObj.optJSONArray("folders")?.length() ?: 0),
            attemptsCount = countsObj.optInt("attemptsCount", sectionsObj.optJSONArray("attempts")?.length() ?: 0),
            analyticsDaysCount = countsObj.optInt("analyticsDaysCount", 0),
            vocabDecksCount = countsObj.optInt("vocabDecksCount", 0),
            vocabWordsCount = countsObj.optInt("vocabWordsCount", 0),
            promptPresetsCount = countsObj.optInt("promptPresetsCount", 0),
            countdownsCount = countsObj.optInt("countdownsCount", 0),
            todosCount = countsObj.optInt("todosCount", 0),
            draftsCount = 0,
            hasBrainLabData = brainObj?.optJSONObject("brainLabStats") != null,
            hasAppSettings = brainObj != null
        )

        return RestorePreview(
            sourceLabel = sourceLabel,
            isLegacyJson = false,
            checksumVerified = verified,
            checksumDetails = details,
            manifest = BackupManifest(
                schemaVersion = manifestObj.optInt("schemaVersion", 5),
                appVersion = manifestObj.optString("appVersion", "web"),
                createdAt = manifestObj.optLong("createdAt", System.currentTimeMillis()),
                deviceName = manifestObj.optString("deviceName", "ExamSet Web"),
                counts = counts,
                entryChecksumsSha256 = checksumMap
            ),
            tempFile = tempFile,
            legacyJsonContent = rawJson,
            isWebVaultJson = true
        )
    }

    suspend fun restoreWebVaultJson(
        context: Context,
        db: AppDatabase,
        appPrefs: ExamSetPreferences,
        themePrefs: ThemePreferences,
        rawJson: String,
        mode: RestoreMode,
        mergeAppSettings: Boolean
    ): BackupCounts {
        val root = JSONObject(rawJson)
        val sections = root.getJSONObject("sections")
        val overwriteAll = mode == RestoreMode.FULL_REPLACE
        val syncSettings = overwriteAll || mergeAppSettings

        if (overwriteAll) {
            VaultArchiveEngine.createLocalRollingSnapshot(
                context = context,
                db = db,
                appPrefs = appPrefs,
                themePrefs = themePrefs,
                providerTag = "LOCAL_AUTO",
                prefix = "pre_restore"
            )
        }

        // 1. Folders ("default" root on Web maps to Android's null folderId)
        val incomingFolders = mutableListOf<FolderItem>()
        val foldersArr = sections.optJSONArray("folders") ?: JSONArray()
        for (i in 0 until foldersArr.length()) {
            val fObj = foldersArr.optJSONObject(i) ?: continue
            val fid = fObj.optString("id", "").trim()
            if (fid.isBlank() || fid == "default") continue
            val parentRaw = if (fObj.has("parentId") && !fObj.isNull("parentId")) {
                fObj.optString("parentId", "").trim().takeIf { it.isNotBlank() && it != "default" }
            } else null
            val createdAt = fObj.optLong("createdAt", System.currentTimeMillis())
            incomingFolders += FolderItem(
                id = fid,
                name = fObj.optString("name", "Folder"),
                color = fObj.optString("color", "#0284C7"),
                parentId = parentRaw,
                createdAt = createdAt,
                updatedAt = fObj.optLong("updatedAt", createdAt)
            )
        }

        val mergedFolders = if (overwriteAll) {
            incomingFolders
        } else {
            val current = appPrefs.getCustomFolders().associateBy { it.id }.toMutableMap()
            incomingFolders.forEach { inc ->
                val ex = current[inc.id]
                if (ex == null || inc.updatedAt > ex.updatedAt) {
                    current[inc.id] = inc
                }
            }
            current.values.toList()
        }
        appPrefs.saveCustomFolders(mergedFolders)
        val validFolderIds = mergedFolders.map { it.id }.toSet()

        // 2. Papers, Study Notes, Attempts & Analytics inside Room transaction
        val papersArr = sections.optJSONArray("papers") ?: JSONArray()
        val notesArr = sections.optJSONArray("studyNotes") ?: JSONArray()
        val attemptsArr = sections.optJSONArray("attempts") ?: JSONArray()
        val plannerObj = sections.optJSONObject("plannerAndAnalytics") ?: JSONObject()

        val webPaperIdToLocalId = mutableMapOf<String, Long>()
        val paperMsqOverrides = mutableMapOf<Long, MsqScoringPolicy>()
        val restoredNoteIdBySyncUid = mutableMapOf<String, Long>()
        val webNoteIdToLocalId = mutableMapOf<String, Long>()
        val noteSrsToRestore = mutableListOf<Pair<Long, Map<Int, FlashcardSrsState>>>()

        val paperDao = db.paperDao()
        val noteDao = db.studyNoteDao()

        var importedPapersCount = 0
        var importedNotesCount = 0
        var importedAttemptsCount = 0
        var importedAnalyticsCount = 0

        db.withTransaction {
            if (overwriteAll) {
                paperDao.deleteAllAttempts()
                paperDao.deleteAllPapers()
                noteDao.deleteAllStudyNotes()
                paperDao.deleteAllDailyAnalytics()
            }

            val existingAttemptsByKey = if (!overwriteAll) {
                paperDao.getAllAttemptsOnce().associateBy { "${it.paperId}_${it.createdAt}" }
            } else emptyMap()

            for (i in 0 until papersArr.length()) {
                val pObj = papersArr.optJSONObject(i) ?: continue
                val metaObj = pObj.optJSONObject("metadata") ?: pObj
                val webPaperId = metaObj.optString("id", "").trim()
                val parsed = ExamJsonHelper.fromJson(pObj.toString())
                val rawText = pObj.optString("rawText", "").takeIf { it.isNotBlank() }
                    ?: McqParser.reconstructToRaw(parsed)

                val rawFolderId = if (metaObj.has("folderId") && !metaObj.isNull("folderId")) {
                    metaObj.optString("folderId", "").trim().takeIf { it.isNotBlank() && it != "default" && it in validFolderIds }
                } else null

                val duration = metaObj.optInt("durationMinutes", parsed.metadata.durationMinutes).coerceAtLeast(1)
                val posMarks = metaObj.optDouble("positiveMarks", parsed.metadata.positiveMarks)
                val negMarks = metaObj.optDouble("negativeMarks", parsed.metadata.negativeMarks)
                val unattMarks = metaObj.optDouble("unattendedMarks", parsed.metadata.unattendedMarks)
                val createdAt = metaObj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = metaObj.optLong("updatedAt", createdAt)
                val msqPolicy = MsqScoringPolicy.fromCode(metaObj.optString("msqScoringPolicy", ""))

                val entity = PaperEntity(
                    title = parsed.metadata.title,
                    subject = parsed.metadata.subject.ifBlank { "General Knowledge" },
                    topic = parsed.metadata.topic,
                    setNumber = parsed.metadata.setNumber.ifBlank { "01" },
                    exam = metaObj.optString("targetExam", parsed.metadata.exam),
                    questionCount = parsed.questions.size,
                    answerKeyCount = parsed.quickAnswersCount,
                    explanationCount = parsed.explanationsCount,
                    rawText = rawText,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    folderId = rawFolderId,
                    rollNumber = parsed.metadata.rollNumber,
                    bookletNumber = parsed.metadata.bookletNumber,
                    durationMinutes = duration,
                    positiveMarks = posMarks,
                    negativeMarks = negMarks,
                    unattendedMarks = unattMarks,
                    manualCategory = metaObj.optString("manualCategory", "")
                )

                val existing = if (!overwriteAll) {
                    paperDao.findPaperByMetadata(entity.title, entity.subject, entity.setNumber)
                        ?: entity.bookletNumber.takeIf { it.isNotBlank() }?.let { paperDao.findPaperByBookletNumber(it) }
                        ?: paperDao.findPaperByRawText(entity.rawText)
                } else null

                val finalPaperId = if (existing != null) {
                    if (entity.updatedAt > existing.updatedAt) {
                        paperDao.updatePaper(entity.copy(id = existing.id))
                    }
                    existing.id
                } else {
                    paperDao.insertPaper(entity)
                }

                if (webPaperId.isNotBlank()) {
                    webPaperIdToLocalId[webPaperId] = finalPaperId
                }
                if (msqPolicy != MsqScoringPolicy.ALL_OR_NOTHING) {
                    paperMsqOverrides[finalPaperId] = msqPolicy
                }
                importedPapersCount++
            }

            // Notes
            val currentNotesSnapshot = if (!overwriteAll) noteDao.getAllStudyNotesOnce() else emptyList()
            for (i in 0 until notesArr.length()) {
                val nObj = notesArr.optJSONObject(i) ?: continue
                val webNoteId = nObj.optString("id", "").trim()
                val syncUid = webNoteId.ifBlank { UUID.randomUUID().toString() }
                val rawFolderId = if (nObj.has("folderId") && !nObj.isNull("folderId")) {
                    nObj.optString("folderId", "").trim().takeIf { it.isNotBlank() && it != "default" && it in validFolderIds }
                } else null
                val linkedWebPaperId = if (nObj.has("linkedPaperId") && !nObj.isNull("linkedPaperId")) {
                    nObj.optString("linkedPaperId", "").trim().takeIf { it.isNotBlank() }
                } else null
                val mappedPaperId = linkedWebPaperId?.let { webPaperIdToLocalId[it] }

                val bookmarkedArr = nObj.optJSONArray("bookmarkedIndices") ?: JSONArray()
                val masteredArr = nObj.optJSONArray("masteredIndices") ?: JSONArray()
                val formatRuleJson = nObj.optJSONObject("formatRule")?.toString() ?: "{}"
                val createdAt = nObj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = nObj.optLong("updatedAt", createdAt)

                val incomingNote = StudyNoteEntity(
                    folderId = rawFolderId,
                    title = nObj.optString("title", "Knowledge Set"),
                    subject = nObj.optString("subject", ""),
                    course = nObj.optString("course", ""),
                    sourceAi = nObj.optString("sourceAi", "Knowledge Set"),
                    rawContent = nObj.optString("rawContent", ""),
                    bookmarkedLinesJson = bookmarkedArr.toString(),
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    collectionType = nObj.optString("collectionType", "ONE_LINERS"),
                    customLabel = nObj.optString("customLabel", ""),
                    linkedPaperId = mappedPaperId,
                    formatRuleJson = formatRuleJson,
                    masteredBlocksJson = masteredArr.toString(),
                    syncUid = syncUid
                )

                val existingNote = if (!overwriteAll) {
                    noteDao.getStudyNoteBySyncUid(syncUid)
                        ?: currentNotesSnapshot.firstOrNull {
                            it.title == incomingNote.title && it.rawContent == incomingNote.rawContent
                        }
                } else null

                val finalNoteId = if (existingNote != null) {
                    val merged = VaultArchiveEngine.mergeStudyNoteForRestore(
                        existing = existingNote,
                        incoming = incomingNote,
                        remappedLinkedPaper = mappedPaperId,
                        cleanFolderId = rawFolderId
                    )
                    if (merged != existingNote) {
                        noteDao.updateStudyNote(merged)
                    }
                    existingNote.id
                } else {
                    noteDao.insertStudyNote(incomingNote)
                }

                restoredNoteIdBySyncUid[syncUid] = finalNoteId
                if (webNoteId.isNotBlank()) {
                    webNoteIdToLocalId[webNoteId] = finalNoteId
                }

                // Parse per-note srsStates object
                val srsObj = nObj.optJSONObject("srsStates")
                if (srsObj != null && srsObj.length() > 0) {
                    val srsMap = mutableMapOf<Int, FlashcardSrsState>()
                    srsObj.keys().forEach { key ->
                        val idx = key.toIntOrNull() ?: return@forEach
                        val stObj = srsObj.optJSONObject(key) ?: return@forEach
                        val ratingStr = stObj.optString("lastRating", "")
                        val rating = runCatching {
                            if (ratingStr.isNotBlank()) SrsRating.valueOf(ratingStr) else null
                        }.getOrNull()
                        srsMap[idx] = FlashcardSrsState(
                            blockIndex = stObj.optInt("blockIndex", idx),
                            repetitions = stObj.optInt("repetitions", 0),
                            easeFactor = stObj.optDouble("easeFactor", 2.5).toFloat().coerceIn(1.3f, 3.0f),
                            intervalDays = stObj.optInt("intervalDays", 0),
                            dueAtMillis = stObj.optLong("dueAtMillis", 0L),
                            lastReviewedAtMillis = stObj.optLong("lastReviewedAtMillis", 0L),
                            lapses = stObj.optInt("lapses", 0),
                            lastRating = rating,
                            reviewCount = stObj.optInt("reviewCount", 0),
                            correctCount = stObj.optInt("correctCount", 0)
                        )
                    }
                    if (srsMap.isNotEmpty()) {
                        noteSrsToRestore += (finalNoteId to srsMap)
                    }
                }
                importedNotesCount++
            }

            // Attempts
            for (i in 0 until attemptsArr.length()) {
                val aObj = attemptsArr.optJSONObject(i) ?: continue
                val webPaperId = aObj.optString("paperId", "").trim()
                val mappedPaperId = webPaperIdToLocalId[webPaperId] ?: continue
                val submittedAt = aObj.optLong("submittedAt", System.currentTimeMillis())
                val dedupKey = "${mappedPaperId}_$submittedAt"
                if (!overwriteAll && existingAttemptsByKey.containsKey(dedupKey)) {
                    continue
                }
                val subAnsObj = aObj.optJSONObject("submittedAnswers") ?: JSONObject()
                val ansMap = buildMap<Int, String> {
                    subAnsObj.keys().forEach { k ->
                        val qNum = k.toIntOrNull() ?: return@forEach
                        val v = subAnsObj.optString(k, "").trim()
                        if (v.isNotBlank()) put(qNum, v)
                    }
                }
                val qTimeObj = aObj.optJSONObject("questionTimes") ?: JSONObject()
                val qTimes = buildMap<Int, Long> {
                    qTimeObj.keys().forEach { k ->
                        val qNum = k.toIntOrNull() ?: return@forEach
                        val secs = qTimeObj.optLong(k, 0L)
                        if (secs > 0L) put(qNum, secs)
                    }
                }
                val correct = aObj.optInt("correctCount", 0)
                val incorrect = aObj.optInt("incorrectCount", 0)
                val unanswered = aObj.optInt("unansweredCount", 0)
                val partial = aObj.optInt("partialCount", 0)
                val totalQ = (correct + partial + incorrect + unanswered).coerceAtLeast(ansMap.size)
                val answersJson = AttemptEntity.buildAnswersJson(
                    map = ansMap,
                    rollNumber = aObj.optString("rollNumber", ""),
                    bookletNumber = aObj.optString("bookletNumber", ""),
                    bookletCode = aObj.optString("bookletCode", ""),
                    questionTimes = qTimes
                )
                paperDao.insertAttempt(
                    AttemptEntity(
                        paperId = mappedPaperId,
                        answersJson = answersJson,
                        isSubmitted = true,
                        score = aObj.optDouble("score", 0.0),
                        correctCount = correct,
                        incorrectCount = incorrect,
                        unansweredCount = unanswered,
                        totalQuestions = totalQ,
                        timeSpentSeconds = aObj.optLong("timeSpentSeconds", 0L),
                        createdAt = submittedAt,
                        submittedAt = submittedAt
                    )
                )
                importedAttemptsCount++
            }

            // Daily Analytics
            val dailyObj = plannerObj.optJSONObject("dailyAnalytics") ?: JSONObject()
            dailyObj.keys().forEach { dateKey ->
                val d = dailyObj.optJSONObject(dateKey) ?: return@forEach
                val dateStr = d.optString("dateStr", dateKey).trim()
                if (dateStr.isBlank()) return@forEach
                val incomingDay = DailyStudyAnalyticsEntity(
                    dateString = dateStr,
                    totalTestTimeSeconds = d.optLong("studyTimeSeconds", 0L),
                    testsCompletedCount = d.optInt("testsCompleted", 0),
                    testsStartedCount = d.optInt("testsCompleted", 0),
                    questionsAttemptedCount = d.optInt("questionsAnswered", 0),
                    correctAnswersCount = d.optInt("correctAnswers", 0),
                    dailyGoalSeconds = appPrefs.getDailyGoalMinutes() * 60L,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
                val existingDay = if (!overwriteAll) paperDao.getDailyAnalytics(dateStr) else null
                if (existingDay == null || incomingDay.totalTestTimeSeconds >= existingDay.totalTestTimeSeconds) {
                    paperDao.upsertDailyAnalytics(incomingDay)
                }
                importedAnalyticsCount++
            }
        }

        // Paper MSQ Policies
        if (overwriteAll) {
            appPrefs.savePaperMsqPolicies(paperMsqOverrides)
        } else if (paperMsqOverrides.isNotEmpty()) {
            val map = appPrefs.getAllPaperMsqPolicies().toMutableMap()
            paperMsqOverrides.forEach { (pid, policy) ->
                if (!map.containsKey(pid)) map[pid] = policy
            }
            appPrefs.savePaperMsqPolicies(map)
        }

        // Flashcard SRS
        if (overwriteAll) {
            appPrefs.getAllFlashcardSrsMaps().keys.forEach { appPrefs.clearFlashcardSrsMap(it) }
        }
        noteSrsToRestore.forEach { (noteId, incomingMap) ->
            if (overwriteAll) {
                appPrefs.saveFlashcardSrsMap(noteId, incomingMap)
            } else {
                val cur = appPrefs.getFlashcardSrsMap(noteId).toMutableMap()
                incomingMap.forEach { (idx, st) ->
                    val ex = cur[idx]
                    if (ex == null || st.reviewCount > ex.reviewCount ||
                        (st.reviewCount == ex.reviewCount && st.lastReviewedAtMillis > ex.lastReviewedAtMillis)
                    ) {
                        cur[idx] = st
                    }
                }
                appPrefs.saveFlashcardSrsMap(noteId, cur)
            }
        }

        // 3. Vocabulary & Prompt Presets
        val vpObj = sections.optJSONObject("vocabAndPresets") ?: JSONObject()
        val compatVocabObj = JSONObject().apply {
            vpObj.optJSONArray("vocabDecks")?.let { put("vocabularyDecksJson", it.toString()) }
            vpObj.optJSONObject("vocabSrs")?.let { put("vocabularySrsJson", it.toString()) }
            vpObj.optString("englishVariant", "").takeIf { it.isNotBlank() }?.let {
                put("preferredEnglishVariant", it)
            }
            vpObj.optJSONObject("wotdStates")?.let { put("languageWordOfTheDayStatesJson", it.toString()) }
            vpObj.optJSONArray("headwordInbox")?.let { put("headwordInboxJson", it.toString()) }
            vpObj.optJSONArray("dismissedHeadwords")?.let { put("dismissedWordKeysJson", it.toString()) }
        }
        VaultArchiveEngine.applyVocabularyFieldsPublic(compatVocabObj, appPrefs, overwriteAll, mergeAppSettings)

        val presetsArr = vpObj.optJSONArray("promptPresets")
        if (presetsArr != null) {
            val incomingPresets = PromptPreset.deserializeList(presetsArr.toString())
            if (overwriteAll) {
                appPrefs.savePromptPresets(incomingPresets)
            } else if (incomingPresets.isNotEmpty()) {
                val merged = appPrefs.getPromptPresets().associateBy { it.id }.toMutableMap()
                incomingPresets.forEach { p ->
                    val ex = merged[p.id]
                    if (ex == null || p.updatedAt > ex.updatedAt) merged[p.id] = p
                }
                appPrefs.savePromptPresets(merged.values.toList())
            }
        }

        // 4. Planner Countdowns & Todos
        val countdownsArr = plannerObj.optJSONArray("countdowns")
        if (countdownsArr != null) {
            val incomingCds = buildList {
                for (i in 0 until countdownsArr.length()) {
                    val o = countdownsArr.optJSONObject(i) ?: continue
                    val normalized = JSONObject(o.toString()).apply {
                        if (!has("targetDateStr") && has("targetDateIso")) put("targetDateStr", optString("targetDateIso"))
                        if (!has("subject") && has("examType")) put("subject", optString("examType"))
                        if (!has("colorHex") && has("color")) put("colorHex", optString("color"))
                        if (!has("targetTimeStr") && has("targetTime")) put("targetTimeStr", optString("targetTime"))
                    }
                    ExamCountdownTarget.fromJson(normalized)?.let { add(it) }
                }
            }
            if (overwriteAll) {
                appPrefs.saveExamCountdowns(incomingCds)
            } else if (incomingCds.isNotEmpty()) {
                val merged = appPrefs.getExamCountdowns().associateBy { it.id }.toMutableMap()
                incomingCds.forEach { cd ->
                    val ex = merged[cd.id]
                    if (ex == null || cd.updatedAt > ex.updatedAt) merged[cd.id] = cd
                }
                appPrefs.saveExamCountdowns(merged.values.sortedBy { it.targetDateStr })
            }
        }

        val todosArr = plannerObj.optJSONArray("studyTodos")
        if (todosArr != null) {
            val incomingTodos = buildList {
                for (i in 0 until todosArr.length()) {
                    val o = todosArr.optJSONObject(i) ?: continue
                    val title = o.optString("title", "").trim()
                    if (title.isBlank()) continue
                    val linkedPaperStr = if (o.has("linkedPaperId") && !o.isNull("linkedPaperId")) o.optString("linkedPaperId", "") else ""
                    val linkedNoteStr = if (o.has("linkedNoteId") && !o.isNull("linkedNoteId")) o.optString("linkedNoteId", "") else ""
                    val completedAt = if (o.has("completedAt") && !o.isNull("completedAt")) o.optLong("completedAt", 0L).takeIf { it > 0L } else null
                    val createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    add(
                        StudyTodoItem(
                            id = o.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                            title = title,
                            priority = TodoPriority.fromString(o.optString("priority")),
                            dueDateStr = o.optString("dueDateStr", "").trim(),
                            isCompleted = o.optBoolean("isCompleted", false),
                            targetPomodoros = o.optInt("targetPomodoros", 1).coerceIn(1, 12),
                            completedPomodoros = o.optInt("completedPomodoros", 0).coerceAtLeast(0),
                            linkedType = TodoLinkType.fromString(o.optString("linkedType")),
                            linkedPaperId = linkedPaperStr.takeIf { it.isNotBlank() }?.let { webPaperIdToLocalId[it] },
                            linkedNoteId = linkedNoteStr.takeIf { it.isNotBlank() }?.let { webNoteIdToLocalId[it] },
                            linkedDeckLanguage = o.optString("linkedDeckLanguage", ""),
                            linkedLabel = o.optString("linkedLabel", ""),
                            createdAt = createdAt,
                            completedAt = completedAt,
                            updatedAt = maxOf(completedAt ?: 0L, createdAt)
                        )
                    )
                }
            }
            if (overwriteAll) {
                appPrefs.saveStudyTodos(incomingTodos)
            } else if (incomingTodos.isNotEmpty()) {
                val merged = appPrefs.getStudyTodos().associateBy { it.id }.toMutableMap()
                incomingTodos.forEach { td ->
                    val ex = merged[td.id]
                    if (ex == null || td.updatedAt > ex.updatedAt ||
                        (td.isCompleted && !ex.isCompleted) ||
                        td.completedPomodoros > ex.completedPomodoros
                    ) {
                        merged[td.id] = td
                    }
                }
                appPrefs.saveStudyTodos(merged.values.sortedByDescending { it.createdAt })
            }
        }

        // 5. Brain & Preferences + KnowledgeCourses
        val bpObj = sections.optJSONObject("brainAndPreferences") ?: JSONObject()
        val coursesObj = bpObj.optJSONObject("knowledgeCourses")
        if (coursesObj != null) {
            val incomingMap = linkedMapOf<String, List<String>>()
            coursesObj.keys().forEach { cRaw ->
                val c = cRaw.trim()
                if (c.isBlank()) return@forEach
                val arr = coursesObj.optJSONArray(cRaw)
                val subs = buildList<String> {
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val s = arr.optString(i, "").trim()
                            if (s.isNotBlank() && none { it.equals(s, ignoreCase = true) }) add(s)
                        }
                    }
                }
                incomingMap[c] = subs
            }
            if (overwriteAll) {
                appPrefs.saveKnowledgeCoursesMap(incomingMap)
            } else if (incomingMap.isNotEmpty()) {
                val merged = linkedMapOf<String, MutableList<String>>()
                val canonicalByLower = mutableMapOf<String, String>()
                fun absorb(cRaw: String, subs: List<String>) {
                    val c = cRaw.trim()
                    if (c.isBlank()) return
                    val canon = canonicalByLower.getOrPut(c.lowercase(Locale.ROOT)) { c }
                    val list = merged.getOrPut(canon) { mutableListOf() }
                    subs.forEach { sRaw ->
                        val s = sRaw.trim()
                        if (s.isNotBlank() && list.none { it.equals(s, ignoreCase = true) }) list.add(s)
                    }
                }
                appPrefs.getKnowledgeCoursesMap().forEach { (c, s) -> absorb(c, s) }
                incomingMap.forEach { (c, s) -> absorb(c, s) }
                appPrefs.saveKnowledgeCoursesMap(merged)
            }
        } else if (overwriteAll) {
            appPrefs.saveKnowledgeCoursesMap(emptyMap())
        }

        bpObj.optJSONObject("brainLabStats")?.let { statsObj ->
            val incStats = BrainLabStats.fromJson(statsObj)
            if (overwriteAll) {
                appPrefs.saveBrainLabStats(incStats)
            } else {
                appPrefs.saveBrainLabStats(BrainLabStats.mergeForRestore(appPrefs.getBrainLabStats(), incStats))
            }
        }
        if (syncSettings) {
            bpObj.optJSONObject("brainLabSettings")?.let { settingsObj ->
                appPrefs.saveBrainLabSettings(BrainLabSettings.fromJson(settingsObj))
            }
            plannerObj.optJSONObject("pomodoroConfig")?.let { pCfg ->
                appPrefs.setPomodoroPresetName(com.example.model.PomodoroPreset.fromString(pCfg.optString("preset")).name)
                if (pCfg.has("customFocusMinutes")) appPrefs.setPomodoroCustomFocusMinutes(pCfg.optInt("customFocusMinutes", 30))
                if (pCfg.has("customBreakMinutes")) appPrefs.setPomodoroCustomBreakMinutes(pCfg.optInt("customBreakMinutes", 5))
                if (pCfg.has("dailyFocusGoalMinutes")) appPrefs.setDailyGoalMinutes(pCfg.optInt("dailyFocusGoalMinutes", 120))
            }
            bpObj.optString("themeMode", "").takeIf { it.isNotBlank() }?.let {
                themePrefs.setThemeMode(ThemeMode.fromString(it))
            }
            bpObj.optString("colorPreset", "").takeIf { it.isNotBlank() }?.let {
                themePrefs.setColorSchemePreset(ColorSchemePreset.fromString(it))
            }
            bpObj.optString("fontScaleMode", "").takeIf { it.isNotBlank() }?.let {
                appPrefs.setFontScaleMode(AppFontScaleMode.fromString(it))
            }
        }

        val tipsArr = bpObj.optJSONArray("dismissedTips")
        if (tipsArr != null) {
            val tips = (0 until tipsArr.length()).mapNotNull { tipsArr.optString(it, "").takeIf { s -> s.isNotBlank() } }.toSet()
            if (overwriteAll) {
                appPrefs.saveDismissedFormatTips(tips)
            } else if (syncSettings && tips.isNotEmpty()) {
                appPrefs.saveDismissedFormatTips(appPrefs.getDismissedFormatTips() + tips)
            }
        }

        val currentDecks = appPrefs.getVocabularyDecks()
        return BackupCounts(
            papersCount = importedPapersCount,
            notesCount = importedNotesCount,
            knowledgeCoursesCount = appPrefs.getKnowledgeCoursesMap().size,
            foldersCount = mergedFolders.size,
            attemptsCount = importedAttemptsCount,
            analyticsDaysCount = importedAnalyticsCount,
            vocabDecksCount = currentDecks.size,
            vocabWordsCount = currentDecks.sumOf { it.entries.size },
            promptPresetsCount = appPrefs.getPromptPresets().size,
            countdownsCount = appPrefs.getExamCountdowns().size,
            todosCount = appPrefs.getStudyTodos().size,
            hasBrainLabData = appPrefs.getBrainLabStatsRawJson().isNotBlank(),
            hasAppSettings = true
        )
    }

    private fun sha256Utf8(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Scans raw JSON to extract exact verbatim child value slices inside an object property (e.g. `"sections"`),
     * avoiding numeric formatting differences (e.g. `2.0` vs `2`) when re-serializing parsed JSONObject trees.
     */
    internal fun extractObjectChildRawSlices(rawJson: String, propertyName: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val len = rawJson.length
        var i = 0
        fun skipWs() {
            while (i < len && rawJson[i].isWhitespace()) i++
        }
        fun readStringToken(): Pair<String, Int>? {
            if (i >= len || rawJson[i] != '"') return null
            val start = i
            val sb = StringBuilder()
            i++
            while (i < len) {
                val c = rawJson[i++]
                if (c == '\\' && i < len) {
                    val esc = rawJson[i++]
                    sb.append(esc)
                } else if (c == '"') {
                    return sb.toString() to start
                } else {
                    sb.append(c)
                }
            }
            return null
        }
        fun scanValueEnd(): Int {
            skipWs()
            if (i >= len) return len
            val first = rawJson[i]
            if (first == '"') {
                readStringToken()
                return i
            }
            if (first == '{' || first == '[') {
                var depth = 0
                var inStr = false
                while (i < len) {
                    val c = rawJson[i++]
                    if (inStr) {
                        if (c == '\\' && i < len) i++
                        else if (c == '"') inStr = false
                    } else {
                        if (c == '"') inStr = true
                        else if (c == '{' || c == '[') depth++
                        else if (c == '}' || c == ']') {
                            depth--
                            if (depth == 0) return i
                        }
                    }
                }
                return len
            }
            while (i < len && rawJson[i] != ',' && rawJson[i] != '}' && rawJson[i] != ']' && !rawJson[i].isWhitespace()) {
                i++
            }
            return i
        }

        skipWs()
        if (i >= len || rawJson[i] != '{') return result
        i++
        while (i < len) {
            skipWs()
            if (i >= len || rawJson[i] == '}') break
            if (rawJson[i] == ',') { i++; continue }
            val keyPair = readStringToken() ?: break
            skipWs()
            if (i < len && rawJson[i] == ':') i++ else break
            skipWs()
            if (keyPair.first == propertyName && i < len && rawJson[i] == '{') {
                i++
                while (i < len) {
                    skipWs()
                    if (i >= len || rawJson[i] == '}') break
                    if (rawJson[i] == ',') { i++; continue }
                    val childKey = readStringToken() ?: break
                    skipWs()
                    if (i < len && rawJson[i] == ':') i++ else break
                    skipWs()
                    val valStart = i
                    val valEnd = scanValueEnd()
                    result[childKey.first] = rawJson.substring(valStart, valEnd)
                }
                break
            } else {
                scanValueEnd()
            }
        }
        return result
    }
}
