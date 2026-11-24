import { MsqScoringPolicy, QuestionItem } from '../types/exam';

export type QuestionGradeStatus = 'CORRECT' | 'PARTIAL' | 'INCORRECT' | 'UNANSWERED' | 'NEUTRALIZED';

export interface QuestionGradeResult {
  status: QuestionGradeStatus;
  marks: number;
  correctSelections: string[];
  wrongSelections: string[];
  missedCorrectSelections: string[];
}

const VALID_LETTERS = new Set(['A', 'B', 'C', 'D', 'E']);

const MULTI_SELECT_CUE_PATTERNS = [
  /multiple[-\s]*select/i,
  /\bselect\s+(?:all|more\s+than\s+one)\b/i,
  /\bmore\s+than\s+one\s+(?:option|correct|answer)\b/i,
  /\bchoose\s+(?:all|all\s+that\s+apply)\b/i,
  /\bmultiple\s+correct\b/i,
  /\bMSQ\b/i,
];

const SINGLE_CHOICE_STATEMENT_EXCLUSION_PATTERNS = [
  /which\s+of\s+the\s+statements?\s+(?:given\s+)?(?:above|below)\s+(?:is|are)\s+correct/i,
  /select\s+the\s+correct\s+answer\s+using\s+the\s+code/i,
  /how\s+many\s+of\s+the\s+(?:statements?|pairs?)\s+(?:given\s+)?above/i,
  /statement\s+(?:I|II|1|2)\s+(?:is|are)\s+correct/i,
];

export const AnswerKeyMatcher = {
  /** Normalizes 'c, a', 'A & C', '(B)' -> 'A,C' or 'B' */
  normalizeKey(raw: string | null | undefined): string {
    const letters = this.parseSelectedLetters(raw);
    return letters.join(',');
  },

  /** Extracts sorted unique option letters (A..E) from a raw answer string */
  parseSelectedLetters(raw: string | null | undefined): string[] {
    if (!raw || typeof raw !== 'string') return [];
    const cleaned = raw
      .toUpperCase()
      .replace(/OPTION\s*/g, '')
      .replace(/[()[\]{}]/g, ' ')
      .replace(/&|\+|\bAND\b|\//g, ',');

    const seen = new Set<string>();
    const tokens = cleaned.split(/[,;\s]+/).filter(Boolean);
    for (const tok of tokens) {
      const ch = tok.trim();
      if (VALID_LETTERS.has(ch)) {
        seen.add(ch);
      } else if (/^[A-E]{2,5}$/.test(ch)) {
        // Handle compact keys like "AC" or "ABC"
        for (const c of ch) {
          if (VALID_LETTERS.has(c)) seen.add(c);
        }
      }
    }
    return Array.from(seen).sort();
  },

  toggleSelection(currentRaw: string | null | undefined, optionLetter: string): string {
    const letter = optionLetter.trim().toUpperCase();
    if (!VALID_LETTERS.has(letter)) return this.normalizeKey(currentRaw);
    const set = new Set(this.parseSelectedLetters(currentRaw));
    if (set.has(letter)) {
      set.delete(letter);
    } else {
      set.add(letter);
    }
    return Array.from(set).sort().join(',');
  },

  containsOption(keyRaw: string | null | undefined, letter: string): boolean {
    const target = letter.trim().toUpperCase();
    return this.parseSelectedLetters(keyRaw).includes(target);
  },

  areKeysEquivalent(a: string | null | undefined, b: string | null | undefined): boolean {
    const normA = this.normalizeKey(a);
    const normB = this.normalizeKey(b);
    return normA.length > 0 && normA === normB;
  },

  formatKeyForDisplay(raw: string | null | undefined): string {
    const letters = this.parseSelectedLetters(raw);
    if (letters.length === 0) return '—';
    return letters.join(', ');
  },

  isMultiSelect(q: QuestionItem): boolean {
    if (q.questionType === 'multiple-select') return true;
    const keyLetters = this.parseSelectedLetters(q.correctAnswer);
    if (keyLetters.length >= 2) return true;

    const stem = q.question || '';
    if (SINGLE_CHOICE_STATEMENT_EXCLUSION_PATTERNS.some((re) => re.test(stem))) {
      return false;
    }
    return MULTI_SELECT_CUE_PATTERNS.some((re) => re.test(stem));
  },

  scoreQuestion(
    question: QuestionItem,
    userAnswer: string | null | undefined,
    positiveMarks: number,
    negativeMarks: number,
    unattendedMarks: number = 0,
    msqPolicy: MsqScoringPolicy = 'ALL_OR_NOTHING'
  ): QuestionGradeResult {
    const official = this.parseSelectedLetters(question.correctAnswer);
    const selected = this.parseSelectedLetters(userAnswer);

    if (question.isNeutralized) {
      return {
        status: 'NEUTRALIZED',
        marks: positiveMarks,
        correctSelections: official,
        wrongSelections: [],
        missedCorrectSelections: [],
      };
    }

    if (selected.length === 0) {
      return {
        status: 'UNANSWERED',
        marks: unattendedMarks > 0 ? -unattendedMarks : 0,
        correctSelections: [],
        wrongSelections: [],
        missedCorrectSelections: official,
      };
    }

    if (official.length === 0) {
      return {
        status: 'UNANSWERED',
        marks: 0,
        correctSelections: [],
        wrongSelections: [],
        missedCorrectSelections: [],
      };
    }

    const officialSet = new Set(official);
    const selectedSet = new Set(selected);

    const correctSelections = selected.filter((l) => officialSet.has(l));
    const wrongSelections = selected.filter((l) => !officialSet.has(l));
    const missedCorrectSelections = official.filter((l) => !selectedSet.has(l));

    if (wrongSelections.length === 0 && missedCorrectSelections.length === 0) {
      return {
        status: 'CORRECT',
        marks: positiveMarks,
        correctSelections,
        wrongSelections,
        missedCorrectSelections,
      };
    }

    if (
      msqPolicy === 'PARTIAL_STRICT' &&
      official.length >= 2 &&
      wrongSelections.length === 0 &&
      correctSelections.length > 0 &&
      correctSelections.length < official.length
    ) {
      const partialMarks = Math.round((positiveMarks * (correctSelections.length / official.length)) * 100) / 100;
      return {
        status: 'PARTIAL',
        marks: partialMarks,
        correctSelections,
        wrongSelections,
        missedCorrectSelections,
      };
    }

    return {
      status: 'INCORRECT',
      marks: negativeMarks > 0 ? -negativeMarks : 0,
      correctSelections,
      wrongSelections,
      missedCorrectSelections,
    };
  },
};
