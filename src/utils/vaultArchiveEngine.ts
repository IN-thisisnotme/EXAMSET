import type { AppFontScaleMode, ColorSchemePreset, ThemeMode } from '../theme/colors';
import type {
  ActiveExamSession,
  AttemptRecord,
  ExamCountdownTarget,
  FolderItem,
  QuestionPaper,
} from '../types/exam';
import type {
  DictationConfig,
  FlashcardSrsState,
  HeadwordQueueItem,
  KnowledgeCoursesMap,
  LanguageWordOfTheDayState,
  PromptPreset,
  StudyNoteRecord,
  VocabularyDeck,
  VocabularyEntry,
  VocabularyExample,
} from '../types/phase4';
import type {
  BackupCounts,
  BackupManifest,
  BrainLabSettings,
  BrainLabStats,
  DailyStudyAnalytics,
  PomodoroConfig,
  RestoreMode,
  RestorePreview,
  StudyTodoItem,
} from '../types/phase5';
import {
  DEFAULT_BRAIN_LAB_SETTINGS,
  DEFAULT_POMODORO_CONFIG,
  EMPTY_BRAIN_LAB_STATS,
} from '../types/phase5';
import { mergeBrainLabStats } from './brainLabEngines';
import { sha256HexSync } from './sha256Sync';
import { isZipBytes, MAX_ZIP_TOTAL_BYTES } from './zipArchiveHelper';
import {
  buildAndroidZipVaultArchive,
  inspectAndroidZipBytes,
} from './androidZipVaultBridge';

export { sha256HexSync, buildAndroidZipVaultArchive, inspectAndroidZipBytes };

export const MAX_JSON_IMPORT_BYTES = 64 * 1024 * 1024;

export interface WorkspaceBackupState {
  papers: QuestionPaper[];
  folders: FolderItem[];
  attempts: AttemptRecord[];
  activeSessions?: Record<string, ActiveExamSession>;
  countdowns: ExamCountdownTarget[];
  studyNotes: StudyNoteRecord[];
  knowledgeCourses?: KnowledgeCoursesMap;
  vocabDecks: VocabularyDeck[];
  vocabSrs: Record<string, Record<string, FlashcardSrsState>>;
  englishVariant: string;
  headwordInbox: HeadwordQueueItem[];
  dismissedHeadwords: string[];
  wotdStates: Record<string, LanguageWordOfTheDayState>;
  promptPresets: PromptPreset[];
  dictationConfig: DictationConfig;
  studyTodos: StudyTodoItem[];
  pomodoroConfig: PomodoroConfig;
  dailyAnalytics: Record<string, DailyStudyAnalytics>;
  brainLabStats: BrainLabStats;
  brainLabSettings: BrainLabSettings;
  dismissedTips: string[];
  themeMode: ThemeMode;
  colorPreset: ColorSchemePreset;
  fontScaleMode: AppFontScaleMode;
}

export interface VaultArchiveDocument {
  manifest: BackupManifest;
  sections: {
    papers: QuestionPaper[];
    folders: FolderItem[];
    attempts: AttemptRecord[];
    countdowns: ExamCountdownTarget[];
    studyNotes: StudyNoteRecord[];
    vocabulary: {
      decks: VocabularyDeck[];
      srs: Record<string, Record<string, FlashcardSrsState>>;
      englishVariant: string;
      headwordInbox: HeadwordQueueItem[];
      dismissedHeadwords: string[];
      wotdStates: Record<string, LanguageWordOfTheDayState>;
    };
    presetsAndDictation: {
      promptPresets: PromptPreset[];
      dictationConfig: DictationConfig;
    };
    plannerAndAnalytics: {
      studyTodos: StudyTodoItem[];
      pomodoroConfig: PomodoroConfig;
      dailyAnalytics: Record<string, DailyStudyAnalytics>;
      activeSessions?: Record<string, ActiveExamSession>;
    };
    brainAndPreferences: {
      brainLabStats: BrainLabStats;
      brainLabSettings: BrainLabSettings;
      dismissedTips: string[];
      knowledgeCourses?: KnowledgeCoursesMap;
      themeMode: ThemeMode;
      colorPreset: ColorSchemePreset;
      fontScaleMode: AppFontScaleMode;
    };
  };
}

export function normalizeCourseMapForBackup(raw: unknown): KnowledgeCoursesMap {
  const out: KnowledgeCoursesMap = {};
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return out;
  for (const [cRaw, sRaw] of Object.entries(raw as Record<string, unknown>)) {
    const course = String(cRaw || '').trim();
    if (!course) continue;
    const seen = new Set<string>();
    const subjects: string[] = [];
    if (Array.isArray(sRaw)) {
      for (const s of sRaw) {
        const sub = String(s || '').trim();
        const k = sub.toLowerCase();
        if (sub && !seen.has(k)) {
          seen.add(k);
          subjects.push(sub);
        }
      }
    }
    out[course] = subjects;
  }
  return out;
}

