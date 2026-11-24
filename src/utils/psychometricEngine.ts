import {
  AttemptRecord,
  MsqScoringPolicy,
  QuestionItem,
  QuestionPaper,
} from '../types/exam';
import { AnswerKeyMatcher, QuestionGradeStatus } from './answerKeyMatcher';

export interface ItemEvaluation {
  questionNum: number;
  difficultyP: number;
  difficultyLabel: string;
  discriminationD: number;
  discriminationLabel: string;
  pointBiserialR: number;
  pointBiserialLabel: string;
  totalAttempts: number;
  correctCount: number;
  unattemptedCount: number;
  optionFrequencies: Record<string, number>;
  nonFunctioningDistractors: string[];
  isFlagged: boolean;
  flagReason: string | null;
}

export interface CandidateScorecard {
  rank: number;
  rollNumber: string;
  bookletNumber: string;
  bookletCode: string;
  totalScore: number;
  correctCount: number;
  partialCount: number;
  incorrectCount: number;
  unansweredCount: number;
  accuracyPercent: number;
  submittedAt: number;
}

export interface CandidateSubmission {
  rollNumber: string;
  bookletNumber: string;
  bookletCode: string;
  answers: Record<number, string>;
  submittedAt: number;
  precomputedScore?: number;
}

export interface CohortPsychometricReport {
  isSyntheticDemo: boolean;
  candidateCount: number;
  questionCount: number;
  meanScore: number;
  medianScore: number;
  highestScore: number;
  lowestScore: number;
  standardDeviation: number;
  cronbachAlpha: number;
  items: ItemEvaluation[];
  flaggedItemCount: number;
  candidates: CandidateScorecard[];
  hasOptionE: boolean;
}

export type PacingQuadrant =
  | 'FAST_ACCURATE'
  | 'SLOW_ACCURATE'
  | 'FAST_WRONG'
  | 'SLOW_WRONG'
  | 'SKIPPED';

export interface QuestionPacingItem {
  qNum: number;
  questionType: string;
  timeSeconds: number;
  status: QuestionGradeStatus;
  marks: number;
  quadrant: PacingQuadrant;
}

export interface TypeBreakdownRow {
  typeLabel: string;
  total: number;
  correct: number;
  incorrect: number;
  unanswered: number;
  accuracyPercent: number;
  avgTimeSeconds: number;
}

export interface PacingAnalysisSummary {
  totalTrackedSeconds: number;
  avgTimePerQuestion: number;
  avgTimeCorrect: number;
  avgTimeIncorrect: number;
  avgTimeSkipped: number;
  slowestQuestions: QuestionPacingItem[];
  fastestCorrect: QuestionPacingItem[];
  quadrantCounts: Record<PacingQuadrant, number>;
  typeBreakdown: TypeBreakdownRow[];
  items: QuestionPacingItem[];
}

function getDifficultyLabel(p: number): string {
  if (p >= 0.8) return 'Easy';
  if (p >= 0.4) return 'Moderate';
  if (p >= 0.2) return 'Difficult';
  return 'Hard / Outlier';
}

function getDiscriminationLabel(d: number): string {
  if (d >= 0.4) return 'Excellent';
  if (d >= 0.3) return 'Good';
  if (d >= 0.2) return 'Acceptable';
  if (d >= 0) return 'Marginal';
  return 'Defective / Negative';
}

function getPointBiserialLabel(r: number): string {
  if (r >= 0.35) return 'Excellent (r_pb >= 0.35)';
  if (r >= 0.25) return 'Good (r_pb >= 0.25)';
  if (r >= 0.15) return 'Fair (r_pb >= 0.15)';
  if (r >= 0) return 'Marginal (r_pb >= 0.00)';
  return 'Negative (Check Key!)';
}

function seededRandom(seedNum: number): () => number {
  let state = Math.abs(seedNum) || 1234567;
  return () => {
    state = (state * 1664525 + 1013904223) % 4294967296;
    return state / 4294967296;
  };
}

function hashTitle(s: string): number {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return Math.abs(h);
}

