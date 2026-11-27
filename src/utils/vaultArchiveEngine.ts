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

export const MAX_TEXT_BACKUP_BYTES = 32 * 1024 * 1024;
export const MAX_ZIP_BACKUP_BYTES = 64 * 1024 * 1024;

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

// Pure synchronous FIPS 180-4 SHA-256 implementation so checksums work
// identically across HTTPS browsers, localhost, file://, and Node tests.
const SHA256_K = [
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
];

function rotr(x: number, n: number): number {
  return (x >>> n) | (x << (32 - n));
}

function encodeUtf8Bytes(input: string): number[] {
  if (typeof TextEncoder !== 'undefined') {
    return Array.from(new TextEncoder().encode(input));
  }
  const bytes: number[] = [];
  for (let i = 0; i < input.length; i++) {
    let code = input.charCodeAt(i);
    if (code < 0x80) {
      bytes.push(code);
    } else if (code < 0x800) {
      bytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
    } else if (code < 0xd800 || code >= 0xe000) {
      bytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
    } else {
      i++;
      code = 0x10000 + (((code & 0x3ff) << 10) | (input.charCodeAt(i) & 0x3ff));
      bytes.push(
        0xf0 | (code >> 18),
        0x80 | ((code >> 12) & 0x3f),
        0x80 | ((code >> 6) & 0x3f),
        0x80 | (code & 0x3f)
      );
    }
  }
  return bytes;
}

export function sha256HexSync(input: string): string {
  return sha256HexBytesSync(encodeUtf8Bytes(input));
}

export function sha256HexBytesSync(bytes: Uint8Array | number[]): string {
  const rawLen = bytes.length;
  const msg = new Array<number>(rawLen);
  for (let i = 0; i < rawLen; i++) {
    msg[i] = bytes[i] & 0xff;
  }
  const bitLen = rawLen * 8;
  msg.push(0x80);
  while ((msg.length % 64) !== 56) {
    msg.push(0);
  }
  const highBits = Math.floor(bitLen / 0x100000000);
  const lowBits = bitLen >>> 0;
  for (let s = 24; s >= 0; s -= 8) msg.push((highBits >>> s) & 0xff);
  for (let s = 24; s >= 0; s -= 8) msg.push((lowBits >>> s) & 0xff);

  let h0 = 0x6a09e667;
  let h1 = 0xbb67ae85;
  let h2 = 0x3c6ef372;
  let h3 = 0xa54ff53a;
  let h4 = 0x510e527f;
  let h5 = 0x9b05688c;
  let h6 = 0x1f83d9ab;
  let h7 = 0x5be0cd19;

  const w = new Array<number>(64).fill(0);
  for (let chunk = 0; chunk < msg.length; chunk += 64) {
    for (let i = 0; i < 16; i++) {
      const off = chunk + i * 4;
      w[i] =
        ((msg[off] << 24) | (msg[off + 1] << 16) | (msg[off + 2] << 8) | msg[off + 3]) >>> 0;
    }
    for (let i = 16; i < 64; i++) {
      const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3);
      const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
    }

    let a = h0;
    let b = h1;
    let c = h2;
    let d = h3;
    let e = h4;
    let f = h5;
    let g = h6;
    let h = h7;

    for (let i = 0; i < 64; i++) {
      const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
      const ch = (e & f) ^ (~e & g);
      const temp1 = (h + S1 + ch + SHA256_K[i] + w[i]) >>> 0;
      const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const temp2 = (S0 + maj) >>> 0;

      h = g;
      g = f;
      f = e;
      e = (d + temp1) >>> 0;
      d = c;
      c = b;
      b = a;
      a = (temp1 + temp2) >>> 0;
    }

    h0 = (h0 + a) >>> 0;
    h1 = (h1 + b) >>> 0;
    h2 = (h2 + c) >>> 0;
    h3 = (h3 + d) >>> 0;
    h4 = (h4 + e) >>> 0;
    h5 = (h5 + f) >>> 0;
    h6 = (h6 + g) >>> 0;
    h7 = (h7 + h) >>> 0;
  }

  return [h0, h1, h2, h3, h4, h5, h6, h7]
    .map((n) => (n >>> 0).toString(16).padStart(8, '0'))
    .join('');
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
      activeSessions: state.activeSessions || {},
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

