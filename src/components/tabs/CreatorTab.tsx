import React, { useMemo, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  Eye,
  FilePlus2,
  Layers,
  Lock,
  Play,
  Printer,
  Search,
  ShieldCheck,
  Sliders,
  Sparkles,
} from 'lucide-react';
import { QuestionPaper } from '../../types/exam';
import { useTheme } from '../../theme/ThemeProvider';
import { AnswerKeyMatcher } from '../../utils/answerKeyMatcher';
import { PaperLanguageDetector } from '../../utils/paperLanguageDetector';
import { getDisplayTypeLabel, shouldStackOptions } from '../../utils/questionFormatter';
import { StructuredQuestionView } from '../StructuredQuestionView';
import { LatexText } from '../LatexText';
import { EmptyPaperPromptCard } from '../EmptyPaperPromptCard';

interface CreatorTabProps {
  currentPaper: QuestionPaper | null;
  onSavePaper: (paper: QuestionPaper, silent?: boolean) => void;
  onNavigateToMaster: () => void;
  onNavigateToStudentPortal?: () => void;
  onNavigateToPrint?: () => void;
  onOpenCreateModal?: () => void;
  onOpenBatchImportModal?: () => void;
  onNavigateToVault?: () => void;
  onOpenEditMetadata?: () => void;
  onOpenAuditModal?: () => void;
}

