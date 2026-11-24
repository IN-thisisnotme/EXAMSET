import { QuestionItem, QuestionOptions } from '../types/exam';

export interface AssertionReasonParts {
  leadIn?: string;
  assertion: string;
  reason: string;
  leadOut?: string;
}

export interface StatementItem {
  label: string;
  text: string;
}

export interface StatementQuestionParts {
  leadIn?: string;
  statements: StatementItem[];
  leadOut?: string;
}

export interface DualMatchRow {
  leftKey: string;
  leftText: string;
  rightKey: string;
  rightText: string;
}

export interface DualMatchParts {
  leadIn?: string;
  leftHeader: string;
  rightHeader: string;
  rows: DualMatchRow[];
  leadOut?: string;
}

export interface MarkdownTableData {
  headers: string[];
  rows: string[][];
}

export function parseAssertionReason(text: string): AssertionReasonParts | null {
  if (!text || typeof text !== 'string') return null;
  const normalized = text.trim();

  const aRegex =
    /(?:^|\n)\s*(?:(?:\*\*|__)?\s*)?(?:Assertion\s*(?:\([a-zA-Z]\)|[a-zA-Z])?|अभिकथन\s*(?:\([a-zA-Z\u0900-\u097F]\)|[a-zA-Z\u0900-\u097F])?|\([Aa]\)\s*Assertion)\s*(?:\*\*|__)?\s*[:\-–—]?\s*(?:\*\*|__)?/i;
  const rRegex =
    /(?:^|\n)\s*(?:(?:\*\*|__)?\s*)?(?:Reason\s*(?:\([a-zA-Z]\)|[a-zA-Z])?|कारण\s*(?:\([a-zA-Z\u0900-\u097F]\)|[a-zA-Z\u0900-\u097F])?|\([Rr]\)\s*Reason)\s*(?:\*\*|__)?\s*[:\-–—]?\s*(?:\*\*|__)?/i;

  const aMatch = normalized.match(aRegex);
  const rMatch = normalized.match(rRegex);
  if (!aMatch || !rMatch || aMatch.index === undefined || rMatch.index === undefined) return null;
  if (rMatch.index <= aMatch.index) return null;

  const rawLeadIn = normalized.substring(0, aMatch.index).trim();
  const cleanLeadIn = rawLeadIn
    .replace(/^\s*(?:#{1,6}\s*)?(?:Assertion[–\- ]Reason|अभिकथन[–\- ]कारण)\s*[:.\-]?\s*/i, '')
    .trim();
  const leadIn = cleanLeadIn.length > 0 ? cleanLeadIn : undefined;

  const aStart = aMatch.index + aMatch[0].length;
  const rawAssertion = normalized.substring(aStart, rMatch.index).trim();
  const rStart = rMatch.index + rMatch[0].length;
  const afterReason = normalized.substring(rStart).trim();

  const leadOutRegex =
    /(?:\n|^)\s*(?:(?:\*\*|__)?\s*)?(In\s+(?:the\s+)?light\s+of\s+the\s+above\s+statements|Select\s+the\s+correct\s+answer|Choose\s+the\s+correct|Which\s+of\s+the\s+following\s+is\s+correct|उपर्युक्त\s+कथनों\s+के\s+आलोक\s+में|उपरोक्त\s+कथनों\s+के|Codes?\s*[:\-]|कूट\s*[:\-])[\s\S]*$/i;
  const leadOutMatch = afterReason.match(leadOutRegex);

  let rawReason = afterReason;
  let leadOut: string | undefined;
  if (leadOutMatch && leadOutMatch.index !== undefined) {
    rawReason = afterReason.substring(0, leadOutMatch.index).trim();
    leadOut = leadOutMatch[0].trim();
  }

  const assertion = rawAssertion.replace(/^[:\-–—]\s*/, '').trim();
  const reason = rawReason.replace(/^[:\-–—]\s*/, '').trim();
  if (!assertion || !reason) return null;

  return { leadIn, assertion, reason, leadOut };
}

export function parseStatementQuestion(text: string): StatementQuestionParts | null {
  if (!text || typeof text !== 'string') return null;

  const statementRegex =
    /(?:^|\n)\s*(?:(?:\*\*|__)?\s*)?(?:Statement\s*(?:[IVX\d]+)|कथन\s*(?:[IVX\d]+))\s*[:\-–—]\s*(?:\*\*|__)?/gi;
  const matches = Array.from(text.matchAll(statementRegex));

  if (matches.length >= 2) {
    const firstMatch = matches[0];
    if (firstMatch.index === undefined) return null;
    const leadIn = text.substring(0, firstMatch.index).trim() || undefined;
    const statements: StatementItem[] = [];

    for (let i = 0; i < matches.length; i++) {
      const cur = matches[i];
      const next = matches[i + 1];
      const start = cur.index! + cur[0].length;
      const end = next ? next.index! : text.length;
      const label = cur[0].replace(/[\n*:_–—\-]/g, '').trim();
      let body = text.substring(start, end).trim();

      if (i === matches.length - 1) {
        const leadOutMatch = body.match(
          /(?:\n|^)\s*(?:In\s+(?:the\s+)?light\s+of|Choose\s+the\s+correct|Select\s+the\s+correct|Which\s+of\s+the\s+(?:above|statements)|How\s+many\s+of\s+the\s+(?:statements|above))[\s\S]*$/i
        );
        if (leadOutMatch && leadOutMatch.index !== undefined) {
          const leadOut = leadOutMatch[0].trim();
          body = body.substring(0, leadOutMatch.index).trim();
          statements.push({ label, text: body });
          return { leadIn, statements, leadOut };
        }
      }
      statements.push({ label, text: body });
    }
    return { leadIn, statements };
  }

  // Also parse numbered UPSC statements (e.g. "Consider the following statements:\n1. ...\n2. ...\nWhich...")
  const numberedRegex = /(?:^|\n)\s*(\d)\.\s+([^\n]+)/g;
  const numMatches = Array.from(text.matchAll(numberedRegex));
  if (numMatches.length >= 2 && /consider\s+the\s+following|which\s+(?:of\s+the\s+(?:above|statements)|combination)/i.test(text)) {
    const firstIdx = numMatches[0].index!;
    const lastMatch = numMatches[numMatches.length - 1];
    const lastEnd = lastMatch.index! + lastMatch[0].length;
    const leadIn = text.substring(0, firstIdx).trim() || undefined;
    const leadOut = text.substring(lastEnd).trim() || undefined;
    const statements = numMatches.map((m) => ({ label: m[1], text: m[2].trim() }));
    return { leadIn, statements, leadOut };
  }

  return null;
}

export function isDualMatchQuestion(questionText: string): boolean {
  if (!questionText) return false;
  const lower = questionText.toLowerCase();
  return (
    (lower.includes('list-i') || lower.includes('list i') || lower.includes('column i') || lower.includes('match the following')) &&
    (lower.includes('list-ii') || lower.includes('list ii') || lower.includes('column ii') || /(?:^|\n)\s*[A-D][\.\:\)]\s+.+(?:\t|\s{2,})[1-4][\.\:\)]/m.test(questionText))
  );
}

