import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  CheckCircle2,
  Eye,
  Flame,
  Layers,
  RotateCcw,
  Shuffle,
  Star,
} from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';
import {
  FlashcardSrsState,
  SrsRating,
  StudyNoteBlock,
  StudyNoteRecord,
} from '../types/phase4';
import { SpacedRepetitionEngine } from '../utils/spacedRepetitionEngine';
import { LatexText } from './LatexText';

interface FlashcardDeckViewProps {
  note: StudyNoteRecord;
  blocks: StudyNoteBlock[];
  onUpdateSrsStates: (nextStates: Record<number, FlashcardSrsState>) => void;
  onToggleBookmark: (blockIndex: number) => void;
}

type StudyCardBlock = Extract<StudyNoteBlock, { kind: 'one_liner' | 'multi_liner_card' }>;

export const FlashcardDeckView: React.FC<FlashcardDeckViewProps> = ({
  note,
  blocks,
  onUpdateSrsStates,
  onToggleBookmark,
}) => {
  const { colors } = useTheme();
  const [cramAll, setCramAll] = useState<boolean>(false);
  const [shuffleSeed, setShuffleSeed] = useState<number>(0);
  const [reverseSides, setReverseSides] = useState<boolean>(false);
  const [isFlipped, setIsFlipped] = useState<boolean>(false);
  const [queue, setQueue] = useState<StudyCardBlock[]>([]);
  const [currentIdx, setCurrentIdx] = useState<number>(0);
  const [reviewedInSession, setReviewedInSession] = useState<number>(0);
  const [confirmReset, setConfirmReset] = useState<boolean>(false);

  const studyCards = useMemo(
    () =>
      blocks.filter(
        (b): b is StudyCardBlock => b.kind === 'one_liner' || b.kind === 'multi_liner_card'
      ),
    [blocks]
  );

  const srsStringKeyMap = useMemo(() => {
    const m: Record<string, FlashcardSrsState | undefined> = {};
    for (const [k, v] of Object.entries(note.srsStates || {})) {
      m[String(k)] = v;
    }
    return m;
  }, [note.srsStates]);

  const deckStats = useMemo(
    () => SpacedRepetitionEngine.computeDeckStats(studyCards, (c) => c.blockIndex, srsStringKeyMap),
    [studyCards, srsStringKeyMap]
  );

  // Build / rebuild study queue when note or deck mode changes
  const rebuildQueue = useCallback(
    (useCram: boolean, seed: number) => {
      const built = SpacedRepetitionEngine.buildStudyQueue(
        studyCards,
        (c) => c.blockIndex,
        srsStringKeyMap,
        { cramAll: useCram, prioritizeDue: true, shuffleSeed: seed }
      );
      setQueue(built);
      setCurrentIdx(0);
      setIsFlipped(false);
    },
    [studyCards, srsStringKeyMap]
  );

  useEffect(() => {
    rebuildQueue(cramAll, shuffleSeed);
    setReviewedInSession(0);
    // Only reset session queue automatically when note id, filter mode, or shuffle changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [note.id, cramAll, shuffleSeed, studyCards.length]);

  const activeCard = queue[currentIdx] || null;
  const activeSrs = activeCard ? note.srsStates?.[activeCard.blockIndex] : undefined;

  const intervalPreviews = useMemo(
    () => SpacedRepetitionEngine.previewNextIntervalLabels(activeSrs),
    [activeSrs]
  );

  const handleRate = useCallback(
    (rating: SrsRating) => {
      if (!activeCard) return;
      const nextState = SpacedRepetitionEngine.computeNextState(
        activeSrs,
        activeCard.blockIndex,
        rating
      );
      const nextMap: Record<number, FlashcardSrsState> = {
        ...(note.srsStates || {}),
        [activeCard.blockIndex]: nextState,
      };
      onUpdateSrsStates(nextMap);
      setReviewedInSession((c) => c + 1);

      let workingQueue = queue;
      if (rating === 'AGAIN') {
        workingQueue = SpacedRepetitionEngine.reinsertCardSoon(queue, currentIdx, activeCard);
        setQueue(workingQueue);
      }

      setCurrentIdx((idx) => idx + 1);
      setIsFlipped(false);
    },
    [activeCard, activeSrs, note.srsStates, onUpdateSrsStates, queue, currentIdx]
  );

  // Keyboard shortcuts for fast review: Space/Enter = Flip, 1-4 = Rate
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
      if (!activeCard) return;

      if (e.code === 'Space' || e.key === 'Enter') {
        e.preventDefault();
        setIsFlipped((f) => !f);
      } else if (isFlipped) {
        if (e.key === '1') handleRate('AGAIN');
        else if (e.key === '2') handleRate('HARD');
        else if (e.key === '3') handleRate('GOOD');
        else if (e.key === '4') handleRate('EASY');
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [activeCard, isFlipped, handleRate]);

  const bookmarkedSet = useMemo(() => new Set(note.bookmarkedIndices || []), [note.bookmarkedIndices]);

  if (studyCards.length === 0) {
    return (
      <div
        className="rounded-xl border p-8 text-center space-y-2"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <Layers className="w-8 h-8 mx-auto" style={{ color: colors.muted }} />
        <p className="text-sm font-bold" style={{ color: colors.inkBold }}>
          No flashcards or recall items extracted from this note yet.
        </p>
        <p className="text-xs" style={{ color: colors.muted }}>
          Add numbered facts (`1. **Term** -&gt; Detail`) or `Q: ... / A: ...` lines to practice with SM-2 Spaced Repetition.
        </p>
      </div>
    );
  }

  const renderCardFront = (card: StudyCardBlock) => {
    if (card.kind === 'one_liner') {
      const prompt = reverseSides ? card.detail : card.leadTerm || 'Recall Fact';
      return (
        <div className="space-y-2 text-center">
          <span
            className="inline-block px-2.5 py-0.5 rounded-full text-[0.625rem] font-bold border"
            style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
          >
            Item #{card.displayIndex} • {reverseSides ? 'Reverse Clue' : 'Front Prompt'}
          </span>
          <div className="text-lg sm:text-xl font-bold leading-snug" style={{ color: colors.inkBold }}>
            <LatexText text={prompt} />
          </div>
        </div>
      );
    }

    const promptTitle = card.title;
    return (
      <div className="space-y-3 text-center">
        <div className="flex flex-wrap items-center justify-center gap-1.5">
          <span
            className="px-2.5 py-0.5 rounded-full text-[0.625rem] font-bold border"
            style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
          >
            {card.displayIndex} • {card.questionType || 'Concept Card'}
          </span>
          {card.statusTag && (
            <span
              className="px-2 py-0.5 rounded-full text-[0.625rem] font-bold"
              style={{ backgroundColor: colors.redLight, color: colors.red }}
            >
              {card.statusTag}
            </span>
          )}
        </div>
        <div className="text-base sm:text-lg font-bold leading-snug" style={{ color: colors.inkBold }}>
          <LatexText text={promptTitle} />
        </div>
        {card.optionsList.length > 0 && (
          <div className="text-left max-w-xl mx-auto space-y-1 pt-2 text-xs" style={{ color: colors.ink }}>
            {card.optionsList.map((o) => (
              <div key={o.label}>
                <strong>({o.label})</strong> <LatexText text={o.text} />
              </div>
            ))}
          </div>
        )}
      </div>
    );
  };

  const renderCardBack = (card: StudyCardBlock) => {
    if (card.kind === 'one_liner') {
      const back = reverseSides ? card.leadTerm || card.detail : card.detail;
      return (
        <div className="space-y-2">
          <div className="text-[0.6875rem] font-bold uppercase tracking-wider" style={{ color: colors.teal }}>
            Active Recall Answer
          </div>
          <div className="text-sm sm:text-base leading-relaxed" style={{ color: colors.inkBold }}>
            <LatexText text={back} />
          </div>
        </div>
      );
    }

    return (
      <div className="space-y-2.5 text-left">
        <div className="text-[0.6875rem] font-bold uppercase tracking-wider" style={{ color: colors.teal }}>
          Answer &amp; Key Breakdown
        </div>
        {card.correctAnswer && (
          <div
            className="px-3 py-1.5 rounded-lg border text-xs font-bold"
            style={{ backgroundColor: colors.greenLight, borderColor: colors.green, color: colors.greenDark }}
          >
            Correct Answer: <LatexText text={card.correctAnswer} />
          </div>
        )}
        {card.paragraphs.map((p, i) => (
          <div key={i} className="text-sm leading-relaxed" style={{ color: colors.ink }}>
            <LatexText text={p} />
          </div>
        ))}
        {card.formula && (
          <div
            className="px-3 py-2 rounded-lg border font-mono text-xs"
            style={{ backgroundColor: colors.blueLight, borderColor: colors.border, color: colors.inkBold }}
          >
            <strong className="mr-1.5">Formula:</strong>
            <LatexText text={card.formula} />
          </div>
        )}
        {card.keyTakeaway && (
          <div
            className="px-3 py-2 rounded-lg border text-xs"
            style={{ backgroundColor: colors.amberLight, borderColor: colors.amber, color: colors.inkBold }}
          >
            <strong>Key Takeaway:</strong> <LatexText text={card.keyTakeaway} />
          </div>
        )}
        {card.nestedBullets.length > 0 && (
          <ul className="space-y-1 pl-4 list-disc text-xs" style={{ color: colors.ink }}>
            {card.nestedBullets.map((nb, idx) => (
              <li key={idx} style={{ marginLeft: `${(nb.level - 1) * 10}px` }}>
                <LatexText text={nb.text} />
              </li>
            ))}
          </ul>
        )}
      </div>
    );
  };

  return (
    <div className="space-y-4">
      {/* SRS Stats & Controls Header */}
      <div
        className="p-4 rounded-xl border flex flex-col md:flex-row md:items-center justify-between gap-3"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <span
            className="px-2.5 py-1 rounded-lg font-bold border"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
          >
            Total: {deckStats.totalCount}
          </span>
          <span
            className="px-2.5 py-1 rounded-lg font-bold"
            style={{ backgroundColor: colors.redLight, color: colors.red }}
          >
            Due: {deckStats.dueCount}
          </span>
          <span
            className="px-2.5 py-1 rounded-lg font-bold"
            style={{ backgroundColor: colors.blueLight, color: colors.inkBold }}
          >
            New: {deckStats.newCount}
          </span>
          <span
            className="px-2.5 py-1 rounded-lg font-bold"
            style={{ backgroundColor: colors.amberLight, color: colors.amber }}
          >
            Learning: {deckStats.learningCount}
          </span>
          <span
            className="px-2.5 py-1 rounded-lg font-bold"
            style={{ backgroundColor: colors.greenLight, color: colors.greenDark }}
          >
            Mastered: {deckStats.masteredCount}
          </span>
          {deckStats.retentionPercent > 0 && (
            <span className="text-[0.6875rem] font-bold" style={{ color: colors.teal }}>
              Retention: {deckStats.retentionPercent}%
            </span>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-1.5 text-xs">
          <button
            type="button"
            onClick={() => setCramAll((v) => !v)}
            className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg border font-bold"
            style={
              cramAll
                ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText, borderColor: colors.primaryButtonBg }
                : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
            }
          >
            <Flame className="w-3.5 h-3.5" />
            {cramAll ? 'Cramming All' : 'Due & New Only'}
          </button>

          <button
            type="button"
            onClick={() => setReverseSides((r) => !r)}
            className="px-2.5 py-1.5 rounded-lg border font-bold"
            style={
              reverseSides
                ? { backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }
                : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
            }
          >
            {reverseSides ? 'Reverse Sides' : 'Standard Sides'}
          </button>

          <button
            type="button"
            onClick={() => setShuffleSeed((s) => (s === 0 ? Date.now() : s + 1))}
            className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg border font-bold"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
          >
            <Shuffle className="w-3.5 h-3.5" />
            Shuffle
          </button>

          {confirmReset ? (
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => {
                  onUpdateSrsStates({});
                  setConfirmReset(false);
                }}
                className="px-2.5 py-1.5 rounded-lg font-bold text-white"
                style={{ backgroundColor: colors.red }}
              >
                Confirm Reset
              </button>
              <button
                type="button"
                onClick={() => setConfirmReset(false)}
                className="px-2 py-1.5 rounded-lg border font-semibold"
                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.muted }}
              >
                Cancel
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setConfirmReset(true)}
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg border font-bold"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.red }}
            >
              <RotateCcw className="w-3.5 h-3.5" />
              Reset SRS
            </button>
          )}
        </div>
      </div>

      {/* Main Flashcard Stage */}
      {!activeCard ? (
        <div
          className="rounded-2xl border p-8 text-center space-y-4"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <CheckCircle2 className="w-12 h-12 mx-auto" style={{ color: colors.green }} />
          <div>
            <h4 className="text-base font-black" style={{ color: colors.inkBold }}>
              {reviewedInSession > 0
                ? `Session Complete! Reviewed ${reviewedInSession} card(s).`
                : 'No cards due right now!'}
            </h4>
            <p className="text-xs mt-1" style={{ color: colors.muted }}>
              All due and new cards in this deck are caught up. Enable Cram All to practice mastered cards early.
            </p>
          </div>
          <div className="flex flex-wrap justify-center gap-2">
            <button
              type="button"
              onClick={() => setCramAll(true)}
              className="px-4 py-2 rounded-lg text-xs font-bold"
              style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
            >
              Practice All Cards (Cram Mode)
            </button>
            <button
              type="button"
              onClick={() => rebuildQueue(cramAll, shuffleSeed)}
              className="px-4 py-2 rounded-lg border text-xs font-bold"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
            >
              Reload Queue
            </button>
          </div>
        </div>
      ) : (
        <div
          className="rounded-2xl border p-5 sm:p-7 space-y-5 shadow-sm"
          style={{ backgroundColor: colors.surface, borderColor: colors.border }}
        >
          {/* Progress + Bookmark Bar */}
          <div className="flex items-center justify-between text-xs" style={{ color: colors.muted }}>
            <span className="font-bold">
              Card {Math.min(currentIdx + 1, queue.length)} of {queue.length}
              {reviewedInSession > 0 ? ` • Reviewed: ${reviewedInSession}` : ''}
            </span>
            <div className="flex items-center gap-2">
              <span className="text-[0.6875rem] font-mono">
                Status: {SpacedRepetitionEngine.statusOf(activeSrs)}
              </span>
              <button
                type="button"
                onClick={() => onToggleBookmark(activeCard.blockIndex)}
                className="p-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                title="Bookmark Card"
              >
                <Star
                  className="w-4 h-4"
                  style={{
                    color: bookmarkedSet.has(activeCard.blockIndex) ? colors.amber : colors.muted,
                    fill: bookmarkedSet.has(activeCard.blockIndex) ? colors.amber : 'none',
                  }}
                />
              </button>
            </div>
          </div>

          <div className="w-full h-1.5 rounded-full overflow-hidden" style={{ backgroundColor: colors.surfaceVariant }}>
            <div
              className="h-full transition-all"
              style={{
                width: `${Math.round(((currentIdx + 1) / Math.max(1, queue.length)) * 100)}%`,
                backgroundColor: colors.teal,
              }}
            />
          </div>

          {/* Front Content */}
          <div
            className="min-h-[140px] flex items-center justify-center p-4 rounded-xl border"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.borderLight }}
          >
            {renderCardFront(activeCard)}
          </div>

          {/* Reveal / Back Content */}
          {!isFlipped ? (
            <div className="flex flex-col items-center gap-2 pt-1">
              <button
                type="button"
                onClick={() => setIsFlipped(true)}
                className="flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-black shadow transition"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                <Eye className="w-4 h-4" />
                Reveal Answer (Space)
              </button>
            </div>
          ) : (
            <div className="space-y-4">
              <div
                className="p-4 sm:p-5 rounded-xl border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.teal }}
              >
                {renderCardBack(activeCard)}
              </div>

              {/* 4 SM-2 Rating Buttons */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {(
                  [
                    { rating: 'AGAIN' as SrsRating, label: 'Again (1)', bg: colors.redLight, fg: colors.red, bd: colors.red },
                    { rating: 'HARD' as SrsRating, label: 'Hard (2)', bg: colors.amberLight, fg: colors.amber, bd: colors.amber },
                    { rating: 'GOOD' as SrsRating, label: 'Good (3)', bg: colors.tealLight, fg: colors.tealDark, bd: colors.teal },
                    { rating: 'EASY' as SrsRating, label: 'Easy (4)', bg: colors.greenLight, fg: colors.greenDark, bd: colors.green },
                  ]
                ).map((btn) => (
                  <button
                    key={btn.rating}
                    type="button"
                    onClick={() => handleRate(btn.rating)}
                    className="p-3 rounded-xl border text-center transition hover:opacity-90"
                    style={{ backgroundColor: btn.bg, borderColor: btn.bd, color: btn.fg }}
                  >
                    <div className="text-xs font-black">{btn.label}</div>
                    <div className="text-[0.6875rem] font-bold opacity-85 mt-0.5">
                      Next: {intervalPreviews[btn.rating]}
                    </div>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default FlashcardDeckView;
