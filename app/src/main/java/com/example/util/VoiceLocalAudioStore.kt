package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.UUID

private const val TAG_AUDIO_STORE = "VoiceLocalAudio"
private const val RECORDINGS_FOLDER = "voice_recordings"
private const val INDEX_FILE_NAME = "saved_clips.json"
const val DEFAULT_MAX_VOICE_TAKES_IN_SET = 3
const val DEFAULT_SEQUENCE_GAP_MS = 700L
private const val MAX_WAV_DATA_BYTES = 64L * 1024L * 1024L

data class SavedVoiceClip(
    val id: String,
    val takeNumber: Int,
    val filePath: String,
    val promptId: String,
    val promptTitle: String,
    val languageLabel: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val createdAtMs: Long,
    val transcriptPreview: String = "",
    val matchPercent: Int? = null,
    val estimatedWpm: Int = 0,
    val isFinal: Boolean = false,
    val stackKey: String = ""
) {
    fun effectiveStackKey(): String =
        stackKey.takeIf { it.isNotBlank() } ?: VoiceLocalAudioStore.deriveStackKey(promptId, promptTitle)

    fun toJsonString(): String = buildString {
        append('{')
        append("\"id\":\"").append(escapeJsonString(id)).append("\",")
        append("\"takeNumber\":").append(takeNumber).append(',')
        append("\"filePath\":\"").append(escapeJsonString(filePath)).append("\",")
        append("\"promptId\":\"").append(escapeJsonString(promptId)).append("\",")
        append("\"promptTitle\":\"").append(escapeJsonString(promptTitle)).append("\",")
        append("\"languageLabel\":\"").append(escapeJsonString(languageLabel)).append("\",")
        append("\"durationMs\":").append(durationMs).append(',')
        append("\"sizeBytes\":").append(sizeBytes).append(',')
        append("\"createdAtMs\":").append(createdAtMs).append(',')
        append("\"transcriptPreview\":\"").append(escapeJsonString(transcriptPreview)).append("\",")
        append("\"matchPercent\":").append(matchPercent ?: -1).append(',')
        append("\"estimatedWpm\":").append(estimatedWpm).append(',')
        append("\"isFinal\":").append(isFinal).append(',')
        append("\"stackKey\":\"").append(escapeJsonString(effectiveStackKey())).append('\"')
        append('}')
    }

    companion object {
        fun fromFieldMap(fields: Map<String, String>): SavedVoiceClip? {
            val path = fields["filePath"]?.trim().orEmpty()
            if (path.isBlank()) return null
            val rawMatch = fields["matchPercent"]?.toIntOrNull() ?: -1
            val promptId = fields["promptId"].orEmpty()
            val promptTitle = fields["promptTitle"]?.takeIf { it.isNotBlank() } ?: "Voice Practice"
            val parsedStackKey = fields["stackKey"]?.takeIf { it.isNotBlank() }
                ?: VoiceLocalAudioStore.deriveStackKey(promptId, promptTitle)
            return SavedVoiceClip(
                id = fields["id"]?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                takeNumber = (fields["takeNumber"]?.toIntOrNull() ?: 1).coerceAtLeast(1),
                filePath = path,
                promptId = promptId,
                promptTitle = promptTitle,
                languageLabel = fields["languageLabel"]?.takeIf { it.isNotBlank() } ?: "English",
                durationMs = (fields["durationMs"]?.toLongOrNull() ?: 0L).coerceAtLeast(0L),
                sizeBytes = (fields["sizeBytes"]?.toLongOrNull() ?: 0L).coerceAtLeast(0L),
                createdAtMs = fields["createdAtMs"]?.toLongOrNull() ?: 0L,
                transcriptPreview = fields["transcriptPreview"].orEmpty(),
                matchPercent = rawMatch.takeIf { it in 0..100 },
                estimatedWpm = (fields["estimatedWpm"]?.toIntOrNull() ?: 0).coerceAtLeast(0),
                isFinal = fields["isFinal"]?.equals("true", ignoreCase = true) == true,
                stackKey = parsedStackKey
            )
        }
    }
}

private fun escapeJsonString(raw: String): String = buildString(raw.length + 8) {
    for (ch in raw) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) {
                append("\\u").append(ch.code.toString(16).padStart(4, '0'))
            } else {
                append(ch)
            }
        }
    }
}

