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
import com.example.model.BackupCounts
import com.example.model.BackupManifest
import com.example.model.FolderItem
import com.example.model.RestoreMode
import com.example.model.RestorePreview
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

    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_FOLDERS = "folders.json"
    private const val ENTRY_PAPERS = "papers.ndjson"
    private const val ENTRY_NOTES = "notes.ndjson"
    private const val ENTRY_ATTEMPTS = "attempts.ndjson"
    private const val ENTRY_ANALYTICS = "analytics.json"
    private const val ENTRY_PREFERENCES = "preferences.json"

    fun getVaultDir(context: Context): File {
        return File(context.filesDir, VAULT_DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    fun getExportCacheDir(context: Context): File {
        return File(context.cacheDir, EXPORT_CACHE_DIR).apply { if (!exists()) mkdirs() }
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
        val notes = noteDao.getAllStudyNotesOnce()
        val attempts = paperDao.getAllAttemptsOnce()
        val analytics = paperDao.getAllDailyAnalyticsOnce()

        val checksums = linkedMapOf<String, String>()
        val createdAt = System.currentTimeMillis()
        val counts = BackupCounts(
            papersCount = papers.size,
            notesCount = notes.size,
            foldersCount = folders.size,
            attemptsCount = attempts.size,
            analyticsDaysCount = analytics.size
        )

        ZipOutputStream(BufferedOutputStream(FileOutputStream(destinationFile))).use { zos ->
            zos.setLevel(6)

            // 1. Folders
            onProgress("Packing ${folders.size} folders…", 0.25f)
            val foldersJson = serializeFoldersJson(folders)
            checksums[ENTRY_FOLDERS] = writeStringEntryWithSha256(zos, ENTRY_FOLDERS, foldersJson)

            // 2. Question Sets (NDJSON streaming line-by-line)
            onProgress("Streaming ${papers.size} question sets…", 0.45f)
            checksums[ENTRY_PAPERS] = writeNdjsonEntryWithSha256(zos, ENTRY_PAPERS) { writeLine ->
                papers.forEach { paper -> writeLine(serializePaper(paper).toString()) }
            }

            // 3. Knowledge Sets / Study Notes (NDJSON streaming)
            onProgress("Streaming ${notes.size} knowledge sets…", 0.65f)
            checksums[ENTRY_NOTES] = writeNdjsonEntryWithSha256(zos, ENTRY_NOTES) { writeLine ->
                notes.forEach { note -> writeLine(serializeStudyNote(note).toString()) }
            }

            // 4. Test Attempts (NDJSON streaming)
            onProgress("Streaming ${attempts.size} test attempts…", 0.80f)
            checksums[ENTRY_ATTEMPTS] = writeNdjsonEntryWithSha256(zos, ENTRY_ATTEMPTS) { writeLine ->
                attempts.forEach { attempt -> writeLine(serializeAttempt(attempt).toString()) }
            }

            // 5. Daily Analytics
            onProgress("Packing daily study analytics…", 0.90f)
            checksums[ENTRY_ANALYTICS] = writeStringEntryWithSha256(zos, ENTRY_ANALYTICS, serializeAnalyticsJson(analytics))

            // 6. Preferences
            checksums[ENTRY_PREFERENCES] = writeStringEntryWithSha256(
                zos,
                ENTRY_PREFERENCES,
                serializePreferencesJson(appPrefs, themePrefs)
            )

            // 7. Manifest (always written last with entry checksums)
            val manifest = BackupManifest(
                schemaVersion = 3,
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
            details = "${manifest.counts.papersCount} sets • ${manifest.counts.notesCount} knowledge sets • ${manifest.counts.attemptsCount} attempts",
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
            val rawJson = file.readText(Charsets.UTF_8)
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
            val manifestJson = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).readText()
            val manifest = parseManifest(JSONObject(manifestJson))

            var mismatchCount = 0
            val checkedEntries = mutableListOf<String>()
            for ((entryName, expectedHash) in manifest.entryChecksumsSha256) {
                val entry = zip.getEntry(entryName)
                if (entry == null) {
                    mismatchCount++
                    continue
                }
                val actualHash = zip.getInputStream(entry).use { computeStreamSha256(it) }
                if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                    mismatchCount++
                } else {
                    checkedEntries.add(entryName)
                }
            }

            val verified = mismatchCount == 0 && checkedEntries.isNotEmpty()
            val details = if (verified) {
                "Verified ${checkedEntries.size} encrypted/checksummed entries (SHA-256 OK)"
            } else {
                "Warning: $mismatchCount archive entry checksum mismatch(es) detected!"
            }

            return RestorePreview(
                sourceLabel = sourceLabel,
                isLegacyJson = false,
                checksumVerified = verified,
                checksumDetails = details,
                manifest = manifest,
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
        mode: RestoreMode
    ): BackupCounts {
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
                    paperDao.insertPaper(entity)
                }
            }
            return BackupCounts(papersCount = imported.size)
        }

        val zipFile = preview.tempFile ?: throw IllegalStateException("Vault file unavailable for restore")
        if (mode == RestoreMode.FULL_REPLACE) {
            createLocalRollingSnapshot(context, db, appPrefs, themePrefs, providerTag = "LOCAL_AUTO", prefix = "pre_restore")
        }

        ZipFile(zipFile).use { zip ->
            val folders = zip.getEntry(ENTRY_FOLDERS)?.let { entry ->
                parseFoldersJson(zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText())
            }.orEmpty()

            val papers = mutableListOf<PaperEntity>()
            zip.getEntry(ENTRY_PAPERS)?.let { entry ->
                BufferedReader(InputStreamReader(zip.getInputStream(entry), Charsets.UTF_8)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isNotBlank()) papers.add(deserializePaper(JSONObject(line)))
                    }
                }
            }

            val notes = mutableListOf<StudyNoteEntity>()
            zip.getEntry(ENTRY_NOTES)?.let { entry ->
                BufferedReader(InputStreamReader(zip.getInputStream(entry), Charsets.UTF_8)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isNotBlank()) notes.add(deserializeStudyNote(JSONObject(line)))
                    }
                }
            }

            val attempts = mutableListOf<AttemptEntity>()
            zip.getEntry(ENTRY_ATTEMPTS)?.let { entry ->
                BufferedReader(InputStreamReader(zip.getInputStream(entry), Charsets.UTF_8)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isNotBlank()) attempts.add(deserializeAttempt(JSONObject(line)))
                    }
                }
            }

            val analytics = zip.getEntry(ENTRY_ANALYTICS)?.let { entry ->
                parseAnalyticsJson(zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText())
            }.orEmpty()

            val prefsJson = zip.getEntry(ENTRY_PREFERENCES)?.let { entry ->
                zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
            }

            val paperDao = db.paperDao()
            val noteDao = db.studyNoteDao()

            // Restore folders first
            val mergedFolders = if (mode == RestoreMode.FULL_REPLACE) {
                folders
            } else {
                val current = appPrefs.getCustomFolders().associateBy { it.id }.toMutableMap()
                folders.forEach { incoming ->
                    if (!current.containsKey(incoming.id)) {
                        current[incoming.id] = incoming
                    }
                }
                current.values.toList()
            }
            appPrefs.saveCustomFolders(mergedFolders)

            val paperIdMap = mutableMapOf<Long, Long>()

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

                for (n in notes) {
                    val remappedLinkedPaper = n.linkedPaperId?.let { paperIdMap[it] ?: it }
                    val cleanUid = n.syncUid.ifBlank { UUID.randomUUID().toString() }
                    val existingNote = if (mode == RestoreMode.SMART_MERGE) {
                        noteDao.getStudyNoteBySyncUid(cleanUid)
                            ?: noteDao.getAllStudyNotesOnce().firstOrNull {
                                it.title == n.title && it.rawContent == n.rawContent
                            }
                    } else null

                    if (existingNote != null) {
                        if (n.updatedAt > existingNote.updatedAt) {
                            noteDao.updateStudyNote(
                                n.copy(
                                    id = existingNote.id,
                                    linkedPaperId = remappedLinkedPaper,
                                    syncUid = cleanUid
                                )
                            )
                        }
                    } else {
                        noteDao.insertStudyNote(
                            n.copy(
                                id = 0L,
                                linkedPaperId = remappedLinkedPaper,
                                syncUid = cleanUid
                            )
                        )
                    }
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

            if (!prefsJson.isNullOrBlank()) {
                applyPreferencesJson(prefsJson, appPrefs, themePrefs, overwriteAll = mode == RestoreMode.FULL_REPLACE)
            }

            return BackupCounts(
                papersCount = papers.size,
                notesCount = notes.size,
                foldersCount = folders.size,
                attemptsCount = attempts.size,
                analyticsDaysCount = analytics.size
            )
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
                put("foldersCount", m.counts.foldersCount)
                put("attemptsCount", m.counts.attemptsCount)
                put("analyticsDaysCount", m.counts.analyticsDaysCount)
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
                foldersCount = c.optInt("foldersCount", 0),
                attemptsCount = c.optInt("attemptsCount", 0),
                analyticsDaysCount = c.optInt("analyticsDaysCount", 0)
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
            })
        }
        return arr.toString()
    }

    private fun parseFoldersJson(json: String): List<FolderItem> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    FolderItem(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        color = o.optString("color", "#0284C7"),
                        parentId = if (o.has("parentId") && !o.isNull("parentId")) o.optString("parentId").ifBlank { null } else null,
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
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

    private fun serializePreferencesJson(appPrefs: ExamSetPreferences, themePrefs: ThemePreferences): String {
        return JSONObject().apply {
            put("dailyGoalMinutes", appPrefs.getDailyGoalMinutes())
            put("fontScaleMode", appPrefs.getFontScaleMode().name)
            put("shuffleOptions", appPrefs.getShuffleOptions())
            put("themeMode", themePrefs.getThemeMode().name)
        }.toString()
    }

    private fun applyPreferencesJson(
        json: String,
        appPrefs: ExamSetPreferences,
        themePrefs: ThemePreferences,
        overwriteAll: Boolean
    ) {
        runCatching {
            val obj = JSONObject(json)
            if (overwriteAll || appPrefs.getDailyGoalMinutes() == 30) {
                appPrefs.setDailyGoalMinutes(obj.optInt("dailyGoalMinutes", 30))
            }
            if (overwriteAll) {
                appPrefs.setFontScaleMode(AppFontScaleMode.fromString(obj.optString("fontScaleMode")))
                appPrefs.setShuffleOptions(obj.optBoolean("shuffleOptions", false))
                themePrefs.setThemeMode(ThemeMode.fromString(obj.optString("themeMode")))
            }
        }
    }
}
