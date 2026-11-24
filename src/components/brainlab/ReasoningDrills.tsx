import React, { useMemo, useRef, useState } from 'react';
import { ArrowRight, CheckCircle2, Flame, RotateCcw, XCircle } from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import type { BrainLabSettings, BrainLabStats } from '../../types/phase5';
import {
  DATA_SUFFICIENCY_LABELS,
  formatSyllogismStatement,
  generateSeatingPuzzle,
  generateSyllogismPuzzle,
  getShuffledArgumentBank,
  getShuffledDataSufficiencyBank,
  playSubtleChime,
  SYLLOGISM_ANSWER_LABELS,
  type ArgumentQuestion,
  type DataSufficiencyAnswer,
  type DataSufficiencyQuestion,
  type SeatingPuzzle,
  type SyllogismAnswer,
  type SyllogismPuzzle,
} from '../../utils/brainLabEngines';

interface DrillCommonProps {
  stats: BrainLabStats;
  settings: BrainLabSettings;
  onUpdateStats: (updater: (prev: BrainLabStats) => BrainLabStats, studySeconds?: number) => void;
}

// =========================================================================
// 4. Venn Syllogism Arena
// =========================================================================

export const SyllogismDrill: React.FC<DrillCommonProps> = ({ stats, settings, onUpdateStats }) => {
  const { colors } = useTheme();
  const [puzzle, setPuzzle] = useState<SyllogismPuzzle>(() => generateSyllogismPuzzle());
  const [selectedAnswer, setSelectedAnswer] = useState<SyllogismAnswer | null>(null);
  const startMsRef = useRef<number>(Date.now());

  const handleChoose = (ans: SyllogismAnswer) => {
    if (selectedAnswer !== null) return;
    setSelectedAnswer(ans);
    const isCorrect = ans === puzzle.verification.answer;
    const secs = Math.max(1, Math.round((Date.now() - startMsRef.current) / 1000));
    if (settings.audioEnabled) playSubtleChime(isCorrect ? 780 : 320, 140);

    onUpdateStats((prev) => {
      const nextStreak = isCorrect ? prev.reasoningStreak + 1 : 0;
      return {
        ...prev,
        syllogismSolved: prev.syllogismSolved + 1,
        syllogismCorrect: prev.syllogismCorrect + (isCorrect ? 1 : 0),
        reasoningStreak: nextStreak,
        bestReasoningStreak: Math.max(prev.bestReasoningStreak, nextStreak),
      };
    }, secs);
  };

  const nextPuzzle = () => {
    setPuzzle(generateSyllogismPuzzle());
    setSelectedAnswer(null);
    startMsRef.current = Date.now();
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between text-xs font-bold">
        <span style={{ color: colors.muted }}>
          Solved: {stats.syllogismSolved} ({stats.syllogismCorrect} right)
        </span>
        <span className="flex items-center gap-1" style={{ color: colors.amber }}>
          <Flame className="w-3.5 h-3.5" />
          Streak: {stats.reasoningStreak} (Best: {stats.bestReasoningStreak})
        </span>
      </div>

      <div
        className="p-4 rounded-xl border space-y-3"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div>
          <div className="text-[0.6875rem] font-black uppercase mb-1.5" style={{ color: colors.teal }}>
            Statements (Assume strictly true)
          </div>
          <ul className="space-y-1 text-xs font-semibold" style={{ color: colors.inkBold }}>
            {puzzle.premises.map((p, idx) => (
              <li key={idx}>
                {idx + 1}. {formatSyllogismStatement(p, puzzle.terms)}
              </li>
            ))}
          </ul>
        </div>

        <div className="pt-2 border-t" style={{ borderColor: colors.borderLight }}>
          <div className="text-[0.6875rem] font-black uppercase mb-1.5" style={{ color: colors.inkBold }}>
            Conclusions
          </div>
          <div className="space-y-1 text-xs font-bold" style={{ color: colors.ink }}>
            <div>I. {formatSyllogismStatement(puzzle.conclusionI, puzzle.terms)}</div>
            <div>II. {formatSyllogismStatement(puzzle.conclusionII, puzzle.terms)}</div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {(Object.keys(SYLLOGISM_ANSWER_LABELS) as SyllogismAnswer[]).map((ansKey) => {
          const isChosen = selectedAnswer === ansKey;
          const isTruth = puzzle.verification.answer === ansKey;
          let bg = colors.surfaceVariant;
          let border = colors.border;
          if (selectedAnswer !== null) {
            if (isTruth) {
              bg = colors.tealLight;
              border = colors.teal;
            } else if (isChosen) {
              bg = colors.redLight;
              border = colors.red;
            }
          }
          return (
            <button
              key={ansKey}
              type="button"
              disabled={selectedAnswer !== null}
              onClick={() => handleChoose(ansKey)}
              className="p-3 rounded-xl border text-left text-xs font-bold transition"
              style={{ backgroundColor: bg, borderColor: border, color: colors.inkBold }}
            >
              {SYLLOGISM_ANSWER_LABELS[ansKey]}
            </button>
          );
        })}
      </div>

      {selectedAnswer !== null && (
        <div
          className="p-4 rounded-xl border space-y-2 text-xs"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.teal }}
        >
          <div className="flex items-center gap-1.5 font-black" style={{ color: colors.inkBold }}>
            {selectedAnswer === puzzle.verification.answer ? (
              <CheckCircle2 className="w-4 h-4" style={{ color: colors.teal }} />
            ) : (
              <XCircle className="w-4 h-4" style={{ color: colors.red }} />
            )}
            <span>
              {selectedAnswer === puzzle.verification.answer
                ? 'Logically Verified!'
                : `Correct Answer: ${SYLLOGISM_ANSWER_LABELS[puzzle.verification.answer]}`}
            </span>
          </div>
          <pre className="whitespace-pre-wrap font-sans leading-relaxed" style={{ color: colors.ink }}>
            {puzzle.verification.explanation}
          </pre>
          <button
            type="button"
            onClick={nextPuzzle}
            className="mt-1 px-4 py-2 rounded-lg text-xs font-black inline-flex items-center gap-1.5"
            style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
          >
            Next Venn Puzzle
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
      )}
    </div>
  );
};

