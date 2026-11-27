import test from 'node:test';
import assert from 'node:assert/strict';
import { deflateRawSync } from 'node:zlib';

import {
  DEFAULT_BRAIN_LAB_SETTINGS,
  DEFAULT_POMODORO_CONFIG,
  EMPTY_BRAIN_LAB_STATS,
  type VaultSnapshotRecord,
} from '../src/types/phase5';
import { DEFAULT_DICTATION_CONFIG, DEFAULT_KNOWLEDGE_RULE } from '../src/types/phase4';
import {
  applyVaultRestoreToState,
  buildVaultArchive,
  inspectVaultArchiveText,
  sha256HexBytesSync,
  type WorkspaceBackupState,
} from '../src/utils/vaultArchiveEngine';
import { inspectAndroidZipArchive, isZipMagicBytes } from '../src/utils/zipVaultReader';
import { CloudSyncEngine } from '../src/utils/cloudSyncEngine';
import { ExamStore } from '../src/data/examStore';
import { StudyNoteParser } from '../src/utils/studyNoteParser';

function createMemoryStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear() {
      map.clear();
    },
    getItem(key: string) {
      return map.has(key) ? map.get(key)! : null;
    },
    key(index: number) {
      return Array.from(map.keys())[index] ?? null;
    },
    removeItem(key: string) {
      map.delete(key);
    },
    setItem(key: string, value: string) {
      map.set(key, String(value));
    },
  };
}
 
function makeWorkspaceState(): WorkspaceBackupState {
  return {
    papers: [
      {
        metadata: {
          id: 'p-1',
          title: 'Polity Mock 1',
          subject: 'Polity',
          topic: 'Parliament',
          setNumber: 'Set 01',
          targetExam: 'UPSC CSE',
          rollNumber: '1234567890',
          bookletNumber: '1234567',
          durationMinutes: 30,
          positiveMarks: 2,
          negativeMarks: 0.66,
          unattendedMarks: 0,
          msqScoringPolicy: 'ALL_OR_NOTHING',
          folderId: 'default',
          createdAt: 1_700_000_000_000,
          updatedAt: 1_700_000_005_000,
        },
        questions: [
          {
            num: 1,
            question: 'Which article defines Money Bill?',
            options: { a: '108', b: '110', c: '112', d: '123' },
            correctAnswer: 'B',
            explanation: 'Article 110 defines Money Bills.',
          },
        ],
        rawText: '1. Which article defines Money Bill?\n(A) 108\n(B) 110\n(C) 112\n(D) 123\nAnswer: B',
      },
    ],
    folders: [
      {
        id: 'default',
        name: 'My Question Papers',
        color: '#177E89',
        parentId: null,
        createdAt: 1_700_000_000_000,
      },
    ],
    attempts: [],
    countdowns: [],
    studyNotes: [],
    vocabDecks: [],
    vocabSrs: {},
    englishVariant: 'IN',
    headwordInbox: [],
    dismissedHeadwords: [],
    wotdStates: {},
    promptPresets: [],
    dictationConfig: DEFAULT_DICTATION_CONFIG,
    studyTodos: [],
    pomodoroConfig: DEFAULT_POMODORO_CONFIG,
    dailyAnalytics: {},
    brainLabStats: EMPTY_BRAIN_LAB_STATS,
    brainLabSettings: DEFAULT_BRAIN_LAB_SETTINGS,
    dismissedTips: [],
    themeMode: 'LIGHT',
    colorPreset: 'NORDIC_SLATE',
    fontScaleMode: 'STANDARD_FIXED',
  };
}

test('vaultArchiveEngine builds valid v5 archive and detects missing sections or blank checksums', () => {
  const state = makeWorkspaceState();
  const { rawJson } = buildVaultArchive(state, 'Unit Test Device', 1_700_000_100_000);

  const validPreview = inspectVaultArchiveText(rawJson, 'valid.examset.json');
  assert.equal(validPreview.checksumVerified, true);
  assert.equal(validPreview.manifest.counts.papersCount, 1);

  // Strip papers section AND its checksum -> must still fail checksumVerified
  const strippedObj = JSON.parse(rawJson);
  delete strippedObj.sections.papers;
  delete strippedObj.manifest.entryChecksumsSha256.papers;
  const strippedPreview = inspectVaultArchiveText(JSON.stringify(strippedObj), 'stripped.examset.json');
  assert.equal(strippedPreview.checksumVerified, false);
  assert.match(strippedPreview.checksumDetails, /papers \(missing section\)/);

  // Blank checksum -> must fail verification
  const blankChecksumObj = JSON.parse(rawJson);
  blankChecksumObj.manifest.entryChecksumsSha256.folders = '   ';
  const blankPreview = inspectVaultArchiveText(JSON.stringify(blankChecksumObj), 'blank.examset.json');
  assert.equal(blankPreview.checksumVerified, false);
  assert.match(blankPreview.checksumDetails, /folders \(missing checksum\)/);

  // Smart merge preserves both local and incoming papers cleanly
  const otherState = makeWorkspaceState();
  otherState.papers[0] = {
    ...otherState.papers[0],
    metadata: { ...otherState.papers[0].metadata, id: 'p-2', title: 'Economy Mock 2' },
  };
  const merged = applyVaultRestoreToState(
    otherState,
    validPreview,
    'SMART_MERGE',
    state.folders[0]
  );
  assert.equal(merged.papers.length, 2);
});