export const PsychometricEngine = {
  submissionsFromAttemptRecords(attempts: AttemptRecord[]): CandidateSubmission[] {
    return attempts.map((att) => ({
      rollNumber: att.rollNumber || '0000000001',
      bookletNumber: att.bookletNumber || '0000001',
      bookletCode: att.bookletCode || '01',
      answers: att.submittedAnswers || {},
      submittedAt: att.submittedAt,
      precomputedScore: att.score,
    }));
  },

  evaluateCohort(
    submissions: CandidateSubmission[],
    paper: QuestionPaper,
    isSyntheticDemo: boolean = false
  ): CohortPsychometricReport {
    const n = submissions.length;
    const k = paper.questions.length;
    const hasE = paper.questions.some(
      (q) => Boolean(q.options.e?.trim()) || AnswerKeyMatcher.containsOption(q.correctAnswer, 'E')
    );
    const optionChars = hasE ? ['A', 'B', 'C', 'D', 'E'] : ['A', 'B', 'C', 'D'];

    if (n === 0 || k === 0) {
      const emptyItems: ItemEvaluation[] = paper.questions.map((q) => {
        const freqs: Record<string, number> = {};
        optionChars.forEach((c) => (freqs[c] = 0));
        return {
          questionNum: q.num,
          difficultyP: 0,
          difficultyLabel: 'N/A',
          discriminationD: 0,
          discriminationLabel: 'N/A',
          pointBiserialR: 0,
          pointBiserialLabel: 'N/A',
          totalAttempts: 0,
          correctCount: 0,
          unattemptedCount: 0,
          optionFrequencies: freqs,
          nonFunctioningDistractors: [],
          isFlagged: false,
          flagReason: 'Awaiting Submissions',
        };
      });
      return {
        isSyntheticDemo,
        candidateCount: 0,
        questionCount: k,
        meanScore: 0,
        medianScore: 0,
        highestScore: 0,
        lowestScore: 0,
        standardDeviation: 0,
        cronbachAlpha: 0,
        items: emptyItems,
        flaggedItemCount: 0,
        candidates: [],
        hasOptionE: hasE,
      };
    }

    const posMarks = (paper.metadata.positiveMarks ?? 0) > 0 ? paper.metadata.positiveMarks! : 1.0;
    const negMarks = (paper.metadata.negativeMarks ?? 0) >= 0 ? paper.metadata.negativeMarks! : 0.0;
    const unkMarks = (paper.metadata.unattendedMarks ?? 0) >= 0 ? paper.metadata.unattendedMarks! : 0.0;
    const msqPolicy: MsqScoringPolicy = paper.metadata.msqScoringPolicy || 'ALL_OR_NOTHING';

    interface CandidateEval {
      sub: CandidateSubmission;
      score: number;
      correct: number;
      partial: number;
      incorrect: number;
      unanswered: number;
    }

    const candidateEvals: CandidateEval[] = submissions.map((sub) => {
      let correct = 0;
      let partial = 0;
      let incorrect = 0;
      let unanswered = 0;
      let netScoreSum = 0;

      for (const q of paper.questions) {
        const rawAns = sub.answers[q.num];
        if (!rawAns || !rawAns.trim() || !q.correctAnswer) {
          if (q.isNeutralized) {
            correct++;
            netScoreSum += posMarks;
          } else {
            unanswered++;
            netScoreSum -= unkMarks;
          }
        } else {
          const res = AnswerKeyMatcher.scoreQuestion(
            q,
            rawAns,
            posMarks,
            negMarks,
            unkMarks,
            msqPolicy
          );
          if (res.status === 'CORRECT' || res.status === 'NEUTRALIZED') correct++;
          else if (res.status === 'PARTIAL') partial++;
          else if (res.status === 'INCORRECT') incorrect++;
          else unanswered++;
          netScoreSum += res.marks;
        }
      }

      const score =
        sub.precomputedScore !== undefined
          ? sub.precomputedScore
          : Math.round(netScoreSum * 100) / 100;

      return { sub, score, correct, partial, incorrect, unanswered };
    });

    const rawScores = candidateEvals.map((c) => c.score);
    const mean = rawScores.reduce((a, b) => a + b, 0) / n;
    const sortedByScore = [...candidateEvals].sort((a, b) => b.score - a.score);
    const sortedScores = [...rawScores].sort((a, b) => a - b);
    const median =
      n % 2 === 1
        ? sortedScores[Math.floor(n / 2)]
        : (sortedScores[n / 2 - 1] + sortedScores[n / 2]) / 2;

    const maxScore = sortedScores[sortedScores.length - 1];
    const minScore = sortedScores[0];
    const variance =
      rawScores.reduce((acc, s) => acc + (s - mean) * (s - mean), 0) / n;
    const stdDev = Math.sqrt(Math.max(variance, 1e-4));

    const cohort27Size = n >= 4 ? Math.max(1, Math.floor(n * 0.27)) : 1;
    const upperCohort = sortedByScore.slice(0, cohort27Size).map((c) => c.sub);
    const lowerCohort = n > 1 ? sortedByScore.slice(n - cohort27Size).map((c) => c.sub) : [];

    let sumItemVariances = 0;
    const items: ItemEvaluation[] = [];

    for (const q of paper.questions) {
      const expectedLetters = AnswerKeyMatcher.parseSelectedLetters(q.correctAnswer);
      const expectedCanonical = expectedLetters.join(',');
      let correctCount = 0;
      let unattemptedCount = 0;
      const optionFreqs: Record<string, number> = {};
      optionChars.forEach((c) => (optionFreqs[c] = 0));
      const scoresOfCorrect: number[] = [];

      for (const cEval of candidateEvals) {
        const rawAns = cEval.sub.answers[q.num];
        const selected = AnswerKeyMatcher.parseSelectedLetters(rawAns);
        if (selected.length > 0) {
          for (const letter of selected) {
            optionFreqs[letter] = (optionFreqs[letter] ?? 0) + 1;
          }
          if (expectedCanonical && selected.join(',') === expectedCanonical) {
            correctCount++;
            scoresOfCorrect.push(cEval.score);
          }
        } else {
          unattemptedCount++;
        }
      }

      const pValue = correctCount / n;
      sumItemVariances += pValue * (1 - pValue);

      let dIndex = 0;
      if (n >= 2 && expectedCanonical && upperCohort.length > 0 && lowerCohort.length > 0) {
        const upperCorrect = upperCohort.filter(
          (sub) => AnswerKeyMatcher.normalizeKey(sub.answers[q.num]) === expectedCanonical
        ).length;
        const lowerCorrect = lowerCohort.filter(
          (sub) => AnswerKeyMatcher.normalizeKey(sub.answers[q.num]) === expectedCanonical
        ).length;
        const rawD = upperCorrect / upperCohort.length - lowerCorrect / lowerCohort.length;
        dIndex = Math.max(-1, Math.min(1, rawD));
      }

      let rPb = 0;
      if (n >= 3 && correctCount >= 1 && correctCount < n && stdDev > 1e-4) {
        const meanCorrect =
          scoresOfCorrect.reduce((a, b) => a + b, 0) / scoresOfCorrect.length;
        const pqRatio = Math.sqrt(pValue / (1 - pValue));
        const rawR = ((meanCorrect - mean) / stdDev) * pqRatio;
        rPb = Math.max(-1, Math.min(1, rawR));
      }

      const nonFunctioning: string[] = [];
      for (const opt of optionChars) {
        if (!expectedLetters.includes(opt)) {
          const pct = (optionFreqs[opt] ?? 0) / n;
          if (pct < 0.05) {
            nonFunctioning.push(opt);
          }
        }
      }

      let isFlagged = false;
      let flagReason: string | null = null;
      if (n >= 4 && (rPb < 0 || dIndex < 0)) {
        isFlagged = true;
        flagReason = `Negative discrimination (r_pb=${rPb.toFixed(2)}, D=${dIndex.toFixed(2)}): High scorers missed more than low scorers. Verify answer key.`;
      } else if (pValue < 0.15) {
        isFlagged = true;
        flagReason = `Extremely high difficulty (p=${pValue.toFixed(2)}): Less than 15% pass rate.`;
      } else if (n >= 20 && nonFunctioning.length >= 2) {
        isFlagged = true;
        flagReason = `Weak distractors: Options ${nonFunctioning.join(', ')} chosen by <5% of cohort.`;
      } else if (n >= 10 && dIndex < 0.12 && pValue >= 0.3 && pValue <= 0.7) {
        isFlagged = true;
        flagReason = `Marginal discrimination (D=${dIndex.toFixed(2)}): Fails to differentiate ability.`;
      }

      items.push({
        questionNum: q.num,
        difficultyP: pValue,
        difficultyLabel: getDifficultyLabel(pValue),
        discriminationD: dIndex,
        discriminationLabel: getDiscriminationLabel(dIndex),
        pointBiserialR: rPb,
        pointBiserialLabel: getPointBiserialLabel(rPb),
        totalAttempts: n - unattemptedCount,
        correctCount,
        unattemptedCount,
        optionFrequencies: optionFreqs,
        nonFunctioningDistractors: nonFunctioning,
        isFlagged,
        flagReason,
      });
    }

    const alpha =
      k > 1 && variance > 1e-4
        ? Math.max(0, Math.min(0.99, (k / (k - 1)) * (1 - sumItemVariances / variance)))
        : 0.85;

    const candidates: CandidateScorecard[] = sortedByScore.map((cEval, idx) => {
      const att = cEval.correct + cEval.partial + cEval.incorrect;
      const acc = att > 0 ? Math.round((cEval.correct / att) * 100) : 0;
      return {
        rank: idx + 1,
        rollNumber: cEval.sub.rollNumber,
        bookletNumber: cEval.sub.bookletNumber,
        bookletCode: cEval.sub.bookletCode,
        totalScore: cEval.score,
        correctCount: cEval.correct,
        partialCount: cEval.partial,
        incorrectCount: cEval.incorrect,
        unansweredCount: cEval.unanswered,
        accuracyPercent: acc,
        submittedAt: cEval.sub.submittedAt,
      };
    });

    return {
      isSyntheticDemo,
      candidateCount: n,
      questionCount: k,
      meanScore: mean,
      medianScore: median,
      highestScore: maxScore,
      lowestScore: minScore,
      standardDeviation: stdDev,
      cronbachAlpha: alpha,
      items,
      flaggedItemCount: items.filter((i) => i.isFlagged).length,
      candidates,
      hasOptionE: hasE,
    };
  },

  /**
   * Generates a clearly labeled synthetic cohort ONLY for explicit demo preview.
   */
  generateSimulatedCohortSubmissions(
    paper: QuestionPaper,
    candidateCount: number = 40
  ): CandidateSubmission[] {
    const rand = seededRandom(hashTitle(paper.metadata.title || 'ExamSet'));
    const sets = ['01', '02', '03', '04'];
    const now = Date.now();

    return Array.from({ length: candidateCount }, (_, idx) => {
      const i = idx + 1;
      // Box-Muller approximate standard normal clamped [-2, 2]
      const u1 = Math.max(1e-6, rand());
      const u2 = rand();
      const gaussian = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
      const ability = Math.max(-2, Math.min(2, gaussian * 0.85));

      const answers: Record<number, string> = {};
      for (const q of paper.questions) {
        const expected = AnswerKeyMatcher.parseSelectedLetters(q.correctAnswer);
        const expectedSet = expected.length > 0 ? expected : ['A'];
        const itemDiff = (((q.question.length || 40) % 7) - 3) * 0.3;
        const probCorrect = 1 / (1 + Math.exp(-(ability - itemDiff)));
        const isMsq = AnswerKeyMatcher.isMultiSelect(q) && expectedSet.length >= 2;

        const qOpts = ['A', 'B', 'C', 'D'];
        if (q.options.e?.trim()) qOpts.push('E');

        if (rand() < 0.06) {
          continue; // skipped
        } else if (rand() < probCorrect) {
          answers[q.num] = expectedSet.join(',');
        } else if (isMsq) {
          if (rand() < 0.45 && expectedSet.length >= 2) {
            answers[q.num] = expectedSet.slice(0, expectedSet.length - 1).join(',');
          } else {
            const distractors = qOpts.filter((o) => !expectedSet.includes(o));
            const wrong = distractors[Math.floor(rand() * distractors.length)] || 'B';
            answers[q.num] = Array.from(new Set([expectedSet[0], wrong])).sort().join(',');
          }
        } else {
          const distractors = qOpts.filter((o) => !expectedSet.includes(o));
          answers[q.num] = distractors[Math.floor(rand() * distractors.length)] || 'B';
        }
      }

      return {
        rollNumber: `202699${String(i).padStart(4, '0')}`,
        bookletNumber: `799${String(i).padStart(4, '0')}`,
        bookletCode: sets[idx % sets.length],
        answers,
        submittedAt: now - (candidateCount - i) * 60_000,
      };
    });
  },

  formatPsychometricReportCsv(report: CohortPsychometricReport, examTitle: string): string {
    const esc = (v: string) => `"${String(v || '').replace(/"/g, '""')}"`;
    const lines: string[] = [
      '# Psychometric Item Analysis Report (Classical Test Theory)',
      `# Exam: ${esc(examTitle)}`,
      `# Source: ${report.isSyntheticDemo ? 'Synthetic Demo Cohort' : 'Saved Candidate Attempts'}`,
      `# Total Candidates: ${report.candidateCount} | Total Questions: ${report.questionCount}`,
      `# Mean Score: ${report.meanScore.toFixed(2)} | Std Dev: ${report.standardDeviation.toFixed(2)} | Cronbach Alpha: ${report.cronbachAlpha.toFixed(3)}`,
      '',
      report.hasOptionE
        ? 'Question_Num,Difficulty_P,Difficulty_Label,Discrimination_D,Point_Biserial_r_pb,Point_Biserial_Label,Correct_Count,Unattempted_Count,Opt_A_Count,Opt_B_Count,Opt_C_Count,Opt_D_Count,Opt_E_Count,Non_Functioning_Distractors,Flagged,Flag_Reason'
        : 'Question_Num,Difficulty_P,Difficulty_Label,Discrimination_D,Point_Biserial_r_pb,Point_Biserial_Label,Correct_Count,Unattempted_Count,Opt_A_Count,Opt_B_Count,Opt_C_Count,Opt_D_Count,Non_Functioning_Distractors,Flagged,Flag_Reason',
    ];

    for (const item of report.items) {
      const cols = [
        String(item.questionNum),
        item.difficultyP.toFixed(3),
        esc(item.difficultyLabel),
        item.discriminationD.toFixed(3),
        item.pointBiserialR.toFixed(3),
        esc(item.pointBiserialLabel),
        String(item.correctCount),
        String(item.unattemptedCount),
        String(item.optionFrequencies['A'] ?? 0),
        String(item.optionFrequencies['B'] ?? 0),
        String(item.optionFrequencies['C'] ?? 0),
        String(item.optionFrequencies['D'] ?? 0),
      ];
      if (report.hasOptionE) {
        cols.push(String(item.optionFrequencies['E'] ?? 0));
      }
      cols.push(
        esc(
          item.nonFunctioningDistractors.length === 0
            ? 'None'
            : item.nonFunctioningDistractors.join(';')
        ),
        esc(item.isFlagged ? 'YES' : 'NO'),
        esc(item.flagReason || '')
      );
      lines.push(cols.join(','));
    }

    return lines.join('\n');
  },

  formatCandidateScorecardsCsv(report: CohortPsychometricReport, examTitle: string): string {
    const esc = (v: string) => `"${String(v || '').replace(/"/g, '""')}"`;
    const lines: string[] = [
      '# Student Candidate Examination Scorecards & Ranking',
      `# Exam: ${esc(examTitle)}`,
      `# Total Evaluated: ${report.candidates.length}`,
      '',
      'Rank,Roll_Number,Booklet_Number,Set_Code,Total_Score,Correct_Count,Partial_Count,Incorrect_Count,Unanswered_Count,Accuracy_Percent,Submission_Timestamp',
    ];
    for (const c of report.candidates) {
      lines.push(
        [
          String(c.rank),
          c.rollNumber,
          c.bookletNumber,
          c.bookletCode,
          c.totalScore.toFixed(2),
          String(c.correctCount),
          String(c.partialCount),
          String(c.incorrectCount),
          String(c.unansweredCount),
          `${c.accuracyPercent}%`,
          esc(new Date(c.submittedAt).toISOString()),
        ].join(',')
      );
    }
    return lines.join('\n');
  },

  generateStudentReportCard(
    candidate: CandidateScorecard,
    paper: QuestionPaper,
    answers: Record<number, string> = {},
    totalCohortSize: number = 1
  ): string {
    const posMarks = (paper.metadata.positiveMarks ?? 0) > 0 ? paper.metadata.positiveMarks! : 1.0;
    const negMarks = (paper.metadata.negativeMarks ?? 0) >= 0 ? paper.metadata.negativeMarks! : 0.0;
    const unkMarks = (paper.metadata.unattendedMarks ?? 0) >= 0 ? paper.metadata.unattendedMarks! : 0.0;
    const msqPolicy = paper.metadata.msqScoringPolicy || 'ALL_OR_NOTHING';
    const maxTotal = (paper.questions.length * posMarks).toFixed(2);
    const percentile =
      totalCohortSize > 1
        ? `${(((totalCohortSize - candidate.rank + 1) / totalCohortSize) * 100).toFixed(1)}th Percentile`
        : 'Individual Assessment';

    const lines: string[] = [
      '================================================================================',
      '                       OFFICIAL STUDENT EXAMINATION REPORT CARD                 ',
      '================================================================================',
      '',
      `Examination Title : ${paper.metadata.title}`,
      `Subject / Topic   : ${paper.metadata.subject}${paper.metadata.topic ? ` (${paper.metadata.topic})` : ''}`,
      `Set Code          : ${candidate.bookletCode || paper.metadata.setNumber}`,
      `Roll No / Booklet : ${candidate.rollNumber} / ${candidate.bookletNumber}`,
      `Evaluated At      : ${new Date(candidate.submittedAt).toLocaleString()}`,
      '',
      `Overall Score     : ${candidate.totalScore.toFixed(2)} / ${maxTotal} marks`,
      `Rank / Percentile : #${candidate.rank} of ${totalCohortSize} (${percentile})`,
      `Accuracy Rate     : ${candidate.accuracyPercent}%`,
      `Correct / Partial / Wrong / Skip : ${candidate.correctCount} / ${candidate.partialCount} / ${candidate.incorrectCount} / ${candidate.unansweredCount}`,
      '',
      '--------------------------------------------------------------------------------',
      'Q#   Marked         Official Key   Marks     Status',
      '--------------------------------------------------------------------------------',
    ];

    for (const q of paper.questions) {
      const rawAns = answers[q.num];
      const res = AnswerKeyMatcher.scoreQuestion(
        q,
        rawAns,
        posMarks,
        negMarks,
        unkMarks,
        msqPolicy
      );
      const chosen = AnswerKeyMatcher.formatKeyForDisplay(rawAns);
      const key = AnswerKeyMatcher.formatKeyForDisplay(q.correctAnswer);
      const pts = res.marks > 0 ? `+${res.marks.toFixed(2)}` : res.marks.toFixed(2);
      lines.push(
        `${String(q.num).padStart(3, '0')}  ${chosen.padEnd(13)}  ${key.padEnd(13)}  ${pts.padEnd(8)}  ${res.status}`
      );
    }
    lines.push('================================================================================');
    return lines.join('\n');
  },
};

