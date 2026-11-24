import React, { useState } from 'react';
import { Check, ChevronDown, ChevronUp, Edit3, Key, Trash2, X, ArrowRight } from 'lucide-react';
import { QuestionItem } from '../types/exam';
import { useTheme } from '../theme/ThemeProvider';
import { AnswerKeyMatcher } from '../utils/answerKeyMatcher';

export interface ChangedOptionDiff {
  letter: string;
  oldText: string;
  newText: string;
}

export interface QuestionDiff {
  questionNum: number;
  questionText: string;
  originalQuestion?: QuestionItem | null;
  currentQuestion?: QuestionItem | null;
  isNew: boolean;
  isDeleted: boolean;
  answerKeyChanged: boolean;
  oldAnswerKey?: string | null;
  newAnswerKey?: string | null;
  stemChanged: boolean;
  oldStem?: string;
  newStem?: string;
  optionsChanged: boolean;
  changedOptions: ChangedOptionDiff[];
  explanationChanged: boolean;
  oldExplanation?: string | null;
  newExplanation?: string | null;
}

export function computeQuestionDiffs(
  originalList: QuestionItem[],
  currentList: QuestionItem[]
): QuestionDiff[] {
  const originalMap = new Map(originalList.map((q) => [q.num, q]));
  const currentMap = new Map(currentList.map((q) => [q.num, q]));
  const diffs: QuestionDiff[] = [];

  for (const current of currentList) {
    const original = originalMap.get(current.num);
    if (!original) {
      diffs.push({
        questionNum: current.num,
        questionText: current.question,
        originalQuestion: null,
        currentQuestion: current,
        isNew: true,
        isDeleted: false,
        answerKeyChanged: Boolean(current.correctAnswer),
        newAnswerKey: current.correctAnswer,
        stemChanged: true,
        newStem: current.question,
        optionsChanged: false,
        changedOptions: [],
        explanationChanged: Boolean(current.explanation),
        newExplanation: current.explanation,
      });
      continue;
    }

    const oldKey = AnswerKeyMatcher.normalizeKey(original.correctAnswer);
    const newKey = AnswerKeyMatcher.normalizeKey(current.correctAnswer);
    const keyChanged = oldKey !== newKey;
    const stemChanged = original.question.trim() !== current.question.trim();
    const expChanged = (original.explanation || '').trim() !== (current.explanation || '').trim();

    const changedOptions: ChangedOptionDiff[] = [];
    (['a', 'b', 'c', 'd', 'e'] as const).forEach((k) => {
      const oldOpt = (original.options[k] || '').trim();
      const newOpt = (current.options[k] || '').trim();
      if (oldOpt !== newOpt) {
        changedOptions.push({ letter: k.toUpperCase(), oldText: oldOpt, newText: newOpt });
      }
    });

    const flagChanged =
      Boolean(original.isVerified) !== Boolean(current.isVerified) ||
      Boolean(original.isNeutralized) !== Boolean(current.isNeutralized) ||
      (original.formula || '') !== (current.formula || '') ||
      (original.diagramLabel || '') !== (current.diagramLabel || '');

    if (keyChanged || stemChanged || expChanged || changedOptions.length > 0 || flagChanged) {
      diffs.push({
        questionNum: current.num,
        questionText: current.question,
        originalQuestion: original,
        currentQuestion: current,
        isNew: false,
        isDeleted: false,
        answerKeyChanged: keyChanged,
        oldAnswerKey: original.correctAnswer,
        newAnswerKey: current.correctAnswer,
        stemChanged,
        oldStem: original.question,
        newStem: current.question,
        optionsChanged: changedOptions.length > 0,
        changedOptions,
        explanationChanged: expChanged,
        oldExplanation: original.explanation,
        newExplanation: current.explanation,
      });
    }
  }

  for (const original of originalList) {
    if (!currentMap.has(original.num)) {
      diffs.push({
        questionNum: original.num,
        questionText: original.question,
        originalQuestion: original,
        currentQuestion: null,
        isNew: false,
        isDeleted: true,
        answerKeyChanged: false,
        oldAnswerKey: original.correctAnswer,
        stemChanged: false,
        oldStem: original.question,
        optionsChanged: false,
        changedOptions: [],
        explanationChanged: false,
      });
    }
  }

  return diffs.sort((a, b) => a.questionNum - b.questionNum);
}

