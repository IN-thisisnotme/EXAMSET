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
import com.example.model.HeadwordQueueItem
import com.example.model.McqQuestion
import com.example.model.MsqScoringPolicy
import com.example.model.PaperCategory
import com.example.model.PaperMetadata
import com.example.model.PomodoroPreset
import com.example.model.PromptPreset
import com.example.model.PromptPresetMode
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
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
import java.util.Locale
import java.util.UUID

/**
 * Bidirectional adapter enabling Android to inspect, SHA-256 verify, restore,
 * and export Web `.examset.json` v5 (`examset-vault-v5`) archives natively.
 */
object WebVaultJsonAdapter {

    private val REQUIRED_WEB_SECTIONS = listOf(
        "papers",
        "folders",
        "attempts",
        "countdowns",
        "studyNotes",
        "vocabulary",
        "presetsAndDictation",
        "plannerAndAnalytics",
        "brainAndPreferences"
    )

    fun isWebVaultV5Json(rawJson: String): Boolean {
        val trimmed = rawJson.trimStart()
        if (!trimmed.startsWith("{")) return false
        return runCatching {
            val obj = JSONObject(trimmed)
            obj.optJSONObject("manifest")?.optString("format", "") == "examset-vault-v5"
        }.getOrDefault(false)
    }

    fun inspectWebVaultJson(
        file: File,
        rawJson: String,
        sourceLabel: String
    ): RestorePreview {
        val root = JSONObject(rawJson)
        val manifestObj = root.optJSONObject("manifest")
            ?: throw IllegalStateException("Invalid Web vault: missing manifest")
        val sectionsObj = root.optJSONObject("sections")
            ?: throw IllegalStateException("Invalid Web vault: missing sections")

        val sectionSlices = WebJsonSerializer.extractJsonObjectMemberRawSlices(rawJson, "sections")
        val checksumsObj = manifestObj.optJSONObject("entryChecksumsSha256") ?: JSONObject()
        val expectedChecksums = linkedMapOf<String, String>()
        checksumsObj.keys().forEach { k -> expectedChecksums[k] = checksumsObj.optString(k, "") }

        var mismatchCount = 0
        val verifiedKeys = mutableListOf<String>()

        for (reqKey in REQUIRED_WEB_SECTIONS) {
            if (!sectionsObj.has(reqKey) || expectedChecksums[reqKey].isNullOrBlank()) {
                mismatchCount++
            }
        }
        val keysToCheck = (REQUIRED_WEB_SECTIONS + expectedChecksums.keys).distinct()
        for (key in keysToCheck) {
            val expected = expectedChecksums[key]
            val slice = sectionSlices[key]
            if (expected.isNullOrBlank() || slice == null) {
                if (key in expectedChecksums || key in REQUIRED_WEB_SECTIONS) mismatchCount++
                continue
            }
            val minifiedSlice = WebJsonSerializer.minifyJsonPreserveRawTokens(slice)
            val minifiedHash = VaultArchiveEngine.computeSha256Hex(minifiedSlice.toByteArray(Charsets.UTF_8))
            val rawHash = VaultArchiveEngine.computeSha256Hex(slice.toByteArray(Charsets.UTF_8))
            if (minifiedHash.equals(expected, ignoreCase = true) || rawHash.equals(expected, ignoreCase = true)) {
                verifiedKeys.add(key)
            } else {
                mismatchCount++
            }
        }

        val verified = mismatchCount == 0 && verifiedKeys.size >= REQUIRED_WEB_SECTIONS.size
        val details = if (verified) {
            "Verified Web .examset.json (${verifiedKeys.size} sections SHA-256 OK)"
        } else {
            "Warning: missing required sections or $mismatchCount SHA-256 checksum mismatch(es)!"
        }

        val c = manifestObj.optJSONObject("counts") ?: JSONObject()
        val studyNotesSec = sectionsObj.optJSONObject("studyNotes")
        val coursesObj = studyNotesSec?.optJSONObject("knowledgeCourses")
        val detectedCourses = c.optInt("knowledgeCoursesCount", 0).takeIf { it > 0 }
            ?: coursesObj?.length() ?: 0

        val vocabSec = sectionsObj.optJSONObject("vocabulary")
        val vocabDecksArr = vocabSec?.optJSONArray("vocabDecks")
        var detectedWords = c.optInt("vocabWordsCount", 0)
        if (detectedWords == 0 && vocabDecksArr != null) {
            for (i in 0 until vocabDecksArr.length()) {
                detectedWords += vocabDecksArr.optJSONObject(i)?.optJSONArray("entries")?.length() ?: 0
            }
        }

        val presetsSec = sectionsObj.optJSONObject("presetsAndDictation")
        val draftsObj = presetsSec?.optJSONObject("drafts")
        val detectedDrafts = draftsObj?.let { d ->
            listOf("questionPromptDraft", "knowledgePromptDraft", "flashcardPromptDraft", "vocabularyPromptDraft")
                .count { d.optJSONObject(it) != null }
        } ?: 0

        val brainSec = sectionsObj.optJSONObject("brainAndPreferences")
        val hasBrain = brainSec?.optJSONObject("brainLabStats") != null ||
            brainSec?.optJSONObject("brainLabSettings") != null

        val manifest = BackupManifest(
            schemaVersion = manifestObj.optInt("schemaVersion", 5),
            appVersion = manifestObj.optString("appVersion", "web"),
            createdAt = manifestObj.optLong("createdAt", file.lastModified()),
            deviceName = manifestObj.optString("deviceName", "ExamSet Web Vault"),
            counts = BackupCounts(
                papersCount = c.optInt("papersCount", sectionsObj.optJSONArray("papers")?.length() ?: 0),
                notesCount = c.optInt("notesCount", studyNotesSec?.optJSONArray("items")?.length() ?: 0),
                knowledgeCoursesCount = detectedCourses,
                foldersCount = c.optInt("foldersCount", sectionsObj.optJSONArray("folders")?.length() ?: 0),
                attemptsCount = c.optInt("attemptsCount", sectionsObj.optJSONArray("attempts")?.length() ?: 0),
                analyticsDaysCount = c.optInt("analyticsDaysCount", 0),
                vocabDecksCount = c.optInt("vocabDecksCount", vocabDecksArr?.length() ?: 0),
                vocabWordsCount = detectedWords,
                promptPresetsCount = c.optInt("promptPresetsCount", presetsSec?.optJSONArray("presets")?.length() ?: 0),
                countdownsCount = c.optInt("countdownsCount", sectionsObj.optJSONArray("countdowns")?.length() ?: 0),
                todosCount = c.optInt("todosCount", 0),
                draftsCount = detectedDrafts,
                hasBrainLabData = hasBrain,
                hasAppSettings = brainSec != null
            ),
            entryChecksumsSha256 = expectedChecksums
        )

        return RestorePreview(
            sourceLabel = sourceLabel,
            isLegacyJson = false,
            isWebVaultJson = true,
            checksumVerified = verified,
            checksumDetails = details,
            manifest = manifest,
            tempFile = file
        )
    }

