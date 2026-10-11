import { ExamConfig, QuestionPaper } from '../types/exam';
import { SvgQrGenerator } from './qrCodeHelper';

export interface OmrColumnDefinition {
  columnIndex: number;
  startQuestion: number;
  endQuestion: number;
  questionNumbers: number[];
}

export interface OmrGeometry {
  numColumns: number;
  rowsPerColumn: number;
  bubbleSizePx: number;
  bubbleFontPt: number;
  qNumFontPt: number;
  qNumWidthPx: number;
  rowPaddingCss: string;
  bubbleMarginCss: string;
}

export class OmrGenerator {
  /**
   * Calculates column and row distribution with MINIMAL padding:
   * - <= 30 questions: 2 columns
   * - 31 to 75 questions: 3 columns
   * - > 75 questions: 4 columns
   * Strictly keeps vertical gap tight (padding: 0.4px 0 or less, margin: 0).
   */
  public static calculateGeometry(questionCount: number): OmrGeometry {
    const count = Math.max(1, questionCount);

    let numColumns: number;
    let rowsPerColumn: number;

    if (count <= 30) {
      numColumns = 2;
      rowsPerColumn = Math.ceil(count / 2);
    } else if (count <= 75) {
      numColumns = 3;
      rowsPerColumn = Math.ceil(count / 3);
    } else {
      numColumns = 4;
      rowsPerColumn = Math.ceil(count / 4);
    }

    if (rowsPerColumn > 35) {
      return {
        numColumns,
        rowsPerColumn,
        bubbleSizePx: 11.2,
        bubbleFontPt: 5.5,
        qNumFontPt: 6.8,
        qNumWidthPx: 22,
        rowPaddingCss: '0.2px 0',
        bubbleMarginCss: '0 1.2px'
      };
    } else if (rowsPerColumn > 25) {
      return {
        numColumns,
        rowsPerColumn,
        bubbleSizePx: 11.5,
        bubbleFontPt: 6.0,
        qNumFontPt: 7.0,
        qNumWidthPx: 24,
        rowPaddingCss: '0.3px 0',
        bubbleMarginCss: '0 1.5px'
      };
    } else {
      return {
        numColumns,
        rowsPerColumn,
        bubbleSizePx: 11.5,
        bubbleFontPt: 6.0,
        qNumFontPt: 7.2,
        qNumWidthPx: 24,
        rowPaddingCss: '0.4px 0',
        bubbleMarginCss: '0 1.8px'
      };
    }
  }

  /**
   * Partitions questions sequentially across columns.
   */
  public static getColumns(questionCount: number): OmrColumnDefinition[] {
    const { numColumns, rowsPerColumn } = this.calculateGeometry(questionCount);
    const columns: OmrColumnDefinition[] = [];

    for (let c = 0; c < numColumns; c++) {
      const start = c * rowsPerColumn + 1;
      const end = Math.min((c + 1) * rowsPerColumn, questionCount);
      const questionNumbers: number[] = [];
      for (let q = start; q <= end; q++) {
        questionNumbers.push(q);
      }
      columns.push({
        columnIndex: c,
        startQuestion: start,
        endQuestion: end,
        questionNumbers
      });
    }

    return columns;
  }

