import {
  type BrainLabStats,
  type BreathingProtocol,
  EMPTY_BRAIN_LAB_STATS,
  type NBackMode,
} from '../types/phase5';

// Safe RNG helper supporting deterministic seeds in tests
export type RngFn = () => number;

function randInt(rng: RngFn, min: number, maxExclusive: number): number {
  return min + Math.floor(rng() * (maxExclusive - min));
}

function shuffled<T>(items: readonly T[], rng: RngFn): T[] {
  const arr = [...items];
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    const tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
  }
  return arr;
}

// Safely play subtle Web Audio harmonic chime when user enables sound
export function playSubtleChime(freqHz: number = 660, durationMs: number = 140): void {
  try {
    if (typeof window === 'undefined') return;
    const AudioCtx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(freqHz, ctx.currentTime);
    gain.gain.setValueAtTime(0.0001, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.08, ctx.currentTime + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + durationMs / 1000);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + durationMs / 1000 + 0.02);
    setTimeout(() => {
      try {
        ctx.close();
      } catch {
        // ignore
      }
    }, durationMs + 100);
  } catch {
    // Web Audio unavailable
  }
}

// =========================================================================
// 1. Working Memory: N-Back, Corsi & Reverse Span, Stroop Rule-Switch
// =========================================================================

export const N_BACK_LETTERS = ['A', 'C', 'H', 'K', 'L', 'Q', 'R', 'T'] as const;

export interface NBackTrial {
  stepIndex: number;
  cellIndex: number; // 0..8
  letter: string;
  isPositionMatch: boolean;
  isLetterMatch: boolean;
}

export interface NBackEvaluation {
  totalEvaluatedTrials: number;
  hits: number;
  misses: number;
  falseAlarms: number;
  accuracyPercent: number;
  nextRecommendedN: number;
  tierDeltaLabel: string;
}

export function generateNBackSequence(
  n: number,
  totalTrials: number = 16 + Math.max(1, Math.min(4, n)),
  mode: NBackMode = 'DUAL',
  rng: RngFn = Math.random
): NBackTrial[] {
  const safeN = Math.max(1, Math.min(4, n));
  const count = Math.max(safeN + 6, totalTrials);
  const positions: number[] = new Array(count).fill(0);
  const letters: string[] = new Array(count).fill('A');

  for (let i = 0; i < count; i++) {
    if (i < safeN) {
      positions[i] = randInt(rng, 0, 9);
      letters[i] = N_BACK_LETTERS[randInt(rng, 0, N_BACK_LETTERS.length)];
    } else {
      const forcePosMatch = mode !== 'LETTER_ONLY' && rng() < 0.30;
      const forceLetterMatch = mode !== 'POSITION_ONLY' && rng() < 0.30;

      if (forcePosMatch) {
        positions[i] = positions[i - safeN];
      } else {
        let candidate = randInt(rng, 0, 9);
        if (candidate === positions[i - safeN]) {
          candidate = (candidate + 1 + randInt(rng, 0, 8)) % 9;
        }
        positions[i] = candidate;
      }

      if (forceLetterMatch) {
        letters[i] = letters[i - safeN];
      } else {
        let candidate = N_BACK_LETTERS[randInt(rng, 0, N_BACK_LETTERS.length)];
        if (candidate === letters[i - safeN]) {
          const curIdx = N_BACK_LETTERS.indexOf(candidate);
          const altIdx = (curIdx + 1 + randInt(rng, 0, N_BACK_LETTERS.length - 1)) % N_BACK_LETTERS.length;
          candidate = N_BACK_LETTERS[altIdx];
        }
        letters[i] = candidate;
      }
    }
  }

  return Array.from({ length: count }, (_, idx) => {
    const posMatch = idx >= safeN && positions[idx] === positions[idx - safeN] && mode !== 'LETTER_ONLY';
    const letMatch = idx >= safeN && letters[idx] === letters[idx - safeN] && mode !== 'POSITION_ONLY';
    return {
      stepIndex: idx,
      cellIndex: positions[idx],
      letter: letters[idx],
      isPositionMatch: posMatch,
      isLetterMatch: letMatch,
    };
  });
}

export function evaluateNBackSession(
  currentN: number,
  mode: NBackMode,
  trials: NBackTrial[],
  userPosTaps: Set<number>,
  userLetterTaps: Set<number>
): NBackEvaluation {
  const safeN = Math.max(1, Math.min(4, currentN));
  let hits = 0;
  let misses = 0;
  let falseAlarms = 0;
  let correctRejections = 0;

  const activeChannels = mode === 'DUAL' ? 2 : 1;
  const scored = trials.filter((t) => t.stepIndex >= safeN);

  for (const trial of scored) {
    if (mode !== 'LETTER_ONLY') {
      const tapped = userPosTaps.has(trial.stepIndex);
      if (trial.isPositionMatch && tapped) hits++;
      else if (trial.isPositionMatch && !tapped) misses++;
      else if (!trial.isPositionMatch && tapped) falseAlarms++;
      else correctRejections++;
    }
    if (mode !== 'POSITION_ONLY') {
      const tapped = userLetterTaps.has(trial.stepIndex);
      if (trial.isLetterMatch && tapped) hits++;
      else if (trial.isLetterMatch && !tapped) misses++;
      else if (!trial.isLetterMatch && tapped) falseAlarms++;
      else correctRejections++;
    }
  }

  const totalSignals = scored.length * activeChannels;
  const accuracy =
    totalSignals > 0 ? Math.max(0, Math.min(100, Math.round(((hits + correctRejections) / totalSignals) * 100))) : 0;

  const nextN =
    accuracy >= 85 ? Math.min(4, safeN + 1) : accuracy < 55 ? Math.max(1, safeN - 1) : safeN;

  const deltaLabel =
    nextN > safeN
      ? `Promoted to ${nextN}-Back`
      : nextN < safeN
      ? `Stepped down to ${nextN}-Back`
      : `Holding at ${nextN}-Back`;

  return {
    totalEvaluatedTrials: scored.length,
    hits,
    misses,
    falseAlarms,
    accuracyPercent: accuracy,
    nextRecommendedN: nextN,
    tierDeltaLabel: deltaLabel,
  };
}

export interface CorsiPuzzle {
  gridSize: number;
  span: number;
  reverseMode: boolean;
  sequence: number[];
  expectedInput: number[];
  alphanumericLabels: string[];
}

const ALPHANUMERIC_POOL = [
  'A1', 'B4', 'C7', 'D2', 'E9', 'F5', 'G3', 'H8',
  'J6', 'K2', 'L7', 'M3', 'N5', 'P8', 'R4', 'T1',
  'V9', 'W6', 'X3', 'Z7', 'Q2', 'S5', 'K8', 'R7', 'M9',
];

export function generateCorsiPuzzle(
  gridSize: number = 4,
  span: number = 4,
  reverseMode: boolean = true,
  withAlphanumericLabels: boolean = false,
  rng: RngFn = Math.random
): CorsiPuzzle {
  const safeGrid = Math.max(4, Math.min(5, gridSize));
  const totalCells = safeGrid * safeGrid;
  const safeSpan = Math.min(totalCells, Math.max(4, Math.min(8, span)));
  const allIndices = Array.from({ length: totalCells }, (_, i) => i);
  const sequence = shuffled(allIndices, rng).slice(0, safeSpan);
  const expectedInput = reverseMode ? [...sequence].reverse() : [...sequence];
  const alphanumericLabels = withAlphanumericLabels
    ? shuffled(ALPHANUMERIC_POOL, rng).slice(0, totalCells)
    : [];

  return {
    gridSize: safeGrid,
    span: safeSpan,
    reverseMode,
    sequence,
    expectedInput,
    alphanumericLabels,
  };
}

export interface StroopColorMeta {
  id: 'RED' | 'BLUE' | 'GREEN' | 'AMBER';
  label: string;
  hex: string;
}

export const STROOP_COLORS: StroopColorMeta[] = [
  { id: 'RED', label: 'RED', hex: '#DC2626' },
  { id: 'BLUE', label: 'BLUE', hex: '#2563EB' },
  { id: 'GREEN', label: 'GREEN', hex: '#16A34A' },
  { id: 'AMBER', label: 'AMBER', hex: '#D97706' },
];

export const STROOP_DIRECTIONS = [
  { id: 'UP', label: 'UP', symbol: '↑' },
  { id: 'RIGHT', label: 'RIGHT', symbol: '→' },
  { id: 'DOWN', label: 'DOWN', symbol: '↓' },
  { id: 'LEFT', label: 'LEFT', symbol: '←' },
] as const;

export type StroopRuleId =
  | 'TAP_INK_COLOR'
  | 'TAP_WORD_MEANING'
  | 'TAP_ARROW_DIR'
  | 'TAP_OPPOSITE_DIR';

