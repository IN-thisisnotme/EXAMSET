import { ExamConfig, QuestionPaper, PrintMode, QuestionItem } from '../types/exam';
import { OmrGenerator } from './omrGenerator';
import { parseAssertionReason, shouldStackOptions, isAssertionReason } from './questionFormatter';
import { McqParser } from './mcqParser';

export class PrintDocumentHelper {
  /**
   * Generates complete, highly styled HTML designed specifically for A4 printing.
   */
  public static generatePrintHtml(paper: QuestionPaper, config: ExamConfig): string {
    switch (config.printMode) {
      case 'STUDENT_PAPER':
        return this.generateStudentExamHtml(paper, config);
      case 'OMR_ONLY':
        return this.generateStandaloneOmrHtml(paper, config);
      case 'MASTER_BOOKLET':
        return this.generateMasterBookletHtml(paper, config);
      case 'SOLUTIONS_ONLY':
        return this.generateSolutionsOnlyHtml(paper, config);
      default:
        return this.generateStudentExamHtml(paper, config);
    }
  }

  /**
   * Formats an authentic student examination question paper.
   * Continuous flow without dividing lines, 2-column option, candidate box, instructions, and optional single-page OMR.
   */
  private static generateStudentExamHtml(paper: QuestionPaper, config: ExamConfig): string {
    const questionsHtml = paper.questions.map((q) => this.renderStudentQuestionHtml(q)).join('\n');

    const omrHtml = config.includeOmrSheet 
      ? OmrGenerator.generateHtml(paper, config, false) 
      : '';

    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <title>${this.escapeHtml(config.examTitle)}</title>
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
          /* Candidate Details Box (No roll number) */
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
            ${config.twoColumns ? 'column-count: 2; column-gap: 20px; column-rule: 1px solid #eaeaea;' : ''}
          }
          .student-q-item {
            break-inside: avoid;
            page-break-inside: avoid;
            margin-bottom: 12px;
            padding-bottom: 2px;
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
            grid-template-columns: 1fr 1fr;
            gap: 3px 12px;
            font-size: 8.5pt;
          }
          .options-stack {
            display: flex;
            flex-direction: column;
            gap: 3px;
            font-size: 8.5pt;
          }
          .opt-cell {
            display: flex;
            align-items: baseline;
            line-height: 1.35;
          }
          .opt-key {
            font-weight: 700;
            color: #17324D;
            margin-right: 5px;
            min-width: 22px;
            flex-shrink: 0;
          }
          .opt-val {
            flex: 1;
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
          ${OmrGenerator.getOmrStyles(false)}
        </style>
      </head>
      <body>
        <div class="exam-header">
          <div class="inst-name">${this.escapeHtml(config.institutionName)}</div>
          <div class="exam-name">${this.escapeHtml(config.examTitle)}</div>
          <div class="exam-meta-row">
            <span><strong>Subject:</strong> ${this.escapeHtml(paper.metadata.subject)}</span>
            <span><strong>Paper Code / Series:</strong> ${this.escapeHtml(paper.metadata.setNumber || config.seriesCode)}</span>
            <span><strong>Time:</strong> ${this.escapeHtml(config.timeAllowed)}</span>
            <span><strong>Max Marks:</strong> ${this.escapeHtml(config.maxMarks)}</span>
          </div>
        </div>

        ${config.includeCandidateDetails ? `
        <div class="candidate-box">
          <div class="cand-row">
            <span><strong>Candidate Name:</strong> ___________________________________</span>
            <span><strong>Candidate Signature:</strong> _____________________</span>
            <span><strong>Invigilator's Signature:</strong> _____________________</span>
          </div>
        </div>
        ` : ''}

        ${config.includeInstructions ? `
        <div class="instructions-box">
          <h4>Instructions to Candidates:</h4>
          <ol>
            ${config.customInstructions.map(i => `<li>${this.escapeHtml(i)}</li>`).join('')}
          </ol>
        </div>
        ` : ''}

        <div class="questions-container">
          ${questionsHtml}
        </div>

        <div class="footer">
          *** END OF QUESTION PAPER — ${paper.questions.length} QUESTIONS ***
        </div>

        ${omrHtml}
      </body>
      </html>
    `;
  }

  /**
   * Standalone OMR Sheet print mode.
   */
  private static generateStandaloneOmrHtml(paper: QuestionPaper, config: ExamConfig): string {
    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <title>OMR Response Sheet - ${this.escapeHtml(paper.metadata.title)}</title>
        <style>
          @page {
            size: A4 portrait;
            margin: 0;
          }
          * { box-sizing: border-box; }
          body {
            font-family: Arial, Helvetica, "Nimbus Sans L", sans-serif;
            margin: 0;
            padding: 0;
            background: #fff;
            color: #000;
          }
          ${OmrGenerator.getOmrStyles(true)}
        </style>
      </head>
      <body>
        ${OmrGenerator.generateHtml(paper, config, true)}
      </body>
      </html>
    `;
  }

