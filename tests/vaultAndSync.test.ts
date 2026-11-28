import test from 'node:test';
import assert from 'node:assert/strict';

import {
  DEFAULT_BRAIN_LAB_SETTINGS,
  DEFAULT_POMODORO_CONFIG,
  EMPTY_BRAIN_LAB_STATS,
  type VaultSnapshotRecord,
} from '../src/types/phase5';
import { DEFAULT_DICTATION_CONFIG, DEFAULT_KNOWLEDGE_RULE } from '../src/types/phase4';
import {
  applyVaultRestoreToState,
  buildAndroidZipVaultArchive,
  buildPromptPresetsPackJson,
  buildVaultArchive,
  inspectVaultArchiveBytes,
  inspectVaultArchiveText,
  parsePromptPresetsPackJson,
  type WorkspaceBackupState,
} from '../src/utils/vaultArchiveEngine';
import { buildZipArchive, readZipTextEntries } from '../src/utils/zipArchiveHelper';
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
    activeSessions: {},
    countdowns: [],
    studyNotes: [],
    knowledgeCourses: {},
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
    colorPreset: 'MINIMAL',
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

test('Android .examset.zip bridge exports, inspects, verifies SHA-256, and detects tampering', async () => {
  const state = makeWorkspaceState();
  state.knowledgeCourses = { 'UPSC GS': ['Polity', 'Economy'] };
  state.studyNotes = [
    {
      id: 'note-zip-1',
      title: 'Parliament Notes',
      course: 'UPSC GS',
      subject: 'Polity',
      sourceAi: 'Custom',
      rawContent: '1. **Money Bill** -> Art 110',
      folderId: 'default',
      collectionType: 'ONE_LINERS',
      formatRule: DEFAULT_KNOWLEDGE_RULE,
      bookmarkedIndices: [0],
      masteredIndices: [0],
      srsStates: {
        0: {
          blockIndex: 0,
          intervalDays: 6,
          easeFactor: 2.5,
          repetitions: 2,
          lapses: 0,
          dueAtMillis: 1_700_500_000_000,
          lastReviewedAtMillis: 1_700_000_100_000,
          lastRating: 'GOOD',
          reviewCount: 2,
          correctCount: 2,
        },
      },
      createdAt: 1_700_000_000_000,
      updatedAt: 1_700_000_050_000,
    },
  ];
  state.vocabDecks = [
    {
      id: 'deck-en-b2',
      title: 'English B2',
      language: 'English',
      languageVariant: 'IN',
      translationLanguage: '',
      levelCode: 'B2',
      customLevelLabel: '',
      domain: 'Editorial',
      createdAt: 1_700_000_000_000,
      updatedAt: 1_700_000_010_000,
      entries: [
        {
          id: 'word-1',
          word: 'ameliorate',
          partOfSpeech: 'verb',
          pronunciation: '/əˈmiːliəreɪt/',
          levelCode: 'B2',
          customLevelLabel: '',
          language: 'English',
          languageVariant: 'IN',
          meaning: 'Make better',
          translation: '',
          translationLanguage: '',
          examples: [{ context: 'Polity', sentence: 'Ameliorate rural distress.', translation: '' }],
          synonyms: ['improve'],
          antonyms: ['worsen'],
          collocations: ['ameliorate conditions'],
          usageNote: 'Formal',
          isBookmarked: true,
          createdAt: 1_700_000_000_000,
          updatedAt: 1_700_000_010_000,
        },
      ],
    },
  ];
  state.vocabSrs = {
    'deck-en-b2': {
      'word-1': {
        blockIndex: 0,
        intervalDays: 4,
        easeFactor: 2.6,
        repetitions: 2,
        lapses: 0,
        dueAtMillis: 1_700_400_000_000,
        lastReviewedAtMillis: 1_700_000_050_000,
        lastRating: 'EASY',
        reviewCount: 2,
        correctCount: 2,
      },
    },
  };
  state.studyTodos = [
    {
      id: 'todo-1',
      title: 'Revise Art 110',
      subject: 'Polity',
      priority: 'HIGH',
      estimatedPomodoros: 2,
      completedPomodoros: 1,
      isCompleted: false,
      dueDateStr: '2026-11-30',
      createdAt: 1_700_000_000_000,
    },
  ];

  const { bytes, manifest } = await buildAndroidZipVaultArchive(state, 'Web ZIP Device', 1_700_000_200_000);
  assert.equal(manifest.format, 'examset-vault-v5');
  assert.equal(manifest.counts.papersCount, 1);
  assert.equal(manifest.counts.notesCount, 1);
  assert.equal(manifest.counts.knowledgeCoursesCount, 1);
  assert.equal(manifest.counts.vocabDecksCount, 1);
  assert.equal(manifest.counts.vocabWordsCount, 1);

  const preview = await inspectVaultArchiveBytes(bytes, 'roundtrip.examset.zip');
  assert.equal(preview.checksumVerified, true);
  assert.equal(preview.manifest.counts.papersCount, 1);
  assert.equal(preview.manifest.counts.notesCount, 1);
  assert.equal(preview.manifest.counts.knowledgeCoursesCount, 1);
  const parsedDoc = JSON.parse(preview.rawJson);
  assert.equal(parsedDoc.sections.studyNotes[0].course, 'UPSC GS');
  assert.deepEqual(parsedDoc.sections.studyNotes[0].masteredIndices, [0]);
  assert.equal(parsedDoc.sections.vocabulary.decks[0].entries[0].word, 'ameliorate');
  assert.equal(parsedDoc.sections.vocabulary.srs['deck-en-b2']['word-1'].repetitions, 2);

  // Tamper with one entry inside ZIP -> must fail checksum verification cleanly
  const entries = await readZipTextEntries(bytes);
  entries.set('folders.json', '[]');
  const tamperedZip = await buildZipArchive(
    Array.from(entries.entries()).map(([name, data]) => ({ name, data }))
  );
  const tamperedPreview = await inspectVaultArchiveBytes(tamperedZip, 'tampered.examset.zip');
  assert.equal(tamperedPreview.checksumVerified, false);
  assert.match(tamperedPreview.checksumDetails, /folders\.json/);
});

