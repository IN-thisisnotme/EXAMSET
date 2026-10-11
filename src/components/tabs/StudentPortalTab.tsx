import React, { useState, useEffect, useMemo } from 'react';
import { 
  CheckCircle2, 
  Clock, 
  Flag, 
  RotateCcw, 
  Check, 
  X, 
  AlertTriangle, 
  ArrowLeft, 
  ArrowRight, 
  Bookmark, 
  Award,
  ChevronRight,
  Eye,
  BarChart2
} from 'lucide-react';
import { QuestionPaper, CorrectOption, QuestionItem } from '../../types/exam';
import { parseAssertionReason, shouldStackOptions, isAssertionReason } from '../../utils/questionFormatter';

interface StudentPortalTabProps {
  currentPaper: QuestionPaper;
}

type QuestionStatus = 'answered' | 'unanswered' | 'marked' | 'marked-answered';

export const StudentPortalTab: React.FC<StudentPortalTabProps> = ({ currentPaper }) => {
  const [testActive, setTestActive] = useState<boolean>(false);
  const [testFinished, setTestFinished] = useState<boolean>(false);
  const [currentIndex, setCurrentIndex] = useState<number>(0);
  
  // Student answer state: questionId -> 'A' | 'B' | 'C' | 'D'
  const [selectedAnswers, setSelectedAnswers] = useState<Record<string, CorrectOption>>({});
  
  // Flagged for review state
  const [flaggedQuestions, setFlaggedQuestions] = useState<Record<string, boolean>>({});
  
  // Visited questions state
  const [visitedQuestions, setVisitedQuestions] = useState<Record<string, boolean>>({
    [currentPaper.questions[0]?.id || '']: true
  });

  // Timer state (seconds)
  const defaultDuration = Math.max(10, currentPaper.questions.length * 60); // 1 min per question
  const [timeLeft, setTimeLeft] = useState<number>(defaultDuration);
  const [timeSpent, setTimeSpent] = useState<number>(0);
  const [showSubmitModal, setShowSubmitModal] = useState<boolean>(false);
  const [reviewFilter, setReviewFilter] = useState<'all' | 'incorrect' | 'marked' | 'unattempted'>('all');

  const questions = currentPaper.questions;
  const currentQuestion: QuestionItem | undefined = questions[currentIndex];

  // Timer interval
  useEffect(() => {
    let interval: any = null;
    if (testActive && !testFinished && timeLeft > 0) {
      interval = setInterval(() => {
        setTimeLeft((prev) => {
          if (prev <= 1) {
            handleFinalSubmit();
            return 0;
          }
          return prev - 1;
        });
        setTimeSpent((prev) => prev + 1);
      }, 1000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [testActive, testFinished, timeLeft]);

  // Mark current question as visited
  useEffect(() => {
    if (currentQuestion && testActive) {
      setVisitedQuestions((prev) => ({
        ...prev,
        [currentQuestion.id]: true
      }));
    }
  }, [currentIndex, testActive, currentQuestion]);

  const handleStartTest = () => {
    setSelectedAnswers({});
    setFlaggedQuestions({});
    setVisitedQuestions({ [questions[0]?.id || '']: true });
    setTimeLeft(defaultDuration);
    setTimeSpent(0);
    setCurrentIndex(0);
    setTestActive(true);
    setTestFinished(false);
  };

  const handleSelectOption = (option: CorrectOption) => {
    if (!currentQuestion) return;
    setSelectedAnswers((prev) => ({
      ...prev,
      [currentQuestion.id]: option
    }));
  };

  const handleClearAnswer = () => {
    if (!currentQuestion) return;
    setSelectedAnswers((prev) => {
      const next = { ...prev };
      delete next[currentQuestion.id];
      return next;
    });
  };

  const handleToggleFlag = () => {
    if (!currentQuestion) return;
    setFlaggedQuestions((prev) => ({
      ...prev,
      [currentQuestion.id]: !prev[currentQuestion.id]
    }));
  };

  const handleFinalSubmit = () => {
    setShowSubmitModal(false);
    setTestFinished(true);
    setTestActive(false);
  };

  const formatTime = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // Evaluation computation
  const evaluation = useMemo(() => {
    let correctCount = 0;
    let incorrectCount = 0;
    let unattemptedCount = 0;

    questions.forEach((q) => {
      const studentAns = selectedAnswers[q.id];
      if (!studentAns) {
        unattemptedCount++;
      } else if (q.correctAnswer && studentAns === q.correctAnswer) {
        correctCount++;
      } else {
        incorrectCount++;
      }
    });

    const score = correctCount; // 1 mark per correct
    const maxScore = questions.length;
    const accuracy = (correctCount + incorrectCount) > 0 
      ? Math.round((correctCount / (correctCount + incorrectCount)) * 100) 
      : 0;

    return {
      correctCount,
      incorrectCount,
      unattemptedCount,
      score,
      maxScore,
      accuracy
    };
  }, [questions, selectedAnswers]);

  if (questions.length === 0) {
    return (
      <div className="text-center py-16 bg-white rounded-xl border border-slate-200">
        <AlertTriangle className="w-10 h-10 text-amber-500 mx-auto mb-3" />
        <h3 className="text-base font-bold text-slate-800">No Questions Available for Test</h3>
        <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
          Please add questions via the Question Creator or load a sample paper to launch the student test portal.
        </p>
      </div>
    );
  }

  // SCREEN 1: Test Start Screen
  if (!testActive && !testFinished) {
    return (
      <div className="max-w-3xl mx-auto bg-white rounded-xl border border-slate-200 shadow-sm p-6 sm:p-8 space-y-6">
        <div className="border-b border-slate-100 pb-5 text-center sm:text-left flex flex-col sm:flex-row items-center justify-between gap-4">
          <div>
            <span className="text-xs font-bold text-blue-600 bg-blue-50 px-2.5 py-1 rounded-full border border-blue-200">
              Computer-Based Test (CBT)
            </span>
            <h2 className="text-2xl font-black text-slate-900 mt-2">
              {currentPaper.metadata.title}
            </h2>
            <p className="text-xs text-slate-500 mt-1">
              {currentPaper.metadata.subject} • {currentPaper.metadata.setNumber}
            </p>
          </div>

          <div className="text-center bg-slate-50 p-3 rounded-lg border border-slate-200 min-w-[140px]">
            <div className="text-xs text-slate-500 font-medium">Duration</div>
            <div className="text-xl font-black text-slate-900">{formatTime(defaultDuration)}</div>
          </div>
        </div>

        {/* Exam Specifications */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
          <div className="p-3 bg-slate-50 rounded-lg border border-slate-100">
            <div className="text-lg font-black text-slate-800">{questions.length}</div>
            <div className="text-[11px] text-slate-500">Total Questions</div>
          </div>
          <div className="p-3 bg-slate-50 rounded-lg border border-slate-100">
            <div className="text-lg font-black text-slate-800">{questions.length}</div>
            <div className="text-[11px] text-slate-500">Max Marks</div>
          </div>
          <div className="p-3 bg-slate-50 rounded-lg border border-slate-100">
            <div className="text-lg font-black text-emerald-600">+1.0</div>
            <div className="text-[11px] text-slate-500">Correct Mark</div>
          </div>
          <div className="p-3 bg-slate-50 rounded-lg border border-slate-100">
            <div className="text-lg font-black text-rose-600">0.0</div>
            <div className="text-[11px] text-slate-500">Negative Mark</div>
          </div>
        </div>

        {/* General Candidate Instructions */}
        <div className="bg-slate-50 p-4 rounded-xl border border-slate-200 text-xs text-slate-600 space-y-2">
          <h4 className="font-bold text-slate-800 text-sm flex items-center gap-1.5">
            <CheckCircle2 className="w-4 h-4 text-blue-600" />
            Standard Test Instructions:
          </h4>
          <ul className="list-disc pl-5 space-y-1 text-slate-600">
            <li>The test clock will count down from the total allotted duration.</li>
            <li>Use the <strong>Question Palette</strong> on the right to jump directly to any question.</li>
            <li>You can mark questions for review using the <strong>Mark for Review</strong> toggle.</li>
            <li>To deselect an answer, click the <strong>Clear Response</strong> button.</li>
            <li>The test will automatically submit when the timer reaches zero.</li>
          </ul>
        </div>

        <div className="pt-2 flex justify-center sm:justify-end">
          <button
            onClick={handleStartTest}
            className="flex items-center gap-2 px-8 py-3 bg-blue-600 text-white font-bold text-sm rounded-xl hover:bg-blue-700 transition shadow-md"
          >
            <span>Start Practice Test Now</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    );
  }

  // SCREEN 2: Test Result & Performance Review
  if (testFinished) {
    const filteredQuestions = questions.filter(q => {
      const ans = selectedAnswers[q.id];
      if (reviewFilter === 'incorrect') return ans && ans !== q.correctAnswer;
      if (reviewFilter === 'unattempted') return !ans;
      if (reviewFilter === 'marked') return flaggedQuestions[q.id];
      return true;
    });

    return (
      <div className="space-y-6">
        {/* Score Card Hero Banner */}
        <div className="bg-slate-900 text-white p-6 sm:p-8 rounded-2xl shadow-md flex flex-col md:flex-row items-center justify-between gap-6">
          <div className="space-y-2 text-center md:text-left">
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-blue-500/20 text-blue-300 text-xs font-bold border border-blue-400/30">
              <Award className="w-3.5 h-3.5" />
              <span>Assessment Completed</span>
            </div>
            <h2 className="text-2xl sm:text-3xl font-black">{currentPaper.metadata.title}</h2>
            <p className="text-xs text-slate-300">
              Time Spent: {formatTime(timeSpent)} • {questions.length} Questions Evaluated
            </p>
          </div>

          <div className="flex items-center gap-4">
            <div className="text-center px-5 py-3 bg-white/10 rounded-xl border border-white/10 min-w-[110px]">
              <div className="text-3xl font-black text-emerald-400">
                {evaluation.score} <span className="text-sm font-normal text-slate-300">/ {evaluation.maxScore}</span>
              </div>
              <div className="text-[11px] font-semibold text-slate-300 mt-1">Final Score</div>
            </div>

            <div className="text-center px-5 py-3 bg-white/10 rounded-xl border border-white/10 min-w-[110px]">
              <div className="text-3xl font-black text-blue-400">
                {evaluation.accuracy}%
              </div>
              <div className="text-[11px] font-semibold text-slate-300 mt-1">Accuracy</div>
            </div>

            <button
              onClick={handleStartTest}
              className="flex items-center gap-1.5 px-4 py-3 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-xl transition shadow"
            >
              <RotateCcw className="w-4 h-4" />
              <span>Retake Test</span>
            </button>
          </div>
        </div>

        {/* Metrics Breakdown Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm text-center">
            <div className="text-2xl font-black text-emerald-600">{evaluation.correctCount}</div>
            <div className="text-xs font-semibold text-slate-600 mt-1">Correct Answers</div>
          </div>
          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm text-center">
            <div className="text-2xl font-black text-rose-600">{evaluation.incorrectCount}</div>
            <div className="text-xs font-semibold text-slate-600 mt-1">Incorrect Answers</div>
          </div>
          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm text-center">
            <div className="text-2xl font-black text-slate-500">{evaluation.unattemptedCount}</div>
            <div className="text-xs font-semibold text-slate-600 mt-1">Unattempted</div>
          </div>
          <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm text-center">
            <div className="text-2xl font-black text-purple-600">
              {Object.values(flaggedQuestions).filter(Boolean).length}
            </div>
            <div className="text-xs font-semibold text-slate-600 mt-1">Marked for Review</div>
          </div>
        </div>

        {/* Detailed Solutions Review Section */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-200 pb-4">
            <div>
              <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <BarChart2 className="w-5 h-5 text-blue-600" />
                Comprehensive Question Analysis & Pedagogical Review
              </h3>
              <p className="text-xs text-slate-500 mt-0.5">
                Review your selections side-by-side with official answer keys and detailed explanations.
              </p>
            </div>

            {/* Filter Buttons */}
            <div className="flex items-center gap-1.5 flex-wrap">
              {[
                { id: 'all', label: `All (${questions.length})` },
                { id: 'incorrect', label: `Incorrect (${evaluation.incorrectCount})` },
                { id: 'unattempted', label: `Unattempted (${evaluation.unattemptedCount})` },
                { id: 'marked', label: `Flagged (${Object.values(flaggedQuestions).filter(Boolean).length})` }
              ].map(tab => (
                <button
                  key={tab.id}
                  onClick={() => setReviewFilter(tab.id as any)}
                  className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                    reviewFilter === tab.id
                      ? 'bg-blue-600 text-white'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          {/* Questions Review List */}
          <div className="space-y-4">
            {filteredQuestions.map((q) => {
              const studentChoice = selectedAnswers[q.id];
              const isCorrect = studentChoice && studentChoice === q.correctAnswer;
              const isUnattempted = !studentChoice;

              return (
                <div 
                  key={q.id}
                  className={`p-4 rounded-xl border transition ${
                    isCorrect 
                      ? 'bg-emerald-50/40 border-emerald-200' 
                      : isUnattempted 
                      ? 'bg-slate-50 border-slate-200' 
                      : 'bg-rose-50/40 border-rose-200'
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="space-y-1.5 flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-wide">
                          Question {q.num}
                        </span>
                        <span className="text-[10px] font-semibold text-purple-700 bg-purple-50 px-1.5 py-0.5 rounded border border-purple-200">
                          {q.questionType || (isAssertionReason(q.question, q.options) ? 'Assertion–Reason' : 'Direct Fact')}
                        </span>
                      </div>

                      {(() => {
                        const ar = parseAssertionReason(q.question);
                        if (ar) {
                          return (
                            <div className="space-y-2 mt-1">
                              {ar.leadIn && (
                                <p className="text-xs font-medium text-slate-600 italic">
                                  {ar.leadIn}
                                </p>
                              )}
                              <div className="p-2.5 rounded-lg bg-amber-50 border border-amber-200 text-xs">
                                <span className="font-bold text-amber-900 mr-2">Assertion (A):</span>
                                <span className="text-slate-800 whitespace-pre-wrap">{ar.assertion}</span>
                              </div>
                              <div className="p-2.5 rounded-lg bg-indigo-50 border border-indigo-200 text-xs">
                                <span className="font-bold text-indigo-900 mr-2">Reason (R):</span>
                                <span className="text-slate-800 whitespace-pre-wrap">{ar.reason}</span>
                              </div>
                              {ar.leadOut && (
                                <p className="text-[11px] text-slate-500 italic">
                                  {ar.leadOut}
                                </p>
                              )}
                            </div>
                          );
                        }
                        return (
                          <h4 className="text-sm font-bold text-slate-900 leading-snug whitespace-pre-wrap">
                            {q.question}
                          </h4>
                        );
                      })()}
                    </div>

                    <div className="shrink-0">
                      {isCorrect && (
                        <span className="flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800 border border-emerald-300">
                          <Check className="w-3.5 h-3.5" /> Correct (+1)
                        </span>
                      )}
                      {!isCorrect && !isUnattempted && (
                        <span className="flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold bg-rose-100 text-rose-800 border border-rose-300">
                          <X className="w-3.5 h-3.5" /> Incorrect (0)
                        </span>
                      )}
                      {isUnattempted && (
                        <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-200 text-slate-700">
                          Not Attempted
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Options List */}
                  {(() => {
                    const effectiveType = q.questionType || (isAssertionReason(q.question, q.options) ? 'Assertion–Reason' : 'Direct Fact');
                    const isStacked = shouldStackOptions(effectiveType, q.options);

                    return (
                      <div className={`gap-2 mt-3 text-xs ${isStacked ? 'flex flex-col' : 'grid grid-cols-1 sm:grid-cols-2'}`}>
                        {(['A', 'B', 'C', 'D'] as CorrectOption[]).map((opt) => {
                          const optKey = opt.toLowerCase() as 'a' | 'b' | 'c' | 'd';
                          const optText = q.options[optKey];
                          const isSelected = studentChoice === opt;
                          const isActualKey = q.correctAnswer === opt;

                          let optClasses = 'bg-white border-slate-200 text-slate-700';
                          if (isActualKey) {
                            optClasses = 'bg-emerald-100/70 border-emerald-400 text-emerald-900 font-semibold';
                          } else if (isSelected && !isActualKey) {
                            optClasses = 'bg-rose-100/70 border-rose-400 text-rose-900 font-semibold';
                          }

                          return (
                            <div
                              key={opt}
                              className={`p-2.5 rounded-lg border flex items-start justify-between gap-2 ${optClasses}`}
                            >
                              <div className="flex items-start gap-2 flex-1 min-w-0">
                                <span className="font-bold shrink-0 mt-0.5">({opt})</span>
                                <span className="whitespace-pre-wrap break-words leading-relaxed">{optText}</span>
                              </div>

                              <div className="shrink-0 flex items-center gap-1 mt-0.5">
                                {isActualKey && (
                                  <span className="text-[10px] uppercase font-bold text-emerald-800 bg-emerald-200 px-1.5 py-0.5 rounded">
                                    Correct Key
                                  </span>
                                )}
                                {isSelected && !isActualKey && (
                                  <span className="text-[10px] uppercase font-bold text-rose-800 bg-rose-200 px-1.5 py-0.5 rounded">
                                    Your Choice
                                  </span>
                                )}
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    );
                  })()}

                  {/* Explanation Box */}
                  {q.explanation && (
                    <div className="mt-3 p-3 bg-blue-50/60 rounded-lg border border-blue-100 text-xs text-blue-950">
                      <div className="font-bold text-blue-900 flex items-center gap-1.5 mb-1">
                        <Eye className="w-3.5 h-3.5" />
                        <span>Pedagogical Explanation:</span>
                      </div>
                      <p className="leading-relaxed text-blue-900/90">{q.explanation}</p>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    );
  }

  // SCREEN 3: Active Computer-Based Test (CBT) View
  return (
    <div className="space-y-4">
      {/* Test Active Header Bar */}
      <div className="bg-slate-900 text-white px-4 py-3 rounded-xl shadow flex items-center justify-between">
        <div>
          <h3 className="font-bold text-sm truncate max-w-xs sm:max-w-md">
            {currentPaper.metadata.title}
          </h3>
          <p className="text-[11px] text-slate-400">
            Question {currentIndex + 1} of {questions.length}
          </p>
        </div>

        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 bg-slate-800 px-3 py-1.5 rounded-lg border border-slate-700">
            <Clock className={`w-4 h-4 ${timeLeft < 300 ? 'text-rose-400 animate-pulse' : 'text-blue-400'}`} />
            <span className={`font-mono text-sm font-bold ${timeLeft < 300 ? 'text-rose-400' : 'text-white'}`}>
              {formatTime(timeLeft)}
            </span>
          </div>

          <button
            onClick={() => setShowSubmitModal(true)}
            className="px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-lg transition"
          >
            Submit Exam
          </button>
        </div>
      </div>

      {/* Main CBT Workspace: Question Area + Question Palette */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Question Item & Option Cards */}
        <div className="lg:col-span-8 bg-white rounded-xl border border-slate-200 shadow-sm p-6 flex flex-col justify-between min-h-[480px]">
          <div className="space-y-6">
            {/* Question Header & Flags */}
            <div className="flex items-center justify-between pb-3 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold uppercase tracking-wider text-blue-600 bg-blue-50 px-2.5 py-1 rounded-md border border-blue-200">
                  Question {currentQuestion?.num}
                </span>
                <span className="text-[11px] font-semibold text-purple-700 bg-purple-50 px-2 py-0.5 rounded-full border border-purple-200">
                  {currentQuestion?.questionType || 'Direct Fact'}
                </span>
              </div>

              <button
                onClick={handleToggleFlag}
                className={`flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-semibold transition ${
                  flaggedQuestions[currentQuestion?.id || '']
                    ? 'bg-purple-100 text-purple-800 border border-purple-300'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                <Bookmark className="w-3.5 h-3.5" />
                <span>{flaggedQuestions[currentQuestion?.id || ''] ? 'Flagged for Review' : 'Mark for Review'}</span>
              </button>
            </div>

            {/* Question Stem */}
            {(() => {
              const ar = currentQuestion ? parseAssertionReason(currentQuestion.question) : null;
              if (ar) {
                return (
                  <div className="space-y-3">
                    {ar.leadIn && (
                      <p className="text-sm font-semibold text-slate-700 italic">
                        {ar.leadIn}
                      </p>
                    )}
                    <div className="p-4 rounded-xl bg-amber-50/80 border border-amber-200">
                      <div className="flex items-start gap-3">
                        <span className="shrink-0 px-2.5 py-1 rounded-md text-xs font-bold bg-amber-200 text-amber-900 border border-amber-300">
                          Assertion (A)
                        </span>
                        <p className="text-sm sm:text-base font-medium text-slate-900 leading-relaxed whitespace-pre-wrap flex-1">
                          {ar.assertion}
                        </p>
                      </div>
                    </div>
                    <div className="p-4 rounded-xl bg-indigo-50/80 border border-indigo-200">
                      <div className="flex items-start gap-3">
                        <span className="shrink-0 px-2.5 py-1 rounded-md text-xs font-bold bg-indigo-200 text-indigo-900 border border-indigo-300">
                          Reason (R)
                        </span>
                        <p className="text-sm sm:text-base font-medium text-slate-900 leading-relaxed whitespace-pre-wrap flex-1">
                          {ar.reason}
                        </p>
                      </div>
                    </div>
                    {ar.leadOut && (
                      <p className="text-xs text-slate-500 italic mt-1">
                        {ar.leadOut}
                      </p>
                    )}
                  </div>
                );
              }
              return (
                <div className="text-base sm:text-lg font-bold text-slate-900 leading-relaxed whitespace-pre-wrap">
                  {currentQuestion?.question}
                </div>
              );
            })()}

            {/* Interactive Option Cards */}
            {(() => {
              const effectiveType = currentQuestion?.questionType || (currentQuestion && isAssertionReason(currentQuestion.question, currentQuestion.options) ? 'Assertion–Reason' : 'Direct Fact');
              const isStacked = currentQuestion ? shouldStackOptions(effectiveType, currentQuestion.options) : true;

              return (
                <div className={`gap-2.5 ${isStacked ? 'flex flex-col' : 'grid grid-cols-1 sm:grid-cols-2'}`}>
                  {(['A', 'B', 'C', 'D'] as CorrectOption[]).map((opt) => {
                    const optKey = opt.toLowerCase() as 'a' | 'b' | 'c' | 'd';
                    const text = currentQuestion?.options[optKey] || '';
                    const isSelected = selectedAnswers[currentQuestion?.id || ''] === opt;

                    return (
                      <button
                        key={opt}
                        onClick={() => handleSelectOption(opt)}
                        className={`w-full p-4 rounded-xl border text-left flex items-start justify-between gap-3 transition ${
                          isSelected
                            ? 'bg-blue-50 border-blue-500 shadow-sm ring-1 ring-blue-400'
                            : 'bg-white hover:bg-slate-50 border-slate-200'
                        }`}
                      >
                        <div className="flex items-start gap-3 min-w-0 flex-1">
                          <div className={`w-7 h-7 rounded-full flex items-center justify-center font-bold text-xs shrink-0 mt-0.5 ${
                            isSelected ? 'bg-blue-600 text-white shadow-2xs' : 'bg-slate-100 text-slate-700 border border-slate-300'
                          }`}>
                            {opt}
                          </div>
                          <span className={`text-sm leading-relaxed whitespace-pre-wrap break-words flex-1 ${isSelected ? 'font-bold text-blue-950' : 'text-slate-800 font-medium'}`}>
                            {text}
                          </span>
                        </div>

                        <div className={`w-4 h-4 rounded-full border shrink-0 mt-1.5 flex items-center justify-center ${
                          isSelected ? 'border-blue-600 bg-blue-600' : 'border-slate-300'
                        }`}>
                          {isSelected && <Check className="w-2.5 h-2.5 text-white stroke-[3]" />}
                        </div>
                      </button>
                    );
                  })}
                </div>
              );
            })()}
          </div>

          {/* Bottom Action Footer */}
          <div className="pt-6 border-t border-slate-100 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <button
                onClick={handleClearAnswer}
                disabled={!selectedAnswers[currentQuestion?.id || '']}
                className="px-3 py-2 text-xs font-semibold text-slate-600 hover:text-slate-900 hover:bg-slate-100 rounded-lg transition disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Clear Response
              </button>
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={() => setCurrentIndex((prev) => Math.max(0, prev - 1))}
                disabled={currentIndex === 0}
                className="flex items-center gap-1.5 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold text-xs rounded-lg transition disabled:opacity-40 disabled:cursor-not-allowed"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
                Previous
              </button>

              <button
                onClick={() => setCurrentIndex((prev) => Math.min(questions.length - 1, prev + 1))}
                disabled={currentIndex === questions.length - 1}
                className="flex items-center gap-1.5 px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-lg transition disabled:opacity-40 disabled:cursor-not-allowed"
              >
                Next
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>

        {/* Right Column: Question Palette Grid */}
        <div className="lg:col-span-4 space-y-4">
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-4 space-y-4">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Question Palette
            </h4>

            {/* Status Legend */}
            <div className="grid grid-cols-2 gap-2 text-[11px] text-slate-600">
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-emerald-500"></span>
                <span>Answered ({Object.keys(selectedAnswers).length})</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-slate-200 border border-slate-300"></span>
                <span>Unanswered ({questions.length - Object.keys(selectedAnswers).length})</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-purple-500"></span>
                <span>Flagged ({Object.values(flaggedQuestions).filter(Boolean).length})</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-blue-600"></span>
                <span>Current</span>
              </div>
            </div>

            {/* Matrix of Question Buttons */}
            <div className="grid grid-cols-5 gap-2 max-h-[360px] overflow-y-auto pr-1">
              {questions.map((q, idx) => {
                const isSelected = !!selectedAnswers[q.id];
                const isFlagged = !!flaggedQuestions[q.id];
                const isCurrent = idx === currentIndex;

                let btnStyles = 'bg-slate-100 text-slate-700 border-slate-200';
                if (isCurrent) {
                  btnStyles = 'ring-2 ring-blue-600 font-black bg-blue-50 text-blue-900 border-blue-300';
                } else if (isFlagged && isSelected) {
                  btnStyles = 'bg-purple-600 text-white font-bold border-purple-700';
                } else if (isFlagged) {
                  btnStyles = 'bg-purple-100 text-purple-800 font-bold border-purple-300';
                } else if (isSelected) {
                  btnStyles = 'bg-emerald-600 text-white font-bold border-emerald-700';
                }

                return (
                  <button
                    key={q.id}
                    onClick={() => setCurrentIndex(idx)}
                    className={`h-9 rounded-lg border text-xs font-bold transition flex items-center justify-center relative ${btnStyles}`}
                  >
                    <span>{q.num}</span>
                    {isFlagged && (
                      <span className="absolute -top-1 -right-1 w-2 h-2 rounded-full bg-purple-500"></span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </div>

      {/* Final Submit Confirmation Modal */}
      {showSubmitModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-200 space-y-4 animate-scale-in">
            <h3 className="text-lg font-bold text-slate-900">Confirm Exam Submission</h3>
            <p className="text-xs text-slate-500">
              Are you sure you want to finish your test? Review your response summary before final evaluation:
            </p>

            <div className="bg-slate-50 p-4 rounded-xl space-y-2 text-xs">
              <div className="flex justify-between">
                <span className="text-slate-600">Total Questions:</span>
                <span className="font-bold text-slate-800">{questions.length}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-emerald-700 font-medium">Answered:</span>
                <span className="font-bold text-emerald-700">{Object.keys(selectedAnswers).length}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Unanswered:</span>
                <span className="font-bold text-slate-700">{questions.length - Object.keys(selectedAnswers).length}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-purple-700 font-medium">Flagged for Review:</span>
                <span className="font-bold text-purple-700">{Object.values(flaggedQuestions).filter(Boolean).length}</span>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                onClick={() => setShowSubmitModal(false)}
                className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-lg transition"
              >
                Resume Test
              </button>
              <button
                onClick={handleFinalSubmit}
                className="px-5 py-2 text-xs font-bold bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg transition shadow"
              >
                Yes, Submit Now
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default StudentPortalTab;
