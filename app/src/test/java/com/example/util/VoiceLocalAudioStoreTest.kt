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

    @Test
    fun deriveStackKey_groupsNoteBlocksTogetherAndSeparatesPapers() {
        assertEquals("note_42", VoiceLocalAudioStore.deriveStackKey("note_42_0", "Bill A"))
        assertEquals("note_42", VoiceLocalAudioStore.deriveStackKey("note_42_3", "Bill B"))
        assertEquals("paper_7_1", VoiceLocalAudioStore.deriveStackKey("paper_7_1", "Quiz Q1"))
        assertEquals("my_custom_note", VoiceLocalAudioStore.deriveStackKey("", "  My Custom   Note "))
    }

    @Test
    fun versionStacks_isolateNotesAndProtectFinalTakeDuringRollingEviction() {
        val dir = tempFolder.newFolder("version_stacks_isolation")
        val a1 = createValidWav(dir, "a1.wav")
        val a2 = createValidWav(dir, "a2.wav")
        val a3 = createValidWav(dir, "a3.wav")
        val a4 = createValidWav(dir, "a4.wav")
        val b1 = createValidWav(dir, "b1.wav")
        val b2 = createValidWav(dir, "b2.wav")

        // Record 3 takes for Note A (note_10_0 / note_10_1 share stackKey note_10)
        VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, a1.absolutePath, "note_10_0", "Note A", "English", 3000L, "v1")
        Thread.sleep(5L)
        VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, a2.absolutePath, "note_10_1", "Note A", "English", 3200L, "v2")
        Thread.sleep(5L)
        val afterA3 = VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, a3.absolutePath, "note_10_0", "Note A", "English", 3400L, "v3")

        // Record 2 takes for Note B (note_20_0)
        Thread.sleep(5L)
        VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, b1.absolutePath, "note_20_0", "Note B", "English", 2500L, "b1")
        Thread.sleep(5L)
        VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, b2.absolutePath, "note_20_0", "Note B", "English", 2600L, "b2")

        // Mark Note A take #1 as Final (should be protected when take #4 arrives)
        val a1Id = afterA3.first { it.filePath == a1.absolutePath }.id
        val markedFinal = VoiceLocalAudioStore.setFinalClipInDir(dir, a1Id)
        val noteAClipsAfterMark = markedFinal.filter { it.effectiveStackKey() == "note_10" }
        assertEquals(3, noteAClipsAfterMark.size)
        assertTrue(noteAClipsAfterMark.first { it.id == a1Id }.isFinal)

        // Add take #4 to Note A -> evicts oldest NON-final draft (a2), keeps a1 (Final), a3, a4, and leaves Note B intact
        Thread.sleep(5L)
        val afterA4 = VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, a4.absolutePath, "note_10_0", "Note A", "English", 3800L, "v4")
        val stackA = afterA4.filter { it.effectiveStackKey() == "note_10" }
        val stackB = afterA4.filter { it.effectiveStackKey() == "note_20" }

        assertEquals(3, stackA.size)
        assertEquals(2, stackB.size)
        assertTrue("Final clip a1 must be protected from eviction", a1.exists())
        assertFalse("Oldest non-final draft a2 should be evicted", a2.exists())
        assertTrue(a3.exists() && a4.exists() && b1.exists() && b2.exists())
    }

    @Test
    fun setFinalAndClearDraftsAndDeleteStack_operateOnlyWithinTargetStack() {
        val dir = tempFolder.newFolder("stack_lifecycle")
        val a1 = createValidWav(dir, "a1.wav")
        val a2 = createValidWav(dir, "a2.wav")
        val b1 = createValidWav(dir, "b1.wav")

        VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, a1.absolutePath, "note_1_0", "Alpha", "English", 2000L)
        Thread.sleep(5L)
        val clips2 = VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, a2.absolutePath, "note_1_0", "Alpha", "English", 2200L)
        Thread.sleep(5L)
        val allClips = VoiceLocalAudioStore.addTakeToRollingSetInDir(dir, b1.absolutePath, "note_2_0", "Beta", "English", 2400L)

        val a1Id = allClips.first { it.filePath == a1.absolutePath }.id
        val a2Id = clips2.first { it.filePath == a2.absolutePath }.id

        // Toggle Final from a1 -> a2 non-destructively (all WAVs stay on disk)
        VoiceLocalAudioStore.setFinalClipInDir(dir, a1Id)
        val switched = VoiceLocalAudioStore.setFinalClipInDir(dir, a2Id)
        val switchedA = switched.filter { it.effectiveStackKey() == "note_1" }
        assertFalse(switchedA.first { it.id == a1Id }.isFinal)
        assertTrue(switchedA.first { it.id == a2Id }.isFinal)
        assertTrue(a1.exists() && a2.exists() && b1.exists())

        // Clear non-final drafts in note_1 -> deletes a1, preserves a2 and note_2 (b1)
        val afterClearDrafts = VoiceLocalAudioStore.clearNonFinalDraftsInDir(dir, "note_1")
        assertEquals(2, afterClearDrafts.size)
        assertFalse(a1.exists())
        assertTrue(a2.exists() && b1.exists())

        // Delete note_1 stack -> deletes a2, preserves note_2 (b1)
        val afterDeleteStack = VoiceLocalAudioStore.deleteStackInDir(dir, "note_1")
        assertEquals(1, afterDeleteStack.size)
        assertEquals("note_2", afterDeleteStack.first().effectiveStackKey())
        assertFalse(a2.exists())
        assertTrue(b1.exists())
    }

    @Test
    fun voiceStackPlaybackQueue_ordersAndNavigatesVersionsSequentially() {
        val c1 = SavedVoiceClip("id1", 1, "/tmp/1.wav", "note_1_0", "T", "English", 2000L, 100L, 100L)
        val c2 = SavedVoiceClip("id2", 2, "/tmp/2.wav", "note_1_0", "T", "English", 2500L, 100L, 200L)
        val c3 = SavedVoiceClip("id3", 3, "/tmp/3.wav", "note_1_0", "T", "English", 3000L, 100L, 300L)

        val ordered = VoiceStackPlaybackQueue.orderedStackClips(listOf(c3, c1, c2))
        assertEquals(listOf("id1", "id2", "id3"), ordered.map { it.id })
        assertEquals(0, VoiceStackPlaybackQueue.resolveStartIndex(ordered, null))
        assertEquals(1, VoiceStackPlaybackQueue.resolveStartIndex(ordered, "id2"))
        assertEquals("id2", VoiceStackPlaybackQueue.nextClip(ordered, "id1")?.id)
        assertEquals(null, VoiceStackPlaybackQueue.nextClip(ordered, "id3"))
        assertEquals("id1", VoiceStackPlaybackQueue.previousClip(ordered, "id2")?.id)
        assertEquals(null, VoiceStackPlaybackQueue.previousClip(ordered, "id1"))
    }
}