export const CreatorTab: React.FC<CreatorTabProps> = ({
  currentPaper,
  onNavigateToMaster,
  onNavigateToStudentPortal,
  onNavigateToPrint,
  onOpenCreateModal,
  onOpenBatchImportModal,
  onNavigateToVault,
  onOpenEditMetadata,
  onOpenAuditModal,
}) => {
  const { colors, scalePx } = useTheme();
  const [inspectIdx, setInspectIdx] = useState<number>(0);
  const [inspectSearch, setInspectSearch] = useState<string>('');
  const [showAnswersInPreview, setShowAnswersInPreview] = useState<boolean>(false);

  const metrics = useMemo(() => {
    const qs = currentPaper?.questions || [];
    const total = qs.length;
    const withKeys = qs.filter((q) => Boolean(AnswerKeyMatcher.normalizeKey(q.correctAnswer))).length;
    const withExps = qs.filter((q) => Boolean(q.explanation?.trim())).length;
    const msqCount = qs.filter((q) => AnswerKeyMatcher.isMultiSelect(q)).length;
    const optECount = qs.filter((q) => Boolean(q.options.e?.trim())).length;
    const verifiedCount = qs.filter((q) => Boolean(q.isVerified)).length;
    const neutralizedCount = qs.filter((q) => Boolean(q.isNeutralized)).length;
    return { total, withKeys, withExps, msqCount, optECount, verifiedCount, neutralizedCount };
  }, [currentPaper]);

  const detectedLangs = useMemo(
    () =>
      currentPaper
        ? PaperLanguageDetector.detectLanguages(
            currentPaper.questions.slice(0, 8).map((q) => q.question).join('\n'),
            currentPaper.metadata.title
          )
        : ['English'],
    [currentPaper]
  );

  const filteredPreviewQuestions = useMemo(() => {
    const qs = currentPaper?.questions || [];
    const s = inspectSearch.trim().toLowerCase();
    if (!s) return qs;
    return qs.filter(
      (q) =>
        String(q.num) === s ||
        q.question.toLowerCase().includes(s) ||
        q.options.a.toLowerCase().includes(s) ||
        q.options.b.toLowerCase().includes(s)
    );
  }, [currentPaper, inspectSearch]);

  if (!currentPaper) {
    return (
      <EmptyPaperPromptCard
        title="Welcome to ExamSet Workbench"
        description="Start fresh just like Android: create your first MCQ/MSQ Question Set, batch-import .txt/.json files, or restore an existing .examset.zip / .examset.json backup."
        onCreateSet={onOpenCreateModal}
        onBatchImport={onOpenBatchImportModal}
        onOpenVault={onNavigateToVault}
      />
    );
  }

  const safeIdx = Math.min(inspectIdx, Math.max(0, filteredPreviewQuestions.length - 1));
  const activeQ = filteredPreviewQuestions[safeIdx];

  return (
    <div className="space-y-6">
      {/* Protected Active Exam Header Banner */}
      <div
        className="p-4 sm:p-5 rounded-2xl space-y-4"
        style={{ background: colors.heroGradient, color: colors.navyBannerText, boxShadow: colors.cardShadow }}
      >
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
          <div className="space-y-1 min-w-0 flex-1">
            <div className="flex items-center gap-2 flex-wrap">
              <span
                className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[0.6875rem] font-bold"
                style={{ backgroundColor: 'rgba(255,255,255,0.14)' }}
              >
                <Lock className="w-3 h-3" />
                Protected Exam Workbench
              </span>
              <span
                className="px-2 py-0.5 rounded text-[0.6875rem] font-mono font-bold"
                style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
              >
                Set {currentPaper.metadata.setNumber}
              </span>
              <span className="text-[0.6875rem] opacity-85">{detectedLangs.join(' + ')}</span>
            </div>
            <h2 className="text-lg font-black tracking-tight es-clamp-2" title={currentPaper.metadata.title}>
              {currentPaper.metadata.title}
            </h2>
            <p className="es-scroll-x text-xs opacity-85">
              {currentPaper.metadata.subject}
              {currentPaper.metadata.topic ? ` • ${currentPaper.metadata.topic}` : ''} • Roll{' '}
              <span className="font-mono">{currentPaper.metadata.rollNumber || '0000000001'}</span> • Booklet{' '}
              <span className="font-mono">{currentPaper.metadata.bookletNumber || '0000001'}</span>
            </p>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            {onOpenEditMetadata && (
              <button
                type="button"
                onClick={onOpenEditMetadata}
                className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold"
                style={{ backgroundColor: 'rgba(255,255,255,0.16)', color: '#FFFFFF' }}
              >
                <Sliders className="w-3.5 h-3.5" />
                Edit Metadata
              </button>
            )}
            {onOpenAuditModal && (
              <button
                type="button"
                onClick={onOpenAuditModal}
                className="flex items-center gap-1.5 px-3.5 py-2 min-h-[40px] rounded-lg text-xs font-bold shadow-sm transition hover:opacity-95"
                style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
              >
                <ShieldCheck className="w-4 h-4" />
                AI Audit Hub
              </button>
            )}
          </div>
        </div>

        {/* Quality Scorecard Row */}
        <div className="grid grid-cols-2 sm:grid-cols-6 gap-2.5 pt-2">
          {[
            { label: 'Questions', value: metrics.total },
            { label: 'Answer Keys', value: `${metrics.withKeys}/${metrics.total}` },
            { label: 'Explanations', value: `${metrics.withExps}/${metrics.total}` },
            { label: 'MSQ Items', value: metrics.msqCount },
            { label: 'Option E', value: metrics.optECount },
            { label: 'Verified Seals', value: metrics.verifiedCount },
          ].map((stat) => (
            <div
              key={stat.label}
              className="p-2.5 rounded-xl text-center"
              style={{ backgroundColor: 'rgba(255,255,255,0.10)' }}
            >
              <div className="text-base font-black tabular-nums">{stat.value}</div>
              <div className="text-[0.65625rem] opacity-85 font-semibold">{stat.label}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Safe Authoring & Workflow Actions */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
        {onOpenCreateModal && (
          <button
            type="button"
            onClick={onOpenCreateModal}
            className="p-4 rounded-xl text-left transition hover:shadow-md flex flex-col justify-between gap-2"
            style={{ backgroundColor: colors.tealLight, border: `1.5px solid ${colors.teal}` }}
          >
            <FilePlus2 className="w-5 h-5" style={{ color: colors.teal }} />
            <div>
              <div className="text-xs font-bold" style={{ color: colors.inkBold }}>
                Create New Question Set
              </div>
              <div className="text-[0.6875rem]" style={{ color: colors.inkMuted }}>
                Paste AI text/JSON safely without overwriting active paper
              </div>
            </div>
          </button>
        )}

        {onOpenBatchImportModal && (
          <button
            type="button"
            onClick={onOpenBatchImportModal}
            className="p-4 rounded-xl text-left transition hover:shadow-md flex flex-col justify-between gap-2"
            style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}` }}
          >
            <Layers className="w-5 h-5" style={{ color: colors.teal }} />
            <div>
              <div className="text-xs font-bold" style={{ color: colors.inkBold }}>
                Batch Import Files
              </div>
              <div className="text-[0.6875rem]" style={{ color: colors.muted }}>
                Bulk validate multiple .json / .txt papers & cohort IDs
              </div>
            </div>
          </button>
        )}

        <button
          type="button"
          onClick={onNavigateToMaster}
          className="p-4 rounded-xl text-left transition hover:shadow-md flex flex-col justify-between gap-2"
          style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}` }}
        >
          <Eye className="w-5 h-5" style={{ color: colors.teal }} />
          <div>
            <div className="text-xs font-bold" style={{ color: colors.inkBold }}>
              Master View & Diff Editor
            </div>
            <div className="text-[0.6875rem]" style={{ color: colors.muted }}>
              Edit keys, MSQ flags, formulas, & review alteration diffs
            </div>
          </div>
        </button>

        {onNavigateToStudentPortal && (
          <button
            type="button"
            onClick={onNavigateToStudentPortal}
            className="p-4 rounded-xl text-left transition hover:shadow-md flex flex-col justify-between gap-2"
            style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}` }}
          >
            <Play className="w-5 h-5" style={{ color: colors.green }} />
            <div>
              <div className="text-xs font-bold" style={{ color: colors.inkBold }}>
                Student Test Portal
              </div>
              <div className="text-[0.6875rem]" style={{ color: colors.muted }}>
                TCS CBT simulation, pacing analytics &amp; CTT psychometrics
              </div>
            </div>
          </button>
        )}

        {onNavigateToPrint && (
          <button
            type="button"
            onClick={onNavigateToPrint}
            className="p-4 rounded-xl text-left transition hover:shadow-md flex flex-col justify-between gap-2"
            style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}` }}
          >
            <Printer className="w-5 h-5" style={{ color: colors.amber }} />
            <div>
              <div className="text-xs font-bold" style={{ color: colors.inkBold }}>
                Print &amp; OMR Studio
              </div>
              <div className="text-[0.6875rem]" style={{ color: colors.muted }}>
                Generate printable A4 booklets, 1-page OMR &amp; CBT HTML
              </div>
            </div>
          </button>
        )}
      </div>

      {/* Read-Only Question Carousel & Diagnostics Preview */}
      <div
        className="rounded-2xl overflow-hidden"
        style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}` }}
      >
        <div
          className="px-4 py-3 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
          style={{ backgroundColor: colors.surfaceVariant, borderBottom: `1px solid ${colors.border}` }}
        >
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4" style={{ color: colors.teal }} />
            <h3 className="text-xs font-bold uppercase tracking-wider" style={{ color: colors.inkBold }}>
              Safe Read-Only Question Inspector
            </h3>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <div className="relative">
              <Search className="w-3.5 h-3.5 absolute left-2.5 top-2" style={{ color: colors.muted }} />
              <input
                type="text"
                value={inspectSearch}
                onChange={(e) => {
                  setInspectSearch(e.target.value);
                  setInspectIdx(0);
                }}
                placeholder="Find Q# or keyword..."
                className="pl-8 pr-3 py-1.5 rounded-lg text-xs focus:outline-none"
                style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
              />
            </div>

            <label className="flex items-center gap-1.5 text-xs font-semibold cursor-pointer">
              <input
                type="checkbox"
                checked={showAnswersInPreview}
                onChange={(e) => setShowAnswersInPreview(e.target.checked)}
              />
              <span>Reveal Keys & Solutions</span>
            </label>
          </div>
        </div>

        {activeQ ? (
          <div className="p-5 space-y-4">
            <div className="flex items-center justify-between gap-2 flex-wrap">
              <div className="flex items-center gap-2">
                <span
                  className="px-2.5 py-1 rounded-lg text-xs font-bold"
                  style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
                >
                  Q{activeQ.num} of {currentPaper.questions.length}
                </span>
                <span
                  className="px-2.5 py-1 rounded-lg text-[0.6875rem] font-bold"
                  style={{ backgroundColor: colors.blueLight, color: colors.tealDark }}
                >
                  {getDisplayTypeLabel(activeQ)}
                </span>
                {AnswerKeyMatcher.isMultiSelect(activeQ) && (
                  <span
                    className="px-2 py-0.5 rounded text-[0.625rem] font-bold"
                    style={{ backgroundColor: colors.purpleLight, color: colors.purple }}
                  >
                    MSQ
                  </span>
                )}
              </div>

              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  disabled={safeIdx === 0}
                  onClick={() => setInspectIdx(Math.max(0, safeIdx - 1))}
                  className="p-1.5 rounded-lg border disabled:opacity-40"
                  style={{ borderColor: colors.border, backgroundColor: colors.surfaceVariant, color: colors.ink }}
                  aria-label="Previous question"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span className="text-xs font-mono px-2" style={{ color: colors.muted }}>
                  {safeIdx + 1} / {filteredPreviewQuestions.length}
                </span>
                <button
                  type="button"
                  disabled={safeIdx >= filteredPreviewQuestions.length - 1}
                  onClick={() => setInspectIdx(Math.min(filteredPreviewQuestions.length - 1, safeIdx + 1))}
                  className="p-1.5 rounded-lg border disabled:opacity-40"
                  style={{ borderColor: colors.border, backgroundColor: colors.surfaceVariant, color: colors.ink }}
                  aria-label="Next question"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>

            <StructuredQuestionView
              questionText={activeQ.question}
              formula={activeQ.formula}
              diagramLabel={activeQ.diagramLabel}
              diagramUrl={activeQ.diagramUrl}
              isVerified={activeQ.isVerified}
              isNeutralized={activeQ.isNeutralized}
            />

            <div
              className={
                shouldStackOptions(activeQ.questionType, activeQ.options)
                  ? 'grid grid-cols-1 gap-2'
                  : 'grid grid-cols-1 sm:grid-cols-2 gap-2.5'
              }
            >
              {(['A', 'B', 'C', 'D', 'E'] as const).map((letter) => {
                const txt = activeQ.options[letter.toLowerCase() as 'a' | 'b' | 'c' | 'd' | 'e'];
                if (!txt && letter === 'E') return null;
                const isCorrect =
                  showAnswersInPreview && AnswerKeyMatcher.containsOption(activeQ.correctAnswer, letter);
                return (
                  <div
                    key={letter}
                    className="p-3 rounded-xl flex items-start gap-2.5"
                    style={{
                      backgroundColor: isCorrect ? colors.greenLight : colors.surfaceVariant,
                      border: `1px solid ${isCorrect ? colors.green : colors.border}`,
                      fontSize: scalePx(13.5),
                    }}
                  >
                    <span
                      className="w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0"
                      style={{
                        backgroundColor: isCorrect ? colors.green : colors.surface,
                        color: isCorrect ? colors.onAccentText : colors.inkBold,
                        border: isCorrect ? 'none' : `1px solid ${colors.border}`,
                      }}
                    >
                      {letter}
                    </span>
                    <div className="flex-1" style={{ color: isCorrect ? colors.green : colors.ink }}>
                      <LatexText text={txt || '—'} />
                    </div>
                  </div>
                );
              })}
            </div>

            {showAnswersInPreview && (
              <div
                className="p-3.5 rounded-xl text-xs space-y-1.5"
                style={{ backgroundColor: colors.blueLight, border: `1px solid ${colors.border}` }}
              >
                <div className="font-bold" style={{ color: colors.green }}>
                  Official Key: {AnswerKeyMatcher.formatKeyForDisplay(activeQ.correctAnswer)}
                </div>
                <div style={{ color: colors.ink }}>
                  <LatexText text={activeQ.explanation || 'No explanation provided for this question yet.'} />
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="p-8 text-center text-xs" style={{ color: colors.muted }}>
            No matching questions found in current filter.
          </div>
        )}
      </div>
    </div>
  );
};

export default CreatorTab;