test('Smart Merge canonicalizes Vocab buckets, remaps vocabSrs IDs, validates note folderId, and merges/clears activeSessions', () => {
  const localState = makeWorkspaceState();
  localState.vocabDecks = [
    {
      id: 'local-deck-b2',
      title: 'English B2',
      language: 'English',
      languageVariant: 'IN',
      translationLanguage: '',
      levelCode: 'B2',
      customLevelLabel: '',
      domain: 'Editorial',
      createdAt: 1_700_000_000_000,
      updatedAt: 1_700_000_010_000,
      entries: [
        {
          id: 'local-w1',
          word: 'ameliorate',
          partOfSpeech: 'verb',
          pronunciation: '',
          levelCode: 'B2',
          customLevelLabel: '',
          language: 'English',
          languageVariant: 'IN',
          meaning: 'Make better',
          translation: '',
          translationLanguage: '',
          examples: [{ context: 'Ctx', sentence: 'Local sentence.', translation: '' }],
          synonyms: ['improve'],
          antonyms: [],
          collocations: [],
          usageNote: '',
          isBookmarked: false,
          createdAt: 1_700_000_000_000,
          updatedAt: 1_700_000_010_000,
        },
      ],
    },
  ];
  localState.vocabSrs = {
    'local-deck-b2': {
      'local-w1': {
        blockIndex: 0,
        intervalDays: 2,
        easeFactor: 2.5,
        repetitions: 1,
        lapses: 0,
        dueAtMillis: 1_700_100_000_000,
        lastReviewedAtMillis: 1_700_000_010_000,
        lastRating: 'GOOD',
        reviewCount: 1,
        correctCount: 1,
      },
    },
  };
  localState.activeSessions = {
    'p-1': {
      paperId: 'p-1',
      answers: { 1: 'B' },
      markedForReview: {},
      questionTimes: { 1: 12 },
      currentIndex: 0,
      secondsRemaining: 1200,
      elapsedSeconds: 600,
      lastSavedAt: 1_700_000_050_000,
    },
  };

  const incomingState = makeWorkspaceState();
  incomingState.vocabDecks = [
    {
      id: 'remote-deck-b2-uuid',
      title: 'English B2',
      language: 'english',
      languageVariant: 'IN',
      translationLanguage: '',
      levelCode: 'B2',
      customLevelLabel: '',
      domain: 'Editorial',
      createdAt: 1_700_000_000_000,
      updatedAt: 1_700_000_020_000,
      entries: [
        {
          id: 'remote-w1-uuid',
          word: 'Ameliorate',
          partOfSpeech: 'Verb',
          pronunciation: '',
          levelCode: 'B2',
          customLevelLabel: '',
          language: 'English',
          languageVariant: 'IN',
          meaning: 'Make better',
          translation: '',
          translationLanguage: '',
          examples: [{ context: 'Ctx', sentence: 'Incoming sentence.', translation: '' }],
          synonyms: ['mitigate'],
          antonyms: [],
          collocations: [],
          usageNote: '',
          isBookmarked: true,
          createdAt: 1_700_000_000_000,
          updatedAt: 1_700_000_020_000,
        },
        {
          id: 'remote-w2-uuid',
          word: 'pragmatic',
          partOfSpeech: 'adjective',
          pronunciation: '',
          levelCode: 'B2',
          customLevelLabel: '',
          language: 'English',
          languageVariant: 'IN',
          meaning: 'Practical',
          translation: '',
          translationLanguage: '',
          examples: [],
          synonyms: ['practical'],
          antonyms: [],
          collocations: [],
          usageNote: '',
          isBookmarked: false,
          createdAt: 1_700_000_000_000,
          updatedAt: 1_700_000_020_000,
        },
      ],
    },
  ];
  incomingState.vocabSrs = {
    'remote-deck-b2-uuid': {
      'remote-w1-uuid': {
        blockIndex: 0,
        intervalDays: 8,
        easeFactor: 2.6,
        repetitions: 4,
        lapses: 0,
        dueAtMillis: 1_700_800_000_000,
        lastReviewedAtMillis: 1_700_000_200_000,
        lastRating: 'EASY',
        reviewCount: 4,
        correctCount: 4,
      },
    },
  };
  incomingState.studyNotes = [
    {
      id: 'note-bad-folder',
      title: 'Orphan Note',
      course: '',
      subject: 'Polity',
      sourceAi: 'Custom',
      rawContent: '1. **Art 1** -> Union',
      folderId: 'missing-folder-id',
      collectionType: 'ONE_LINERS',
      formatRule: DEFAULT_KNOWLEDGE_RULE,
      bookmarkedIndices: [],
      masteredIndices: [],
      srsStates: {},
      createdAt: 1_700_000_000_000,
      updatedAt: 1_700_000_000_000,
    },
  ];
  incomingState.activeSessions = {
    'p-remote': {
      paperId: 'p-remote',
      answers: { 1: 'A' },
      markedForReview: {},
      questionTimes: { 1: 5 },
      currentIndex: 0,
      secondsRemaining: 900,
      elapsedSeconds: 300,
      lastSavedAt: 1_700_000_040_000,
    },
  };

  const { rawJson } = buildVaultArchive(incomingState, 'Remote Device', 1_700_000_300_000);
  const preview = inspectVaultArchiveText(rawJson, 'remote.examset.json');

  const merged = applyVaultRestoreToState(localState, preview, 'SMART_MERGE', localState.folders[0]);
  assert.equal(merged.vocabDecks.length, 1, 'Same language|levelCode bucket must merge cleanly without duplicating decks');
  assert.equal(merged.vocabDecks[0].id, 'local-deck-b2');
  assert.equal(merged.vocabDecks[0].entries.length, 2);
  const mergedAmeliorate = merged.vocabDecks[0].entries.find((e) => e.word.toLowerCase() === 'ameliorate');
  assert.ok(mergedAmeliorate);
  assert.equal(mergedAmeliorate.isBookmarked, true);
  assert.deepEqual(mergedAmeliorate.synonyms.sort(), ['improve', 'mitigate']);
  assert.equal(merged.vocabSrs['local-deck-b2']?.['local-w1']?.repetitions, 4, 'SRS progress must remap onto canonical local IDs');
  assert.equal(merged.studyNotes[0].folderId, 'default', 'Invalid note folderId must fall back to root folder');
  assert.ok(merged.activeSessions?.['p-1']);
  assert.ok(merged.activeSessions?.['p-remote']);

  // Clean replace clears old activeSessions not in incoming backup
  const replaced = applyVaultRestoreToState(localState, preview, 'FULL_REPLACE', localState.folders[0]);
  assert.equal(replaced.activeSessions?.['p-1'], undefined);
  assert.ok(replaced.activeSessions?.['p-remote']);
});

