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
  KnowledgeCoursesMap,
  LanguageWordOfTheDayState,
  PromptPreset,
  StudyNoteRecord,
  VocabularyDeck,
} from '../types/phase4';
import {
  DEFAULT_BRAIN_LAB_SETTINGS,
  DEFAULT_POMODORO_CONFIG,
  EMPTY_BRAIN_LAB_STATS,
  type AutoBackupSchedule,
  type BrainLabSettings,
  type BrainLabStats,
  type DailyStudyAnalytics,
  type PomodoroConfig,
  type StudyTodoItem,
  type VaultSnapshotRecord,
} from '../types/phase5';
import { ExamNumberGenerator } from '../utils/examNumberGenerator';
import { StudyDateUtils } from '../utils/studyPlannerEngine';

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
  KNOWLEDGE_COURSES: 'examset_knowledge_courses_v1',
  VOCAB_DECKS: 'examset_vocab_decks_v1',
  VOCAB_SRS: 'examset_vocab_srs_v1',
  VOCAB_VARIANT: 'examset_vocab_variant_v1',
  HEADWORD_INBOX: 'examset_headword_inbox_v1',
  HEADWORD_DISMISSED: 'examset_headword_dismissed_v1',
  WOTD_STATES: 'examset_wotd_states_v1',
  PROMPT_PRESETS: 'examset_prompt_presets_v1',
  DICTATION_CONFIG: 'examset_dictation_config_v1',
  // Phase 5 Storage Keys
  STUDY_TODOS: 'examset_study_todos_v1',
  POMODORO_CONFIG: 'examset_pomodoro_config_v1',
  DAILY_ANALYTICS: 'examset_daily_analytics_v1',
  BRAIN_LAB_SETTINGS: 'examset_brain_lab_settings_v1',
  BRAIN_LAB_STATS: 'examset_brain_lab_stats_v1',
  VAULT_SNAPSHOTS: 'examset_vault_snapshots_v1',
  DISMISSED_TIPS: 'examset_dismissed_tips_v1',
  AUTO_BACKUP_SCHEDULE: 'examset_auto_backup_schedule_v1',
  LAST_AUTO_BACKUP_AT: 'examset_last_auto_backup_at_v1',
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

const SNAPSHOT_BLOB_DB = 'examset_vault_blobs_v1';
const SNAPSHOT_BLOB_STORE = 'snapshot_blobs';

function openSnapshotBlobDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB unavailable'));
      return;
    }
    const req = indexedDB.open(SNAPSHOT_BLOB_DB, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(SNAPSHOT_BLOB_STORE)) {
        db.createObjectStore(SNAPSHOT_BLOB_STORE);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error || new Error('Failed to open snapshot blob store'));
  });
}

async function putSnapshotBlobInIdb(id: string, rawJson: string): Promise<void> {
  const db = await openSnapshotBlobDb();
  try {
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(SNAPSHOT_BLOB_STORE, 'readwrite');
      tx.objectStore(SNAPSHOT_BLOB_STORE).put(rawJson, id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error || new Error('Failed to write snapshot blob'));
    });
  } finally {
    db.close();
  }
}

async function getSnapshotBlobFromIdb(id: string): Promise<string | null> {
  const db = await openSnapshotBlobDb();
  try {
    return await new Promise<string | null>((resolve, reject) => {
      const tx = db.transaction(SNAPSHOT_BLOB_STORE, 'readonly');
      const req = tx.objectStore(SNAPSHOT_BLOB_STORE).get(id);
      req.onsuccess = () => resolve(typeof req.result === 'string' ? req.result : null);
      req.onerror = () => reject(req.error || new Error('Failed to read snapshot blob'));
    });
  } finally {
    db.close();
  }
}

async function deleteSnapshotBlobsInIdb(ids: string[]): Promise<void> {
  if (ids.length === 0) return;
  const db = await openSnapshotBlobDb();
  try {
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(SNAPSHOT_BLOB_STORE, 'readwrite');
      const store = tx.objectStore(SNAPSHOT_BLOB_STORE);
      ids.forEach((id) => store.delete(id));
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error || new Error('Failed to delete snapshot blobs'));
    });
  } finally {
    db.close();
  }
}