  /**
   * Formats Master Booklet PDF layout with answer key cards and solutions.
   */
  private static generateMasterBookletHtml(paper: QuestionPaper, config: ExamConfig): string {
    const questionsCardsHtml = paper.questions.map((q) => this.renderMasterQuestionHtml(q)).join('\n');

    const answerKeyGridHtml = this.generateAnswerKeyTableHtml(paper);
    const explanationsHtml = this.generateExplanationsHtml(paper);

    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <title>${this.escapeHtml(paper.metadata.title)} - Master Booklet</title>
        <style>
          @page {
            size: A4 portrait;
            margin: 12mm 14mm;
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
          }
          .banner-sub {
            font-size: 9.5pt;
            color: #EAF6F6;
          }
          .q-card {
            border: 1px solid #e0e0e0;
            border-left: 3.5px solid #2B7A78;
            border-radius: 6px;
            padding: 9px 13px;
            margin-bottom: 10px;
            background: #ffffff;
            break-inside: avoid;
            page-break-inside: avoid;
          }
          .q-text {
            font-size: 9.5pt;
            font-weight: 600;
            margin-bottom: 7px;
            color: #17324D;
          }
          .options-table {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 4px 14px;
            font-size: 8.5pt;
          }
          .options-table-stacked {
            display: flex;
            flex-direction: column;
            gap: 4px;
            font-size: 8.5pt;
          }
          .opt-item {
            padding: 5px 8px;
            border-radius: 4px;
            background: #f8fafc;
            border: 1px solid #f1f5f9;
            display: flex;
            align-items: baseline;
            line-height: 1.35;
          }
          .opt-correct {
            background: #ecfdf5;
            border-color: #10b981;
            font-weight: bold;
            color: #065f46;
          }
          .opt-badge {
            font-weight: 700;
            margin-right: 5px;
          }
          .section-divider {
            page-break-before: always;
            break-before: page;
            padding-top: 10px;
          }
          .section-title {
            font-size: 13pt;
            font-weight: 800;
            color: #17324D;
            border-bottom: 2px solid #2B7A78;
            padding-bottom: 4px;
            margin-bottom: 12px;
          }
          .key-table {
            width: 100%;
            border-collapse: collapse;
            font-size: 8.5pt;
            margin-bottom: 16px;
          }
          .key-table th, .key-table td {
            border: 1px solid #cbd5e1;
            padding: 4px 8px;
            text-align: center;
          }
          .key-table th {
            background: #17324D;
            color: #fff;
            font-weight: 700;
          }
          .exp-card {
            border-left: 3px solid #3b82f6;
            background: #f8fafc;
            padding: 8px 12px;
            border-radius: 4px;
            margin-bottom: 8px;
            break-inside: avoid;
            page-break-inside: avoid;
            font-size: 8.5pt;
          }
        </style>
      </head>
      <body>
        <div class="navy-banner">
          <div class="banner-title">${this.escapeHtml(paper.metadata.title)}</div>
          <div class="banner-sub">
            ${this.escapeHtml(paper.metadata.subject)} • ${this.escapeHtml(paper.metadata.setNumber || 'Set A')} • Master Evaluation Booklet
          </div>
        </div>

        <div>
          ${questionsCardsHtml}
        </div>

        <div class="section-divider">
          <div class="section-title">Official Answer Key</div>
          ${answerKeyGridHtml}

          <div class="section-title" style="margin-top: 24px;">Detailed Explanations</div>
          ${explanationsHtml}
        </div>
      </body>
      </html>
    `;
  }

  /**
   * Solutions Only print mode.
   */
  private static generateSolutionsOnlyHtml(paper: QuestionPaper, config: ExamConfig): string {
    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <title>Answer Key & Solutions - ${this.escapeHtml(paper.metadata.title)}</title>
        <style>
          @page { size: A4 portrait; margin: 12mm 14mm; }
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif; font-size: 9pt; }
          .title { font-size: 14pt; font-weight: bold; margin-bottom: 12px; color: #17324D; }
          .key-table { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
          .key-table th, .key-table td { border: 1px solid #cbd5e1; padding: 5px; text-align: center; }
          .key-table th { background: #17324D; color: #fff; }
          .exp-card { border-left: 3px solid #059669; background: #f0fdf4; padding: 8px 12px; margin-bottom: 8px; break-inside: avoid; }
        </style>
      </head>
      <body>
        <div class="title">Answer Key & Explanations: ${this.escapeHtml(paper.metadata.title)}</div>
        ${this.generateAnswerKeyTableHtml(paper)}
        <div style="font-weight: bold; font-size: 11pt; margin: 16px 0 8px 0;">Detailed Explanations</div>
        ${this.generateExplanationsHtml(paper)}
      </body>
      </html>
    `;
  }

