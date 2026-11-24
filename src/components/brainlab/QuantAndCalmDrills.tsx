import React, { useEffect, useMemo, useRef, useState } from 'react';
import { CheckCircle2, Eye, Play, RotateCcw, Sparkles } from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import {
  BREATHING_PROTOCOLS,
  type BrainLabSettings,
  type BrainLabStats,
  type BreathingProtocol,
} from '../../types/phase5';
import {
  generateQuantQuestion,
  generateSchulteBoard,
  generateZenLogicPuzzle,
  isZenGridSolvedCorrectly,
  playSubtleChime,
  QUANT_BADGE_MAP,
  sampleBreathingState,
  SCHULTE_MODE_META,
  SOFT_RESET_STEPS,
  type QuantQuestion,
  type SchulteBoard,
  type SchulteMode,
  type ZenLogicPuzzle,
} from '../../utils/brainLabEngines';

interface DrillCommonProps {
  stats: BrainLabStats;
  settings: BrainLabSettings;
  onUpdateStats: (updater: (prev: BrainLabStats) => BrainLabStats, studySeconds?: number) => void;
}

function minPos(a: number, b: number): number {
  if (a <= 0) return b;
  if (b <= 0) return a;
  return Math.min(a, b);
}

// =========================================================================
// 8. Schulte Table & Alternating Trail Grid
// =========================================================================

export const SchulteTrailDrill: React.FC<DrillCommonProps> = ({ stats, settings, onUpdateStats }) => {
  const { colors } = useTheme();
  const [mode, setMode] = useState<SchulteMode>('SCHULTE_5X5');
  const [board, setBoard] = useState<SchulteBoard>(() => generateSchulteBoard('SCHULTE_5X5'));
  const [dimCompleted, setDimCompleted] = useState<boolean>(true);
  const [nextIdx, setNextIdx] = useState<number>(0);
  const [elapsedMs, setElapsedMs] = useState<number>(0);
  const [isRunning, setIsRunning] = useState<boolean>(false);
  const [isFinished, setIsFinished] = useState<boolean>(false);
  const startMsRef = useRef<number>(0);

  useEffect(() => {
    if (!isRunning) return;
    const t = setInterval(() => {
      setElapsedMs(Date.now() - startMsRef.current);
    }, 100);
    return () => clearInterval(t);
  }, [isRunning]);

  const startBoard = (m: SchulteMode = mode) => {
    setMode(m);
    setBoard(generateSchulteBoard(m));
    setNextIdx(0);
    setElapsedMs(0);
    setIsFinished(false);
    startMsRef.current = Date.now();
    setIsRunning(true);
  };

  const handleTapCell = (label: string) => {
    if (isFinished) return;
    if (!isRunning) {
      startMsRef.current = Date.now();
      setIsRunning(true);
    }
    if (board.expectedOrder[nextIdx] !== label) {
      if (settings.audioEnabled) playSubtleChime(280, 70);
      return;
    }

    const updatedIdx = nextIdx + 1;
    setNextIdx(updatedIdx);

    if (updatedIdx >= board.expectedOrder.length) {
      const totalMs = Math.max(500, Date.now() - startMsRef.current);
      setElapsedMs(totalMs);
      setIsRunning(false);
      setIsFinished(true);
      if (settings.audioEnabled) playSubtleChime(880, 180);

      onUpdateStats((prev) => ({
        ...prev,
        bestSchulte5x5Ms: mode === 'SCHULTE_5X5' ? minPos(prev.bestSchulte5x5Ms, totalMs) : prev.bestSchulte5x5Ms,
        bestSchulte6x6Ms: mode === 'SCHULTE_6X6' ? minPos(prev.bestSchulte6x6Ms, totalMs) : prev.bestSchulte6x6Ms,
        bestTrail5x5Ms: mode === 'TRAIL_5X5' ? minPos(prev.bestTrail5x5Ms, totalMs) : prev.bestTrail5x5Ms,
      }), Math.max(1, Math.round(totalMs / 1000)));
    }
  };

  const bestForMode =
    mode === 'SCHULTE_5X5'
      ? stats.bestSchulte5x5Ms
      : mode === 'SCHULTE_6X6'
      ? stats.bestSchulte6x6Ms
      : stats.bestTrail5x5Ms;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
        <div className="flex flex-wrap gap-1.5">
          {(Object.keys(SCHULTE_MODE_META) as SchulteMode[]).map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => startBoard(m)}
              className="px-2.5 py-1.5 rounded-lg border font-bold"
              style={
                mode === m
                  ? { backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.tealDark }
                  : { backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }
              }
            >
              {SCHULTE_MODE_META[m].label}
            </button>
          ))}
        </div>
        <label className="flex items-center gap-1.5 cursor-pointer font-semibold">
          <input
            type="checkbox"
            checked={dimCompleted}
            onChange={(e) => setDimCompleted(e.target.checked)}
          />
          Dim cleared cells
        </label>
      </div>

      <div className="max-w-md mx-auto space-y-3">
        <div
          className="flex items-center justify-between px-4 py-2.5 rounded-xl border text-xs font-black"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <span style={{ color: colors.teal }}>
            {isFinished ? 'Completed!' : `Find: ${board.expectedOrder[nextIdx] || 'Done'}`}
          </span>
          <span style={{ color: colors.inkBold }}>
            {(elapsedMs / 1000).toFixed(1)}s{' '}
            {bestForMode > 0 && <span className="font-semibold opacity-70">(Best {(bestForMode / 1000).toFixed(1)}s)</span>}
          </span>
        </div>

        <div
          className="grid gap-1.5"
          style={{ gridTemplateColumns: `repeat(${board.gridSize}, minmax(0, 1fr))` }}
        >
          {board.cells.map((cell) => {
            const ordIdx = board.expectedOrder.indexOf(cell);
            const alreadyCleared = ordIdx >= 0 && ordIdx < nextIdx;
            return (
              <button
                key={cell}
                type="button"
                onClick={() => handleTapCell(cell)}
                className="aspect-square rounded-xl border text-sm font-black transition"
                style={{
                  backgroundColor: alreadyCleared && dimCompleted ? colors.surface : colors.surfaceVariant,
                  borderColor: alreadyCleared ? colors.borderLight : colors.border,
                  color: alreadyCleared && dimCompleted ? colors.muted : colors.inkBold,
                  opacity: alreadyCleared && dimCompleted ? 0.35 : 1,
                }}
              >
                {cell}
              </button>
            );
          })}
        </div>

        <div className="flex justify-center">
          <button
            type="button"
            onClick={() => startBoard(mode)}
            className="px-4 py-2 rounded-xl text-xs font-bold border inline-flex items-center gap-1.5"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
          >
            <RotateCcw className="w-3.5 h-3.5" />
            Shuffle New Board
          </button>
        </div>
      </div>
    </div>
  );
};

