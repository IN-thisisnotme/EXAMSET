import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Bell,
  BellOff,
  CalendarClock,
  CheckCircle2,
  Circle,
  Clock,
  Flame,
  Link2,
  Pause,
  Play,
  Plus,
  RotateCcw,
  SkipForward,
  Sparkles,
  Target,
  Trash2,
  Volume2,
  VolumeX,
} from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import type {
  ActiveExamSession,
  ExamCountdownTarget,
  QuestionPaper,
} from '../../types/exam';
import type {
  FlashcardSrsState,
  HeadwordQueueItem,
  StudyNoteRecord,
  VocabularyDeck,
} from '../../types/phase4';
import {
  POMODORO_PRESET_META,
  type DailyStudyAnalytics,
  type PomodoroConfig,
  type PomodoroPhase,
  type PomodoroPreset,
  type StudyDueSuggestion,
  type StudyTodoItem,
  type TodoFilter,
  type TodoLinkType,
  type TodoPriority,
} from '../../types/phase5';
import {
  buildDueSuggestions,
  computeAnalyticsSummary,
  computeRemainingSecondsFromDeadline,
  filterAndSortTodos,
  formatMmSs,
  StudyDateUtils,
} from '../../utils/studyPlannerEngine';
import { playSubtleChime } from '../../utils/brainLabEngines';

interface StudyPlannerTabProps {
  papers: QuestionPaper[];
  studyNotes: StudyNoteRecord[];
  vocabDecks: VocabularyDeck[];
  vocabSrs: Record<string, Record<string, FlashcardSrsState>>;
  headwordInbox: HeadwordQueueItem[];
  activeSessions: Record<string, ActiveExamSession>;
  countdowns: ExamCountdownTarget[];
  todos: StudyTodoItem[];
  pomodoroConfig: PomodoroConfig;
  dailyAnalytics: Record<string, DailyStudyAnalytics>;
  onSaveTodos: (next: StudyTodoItem[]) => void;
  onSavePomodoroConfig: (cfg: PomodoroConfig) => void;
  onRecordFocusSeconds: (secs: number) => void;
  onOpenCountdownsModal: () => void;
  onJumpToSuggestion: (suggestion: StudyDueSuggestion) => void;
  onJumpToTodoLink: (todo: StudyTodoItem) => void;
  onNotify: (msg: string) => void;
}