export const STROOP_RULE_META: Record<StroopRuleId, { badge: string; instruction: string }> = {
  TAP_INK_COLOR: { badge: 'TAP INK COLOR', instruction: 'Tap the INK color, ignore the word' },
  TAP_WORD_MEANING: { badge: 'TAP WORD MEANING', instruction: 'Tap the WORD meaning, ignore ink color' },
  TAP_ARROW_DIR: { badge: 'TAP ARROW DIRECTION', instruction: 'Tap the direction the arrow POINTS' },
  TAP_OPPOSITE_DIR: { badge: 'TAP OPPOSITE DIRECTION', instruction: 'Tap the OPPOSITE direction to the arrow' },
};

export interface StroopTrial {
  id: number;
  isArrowMode: boolean;
  rule: StroopRuleId;
  displayWord: StroopColorMeta;
  inkColor: StroopColorMeta;
  arrowIndex: number;
  expectedChoiceIndex: number;
  choices: string[];
}

export function generateStroopTrial(
  stepIndex: number,
  allowArrowMode: boolean = true,
  previousRule: StroopRuleId | null = null,
  rng: RngFn = Math.random
): StroopTrial {
  const isArrow = allowArrowMode && rng() < 0.35;
  if (!isArrow) {
    const wordIdx = randInt(rng, 0, STROOP_COLORS.length);
    const word = STROOP_COLORS[wordIdx];
    const inkIdx =
      rng() < 0.80
        ? (wordIdx + 1 + randInt(rng, 0, STROOP_COLORS.length - 1)) % STROOP_COLORS.length
        : wordIdx;
    const ink = STROOP_COLORS[inkIdx];

    let rule: StroopRuleId;
    if (previousRule === null) {
      rule = rng() < 0.5 ? 'TAP_INK_COLOR' : 'TAP_WORD_MEANING';
    } else if (rng() < 0.42) {
      rule = previousRule === 'TAP_INK_COLOR' ? 'TAP_WORD_MEANING' : 'TAP_INK_COLOR';
    } else if (previousRule === 'TAP_INK_COLOR' || previousRule === 'TAP_WORD_MEANING') {
      rule = previousRule;
    } else {
      rule = 'TAP_INK_COLOR';
    }

    return {
      id: stepIndex,
      isArrowMode: false,
      rule,
      displayWord: word,
      inkColor: ink,
      arrowIndex: 0,
      expectedChoiceIndex: rule === 'TAP_INK_COLOR' ? inkIdx : wordIdx,
      choices: STROOP_COLORS.map((c) => c.label),
    };
  }

  const arrowIdx = randInt(rng, 0, STROOP_DIRECTIONS.length);
  let rule: StroopRuleId;
  if (previousRule === null) {
    rule = rng() < 0.5 ? 'TAP_ARROW_DIR' : 'TAP_OPPOSITE_DIR';
  } else if (rng() < 0.45) {
    rule = previousRule === 'TAP_ARROW_DIR' ? 'TAP_OPPOSITE_DIR' : 'TAP_ARROW_DIR';
  } else if (previousRule === 'TAP_ARROW_DIR' || previousRule === 'TAP_OPPOSITE_DIR') {
    rule = previousRule;
  } else {
    rule = 'TAP_OPPOSITE_DIR';
  }

  return {
    id: stepIndex,
    isArrowMode: true,
    rule,
    displayWord: STROOP_COLORS[0],
    inkColor: STROOP_COLORS[1],
    arrowIndex: arrowIdx,
    expectedChoiceIndex: rule === 'TAP_ARROW_DIR' ? arrowIdx : (arrowIdx + 2) % 4,
    choices: STROOP_DIRECTIONS.map((d) => `${d.symbol} ${d.label}`),
  };
}

// =========================================================================
// 2. Critical Reasoning: Venn Syllogisms, Seating Grids, Arguments, Data Sufficiency
// =========================================================================

export type SyllogismQuantifier = 'ALL' | 'NO' | 'SOME' | 'SOME_NOT';

export interface SyllogismStatement {
  quantifier: SyllogismQuantifier;
  subjectIdx: number;
  predicateIdx: number;
}

export type SyllogismAnswer = 'ONLY_I' | 'ONLY_II' | 'BOTH' | 'EITHER' | 'NEITHER';

export const SYLLOGISM_ANSWER_LABELS: Record<SyllogismAnswer, string> = {
  ONLY_I: 'Only Conclusion I follows',
  ONLY_II: 'Only Conclusion II follows',
  BOTH: 'Both I and II follow',
  EITHER: 'Either I or II follows',
  NEITHER: 'Neither I nor II follows',
};

export interface SyllogismVerification {
  conclusionIFollows: boolean;
  conclusionIIFollows: boolean;
  eitherOrValid: boolean;
  answer: SyllogismAnswer;
  explanation: string;
}

export interface SyllogismPuzzle {
  terms: string[];
  premises: SyllogismStatement[];
  conclusionI: SyllogismStatement;
  conclusionII: SyllogismStatement;
  verification: SyllogismVerification;
}

export function formatSyllogismStatement(stmt: SyllogismStatement, terms: string[]): string {
  const s = terms[stmt.subjectIdx] || 'A';
  const p = terms[stmt.predicateIdx] || 'B';
  switch (stmt.quantifier) {
    case 'ALL':
      return `All ${s} are ${p}`;
    case 'NO':
      return `No ${s} is ${p}`;
    case 'SOME':
      return `Some ${s} are ${p}`;
    case 'SOME_NOT':
      return `Some ${s} are not ${p}`;
  }
}

const TERM_GROUPS: string[][] = [
  ['Judges', 'Lawyers', 'Scholars', 'Orators'],
  ['Photons', 'Waves', 'Particles', 'Signals'],
  ['Sensors', 'Relays', 'Circuits', 'Modules'],
  ['Novels', 'Essays', 'Poems', 'Journals'],
  ['Atoms', 'Ions', 'Radicals', 'Catalysts'],
  ['Matrices', 'Vectors', 'Tensors', 'Scalars'],
];

function trailingZeroBitIndex(mask: number): number {
  let idx = 0;
  let m = mask;
  while ((m & 1) === 0 && idx < 31) {
    m >>= 1;
    idx++;
  }
  return idx;
}