export function mergeCourseMapsForRestore(
  base: KnowledgeCoursesMap,
  incoming: KnowledgeCoursesMap
): KnowledgeCoursesMap {
  const out: KnowledgeCoursesMap = {};
  const courseLookup = new Map<string, string>();
  const add = (cRaw: string, subs: string[]) => {
    const c = cRaw.trim();
    if (!c) return;
    const ck = c.toLowerCase();
    const canonical = courseLookup.get(ck) || c;
    courseLookup.set(ck, canonical);
    const list = out[canonical] || [];
    const seen = new Set(list.map((s) => s.trim().toLowerCase()));
    const next = [...list];
    for (const sRaw of subs || []) {
      const s = String(sRaw || '').trim();
      const sk = s.toLowerCase();
      if (s && !seen.has(sk)) {
        seen.add(sk);
        next.push(s);
      }
    }
    out[canonical] = next;
  };
  for (const [c, subs] of Object.entries(base || {})) add(c, subs);
  for (const [c, subs] of Object.entries(incoming || {})) add(c, subs);
  return out;
}

export function normalizeStudyNoteForBackup(note: StudyNoteRecord): StudyNoteRecord {
  return {
    ...note,
    course: (note.course || '').trim(),
    subject: (note.subject || '').trim() || 'General Studies',
    bookmarkedIndices: Array.isArray(note.bookmarkedIndices) ? note.bookmarkedIndices : [],
    masteredIndices: Array.isArray(note.masteredIndices) ? note.masteredIndices : [],
    srsStates: note.srsStates || {},
  };
}

export function computeBackupCounts(state: WorkspaceBackupState): BackupCounts {
  return {
    papersCount: state.papers.length,
    notesCount: state.studyNotes.length,
    knowledgeCoursesCount: Object.keys(normalizeCourseMapForBackup(state.knowledgeCourses || {})).length,
    foldersCount: state.folders.length,
    attemptsCount: state.attempts.length,
    analyticsDaysCount: Object.keys(state.dailyAnalytics).length,
    vocabDecksCount: state.vocabDecks.length,
    vocabWordsCount: state.vocabDecks.reduce((sum, d) => sum + d.entries.length, 0),
    promptPresetsCount: state.promptPresets.length,
    todosCount: state.studyTodos.length,
    countdownsCount: state.countdowns.length,
  };
}

export function buildVaultArchive(
  state: WorkspaceBackupState,
  deviceName: string = 'ExamSet Web Browser',
  nowMs: number = Date.now()
): { document: VaultArchiveDocument; rawJson: string } {
  const normalizedCourses = normalizeCourseMapForBackup(state.knowledgeCourses || {});
  const normalizedNotes = state.studyNotes.map(normalizeStudyNoteForBackup);
  const sections: VaultArchiveDocument['sections'] = {
    papers: state.papers,
    folders: state.folders,
    attempts: state.attempts,
    countdowns: state.countdowns,
    studyNotes: normalizedNotes,
    vocabulary: {
      decks: state.vocabDecks,
      srs: state.vocabSrs,
      englishVariant: state.englishVariant,
      headwordInbox: state.headwordInbox,
      dismissedHeadwords: state.dismissedHeadwords,
      wotdStates: state.wotdStates,
    },
    presetsAndDictation: {
      promptPresets: state.promptPresets,
      dictationConfig: state.dictationConfig,
    },
    plannerAndAnalytics: {
      studyTodos: state.studyTodos,
      pomodoroConfig: state.pomodoroConfig,
      dailyAnalytics: state.dailyAnalytics,
      ...(state.activeSessions && Object.keys(state.activeSessions).length > 0
        ? { activeSessions: state.activeSessions }
        : {}),
    },
    brainAndPreferences: {
      brainLabStats: state.brainLabStats,
      brainLabSettings: state.brainLabSettings,
      dismissedTips: state.dismissedTips,
      knowledgeCourses: normalizedCourses,
      themeMode: state.themeMode,
      colorPreset: state.colorPreset,
      fontScaleMode: state.fontScaleMode,
    },
  };

  const entryChecksumsSha256: Record<string, string> = {};
  for (const [key, val] of Object.entries(sections)) {
    entryChecksumsSha256[key] = sha256HexSync(JSON.stringify(val));
  }

  const manifest: BackupManifest = {
    format: 'examset-vault-v5',
    schemaVersion: 5,
    appVersion: '2.5.0-web',
    createdAt: nowMs,
    deviceName,
    counts: computeBackupCounts(state),
    entryChecksumsSha256,
  };

  const document: VaultArchiveDocument = { manifest, sections };
  const rawJson = JSON.stringify(document, null, 2);
  return { document, rawJson };
}

