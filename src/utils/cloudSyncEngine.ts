import type {
  CloudAccountStatus,
  CloudProviderType,
  LinkedDiskFileStatus,
  RemoteCloudBackupFile,
  TokenStoragePreference,
  TrustedTimeStatus,
} from '../types/phase5';
import { downloadBytesSafely, downloadTextSafely } from './downloadHelper';
import { isZipBytes } from './zipArchiveHelper';

const STORAGE_KEYS = {
  GOOGLE_CLIENT_ID: 'examset_sync_google_client_id_v1',
  ONEDRIVE_CLIENT_ID: 'examset_sync_onedrive_client_id_v1',
  PROVIDER_META_PREFIX: 'examset_sync_meta_v1_',
  TOKEN_PREFIX: 'examset_sync_token_v1_',
  LINKED_DISK_META: 'examset_linked_disk_file_meta_v1',
  OAUTH_PENDING: 'examset_oauth_pending_tx_v1',
  OAUTH_RELAY: 'examset_oauth_callback_relay_v1',
};

const OAUTH_CHANNEL_NAME = 'examset_oauth_callback';
const OPFS_SYNC_DIR = 'examset_sync';
const OPFS_DEFAULT_JSON_NAME = 'ExamSet_Live_Sync.examset.json';
const MAX_CLOUD_DOWNLOAD_BYTES = 80 * 1024 * 1024;
const IDB_NAME = 'examset_disk_sync_v1';
const IDB_STORE = 'file_handles';
const IDB_HANDLE_KEY = 'linked_sync_handle';
const BACKUP_FOLDER_NAME = 'ExamSet Backups';
const PENDING_OAUTH_TTL_MS = 15 * 60 * 1000;
const refreshInFlight = new Map<CloudProviderType, Promise<string>>();

interface StoredTokenBundle {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

interface StoredProviderMeta {
  accountEmail: string;
  lastSyncedAt: number;
  lastError: string | null;
  tokenStoragePreference: TokenStoragePreference;
}

interface PendingOAuthTx {
  provider: CloudProviderType;
  state: string;
  codeVerifier?: string;
  redirectUri: string;
  createdAt: number;
}

interface BrowserFileHandle {
  name?: string;
  kind?: string;
  queryPermission?: (opts: { mode: 'read' | 'readwrite' }) => Promise<'granted' | 'denied' | 'prompt'>;
  requestPermission?: (opts: { mode: 'read' | 'readwrite' }) => Promise<'granted' | 'denied' | 'prompt'>;
  createWritable?: () => Promise<{ write: (data: string | Uint8Array | Blob) => Promise<void>; close: () => Promise<void> }>;
  getFile?: () => Promise<File>;
}

function safeLocalGet(key: string): string {
  try {
    return typeof localStorage !== 'undefined' ? localStorage.getItem(key) || '' : '';
  } catch {
    return '';
  }
}

function safeLocalSet(key: string, value: string): void {
  try {
    if (typeof localStorage !== 'undefined') localStorage.setItem(key, value);
  } catch {
    // ignore storage errors
  }
}

function safeLocalRemove(key: string): void {
  try {
    if (typeof localStorage !== 'undefined') localStorage.removeItem(key);
  } catch {
    // ignore
  }
}

function safeSessionGet(key: string): string {
  try {
    return typeof sessionStorage !== 'undefined' ? sessionStorage.getItem(key) || '' : '';
  } catch {
    return '';
  }
}

function safeSessionSet(key: string, value: string): void {
  try {
    if (typeof sessionStorage !== 'undefined') sessionStorage.setItem(key, value);
  } catch {
    // ignore
  }
}

function safeSessionRemove(key: string): void {
  try {
    if (typeof sessionStorage !== 'undefined') sessionStorage.removeItem(key);
  } catch {
    // ignore
  }
}

function readMeta(provider: CloudProviderType): StoredProviderMeta {
  const raw = safeLocalGet(`${STORAGE_KEYS.PROVIDER_META_PREFIX}${provider}`);
  if (raw) {
    try {
      const parsed = JSON.parse(raw) as Partial<StoredProviderMeta>;
      return {
        accountEmail: parsed.accountEmail || '',
        lastSyncedAt: Number(parsed.lastSyncedAt || 0),
        lastError: parsed.lastError || null,
        tokenStoragePreference: parsed.tokenStoragePreference === 'LOCAL' ? 'LOCAL' : 'SESSION',
      };
    } catch {
      // fall through
    }
  }
  return {
    accountEmail: '',
    lastSyncedAt: 0,
    lastError: null,
    tokenStoragePreference: 'SESSION',
  };
}

function writeMeta(provider: CloudProviderType, patch: Partial<StoredProviderMeta>): StoredProviderMeta {
  const next = { ...readMeta(provider), ...patch };
  safeLocalSet(`${STORAGE_KEYS.PROVIDER_META_PREFIX}${provider}`, JSON.stringify(next));
  return next;
}

function readTokenBundle(provider: CloudProviderType): StoredTokenBundle | null {
  const key = `${STORAGE_KEYS.TOKEN_PREFIX}${provider}`;
  const raw = safeSessionGet(key) || safeLocalGet(key);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<StoredTokenBundle>;
    if (!parsed.accessToken) return null;
    return {
      accessToken: parsed.accessToken,
      refreshToken: parsed.refreshToken || '',
      expiresAt: Number(parsed.expiresAt || 0),
    };
  } catch {
    return null;
  }
}

function writeTokenBundle(
  provider: CloudProviderType,
  bundle: StoredTokenBundle,
  preference: TokenStoragePreference
): void {
  const key = `${STORAGE_KEYS.TOKEN_PREFIX}${provider}`;
  const payload = JSON.stringify(bundle);
  if (preference === 'LOCAL') {
    safeLocalSet(key, payload);
    safeSessionSet(key, payload);
  } else {
    safeSessionSet(key, payload);
    safeLocalRemove(key);
  }
}

function clearTokenBundle(provider: CloudProviderType): void {
  const key = `${STORAGE_KEYS.TOKEN_PREFIX}${provider}`;
  safeSessionRemove(key);
  safeLocalRemove(key);
}

// --- Crypto & PKCE Utilities ---

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function generateRandomToken(byteLen = 24): string {
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const arr = new Uint8Array(byteLen);
    crypto.getRandomValues(arr);
    return base64UrlEncode(arr);
  }
  // [DEPRECATED 2026-11-24] Reason: Math.random() fallback is not cryptographically secure for OAuth state, PKCE verifiers, or multipart boundaries. Restore if: Non-WebCrypto legacy environment explicitly provides an injected CSPRNG shim.
  // return `${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`;
  throw new Error('Secure Web Crypto random generator is required for OAuth.');
}

async function computeS256Challenge(verifier: string): Promise<string> {
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    const encoded = new TextEncoder().encode(verifier);
    const digest = await crypto.subtle.digest('SHA-256', encoded);
    return base64UrlEncode(new Uint8Array(digest));
  }
  throw new Error('Web Crypto SHA-256 is required for OneDrive PKCE authorization.');
}