interface ReviewChangesModalProps {
  isOpen: boolean;
  diffs: QuestionDiff[];
  onConfirmSave: () => void;
  onDiscard: () => void;
  onClose: () => void;
}

export const ReviewChangesModal: React.FC<ReviewChangesModalProps> = ({
  isOpen,
  diffs,
  onConfirmSave,
  onDiscard,
  onClose,
}) => {
  const { colors } = useTheme();
  const [expanded, setExpanded] = useState<Record<number, boolean>>({});

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.58)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-2xl max-h-[88vh] rounded-2xl shadow-2xl flex flex-col overflow-hidden"
        style={{
          backgroundColor: colors.surface,
          border: `1px solid ${colors.border}`,
          color: colors.ink,
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          className="px-5 py-4 flex items-center justify-between"
          style={{ borderBottom: `1px solid ${colors.border}` }}
        >
          <div className="flex items-center gap-2.5">
            <div
              className="w-9 h-9 rounded-lg flex items-center justify-center"
              style={{ backgroundColor: colors.tealLight, color: colors.teal }}
            >
              <Edit3 className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold" style={{ color: colors.inkBold }}>
                Review Alterations & Save
              </h3>
              <p className="text-[11px]" style={{ color: colors.muted }}>
                {diffs.length} question{diffs.length === 1 ? '' : 's'} modified • Full context preview before commit
              </p>
            </div>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg" style={{ color: colors.muted }} aria-label="Close">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {diffs.map((d) => {
            const isOpenOpts = expanded[d.questionNum] ?? true;
            const activeQ = d.currentQuestion || d.originalQuestion;
            return (
              <div
                key={`${d.questionNum}-${d.isDeleted}`}
                className="p-4 rounded-xl space-y-3"
                style={{
                  backgroundColor: colors.surfaceVariant,
                  border: `1px solid ${d.answerKeyChanged ? colors.amber : colors.border}`,
                }}
              >
                <div className="flex items-center justify-between flex-wrap gap-2">
                  <div className="flex items-center gap-2">
                    <span
                      className="px-2.5 py-0.5 rounded text-xs font-bold"
                      style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
                    >
                      Question {d.questionNum}
                    </span>
                    {d.isNew && (
                      <span
                        className="px-2 py-0.5 rounded text-[10px] font-bold"
                        style={{ backgroundColor: colors.badgeSuccessBg, color: colors.badgeSuccessText }}
                      >
                        + New
                      </span>
                    )}
                    {d.isDeleted && (
                      <span
                        className="px-2 py-0.5 rounded text-[10px] font-bold"
                        style={{ backgroundColor: colors.badgeDangerBg, color: colors.badgeDangerText }}
                      >
                        Deleted
                      </span>
                    )}
                    {d.answerKeyChanged && (
                      <span
                        className="px-2 py-0.5 rounded text-[10px] font-bold"
                        style={{ backgroundColor: colors.amberLight, color: colors.amber }}
                      >
                        Answer Key Changed
                      </span>
                    )}
                    {d.stemChanged && !d.isNew && (
                      <span
                        className="px-2 py-0.5 rounded text-[10px] font-bold"
                        style={{ backgroundColor: colors.tealLight, color: colors.tealDark }}
                      >
                        Stem Edited
                      </span>
                    )}
                  </div>
                </div>

                {/* Stem */}
                <div
                  className="p-2.5 rounded-lg text-xs space-y-1"
                  style={{ backgroundColor: colors.surface, border: `1px solid ${colors.borderLight}` }}
                >
                  {d.stemChanged && !d.isNew ? (
                    <>
                      <div className="line-through" style={{ color: colors.muted }}>
                        Original: {d.oldStem}
                      </div>
                      <div className="font-semibold" style={{ color: colors.inkBold }}>
                        Updated: {d.newStem}
                      </div>
                    </>
                  ) : (
                    <div className="font-semibold" style={{ color: colors.inkBold }}>
                      {d.questionText}
                    </div>
                  )}
                </div>

                {/* Key Diff */}
                {d.answerKeyChanged && (
                  <div
                    className="p-2.5 rounded-lg flex items-center gap-3 text-xs"
                    style={{ backgroundColor: colors.amberLight, border: `1px solid ${colors.amber}` }}
                  >
                    <Key className="w-4 h-4 flex-shrink-0" style={{ color: colors.amber }} />
                    <div className="flex items-center gap-2 font-bold">
                      <span className="px-2 py-0.5 rounded" style={{ backgroundColor: colors.surface, color: colors.ink }}>
                        Old: {d.oldAnswerKey || 'None'}
                      </span>
                      <ArrowRight className="w-3.5 h-3.5" style={{ color: colors.teal }} />
                      <span className="px-2 py-0.5 rounded text-white" style={{ backgroundColor: colors.green }}>
                        New: {d.newAnswerKey || 'None'}
                      </span>
                    </div>
                  </div>
                )}

                {/* Full Options Context */}
                {activeQ && !d.isDeleted && (
                  <div
                    className="rounded-lg p-2.5"
                    style={{ backgroundColor: colors.surface, border: `1px solid ${colors.borderLight}` }}
                  >
                    <button
                      type="button"
                      onClick={() => setExpanded((p) => ({ ...p, [d.questionNum]: !isOpenOpts }))}
                      className="w-full flex items-center justify-between text-[11px] font-bold"
                      style={{ color: colors.inkBold }}
                    >
                      <span>Options Context</span>
                      {isOpenOpts ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                    </button>
                    {isOpenOpts && (
                      <div className="mt-2 space-y-1">
                        {(['A', 'B', 'C', 'D', 'E'] as const).map((letter) => {
                          const txt = activeQ.options[letter.toLowerCase() as 'a' | 'b' | 'c' | 'd' | 'e'];
                          if (!txt && letter === 'E') return null;
                          const isKey = AnswerKeyMatcher.containsOption(activeQ.correctAnswer, letter);
                          const isEdited = d.changedOptions.some((c) => c.letter === letter);
                          return (
                            <div
                              key={letter}
                              className="px-2 py-1 rounded text-xs flex items-center justify-between gap-2"
                              style={{
                                backgroundColor: isKey ? colors.greenLight : 'transparent',
                                color: isKey ? colors.green : colors.ink,
                              }}
                            >
                              <span>
                                <strong>({letter})</strong> {txt || '—'}
                              </span>
                              {isKey && <span className="text-[10px] font-bold">✓ Key</span>}
                              {!isKey && isEdited && (
                                <span className="text-[10px] font-semibold" style={{ color: colors.teal }}>
                                  Edited
                                </span>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                )}

                {/* Explanation change */}
                {d.explanationChanged && (
                  <div
                    className="p-2.5 rounded-lg text-[11px]"
                    style={{ backgroundColor: colors.surface, border: `1px solid ${colors.borderLight}` }}
                  >
                    <span className="font-bold" style={{ color: colors.teal }}>
                      Explanation Updated:{' '}
                    </span>
                    <span style={{ color: colors.ink }}>{d.newExplanation || 'Cleared'}</span>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        <div
          className="px-5 py-3 flex items-center justify-between gap-2 flex-wrap"
          style={{ borderTop: `1px solid ${colors.border}`, backgroundColor: colors.surfaceVariant }}
        >
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onDiscard}
              className="flex items-center gap-1 px-3 py-2 rounded-lg text-xs font-bold border"
              style={{ borderColor: colors.red, color: colors.red, backgroundColor: colors.surface }}
            >
              <Trash2 className="w-3.5 h-3.5" />
              Discard Edits
            </button>
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-2 rounded-lg text-xs font-semibold"
              style={{ color: colors.muted }}
            >
              Keep Editing
            </button>
          </div>
          <button
            type="button"
            onClick={onConfirmSave}
            className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-bold text-white"
            style={{ backgroundColor: colors.teal }}
          >
            <Check className="w-4 h-4" />
            Confirm & Save Changes
          </button>
        </div>
      </div>
    </div>
  );
};

export default ReviewChangesModal;
