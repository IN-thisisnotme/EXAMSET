import { QuestionItem, QuestionOptions, QuestionType } from '../types/exam';

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

/**
 * Parses an Assertion-Reason question stem into its component parts:
 * - leadIn (introductory instruction or context, if any)
 * - assertion (Assertion / अभिकथन text)
 * - reason (Reason / कारण text)
 * - leadOut (trailing prompt question, e.g. "In the light of the above statements...")
 */
export function parseAssertionReason(text: string): AssertionReasonParts | null {
  if (!text || typeof text !== 'string') return null;

  // Clean markdown bold markers around tags if any
  const normalized = text.trim();

  // Assertion marker regex: Assertion (A):, Assertion:, अभिकथन (A):, (A) Assertion:, etc.
  const aRegex = /(?:^|\n)\s*(?:(?:\*\*|__)?\s*)?(?:Assertion\s*(?:\([a-zA-Z]\)|[a-zA-Z])?|अभिकथन\s*(?:\([a-zA-Z\u0900-\u097F]\)|[a-zA-Z\u0900-\u097F])?|\([Aa]\)\s*Assertion)\s*(?:\*\*|__)?\s*[:\-–—]?\s*(?:\*\*|__)?/i;

  // Reason marker regex: Reason (R):, Reason:, कारण (R):, (R) Reason:, etc.
  const rRegex = /(?:^|\n)\s*(?:(?:\*\*|__)?\s*)?(?:Reason\s*(?:\([a-zA-Z]\)|[a-zA-Z])?|कारण\s*(?:\([a-zA-Z\u0900-\u097F]\)|[a-zA-Z\u0900-\u097F])?|\([Rr]\)\s*Reason)\s*(?:\*\*|__)?\s*[:\-–—]?\s*(?:\*\*|__)?/i;

  const aMatch = normalized.match(aRegex);
  const rMatch = normalized.match(rRegex);

  if (!aMatch || !rMatch || aMatch.index === undefined || rMatch.index === undefined) {
    return null;
  }

  // Reason must appear after Assertion
  if (rMatch.index <= aMatch.index) {
    return null;
  }

  // 1. Lead-in: Everything before Assertion marker
  const rawLeadIn = normalized.substring(0, aMatch.index).trim();
  // Strip any leading question type header like "Assertion–Reason" or "Assertion-Reason"
  const cleanLeadIn = rawLeadIn
    .replace(/^\s*(?:#{1,6}\s*)?(?:Assertion[–\- ]Reason|अभिकथन[–\- ]कारण)\s*[:\.\-]?\s*/i, '')
    .trim();
  const leadIn = cleanLeadIn.length > 0 ? cleanLeadIn : undefined;

  // 2. Assertion: Text between Assertion marker end and Reason marker start
  const aStart = aMatch.index + aMatch[0].length;
  const rawAssertion = normalized.substring(aStart, rMatch.index).trim();

  // 3. Reason & Lead-out: Text after Reason marker
  const rStart = rMatch.index + rMatch[0].length;
  const afterReason = normalized.substring(rStart).trim();

  // Detect common trailing lead-out prompts
  const leadOutRegex = /(?:\n|^)\s*(?:(?:\*\*|__)?\s*)?(In\s+(?:the\s+)?light\s+of\s+the\s+above\s+statements|Select\s+the\s+correct\s+answer|Choose\s+the\s+correct|Which\s+of\s+the\s+following\s+is\s+correct|उपर्युक्त\s+कथनों\s+के\s+आलोक\s+में|उपरोक्त\s+कथनों\s+के|Codes?\s*[:\-]|कूट\s*[:\-])[\s\S]*$/i;
  const leadOutMatch = afterReason.match(leadOutRegex);

  let rawReason = afterReason;
  let leadOut: string | undefined = undefined;

  if (leadOutMatch && leadOutMatch.index !== undefined) {
    rawReason = afterReason.substring(0, leadOutMatch.index).trim();
    leadOut = leadOutMatch[0].trim();
  }

  const assertion = rawAssertion.replace(/^[:\-–—]\s*/, '').trim();
  const reason = rawReason.replace(/^[:\-–—]\s*/, '').trim();

  if (!assertion || !reason) {
    return null;
  }

  return {
    leadIn,
    assertion,
    reason,
    leadOut
  };
}

/**
 * Parses statement-based questions (e.g. Statement I / Statement II, or 1., 2., 3.)
 */
export function parseStatementQuestion(text: string): StatementQuestionParts | null {
  if (!text || typeof text !== 'string') return null;

  const statementRegex = /(?:^|\n)\s*(?:(?:\*\*|__)?\s*)?(?:Statement\s*(?:[I|V|X\d]+)|कथन\s*(?:[I|V|X\d]+))\s*[:\-–—]\s*(?:\*\*|__)?/gi;
  const matches = Array.from(text.matchAll(statementRegex));

  if (matches.length < 2) {
    return null;
  }

  const firstMatch = matches[0];
  if (firstMatch.index === undefined) return null;

  const leadIn = text.substring(0, firstMatch.index).trim() || undefined;
  const statements: StatementItem[] = [];

  for (let i = 0; i < matches.length; i++) {
    const cur = matches[i];
    const next = matches[i + 1];
    const start = cur.index! + cur[0].length;
    const end = next ? next.index! : text.length;

    const label = cur[0].replace(/[\n\*\:_–—\-]/g, '').trim();
    let body = text.substring(start, end).trim();

    // Check if last statement has leadOut
    if (i === matches.length - 1) {
      const leadOutMatch = body.match(/(?:\n|^)\s*(?:In\s+(?:the\s+)?light\s+of|Choose\s+the\s+correct|Which\s+of\s+the\s+above)[\s\S]*$/i);
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

/**
 * Determines whether a question is an Assertion-Reason question by inspection.
 */
export function isAssertionReason(questionText: string, options?: QuestionOptions, qType?: string): boolean {
  if (qType && qType.toLowerCase().includes('assertion')) {
    return true;
  }

  if (parseAssertionReason(questionText) !== null) {
    return true;
  }

  const qLower = (questionText || '').toLowerCase();
  const optLower = options ? `${options.a} ${options.b} ${options.c} ${options.d}`.toLowerCase() : '';

  if (
    (qLower.includes('assertion') && qLower.includes('reason')) ||
    (qLower.includes('अभिकथन') && qLower.includes('कारण')) ||
    optLower.includes('both a and r are true') ||
    optLower.includes('both assertion (a) and reason (r)') ||
    optLower.includes('correct explanation of a') ||
    optLower.includes('r is the correct explanation') ||
    optLower.includes('a is true, but r is false') ||
    optLower.includes('assertion (a) is true')
  ) {
    return true;
  }

  return false;
}

/**
 * Determines whether options should be displayed in a stacked vertical list (single-column)
 * rather than a tight 2x2 grid.
 *
 * Rules:
 * 1. Assertion–Reason options are ALWAYS stacked (long explanatory sentences).
 * 2. Match the Following & Statement-based options are ALWAYS stacked.
 * 3. Any option containing line breaks (\n) is ALWAYS stacked.
 * 4. Any option longer than 30 characters is ALWAYS stacked.
 */
export function shouldStackOptions(qType?: string, options?: QuestionOptions): boolean {
  if (!options) return false;

  const type = (qType || '').toLowerCase();
  if (
    type.includes('assertion') ||
    type.includes('match') ||
    type.includes('statement') ||
    type.includes('pair') ||
    type.includes('chronological')
  ) {
    return true;
  }

  const allOpts = [options.a, options.b, options.c, options.d].filter(Boolean);

  // If any option has a newline or is long, stack options vertically
  return allOpts.some(opt => opt.includes('\n') || opt.length > 30);
}
