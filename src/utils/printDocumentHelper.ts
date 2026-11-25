import { ExamConfig, QuestionItem, QuestionPaper } from '../types/exam';
import { AnswerKeyMatcher } from './answerKeyMatcher';
import { LatexHelper } from './latexHelper';
import { OmrGenerator } from './omrGenerator';
import {
  extractMarkdownTables,
  parseAssertionReason,
  parseDualMatchData,
  parseStatementQuestion,
  shouldStackOptions,
} from './questionFormatter';
import { RichTextHelper } from './richTextHelper';
import { McqParser } from './mcqParser';

function escapeJsonForInlineScript(json: string): string {
  return json
    .replace(/<\//g, '<\\/')
    .replace(/<!--/g, '\\u003c!--')
    .replace(/<script/gi, (m) => `\\u003c${m.slice(1)}`)
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
}

export class PrintDocumentHelper {
  public static generatePrintHtml(paper: QuestionPaper, config: ExamConfig): string {
    switch (config.printMode) {
      case 'paper_and_omr':
        return this.generateStudentExamHtml(paper, config, true);
      case 'paper_only':
        return this.generateStudentExamHtml(paper, config, false);
      case 'omr_only':
        return this.generateStandaloneOmrHtml(paper, config);
      case 'master_booklet':
        return this.generateMasterBookletHtml(paper, config);
      case 'solutions_only':
        return this.generateSolutionsOnlyHtml(paper, config);
      default:
        return this.generateStudentExamHtml(paper, config, true);
    }
  }

  private static escapeRichHtml(text: string): string {
    if (!text) return '';
    const latexNormalized = LatexHelper.toRichTextString(text);
    const tokens = RichTextHelper.tokenize(latexNormalized);
    return tokens
      .map((t) => {
        let html = RichTextHelper.escapeHtml(t.text).replace(/\n/g, '<br/>');
        if (t.code) {
          html = `<code style="font-family:monospace;background:#f1f5f9;padding:0 3px;border-radius:3px;">${html}</code>`;
        }
        if (t.bold) html = `<strong>${html}</strong>`;
        if (t.italic) html = `<em>${html}</em>`;
        if (t.sub) html = `<sub>${html}</sub>`;
        if (t.sup) html = `<sup>${html}</sup>`;
        return html;
      })
      .join('');
  }

  private static renderStructuredStemHtml(q: QuestionItem): string {
    const ar = parseAssertionReason(q.question);
    if (ar) {
      return `
        <div class="ar-print-block">
          ${ar.leadIn ? `<div class="ar-print-leadin">${this.escapeRichHtml(ar.leadIn)}</div>` : ''}
          <div class="ar-print-statement"><span class="ar-print-label">Assertion (A):</span> ${this.escapeRichHtml(ar.assertion)}</div>
          <div class="ar-print-statement"><span class="ar-print-label">Reason (R):</span> ${this.escapeRichHtml(ar.reason)}</div>
          ${ar.leadOut ? `<div class="ar-print-leadout">${this.escapeRichHtml(ar.leadOut)}</div>` : ''}
        </div>
      `;
    }

    const dual = parseDualMatchData(q.question);
    if (dual) {
      const rowsHtml = dual.rows
        .map(
          (r) =>
            `<tr><td>${r.leftKey ? `<strong>(${this.escapeHtml(r.leftKey)})</strong> ` : ''}${this.escapeRichHtml(
              r.leftText
            )}</td><td>${r.rightKey ? `<strong>(${this.escapeHtml(r.rightKey)})</strong> ` : ''}${this.escapeRichHtml(
              r.rightText
            )}</td></tr>`
        )
        .join('');
      return `
        <div class="ar-print-block">
          ${dual.leadIn ? `<div class="ar-print-leadin">${this.escapeRichHtml(dual.leadIn)}</div>` : ''}
          <table class="match-table">
            <thead><tr><th>${this.escapeHtml(dual.leftHeader)}</th><th>${this.escapeHtml(dual.rightHeader)}</th></tr></thead>
            <tbody>${rowsHtml}</tbody>
          </table>
          ${dual.leadOut ? `<div class="ar-print-leadout">${this.escapeRichHtml(dual.leadOut)}</div>` : ''}
        </div>
      `;
    }

    const stmt = parseStatementQuestion(q.question);
    if (stmt) {
      const listHtml = stmt.statements
        .map(
          (s) =>
            `<div class="ar-print-statement"><strong>${this.escapeHtml(s.label)}.</strong> ${this.escapeRichHtml(
              s.text
            )}</div>`
        )
        .join('');
      return `
        <div class="ar-print-block">
          ${stmt.leadIn ? `<div class="ar-print-leadin">${this.escapeRichHtml(stmt.leadIn)}</div>` : ''}
          ${listHtml}
          ${stmt.leadOut ? `<div class="ar-print-leadout">${this.escapeRichHtml(stmt.leadOut)}</div>` : ''}
        </div>
      `;
    }

    const tblExtract = extractMarkdownTables(q.question);
    if (tblExtract.tables.length > 0) {
      const tablesHtml = tblExtract.tables
        .map(
          (tbl) => `
          <table class="match-table">
            <thead><tr>${tbl.headers.map((h) => `<th>${this.escapeRichHtml(h)}</th>`).join('')}</tr></thead>
            <tbody>${tbl.rows
              .map((row) => `<tr>${row.map((cell) => `<td>${this.escapeRichHtml(cell)}</td>`).join('')}</tr>`)
              .join('')}</tbody>
          </table>
        `
        )
        .join('');
      return `
        ${tblExtract.textWithoutTables ? `<div>${this.escapeRichHtml(tblExtract.textWithoutTables)}</div>` : ''}
        ${tablesHtml}
      `;
    }

    return this.escapeRichHtml(q.question);
  }

  private static getWatermarkCss(config: ExamConfig): string {
    if (!config.showWatermark) return '';
    return `
      body::before {
        content: "${this.escapeHtml(config.institutionName || 'EXAMSET')}";
        position: fixed;
        top: 45%;
        left: 50%;
        transform: translate(-50%, -50%) rotate(-32deg);
        font-size: 46pt;
        font-weight: 900;
        color: rgba(23, 50, 77, 0.055);
        letter-spacing: 6px;
        pointer-events: none;
        z-index: 0;
        white-space: nowrap;
      }
    `;
  }

  private static generateStudentExamHtml(
    paper: QuestionPaper,
    config: ExamConfig,
    appendOmr: boolean
  ): string {
    const questionsHtml = paper.questions.map((q) => this.renderStudentQuestionHtml(q)).join('\n');
    const omrHtml = appendOmr ? OmrGenerator.generateHtml(paper, config, false) : '';
    const rollText = config.rollNumber || paper.metadata.rollNumber || '0000000001';
    const bookletText = config.bookletNumber || paper.metadata.bookletNumber || '0000001';
    const setText = config.seriesCode || paper.metadata.setNumber || '01';

    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>${this.escapeHtml(config.examTitle)}</title>
  <style>
    @page { size: A4 portrait; margin: 8mm 10mm; }
    * { box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      color: #000000;
      background: #ffffff;
      margin: 0;
      padding: 0;
      font-size: 9.8pt;
      line-height: 1.35;
      position: relative;
    }
    ${this.getWatermarkCss(config)}
    .exam-header {
      border: 1.6px solid #17324D;
      padding: 8px 12px;
      margin-bottom: 10px;
      text-align: center;
      break-inside: avoid;
      background: #ffffff;
    }
    .inst-name { font-size: 12.5pt; font-weight: 800; letter-spacing: 0.7px; text-transform: uppercase; margin-bottom: 2px; }
    .exam-name { font-size: 11pt; font-weight: 700; color: #17324D; margin-bottom: 4px; }
    .exam-meta-row {
      display: flex; flex-wrap: wrap; justify-content: space-between; gap: 6px;
      font-size: 8.2pt; font-weight: 600; border-top: 1px solid #cbd5e1; padding-top: 4px; margin-top: 4px;
    }
    .candidate-box {
      border: 1px solid #475569;
      padding: 6px 10px;
      margin-bottom: 10px;
      font-size: 8.2pt;
      break-inside: avoid;
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 6px 10px;
    }
    .instructions-box {
      border: 1px dashed #64748b;
      background: #f8fafc;
      padding: 6px 10px;
      margin-bottom: 12px;
      font-size: 8pt;
      line-height: 1.28;
      break-inside: avoid;
    }
    .instructions-box h4 { margin: 0 0 3px 0; font-size: 8.5pt; text-transform: uppercase; }
    .instructions-box ol { margin: 0; padding-left: 16px; }
    .questions-container {
      ${config.twoColumns ? 'column-count: 2; column-gap: 18px; column-rule: 1px solid #e2e8f0;' : ''}
    }
    .student-q-item { break-inside: avoid; page-break-inside: avoid; margin-bottom: 11px; }
    .q-head { font-weight: 600; font-size: 9.4pt; margin-bottom: 4px; color: #0f172a; }
    .msq-pill {
      display: inline-block; font-size: 6.8pt; font-weight: 800; color: #b45309;
      background: #fef3c7; border: 1px solid #fcd34d; padding: 0 4px; border-radius: 3px; margin-left: 4px;
    }
    .options-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 3px 10px; font-size: 8.5pt; }
    .options-stack { display: flex; flex-direction: column; gap: 3px; font-size: 8.5pt; }
    .opt-cell { display: flex; align-items: baseline; line-height: 1.32; }
    .opt-key { font-weight: 700; color: #17324D; margin-right: 4px; min-width: 21px; flex-shrink: 0; }
    .opt-val { flex: 1; }
    .ar-print-block { margin: 2px 0 4px 0; font-weight: normal; }
    .ar-print-statement { margin: 2px 0; }
    .ar-print-label { font-weight: 700; }
    .ar-print-leadin, .ar-print-leadout { margin: 2px 0; font-style: italic; }
    .match-table { width: 100%; border-collapse: collapse; margin: 4px 0; font-size: 8pt; }
    .match-table th, .match-table td { border: 1px solid #cbd5e1; padding: 3px 6px; text-align: left; vertical-align: top; }
    .match-table th { background: #f1f5f9; font-weight: 700; }
    .footer {
      font-size: 7.5pt; color: #64748b; text-align: center;
      margin-top: 14px; border-top: 1px solid #cbd5e1; padding-top: 4px; break-inside: avoid;
    }
    ${OmrGenerator.getOmrStyles(false)}
  </style>
</head>
<body>
  <div class="exam-header">
    <div class="inst-name">${this.escapeHtml(config.institutionName)}</div>
    <div class="exam-name">${this.escapeHtml(config.examTitle)}</div>
    <div class="exam-meta-row">
      <span><strong>Subject:</strong> ${this.escapeHtml(paper.metadata.subject || config.subject)}</span>
      <span><strong>Set:</strong> ${this.escapeHtml(setText)}</span>
      <span><strong>Time:</strong> ${config.durationMinutes || 120} Min</span>
      <span><strong>Max Marks:</strong> ${this.escapeHtml(config.maxMarks)} (+${config.positiveMarks}/-${config.negativeMarks})</span>
    </div>
  </div>

  ${
    config.includeCandidateDetails
      ? `<div class="candidate-box">
          <span><strong>Candidate Name:</strong> _______________________</span>
          <span><strong>Roll No.:</strong> ${this.escapeHtml(rollText)}</span>
          <span><strong>Booklet No.:</strong> ${this.escapeHtml(bookletText)}</span>
          <span><strong>Candidate Sign:</strong> ______________________</span>
          <span><strong>Invigilator Sign:</strong> ____________________</span>
          <span><strong>Centre Code:</strong> _________________________</span>
        </div>`
      : ''
  }

  ${
    config.includeInstructions
      ? `<div class="instructions-box">
          <h4>Instructions to Candidates:</h4>
          <ol>${config.customInstructions.map((i) => `<li>${this.escapeHtml(i)}</li>`).join('')}</ol>
        </div>`
      : ''
  }

  <div class="questions-container">${questionsHtml}</div>
  <div class="footer">*** END OF QUESTION PAPER — ${paper.questions.length} QUESTIONS ***</div>
  ${omrHtml}
</body>
</html>`;
  }

  private static generateStandaloneOmrHtml(paper: QuestionPaper, config: ExamConfig): string {
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>OMR Response Sheet - ${this.escapeHtml(paper.metadata.title)}</title>
  <style>
    @page { size: A4 portrait; margin: 0; }
    * { box-sizing: border-box; }
    body { font-family: Arial, Helvetica, sans-serif; margin: 0; padding: 0; background: #fff; }
    ${OmrGenerator.getOmrStyles(true)}
  </style>
</head>
<body>
  ${OmrGenerator.generateHtml(paper, config, true)}
</body>
</html>`;
  }

  private static generateMasterBookletHtml(paper: QuestionPaper, config: ExamConfig): string {
    const questionsCardsHtml = paper.questions.map((q) => this.renderMasterQuestionHtml(q)).join('\n');
    const answerKeyGridHtml = this.generateAnswerKeyTableHtml(paper);
    const explanationsHtml = this.generateExplanationsHtml(paper);

    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>${this.escapeHtml(paper.metadata.title)} - Master Booklet</title>
  <style>
    @page { size: A4 portrait; margin: 12mm 14mm; }
    * { box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      color: #0f172a; background: #fff; margin: 0; padding: 0; font-size: 9pt; line-height: 1.35;
    }
    ${this.getWatermarkCss(config)}
    .navy-banner {
      background: #17324D; color: #ffffff; border-radius: 8px;
      padding: 12px 16px; text-align: center; margin-bottom: 14px;
    }
    .banner-title { font-size: 13pt; font-weight: 800; margin-bottom: 3px; }
    .banner-sub { font-size: 9pt; color: #EAF6F6; }
    .q-card {
      border: 1px solid #cbd5e1; border-left: 3.5px solid #177E89; border-radius: 6px;
      padding: 9px 12px; margin-bottom: 10px; background: #fff; break-inside: avoid;
    }
    .q-text { font-size: 9.4pt; font-weight: 600; margin-bottom: 6px; color: #17324D; }
    .options-table { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 12px; font-size: 8.5pt; }
    .options-table-stacked { display: flex; flex-direction: column; gap: 4px; font-size: 8.5pt; }
    .opt-item {
      padding: 4px 8px; border-radius: 4px; background: #f8fafc;
      border: 1px solid #e2e8f0; display: flex; align-items: baseline;
    }
    .opt-correct { background: #ecfdf5; border-color: #10b981; font-weight: bold; color: #065f46; }
    .opt-badge { font-weight: 700; margin-right: 5px; }
    .match-table { width: 100%; border-collapse: collapse; margin: 4px 0; font-size: 8pt; }
    .match-table th, .match-table td { border: 1px solid #cbd5e1; padding: 3px 6px; text-align: left; }
    .match-table th { background: #f1f5f9; font-weight: 700; }
    .ar-print-block { font-weight: normal; margin: 2px 0; }
    .ar-print-statement { margin: 2px 0; }
    .ar-print-label { font-weight: 700; }
    .formula-box {
      margin-top: 5px; padding: 4px 8px; background: #EAF6F6; border: 1px solid #cbd5e1;
      border-radius: 4px; font-family: monospace; font-size: 8pt;
    }
    .section-divider { page-break-before: always; break-before: page; padding-top: 8px; }
    .section-title {
      font-size: 12.5pt; font-weight: 800; color: #17324D;
      border-bottom: 2px solid #177E89; padding-bottom: 4px; margin-bottom: 10px;
    }
    .key-table { width: 100%; border-collapse: collapse; font-size: 8.4pt; margin-bottom: 14px; }
    .key-table th, .key-table td { border: 1px solid #cbd5e1; padding: 4px 6px; text-align: center; }
    .key-table th { background: #17324D; color: #fff; font-weight: 700; }
    .exp-card {
      border-left: 3px solid #177E89; background: #f8fafc;
      padding: 7px 11px; border-radius: 4px; margin-bottom: 8px; break-inside: avoid; font-size: 8.5pt;
    }
  </style>
</head>
<body>
  <div class="navy-banner">
    <div class="banner-title">${this.escapeHtml(paper.metadata.title)}</div>
    <div class="banner-sub">
      ${this.escapeHtml(paper.metadata.subject)} • Set ${this.escapeHtml(
      config.seriesCode || paper.metadata.setNumber || '01'
    )} • Master Evaluation Booklet (+${config.positiveMarks}/-${config.negativeMarks})
    </div>
  </div>
  <div>${questionsCardsHtml}</div>
  <div class="section-divider">
    <div class="section-title">Official Answer Key</div>
    ${answerKeyGridHtml}
    <div class="section-title" style="margin-top: 20px;">Detailed Explanations</div>
    ${explanationsHtml}
  </div>
</body>
</html>`;
  }

  private static generateSolutionsOnlyHtml(paper: QuestionPaper, config: ExamConfig): string {
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Answer Key & Solutions - ${this.escapeHtml(paper.metadata.title)}</title>
  <style>
    @page { size: A4 portrait; margin: 12mm 14mm; }
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif; font-size: 9pt; color: #0f172a; }
    ${this.getWatermarkCss(config)}
    .title { font-size: 13.5pt; font-weight: 800; margin-bottom: 10px; color: #17324D; border-bottom: 2px solid #177E89; padding-bottom: 4px; }
    .key-table { width: 100%; border-collapse: collapse; margin-bottom: 14px; font-size: 8.4pt; }
    .key-table th, .key-table td { border: 1px solid #cbd5e1; padding: 4px; text-align: center; }
    .key-table th { background: #17324D; color: #fff; }
    .exp-card { border-left: 3px solid #247A55; background: #EEF8F2; padding: 8px 11px; margin-bottom: 8px; break-inside: avoid; border-radius: 4px; }
  </style>
</head>
<body>
  <div class="title">Official Answer Key & Explanations: ${this.escapeHtml(paper.metadata.title)}</div>
  ${this.generateAnswerKeyTableHtml(paper)}
  <div style="font-weight: 800; font-size: 11pt; margin: 14px 0 8px 0; color: #17324D;">Detailed Explanations</div>
  ${this.generateExplanationsHtml(paper)}
</body>
</html>`;
  }

  private static generateAnswerKeyTableHtml(paper: QuestionPaper): string {
    const questions = paper.questions;
    let html =
      '<table class="key-table"><thead><tr><th>Q.No</th><th>Key</th><th>Q.No</th><th>Key</th><th>Q.No</th><th>Key</th><th>Q.No</th><th>Key</th></tr></thead><tbody>';

    for (let i = 0; i < questions.length; i += 4) {
      html += '<tr>';
      for (let c = 0; c < 4; c++) {
        const q = questions[i + c];
        if (q) {
          const disp = q.isNeutralized ? 'BONUS' : AnswerKeyMatcher.formatKeyForDisplay(q.correctAnswer);
          html += `<td><strong>${q.num}</strong></td><td><strong>${this.escapeHtml(disp)}</strong></td>`;
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
    return paper.questions
      .map(
        (q) => `
      <div class="exp-card">
        <div><strong>Q.${q.num}:</strong> Key: <strong>(${this.escapeHtml(
          q.isNeutralized ? 'NEUTRALIZED / BONUS' : AnswerKeyMatcher.formatKeyForDisplay(q.correctAnswer)
        )})</strong></div>
        <div style="margin-top: 3px; color: #334155;">${this.escapeRichHtml(
          q.explanation || 'No detailed explanation provided for this question.'
        )}</div>
      </div>
    `
      )
      .join('\n');
  }

  private static renderStudentQuestionHtml(q: QuestionItem): string {
    const effectiveType =
      q.questionType || McqParser.classifyQuestionType(q.question, q.options);
    const stacked = shouldStackOptions(effectiveType, q.options);
    const containerClass = stacked ? 'options-stack' : 'options-grid';
    const isMsq = AnswerKeyMatcher.isMultiSelect(q);

    const optEntries: { label: string; val: string }[] = [
      { label: 'A', val: q.options.a },
      { label: 'B', val: q.options.b },
      { label: 'C', val: q.options.c },
      { label: 'D', val: q.options.d },
    ];
    if (q.options.e && q.options.e.trim()) {
      optEntries.push({ label: 'E', val: q.options.e });
    }

    return `
      <div class="student-q-item">
        <div class="q-head"><strong>${q.num}.</strong> ${isMsq ? '<span class="msq-pill">MSQ</span>' : ''} ${this.renderStructuredStemHtml(q)}</div>
        <div class="${containerClass}">
          ${optEntries
            .map(
              (o) =>
                `<div class="opt-cell"><span class="opt-key">(${o.label})</span><span class="opt-val">${this.escapeRichHtml(
                  o.val
                )}</span></div>`
            )
            .join('')}
        </div>
      </div>
    `;
  }

  private static renderMasterQuestionHtml(q: QuestionItem): string {
    const effectiveType =
      q.questionType || McqParser.classifyQuestionType(q.question, q.options);
    const stacked = shouldStackOptions(effectiveType, q.options);
    const containerClass = stacked ? 'options-table-stacked' : 'options-table';

    const optEntries: { label: string; val: string }[] = [
      { label: 'A', val: q.options.a },
      { label: 'B', val: q.options.b },
      { label: 'C', val: q.options.c },
      { label: 'D', val: q.options.d },
    ];
    if (q.options.e && q.options.e.trim()) {
      optEntries.push({ label: 'E', val: q.options.e });
    }

    return `
      <div class="q-card">
        <div class="q-text">
          <div style="display:flex;align-items:center;gap:6px;margin-bottom:4px;flex-wrap:wrap;">
            <strong>${q.num}.</strong>
            <span style="font-size:7.2pt;font-weight:700;color:#177E89;background:#EAF6F6;border:1px solid #D5E1E7;padding:1px 5px;border-radius:4px;">${this.escapeHtml(
              effectiveType
            )}</span>
            ${
              q.isVerified
                ? '<span style="font-size:7pt;font-weight:700;color:#247A55;background:#EEF8F2;padding:1px 5px;border-radius:4px;">VERIFIED</span>'
                : ''
            }
            ${
              q.isNeutralized
                ? '<span style="font-size:7pt;font-weight:700;color:#B45309;background:#FFF8E8;padding:1px 5px;border-radius:4px;">NEUTRALIZED</span>'
                : ''
            }
          </div>
          ${this.renderStructuredStemHtml(q)}
          ${
            q.formula
              ? `<div class="formula-box"><strong>Formula:</strong> ${this.escapeRichHtml(q.formula)}</div>`
              : ''
          }
        </div>
        <div class="${containerClass}">
          ${optEntries
            .map((o) => {
              const isKey = AnswerKeyMatcher.containsOption(q.correctAnswer, o.label);
              return `<div class="opt-item ${isKey ? 'opt-correct' : ''}"><span class="opt-badge">(${
                o.label
              })</span><span class="opt-val">${this.escapeRichHtml(o.val)}</span></div>`;
            })
            .join('')}
        </div>
      </div>
    `;
  }

  /**
   * Generates a standalone offline HTML interactive Computer-Based Test (CBT) bundle
   * mirroring Android PrintDocumentHelper.generateInteractiveExamHtml.
   */
  public static generateInteractiveExamHtml(paper: QuestionPaper, config: ExamConfig): string {
    const durationMinutes =
      config.durationMinutes > 0
        ? config.durationMinutes
        : Math.max(30, paper.questions.length * 2);
    const positiveMarks = config.positiveMarks > 0 ? config.positiveMarks : 2.0;
    const negativeMarks = config.negativeMarks >= 0 ? config.negativeMarks : 0.66;
    const unattendedMarks = config.unattendedMarks >= 0 ? config.unattendedMarks : 0.0;

    const payload = {
      meta: {
        institution: config.institutionName || 'EXAMINATION BOARD',
        title: config.examTitle || paper.metadata.title || 'OBJECTIVE EVALUATION TEST',
        subject: paper.metadata.subject || config.subject || 'General Assessment',
        seriesCode: config.seriesCode || paper.metadata.setNumber || '01',
        rollNumber: config.rollNumber || paper.metadata.rollNumber || '0000000001',
        bookletNumber: config.bookletNumber || paper.metadata.bookletNumber || '0000001',
        durationMinutes,
        totalQuestions: paper.questions.length,
        positiveMarks,
        negativeMarks,
        unattendedMarks,
        msqScoringPolicy: config.msqScoringPolicy || 'ALL_OR_NOTHING',
      },
      questions: paper.questions.map((q, idx) => {
        const opts: { key: string; rendered: string }[] = [
          { key: 'A', rendered: this.escapeRichHtml(q.options.a) },
          { key: 'B', rendered: this.escapeRichHtml(q.options.b) },
          { key: 'C', rendered: this.escapeRichHtml(q.options.c) },
          { key: 'D', rendered: this.escapeRichHtml(q.options.d) },
        ];
        if (q.options.e && q.options.e.trim()) {
          opts.push({ key: 'E', rendered: this.escapeRichHtml(q.options.e) });
        }
        return {
          index: idx,
          num: q.num,
          stemHtml: this.renderStructuredStemHtml(q),
          isMsq: AnswerKeyMatcher.isMultiSelect(q),
          isNeutralized: Boolean(q.isNeutralized),
          options: opts,
          correctAnswer: AnswerKeyMatcher.normalizeKey(q.correctAnswer),
          explanationHtml: q.explanation ? this.escapeRichHtml(q.explanation) : '',
        };
      }),
    };

    const safeJson = escapeJsonForInlineScript(JSON.stringify(payload));
    const safeTitle = this.escapeHtml(payload.meta.title);

    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${safeTitle} — Offline CBT</title>
  <style>
    :root { --bg:#F8FAFC; --card:#FFFFFF; --border:#D5E1E7; --ink:#0F172A; --muted:#475569; --navy:#17324D; --teal:#177E89; --tealBg:#EAF6F6; --ok:#247A55; --bad:#DC2626; --warn:#D97706; --qSize:15px; }
    * { box-sizing:border-box; margin:0; padding:0; }
    body { font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Arial,sans-serif; background:var(--bg); color:var(--ink); min-height:100vh; }
    .hidden { display:none !important; }
    .lobby { min-height:100vh; display:flex; align-items:center; justify-content:center; padding:16px; background:linear-gradient(135deg,#0f172a,#17324D); }
    .lobby-card { background:#fff; border-radius:14px; max-width:540px; width:100%; padding:24px; box-shadow:0 16px 36px rgba(0,0,0,0.3); }
    .lobby-card h1 { font-size:18px; color:var(--navy); margin-top:4px; }
    .meta-grid { display:grid; grid-template-columns:repeat(4,1fr); gap:8px; margin:14px 0; text-align:center; background:#F1F5F9; padding:10px; border-radius:8px; font-size:11px; }
    .meta-grid strong { display:block; font-size:14px; color:var(--navy); }
    label.field { display:block; font-size:11px; font-weight:700; color:var(--muted); margin-bottom:4px; text-transform:uppercase; }
    input.inp { width:100%; padding:9px 11px; border:1.5px solid var(--border); border-radius:8px; margin-bottom:10px; font-weight:600; }
    .btn { border:none; border-radius:8px; padding:10px 16px; font-weight:700; cursor:pointer; font-size:13px; }
    .btn-primary { background:var(--teal); color:#fff; }
    .btn-navy { background:var(--navy); color:#fff; }
    .btn-outline { background:#fff; border:1px solid var(--border); color:var(--ink); }
    .topbar { background:var(--navy); color:#fff; padding:10px 16px; display:flex; align-items:center; justify-content:space-between; gap:10px; flex-wrap:wrap; position:sticky; top:0; z-index:20; }
    .workspace { max-width:1120px; margin:18px auto; padding:0 16px; display:grid; grid-template-columns:1fr 300px; gap:16px; }
    @media(max-width:860px){ .workspace { grid-template-columns:1fr; } }
    .card { background:var(--card); border:1px solid var(--border); border-radius:12px; padding:18px; }
    .q-stem { font-size:var(--qSize); line-height:1.5; margin:12px 0 16px; font-weight:600; }
    .opt-btn { width:100%; text-align:left; padding:11px 14px; border-radius:10px; border:1.5px solid var(--border); background:#F8FAFC; margin-bottom:8px; cursor:pointer; display:flex; gap:10px; align-items:flex-start; font-size:calc(var(--qSize) - 1px); }
    .opt-btn.selected { background:var(--tealBg); border-color:var(--teal); font-weight:700; }
    .opt-chip { width:26px; height:26px; border-radius:50%; background:#fff; border:1px solid var(--border); display:inline-flex; align-items:center; justify-content:center; font-size:12px; font-weight:800; flex-shrink:0; }
    .opt-btn.selected .opt-chip { background:var(--teal); color:#fff; border-color:var(--teal); }
    .palette-grid { display:grid; grid-template-columns:repeat(5,1fr); gap:6px; margin-top:10px; }
    .p-btn { height:34px; border-radius:7px; border:1px solid var(--border); background:#F1F5F9; font-weight:700; font-size:12px; cursor:pointer; }
    .p-btn.cur { outline:2px solid var(--teal); }
    .p-btn.ans { background:var(--ok); color:#fff; border-color:var(--ok); }
    .p-btn.mrk { background:#7C3AED; color:#fff; border-radius:50%; border-color:#7C3AED; }
    .match-table { width:100%; border-collapse:collapse; margin:6px 0; font-size:12px; }
    .match-table th,.match-table td { border:1px solid var(--border); padding:4px 7px; text-align:left; }
  </style>
</head>
<body>
  <div id="lobby" class="lobby">
    <div class="lobby-card">
      <div style="font-size:11px;font-weight:800;color:var(--teal);text-transform:uppercase;">${this.escapeHtml(payload.meta.institution)}</div>
      <h1>${safeTitle}</h1>
      <p style="font-size:12px;color:var(--muted);margin-top:2px;">${this.escapeHtml(payload.meta.subject)} • Set ${this.escapeHtml(payload.meta.seriesCode)}</p>
      <div class="meta-grid">
        <div><strong>${payload.questions.length}</strong>Questions</div>
        <div><strong>${durationMinutes}m</strong>Duration</div>
        <div><strong>+${positiveMarks}</strong>Correct</div>
        <div><strong>-${negativeMarks}</strong>Penalty</div>
      </div>
      <label class="field">Candidate Roll Number</label>
      <input id="inp-roll" class="inp" value="${this.escapeHtml(payload.meta.rollNumber)}" />
      <label class="field">Booklet Number</label>
      <input id="inp-booklet" class="inp" value="${this.escapeHtml(payload.meta.bookletNumber)}" />
      <div id="tab-warn-info" style="font-size:11px;color:var(--muted);margin-bottom:12px;">Keyboard shortcuts: 1-5/A-E select, Arrows navigate, M flag. Tab switches are logged.</div>
      <button id="btn-start" class="btn btn-primary" style="width:100%;">Start Interactive Examination</button>
    </div>
  </div>

  <div id="exam-ui" class="hidden">
    <div class="topbar">
      <div><strong>${safeTitle}</strong> <span id="top-progress" style="font-size:12px;opacity:.8;margin-left:8px;"></span></div>
      <div style="display:flex;align-items:center;gap:8px;">
        <span id="tab-switch-badge" class="hidden" style="background:#DC2626;padding:3px 8px;border-radius:6px;font-size:11px;font-weight:700;"></span>
        <button class="btn btn-outline" style="padding:4px 8px;" onclick="adjFont(-1)">A-</button>
        <button class="btn btn-outline" style="padding:4px 8px;" onclick="adjFont(1)">A+</button>
        <span id="clock" style="font-family:monospace;font-weight:800;background:rgba(255,255,255,.15);padding:5px 10px;border-radius:6px;">00:00</span>
        <button class="btn" style="background:#16a34a;color:#fff;" onclick="submitExam()">Submit</button>
      </div>
    </div>
    <div class="workspace">
      <div class="card">
        <div style="display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid var(--border);padding-bottom:8px;">
          <span id="q-badge" style="font-weight:800;color:var(--navy);"></span>
          <button id="btn-mark" class="btn btn-outline" onclick="toggleMark()">Mark for Review</button>
        </div>
        <div id="q-stem" class="q-stem"></div>
        <div id="q-opts"></div>
        <div style="display:flex;justify-content:space-between;margin-top:14px;padding-top:12px;border-top:1px solid var(--border);">
          <button class="btn btn-outline" onclick="clearAns()">Clear</button>
          <div style="display:flex;gap:8px;">
            <button class="btn btn-outline" onclick="go(-1)">Previous</button>
            <button class="btn btn-navy" onclick="go(1)">Save &amp; Next</button>
          </div>
        </div>
      </div>
      <div class="card">
        <div style="font-weight:800;font-size:12px;text-transform:uppercase;color:var(--muted);">Question Palette</div>
        <div id="palette" class="palette-grid"></div>
      </div>
    </div>
  </div>

  <div id="results-ui" class="hidden" style="max-width:960px;margin:20px auto;padding:0 16px;"></div>

  <script>
    const DATA = ${safeJson};
    let idx = 0, answers = {}, marked = {}, qTimes = {}, secLeft = DATA.meta.durationMinutes * 60, timerId = null, tabSwitches = 0, fontPx = 15;
    function adjFont(d){ fontPx = Math.max(12, Math.min(22, fontPx + d)); document.documentElement.style.setProperty('--qSize', fontPx + 'px'); }
    function fmt(s){ const m = Math.floor(s/60), r = s%60; return String(m).padStart(2,'0')+':'+String(r).padStart(2,'0'); }
    function toggleLetter(cur, ltr){ const s = new Set((cur||'').split(',').filter(Boolean)); s.has(ltr)?s.delete(ltr):s.add(ltr); return Array.from(s).sort().join(','); }
    document.getElementById('btn-start').onclick = () => {
      document.getElementById('lobby').classList.add('hidden');
      document.getElementById('exam-ui').classList.remove('hidden');
      render();
      timerId = setInterval(() => {
        if(document.visibilityState !== 'visible') return;
        const q = DATA.questions[idx]; if(q) qTimes[q.num] = (qTimes[q.num]||0)+1;
        secLeft--; document.getElementById('clock').textContent = fmt(Math.max(0, secLeft));
        if(secLeft <= 0) submitExam();
      }, 1000);
    };
    document.addEventListener('visibilitychange', () => {
      if(document.visibilityState === 'hidden' && !document.getElementById('exam-ui').classList.contains('hidden')) {
        tabSwitches++;
        const b = document.getElementById('tab-switch-badge');
        b.textContent = 'Tab Switches: ' + tabSwitches;
        b.classList.remove('hidden');
      }
    });
    function pickOpt(key){ const q = DATA.questions[idx]; answers[q.num] = q.isMsq ? toggleLetter(answers[q.num], key) : key; if(!answers[q.num]) delete answers[q.num]; render(); }
    function clearAns(){ delete answers[DATA.questions[idx].num]; render(); }
    function toggleMark(){ const n = DATA.questions[idx].num; marked[n] = !marked[n]; render(); }
    function go(d){ const next = idx + d; if(next >= 0 && next < DATA.questions.length){ idx = next; render(); } }
    function render(){
      const q = DATA.questions[idx];
      document.getElementById('top-progress').textContent = 'Q.' + q.num + ' (' + (idx+1) + '/' + DATA.questions.length + ')';
      document.getElementById('q-badge').textContent = 'Question ' + q.num + (q.isMsq ? ' [MSQ - Multi Select]' : '');
      document.getElementById('btn-mark').textContent = marked[q.num] ? 'Marked ✓' : 'Mark for Review';
      document.getElementById('q-stem').innerHTML = q.stemHtml;
      document.getElementById('q-opts').innerHTML = q.options.map(o => {
        const sel = (answers[q.num]||'').split(',').includes(o.key);
        return '<button class="opt-btn '+(sel?'selected':'')+'" onclick="pickOpt(\\''+o.key+'\\')"><span class="opt-chip">'+o.key+'</span><span>'+o.rendered+'</span></button>';
      }).join('');
      document.getElementById('palette').innerHTML = DATA.questions.map((item, i) => {
        const cls = ['p-btn', i===idx?'cur':'', answers[item.num]?'ans':'', marked[item.num]?'mrk':''].join(' ');
        return '<button class="'+cls+'" onclick="idx='+i+';render()">'+item.num+'</button>';
      }).join('');
    }
    window.addEventListener('keydown', (e) => {
      if(document.getElementById('exam-ui').classList.contains('hidden')) return;
      const q = DATA.questions[idx];
      const mapIdx = ['1','2','3','4','5'].indexOf(e.key);
      const ltrIdx = ['A','B','C','D','E'].indexOf(e.key.toUpperCase());
      const i = mapIdx >= 0 ? mapIdx : ltrIdx;
      if(i >= 0 && i < q.options.length) pickOpt(q.options[i].key);
      else if(e.key === 'ArrowRight') go(1);
      else if(e.key === 'ArrowLeft') go(-1);
      else if(e.key.toUpperCase() === 'M') toggleMark();
    });
    function submitExam(){
      clearInterval(timerId);
      document.getElementById('exam-ui').classList.add('hidden');
      const rEl = document.getElementById('results-ui');
      rEl.classList.remove('hidden');
      let c=0, w=0, u=0, score=0;
      const cards = DATA.questions.map(q => {
        const chosen = (answers[q.num]||'').split(',').filter(Boolean).sort().join(',');
        const key = q.correctAnswer;
        let st = 'UNANSWERED', m = -DATA.meta.unattendedMarks;
        if(q.isNeutralized){ st='NEUTRALIZED'; m=DATA.meta.positiveMarks; c++; }
        else if(!chosen){ u++; }
        else if(chosen === key){ st='CORRECT'; m=DATA.meta.positiveMarks; c++; }
        else { st='INCORRECT'; m=-DATA.meta.negativeMarks; w++; }
        score += m;
        return '<div class="card" style="margin-bottom:10px;"><div><strong>Q.'+q.num+'</strong> • '+st+' ('+m.toFixed(2)+') • Yours: <strong>'+(chosen||'—')+'</strong> | Key: <strong>'+(key||'—')+'</strong> • '+(qTimes[q.num]||0)+'s</div><div style="margin-top:6px;">'+q.stemHtml+'</div>'+(q.explanationHtml?'<div style="margin-top:6px;font-size:12px;background:#F1F5F9;padding:8px;border-radius:6px;">'+q.explanationHtml+'</div>':'')+'</div>';
      }).join('');
      rEl.innerHTML = '<div class="card" style="margin-bottom:14px;background:var(--navy);color:#fff;"><h2>Score: '+score.toFixed(2)+' / '+(DATA.questions.length*DATA.meta.positiveMarks).toFixed(2)+'</h2><p style="font-size:13px;margin-top:4px;">Correct: '+c+' • Wrong: '+w+' • Skipped: '+u+' • Tab Switches: '+tabSwitches+'</p></div>' + cards;
    }
  </script>
</body>
</html>`;
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
