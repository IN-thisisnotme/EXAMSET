// Phase 5 Domain Types: Study Planner, Pomodoro Focus Clock, Brain & Calm Lab, Backup Vault & App Guide

export type TodoPriority = 'HIGH' | 'MEDIUM' | 'LOW';

export type TodoFilter = 'ALL' | 'TODAY' | 'HIGH_PRIORITY' | 'COMPLETED';

export type TodoLinkType = 'NONE' | 'PAPER' | 'NOTE' | 'VOCAB_DECK';

export interface StudyTodoItem {
  id: string;
  title: string;
  priority: TodoPriority;
  dueDateStr: string; // YYYY-MM-DD or ''
  isCompleted: boolean;
  targetPomodoros: number;
  completedPomodoros: number;
  linkedType: TodoLinkType;
  linkedPaperId?: string | null;
  linkedNoteId?: string | null;
  linkedDeckLanguage: string;
  linkedLabel: string;
  createdAt: number;
  completedAt?: number | null;
}

export type PomodoroPreset = 'CLASSIC_25_5' | 'DEEP_50_10' | 'MARATHON_90_15' | 'CUSTOM';

export const POMODORO_PRESET_META: Record<
  PomodoroPreset,
  { id: PomodoroPreset; label: string; focusMinutes: number; breakMinutes: number; summary: string }
> = {
  CLASSIC_25_5: {
    id: 'CLASSIC_25_5',
    label: '25 / 5 Classic',
    focusMinutes: 25,
    breakMinutes: 5,
    summary: 'Balanced focus sprint with 5m reset',
  },
  DEEP_50_10: {
    id: 'DEEP_50_10',
    label: '50 / 10 Deep',
    focusMinutes: 50,
    breakMinutes: 10,
    summary: 'Deep problem-solving or GS reading block',
  },
  MARATHON_90_15: {
    id: 'MARATHON_90_15',
    label: '90 / 15 Mock',
    focusMinutes: 90,
    breakMinutes: 15,
    summary: 'Full-length exam stamina simulation',
  },
  CUSTOM: {
    id: 'CUSTOM',
    label: 'Custom',
    focusMinutes: 30,
    breakMinutes: 5,
    summary: 'Your custom focus & break cadence',
  },
};

export type PomodoroPhase = 'IDLE' | 'FOCUS' | 'BREAK' | 'PAUSED';

export interface PomodoroConfig {
  preset: PomodoroPreset;
  customFocusMinutes: number;
  customBreakMinutes: number;
  quietModeEnabled: boolean;
  chimeEnabled: boolean;
  browserNotifyEnabled: boolean;
  dailyFocusGoalMinutes: number;
}

export const DEFAULT_POMODORO_CONFIG: PomodoroConfig = {
  preset: 'CLASSIC_25_5',
  customFocusMinutes: 30,
  customBreakMinutes: 5,
  quietModeEnabled: false,
  chimeEnabled: true,
  browserNotifyEnabled: false,
  dailyFocusGoalMinutes: 120,
};

export interface DailyStudyAnalytics {
  dateStr: string; // YYYY-MM-DD
  testsCompleted: number;
  questionsAnswered: number;
  correctAnswers: number;
  studyTimeSeconds: number;
  flashcardsReviewed: number;
  vocabWordsReviewed: number;
}

export type DueSuggestionCategory = 'FLASHCARDS' | 'VOCABULARY' | 'HEADWORD_INBOX' | 'PAUSED_TEST';

export interface StudyDueSuggestion {
  id: string;
  category: DueSuggestionCategory;
  title: string;
  subtitle: string;
  count: number;
  paperId?: string | null;
  noteId?: string | null;
  language?: string | null;
}

// --- Brain & Calm Lab Types ---

export type BrainLabPillar = 'WORKING_MEMORY' | 'CRITICAL_REASONING' | 'VISUAL_QUANT' | 'CALM_RESET';

export type BrainLabActivityId =
  | 'N_BACK'
  | 'CORSI_SPAN'
  | 'STROOP_SWITCH'
  | 'SYLLOGISM'
  | 'CONSTRAINT_SEATING'
  | 'ARGUMENT_CRITIQUE'
  | 'DATA_SUFFICIENCY'
  | 'SCHULTE_TRAIL'
  | 'MENTAL_QUANT'
  | 'GUIDED_BREATHING'
  | 'VISUAL_SOFT_RESET'
  | 'ZEN_LOGIC_GRID';

export interface BrainLabPillarMeta {
  id: BrainLabPillar;
  title: string;
  subtitle: string;
  badge: string;
}

