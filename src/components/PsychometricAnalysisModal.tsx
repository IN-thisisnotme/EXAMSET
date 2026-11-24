import React, { useMemo, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  BarChart3,
  CheckCircle2,
  Download,
  FlaskConical,
  Users,
  X,
} from 'lucide-react';
import { AttemptRecord, QuestionPaper } from '../types/exam';
import { useTheme } from '../theme/ThemeProvider';
import {
  CohortPsychometricReport,
  PsychometricEngine,
} from '../utils/psychometricEngine';

interface PsychometricAnalysisModalProps {
  isOpen: boolean;
  onClose: () => void;
  paper: QuestionPaper;
  realAttempts: AttemptRecord[];
}

export const PsychometricAnalysisModal: React.FC<PsychometricAnalysisModalProps> = ({
  isOpen,
  onClose,
  paper,
  realAttempts,
}) => {
  const { colors } = useTheme();
  const [useSyntheticDemo, setUseSyntheticDemo] = useState<boolean>(realAttempts.length === 0);
  const [activeSubTab, setActiveSubTab] = useState<'items' | 'candidates'>('items');
  const [flaggedOnly, setFlaggedOnly] = useState<boolean>(false);

  const report: CohortPsychometricReport = useMemo(() => {
    if (!useSyntheticDemo && realAttempts.length > 0) {
      const subs = PsychometricEngine.submissionsFromAttemptRecords(realAttempts);
      return PsychometricEngine.evaluateCohort(subs, paper, false);
    }
    if (useSyntheticDemo) {
      const subs = PsychometricEngine.generateSimulatedCohortSubmissions(paper, 40);
      return PsychometricEngine.evaluateCohort(subs, paper, true);
    }
    return PsychometricEngine.evaluateCohort([], paper, false);
  }, [useSyntheticDemo, realAttempts, paper]);

  if (!isOpen) return null;

  const downloadText = (filename: string, content: string, mime: string = 'text/csv;charset=utf-8') => {
    const blob = new Blob([content], { type: mime });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const displayedItems = flaggedOnly ? report.items.filter((i) => i.isFlagged) : report.items;
  const maxPosMarks = (paper.questions.length * ((paper.metadata.positiveMarks ?? 0) > 0 ? paper.metadata.positiveMarks! : 1.0)).toFixed(1);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 backdrop-blur-sm"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.58)' }}
    >
      <div
        className="w-full max-w-5xl max-h-[90vh] rounded-2xl shadow-2xl border flex flex-col overflow-hidden"
        style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
      >
        {/* Header */}
        <div
          className="px-5 py-4 border-b flex flex-wrap items-center justify-between gap-3"
          style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText, borderColor: colors.border }}
        >
          <div className="flex items-center gap-2.5">
            <Activity className="w-5 h-5" />
            <div>
              <h3 className="text-base font-bold leading-tight">
                Classical Test Theory (CTT) Psychometric Engine
              </h3>
              <p className="text-[11px] opacity-85">
                {paper.metadata.title} • {paper.questions.length} Items • Item Facility, Upper/Lower 27% Discrimination & Reliability
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:opacity-80"
            aria-label="Close psychometric analysis"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Source Switcher Banner */}
        <div
          className="px-5 py-3 border-b flex flex-wrap items-center justify-between gap-3"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <div className="flex items-center gap-2 flex-wrap">
            <button
              onClick={() => setUseSyntheticDemo(false)}
              disabled={realAttempts.length === 0}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border transition disabled:opacity-50"
              style={
                !useSyntheticDemo && realAttempts.length > 0
                  ? { backgroundColor: colors.teal, color: '#FFFFFF', borderColor: colors.teal }
                  : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
              }
            >
              <Users className="w-3.5 h-3.5" />
              <span>Real Submitted Attempts ({realAttempts.length})</span>
            </button>

            <button
              onClick={() => setUseSyntheticDemo(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border transition"
              style={
                useSyntheticDemo
                  ? { backgroundColor: colors.amber, color: '#FFFFFF', borderColor: colors.amber }
                  : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
              }
            >
              <FlaskConical className="w-3.5 h-3.5" />
              <span>Simulated Synthetic Cohort (40 Virtual — Demo Only)</span>
            </button>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() =>
                downloadText(
                  `${paper.metadata.title.replace(/\s+/g, '_')}_CTT_Items.csv`,
                  PsychometricEngine.formatPsychometricReportCsv(report, paper.metadata.title)
                )
              }
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs font-semibold border"
              style={{ backgroundColor: colors.surface, color: colors.inkBold, borderColor: colors.border }}
            >
              <Download className="w-3.5 h-3.5" />
              Items CSV
            </button>
            <button
              onClick={() =>
                downloadText(
                  `${paper.metadata.title.replace(/\s+/g, '_')}_Scorecards.csv`,
                  PsychometricEngine.formatCandidateScorecardsCsv(report, paper.metadata.title)
                )
              }
              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs font-semibold border"
              style={{ backgroundColor: colors.surface, color: colors.inkBold, borderColor: colors.border }}
            >
              <Download className="w-3.5 h-3.5" />
              Scorecards CSV
            </button>
          </div>
        </div>

        {/* Warning Notice when Demo Mode is active */}
        {report.isSyntheticDemo && (
          <div
            className="px-5 py-2 text-xs font-medium flex items-center gap-2 border-b"
            style={{
              backgroundColor: colors.badgeWarningBg,
              color: colors.badgeWarningText,
              borderColor: colors.border,
            }}
          >
            <FlaskConical className="w-4 h-4 flex-shrink-0" />
            <span>
              <strong>Simulated Synthetic Cohort (Demo Mode):</strong> Generated via IRT logistic ability model across 40 virtual candidates to preview psychometric distributions. Not mixed with real candidate attempts.
            </span>
          </div>
        )}

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-5 space-y-4">
          {/* Cohort Summary Metrics */}
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-3">
            {[
              { label: 'Cohort Size', value: `${report.candidateCount}`, sub: report.isSyntheticDemo ? 'Synthetic' : 'Real Attempts' },
              { label: 'Mean Score', value: report.meanScore.toFixed(2), sub: `Max ${maxPosMarks}` },
              { label: 'Median Score', value: report.medianScore.toFixed(2), sub: `Range ${report.lowestScore.toFixed(1)}..${report.highestScore.toFixed(1)}` },
              { label: 'Std Deviation', value: report.standardDeviation.toFixed(2), sub: 'Score Spread' },
              { label: "Cronbach's α", value: report.cronbachAlpha.toFixed(3), sub: report.cronbachAlpha >= 0.7 ? 'Reliable' : 'Low Consistency' },
              { label: 'Flagged Items', value: `${report.flaggedItemCount}`, sub: report.flaggedItemCount > 0 ? 'Review Keys' : 'Healthy Bank' },
            ].map((stat, i) => (
              <div
                key={i}
                className="p-3 rounded-xl border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                <div className="text-[11px] font-semibold" style={{ color: colors.muted }}>
                  {stat.label}
                </div>
                <div className="text-lg font-black mt-0.5" style={{ color: colors.inkBold }}>
                  {stat.value}
                </div>
                <div className="text-[10px]" style={{ color: colors.inkMuted }}>
                  {stat.sub}
                </div>
              </div>
            ))}
          </div>

          {/* Sub-navigation */}
          <div className="flex flex-wrap items-center justify-between gap-2 border-b pb-2" style={{ borderColor: colors.border }}>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setActiveSubTab('items')}
                className="px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5"
                style={
                  activeSubTab === 'items'
                    ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                    : { backgroundColor: colors.surfaceVariant, color: colors.ink }
                }
              >
                <BarChart3 className="w-3.5 h-3.5" />
                Item Quality Matrix ({report.items.length})
              </button>
              <button
                onClick={() => setActiveSubTab('candidates')}
                className="px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5"
                style={
                  activeSubTab === 'candidates'
                    ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                    : { backgroundColor: colors.surfaceVariant, color: colors.ink }
                }
              >
                <Users className="w-3.5 h-3.5" />
                Candidate Scorecards ({report.candidates.length})
              </button>
            </div>

            {activeSubTab === 'items' && (
              <label className="flex items-center gap-1.5 text-xs font-semibold cursor-pointer">
                <input
                  type="checkbox"
                  checked={flaggedOnly}
                  onChange={(e) => setFlaggedOnly(e.target.checked)}
                />
                <span>Show Flagged Items Only ({report.flaggedItemCount})</span>
              </label>
            )}
          </div>

          {/* Tab 1: Item Matrix */}
          {activeSubTab === 'items' && (
            <div className="overflow-x-auto border rounded-xl" style={{ borderColor: colors.border }}>
              <table className="w-full text-xs text-left border-collapse">
                <thead>
                  <tr style={{ backgroundColor: colors.surfaceVariant, color: colors.inkBold }}>
                    <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Q#</th>
                    <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Difficulty (p)</th>
                    <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Discrim (D)</th>
                    <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Point-Biserial</th>
                    <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Option Distractor Counts</th>
                    <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Audit Status</th>
                  </tr>
                </thead>
                <tbody>
                  {displayedItems.map((item) => {
                    const qObj = paper.questions.find((q) => q.num === item.questionNum);
                    const opts = report.hasOptionE ? ['A', 'B', 'C', 'D', 'E'] : ['A', 'B', 'C', 'D'];
                    return (
                      <tr
                        key={item.questionNum}
                        className="border-b last:border-b-0"
                        style={{
                          borderColor: colors.borderLight,
                          backgroundColor: item.isFlagged ? colors.redLight : 'transparent',
                        }}
                      >
                        <td className="px-3 py-2 font-bold">Q.{item.questionNum}</td>
                        <td className="px-3 py-2">
                          <span className="font-bold">{item.difficultyP.toFixed(2)}</span>{' '}
                          <span className="text-[10px]" style={{ color: colors.muted }}>
                            ({item.difficultyLabel})
                          </span>
                        </td>
                        <td className="px-3 py-2">
                          <span
                            className="font-bold"
                            style={{ color: item.discriminationD < 0 ? colors.red : colors.inkBold }}
                          >
                            {item.discriminationD.toFixed(2)}
                          </span>{' '}
                          <span className="text-[10px]" style={{ color: colors.muted }}>
                            ({item.discriminationLabel})
                          </span>
                        </td>
                        <td className="px-3 py-2 font-mono">
                          {item.pointBiserialR.toFixed(2)}
                        </td>
                        <td className="px-3 py-2">
                          <div className="flex flex-wrap gap-1.5">
                            {opts.map((opt) => {
                              const count = item.optionFrequencies[opt] ?? 0;
                              const isKey = (qObj?.correctAnswer || '').includes(opt);
                              return (
                                <span
                                  key={opt}
                                  className="px-1.5 py-0.5 rounded text-[10px] font-bold"
                                  style={
                                    isKey
                                      ? { backgroundColor: colors.badgeSuccessBg, color: colors.badgeSuccessText }
                                      : { backgroundColor: colors.surfaceVariant, color: colors.ink }
                                  }
                                >
                                  {opt}: {count}
                                </span>
                              );
                            })}
                          </div>
                        </td>
                        <td className="px-3 py-2 max-w-xs">
                          {item.isFlagged ? (
                            <div className="flex items-start gap-1 text-[11px]" style={{ color: colors.badgeDangerText }}>
                              <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
                              <span>{item.flagReason}</span>
                            </div>
                          ) : (
                            <span
                              className="inline-flex items-center gap-1 text-[11px] font-semibold"
                              style={{ color: colors.badgeSuccessText }}
                            >
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              Sound Item
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {/* Tab 2: Ranked Candidates */}
          {activeSubTab === 'candidates' && (
            <div className="overflow-x-auto border rounded-xl" style={{ borderColor: colors.border }}>
              <table className="w-full text-xs text-left border-collapse">
                <thead>
                  <tr style={{ backgroundColor: colors.surfaceVariant, color: colors.inkBold }}>
                    <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Rank</th>
                    <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Roll Number</th>
                    <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Booklet / Set</th>
                    <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Net Score</th>
                    <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>C / P / W / S</th>
                    <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Accuracy</th>
                  </tr>
                </thead>
                <tbody>
                  {report.candidates.map((c) => (
                    <tr
                      key={`${c.rollNumber}-${c.rank}`}
                      className="border-b last:border-b-0"
                      style={{ borderColor: colors.borderLight }}
                    >
                      <td className="px-3 py-2 font-black">#{c.rank}</td>
                      <td className="px-3 py-2 font-mono">{c.rollNumber}</td>
                      <td className="px-3 py-2 font-mono">
                        {c.bookletNumber} • Set {c.bookletCode}
                      </td>
                      <td className="px-3 py-2 font-bold" style={{ color: colors.teal }}>
                        {c.totalScore.toFixed(2)}
                      </td>
                      <td className="px-3 py-2 font-mono">
                        {c.correctCount} / {c.partialCount} / {c.incorrectCount} / {c.unansweredCount}
                      </td>
                      <td className="px-3 py-2 font-bold">{c.accuracyPercent}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default PsychometricAnalysisModal;