function pruneVaultSnapshotList(snapshots: VaultSnapshotRecord[]): {
  kept: VaultSnapshotRecord[];
  evictedIds: string[];
} {
  const sorted = [...snapshots].sort((a, b) => b.createdAt - a.createdAt);
  const manuals = sorted.filter((s) => s.kind === 'manual').slice(0, 10);
  const autoRestores = sorted.filter((s) => s.kind === 'pre_restore').slice(0, 5);
  const kept = [...manuals, ...autoRestores].sort((a, b) => b.createdAt - a.createdAt);
  const keptIds = new Set(kept.map((s) => s.id));
  const evictedIds = sorted.filter((s) => !keptIds.has(s.id)).map((s) => s.id);
  return { kept, evictedIds };
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
      targetExam: paper.metadata.targetExam || 'Standard Assessment',
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

  saveAttempts(attempts: AttemptRecord[]): void {
    safeWriteJson(KEYS.ATTEMPTS, Array.isArray(attempts) ? attempts : []);
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

  getAllActiveSessions(): Record<string, ActiveExamSession> {
    return safeReadJson<Record<string, ActiveExamSession>>(KEYS.SESSIONS, {});
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

  saveAllActiveSessions(sessions: Record<string, ActiveExamSession>): void {
    safeWriteJson(KEYS.SESSIONS, sessions && typeof sessions === 'object' ? sessions : {});
  },

  loadCountdowns(): ExamCountdownTarget[] {
    return safeReadJson<ExamCountdownTarget[]>(KEYS.COUNTDOWNS, []);
  },

  saveCountdowns(items: ExamCountdownTarget[]): void {
    safeWriteJson(KEYS.COUNTDOWNS, items);
  },

  // --- Phase 4 Persistence Methods ---

  normalizeStudyNote(note: StudyNoteRecord): StudyNoteRecord {
    return {
      ...note,
      course: (note.course || '').trim(),
      subject: (note.subject || '').trim() || 'General Studies',
      bookmarkedIndices: Array.isArray(note.bookmarkedIndices) ? note.bookmarkedIndices : [],
      masteredIndices: Array.isArray(note.masteredIndices) ? note.masteredIndices : [],
      srsStates: note.srsStates || {},
    };
  },

  normalizeKnowledgeCoursesMap(raw: unknown): KnowledgeCoursesMap {
    const out: KnowledgeCoursesMap = {};
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return out;
    for (const [rawCourse, rawSubjects] of Object.entries(raw as Record<string, unknown>)) {
      const course = String(rawCourse || '').trim();
      if (!course) continue;
      const seenSub = new Set<string>();
      const subjects: string[] = [];
      if (Array.isArray(rawSubjects)) {
        for (const item of rawSubjects) {
          const sub = String(item || '').trim();
          const key = sub.toLowerCase();
          if (sub && !seenSub.has(key)) {
            seenSub.add(key);
            subjects.push(sub);
          }
        }
      }
      out[course] = subjects;
    }
    return out;
  },

  mergeKnowledgeCoursesMaps(
    base: KnowledgeCoursesMap,
    incoming: KnowledgeCoursesMap
  ): KnowledgeCoursesMap {
    const result: KnowledgeCoursesMap = {};
    const courseKeyMap = new Map<string, string>();
    const addEntry = (courseRaw: string, subjectsRaw: string[]) => {
      const cTrim = courseRaw.trim();
      if (!cTrim) return;
      const cKey = cTrim.toLowerCase();
      const canonicalCourse = courseKeyMap.get(cKey) || cTrim;
      courseKeyMap.set(cKey, canonicalCourse);
      const existingList = result[canonicalCourse] || [];
      const seenSub = new Set(existingList.map((s) => s.trim().toLowerCase()));
      const mergedList = [...existingList];
      for (const sRaw of subjectsRaw || []) {
        const sTrim = String(sRaw || '').trim();
        const sKey = sTrim.toLowerCase();
        if (sTrim && !seenSub.has(sKey)) {
          seenSub.add(sKey);
          mergedList.push(sTrim);
        }
      }
      result[canonicalCourse] = mergedList;
    };
    for (const [c, subs] of Object.entries(base || {})) addEntry(c, subs);
    for (const [c, subs] of Object.entries(incoming || {})) addEntry(c, subs);
    return result;
  },

  loadKnowledgeCoursesMap(): KnowledgeCoursesMap {
    const stored = safeReadJson<unknown>(KEYS.KNOWLEDGE_COURSES, {});
    return this.normalizeKnowledgeCoursesMap(stored);
  },

  saveKnowledgeCoursesMap(map: KnowledgeCoursesMap): void {
    safeWriteJson(KEYS.KNOWLEDGE_COURSES, this.normalizeKnowledgeCoursesMap(map));
  },

  loadStudyNotes(seedNotes: StudyNoteRecord[] = []): StudyNoteRecord[] {
    const stored = safeReadJson<StudyNoteRecord[] | null>(KEYS.STUDY_NOTES, null);
    if (stored && Array.isArray(stored) && stored.length > 0) {
      return [...stored].map((n) => this.normalizeStudyNote(n)).sort((a, b) => b.updatedAt - a.updatedAt);
    }
    if (stored === null && seedNotes.length > 0) {
      const normalized = seedNotes.map((n) => this.normalizeStudyNote(n));
      safeWriteJson(KEYS.STUDY_NOTES, normalized);
      return normalized;
    }
    return (stored || []).map((n) => this.normalizeStudyNote(n));
  },

  saveStudyNotes(notes: StudyNoteRecord[]): void {
    safeWriteJson(
      KEYS.STUDY_NOTES,
      notes.map((n) => this.normalizeStudyNote(n))
    );
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

  // --- Phase 5 Persistence Methods ---

  loadStudyTodos(): StudyTodoItem[] {
    return safeReadJson<StudyTodoItem[]>(KEYS.STUDY_TODOS, []);
  },

  saveStudyTodos(todos: StudyTodoItem[]): void {
    safeWriteJson(KEYS.STUDY_TODOS, todos);
  },

  loadPomodoroConfig(): PomodoroConfig {
    const raw = safeReadJson<Partial<PomodoroConfig> | null>(KEYS.POMODORO_CONFIG, null);
    return raw ? { ...DEFAULT_POMODORO_CONFIG, ...raw } : DEFAULT_POMODORO_CONFIG;
  },

  savePomodoroConfig(cfg: PomodoroConfig): void {
    safeWriteJson(KEYS.POMODORO_CONFIG, cfg);
  },

  loadDailyAnalytics(): Record<string, DailyStudyAnalytics> {
    return safeReadJson<Record<string, DailyStudyAnalytics>>(KEYS.DAILY_ANALYTICS, {});
  },

  saveDailyAnalytics(map: Record<string, DailyStudyAnalytics>): void {
    safeWriteJson(KEYS.DAILY_ANALYTICS, map);
  },

  recordStudyTimeSeconds(seconds: number, nowMs: number = Date.now()): Record<string, DailyStudyAnalytics> {
    const safeSecs = Math.max(0, Math.round(seconds));
    const map = this.loadDailyAnalytics();
    if (safeSecs <= 0) return map;
    const key = StudyDateUtils.todayDateStr(nowMs);
    const cur = map[key] || {
      dateStr: key,
      testsCompleted: 0,
      questionsAnswered: 0,
      correctAnswers: 0,
      studyTimeSeconds: 0,
      flashcardsReviewed: 0,
      vocabWordsReviewed: 0,
    };
    const next: Record<string, DailyStudyAnalytics> = {
      ...map,
      [key]: { ...cur, studyTimeSeconds: cur.studyTimeSeconds + safeSecs },
    };
    this.saveDailyAnalytics(next);
    return next;
  },

  recordTestAttemptAnalytics(record: AttemptRecord): Record<string, DailyStudyAnalytics> {
    const map = this.loadDailyAnalytics();
    const key = StudyDateUtils.todayDateStr(record.submittedAt || Date.now());
    const cur = map[key] || {
      dateStr: key,
      testsCompleted: 0,
      questionsAnswered: 0,
      correctAnswers: 0,
      studyTimeSeconds: 0,
      flashcardsReviewed: 0,
      vocabWordsReviewed: 0,
    };
    const answered = record.correctCount + record.partialCount + record.incorrectCount;
    const next: Record<string, DailyStudyAnalytics> = {
      ...map,
      [key]: {
        ...cur,
        testsCompleted: cur.testsCompleted + 1,
        questionsAnswered: cur.questionsAnswered + answered,
        correctAnswers: cur.correctAnswers + record.correctCount,
        studyTimeSeconds: cur.studyTimeSeconds + Math.max(0, Math.round(record.timeSpentSeconds || 0)),
      },
    };
    this.saveDailyAnalytics(next);
    return next;
  },

  loadBrainLabSettings(): BrainLabSettings {
    const raw = safeReadJson<Partial<BrainLabSettings> | null>(KEYS.BRAIN_LAB_SETTINGS, null);
    return raw ? { ...DEFAULT_BRAIN_LAB_SETTINGS, ...raw } : DEFAULT_BRAIN_LAB_SETTINGS;
  },

  saveBrainLabSettings(settings: BrainLabSettings): void {
    safeWriteJson(KEYS.BRAIN_LAB_SETTINGS, settings);
  },

  loadBrainLabStats(): BrainLabStats {
    const raw = safeReadJson<Partial<BrainLabStats> | null>(KEYS.BRAIN_LAB_STATS, null);
    return raw ? { ...EMPTY_BRAIN_LAB_STATS, ...raw } : EMPTY_BRAIN_LAB_STATS;
  },

  saveBrainLabStats(stats: BrainLabStats): void {
    safeWriteJson(KEYS.BRAIN_LAB_STATS, stats);
  },

  loadVaultSnapshots(): VaultSnapshotRecord[] {
    return safeReadJson<VaultSnapshotRecord[]>(KEYS.VAULT_SNAPSHOTS, []).sort(
      (a, b) => b.createdAt - a.createdAt
    );
  },

  saveVaultSnapshots(snapshots: VaultSnapshotRecord[]): void {
    safeWriteJson(KEYS.VAULT_SNAPSHOTS, snapshots);
  },

  addVaultSnapshot(snap: VaultSnapshotRecord): VaultSnapshotRecord[] {
    const existing = this.loadVaultSnapshots();
    const { kept } = pruneVaultSnapshotList([snap, ...existing]);
    this.saveVaultSnapshots(kept);
    return kept;
  },

  async addVaultSnapshotWithBlob(snap: VaultSnapshotRecord): Promise<VaultSnapshotRecord[]> {
    const sizeBytes = snap.sizeBytes ?? snap.rawJson.length;
    let storedSnap: VaultSnapshotRecord = {
      ...snap,
      sizeBytes,
    };

    if (snap.rawJson) {
      try {
        await putSnapshotBlobInIdb(snap.id, snap.rawJson);
        storedSnap = {
          ...snap,
          rawJson: '',
          blobStorage: 'idb',
          sizeBytes,
        };
      } catch {
        storedSnap = {
          ...snap,
          blobStorage: 'inline',
          sizeBytes,
        };
      }
    }

    const existing = this.loadVaultSnapshots();
    const { kept, evictedIds } = pruneVaultSnapshotList([storedSnap, ...existing]);
    this.saveVaultSnapshots(kept);
    if (evictedIds.length > 0) {
      try {
        await deleteSnapshotBlobsInIdb(evictedIds);
      } catch {
        // Best-effort cleanup of evicted IndexedDB blobs
      }
    }
    return kept;
  },

  async resolveSnapshotRawJson(snap: VaultSnapshotRecord): Promise<string> {
    if (snap.rawJson && snap.rawJson.trim().length > 0) {
      return snap.rawJson;
    }
    try {
      const fromIdb = await getSnapshotBlobFromIdb(snap.id);
      if (fromIdb) return fromIdb;
    } catch {
      // Fall through to missing error below
    }
    throw new Error('Snapshot payload is unavailable in local browser storage.');
  },

  async deleteVaultSnapshotWithBlob(snapshotId: string): Promise<VaultSnapshotRecord[]> {
    const next = this.loadVaultSnapshots().filter((s) => s.id !== snapshotId);
    this.saveVaultSnapshots(next);
    try {
      await deleteSnapshotBlobsInIdb([snapshotId]);
    } catch {
      // Ignore IDB cleanup failures if IDB is unavailable
    }
    return next;
  },

  loadDismissedTips(): string[] {
    return safeReadJson<string[]>(KEYS.DISMISSED_TIPS, []);
  },

  saveDismissedTips(tips: string[]): void {
    safeWriteJson(KEYS.DISMISSED_TIPS, tips);
  },

  loadAutoBackupSchedule(): AutoBackupSchedule {
    const val = safeReadJson<string>(KEYS.AUTO_BACKUP_SCHEDULE, 'OFF');
    if (val === 'ON_CHANGE' || val === 'EVERY_6H' || val === 'DAILY' || val === 'OFF') {
      return val;
    }
    return 'OFF';
  },

  saveAutoBackupSchedule(schedule: AutoBackupSchedule): void {
    safeWriteJson(KEYS.AUTO_BACKUP_SCHEDULE, schedule);
  },

  loadLastAutoBackupAt(): number {
    return safeReadJson<number>(KEYS.LAST_AUTO_BACKUP_AT, 0);
  },

  saveLastAutoBackupAt(ts: number): void {
    safeWriteJson(KEYS.LAST_AUTO_BACKUP_AT, ts);
  },
};