export const BRAIN_LAB_PILLARS: BrainLabPillarMeta[] = [
  {
    id: 'WORKING_MEMORY',
    title: 'Working Memory',
    subtitle: 'Active updating, sequence reversal & distractor inhibition',
    badge: 'ATTENTION',
  },
  {
    id: 'CRITICAL_REASONING',
    title: 'Critical Deduction',
    subtitle: 'Venn-verified syllogisms, seating grids & fallacy critique',
    badge: 'LOGIC',
  },
  {
    id: 'VISUAL_QUANT',
    title: 'Scan & Quant Blitz',
    subtitle: 'Peripheral Schulte tables, trail switching & fast mental math',
    badge: 'SPEED',
  },
  {
    id: 'CALM_RESET',
    title: 'Calm & Reset',
    subtitle: 'Nervous-system breathing pacers, 20-20-20 & untimed Zen grids',
    badge: 'RECOVERY',
  },
];

export interface BrainLabActivityMeta {
  id: BrainLabActivityId;
  pillar: BrainLabPillar;
  title: string;
  shortSummary: string;
  examTransfer: string;
  defaultBudgetMinutes: number;
  countsAsCognitiveStudy: boolean;
}

export const BRAIN_LAB_ACTIVITIES: BrainLabActivityMeta[] = [
  {
    id: 'N_BACK',
    pillar: 'WORKING_MEMORY',
    title: 'Adaptive N-Back (1–4)',
    shortSummary: 'Dual or single position + letter updating with adaptive tiering',
    examTransfer: 'Holds nested legal clauses, RC threads, and multi-condition rules in active memory',
    defaultBudgetMinutes: 3,
    countsAsCognitiveStudy: true,
  },
  {
    id: 'CORSI_SPAN',
    pillar: 'WORKING_MEMORY',
    title: 'Corsi & Reverse Span',
    shortSummary: '4×4 / 5×5 spatial sequence & reverse sequence manipulation',
    examTransfer: 'Strengthens active manipulation rather than passive visual echo',
    defaultBudgetMinutes: 3,
    countsAsCognitiveStudy: true,
  },
  {
    id: 'STROOP_SWITCH',
    pillar: 'WORKING_MEMORY',
    title: 'Stroop Rule-Switch',
    shortSummary: 'Color-word & arrow-direction conflicts with live rule flips',
    examTransfer: 'Trains impulse inhibition against NOT-incorrect distractor traps',
    defaultBudgetMinutes: 3,
    countsAsCognitiveStudy: true,
  },
  {
    id: 'SYLLOGISM',
    pillar: 'CRITICAL_REASONING',
    title: 'Venn Syllogism Arena',
    shortSummary: 'Exhaustively verified 2–3 premise deductions with counterexamples',
    examTransfer: 'Eliminates intuitive leaps on All/Some/No/Some-Not exam items',
    defaultBudgetMinutes: 4,
    countsAsCognitiveStudy: true,
  },
  {
    id: 'CONSTRAINT_SEATING',
    pillar: 'CRITICAL_REASONING',
    title: 'Seating & Pairing Grid',
    shortSummary: 'Unique-solution DILR mini-puzzles with an interactive seat scratchpad',
    examTransfer: 'Sharpens structured elimination for CAT DILR & analytical reasoning',
    defaultBudgetMinutes: 5,
    countsAsCognitiveStudy: true,
  },
  {
    id: 'ARGUMENT_CRITIQUE',
    pillar: 'CRITICAL_REASONING',
    title: 'Argument & Fallacy',
    shortSummary: 'Pinpoint required assumptions, inferences & named logical fallacies',
    examTransfer: 'Targets editorial CR passages, CLAT & GMAT assumption traps',
    defaultBudgetMinutes: 4,
    countsAsCognitiveStudy: true,
  },
  {
    id: 'DATA_SUFFICIENCY',
    pillar: 'CRITICAL_REASONING',
    title: 'Data Sufficiency Check',
    shortSummary: 'Judge Statement I & II sufficiency without full arithmetic',
    examTransfer: 'Catches hidden negative, zero, and fractional boundary traps',
    defaultBudgetMinutes: 4,
    countsAsCognitiveStudy: true,
  },
  {
    id: 'SCHULTE_TRAIL',
    pillar: 'VISUAL_QUANT',
    title: 'Schulte & Trail Grid',
    shortSummary: '5×5 / 6×6 peripheral scan and alternating 1→A→2→B switching',
    examTransfer: 'Widens eye span for faster RC skimming & clean OMR row alignment',
    defaultBudgetMinutes: 3,
    countsAsCognitiveStudy: true,
  },
  {
    id: 'MENTAL_QUANT',
    pillar: 'VISUAL_QUANT',
    title: 'Mental Quant Blitz',
    shortSummary: '60s fraction-to-% benchmarks, compound shifts & ratio duels',
    examTransfer: 'Builds pen-free DI approximation speed under strict time pressure',
    defaultBudgetMinutes: 3,
    countsAsCognitiveStudy: true,
  },
  {
    id: 'GUIDED_BREATHING',
    pillar: 'CALM_RESET',
    title: 'Guided Breathing Pacer',
    shortSummary: 'Box 4-4-4-4, Cyclic Sigh & 4-7-8 smooth 60fps ring + audio cue',
    examTransfer: 'Downshifts pre-exam adrenaline so prefrontal working memory stays online',
    defaultBudgetMinutes: 3,
    countsAsCognitiveStudy: false,
  },
  {
    id: 'VISUAL_SOFT_RESET',
    pillar: 'CALM_RESET',
    title: '60s Visual Soft-Reset',
    shortSummary: '20-20-20 far-gaze, shoulder/jaw release & slow blink cadence',
    examTransfer: 'Relieves eye strain after long mock tests or dense textbook sessions',
    defaultBudgetMinutes: 1,
    countsAsCognitiveStudy: false,
  },
  {
    id: 'ZEN_LOGIC_GRID',
    pillar: 'CALM_RESET',
    title: 'Untimed Zen Logic Grid',
    shortSummary: 'Calm 4×4 & 6×6 single-solution grids with zero timers or penalties',
    examTransfer: 'Settles racing thoughts cleanly post-mock or before sleep',
    defaultBudgetMinutes: 5,
    countsAsCognitiveStudy: false,
  },
];