private fun parseClipsJsonArray(jsonText: String): List<SavedVoiceClip> {
    val len = jsonText.length
    var idx = 0
    fun skipWs() {
        while (idx < len && jsonText[idx].isWhitespace()) idx++
    }
    fun parseString(): String {
        if (idx >= len || jsonText[idx] != '"') return ""
        idx++
        val sb = StringBuilder()
        while (idx < len) {
            val c = jsonText[idx++]
            if (c == '"') break
            if (c == '\\' && idx < len) {
                when (val esc = jsonText[idx++]) {
                    '\\', '"', '/' -> sb.append(esc)
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> if (idx + 4 <= len) {
                        jsonText.substring(idx, idx + 4).toIntOrNull(16)?.let { sb.append(it.toChar()) }
                        idx += 4
                    }
                    else -> sb.append(esc)
                }
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }
    fun parsePrimitive(): String {
        val start = idx
        while (idx < len && jsonText[idx] !in charArrayOf(',', '}', ']', ' ', '\n', '\r', '\t')) {
            idx++
        }
        return jsonText.substring(start, idx)
    }
    fun skipUnknownValue() {
        skipWs()
        if (idx >= len) return
        when (jsonText[idx]) {
            '"' -> parseString()
            '{' -> {
                var depth = 0
                while (idx < len) {
                    when (jsonText[idx++]) {
                        '"' -> { idx--; parseString() }
                        '{' -> depth++
                        '}' -> { depth--; if (depth <= 0) break }
                    }
                }
            }
            '[' -> {
                var depth = 0
                while (idx < len) {
                    when (jsonText[idx++]) {
                        '"' -> { idx--; parseString() }
                        '[' -> depth++
                        ']' -> { depth--; if (depth <= 0) break }
                    }
                }
            }
            else -> parsePrimitive()
        }
    }

    val result = mutableListOf<SavedVoiceClip>()
    skipWs()
    if (idx >= len || jsonText[idx] != '[') return emptyList()
    idx++
    while (idx < len) {
        skipWs()
        if (idx >= len || jsonText[idx] == ']') break
        if (jsonText[idx] == ',') { idx++; continue }
        if (jsonText[idx] != '{') { skipUnknownValue(); continue }
        idx++
        val fields = HashMap<String, String>()
        while (idx < len) {
            skipWs()
            if (idx >= len) break
            if (jsonText[idx] == '}') { idx++; break }
            if (jsonText[idx] == ',') { idx++; continue }
            val key = if (jsonText[idx] == '"') parseString() else break
            skipWs()
            if (idx < len && jsonText[idx] == ':') idx++
            skipWs()
            val value = if (idx < len && jsonText[idx] == '"') {
                parseString()
            } else if (idx < len && (jsonText[idx] == '{' || jsonText[idx] == '[')) {
                skipUnknownValue(); ""
            } else {
                parsePrimitive()
            }
            fields[key] = value
        }
        SavedVoiceClip.fromFieldMap(fields)?.let(result::add)
    }
    return result
}

/**
 * Append-safe 16 kHz / 16-bit / mono PCM WAV writer stored strictly in [Context.getNoBackupFilesDir].
 */
class WavSessionWriter(
    val file: File,
    private val sampleRateHz: Int = 16_000,
    private val channels: Int = 1,
    private val bitsPerSample: Int = 16
) {
    private var outStream: FileOutputStream? = null
    private var dataBytesWritten: Long = 0L

    @Synchronized
    fun openOrResume(): Boolean {
        return runCatching {
            file.parentFile?.mkdirs()
            val existsAndValid = file.exists() && file.length() >= 44L
            if (!existsAndValid) {
                FileOutputStream(file, false).use { fos ->
                    fos.write(buildWavHeader(0L))
                    fos.flush()
                }
                dataBytesWritten = 0L
            } else {
                dataBytesWritten = (file.length() - 44L).coerceAtLeast(0L)
            }
            outStream = FileOutputStream(file, true)
            true
        }.getOrElse { err ->
            Log.w(TAG_AUDIO_STORE, "Unable to open WAV writer", err)
            false
        }
    }

    @Synchronized
    fun writePcm(buffer: ByteArray, length: Int) {
        if (length <= 0) return
        val stream = outStream ?: return
        if (dataBytesWritten >= MAX_WAV_DATA_BYTES) return
        val allowed = minOf(length.toLong(), MAX_WAV_DATA_BYTES - dataBytesWritten).toInt()
        if (allowed <= 0) return
        runCatching {
            stream.write(buffer, 0, allowed)
            dataBytesWritten += allowed
        }
    }

    @Synchronized
    fun closeAndFinalize(): Long {
        runCatching { outStream?.flush() }
        runCatching { outStream?.close() }
        outStream = null
        if (file.exists() && file.length() >= 44L) {
            val actualDataBytes = (file.length() - 44L).coerceAtLeast(0L)
            dataBytesWritten = actualDataBytes
            runCatching {
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(0L)
                    raf.write(buildWavHeader(actualDataBytes))
                }
            }
        }
        return dataBytesWritten
    }

    private fun buildWavHeader(totalAudioLen: Long): ByteArray {
        val totalDataLen = totalAudioLen + 36L
        val byteRate = sampleRateHz.toLong() * channels * bitsPerSample / 8L
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, totalDataLen)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16L)
        writeShortLE(header, 20, 1.toShort()) // PCM
        writeShortLE(header, 22, channels.toShort())
        writeIntLE(header, 24, sampleRateHz.toLong())
        writeIntLE(header, 28, byteRate)
        writeShortLE(header, 32, blockAlign)
        writeShortLE(header, 34, bitsPerSample.toShort())
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, totalAudioLen)
        return header
    }

    private fun writeIntLE(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(buf: ByteArray, offset: Int, value: Short) {
        buf[offset] = (value.toInt() and 0xFF).toByte()
        buf[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }
}

/**
 * Strictly local-only voice clip storage placed inside [Context.getNoBackupFilesDir]
 * so Android Auto Backup, device transfer, and ExamSet Cloud Vault never upload audio files.
 *
 * Groups clips into per-note / per-prompt **Version Stacks**, each maintaining a rolling set
 * of up to [DEFAULT_MAX_VOICE_TAKES_IN_SET] (3) takes so users can toggle versions, play them
 * sequentially for comparison, select a Final version non-destructively, or clear old drafts.
 */
object VoiceLocalAudioStore {

    private val noteBlockIdRegex = Regex("""^(note_[^_]+)_\d+$""")

    fun deriveStackKey(promptId: String, fallbackTitle: String = ""): String {
        val trimmedId = promptId.trim()
        if (trimmedId.isNotBlank()) {
            val noteMatch = noteBlockIdRegex.find(trimmedId)
            if (noteMatch != null) {
                return noteMatch.groupValues[1]
            }
            return trimmedId
        }
        return fallbackTitle.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), "_")
            .take(48)
            .ifBlank { "default_stack" }
    }

    fun getRecordingsDir(context: Context): File {
        val base = context.noBackupFilesDir ?: context.filesDir
        return File(base, RECORDINGS_FOLDER).apply { if (!exists()) mkdirs() }
    }

    fun createSessionTakeFile(context: Context): File {
        val dir = getRecordingsDir(context)
        return File(dir, "take_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}.wav")
    }

    fun loadClips(context: Context): List<SavedVoiceClip> =
        loadClipsInDir(getRecordingsDir(context))

    fun loadClipsInDir(dir: File): List<SavedVoiceClip> {
        val indexFile = File(dir, INDEX_FILE_NAME)
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            parseClipsJsonArray(indexFile.readText())
                .mapNotNull { item ->
                    val file = File(item.filePath)
                    if (file.exists() && file.length() > 44L) {
                        item.copy(
                            sizeBytes = file.length(),
                            stackKey = item.effectiveStackKey()
                        )
                    } else {
                        null
                    }
                }
                .sortedWith(compareBy<SavedVoiceClip> { it.createdAtMs }.thenBy { it.takeNumber })
        }.getOrDefault(emptyList())
    }

    fun addTakeToRollingSet(
        context: Context,
        sourceWavPath: String,
        promptId: String,
        promptTitle: String,
        languageLabel: String,
        durationMs: Long,
        transcriptPreview: String = "",
        matchPercent: Int? = null,
        estimatedWpm: Int = 0,
        maxTakesInSet: Int = DEFAULT_MAX_VOICE_TAKES_IN_SET,
        stackKey: String = deriveStackKey(promptId, promptTitle)
    ): List<SavedVoiceClip> = addTakeToRollingSetInDir(
        dir = getRecordingsDir(context),
        sourceWavPath = sourceWavPath,
        promptId = promptId,
        promptTitle = promptTitle,
        languageLabel = languageLabel,
        durationMs = durationMs,
        transcriptPreview = transcriptPreview,
        matchPercent = matchPercent,
        estimatedWpm = estimatedWpm,
        maxTakesInSet = maxTakesInSet,
        stackKey = stackKey
    )

    fun addTakeToRollingSetInDir(
        dir: File,
        sourceWavPath: String,
        promptId: String,
        promptTitle: String,
        languageLabel: String,
        durationMs: Long,
        transcriptPreview: String = "",
        matchPercent: Int? = null,
        estimatedWpm: Int = 0,
        maxTakesInSet: Int = DEFAULT_MAX_VOICE_TAKES_IN_SET,
        stackKey: String = deriveStackKey(promptId, promptTitle)
    ): List<SavedVoiceClip> {
        if (!dir.exists()) dir.mkdirs()
        val src = File(sourceWavPath)
        val existing = loadClipsInDir(dir).toMutableList()
        if (!src.exists() || src.length() <= 44L) {
            return existing
        }

        val resolvedStackKey = stackKey.ifBlank { deriveStackKey(promptId, promptTitle) }

        // If already registered (e.g. re-evaluation on same clip), update metrics instead of duplicating
        val existingIdx = existing.indexOfFirst { it.filePath == src.absolutePath }
        if (existingIdx >= 0) {
            val current = existing[existingIdx]
            existing[existingIdx] = current.copy(
                languageLabel = languageLabel.ifBlank { current.languageLabel },
                durationMs = durationMs.coerceAtLeast(current.durationMs),
                sizeBytes = src.length(),
                transcriptPreview = transcriptPreview.ifBlank { current.transcriptPreview },
                matchPercent = matchPercent ?: current.matchPercent,
                estimatedWpm = if (estimatedWpm > 0) estimatedWpm else current.estimatedWpm,
                stackKey = current.effectiveStackKey()
            )
            writeIndex(dir, existing)
            return existing
        }

        val otherStackClips = existing.filter { it.effectiveStackKey() != resolvedStackKey }.toMutableList()
        val stackClips = existing
            .filter { it.effectiveStackKey() == resolvedStackKey }
            .sortedWith(compareBy<SavedVoiceClip> { it.createdAtMs }.thenBy { it.takeNumber })
            .toMutableList()

        val limit = maxTakesInSet.coerceIn(2, 3)
        // Evict oldest non-final draft first inside THIS stack; if none non-final, evict oldest overall
        while (stackClips.size >= limit) {
            val evictIndex = stackClips.indexOfFirst { !it.isFinal }.let { idx ->
                if (idx >= 0) idx else 0
            }
            val evicted = stackClips.removeAt(evictIndex)
            runCatching { File(evicted.filePath).delete() }
        }

        val nextTakeNum = (stackClips.maxOfOrNull { it.takeNumber } ?: 0) + 1
        val newClip = SavedVoiceClip(
            id = UUID.randomUUID().toString(),
            takeNumber = nextTakeNum,
            filePath = src.absolutePath,
            promptId = promptId,
            promptTitle = promptTitle.ifBlank { "Voice Practice" },
            languageLabel = languageLabel.ifBlank { "English" },
            durationMs = durationMs.coerceAtLeast(0L),
            sizeBytes = src.length(),
            createdAtMs = System.currentTimeMillis(),
            transcriptPreview = transcriptPreview.trim().take(160),
            matchPercent = matchPercent,
            estimatedWpm = estimatedWpm.coerceAtLeast(0),
            isFinal = false,
            stackKey = resolvedStackKey
        )
        stackClips.add(newClip)
        val updated = (otherStackClips + stackClips)
            .sortedWith(compareBy<SavedVoiceClip> { it.createdAtMs }.thenBy { it.takeNumber })
        writeIndex(dir, updated)
        return updated
    }

    fun updateTakeEvaluation(
        context: Context,
        filePath: String,
        transcriptPreview: String,
        matchPercent: Int?,
        estimatedWpm: Int
    ): List<SavedVoiceClip> = updateTakeEvaluationInDir(
        dir = getRecordingsDir(context),
        filePath = filePath,
        transcriptPreview = transcriptPreview,
        matchPercent = matchPercent,
        estimatedWpm = estimatedWpm
    )

    fun updateTakeEvaluationInDir(
        dir: File,
        filePath: String,
        transcriptPreview: String,
        matchPercent: Int?,
        estimatedWpm: Int
    ): List<SavedVoiceClip> {
        val existing = loadClipsInDir(dir).toMutableList()
        val idx = existing.indexOfFirst { it.filePath == filePath }
        if (idx >= 0) {
            val cur = existing[idx]
            existing[idx] = cur.copy(
                transcriptPreview = transcriptPreview.trim().take(160).ifBlank { cur.transcriptPreview },
                matchPercent = matchPercent,
                estimatedWpm = estimatedWpm.coerceAtLeast(0)
            )
            writeIndex(dir, existing)
        }
        return existing
    }

    /**
     * Non-destructively marks [finalClipId] as the Final version inside its Version Stack while
     * preserving all sibling takes so the user can keep toggling and comparing audio clips.
     */
    fun setFinalClip(context: Context, finalClipId: String): List<SavedVoiceClip> =
        setFinalClipInDir(getRecordingsDir(context), finalClipId)

    fun setFinalClipInDir(dir: File, finalClipId: String): List<SavedVoiceClip> {
        val existing = loadClipsInDir(dir)
        val chosen = existing.firstOrNull { it.id == finalClipId } ?: return existing
        val targetStack = chosen.effectiveStackKey()
        val updated = existing.map { clip ->
            if (clip.effectiveStackKey() == targetStack) {
                clip.copy(isFinal = (clip.id == chosen.id))
            } else {
                clip
            }
        }
        writeIndex(dir, updated)
        return updated
    }

    /**
     * Deletes non-final drafts inside [stackKey] once a Final take has been marked,
     * leaving the Final take and all other notes' Version Stacks untouched.
     */
    fun clearNonFinalDrafts(context: Context, stackKey: String): List<SavedVoiceClip> =
        clearNonFinalDraftsInDir(getRecordingsDir(context), stackKey)

    fun clearNonFinalDraftsInDir(dir: File, stackKey: String): List<SavedVoiceClip> {
        val existing = loadClipsInDir(dir)
        val stackHasFinal = existing.any { it.effectiveStackKey() == stackKey && it.isFinal }
        if (!stackHasFinal) return existing
        val updated = existing.filter { clip ->
            val inTarget = clip.effectiveStackKey() == stackKey
            if (inTarget && !clip.isFinal) {
                runCatching { File(clip.filePath).delete() }
                false
            } else {
                true
            }
        }
        writeIndex(dir, updated)
        return updated
    }

    /**
     * Marks [finalClipId] as the final chosen take and deletes every other draft clip in the
     * same stack from disk, leaving only that chosen recording (plus other stacks intact).
     */
    fun keepOnlyFinalClip(context: Context, finalClipId: String): List<SavedVoiceClip> =
        keepOnlyFinalClipInDir(getRecordingsDir(context), finalClipId)

    fun keepOnlyFinalClipInDir(dir: File, finalClipId: String): List<SavedVoiceClip> {
        val existing = loadClipsInDir(dir)
        val chosen = existing.firstOrNull { it.id == finalClipId } ?: return existing
        val targetStack = chosen.effectiveStackKey()
        val updated = existing.mapNotNull { clip ->
            if (clip.effectiveStackKey() != targetStack) {
                clip
            } else if (clip.id == chosen.id) {
                clip.copy(isFinal = true)
            } else {
                runCatching { File(clip.filePath).delete() }
                null
            }
        }
        writeIndex(dir, updated)
        return updated
    }

    fun deleteClip(context: Context, clipId: String): List<SavedVoiceClip> =
        deleteClipInDir(getRecordingsDir(context), clipId)

    fun deleteClipInDir(dir: File, clipId: String): List<SavedVoiceClip> {
        val existing = loadClipsInDir(dir)
        val target = existing.firstOrNull { it.id == clipId }
        if (target != null) {
            runCatching { File(target.filePath).delete() }
        }
        val updated = existing.filterNot { it.id == clipId }
        writeIndex(dir, updated)
        return updated
    }

    fun deleteStack(context: Context, stackKey: String): List<SavedVoiceClip> =
        deleteStackInDir(getRecordingsDir(context), stackKey)

    fun deleteStackInDir(dir: File, stackKey: String): List<SavedVoiceClip> {
        val existing = loadClipsInDir(dir)
        val remaining = existing.filter { clip ->
            if (clip.effectiveStackKey() == stackKey) {
                runCatching { File(clip.filePath).delete() }
                false
            } else {
                true
            }
        }
        writeIndex(dir, remaining)
        return remaining
    }

    fun pruneOrphanWavs(context: Context, activeRecordingPath: String? = null) =
        pruneOrphanWavsInDir(getRecordingsDir(context), activeRecordingPath)

    fun pruneOrphanWavsInDir(dir: File, activeRecordingPath: String? = null) {
        val trackedPaths = loadClipsInDir(dir).map { it.filePath }.toSet()
        dir.listFiles()?.forEach { f ->
            if (f.name == INDEX_FILE_NAME) return@forEach
            val path = f.absolutePath
            if (path != activeRecordingPath && path !in trackedPaths && f.extension.equals("wav", ignoreCase = true)) {
                runCatching { f.delete() }
            }
        }
    }

    fun deleteAllRecordings(context: Context) =
        deleteAllRecordingsInDir(getRecordingsDir(context))

    fun deleteAllRecordingsInDir(dir: File) {
        dir.listFiles()?.forEach { f ->
            runCatching { f.delete() }
        }
    }

    private fun writeIndex(dir: File, clips: List<SavedVoiceClip>) {
        val indexFile = File(dir, INDEX_FILE_NAME)
        val payload = clips.joinToString(prefix = "[", postfix = "]", separator = ",") { it.toJsonString() }
        runCatching {
            indexFile.writeText(payload)
        }
    }
}