export const StudyPlannerTab: React.FC<StudyPlannerTabProps> = ({
  papers,
  studyNotes,
  vocabDecks,
  vocabSrs,
  headwordInbox,
  activeSessions,
  countdowns,
  todos,
  pomodoroConfig,
  dailyAnalytics,
  onSaveTodos,
  onSavePomodoroConfig,
  onRecordFocusSeconds,
  onOpenCountdownsModal,
  onJumpToSuggestion,
  onJumpToTodoLink,
  onNotify,
}) => {
  const { colors, scalePx } = useTheme();

  // Compute active preset durations
  const activeFocusMinutes =
    pomodoroConfig.preset === 'CUSTOM'
      ? Math.max(5, Math.min(180, pomodoroConfig.customFocusMinutes))
      : POMODORO_PRESET_META[pomodoroConfig.preset].focusMinutes;
  const activeBreakMinutes =
    pomodoroConfig.preset === 'CUSTOM'
      ? Math.max(1, Math.min(60, pomodoroConfig.customBreakMinutes))
      : POMODORO_PRESET_META[pomodoroConfig.preset].breakMinutes;

  // Timestamp-anchored Pomodoro state
  const [phase, setPhase] = useState<PomodoroPhase>('IDLE');
  const [pausedFromPhase, setPausedFromPhase] = useState<'FOCUS' | 'BREAK' | null>(null);
  const [remainingSeconds, setRemainingSeconds] = useState<number>(activeFocusMinutes * 60);
  const [totalPhaseSeconds, setTotalPhaseSeconds] = useState<number>(activeFocusMinutes * 60);
  const [linkedTodoId, setLinkedTodoId] = useState<string | null>(null);
  const [completedPomodorosSession, setCompletedPomodorosSession] = useState<number>(0);

  const deadlineAtRef = useRef<number>(0);
  const lastFocusSyncMsRef = useRef<number>(0);
  const bufferedSecondsRef = useRef<number>(0);
  const todosRef = useRef(todos);
  todosRef.current = todos;
  const linkedTodoIdRef = useRef(linkedTodoId);
  linkedTodoIdRef.current = linkedTodoId;

  // Custom Todo Composer State
  const [todoFilter, setTodoFilter] = useState<TodoFilter>('ALL');
  const [newTitle, setNewTitle] = useState('');
  const [newPriority, setNewPriority] = useState<TodoPriority>('MEDIUM');
  const [newDueDate, setNewDueDate] = useState<string>(() => StudyDateUtils.todayDateStr());
  const [newTargetPomos, setNewTargetPomos] = useState<number>(2);
  const [newLinkType, setNewLinkType] = useState<TodoLinkType>('NONE');
  const [newLinkTargetId, setNewLinkTargetId] = useState<string>('');

  // Keep IDLE countdown synced if user tweaks preset/custom duration
  useEffect(() => {
    if (phase === 'IDLE') {
      const secs = activeFocusMinutes * 60;
      setRemainingSeconds(secs);
      setTotalPhaseSeconds(secs);
    }
  }, [activeFocusMinutes, phase]);

  // Unique vocabulary languages for optional task linking
  const vocabLanguages = useMemo(
    () => Array.from(new Set(vocabDecks.map((d) => d.language || 'English'))),
    [vocabDecks]
  );

  // Sync monotonic timer every 500ms while FOCUS or BREAK
  useEffect(() => {
    if (phase !== 'FOCUS' && phase !== 'BREAK') return;

    const triggerCompletionAlert = (title: string, body: string) => {
      if (pomodoroConfig.chimeEnabled) {
        playSubtleChime(784, 220);
        setTimeout(() => playSubtleChime(988, 260), 190);
      }
      if (
        pomodoroConfig.browserNotifyEnabled &&
        typeof window !== 'undefined' &&
        'Notification' in window &&
        window.Notification.permission === 'granted'
      ) {
        try {
          new window.Notification(title, { body });
        } catch {
          // ignore notification restriction
        }
      }
      onNotify(`${title}: ${body}`);
    };

    const interval = setInterval(() => {
      const now = Date.now();
      if (phase === 'FOCUS') {
        const deltaMs = Math.max(0, now - lastFocusSyncMsRef.current);
        lastFocusSyncMsRef.current = now;
        bufferedSecondsRef.current += deltaMs / 1000;
        if (bufferedSecondsRef.current >= 15) {
          const whole = Math.floor(bufferedSecondsRef.current);
          bufferedSecondsRef.current -= whole;
          if (whole > 0) onRecordFocusSeconds(whole);
        }
      } else {
        lastFocusSyncMsRef.current = now;
      }

      const rem = computeRemainingSecondsFromDeadline(deadlineAtRef.current, now);
      setRemainingSeconds(rem);

      if (rem <= 0) {
        if (phase === 'FOCUS') {
          const wholeLeft = Math.floor(bufferedSecondsRef.current);
          bufferedSecondsRef.current = 0;
          if (wholeLeft > 0) onRecordFocusSeconds(wholeLeft);

          // Increment linked Todo Pomodoro count
          const targetId = linkedTodoIdRef.current;
          if (targetId) {
            const updatedTodos = todosRef.current.map((t) =>
              t.id === targetId
                ? { ...t, completedPomodoros: t.completedPomodoros + 1 }
                : t
            );
            onSaveTodos(updatedTodos);
          }

          setCompletedPomodorosSession((c) => c + 1);
          triggerCompletionAlert(
            'Focus Block Complete',
            `Great work! Starting ${activeBreakMinutes}-minute recovery break.`
          );

          const breakSecs = activeBreakMinutes * 60;
          deadlineAtRef.current = now + breakSecs * 1000;
          lastFocusSyncMsRef.current = now;
          setPhase('BREAK');
          setTotalPhaseSeconds(breakSecs);
          setRemainingSeconds(breakSecs);
        } else {
          triggerCompletionAlert('Break Complete', 'Ready for your next Focus block.');
          const nextFocusSecs = activeFocusMinutes * 60;
          setPhase('IDLE');
          setPausedFromPhase(null);
          setTotalPhaseSeconds(nextFocusSecs);
          setRemainingSeconds(nextFocusSecs);
        }
      }
    }, 500);

    return () => clearInterval(interval);
  }, [
    phase,
    activeBreakMinutes,
    activeFocusMinutes,
    pomodoroConfig.chimeEnabled,
    pomodoroConfig.browserNotifyEnabled,
    onRecordFocusSeconds,
    onSaveTodos,
    onNotify,
  ]);

  const flushBufferedFocusSeconds = () => {
    const whole = Math.floor(bufferedSecondsRef.current);
    bufferedSecondsRef.current = 0;
    if (whole > 0) onRecordFocusSeconds(whole);
  };

  const handleStartFocus = (overrideTodoId?: string | null) => {
    flushBufferedFocusSeconds();
    if (overrideTodoId !== undefined) setLinkedTodoId(overrideTodoId);
    const secs = activeFocusMinutes * 60;
    const now = Date.now();
    deadlineAtRef.current = now + secs * 1000;
    lastFocusSyncMsRef.current = now;
    bufferedSecondsRef.current = 0;
    setPhase('FOCUS');
    setPausedFromPhase(null);
    setTotalPhaseSeconds(secs);
    setRemainingSeconds(secs);
  };

  const handlePause = () => {
    if (phase !== 'FOCUS' && phase !== 'BREAK') return;
    if (phase === 'FOCUS') flushBufferedFocusSeconds();
    const rem = computeRemainingSecondsFromDeadline(deadlineAtRef.current, Date.now());
    setRemainingSeconds(Math.max(1, rem));
    setPausedFromPhase(phase);
    setPhase('PAUSED');
  };

  const handleResume = () => {
    if (phase !== 'PAUSED') return;
    const target = pausedFromPhase || 'FOCUS';
    const now = Date.now();
    deadlineAtRef.current = now + Math.max(1, remainingSeconds) * 1000;
    lastFocusSyncMsRef.current = now;
    setPhase(target);
    setPausedFromPhase(null);
  };

  const handleSkipToBreak = () => {
    flushBufferedFocusSeconds();
    const breakSecs = activeBreakMinutes * 60;
    const now = Date.now();
    deadlineAtRef.current = now + breakSecs * 1000;
    lastFocusSyncMsRef.current = now;
    setPhase('BREAK');
    setPausedFromPhase(null);
    setTotalPhaseSeconds(breakSecs);
    setRemainingSeconds(breakSecs);
  };

  const handleResetTimer = () => {
    if (phase === 'FOCUS') flushBufferedFocusSeconds();
    const focusSecs = activeFocusMinutes * 60;
    setPhase('IDLE');
    setPausedFromPhase(null);
    setTotalPhaseSeconds(focusSecs);
    setRemainingSeconds(focusSecs);
  };

  const handleToggleBrowserNotify = async () => {
    if (typeof window === 'undefined' || !('Notification' in window)) {
      onNotify('Browser notifications are not supported in this browser.');
      return;
    }
    if (!pomodoroConfig.browserNotifyEnabled) {
      if (window.Notification.permission === 'default') {
        const res = await window.Notification.requestPermission();
        if (res !== 'granted') {
          onNotify('Browser notification permission was denied.');
          return;
        }
      } else if (window.Notification.permission === 'denied') {
        onNotify('Notifications are blocked in browser site settings.');
        return;
      }
      onSavePomodoroConfig({ ...pomodoroConfig, browserNotifyEnabled: true });
    } else {
      onSavePomodoroConfig({ ...pomodoroConfig, browserNotifyEnabled: false });
    }
  };

  const dueSuggestions = useMemo(
    () =>
      buildDueSuggestions({
        studyNotes,
        vocabDecks,
        vocabSrs,
        headwordInbox,
        papers,
        activeSessions,
      }),
    [studyNotes, vocabDecks, vocabSrs, headwordInbox, papers, activeSessions]
  );

  const visibleTodos = useMemo(
    () => filterAndSortTodos(todos, todoFilter),
    [todos, todoFilter]
  );

  const analyticsSummary = useMemo(
    () => computeAnalyticsSummary(dailyAnalytics),
    [dailyAnalytics]
  );

  const linkedTodoObj = useMemo(
    () => todos.find((t) => t.id === linkedTodoId) || null,
    [todos, linkedTodoId]
  );

  const todayMinutes = Math.round(analyticsSummary.today.studyTimeSeconds / 60);
  const goalProgress = Math.min(
    1,
    todayMinutes / Math.max(15, pomodoroConfig.dailyFocusGoalMinutes || 120)
  );
  const timerProgress =
    totalPhaseSeconds > 0 ? Math.max(0, Math.min(1, remainingSeconds / totalPhaseSeconds)) : 0;
  const ringRadius = 62;
  const ringCircumference = 2 * Math.PI * ringRadius;
  const ringDashOffset = ringCircumference * (1 - timerProgress);

  const handleAddTodo = (e: React.FormEvent) => {
    e.preventDefault();
    const clean = newTitle.trim();
    if (!clean) return;

    let linkedPaperId: string | null = null;
    let linkedNoteId: string | null = null;
    let linkedDeckLanguage = '';
    let linkedLabel = '';

    if (newLinkType === 'PAPER' && newLinkTargetId) {
      const p = papers.find((x) => x.metadata.id === newLinkTargetId);
      if (p) {
        linkedPaperId = p.metadata.id;
        linkedLabel = p.metadata.title;
      }
    } else if (newLinkType === 'NOTE' && newLinkTargetId) {
      const n = studyNotes.find((x) => x.id === newLinkTargetId);
      if (n) {
        linkedNoteId = n.id;
        linkedLabel = n.title;
      }
    } else if (newLinkType === 'VOCAB_DECK' && newLinkTargetId) {
      linkedDeckLanguage = newLinkTargetId;
      linkedLabel = `${newLinkTargetId} Vocab`;
    }

    const item: StudyTodoItem = {
      id: `todo-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      title: clean,
      priority: newPriority,
      dueDateStr: newDueDate,
      isCompleted: false,
      targetPomodoros: newTargetPomos,
      completedPomodoros: 0,
      linkedType: linkedLabel ? newLinkType : 'NONE',
      linkedPaperId,
      linkedNoteId,
      linkedDeckLanguage,
      linkedLabel,
      createdAt: Date.now(),
      completedAt: null,
    };
    onSaveTodos([item, ...todos]);
    setNewTitle('');
    onNotify(`Added study task "${clean}"`);
  };

  const priorityColors: Record<TodoPriority, { bg: string; fg: string }> = {
    HIGH: { bg: colors.redLight, fg: colors.red },
    MEDIUM: { bg: colors.amberLight, fg: colors.amber },
    LOW: { bg: colors.tealLight, fg: colors.tealDark },
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div
        className="rounded-2xl p-4 sm:p-5 border flex flex-col md:flex-row md:items-center justify-between gap-4"
        style={{
          backgroundColor: colors.surfaceVariant,
          borderColor: colors.border,
        }}
      >
        <div>
          <div className="flex items-center gap-2">
            <Clock className="w-5 h-5" style={{ color: colors.teal }} />
            <h2 className="font-black" style={{ fontSize: scalePx(20), color: colors.inkBold }}>
              Smart Study Planner &amp; Focus Clock
            </h2>
          </div>
          <p className="text-xs mt-1" style={{ color: colors.muted }}>
            Timestamp-locked Pomodoro timer paired with SM-2 / Vocab / Exam queues &amp; priority tasks
          </p>
        </div>

        {/* Analytics Strip */}
        <div className="grid grid-cols-3 gap-2 sm:gap-3">
          <div
            className="px-3 py-2 rounded-xl border text-center"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="text-xs font-black" style={{ color: colors.teal }}>
              {todayMinutes}m
            </div>
            <div className="text-[10px] font-semibold" style={{ color: colors.muted }}>
              Today Focus
            </div>
          </div>
          <div
            className="px-3 py-2 rounded-xl border text-center"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="flex items-center justify-center gap-1 text-xs font-black" style={{ color: colors.amber }}>
              <Flame className="w-3.5 h-3.5" />
              {analyticsSummary.streakDays}d
            </div>
            <div className="text-[10px] font-semibold" style={{ color: colors.muted }}>
              Study Streak
            </div>
          </div>
          <div
            className="px-3 py-2 rounded-xl border text-center"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="text-xs font-black" style={{ color: colors.inkBold }}>
              {analyticsSummary.sevenDayMinutes}m
            </div>
            <div className="text-[10px] font-semibold" style={{ color: colors.muted }}>
              7-Day Total
            </div>
          </div>
        </div>
      </div>

      {/* Main Split Grid: Left = Pomodoro Focus Clock + Countdowns | Right = Due Queue + Custom Todos */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* LEFT COLUMN (5 cols) */}
        <div className="lg:col-span-5 space-y-5">
          {/* Pomodoro Focus Clock Card */}
          <div
            className="rounded-2xl border p-5 space-y-4"
            style={{
              backgroundColor: pomodoroConfig.quietModeEnabled ? colors.navyBanner : colors.surface,
              borderColor: phase === 'FOCUS' ? colors.teal : colors.border,
              color: pomodoroConfig.quietModeEnabled ? colors.navyBannerText : colors.ink,
            }}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span
                  className="text-[11px] font-black px-2.5 py-1 rounded-full uppercase tracking-wide"
                  style={{
                    backgroundColor:
                      phase === 'FOCUS'
                        ? colors.teal
                        : phase === 'BREAK'
                        ? colors.amber
                        : colors.surfaceVariant,
                    color: phase === 'FOCUS' || phase === 'BREAK' ? '#FFFFFF' : colors.inkBold,
                  }}
                >
                  {phase === 'IDLE'
                    ? 'READY TO FOCUS'
                    : phase === 'FOCUS'
                    ? 'DEEP FOCUS ACTIVE'
                    : phase === 'BREAK'
                    ? 'RECOVERY BREAK'
                    : `PAUSED (${pausedFromPhase || 'FOCUS'})`}
                </span>
                {completedPomodorosSession > 0 && (
                  <span className="text-[11px] font-bold opacity-80">
                    {completedPomodorosSession} done this session
                  </span>
                )}
              </div>

              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={() =>
                    onSavePomodoroConfig({
                      ...pomodoroConfig,
                      chimeEnabled: !pomodoroConfig.chimeEnabled,
                    })
                  }
                  className="p-1.5 rounded-lg border"
                  style={{
                    backgroundColor: colors.surfaceVariant,
                    borderColor: colors.border,
                    color: pomodoroConfig.chimeEnabled ? colors.teal : colors.muted,
                  }}
                  title={pomodoroConfig.chimeEnabled ? 'Mute interval chime' : 'Enable interval chime'}
                >
                  {pomodoroConfig.chimeEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
                </button>
                <button
                  type="button"
                  onClick={handleToggleBrowserNotify}
                  className="p-1.5 rounded-lg border"
                  style={{
                    backgroundColor: colors.surfaceVariant,
                    borderColor: colors.border,
                    color: pomodoroConfig.browserNotifyEnabled ? colors.teal : colors.muted,
                  }}
                  title="Toggle browser completion notification"
                >
                  {pomodoroConfig.browserNotifyEnabled ? (
                    <Bell className="w-4 h-4" />
                  ) : (
                    <BellOff className="w-4 h-4" />
                  )}
                </button>
              </div>
            </div>

            {/* Circular Timer Ring */}
            <div className="flex flex-col items-center justify-center py-2">
              <div className="relative w-40 h-40 flex items-center justify-center">
                <svg className="w-40 h-40 -rotate-90" viewBox="0 0 144 144">
                  <circle
                    cx="72"
                    cy="72"
                    r={ringRadius}
                    fill="none"
                    stroke={pomodoroConfig.quietModeEnabled ? 'rgba(255,255,255,0.18)' : colors.border}
                    strokeWidth="9"
                  />
                  <circle
                    cx="72"
                    cy="72"
                    r={ringRadius}
                    fill="none"
                    stroke={phase === 'BREAK' ? colors.amber : colors.teal}
                    strokeWidth="9"
                    strokeLinecap="round"
                    strokeDasharray={ringCircumference}
                    strokeDashoffset={ringDashOffset}
                  />
                </svg>
                <div className="absolute inset-0 flex flex-col items-center justify-center">
                  <div
                    className="font-mono font-black tracking-tight"
                    style={{ fontSize: scalePx(30) }}
                  >
                    {formatMmSs(remainingSeconds)}
                  </div>
                  <div className="text-[11px] font-semibold opacity-75">
                    {activeFocusMinutes}m / {activeBreakMinutes}m
                  </div>
                </div>
              </div>

              {linkedTodoObj && (
                <div
                  className="mt-2 px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1.5 max-w-full"
                  style={{ backgroundColor: colors.tealLight, color: colors.tealDark }}
                >
                  <Target className="w-3.5 h-3.5 flex-shrink-0" />
                  <span className="truncate">{linkedTodoObj.title}</span>
                  <button
                    type="button"
                    onClick={() => setLinkedTodoId(null)}
                    className="ml-1 text-[10px] underline"
                  >
                    unlink
                  </button>
                </div>
              )}
            </div>

            {/* Timer Action Controls */}
            <div className="flex flex-wrap items-center justify-center gap-2">
              {phase === 'IDLE' && (
                <button
                  type="button"
                  onClick={() => handleStartFocus()}
                  className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-black text-white shadow-sm"
                  style={{ backgroundColor: colors.teal }}
                >
                  <Play className="w-4 h-4" />
                  Start Focus ({activeFocusMinutes}m)
                </button>
              )}
              {(phase === 'FOCUS' || phase === 'BREAK') && (
                <button
                  type="button"
                  onClick={handlePause}
                  className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-xs font-black text-white"
                  style={{ backgroundColor: colors.amber }}
                >
                  <Pause className="w-4 h-4" />
                  Pause
                </button>
              )}
              {phase === 'PAUSED' && (
                <button
                  type="button"
                  onClick={handleResume}
                  className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-xs font-black text-white"
                  style={{ backgroundColor: colors.teal }}
                >
                  <Play className="w-4 h-4" />
                  Resume
                </button>
              )}
              {phase === 'FOCUS' && (
                <button
                  type="button"
                  onClick={handleSkipToBreak}
                  className="flex items-center gap-1.5 px-3 py-2.5 rounded-xl text-xs font-bold border"
                  style={{
                    backgroundColor: colors.surfaceVariant,
                    borderColor: colors.border,
                    color: colors.inkBold,
                  }}
                >
                  <SkipForward className="w-3.5 h-3.5" />
                  Break
                </button>
              )}
              {phase !== 'IDLE' && (
                <button
                  type="button"
                  onClick={handleResetTimer}
                  className="flex items-center gap-1.5 px-3 py-2.5 rounded-xl text-xs font-bold border"
                  style={{
                    backgroundColor: colors.surfaceVariant,
                    borderColor: colors.border,
                    color: colors.inkBold,
                  }}
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  Reset
                </button>
              )}
            </div>

            {/* Presets */}
            <div className="pt-2 border-t space-y-2" style={{ borderColor: colors.borderLight }}>
              <div className="grid grid-cols-2 gap-1.5">
                {(Object.keys(POMODORO_PRESET_META) as PomodoroPreset[]).map((presetKey) => {
                  const meta = POMODORO_PRESET_META[presetKey];
                  const selected = pomodoroConfig.preset === presetKey;
                  return (
                    <button
                      key={presetKey}
                      type="button"
                      onClick={() => onSavePomodoroConfig({ ...pomodoroConfig, preset: presetKey })}
                      className="px-2.5 py-1.5 rounded-lg text-xs font-bold border text-left transition"
                      style={
                        selected
                          ? { backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.tealDark }
                          : { backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }
                      }
                    >
                      {meta.label}
                    </button>
                  );
                })}
              </div>

              {pomodoroConfig.preset === 'CUSTOM' && (
                <div className="grid grid-cols-2 gap-2 text-xs pt-1">
                  <label className="block">
                    <span className="text-[10px] font-bold opacity-80">Focus (min)</span>
                    <input
                      type="number"
                      min={5}
                      max={180}
                      value={pomodoroConfig.customFocusMinutes}
                      onChange={(e) =>
                        onSavePomodoroConfig({
                          ...pomodoroConfig,
                          customFocusMinutes: Math.max(5, Math.min(180, Number(e.target.value) || 25)),
                        })
                      }
                      className="mt-0.5 w-full px-2.5 py-1.5 rounded-lg border"
                      style={{
                        backgroundColor: colors.surfaceVariant,
                        borderColor: colors.border,
                        color: colors.inkBold,
                      }}
                    />
                  </label>
                  <label className="block">
                    <span className="text-[10px] font-bold opacity-80">Break (min)</span>
                    <input
                      type="number"
                      min={1}
                      max={60}
                      value={pomodoroConfig.customBreakMinutes}
                      onChange={(e) =>
                        onSavePomodoroConfig({
                          ...pomodoroConfig,
                          customBreakMinutes: Math.max(1, Math.min(60, Number(e.target.value) || 5)),
                        })
                      }
                      className="mt-0.5 w-full px-2.5 py-1.5 rounded-lg border"
                      style={{
                        backgroundColor: colors.surfaceVariant,
                        borderColor: colors.border,
                        color: colors.inkBold,
                      }}
                    />
                  </label>
                </div>
              )}

              {/* Quiet Focus Shield + Daily Goal */}
              <div className="flex items-center justify-between text-xs pt-1">
                <label className="flex items-center gap-2 cursor-pointer font-semibold">
                  <input
                    type="checkbox"
                    checked={pomodoroConfig.quietModeEnabled}
                    onChange={(e) =>
                      onSavePomodoroConfig({ ...pomodoroConfig, quietModeEnabled: e.target.checked })
                    }
                  />
                  High-Contrast Quiet Backdrop
                </label>
                <span className="text-[11px] font-bold" style={{ color: colors.teal }}>
                  Goal: {todayMinutes}/{pomodoroConfig.dailyFocusGoalMinutes}m ({Math.round(goalProgress * 100)}%)
                </span>
              </div>
              <div className="w-full h-1.5 rounded-full overflow-hidden" style={{ backgroundColor: colors.border }}>
                <div
                  className="h-full rounded-full transition-all"
                  style={{ width: `${Math.round(goalProgress * 100)}%`, backgroundColor: colors.teal }}
                />
              </div>
            </div>
          </div>

          {/* Exam Target Countdowns Card */}
          <div
            className="rounded-2xl border p-4 space-y-3"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <CalendarClock className="w-4 h-4" style={{ color: colors.teal }} />
                <h3 className="text-sm font-black" style={{ color: colors.inkBold }}>
                  Exam Target Countdowns
                </h3>
              </div>
              <button
                type="button"
                onClick={onOpenCountdownsModal}
                className="text-xs font-bold px-2.5 py-1 rounded-lg border"
                style={{
                  backgroundColor: colors.tealLight,
                  borderColor: colors.border,
                  color: colors.tealDark,
                }}
              >
                Manage
              </button>
            </div>
            {countdowns.length === 0 ? (
              <p className="text-xs" style={{ color: colors.muted }}>
                No upcoming exam milestones set. Click Manage to pin an exam countdown.
              </p>
            ) : (
              <div className="space-y-2">
                {countdowns.slice(0, 4).map((cd) => {
                  const days = StudyDateUtils.computeDaysRemaining(cd.targetDateIso);
                  return (
                    <div
                      key={cd.id}
                      className="flex items-center justify-between px-3 py-2 rounded-xl border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                    >
                      <div className="min-w-0">
                        <div className="text-xs font-bold truncate" style={{ color: colors.inkBold }}>
                          {cd.title}
                        </div>
                        <div className="text-[10px]" style={{ color: colors.muted }}>
                          {cd.examType} • {StudyDateUtils.formatHumanDate(cd.targetDateIso)}
                        </div>
                      </div>
                      <span
                        className="text-xs font-black px-2.5 py-1 rounded-full flex-shrink-0"
                        style={{
                          backgroundColor: days !== null && days <= 14 ? colors.redLight : colors.tealLight,
                          color: days !== null && days <= 14 ? colors.red : colors.tealDark,
                        }}
                      >
                        {days === null
                          ? cd.targetDateIso
                          : days > 0
                          ? `${days}d left`
                          : days === 0
                          ? 'Today!'
                          : `${-days}d ago`}
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* RIGHT COLUMN (7 cols): Auto-Detected Due Queue + Study To-Do Manager */}
        <div className="lg:col-span-7 space-y-5">
          {/* Auto-Detected Due Revision Queue */}
          <div
            className="rounded-2xl border p-4 space-y-3"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Sparkles className="w-4 h-4" style={{ color: colors.teal }} />
                <h3 className="text-sm font-black" style={{ color: colors.inkBold }}>
                  Auto-Detected Due Queue
                </h3>
              </div>
              <span
                className="text-[11px] font-bold px-2 py-0.5 rounded-full"
                style={{ backgroundColor: colors.tealLight, color: colors.tealDark }}
              >
                {dueSuggestions.length} item(s)
              </span>
            </div>

            {dueSuggestions.length === 0 ? (
              <div
                className="p-4 rounded-xl text-xs text-center"
                style={{ backgroundColor: colors.surfaceVariant, color: colors.muted }}
              >
                All SM-2 flashcards, vocabulary decks, headword inbox items, and paused tests are caught up.
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                {dueSuggestions.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => onJumpToSuggestion(item)}
                    className="p-3 rounded-xl border text-left hover:opacity-95 transition flex flex-col justify-between gap-2"
                    style={{
                      backgroundColor: colors.surfaceVariant,
                      borderColor: colors.border,
                    }}
                  >
                    <div className="flex items-center justify-between gap-2 w-full">
                      <span
                        className="text-[10px] font-black px-2 py-0.5 rounded-full uppercase"
                        style={{ backgroundColor: colors.tealLight, color: colors.tealDark }}
                      >
                        {item.category.replace('_', ' ')}
                      </span>
                      <span className="text-xs font-black" style={{ color: colors.teal }}>
                        {item.count}
                      </span>
                    </div>
                    <div>
                      <div className="text-xs font-bold line-clamp-1" style={{ color: colors.inkBold }}>
                        {item.title}
                      </div>
                      <div className="text-[11px] line-clamp-1" style={{ color: colors.muted }}>
                        {item.subtitle}
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Custom Study To-Do List */}
          <div
            className="rounded-2xl border p-4 sm:p-5 space-y-4"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-sm font-black" style={{ color: colors.inkBold }}>
                Study Tasks &amp; Pomodoro Targets
              </h3>
              <div className="flex flex-wrap gap-1.5">
                {(
                  [
                    { id: 'ALL', label: 'Active' },
                    { id: 'TODAY', label: 'Today' },
                    { id: 'HIGH_PRIORITY', label: 'High Priority' },
                    { id: 'COMPLETED', label: 'Completed' },
                  ] as { id: TodoFilter; label: string }[]
                ).map((f) => {
                  const sel = todoFilter === f.id;
                  return (
                    <button
                      key={f.id}
                      type="button"
                      onClick={() => setTodoFilter(f.id)}
                      className="px-2.5 py-1 rounded-lg text-xs font-bold border"
                      style={
                        sel
                          ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText, borderColor: colors.primaryButtonBg }
                          : { backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }
                      }
                    >
                      {f.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Composer Form */}
            <form
              onSubmit={handleAddTodo}
              className="p-3 rounded-xl border space-y-2.5"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
            >
              <div className="flex flex-col sm:flex-row gap-2">
                <input
                  type="text"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="Add study goal (e.g. Solve Polity Mock + Revise Constitutional Articles)"
                  className="flex-1 px-3 py-2 rounded-lg border text-xs font-medium focus:outline-none"
                  style={{
                    backgroundColor: colors.surface,
                    borderColor: colors.border,
                    color: colors.inkBold,
                  }}
                />
                <button
                  type="submit"
                  className="flex items-center justify-center gap-1.5 px-4 py-2 rounded-lg text-xs font-black text-white flex-shrink-0"
                  style={{ backgroundColor: colors.teal }}
                >
                  <Plus className="w-4 h-4" />
                  Add Task
                </button>
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
                <select
                  value={newPriority}
                  onChange={(e) => setNewPriority(e.target.value as TodoPriority)}
                  className="px-2.5 py-1.5 rounded-lg border font-semibold"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                >
                  <option value="HIGH">High Priority</option>
                  <option value="MEDIUM">Medium Priority</option>
                  <option value="LOW">Low Priority</option>
                </select>

                <input
                  type="date"
                  value={newDueDate}
                  onChange={(e) => setNewDueDate(e.target.value)}
                  className="px-2.5 py-1.5 rounded-lg border font-semibold"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                />

                <select
                  value={newTargetPomos}
                  onChange={(e) => setNewTargetPomos(Number(e.target.value))}
                  className="px-2.5 py-1.5 rounded-lg border font-semibold"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                >
                  {[1, 2, 3, 4, 5, 6, 8].map((n) => (
                    <option key={n} value={n}>
                      Target: {n} Pomodoro{n > 1 ? 's' : ''}
                    </option>
                  ))}
                </select>

                <select
                  value={newLinkType}
                  onChange={(e) => {
                    const nextType = e.target.value as TodoLinkType;
                    setNewLinkType(nextType);
                    if (nextType === 'PAPER') setNewLinkTargetId(papers[0]?.metadata.id || '');
                    else if (nextType === 'NOTE') setNewLinkTargetId(studyNotes[0]?.id || '');
                    else if (nextType === 'VOCAB_DECK') setNewLinkTargetId(vocabLanguages[0] || '');
                    else setNewLinkTargetId('');
                  }}
                  className="px-2.5 py-1.5 rounded-lg border font-semibold"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                >
                  <option value="NONE">No Material Link</option>
                  <option value="PAPER">Link Exam Paper</option>
                  <option value="NOTE">Link Study Note</option>
                  <option value="VOCAB_DECK">Link Vocab Lang</option>
                </select>
              </div>

              {newLinkType !== 'NONE' && (
                <select
                  value={newLinkTargetId}
                  onChange={(e) => setNewLinkTargetId(e.target.value)}
                  className="w-full px-2.5 py-1.5 rounded-lg border text-xs font-semibold"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                >
                  {newLinkType === 'PAPER' &&
                    papers.map((p) => (
                      <option key={p.metadata.id} value={p.metadata.id}>
                        Paper: {p.metadata.title} ({p.questions.length} Qs)
                      </option>
                    ))}
                  {newLinkType === 'NOTE' &&
                    studyNotes.map((n) => (
                      <option key={n.id} value={n.id}>
                        Note: {n.title}
                      </option>
                    ))}
                  {newLinkType === 'VOCAB_DECK' &&
                    vocabLanguages.map((lang) => (
                      <option key={lang} value={lang}>
                        Vocabulary: {lang}
                      </option>
                    ))}
                </select>
              )}
            </form>

            {/* Task List */}
            {visibleTodos.length === 0 ? (
              <div
                className="p-6 rounded-xl text-center text-xs"
                style={{ backgroundColor: colors.surfaceVariant, color: colors.muted }}
              >
                No tasks match the selected filter. Add one above or switch tabs!
              </div>
            ) : (
              <div className="space-y-2">
                {visibleTodos.map((todo) => {
                  const pMeta = priorityColors[todo.priority];
                  const isLinkedToTimer = linkedTodoId === todo.id;
                  return (
                    <div
                      key={todo.id}
                      className="p-3 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                      style={{
                        backgroundColor: isLinkedToTimer ? colors.tealLight : colors.surfaceVariant,
                        borderColor: isLinkedToTimer ? colors.teal : colors.border,
                      }}
                    >
                      <div className="flex items-start gap-2.5 min-w-0">
                        <button
                          type="button"
                          onClick={() =>
                            onSaveTodos(
                              todos.map((t) =>
                                t.id === todo.id
                                  ? {
                                      ...t,
                                      isCompleted: !t.isCompleted,
                                      completedAt: !t.isCompleted ? Date.now() : null,
                                    }
                                  : t
                              )
                            )
                          }
                          className="mt-0.5 flex-shrink-0"
                          aria-label="Toggle task complete"
                        >
                          {todo.isCompleted ? (
                            <CheckCircle2 className="w-5 h-5" style={{ color: colors.teal }} />
                          ) : (
                            <Circle className="w-5 h-5" style={{ color: colors.muted }} />
                          )}
                        </button>
                        <div className="min-w-0">
                          <div
                            className={`text-xs font-bold ${todo.isCompleted ? 'line-through opacity-65' : ''}`}
                            style={{ color: colors.inkBold }}
                          >
                            {todo.title}
                          </div>
                          <div className="flex flex-wrap items-center gap-1.5 mt-1">
                            <span
                              className="text-[10px] font-black px-2 py-0.5 rounded-full"
                              style={{ backgroundColor: pMeta.bg, color: pMeta.fg }}
                            >
                              {todo.priority}
                            </span>
                            {todo.dueDateStr && (
                              <span className="text-[10px] font-semibold" style={{ color: colors.muted }}>
                                Due {todo.dueDateStr}
                              </span>
                            )}
                            <span className="text-[10px] font-bold" style={{ color: colors.ink }}>
                              🍅 {todo.completedPomodoros}/{todo.targetPomodoros}
                            </span>
                            {todo.linkedLabel && (
                              <button
                                type="button"
                                onClick={() => onJumpToTodoLink(todo)}
                                className="inline-flex items-center gap-1 text-[10px] font-bold px-2 py-0.5 rounded-full underline"
                                style={{ backgroundColor: colors.surface, color: colors.teal }}
                              >
                                <Link2 className="w-3 h-3" />
                                {todo.linkedLabel}
                              </button>
                            )}
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 flex-shrink-0">
                        {!todo.isCompleted && (
                          <button
                            type="button"
                            onClick={() => handleStartFocus(todo.id)}
                            className="px-2.5 py-1.5 rounded-lg text-[11px] font-bold text-white flex items-center gap-1"
                            style={{ backgroundColor: colors.teal }}
                            title="Link & start Pomodoro Focus block on this task"
                          >
                            <Play className="w-3 h-3" />
                            Focus
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() =>
                            onSaveTodos(
                              todos.map((t) =>
                                t.id === todo.id
                                  ? { ...t, completedPomodoros: t.completedPomodoros + 1 }
                                  : t
                              )
                            )
                          }
                          className="px-2 py-1.5 rounded-lg text-[11px] font-bold border"
                          style={{
                            backgroundColor: colors.surface,
                            borderColor: colors.border,
                            color: colors.inkBold,
                          }}
                          title="Log +1 completed Pomodoro block"
                        >
                          +1 🍅
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            if (linkedTodoId === todo.id) setLinkedTodoId(null);
                            onSaveTodos(todos.filter((t) => t.id !== todo.id));
                          }}
                          className="p-1.5 rounded-lg"
                          style={{ color: colors.red }}
                          aria-label="Delete task"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default StudyPlannerTab;
