import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  CheckCircle2,
  Eye,
  Flame,
  RotateCcw,
  Shuffle,
  Star,
  Volume2,
} from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';
import {
  FlashcardSrsState,
  SrsRating,
  VocabularyDeck,
  VocabularyEntry,
  VocabularyRecallDirection,
} from '../types/phase4';
import { resolveSpeechLocale, speakText } from '../utils/browserSpeech';
import { SpacedRepetitionEngine } from '../utils/spacedRepetitionEngine';

export interface VocabQueueEntry {
  deckId: string;
  deckTitle: string;
  entry: VocabularyEntry;
}

interface VocabularyRecallViewProps {
  title: string;
  items: VocabQueueEntry[];
  srsByDeck: Record<string, Record<string, FlashcardSrsState>>;
  onRateEntry: (deckId: string, entryId: string, rating: SrsRating) => void;
  onToggleBookmark: (deckId: string, entryId: string) => void;
  onResetSrs?: (deckIds: string[]) => void;
}

export const VocabularyRecallView: React.FC<VocabularyRecallViewProps> = ({
  title,
  items,
  srsByDeck,
  onRateEntry,
  onToggleBookmark,
  onResetSrs,
}) => {
  const { colors } = useTheme();
  const [direction, setDirection] = useState<VocabularyRecallDirection>('WORD_TO_MEANING');
  const [cramAll, setCramAll] = useState<boolean>(false);
  const [shuffleSeed, setShuffleSeed] = useState<number>(0);
  const [isFlipped, setIsFlipped] = useState<boolean>(false);
  const [queue, setQueue] = useState<VocabQueueEntry[]>([]);
  const [currentIdx, setCurrentIdx] = useState<number>(0);
  const [reviewedCount, setReviewedCount] = useState<number>(0);
  const [confirmReset, setConfirmReset] = useState<boolean>(false);

  const flatSrsMap = useMemo(() => {
    const out: Record<string, FlashcardSrsState | undefined> = {};
    for (const item of items) {
      out[`${item.deckId}::${item.entry.id}`] = srsByDeck[item.deckId]?.[item.entry.id];
    }
    return out;
  }, [items, srsByDeck]);

  const deckStats = useMemo(
    () =>
      SpacedRepetitionEngine.computeDeckStats(
        items,
        (it) => `${it.deckId}::${it.entry.id}`,
        flatSrsMap
      ),
    [items, flatSrsMap]
  );

  const rebuildQueue = useCallback(
    (useCram: boolean, seed: number) => {
      const built = SpacedRepetitionEngine.buildStudyQueue(
        items,
        (it) => `${it.deckId}::${it.entry.id}`,
        flatSrsMap,
        { cramAll: useCram, prioritizeDue: true, shuffleSeed: seed }
      );
      setQueue(built);
      setCurrentIdx(0);
      setIsFlipped(false);
    },
    [items, flatSrsMap]
  );

  useEffect(() => {
    rebuildQueue(cramAll, shuffleSeed);
    setReviewedCount(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [title, cramAll, shuffleSeed, items.length]);

  const activeItem = queue[currentIdx] || null;
  const activeSrs = activeItem
    ? srsByDeck[activeItem.deckId]?.[activeItem.entry.id]
    : undefined;

  const intervalPreviews = useMemo(
    () => SpacedRepetitionEngine.previewNextIntervalLabels(activeSrs),
    [activeSrs]
  );

  const handleRate = useCallback(
    (rating: SrsRating) => {
      if (!activeItem) return;
      onRateEntry(activeItem.deckId, activeItem.entry.id, rating);
      setReviewedCount((c) => c + 1);
      if (rating === 'AGAIN') {
        setQueue((q) => SpacedRepetitionEngine.reinsertCardSoon(q, currentIdx, activeItem));
      }
      setCurrentIdx((idx) => idx + 1);
      setIsFlipped(false);
    },
    [activeItem, onRateEntry, currentIdx]
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
      if (!activeItem) return;

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
  }, [activeItem, isFlipped, handleRate]);

  if (items.length === 0) {
    return (
      <div
        className="p-8 rounded-xl border text-center space-y-2"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <p className="text-sm font-bold" style={{ color: colors.inkBold }}>
          No vocabulary entries available in this queue.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Stats & Direction Bar */}
      <div
        className="p-4 rounded-xl border flex flex-col md:flex-row md:items-center justify-between gap-3"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <span className="font-black" style={{ color: colors.inkBold }}>
            {title}
          </span>
          <span className="px-2 py-0.5 rounded font-bold" style={{ backgroundColor: colors.redLight, color: colors.red }}>
            Due: {deckStats.dueCount}
          </span>
          <span className="px-2 py-0.5 rounded font-bold" style={{ backgroundColor: colors.blueLight, color: colors.inkBold }}>
            New: {deckStats.newCount}
          </span>
          <span className="px-2 py-0.5 rounded font-bold" style={{ backgroundColor: colors.greenLight, color: colors.greenDark }}>
            Mastered: {deckStats.masteredCount}/{deckStats.totalCount}
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-1.5 text-xs">
          <button
            type="button"
            onClick={() =>
              setDirection((d) => (d === 'WORD_TO_MEANING' ? 'MEANING_TO_WORD' : 'WORD_TO_MEANING'))
            }
            className="px-2.5 py-1.5 rounded-lg border font-bold"
            style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
          >
            {direction === 'WORD_TO_MEANING' ? 'Word → Meaning' : 'Meaning → Word'}
          </button>

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
            {cramAll ? 'Cramming All' : 'Due & New'}
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

          {onResetSrs && (
            confirmReset ? (
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => {
                    onResetSrs(Array.from(new Set(items.map((i) => i.deckId))));
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
                  className="px-2 py-1.5 rounded-lg border"
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
            )
          )}
        </div>
      </div>

      {/* Active Card Stage */}
      {!activeItem ? (
        <div
          className="rounded-2xl border p-8 text-center space-y-4"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <CheckCircle2 className="w-12 h-12 mx-auto" style={{ color: colors.green }} />
          <div>
            <h4 className="text-base font-black" style={{ color: colors.inkBold }}>
              {reviewedCount > 0 ? `Completed ${reviewedCount} review(s)!` : 'All words caught up!'}
            </h4>
            <p className="text-xs mt-1" style={{ color: colors.muted }}>
              Switch to Cram Mode to drill mastered vocabulary before scheduled intervals expire.
            </p>
          </div>
          <div className="flex justify-center gap-2">
            <button
              type="button"
              onClick={() => setCramAll(true)}
              className="px-4 py-2 rounded-lg text-xs font-bold"
              style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
            >
              Practice All Words
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
          <div className="flex items-center justify-between text-xs" style={{ color: colors.muted }}>
            <span className="font-bold">
              Word {Math.min(currentIdx + 1, queue.length)} of {queue.length} • {activeItem.deckTitle}
            </span>
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() =>
                  speakText(activeItem.entry.word, {
                    lang: resolveSpeechLocale(
                      activeItem.entry.language,
                      activeItem.entry.languageVariant
                    ),
                  })
                }
                className="flex items-center gap-1 px-2.5 py-1 rounded-lg border font-bold"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.tealDark }}
              >
                <Volume2 className="w-3.5 h-3.5" />
                Speak
              </button>
              <button
                type="button"
                onClick={() => onToggleBookmark(activeItem.deckId, activeItem.entry.id)}
                className="p-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                title="Star Word"
              >
                <Star
                  className="w-4 h-4"
                  style={{
                    color: activeItem.entry.isBookmarked ? colors.amber : colors.muted,
                    fill: activeItem.entry.isBookmarked ? colors.amber : 'none',
                  }}
                />
              </button>
            </div>
          </div>

          {/* Front Prompt */}
          <div
            className="min-h-[140px] flex flex-col items-center justify-center p-5 rounded-xl border text-center space-y-2"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.borderLight }}
          >
            <div className="flex items-center gap-1.5 text-[10px] font-bold">
              <span
                className="px-2 py-0.5 rounded border"
                style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
              >
                {activeItem.entry.customLevelLabel || activeItem.entry.levelCode}
              </span>
              {activeItem.entry.partOfSpeech && (
                <span
                  className="px-2 py-0.5 rounded border"
                  style={{ backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }}
                >
                  {activeItem.entry.partOfSpeech}
                </span>
              )}
            </div>

            {direction === 'WORD_TO_MEANING' ? (
              <>
                <div className="text-2xl sm:text-3xl font-black tracking-tight" style={{ color: colors.inkBold }}>
                  {activeItem.entry.word}
                </div>
                {activeItem.entry.pronunciation && (
                  <div className="font-mono text-xs" style={{ color: colors.muted }}>
                    {activeItem.entry.pronunciation}
                  </div>
                )}
              </>
            ) : (
              <div className="text-base sm:text-lg font-bold max-w-xl" style={{ color: colors.inkBold }}>
                {activeItem.entry.meaning}
              </div>
            )}
          </div>

          {/* Back Detail & Ratings */}
          {!isFlipped ? (
            <div className="flex justify-center">
              <button
                type="button"
                onClick={() => setIsFlipped(true)}
                className="flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-black shadow"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                <Eye className="w-4 h-4" />
                Reveal Definition &amp; Usage (Space)
              </button>
            </div>
          ) : (
            <div className="space-y-4">
              <div
                className="p-4 rounded-xl border space-y-2.5 text-xs"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.teal }}
              >
                {direction === 'MEANING_TO_WORD' && (
                  <div className="text-lg font-black" style={{ color: colors.tealDark }}>
                    {activeItem.entry.word}{' '}
                    {activeItem.entry.pronunciation && (
                      <span className="font-mono text-xs font-normal" style={{ color: colors.muted }}>
                        {activeItem.entry.pronunciation}
                      </span>
                    )}
                  </div>
                )}

                {direction === 'WORD_TO_MEANING' && (
                  <div className="text-sm font-bold" style={{ color: colors.inkBold }}>
                    {activeItem.entry.meaning}
                  </div>
                )}

                {activeItem.entry.translation && (
                  <div className="font-semibold" style={{ color: colors.tealDark }}>
                    Translation: {activeItem.entry.translation}
                  </div>
                )}

                {activeItem.entry.examples.length > 0 && (
                  <div className="space-y-1 pt-1">
                    {activeItem.entry.examples.map((ex, i) => (
                      <div key={i} style={{ color: colors.ink }}>
                        <span className="font-bold mr-1" style={{ color: colors.muted }}>
                          [{ex.context}]
                        </span>
                        “{ex.sentence}”
                      </div>
                    ))}
                  </div>
                )}

                {(activeItem.entry.synonyms.length > 0 || activeItem.entry.collocations.length > 0) && (
                  <div className="flex flex-wrap gap-3 pt-1" style={{ color: colors.ink }}>
                    {activeItem.entry.synonyms.length > 0 && (
                      <span>
                        <strong>Synonyms:</strong> {activeItem.entry.synonyms.join(', ')}
                      </span>
                    )}
                    {activeItem.entry.collocations.length > 0 && (
                      <span>
                        <strong>Collocations:</strong> {activeItem.entry.collocations.join(', ')}
                      </span>
                    )}
                  </div>
                )}

                {activeItem.entry.usageNote && (
                  <div
                    className="p-2.5 rounded-lg border"
                    style={{ backgroundColor: colors.amberLight, borderColor: colors.amber, color: colors.inkBold }}
                  >
                    <strong>Usage Note:</strong> {activeItem.entry.usageNote}
                  </div>
                )}
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {(
                  [
                    { r: 'AGAIN' as SrsRating, lbl: 'Again (1)', bg: colors.redLight, fg: colors.red, bd: colors.red },
                    { r: 'HARD' as SrsRating, lbl: 'Hard (2)', bg: colors.amberLight, fg: colors.amber, bd: colors.amber },
                    { r: 'GOOD' as SrsRating, lbl: 'Good (3)', bg: colors.tealLight, fg: colors.tealDark, bd: colors.teal },
                    { r: 'EASY' as SrsRating, lbl: 'Easy (4)', bg: colors.greenLight, fg: colors.greenDark, bd: colors.green },
                  ]
                ).map((b) => (
                  <button
                    key={b.r}
                    type="button"
                    onClick={() => handleRate(b.r)}
                    className="p-3 rounded-xl border text-center"
                    style={{ backgroundColor: b.bg, borderColor: b.bd, color: b.fg }}
                  >
                    <div className="text-xs font-black">{b.lbl}</div>
                    <div className="text-[11px] font-bold opacity-85 mt-0.5">
                      Next: {intervalPreviews[b.r]}
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

export function flattenDecksToQueueItems(decks: VocabularyDeck[]): VocabQueueEntry[] {
  const out: VocabQueueEntry[] = [];
  for (const d of decks) {
    for (const entry of d.entries) {
      out.push({ deckId: d.id, deckTitle: d.title, entry });
    }
  }
  return out;
}

export default VocabularyRecallView;