test('cloudSyncEngine fails closed without Web Crypto and enforces 15m TTL + refresh dedup', async () => {
  const prevWindow = globalThis.window;
  const prevLocal = globalThis.localStorage;
  const prevSession = globalThis.sessionStorage;
  const prevFetch = globalThis.fetch;
  const cryptoDesc = Object.getOwnPropertyDescriptor(globalThis, 'crypto');

  const localStore = createMemoryStorage();
  const sessionStore = createMemoryStorage();
  const mockWindow = {
    location: {
      origin: 'https://examset.example.test',
      pathname: '/',
      href: 'https://examset.example.test/',
      hash: '',
      search: '?code=abc&state=xyz',
      assign() {},
    },
    history: {
      replaceState() {},
    },
    opener: null,
    self: null as unknown,
    top: null as unknown,
    open: () => null,
    addEventListener() {},
    removeEventListener() {},
  };
  mockWindow.self = mockWindow;
  mockWindow.top = mockWindow;

  Object.defineProperty(globalThis, 'window', { value: mockWindow, configurable: true, writable: true });
  Object.defineProperty(globalThis, 'document', { value: { title: 'ExamSet' }, configurable: true, writable: true });
  Object.defineProperty(globalThis, 'localStorage', { value: localStore, configurable: true, writable: true });
  Object.defineProperty(globalThis, 'sessionStorage', { value: sessionStore, configurable: true, writable: true });

  try {
    CloudSyncEngine.setClientId('GOOGLE_DRIVE', 'test-google-client-id');
    CloudSyncEngine.setClientId('ONEDRIVE', 'test-client-id');

    // 1. Fail-closed when crypto.getRandomValues is absent
    Object.defineProperty(globalThis, 'crypto', { value: undefined, configurable: true });
    await assert.rejects(
      () => CloudSyncEngine.startCloudOAuth('GOOGLE_DRIVE'),
      /Secure Web Crypto random generator is required/
    );
    if (cryptoDesc) {
      Object.defineProperty(globalThis, 'crypto', cryptoDesc);
    }

    // 2. Expired pending OAuth transaction (>15m) rejected
    sessionStore.setItem(
      'examset_oauth_pending_tx_v1',
      JSON.stringify({
        provider: 'ONEDRIVE',
        state: 'xyz',
        codeVerifier: 'verifier_1',
        redirectUri: 'https://examset.example.test/',
        createdAt: Date.now() - 16 * 60 * 1000,
      })
    );
    const expiredResult = await CloudSyncEngine.handleOAuthCallbackIfPresent();
    assert.ok(expiredResult);
    assert.equal(expiredResult?.ok, false);
    assert.match(expiredResult?.message || '', /expired/i);
    assert.equal(sessionStore.getItem('examset_oauth_pending_tx_v1'), null);

    // 3. Concurrent ensureValidAccessToken calls deduplicate OneDrive refresh request
    sessionStore.setItem(
      'examset_sync_token_v1_ONEDRIVE',
      JSON.stringify({
        accessToken: 'expired-token',
        refreshToken: 'valid-refresh-token',
        expiresAt: Date.now() - 10_000,
      })
    );

    let fetchCount = 0;
    Object.defineProperty(globalThis, 'fetch', {
      configurable: true,
      writable: true,
      value: async () => {
        fetchCount++;
        await new Promise((r) => setTimeout(r, 20));
        return {
          ok: true,
          json: async () => ({
            access_token: 'fresh-access-token',
            refresh_token: 'rotated-refresh-token',
            expires_in: 3600,
          }),
        };
      },
    });

    const [t1, t2] = await Promise.all([
      CloudSyncEngine.ensureValidAccessToken('ONEDRIVE'),
      CloudSyncEngine.ensureValidAccessToken('ONEDRIVE'),
    ]);
    assert.equal(t1, 'fresh-access-token');
    assert.equal(t2, 'fresh-access-token');
    assert.equal(fetchCount, 1, 'Concurrent OneDrive refresh calls must share a single token request');
  } finally {
    Object.defineProperty(globalThis, 'window', { value: prevWindow, configurable: true, writable: true });
    Object.defineProperty(globalThis, 'localStorage', { value: prevLocal, configurable: true, writable: true });
    Object.defineProperty(globalThis, 'sessionStorage', { value: prevSession, configurable: true, writable: true });
    Object.defineProperty(globalThis, 'fetch', { value: prevFetch, configurable: true, writable: true });
    if (cryptoDesc) {
      Object.defineProperty(globalThis, 'crypto', cryptoDesc);
    }
  }
});