export async function inspectVaultArchiveBytes(
  bytes: Uint8Array,
  sourceLabel: string
): Promise<RestorePreview> {
  if (bytes.byteLength === 0) {
    throw new Error('Selected backup file is empty.');
  }
  if (isZipBytes(bytes)) {
    if (bytes.byteLength > MAX_ZIP_TOTAL_BYTES) {
      throw new Error('Selected .examset.zip archive exceeds safe 80 MB size limit.');
    }
    return inspectAndroidZipBytes(bytes, sourceLabel);
  }
  if (bytes.byteLength > MAX_JSON_IMPORT_BYTES) {
    throw new Error('Selected JSON backup exceeds safe 64 MB size limit.');
  }
  const text = new TextDecoder('utf-8').decode(bytes);
  return inspectVaultArchiveText(text, sourceLabel);
}

export async function inspectVaultArchiveFile(file: File): Promise<RestorePreview> {
  const maxAllowed = Math.max(MAX_ZIP_TOTAL_BYTES, MAX_JSON_IMPORT_BYTES);
  if (file.size > maxAllowed) {
    throw new Error(`Selected file (${Math.round(file.size / (1024 * 1024))} MB) exceeds safe size limit.`);
  }
  const buffer = await file.arrayBuffer();
  return inspectVaultArchiveBytes(new Uint8Array(buffer), file.name);
}