export function verifySyllogism(
  terms: string[],
  premises: SyllogismStatement[],
  conclusionI: SyllogismStatement,
  conclusionII: SyllogismStatement
): SyllogismVerification | null {
  const termCount = Math.max(3, Math.min(4, terms.length));
  const regionCount = 1 << termCount;
  const allRegionsMask = (1 << regionCount) - 1;

  const maskFor = (stmt: SyllogismStatement, positivePredicate: boolean): number => {
    let mask = 0;
    for (let r = 0; r < regionCount; r++) {
      const inS = ((r >> stmt.subjectIdx) & 1) === 1;
      const inP = ((r >> stmt.predicateIdx) & 1) === 1;
      if (inS && (positivePredicate ? inP : !inP)) {
        mask |= 1 << r;
      }
    }
    return mask;
  };

  let allowedMask = allRegionsMask;
  const existentialReqMasks: number[] = [];

  for (const p of premises) {
    if (p.quantifier === 'ALL') {
      allowedMask &= ~maskFor(p, false);
    } else if (p.quantifier === 'NO') {
      allowedMask &= ~maskFor(p, true);
    } else if (p.quantifier === 'SOME') {
      existentialReqMasks.push(maskFor(p, true));
    } else {
      existentialReqMasks.push(maskFor(p, false));
    }
  }

  for (const req of existentialReqMasks) {
    if ((req & allowedMask) === 0) return null;
  }

  for (let tIdx = 0; tIdx < termCount; tIdx++) {
    let termAllowed = 0;
    for (let r = 0; r < regionCount; r++) {
      if (((r >> tIdx) & 1) === 1 && ((allowedMask >> r) & 1) === 1) {
        termAllowed |= 1 << r;
      }
    }
    if (termAllowed === 0) return null;
  }

  const describeRegion = (region: number): string => {
    const inside: string[] = [];
    const outside: string[] = [];
    for (let i = 0; i < termCount; i++) {
      if (((region >> i) & 1) === 1) inside.push(terms[i]);
      else outside.push(terms[i]);
    }
    const inStr = inside.length === 0 ? 'None' : inside.join(' ∩ ');
    const outStr = outside.length === 0 ? '' : ` (outside ${outside.join(', ')})`;
    return `[${inStr}${outStr}]`;
  };

  const evaluateConclusion = (conc: SyllogismStatement): [boolean, string] => {
    const label = formatSyllogismStatement(conc, terms);
    if (conc.quantifier === 'ALL' || conc.quantifier === 'NO') {
      const forbidMask = maskFor(conc, conc.quantifier === 'NO');
      const counterRegions = allowedMask & forbidMask;
      if (counterRegions === 0) {
        return [
          true,
          `${label} holds in all Venn subsets because every counterexample region is ruled out by the universal premises.`,
        ];
      }
      const witness = trailingZeroBitIndex(counterRegions);
      return [
        false,
        `${label} does NOT necessarily follow. Counterexample Venn subset ${describeRegion(witness)} satisfies all premises while violating the conclusion.`,
      ];
    }

    const targetMask = maskFor(conc, conc.quantifier === 'SOME');
    const forced = existentialReqMasks.some((req) => {
      const active = req & allowedMask;
      return active !== 0 && (active & ~targetMask) === 0;
    });
    if (forced) {
      return [
        true,
        `${label} necessarily follows because an existential premise forces at least one element inside the target Venn intersection.`,
      ];
    }

    const witnessNotes: string[] = [];
    for (const req of existentialReqMasks) {
      const safe = req & allowedMask & ~targetMask;
      if (safe !== 0) {
        const desc = describeRegion(trailingZeroBitIndex(safe));
        if (!witnessNotes.includes(desc)) witnessNotes.push(desc);
      }
    }
    const fallback = allowedMask & ~targetMask;
    const witnessDesc =
      witnessNotes.length > 0
        ? `Counterexample: placing witnesses in ${witnessNotes.join(' and ')}`
        : fallback !== 0
        ? `Counterexample: elements residing in ${describeRegion(trailingZeroBitIndex(fallback))}`
        : 'Counterexample: empty target subset';
    return [
      false,
      `${label} does NOT necessarily follow. ${witnessDesc} satisfies every premise without making this conclusion true.`,
    ];
  };

  const [iFollows, iExpl] = evaluateConclusion(conclusionI);
  const [iiFollows, iiExpl] = evaluateConclusion(conclusionII);

  const isComplementaryPair = (a: SyllogismStatement, b: SyllogismStatement): boolean => {
    const sameDir = a.subjectIdx === b.subjectIdx && a.predicateIdx === b.predicateIdx;
    const swappedDir = a.subjectIdx === b.predicateIdx && a.predicateIdx === b.subjectIdx;
    const set = new Set([a.quantifier, b.quantifier]);
    if (sameDir && (set.has('NO') && set.has('SOME'))) return true;
    if (sameDir && (set.has('ALL') && set.has('SOME_NOT'))) return true;
    if (swappedDir && (set.has('NO') && set.has('SOME'))) return true;
    return false;
  };

  const eitherOr = !iFollows && !iiFollows && isComplementaryPair(conclusionI, conclusionII);
  const finalAnswer: SyllogismAnswer =
    iFollows && iiFollows
      ? 'BOTH'
      : iFollows
      ? 'ONLY_I'
      : iiFollows
      ? 'ONLY_II'
      : eitherOr
      ? 'EITHER'
      : 'NEITHER';

  let explanation = `Conclusion I: ${iExpl}\nConclusion II: ${iiExpl}`;
  if (eitherOr) {
    explanation +=
      '\nEither/Or Check: Together, I and II form a strict complementary Venn partition, so in every valid model at least one must hold.';
  }

  return {
    conclusionIFollows: iFollows,
    conclusionIIFollows: iiFollows,
    eitherOrValid: eitherOr,
    answer: finalAnswer,
    explanation,
  };
}

export function generateSyllogismPuzzle(
  desiredAnswer: SyllogismAnswer | null = null,
  rng: RngFn = Math.random
): SyllogismPuzzle {
  const answers: SyllogismAnswer[] = ['ONLY_I', 'ONLY_II', 'BOTH', 'EITHER', 'NEITHER'];
  const target = desiredAnswer ?? answers[randInt(rng, 0, answers.length)];
  const terms = shuffled(TERM_GROUPS[randInt(rng, 0, TERM_GROUPS.length)], rng);
  const qTypes: SyllogismQuantifier[] = ['ALL', 'NO', 'SOME', 'SOME_NOT'];

  const stmtEq = (a: SyllogismStatement, b: SyllogismStatement) =>
    a.quantifier === b.quantifier && a.subjectIdx === b.subjectIdx && a.predicateIdx === b.predicateIdx;

  for (let attempt = 0; attempt < 400; attempt++) {
    const p1: SyllogismStatement = { quantifier: qTypes[randInt(rng, 0, 4)], subjectIdx: 0, predicateIdx: 1 };
    const p2: SyllogismStatement = { quantifier: qTypes[randInt(rng, 0, 4)], subjectIdx: 1, predicateIdx: 2 };
    const useThird = rng() < 0.5;
    const premises = useThird
      ? [p1, p2, { quantifier: qTypes[randInt(rng, 0, 4)], subjectIdx: 2, predicateIdx: 3 }]
      : [p1, p2];
    const maxTerm = useThird ? 3 : 2;

    let c1: SyllogismStatement;
    let c2: SyllogismStatement;
    if (target === 'EITHER') {
      const s = randInt(rng, 0, maxTerm + 1);
      let p = randInt(rng, 0, maxTerm + 1);
      if (p === s) p = (s + 1) % (maxTerm + 1);
      if (rng() < 0.5) {
        c1 = { quantifier: 'SOME', subjectIdx: s, predicateIdx: p };
        c2 = { quantifier: 'NO', subjectIdx: s, predicateIdx: p };
      } else {
        c1 = { quantifier: 'ALL', subjectIdx: s, predicateIdx: p };
        c2 = { quantifier: 'SOME_NOT', subjectIdx: s, predicateIdx: p };
      }
    } else {
      const s1 = randInt(rng, 0, maxTerm + 1);
      let p1c = randInt(rng, 0, maxTerm + 1);
      if (p1c === s1) p1c = (s1 + 1) % (maxTerm + 1);
      const s2 = randInt(rng, 0, maxTerm + 1);
      let p2c = randInt(rng, 0, maxTerm + 1);
      if (p2c === s2) p2c = (s2 + 1) % (maxTerm + 1);
      c1 = { quantifier: qTypes[randInt(rng, 0, 4)], subjectIdx: s1, predicateIdx: p1c };
      c2 = { quantifier: qTypes[randInt(rng, 0, 4)], subjectIdx: s2, predicateIdx: p2c };
    }

    if (premises.some((pr) => stmtEq(pr, c1) || stmtEq(pr, c2)) || stmtEq(c1, c2)) continue;

    const sliceTerms = terms.slice(0, maxTerm + 1);
    const verified = verifySyllogism(sliceTerms, premises, c1, c2);
    if (verified && verified.answer === target) {
      return {
        terms: sliceTerms,
        premises,
        conclusionI: c1,
        conclusionII: c2,
        verification: verified,
      };
    }
  }

  const fallbackTerms = ['Alphas', 'Betas', 'Gammas', 'Deltas'];
  const fp: SyllogismStatement[] = [
    { quantifier: 'ALL', subjectIdx: 0, predicateIdx: 1 },
    { quantifier: 'NO', subjectIdx: 1, predicateIdx: 2 },
    { quantifier: 'SOME', subjectIdx: 2, predicateIdx: 3 },
  ];
  const fc1: SyllogismStatement = { quantifier: 'NO', subjectIdx: 0, predicateIdx: 2 };
  const fc2: SyllogismStatement = { quantifier: 'SOME_NOT', subjectIdx: 3, predicateIdx: 1 };
  const ver = verifySyllogism(fallbackTerms, fp, fc1, fc2)!;
  return { terms: fallbackTerms, premises: fp, conclusionI: fc1, conclusionII: fc2, verification: ver };
}

export interface SeatingPuzzle {
  id: string;
  isCircular: boolean;
  people: string[];
  clues: string[];
  promptQuestion: string;
  options: string[];
  correctIndex: number;
  explanation: string;
  solutionOrder: string[];
}

interface SeatRule {
  holds: (perm: number[], isCircular: boolean) => boolean;
  describe: (names: string[], isCircular: boolean) => string;
}

const SEAT_NAME_POOLS: string[][] = [
  ['Aarav', 'Bhavna', 'Chirag', 'Diya', 'Eshan'],
  ['Kabir', 'Meera', 'Nikhil', 'Priya', 'Rohan'],
  ['Ananya', 'Dev', 'Farah', 'Gaurav', 'Ira'],
];

