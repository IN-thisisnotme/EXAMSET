import type { AppFontScaleMode, ColorSchemePreset, ThemeMode } from '../theme/colors';
import type {
  AttemptRecord,
  ExamCountdownTarget,
  FolderItem,
  MsqScoringPolicy,
  PaperCategory,
  QuestionPaper,
} from '../types/exam';
import {
  DEFAULT_DICTATION_CONFIG,
  DEFAULT_KNOWLEDGE_RULE,
  type FlashcardSrsState,
  type HeadwordQueueItem,
  type KnowledgeCollectionType,
  type LanguageWordOfTheDayState,
  type PromptPreset,
  type SrsRating,
  type StudyNoteRecord,
  type VocabularyDeck,
  type VocabularyLevelCode,
} from '../types/phase4';
import {
  DEFAULT_BRAIN_LAB_SETTINGS,
  DEFAULT_POMODORO_CONFIG,
  EMPTY_BRAIN_LAB_STATS,
  type BrainLabSettings,
  type BrainLabStats,
  type DailyStudyAnalytics,
  type PomodoroPreset,
  type RestorePreview,
  type StudyTodoItem,
} from '../types/phase5';
import { McqParser } from './mcqParser';
import {
  MAX_TEXT_BACKUP_BYTES,
  MAX_ZIP_BACKUP_BYTES,
  normalizeCourseMapForBackup,
  sha256HexBytesSync,
  sha256HexSync,
  type VaultArchiveDocument,
} from './vaultArchiveEngine';

interface ZipCdEntry {
  name: string;
  method: number;
  compressedSize: number;
  uncompressedSize: number;
  localHeaderOffset: number;
}

const EOCD_SIG = 0x06054b50;
const CD_SIG = 0x02014b50;
const LFH_SIG = 0x04034b50;

export function isZipMagicBytes(bytes: Uint8Array): boolean {
  return bytes.length >= 4 && bytes[0] === 0x50 && bytes[1] === 0x4b;
}