    suspend fun createWebVaultJson(
        context: Context,
        db: AppDatabase,
        appPrefs: ExamSetPreferences,
        themePrefs: ThemePreferences,
        nowMillis: Long = System.currentTimeMillis()
    ): String = WebVaultExportBuilder.createWebVaultJson(context, db, appPrefs, themePrefs, nowMillis)

    suspend fun restoreFromWebVaultJson(
        context: Context,
        db: AppDatabase,
        appPrefs: ExamSetPreferences,
        themePrefs: ThemePreferences,
        preview: RestorePreview,
        mode: RestoreMode,
        mergeAppSettings: Boolean
    ): BackupCounts {
        val rawJson = preview.tempFile?.takeIf { it.exists() }?.readText(Charsets.UTF_8)
            ?: throw IllegalStateException("Web vault JSON file unavailable for restore.")
        val root = JSONObject(rawJson)
        val sections = root.optJSONObject("sections")
            ?: throw IllegalStateException("Invalid Web vault: missing sections object.")

        if (mode == RestoreMode.FULL_REPLACE) {
            VaultArchiveEngine.createLocalRollingSnapshot(
                context = context,
                db = db,
                appPrefs = appPrefs,
                themePrefs = themePrefs,
                providerTag = "LOCAL_AUTO",
                prefix = "pre_restore"
            )
        }

        val overwriteAll = mode == RestoreMode.FULL_REPLACE
        val syncSettings = overwriteAll || mergeAppSettings

        // 1. Folders ("default" root maps to null on Android)
        val incomingFolders = parseWebFolders(sections.optJSONArray("folders"))
        val mergedFolders = if (overwriteAll) {
            incomingFolders
        } else {
            val map = appPrefs.getCustomFolders().associateBy { it.id }.toMutableMap()
            incomingFolders.forEach { f ->
                val cur = map[f.id]
                if (cur == null || f.updatedAt > cur.updatedAt) map[f.id] = f
            }
            map.values.toList()
        }
        appPrefs.saveCustomFolders(mergedFolders)
        val validFolderIds = mergedFolders.map { it.id }.toSet()

        // 2. Papers, Notes, Attempts, Analytics in Room transaction
        val parsedPapers = parseWebPapers(sections.optJSONArray("papers"), validFolderIds)
        val studyNotesSec = sections.optJSONObject("studyNotes")
        val parsedNotes = parseWebStudyNotes(studyNotesSec?.optJSONArray("items"), validFolderIds)
        val plannerSec = sections.optJSONObject("plannerAndAnalytics")
        val parsedAttempts = parseWebAttempts(sections.optJSONArray("attempts"))
        val parsedAnalytics = parseWebAnalytics(plannerSec?.optJSONArray("dailyAnalytics"), appPrefs.getDailyGoalMinutes())

        val paperDao = db.paperDao()
        val noteDao = db.studyNoteDao()
        val paperIdByWebId = mutableMapOf<String, Long>()
        val msqPolicyByRoomId = mutableMapOf<Long, MsqScoringPolicy>()
        val noteIdByWebId = mutableMapOf<String, Long>()

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

            parsedPapers.forEach { item ->
                val p = item.entity
                val existing = if (!overwriteAll) {
                    paperDao.findPaperByMetadata(p.title, p.subject, p.setNumber)
                        ?: p.bookletNumber.takeIf { it.isNotBlank() }?.let { paperDao.findPaperByBookletNumber(it) }
                        ?: paperDao.findPaperByRawText(p.rawText)
                } else null

                val finalId = if (existing != null) {
                    if (p.updatedAt > existing.updatedAt) {
                        paperDao.updatePaper(p.copy(id = existing.id))
                    }
                    existing.id
                } else {
                    paperDao.insertPaper(p.copy(id = 0L))
                }
                if (item.webId.isNotBlank()) paperIdByWebId[item.webId] = finalId
                msqPolicyByRoomId[finalId] = item.msqPolicy
            }

            parsedNotes.forEach { nItem ->
                val remappedPaper = nItem.linkedWebPaperId?.let { paperIdByWebId[it] }
                val cleanUid = nItem.entity.syncUid.ifBlank { UUID.randomUUID().toString() }
                val existing = if (!overwriteAll) {
                    noteDao.getStudyNoteBySyncUid(cleanUid)
                        ?: noteDao.getAllStudyNotesOnce().firstOrNull {
                            it.title == nItem.entity.title && it.rawContent == nItem.entity.rawContent
                        }
                } else null

                val finalNoteId = if (existing != null) {
                    val mergedNote = VaultArchiveEngine.mergeStudyNoteEntity(
                        existing = existing,
                        incoming = nItem.entity,
                        remappedLinkedPaper = remappedPaper,
                        cleanUid = cleanUid
                    )
                    noteDao.updateStudyNote(mergedNote)
                    existing.id
                } else {
                    noteDao.insertStudyNote(
                        nItem.entity.copy(
                            id = 0L,
                            linkedPaperId = remappedPaper,
                            syncUid = cleanUid
                        )
                    )
                }
                noteIdByWebId[nItem.webId] = finalNoteId
            }

            parsedAttempts.forEach { attItem ->
                val mappedPaperId = paperIdByWebId[attItem.webPaperId] ?: return@forEach
                val dedupKey = "${mappedPaperId}_${attItem.entity.createdAt}"
                if (!overwriteAll && existingAttemptsByKey.containsKey(dedupKey)) return@forEach
                paperDao.insertAttempt(attItem.entity.copy(id = 0L, paperId = mappedPaperId))
            }

            parsedAnalytics.forEach { day ->
                val existingDay = if (!overwriteAll) paperDao.getDailyAnalytics(day.dateString) else null
                if (existingDay == null || day.totalTestTimeSeconds >= existingDay.totalTestTimeSeconds) {
                    paperDao.upsertDailyAnalytics(day)
                }
            }
        }