export function generateSeatingPuzzle(
  seatCount: number = 4,
  isCircular: boolean = false,
  rng: RngFn = Math.random
): SeatingPuzzle {
  const count = Math.max(4, Math.min(5, seatCount));
  const pool = SEAT_NAME_POOLS[randInt(rng, 0, SEAT_NAME_POOLS.length)];
  const names = pool.slice(0, count);

  const allPerms: number[][] = [];
  const used = new Array<boolean>(count).fill(false);
  const current: number[] = [];
  const buildPerms = () => {
    if (current.length === count) {
      allPerms.push([...current]);
      return;
    }
    for (let i = 0; i < count; i++) {
      if (!used[i]) {
        used[i] = true;
        current.push(i);
        buildPerms();
        current.pop();
        used[i] = false;
      }
    }
  };
  buildPerms();

  const fixedRule = (personIdx: number, seatIdx: number): SeatRule => ({
    holds: (perm) => perm[seatIdx] === personIdx,
    describe: (ns, circ) =>
      circ
        ? `${ns[personIdx]} sits at Seat #${seatIdx + 1}.`
        : `${ns[personIdx]} sits at position ${seatIdx + 1} from the left.`,
  });

  const offsetRule = (personIdx: number, anchorIdx: number, offset: number): SeatRule => ({
    holds: (perm, circ) => {
      const posA = perm.indexOf(personIdx);
      const posB = perm.indexOf(anchorIdx);
      return circ ? (posB + offset + perm.length) % perm.length === posA : posB + offset === posA;
    },
    describe: (ns) => {
      const absOff = Math.abs(offset);
      const dir = offset > 0 ? 'right' : 'left';
      if (absOff === 1) return `${ns[personIdx]} sits immediately to the ${dir} of ${ns[anchorIdx]}.`;
      const ord = absOff === 2 ? '2nd to the' : `${absOff}th to the`;
      return `${ns[personIdx]} sits ${ord} ${dir} of ${ns[anchorIdx]}.`;
    },
  });

  const notAdjRule = (personA: number, personB: number): SeatRule => ({
    holds: (perm, circ) => {
      const diff = Math.abs(perm.indexOf(personA) - perm.indexOf(personB));
      const adj = circ ? diff === 1 || diff === perm.length - 1 : diff === 1;
      return !adj;
    },
    describe: (ns) => `${ns[personA]} and ${ns[personB]} do NOT sit next to each other.`,
  });

  const notEndsRule = (personIdx: number): SeatRule => ({
    holds: (perm) => {
      const pos = perm.indexOf(personIdx);
      return pos !== 0 && pos !== perm.length - 1;
    },
    describe: (ns) => `${ns[personIdx]} does NOT sit at either extreme end.`,
  });

  for (let attempt = 0; attempt < 80; attempt++) {
    const truth = shuffled(Array.from({ length: count }, (_, i) => i), rng);
    const candidateRules: SeatRule[] = [];
    if (isCircular) {
      candidateRules.push(fixedRule(truth[0], 0));
    } else {
      const anchor = randInt(rng, 0, count);
      candidateRules.push(fixedRule(truth[anchor], anchor));
    }

    for (let i = 0; i < count; i++) {
      for (let j = 0; j < count; j++) {
        if (i === j) continue;
        const posI = truth.indexOf(i);
        const posJ = truth.indexOf(j);
        const rawOffset = posI - posJ;
        if (isCircular) {
          const cw = (posI - posJ + count) % count;
          if (cw === 1 || cw === 2) candidateRules.push(offsetRule(i, j, cw));
          const ccw = (posJ - posI + count) % count;
          if (ccw === 1 || ccw === 2) candidateRules.push(offsetRule(i, j, -ccw));
        } else if (rawOffset >= -2 && rawOffset <= 2 && rawOffset !== 0) {
          candidateRules.push(offsetRule(i, j, rawOffset));
        }
        if (i < j && notAdjRule(i, j).holds(truth, isCircular)) {
          candidateRules.push(notAdjRule(i, j));
        }
      }
      if (!isCircular && notEndsRule(i).holds(truth, false)) {
        candidateRules.push(notEndsRule(i));
      }
    }

    const chosen: SeatRule[] = [candidateRules[0]];
    const remaining = shuffled(candidateRules.slice(1), rng);
    let matching = allPerms.filter((p) => chosen[0].holds(p, isCircular));

    for (const rule of remaining) {
      if (matching.length <= 1) break;
      const narrowed = matching.filter((p) => rule.holds(p, isCircular));
      if (narrowed.length > 0 && narrowed.length < matching.length) {
        chosen.push(rule);
        matching = narrowed;
      }
    }

    if (matching.length === 1 && chosen.length <= 5) {
      const solution = matching[0];
      const solutionNames = solution.map((idx) => names[idx]);
      const targetSeat = isCircular ? randInt(rng, 1, count) : randInt(rng, 0, count);
      const targetName = solutionNames[targetSeat];
      const promptQuestion = isCircular
        ? `Everyone faces the center of a ${count}-seat circular table. Who sits at Seat #${targetSeat + 1}?`
        : `All ${count} people sit in a straight row facing North. Who sits at position ${targetSeat + 1} from the left?`;
      const options = shuffled(names, rng);
      const orderStr = isCircular
        ? solutionNames.map((n, idx) => `#${idx + 1}:${n}`).join(' → ')
        : solutionNames.join(' | ');
      return {
        id: `seat_${count}_${randInt(rng, 1000, 9999)}`,
        isCircular,
        people: names,
        clues: chosen.map((r) => r.describe(names, isCircular)),
        promptQuestion,
        options,
        correctIndex: options.indexOf(targetName),
        explanation: `Unique verified arrangement: ${orderStr}. Therefore ${targetName} occupies Seat ${targetSeat + 1}.`,
        solutionOrder: solutionNames,
      };
    }
  }

  const fallbackOrder = ['Aarav', 'Chirag', 'Bhavna', 'Diya'];
  return {
    id: 'seat_fallback',
    isCircular: false,
    people: fallbackOrder,
    clues: [
      'Aarav sits at position 1 from the left.',
      'Bhavna sits 2nd to the right of Aarav.',
      'Chirag sits immediately to the left of Bhavna.',
    ],
    promptQuestion: 'All 4 people sit in a row facing North. Who sits at position 4 from the left?',
    options: ['Aarav', 'Bhavna', 'Chirag', 'Diya'],
    correctIndex: 3,
    explanation: 'Unique verified arrangement: Aarav | Chirag | Bhavna | Diya.',
    solutionOrder: fallbackOrder,
  };
}

export interface ArgumentQuestion {
  id: string;
  categoryTag: string;
  passage: string;
  prompt: string;
  options: string[];
  correctIndex: number;
  explanation: string;
}

