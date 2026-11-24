import React, { useMemo, useState } from 'react';
import {
  Check,
  Copy,
  Inbox,
  Plus,
  Trash2,
  X,
} from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';
import {
  HeadwordFilterResult,
  HeadwordQueueItem,
  VocabularyDeck,
  VocabularyLevelCode,
} from '../types/phase4';
import { PROMPT_LEVEL_CODES, VocabularyEngine, VocabularyVariants } from '../utils/vocabularyEngine';

interface HeadwordInboxModalProps {
  isOpen: boolean;
  inbox: HeadwordQueueItem[];
  dismissedKeys: string[];
  decks: VocabularyDeck[];
  defaultEnglishVariant?: string;
  onSaveInbox: (items: HeadwordQueueItem[]) => void;
  onDismissKey: (compositeKey: string, itemId: string) => void;
  onClose: () => void;
}

export const HeadwordInboxModal: React.FC<HeadwordInboxModalProps> = ({
  isOpen,
  inbox,
  dismissedKeys,
  decks,
  defaultEnglishVariant = 'UK',
  onSaveInbox,
  onDismissKey,
  onClose,
}) => {
  const { colors } = useTheme();
  const [rawPaste, setRawPaste] = useState<string>('');
  const [defaultLang, setDefaultLang] = useState<string>('English');
  const [defaultLevel, setDefaultLevel] = useState<VocabularyLevelCode>('B2');
  const [filterLang, setFilterLang] = useState<string>('ALL');
  const [lastResult, setLastResult] = useState<HeadwordFilterResult | null>(null);
  const [copiedBatch, setCopiedBatch] = useState<boolean>(false);

  const visibleItems = useMemo(
    () =>
      filterLang === 'ALL'
        ? inbox
        : inbox.filter((i) => i.language.toLowerCase() === filterLang.toLowerCase()),
    [inbox, filterLang]
  );

  const languagesInQueue = useMemo(
    () => Array.from(new Set(inbox.map((i) => i.language))),
    [inbox]
  );

  if (!isOpen) return null;

  const handleQueueHeadwords = () => {
    const candidates = VocabularyEngine.parseHeadwordIndexInput(
      rawPaste,
      defaultLang,
      defaultLevel,
      'Exam Curation'
    );
    const res = VocabularyEngine.filterAndQueueHeadwords(
      candidates,
      decks,
      inbox,
      dismissedKeys
    );
    setLastResult(res);
    if (res.addedItems.length > 0) {
      onSaveInbox([...res.addedItems, ...inbox]);
      setRawPaste('');
    }
  };

  const handleCopyStage2Prompt = () => {
    const batch = visibleItems.slice(0, 15);
    if (batch.length === 0) return;
    const lang = batch[0].language || 'English';
    const directive = VocabularyVariants.promptDirective(lang, defaultEnglishVariant);
    const wordList = batch.map((b) => `- ${b.word} (${b.levelCode})`).join('\n');
    const prompt = [
      `Act as a master lexicographer for ${lang}.`,
      `Generate complete strict JSON vocabulary entries for ONLY these queued headwords:`,
      wordList,
      ...(directive ? ['', `Regional Convention: ${directive}`] : []),
      '',
      `Output strict valid JSON array: [{"word":"...","pronunciation":"...","partOfSpeech":"...","level":"...","meaning":"...","examples":[{"context":"Editorial","sentence":"..."}],"synonyms":[...],"collocations":[...],"usageNote":"..."}]`,
    ].join('\n');
    navigator.clipboard?.writeText(prompt);
    setCopiedBatch(true);
    setTimeout(() => setCopiedBatch(false), 2200);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 overflow-y-auto">
      <div
        className="w-full max-w-3xl rounded-2xl border shadow-2xl overflow-hidden flex flex-col max-h-[90vh]"
        style={{ backgroundColor: colors.surface, borderColor: colors.border }}
      >
        <div
          className="px-5 py-4 border-b flex items-center justify-between"
          style={{ backgroundColor: colors.navyBanner, borderColor: colors.border }}
        >
          <div className="flex items-center gap-2.5">
            <Inbox className="w-5 h-5 text-emerald-300" />
            <div>
              <h3 className="text-base font-black" style={{ color: colors.navyBannerText }}>
                Stage-1 Headword Inbox ({inbox.length} queued)
              </h3>
              <p className="text-[11px] opacity-80" style={{ color: colors.navyBannerText }}>
                Curate headword lists, auto-deduplicate against saved decks, and generate Stage-2 detail batches
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-5 overflow-y-auto space-y-4 text-xs flex-1">
          <div
            className="p-3.5 rounded-xl border space-y-3"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
          >
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block font-bold mb-1" style={{ color: colors.ink }}>
                  Default Language
                </label>
                <input
                  type="text"
                  value={defaultLang}
                  onChange={(e) => setDefaultLang(e.target.value)}
                  className="w-full px-2.5 py-1.5 rounded-lg border"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>
              <div>
                <label className="block font-bold mb-1" style={{ color: colors.ink }}>
                  Default Level
                </label>
                <select
                  value={defaultLevel}
                  onChange={(e) => setDefaultLevel(e.target.value as VocabularyLevelCode)}
                  className="w-full px-2.5 py-1.5 rounded-lg border"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                >
                  {PROMPT_LEVEL_CODES.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <textarea
              value={rawPaste}
              onChange={(e) => setRawPaste(e.target.value)}
              rows={4}
              placeholder="Paste Stage-1 Headword JSON array or word-per-line index..."
              className="w-full p-2.5 rounded-lg border font-mono text-xs"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            />

            <div className="flex flex-wrap items-center justify-between gap-2">
              {lastResult ? (
                <span className="text-[11px] font-semibold" style={{ color: colors.tealDark }}>
                  Added {lastResult.addedItems.length} • Already in Decks: {lastResult.skippedAlreadyInDeckCount} • In Queue: {lastResult.skippedAlreadyInQueueCount} • Dismissed: {lastResult.skippedDismissedCount}
                </span>
              ) : (
                <span className="text-[11px]" style={{ color: colors.muted }}>
                  Duplicates against existing decks are filtered automatically.
                </span>
              )}

              <button
                type="button"
                disabled={!rawPaste.trim()}
                onClick={handleQueueHeadwords}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg font-bold disabled:opacity-40"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                <Plus className="w-3.5 h-3.5" />
                Queue Unique Headwords
              </button>
            </div>
          </div>

          {/* Pending Queue Header */}
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex flex-wrap items-center gap-1.5">
              <button
                type="button"
                onClick={() => setFilterLang('ALL')}
                className="px-2.5 py-1 rounded-lg border font-bold"
                style={
                  filterLang === 'ALL'
                    ? { backgroundColor: colors.teal, color: '#fff', borderColor: colors.teal }
                    : { backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }
                }
              >
                All ({inbox.length})
              </button>
              {languagesInQueue.map((lng) => (
                <button
                  key={lng}
                  type="button"
                  onClick={() => setFilterLang(lng)}
                  className="px-2.5 py-1 rounded-lg border font-bold"
                  style={
                    filterLang === lng
                      ? { backgroundColor: colors.teal, color: '#fff', borderColor: colors.teal }
                      : { backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }
                  }
                >
                  {lng}
                </button>
              ))}
            </div>

            {visibleItems.length > 0 && (
              <button
                type="button"
                onClick={handleCopyStage2Prompt}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border font-bold"
                style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
              >
                {copiedBatch ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                {copiedBatch
                  ? 'Copied Stage-2 Batch!'
                  : `Copy Stage-2 AI Prompt (Top ${Math.min(15, visibleItems.length)})`}
              </button>
            )}
          </div>

          {/* List */}
          {visibleItems.length === 0 ? (
            <div
              className="p-8 rounded-xl border text-center"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.muted }}
            >
              Headword inbox is empty. Paste curated headwords above or generate a Stage-1 Index from Prompt Studio.
            </div>
          ) : (
            <div className="space-y-1.5">
              {visibleItems.map((item) => (
                <div
                  key={item.id}
                  className="p-2.5 rounded-lg border flex items-center justify-between gap-2"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                >
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-sm" style={{ color: colors.inkBold }}>
                        {item.word}
                      </span>
                      <span
                        className="px-1.5 py-0.5 rounded text-[10px] font-bold border"
                        style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.border }}
                      >
                        {item.language} • {item.levelCode}
                      </span>
                      {item.partOfSpeech && (
                        <span className="text-[11px]" style={{ color: colors.muted }}>
                          ({item.partOfSpeech})
                        </span>
                      )}
                    </div>
                    {item.utilityReason && (
                      <p className="text-[11px] truncate" style={{ color: colors.muted }}>
                        {item.utilityReason}
                      </p>
                    )}
                  </div>

                  <button
                    type="button"
                    onClick={() =>
                      onDismissKey(
                        `${item.language.trim().toLowerCase()}::${VocabularyEngine.normalizeWordKey(item.word)}`,
                        item.id
                      )
                    }
                    className="p-1.5 rounded-lg border"
                    style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.red }}
                    title="Dismiss from Inbox"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default HeadwordInboxModal;