function safeJsonParse<T>(raw: unknown, fallback: T): T {
  if (typeof raw !== 'string' || !raw.trim()) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function readZipDirectory(zipBytes: Uint8Array): Map<string, ZipCdEntry> {
  if (zipBytes.length > MAX_ZIP_BACKUP_BYTES) {
    throw new Error(`ZIP archive exceeds maximum ${MAX_ZIP_BACKUP_BYTES / (1024 * 1024)} MB limit.`);
  }
  if (!isZipMagicBytes(zipBytes)) {
    throw new Error('Selected file is not a valid ZIP archive.');
  }

  const view = new DataView(zipBytes.buffer, zipBytes.byteOffset, zipBytes.byteLength);
  const searchStart = Math.max(0, zipBytes.length - 65557);
  let eocdOffset = -1;
  for (let i = zipBytes.length - 22; i >= searchStart; i--) {
    if (view.getUint32(i, true) === EOCD_SIG) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset < 0) {
    throw new Error('Corrupt ZIP archive: End of Central Directory record missing.');
  }

  const totalEntries = view.getUint16(eocdOffset + 10, true);
  const cdSize = view.getUint32(eocdOffset + 12, true);
  const cdOffset = view.getUint32(eocdOffset + 16, true);
  if (cdOffset + cdSize > zipBytes.length) {
    throw new Error('Corrupt ZIP archive: Central Directory out of bounds.');
  }

  const decoder = new TextDecoder('utf-8');
  const entries = new Map<string, ZipCdEntry>();
  let pos = cdOffset;
  for (let idx = 0; idx < totalEntries; idx++) {
    if (pos + 46 > zipBytes.length || view.getUint32(pos, true) !== CD_SIG) {
      throw new Error('Corrupt ZIP archive: invalid Central Directory header.');
    }
    const method = view.getUint16(pos + 10, true);
    const compressedSize = view.getUint32(pos + 20, true);
    const uncompressedSize = view.getUint32(pos + 24, true);
    const fileNameLen = view.getUint16(pos + 28, true);
    const extraLen = view.getUint16(pos + 30, true);
    const commentLen = view.getUint16(pos + 32, true);
    const localHeaderOffset = view.getUint32(pos + 42, true);
    const nameBytes = zipBytes.subarray(pos + 46, pos + 46 + fileNameLen);
    const name = decoder.decode(nameBytes);
    pos += 46 + fileNameLen + extraLen + commentLen;
    if (!name.endsWith('/')) {
      entries.set(name, { name, method, compressedSize, uncompressedSize, localHeaderOffset });
    }
  }
  return entries;
}

async function extractZipEntryBytes(zipBytes: Uint8Array, entry: ZipCdEntry): Promise<Uint8Array> {
  if (entry.uncompressedSize > MAX_TEXT_BACKUP_BYTES) {
    throw new Error(`ZIP entry '${entry.name}' exceeds ${MAX_TEXT_BACKUP_BYTES / (1024 * 1024)} MB limit.`);
  }
  const view = new DataView(zipBytes.buffer, zipBytes.byteOffset, zipBytes.byteLength);
  const off = entry.localHeaderOffset;
  if (off + 30 > zipBytes.length || view.getUint32(off, true) !== LFH_SIG) {
    throw new Error(`Corrupt local header for ZIP entry '${entry.name}'.`);
  }
  const lfNameLen = view.getUint16(off + 26, true);
  const lfExtraLen = view.getUint16(off + 28, true);
  const dataStart = off + 30 + lfNameLen + lfExtraLen;
  const dataEnd = dataStart + entry.compressedSize;
  if (dataEnd > zipBytes.length) {
    throw new Error(`Compressed payload out of bounds for '${entry.name}'.`);
  }
  const compressed = zipBytes.slice(dataStart, dataEnd);

  if (entry.method === 0) {
    if (compressed.length > MAX_TEXT_BACKUP_BYTES) {
      throw new Error(`ZIP entry '${entry.name}' exceeds ${MAX_TEXT_BACKUP_BYTES / (1024 * 1024)} MB limit.`);
    }
    return compressed;
  }
  if (entry.method !== 8) {
    throw new Error(`Unsupported ZIP compression method (${entry.method}) for '${entry.name}'.`);
  }
  if (typeof DecompressionStream === 'undefined') {
    throw new Error('Browser DecompressionStream API is required to read deflated .examset.zip archives.');
  }

  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(compressed);
      controller.close();
    },
  }).pipeThrough(new DecompressionStream('deflate-raw') as unknown as ReadableWritablePair<Uint8Array, Uint8Array>);

  const reader = stream.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      if (value) {
        total += value.byteLength;
        if (total > MAX_TEXT_BACKUP_BYTES) {
          await reader.cancel();
          throw new Error(`ZIP entry '${entry.name}' exceeds ${MAX_TEXT_BACKUP_BYTES / (1024 * 1024)} MB uncompressed limit.`);
        }
        chunks.push(value);
      }
    }
  } finally {
    reader.releaseLock();
  }

  const merged = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return merged;
}

function mapAndroidColorPreset(raw: string): ColorSchemePreset {
  switch ((raw || '').trim().toUpperCase()) {
    case 'WARM_PAPER':
    case 'TERRACOTTA_WARM':
      return 'WARM_PAPER';
    case 'FOREST_SAGE':
    case 'EMERALD_FOCUS':
    case 'BOTANIC_GREEN':
      return 'FOREST_SAGE';
    case 'ROYAL_INDIGO':
    case 'DEEP_INDIGO':
    case 'EYE_COMFORT':
      return 'ROYAL_INDIGO';
    case 'OLED_OBSIDIAN':
    case 'MIDNIGHT_OLED':
    case 'AMOLED_DARK':
      return 'OLED_OBSIDIAN';
    default:
      return 'NORDIC_SLATE';
  }
}

function mapAndroidThemeMode(raw: string): ThemeMode {
  const upper = (raw || '').trim().toUpperCase();
  return upper === 'DARK' || upper === 'LIGHT' || upper === 'SYSTEM' ? upper : 'SYSTEM';
}

function mapAndroidFontScale(raw: string): AppFontScaleMode {
  const upper = (raw || '').trim().toUpperCase();
  return upper === 'COMPACT' ||
    upper === 'STANDARD_FIXED' ||
    upper === 'LARGE' ||
    upper === 'EXTRA_LARGE' ||
    upper === 'FOLLOW_SYSTEM'
    ? upper
    : 'STANDARD_FIXED';
}

