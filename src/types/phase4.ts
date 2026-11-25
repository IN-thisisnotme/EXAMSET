// Phase 4 Domain Types: Knowledge Studio, SM-2 SRS, Notebook Dictation, Vocabulary Hub & Multi-Mode Prompt Studio

export type KnowledgeCollectionType =
  | 'ONE_LINERS'
  | 'MULTI_LINER'
  | 'CONCEPT_CODEX'
  | 'LONG_ANSWER'
  | 'COMPARISON_TABLE'
  | 'FORMULA_SHEET'
  | 'FLASHCARDS'
  | 'WEAK_AREA_REVIEW'
  | 'CUSTOM';

export const KNOWLEDGE_COLLECTION_META: Record<
  KnowledgeCollectionType,
  { code: KnowledgeCollectionType; label: String & string; shortBadge: string; description: string }
> = {
  ONE_LINERS: {
    code: 'ONE_LINERS',
    label: 'One-Liners',
    shortBadge: 'ONE-LINER',
    description: 'Crisp term-to-fact recall lines & numbered takeaways',
  },
  MULTI_LINER: {
    code: 'MULTI_LINER',
    label: 'Multi-Liner Cards',
    shortBadge: 'MULTI-LINER',
    description: 'Rich concept cards with nested sub-points, examples & formulas',
  },
  CONCEPT_CODEX: {
    code: 'CONCEPT_CODEX',
    label: 'Concept Codex',
    shortBadge: 'CODEX',
    description: 'Deep multi-layer topic breakdown for thorough study',
  },
  LONG_ANSWER: {
    code: 'LONG_ANSWER',
    label: 'Long-Form / Subjective Q&A',
    shortBadge: 'LONG Q&A',
    description: 'Multi-paragraph long-form, essay & subjective answers with LaTeX, tables & citations',
  },
  COMPARISON_TABLE: {
    code: 'COMPARISON_TABLE',
    label: 'Comparison Matrix',
    shortBadge: 'MATRIX',
    description: 'Structured side-by-side Markdown comparison tables',
  },
  FORMULA_SHEET: {
    code: 'FORMULA_SHEET',
    label: 'Formula & Law Sheet',
    shortBadge: 'FORMULAS',
    description: 'LaTeX equations, constants, units & quick derivations',
  },
  FLASHCARDS: {
    code: 'FLASHCARDS',
    label: 'Spaced Repetition Flashcards',
    shortBadge: 'FLASHCARDS',
    description: 'Atomic front/back active-recall cards tuned for SM-2 spaced repetition',
  },
  WEAK_AREA_REVIEW: {
    code: 'WEAK_AREA_REVIEW',
    label: 'Mistake & Weak-Area Log',
    shortBadge: 'WEAK AREAS',
    description: 'Compiled from missed or skipped test questions',
  },
  CUSTOM: {
    code: 'CUSTOM',
    label: 'Custom Format',
    shortBadge: 'CUSTOM',
    description: 'User-defined layout and custom AI prompt rules',
  },
};

export type KnowledgeDepth = 'CRISP' | 'BALANCED' | 'DEEP';

export type KnowledgeLayerOrder =
  | 'TERM_FIRST'
  | 'BACKWARDS_ANSWER_FIRST'
  | 'QA_SEPARATE_LINES'
  | 'AUTO_DETECT';

export type KnowledgeTransformAction =
  | 'FIX_BACKWARDS_CHATGPT'
  | 'SWAP_LEAD_DETAIL'
  | 'MERGE_NESTED_BULLETS'
  | 'STRIP_AI_CHATTER'
  | 'SPLIT_SEMICOLONS';

export interface KnowledgeFormatRule {
  collectionType: KnowledgeCollectionType;
  customCollectionLabel: string;
  layerOrder: KnowledgeLayerOrder;
  depth: KnowledgeDepth;
  language: string;
  preserveNestedBullets: boolean;
  includeLatexMath: boolean;
  includeCallouts: boolean;
  includeComparisonTable: boolean;
  customInstruction: string;
}

