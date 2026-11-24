import {
  ActiveExamSession,
  AttemptRecord,
  ExamCountdownTarget,
  FolderItem,
  QuestionPaper,
} from '../types/exam';
import {
  DEFAULT_DICTATION_CONFIG,
  DictationConfig,
  FlashcardSrsState,
  HeadwordQueueItem,
  LanguageWordOfTheDayState,
  PromptPreset,
  StudyNoteRecord,
  VocabularyDeck,
} from '../types/phase4';
import { ExamNumberGenerator } from '../utils/examNumberGenerator';

const KEYS = {
  ACTIVE_PAPER: 'examset_active_paper',
  ACTIVE_PAPER_ID: 'examset_active_paper_id',
  PAPERS_LIBRARY: 'examset_papers_library',
  FOLDERS: 'examset_folders_library',
  ATTEMPTS: 'examset_attempts_v2',
  SESSIONS: 'examset_active_sessions_v1',
  COUNTDOWNS: 'examset_countdowns_v1',
  // Phase 4 Storage Keys
  STUDY_NOTES: 'examset_study_notes_v1',
  VOCAB_DECKS: 'examset_vocab_decks_v1',
  VOCAB_SRS: 'examset_vocab_srs_v1',
  VOCAB_VARIANT: 'examset_vocab_variant_v1',
  HEADWORD_INBOX: 'examset_headword_inbox_v1',
  HEADWORD_DISMISSED: 'examset_headword_dismissed_v1',
  WOTD_STATES: 'examset_wotd_states_v1',
  PROMPT_PRESETS: 'examset_prompt_presets_v1',
  DICTATION_CONFIG: 'examset_dictation_config_v1',
};

const LEGACY_SAMPLE_FOLDER_IDS = new Set(['f-science', 'f-upsc', 'f-ssc']);

export const DEFAULT_ROOT_FOLDER: FolderItem = {
  id: 'default',
  name: 'My Question Papers',
  color: '#177E89',
  parentId: null,
  createdAt: 1700000000000,
};

function safeReadJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function safeWriteJson(key: string, val: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(val));
  } catch (err) {
    console.warn('localStorage write failed for', key, err);
  }
}

export function normalizePaperMetadata(paper: QuestionPaper): QuestionPaper {
  const now = Date.now();
  return {
    ...paper,
    metadata: {
      ...paper.metadata,
      id: paper.metadata.id || `paper-${now}`,
      title: paper.metadata.title?.trim() || 'Untitled Examination Paper',
      subject: paper.metadata.subject?.trim() || 'General Assessment',
      topic: paper.metadata.topic || '',
      setNumber: ExamNumberGenerator.normalizeSetCode(paper.metadata.setNumber),
      targetExam: paper.metadata.targetExam || 'All India Competitive Exam',
      rollNumber: ExamNumberGenerator.normalizeRollNumber(paper.metadata.rollNumber),
      bookletNumber: ExamNumberGenerator.normalizeBookletNumber(paper.metadata.bookletNumber),
      durationMinutes: paper.metadata.durationMinutes ?? Math.max(15, paper.questions.length * 2),
      positiveMarks: paper.metadata.positiveMarks ?? 2.0,
      negativeMarks: paper.metadata.negativeMarks ?? 0.66,
      unattendedMarks: paper.metadata.unattendedMarks ?? 0.0,
      msqScoringPolicy: paper.metadata.msqScoringPolicy || 'ALL_OR_NOTHING',
      folderId: paper.metadata.folderId || 'default',
      createdAt: paper.metadata.createdAt || now,
      updatedAt: now,
    },
  };
}