export function parseDualMatchData(text: string): DualMatchParts | null {
  if (!text) return null;
  const lines = text.split('\n');
  const rows: DualMatchRow[] = [];
  let leftHeader = 'List I';
  let rightHeader = 'List II';
  const leadInLines: string[] = [];
  const leadOutLines: string[] = [];
  let sawTable = false;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;

    // Header row in tab or pipe format
    if (!sawTable && (/list[-\s]*i/i.test(line) && /list[-\s]*ii/i.test(line)) || (/column\s*i/i.test(line) && /column\s*ii/i.test(line))) {
      const cells = line.includes('|')
        ? line.split('|').map((c) => c.replace(/\*\*/g, '').trim()).filter(Boolean)
        : line.split(/\t|\s{2,}/).map((c) => c.trim()).filter(Boolean);
      if (cells.length >= 2) {
        leftHeader = cells[0];
        rightHeader = cells[1];
        sawTable = true;
        continue;
      }
    }

    // Markdown separator line
    if (/^\|?[\s:\-]+\|[\s:\-|]+$/.test(line)) {
      sawTable = true;
      continue;
    }

    // Row like "| A. Cursorial | 1. Climbing |" or "A. Insulin\t1. Blood glucose"
    if (line.includes('|') || line.includes('\t')) {
      const cells = line.includes('|')
        ? line.split('|').map((c) => c.replace(/\*\*/g, '').trim()).filter(Boolean)
        : line.split('\t').map((c) => c.trim()).filter(Boolean);

      if (cells.length >= 2) {
        const leftMatch = cells[0].match(/^([A-Ea-e1-5])[\.\:\)]\s*(.+)$/);
        const rightMatch = cells[1].match(/^([1-5ivxIVXa-eA-E])[\.\:\)]\s*(.+)$/);
        if (leftMatch || rightMatch || sawTable) {
          sawTable = true;
          rows.push({
            leftKey: leftMatch ? leftMatch[1].toUpperCase() : '',
            leftText: leftMatch ? leftMatch[2].trim() : cells[0],
            rightKey: rightMatch ? rightMatch[1] : '',
            rightText: rightMatch ? rightMatch[2].trim() : cells[1],
          });
          continue;
        }
      }
    }

    if (!sawTable) leadInLines.push(line);
    else leadOutLines.push(line);
  }

  if (rows.length < 2) return null;
  return {
    leadIn: leadInLines.join('\n').trim() || undefined,
    leftHeader,
    rightHeader,
    rows,
    leadOut: leadOutLines.join('\n').trim() || undefined,
  };
}