export function inspectVaultArchiveText(rawText: string, sourceLabel: string): RestorePreview {
  const trimmed = (rawText || '').trim();
  if (!trimmed) {
    throw new Error('Empty backup input. Select a valid ExamSet vault or paper JSON file.');
  }
  if (trimmed.length > MAX_JSON_IMPORT_BYTES) {
    throw new Error('JSON backup text exceeds safe 64 MB size limit.');
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    throw new Error('Invalid JSON syntax. Unable to inspect backup file.');
  }

  // Case 1: Native Vault Archive v5
  if (
    parsed &&
    typeof parsed === 'object' &&
    'manifest' in parsed &&
    'sections' in parsed
  ) {
    const doc = parsed as VaultArchiveDocument;
    if (doc.manifest?.format !== 'examset-vault-v5' || !doc.sections) {
      throw new Error('Unrecognized vault archive structure.');
    }

    const expectedChecksums = doc.manifest.entryChecksumsSha256 || {};
    const sectionsObj = doc.sections as unknown as Record<string, unknown>;
    const requiredSections = [
      'papers',
      'folders',
      'attempts',
      'countdowns',
      'studyNotes',
      'vocabulary',
      'presetsAndDictation',
      'plannerAndAnalytics',
      'brainAndPreferences',
    ] as const;
    const requiredSet = new Set<string>(requiredSections);
    let verifiedCount = 0;
    const mismatches: string[] = [];

    // [DEPRECATED 2026-11-24] Reason: Iterating only Object.entries(doc.sections) silently skipped missing required sections and treated empty checksums identically to missing ones. Restore if: Partial section-only archives are explicitly supported.
    // for (const [sectionKey, sectionValue] of Object.entries(doc.sections)) { ... }
    for (const sectionKey of requiredSections) {
      if (!Object.prototype.hasOwnProperty.call(sectionsObj, sectionKey) || sectionsObj[sectionKey] === undefined) {
        mismatches.push(`${sectionKey} (missing section)`);
        continue;
      }
      const expectedRaw = expectedChecksums[sectionKey];
      const expected = typeof expectedRaw === 'string' ? expectedRaw.trim() : '';
      if (!expected) {
        mismatches.push(`${sectionKey} (missing checksum)`);
        continue;
      }
      const actual = sha256HexSync(JSON.stringify(sectionsObj[sectionKey]));
      if (actual.toLowerCase() !== expected.toLowerCase()) {
        mismatches.push(`${sectionKey} (hash mismatch)`);
      } else {
        verifiedCount++;
      }
    }

    for (const extraKey of Object.keys(sectionsObj)) {
      if (requiredSet.has(extraKey)) continue;
      const expectedRaw = expectedChecksums[extraKey];
      const expected = typeof expectedRaw === 'string' ? expectedRaw.trim() : '';
      if (!expected) {
        mismatches.push(`${extraKey} (missing checksum)`);
        continue;
      }
      const actual = sha256HexSync(JSON.stringify(sectionsObj[extraKey]));
      if (actual.toLowerCase() !== expected.toLowerCase()) {
        mismatches.push(`${extraKey} (hash mismatch)`);
      } else {
        verifiedCount++;
      }
    }

    const allVerified = mismatches.length === 0 && verifiedCount >= requiredSections.length;
    const checksumDetails = allVerified
      ? `SHA-256 verified across all ${verifiedCount} archive sections.`
      : `Verification warning on: ${mismatches.join(', ')}.`;

    const recomputedCounts: BackupCounts = {
      papersCount: Array.isArray(doc.sections.papers) ? doc.sections.papers.length : 0,
      notesCount: Array.isArray(doc.sections.studyNotes) ? doc.sections.studyNotes.length : 0,
      knowledgeCoursesCount: Object.keys(
        normalizeCourseMapForBackup(doc.sections.brainAndPreferences?.knowledgeCourses || {})
      ).length,
      foldersCount: Array.isArray(doc.sections.folders) ? doc.sections.folders.length : 0,
      attemptsCount: Array.isArray(doc.sections.attempts) ? doc.sections.attempts.length : 0,
      analyticsDaysCount: doc.sections.plannerAndAnalytics?.dailyAnalytics
        ? Object.keys(doc.sections.plannerAndAnalytics.dailyAnalytics).length
        : 0,
      vocabDecksCount: doc.sections.vocabulary?.decks?.length || 0,
      vocabWordsCount: (doc.sections.vocabulary?.decks || []).reduce(
        (s, d) => s + (d.entries?.length || 0),
        0
      ),
      promptPresetsCount: doc.sections.presetsAndDictation?.promptPresets?.length || 0,
      todosCount: doc.sections.plannerAndAnalytics?.studyTodos?.length || 0,
      countdownsCount: Array.isArray(doc.sections.countdowns) ? doc.sections.countdowns.length : 0,
    };

    return {
      sourceLabel,
      isLegacyJson: false,
      checksumVerified: allVerified,
      checksumDetails,
      manifest: {
        ...doc.manifest,
        counts: recomputedCounts,
      },
      rawJson: trimmed,
    };
  }

  // Case 2: Legacy Paper Bundle JSON ({ papers: [...] } | QuestionPaper[] | QuestionPaper)
  let legacyPapers: QuestionPaper[] = [];
  if (Array.isArray(parsed)) {
    legacyPapers = parsed.filter(
      (p) => p && typeof p === 'object' && 'questions' in p && Array.isArray(p.questions)
    ) as QuestionPaper[];
  } else if (parsed && typeof parsed === 'object') {
    const obj = parsed as Record<string, unknown>;
    if (Array.isArray(obj.papers)) {
      legacyPapers = obj.papers as QuestionPaper[];
    } else if (Array.isArray(obj.questions)) {
      legacyPapers = [parsed as QuestionPaper];
    }
  }

  if (legacyPapers.length === 0) {
    throw new Error('This file is neither an ExamSet Vault Archive nor a Question Paper JSON bundle.');
  }

  return {
    sourceLabel,
    isLegacyJson: true,
    checksumVerified: true,
    checksumDetails: `Legacy Question Paper JSON (${legacyPapers.length} paper(s))`,
    manifest: {
      format: 'examset-legacy-json',
      schemaVersion: 1,
      appVersion: 'Legacy JSON',
      createdAt: Date.now(),
      deviceName: 'External JSON Export',
      counts: {
        papersCount: legacyPapers.length,
        notesCount: 0,
        foldersCount: 0,
        attemptsCount: 0,
        analyticsDaysCount: 0,
        vocabDecksCount: 0,
        vocabWordsCount: 0,
        promptPresetsCount: 0,
        todosCount: 0,
        countdownsCount: 0,
      },
      entryChecksumsSha256: {},
    },
    rawJson: trimmed,
  };
}

function mergeSrsEntry(
  a: FlashcardSrsState | undefined,
  b: FlashcardSrsState | undefined
): FlashcardSrsState | undefined {
  if (!a) return b;
  if (!b) return a;
  if (b.reviewCount > a.reviewCount) return b;
  if (a.reviewCount > b.reviewCount) return a;
  return b.lastReviewedAtMillis >= a.lastReviewedAtMillis ? b : a;
}

