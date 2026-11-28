package com.example.util

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.AppFontScaleMode
import com.example.data.AttemptEntity
import com.example.data.BackupSnapshotEntity
import com.example.data.DailyStudyAnalyticsEntity
import com.example.data.ExamSetPreferences
import com.example.data.PaperEntity
import com.example.data.StudyNoteEntity
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import com.example.data.ColorSchemePreset
import com.example.model.BackupCounts
import com.example.model.BackupManifest
import com.example.model.FlashcardSrsState
import com.example.model.FolderItem
import com.example.model.PromptPreset
import com.example.model.PromptPresetMode
import com.example.model.PromptPresetsImportSummary
import com.example.model.RestoreMode
import com.example.model.RestorePreview
import com.example.model.SrsRating
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.DigestInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object VaultArchiveEngine {

    private const val VAULT_DIR_NAME = "vault_backups"
    private const val EXPORT_CACHE_DIR = "vault_exports"
    private const val MAX_ROLLING_AUTO_SNAPSHOTS = 5
    internal const val MAX_ENTRY_UNCOMPRESSED_BYTES = 32L * 1024L * 1024L

    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_FOLDERS = "folders.json"
    private const val ENTRY_PAPERS = "papers.ndjson"
    private const val ENTRY_NOTES = "notes.ndjson"
    private const val ENTRY_ATTEMPTS = "attempts.ndjson"
    private const val ENTRY_ANALYTICS = "analytics.json"
    private const val ENTRY_VOCABULARY = "vocabulary.json"
    private const val ENTRY_PREFERENCES = "preferences.json"

    fun formatCountsSummary(counts: BackupCounts): String {
        val parts = mutableListOf(
            "${counts.papersCount} sets",
            "${counts.notesCount} knowledge"
        )
        if (counts.vocabDecksCount > 0 || counts.vocabWordsCount > 0) {
            parts += "${counts.vocabDecksCount} vocab (${counts.vocabWordsCount} words)"
        } else {
            parts += "0 vocab"
        }
        return parts.joinToString(" • ")
    }

    fun buildSnapshotDetails(counts: BackupCounts, originNote: String = ""): String {
        val base = "${formatCountsSummary(counts)} • ${counts.attemptsCount} attempts"
        return if (originNote.isBlank()) base else "$base • $originNote"
    }

    fun getVaultDir(context: Context): File {
        return File(context.filesDir, VAULT_DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    fun getExportCacheDir(context: Context): File {
        return File(context.cacheDir, EXPORT_CACHE_DIR).apply { if (!exists()) mkdirs() }
    }

    fun pruneExportCacheDir(
        context: Context,
        maxAgeMs: Long = 60L * 60L * 1000L,
        excludeFile: File? = null
    ) {
        val dir = File(context.cacheDir, EXPORT_CACHE_DIR)
        if (!dir.exists()) return
        val cutoff = System.currentTimeMillis() - maxAgeMs
        val excludeCanonical = excludeFile?.let { runCatching { it.canonicalPath }.getOrNull() }
        dir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            val path = runCatching { f.canonicalPath }.getOrNull()
            if (excludeCanonical != null && path == excludeCanonical) return@forEach
            if (f.lastModified() < cutoff) {
                runCatching { f.delete() }
            }
        }
    }

    fun getShareableVaultUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun defaultVaultFileName(prefix: String = "examset_vault"): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${prefix}_$stamp.examset.zip"
    }

    suspend fun createVaultArchive(
        context: Context,
        db: AppDatabase,
        appPrefs: ExamSetPreferences,
        themePrefs: ThemePreferences,
        destinationFile: File,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): BackupManifest {
        destinationFile.parentFile?.mkdirs()

        val paperDao = db.paperDao()
        val noteDao = db.studyNoteDao()

        onProgress("Collecting library metadata…", 0.1f)
        val folders = appPrefs.getCustomFolders()
        val papers = paperDao.getAllPapersOnce()
        val rawNotes = noteDao.getAllStudyNotesOnce()
        val notes = rawNotes.map { note ->
            if (note.syncUid.isNotBlank()) note else note.copy(syncUid = UUID.randomUUID().toString())
        }
        val attempts = paperDao.getAllAttemptsOnce()
        val analytics = paperDao.getAllDailyAnalyticsOnce()
        val vocabDecks = appPrefs.getVocabularyDecks()
        val vocabWordsCount = vocabDecks.sumOf { it.entries.size }
        val promptPresets = appPrefs.getPromptPresets()
        val countdowns = appPrefs.getExamCountdowns()
        val todos = appPrefs.getStudyTodos()
        val draftsCount = listOfNotNull(
            appPrefs.getQuestionPromptDraft(),
            appPrefs.getKnowledgePromptDraft(),
            appPrefs.getFlashcardPromptDraft(),
            appPrefs.getVocabularyPromptDraft()
        ).size
        val hasBrainLabData = appPrefs.getBrainLabStatsRawJson().isNotBlank() ||
            appPrefs.getBrainLabSettingsRawJson().isNotBlank()
        val knowledgeCoursesCount = appPrefs.getKnowledgeCoursesMap().size
        val noteUidById = notes.associate { it.id to it.syncUid }

        val checksums = linkedMapOf<String, String>()
        val createdAt = System.currentTimeMillis()
        val counts = BackupCounts(
            papersCount = papers.size,
            notesCount = notes.size,
            knowledgeCoursesCount = knowledgeCoursesCount,
            foldersCount = folders.size,
            attemptsCount = attempts.size,
            analyticsDaysCount = analytics.size,
            vocabDecksCount = vocabDecks.size,
            vocabWordsCount = vocabWordsCount,
            promptPresetsCount = promptPresets.size,
            countdownsCount = countdowns.size,
            todosCount = todos.size,
            draftsCount = draftsCount,
            hasBrainLabData = hasBrainLabData,
            hasAppSettings = true
        )

        ZipOutputStream(BufferedOutputStream(FileOutputStream(destinationFile))).use { zos ->
            zos.setLevel(6)

            // 1. Folders
            onProgress("Packing ${folders.size} folders…", 0.2f)
            val foldersJson = serializeFoldersJson(folders)
            checksums[ENTRY_FOLDERS] = writeStringEntryWithSha256(zos, ENTRY_FOLDERS, foldersJson)

            // 2. Question Sets (NDJSON streaming line-by-line)
            onProgress("Streaming ${papers.size} question sets…", 0.35f)
            checksums[ENTRY_PAPERS] = writeNdjsonEntryWithSha256(zos, ENTRY_PAPERS) { writeLine ->
                papers.forEach { paper -> writeLine(serializePaper(paper).toString()) }
            }

            // 3. Knowledge Sets / Study Notes (NDJSON streaming)
            onProgress("Streaming ${notes.size} knowledge sets…", 0.55f)
            checksums[ENTRY_NOTES] = writeNdjsonEntryWithSha256(zos, ENTRY_NOTES) { writeLine ->
                notes.forEach { note -> writeLine(serializeStudyNote(note).toString()) }
            }

            // 4. Test Attempts (NDJSON streaming)
            onProgress("Streaming ${attempts.size} test attempts…", 0.7f)
            checksums[ENTRY_ATTEMPTS] = writeNdjsonEntryWithSha256(zos, ENTRY_ATTEMPTS) { writeLine ->
                attempts.forEach { attempt -> writeLine(serializeAttempt(attempt).toString()) }
            }

            // 5. Daily Analytics
            onProgress("Packing daily study analytics…", 0.8f)
            checksums[ENTRY_ANALYTICS] = writeStringEntryWithSha256(zos, ENTRY_ANALYTICS, serializeAnalyticsJson(analytics))

            // 6. Dedicated Vocabulary Archive (decks, entries, SM-2 SRS, WOTD, variants)
            onProgress("Packing ${vocabDecks.size} vocabulary sets ($vocabWordsCount words)…", 0.9f)
            checksums[ENTRY_VOCABULARY] = writeStringEntryWithSha256(
                zos,
                ENTRY_VOCABULARY,
                serializeVocabularyArchiveJson(appPrefs, vocabDecks)
            )

            // 7. Preferences, Flashcard SRS, Prompt Presets & Drafts (+ legacy vocab keys for compatibility)
            checksums[ENTRY_PREFERENCES] = writeStringEntryWithSha256(
                zos,
                ENTRY_PREFERENCES,
                serializePreferencesJson(appPrefs, themePrefs, noteUidById)
            )

            // 8. Manifest (always written last with entry checksums)
            val manifest = BackupManifest(
                schemaVersion = 5,
                appVersion = "1.0",
                createdAt = createdAt,
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                counts = counts,
                entryChecksumsSha256 = checksums
            )
            writeStringEntryWithSha256(zos, ENTRY_MANIFEST, serializeManifest(manifest).toString(2))
            onProgress("Finalizing vault archive…", 1.0f)
            return manifest
        }
    }

    suspend fun createLocalRollingSnapshot(
        context: Context,
        db: AppDatabase,
        appPrefs: ExamSetPreferences,
        themePrefs: ThemePreferences,
        providerTag: String = "LOCAL_VAULT",
        prefix: String = "vault"
    ): Pair<BackupSnapshotEntity, File> {
        val vaultDir = getVaultDir(context)
        val file = File(vaultDir, defaultVaultFileName(prefix))
        val manifest = createVaultArchive(context, db, appPrefs, themePrefs, file)
        val fileSha = computeFileSha256(file)

        val snapshot = BackupSnapshotEntity(
            provider = providerTag,
            fileName = file.name,
            localPath = file.absolutePath,
            remoteFileId = "",
            checksumSha256 = fileSha,
            papersCount = manifest.counts.papersCount,
            notesCount = manifest.counts.notesCount,
            foldersCount = manifest.counts.foldersCount,
            attemptsCount = manifest.counts.attemptsCount,
            sizeBytes = file.length(),
            status = "VERIFIED",
            details = buildSnapshotDetails(manifest.counts),
            createdAt = manifest.createdAt
        )
        db.backupSnapshotDao().insertSnapshot(snapshot)
        pruneOldRollingSnapshots(vaultDir)
        return snapshot to file
    }

    private fun pruneOldRollingSnapshots(vaultDir: File) {
        val autoFiles = vaultDir.listFiles { f ->
            f.isFile && (f.name.startsWith("auto_") || f.name.startsWith("pre_restore_")) && f.name.endsWith(".zip")
        }?.sortedByDescending { it.lastModified() }.orEmpty()

        if (autoFiles.size > MAX_ROLLING_AUTO_SNAPSHOTS) {
            autoFiles.drop(MAX_ROLLING_AUTO_SNAPSHOTS).forEach { runCatching { it.delete() } }
        }
    }

    fun inspectVaultUri(context: Context, uri: Uri): RestorePreview {
        pruneExportCacheDir(context)
        val tempFile = File(getExportCacheDir(context), "inspect_${System.currentTimeMillis()}.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedOutputStream(FileOutputStream(tempFile)).use { out ->
                input.copyTo(out, bufferSize = 16 * 1024)
            }
        } ?: throw IllegalStateException("Unable to open selected backup file.")

        return inspectLocalFile(tempFile, sourceLabel = uri.lastPathSegment ?: "Picked Backup File")
    }

    fun inspectLocalFile(file: File, sourceLabel: String = file.name): RestorePreview {
        if (!file.exists() || file.length() == 0L) {
            throw IllegalStateException("Backup file is empty or unreadable.")
        }
        val isZip = isZipArchive(file)
        if (!isZip) {
            if (file.length() > MAX_ENTRY_UNCOMPRESSED_BYTES * 2) {
                throw IllegalStateException("Selected JSON backup exceeds safe size limit (64 MB).")
            }
            val rawJson = file.readText(Charsets.UTF_8)
            val trimmed = rawJson.trim()
            if (trimmed.startsWith("{")) {
                val parsedRoot = runCatching { JSONObject(trimmed) }.getOrNull()
                if (parsedRoot != null && WebVaultBridge.isWebVaultV5Root(parsedRoot)) {
                    val sectionsObj = parsedRoot.optJSONObject("sections")
                    if (sectionsObj != null && sectionsObj.has("vocabAndPresets")) {
                        return WebVaultBridge.inspectWebVaultJson(trimmed, parsedRoot, sourceLabel, file)
                    }
                    return WebVaultJsonAdapter.inspectWebVaultJson(file, trimmed, sourceLabel)
                }
            }
            val papers = ExamJsonHelper.bundleFromJson(rawJson)
            return RestorePreview(
                sourceLabel = sourceLabel,
                isLegacyJson = true,
                checksumVerified = true,
                checksumDetails = "Legacy ExamSet JSON (${papers.size} question sets detected)",
                manifest = BackupManifest(
                    schemaVersion = 2,
                    createdAt = file.lastModified(),
                    counts = BackupCounts(papersCount = papers.size)
                ),
                tempFile = file,
                legacyJsonContent = rawJson
            )
        }

        ZipFile(file).use { zip ->
            val manifestEntry = zip.getEntry(ENTRY_MANIFEST)
                ?: throw IllegalStateException("Invalid ExamSet vault: missing $ENTRY_MANIFEST")
            val manifestJson = readZipEntryTextBounded(zip, manifestEntry)
            val manifest = parseManifest(JSONObject(manifestJson))

            val requiredCoreEntries = buildList {
                add(ENTRY_FOLDERS)
                add(ENTRY_PAPERS)
                add(ENTRY_NOTES)
                add(ENTRY_ATTEMPTS)
                add(ENTRY_ANALYTICS)
                add(ENTRY_PREFERENCES)
                if (manifest.schemaVersion >= 5 || zip.getEntry(ENTRY_VOCABULARY) != null) {
                    add(ENTRY_VOCABULARY)
                }
            }

            var mismatchCount = 0
            val checkedEntries = mutableListOf<String>()
            for (coreEntry in requiredCoreEntries) {
                val entry = zip.getEntry(coreEntry)
                val expected = manifest.entryChecksumsSha256[coreEntry]
                if (entry == null || expected.isNullOrBlank()) {
                    mismatchCount++
                }
            }
            for ((entryName, expectedHash) in manifest.entryChecksumsSha256) {
                val entry = zip.getEntry(entryName)
                if (entry == null || expectedHash.isBlank()) {
                    mismatchCount++
                    continue
                }
                val actualHash = openBoundedZipEntryStream(zip, entry).use { computeStreamSha256(it) }
                if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                    mismatchCount++
                } else {
                    checkedEntries.add(entryName)
                }
            }

            val verified = mismatchCount == 0 && checkedEntries.size >= requiredCoreEntries.size
            val details = if (verified) {
                "Verified ${checkedEntries.size} checksummed entries (SHA-256 OK)"
            } else {
                "Warning: missing required archive entries or $mismatchCount checksum mismatch(es) detected!"
            }

            // Backfill counts for legacy v3/v4 vaults or partial manifests
            val vocabEntry = zip.getEntry(ENTRY_VOCABULARY)
            val prefsEntry = zip.getEntry(ENTRY_PREFERENCES)
            var detectedDecks = manifest.counts.vocabDecksCount
            var detectedWords = manifest.counts.vocabWordsCount
            var detectedPresets = manifest.counts.promptPresetsCount
            var detectedCountdowns = manifest.counts.countdownsCount
            var detectedTodos = manifest.counts.todosCount
            var detectedDrafts = manifest.counts.draftsCount
            var detectedBrainLab = manifest.counts.hasBrainLabData
            var detectedAppSettings = manifest.counts.hasAppSettings

            if (vocabEntry != null && detectedDecks == 0) {
                runCatching {
                    val vObj = JSONObject(readZipEntryTextBounded(zip, vocabEntry))
                    val decks = VocabularyDeck.deserializeList(vObj.optString("vocabularyDecksJson", ""))
                    detectedDecks = decks.size
                    detectedWords = decks.sumOf { it.entries.size }
                }
            }
            var detectedKnowledgeCourses = manifest.counts.knowledgeCoursesCount
            if (prefsEntry != null) {
                runCatching {
                    val pObj = JSONObject(readZipEntryTextBounded(zip, prefsEntry))
                    detectedAppSettings = true
                    if (detectedDecks == 0) {
                        val decks = VocabularyDeck.deserializeList(pObj.optString("vocabularyDecksJson", ""))
                        detectedDecks = decks.size
                        detectedWords = decks.sumOf { it.entries.size }
                    }
                    if (detectedPresets == 0) {
                        detectedPresets = PromptPreset.deserializeList(
                            pObj.optString("promptPresetsJson", "")
                        ).size
                    }
                    if (detectedCountdowns == 0) {
                        detectedCountdowns = com.example.model.ExamCountdownTarget.deserializeList(
                            pObj.optString("examCountdownsJson", "")
                        ).size
                    }
                    if (detectedTodos == 0) {
                        detectedTodos = com.example.model.StudyTodoItem.deserializeList(
                            pObj.optString("studyTodosJson", "")
                        ).size
                    }
                    if (detectedDrafts == 0) {
                        detectedDrafts = listOf(
                            "questionPromptDraft",
                            "knowledgePromptDraft",
                            "flashcardPromptDraft",
                            "vocabularyPromptDraft"
                        ).count { pObj.optString(it, "").isNotBlank() }
                    }
                    if (!detectedBrainLab) {
                        detectedBrainLab = pObj.optString("brainLabStatsJson", "").isNotBlank() ||
                            pObj.optString("brainLabSettingsJson", "").isNotBlank()
                    }
                    if (detectedKnowledgeCourses == 0) {
                        val cRaw = pObj.optString("knowledgeCoursesJson", "")
                        if (cRaw.isNotBlank()) {
                            detectedKnowledgeCourses = runCatching { JSONObject(cRaw).length() }.getOrDefault(0)
                        }
                    }
                }
            }
            val enrichedCounts = manifest.counts.copy(
                knowledgeCoursesCount = detectedKnowledgeCourses,
                vocabDecksCount = detectedDecks,
                vocabWordsCount = detectedWords,
                promptPresetsCount = detectedPresets,
                countdownsCount = detectedCountdowns,
                todosCount = detectedTodos,
                draftsCount = detectedDrafts,
                hasBrainLabData = detectedBrainLab,
                hasAppSettings = detectedAppSettings
            )

            return RestorePreview(
                sourceLabel = sourceLabel,
                isLegacyJson = false,
                checksumVerified = verified,
                checksumDetails = details,
                manifest = manifest.copy(counts = enrichedCounts),
                tempFile = file
            )
        }
    }

    suspend fun restoreFromPreview(
        context: Context,
        db: AppDatabase,
        appPrefs: ExamSetPreferences,
        themePrefs: ThemePreferences,
        preview: RestorePreview,
        mode: RestoreMode,
        mergeAppSettings: Boolean = true
    ): BackupCounts {
        if (preview.isWebVaultJson) {
            val rawJson = preview.legacyJsonContent ?: preview.tempFile?.readText(Charsets.UTF_8)
                ?: throw IllegalStateException("Web vault JSON content missing")
            val isBridgeV5 = runCatching {
                JSONObject(rawJson).optJSONObject("sections")?.has("vocabAndPresets") == true
            }.getOrDefault(false)
            return if (isBridgeV5) {
                WebVaultBridge.restoreWebVaultJson(
                    context = context,
                    db = db,
                    appPrefs = appPrefs,
                    themePrefs = themePrefs,
                    rawJson = rawJson,
                    mode = mode,
                    mergeAppSettings = mergeAppSettings
                )
            } else {
                WebVaultJsonAdapter.restoreFromWebVaultJson(
                    context = context,
                    db = db,
                    appPrefs = appPrefs,
                    themePrefs = themePrefs,
                    preview = preview,
                    mode = mode,
                    mergeAppSettings = mergeAppSettings
                )
            }
        }
        if (preview.isLegacyJson) {
            val rawJson = preview.legacyJsonContent ?: preview.tempFile?.readText(Charsets.UTF_8)
                ?: throw IllegalStateException("Legacy JSON content missing")
            if (mode == RestoreMode.FULL_REPLACE) {
                createLocalRollingSnapshot(context, db, appPrefs, themePrefs, providerTag = "LOCAL_AUTO", prefix = "pre_restore")
            }
            val imported = ExamJsonHelper.bundleFromJson(rawJson)
            val paperDao = db.paperDao()
            db.withTransaction {
                if (mode == RestoreMode.FULL_REPLACE) {
                    paperDao.deleteAllAttempts()
                    paperDao.deleteAllPapers()
                }
                imported.forEach { paper ->
                    val raw = McqParser.reconstructToRaw(paper)
                    val existing = if (mode == RestoreMode.SMART_MERGE) {
                        paperDao.findPaperByMetadata(paper.metadata.title, paper.metadata.subject, paper.metadata.setNumber)
                            ?: paper.metadata.bookletNumber.takeIf { it.isNotBlank() }?.let { paperDao.findPaperByBookletNumber(it) }
                    } else null
                    val entity = PaperEntity(
                        id = existing?.id ?: 0L,
                        title = paper.metadata.title,
                        subject = paper.metadata.subject.ifBlank { "General Knowledge" },
                        topic = paper.metadata.topic,
                        setNumber = paper.metadata.setNumber.ifBlank { "01" },
                        exam = paper.metadata.exam,
                        questionCount = paper.questions.size,
                        answerKeyCount = paper.quickAnswersCount,
                        explanationCount = paper.explanationsCount,
                        rawText = raw,
                        createdAt = if (paper.createdAt > 0) paper.createdAt else System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        folderId = paper.metadata.folderId ?: existing?.folderId,
                        rollNumber = paper.metadata.rollNumber.ifBlank { ExamNumberGenerator.generateRollNumber() },
                        bookletNumber = paper.metadata.bookletNumber.ifBlank { ExamNumberGenerator.generateBookletNumber() },
                        durationMinutes = paper.metadata.durationMinutes,
                        positiveMarks = paper.metadata.positiveMarks,
                        negativeMarks = paper.metadata.negativeMarks,
                        unattendedMarks = paper.metadata.unattendedMarks,
                        manualCategory = paper.metadata.manualCategory
                    )
                    val savedId = paperDao.insertPaper(entity)
                    if (paper.metadata.msqScoringPolicy != com.example.model.MsqScoringPolicy.ALL_OR_NOTHING) {
                        appPrefs.setPaperMsqPolicy(savedId, paper.metadata.msqScoringPolicy)
                    }
                }
            }
            return BackupCounts(papersCount = imported.size)
        }

        val zipFile = preview.tempFile ?: throw IllegalStateException("Vault file unavailable for restore")

        ZipFile(zipFile).use { zip ->
            val folders = zip.getEntry(ENTRY_FOLDERS)?.let { entry ->
                parseFoldersJson(readZipEntryTextBounded(zip, entry))
            }.orEmpty()

            val papers = mutableListOf<PaperEntity>()
            zip.getEntry(ENTRY_PAPERS)?.let { entry ->
                BufferedReader(InputStreamReader(openBoundedZipEntryStream(zip, entry), Charsets.UTF_8)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isNotBlank()) papers.add(deserializePaper(JSONObject(line)))
                    }
                }
            }

            val notes = mutableListOf<StudyNoteEntity>()
            zip.getEntry(ENTRY_NOTES)?.let { entry ->
                BufferedReader(InputStreamReader(openBoundedZipEntryStream(zip, entry), Charsets.UTF_8)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isNotBlank()) notes.add(deserializeStudyNote(JSONObject(line)))
                    }
                }
            }

            val attempts = mutableListOf<AttemptEntity>()
            zip.getEntry(ENTRY_ATTEMPTS)?.let { entry ->
                BufferedReader(InputStreamReader(openBoundedZipEntryStream(zip, entry), Charsets.UTF_8)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isNotBlank()) attempts.add(deserializeAttempt(JSONObject(line)))
                    }
                }
            }

            val analytics = zip.getEntry(ENTRY_ANALYTICS)?.let { entry ->
                parseAnalyticsJson(readZipEntryTextBounded(zip, entry))
            }.orEmpty()

            val vocabArchiveJson = zip.getEntry(ENTRY_VOCABULARY)?.let { entry ->
                readZipEntryTextBounded(zip, entry)
            }
            val vocabObj = vocabArchiveJson?.takeIf { it.isNotBlank() }?.let { raw ->
                try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    throw IllegalStateException("Corrupted $ENTRY_VOCABULARY in vault archive: ${e.message}", e)
                }
            }

            val prefsJson = zip.getEntry(ENTRY_PREFERENCES)?.let { entry ->
                readZipEntryTextBounded(zip, entry)
            }
            val prefsObj = prefsJson?.takeIf { it.isNotBlank() }?.let { raw ->
                try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    throw IllegalStateException("Corrupted $ENTRY_PREFERENCES in vault archive: ${e.message}", e)
                }
            }

            if (mode == RestoreMode.FULL_REPLACE) {
                createLocalRollingSnapshot(context, db, appPrefs, themePrefs, providerTag = "LOCAL_AUTO", prefix = "pre_restore")
            }

            val paperDao = db.paperDao()
            val noteDao = db.studyNoteDao()

            // Restore folders first (newest-wins updatedAt merge on Smart Merge)
            val mergedFolders = if (mode == RestoreMode.FULL_REPLACE) {
                folders
            } else {
                val current = appPrefs.getCustomFolders().associateBy { it.id }.toMutableMap()
                folders.forEach { incoming ->
                    val existing = current[incoming.id]
                    if (existing == null || incoming.updatedAt > existing.updatedAt) {
                        current[incoming.id] = incoming
                    }
                }
                current.values.toList()
            }
            appPrefs.saveCustomFolders(mergedFolders)

            val paperIdMap = mutableMapOf<Long, Long>()
            val restoredNoteIdByUid = mutableMapOf<String, Long>()

            db.withTransaction {
                if (mode == RestoreMode.FULL_REPLACE) {
                    paperDao.deleteAllAttempts()
                    paperDao.deleteAllPapers()
                    noteDao.deleteAllStudyNotes()
                    paperDao.deleteAllDailyAnalytics()
                }

                val existingAttemptsByKey = if (mode == RestoreMode.SMART_MERGE) {
                    paperDao.getAllAttemptsOnce().associateBy { "${it.paperId}_${it.createdAt}" }
                } else emptyMap()

                for (p in papers) {
                    val oldId = p.id
                    val existing = if (mode == RestoreMode.SMART_MERGE) {
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
                    if (oldId > 0L) paperIdMap[oldId] = finalId
                }

                val validFolderIds = mergedFolders.map { it.id }.toSet()
                for (n in notes) {
                    // [DEPRECATED 2026-11-24] Reason: Falling back to raw incoming linkedPaperId (paperIdMap[it] ?: it) mislinks notes to unrelated local papers sharing an auto-increment Room ID. Restore if: External paper ID stability is introduced across devices.
                    val remappedLinkedPaper = n.linkedPaperId?.let { paperIdMap[it] }
                    val cleanFolderId = n.folderId?.takeIf { it in validFolderIds }
                    val cleanUid = n.syncUid.ifBlank { UUID.randomUUID().toString() }
                    val existingNote = if (mode == RestoreMode.SMART_MERGE) {
                        noteDao.getStudyNoteBySyncUid(cleanUid)
                            ?: noteDao.getAllStudyNotesOnce().firstOrNull {
                                it.title == n.title && it.rawContent == n.rawContent
                            }
                    } else null

                    val finalNoteId = if (existingNote != null) {
                        val merged = mergeStudyNoteForRestore(
                            existing = existingNote,
                            incoming = n.copy(syncUid = cleanUid),
                            remappedLinkedPaper = remappedLinkedPaper,
                            cleanFolderId = cleanFolderId
                        )
                        if (merged != existingNote) {
                            noteDao.updateStudyNote(merged)
                        }
                        existingNote.id
                    } else {
                        noteDao.insertStudyNote(
                            n.copy(
                                id = 0L,
                                folderId = cleanFolderId,
                                linkedPaperId = remappedLinkedPaper,
                                syncUid = cleanUid
                            )
                        )
                    }
                    restoredNoteIdByUid[cleanUid] = finalNoteId
                }

                for (att in attempts) {
                    val mappedPaperId = paperIdMap[att.paperId] ?: continue
                    val dedupKey = "${mappedPaperId}_${att.createdAt}"
                    if (mode == RestoreMode.SMART_MERGE && existingAttemptsByKey.containsKey(dedupKey)) {
                        continue
                    }
                    paperDao.insertAttempt(att.copy(id = 0L, paperId = mappedPaperId))
                }

                for (day in analytics) {
                    val existingDay = if (mode == RestoreMode.SMART_MERGE) {
                        paperDao.getDailyAnalytics(day.dateString)
                    } else null
                    if (existingDay == null || day.totalTestTimeSeconds >= existingDay.totalTestTimeSeconds) {
                        paperDao.upsertDailyAnalytics(day)
                    }
                }
            }

            val overwriteAll = mode == RestoreMode.FULL_REPLACE
            if (prefsObj != null) {
                applyPreferencesJson(
                    obj = prefsObj,
                    appPrefs = appPrefs,
                    themePrefs = themePrefs,
                    overwriteAll = overwriteAll,
                    mergeAppSettings = mergeAppSettings,
                    restoredNoteIdByUid = restoredNoteIdByUid,
                    paperIdMap = paperIdMap,
                    skipVocabularyIfDedicatedEntry = vocabObj != null
                )
            }
            if (vocabObj != null) {
                applyVocabularyArchiveJson(vocabObj, appPrefs, overwriteAll, mergeAppSettings)
            }

            val currentDecks = appPrefs.getVocabularyDecks()
            val currentPresets = appPrefs.getPromptPresets()
            val currentCountdowns = appPrefs.getExamCountdowns()
            val currentTodos = appPrefs.getStudyTodos()
            val currentCoursesCount = appPrefs.getKnowledgeCoursesMap().size
            return BackupCounts(
                papersCount = papers.size,
                notesCount = notes.size,
                knowledgeCoursesCount = preview.manifest.counts.knowledgeCoursesCount.takeIf { it > 0 } ?: currentCoursesCount,
                foldersCount = folders.size,
                attemptsCount = attempts.size,
                analyticsDaysCount = analytics.size,
                vocabDecksCount = preview.manifest.counts.vocabDecksCount.takeIf { it > 0 } ?: currentDecks.size,
                vocabWordsCount = preview.manifest.counts.vocabWordsCount.takeIf { it > 0 } ?: currentDecks.sumOf { it.entries.size },
                promptPresetsCount = preview.manifest.counts.promptPresetsCount.takeIf { it > 0 } ?: currentPresets.size,
                countdownsCount = preview.manifest.counts.countdownsCount.takeIf { it > 0 } ?: currentCountdowns.size,
                todosCount = preview.manifest.counts.todosCount.takeIf { it > 0 } ?: currentTodos.size,
                draftsCount = preview.manifest.counts.draftsCount,
                hasBrainLabData = preview.manifest.counts.hasBrainLabData,
                hasAppSettings = preview.manifest.counts.hasAppSettings || prefsObj != null
            )
        }
    }

    private class BoundedInputStream(
        private val delegate: java.io.InputStream,
        private val entryName: String,
        private val maxBytes: Long = MAX_ENTRY_UNCOMPRESSED_BYTES
    ) : java.io.InputStream() {
        private var bytesRead = 0L

        private fun checkLimit(added: Int) {
            if (added > 0) {
                bytesRead += added
                if (bytesRead > maxBytes) {
                    throw IllegalStateException(
                        "Vault entry '$entryName' exceeds the maximum allowed uncompressed size (${maxBytes / (1024 * 1024)} MB)."
                    )
                }
            }
        }

        override fun read(): Int {
            val b = delegate.read()
            if (b != -1) checkLimit(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = delegate.read(b, off, len)
            checkLimit(count)
            return count
        }

        override fun close() {
            delegate.close()
        }
    }

    private fun openBoundedZipEntryStream(
        zip: ZipFile,
        entry: ZipEntry,
        maxBytes: Long = MAX_ENTRY_UNCOMPRESSED_BYTES
    ): java.io.InputStream {
        if (entry.size > maxBytes) {
            throw IllegalStateException(
                "Vault entry '${entry.name}' exceeds the maximum allowed uncompressed size (${maxBytes / (1024 * 1024)} MB)."
            )
        }
        return BoundedInputStream(zip.getInputStream(entry), entry.name, maxBytes)
    }

    private fun readZipEntryTextBounded(
        zip: ZipFile,
        entry: ZipEntry,
        maxBytes: Long = MAX_ENTRY_UNCOMPRESSED_BYTES
    ): String {
        return openBoundedZipEntryStream(zip, entry, maxBytes).use { bounded ->
            bounded.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    private fun isZipArchive(file: File): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(2)
                fis.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun writeStringEntryWithSha256(zos: ZipOutputStream, entryName: String, content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(bytes)
        zos.closeEntry()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.toHexString()
    }

    private fun writeNdjsonEntryWithSha256(
        zos: ZipOutputStream,
        entryName: String,
        producer: (writeLine: (String) -> Unit) -> Unit
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        zos.putNextEntry(ZipEntry(entryName))
        val newLine = "\n".toByteArray(Charsets.UTF_8)
        producer { line ->
            val bytes = line.toByteArray(Charsets.UTF_8)
            zos.write(bytes)
            zos.write(newLine)
            digest.update(bytes)
            digest.update(newLine)
        }
        zos.closeEntry()
        return digest.digest().toHexString()
    }

    fun computeFileSha256(file: File): String {
        return BufferedInputStream(FileInputStream(file)).use { computeStreamSha256(it) }
    }

    private fun computeStreamSha256(input: java.io.InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        DigestInputStream(input, md).use { dis ->
            while (dis.read(buffer) != -1) {
                // drain stream through digest
            }
        }
        return md.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    internal fun computeSha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

    // --- JSON Serialization Helpers ---

    private fun serializeManifest(m: BackupManifest): JSONObject {
        return JSONObject().apply {
            put("schemaVersion", m.schemaVersion)
            put("appVersion", m.appVersion)
            put("createdAt", m.createdAt)
            put("deviceName", m.deviceName)
            put("counts", JSONObject().apply {
                put("papersCount", m.counts.papersCount)
                put("notesCount", m.counts.notesCount)
                put("knowledgeCoursesCount", m.counts.knowledgeCoursesCount)
                put("foldersCount", m.counts.foldersCount)
                put("attemptsCount", m.counts.attemptsCount)
                put("analyticsDaysCount", m.counts.analyticsDaysCount)
                put("vocabDecksCount", m.counts.vocabDecksCount)
                put("vocabWordsCount", m.counts.vocabWordsCount)
                put("promptPresetsCount", m.counts.promptPresetsCount)
                put("countdownsCount", m.counts.countdownsCount)
                put("todosCount", m.counts.todosCount)
                put("draftsCount", m.counts.draftsCount)
                put("hasBrainLabData", m.counts.hasBrainLabData)
                put("hasAppSettings", m.counts.hasAppSettings)
            })
            val cs = JSONObject()
            m.entryChecksumsSha256.forEach { (k, v) -> cs.put(k, v) }
            put("entryChecksumsSha256", cs)
        }
    }

    private fun parseManifest(obj: JSONObject): BackupManifest {
        val c = obj.optJSONObject("counts") ?: JSONObject()
        val csObj = obj.optJSONObject("entryChecksumsSha256") ?: JSONObject()
        val checksums = buildMap {
            csObj.keys().forEach { key -> put(key, csObj.optString(key, "")) }
        }
        return BackupManifest(
            schemaVersion = obj.optInt("schemaVersion", 3),
            appVersion = obj.optString("appVersion", "1.0"),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            deviceName = obj.optString("deviceName", ""),
            counts = BackupCounts(
                papersCount = c.optInt("papersCount", 0),
                notesCount = c.optInt("notesCount", 0),
                knowledgeCoursesCount = c.optInt("knowledgeCoursesCount", 0),
                foldersCount = c.optInt("foldersCount", 0),
                attemptsCount = c.optInt("attemptsCount", 0),
                analyticsDaysCount = c.optInt("analyticsDaysCount", 0),
                vocabDecksCount = c.optInt("vocabDecksCount", 0),
                vocabWordsCount = c.optInt("vocabWordsCount", 0),
                promptPresetsCount = c.optInt("promptPresetsCount", 0),
                countdownsCount = c.optInt("countdownsCount", 0),
                todosCount = c.optInt("todosCount", 0),
                draftsCount = c.optInt("draftsCount", 0),
                hasBrainLabData = c.optBoolean("hasBrainLabData", false),
                hasAppSettings = c.optBoolean("hasAppSettings", false)
            ),
            entryChecksumsSha256 = checksums
        )
    }

    private fun serializeFoldersJson(folders: List<FolderItem>): String {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("color", f.color)
                if (f.parentId != null) put("parentId", f.parentId) else put("parentId", JSONObject.NULL)
                put("createdAt", f.createdAt)
                put("updatedAt", f.updatedAt)
            })
        }
        return arr.toString()
    }

    private fun parseFoldersJson(json: String): List<FolderItem> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val createdAt = o.optLong("createdAt", System.currentTimeMillis())
                add(
                    FolderItem(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        color = o.optString("color", "#0284C7"),
                        parentId = if (o.has("parentId") && !o.isNull("parentId")) o.optString("parentId").ifBlank { null } else null,
                        createdAt = createdAt,
                        updatedAt = o.optLong("updatedAt", createdAt)
                    )
                )
            }
        }
    }

    private fun serializePaper(p: PaperEntity): JSONObject {
        return JSONObject().apply {
            put("id", p.id)
            put("title", p.title)
            put("subject", p.subject)
            put("topic", p.topic)
            put("setNumber", p.setNumber)
            put("exam", p.exam)
            put("questionCount", p.questionCount)
            put("answerKeyCount", p.answerKeyCount)
            put("explanationCount", p.explanationCount)
            put("rawText", p.rawText)
            put("createdAt", p.createdAt)
            put("updatedAt", p.updatedAt)
            if (p.folderId != null) put("folderId", p.folderId) else put("folderId", JSONObject.NULL)
            put("rollNumber", p.rollNumber)
            put("bookletNumber", p.bookletNumber)
            put("durationMinutes", p.durationMinutes)
            put("positiveMarks", p.positiveMarks)
            put("negativeMarks", p.negativeMarks)
            put("unattendedMarks", p.unattendedMarks)
            put("manualCategory", p.manualCategory)
        }
    }

    private fun deserializePaper(o: JSONObject): PaperEntity {
        return PaperEntity(
            id = o.optLong("id", 0L),
            title = o.optString("title", "Question Set"),
            subject = o.optString("subject", "General"),
            topic = o.optString("topic", ""),
            setNumber = o.optString("setNumber", "01"),
            exam = o.optString("exam", ""),
            questionCount = o.optInt("questionCount", 0),
            answerKeyCount = o.optInt("answerKeyCount", 0),
            explanationCount = o.optInt("explanationCount", 0),
            rawText = o.optString("rawText", ""),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            folderId = if (o.has("folderId") && !o.isNull("folderId")) o.optString("folderId").ifBlank { null } else null,
            rollNumber = o.optString("rollNumber", ""),
            bookletNumber = o.optString("bookletNumber", ""),
            durationMinutes = o.optInt("durationMinutes", 60),
            positiveMarks = o.optDouble("positiveMarks", 4.0),
            negativeMarks = o.optDouble("negativeMarks", 1.0),
            unattendedMarks = o.optDouble("unattendedMarks", 0.0),
            manualCategory = o.optString("manualCategory", "")
        )
    }

    private fun serializeStudyNote(n: StudyNoteEntity): JSONObject {
        return JSONObject().apply {
            put("id", n.id)
            if (n.folderId != null) put("folderId", n.folderId) else put("folderId", JSONObject.NULL)
            put("title", n.title)
            put("subject", n.subject)
            put("course", n.course)
            put("sourceAi", n.sourceAi)
            put("rawContent", n.rawContent)
            put("bookmarkedLinesJson", n.bookmarkedLinesJson)
            put("createdAt", n.createdAt)
            put("updatedAt", n.updatedAt)
            put("collectionType", n.collectionType)
            put("customLabel", n.customLabel)
            if (n.linkedPaperId != null) put("linkedPaperId", n.linkedPaperId) else put("linkedPaperId", JSONObject.NULL)
            put("formatRuleJson", n.formatRuleJson)
            put("masteredBlocksJson", n.masteredBlocksJson)
            put("syncUid", n.syncUid)
        }
    }

    private fun deserializeStudyNote(o: JSONObject): StudyNoteEntity {
        return StudyNoteEntity(
            id = o.optLong("id", 0L),
            folderId = if (o.has("folderId") && !o.isNull("folderId")) o.optString("folderId").ifBlank { null } else null,
            title = o.optString("title", "Knowledge Set"),
            subject = o.optString("subject", ""),
            course = o.optString("course", ""),
            sourceAi = o.optString("sourceAi", "Knowledge Set"),
            rawContent = o.optString("rawContent", ""),
            bookmarkedLinesJson = o.optString("bookmarkedLinesJson", "[]"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            collectionType = o.optString("collectionType", "ONE_LINERS"),
            customLabel = o.optString("customLabel", ""),
            linkedPaperId = if (o.has("linkedPaperId") && !o.isNull("linkedPaperId")) o.optLong("linkedPaperId") else null,
            formatRuleJson = o.optString("formatRuleJson", "{}"),
            masteredBlocksJson = o.optString("masteredBlocksJson", "[]"),
            syncUid = o.optString("syncUid", "").ifBlank { UUID.randomUUID().toString() }
        )
    }

    private fun serializeAttempt(a: AttemptEntity): JSONObject {
        return JSONObject().apply {
            put("id", a.id)
            put("paperId", a.paperId)
            put("answersJson", a.answersJson)
            put("isSubmitted", a.isSubmitted)
            put("score", a.score)
            put("correctCount", a.correctCount)
            put("incorrectCount", a.incorrectCount)
            put("unansweredCount", a.unansweredCount)
            put("totalQuestions", a.totalQuestions)
            put("timeSpentSeconds", a.timeSpentSeconds)
            put("createdAt", a.createdAt)
            if (a.submittedAt != null) put("submittedAt", a.submittedAt) else put("submittedAt", JSONObject.NULL)
        }
    }

    private fun deserializeAttempt(o: JSONObject): AttemptEntity {
        return AttemptEntity(
            id = o.optLong("id", 0L),
            paperId = o.optLong("paperId", 0L),
            answersJson = o.optString("answersJson", "{}"),
            isSubmitted = o.optBoolean("isSubmitted", false),
            score = o.optDouble("score", 0.0),
            correctCount = o.optInt("correctCount", 0),
            incorrectCount = o.optInt("incorrectCount", 0),
            unansweredCount = o.optInt("unansweredCount", 0),
            totalQuestions = o.optInt("totalQuestions", 0),
            timeSpentSeconds = o.optLong("timeSpentSeconds", 0L),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            submittedAt = if (o.has("submittedAt") && !o.isNull("submittedAt")) o.optLong("submittedAt") else null
        )
    }

    private fun serializeAnalyticsJson(days: List<DailyStudyAnalyticsEntity>): String {
        val arr = JSONArray()
        days.forEach { d ->
            arr.put(JSONObject().apply {
                put("dateString", d.dateString)
                put("totalTestTimeSeconds", d.totalTestTimeSeconds)
                put("testsCompletedCount", d.testsCompletedCount)
                put("testsStartedCount", d.testsStartedCount)
                put("questionsAttemptedCount", d.questionsAttemptedCount)
                put("correctAnswersCount", d.correctAnswersCount)
                put("dailyGoalSeconds", d.dailyGoalSeconds)
                put("lastUpdatedTimestamp", d.lastUpdatedTimestamp)
            })
        }
        return arr.toString()
    }

    private fun parseAnalyticsJson(json: String): List<DailyStudyAnalyticsEntity> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    DailyStudyAnalyticsEntity(
                        dateString = o.optString("dateString"),
                        totalTestTimeSeconds = o.optLong("totalTestTimeSeconds", 0L),
                        testsCompletedCount = o.optInt("testsCompletedCount", 0),
                        testsStartedCount = o.optInt("testsStartedCount", 0),
                        questionsAttemptedCount = o.optInt("questionsAttemptedCount", 0),
                        correctAnswersCount = o.optInt("correctAnswersCount", 0),
                        dailyGoalSeconds = o.optLong("dailyGoalSeconds", 1800L),
                        lastUpdatedTimestamp = o.optLong("lastUpdatedTimestamp", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun serializeVocabularyArchiveJson(
        appPrefs: ExamSetPreferences,
        decks: List<VocabularyDeck>
    ): String {
        return JSONObject().apply {
            put("schemaVersion", 2)
            put("vocabDecksCount", decks.size)
            put("vocabWordsCount", decks.sumOf { it.entries.size })
            put("vocabularyDecksJson", VocabularyDeck.serializeList(decks))
            put("vocabularySrsJson", appPrefs.getVocabularySrsRawJson())
            put("preferredEnglishVariant", appPrefs.getPreferredEnglishVariant())
            put("languageWordOfTheDayStatesJson", appPrefs.getLanguageWordOfTheDayStatesJson() ?: "{}")
            appPrefs.getWordOfTheDayCacheJson()?.let { put("wordOfTheDayCacheJson", it) }
            put("vocabReminderEnabled", appPrefs.isVocabularyReminderEnabled())
            put("headwordInboxJson", appPrefs.getHeadwordInboxRawJson())
            put("dismissedWordKeysJson", appPrefs.getDismissedWordKeysRawJson())
        }.toString()
    }

    private fun applyVocabularyArchiveJson(
        obj: JSONObject,
        appPrefs: ExamSetPreferences,
        overwriteAll: Boolean,
        mergeAppSettings: Boolean = true
    ) {
        applyVocabularyFields(obj, appPrefs, overwriteAll, mergeAppSettings)
    }

    private fun serializePreferencesJson(
        appPrefs: ExamSetPreferences,
        themePrefs: ThemePreferences,
        noteUidById: Map<Long, String>
    ): String {
        val allFlashcardSrs = appPrefs.getAllFlashcardSrsMaps()
        val srsByUidObj = JSONObject()
        allFlashcardSrs.forEach { (noteId, cardMap) ->
            val uid = noteUidById[noteId]
            if (!uid.isNullOrBlank() && cardMap.isNotEmpty()) {
                val arr = JSONArray()
                cardMap.values.forEach { st ->
                    arr.put(JSONObject().apply {
                        put("blockIndex", st.blockIndex)
                        put("repetitions", st.repetitions)
                        put("easeFactor", st.easeFactor.toDouble())
                        put("intervalDays", st.intervalDays)
                        put("dueAtMillis", st.dueAtMillis)
                        put("lastReviewedAtMillis", st.lastReviewedAtMillis)
                        put("lapses", st.lapses)
                        put("lastRating", st.lastRating?.name ?: "")
                        put("reviewCount", st.reviewCount)
                        put("correctCount", st.correctCount)
                    })
                }
                srsByUidObj.put(uid, arr)
            }
        }

        return JSONObject().apply {
            put("dailyGoalMinutes", appPrefs.getDailyGoalMinutes())
            put("fontScaleMode", appPrefs.getFontScaleMode().name)
            put("fontSizeScale", appPrefs.getFontSizeScale().toDouble())
            put("cbtFontScaleStep", appPrefs.getCbtFontScaleStep())
            put("shuffleOptions", appPrefs.getShuffleOptions())
            put("lastActiveFolderId", appPrefs.getLastActiveFolderId())
            put("themeMode", themePrefs.getThemeMode().name)
            put("colorSchemePreset", themePrefs.getColorSchemePreset().name)
            put("promptPresetsJson", appPrefs.getPromptPresetsRawJson())
            put("activePromptPresetIdsJson", appPrefs.getActivePromptPresetIdsRawJson())
            put("dismissedFormatTipsJson", appPrefs.getDismissedFormatTipsRawJson())
            appPrefs.getQuestionPromptDraft()?.let { put("questionPromptDraft", it.toJson().toString()) }
            appPrefs.getKnowledgePromptDraft()?.let { put("knowledgePromptDraft", it.toJson().toString()) }
            appPrefs.getFlashcardPromptDraft()?.let { put("flashcardPromptDraft", it.toJson().toString()) }
            appPrefs.getVocabularyPromptDraft()?.let { put("vocabularyPromptDraft", it.toJson().toString()) }
            put("flashcardSrsByNoteUid", srsByUidObj)
            // Keep vocabulary keys in preferences.json too for seamless downgrade compatibility
            put("vocabularyDecksJson", appPrefs.getVocabularyDecksRawJson())
            put("vocabularySrsJson", appPrefs.getVocabularySrsRawJson())
            put("preferredEnglishVariant", appPrefs.getPreferredEnglishVariant())
            put("languageWordOfTheDayStatesJson", appPrefs.getLanguageWordOfTheDayStatesJson() ?: "{}")
            appPrefs.getWordOfTheDayCacheJson()?.let { put("wordOfTheDayCacheJson", it) }
            put("vocabReminderEnabled", appPrefs.isVocabularyReminderEnabled())
            put("headwordInboxJson", appPrefs.getHeadwordInboxRawJson())
            put("dismissedWordKeysJson", appPrefs.getDismissedWordKeysRawJson())
            put("examCountdownsJson", appPrefs.getExamCountdownsRawJson())
            put("studyTodosJson", appPrefs.getStudyTodosRawJson())
            put("pomodoroPreset", appPrefs.getPomodoroPresetName())
            put("pomodoroCustomFocusMinutes", appPrefs.getPomodoroCustomFocusMinutes())
            put("pomodoroCustomBreakMinutes", appPrefs.getPomodoroCustomBreakMinutes())
            put("preferredDndMode", appPrefs.getPreferredDndMode().name)
            put("defaultMsqScoringPolicy", appPrefs.getDefaultMsqScoringPolicy().code)
            put("paperMsqPoliciesJson", appPrefs.getPaperMsqPoliciesRawJson())
            appPrefs.getBrainLabStatsRawJson().takeIf { it.isNotBlank() }?.let { put("brainLabStatsJson", it) }
            appPrefs.getBrainLabSettingsRawJson().takeIf { it.isNotBlank() }?.let { put("brainLabSettingsJson", it) }
            val coursesJson = JSONObject().apply {
                appPrefs.getKnowledgeCoursesMap().forEach { (course, subjects) ->
                    val arr = JSONArray()
                    subjects.forEach { arr.put(it) }
                    put(course, arr)
                }
            }
            put("knowledgeCoursesJson", coursesJson.toString())
        }.toString()
    }

    private fun applyPreferencesJson(
        obj: JSONObject,
        appPrefs: ExamSetPreferences,
        themePrefs: ThemePreferences,
        overwriteAll: Boolean,
        mergeAppSettings: Boolean = true,
        restoredNoteIdByUid: Map<String, Long> = emptyMap(),
        paperIdMap: Map<Long, Long> = emptyMap(),
        skipVocabularyIfDedicatedEntry: Boolean = false
    ) {
        val syncSettings = overwriteAll || mergeAppSettings

        if (syncSettings && obj.has("dailyGoalMinutes")) {
            appPrefs.setDailyGoalMinutes(obj.optInt("dailyGoalMinutes", 30))
        }
        if (syncSettings) {
            obj.optString("fontScaleMode", "").takeIf { it.isNotBlank() }?.let {
                appPrefs.setFontScaleMode(AppFontScaleMode.fromString(it))
            }
            if (obj.has("fontSizeScale")) {
                val scale = obj.optDouble("fontSizeScale", -1.0)
                if (scale in 0.85..1.6) {
                    appPrefs.setFontSizeScale(scale.toFloat())
                }
            }
            if (obj.has("cbtFontScaleStep")) {
                appPrefs.setCbtFontScaleStep(obj.optInt("cbtFontScaleStep", 0).coerceIn(-1, 1))
            }
            if (obj.has("shuffleOptions")) {
                appPrefs.setShuffleOptions(obj.optBoolean("shuffleOptions", false))
            }
            obj.optString("themeMode", "").takeIf { it.isNotBlank() }?.let {
                themePrefs.setThemeMode(ThemeMode.fromString(it))
            }
            obj.optString("colorSchemePreset", "").takeIf { it.isNotBlank() }?.let {
                themePrefs.setColorSchemePreset(ColorSchemePreset.fromString(it))
            }
            obj.optString("questionPromptDraft", "").takeIf { it.isNotBlank() }?.let { raw ->
                runCatching {
                    appPrefs.saveQuestionPromptDraft(com.example.model.QuestionPromptConfig.fromJson(JSONObject(raw)))
                }
            }
            obj.optString("knowledgePromptDraft", "").takeIf { it.isNotBlank() }?.let { raw ->
                runCatching {
                    appPrefs.saveKnowledgePromptDraft(com.example.model.KnowledgePromptConfig.fromJson(JSONObject(raw)))
                }
            }
            obj.optString("flashcardPromptDraft", "").takeIf { it.isNotBlank() }?.let { raw ->
                runCatching {
                    appPrefs.saveFlashcardPromptDraft(com.example.model.FlashcardPromptConfig.fromJson(JSONObject(raw)))
                }
            }
            obj.optString("vocabularyPromptDraft", "").takeIf { it.isNotBlank() }?.let { raw ->
                runCatching {
                    appPrefs.saveVocabularyPromptDraft(com.example.model.VocabularyPromptConfig.fromJson(JSONObject(raw)))
                }
            }
        }

        val incomingPresetsRaw = obj.optString("promptPresetsJson", "")
        if (incomingPresetsRaw.isNotBlank()) {
            val incomingPresets = PromptPreset.deserializeList(incomingPresetsRaw)
            if (overwriteAll) {
                appPrefs.savePromptPresets(incomingPresets)
            } else if (incomingPresets.isNotEmpty()) {
                val existing = appPrefs.getPromptPresets().associateBy { it.id }.toMutableMap()
                incomingPresets.forEach { preset ->
                    val current = existing[preset.id]
                    if (current == null || preset.updatedAt > current.updatedAt) {
                        existing[preset.id] = preset
                    }
                }
                appPrefs.savePromptPresets(existing.values.toList())
            }
        }

        // Restore active prompt preset selections per mode, validated against restored preset IDs
        val validPresetIdsByMode = appPrefs.getPromptPresets().groupBy { it.mode }.mapValues { (_, list) ->
            list.map { it.id }.toSet()
        }
        val activeIdsRaw = obj.optString("activePromptPresetIdsJson", "")
        if (syncSettings && activeIdsRaw.isNotBlank()) {
            appPrefs.saveActivePromptPresetIdsFromJson(activeIdsRaw, overwriteAll = overwriteAll || mergeAppSettings)
        } else if (overwriteAll) {
            PromptPresetMode.entries.forEach { appPrefs.setActivePromptPresetId(it, null) }
        }
        PromptPresetMode.entries.forEach { mode ->
            val currentId = appPrefs.getActivePromptPresetId(mode)
            if (!currentId.isNullOrBlank() && currentId !in validPresetIdsByMode[mode].orEmpty()) {
                appPrefs.setActivePromptPresetId(mode, null)
            }
        }

        // Restore dismissed format tips (replace in Clean Replace, union in Smart Merge)
        val dismissedTipsRaw = obj.optString("dismissedFormatTipsJson", "")
        if (dismissedTipsRaw.isNotBlank()) {
            val incomingTips = runCatching {
                val arr = JSONArray(dismissedTipsRaw)
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }.toSet()
            }.getOrDefault(emptySet())
            if (overwriteAll) {
                appPrefs.saveDismissedFormatTips(incomingTips)
            } else if (syncSettings && incomingTips.isNotEmpty()) {
                appPrefs.saveDismissedFormatTips(appPrefs.getDismissedFormatTips() + incomingTips)
            }
        } else if (overwriteAll) {
            appPrefs.saveDismissedFormatTips(emptySet())
        }

        // Restore last active folder if valid
        if (syncSettings) {
            val incomingFolderId = obj.optString("lastActiveFolderId", "").trim()
            if (incomingFolderId == "all" || appPrefs.getCustomFolders().any { it.id == incomingFolderId }) {
                appPrefs.setLastActiveFolderId(incomingFolderId)
            } else if (overwriteAll) {
                appPrefs.setLastActiveFolderId("all")
            }
        }

        // Restore registered Knowledge Courses -> Subjects hierarchy map
        val incomingCoursesRaw = obj.optString("knowledgeCoursesJson", "")
        if (incomingCoursesRaw.isNotBlank()) {
            val parsedObj = runCatching { JSONObject(incomingCoursesRaw) }.getOrNull()
            if (parsedObj != null) {
                val incomingMap = parseKnowledgeCoursesMapJson(incomingCoursesRaw)
                if (overwriteAll) {
                    appPrefs.saveKnowledgeCoursesMap(incomingMap)
                } else if (incomingMap.isNotEmpty()) {
                    appPrefs.saveKnowledgeCoursesMap(
                        mergeKnowledgeCoursesMaps(appPrefs.getKnowledgeCoursesMap(), incomingMap)
                    )
                }
            }
        } else if (overwriteAll) {
            appPrefs.saveKnowledgeCoursesMap(emptyMap())
        }

        // Restore Knowledge Set Flashcard SM-2 SRS mapped by syncUid -> local Room noteId
        if (overwriteAll) {
            appPrefs.getAllFlashcardSrsMaps().keys.forEach { oldNoteId ->
                appPrefs.clearFlashcardSrsMap(oldNoteId)
            }
        }
        val srsByUidObj = obj.optJSONObject("flashcardSrsByNoteUid")
        if (srsByUidObj != null && restoredNoteIdByUid.isNotEmpty()) {
            val keys = srsByUidObj.keys()
            while (keys.hasNext()) {
                val uid = keys.next()
                val targetNoteId = restoredNoteIdByUid[uid] ?: continue
                val arr = srsByUidObj.optJSONArray(uid) ?: continue
                val incomingMap = mutableMapOf<Int, FlashcardSrsState>()
                for (i in 0 until arr.length()) {
                    val stObj = arr.optJSONObject(i) ?: continue
                    val idx = stObj.optInt("blockIndex", -1)
                    if (idx < 0) continue
                    val ratingName = stObj.optString("lastRating", "")
                    val rating = runCatching {
                        if (ratingName.isNotBlank()) SrsRating.valueOf(ratingName) else null
                    }.getOrNull()
                    incomingMap[idx] = FlashcardSrsState(
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
                if (incomingMap.isNotEmpty()) {
                    if (overwriteAll) {
                        appPrefs.saveFlashcardSrsMap(targetNoteId, incomingMap)
                    } else {
                        val existingMap = appPrefs.getFlashcardSrsMap(targetNoteId).toMutableMap()
                        incomingMap.forEach { (idx, incomingSt) ->
                            val curSt = existingMap[idx]
                            if (curSt == null || isIncomingSrsNewer(curSt, incomingSt)) {
                                existingMap[idx] = incomingSt
                            }
                        }
                        appPrefs.saveFlashcardSrsMap(targetNoteId, existingMap)
                    }
                }
            }
        }

        if (!skipVocabularyIfDedicatedEntry) {
            applyVocabularyFields(obj, appPrefs, overwriteAll, mergeAppSettings)
        }

        // Restore Exam Countdowns & Study Planner To-Dos (newest-wins updatedAt merge)
        val incomingCountdownsRaw = obj.optString("examCountdownsJson", "")
        if (incomingCountdownsRaw.isNotBlank()) {
            val incomingCountdowns = com.example.model.ExamCountdownTarget.deserializeList(incomingCountdownsRaw)
            if (overwriteAll) {
                appPrefs.saveExamCountdowns(incomingCountdowns)
            } else if (incomingCountdowns.isNotEmpty()) {
                val merged = appPrefs.getExamCountdowns().associateBy { it.id }.toMutableMap()
                incomingCountdowns.forEach { target ->
                    val existing = merged[target.id]
                    if (existing == null || target.updatedAt > existing.updatedAt) {
                        merged[target.id] = target
                    }
                }
                appPrefs.saveExamCountdowns(merged.values.sortedBy { it.targetDateStr })
            }
        }

        val incomingTodosRaw = obj.optString("studyTodosJson", "")
        if (incomingTodosRaw.isNotBlank()) {
            val incomingTodos = com.example.model.StudyTodoItem.deserializeList(incomingTodosRaw)
            if (overwriteAll) {
                appPrefs.saveStudyTodos(incomingTodos)
            } else if (incomingTodos.isNotEmpty()) {
                val merged = appPrefs.getStudyTodos().associateBy { it.id }.toMutableMap()
                incomingTodos.forEach { todo ->
                    val existing = merged[todo.id]
                    if (existing == null ||
                        todo.updatedAt > existing.updatedAt ||
                        (todo.isCompleted && !existing.isCompleted) ||
                        todo.completedPomodoros > existing.completedPomodoros
                    ) {
                        merged[todo.id] = todo
                    }
                }
                appPrefs.saveStudyTodos(merged.values.sortedByDescending { it.createdAt })
            }
        }

        if (syncSettings) {
            obj.optString("pomodoroPreset", "").takeIf { it.isNotBlank() }?.let {
                appPrefs.setPomodoroPresetName(com.example.model.PomodoroPreset.fromString(it).name)
            }
            if (obj.has("pomodoroCustomFocusMinutes")) {
                appPrefs.setPomodoroCustomFocusMinutes(obj.optInt("pomodoroCustomFocusMinutes", 30))
            }
            if (obj.has("pomodoroCustomBreakMinutes")) {
                appPrefs.setPomodoroCustomBreakMinutes(obj.optInt("pomodoroCustomBreakMinutes", 5))
            }
            obj.optString("preferredDndMode", "").takeIf { it.isNotBlank() }?.let {
                appPrefs.setPreferredDndMode(com.example.model.PomodoroDndMode.fromString(it))
            }
            obj.optString("defaultMsqScoringPolicy", "").takeIf { it.isNotBlank() }?.let {
                appPrefs.setDefaultMsqScoringPolicy(com.example.model.MsqScoringPolicy.fromCode(it))
            }
        }

        val incomingMsqPoliciesRaw = obj.optString("paperMsqPoliciesJson", "")
        if (incomingMsqPoliciesRaw.isNotBlank()) {
            runCatching {
                val policyObj = JSONObject(incomingMsqPoliciesRaw)
                val targetMap = if (overwriteAll) {
                    mutableMapOf()
                } else {
                    appPrefs.getAllPaperMsqPolicies().toMutableMap()
                }
                policyObj.keys().forEach { oldKey ->
                    val oldPaperId = oldKey.toLongOrNull() ?: return@forEach
                    val newPaperId = paperIdMap[oldPaperId] ?: oldPaperId
                    if (newPaperId > 0L) {
                        val policy = com.example.model.MsqScoringPolicy.fromCode(policyObj.optString(oldKey))
                        if (overwriteAll || !targetMap.containsKey(newPaperId)) {
                            targetMap[newPaperId] = policy
                        }
                    }
                }
                appPrefs.savePaperMsqPolicies(targetMap)
            }
        }

        val incomingBrainStatsRaw = obj.optString("brainLabStatsJson", "")
        if (incomingBrainStatsRaw.isNotBlank()) {
            runCatching {
                val incomingStats = com.example.model.BrainLabStats.fromJson(JSONObject(incomingBrainStatsRaw))
                if (overwriteAll) {
                    appPrefs.saveBrainLabStats(incomingStats)
                } else {
                    val merged = com.example.model.BrainLabStats.mergeForRestore(
                        existing = appPrefs.getBrainLabStats(),
                        incoming = incomingStats
                    )
                    appPrefs.saveBrainLabStats(merged)
                }
            }
        } else if (overwriteAll) {
            appPrefs.saveBrainLabStats(com.example.model.BrainLabStats())
        }

        val incomingBrainSettingsRaw = obj.optString("brainLabSettingsJson", "")
        if (syncSettings && incomingBrainSettingsRaw.isNotBlank()) {
            runCatching {
                appPrefs.saveBrainLabSettings(com.example.model.BrainLabSettings.fromJson(JSONObject(incomingBrainSettingsRaw)))
            }
        }
    }

    /**
     * Exports all Prompt Presets, active preset selections per mode, and all 4 AI prompt builder drafts
     * as a standalone shareable `.json` pack.
     */
    fun exportPromptPresetsPackJson(appPrefs: ExamSetPreferences): String {
        val presets = appPrefs.getPromptPresets()
        val draftsObj = JSONObject().apply {
            appPrefs.getQuestionPromptDraft()?.let { put("questionPromptDraft", it.toJson()) }
            appPrefs.getKnowledgePromptDraft()?.let { put("knowledgePromptDraft", it.toJson()) }
            appPrefs.getFlashcardPromptDraft()?.let { put("flashcardPromptDraft", it.toJson()) }
            appPrefs.getVocabularyPromptDraft()?.let { put("vocabularyPromptDraft", it.toJson()) }
        }
        return JSONObject().apply {
            put("packType", "examset_prompt_presets_pack")
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put("presetsCount", presets.size)
            put("presets", JSONArray(PromptPreset.serializeList(presets)))
            put("activePresetIds", JSONObject(appPrefs.getActivePromptPresetIdsRawJson()))
            put("drafts", draftsObj)
        }.toString(2)
    }

    /**
     * Imports a standalone Prompt Presets Pack `.json` (supports envelope format or raw PromptPreset JSONArray)
     * using timestamp-aware Smart Merge (`updatedAt` newest-wins), restoring active selections & drafts.
     */
    fun importPromptPresetsPackJson(
        rawJson: String,
        appPrefs: ExamSetPreferences,
        overwriteAll: Boolean = false
    ): PromptPresetsImportSummary {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Selected preset pack file is empty.")
        }

        val incomingPresets: List<PromptPreset>
        var activeIdsJson: String? = null
        var draftsObj: JSONObject? = null

        if (trimmed.startsWith("[")) {
            incomingPresets = PromptPreset.deserializeList(trimmed)
        } else {
            val root = JSONObject(trimmed)
            val presetsArr = root.optJSONArray("presets")
            incomingPresets = if (presetsArr != null) {
                PromptPreset.deserializeList(presetsArr.toString())
            } else {
                PromptPreset.deserializeList(root.optString("promptPresetsJson", ""))
            }
            activeIdsJson = root.optJSONObject("activePresetIds")?.toString()
                ?: root.optString("activePromptPresetIdsJson", "").takeIf { it.isNotBlank() }
            draftsObj = root.optJSONObject("drafts")
        }

        if (incomingPresets.isEmpty() && draftsObj == null) {
            throw IllegalArgumentException("No valid Prompt Presets or drafts found in selected file.")
        }

        var added = 0
        var updated = 0
        if (overwriteAll) {
            appPrefs.savePromptPresets(incomingPresets)
            added = incomingPresets.size
        } else {
            val current = appPrefs.getPromptPresets().associateBy { it.id }.toMutableMap()
            incomingPresets.forEach { preset ->
                val existing = current[preset.id]
                if (existing == null) {
                    current[preset.id] = preset
                    added++
                } else if (preset.updatedAt > existing.updatedAt) {
                    current[preset.id] = preset
                    updated++
                }
            }
            appPrefs.savePromptPresets(current.values.toList())
        }

        val activeRestored = if (!activeIdsJson.isNullOrBlank()) {
            val count = appPrefs.saveActivePromptPresetIdsFromJson(activeIdsJson, overwriteAll = overwriteAll)
            val validByMode = appPrefs.getPromptPresets().groupBy { it.mode }.mapValues { (_, list) ->
                list.map { it.id }.toSet()
            }
            PromptPresetMode.entries.forEach { mode ->
                val id = appPrefs.getActivePromptPresetId(mode)
                if (!id.isNullOrBlank() && id !in validByMode[mode].orEmpty()) {
                    appPrefs.setActivePromptPresetId(mode, null)
                }
            }
            count
        } else 0

        var draftsRestored = 0
        if (draftsObj != null) {
            draftsObj.optJSONObject("questionPromptDraft")?.let { obj ->
                if (overwriteAll || appPrefs.getQuestionPromptDraft() == null) {
                    runCatching {
                        appPrefs.saveQuestionPromptDraft(com.example.model.QuestionPromptConfig.fromJson(obj))
                        draftsRestored++
                    }
                }
            }
            draftsObj.optJSONObject("knowledgePromptDraft")?.let { obj ->
                if (overwriteAll || appPrefs.getKnowledgePromptDraft() == null) {
                    runCatching {
                        appPrefs.saveKnowledgePromptDraft(com.example.model.KnowledgePromptConfig.fromJson(obj))
                        draftsRestored++
                    }
                }
            }
            draftsObj.optJSONObject("flashcardPromptDraft")?.let { obj ->
                if (overwriteAll || appPrefs.getFlashcardPromptDraft() == null) {
                    runCatching {
                        appPrefs.saveFlashcardPromptDraft(com.example.model.FlashcardPromptConfig.fromJson(obj))
                        draftsRestored++
                    }
                }
            }
            draftsObj.optJSONObject("vocabularyPromptDraft")?.let { obj ->
                if (overwriteAll || appPrefs.getVocabularyPromptDraft() == null) {
                    runCatching {
                        appPrefs.saveVocabularyPromptDraft(com.example.model.VocabularyPromptConfig.fromJson(obj))
                        draftsRestored++
                    }
                }
            }
        }

        return PromptPresetsImportSummary(
            totalPresetsInPack = incomingPresets.size,
            addedCount = added,
            updatedCount = updated,
            activeSelectionsRestored = activeRestored,
            draftsRestored = draftsRestored
        )
    }

    private fun applyVocabularyFields(
        obj: JSONObject,
        appPrefs: ExamSetPreferences,
        overwriteAll: Boolean,
        mergeAppSettings: Boolean = true
    ) {
        val syncSettings = overwriteAll || mergeAppSettings
        if (syncSettings || appPrefs.getPreferredEnglishVariant().isBlank()) {
            val incomingVariant = obj.optString("preferredEnglishVariant", "")
            if (incomingVariant.isNotBlank()) {
                appPrefs.setPreferredEnglishVariant(incomingVariant)
            }
        }
        if (syncSettings && obj.has("vocabReminderEnabled")) {
            appPrefs.setVocabularyReminderEnabled(obj.optBoolean("vocabReminderEnabled", false))
        }

        val deckIdRemap = mutableMapOf<String, String>()
        val entryIdRemap = mutableMapOf<String, String>()

        val incomingVocabDecksRaw = obj.optString("vocabularyDecksJson", "")
        if (incomingVocabDecksRaw.isNotBlank()) {
            val incomingDecks = VocabularyDeck.deserializeList(incomingVocabDecksRaw)
            if (overwriteAll) {
                appPrefs.saveVocabularyDecks(incomingDecks)
            } else if (incomingDecks.isNotEmpty()) {
                val mergedDecks = mergeVocabularyDecksForRestore(
                    existingDecks = appPrefs.getVocabularyDecks(),
                    incomingDecks = incomingDecks,
                    deckIdRemap = deckIdRemap,
                    entryIdRemap = entryIdRemap
                )
                appPrefs.saveVocabularyDecks(mergedDecks)
            }
        }

        val incomingVocabSrsRaw = obj.optString("vocabularySrsJson", "")
        if (incomingVocabSrsRaw.isNotBlank()) {
            val incomingSrs = appPrefs.parseVocabSrsRootJson(incomingVocabSrsRaw)
            if (overwriteAll) {
                appPrefs.saveAllVocabularySrsMaps(incomingSrs)
            } else if (incomingSrs.isNotEmpty()) {
                val merged = appPrefs.getAllVocabularySrsMaps()
                    .mapValues { it.value.toMutableMap() }
                    .toMutableMap()
                incomingSrs.forEach { (rawDeckId, entryMap) ->
                    val resolvedDeckId = deckIdRemap[rawDeckId] ?: rawDeckId
                    val target = merged.getOrPut(resolvedDeckId) { mutableMapOf() }
                    entryMap.forEach { (rawEntryId, incomingSt) ->
                        val resolvedEntryId = entryIdRemap[rawEntryId] ?: rawEntryId
                        val currentSt = target[resolvedEntryId]
                        if (currentSt == null || isIncomingSrsNewer(currentSt, incomingSt)) {
                            target[resolvedEntryId] = incomingSt
                        }
                    }
                }
                appPrefs.saveAllVocabularySrsMaps(merged)
            }
        }

        val incomingWotdStatesRaw = obj.optString("languageWordOfTheDayStatesJson", "")
        if (incomingWotdStatesRaw.isNotBlank()) {
            if (overwriteAll) {
                appPrefs.saveLanguageWordOfTheDayStatesJson(incomingWotdStatesRaw)
            } else {
                val mergedWotd = WordOfTheDayService.mergePersistedLanguageStatesJson(
                    existingRaw = appPrefs.getLanguageWordOfTheDayStatesJson(),
                    incomingRaw = incomingWotdStatesRaw
                )
                appPrefs.saveLanguageWordOfTheDayStatesJson(mergedWotd)
            }
        }

        val incomingWotdCacheRaw = obj.optString("wordOfTheDayCacheJson", "")
        if (incomingWotdCacheRaw.isNotBlank() &&
            (overwriteAll || (syncSettings && appPrefs.getWordOfTheDayCacheJson().isNullOrBlank()))
        ) {
            appPrefs.saveWordOfTheDayCacheJson(incomingWotdCacheRaw)
        }

        val incomingInboxRaw = obj.optString("headwordInboxJson", "")
        if (incomingInboxRaw.isNotBlank()) {
            val incomingItems = runCatching {
                val arr = JSONArray(incomingInboxRaw)
                (0 until arr.length()).mapNotNull { i ->
                    com.example.model.HeadwordQueueItem.fromJson(arr.optJSONObject(i))
                }
            }.getOrDefault(emptyList())
            if (overwriteAll) {
                appPrefs.saveHeadwordInbox(incomingItems)
            } else if (incomingItems.isNotEmpty()) {
                val current = appPrefs.getHeadwordInbox()
                val currentPairs = current.map {
                    it.language.trim().lowercase() to it.normalizedWordKey()
                }.toSet()
                val toAdd = incomingItems.filter {
                    (it.language.trim().lowercase() to it.normalizedWordKey()) !in currentPairs
                }
                appPrefs.saveHeadwordInbox(current + toAdd)
            }
        }

        val incomingDismissedRaw = obj.optString("dismissedWordKeysJson", "")
        if (incomingDismissedRaw.isNotBlank()) {
            val incomingKeys = runCatching {
                val arr = JSONArray(incomingDismissedRaw)
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }.toSet()
            }.getOrDefault(emptySet())
            if (overwriteAll) {
                appPrefs.saveDismissedWordKeys(incomingKeys)
            } else if (incomingKeys.isNotEmpty()) {
                val current = appPrefs.getDismissedWordKeys()
                appPrefs.saveDismissedWordKeys(current + incomingKeys)
            }
        }
    }

    internal fun mergeVocabularyDecksForRestore(
        existingDecks: List<VocabularyDeck>,
        incomingDecks: List<VocabularyDeck>,
        deckIdRemap: MutableMap<String, String>,
        entryIdRemap: MutableMap<String, String>
    ): List<VocabularyDeck> {
        val result = existingDecks.toMutableList()
        for (incoming in incomingDecks) {
            val incomingBucketKey = canonicalDeckBucketKey(incoming)
            val matchIdx = result.indexOfFirst { existing ->
                existing.id == incoming.id || canonicalDeckBucketKey(existing) == incomingBucketKey
            }
            if (matchIdx < 0) {
                result.add(incoming)
                deckIdRemap[incoming.id] = incoming.id
            } else {
                val existing = result[matchIdx]
                deckIdRemap[incoming.id] = existing.id
                val existingEntries = existing.entries.toMutableList()
                val idxByWordKey = mutableMapOf<String, Int>()
                existingEntries.forEachIndexed { idx, e ->
                    val key = e.normalizedWordKey().ifBlank { e.id }
                    idxByWordKey.putIfAbsent(key, idx)
                }

                for (incEntry in incoming.entries) {
                    val wordKey = incEntry.normalizedWordKey().ifBlank { incEntry.id }
                    val existingEntryIdx = idxByWordKey[wordKey]
                        ?: existingEntries.indexOfFirst { it.id == incEntry.id }.takeIf { it >= 0 }
                    if (existingEntryIdx == null) {
                        existingEntries.add(incEntry)
                        idxByWordKey[wordKey] = existingEntries.lastIndex
                    } else {
                        val curEntry = existingEntries[existingEntryIdx]
                        entryIdRemap[incEntry.id] = curEntry.id
                        existingEntries[existingEntryIdx] = mergeVocabularyEntryForRestore(curEntry, incEntry)
                    }
                }

                val sortedEntries = existingEntries.sortedWith(
                    compareBy<VocabularyEntry>(
                        { it.word.trim().lowercase(Locale.ROOT) },
                        { it.partOfSpeech.trim().lowercase(Locale.ROOT) },
                        { it.createdAt }
                    )
                )
                result[matchIdx] = existing.copy(
                    translationLanguage = existing.translationLanguage.ifBlank { incoming.translationLanguage },
                    languageVariant = existing.languageVariant.ifBlank { incoming.languageVariant },
                    domain = existing.domain.ifBlank { incoming.domain },
                    entries = sortedEntries,
                    updatedAt = maxOf(existing.updatedAt, incoming.updatedAt)
                )
            }
        }
        return result
    }

    private fun mergeVocabularyEntryForRestore(
        existing: VocabularyEntry,
        incoming: VocabularyEntry
    ): VocabularyEntry {
        val preferIncoming = incoming.updatedAt > existing.updatedAt
        val mergedExamples = (existing.examples + incoming.examples)
            .distinctBy { it.normalizedSentenceKey().ifBlank { it.sentence.trim() } }
        val mergedSynonyms = (existing.synonyms + incoming.synonyms)
            .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }
        val mergedAntonyms = (existing.antonyms + incoming.antonyms)
            .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }
        val mergedCollocations = (existing.collocations + incoming.collocations)
            .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }

        return existing.copy(
            pronunciation = if (preferIncoming && incoming.pronunciation.isNotBlank()) incoming.pronunciation else existing.pronunciation.ifBlank { incoming.pronunciation },
            partOfSpeech = if (preferIncoming && incoming.partOfSpeech.isNotBlank()) incoming.partOfSpeech else existing.partOfSpeech.ifBlank { incoming.partOfSpeech },
            meaning = if (preferIncoming && incoming.meaning.isNotBlank()) incoming.meaning else existing.meaning.ifBlank { incoming.meaning },
            translation = if (preferIncoming && incoming.translation.isNotBlank()) incoming.translation else existing.translation.ifBlank { incoming.translation },
            translationLanguage = existing.translationLanguage.ifBlank { incoming.translationLanguage },
            usageNote = if (preferIncoming && incoming.usageNote.isNotBlank()) incoming.usageNote else existing.usageNote.ifBlank { incoming.usageNote },
            examples = mergedExamples,
            synonyms = mergedSynonyms,
            antonyms = mergedAntonyms,
            collocations = mergedCollocations,
            isBookmarked = existing.isBookmarked || incoming.isBookmarked,
            updatedAt = maxOf(existing.updatedAt, incoming.updatedAt)
        )
    }

    private fun canonicalDeckBucketKey(deck: VocabularyDeck): String {
        val lang = deck.language.trim().ifBlank { "English" }.lowercase(Locale.ROOT)
        val level = deck.resolvedLevel()
        val custom = if (level == VocabularyLevel.CUSTOM) deck.customLevelLabel.trim().lowercase(Locale.ROOT) else ""
        return "$lang|${level.code}|$custom"
    }

    internal fun mergeStudyNoteForRestore(
        existing: StudyNoteEntity,
        incoming: StudyNoteEntity,
        remappedLinkedPaper: Long?,
        cleanFolderId: String?
    ): StudyNoteEntity {
        val preferIncoming = incoming.updatedAt > existing.updatedAt
        val base = if (preferIncoming) incoming else existing
        val mergedBookmarks = (existing.getBookmarkedIndices() + incoming.getBookmarkedIndices()).sorted()
        val mergedMastered = (existing.getMasteredIndices() + incoming.getMasteredIndices()).sorted()
        val mergedCourse = if (incoming.updatedAt >= existing.updatedAt && incoming.course.isNotBlank()) {
            incoming.course.trim()
        } else {
            existing.course.trim().ifBlank { incoming.course.trim() }
        }
        val mergedLinkedPaper = if (preferIncoming) {
            remappedLinkedPaper ?: existing.linkedPaperId
        } else {
            existing.linkedPaperId ?: remappedLinkedPaper
        }
        val mergedFolderId = if (preferIncoming) {
            cleanFolderId ?: existing.folderId
        } else {
            existing.folderId ?: cleanFolderId
        }
        val mergedBookmarksJson = JSONArray(mergedBookmarks).toString()
        val mergedMasteredJson = JSONArray(mergedMastered).toString()

        return base.copy(
            id = existing.id,
            folderId = mergedFolderId,
            course = mergedCourse,
            linkedPaperId = mergedLinkedPaper,
            bookmarkedLinesJson = mergedBookmarksJson,
            masteredBlocksJson = mergedMasteredJson,
            updatedAt = maxOf(existing.updatedAt, incoming.updatedAt),
            syncUid = existing.syncUid.ifBlank { incoming.syncUid }
        )
    }

    internal fun mergeStudyNoteEntity(
        existing: StudyNoteEntity,
        incoming: StudyNoteEntity,
        remappedLinkedPaper: Long?,
        cleanUid: String
    ): StudyNoteEntity = mergeStudyNoteForRestore(
        existing = existing,
        incoming = incoming.copy(syncUid = cleanUid),
        remappedLinkedPaper = remappedLinkedPaper,
        cleanFolderId = incoming.folderId
    )

    internal fun parseKnowledgeCoursesMapJson(raw: String): Map<String, List<String>> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val cObj = JSONObject(raw)
            val incomingMap = linkedMapOf<String, List<String>>()
            val cKeys = cObj.keys()
            while (cKeys.hasNext()) {
                val course = cKeys.next().trim()
                if (course.isBlank()) continue
                val arr = cObj.optJSONArray(course)
                val subs = buildList<String> {
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val s = arr.optString(i).trim()
                            if (s.isNotBlank() && none { it.equals(s, ignoreCase = true) }) add(s)
                        }
                    }
                }
                incomingMap[course] = subs
            }
            incomingMap
        }.getOrDefault(emptyMap())
    }

    internal fun mergeKnowledgeCoursesMaps(
        existing: Map<String, List<String>>,
        incoming: Map<String, List<String>>
    ): Map<String, List<String>> {
        val merged = linkedMapOf<String, MutableList<String>>()
        val canonicalCourseByLower = mutableMapOf<String, String>()
        fun absorb(courseRaw: String, subs: List<String>) {
            val c = courseRaw.trim()
            if (c.isBlank()) return
            val canonical = canonicalCourseByLower.getOrPut(c.lowercase(Locale.ROOT)) { c }
            val list = merged.getOrPut(canonical) { mutableListOf() }
            subs.forEach { sRaw ->
                val s = sRaw.trim()
                if (s.isNotBlank() && list.none { it.equals(s, ignoreCase = true) }) {
                    list.add(s)
                }
            }
        }
        existing.forEach { (c, subs) -> absorb(c, subs) }
        incoming.forEach { (c, subs) -> absorb(c, subs) }
        return merged
    }

    internal fun applyVocabularyFieldsPublic(
        obj: JSONObject,
        appPrefs: ExamSetPreferences,
        overwriteAll: Boolean,
        mergeAppSettings: Boolean = true
    ) {
        applyVocabularyFields(obj, appPrefs, overwriteAll, mergeAppSettings)
    }

    internal fun isIncomingSrsNewer(existing: FlashcardSrsState, incoming: FlashcardSrsState): Boolean {
        if (incoming.reviewCount != existing.reviewCount) {
            return incoming.reviewCount > existing.reviewCount
        }
        return incoming.lastReviewedAtMillis > existing.lastReviewedAtMillis
    }
}
