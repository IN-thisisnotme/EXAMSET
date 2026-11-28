import type { AppFontScaleMode, ColorSchemePreset, ThemeMode } from '../theme/colors';
import type {
  AttemptRecord,
  ExamCountdownTarget,
  FolderItem,
  MsqScoringPolicy,
  QuestionPaper,
} from '../types/exam';
import {
  DEFAULT_DICTATION_CONFIG,
  DEFAULT_KNOWLEDGE_RULE,
  type FlashcardSrsState,
  type KnowledgeCollectionType,
  type KnowledgeCoursesMap,
  type PromptPreset,
  type SrsRating,
  type StudyNoteRecord,
  type VocabularyDeck,
} from '../types/phase4';
import {
  DEFAULT_BRAIN_LAB_SETTINGS,
  DEFAULT_POMODORO_CONFIG,
  EMPTY_BRAIN_LAB_STATS,
  type BackupCounts,
  type BackupManifest,
  type BrainLabSettings,
  type BrainLabStats,
  type DailyStudyAnalytics,
  type PomodoroPreset,
  type RestorePreview,
  type StudyTodoItem,
  type TodoPriority,
} from '../types/phase5';
import { McqParser } from './mcqParser';
import { sha256HexSync } from './sha256Sync';
import { buildZipArchive, readZipTextEntries } from './zipArchiveHelper';
import type { VaultArchiveDocument, WorkspaceBackupState } from './vaultArchiveEngine';

function mapWebCollectionToAndroid(c: KnowledgeCollectionType | string): string {
  if (c === 'ONE_LINER') return 'ONE_LINERS';
  if (c === 'MULTI_LINER') return 'MULTI_LINERS';
  return c || 'ONE_LINERS';
}

function mapAndroidCollectionToWeb(c: string): KnowledgeCollectionType {
  if (c === 'MULTI_LINERS') return 'MULTI_LINER';
  if (
    c === 'ONE_LINERS' ||
    c === 'MULTI_LINER' ||
    c === 'CONCEPT_CODEX' ||
    c === 'LONG_ANSWER' ||
    c === 'COMPARISON_TABLE' ||
    c === 'FORMULA_SHEET' ||
    c === 'FLASHCARDS' ||
    c === 'WEAK_AREA_REVIEW' ||
    c === 'CUSTOM'
  ) {
    return c;
  }
  return 'ONE_LINERS';
}

function mapAndroidColorPresetToWeb(raw: string): ColorSchemePreset {
  const upper = (raw || '').trim().toUpperCase();
  if (
    upper === 'NORDIC_SLATE' ||
    upper === 'WARM_PAPER' ||
    upper === 'FOREST_SAGE' ||
    upper === 'ROYAL_INDIGO' ||
    upper === 'OLED_OBSIDIAN'
  ) {
    return upper;
  }
  return 'NORDIC_SLATE';
}

function mapWebColorPresetToAndroid(preset: ColorSchemePreset): string {
  if (
    preset === 'NORDIC_SLATE' ||
    preset === 'WARM_PAPER' ||
    preset === 'FOREST_SAGE' ||
    preset === 'ROYAL_INDIGO' ||
    preset === 'OLED_OBSIDIAN'
  ) {
    return preset;
  }
  return 'NORDIC_SLATE';
}

function mapAndroidFontScaleToWeb(raw: string): AppFontScaleMode {
  const upper = (raw || '').trim().toUpperCase();
  if (
    upper === 'COMPACT' ||
    upper === 'STANDARD_FIXED' ||
    upper === 'LARGE' ||
    upper === 'EXTRA_LARGE' ||
    upper === 'FOLLOW_SYSTEM'
  ) {
    return upper;
  }
  return 'STANDARD_FIXED';
}

function serializePaperToRawText(paper: QuestionPaper): string {
  const lines: string[] = [];
  for (const q of paper.questions) {
    lines.push(`${q.num}. ${q.question}`);
    if (q.options.a) lines.push(`(A) ${q.options.a}`);
    if (q.options.b) lines.push(`(B) ${q.options.b}`);
    if (q.options.c) lines.push(`(C) ${q.options.c}`);
    if (q.options.d) lines.push(`(D) ${q.options.d}`);
    if (q.options.e) lines.push(`(E) ${q.options.e}`);
    if (q.correctAnswer) lines.push(`Answer: ${q.correctAnswer}`);
    if (q.explanation) lines.push(`Explanation: ${q.explanation}`);
    lines.push('');
  }
  return lines.join('\n').trim();
}

function parseIntListSafe(raw: unknown): number[] {
  if (Array.isArray(raw)) {
    return raw.map((n) => Number(n)).filter((n) => Number.isInteger(n) && n >= 0);
  }
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const arr = JSON.parse(raw);
      if (Array.isArray(arr)) {
        return arr.map((n) => Number(n)).filter((n) => Number.isInteger(n) && n >= 0);
      }
    } catch {
      // ignore
    }
  }
  return [];
}

