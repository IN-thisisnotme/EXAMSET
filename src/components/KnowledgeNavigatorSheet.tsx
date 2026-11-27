import React, { useMemo, useState } from 'react';
import {
  ArrowUp,
  Check,
  Grid,
  Star,
  X,
} from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';
import { FlashcardSrsState, StudyNoteBlock } from '../types/phase4';
import { SpacedRepetitionEngine } from '../utils/spacedRepetitionEngine';

type NavigatorFilter = 'ALL' | 'UNMASTERED' | 'STARRED' | 'DUE_SRS';

interface KnowledgeNavigatorSheetProps {
  isOpen: boolean;
  noteTitle: string;
  badgeLabel: string;
  allBlocks: StudyNoteBlock[];
  bookmarkedSet: Set<number>;
  masteredSet: Set<number>;
  srsStates: Record<number, FlashcardSrsState>;
  currentBlockIndex: number | null;
  onJumpToBlock: (blockIndex: number) => void;
  onScrollToTop: () => void;
  onClose: () => void;
}

export const KnowledgeNavigatorSheet: React.FC<KnowledgeNavigatorSheetProps> = ({
  isOpen,
  noteTitle,
  badgeLabel,
  allBlocks,
  bookmarkedSet,
  masteredSet,
  srsStates,
  currentBlockIndex,
  onJumpToBlock,
  onScrollToTop,
  onClose,
}) => {
  const { colors } = useTheme();
  const [filter, setFilter] = useState<NavigatorFilter>('ALL');

  const headings = useMemo(
    () => allBlocks.filter((b): b is Extract<StudyNoteBlock, { kind: 'heading' }> => b.kind === 'heading'),
    [allBlocks]
  );

  const studyUnits = useMemo(
    () =>
      allBlocks.filter(
        (b): b is Extract<StudyNoteBlock, { kind: 'one_liner' | 'multi_liner_card' }> =>
          b.kind === 'one_liner' || b.kind === 'multi_liner_card'
      ),
    [allBlocks]
  );

  const nowMs = Date.now();
  const dueBlockIndices = useMemo(() => {
    const due = new Set<number>();
    for (const unit of studyUnits) {
      const st = srsStates?.[unit.blockIndex];
      if (st && SpacedRepetitionEngine.statusOf(st, nowMs) === 'DUE') {
        due.add(unit.blockIndex);
      }
    }
    return due;
  }, [studyUnits, srsStates, nowMs]);

  const unmasteredCount = useMemo(
    () => studyUnits.filter((u) => !masteredSet.has(u.blockIndex)).length,
    [studyUnits, masteredSet]
  );
  const starredCount = useMemo(
    () => studyUnits.filter((u) => bookmarkedSet.has(u.blockIndex)).length,
    [studyUnits, bookmarkedSet]
  );

  const filteredUnits = useMemo(() => {
    return studyUnits
      .map((block, idx) => ({ ordinal: idx + 1, block }))
      .filter(({ block }) => {
        if (filter === 'UNMASTERED') return !masteredSet.has(block.blockIndex);
        if (filter === 'STARRED') return bookmarkedSet.has(block.blockIndex);
        if (filter === 'DUE_SRS') return dueBlockIndices.has(block.blockIndex);
        return true;
      });
  }, [studyUnits, filter, masteredSet, bookmarkedSet, dueBlockIndices]);

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex justify-end bg-black/50"
      onClick={onClose}
    >
      <div
        className="w-full max-w-sm h-full border-l shadow-2xl flex flex-col"
        style={{ backgroundColor: colors.surface, borderColor: colors.border }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          className="px-4 py-3.5 border-b flex items-center justify-between gap-2"
          style={{ borderColor: colors.border }}
        >
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <Grid className="w-4 h-4 flex-shrink-0" style={{ color: colors.teal }} />
              <h4 className="text-sm font-black" style={{ color: colors.inkBold }}>
                Study Navigator
              </h4>
            </div>
            <p className="text-[0.6875rem] truncate mt-0.5" style={{ color: colors.muted }}>
              {badgeLabel} • {studyUnits.length} Units ({masteredSet.size} Mastered)
            </p>
            {noteTitle && (
              <p className="text-[0.625rem] truncate" style={{ color: colors.inkMuted }}>
                {noteTitle}
              </p>
            )}
          </div>

          <button
            type="button"
            onClick={onClose}
            className="min-w-[40px] min-h-[40px] flex items-center justify-center rounded-lg border"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
            aria-label="Close Navigator"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-4 text-xs">
          {/* Top actions & Section Headings */}
          <div className="flex items-center justify-between gap-2">
            <button
              type="button"
              onClick={() => {
                onScrollToTop();
                onClose();
              }}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg border font-bold text-[0.6875rem]"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.tealDark }}
            >
              <ArrowUp className="w-3.5 h-3.5" />
              Scroll to Top
            </button>
            <span className="text-[0.625rem] font-semibold" style={{ color: colors.muted }}>
              Tap a number to jump
            </span>
          </div>

          {headings.length > 0 && (
            <div className="space-y-1.5">
              <div className="text-[0.625rem] font-black uppercase tracking-wider" style={{ color: colors.muted }}>
                Section Headings ({headings.length})
              </div>
              <div className="flex flex-wrap gap-1.5">
                {headings.map((h) => (
                  <button
                    key={h.blockIndex}
                    type="button"
                    onClick={() => {
                      onJumpToBlock(h.blockIndex);
                      onClose();
                    }}
                    className="px-2.5 py-1 rounded-lg border text-[0.6875rem] font-bold text-left truncate max-w-full"
                    style={{
                      backgroundColor: colors.tealLight,
                      borderColor: colors.border,
                      color: colors.tealDark,
                    }}
                  >
                    § {h.text}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Filter Chips */}
          <div className="flex flex-wrap gap-1.5">
            {(
              [
                { id: 'ALL' as NavigatorFilter, label: `All (${studyUnits.length})` },
                { id: 'UNMASTERED' as NavigatorFilter, label: `Remaining (${unmasteredCount})` },
                { id: 'STARRED' as NavigatorFilter, label: `Starred (${starredCount})` },
                ...(dueBlockIndices.size > 0
                  ? [{ id: 'DUE_SRS' as NavigatorFilter, label: `Due SRS (${dueBlockIndices.size})` }]
                  : []),
              ]
            ).map((chip) => (
              <button
                key={chip.id}
                type="button"
                onClick={() => setFilter(chip.id)}
                className="px-2.5 py-1 rounded-lg border text-[0.6875rem] font-bold"
                style={
                  filter === chip.id
                    ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText, borderColor: colors.primaryButtonBg }
                    : { backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }
                }
              >
                {chip.label}
              </button>
            ))}
          </div>

          {/* Grid */}
          {filteredUnits.length === 0 ? (
            <div
              className="p-6 rounded-xl border text-center"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.muted }}
            >
              No items match this filter.
            </div>
          ) : (
            <div className="grid grid-cols-5 gap-2">
              {filteredUnits.map(({ ordinal, block }) => {
                const isMastered = masteredSet.has(block.blockIndex);
                const isStarred = bookmarkedSet.has(block.blockIndex);
                const isDue = dueBlockIndices.has(block.blockIndex);
                const isCurrent = currentBlockIndex === block.blockIndex;
                return (
                  <button
                    key={block.blockIndex}
                    type="button"
                    onClick={() => {
                      onJumpToBlock(block.blockIndex);
                      onClose();
                    }}
                    className="relative min-h-[44px] rounded-xl border flex flex-col items-center justify-center font-black transition"
                    style={{
                      backgroundColor: isCurrent
                        ? colors.teal
                        : isMastered
                        ? colors.greenLight
                        : colors.surfaceVariant,
                      color: isCurrent
                        ? colors.onAccentText
                        : isMastered
                        ? colors.greenDark
                        : colors.inkBold,
                      borderColor: isCurrent
                        ? colors.teal
                        : isStarred
                        ? colors.amber
                        : colors.border,
                    }}
                    title={block.kind === 'one_liner' ? block.leadTerm || block.detail : block.title}
                  >
                    <span className="text-xs">{ordinal}</span>
                    <div className="flex items-center gap-0.5 mt-0.5">
                      {isMastered && <Check className="w-2.5 h-2.5" />}
                      {isStarred && <Star className="w-2.5 h-2.5" style={{ fill: colors.amber, color: colors.amber }} />}
                      {isDue && (
                        <span
                          className="w-1.5 h-1.5 rounded-full inline-block"
                          style={{ backgroundColor: colors.red }}
                        />
                      )}
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default KnowledgeNavigatorSheet;