/**
 * Pure-Kotlin ordering and navigation helpers for a Version Stack sequential playback queue.
 */
object VoiceStackPlaybackQueue {
    fun orderedStackClips(clips: List<SavedVoiceClip>): List<SavedVoiceClip> =
        clips.sortedWith(compareBy<SavedVoiceClip> { it.createdAtMs }.thenBy { it.takeNumber })

    fun resolveStartIndex(
        orderedClips: List<SavedVoiceClip>,
        startFromClipId: String?
    ): Int {
        if (orderedClips.isEmpty()) return -1
        if (startFromClipId.isNullOrBlank()) return 0
        val idx = orderedClips.indexOfFirst { it.id == startFromClipId }
        return if (idx >= 0) idx else 0
    }

    fun nextClip(
        orderedClips: List<SavedVoiceClip>,
        currentClipId: String?
    ): SavedVoiceClip? {
        if (orderedClips.isEmpty()) return null
        val idx = orderedClips.indexOfFirst { it.id == currentClipId }
        return when {
            idx < 0 -> orderedClips.firstOrNull()
            idx + 1 < orderedClips.size -> orderedClips[idx + 1]
            else -> null
        }
    }

    fun previousClip(
        orderedClips: List<SavedVoiceClip>,
        currentClipId: String?
    ): SavedVoiceClip? {
        if (orderedClips.isEmpty()) return null
        val idx = orderedClips.indexOfFirst { it.id == currentClipId }
        return when {
            idx <= 0 -> null
            else -> orderedClips[idx - 1]
        }
    }
}

