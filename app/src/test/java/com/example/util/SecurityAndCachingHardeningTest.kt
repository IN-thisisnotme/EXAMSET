package com.example.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ExamSetPreferences
import com.example.data.PaperEntity
import com.example.data.ThemePreferences
import com.example.model.ExamConfig
import com.example.model.McqQuestion
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import com.example.model.RestoreMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityAndCachingHardeningTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var appPrefs: ExamSetPreferences
    private lateinit var themePrefs: ThemePreferences
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appPrefs = ExamSetPreferences(context)
        themePrefs = ThemePreferences(context)
        tempDir = File(context.cacheDir, "hardening_test_${System.currentTimeMillis()}").apply { mkdirs() }
        McqParser.clearParseCache()
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
        McqParser.clearParseCache()
    }

    @Test
    fun generateInteractiveExamHtml_escapesScriptBreakoutsAndAvoidsRawInnerHtmlFallbacks() {
        val maliciousStem = "What prints </script><script>alert('xss')</script> <!-- comment -->?"
        val paper = QuestionPaper(
            id = 1L,
            metadata = PaperMetadata(title = "XSS Audit </script>", subject = "Security"),
            questions = listOf(
                McqQuestion(
                    num = 1,
                    question = maliciousStem,
                    options = QuestionOptions(
                        a = "<img src=x onerror=alert(1)>",
                        b = "</SCRIPT><script>evil()</script>",
                        c = "Safe Option",
                        d = "Another Option"
                    ),
                    correctAnswer = "C",
                    explanation = "</script> explanation"
                )
            )
        )

        val html = PrintDocumentHelper.generateInteractiveExamHtml(paper, ExamConfig())
        val scriptStart = html.indexOf("const EXAM_DATA = ")
        assertTrue("Generated HTML must embed EXAM_DATA", scriptStart >= 0)
        val scriptEnd = html.indexOf(";\n", scriptStart)
        val embeddedJson = html.substring(scriptStart, scriptEnd)

        assertFalse(
            "Inline EXAM_DATA JSON must escape closing script tags",
            embeddedJson.contains("</script>", ignoreCase = true)
        )
        assertFalse(
            "Inline EXAM_DATA JSON must escape HTML comment openers",
            embeddedJson.contains("<!--")
        )
        assertFalse(
            "CBT template must not assign unescaped stemRaw into innerHTML",
            html.contains(".innerHTML = q.stemHtml || q.stemRaw")
        )
        assertFalse(
            "CBT template must not assign unescaped opt.text into innerHTML",
            html.contains(".innerHTML = opt.rendered || opt.text")
        )
    }

    @Test
    fun vaultRestore_rejectsCorruptedPreferencesJsonBeforeMutatingExistingDatabase() = runBlocking {
        db.paperDao().insertPaper(
            PaperEntity(
                title = "Existing Paper",
                subject = "Physics",
                questionCount = 1,
                answerKeyCount = 1,
                explanationCount = 0,
                rawText = "1. Unit of force?\n(A) Newton\n(B) Joule\n(C) Watt\n(D) Pascal\nAnswer: A"
            )
        )
        val validZip = File(tempDir, "valid.examset.zip")
        VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, validZip)

        // Corrupt preferences.json while keeping a valid zip container
        val brokenPrefsZip = File(tempDir, "broken_prefs.examset.zip")
        ZipFile(validZip).use { src ->
            ZipOutputStream(brokenPrefsZip.outputStream()).use { zos ->
                src.entries().asSequence().forEach { entry ->
                    zos.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "preferences.json") {
                        zos.write("{not-valid-json".toByteArray(Charsets.UTF_8))
                    } else {
                        zos.write(src.getInputStream(entry).readBytes())
                    }
                    zos.closeEntry()
                }
            }
        }

        val preview = VaultArchiveEngine.inspectLocalFile(brokenPrefsZip)
        assertFalse(preview.checksumVerified)

        try {
            VaultArchiveEngine.restoreFromPreview(
                context = context,
                db = db,
                appPrefs = appPrefs,
                themePrefs = themePrefs,
                preview = preview,
                mode = RestoreMode.FULL_REPLACE
            )
            fail("Expected IllegalStateException for corrupted preferences.json")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message?.contains("preferences.json") == true)
        }

        // Existing paper must remain untouched because parsing happened before destructive restore
        val papersAfterFailure = db.paperDao().getAllPapersOnce()
        assertEquals(1, papersAfterFailure.size)
        assertEquals("Existing Paper", papersAfterFailure.first().title)
    }

    @Test
    fun vaultInspect_rejectsOversizedZipEntryExceedingBound() {
        val validZip = File(tempDir, "base.examset.zip")
        runBlocking {
            VaultArchiveEngine.createVaultArchive(context, db, appPrefs, themePrefs, validZip)
        }

        val oversizedBytes = VaultArchiveEngine.MAX_ENTRY_UNCOMPRESSED_BYTES + 1024L
        val zipBombFile = File(tempDir, "oversized_entry.examset.zip")
        ZipFile(validZip).use { src ->
            ZipOutputStream(zipBombFile.outputStream()).use { zos ->
                src.entries().asSequence().forEach { entry ->
                    zos.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "notes.ndjson") {
                        val chunk = ByteArray(64 * 1024)
                        var remaining = oversizedBytes
                        while (remaining > 0L) {
                            val toWrite = minOf(remaining, chunk.size.toLong()).toInt()
                            zos.write(chunk, 0, toWrite)
                            remaining -= toWrite
                        }
                    } else {
                        zos.write(src.getInputStream(entry).readBytes())
                    }
                    zos.closeEntry()
                }
            }
        }

        try {
            VaultArchiveEngine.inspectLocalFile(zipBombFile)
            fail("Expected IllegalStateException when inspecting oversized ZIP entry")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message?.contains("exceeds the maximum allowed uncompressed size") == true)
        }
    }

    @Test
    fun mcqParser_parseWithResult_cachesIdenticalInputsSafely() {
        val raw = """
            # Caching Test
            1. What is the chemical symbol for water?
            (A) H2O
            (B) CO2
            (C) O2
            (D) N2
            Answer: A
        """.trimIndent()

        val first = McqParser.parseWithResult(raw)
        val second = McqParser.parseWithResult(raw)
        assertSame("Repeated parseWithResult for identical rawText should reuse cached ParseResult", first, second)

        McqParser.clearParseCache()
        val third = McqParser.parseWithResult(raw)
        assertEquals(first.paper.questions, third.paper.questions)
    }
}