export function applyVaultRestoreToState(
  current: WorkspaceBackupState,
  preview: RestorePreview,
  mode: RestoreMode,
  defaultRootFolder: FolderItem
): WorkspaceBackupState {
  const parsed = JSON.parse(preview.rawJson) as unknown;

  // Legacy Question Paper JSON restore
  if (preview.isLegacyJson) {
    let incomingPapers: QuestionPaper[] = [];
    if (Array.isArray(parsed)) {
      incomingPapers = parsed as QuestionPaper[];
    } else if (parsed && typeof parsed === 'object') {
      const obj = parsed as Record<string, unknown>;
      if (Array.isArray(obj.papers)) incomingPapers = obj.papers as QuestionPaper[];
      else if (Array.isArray(obj.questions)) incomingPapers = [parsed as QuestionPaper];
    }
    if (mode === 'FULL_REPLACE') {
      return {
        ...current,
        papers: incomingPapers.length > 0 ? incomingPapers : current.papers,
      };
    }
    const map = new Map<string, QuestionPaper>();
    for (const p of current.papers) map.set(p.metadata.id, p);
    for (const inc of incomingPapers) {
      const ex = map.get(inc.metadata.id);
      if (!ex || (inc.metadata.updatedAt || inc.metadata.createdAt) >= (ex.metadata.updatedAt || ex.metadata.createdAt)) {
        map.set(inc.metadata.id, {
          ...inc,
          metadata: { ...inc.metadata, folderId: inc.metadata.folderId || 'default' },
        });
      }
    }
    return { ...current, papers: Array.from(map.values()) };
  }

  const doc = parsed as VaultArchiveDocument;
  const s = doc.sections;

  if (mode === 'FULL_REPLACE') {
    const folders = Array.isArray(s.folders) && s.folders.length > 0 ? [...s.folders] : [defaultRootFolder];
    if (!folders.some((f) => f.id === 'default')) folders.unshift(defaultRootFolder);
    const folderIds = new Set(folders.map((f) => f.id));
    const papers = (s.papers || []).map((p) => ({
      ...p,
      metadata: {
        ...p.metadata,
        folderId: p.metadata.folderId && folderIds.has(p.metadata.folderId) ? p.metadata.folderId : 'default',
      },
    }));

    return {
      papers,
      folders,
      attempts: Array.isArray(s.attempts) ? s.attempts : [],
      activeSessions: s.plannerAndAnalytics?.activeSessions || {},
      countdowns: Array.isArray(s.countdowns) ? s.countdowns : [],
      studyNotes: (Array.isArray(s.studyNotes) ? s.studyNotes : []).map((n) => {
        const norm = normalizeStudyNoteForBackup(n);
        return {
          ...norm,
          folderId: norm.folderId && folderIds.has(norm.folderId) ? norm.folderId : 'default',
        };
      }),
      knowledgeCourses: normalizeCourseMapForBackup(s.brainAndPreferences?.knowledgeCourses || {}),
      vocabDecks: s.vocabulary?.decks || [],
      vocabSrs: s.vocabulary?.srs || {},
      englishVariant: s.vocabulary?.englishVariant || 'UK',
      headwordInbox: s.vocabulary?.headwordInbox || [],
      dismissedHeadwords: s.vocabulary?.dismissedHeadwords || [],
      wotdStates: s.vocabulary?.wotdStates || {},
      promptPresets: s.presetsAndDictation?.promptPresets || [],
      dictationConfig: s.presetsAndDictation?.dictationConfig || current.dictationConfig,
      studyTodos: s.plannerAndAnalytics?.studyTodos || [],
      pomodoroConfig: { ...DEFAULT_POMODORO_CONFIG, ...(s.plannerAndAnalytics?.pomodoroConfig || {}) },
      dailyAnalytics: s.plannerAndAnalytics?.dailyAnalytics || {},
      brainLabStats: { ...EMPTY_BRAIN_LAB_STATS, ...(s.brainAndPreferences?.brainLabStats || {}) },
      brainLabSettings: { ...DEFAULT_BRAIN_LAB_SETTINGS, ...(s.brainAndPreferences?.brainLabSettings || {}) },
      dismissedTips: s.brainAndPreferences?.dismissedTips || [],
      themeMode: s.brainAndPreferences?.themeMode || current.themeMode,
      colorPreset: s.brainAndPreferences?.colorPreset || current.colorPreset,
      fontScaleMode: s.brainAndPreferences?.fontScaleMode || current.fontScaleMode,
    };
  }

  // SMART_MERGE
  const folderMap = new Map<string, FolderItem>();
  for (const f of current.folders) folderMap.set(f.id, f);
  for (const f of s.folders || []) {
    if (!folderMap.has(f.id)) folderMap.set(f.id, f);
  }
  if (!folderMap.has('default')) folderMap.set('default', defaultRootFolder);
  const mergedFolders = Array.from(folderMap.values());
  const validFolderIds = new Set(mergedFolders.map((f) => f.id));

  const paperMap = new Map<string, QuestionPaper>();
  for (const p of current.papers) paperMap.set(p.metadata.id, p);
  for (const inc of s.papers || []) {
    const safeInc: QuestionPaper = {
      ...inc,
      metadata: {
        ...inc.metadata,
        folderId: inc.metadata.folderId && validFolderIds.has(inc.metadata.folderId) ? inc.metadata.folderId : 'default',
      },
    };
    const ex = paperMap.get(safeInc.metadata.id);
    if (
      !ex ||
      (safeInc.metadata.updatedAt || safeInc.metadata.createdAt) >
        (ex.metadata.updatedAt || ex.metadata.createdAt)
    ) {
      paperMap.set(safeInc.metadata.id, safeInc);
    }
  }

  // Attempts deduplication by id and fingerprint
  const attemptByKey = new Map<string, AttemptRecord>();
  const addAttempt = (a: AttemptRecord) => {
    const fp = `${a.paperId}_${a.submittedAt}_${Math.round(a.score * 100)}`;
    if (!attemptByKey.has(fp)) attemptByKey.set(fp, a);
  };
  for (const a of current.attempts) addAttempt(a);
  for (const a of s.attempts || []) addAttempt(a);
  const mergedAttempts = Array.from(attemptByKey.values()).sort((a, b) => b.submittedAt - a.submittedAt);

  // Countdowns union by title+date+time+timezoneMode
  const countdownMap = new Map<string, ExamCountdownTarget>();
  for (const cd of current.countdowns) {
    countdownMap.set(
      `${cd.title.trim().toLowerCase()}|${cd.targetDateIso}|${cd.targetTime || ''}|${cd.timezoneMode || 'LOCAL'}`,
      cd
    );
  }
  for (const cd of s.countdowns || []) {
    const k = `${cd.title.trim().toLowerCase()}|${cd.targetDateIso}|${cd.targetTime || ''}|${cd.timezoneMode || 'LOCAL'}`;
    if (!countdownMap.has(k)) countdownMap.set(k, cd);
  }

  // Study Notes merge (taking newer rawContent + unioning bookmarks/mastery + merging SRS + preserving course)
  const noteMap = new Map<string, StudyNoteRecord>();
  for (const n of current.studyNotes) noteMap.set(n.id, normalizeStudyNoteForBackup(n));
  for (const rawInc of s.studyNotes || []) {
    const rawNorm = normalizeStudyNoteForBackup(rawInc);
    const inc: StudyNoteRecord = {
      ...rawNorm,
      folderId:
        rawNorm.folderId && validFolderIds.has(rawNorm.folderId) ? rawNorm.folderId : 'default',
    };
    const ex = noteMap.get(inc.id);
    if (!ex) {
      noteMap.set(inc.id, inc);
    } else {
      const mergedBookmarks = Array.from(
        new Set([...(ex.bookmarkedIndices || []), ...(inc.bookmarkedIndices || [])])
      );
      const mergedMastered = Array.from(
        new Set([...(ex.masteredIndices || []), ...(inc.masteredIndices || [])])
      );
      const mergedSrs: Record<number, FlashcardSrsState> = { ...(ex.srsStates || {}) };
      for (const [kStr, v] of Object.entries(inc.srsStates || {})) {
        const k = Number(kStr);
        const best = mergeSrsEntry(mergedSrs[k], v);
        if (best) mergedSrs[k] = best;
      }
      const preferIncoming = (inc.updatedAt || 0) >= (ex.updatedAt || 0);
      noteMap.set(inc.id, {
        ...(preferIncoming ? inc : ex),
        course: preferIncoming
          ? (inc.course || ex.course || '')
          : (ex.course || inc.course || ''),
        folderId: preferIncoming ? inc.folderId : ex.folderId,
        bookmarkedIndices: mergedBookmarks,
        masteredIndices: mergedMastered,
        srsStates: mergedSrs,
      });
    }
  }
  const mergedKnowledgeCourses = mergeCourseMapsForRestore(
    current.knowledgeCourses || {},
    normalizeCourseMapForBackup(s.brainAndPreferences?.knowledgeCourses || {})
  );

  // Vocab Decks & SRS canonical bucket union (`language|levelCode|customLevelLabel`) + ID remapping
  const canonicalBucketKey = (d: VocabularyDeck) =>
    `${(d.language || 'English').trim().toLowerCase()}|${(d.levelCode || 'B2').trim().toLowerCase()}|${(d.customLevelLabel || '').trim().toLowerCase()}`;

  const mergeStringListUnion = (a: string[] = [], b: string[] = []): string[] => {
    const out: string[] = [];
    const seen = new Set<string>();
    for (const raw of [...a, ...b]) {
      const clean = String(raw || '').trim();
      const key = clean.toLowerCase();
      if (clean && !seen.has(key)) {
        seen.add(key);
        out.push(clean);
      }
    }
    return out;
  };

  const mergeExamplesUnion = (a: VocabularyExample[] = [], b: VocabularyExample[] = []): VocabularyExample[] => {
    const out: VocabularyExample[] = [];
    const seen = new Set<string>();
    for (const exItem of [...a, ...b]) {
      const sent = String(exItem?.sentence || '').trim();
      const key = sent.toLowerCase();
      if (sent && !seen.has(key)) {
        seen.add(key);
        out.push(exItem);
      }
    }
    return out;
  };

  const deckList: VocabularyDeck[] = [];
  const deckIdxById = new Map<string, number>();
  const deckIdxByBucket = new Map<string, number>();
  const deckIdRemap = new Map<string, string>();
  const entryIdRemap = new Map<string, string>();

  for (const d of current.vocabDecks) {
    const idx = deckList.length;
    deckList.push(d);
    deckIdxById.set(d.id, idx);
    const bKey = canonicalBucketKey(d);
    if (!deckIdxByBucket.has(bKey)) deckIdxByBucket.set(bKey, idx);
  }

  for (const incDeck of s.vocabulary?.decks || []) {
    const bKey = canonicalBucketKey(incDeck);
    const matchIdx = deckIdxById.get(incDeck.id) ?? deckIdxByBucket.get(bKey);
    if (matchIdx === undefined) {
      const idx = deckList.length;
      deckList.push(incDeck);
      deckIdxById.set(incDeck.id, idx);
      deckIdxByBucket.set(bKey, idx);
    } else {
      const ex = deckList[matchIdx];
      deckIdRemap.set(incDeck.id, ex.id);
      const wordMap = new Map<string, VocabularyEntry>();
      for (const e of ex.entries) {
        wordMap.set(`${e.word.trim().toLowerCase()}|${e.partOfSpeech.trim().toLowerCase()}`, e);
      }
      for (const incEntry of incDeck.entries || []) {
        const wKey = `${incEntry.word.trim().toLowerCase()}|${incEntry.partOfSpeech.trim().toLowerCase()}`;
        const existingEntry = wordMap.get(wKey);
        if (!existingEntry) {
          wordMap.set(wKey, incEntry);
        } else {
          entryIdRemap.set(incEntry.id, existingEntry.id);
          const preferInc = (incEntry.updatedAt || 0) > (existingEntry.updatedAt || 0);
          wordMap.set(wKey, {
            ...(preferInc ? incEntry : existingEntry),
            id: existingEntry.id,
            examples: mergeExamplesUnion(existingEntry.examples, incEntry.examples),
            synonyms: mergeStringListUnion(existingEntry.synonyms, incEntry.synonyms),
            antonyms: mergeStringListUnion(existingEntry.antonyms, incEntry.antonyms),
            collocations: mergeStringListUnion(existingEntry.collocations, incEntry.collocations),
            isBookmarked: Boolean(existingEntry.isBookmarked || incEntry.isBookmarked),
          });
        }
      }
      deckList[matchIdx] = {
        ...(incDeck.updatedAt > ex.updatedAt ? incDeck : ex),
        id: ex.id,
        entries: Array.from(wordMap.values()),
      };
    }
  }

  const mergedVocabSrs: Record<string, Record<string, FlashcardSrsState>> = {};
  for (const [deckId, map] of Object.entries(current.vocabSrs)) {
    mergedVocabSrs[deckId] = { ...map };
  }
  for (const [rawDeckId, incMap] of Object.entries(s.vocabulary?.srs || {})) {
    const resolvedDeckId = deckIdRemap.get(rawDeckId) || rawDeckId;
    const target = mergedVocabSrs[resolvedDeckId] || {};
    for (const [rawWordId, st] of Object.entries(incMap)) {
      const resolvedWordId = entryIdRemap.get(rawWordId) || rawWordId;
      const best = mergeSrsEntry(target[resolvedWordId], st);
      if (best) target[resolvedWordId] = best;
    }
    mergedVocabSrs[resolvedDeckId] = target;
  }

  // Headword Inbox union
  const inboxMap = new Map<string, HeadwordQueueItem>();
  for (const h of current.headwordInbox) inboxMap.set(`${h.language.toLowerCase()}|${h.word.toLowerCase()}`, h);
  for (const h of s.vocabulary?.headwordInbox || []) {
    const k = `${h.language.toLowerCase()}|${h.word.toLowerCase()}`;
    if (!inboxMap.has(k)) inboxMap.set(k, h);
  }

  // Prompt Presets union
  const presetMap = new Map<string, PromptPreset>();
  for (const p of current.promptPresets) presetMap.set(p.id, p);
  for (const p of s.presetsAndDictation?.promptPresets || []) {
    const ex = presetMap.get(p.id);
    if (!ex || p.updatedAt > ex.updatedAt) presetMap.set(p.id, p);
  }

  // Study Todos union
  const todoMap = new Map<string, StudyTodoItem>();
  for (const t of current.studyTodos) todoMap.set(t.id, t);
  for (const t of s.plannerAndAnalytics?.studyTodos || []) {
    const ex = todoMap.get(t.id);
    if (!ex || t.completedPomodoros > ex.completedPomodoros || (t.isCompleted && !ex.isCompleted)) {
      todoMap.set(t.id, t);
    }
  }

  // Daily Analytics max-per-day merge
  const mergedAnalytics: Record<string, DailyStudyAnalytics> = { ...current.dailyAnalytics };
  for (const [dateStr, incDay] of Object.entries(s.plannerAndAnalytics?.dailyAnalytics || {})) {
    const ex = mergedAnalytics[dateStr];
    if (!ex) {
      mergedAnalytics[dateStr] = incDay;
    } else {
      mergedAnalytics[dateStr] = {
        dateStr,
        testsCompleted: Math.max(ex.testsCompleted, incDay.testsCompleted),
        questionsAnswered: Math.max(ex.questionsAnswered, incDay.questionsAnswered),
        correctAnswers: Math.max(ex.correctAnswers, incDay.correctAnswers),
        studyTimeSeconds: Math.max(ex.studyTimeSeconds, incDay.studyTimeSeconds),
        flashcardsReviewed: Math.max(ex.flashcardsReviewed, incDay.flashcardsReviewed),
        vocabWordsReviewed: Math.max(ex.vocabWordsReviewed, incDay.vocabWordsReviewed),
      };
    }
  }

  const mergedActiveSessions = {
    ...(s.plannerAndAnalytics?.activeSessions || {}),
    ...(current.activeSessions || {}),
  };

  return {
    papers: Array.from(paperMap.values()),
    folders: mergedFolders,
    attempts: mergedAttempts,
    activeSessions: mergedActiveSessions,
    countdowns: Array.from(countdownMap.values()),
    studyNotes: Array.from(noteMap.values()).sort((a, b) => b.updatedAt - a.updatedAt),
    knowledgeCourses: mergedKnowledgeCourses,
    vocabDecks: deckList,
    vocabSrs: mergedVocabSrs,
    englishVariant: current.englishVariant || s.vocabulary?.englishVariant || 'UK',
    headwordInbox: Array.from(inboxMap.values()),
    dismissedHeadwords: Array.from(
      new Set([...current.dismissedHeadwords, ...(s.vocabulary?.dismissedHeadwords || [])])
    ),
    wotdStates: { ...(s.vocabulary?.wotdStates || {}), ...current.wotdStates },
    promptPresets: Array.from(presetMap.values()),
    dictationConfig: current.dictationConfig,
    studyTodos: Array.from(todoMap.values()),
    pomodoroConfig: current.pomodoroConfig,
    dailyAnalytics: mergedAnalytics,
    brainLabStats: mergeBrainLabStats(
      current.brainLabStats,
      s.brainAndPreferences?.brainLabStats || EMPTY_BRAIN_LAB_STATS
    ),
    brainLabSettings: current.brainLabSettings,
    dismissedTips: Array.from(
      new Set([...current.dismissedTips, ...(s.brainAndPreferences?.dismissedTips || [])])
    ),
    themeMode: current.themeMode,
    colorPreset: current.colorPreset,
    fontScaleMode: current.fontScaleMode,
  };
}

