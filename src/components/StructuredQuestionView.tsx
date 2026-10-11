import React from 'react';
import { Check, Info, Sparkles, BookOpen } from 'lucide-react';
import { QuestionItem, CorrectOption, QuestionType } from '../types/exam';
import { parseAssertionReason, shouldStackOptions, isAssertionReason } from '../utils/questionFormatter';

interface StructuredQuestionViewProps {
  question: QuestionItem;
  questionNumber?: number;
  showAnswerIndicator?: boolean;
  showExplanation?: boolean;
  interactive?: boolean;
  selectedOption?: CorrectOption | null;
  onSelectOption?: (opt: CorrectOption) => void;
  className?: string;
}

export const StructuredQuestionView: React.FC<StructuredQuestionViewProps> = ({
  question,
  questionNumber,
  showAnswerIndicator = true,
  showExplanation = true,
  interactive = false,
  selectedOption,
  onSelectOption,
  className = ''
}) => {
  const arParts = parseAssertionReason(question.question);
  const isAr = arParts !== null || isAssertionReason(question.question, question.options, question.questionType);
  const qType: QuestionType = (question.questionType || (isAr ? 'Assertion–Reason' : 'Direct Fact')) as QuestionType;
  const isStacked = shouldStackOptions(qType, question.options);

  const num = questionNumber ?? question.num;
  const optionKeys: ('a' | 'b' | 'c' | 'd')[] = ['a', 'b', 'c', 'd'];

  return (
    <div className={`w-full text-left ${className}`}>
      {/* Question Header: Number & Type Badge */}
      <div className="flex flex-wrap items-center justify-between gap-2 mb-2.5">
        <div className="flex items-center gap-2">
          <span className="w-6 h-6 rounded-md bg-slate-900 text-white font-bold text-xs flex items-center justify-center shrink-0">
            {num}
          </span>
          <span className="text-xs font-bold text-slate-900">
            Question #{num}
          </span>
        </div>

        {qType && (
          <span className={`px-2.5 py-0.5 rounded-full text-[11px] font-semibold tracking-wide border shadow-2xs ${
            qType.includes('Assertion')
              ? 'bg-purple-50 text-purple-700 border-purple-200'
              : qType.includes('Match')
              ? 'bg-amber-50 text-amber-700 border-amber-200'
              : qType.includes('Statement')
              ? 'bg-indigo-50 text-indigo-700 border-indigo-200'
              : qType.includes('Numerical')
              ? 'bg-cyan-50 text-cyan-700 border-cyan-200'
              : qType.includes('Sequence') || qType.includes('Chronological')
              ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
              : 'bg-slate-100 text-slate-700 border-slate-200'
          }`}>
            {qType}
          </span>
        )}
      </div>

      {/* Question Stem Content */}
      <div className="space-y-2.5">
        {arParts ? (
          /* Specialized Assertion-Reason Structured Presentation */
          <div className="space-y-2.5">
            {arParts.leadIn && (
              <div className="text-xs sm:text-sm text-slate-700 font-medium whitespace-pre-wrap leading-relaxed bg-slate-50/70 p-2.5 rounded-lg border border-slate-200/60">
                {arParts.leadIn}
              </div>
            )}

            {/* Assertion (A) Card */}
            <div className="rounded-xl border border-sky-200 bg-sky-50/60 p-3 sm:p-3.5 transition hover:border-sky-300">
              <div className="flex items-start gap-3">
                <span className="shrink-0 px-2 py-0.5 rounded-md text-[10.5px] font-extrabold uppercase tracking-wider bg-sky-600 text-white shadow-2xs mt-0.5">
                  Assertion (A)
                </span>
                <div className="flex-1 min-w-0 text-xs sm:text-sm text-slate-900 font-medium whitespace-pre-wrap leading-relaxed break-words">
                  {arParts.assertion}
                </div>
              </div>
            </div>

            {/* Reason (R) Card */}
            <div className="rounded-xl border border-amber-200 bg-amber-50/60 p-3 sm:p-3.5 transition hover:border-amber-300">
              <div className="flex items-start gap-3">
                <span className="shrink-0 px-2 py-0.5 rounded-md text-[10.5px] font-extrabold uppercase tracking-wider bg-amber-600 text-white shadow-2xs mt-0.5">
                  Reason (R)
                </span>
                <div className="flex-1 min-w-0 text-xs sm:text-sm text-slate-900 font-medium whitespace-pre-wrap leading-relaxed break-words">
                  {arParts.reason}
                </div>
              </div>
            </div>

            {arParts.leadOut && (
              <div className="text-xs sm:text-sm text-slate-700 font-semibold italic mt-1 whitespace-pre-wrap leading-relaxed pl-1">
                {arParts.leadOut}
              </div>
            )}
          </div>
        ) : (
          /* Standard Question Body with preserved line breaks */
          <div className="text-xs sm:text-sm text-slate-900 font-medium whitespace-pre-wrap leading-relaxed break-words">
            {question.question}
          </div>
        )}
      </div>

      {/* Options Presentation with strict items-start alignment and line-break preservation */}
      <div className={isStacked ? 'flex flex-col space-y-2 mt-3.5' : 'grid grid-cols-1 sm:grid-cols-2 gap-2 mt-3.5'}>
        {optionKeys.map((key) => {
          const upperKey = key.toUpperCase() as CorrectOption;
          const optText = question.options[key] || '';
          const isCorrect = question.correctAnswer === upperKey;
          const isSelected = selectedOption === upperKey;

          return (
            <div
              key={key}
              onClick={() => interactive && onSelectOption && onSelectOption(upperKey)}
              className={`flex items-start gap-3 p-3 rounded-xl border text-left transition-all ${
                interactive ? 'cursor-pointer hover:bg-slate-50 active:scale-[0.99]' : ''
              } ${
                showAnswerIndicator && isCorrect
                  ? 'bg-emerald-50/80 border-emerald-300 shadow-2xs'
                  : isSelected
                  ? 'bg-blue-50 border-blue-400 ring-2 ring-blue-200'
                  : 'bg-white border-slate-200 hover:border-slate-300'
              }`}
            >
              {/* Option Letter Badge anchored at top-left, vertically aligned with first line of text */}
              <div
                className={`w-6 h-6 rounded-full flex items-center justify-center font-bold text-xs shrink-0 mt-0.5 transition-colors ${
                  showAnswerIndicator && isCorrect
                    ? 'bg-emerald-600 text-white'
                    : isSelected
                    ? 'bg-blue-600 text-white'
                    : 'bg-slate-100 text-slate-700 border border-slate-200'
                }`}
              >
                {upperKey}
              </div>

              {/* Option Text with natural multi-line wrapping and preserved line breaks */}
              <div className="flex-1 min-w-0 text-xs sm:text-sm text-slate-800 font-medium leading-relaxed whitespace-pre-wrap break-words">
                {optText}
              </div>

              {/* Correct Answer Pill */}
              {showAnswerIndicator && isCorrect && (
                <span className="shrink-0 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-emerald-600 text-white flex items-center gap-1 shadow-2xs mt-0.5">
                  <Check className="w-3 h-3 stroke-[2.5]" /> Correct
                </span>
              )}
            </div>
          );
        })}
      </div>

      {/* Pedagogical Explanation Card */}
      {showExplanation && question.explanation && (
        <div className="mt-3.5 pt-3 border-t border-slate-200/80">
          <div className="rounded-xl border border-slate-200 bg-slate-50/90 p-3 sm:p-3.5">
            <div className="flex items-center gap-1.5 text-xs font-bold text-slate-800 mb-1.5">
              <BookOpen className="w-3.5 h-3.5 text-blue-600" />
              <span>Explanation & Analysis</span>
            </div>
            <div className="text-xs text-slate-600 whitespace-pre-wrap leading-relaxed break-words font-medium">
              {question.explanation}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
