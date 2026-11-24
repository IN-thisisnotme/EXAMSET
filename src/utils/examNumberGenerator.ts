import { QuestionPaper } from '../types/exam';

/**
 * Generates and normalizes standardized 10-digit Roll Numbers,
 * 7-digit Test Booklet Numbers, and 2-digit Set Codes matching ExamNumberGenerator.kt.
 */
export const ExamNumberGenerator = {
  ROLL_DIGITS: 10,
  BOOKLET_DIGITS: 7,
  SET_DIGITS: 2,

  digitsOnly(raw: string | undefined | null): string {
    if (!raw) return '';
    return raw.replace(/\D/g, '');
  },

  normalizeRollNumber(raw: string | undefined | null, length: number = 10): string {
    const digits = this.digitsOnly(raw);
    if (!digits) {
      return length === 10 ? '0000000001' : '1'.padStart(length, '0');
    }
    if (digits.length > length) {
      return digits.slice(digits.length - length);
    }
    return digits.padStart(length, '0');
  },

  normalizeBookletNumber(raw: string | undefined | null, length: number = 7): string {
    const digits = this.digitsOnly(raw);
    if (!digits) {
      return length === 7 ? '0000001' : '1'.padStart(length, '0');
    }
    if (digits.length > length) {
      return digits.slice(digits.length - length);
    }
    return digits.padStart(length, '0');
  },

  normalizeSetCode(raw: string | undefined | null): string {
    const trimmed = (raw || '').trim();
    if (!trimmed) return '01';
    const digits = this.digitsOnly(trimmed);
    if (digits) {
      const sliced = digits.length > 2 ? digits.slice(digits.length - 2) : digits;
      return sliced.padStart(2, '0');
    }
    const letterMatch = trimmed.toUpperCase().match(/[A-Z]/);
    if (letterMatch) {
      const idx = letterMatch[0].charCodeAt(0) - 'A'.charCodeAt(0) + 1;
      return String(Math.max(1, Math.min(99, idx))).padStart(2, '0');
    }
    return '01';
  },

  nextRollNumber(existing: string[], yearPrefix?: number): string {
    const yr = yearPrefix ?? new Date().getFullYear();
    const yrPrefixStr = String(yr);
    let maxSeq = 0;
    for (const r of existing) {
      const norm = this.normalizeRollNumber(r, 10);
      if (norm.startsWith(yrPrefixStr)) {
        const seq = parseInt(norm.slice(4), 10);
        if (!Number.isNaN(seq) && seq > maxSeq) maxSeq = seq;
      } else {
        const num = parseInt(norm, 10);
        if (!Number.isNaN(num) && num > maxSeq && num < 999999) maxSeq = num;
      }
    }
    const nextSeq = String(maxSeq + 1).padStart(6, '0');
    return `${yrPrefixStr}${nextSeq}`;
  },

  nextBookletNumber(existing: string[]): string {
    let maxNum = 7000000;
    for (const b of existing) {
      const num = parseInt(this.digitsOnly(b), 10);
      if (!Number.isNaN(num) && num > maxNum) maxNum = num;
    }
    return String(maxNum + 1).padStart(7, '0');
  },

  generateSequentialNumbers(
    startRoll: string,
    startBooklet: string,
    count: number
  ): { rollNumber: string; bookletNumber: string }[] {
    let rollVal = BigInt(this.normalizeRollNumber(startRoll, 10));
    let bookletVal = BigInt(this.normalizeBookletNumber(startBooklet, 7));
    const list: { rollNumber: string; bookletNumber: string }[] = [];
    for (let i = 0; i < count; i++) {
      list.push({
        rollNumber: String(rollVal + BigInt(i)).padStart(10, '0').slice(-10),
        bookletNumber: String(bookletVal + BigInt(i)).padStart(7, '0').slice(-7),
      });
    }
    return list;
  },

  generateCohortManifestCsv(papers: QuestionPaper[]): string {
    const headers = ['Set No', 'Title', 'Subject', 'Roll Number', 'Booklet Number', 'Questions', 'Target Exam'];
    const esc = (v: string) => `"${String(v || '').replace(/"/g, '""')}"`;
    const rows = papers.map((p) => [
      esc(p.metadata.setNumber || '01'),
      esc(p.metadata.title),
      esc(p.metadata.subject),
      esc(this.normalizeRollNumber(p.metadata.rollNumber)),
      esc(this.normalizeBookletNumber(p.metadata.bookletNumber)),
      String(p.questions.length),
      esc(p.metadata.targetExam),
    ].join(','));
    return [headers.join(','), ...rows].join('\n');
  }
};
