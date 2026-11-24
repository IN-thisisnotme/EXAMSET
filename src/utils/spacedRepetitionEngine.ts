import type {
  DeckSrsStats,
  FlashcardSrsState,
  FlashcardSrsStatus,
  SrsRating,
} from '../types/phase4';

export const DAY_MILLIS = 86_400_000;
export const AGAIN_DELAY_MILLIS = 10 * 60_000;
export const MIN_EASE_FACTOR = 1.3;
export const DEFAULT_EASE_FACTOR = 2.5;
export const MAX_EASE_FACTOR = 3.0;
export const MAX_INTERVAL_DAYS = 365;
export const MASTERED_MIN_INTERVAL_DAYS = 7;

function clamp(val: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, val));
}

export const SpacedRepetitionEngine = {
  computeNextState(
    current: FlashcardSrsState | undefined | null,
    blockIndex: number,
    rating: SrsRating,
    nowMillis: number = Date.now()
  ): FlashcardSrsState {
    const oldEase = clamp(
      current?.easeFactor ?? DEFAULT_EASE_FACTOR,
      MIN_EASE_FACTOR,
      MAX_EASE_FACTOR
    );
    const oldReps = Math.max(0, current?.repetitions ?? 0);
    const oldInterval = Math.max(0, current?.intervalDays ?? 0);
    const oldLapses = current?.lapses ?? 0;
    const oldReviews = current?.reviewCount ?? 0;
    const oldCorrect = current?.correctCount ?? 0;

    let newReps = oldReps;
    let newIntervalDays = oldInterval;
    let newEase = oldEase;
    let newDueAtMillis = nowMillis;
    let newLapses = oldLapses;

    if (rating === 'AGAIN') {
      newReps = 0;
      newIntervalDays = 0;
      newEase = Math.max(MIN_EASE_FACTOR, oldEase - 0.2);
      newDueAtMillis = nowMillis + AGAIN_DELAY_MILLIS;
      newLapses = oldLapses + 1;
    } else if (rating === 'HARD') {
      newReps = oldReps + 1;
      if (oldReps === 0) newIntervalDays = 1;
      else if (oldReps === 1) newIntervalDays = 3;
      else {
        newIntervalDays = clamp(
          Math.round(oldInterval * 1.2),
          oldInterval + 1,
          MAX_INTERVAL_DAYS
        );
      }
      newEase = Math.max(MIN_EASE_FACTOR, oldEase - 0.15);
      newDueAtMillis = nowMillis + newIntervalDays * DAY_MILLIS;
    } else if (rating === 'GOOD') {
      newReps = oldReps + 1;
      if (oldReps === 0) newIntervalDays = 1;
      else if (oldReps === 1) newIntervalDays = 6;
      else {
        newIntervalDays = clamp(
          Math.round(oldInterval * oldEase),
          oldInterval + 1,
          MAX_INTERVAL_DAYS
        );
      }
      newEase = oldEase;
      newDueAtMillis = nowMillis + newIntervalDays * DAY_MILLIS;
    } else {
      // EASY
      newReps = oldReps + 1;
      if (oldReps === 0) newIntervalDays = 4;
      else if (oldReps === 1) newIntervalDays = 8;
      else {
        newIntervalDays = clamp(
          Math.round(oldInterval * oldEase * 1.3),
          oldInterval + 2,
          MAX_INTERVAL_DAYS
        );
      }
      newEase = Math.min(MAX_EASE_FACTOR, oldEase + 0.15);
      newDueAtMillis = nowMillis + newIntervalDays * DAY_MILLIS;
    }

    return {
      blockIndex,
      repetitions: newReps,
      easeFactor: Math.round(newEase * 100) / 100,
      intervalDays: newIntervalDays,
      dueAtMillis: newDueAtMillis,
      lastReviewedAtMillis: nowMillis,
      lapses: newLapses,
      lastRating: rating,
      reviewCount: oldReviews + 1,
      correctCount: oldCorrect + (rating === 'AGAIN' ? 0 : 1),
    };
  },

  formatInterval(intervalDays: number, rating?: SrsRating): string {
    if (rating === 'AGAIN' || intervalDays <= 0) return '<10m';
    if (intervalDays < 14) return `${intervalDays}d`;
    if (intervalDays < 30) return `${Math.max(2, Math.round(intervalDays / 7))}w`;
    if (intervalDays < 365) return `${Math.max(1, Math.round(intervalDays / 30))}mo`;
    return '1y';
  },

  previewNextIntervalLabels(
    current: FlashcardSrsState | undefined | null,
    nowMillis: number = Date.now()
  ): Record<SrsRating, string> {
    const idx = current?.blockIndex ?? 0;
    const ratings: SrsRating[] = ['AGAIN', 'HARD', 'GOOD', 'EASY'];
    const out = {} as Record<SrsRating, string>;
    for (const r of ratings) {
      const sim = this.computeNextState(current, idx, r, nowMillis);
      out[r] = this.formatInterval(sim.intervalDays, r);
    }
    return out;
  },

  statusOf(
    state: FlashcardSrsState | undefined | null,
    nowMillis: number = Date.now()
  ): FlashcardSrsStatus {
    if (!state || state.reviewCount === 0) return 'NEW';
    if (state.dueAtMillis <= nowMillis) return 'DUE';
    if (state.intervalDays >= MASTERED_MIN_INTERVAL_DAYS && state.repetitions >= 2) {
      return 'MASTERED';
    }
    return 'LEARNING';
  },

  isMasteredBySrs(state: FlashcardSrsState | undefined | null): boolean {
    return (
      Boolean(state) &&
      (state?.intervalDays ?? 0) >= MASTERED_MIN_INTERVAL_DAYS &&
      (state?.repetitions ?? 0) >= 2
    );
  },

  buildStudyQueue<T>(
    items: T[],
    getKey: (item: T) => number | string,
    srsMap: Record<string, FlashcardSrsState | undefined>,
    options: { cramAll?: boolean; prioritizeDue?: boolean; shuffleSeed?: number; nowMillis?: number } = {}
  ): T[] {
    if (items.length === 0) return [];
    const now = options.nowMillis ?? Date.now();
    const prioritizeDue = options.prioritizeDue ?? true;
    const dueList: T[] = [];
    const newList: T[] = [];
    const aheadList: T[] = [];

    for (const item of items) {
      const st = srsMap[String(getKey(item))];
      const status = this.statusOf(st, now);
      if (status === 'DUE') dueList.push(item);
      else if (status === 'NEW') newList.push(item);
      else aheadList.push(item);
    }

    const sortedDue = prioritizeDue
      ? [...dueList].sort(
          (a, b) =>
            (srsMap[String(getKey(a))]?.dueAtMillis ?? 0) -
            (srsMap[String(getKey(b))]?.dueAtMillis ?? 0)
        )
      : dueList;

    let baseQueue: T[];
    if (options.cramAll) {
      if (prioritizeDue) {
        const sortedAhead = [...aheadList].sort(
          (a, b) =>
            (srsMap[String(getKey(a))]?.dueAtMillis ?? Number.MAX_SAFE_INTEGER) -
            (srsMap[String(getKey(b))]?.dueAtMillis ?? Number.MAX_SAFE_INTEGER)
        );
        baseQueue = [...sortedDue, ...newList, ...sortedAhead];
      } else {
        baseQueue = [...items];
      }
    } else {
      baseQueue = [...sortedDue, ...newList];
    }

    if (options.shuffleSeed && baseQueue.length > 1) {
      const arr = [...baseQueue];
      let seed = Math.abs(options.shuffleSeed) || 12345;
      for (let i = arr.length - 1; i > 0; i--) {
        seed = (seed * 1664525 + 1013904223) % 4294967296;
        const j = seed % (i + 1);
        const tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
      }
      return arr;
    }

    return baseQueue;
  },

  computeDeckStats<T>(
    items: T[],
    getKey: (item: T) => number | string,
    srsMap: Record<string, FlashcardSrsState | undefined>,
    nowMillis: number = Date.now()
  ): DeckSrsStats {
    if (items.length === 0) {
      return {
        totalCount: 0,
        dueCount: 0,
        newCount: 0,
        learningCount: 0,
        masteredCount: 0,
        retentionPercent: 0,
      };
    }

    let dueCount = 0;
    let newCount = 0;
    let learningCount = 0;
    let masteredCount = 0;
    let totalReviews = 0;
    let totalCorrect = 0;

    for (const item of items) {
      const st = srsMap[String(getKey(item))];
      const status = this.statusOf(st, nowMillis);
      if (status === 'DUE') dueCount++;
      else if (status === 'NEW') newCount++;
      else if (status === 'LEARNING') learningCount++;
      else masteredCount++;

      if (st) {
        totalReviews += st.reviewCount;
        totalCorrect += st.correctCount;
      }
    }

    const retentionPercent =
      totalReviews > 0 ? clamp(Math.round((totalCorrect / totalReviews) * 100), 0, 100) : 0;

    return {
      totalCount: items.length,
      dueCount,
      newCount,
      learningCount,
      masteredCount,
      retentionPercent,
    };
  },

  reinsertCardSoon<T>(queue: T[], currentIndex: number, card: T): T[] {
    if (queue.length === 0) return [card];
    const next = [...queue];
    const insertPos = Math.min(next.length, currentIndex + 3);
    next.splice(insertPos, 0, card);
    return next;
  },
};
