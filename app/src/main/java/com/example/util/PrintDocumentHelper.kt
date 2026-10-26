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
import org.json.JSONArray
import org.json.JSONObject

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

    /**
     * Computes the formatted CBT HTML filename: {subject}_{rollNumber}_{bookletNumber}_CBT.html
     */
    fun getExamCbtHtmlFileName(paper: QuestionPaper, config: ExamConfig): String {
        val rawSubject = paper.metadata.subject.ifBlank { config.subject.ifBlank { "Exam" } }
        val subjectClean = sanitizeForFileName(rawSubject)

        val rollClean = sanitizeForFileName(
            config.rollNumber.ifBlank { paper.metadata.rollNumber.ifBlank { "0000000001" } }
        )
        val bookletClean = sanitizeForFileName(
            config.bookletNumber.ifBlank { paper.metadata.bookletNumber.ifBlank { "0000001" } }
        )

        return "${subjectClean}_${rollClean}_${bookletClean}_CBT.html"
    }

    /**
     * Saves the question paper as an interactive Computer-Based Test (CBT) HTML application
     * directly into the device file manager under:
     * Documents/exam_set/{YYYY}/{MM}/{Subject}/
     */
    fun saveInteractiveExamHtmlDirectly(
        context: Context,
        paper: QuestionPaper,
        config: ExamConfig,
        onSuccess: (savedPath: String) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        try {
            val fileName = getExamCbtHtmlFileName(paper, config)
            val html = generateInteractiveExamHtml(paper, config)

            val yearStr = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            val monthStr = SimpleDateFormat("MM", Locale.getDefault()).format(Date())
            val subjectClean = sanitizeForFileName(
                paper.metadata.subject.ifBlank { config.subject.ifBlank { "General" } }
            )

            val relativeSubDir = "exam_set/$yearStr/$monthStr/$subjectClean"

            val tempFile = File(context.cacheDir, "temp_cbt_${System.currentTimeMillis()}.html")
            tempFile.writeText(html, Charsets.UTF_8)

            val finalPath = exportTempFileToExamSetDirectory(
                context = context,
                tempFile = tempFile,
                relativeSubDir = relativeSubDir,
                fileName = fileName,
                mimeType = "text/html"
            )
            tempFile.delete()
            onSuccess(finalPath)
        } catch (e: Exception) {
            onError("Failed to save CBT HTML: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun normalizeAnswerLetter(ans: String?): String {
        if (ans.isNullOrBlank()) return ""
        val trimmed = ans.trim().uppercase()
        return when (trimmed) {
            "1", "(1)", "A", "(A)", "OPT A", "OPTION A" -> "A"
            "2", "(2)", "B", "(B)", "OPT B", "OPTION B" -> "B"
            "3", "(3)", "C", "(C)", "OPT C", "OPTION C" -> "C"
            "4", "(4)", "D", "(D)", "OPT D", "OPTION D" -> "D"
            "5", "(5)", "E", "(E)", "OPT E", "OPTION E" -> "E"
            else -> if (trimmed.length == 1 && trimmed[0] in 'A'..'E') trimmed else ""
        }
    }

    private fun exportTempFileToExamSetDirectory(
        context: Context,
        tempFile: File,
        relativeSubDir: String,
        fileName: String,
        mimeType: String = "application/pdf"
    ): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$relativeSubDir")
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)
                ?: throw IllegalStateException("Unable to allocate MediaStore entry for $fileName")

            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(tempFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalStateException("Could not write to MediaStore output stream")

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
     * Generates a fully interactive, self-contained Computer-Based Test (CBT) HTML application
     * featuring:
     * - Candidate Lobby & Roll Number verification gate before starting the timer
     * - Slimline 42px responsive header preserving vertical viewport in portrait and landscape
     * - Font-size scaling controller (A-, A, A+)
     * - Question Paper overview modal
     * - Desktop keyboard shortcuts (1..4, A..D, Enter, Arrow keys, Backspace, M, Esc)
     * - Anti-cheating tab-switch detection
     * - Local session auto-save and crash recovery via sessionStorage
     * - Responsive question container with sticky action toolbar
     * - Question palette side drawer with auto-dismiss on mobile
     * - Comprehensive scorecard with arithmetic net score breakdown and accuracy rate
     * - Web Share API + clipboard fallback for instant score sharing
     * - Structured JSON result export for instructors
     * - Full step-by-step detailed explanations matching Master View
     * - Clean ISO A4 print stylesheet (@media print)
     */
    fun generateInteractiveExamHtml(paper: QuestionPaper, config: ExamConfig): String {
        val parsedMinutes = Regex("""\b(\d+)\b""").find(config.timeAllowed)?.groupValues?.get(1)?.toIntOrNull()
        val durationMinutes = config.durationMinutes.takeIf { it > 0 } ?: (parsedMinutes ?: (paper.questions.size * 2).coerceAtLeast(30))
        val positiveMarks = config.positiveMarks.takeIf { it > 0.0 } ?: 4.0
        val negativeMarks = config.negativeMarks.takeIf { it >= 0.0 } ?: 1.0
        val posFormatted = String.format(java.util.Locale.US, "%.2f", positiveMarks)
        val negFormatted = String.format(java.util.Locale.US, "%.2f", negativeMarks)

        val metaObj = JSONObject().apply {
            put("institution", config.institutionName.ifBlank { "CENTRAL EXAMINATION BOARD" })
            put("title", config.examTitle.ifBlank { paper.metadata.title.ifBlank { "OBJECTIVE EVALUATION TEST" } })
            put("subject", paper.metadata.subject.ifBlank { config.subject.ifBlank { "General Assessment" } })
            put("topic", paper.metadata.topic.ifBlank { config.topic })
            put("seriesCode", config.seriesCode.ifBlank { "SET - A" })
            put("rollNumber", config.rollNumber.ifBlank { paper.metadata.rollNumber.ifBlank { "0000000001" } })
            put("bookletNumber", config.bookletNumber.ifBlank { paper.metadata.bookletNumber.ifBlank { "0000001" } })
            put("durationMinutes", durationMinutes)
            put("totalQuestions", paper.questions.size)
            put("positiveMarks", positiveMarks)
            put("negativeMarks", negativeMarks)
        }

        val questionsArray = JSONArray()
        for ((idx, q) in paper.questions.withIndex()) {
            val qObj = JSONObject()
            qObj.put("index", idx)
            qObj.put("num", q.num)
            qObj.put("stemRaw", q.question)
            qObj.put("stemHtml", escapeRichHtml(q.question))

            val optsArray = JSONArray()
            for ((key, text) in q.options.asList) {
                if (text.isNotBlank() || key in listOf("A", "B", "C", "D")) {
                    val optObj = JSONObject()
                    optObj.put("key", key)
                    optObj.put("text", text)
                    optObj.put("rendered", escapeRichHtml(text))
                    optsArray.put(optObj)
                }
            }
            qObj.put("options", optsArray)
            qObj.put("correctAnswer", normalizeAnswerLetter(q.correctAnswer))
            qObj.put("explanationHtml", if (!q.explanation.isNullOrBlank()) escapeRichHtml(q.explanation) else "")
            qObj.put("formula", q.formula ?: "")
            qObj.put("diagramLabel", q.diagramLabel ?: "")
            questionsArray.put(qObj)
        }

        val examDataJson = JSONObject().apply {
            put("meta", metaObj)
            put("questions", questionsArray)
        }.toString()

        val safeTitle = escapeHtml(config.examTitle.ifBlank { "Examination Paper" })
        val safeSubject = escapeHtml(paper.metadata.subject.ifBlank { config.subject.ifBlank { "Exam" } })
        val safeInst = escapeHtml(config.institutionName.ifBlank { "Central Examination Board" })
        val safeRoll = escapeHtml(config.rollNumber.ifBlank { paper.metadata.rollNumber.ifBlank { "0000000001" } })
        val safeBooklet = escapeHtml(config.bookletNumber.ifBlank { paper.metadata.bookletNumber.ifBlank { "0000001" } })

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
    <title>$safeTitle - $safeSubject</title>
    <style>
        :root {
            --bg-page: #f8fafc;
            --bg-card: #ffffff;
            --border-color: #e2e8f0;
            --text-main: #0f172a;
            --text-muted: #64748b;
            --primary: #2563eb;
            --primary-hover: #1d4ed8;
            --primary-light: #eff6ff;
            --teal: #0d9488;
            --teal-light: #f0fdfa;
            --success: #16a34a;
            --success-light: #dcfce7;
            --danger: #dc2626;
            --danger-light: #fee2e2;
            --warning: #ea580c;
            --warning-light: #ffedd5;
            --purple: #7c3aed;
            --purple-light: #f3e8ff;
            --gray-palette: #94a3b8;
            --gray-unvisited: #e2e8f0;
            --q-font-size: 15.5px;
            --header-h: 44px;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            -webkit-tap-highlight-color: transparent;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            background-color: var(--bg-page);
            color: var(--text-main);
            min-height: 100vh;
            min-height: -webkit-fill-available;
            display: flex;
            flex-direction: column;
            overflow-x: hidden;
        }

        /* Lobby & Candidate Verification Screen */
        #lobby-screen {
            position: fixed;
            inset: 0;
            background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 1000;
            padding: 16px;
            overflow-y: auto;
        }

        .lobby-card {
            background: #ffffff;
            border-radius: 14px;
            max-width: 560px;
            width: 100%;
            padding: 24px;
            box-shadow: 0 12px 30px rgba(0,0,0,0.3);
            margin: auto;
        }

        .lobby-header {
            text-align: center;
            border-bottom: 1.5px solid var(--border-color);
            padding-bottom: 14px;
            margin-bottom: 16px;
        }

        .lobby-inst {
            font-size: 11px;
            font-weight: 800;
            letter-spacing: 1px;
            color: var(--teal);
            text-transform: uppercase;
        }

        .lobby-title {
            font-size: 18px;
            font-weight: 800;
            color: var(--text-main);
            margin-top: 3px;
        }

        .lobby-subject {
            font-size: 12.5px;
            color: var(--text-muted);
            margin-top: 2px;
        }

        .lobby-inputs {
            display: flex;
            flex-direction: column;
            gap: 12px;
            margin-bottom: 16px;
        }

        .input-group label {
            display: block;
            font-size: 11.5px;
            font-weight: 700;
            color: var(--text-muted);
            margin-bottom: 4px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .lobby-input {
            width: 100%;
            padding: 10px 12px;
            font-size: 14.5px;
            font-weight: 600;
            border: 1.5px solid var(--border-color);
            border-radius: 7px;
            background: #f8fafc;
            color: var(--text-main);
            outline: none;
            transition: border-color 0.15s ease;
        }
        .lobby-input:focus {
            border-color: var(--primary);
            background: #ffffff;
        }

        .lobby-meta-grid {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 8px;
            background: #f8fafc;
            padding: 10px;
            border-radius: 8px;
            border: 1px solid var(--border-color);
            margin-bottom: 14px;
            text-align: center;
        }

        .meta-stat-item .val {
            font-size: 14px;
            font-weight: 800;
            color: var(--text-main);
        }
        .meta-stat-item .lbl {
            font-size: 10px;
            color: var(--text-muted);
            text-transform: uppercase;
            font-weight: 700;
        }

        .lobby-rules-box {
            background: #f1f5f9;
            border-radius: 8px;
            padding: 12px;
            font-size: 12px;
            line-height: 1.5;
            color: var(--text-main);
            max-height: 130px;
            overflow-y: auto;
            margin-bottom: 14px;
        }
        .lobby-rules-box ul {
            padding-left: 18px;
        }
        .lobby-rules-box li {
            margin-bottom: 4px;
        }

        .lobby-agree-row {
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 12.5px;
            color: var(--text-main);
            cursor: pointer;
            user-select: none;
            margin-bottom: 16px;
        }
        .lobby-agree-row input {
            width: 17px;
            height: 17px;
            cursor: pointer;
        }

        .btn-start-exam {
            width: 100%;
            padding: 12px;
            border-radius: 8px;
            border: none;
            background: var(--success);
            color: #ffffff;
            font-size: 15px;
            font-weight: 800;
            cursor: pointer;
            letter-spacing: 0.5px;
            transition: background 0.15s ease;
        }
        .btn-start-exam:hover:not(:disabled) {
            background: #15803d;
        }
        .btn-start-exam:disabled {
            opacity: 0.45;
            cursor: not-allowed;
            background: var(--gray-palette);
        }

        /* Slimline 42px Adaptive Header */
        .cbt-header {
            background: #0f172a;
            color: #ffffff;
            height: var(--header-h);
            padding: 0 12px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            position: sticky;
            top: 0;
            z-index: 100;
            box-shadow: 0 1px 4px rgba(0,0,0,0.15);
            flex-shrink: 0;
        }

        .header-left {
            display: flex;
            align-items: center;
            gap: 8px;
            min-width: 0;
        }

        .header-sub-badge {
            font-size: 11.5px;
            font-weight: 700;
            color: #f8fafc;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            max-width: 160px;
        }

        .header-q-progress {
            font-size: 11.5px;
            padding: 2px 7px;
            background: rgba(255,255,255,0.12);
            border-radius: 4px;
            color: #38bdf8;
            font-weight: 700;
            white-space: nowrap;
        }

        .header-right {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-shrink: 0;
        }

        .timer-box {
            display: flex;
            align-items: center;
            gap: 5px;
            background: rgba(13, 148, 136, 0.25);
            border: 1px solid var(--teal);
            padding: 3px 8px;
            border-radius: 6px;
            color: #5eead4;
            font-family: monospace;
            font-size: 13.5px;
            font-weight: 800;
            letter-spacing: 0.5px;
        }
        .timer-box.warning {
            background: rgba(220, 38, 38, 0.25);
            border-color: var(--danger);
            color: #fca5a5;
            animation: pulse-warn 1s infinite alternate;
        }

        .font-scale-group {
            display: flex;
            gap: 2px;
            background: rgba(255,255,255,0.1);
            padding: 2px;
            border-radius: 4px;
        }
        .btn-font-scale {
            background: transparent;
            border: none;
            color: #cbd5e1;
            padding: 2px 5px;
            font-size: 11px;
            font-weight: 700;
            cursor: pointer;
            border-radius: 3px;
        }
        .btn-font-scale:hover {
            background: rgba(255,255,255,0.2);
            color: #ffffff;
        }

        .btn-hdr-action {
            background: rgba(255,255,255,0.12);
            border: 1px solid rgba(255,255,255,0.2);
            color: #ffffff;
            padding: 4px 8px;
            border-radius: 5px;
            font-size: 11px;
            font-weight: 700;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 4px;
            white-space: nowrap;
        }
        .btn-hdr-action:hover {
            background: rgba(255,255,255,0.22);
        }

        .btn-toggle-palette {
            display: none;
        }

        /* Main CBT Viewport Layout */
        .cbt-container {
            display: none;
            flex: 1;
            max-width: 1400px;
            margin: 0 auto;
            width: 100%;
            height: calc(100vh - var(--header-h));
            overflow: hidden;
            position: relative;
        }

        .cbt-main-panel {
            flex: 1;
            display: flex;
            flex-direction: column;
            height: 100%;
            min-width: 0;
            background: var(--bg-page);
        }

        /* Independent Scrollable Question Area */
        .q-scroll-area {
            flex: 1;
            overflow-y: auto;
            -webkit-overflow-scrolling: touch;
            padding: 14px 18px;
        }

        .q-meta-bar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding-bottom: 8px;
            border-bottom: 1.5px solid var(--border-color);
            margin-bottom: 12px;
        }

        .q-num-tag {
            font-size: 15px;
            font-weight: 800;
            color: var(--text-main);
        }

        .q-marking-tag {
            display: flex;
            gap: 6px;
            font-size: 11.5px;
            font-weight: 700;
        }

        .mark-pos {
            color: var(--success);
            background: var(--success-light);
            padding: 1px 7px;
            border-radius: 4px;
        }
        .mark-neg {
            color: var(--danger);
            background: var(--danger-light);
            padding: 1px 7px;
            border-radius: 4px;
        }

        /* Question Stem Card */
        .q-card {
            background: var(--bg-card);
            border: 1.5px solid var(--border-color);
            border-radius: 9px;
            padding: 16px;
            margin-bottom: 14px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.03);
        }

        .q-text {
            font-size: var(--q-font-size);
            line-height: 1.6;
            color: var(--text-main);
            word-break: break-word;
        }

        .q-formula-box {
            margin-top: 8px;
            padding: 8px 12px;
            background: #f1f5f9;
            border-left: 3px solid var(--primary);
            font-family: monospace;
            font-size: 13.5px;
            border-radius: 0 6px 6px 0;
        }

        /* Options List */
        .options-list {
            display: flex;
            flex-direction: column;
            gap: 9px;
            margin-bottom: 14px;
        }

        .option-item {
            display: flex;
            align-items: center;
            gap: 12px;
            min-height: 48px;
            padding: 10px 14px;
            background: var(--bg-card);
            border: 1.5px solid var(--border-color);
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.12s ease;
            user-select: none;
        }
        .option-item:hover {
            border-color: var(--primary);
            background: var(--primary-light);
        }
        .option-item.selected {
            border-color: var(--primary);
            background: var(--primary-light);
            box-shadow: 0 0 0 1px var(--primary);
        }

        .opt-bubble {
            width: 30px;
            height: 30px;
            border-radius: 50%;
            border: 2px solid var(--border-color);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 12.5px;
            font-weight: 800;
            color: var(--text-muted);
            background: #ffffff;
            flex-shrink: 0;
            transition: all 0.12s ease;
        }
        .option-item.selected .opt-bubble {
            border-color: var(--primary);
            background: var(--primary);
            color: #ffffff;
        }

        .opt-text {
            font-size: var(--q-font-size);
            line-height: 1.45;
            color: var(--text-main);
            flex: 1;
        }

        /* Sticky Bottom Toolbar */
        .q-actions-bar {
            background: #ffffff;
            border-top: 1.5px solid var(--border-color);
            padding: 10px 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
            flex-shrink: 0;
            box-shadow: 0 -2px 8px rgba(0,0,0,0.04);
            flex-wrap: wrap;
        }

        .btn-cbt {
            padding: 8px 14px;
            border-radius: 6px;
            font-size: 12.5px;
            font-weight: 700;
            cursor: pointer;
            border: none;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 5px;
            transition: all 0.12s ease;
            white-space: nowrap;
        }
        .btn-cbt:disabled {
            opacity: 0.45;
            cursor: not-allowed;
        }

        .btn-prev {
            background: #ffffff;
            color: var(--text-main);
            border: 1.5px solid var(--border-color);
        }
        .btn-prev:hover:not(:disabled) { background: #f1f5f9; }

        .btn-clear {
            background: #ffffff;
            color: var(--warning);
            border: 1.5px solid var(--warning);
        }
        .btn-clear:hover:not(:disabled) { background: var(--warning-light); }

        .btn-review {
            background: #ffffff;
            color: var(--purple);
            border: 1.5px solid var(--purple);
        }
        .btn-review:hover:not(:disabled) { background: var(--purple-light); }

        .btn-save-next {
            background: var(--primary);
            color: #ffffff;
        }
        .btn-save-next:hover:not(:disabled) { background: var(--primary-hover); }

        /* Side Palette Panel */
        .cbt-side-panel {
            width: 300px;
            background: var(--bg-card);
            border-left: 1.5px solid var(--border-color);
            display: flex;
            flex-direction: column;
            height: 100%;
            overflow-y: auto;
            flex-shrink: 0;
        }

        .candidate-card {
            padding: 10px 14px;
            background: #f1f5f9;
            border-bottom: 1.5px solid var(--border-color);
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .cand-avatar-box {
            width: 44px;
            height: 48px;
            border-radius: 4px;
            border: 1.5px solid #94a3b8;
            background: #ffffff;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
            box-shadow: inset 0 0 4px rgba(0,0,0,0.06);
        }
        .cand-info h4 {
            font-size: 13.5px;
            font-weight: 800;
            color: var(--text-main);
            margin: 0;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            max-width: 190px;
        }
        .cand-info p {
            font-size: 11px;
            color: var(--text-muted);
            margin-top: 2px;
        }

        .palette-legend {
            padding: 10px 12px;
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 8px;
            border-bottom: 1.5px solid var(--border-color);
            font-size: 11px;
            background: #f8fafc;
        }
        .legend-item {
            display: flex;
            align-items: center;
            gap: 7px;
            color: var(--text-main);
            font-size: 10.5px;
            font-weight: 600;
        }
        .legend-pill {
            width: 24px;
            height: 24px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 10.5px;
            font-weight: 800;
            flex-shrink: 0;
        }
        .pill-answered {
            background: #22c55e;
            color: #ffffff;
            clip-path: polygon(0% 0%, 100% 0%, 100% 75%, 50% 100%, 0% 75%);
        }
        .pill-not-answered {
            background: #ef4444;
            color: #ffffff;
            clip-path: polygon(0% 20%, 50% 0%, 100% 20%, 100% 100%, 0% 100%);
        }
        .pill-not-visited {
            background: #e2e8f0;
            color: #1e293b;
            border-radius: 4px;
            border: 1px solid #cbd5e1;
        }
        .pill-review {
            background: #7c3aed;
            color: #ffffff;
            border-radius: 50%;
        }
        .pill-ans-review {
            background: #7c3aed;
            color: #ffffff;
            border-radius: 50%;
            position: relative;
        }
        .pill-ans-review::after {
            content: "";
            position: absolute;
            bottom: 1px;
            right: 1px;
            width: 7px;
            height: 7px;
            background: #22c55e;
            border-radius: 50%;
            border: 1.5px solid #ffffff;
        }

        .palette-box {
            padding: 12px;
            flex: 1;
            overflow-y: auto;
        }
        .palette-title {
            font-size: 11.5px;
            font-weight: 800;
            color: #1e293b;
            margin-bottom: 8px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .palette-grid {
            display: grid;
            grid-template-columns: repeat(5, 1fr);
            gap: 7px;
        }

        .q-palette-btn {
            height: 38px;
            border: none;
            font-size: 13px;
            font-weight: 800;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: transform 0.1s ease;
            position: relative;
        }
        .q-palette-btn:hover {
            transform: scale(1.08);
        }
        .q-palette-btn.active-q {
            box-shadow: 0 0 0 2.5px #0f172a !important;
            font-weight: 900;
            transform: scale(1.05);
        }
        .q-palette-btn.st-not-visited {
            background: #e2e8f0;
            color: #1e293b;
            border-radius: 4px;
            border: 1px solid #cbd5e1;
        }
        .q-palette-btn.st-not-answered {
            background: #ef4444;
            color: #ffffff;
            clip-path: polygon(0% 20%, 50% 0%, 100% 20%, 100% 100%, 0% 100%);
        }
        .q-palette-btn.st-answered {
            background: #22c55e;
            color: #ffffff;
            clip-path: polygon(0% 0%, 100% 0%, 100% 75%, 50% 100%, 0% 75%);
        }
        .q-palette-btn.st-review {
            background: #7c3aed;
            color: #ffffff;
            border-radius: 50%;
        }
        .q-palette-btn.st-ans-review {
            background: #7c3aed;
            color: #ffffff;
            border-radius: 50%;
            position: relative;
        }
        .q-palette-btn.st-ans-review::after {
            content: "";
            position: absolute;
            bottom: 2px;
            right: 2px;
            width: 8px;
            height: 8px;
            background: #22c55e;
            border-radius: 50%;
            border: 1.5px solid #ffffff;
        }

        .palette-footer {
            padding: 12px;
            border-top: 1px solid var(--border-color);
            background: #f8fafc;
        }
        .btn-submit-exam {
            width: 100%;
            background: #0f172a;
            color: #ffffff;
            padding: 11px;
            font-size: 13.5px;
            font-weight: 800;
            letter-spacing: 0.5px;
            text-transform: uppercase;
        }
        .btn-submit-exam:hover { background: #1e293b; }

        /* Modals */
        .modal-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0,0,0,0.6);
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 500;
            padding: 14px;
        }
        .modal-card {
            background: #ffffff;
            border-radius: 12px;
            max-width: 500px;
            width: 100%;
            padding: 22px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.25);
            max-height: 90vh;
            overflow-y: auto;
        }
        .modal-title {
            font-size: 17px;
            font-weight: 800;
            margin-bottom: 8px;
            color: var(--text-main);
        }
        .modal-summary-table {
            width: 100%;
            border-collapse: collapse;
            margin: 14px 0;
            font-size: 13px;
        }
        .modal-summary-table td, .modal-summary-table th {
            padding: 7px 10px;
            border: 1px solid var(--border-color);
        }
        .modal-summary-table th {
            background: #f8fafc;
            text-align: left;
            font-weight: 700;
        }
        .modal-actions {
            display: flex;
            gap: 8px;
            justify-content: flex-end;
            margin-top: 16px;
        }

        /* Question Paper Modal */
        #qp-modal .modal-card {
            max-width: 750px;
        }
        .qp-item {
            padding: 12px;
            border-bottom: 1px solid var(--border-color);
        }
        .qp-item-hdr {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 6px;
        }
        .btn-jump-q {
            padding: 3px 8px;
            font-size: 11px;
            font-weight: 700;
            background: var(--primary);
            color: #ffffff;
            border: none;
            border-radius: 4px;
            cursor: pointer;
        }

        /* Results / Scorecard View */
        #results-screen {
            display: none;
            max-width: 1050px;
            margin: 0 auto;
            width: 100%;
            padding: 20px 14px;
        }

        .score-hero-card {
            background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
            border-radius: 12px;
            padding: 24px;
            color: #ffffff;
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
            box-shadow: 0 4px 14px rgba(0,0,0,0.12);
            flex-wrap: wrap;
            gap: 16px;
        }
        .score-hero-left h2 {
            font-size: 22px;
            font-weight: 800;
            margin-bottom: 4px;
        }
        .score-hero-left p {
            font-size: 13px;
            color: #cbd5e1;
        }
        .score-badge-box {
            display: flex;
            align-items: baseline;
            gap: 6px;
            background: rgba(255,255,255,0.12);
            padding: 10px 18px;
            border-radius: 10px;
            border: 1px solid rgba(255,255,255,0.2);
        }
        .score-number {
            font-size: 34px;
            font-weight: 900;
            color: #38bdf8;
        }
        .score-total {
            font-size: 16px;
            color: #94a3b8;
            font-weight: 700;
        }

        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
            gap: 12px;
            margin-bottom: 20px;
        }
        .stat-card {
            background: var(--bg-card);
            border: 1.5px solid var(--border-color);
            border-radius: 8px;
            padding: 14px;
            display: flex;
            flex-direction: column;
            gap: 3px;
        }
        .stat-card .st-val {
            font-size: 22px;
            font-weight: 900;
        }
        .stat-card .st-label {
            font-size: 11px;
            font-weight: 700;
            text-transform: uppercase;
            color: var(--text-muted);
        }
        .stat-card.correct { border-color: var(--success); background: #f0fdf4; color: var(--success); }
        .stat-card.incorrect { border-color: var(--danger); background: #fef2f2; color: var(--danger); }
        .stat-card.accuracy { border-color: var(--teal); background: var(--teal-light); color: var(--teal); }

        .results-actions-bar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 18px;
            flex-wrap: wrap;
            gap: 10px;
        }
        .filter-tabs {
            display: flex;
            gap: 5px;
            flex-wrap: wrap;
        }
        .filter-btn {
            padding: 5px 12px;
            border-radius: 18px;
            font-size: 12px;
            font-weight: 700;
            border: 1.5px solid var(--border-color);
            background: #ffffff;
            cursor: pointer;
        }
        .filter-btn.active {
            background: #0f172a;
            color: #ffffff;
            border-color: #0f172a;
        }

        .results-action-buttons {
            display: flex;
            gap: 6px;
            flex-wrap: wrap;
        }
        .btn-share-res {
            background: var(--teal);
            color: #ffffff;
        }
        .btn-share-res:hover { background: #0f766e; }

        .btn-export-json {
            background: #475569;
            color: #ffffff;
        }
        .btn-export-json:hover { background: #334155; }

        .solutions-list {
            display: flex;
            flex-direction: column;
            gap: 14px;
        }
        .sol-card {
            background: var(--bg-card);
            border: 1.5px solid var(--border-color);
            border-radius: 9px;
            padding: 16px;
        }
        .sol-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 10px;
            padding-bottom: 6px;
            border-bottom: 1px solid var(--border-color);
        }
        .sol-qnum {
            font-size: 14.5px;
            font-weight: 800;
        }
        .sol-tag {
            font-size: 11.5px;
            font-weight: 800;
            padding: 2px 8px;
            border-radius: 5px;
        }
        .sol-tag.tag-correct { background: var(--success-light); color: var(--success); }
        .sol-tag.tag-incorrect { background: var(--danger-light); color: var(--danger); }
        .sol-tag.tag-unattempted { background: #f1f5f9; color: var(--text-muted); }

        .sol-qtext {
            font-size: 14.5px;
            line-height: 1.55;
            margin-bottom: 12px;
        }
        .sol-options-box {
            display: flex;
            flex-direction: column;
            gap: 7px;
            margin-bottom: 14px;
        }
        .sol-opt-row {
            display: flex;
            align-items: center;
            gap: 9px;
            padding: 8px 12px;
            border-radius: 6px;
            border: 1.5px solid var(--border-color);
            font-size: 13.5px;
        }
        .sol-opt-row.is-correct {
            border-color: var(--success);
            background: #f0fdf4;
            font-weight: 700;
        }
        .sol-opt-row.is-wrong-selection {
            border-color: var(--danger);
            background: #fef2f2;
            font-weight: 700;
        }
        .sol-opt-badge {
            width: 22px;
            height: 22px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 11.5px;
            font-weight: 800;
            background: #e2e8f0;
            flex-shrink: 0;
        }
        .sol-opt-row.is-correct .sol-opt-badge {
            background: var(--success);
            color: #ffffff;
        }
        .sol-opt-row.is-wrong-selection .sol-opt-badge {
            background: var(--danger);
            color: #ffffff;
        }

        .sol-exp-box {
            background: #f8fafc;
            border-left: 3.5px solid var(--teal);
            border-radius: 0 7px 7px 0;
            padding: 12px 14px;
            font-size: 13.5px;
            line-height: 1.55;
        }
        .sol-exp-title {
            font-size: 12px;
            font-weight: 800;
            color: var(--teal);
            text-transform: uppercase;
            letter-spacing: 0.5px;
            margin-bottom: 5px;
        }

        /* Toast Notice */
        #cbt-toast {
            position: fixed;
            bottom: 24px;
            left: 50%;
            transform: translateX(-50%);
            background: #0f172a;
            color: #ffffff;
            padding: 9px 18px;
            border-radius: 20px;
            font-size: 12.5px;
            font-weight: 700;
            z-index: 2000;
            display: none;
            box-shadow: 0 4px 12px rgba(0,0,0,0.2);
            animation: toast-fade 0.2s ease;
        }
        @keyframes toast-fade {
            from { opacity: 0; transform: translate(-50%, 10px); }
            to { opacity: 1; transform: translate(-50%, 0); }
        }

        /* Mobile & Tablet Responsiveness */
        @media (max-width: 850px) {
            .btn-toggle-palette {
                display: inline-flex;
            }

            .cbt-side-panel {
                position: absolute;
                top: 0;
                right: 0;
                width: 85%;
                max-width: 310px;
                box-shadow: -4px 0 20px rgba(0,0,0,0.25);
                transform: translateX(100%);
                transition: transform 0.25s ease-in-out;
                z-index: 200;
            }
            .cbt-side-panel.open {
                transform: translateX(0);
            }
            .q-scroll-area {
                padding: 12px 14px;
            }
        }

        /* Print Media Styles */
        @media print {
            .cbt-header, .q-actions-bar, .results-actions-bar, .btn-hdr-action, .font-scale-group, #cbt-screen, #lobby-screen, #submit-modal, #qp-modal {
                display: none !important;
            }
            #results-screen {
                display: block !important;
                padding: 0;
                max-width: 100%;
            }
            .score-hero-card {
                background: #ffffff !important;
                color: #000000 !important;
                border: 2px solid #000000;
            }
            .score-number { color: #000000; }
            .sol-card {
                break-inside: avoid;
                page-break-inside: avoid;
                border: 1px solid #000000;
                margin-bottom: 12px;
            }
        }
    </style>
</head>
<body>

    <!-- Stage 1: Candidate Verification & Instructions Lobby (Pre-Exam) -->
    <div id="lobby-screen">
        <div class="lobby-card">
            <div class="lobby-header">
                <div class="lobby-inst">$safeInst</div>
                <h1 class="lobby-title">$safeTitle</h1>
                <div class="lobby-subject">$safeSubject &bull; Series: $safeBooklet</div>
            </div>

            <!-- Candidate Inputs -->
            <div class="lobby-inputs">
                <div class="input-group">
                    <label for="cand-name-input">Candidate Full Name *</label>
                    <input type="text" id="cand-name-input" class="lobby-input" value="" placeholder="Enter your full name" />
                </div>
                <div class="input-group">
                    <label for="cand-roll-input">Roll Number / Student ID *</label>
                    <input type="text" id="cand-roll-input" class="lobby-input" value="$safeRoll" placeholder="Enter assigned roll number" />
                </div>
            </div>

            <!-- Test Specifications -->
            <div class="lobby-meta-grid">
                <div class="meta-stat-item">
                    <div class="val">${paper.questions.size}</div>
                    <div class="lbl">Questions</div>
                </div>
                <div class="meta-stat-item">
                    <div class="val">$durationMinutes m</div>
                    <div class="lbl">Duration</div>
                </div>
                <div class="meta-stat-item">
                    <div class="val">+$posFormatted</div>
                    <div class="lbl">Per Correct</div>
                </div>
                <div class="meta-stat-item">
                    <div class="val">-$negFormatted</div>
                    <div class="lbl">Penalty</div>
                </div>
            </div>

            <!-- Rules Summary -->
            <div class="lobby-rules-box">
                <ul>
                    <li>The clock will be set at the server. The countdown timer at the top displays remaining time.</li>
                    <li>Questions can be answered by clicking the appropriate option (A, B, C, or D).</li>
                    <li>Desktop shortcuts: Keys 1..4 or A..D to select, Enter or Arrow Keys to navigate.</li>
                    <li>Use <b>Mark for Review & Next</b> to flag questions for subsequent reconsideration.</li>
                    <li>Your work is auto-saved locally. Navigating away or closing the tab will trigger an alert.</li>
                </ul>
            </div>

            <label class="lobby-agree-row">
                <input type="checkbox" id="rules-agree-check" onchange="toggleStartButton()" />
                <span>I have read and understood all examination instructions.</span>
            </label>

            <button class="btn-start-exam" id="btn-start-exam" disabled onclick="startExamSession()">
                Begin Examination &rarr;
            </button>
        </div>
    </div>

    <!-- Stage 2: Live Examination Portal -->

    <!-- Slimline 42px Adaptive Header -->
    <header class="cbt-header" id="cbt-header">
        <div class="header-left">
            <span class="header-sub-badge">$safeSubject</span>
            <span class="header-q-progress" id="hdr-q-indicator">Q 1 / ${paper.questions.size}</span>
        </div>
        <div class="header-right">
            <!-- Font Sizing Group -->
            <div class="font-scale-group" title="Adjust text size">
                <button class="btn-font-scale" onclick="adjustFontSize(-1)">A-</button>
                <button class="btn-font-scale" onclick="adjustFontSize(0)">A</button>
                <button class="btn-font-scale" onclick="adjustFontSize(1)">A+</button>
            </div>

            <!-- Question Paper Overview Button -->
            <button class="btn-hdr-action" onclick="openQuestionPaperModal()">
                Paper
            </button>

            <!-- Digital Timer Pill -->
            <div class="timer-box" id="timer-box">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3">
                    <circle cx="12" cy="12" r="10"></circle>
                    <polyline points="12 6 12 12 16 14"></polyline>
                </svg>
                <span id="timer-display">00:00:00</span>
            </div>

            <!-- Mobile Palette Toggle -->
            <button class="btn-hdr-action btn-toggle-palette" id="btn-toggle-palette" onclick="toggleMobilePalette()">
                [#]
            </button>
        </div>
    </header>

    <!-- Main CBT Screen -->
    <div id="cbt-screen" class="cbt-container">
        <!-- Question Pane -->
        <main class="cbt-main-panel">
            <div class="q-scroll-area">
                <div class="q-meta-bar">
                    <div class="q-num-tag" id="q-title-display">Question 1</div>
                    <div class="q-marking-tag">
                        <span class="mark-pos">+4.00</span>
                        <span class="mark-neg">-1.00</span>
                    </div>
                </div>

                <!-- Stem -->
                <div class="q-card">
                    <div class="q-text" id="q-stem-display">Loading question...</div>
                    <div class="q-formula-box" id="q-formula-box" style="display: none;"></div>
                </div>

                <!-- Options -->
                <div class="options-list" id="options-container">
                    <!-- Populated options A, B, C, D -->
                </div>
            </div>

            <!-- Sticky Bottom Toolbar -->
            <div class="q-actions-bar">
                <div style="display: flex; gap: 6px;">
                    <button class="btn-cbt btn-prev" id="btn-prev" onclick="goToPrevQuestion()">
                        &larr; Prev
                    </button>
                    <button class="btn-cbt btn-clear" id="btn-clear" onclick="clearCurrentResponse()">
                        Clear
                    </button>
                </div>
                <div style="display: flex; gap: 6px;">
                    <button class="btn-cbt btn-review" id="btn-review" onclick="markForReviewAndNext()">
                        Review & Next
                    </button>
                    <button class="btn-cbt btn-save-next" id="btn-save-next" onclick="saveAndNext()">
                        Save & Next &rarr;
                    </button>
                </div>
            </div>
        </main>

        <!-- Side Palette Drawer -->
        <aside class="cbt-side-panel" id="cbt-side-panel">
            <div class="candidate-card">
                <div class="cand-avatar-box" id="cand-avatar-box" title="Candidate Identity Photo">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#64748b" stroke-width="2">
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                        <circle cx="12" cy="7" r="4"></circle>
                    </svg>
                    <span style="font-size: 7.5px; font-weight: 800; color: #64748b; margin-top: 1px;">PHOTO</span>
                </div>
                <div class="cand-info">
                    <h4 id="panel-cand-name">Student</h4>
                    <p>Roll: <span id="panel-cand-roll" style="font-weight: 700; color: var(--text-main);">$safeRoll</span></p>
                    <p style="font-size: 10px; color: var(--teal); font-weight: 700;">Node: LAB-03 &bull; C-01</p>
                </div>
            </div>

            <!-- Authentic 5-State NTA / TCS Examination Legend -->
            <div class="palette-legend">
                <div class="legend-item">
                    <div class="legend-pill pill-answered" id="leg-ans-cnt">0</div>
                    <span>Answered</span>
                </div>
                <div class="legend-item">
                    <div class="legend-pill pill-not-answered" id="leg-unans-cnt">0</div>
                    <span>Not Answered</span>
                </div>
                <div class="legend-item">
                    <div class="legend-pill pill-not-visited" id="leg-visit-cnt">0</div>
                    <span>Not Visited</span>
                </div>
                <div class="legend-item">
                    <div class="legend-pill pill-review" id="leg-rev-cnt">0</div>
                    <span>Marked Review</span>
                </div>
                <div class="legend-item" style="grid-column: span 2;">
                    <div class="legend-pill pill-ans-review" id="leg-ans-rev-cnt">0</div>
                    <span>Answered &amp; Marked for Review</span>
                </div>
            </div>

            <!-- Palette Grid -->
            <div class="palette-box">
                <div class="palette-title">Question Palette</div>
                <div class="palette-grid" id="palette-grid">
                    <!-- Buttons 1 to N -->
                </div>
            </div>

            <!-- Palette Footer -->
            <div class="palette-footer">
                <button class="btn-cbt btn-submit-exam" onclick="openSubmitModal()">
                    Submit Test
                </button>
            </div>
        </aside>
    </div>

    <!-- Question Paper Overview Modal -->
    <div class="modal-overlay" id="qp-modal">
        <div class="modal-card">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                <h3 class="modal-title">Complete Question Paper</h3>
                <button class="btn-cbt btn-prev" onclick="closeQuestionPaperModal()">✕ Close</button>
            </div>
            <div id="qp-modal-content">
                <!-- Dynamically populated question list -->
            </div>
        </div>
    </div>

    <!-- Submit Confirmation Modal -->
    <div class="modal-overlay" id="submit-modal">
        <div class="modal-card">
            <h3 class="modal-title">Submit Examination</h3>
            <p style="font-size: 13px; color: var(--text-muted);">
                Are you sure you want to finish and submit your examination? Review your attempt summary below:
            </p>
            <table class="modal-summary-table">
                <tr><th>Total Questions</th><td id="mod-tot">0</td></tr>
                <tr><th>Answered</th><td id="mod-ans" style="color: var(--success); font-weight: 700;">0</td></tr>
                <tr><th>Not Answered</th><td id="mod-unans" style="color: var(--danger); font-weight: 700;">0</td></tr>
                <tr><th>Marked for Review</th><td id="mod-rev" style="color: var(--purple); font-weight: 700;">0</td></tr>
                <tr><th>Answered &amp; Marked for Review</th><td id="mod-ans-rev" style="color: var(--purple); font-weight: 700;">0</td></tr>
                <tr><th>Not Visited</th><td id="mod-notvis">0</td></tr>
            </table>
            <div id="unanswered-warning" style="display:none; color: var(--danger); font-size: 12px; font-weight: 700; margin-bottom: 10px;">
                ⚠️ You have unanswered questions remaining.
            </div>
            <div class="modal-actions">
                <button class="btn-cbt btn-prev" onclick="closeSubmitModal()">Resume Test</button>
                <button class="btn-cbt btn-submit-exam" style="background: var(--success);" onclick="confirmSubmitExam()">Confirm & Submit</button>
            </div>
        </div>
    </div>

    <!-- Stage 3: Results, Scorecard & Detailed Solutions -->
    <div id="results-screen">
        <div class="score-hero-card">
            <div class="score-hero-left">
                <h2>Examination Performance Report</h2>
                <p>$safeTitle &bull; $safeSubject</p>
                <p style="margin-top: 4px; font-size: 12px; color: #94a3b8;">
                    Candidate: <b id="res-cand-name" style="color:#ffffff;">Candidate</b> (Roll: <span id="res-cand-roll">$safeRoll</span>) &bull; Submitted: <span id="res-date"></span>
                </p>
            </div>
            <div class="score-badge-box">
                <span class="score-number" id="res-score">0</span>
                <span class="score-total" id="res-max-score">/ 0</span>
            </div>
        </div>

        <!-- Metrics Grid -->
        <div class="stats-grid">
            <div class="stat-card">
                <span class="st-val" id="res-total-q">0</span>
                <span class="st-label">Total Questions</span>
            </div>
            <div class="stat-card correct">
                <span class="st-val" id="res-correct-cnt">0</span>
                <span class="st-label" id="res-correct-lbl">Correct</span>
            </div>
            <div class="stat-card incorrect">
                <span class="st-val" id="res-wrong-cnt">0</span>
                <span class="st-label" id="res-wrong-lbl">Incorrect</span>
            </div>
            <div class="stat-card">
                <span class="st-val" id="res-unattempt-cnt">0</span>
                <span class="st-label">Unattempted</span>
            </div>
            <div class="stat-card accuracy">
                <span class="st-val" id="res-accuracy">0%</span>
                <span class="st-label">Accuracy Rate</span>
            </div>
            <div class="stat-card">
                <span class="st-val" id="res-tab-switches">0</span>
                <span class="st-label">Tab Switches</span>
            </div>
        </div>

        <!-- Actions & Filters -->
        <div class="results-actions-bar">
            <div class="filter-tabs">
                <button class="filter-btn active" onclick="filterSolutions('ALL', this)">All (<span id="flt-all">0</span>)</button>
                <button class="filter-btn" onclick="filterSolutions('CORRECT', this)">Correct (<span id="flt-cor">0</span>)</button>
                <button class="filter-btn" onclick="filterSolutions('INCORRECT', this)">Incorrect (<span id="flt-inc">0</span>)</button>
                <button class="filter-btn" onclick="filterSolutions('UNATTEMPTED', this)">Unattempted (<span id="flt-una">0</span>)</button>
            </div>
            <div class="results-action-buttons">
                <button class="btn-cbt btn-share-res" onclick="shareScorecard()">
                    Share Result
                </button>
                <button class="btn-cbt btn-export-json" onclick="exportResultDataJson()">
                    Export JSON
                </button>
                <button class="btn-cbt btn-prev" onclick="window.print()">
                    Print Report
                </button>
                <button class="btn-cbt btn-prev" onclick="retakeExam()">
                    Retake
                </button>
            </div>
        </div>

        <!-- Solutions List -->
        <div class="solutions-list" id="solutions-container">
            <!-- Populated solution cards with full explanations -->
        </div>
    </div>

    <!-- Toast Notification -->
    <div id="cbt-toast">Notice</div>

    <!-- JavaScript Application Engine -->
    <script>
        const EXAM_DATA = $examDataJson;
        const STORAGE_KEY = 'CBT_SESSION_' + (EXAM_DATA.meta.bookletNumber || 'DEFAULT');

        let candidateName = 'Candidate';
        let candidateRoll = EXAM_DATA.meta.rollNumber || '$safeRoll';
        let currentIndex = 0;
        let answers = {};
        let reviewStatus = {};
        let visitedStatus = {};
        let timeRemaining = (EXAM_DATA.meta.durationMinutes || 60) * 60;
        let timerInterval = null;
        let examStarted = false;
        let isSubmitted = false;
        let activeFilter = 'ALL';
        let tabSwitchCount = 0;
        let currentFontScale = 1.0;

        // On DOM ready: check if previous session can be recovered
        window.addEventListener('DOMContentLoaded', () => {
            initPaletteGrid();
            initQuestionPaperModal();
            setupKeyboardShortcuts();
            setupTabSwitchMonitor();

            // Try recovery from sessionStorage
            const savedSession = sessionStorage.getItem(STORAGE_KEY);
            if (savedSession) {
                try {
                    const data = JSON.parse(savedSession);
                    if (data && !data.isSubmitted) {
                        candidateName = data.candidateName || 'Candidate';
                        candidateRoll = data.candidateRoll || '$safeRoll';
                        answers = data.answers || {};
                        reviewStatus = data.reviewStatus || {};
                        visitedStatus = data.visitedStatus || {};
                        timeRemaining = data.timeRemaining || timeRemaining;
                        tabSwitchCount = data.tabSwitchCount || 0;

                        document.getElementById('cand-name-input').value = candidateName;
                        document.getElementById('cand-roll-input').value = candidateRoll;
                        document.getElementById('rules-agree-check').checked = true;
                        document.getElementById('btn-start-exam').disabled = false;
                    }
                } catch (e) {
                    console.warn("Could not parse saved session", e);
                }
            }
        });

        // Safety on unload
        window.onbeforeunload = function(e) {
            if (examStarted && !isSubmitted) {
                e.preventDefault();
                e.returnValue = '';
            }
        };

        function showToast(msg) {
            const toast = document.getElementById('cbt-toast');
            toast.textContent = msg;
            toast.style.display = 'block';
            setTimeout(() => { toast.style.display = 'none'; }, 2500);
        }

        function toggleStartButton() {
            const agreed = document.getElementById('rules-agree-check').checked;
            document.getElementById('btn-start-exam').disabled = !agreed;
        }

        function startExamSession() {
            candidateName = document.getElementById('cand-name-input').value.trim() || 'Candidate';
            candidateRoll = document.getElementById('cand-roll-input').value.trim() || '$safeRoll';

            // Bind values
            document.getElementById('panel-cand-name').textContent = candidateName;
            document.getElementById('panel-cand-roll').textContent = candidateRoll;
            const letterEl = document.getElementById('cand-avatar-letter');
            if (letterEl) {
                letterEl.textContent = candidateName.charAt(0).toUpperCase();
            }

            document.getElementById('lobby-screen').style.display = 'none';
            document.getElementById('cbt-screen').style.display = 'flex';
            examStarted = true;

            loadQuestion(0);
            startCountdownTimer();
            saveSession();

            // Request fullscreen on desktop/tablets if possible
            if (document.documentElement.requestFullscreen) {
                document.documentElement.requestFullscreen().catch(() => {});
            }
        }

        function formatTimerDisplay(totalSeconds) {
            const h = Math.floor(totalSeconds / 3600);
            const m = Math.floor((totalSeconds % 3600) / 60);
            const s = totalSeconds % 60;
            return [h, m, s].map(v => String(v).padStart(2, '0')).join(':');
        }

        function startCountdownTimer() {
            const displayEl = document.getElementById('timer-display');
            const timerBox = document.getElementById('timer-box');
            displayEl.textContent = formatTimerDisplay(timeRemaining);

            timerInterval = setInterval(() => {
                if (timeRemaining > 0) {
                    timeRemaining--;
                    displayEl.textContent = formatTimerDisplay(timeRemaining);
                    if (timeRemaining <= 300) {
                        timerBox.classList.add('warning');
                    }
                    if (timeRemaining % 10 === 0) {
                        saveSession();
                    }
                } else {
                    clearInterval(timerInterval);
                    alert("Time has elapsed! Your examination will now be submitted.");
                    confirmSubmitExam();
                }
            }, 1000);
        }

        function adjustFontSize(delta) {
            if (delta === 0) currentFontScale = 1.0;
            else if (delta > 0 && currentFontScale < 1.3) currentFontScale += 0.1;
            else if (delta < 0 && currentFontScale > 0.8) currentFontScale -= 0.1;

            const newSize = (15.5 * currentFontScale).toFixed(1) + 'px';
            document.documentElement.style.setProperty('--q-font-size', newSize);
        }

        function setupKeyboardShortcuts() {
            window.addEventListener('keydown', (e) => {
                if (!examStarted || isSubmitted) return;
                // Ignore if active in input field
                if (document.activeElement && document.activeElement.tagName === 'INPUT') return;

                const key = e.key.toUpperCase();
                if (['1', '2', '3', '4', 'A', 'B', 'C', 'D'].includes(key)) {
                    let optKey = key;
                    if (key === '1') optKey = 'A';
                    if (key === '2') optKey = 'B';
                    if (key === '3') optKey = 'C';
                    if (key === '4') optKey = 'D';
                    selectOption(optKey);
                } else if (e.key === 'ArrowRight' || e.key === 'Enter') {
                    saveAndNext();
                } else if (e.key === 'ArrowLeft') {
                    goToPrevQuestion();
                } else if (e.key === 'Backspace' || e.key === 'Delete') {
                    clearCurrentResponse();
                } else if (key === 'M') {
                    markForReviewAndNext();
                } else if (e.key === 'Escape') {
                    closeSubmitModal();
                    closeQuestionPaperModal();
                    toggleMobilePalette(false);
                }
            });
        }

        function setupTabSwitchMonitor() {
            document.addEventListener('visibilitychange', () => {
                if (examStarted && !isSubmitted && document.visibilityState === 'hidden') {
                    tabSwitchCount++;
                    showToast("Notice: Tab switch detected (" + tabSwitchCount + ")");
                    saveSession();
                }
            });
        }

        function saveSession() {
            if (!examStarted || isSubmitted) return;
            try {
                sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
                    candidateName,
                    candidateRoll,
                    answers,
                    reviewStatus,
                    visitedStatus,
                    timeRemaining,
                    tabSwitchCount,
                    isSubmitted: false
                }));
            } catch (e) {}
        }

        function initPaletteGrid() {
            const grid = document.getElementById('palette-grid');
            grid.innerHTML = '';
            EXAM_DATA.questions.forEach((q, idx) => {
                const btn = document.createElement('button');
                btn.className = 'q-palette-btn st-not-visited';
                btn.id = 'palette-btn-' + idx;
                btn.textContent = q.num || (idx + 1);
                btn.onclick = () => {
                    loadQuestion(idx);
                    if (window.innerWidth <= 850) {
                        toggleMobilePalette(false);
                    }
                };
                grid.appendChild(btn);
            });
        }

        function initQuestionPaperModal() {
            const container = document.getElementById('qp-modal-content');
            container.innerHTML = '';
            EXAM_DATA.questions.forEach((q, idx) => {
                const item = document.createElement('div');
                item.className = 'qp-item';

                const hdr = document.createElement('div');
                hdr.className = 'qp-item-hdr';
                hdr.innerHTML = '<span style="font-weight:700; font-size:13px;">Q ' + (q.num || (idx + 1)) + '</span>';

                const jumpBtn = document.createElement('button');
                jumpBtn.className = 'btn-jump-q';
                jumpBtn.textContent = 'Go to Question';
                jumpBtn.onclick = () => {
                    closeQuestionPaperModal();
                    loadQuestion(idx);
                };
                hdr.appendChild(jumpBtn);

                const stem = document.createElement('div');
                stem.style.fontSize = '13.5px';
                stem.style.marginBottom = '6px';
                stem.innerHTML = q.stemHtml || q.stemRaw || '';

                item.appendChild(hdr);
                item.appendChild(stem);
                container.appendChild(item);
            });
        }

        function openQuestionPaperModal() {
            document.getElementById('qp-modal').style.display = 'flex';
        }
        function closeQuestionPaperModal() {
            document.getElementById('qp-modal').style.display = 'none';
        }

        function loadQuestion(index) {
            if (index < 0 || index >= EXAM_DATA.questions.length) return;
            currentIndex = index;
            visitedStatus[index] = true;

            const q = EXAM_DATA.questions[index];
            document.getElementById('hdr-q-indicator').textContent = 'Q ' + (q.num || (index + 1)) + ' / ' + EXAM_DATA.questions.length;
            document.getElementById('q-title-display').textContent = 'Question ' + (q.num || (index + 1));
            document.getElementById('q-stem-display').innerHTML = q.stemHtml || q.stemRaw || '';

            const formulaBox = document.getElementById('q-formula-box');
            if (q.formula && q.formula.trim().length > 0) {
                formulaBox.style.display = 'block';
                formulaBox.textContent = q.formula;
            } else {
                formulaBox.style.display = 'none';
            }

            const optsContainer = document.getElementById('options-container');
            optsContainer.innerHTML = '';
            const selectedOpt = answers[index];

            q.options.forEach(opt => {
                const optItem = document.createElement('div');
                optItem.className = 'option-item' + (selectedOpt === opt.key ? ' selected' : '');
                optItem.onclick = () => selectOption(opt.key);

                const bubble = document.createElement('div');
                bubble.className = 'opt-bubble';
                bubble.textContent = opt.key;

                const textSpan = document.createElement('div');
                textSpan.className = 'opt-text';
                textSpan.innerHTML = opt.rendered || opt.text || '';

                optItem.appendChild(bubble);
                optItem.appendChild(textSpan);
                optsContainer.appendChild(optItem);
            });

            document.getElementById('btn-prev').disabled = (index === 0);
            const saveNextBtn = document.getElementById('btn-save-next');
            if (index === EXAM_DATA.questions.length - 1) {
                saveNextBtn.innerHTML = 'Save & Submit &rarr;';
            } else {
                saveNextBtn.innerHTML = 'Save & Next &rarr;';
            }

            updatePaletteItem(index);
            updateLegendCounts();
            saveSession();
        }

        function selectOption(key) {
            answers[currentIndex] = key;
            loadQuestion(currentIndex);
        }

        function clearCurrentResponse() {
            delete answers[currentIndex];
            delete reviewStatus[currentIndex];
            loadQuestion(currentIndex);
        }

        function markForReviewAndNext() {
            reviewStatus[currentIndex] = true;
            goToNextQuestion();
        }

        function saveAndNext() {
            delete reviewStatus[currentIndex];
            if (currentIndex === EXAM_DATA.questions.length - 1) {
                openSubmitModal();
            } else {
                goToNextQuestion();
            }
        }

        function goToPrevQuestion() {
            if (currentIndex > 0) loadQuestion(currentIndex - 1);
        }

        function goToNextQuestion() {
            if (currentIndex < EXAM_DATA.questions.length - 1) {
                loadQuestion(currentIndex + 1);
            } else {
                updatePaletteItem(currentIndex);
                updateLegendCounts();
            }
        }

        function updatePaletteItem(idx) {
            const btn = document.getElementById('palette-btn-' + idx);
            if (!btn) return;
            btn.className = 'q-palette-btn';

            if (idx === currentIndex) btn.classList.add('active-q');

            const isAns = !!answers[idx];
            const isRev = !!reviewStatus[idx];
            const isVis = !!visitedStatus[idx];

            if (isAns && isRev) btn.classList.add('st-ans-review');
            else if (isRev) btn.classList.add('st-review');
            else if (isAns) btn.classList.add('st-answered');
            else if (isVis) btn.classList.add('st-not-answered');
            else btn.classList.add('st-not-visited');
        }

        function updateLegendCounts() {
            let answered = 0, notAnswered = 0, notVisited = 0, markedReview = 0, ansReview = 0;
            EXAM_DATA.questions.forEach((_, idx) => {
                const isAns = !!answers[idx];
                const isRev = !!reviewStatus[idx];
                const isVis = !!visitedStatus[idx];

                if (isRev && isAns) ansReview++;
                else if (isRev) markedReview++;
                else if (isAns) answered++;
                else if (isVis) notAnswered++;
                else notVisited++;
                updatePaletteItem(idx);
            });

            document.getElementById('leg-ans-cnt').textContent = answered;
            document.getElementById('leg-unans-cnt').textContent = notAnswered;
            document.getElementById('leg-visit-cnt').textContent = notVisited;
            document.getElementById('leg-rev-cnt').textContent = markedReview;
            const ansRevEl = document.getElementById('leg-ans-rev-cnt');
            if (ansRevEl) ansRevEl.textContent = ansReview;
        }

        function toggleMobilePalette(forceState) {
            const panel = document.getElementById('cbt-side-panel');
            if (typeof forceState === 'boolean') {
                if (forceState) panel.classList.add('open');
                else panel.classList.remove('open');
            } else {
                panel.classList.toggle('open');
            }
        }

        function openSubmitModal() {
            let answered = 0, notAnswered = 0, notVisited = 0, markedReview = 0, ansReview = 0;
            EXAM_DATA.questions.forEach((_, idx) => {
                const isAns = !!answers[idx];
                const isRev = !!reviewStatus[idx];
                const isVis = !!visitedStatus[idx];

                if (isRev && isAns) ansReview++;
                else if (isRev) markedReview++;
                else if (isAns) answered++;
                else if (isVis) notAnswered++;
                else notVisited++;
            });

            document.getElementById('mod-tot').textContent = EXAM_DATA.questions.length;
            document.getElementById('mod-ans').textContent = answered;
            document.getElementById('mod-unans').textContent = notAnswered;
            document.getElementById('mod-rev').textContent = markedReview;
            const modAnsRev = document.getElementById('mod-ans-rev');
            if (modAnsRev) modAnsRev.textContent = ansReview;
            document.getElementById('mod-notvis').textContent = notVisited;

            const warnEl = document.getElementById('unanswered-warning');
            if (notAnswered > 0 || notVisited > 0) {
                warnEl.style.display = 'block';
                warnEl.textContent = '⚠️ You have ' + (notAnswered + notVisited) + ' unanswered question(s).';
            } else {
                warnEl.style.display = 'none';
            }

            document.getElementById('submit-modal').style.display = 'flex';
        }

        function closeSubmitModal() {
            document.getElementById('submit-modal').style.display = 'none';
        }

        function confirmSubmitExam() {
            closeSubmitModal();
            clearInterval(timerInterval);
            isSubmitted = true;

            try {
                sessionStorage.removeItem(STORAGE_KEY);
            } catch (e) {}

            document.getElementById('cbt-screen').style.display = 'none';
            document.getElementById('cbt-header').style.display = 'none';
            document.getElementById('results-screen').style.display = 'block';

            renderPerformanceReport();
        }

        function renderPerformanceReport() {
            document.getElementById('res-date').textContent = new Date().toLocaleString();
            document.getElementById('res-cand-name').textContent = candidateName;
            document.getElementById('res-cand-roll').textContent = candidateRoll;

            let correctCount = 0, wrongCount = 0, unattemptedCount = 0;
            const posMarks = EXAM_DATA.meta.positiveMarks || 4.0;
            const negMarks = EXAM_DATA.meta.negativeMarks || 1.0;

            const solContainer = document.getElementById('solutions-container');
            solContainer.innerHTML = '';

            EXAM_DATA.questions.forEach((q, idx) => {
                const userAns = answers[idx];
                const officialAns = q.correctAnswer;
                const isAttempted = !!userAns;
                const isCorrect = isAttempted && (userAns === officialAns);

                if (!isAttempted) unattemptedCount++;
                else if (isCorrect) correctCount++;
                else wrongCount++;

                const card = document.createElement('div');
                card.className = 'sol-card';
                card.dataset.status = isCorrect ? 'CORRECT' : (!isAttempted ? 'UNATTEMPTED' : 'INCORRECT');

                const hdr = document.createElement('div');
                hdr.className = 'sol-header';

                const qNumEl = document.createElement('div');
                qNumEl.className = 'sol-qnum';
                qNumEl.textContent = 'Question ' + (q.num || (idx + 1));

                const tag = document.createElement('div');
                if (isCorrect) {
                    tag.className = 'sol-tag tag-correct';
                    tag.textContent = 'CORRECT (+' + posMarks.toFixed(2) + ')';
                } else if (!isAttempted) {
                    tag.className = 'sol-tag tag-unattempted';
                    tag.textContent = 'UNATTEMPTED (0.00)';
                } else {
                    tag.className = 'sol-tag tag-incorrect';
                    tag.textContent = 'INCORRECT (-' + negMarks.toFixed(2) + ')';
                }

                hdr.appendChild(qNumEl);
                hdr.appendChild(tag);
                card.appendChild(hdr);

                const stem = document.createElement('div');
                stem.className = 'sol-qtext';
                stem.innerHTML = q.stemHtml || q.stemRaw || '';
                card.appendChild(stem);

                const optsBox = document.createElement('div');
                optsBox.className = 'sol-options-box';

                q.options.forEach(opt => {
                    const row = document.createElement('div');
                    const isThisCorrect = (opt.key === officialAns);
                    const isThisSelected = (opt.key === userAns);

                    if (isThisCorrect) row.className = 'sol-opt-row is-correct';
                    else if (isThisSelected) row.className = 'sol-opt-row is-wrong-selection';
                    else row.className = 'sol-opt-row';

                    const badge = document.createElement('div');
                    badge.className = 'sol-opt-badge';
                    badge.textContent = opt.key;

                    const txt = document.createElement('div');
                    txt.style.flex = '1';
                    txt.innerHTML = opt.rendered || opt.text || '';

                    row.appendChild(badge);
                    row.appendChild(txt);

                    if (isThisSelected && isThisCorrect) {
                        const note = document.createElement('span');
                        note.style.fontSize = '11px';
                        note.style.color = 'var(--success)';
                        note.textContent = '✓ Your Answer (Correct)';
                        row.appendChild(note);
                    } else if (isThisSelected) {
                        const note = document.createElement('span');
                        note.style.fontSize = '11px';
                        note.style.color = 'var(--danger)';
                        note.textContent = '✗ Your Answer';
                        row.appendChild(note);
                    } else if (isThisCorrect) {
                        const note = document.createElement('span');
                        note.style.fontSize = '11px';
                        note.style.color = 'var(--success)';
                        note.textContent = '✓ Correct Answer';
                        row.appendChild(note);
                    }

                    optsBox.appendChild(row);
                });
                card.appendChild(optsBox);

                if (q.explanationHtml && q.explanationHtml.trim().length > 0) {
                    const expBox = document.createElement('div');
                    expBox.className = 'sol-exp-box';

                    const expTitle = document.createElement('div');
                    expTitle.className = 'sol-exp-title';
                    expTitle.innerHTML = '&#9733; Detailed Step-by-Step Explanation';

                    const expBody = document.createElement('div');
                    expBody.innerHTML = q.explanationHtml;

                    expBox.appendChild(expTitle);
                    expBox.appendChild(expBody);
                    card.appendChild(expBox);
                }

                solContainer.appendChild(card);
            });

            const rawScore = (correctCount * posMarks) - (wrongCount * negMarks);
            const maxMarks = EXAM_DATA.questions.length * posMarks;
            const attemptedTotal = correctCount + wrongCount;
            const accuracy = attemptedTotal > 0 ? Math.round((correctCount / attemptedTotal) * 100) : 0;

            document.getElementById('res-score').textContent = rawScore.toFixed(1);
            document.getElementById('res-max-score').textContent = '/ ' + maxMarks.toFixed(1);
            document.getElementById('res-total-q').textContent = EXAM_DATA.questions.length;
            document.getElementById('res-correct-cnt').textContent = correctCount;
            const corLbl = document.getElementById('res-correct-lbl');
            if (corLbl) corLbl.textContent = 'Correct (+' + posMarks.toFixed(2) + ')';
            document.getElementById('res-wrong-cnt').textContent = wrongCount;
            const wrgLbl = document.getElementById('res-wrong-lbl');
            if (wrgLbl) wrgLbl.textContent = 'Incorrect (-' + negMarks.toFixed(2) + ')';
            document.getElementById('res-unattempt-cnt').textContent = unattemptedCount;
            document.getElementById('res-accuracy').textContent = accuracy + '%';
            document.getElementById('res-tab-switches').textContent = tabSwitchCount;

            document.getElementById('flt-all').textContent = EXAM_DATA.questions.length;
            document.getElementById('flt-cor').textContent = correctCount;
            document.getElementById('flt-inc').textContent = wrongCount;
            document.getElementById('flt-una').textContent = unattemptedCount;
        }

        function filterSolutions(type, btnElement) {
            activeFilter = type;
            document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
            if (btnElement) btnElement.classList.add('active');

            const cards = document.querySelectorAll('.sol-card');
            cards.forEach(c => {
                if (type === 'ALL' || c.dataset.status === type) {
                    c.style.display = 'block';
                } else {
                    c.style.display = 'none';
                }
            });
        }

        function shareScorecard() {
            const score = document.getElementById('res-score').textContent;
            const maxScore = document.getElementById('res-max-score').textContent;
            const accuracy = document.getElementById('res-accuracy').textContent;
            const textSummary =
                "CBT Test Result:\n" +
                "Candidate: " + candidateName + " (Roll: " + candidateRoll + ")\n" +
                "Exam: " + (EXAM_DATA.meta.title || "Exam") + "\n" +
                "Subject: " + (EXAM_DATA.meta.subject || "Subject") + "\n" +
                "Score: " + score + " " + maxScore + " (Accuracy: " + accuracy + ")\n" +
                "Tab Switches: " + tabSwitchCount + "\n" +
                "Date: " + new Date().toLocaleDateString();

            if (navigator.share) {
                navigator.share({
                    title: 'Exam Scorecard - ' + candidateName,
                    text: textSummary
                }).catch(() => {
                    copyToClipboard(textSummary);
                });
            } else {
                copyToClipboard(textSummary);
            }
        }

        function copyToClipboard(text) {
            navigator.clipboard.writeText(text).then(() => {
                showToast("Scorecard copied to clipboard!");
            }).catch(() => {
                showToast("Scorecard summary generated.");
            });
        }

        function exportResultDataJson() {
            const resultPayload = {
                candidateName,
                candidateRoll,
                examTitle: EXAM_DATA.meta.title,
                subject: EXAM_DATA.meta.subject,
                seriesCode: EXAM_DATA.meta.seriesCode,
                timestamp: new Date().toISOString(),
                totalQuestions: EXAM_DATA.questions.length,
                answers,
                tabSwitchCount,
                score: document.getElementById('res-score').textContent,
                maxScore: document.getElementById('res-max-score').textContent,
                accuracy: document.getElementById('res-accuracy').textContent
            };

            const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(resultPayload, null, 2));
            const dlAnchor = document.createElement('a');
            dlAnchor.setAttribute("href", dataStr);
            dlAnchor.setAttribute("download", "Roll_" + candidateRoll + "_Scorecard.json");
            document.body.appendChild(dlAnchor);
            dlAnchor.click();
            dlAnchor.remove();
        }

        function retakeExam() {
            if (confirm("Do you want to clear your current answers and retake this test?")) {
                try { sessionStorage.removeItem(STORAGE_KEY); } catch (e) {}
                location.reload();
            }
        }
    </script>
