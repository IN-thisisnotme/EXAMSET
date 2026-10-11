package com.example.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.model.ExamConfig
import com.example.model.OmrGeometry
import com.example.model.PrintMode
import com.example.model.QuestionPaper

object PrintDocumentHelper {

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
        val jobName = "${paper.metadata.title.replace(" ", "_")}_${config.printMode.name}"
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
            <style>
                @page {
                    size: A4 portrait;
                    margin: 8mm 10mm;
                }
                * { box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
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
                    <span><strong>Paper Code / Series:</strong> ${escapeHtml(paper.metadata.setNumber.ifBlank { config.seriesCode })}</span>
                    <span><strong>Time:</strong> ${escapeHtml(config.timeAllowed)}</span>
                    <span><strong>Max Marks:</strong> ${escapeHtml(config.maxMarks)}</span>
                </div>
            </div>

            ${if (config.includeCandidateDetails) """
            <div class="candidate-box">
                <div class="cand-row">
                    <span><strong>Candidate Name:</strong> ___________________________________</span>
                    <span><strong>Candidate Signature:</strong> _____________________</span>
                    <span><strong>Invigilator's Signature:</strong> _____________________</span>
                </div>
            </div>
            """ else ""}

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
            <style>
                @page {
                    size: A4 portrait;
                    margin: 18mm 16mm 16mm 16mm;
                }
                * { box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
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
                <div class="banner-sub">${escapeHtml(paper.metadata.subject)}</div>
                ${if (paper.metadata.exam.isNotEmpty()) "<div class=\"banner-exam\">" + escapeHtml(paper.metadata.exam) + "</div>" else ""}
            </div>

            <div class="section-heading">Questions</div>
            $questionsCardsHtml

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
            <style>
                @page {
                    size: A4 portrait;
                    margin: ${OmrGeometry.MARGIN_Y_MM}mm ${OmrGeometry.MARGIN_X_MM}mm;
                }
                * { box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
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
        /* Drop-out Magenta/Pink NEET OMR Theme Rules */
        .omr-page {
            ${if (isStandalone) "" else "page-break-before: always; break-before: page;"}
            page-break-inside: avoid;
            break-inside: avoid;
            page-break-after: avoid;
            break-after: avoid;
            box-sizing: border-box;
            width: 100%;
            height: ${OmrGeometry.PRINTABLE_HEIGHT_MM}mm;
            max-height: ${OmrGeometry.PRINTABLE_HEIGHT_MM}mm;
            overflow: hidden;
            margin: 0;
            padding: 0;
            color: #000000;
        }
        .omr-wrapper {
            position: relative;
            border: 1.8px solid #be185d;
            padding: 2.0mm 2.5mm;
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
            height: 100%;
            justify-content: space-between;
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

        /* Top Header Area */
        .omr-top-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin: 0 5mm 1.5mm 5mm;
            gap: 4mm;
        }
        .omr-header-warning {
            flex: 1.2;
            border: 1.2px solid #be185d;
            border-radius: 4px;
            padding: 1.2mm 2.5mm;
            font-size: 5.6pt;
            font-weight: 700;
            color: #be185d;
            line-height: 1.15;
            background: #fdf2f8;
        }
        .omr-header-title-box {
            flex: 1;
            text-align: right;
        }
        .omr-title {
            font-weight: 900;
            font-size: 11pt;
            letter-spacing: 0.5px;
            text-transform: uppercase;
            color: #be185d;
            line-height: 1.1;
        }
        .omr-exam-subtitle {
            font-size: 7.2pt;
            font-weight: 700;
            color: #9d174d;
        }

        /* 2-Panel Main Container */
        .omr-body-container {
            display: flex;
            flex: 1;
            gap: 2.5mm;
            min-height: 0;
            margin: 0 1mm;
        }

        /* Left Identification Panel */
        .omr-left-panel {
            width: ${OmrGeometry.LEFT_PANEL_WIDTH_MM}mm;
            min-width: ${OmrGeometry.LEFT_PANEL_WIDTH_MM}mm;
            max-width: ${OmrGeometry.LEFT_PANEL_WIDTH_MM}mm;
            display: flex;
            flex-direction: column;
            gap: 1.5mm;
            border: 1.2px solid #be185d;
            padding: 1.2mm;
            background: #fff;
            box-sizing: border-box;
            position: relative;
        }
        .omr-left-banner-text {
            position: absolute;
            left: -20px;
            top: 50%;
            transform: translateY(-50%) rotate(-90deg);
            font-size: 5.5pt;
            font-weight: 800;
            color: #be185d;
            letter-spacing: 0.5px;
            white-space: nowrap;
        }
        .grid-header-title {
            background: #be185d;
            color: #ffffff;
            font-size: 6.2pt;
            font-weight: 800;
            text-align: center;
            padding: 1px 0;
            letter-spacing: 0.5px;
            text-transform: uppercase;
        }
        .digit-entry-row {
            display: flex;
            border-bottom: 1px solid #be185d;
            background: #fff;
        }
        .digit-box {
            flex: 1;
            height: 3.5mm;
            border-right: 1px solid #be185d;
            box-sizing: border-box;
        }
        .digit-box:last-child {
            border-right: none;
        }
        .bubble-matrix {
            display: flex;
            padding: 0.8mm 0;
            justify-content: space-around;
        }
        .bubble-col {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 0.5mm;
        }
        .micro-bubble {
            width: 2.8mm;
            height: 2.8mm;
            border: 0.3mm solid #be185d;
            border-radius: 50%;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 4.2pt;
            font-weight: 700;
            color: #be185d;
            background: #ffffff;
            box-sizing: border-box;
            line-height: 1;
        }

        /* Booklet Code & Barcode Box */
        .code-and-barcode-row {
            display: flex;
            gap: 1.5mm;
            align-items: stretch;
        }
        .booklet-code-box {
            flex: 1;
            border: 1px solid #be185d;
            padding: 1mm;
            text-align: center;
        }
        .booklet-code-large {
            font-size: 16pt;
            font-weight: 900;
            color: #be185d;
            line-height: 1.1;
        }
        .sheet-no-box {
            flex: 1.4;
            border: 1px dashed #be185d;
            padding: 1mm;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            font-size: 5pt;
            color: #be185d;
            font-weight: 700;
            text-align: center;
            background: #fdf2f8;
        }
        .barcode-simulation {
            display: flex;
            height: 10mm;
            gap: 1px;
            justify-content: center;
            align-items: center;
        }
        .bar {
            background: #be185d;
            height: 100%;
        }

        /* Candidate Declaration Box */
        .declaration-box {
            border: 1px solid #be185d;
            padding: 1.2mm;
            font-size: 4.8pt;
            color: #831843;
            line-height: 1.15;
            background: #fff;
        }
        .declaration-title {
            font-weight: 800;
            color: #be185d;
            font-size: 5.2pt;
            margin-bottom: 0.5mm;
            text-transform: uppercase;
        }
        .decl-sig-line {
            border-bottom: 0.8px solid #be185d;
            margin-top: 5mm;
            margin-bottom: 0.5mm;
        }
        .decl-sig-caption {
            font-size: 4.4pt;
            font-weight: 700;
            color: #be185d;
            text-align: center;
        }

        /* Right 4-Column Question Grid */
        .omr-right-grid {
            width: ${OmrGeometry.RIGHT_GRID_WIDTH_MM}mm;
            min-width: ${OmrGeometry.RIGHT_GRID_WIDTH_MM}mm;
            max-width: ${OmrGeometry.RIGHT_GRID_WIDTH_MM}mm;
            display: flex;
            gap: ${OmrGeometry.COLUMN_GUTTER_MM}mm;
            box-sizing: border-box;
        }
        .omr-column {
            width: ${OmrGeometry.COLUMN_WIDTH_MM}mm;
            min-width: ${OmrGeometry.COLUMN_WIDTH_MM}mm;
            max-width: ${OmrGeometry.COLUMN_WIDTH_MM}mm;
            display: flex;
            flex-direction: column;
            border: 1px solid #be185d;
            box-sizing: border-box;
            background: #ffffff;
        }
        .omr-col-header {
            background: #be185d;
            color: #ffffff;
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 1px 1.5mm;
            height: ${OmrGeometry.COL_HEADER_HEIGHT_MM}mm;
            box-sizing: border-box;
            font-size: 5.4pt;
            font-weight: 800;
        }
        .header-qnum-lbl {
            width: 6.0mm;
            text-align: center;
        }
        .header-resp-wrap {
            display: flex;
            gap: 1.0mm;
            align-items: center;
        }
        .header-opt-lbl {
            width: ${OmrGeometry.BUBBLE_DIAMETER_MM}mm;
            text-align: center;
            font-size: 5.5pt;
        }

        /* Question Rows */
        .section-separator {
            background: #fdf2f8;
            border-top: 1px solid #be185d;
            border-bottom: 1px solid #be185d;
            font-size: 4.6pt;
            font-weight: 800;
            color: #be185d;
            text-align: center;
            padding: 0.3mm 0;
            letter-spacing: 0.3px;
        }
        .omr-row {
            display: flex;
            align-items: center;
            justify-content: flex-start;
            height: ${OmrGeometry.ROW_PITCH_MM}mm;
            min-height: ${OmrGeometry.ROW_PITCH_MM}mm;
            max-height: ${OmrGeometry.ROW_PITCH_MM}mm;
            padding: 0 0.8mm;
            box-sizing: border-box;
        }
        .omr-row-shaded {
            background-color: #fdf2f8;
        }
        .omr-qnum {
            width: ${OmrGeometry.QNUM_WIDTH_MM}mm;
            font-size: 6.2pt;
            font-weight: 800;
            text-align: right;
            color: #be185d;
            font-family: "Courier New", Courier, monospace;
            padding-right: ${OmrGeometry.QNUM_TO_BUBBLE_GAP_MM}mm;
            box-sizing: border-box;
        }
        .bubble-group {
            display: flex;
            gap: 1.0mm; /* Fixed natural spacing */
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
            font-weight: 700;
            font-size: 5.6pt;
            color: #be185d;
            background: #ffffff;
            line-height: 1;
            box-sizing: border-box;
            text-transform: uppercase;
        }
        .bubble-empty-slot {
            opacity: 0.35;
        }

        /* Bottom Verification Area */
        .omr-bottom-area {
            border: 1.2px solid #be185d;
            margin: 1.5mm 1mm 0 1mm;
            background: #fff;
            padding: 1.2mm;
            box-sizing: border-box;
        }
        .omr-verification-grid {
            display: grid;
            grid-template-columns: 1.3fr 1.3fr 1.4fr 1.6fr;
            gap: 2mm;
            align-items: stretch;
        }
        .omr-verif-box {
            border: 1px solid #be185d;
            padding: 1mm 1.5mm;
            background: #fff;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            min-height: 14mm;
            box-sizing: border-box;
        }
        .omr-verif-label {
            font-size: 5.2pt;
            font-weight: 800;
            color: #be185d;
            text-transform: uppercase;
            letter-spacing: 0.2px;
            line-height: 1.1;
        }
        .omr-verif-line {
            border-bottom: 1px solid #be185d;
            height: 9mm;
            width: 100%;
        }
        .omr-thumb-box {
            border: 1px dashed #be185d;
            height: 9mm;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 4.8pt;
            color: #be185d;
            font-weight: 700;
            background: #fdf2f8;
        }
        .omr-sig-dual {
            display: flex;
            flex-direction: column;
            gap: 1mm;
        }
        .omr-sig-dual-row {
            display: flex;
            justify-content: space-between;
            font-size: 4.8pt;
            color: #be185d;
            font-weight: 700;
            border-bottom: 0.8px solid #be185d;
            padding-bottom: 3.5mm;
        }
        .end-of-omr-notice {
            text-align: center;
            font-size: 5.2pt;
            font-weight: 800;
            color: #be185d;
            margin-top: 1mm;
            letter-spacing: 0.5px;
            text-transform: uppercase;
        }
        """.trimIndent()
    }

    private fun generateOmrSectionHtml(paper: QuestionPaper, config: ExamConfig, isStandalone: Boolean = false): String {
        val questionCount = paper.questions.size.coerceAtLeast(1)
        val sb = StringBuilder()

        sb.append("""
        <div class="omr-page">
        <div class="omr-wrapper">
            <!-- 4 Corner Registration Fiducial Anchors in drop-out magenta (#be185d) -->
            <div class="omr-anchor anchor-tl"></div>
            <div class="omr-anchor anchor-tr"></div>
            <div class="omr-anchor anchor-bl"></div>
            <div class="omr-anchor anchor-br"></div>

            <!-- Top Header -->
            <div class="omr-top-header">
                <div class="omr-header-warning">
                    The candidate should ensure that Roll No. and Test Booklet No. have been filled and marked correctly and Test Booklet Code printed in this sheet and Test Booklet is the same.
                </div>
                <div class="omr-header-title-box">
                    <div class="omr-title">NEET OMR RESPONSE SHEET / ANSWER SHEET</div>
                    <div class="omr-exam-subtitle">${escapeHtml(config.institutionName.ifBlank { "NATIONAL ELIGIBILITY CUM ENTRANCE TEST" })}</div>
                </div>
            </div>

            <!-- 2-Panel Main Body -->
            <div class="omr-body-container">
                <!-- Left Identification Panel -->
                <div class="omr-left-panel">
                    <!-- Roll No Grid (10 Digits) -->
                    <div>
                        <div class="grid-header-title">ROLL NO.</div>
                        <div class="digit-entry-row">
                            ${(1..10).joinToString("") { "<div class=\"digit-box\"></div>" }}
                        </div>
                        <div class="bubble-matrix">
                            ${(1..10).joinToString("") {
                                """
                                <div class="bubble-col">
                                    ${(1..9).joinToString("") { d -> "<span class=\"micro-bubble\">$d</span>" }}
                                    <span class="micro-bubble">0</span>
                                </div>
                                """.trimIndent()
                            }}
                        </div>
                    </div>

                    <!-- Test Booklet No Grid (7 Digits) -->
                    <div>
                        <div class="grid-header-title">TEST BOOKLET NO.</div>
                        <div class="digit-entry-row">
                            ${(1..7).joinToString("") { "<div class=\"digit-box\"></div>" }}
                        </div>
                        <div class="bubble-matrix">
                            ${(1..7).joinToString("") {
                                """
                                <div class="bubble-col">
                                    ${(1..9).joinToString("") { d -> "<span class=\"micro-bubble\">$d</span>" }}
                                    <span class="micro-bubble">0</span>
                                </div>
                                """.trimIndent()
                            }}
                        </div>
                    </div>

                    <!-- Test Booklet Code & Answer Sheet No -->
                    <div class="code-and-barcode-row">
                        <div class="booklet-code-box">
                            <div style="font-size: 5.2pt; font-weight: 800; color: #be185d;">Test Booklet Code</div>
                            <div class="booklet-code-large">${escapeHtml(paper.metadata.setNumber.ifBlank { config.seriesCode.ifBlank { "A" } })}</div>
                        </div>
                        <div class="sheet-no-box">
                            <div>Answer Sheet No.</div>
                            <div class="barcode-simulation">
                                <div class="bar" style="width:2px;"></div>
                                <div class="bar" style="width:1px; margin-left:1px;"></div>
                                <div class="bar" style="width:3px; margin-left:2px;"></div>
                                <div class="bar" style="width:1px; margin-left:1px;"></div>
                                <div class="bar" style="width:2px; margin-left:1px;"></div>
                                <div class="bar" style="width:4px; margin-left:2px;"></div>
                                <div class="bar" style="width:1px; margin-left:1px;"></div>
                                <div class="bar" style="width:2px; margin-left:1px;"></div>
                                <div class="bar" style="width:3px; margin-left:1px;"></div>
                            </div>
                            <div style="font-size:4.5pt; font-family:monospace;">${String.format("%08d", (paper.id.hashCode().let { if (it < 0) -it else it } % 90000000) + 10000000)}</div>
                        </div>
                    </div>

                    <!-- Candidate Declaration -->
                    <div class="declaration-box">
                        <div class="declaration-title">Declaration by the Candidate</div>
                        "I declare that particulars and signature on this OMR Answer Sheet are mine and are same as in Attendance Sheet. I further declare that particulars filled-in and signature as well as circles darkened on this ORIGINAL Copy are same as in OFFICE Copy."
                        <div class="decl-sig-line"></div>
                        <div class="decl-sig-caption">Signature with time (in running handwriting)</div>
                        <div class="decl-sig-line" style="margin-top: 3.5mm;"></div>
                        <div class="decl-sig-caption">CANDIDATE'S NAME (in running handwriting)</div>
                    </div>
                </div>

                <!-- Right 4-Column Question Grid -->
                <div class="omr-right-grid">
        """.trimIndent())

        // 4 Fixed Columns matching NEET Answer Sheet
        for (col in 0 until OmrGeometry.NUM_GRID_COLUMNS) {
            val startQ = col * OmrGeometry.ROWS_PER_COLUMN + 1
            val endQ = (col + 1) * OmrGeometry.ROWS_PER_COLUMN

            sb.append("""
            <div class="omr-column">
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

            for (i in startQ..endQ) {
                val rowInCol = (i - startQ)
                // Add Section divider between Section A (1..35) and Section B (36..50)
                if (rowInCol == OmrGeometry.SECTION_A_ROWS) {
                    sb.append("<div class=\"section-separator\">SECTION-B: Q. $i to $endQ</div>\n")
                }

                val blockIndex = (i - 1) / 5
                val isShaded = (blockIndex % 2) == 1
                val shadedClass = if (isShaded) "omr-row-shaded" else ""
                val qPadded = String.format("%03d", i)

                val isActive = i <= questionCount
                val slotClass = if (isActive) "" else "bubble-empty-slot"

                sb.append("""
                <div class="omr-row $shadedClass $slotClass">
                    <span class="omr-qnum">$qPadded.</span>
                    <div class="bubble-group">
                        <span class="bubble">1</span>
                        <span class="bubble">2</span>
                        <span class="bubble">3</span>
                        <span class="bubble">4</span>
                    </div>
                </div>
                """.trimIndent())
            }

            sb.append("</div>\n")
        }

        sb.append("""
                </div>
            </div>

            <!-- Bottom Verification & Parent Information -->
            <div class="omr-bottom-area">
                <div class="omr-verification-grid">
                    <div class="omr-verif-box">
                        <div class="omr-verif-label">MOTHER'S NAME (In running handwriting)</div>
                        <div class="omr-verif-line"></div>
                    </div>
                    <div class="omr-verif-box">
                        <div class="omr-verif-label">FATHER'S NAME (In running handwriting)</div>
                        <div class="omr-verif-line"></div>
                    </div>
                    <div class="omr-verif-box">
                        <div class="omr-verif-label">CANDIDATE'S LEFT THUMB</div>
                        <div class="omr-thumb-box">Left Thumb Impression</div>
                    </div>
                    <div class="omr-verif-box">
                        <div class="omr-verif-label">INVIGILATOR'S SIGNATURE WITH TIME</div>
                        <div class="omr-sig-dual">
                            <div class="omr-sig-dual-row"><span>1.</span><span>Sign &amp; Stamp</span></div>
                            <div class="omr-sig-dual-row" style="border-bottom:none; padding-bottom:0;"><span>2.</span><span>Sign &amp; Stamp</span></div>
                        </div>
                    </div>
                </div>
                <div class="end-of-omr-notice">❖ OFFICIAL NEET-STYLE OMR ANSWER SHEET — SINGLE PAGE DROP-OUT MAGENTA EDITION ❖</div>
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
