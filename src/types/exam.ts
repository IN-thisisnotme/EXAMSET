export type CorrectOption = 'A' | 'B' | 'C' | 'D';

export type QuestionType =
  | 'Direct Fact'
  | 'Statement-Based'
  | 'Multiple Statements + Combination'
  | 'Assertion–Reason'
  | 'Match the Following'
  | 'Pair-Based'
  | 'Multiple Correct Statements'
  | 'Incorrect / Not Correct'
  | 'Correctly Matched'
  | 'Chronological Order'
  | 'Ascending/Descending Order'
  | 'Sequence/Process'
  | 'Cause–Effect'
  | 'Conceptual/Application'
  | 'Case-Based'
  | 'Numerical/Calculation'
  | 'Data Interpretation'
  | 'Map/Geography-Based'
  | 'Image/Diagram-Based'
  | 'Pair of Statements'
  | 'Three/Four-Statement Trap'
  | 'Elimination-Based'
  | 'Negative-Logic Question'
  | 'Quantitative Comparison'
  | 'Legal/Constitutional Application'
  | 'Policy/Institution-Based'
  | 'Term–Definition'
  | 'Example-Based'
  | 'Classification'
  | 'Odd-One-Out';

export interface QuestionOptions {
  a: string;
  b: string;
  c: string;
  d: string;
}

export interface QuestionItem {
  id: string;
  num: number;
  question: string;
  options: QuestionOptions;
  correctAnswer: CorrectOption | null;
  explanation: string;
  questionType?: QuestionType;
}

export interface PaperMetadata {
  id: string;
  title: string;
  subject: string;
  topic: string;
  setNumber: string;
  targetExam: string;
  createdAt: number;
  folderId?: string;
}

export interface QuestionPaper {
  metadata: PaperMetadata;
  questions: QuestionItem[];
}

export type PrintMode = 
  | 'STUDENT_PAPER' 
  | 'MASTER_BOOKLET' 
  | 'OMR_ONLY' 
  | 'SOLUTIONS_ONLY';

export interface ExamConfig {
  institutionName: string;
  examTitle: string;
  seriesCode: string;
  timeAllowed: string;
  maxMarks: string;
  printMode: PrintMode;
  includeCandidateDetails: boolean;
  includeInstructions: boolean;
  includeOmrSheet: boolean;
  twoColumns: boolean;
  customInstructions: string[];
}

export interface ParseResult {
  paper: QuestionPaper;
  rawCount: number;
  hasAnswerKeys: boolean;
  hasExplanations: boolean;
  warnings: string[];
}

export const DEFAULT_EXAM_CONFIG: ExamConfig = {
  institutionName: 'CENTRAL BOARD OF SECONDARY EDUCATION',
  examTitle: 'ALL INDIA COMPETITIVE EXAMINATION',
  seriesCode: 'SET - A',
  timeAllowed: '120 Minutes',
  maxMarks: '100',
  printMode: 'STUDENT_PAPER',
  includeCandidateDetails: true,
  includeInstructions: true,
  includeOmrSheet: true,
  twoColumns: true,
  customInstructions: [
    'Use only blue or black ballpoint pen to fill bubbles on the OMR sheet.',
    'Each question carries 1 mark. Darken only one circle for each question completely.',
    'Rough work must be done only in the designated space provided at the end of this booklet.',
    'Ensure your Test Series Code is correctly darkened before submission.'
  ]
};