export const QuestionPacingAnalyzer = {
  analyzePacing(
    paper: QuestionPaper,
    questionTimes: Record<number, number>,
    answers: Record<number, string>,
    positiveMarks: number,
    negativeMarks: number,
    unattendedMarks: number,
    msqPolicy: MsqScoringPolicy
  ): PacingAnalysisSummary {
    const items: QuestionPacingItem[] = [];
    const correctTimes: number[] = [];
    const wrongTimes: number[] = [];
    const skippedTimes: number[] = [];
    const quadrantCounts: Record<PacingQuadrant, number> = {
      FAST_ACCURATE: 0,
      SLOW_ACCURATE: 0,
      FAST_WRONG: 0,
      SLOW_WRONG: 0,
      SKIPPED: 0,
    };

    const rawAvg =
      paper.questions.length > 0
        ? paper.questions.reduce((acc, q) => acc + (questionTimes[q.num] ?? 0), 0) /
          paper.questions.length
        : 0;
    const thresholdSec = Math.max(20, rawAvg);

    const typeMap = new Map<
      string,
      { total: number; correct: number; incorrect: number; unanswered: number; timeSum: number }
    >();

    for (const q of paper.questions) {
      const t = questionTimes[q.num] ?? 0;
      const res = AnswerKeyMatcher.scoreQuestion(
        q,
        answers[q.num],
        positiveMarks,
        negativeMarks,
        unattendedMarks,
        msqPolicy
      );

      let quadrant: PacingQuadrant = 'SKIPPED';
      if (res.status === 'UNANSWERED') {
        quadrant = 'SKIPPED';
        skippedTimes.push(t);
      } else if (res.status === 'CORRECT' || res.status === 'NEUTRALIZED' || res.status === 'PARTIAL') {
        quadrant = t <= thresholdSec ? 'FAST_ACCURATE' : 'SLOW_ACCURATE';
        correctTimes.push(t);
      } else {
        quadrant = t <= thresholdSec ? 'FAST_WRONG' : 'SLOW_WRONG';
        wrongTimes.push(t);
      }
      quadrantCounts[quadrant]++;

      const typeLabel = (q.questionType || 'factual').replace(/-/g, ' ');
      const bucket = typeMap.get(typeLabel) || {
        total: 0,
        correct: 0,
        incorrect: 0,
        unanswered: 0,
        timeSum: 0,
      };
      bucket.total++;
      bucket.timeSum += t;
      if (res.status === 'CORRECT' || res.status === 'NEUTRALIZED') bucket.correct++;
      else if (res.status === 'INCORRECT') bucket.incorrect++;
      else bucket.unanswered++;
      typeMap.set(typeLabel, bucket);

      items.push({
        qNum: q.num,
        questionType: typeLabel,
        timeSeconds: t,
        status: res.status,
        marks: res.marks,
        quadrant,
      });
    }

    const totalTrackedSeconds = items.reduce((a, b) => a + b.timeSeconds, 0);
    const avg = (arr: number[]) =>
      arr.length > 0 ? Math.round(arr.reduce((a, b) => a + b, 0) / arr.length) : 0;

    const slowestQuestions = [...items]
      .filter((i) => i.timeSeconds > 0)
      .sort((a, b) => b.timeSeconds - a.timeSeconds)
      .slice(0, 5);

    const fastestCorrect = [...items]
      .filter((i) => i.status === 'CORRECT' && i.timeSeconds > 0)
      .sort((a, b) => a.timeSeconds - b.timeSeconds)
      .slice(0, 5);

    const typeBreakdown: TypeBreakdownRow[] = Array.from(typeMap.entries()).map(([typeLabel, b]) => {
      const att = b.correct + b.incorrect;
      return {
        typeLabel,
        total: b.total,
        correct: b.correct,
        incorrect: b.incorrect,
        unanswered: b.unanswered,
        accuracyPercent: att > 0 ? Math.round((b.correct / att) * 100) : 0,
        avgTimeSeconds: b.total > 0 ? Math.round(b.timeSum / b.total) : 0,
      };
    });

    return {
      totalTrackedSeconds,
      avgTimePerQuestion: paper.questions.length > 0 ? Math.round(totalTrackedSeconds / paper.questions.length) : 0,
      avgTimeCorrect: avg(correctTimes),
      avgTimeIncorrect: avg(wrongTimes),
      avgTimeSkipped: avg(skippedTimes),
      slowestQuestions,
      fastestCorrect,
      quadrantCounts,
      typeBreakdown,
      items,
    };
  },
};