export const DEFAULT_KNOWLEDGE_RULE: KnowledgeFormatRule = {
  collectionType: 'ONE_LINERS',
  customCollectionLabel: '',
  layerOrder: 'AUTO_DETECT',
  depth: 'BALANCED',
  language: 'English',
  preserveNestedBullets: true,
  includeLatexMath: true,
  includeCallouts: true,
  includeComparisonTable: false,
  customInstruction: '',
};

export type StudyNoteBlock =
  | {
      kind: 'heading';
      blockIndex: number;
      level: number;
      text: string;
    }
  | {
      kind: 'one_liner';
      blockIndex: number;
      displayIndex: string;
      leadTerm: string;
      detail: string;
      hasRecallTarget: boolean;
    }
  | {
      kind: 'multi_liner_card';
      blockIndex: number;
      displayIndex: string;
      title: string;
      badge: string;
      paragraphs: string[];
      nestedBullets: { level: number; text: string }[];
      formula: string;
      isReversed: boolean;
      isQuestionCard: boolean;
      questionType: string;
      statusTag: string;
      candidateAnswer: string;
      optionsList: { label: string; text: string }[];
      correctAnswer: string;
      keyTakeaway: string;
    }
  | {
      kind: 'table';
      blockIndex: number;
      headers: string[];
      rows: string[][];
    }
  | {
      kind: 'callout';
      blockIndex: number;
      label: string;
      body: string;
    }
  | {
      kind: 'paragraph';
      blockIndex: number;
      text: string;
    };

export type SrsRating = 'AGAIN' | 'HARD' | 'GOOD' | 'EASY';

export type FlashcardSrsStatus = 'NEW' | 'LEARNING' | 'DUE' | 'MASTERED';

export interface FlashcardSrsState {
  blockIndex: number;
  repetitions: number;
  easeFactor: number;
  intervalDays: number;
  dueAtMillis: number;
  lastReviewedAtMillis: number;
  lapses: number;
  lastRating?: SrsRating | null;
  reviewCount: number;
  correctCount: number;
}

export interface DeckSrsStats {
  totalCount: number;
  dueCount: number;
  newCount: number;
  learningCount: number;
  masteredCount: number;
  retentionPercent: number;
}

export type FlashcardPromptStyle =
  | 'QA_RETRIEVAL'
  | 'TERM_DEFINITION'
  | 'CLOZE_SCENARIO'
  | 'FORMULA_TRAP';

export interface StudyNoteRecord {
  id: string;
  title: string;
  subject: string;
  sourceAi: string;
  rawContent: string;
  folderId: string;
  collectionType: KnowledgeCollectionType;
  customLabel: string;
  linkedPaperId?: string | null;
  formatRule: KnowledgeFormatRule;
  bookmarkedIndices: number[];
  srsStates: Record<number, FlashcardSrsState>;
  createdAt: number;
  updatedAt: number;
}

// Dictation Engine Types
export type DictationPunctuationMode = 'OFF' | 'ESSENTIAL' | 'FULL_VERBATIM';

export type DictationElevationInterval = 'EVERY_45S' | 'EVERY_60S' | 'EVERY_90S' | 'PER_PASSAGE';

export interface DictationConfig {
  targetWpm: number;
  speechBurstRate: number;
  speechBurstWpmBaseline: number;
  punctuationMode: DictationPunctuationMode;
  elevatingEnabled: boolean;
  startWpm: number;
  targetMaxWpm: number;
  stepWpm: number;
  elevationInterval: DictationElevationInterval;
  autoAdvancePassages: boolean;
}

export const DEFAULT_DICTATION_CONFIG: DictationConfig = {
  targetWpm: 45,
  speechBurstRate: 0.96,
  speechBurstWpmBaseline: 148,
  punctuationMode: 'OFF',
  elevatingEnabled: false,
  startWpm: 35,
  targetMaxWpm: 75,
  stepWpm: 5,
  elevationInterval: 'EVERY_60S',
  autoAdvancePassages: true,
};