const ARGUMENT_BANK: ArgumentQuestion[] = [
  {
    id: 'arg_1',
    categoryTag: 'Correlation ≠ Causation',
    passage: 'A city survey found that students who carry expensive scientific calculators score 18% higher on quantitative exams than those using basic calculators. Therefore, buying an advanced calculator boosts quantitative reasoning ability.',
    prompt: 'Which flaw most undermines the conclusion?',
    options: [
      'Confuses correlation with causation by ignoring confounding preparation or track differences',
      'Uses circular reasoning by restating the conclusion in the premise',
      'Commits a strawman fallacy by misrepresenting student preferences',
      'Relies on an appeal to authority without statistical evidence',
    ],
    correctIndex: 0,
    explanation: 'Students enrolled in advanced STEM tracks are more likely both to own scientific calculators and to have stronger quantitative training. Correlation alone does not prove the calculator caused higher scores.',
  },
  {
    id: 'arg_2',
    categoryTag: 'Required Assumption',
    passage: 'To cut highway congestion during peak hours, the municipal council plans to double toll charges between 8 AM and 10 AM. Because commuters dislike higher fees, morning highway traffic will drop sharply.',
    prompt: 'Which assumption is strictly required for the plan to work?',
    options: [
      'Commuters have feasible alternatives (such as shifting travel times or using transit) instead of paying the toll',
      'Doubling the toll will generate enough revenue to build two additional highway lanes',
      'Afternoon traffic will decrease by the exact same proportion as morning traffic',
      'All morning commuters currently travel alone in private cars',
    ],
    correctIndex: 0,
    explanation: 'Even if commuters dislike higher fees, if morning travel is completely inelastic (no transit or flexible hours), traffic will not drop—commuters will simply pay more.',
  },
  {
    id: 'arg_3',
    categoryTag: 'False Dilemma',
    passage: "Editorial: 'Either the examination board cancels negative marking entirely, or deserving students will continue losing marks due to careless clerical errors.'",
    prompt: 'Which logical fallacy is committed in the editorial?',
    options: [
      'False Dilemma — treats two extreme choices as the only possibilities',
      'Hasty Generalization — extrapolates from an unrepresentative sample',
      'Ad Hominem — attacks the motives of the examination board',
      'Post Hoc — assumes an earlier event caused a later one',
    ],
    correctIndex: 0,
    explanation: 'Middle-ground options exist (partial credit, clearer OMR formatting, or lower penalty ratios), so presenting only two extremes is a False Dilemma.',
  },
  {
    id: 'arg_4',
    categoryTag: 'Direct Inference',
    passage: 'Every candidate shortlisted for the final interview passed both the written aptitude screen and the domain essay. Priya was shortlisted for the final interview, whereas Rohan passed the written aptitude screen but was not shortlisted.',
    prompt: 'Which statement must be true based strictly on the passage?',
    options: [
      'Priya passed the domain essay',
      'Rohan failed the domain essay',
      'Everyone who passed both papers was shortlisted',
      'Priya scored higher than Rohan on the written aptitude screen',
    ],
    correctIndex: 0,
    explanation: 'Passing both tests is a necessary condition for shortlisting. Since Priya was shortlisted, she must have passed the domain essay. Rohan might have passed both yet missed a tie-breaker or quota.',
  },
  {
    id: 'arg_5',
    categoryTag: 'Circular Reasoning',
    passage: 'The new textbook is undeniably the most accurate syllabus guide on the market, because every statement in it conforms completely to the authoritative facts presented inside the textbook itself.',
    prompt: 'Which reasoning error appears here?',
    options: [
      "Circular Reasoning — uses the textbook's own claims to prove its accuracy",
      "Strawman — exaggerates a rival textbook's weaknesses",
      'Slippery Slope — predicts an unsupported chain of disasters',
      'Equivocation — shifts the meaning of a technical term mid-argument',
    ],
    correctIndex: 0,
    explanation: "Justifying a source's accuracy solely by referencing the source itself assumes what needs independent verification.",
  },
  {
    id: 'arg_6',
    categoryTag: 'Strawman Fallacy',
    passage: "A: 'We should allocate 15 minutes of class time daily to silent reading comprehension.'\nB: 'My opponent wants to abolish mathematics instruction and turn the entire school day into leisure reading!'",
    prompt: 'How does Speaker B distort the debate?',
    options: [
      "Strawman — replaces A's modest proposal with an extreme caricature that is easy to attack",
      'False Analogy — compares two unrelated educational systems',
      'Bandwagon — appeals to majority opinion rather than evidence',
      'Red Herring — introduces an irrelevant budget statistic',
    ],
    correctIndex: 0,
    explanation: "Speaker A proposed 15 minutes of reading comprehension, not abolishing math. Attacking an exaggerated version of an opponent's position is a Strawman.",
  },
  {
    id: 'arg_7',
    categoryTag: 'Hasty Generalization',
    passage: 'Two candidates from District X who enrolled in our weekend mock series topped the state rank list. Therefore, every weekend-series student from District X is guaranteed a top-100 state rank.',
    prompt: 'What is the primary logical flaw?',
    options: [
      'Hasty Generalization — leaps to a universal guarantee from only two standout cases',
      'Tu Quoque — accuses critics of hypocrisy',
      "Equivocation — uses 'top' in two incompatible senses",
      'Composition — assumes parts inherit properties of the whole',
    ],
    correctIndex: 0,
    explanation: "Drawing an 'every student is guaranteed' conclusion from two top performers ignores sample size and survivorship bias.",
  },
  {
    id: 'arg_8',
    categoryTag: 'Necessary vs Sufficient',
    passage: "Policy: 'To qualify for a research stipend, an applicant must publish at least one peer-reviewed paper.' Meera has published three peer-reviewed papers, so she is assured of receiving the stipend.",
    prompt: 'Why is the conclusion flawed?',
    options: [
      'Confuses a necessary condition with a sufficient condition',
      'Relies on an outdated definition of peer review',
      'Assumes publication quality equals publication count',
      'Neglects to specify when the stipend is disbursed',
    ],
    correctIndex: 0,
    explanation: "Publishing one paper is required ('must'), not sufficient on its own—other criteria such as GPA, funding caps, or interviews may still disqualify Meera.",
  },
  {
    id: 'arg_9',
    categoryTag: 'Base Rate Neglect',
    passage: 'Diagnostic Alert: A rapid test is 95% accurate. It flags a rare syllabus misconception found in only 1% of test-takers. Therefore, a student flagged positive has a 95% chance of actually having the misconception.',
    prompt: 'Why is the 95% probability claim incorrect?',
    options: [
      'It ignores the 1% base rate: out of 1,000 students, ~50 false positives outnumber ~10 true positives',
      'It assumes students take the test twice independently',
      'It confuses precision with recall in reverse',
      'It overlooks the confidence interval of the sample size',
    ],
    correctIndex: 0,
    explanation: 'With 1% prevalence in 1,000 students: ~10 true cases (~9.5 flagged) vs ~990 non-misconception cases (~49.5 false positives). Most positive flags are false positives!',
  },
  {
    id: 'arg_10',
    categoryTag: 'Required Assumption',
    passage: 'After installing glare-free LED lamps in Study Hall B, average reading speed rose by 12% over the next month. Hence, glare-free lighting directly increases reading speed.',
    prompt: 'Which unstated assumption does the conclusion depend upon?',
    options: [
      'No other factor (such as easier practice passages or self-selection of fast readers into Hall B) changed during that month',
      'Glare-free LEDs consume less electricity than fluorescent tubes',
      'All students in Study Hall B prefer bright white lighting over warm lighting',
      'Reading comprehension accuracy also increased by at least 12%',
    ],
    correctIndex: 0,
    explanation: 'Causal attribution from a before-and-after observation requires ruling out rival explanations like passage difficulty or student cohort changes.',
  },
];

export function getShuffledArgumentBank(rng: RngFn = Math.random): ArgumentQuestion[] {
  return shuffled(ARGUMENT_BANK, rng).map((q) => {
    const indexed = shuffled(
      q.options.map((text, idx) => ({ idx, text })),
      rng
    );
    return {
      ...q,
      options: indexed.map((i) => i.text),
      correctIndex: indexed.findIndex((i) => i.idx === q.correctIndex),
    };
  });
}

export type DataSufficiencyAnswer =
  | 'STATEMENT_I_ALONE'
  | 'STATEMENT_II_ALONE'
  | 'EITHER_ALONE'
  | 'BOTH_TOGETHER'
  | 'NEITHER';

export const DATA_SUFFICIENCY_LABELS: Record<DataSufficiencyAnswer, string> = {
  STATEMENT_I_ALONE: 'Statement I alone is sufficient',
  STATEMENT_II_ALONE: 'Statement II alone is sufficient',
  EITHER_ALONE: 'Either Statement I alone OR II alone is sufficient',
  BOTH_TOGETHER: 'Both Statements I and II together are needed',
  NEITHER: 'Neither I nor II (even together) is sufficient',
};

export interface DataSufficiencyQuestion {
  id: string;
  trapTag: string;
  question: string;
  statementI: string;
  statementII: string;
  answer: DataSufficiencyAnswer;
  explanation: string;
}

