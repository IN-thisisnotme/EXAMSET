import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  Archive,
  CheckCircle2,
  Clock,
  Cloud,
  CloudDownload,
  CloudUpload,
  Copy,
  Download,
  FileJson,
  FolderSync,
  HardDrive,
  History,
  Layers,
  Link2,
  RefreshCw,
  Settings,
  ShieldCheck,
  Trash2,
  Unlink,
  Upload,
  X,
} from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import {
  AUTO_BACKUP_SCHEDULE_OPTIONS,
  type AutoBackupSchedule,
  type CloudAccountStatus,
  type CloudProviderType,
  type LinkedDiskFileStatus,
  type RemoteCloudBackupFile,
  type RestoreMode,
  type RestorePreview,
  type TokenStoragePreference,
  type TrustedTimeStatus,
  type VaultSnapshotRecord,
} from '../../types/phase5';
import { CloudSyncEngine } from '../../utils/cloudSyncEngine';
import {
  buildVaultArchive,
  computeBackupCounts,
  inspectVaultArchiveText,
  type WorkspaceBackupState,
} from '../../utils/vaultArchiveEngine';

interface BackupVaultTabProps {
  workspaceState: WorkspaceBackupState;
  snapshots: VaultSnapshotRecord[];
  autoBackupSchedule: AutoBackupSchedule;
  lastAutoBackupAt: number;
  onChangeAutoBackupSchedule: (schedule: AutoBackupSchedule) => void;
  onCreateSnapshot: (label: string, kind: 'manual' | 'pre_restore', rawJson: string) => void;
  onDeleteSnapshot: (snapshotId: string) => void;
  onExecuteRestore: (preview: RestorePreview, mode: RestoreMode) => void;
  onNotify: (msg: string) => void;
}

function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 KB';
  const kb = bytes / 1024;
  return kb < 1024 ? `${kb.toFixed(1)} KB` : `${(kb / 1024).toFixed(2)} MB`;
}

