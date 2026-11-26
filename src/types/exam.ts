export type CorrectOption = 'A' | 'B' | 'C' | 'D' | 'E';

export type QuestionType =
  | 'conceptual'
  | 'factual'
  | 'numerical'
  | 'assertion-reason'
  | 'statement'
  | 'match-the-following'
  | 'chronological'
  | 'pair-based'
  | 'multiple-select';

export type PaperCategory = 'FULL_MOCK' | 'SUBJECT_TEST' | 'TOPIC_DRILL' | 'PYQ' | 'REVISION';

export type MsqScoringPolicy = 'ALL_OR_NOTHING' | 'PARTIAL_STRICT';

export type OmrBubblingMode = 'BLANK_CANDIDATE' | 'PRE_PRINTED_DIGITS' | 'PRE_DARKENED_BUBBLES';

export type ExamTimerMode = 'COUNTDOWN' | 'OPEN_STOPWATCH';

export type ExamInterfaceMode = 'TCS_ION_CBT' | 'SCROLLABLE_PAPER';

export interface QuestionOptions {
  a: string;
  b: string;
  c: string;
  d: string;
  e?: string;
}

export interface QuestionItem {
  id: string;
  num: number;
  question: string;
  questionType?: QuestionType;
  options: QuestionOptions;
  /** Single key ('A'..'E') or canonical MSQ keys ('A,C') */
  correctAnswer: string | null;
  explanation: string | null;
  diagramUrl?: string | null;
  diagramLabel?: string | null;
  formula?: string | null;
  isNeutralized?: boolean;
  isVerified?: boolean;
  tags?: string[];
}

export interface PaperMetadata {
  id: string;
  title: string;
  subject: string;
  topic: string;
  setNumber: string;
  targetExam: string;
  rollNumber?: string;
  bookletNumber?: string;
  durationMinutes?: number;
  positiveMarks?: number;
  negativeMarks?: number;
  unattendedMarks?: number;
  msqScoringPolicy?: MsqScoringPolicy;
  manualCategory?: PaperCategory;
  isCompleted?: boolean;
  completedAt?: number | null;
  createdAt: number;
  updatedAt?: number;
  folderId?: string;
}

export interface QuestionPaper {
  metadata: PaperMetadata;
  questions: QuestionItem[];
}

export type PrintMode = 'paper_and_omr' | 'paper_only' | 'omr_only' | 'master_booklet' | 'solutions_only';

export interface ExamConfig {
  examTitle: string;
  institutionName: string;
  subject: string;
  topic: string;
  seriesCode: string;
  rollNumber: string;
  bookletNumber: string;
  timeAllowed: string;
  durationMinutes: number;
  maxMarks: string;
  positiveMarks: number;
  negativeMarks: number;
  unattendedMarks: number;
  msqScoringPolicy: MsqScoringPolicy;
  omrBubblingMode: OmrBubblingMode;
  showWatermark: boolean;
  twoColumns: boolean;
  includeCandidateDetails: boolean;
  includeInstructions: boolean;
  customInstructions: string[];
  printMode: PrintMode;
  omrOptionCount: 4 | 5;
}

export const DEFAULT_EXAM_CONFIG: ExamConfig = {
  examTitle: 'OBJECTIVE EXAMINATION PAPER',
  institutionName: 'EXAMINATION BOARD',
  subject: 'General Studies & Sciences',
  topic: 'Comprehensive Mock',
  seriesCode: '01',
  rollNumber: '0000000001',
  bookletNumber: '0000001',
  timeAllowed: '120 Minutes',
  durationMinutes: 120,
  maxMarks: '200 Marks',
  positiveMarks: 2.0,
  negativeMarks: 0.66,
  unattendedMarks: 0.0,
  msqScoringPolicy: 'ALL_OR_NOTHING',
  omrBubblingMode: 'PRE_PRINTED_DIGITS',
  showWatermark: false,
  twoColumns: true,
  includeCandidateDetails: true,
  includeInstructions: true,
  customInstructions: [
    'Use ONLY Blue or Black Ball Point Pen to darken the circles on the OMR Sheet.',
    'Check that the Test Booklet Code & Roll Number match your OMR Response Sheet.',
    'Darken ONLY ONE circle per single-choice question. For MSQ items, mark all applicable bubbles.',
    'Do not make any stray marks or fold the Optical Mark Reader (OMR) timing tracks.',
    'Rough work must be done only in the blank space provided in the Test Booklet.'
  ],
  printMode: 'paper_and_omr',
  omrOptionCount: 4
};

