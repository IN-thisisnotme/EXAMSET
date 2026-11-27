import React, { useEffect, useMemo, useState } from 'react';
import {
  CheckCircle2,
  ClipboardPaste,
  FilePlus2,
  Layers,
  Sparkles,
  X,
} from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';
import { StudyNoteRecord } from '../types/phase4';
import { StudyNoteParser } from '../utils/studyNoteParser';

interface AppendKnowledgeModalProps {
  isOpen: boolean;
  note: StudyNoteRecord | null;
  onApplyAppend: (updatedNote: StudyNoteRecord, addedCount: number, skippedCount: number) => void;
  onClose: () => void;
}

export const AppendKnowledgeModal: React.FC<AppendKnowledgeModalProps> = ({
  isOpen,
  note,
  onApplyAppend,
  onClose,
}) => {
  const { colors } = useTheme();
  const [sectionHeading, setSectionHeading] = useState<string>('');
  const [incomingText, setIncomingText] = useState<string>('');
  const [skipDuplicates, setSkipDuplicates] = useState<boolean>(true);
  const [showPreviewSegment, setShowPreviewSegment] = useState<boolean>(false);
  const [clipboardStatus, setClipboardStatus] = useState<string>('');

  useEffect(() => {
    if (!isOpen) return;
    setSectionHeading('');
    setIncomingText('');
    setSkipDuplicates(true);
    setShowPreviewSegment(false);
    setClipboardStatus('');
  }, [isOpen, note?.id]);

  const combinedInput = useMemo(() => {
    const cleanBody = incomingText.trim();
    if (!cleanBody) return '';
    const cleanHeading = sectionHeading.trim().replace(/^#+\s*/, '');
    return cleanHeading ? `## ${cleanHeading}\n${cleanBody}` : cleanBody;
  }, [sectionHeading, incomingText]);

  const appendPreview = useMemo(() => {
    if (!note) return null;
    return StudyNoteParser.prepareAppendedContent(
      note.rawContent,
      combinedInput,
      note.formatRule,
      skipDuplicates
    );
  }, [note, combinedInput, skipDuplicates]);

  if (!isOpen || !note || !appendPreview) return null;

  const handlePasteFromClipboard = async () => {
    setClipboardStatus('');
    try {
      if (navigator.clipboard && typeof navigator.clipboard.readText === 'function') {
        const clip = await navigator.clipboard.readText();
        if (clip && clip.trim()) {
          setIncomingText((prev) => (prev.trim() ? `${prev.trimEnd()}\n\n${clip.trim()}` : clip.trim()));
          setClipboardStatus('Pasted text from clipboard.');
        } else {
          setClipboardStatus('Clipboard is empty.');
        }
      } else {
        setClipboardStatus('Clipboard API unavailable in this browser; paste with Ctrl/Cmd+V below.');
      }
    } catch {
      setClipboardStatus('Clipboard permission denied; please paste directly into the box below.');
    }
  };

  const canApply =
    Boolean(appendPreview.appendSegment.trim()) &&
    (appendPreview.addedUnitCount > 0 || appendPreview.structuralBlocksCount > 0);

  const handleConfirm = () => {
    if (!canApply) return;
    onApplyAppend(
      {
        ...note,
        rawContent: appendPreview.combinedContent,
        updatedAt: Date.now(),
      },
      appendPreview.addedUnitCount,
      appendPreview.skippedDuplicatesCount
    );
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-3 sm:p-4 overflow-y-auto"
      onClick={onClose}
    >
      <div
        className="w-full max-w-2xl rounded-2xl border shadow-2xl overflow-hidden flex flex-col max-h-[90dvh]"
        style={{ backgroundColor: colors.surface, borderColor: colors.border }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          className="px-5 py-3.5 border-b flex items-center justify-between gap-3"
          style={{ backgroundColor: colors.navyBanner, borderColor: colors.border }}
        >
          <div className="min-w-0">
            <h3 className="text-sm sm:text-base font-black flex items-center gap-2" style={{ color: colors.navyBannerText }}>
              <FilePlus2 className="w-4 h-4 text-emerald-300 flex-shrink-0" />
              <span className="truncate">Append to Study Set</span>
            </h3>
            <p className="text-[0.6875rem] opacity-85 truncate" style={{ color: colors.navyBannerText }}>
              {note.title} • {appendPreview.existingUnitCount} existing item(s) preserved
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white"
            aria-label="Close Append Modal"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-4 sm:p-5 overflow-y-auto space-y-3.5 text-xs flex-1">
          <div
            className="p-3 rounded-xl border flex flex-wrap items-center justify-between gap-2"
            style={{ backgroundColor: colors.tealLight, borderColor: colors.teal }}
          >
            <div className="text-[0.6875rem] font-semibold" style={{ color: colors.tealDark }}>
              Non-destructive append keeps all existing stars, mastery checkmarks, and SM-2 flashcard progress intact and continues numbering from #{appendPreview.existingUnitCount + 1}.
            </div>
            <button
              type="button"
              onClick={() => void handlePasteFromClipboard()}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-bold flex-shrink-0"
              style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
            >
              <ClipboardPaste className="w-3.5 h-3.5" />
              Paste Clipboard
            </button>
          </div>

          {clipboardStatus && (
            <div className="text-[0.6875rem] font-semibold" style={{ color: colors.tealDark }}>
              {clipboardStatus}
            </div>
          )}

          <div>
            <label className="block font-bold mb-1" style={{ color: colors.inkBold }}>
              Optional Section Heading (## Heading)
            </label>
            <input
              type="text"
              value={sectionHeading}
              onChange={(e) => setSectionHeading(e.target.value)}
              placeholder="e.g., Part II — Exception Rules & Traps"
              className="w-full px-3 py-2 rounded-lg border"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
            />
          </div>

          <div>
            <label className="block font-bold mb-1" style={{ color: colors.inkBold }}>
              New Items / Notes to Append
            </label>
            <textarea
              value={incomingText}
              onChange={(e) => setIncomingText(e.target.value)}
              rows={8}
              placeholder="Paste new numbered facts, Q&A flashcards, Markdown tables, or callouts..."
              className="w-full min-h-[11rem] p-3 rounded-xl border font-mono text-xs leading-relaxed"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
            />
          </div>

          <div className="flex flex-wrap items-center justify-between gap-2">
            <label className="flex items-center gap-2 cursor-pointer font-semibold" style={{ color: colors.ink }}>
              <input
                type="checkbox"
                checked={skipDuplicates}
                onChange={(e) => setSkipDuplicates(e.target.checked)}
              />
              <span>Skip duplicate recall items already in this note</span>
            </label>

            {appendPreview.appendSegment && (
              <button
                type="button"
                onClick={() => setShowPreviewSegment((v) => !v)}
                className="font-bold underline"
                style={{ color: colors.tealDark }}
              >
                {showPreviewSegment ? 'Hide Appended Segment' : 'Preview Numbered Output'}
              </button>
            )}
          </div>

          {combinedInput.trim() && (
            <div
              className="p-3 rounded-xl border flex flex-wrap items-center gap-2 text-[0.6875rem] font-bold"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
            >
              <Sparkles className="w-3.5 h-3.5" style={{ color: colors.teal }} />
              <span style={{ color: colors.greenDark }}>+{appendPreview.addedUnitCount} Recall Item(s)</span>
              {appendPreview.structuralBlocksCount > 0 && (
                <span style={{ color: colors.ink }}>• {appendPreview.structuralBlocksCount} Heading/Table/Callout(s)</span>
              )}
              {appendPreview.skippedDuplicatesCount > 0 && (
                <span style={{ color: colors.amber }}>
                  • {appendPreview.skippedDuplicatesCount} Duplicate(s) Skipped
                </span>
              )}
            </div>
          )}

          {showPreviewSegment && appendPreview.appendSegment && (
            <pre
              className="p-3 rounded-xl border font-mono text-[0.6875rem] whitespace-pre-wrap max-h-48 overflow-y-auto"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.teal, color: colors.inkBold }}
            >
              {appendPreview.appendSegment}
            </pre>
          )}
        </div>

        <div
          className="px-5 py-3 border-t flex items-center justify-end gap-2"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-lg border text-xs font-bold"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={!canApply}
            onClick={handleConfirm}
            className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-black disabled:opacity-50"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            <CheckCircle2 className="w-4 h-4" />
            Append {appendPreview.addedUnitCount > 0 ? `${appendPreview.addedUnitCount} Item(s)` : 'Content'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default AppendKnowledgeModal;