test('ExamStore snapshot fallback works without IndexedDB and prunes manual/auto caps', async () => {
  const prevLocal = globalThis.localStorage;
  const localStore = createMemoryStorage();
  Object.defineProperty(globalThis, 'localStorage', { value: localStore, configurable: true, writable: true });

  try {
    const state = makeWorkspaceState();
    const { document, rawJson } = buildVaultArchive(state, 'Snapshot Test', 1_700_000_200_000);

    for (let i = 0; i < 12; i++) {
      const snap: VaultSnapshotRecord = {
        id: `snap-m-${i}`,
        label: `Manual ${i}`,
        kind: 'manual',
        createdAt: 1_700_000_000_000 + i,
        manifest: document.manifest,
        rawJson,
      };
      await ExamStore.addVaultSnapshotWithBlob(snap);
    }

    const loaded = ExamStore.loadVaultSnapshots();
    assert.equal(loaded.length, 10, 'Manual snapshots must be capped to 10');
    assert.equal(loaded[0].blobStorage, 'inline');
    assert.equal(loaded[0].sizeBytes, rawJson.length);

    const resolved = await ExamStore.resolveSnapshotRawJson(loaded[0]);
    assert.equal(resolved, rawJson);

    const afterDelete = await ExamStore.deleteVaultSnapshotWithBlob(loaded[0].id);
    assert.equal(afterDelete.length, 9);
  } finally {
    Object.defineProperty(globalThis, 'localStorage', { value: prevLocal, configurable: true, writable: true });
  }
});

test('StudyNoteParser.prepareAppendedContent deduplicates and renumbers sequentially', () => {
  const existing = [
    '## Core Principles',
    '1. **Article 14** -> Equality before law.',
    '2. **Article 21** -> Protection of life and personal liberty.',
  ].join('\n');

  const incoming = [
    '1. **Article 14** -> Equality before law.',
    '2. **Article 32** -> Constitutional Remedies.',
    '3. **Article 44** -> Uniform Civil Code.',
  ].join('\n');

  const preview = StudyNoteParser.prepareAppendedContent(existing, incoming, DEFAULT_KNOWLEDGE_RULE, true);
  assert.equal(preview.addedUnitCount, 2);
  assert.equal(preview.skippedDuplicatesCount, 1);
  assert.match(preview.appendSegment, /^3\. \*\*Article 32\*\*/m);
  assert.match(preview.appendSegment, /^4\. \*\*Article 44\*\*/m);
  assert.match(preview.combinedContent, /Article 21[\s\S]*3\. \*\*Article 32\*\*/);
});

test('vaultArchiveEngine round-trips knowledgeCourses, course, and masteredIndices across Clean Replace & Smart Merge', () => {
  const incomingState = makeWorkspaceState();
  incomingState.knowledgeCourses = {
    'UPSC GS': ['Polity', 'Economy'],
    'SSC Tier-2': ['Quant'],
  };
  incomingState.studyNotes = [
    {
      id: 'note-1',
      title: 'Fundamental Rights',
      course: 'UPSC GS',
      subject: 'Polity',
      sourceAi: 'Custom',
      rawContent: '1. **Art 14** -> Equality\n2. **Art 19** -> Freedoms\n3. **Art 21** -> Life',
      folderId: 'default',
      collectionType: 'ONE_LINER',
      formatRule: DEFAULT_KNOWLEDGE_RULE,
      bookmarkedIndices: [1],
      masteredIndices: [0, 2],
      srsStates: {},
      createdAt: 1_700_000_000_000,
      updatedAt: 1_700_000_100_000,
    },
  ];

  const { rawJson } = buildVaultArchive(incomingState, 'Course Device', 1_700_000_300_000);
  const preview = inspectVaultArchiveText(rawJson, 'courses.examset.json');
  assert.equal(preview.checksumVerified, true);
  assert.equal(preview.manifest.counts.knowledgeCoursesCount, 2);

  // Smart Merge into local state having different course + overlapping note with different masteredIndices
  const localState = makeWorkspaceState();
  localState.knowledgeCourses = {
    'UPSC GS': ['History'],
    'State PSC': ['Geography'],
  };
  localState.studyNotes = [
    {
      ...incomingState.studyNotes[0],
      bookmarkedIndices: [0],
      masteredIndices: [1],
      updatedAt: 1_700_000_050_000,
    },
  ];

  const merged = applyVaultRestoreToState(localState, preview, 'SMART_MERGE', localState.folders[0]);
  assert.deepEqual(merged.knowledgeCourses?.['UPSC GS'], ['History', 'Polity', 'Economy']);
  assert.deepEqual(merged.knowledgeCourses?.['SSC Tier-2'], ['Quant']);
  assert.deepEqual(merged.knowledgeCourses?.['State PSC'], ['Geography']);
  assert.equal(merged.studyNotes.length, 1);
  assert.equal(merged.studyNotes[0].course, 'UPSC GS');
  assert.deepEqual(merged.studyNotes[0].masteredIndices?.slice().sort(), [0, 1, 2]);
  assert.deepEqual(merged.studyNotes[0].bookmarkedIndices?.slice().sort(), [0, 1]);

  // Clean Replace replaces course map cleanly
  const replaced = applyVaultRestoreToState(localState, preview, 'FULL_REPLACE', localState.folders[0]);
  assert.deepEqual(replaced.knowledgeCourses, incomingState.knowledgeCourses);
  assert.deepEqual(replaced.studyNotes[0].masteredIndices, [0, 2]);

  // Backward compatibility: older v5 archive without knowledgeCourses still verifies cleanly
  const legacyObj = JSON.parse(rawJson);
  delete legacyObj.sections.brainAndPreferences.knowledgeCourses;
  // Re-build checksum on brainAndPreferences to simulate older v5 generator
  const olderState = makeWorkspaceState();
  const { rawJson: olderRaw } = buildVaultArchive(olderState, 'Legacy v5', 1_700_000_000_000);
  const olderPreview = inspectVaultArchiveText(olderRaw, 'older.examset.json');
  assert.equal(olderPreview.checksumVerified, true);
  assert.equal(olderPreview.manifest.counts.knowledgeCoursesCount, 0);
});