export type NBackMode = 'DUAL' | 'POSITION_ONLY' | 'LETTER_ONLY';

export type BreathingProtocol = 'BOX_4_4_4_4' | 'CYCLIC_SIGH' | 'CALM_4_7_8';

export const BREATHING_PROTOCOLS: Record<
  BreathingProtocol,
  { id: BreathingProtocol; title: string; ratioLabel: string; description: string }
> = {
  BOX_4_4_4_4: {
    id: 'BOX_4_4_4_4',
    title: 'Box Breathing',
    ratioLabel: '4-4-4-4',
    description: 'Balanced cadence for steady pre-mock focus and composure.',
  },
  CYCLIC_SIGH: {
    id: 'CYCLIC_SIGH',
    title: 'Physiological Cyclic Sigh',
    ratioLabel: '2.5-1.5-6',
    description: 'Double nasal inhale + extended exhale for rapid autonomic downshift.',
  },
  CALM_4_7_8: {
    id: 'CALM_4_7_8',
    title: '4-7-8 Calm',
    ratioLabel: '4-7-8',
    description: 'Long breath hold and exhale for post-study recovery and sleep wind-down.',
  },
};

export interface BrainLabSettings {
  audioEnabled: boolean;
  countCalmInDailyStudyTime: boolean;
  preferredBudgetMinutes: number;
}

export const DEFAULT_BRAIN_LAB_SETTINGS: BrainLabSettings = {
  audioEnabled: false,
  countCalmInDailyStudyTime: false,
  preferredBudgetMinutes: 3,
};

export interface BrainLabStats {
  bestNBackTier: number;
  bestNBackAccuracy: number;
  bestCorsiForwardSpan: number;
  bestCorsiReverseSpan: number;
  bestStroopAccuracy: number;
  bestStroopReactionMs: number;
  syllogismSolved: number;
  syllogismCorrect: number;
  reasoningStreak: number;
  bestReasoningStreak: number;
  constraintSolved: number;
  argumentSolved: number;
  dataSufficiencySolved: number;
  bestSchulte5x5Ms: number;
  bestSchulte6x6Ms: number;
  bestTrail5x5Ms: number;
  bestQuantBlitzScore: number;
  calmSessionsCompleted: number;
  zenGridsCompleted: number;
  totalCognitiveSeconds: number;
  totalCalmSeconds: number;
  lastUpdatedAtMillis: number;
}

export const EMPTY_BRAIN_LAB_STATS: BrainLabStats = {
  bestNBackTier: 1,
  bestNBackAccuracy: 0,
  bestCorsiForwardSpan: 0,
  bestCorsiReverseSpan: 0,
  bestStroopAccuracy: 0,
  bestStroopReactionMs: 0,
  syllogismSolved: 0,
  syllogismCorrect: 0,
  reasoningStreak: 0,
  bestReasoningStreak: 0,
  constraintSolved: 0,
  argumentSolved: 0,
  dataSufficiencySolved: 0,
  bestSchulte5x5Ms: 0,
  bestSchulte6x6Ms: 0,
  bestTrail5x5Ms: 0,
  bestQuantBlitzScore: 0,
  calmSessionsCompleted: 0,
  zenGridsCompleted: 0,
  totalCognitiveSeconds: 0,
  totalCalmSeconds: 0,
  lastUpdatedAtMillis: 0,
};

// --- Backup Vault & App Guide Types ---

