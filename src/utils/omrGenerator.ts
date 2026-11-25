import { ExamConfig, OmrBubblingMode, QuestionPaper } from '../types/exam';
import { ExamNumberGenerator } from './examNumberGenerator';
import { SvgQrGenerator } from './qrCodeHelper';

export interface OmrColumnDefinition {
  columnIndex: number;
  startQuestion: number;
  endQuestion: number;
  questionNumbers: number[];
}

export interface OmrGeometrySpec {
  numColumns: number;
  rowsPerColumn: number;
  bubbleSizeMm: number;
  bubbleFontPt: number;
  qNumFontPt: number;
  qNumWidthMm: number;
  rowPaddingMm: string;
  bubbleGapMm: number;
  optionCount: 4 | 5;
}

export class OmrGenerator {
  public static calculateGeometry(questionCount: number, optionCount: 4 | 5): OmrGeometrySpec {
    const count = Math.max(1, Math.min(200, questionCount));

    let numColumns = 4;
    if (count <= 40) numColumns = 2;
    else if (count <= 105) numColumns = 3;
    else numColumns = 4;

    const rowsPerColumn = Math.ceil(count / numColumns);

    if (rowsPerColumn > 38) {
      return {
        numColumns,
        rowsPerColumn,
        bubbleSizeMm: optionCount === 5 ? 3.2 : 3.55,
        bubbleFontPt: 5.2,
        qNumFontPt: 6.4,
        qNumWidthMm: 5.8,
        rowPaddingMm: '0.25mm 0',
        bubbleGapMm: optionCount === 5 ? 0.65 : 0.9,
        optionCount,
      };
    }

    if (rowsPerColumn > 25) {
      return {
        numColumns,
        rowsPerColumn,
        bubbleSizeMm: optionCount === 5 ? 3.45 : 3.75,
        bubbleFontPt: 5.6,
        qNumFontPt: 6.8,
        qNumWidthMm: 6.2,
        rowPaddingMm: '0.45mm 0',
        bubbleGapMm: optionCount === 5 ? 0.8 : 1.1,
        optionCount,
      };
    }

    return {
      numColumns,
      rowsPerColumn,
      bubbleSizeMm: 3.85,
      bubbleFontPt: 6.0,
      qNumFontPt: 7.2,
      qNumWidthMm: 6.5,
      rowPaddingMm: '0.7mm 0',
      bubbleGapMm: optionCount === 5 ? 1.0 : 1.4,
      optionCount,
    };
  }

  public static getColumns(questionCount: number, optionCount: 4 | 5): OmrColumnDefinition[] {
    const clamped = Math.max(1, Math.min(200, questionCount));
    const { numColumns, rowsPerColumn } = this.calculateGeometry(clamped, optionCount);
    const columns: OmrColumnDefinition[] = [];

    for (let c = 0; c < numColumns; c++) {
      const start = c * rowsPerColumn + 1;
      const end = Math.min((c + 1) * rowsPerColumn, clamped);
      const questionNumbers: number[] = [];
      for (let q = start; q <= end; q++) {
        questionNumbers.push(q);
      }
      columns.push({
        columnIndex: c,
        startQuestion: start,
        endQuestion: end,
        questionNumbers,
      });
    }
    return columns;
  }