function mapAndroidCategory(raw: string): PaperCategory | undefined {
  switch ((raw || '').trim().toUpperCase()) {
    case 'FULL_MOCK':
    case 'MOCK_TEST':
      return 'FULL_MOCK';
    case 'SUBJECT_TEST':
    case 'SECTIONAL':
      return 'SUBJECT_TEST';
    case 'TOPIC_DRILL':
      return 'TOPIC_DRILL';
    case 'PYQ':
      return 'PYQ';
    case 'REVISION':
    case 'STUDY_NOTES':
      return 'REVISION';
    default:
      return undefined;
  }
}

function mapCollectionType(raw: string): KnowledgeCollectionType {
  const u = (raw || '').trim().toUpperCase();
  if (u === 'ONE_LINER') return 'ONE_LINERS';
  const valid: KnowledgeCollectionType[] = [
    'ONE_LINERS',
    'MULTI_LINER',
    'CONCEPT_CODEX',
    'LONG_ANSWER',
    'COMPARISON_TABLE',
    'FORMULA_SHEET',
    'FLASHCARDS',
    'WEAK_AREA_REVIEW',
    'CUSTOM',
  ];
  return valid.includes(u as KnowledgeCollectionType) ? (u as KnowledgeCollectionType) : 'ONE_LINERS';
}

