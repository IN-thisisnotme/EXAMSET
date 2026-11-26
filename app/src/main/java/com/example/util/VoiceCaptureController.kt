package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.sqrt

private const val TAG = "VoiceCapture"
private const val SAMPLE_RATE_HZ = 16_000
private const val MAX_RECORDING_MS = 180_000L

@Stable
class VoiceCaptureController(context: Context) {
    private val appContext = context.applicationContext ?: context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val cacheFolder = File(appContext.cacheDir, "voice_practice").apply { mkdirs() }
    private var activeRecordingFile: File? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var mediaPlayer: MediaPlayer? = null
    private var activeFocusRequest: AudioFocusRequest? = null

    @Volatile
    private var recordingActiveFlag = false
    private var recordStartTimeMs = 0L

    var isRecording by mutableStateOf(false)
        private set

    var isPlayingBack by mutableStateOf(false)
        private set

    var elapsedMs by mutableLongStateOf(0L)
        private set

    var playbackPositionMs by mutableLongStateOf(0L)
        private set

    var liveRmsLevel by mutableFloatStateOf(0f)
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    val rmsHistory = mutableStateListOf<Float>()

    private val playbackProgressTicker = object : Runnable {
        override fun run() {
            val player = mediaPlayer
            if (isPlayingBack && player != null) {
                try {
                    playbackPositionMs = player.currentPosition.toLong().coerceAtLeast(0L)
                    mainHandler.postDelayed(this, 80L)
                } catch (_: Exception) {
                    isPlayingBack = false
                }
            }
        }
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            mainHandler.post {
                if (isPlayingBack) stopPlayback()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        stopPlayback()
        stopRecordingInternal(discard = true)
        lastError = null

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufSize <= 0) {
            lastError = "Audio recording hardware is unavailable on this device."
            return false
        }

        val bufferSize = maxOf(minBufSize * 2, SAMPLE_RATE_HZ / 5 * 2)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission missing", e)
            lastError = "Microphone permission is required to record your voice."
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord", e)
            lastError = e.localizedMessage ?: "Unable to initialize microphone."
            return false
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            lastError = "Microphone could not be initialized."
            return false
        }

        cleanOldTempFiles()
        val targetFile = File(cacheFolder, "attempt_${System.currentTimeMillis()}.wav")
        activeRecordingFile = targetFile
        rmsHistory.clear()
        elapsedMs = 0L
        liveRmsLevel = 0f

        requestAudioFocus()
        return try {
            recorder.startRecording()
            audioRecord = recorder
            recordingActiveFlag = true
            isRecording = true
            recordStartTimeMs = System.currentTimeMillis()

            recordingThread = Thread({
                writeWavStream(recorder, targetFile, bufferSize)
            }, "ExamSetVoiceRecorder").apply { start() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            recorder.release()
            audioRecord = null
            recordingActiveFlag = false
            isRecording = false
            abandonAudioFocus()
            lastError = e.localizedMessage ?: "Could not start recording."
            false
        }
    }

    fun stopRecording(): File? {
        stopRecordingInternal(discard = false)
        return activeRecordingFile?.takeIf { it.exists() && it.length() > 44L }
    }

    fun readRecordedAudioBytes(): ByteArray? {
        val file = activeRecordingFile ?: return null
        if (!file.exists() || file.length() <= 44L) return null
        return try {
            file.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read recorded audio bytes", e)
            null
        }
    }

    fun hasRecording(): Boolean {
        val file = activeRecordingFile
        return file != null && file.exists() && file.length() > 44L
    }

    fun togglePlayback() {
        if (isPlayingBack) {
            stopPlayback()
            return
        }
        val file = activeRecordingFile ?: return
        if (!file.exists() || file.length() <= 44L) return

        stopRecordingInternal(discard = false)
        requestAudioFocus()
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    mainHandler.post {
                        isPlayingBack = false
                        playbackPositionMs = 0L
                        mainHandler.removeCallbacks(playbackProgressTicker)
                        abandonAudioFocus()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    mainHandler.post {
                        isPlayingBack = false
                        playbackPositionMs = 0L
                        mainHandler.removeCallbacks(playbackProgressTicker)
                        abandonAudioFocus()
                    }
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            isPlayingBack = true
            playbackPositionMs = 0L
            mainHandler.post(playbackProgressTicker)
        } catch (e: Exception) {
            Log.e(TAG, "Playback failed", e)
            lastError = e.localizedMessage ?: "Unable to play recorded audio."
            stopPlayback()
        }
    }