// --- IndexedDB Handle Storage for Computer Disk / Synced Folder Sync ---

function openSyncDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB is unavailable in this browser context.'));
      return;
    }
    const req = indexedDB.open(IDB_NAME, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(IDB_STORE)) {
        db.createObjectStore(IDB_STORE);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error || new Error('Failed to open IndexedDB handle store.'));
  });
}

async function saveDiskFileHandle(handle: BrowserFileHandle): Promise<void> {
  const db = await openSyncDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(IDB_STORE, 'readwrite');
    tx.objectStore(IDB_STORE).put(handle, IDB_HANDLE_KEY);
    tx.oncomplete = () => {
      db.close();
      resolve();
    };
    tx.onerror = () => {
      db.close();
      reject(tx.error || new Error('Failed to save file handle.'));
    };
  });
}

async function loadDiskFileHandle(): Promise<BrowserFileHandle | null> {
  try {
    const db = await openSyncDb();
    return await new Promise<BrowserFileHandle | null>((resolve, reject) => {
      const tx = db.transaction(IDB_STORE, 'readonly');
      const req = tx.objectStore(IDB_STORE).get(IDB_HANDLE_KEY);
      req.onsuccess = () => {
        db.close();
        resolve((req.result as BrowserFileHandle | undefined) || null);
      };
      req.onerror = () => {
        db.close();
        reject(req.error);
      };
    });
  } catch {
    return null;
  }
}

async function clearDiskFileHandle(): Promise<void> {
  try {
    const db = await openSyncDb();
    await new Promise<void>((resolve) => {
      const tx = db.transaction(IDB_STORE, 'readwrite');
      tx.objectStore(IDB_STORE).delete(IDB_HANDLE_KEY);
      tx.oncomplete = () => {
        db.close();
        resolve();
      };
      tx.onerror = () => {
        db.close();
        resolve();
      };
    });
  } catch {
    // ignore
  }
}

// [DEPRECATED 2026-11-28] Reason: Synchronous URL.revokeObjectURL cancelled downloads in Firefox & Safari; replaced with downloadTextSafely / downloadBytesSafely + OPFS helpers. Restore if: Synchronous revocation is ever needed.
// function triggerDownloadTextFile(filename: string, rawJson: string): void { ... }
function triggerDownloadArchive(filename: string, payload: string | Uint8Array): void {
  if (typeof payload === 'string') {
    downloadTextSafely(payload, filename, 'application/json;charset=utf-8');
  } else {
    downloadBytesSafely(payload, filename, 'application/zip');
  }
}

async function getOpfsSyncDir(create: boolean): Promise<FileSystemDirectoryHandle | null> {
  try {
    if (typeof navigator === 'undefined' || !navigator.storage?.getDirectory) return null;
    const root = await navigator.storage.getDirectory();
    return await root.getDirectoryHandle(OPFS_SYNC_DIR, { create });
  } catch {
    return null;
  }
}

async function writeOpfsSyncFile(fileName: string, payload: string | Uint8Array): Promise<boolean> {
  try {
    const dir = await getOpfsSyncDir(true);
    if (!dir) return false;
    const fh = await dir.getFileHandle(fileName, { create: true });
    const writable = await fh.createWritable();
    if (typeof payload === 'string') {
      await writable.write(payload);
    } else {
      const copy = new Uint8Array(payload.byteLength);
      copy.set(payload);
      await writable.write(copy.buffer);
    }
    await writable.close();
    return true;
  } catch {
    return false;
  }
}

async function readOpfsSyncFile(preferredName?: string): Promise<{ bytes: Uint8Array; fileName: string } | null> {
  try {
    const dir = await getOpfsSyncDir(false);
    if (!dir) return null;
    const candidates = Array.from(
      new Set([preferredName, 'ExamSet_Live_Sync.examset.zip', OPFS_DEFAULT_JSON_NAME].filter(Boolean))
    ) as string[];
    for (const name of candidates) {
      try {
        const fh = await dir.getFileHandle(name);
        const file = await fh.getFile();
        const buf = await file.arrayBuffer();
        return { bytes: new Uint8Array(buf), fileName: file.name || name };
      } catch {
        // try next
      }
    }
    return null;
  } catch {
    return null;
  }
}

async function clearOpfsSyncFiles(): Promise<void> {
  try {
    if (typeof navigator === 'undefined' || !navigator.storage?.getDirectory) return;
    const root = await navigator.storage.getDirectory();
    await root.removeEntry(OPFS_SYNC_DIR, { recursive: true });
  } catch {
    // ignore
  }
}

function pickDiskFileViaInput(accept: string): Promise<File | null> {
  return new Promise((resolve) => {
    if (typeof document === 'undefined') {
      resolve(null);
      return;
    }
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    input.style.display = 'none';
    let settled = false;
    const finalize = (file: File | null) => {
      if (settled) return;
      settled = true;
      window.removeEventListener('focus', onWindowFocus);
      if (input.parentNode) input.parentNode.removeChild(input);
      resolve(file);
    };
    const onWindowFocus = () => {
      setTimeout(() => {
        if (!settled) {
          finalize(input.files?.[0] || null);
        }
      }, 650);
    };
    input.addEventListener('change', () => {
      finalize(input.files?.[0] || null);
    });
    input.addEventListener('cancel', () => {
      finalize(null);
    });
    document.body.appendChild(input);
    window.addEventListener('focus', onWindowFocus, { once: true });
    input.click();
  });
}

function readLinkedDiskMeta(): { displayName: string; lastSyncedAt: number; lastError: string | null } {
  const raw = safeLocalGet(STORAGE_KEYS.LINKED_DISK_META);
  if (!raw) return { displayName: '', lastSyncedAt: 0, lastError: null };
  try {
    const parsed = JSON.parse(raw) as {
      displayName?: string;
      lastSyncedAt?: number;
      lastError?: string | null;
    };
    return {
      displayName: parsed.displayName || '',
      lastSyncedAt: Number(parsed.lastSyncedAt || 0),
      lastError: parsed.lastError || null,
    };
  } catch {
    return { displayName: '', lastSyncedAt: 0, lastError: null };
  }
}

function writeLinkedDiskMeta(meta: { displayName: string; lastSyncedAt: number; lastError: string | null }): void {
  safeLocalSet(STORAGE_KEYS.LINKED_DISK_META, JSON.stringify(meta));
}

