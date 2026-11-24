import React, { useEffect, useMemo, useState } from 'react';
import {
  ArrowLeft,
  BookOpen,
  Calendar,
  Download,
  Flame,
  Folder,
  Globe,
  Inbox,
  Layers,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Sparkles,
  Star,
  Trash2,
  Volume2,
} from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import {
  FlashcardSrsState,
  HeadwordQueueItem,
  LanguageWordOfTheDayState,
  SrsRating,
  VocabularyDeck,
  VocabularyEntry,
  VocabularyLevelCode,
} from '../../types/phase4';
import { resolveSpeechLocale, speakText } from '../../utils/browserSpeech';
import { SpacedRepetitionEngine } from '../../utils/spacedRepetitionEngine';
import {
  FOLDER_LEVEL_CODES,
  getLevelDescriptor,
  VocabularyEngine,
  WOTD_MIN_WORDS_THRESHOLD,
} from '../../utils/vocabularyEngine';
import {
  flattenDecksToQueueItems,
  VocabQueueEntry,
  VocabularyRecallView,
} from '../VocabularyRecallView';

interface VocabularyHubTabProps {
  decks: VocabularyDeck[];
  srsByDeck: Record<string, Record<string, FlashcardSrsState>>;
  englishVariant: string;
  headwordInbox: HeadwordQueueItem[];
  wotdStates: Record<string, LanguageWordOfTheDayState>;
  onChangeEnglishVariant: (variant: string) => void;
  onUpdateDecks: (decks: VocabularyDeck[]) => void;
  onUpdateSrs: (nextSrs: Record<string, Record<string, FlashcardSrsState>>) => void;
  onSaveWotdStates: (next: Record<string, LanguageWordOfTheDayState>) => void;
  onOpenImportModal: (defaultLanguage?: string) => void;
  onOpenHeadwordInbox: () => void;
  onOpenAuditModal: (deck: VocabularyDeck) => void;
}

type HubView =
  | { kind: 'LANGUAGES' }
  | { kind: 'LEVELS'; language: string }
  | { kind: 'DECK_DETAIL'; language: string; deckId: string }
  | { kind: 'VIRTUAL_COLLECTION'; title: string; subtitle: string; decks: VocabularyDeck[]; backLanguage?: string };