export interface ParseResult {
  paper: QuestionPaper;
  rawCount: number;
  hasAnswerKeys: boolean;
  hasExplanations: boolean;
  detectedMsqCount?: number;
  detectedOptionECount?: number;
  warnings: string[];
}

export interface FolderItem {
  id: string;
  name: string;
  color: string;
  parentId?: string | null;
  createdAt: number;
}

export interface AttemptRecord {
  id: string;
  paperId: string;
  paperTitle: string;
  subject: string;
  rollNumber: string;
  bookletNumber: string;
  bookletCode: string;
  score: number;
  maxScore: number;
  correctCount: number;
  partialCount: number;
  incorrectCount: number;
  unansweredCount: number;
  accuracyPercent: number;
  timeSpentSeconds: number;
  positiveMarks: number;
  negativeMarks: number;
  unattendedMarks: number;
  msqScoringPolicy: MsqScoringPolicy;
  submittedAnswers: Record<number, string>;
  questionTimes: Record<number, number>;
  submittedAt: number;
}

export interface ActiveExamSession {
  paperId: string;
  interfaceMode: ExamInterfaceMode;
  timerMode: ExamTimerMode;
  shuffleOptions: boolean;
  shuffleSeed: number;
  currentIndex: number;
  answers: Record<number, string>;
  markedForReview: Record<number, boolean>;
  visited: Record<number, boolean>;
  questionTimes: Record<number, number>;
  timeLeftSeconds: number;
  elapsedSeconds: number;
  targetDurationSeconds: number;
  startedAt: number;
}

export interface ExamCountdownTarget {
  id: string;
  title: string;
  targetDateIso: string; // YYYY-MM-DD
  examType: string;
  isPinned: boolean;
  color: string;
  targetTime?: string; // HH:mm (24h), optional for backward compatibility
  timezoneMode?: 'LOCAL' | 'GMT';
  targetUtcMillis?: number;
}

export type AuditPhase = 'PHASE_1_KEYS' | 'PHASE_2_REPLACEMENTS' | 'PHASE_3_EXPLANATIONS';

export type AuditIssueType =
  | 'WRONG_KEY'
  | 'MISSING_KEY'
  | 'FLAWED_QUESTION'
  | 'VAGUE_OPTIONS'
  | 'MISSING_OPTIONS'
  | 'MISSING_EXPLANATION'
  | 'CLEAN';

export interface AuditQuestionReport {
  qNum: number;
  status: AuditIssueType;
  currentKey?: string | null;
  suggestedKey?: string | null;
  flawReason?: string | null;
  recheckedExplanation?: string | null;
  replacementQuestion?: QuestionItem | null;
  originalQuestion?: QuestionItem | null;
  isResolved: boolean;
  isVerified: boolean;
  isNeutralized: boolean;
}

export interface AuditSummaryMetrics {
  totalAudited: number;
  wrongKeysCount: number;
  missingKeysCount: number;
  flawedQuestionsCount: number;
  missingOptionsCount: number;
  cleanCount: number;
  verifiedCount: number;
  neutralizedCount: number;
}

export type ValidationSeverity = 'ERROR' | 'WARNING' | 'INFO';

export interface ValidationIssue {
  severity: ValidationSeverity;
  message: string;
  questionNumber?: number;
}

export type BatchItemStatus = 'VALID' | 'WARNING' | 'INVALID';

export interface BatchImportItem {
  id: string;
  fileName: string;
  fileSizeBytes: number;
  fileSizeFormatted: string;
  paper: QuestionPaper | null;
  status: BatchItemStatus;
  issues: ValidationIssue[];
  isSelected: boolean;
  customSubject?: string;
  rawJsonPreview: string;
}

export interface BatchValidationSummary {
  totalFiles: number;
  totalPapers: number;
  validCount: number;
  warningCount: number;
  invalidCount: number;
  totalQuestions: number;
}
