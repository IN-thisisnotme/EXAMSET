package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID

private const val TAG_AUDIO_STORE = "VoiceLocalAudio"
private const val RECORDINGS_FOLDER = "voice_recordings"
private const val INDEX_FILE_NAME = "saved_clips.json"
const val DEFAULT_MAX_VOICE_TAKES_IN_SET = 3
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
    val isFinal: Boolean = false
) {
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
        append("\"isFinal\":").append(isFinal)
        append('}')
    }

    companion object {
        fun fromFieldMap(fields: Map<String, String>): SavedVoiceClip? {
            val path = fields["filePath"]?.trim().orEmpty()
            if (path.isBlank()) return null
            val rawMatch = fields["matchPercent"]?.toIntOrNull() ?: -1
            return SavedVoiceClip(
                id = fields["id"]?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                takeNumber = (fields["takeNumber"]?.toIntOrNull() ?: 1).coerceAtLeast(1),
                filePath = path,
                promptId = fields["promptId"].orEmpty(),
                promptTitle = fields["promptTitle"]?.takeIf { it.isNotBlank() } ?: "Voice Practice",
                languageLabel = fields["languageLabel"]?.takeIf { it.isNotBlank() } ?: "English",
                durationMs = (fields["durationMs"]?.toLongOrNull() ?: 0L).coerceAtLeast(0L),
                sizeBytes = (fields["sizeBytes"]?.toLongOrNull() ?: 0L).coerceAtLeast(0L),
                createdAtMs = fields["createdAtMs"]?.toLongOrNull() ?: 0L,
                transcriptPreview = fields["transcriptPreview"].orEmpty(),
                matchPercent = rawMatch.takeIf { it in 0..100 },
                estimatedWpm = (fields["estimatedWpm"]?.toIntOrNull() ?: 0).coerceAtLeast(0),
                isFinal = fields["isFinal"]?.equals("true", ignoreCase = true) == true
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
 * Maintains a rolling buffer of up to [DEFAULT_MAX_VOICE_TAKES_IN_SET] (3) takes so users
 * can compare versions side by side, override the oldest take when recording a 4th, and
 * tap "Keep Only Final" to discard all other drafts.
 */
object VoiceLocalAudioStore {

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
                        item.copy(sizeBytes = file.length())
                    } else {
                        null
                    }
                }
                .sortedBy { it.createdAtMs }
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
        maxTakesInSet: Int = DEFAULT_MAX_VOICE_TAKES_IN_SET
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
        maxTakesInSet = maxTakesInSet
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
        maxTakesInSet: Int = DEFAULT_MAX_VOICE_TAKES_IN_SET
    ): List<SavedVoiceClip> {
        if (!dir.exists()) dir.mkdirs()
        val src = File(sourceWavPath)
        val existing = loadClipsInDir(dir).toMutableList()
        if (!src.exists() || src.length() <= 44L) {
            return existing
        }

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
                estimatedWpm = if (estimatedWpm > 0) estimatedWpm else current.estimatedWpm
            )
            writeIndex(dir, existing)
            return existing
        }

        val limit = maxTakesInSet.coerceIn(2, 3)
        // Evict oldest clip(s) first (FIFO ring buffer: 4th take overrides the 1st)
        while (existing.size >= limit) {
            val oldest = existing.removeAt(0)
            runCatching { File(oldest.filePath).delete() }
        }

        val nextTakeNum = (existing.maxOfOrNull { it.takeNumber } ?: 0) + 1
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
            isFinal = false
        )
        existing.add(newClip)
        writeIndex(dir, existing)
        return existing
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
     * Marks [finalClipId] as the final chosen take and deletes every other draft clip from disk,
     * leaving only that single chosen recording on device.
     */
    fun keepOnlyFinalClip(context: Context, finalClipId: String): List<SavedVoiceClip> =
        keepOnlyFinalClipInDir(getRecordingsDir(context), finalClipId)

    fun keepOnlyFinalClipInDir(dir: File, finalClipId: String): List<SavedVoiceClip> {
        val existing = loadClipsInDir(dir)
        val chosen = existing.firstOrNull { it.id == finalClipId } ?: return existing
        existing.forEach { clip ->
            if (clip.id != chosen.id) {
                runCatching { File(clip.filePath).delete() }
            }
        }
        val finalized = listOf(chosen.copy(isFinal = true))
        writeIndex(dir, finalized)
        return finalized
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

@Stable
class VoiceAudioPlayer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null

    var activeFilePath by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var currentPositionMs by mutableLongStateOf(0L)
        private set

    var totalDurationMs by mutableLongStateOf(0L)
        private set

    var playbackError by mutableStateOf<String?>(null)
        private set

    private val progressTicker = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (mp != null && isPlaying) {
                runCatching {
                    currentPositionMs = mp.currentPosition.toLong().coerceAtLeast(0L)
                }
                mainHandler.postDelayed(this, 120L)
            }
        }
    }

    fun playOrPause(filePath: String) {
        playbackError = null
        val file = File(filePath)
        if (!file.exists() || file.length() <= 44L) {
            playbackError = "Recorded audio file is unavailable."
            return
        }

        val current = mediaPlayer
        if (current != null && activeFilePath == filePath) {
            runCatching {
                if (current.isPlaying) {
                    current.pause()
                    isPlaying = false
                    mainHandler.removeCallbacks(progressTicker)
                } else {
                    if (currentPositionMs >= (totalDurationMs - 150L).coerceAtLeast(0L) && totalDurationMs > 0L) {
                        current.seekTo(0)
                        currentPositionMs = 0L
                    }
                    current.start()
                    isPlaying = true
                    mainHandler.post(progressTicker)
                }
            }.onFailure {
                stopAndRelease()
                startFreshPlayback(filePath)
            }
            return
        }

        stopAndRelease()
        startFreshPlayback(filePath)
    }

    private fun startFreshPlayback(filePath: String) {
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
                        this@VoiceAudioPlayer.isPlaying = false
                        this@VoiceAudioPlayer.currentPositionMs = this@VoiceAudioPlayer.totalDurationMs
                        mainHandler.removeCallbacks(progressTicker)
                    }
                }
                setOnErrorListener { _, _, _ ->
                    mainHandler.post {
                        this@VoiceAudioPlayer.playbackError = "Unable to play this local audio clip."
                        stopAndRelease()
                    }
                    true
                }
                prepare()
            }
            mediaPlayer = mp
            activeFilePath = filePath
            totalDurationMs = mp.duration.toLong().coerceAtLeast(0L)
            currentPositionMs = 0L
            mp.start()
            isPlaying = true
            mainHandler.post(progressTicker)
        }.onFailure { err ->
            Log.e(TAG_AUDIO_STORE, "Local playback failed", err)
            playbackError = "Audio playback could not start for this clip."
            stopAndRelease()
        }
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
        mainHandler.removeCallbacks(progressTicker)
        runCatching {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        mediaPlayer = null
        isPlaying = false
        currentPositionMs = 0L
        totalDurationMs = 0L
        activeFilePath = null
    }
}

@Composable
fun rememberVoiceAudioPlayer(): VoiceAudioPlayer {
    val context = LocalContext.current
    val player = remember(context) { VoiceAudioPlayer() }
    DisposableEffect(player) {
        onDispose {
            player.stopAndRelease()
        }
    }
    return player
}