const DATA_SUFFICIENCY_BANK: DataSufficiencyQuestion[] = [
  {
    id: 'ds_1',
    trapTag: 'Negative & Fractional Trap',
    question: 'Is x² > x ?',
    statementI: 'x > 0',
    statementII: 'x < 1',
    answer: 'BOTH_TOGETHER',
    explanation: "From I alone: x = 0.5 gives x² = 0.25 < x (No), whereas x = 2 gives 4 > 2 (Yes). Not sufficient.\nFrom II alone: x = 0.5 gives No, x = -2 gives 4 > -2 (Yes). Not sufficient.\nTogether (0 < x < 1): x² is ALWAYS strictly less than x, giving a definite 'NO'. BOTH together suffice!",
  },
  {
    id: 'ds_2',
    trapTag: 'Even Power Sign Trap',
    question: 'What is the exact value of real number x ?',
    statementI: 'x² = 25',
    statementII: 'x³ = -125',
    answer: 'STATEMENT_II_ALONE',
    explanation: 'Statement I gives x = +5 or x = -5 (two values, not unique).\nStatement II gives only one real solution: x = -5. Hence II alone is sufficient.',
  },
  {
    id: 'ds_3',
    trapTag: 'Factor Independence',
    question: 'Is integer n divisible by 6?',
    statementI: 'n is divisible by 2.',
    statementII: 'n is divisible by 3.',
    answer: 'BOTH_TOGETHER',
    explanation: 'Statement I alone allows n = 4 (No) or n = 6 (Yes). Statement II alone allows n = 9 (No) or n = 6 (Yes). Because gcd(2, 3) = 1, combining both guarantees n is divisible by 6.',
  },
  {
    id: 'ds_4',
    trapTag: 'Negative Multiplier Flip',
    question: 'Is a > b ?',
    statementI: 'a / c > b / c',
    statementII: 'c > 0',
    answer: 'BOTH_TOGETHER',
    explanation: 'From I alone, c could be negative (c = -1 flips the inequality: -2 > -4 gives 2 < 4). From II alone, we know nothing about a or b. Together, c > 0 lets us multiply safely: a > b.',
  },
  {
    id: 'ds_5',
    trapTag: 'Linear Equation Elimination',
    question: 'What is the value of (3x + 2y)?',
    statementI: '6x + 4y = 28',
    statementII: 'x - y = 3',
    answer: 'STATEMENT_I_ALONE',
    explanation: 'Do not rush to solve for x and y separately! Dividing Statement I by 2 immediately gives 3x + 2y = 14. Statement I alone suffices.',
  },
  {
    id: 'ds_6',
    trapTag: 'Algebraic Parity Identity',
    question: 'Is x an odd integer?',
    statementI: '2x is an even integer.',
    statementII: '3x is an odd integer.',
    answer: 'BOTH_TOGETHER',
    explanation: 'From I alone: 2x is even ⇒ x can be 2 (No) or 3 (Yes).\nFrom II alone: 3x is odd ⇒ x could be 1 (Yes) or 1/3 (Not an integer, No!).\nTogether: Since 3x and 2x are both integers, x = 3x - 2x MUST be an integer; and since 3x is odd, x must be an odd integer!',
  },
  {
    id: 'ds_7',
    trapTag: 'Sign of Product Trap',
    question: 'Is xy > 0 ?',
    statementI: 'x + y > 0',
    statementII: '|x| > |y|',
    answer: 'NEITHER',
    explanation: 'Test boundary values satisfying both I and II:\nCase 1: x = 5, y = 2 ⇒ x+y = 7 > 0, |5| > |2|, and xy = 10 > 0 (Yes).\nCase 2: x = 5, y = -2 ⇒ x+y = 3 > 0, |5| > |-2|, yet xy = -10 < 0 (No). Even together they are insufficient.',
  },
  {
    id: 'ds_8',
    trapTag: 'Absolute Value Equivalence',
    question: 'Is |x - 3| < 2 ?',
    statementI: '1 < x < 5',
    statementII: 'x² - 6x + 5 < 0',
    answer: 'EITHER_ALONE',
    explanation: '|x - 3| < 2 simplifies to 1 < x < 5.\nStatement I states 1 < x < 5 directly.\nStatement II factors as (x - 1)(x - 5) < 0, which ALSO holds iff 1 < x < 5.\nSo either statement alone is sufficient!',
  },
  {
    id: 'ds_9',
    trapTag: 'Zero Solution Trap',
    question: 'What is the value of real number p ?',
    statementI: 'p² = p',
    statementII: 'p > 0',
    answer: 'BOTH_TOGETHER',
    explanation: 'Statement I gives p(p - 1) = 0, so p = 0 or p = 1. Statement II rules out p = 0, leaving uniquely p = 1. Together they suffice.',
  },
  {
    id: 'ds_10',
    trapTag: 'Margin & Revenue Dependency',
    question: 'Did Store A earn a higher total profit in May than in April?',
    statementI: "Store A's revenue rose 20% from April to May.",
    statementII: "Store A's profit margin (% of revenue) stayed constant at +15% in both months.",
    answer: 'BOTH_TOGETHER',
    explanation: 'Revenue alone (I) could accompany rising costs and lower profit. A constant +15% margin alone (II) gives no revenue comparison. Together, 15% of 1.2R > 15% of R (with positive revenue), so May profit is strictly higher.',
  },
];

export function getShuffledDataSufficiencyBank(rng: RngFn = Math.random): DataSufficiencyQuestion[] {
  return shuffled(DATA_SUFFICIENCY_BANK, rng);
}

// =========================================================================
// 3. Visual Scan & Mental Quant Blitz
// =========================================================================

export type SchulteMode = 'SCHULTE_5X5' | 'SCHULTE_6X6' | 'TRAIL_5X5';

export const SCHULTE_MODE_META: Record<SchulteMode, { label: string; gridSize: number }> = {
  SCHULTE_5X5: { label: '5×5 Classic (1 → 25)', gridSize: 5 },
  SCHULTE_6X6: { label: '6×6 Wide (1 → 36)', gridSize: 6 },
  TRAIL_5X5: { label: '5×5 Alternating Trail (1→A→2→B…)', gridSize: 5 },
};

export interface SchulteBoard {
  mode: SchulteMode;
  gridSize: number;
  cells: string[];
  expectedOrder: string[];
}

export function generateSchulteBoard(
  mode: SchulteMode,
  rng: RngFn = Math.random
): SchulteBoard {
  const gridSize = SCHULTE_MODE_META[mode].gridSize;
  const total = gridSize * gridSize;
  const expectedOrder: string[] = [];

  if (mode === 'TRAIL_5X5') {
    let num = 1;
    let charCode = 'A'.charCodeAt(0);
    while (expectedOrder.length < total) {
      expectedOrder.push(String(num++));
      if (expectedOrder.length < total) {
        expectedOrder.push(String.fromCharCode(charCode++));
      }
    }
  } else {
    for (let i = 1; i <= total; i++) {
      expectedOrder.push(String(i));
    }
  }

  return {
    mode,
    gridSize,
    cells: shuffled(expectedOrder, rng),
    expectedOrder,
  };
}

export type QuantQuestionType =
  | 'FRACTION_PERCENT'
  | 'COMPOUND_SHIFT'
  | 'FRACTION_COMPARE'
  | 'FAST_APPROX';

export const QUANT_BADGE_MAP: Record<QuantQuestionType, string> = {
  FRACTION_PERCENT: 'FRACTION → %',
  COMPOUND_SHIFT: 'COMPOUND % SHIFT',
  FRACTION_COMPARE: 'RATIO DUEL',
  FAST_APPROX: 'FAST APPROX',
};

export interface QuantQuestion {
  id: number;
  type: QuantQuestionType;
  prompt: string;
  options: string[];
  correctIndex: number;
  explanation: string;
}

const QUANT_BENCHMARKS = [
  { num: 1, den: 6, pctText: '16.67%' },
  { num: 1, den: 7, pctText: '14.28%' },
  { num: 2, den: 7, pctText: '28.57%' },
  { num: 3, den: 7, pctText: '42.86%' },
  { num: 1, den: 8, pctText: '12.50%' },
  { num: 3, den: 8, pctText: '37.50%' },
  { num: 5, den: 8, pctText: '62.50%' },
  { num: 7, den: 8, pctText: '87.50%' },
  { num: 4, den: 9, pctText: '44.44%' },
  { num: 5, den: 9, pctText: '55.56%' },
  { num: 2, den: 11, pctText: '18.18%' },
  { num: 5, den: 12, pctText: '41.67%' },
  { num: 7, den: 12, pctText: '58.33%' },
  { num: 1, den: 15, pctText: '6.67%' },
];

