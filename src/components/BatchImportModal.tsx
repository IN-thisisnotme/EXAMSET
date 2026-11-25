import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  FileJson,
  Layers,
  Upload,
  X,
  XCircle,
} from 'lucide-react';
import { BatchImportItem, FolderItem, QuestionPaper } from '../types/exam';
import { useTheme } from '../theme/ThemeProvider';
import { BatchExamImporter } from '../utils/batchExamImporter';
import { ExamNumberGenerator } from '../utils/examNumberGenerator';
import { buildUserFolderSuggestions } from '../utils/folderPathHelper';

interface BatchImportModalProps {
  isOpen: boolean;
  folders: FolderItem[];
  existingPapers: QuestionPaper[];
  defaultFolderId?: string;
  onImportBatch: (papers: QuestionPaper[]) => void;
  onClose: () => void;
}

export const BatchImportModal: React.FC<BatchImportModalProps> = ({
  isOpen,
  folders,
  existingPapers,
  defaultFolderId = 'default',
  onImportBatch,
  onClose,
}) => {
  const { colors } = useTheme();
  const [items, setItems] = useState<BatchImportItem[]>([]);
  const [targetFolderId, setTargetFolderId] = useState<string>(defaultFolderId);
  const [assignCohortNumbers, setAssignCohortNumbers] = useState<boolean>(true);
  const [pastedJson, setPastedJson] = useState<string>('');

  const summary = useMemo(() => BatchExamImporter.summarize(items), [items]);
  const selectedCount = useMemo(() => items.filter((i) => i.isSelected && i.paper).length, [items]);

  if (!isOpen) return null;

  const handleFilesSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const fileList = Array.from(e.target.files || []);
    if (fileList.length === 0) return;

    const collected: BatchImportItem[] = [];
    for (const file of fileList) {
      const text = await file.text();
      collected.push(...BatchExamImporter.validateRawContent(text, file.name, file.size));
    }
    setItems((prev) => [...prev, ...collected]);
    e.target.value = '';
  };

  const handleAddPasted = () => {
    if (!pastedJson.trim()) return;
    const parsed = BatchExamImporter.validateRawContent(pastedJson, `pasted_batch_${items.length + 1}.json`);
    setItems((prev) => [...prev, ...parsed]);
    setPastedJson('');
  };

  const handleConfirmImport = () => {
    const selectedItems = items.filter((i) => i.isSelected && i.paper);
    if (selectedItems.length === 0) return;

    const existingRolls = existingPapers.map((p) => p.metadata.rollNumber || '');
    const existingBooklets = existingPapers.map((p) => p.metadata.bookletNumber || '');
    const seqStartRoll = ExamNumberGenerator.nextRollNumber(existingRolls);
    const seqStartBooklet = ExamNumberGenerator.nextBookletNumber(existingBooklets);
    const seqNumbers = ExamNumberGenerator.generateSequentialNumbers(seqStartRoll, seqStartBooklet, selectedItems.length);

    const papersToImport: QuestionPaper[] = selectedItems.map((item, idx) => {
      const base = item.paper!;
      return {
        ...base,
        metadata: {
          ...base.metadata,
          id: `paper-${Date.now()}-${idx}-${Math.random().toString(36).slice(2, 6)}`,
          folderId: targetFolderId || 'default',
          rollNumber: assignCohortNumbers ? seqNumbers[idx].rollNumber : base.metadata.rollNumber || seqNumbers[idx].rollNumber,
          bookletNumber: assignCohortNumbers
            ? seqNumbers[idx].bookletNumber
            : base.metadata.bookletNumber || seqNumbers[idx].bookletNumber,
          setNumber: ExamNumberGenerator.normalizeSetCode(base.metadata.setNumber || String(idx + 1)),
          updatedAt: Date.now(),
        },
      };
    });

    onImportBatch(papersToImport);
    setItems([]);
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.6)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-3xl max-h-[90vh] rounded-2xl shadow-2xl flex flex-col overflow-hidden"
        style={{
          backgroundColor: colors.surface,
          border: `1px solid ${colors.border}`,
          color: colors.ink,
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          className="px-5 py-4 flex items-center justify-between"
          style={{ borderBottom: `1px solid ${colors.border}` }}
        >
          <div className="flex items-center gap-2.5">
            <div
              className="w-9 h-9 rounded-lg flex items-center justify-center"
              style={{ backgroundColor: colors.tealLight, color: colors.teal }}
            >
              <Layers className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm font-bold" style={{ color: colors.inkBold }}>
                Batch JSON & Multi-File Importer
              </h3>
              <p className="text-[0.6875rem]" style={{ color: colors.muted }}>
                Upload multiple .json or .txt exam files simultaneously with schema validation & sequential Roll/Booklet assignment
              </p>
            </div>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg" style={{ color: colors.muted }} aria-label="Close">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-5 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <label
              className="cursor-pointer inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold shadow-sm"
              style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
            >
              <Upload className="w-4 h-4" />
              <span>Select Multiple Files (.json / .txt)</span>
              <input
                type="file"
                accept=".json,.txt,.md"
                multiple
                onChange={handleFilesSelected}
                className="hidden"
              />
            </label>

            <div className="flex items-center gap-3 text-xs">
              <div className="flex items-center gap-1.5">
                <span className="font-bold" style={{ color: colors.inkMuted }}>
                  Folder:
                </span>
                <select
                  value={targetFolderId}
                  onChange={(e) => setTargetFolderId(e.target.value)}
                  className="px-2.5 py-1.5 rounded-lg"
                  style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
                >
                  {folders
                    .filter((f) => f.id === 'default')
                    .map((f) => (
                      <option key={f.id} value={f.id}>
                        {f.name}
                      </option>
                    ))}
                  {buildUserFolderSuggestions(folders).map((entry) => (
                    <option key={entry.id} value={entry.id}>
                      [{entry.levelLabel}] {entry.fullPath}
                    </option>
                  ))}
                </select>
              </div>
              <label className="flex items-center gap-1.5 cursor-pointer font-semibold">
                <input
                  type="checkbox"
                  checked={assignCohortNumbers}
                  onChange={(e) => setAssignCohortNumbers(e.target.checked)}
                />
                <span>Sequential Roll/Booklet IDs</span>
              </label>
            </div>
          </div>

          {/* Or Paste JSON Array/Bundle */}
          <div className="flex gap-2">
            <input
              type="text"
              value={pastedJson}
              onChange={(e) => setPastedJson(e.target.value)}
              placeholder="Or paste JSON paper / array / bundle and click Queue..."
              className="flex-1 px-3 py-2 rounded-lg text-xs font-mono focus:outline-none"
              style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
            />
            <button
              type="button"
              onClick={handleAddPasted}
              className="px-3.5 py-2 rounded-lg text-xs font-bold border"
              style={{ borderColor: colors.border, backgroundColor: colors.blueLight, color: colors.teal }}
            >
              Queue JSON
            </button>
          </div>

          {/* Summary Bar */}
          {items.length > 0 && (
            <div
              className="p-3 rounded-xl flex flex-wrap items-center justify-between gap-2 text-xs font-semibold"
              style={{ backgroundColor: colors.blueLight, border: `1px solid ${colors.border}` }}
            >
              <span>
                Files: {summary.totalFiles} • Total Qs: {summary.totalQuestions}
              </span>
              <div className="flex items-center gap-2">
                <span style={{ color: colors.green }}>✓ Valid: {summary.validCount}</span>
                <span style={{ color: colors.amber }}>⚠ Warning: {summary.warningCount}</span>
                <span style={{ color: colors.red }}>✕ Invalid: {summary.invalidCount}</span>
              </div>
            </div>
          )}

          {/* Queue List */}
          {items.length === 0 ? (
            <div
              className="p-8 rounded-xl text-center space-y-2 border-2 border-dashed"
              style={{ borderColor: colors.border, backgroundColor: colors.surfaceVariant }}
            >
              <FileJson className="w-8 h-8 mx-auto" style={{ color: colors.muted }} />
              <div className="text-xs font-bold" style={{ color: colors.inkBold }}>
                No files queued for batch import
              </div>
              <div className="text-[0.6875rem]" style={{ color: colors.muted }}>
                Supports single ExamSet JSON, arrays of question sets, or multi-set bundles.
              </div>
            </div>
          ) : (
            <div className="space-y-2">
              {items.map((item) => {
                const statusColor =
                  item.status === 'VALID' ? colors.green : item.status === 'WARNING' ? colors.amber : colors.red;
                const StatusIcon =
                  item.status === 'VALID' ? CheckCircle2 : item.status === 'WARNING' ? AlertTriangle : XCircle;
                return (
                  <div
                    key={item.id}
                    className="p-3 rounded-xl flex items-start justify-between gap-3"
                    style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}` }}
                  >
                    <label className="flex items-start gap-2.5 flex-1 cursor-pointer">
                      <input
                        type="checkbox"
                        disabled={!item.paper}
                        checked={item.isSelected}
                        onChange={(e) =>
                          setItems((prev) =>
                            prev.map((x) => (x.id === item.id ? { ...x, isSelected: e.target.checked } : x))
                          )
                        }
                        className="mt-1"
                      />
                      <div className="space-y-1 flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-xs font-bold truncate" style={{ color: colors.inkBold }}>
                            {item.paper?.metadata.title || item.fileName}
                          </span>
                          <span className="text-[0.625rem] font-mono" style={{ color: colors.muted }}>
                            {item.fileName} ({item.fileSizeFormatted})
                          </span>
                        </div>
                        {item.paper && (
                          <div className="text-[0.6875rem]" style={{ color: colors.inkMuted }}>
                            {item.paper.metadata.subject} • {item.paper.questions.length} Qs • Set{' '}
                            {item.paper.metadata.setNumber}
                          </div>
                        )}
                        {item.issues.length > 0 && (
                          <div className="space-y-0.5">
                            {item.issues.map((iss, k) => (
                              <div
                                key={k}
                                className="text-[0.65625rem]"
                                style={{
                                  color:
                                    iss.severity === 'ERROR'
                                      ? colors.red
                                      : iss.severity === 'WARNING'
                                        ? colors.amber
                                        : colors.muted,
                                }}
                              >
                                • [{iss.severity}] {iss.message}
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </label>

                    <div className="flex items-center gap-2">
                      <span
                        className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[0.625rem] font-bold"
                        style={{ backgroundColor: colors.surface, color: statusColor, border: `1px solid ${statusColor}` }}
                      >
                        <StatusIcon className="w-3 h-3" />
                        {item.status}
                      </span>
                      <button
                        type="button"
                        onClick={() => setItems((prev) => prev.filter((x) => x.id !== item.id))}
                        className="text-xs px-1.5 py-0.5 rounded"
                        style={{ color: colors.muted }}
                      >
                        ✕
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        <div
          className="px-5 py-3 flex items-center justify-between gap-2"
          style={{ borderTop: `1px solid ${colors.border}`, backgroundColor: colors.surfaceVariant }}
        >
          <button
            type="button"
            onClick={() => setItems([])}
            className="text-xs font-semibold"
            style={{ color: colors.muted }}
          >
            Clear Queue
          </button>
          <div className="flex items-center gap-2">
            <button type="button" onClick={onClose} className="px-3 py-2 text-xs font-semibold" style={{ color: colors.muted }}>
              Cancel
            </button>
            <button
              type="button"
              disabled={selectedCount === 0}
              onClick={handleConfirmImport}
              className="px-4 py-2 rounded-lg text-xs font-bold disabled:opacity-40"
              style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
            >
              Import {selectedCount} Selected Set{selectedCount === 1 ? '' : 's'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default BatchImportModal;