  public static getOmrStyles(isStandalone: boolean = true): string {
    return `
      @page {
        size: A4 portrait;
        margin: 0;
      }
      * {
        box-sizing: border-box;
        -webkit-print-color-adjust: exact;
        print-color-adjust: exact;
      }
      .omr-page {
        width: 210mm;
        height: 297mm;
        max-height: 297mm;
        padding: 4mm 4.5mm;
        margin: 0 auto;
        background: #ffffff;
        font-family: Arial, Helvetica, sans-serif;
        color: #be185d;
        position: relative;
        overflow: hidden;
        ${isStandalone ? '' : 'page-break-before: always; break-before: page;'}
        page-break-inside: avoid;
        break-inside: avoid;
      }
      .omr-wrapper {
        border: 1.8px solid #be185d;
        padding: 2mm 2.5mm;
        background: #ffffff;
        position: relative;
        width: 100%;
        height: 288mm;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
      }
      .omr-anchor {
        position: absolute;
        width: 5.5mm;
        height: 5.5mm;
        background-color: #be185d;
        z-index: 20;
      }
      .anchor-tl { top: 1.2mm; left: 1.2mm; }
      .anchor-tr { top: 1.2mm; right: 1.2mm; }
      .anchor-bl { bottom: 1.2mm; left: 1.2mm; }
      .anchor-br { bottom: 1.2mm; right: 1.2mm; }

      .omr-top-header {
        border: 1.4px solid #be185d;
        background: #fdf2f8;
        padding: 1.5mm 3mm 1.5mm 7mm;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 2.5mm;
      }
      .omr-top-title-area {
        flex: 1;
        min-width: 0;
      }
      .omr-inst-name {
        font-size: 7pt;
        font-weight: 900;
        color: #9d174d;
        letter-spacing: 0.4px;
        text-transform: uppercase;
      }
      .omr-sheet-title {
        font-size: 10pt;
        font-weight: 900;
        color: #be185d;
        text-transform: uppercase;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .omr-meta-badges {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 1.4mm;
        margin-top: 0.6mm;
      }
      .omr-badge {
        font-size: 6pt;
        font-weight: 800;
        color: #be185d;
        background: #ffffff;
        border: 1px solid #be185d;
        padding: 0.4mm 1.4mm;
        border-radius: 2px;
      }
      .omr-qr-box {
        display: flex;
        flex-direction: column;
        align-items: center;
        background: #ffffff;
        border: 1px solid #be185d;
        padding: 0.8mm;
        flex-shrink: 0;
      }
      .omr-qr-label {
        font-size: 4.6pt;
        font-weight: 900;
        color: #be185d;
        margin-top: 0.4mm;
      }

      /* Main 2-panel OMR workspace: Left Identification + Right Response Grid */
      .omr-main-body {
        display: flex;
        gap: 2.2mm;
        flex: 1;
        min-height: 0;
        margin: 1.6mm 0;
      }
      .omr-left-id-col {
        width: 58mm;
        flex-shrink: 0;
        border: 1.3px solid #be185d;
        background: #fff9fb;
        padding: 1.4mm;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        gap: 1.2mm;
      }
      .id-matrix-box {
        border: 1px solid #f472b6;
        background: #ffffff;
        padding: 1mm;
      }
      .id-matrix-title {
        font-size: 5.8pt;
        font-weight: 900;
        text-align: center;
        color: #9d174d;
        text-transform: uppercase;
        border-bottom: 1px solid #fbcfe8;
        padding-bottom: 0.4mm;
        margin-bottom: 0.8mm;
      }
      .id-digits-row {
        display: flex;
        justify-content: center;
        gap: 0.6mm;
        margin-bottom: 0.8mm;
      }
      .id-digit-cell {
        width: 4.2mm;
        height: 4.5mm;
        border: 1px solid #be185d;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 7pt;
        font-weight: 900;
        font-family: "Courier New", monospace;
        color: #0f172a;
        background: #ffffff;
      }
      .id-bubbles-matrix {
        display: flex;
        justify-content: center;
        gap: 0.6mm;
      }
      .id-bubble-col {
        display: flex;
        flex-direction: column;
        gap: 0.5mm;
        align-items: center;
        width: 4.2mm;
      }
      .micro-bubble {
        width: 3.1mm;
        height: 3.1mm;
        border: 0.9px solid #be185d;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 4.6pt;
        font-weight: 700;
        color: #be185d;
        background: #ffffff;
      }
      .micro-bubble.darkened {
        background: #111827;
        border-color: #111827;
        color: #ffffff;
      }
      .id-decl-box {
        border: 1px solid #f472b6;
        background: #ffffff;
        padding: 1mm 1.2mm;
        font-size: 5pt;
        line-height: 1.25;
        color: #831843;
      }
      .id-write-box {
        border: 1px solid #be185d;
        background: #ffffff;
        padding: 1mm 1.2mm;
        min-height: 9mm;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
      }
      .id-write-label {
        font-size: 5pt;
        font-weight: 800;
        text-transform: uppercase;
        color: #9d174d;
      }

      /* Right Response Grid */
      .omr-response-section {
        flex: 1;
        border: 1.4px solid #be185d;
        background: #ffffff;
        display: flex;
        overflow: hidden;
      }
      .omr-left-timing-track {
        width: 2.6mm;
        background: #fdf2f8;
        border-right: 1px solid #f472b6;
        display: flex;
        flex-direction: column;
        justify-content: space-around;
        align-items: center;
        padding: 0.5mm 0;
      }
      .timing-tick {
        width: 1.8mm;
        height: 1.2px;
        background: #be185d;
      }
      .omr-grid-container {
        display: flex;
        flex: 1;
        width: 100%;
      }
      .omr-column {
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: flex-start;
        padding: 0.8mm 1mm;
        border-right: 1px solid #fbcfe8;
      }
      .omr-column:last-child {
        border-right: none;
      }
      .omr-col-header {
        font-size: 5.5pt;
        font-weight: 900;
        text-align: center;
        background: #fdf2f8;
        border-bottom: 1px solid #be185d;
        padding: 0.6mm 0;
        margin-bottom: 0.5mm;
        color: #9d174d;
      }
      .omr-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .omr-row-shaded {
        background-color: #fdf2f8;
      }
      .omr-qnum {
        font-weight: 800;
        text-align: right;
        font-family: "Courier New", Courier, monospace;
        color: #be185d;
        flex-shrink: 0;
      }
      .omr-bubbles-wrap {
        display: flex;
        align-items: center;
      }
      .bubble {
        border: 1px solid #be185d;
        border-radius: 50%;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-weight: 700;
        color: #be185d;
        background: #ffffff;
        line-height: 1;
      }

      /* Bottom Verification Row */
      .omr-bottom-verification {
        display: flex;
        gap: 2.5mm;
        align-items: stretch;
      }
      .omr-verif-box {
        flex: 1;
        border: 1px solid #be185d;
        background: #fff9fb;
        padding: 1mm 2mm;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        min-height: 14mm;
      }
      .omr-verif-label {
        font-size: 5.2pt;
        font-weight: 800;
        color: #9d174d;
        text-transform: uppercase;
      }
      .omr-sig-slot {
        border-top: 1px dashed #f472b6;
        padding-top: 0.8mm;
        margin-top: 6mm;
        font-size: 5pt;
        text-align: center;
        color: #be185d;
      }
      .omr-bottom-footer {
        border-top: 1.2px solid #be185d;
        padding-top: 0.6mm;
        margin-top: 1mm;
        display: flex;
        flex-direction: column;
        gap: 0.4mm;
      }
      .omr-footer-text {
        font-size: 5pt;
        font-weight: 800;
        color: #be185d;
        text-align: center;
      }
      .omr-bottom-timing-track {
        display: flex;
        justify-content: space-between;
        align-items: center;
        height: 1.6mm;
        padding: 0 5mm;
      }
      .bottom-timing-tick {
        width: 1.2px;
        height: 1.4mm;
        background: #be185d;
      }
    `;
  }

