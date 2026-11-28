import React from 'react';
import { Archive, FilePlus2, FolderUp, Sparkles } from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';

interface EmptyPaperPromptCardProps {
  title?: string;
  description?: string;
  onCreateSet?: () => void;
  onBatchImport?: () => void;
  onOpenVault?: () => void;
  onOpenLibrary?: () => void;
}

export const EmptyPaperPromptCard: React.FC<EmptyPaperPromptCardProps> = ({
  title = 'No Question Set Selected',
  description = 'Your ExamSet workspace starts clean just like the Android app. Create your first MCQ/MSQ paper, batch-import .json/.txt files, or restore an existing .examset.zip / .examset.json backup.',
  onCreateSet,
  onBatchImport,
  onOpenVault,
  onOpenLibrary,
}) => {
  const { colors, scalePx } = useTheme();

  return (
    <div
      className="rounded-2xl border-2 border-dashed p-6 sm:p-10 text-center space-y-5 max-w-2xl mx-auto my-6"
      style={{
        backgroundColor: colors.surfaceVariant,
        borderColor: colors.border,
      }}
    >
      <div
        className="w-14 h-14 mx-auto rounded-2xl flex items-center justify-center shadow-sm"
        style={{ backgroundColor: colors.tealLight, color: colors.teal }}
      >
        <Sparkles className="w-7 h-7" />
      </div>

      <div className="space-y-2">
        <h2
          className="font-black tracking-tight"
          style={{ fontSize: scalePx(20), color: colors.inkBold }}
        >
          {title}
        </h2>
        <p className="text-xs sm:text-sm leading-relaxed max-w-xl mx-auto" style={{ color: colors.muted }}>
          {description}
        </p>
      </div>

      <div className="flex flex-wrap items-center justify-center gap-2.5 pt-1">
        {onCreateSet && (
          <button
            type="button"
            onClick={onCreateSet}
            className="min-h-[44px] flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-black shadow-sm"
            style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
          >
            <FilePlus2 className="w-4 h-4" />
            Create Question Set
          </button>
        )}
        {onBatchImport && (
          <button
            type="button"
            onClick={onBatchImport}
            className="min-h-[44px] flex items-center gap-2 px-4 py-2 rounded-xl border text-xs font-bold"
            style={{
              backgroundColor: colors.surface,
              borderColor: colors.border,
              color: colors.inkBold,
            }}
          >
            <FolderUp className="w-4 h-4" style={{ color: colors.teal }} />
            Batch Import Files
          </button>
        )}
        {onOpenVault && (
          <button
            type="button"
            onClick={onOpenVault}
            className="min-h-[44px] flex items-center gap-2 px-4 py-2 rounded-xl border text-xs font-bold"
            style={{
              backgroundColor: colors.surface,
              borderColor: colors.border,
              color: colors.inkBold,
            }}
          >
            <Archive className="w-4 h-4" style={{ color: colors.teal }} />
            Restore Backup Vault
          </button>
        )}
        {onOpenLibrary && (
          <button
            type="button"
            onClick={onOpenLibrary}
            className="min-h-[44px] flex items-center gap-2 px-4 py-2 rounded-xl border text-xs font-bold"
            style={{
              backgroundColor: colors.surface,
              borderColor: colors.border,
              color: colors.inkBold,
            }}
          >
            Open Library &amp; Folders
          </button>
        )}
      </div>
    </div>
  );
};