function parseJsonObjectSafe<T>(raw: unknown, fallback: T): T {
  if (raw && typeof raw === 'object') return raw as T;
  if (typeof raw === 'string' && raw.trim()) {
    try {
      return JSON.parse(raw) as T;
    } catch {
      return fallback;
    }
  }
  return fallback;
}

/**
 * Builds an Android-compatible `.examset.zip` vault archive with per-entry SHA-256 manifest.json,
 * folders.json, papers.ndjson, notes.ndjson, attempts.ndjson, analytics.json, vocabulary.json, and preferences.json.
 */
export async function buildAndroidZipVaultArchive(
  state: WorkspaceBackupState,
  deviceName: string = 'ExamSet Web Browser',
  nowMs: number = Date.now()
): Promise<{ bytes: Uint8Array; manifest: BackupManifest }> {
  const foldersJson = JSON.stringify(
    state.folders.map((f) => ({
      id: f.id,
      name: f.name,
      color: f.color || '#177E89',
      parentId: f.parentId ?? null,
      createdAt: f.createdAt || nowMs,
      updatedAt: f.createdAt || nowMs,
    }))
  );

  const paperNumericIdByWebId = new Map<string, number>();
  const paperMsqPolicies: Record<string, MsqScoringPolicy> = {};
  const paperLines: string[] = [];

  state.papers.forEach((p, idx) => {
    const numId = idx + 1001;
    paperNumericIdByWebId.set(p.metadata.id, numId);
    paperMsqPolicies[String(numId)] = p.metadata.msqScoringPolicy || 'ALL_OR_NOTHING';

    const serializedRaw = serializePaperToRawText(p);
    const answerKeyCount = p.questions.filter((q) => Boolean(q.correctAnswer?.trim())).length;
    const explanationCount = p.questions.filter((q) => Boolean(q.explanation?.trim())).length;

    paperLines.push(
      JSON.stringify({
        id: numId,
        title: p.metadata.title,
        subject: p.metadata.subject,
        topic: p.metadata.topic || '',
        setNumber: p.metadata.setNumber || '01',
        exam: p.metadata.targetExam || '',
        questionCount: p.questions.length,
        answerKeyCount,
        explanationCount,
        rawText: serializedRaw,
        createdAt: p.metadata.createdAt || nowMs,
        updatedAt: p.metadata.updatedAt || p.metadata.createdAt || nowMs,
        folderId: p.metadata.folderId || 'default',
        rollNumber: p.metadata.rollNumber || '0000000001',
        bookletNumber: p.metadata.bookletNumber || '0000001',
        durationMinutes: p.metadata.durationMinutes ?? 60,
        positiveMarks: p.metadata.positiveMarks ?? 2.0,
        negativeMarks: p.metadata.negativeMarks ?? 0.66,
        unattendedMarks: p.metadata.unattendedMarks ?? 0.0,
        manualCategory: p.metadata.manualCategory || '',
      })
    );
  });
  const papersNdjson = paperLines.join('\n');

  const noteLines: string[] = [];
  const flashcardSrsByNoteUid: Record<string, unknown[]> = {};

  state.studyNotes.forEach((n, idx) => {
    const syncUid = n.id;
    const linkedNumId = n.linkedPaperId ? paperNumericIdByWebId.get(n.linkedPaperId) ?? null : null;

    noteLines.push(
      JSON.stringify({
        id: idx + 2001,
        folderId: n.folderId || 'default',
        title: n.title,
        subject: n.subject,
        course: n.course || '',
        sourceAi: n.sourceAi || 'ExamSet Web',
        rawContent: n.rawContent,
        bookmarkedLinesJson: JSON.stringify(n.bookmarkedIndices || []),
        createdAt: n.createdAt || nowMs,
        updatedAt: n.updatedAt || n.createdAt || nowMs,
        collectionType: mapWebCollectionToAndroid(n.collectionType),
        customLabel: n.customLabel || '',
        linkedPaperId: linkedNumId,
        formatRuleJson: JSON.stringify({
          ...(n.formatRule || DEFAULT_KNOWLEDGE_RULE),
          collectionType: mapWebCollectionToAndroid(n.collectionType),
        }),
        masteredBlocksJson: JSON.stringify(n.masteredIndices || []),
        syncUid,
      })
    );

    const srsEntries = Object.entries(n.srsStates || {});
    if (srsEntries.length > 0) {
      flashcardSrsByNoteUid[syncUid] = srsEntries.map(([bStr, st]) => ({
        blockIndex: Number.isFinite(st.blockIndex) ? st.blockIndex : Number(bStr),
        repetitions: st.repetitions || 0,
        easeFactor: st.easeFactor || 2.5,
        intervalDays: st.intervalDays || 0,
        dueAtMillis: st.dueAtMillis || 0,
        lastReviewedAtMillis: st.lastReviewedAtMillis || 0,
        lapses: st.lapses || 0,
        lastRating: st.lastRating || '',
        reviewCount: st.reviewCount || 0,
        correctCount: st.correctCount || 0,
      }));
    }
  });
  const notesNdjson = noteLines.join('\n');

  const attemptLines = state.attempts.map((a, idx) => {
    const packedAnswers: Record<string, unknown> = {
      _rollNumber: a.rollNumber || '',
      _bookletNumber: a.bookletNumber || '',
      _bookletCode: a.bookletCode || 'A',
      _webPaperId: a.paperId,
    };
    if (a.questionTimes && Object.keys(a.questionTimes).length > 0) {
      packedAnswers._qTime = a.questionTimes;
    }
    for (const [qNum, ans] of Object.entries(a.submittedAnswers || {})) {
      packedAnswers[String(qNum)] = ans;
    }
    const paperNumId = paperNumericIdByWebId.get(a.paperId) || 1;
    const totalQuestions = Math.max(
      1,
      a.correctCount + a.partialCount + a.incorrectCount + a.unansweredCount
    );
    return JSON.stringify({
      id: idx + 1,
      paperId: paperNumId,
      answersJson: JSON.stringify(packedAnswers),
      isSubmitted: true,
      score: a.score,
      correctCount: a.correctCount + a.partialCount,
      incorrectCount: a.incorrectCount,
      unansweredCount: a.unansweredCount,
      totalQuestions,
      timeSpentSeconds: a.timeSpentSeconds || 0,
      createdAt: a.submittedAt || nowMs,
      submittedAt: a.submittedAt || nowMs,
    });
  });
  const attemptsNdjson = attemptLines.join('\n');

  const analyticsArr = Object.values(state.dailyAnalytics || {}).map((d) => ({
    dateString: d.dateStr,
    totalTestTimeSeconds: d.studyTimeSeconds || 0,
    testsCompletedCount: d.testsCompleted || 0,
    testsStartedCount: d.testsCompleted || 0,
    questionsAttemptedCount: d.questionsAnswered || 0,
    correctAnswersCount: d.correctAnswers || 0,
    dailyGoalSeconds: (state.pomodoroConfig?.dailyFocusGoalMinutes || 120) * 60,
    lastUpdatedTimestamp: nowMs,
  }));
  const analyticsJson = JSON.stringify(analyticsArr);

  const vocabularyObj = {
    schemaVersion: 2,
    vocabDecksCount: state.vocabDecks.length,
    vocabWordsCount: state.vocabDecks.reduce((s, d) => s + d.entries.length, 0),
    vocabularyDecksJson: JSON.stringify(state.vocabDecks),
    vocabularySrsJson: JSON.stringify(state.vocabSrs || {}),
    preferredEnglishVariant: state.englishVariant || 'UK',
    languageWordOfTheDayStatesJson: JSON.stringify(state.wotdStates || {}),
    vocabReminderEnabled: false,
    headwordInboxJson: JSON.stringify(state.headwordInbox || []),
    dismissedWordKeysJson: JSON.stringify(state.dismissedHeadwords || []),
  };
  const vocabularyJson = JSON.stringify(vocabularyObj);

  const androidCountdowns = state.countdowns.map((cd) => ({
    id: cd.id,
    title: cd.title,
    targetDateStr: cd.targetDateIso,
    targetTimeStr: cd.targetTime || '',
    timezoneMode: cd.timezoneMode || 'LOCAL',
    targetUtcMillis: cd.targetUtcMillis || 0,
    subject: cd.examType || '',
    isPinned: Boolean(cd.isPinned),
    colorHex: cd.color || '#177E89',
    updatedAt: nowMs,
  }));

  const androidTodos = state.studyTodos.map((t) => ({
    id: t.id,
    title: t.title,
    priority: t.priority,
    dueDateStr: t.dueDateStr || '',
    isCompleted: Boolean(t.isCompleted),
    targetPomodoros: t.targetPomodoros || 1,
    completedPomodoros: t.completedPomodoros || 0,
    linkedType: t.linkedType || 'NONE',
    linkedPaperId: t.linkedPaperId ? paperNumericIdByWebId.get(t.linkedPaperId) ?? null : null,
    linkedNoteId: null,
    linkedDeckLanguage: t.linkedDeckLanguage || '',
    linkedLabel: t.linkedLabel || '',
    createdAt: t.createdAt || nowMs,
    completedAt: t.completedAt ?? null,
    updatedAt: t.completedAt || t.createdAt || nowMs,
  }));

  const preferencesJson = JSON.stringify({
    dailyGoalMinutes: state.pomodoroConfig?.dailyFocusGoalMinutes || 120,
    fontScaleMode: state.fontScaleMode || 'STANDARD_FIXED',
    themeMode: state.themeMode === 'DARK' ? 'DARK' : 'LIGHT',
    colorSchemePreset: mapWebColorPresetToAndroid(state.colorPreset),
    promptPresetsJson: JSON.stringify(state.promptPresets || []),
    activePromptPresetIdsJson: '{}',
    dismissedFormatTipsJson: JSON.stringify(state.dismissedTips || []),
    flashcardSrsByNoteUid,
    vocabularyDecksJson: vocabularyObj.vocabularyDecksJson,
    vocabularySrsJson: vocabularyObj.vocabularySrsJson,
    preferredEnglishVariant: vocabularyObj.preferredEnglishVariant,
    languageWordOfTheDayStatesJson: vocabularyObj.languageWordOfTheDayStatesJson,
    vocabReminderEnabled: false,
    headwordInboxJson: vocabularyObj.headwordInboxJson,
    dismissedWordKeysJson: vocabularyObj.dismissedWordKeysJson,
    examCountdownsJson: JSON.stringify(androidCountdowns),
    studyTodosJson: JSON.stringify(androidTodos),
    pomodoroPreset: state.pomodoroConfig?.preset || 'CLASSIC_25_5',
    pomodoroCustomFocusMinutes: state.pomodoroConfig?.customFocusMinutes || 30,
    pomodoroCustomBreakMinutes: state.pomodoroConfig?.customBreakMinutes || 5,
    defaultMsqScoringPolicy: 'ALL_OR_NOTHING',
    paperMsqPoliciesJson: JSON.stringify(paperMsqPolicies),
    brainLabStatsJson: JSON.stringify(state.brainLabStats || EMPTY_BRAIN_LAB_STATS),
    brainLabSettingsJson: JSON.stringify(state.brainLabSettings || DEFAULT_BRAIN_LAB_SETTINGS),
    knowledgeCoursesJson: JSON.stringify(state.knowledgeCourses || {}),
  });

  const entryChecksumsSha256: Record<string, string> = {
    'folders.json': sha256HexSync(foldersJson),
    'papers.ndjson': sha256HexSync(papersNdjson),
    'notes.ndjson': sha256HexSync(notesNdjson),
    'attempts.ndjson': sha256HexSync(attemptsNdjson),
    'analytics.json': sha256HexSync(analyticsJson),
    'vocabulary.json': sha256HexSync(vocabularyJson),
    'preferences.json': sha256HexSync(preferencesJson),
  };

  const counts: BackupCounts = {
    papersCount: state.papers.length,
    notesCount: state.studyNotes.length,
    knowledgeCoursesCount: Object.keys(state.knowledgeCourses || {}).length,
    foldersCount: state.folders.length,
    attemptsCount: state.attempts.length,
    analyticsDaysCount: Object.keys(state.dailyAnalytics || {}).length,
    vocabDecksCount: state.vocabDecks.length,
    vocabWordsCount: state.vocabDecks.reduce((s, d) => s + d.entries.length, 0),
    promptPresetsCount: state.promptPresets.length,
    todosCount: state.studyTodos.length,
    countdownsCount: state.countdowns.length,
  };

  const manifest: BackupManifest = {
    format: 'examset-vault-v5',
    schemaVersion: 5,
    appVersion: '2.5.0-web',
    createdAt: nowMs,
    deviceName,
    counts,
    entryChecksumsSha256,
  };

  const manifestJson = JSON.stringify(
    {
      schemaVersion: 5,
      appVersion: '2.5.0-web',
      createdAt: nowMs,
      deviceName,
      counts: {
        ...counts,
        draftsCount: 0,
        hasBrainLabData: true,
        hasAppSettings: true,
      },
      entryChecksumsSha256,
    },
    null,
    2
  );

  const bytes = await buildZipArchive([
    { name: 'folders.json', data: foldersJson },
    { name: 'papers.ndjson', data: papersNdjson },
    { name: 'notes.ndjson', data: notesNdjson },
    { name: 'attempts.ndjson', data: attemptsNdjson },
    { name: 'analytics.json', data: analyticsJson },
    { name: 'vocabulary.json', data: vocabularyJson },
    { name: 'preferences.json', data: preferencesJson },
    { name: 'manifest.json', data: manifestJson },
  ]);

  return { bytes, manifest };
}