        // Paper MSQ policies
        if (overwriteAll) {
            appPrefs.savePaperMsqPolicies(msqPolicyByRoomId)
        } else if (msqPolicyByRoomId.isNotEmpty()) {
            val policies = appPrefs.getAllPaperMsqPolicies().toMutableMap()
            msqPolicyByRoomId.forEach { (pid, pol) ->
                if (!policies.containsKey(pid)) policies[pid] = pol
            }
            appPrefs.savePaperMsqPolicies(policies)
        }

        // 3. Flashcard SRS per StudyNote
        if (overwriteAll) {
            appPrefs.getAllFlashcardSrsMaps().keys.forEach { appPrefs.clearFlashcardSrsMap(it) }
        }
        parsedNotes.forEach { nItem ->
            val targetId = noteIdByWebId[nItem.webId] ?: return@forEach
            if (nItem.srsMap.isEmpty()) return@forEach
            if (overwriteAll) {
                appPrefs.saveFlashcardSrsMap(targetId, nItem.srsMap)
            } else {
                val curMap = appPrefs.getFlashcardSrsMap(targetId).toMutableMap()
                nItem.srsMap.forEach { (idx, inc) ->
                    val existingSt = curMap[idx]
                    if (existingSt == null || VaultArchiveEngine.isIncomingSrsNewer(existingSt, inc)) {
                        curMap[idx] = inc
                    }
                }
                appPrefs.saveFlashcardSrsMap(targetId, curMap)
            }
        }

        // 4. Knowledge Courses
        val coursesObj = studyNotesSec?.optJSONObject("knowledgeCourses")
        val incomingCourses = coursesObj?.let { VaultArchiveEngine.parseKnowledgeCoursesMapJson(it.toString()) }.orEmpty()
        if (overwriteAll) {
            appPrefs.saveKnowledgeCoursesMap(incomingCourses)
        } else if (incomingCourses.isNotEmpty()) {
            appPrefs.saveKnowledgeCoursesMap(
                VaultArchiveEngine.mergeKnowledgeCoursesMaps(appPrefs.getKnowledgeCoursesMap(), incomingCourses)
            )
        }

        // 5. Vocabulary Hub
        restoreWebVocabulary(sections.optJSONObject("vocabulary"), appPrefs, overwriteAll, syncSettings)