export const BackupVaultTab: React.FC<BackupVaultTabProps> = ({
  workspaceState,
  snapshots,
  autoBackupSchedule,
  lastAutoBackupAt,
  onChangeAutoBackupSchedule,
  onCreateSnapshot,
  onDeleteSnapshot,
  onExecuteRestore,
  onNotify,
}) => {
  const { colors, scalePx } = useTheme();
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [pasteMode, setPasteMode] = useState(false);
  const [rawPasteText, setRawPasteText] = useState('');
  const [restorePreview, setRestorePreview] = useState<RestorePreview | null>(null);
  const [restoreMode, setRestoreMode] = useState<RestoreMode>('SMART_MERGE');
  const [inspectError, setInspectError] = useState<string | null>(null);
  const [customSnapshotLabel, setCustomSnapshotLabel] = useState('');

  // Sync & Cloud States
  const [diskStatus, setDiskStatus] = useState<LinkedDiskFileStatus>({
    isLinked: false,
    displayName: '',
    syncMode: CloudSyncEngine.isFileSystemAccessSupported() ? 'CHROMIUM_FSA' : 'DOWNLOAD_FALLBACK',
    permissionState: CloudSyncEngine.isFileSystemAccessSupported() ? 'prompt' : 'unavailable',
    lastSyncedAt: 0,
    lastError: null,
  });
  const [driveStatus, setDriveStatus] = useState<CloudAccountStatus>(() =>
    CloudSyncEngine.getAccountStatus('GOOGLE_DRIVE')
  );
  const [oneDriveStatus, setOneDriveStatus] = useState<CloudAccountStatus>(() =>
    CloudSyncEngine.getAccountStatus('ONEDRIVE')
  );
  const [trustedTime, setTrustedTime] = useState<TrustedTimeStatus>({
    isSynced: false,
    isOnline: typeof navigator !== 'undefined' ? navigator.onLine !== false : true,
    checkedAt: 0,
    serverTimeMs: null,
    skewSeconds: 0,
    sourceLabel: 'Checking network time…',
  });
  const [remoteBackups, setRemoteBackups] = useState<RemoteCloudBackupFile[]>([]);
  const [opBusyLabel, setOpBusyLabel] = useState<string | null>(null);
  const [opFeedback, setOpFeedback] = useState<{ ok: boolean; message: string } | null>(null);

  // Client ID Setup Modal State
  const [configProvider, setConfigProvider] = useState<CloudProviderType | null>(null);
  const [clientIdDraft, setClientIdDraft] = useState('');
  const [tokenPrefDraft, setTokenPrefDraft] = useState<TokenStoragePreference>('SESSION');
  const [copiedUri, setCopiedUri] = useState<string | null>(null);

  const liveCounts = useMemo(() => computeBackupCounts(workspaceState), [workspaceState]);
  const isEmbeddedFrame = useMemo(() => CloudSyncEngine.isEmbeddedCrossOriginFrame(), []);
  const activeOrigin = useMemo(
    () => (typeof window !== 'undefined' ? window.location.origin : ''),
    []
  );
  const activeRedirectUri = useMemo(() => CloudSyncEngine.getRedirectUri(), []);

  const refreshAllSyncStates = useCallback(async () => {
    const [disk, time] = await Promise.all([
      CloudSyncEngine.getLinkedDiskStatus(),
      CloudSyncEngine.checkTrustedOnlineTime(),
    ]);
    setDiskStatus(disk);
    setTrustedTime(time);
    setDriveStatus(CloudSyncEngine.getAccountStatus('GOOGLE_DRIVE'));
    setOneDriveStatus(CloudSyncEngine.getAccountStatus('ONEDRIVE'));
  }, []);

  useEffect(() => {
    void refreshAllSyncStates();
  }, [refreshAllSyncStates]);

  const triggerDownload = (filename: string, rawJson: string) => {
    const blob = new Blob([rawJson], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const handleDownloadVault = () => {
    const { rawJson } = buildVaultArchive(workspaceState, 'ExamSet Web Workspace');
    const dateStamp = new Date().toISOString().slice(0, 10);
    triggerDownload(`ExamSet_Vault_${dateStamp}.examset.json`, rawJson);
    onNotify('Downloaded SHA-256 verified Backup Vault archive');
  };

  const handleSaveLocalCheckpoint = () => {
    const { manifest, rawJson } = buildVaultArchive(workspaceState, 'Browser Local Vault');
    const label =
      customSnapshotLabel.trim() ||
      `Checkpoint (${manifest.counts.papersCount} Sets, ${manifest.counts.notesCount} Notes, ${manifest.counts.vocabWordsCount} Words)`;
    onCreateSnapshot(label, 'manual', rawJson);
    setCustomSnapshotLabel('');
    onNotify('Saved verified snapshot to Browser Local Vault');
  };

  const handleInspectRaw = (rawText: string, sourceLabel: string) => {
    setInspectError(null);
    try {
      const preview = inspectVaultArchiveText(rawText, sourceLabel);
      setRestorePreview(preview);
    } catch (err) {
      setRestorePreview(null);
      setInspectError(err instanceof Error ? err.message : 'Unable to parse backup archive.');
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const text = typeof reader.result === 'string' ? reader.result : '';
      handleInspectRaw(text, file.name);
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  const confirmRestore = () => {
    if (!restorePreview) return;
    onExecuteRestore(restorePreview, restoreMode);
    setRestorePreview(null);
    setRawPasteText('');
    setPasteMode(false);
  };

  // --- Computer Disk & Cloud-Synced Folder Handlers ---

  const handleLinkNewDiskFile = async () => {
    setOpFeedback(null);
    setOpBusyLabel('Preparing live disk sync file…');
    try {
      const { rawJson } = buildVaultArchive(workspaceState, 'ExamSet Live Disk Sync');
      const res = await CloudSyncEngine.linkNewDiskSyncFile(rawJson);
      setDiskStatus(res.status);
      setOpFeedback({ ok: !res.status.lastError, message: res.message });
      onNotify(res.message);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unable to link disk sync file.';
      setOpFeedback({ ok: false, message: msg });
    } finally {
      setOpBusyLabel(null);
    }
  };

  const handleLinkExistingDiskFile = async () => {
    setOpFeedback(null);
    setOpBusyLabel('Opening existing disk / synced folder file…');
    try {
      const res = await CloudSyncEngine.linkExistingDiskSyncFile();
      if (res) {
        setDiskStatus(res.status);
        handleInspectRaw(res.rawJson, `Linked Disk: ${res.fileName}`);
        setOpFeedback({
          ok: true,
          message: `Linked "${res.fileName}" and staged verified restore preview.`,
        });
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unable to open existing disk file.';
      setOpFeedback({ ok: false, message: msg });
    } finally {
      setOpBusyLabel(null);
    }
  };

  const handleSyncDiskNow = async () => {
    setOpFeedback(null);
    setOpBusyLabel(`Syncing "${diskStatus.displayName}" to disk…`);
    try {
      const { rawJson } = buildVaultArchive(workspaceState, 'ExamSet Live Disk Sync');
      const updated = await CloudSyncEngine.syncToLinkedDiskFile(rawJson);
      setDiskStatus(updated);
      const msg = `Synced "${updated.displayName}" cleanly.`;
      setOpFeedback({ ok: true, message: msg });
      onNotify(msg);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Disk sync failed.';
      setOpFeedback({ ok: false, message: msg });
      setDiskStatus(await CloudSyncEngine.getLinkedDiskStatus());
    } finally {
      setOpBusyLabel(null);
    }
  };

  const handleRestoreFromDisk = async () => {
    setOpFeedback(null);
    setOpBusyLabel(`Reading "${diskStatus.displayName}"…`);
    try {
      const { rawJson, fileName, status } = await CloudSyncEngine.readFromLinkedDiskFile();
      setDiskStatus(status);
      handleInspectRaw(rawJson, `Disk File: ${fileName}`);
      setOpFeedback({ ok: true, message: `Loaded "${fileName}" — choose Merge or Clean Replace.` });
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unable to read linked disk file.';
      setOpFeedback({ ok: false, message: msg });
    } finally {
      setOpBusyLabel(null);
    }
  };

  const handleUnlinkDisk = async () => {
    const next = await CloudSyncEngine.unlinkDiskSyncFile();
    setDiskStatus(next);
    setOpFeedback({ ok: true, message: 'Unlinked computer disk sync file.' });
  };

  // --- Cloud OAuth2 Handlers ---

  const openProviderConfigModal = (provider: CloudProviderType) => {
    const st = provider === 'GOOGLE_DRIVE' ? driveStatus : oneDriveStatus;
    setConfigProvider(provider);
    setClientIdDraft(st.clientId);
    setTokenPrefDraft(st.tokenStoragePreference);
  };

  const saveProviderConfig = () => {
    if (!configProvider) return;
    CloudSyncEngine.setClientId(configProvider, clientIdDraft);
    CloudSyncEngine.setTokenStoragePreference(configProvider, tokenPrefDraft);
    setDriveStatus(CloudSyncEngine.getAccountStatus('GOOGLE_DRIVE'));
    setOneDriveStatus(CloudSyncEngine.getAccountStatus('ONEDRIVE'));
    setConfigProvider(null);
    onNotify('Saved OAuth Client configuration');
  };

  const handleConnectProvider = async (provider: CloudProviderType) => {
    setOpFeedback(null);
    const label = provider === 'GOOGLE_DRIVE' ? 'Google Drive' : 'Microsoft OneDrive';
    setOpBusyLabel(`Authorizing ${label} in popup…`);
    try {
      await CloudSyncEngine.startCloudOAuth(provider);
      setDriveStatus(CloudSyncEngine.getAccountStatus('GOOGLE_DRIVE'));
      setOneDriveStatus(CloudSyncEngine.getAccountStatus('ONEDRIVE'));
      const msg = `${label} connected cleanly.`;
      setOpFeedback({ ok: true, message: msg });
      onNotify(msg);
    } catch (err) {
      const msg = err instanceof Error ? err.message : `${label} authorization failed.`;
      setOpFeedback({ ok: false, message: msg });
      setDriveStatus(CloudSyncEngine.getAccountStatus('GOOGLE_DRIVE'));
      setOneDriveStatus(CloudSyncEngine.getAccountStatus('ONEDRIVE'));
    } finally {
      setOpBusyLabel(null);
    }
  };

  const handleSyncToProvider = async (provider: CloudProviderType) => {
    setOpFeedback(null);
    const label = provider === 'GOOGLE_DRIVE' ? 'Google Drive' : 'OneDrive';
    setOpBusyLabel(`Uploading verified vault to ${label}…`);
    try {
      const { rawJson } = buildVaultArchive(workspaceState, `ExamSet ${label} Sync`);
      const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
      const fileName = `ExamSet_Vault_${stamp}.examset.json`;
      const remote = await CloudSyncEngine.uploadVaultToCloud(provider, fileName, rawJson);
      setDriveStatus(CloudSyncEngine.getAccountStatus('GOOGLE_DRIVE'));
      setOneDriveStatus(CloudSyncEngine.getAccountStatus('ONEDRIVE'));
      setRemoteBackups((prev) => [remote, ...prev.filter((r) => r.remoteId !== remote.remoteId)].slice(0, 15));
      const msg = `Synced "${remote.name}" (${formatBytes(remote.sizeBytes)}) to ${label}.`;
      setOpFeedback({ ok: true, message: msg });
      onNotify(msg);
    } catch (err) {
      const msg = err instanceof Error ? err.message : `Upload to ${label} failed.`;
      setOpFeedback({ ok: false, message: msg });
      setDriveStatus(CloudSyncEngine.getAccountStatus('GOOGLE_DRIVE'));
      setOneDriveStatus(CloudSyncEngine.getAccountStatus('ONEDRIVE'));
    } finally {
      setOpBusyLabel(null);
    }
  };

  const handleListCloudBackups = async (provider: CloudProviderType) => {
    setOpFeedback(null);
    const label = provider === 'GOOGLE_DRIVE' ? 'Google Drive' : 'OneDrive';
    setOpBusyLabel(`Listing backups in ${label}…`);
    try {
      const list = await CloudSyncEngine.listCloudBackups(provider);
      setRemoteBackups(list);
      setOpFeedback({
        ok: true,
        message:
          list.length > 0
            ? `Found ${list.length} backup archive(s) in ${label}.`
            : `No archives found inside "${label} / ExamSet Backups" yet.`,
      });
    } catch (err) {
      const msg = err instanceof Error ? err.message : `Could not list ${label} backups.`;
      setOpFeedback({ ok: false, message: msg });
      setDriveStatus(CloudSyncEngine.getAccountStatus('GOOGLE_DRIVE'));
      setOneDriveStatus(CloudSyncEngine.getAccountStatus('ONEDRIVE'));
    } finally {
      setOpBusyLabel(null);
    }
  };

  const handleRestoreRemoteBackup = async (remote: RemoteCloudBackupFile) => {
    setOpFeedback(null);
    setOpBusyLabel(`Downloading "${remote.name}"…`);
    try {
      const rawJson = await CloudSyncEngine.downloadCloudBackupText(remote);
      handleInspectRaw(rawJson, `${remote.provider === 'GOOGLE_DRIVE' ? 'Google Drive' : 'OneDrive'}: ${remote.name}`);
      setOpFeedback({
        ok: true,
        message: `Downloaded "${remote.name}" — verify checksum & select restore mode.`,
      });
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Download failed.';
      setOpFeedback({ ok: false, message: msg });
    } finally {
      setOpBusyLabel(null);
    }
  };

  const handleDisconnectProvider = (provider: CloudProviderType) => {
    CloudSyncEngine.disconnectCloud(provider);
    setDriveStatus(CloudSyncEngine.getAccountStatus('GOOGLE_DRIVE'));
    setOneDriveStatus(CloudSyncEngine.getAccountStatus('ONEDRIVE'));
    setRemoteBackups((prev) => prev.filter((r) => r.provider !== provider));
    onNotify(`Disconnected ${provider === 'GOOGLE_DRIVE' ? 'Google Drive' : 'OneDrive'}`);
  };

  const copyHelperValue = (label: string, val: string) => {
    navigator.clipboard?.writeText(val);
    setCopiedUri(label);
    setTimeout(() => setCopiedUri(null), 2000);
  };

  const renderCloudProviderCard = (status: CloudAccountStatus) => {
    const isGoogle = status.provider === 'GOOGLE_DRIVE';
    const accent = isGoogle ? '#0F9D58' : '#0078D4';
    return (
      <div
        key={status.provider}
        className="rounded-xl border p-3.5 sm:p-4 space-y-2.5"
        style={{
          backgroundColor: colors.surfaceVariant,
          borderColor: status.isConnected ? accent : colors.border,
        }}
      >
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2.5 min-w-0">
            <div
              className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0"
              style={{
                backgroundColor: status.isConnected ? `${accent}20` : colors.surface,
                color: status.isConnected ? accent : colors.muted,
                border: `1px solid ${colors.border}`,
              }}
            >
              <Cloud className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <div className="text-xs font-black truncate" style={{ color: colors.inkBold }}>
                {status.displayName}
              </div>
              <div className="text-[11px] truncate" style={{ color: colors.muted }}>
                {status.isConnected && status.accountEmail
                  ? status.accountEmail
                  : status.isConnected
                  ? 'Connected & authorized'
                  : status.hasClientIdConfigured
                  ? 'Client ID saved — ready to connect'
                  : 'Configure OAuth Client ID to connect'}
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={() => openProviderConfigModal(status.provider)}
            className="min-w-[40px] min-h-[40px] flex items-center justify-center rounded-lg border flex-shrink-0"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            title={`Configure ${status.displayName} OAuth`}
            aria-label={`Configure ${status.displayName} OAuth`}
          >
            <Settings className="w-4 h-4" />
          </button>
        </div>

        {status.lastSyncedAt > 0 && (
          <div className="text-[11px] font-medium" style={{ color: colors.tealDark }}>
            Last synced: {new Date(status.lastSyncedAt).toLocaleString()}
          </div>
        )}
        {status.lastError && (
          <div
            className="p-2 rounded-lg text-[11px] font-semibold"
            style={{ backgroundColor: colors.redLight, color: colors.red }}
          >
            {status.lastError}
          </div>
        )}

        <div className="flex flex-wrap items-center gap-2">
          {!status.isConnected ? (
            <>
              <button
                type="button"
                onClick={() => openProviderConfigModal(status.provider)}
                className="min-h-[40px] px-3 py-1.5 rounded-lg border text-xs font-bold"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
              >
                Client ID Setup
              </button>
              <button
                type="button"
                onClick={() =>
                  status.hasClientIdConfigured
                    ? void handleConnectProvider(status.provider)
                    : openProviderConfigModal(status.provider)
                }
                disabled={Boolean(opBusyLabel)}
                className="min-h-[40px] px-3.5 py-1.5 rounded-lg text-xs font-black text-white disabled:opacity-50"
                style={{ backgroundColor: accent }}
              >
                {status.hasClientIdConfigured ? 'Connect Account' : 'Setup & Connect'}
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={() => void handleSyncToProvider(status.provider)}
                disabled={Boolean(opBusyLabel)}
                className="min-h-[40px] flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-black text-white disabled:opacity-50"
                style={{ backgroundColor: accent }}
              >
                <CloudUpload className="w-3.5 h-3.5" />
                Sync Now
              </button>
              <button
                type="button"
                onClick={() => void handleListCloudBackups(status.provider)}
                disabled={Boolean(opBusyLabel)}
                className="min-h-[40px] flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-bold disabled:opacity-50"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
              >
                <CloudDownload className="w-3.5 h-3.5" style={{ color: accent }} />
                List Backups
              </button>
              <button
                type="button"
                onClick={() => handleDisconnectProvider(status.provider)}
                className="min-h-[40px] px-2.5 py-1.5 rounded-lg text-xs font-bold"
                style={{ color: colors.red }}
              >
                Disconnect
              </button>
            </>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div
        className="rounded-2xl border p-4 sm:p-5 flex flex-col lg:flex-row lg:items-center justify-between gap-4"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <Archive className="w-5 h-5 flex-shrink-0" style={{ color: colors.teal }} />
            <h2 className="font-black" style={{ fontSize: scalePx(20), color: colors.inkBold }}>
              Backup Vault, Cloud &amp; Disk Sync
            </h2>
          </div>
          <p className="text-xs mt-1 leading-relaxed" style={{ color: colors.muted }}>
            SHA-256 verified recovery across Question Sets, Attempts, Knowledge Notes, SM-2, Vocab, Tasks, Brain Lab stats, and Appearance settings.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={handleSaveLocalCheckpoint}
            className="min-h-[44px] flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-black text-white shadow-sm"
            style={{ backgroundColor: colors.teal }}
          >
            <HardDrive className="w-4 h-4" />
            Save Snapshot
          </button>
          <button
            type="button"
            onClick={handleDownloadVault}
            className="min-h-[44px] flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold border"
            style={{ backgroundColor: colors.navyBanner, borderColor: colors.navyBanner, color: colors.navyBannerText }}
          >
            <Download className="w-4 h-4" />
            Export .examset.json
          </button>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="min-h-[44px] flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold border"
            style={{
              backgroundColor: colors.surface,
              borderColor: colors.border,
              color: colors.inkBold,
            }}
          >
            <Upload className="w-4 h-4" style={{ color: colors.teal }} />
            Import &amp; Inspect
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept=".json,.examset.json"
            className="hidden"
            onChange={handleFileChange}
          />
        </div>
      </div>

      {/* Live Workspace Inventory Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
        {[
          { label: 'Question Sets', value: liveCounts.papersCount },
          { label: 'Knowledge Notes', value: liveCounts.notesCount },
          { label: 'Vocab Words', value: `${liveCounts.vocabWordsCount} (${liveCounts.vocabDecksCount} sets)` },
          { label: 'Test Attempts', value: liveCounts.attemptsCount },
          { label: 'Tasks / Presets', value: `${liveCounts.todosCount} / ${liveCounts.promptPresetsCount}` },
        ].map((stat) => (
          <div
            key={stat.label}
            className="p-3.5 rounded-xl border min-w-0"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="text-sm font-black truncate" style={{ color: colors.teal }}>
              {stat.value}
            </div>
            <div className="text-[11px] font-semibold mt-0.5 truncate" style={{ color: colors.muted }}>
              {stat.label}
            </div>
          </div>
        ))}
      </div>

      {/* Sync Operation Status */}
      {(opBusyLabel || opFeedback) && (
        <div
          className="p-3.5 rounded-xl border flex items-center justify-between gap-3 text-xs font-semibold"
          style={{
            backgroundColor: opBusyLabel
              ? colors.blueLight
              : opFeedback?.ok
              ? colors.greenLight
              : colors.redLight,
            borderColor: opBusyLabel ? colors.teal : opFeedback?.ok ? colors.green : colors.red,
            color: colors.inkBold,
          }}
        >
          <div className="flex items-center gap-2 min-w-0">
            {opBusyLabel ? (
              <RefreshCw className="w-4 h-4 animate-spin flex-shrink-0" style={{ color: colors.teal }} />
            ) : opFeedback?.ok ? (
              <CheckCircle2 className="w-4 h-4 flex-shrink-0" style={{ color: colors.green }} />
            ) : (
              <AlertTriangle className="w-4 h-4 flex-shrink-0" style={{ color: colors.red }} />
            )}
            <span className="break-words">{opBusyLabel || opFeedback?.message}</span>
          </div>
          {!opBusyLabel && (
            <button
              type="button"
              onClick={() => setOpFeedback(null)}
              className="p-1.5 rounded-lg flex-shrink-0"
              aria-label="Dismiss sync status"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      )}

      {/* Online Cloud & Computer Disk Sync Hub */}
      <div
        className="rounded-2xl border p-4 sm:p-5 space-y-4"
        style={{ backgroundColor: colors.surface, borderColor: colors.border }}
      >
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <FolderSync className="w-4 h-4 flex-shrink-0" style={{ color: colors.teal }} />
            <h3 className="text-xs font-black uppercase tracking-wider" style={{ color: colors.inkMuted }}>
              Computer Disk &amp; Online Cloud Sync
            </h3>
          </div>
          <button
            type="button"
            onClick={() => void refreshAllSyncStates()}
            className="min-h-[36px] flex items-center gap-1.5 px-2.5 py-1 rounded-lg border text-[11px] font-bold"
            style={{
              backgroundColor: trustedTime.isSynced ? colors.greenLight : colors.amberLight,
              borderColor: trustedTime.isSynced ? colors.green : colors.amber,
              color: colors.inkBold,
            }}
            title={trustedTime.sourceLabel}
          >
            <Clock className="w-3.5 h-3.5" style={{ color: trustedTime.isSynced ? colors.green : colors.amber }} />
            <span>{trustedTime.isSynced ? 'Online Time Synced' : trustedTime.isOnline ? 'Local Clock' : 'Offline Mode'}</span>
            <RefreshCw className="w-3 h-3 opacity-70" />
          </button>
        </div>

        {/* Zero-Setup Computer Disk / Desktop Cloud-Folder Live Sync Card */}
        <div
          className="rounded-xl border p-4 space-y-3"
          style={{
            backgroundColor: diskStatus.isLinked ? colors.tealLight : colors.blueLight,
            borderColor: colors.teal,
          }}
        >
          <div className="flex items-start gap-2.5">
            <Link2 className="w-5 h-5 mt-0.5 flex-shrink-0" style={{ color: colors.teal }} />
            <div className="min-w-0 flex-1">
              <div className="text-sm font-black" style={{ color: colors.inkBold }}>
                Quick Disk &amp; Desktop Cloud-Folder Sync (Zero OAuth Setup)
              </div>
              <p className="text-xs mt-0.5 leading-relaxed" style={{ color: colors.inkMuted }}>
                {diskStatus.isLinked
                  ? `Linked File: ${diskStatus.displayName}${diskStatus.lastSyncedAt > 0 ? ` • Last synced ${new Date(diskStatus.lastSyncedAt).toLocaleString()}` : ''}`
                  : 'Link an .examset.json file on your computer hard disk—or inside your synced Google Drive for Desktop, OneDrive, iCloud Drive, or Dropbox folder—for 1-click overwrite and restore.'}
              </p>
              {isEmbeddedFrame && !diskStatus.isLinked && (
                <p className="text-[11px] mt-1 font-semibold" style={{ color: colors.amber }}>
                  Note: Persistent File Picker handles require a direct Chrome/Edge browser tab; inside embedded preview frames or Safari/Firefox, safe file download/import is used automatically.
                </p>
              )}
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {!diskStatus.isLinked ? (
              <>
                <button
                  type="button"
                  onClick={() => void handleLinkNewDiskFile()}
                  disabled={Boolean(opBusyLabel)}
                  className="min-h-[42px] flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-black text-white disabled:opacity-50"
                  style={{ backgroundColor: colors.teal }}
                >
                  <CloudUpload className="w-4 h-4" />
                  Link Disk / Cloud Folder File
                </button>
                <button
                  type="button"
                  onClick={() => void handleLinkExistingDiskFile()}
                  disabled={Boolean(opBusyLabel)}
                  className="min-h-[42px] flex items-center gap-1.5 px-3.5 py-2 rounded-xl border text-xs font-bold disabled:opacity-50"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                >
                  <Upload className="w-4 h-4" style={{ color: colors.teal }} />
                  Open Existing Sync File
                </button>
              </>
            ) : (
              <>
                <button
                  type="button"
                  onClick={() => void handleSyncDiskNow()}
                  disabled={Boolean(opBusyLabel)}
                  className="min-h-[42px] flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-black text-white disabled:opacity-50"
                  style={{ backgroundColor: colors.teal }}
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  Sync Disk File Now
                </button>
                <button
                  type="button"
                  onClick={() => void handleRestoreFromDisk()}
                  disabled={Boolean(opBusyLabel)}
                  className="min-h-[42px] flex items-center gap-1.5 px-3.5 py-2 rounded-xl border text-xs font-bold disabled:opacity-50"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                >
                  <CloudDownload className="w-3.5 h-3.5" style={{ color: colors.teal }} />
                  Inspect &amp; Restore
                </button>
                <button
                  type="button"
                  onClick={() => void handleLinkNewDiskFile()}
                  disabled={Boolean(opBusyLabel)}
                  className="min-h-[42px] px-3 py-2 rounded-xl border text-xs font-semibold"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                >
                  Change File
                </button>
                <button
                  type="button"
                  onClick={() => void handleUnlinkDisk()}
                  className="min-h-[42px] flex items-center gap-1 px-2.5 py-2 rounded-xl text-xs font-bold"
                  style={{ color: colors.red }}
                >
                  <Unlink className="w-3.5 h-3.5" />
                  Unlink
                </button>
              </>
            )}
          </div>
        </div>

        {/* Direct OAuth2 Cloud Providers Grid */}
        <div className="space-y-2">
          <div className="text-[11px] font-bold uppercase tracking-wider" style={{ color: colors.muted }}>
            Direct OAuth2 Cloud Drive Sync (BYO Web Client ID)
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {renderCloudProviderCard(driveStatus)}
            {renderCloudProviderCard(oneDriveStatus)}
          </div>
        </div>

        {/* Remote Cloud Backups Listing */}
        {remoteBackups.length > 0 && (
          <div className="pt-3 border-t space-y-2" style={{ borderColor: colors.border }}>
            <div className="flex items-center justify-between">
              <span className="text-xs font-black" style={{ color: colors.inkBold }}>
                Cloud Archives Found ({remoteBackups.length})
              </span>
              <button
                type="button"
                onClick={() => setRemoteBackups([])}
                className="text-[11px] font-semibold"
                style={{ color: colors.muted }}
              >
                Clear List
              </button>
            </div>
            <div className="space-y-2">
              {remoteBackups.slice(0, 8).map((remote) => (
                <div
                  key={`${remote.provider}_${remote.remoteId}`}
                  className="p-2.5 rounded-xl border flex flex-wrap items-center justify-between gap-2"
                  style={{ backgroundColor: colors.blueLight, borderColor: colors.border }}
                >
                  <div className="min-w-0 flex-1">
                    <div className="text-xs font-bold truncate" style={{ color: colors.inkBold }}>
                      {remote.name}
                    </div>
                    <div className="text-[11px]" style={{ color: colors.muted }}>
                      {remote.provider === 'GOOGLE_DRIVE' ? 'Google Drive' : 'OneDrive'} •{' '}
                      {formatBytes(remote.sizeBytes)}
                      {remote.modifiedAt > 0
                        ? ` • ${new Date(remote.modifiedAt).toLocaleString()}`
                        : ''}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => void handleRestoreRemoteBackup(remote)}
                    className="min-h-[38px] px-3 py-1.5 rounded-lg border text-xs font-bold flex-shrink-0"
                    style={{
                      backgroundColor: colors.surface,
                      borderColor: colors.teal,
                      color: colors.tealDark,
                    }}
                  >
                    Inspect &amp; Restore
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Auto-Backup Cadence Selector */}
      <div
        className="rounded-2xl border p-4 sm:p-5 space-y-3"
        style={{ backgroundColor: colors.surface, borderColor: colors.border }}
      >
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 className="text-xs font-black uppercase tracking-wider" style={{ color: colors.inkMuted }}>
              Auto-Backup Schedule &amp; Workspace Guard
            </h3>
            <p className="text-[11px] mt-0.5" style={{ color: colors.muted }}>
              {lastAutoBackupAt > 0
                ? `Last auto-backup run: ${new Date(lastAutoBackupAt).toLocaleString()}`
                : 'Automatically stores rolling recovery checkpoints (and syncs granted disk/cloud links while active).'}
            </p>
          </div>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2">
          {AUTO_BACKUP_SCHEDULE_OPTIONS.map((opt) => {
            const active = autoBackupSchedule === opt.id;
            return (
              <button
                key={opt.id}
                type="button"
                onClick={() => onChangeAutoBackupSchedule(opt.id)}
                className="min-h-[48px] p-3 rounded-xl border text-left transition"
                style={{
                  backgroundColor: active ? colors.tealLight : colors.surfaceVariant,
                  borderColor: active ? colors.teal : colors.border,
                }}
              >
                <div className="text-xs font-black" style={{ color: colors.inkBold }}>
                  {opt.label}
                </div>
                <div className="text-[11px] mt-0.5 leading-snug" style={{ color: colors.muted }}>
                  {opt.description}
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Two-Column Actions: Left = Local Checkpoint & Paste JSON | Right = Restore Preview or Local Vault History */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-5 space-y-5">
          {/* Save Browser Local Vault Snapshot */}
          <div
            className="rounded-2xl border p-4 sm:p-5 space-y-3"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="flex items-center gap-2">
              <HardDrive className="w-4 h-4" style={{ color: colors.teal }} />
              <h3 className="text-sm font-black" style={{ color: colors.inkBold }}>
                Create Local Vault Checkpoint
              </h3>
            </div>
            <p className="text-xs" style={{ color: colors.muted }}>
              Save a SHA-256 checksummed recovery checkpoint in your browser storage before bulk imports or edits.
            </p>
            <div className="flex flex-col sm:flex-row gap-2">
              <input
                type="text"
                value={customSnapshotLabel}
                onChange={(e) => setCustomSnapshotLabel(e.target.value)}
                placeholder="Optional label (e.g. Before Mock Batch)"
                className="flex-1 min-h-[42px] px-3 py-2 rounded-lg border text-xs"
                style={{
                  backgroundColor: colors.surfaceVariant,
                  borderColor: colors.border,
                  color: colors.inkBold,
                }}
              />
              <button
                type="button"
                onClick={handleSaveLocalCheckpoint}
                className="min-h-[42px] px-4 py-2 rounded-lg text-xs font-black text-white flex-shrink-0"
                style={{ backgroundColor: colors.teal }}
              >
                Save Snapshot
              </button>
            </div>
          </div>

          {/* Paste JSON Inspector */}
          <div
            className="rounded-2xl border p-4 sm:p-5 space-y-3"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2 min-w-0">
                <FileJson className="w-4 h-4 flex-shrink-0" style={{ color: colors.teal }} />
                <h3 className="text-sm font-black truncate" style={{ color: colors.inkBold }}>
                  Restore from File or Pasted JSON
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setPasteMode((v) => !v)}
                className="text-xs font-bold underline flex-shrink-0"
                style={{ color: colors.teal }}
              >
                {pasteMode ? 'Hide Paste Box' : 'Paste JSON'}
              </button>
            </div>

            {pasteMode && (
              <div className="space-y-2">
                <textarea
                  rows={5}
                  value={rawPasteText}
                  onChange={(e) => setRawPasteText(e.target.value)}
                  placeholder="Paste contents of an .examset.json vault archive or Question Paper JSON..."
                  className="w-full p-2.5 rounded-xl border text-xs font-mono"
                  style={{
                    backgroundColor: colors.surfaceVariant,
                    borderColor: colors.border,
                    color: colors.inkBold,
                  }}
                />
                <button
                  type="button"
                  onClick={() => handleInspectRaw(rawPasteText, 'Pasted JSON')}
                  className="min-h-[42px] px-4 py-2 rounded-lg text-xs font-black text-white"
                  style={{ backgroundColor: colors.teal }}
                >
                  Inspect &amp; Verify Checksum
                </button>
              </div>
            )}

            {inspectError && (
              <div
                className="p-3 rounded-xl border text-xs font-semibold flex items-center gap-2"
                style={{ backgroundColor: colors.redLight, borderColor: colors.red, color: colors.red }}
              >
                <AlertTriangle className="w-4 h-4 flex-shrink-0" />
                <span>{inspectError}</span>
              </div>
            )}
          </div>
        </div>

        <div className="lg:col-span-7 space-y-5">
          {restorePreview && (
            <div
              className="rounded-2xl border-2 p-4 sm:p-5 space-y-4"
              style={{ backgroundColor: colors.surface, borderColor: colors.teal }}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    {restorePreview.checksumVerified ? (
                      <ShieldCheck className="w-5 h-5 flex-shrink-0" style={{ color: colors.teal }} />
                    ) : (
                      <AlertTriangle className="w-5 h-5 flex-shrink-0" style={{ color: colors.amber }} />
                    )}
                    <h3 className="text-sm font-black break-words" style={{ color: colors.inkBold }}>
                      Restore Preview: {restorePreview.sourceLabel}
                    </h3>
                  </div>
                  <p className="text-xs mt-1 font-semibold" style={{ color: colors.tealDark }}>
                    {restorePreview.checksumDetails}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setRestorePreview(null)}
                  className="text-xs font-bold px-2.5 py-1.5 rounded border flex-shrink-0"
                  style={{ borderColor: colors.border, color: colors.muted }}
                >
                  Cancel
                </button>
              </div>

              <div className="grid grid-cols-3 sm:grid-cols-5 gap-2 text-center">
                {[
                  ['Papers', restorePreview.manifest.counts.papersCount],
                  ['Notes', restorePreview.manifest.counts.notesCount],
                  ['Vocab', restorePreview.manifest.counts.vocabWordsCount],
                  ['Attempts', restorePreview.manifest.counts.attemptsCount],
                  ['Folders', restorePreview.manifest.counts.foldersCount],
                ].map(([lbl, cnt]) => (
                  <div
                    key={String(lbl)}
                    className="p-2 rounded-lg border"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                  >
                    <div className="text-xs font-black" style={{ color: colors.inkBold }}>
                      {cnt}
                    </div>
                    <div className="text-[10px]" style={{ color: colors.muted }}>
                      {lbl}
                    </div>
                  </div>
                ))}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                <button
                  type="button"
                  onClick={() => setRestoreMode('SMART_MERGE')}
                  className="p-3 rounded-xl border text-left transition"
                  style={
                    restoreMode === 'SMART_MERGE'
                      ? { backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.inkBold }
                      : { backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }
                  }
                >
                  <div className="flex items-center gap-1.5 text-xs font-black">
                    <Layers className="w-4 h-4" style={{ color: colors.teal }} />
                    Smart Merge (Recommended)
                  </div>
                  <p className="text-[11px] mt-1" style={{ color: colors.muted }}>
                    Keeps local items, adds missing records, merges SRS progress &amp; takes newer updates cleanly.
                  </p>
                </button>

                <button
                  type="button"
                  onClick={() => setRestoreMode('FULL_REPLACE')}
                  className="p-3 rounded-xl border text-left transition"
                  style={
                    restoreMode === 'FULL_REPLACE'
                      ? { backgroundColor: colors.amberLight, borderColor: colors.amber, color: colors.inkBold }
                      : { backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }
                  }
                >
                  <div className="flex items-center gap-1.5 text-xs font-black">
                    <RefreshCw className="w-4 h-4" style={{ color: colors.amber }} />
                    Clean Replace
                  </div>
                  <p className="text-[11px] mt-1" style={{ color: colors.muted }}>
                    Automatically saves a pre-restore safety snapshot first, then replaces your library with this vault.
                  </p>
                </button>
              </div>

              <button
                type="button"
                onClick={confirmRestore}
                className="w-full min-h-[44px] py-2.5 rounded-xl text-xs font-black text-white flex items-center justify-center gap-2"
                style={{ backgroundColor: colors.teal }}
              >
                <CheckCircle2 className="w-4 h-4" />
                {restoreMode === 'SMART_MERGE' ? 'Apply Smart Merge' : 'Save Safety Snapshot & Clean Replace'}
              </button>
            </div>
          )}

          {/* Local Snapshots History */}
          <div
            className="rounded-2xl border p-4 sm:p-5 space-y-3"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <History className="w-4 h-4" style={{ color: colors.teal }} />
                <h3 className="text-sm font-black" style={{ color: colors.inkBold }}>
                  Local Vault Snapshots ({snapshots.length})
                </h3>
              </div>
              <span className="text-[11px]" style={{ color: colors.muted }}>
                Up to 5 rolling pre-restore guards kept automatically
              </span>
            </div>

            {snapshots.length === 0 ? (
              <div
                className="p-6 rounded-xl text-center text-xs"
                style={{ backgroundColor: colors.surfaceVariant, color: colors.muted }}
              >
                No saved local snapshots yet. Click &ldquo;Save Snapshot&rdquo; to create your first verified checkpoint.
              </div>
            ) : (
              <div className="space-y-2">
                {snapshots.map((snap) => (
                  <div
                    key={snap.id}
                    className="p-3 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-2.5"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span
                          className="text-[10px] font-black px-2 py-0.5 rounded-full uppercase flex-shrink-0"
                          style={{
                            backgroundColor: snap.kind === 'pre_restore' ? colors.amberLight : colors.tealLight,
                            color: snap.kind === 'pre_restore' ? colors.amber : colors.tealDark,
                          }}
                        >
                          {snap.kind === 'pre_restore' ? 'AUTO GUARD' : 'MANUAL'}
                        </span>
                        <span className="text-xs font-bold truncate" style={{ color: colors.inkBold }}>
                          {snap.label}
                        </span>
                      </div>
                      <div className="text-[11px] mt-1" style={{ color: colors.muted }}>
                        {new Date(snap.createdAt).toLocaleString()} • {snap.manifest.counts.papersCount} Sets •{' '}
                        {snap.manifest.counts.notesCount} Notes • {snap.manifest.counts.vocabWordsCount} Words
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 flex-shrink-0">
                      <button
                        type="button"
                        onClick={() => handleInspectRaw(snap.rawJson, snap.label)}
                        className="min-h-[38px] px-3 py-1.5 rounded-lg text-xs font-bold text-white"
                        style={{ backgroundColor: colors.teal }}
                      >
                        Restore
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          triggerDownload(
                            `ExamSet_Snapshot_${new Date(snap.createdAt).toISOString().slice(0, 10)}.examset.json`,
                            snap.rawJson
                          );
                        }}
                        className="min-w-[38px] min-h-[38px] flex items-center justify-center rounded-lg border"
                        style={{
                          backgroundColor: colors.surface,
                          borderColor: colors.border,
                          color: colors.inkBold,
                        }}
                        title="Download this snapshot JSON"
                      >
                        <Download className="w-3.5 h-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => onDeleteSnapshot(snap.id)}
                        className="min-w-[38px] min-h-[38px] flex items-center justify-center rounded-lg"
                        style={{ color: colors.red }}
                        aria-label="Delete snapshot"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* OAuth Client Configuration Modal */}
      {configProvider && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.55)' }}
          onClick={() => setConfigProvider(null)}
        >
          <div
            className="w-full max-w-lg max-h-[90dvh] flex flex-col rounded-2xl shadow-2xl overflow-hidden"
            style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.ink }}
            onClick={(e) => e.stopPropagation()}
          >
            <div
              className="px-4 sm:px-5 py-3.5 flex items-center justify-between flex-shrink-0"
              style={{ borderBottom: `1px solid ${colors.border}` }}
            >
              <h3 className="text-sm font-black" style={{ color: colors.inkBold }}>
                {configProvider === 'GOOGLE_DRIVE' ? 'Google Drive' : 'Microsoft OneDrive'} OAuth2 Web Setup
              </h3>
              <button
                type="button"
                onClick={() => setConfigProvider(null)}
                className="min-w-[40px] min-h-[40px] flex items-center justify-center rounded-lg"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="p-4 sm:p-5 space-y-3.5 overflow-y-auto flex-1 text-xs">
              <p className="leading-relaxed" style={{ color: colors.muted }}>
                {configProvider === 'GOOGLE_DRIVE'
                  ? 'In Google Cloud Console, create an OAuth 2.0 Web Application Client ID, enable Google Drive API (scope: drive.file), and whitelist the Origin & Redirect URI below.'
                  : 'In Microsoft Entra ID, register a Single-Page Application (SPA) with the Redirect URI below and grant Microsoft Graph Files.ReadWrite.AppFolder.'}
              </p>

              {/* Origin Copy Row */}
              <div
                className="p-2.5 rounded-xl border flex items-center justify-between gap-2"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                <div className="min-w-0 flex-1">
                  <div className="text-[10px] font-bold uppercase" style={{ color: colors.muted }}>
                    Authorized JavaScript Origin
                  </div>
                  <div className="font-mono text-[11px] truncate" style={{ color: colors.inkBold }}>
                    {activeOrigin}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => copyHelperValue('origin', activeOrigin)}
                  className="px-2.5 py-1.5 rounded-lg border text-[11px] font-bold flex items-center gap-1 flex-shrink-0"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.teal }}
                >
                  <Copy className="w-3 h-3" />
                  {copiedUri === 'origin' ? 'Copied' : 'Copy'}
                </button>
              </div>

              {/* Redirect URI Copy Row */}
              <div
                className="p-2.5 rounded-xl border flex items-center justify-between gap-2"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                <div className="min-w-0 flex-1">
                  <div className="text-[10px] font-bold uppercase" style={{ color: colors.muted }}>
                    Authorized Redirect URI
                  </div>
                  <div className="font-mono text-[11px] truncate" style={{ color: colors.inkBold }}>
                    {activeRedirectUri}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => copyHelperValue('redirect', activeRedirectUri)}
                  className="px-2.5 py-1.5 rounded-lg border text-[11px] font-bold flex items-center gap-1 flex-shrink-0"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.teal }}
                >
                  <Copy className="w-3 h-3" />
                  {copiedUri === 'redirect' ? 'Copied' : 'Copy'}
                </button>
              </div>

              <div className="space-y-1">
                <label className="block font-bold" style={{ color: colors.inkBold }}>
                  OAuth Client ID
                </label>
                <input
                  type="text"
                  value={clientIdDraft}
                  onChange={(e) => setClientIdDraft(e.target.value)}
                  placeholder={
                    configProvider === 'GOOGLE_DRIVE'
                      ? '1234567890-abcdef.apps.googleusercontent.com'
                      : 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
                  }
                  className="w-full min-h-[42px] px-3 py-2 rounded-xl border font-mono text-xs"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>

              <label
                className="flex items-start gap-2.5 p-2.5 rounded-xl border cursor-pointer"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                <input
                  type="checkbox"
                  checked={tokenPrefDraft === 'LOCAL'}
                  onChange={(e) => setTokenPrefDraft(e.target.checked ? 'LOCAL' : 'SESSION')}
                  className="mt-0.5"
                />
                <span>
                  <strong className="block" style={{ color: colors.inkBold }}>
                    Remember token across browser restarts
                  </strong>
                  <span className="text-[11px]" style={{ color: colors.muted }}>
                    Off by default (stores token in safer tab sessionStorage; enable only on your personal private computer).
                  </span>
                </span>
              </label>
            </div>

            <div
              className="px-4 sm:px-5 py-3 flex items-center justify-end gap-2 flex-shrink-0"
              style={{ borderTop: `1px solid ${colors.border}`, backgroundColor: colors.surfaceVariant }}
            >
              <button
                type="button"
                onClick={() => setConfigProvider(null)}
                className="min-h-[40px] px-3.5 py-1.5 rounded-lg border text-xs font-bold"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={saveProviderConfig}
                className="min-h-[40px] px-4 py-1.5 rounded-lg text-xs font-black text-white"
                style={{ backgroundColor: colors.teal }}
              >
                Save Client Config
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default BackupVaultTab;