/**
 * Reads and SHA-256 verifies an Android `.examset.zip` vault archive into a standard Web `RestorePreview`
 * whose normalized `rawJson` can be inspected and restored seamlessly via `applyVaultRestoreToState`.
 */
export async function inspectAndroidZipBytes(
  bytes: Uint8Array,
  sourceLabel: string
): Promise<RestorePreview> {
  const entries = await readZipTextEntries(bytes);
  const manifestRaw = entries.get('manifest.json');
  if (!manifestRaw) {
    throw new Error('Invalid Android .examset.zip archive: missing manifest.json.');
  }

  let manifestObj: {
    schemaVersion?: number;
    appVersion?: string;
    createdAt?: number;
    deviceName?: string;
    counts?: Partial<BackupCounts>;
    entryChecksumsSha256?: Record<string, string>;
  };
  try {
    manifestObj = JSON.parse(manifestRaw);
  } catch {
    throw new Error('Corrupted manifest.json inside .examset.zip archive.');
  }

  const schemaVer = Number(manifestObj.schemaVersion || 3);
  const expectedMap = manifestObj.entryChecksumsSha256 || {};
  const requiredEntries = [
    'folders.json',
    'papers.ndjson',
    'notes.ndjson',
    'attempts.ndjson',
    'analytics.json',
    'preferences.json',
  ];
  if (schemaVer >= 5 || entries.has('vocabulary.json')) {
    requiredEntries.push('vocabulary.json');
  }

  const mismatches: string[] = [];
  let verifiedCount = 0;

  for (const entryName of requiredEntries) {
    if (!entries.has(entryName)) {
      mismatches.push(`${entryName} (missing entry)`);
    } else if (!expectedMap[entryName] || !expectedMap[entryName].trim()) {
      mismatches.push(`${entryName} (missing checksum)`);
    }
  }

  for (const [entryName, expectedHash] of Object.entries(expectedMap)) {
    const content = entries.get(entryName);
    if (content === undefined || !expectedHash || !expectedHash.trim()) {
      mismatches.push(`${entryName} (missing payload/checksum)`);
      continue;
    }
    const actualHash = sha256HexSync(content);
    if (actualHash.toLowerCase() !== expectedHash.trim().toLowerCase()) {
      mismatches.push(`${entryName} (hash mismatch)`);
    } else {
      verifiedCount++;
    }
  }

  const allVerified = mismatches.length === 0 && verifiedCount >= requiredEntries.length;
  const checksumDetails = allVerified
    ? `Verified ${verifiedCount} Android ZIP entries (SHA-256 OK).`
    : `Verification warning on ZIP entries: ${mismatches.join(', ')}.`;

  // 1. Folders
  const foldersRaw = parseJsonObjectSafe<Array<Record<string, unknown>>>(entries.get('folders.json'), []);
  const folders: FolderItem[] = foldersRaw
    .filter((f) => f && typeof f.id === 'string')
    .map((f) => ({
      id: String(f.id),
      name: String(f.name || 'Folder'),
      color: String(f.color || '#177E89'),
      parentId: f.parentId ? String(f.parentId) : null,
      createdAt: Number(f.createdAt || Date.now()),
    }));

  // 2. Preferences first (for MSQ policies, flashcard SRS, countdowns, todos, presets, courses)
  const prefsObj = parseJsonObjectSafe<Record<string, unknown>>(entries.get('preferences.json'), {});
  const paperMsqPolicies = parseJsonObjectSafe<Record<string, MsqScoringPolicy>>(
    prefsObj.paperMsqPoliciesJson,
    {}
  );

  // 3. Papers (NDJSON)
  const paperIdMapByAndroidId = new Map<number, string>();
  const papers: QuestionPaper[] = [];
  const papersText = entries.get('papers.ndjson') || '';
  for (const line of papersText.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    try {
      const pObj = JSON.parse(trimmed) as Record<string, unknown>;
      const numId = Number(pObj.id || 0);
      const rawText = String(pObj.rawText || '');
      const parsed = McqParser.parse(rawText);
      const webPaperId = `android-paper-${numId || Date.now()}-${papers.length + 1}`;
      if (numId > 0) paperIdMapByAndroidId.set(numId, webPaperId);

      const msqPolicy =
        paperMsqPolicies[String(numId)] ||
        (prefsObj.defaultMsqScoringPolicy === 'PARTIAL_STRICT' ? 'PARTIAL_STRICT' : 'ALL_OR_NOTHING');

      const manualCat = String(pObj.manualCategory || '').trim();
      papers.push({
        metadata: {
          id: webPaperId,
          title: String(pObj.title || parsed.paper.metadata.title || 'Imported Android Paper'),
          subject: String(pObj.subject || parsed.paper.metadata.subject || 'General'),
          topic: String(pObj.topic || ''),
          setNumber: String(pObj.setNumber || '01'),
          targetExam: String(pObj.exam || 'Standard Assessment'),
          rollNumber: String(pObj.rollNumber || '0000000001'),
          bookletNumber: String(pObj.bookletNumber || '0000001'),
          durationMinutes: Number(pObj.durationMinutes || 60),
          positiveMarks: Number(pObj.positiveMarks ?? 2.0),
          negativeMarks: Number(pObj.negativeMarks ?? 0.66),
          unattendedMarks: Number(pObj.unattendedMarks ?? 0.0),
          msqScoringPolicy: msqPolicy,
          ...(manualCat ? { manualCategory: manualCat as QuestionPaper['metadata']['manualCategory'] } : {}),
          folderId: pObj.folderId ? String(pObj.folderId) : 'default',
          createdAt: Number(pObj.createdAt || Date.now()),
          updatedAt: Number(pObj.updatedAt || pObj.createdAt || Date.now()),
        },
        questions: parsed.paper.questions,
      });
    } catch {
      // Skip malformed line gracefully
    }
  }

  // 4. Flashcard SRS by note UID + StudyNotes (NDJSON)
  const srsByUidRaw = parseJsonObjectSafe<Record<string, Array<Record<string, unknown>>>>(
    prefsObj.flashcardSrsByNoteUid,
    {}
  );
  const studyNotes: StudyNoteRecord[] = [];
  const noteWebIdByAndroidId = new Map<number, string>();
  const notesText = entries.get('notes.ndjson') || '';

  for (const line of notesText.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    try {
      const nObj = JSON.parse(trimmed) as Record<string, unknown>;
      const syncUid = String(nObj.syncUid || '').trim() || `android-note-${nObj.id || studyNotes.length + 1}`;
      const numId = Number(nObj.id || 0);
      if (numId > 0) noteWebIdByAndroidId.set(numId, syncUid);

      const srsArr = srsByUidRaw[syncUid] || [];
      const srsStates: Record<number, FlashcardSrsState> = {};
      for (const st of srsArr) {
        const blockIdx = Number(st.blockIndex ?? -1);
        if (blockIdx < 0) continue;
        const ratingStr = String(st.lastRating || '').trim();
        const validRating: SrsRating | null =
          ratingStr === 'AGAIN' || ratingStr === 'HARD' || ratingStr === 'GOOD' || ratingStr === 'EASY'
            ? ratingStr
            : null;
        srsStates[blockIdx] = {
          blockIndex: blockIdx,
          repetitions: Number(st.repetitions || 0),
          easeFactor: Number(st.easeFactor || 2.5),
          intervalDays: Number(st.intervalDays || 0),
          dueAtMillis: Number(st.dueAtMillis || 0),
          lastReviewedAtMillis: Number(st.lastReviewedAtMillis || 0),
          lapses: Number(st.lapses || 0),
          lastRating: validRating,
          reviewCount: Number(st.reviewCount || 0),
          correctCount: Number(st.correctCount || 0),
        };
      }

      const rawRule = parseJsonObjectSafe<Record<string, unknown>>(nObj.formatRuleJson, {});
      const collectionType = mapAndroidCollectionToWeb(String(nObj.collectionType || 'ONE_LINERS'));
      const linkedAndroidPaperId = nObj.linkedPaperId ? Number(nObj.linkedPaperId) : null;

      studyNotes.push({
        id: syncUid,
        title: String(nObj.title || 'Knowledge Set'),
        subject: String(nObj.subject || 'General Studies'),
        course: String(nObj.course || '').trim(),
        sourceAi: String(nObj.sourceAi || 'Android Import'),
        rawContent: String(nObj.rawContent || ''),
        folderId: nObj.folderId ? String(nObj.folderId) : 'default',
        collectionType,
        customLabel: String(nObj.customLabel || ''),
        linkedPaperId:
          linkedAndroidPaperId !== null ? paperIdMapByAndroidId.get(linkedAndroidPaperId) ?? null : null,
        formatRule: {
          ...DEFAULT_KNOWLEDGE_RULE,
          ...(rawRule as Partial<StudyNoteRecord['formatRule']>),
          collectionType,
        },
        bookmarkedIndices: parseIntListSafe(nObj.bookmarkedLinesJson),
        masteredIndices: parseIntListSafe(nObj.masteredBlocksJson),
        srsStates,
        createdAt: Number(nObj.createdAt || Date.now()),
        updatedAt: Number(nObj.updatedAt || nObj.createdAt || Date.now()),
      });
    } catch {
      // Skip malformed line
    }
  }

  // 5. Attempts (NDJSON)
  const paperByWebId = new Map(papers.map((p) => [p.metadata.id, p]));
  const attempts: AttemptRecord[] = [];
  const attemptsText = entries.get('attempts.ndjson') || '';
  for (const line of attemptsText.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    try {
      const aObj = JSON.parse(trimmed) as Record<string, unknown>;
      const numPaperId = Number(aObj.paperId || 0);
      const ansObj = parseJsonObjectSafe<Record<string, unknown>>(aObj.answersJson, {});
      const webPaperId =
        (typeof ansObj._webPaperId === 'string' && ansObj._webPaperId) ||
        paperIdMapByAndroidId.get(numPaperId) ||
        `android-paper-${numPaperId}`;
      const matchedPaper = paperByWebId.get(webPaperId);

      const submittedAnswers: Record<number, string> = {};
      const questionTimes: Record<number, number> = {};
      for (const [k, v] of Object.entries(ansObj)) {
        if (k === '_qTime' && v && typeof v === 'object') {
          for (const [qtK, qtV] of Object.entries(v as Record<string, unknown>)) {
            const qn = Number(qtK);
            if (Number.isInteger(qn) && qn > 0) questionTimes[qn] = Number(qtV || 0);
          }
          continue;
        }
        if (k.startsWith('_')) continue;
        const qn = Number(k);
        if (Number.isInteger(qn) && qn > 0 && typeof v === 'string') {
          submittedAnswers[qn] = v;
        }
      }

      const correctCount = Number(aObj.correctCount || 0);
      const incorrectCount = Number(aObj.incorrectCount || 0);
      const unansweredCount = Number(aObj.unansweredCount || 0);
      const pos = matchedPaper?.metadata.positiveMarks ?? 2.0;
      const maxScore = Math.max(1, (matchedPaper?.questions.length || Number(aObj.totalQuestions || 1)) * pos);
      const attempted = correctCount + incorrectCount;

      attempts.push({
        id: `att-android-${aObj.id || attempts.length + 1}`,
        paperId: webPaperId,
        paperTitle: matchedPaper?.metadata.title || `Paper #${numPaperId}`,
        subject: matchedPaper?.metadata.subject || 'General',
        rollNumber: String(ansObj._rollNumber || matchedPaper?.metadata.rollNumber || '0000000001'),
        bookletNumber: String(ansObj._bookletNumber || matchedPaper?.metadata.bookletNumber || '0000001'),
        bookletCode: String(ansObj._bookletCode || 'A'),
        score: Number(aObj.score || 0),
        maxScore,
        correctCount,
        partialCount: 0,
        incorrectCount,
        unansweredCount,
        accuracyPercent: attempted > 0 ? Math.round((correctCount / attempted) * 100) : 0,
        timeSpentSeconds: Number(aObj.timeSpentSeconds || 0),
        positiveMarks: pos,
        negativeMarks: matchedPaper?.metadata.negativeMarks ?? 0.66,
        unattendedMarks: matchedPaper?.metadata.unattendedMarks ?? 0.0,
        msqScoringPolicy: matchedPaper?.metadata.msqScoringPolicy || 'ALL_OR_NOTHING',
        submittedAnswers,
        questionTimes,
        submittedAt: Number(aObj.submittedAt || aObj.createdAt || Date.now()),
      });
    } catch {
      // ignore malformed attempt line
    }
  }

  // 6. Analytics
  const analyticsArr = parseJsonObjectSafe<Array<Record<string, unknown>>>(entries.get('analytics.json'), []);
  const dailyAnalytics: Record<string, DailyStudyAnalytics> = {};
  for (const d of analyticsArr) {
    const dateStr = String(d.dateString || '').trim();
    if (!dateStr) continue;
    dailyAnalytics[dateStr] = {
      dateStr,
      testsCompleted: Number(d.testsCompletedCount || 0),
      questionsAnswered: Number(d.questionsAttemptedCount || 0),
      correctAnswers: Number(d.correctAnswersCount || 0),
      studyTimeSeconds: Number(d.totalTestTimeSeconds || 0),
      flashcardsReviewed: 0,
      vocabWordsReviewed: 0,
    };
  }

  // 7. Vocabulary (dedicated entry preferred, preferences fallback)
  const vocabSource = entries.has('vocabulary.json')
    ? parseJsonObjectSafe<Record<string, unknown>>(entries.get('vocabulary.json'), {})
    : prefsObj;
  const vocabDecks = parseJsonObjectSafe<VocabularyDeck[]>(vocabSource.vocabularyDecksJson, []);
  const vocabSrs = parseJsonObjectSafe<Record<string, Record<string, FlashcardSrsState>>>(
    vocabSource.vocabularySrsJson,
    {}
  );
  const englishVariant = String(vocabSource.preferredEnglishVariant || 'UK') || 'UK';
  const headwordInbox = parseJsonObjectSafe(vocabSource.headwordInboxJson, []);
  const dismissedHeadwords = parseJsonObjectSafe<string[]>(vocabSource.dismissedWordKeysJson, []);
  const wotdStates = parseJsonObjectSafe(vocabSource.languageWordOfTheDayStatesJson, {});

  // 8. Countdowns, Todos, Presets, Courses, BrainLab, Appearance
  const countdownsRaw = parseJsonObjectSafe<Array<Record<string, unknown>>>(prefsObj.examCountdownsJson, []);
  const countdowns: ExamCountdownTarget[] = countdownsRaw.map((cd, i) => ({
    id: String(cd.id || `cd-android-${i}`),
    title: String(cd.title || 'Exam Target'),
    targetDateIso: String(cd.targetDateStr || cd.targetDateIso || ''),
    examType: String(cd.subject || cd.examType || ''),
    isPinned: Boolean(cd.isPinned),
    color: String(cd.colorHex || cd.color || '#177E89'),
    targetTime: cd.targetTimeStr ? String(cd.targetTimeStr) : undefined,
    timezoneMode: cd.timezoneMode === 'GMT' ? 'GMT' : 'LOCAL',
    targetUtcMillis: cd.targetUtcMillis ? Number(cd.targetUtcMillis) : undefined,
  }));

  const todosRaw = parseJsonObjectSafe<Array<Record<string, unknown>>>(prefsObj.studyTodosJson, []);
  const studyTodos: StudyTodoItem[] = todosRaw.map((t, i) => {
    const rawPid = t.linkedPaperId ? Number(t.linkedPaperId) : null;
    const rawNid = t.linkedNoteId ? Number(t.linkedNoteId) : null;
    const prioRaw = String(t.priority || 'MEDIUM').toUpperCase();
    const priority: TodoPriority =
      prioRaw === 'HIGH' || prioRaw === 'LOW' || prioRaw === 'MEDIUM' ? prioRaw : 'MEDIUM';
    return {
      id: String(t.id || `todo-android-${i}`),
      title: String(t.title || 'Study Task'),
      priority,
      dueDateStr: String(t.dueDateStr || ''),
      isCompleted: Boolean(t.isCompleted),
      targetPomodoros: Number(t.targetPomodoros || 1),
      completedPomodoros: Number(t.completedPomodoros || 0),
      linkedType: (String(t.linkedType || 'NONE') as StudyTodoItem['linkedType']) || 'NONE',
      linkedPaperId: rawPid !== null ? paperIdMapByAndroidId.get(rawPid) ?? null : null,
      linkedNoteId: rawNid !== null ? noteWebIdByAndroidId.get(rawNid) ?? null : null,
      linkedDeckLanguage: String(t.linkedDeckLanguage || ''),
      linkedLabel: String(t.linkedLabel || ''),
      createdAt: Number(t.createdAt || Date.now()),
      completedAt: t.completedAt ? Number(t.completedAt) : null,
    };
  });

  const promptPresets = parseJsonObjectSafe<PromptPreset[]>(prefsObj.promptPresetsJson, []);
  const dismissedTips = parseJsonObjectSafe<string[]>(prefsObj.dismissedFormatTipsJson, []);
  const knowledgeCourses = parseJsonObjectSafe<KnowledgeCoursesMap>(prefsObj.knowledgeCoursesJson, {});
  const brainLabStats = {
    ...EMPTY_BRAIN_LAB_STATS,
    ...parseJsonObjectSafe<Partial<BrainLabStats>>(prefsObj.brainLabStatsJson, {}),
  };
  const brainLabSettings = {
    ...DEFAULT_BRAIN_LAB_SETTINGS,
    ...parseJsonObjectSafe<Partial<BrainLabSettings>>(prefsObj.brainLabSettingsJson, {}),
  };

  const presetRaw = String(prefsObj.pomodoroPreset || 'CLASSIC_25_5');
  const pomodoroPreset: PomodoroPreset =
    presetRaw === 'CLASSIC_25_5' ||
    presetRaw === 'DEEP_50_10' ||
    presetRaw === 'MARATHON_90_15' ||
    presetRaw === 'CUSTOM'
      ? presetRaw
      : 'CLASSIC_25_5';

  const normalizedDoc: VaultArchiveDocument = {
    manifest: {
      format: 'examset-vault-v5',
      schemaVersion: schemaVer,
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
      entryChecksumsSha256: expectedMap,
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
          preset: pomodoroPreset,
          customFocusMinutes: Number(prefsObj.pomodoroCustomFocusMinutes || 30),
          customBreakMinutes: Number(prefsObj.pomodoroCustomBreakMinutes || 5),
          dailyFocusGoalMinutes: Number(prefsObj.dailyGoalMinutes || 120),
        },
        dailyAnalytics,
      },
      brainAndPreferences: {
        brainLabStats,
        brainLabSettings,
        dismissedTips,
        knowledgeCourses,
        themeMode: (String(prefsObj.themeMode || 'LIGHT').toUpperCase() === 'DARK' ? 'DARK' : 'LIGHT') as ThemeMode,
        colorPreset: mapAndroidColorPresetToWeb(String(prefsObj.colorSchemePreset || '')),
        fontScaleMode: mapAndroidFontScaleToWeb(String(prefsObj.fontScaleMode || '')),
      },
    },
  };

  return {
    sourceLabel,
    isLegacyJson: false,
    checksumVerified: allVerified,
    checksumDetails,
    manifest: normalizedDoc.manifest,
    rawJson: JSON.stringify(normalizedDoc),
  };
}
