import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  Award,
  BarChart2,
  Bookmark,
  Check,
  Clock,
  Eye,
  Grid,
  History,
  List,
  PauseCircle,
  Play,
  RotateCcw,
  Shuffle,
  Sliders,
  Target,
  Timer,
  X,
  Zap,
} from 'lucide-react';
import {
  ActiveExamSession,
  AttemptRecord,
  ExamInterfaceMode,
  ExamTimerMode,
  MsqScoringPolicy,
  QuestionItem,
  QuestionPaper,
} from '../../types/exam';
import { useTheme } from '../../theme/ThemeProvider';
import { ExamStore } from '../../data/examStore';
import { AnswerKeyMatcher } from '../../utils/answerKeyMatcher';
import { shouldStackOptions } from '../../utils/questionFormatter';
import { QuestionPacingAnalyzer } from '../../utils/psychometricEngine';
import { StructuredQuestionView } from '../StructuredQuestionView';
import { LatexText } from '../LatexText';
import { AttemptHistoryModal } from '../AttemptHistoryModal';
import { PsychometricAnalysisModal } from '../PsychometricAnalysisModal';

interface StudentPortalTabProps {
  currentPaper: QuestionPaper;
  attempts?: AttemptRecord[];
  onSaveAttempt?: (record: AttemptRecord) => void;
  onDeleteAttempt?: (attemptId: string) => void;
}

type PortalStage = 'OVERVIEW' | 'ACTIVE' | 'RESULTS';
type ReviewFilter = 'all' | 'correct' | 'partial' | 'incorrect' | 'unanswered' | 'marked';
type ResultsSubTab = 'solutions' | 'diagnostics';

function seededShuffle<T>(items: T[], seed: number): T[] {
  const arr = [...items];
  let state = Math.abs(seed) || 9876543;
  for (let i = arr.length - 1; i > 0; i--) {
    state = (state * 1664525 + 1013904223) % 4294967296;
    const j = state % (i + 1);
    const tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
  }
  return arr;
}