// =========================================================================
// 9. Mental Quant 60s Blitz
// =========================================================================

export const MentalQuantDrill: React.FC<DrillCommonProps> = ({ stats, settings, onUpdateStats }) => {
  const { colors } = useTheme();
  const BLITZ_SECS = 60;
  const [phase, setPhase] = useState<'SETUP' | 'RUNNING' | 'SUMMARY'>('SETUP');
  const [timeLeft, setTimeLeft] = useState<number>(BLITZ_SECS);
  const [question, setQuestion] = useState<QuantQuestion>(() => generateQuantQuestion(1));
  const [score, setScore] = useState<number>(0);
  const [answered, setAnswered] = useState<number>(0);
  const [flashErr, setFlashErr] = useState<string | null>(null);
  const deadlineRef = useRef<number>(0);
  const scoreRef = useRef(score);
  scoreRef.current = score;

  useEffect(() => {
    if (phase !== 'RUNNING') return;
    const t = setInterval(() => {
      const rem = Math.max(0, Math.ceil((deadlineRef.current - Date.now()) / 1000));
      setTimeLeft(rem);
      if (rem <= 0) {
        clearInterval(t);
        setPhase('SUMMARY');
        if (settings.audioEnabled) playSubtleChime(780, 200);
        onUpdateStats(
          (prev) => ({
            ...prev,
            bestQuantBlitzScore: Math.max(prev.bestQuantBlitzScore, scoreRef.current),
          }),
          BLITZ_SECS
        );
      }
    }, 250);
    return () => clearInterval(t);
  }, [phase, settings.audioEnabled, onUpdateStats]);

  const startBlitz = () => {
    setScore(0);
    setAnswered(0);
    setFlashErr(null);
    setTimeLeft(BLITZ_SECS);
    setQuestion(generateQuantQuestion(1));
    deadlineRef.current = Date.now() + BLITZ_SECS * 1000;
    setPhase('RUNNING');
  };

  const handleAnswer = (optIdx: number) => {
    if (phase !== 'RUNNING') return;
    const isCorrect = optIdx === question.correctIndex;
    setAnswered((a) => a + 1);
    if (isCorrect) {
      setScore((s) => s + 1);
      setFlashErr(null);
      if (settings.audioEnabled) playSubtleChime(760, 75);
      setQuestion(generateQuantQuestion(answered + 2));
    } else {
      setFlashErr(question.explanation);
      if (settings.audioEnabled) playSubtleChime(290, 90);
      setTimeout(() => {
        setFlashErr(null);
        setQuestion(generateQuantQuestion(answered + 2));
      }, 950);
    }
  };

  return (
    <div className="space-y-4">
      {phase === 'SETUP' && (
        <div
          className="p-5 rounded-2xl border space-y-3"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <p className="text-xs" style={{ color: colors.ink }}>
            60-second sprint covering fraction-to-% conversions, compound shifts, ratio duels &amp; fast approximations.
            Personal Best: <strong>{stats.bestQuantBlitzScore}</strong> solved in 60s.
          </p>
          <button
            type="button"
            onClick={startBlitz}
            className="px-5 py-2.5 rounded-xl text-xs font-black text-white inline-flex items-center gap-1.5"
            style={{ backgroundColor: colors.teal }}
          >
            <Play className="w-4 h-4" />
            Start 60s Quant Blitz
          </button>
        </div>
      )}

      {phase === 'RUNNING' && (
        <div className="max-w-md mx-auto space-y-4">
          <div className="flex items-center justify-between text-xs font-black">
            <span style={{ color: timeLeft <= 10 ? colors.red : colors.teal }}>⏱ {timeLeft}s left</span>
            <span style={{ color: colors.inkBold }}>Score: {score}</span>
          </div>

          <div
            className="p-4 rounded-2xl border space-y-2"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
          >
            <span
              className="text-[10px] font-black px-2 py-0.5 rounded-full"
              style={{ backgroundColor: colors.tealLight, color: colors.tealDark }}
            >
              {QUANT_BADGE_MAP[question.type]}
            </span>
            <div className="text-sm font-black" style={{ color: colors.inkBold }}>
              {question.prompt}
            </div>
          </div>

          {flashErr && (
            <div
              className="p-2.5 rounded-xl border text-xs font-semibold"
              style={{ backgroundColor: colors.redLight, borderColor: colors.red, color: colors.red }}
            >
              {flashErr}
            </div>
          )}

          <div className="grid grid-cols-2 gap-2.5">
            {question.options.map((opt, idx) => (
              <button
                key={`${question.id}-${opt}`}
                type="button"
                disabled={flashErr !== null}
                onClick={() => handleAnswer(idx)}
                className="py-3 px-2 rounded-xl border text-xs font-black"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              >
                {opt}
              </button>
            ))}
          </div>
        </div>
      )}

      {phase === 'SUMMARY' && (
        <div
          className="max-w-md mx-auto p-5 rounded-2xl border text-center space-y-3"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.teal }}
        >
          <div className="text-2xl font-black" style={{ color: colors.teal }}>
            {score} Correct in 60s
          </div>
          <p className="text-xs" style={{ color: colors.muted }}>
            Attempted {answered} questions ({answered > 0 ? Math.round((score / answered) * 100) : 0}% accuracy)
          </p>
          <button
            type="button"
            onClick={startBlitz}
            className="px-5 py-2 rounded-xl text-xs font-black text-white"
            style={{ backgroundColor: colors.teal }}
          >
            Run Blitz Again
          </button>
        </div>
      )}
    </div>
  );
};