    fun stopPlayback() {
        mainHandler.removeCallbacks(playbackProgressTicker)
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        isPlayingBack = false
        playbackPositionMs = 0L
        abandonAudioFocus()
    }

    fun clearRecording() {
        stopPlayback()
        stopRecordingInternal(discard = true)
        rmsHistory.clear()
        elapsedMs = 0L
        liveRmsLevel = 0f
        lastError = null
    }

    private fun stopRecordingInternal(discard: Boolean) {
        recordingActiveFlag = false
        val thread = recordingThread
        recordingThread = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            thread?.join(600L)
        } catch (_: InterruptedException) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        isRecording = false
        liveRmsLevel = 0f
        abandonAudioFocus()

        if (discard) {
            activeRecordingFile?.delete()
            activeRecordingFile = null
        }
    }

    private fun writeWavStream(
        recorder: AudioRecord,
        outFile: File,
        bufferSizeBytes: Int
    ) {
        val pcmSamples = ShortArray(bufferSizeBytes / 2)
        val byteScratch = ByteArray(bufferSizeBytes)
        var totalAudioBytes = 0L
        var lastSamplePushMs = 0L

        try {
            FileOutputStream(outFile).use { fos ->
                fos.write(ByteArray(44))
                while (recordingActiveFlag) {
                    val readCount = recorder.read(pcmSamples, 0, pcmSamples.size)
                    if (readCount <= 0) continue

                    var sumSq = 0.0
                    var byteIdx = 0
                    for (i in 0 until readCount) {
                        val s = pcmSamples[i]
                        sumSq += s.toDouble() * s.toDouble()
                        byteScratch[byteIdx++] = (s.toInt() and 0xFF).toByte()
                        byteScratch[byteIdx++] = ((s.toInt() shr 8) and 0xFF).toByte()
                    }
                    fos.write(byteScratch, 0, byteIdx)
                    totalAudioBytes += byteIdx

                    val rms = (sqrt(sumSq / readCount) / 32768.0).toFloat().coerceIn(0f, 1f)
                    val now = System.currentTimeMillis()
                    val currentElapsed = (now - recordStartTimeMs).coerceAtLeast(0L)

                    if (now - lastSamplePushMs >= 95L) {
                        lastSamplePushMs = now
                        mainHandler.post {
                            if (recordingActiveFlag) {
                                liveRmsLevel = (rms * 3.2f).coerceIn(0f, 1f)
                                elapsedMs = currentElapsed
                                rmsHistory.add(rms)
                            }
                        }
                    }

                    if (currentElapsed >= MAX_RECORDING_MS) {
                        mainHandler.post { stopRecording() }
                        break
                    }
                }
            }
            if (outFile.exists() && totalAudioBytes > 0L) {
                patchWavHeader(outFile, totalAudioBytes)
            } else {
                outFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio write loop failed", e)
            mainHandler.post {
                lastError = e.localizedMessage ?: "Audio capture error."
            }
        }
    }

    private fun patchWavHeader(file: File, totalAudioBytes: Long) {
        val totalDataLen = totalAudioBytes + 36L
        val byteRate = SAMPLE_RATE_HZ * 2L
        val header = ByteArrayOutputStream(44).apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            writeIntLe(totalDataLen.toInt())
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write("fmt ".toByteArray(Charsets.US_ASCII))
            writeIntLe(16)
            writeShortLe(1) // PCM
            writeShortLe(1) // Mono
            writeIntLe(SAMPLE_RATE_HZ)
            writeIntLe(byteRate.toInt())
            writeShortLe(2) // Block align
            writeShortLe(16) // Bits per sample
            write("data".toByteArray(Charsets.US_ASCII))
            writeIntLe(totalAudioBytes.toInt())
        }.toByteArray()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }

    private fun ByteArrayOutputStream.writeIntLe(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF)
        write((v shr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeShortLe(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
    }

    private fun cleanOldTempFiles() {
        cacheFolder.listFiles()?.forEach { f ->
            if (f != activeRecordingFile) {
                f.delete()
            }
        }
    }

    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                activeFocusRequest = req
                manager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
                activeFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(audioFocusListener)
            }
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        stopPlayback()
        stopRecordingInternal(discard = true)
        cleanOldTempFiles()
    }
}

@Composable
fun rememberVoiceCaptureController(): VoiceCaptureController {
    val context = LocalContext.current
    val controller = remember(context) { VoiceCaptureController(context) }
    DisposableEffect(controller) {
        onDispose {
            controller.shutdown()
        }
    }
    return controller
}