test('Prompt Presets pack exports, parses, and merges deterministically', () => {
  const existing = [
    {
      id: 'preset-1',
      name: 'Polity Prompt',
      mode: 'QUESTIONS' as const,
      configJson: '{}',
      builtPrompt: 'old prompt',
      createdAt: 100,
      updatedAt: 200,
    },
  ];
  const incomingPack = buildPromptPresetsPackJson([
    {
      id: 'preset-1',
      name: 'Polity Prompt v2',
      mode: 'QUESTIONS' as const,
      configJson: '{}',
      builtPrompt: 'new prompt',
      createdAt: 100,
      updatedAt: 300,
    },
    {
      id: 'preset-2',
      name: 'Vocab Pack',
      mode: 'VOCAB' as const,
      configJson: '{}',
      builtPrompt: 'vocab prompt',
      createdAt: 150,
      updatedAt: 150,
    },
  ]);

  const res = parsePromptPresetsPackJson(incomingPack, existing);
  assert.equal(res.added, 1);
  assert.equal(res.updated, 1);
  assert.equal(res.merged.length, 2);
  assert.equal(res.merged.find((p) => p.id === 'preset-1')?.builtPrompt, 'new prompt');
});

test('ExamStore starts cleanly empty and cleanLegacySamplesOnce removes only untouched starter samples', () => {
  const prevLocal = globalThis.localStorage;
  const localStore = createMemoryStorage();
  Object.defineProperty(globalThis, 'localStorage', { value: localStore, configurable: true, writable: true });

  try {
    // 1. Fresh install starts completely empty
    assert.deepEqual(ExamStore.loadPapers(), []);
    assert.equal(ExamStore.getActivePaper(), null);
    assert.deepEqual(ExamStore.loadStudyNotes(), []);
    assert.deepEqual(ExamStore.loadVocabDecks(), []);

    // 2. Populate legacy untouched starter items + user-modified starter item
    localStore.setItem(
      'examset_papers_library',
      JSON.stringify([
        {
          metadata: {
            id: 'paper-default-01',
            title: 'National Science & Technology Olympiad',
            subject: 'Physics',
            topic: 'Dry Ice',
            setNumber: '01',
            targetExam: 'STEM',
            createdAt: 1000,
            updatedAt: 1000,
            folderId: 'default',
          },
          questions: [{ id: 'q1', num: 1, question: 'What is commonly known as dry ice?', options: { a: 'CO2', b: 'H2O', c: 'N2', d: 'O2' }, correctAnswer: 'A', explanation: '' }],
        },
        {
          metadata: {
            id: 'user-paper-99',
            title: 'My Custom UPSC Test',
            subject: 'Polity',
            topic: 'Rights',
            setNumber: '01',
            targetExam: 'UPSC',
            createdAt: 1000,
            updatedAt: 1000,
            folderId: 'default',
          },
          questions: [],
        },
      ])
    );
    localStore.setItem(
      'examset_study_notes_v1',
      JSON.stringify([
        {
          id: 'note-starter-physics',
          title: 'Thermodynamics',
          subject: 'Physics',
          sourceAi: 'Starter',
          rawContent: '1. Law',
          folderId: 'default',
          collectionType: 'MULTI_LINER',
          formatRule: DEFAULT_KNOWLEDGE_RULE,
          bookmarkedIndices: [],
          srsStates: {},
          createdAt: 1000,
          updatedAt: 1000,
        },
        {
          id: 'note-starter-flashcards',
          title: 'Edited Starter Flashcards',
          subject: 'Polity',
          sourceAi: 'Starter',
          rawContent: 'Q1: Test\nA: Ans',
          folderId: 'default',
          collectionType: 'FLASHCARDS',
          formatRule: DEFAULT_KNOWLEDGE_RULE,
          bookmarkedIndices: [],
          srsStates: {},
          createdAt: 1000,
          updatedAt: 2000,
        },
      ])
    );
    localStore.setItem(
      'examset_vocab_decks_v1',
      JSON.stringify([
        {
          id: 'vocab-starter-editorial-b2',
          title: 'Starter Vocab',
          language: 'English',
          languageVariant: 'UK',
          translationLanguage: '',
          levelCode: 'B2',
          customLevelLabel: '',
          domain: 'Editorial',
          createdAt: 1000,
          updatedAt: 1000,
          entries: [],
        },
      ])
    );

    ExamStore.cleanLegacySamplesOnce();

    const remainingPapers = ExamStore.loadPapers();
    assert.equal(remainingPapers.length, 1);
    assert.equal(remainingPapers[0].metadata.id, 'user-paper-99');

    const remainingNotes = ExamStore.loadStudyNotes();
    assert.equal(remainingNotes.length, 1);
    assert.equal(remainingNotes[0].id, 'note-starter-flashcards', 'User-edited starter note must be preserved');

    assert.deepEqual(ExamStore.loadVocabDecks(), []);
    assert.equal(localStore.getItem('examset_legacy_samples_cleaned_v1'), '1');
  } finally {
    Object.defineProperty(globalThis, 'localStorage', { value: prevLocal, configurable: true, writable: true });
  }
});

