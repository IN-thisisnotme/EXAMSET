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
  buildVaultArchive,
  inspectVaultArchiveText,
  type WorkspaceBackupState,
} from '../src/utils/vaultArchiveEngine';
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