// =========================================================================
// 5. Constraint Seating & Pairing Grid
// =========================================================================

export const SeatingGridDrill: React.FC<DrillCommonProps> = ({ stats, settings, onUpdateStats }) => {
  const { colors } = useTheme();
  const [seatCount, setSeatCount] = useState<4 | 5>(4);
  const [isCircular, setIsCircular] = useState<boolean>(false);
  const [puzzle, setPuzzle] = useState<SeatingPuzzle>(() => generateSeatingPuzzle(4, false));
  const [scratchpad, setScratchpad] = useState<(string | null)[]>([null, null, null, null]);
  const [activePerson, setActivePerson] = useState<string | null>(null);
  const [selectedIdx, setSelectedIdx] = useState<number | null>(null);
  const startMsRef = useRef<number>(Date.now());

  const loadNewPuzzle = (cnt: 4 | 5 = seatCount, circ: boolean = isCircular) => {
    const next = generateSeatingPuzzle(cnt, circ);
    setPuzzle(next);
    setScratchpad(new Array(cnt).fill(null));
    setActivePerson(null);
    setSelectedIdx(null);
    startMsRef.current = Date.now();
  };

  const handleSeatAssign = (seatIdx: number) => {
    if (activePerson) {
      setScratchpad((prev) =>
        prev.map((val, idx) => (idx === seatIdx ? activePerson : val === activePerson ? null : val))
      );
      setActivePerson(null);
    } else if (scratchpad[seatIdx]) {
      setScratchpad((prev) => prev.map((v, i) => (i === seatIdx ? null : v)));
    }
  };

  const handleSubmitOption = (idx: number) => {
    if (selectedIdx !== null) return;
    setSelectedIdx(idx);
    const isCorrect = idx === puzzle.correctIndex;
    const secs = Math.max(1, Math.round((Date.now() - startMsRef.current) / 1000));
    if (settings.audioEnabled) playSubtleChime(isCorrect ? 780 : 320, 140);

    onUpdateStats((prev) => {
      const nextStreak = isCorrect ? prev.reasoningStreak + 1 : 0;
      return {
        ...prev,
        constraintSolved: prev.constraintSolved + 1,
        reasoningStreak: nextStreak,
        bestReasoningStreak: Math.max(prev.bestReasoningStreak, nextStreak),
      };
    }, secs);
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => {
              const next: 4 | 5 = seatCount === 4 ? 5 : 4;
              setSeatCount(next);
              loadNewPuzzle(next, isCircular);
            }}
            className="px-3 py-1.5 rounded-lg border font-bold"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
          >
            Seats: {seatCount}
          </button>
          <button
            type="button"
            onClick={() => {
              const next = !isCircular;
              setIsCircular(next);
              loadNewPuzzle(seatCount, next);
            }}
            className="px-3 py-1.5 rounded-lg border font-bold"
            style={{
              backgroundColor: isCircular ? colors.tealLight : colors.surfaceVariant,
              borderColor: isCircular ? colors.teal : colors.border,
              color: colors.inkBold,
            }}
          >
            {isCircular ? 'Circular Table' : 'Linear Row'}
          </button>
        </div>
        <span className="font-bold" style={{ color: colors.muted }}>
          Puzzles Solved: {stats.constraintSolved}
        </span>
      </div>

      {/* Clues Box */}
      <div
        className="p-4 rounded-xl border space-y-2"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div className="text-[0.6875rem] font-black uppercase" style={{ color: colors.teal }}>
          Constraints
        </div>
        <ul className="space-y-1 text-xs font-semibold" style={{ color: colors.inkBold }}>
          {puzzle.clues.map((c, idx) => (
            <li key={idx}>• {c}</li>
          ))}
        </ul>
      </div>

      {/* Interactive Scratchpad */}
      <div
        className="p-3.5 rounded-xl border space-y-2.5"
        style={{ backgroundColor: colors.surface, borderColor: colors.border }}
      >
        <div className="flex items-center justify-between text-[0.6875rem]">
          <span className="font-bold" style={{ color: colors.inkBold }}>
            1-Tap Elimination Scratchpad (tap a person, then tap a seat)
          </span>
          <button
            type="button"
            onClick={() => setScratchpad(new Array(seatCount).fill(null))}
            className="underline"
            style={{ color: colors.muted }}
          >
            clear
          </button>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {puzzle.people.map((person) => {
            const placed = scratchpad.includes(person);
            const active = activePerson === person;
            return (
              <button
                key={person}
                type="button"
                onClick={() => setActivePerson(active ? null : person)}
                className="px-2.5 py-1 rounded-full text-xs font-bold border"
                style={
                  active
                    ? { backgroundColor: colors.teal, color: colors.onAccentText, borderColor: colors.teal }
                    : placed
                    ? { backgroundColor: colors.surfaceVariant, color: colors.muted, borderColor: colors.border }
                    : { backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.border }
                }
              >
                {person}
              </button>
            );
          })}
        </div>
        <div className="grid grid-cols-4 sm:grid-cols-5 gap-2">
          {scratchpad.map((occupant, sIdx) => (
            <button
              key={sIdx}
              type="button"
              onClick={() => handleSeatAssign(sIdx)}
              className="p-2 rounded-xl border text-center"
              style={{
                backgroundColor: occupant ? colors.tealLight : colors.surfaceVariant,
                borderColor: activePerson ? colors.teal : colors.border,
              }}
            >
              <div className="text-[0.625rem] font-bold" style={{ color: colors.muted }}>
                Seat #{sIdx + 1}
              </div>
              <div className="text-xs font-black truncate mt-0.5" style={{ color: colors.inkBold }}>
                {occupant || '—'}
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Question & Options */}
      <div className="space-y-2">
        <div className="text-xs font-black" style={{ color: colors.inkBold }}>
          {puzzle.promptQuestion}
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
          {puzzle.options.map((opt, idx) => {
            const isChosen = selectedIdx === idx;
            const isTruth = puzzle.correctIndex === idx;
            let bg = colors.surfaceVariant;
            let border = colors.border;
            if (selectedIdx !== null) {
              if (isTruth) {
                bg = colors.tealLight;
                border = colors.teal;
              } else if (isChosen) {
                bg = colors.redLight;
                border = colors.red;
              }
            }
            return (
              <button
                key={opt}
                type="button"
                disabled={selectedIdx !== null}
                onClick={() => handleSubmitOption(idx)}
                className="py-2.5 px-3 rounded-xl border text-xs font-bold"
                style={{ backgroundColor: bg, borderColor: border, color: colors.inkBold }}
              >
                {opt}
              </button>
            );
          })}
        </div>
      </div>

      {selectedIdx !== null && (
        <div
          className="p-3.5 rounded-xl border text-xs space-y-2"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.teal }}
        >
          <div className="font-bold" style={{ color: colors.inkBold }}>
            {puzzle.explanation}
          </div>
          <button
            type="button"
            onClick={() => loadNewPuzzle(seatCount, isCircular)}
            className="px-4 py-2 rounded-lg text-xs font-black inline-flex items-center gap-1.5"
            style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
          >
            <RotateCcw className="w-3.5 h-3.5" />
            New Seating Puzzle
          </button>
        </div>
      )}
    </div>
  );
};