  /**
   * Drop-out red CSS stylesheet:
   * - Full Drop-Out Red palette throughout the entire OMR sheet (#b91c1c and #f87171)
   * - Dedicated Left Signature & Instructions Sidebar (fixed 25.4mm / exactly 1 inch separation)
   * - Content-fit bottom margin: If questions end at 10cm from top, OMR bottom border hugs that height + padding, leaving rest below blank
   * - Minimized whitespace in OMR section
   * - Zero roll number fields
   * - High-visibility QR Code in header
   */
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
        padding: 5mm 6mm;
        margin: 0 auto;
        background: #ffffff;
        font-family: Arial, Helvetica, "Nimbus Sans L", sans-serif;
        color: #b91c1c;
        position: relative;
        overflow: hidden;
        ${isStandalone ? '' : 'page-break-before: always; break-before: page;'}
        page-break-inside: avoid;
        break-inside: avoid;
        break-after: avoid;
      }

      /* Primary Outer Container - Hugs questions tightly, leaving blank space below */
      .omr-wrapper {
        border: 2px solid #b91c1c;
        padding: 2mm 3mm;
        display: block;
        background: #ffffff;
        position: relative;
        width: 100%;
      }

      /* Optical Scanning Registration Square Anchors located at corners of omr-wrapper */
      .omr-anchor {
        position: absolute;
        width: 6.5mm;
        height: 6.5mm;
        background-color: #b91c1c;
        z-index: 20;
      }
      .anchor-tl { top: 1.5mm; left: 1.5mm; }
      .anchor-tr { top: 1.5mm; right: 1.5mm; }
      .anchor-bl { bottom: 1.5mm; left: 1.5mm; }
      .anchor-br { bottom: 1.5mm; right: 1.5mm; }

      /* Top Header & Metadata Strip */
      .omr-top-header {
        border: 1.5px solid #b91c1c;
        background: #fff1f2;
        padding: 1.8mm 2.5mm;
        margin-bottom: 2mm;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 3mm;
      }
      .omr-top-title-area {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 0.8mm;
      }
      .omr-inst-name {
        font-size: 7.5pt;
        font-weight: 900;
        color: #991b1b;
        letter-spacing: 0.5px;
        text-transform: uppercase;
      }
      .omr-sheet-title {
        font-size: 10.5pt;
        font-weight: 900;
        color: #b91c1c;
        letter-spacing: 0.8px;
        text-transform: uppercase;
      }
      .omr-meta-badges {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 1.8mm;
        margin-top: 0.4mm;
      }
      .omr-badge {
        font-size: 6.5pt;
        font-weight: 800;
        color: #b91c1c;
        background: #ffffff;
        border: 1px solid #b91c1c;
        padding: 0.5mm 1.8mm;
        border-radius: 2px;
        white-space: nowrap;
      }

      /* Series Code Bubbles (a, b, c, d) */
      .omr-series-wrap {
        display: inline-flex;
        align-items: center;
        gap: 1.2mm;
        background: #ffffff;
        border: 1px solid #b91c1c;
        padding: 0.5mm 1.8mm;
        border-radius: 2px;
      }
      .omr-series-title {
        font-size: 6.5pt;
        font-weight: 800;
        color: #b91c1c;
      }
      .omr-series-circle {
        width: 12px;
        height: 12px;
        border: 1.2px solid #b91c1c;
        border-radius: 50%;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 5.8pt;
        font-weight: 800;
        color: #b91c1c;
        background: #ffffff;
        line-height: 1;
      }
      .omr-series-circle.selected {
        background: #b91c1c;
        color: #ffffff;
      }

      /* Prominent Top-Right QR Code Box (20mm x 20mm with label) */
      .omr-qr-box {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        background: #ffffff;
        border: 1.2px solid #b91c1c;
        padding: 1.2mm;
        flex-shrink: 0;
      }
      .omr-qr-label {
        font-size: 5pt;
        font-weight: 900;
        color: #b91c1c;
        margin-top: 0.8mm;
        letter-spacing: 0.4px;
        text-transform: uppercase;
      }

      /* Full-Width Response Section with Optical Calibration Track (No left sidebar) */
      .omr-response-section {
        width: 100%;
        border: 1.5px solid #b91c1c;
        background: #ffffff;
        display: flex;
        position: relative;
        overflow: hidden;
        margin-bottom: 2mm;
      }

      /* Horizontal Bottom Verification Section (Name and Signatures, 1 inch fixed size) */
      .omr-bottom-verification {
        display: flex;
        align-items: stretch;
        gap: 3mm;
        margin-bottom: 1.8mm;
      }
      .omr-verif-box {
        flex: 1;
        border: 1.2px solid #b91c1c;
        background: #fffbfa;
        padding: 2mm 2.5mm;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        box-sizing: border-box;
      }
      .omr-verif-label {
        font-size: 5.5pt;
        font-weight: 800;
        color: #b91c1c;
        text-transform: uppercase;
        margin-bottom: 1.2mm;
        letter-spacing: 0.3px;
      }
      .omr-verif-line {
        border-bottom: 1.2px solid #b91c1c;
        height: 1in;
        min-height: 1in;
        width: 100%;
        box-sizing: border-box;
      }
      .omr-sig-slot {
        height: 1in;
        min-height: 1in;
        border: 1.2px dashed #f87171;
        background: #ffffff;
        display: flex;
        align-items: flex-end;
        justify-content: center;
        padding-bottom: 2mm;
        font-size: 5.5pt;
        font-weight: 700;
        color: #b91c1c;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        box-sizing: border-box;
      }
      .omr-marking-banner {
        border: 1px solid #b91c1c;
        background: #fff1f2;
        color: #991b1b;
        padding: 1.2mm 2.5mm;
        font-size: 5.8pt;
        text-align: center;
        line-height: 1.2;
        margin-bottom: 1.5mm;
      }

      .omr-left-timing-track {
        width: 2.8mm;
        background: #fff1f2;
        border-right: 1.2px solid #f87171;
        display: flex;
        flex-direction: column;
        justify-content: space-around;
        align-items: center;
        padding: 0.5mm 0;
      }
      .timing-tick {
        width: 2mm;
        height: 1.2px;
        background: #b91c1c;
      }

      /* Questions Grid Container - minimum height, strictly hugs rows */
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
        padding: 0.3mm 0.8mm;
        border-right: 1.2px solid #f87171;
      }
      .omr-column:last-child {
        border-right: none;
      }

      /* Question Row with Minimal Padding (Strictly no horizontal borders) */
      .omr-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin: 0;
        border: none !important;
        border-top: none !important;
        border-bottom: none !important;
      }
      /* Soft Blush zebra striping every 5 questions without dividing borders */
      .omr-row-shaded {
        background-color: #fff1f2;
        border: none !important;
      }

      /* Monospaced 3-digit Question Number in Drop-Out Red */
      .omr-qnum {
        font-weight: 800;
        text-align: right;
        margin-right: 1.5px;
        font-family: "Courier New", Courier, monospace;
        color: #b91c1c;
        letter-spacing: -0.2px;
      }

      /* Drop-out Red Circular Bubbles (Standardized 3.8–4.0mm: 3.9mm) */
      .bubble {
        width: 3.9mm;
        height: 3.9mm;
        min-width: 3.9mm;
        min-height: 3.9mm;
        max-width: 3.9mm;
        max-height: 3.9mm;
        border: 1.1px solid #b91c1c;
        border-radius: 50%;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-weight: 700;
        font-size: 5.5pt;
        color: #b91c1c;
        background: #ffffff;
        line-height: 1;
        box-sizing: border-box;
      }

      /* Bottom Footer with Timing Track */
      .omr-bottom-footer {
        border-top: 1.5px solid #b91c1c;
        padding-top: 0.8mm;
        display: flex;
        flex-direction: column;
        gap: 0.6mm;
      }
      .omr-footer-text {
        font-size: 5.4pt;
        font-weight: 800;
        color: #b91c1c;
        text-transform: uppercase;
        letter-spacing: 0.4px;
        text-align: center;
      }
      .omr-bottom-timing-track {
        display: flex;
        justify-content: space-between;
        align-items: center;
        height: 1.8mm;
        padding: 0 3mm;
        border-top: 1px solid #f87171;
      }
      .bottom-timing-tick {
        width: 1.2px;
        height: 1.5mm;
        background: #b91c1c;
      }
    `;
  }

  /**
   * Generates Drop-out Red OMR HTML markup:
   * - Full Drop-Out Red Palette across the entire sheet
   * - Left-hand Signature and Instructions bar (fixed 25.4mm / 1 inch width)
   * - Response section hugs the questions tightly with minimal padding
   * - If questions end early, bottom of OMR wrapper ends right there + padding, leaving blank space below
   * - No roll number anywhere
   * - High-resolution QR Code in header
   */
  public static generateHtml(paper: QuestionPaper, config: ExamConfig, isStandalone: boolean = false): string {
    const questionCount = Math.max(1, paper.questions.length);
    const geometry = this.calculateGeometry(questionCount);
    const columns = this.getColumns(questionCount);

    const seriesText = paper.metadata.setNumber || config.seriesCode || 'SET-A';
    const cleanSeries = seriesText.replace(/SET\s*-?\s*/i, '').trim().toUpperCase() || 'A';

    // Generate 20mm Top-Right Offline Machine-Grading QR Code SVG
    const qrPayload = SvgQrGenerator.generatePayload(
      paper.metadata.id || 'EXAM',
      paper.metadata.subject || 'General Studies',
      cleanSeries,
      config.timeAllowed || '120 Min',
      config.maxMarks || '200',
      paper.questions
    );
    const qrSvg = SvgQrGenerator.generateSvg(qrPayload, 20, '#b91c1c');

    // Generate Left Timing Ticks
    const leftTimingTicksCount = Math.max(15, geometry.rowsPerColumn);
    const leftTimingTicksHtml = Array.from({ length: leftTimingTicksCount }, () => '<div class="timing-tick"></div>').join('');

    // Generate Bottom Timing Ticks
    const bottomTimingTicksHtml = Array.from({ length: 32 }, () => '<div class="bottom-timing-tick"></div>').join('');

    // Build Question Bubble Grid
    let gridHtml = '<div class="omr-grid-container">\n';
    columns.forEach((col) => {
      gridHtml += '<div class="omr-column">\n';
      col.questionNumbers.forEach((qNum, idx) => {
        const blockIndex = Math.floor((qNum - 1) / 5);
        const isShaded = blockIndex % 2 === 1;
        gridHtml += `
          <div class="omr-row ${isShaded ? 'omr-row-shaded' : ''}" style="padding: ${geometry.rowPaddingCss};">
            <span class="omr-qnum" style="width: ${geometry.qNumWidthPx}px; font-size: ${geometry.qNumFontPt}pt;">${String(qNum).padStart(3, '0')}</span>
            <span class="bubble" style="width: 3.8mm; height: 3.8mm; margin: ${geometry.bubbleMarginCss};">a</span>
            <span class="bubble" style="width: 3.8mm; height: 3.8mm; margin: ${geometry.bubbleMarginCss};">b</span>
            <span class="bubble" style="width: 3.8mm; height: 3.8mm; margin: ${geometry.bubbleMarginCss};">c</span>
            <span class="bubble" style="width: 3.8mm; height: 3.8mm; margin: ${geometry.bubbleMarginCss};">d</span>
          </div>
        `;
      });
      gridHtml += '</div>\n';
    });
    gridHtml += '</div>';

    return `
      <div class="omr-page">
        <div class="omr-wrapper">
          <!-- 4 Corner Optical Scanning Registration Anchors on OMR border -->
          <div class="omr-anchor anchor-tl"></div>
          <div class="omr-anchor anchor-tr"></div>
          <div class="omr-anchor anchor-bl"></div>
          <div class="omr-anchor anchor-br"></div>

          <!-- Top Header: Institutional Identity, Metadata & Visible QR Code -->
          <div class="omr-top-header">
            <div class="omr-top-title-area">
              <div class="omr-inst-name">${this.escapeHtml(config.institutionName || 'CENTRAL BOARD OF EXAMINATION')}</div>
              <div class="omr-sheet-title">${this.escapeHtml(paper.metadata.title || 'OMR RESPONSE SHEET')}</div>
              <div class="omr-meta-badges">
                <span class="omr-badge">SUBJECT: ${this.escapeHtml(paper.metadata.subject || 'GENERAL STUDIES')}</span>
                <span class="omr-badge">TIME: ${this.escapeHtml(config.timeAllowed || '120 MIN')}</span>
                <span class="omr-badge">MARKS: ${this.escapeHtml(config.maxMarks || '200')}</span>
                <span class="omr-badge">QUESTIONS: ${paper.questions.length}</span>

                <!-- Test Series Code (a, b, c, d) -->
                <div class="omr-series-wrap">
                  <span class="omr-series-title">SERIES:</span>
                  ${['A', 'B', 'C', 'D'].map(letter => {
                    const isSelected = letter === cleanSeries;
                    return `<span class="omr-series-circle ${isSelected ? 'selected' : ''}">${letter.toLowerCase()}</span>`;
                  }).join('')}
                </div>
              </div>
            </div>

            <!-- Top-Right QR Code Box -->
            <div class="omr-qr-box" title="Offline OMR Machine Evaluation Key">
              ${qrSvg}
              <span class="omr-qr-label">OFFLINE KEY</span>
            </div>
          </div>

          <!-- Main Body: Full-Width Questions Grid with Optical Timing Track (No left sidebar) -->
          <div class="omr-response-section">
            <div class="omr-left-timing-track">
              ${leftTimingTicksHtml}
            </div>
            ${gridHtml}
          </div>

          <!-- Bottom Verification Section (Name, Signatures, Rules - Horizontal, not on left, no roll no) -->
          <div class="omr-bottom-verification">
            <div class="omr-verif-box">
              <span class="omr-verif-label">Candidate Name (In Block Letters)</span>
              <div class="omr-verif-line"></div>
            </div>
            <div class="omr-verif-box">
              <span class="omr-verif-label">Candidate Signature</span>
              <div class="omr-sig-slot">Sign Inside Box</div>
            </div>
            <div class="omr-verif-box">
              <span class="omr-verif-label">Invigilator Signature &amp; Stamp</span>
              <div class="omr-sig-slot">Sig &amp; Stamp</div>
            </div>
          </div>

          <!-- Marking Instructions Banner -->
          <div class="omr-marking-banner">
            <strong>INSTRUCTIONS:</strong> 1. Use Blue/Black ballpoint pen only. 2. Darken completely one bubble for each question. 3. Do not fold or tear this sheet. 4. Multiple markings invalid.
          </div>

          <!-- Bottom Footer -->
          <div class="omr-bottom-footer">
            <div class="omr-footer-text">
              OPTICAL MARK READER (OMR) EVALUATION SHEET • DO NOT TEAR OR MUTILATE
            </div>
            <div class="omr-bottom-timing-track">
              ${bottomTimingTicksHtml}
            </div>
          </div>
        </div>
      </div>
    `;
  }

  private static escapeHtml(str: string): string {
    if (!str) return '';
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
}