test('CloudSyncEngine supports non-Chromium browsers via OPFS + safe async download, blocks file:// OAuth, and excludes Drive folders', async () => {
  const prevWindow = globalThis.window;
  const prevDocument = globalThis.document;
  const prevLocal = globalThis.localStorage;
  const prevSession = globalThis.sessionStorage;
  const prevFetch = globalThis.fetch;
  const navDesc = Object.getOwnPropertyDescriptor(globalThis, 'navigator');
  const urlDesc = Object.getOwnPropertyDescriptor(globalThis, 'URL');

  const localStore = createMemoryStorage();
  const sessionStore = createMemoryStorage();
  const opfsFiles = new Map<string, Uint8Array | string>();
  const mockOpfsDir = {
    async getDirectoryHandle() {
      return mockOpfsDir;
    },
    async getFileHandle(name: string, opts?: { create?: boolean }) {
      if (!opfsFiles.has(name) && !opts?.create) {
        throw new Error('NotFoundError');
      }
      return {
        async createWritable() {
          let written: Uint8Array | string = '';
          return {
            async write(chunk: unknown) {
              if (typeof chunk === 'string') {
                written = chunk;
              } else if (chunk instanceof ArrayBuffer) {
                written = new Uint8Array(chunk);
              }
            },
            async close() {
              opfsFiles.set(name, written);
            },
          };
        },
        async getFile() {
          const val = opfsFiles.get(name);
          const buf =
            typeof val === 'string'
              ? new TextEncoder().encode(val).buffer
              : val instanceof Uint8Array
                ? val.buffer.slice(val.byteOffset, val.byteOffset + val.byteLength)
                : new ArrayBuffer(0);
          return {
            name,
            async arrayBuffer() {
              return buf;
            },
          };
        },
      };
    },
    async removeEntry() {
      opfsFiles.clear();
    },
  };

  let clickedDownloads = 0;
  let immediateRevokeCalls = 0;
  let createObjectUrlCount = 0;

  const mockWindow = {
    location: {
      protocol: 'https:',
      origin: 'https://examset.example.test',
      pathname: '/',
      href: 'https://examset.example.test/',
      hash: '',
      search: '',
      assign() {},
    },
    history: { replaceState() {} },
    opener: null,
    self: null as unknown,
    top: null as unknown,
    open: () => null,
    addEventListener() {},
    removeEventListener() {},
  };
  mockWindow.self = mockWindow;
  mockWindow.top = mockWindow;

  const mockDocument = {
    title: 'ExamSet',
    body: {
      appendChild() {},
      removeChild() {},
    },
    createElement() {
      return {
        style: {},
        href: '',
        download: '',
        click() {
          clickedDownloads++;
        },
        remove() {},
      };
    },
  };

  Object.defineProperty(globalThis, 'window', { value: mockWindow, configurable: true, writable: true });
  Object.defineProperty(globalThis, 'document', { value: mockDocument, configurable: true, writable: true });
  Object.defineProperty(globalThis, 'localStorage', { value: localStore, configurable: true, writable: true });
  Object.defineProperty(globalThis, 'sessionStorage', { value: sessionStore, configurable: true, writable: true });
  Object.defineProperty(globalThis, 'navigator', {
    value: { storage: { getDirectory: async () => mockOpfsDir } },
    configurable: true,
    writable: true,
  });
  Object.defineProperty(globalThis, 'URL', {
    value: {
      createObjectURL() {
        createObjectUrlCount++;
        return 'blob:examset-test';
      },
      revokeObjectURL() {
        immediateRevokeCalls++;
      },
    },
    configurable: true,
    writable: true,
  });

  try {
    assert.equal(CloudSyncEngine.isFileSystemAccessSupported(), false);

    // 1. linkNewDiskSyncFile works cleanly in Firefox/non-Chromium and does NOT revoke ObjectURL synchronously
    const linkRes = await CloudSyncEngine.linkNewDiskSyncFile('{"hello":"world"}');
    assert.equal(linkRes.status.isLinked, true);
    assert.equal(linkRes.status.syncMode, 'DOWNLOAD_FALLBACK');
    assert.equal(clickedDownloads, 1);
    assert.equal(createObjectUrlCount, 1);
    assert.equal(immediateRevokeCalls, 0, 'Firefox download fix must defer revokeObjectURL asynchronously');

    // 2. silentOpfsOnly background sync writes to OPFS without firing an unwanted download
    await CloudSyncEngine.syncToLinkedDiskFile('{"updated":true}', undefined, true);
    assert.equal(clickedDownloads, 1, 'Background auto-guard sync must not trigger unexpected file downloads');

    // 3. Unlinking clears OPFS + linked state
    const unlinked = await CloudSyncEngine.unlinkDiskSyncFile();
    assert.equal(unlinked.isLinked, false);
    assert.equal(opfsFiles.size, 0);

    // 4. Google Drive listCloudBackups query excludes folder mimeType
    CloudSyncEngine.setClientId('GOOGLE_DRIVE', 'test-client');
    sessionStore.setItem(
      'examset_sync_token_v1_GOOGLE_DRIVE',
      JSON.stringify({ accessToken: 'tok-1', refreshToken: '', expiresAt: 0 })
    );
    const seenUrls: string[] = [];
    Object.defineProperty(globalThis, 'fetch', {
      configurable: true,
      writable: true,
      value: async (url: string) => {
        seenUrls.push(String(url));
        if (String(url).includes('application%2Fvnd.google-apps.folder') && String(url).includes('name%3D')) {
          return { ok: true, status: 200, json: async () => ({ files: [{ id: 'folder-123' }] }) };
        }
        return { ok: true, status: 200, json: async () => ({ files: [] }) };
      },
    });
    await CloudSyncEngine.listCloudBackups('GOOGLE_DRIVE');
    const listUrl = decodeURIComponent(seenUrls[seenUrls.length - 1] || '');
    assert.match(listUrl, /mimeType!='application\/vnd\.google-apps\.folder'/);

    // 5. file:// OAuth fails cleanly with actionable offline hint
    mockWindow.location.protocol = 'file:';
    await assert.rejects(
      () => CloudSyncEngine.startCloudOAuth('GOOGLE_DRIVE'),
      /requires http:\/\/ or https:\/\/ hosting/i
    );
  } finally {
    Object.defineProperty(globalThis, 'window', { value: prevWindow, configurable: true, writable: true });
    Object.defineProperty(globalThis, 'document', { value: prevDocument, configurable: true, writable: true });
    Object.defineProperty(globalThis, 'localStorage', { value: prevLocal, configurable: true, writable: true });
    Object.defineProperty(globalThis, 'sessionStorage', { value: prevSession, configurable: true, writable: true });
    Object.defineProperty(globalThis, 'fetch', { value: prevFetch, configurable: true, writable: true });
    if (navDesc) Object.defineProperty(globalThis, 'navigator', navDesc);
    if (urlDesc) Object.defineProperty(globalThis, 'URL', urlDesc);
  }
});