export const ExamStore = {
  loadPapers(seedPaper?: QuestionPaper): QuestionPaper[] {
    const stored = safeReadJson<QuestionPaper[]>(KEYS.PAPERS_LIBRARY, []);
    if (stored.length > 0) {
      return stored.map((p) => ({
        ...p,
        metadata: {
          ...p.metadata,
          folderId: p.metadata.folderId || 'default',
          rollNumber: p.metadata.rollNumber || '0000000001',
          bookletNumber: p.metadata.bookletNumber || '0000001',
          positiveMarks: p.metadata.positiveMarks ?? 2.0,
          negativeMarks: p.metadata.negativeMarks ?? 0.66,
          unattendedMarks: p.metadata.unattendedMarks ?? 0.0,
          msqScoringPolicy: p.metadata.msqScoringPolicy || 'ALL_OR_NOTHING',
        },
      }));
    }

    const legacyActive = safeReadJson<QuestionPaper | null>(KEYS.ACTIVE_PAPER, null);
    const initial = legacyActive || seedPaper;
    if (initial) {
      const norm = normalizePaperMetadata(initial);
      safeWriteJson(KEYS.PAPERS_LIBRARY, [norm]);
      return [norm];
    }
    return [];
  },

  savePapers(papers: QuestionPaper[]): void {
    safeWriteJson(KEYS.PAPERS_LIBRARY, papers);
  },

  upsertPaper(paper: QuestionPaper): QuestionPaper[] {
    const normalized = normalizePaperMetadata(paper);
    const list = this.loadPapers();
    const idx = list.findIndex((p) => p.metadata.id === normalized.metadata.id);
    const next = idx >= 0 ? list.map((p, i) => (i === idx ? normalized : p)) : [normalized, ...list];
    this.savePapers(next);
    this.setActivePaper(normalized);
    return next;
  },

  setActivePaper(paper: QuestionPaper): void {
    safeWriteJson(KEYS.ACTIVE_PAPER, paper);
    try {
      localStorage.setItem(KEYS.ACTIVE_PAPER_ID, paper.metadata.id);
    } catch {
      // ignore
    }
  },

  getActivePaper(seedPaper: QuestionPaper): QuestionPaper {
    const library = this.loadPapers(seedPaper);
    const activeId = (() => {
      try {
        return localStorage.getItem(KEYS.ACTIVE_PAPER_ID);
      } catch {
        return null;
      }
    })();
    if (activeId) {
      const found = library.find((p) => p.metadata.id === activeId);
      if (found) return found;
    }
    const legacy = safeReadJson<QuestionPaper | null>(KEYS.ACTIVE_PAPER, null);
    if (legacy) return normalizePaperMetadata(legacy);
    return library[0] || normalizePaperMetadata(seedPaper);
  },

  loadFolders(papers: QuestionPaper[]): FolderItem[] {
    const raw = safeReadJson<FolderItem[]>(KEYS.FOLDERS, []);
    const assignedFolderIds = new Set(papers.map((p) => p.metadata.folderId || 'default'));

    // Cleanup legacy empty boilerplate sample folders
    const cleaned = raw.filter(
      (f) => f.id === 'default' || !LEGACY_SAMPLE_FOLDER_IDS.has(f.id) || assignedFolderIds.has(f.id)
    );

    if (!cleaned.some((f) => f.id === 'default')) {
      cleaned.unshift(DEFAULT_ROOT_FOLDER);
    }

    if (cleaned.length !== raw.length) {
      safeWriteJson(KEYS.FOLDERS, cleaned);
    }
    return cleaned;
  },

  saveFolders(folders: FolderItem[]): void {
    const withDefault = folders.some((f) => f.id === 'default')
      ? folders
      : [DEFAULT_ROOT_FOLDER, ...folders];
    safeWriteJson(KEYS.FOLDERS, withDefault);
  },

  loadAttempts(paperId?: string): AttemptRecord[] {
    const all = safeReadJson<AttemptRecord[]>(KEYS.ATTEMPTS, []);
    const sorted = [...all].sort((a, b) => b.submittedAt - a.submittedAt);
    return paperId ? sorted.filter((a) => a.paperId === paperId) : sorted;
  },

  saveAttempt(record: AttemptRecord): AttemptRecord[] {
    const all = this.loadAttempts();
    const next = [record, ...all].slice(0, 150);
    safeWriteJson(KEYS.ATTEMPTS, next);
    return next;
  },

  deleteAttempt(attemptId: string): AttemptRecord[] {
    const next = this.loadAttempts().filter((a) => a.id !== attemptId);
    safeWriteJson(KEYS.ATTEMPTS, next);
    return next;
  },

  getActiveSession(paperId: string): ActiveExamSession | null {
    const map = safeReadJson<Record<string, ActiveExamSession>>(KEYS.SESSIONS, {});
    return map[paperId] || null;
  },

  saveActiveSession(paperId: string, session: ActiveExamSession | null): void {
    const map = safeReadJson<Record<string, ActiveExamSession>>(KEYS.SESSIONS, {});
    if (session === null) {
      delete map[paperId];
    } else {
      map[paperId] = session;
    }
    safeWriteJson(KEYS.SESSIONS, map);
  },

  loadCountdowns(): ExamCountdownTarget[] {
    return safeReadJson<ExamCountdownTarget[]>(KEYS.COUNTDOWNS, []);
  },

  saveCountdowns(items: ExamCountdownTarget[]): void {
    safeWriteJson(KEYS.COUNTDOWNS, items);
  },

  // --- Phase 4 Persistence Methods ---

  loadStudyNotes(seedNotes: StudyNoteRecord[] = []): StudyNoteRecord[] {
    const stored = safeReadJson<StudyNoteRecord[] | null>(KEYS.STUDY_NOTES, null);
    if (stored && Array.isArray(stored) && stored.length > 0) {
      return [...stored].sort((a, b) => b.updatedAt - a.updatedAt);
    }
    if (stored === null && seedNotes.length > 0) {
      safeWriteJson(KEYS.STUDY_NOTES, seedNotes);
      return seedNotes;
    }
    return stored || [];
  },

  saveStudyNotes(notes: StudyNoteRecord[]): void {
    safeWriteJson(KEYS.STUDY_NOTES, notes);
  },

  loadVocabDecks(seedDecks: VocabularyDeck[] = []): VocabularyDeck[] {
    const stored = safeReadJson<VocabularyDeck[] | null>(KEYS.VOCAB_DECKS, null);
    if (stored && Array.isArray(stored) && stored.length > 0) {
      return stored;
    }
    if (stored === null && seedDecks.length > 0) {
      safeWriteJson(KEYS.VOCAB_DECKS, seedDecks);
      return seedDecks;
    }
    return stored || [];
  },

  saveVocabDecks(decks: VocabularyDeck[]): void {
    safeWriteJson(KEYS.VOCAB_DECKS, decks);
  },

  loadVocabSrs(): Record<string, Record<string, FlashcardSrsState>> {
    return safeReadJson<Record<string, Record<string, FlashcardSrsState>>>(KEYS.VOCAB_SRS, {});
  },

  saveVocabSrs(srsMap: Record<string, Record<string, FlashcardSrsState>>): void {
    safeWriteJson(KEYS.VOCAB_SRS, srsMap);
  },

  loadEnglishVariant(): string {
    try {
      return localStorage.getItem(KEYS.VOCAB_VARIANT) || 'UK';
    } catch {
      return 'UK';
    }
  },

  saveEnglishVariant(variant: string): void {
    try {
      localStorage.setItem(KEYS.VOCAB_VARIANT, variant);
    } catch {
      // ignore
    }
  },

  loadHeadwordInbox(): HeadwordQueueItem[] {
    return safeReadJson<HeadwordQueueItem[]>(KEYS.HEADWORD_INBOX, []);
  },

  saveHeadwordInbox(items: HeadwordQueueItem[]): void {
    safeWriteJson(KEYS.HEADWORD_INBOX, items);
  },

  loadDismissedHeadwords(): string[] {
    return safeReadJson<string[]>(KEYS.HEADWORD_DISMISSED, []);
  },

  saveDismissedHeadwords(keys: string[]): void {
    safeWriteJson(KEYS.HEADWORD_DISMISSED, keys.slice(-2000));
  },

  loadWotdStates(): Record<string, LanguageWordOfTheDayState> {
    return safeReadJson<Record<string, LanguageWordOfTheDayState>>(KEYS.WOTD_STATES, {});
  },

  saveWotdStates(states: Record<string, LanguageWordOfTheDayState>): void {
    safeWriteJson(KEYS.WOTD_STATES, states);
  },

  loadPromptPresets(): PromptPreset[] {
    return safeReadJson<PromptPreset[]>(KEYS.PROMPT_PRESETS, []);
  },

  savePromptPresets(presets: PromptPreset[]): void {
    safeWriteJson(KEYS.PROMPT_PRESETS, presets);
  },

  loadDictationConfig(): DictationConfig {
    const raw = safeReadJson<Partial<DictationConfig> | null>(KEYS.DICTATION_CONFIG, null);
    return raw ? { ...DEFAULT_DICTATION_CONFIG, ...raw } : DEFAULT_DICTATION_CONFIG;
  },

  saveDictationConfig(cfg: DictationConfig): void {
    safeWriteJson(KEYS.DICTATION_CONFIG, cfg);
  },
};
