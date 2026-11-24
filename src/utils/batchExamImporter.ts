import {
  BatchImportItem,
  BatchItemStatus,
  BatchValidationSummary,
  QuestionItem,
  QuestionPaper,
  ValidationIssue,
} from '../types/exam';
import { AnswerKeyMatcher } from './answerKeyMatcher';
import { ExamNumberGenerator } from './examNumberGenerator';
import { McqParser } from './mcqParser';

function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function makeUuid(): string {
  return `batch-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function coerceQuestions(arr: any[]): QuestionItem[] {
  return arr.map((item, idx) => {
    const opts = item.options || {};
    return {
      id: item.id || `q-${Date.now()}-${idx + 1}`,
      num: Number(item.num || item.number || idx + 1),
      question: String(item.question || item.stem || item.text || '').trim(),
      options: {
        a: String(opts.a ?? opts.A ?? item.optionA ?? ''),
        b: String(opts.b ?? opts.B ?? item.optionB ?? ''),
        c: String(opts.c ?? opts.C ?? item.optionC ?? ''),
        d: String(opts.d ?? opts.D ?? item.optionD ?? ''),
        e: opts.e || opts.E || item.optionE ? String(opts.e ?? opts.E ?? item.optionE) : undefined,
      },
      correctAnswer: AnswerKeyMatcher.normalizeKey(item.correctAnswer ?? item.answer ?? item.key ?? '') || null,
      explanation: item.explanation || item.solution ? String(item.explanation ?? item.solution) : null,
      questionType: item.questionType,
      diagramUrl: item.diagramUrl || null,
      diagramLabel: item.diagramLabel || null,
      formula: item.formula || null,
      isNeutralized: Boolean(item.isNeutralized),
      isVerified: Boolean(item.isVerified),
    };
  });
}

function validatePaper(
  paper: QuestionPaper,
  fileName: string,
  fileSizeBytes: number,
  preview: string
): BatchImportItem {
  const issues: ValidationIssue[] = [];

  if (!paper.questions || paper.questions.length === 0) {
    issues.push({ severity: 'ERROR', message: 'Contains 0 valid questions.' });
  } else {
    let missingKeys = 0;
    let missingExps = 0;
    let missingOpts = 0;

    for (const q of paper.questions) {
      if (!q.question.trim()) {
        issues.push({ severity: 'ERROR', message: `Q${q.num}: Empty question stem.`, questionNumber: q.num });
      }
      if (!q.options.a.trim() || !q.options.b.trim() || !q.options.c.trim() || !q.options.d.trim()) {
        missingOpts++;
      }
      if (!q.correctAnswer) missingKeys++;
      if (!q.explanation) missingExps++;
    }

    if (missingOpts > 0) {
      issues.push({ severity: 'WARNING', message: `${missingOpts} question(s) have incomplete A–D options.` });
    }
    if (missingKeys > 0) {
      issues.push({ severity: 'WARNING', message: `${missingKeys} question(s) are missing answer keys.` });
    }
    if (missingExps > 0) {
      issues.push({ severity: 'INFO', message: `${missingExps} question(s) have no explanations.` });
    }
  }

  const hasError = issues.some((i) => i.severity === 'ERROR');
  const hasWarning = issues.some((i) => i.severity === 'WARNING');
  const status: BatchItemStatus = hasError ? 'INVALID' : hasWarning ? 'WARNING' : 'VALID';

  return {
    id: makeUuid(),
    fileName,
    fileSizeBytes,
    fileSizeFormatted: formatBytes(fileSizeBytes),
    paper: hasError ? null : paper,
    status,
    issues,
    isSelected: !hasError,
    rawJsonPreview: preview.slice(0, 220),
  };
}

export const BatchExamImporter = {
  validateRawContent(rawContent: string, fileName: string, fileSizeBytes: number = 0): BatchImportItem[] {
    const trimmed = rawContent.trim();
    const byteSize = fileSizeBytes || new Blob([trimmed]).size;

    if (!trimmed) {
      return [
        {
          id: makeUuid(),
          fileName,
          fileSizeBytes: byteSize,
          fileSizeFormatted: formatBytes(byteSize),
          paper: null,
          status: 'INVALID',
          issues: [{ severity: 'ERROR', message: 'File is empty.' }],
          isSelected: false,
          rawJsonPreview: '',
        },
      ];
    }

    // Try JSON first if starts with { or [ or file ends with .json
    if (trimmed.startsWith('{') || trimmed.startsWith('[') || fileName.toLowerCase().endsWith('.json')) {
      try {
        const parsed = JSON.parse(trimmed);
        const now = Date.now();
        const baseName = fileName.replace(/\.[^.]+$/, '');

        if (Array.isArray(parsed)) {
          if (parsed.length === 0) {
            return [validatePaper({ metadata: { id: `p-${now}`, title: baseName, subject: 'General', topic: '', setNumber: '01', targetExam: 'Competitive Exam', createdAt: now }, questions: [] }, fileName, byteSize, trimmed)];
          }
          const first = parsed[0];
          if (first && typeof first === 'object' && !Array.isArray(first.questions) && (first.question || first.options)) {
            const paper: QuestionPaper = {
              metadata: {
                id: `paper-${now}-${Math.random().toString(36).slice(2, 6)}`,
                title: baseName,
                subject: 'General Assessment',
                topic: '',
                setNumber: '01',
                targetExam: 'Competitive Exam',
                createdAt: now,
              },
              questions: coerceQuestions(parsed),
            };
            return [validatePaper(paper, fileName, byteSize, trimmed)];
          }
          return parsed.map((obj: any, i: number) => {
            const meta = obj.metadata || {};
            const paper: QuestionPaper = {
              metadata: {
                id: meta.id || `paper-${now}-${i + 1}`,
                title: meta.title || obj.title || `${baseName} Part ${i + 1}`,
                subject: meta.subject || obj.subject || 'General Assessment',
                topic: meta.topic || obj.topic || '',
                setNumber: ExamNumberGenerator.normalizeSetCode(meta.setNumber || obj.setNumber),
                targetExam: meta.targetExam || obj.targetExam || 'Competitive Exam',
                rollNumber: meta.rollNumber,
                bookletNumber: meta.bookletNumber,
                durationMinutes: meta.durationMinutes,
                positiveMarks: meta.positiveMarks,
                negativeMarks: meta.negativeMarks,
                folderId: meta.folderId,
                createdAt: meta.createdAt || now,
              },
              questions: Array.isArray(obj.questions) ? coerceQuestions(obj.questions) : [],
            };
            return validatePaper(paper, `${baseName}_part_${i + 1}.json`, byteSize, JSON.stringify(obj));
          });
        }

        // Bundle { questionSets: [...] } or { sets: [...] }
        const bundleSets = parsed.questionSets || parsed.sets;
        if (Array.isArray(bundleSets)) {
          return bundleSets.map((obj: any, i: number) => {
            const meta = obj.metadata || {};
            const paper: QuestionPaper = {
              metadata: {
                id: meta.id || `paper-${now}-${i + 1}`,
                title: meta.title || obj.title || `${baseName} Set ${i + 1}`,
                subject: meta.subject || obj.subject || 'General Assessment',
                topic: meta.topic || obj.topic || '',
                setNumber: ExamNumberGenerator.normalizeSetCode(meta.setNumber || obj.setNumber || String(i + 1)),
                targetExam: meta.targetExam || obj.targetExam || 'Competitive Exam',
                folderId: meta.folderId || parsed.folderId,
                createdAt: meta.createdAt || now,
              },
              questions: Array.isArray(obj.questions) ? coerceQuestions(obj.questions) : [],
            };
            return validatePaper(paper, `${baseName}_set_${i + 1}.json`, byteSize, JSON.stringify(obj));
          });
        }

        // Single QuestionPaper object
        const meta = parsed.metadata || {};
        const paper: QuestionPaper = {
          metadata: {
            id: meta.id || `paper-${now}`,
            title: meta.title || parsed.title || baseName,
            subject: meta.subject || parsed.subject || 'General Assessment',
            topic: meta.topic || parsed.topic || '',
            setNumber: ExamNumberGenerator.normalizeSetCode(meta.setNumber || parsed.setNumber),
            targetExam: meta.targetExam || parsed.targetExam || 'Competitive Exam',
            rollNumber: meta.rollNumber || parsed.rollNumber,
            bookletNumber: meta.bookletNumber || parsed.bookletNumber,
            durationMinutes: meta.durationMinutes ?? parsed.durationMinutes,
            positiveMarks: meta.positiveMarks ?? parsed.positiveMarks,
            negativeMarks: meta.negativeMarks ?? parsed.negativeMarks,
            msqScoringPolicy: meta.msqScoringPolicy || parsed.msqScoringPolicy,
            folderId: meta.folderId || parsed.folderId,
            createdAt: meta.createdAt || now,
          },
          questions: Array.isArray(parsed.questions) ? coerceQuestions(parsed.questions) : [],
        };
        return [validatePaper(paper, fileName, byteSize, trimmed)];
      } catch (err: any) {
        return [
          {
            id: makeUuid(),
            fileName,
            fileSizeBytes: byteSize,
            fileSizeFormatted: formatBytes(byteSize),
            paper: null,
            status: 'INVALID',
            issues: [{ severity: 'ERROR', message: `Malformed JSON: ${err.message}` }],
            isSelected: false,
            rawJsonPreview: trimmed.slice(0, 220),
          },
        ];
      }
    }

    // Fallback: Parse raw text/Markdown table via McqParser
    const parseRes = McqParser.parse(trimmed);
    const now = Date.now();
    const paper: QuestionPaper = {
      metadata: {
        id: `paper-${now}-${Math.random().toString(36).slice(2, 6)}`,
        title: fileName.replace(/\.[^.]+$/, '') || 'Imported Text Set',
        subject: 'General Assessment',
        topic: '',
        setNumber: '01',
        targetExam: 'Competitive Exam',
        createdAt: now,
      },
      questions: parseRes.questions,
    };
    return [validatePaper(paper, fileName, byteSize, trimmed)];
  },

  summarize(items: BatchImportItem[]): BatchValidationSummary {
    return {
      totalFiles: items.length,
      totalPapers: items.filter((i) => i.paper !== null).length,
      validCount: items.filter((i) => i.status === 'VALID').length,
      warningCount: items.filter((i) => i.status === 'WARNING').length,
      invalidCount: items.filter((i) => i.status === 'INVALID').length,
      totalQuestions: items.reduce((acc, i) => acc + (i.paper?.questions.length || 0), 0),
    };
  },
};