// =========================================================================
// 10. Guided Breathing Pacer
// =========================================================================

export const GuidedBreathingDrill: React.FC<DrillCommonProps> = ({ settings, onUpdateStats }) => {
  const { colors, scalePx } = useTheme();
  const [protocol, setProtocol] = useState<BreathingProtocol>('BOX_4_4_4_4');
  const [isRunning, setIsRunning] = useState<boolean>(false);
  const [elapsedMs, setElapsedMs] = useState<number>(0);
  const startMsRef = useRef<number>(0);
  const lastPhaseRef = useRef<number>(-1);

  useEffect(() => {
    if (!isRunning) return;
    let rafId = 0;
    const tick = () => {
      const el = Date.now() - startMsRef.current;
      setElapsedMs(el);
      const sample = sampleBreathingState(protocol, el);
      if (sample.phaseIndex !== lastPhaseRef.current) {
        lastPhaseRef.current = sample.phaseIndex;
        if (settings.audioEnabled) playSubtleChime(sample.phaseIndex === 0 ? 528 : 396, 120);
      }
      rafId = requestAnimationFrame(tick);
    };
    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [isRunning, protocol, settings.audioEnabled]);

  const sample = useMemo(() => sampleBreathingState(protocol, elapsedMs), [protocol, elapsedMs]);

  const handleStopAndLog = () => {
    setIsRunning(false);
    const secs = Math.max(1, Math.round(elapsedMs / 1000));
    if (secs >= 5) {
      onUpdateStats(
        (prev) => ({
          ...prev,
          calmSessionsCompleted: prev.calmSessionsCompleted + 1,
        }),
        settings.countCalmInDailyStudyTime ? secs : 0
      );
    }
    setElapsedMs(0);
    lastPhaseRef.current = -1;
  };

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
        {(Object.keys(BREATHING_PROTOCOLS) as BreathingProtocol[]).map((key) => {
          const p = BREATHING_PROTOCOLS[key];
          const sel = protocol === key;
          return (
            <button
              key={key}
              type="button"
              onClick={() => {
                setProtocol(key);
                if (isRunning) {
                  startMsRef.current = Date.now();
                  lastPhaseRef.current = -1;
                }
              }}
              className="p-3 rounded-xl border text-left"
              style={
                sel
                  ? { backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.inkBold }
                  : { backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }
              }
            >
              <div className="text-xs font-black">
                {p.title} ({p.ratioLabel})
              </div>
              <div className="text-[11px] mt-0.5 opacity-80">{p.description}</div>
            </button>
          );
        })}
      </div>

      <div className="flex flex-col items-center justify-center py-6 space-y-4">
        <div
          className="w-48 h-48 rounded-full flex items-center justify-center relative"
          style={{ backgroundColor: colors.surfaceVariant, border: `2px solid ${colors.border}` }}
        >
          <div
            className="rounded-full flex flex-col items-center justify-center transition-transform duration-75"
            style={{
              width: '100%',
              height: '100%',
              transform: `scale(${sample.ringScale})`,
              backgroundColor: colors.tealLight,
              border: `3px solid ${colors.teal}`,
            }}
          />
          <div className="absolute inset-0 flex flex-col items-center justify-center text-center px-4">
            <div className="font-black" style={{ fontSize: scalePx(20), color: colors.inkBold }}>
              {isRunning ? sample.cueLabel : 'Ready'}
            </div>
            <div className="text-xs font-bold mt-1" style={{ color: colors.teal }}>
              {isRunning ? `${sample.secondsRemainingInPhase}s • Cycle ${sample.cycleCount + 1}` : 'Press Start'}
            </div>
          </div>
        </div>

        <p className="text-xs text-center max-w-sm" style={{ color: colors.muted }}>
          {sample.subCue}
        </p>

        {!isRunning ? (
          <button
            type="button"
            onClick={() => {
              startMsRef.current = Date.now();
              lastPhaseRef.current = -1;
              setIsRunning(true);
            }}
            className="px-6 py-2.5 rounded-xl text-xs font-black text-white flex items-center gap-2"
            style={{ backgroundColor: colors.teal }}
          >
            <Play className="w-4 h-4" />
            Begin Guided Breathing
          </button>
        ) : (
          <button
            type="button"
            onClick={handleStopAndLog}
            className="px-6 py-2.5 rounded-xl text-xs font-black border"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
          >
            Finish &amp; Log Reset ({Math.round(elapsedMs / 1000)}s)
          </button>
        )}
      </div>
    </div>
  );
};