export function buildPromptPresetsPackJson(presets: PromptPreset[]): string {
  return JSON.stringify(
    {
      packType: 'examset_prompt_presets_pack',
      schemaVersion: 1,
      exportedAt: Date.now(),
      presetsCount: presets.length,
      presets,
    },
    null,
    2
  );
}

export function parsePromptPresetsPackJson(
  rawJson: string,
  existing: PromptPreset[]
): { merged: PromptPreset[]; added: number; updated: number } {
  const trimmed = (rawJson || '').trim();
  if (!trimmed) {
    throw new Error('Selected preset pack file is empty.');
  }
  const parsed = JSON.parse(trimmed) as unknown;
  let incoming: PromptPreset[] = [];
  if (Array.isArray(parsed)) {
    incoming = parsed as PromptPreset[];
  } else if (parsed && typeof parsed === 'object') {
    const obj = parsed as Record<string, unknown>;
    if (Array.isArray(obj.presets)) {
      incoming = obj.presets as PromptPreset[];
    } else if (typeof obj.promptPresetsJson === 'string') {
      incoming = JSON.parse(obj.promptPresetsJson) as PromptPreset[];
    }
  }
  const validIncoming = incoming.filter(
    (p) => p && typeof p.id === 'string' && typeof p.name === 'string' && typeof p.mode === 'string'
  );
  if (validIncoming.length === 0) {
    throw new Error('No valid Prompt Presets found in selected file.');
  }

  const map = new Map<string, PromptPreset>();
  for (const p of existing) map.set(p.id, p);
  let added = 0;
  let updated = 0;
  for (const inc of validIncoming) {
    const ex = map.get(inc.id);
    if (!ex) {
      map.set(inc.id, inc);
      added++;
    } else if ((inc.updatedAt || 0) > (ex.updatedAt || 0)) {
      map.set(inc.id, inc);
      updated++;
    }
  }
  return { merged: Array.from(map.values()), added, updated };
}
