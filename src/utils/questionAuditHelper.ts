import {
  AuditIssueType,
  AuditQuestionReport,
  AuditSummaryMetrics,
  QuestionItem,
  QuestionPaper,
} from '../types/exam';
import { AnswerKeyMatcher } from './answerKeyMatcher';

const VISUAL_DEPENDENCY_PATTERNS = [
  /\b(?:see|refer\s+to|in|from)\s+(?:the\s+)?(?:given|above|below|adjacent|following)?\s*(?:figure|diagram|graph|chart|circuit|image|map|picture)\b/i,
  /\bas\s+shown\s+in\s+(?:the\s+)?(?:fig(?:ure)?|diagram|graph|chart|picture)\b/i,
  /\[(?:diagram|image|figure|chart)\]/i,
];

export const QuestionAuditHelper = {
  generatePhase1Prompt(paper: QuestionPaper, subset: QuestionItem[] = paper.questions): string {
    const lines = [
      '=== PHASE 1: MCQ QUESTION STEM & ANSWER KEY AUDIT ===',
      `Paper: "${paper.metadata.title}" (Subject: ${paper.metadata.subject}, Set: ${paper.metadata.setNumber})`,
      'ROLE: Senior academic psychometrician & chief examination reviewer.',
      'TASK: Audit every question stem, distractors (A..E), and verify the marked answer key.',
      'Return RAW, VALID JSON ONLY inside a single ```json ... ``` block:',
      '```json',
      '[',
      '  {',
      '    "qNum": 1,',
      '    "status": "CLEAN | WRONG_KEY | MISSING_KEY | FLAWED_QUESTION | VAGUE_OPTIONS",',
      '    "currentKey": "A",',
      '    "suggestedKey": "A",',
      '    "flawReason": null,',
      '    "recheckedExplanation": "Concise verification reasoning..."',
      '  }',
      ']',
      '```',
      '',
      '=== QUESTIONS TO AUDIT ===',
    ];

    for (const q of subset) {
      const msqTag = AnswerKeyMatcher.isMultiSelect(q) ? ' [MSQ]' : '';
      lines.push(`Q${q.num}${msqTag}. ${q.question.trim()}`);
      if (q.diagramLabel) lines.push(`   [Diagram: ${q.diagramLabel}]`);
      if (q.formula) lines.push(`   [Formula: ${q.formula}]`);
      const opts = [
        `A) ${q.options.a}`,
        `B) ${q.options.b}`,
        `C) ${q.options.c}`,
        `D) ${q.options.d}`,
        q.options.e ? `E) ${q.options.e}` : null,
      ].filter(Boolean);
      lines.push(`   ${opts.join(' | ')}`);
      lines.push(`   Marked Key: ${q.correctAnswer || '[NONE]'}`);
      lines.push('');
    }
    return lines.join('\n');
  },

  generatePhase2ReplacementPrompt(paper: QuestionPaper, flawedReports: AuditQuestionReport[]): string {
    const lines = [
      '=== PHASE 2: IN-PLACE REPLACEMENT FOR FLAWED QUESTIONS ===',
      `Paper: "${paper.metadata.title}" (${paper.metadata.subject})`,
      'Generate self-contained replacement MCQs preserving syllabus topic, difficulty, and numbering.',
      'Return RAW, VALID JSON ONLY inside ```json ... ```:',
      '```json',
      '[',
      '  {',
      '    "qNum": 1,',
      '    "replacementQuestion": {',
      '      "question": "Clear text-solvable stem...",',
      '      "options": { "a": "...", "b": "...", "c": "...", "d": "..." },',
      '      "correctAnswer": "A",',
      '      "explanation": "Step-by-step rationale..."',
      '    }',
      '  }',
      ']',
      '```',
      '',
      '=== FLAWED ITEMS ===',
    ];

    for (const rep of flawedReports) {
      const q = rep.originalQuestion || paper.questions.find((x) => x.num === rep.qNum);
      lines.push(`Q${rep.qNum}: Reason = ${rep.flawReason || rep.status}`);
      if (q) {
        lines.push(`   Original Stem: ${q.question}`);
        lines.push(`   Options: A) ${q.options.a} | B) ${q.options.b} | C) ${q.options.c} | D) ${q.options.d}`);
      }
      lines.push('');
    }
    return lines.join('\n');
  },

  generatePhase3ExplanationPrompt(paper: QuestionPaper, subset: QuestionItem[] = paper.questions): string {
    const lines = [
      '=== PHASE 3: STEP-BY-STEP EXPLANATIONS & DISTRACTOR AUDIT ===',
      `Paper: "${paper.metadata.title}" (${paper.metadata.subject})`,
      'Output RAW, VALID JSON ONLY inside ```json ... ```:',
      '```json',
      '[',
      '  {',
      '    "qNum": 1,',
      '    "recheckedExplanation": "Step 1: ...\\n**Core Concept:** ...\\n**Distractor Analysis:** ..."',
      '  }',
      ']',
      '```',
      '',
      '=== QUESTIONS ===',
    ];
    for (const q of subset) {
      lines.push(`Q${q.num}. ${q.question.trim()}`);
      lines.push(`   A) ${q.options.a} | B) ${q.options.b} | C) ${q.options.c} | D) ${q.options.d}${q.options.e ? ` | E) ${q.options.e}` : ''}`);
      lines.push(`   Key: ${q.correctAnswer || '[NONE]'}`);
      if (q.explanation) lines.push(`   Draft Explanation: ${q.explanation}`);
      lines.push('');
    }
    return lines.join('\n');
  },

  runHeuristicPreAudit(paper: QuestionPaper): AuditQuestionReport[] {
    return paper.questions.map((q) => {
      const normKey = AnswerKeyMatcher.normalizeKey(q.correctAnswer);
      const optList = [
        q.options.a.trim(),
        q.options.b.trim(),
        q.options.c.trim(),
        q.options.d.trim(),
      ];
      if (q.options.e?.trim()) optList.push(q.options.e.trim());

      const missingCoreOpts = [q.options.a, q.options.b, q.options.c, q.options.d].some((o) => !o.trim());
      const uniqueNonBlank = new Set(optList.filter(Boolean).map((o) => o.toLowerCase()));
      const hasDuplicates = uniqueNonBlank.size < optList.filter(Boolean).length;
      const missingVisual =
        !q.diagramUrl &&
        !q.diagramLabel &&
        VISUAL_DEPENDENCY_PATTERNS.some((re) => re.test(q.question));

      let status: AuditIssueType = 'CLEAN';
      let flawReason: string | null = null;

      if (missingCoreOpts) {
        status = 'MISSING_OPTIONS';
        flawReason = 'One or more options (A–D) are blank or failed to parse.';
      } else if (!normKey) {
        status = 'MISSING_KEY';
        flawReason = 'No official answer key is assigned to this question.';
      } else if (hasDuplicates) {
        status = 'VAGUE_OPTIONS';
        flawReason = 'Duplicate option text detected among choices.';
      } else if (missingVisual) {
        status = 'FLAWED_QUESTION';
        flawReason = 'Question references a visual figure/diagram without an attached diagramUrl or diagramLabel.';
      } else if (!q.explanation || !q.explanation.trim()) {
        status = 'MISSING_EXPLANATION';
        flawReason = 'Step-by-step explanation is empty.';
      }

      return {
        qNum: q.num,
        status,
        currentKey: normKey || null,
        suggestedKey: normKey || null,
        flawReason,
        recheckedExplanation: q.explanation,
        originalQuestion: q,
        isResolved: status === 'CLEAN' || !!q.isVerified,
        isVerified: !!q.isVerified,
        isNeutralized: !!q.isNeutralized,
      };
    });
  },

  parseAuditReport(rawResponse: string, paper: QuestionPaper): AuditQuestionReport[] {
    let cleaned = rawResponse.trim();
    const fenced = cleaned.match(/```(?:json)?\s*([\s\S]*?)```/i);
    if (fenced?.[1]) {
      cleaned = fenced[1].trim();
    } else {
      const start = cleaned.indexOf('[');
      const end = cleaned.lastIndexOf(']');
      if (start !== -1 && end > start) {
        cleaned = cleaned.slice(start, end + 1);
      }
    }

    const parsed = JSON.parse(cleaned);
    if (!Array.isArray(parsed)) {
      throw new Error('Audit JSON must be an array of question report objects.');
    }

    const qByNum = new Map(paper.questions.map((q) => [q.num, q]));

    return parsed.map((item: any): AuditQuestionReport => {
      const qNum = Number(item.qNum ?? item.num ?? 0);
      const orig = qByNum.get(qNum) || null;
      const rawStatus = String(item.status || 'CLEAN').toUpperCase();

      let status: AuditIssueType = 'CLEAN';
      if (rawStatus.includes('WRONG')) status = 'WRONG_KEY';
      else if (rawStatus.includes('MISSING_KEY')) status = 'MISSING_KEY';
      else if (rawStatus.includes('MISSING_OPTION')) status = 'MISSING_OPTIONS';
      else if (rawStatus.includes('VAGUE')) status = 'VAGUE_OPTIONS';
      else if (rawStatus.includes('FLAW')) status = 'FLAWED_QUESTION';
      else if (rawStatus.includes('EXPLANATION')) status = 'MISSING_EXPLANATION';

      let replacementQuestion: QuestionItem | null = null;
      if (item.replacementQuestion && typeof item.replacementQuestion === 'object') {
        const rq = item.replacementQuestion;
        const opts = rq.options || {};
        replacementQuestion = {
          id: orig?.id || `q-${Date.now()}-${qNum}`,
          num: qNum,
          question: String(rq.question || ''),
          options: {
            a: String(opts.a ?? ''),
            b: String(opts.b ?? ''),
            c: String(opts.c ?? ''),
            d: String(opts.d ?? ''),
            e: opts.e ? String(opts.e) : undefined,
          },
          correctAnswer: AnswerKeyMatcher.normalizeKey(rq.correctAnswer) || null,
          explanation: rq.explanation ? String(rq.explanation) : null,
          isVerified: true,
        };
      }

      const suggestedKey = item.suggestedKey ? AnswerKeyMatcher.normalizeKey(String(item.suggestedKey)) : null;
      const recheckedExplanation = item.recheckedExplanation
        ? String(item.recheckedExplanation)
        : item.explanation
          ? String(item.explanation)
          : null;

      return {
        qNum,
        status,
        currentKey: orig?.correctAnswer || (item.currentKey ? String(item.currentKey) : null),
        suggestedKey,
        flawReason: item.flawReason ? String(item.flawReason) : null,
        recheckedExplanation,
        replacementQuestion,
        originalQuestion: orig,
        isResolved: status === 'CLEAN' && !replacementQuestion && !recheckedExplanation,
        isVerified: status === 'CLEAN',
        isNeutralized: !!orig?.isNeutralized,
      };
    });
  },

  mergeAuditReports(
    existing: AuditQuestionReport[],
    incoming: AuditQuestionReport[],
    paper: QuestionPaper
  ): AuditQuestionReport[] {
    const byNum = new Map<number, AuditQuestionReport>();
    for (const r of existing) byNum.set(r.qNum, r);
    for (const r of incoming) {
      const prev = byNum.get(r.qNum);
      if (!prev) {
        byNum.set(r.qNum, r);
      } else {
        byNum.set(r.qNum, {
          ...prev,
          status: r.status !== 'CLEAN' ? r.status : prev.status,
          suggestedKey: r.suggestedKey || prev.suggestedKey,
          flawReason: r.flawReason || prev.flawReason,
          recheckedExplanation: r.recheckedExplanation || prev.recheckedExplanation,
          replacementQuestion: r.replacementQuestion || prev.replacementQuestion,
          originalQuestion: r.originalQuestion || prev.originalQuestion || paper.questions.find((q) => q.num === r.qNum) || null,
          isResolved: r.replacementQuestion ? false : r.isResolved && prev.isResolved,
        });
      }
    }
    return Array.from(byNum.values()).sort((a, b) => a.qNum - b.qNum);
  },

  applyReportToPaper(paper: QuestionPaper, report: AuditQuestionReport): QuestionPaper {
    const updated = paper.questions.map((q) => {
      if (q.num !== report.qNum) return q;
      if (report.replacementQuestion) {
        return {
          ...report.replacementQuestion,
          id: q.id,
          num: q.num,
          isVerified: true,
        };
      }
      return {
        ...q,
        correctAnswer: report.suggestedKey || q.correctAnswer,
        explanation: report.recheckedExplanation || q.explanation,
        isVerified: true,
      };
    });
    return {
      ...paper,
      questions: updated,
      metadata: { ...paper.metadata, updatedAt: Date.now() },
    };
  },

  computeMetrics(reports: AuditQuestionReport[]): AuditSummaryMetrics {
    return {
      totalAudited: reports.length,
      wrongKeysCount: reports.filter((r) => r.status === 'WRONG_KEY').length,
      missingKeysCount: reports.filter((r) => r.status === 'MISSING_KEY').length,
      flawedQuestionsCount: reports.filter((r) => r.status === 'FLAWED_QUESTION' || r.status === 'VAGUE_OPTIONS').length,
      missingOptionsCount: reports.filter((r) => r.status === 'MISSING_OPTIONS').length,
      cleanCount: reports.filter((r) => r.status === 'CLEAN').length,
      verifiedCount: reports.filter((r) => r.isVerified).length,
      neutralizedCount: reports.filter((r) => r.isNeutralized).length,
    };
  },
};