export interface DictationPassageItem {
  id: string;
  blockIndex: number;
  title: string;
  badge: string;
  promptQuestion: string;
  rawDictationText: string;
  originalProofreadText: string;
}

export interface DictationPhrase {
  index: number;
  sentenceIndex: number;
  paragraphIndex: number;
  displayText: string;
  spokenText: string;
  wordCount: number;
  charCount: number;
  isSentenceEnd: boolean;
  isParagraphEnd: boolean;
}

// Vocabulary Hub Types
export type VocabularyLevelCode =
  | 'A1'
  | 'A2'
  | 'B1'
  | 'B2'
  | 'C1'
  | 'C2'
  | 'MIXED'
  | 'CUSTOM';

export type VocabularyRecallDirection = 'WORD_TO_MEANING' | 'MEANING_TO_WORD';

export type VocabularyUtilityTier =
  | 'DAILY_HIGH_FREQUENCY'
  | 'EXAM_EDITORIAL'
  | 'LITERARY_PRECISION'
  | 'GENERAL_BALANCED';

export interface VocabularyExample {
  context: string;
  sentence: string;
  translation: string;
}

export interface VocabularyEntry {
  id: string;
  word: string;
  pronunciation: string;
  partOfSpeech: string;
  levelCode: VocabularyLevelCode;
  customLevelLabel: string;
  language: string;
  languageVariant: string;
  meaning: string;
  translation: string;
  translationLanguage: string;
  examples: VocabularyExample[];
  synonyms: string[];
  antonyms: string[];
  collocations: string[];
  usageNote: string;
  isBookmarked: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface VocabularyDeck {
  id: string;
  title: string;
  language: string;
  languageVariant: string;
  translationLanguage: string;
  levelCode: VocabularyLevelCode;
  customLevelLabel: string;
  domain: string;
  entries: VocabularyEntry[];
  createdAt: number;
  updatedAt: number;
}

export interface HeadwordQueueItem {
  id: string;
  word: string;
  language: string;
  levelCode: VocabularyLevelCode;
  customLevelLabel: string;
  partOfSpeech: string;
  domain: string;
  utilityReason: string;
  addedAt: number;
}

export interface HeadwordFilterResult {
  addedItems: HeadwordQueueItem[];
  skippedAlreadyInDeckCount: number;
  skippedAlreadyInQueueCount: number;
  skippedDismissedCount: number;
  totalProcessedCount: number;
}

export interface WordOfTheDayHistoryItem {
  utcDateKey: string;
  wordKey: string;
  word: string;
  partOfSpeech: string;
  pronunciation: string;
  meaning: string;
  translation: string;
  levelCode: string;
  customLevelLabel: string;
  sourceDeckId: string;
  sourceDeckTitle: string;
  shownAtMillis: number;
}

export interface LanguageWordOfTheDayState {
  languageKey: string;
  activeUtcDateKey: string;
  activeWordKey: string;
  seenWordKeysInCycle: string[];
  cycleNumber: number;
  history: WordOfTheDayHistoryItem[];
}

export type VocabularyDuplicateAction =
  | 'MERGE_SEGMENTS'
  | 'SKIP'
  | 'REPLACE_EXISTING'
  | 'KEEP_SEPARATE';

export interface VocabularyMergeDecision {
  action: VocabularyDuplicateAction;
  mergeExamples: boolean;
  mergeTranslation: boolean;
  mergeSynonymsAndCollocations: boolean;
  mergeMeaningAndNote: boolean;
  mergeLevelAndMeta: boolean;
}

export interface VocabularyDuplicateItem {
  id: string;
  incoming: VocabularyEntry;
  existing: VocabularyEntry;
  existingDeckId: string;
  existingDeckTitle: string;
  existingLevelTitle: string;
  isSameDeck: boolean;
  isIntraBatch: boolean;
}

export interface VocabularyImportAnalysis {
  uniqueNewEntries: VocabularyEntry[];
  duplicateItems: VocabularyDuplicateItem[];
  totalParsedCount: number;
  detectedFormat: 'JSON' | 'STRUCTURED_TEXT' | 'UNKNOWN';
  warnings: string[];
}

export type VocabularyAuditStatus =
  | 'VERIFIED'
  | 'CATEGORY_ISSUE'
  | 'LEVEL_MISMATCH'
  | 'MEANING_FIX'
  | 'EXAMPLE_FIX'
  | 'DUPLICATE_ALERT';

export interface VocabularyAuditFinding {
  entryId: string;
  currentEntry: VocabularyEntry;
  status: VocabularyAuditStatus;
  issueSummary: string;
  verifiedCategoryType: string;
  suggestedPartOfSpeech?: string;
  suggestedLevelCode?: VocabularyLevelCode;
  suggestedCustomLevelLabel?: string;
  suggestedMeaning?: string;
  suggestedTranslation?: string;
  suggestedUsageNote?: string;
  suggestedExamples?: VocabularyExample[];
}

// Multi-Mode Prompt Presets
export type PromptPresetMode =
  | 'QUESTION_SET'
  | 'KNOWLEDGE_SET'
  | 'FLASHCARD_SET'
  | 'VOCABULARY';

export interface QuestionPromptConfig {
  examType: string;
  subject: string;
  topic: string;
  questionCount: number;
  difficulty: string;
  questionLanguage: string;
  explanationChoice: string;
  customExplanationLanguage: string;
  optionCount: 4 | 5;
  includeExplanations: boolean;
  includeFormulasAndDiagrams: boolean;
  targetOutputFormat: 'JSON' | 'Text' | 'Table';
  promptDensity: 'Concise' | 'Comprehensive';
  enableQuantityCorrectionRule: boolean;
}

export interface KnowledgePromptConfig {
  examTarget: string;
  subject: string;
  topic: string;
  collectionType: KnowledgeCollectionType;
  customCollectionLabel: string;
  itemCount: number;
  depth: KnowledgeDepth;
  layerOrder: KnowledgeLayerOrder;
  language: string;
  includeBilingualTerms: boolean;
  preserveNestedBullets: boolean;
  includeLatexMath: boolean;
  includeCallouts: boolean;
  includeComparisonTable: boolean;
  includePyqTags: boolean;
  customInstruction: string;
}

export interface FlashcardPromptConfig {
  examTarget: string;
  subject: string;
  topic: string;
  cardStyle: FlashcardPromptStyle;
  cardCount: number;
  depth: KnowledgeDepth;
  language: string;
  includeBilingualTerms: boolean;
  atomicSrsMode: boolean;
  includeMnemonics: boolean;
  includeExamples: boolean;
  includeLatexMath: boolean;
  includeExamTraps: boolean;
  customInstruction: string;
}

export interface VocabularyPromptConfig {
  language: string;
  languageVariant: string;
  translationLanguage: string;
  levelCode: VocabularyLevelCode;
  customLevelLabel: string;
  domain: string;
  topic: string;
  wordCount: number;
  examplesPerWord: number;
  outputFormat: 'Strict JSON' | 'Structured Text';
  includePronunciation: boolean;
  includeSynonymsAndCollocations: boolean;
  includeUsageNote: boolean;
  excludeAlreadySavedWords: boolean;
  isHeadwordIndexOnly: boolean;
  utilityTier: VocabularyUtilityTier;
  customInstruction: string;
}

export interface PromptPreset {
  id: string;
  name: string;
  mode: PromptPresetMode;
  isPinned: boolean;
  createdAt: number;
  updatedAt: number;
  lastUsedAt: number;
  questionConfig?: QuestionPromptConfig;
  knowledgeConfig?: KnowledgePromptConfig;
  flashcardConfig?: FlashcardPromptConfig;
  vocabularyConfig?: VocabularyPromptConfig;
}