test('vaultArchiveEngine merges canonical vocab buckets with SRS remap, validates folderId, and handles activeSessions', () => {
  const localState = makeWorkspaceState();
  localState.vocabDecks = [
    {
      id: 'deck_local_c1',
      title: 'English • C1',
      language: 'English',
      languageVariant: 'UK',
      translationLanguage: 'Hindi',
      levelCode: 'C1',
      customLevelLabel: '',
      domain: 'General',
      createdAt: 1000,
      updatedAt: 2000,
      entries: [
        {
          id: 'w_loc_ephemeral',
          word: 'Ephemeral',
          pronunciation: '',
          partOfSpeech: 'adjective',
          levelCode: 'C1',
          customLevelLabel: '',
          language: 'English',
          languageVariant: 'UK',
          meaning: 'Short',
          translation: '',
          translationLanguage: 'Hindi',
          examples: [{ context: 'Daily', sentence: 'Fame is ephemeral.', translation: '' }],
          synonyms: ['fleeting'],
          antonyms: [],
          collocations: [],
          usageNote: '',
          isBookmarked: true,
          createdAt: 1000,
          updatedAt: 1500,
        },
      ],
    },
  ];
  localState.activeSessions = {
    'stale-paper': {
      paperId: 'stale-paper',
      interfaceMode: 'TCS_ION_CBT',
      timerMode: 'COUNTDOWN',
      shuffleOptions: false,
      shuffleSeed: 1,
      currentIndex: 2,
      answers: { 1: 'A' },
      markedForReview: {},
      visited: { 1: true },
      questionTimes: { 1: 12 },
      timeLeftSeconds: 600,
      elapsedSeconds: 45,
      targetDurationSeconds: 900,
      startedAt: 1_700_000_010_000,
    },
  };

  const incomingState = makeWorkspaceState();
  incomingState.studyNotes = [
    {
      id: 'note-orphan',
      title: 'Orphaned Folder Note',
      subject: 'Polity',
      sourceAi: 'Custom',
      rawContent: '1. **Rule** -> Valid fallback',
      folderId: 'missing_folder_id',
      collectionType: 'ONE_LINERS',
      formatRule: DEFAULT_KNOWLEDGE_RULE,
      bookmarkedIndices: [],
      masteredIndices: [],
      srsStates: {},
      createdAt: 1000,
      updatedAt: 2000,
    },
  ];
  incomingState.vocabDecks = [
    {
      id: 'deck_remote_c1_other_uuid',
      title: 'English C1 Remote',
      language: 'english',
      languageVariant: 'UK',
      translationLanguage: 'Hindi',
      levelCode: 'C1',
      customLevelLabel: '',
      domain: 'Editorial',
      createdAt: 1000,
      updatedAt: 5000,
      entries: [
        {
          id: 'w_rem_ephemeral',
          word: 'Ephemeral',
          pronunciation: '/ɪˈfɛm.ər.əl/',
          partOfSpeech: 'adjective',
          levelCode: 'C1',
          customLevelLabel: '',
          language: 'English',
          languageVariant: 'UK',
          meaning: 'Lasting for a very short time',
          translation: 'क्षणिक',
          translationLanguage: 'Hindi',
          examples: [
            { context: 'Daily', sentence: 'Fame is ephemeral.', translation: '' },
            { context: 'Editorial', sentence: 'Spring blossoms are ephemeral.', translation: '' },
          ],
          synonyms: ['Fleeting', 'transitory'],
          antonyms: ['permanent'],
          collocations: [],
          usageNote: 'Common in essays',
          isBookmarked: false,
          createdAt: 1000,
          updatedAt: 5000,
        },
        {
          id: 'w_rem_lucid',
          word: 'Lucid',
          pronunciation: '',
          partOfSpeech: 'adjective',
          levelCode: 'C1',
          customLevelLabel: '',
          language: 'English',
          languageVariant: 'UK',
          meaning: 'Expressed clearly',
          translation: '',
          translationLanguage: 'Hindi',
          examples: [],
          synonyms: [],
          antonyms: [],
          collocations: [],
          usageNote: '',
          isBookmarked: true,
          createdAt: 2000,
          updatedAt: 2000,
        },
      ],
    },
  ];
  incomingState.vocabSrs = {
    deck_remote_c1_other_uuid: {
      w_rem_ephemeral: {
        blockIndex: 0,
        repetitions: 3,
        easeFactor: 2.6,
        intervalDays: 6,
        dueAtMillis: 1_790_000_000_000,
        lastReviewedAtMillis: 1_789_000_000_000,
        lapses: 0,
        lastRating: 'GOOD',
        reviewCount: 5,
        correctCount: 5,
      },
      w_rem_lucid: {
        blockIndex: 1,
        repetitions: 1,
        easeFactor: 2.5,
        intervalDays: 1,
        dueAtMillis: 1_790_000_000_000,
        lastReviewedAtMillis: 1_789_000_000_000,
        lapses: 0,
        lastRating: 'GOOD',
        reviewCount: 1,
        correctCount: 1,
      },
    },
  };
  incomingState.activeSessions = {
    'p-1': {
      paperId: 'p-1',
      interfaceMode: 'TCS_ION_CBT',
      timerMode: 'COUNTDOWN',
      shuffleOptions: false,
      shuffleSeed: 42,
      currentIndex: 0,
      answers: {},
      markedForReview: {},
      visited: { 0: true },
      questionTimes: {},
      timeLeftSeconds: 1700,
      elapsedSeconds: 100,
      targetDurationSeconds: 1800,
      startedAt: 1_700_000_200_000,
    },
  };

  const { rawJson } = buildVaultArchive(incomingState, 'Remote Device', 1_700_000_300_000);
  const preview = inspectVaultArchiveText(rawJson, 'merge_vocab.examset.json');
  assert.equal(preview.checksumVerified, true);

  const merged = applyVaultRestoreToState(localState, preview, 'SMART_MERGE', localState.folders[0]);
  assert.equal(merged.vocabDecks.length, 1, 'Same canonical bucket must consolidate without duplicating decks');
  assert.equal(merged.vocabDecks[0].id, 'deck_local_c1');
  assert.equal(merged.vocabDecks[0].entries.length, 2);
  const ephemeral = merged.vocabDecks[0].entries.find((e) => e.word === 'Ephemeral')!;
  assert.equal(ephemeral.meaning, 'Lasting for a very short time');
  assert.equal(ephemeral.isBookmarked, true);
  assert.equal(ephemeral.examples.length, 2);
  assert.deepEqual(ephemeral.synonyms, ['fleeting', 'transitory']);
  assert.equal(merged.vocabSrs['deck_local_c1']?.['w_loc_ephemeral']?.reviewCount, 5);
  assert.equal(merged.vocabSrs['deck_local_c1']?.['w_rem_lucid']?.reviewCount, 1);
  assert.equal(merged.studyNotes[0].folderId, 'default');
  assert.ok(merged.activeSessions?.['stale-paper']);
  assert.ok(merged.activeSessions?.['p-1']);

  // FULL_REPLACE clears stale sessions & falls back invalid appearance values
  const tamperedAppearance = JSON.parse(rawJson);
  tamperedAppearance.sections.brainAndPreferences.themeMode = 'INVALID_MODE';
  tamperedAppearance.sections.brainAndPreferences.colorPreset = 'BAD_PRESET';
  tamperedAppearance.sections.plannerAndAnalytics.activeSessions = {};
  const replacePreview = inspectVaultArchiveText(JSON.stringify(tamperedAppearance), 'replace.examset.json');
  const replaced = applyVaultRestoreToState(localState, replacePreview, 'FULL_REPLACE', localState.folders[0]);
  assert.deepEqual(replaced.activeSessions, {});
  assert.equal(replaced.themeMode, 'LIGHT');
  assert.equal(replaced.colorPreset, 'NORDIC_SLATE');
  assert.equal(replaced.studyNotes[0].folderId, 'default');
});

