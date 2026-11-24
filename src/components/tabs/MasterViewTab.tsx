import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  Award,
  BookOpen,
  Check,
  CheckCircle2,
  Edit3,
  Key,
  Layers,
  Plus,
  Search,
  ShieldCheck,
  Sliders,
  Trash2,
  Upload,
} from 'lucide-react';
import { QuestionItem, QuestionPaper } from '../../types/exam';
import { useTheme } from '../../theme/ThemeProvider';
import { AnswerKeyMatcher } from '../../utils/answerKeyMatcher';
import { McqParser } from '../../utils/mcqParser';
import { shouldStackOptions } from '../../utils/questionFormatter';
import { StructuredQuestionView } from '../StructuredQuestionView';
import { LatexText } from '../LatexText';
import { computeQuestionDiffs, ReviewChangesModal } from '../ReviewChangesModal';

interface MasterViewTabProps {
  currentPaper: QuestionPaper;
  onUpdatePaper: (paper: QuestionPaper, silent?: boolean) => void;
  onOpenEditMetadata?: () => void;
  onOpenAuditModal?: () => void;
}

type MasterFilter = 'all' | 'missing-key' | 'missing-exp' | 'missing-option' | 'msq' | 'flagged';

export const MasterViewTab: React.FC<MasterViewTabProps> = ({
  currentPaper,
  onUpdatePaper,
  onOpenEditMetadata,
  onOpenAuditModal,
}) => {
  const { colors, scalePx } = useTheme();
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [filterMode, setFilterMode] = useState<MasterFilter>('all');

  // Protected Edit Mode state with rollback snapshot & Review Changes modal
  const [isEditMode, setIsEditMode] = useState<boolean>(false);
  const [snapshotQuestions, setSnapshotQuestions] = useState<QuestionItem[] | null>(null);
  const [draftQuestions, setDraftQuestions] = useState<QuestionItem[]>(currentPaper.questions);
  const [showReviewModal, setShowReviewModal] = useState<boolean>(false);

  // Bulk Key / Explanation Merge modal
  const [showBulkModal, setShowBulkModal] = useState<boolean>(false);
  const [bulkText, setBulkText] = useState<string>('');
  const [bulkStatusMsg, setBulkStatusMsg] = useState<string | null>(null);

  const activeQuestions = isEditMode ? draftQuestions : currentPaper.questions;

  const stats = useMemo(() => {
    const qs = activeQuestions;
    const total = qs.length;
    const missingKeys = qs.filter((q) => !AnswerKeyMatcher.normalizeKey(q.correctAnswer)).length;
    const missingExp = qs.filter((q) => !q.explanation || !q.explanation.trim()).length;
    const missingOpts = qs.filter((q) => !q.options.a.trim() || !q.options.b.trim() || !q.options.c.trim() || !q.options.d.trim()).length;
    const msqCount = qs.filter((q) => AnswerKeyMatcher.isMultiSelect(q)).length;
    const flaggedCount = qs.filter((q) => q.isNeutralized || !q.isVerified).length;
    return { total, missingKeys, missingExp, missingOpts, msqCount, flaggedCount };
  }, [activeQuestions]);

  const filteredQuestions = useMemo(() => {
    const s = searchQuery.trim().toLowerCase();
    return activeQuestions.filter((q) => {
      if (filterMode === 'missing-key' && AnswerKeyMatcher.normalizeKey(q.correctAnswer)) return false;
      if (filterMode === 'missing-exp' && q.explanation?.trim()) return false;
      if (filterMode === 'missing-option' && q.options.a && q.options.b && q.options.c && q.options.d) return false;
      if (filterMode === 'msq' && !AnswerKeyMatcher.isMultiSelect(q)) return false;
      if (filterMode === 'flagged' && !q.isNeutralized && q.isVerified) return false;

      if (!s) return true;
      return (
        String(q.num) === s ||
        q.question.toLowerCase().includes(s) ||
        q.options.a.toLowerCase().includes(s) ||
        q.options.b.toLowerCase().includes(s) ||
        q.options.c.toLowerCase().includes(s) ||
        q.options.d.toLowerCase().includes(s) ||
        (q.explanation || '').toLowerCase().includes(s)
      );
    });
  }, [activeQuestions, searchQuery, filterMode]);

  const diffs = useMemo(() => {
    if (!isEditMode || !snapshotQuestions) return [];
    return computeQuestionDiffs(snapshotQuestions, draftQuestions);
  }, [isEditMode, snapshotQuestions, draftQuestions]);

  const enterEditMode = () => {
    setSnapshotQuestions(JSON.parse(JSON.stringify(currentPaper.questions)));
    setDraftQuestions(JSON.parse(JSON.stringify(currentPaper.questions)));
    setIsEditMode(true);
  };

  const handleRequestFinishEdit = () => {
    if (diffs.length === 0) {
      setIsEditMode(false);
      setSnapshotQuestions(null);
      return;
    }
    setShowReviewModal(true);
  };

  const handleConfirmSaveEdits = () => {
    onUpdatePaper({
      ...currentPaper,
      questions: draftQuestions,
      metadata: { ...currentPaper.metadata, updatedAt: Date.now() },
    });
    setShowReviewModal(false);
    setIsEditMode(false);
    setSnapshotQuestions(null);
  };

  const handleDiscardEdits = () => {
    if (snapshotQuestions) setDraftQuestions(snapshotQuestions);
    setShowReviewModal(false);
    setIsEditMode(false);
    setSnapshotQuestions(null);
  };

  const updateDraftQ = (qNum: number, updater: (q: QuestionItem) => QuestionItem) => {
    setDraftQuestions((prev) => prev.map((q) => (q.num === qNum ? updater(q) : q)));
  };

  const handleToggleKey = (qNum: number, letter: string) => {
    updateDraftQ(qNum, (q) => {
      const isMsq = AnswerKeyMatcher.isMultiSelect(q);
      const nextKey = isMsq
        ? AnswerKeyMatcher.toggleSelection(q.correctAnswer, letter)
        : letter;
      return { ...q, correctAnswer: nextKey };
    });
  };

  const handleAddQuestion = () => {
    const nextNum = draftQuestions.reduce((m, q) => Math.max(m, q.num), 0) + 1;
    const newQ: QuestionItem = {
      id: `q-new-${Date.now()}`,
      num: nextNum,
      question: 'New question stem...',
      options: { a: 'Option A', b: 'Option B', c: 'Option C', d: 'Option D' },
      correctAnswer: 'A',
      explanation: '',
    };
    setDraftQuestions((prev) => [...prev, newQ]);
  };

  const handleDeleteDraftQ = (qNum: number) => {
    setDraftQuestions((prev) => prev.filter((q) => q.num !== qNum));
  };

  const handleDiagramUpload = (qNum: number, e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      const dataUrl = ev.target?.result as string;
      if (dataUrl) {
        updateDraftQ(qNum, (q) => ({
          ...q,
          diagramUrl: dataUrl,
          diagramLabel: q.diagramLabel || file.name,
        }));
      }
    };
    reader.readAsDataURL(file);
  };

  const handleApplyBulkMerge = () => {
    setBulkStatusMsg(null);
    if (!bulkText.trim()) return;
    const keysMap = McqParser.parseAnswerKeys(bulkText);
    const { explanations: expMap, derivedAnswers } = McqParser.parseExplanations(bulkText);

    if (keysMap.size === 0 && expMap.size === 0 && derivedAnswers.size === 0) {
      setBulkStatusMsg('No structured Answer Keys (e.g. "1: A,C") or Explanations ("1. Reasoning...") detected.');
      return;
    }

    let matched = 0;
    const updated = currentPaper.questions.map((q) => {
      const nextKey = keysMap.get(q.num) || derivedAnswers.get(q.num) || q.correctAnswer;
      const nextExp = expMap.get(q.num) || q.explanation;
      if (nextKey !== q.correctAnswer || nextExp !== q.explanation) matched++;
      return { ...q, correctAnswer: nextKey, explanation: nextExp };
    });

    onUpdatePaper({
      ...currentPaper,
      questions: updated,
      metadata: { ...currentPaper.metadata, updatedAt: Date.now() },
    });
    setBulkText('');
    setShowBulkModal(false);
    setBulkStatusMsg(`Updated ${matched} question(s).`);
  };

  return (
    <div className="space-y-5">
      <ReviewChangesModal
        isOpen={showReviewModal}
        diffs={diffs}
        onConfirmSave={handleConfirmSaveEdits}
        onDiscard={handleDiscardEdits}
        onClose={() => setShowReviewModal(false)}
      />

      {/* Top Banner */}
      <div
        className="p-4 sm:p-5 rounded-2xl flex flex-col lg:flex-row lg:items-center justify-between gap-4"
        style={{ background: colors.heroGradient, color: colors.navyBannerText, boxShadow: colors.cardShadow }}
      >
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2 text-xs font-bold opacity-85 uppercase tracking-wider">
            <Award className="w-4 h-4 flex-shrink-0" />
            <span className="truncate">Master Answer Key & Solutions Repository</span>
          </div>
          <h2 className="text-lg font-black mt-1 es-clamp-2" title={currentPaper.metadata.title}>
            {currentPaper.metadata.title}
          </h2>
          <p className="es-scroll-x text-xs opacity-85 mt-0.5">
            {currentPaper.metadata.subject} • Set {currentPaper.metadata.setNumber} • Marking: +
            {currentPaper.metadata.positiveMarks ?? 2} / -{currentPaper.metadata.negativeMarks ?? 0.66} •{' '}
            {currentPaper.metadata.msqScoringPolicy === 'PARTIAL_STRICT' ? 'MSQ Partial' : 'MSQ All-or-Nothing'}
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {!isEditMode ? (
            <>
              {onOpenEditMetadata && (
                <button
                  type="button"
                  onClick={onOpenEditMetadata}
                  className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold"
                  style={{ backgroundColor: 'rgba(255,255,255,0.14)', color: '#FFFFFF' }}
                >
                  <Sliders className="w-3.5 h-3.5" />
                  Metadata
                </button>
              )}
              <button
                type="button"
                onClick={() => setShowBulkModal(true)}
                className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold"
                style={{ backgroundColor: 'rgba(255,255,255,0.14)', color: '#FFFFFF' }}
              >
                <Key className="w-3.5 h-3.5" />
                Bulk Keys / Exp
              </button>
              {onOpenAuditModal && (
                <button
                  type="button"
                  onClick={onOpenAuditModal}
                  className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold shadow-sm transition hover:opacity-95"
                  style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
                >
                  <ShieldCheck className="w-4 h-4" />
                  3-Phase Audit
                </button>
              )}
              <button
                type="button"
                onClick={enterEditMode}
                className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold text-slate-900 bg-white shadow"
              >
                <Edit3 className="w-3.5 h-3.5" />
                Enter Edit Mode
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={handleAddQuestion}
                className="flex items-center gap-1 px-3 py-2 rounded-lg text-xs font-bold"
                style={{ backgroundColor: 'rgba(255,255,255,0.16)', color: '#FFFFFF' }}
              >
                <Plus className="w-3.5 h-3.5" />
                Add Question
              </button>
              <button
                type="button"
                onClick={handleDiscardEdits}
                className="px-3 py-2 rounded-lg text-xs font-bold text-white"
                style={{ backgroundColor: colors.red }}
              >
                Discard ({diffs.length})
              </button>
              <button
                type="button"
                onClick={handleRequestFinishEdit}
                className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-bold shadow-sm"
                style={{ backgroundColor: colors.green, color: colors.onAccentText }}
              >
                <Check className="w-4 h-4" />
                Review & Save ({diffs.length})
              </button>
            </>
          )}
        </div>
      </div>

      {/* Quick Jump Question Number Chips */}
      <div
        className="p-3 rounded-xl flex items-center gap-1.5 overflow-x-auto"
        style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}` }}
      >
        <span className="text-[0.6875rem] font-bold uppercase tracking-wider mr-1 flex-shrink-0" style={{ color: colors.muted }}>
          Jump:
        </span>
        {activeQuestions.map((q) => {
          const hasKey = Boolean(AnswerKeyMatcher.normalizeKey(q.correctAnswer));
          return (
            <a
              key={q.id}
              href={`#master-q-${q.num}`}
              className="px-2 py-1 rounded text-[0.6875rem] font-bold flex-shrink-0 transition"
              style={{
                backgroundColor: q.isNeutralized
                  ? colors.purpleLight
                  : !hasKey
                    ? colors.amberLight
                    : colors.surface,
                color: q.isNeutralized ? colors.purple : !hasKey ? colors.amber : colors.inkBold,
                border: `1px solid ${colors.border}`,
              }}
            >
              {q.num}
            </a>
          );
        })}
      </div>

      {/* Search & Filter Pills */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 absolute left-3 top-2.5" style={{ color: colors.muted }} />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search Q#, stem, options, or explanation..."
            className="w-full pl-9 pr-3 py-2 rounded-lg text-xs focus:outline-none"
            style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
          />
        </div>

        <div className="flex items-center gap-1.5 flex-wrap text-xs">
          {([
            { id: 'all', label: `All (${stats.total})` },
            { id: 'missing-key', label: `Missing Key (${stats.missingKeys})` },
            { id: 'missing-exp', label: `Missing Exp (${stats.missingExp})` },
            { id: 'missing-option', label: `Missing Opts (${stats.missingOpts})` },
            { id: 'msq', label: `MSQ (${stats.msqCount})` },
            { id: 'flagged', label: `Unsealed/Neutralized (${stats.flaggedCount})` },
          ] as { id: MasterFilter; label: string }[]).map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setFilterMode(tab.id)}
              className="px-2.5 py-1.5 rounded-lg font-bold transition"
              style={{
                backgroundColor: filterMode === tab.id ? colors.teal : colors.surfaceVariant,
                color: filterMode === tab.id ? colors.onAccentText : colors.ink,
              }}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Question Cards */}
      <div className="space-y-4">
        {filteredQuestions.map((q) => {
          const isMsq = AnswerKeyMatcher.isMultiSelect(q);
          const stacked = shouldStackOptions(q.questionType, q.options);

          return (
            <div
              key={q.id}
              id={`master-q-${q.num}`}
              className="p-5 rounded-2xl space-y-3.5"
              style={{
                backgroundColor: colors.surface,
                border: `1.5px solid ${q.isNeutralized ? colors.purple : colors.border}`,
              }}
            >
              {/* Card Header */}
              <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2 flex-wrap">
                  <span
                    className="px-2.5 py-1 rounded-lg text-xs font-black"
                    style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
                  >
                    Q{q.num}
                  </span>
                  {isMsq && (
                    <span
                      className="px-2 py-0.5 rounded text-[0.65625rem] font-bold"
                      style={{ backgroundColor: colors.purpleLight, color: colors.purple }}
                    >
                      MSQ • Multi-Select
                    </span>
                  )}
                  {q.isVerified && (
                    <span
                      className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[0.65625rem] font-bold"
                      style={{ backgroundColor: colors.badgeSuccessBg, color: colors.badgeSuccessText }}
                    >
                      <CheckCircle2 className="w-3 h-3" />
                      Verified
                    </span>
                  )}
                  {q.isNeutralized && (
                    <span
                      className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[0.65625rem] font-bold"
                      style={{ backgroundColor: colors.badgeWarningBg, color: colors.badgeWarningText }}
                    >
                      <AlertTriangle className="w-3 h-3" />
                      Neutralized
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-2">
                  <span
                    className="px-2.5 py-1 rounded-lg text-xs font-bold"
                    style={{
                      backgroundColor: AnswerKeyMatcher.normalizeKey(q.correctAnswer)
                        ? colors.greenLight
                        : colors.amberLight,
                      color: AnswerKeyMatcher.normalizeKey(q.correctAnswer) ? colors.green : colors.amber,
                    }}
                  >
                    Key: {AnswerKeyMatcher.formatKeyForDisplay(q.correctAnswer)}
                  </span>

                  {isEditMode && (
                    <>
                      <button
                        type="button"
                        onClick={() =>
                          updateDraftQ(q.num, (x) => ({
                            ...x,
                            questionType: isMsq ? 'conceptual' : 'multiple-select',
                          }))
                        }
                        className="px-2 py-1 rounded text-[0.6875rem] font-bold border"
                        style={{
                          borderColor: colors.border,
                          backgroundColor: isMsq ? colors.purpleLight : colors.surfaceVariant,
                          color: isMsq ? colors.purple : colors.ink,
                        }}
                      >
                        {isMsq ? 'Set Single-Choice' : 'Make MSQ'}
                      </button>
                      <button
                        type="button"
                        onClick={() => updateDraftQ(q.num, (x) => ({ ...x, isVerified: !x.isVerified }))}
                        className="px-2 py-1 rounded text-[0.6875rem] font-bold border"
                        style={{
                          borderColor: colors.border,
                          backgroundColor: q.isVerified ? colors.greenLight : colors.surfaceVariant,
                          color: q.isVerified ? colors.green : colors.ink,
                        }}
                      >
                        {q.isVerified ? 'Unseal' : 'Seal Verified'}
                      </button>
                      <button
                        type="button"
                        onClick={() => updateDraftQ(q.num, (x) => ({ ...x, isNeutralized: !x.isNeutralized }))}
                        className="px-2 py-1 rounded text-[0.6875rem] font-bold border"
                        style={{
                          borderColor: colors.border,
                          backgroundColor: q.isNeutralized ? colors.amberLight : colors.surfaceVariant,
                          color: q.isNeutralized ? colors.amber : colors.ink,
                        }}
                      >
                        {q.isNeutralized ? 'Restore Score' : 'Neutralize'}
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDeleteDraftQ(q.num)}
                        className="p-1.5 rounded"
                        style={{ color: colors.red }}
                        aria-label="Delete question"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </>
                  )}
                </div>
              </div>

              {/* Stem */}
              {isEditMode ? (
                <div className="space-y-2.5">
                  <textarea
                    value={q.question}
                    onChange={(e) => updateDraftQ(q.num, (x) => ({ ...x, question: e.target.value }))}
                    rows={3}
                    placeholder="Enter question stem text..."
                    className="w-full min-h-[5.5rem] resize-y p-3 rounded-xl text-xs font-medium leading-relaxed focus:outline-none"
                    style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
                  />
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
                    <input
                      type="text"
                      value={q.formula || ''}
                      onChange={(e) => updateDraftQ(q.num, (x) => ({ ...x, formula: e.target.value || null }))}
                      placeholder="Reference LaTeX formula (optional)"
                      className="px-3 py-1.5 rounded-lg font-mono focus:outline-none"
                      style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
                    />
                    <input
                      type="text"
                      value={q.diagramLabel || ''}
                      onChange={(e) => updateDraftQ(q.num, (x) => ({ ...x, diagramLabel: e.target.value || null }))}
                      placeholder="Diagram caption / label"
                      className="px-3 py-1.5 rounded-lg focus:outline-none"
                      style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
                    />
                    <label
                      className="cursor-pointer flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-lg font-semibold border"
                      style={{ borderColor: colors.border, backgroundColor: colors.blueLight, color: colors.teal }}
                    >
                      <Upload className="w-3.5 h-3.5" />
                      <span>{q.diagramUrl ? 'Replace Diagram Image' : 'Attach Diagram Image'}</span>
                      <input type="file" accept="image/*" onChange={(e) => handleDiagramUpload(q.num, e)} className="hidden" />
                    </label>
                  </div>
                </div>
              ) : (
                <StructuredQuestionView
                  questionText={q.question}
                  formula={q.formula}
                  diagramLabel={q.diagramLabel}
                  diagramUrl={q.diagramUrl}
                  isVerified={q.isVerified}
                  isNeutralized={q.isNeutralized}
                />
              )}

              {/* Options Grid */}
              <div className={stacked || isEditMode ? 'grid grid-cols-1 gap-2' : 'grid grid-cols-1 sm:grid-cols-2 gap-2.5'}>
                {(['A', 'B', 'C', 'D', 'E'] as const).map((letter) => {
                  const key = letter.toLowerCase() as 'a' | 'b' | 'c' | 'd' | 'e';
                  const txt = q.options[key] ?? '';
                  if (!isEditMode && letter === 'E' && !txt.trim()) return null;
                  if (isEditMode && letter === 'E' && q.options.e === undefined) {
                    return (
                      <button
                        key="add-e"
                        type="button"
                        onClick={() => updateDraftQ(q.num, (x) => ({ ...x, options: { ...x.options, e: '' } }))}
                        className="text-left text-xs font-bold px-3 py-1.5 rounded-lg border border-dashed"
                        style={{ borderColor: colors.border, color: colors.teal }}
                      >
                        + Add Option (E)
                      </button>
                    );
                  }

                  const isSelectedKey = AnswerKeyMatcher.containsOption(q.correctAnswer, letter);

                  return (
                    <div
                      key={letter}
                      className="p-2.5 rounded-xl flex items-center gap-2.5"
                      style={{
                        backgroundColor: isSelectedKey ? colors.greenLight : colors.surfaceVariant,
                        border: `1px solid ${isSelectedKey ? colors.green : colors.border}`,
                        fontSize: scalePx(13.5),
                      }}
                    >
                      <button
                        type="button"
                        disabled={!isEditMode}
                        onClick={() => isEditMode && handleToggleKey(q.num, letter)}
                        className={`w-7 h-7 ${isMsq ? 'rounded-md' : 'rounded-full'} flex items-center justify-center text-xs font-bold flex-shrink-0`}
                        style={{
                          backgroundColor: isSelectedKey ? colors.green : colors.surface,
                          color: isSelectedKey ? colors.onAccentText : colors.inkBold,
                          border: isSelectedKey ? 'none' : `1px solid ${colors.border}`,
                        }}
                        title={isEditMode ? `Toggle ${letter} as correct key` : undefined}
                      >
                        {letter}
                      </button>

                      {isEditMode ? (
                        <input
                          type="text"
                          value={txt}
                          onChange={(e) =>
                            updateDraftQ(q.num, (x) => ({
                              ...x,
                              options: { ...x.options, [key]: e.target.value },
                            }))
                          }
                          className="flex-1 px-2.5 py-1.5 rounded-lg text-xs focus:outline-none"
                          style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
                        />
                      ) : (
                        <div className="flex-1" style={{ color: isSelectedKey ? colors.green : colors.ink }}>
                          <LatexText text={txt || '—'} />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>

              {/* Explanation */}
              <div
                className="p-3.5 rounded-xl space-y-1.5"
                style={{ backgroundColor: colors.blueLight, border: `1px solid ${colors.border}` }}
              >
                <div className="text-[0.6875rem] font-bold uppercase tracking-wider flex items-center gap-1" style={{ color: colors.tealDark }}>
                  <BookOpen className="w-3.5 h-3.5" />
                  <span>Detailed Explanation</span>
                </div>
                {isEditMode ? (
                  <textarea
                    value={q.explanation || ''}
                    onChange={(e) => updateDraftQ(q.num, (x) => ({ ...x, explanation: e.target.value }))}
                    rows={3}
                    placeholder="Enter step-by-step pedagogical explanation..."
                    className="w-full min-h-[5.5rem] resize-y p-2.5 rounded-lg text-xs leading-relaxed focus:outline-none"
                    style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.inkBold }}
                  />
                ) : (
                  <div className="text-xs leading-relaxed" style={{ color: colors.ink }}>
                    <LatexText text={q.explanation || 'No explanation provided yet.'} />
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Bulk Keys / Explanations Merge Modal */}
      {showBulkModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.58)' }}
          onClick={() => setShowBulkModal(false)}
        >
          <div
            className="w-full max-w-xl max-h-[90dvh] flex flex-col rounded-2xl shadow-2xl overflow-hidden"
            style={{ backgroundColor: colors.surface, border: `1px solid ${colors.border}`, color: colors.ink }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="px-4 sm:px-5 py-3.5 sm:py-4 flex items-center justify-between gap-2 shrink-0" style={{ borderBottom: `1px solid ${colors.border}` }}>
              <div className="flex items-center gap-2 min-w-0">
                <Layers className="w-4 h-4 shrink-0" style={{ color: colors.teal }} />
                <h4 className="text-sm font-bold truncate" style={{ color: colors.inkBold }}>
                  Bulk Merge Answer Keys & Explanations
                </h4>
              </div>
              <button
                type="button"
                aria-label="Close modal"
                onClick={() => setShowBulkModal(false)}
                className="min-w-[40px] min-h-[40px] flex items-center justify-center rounded-lg"
                style={{ color: colors.muted }}
              >
                ✕
              </button>
            </div>
            <div className="p-4 sm:p-5 space-y-3 text-xs overflow-y-auto flex-1">
              <p style={{ color: colors.muted }}>
                Paste Answer Keys (e.g. <code>1. A</code>, <code>2: B, D</code>) and/or Detailed Explanations (<code>1. Reasoning...</code>) to batch-update questions cleanly.
              </p>
              <textarea
                value={bulkText}
                onChange={(e) => setBulkText(e.target.value)}
                rows={8}
                placeholder="Paste Answer Keys (e.g. 1. A, 2. B, C) and/or Detailed Explanation section..."
                className="w-full min-h-[11rem] resize-y p-3 rounded-xl font-mono text-xs leading-relaxed focus:outline-none"
                style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
              />
              {bulkStatusMsg && (
                <div className="font-semibold" style={{ color: colors.amber }}>
                  {bulkStatusMsg}
                </div>
              )}
            </div>
            <div
              className="px-4 sm:px-5 py-3 flex justify-end gap-2 shrink-0"
              style={{ borderTop: `1px solid ${colors.border}`, backgroundColor: colors.surfaceVariant }}
            >
              <button type="button" onClick={() => setShowBulkModal(false)} className="px-3 py-2 min-h-[44px] text-xs font-semibold" style={{ color: colors.muted }}>
                Cancel
              </button>
              <button
                type="button"
                onClick={handleApplyBulkMerge}
                className="px-4 py-2 min-h-[44px] rounded-lg text-xs font-bold"
                style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
              >
                Merge into Active Paper
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default MasterViewTab;