export function generateQuantQuestion(id: number, rng: RngFn = Math.random): QuantQuestion {
  const kinds: QuantQuestionType[] = [
    'FRACTION_PERCENT',
    'COMPOUND_SHIFT',
    'FRACTION_COMPARE',
    'FAST_APPROX',
  ];
  const kind = kinds[randInt(rng, 0, kinds.length)];

  if (kind === 'FRACTION_PERCENT') {
    const target = QUANT_BENCHMARKS[randInt(rng, 0, QUANT_BENCHMARKS.length)];
    const distractors = shuffled(
      QUANT_BENCHMARKS.filter((b) => b.pctText !== target.pctText).map((b) => b.pctText),
      rng
    ).slice(0, 3);
    const options = shuffled([target.pctText, ...distractors], rng);
    return {
      id,
      type: kind,
      prompt: `Closest percentage equivalent of ${target.num} / ${target.den} ?`,
      options,
      correctIndex: options.indexOf(target.pctText),
      explanation: `1/${target.den} ≈ ${(100 / target.den).toFixed(2)}%, so ${target.num}/${target.den} ≈ ${target.pctText}.`,
    };
  }

  if (kind === 'COMPOUND_SHIFT') {
    const shifts = [-25, -20, -15, -10, 10, 15, 20, 25, 30];
    const a = shifts[randInt(rng, 0, shifts.length)];
    const b = shifts[randInt(rng, 0, shifts.length)];
    const net = a + b + (a * b) / 100;
    const fmt = (v: number): string => {
      const r = Math.round(v * 100) / 100;
      const prefix = r > 0 ? '+' : '';
      return Number.isInteger(r) ? `${prefix}${r}%` : `${prefix}${r.toFixed(2)}%`;
    };
    const correctStr = fmt(net);
    const distractors = Array.from(
      new Set([fmt(a + b), fmt(net + 4), fmt(net - 4), fmt(net + 2.5), fmt(-net)])
    )
      .filter((x) => x !== correctStr)
      .slice(0, 3);
    const options = shuffled([correctStr, ...distractors], rng);
    return {
      id,
      type: kind,
      prompt: `A value changes by ${a > 0 ? `+${a}%` : `${a}%`}, then by ${b > 0 ? `+${b}%` : `${b}%`}. Net % change?`,
      options,
      correctIndex: options.indexOf(correctStr),
      explanation: `Successive formula: a + b + (ab)/100 = ${a} + ${b} + (${a * b})/100 = ${correctStr}.`,
    };
  }

  if (kind === 'FRACTION_COMPARE') {
    let n1 = 7;
    let d1 = 13;
    let n2 = 8;
    let d2 = 15;
    for (let guard = 0; guard < 150; guard++) {
      const cd1 = randInt(rng, 13, 35);
      const cn1 = randInt(rng, 7, cd1 - 1);
      const cd2 = randInt(rng, 13, 35);
      const cn2 = randInt(rng, 7, cd2 - 1);
      const lhs = cn1 * cd2;
      const rhs = cn2 * cd1;
      const diff = Math.abs(cn1 / cd1 - cn2 / cd2);
      if (lhs !== rhs && diff >= 0.012 && diff <= 0.12) {
        n1 = cn1;
        d1 = cd1;
        n2 = cn2;
        d2 = cd2;
        break;
      }
    }
    const f1 = `${n1}/${d1}`;
    const f2 = `${n2}/${d2}`;
    const lhs = n1 * d2;
    const rhs = n2 * d1;
    const larger = lhs > rhs ? f1 : f2;
    const options = [f1, f2, 'Both are equal', 'Cannot be compared'];
    return {
      id,
      type: kind,
      prompt: `Which fraction is strictly LARGER: ${f1} or ${f2} ?`,
      options,
      correctIndex: options.indexOf(larger),
      explanation: `Cross-multiply: ${n1} × ${d2} = ${lhs} vs ${n2} × ${d1} = ${rhs}. So ${larger} is larger.`,
    };
  }

  const pctOptions = [12, 15, 18, 22, 24, 28, 35, 45];
  const pct = pctOptions[randInt(rng, 0, pctOptions.length)];
  const base = randInt(rng, 12, 48) * 10 + randInt(rng, 1, 9);
  const exact = (pct * base) / 100;
  const rounded = Math.round(exact);
  const correctStr = `~${rounded}`;
  const distractors = Array.from(
    new Set([
      `~${Math.max(1, Math.round(rounded * 0.78))}`,
      `~${Math.round(rounded * 1.24)}`,
      `~${rounded + 19}`,
    ])
  )
    .filter((x) => x !== correctStr)
    .slice(0, 3);
  const options = shuffled([correctStr, ...distractors], rng);
  return {
    id,
    type: kind,
    prompt: `Closest integer to ${pct}% of ${base} ?`,
    options,
    correctIndex: options.indexOf(correctStr),
    explanation: `${pct}% of ${base} = ${exact.toFixed(2)}, closest to ${rounded}.`,
  };
}

// =========================================================================
// 4. Calm & Reset: Guided Breathing, 20-20-20 Eye Reset, Untimed Zen Grid
// =========================================================================

export interface BreathingPhaseSample {
  cueLabel: string;
  subCue: string;
  phaseIndex: number;
  secondsRemainingInPhase: number;
  ringScale: number;
  cycleCount: number;
}

function easeInOut(t: number): number {
  const c = Math.max(0, Math.min(1, t));
  return c * c * (3 - 2 * c);
}

export function sampleBreathingState(
  protocol: BreathingProtocol,
  elapsedMillis: number
): BreathingPhaseSample {
  const safeMs = Math.max(0, elapsedMillis);
  if (protocol === 'BOX_4_4_4_4') {
    const cycleMs = 16000;
    const cycle = Math.floor(safeMs / cycleMs);
    const inCycle = safeMs % cycleMs;
    const phase = Math.floor(inCycle / 4000);
    const p = (inCycle % 4000) / 4000;
    const remSec = Math.max(1, Math.min(4, Math.ceil((4000 - (inCycle % 4000)) / 1000)));
    if (phase === 0) {
      return {
        cueLabel: 'Inhale',
        subCue: 'Draw air in smoothly through the nose',
        phaseIndex: 0,
        secondsRemainingInPhase: remSec,
        ringScale: 0.42 + 0.50 * easeInOut(p),
        cycleCount: cycle,
      };
    }
    if (phase === 1) {
      return {
        cueLabel: 'Hold',
        subCue: 'Keep shoulders relaxed and jaw soft',
        phaseIndex: 1,
        secondsRemainingInPhase: remSec,
        ringScale: 0.92,
        cycleCount: cycle,
      };
    }
    if (phase === 2) {
      return {
        cueLabel: 'Exhale',
        subCue: 'Release breath slowly and steadily',
        phaseIndex: 2,
        secondsRemainingInPhase: remSec,
        ringScale: 0.92 - 0.50 * easeInOut(p),
        cycleCount: cycle,
      };
    }
    return {
      cueLabel: 'Hold Empty',
      subCue: 'Rest softly before the next cycle',
      phaseIndex: 3,
      secondsRemainingInPhase: remSec,
      ringScale: 0.42,
      cycleCount: cycle,
    };
  }

  if (protocol === 'CYCLIC_SIGH') {
    const cycleMs = 10000;
    const cycle = Math.floor(safeMs / cycleMs);
    const inCycle = safeMs % cycleMs;
    if (inCycle < 2500) {
      return {
        cueLabel: 'Inhale',
        subCue: 'Draw air in smoothly through the nose',
        phaseIndex: 0,
        secondsRemainingInPhase: Math.max(1, Math.ceil((2500 - inCycle) / 1000)),
        ringScale: 0.40 + 0.36 * easeInOut(inCycle / 2500),
        cycleCount: cycle,
      };
    }
    if (inCycle < 4000) {
      return {
        cueLabel: 'Top-Up Inhale',
        subCue: 'Take one short extra sip of air',
        phaseIndex: 1,
        secondsRemainingInPhase: Math.max(1, Math.ceil((4000 - inCycle) / 1000)),
        ringScale: 0.76 + 0.18 * easeInOut((inCycle - 2500) / 1500),
        cycleCount: cycle,
      };
    }
    return {
      cueLabel: 'Exhale',
      subCue: 'Release breath slowly and steadily',
      phaseIndex: 2,
      secondsRemainingInPhase: Math.max(1, Math.ceil((10000 - inCycle) / 1000)),
      ringScale: 0.94 - 0.54 * easeInOut((inCycle - 4000) / 6000),
      cycleCount: cycle,
    };
  }

  // CALM_4_7_8
  const cycleMs = 19000;
  const cycle = Math.floor(safeMs / cycleMs);
  const inCycle = safeMs % cycleMs;
  if (inCycle < 4000) {
    return {
      cueLabel: 'Inhale',
      subCue: 'Draw air in smoothly through the nose',
      phaseIndex: 0,
      secondsRemainingInPhase: Math.max(1, Math.ceil((4000 - inCycle) / 1000)),
      ringScale: 0.42 + 0.50 * easeInOut(inCycle / 4000),
      cycleCount: cycle,
    };
  }
  if (inCycle < 11000) {
    return {
      cueLabel: 'Hold',
      subCue: 'Keep shoulders relaxed and jaw soft',
      phaseIndex: 1,
      secondsRemainingInPhase: Math.max(1, Math.ceil((11000 - inCycle) / 1000)),
      ringScale: 0.92,
      cycleCount: cycle,
    };
  }
  return {
    cueLabel: 'Exhale',
    subCue: 'Release breath slowly and steadily',
    phaseIndex: 2,
    secondsRemainingInPhase: Math.max(1, Math.ceil((19000 - inCycle) / 1000)),
    ringScale: 0.92 - 0.50 * easeInOut((inCycle - 11000) / 8000),
    cycleCount: cycle,
  };
}

export const SOFT_RESET_STEPS = [
  {
    title: '20-20-20 Far Gaze',
    prompt: 'Look away from your screen toward an object at least 20 feet (6 meters) away. Let your eyes unfocus gently.',
    durationSeconds: 20,
  },
  {
    title: 'Shoulder & Jaw Release',
    prompt: 'Drop your shoulders away from your ears, unclench your back teeth, and let your hands rest open.',
    durationSeconds: 20,
  },
  {
    title: 'Slow Blink & Reset',
    prompt: 'Blink slowly 4–5 times to rehydrate your eyes, take one quiet breath, and return with clear attention.',
    durationSeconds: 20,
  },
];

export interface ZenLogicPuzzle {
  size: 4 | 6;
  boxRows: number;
  boxCols: number;
  givens: number[];
  solution: number[];
}