export type RestoreMode = 'SMART_MERGE' | 'FULL_REPLACE';

export interface BackupCounts {
  papersCount: number;
  notesCount: number;
  foldersCount: number;
  attemptsCount: number;
  analyticsDaysCount: number;
  vocabDecksCount: number;
  vocabWordsCount: number;
  promptPresetsCount: number;
  todosCount: number;
  countdownsCount: number;
}

export interface BackupManifest {
  format: 'examset-vault-v5' | 'examset-legacy-json';
  schemaVersion: number;
  appVersion: string;
  createdAt: number;
  deviceName: string;
  counts: BackupCounts;
  entryChecksumsSha256: Record<string, string>;
}

export interface VaultSnapshotRecord {
  id: string;
  label: string;
  kind: 'manual' | 'pre_restore';
  createdAt: number;
  manifest: BackupManifest;
  rawJson: string;
  blobStorage?: 'idb' | 'inline';
  sizeBytes?: number;
}

export interface RestorePreview {
  sourceLabel: string;
  isLegacyJson: boolean;
  checksumVerified: boolean;
  checksumDetails: string;
  manifest: BackupManifest;
  rawJson: string;
}

export type AppGuideCategory =
  | 'ALL'
  | 'EXAMS_TESTS'
  | 'NOTES_FLASHCARDS'
  | 'LANGUAGES_AUDIO'
  | 'FOCUS_PRODUCTIVITY'
  | 'BACKUP_TOOLS';

export type GuideDestination =
  | 'CREATOR'
  | 'MASTER_VIEW'
  | 'STUDENT_TEST'
  | 'PRINT_OMR'
  | 'FOLDERS'
  | 'AI_PROMPT_STUDIO'
  | 'KNOWLEDGE_STUDIO'
  | 'DICTATION_LAB'
  | 'VOCABULARY_HUB'
  | 'HEADWORD_INBOX'
  | 'STUDY_PLANNER'
  | 'BRAIN_LAB'
  | 'BACKUP_VAULT'
  | 'COUNTDOWNS';

export interface AppGuideEntry {
  id: string;
  title: string;
  category: AppGuideCategory;
  summary: string;
  quickTip: string;
  details: string;
  keywords: string[];
  actionLabel: string;
  destination: GuideDestination;
}

// --- Cloud Drive, OneDrive & Computer Disk Sync Types ---

export type CloudProviderType = 'GOOGLE_DRIVE' | 'ONEDRIVE';

export type AutoBackupSchedule = 'OFF' | 'ON_CHANGE' | 'EVERY_6H' | 'DAILY';

export interface AutoBackupScheduleOption {
  id: AutoBackupSchedule;
  label: string;
  intervalMs: number;
  description: string;
}

export const AUTO_BACKUP_SCHEDULE_OPTIONS: AutoBackupScheduleOption[] = [
  {
    id: 'OFF',
    label: 'Manual Only',
    intervalMs: 0,
    description: 'Create snapshots and sync manually when desired',
  },
  {
    id: 'ON_CHANGE',
    label: 'On Workspace Change',
    intervalMs: 60 * 1000,
    description: 'Debounced local checkpoint & live disk sync after edits (max once per min)',
  },
  {
    id: 'EVERY_6H',
    label: 'Every 6 Hours',
    intervalMs: 6 * 60 * 60 * 1000,
    description: 'Rolling checkpoint & connected sync every 6 hours while open',
  },
  {
    id: 'DAILY',
    label: 'Daily Backup',
    intervalMs: 24 * 60 * 60 * 1000,
    description: 'Daily scheduled snapshot & linked cloud/disk sync',
  },
];

export type TokenStoragePreference = 'SESSION' | 'LOCAL';

export interface LinkedDiskFileStatus {
  isLinked: boolean;
  displayName: string;
  syncMode: 'CHROMIUM_FSA' | 'DOWNLOAD_FALLBACK';
  permissionState: 'granted' | 'prompt' | 'unavailable';
  lastSyncedAt: number;
  lastError: string | null;
}

export interface CloudAccountStatus {
  provider: CloudProviderType;
  displayName: string;
  hasClientIdConfigured: boolean;
  clientId: string;
  isConnected: boolean;
  accountEmail: string;
  expiresAt: number;
  lastSyncedAt: number;
  lastError: string | null;
  tokenStoragePreference: TokenStoragePreference;
}

export interface RemoteCloudBackupFile {
  provider: CloudProviderType;
  remoteId: string;
  name: string;
  sizeBytes: number;
  modifiedAt: number;
}

export interface TrustedTimeStatus {
  isSynced: boolean;
  isOnline: boolean;
  checkedAt: number;
  serverTimeMs: number | null;
  skewSeconds: number;
  sourceLabel: string;
}

