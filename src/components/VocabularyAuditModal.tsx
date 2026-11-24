import React, { useMemo, useState } from 'react';
import {
  AlertCircle,
  CheckCircle2,
  ShieldCheck,
  Wrench,
  X,
} from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';
import {
  VocabularyAuditFinding,
  VocabularyAuditStatus,
  VocabularyDeck,
} from '../types/phase4';
import { VocabularyEngine } from '../utils/vocabularyEngine';

interface VocabularyAuditModalProps {
  isOpen: boolean;
  deck: VocabularyDeck | null;
  allDecks: VocabularyDeck[];
  onUpdateDeck: (updated: VocabularyDeck) => void;
  onClose: () => void;
}

export const VocabularyAuditModal: React.FC<VocabularyAuditModalProps> = ({
  isOpen,
  deck,
  allDecks,
  onUpdateDeck,
  onClose,
}) => {
  const { colors } = useTheme();
  const [statusFilter, setStatusFilter] = useState<VocabularyAuditStatus | 'ALL' | 'ISSUES_ONLY'>('ISSUES_ONLY');

  const findings = useMemo<VocabularyAuditFinding[]>(() => {
    if (!deck) return [];
    return VocabularyEngine.runLocalLexiconAudit(deck, allDecks);
  }, [deck, allDecks]);

  const filtered = useMemo(() => {
    if (statusFilter === 'ALL') return findings;
    if (statusFilter === 'ISSUES_ONLY') return findings.filter((f) => f.status !== 'VERIFIED');
    return findings.filter((f) => f.status === statusFilter);
  }, [findings, statusFilter]);

  if (!isOpen || !deck) return null;

  const issueCount = findings.filter((f) => f.status !== 'VERIFIED').length;

  const handleApplyFix = (finding: VocabularyAuditFinding) => {
    const nextEntries = deck.entries.map((e) => {
      if (e.id !== finding.entryId) return e;
      return {
        ...e,
        partOfSpeech: finding.suggestedPartOfSpeech || e.partOfSpeech,
        examples: finding.suggestedExamples && e.examples.length === 0 ? finding.suggestedExamples : e.examples,
        meaning: finding.suggestedMeaning || e.meaning,
        usageNote: finding.suggestedUsageNote || e.usageNote,
        updatedAt: Date.now(),
      };
    });
    onUpdateDeck({ ...deck, entries: nextEntries, updatedAt: Date.now() });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 overflow-y-auto">
      <div
        className="w-full max-w-3xl rounded-2xl border shadow-2xl overflow-hidden flex flex-col max-h-[88vh]"
        style={{ backgroundColor: colors.surface, borderColor: colors.border }}
      >
        <div
          className="px-5 py-4 border-b flex items-center justify-between"
          style={{ backgroundColor: colors.navyBanner, borderColor: colors.border }}
        >
          <div className="flex items-center gap-2.5">
            <ShieldCheck className="w-5 h-5 text-emerald-300" />
            <div>
              <h3 className="text-base font-black" style={{ color: colors.navyBannerText }}>
                Offline Lexicon Audit — {deck.title}
              </h3>
              <p className="text-[0.6875rem] opacity-80" style={{ color: colors.navyBannerText }}>
                {findings.length} total entries • {issueCount} item(s) flagged for review
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
          <div className="flex flex-wrap gap-1.5">
            {(
              [
                { id: 'ISSUES_ONLY', label: `Issues (${issueCount})` },
                { id: 'ALL', label: `All (${findings.length})` },
                { id: 'VERIFIED', label: `Verified (${findings.length - issueCount})` },
              ] as const
            ).map((tab) => (
              <button
                key={tab.id}
                type="button"
                onClick={() => setStatusFilter(tab.id)}
                className="px-3 py-1.5 rounded-lg border font-bold"
                style={
                  statusFilter === tab.id
                    ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText, borderColor: colors.primaryButtonBg }
                    : { backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }
                }
              >
                {tab.label}
              </button>
            ))}
          </div>

          {filtered.length === 0 ? (
            <div
              className="p-8 rounded-xl border text-center space-y-2"
              style={{ backgroundColor: colors.greenLight, borderColor: colors.green }}
            >
              <CheckCircle2 className="w-8 h-8 mx-auto" style={{ color: colors.greenDark }} />
              <p className="font-bold" style={{ color: colors.greenDark }}>
                All lexicon checks passed with zero issues in this filter!
              </p>
            </div>
          ) : (
            <div className="space-y-2.5">
              {filtered.map((f) => {
                const isOk = f.status === 'VERIFIED';
                const canAutoFix = Boolean(f.suggestedPartOfSpeech || f.suggestedExamples);
                return (
                  <div
                    key={f.entryId}
                    className="p-3.5 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-2"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                  >
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        {isOk ? (
                          <CheckCircle2 className="w-4 h-4" style={{ color: colors.green }} />
                        ) : (
                          <AlertCircle className="w-4 h-4" style={{ color: colors.amber }} />
                        )}
                        <span className="font-black text-sm" style={{ color: colors.inkBold }}>
                          {f.currentEntry.word}
                        </span>
                        <span
                          className="px-2 py-0.5 rounded text-[0.625rem] font-bold border"
                          style={{
                            backgroundColor: isOk ? colors.greenLight : colors.amberLight,
                            color: isOk ? colors.greenDark : colors.amber,
                            borderColor: isOk ? colors.green : colors.amber,
                          }}
                        >
                          {f.status.replace('_', ' ')}
                        </span>
                      </div>
                      <p style={{ color: colors.ink }}>{f.issueSummary}</p>
                    </div>

                    {canAutoFix && (
                      <button
                        type="button"
                        onClick={() => handleApplyFix(f)}
                        className="flex items-center gap-1 px-3 py-1.5 rounded-lg font-bold self-start sm:self-center flex-shrink-0"
                        style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
                      >
                        <Wrench className="w-3.5 h-3.5" />
                        Apply Suggested Fix
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        <div
          className="px-5 py-3 border-t flex justify-end"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-lg border text-xs font-bold"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};

export default VocabularyAuditModal;