export function countZenSolutions(
  grid: number[],
  size: number,
  boxRows: number,
  boxCols: number,
  limit: number = 2
): number {
  const work = [...grid];
  let solutions = 0;

  const findNextEmpty = (): [number, number] | null => {
    let bestIdx = -1;
    let bestMask = 0;
    let bestCount = Number.MAX_SAFE_INTEGER;

    for (let idx = 0; idx < work.length; idx++) {
      if (work[idx] !== 0) continue;
      const r = Math.floor(idx / size);
      const c = idx % size;
      const br = Math.floor(r / boxRows) * boxRows;
      const bc = Math.floor(c / boxCols) * boxCols;

      let usedMask = 0;
      for (let k = 0; k < size; k++) {
        const rv = work[r * size + k];
        if (rv !== 0) usedMask |= 1 << rv;
        const cv = work[k * size + c];
        if (cv !== 0) usedMask |= 1 << cv;
      }
      for (let dr = 0; dr < boxRows; dr++) {
        for (let dc = 0; dc < boxCols; dc++) {
          const bv = work[(br + dr) * size + (bc + dc)];
          if (bv !== 0) usedMask |= 1 << bv;
        }
      }

      let candMask = 0;
      let candCount = 0;
      for (let v = 1; v <= size; v++) {
        if (((usedMask >> v) & 1) === 0) {
          candMask |= 1 << v;
          candCount++;
        }
      }
      if (candCount === 0) return [idx, 0];
      if (candCount < bestCount) {
        bestCount = candCount;
        bestIdx = idx;
        bestMask = candMask;
        if (candCount === 1) break;
      }
    }
    return bestIdx === -1 ? null : [bestIdx, bestMask];
  };

  const dfs = () => {
    if (solutions >= limit) return;
    const next = findNextEmpty();
    if (!next) {
      solutions++;
      return;
    }
    const [idx, candMask] = next;
    if (candMask === 0) return;
    for (let v = 1; v <= size; v++) {
      if (((candMask >> v) & 1) !== 0) {
        work[idx] = v;
        dfs();
        work[idx] = 0;
        if (solutions >= limit) return;
      }
    }
  };

  dfs();
  return solutions;
}

export function generateZenLogicPuzzle(size: 4 | 6 = 4, rng: RngFn = Math.random): ZenLogicPuzzle {
  const safeSize: 4 | 6 = size === 6 ? 6 : 4;
  const boxRows = 2;
  const boxCols = safeSize === 6 ? 3 : 2;

  const base = new Array<number>(safeSize * safeSize);
  for (let r = 0; r < safeSize; r++) {
    for (let c = 0; c < safeSize; c++) {
      base[r * safeSize + c] = ((r * boxCols + Math.floor(r / boxRows) + c) % safeSize) + 1;
    }
  }
  const symbols = shuffled(Array.from({ length: safeSize }, (_, i) => i + 1), rng);
  for (let i = 0; i < base.length; i++) {
    base[i] = symbols[base[i] - 1];
  }

  const numBands = safeSize / boxRows;
  const bandOrder = shuffled(Array.from({ length: numBands }, (_, i) => i), rng);
  const rowMap = bandOrder.flatMap((band) =>
    shuffled(Array.from({ length: boxRows }, (_, i) => band * boxRows + i), rng)
  );

  const numStacks = safeSize / boxCols;
  const stackOrder = shuffled(Array.from({ length: numStacks }, (_, i) => i), rng);
  const colMap = stackOrder.flatMap((stack) =>
    shuffled(Array.from({ length: boxCols }, (_, i) => stack * boxCols + i), rng)
  );

  const solution = new Array<number>(safeSize * safeSize);
  for (let r = 0; r < safeSize; r++) {
    for (let c = 0; c < safeSize; c++) {
      solution[r * safeSize + c] = base[rowMap[r] * safeSize + colMap[c]];
    }
  }

  const givens = [...solution];
  const targetBlanks = safeSize === 4 ? 9 : 18;
  const indices = shuffled(Array.from({ length: safeSize * safeSize }, (_, i) => i), rng);
  let removed = 0;

  for (const idx of indices) {
    if (removed >= targetBlanks) break;
    const backup = givens[idx];
    givens[idx] = 0;
    if (countZenSolutions(givens, safeSize, boxRows, boxCols, 2) === 1) {
      removed++;
    } else {
      givens[idx] = backup;
    }
  }

  return { size: safeSize, boxRows, boxCols, givens, solution };
}

export function isZenGridSolvedCorrectly(
  board: number[],
  size: number,
  boxRows: number,
  boxCols: number
): boolean {
  if (board.length !== size * size || board.some((v) => v < 1 || v > size)) return false;
  const fullMask = (1 << (size + 1)) - 2;
  for (let i = 0; i < size; i++) {
    let rowMask = 0;
    let colMask = 0;
    for (let j = 0; j < size; j++) {
      rowMask |= 1 << board[i * size + j];
      colMask |= 1 << board[j * size + i];
    }
    if (rowMask !== fullMask || colMask !== fullMask) return false;
  }
  for (let br = 0; br < size; br += boxRows) {
    for (let bc = 0; bc < size; bc += boxCols) {
      let boxMask = 0;
      for (let dr = 0; dr < boxRows; dr++) {
        for (let dc = 0; dc < boxCols; dc++) {
          boxMask |= 1 << board[(br + dr) * size + (bc + dc)];
        }
      }
      if (boxMask !== fullMask) return false;
    }
  }
  return true;
}

// =========================================================================
// 5. Vault Merge Utility for BrainLabStats
// =========================================================================

function minPositive(a: number, b: number): number {
  if (a <= 0) return b;
  if (b <= 0) return a;
  return Math.min(a, b);
}

export function mergeBrainLabStats(
  existing: BrainLabStats = EMPTY_BRAIN_LAB_STATS,
  incoming: BrainLabStats = EMPTY_BRAIN_LAB_STATS
): BrainLabStats {
  return {
    bestNBackTier: Math.max(existing.bestNBackTier || 1, incoming.bestNBackTier || 1),
    bestNBackAccuracy: Math.max(existing.bestNBackAccuracy || 0, incoming.bestNBackAccuracy || 0),
    bestCorsiForwardSpan: Math.max(existing.bestCorsiForwardSpan || 0, incoming.bestCorsiForwardSpan || 0),
    bestCorsiReverseSpan: Math.max(existing.bestCorsiReverseSpan || 0, incoming.bestCorsiReverseSpan || 0),
    bestStroopAccuracy: Math.max(existing.bestStroopAccuracy || 0, incoming.bestStroopAccuracy || 0),
    bestStroopReactionMs: minPositive(existing.bestStroopReactionMs || 0, incoming.bestStroopReactionMs || 0),
    syllogismSolved: Math.max(existing.syllogismSolved || 0, incoming.syllogismSolved || 0),
    syllogismCorrect: Math.max(existing.syllogismCorrect || 0, incoming.syllogismCorrect || 0),
    reasoningStreak:
      (incoming.lastUpdatedAtMillis || 0) > (existing.lastUpdatedAtMillis || 0)
        ? incoming.reasoningStreak || 0
        : existing.reasoningStreak || 0,
    bestReasoningStreak: Math.max(existing.bestReasoningStreak || 0, incoming.bestReasoningStreak || 0),
    constraintSolved: Math.max(existing.constraintSolved || 0, incoming.constraintSolved || 0),
    argumentSolved: Math.max(existing.argumentSolved || 0, incoming.argumentSolved || 0),
    dataSufficiencySolved: Math.max(existing.dataSufficiencySolved || 0, incoming.dataSufficiencySolved || 0),
    bestSchulte5x5Ms: minPositive(existing.bestSchulte5x5Ms || 0, incoming.bestSchulte5x5Ms || 0),
    bestSchulte6x6Ms: minPositive(existing.bestSchulte6x6Ms || 0, incoming.bestSchulte6x6Ms || 0),
    bestTrail5x5Ms: minPositive(existing.bestTrail5x5Ms || 0, incoming.bestTrail5x5Ms || 0),
    bestQuantBlitzScore: Math.max(existing.bestQuantBlitzScore || 0, incoming.bestQuantBlitzScore || 0),
    calmSessionsCompleted: Math.max(existing.calmSessionsCompleted || 0, incoming.calmSessionsCompleted || 0),
    zenGridsCompleted: Math.max(existing.zenGridsCompleted || 0, incoming.zenGridsCompleted || 0),
    totalCognitiveSeconds: Math.max(existing.totalCognitiveSeconds || 0, incoming.totalCognitiveSeconds || 0),
    totalCalmSeconds: Math.max(existing.totalCalmSeconds || 0, incoming.totalCalmSeconds || 0),
    lastUpdatedAtMillis: Math.max(existing.lastUpdatedAtMillis || 0, incoming.lastUpdatedAtMillis || 0),
  };
}