export function inspectVaultArchiveText(rawText: string, sourceLabel: string): RestorePreview {
  const trimmed = (rawText || '').trim();
  if (!trimmed) {
    throw new Error('Empty backup input. Select a valid ExamSet vault or paper JSON file.');
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

const VALID_THEME_MODES = new Set<ThemeMode>(['LIGHT', 'DARK', 'SYSTEM']);
const VALID_COLOR_PRESETS = new Set<ColorSchemePreset>([
  'NORDIC_SLATE',
  'WARM_PAPER',
  'FOREST_SAGE',
  'ROYAL_INDIGO',
  'OLED_OBSIDIAN',
]);
const VALID_FONT_SCALE_MODES = new Set<AppFontScaleMode>([
  'COMPACT',
  'STANDARD_FIXED',
  'LARGE',
  'EXTRA_LARGE',
  'FOLLOW_SYSTEM',
]);

function canonicalVocabBucketKey(deck: VocabularyDeck): string {
  const lang = (deck.language || 'English').trim().toLowerCase() || 'english';
  const level = (deck.levelCode || 'CUSTOM').trim().toUpperCase();
  const custom = level === 'CUSTOM' ? (deck.customLevelLabel || '').trim().toLowerCase() : '';
  return `${lang}|${level}|${custom}`;
}

function normalizedVocabWordKey(entry: VocabularyDeck['entries'][number]): string {
  const word = (entry.word || '').trim().toLowerCase();
  const pos = (entry.partOfSpeech || '').trim().toLowerCase();
  return word ? `${word}|${pos}` : entry.id;
}

function mergeVocabEntryForRestore(
  existing: VocabularyDeck['entries'][number],
  incoming: VocabularyDeck['entries'][number]
): VocabularyDeck['entries'][number] {
  const preferIncoming = (incoming.updatedAt || 0) > (existing.updatedAt || 0);

  const seenExamples = new Set<string>();
  const mergedExamples: VocabularyDeck['entries'][number]['examples'] = [];
  for (const ex of [...(existing.examples || []), ...(incoming.examples || [])]) {
    const sent = (ex.sentence || '').trim();
    const key = sent.toLowerCase();
    if (sent && !seenExamples.has(key)) {
      seenExamples.add(key);
      mergedExamples.push(ex);
    }
  }

  const dedupStrings = (a: string[] = [], b: string[] = []): string[] => {
    const seen = new Set<string>();
    const out: string[] = [];
    for (const raw of [...a, ...b]) {
      const trimmed = String(raw || '').trim();
      const k = trimmed.toLowerCase();
      if (trimmed && !seen.has(k)) {
        seen.add(k);
        out.push(trimmed);
      }
    }
    return out;
  };

  const pickScalar = (incVal: string | undefined, exVal: string | undefined): string => {
    const incClean = (incVal || '').trim();
    const exClean = (exVal || '').trim();
    if (preferIncoming && incClean) return incVal || '';
    return exClean ? exVal || '' : incVal || '';
  };

  return {
    ...existing,
    pronunciation: pickScalar(incoming.pronunciation, existing.pronunciation),
    partOfSpeech: pickScalar(incoming.partOfSpeech, existing.partOfSpeech),
    meaning: pickScalar(incoming.meaning, existing.meaning),
    translation: pickScalar(incoming.translation, existing.translation),
    translationLanguage: (existing.translationLanguage || '').trim()
      ? existing.translationLanguage
      : incoming.translationLanguage || '',
    usageNote: pickScalar(incoming.usageNote, existing.usageNote),
    examples: mergedExamples,
    synonyms: dedupStrings(existing.synonyms, incoming.synonyms),
    antonyms: dedupStrings(existing.antonyms, incoming.antonyms),
    collocations: dedupStrings(existing.collocations, incoming.collocations),
    isBookmarked: Boolean(existing.isBookmarked || incoming.isBookmarked),
    updatedAt: Math.max(existing.updatedAt || 0, incoming.updatedAt || 0),
  };
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
    const studyNotes = (Array.isArray(s.studyNotes) ? s.studyNotes : []).map((n) =>
      normalizeStudyNoteForBackup({
        ...n,
        folderId: n.folderId && folderIds.has(n.folderId) ? n.folderId : 'default',
      })
    );
    const rawSessions = s.plannerAndAnalytics?.activeSessions;
    const activeSessions: Record<string, ActiveExamSession> =
      rawSessions && typeof rawSessions === 'object' && !Array.isArray(rawSessions)
        ? { ...rawSessions }
        : {};
    const incTheme = s.brainAndPreferences?.themeMode;
    const incPreset = s.brainAndPreferences?.colorPreset;
    const incFontScale = s.brainAndPreferences?.fontScaleMode;

    return {
      papers,
      folders,
      attempts: Array.isArray(s.attempts) ? s.attempts : [],
      activeSessions,
      countdowns: Array.isArray(s.countdowns) ? s.countdowns : [],
      studyNotes,
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
      themeMode:
        incTheme && VALID_THEME_MODES.has(incTheme)
          ? incTheme
          : VALID_THEME_MODES.has(current.themeMode)
            ? current.themeMode
            : 'LIGHT',
      colorPreset:
        incPreset && VALID_COLOR_PRESETS.has(incPreset)
          ? incPreset
          : VALID_COLOR_PRESETS.has(current.colorPreset)
            ? current.colorPreset
            : 'NORDIC_SLATE',
      fontScaleMode:
        incFontScale && VALID_FONT_SCALE_MODES.has(incFontScale)
          ? incFontScale
          : VALID_FONT_SCALE_MODES.has(current.fontScaleMode)
            ? current.fontScaleMode
            : 'STANDARD_FIXED',
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

  // Active exam sessions merge (per paperId, preserve local or take incoming with more progress)
  const mergedActiveSessions: Record<string, ActiveExamSession> = {
    ...(current.activeSessions || {}),
  };
  for (const [paperId, incSess] of Object.entries(s.plannerAndAnalytics?.activeSessions || {})) {
    if (!incSess || typeof incSess !== 'object') continue;
    const exSess = mergedActiveSessions[paperId];
    if (
      !exSess ||
      (incSess.startedAt || 0) > (exSess.startedAt || 0) ||
      ((incSess.startedAt || 0) === (exSess.startedAt || 0) &&
        (incSess.elapsedSeconds || 0) > (exSess.elapsedSeconds || 0))
    ) {
      mergedActiveSessions[paperId] = incSess;
    }
  }

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

  // Study Notes merge (taking newer rawContent + unioning bookmarks/mastery + merging SRS + preserving course + validating folderId)
  const noteMap = new Map<string, StudyNoteRecord>();
  for (const n of current.studyNotes) {
    noteMap.set(
      n.id,
      normalizeStudyNoteForBackup({
        ...n,
        folderId: n.folderId && validFolderIds.has(n.folderId) ? n.folderId : 'default',
      })
    );
  }
  for (const rawInc of s.studyNotes || []) {
    const inc = normalizeStudyNoteForBackup({
      ...rawInc,
      folderId: rawInc.folderId && validFolderIds.has(rawInc.folderId) ? rawInc.folderId : 'default',
    });
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

  // [DEPRECATED 2026-11-27] Reason: Matching vocabulary decks only by id and skipping existing word|partOfSpeech entries causes cross-device duplicate decks and drops entry edits/examples/bookmarks. Restore if: Strict deck-ID-only non-overwriting merge is explicitly needed.
  // const deckMap = new Map<string, VocabularyDeck>();
  // for (const d of current.vocabDecks) deckMap.set(d.id, d);
  // for (const incDeck of s.vocabulary?.decks || []) { ... }
  // const mergedVocabSrs: Record<string, Record<string, FlashcardSrsState>> = {};
  // for (const [deckId, incMap] of Object.entries(s.vocabulary?.srs || {})) { ... }

  // Canonical Vocab Decks (matching by id OR language|levelCode|customLevelLabel) & SRS remap
  const mergedDecks = current.vocabDecks.map((d) => ({ ...d, entries: [...(d.entries || [])] }));
  const deckIdRemap = new Map<string, string>();
  const entryIdRemap = new Map<string, string>();

  for (const incDeck of s.vocabulary?.decks || []) {
    const incBucket = canonicalVocabBucketKey(incDeck);
    const matchIdx = mergedDecks.findIndex(
      (ex) => ex.id === incDeck.id || canonicalVocabBucketKey(ex) === incBucket
    );
    if (matchIdx < 0) {
      mergedDecks.push({ ...incDeck, entries: [...(incDeck.entries || [])] });
      deckIdRemap.set(incDeck.id, incDeck.id);
    } else {
      const exDeck = mergedDecks[matchIdx];
      deckIdRemap.set(incDeck.id, exDeck.id);
      const entries = [...(exDeck.entries || [])];
      const idxByWordKey = new Map<string, number>();
      entries.forEach((entry, idx) => {
        const key = normalizedVocabWordKey(entry);
        if (!idxByWordKey.has(key)) idxByWordKey.set(key, idx);
      });

      for (const incEntry of incDeck.entries || []) {
        const key = normalizedVocabWordKey(incEntry);
        let existingEntryIdx = idxByWordKey.get(key);
        if (existingEntryIdx === undefined) {
          const byIdIdx = entries.findIndex((e) => e.id === incEntry.id);
          if (byIdIdx >= 0) existingEntryIdx = byIdIdx;
        }
        if (existingEntryIdx === undefined) {
          entries.push(incEntry);
          idxByWordKey.set(key, entries.length - 1);
        } else {
          const curEntry = entries[existingEntryIdx];
          entryIdRemap.set(incEntry.id, curEntry.id);
          entries[existingEntryIdx] = mergeVocabEntryForRestore(curEntry, incEntry);
        }
      }

      entries.sort((a, b) => {
        const wCmp = (a.word || '').trim().toLowerCase().localeCompare((b.word || '').trim().toLowerCase());
        if (wCmp !== 0) return wCmp;
        const pCmp = (a.partOfSpeech || '').trim().toLowerCase().localeCompare((b.partOfSpeech || '').trim().toLowerCase());
        if (pCmp !== 0) return pCmp;
        return (a.createdAt || 0) - (b.createdAt || 0);
      });

      mergedDecks[matchIdx] = {
        ...exDeck,
        translationLanguage: exDeck.translationLanguage?.trim()
          ? exDeck.translationLanguage
          : incDeck.translationLanguage || '',
        languageVariant: exDeck.languageVariant?.trim()
          ? exDeck.languageVariant
          : incDeck.languageVariant || '',
        domain: exDeck.domain?.trim() ? exDeck.domain : incDeck.domain || '',
        entries,
        updatedAt: Math.max(exDeck.updatedAt || 0, incDeck.updatedAt || 0),
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

  return {
    papers: Array.from(paperMap.values()),
    folders: mergedFolders,
    attempts: mergedAttempts,
    activeSessions: mergedActiveSessions,
    countdowns: Array.from(countdownMap.values()),
    studyNotes: Array.from(noteMap.values()).sort((a, b) => b.updatedAt - a.updatedAt),
    knowledgeCourses: mergedKnowledgeCourses,
    vocabDecks: mergedDecks,
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