  private static generateAnswerKeyTableHtml(paper: QuestionPaper): string {
    const questions = paper.questions;
    let html = '<table class="key-table"><thead><tr><th>Q.No</th><th>Key</th><th>Q.No</th><th>Key</th><th>Q.No</th><th>Key</th><th>Q.No</th><th>Key</th></tr></thead><tbody>';

    for (let i = 0; i < questions.length; i += 4) {
      html += '<tr>';
      for (let c = 0; c < 4; c++) {
        const q = questions[i + c];
        if (q) {
          html += `<td><strong>${q.num}</strong></td><td><strong>${q.correctAnswer || '-'}</strong></td>`;
        } else {
          html += '<td></td><td></td>';
        }
      }
      html += '</tr>';
    }

    html += '</tbody></table>';
    return html;
  }

  private static generateExplanationsHtml(paper: QuestionPaper): string {
    return paper.questions.map(q => `
      <div class="exp-card">
        <div><strong>Q.${q.num}:</strong> Correct Answer: <strong>(${q.correctAnswer || 'Not specified'})</strong></div>
        <div style="margin-top: 4px; color: #334155;">${this.escapeRichHtml(q.explanation || 'No detailed explanation provided for this question.')}</div>
      </div>
    `).join('\n');
  }

  private static escapeRichHtml(text: string): string {
    let html = this.escapeHtml(text);
    html = html.replace(/\*\*(.+?)\*\*/g, '<b>$1</b>');
    html = html.replace(/__(.+?)__/g, '<b>$1</b>');
    html = html.replace(/(?<!\*)\*([^*]+?)\*(?!\*)/g, '<i>$1</i>');
    html = html.replace(/\n/g, '<br/>');
    return html;
  }