export async function inspectAndroidZipArchive(
  zipBytes: Uint8Array,
  sourceLabel: string
): Promise<RestorePreview> {
  const cd = readZipDirectory(zipBytes);
  const manifestEntry = cd.get('manifest.json');
  if (!manifestEntry) {
    throw new Error('Invalid ExamSet ZIP archive: missing manifest.json.');
  }

  const decoder = new TextDecoder('utf-8');
  const manifestObj = JSON.parse(decoder.decode(await extractZipEntryBytes(zipBytes, manifestEntry))) as {
    schemaVersion?: number;
    appVersion?: string;
    createdAt?: number;
    deviceName?: string;
    entryChecksumsSha256?: Record<string, string>;
  };

  const schemaVersion = Number(manifestObj.schemaVersion || 3);
  const expectedChecksums = manifestObj.entryChecksumsSha256 || {};
  const requiredEntries = [
    'folders.json',
    'papers.ndjson',
    'notes.ndjson',
    'attempts.ndjson',
    'analytics.json',
    'preferences.json',
  ];
  if (schemaVersion >= 5 || cd.has('vocabulary.json')) {
    requiredEntries.push('vocabulary.json');
  }

  const rawTextByEntry = new Map<string, string>();
  const mismatches: string[] = [];
  let verifiedCount = 0;

  for (const reqName of requiredEntries) {
    if (!cd.has(reqName)) {
      mismatches.push(`${reqName} (missing entry)`);
    } else if (!expectedChecksums[reqName]?.trim()) {
      mismatches.push(`${reqName} (missing checksum)`);
    }
  }

  for (const [entryName, cdEntry] of cd.entries()) {
    if (entryName === 'manifest.json') continue;
    const entryBytes = await extractZipEntryBytes(zipBytes, cdEntry);
    rawTextByEntry.set(entryName, decoder.decode(entryBytes));
    const expected = expectedChecksums[entryName]?.trim();
    if (expected) {
      const actual = sha256HexBytesSync(entryBytes);
      if (actual.toLowerCase() === expected.toLowerCase()) {
        verifiedCount++;
      } else {
        mismatches.push(`${entryName} (hash mismatch)`);
      }
    }
  }

  for (const [entryName] of Object.entries(expectedChecksums)) {
    if (!cd.has(entryName)) {
      mismatches.push(`${entryName} (missing from zip)`);
    }
  }

  const allVerified = mismatches.length === 0 && verifiedCount >= requiredEntries.length;
  const checksumDetails = allVerified
    ? `SHA-256 verified across all ${verifiedCount} Android ZIP entries.`
    : `Verification warning on: ${mismatches.join(', ')}.`;

  // 1. Folders
  const rawFolders = safeJsonParse<Array<Record<string, unknown>>>(rawTextByEntry.get('folders.json') || '[]', []);
  const folders: FolderItem[] = rawFolders
    .filter((f) => String(f.id || '') && String(f.id) !== 'default')
    .map((f) => ({
      id: String(f.id),
      name: String(f.name || 'Folder'),
      color: String(f.color || '#177E89'),
      parentId: f.parentId ? String(f.parentId) : null,
      createdAt: Number(f.createdAt || Date.now()),
    }));

  // 2. Preferences
  const prefs = safeJsonParse<Record<string, unknown>>(rawTextByEntry.get('preferences.json') || '{}', {});
  const defaultMsqPolicy: MsqScoringPolicy =
    prefs.defaultMsqScoringPolicy === 'PARTIAL_STRICT' ? 'PARTIAL_STRICT' : 'ALL_OR_NOTHING';
  const paperMsqMap = safeJsonParse<Record<string, string>>(prefs.paperMsqPoliciesJson, {});
  const flashcardSrsByUid = (prefs.flashcardSrsByNoteUid || {}) as Record<
    string,
    Array<Record<string, unknown>>
  >;

  // 3. Papers
  const paperIdMap = new Map<string, string>();
  const paperMetaLookup = new Map<string, { title: string; subject: string; pos: number; neg: number; unatt: number; msq: MsqScoringPolicy }>();
  const papers: QuestionPaper[] = [];
  const paperLines = (rawTextByEntry.get('papers.ndjson') || '').split('\n').filter((l) => l.trim());
  for (const line of paperLines) {
    const p = safeJsonParse<Record<string, unknown>>(line, {});
    const oldId = String(p.id ?? '');
    if (!oldId) continue;
    const webId = `paper-${oldId}`;
    paperIdMap.set(oldId, webId);
    const parsed = McqParser.parse(String(p.rawText || ''));
    const pos = Number(p.positiveMarks ?? 2.0);
    const neg = Number(p.negativeMarks ?? 0.66);
    const unatt = Number(p.unattendedMarks ?? 0.0);
    const msqPolicy: MsqScoringPolicy =
      paperMsqMap[oldId] === 'PARTIAL_STRICT' || defaultMsqPolicy === 'PARTIAL_STRICT'
        ? paperMsqMap[oldId] === 'ALL_OR_NOTHING'
          ? 'ALL_OR_NOTHING'
          : 'PARTIAL_STRICT'
        : 'ALL_OR_NOTHING';
    const title = String(p.title || parsed.paper.metadata.title || 'Question Set');
    const subject = String(p.subject || parsed.paper.metadata.subject || 'General Studies');
    paperMetaLookup.set(oldId, { title, subject, pos, neg, unatt, msq: msqPolicy });

    papers.push({
      metadata: {
        id: webId,
        title,
        subject,
        topic: String(p.topic || ''),
        setNumber: String(p.setNumber || '01'),
        targetExam: String(p.exam || 'Standard Assessment'),
        rollNumber: String(p.rollNumber || '0000000001'),
        bookletNumber: String(p.bookletNumber || '0000001'),
        durationMinutes: Number(p.durationMinutes || 60),
        positiveMarks: pos,
        negativeMarks: neg,
        unattendedMarks: unatt,
        msqScoringPolicy: msqPolicy,
        manualCategory: mapAndroidCategory(String(p.manualCategory || '')),
        folderId: p.folderId ? String(p.folderId) : 'default',
        createdAt: Number(p.createdAt || Date.now()),
        updatedAt: Number(p.updatedAt || p.createdAt || Date.now()),
      },
      questions: parsed.paper.questions,
    });
  }

  // 4. Notes
  const noteIdMap = new Map<string, string>();
  const studyNotes: StudyNoteRecord[] = [];
  const noteLines = (rawTextByEntry.get('notes.ndjson') || '').split('\n').filter((l) => l.trim());
  for (const line of noteLines) {
    const n = safeJsonParse<Record<string, unknown>>(line, {});
    const oldNoteId = String(n.id ?? '');
    const syncUid = String(n.syncUid || '').trim() || `note-${oldNoteId || Date.now()}`;
    if (oldNoteId) noteIdMap.set(oldNoteId, syncUid);
    const srsArr = flashcardSrsByUid[syncUid] || [];
    const srsStates: Record<number, FlashcardSrsState> = {};
    for (const item of srsArr) {
      const bIdx = Number(item.blockIndex ?? 0);
      srsStates[bIdx] = {
        blockIndex: bIdx,
        repetitions: Number(item.repetitions ?? 0),
        easeFactor: Number(item.easeFactor ?? 2.5),
        intervalDays: Number(item.intervalDays ?? 0),
        dueAtMillis: Number(item.dueAtMillis ?? 0),
        lastReviewedAtMillis: Number(item.lastReviewedAtMillis ?? 0),
        lapses: Number(item.lapses ?? 0),
        lastRating: (String(item.lastRating || '').trim() || null) as SrsRating | null,
        reviewCount: Number(item.reviewCount ?? 0),
        correctCount: Number(item.correctCount ?? 0),
      };
    }
    const rawRule = safeJsonParse<Record<string, unknown>>(n.formatRuleJson, {});
    const linkedOld = n.linkedPaperId != null ? String(n.linkedPaperId) : '';
    studyNotes.push({
      id: syncUid,
      title: String(n.title || 'Knowledge Set'),
      subject: String(n.subject || 'General Studies'),
      course: String(n.course || '').trim(),
      sourceAi: String(n.sourceAi || 'Custom'),
      rawContent: String(n.rawContent || ''),
      folderId: n.folderId ? String(n.folderId) : 'default',
      collectionType: mapCollectionType(String(n.collectionType || 'ONE_LINERS')),
      customLabel: String(n.customLabel || ''),
      linkedPaperId: linkedOld ? paperIdMap.get(linkedOld) || null : null,
      formatRule: { ...DEFAULT_KNOWLEDGE_RULE, ...(rawRule as Partial<typeof DEFAULT_KNOWLEDGE_RULE>) },
      bookmarkedIndices: safeJsonParse<number[]>(n.bookmarkedLinesJson, []),
      masteredIndices: safeJsonParse<number[]>(n.masteredBlocksJson, []),
      srsStates,
      createdAt: Number(n.createdAt || Date.now()),
      updatedAt: Number(n.updatedAt || n.createdAt || Date.now()),
    });
  }

  // 5. Attempts
  const attempts: AttemptRecord[] = [];
  const attemptLines = (rawTextByEntry.get('attempts.ndjson') || '').split('\n').filter((l) => l.trim());
  for (const line of attemptLines) {
    const a = safeJsonParse<Record<string, unknown>>(line, {});
    const oldPaperId = String(a.paperId ?? '');
    const webPaperId = paperIdMap.get(oldPaperId) || `paper-${oldPaperId}`;
    const meta = paperMetaLookup.get(oldPaperId);
    const ansPayload = safeJsonParse<Record<string, unknown>>(a.answersJson, {});
    const submittedAnswers: Record<number, string> = {};
    for (const [k, v] of Object.entries(ansPayload)) {
      if (/^\d+$/.test(k)) submittedAnswers[Number(k)] = String(v);
    }
    const qTimesRaw = (ansPayload._qTime as Record<string, unknown> | undefined) || {};
    const questionTimes: Record<number, number> = {};
    for (const [k, v] of Object.entries(qTimesRaw)) {
      if (/^\d+$/.test(k)) questionTimes[Number(k)] = Number(v || 0);
    }
    const totalQ = Number(a.totalQuestions || 0);
    const posMarks = meta?.pos ?? 2.0;
    const score = Number(a.score ?? 0);
    const correctCount = Number(a.correctCount ?? 0);
    const incorrectCount = Number(a.incorrectCount ?? 0);
    const attempted = correctCount + incorrectCount;
    attempts.push({
      id: `att-${String(a.id ?? Date.now())}`,
      paperId: webPaperId,
      paperTitle: meta?.title || 'Restored Exam',
      subject: meta?.subject || 'General',
      rollNumber: String(ansPayload._rollNumber || '0000000001'),
      bookletNumber: String(ansPayload._bookletNumber || '0000001'),
      bookletCode: String(ansPayload._bookletCode || 'A'),
      score,
      maxScore: Math.max(score, totalQ * posMarks),
      correctCount,
      partialCount: 0,
      incorrectCount,
      unansweredCount: Number(a.unansweredCount ?? 0),
      accuracyPercent: attempted > 0 ? Math.round((correctCount / attempted) * 100) : 0,
      timeSpentSeconds: Number(a.timeSpentSeconds ?? 0),
      positiveMarks: posMarks,
      negativeMarks: meta?.neg ?? 0.66,
      unattendedMarks: meta?.unatt ?? 0.0,
      msqScoringPolicy: meta?.msq ?? defaultMsqPolicy,
      submittedAnswers,
      questionTimes,
      submittedAt: Number(a.submittedAt || a.createdAt || Date.now()),
    });
  }

  // 6. Daily Analytics
  const rawAnalytics = safeJsonParse<Array<Record<string, unknown>>>(
    rawTextByEntry.get('analytics.json') || '[]',
    []
  );
  const dailyAnalytics: Record<string, DailyStudyAnalytics> = {};
  for (const d of rawAnalytics) {
    const dateStr = String(d.dateString || '').trim();
    if (!dateStr) continue;
    dailyAnalytics[dateStr] = {
      dateStr,
      testsCompleted: Number(d.testsCompletedCount ?? 0),
      questionsAnswered: Number(d.questionsAttemptedCount ?? 0),
      correctAnswers: Number(d.correctAnswersCount ?? 0),
      studyTimeSeconds: Number(d.totalTestTimeSeconds ?? 0),
      flashcardsReviewed: 0,
      vocabWordsReviewed: 0,
    };
  }

  // 7. Vocabulary (dedicated vocabulary.json or preferences fallback)
  const vocabObj = safeJsonParse<Record<string, unknown>>(rawTextByEntry.get('vocabulary.json') || '{}', {});
  const vocabSource = Object.keys(vocabObj).length > 0 ? vocabObj : prefs;
  const vocabDecks = safeJsonParse<VocabularyDeck[]>(vocabSource.vocabularyDecksJson, []);
  const vocabSrs = safeJsonParse<Record<string, Record<string, FlashcardSrsState>>>(
    vocabSource.vocabularySrsJson,
    {}
  );
  const wotdStates = safeJsonParse<Record<string, LanguageWordOfTheDayState>>(
    vocabSource.languageWordOfTheDayStatesJson,
    {}
  );
  const rawInbox = safeJsonParse<Array<Record<string, unknown>>>(vocabSource.headwordInboxJson, []);
  const headwordInbox: HeadwordQueueItem[] = rawInbox.map((h, i) => ({
    id: String(h.id || `hq-${i}`),
    word: String(h.word || ''),
    language: String(h.language || 'English'),
    levelCode: (String(h.levelCode || 'B2') as VocabularyLevelCode),
    customLevelLabel: String(h.customLevelLabel || ''),
    partOfSpeech: String(h.partOfSpeech || ''),
    domain: String(h.domain || ''),
    utilityReason: String(h.utilityReason || ''),
    addedAt: Number(h.addedAt || Date.now()),
  }));
  const dismissedHeadwords = safeJsonParse<string[]>(vocabSource.dismissedWordKeysJson, []);
  const englishVariant = String(vocabSource.preferredEnglishVariant || 'UK');

  // 8. Countdowns, Todos, Presets, Brain Lab
  const rawCountdowns = safeJsonParse<Array<Record<string, unknown>>>(prefs.examCountdownsJson, []);
  const countdowns: ExamCountdownTarget[] = rawCountdowns.map((c) => ({
    id: String(c.id || `cd-${Date.now()}`),
    title: String(c.title || 'Exam'),
    targetDateIso: String(c.targetDateStr || c.targetDateIso || ''),
    examType: String(c.subject || c.examType || 'Competitive Exam'),
    isPinned: Boolean(c.isPinned),
    color: String(c.colorHex || c.color || '#177E89'),
    targetTime: c.targetTimeStr ? String(c.targetTimeStr) : undefined,
    timezoneMode: c.timezoneMode === 'GMT' ? 'GMT' : 'LOCAL',
  }));

  const rawTodos = safeJsonParse<Array<Record<string, unknown>>>(prefs.studyTodosJson, []);
  const studyTodos: StudyTodoItem[] = rawTodos.map((t) => {
    const oldP = t.linkedPaperId != null ? String(t.linkedPaperId) : '';
    const oldN = t.linkedNoteId != null ? String(t.linkedNoteId) : '';
    return {
      id: String(t.id || `todo-${Date.now()}`),
      title: String(t.title || 'Study Task'),
      priority: (String(t.priority || 'MEDIUM') as StudyTodoItem['priority']),
      dueDateStr: String(t.dueDateStr || ''),
      isCompleted: Boolean(t.isCompleted),
      targetPomodoros: Number(t.targetPomodoros ?? 1),
      completedPomodoros: Number(t.completedPomodoros ?? 0),
      linkedType: (String(t.linkedType || 'NONE') as StudyTodoItem['linkedType']),
      linkedPaperId: oldP ? paperIdMap.get(oldP) || null : null,
      linkedNoteId: oldN ? noteIdMap.get(oldN) || null : null,
      linkedDeckLanguage: String(t.linkedDeckLanguage || ''),
      linkedLabel: String(t.linkedLabel || ''),
      createdAt: Number(t.createdAt || Date.now()),
      completedAt: t.completedAt != null ? Number(t.completedAt) : null,
    };
  });

  const promptPresets = safeJsonParse<PromptPreset[]>(prefs.promptPresetsJson, []);
  const dismissedTips = safeJsonParse<string[]>(prefs.dismissedFormatTipsJson, []);
  const knowledgeCourses = normalizeCourseMapForBackup(
    safeJsonParse<Record<string, unknown>>(prefs.knowledgeCoursesJson, {})
  );
  const brainLabStats: BrainLabStats = {
    ...EMPTY_BRAIN_LAB_STATS,
    ...safeJsonParse<Partial<BrainLabStats>>(prefs.brainLabStatsJson, {}),
  };
  const brainLabSettings: BrainLabSettings = {
    ...DEFAULT_BRAIN_LAB_SETTINGS,
    ...safeJsonParse<Partial<BrainLabSettings>>(prefs.brainLabSettingsJson, {}),
  };

  const doc: VaultArchiveDocument = {
    manifest: {
      format: 'examset-vault-v5',
      schemaVersion: 5,
      appVersion: String(manifestObj.appVersion || 'Android ZIP'),
      createdAt: Number(manifestObj.createdAt || Date.now()),
      deviceName: String(manifestObj.deviceName || 'Android Device'),
      counts: {
        papersCount: papers.length,
        notesCount: studyNotes.length,
        knowledgeCoursesCount: Object.keys(knowledgeCourses).length,
        foldersCount: folders.length,
        attemptsCount: attempts.length,
        analyticsDaysCount: Object.keys(dailyAnalytics).length,
        vocabDecksCount: vocabDecks.length,
        vocabWordsCount: vocabDecks.reduce((s, d) => s + (d.entries?.length || 0), 0),
        promptPresetsCount: promptPresets.length,
        todosCount: studyTodos.length,
        countdownsCount: countdowns.length,
      },
      entryChecksumsSha256: {},
    },
    sections: {
      papers,
      folders,
      attempts,
      countdowns,
      studyNotes,
      vocabulary: {
        decks: vocabDecks,
        srs: vocabSrs,
        englishVariant,
        headwordInbox,
        dismissedHeadwords,
        wotdStates,
      },
      presetsAndDictation: {
        promptPresets,
        dictationConfig: DEFAULT_DICTATION_CONFIG,
      },
      plannerAndAnalytics: {
        studyTodos,
        pomodoroConfig: {
          ...DEFAULT_POMODORO_CONFIG,
          preset: (String(prefs.pomodoroPreset || 'CLASSIC_25_5') as PomodoroPreset),
          customFocusMinutes: Number(prefs.pomodoroCustomFocusMinutes ?? 30),
          customBreakMinutes: Number(prefs.pomodoroCustomBreakMinutes ?? 5),
          dailyFocusGoalMinutes: Number(prefs.dailyGoalMinutes ?? 120),
        },
        dailyAnalytics,
        activeSessions: {},
      },
      brainAndPreferences: {
        brainLabStats,
        brainLabSettings,
        dismissedTips,
        knowledgeCourses,
        themeMode: mapAndroidThemeMode(String(prefs.themeMode || 'SYSTEM')),
        colorPreset: mapAndroidColorPreset(String(prefs.colorSchemePreset || '')),
        fontScaleMode: mapAndroidFontScale(String(prefs.fontScaleMode || '')),
      },
    },
  };

  for (const [k, v] of Object.entries(doc.sections)) {
    doc.manifest.entryChecksumsSha256[k] = sha256HexSync(JSON.stringify(v));
  }

  return {
    sourceLabel,
    isLegacyJson: false,
    checksumVerified: allVerified,
    checksumDetails,
    manifest: doc.manifest,
    rawJson: JSON.stringify(doc, null, 2),
  };
}