function buildZipBytesForTest(
  entries: Array<{ name: string; bytes: Uint8Array; deflate?: boolean; dataDescriptor?: boolean }>
): Uint8Array {
  const localParts: Uint8Array[] = [];
  const cdParts: Uint8Array[] = [];
  let offset = 0;

  for (const e of entries) {
    const nameBytes = new TextEncoder().encode(e.name);
    const compressed = e.deflate ? new Uint8Array(deflateRawSync(e.bytes)) : e.bytes;
    const method = e.deflate ? 8 : 0;
    const flags = e.dataDescriptor ? 0x08 : 0;

    // Local File Header (with 0 sizes when dataDescriptor=true to test Central Directory precedence)
    const lfh = new Uint8Array(30 + nameBytes.length);
    const lv = new DataView(lfh.buffer);
    lv.setUint32(0, 0x04034b50, true);
    lv.setUint16(4, 20, true);
    lv.setUint16(6, flags, true);
    lv.setUint16(8, method, true);
    lv.setUint32(18, e.dataDescriptor ? 0 : compressed.length, true);
    lv.setUint32(22, e.dataDescriptor ? 0 : e.bytes.length, true);
    lv.setUint16(26, nameBytes.length, true);
    lfh.set(nameBytes, 30);

    const dd = e.dataDescriptor ? new Uint8Array(12) : new Uint8Array(0);
    if (e.dataDescriptor) {
      const dv = new DataView(dd.buffer);
      dv.setUint32(4, compressed.length, true);
      dv.setUint32(8, e.bytes.length, true);
    }

    const cdh = new Uint8Array(46 + nameBytes.length);
    const cv = new DataView(cdh.buffer);
    cv.setUint32(0, 0x02014b50, true);
    cv.setUint16(4, 20, true);
    cv.setUint16(6, 20, true);
    cv.setUint16(8, flags, true);
    cv.setUint16(10, method, true);
    cv.setUint32(20, compressed.length, true);
    cv.setUint32(24, e.bytes.length, true);
    cv.setUint16(28, nameBytes.length, true);
    cv.setUint32(42, offset, true);
    cdh.set(nameBytes, 46);

    localParts.push(lfh, compressed, dd);
    cdParts.push(cdh);
    offset += lfh.length + compressed.length + dd.length;
  }

  const cdOffset = offset;
  const cdSize = cdParts.reduce((s, p) => s + p.length, 0);
  const eocd = new Uint8Array(22);
  const ev = new DataView(eocd.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(8, entries.length, true);
  ev.setUint16(10, entries.length, true);
  ev.setUint32(12, cdSize, true);
  ev.setUint32(16, cdOffset, true);

  const totalLen = cdOffset + cdSize + eocd.length;
  const out = new Uint8Array(totalLen);
  let pos = 0;
  for (const part of [...localParts, ...cdParts, eocd]) {
    out.set(part, pos);
    pos += part.length;
  }
  return out;
}

test('zipVaultReader verifies and converts Android v5 .examset.zip (DEFLATE + data descriptors) and detects tampering', async () => {
  const enc = (s: string) => new TextEncoder().encode(s);
  const foldersBytes = enc(JSON.stringify([{ id: 'fld_sci', name: 'Science', color: '#0284C7', parentId: null, createdAt: 1000 }]));
  const papersBytes = enc(
    `${JSON.stringify({
      id: 7,
      title: 'Android Physics Paper',
      subject: 'Physics',
      topic: 'Optics',
      setNumber: '02',
      exam: 'JEE',
      questionCount: 1,
      answerKeyCount: 1,
      explanationCount: 1,
      rawText: '1. Speed of light?\n(A) c\n(B) 2c\n(C) 3c\n(D) 4c\nAnswer Key:\n1: A',
      createdAt: 1000,
      updatedAt: 2000,
      folderId: 'fld_sci',
      durationMinutes: 45,
      positiveMarks: 4,
      negativeMarks: 1,
      unattendedMarks: 0,
      manualCategory: 'MOCK_TEST',
    })}\n`
  );
  const notesBytes = enc(
    `${JSON.stringify({
      id: 3,
      folderId: 'fld_sci',
      title: 'Optics Summary',
      subject: 'Physics',
      course: 'JEE Core',
      sourceAi: 'Notes',
      rawContent: '1. **Snell Law** -> n1 sin i = n2 sin r',
      bookmarkedLinesJson: '[0]',
      createdAt: 1000,
      updatedAt: 3000,
      collectionType: 'ONE_LINER',
      customLabel: '',
      linkedPaperId: 7,
      formatRuleJson: '{}',
      masteredBlocksJson: '[0]',
      syncUid: 'uid-optics-3',
    })}\n`
  );
  const attemptsBytes = enc(
    `${JSON.stringify({
      id: 9,
      paperId: 7,
      answersJson: JSON.stringify({ '1': 'A', _qTime: { '1': 14 }, _rollNumber: '0000000099' }),
      isSubmitted: true,
      score: 4,
      correctCount: 1,
      incorrectCount: 0,
      unansweredCount: 0,
      totalQuestions: 1,
      timeSpentSeconds: 14,
      createdAt: 2500,
      submittedAt: 2514,
    })}\n`
  );
  const analyticsBytes = enc(
    JSON.stringify([{ dateString: '2026-11-26', totalTestTimeSeconds: 300, testsCompletedCount: 1, questionsAttemptedCount: 1, correctAnswersCount: 1 }])
  );
  const vocabBytes = enc(
    JSON.stringify({
      vocabularyDecksJson: JSON.stringify([]),
      vocabularySrsJson: JSON.stringify({}),
      preferredEnglishVariant: 'IN',
      languageWordOfTheDayStatesJson: JSON.stringify({}),
      headwordInboxJson: JSON.stringify([]),
      dismissedWordKeysJson: JSON.stringify([]),
    })
  );
  const prefsBytes = enc(
    JSON.stringify({
      dailyGoalMinutes: 60,
      fontScaleMode: 'LARGE',
      themeMode: 'DARK',
      colorSchemePreset: 'AMOLED_DARK',
      defaultMsqScoringPolicy: 'PARTIAL_STRICT',
      paperMsqPoliciesJson: JSON.stringify({ '7': 'PARTIAL_STRICT' }),
      flashcardSrsByNoteUid: {
        'uid-optics-3': [{ blockIndex: 0, repetitions: 2, easeFactor: 2.5, intervalDays: 3, dueAtMillis: 9000, lastReviewedAtMillis: 4000, lapses: 0, lastRating: 'GOOD', reviewCount: 3, correctCount: 3 }],
      },
      promptPresetsJson: '[]',
      examCountdownsJson: JSON.stringify([{ id: 'cd-1', title: 'JEE Main', targetDateStr: '2027-01-20', subject: 'Physics', colorHex: '#177E89' }]),
      studyTodosJson: JSON.stringify([{ id: 'td-1', title: 'Revise Optics', priority: 'HIGH', linkedType: 'NOTE', linkedNoteId: 3 }]),
      knowledgeCoursesJson: JSON.stringify({ 'JEE Core': ['Physics', 'Maths'] }),
    })
  );

  const checksums = {
    'folders.json': sha256HexBytesSync(foldersBytes),
    'papers.ndjson': sha256HexBytesSync(papersBytes),
    'notes.ndjson': sha256HexBytesSync(notesBytes),
    'attempts.ndjson': sha256HexBytesSync(attemptsBytes),
    'analytics.json': sha256HexBytesSync(analyticsBytes),
    'vocabulary.json': sha256HexBytesSync(vocabBytes),
    'preferences.json': sha256HexBytesSync(prefsBytes),
  };
  const manifestBytes = enc(
    JSON.stringify({
      schemaVersion: 5,
      appVersion: '1.0',
      createdAt: 1_700_000_500_000,
      deviceName: 'Pixel Test',
      entryChecksumsSha256: checksums,
    })
  );

  const zipBytes = buildZipBytesForTest([
    { name: 'folders.json', bytes: foldersBytes, deflate: true, dataDescriptor: true },
    { name: 'papers.ndjson', bytes: papersBytes, deflate: true, dataDescriptor: true },
    { name: 'notes.ndjson', bytes: notesBytes, deflate: true, dataDescriptor: true },
    { name: 'attempts.ndjson', bytes: attemptsBytes, deflate: false },
    { name: 'analytics.json', bytes: analyticsBytes, deflate: true },
    { name: 'vocabulary.json', bytes: vocabBytes, deflate: true },
    { name: 'preferences.json', bytes: prefsBytes, deflate: true },
    { name: 'manifest.json', bytes: manifestBytes, deflate: true },
  ]);

  assert.equal(isZipMagicBytes(zipBytes), true);
  const preview = await inspectAndroidZipArchive(zipBytes, 'android.examset.zip');
  assert.equal(preview.checksumVerified, true, preview.checksumDetails);
  assert.equal(preview.manifest.counts.papersCount, 1);
  assert.equal(preview.manifest.counts.notesCount, 1);
  assert.equal(preview.manifest.counts.knowledgeCoursesCount, 1);

  const base = makeWorkspaceState();
  const restored = applyVaultRestoreToState(base, preview, 'FULL_REPLACE', base.folders[0]);
  assert.equal(restored.papers[0].metadata.msqScoringPolicy, 'PARTIAL_STRICT');
  assert.equal(restored.papers[0].metadata.manualCategory, 'FULL_MOCK');
  assert.equal(restored.studyNotes[0].id, 'uid-optics-3');
  assert.equal(restored.studyNotes[0].course, 'JEE Core');
  assert.equal(restored.studyNotes[0].linkedPaperId, 'paper-7');
  assert.equal(restored.studyNotes[0].srsStates[0].reviewCount, 3);
  assert.equal(restored.studyTodos[0].linkedNoteId, 'uid-optics-3');
  assert.equal(restored.themeMode, 'DARK');
  assert.equal(restored.colorPreset, 'OLED_OBSIDIAN');
  assert.equal(restored.fontScaleMode, 'LARGE');
  assert.deepEqual(restored.knowledgeCourses?.['JEE Core'], ['Physics', 'Maths']);

  // Tamper notes.ndjson -> must fail checksumVerified
  const tamperedZip = buildZipBytesForTest([
    { name: 'folders.json', bytes: foldersBytes, deflate: true },
    { name: 'papers.ndjson', bytes: papersBytes, deflate: true },
    { name: 'notes.ndjson', bytes: enc('{"tampered":true}\n'), deflate: true },
    { name: 'attempts.ndjson', bytes: attemptsBytes, deflate: true },
    { name: 'analytics.json', bytes: analyticsBytes, deflate: true },
    { name: 'vocabulary.json', bytes: vocabBytes, deflate: true },
    { name: 'preferences.json', bytes: prefsBytes, deflate: true },
    { name: 'manifest.json', bytes: manifestBytes, deflate: true },
  ]);
  const badPreview = await inspectAndroidZipArchive(tamperedZip, 'bad.examset.zip');
  assert.equal(badPreview.checksumVerified, false);
  assert.match(badPreview.checksumDetails, /notes\.ndjson \(hash mismatch\)/);
});

test('cloudSyncEngine blocks OAuth under file:// origin, excludes Drive folders, and deletes remote backups', async () => {
  const prevWindow = globalThis.window;
  const prevLocal = globalThis.localStorage;
  const prevSession = globalThis.sessionStorage;
  const prevFetch = globalThis.fetch;

  const localStore = createMemoryStorage();
  const sessionStore = createMemoryStorage();
  const fileWindow = {
    location: { protocol: 'file:', origin: 'null', pathname: '/ExamSet-Standalone.html', href: 'file:///ExamSet-Standalone.html', hash: '', search: '' },
    history: { replaceState() {} },
    opener: null,
    self: null as unknown,
    top: null as unknown,
  };
  fileWindow.self = fileWindow;
  fileWindow.top = fileWindow;

  Object.defineProperty(globalThis, 'window', { value: fileWindow, configurable: true, writable: true });
  Object.defineProperty(globalThis, 'localStorage', { value: localStore, configurable: true, writable: true });
  Object.defineProperty(globalThis, 'sessionStorage', { value: sessionStore, configurable: true, writable: true });

  try {
    assert.equal(CloudSyncEngine.isFileProtocolOrigin(), true);
    CloudSyncEngine.setClientId('GOOGLE_DRIVE', 'client-id-1');
    await assert.rejects(
      () => CloudSyncEngine.startCloudOAuth('GOOGLE_DRIVE'),
      /Quick Disk Sync/i
    );

    // Switch to HTTPS origin to verify Drive list query filter & remote deletion
    fileWindow.location.protocol = 'https:';
    fileWindow.location.origin = 'https://examset.example.test';
    sessionStore.setItem(
      'examset_sync_token_v1_GOOGLE_DRIVE',
      JSON.stringify({ accessToken: 'tok', refreshToken: '', expiresAt: Date.now() + 600_000 })
    );

    const fetchedCalls: { url: string; method: string }[] = [];
    Object.defineProperty(globalThis, 'fetch', {
      configurable: true,
      writable: true,
      value: async (url: string, init?: { method?: string }) => {
        fetchedCalls.push({ url, method: init?.method || 'GET' });
        if (url.includes('spaces=drive')) {
          return { ok: true, json: async () => ({ files: [{ id: 'folder-123' }] }) };
        }
        if (url.includes('orderBy=modifiedTime')) {
          return { ok: true, json: async () => ({ files: [{ id: 'file-1', name: 'Vault.examset.json', size: '1024', modifiedTime: '2026-11-27T00:00:00Z' }] }) };
        }
        return { ok: true, status: 204, json: async () => ({}) };
      },
    });

    const list = await CloudSyncEngine.listCloudBackups('GOOGLE_DRIVE');
    assert.equal(list.length, 1);
    const listCall = fetchedCalls.find((c) => c.url.includes('orderBy=modifiedTime'))!;
    assert.match(decodeURIComponent(listCall.url), /mimeType!='application\/vnd\.google-apps\.folder'/);

    await CloudSyncEngine.deleteCloudBackup(list[0]);
    const delCall = fetchedCalls.find((c) => c.method === 'DELETE');
    assert.ok(delCall?.url.includes('/files/file-1'));
  } finally {
    Object.defineProperty(globalThis, 'window', { value: prevWindow, configurable: true, writable: true });
    Object.defineProperty(globalThis, 'localStorage', { value: prevLocal, configurable: true, writable: true });
    Object.defineProperty(globalThis, 'sessionStorage', { value: prevSession, configurable: true, writable: true });
    Object.defineProperty(globalThis, 'fetch', { value: prevFetch, configurable: true, writable: true });
  }
});

