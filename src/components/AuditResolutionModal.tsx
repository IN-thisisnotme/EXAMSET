import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  Check,
  CheckCircle2,
  Copy,
  RotateCcw,
  ShieldCheck,
  Sparkles,
  Wrench,
  X,
} from 'lucide-react';
import { AuditPhase, AuditQuestionReport, QuestionPaper } from '../types/exam';
import { useTheme } from '../theme/ThemeProvider';
import { QuestionAuditHelper } from '../utils/questionAuditHelper';

interface AuditResolutionModalProps {
  isOpen: boolean;
  paper: QuestionPaper;
  onUpdatePaper: (updated: QuestionPaper) => void;
  onClose: () => void;
}

export const AuditResolutionModal: React.FC<AuditResolutionModalProps> = ({
  isOpen,
  paper,
  onUpdatePaper,
  onClose,
}) => {
  const { colors } = useTheme();
  const [phase, setPhase] = useState<AuditPhase>('PHASE_1_KEYS');
  const [reports, setReports] = useState<AuditQuestionReport[]>(() =>
    QuestionAuditHelper.runHeuristicPreAudit(paper)
  );
  const [rawJsonInput, setRawJsonInput] = useState<string>('');
  const [parseError, setParseError] = useState<string | null>(null);
  const [copiedPrompt, setCopiedPrompt] = useState<boolean>(false);
  const [undoStack, setUndoStack] = useState<QuestionPaper[]>([]);
  const [filterMode, setFilterMode] = useState<'issues' | 'all'>('issues');

  const metrics = useMemo(() => QuestionAuditHelper.computeMetrics(reports), [reports]);

  const activePrompt = useMemo(() => {
    if (phase === 'PHASE_1_KEYS') {
      return QuestionAuditHelper.generatePhase1Prompt(paper);
    }
    if (phase === 'PHASE_2_REPLACEMENTS') {
      const flawed = reports.filter(
        (r) => r.status === 'FLAWED_QUESTION' || r.status === 'VAGUE_OPTIONS' || r.status === 'MISSING_OPTIONS'
      );
      return QuestionAuditHelper.generatePhase2ReplacementPrompt(paper, flawed.length > 0 ? flawed : reports.slice(0, 3));
    }
    return QuestionAuditHelper.generatePhase3ExplanationPrompt(paper);
  }, [phase, paper, reports]);

  if (!isOpen) return null;

  const pushUndoAndApply = (nextPaper: QuestionPaper) => {
    setUndoStack((prev) => [paper, ...prev].slice(0, 15));
    onUpdatePaper(nextPaper);
  };

  const handleCopyPrompt = () => {
    navigator.clipboard.writeText(activePrompt);
    setCopiedPrompt(true);
    setTimeout(() => setCopiedPrompt(false), 2000);
  };

  const handleRunHeuristic = () => {
    setReports(QuestionAuditHelper.runHeuristicPreAudit(paper));
    setParseError(null);
  };

  const handleImportAiAudit = () => {
    setParseError(null);
    if (!rawJsonInput.trim()) {
      setParseError('Paste the JSON array returned by your AI auditor first.');
      return;
    }
    try {
      const parsed = QuestionAuditHelper.parseAuditReport(rawJsonInput, paper);
      setReports((prev) => QuestionAuditHelper.mergeAuditReports(prev, parsed, paper));
      setRawJsonInput('');
    } catch (err: any) {
      setParseError(`Audit JSON parse error: ${err.message}`);
    }
  };

  const handleApplyFix = (report: AuditQuestionReport) => {
    const nextPaper = QuestionAuditHelper.applyReportToPaper(paper, report);
    pushUndoAndApply(nextPaper);
    setReports((prev) =>
      prev.map((r) => (r.qNum === report.qNum ? { ...r, isResolved: true, isVerified: true } : r))
    );
  };

  const handleToggleNeutralize = (qNum: number) => {
    const nextPaper: QuestionPaper = {
      ...paper,
      questions: paper.questions.map((q) => (q.num === qNum ? { ...q, isNeutralized: !q.isNeutralized } : q)),
      metadata: { ...paper.metadata, updatedAt: Date.now() },
    };
    pushUndoAndApply(nextPaper);
    setReports((prev) =>
      prev.map((r) => (r.qNum === qNum ? { ...r, isNeutralized: !r.isNeutralized, isResolved: true } : r))
    );
  };

  const handleUndo = () => {
    if (undoStack.length === 0) return;
    const [prevPaper, ...rest] = undoStack;
    setUndoStack(rest);
    onUpdatePaper(prevPaper);
  };

  const visibleReports = filterMode === 'issues' ? reports.filter((r) => r.status !== 'CLEAN' || !r.isResolved) : reports;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.62)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-4xl max-h-[92vh] rounded-2xl shadow-2xl flex flex-col overflow-hidden"
        style={{
          backgroundColor: colors.surface,
          border: `1px solid ${colors.border}`,
          color: colors.ink,
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          className="px-5 py-4 flex items-center justify-between flex-wrap gap-2"
          style={{ borderBottom: `1px solid ${colors.border}` }}
        >
          <div className="flex items-center gap-2.5">
            <div
              className="w-9 h-9 rounded-lg flex items-center justify-center"
              style={{ backgroundColor: colors.tealLight, color: colors.teal }}
            >
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm font-bold" style={{ color: colors.inkBold }}>
                3-Phase Quality Audit & Resolution Hub
              </h3>
              <p className="text-[11px]" style={{ color: colors.muted }}>
                Run local structural diagnostics, generate 3-phase AI audit prompts, & apply 1-tap fixes with rollback
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {undoStack.length > 0 && (
              <button
                type="button"
                onClick={handleUndo}
                className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs font-bold border"
                style={{ borderColor: colors.amber, color: colors.amber, backgroundColor: colors.amberLight }}
              >
                <RotateCcw className="w-3.5 h-3.5" />
                Undo Last ({undoStack.length})
              </button>
            )}
            <button onClick={onClose} className="p-1.5 rounded-lg" style={{ color: colors.muted }}>
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-5 space-y-4">
          {/* Metrics Strip */}
          <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 text-center text-xs">
            {[
              { label: 'Clean', val: metrics.cleanCount, bg: colors.greenLight, fg: colors.green },
              { label: 'Wrong/Miss Key', val: metrics.wrongKeysCount + metrics.missingKeysCount, bg: colors.redLight, fg: colors.red },
              { label: 'Flawed/Opts', val: metrics.flawedQuestionsCount + metrics.missingOptionsCount, bg: colors.amberLight, fg: colors.amber },
              { label: 'Verified', val: metrics.verifiedCount, bg: colors.tealLight, fg: colors.tealDark },
              { label: 'Neutralized', val: metrics.neutralizedCount, bg: colors.purpleLight, fg: colors.purple },
            ].map((m) => (
              <div key={m.label} className="p-2.5 rounded-xl" style={{ backgroundColor: m.bg }}>
                <div className="text-base font-black" style={{ color: m.fg }}>
                  {m.val}
                </div>
                <div className="text-[10.5px] font-bold" style={{ color: m.fg }}>
                  {m.label}
                </div>
              </div>
            ))}
          </div>

          {/* 3-Phase Stepper + Copy Prompt */}
          <div
            className="p-4 rounded-xl space-y-3"
            style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}` }}
          >
            <div className="flex items-center justify-between flex-wrap gap-2">
              <div className="flex gap-1.5 flex-wrap">
                {([
                  { id: 'PHASE_1_KEYS', label: 'Phase 1: Keys & Stems' },
                  { id: 'PHASE_2_REPLACEMENTS', label: 'Phase 2: Replacements' },
                  { id: 'PHASE_3_EXPLANATIONS', label: 'Phase 3: Explanations' },
                ] as { id: AuditPhase; label: string }[]).map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => setPhase(p.id)}
                    className="px-3 py-1.5 rounded-lg text-xs font-bold transition"
                    style={{
                      backgroundColor: phase === p.id ? colors.teal : colors.surface,
                      color: phase === p.id ? '#FFFFFF' : colors.ink,
                      border: `1px solid ${phase === p.id ? colors.teal : colors.border}`,
                    }}
                  >
                    {p.label}
                  </button>
                ))}
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={handleRunHeuristic}
                  className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-bold border"
                  style={{ borderColor: colors.border, backgroundColor: colors.surface, color: colors.ink }}
                >
                  <Wrench className="w-3.5 h-3.5" style={{ color: colors.teal }} />
                  Re-scan Locally
                </button>
                <button
                  type="button"
                  onClick={handleCopyPrompt}
                  className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-bold text-white"
                  style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
                >
                  {copiedPrompt ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                  {copiedPrompt ? 'Copied Audit Prompt' : 'Copy Audit Prompt'}
                </button>
              </div>
            </div>

            <div className="flex gap-2">
              <textarea
                value={rawJsonInput}
                onChange={(e) => setRawJsonInput(e.target.value)}
                rows={2}
                placeholder="Paste JSON audit response from Gemini / ChatGPT / Claude here..."
                className="flex-1 p-2.5 rounded-lg font-mono text-[11px] focus:outline-none"
                style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
              />
              <button
                type="button"
                onClick={handleImportAiAudit}
                className="px-4 py-2 rounded-lg text-xs font-bold text-white self-stretch"
                style={{ backgroundColor: colors.teal }}
              >
                <Sparkles className="w-3.5 h-3.5 inline mr-1" />
                Parse Report
              </button>
            </div>
            {parseError && (
              <div className="text-xs font-semibold" style={{ color: colors.red }}>
                {parseError}
              </div>
            )}
          </div>

          {/* Filter Bar */}
          <div className="flex items-center justify-between text-xs">
            <span className="font-bold" style={{ color: colors.inkBold }}>
              Audit Queue ({visibleReports.length} shown)
            </span>
            <div className="flex gap-1.5">
              <button
                type="button"
                onClick={() => setFilterMode('issues')}
                className="px-2.5 py-1 rounded-lg font-semibold"
                style={{
                  backgroundColor: filterMode === 'issues' ? colors.tealLight : colors.surfaceVariant,
                  color: filterMode === 'issues' ? colors.tealDark : colors.muted,
                }}
              >
                Actionable Issues
              </button>
              <button
                type="button"
                onClick={() => setFilterMode('all')}
                className="px-2.5 py-1 rounded-lg font-semibold"
                style={{
                  backgroundColor: filterMode === 'all' ? colors.tealLight : colors.surfaceVariant,
                  color: filterMode === 'all' ? colors.tealDark : colors.muted,
                }}
              >
                All ({reports.length})
              </button>
            </div>
          </div>

          {/* Reports List */}
          <div className="space-y-2.5">
            {visibleReports.length === 0 ? (
              <div
                className="p-6 rounded-xl text-center text-xs space-y-1"
                style={{ backgroundColor: colors.greenLight, color: colors.green }}
              >
                <CheckCircle2 className="w-6 h-6 mx-auto" />
                <div className="font-bold">All audited questions look clean and resolved!</div>
              </div>
            ) : (
              visibleReports.map((rep) => {
                const q = paper.questions.find((x) => x.num === rep.qNum);
                const isIssue = rep.status !== 'CLEAN' && !rep.isResolved;
                return (
                  <div
                    key={rep.qNum}
                    className="p-3.5 rounded-xl space-y-2"
                    style={{
                      backgroundColor: colors.surfaceVariant,
                      border: `1px solid ${isIssue ? colors.amber : colors.border}`,
                    }}
                  >
                    <div className="flex items-center justify-between flex-wrap gap-2">
                      <div className="flex items-center gap-2">
                        <span
                          className="px-2 py-0.5 rounded text-xs font-bold"
                          style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
                        >
                          Q{rep.qNum}
                        </span>
                        <span
                          className="px-2 py-0.5 rounded text-[10px] font-bold"
                          style={{
                            backgroundColor: rep.status === 'CLEAN' ? colors.greenLight : colors.amberLight,
                            color: rep.status === 'CLEAN' ? colors.green : colors.amber,
                          }}
                        >
                          {rep.status}
                        </span>
                        {rep.isVerified && (
                          <span className="text-[10px] font-bold" style={{ color: colors.green }}>
                            ✓ Sealed
                          </span>
                        )}
                        {q?.isNeutralized && (
                          <span className="text-[10px] font-bold" style={{ color: colors.purple }}>
                            Neutralized
                          </span>
                        )}
                      </div>

                      <div className="flex items-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => handleToggleNeutralize(rep.qNum)}
                          className="px-2.5 py-1 rounded text-[11px] font-semibold border"
                          style={{
                            borderColor: colors.border,
                            backgroundColor: q?.isNeutralized ? colors.purpleLight : colors.surface,
                            color: q?.isNeutralized ? colors.purple : colors.ink,
                          }}
                        >
                          {q?.isNeutralized ? 'Un-neutralize' : 'Neutralize (Full Marks)'}
                        </button>
                        {(rep.suggestedKey || rep.recheckedExplanation || rep.replacementQuestion) && (
                          <button
                            type="button"
                            onClick={() => handleApplyFix(rep)}
                            className="px-2.5 py-1 rounded text-[11px] font-bold text-white"
                            style={{ backgroundColor: colors.teal }}
                          >
                            Apply Fix
                          </button>
                        )}
                      </div>
                    </div>

                    {q && (
                      <div className="text-xs line-clamp-2" style={{ color: colors.inkBold }}>
                        {q.question}
                      </div>
                    )}

                    {rep.flawReason && (
                      <div className="text-[11px] flex items-start gap-1.5" style={{ color: colors.amber }}>
                        <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
                        <span>{rep.flawReason}</span>
                      </div>
                    )}

                    {rep.suggestedKey && rep.suggestedKey !== rep.currentKey && (
                      <div className="text-[11px] font-bold" style={{ color: colors.green }}>
                        Suggested Key Change: {rep.currentKey || 'None'} → {rep.suggestedKey}
                      </div>
                    )}

                    {rep.replacementQuestion && (
                      <div
                        className="p-2 rounded-lg text-[11px]"
                        style={{ backgroundColor: colors.surface, border: `1px solid ${colors.teal}` }}
                      >
                        <strong style={{ color: colors.teal }}>Replacement Ready: </strong>
                        {rep.replacementQuestion.question} (Key: {rep.replacementQuestion.correctAnswer})
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>

        <div
          className="px-5 py-3 flex justify-end"
          style={{ borderTop: `1px solid ${colors.border}`, backgroundColor: colors.surfaceVariant }}
        >
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-lg text-xs font-bold"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            Close Audit Hub
          </button>
        </div>
      </div>
    </div>
  );
};

export default AuditResolutionModal;