export const VocabularyHubTab: React.FC<VocabularyHubTabProps> = ({
  decks,
  srsByDeck,
  englishVariant,
  headwordInbox,
  wotdStates,
  onChangeEnglishVariant,
  onUpdateDecks,
  onUpdateSrs,
  onSaveWotdStates,
  onOpenImportModal,
  onOpenHeadwordInbox,
  onOpenAuditModal,
}) => {
  const { colors } = useTheme();
  const [view, setView] = useState<HubView>({ kind: 'LANGUAGES' });
  const [deckSubTab, setDeckSubTab] = useState<'WORDS' | 'RECALL'>('WORDS');
  const [globalSearch, setGlobalSearch] = useState<string>('');
  const [deckSearch, setDeckSearch] = useState<string>('');
  const [starredOnly, setStarredOnly] = useState<boolean>(false);
  const [wotdHistoryOpen, setWotdHistoryOpen] = useState<boolean>(false);
  const [pendingDeleteWordId, setPendingDeleteWordId] = useState<string | null>(null);
  const [pendingDeleteDeckId, setPendingDeleteDeckId] = useState<string | null>(null);

  const languageStats = useMemo(
    () => VocabularyEngine.computeLanguageStats(decks, srsByDeck),
    [decks, srsByDeck]
  );

  const allQueueItems = useMemo(() => flattenDecksToQueueItems(decks), [decks]);

  const totalDueAcrossAll = useMemo(
    () => languageStats.reduce((sum, l) => sum + l.dueCount, 0),
    [languageStats]
  );

  // Global Search hits across all decks
  const globalSearchHits = useMemo(() => {
    const q = globalSearch.trim().toLowerCase();
    if (!q) return [];
    const matches: VocabQueueEntry[] = [];
    for (const d of decks) {
      for (const e of d.entries) {
        if (
          e.word.toLowerCase().includes(q) ||
          e.meaning.toLowerCase().includes(q) ||
          e.translation.toLowerCase().includes(q)
        ) {
          matches.push({ deckId: d.id, deckTitle: d.title, entry: e });
        }
      }
    }
    return matches.slice(0, 60);
  }, [decks, globalSearch]);

  // Active WOTD evaluation whenever inside LEVELS view
  const activeLevelLanguage = view.kind === 'LEVELS' ? view.language : '';
  const wotdResolution = useMemo(() => {
    if (!activeLevelLanguage) return null;
    return VocabularyEngine.resolveWordOfTheDay(decks, activeLevelLanguage, wotdStates, false);
  }, [decks, activeLevelLanguage, wotdStates]);

  // Persist initial daily UTC state update when entering LEVELS
  useEffect(() => {
    if (wotdResolution?.updatedState && activeLevelLanguage) {
      const key = activeLevelLanguage.trim().toLowerCase();
      onSaveWotdStates({
        ...wotdStates,
        [key]: wotdResolution.updatedState,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeLevelLanguage, wotdResolution?.updatedState?.activeWordKey, wotdResolution?.updatedState?.activeUtcDateKey]);

  const handleCycleWotd = (language: string) => {
    const res = VocabularyEngine.resolveWordOfTheDay(decks, language, wotdStates, true);
    if (res.updatedState) {
      onSaveWotdStates({
        ...wotdStates,
        [language.trim().toLowerCase()]: res.updatedState,
      });
    }
  };

  const handleRateEntry = (deckId: string, entryId: string, rating: SrsRating) => {
    const deckSrs = srsByDeck[deckId] || {};
    const prev = deckSrs[entryId];
    const nextState = SpacedRepetitionEngine.computeNextState(prev, 0, rating);
    onUpdateSrs({
      ...srsByDeck,
      [deckId]: {
        ...deckSrs,
        [entryId]: nextState,
      },
    });
  };

  const handleToggleWordBookmark = (deckId: string, entryId: string) => {
    const nextDecks = decks.map((d) =>
      d.id === deckId
        ? {
            ...d,
            entries: d.entries.map((e) =>
              e.id === entryId ? { ...e, isBookmarked: !e.isBookmarked, updatedAt: Date.now() } : e
            ),
          }
        : d
    );
    onUpdateDecks(nextDecks);
  };

  const handleDeleteWord = (deckId: string, entryId: string) => {
    const nextDecks = decks.map((d) =>
      d.id === deckId
        ? { ...d, entries: d.entries.filter((e) => e.id !== entryId), updatedAt: Date.now() }
        : d
    );
    onUpdateDecks(nextDecks);
    setPendingDeleteWordId(null);
  };

  const handleDeleteDeck = (deckId: string) => {
    const remaining = decks.filter((d) => d.id !== deckId);
    onUpdateDecks(remaining);
    setPendingDeleteDeckId(null);
    if (view.kind === 'DECK_DETAIL' && view.deckId === deckId) {
      setView({ kind: 'LEVELS', language: view.language });
    }
  };

  const handleResetSrsForDecks = (deckIds: string[]) => {
    const next = { ...srsByDeck };
    for (const id of deckIds) {
      delete next[id];
    }
    onUpdateSrs(next);
  };

  const handleExportDeckJson = (deck: VocabularyDeck) => {
    const blob = new Blob([JSON.stringify(deck, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${deck.title.replace(/[^a-z0-9]+/gi, '_').toLowerCase()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const renderWordCard = (deckId: string, entry: VocabularyEntry, showDeckLabel?: string) => (
    <div
      key={`${deckId}-${entry.id}`}
      className="p-4 rounded-xl border space-y-2"
      style={{
        backgroundColor: entry.isBookmarked ? colors.amberLight : colors.surfaceVariant,
        borderColor: entry.isBookmarked ? colors.amber : colors.border,
      }}
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-base font-black" style={{ color: colors.inkBold }}>
              {entry.word}
            </span>
            {entry.pronunciation && (
              <span className="font-mono text-xs" style={{ color: colors.muted }}>
                {entry.pronunciation}
              </span>
            )}
            <span
              className="px-2 py-0.5 rounded text-[0.625rem] font-bold border"
              style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
            >
              {entry.customLevelLabel || entry.levelCode}
            </span>
            {entry.partOfSpeech && (
              <span
                className="px-2 py-0.5 rounded text-[0.625rem] font-semibold border"
                style={{ backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }}
              >
                {entry.partOfSpeech}
              </span>
            )}
            {showDeckLabel && (
              <span className="text-[0.625rem] font-semibold" style={{ color: colors.muted }}>
                • {showDeckLabel}
              </span>
            )}
          </div>
          <p className="text-xs font-bold mt-1" style={{ color: colors.inkBold }}>
            {entry.meaning}
          </p>
          {entry.translation && (
            <p className="text-xs font-semibold mt-0.5" style={{ color: colors.tealDark }}>
              Translation: {entry.translation}
            </p>
          )}
        </div>

        <div className="flex items-center gap-1 flex-shrink-0">
          <button
            type="button"
            onClick={() =>
              speakText(entry.word, {
                lang: resolveSpeechLocale(entry.language, entry.languageVariant),
              })
            }
            className="p-1.5 rounded-lg border"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.tealDark }}
            title="Pronounce word"
          >
            <Volume2 className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={() => handleToggleWordBookmark(deckId, entry.id)}
            className="p-1.5 rounded-lg border"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
            title="Star word"
          >
            <Star
              className="w-3.5 h-3.5"
              style={{
                color: entry.isBookmarked ? colors.amber : colors.muted,
                fill: entry.isBookmarked ? colors.amber : 'none',
              }}
            />
          </button>
          {pendingDeleteWordId === `${deckId}-${entry.id}` ? (
            <button
              type="button"
              onClick={() => handleDeleteWord(deckId, entry.id)}
              className="px-2 py-1 rounded-lg font-bold text-white text-[0.625rem]"
              style={{ backgroundColor: colors.red }}
            >
              Delete
            </button>
          ) : (
            <button
              type="button"
              onClick={() => setPendingDeleteWordId(`${deckId}-${entry.id}`)}
              className="p-1.5 rounded-lg border"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.red }}
              title="Remove word"
            >
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      {entry.examples.length > 0 && (
        <div className="text-xs space-y-0.5 pl-2 border-l-2" style={{ borderColor: colors.teal, color: colors.ink }}>
          {entry.examples.map((ex, i) => (
            <div key={i}>
              <span className="font-bold mr-1" style={{ color: colors.muted }}>
                [{ex.context}]
              </span>
              {ex.sentence}
            </div>
          ))}
        </div>
      )}

      {(entry.synonyms.length > 0 || entry.collocations.length > 0 || entry.usageNote) && (
        <div className="flex flex-wrap gap-3 text-[0.6875rem]" style={{ color: colors.muted }}>
          {entry.synonyms.length > 0 && (
            <span>
              <strong style={{ color: colors.ink }}>Synonyms:</strong> {entry.synonyms.join(', ')}
            </span>
          )}
          {entry.collocations.length > 0 && (
            <span>
              <strong style={{ color: colors.ink }}>Collocations:</strong> {entry.collocations.join(', ')}
            </span>
          )}
          {entry.usageNote && (
            <span>
              <strong style={{ color: colors.ink }}>Note:</strong> {entry.usageNote}
            </span>
          )}
        </div>
      )}
    </div>
  );

  // Selected deck memo
  const selectedDeck = useMemo(
    () => (view.kind === 'DECK_DETAIL' ? decks.find((d) => d.id === view.deckId) || null : null),
    [view, decks]
  );

  const filteredDeckEntries = useMemo(() => {
    if (!selectedDeck) return [];
    const q = deckSearch.trim().toLowerCase();
    return [...selectedDeck.entries]
      .filter((e) => !starredOnly || e.isBookmarked)
      .filter(
        (e) =>
          !q ||
          e.word.toLowerCase().includes(q) ||
          e.meaning.toLowerCase().includes(q) ||
          e.translation.toLowerCase().includes(q)
      )
      .sort((a, b) => a.word.localeCompare(b.word));
  }, [selectedDeck, deckSearch, starredOnly]);

  // --- TIER 3A: VIRTUAL COLLECTION (Mixed Compilation or Cross-Language Due Review) ---
  if (view.kind === 'VIRTUAL_COLLECTION') {
    const queueItems = flattenDecksToQueueItems(view.decks);
    return (
      <div className="space-y-4">
        <div
          className="p-4 rounded-xl border flex flex-wrap items-center justify-between gap-3"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() =>
                setView(view.backLanguage ? { kind: 'LEVELS', language: view.backLanguage } : { kind: 'LANGUAGES' })
              }
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs font-bold"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            >
              <ArrowLeft className="w-4 h-4" />
              Back
            </button>
            <div className="min-w-0">
              <h3 className="text-base font-black es-clamp-2" style={{ color: colors.inkBold }}>
                {view.title}
              </h3>
              <p className="text-xs es-scroll-x" style={{ color: colors.muted }}>
                {view.subtitle}
              </p>
            </div>
          </div>

          <div className="flex rounded-lg border p-0.5 text-xs" style={{ backgroundColor: colors.surface, borderColor: colors.border }}>
            <button
              type="button"
              onClick={() => setDeckSubTab('RECALL')}
              className="px-3 py-1.5 rounded-md font-bold"
              style={
                deckSubTab === 'RECALL'
                  ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                  : { color: colors.ink }
              }
            >
              SM-2 Recall
            </button>
            <button
              type="button"
              onClick={() => setDeckSubTab('WORDS')}
              className="px-3 py-1.5 rounded-md font-bold"
              style={
                deckSubTab === 'WORDS'
                  ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                  : { color: colors.ink }
              }
            >
              Word List ({queueItems.length})
            </button>
          </div>
        </div>

        {deckSubTab === 'RECALL' ? (
          <VocabularyRecallView
            title={view.title}
            items={queueItems}
            srsByDeck={srsByDeck}
            onRateEntry={handleRateEntry}
            onToggleBookmark={handleToggleWordBookmark}
            onResetSrs={handleResetSrsForDecks}
          />
        ) : (
          <div className="space-y-2.5">
            {queueItems.map((it) => renderWordCard(it.deckId, it.entry, it.deckTitle))}
          </div>
        )}
      </div>
    );
  }

  // --- TIER 3B: DECK DETAIL VIEW ---
  if (view.kind === 'DECK_DETAIL' && selectedDeck) {
    const queueItems = flattenDecksToQueueItems([selectedDeck]);
    return (
      <div className="space-y-4">
        <div
          className="p-4 rounded-xl border flex flex-col lg:flex-row lg:items-center justify-between gap-3"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <div className="flex items-start gap-3">
            <button
              type="button"
              onClick={() => setView({ kind: 'LEVELS', language: view.language })}
              className="flex items-center gap-1 px-3 py-2 rounded-lg border text-xs font-bold"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            >
              <ArrowLeft className="w-4 h-4" />
              {view.language}
            </button>
            <div>
              <div className="flex items-center gap-2">
                <span
                  className="px-2 py-0.5 rounded text-[0.625rem] font-black border"
                  style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
                >
                  {getLevelDescriptor(selectedDeck.levelCode, selectedDeck.language, selectedDeck.customLevelLabel).shortBadge}
                </span>
                <span className="text-[0.6875rem]" style={{ color: colors.muted }}>
                  {selectedDeck.domain} • {selectedDeck.entries.length} word(s)
                </span>
              </div>
              <h3 className="text-base sm:text-lg font-black mt-0.5 es-clamp-2" style={{ color: colors.inkBold }}>
                {selectedDeck.title}
              </h3>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-1.5 text-xs">
            <div className="flex rounded-lg border p-0.5" style={{ backgroundColor: colors.surface, borderColor: colors.border }}>
              <button
                type="button"
                onClick={() => setDeckSubTab('WORDS')}
                className="px-3 py-1.5 rounded-md font-bold"
                style={
                  deckSubTab === 'WORDS'
                    ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                    : { color: colors.ink }
                }
              >
                A–Z Word List
              </button>
              <button
                type="button"
                onClick={() => setDeckSubTab('RECALL')}
                className="flex items-center gap-1 px-3 py-1.5 rounded-md font-bold"
                style={
                  deckSubTab === 'RECALL'
                    ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                    : { color: colors.ink }
                }
              >
                <Layers className="w-3.5 h-3.5" />
                SM-2 Recall
              </button>
            </div>

            <button
              type="button"
              onClick={() => onOpenAuditModal(selectedDeck)}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border font-bold"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.tealDark }}
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              Audit
            </button>

            <button
              type="button"
              onClick={() => handleExportDeckJson(selectedDeck)}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border font-bold"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
            >
              <Download className="w-3.5 h-3.5" />
              Export JSON
            </button>
          </div>
        </div>

        {deckSubTab === 'RECALL' ? (
          <VocabularyRecallView
            title={selectedDeck.title}
            items={queueItems}
            srsByDeck={srsByDeck}
            onRateEntry={handleRateEntry}
            onToggleBookmark={handleToggleWordBookmark}
            onResetSrs={handleResetSrsForDecks}
          />
        ) : (
          <div className="space-y-3">
            <div className="flex flex-wrap gap-2 items-center">
              <div className="relative flex-1 min-w-[200px]">
                <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2" style={{ color: colors.muted }} />
                <input
                  type="text"
                  value={deckSearch}
                  onChange={(e) => setDeckSearch(e.target.value)}
                  placeholder="Filter words in this deck..."
                  className="w-full pl-9 pr-3 py-2 rounded-xl border text-xs"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>

              <button
                type="button"
                onClick={() => setStarredOnly((v) => !v)}
                className="flex items-center gap-1 px-3 py-2 rounded-xl border text-xs font-bold"
                style={
                  starredOnly
                    ? { backgroundColor: colors.amberLight, borderColor: colors.amber, color: colors.amber }
                    : { backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }
                }
              >
                <Star className="w-3.5 h-3.5" />
                Starred Only
              </button>
            </div>

            {filteredDeckEntries.length === 0 ? (
              <div
                className="p-8 rounded-xl border text-center text-xs"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.muted }}
              >
                No matching words in this deck.
              </div>
            ) : (
              <div className="space-y-2.5">
                {filteredDeckEntries.map((entry) => renderWordCard(selectedDeck.id, entry))}
              </div>
            )}
          </div>
        )}
      </div>
    );
  }

  // --- TIER 2: PROFICIENCY LEVEL FOLDERS + WORD OF THE DAY ---
  if (view.kind === 'LEVELS') {
    const lang = view.language;
    const langDecks = decks.filter((d) => d.language.trim().toLowerCase() === lang.trim().toLowerCase());
    const levelBuckets = new Map<VocabularyLevelCode, VocabularyDeck[]>();
    for (const code of [...FOLDER_LEVEL_CODES, 'MIXED' as VocabularyLevelCode, 'CUSTOM' as VocabularyLevelCode]) {
      const matching = langDecks.filter((d) => d.levelCode === code);
      if (matching.length > 0 || FOLDER_LEVEL_CODES.includes(code)) {
        levelBuckets.set(code, matching);
      }
    }

    return (
      <div className="space-y-5">
        <div
          className="p-4 rounded-xl border flex flex-wrap items-center justify-between gap-3"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setView({ kind: 'LANGUAGES' })}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border text-xs font-bold"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            >
              <ArrowLeft className="w-4 h-4" />
              All Languages
            </button>
            <div className="min-w-0">
              <h3 className="text-base font-black es-clamp-2" style={{ color: colors.inkBold }}>
                {lang} Proficiency Folders
              </h3>
              <p className="text-xs es-scroll-x" style={{ color: colors.muted }}>
                {langDecks.length} deck(s) • Localized proficiency tracks &amp; daily spotlight
              </p>
            </div>
          </div>

          <div className="flex flex-wrap gap-2">
            {langDecks.length > 0 && (
              <button
                type="button"
                onClick={() => {
                  setDeckSubTab('RECALL');
                  setView({
                    kind: 'VIRTUAL_COLLECTION',
                    title: `${lang} — Mixed Compilation`,
                    subtitle: `All ${langDecks.reduce((s, d) => s + d.entries.length, 0)} words across ${lang} levels`,
                    decks: langDecks,
                    backLanguage: lang,
                  });
                }}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs font-bold"
                style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
              >
                <Layers className="w-3.5 h-3.5" />
                Practice Mixed Compilation
              </button>
            )}
            <button
              type="button"
              onClick={() => onOpenImportModal(lang)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold"
              style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
            >
              <Plus className="w-3.5 h-3.5" />
              Import {lang} Words
            </button>
          </div>
        </div>

        {/* Word of the Day Card */}
        {wotdResolution && (
          <div
            className="p-4 rounded-xl border space-y-2.5"
            style={{ backgroundColor: colors.blueLight, borderColor: colors.teal }}
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2 text-xs font-black" style={{ color: colors.inkBold }}>
                <Calendar className="w-4 h-4" style={{ color: colors.teal }} />
                <span>
                  Word of the Day ({lang}) • UTC {wotdResolution.utcDateKey}
                </span>
              </div>

              {wotdResolution.unlocked && (
                <div className="flex items-center gap-1.5 text-xs">
                  <button
                    type="button"
                    onClick={() =>
                      speakText(wotdResolution.entry!.word, {
                        lang: resolveSpeechLocale(lang, wotdResolution.entry!.languageVariant),
                      })
                    }
                    className="flex items-center gap-1 px-2.5 py-1 rounded-lg border font-bold"
                    style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.tealDark }}
                  >
                    <Volume2 className="w-3.5 h-3.5" />
                    Pronounce
                  </button>
                  <button
                    type="button"
                    onClick={() => handleCycleWotd(lang)}
                    className="flex items-center gap-1 px-2.5 py-1 rounded-lg border font-bold"
                    style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                    Next Spotlight
                  </button>
                  <button
                    type="button"
                    onClick={() => setWotdHistoryOpen((v) => !v)}
                    className="px-2.5 py-1 rounded-lg border font-bold"
                    style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
                  >
                    History ({wotdResolution.history.length})
                  </button>
                </div>
              )}
            </div>

            {!wotdResolution.unlocked ? (
              <div className="space-y-1.5 text-xs">
                <p style={{ color: colors.ink }}>
                  Daily spotlight unlocks once you save at least <strong>{WOTD_MIN_WORDS_THRESHOLD} unique words</strong> in {lang}{' '}
                  (currently <strong>{wotdResolution.uniqueCount} / {WOTD_MIN_WORDS_THRESHOLD}</strong>).
                </p>
                <div className="w-full h-2 rounded-full overflow-hidden" style={{ backgroundColor: colors.surface }}>
                  <div
                    className="h-full"
                    style={{
                      width: `${Math.min(100, (wotdResolution.uniqueCount / WOTD_MIN_WORDS_THRESHOLD) * 100)}%`,
                      backgroundColor: colors.teal,
                    }}
                  />
                </div>
              </div>
            ) : (
              wotdResolution.entry && (
                <div
                  className="p-3.5 rounded-xl border space-y-1.5"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border }}
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-lg font-black" style={{ color: colors.inkBold }}>
                      {wotdResolution.entry.word}
                    </span>
                    {wotdResolution.entry.pronunciation && (
                      <span className="font-mono text-xs" style={{ color: colors.muted }}>
                        {wotdResolution.entry.pronunciation}
                      </span>
                    )}
                    <span className="text-[0.6875rem] font-bold" style={{ color: colors.tealDark }}>
                      Cycle #{wotdResolution.cycleNumber} ({wotdResolution.seenCount}/{wotdResolution.uniqueCount} seen)
                    </span>
                  </div>
                  <p className="text-xs font-semibold" style={{ color: colors.ink }}>
                    {wotdResolution.entry.meaning}
                  </p>
                  {wotdResolution.entry.examples[0] && (
                    <p className="text-xs italic" style={{ color: colors.muted }}>
                      “{wotdResolution.entry.examples[0].sentence}”
                    </p>
                  )}

                  {wotdHistoryOpen && wotdResolution.history.length > 0 && (
                    <div className="pt-2 mt-2 border-t text-[0.6875rem] space-y-1" style={{ borderColor: colors.borderLight }}>
                      <div className="font-bold" style={{ color: colors.muted }}>
                        Recent Spotlight History:
                      </div>
                      {wotdResolution.history.slice(0, 6).map((h) => (
                        <div key={`${h.utcDateKey}-${h.wordKey}`} className="flex justify-between">
                          <span className="font-bold" style={{ color: colors.inkBold }}>
                            {h.word}
                          </span>
                          <span style={{ color: colors.muted }}>{h.utcDateKey}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )
            )}
          </div>
        )}

        {/* Proficiency Level Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from(levelBuckets.entries()).map(([code, lvlDecks]) => {
            const desc = getLevelDescriptor(code, lang);
            const totalWords = lvlDecks.reduce((s, d) => s + d.entries.length, 0);
            return (
              <div
                key={code}
                className="rounded-xl border p-4 space-y-3"
                style={{ backgroundColor: colors.surface, borderColor: colors.border }}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Folder className="w-4 h-4" style={{ color: colors.teal }} />
                    <span className="text-sm font-black" style={{ color: colors.inkBold }}>
                      {desc.title}
                    </span>
                  </div>
                  <span
                    className="px-2 py-0.5 rounded text-[0.625rem] font-bold"
                    style={{ backgroundColor: colors.surfaceVariant, color: colors.ink }}
                  >
                    {totalWords} words
                  </span>
                </div>

                {lvlDecks.length === 0 ? (
                  <p className="text-xs" style={{ color: colors.muted }}>
                    No decks yet in {desc.shortBadge}.
                  </p>
                ) : (
                  <div className="space-y-2">
                    {lvlDecks.map((deck) => {
                      const stats = SpacedRepetitionEngine.computeDeckStats(
                        deck.entries,
                        (e) => e.id,
                        srsByDeck[deck.id] || {}
                      );
                      return (
                        <div
                          key={deck.id}
                          className="p-2.5 rounded-lg border space-y-2"
                          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.borderLight }}
                        >
                          <div className="flex items-start justify-between gap-2">
                            <div>
                              <div className="text-xs font-bold es-clamp-2" style={{ color: colors.inkBold }}>
                                {deck.title}
                              </div>
                              <div className="text-[0.625rem] es-scroll-x" style={{ color: colors.muted }}>
                                {deck.entries.length} words • {stats.dueCount + stats.newCount} due/new •{' '}
                                {stats.masteredCount} mastered
                              </div>
                            </div>
                          </div>

                          <div className="flex items-center justify-between gap-1">
                            <div className="flex gap-1">
                              <button
                                type="button"
                                onClick={() => {
                                  setDeckSubTab('WORDS');
                                  setView({ kind: 'DECK_DETAIL', language: lang, deckId: deck.id });
                                }}
                                className="px-2.5 py-1 rounded text-[0.6875rem] font-bold"
                                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
                              >
                                Open Deck
                              </button>
                              <button
                                type="button"
                                onClick={() => {
                                  setDeckSubTab('RECALL');
                                  setView({ kind: 'DECK_DETAIL', language: lang, deckId: deck.id });
                                }}
                                className="px-2.5 py-1 rounded border text-[0.6875rem] font-bold"
                                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.tealDark }}
                              >
                                Train SM-2
                              </button>
                            </div>

                            {pendingDeleteDeckId === deck.id ? (
                              <button
                                type="button"
                                onClick={() => handleDeleteDeck(deck.id)}
                                className="px-2 py-1 rounded text-[0.625rem] font-bold text-white"
                                style={{ backgroundColor: colors.red }}
                              >
                                Confirm
                              </button>
                            ) : (
                              <button
                                type="button"
                                onClick={() => setPendingDeleteDeckId(deck.id)}
                                className="p-1 rounded border"
                                style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.red }}
                                title="Delete deck"
                              >
                                <Trash2 className="w-3 h-3" />
                              </button>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    );
  }

  // --- TIER 1: LANGUAGE FOLDERS HOME ---
  return (
    <div className="space-y-5">
      {/* Top Banner */}
      <div
        className="p-4 sm:p-5 rounded-2xl border flex flex-col lg:flex-row lg:items-center justify-between gap-4"
        style={{ background: colors.heroGradient, borderColor: colors.border, boxShadow: colors.cardShadow }}
      >
        <div className="min-w-0">
          <h3 className="text-base font-black flex items-center gap-2 tracking-tight" style={{ color: colors.inkBold }}>
            <Globe className="w-5 h-5 flex-shrink-0" style={{ color: colors.teal }} />
            <span className="es-clamp-2">Vocabulary Hub &amp; Active Recall Lexicon</span>
          </h3>
          <p className="text-xs mt-1 es-scroll-x" style={{ color: colors.muted }}>
            Organize vocabulary by Language → Proficiency Folder (CEFR / JLPT / HSK / TOPIK) with UTC Word of the Day, Headword Inbox &amp; SM-2 SRS.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 text-xs">
          {/* English Variant Toggle */}
          <div
            className="flex items-center gap-1 px-2 py-1 rounded-lg border"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <span className="font-bold text-[0.6875rem] mr-1" style={{ color: colors.muted }}>
              Variant:
            </span>
            {(['UK', 'US', 'Neutral'] as const).map((vr) => (
              <button
                key={vr}
                type="button"
                onClick={() => onChangeEnglishVariant(vr)}
                className="px-2 py-0.5 rounded font-bold text-[0.6875rem]"
                style={
                  englishVariant.toUpperCase() === vr.toUpperCase()
                    ? { backgroundColor: colors.teal, color: colors.onAccentText }
                    : { color: colors.ink }
                }
              >
                {vr}
              </button>
            ))}
          </div>

          <button
            type="button"
            onClick={onOpenHeadwordInbox}
            className="flex items-center gap-1.5 px-3 py-2 rounded-lg border font-bold"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
          >
            <Inbox className="w-4 h-4" style={{ color: colors.teal }} />
            Headword Inbox ({headwordInbox.length})
          </button>

          {allQueueItems.length > 0 && (
            <button
              type="button"
              onClick={() => {
                setDeckSubTab('RECALL');
                setView({
                  kind: 'VIRTUAL_COLLECTION',
                  title: 'Cross-Language Active Recall',
                  subtitle: `${totalDueAcrossAll} due/new words across ${languageStats.length} language(s)`,
                  decks,
                });
              }}
              className="flex items-center gap-1.5 px-3 py-2 rounded-lg border font-bold"
              style={{ backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.tealDark }}
            >
              <Flame className="w-4 h-4" />
              Review All Due ({totalDueAcrossAll})
            </button>
          )}

          <button
            type="button"
            onClick={() => onOpenImportModal('English')}
            className="flex items-center gap-1.5 px-4 py-2 rounded-lg font-bold shadow-sm"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            <Plus className="w-4 h-4" />
            Import Words
          </button>
        </div>
      </div>

      {/* Global Search */}
      <div className="relative">
        <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2" style={{ color: colors.muted }} />
        <input
          type="text"
          value={globalSearch}
          onChange={(e) => setGlobalSearch(e.target.value)}
          placeholder="Search any word, definition, or translation across all language decks..."
          className="w-full pl-10 pr-4 py-2.5 rounded-xl border text-xs"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
        />
      </div>

      {/* Global Search Results OR Language Folders */}
      {globalSearch.trim() ? (
        <div className="space-y-3">
          <div className="text-xs font-bold" style={{ color: colors.muted }}>
            Search Results ({globalSearchHits.length})
          </div>
          {globalSearchHits.length === 0 ? (
            <div
              className="p-8 rounded-xl border text-center text-xs"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.muted }}
            >
              No matching vocabulary entries found for “{globalSearch}”.
            </div>
          ) : (
            <div className="space-y-2.5">
              {globalSearchHits.map((hit) => renderWordCard(hit.deckId, hit.entry, hit.deckTitle))}
            </div>
          )}
        </div>
      ) : languageStats.length === 0 ? (
        <div
          className="p-10 rounded-xl border text-center space-y-3"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <BookOpen className="w-10 h-10 mx-auto" style={{ color: colors.muted }} />
          <p className="text-sm font-bold" style={{ color: colors.inkBold }}>
            No vocabulary decks saved yet.
          </p>
          <button
            type="button"
            onClick={() => onOpenImportModal('English')}
            className="px-4 py-2 rounded-lg text-xs font-bold"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            Import First Vocabulary Deck
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {languageStats.map((ls) => (
            <div
              key={ls.language}
              className="p-5 rounded-xl border space-y-4 transition"
              style={{ backgroundColor: colors.surface, borderColor: colors.border }}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <div
                    className="p-2.5 rounded-xl"
                    style={{ backgroundColor: colors.tealLight, color: colors.tealDark }}
                  >
                    <Sparkles className="w-5 h-5" />
                  </div>
                  <div>
                    <h4 className="text-base font-black" style={{ color: colors.inkBold }}>
                      {ls.language}
                    </h4>
                    <span className="text-xs" style={{ color: colors.muted }}>
                      {ls.deckCount} deck(s) • {ls.totalWords} words
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex flex-wrap gap-2 text-[0.6875rem] font-bold">
                <span className="px-2.5 py-1 rounded-lg" style={{ backgroundColor: colors.blueLight, color: colors.inkBold }}>
                  {ls.dueCount} Due / New
                </span>
                <span className="px-2.5 py-1 rounded-lg" style={{ backgroundColor: colors.greenLight, color: colors.greenDark }}>
                  {ls.masteredCount} Mastered
                </span>
              </div>

              <button
                type="button"
                onClick={() => setView({ kind: 'LEVELS', language: ls.language })}
                className="w-full py-2.5 rounded-lg text-xs font-bold transition"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                Open {ls.language} Levels &amp; Word of the Day
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default VocabularyHubTab;
