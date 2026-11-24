import React, { useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  Archive,
  CheckCircle2,
  Download,
  FileJson,
  HardDrive,
  History,
  Layers,
  RefreshCw,
  ShieldCheck,
  Trash2,
  Upload,
} from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import type {
  RestoreMode,
  RestorePreview,
  VaultSnapshotRecord,
} from '../../types/phase5';
import {
  buildVaultArchive,
  computeBackupCounts,
  inspectVaultArchiveText,
  type WorkspaceBackupState,
} from '../../utils/vaultArchiveEngine';

interface BackupVaultTabProps {
  workspaceState: WorkspaceBackupState;
  snapshots: VaultSnapshotRecord[];
  onCreateSnapshot: (label: string, kind: 'manual' | 'pre_restore', rawJson: string) => void;
  onDeleteSnapshot: (snapshotId: string) => void;
  onExecuteRestore: (preview: RestorePreview, mode: RestoreMode) => void;
  onNotify: (msg: string) => void;
}

export const BackupVaultTab: React.FC<BackupVaultTabProps> = ({
  workspaceState,
  snapshots,
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

  const liveCounts = useMemo(() => computeBackupCounts(workspaceState), [workspaceState]);

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
      `Checkpoint (${manifest.counts.papersCount} Papers, ${manifest.counts.notesCount} Notes, ${manifest.counts.vocabWordsCount} Words)`;
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

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div
        className="rounded-2xl border p-4 sm:p-5 flex flex-col md:flex-row md:items-center justify-between gap-4"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div>
          <div className="flex items-center gap-2">
            <Archive className="w-5 h-5" style={{ color: colors.teal }} />
            <h2 className="font-black" style={{ fontSize: scalePx(20), color: colors.inkBold }}>
              Backup Vault &amp; SHA-256 Snapshot Restore
            </h2>
          </div>
          <p className="text-xs mt-1" style={{ color: colors.muted }}>
            Full workspace export &amp; recovery across Question Papers, Attempts, Notes, SM-2, Vocab, Todos, Brain Lab stats &amp; Theme settings
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={handleDownloadVault}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-black text-white shadow-sm"
            style={{ backgroundColor: colors.teal }}
          >
            <Download className="w-4 h-4" />
            Export Vault (.examset.json)
          </button>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold border"
            style={{
              backgroundColor: colors.surface,
              borderColor: colors.border,
              color: colors.inkBold,
            }}
          >
            <Upload className="w-4 h-4" style={{ color: colors.teal }} />
            Import &amp; Inspect File
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
          { label: 'Question Papers', value: liveCounts.papersCount },
          { label: 'Study Notes', value: liveCounts.notesCount },
          { label: 'Vocab Words', value: `${liveCounts.vocabWordsCount} (${liveCounts.vocabDecksCount} decks)` },
          { label: 'Test Attempts', value: liveCounts.attemptsCount },
          { label: 'Tasks / Presets', value: `${liveCounts.todosCount} / ${liveCounts.promptPresetsCount}` },
        ].map((stat) => (
          <div
            key={stat.label}
            className="p-3.5 rounded-xl border"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="text-sm font-black" style={{ color: colors.teal }}>
              {stat.value}
            </div>
            <div className="text-[11px] font-semibold mt-0.5" style={{ color: colors.muted }}>
              {stat.label}
            </div>
          </div>
        ))}
      </div>

      {/* Two-Column Actions: Left = Local Checkpoint & Paste JSON | Right = Restore Preview or Local Vault History */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column */}
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
              Save a SHA-256 checksummed recovery checkpoint directly inside your browser storage before bulk imports or edits.
            </p>
            <div className="flex flex-col sm:flex-row gap-2">
              <input
                type="text"
                value={customSnapshotLabel}
                onChange={(e) => setCustomSnapshotLabel(e.target.value)}
                placeholder="Optional label (e.g. Before UPSC Full Mock Batch)"
                className="flex-1 px-3 py-2 rounded-lg border text-xs"
                style={{
                  backgroundColor: colors.surfaceVariant,
                  borderColor: colors.border,
                  color: colors.inkBold,
                }}
              />
              <button
                type="button"
                onClick={handleSaveLocalCheckpoint}
                className="px-4 py-2 rounded-lg text-xs font-black text-white flex-shrink-0"
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
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <FileJson className="w-4 h-4" style={{ color: colors.teal }} />
                <h3 className="text-sm font-black" style={{ color: colors.inkBold }}>
                  Restore from File or Pasted JSON
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setPasteMode((v) => !v)}
                className="text-xs font-bold underline"
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
                  className="px-4 py-2 rounded-lg text-xs font-black text-white"
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

        {/* Right Column */}
        <div className="lg:col-span-7 space-y-5">
          {/* Restore Preview Panel (when a file/snapshot is staged for restore) */}
          {restorePreview && (
            <div
              className="rounded-2xl border-2 p-4 sm:p-5 space-y-4"
              style={{ backgroundColor: colors.surface, borderColor: colors.teal }}
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="flex items-center gap-2">
                    {restorePreview.checksumVerified ? (
                      <ShieldCheck className="w-5 h-5" style={{ color: colors.teal }} />
                    ) : (
                      <AlertTriangle className="w-5 h-5" style={{ color: colors.amber }} />
                    )}
                    <h3 className="text-sm font-black" style={{ color: colors.inkBold }}>
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
                  className="text-xs font-bold px-2 py-1 rounded border"
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

              {/* Restore Mode Selector */}
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
                className="w-full py-2.5 rounded-xl text-xs font-black text-white flex items-center justify-center gap-2"
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
            <div className="flex items-center justify-between">
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
                          className="text-[10px] font-black px-2 py-0.5 rounded-full uppercase"
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
                        {new Date(snap.createdAt).toLocaleString()} • {snap.manifest.counts.papersCount} Papers •{' '}
                        {snap.manifest.counts.notesCount} Notes • {snap.manifest.counts.vocabWordsCount} Words
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 flex-shrink-0">
                      <button
                        type="button"
                        onClick={() => handleInspectRaw(snap.rawJson, snap.label)}
                        className="px-2.5 py-1.5 rounded-lg text-xs font-bold text-white"
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
                        className="p-1.5 rounded-lg border"
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
                        className="p-1.5 rounded-lg"
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
    </div>
  );
};

export default BackupVaultTab;