  private static renderStudentQuestionHtml(q: QuestionItem): string {
    const isAr = isAssertionReason(q.question, q.options, q.questionType);
    const ar = parseAssertionReason(q.question);
    const effectiveType = q.questionType || (isAr ? 'Assertion–Reason' : McqParser.classifyQuestionType(q.question, q.options));
    const stacked = shouldStackOptions(effectiveType, q.options);
    const containerClass = stacked ? 'options-stack' : 'options-grid';

    let stemHtml = '';
    if (ar) {
      stemHtml = `
        <div class="ar-print-block">
          ${ar.leadIn ? `<div class="ar-print-leadin">${this.escapeRichHtml(ar.leadIn)}</div>` : ''}
          <div class="ar-print-statement"><span class="ar-print-label">Assertion (A):</span> ${this.escapeRichHtml(ar.assertion)}</div>
          <div class="ar-print-statement"><span class="ar-print-label">Reason (R):</span> ${this.escapeRichHtml(ar.reason)}</div>
          ${ar.leadOut ? `<div class="ar-print-leadout">${this.escapeRichHtml(ar.leadOut)}</div>` : ''}
        </div>
      `;
    } else {
      stemHtml = this.escapeRichHtml(q.question);
    }

    return `
      <div class="student-q-item">
        <div class="q-head"><strong>${q.num}.</strong> ${stemHtml}</div>
        <div class="${containerClass}">
          <div class="opt-cell"><span class="opt-key">(A)</span> <span class="opt-val">${this.escapeRichHtml(q.options.a)}</span></div>
          <div class="opt-cell"><span class="opt-key">(B)</span> <span class="opt-val">${this.escapeRichHtml(q.options.b)}</span></div>
          <div class="opt-cell"><span class="opt-key">(C)</span> <span class="opt-val">${this.escapeRichHtml(q.options.c)}</span></div>
          <div class="opt-cell"><span class="opt-key">(D)</span> <span class="opt-val">${this.escapeRichHtml(q.options.d)}</span></div>
        </div>
      </div>
    `;
  }

  private static renderMasterQuestionHtml(q: QuestionItem): string {
    const isAr = isAssertionReason(q.question, q.options, q.questionType);
    const ar = parseAssertionReason(q.question);
    const effectiveType = q.questionType || (isAr ? 'Assertion–Reason' : McqParser.classifyQuestionType(q.question, q.options));
    const stacked = shouldStackOptions(effectiveType, q.options);
    const containerClass = stacked ? 'options-table-stacked' : 'options-table';

    let stemHtml = '';
    if (ar) {
      stemHtml = `
        <div class="ar-print-block">
          ${ar.leadIn ? `<div class="ar-print-leadin">${this.escapeRichHtml(ar.leadIn)}</div>` : ''}
          <div class="ar-print-statement"><span class="ar-print-label">Assertion (A):</span> ${this.escapeRichHtml(ar.assertion)}</div>
          <div class="ar-print-statement"><span class="ar-print-label">Reason (R):</span> ${this.escapeRichHtml(ar.reason)}</div>
          ${ar.leadOut ? `<div class="ar-print-leadout">${this.escapeRichHtml(ar.leadOut)}</div>` : ''}
        </div>
      `;
    } else {
      stemHtml = this.escapeRichHtml(q.question);
    }

    return `
      <div class="q-card">
        <div class="q-text">
          <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px;">
            <strong>${q.num}.</strong>
            <span style="font-size: 7.5pt; font-weight: 700; color: #4338ca; background: #eef2ff; border: 1px solid #c7d2fe; padding: 1px 5px; border-radius: 4px;">
              ${effectiveType}
            </span>
          </div>
          ${stemHtml}
        </div>
        <div class="${containerClass}">
          <div class="opt-item ${q.correctAnswer === 'A' ? 'opt-correct' : ''}"><span class="opt-badge">(A)</span> <span class="opt-val">${this.escapeRichHtml(q.options.a)}</span></div>
          <div class="opt-item ${q.correctAnswer === 'B' ? 'opt-correct' : ''}"><span class="opt-badge">(B)</span> <span class="opt-val">${this.escapeRichHtml(q.options.b)}</span></div>
          <div class="opt-item ${q.correctAnswer === 'C' ? 'opt-correct' : ''}"><span class="opt-badge">(C)</span> <span class="opt-val">${this.escapeRichHtml(q.options.c)}</span></div>
          <div class="opt-item ${q.correctAnswer === 'D' ? 'opt-correct' : ''}"><span class="opt-badge">(D)</span> <span class="opt-val">${this.escapeRichHtml(q.options.d)}</span></div>
        </div>
      </div>
    `;
  }

  private static escapeHtml(str: string): string {
    return (str || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
}
