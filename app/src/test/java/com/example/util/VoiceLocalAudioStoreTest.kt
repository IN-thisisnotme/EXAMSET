package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceLocalAudioStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createValidWav(dir: File, name: String, pcmBytes: ByteArray = ByteArray(640) { 1 }): File {
        val f = File(dir, name)
        val writer = WavSessionWriter(f)
        assertTrue(writer.openOrResume())
        writer.writePcm(pcmBytes, pcmBytes.size)
        val written = writer.closeAndFinalize()
        assertEquals(pcmBytes.size.toLong(), written)
        return f
    }

    @Test
    fun wavSessionWriter_writesValidRiffHeaderAcrossResumePasses() {
        val dir = tempFolder.newFolder("wav_test")
        val wavFile = File(dir, "session.wav")
        val writer = WavSessionWriter(wavFile)
        assertTrue(writer.openOrResume())
        writer.writePcm(ByteArray(320) { 2 }, 320)
        writer.closeAndFinalize()

        // Resume writing on the same WAV (simulates Pause -> Resume -> Finish)
        assertTrue(writer.openOrResume())
        writer.writePcm(ByteArray(480) { 3 }, 480)
        val totalDataBytes = writer.closeAndFinalize()

        assertEquals(800L, totalDataBytes)
        assertEquals(844L, wavFile.length())

        RandomAccessFile(wavFile, "r").use { raf ->
            val header = ByteArray(44)
            raf.readFully(header)
            assertEquals("RIFF", String(header, 0, 4))
            assertEquals("WAVE", String(header, 8, 4))
            assertEquals("data", String(header, 36, 4))
            val riffChunkSize = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val dataSubchunkSize = ByteBuffer.wrap(header, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
            assertEquals(836, riffChunkSize)
            assertEquals(800, dataSubchunkSize)
        }
    }

    @Test
    fun addTakeToRollingSetInDir_capsAtThreeTakesAndEvictsOldestTakeFirst() {
        val dir = tempFolder.newFolder("rolling_takes")
        val f1 = createValidWav(dir, "take1.wav")
        val f2 = createValidWav(dir, "take2.wav")
        val f3 = createValidWav(dir, "take3.wav")
        val f4 = createValidWav(dir, "take4.wav")

        VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, f1.absolutePath, "p1", "Prompt 1", "English (US)", 4000L, "first")
        Thread.sleep(5L)
        VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, f2.absolutePath, "p1", "Prompt 1", "English (US)", 5000L, "second")
        Thread.sleep(5L)
        val threeClips = VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, f3.absolutePath, "p1", "Prompt 1", "English (US)", 6000L, "third")

        assertEquals(3, threeClips.size)
        assertEquals(listOf(1, 2, 3), threeClips.map { it.takeNumber })
        assertTrue(f1.exists())

        Thread.sleep(5L)
        val afterFourth = VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, f4.absolutePath, "p1", "Prompt 1", "English (US)", 7000L, "fourth")
        assertEquals(3, afterFourth.size)
        assertEquals(listOf(2, 3, 4), afterFourth.map { it.takeNumber })
        assertFalse("Oldest take #1 should be deleted when 4th is recorded", f1.exists())
        assertTrue(f2.exists() && f3.exists() && f4.exists())
    }

    @Test
    fun keepOnlyFinalClipInDir_retainsOnlySelectedTakeAndDeletesDrafts() {
        val dir = tempFolder.newFolder("keep_final")
        val f1 = createValidWav(dir, "take1.wav")
        val f2 = createValidWav(dir, "take2.wav")
        val f3 = createValidWav(dir, "take3.wav")

        VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, f1.absolutePath, "p1", "Prompt 1", "Hindi (hi-IN)", 4000L)
        Thread.sleep(5L)
        val clips2 = VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, f2.absolutePath, "p1", "Prompt 1", "Hindi (hi-IN)", 5000L)
        Thread.sleep(5L)
        VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, f3.absolutePath, "p1", "Prompt 1", "Hindi (hi-IN)", 6000L)

        val secondTakeId = clips2[1].id
        val remaining = VoiceLocalAudioStore.keepOnlyFinalClipInDir(dir, secondTakeId)

        assertEquals(1, remaining.size)
        assertEquals(secondTakeId, remaining.first().id)
        assertTrue(remaining.first().isFinal)
        assertFalse(f1.exists())
        assertTrue(f2.exists())
        assertFalse(f3.exists())
    }
}