// =========================================================================
// 6. Argument & Logical Fallacy Critique
// =========================================================================

export const ArgumentCritiqueDrill: React.FC<DrillCommonProps> = ({ stats, settings, onUpdateStats }) => {
  const { colors } = useTheme();
  const [bank, setBank] = useState<ArgumentQuestion[]>(() => getShuffledArgumentBank());
  const [idx, setIdx] = useState<number>(0);
  const [chosenIdx, setChosenIdx] = useState<number | null>(null);
  const startMsRef = useRef<number>(Date.now());

  const current = useMemo(() => bank[idx % bank.length], [bank, idx]);

  const handleSelect = (optIdx: number) => {
    if (chosenIdx !== null) return;
    setChosenIdx(optIdx);
    const isCorrect = optIdx === current.correctIndex;
    const secs = Math.max(1, Math.round((Date.now() - startMsRef.current) / 1000));
    if (settings.audioEnabled) playSubtleChime(isCorrect ? 780 : 320, 140);

    onUpdateStats((prev) => {
      const nextStreak = isCorrect ? prev.reasoningStreak + 1 : 0;
      return {
        ...prev,
        argumentSolved: prev.argumentSolved + 1,
        reasoningStreak: nextStreak,
        bestReasoningStreak: Math.max(prev.bestReasoningStreak, nextStreak),
      };
    }, secs);
  };

  const handleNext = () => {
    setChosenIdx(null);
    startMsRef.current = Date.now();
    if (idx + 1 >= bank.length) {
      setBank(getShuffledArgumentBank());
      setIdx(0);
    } else {
      setIdx(idx + 1);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between text-xs font-bold">
        <span
          className="px-2.5 py-0.5 rounded-full"
          style={{ backgroundColor: colors.tealLight, color: colors.tealDark }}
        >
          {current.categoryTag}
        </span>
        <span style={{ color: colors.muted }}>
          Solved: {stats.argumentSolved} • Item {(idx % bank.length) + 1}/{bank.length}
        </span>
      </div>

      <div
        className="p-4 rounded-xl border space-y-2"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <p className="text-xs sm:text-sm leading-relaxed whitespace-pre-line font-medium" style={{ color: colors.inkBold }}>
          {current.passage}
        </p>
        <p className="text-xs font-black pt-1" style={{ color: colors.teal }}>
          {current.prompt}
        </p>
      </div>

      <div className="space-y-2">
        {current.options.map((opt, optIdx) => {
          const isChosen = chosenIdx === optIdx;
          const isTruth = current.correctIndex === optIdx;
          let bg = colors.surfaceVariant;
          let border = colors.border;
          if (chosenIdx !== null) {
            if (isTruth) {
              bg = colors.tealLight;
              border = colors.teal;
            } else if (isChosen) {
              bg = colors.redLight;
              border = colors.red;
            }
          }
          return (
            <button
              key={opt}
              type="button"
              disabled={chosenIdx !== null}
              onClick={() => handleSelect(optIdx)}
              className="w-full p-3 rounded-xl border text-left text-xs font-semibold transition"
              style={{ backgroundColor: bg, borderColor: border, color: colors.inkBold }}
            >
              {opt}
            </button>
          );
        })}
      </div>

      {chosenIdx !== null && (
        <div
          className="p-4 rounded-xl border text-xs space-y-2"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.teal }}
        >
          <div className="font-bold" style={{ color: colors.inkBold }}>
            {current.explanation}
          </div>
          <button
            type="button"
            onClick={handleNext}
            className="px-4 py-2 rounded-lg text-xs font-black inline-flex items-center gap-1.5"
            style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
          >
            Next Argument
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
      )}
    </div>
  );
};

