import type { ActiveExamSession, ExamCountdownTarget, QuestionPaper } from '../types/exam';
import type { FlashcardSrsState, HeadwordQueueItem, StudyNoteRecord, VocabularyDeck } from '../types/phase4';
import type {
  DailyStudyAnalytics,
  StudyDueSuggestion,
  StudyTodoItem,
  TodoFilter,
} from '../types/phase5';
import { SpacedRepetitionEngine } from './spacedRepetitionEngine';
import { StudyNoteParser } from './studyNoteParser';

const MS_PER_DAY = 86_400_000;
const MS_PER_HOUR = 3_600_000;
const MS_PER_MINUTE = 60_000;
const TIME_HM_REGEX = /^([01]\d|2[0-3]):([0-5]\d)$/;

export const StudyDateUtils = {
  formatDateString(millis: number): string {
    const d = new Date(millis);
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  },

  todayDateStr(nowMs: number = Date.now()): string {
    return this.formatDateString(nowMs);
  },

  isValidTimeHm(timeStr: string): boolean {
    return TIME_HM_REGEX.test((timeStr || '').trim());
  },

  normalizeTimeHmOrBlank(timeStr: string): string {
    const trimmed = (timeStr || '').trim();
    return TIME_HM_REGEX.test(trimmed) ? trimmed : '';
  },

  getLocalTimezoneLabel(nowMillis: number = Date.now()): string {
    try {
      const parts = new Intl.DateTimeFormat(undefined, { timeZoneName: 'short' }).formatToParts(
        new Date(nowMillis)
      );
      const tzPart = parts.find((p) => p.type === 'timeZoneName')?.value?.trim();
      if (tzPart) return tzPart;
    } catch {
      // Fallback handled below
    }
    const offsetMin = -new Date(nowMillis).getTimezoneOffset();
    if (offsetMin === 0) return 'UTC';
    const sign = offsetMin > 0 ? '+' : '-';
    const absMin = Math.abs(offsetMin);
    const hh = String(Math.floor(absMin / 60)).padStart(2, '0');
    const mm = String(absMin % 60).padStart(2, '0');
    return `GMT${sign}${hh}:${mm}`;
  },

  startOfDayMillis(millis: number): number {
    const d = new Date(millis);
    d.setHours(0, 0, 0, 0);
    return d.getTime();
  },

  parseDateToStartOfDayMillis(dateStr: string): number | null {
    return this.parseDateTimeMillis(dateStr, '00:00', false);
  },

  parseDateTimeMillis(dateStr: string, timeStr: string = '00:00', useGmt: boolean = false): number | null {
    const trimmed = (dateStr || '').trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) return null;
    const [y, m, day] = trimmed.split('-').map((n) => Number.parseInt(n, 10));
    if (!y || m < 1 || m > 12 || day < 1 || day > 31) return null;

    const cleanTime = (timeStr || '').trim() || '00:00';
    const match = TIME_HM_REGEX.exec(cleanTime);
    if (!match) return null;
    const hour = Number.parseInt(match[1], 10);
    const minute = Number.parseInt(match[2], 10);

    if (useGmt) {
      const ms = Date.UTC(y, m - 1, day, hour, minute, 0, 0);
      const check = new Date(ms);
      if (
        check.getUTCFullYear() !== y ||
        check.getUTCMonth() !== m - 1 ||
        check.getUTCDate() !== day
      ) {
        return null;
      }
      return ms;
    }

    const d = new Date(y, m - 1, day, hour, minute, 0, 0);
    if (d.getFullYear() !== y || d.getMonth() !== m - 1 || d.getDate() !== day) {
      return null;
    }
    return d.getTime();
  },

  computeDaysRemaining(targetDateStr: string, nowMillis: number = Date.now()): number | null {
    const targetStart = this.parseDateToStartOfDayMillis(targetDateStr);
    if (targetStart === null) return null;
    const todayStart = this.startOfDayMillis(nowMillis);
    const diffMs = targetStart - todayStart;
    if (diffMs >= 0) {
      return Math.floor((diffMs + MS_PER_DAY / 2) / MS_PER_DAY);
    }
    return -Math.floor((-diffMs + MS_PER_DAY / 2) / MS_PER_DAY);
  },

  addDaysToDateString(baseMillis: number, daysToAdd: number): string {
    const d = new Date(this.startOfDayMillis(baseMillis));
    d.setDate(d.getDate() + daysToAdd);
    return this.formatDateString(d.getTime());
  },

  formatHumanDate(dateStr: string): string {
    const ms = this.parseDateToStartOfDayMillis(dateStr);
    if (ms === null) return dateStr;
    return new Date(ms).toLocaleDateString(undefined, {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  },

  formatHumanDateTimeLocal(epochMillis: number): string {
    const d = new Date(epochMillis);
    const datePart = d.toLocaleDateString(undefined, {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    const tzLabel = this.getLocalTimezoneLabel(epochMillis);
    return `${datePart} • ${hh}:${mm} ${tzLabel}`;
  },

  hasExplicitCountdownTime(cd: ExamCountdownTarget): boolean {
    return (
      this.isValidTimeHm(cd.targetTime || '') ||
      (typeof cd.targetUtcMillis === 'number' && Number.isFinite(cd.targetUtcMillis) && cd.targetUtcMillis > 0)
    );
  },

  resolveCountdownTargetMillis(cd: ExamCountdownTarget): number | null {
    if (typeof cd.targetUtcMillis === 'number' && Number.isFinite(cd.targetUtcMillis) && cd.targetUtcMillis > 0) {
      return cd.targetUtcMillis;
    }
    if (this.isValidTimeHm(cd.targetTime || '')) {
      return this.parseDateTimeMillis(cd.targetDateIso, cd.targetTime!, cd.timezoneMode === 'GMT');
    }
    return this.parseDateToStartOfDayMillis(cd.targetDateIso);
  },

  computeCountdownRemainingMillis(cd: ExamCountdownTarget, nowMillis: number = Date.now()): number | null {
    const targetMs = this.resolveCountdownTargetMillis(cd);
    if (targetMs === null) return null;
    return targetMs - nowMillis;
  },

  computeCountdownDaysRemaining(cd: ExamCountdownTarget, nowMillis: number = Date.now()): number | null {
    if (this.hasExplicitCountdownTime(cd)) {
      const diff = this.computeCountdownRemainingMillis(cd, nowMillis);
      if (diff === null) return null;
      return diff >= 0 ? Math.floor(diff / MS_PER_DAY) : -Math.floor(-diff / MS_PER_DAY);
    }
    return this.computeDaysRemaining(cd.targetDateIso, nowMillis);
  },

  formatRemainingDuration(diffMillis: number, compact: boolean = false): string {
    if (diffMillis === 0) return 'Now!';
    if (diffMillis > 0) {
      const days = Math.floor(diffMillis / MS_PER_DAY);
      const hours = Math.floor((diffMillis % MS_PER_DAY) / MS_PER_HOUR);
      const mins = Math.floor((diffMillis % MS_PER_HOUR) / MS_PER_MINUTE);
      const secs = Math.floor((diffMillis % MS_PER_MINUTE) / 1000);
      if (days > 0) return compact ? `${days}d ${hours}h left` : `${days}d ${hours}h ${mins}m left`;
      if (hours > 0) return compact ? `${hours}h ${mins}m left` : `${hours}h ${mins}m ${secs}s left`;
      if (mins > 0) return compact ? `${mins}m left` : `${mins}m ${secs}s left`;
      return `${Math.max(1, secs)}s left`;
    }
    const absMs = -diffMillis;
    const days = Math.floor(absMs / MS_PER_DAY);
    const hours = Math.floor((absMs % MS_PER_DAY) / MS_PER_HOUR);
    const mins = Math.floor((absMs % MS_PER_HOUR) / MS_PER_MINUTE);
    if (days > 0) return compact ? `${days}d ago` : `${days}d ${hours}h ago`;
    if (hours > 0) return `${hours}h ${mins}m ago`;
    if (mins > 0) return `${mins}m ago`;
    return 'Just passed';
  },

  formatCountdownShort(cd: ExamCountdownTarget, nowMillis: number = Date.now()): string {
    if (this.hasExplicitCountdownTime(cd)) {
      const diff = this.computeCountdownRemainingMillis(cd, nowMillis);
      if (diff === null) return cd.targetDateIso;
      return this.formatRemainingDuration(diff, true);
    }
    const days = this.computeDaysRemaining(cd.targetDateIso, nowMillis);
    if (days === null) return cd.targetDateIso;
    if (days > 0) return `${days}d left`;
    if (days === 0) return 'Today!';
    return `${-days}d ago`;
  },

  formatCountdownDetailed(cd: ExamCountdownTarget, nowMillis: number = Date.now()): string {
    if (this.hasExplicitCountdownTime(cd)) {
      const diff = this.computeCountdownRemainingMillis(cd, nowMillis);
      if (diff === null) return cd.targetDateIso;
      return this.formatRemainingDuration(diff, false);
    }
    return this.formatCountdownShort(cd, nowMillis);
  },

  formatCountdownSchedule(cd: ExamCountdownTarget): string {
    if (!this.hasExplicitCountdownTime(cd)) {
      return this.formatHumanDate(cd.targetDateIso);
    }
    const ms = this.resolveCountdownTargetMillis(cd);
    if (ms === null) return this.formatHumanDate(cd.targetDateIso);
    const localStr = this.formatHumanDateTimeLocal(ms);
    const localTz = this.getLocalTimezoneLabel(ms).toUpperCase();
    if (cd.timezoneMode === 'GMT' && localTz !== 'GMT' && localTz !== 'UTC') {
      return `${localStr} (${cd.targetTime || '00:00'} GMT)`;
    }
    return localStr;
  },
};

export function computeRemainingSecondsFromDeadline(deadlineAtMs: number, nowMs: number = Date.now()): number {
  const remainingMs = Math.max(0, deadlineAtMs - nowMs);
  return remainingMs === 0 ? 0 : Math.ceil(remainingMs / 1000);
}

export function formatMmSs(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const hrs = Math.floor(safe / 3600);
  const mins = Math.floor((safe % 3600) / 60);
  const secs = safe % 60;
  if (hrs > 0) {
    return `${String(hrs).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

export function buildDueSuggestions(params: {
  studyNotes: StudyNoteRecord[];
  vocabDecks: VocabularyDeck[];
  vocabSrs: Record<string, Record<string, FlashcardSrsState>>;
  headwordInbox: HeadwordQueueItem[];
  papers: QuestionPaper[];
  activeSessions: Record<string, ActiveExamSession>;
  nowMs?: number;
}): StudyDueSuggestion[] {
  const nowMs = params.nowMs ?? Date.now();
  const suggestions: StudyDueSuggestion[] = [];

  // 1. Paused / In-progress tests
  for (const paper of params.papers) {
    const sess = params.activeSessions[paper.metadata.id];
    if (!sess) continue;
    const answeredCount = Object.keys(sess.answers || {}).length;
    const total = paper.questions.length;
    suggestions.push({
      id: `paused_${paper.metadata.id}`,
      category: 'PAUSED_TEST',
      title: `Resume: ${paper.metadata.title}`,
      subtitle: `${answeredCount}/${total} answered • ${Math.max(1, Math.round((sess.timeLeftSeconds || 0) / 60))}m remaining`,
      count: Math.max(1, total - answeredCount),
      paperId: paper.metadata.id,
    });
  }

  // 2. SM-2 Flashcards due across Study Notes
  for (const note of params.studyNotes) {
    const blocks = StudyNoteParser.parseBlocks(note.rawContent, note.formatRule);
    const reviewables = blocks.filter(
      (b) => b.kind === 'one_liner' || b.kind === 'multi_liner_card'
    );
    if (reviewables.length === 0) continue;

    const srsMap: Record<string, FlashcardSrsState | undefined> = {};
    for (const [k, v] of Object.entries(note.srsStates || {})) {
      srsMap[k] = v;
    }
    const stats = SpacedRepetitionEngine.computeDeckStats(
      reviewables,
      (b) => b.blockIndex,
      srsMap,
      nowMs
    );
    const hasStarted = Object.keys(note.srsStates || {}).length > 0;
    if (stats.dueCount > 0 || (stats.newCount > 0 && !hasStarted)) {
      const count = stats.dueCount > 0 ? stats.dueCount : Math.min(stats.newCount, 10);
      const label =
        stats.dueCount > 0
          ? `${stats.dueCount} due for review`
          : `${stats.newCount} new cards unstarted`;
      suggestions.push({
        id: `fc_${note.id}`,
        category: 'FLASHCARDS',
        title: note.title,
        subtitle: `${note.subject || 'General'} • ${label}`,
        count,
        noteId: note.id,
      });
    }
  }

  // 3. Vocabulary SRS due per language
  const decksByLang = new Map<string, VocabularyDeck[]>();
  for (const deck of params.vocabDecks) {
    const lang = deck.language || 'English';
    const arr = decksByLang.get(lang) || [];
    arr.push(deck);
    decksByLang.set(lang, arr);
  }
  for (const [lang, decks] of decksByLang.entries()) {
    let dueCount = 0;
    let totalEntries = 0;
    for (const deck of decks) {
      totalEntries += deck.entries.length;
      const srsForDeck = params.vocabSrs[deck.id] || {};
      const stats = SpacedRepetitionEngine.computeDeckStats(
        deck.entries,
        (e) => e.id,
        srsForDeck,
        nowMs
      );
      dueCount += stats.dueCount;
    }
    if (dueCount > 0) {
      suggestions.push({
        id: `vocab_${lang}`,
        category: 'VOCABULARY',
        title: `${lang} Vocabulary Recall`,
        subtitle: `${dueCount} words due across ${decks.length} deck(s) (${totalEntries} total)`,
        count: dueCount,
        language: lang,
      });
    }
  }

  // 4. Queued Headwords
  if (params.headwordInbox.length > 0) {
    suggestions.push({
      id: 'headword_inbox_due',
      category: 'HEADWORD_INBOX',
      title: 'Headword Inbox Enrichment',
      subtitle: `${params.headwordInbox.length} captured headword(s) awaiting enrichment`,
      count: params.headwordInbox.length,
    });
  }

  return suggestions.sort((a, b) => b.count - a.count);
}

export function filterAndSortTodos(
  todos: StudyTodoItem[],
  filter: TodoFilter,
  todayStr: string = StudyDateUtils.todayDateStr()
): StudyTodoItem[] {
  const priorityRank: Record<StudyTodoItem['priority'], number> = {
    HIGH: 0,
    MEDIUM: 1,
    LOW: 2,
  };

  const filtered = todos.filter((t) => {
    if (filter === 'ALL') return !t.isCompleted;
    if (filter === 'TODAY') return !t.isCompleted && (!t.dueDateStr || t.dueDateStr <= todayStr);
    if (filter === 'HIGH_PRIORITY') return !t.isCompleted && t.priority === 'HIGH';
    return t.isCompleted;
  });

  return [...filtered].sort((a, b) => {
    if (a.isCompleted !== b.isCompleted) return a.isCompleted ? 1 : -1;
    const pDiff = priorityRank[a.priority] - priorityRank[b.priority];
    if (pDiff !== 0) return pDiff;
    if (a.dueDateStr && b.dueDateStr && a.dueDateStr !== b.dueDateStr) {
      return a.dueDateStr.localeCompare(b.dueDateStr);
    }
    if (a.dueDateStr && !b.dueDateStr) return -1;
    if (!a.dueDateStr && b.dueDateStr) return 1;
    return b.createdAt - a.createdAt;
  });
}

export interface AnalyticsSummary {
  today: DailyStudyAnalytics;
  sevenDayMinutes: number;
  streakDays: number;
  totalTestsCompleted: number;
  totalQuestionsAnswered: number;
  totalFlashcardsReviewed: number;
  totalVocabReviewed: number;
  overallAccuracyPercent: number;
}

export function computeAnalyticsSummary(
  daysMap: Record<string, DailyStudyAnalytics>,
  nowMs: number = Date.now()
): AnalyticsSummary {
  const todayStr = StudyDateUtils.todayDateStr(nowMs);
  const emptyDay: DailyStudyAnalytics = {
    dateStr: todayStr,
    testsCompleted: 0,
    questionsAnswered: 0,
    correctAnswers: 0,
    studyTimeSeconds: 0,
    flashcardsReviewed: 0,
    vocabWordsReviewed: 0,
  };
  const today = daysMap[todayStr] || emptyDay;

  let sevenDaySeconds = 0;
  for (let i = 0; i < 7; i++) {
    const key = StudyDateUtils.addDaysToDateString(nowMs, -i);
    sevenDaySeconds += daysMap[key]?.studyTimeSeconds || 0;
  }

  // Consecutive streak: starts today if any activity, else checks starting yesterday
  let streakDays = 0;
  const hasActivity = (d?: DailyStudyAnalytics): boolean =>
    Boolean(
      d &&
        (d.studyTimeSeconds >= 60 ||
          d.testsCompleted > 0 ||
          d.flashcardsReviewed > 0 ||
          d.vocabWordsReviewed > 0)
    );
  const startOffset = hasActivity(daysMap[todayStr]) ? 0 : 1;
  for (let i = startOffset; i < 365; i++) {
    const key = StudyDateUtils.addDaysToDateString(nowMs, -i);
    if (hasActivity(daysMap[key])) {
      streakDays++;
    } else {
      break;
    }
  }

  let totalTestsCompleted = 0;
  let totalQuestionsAnswered = 0;
  let totalCorrectAnswers = 0;
  let totalFlashcardsReviewed = 0;
  let totalVocabReviewed = 0;

  for (const day of Object.values(daysMap)) {
    totalTestsCompleted += day.testsCompleted || 0;
    totalQuestionsAnswered += day.questionsAnswered || 0;
    totalCorrectAnswers += day.correctAnswers || 0;
    totalFlashcardsReviewed += day.flashcardsReviewed || 0;
    totalVocabReviewed += day.vocabWordsReviewed || 0;
  }

  const overallAccuracyPercent =
    totalQuestionsAnswered > 0
      ? Math.min(100, Math.round((totalCorrectAnswers / totalQuestionsAnswered) * 100))
      : 0;

  return {
    today,
    sevenDayMinutes: Math.round(sevenDaySeconds / 60),
    streakDays,
    totalTestsCompleted,
    totalQuestionsAnswered,
    totalFlashcardsReviewed,
    totalVocabReviewed,
    overallAccuracyPercent,
  };
}