@Stable
class VoiceAudioPlayer(
    context: Context? = null
) {
    private val appContext: Context? = context?.applicationContext
    private val audioManager: AudioManager? = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var playbackGeneration: Int = 0
    private var pendingGapRunnable: Runnable? = null

    var activeFilePath by mutableStateOf<String?>(null)
        private set

    var activeClipId by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var currentPositionMs by mutableLongStateOf(0L)
        private set

    var totalDurationMs by mutableLongStateOf(0L)
        private set

    var playbackError by mutableStateOf<String?>(null)
        private set

    var isSequenceMode by mutableStateOf(false)
        private set

    var sequenceClips by mutableStateOf<List<SavedVoiceClip>>(emptyList())
        private set

    var activeSequenceIndex by mutableIntStateOf(-1)
        private set

    var isWaitingBetweenTakes by mutableStateOf(false)
        private set

    private var sequenceGapMs: Long = DEFAULT_SEQUENCE_GAP_MS

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                mainHandler.post { pausePlayback() }
            }
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (mp != null && isPlaying) {
                runCatching {
                    currentPositionMs = mp.currentPosition.toLong().coerceAtLeast(0L)
                }
                mainHandler.postDelayed(this, 100L)
            }
        }
    }

    fun playClip(clip: SavedVoiceClip) {
        if (activeClipId == clip.id && mediaPlayer != null) {
            toggleCurrentPlayback()
            return
        }
        playbackGeneration++
        clearPendingGap()
        isSequenceMode = false
        sequenceClips = emptyList()
        activeSequenceIndex = -1
        isWaitingBetweenTakes = false
        startFreshPlayback(
            filePath = clip.filePath,
            clipId = clip.id,
            generation = playbackGeneration
        )
    }

    fun playOrPause(filePath: String) {
        playbackError = null
        val file = File(filePath)
        if (!file.exists() || file.length() <= 44L) {
            playbackError = "Recorded audio file is unavailable."
            return
        }

        if (mediaPlayer != null && activeFilePath == filePath) {
            toggleCurrentPlayback()
            return
        }

        playbackGeneration++
        clearPendingGap()
        isSequenceMode = false
        sequenceClips = emptyList()
        activeSequenceIndex = -1
        isWaitingBetweenTakes = false
        startFreshPlayback(
            filePath = filePath,
            clipId = null,
            generation = playbackGeneration
        )
    }

    fun playSequence(
        clips: List<SavedVoiceClip>,
        startFromClipId: String? = null,
        gapMs: Long = DEFAULT_SEQUENCE_GAP_MS
    ) {
        playbackError = null
        val ordered = VoiceStackPlaybackQueue.orderedStackClips(clips)
        if (ordered.isEmpty()) {
            playbackError = "No saved takes in this version stack."
            return
        }
        val playable = ordered.filter { clip ->
            val f = File(clip.filePath)
            f.exists() && f.length() > 44L
        }
        if (playable.isEmpty()) {
            playbackError = "Saved audio files in this stack are unavailable."
            return
        }

        val startIdx = VoiceStackPlaybackQueue.resolveStartIndex(playable, startFromClipId)
        playbackGeneration++
        clearPendingGap()
        sequenceGapMs = gapMs.coerceIn(0L, 2_500L)
        sequenceClips = playable
        isSequenceMode = true
        isWaitingBetweenTakes = false
        startSequenceAt(index = startIdx, generation = playbackGeneration)
    }

    fun toggleSequencePlayPause(
        clips: List<SavedVoiceClip>,
        preferredClipId: String? = null
    ) {
        if (isSequenceMode) {
            if (isWaitingBetweenTakes) {
                // Cancel pause countdown and immediately advance or pause
                pausePlayback()
                return
            }
            if (mediaPlayer != null) {
                toggleCurrentPlayback()
                return
            }
        }
        playSequence(clips = clips, startFromClipId = preferredClipId)
    }

    fun skipToNextInSequence() {
        val clips = sequenceClips
        if (!isSequenceMode || clips.isEmpty()) return
        val nextIdx = activeSequenceIndex + 1
        if (nextIdx >= clips.size) {
            stopAndRelease()
            return
        }
        playbackGeneration++
        clearPendingGap()
        isWaitingBetweenTakes = false
        startSequenceAt(index = nextIdx, generation = playbackGeneration)
    }

    fun skipToPreviousInSequence() {
        val clips = sequenceClips
        if (!isSequenceMode || clips.isEmpty()) return
        // If > 2s into current clip, restart current clip first; else go to previous clip
        if (currentPositionMs > 2_000L && activeSequenceIndex in clips.indices) {
            seekTo(0L)
            if (!isPlaying && !isWaitingBetweenTakes) {
                toggleCurrentPlayback()
            }
            return
        }
        val prevIdx = (activeSequenceIndex - 1).coerceAtLeast(0)
        playbackGeneration++
        clearPendingGap()
        isWaitingBetweenTakes = false
        startSequenceAt(index = prevIdx, generation = playbackGeneration)
    }

    fun pausePlayback() {
        clearPendingGap()
        isWaitingBetweenTakes = false
        mainHandler.removeCallbacks(progressTicker)
        runCatching {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.pause()
            }
        }
        isPlaying = false
        abandonPlaybackAudioFocus()
    }

    private fun toggleCurrentPlayback() {
        val current = mediaPlayer ?: return
        val path = activeFilePath ?: return
        runCatching {
            if (current.isPlaying) {
                current.pause()
                isPlaying = false
                mainHandler.removeCallbacks(progressTicker)
                abandonPlaybackAudioFocus()
            } else {
                requestPlaybackAudioFocus()
                if (currentPositionMs >= (totalDurationMs - 150L).coerceAtLeast(0L) && totalDurationMs > 0L) {
                    current.seekTo(0)
                    currentPositionMs = 0L
                }
                current.start()
                isPlaying = true
                mainHandler.post(progressTicker)
            }
        }.onFailure {
            val gen = ++playbackGeneration
            startFreshPlayback(filePath = path, clipId = activeClipId, generation = gen)
        }
    }

    private fun startSequenceAt(index: Int, generation: Int) {
        if (generation != playbackGeneration) return
        val clips = sequenceClips
        if (index !in clips.indices) {
            stopAndRelease()
            return
        }
        activeSequenceIndex = index
        val target = clips[index]
        val f = File(target.filePath)
        if (!f.exists() || f.length() <= 44L) {
            // Skip missing clip cleanly
            if (index + 1 < clips.size) {
                startSequenceAt(index + 1, generation)
            } else {
                playbackError = "One or more clips in this stack were unavailable."
                stopAndRelease()
            }
            return
        }
        startFreshPlayback(
            filePath = target.filePath,
            clipId = target.id,
            generation = generation
        )
    }

    private fun startFreshPlayback(
        filePath: String,
        clipId: String?,
        generation: Int
    ) {
        releaseMediaPlayerOnly()
        playbackError = null
        val file = File(filePath)
        if (!file.exists() || file.length() <= 44L) {
            if (isSequenceMode && generation == playbackGeneration) {
                advanceSequenceAfterCompletion(generation)
            } else {
                playbackError = "Recorded audio file is unavailable."
            }
            return
        }

        requestPlaybackAudioFocus()
        runCatching {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(filePath)
                setOnCompletionListener {
                    mainHandler.post {
                        if (generation != playbackGeneration) return@post
                        this@VoiceAudioPlayer.isPlaying = false
                        this@VoiceAudioPlayer.currentPositionMs = this@VoiceAudioPlayer.totalDurationMs
                        mainHandler.removeCallbacks(progressTicker)
                        if (this@VoiceAudioPlayer.isSequenceMode) {
                            advanceSequenceAfterCompletion(generation)
                        } else {
                            abandonPlaybackAudioFocus()
                        }
                    }
                }
                setOnErrorListener { _, _, _ ->
                    mainHandler.post {
                        if (generation != playbackGeneration) return@post
                        if (this@VoiceAudioPlayer.isSequenceMode) {
                            advanceSequenceAfterCompletion(generation)
                        } else {
                            this@VoiceAudioPlayer.playbackError = "Unable to play this local audio clip."
                            stopAndRelease()
                        }
                    }
                    true
                }
                prepare()
            }
            if (generation != playbackGeneration) {
                runCatching { mp.release() }
                return
            }
            mediaPlayer = mp
            activeFilePath = filePath
            activeClipId = clipId
            totalDurationMs = mp.duration.toLong().coerceAtLeast(0L)
            currentPositionMs = 0L
            mp.start()
            isPlaying = true
            isWaitingBetweenTakes = false
            mainHandler.post(progressTicker)
        }.onFailure { err ->
            Log.e(TAG_AUDIO_STORE, "Local playback failed", err)
            if (isSequenceMode && generation == playbackGeneration) {
                advanceSequenceAfterCompletion(generation)
            } else {
                playbackError = "Audio playback could not start for this clip."
                stopAndRelease()
            }
        }
    }

    private fun advanceSequenceAfterCompletion(generation: Int) {
        if (generation != playbackGeneration) return
        val nextIdx = activeSequenceIndex + 1
        if (nextIdx !in sequenceClips.indices) {
            stopAndRelease()
            return
        }
        releaseMediaPlayerOnly()
        isWaitingBetweenTakes = true
        val gapRunnable = Runnable {
            if (generation != playbackGeneration || !isSequenceMode) return@Runnable
            isWaitingBetweenTakes = false
            startSequenceAt(index = nextIdx, generation = generation)
        }
        pendingGapRunnable = gapRunnable
        mainHandler.postDelayed(gapRunnable, sequenceGapMs)
    }

    fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        val clamped = positionMs.coerceIn(0L, totalDurationMs.coerceAtLeast(0L))
        runCatching {
            mp.seekTo(clamped.toInt())
            currentPositionMs = clamped
        }
    }

    fun stopAndRelease() {
        playbackGeneration++
        clearPendingGap()
        releaseMediaPlayerOnly()
        isPlaying = false
        isWaitingBetweenTakes = false
        isSequenceMode = false
        sequenceClips = emptyList()
        activeSequenceIndex = -1
        currentPositionMs = 0L
        totalDurationMs = 0L
        activeFilePath = null
        activeClipId = null
        abandonPlaybackAudioFocus()
    }

    private fun clearPendingGap() {
        pendingGapRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingGapRunnable = null
    }

    private fun releaseMediaPlayerOnly() {
        mainHandler.removeCallbacks(progressTicker)
        runCatching {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        mediaPlayer = null
    }

    private fun requestPlaybackAudioFocus() {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }
    }

    private fun abandonPlaybackAudioFocus() {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusListener)
            }
        }
    }
}

@Composable
fun rememberVoiceAudioPlayer(): VoiceAudioPlayer {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(context) { VoiceAudioPlayer(context) }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pausePlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.stopAndRelease()
        }
    }
    return player
}