// =========================================================================
// 7. Data Sufficiency Discipline Check
// =========================================================================

export const DataSufficiencyDrill: React.FC<DrillCommonProps> = ({ stats, settings, onUpdateStats }) => {
  const { colors } = useTheme();
  const [bank, setBank] = useState<DataSufficiencyQuestion[]>(() => getShuffledDataSufficiencyBank());
  const [idx, setIdx] = useState<number>(0);
  const [chosen, setChosen] = useState<DataSufficiencyAnswer | null>(null);
  const startMsRef = useRef<number>(Date.now());

  const current = useMemo(() => bank[idx % bank.length], [bank, idx]);

  const handleSelect = (ans: DataSufficiencyAnswer) => {
    if (chosen !== null) return;
    setChosen(ans);
    const isCorrect = ans === current.answer;
    const secs = Math.max(1, Math.round((Date.now() - startMsRef.current) / 1000));
    if (settings.audioEnabled) playSubtleChime(isCorrect ? 780 : 320, 140);

    onUpdateStats((prev) => {
      const nextStreak = isCorrect ? prev.reasoningStreak + 1 : 0;
      return {
        ...prev,
        dataSufficiencySolved: prev.dataSufficiencySolved + 1,
        reasoningStreak: nextStreak,
        bestReasoningStreak: Math.max(prev.bestReasoningStreak, nextStreak),
      };
    }, secs);
  };

  const handleNext = () => {
    setChosen(null);
    startMsRef.current = Date.now();
    if (idx + 1 >= bank.length) {
      setBank(getShuffledDataSufficiencyBank());
      setIdx(0);
    } else {
      setIdx(idx + 1);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between text-xs font-bold">
        <span
          className="px-2.5 py-0.5 rounded-full"
          style={{ backgroundColor: colors.amberLight, color: colors.amber }}
        >
          {current.trapTag}
        </span>
        <span style={{ color: colors.muted }}>
          Solved: {stats.dataSufficiencySolved} • {(idx % bank.length) + 1}/{bank.length}
        </span>
      </div>

      <div
        className="p-4 rounded-xl border space-y-2.5"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div className="text-sm font-black" style={{ color: colors.inkBold }}>
          {current.question}
        </div>
        <div className="p-2.5 rounded-lg border text-xs" style={{ backgroundColor: colors.surface, borderColor: colors.border }}>
          <strong>Statement I:</strong> {current.statementI}
        </div>
        <div className="p-2.5 rounded-lg border text-xs" style={{ backgroundColor: colors.surface, borderColor: colors.border }}>
          <strong>Statement II:</strong> {current.statementII}
        </div>
      </div>

      <div className="space-y-2">
        {(Object.keys(DATA_SUFFICIENCY_LABELS) as DataSufficiencyAnswer[]).map((ansKey) => {
          const isChosen = chosen === ansKey;
          const isTruth = current.answer === ansKey;
          let bg = colors.surfaceVariant;
          let border = colors.border;
          if (chosen !== null) {
            if (isTruth) {
              bg = colors.tealLight;
              border = colors.teal;
            } else if (isChosen) {
              bg = colors.redLight;
              border = colors.red;
            }
          }
          return (
            <button
              key={ansKey}
              type="button"
              disabled={chosen !== null}
              onClick={() => handleSelect(ansKey)}
              className="w-full p-2.5 rounded-xl border text-left text-xs font-bold transition"
              style={{ backgroundColor: bg, borderColor: border, color: colors.inkBold }}
            >
              {DATA_SUFFICIENCY_LABELS[ansKey]}
            </button>
          );
        })}
      </div>

      {chosen !== null && (
        <div
          className="p-4 rounded-xl border text-xs space-y-2"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.teal }}
        >
          <pre className="whitespace-pre-wrap font-sans font-medium" style={{ color: colors.inkBold }}>
            {current.explanation}
          </pre>
          <button
            type="button"
            onClick={handleNext}
            className="px-4 py-2 rounded-lg text-xs font-black inline-flex items-center gap-1.5"
            style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
          >
            Next Trap Check
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
      )}
    </div>
  );
};
