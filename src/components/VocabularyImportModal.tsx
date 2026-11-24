import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  Download,
  Upload,
  X,
} from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';
import {
  VocabularyDeck,
  VocabularyDuplicateAction,
  VocabularyEntry,
  VocabularyLevelCode,
  VocabularyMergeDecision,
} from '../types/phase4';
import {
  getLevelDescriptor,
  PROMPT_LEVEL_CODES,
  VocabularyEngine,
  VocabularyVariants,
} from '../utils/vocabularyEngine';

interface VocabularyImportModalProps {
  isOpen: boolean;
  decks: VocabularyDeck[];
  defaultLanguage?: string;
  defaultEnglishVariant?: string;
  onCompleteImport: (updatedDecks: VocabularyDeck[], summaryMsg: string) => void;
  onClose: () => void;
}

const DEFAULT_DECISION: VocabularyMergeDecision = {
  action: 'MERGE_SEGMENTS',
  mergeExamples: true,
  mergeTranslation: true,
  mergeSynonymsAndCollocations: true,
  mergeMeaningAndNote: false,
  mergeLevelAndMeta: true,
};

export const VocabularyImportModal: React.FC<VocabularyImportModalProps> = ({
  isOpen,
  decks,
  defaultLanguage = 'English',
  defaultEnglishVariant = 'UK',
  onCompleteImport,
  onClose,
}) => {
  const { colors } = useTheme();
  const [rawInput, setRawInput] = useState<string>('');
  const [language, setLanguage] = useState<string>(defaultLanguage);
  const [variant, setVariant] = useState<string>(defaultEnglishVariant);
  const [levelCode, setLevelCode] = useState<VocabularyLevelCode>('B2');
  const [customLevelLabel, setCustomLevelLabel] = useState<string>('');
  const [targetDeckId, setTargetDeckId] = useState<string>('__NEW__');
  const [newDeckTitle, setNewDeckTitle] = useState<string>('');
  const [domain, setDomain] = useState<string>('Competitive Exam Editorial');
  const [decisions, setDecisions] = useState<Record<string, VocabularyMergeDecision>>({});

  const parsed = useMemo(
    () => VocabularyEngine.parseVocabularyInput(rawInput, levelCode, customLevelLabel),
    [rawInput, levelCode, customLevelLabel]
  );

  const analysis = useMemo(() => {
    if (parsed.entries.length === 0) return null;
    return VocabularyEngine.analyzeImport(
      parsed.entries,
      parsed.detectedFormat,
      parsed.warnings,
      language,
      decks,
      targetDeckId === '__NEW__' ? null : targetDeckId
    );
  }, [parsed, language, decks, targetDeckId]);

  const matchingDecks = useMemo(
    () => decks.filter((d) => d.language.trim().toLowerCase() === language.trim().toLowerCase()),
    [decks, language]
  );

  if (!isOpen) return null;

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setRawInput(reader.result);
      }
    };
    reader.readAsText(file);
  };

  const getDecision = (dupId: string): VocabularyMergeDecision =>
    decisions[dupId] || DEFAULT_DECISION;

  const updateDecision = (dupId: string, patch: Partial<VocabularyMergeDecision>) => {
    setDecisions((prev) => ({
      ...prev,
      [dupId]: { ...(prev[dupId] || DEFAULT_DECISION), ...patch },
    }));
  };

  const handleConfirmImport = () => {
    if (!analysis) return;
    const now = Date.now();
    const normVariant = VocabularyVariants.normalize(language, variant);

    const normalizeIncoming = (e: VocabularyEntry): VocabularyEntry => ({
      ...e,
      language: e.language || language.trim() || 'English',
      languageVariant: e.languageVariant || normVariant,
      createdAt: e.createdAt || now,
      updatedAt: now,
    });

    let workingDecks = decks.map((d) => ({ ...d, entries: [...d.entries] }));
    const entriesToAddToTarget: VocabularyEntry[] = analysis.uniqueNewEntries.map(normalizeIncoming);
    let mergedCount = 0;
    let skippedCount = 0;

    for (const dup of analysis.duplicateItems) {
      const dec = getDecision(dup.id);
      if (dec.action === 'SKIP') {
        skippedCount++;
        continue;
      }
      if (dec.action === 'KEEP_SEPARATE') {
        entriesToAddToTarget.push(normalizeIncoming(dup.incoming));
        continue;
      }
      if (dup.isIntraBatch) {
        if (dec.action === 'MERGE_SEGMENTS' || dec.action === 'REPLACE_EXISTING') {
          const idxInBatch = entriesToAddToTarget.findIndex(
            (x) => VocabularyEngine.normalizeWordKey(x.word) === VocabularyEngine.normalizeWordKey(dup.incoming.word)
          );
          if (idxInBatch >= 0) {
            entriesToAddToTarget[idxInBatch] =
              dec.action === 'REPLACE_EXISTING'
                ? normalizeIncoming(dup.incoming)
                : VocabularyEngine.mergeEntrySegments(entriesToAddToTarget[idxInBatch], dup.incoming, dec);
            mergedCount++;
          }
        }
        continue;
      }

      const hostDeck = workingDecks.find((d) => d.id === dup.existingDeckId);
      if (!hostDeck) continue;
      const entryIdx = hostDeck.entries.findIndex((e) => e.id === dup.existing.id);
      if (entryIdx >= 0) {
        hostDeck.entries[entryIdx] =
          dec.action === 'REPLACE_EXISTING'
            ? { ...normalizeIncoming(dup.incoming), id: dup.existing.id }
            : VocabularyEngine.mergeEntrySegments(hostDeck.entries[entryIdx], dup.incoming, dec);
        hostDeck.updatedAt = now;
        mergedCount++;
      }
    }

    if (entriesToAddToTarget.length > 0 || targetDeckId === '__NEW__') {
      if (targetDeckId === '__NEW__') {
        if (entriesToAddToTarget.length > 0) {
          const lvlDesc = getLevelDescriptor(levelCode, language, customLevelLabel);
          const created: VocabularyDeck = {
            id: `vdeck-${now}-${Math.random().toString(36).slice(2, 6)}`,
            title: newDeckTitle.trim() || `${language.trim() || 'English'} ${lvlDesc.shortBadge} Pack`,
            language: language.trim() || 'English',
            languageVariant: normVariant,
            translationLanguage: entriesToAddToTarget.find((e) => e.translationLanguage)?.translationLanguage || '',
            levelCode: levelCode === 'MIXED' ? 'B2' : levelCode,
            customLevelLabel: levelCode === 'CUSTOM' ? customLevelLabel.trim() : '',
            domain: domain.trim() || 'General',
            entries: entriesToAddToTarget,
            createdAt: now,
            updatedAt: now,
          };
          workingDecks = [created, ...workingDecks];
        }
      } else {
        workingDecks = workingDecks.map((d) =>
          d.id === targetDeckId
            ? { ...d, entries: [...d.entries, ...entriesToAddToTarget], updatedAt: now }
            : d
        );
      }
    }

    onCompleteImport(
      workingDecks,
      `Imported ${entriesToAddToTarget.length} new word(s), merged ${mergedCount}, skipped ${skippedCount}.`
    );
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 overflow-y-auto">
      <div
        className="w-full max-w-4xl rounded-2xl border shadow-2xl overflow-hidden flex flex-col max-h-[92vh]"
        style={{ backgroundColor: colors.surface, borderColor: colors.border }}
      >
        <div
          className="px-5 py-4 border-b flex items-center justify-between"
          style={{ backgroundColor: colors.navyBanner, borderColor: colors.border }}
        >
          <div className="flex items-center gap-2.5">
            <Download className="w-5 h-5 text-emerald-300" />
            <div>
              <h3 className="text-base font-black" style={{ color: colors.navyBannerText }}>
                Vocabulary Batch Importer &amp; Duplicate Segment Merger
              </h3>
              <p className="text-[0.6875rem] opacity-80" style={{ color: colors.navyBannerText }}>
                Accepts Strict JSON arrays or WORD:/MEANING:/EXAMPLES: text blocks with automatic duplicate segment review
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
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkBold }}>
                Language
              </label>
              <input
                type="text"
                value={language}
                onChange={(e) => setLanguage(e.target.value)}
                className="w-full px-3 py-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              />
            </div>

            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkBold }}>
                Variant (UK/US)
              </label>
              <select
                value={variant}
                onChange={(e) => setVariant(e.target.value)}
                className="w-full px-3 py-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              >
                <option value="UK">UK (British / Oxford)</option>
                <option value="US">US (American)</option>
                <option value="Neutral">Neutral / International</option>
              </select>
            </div>

            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkBold }}>
                Default Level
              </label>
              <select
                value={levelCode}
                onChange={(e) => setLevelCode(e.target.value as VocabularyLevelCode)}
                className="w-full px-3 py-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              >
                {PROMPT_LEVEL_CODES.map((c) => (
                  <option key={c} value={c}>
                    {getLevelDescriptor(c, language, customLevelLabel).title}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkBold }}>
                Target Deck
              </label>
              <select
                value={targetDeckId}
                onChange={(e) => setTargetDeckId(e.target.value)}
                className="w-full px-3 py-1.5 rounded-lg border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              >
                <option value="__NEW__">+ Create New Deck</option>
                {matchingDecks.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.title} ({d.entries.length} words)
                  </option>
                ))}
              </select>
            </div>
          </div>

          {targetDeckId === '__NEW__' && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block font-bold mb-1" style={{ color: colors.ink }}>
                  New Deck Title
                </label>
                <input
                  type="text"
                  value={newDeckTitle}
                  onChange={(e) => setNewDeckTitle(e.target.value)}
                  placeholder="e.g., Economist Analytical Lexicon C1"
                  className="w-full px-3 py-1.5 rounded-lg border"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>
              <div>
                <label className="block font-bold mb-1" style={{ color: colors.ink }}>
                  Domain
                </label>
                <input
                  type="text"
                  value={domain}
                  onChange={(e) => setDomain(e.target.value)}
                  className="w-full px-3 py-1.5 rounded-lg border"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>
            </div>
          )}

          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="font-bold" style={{ color: colors.inkBold }}>
                Paste AI JSON Array or Structured Vocabulary Text
              </label>
              <label
                className="flex items-center gap-1 px-2.5 py-1 rounded-lg border cursor-pointer font-bold"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.tealDark }}
              >
                <Upload className="w-3.5 h-3.5" />
                Load .json / .txt
                <input type="file" accept=".json,.txt,.md" onChange={handleFileUpload} className="hidden" />
              </label>
            </div>

            <textarea
              value={rawInput}
              onChange={(e) => setRawInput(e.target.value)}
              rows={8}
              placeholder='Paste Vocabulary JSON array (e.g. [{"word":"...", "meaning":"...", "level":"C1"}]) or word list...'
              className="w-full min-h-[10.5rem] resize-y p-3 rounded-xl border font-mono text-xs leading-relaxed"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
            />
          </div>

          {/* Analysis Summary */}
          {rawInput.trim() && parsed.warnings.length > 0 && (
            <div
              className="p-3 rounded-lg border font-semibold"
              style={{ backgroundColor: colors.redLight, borderColor: colors.red, color: colors.red }}
            >
              {parsed.warnings.join(' ')}
            </div>
          )}

          {analysis && (
            <div className="space-y-3">
              <div
                className="p-3 rounded-xl border flex flex-wrap items-center gap-3"
                style={{ backgroundColor: colors.tealLight, borderColor: colors.teal }}
              >
                <CheckCircle2 className="w-4 h-4" style={{ color: colors.tealDark }} />
                <span className="font-bold" style={{ color: colors.inkBold }}>
                  Parsed {analysis.totalParsedCount} entry/entries ({analysis.detectedFormat}):
                </span>
                <span className="font-bold" style={{ color: colors.greenDark }}>
                  {analysis.uniqueNewEntries.length} New
                </span>
                {analysis.duplicateItems.length > 0 && (
                  <span className="font-bold" style={{ color: colors.amber }}>
                    {analysis.duplicateItems.length} Overlap / Duplicate(s) Detected
                  </span>
                )}
              </div>

              {analysis.duplicateItems.length > 0 && (
                <div className="space-y-2.5">
                  <div className="font-bold flex items-center gap-1.5" style={{ color: colors.amber }}>
                    <AlertTriangle className="w-4 h-4" />
                    Configure Duplicate Resolution ({analysis.duplicateItems.length})
                  </div>
                  {analysis.duplicateItems.map((dup) => {
                    const dec = getDecision(dup.id);
                    return (
                      <div
                        key={dup.id}
                        className="p-3 rounded-xl border space-y-2"
                        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                      >
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div>
                            <span className="font-black text-sm" style={{ color: colors.inkBold }}>
                              {dup.incoming.word}
                            </span>
                            <span className="ml-2 text-[0.6875rem]" style={{ color: colors.muted }}>
                              Already in: {dup.existingDeckTitle} ({dup.existingLevelTitle})
                            </span>
                          </div>

                          <select
                            value={dec.action}
                            onChange={(e) =>
                              updateDecision(dup.id, { action: e.target.value as VocabularyDuplicateAction })
                            }
                            className="px-2.5 py-1 rounded-lg border font-bold"
                            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                          >
                            <option value="MERGE_SEGMENTS">Merge Selected Segments</option>
                            <option value="SKIP">Skip Duplicate</option>
                            <option value="REPLACE_EXISTING">Replace Existing</option>
                            <option value="KEEP_SEPARATE">Keep Both Separate</option>
                          </select>
                        </div>

                        {dec.action === 'MERGE_SEGMENTS' && (
                          <div className="flex flex-wrap gap-3 text-[0.6875rem] pt-1">
                            {[
                              { k: 'mergeExamples', label: 'Append Examples' },
                              { k: 'mergeSynonymsAndCollocations', label: 'Union Synonyms/Collocations' },
                              { k: 'mergeTranslation', label: 'Merge Translation' },
                              { k: 'mergeMeaningAndNote', label: 'Update Meaning/Note' },
                            ].map((opt) => (
                              <label key={opt.k} className="flex items-center gap-1.5 cursor-pointer">
                                <input
                                  type="checkbox"
                                  checked={Boolean((dec as unknown as Record<string, unknown>)[opt.k])}
                                  onChange={(e) => updateDecision(dup.id, { [opt.k]: e.target.checked })}
                                />
                                <span style={{ color: colors.ink }}>{opt.label}</span>
                              </label>
                            ))}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>

        <div
          className="px-5 py-3.5 border-t flex items-center justify-end gap-2"
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
            disabled={!analysis || analysis.totalParsedCount === 0}
            onClick={handleConfirmImport}
            className="px-4 py-2 rounded-lg text-xs font-bold disabled:opacity-40"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            Confirm Import ({analysis?.totalParsedCount || 0})
          </button>
        </div>
      </div>
    </div>
  );
};

export default VocabularyImportModal;