        // 6. Presets, Drafts, Countdowns, Todos, Pomodoro, Brain Lab & Preferences
        restoreWebPresetsAndSettings(
            sections = sections,
            appPrefs = appPrefs,
            themePrefs = themePrefs,
            overwriteAll = overwriteAll,
            syncSettings = syncSettings,
            paperIdByWebId = paperIdByWebId,
            noteIdByWebId = noteIdByWebId
        )

        val currentDecks = appPrefs.getVocabularyDecks()
        return BackupCounts(
            papersCount = parsedPapers.size,
            notesCount = parsedNotes.size,
            knowledgeCoursesCount = appPrefs.getKnowledgeCoursesMap().size,
            foldersCount = incomingFolders.size,
            attemptsCount = parsedAttempts.size,
            analyticsDaysCount = parsedAnalytics.size,
            vocabDecksCount = currentDecks.size,
            vocabWordsCount = currentDecks.sumOf { it.entries.size },
            promptPresetsCount = appPrefs.getPromptPresets().size,
            countdownsCount = appPrefs.getExamCountdowns().size,
            todosCount = appPrefs.getStudyTodos().size,
            hasBrainLabData = true,
            hasAppSettings = syncSettings
        )
    }

    private data class ParsedWebPaper(val webId: String, val entity: PaperEntity, val msqPolicy: MsqScoringPolicy)
    private data class ParsedWebNote(
        val webId: String,
        val entity: StudyNoteEntity,
        val linkedWebPaperId: String?,
        val srsMap: Map<Int, FlashcardSrsState>
    )
    private data class ParsedWebAttempt(val webPaperId: String, val entity: AttemptEntity)

    private fun parseWebFolders(arr: JSONArray?): List<FolderItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id", "").trim()
                if (id.isBlank() || id == "default") continue
                val createdAt = o.optLong("createdAt", System.currentTimeMillis())
                val rawParent = if (o.has("parentId") && !o.isNull("parentId")) o.optString("parentId", "").trim() else ""
                val parentId = rawParent.takeIf { it.isNotBlank() && it != "default" }
                add(
                    FolderItem(
                        id = id,
                        name = o.optString("name", "Folder").ifBlank { "Folder" },
                        color = o.optString("color", "#0284C7").ifBlank { "#0284C7" },
                        parentId = parentId,
                        createdAt = createdAt,
                        updatedAt = o.optLong("updatedAt", createdAt)
                    )
                )
            }
        }
    }

    private fun parseWebPapers(arr: JSONArray?, validFolderIds: Set<String>): List<ParsedWebPaper> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val metaObj = obj.optJSONObject("metadata") ?: continue
                val qArr = obj.optJSONArray("questions") ?: JSONArray()
                val webId = metaObj.optString("id", "").trim()
                val title = metaObj.optString("title", "Examination Paper").ifBlank { "Examination Paper" }
                val subject = metaObj.optString("subject", "General Knowledge").ifBlank { "General Knowledge" }
                val topic = metaObj.optString("topic", "")
                val setNumber = metaObj.optString("setNumber", "01").ifBlank { "01" }
                val targetExam = metaObj.optString("targetExam", "")
                val roll = metaObj.optString("rollNumber", "").ifBlank { ExamNumberGenerator.generateRollNumber() }
                val booklet = metaObj.optString("bookletNumber", "").ifBlank { ExamNumberGenerator.generateBookletNumber() }
                val rawFolder = metaObj.optString("folderId", "").trim()
                val folderId = rawFolder.takeIf { it.isNotBlank() && it != "default" && it in validFolderIds }
                val createdAt = metaObj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = metaObj.optLong("updatedAt", createdAt)
                val msqPolicy = MsqScoringPolicy.fromCode(metaObj.optString("msqScoringPolicy"))

                val questions = buildList {
                    for (j in 0 until qArr.length()) {
                        val qObj = qArr.optJSONObject(j) ?: continue
                        val opts = qObj.optJSONObject("options") ?: JSONObject()
                        add(
                            McqQuestion(
                                num = qObj.optInt("num", j + 1),
                                question = qObj.optString("question", ""),
                                options = QuestionOptions(
                                    a = opts.optString("a", ""),
                                    b = opts.optString("b", ""),
                                    c = opts.optString("c", ""),
                                    d = opts.optString("d", ""),
                                    e = opts.optString("e", "")
                                ),
                                correctAnswer = if (qObj.has("correctAnswer") && !qObj.isNull("correctAnswer")) qObj.optString("correctAnswer").ifBlank { null } else null,
                                explanation = if (qObj.has("explanation") && !qObj.isNull("explanation")) qObj.optString("explanation").ifBlank { null } else null,
                                questionType = qObj.optString("questionType", "").ifBlank { null },
                                diagramUrl = qObj.optString("diagramUrl", "").ifBlank { null },
                                diagramLabel = qObj.optString("diagramLabel", "").ifBlank { null },
                                formula = qObj.optString("formula", "").ifBlank { null },
                                isNeutralized = qObj.optBoolean("isNeutralized", false),
                                isVerified = qObj.optBoolean("isVerified", false)
                            )
                        )
                    }
                }

                val metadata = PaperMetadata(
                    id = webId,
                    title = title,
                    subject = subject,
                    topic = topic,
                    setNumber = setNumber,
                    exam = targetExam,
                    folderId = folderId,
                    manualCategory = mapWebCategoryToAndroid(metaObj.optString("manualCategory", "")),
                    rollNumber = roll,
                    bookletNumber = booklet,
                    durationMinutes = metaObj.optInt("durationMinutes", 60).coerceAtLeast(1),
                    positiveMarks = metaObj.optDouble("positiveMarks", 4.0),
                    negativeMarks = metaObj.optDouble("negativeMarks", 1.0),
                    unattendedMarks = metaObj.optDouble("unattendedMarks", 0.0),
                    msqScoringPolicy = msqPolicy,
                    createdAt = createdAt
                )
                val rawText = McqParser.reconstructToRaw(
                    QuestionPaper(metadata = metadata, questions = questions, createdAt = createdAt)
                )
                val entity = PaperEntity(
                    title = title,
                    subject = subject,
                    topic = topic,
                    setNumber = setNumber,
                    exam = targetExam,
                    questionCount = questions.size,
                    answerKeyCount = questions.count { !it.correctAnswer.isNullOrBlank() },
                    explanationCount = questions.count { !it.explanation.isNullOrBlank() },
                    rawText = rawText,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    folderId = folderId,
                    rollNumber = roll,
                    bookletNumber = booklet,
                    durationMinutes = metadata.durationMinutes,
                    positiveMarks = metadata.positiveMarks,
                    negativeMarks = metadata.negativeMarks,
                    unattendedMarks = metadata.unattendedMarks,
                    manualCategory = metadata.manualCategory
                )
                add(ParsedWebPaper(webId = webId, entity = entity, msqPolicy = msqPolicy))
            }
        }
    }

    private fun parseWebStudyNotes(arr: JSONArray?, validFolderIds: Set<String>): List<ParsedWebNote> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val webId = o.optString("id", "").ifBlank { UUID.randomUUID().toString() }
                val rawFolder = o.optString("folderId", "").trim()
                val folderId = rawFolder.takeIf { it.isNotBlank() && it != "default" && it in validFolderIds }
                val linkedWebPaperId = if (o.has("linkedPaperId") && !o.isNull("linkedPaperId")) {
                    o.optString("linkedPaperId", "").trim().ifBlank { null }
                } else null
                val bookmarked = o.optJSONArray("bookmarkedIndices")?.toString() ?: "[]"
                val mastered = o.optJSONArray("masteredIndices")?.toString() ?: "[]"
                val ruleJson = o.optJSONObject("formatRule")?.toString() ?: "{}"
                val srsObj = o.optJSONObject("srsStates")
                val srsMap = mutableMapOf<Int, FlashcardSrsState>()
                if (srsObj != null) {
                    srsObj.keys().forEach { key ->
                        val stObj = srsObj.optJSONObject(key) ?: return@forEach
                        val idx = key.toIntOrNull() ?: stObj.optInt("blockIndex", -1)
                        if (idx < 0) return@forEach
                        val rating = runCatching {
                            val r = stObj.optString("lastRating", "")
                            if (r.isNotBlank()) SrsRating.valueOf(r) else null
                        }.getOrNull()
                        srsMap[idx] = FlashcardSrsState(
                            blockIndex = idx,
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
                }
                val createdAt = o.optLong("createdAt", System.currentTimeMillis())
                val entity = StudyNoteEntity(
                    folderId = folderId,
                    title = o.optString("title", "Knowledge Set").ifBlank { "Knowledge Set" },
                    subject = o.optString("subject", ""),
                    course = o.optString("course", ""),
                    sourceAi = o.optString("sourceAi", "One-Liners"),
                    rawContent = o.optString("rawContent", ""),
                    bookmarkedLinesJson = bookmarked,
                    createdAt = createdAt,
                    updatedAt = o.optLong("updatedAt", createdAt),
                    collectionType = o.optString("collectionType", "ONE_LINERS"),
                    customLabel = o.optString("customLabel", ""),
                    linkedPaperId = null,
                    formatRuleJson = ruleJson,
                    masteredBlocksJson = mastered,
                    syncUid = webId
                )
                add(ParsedWebNote(webId = webId, entity = entity, linkedWebPaperId = linkedWebPaperId, srsMap = srsMap))
            }
        }
    }

    private fun parseWebAttempts(arr: JSONArray?): List<ParsedWebAttempt> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val webPaperId = o.optString("paperId", "").trim()
                if (webPaperId.isBlank()) continue
                val ansObj = o.optJSONObject("submittedAnswers") ?: JSONObject()
                val ansMap = mutableMapOf<Int, String>()
                ansObj.keys().forEach { k ->
                    val qNum = k.toIntOrNull() ?: return@forEach
                    val v = ansObj.optString(k, "").trim()
                    if (v.isNotBlank()) ansMap[qNum] = v
                }
                val qtObj = o.optJSONObject("questionTimes") ?: JSONObject()
                val qtMap = mutableMapOf<Int, Long>()
                qtObj.keys().forEach { k ->
                    val qNum = k.toIntOrNull() ?: return@forEach
                    val secs = qtObj.optLong(k, 0L)
                    if (secs > 0L) qtMap[qNum] = secs
                }
                val correct = o.optInt("correctCount", 0)
                val incorrect = o.optInt("incorrectCount", 0)
                val unanswered = o.optInt("unansweredCount", 0)
                val partial = o.optInt("partialCount", 0)
                val submittedAt = o.optLong("submittedAt", System.currentTimeMillis())
                val entity = AttemptEntity(
                    paperId = 0L,
                    answersJson = AttemptEntity.buildAnswersJson(
                        map = ansMap,
                        rollNumber = o.optString("rollNumber", ""),
                        bookletNumber = o.optString("bookletNumber", ""),
                        bookletCode = o.optString("bookletCode", ""),
                        questionTimes = qtMap
                    ),
                    isSubmitted = true,
                    score = o.optDouble("score", 0.0),
                    correctCount = correct + partial,
                    incorrectCount = incorrect,
                    unansweredCount = unanswered,
                    totalQuestions = correct + partial + incorrect + unanswered,
                    timeSpentSeconds = o.optLong("timeSpentSeconds", 0L),
                    createdAt = submittedAt,
                    submittedAt = submittedAt
                )
                add(ParsedWebAttempt(webPaperId = webPaperId, entity = entity))
            }
        }
    }

    private fun parseWebAnalytics(arr: JSONArray?, defaultGoalMinutes: Int): List<DailyStudyAnalyticsEntity> {
        if (arr == null) return emptyList()
        val goalSec = (defaultGoalMinutes.coerceIn(5, 360)) * 60L
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val dateStr = o.optString("dateStr", "").trim()
                if (dateStr.isBlank()) continue
                val completed = o.optInt("testsCompleted", 0)
                add(
                    DailyStudyAnalyticsEntity(
                        dateString = dateStr,
                        totalTestTimeSeconds = o.optLong("studyTimeSeconds", 0L),
                        testsCompletedCount = completed,
                        testsStartedCount = completed,
                        questionsAttemptedCount = o.optInt("questionsAnswered", 0),
                        correctAnswersCount = o.optInt("correctAnswers", 0),
                        dailyGoalSeconds = goalSec,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun restoreWebVocabulary(
        vocabSec: JSONObject?,
        appPrefs: ExamSetPreferences,
        overwriteAll: Boolean,
        syncSettings: Boolean
    ) {
        if (vocabSec == null) return
        if (syncSettings) {
            vocabSec.optString("englishVariant", "").takeIf { it.isNotBlank() }?.let {
                appPrefs.setPreferredEnglishVariant(it)
            }
        }

        val deckIdRemap = mutableMapOf<String, String>()
        val entryIdRemap = mutableMapOf<String, String>()
        val decksArr = vocabSec.optJSONArray("vocabDecks")
        if (decksArr != null) {
            val incomingDecks = VocabularyDeck.deserializeList(decksArr.toString())
            if (overwriteAll) {
                appPrefs.saveVocabularyDecks(incomingDecks)
            } else if (incomingDecks.isNotEmpty()) {
                appPrefs.saveVocabularyDecks(
                    VaultArchiveEngine.mergeVocabularyDecksForRestore(
                        existingDecks = appPrefs.getVocabularyDecks(),
                        incomingDecks = incomingDecks,
                        deckIdRemap = deckIdRemap,
                        entryIdRemap = entryIdRemap
                    )
                )
            }
        }

        val srsObj = vocabSec.optJSONObject("vocabSrs")
        if (srsObj != null) {
            val incomingSrs = appPrefs.parseVocabSrsRootJson(srsObj.toString())
            if (overwriteAll) {
                appPrefs.saveAllVocabularySrsMaps(incomingSrs)
            } else if (incomingSrs.isNotEmpty()) {
                val merged = appPrefs.getAllVocabularySrsMaps().mapValues { it.value.toMutableMap() }.toMutableMap()
                incomingSrs.forEach { (rawDeckId, entryMap) ->
                    val resolvedDeckId = deckIdRemap[rawDeckId] ?: rawDeckId
                    val target = merged.getOrPut(resolvedDeckId) { mutableMapOf() }
                    entryMap.forEach { (rawEntryId, st) ->
                        val resolvedEntryId = entryIdRemap[rawEntryId] ?: rawEntryId
                        val cur = target[resolvedEntryId]
                        if (cur == null || VaultArchiveEngine.isIncomingSrsNewer(cur, st)) {
                            target[resolvedEntryId] = st
                        }
                    }
                }
                appPrefs.saveAllVocabularySrsMaps(merged)
            }
        }

        val wotdObj = vocabSec.optJSONObject("wotdStates")
        if (wotdObj != null) {
            val rawWotd = wotdObj.toString()
            if (overwriteAll) {
                appPrefs.saveLanguageWordOfTheDayStatesJson(rawWotd)
            } else {
                appPrefs.saveLanguageWordOfTheDayStatesJson(
                    WordOfTheDayService.mergePersistedLanguageStatesJson(appPrefs.getLanguageWordOfTheDayStatesJson(), rawWotd)
                )
            }
        }

        val inboxArr = vocabSec.optJSONArray("headwordInbox")
        if (inboxArr != null) {
            val incomingItems = (0 until inboxArr.length()).mapNotNull { HeadwordQueueItem.fromJson(inboxArr.optJSONObject(it)) }
            if (overwriteAll) {
                appPrefs.saveHeadwordInbox(incomingItems)
            } else if (incomingItems.isNotEmpty()) {
                val cur = appPrefs.getHeadwordInbox()
                val curPairs = cur.map { it.language.trim().lowercase(Locale.ROOT) to it.normalizedWordKey() }.toSet()
                appPrefs.saveHeadwordInbox(cur + incomingItems.filter {
                    (it.language.trim().lowercase(Locale.ROOT) to it.normalizedWordKey()) !in curPairs
                })
            }
        }

        val dismissedArr = vocabSec.optJSONArray("dismissedHeadwords")
        if (dismissedArr != null) {
            val incomingKeys = (0 until dismissedArr.length()).mapNotNull { dismissedArr.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
            if (overwriteAll) {
                appPrefs.saveDismissedWordKeys(incomingKeys)
            } else if (incomingKeys.isNotEmpty()) {
                appPrefs.saveDismissedWordKeys(appPrefs.getDismissedWordKeys() + incomingKeys)
            }
        }
    }

    private fun restoreWebPresetsAndSettings(
        sections: JSONObject,
        appPrefs: ExamSetPreferences,
        themePrefs: ThemePreferences,
        overwriteAll: Boolean,
        syncSettings: Boolean,
        paperIdByWebId: Map<String, Long>,
        noteIdByWebId: Map<String, Long>
    ) {
        val presetsSec = sections.optJSONObject("presetsAndDictation")
        val presetsArr = presetsSec?.optJSONArray("presets")
        if (presetsArr != null) {
            val incoming = PromptPreset.deserializeList(presetsArr.toString())
            if (overwriteAll) {
                appPrefs.savePromptPresets(incoming)
            } else if (incoming.isNotEmpty()) {
                val map = appPrefs.getPromptPresets().associateBy { it.id }.toMutableMap()
                incoming.forEach { p ->
                    val cur = map[p.id]
                    if (cur == null || p.updatedAt > cur.updatedAt) map[p.id] = p
                }
                appPrefs.savePromptPresets(map.values.toList())
            }
        }
        val activeIds = presetsSec?.optJSONObject("activePresetIds")?.toString()
        if (syncSettings && !activeIds.isNullOrBlank()) {
            appPrefs.saveActivePromptPresetIdsFromJson(activeIds, overwriteAll)
        } else if (overwriteAll) {
            PromptPresetMode.entries.forEach { appPrefs.setActivePromptPresetId(it, null) }
        }
        if (syncSettings) {
            presetsSec?.optJSONObject("drafts")?.let { d ->
                d.optJSONObject("questionPromptDraft")?.let { appPrefs.saveQuestionPromptDraft(com.example.model.QuestionPromptConfig.fromJson(it)) }
                d.optJSONObject("knowledgePromptDraft")?.let { appPrefs.saveKnowledgePromptDraft(com.example.model.KnowledgePromptConfig.fromJson(it)) }
                d.optJSONObject("flashcardPromptDraft")?.let { appPrefs.saveFlashcardPromptDraft(com.example.model.FlashcardPromptConfig.fromJson(it)) }
                d.optJSONObject("vocabularyPromptDraft")?.let { appPrefs.saveVocabularyPromptDraft(com.example.model.VocabularyPromptConfig.fromJson(it)) }
            }
        }

        // Countdowns (Web uses targetDateIso, examType, color, targetTime)
        val countdownArr = sections.optJSONArray("countdowns")
        if (countdownArr != null) {
            val incoming = buildList {
                for (i in 0 until countdownArr.length()) {
                    val o = countdownArr.optJSONObject(i) ?: continue
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
                appPrefs.saveExamCountdowns(incoming)
            } else if (incoming.isNotEmpty()) {
                val map = appPrefs.getExamCountdowns().associateBy { it.id }.toMutableMap()
                incoming.forEach { c ->
                    val cur = map[c.id]
                    if (cur == null || c.updatedAt > cur.updatedAt) map[c.id] = c
                }
                appPrefs.saveExamCountdowns(map.values.sortedBy { it.targetDateStr })
            }
        }

        // Todos & Pomodoro
        val plannerSec = sections.optJSONObject("plannerAndAnalytics")
        val todosArr = plannerSec?.optJSONArray("studyTodos")
        if (todosArr != null) {
            val incoming = buildList {
                for (i in 0 until todosArr.length()) {
                    val o = todosArr.optJSONObject(i) ?: continue
                    val title = o.optString("title", "").trim()
                    if (title.isBlank()) continue
                    val linkedWebP = o.optString("linkedPaperId", "").trim()
                    val linkedWebN = o.optString("linkedNoteId", "").trim()
                    val created = o.optLong("createdAt", System.currentTimeMillis())
                    val completedAt = if (o.has("completedAt") && !o.isNull("completedAt")) o.optLong("completedAt") else null
                    add(
                        StudyTodoItem(
                            id = o.optString("id", "").ifBlank { UUID.randomUUID().toString() },
                            title = title,
                            priority = TodoPriority.fromString(o.optString("priority")),
                            dueDateStr = o.optString("dueDateStr", ""),
                            isCompleted = o.optBoolean("isCompleted", false),
                            targetPomodoros = o.optInt("targetPomodoros", 1).coerceAtLeast(1),
                            completedPomodoros = o.optInt("completedPomodoros", 0).coerceAtLeast(0),
                            linkedType = TodoLinkType.fromString(o.optString("linkedType")),
                            linkedPaperId = linkedWebP.takeIf { it.isNotBlank() }?.let { paperIdByWebId[it] },
                            linkedNoteId = linkedWebN.takeIf { it.isNotBlank() }?.let { noteIdByWebId[it] },
                            linkedDeckLanguage = o.optString("linkedDeckLanguage", ""),
                            linkedLabel = o.optString("linkedLabel", ""),
                            createdAt = created,
                            completedAt = completedAt,
                            updatedAt = o.optLong("updatedAt", maxOf(completedAt ?: 0L, created))
                        )
                    )
                }
            }
            if (overwriteAll) {
                appPrefs.saveStudyTodos(incoming)
            } else if (incoming.isNotEmpty()) {
                val map = appPrefs.getStudyTodos().associateBy { it.id }.toMutableMap()
                incoming.forEach { t ->
                    val cur = map[t.id]
                    if (cur == null || t.updatedAt > cur.updatedAt || (t.isCompleted && !cur.isCompleted) || t.completedPomodoros > cur.completedPomodoros) {
                        map[t.id] = t
                    }
                }
                appPrefs.saveStudyTodos(map.values.sortedByDescending { it.createdAt })
            }
        }

        val pom = plannerSec?.optJSONObject("pomodoroConfig")
        if (syncSettings && pom != null) {
            pom.optString("preset", "").takeIf { it.isNotBlank() }?.let {
                appPrefs.setPomodoroPresetName(PomodoroPreset.fromString(it).name)
            }
            if (pom.has("customFocusMinutes")) appPrefs.setPomodoroCustomFocusMinutes(pom.optInt("customFocusMinutes", 30))
            if (pom.has("customBreakMinutes")) appPrefs.setPomodoroCustomBreakMinutes(pom.optInt("customBreakMinutes", 5))
            if (pom.has("dailyFocusGoalMinutes")) appPrefs.setDailyGoalMinutes(pom.optInt("dailyFocusGoalMinutes", 30))
        }

        // Brain Lab & Theme/Typography
        val brainSec = sections.optJSONObject("brainAndPreferences") ?: return
        brainSec.optJSONObject("brainLabStats")?.let { statsObj ->
            val incStats = BrainLabStats.fromJson(statsObj)
            if (overwriteAll) {
                appPrefs.saveBrainLabStats(incStats)
            } else {
                appPrefs.saveBrainLabStats(BrainLabStats.mergeForRestore(appPrefs.getBrainLabStats(), incStats))
            }
        } ?: if (overwriteAll) appPrefs.saveBrainLabStats(BrainLabStats()) else Unit

        if (syncSettings) {
            brainSec.optJSONObject("brainLabSettings")?.let {
                appPrefs.saveBrainLabSettings(BrainLabSettings.fromJson(it))
            }
            brainSec.optString("themeMode", "").takeIf { it.isNotBlank() }?.let {
                themePrefs.setThemeMode(ThemeMode.fromString(it))
            }
            brainSec.optString("colorPreset", "").takeIf { it.isNotBlank() }?.let {
                themePrefs.setColorSchemePreset(ColorSchemePreset.fromString(it))
            }
            brainSec.optString("fontScaleMode", "").takeIf { it.isNotBlank() }?.let {
                appPrefs.setFontScaleMode(AppFontScaleMode.fromString(it))
            }
        }

        val tipsArr = brainSec.optJSONArray("dismissedTips")
        if (tipsArr != null) {
            val incTips = (0 until tipsArr.length()).mapNotNull { tipsArr.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
            if (overwriteAll) {
                appPrefs.saveDismissedFormatTips(incTips)
            } else if (syncSettings && incTips.isNotEmpty()) {
                appPrefs.saveDismissedFormatTips(appPrefs.getDismissedFormatTips() + incTips)
            }
        }
    }

    private fun mapWebCategoryToAndroid(raw: String): String {
        return when (raw.trim().uppercase(Locale.ROOT)) {
            "FULL_MOCK" -> PaperCategory.MOCK_TEST.code
            "SUBJECT_TEST", "TOPIC_DRILL" -> PaperCategory.SECTIONAL.code
            "PYQ" -> PaperCategory.PYQ.code
            "REVISION" -> PaperCategory.STUDY_NOTES.code
            else -> ""
        }
    }
}