</body>
</html>
        """.trimIndent()
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

                // Question type badge (Conceptual, Numerical, etc.) commented out per user request:
                /*
                val effectiveType = QuestionFormatter.displayType(q) ?: "Multiple Choice"
                */

                append("""
                <div class="q-card">
                    <div class="q-text">
                        <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px;">
                            <strong>${q.num}.</strong>
                            <!-- Question type badge commented out per user request:
                            <span style="font-size: 7.5pt; font-weight: 700; color: #4338ca; background: #eef2ff; border: 1px solid #c7d2fe; padding: 1px 5px; border-radius: 4px;">
                                ${QuestionFormatter.displayType(q) ?: "Multiple Choice"}
                            </span>
                            -->
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
            <title>OMR Response Sheet - Set ${escapeHtml(paper.metadata.setNumber.ifBlank { "01" })}</title>
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
            flex-wrap: wrap;
            justify-content: center;
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
        val rawSet = paper.metadata.setNumber.ifBlank { config.seriesCode.ifBlank { "01" } }.trim().uppercase()
        val setClean = ExamNumberGenerator.extractBookletCode(rawSet)
        val setStr = ExamNumberGenerator.normalizeSetNumber(setClean)
        val showSetDigits = (config.omrBubblingMode == OmrBubblingMode.PRE_PRINTED_DIGITS ||
                config.omrBubblingMode == OmrBubblingMode.PRE_DARKENED_BUBBLES) && setStr.length == 2
        val darkenSetBubbles = config.omrBubblingMode == OmrBubblingMode.PRE_DARKENED_BUBBLES && setStr.length == 2

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

                    <!-- Set Number Grid (2 Columns x 10 Rows with 2 Digit Boxes) -->
                    <div class="panel-box">
                        <div class="grid-header-title">SET NO.</div>
                        <div class="digit-entry-row" style="justify-content: center; gap: 4px;">
                            ${(0 until 2).joinToString("") { colIdx ->
                                val ch = if (showSetDigits && setStr.length == 2) setStr[colIdx].toString() else ""
                                "<div class=\"digit-box\" style=\"width: 6mm;\">$ch</div>"
                            }}
                        </div>
                        <div class="bubble-grid-cols">
                            ${(0..9).joinToString("") { d ->
                                "<div class=\"bubble-grid-row\" style=\"justify-content: center; gap: 4px;\">" +
                                (0 until 2).joinToString("") { colIdx ->
                                    val isDarkened = darkenSetBubbles && setStr.length == 2 && (setStr[colIdx] - '0') == d
                                    val cls = if (isDarkened) "micro-bubble micro-bubble-darkened" else "micro-bubble"
                                    "<span class=\"$cls\" style=\"width:3.3mm; height:3.3mm;\">$d</span>"
                                } + "</div>"
                            }}
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
