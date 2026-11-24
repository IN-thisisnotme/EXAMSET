import {
  ActiveExamSession,
  AttemptRecord,
  ExamCountdownTarget,
  FolderItem,
  QuestionPaper,
} from '../types/exam';
import { ExamNumberGenerator } from '../utils/examNumberGenerator';

const KEYS = {
  ACTIVE_PAPER: 'examset_active_paper',
  ACTIVE_PAPER_ID: 'examset_active_paper_id',
  PAPERS_LIBRARY: 'examset_papers_library',
  FOLDERS: 'examset_folders_library',
  ATTEMPTS: 'examset_attempts_v2',
  SESSIONS: 'examset_active_sessions_v1',
  COUNTDOWNS: 'examset_countdowns_v1',
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
};
