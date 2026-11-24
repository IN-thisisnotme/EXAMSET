import React, { useEffect, useRef, useState } from 'react';
import { Award, CheckCircle2, Play, RotateCcw } from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import type { BrainLabSettings, BrainLabStats, NBackMode } from '../../types/phase5';
import {
  evaluateNBackSession,
  generateCorsiPuzzle,
  generateNBackSequence,
  generateStroopTrial,
  playSubtleChime,
  STROOP_RULE_META,
  type CorsiPuzzle,
  type NBackEvaluation,
  type NBackTrial,
  type StroopRuleId,
  type StroopTrial,
} from '../../utils/brainLabEngines';

interface DrillCommonProps {
  stats: BrainLabStats;
  settings: BrainLabSettings;
  onUpdateStats: (updater: (prev: BrainLabStats) => BrainLabStats, studySeconds?: number) => void;
}

// =========================================================================
// 1. Adaptive N-Back (1–4)
// =========================================================================

export const NBackDrill: React.FC<DrillCommonProps> = ({ stats, settings, onUpdateStats }) => {
  const { colors, scalePx } = useTheme();
  const [nTier, setNTier] = useState<number>(() => Math.max(1, Math.min(4, stats.bestNBackTier || 2)));
  const [mode, setMode] = useState<NBackMode>('DUAL');
  const [phase, setPhase] = useState<'SETUP' | 'RUNNING' | 'SUMMARY'>('SETUP');
  const [trials, setTrials] = useState<NBackTrial[]>([]);
  const [stepIdx, setStepIdx] = useState<number>(0);
  const [userPosTaps, setUserPosTaps] = useState<Set<number>>(() => new Set());
  const [userLetterTaps, setUserLetterTaps] = useState<Set<number>>(() => new Set());
  const [evaluation, setEvaluation] = useState<NBackEvaluation | null>(null);
  const runStartMsRef = useRef<number>(0);

  const posTapsRef = useRef(userPosTaps);
  posTapsRef.current = userPosTaps;
  const letterTapsRef = useRef(userLetterTaps);
  letterTapsRef.current = userLetterTaps;

  useEffect(() => {
    if (phase !== 'RUNNING' || trials.length === 0) return;

    const timer = setInterval(() => {
      setStepIdx((cur) => {
        const next = cur + 1;
        if (next >= trials.length) {
          clearInterval(timer);
          const ev = evaluateNBackSession(nTier, mode, trials, posTapsRef.current, letterTapsRef.current);
          setEvaluation(ev);
          setPhase('SUMMARY');
          const elapsedSecs = Math.max(1, Math.round((Date.now() - runStartMsRef.current) / 1000));
          onUpdateStats(
            (prev) => ({
              ...prev,
              bestNBackTier: Math.max(prev.bestNBackTier, ev.accuracyPercent >= 75 ? nTier : prev.bestNBackTier),
              bestNBackAccuracy: Math.max(prev.bestNBackAccuracy, ev.accuracyPercent),
            }),
            elapsedSecs
          );
          if (settings.audioEnabled) playSubtleChime(740, 180);
          return cur;
        }
        if (settings.audioEnabled) playSubtleChime(520, 60);
        return next;
      });
    }, 2200);

    return () => clearInterval(timer);
  }, [phase, trials, nTier, mode, settings.audioEnabled, onUpdateStats]);

  const startRun = (customN: number = nTier) => {
    const seq = generateNBackSequence(customN, 16 + customN, mode);
    setTrials(seq);
    setStepIdx(0);
    setUserPosTaps(new Set());
    setUserLetterTaps(new Set());
    setEvaluation(null);
    runStartMsRef.current = Date.now();
    setPhase('RUNNING');
  };

  const currentTrial = trials[stepIdx];

  return (
    <div className="space-y-4">
      {phase === 'SETUP' && (
        <div
          className="p-5 rounded-2xl border space-y-4"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <div className="text-xs font-bold mb-2" style={{ color: colors.inkBold }}>
                N-Back Tier (Lookback Steps)
              </div>
              <div className="flex gap-2">
                {[1, 2, 3, 4].map((tier) => (
                  <button
                    key={tier}
                    type="button"
                    onClick={() => setNTier(tier)}
                    className="flex-1 py-2 rounded-xl border text-xs font-black"
                    style={
                      nTier === tier
                        ? { backgroundColor: colors.teal, color: colors.onAccentText, borderColor: colors.teal }
                        : { backgroundColor: colors.surface, color: colors.inkBold, borderColor: colors.border }
                    }
                  >
                    {tier}-Back
                  </button>
                ))}
              </div>
            </div>

            <div>
              <div className="text-xs font-bold mb-2" style={{ color: colors.inkBold }}>
                Channel Mode
              </div>
              <div className="grid grid-cols-3 gap-1.5">
                {(
                  [
                    { id: 'DUAL', label: 'Dual' },
                    { id: 'POSITION_ONLY', label: 'Position' },
                    { id: 'LETTER_ONLY', label: 'Letter' },
                  ] as { id: NBackMode; label: string }[]
                ).map((m) => (
                  <button
                    key={m.id}
                    type="button"
                    onClick={() => setMode(m.id)}
                    className="py-2 rounded-xl border text-xs font-bold"
                    style={
                      mode === m.id
                        ? { backgroundColor: colors.tealLight, borderColor: colors.teal, color: colors.tealDark }
                        : { backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }
                    }
                  >
                    {m.label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <p className="text-xs" style={{ color: colors.muted }}>
            Press <strong>Position Match</strong> if the lit cell matches {nTier} step(s) earlier; press{' '}
            <strong>Letter Match</strong> if the letter matches {nTier} step(s) earlier. Personal best:{' '}
            {stats.bestNBackTier}-Back ({stats.bestNBackAccuracy}%).
          </p>

          <button
            type="button"
            onClick={() => startRun(nTier)}
            className="w-full sm:w-auto flex items-center justify-center gap-2 px-6 py-2.5 rounded-xl text-sm font-black"
            style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
          >
            <Play className="w-4 h-4" />
            Begin {nTier}-Back Session
          </button>
        </div>
      )}

      {phase === 'RUNNING' && currentTrial && (
        <div className="space-y-4 max-w-md mx-auto">
          <div className="flex items-center justify-between text-xs font-bold">
            <span style={{ color: colors.teal }}>
              {nTier}-Back • Trial {stepIdx + 1} / {trials.length}
            </span>
            <button
              type="button"
              onClick={() => setPhase('SETUP')}
              className="px-2.5 py-1 rounded border text-xs"
              style={{ borderColor: colors.border, color: colors.muted }}
            >
              Abort
            </button>
          </div>

          {mode !== 'POSITION_ONLY' && (
            <div
              className="py-3 rounded-xl border text-center font-black"
              style={{
                backgroundColor: colors.surfaceVariant,
                borderColor: colors.border,
                color: colors.inkBold,
                fontSize: scalePx(26),
              }}
            >
              {currentTrial.letter}
            </div>
          )}

          {mode !== 'LETTER_ONLY' && (
            <div className="grid grid-cols-3 gap-2.5">
              {Array.from({ length: 9 }, (_, idx) => {
                const active = currentTrial.cellIndex === idx;
                return (
                  <div
                    key={idx}
                    className="aspect-square rounded-xl border transition-all duration-150 flex items-center justify-center font-black"
                    style={{
                      backgroundColor: active ? colors.teal : colors.surfaceVariant,
                      borderColor: active ? colors.teal : colors.border,
                      color: active ? colors.onAccentText : colors.muted,
                    }}
                  >
                    {active ? '●' : ''}
                  </div>
                );
              })}
            </div>
          )}

          <div className="flex gap-3">
            {mode !== 'LETTER_ONLY' && (
              <button
                type="button"
                disabled={stepIdx < nTier}
                onClick={() =>
                  setUserPosTaps((prev) => new Set(prev).add(currentTrial.stepIndex))
                }
                className="flex-1 py-3 rounded-xl text-xs font-black border disabled:opacity-40"
                style={
                  userPosTaps.has(currentTrial.stepIndex)
                    ? { backgroundColor: colors.teal, color: colors.onAccentText, borderColor: colors.teal }
                    : { backgroundColor: colors.surfaceVariant, color: colors.inkBold, borderColor: colors.border }
                }
              >
                {userPosTaps.has(currentTrial.stepIndex) ? '✓ Position Marked' : 'Position Match'}
              </button>
            )}
            {mode !== 'POSITION_ONLY' && (
              <button
                type="button"
                disabled={stepIdx < nTier}
                onClick={() =>
                  setUserLetterTaps((prev) => new Set(prev).add(currentTrial.stepIndex))
                }
                className="flex-1 py-3 rounded-xl text-xs font-black border disabled:opacity-40"
                style={
                  userLetterTaps.has(currentTrial.stepIndex)
                    ? { backgroundColor: colors.teal, color: colors.onAccentText, borderColor: colors.teal }
                    : { backgroundColor: colors.surfaceVariant, color: colors.inkBold, borderColor: colors.border }
                }
              >
                {userLetterTaps.has(currentTrial.stepIndex) ? '✓ Letter Marked' : 'Letter Match'}
              </button>
            )}
          </div>
          {stepIdx < nTier && (
            <p className="text-[0.6875rem] text-center" style={{ color: colors.muted }}>
              Observe initial {nTier} lead-in trial(s) — matching unlocks at Trial {nTier + 1}.
            </p>
          )}
        </div>
      )}

      {phase === 'SUMMARY' && evaluation && (
        <div
          className="p-5 rounded-2xl border space-y-4 max-w-lg mx-auto text-center"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.teal }}
        >
          <Award className="w-8 h-8 mx-auto" style={{ color: colors.teal }} />
          <div>
            <div className="text-2xl font-black" style={{ color: colors.inkBold }}>
              {evaluation.accuracyPercent}% Accuracy
            </div>
            <div className="text-xs font-bold mt-1" style={{ color: colors.teal }}>
              {evaluation.tierDeltaLabel}
            </div>
          </div>
          <div className="grid grid-cols-3 gap-2 text-xs">
            <div className="p-2 rounded-lg border" style={{ backgroundColor: colors.surface, borderColor: colors.border }}>
              <div className="font-black" style={{ color: colors.teal }}>{evaluation.hits}</div>
              <div style={{ color: colors.muted }}>Hits</div>
            </div>
            <div className="p-2 rounded-lg border" style={{ backgroundColor: colors.surface, borderColor: colors.border }}>
              <div className="font-black" style={{ color: colors.amber }}>{evaluation.misses}</div>
              <div style={{ color: colors.muted }}>Misses</div>
            </div>
            <div className="p-2 rounded-lg border" style={{ backgroundColor: colors.surface, borderColor: colors.border }}>
              <div className="font-black" style={{ color: colors.red }}>{evaluation.falseAlarms}</div>
              <div style={{ color: colors.muted }}>False Alarms</div>
            </div>
          </div>
          <div className="flex flex-wrap justify-center gap-2">
            <button
              type="button"
              onClick={() => {
                setNTier(evaluation.nextRecommendedN);
                startRun(evaluation.nextRecommendedN);
              }}
              className="px-4 py-2 rounded-xl text-xs font-black"
              style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
            >
              Run {evaluation.nextRecommendedN}-Back Next
            </button>
            <button
              type="button"
              onClick={() => setPhase('SETUP')}
              className="px-4 py-2 rounded-xl text-xs font-bold border"
              style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
            >
              Change Settings
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

// =========================================================================
// 2. Corsi Spatial Matrix & Reverse Span
// =========================================================================

export const CorsiSpanDrill: React.FC<DrillCommonProps> = ({ stats, settings, onUpdateStats }) => {
  const { colors } = useTheme();
  const [gridSize, setGridSize] = useState<4 | 5>(4);
  const [span, setSpan] = useState<number>(4);
  const [reverseMode, setReverseMode] = useState<boolean>(true);
  const [withLabels, setWithLabels] = useState<boolean>(false);
  const [puzzle, setPuzzle] = useState<CorsiPuzzle>(() => generateCorsiPuzzle(4, 4, true, false));
  const [stage, setStage] = useState<'IDLE' | 'SHOWING' | 'RECALL' | 'RESULT'>('IDLE');
  const [litCell, setLitCell] = useState<number | null>(null);
  const [userSeq, setUserSeq] = useState<number[]>([]);
  const [passed, setPassed] = useState<boolean>(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startMsRef = useRef<number>(0);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  const beginRound = (nextSpan: number = span) => {
    if (timerRef.current) clearTimeout(timerRef.current);
    const nextPuzzle = generateCorsiPuzzle(gridSize, nextSpan, reverseMode, withLabels);
    setPuzzle(nextPuzzle);
    setUserSeq([]);
    setPassed(false);
    setStage('SHOWING');
    startMsRef.current = Date.now();

    let idx = 0;
    const stepHighlight = () => {
      if (idx >= nextPuzzle.sequence.length) {
        setLitCell(null);
        setStage('RECALL');
        return;
      }
      setLitCell(nextPuzzle.sequence[idx]);
      if (settings.audioEnabled) playSubtleChime(580 + idx * 45, 70);
      timerRef.current = setTimeout(() => {
        setLitCell(null);
        idx++;
        timerRef.current = setTimeout(stepHighlight, 240);
      }, 650);
    };
    timerRef.current = setTimeout(stepHighlight, 350);
  };

  const handleTapCell = (cellIdx: number) => {
    if (stage !== 'RECALL') return;
    if (userSeq.includes(cellIdx)) return;
    const next = [...userSeq, cellIdx];
    setUserSeq(next);

    const pos = next.length - 1;
    if (puzzle.expectedInput[pos] !== cellIdx) {
      setPassed(false);
      setStage('RESULT');
      if (settings.audioEnabled) playSubtleChime(320, 160);
      return;
    }

    if (next.length === puzzle.expectedInput.length) {
      setPassed(true);
      setStage('RESULT');
      const secs = Math.max(1, Math.round((Date.now() - startMsRef.current) / 1000));
      onUpdateStats(
        (prev) => ({
          ...prev,
          bestCorsiForwardSpan: !reverseMode
            ? Math.max(prev.bestCorsiForwardSpan, puzzle.span)
            : prev.bestCorsiForwardSpan,
          bestCorsiReverseSpan: reverseMode
            ? Math.max(prev.bestCorsiReverseSpan, puzzle.span)
            : prev.bestCorsiReverseSpan,
        }),
        secs
      );
      if (settings.audioEnabled) playSubtleChime(880, 180);
    }
  };

  return (
    <div className="space-y-4">
      <div
        className="p-3.5 rounded-xl border flex flex-wrap items-center justify-between gap-2 text-xs"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => setReverseMode((r) => !r)}
            className="px-3 py-1.5 rounded-lg border font-bold"
            style={{
              backgroundColor: reverseMode ? colors.tealLight : colors.surface,
              borderColor: colors.teal,
              color: colors.tealDark,
            }}
          >
            Order: {reverseMode ? 'Reverse (Hard)' : 'Forward'}
          </button>
          <button
            type="button"
            onClick={() => setGridSize((g) => (g === 4 ? 5 : 4))}
            className="px-3 py-1.5 rounded-lg border font-bold"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
          >
            Grid: {gridSize}×{gridSize}
          </button>
          <button
            type="button"
            onClick={() => setWithLabels((v) => !v)}
            className="px-3 py-1.5 rounded-lg border font-bold"
            style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
          >
            Codes: {withLabels ? 'Alphanumeric' : 'Clean'}
          </button>
        </div>
        <div className="font-bold" style={{ color: colors.muted }}>
          Best Rev: {stats.bestCorsiReverseSpan} • Fwd: {stats.bestCorsiForwardSpan}
        </div>
      </div>

      <div className="max-w-sm mx-auto space-y-3">
        <div className="text-center text-xs font-bold" style={{ color: colors.inkBold }}>
          {stage === 'IDLE' && `Span ${span} (${reverseMode ? 'Tap back-to-front' : 'Tap in sequence'})`}
          {stage === 'SHOWING' && 'Watch carefully…'}
          {stage === 'RECALL' &&
            `Tap ${puzzle.span - userSeq.length} more tile(s) in ${reverseMode ? 'REVERSE' : 'FORWARD'} order`}
          {stage === 'RESULT' &&
            (passed ? `✓ Span ${puzzle.span} Cleared!` : `✗ Sequence mismatch at step ${userSeq.length}`)}
        </div>

        <div
          className="grid gap-2"
          style={{ gridTemplateColumns: `repeat(${puzzle.gridSize}, minmax(0, 1fr))` }}
        >
          {Array.from({ length: puzzle.gridSize * puzzle.gridSize }, (_, idx) => {
            const isLit = litCell === idx;
            const userOrderIdx = userSeq.indexOf(idx);
            const expectedOrderIdx = stage === 'RESULT' ? puzzle.expectedInput.indexOf(idx) : -1;
            return (
              <button
                key={idx}
                type="button"
                disabled={stage !== 'RECALL'}
                onClick={() => handleTapCell(idx)}
                className="aspect-square rounded-xl border text-xs font-black transition-all flex items-center justify-center"
                style={{
                  backgroundColor: isLit
                    ? colors.teal
                    : userOrderIdx >= 0
                    ? colors.tealLight
                    : expectedOrderIdx >= 0
                    ? colors.amberLight
                    : colors.surfaceVariant,
                  borderColor: isLit || userOrderIdx >= 0 ? colors.teal : colors.border,
                  color: isLit ? colors.onAccentText : colors.inkBold,
                }}
              >
                {userOrderIdx >= 0
                  ? `#${userOrderIdx + 1}`
                  : expectedOrderIdx >= 0
                  ? `${expectedOrderIdx + 1}`
                  : puzzle.alphanumericLabels[idx] || ''}
              </button>
            );
          })}
        </div>

        <div className="flex justify-center gap-2 pt-1">
          {stage === 'IDLE' && (
            <button
              type="button"
              onClick={() => beginRound(span)}
              className="px-5 py-2.5 rounded-xl text-xs font-black flex items-center gap-1.5"
              style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
            >
              <Play className="w-4 h-4" />
              Watch Sequence ({span})
            </button>
          )}
          {stage === 'RESULT' && (
            <button
              type="button"
              onClick={() => {
                const next = passed ? Math.min(8, span + 1) : Math.max(4, span - 1);
                setSpan(next);
                beginRound(next);
              }}
              className="px-5 py-2.5 rounded-xl text-xs font-black flex items-center gap-1.5"
              style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
            >
              <RotateCcw className="w-4 h-4" />
              {passed ? `Advance to Span ${Math.min(8, span + 1)}` : `Retry Span ${Math.max(4, span - 1)}`}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

// =========================================================================
// 3. Stroop Impulse & Rule-Switch Arena
// =========================================================================

export const StroopSwitchDrill: React.FC<DrillCommonProps> = ({ stats, settings, onUpdateStats }) => {
  const { colors, scalePx } = useTheme();
  const TOTAL_TRIALS = 15;
  const [phase, setPhase] = useState<'SETUP' | 'RUNNING' | 'SUMMARY'>('SETUP');
  const [allowArrows, setAllowArrows] = useState<boolean>(true);
  const [trial, setTrial] = useState<StroopTrial | null>(null);
  const [trialIdx, setTrialIdx] = useState<number>(0);
  const [correctCount, setCorrectCount] = useState<number>(0);
  const [reactionSumMs, setReactionSumMs] = useState<number>(0);
  const [feedback, setFeedback] = useState<'OK' | 'ERR' | null>(null);
  const trialStartMsRef = useRef<number>(0);
  const sessionStartMsRef = useRef<number>(0);
  const prevRuleRef = useRef<StroopRuleId | null>(null);

  const startSession = () => {
    const first = generateStroopTrial(0, allowArrows, null);
    prevRuleRef.current = first.rule;
    setTrial(first);
    setTrialIdx(0);
    setCorrectCount(0);
    setReactionSumMs(0);
    setFeedback(null);
    trialStartMsRef.current = Date.now();
    sessionStartMsRef.current = Date.now();
    setPhase('RUNNING');
  };

  const handleChoice = (choiceIdx: number) => {
    if (!trial || feedback !== null) return;
    const rt = Math.max(100, Date.now() - trialStartMsRef.current);
    const isCorrect = choiceIdx === trial.expectedChoiceIndex;
    const nextCorrect = correctCount + (isCorrect ? 1 : 0);
    const nextRtSum = reactionSumMs + rt;
    setCorrectCount(nextCorrect);
    setReactionSumMs(nextRtSum);
    setFeedback(isCorrect ? 'OK' : 'ERR');
    if (settings.audioEnabled) playSubtleChime(isCorrect ? 720 : 300, 90);

    setTimeout(() => {
      setFeedback(null);
      if (trialIdx + 1 >= TOTAL_TRIALS) {
        const accuracy = Math.round((nextCorrect / TOTAL_TRIALS) * 100);
        const avgRt = Math.round(nextRtSum / TOTAL_TRIALS);
        const totalSecs = Math.max(1, Math.round((Date.now() - sessionStartMsRef.current) / 1000));
        onUpdateStats(
          (prev) => ({
            ...prev,
            bestStroopAccuracy: Math.max(prev.bestStroopAccuracy, accuracy),
            bestStroopReactionMs:
              prev.bestStroopReactionMs === 0
                ? avgRt
                : Math.min(prev.bestStroopReactionMs, avgRt),
          }),
          totalSecs
        );
        setPhase('SUMMARY');
      } else {
        const nextTrial = generateStroopTrial(trialIdx + 1, allowArrows, prevRuleRef.current);
        prevRuleRef.current = nextTrial.rule;
        setTrial(nextTrial);
        setTrialIdx((i) => i + 1);
        trialStartMsRef.current = Date.now();
      }
    }, 280);
  };

  return (
    <div className="space-y-4">
      {phase === 'SETUP' && (
        <div
          className="p-5 rounded-2xl border space-y-3"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
        >
          <p className="text-xs" style={{ color: colors.ink }}>
            15 rapid conflict trials with live rule switches (INK vs WORD meaning, and Arrow vs Opposite direction).
            Best record: <strong>{stats.bestStroopAccuracy}%</strong> accuracy •{' '}
            <strong>{stats.bestStroopReactionMs > 0 ? `${stats.bestStroopReactionMs} ms` : '—'}</strong> avg reaction.
          </p>
          <label className="flex items-center gap-2 text-xs font-semibold cursor-pointer">
            <input
              type="checkbox"
              checked={allowArrows}
              onChange={(e) => setAllowArrows(e.target.checked)}
            />
            Include Arrow / Opposite-Direction rule switches
          </label>
          <button
            type="button"
            onClick={startSession}
            className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-xs font-black"
            style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
          >
            <Play className="w-4 h-4" />
            Start 15-Trial Impulse Arena
          </button>
        </div>
      )}

      {phase === 'RUNNING' && trial && (
        <div className="max-w-md mx-auto space-y-4 text-center">
          <div className="flex items-center justify-between text-xs font-bold">
            <span style={{ color: colors.teal }}>
              Trial {trialIdx + 1} / {TOTAL_TRIALS}
            </span>
            <span style={{ color: colors.muted }}>Score: {correctCount}</span>
          </div>

          <div
            className="px-4 py-2 rounded-xl border text-xs font-black tracking-wide uppercase"
            style={{ backgroundColor: colors.amberLight, borderColor: colors.amber, color: colors.inkBold }}
          >
            {STROOP_RULE_META[trial.rule].badge}
            <div className="text-[0.6875rem] font-medium normal-case mt-0.5" style={{ color: colors.ink }}>
              {STROOP_RULE_META[trial.rule].instruction}
            </div>
          </div>

          <div
            className="py-8 rounded-2xl border flex items-center justify-center font-black"
            style={{
              backgroundColor: colors.surfaceVariant,
              borderColor:
                feedback === 'OK' ? colors.teal : feedback === 'ERR' ? colors.red : colors.border,
              color: trial.isArrowMode ? colors.inkBold : trial.inkColor.hex,
              fontSize: scalePx(38),
            }}
          >
            {trial.isArrowMode
              ? ['↑', '→', '↓', '←'][trial.arrowIndex]
              : trial.displayWord.label}
          </div>

          <div className="grid grid-cols-2 gap-2.5">
            {trial.choices.map((label, idx) => (
              <button
                key={label}
                type="button"
                onClick={() => handleChoice(idx)}
                className="py-3 rounded-xl border text-xs font-black"
                style={{
                  backgroundColor: colors.surfaceVariant,
                  borderColor: colors.border,
                  color: colors.inkBold,
                }}
              >
                {label}
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
          <CheckCircle2 className="w-8 h-8 mx-auto" style={{ color: colors.teal }} />
          <div className="text-xl font-black" style={{ color: colors.inkBold }}>
            {Math.round((correctCount / TOTAL_TRIALS) * 100)}% • {Math.round(reactionSumMs / TOTAL_TRIALS)} ms avg
          </div>
          <p className="text-xs" style={{ color: colors.muted }}>
            {correctCount} / {TOTAL_TRIALS} impulse-inhibited responses correct.
          </p>
          <button
            type="button"
            onClick={startSession}
            className="px-5 py-2 rounded-xl text-xs font-black"
            style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
          >
            Play Another 15 Trials
          </button>
        </div>
      )}
    </div>
  );
};