export const CloudSyncEngine = {
  getRedirectUri(): string {
    if (typeof window === 'undefined') return '';
    return `${window.location.origin}${window.location.pathname}`;
  },

  isFileSystemAccessSupported(): boolean {
    if (typeof window === 'undefined') return false;
    const win = window as unknown as {
      showSaveFilePicker?: unknown;
      showOpenFilePicker?: unknown;
    };
    return typeof win.showSaveFilePicker === 'function' && typeof win.showOpenFilePicker === 'function';
  },

  isEmbeddedCrossOriginFrame(): boolean {
    if (typeof window === 'undefined') return false;
    try {
      return window.self !== window.top;
    } catch {
      return true;
    }
  },

  // --- Computer Disk / Desktop Cloud-Folder Live Sync ---

  async getLinkedDiskStatus(): Promise<LinkedDiskFileStatus> {
    const meta = readLinkedDiskMeta();
    const fsaAvailable = this.isFileSystemAccessSupported();
    const handle = fsaAvailable ? await loadDiskFileHandle() : null;

    if (!handle) {
      const opfsMatch = meta.displayName ? await readOpfsSyncFile(meta.displayName) : null;
      if (meta.displayName && opfsMatch) {
        return {
          isLinked: true,
          displayName: opfsMatch.fileName || meta.displayName,
          syncMode: 'DOWNLOAD_FALLBACK',
          permissionState: 'granted',
          lastSyncedAt: meta.lastSyncedAt,
          lastError: meta.lastError,
        };
      }
      return {
        isLinked: false,
        displayName: '',
        syncMode: fsaAvailable ? 'CHROMIUM_FSA' : 'DOWNLOAD_FALLBACK',
        permissionState: fsaAvailable ? 'prompt' : 'unavailable',
        lastSyncedAt: meta.lastSyncedAt,
        lastError: meta.lastError,
      };
    }

    let perm: 'granted' | 'prompt' | 'unavailable' = 'prompt';
    try {
      if (handle.queryPermission) {
        const res = await handle.queryPermission({ mode: 'readwrite' });
        perm = res === 'granted' ? 'granted' : 'prompt';
      }
    } catch {
      perm = 'prompt';
    }

    return {
      isLinked: true,
      displayName: handle.name || meta.displayName || OPFS_DEFAULT_JSON_NAME,
      syncMode: 'CHROMIUM_FSA',
      permissionState: perm,
      lastSyncedAt: meta.lastSyncedAt,
      lastError: meta.lastError,
    };
  },

  async linkNewDiskSyncFile(
    rawJson: string,
    binaryZipBytes?: Uint8Array
  ): Promise<{ status: LinkedDiskFileStatus; message: string }> {
    const preferZip = Boolean(binaryZipBytes && binaryZipBytes.byteLength > 0);
    const defaultName = preferZip ? 'ExamSet_Live_Sync.examset.zip' : OPFS_DEFAULT_JSON_NAME;
    const payload: string | Uint8Array = preferZip ? binaryZipBytes! : rawJson;

    if (!this.isFileSystemAccessSupported()) {
      const opfsSaved = await writeOpfsSyncFile(defaultName, payload);
      triggerDownloadArchive(defaultName, payload);
      const now = Date.now();
      if (opfsSaved) {
        writeLinkedDiskMeta({ displayName: defaultName, lastSyncedAt: now, lastError: null });
      }
      const status = await this.getLinkedDiskStatus();
      return {
        status,
        message: opfsSaved
          ? `Saved browser-private checkpoint "${defaultName}" and downloaded a copy to your Downloads / synced cloud folder.`
          : `Downloaded "${defaultName}" safely to your Downloads folder.`,
      };
    }

    const win = window as unknown as {
      showSaveFilePicker: (opts: unknown) => Promise<BrowserFileHandle>;
    };

    try {
      const handle = await win.showSaveFilePicker({
        suggestedName: defaultName,
        types: preferZip
          ? [
              {
                description: 'ExamSet Cross-Platform Vault (.examset.zip)',
                accept: { 'application/zip': ['.examset.zip', '.zip'] },
              },
              {
                description: 'ExamSet Verified Vault JSON (.examset.json)',
                accept: { 'application/json': ['.examset.json', '.json'] },
              },
            ]
          : [
              {
                description: 'ExamSet Verified Vault Archive (.examset.json)',
                accept: { 'application/json': ['.examset.json', '.json'] },
              },
              {
                description: 'ExamSet Cross-Platform Vault (.examset.zip)',
                accept: { 'application/zip': ['.examset.zip', '.zip'] },
              },
            ],
      });

      const writable = await handle.createWritable?.();
      if (!writable) throw new Error('Unable to obtain writable stream for selected file.');
      const chosenName = (handle.name || defaultName).toLowerCase();
      if (chosenName.endsWith('.zip') && binaryZipBytes) {
        const copy = new Uint8Array(binaryZipBytes.byteLength);
        copy.set(binaryZipBytes);
        await writable.write(copy);
      } else {
        await writable.write(rawJson);
      }
      await writable.close();

      await saveDiskFileHandle(handle);
      const now = Date.now();
      const name = handle.name || defaultName;
      writeLinkedDiskMeta({ displayName: name, lastSyncedAt: now, lastError: null });
      return {
        status: await this.getLinkedDiskStatus(),
        message: `Linked & synced live disk file "${name}". You can select any local or synced Google Drive / OneDrive folder.`,
      };
    } catch (err) {
      const e = err as { name?: string; message?: string };
      if (e?.name === 'AbortError') {
        return {
          status: await this.getLinkedDiskStatus(),
          message: 'File link cancelled.',
        };
      }
      if (e?.name === 'SecurityError') {
        await writeOpfsSyncFile(defaultName, payload);
        triggerDownloadArchive(defaultName, payload);
        writeLinkedDiskMeta({ displayName: defaultName, lastSyncedAt: Date.now(), lastError: null });
        const msg =
          'This embedded preview frame restricts persistent disk handles. Saved browser checkpoint & downloaded vault archive cleanly instead.';
        return {
          status: await this.getLinkedDiskStatus(),
          message: msg,
        };
      }
      throw new Error(e?.message || 'Could not link computer disk file.');
    }
  },

  async linkExistingDiskSyncFile(): Promise<{
    status: LinkedDiskFileStatus;
    rawJson: string;
    rawBytes?: Uint8Array;
    fileName: string;
  } | null> {
    if (!this.isFileSystemAccessSupported()) {
      const picked = await pickDiskFileViaInput('.json,.examset.json,.zip,.examset.zip');
      if (!picked) return null;
      const bytes = new Uint8Array(await picked.arrayBuffer());
      const fileName = picked.name || OPFS_DEFAULT_JSON_NAME;
      await writeOpfsSyncFile(fileName, bytes);
      writeLinkedDiskMeta({ displayName: fileName, lastSyncedAt: Date.now(), lastError: null });
      const rawJson = isZipBytes(bytes) ? '' : new TextDecoder('utf-8').decode(bytes);
      return {
        status: await this.getLinkedDiskStatus(),
        rawJson,
        rawBytes: bytes,
        fileName,
      };
    }

    const win = window as unknown as {
      showOpenFilePicker: (opts: unknown) => Promise<BrowserFileHandle[]>;
    };
    try {
      const [handle] = await win.showOpenFilePicker({
        multiple: false,
        types: [
          {
            description: 'ExamSet Verified Vault Archive (.examset.json / .examset.zip)',
            accept: {
              'application/json': ['.examset.json', '.json'],
              'application/zip': ['.examset.zip', '.zip'],
            },
          },
        ],
      });
      if (!handle) return null;
      const file = await handle.getFile?.();
      if (!file) throw new Error('Could not read selected file.');
      const bytes = new Uint8Array(await file.arrayBuffer());
      const rawJson = isZipBytes(bytes) ? '' : new TextDecoder('utf-8').decode(bytes);
      await saveDiskFileHandle(handle);
      const fileName = handle.name || file.name || OPFS_DEFAULT_JSON_NAME;
      writeLinkedDiskMeta({ displayName: fileName, lastSyncedAt: Date.now(), lastError: null });
      return {
        status: await this.getLinkedDiskStatus(),
        rawJson,
        rawBytes: bytes,
        fileName,
      };
    } catch (err) {
      const e = err as { name?: string; message?: string };
      if (e?.name === 'AbortError') return null;
      if (e?.name === 'SecurityError') {
        const picked = await pickDiskFileViaInput('.json,.examset.json,.zip,.examset.zip');
        if (!picked) return null;
        const bytes = new Uint8Array(await picked.arrayBuffer());
        const fileName = picked.name || OPFS_DEFAULT_JSON_NAME;
        await writeOpfsSyncFile(fileName, bytes);
        writeLinkedDiskMeta({ displayName: fileName, lastSyncedAt: Date.now(), lastError: null });
        const rawJson = isZipBytes(bytes) ? '' : new TextDecoder('utf-8').decode(bytes);
        return {
          status: await this.getLinkedDiskStatus(),
          rawJson,
          rawBytes: bytes,
          fileName,
        };
      }
      throw new Error(e?.message || 'Unable to open disk sync file.');
    }
  },

  async syncToLinkedDiskFile(
    rawJson: string,
    binaryZipBytes?: Uint8Array,
    silentOpfsOnly: boolean = false
  ): Promise<LinkedDiskFileStatus> {
    const handle = await loadDiskFileHandle();
    const meta = readLinkedDiskMeta();
    if (!handle) {
      if (meta.displayName) {
        const preferZip = meta.displayName.toLowerCase().endsWith('.zip') && binaryZipBytes;
        const targetName = preferZip ? meta.displayName : OPFS_DEFAULT_JSON_NAME;
        const payload: string | Uint8Array = preferZip ? binaryZipBytes! : rawJson;
        await writeOpfsSyncFile(targetName, payload);
        if (!silentOpfsOnly) {
          triggerDownloadArchive(targetName, payload);
        }
        writeLinkedDiskMeta({ displayName: targetName, lastSyncedAt: Date.now(), lastError: null });
        return await this.getLinkedDiskStatus();
      }
      throw new Error('No linked disk/cloud-folder file found. Click "Link Disk / Sync Folder" first.');
    }
    try {
      let granted = false;
      if (handle.queryPermission) {
        const q = await handle.queryPermission({ mode: 'readwrite' });
        granted = q === 'granted';
      }
      if (!granted && handle.requestPermission) {
        const r = await handle.requestPermission({ mode: 'readwrite' });
        granted = r === 'granted';
      }
      if (!granted) {
        throw new Error('Write permission not granted for linked file. Click "Sync Now" to approve browser prompt.');
      }
      const writable = await handle.createWritable?.();
      if (!writable) throw new Error('Writable handle stream unavailable.');
      const name = handle.name || meta.displayName || OPFS_DEFAULT_JSON_NAME;
      if (name.toLowerCase().endsWith('.zip') && binaryZipBytes) {
        const copy = new Uint8Array(binaryZipBytes.byteLength);
        copy.set(binaryZipBytes);
        await writable.write(copy);
      } else {
        await writable.write(rawJson);
      }
      await writable.close();

      const now = Date.now();
      writeLinkedDiskMeta({ displayName: name, lastSyncedAt: now, lastError: null });
      return await this.getLinkedDiskStatus();
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Disk file sync failed.';
      writeLinkedDiskMeta({ ...meta, lastError: msg });
      throw new Error(msg);
    }
  },

  async readFromLinkedDiskFile(): Promise<{
    rawJson: string;
    rawBytes?: Uint8Array;
    fileName: string;
    status: LinkedDiskFileStatus;
  }> {
    const handle = await loadDiskFileHandle();
    const meta = readLinkedDiskMeta();
    if (!handle) {
      const opfsFile = await readOpfsSyncFile(meta.displayName);
      if (opfsFile) {
        const rawJson = isZipBytes(opfsFile.bytes) ? '' : new TextDecoder('utf-8').decode(opfsFile.bytes);
        return {
          rawJson,
          rawBytes: opfsFile.bytes,
          fileName: opfsFile.fileName,
          status: await this.getLinkedDiskStatus(),
        };
      }
      const picked = await this.linkExistingDiskSyncFile();
      if (!picked) {
        throw new Error('No linked disk file selected.');
      }
      return picked;
    }
    let granted = false;
    if (handle.queryPermission) {
      const q = await handle.queryPermission({ mode: 'read' });
      granted = q === 'granted';
    }
    if (!granted && handle.requestPermission) {
      const r = await handle.requestPermission({ mode: 'read' });
      granted = r === 'granted';
    }
    if (!granted) {
      throw new Error('Read permission required by browser to inspect linked disk file.');
    }
    const file = await handle.getFile?.();
    if (!file) throw new Error('Unable to read linked file.');
    const bytes = new Uint8Array(await file.arrayBuffer());
    const rawJson = isZipBytes(bytes) ? '' : new TextDecoder('utf-8').decode(bytes);
    const fileName = handle.name || file.name || 'Linked Disk Archive';
    return {
      rawJson,
      rawBytes: bytes,
      fileName,
      status: await this.getLinkedDiskStatus(),
    };
  },

  async unlinkDiskSyncFile(): Promise<LinkedDiskFileStatus> {
    await clearDiskFileHandle();
    await clearOpfsSyncFiles();
    safeLocalRemove(STORAGE_KEYS.LINKED_DISK_META);
    return await this.getLinkedDiskStatus();
  },

  // --- Trusted Network Time Verification ---

  async checkTrustedOnlineTime(): Promise<TrustedTimeStatus> {
    const checkedAt = Date.now();
    const isOnline = typeof navigator !== 'undefined' ? navigator.onLine !== false : true;
    if (!isOnline) {
      return {
        isSynced: false,
        isOnline: false,
        checkedAt,
        serverTimeMs: null,
        skewSeconds: 0,
        sourceLabel: 'Offline (local clock)',
      };
    }

    if (typeof fetch === 'undefined' || typeof window === 'undefined') {
      return {
        isSynced: false,
        isOnline: true,
        checkedAt,
        serverTimeMs: null,
        skewSeconds: 0,
        sourceLabel: 'Local device clock',
      };
    }

    try {
      const t0 = Date.now();
      const resp = await fetch(window.location.origin + '/', {
        method: 'HEAD',
        cache: 'no-store',
      });
      const t1 = Date.now();
      const dateHeader = resp.headers.get('date');
      if (dateHeader) {
        const serverMs = Date.parse(dateHeader);
        if (Number.isFinite(serverMs)) {
          const rttMidLocal = Math.round((t0 + t1) / 2);
          const skewSeconds = Math.round((serverMs - rttMidLocal) / 1000);
          return {
            isSynced: true,
            isOnline: true,
            checkedAt: t1,
            serverTimeMs: serverMs,
            skewSeconds,
            sourceLabel: 'Origin HTTPS Time Header',
          };
        }
      }
      return {
        isSynced: resp.ok,
        isOnline: true,
        checkedAt: t1,
        serverTimeMs: t1,
        skewSeconds: 0,
        sourceLabel: resp.ok ? 'Online (Origin reachable)' : 'Origin unverified',
      };
    } catch {
      return {
        isSynced: false,
        isOnline,
        checkedAt: Date.now(),
        serverTimeMs: null,
        skewSeconds: 0,
        sourceLabel: 'Time check unavailable',
      };
    }
  },

  // --- Cloud OAuth2 Configuration & Account State (Google Drive & OneDrive) ---

  getClientId(provider: CloudProviderType): string {
    return safeLocalGet(
      provider === 'GOOGLE_DRIVE' ? STORAGE_KEYS.GOOGLE_CLIENT_ID : STORAGE_KEYS.ONEDRIVE_CLIENT_ID
    ).trim();
  },

  setClientId(provider: CloudProviderType, clientId: string): void {
    const clean = clientId.trim();
    const key =
      provider === 'GOOGLE_DRIVE' ? STORAGE_KEYS.GOOGLE_CLIENT_ID : STORAGE_KEYS.ONEDRIVE_CLIENT_ID;
    if (clean) safeLocalSet(key, clean);
    else safeLocalRemove(key);
    writeMeta(provider, { lastError: null });
  },

  setTokenStoragePreference(provider: CloudProviderType, pref: TokenStoragePreference): CloudAccountStatus {
    const existing = readTokenBundle(provider);
    writeMeta(provider, { tokenStoragePreference: pref });
    if (existing) {
      writeTokenBundle(provider, existing, pref);
    }
    return this.getAccountStatus(provider);
  },

  getAccountStatus(provider: CloudProviderType): CloudAccountStatus {
    const clientId = this.getClientId(provider);
    const meta = readMeta(provider);
    const token = readTokenBundle(provider);
    const now = Date.now();
    const hasUsableToken = Boolean(
      token &&
        token.accessToken &&
        (token.expiresAt === 0 || token.expiresAt > now + 15000 || Boolean(token.refreshToken))
    );

    return {
      provider,
      displayName: provider === 'GOOGLE_DRIVE' ? 'Google Drive' : 'Microsoft OneDrive',
      hasClientIdConfigured: clientId.length > 0,
      clientId,
      isConnected: hasUsableToken,
      accountEmail: meta.accountEmail,
      expiresAt: token?.expiresAt || 0,
      lastSyncedAt: meta.lastSyncedAt,
      lastError: meta.lastError,
      tokenStoragePreference: meta.tokenStoragePreference,
    };
  },

  disconnectCloud(provider: CloudProviderType): CloudAccountStatus {
    refreshInFlight.delete(provider);
    clearTokenBundle(provider);
    writeMeta(provider, {
      accountEmail: '',
      lastError: null,
    });
    return this.getAccountStatus(provider);
  },

  // Handle OAuth popup or full-window redirect on mount
  async handleOAuthCallbackIfPresent(): Promise<{ provider: CloudProviderType; ok: boolean; message: string } | null> {
    if (typeof window === 'undefined') return null;

    const hashParams = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    const searchParams = new URLSearchParams(window.location.search);

    const hasAccessToken = hashParams.has('access_token');
    const hasAuthCode = searchParams.has('code');
    const oauthErr = hashParams.get('error') || searchParams.get('error');

    if (!hasAccessToken && !hasAuthCode && !oauthErr) {
      return null;
    }

    // Hand callback URL back to opener across same-origin popup, BroadcastChannel, and storage relay (Firefox COOP / ETP)
    const relayPayload = { type: 'EXAMSET_OAUTH_CALLBACK', href: window.location.href, at: Date.now() };
    let handedOffToOpener = false;
    try {
      if (typeof BroadcastChannel !== 'undefined') {
        const ch = new BroadcastChannel(OAUTH_CHANNEL_NAME);
        ch.postMessage(relayPayload);
        ch.close();
      }
    } catch {
      // ignore BroadcastChannel failures
    }
    if (window.opener && window.opener !== window) {
      try {
        window.opener.postMessage(relayPayload, window.location.origin);
        handedOffToOpener = true;
      } catch {
        // Fallback below
      }
    }
    if (handedOffToOpener) {
      window.close();
      return null;
    }
    // If window.name matches our popup name and opener was stripped by Firefox COOP/ETP, publish via localStorage relay and close
    if (window.name?.startsWith('examset_oauth_') && safeSessionGet(STORAGE_KEYS.OAUTH_PENDING) === '') {
      safeLocalSet(STORAGE_KEYS.OAUTH_RELAY, JSON.stringify(relayPayload));
      window.close();
      return null;
    }

    // Redirect fallback path in same window: consume pending transaction and clean URL
    const rawPending = safeSessionGet(STORAGE_KEYS.OAUTH_PENDING) || safeLocalGet(STORAGE_KEYS.OAUTH_PENDING);
    safeSessionRemove(STORAGE_KEYS.OAUTH_PENDING);
    safeLocalRemove(STORAGE_KEYS.OAUTH_PENDING);
    if (!rawPending) return null;

    let pending: PendingOAuthTx;
    try {
      pending = JSON.parse(rawPending) as PendingOAuthTx;
    } catch {
      return null;
    }

    const now = Date.now();
    if (
      !pending ||
      typeof pending.createdAt !== 'number' ||
      !Number.isFinite(pending.createdAt) ||
      now - pending.createdAt > PENDING_OAUTH_TTL_MS ||
      pending.createdAt - now > PENDING_OAUTH_TTL_MS
    ) {
      window.history.replaceState({}, document.title, window.location.pathname);
      const expiredMsg = 'Authorization session expired. Please reconnect and try again.';
      if (pending?.provider) {
        writeMeta(pending.provider, { lastError: expiredMsg });
      }
      return {
        provider: pending?.provider || 'GOOGLE_DRIVE',
        ok: false,
        message: expiredMsg,
      };
    }

    try {
      await this.completeOAuthFromCallbackUrl(pending, window.location.href);
      window.history.replaceState({}, document.title, window.location.pathname);
      return {
        provider: pending.provider,
        ok: true,
        message: `${pending.provider === 'GOOGLE_DRIVE' ? 'Google Drive' : 'OneDrive'} connected successfully.`,
      };
    } catch (err) {
      window.history.replaceState({}, document.title, window.location.pathname);
      const msg = err instanceof Error ? err.message : 'OAuth sign-in failed.';
      writeMeta(pending.provider, { lastError: msg });
      return {
        provider: pending.provider,
        ok: false,
        message: msg,
      };
    }
  },

  async startCloudOAuth(provider: CloudProviderType): Promise<CloudAccountStatus> {
    if (typeof window === 'undefined') {
      throw new Error('OAuth requires a browser window.');
    }
    if (window.location.protocol === 'file:') {
      throw new Error(
        'Cloud OAuth2 requires http:// or https:// hosting. When running directly from a file:// offline bundle, use "Export .examset.json / .examset.zip" or Quick Disk Sync.'
      );
    }
    const clientId = this.getClientId(provider);
    if (!clientId) {
      throw new Error(`Enter your ${provider === 'GOOGLE_DRIVE' ? 'Google Drive' : 'OneDrive'} Client ID first.`);
    }

    const state = generateRandomToken(20);
    const redirectUri = this.getRedirectUri();
    let codeVerifier: string | undefined;
    let authUrl = '';

    if (provider === 'GOOGLE_DRIVE') {
      const params = new URLSearchParams({
        client_id: clientId,
        redirect_uri: redirectUri,
        response_type: 'token',
        scope: 'https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.email',
        include_granted_scopes: 'true',
        prompt: 'consent',
        state,
      });
      authUrl = `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`;
    } else {
      codeVerifier = generateRandomToken(32);
      const challenge = await computeS256Challenge(codeVerifier);
      const params = new URLSearchParams({
        client_id: clientId,
        redirect_uri: redirectUri,
        response_type: 'code',
        response_mode: 'query',
        scope: 'Files.ReadWrite.AppFolder offline_access User.Read',
        code_challenge: challenge,
        code_challenge_method: 'S256',
        state,
      });
      authUrl = `https://login.microsoftonline.com/common/oauth2/v2/authorize?${params.toString()}`;
    }

    const tx: PendingOAuthTx = {
      provider,
      state,
      codeVerifier,
      redirectUri,
      createdAt: Date.now(),
    };
    const serializedTx = JSON.stringify(tx);
    safeSessionSet(STORAGE_KEYS.OAUTH_PENDING, serializedTx);
    if (!safeSessionGet(STORAGE_KEYS.OAUTH_PENDING)) {
      safeLocalSet(STORAGE_KEYS.OAUTH_PENDING, serializedTx);
    } else {
      safeLocalRemove(STORAGE_KEYS.OAUTH_PENDING);
    }
    safeLocalRemove(STORAGE_KEYS.OAUTH_RELAY);

    const popup = window.open(
      authUrl,
      `examset_oauth_${provider}`,
      'width=520,height=680,menubar=no,toolbar=no,location=yes,resizable=yes,scrollbars=yes'
    );

    if (!popup) {
      // Popup blocked: navigate cleanly via same-tab redirect
      window.location.assign(authUrl);
      return this.getAccountStatus(provider);
    }

    const callbackHref = await new Promise<string>((resolve, reject) => {
      let finished = false;
      let bc: BroadcastChannel | null = null;
      const cleanup = () => {
        window.removeEventListener('message', onMsg);
        window.removeEventListener('storage', onStorage);
        try {
          bc?.close();
        } catch {
          // ignore
        }
        safeLocalRemove(STORAGE_KEYS.OAUTH_RELAY);
        clearInterval(closedPoll);
        clearTimeout(timeoutId);
      };

      const acceptPayload = (data: { type?: string; href?: string } | null) => {
        if (finished) return;
        if (data?.type === 'EXAMSET_OAUTH_CALLBACK' && typeof data.href === 'string') {
          finished = true;
          cleanup();
          resolve(data.href);
        }
      };

      const onMsg = (ev: MessageEvent) => {
        if (ev.origin !== window.location.origin) return;
        acceptPayload(ev.data as { type?: string; href?: string } | null);
      };

      const onStorage = (ev: StorageEvent) => {
        if (ev.key !== STORAGE_KEYS.OAUTH_RELAY || !ev.newValue) return;
        try {
          acceptPayload(JSON.parse(ev.newValue) as { type?: string; href?: string });
        } catch {
          // ignore
        }
      };

      try {
        if (typeof BroadcastChannel !== 'undefined') {
          bc = new BroadcastChannel(OAUTH_CHANNEL_NAME);
          bc.onmessage = (ev) => acceptPayload(ev.data as { type?: string; href?: string } | null);
        }
      } catch {
        bc = null;
      }

      const closedPoll = setInterval(() => {
        try {
          const relayRaw = safeLocalGet(STORAGE_KEYS.OAUTH_RELAY);
          if (relayRaw) {
            acceptPayload(JSON.parse(relayRaw) as { type?: string; href?: string });
            return;
          }
          if (popup.closed && !finished) {
            finished = true;
            cleanup();
            reject(new Error('Authorization window was closed before completion.'));
          }
        } catch {
          // ignore COOP errors while cross-origin
        }
      }, 450);

      const timeoutId = setTimeout(() => {
        if (!finished) {
          finished = true;
          cleanup();
          try {
            popup.close();
          } catch {
            // ignore
          }
          reject(new Error('OAuth authorization timed out after 3 minutes.'));
        }
      }, 180000);

      window.addEventListener('message', onMsg);
      window.addEventListener('storage', onStorage);
    });

    safeSessionRemove(STORAGE_KEYS.OAUTH_PENDING);
    safeLocalRemove(STORAGE_KEYS.OAUTH_PENDING);

    await this.completeOAuthFromCallbackUrl(tx, callbackHref);
    return this.getAccountStatus(provider);
  },

  async completeOAuthFromCallbackUrl(tx: PendingOAuthTx, callbackHref: string): Promise<void> {
    const parsed = new URL(callbackHref);
    const hashParams = new URLSearchParams(parsed.hash.replace(/^#/, ''));
    const queryParams = new URLSearchParams(parsed.search);

    const err = hashParams.get('error') || queryParams.get('error');
    const errDesc = hashParams.get('error_description') || queryParams.get('error_description');
    if (err) {
      throw new Error(errDesc || `OAuth declined (${err}).`);
    }

    const returnedState = hashParams.get('state') || queryParams.get('state') || '';
    if (returnedState !== tx.state) {
      throw new Error('OAuth state verification failed. Please retry connection.');
    }

    const meta = readMeta(tx.provider);

    if (tx.provider === 'GOOGLE_DRIVE') {
      const accessToken = hashParams.get('access_token') || '';
      const expiresIn = Number(hashParams.get('expires_in') || 3600);
      if (!accessToken) throw new Error('Google Drive returned no access token.');

      const bundle: StoredTokenBundle = {
        accessToken,
        refreshToken: '',
        expiresAt: Date.now() + expiresIn * 1000,
      };
      writeTokenBundle('GOOGLE_DRIVE', bundle, meta.tokenStoragePreference);

      const email = await this.fetchGoogleUserEmail(accessToken);
      writeMeta('GOOGLE_DRIVE', { accountEmail: email, lastError: null });
    } else {
      const code = queryParams.get('code') || '';
      if (!code || !tx.codeVerifier) {
        throw new Error('OneDrive returned no PKCE authorization code.');
      }
      const clientId = this.getClientId('ONEDRIVE');
      const tokenBody = new URLSearchParams({
        client_id: clientId,
        grant_type: 'authorization_code',
        code,
        redirect_uri: tx.redirectUri,
        code_verifier: tx.codeVerifier,
      });

      const resp = await fetch('https://login.microsoftonline.com/common/oauth2/v2.0/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: tokenBody.toString(),
      });
      const json = (await resp.json()) as {
        access_token?: string;
        refresh_token?: string;
        expires_in?: number;
        error_description?: string;
      };
      if (!resp.ok || !json.access_token) {
        throw new Error(json.error_description || 'OneDrive token exchange failed.');
      }

      const bundle: StoredTokenBundle = {
        accessToken: json.access_token,
        refreshToken: json.refresh_token || '',
        expiresAt: Date.now() + Number(json.expires_in || 3600) * 1000,
      };
      writeTokenBundle('ONEDRIVE', bundle, meta.tokenStoragePreference);

      const email = await this.fetchOneDriveUserEmail(json.access_token);
      writeMeta('ONEDRIVE', { accountEmail: email, lastError: null });
    }
  },

  async ensureValidAccessToken(provider: CloudProviderType): Promise<string> {
    const bundle = readTokenBundle(provider);
    if (!bundle) {
      throw new Error(`${provider === 'GOOGLE_DRIVE' ? 'Google Drive' : 'OneDrive'} is not connected.`);
    }
    const now = Date.now();
    if (bundle.expiresAt === 0 || now < bundle.expiresAt - 60000) {
      return bundle.accessToken;
    }

    // Attempt OneDrive refresh if refreshToken present, deduplicating concurrent callers
    if (provider === 'ONEDRIVE' && bundle.refreshToken) {
      const existing = refreshInFlight.get(provider);
      if (existing) {
        return existing;
      }

      const refreshPromise = (async (): Promise<string> => {
        try {
          const latestBundle = readTokenBundle('ONEDRIVE');
          if (latestBundle && (latestBundle.expiresAt === 0 || Date.now() < latestBundle.expiresAt - 60000)) {
            return latestBundle.accessToken;
          }
          const activeBundle = latestBundle || bundle;
          const clientId = this.getClientId('ONEDRIVE');
          const meta = readMeta('ONEDRIVE');
          const body = new URLSearchParams({
            client_id: clientId,
            grant_type: 'refresh_token',
            refresh_token: activeBundle.refreshToken,
            scope: 'Files.ReadWrite.AppFolder offline_access User.Read',
          });
          const resp = await fetch('https://login.microsoftonline.com/common/oauth2/v2.0/token', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString(),
          });
          const json = (await resp.json()) as {
            access_token?: string;
            refresh_token?: string;
            expires_in?: number;
            error_description?: string;
          };
          if (resp.ok && json.access_token) {
            const updated: StoredTokenBundle = {
              accessToken: json.access_token,
              refreshToken: json.refresh_token || activeBundle.refreshToken,
              expiresAt: Date.now() + Number(json.expires_in || 3600) * 1000,
            };
            writeTokenBundle('ONEDRIVE', updated, meta.tokenStoragePreference);
            return updated.accessToken;
          }

          clearTokenBundle(provider);
          const errMsg = 'Session expired. Click Reconnect to authorize access again.';
          writeMeta(provider, { lastError: errMsg });
          throw new Error(errMsg);
        } finally {
          refreshInFlight.delete(provider);
        }
      })();

      refreshInFlight.set(provider, refreshPromise);
      return refreshPromise;
    }

    clearTokenBundle(provider);
    const errMsg = 'Session expired. Click Reconnect to authorize access again.';
    writeMeta(provider, { lastError: errMsg });
    throw new Error(errMsg);
  },

  async fetchGoogleUserEmail(accessToken: string): Promise<string> {
    try {
      const resp = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (!resp.ok) return '';
      const data = (await resp.json()) as { email?: string };
      return data.email || '';
    } catch {
      return '';
    }
  },

  async fetchOneDriveUserEmail(accessToken: string): Promise<string> {
    try {
      const resp = await fetch('https://graph.microsoft.com/v1.0/me', {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (!resp.ok) return '';
      const data = (await resp.json()) as { mail?: string; userPrincipalName?: string };
      return data.mail || data.userPrincipalName || '';
    } catch {
      return '';
    }
  },

  async uploadVaultToCloud(
    provider: CloudProviderType,
    fileName: string,
    payload: string | Uint8Array
  ): Promise<RemoteCloudBackupFile> {
    try {
      const token = await this.ensureValidAccessToken(provider);
      let remote: RemoteCloudBackupFile;
      const isBinaryZip = typeof payload !== 'string';
      const contentType = isBinaryZip ? 'application/zip' : 'application/json';
      const payloadSize = typeof payload === 'string' ? payload.length : payload.byteLength;

      if (provider === 'GOOGLE_DRIVE') {
        const folderId = await this.ensureGoogleDriveFolder(token);
        const boundary = `examset_${generateRandomToken(12)}`;
        const metadataJson = JSON.stringify({
          name: fileName,
          parents: [folderId],
          mimeType: contentType,
        });
        const bodyBlob = isBinaryZip
          ? new Blob([
              `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${metadataJson}\r\n`,
              `--${boundary}\r\nContent-Type: application/zip\r\n\r\n`,
              (() => {
                const copy = new Uint8Array(payload.byteLength);
                copy.set(payload);
                return copy.buffer;
              })(),
              `\r\n--${boundary}--`,
            ])
          : `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${metadataJson}\r\n--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${payload}\r\n--${boundary}--`;

        const resp = await fetch(
          'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size,modifiedTime',
          {
            method: 'POST',
            headers: {
              Authorization: `Bearer ${token}`,
              'Content-Type': `multipart/related; boundary=${boundary}`,
            },
            body: bodyBlob,
          }
        );
        const data = (await resp.json()) as {
          id?: string;
          name?: string;
          size?: string;
          modifiedTime?: string;
          error?: { message?: string };
        };
        if (resp.status === 401) {
          clearTokenBundle(provider);
          throw new Error('Session expired. Click Reconnect to authorize access again.');
        }
        if (!resp.ok || !data.id) {
          throw new Error(data.error?.message || `Google Drive upload failed (HTTP ${resp.status}).`);
        }
        remote = {
          provider: 'GOOGLE_DRIVE',
          remoteId: data.id,
          name: data.name || fileName,
          sizeBytes: Number(data.size || payloadSize),
          modifiedAt: data.modifiedTime ? Date.parse(data.modifiedTime) : Date.now(),
        };
      } else {
        const encodedFolder = encodeURIComponent(BACKUP_FOLDER_NAME);
        const encodedFile = encodeURIComponent(fileName);
        const url = `https://graph.microsoft.com/v1.0/me/drive/special/approot:/${encodedFolder}/${encodedFile}:/content`;
        let body: string | ArrayBuffer;
        if (isBinaryZip) {
          const copy = new Uint8Array(payload.byteLength);
          copy.set(payload);
          body = copy.buffer;
        } else {
          body = payload;
        }
        const resp = await fetch(url, {
          method: 'PUT',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': contentType,
          },
          body,
        });
        const data = (await resp.json()) as {
          id?: string;
          name?: string;
          size?: number;
          lastModifiedDateTime?: string;
          error?: { message?: string };
        };
        if (resp.status === 401) {
          clearTokenBundle(provider);
          throw new Error('Session expired. Click Reconnect to authorize access again.');
        }
        if (!resp.ok || !data.id) {
          throw new Error(data.error?.message || `OneDrive upload failed (HTTP ${resp.status}).`);
        }
        remote = {
          provider: 'ONEDRIVE',
          remoteId: data.id,
          name: data.name || fileName,
          sizeBytes: Number(data.size || payloadSize),
          modifiedAt: data.lastModifiedDateTime ? Date.parse(data.lastModifiedDateTime) : Date.now(),
        };
      }

      writeMeta(provider, { lastSyncedAt: Date.now(), lastError: null });
      return remote;
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Cloud sync upload failed.';
      writeMeta(provider, { lastError: msg });
      throw new Error(msg);
    }
  },

  async ensureGoogleDriveFolder(token: string): Promise<string> {
    const q = encodeURIComponent(
      `name='${BACKUP_FOLDER_NAME}' and mimeType='application/vnd.google-apps.folder' and trashed=false`
    );
    const searchResp = await fetch(
      `https://www.googleapis.com/drive/v3/files?q=${q}&spaces=drive&fields=files(id,name)`,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    if (searchResp.status === 401) {
      clearTokenBundle('GOOGLE_DRIVE');
      throw new Error('Session expired. Click Reconnect to authorize access again.');
    }
    const searchJson = (await searchResp.json()) as { files?: { id: string }[] };
    if (searchResp.ok && searchJson.files && searchJson.files.length > 0) {
      return searchJson.files[0].id;
    }

    const createResp = await fetch('https://www.googleapis.com/drive/v3/files?fields=id', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        name: BACKUP_FOLDER_NAME,
        mimeType: 'application/vnd.google-apps.folder',
      }),
    });
    const created = (await createResp.json()) as { id?: string; error?: { message?: string } };
    if (!createResp.ok || !created.id) {
      throw new Error(created.error?.message || 'Unable to create ExamSet Backups folder in Google Drive.');
    }
    return created.id;
  },

  async listCloudBackups(provider: CloudProviderType): Promise<RemoteCloudBackupFile[]> {
    try {
      const token = await this.ensureValidAccessToken(provider);

      if (provider === 'GOOGLE_DRIVE') {
        const folderId = await this.ensureGoogleDriveFolder(token);
        const q = encodeURIComponent(
          `'${folderId}' in parents and mimeType!='application/vnd.google-apps.folder' and trashed=false`
        );
        const url = `https://www.googleapis.com/drive/v3/files?q=${q}&orderBy=modifiedTime desc&pageSize=15&fields=files(id,name,size,modifiedTime)`;
        const resp = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
        if (resp.status === 401) {
          clearTokenBundle(provider);
          throw new Error('Session expired. Click Reconnect to authorize access again.');
        }
        const json = (await resp.json()) as {
          files?: { id: string; name?: string; size?: string; modifiedTime?: string }[];
          error?: { message?: string };
        };
        if (!resp.ok) {
          throw new Error(json.error?.message || `Failed to list Google Drive backups (HTTP ${resp.status}).`);
        }
        writeMeta(provider, { lastError: null });
        return (json.files || []).map((f) => ({
          provider: 'GOOGLE_DRIVE',
          remoteId: f.id,
          name: f.name || 'Backup Archive',
          sizeBytes: Number(f.size || 0),
          modifiedAt: f.modifiedTime ? Date.parse(f.modifiedTime) : 0,
        }));
      } else {
        const encodedFolder = encodeURIComponent(BACKUP_FOLDER_NAME);
        const url = `https://graph.microsoft.com/v1.0/me/drive/special/approot:/${encodedFolder}:/children?$orderby=lastModifiedDateTime desc&$top=15`;
        const resp = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
        if (resp.status === 401) {
          clearTokenBundle(provider);
          throw new Error('Session expired. Click Reconnect to authorize access again.');
        }
        if (resp.status === 404) {
          return [];
        }
        const json = (await resp.json()) as {
          value?: { id: string; name?: string; size?: number; lastModifiedDateTime?: string; file?: unknown }[];
          error?: { message?: string };
        };
        if (!resp.ok) {
          throw new Error(json.error?.message || `Failed to list OneDrive backups (HTTP ${resp.status}).`);
        }
        writeMeta(provider, { lastError: null });
        return (json.value || [])
          .filter((item) => Boolean(item.file))
          .map((item) => ({
            provider: 'ONEDRIVE',
            remoteId: item.id,
            name: item.name || 'Backup Archive',
            sizeBytes: Number(item.size || 0),
            modifiedAt: item.lastModifiedDateTime ? Date.parse(item.lastModifiedDateTime) : 0,
          }));
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unable to fetch remote cloud backups.';
      writeMeta(provider, { lastError: msg });
      throw new Error(msg);
    }
  },

  async downloadCloudBackupBytes(remote: RemoteCloudBackupFile): Promise<Uint8Array> {
    if (remote.sizeBytes > MAX_CLOUD_DOWNLOAD_BYTES) {
      throw new Error(`Remote archive (${Math.round(remote.sizeBytes / (1024 * 1024))} MB) exceeds safe 80 MB download limit.`);
    }
    const token = await this.ensureValidAccessToken(remote.provider);
    const url =
      remote.provider === 'GOOGLE_DRIVE'
        ? `https://www.googleapis.com/drive/v3/files/${encodeURIComponent(remote.remoteId)}?alt=media`
        : `https://graph.microsoft.com/v1.0/me/drive/items/${encodeURIComponent(remote.remoteId)}/content`;

    const resp = await fetch(url, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (resp.status === 401) {
      clearTokenBundle(remote.provider);
      const errMsg = 'Session expired. Click Reconnect to authorize access again.';
      writeMeta(remote.provider, { lastError: errMsg });
      throw new Error(errMsg);
    }
    if (!resp.ok) {
      throw new Error(`Failed to download remote archive (${resp.status}).`);
    }
    const contentLen = Number(resp.headers.get('content-length') || 0);
    if (contentLen > MAX_CLOUD_DOWNLOAD_BYTES) {
      throw new Error('Remote archive exceeds safe 80 MB download limit.');
    }
    const buf = await resp.arrayBuffer();
    if (buf.byteLength > MAX_CLOUD_DOWNLOAD_BYTES) {
      throw new Error('Downloaded remote archive exceeds safe 80 MB limit.');
    }
    return new Uint8Array(buf);
  },

  async downloadCloudBackupText(remote: RemoteCloudBackupFile): Promise<string> {
    const bytes = await this.downloadCloudBackupBytes(remote);
    return new TextDecoder('utf-8').decode(bytes);
  },
};
