package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.model.ExamConfig
import com.example.model.OmrBubblingMode
import com.example.model.OmrGeometry
import com.example.model.PrintMode
import com.example.model.QuestionPaper
import com.example.util.ExamNumberGenerator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PrintDocumentHelper {

    /**
     * Sanitizes a string to be safely used in a directory or file name,
     * converting spaces and invalid characters to underscores.
     */
    fun sanitizeForFileName(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return "Unknown"
        return trimmed.replace(Regex("[^a-zA-Z0-9.-]"), "_").replace(Regex("_+"), "_")
    }

    /**
     * Computes the formatted PDF filename: {subject}_{rollNumber}_{bookletNumber}.pdf
     * with spaces replaced by underscores.
     */
    fun getExamPdfFileName(paper: QuestionPaper, config: ExamConfig): String {
        val rawSubject = paper.metadata.subject.ifBlank { config.subject.ifBlank { "Exam" } }
        val subjectClean = sanitizeForFileName(rawSubject)

        val rollClean = sanitizeForFileName(
            config.rollNumber.ifBlank { paper.metadata.rollNumber.ifBlank { "0000000001" } }
        )
        val bookletClean = sanitizeForFileName(
            config.bookletNumber.ifBlank { paper.metadata.bookletNumber.ifBlank { "0000001" } }
        )

        return "${subjectClean}_${rollClean}_${bookletClean}.pdf"
    }

    /**
     * Triggers the native Android Print dialog using WebView and PrintDocumentAdapter.
     * Supports saving as PDF and printing directly to wireless printers.
     */
    fun printQuestionPaper(
        context: Context,
        paper: QuestionPaper,
        config: ExamConfig
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = getExamPdfFileName(paper, config).removeSuffix(".pdf")
        val html = generatePrintHtml(paper, config)

        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                val printAttributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager.print(jobName, printAdapter, printAttributes)
            }
        }

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    /**
     * Saves the question paper directly as a PDF file into the device file manager
     * under: Documents/Exam_Set/{YYYY}/{MM}/{Subject}/
     * File name: {Subject}_{RollNo}_{BookletNo}.pdf
     * Without opening or relying on the Android Print Spooler.
     */
    fun savePdfDirectly(
        context: Context,
        paper: QuestionPaper,
        config: ExamConfig,
        onSuccess: (savedPath: String) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        val fileName = getExamPdfFileName(paper, config)
        val html = generatePrintHtml(paper, config)

        val yearStr = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
        val monthStr = SimpleDateFormat("MM", Locale.getDefault()).format(Date())
        val subjectClean = sanitizeForFileName(
            paper.metadata.subject.ifBlank { config.subject.ifBlank { "General" } }
        )

        val relativeSubDir = "exam_set/$yearStr/$monthStr/$subjectClean"

        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                try {
                    val adapter = webView.createPrintDocumentAdapter(fileName.removeSuffix(".pdf"))
                    val attributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                        .build()

                    val tempPdfFile = File(context.cacheDir, "temp_render_${System.currentTimeMillis()}.pdf")

                    android.print.PdfPrintHelper.printAdapterToFile(
                        adapter = adapter,
                        attributes = attributes,
                        destinationFile = tempPdfFile,
                        onSuccess = {
                            try {
                                val finalPath = exportTempFileToExamSetDirectory(
                                    context = context,
                                    tempFile = tempPdfFile,
                                    relativeSubDir = relativeSubDir,
                                    fileName = fileName
                                )
                                tempPdfFile.delete()
                                onSuccess(finalPath)
                            } catch (e: Exception) {
                                tempPdfFile.delete()
                                onError("Failed to export PDF: ${e.localizedMessage ?: "Unknown error"}")
                            }
                        },
                        onError = { error ->
                            tempPdfFile.delete()
                            onError("Failed to render PDF: $error")
                        }
                    )
                } catch (e: Exception) {
                    onError("Failed to initiate PDF generator: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
        }

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun exportTempFileToExamSetDirectory(
        context: Context,
        tempFile: File,
        relativeSubDir: String,
        fileName: String
    ): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$relativeSubDir")
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)
                ?: throw IllegalStateException("Unable to allocate MediaStore entry for PDF")

            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(tempFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalStateException("Could not write to MediaStore PDF output stream")

            "Documents/$relativeSubDir/$fileName"
        } else {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val targetDir = File(docsDir, relativeSubDir)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val targetFile = File(targetDir, fileName)
            FileInputStream(tempFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.absolutePath
        }
    }

    /**
     * Generates complete, highly styled HTML designed specifically for A4 printing.
     */
    fun generatePrintHtml(paper: QuestionPaper, config: ExamConfig): String {
        return when (config.printMode) {
            PrintMode.STUDENT_PAPER -> generateStudentExamHtml(paper, config)
            PrintMode.OMR_ONLY -> generateStandaloneOmrHtml(paper, config)
            PrintMode.MASTER_BOOKLET -> generateMasterBookletHtml(paper, config)
            PrintMode.SOLUTIONS_ONLY -> generateSolutionsOnlyHtml(paper, config)
        }
    }

    /**
     * Formats an authentic student examination question paper.
     */
    private fun generateStudentExamHtml(paper: QuestionPaper, config: ExamConfig): String {
        val questionsHtml = buildString {
            for (q in paper.questions) {
                val isAr = QuestionFormatter.isAssertionReason(q.question, q.options, q.questionType)
                val ar = QuestionFormatter.parseAssertionReason(q.question)
                val stacked = QuestionFormatter.shouldStackOptions(q)
                val containerClass = if (stacked) "options-stack" else "options-grid"

                val stemHtml = if (ar != null) {
                    """
                    <div class="ar-print-block">
                        ${if (!ar.leadIn.isNullOrBlank()) "<div class=\"ar-print-leadin\">${escapeRichHtml(ar.leadIn)}</div>" else ""}
                        <div class="ar-print-statement"><span class="ar-print-label">Assertion (A):</span> ${escapeRichHtml(ar.assertion)}</div>
                        <div class="ar-print-statement"><span class="ar-print-label">Reason (R):</span> ${escapeRichHtml(ar.reason)}</div>
                        ${if (!ar.leadOut.isNullOrBlank()) "<div class=\"ar-print-leadout\">${escapeRichHtml(ar.leadOut)}</div>" else ""}
                    </div>
                    """.trimIndent()
                } else {
                    escapeRichHtml(normalizeQuestionSpacing(q.question))
                }

                append("""
                <div class="student-q-item">
                    <div class="q-head"><strong>${q.num}.</strong> $stemHtml</div>
                    <div class="$containerClass">
                        <div class="opt-cell"><span class="opt-key">(A)</span><span class="opt-text">${escapeRichHtml(q.options.a)}</span></div>
                        <div class="opt-cell"><span class="opt-key">(B)</span><span class="opt-text">${escapeRichHtml(q.options.b)}</span></div>
                        <div class="opt-cell"><span class="opt-key">(C)</span><span class="opt-text">${escapeRichHtml(q.options.c)}</span></div>
                        <div class="opt-cell"><span class="opt-key">(D)</span><span class="opt-text">${escapeRichHtml(q.options.d)}</span></div>
                    </div>
                </div>
                """.trimIndent())
            }
        }

        val omrHtml = if (config.includeOmrSheet) generateOmrSectionHtml(paper, config, isStandalone = false) else ""

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>${escapeHtml(config.examTitle)}</title>
            <link rel="preconnect" href="https://fonts.googleapis.com">
            <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800&family=Roboto+Mono:wght@500;700&display=swap">
            <style>
                @page {
                    size: A4 portrait;
                    margin: 14mm 8mm 8mm 8mm;
                }
                * { box-sizing: border-box; }
                body {
                    font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                    color: #000000;
                    background: #ffffff;
                    margin: 0;
                    padding: 0;
                    font-size: 10pt;
                    line-height: 1.35;
                }
                /* Examination Header */
                .exam-header {
                    border: 1.5px solid #000;
                    padding: 8px 12px;
                    margin-bottom: 10px;
                    text-align: center;
                    break-inside: avoid;
                    page-break-inside: avoid;
                }
                .inst-name {
                    font-size: 13pt;
                    font-weight: 800;
                    letter-spacing: 0.8px;
                    text-transform: uppercase;
                    margin-bottom: 3px;
                }
                .exam-name {
                    font-size: 11pt;
                    font-weight: 700;
                    color: #17324D;
                    margin-bottom: 4px;
                }
                .exam-meta-row {
                    display: flex;
                    justify-content: space-between;
                    font-size: 8.5pt;
                    font-weight: 600;
                    border-top: 1px solid #aaa;
                    padding-top: 4px;
                    margin-top: 4px;
                }
                /* Candidate Details Box */
                .candidate-box {
                    border: 1px solid #444;
                    padding: 6px 10px;
                    margin-bottom: 10px;
                    font-size: 8.5pt;
                    break-inside: avoid;
                    page-break-inside: avoid;
                }
                .cand-row {
                    display: flex;
                    justify-content: space-between;
                    margin-bottom: 4px;
                }
                .roll-box-wrap {
                    display: inline-flex;
                    gap: 3px;
                    vertical-align: middle;
                }
                .digit-cell {
                    width: 15px;
                    height: 18px;
                    border: 1px solid #000;
                    display: inline-block;
                }
                /* Instructions */
                .instructions-box {
                    border: 1px dashed #666;
                    background: #fdfdfd;
                    padding: 6px 10px;
                    margin-bottom: 12px;
                    font-size: 8pt;
                    line-height: 1.25;
                    break-inside: avoid;
                    page-break-inside: avoid;
                }
                .instructions-box h4 {
                    margin: 0 0 3px 0;
                    font-size: 8.5pt;
                    text-transform: uppercase;
                    letter-spacing: 0.5px;
                }
                .instructions-box ol {
                    margin: 0;
                    padding-left: 16px;
                }
                /* Questions Layout - Continuous flow without separator lines */
                .questions-container {
                    ${if (config.twoColumns) "column-count: 2; column-gap: 20px; column-rule: 1px solid #eaeaea;" else ""}
                }
                .student-q-item {
                    break-inside: avoid;
                    page-break-inside: avoid;
                    margin-bottom: 16px;
                    padding-bottom: 12px;
                    border: none;
                }
                .q-head {
                    font-weight: 700;
                    font-size: 9.5pt;
                    margin-bottom: 4px;
                    color: #000;
                    line-height: 1.35;
                }
                .options-grid {
                    display: grid;
                    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
                    column-gap: 18px;
                    row-gap: 6px;
                    align-items: start;
                    font-size: 8.5pt;
                }
                .options-stack {
                    display: flex;
                    flex-direction: column;
                    gap: 4px;
                    font-size: 8.5pt;
                }
                .ar-print-block {
                    margin: 3px 0 5px 0;
                    font-weight: normal;
                }
                .ar-print-statement {
                    margin: 2px 0;
                    line-height: 1.35;
                }
                .ar-print-label {
                    font-weight: 700;
                    color: #000;
                }
                .ar-print-leadin, .ar-print-leadout {
                    margin: 2px 0;
                    font-style: italic;
                }
                .opt-cell {
                    display: grid;
                    grid-template-columns: 24px minmax(0, 1fr);
                    align-items: baseline;
                    column-gap: 4px;
                    margin: 0;
                    line-height: 1.25;
                    min-width: 0;
                }
                .opt-key {
                    font-weight: 600;
                    color: #2c3e50;
                    margin-right: 0;
                    min-width: 0;
                }
                .opt-text {
                    min-width: 0;
                }
                
                /* Page Breaks */
                .page-break {
                    page-break-before: always;
                    break-before: page;
                }
                .footer {
                    font-size: 7.5pt;
                    color: #666;
                    text-align: center;
                    margin-top: 16px;
                    border-top: 1px solid #ccc;
                    padding-top: 4px;
                    break-inside: avoid;
                    page-break-inside: avoid;
                }
                ${getOmrCss(isStandalone = false)}
            </style>
        </head>
        <body>
            <div class="exam-header">
                <div class="inst-name">${escapeHtml(config.institutionName)}</div>
                <div class="exam-name">${escapeHtml(config.examTitle)}</div>
                <div class="exam-meta-row">
                    <span><strong>Subject:</strong> ${escapeHtml(paper.metadata.subject)}</span>
                    <span><strong>Series:</strong> ${escapeHtml(paper.metadata.setNumber.ifBlank { config.seriesCode })}</span>
                    <span><strong>Booklet No:</strong> ${escapeHtml(config.bookletNumber.ifBlank { paper.metadata.bookletNumber.ifBlank { "0000001" } })}</span>
                    <span><strong>Time:</strong> ${escapeHtml(config.timeAllowed)}</span>
                    <span><strong>Max Marks:</strong> ${escapeHtml(config.maxMarks)}</span>
                </div>
            </div>

            ${if (config.includeCandidateDetails) {
                val defaultRoll = config.rollNumber.ifBlank { paper.metadata.rollNumber.ifBlank { "0000000001" } }
                val defaultBooklet = config.bookletNumber.ifBlank { paper.metadata.bookletNumber.ifBlank { "0000001" } }
                val rollDigits = ExamNumberGenerator.normalizeRollNumber(defaultRoll, 10).map { it.toString() }
                val bookletDigits = ExamNumberGenerator.normalizeBookletNumber(defaultBooklet, 7).map { it.toString() }

                val rollBoxesHtml = rollDigits.joinToString("") { "<div class=\"digit-cell\">$it</div>" }
                val bookletBoxesHtml = bookletDigits.joinToString("") { "<div class=\"digit-cell\">$it</div>" }

                """
            <div class="candidate-box">
                <div class="cand-row">
                    <span><strong>Candidate Name:</strong> ___________________________________</span>
                    <span><strong>Roll No:</strong> <div class="roll-box-wrap">$rollBoxesHtml</div></span>
                    <span><strong>Booklet No:</strong> <div class="roll-box-wrap">$bookletBoxesHtml</div></span>
                </div>
                <div class="cand-row" style="margin-top: 6px;">
                    <span><strong>Candidate Signature:</strong> _____________________</span>
                    <span><strong>Invigilator's Signature:</strong> _____________________</span>
                </div>
            </div>
            """
            } else ""}

            ${if (config.includeInstructions) """
            <div class="instructions-box">
                <h4>Instructions to Candidates:</h4>
                <ol>
                    ${config.customInstructions.joinToString("") { "<li>${escapeHtml(it)}</li>" }}
                </ol>
            </div>
            """ else ""}

            <div class="questions-container">
                $questionsHtml
            </div>

            <div class="footer">
                *** END OF QUESTION PAPER — ${paper.questions.size} QUESTIONS ***
            </div>

            $omrHtml
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * Formats the Master Booklet PDF layout identical to the Python script output.
     */
    private fun generateMasterBookletHtml(paper: QuestionPaper, config: ExamConfig): String {
        val questionsCardsHtml = buildString {
            for (q in paper.questions) {
                val isAr = QuestionFormatter.isAssertionReason(q.question, q.options, q.questionType)
                val ar = QuestionFormatter.parseAssertionReason(q.question)
                val stacked = QuestionFormatter.shouldStackOptions(q)
                val containerClass = if (stacked) "options-table-stacked" else "options-table"

                val stemHtml = if (ar != null) {
                    """
                    <div class="ar-print-block">
                        ${if (!ar.leadIn.isNullOrBlank()) "<div class=\"ar-print-leadin\">${escapeRichHtml(ar.leadIn)}</div>" else ""}
                        <div class="ar-print-statement"><span class="ar-print-label">Assertion (A):</span> ${escapeRichHtml(ar.assertion)}</div>
                        <div class="ar-print-statement"><span class="ar-print-label">Reason (R):</span> ${escapeRichHtml(ar.reason)}</div>
                        ${if (!ar.leadOut.isNullOrBlank()) "<div class=\"ar-print-leadout\">${escapeRichHtml(ar.leadOut)}</div>" else ""}
                    </div>
                    """.trimIndent()
                } else {
                    escapeRichHtml(normalizeQuestionSpacing(q.question))
                }

                val effectiveType = QuestionFormatter.displayType(q) ?: "Multiple Choice"

                append("""
                <div class="q-card">
                    <div class="q-text">
                        <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px;">
                            <strong>${q.num}.</strong>
                            <span style="font-size: 7.5pt; font-weight: 700; color: #4338ca; background: #eef2ff; border: 1px solid #c7d2fe; padding: 1px 5px; border-radius: 4px;">
                                $effectiveType
                            </span>
                        </div>
                        $stemHtml
                    </div>
                    <div class="$containerClass">
                        <div class="opt-item ${if (q.correctAnswer.equals("A", ignoreCase = true)) "opt-correct" else ""}"><span class="opt-badge">(A)</span> ${escapeRichHtml(q.options.a)}</div>
                        <div class="opt-item ${if (q.correctAnswer.equals("B", ignoreCase = true)) "opt-correct" else ""}"><span class="opt-badge">(B)</span> ${escapeRichHtml(q.options.b)}</div>
                        <div class="opt-item ${if (q.correctAnswer.equals("C", ignoreCase = true)) "opt-correct" else ""}"><span class="opt-badge">(C)</span> ${escapeRichHtml(q.options.c)}</div>
                        <div class="opt-item ${if (q.correctAnswer.equals("D", ignoreCase = true)) "opt-correct" else ""}"><span class="opt-badge">(D)</span> ${escapeRichHtml(q.options.d)}</div>
                    </div>
                </div>
                """.trimIndent())
            }
        }

        val answerKeyGridHtml = generateAnswerKeyTableHtml(paper)
        val explanationsHtml = generateExplanationsHtml(paper)

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>${escapeHtml(paper.metadata.title)} - Master Booklet</title>
            <link rel="preconnect" href="https://fonts.googleapis.com">
            <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800&family=Roboto+Mono:wght@500;700&display=swap">
            <style>
                @page {
                    size: A4 portrait;
                    margin: 18mm 16mm 16mm 16mm;
                }
                * { box-sizing: border-box; }
                body {
                    font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                    color: #000;
                    background: #fff;
                    margin: 0;
                    padding: 0;
                    font-size: 9pt;
                    line-height: 1.35;
                }
                /* Signature Navy Banner on First Page */
                .navy-banner {
                    background: #17324D;
                    color: #ffffff;
                    border-radius: 8px;
                    padding: 14px 18px;
                    text-align: center;
                    margin-bottom: 16px;
                }
                .banner-title {
                    font-size: 13pt;
                    font-weight: 700;
                    margin-bottom: 4px;
                    letter-spacing: 0.3px;
                }
                .banner-sub {
                    font-size: 9.5pt;
                    color: #EAF6F6;
                    margin-bottom: 2px;
                }
                .banner-exam {
                    font-size: 8.5pt;
                    color: #B2EBF2;
                    font-weight: 600;
                }
                .section-heading {
                    font-size: 13pt;
                    font-weight: 700;
                    color: #17324D;
                    text-align: center;
                    margin: 14px 0 10px 0;
                    border-bottom: 2px solid #177E89;
                    padding-bottom: 4px;
                }
                .master-questions-container {
                    ${if (config.twoColumns) "column-count: 2; column-gap: 8mm; column-rule: 1px solid #D5E1E7;" else ""}
                }
                /* Question Items - Continuous clean flow */
                .q-card {
                    border: none;
                    background: transparent;
                    padding: 4px 0;
                    margin-bottom: 12px;
                    break-inside: avoid;
                    page-break-inside: avoid;
                }
                .q-text {
                    font-weight: 700;
                    color: #000000;
                    font-size: 9.5pt;
                    margin-bottom: 6px;
                }
                .options-table {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 4px 12px;
                }
                .options-table-stacked {
                    display: flex;
                    flex-direction: column;
                    gap: 4px;
                }
                .opt-item {
                    font-size: 8.8pt;
                    color: #000000;
                    padding: 3px 6px;
                    border-radius: 4px;
                }
                .opt-correct {
                    background: #ecfdf5;
                    border: 1px solid #10b981;
                    font-weight: bold;
                    color: #065f46;
                }
                .opt-badge {
                    font-weight: 700;
                    color: #177E89;
                    margin-right: 3px;
                }
                .page-break {
                    page-break-before: always;
                    break-before: page;
                }
                /* Answer Key Table */
                .ans-table {
                    width: 100%;
                    border-collapse: collapse;
                    text-align: center;
                    margin: 10px auto;
                    font-size: 8pt;
                }
                .ans-table th {
                    background: #EAF6F6;
                    color: #17324D;
                    font-weight: 700;
                    padding: 4px 6px;
                    border: 0.5px solid #D5E1E7;
                }
                .ans-table td {
                    padding: 4px 6px;
                    border: 0.5px solid #D5E1E7;
                }
                .ans-qnum {
                    font-weight: 700;
                    color: #000;
                }
                .ans-key {
                    font-weight: 700;
                    color: #12656D;
                }
                /* Explanation Card */
                .exp-card {
                    border: 1px solid #D5E1E7;
                    border-radius: 8px;
                    background: #F3F7FA;
                    padding: 10px 12px;
                    margin-bottom: 9px;
                    break-inside: avoid;
                    page-break-inside: avoid;
                }
                .exp-qnum {
                    font-weight: 700;
                    color: #177E89;
                    margin-right: 4px;
                }
                .exp-correct {
                    color: #247A55;
                    font-weight: 700;
                    margin: 4px 0;
                    font-size: 9pt;
                }
                .exp-body {
                    font-size: 8.8pt;
                    color: #1E293B;
                    line-height: 1.35;
                }
            </style>
        </head>
        <body>
            <div class="navy-banner">
                <div class="banner-title">${escapeHtml(paper.metadata.title)}</div>
                <div class="banner-sub">${escapeHtml(paper.metadata.subject)} — Series: ${escapeHtml(paper.metadata.setNumber.ifBlank { config.seriesCode })}</div>
                <div class="banner-sub">Test Booklet No: ${escapeHtml(config.bookletNumber.ifBlank { paper.metadata.bookletNumber.ifBlank { "0000001" } })} | Roll No: ${escapeHtml(config.rollNumber.ifBlank { paper.metadata.rollNumber.ifBlank { "0000000001" } })}</div>
                ${if (paper.metadata.exam.isNotEmpty()) "<div class=\"banner-exam\">" + escapeHtml(paper.metadata.exam) + "</div>" else ""}
            </div>

            <div class="section-heading">Questions</div>
            <div class="master-questions-container">
                $questionsCardsHtml
            </div>

            <div class="page-break"></div>
            <div class="section-heading">Answer Key</div>
            $answerKeyGridHtml

            ${if (paper.questions.any { it.explanation != null }) """
            <div class="page-break"></div>
            <div class="section-heading">Detailed Explanations</div>
            $explanationsHtml
            """ else ""}
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * Formats solutions only (Answer Key Table + Explanations).
     */
    private fun generateSolutionsOnlyHtml(paper: QuestionPaper, config: ExamConfig): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>${escapeHtml(paper.metadata.title)} - Solutions & Answer Key</title>
            <style>
                @page { size: A4 portrait; margin: 15mm; }
                body { font-family: sans-serif; color: #000; font-size: 9.5pt; }
                .heading { text-align: center; color: #17324D; font-size: 14pt; font-weight: bold; margin-bottom: 4px; }
                .sub { text-align: center; color: #64748B; font-size: 9pt; margin-bottom: 12px; }
                .sec-title { font-size: 12pt; font-weight: bold; color: #177E89; margin: 14px 0 6px 0; border-bottom: 1.5px solid #177E89; padding-bottom: 2px; }
                .ans-table { width: 100%; border-collapse: collapse; text-align: center; margin: 10px auto; font-size: 8.5pt; }
                .ans-table th { background: #EAF6F6; color: #17324D; font-weight: 700; padding: 4px 6px; border: 0.5px solid #D5E1E7; }
                .ans-table td { padding: 4px 6px; border: 0.5px solid #D5E1E7; }
                .exp-card { border: 1px solid #D5E1E7; border-radius: 8px; background: #F3F7FA; padding: 8px 10px; margin-bottom: 8px; break-inside: avoid; }
            </style>
        </head>
        <body>
            <div class="heading">${escapeHtml(paper.metadata.title)} — Solutions Key</div>
            <div class="sub">${escapeHtml(paper.metadata.subject)}</div>

            <div class="sec-title">Answer Key Table</div>
            ${generateAnswerKeyTableHtml(paper)}

            <div class="sec-title">Detailed Explanations</div>
            ${generateExplanationsHtml(paper)}
        </body>
        </html>
        """.trimIndent()
    }

    private fun generateAnswerKeyTableHtml(paper: QuestionPaper): String {
        val pairs = 5
        val questions = paper.questions
        val total = questions.size
        val rows = Math.ceil(total.toDouble() / pairs).toInt()

        val sb = StringBuilder()
        sb.append("<table class=\"ans-table\">\n<thead>\n<tr>\n")
        repeat(pairs) {
            sb.append("<th>Q</th><th>Ans</th>")
        }
        sb.append("\n</tr>\n</thead>\n<tbody>\n")

        for (r in 0 until rows) {
            sb.append("<tr>\n")
            for (c in 0 until pairs) {
                val idx = r + c * rows
                if (idx < total) {
                    val q = questions[idx]
                    val ans = q.correctAnswer ?: "—"
                    sb.append("<td class=\"ans-qnum\">${q.num}</td>")
                    sb.append("<td class=\"ans-key\"><strong>$ans</strong></td>")
                } else {
                    sb.append("<td></td><td></td>")
                }
            }
            sb.append("</tr>\n")
        }

        sb.append("</tbody>\n</table>\n")
        return sb.toString()
    }

    private fun generateExplanationsHtml(paper: QuestionPaper): String {
        val sb = StringBuilder()
        for (q in paper.questions) {
            if (q.explanation == null && q.correctAnswer == null) continue
            val letter = q.correctAnswer?.uppercase()?.trim()
            val optText = when (letter) {
                "A" -> q.options.a
                "B" -> q.options.b
                "C" -> q.options.c
                "D" -> q.options.d
                else -> null
            }
            val ansLine = if (letter != null && !optText.isNullOrBlank()) {
                "Correct Answer: ($letter) ${escapeRichHtml(optText)}"
            } else if (letter != null) {
                "Correct Answer: ($letter)"
            } else {
                "Correct Answer: —"
            }

            val cleanExp = q.explanation?.let { McqParser.formatMultiLineExplanation(it) } ?: "No detailed explanation was provided in input."
            sb.append("""
            <div class="exp-card">
                <div><span class="exp-qnum">${q.num}.</span> <strong>${escapeRichHtml(normalizeQuestionSpacing(q.question))}</strong></div>
                <div class="exp-correct">$ansLine</div>
                <div class="exp-body"><strong>Explanation:</strong> ${escapeRichHtml(cleanExp)}</div>
            </div>
            """.trimIndent())
        }
        return sb.toString()
    }

    /**
     * Formats a standalone single-page A4 OMR Response Sheet.
     */
    private fun generateStandaloneOmrHtml(paper: QuestionPaper, config: ExamConfig): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>OMR Response Sheet - ${escapeHtml(paper.metadata.setNumber.ifBlank { "Set A" })}</title>
            <link rel="preconnect" href="https://fonts.googleapis.com">
            <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800&family=Roboto+Mono:wght@500;700&display=swap">
            <style>
                @page {
                    size: A4 portrait;
                    margin: 8mm 4mm 8mm 4mm;
                }
                * { box-sizing: border-box; }
                body {
                    font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                    color: #000000;
                    background: #ffffff;
                    margin: 0;
                    padding: 0;
                }
                ${getOmrCss(isStandalone = true)}
            </style>
        </head>
        <body>
            ${generateOmrSectionHtml(paper, config, isStandalone = true)}
        </body>
        </html>
        """.trimIndent()
    }

    private fun getOmrCss(isStandalone: Boolean): String {
        return """
        /* Drop-out Magenta/Pink Authentic NEET & UPSC OMR Theme Rules */
        .omr-page {
            ${if (isStandalone) "" else "page-break-before: always; break-before: page;"}
            page-break-inside: avoid;
            break-inside: avoid;
            page-break-after: avoid;
            break-after: avoid;
            box-sizing: border-box;
            width: 100%;
            height: 275mm;
            max-height: 275mm;
            overflow: hidden;
            margin: 0;
            padding-top: ${if (isStandalone) "4mm" else "6mm"};
            padding-bottom: 6mm;
            color: #000000;
        }
        .omr-wrapper {
            position: relative;
            border: 1.5px solid #be185d;
            padding: 1.5mm 2.0mm;
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
            gap: 1.2mm;
            background: #ffffff;
        }
        /* 4 Corner Registration Fiducial Anchors */
        .omr-anchor {
            position: absolute;
            width: ${OmrGeometry.ANCHOR_SIZE_MM}mm;
            height: ${OmrGeometry.ANCHOR_SIZE_MM}mm;
            background-color: #be185d;
            z-index: 20;
        }
        .anchor-tl { top: ${OmrGeometry.ANCHOR_MARGIN_MM}mm; left: ${OmrGeometry.ANCHOR_MARGIN_MM}mm; }
        .anchor-tr { top: ${OmrGeometry.ANCHOR_MARGIN_MM}mm; right: ${OmrGeometry.ANCHOR_MARGIN_MM}mm; }
        .anchor-bl { bottom: ${OmrGeometry.ANCHOR_MARGIN_MM}mm; left: ${OmrGeometry.ANCHOR_MARGIN_MM}mm; }
        .anchor-br { bottom: ${OmrGeometry.ANCHOR_MARGIN_MM}mm; right: ${OmrGeometry.ANCHOR_MARGIN_MM}mm; }

        /* Top Header Area without middle stamp box to ensure clean header and ample staple margin */
        .omr-top-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin: 0 4mm 0.8mm 4mm;
            gap: 6mm;
        }
        .omr-header-warning {
            flex: 1.4;
            border: 1.2px solid #be185d;
            border-radius: 4px;
            padding: 1.2mm 2.0mm;
            font-size: 5.4pt;
            font-weight: 700;
            color: #be185d;
            line-height: 1.2;
            background: #ffffff;
        }
        .omr-header-title-box {
            flex: 1.1;
            text-align: right;
            display: flex;
            flex-direction: column;
            justify-content: center;
        }
        .omr-title {
            font-weight: 900;
            font-size: 10.5pt;
            letter-spacing: 0.5px;
            text-transform: uppercase;
            color: #be185d;
            line-height: 1.1;
        }
        .omr-exam-subtitle {
            font-size: 6.0pt;
            font-weight: 800;
            color: #be185d;
            letter-spacing: 0.3px;
        }

        /* 2-Panel Main Container */
        .omr-body-container {
            display: flex;
            gap: 2.2mm;
            margin: 0 0.5mm;
        }

        /* Left Identification Panel */
        .omr-left-panel {
            width: ${OmrGeometry.LEFT_PANEL_WIDTH_MM}mm;
            min-width: ${OmrGeometry.LEFT_PANEL_WIDTH_MM}mm;
            max-width: ${OmrGeometry.LEFT_PANEL_WIDTH_MM}mm;
            display: flex;
            flex-direction: column;
            gap: 1.2mm;
            box-sizing: border-box;
            position: relative;
        }
        .omr-panel-warning {
            border: 1.2px solid #be185d;
            background: #fdf2f8;
            color: #be185d;
            font-size: 5.2pt;
            font-weight: 900;
            text-align: center;
            padding: 1.0mm 0.6mm;
            letter-spacing: 0.3px;
            line-height: 1.2;
            text-transform: uppercase;
            box-sizing: border-box;
        }
        .panel-box {
            border: 1.2px solid #be185d;
            background: #ffffff;
            box-sizing: border-box;
        }
        .grid-header-title {
            background: #ffffff;
            color: #be185d;
            font-size: 6.8pt;
            font-weight: 900;
            text-align: center;
            padding: 1px 0;
            letter-spacing: 0.5px;
            border-bottom: 1px solid #be185d;
        }
        .digit-entry-row {
            display: flex;
            border-bottom: 1px solid #be185d;
            background: #fff;
        }
        .digit-box {
            flex: 1;
            height: 3.8mm;
            border-right: 1px solid #be185d;
            box-sizing: border-box;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 6.2pt;
            font-weight: 900;
            font-family: "Courier New", Courier, monospace;
            color: #000000;
        }
        .digit-box:last-child {
            border-right: none;
        }
        .roll-grid {
            display: grid;
            grid-template-columns: repeat(10, 1fr);
            row-gap: 0.35mm;
            column-gap: 0.6mm;
            padding: 0.6mm 0.3mm;
            justify-items: center;
            align-items: center;
        }
        .booklet-grid {
            display: grid;
            grid-template-columns: repeat(7, 1fr);
            row-gap: 0.35mm;
            column-gap: 0.8mm;
            padding: 0.6mm 0.3mm;
            justify-items: center;
            align-items: center;
        }
        .micro-bubble {
            width: 3.3mm;
            height: 3.3mm;
            min-width: 3.3mm;
            min-height: 3.3mm;
            max-width: 3.3mm;
            max-height: 3.3mm;
            border: 0.35mm solid #be185d;
            border-radius: 50%;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 5.2pt;
            font-weight: 800;
            color: #be185d;
            background: #ffffff;
            box-sizing: border-box;
            line-height: 1;
            text-align: center;
        }
        .micro-bubble-darkened {
            background: #000000 !important;
            color: #ffffff !important;
            border-color: #000000 !important;
        }

        /* Booklet Code Box */
        .booklet-code-box {
            border: 1.2px solid #be185d;
            padding: 1.0mm;
            box-sizing: border-box;
        }
        .booklet-code-header {
            font-size: 6.2pt;
            font-weight: 900;
            color: #be185d;
            text-align: center;
            margin-bottom: 0.8mm;
        }
        .booklet-code-inner {
            display: flex;
            align-items: center;
            justify-content: space-around;
        }
        .booklet-code-large {
            font-size: 16pt;
            font-weight: 900;
            color: #be185d;
            line-height: 1;
        }
        .code-bubbles {
            display: flex;
            gap: 1.5mm;
        }

        /* Answer Sheet No Area (with L-bracket corner marks) */
        .sheet-no-container {
            position: relative;
            height: 14mm;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            margin: 0.3mm 0;
        }
        .bracket {
            position: absolute;
            width: 3.5mm;
            height: 3.5mm;
            border-color: #be185d;
            border-style: solid;
        }
        .b-tl { top: 0; left: 0; border-width: 1.5px 0 0 1.5px; }
        .b-tr { top: 0; right: 0; border-width: 1.5px 1.5px 0 0; }
        .b-bl { bottom: 0; left: 0; border-width: 0 0 1.5px 1.5px; }
        .b-br { bottom: 0; right: 0; border-width: 0 1.5px 1.5px 0; }
        .sheet-no-label {
            font-size: 5.8pt;
            font-weight: 800;
            color: #be185d;
            margin-bottom: 0.8mm;
        }

        /* Candidate Declaration Box */
        .declaration-box {
            border: 1.2px solid #be185d;
            padding: 1.2mm 1.5mm;
            font-size: 4.7pt;
            color: #831843;
            line-height: 1.18;
            background: #fff;
            box-sizing: border-box;
        }
        .declaration-title {
            font-weight: 900;
            color: #be185d;
            font-size: 5.6pt;
            margin-bottom: 0.6mm;
            text-transform: uppercase;
        }

        /* Candidate's Name Box (Full write-in rectangular box) */
        .cand-name-box {
            border: 1.2px solid #be185d;
            padding: 1.2mm 1.5mm;
            height: 14.5mm;
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            background: #ffffff;
        }
        .cand-name-label {
            font-size: 5.4pt;
            font-weight: 900;
            color: #be185d;
            text-transform: uppercase;
            letter-spacing: 0.2px;
        }
        .cand-name-line {
            border-bottom: 1px solid #be185d;
            width: 100%;
            margin-top: auto;
        }

        /* Right Question Grid (Continuous 4 Columns × 50 Rows, No Section Partitions) */
        .omr-right-grid {
            flex: 1;
            display: flex;
            border: 1.2px solid #be185d;
            box-sizing: border-box;
            background: #ffffff;
        }
        .omr-column {
            flex: 1;
            display: flex;
            flex-direction: column;
            border-right: 1px solid #be185d;
            box-sizing: border-box;
        }
        .omr-column:last-child {
            border-right: none;
        }
        .omr-col-header {
            background: #ffffff;
            border-bottom: 1.2px solid #be185d;
            color: #be185d;
            display: flex;
            align-items: center;
            padding: 0 0.8mm;
            height: ${OmrGeometry.COL_HEADER_HEIGHT_MM}mm;
            box-sizing: border-box;
            font-size: 5.6pt;
            font-weight: 900;
        }
        .header-qnum-lbl {
            width: ${OmrGeometry.QNUM_WIDTH_MM}mm;
            text-align: right;
            padding-right: ${OmrGeometry.QNUM_TO_BUBBLE_GAP_MM}mm;
            box-sizing: border-box;
        }
        .header-resp-wrap {
            display: flex;
            gap: 2.0mm;
            align-items: center;
        }
        .header-opt-lbl {
            width: ${OmrGeometry.BUBBLE_DIAMETER_MM}mm;
            text-align: center;
            font-size: 5.6pt;
            font-weight: 900;
            color: #be185d;
        }

        /* Question Rows */
        .omr-row {
            display: flex;
            align-items: center;
            justify-content: flex-start;
            height: 3.85mm;
            min-height: 3.85mm;
            max-height: 3.85mm;
            padding: 0 0.8mm;
            box-sizing: border-box;
        }
        .omr-row-shaded {
            background-color: #fdf2f8;
        }
        .omr-qnum {
            width: ${OmrGeometry.QNUM_WIDTH_MM}mm;
            font-size: 6.0pt;
            font-weight: 900;
            text-align: right;
            color: #be185d;
            font-family: "Courier New", Courier, monospace;
            padding-right: ${OmrGeometry.QNUM_TO_BUBBLE_GAP_MM}mm;
            box-sizing: border-box;
        }
        .bubble-group {
            display: flex;
            gap: 2.0mm;
            align-items: center;
        }
        .bubble {
            width: ${OmrGeometry.BUBBLE_DIAMETER_MM}mm;
            height: ${OmrGeometry.BUBBLE_DIAMETER_MM}mm;
            min-width: ${OmrGeometry.BUBBLE_DIAMETER_MM}mm;
            min-height: ${OmrGeometry.BUBBLE_DIAMETER_MM}mm;
            max-width: ${OmrGeometry.BUBBLE_DIAMETER_MM}mm;
            max-height: ${OmrGeometry.BUBBLE_DIAMETER_MM}mm;
            border: ${OmrGeometry.BUBBLE_BORDER_THICKNESS_MM}mm solid #be185d;
            border-radius: 50%;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-weight: 800;
            font-size: 5.4pt;
            color: #be185d;
            background: #ffffff;
            line-height: 1;
            box-sizing: border-box;
            text-align: center;
            text-transform: uppercase;
        }

        /* Bottom Verification Area: 3 Distinct Boxes (Thumb + Candidate Sign + Single Invigilator Sign-Across) */
        .omr-bottom-area {
            border: 1.2px solid #be185d;
            margin: 0.8mm 0.5mm 0 0.5mm;
            background: #fff;
            padding: 1.0mm 1.2mm;
            box-sizing: border-box;
            display: flex;
            gap: 2.5mm;
            height: 20mm;
        }
        .omr-thumb-container {
            flex: 1;
            border: 1.2px solid #be185d;
            padding: 1.0mm;
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            background: #ffffff;
        }
        .omr-cand-sig-container {
            flex: 1.2;
            border: 1.2px solid #be185d;
            padding: 1.0mm;
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            background: #ffffff;
        }
        .omr-invig-container {
            flex: 1.5;
            border: 1.2px solid #be185d;
            padding: 1.0mm 1.2mm;
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            background: #ffffff;
        }
        .field-label {
            font-size: 5.3pt;
            font-weight: 900;
            color: #be185d;
            text-transform: uppercase;
            letter-spacing: 0.2px;
        }
        .invig-sig-row {
            display: flex;
            align-items: center;
            border-bottom: 1px solid #be185d;
            padding-bottom: 0.8mm;
            font-size: 5.1pt;
            font-weight: 800;
            color: #be185d;
            gap: 2mm;
        }
        .invig-line {
            flex: 1;
            border-bottom: 1px solid #be185d;
        }
        """.trimIndent()
    }

    private fun generateOmrSectionHtml(paper: QuestionPaper, config: ExamConfig, isStandalone: Boolean = false): String {
        val questionCount = paper.questions.size.coerceAtLeast(1)
        val rawSet = paper.metadata.setNumber.ifBlank { config.seriesCode.ifBlank { "A" } }.trim().uppercase()
        val setClean = ExamNumberGenerator.extractBookletCode(rawSet)
        val bookletLetters = ExamNumberGenerator.resolveBookletSets(setClean)

        val rollStr = if (config.rollNumber.isNotBlank()) {
            ExamNumberGenerator.normalizeRollNumber(config.rollNumber, 10)
        } else if (paper.metadata.rollNumber.isNotBlank()) {
            ExamNumberGenerator.normalizeRollNumber(paper.metadata.rollNumber, 10)
        } else ""

        val bookletStr = if (config.bookletNumber.isNotBlank()) {
            ExamNumberGenerator.normalizeBookletNumber(config.bookletNumber, 7)
        } else if (paper.metadata.bookletNumber.isNotBlank()) {
            ExamNumberGenerator.normalizeBookletNumber(paper.metadata.bookletNumber, 7)
        } else ""

        val showRollDigits = (config.omrBubblingMode == OmrBubblingMode.PRE_PRINTED_DIGITS ||
                config.omrBubblingMode == OmrBubblingMode.PRE_DARKENED_BUBBLES) && rollStr.length == 10
        val darkenRollBubbles = config.omrBubblingMode == OmrBubblingMode.PRE_DARKENED_BUBBLES && rollStr.length == 10

        val showBookletDigits = (config.omrBubblingMode == OmrBubblingMode.PRE_PRINTED_DIGITS ||
                config.omrBubblingMode == OmrBubblingMode.PRE_DARKENED_BUBBLES) && bookletStr.length == 7
        val darkenBookletBubbles = config.omrBubblingMode == OmrBubblingMode.PRE_DARKENED_BUBBLES && bookletStr.length == 7

        val sb = StringBuilder()

        sb.append("""
        <div class="omr-page">
        <div class="omr-wrapper">
            <!-- 4 Corner Registration Fiducial Anchors in drop-out magenta (#be185d) -->
            <div class="omr-anchor anchor-tl"></div>
            <div class="omr-anchor anchor-tr"></div>
            <div class="omr-anchor anchor-bl"></div>
            <div class="omr-anchor anchor-br"></div>

            <!-- Top Header without middle stamp box -->
            <div class="omr-top-header">
                <div class="omr-header-warning">
                    The candidate should ensure that Roll No. and Test Booklet No. have been filled and marked correctly and Test Booklet Code printed in this sheet and Test Booklet is the same.
                </div>
                <div class="omr-header-title-box">
                    <div class="omr-title">${escapeHtml(config.examTitle.ifBlank { paper.metadata.exam.ifBlank { "UPSC CSE" } }).uppercase()} OMR ANSWER SHEET</div>
                    <div class="omr-exam-subtitle">PRACTICE / MOCK TEST OMR RESPONSE SHEET — FOR EXAMINATION PREPARATION ONLY</div>
                </div>
            </div>

            <!-- 2-Panel Main Body -->
            <div class="omr-body-container">
                <!-- Left Identification Panel -->
                <div class="omr-left-panel">
                    <div class="omr-panel-warning">Candidate MUST NOT carry ORIGINAL or OFFICE Copy</div>

                    <!-- Roll No Grid (10 Digits) with Synchronized Grid -->
                    <div class="panel-box">
                        <div class="grid-header-title">ROLL NO.</div>
                        <div class="digit-entry-row">
                            ${(0 until 10).joinToString("") { colIdx ->
                                val ch = if (showRollDigits) rollStr[colIdx].toString() else ""
                                "<div class=\"digit-box\">$ch</div>"
                            }}
                        </div>
                        <div class="roll-grid">
                            ${(0..9).joinToString("") { d ->
                                (0 until 10).joinToString("") { colIdx ->
                                    val isDarkened = darkenRollBubbles && (rollStr[colIdx] - '0') == d
                                    val cls = if (isDarkened) "micro-bubble micro-bubble-darkened" else "micro-bubble"
                                    "<span class=\"$cls\">$d</span>"
                                }
                            }}
                        </div>
                    </div>

                    <!-- Test Booklet No Grid (7 Digits) with Synchronized Grid -->
                    <div class="panel-box">
                        <div class="grid-header-title">TEST BOOKLET No.</div>
                        <div class="digit-entry-row">
                            ${(0 until 7).joinToString("") { colIdx ->
                                val ch = if (showBookletDigits) bookletStr[colIdx].toString() else ""
                                "<div class=\"digit-box\">$ch</div>"
                            }}
                        </div>
                        <div class="booklet-grid">
                            ${(0..9).joinToString("") { d ->
                                (0 until 7).joinToString("") { colIdx ->
                                    val isDarkened = darkenBookletBubbles && (bookletStr[colIdx] - '0') == d
                                    val cls = if (isDarkened) "micro-bubble micro-bubble-darkened" else "micro-bubble"
                                    "<span class=\"$cls\">$d</span>"
                                }
                            }}
                        </div>
                    </div>

                    <!-- Test Booklet Code -->
                    <div class="booklet-code-box">
                        <div class="booklet-code-header">Test Booklet Code</div>
                        <div class="booklet-code-inner">
                            <div class="booklet-code-large">${escapeHtml(setClean.ifBlank { "A" })}</div>
                            <div class="code-bubbles">
                                ${bookletLetters.joinToString("") { code ->
                                    val isDark = (config.omrBubblingMode == OmrBubblingMode.PRE_DARKENED_BUBBLES && code.equals(setClean, ignoreCase = true))
                                    val darkStyle = if (isDark) "background:#000000; color:#ffffff; border-color:#000000;" else "background:#ffffff; color:#be185d;"
                                    "<span class=\"micro-bubble\" style=\"width:3.3mm; height:3.3mm; $darkStyle\">$code</span>"
                                }}
                            </div>
                        </div>
                    </div>

                    <!-- Answer Sheet No. Box with L-Brackets -->
                    <div class="sheet-no-container">
                        <div class="bracket b-tl"></div>
                        <div class="bracket b-tr"></div>
                        <div class="bracket b-bl"></div>
                        <div class="bracket b-br"></div>
                        <div class="sheet-no-label">Answer Sheet No.</div>
                        <div style="font-size: 6.2pt; font-family: monospace; font-weight: 800; color: #be185d; letter-spacing: 1px;">
                            ${String.format("%08d", (paper.id.hashCode().let { if (it < 0) -it else it } % 90000000) + 10000000)}
                        </div>
                    </div>

                    <!-- Candidate's Name Box (Full write-in rectangular box) -->
                    <div class="cand-name-box">
                        <div class="cand-name-label">CANDIDATE'S NAME (in running handwriting)</div>
                        <div class="cand-name-line"></div>
                    </div>

                    <!-- Candidate Declaration -->
                    <div class="declaration-box">
                        <div class="declaration-title">DECLARATION BY THE CANDIDATE</div>
                        "I declare that particulars and signature on this OMR Answer Sheet are mine and same as in Attendance Sheet. I further declare that particulars filled-in and signature as well as circles darkened on this ORIGINAL Copy are same as in OFFICE Copy."
                    </div>
                </div>

                <!-- Right Question Grid (Continuous 4 Columns of 50 Questions Each, 100% Full Opacity) -->
                <div class="omr-right-grid">
                    ${(0 until OmrGeometry.NUM_GRID_COLUMNS).joinToString("") { col ->
                        val startQ = col * OmrGeometry.ROWS_PER_COLUMN + 1
                        val endQ = (col + 1) * OmrGeometry.ROWS_PER_COLUMN
                        buildString {
                            append("""<div class="omr-column">""")
                            append("""
                                <div class="omr-col-header">
                                    <span class="header-qnum-lbl">Q. No.</span>
                                    <div class="header-resp-wrap">
                                        <span class="header-opt-lbl">1</span>
                                        <span class="header-opt-lbl">2</span>
                                        <span class="header-opt-lbl">3</span>
                                        <span class="header-opt-lbl">4</span>
                                    </div>
                                </div>
                            """.trimIndent())
                            for (q in startQ..endQ) {
                                val blockIndex = (q - 1) / 5
                                val isShaded = (blockIndex % 2) == 1
                                val shadedClass = if (isShaded) "omr-row-shaded" else ""
                                val qPadded = String.format("%03d", q)
                                append("""
                                    <div class="omr-row $shadedClass">
                                        <span class="omr-qnum">$qPadded</span>
                                        <div class="bubble-group">
                                            <span class="bubble">1</span>
                                            <span class="bubble">2</span>
                                            <span class="bubble">3</span>
                                            <span class="bubble">4</span>
                                        </div>
                                    </div>
                                """.trimIndent())
                            }
                            append("</div>")
                        }
                    }}
                </div>
            </div>

            <!-- Bottom Verification & Identification Area (3 Distinct Boxes: Thumb, Candidate Sign & Single Invigilator Sign-Across) -->
            <div class="omr-bottom-area">
                <div class="omr-thumb-container">
                    <div class="field-label">CANDIDATE'S LEFT HAND THUMB IMPRESSION</div>
                    <div style="flex:1;"></div>
                </div>
                <div class="omr-cand-sig-container">
                    <div class="field-label">CANDIDATE'S SIGNATURE WITH TIME</div>
                    <div style="font-size: 4.4pt; color: #831843; margin-top: 1px;">(in running handwriting)</div>
                    <div style="flex:1;"></div>
                    <div class="field-line"></div>
                </div>
                <div class="omr-invig-container">
                    <div class="field-label">INVIGILATOR'S SIGNATURE WITH TIME</div>
                    <div style="font-size: 4.4pt; color: #831843; margin-top: 1px;">(Sign across the blank line with time)</div>
                    <div style="flex:1;"></div>
                    <div class="invig-sig-row">
                        <div class="invig-line"></div>
                        <span style="font-size: 4.8pt; white-space: nowrap; font-weight: 800;">(Sign & Date/Time)</span>
                    </div>
                </div>
            </div>
        </div>
        </div>
        """.trimIndent())

        return sb.toString()
    }

    private data class Tuple4<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    private fun normalizeQuestionSpacing(text: String): String {
        return QuestionFormatter.normalizeQuestionSpacing(text)
    }

    private fun escapeRichHtml(text: String): String {
        val cleanedText = McqParser.formatMultiLineExplanation(text)
        val mathProcessed = if (LatexHelper.hasLatex(cleanedText)) {
            LatexHelper.toRichTextString(cleanedText)
        } else {
            cleanedText
        }
        var html = escapeHtml(mathProcessed)

        // Preserve existing HTML-style rich text after escaping.
        html = html
            .replace("&lt;sub&gt;", "<sub>")
            .replace("&lt;/sub&gt;", "</sub>")
            .replace("&lt;sup&gt;", "<sup>")
            .replace("&lt;/sup&gt;", "</sup>")
            .replace("&lt;b&gt;", "<b>")
            .replace("&lt;/b&gt;", "</b>")
            .replace("&lt;i&gt;", "<i>")
            .replace("&lt;/i&gt;", "</i>")

        // Markdown emphasis.
        html = html
            .replace(Regex("""\*\*(.+?)\*\*"""), "<b>$1</b>")
            .replace(Regex("""(?<!_)__([^\s_](?:[\s\S]*?[^\s_])?)__(?!_)"""), "<b>$1</b>")
            .replace(Regex("""(?<!\*)\*([^*]+?)\*(?!\*)"""), "<i>$1</i>")

        // Convert Unicode super/subscript characters directly to HTML.
        val subMap = mapOf(
            '₀' to "0", '₁' to "1", '₂' to "2", '₃' to "3", '₄' to "4",
            '₅' to "5", '₆' to "6", '₇' to "7", '₈' to "8", '₉' to "9",
            '₊' to "+", '₋' to "-", '₌' to "=", 'ₐ' to "a", 'ₑ' to "e",
            'ᵢ' to "i", 'ⱼ' to "j", 'ₒ' to "o", 'ᵣ' to "r", 'ᵤ' to "u",
            'ᵥ' to "v", 'ₓ' to "x"
        )

        val supMap = mapOf(
            '⁰' to "0", '¹' to "1", '²' to "2", '³' to "3", '⁴' to "4",
            '⁵' to "5", '⁶' to "6", '⁷' to "7", '⁸' to "8", '⁹' to "9",
            '⁺' to "+", '⁻' to "-", '⁼' to "=", 'ⁿ' to "n", 'ⁱ' to "i"
        )

        for ((ch, value) in subMap) {
            html = html.replace(ch.toString(), "<sub>$value</sub>")
        }

        for ((ch, value) in supMap) {
            html = html.replace(ch.toString(), "<sup>$value</sup>")
        }

        return html.replace(Regex("""\n{3,}"""), "\n\n").replace("\n", "<br/>")
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