  private static renderDigitMatrixHtml(
    title: string,
    value: string,
    digitCount: number,
    mode: OmrBubblingMode
  ): string {
    const normalized = value.padStart(digitCount, '0').slice(-digitCount);
    const digits = normalized.split('');
    const showDigits = mode !== 'BLANK_CANDIDATE';
    const darken = mode === 'PRE_DARKENED_BUBBLES';

    const digitBoxes = digits
      .map((d) => `<div class="id-digit-cell">${showDigits ? this.escapeHtml(d) : ''}</div>`)
      .join('');

    const colsHtml = digits
      .map((targetDigit) => {
        const rows = Array.from({ length: 10 }, (_, idx) => {
          const digitStr = String(idx);
          const isMatch = darken && targetDigit === digitStr;
          return `<div class="micro-bubble ${isMatch ? 'darkened' : ''}">${digitStr}</div>`;
        }).join('');
        return `<div class="id-bubble-col">${rows}</div>`;
      })
      .join('');

    return `
      <div class="id-matrix-box">
        <div class="id-matrix-title">${this.escapeHtml(title)}</div>
        <div class="id-digits-row">${digitBoxes}</div>
        <div class="id-bubbles-matrix">${colsHtml}</div>
      </div>
    `;
  }

  public static generateHtml(
    paper: QuestionPaper,
    config: ExamConfig,
    isStandalone: boolean = false
  ): string {
    const rawCount = Math.max(1, paper.questions.length);
    const questionCount = Math.min(200, rawCount);
    const hasOptionE =
      config.omrOptionCount === 5 ||
      paper.questions.some(
        (q) => Boolean(q.options.e?.trim()) || (q.correctAnswer || '').toUpperCase().includes('E')
      );
    const optionCount: 4 | 5 = hasOptionE ? 5 : 4;

    const geometry = this.calculateGeometry(questionCount, optionCount);
    const columns = this.getColumns(questionCount, optionCount);

    const rollNum = ExamNumberGenerator.normalizeRollNumber(
      config.rollNumber || paper.metadata.rollNumber,
      10
    );
    const bookletNum = ExamNumberGenerator.normalizeBookletNumber(
      config.bookletNumber || paper.metadata.bookletNumber,
      7
    );
    const setCode = ExamNumberGenerator.normalizeSetCode(
      config.seriesCode || paper.metadata.setNumber
    );

    const qrPayload = SvgQrGenerator.generatePayload(
      paper.metadata.id || 'EXAM',
      paper.metadata.subject || config.subject || 'GS',
      setCode,
      String(config.durationMinutes || 120),
      config.maxMarks || String(questionCount * (config.positiveMarks || 2)),
      paper.questions.slice(0, 200)
    );
    const qrSvg = SvgQrGenerator.generateSvg(qrPayload, 17, '#be185d');

    const leftTimingTicksHtml = Array.from(
      { length: Math.max(18, geometry.rowsPerColumn) },
      () => '<div class="timing-tick"></div>'
    ).join('');
    const bottomTimingTicksHtml = Array.from(
      { length: 34 },
      () => '<div class="bottom-timing-tick"></div>'
    ).join('');

    const optionLetters = optionCount === 5 ? ['a', 'b', 'c', 'd', 'e'] : ['a', 'b', 'c', 'd'];

    const columnsHtml = columns
      .map((col) => {
        if (col.questionNumbers.length === 0) return '';
        const rowsHtml = col.questionNumbers
          .map((qNum) => {
            const isShaded = Math.floor((qNum - 1) / 5) % 2 === 1;
            const bubbles = optionLetters
              .map(
                (ltr) =>
                  `<span class="bubble" style="width:${geometry.bubbleSizeMm}mm;height:${geometry.bubbleSizeMm}mm;font-size:${geometry.bubbleFontPt}pt;margin-left:${geometry.bubbleGapMm}mm;">${ltr}</span>`
              )
              .join('');
            return `
              <div class="omr-row ${isShaded ? 'omr-row-shaded' : ''}" style="padding:${geometry.rowPaddingMm};">
                <span class="omr-qnum" style="width:${geometry.qNumWidthMm}mm;font-size:${geometry.qNumFontPt}pt;">${String(
              qNum
            ).padStart(3, '0')}</span>
                <div class="omr-bubbles-wrap">${bubbles}</div>
              </div>
            `;
          })
          .join('');
        return `
          <div class="omr-column">
            <div class="omr-col-header">Q.${String(col.startQuestion).padStart(3, '0')} – ${String(
          col.endQuestion
        ).padStart(3, '0')}</div>
            ${rowsHtml}
          </div>
        `;
      })
      .join('');

    const overflowNote =
      rawCount > 200
        ? `<span class="omr-badge">NOTE: PAGE 1 COVERS Q.001–200 OF ${rawCount}</span>`
        : '';

    return `
      <div class="omr-page">
        <div class="omr-wrapper">
          <div class="omr-anchor anchor-tl"></div>
          <div class="omr-anchor anchor-tr"></div>
          <div class="omr-anchor anchor-bl"></div>
          <div class="omr-anchor anchor-br"></div>

          <div class="omr-top-header">
            <div class="omr-top-title-area">
              <div class="omr-inst-name">${this.escapeHtml(
                config.institutionName || 'EXAMINATION BOARD'
              )}</div>
              <div class="omr-sheet-title">${this.escapeHtml(
                config.examTitle || paper.metadata.title || 'OMR RESPONSE SHEET'
              )}</div>
              <div class="omr-meta-badges">
                <span class="omr-badge">SUB: ${this.escapeHtml(
                  paper.metadata.subject || config.subject || 'GENERAL'
                )}</span>
                <span class="omr-badge">SET: ${this.escapeHtml(setCode)}</span>
                <span class="omr-badge">Q.COUNT: ${questionCount} (${optionCount}-OPT)</span>
                <span class="omr-badge">TIME: ${config.durationMinutes || 120} MIN</span>
                ${overflowNote}
              </div>
            </div>
            <div class="omr-qr-box" title="Offline OMR Machine Key">
              ${qrSvg}
              <span class="omr-qr-label">OFFLINE KEY</span>
            </div>
          </div>

          <div class="omr-main-body">
            <div class="omr-left-id-col">
              ${this.renderDigitMatrixHtml('Roll Number (10 Digits)', rollNum, 10, config.omrBubblingMode)}
              ${this.renderDigitMatrixHtml('Test Booklet No. (7 Digits)', bookletNum, 7, config.omrBubblingMode)}
              ${this.renderDigitMatrixHtml('Booklet Set Code (2 Digits)', setCode, 2, config.omrBubblingMode)}

              <div class="id-write-box">
                <span class="id-write-label">Candidate Name (Running Hand)</span>
              </div>

              <div class="id-decl-box">
                <strong>DECLARATION:</strong> I certify that I am the candidate whose name and Roll No. appear on this Answer Sheet.
              </div>
            </div>

            <div class="omr-response-section">
              <div class="omr-left-timing-track">${leftTimingTicksHtml}</div>
              <div class="omr-grid-container">${columnsHtml}</div>
            </div>
          </div>

          <div>
            <div class="omr-bottom-verification">
              <div class="omr-verif-box">
                <span class="omr-verif-label">Candidate's L.H. Thumb Impression</span>
                <div class="omr-sig-slot">Impression Inside Box</div>
              </div>
              <div class="omr-verif-box">
                <span class="omr-verif-label">Candidate Signature &amp; Time</span>
                <div class="omr-sig-slot">Sign Inside Box</div>
              </div>
              <div class="omr-verif-box">
                <span class="omr-verif-label">Invigilator 1 &amp; 2 Signature</span>
                <div class="omr-sig-slot">Signature with Time</div>
              </div>
            </div>

            <div class="omr-bottom-footer">
              <div class="omr-footer-text">
                USE BLUE/BLACK BALLPOINT PEN ONLY • DARKEN BUBBLES COMPLETELY • DO NOT FOLD OR STAPLE
              </div>
              <div class="omr-bottom-timing-track">${bottomTimingTicksHtml}</div>
            </div>
          </div>
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