export function extractMarkdownTables(text: string): { textWithoutTables: string; tables: MarkdownTableData[] } {
  if (!text || !text.includes('|')) return { textWithoutTables: text, tables: [] };
  const lines = text.split('\n');
  const tables: MarkdownTableData[] = [];
  const keptLines: string[] = [];

  let i = 0;
  while (i < lines.length) {
    const line = lines[i].trim();
    const next = lines[i + 1]?.trim() || '';
    if (line.includes('|') && /^\|?\s*:?-{2,}:?\s*\|/.test(next)) {
      const headers = line.split('|').map((c) => c.trim()).filter(Boolean);
      const rows: string[][] = [];
      i += 2;
      while (i < lines.length && lines[i].includes('|')) {
        const rowCells = lines[i].split('|').map((c) => c.trim()).filter(Boolean);
        if (rowCells.length > 0) rows.push(rowCells);
        i++;
      }
      if (headers.length >= 2 && rows.length >= 1) {
        tables.push({ headers, rows });
      }
    } else {
      keptLines.push(lines[i]);
      i++;
    }
  }

  return { textWithoutTables: keptLines.join('\n').trim(), tables };
}

export function isAssertionReason(questionText: string, options?: QuestionOptions, qType?: string): boolean {
  if (qType && qType.toLowerCase().includes('assertion')) return true;
  if (parseAssertionReason(questionText) !== null) return true;

  const qLower = (questionText || '').toLowerCase();
  const optLower = options ? `${options.a} ${options.b} ${options.c} ${options.d}`.toLowerCase() : '';

  return (
    (qLower.includes('assertion') && qLower.includes('reason')) ||
    (qLower.includes('अभिकथन') && qLower.includes('कारण')) ||
    optLower.includes('both a and r are true') ||
    optLower.includes('both assertion (a) and reason (r)') ||
    optLower.includes('correct explanation of a') ||
    optLower.includes('r is the correct explanation') ||
    optLower.includes('a is true, but r is false') ||
    optLower.includes('assertion (a) is true')
  );
}

export function shouldStackOptions(qType?: string, options?: QuestionOptions): boolean {
  if (!options) return false;
  const type = (qType || '').toLowerCase();
  if (
    type.includes('assertion') ||
    type.includes('match') ||
    type.includes('statement') ||
    type.includes('pair') ||
    type.includes('chronological') ||
    type.includes('multiple-select')
  ) {
    return true;
  }

  const allOpts = [options.a, options.b, options.c, options.d, options.e].filter((x): x is string => Boolean(x));
  if (allOpts.length >= 5) return true;
  return allOpts.some((opt) => opt.includes('\n') || opt.length > 34);
}

export function getDisplayTypeLabel(q: QuestionItem): string {
  const t = (q.questionType || '').toLowerCase();
  if (t.includes('multiple-select')) return 'MSQ (Multi-Select)';
  if (t.includes('assertion')) return 'Assertion–Reason';
  if (t.includes('match')) return 'Match List';
  if (t.includes('statement')) return 'Statement Analysis';
  if (t.includes('pair')) return 'Pair Matching';
  if (t.includes('chronological')) return 'Chronological';
  if (t.includes('numerical')) return 'Numerical';
  return 'Single Choice MCQ';
}