// =========================================================================
// 11. 60-Second Visual Soft-Reset (20-20-20)
// =========================================================================

export const VisualSoftResetDrill: React.FC<DrillCommonProps> = ({ settings, onUpdateStats }) => {
  const { colors } = useTheme();
  const [stepIdx, setStepIdx] = useState<number>(0);
  const [secInStep, setSecInStep] = useState<number>(20);
  const [running, setRunning] = useState<boolean>(false);
  const [done, setDone] = useState<boolean>(false);

  useEffect(() => {
    if (!running) return;
    const t = setInterval(() => {
      setSecInStep((prev) => {
        if (prev > 1) return prev - 1;
        if (stepIdx + 1 >= SOFT_RESET_STEPS.length) {
          clearInterval(t);
          setRunning(false);
          setDone(true);
          if (settings.audioEnabled) playSubtleChime(660, 220);
          onUpdateStats(
            (s) => ({ ...s, calmSessionsCompleted: s.calmSessionsCompleted + 1 }),
            settings.countCalmInDailyStudyTime ? 60 : 0
          );
          return 0;
        }
        setStepIdx((i) => i + 1);
        if (settings.audioEnabled) playSubtleChime(520, 120);
        return SOFT_RESET_STEPS[stepIdx + 1].durationSeconds;
      });
    }, 1000);
    return () => clearInterval(t);
  }, [running, stepIdx, settings, onUpdateStats]);

  const currentStep = SOFT_RESET_STEPS[stepIdx];

  return (
    <div
      className="max-w-lg mx-auto p-5 rounded-2xl border text-center space-y-4"
      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
    >
      <Eye className="w-8 h-8 mx-auto" style={{ color: colors.teal }} />
      {done ? (
        <>
          <div className="text-lg font-black" style={{ color: colors.inkBold }}>
            Visual &amp; Posture Reset Complete
          </div>
          <p className="text-xs" style={{ color: colors.muted }}>
            Your eye muscles and attention are refreshed for the next study block.
          </p>
          <button
            type="button"
            onClick={() => {
              setDone(false);
              setStepIdx(0);
              setSecInStep(20);
              setRunning(true);
            }}
            className="px-5 py-2 rounded-xl text-xs font-black text-white"
            style={{ backgroundColor: colors.teal }}
          >
            Restart 60s Reset
          </button>
        </>
      ) : (
        <>
          <div className="text-xs font-black uppercase" style={{ color: colors.teal }}>
            Step {stepIdx + 1} / {SOFT_RESET_STEPS.length} • {secInStep}s
          </div>
          <div className="text-base font-black" style={{ color: colors.inkBold }}>
            {currentStep.title}
          </div>
          <p className="text-xs leading-relaxed" style={{ color: colors.ink }}>
            {currentStep.prompt}
          </p>
          {!running ? (
            <button
              type="button"
              onClick={() => setRunning(true)}
              className="px-5 py-2.5 rounded-xl text-xs font-black text-white inline-flex items-center gap-1.5"
              style={{ backgroundColor: colors.teal }}
            >
              <Play className="w-4 h-4" />
              Begin 60s Soft Reset
            </button>
          ) : (
            <button
              type="button"
              onClick={() => setRunning(false)}
              className="px-4 py-2 rounded-xl text-xs font-bold border"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            >
              Pause
            </button>
          )}
        </>
      )}
    </div>
  );
};