function formatClock(totalSeconds: number): string {
  const abs = Math.max(0, Math.floor(Math.abs(totalSeconds)));
  const hrs = Math.floor(abs / 3600);
  const mins = Math.floor((abs % 3600) / 60);
  const secs = abs % 60;
  if (hrs > 0) {
    return `${String(hrs).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

export const StudentPortalTab: React.FC<StudentPortalTabProps> = ({
  currentPaper,
  attempts: externalAttempts,
  onSaveAttempt,
  onDeleteAttempt,
}) => {
  const { colors, scalePx } = useTheme();

  // Internal fallback store if parent props not provided
  const [localAttempts, setLocalAttempts] = useState<AttemptRecord[]>(() =>
    ExamStore.loadAttempts(currentPaper.metadata.id)
  );
  const paperAttempts = useMemo(() => {
    if (externalAttempts) {
      return externalAttempts.filter((a) => a.paperId === currentPaper.metadata.id);
    }
    return localAttempts;
  }, [externalAttempts, localAttempts, currentPaper.metadata.id]);

  // Saved in-progress session
  const [savedSession, setSavedSession] = useState<ActiveExamSession | null>(() =>
    ExamStore.getActiveSession(currentPaper.metadata.id)
  );

  // Lobby configuration state
  const [interfaceMode, setInterfaceMode] = useState<ExamInterfaceMode>('TCS_ION_CBT');
  const [timerMode, setTimerMode] = useState<ExamTimerMode>('COUNTDOWN');
  const [customMinutes, setCustomMinutes] = useState<number>(
    currentPaper.metadata.durationMinutes || Math.max(15, currentPaper.questions.length * 2)
  );
  const [shuffleOptions, setShuffleOptions] = useState<boolean>(false);
  const [shuffleSeed, setShuffleSeed] = useState<number>(() => Date.now() % 100000);

  // Portal run state
  const [stage, setStage] = useState<PortalStage>('OVERVIEW');
  const [activeSubsetNums, setActiveSubsetNums] = useState<number[] | null>(null);
  const [currentIndex, setCurrentIndex] = useState<number>(0);
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [markedForReview, setMarkedForReview] = useState<Record<number, boolean>>({});
  const [visited, setVisited] = useState<Record<number, boolean>>({});
  const [questionTimes, setQuestionTimes] = useState<Record<number, number>>({});
  const [timeLeftSeconds, setTimeLeftSeconds] = useState<number>(0);
  const [elapsedSeconds, setElapsedSeconds] = useState<number>(0);
  const [targetDurationSeconds, setTargetDurationSeconds] = useState<number>(0);
  const [startedAt, setStartedAt] = useState<number>(Date.now());
  const [countdownOverlay, setCountdownOverlay] = useState<number>(0);
  const [localFontBias, setLocalFontBias] = useState<number>(0);
  const [paletteOpen, setPaletteOpen] = useState<boolean>(false);
  const [showSubmitModal, setShowSubmitModal] = useState<boolean>(false);

  // Results state
  const [lastSubmittedRecord, setLastSubmittedRecord] = useState<AttemptRecord | null>(null);
  const [resultsSubTab, setResultsSubTab] = useState<ResultsSubTab>('solutions');
  const [reviewFilter, setReviewFilter] = useState<ReviewFilter>('all');

  // Auxiliary modals
  const [showHistoryModal, setShowHistoryModal] = useState<boolean>(false);
  const [showPsychometricModal, setShowPsychometricModal] = useState<boolean>(false);

  // Sync when paper switches
  useEffect(() => {
    setStage('OVERVIEW');
    setActiveSubsetNums(null);
    setLastSubmittedRecord(null);
    setShowSubmitModal(false);
    setPaletteOpen(false);
    setCustomMinutes(
      currentPaper.metadata.durationMinutes || Math.max(15, currentPaper.questions.length * 2)
    );
    setSavedSession(ExamStore.getActiveSession(currentPaper.metadata.id));
    setLocalAttempts(ExamStore.loadAttempts(currentPaper.metadata.id));
  }, [currentPaper.metadata.id, currentPaper.questions.length, currentPaper.metadata.durationMinutes]);

  const activeQuestions: QuestionItem[] = useMemo(() => {
    if (!activeSubsetNums || activeSubsetNums.length === 0) return currentPaper.questions;
    const set = new Set(activeSubsetNums);
    return currentPaper.questions.filter((q) => set.has(q.num));
  }, [currentPaper.questions, activeSubsetNums]);

  const currentQuestion = activeQuestions[currentIndex];

  const posMarks = (currentPaper.metadata.positiveMarks ?? 0) > 0 ? currentPaper.metadata.positiveMarks! : 2.0;
  const negMarks = (currentPaper.metadata.negativeMarks ?? 0) >= 0 ? currentPaper.metadata.negativeMarks! : 0.66;
  const unkMarks = (currentPaper.metadata.unattendedMarks ?? 0) >= 0 ? currentPaper.metadata.unattendedMarks! : 0.0;
  const msqPolicy: MsqScoringPolicy = currentPaper.metadata.msqScoringPolicy || 'ALL_OR_NOTHING';

  // Build deterministic shuffled option order for any question
  const getDisplayOptionOrder = useCallback(
    (q: QuestionItem): { displayLabel: string; originalKey: string; text: string }[] => {
      const baseKeys = ['A', 'B', 'C', 'D'];
      if (q.options.e && q.options.e.trim().length > 0) baseKeys.push('E');
      const orderedOrig = shuffleOptions ? seededShuffle(baseKeys, shuffleSeed + q.num * 97) : baseKeys;
      return orderedOrig.map((orig, idx) => {
        const displayLabel = ['A', 'B', 'C', 'D', 'E'][idx];
        const lower = orig.toLowerCase() as 'a' | 'b' | 'c' | 'd' | 'e';
        return {
          displayLabel,
          originalKey: orig,
          text: q.options[lower] || '',
        };
      });
    },
    [shuffleOptions, shuffleSeed]
  );

  // Persist active session while test is running
  useEffect(() => {
    if (stage !== 'ACTIVE') return;
    const session: ActiveExamSession = {
      paperId: currentPaper.metadata.id,
      interfaceMode,
      timerMode,
      shuffleOptions,
      shuffleSeed,
      currentIndex,
      answers,
      markedForReview,
      visited,
      questionTimes,
      timeLeftSeconds,
      elapsedSeconds,
      targetDurationSeconds,
      startedAt,
    };
    ExamStore.saveActiveSession(currentPaper.metadata.id, session);
    setSavedSession(session);
  }, [
    stage,
    currentPaper.metadata.id,
    interfaceMode,
    timerMode,
    shuffleOptions,
    shuffleSeed,
    currentIndex,
    answers,
    markedForReview,
    visited,
    questionTimes,
    timeLeftSeconds,
    elapsedSeconds,
    targetDurationSeconds,
    startedAt,
  ]);

  // 3-2-1 Readiness Countdown timer
  useEffect(() => {
    if (stage !== 'ACTIVE' || countdownOverlay <= 0) return;
    const t = setTimeout(() => {
      setCountdownOverlay((prev) => Math.max(0, prev - 1));
    }, 1000);
    return () => clearTimeout(t);
  }, [stage, countdownOverlay]);

  // Submit handler
  const handleFinalSubmit = useCallback(() => {
    setShowSubmitModal(false);
    setPaletteOpen(false);

    let correctCount = 0;
    let partialCount = 0;
    let incorrectCount = 0;
    let unansweredCount = 0;
    let netScore = 0;

    for (const q of activeQuestions) {
      const res = AnswerKeyMatcher.scoreQuestion(
        q,
        answers[q.num],
        posMarks,
        negMarks,
        unkMarks,
        msqPolicy
      );
      if (res.status === 'CORRECT' || res.status === 'NEUTRALIZED') correctCount++;
      else if (res.status === 'PARTIAL') partialCount++;
      else if (res.status === 'INCORRECT') incorrectCount++;
      else unansweredCount++;
      netScore += res.marks;
    }

    const roundedScore = Math.round(netScore * 100) / 100;
    const maxScore = Math.round(activeQuestions.length * posMarks * 100) / 100;
    const attempted = correctCount + partialCount + incorrectCount;
    const accuracyPercent = attempted > 0 ? Math.round((correctCount / attempted) * 100) : 0;

    const record: AttemptRecord = {
      id: `att-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      paperId: currentPaper.metadata.id,
      paperTitle: currentPaper.metadata.title,
      subject: currentPaper.metadata.subject,
      rollNumber: currentPaper.metadata.rollNumber || '0000000001',
      bookletNumber: currentPaper.metadata.bookletNumber || '0000001',
      bookletCode: currentPaper.metadata.setNumber || '01',
      score: roundedScore,
      maxScore,
      correctCount,
      partialCount,
      incorrectCount,
      unansweredCount,
      accuracyPercent,
      timeSpentSeconds: elapsedSeconds,
      positiveMarks: posMarks,
      negativeMarks: negMarks,
      unattendedMarks: unkMarks,
      msqScoringPolicy: msqPolicy,
      submittedAnswers: { ...answers },
      questionTimes: { ...questionTimes },
      submittedAt: Date.now(),
    };

    if (!activeSubsetNums) {
      if (onSaveAttempt) {
        onSaveAttempt(record);
      } else {
        ExamStore.saveAttempt(record);
        setLocalAttempts(ExamStore.loadAttempts(currentPaper.metadata.id));
      }
    }

    ExamStore.saveActiveSession(currentPaper.metadata.id, null);
    setSavedSession(null);
    setLastSubmittedRecord(record);
    setResultsSubTab('solutions');
    setReviewFilter('all');
    setStage('RESULTS');
  }, [
    activeQuestions,
    activeSubsetNums,
    answers,
    currentPaper.metadata,
    elapsedSeconds,
    msqPolicy,
    negMarks,
    onSaveAttempt,
    posMarks,
    questionTimes,
    unkMarks,
  ]);

  // Main live exam clock + per-question pacing tick (only when visible and no overlay covering stem)
  useEffect(() => {
    if (stage !== 'ACTIVE' || countdownOverlay > 0 || showSubmitModal) return;

    const timer = setInterval(() => {
      if (typeof document !== 'undefined' && document.visibilityState !== 'visible') {
        return;
      }

      setElapsedSeconds((prev) => prev + 1);

      if (currentQuestion) {
        setQuestionTimes((prev) => ({
          ...prev,
          [currentQuestion.num]: (prev[currentQuestion.num] ?? 0) + 1,
        }));
      }

      if (timerMode === 'COUNTDOWN') {
        setTimeLeftSeconds((prev) => {
          if (prev <= 1) {
            handleFinalSubmit();
            return 0;
          }
          return prev - 1;
        });
      }
    }, 1000);

    return () => clearInterval(timer);
  }, [stage, countdownOverlay, showSubmitModal, timerMode, currentQuestion, handleFinalSubmit]);

  // Mark visited when navigating
  useEffect(() => {
    if (stage === 'ACTIVE' && currentQuestion) {
      setVisited((prev) => (prev[currentQuestion.num] ? prev : { ...prev, [currentQuestion.num]: true }));
    }
  }, [stage, currentQuestion]);

  const handleSelectChoice = useCallback((q: QuestionItem, originalKey: string) => {
    const isMsq = AnswerKeyMatcher.isMultiSelect(q);
    setVisited((prev) => ({ ...prev, [q.num]: true }));
    setAnswers((prev) => {
      if (isMsq) {
        const updated = AnswerKeyMatcher.toggleSelection(prev[q.num], originalKey);
        if (!updated) {
          const next = { ...prev };
          delete next[q.num];
          return next;
        }
        return { ...prev, [q.num]: updated };
      }
      return { ...prev, [q.num]: originalKey };
    });
  }, []);

  // Keyboard shortcuts for Desktop CBT speed
  useEffect(() => {
    if (stage !== 'ACTIVE' || countdownOverlay > 0 || showSubmitModal || interfaceMode !== 'TCS_ION_CBT') {
      return;
    }
    const handleKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      if (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) return;
      if (!currentQuestion) return;

      const opts = getDisplayOptionOrder(currentQuestion);
      const upper = e.key.toUpperCase();
      const idxFromDigit = ['1', '2', '3', '4', '5'].indexOf(e.key);
      const idxFromLetter = ['A', 'B', 'C', 'D', 'E'].indexOf(upper);
      const optIdx = idxFromDigit >= 0 ? idxFromDigit : idxFromLetter;

      if (optIdx >= 0 && optIdx < opts.length) {
        e.preventDefault();
        handleSelectChoice(currentQuestion, opts[optIdx].originalKey);
      } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        setCurrentIndex((i) => Math.min(activeQuestions.length - 1, i + 1));
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        setCurrentIndex((i) => Math.max(0, i - 1));
      } else if (upper === 'M') {
        e.preventDefault();
        setMarkedForReview((prev) => ({
          ...prev,
          [currentQuestion.num]: !prev[currentQuestion.num],
        }));
      } else if (e.key === 'Backspace') {
        e.preventDefault();
        setAnswers((prev) => {
          const next = { ...prev };
          delete next[currentQuestion.num];
          return next;
        });
      }
    };

    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [
    stage,
    countdownOverlay,
    showSubmitModal,
    interfaceMode,
    currentQuestion,
    activeQuestions.length,
    getDisplayOptionOrder,
    handleSelectChoice,
  ]);

  const handleStartNewTest = (subsetNums: number[] | null = null) => {
    const qList =
      subsetNums && subsetNums.length > 0
        ? currentPaper.questions.filter((q) => subsetNums.includes(q.num))
        : currentPaper.questions;
    if (qList.length === 0) return;

    const targetMins = subsetNums ? Math.max(5, qList.length * 2) : Math.max(1, customMinutes);
    const totalSecs = targetMins * 60;
    const newSeed = Date.now() % 100000;

    setActiveSubsetNums(subsetNums);
    setShuffleSeed(newSeed);
    setAnswers({});
    setMarkedForReview({});
    setVisited({ [qList[0].num]: true });
    setQuestionTimes({});
    setCurrentIndex(0);
    setTargetDurationSeconds(totalSecs);
    setTimeLeftSeconds(totalSecs);
    setElapsedSeconds(0);
    setStartedAt(Date.now());
    setCountdownOverlay(3);
    setStage('ACTIVE');
  };

  const handleResumeSession = () => {
    if (!savedSession) return;
    setActiveSubsetNums(null);
    setInterfaceMode(savedSession.interfaceMode);
    setTimerMode(savedSession.timerMode);
    setShuffleOptions(savedSession.shuffleOptions);
    setShuffleSeed(savedSession.shuffleSeed);
    setCurrentIndex(
      Math.min(savedSession.currentIndex, Math.max(0, currentPaper.questions.length - 1))
    );
    setAnswers(savedSession.answers || {});
    setMarkedForReview(savedSession.markedForReview || {});
    setVisited(savedSession.visited || {});
    setQuestionTimes(savedSession.questionTimes || {});
    setTimeLeftSeconds(savedSession.timeLeftSeconds);
    setElapsedSeconds(savedSession.elapsedSeconds);
    setTargetDurationSeconds(savedSession.targetDurationSeconds);
    setStartedAt(savedSession.startedAt);
    setCountdownOverlay(0);
    setStage('ACTIVE');
  };

  const handleClearResponse = (qNum: number) => {
    setAnswers((prev) => {
      const next = { ...prev };
      delete next[qNum];
      return next;
    });
  };

  const handleSaveAndNext = () => {
    if (currentIndex < activeQuestions.length - 1) {
      setCurrentIndex((i) => i + 1);
    } else {
      setShowSubmitModal(true);
    }
  };

  const handleMarkAndNext = () => {
    if (!currentQuestion) return;
    setMarkedForReview((prev) => ({ ...prev, [currentQuestion.num]: true }));
    if (currentIndex < activeQuestions.length - 1) {
      setCurrentIndex((i) => i + 1);
    }
  };

  const pacingSummary = useMemo(() => {
    return QuestionPacingAnalyzer.analyzePacing(
      { ...currentPaper, questions: activeQuestions },
      questionTimes,
      answers,
      posMarks,
      negMarks,
      unkMarks,
      msqPolicy
    );
  }, [currentPaper, activeQuestions, questionTimes, answers, posMarks, negMarks, unkMarks, msqPolicy]);

  // Latest saved attempt for Retry Wrong / Unattempted shortcuts from overview
  const latestAttempt = paperAttempts[0] || null;
  const wrongOrPartialNumsFromLatest = useMemo(() => {
    if (!latestAttempt) return [];
    return currentPaper.questions
      .filter((q) => {
        const res = AnswerKeyMatcher.scoreQuestion(
          q,
          latestAttempt.submittedAnswers[q.num],
          posMarks,
          negMarks,
          unkMarks,
          msqPolicy
        );
        return res.status === 'INCORRECT' || res.status === 'PARTIAL';
      })
      .map((q) => q.num);
  }, [latestAttempt, currentPaper.questions, posMarks, negMarks, unkMarks, msqPolicy]);

  const unattemptedNumsFromLatest = useMemo(() => {
    if (!latestAttempt) return [];
    return currentPaper.questions
      .filter((q) => !latestAttempt.submittedAnswers[q.num])
      .map((q) => q.num);
  }, [latestAttempt, currentPaper.questions]);

  if (currentPaper.questions.length === 0) {
    return (
      <div
        className="text-center py-16 rounded-xl border"
        style={{ backgroundColor: colors.surface, borderColor: colors.border }}
      >
        <AlertTriangle className="w-10 h-10 mx-auto mb-3" style={{ color: colors.amber }} />
        <h3 className="text-base font-bold" style={{ color: colors.inkBold }}>
          No Questions Available for Assessment
        </h3>
        <p className="text-xs mt-1 max-w-sm mx-auto" style={{ color: colors.muted }}>
          Parse or import questions in Exam Workbench or Library &amp; Folders first to launch the Test Portal.
        </p>
      </div>
    );
  }

  const renderModals = () => (
    <>
      <AttemptHistoryModal
        isOpen={showHistoryModal}
        onClose={() => setShowHistoryModal(false)}
        paper={currentPaper}
        attempts={paperAttempts}
        onDeleteAttempt={(id) => {
          if (onDeleteAttempt) {
            onDeleteAttempt(id);
          } else {
            ExamStore.deleteAttempt(id);
            setLocalAttempts(ExamStore.loadAttempts(currentPaper.metadata.id));
          }
        }}
      />
      <PsychometricAnalysisModal
        isOpen={showPsychometricModal}
        onClose={() => setShowPsychometricModal(false)}
        paper={currentPaper}
        realAttempts={paperAttempts}
      />
    </>
  );

  // =========================================================================
  // SCREEN 1: OVERVIEW & LAUNCH CONSOLE
  // =========================================================================
  if (stage === 'OVERVIEW') {
    const msqCount = currentPaper.questions.filter((q) => AnswerKeyMatcher.isMultiSelect(q)).length;
    const optECount = currentPaper.questions.filter((q) => Boolean(q.options.e?.trim())).length;

    return (
      <div className="space-y-6">
        {renderModals()}

        <div
          className="max-w-4xl mx-auto rounded-2xl border shadow-sm overflow-hidden"
          style={{ backgroundColor: colors.surface, borderColor: colors.border }}
        >
          {/* Banner Header */}
          <div
            className="p-6 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4"
            style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
          >
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-[0.6875rem] font-bold uppercase tracking-wider px-2.5 py-0.5 rounded-full bg-white/15">
                  CBT & Continuous Test Portal
                </span>
                <span className="text-[0.6875rem] font-mono px-2 py-0.5 rounded bg-white/10">
                  Set {currentPaper.metadata.setNumber || '01'}
                </span>
                {msqCount > 0 && (
                  <span className="text-[0.6875rem] font-bold px-2 py-0.5 rounded bg-amber-400/20 text-amber-200">
                    {msqCount} MSQ
                  </span>
                )}
                {optECount > 0 && (
                  <span className="text-[0.6875rem] font-bold px-2 py-0.5 rounded bg-teal-400/20 text-teal-100">
                    5 Options (A–E)
                  </span>
                )}
              </div>
              <h2 className="text-xl sm:text-2xl font-black mt-2 es-clamp-2" title={currentPaper.metadata.title}>
                {currentPaper.metadata.title}
              </h2>
              <p className="es-scroll-x text-xs opacity-85 mt-1">
                {currentPaper.metadata.subject}
                {currentPaper.metadata.topic ? ` • ${currentPaper.metadata.topic}` : ''} • Roll:{' '}
                {currentPaper.metadata.rollNumber || '0000000001'} • Booklet:{' '}
                {currentPaper.metadata.bookletNumber || '0000001'}
              </p>
            </div>

            <div className="flex items-center gap-2 flex-wrap">
              <button
                onClick={() => setShowHistoryModal(true)}
                className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold bg-white/15 hover:bg-white/25 transition"
              >
                <History className="w-3.5 h-3.5" />
                <span>Attempts ({paperAttempts.length})</span>
              </button>
              <button
                onClick={() => setShowPsychometricModal(true)}
                className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-bold bg-white/15 hover:bg-white/25 transition"
              >
                <Activity className="w-3.5 h-3.5" />
                <span>Psychometrics (CTT)</span>
              </button>
            </div>
          </div>

          <div className="p-6 space-y-6">
            {/* Resume Banner */}
            {savedSession && (
              <div
                className="p-4 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                style={{ backgroundColor: colors.tealLight, borderColor: colors.teal }}
              >
                <div className="space-y-0.5">
                  <div className="text-xs font-bold flex items-center gap-1.5" style={{ color: colors.tealDark }}>
                    <PauseCircle className="w-4 h-4" />
                    Paused Examination Session Found
                  </div>
                  <p className="text-[0.6875rem]" style={{ color: colors.ink }}>
                    {Object.keys(savedSession.answers || {}).length} of {currentPaper.questions.length} answered • Elapsed{' '}
                    {formatClock(savedSession.elapsedSeconds)}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => {
                      ExamStore.saveActiveSession(currentPaper.metadata.id, null);
                      setSavedSession(null);
                    }}
                    className="px-3 py-1.5 rounded-lg text-xs font-semibold border"
                    style={{ backgroundColor: colors.surface, color: colors.red, borderColor: colors.border }}
                  >
                    Discard
                  </button>
                  <button
                    onClick={handleResumeSession}
                    className="flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-xs font-bold"
                    style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
                  >
                    <Play className="w-3.5 h-3.5" />
                    Resume Session
                  </button>
                </div>
              </div>
            )}

            {/* Marking Scheme Grid */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
              <div
                className="p-3 rounded-xl border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                <div className="text-lg font-black" style={{ color: colors.inkBold }}>
                  {currentPaper.questions.length}
                </div>
                <div className="text-[0.6875rem]" style={{ color: colors.muted }}>
                  Total Questions
                </div>
              </div>
              <div
                className="p-3 rounded-xl border"
                style={{ backgroundColor: colors.badgeSuccessBg, borderColor: colors.border }}
              >
                <div className="text-lg font-black" style={{ color: colors.badgeSuccessText }}>
                  +{posMarks}
                </div>
                <div className="text-[0.6875rem]" style={{ color: colors.badgeSuccessText }}>
                  Correct Mark
                </div>
              </div>
              <div
                className="p-3 rounded-xl border"
                style={{ backgroundColor: colors.badgeDangerBg, borderColor: colors.border }}
              >
                <div className="text-lg font-black" style={{ color: colors.badgeDangerText }}>
                  -{negMarks}
                </div>
                <div className="text-[0.6875rem]" style={{ color: colors.badgeDangerText }}>
                  Negative Penalty
                </div>
              </div>
              <div
                className="p-3 rounded-xl border"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                <div className="text-xs font-black mt-1" style={{ color: colors.inkBold }}>
                  {msqPolicy === 'PARTIAL_STRICT' ? 'MSQ Partial' : 'MSQ All/Nothing'}
                </div>
                <div className="text-[0.6875rem] mt-0.5" style={{ color: colors.muted }}>
                  Skip: {unkMarks > 0 ? `-${unkMarks}` : '0'}
                </div>
              </div>
            </div>

            {/* Configuration Controls */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Interface Mode */}
              <div
                className="p-4 rounded-xl border space-y-3"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                <div className="text-xs font-bold uppercase tracking-wider flex items-center gap-1.5" style={{ color: colors.muted }}>
                  <Sliders className="w-3.5 h-3.5" />
                  Exam Interface Mode
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setInterfaceMode('TCS_ION_CBT')}
                    className="p-3 rounded-xl border text-left transition"
                    style={
                      interfaceMode === 'TCS_ION_CBT'
                        ? { backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.inkBold }
                        : { backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }
                    }
                  >
                    <div className="flex items-center gap-1.5 text-xs font-bold">
                      <Grid className="w-3.5 h-3.5" />
                      Standard CBT Mode
                    </div>
                    <div className="text-[0.625rem] mt-1" style={{ color: colors.muted }}>
                      1 question/screen, 5-state palette & shortcuts
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setInterfaceMode('SCROLLABLE_PAPER')}
                    className="p-3 rounded-xl border text-left transition"
                    style={
                      interfaceMode === 'SCROLLABLE_PAPER'
                        ? { backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.inkBold }
                        : { backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }
                    }
                  >
                    <div className="flex items-center gap-1.5 text-xs font-bold">
                      <List className="w-3.5 h-3.5" />
                      Scrollable Paper
                    </div>
                    <div className="text-[0.625rem] mt-1" style={{ color: colors.muted }}>
                      Full paper flow with inline answers
                    </div>
                  </button>
                </div>

                <label className="flex items-center justify-between pt-2 border-t cursor-pointer text-xs" style={{ borderColor: colors.borderLight }}>
                  <span className="flex items-center gap-1.5 font-semibold" style={{ color: colors.inkBold }}>
                    <Shuffle className="w-3.5 h-3.5" style={{ color: colors.teal }} />
                    Shuffle Options Deterministically
                  </span>
                  <input
                    type="checkbox"
                    checked={shuffleOptions}
                    onChange={(e) => setShuffleOptions(e.target.checked)}
                  />
                </label>
              </div>

              {/* Timer Mode */}
              <div
                className="p-4 rounded-xl border space-y-3"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                <div className="text-xs font-bold uppercase tracking-wider flex items-center gap-1.5" style={{ color: colors.muted }}>
                  <Timer className="w-3.5 h-3.5" />
                  Clock & Timing Mode
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setTimerMode('COUNTDOWN')}
                    className="p-3 rounded-xl border text-left transition"
                    style={
                      timerMode === 'COUNTDOWN'
                        ? { backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.inkBold }
                        : { backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }
                    }
                  >
                    <div className="text-xs font-bold">Timed Countdown</div>
                    <div className="text-[0.625rem] mt-1" style={{ color: colors.muted }}>
                      Auto-submits when clock hits zero
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setTimerMode('OPEN_STOPWATCH')}
                    className="p-3 rounded-xl border text-left transition"
                    style={
                      timerMode === 'OPEN_STOPWATCH'
                        ? { backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.inkBold }
                        : { backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }
                    }
                  >
                    <div className="text-xs font-bold">Open Stopwatch</div>
                    <div className="text-[0.625rem] mt-1" style={{ color: colors.muted }}>
                      Counts up; alerts past target time
                    </div>
                  </button>
                </div>

                <div className="flex items-center justify-between gap-2 pt-2 border-t text-xs" style={{ borderColor: colors.borderLight }}>
                  <span className="font-semibold" style={{ color: colors.ink }}>
                    {timerMode === 'COUNTDOWN' ? 'Duration (Minutes):' : 'Soft Target (Minutes):'}
                  </span>
                  <div className="flex items-center gap-1.5">
                    {[15, 30, 60, 120].map((m) => (
                      <button
                        key={m}
                        type="button"
                        onClick={() => setCustomMinutes(m)}
                        className="px-2 py-0.5 rounded border text-[0.6875rem] font-bold"
                        style={
                          customMinutes === m
                            ? { backgroundColor: colors.teal, color: colors.onAccentText, borderColor: colors.teal }
                            : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
                        }
                      >
                        {m}m
                      </button>
                    ))}
                    <input
                      type="number"
                      min={1}
                      max={360}
                      value={customMinutes}
                      onChange={(e) => setCustomMinutes(Math.max(1, Number(e.target.value) || 1))}
                      className="w-16 px-2 py-1 rounded border text-xs font-bold text-center"
                      style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* Targeted Practice Shortcuts if prior attempt exists */}
            {latestAttempt && (wrongOrPartialNumsFromLatest.length > 0 || unattemptedNumsFromLatest.length > 0) && (
              <div
                className="p-3.5 rounded-xl border flex flex-wrap items-center justify-between gap-2"
                style={{ backgroundColor: colors.blueLight, borderColor: colors.border }}
              >
                <div className="flex items-center gap-2 text-xs font-semibold" style={{ color: colors.inkBold }}>
                  <Target className="w-4 h-4" style={{ color: colors.teal }} />
                  <span>Targeted Retry Drills (from last attempt):</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {wrongOrPartialNumsFromLatest.length > 0 && (
                    <button
                      onClick={() => handleStartNewTest(wrongOrPartialNumsFromLatest)}
                      className="px-3 py-1.5 rounded-lg text-xs font-bold border"
                      style={{
                        backgroundColor: colors.badgeDangerBg,
                        color: colors.badgeDangerText,
                        borderColor: colors.border,
                      }}
                    >
                      Retry Wrong / Partial ({wrongOrPartialNumsFromLatest.length})
                    </button>
                  )}
                  {unattemptedNumsFromLatest.length > 0 && (
                    <button
                      onClick={() => handleStartNewTest(unattemptedNumsFromLatest)}
                      className="px-3 py-1.5 rounded-lg text-xs font-bold border"
                      style={{
                        backgroundColor: colors.badgeWarningBg,
                        color: colors.badgeWarningText,
                        borderColor: colors.border,
                      }}
                    >
                      Retry Unattempted ({unattemptedNumsFromLatest.length})
                    </button>
                  )}
                </div>
              </div>
            )}

            {/* Primary Start Action */}
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-2 border-t" style={{ borderColor: colors.borderLight }}>
              <div className="text-[0.6875rem]" style={{ color: colors.muted }}>
                Desktop shortcuts in CBT: Keys <kbd className="px-1 border rounded">1–5</kbd> / <kbd className="px-1 border rounded">A–E</kbd> select,{' '}
                <kbd className="px-1 border rounded">←</kbd>/<kbd className="px-1 border rounded">→</kbd> navigate,{' '}
                <kbd className="px-1 border rounded">M</kbd> flag, <kbd className="px-1 border rounded">Backspace</kbd> clear.
              </div>
              <button
                onClick={() => handleStartNewTest(null)}
                className="flex items-center gap-2 px-7 py-3 rounded-xl font-bold text-sm shadow-md transition hover:opacity-95"
                style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
              >
                <Play className="w-4 h-4" />
                <span>Start Full Assessment ({currentPaper.questions.length} Qs)</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // =========================================================================
  // SCREEN 2: RESULTS & PACING DIAGNOSTICS HUB
  // =========================================================================
  if (stage === 'RESULTS' && lastSubmittedRecord) {
    const filteredReview = activeQuestions.filter((q) => {
      const res = AnswerKeyMatcher.scoreQuestion(
        q,
        answers[q.num],
        posMarks,
        negMarks,
        unkMarks,
        msqPolicy
      );
      if (reviewFilter === 'correct') return res.status === 'CORRECT' || res.status === 'NEUTRALIZED';
      if (reviewFilter === 'partial') return res.status === 'PARTIAL';
      if (reviewFilter === 'incorrect') return res.status === 'INCORRECT';
      if (reviewFilter === 'unanswered') return res.status === 'UNANSWERED';
      if (reviewFilter === 'marked') return Boolean(markedForReview[q.num]);
      return true;
    });

    const wrongNums = activeQuestions
      .filter((q) => {
        const s = AnswerKeyMatcher.scoreQuestion(q, answers[q.num], posMarks, negMarks, unkMarks, msqPolicy).status;
        return s === 'INCORRECT' || s === 'PARTIAL';
      })
      .map((q) => q.num);
    const skipNums = activeQuestions
      .filter((q) => !answers[q.num])
      .map((q) => q.num);

    return (
      <div className="space-y-6">
        {renderModals()}

        {/* Hero Scorecard */}
        <div
          className="p-6 sm:p-8 rounded-2xl flex flex-col lg:flex-row lg:items-center justify-between gap-6"
          style={{ background: colors.heroGradient, color: colors.navyBannerText, boxShadow: colors.cardShadow }}
        >
          <div className="space-y-1.5 min-w-0 flex-1">
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white/15 text-xs font-bold">
              <Award className="w-3.5 h-3.5" />
              <span>{activeSubsetNums ? 'Targeted Practice Completed' : 'Assessment Recorded'}</span>
            </div>
            <h2 className="text-2xl font-black es-clamp-2" title={currentPaper.metadata.title}>
              {currentPaper.metadata.title}
            </h2>
            <p className="es-scroll-x text-xs opacity-85">
              Time: {formatClock(lastSubmittedRecord.timeSpentSeconds)} • {activeQuestions.length} Items • Policy: +{posMarks} / -{negMarks}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div className="px-4 py-2.5 rounded-xl bg-white/10 text-center min-w-[110px]">
              <div className="text-2xl font-black text-emerald-300">
                {lastSubmittedRecord.score.toFixed(2)}{' '}
                <span className="text-xs font-normal opacity-80">/ {lastSubmittedRecord.maxScore}</span>
              </div>
              <div className="text-[0.6875rem] opacity-80">Net Score</div>
            </div>
            <div className="px-4 py-2.5 rounded-xl bg-white/10 text-center min-w-[95px]">
              <div className="text-2xl font-black text-sky-300">{lastSubmittedRecord.accuracyPercent}%</div>
              <div className="text-[0.6875rem] opacity-80">Accuracy</div>
            </div>
            <button
              onClick={() => handleStartNewTest(null)}
              className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-xs font-bold bg-white/20 hover:bg-white/30 transition"
            >
              <RotateCcw className="w-4 h-4" />
              Retake Full
            </button>
            <button
              onClick={() => setStage('OVERVIEW')}
              className="px-3.5 py-2.5 rounded-xl text-xs font-bold bg-white/10 hover:bg-white/20 transition"
            >
              Portal Home
            </button>
          </div>
        </div>

        {/* Summary Tiles + Retry Action Strip */}
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
          {[
            { label: 'Correct', val: lastSubmittedRecord.correctCount, color: colors.green },
            { label: 'Partial MSQ', val: lastSubmittedRecord.partialCount, color: colors.amber },
            { label: 'Incorrect', val: lastSubmittedRecord.incorrectCount, color: colors.red },
            { label: 'Unanswered', val: lastSubmittedRecord.unansweredCount, color: colors.muted },
            { label: 'Avg Speed', val: `${pacingSummary.avgTimePerQuestion}s/Q`, color: colors.teal },
          ].map((tile, i) => (
            <div
              key={i}
              className="p-4 rounded-xl border text-center"
              style={{ backgroundColor: colors.surface, borderColor: colors.border }}
            >
              <div className="text-2xl font-black" style={{ color: tile.color }}>
                {tile.val}
              </div>
              <div className="text-[0.6875rem] font-semibold mt-0.5" style={{ color: colors.muted }}>
                {tile.label}
              </div>
            </div>
          ))}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap gap-2">
            {wrongNums.length > 0 && (
              <button
                onClick={() => handleStartNewTest(wrongNums)}
                className="px-3.5 py-2 rounded-xl text-xs font-bold border"
                style={{
                  backgroundColor: colors.badgeDangerBg,
                  color: colors.badgeDangerText,
                  borderColor: colors.border,
                }}
              >
                Retry Wrong & Partial ({wrongNums.length} Qs)
              </button>
            )}
            {skipNums.length > 0 && (
              <button
                onClick={() => handleStartNewTest(skipNums)}
                className="px-3.5 py-2 rounded-xl text-xs font-bold border"
                style={{
                  backgroundColor: colors.badgeWarningBg,
                  color: colors.badgeWarningText,
                  borderColor: colors.border,
                }}
              >
                Retry Unanswered ({skipNums.length} Qs)
              </button>
            )}
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => setShowHistoryModal(true)}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-bold border"
              style={{ backgroundColor: colors.surface, color: colors.inkBold, borderColor: colors.border }}
            >
              <History className="w-3.5 h-3.5" />
              History ({paperAttempts.length})
            </button>
            <button
              onClick={() => setShowPsychometricModal(true)}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-bold border"
              style={{ backgroundColor: colors.surface, color: colors.inkBold, borderColor: colors.border }}
            >
              <Activity className="w-3.5 h-3.5" />
              Psychometric Dashboard
            </button>
          </div>
        </div>

        {/* Sub-Tabs: Solutions Review vs Diagnostics & Pacing */}
        <div
          className="rounded-2xl border p-5 space-y-5"
          style={{ backgroundColor: colors.surface, borderColor: colors.border }}
        >
          <div className="flex flex-wrap items-center justify-between gap-3 border-b pb-3" style={{ borderColor: colors.border }}>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setResultsSubTab('solutions')}
                className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold"
                style={
                  resultsSubTab === 'solutions'
                    ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                    : { backgroundColor: colors.surfaceVariant, color: colors.ink }
                }
              >
                <Eye className="w-4 h-4" />
                Solutions & Official Key Review
              </button>
              <button
                onClick={() => setResultsSubTab('diagnostics')}
                className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold"
                style={
                  resultsSubTab === 'diagnostics'
                    ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }
                    : { backgroundColor: colors.surfaceVariant, color: colors.ink }
                }
              >
                <BarChart2 className="w-4 h-4" />
                Diagnostics & Pacing Telemetry
              </button>
            </div>

            {resultsSubTab === 'solutions' && (
              <div className="flex items-center gap-1.5 flex-wrap">
                {(
                  [
                    { id: 'all', label: `All (${activeQuestions.length})` },
                    { id: 'correct', label: `Correct (${lastSubmittedRecord.correctCount})` },
                    { id: 'partial', label: `Partial (${lastSubmittedRecord.partialCount})` },
                    { id: 'incorrect', label: `Wrong (${lastSubmittedRecord.incorrectCount})` },
                    { id: 'unanswered', label: `Skipped (${lastSubmittedRecord.unansweredCount})` },
                    { id: 'marked', label: `Flagged (${Object.values(markedForReview).filter(Boolean).length})` },
                  ] as { id: ReviewFilter; label: string }[]
                ).map((f) => (
                  <button
                    key={f.id}
                    onClick={() => setReviewFilter(f.id)}
                    className="px-2.5 py-1 rounded-lg text-[0.6875rem] font-bold border"
                    style={
                      reviewFilter === f.id
                        ? { backgroundColor: colors.teal, color: colors.onAccentText, borderColor: colors.teal }
                        : { backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }
                    }
                  >
                    {f.label}
                  </button>
                ))}
              </div>
            )}
          </div>

          {resultsSubTab === 'solutions' ? (
            <div className="space-y-4">
              {filteredReview.map((q) => {
                const userRaw = answers[q.num];
                const grade = AnswerKeyMatcher.scoreQuestion(
                  q,
                  userRaw,
                  posMarks,
                  negMarks,
                  unkMarks,
                  msqPolicy
                );
                const opts = getDisplayOptionOrder(q);
                const stacked = shouldStackOptions(q.questionType, q.options);
                const timeSpentQ = questionTimes[q.num] ?? 0;

                const statusBadge =
                  grade.status === 'CORRECT' || grade.status === 'NEUTRALIZED'
                    ? { bg: colors.badgeSuccessBg, fg: colors.badgeSuccessText, label: `${grade.status} (+${grade.marks})` }
                    : grade.status === 'PARTIAL'
                    ? { bg: colors.badgeWarningBg, fg: colors.badgeWarningText, label: `PARTIAL (+${grade.marks})` }
                    : grade.status === 'INCORRECT'
                    ? { bg: colors.badgeDangerBg, fg: colors.badgeDangerText, label: `INCORRECT (${grade.marks})` }
                    : { bg: colors.surfaceVariant, fg: colors.muted, label: `UNANSWERED (${grade.marks})` };

                return (
                  <div
                    key={q.id}
                    className="p-4 rounded-xl border space-y-3"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <span
                          className="px-2.5 py-0.5 rounded text-xs font-bold"
                          style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
                        >
                          Q.{q.num}
                        </span>
                        <span className="text-[0.6875rem] font-mono flex items-center gap-1" style={{ color: colors.muted }}>
                          <Clock className="w-3 h-3" />
                          {timeSpentQ}s spent
                        </span>
                      </div>

                      <span
                        className="px-2.5 py-0.5 rounded-full text-xs font-bold"
                        style={{ backgroundColor: statusBadge.bg, color: statusBadge.fg }}
                      >
                        {statusBadge.label}
                      </span>
                    </div>

                    <StructuredQuestionView
                      questionText={q.question}
                      formula={q.formula}
                      diagramLabel={q.diagramLabel}
                      diagramUrl={q.diagramUrl}
                      isVerified={q.isVerified}
                      isNeutralized={q.isNeutralized}
                    />

                    <div className={stacked ? 'flex flex-col gap-2' : 'grid grid-cols-1 sm:grid-cols-2 gap-2'}>
                      {opts.map((opt) => {
                        const isOfficial = AnswerKeyMatcher.containsOption(q.correctAnswer, opt.originalKey);
                        const isChosen = AnswerKeyMatcher.containsOption(userRaw, opt.originalKey);

                        let bg = colors.surface;
                        let border = colors.border;
                        if (isOfficial) {
                          bg = colors.greenLight;
                          border = colors.green;
                        } else if (isChosen) {
                          bg = colors.redLight;
                          border = colors.red;
                        }

                        return (
                          <div
                            key={opt.originalKey}
                            className="p-2.5 rounded-lg border flex items-start justify-between gap-2 text-xs"
                            style={{ backgroundColor: bg, borderColor: border }}
                          >
                            <div className="flex items-start gap-2 min-w-0 flex-1">
                              <span className="font-bold">({opt.originalKey})</span>
                              <div className="min-w-0 flex-1">
                                <LatexText text={opt.text} />
                              </div>
                            </div>
                            <div className="flex items-center gap-1 flex-shrink-0">
                              {isOfficial && (
                                <span
                                  className="px-1.5 py-0.5 rounded text-[0.625rem] font-bold"
                                  style={{ backgroundColor: colors.badgeSuccessBg, color: colors.badgeSuccessText }}
                                >
                                  Key
                                </span>
                              )}
                              {isChosen && (
                                <span
                                  className="px-1.5 py-0.5 rounded text-[0.625rem] font-bold"
                                  style={{
                                    backgroundColor: isOfficial ? colors.badgeSuccessBg : colors.badgeDangerBg,
                                    color: isOfficial ? colors.badgeSuccessText : colors.badgeDangerText,
                                  }}
                                >
                                  Yours
                                </span>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>

                    {q.explanation && (
                      <div
                        className="p-3 rounded-lg border text-xs"
                        style={{ backgroundColor: colors.blueLight, borderColor: colors.border }}
                      >
                        <div className="font-bold mb-1" style={{ color: colors.tealDark }}>
                          Detailed Explanation:
                        </div>
                        <LatexText text={q.explanation} />
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ) : (
            /* Diagnostics & Pacing Telemetry Sub-Tab */
            <div className="space-y-5">
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {[
                  { label: 'Avg Correct Time', value: `${pacingSummary.avgTimeCorrect}s`, color: colors.green },
                  { label: 'Avg Wrong Time', value: `${pacingSummary.avgTimeIncorrect}s`, color: colors.red },
                  { label: 'Fast & Accurate', value: `${pacingSummary.quadrantCounts.FAST_ACCURATE}`, color: colors.teal },
                  { label: 'Slow & Wrong', value: `${pacingSummary.quadrantCounts.SLOW_WRONG}`, color: colors.amber },
                ].map((stat, i) => (
                  <div
                    key={i}
                    className="p-3.5 rounded-xl border text-center"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                  >
                    <div className="text-xl font-black" style={{ color: stat.color }}>
                      {stat.value}
                    </div>
                    <div className="text-[0.6875rem] font-semibold mt-0.5" style={{ color: colors.muted }}>
                      {stat.label}
                    </div>
                  </div>
                ))}
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div
                  className="p-4 rounded-xl border space-y-2.5"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                >
                  <h4 className="text-xs font-bold uppercase tracking-wider flex items-center gap-1.5" style={{ color: colors.inkBold }}>
                    <Clock className="w-4 h-4" style={{ color: colors.amber }} />
                    Slowest Questions (Time Sinks)
                  </h4>
                  {pacingSummary.slowestQuestions.length === 0 ? (
                    <p className="text-xs" style={{ color: colors.muted }}>No active question time recorded.</p>
                  ) : (
                    pacingSummary.slowestQuestions.map((item) => (
                      <div
                        key={item.qNum}
                        className="px-3 py-2 rounded-lg border flex items-center justify-between text-xs"
                        style={{ backgroundColor: colors.surface, borderColor: colors.borderLight }}
                      >
                        <span className="font-bold">
                          Q.{item.qNum}{' '}
                          <span className="font-normal capitalize" style={{ color: colors.muted }}>
                            ({item.questionType})
                          </span>
                        </span>
                        <span className="font-mono font-bold">
                          {item.timeSeconds}s • {item.status}
                        </span>
                      </div>
                    ))
                  )}
                </div>

                <div
                  className="p-4 rounded-xl border space-y-2.5"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                >
                  <h4 className="text-xs font-bold uppercase tracking-wider flex items-center gap-1.5" style={{ color: colors.inkBold }}>
                    <Zap className="w-4 h-4" style={{ color: colors.green }} />
                    Fastest Accurate Hits
                  </h4>
                  {pacingSummary.fastestCorrect.length === 0 ? (
                    <p className="text-xs" style={{ color: colors.muted }}>No quick correct answers logged.</p>
                  ) : (
                    pacingSummary.fastestCorrect.map((item) => (
                      <div
                        key={item.qNum}
                        className="px-3 py-2 rounded-lg border flex items-center justify-between text-xs"
                        style={{ backgroundColor: colors.surface, borderColor: colors.borderLight }}
                      >
                        <span className="font-bold">Q.{item.qNum}</span>
                        <span className="font-mono font-bold" style={{ color: colors.green }}>
                          {item.timeSeconds}s (+{item.marks})
                        </span>
                      </div>
                    ))
                  )}
                </div>
              </div>

              {/* Question-Type Weak-Area Table */}
              <div className="overflow-x-auto border rounded-xl" style={{ borderColor: colors.border }}>
                <table className="w-full text-xs text-left border-collapse">
                  <thead>
                    <tr style={{ backgroundColor: colors.surfaceVariant, color: colors.inkBold }}>
                      <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Question Pattern</th>
                      <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Count</th>
                      <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Correct / Wrong / Skip</th>
                      <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Accuracy</th>
                      <th className="px-3 py-2 border-b" style={{ borderColor: colors.border }}>Avg Pacing</th>
                    </tr>
                  </thead>
                  <tbody>
                    {pacingSummary.typeBreakdown.map((row) => (
                      <tr key={row.typeLabel} className="border-b last:border-b-0" style={{ borderColor: colors.borderLight }}>
                        <td className="px-3 py-2 font-bold capitalize">{row.typeLabel}</td>
                        <td className="px-3 py-2">{row.total}</td>
                        <td className="px-3 py-2 font-mono">
                          {row.correct} / {row.incorrect} / {row.unanswered}
                        </td>
                        <td className="px-3 py-2 font-bold">{row.accuracyPercent}%</td>
                        <td className="px-3 py-2 font-mono">{row.avgTimeSeconds}s</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </div>
    );
  }

  // =========================================================================
  // SCREEN 3: ACTIVE EXAM WORKSPACE (TCS iON CBT or SCROLLABLE PAPER)
  // =========================================================================
  const answeredCount = Object.keys(answers).length;
  const markedCount = Object.values(markedForReview).filter(Boolean).length;
  const isOverTime =
    timerMode === 'OPEN_STOPWATCH' &&
    targetDurationSeconds > 0 &&
    elapsedSeconds > targetDurationSeconds;
  const displayClockText =
    timerMode === 'COUNTDOWN' ? formatClock(timeLeftSeconds) : formatClock(elapsedSeconds);
  const lowTimeWarning = timerMode === 'COUNTDOWN' && timeLeftSeconds > 0 && timeLeftSeconds < 300;

  // Compute 5-state palette metrics
  const paletteStates = activeQuestions.map((q) => {
    const hasAns = Boolean(answers[q.num]);
    const isMarked = Boolean(markedForReview[q.num]);
    const isVisited = Boolean(visited[q.num]);
    if (hasAns && isMarked) return 'ANSWERED_MARKED';
    if (isMarked) return 'MARKED';
    if (hasAns) return 'ANSWERED';
    if (isVisited) return 'NOT_ANSWERED';
    return 'NOT_VISITED';
  });

  const renderPaletteCard = () => (
    <div
      className="rounded-xl border p-4 space-y-3"
      style={{ backgroundColor: colors.surface, borderColor: colors.border }}
    >
      <div className="flex items-center justify-between">
        <div>
          <h4 className="text-xs font-bold uppercase tracking-wider" style={{ color: colors.inkBold }}>
            Question Palette
          </h4>
          <p className="text-[0.625rem] font-mono" style={{ color: colors.muted }}>
            Roll {currentPaper.metadata.rollNumber || '0000000001'} • Set {currentPaper.metadata.setNumber || '01'}
          </p>
        </div>
        {paletteOpen && (
          <button
            onClick={() => setPaletteOpen(false)}
            className="lg:hidden p-1 rounded border"
            style={{ borderColor: colors.border }}
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      {/* 5-state legend */}
      <div className="grid grid-cols-2 gap-1.5 text-[0.625rem]" style={{ color: colors.ink }}>
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm bg-emerald-600 inline-block" />
          <span>Answered ({paletteStates.filter((s) => s === 'ANSWERED').length})</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-sm bg-rose-500 inline-block" />
          <span>Not Answered ({paletteStates.filter((s) => s === 'NOT_ANSWERED').length})</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-full bg-purple-600 inline-block" />
          <span>Marked ({paletteStates.filter((s) => s === 'MARKED').length})</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="w-3 h-3 rounded-full bg-purple-600 ring-2 ring-emerald-400 inline-block" />
          <span>Ans & Marked ({paletteStates.filter((s) => s === 'ANSWERED_MARKED').length})</span>
        </div>
        <div className="flex items-center gap-1.5 col-span-2">
          <span className="w-3 h-3 rounded-sm border inline-block" style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }} />
          <span>Not Visited ({paletteStates.filter((s) => s === 'NOT_VISITED').length})</span>
        </div>
      </div>

      <div className="grid grid-cols-5 gap-1.5 max-h-[50dvh] lg:max-h-[380px] overflow-y-auto pr-1">
        {activeQuestions.map((q, idx) => {
          const st = paletteStates[idx];
          const isCur = idx === currentIndex;
          let bg = colors.surfaceVariant;
          let fg = colors.ink;
          let border = colors.border;
          let rounded = 'rounded-lg';

          if (st === 'ANSWERED') {
            bg = '#059669';
            fg = '#FFFFFF';
            border = '#047857';
          } else if (st === 'NOT_ANSWERED') {
            bg = '#E11D48';
            fg = '#FFFFFF';
            border = '#BE123C';
          } else if (st === 'MARKED') {
            bg = '#7C3AED';
            fg = '#FFFFFF';
            border = '#6D28D9';
            rounded = 'rounded-full';
          } else if (st === 'ANSWERED_MARKED') {
            bg = '#7C3AED';
            fg = '#FFFFFF';
            border = '#10B981';
            rounded = 'rounded-full';
          }

          return (
            <button
              key={q.id}
              onClick={() => {
                setCurrentIndex(idx);
                setPaletteOpen(false);
              }}
              className={`h-9 border text-xs font-bold flex items-center justify-center relative transition ${rounded} ${
                isCur ? 'ring-2 ring-offset-1 ring-sky-500' : ''
              }`}
              style={{ backgroundColor: bg, color: fg, borderColor: border }}
            >
              {q.num}
              {st === 'ANSWERED_MARKED' && (
                <span className="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full bg-emerald-400 border border-white" />
              )}
            </button>
          );
        })}
      </div>
    </div>
  );

  return (
    <div className="space-y-4 relative">
      {/* 3-2-1 Readiness Overlay */}
      {countdownOverlay > 0 && (
        <div
          className="fixed inset-0 z-50 flex flex-col items-center justify-center p-6 backdrop-blur-md"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.88)', color: '#FFFFFF' }}
        >
          <div className="text-xs uppercase tracking-widest opacity-80 font-bold">
            Preparing Examination Session
          </div>
          <div className="text-7xl font-black my-4">{countdownOverlay}</div>
          <p className="text-xs opacity-80 mb-6 text-center max-w-sm">
            {currentPaper.metadata.title} • {activeQuestions.length} Questions
          </p>
          <button
            onClick={() => setCountdownOverlay(0)}
            className="px-5 py-2 rounded-xl text-xs font-bold bg-white text-slate-900 hover:opacity-90"
          >
            Skip Countdown & Start Now
          </button>
        </div>
      )}

      {/* Active Top Control Bar */}
      <div
        className="px-3 sm:px-4 py-3 rounded-xl shadow flex flex-wrap items-center justify-between gap-2.5 sm:gap-3"
        style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
      >
        <div className="min-w-0 flex-1">
          <h3 className="font-bold text-sm es-scroll-x" title={currentPaper.metadata.title}>{currentPaper.metadata.title}</h3>
          <p className="text-[0.6875rem] opacity-80 tabular-nums">
            {interfaceMode === 'TCS_ION_CBT' ? `Q.${currentQuestion?.num} (${currentIndex + 1}/${activeQuestions.length})` : `${activeQuestions.length} Questions`} •{' '}
            Answered {answeredCount}/{activeQuestions.length}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {/* Local Font Bias A- / A / A+ */}
          <div className="flex items-center rounded-lg bg-white/10 border border-white/15 overflow-hidden text-[0.6875rem] font-bold">
            <button
              onClick={() => setLocalFontBias((b) => Math.max(-2, b - 1))}
              className="px-2 py-1 hover:bg-white/15"
              title="Decrease question font size"
            >
              A-
            </button>
            <button
              onClick={() => setLocalFontBias(0)}
              className="px-2 py-1 hover:bg-white/15 border-x border-white/15"
              title="Reset font size"
            >
              A
            </button>
            <button
              onClick={() => setLocalFontBias((b) => Math.min(5, b + 1))}
              className="px-2 py-1 hover:bg-white/15"
              title="Increase question font size"
            >
              A+
            </button>
          </div>

          {/* Live Mode Switcher */}
          <button
            onClick={() =>
              setInterfaceMode((m) => (m === 'TCS_ION_CBT' ? 'SCROLLABLE_PAPER' : 'TCS_ION_CBT'))
            }
            className="px-2.5 py-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-[0.6875rem] font-bold"
          >
            {interfaceMode === 'TCS_ION_CBT' ? 'Paper Mode' : 'CBT Mode'}
          </button>

          {/* Timer Chip */}
          <div
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border font-mono text-xs font-bold ${
              lowTimeWarning || isOverTime ? 'bg-rose-600 border-rose-400 animate-pulse' : 'bg-white/10 border-white/20'
            }`}
          >
            <Clock className="w-3.5 h-3.5" />
            <span>{displayClockText}</span>
            {isOverTime && <span className="text-[0.625rem] uppercase">Over</span>}
          </div>

          {interfaceMode === 'TCS_ION_CBT' && (
            <button
              onClick={() => setPaletteOpen((o) => !o)}
              className="lg:hidden flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-white/15 text-xs font-bold"
            >
              <Grid className="w-3.5 h-3.5" />
              Palette
            </button>
          )}

          <button
            onClick={() => setStage('OVERVIEW')}
            className="px-2.5 py-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-xs font-semibold"
            title="Pause & return to lobby (progress preserved)"
          >
            Pause
          </button>

          <button
            onClick={() => setShowSubmitModal(true)}
            className="px-3.5 py-1.5 rounded-lg text-xs font-bold bg-emerald-600 hover:bg-emerald-500 text-white shadow"
          >
            Finish & Submit
          </button>
        </div>
      </div>

      {/* Mode A: TCS iON CBT Single Question View */}
      {interfaceMode === 'TCS_ION_CBT' && currentQuestion && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 lg:gap-5">
          <div
            className="lg:col-span-8 min-w-0 rounded-2xl border p-4 sm:p-6 flex flex-col justify-between min-h-[280px] lg:min-h-[460px] space-y-4 sm:space-y-5"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="space-y-4">
              {/* Item Header */}
              <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b" style={{ borderColor: colors.borderLight }}>
                <div className="flex items-center gap-2">
                  <span
                    className="px-2.5 py-1 rounded-md text-xs font-bold"
                    style={{ backgroundColor: colors.tealLight, color: colors.tealDark }}
                  >
                    Question {currentQuestion.num}
                  </span>
                  {AnswerKeyMatcher.isMultiSelect(currentQuestion) && (
                    <span
                      className="px-2 py-0.5 rounded-full text-[0.625rem] font-bold"
                      style={{ backgroundColor: colors.badgeWarningBg, color: colors.badgeWarningText }}
                    >
                      MSQ (Select all that apply)
                    </span>
                  )}
                  <span className="text-[0.6875rem] font-mono" style={{ color: colors.muted }}>
                    +{posMarks} / -{negMarks}
                  </span>
                </div>

                <button
                  onClick={() =>
                    setMarkedForReview((prev) => ({
                      ...prev,
                      [currentQuestion.num]: !prev[currentQuestion.num],
                    }))
                  }
                  className="flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-bold border"
                  style={
                    markedForReview[currentQuestion.num]
                      ? { backgroundColor: colors.purpleLight, color: colors.purple, borderColor: colors.purple }
                      : { backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }
                  }
                >
                  <Bookmark className="w-3.5 h-3.5" />
                  {markedForReview[currentQuestion.num] ? 'Marked for Review' : 'Mark for Review'}
                </button>
              </div>

              {/* Stem */}
              <div style={{ fontSize: scalePx(15 + localFontBias) }}>
                <StructuredQuestionView
                  questionText={currentQuestion.question}
                  formula={currentQuestion.formula}
                  diagramLabel={currentQuestion.diagramLabel}
                  diagramUrl={currentQuestion.diagramUrl}
                  isVerified={currentQuestion.isVerified}
                  isNeutralized={currentQuestion.isNeutralized}
                />
              </div>

              {/* Interactive Options */}
              {(() => {
                const isMsq = AnswerKeyMatcher.isMultiSelect(currentQuestion);
                const opts = getDisplayOptionOrder(currentQuestion);
                const stacked = shouldStackOptions(currentQuestion.questionType, currentQuestion.options);
                const rawAns = answers[currentQuestion.num];

                return (
                  <div className={stacked ? 'flex flex-col gap-2.5' : 'grid grid-cols-1 sm:grid-cols-2 gap-2.5'}>
                    {opts.map((opt) => {
                      const isSel = AnswerKeyMatcher.containsOption(rawAns, opt.originalKey);
                      return (
                        <button
                          key={opt.originalKey}
                          type="button"
                          onClick={() => handleSelectChoice(currentQuestion, opt.originalKey)}
                          className="w-full p-3.5 rounded-xl border text-left flex items-start justify-between gap-3 transition"
                          style={{
                            backgroundColor: isSel ? colors.tealLight : colors.surfaceVariant,
                            borderColor: isSel ? colors.teal : colors.border,
                            fontSize: scalePx(13.5 + localFontBias),
                          }}
                        >
                          <div className="flex items-start gap-2.5 min-w-0 flex-1">
                            <span
                              className={`w-7 h-7 flex items-center justify-center font-bold text-xs flex-shrink-0 ${
                                isMsq ? 'rounded-md' : 'rounded-full'
                              }`}
                              style={{
                                backgroundColor: isSel ? colors.teal : colors.surface,
                                color: isSel ? colors.onAccentText : colors.inkBold,
                                border: `1px solid ${isSel ? colors.teal : colors.border}`,
                              }}
                            >
                              {opt.displayLabel}
                            </span>
                            <div className="min-w-0 flex-1 leading-relaxed" style={{ color: colors.inkBold }}>
                              <LatexText text={opt.text} />
                            </div>
                          </div>

                          <div
                            className={`w-4 h-4 mt-1 flex items-center justify-center border flex-shrink-0 ${
                              isMsq ? 'rounded' : 'rounded-full'
                            }`}
                            style={{
                              borderColor: isSel ? colors.teal : colors.border,
                              backgroundColor: isSel ? colors.teal : 'transparent',
                              color: colors.onAccentText,
                            }}
                          >
                            {isSel && <Check className="w-2.5 h-2.5 stroke-[3]" />}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                );
              })()}
            </div>

            {/* Bottom CBT Toolbar */}
            <div className="pt-4 border-t flex flex-wrap items-center justify-between gap-2" style={{ borderColor: colors.borderLight }}>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleClearResponse(currentQuestion.num)}
                  disabled={!answers[currentQuestion.num]}
                  className="px-3 py-2 rounded-lg text-xs font-semibold border disabled:opacity-40"
                  style={{ backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }}
                >
                  Clear Response
                </button>
                <button
                  onClick={handleMarkAndNext}
                  className="px-3 py-2 rounded-lg text-xs font-bold border"
                  style={{ backgroundColor: colors.purpleLight, color: colors.purple, borderColor: colors.purple }}
                >
                  Mark & Next
                </button>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => setCurrentIndex((i) => Math.max(0, i - 1))}
                  disabled={currentIndex === 0}
                  className="flex items-center gap-1 px-3.5 py-2 rounded-lg text-xs font-bold border disabled:opacity-40"
                  style={{ backgroundColor: colors.surfaceVariant, color: colors.inkBold, borderColor: colors.border }}
                >
                  <ArrowLeft className="w-3.5 h-3.5" />
                  Prev
                </button>
                <button
                  onClick={handleSaveAndNext}
                  className="flex items-center gap-1 px-4 py-2 rounded-lg text-xs font-bold"
                  style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
                >
                  <span>{currentIndex === activeQuestions.length - 1 ? 'Review & Finish' : 'Save & Next'}</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>

          {/* Desktop Side Palette & Mobile Slide-Over */}
          <div className={`${paletteOpen ? 'block' : 'hidden'} lg:block lg:col-span-4`}>
            {renderPaletteCard()}
          </div>
        </div>
      )}

      {/* Mode B: Continuous Scrollable Paper */}
      {interfaceMode === 'SCROLLABLE_PAPER' && (
        <div className="space-y-4">
          {activeQuestions.map((q, idx) => {
            const isMsq = AnswerKeyMatcher.isMultiSelect(q);
            const opts = getDisplayOptionOrder(q);
            const stacked = shouldStackOptions(q.questionType, q.options);
            const rawAns = answers[q.num];

            return (
              <div
                key={q.id}
                onClick={() => setCurrentIndex(idx)}
                className="p-5 rounded-2xl border space-y-3"
                style={{ backgroundColor: colors.surface, borderColor: colors.border }}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span
                      className="px-2.5 py-0.5 rounded text-xs font-bold"
                      style={{ backgroundColor: colors.navyBanner, color: colors.navyBannerText }}
                    >
                      Q.{q.num}
                    </span>
                    {isMsq && (
                      <span
                        className="px-2 py-0.5 rounded-full text-[0.625rem] font-bold"
                        style={{ backgroundColor: colors.badgeWarningBg, color: colors.badgeWarningText }}
                      >
                        MSQ
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    {rawAns && (
                      <button
                        onClick={() => handleClearResponse(q.num)}
                        className="text-[0.6875rem] font-semibold"
                        style={{ color: colors.muted }}
                      >
                        Clear
                      </button>
                    )}
                    <button
                      onClick={() =>
                        setMarkedForReview((prev) => ({ ...prev, [q.num]: !prev[q.num] }))
                      }
                      className="p-1 rounded"
                      style={{ color: markedForReview[q.num] ? colors.purple : colors.muted }}
                      title="Mark for review"
                    >
                      <Bookmark className="w-4 h-4" />
                    </button>
                  </div>
                </div>

                <div style={{ fontSize: scalePx(14 + localFontBias) }}>
                  <StructuredQuestionView
                    questionText={q.question}
                    formula={q.formula}
                    diagramLabel={q.diagramLabel}
                    diagramUrl={q.diagramUrl}
                    isVerified={q.isVerified}
                    isNeutralized={q.isNeutralized}
                  />
                </div>

                <div className={stacked ? 'flex flex-col gap-2' : 'grid grid-cols-1 sm:grid-cols-2 gap-2'}>
                  {opts.map((opt) => {
                    const isSel = AnswerKeyMatcher.containsOption(rawAns, opt.originalKey);
                    return (
                      <button
                        key={opt.originalKey}
                        type="button"
                        onClick={() => handleSelectChoice(q, opt.originalKey)}
                        className="p-3 rounded-xl border text-left flex items-start gap-2.5 text-xs transition"
                        style={{
                          backgroundColor: isSel ? colors.tealLight : colors.surfaceVariant,
                          borderColor: isSel ? colors.teal : colors.border,
                        }}
                      >
                        <span
                          className={`w-6 h-6 flex items-center justify-center font-bold flex-shrink-0 ${
                            isMsq ? 'rounded' : 'rounded-full'
                          }`}
                          style={{
                            backgroundColor: isSel ? colors.teal : colors.surface,
                            color: isSel ? colors.onAccentText : colors.inkBold,
                            border: `1px solid ${colors.border}`,
                          }}
                        >
                          {opt.displayLabel}
                        </span>
                        <div className="min-w-0 flex-1" style={{ color: colors.inkBold }}>
                          <LatexText text={opt.text} />
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>
            );
          })}

          <div className="flex justify-end pt-2">
            <button
              onClick={() => setShowSubmitModal(true)}
              className="px-6 py-3 rounded-xl text-sm font-bold bg-emerald-600 hover:bg-emerald-500 text-white shadow"
            >
              Finish & Evaluate All Responses ({answeredCount}/{activeQuestions.length})
            </button>
          </div>
        </div>
      )}

      {/* Submit Confirmation Modal */}
      {showSubmitModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 backdrop-blur-sm"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.58)' }}
        >
          <div
            className="w-full max-w-md rounded-2xl p-6 shadow-2xl border space-y-4"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
          >
            <h3 className="text-lg font-bold" style={{ color: colors.inkBold }}>
              Confirm Final Submission
            </h3>
            <div
              className="p-4 rounded-xl border space-y-2 text-xs"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
            >
              <div className="flex justify-between">
                <span>Total Questions:</span>
                <strong>{activeQuestions.length}</strong>
              </div>
              <div className="flex justify-between" style={{ color: colors.green }}>
                <span>Answered:</span>
                <strong>{answeredCount}</strong>
              </div>
              <div className="flex justify-between" style={{ color: colors.muted }}>
                <span>Unanswered:</span>
                <strong>{activeQuestions.length - answeredCount}</strong>
              </div>
              <div className="flex justify-between" style={{ color: colors.purple }}>
                <span>Marked for Review:</span>
                <strong>{markedCount}</strong>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowSubmitModal(false)}
                className="px-4 py-2 rounded-lg text-xs font-semibold border"
                style={{ backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }}
              >
                Resume Exam
              </button>
              <button
                onClick={handleFinalSubmit}
                className="px-5 py-2 rounded-lg text-xs font-bold bg-emerald-600 hover:bg-emerald-500 text-white shadow"
              >
                Submit & View Results
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default StudentPortalTab;
