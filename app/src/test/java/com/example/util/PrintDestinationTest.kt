package com.example.util

import com.example.model.ExamConfig
import com.example.model.PaperMetadata
import com.example.model.QuestionPaper
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PrintDestinationTest {

    @Test
    fun resolveExportDestination_resolvesExactYearMonthSubjectAndFileNameForPdfAndCbtHtml() {
        val calendar = Calendar.getInstance(Locale.US).apply {
            set(2026, Calendar.NOVEMBER, 26, 9, 30, 0)
        }
        val fixedDate = calendar.time

        val paper = QuestionPaper(
            metadata = PaperMetadata(
                title = "Organic Mechanisms",
                subject = "Organic Chemistry",
                rollNumber = "202600042",
                bookletNumber = "770123"
            ),
            questions = emptyList()
        )
        val config = ExamConfig(
            subject = "Fallback Subject",
            rollNumber = "202600042",
            bookletNumber = "770123"
        )

        val previousLocale = Locale.getDefault()
        try {
            // Ensure Arabic-Indic or non-ASCII default locale digits never corrupt folder paths
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))

            val pdfDest = PrintDocumentHelper.resolveExportDestination(
                paper = paper,
                config = config,
                format = ExamExportFormat.PDF,
                date = fixedDate
            )
            assertEquals("Documents/exam_set/", pdfDest.rootDirectory)
            assertEquals("2026", pdfDest.yearFolder)
            assertEquals("11", pdfDest.monthNumberFolder)
            assertEquals("November", pdfDest.monthDisplayName)
            assertEquals("11 (November)", pdfDest.monthFolderLabel)
            assertEquals("Organic_Chemistry", pdfDest.subjectFolder)
            assertEquals("Organic_Chemistry_202600042_770123.pdf", pdfDest.fileName)
            assertEquals(
                "Documents/exam_set/2026/11/Organic_Chemistry/Organic_Chemistry_202600042_770123.pdf",
                pdfDest.fullRelativePath
            )

            val htmlDest = PrintDocumentHelper.resolveExportDestination(
                paper = paper,
                config = config,
                format = ExamExportFormat.CBT_HTML,
                date = fixedDate
            )
            assertEquals("2026", htmlDest.yearFolder)
            assertEquals("11 (November)", htmlDest.monthFolderLabel)
            assertEquals("Organic_Chemistry_202600042_770123_CBT.html", htmlDest.fileName)
            assertEquals(
                "Documents/exam_set/2026/11/Organic_Chemistry/Organic_Chemistry_202600042_770123_CBT.html",
                htmlDest.fullRelativePath
            )
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