// =========================================================================
// 12. Untimed Zen Logic Grid (4x4 & 6x6)
// =========================================================================

export const ZenLogicGridDrill: React.FC<DrillCommonProps> = ({ stats, settings, onUpdateStats }) => {
  const { colors } = useTheme();
  const [gridSize, setGridSize] = useState<4 | 6>(4);
  const [puzzle, setPuzzle] = useState<ZenLogicPuzzle>(() => generateZenLogicPuzzle(4));
  const [board, setBoard] = useState<number[]>(() => [...puzzle.givens]);
  const [selectedCell, setSelectedCell] = useState<number | null>(null);
  const [solved, setSolved] = useState<boolean>(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const startNewPuzzle = (size: 4 | 6 = gridSize) => {
    const next = generateZenLogicPuzzle(size);
    setGridSize(size);
    setPuzzle(next);
    setBoard([...next.givens]);
    setSelectedCell(null);
    setSolved(false);
    setStatusMessage(null);
  };

  const setCellValue = (val: number) => {
    if (selectedCell === null || solved || puzzle.givens[selectedCell] !== 0) return;
    const next = [...board];
    next[selectedCell] = val;
    setBoard(next);
    setStatusMessage(null);

    if (isZenGridSolvedCorrectly(next, puzzle.size, puzzle.boxRows, puzzle.boxCols)) {
      setSolved(true);
      if (settings.audioEnabled) playSubtleChime(780, 200);
      onUpdateStats(
        (prev) => ({ ...prev, zenGridsCompleted: prev.zenGridsCompleted + 1 }),
        settings.countCalmInDailyStudyTime ? 90 : 0
      );
    }
  };

  const revealHint = () => {
    const blanks = board
      .map((v, i) => (v === 0 ? i : -1))
      .filter((i) => i >= 0);
    if (blanks.length === 0) return;
    const target = selectedCell !== null && board[selectedCell] === 0 ? selectedCell : blanks[0];
    const next = [...board];
    next[target] = puzzle.solution[target];
    setBoard(next);
    if (isZenGridSolvedCorrectly(next, puzzle.size, puzzle.boxRows, puzzle.boxCols)) {
      setSolved(true);
    }
  };

  return (
    <div className="space-y-4 max-w-md mx-auto">
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
        <div className="flex gap-1.5">
          {([4, 6] as (4 | 6)[]).map((sz) => (
            <button
              key={sz}
              type="button"
              onClick={() => startNewPuzzle(sz)}
              className="px-3 py-1.5 rounded-lg border font-bold"
              style={
                gridSize === sz
                  ? { backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.tealDark }
                  : { backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.ink }
              }
            >
              {sz}×{sz} Zen Grid
            </button>
          ))}
        </div>
        <span className="font-bold" style={{ color: colors.muted }}>
          Completed: {stats.zenGridsCompleted}
        </span>
      </div>

      {/* Grid */}
      <div
        className="grid gap-1 p-2 rounded-2xl border"
        style={{
          backgroundColor: colors.surfaceVariant,
          borderColor: colors.border,
          gridTemplateColumns: `repeat(${puzzle.size}, minmax(0, 1fr))`,
        }}
      >
        {board.map((val, idx) => {
          const isGiven = puzzle.givens[idx] !== 0;
          const isSelected = selectedCell === idx;
          const r = Math.floor(idx / puzzle.size);
          const c = idx % puzzle.size;
          const altBox =
            (Math.floor(r / puzzle.boxRows) + Math.floor(c / puzzle.boxCols)) % 2 === 0;
          return (
            <button
              key={idx}
              type="button"
              onClick={() => setSelectedCell(idx)}
              className="aspect-square rounded-lg border text-sm font-black transition"
              style={{
                backgroundColor: isSelected
                  ? colors.tealLight
                  : altBox
                  ? colors.surface
                  : colors.surfaceVariant,
                borderColor: isSelected ? colors.teal : colors.border,
                color: isGiven ? colors.inkBold : colors.teal,
              }}
            >
              {val > 0 ? val : ''}
            </button>
          );
        })}
      </div>

      {solved ? (
        <div
          className="p-4 rounded-xl border text-center space-y-2"
          style={{ backgroundColor: colors.tealLight, borderColor: colors.teal }}
        >
          <div className="flex items-center justify-center gap-1.5 text-sm font-black" style={{ color: colors.tealDark }}>
            <CheckCircle2 className="w-5 h-5" />
            Zen Grid Solved Cleanly!
          </div>
          <button
            type="button"
            onClick={() => startNewPuzzle(gridSize)}
            className="px-4 py-2 rounded-lg text-xs font-black text-white"
            style={{ backgroundColor: colors.teal }}
          >
            New Untimed Grid
          </button>
        </div>
      ) : (
        <div className="space-y-2">
          <div className="flex gap-1.5 justify-center">
            {Array.from({ length: puzzle.size }, (_, i) => i + 1).map((num) => (
              <button
                key={num}
                type="button"
                onClick={() => setCellValue(num)}
                className="flex-1 py-2.5 rounded-xl border text-xs font-black"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
              >
                {num}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setCellValue(0)}
              className="px-3 py-2.5 rounded-xl border text-xs font-bold"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.muted }}
            >
              Erase
            </button>
          </div>
          <div className="flex justify-between text-xs">
            <button
              type="button"
              onClick={revealHint}
              className="inline-flex items-center gap-1 font-bold underline"
              style={{ color: colors.teal }}
            >
              <Sparkles className="w-3.5 h-3.5" />
              Gentle Hint
            </button>
            <button
              type="button"
              onClick={() => startNewPuzzle(gridSize)}
              className="inline-flex items-center gap-1 font-bold"
              style={{ color: colors.muted }}
            >
              <RotateCcw className="w-3.5 h-3.5" />
              New Puzzle
            </button>
          </div>
          {statusMessage && (
            <p className="text-xs text-center" style={{ color: colors.muted }}>
              {statusMessage}
            </p>
          )}
        </div>
      )}
    </div>
  );
};
