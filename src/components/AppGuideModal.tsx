import React, { useMemo, useState } from 'react';
import {
  ArrowRight,
  ChevronDown,
  ChevronUp,
  HelpCircle,
  Lightbulb,
  Search,
  X,
} from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';
import type {
  AppGuideCategory,
  AppGuideEntry,
  GuideDestination,
} from '../types/phase5';

interface AppGuideModalProps {
  isOpen: boolean;
  onClose: () => void;
  onNavigateDestination: (destination: GuideDestination) => void;
}

const GUIDE_CATEGORIES: { id: AppGuideCategory; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'EXAMS_TESTS', label: 'Exams & Tests' },
  { id: 'NOTES_FLASHCARDS', label: 'Notes & Flashcards' },
  { id: 'LANGUAGES_AUDIO', label: 'Languages & Audio' },
  { id: 'FOCUS_PRODUCTIVITY', label: 'Focus & Productivity' },
  { id: 'BACKUP_TOOLS', label: 'Backup & Tools' },
];

const APP_GUIDE_CATALOG: AppGuideEntry[] = [
  {
    id: 'mcq_importer',
    title: 'Question Set Importer (Aiken, JSON, Table)',
    category: 'EXAMS_TESTS',
    summary:
      'Paste or import questions from text, Markdown tables, JSON arrays, or files into organized folders.',
    quickTip:
      'Number questions cleanly (1. / Q1.), keep options (A)-(D), and place an Answer Key section like `1: A` at the end.',
    details:
      'Supports single-line, multi-line, Hindi/Devanagari options, LaTeX math ($...$), Assertion-Reason blocks, and automatic duplicate detection.',
    keywords: ['import', 'aiken', 'json', 'markdown', 'table', 'paste', 'questions', 'latex'],
    actionLabel: 'Open Creator',
    destination: 'CREATOR',
  },
  {
    id: 'tcs_cbt_test',
    title: 'TCS iON CBT & Scrollable Exam Modes',
    category: 'EXAMS_TESTS',
    summary:
      'Practice full-screen timed mock tests with Save & Next, Mark for Review, 5-state palette, or continuous paper scroll.',
    quickTip:
      "Switch between single-question CBT and continuous Scrollable Paper right from the Student Portal launch or header.",
    details:
      'Supports countdown & open stopwatch timers, option shuffling with deterministic seed, MSQ partial scoring, and auto-saved resume sessions.',
    keywords: ['cbt', 'tcs', 'mock', 'test', 'timer', 'review', 'palette', 'submit', 'practice'],
    actionLabel: 'Open Student Portal',
    destination: 'STUDENT_TEST',
  },
  {
    id: 'print_omr',
    title: 'Printable Exam Papers & A4 OMR Sheet',
    category: 'EXAMS_TESTS',
    summary:
      'Print A4 question booklets with 10-digit candidate roll grids, timing tracks, Answer Key tables, or Master Solutions.',
    quickTip:
      'Toggle OMR Option Count (4 vs 5 bubbles) and Pre-Printed vs Blank digits directly inside Print & OMR.',
    details:
      'Generates crisp vector OMR sheets calibrated for classroom printing and instant grading comparisons.',
    keywords: ['print', 'pdf', 'omr', 'bubble', 'booklet', 'a4', 'answer key'],
    actionLabel: 'Open Print & OMR',
    destination: 'PRINT_OMR',
  },
  {
    id: 'knowledge_studio',
    title: 'Knowledge Studio & SM-2 Flashcards',
    category: 'NOTES_FLASHCARDS',
    summary:
      'Convert one-liners, missed test questions, and structured study notes into spaced-repetition flashcards.',
    quickTip:
      'Use `Term :: Definition` or `# Heading` blocks; SM-2 schedules optimal Again / Hard / Good / Easy intervals automatically.',
    details:
      'Includes Reader Masking modes (Hide Detail, Hide Lead, Clue-to-Target), Weak-Area compiler from test mistakes, and printable note mode.',
    keywords: ['notes', 'flashcards', 'sm2', 'spaced repetition', 'active recall', 'knowledge'],
    actionLabel: 'Open Knowledge Studio',
    destination: 'KNOWLEDGE_STUDIO',
  },
  {
    id: 'notebook_dictation',
    title: 'Notebook Dictation Lab (WPM & Cues)',
    category: 'LANGUAGES_AUDIO',
    summary:
      'Listen to study notes dictated at adjustable Words Per Minute, elevating pace, and verbal punctuation cues.',
    quickTip:
      'Switch Punctuation Cues between Off, Essential, and Full Verbatim when handwriting definitions into a physical notebook.',
    details:
      'Uses Web Speech Synthesis with phrase-level pause intervals tuned precisely to your target WPM.',
    keywords: ['dictation', 'audio', 'tts', 'voice', 'wpm', 'speed', 'notebook', 'punctuation'],
    actionLabel: 'Open Dictation Lab',
    destination: 'DICTATION_LAB',
  },
  {
    id: 'vocab_hub',
    title: 'Vocabulary Hub, Word of the Day & Headword Inbox',
    category: 'LANGUAGES_AUDIO',
    summary:
      'Organize multilingual decks with IPA/romanization, Word of the Day, duplicate protection, and queued intake.',
    quickTip:
      'Capture words in Headword Inbox while reading, copy an enrichment prompt, and import zero-duplicate cards back.',
    details:
      'Features bidirectional SM-2 recall (Word → Meaning & Meaning → Word), CEFR/custom difficulty filters, and full deck auditing.',
    keywords: ['vocabulary', 'words', 'headword', 'inbox', 'language', 'dictionary', 'srs', 'ipa'],
    actionLabel: 'Open Vocabulary Hub',
    destination: 'VOCABULARY_HUB',
  },
  {
    id: 'headword_queue',
    title: 'Headword Inbox Queue',
    category: 'LANGUAGES_AUDIO',
    summary:
      'Capture tricky words quickly and batch-enrich them before graduating them into full Vocabulary Hub decks.',
    quickTip:
      'Use "Copy AI Prompt for Selected" inside Headword Inbox to enrich up to dozens of queued words in one pass.',
    details:
      'Automatically filters duplicates against both saved decks and previously dismissed headwords.',
    keywords: ['headword', 'inbox', 'queue', 'capture', 'english', 'hindi', 'korean'],
    actionLabel: 'Open Headword Inbox',
    destination: 'HEADWORD_INBOX',
  },
  {
    id: 'study_planner_focus',
    title: 'Smart Study Planner & Pomodoro Focus Clock',
    category: 'FOCUS_PRODUCTIVITY',
    summary:
      'Combine auto-detected due flashcards/vocab/paused tests, custom to-dos, and timestamp-locked Pomodoro blocks.',
    quickTip:
      'Click "Focus" on any study task to link it to the Pomodoro ring; completed focus sprints automatically log +1 Pomodoro.',
    details:
      'Includes 25/5 Classic, 50/10 Deep, 90/15 Mock, and Custom intervals, daily study goals, consecutive streak tracking, and Quiet Mode.',
    keywords: ['planner', 'todo', 'pomodoro', 'focus', 'timer', 'tasks', 'due', 'streak'],
    actionLabel: 'Open Study Planner',
    destination: 'STUDY_PLANNER',
  },
  {
    id: 'brain_calm_lab',
    title: 'Brain & Calm Lab (N-Back, Logic, Schulte & Reset)',
    category: 'FOCUS_PRODUCTIVITY',
    summary:
      '12 offline cognitive drills (N-Back, Corsi, Stroop, Venn Syllogisms, Seating, Schulte, Quant) plus Breathing & Zen grids.',
    quickTip:
      'Set a 3-minute Break Budget before starting so drills sharpen executive attention without cutting into study hours.',
    details:
      'Every syllogism is verified by a 16-region Venn truth-table solver, and every 4×4 / 6×6 Zen Logic Grid guarantees a single unique solution.',
    keywords: ['brain', 'calm', 'n-back', 'corsi', 'stroop', 'syllogism', 'seating', 'schulte', 'quant', 'breathing', 'zen'],
    actionLabel: 'Open Brain & Calm Lab',
    destination: 'BRAIN_LAB',
  },
  {
    id: 'exam_countdowns',
    title: 'Exam-Day Countdowns & Folders',
    category: 'FOCUS_PRODUCTIVITY',
    summary:
      'Organize papers in color-coded nested folders and pin target exam countdown badges across your dashboard.',
    quickTip:
      'Track completion status per paper inside Folders and review target days remaining in both Folders and Study Planner.',
    details:
      'Countdowns are stored persistently and exported inside Backup Vault snapshots.',
    keywords: ['countdown', 'exam date', 'days left', 'folder', 'organize', 'pin'],
    actionLabel: 'Manage Countdowns',
    destination: 'COUNTDOWNS',
  },
  {
    id: 'ai_prompt_studio',
    title: 'AI Prompt Studio & Reusable Presets',
    category: 'BACKUP_TOOLS',
    summary:
      'Generate schema-strict prompts for external AI models outputting ExamSet MCQs, Knowledge Sets, Flashcards, and Vocab JSON.',
    quickTip:
      'Save & Pin your favorite exam templates in the Preset Bar so switching topics takes one click.',
    details:
      'Supports Question Sets, Knowledge Collections, SM-2 Flashcards, and Bilingual Vocabulary JSON prompts with one-click jumps to import.',
    keywords: ['ai', 'prompt', 'generator', 'preset', 'template', 'json'],
    actionLabel: 'Open Prompt Studio',
    destination: 'AI_PROMPT_STUDIO',
  },
  {
    id: 'backup_vault',
    title: 'Backup Vault & SHA-256 Checkpoints',
    category: 'BACKUP_TOOLS',
    summary:
      'Export SHA-256 verified JSON vault archives containing every paper, attempt, note, deck, task, stat, and theme.',
    quickTip:
      'Use Smart Merge when combining devices, or Clean Replace (which auto-saves a rolling safety checkpoint first).',
    details:
      'Verifies section-level SHA-256 integrity hashes before restoring and supports legacy Question Paper JSON bundles too.',
    keywords: ['backup', 'vault', 'restore', 'sha256', 'export', 'merge', 'snapshot'],
    actionLabel: 'Open Backup Vault',
    destination: 'BACKUP_VAULT',
  },
];

export const AppGuideModal: React.FC<AppGuideModalProps> = ({
  isOpen,
  onClose,
  onNavigateDestination,
}) => {
  const { colors, scalePx } = useTheme();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<AppGuideCategory>('ALL');
  const [expandedIds, setExpandedIds] = useState<Record<string, boolean>>({});

  const filteredEntries = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return APP_GUIDE_CATALOG.filter((entry) => {
      const catOk = selectedCategory === 'ALL' || entry.category === selectedCategory;
      const qOk =
        !q ||
        entry.title.toLowerCase().includes(q) ||
        entry.summary.toLowerCase().includes(q) ||
        entry.quickTip.toLowerCase().includes(q) ||
        entry.details.toLowerCase().includes(q) ||
        entry.keywords.some((k) => k.toLowerCase().includes(q));
      return catOk && qOk;
    });
  }, [searchQuery, selectedCategory]);

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-5"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.62)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-3xl rounded-2xl shadow-2xl border flex flex-col max-h-[90vh] overflow-hidden"
        style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.ink }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          className="px-5 py-4 border-b flex items-center justify-between gap-3"
          style={{ borderColor: colors.border }}
        >
          <div className="flex items-center gap-2.5 min-w-0">
            <div
              className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
              style={{ backgroundColor: colors.tealLight, color: colors.teal }}
            >
              <HelpCircle className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <h2 className="font-black truncate" style={{ fontSize: scalePx(16), color: colors.inkBold }}>
                App Guide &amp; Feature Finder
              </h2>
              <p className="text-[11px] truncate" style={{ color: colors.muted }}>
                Search workflows, syntax &amp; jump straight to any tool ({filteredEntries.length}/
                {APP_GUIDE_CATALOG.length})
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 rounded-lg"
            style={{ color: colors.muted }}
            aria-label="Close App Guide"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Search + Category Strip */}
        <div
          className="px-5 py-3 border-b space-y-2.5"
          style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.borderLight }}
        >
          <div className="relative">
            <Search
              className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2"
              style={{ color: colors.teal }}
            />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search OMR, Pomodoro, SM-2, Aiken, N-Back, Syllogism, Backup..."
              className="w-full pl-9 pr-8 py-2 rounded-xl border text-xs font-medium focus:outline-none"
              style={{
                backgroundColor: colors.surface,
                borderColor: colors.border,
                color: colors.inkBold,
              }}
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs font-bold"
                style={{ color: colors.muted }}
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5">
            {GUIDE_CATEGORIES.map((cat) => {
              const sel = selectedCategory === cat.id;
              return (
                <button
                  key={cat.id}
                  type="button"
                  onClick={() => setSelectedCategory(cat.id)}
                  className="px-2.5 py-1 rounded-lg text-xs font-bold whitespace-nowrap border transition"
                  style={
                    sel
                      ? {
                          backgroundColor: colors.tealLight,
                          borderColor: colors.teal,
                          color: colors.tealDark,
                        }
                      : {
                          backgroundColor: colors.surface,
                          borderColor: colors.border,
                          color: colors.ink,
                        }
                  }
                >
                  {cat.label}
                </button>
              );
            })}
          </div>
        </div>

        {/* Entries List */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-3">
          {filteredEntries.length === 0 ? (
            <div
              className="p-8 rounded-2xl border text-center space-y-2"
              style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
            >
              <p className="text-sm font-bold" style={{ color: colors.inkBold }}>
                No matching features found
              </p>
              <p className="text-xs" style={{ color: colors.muted }}>
                Try keywords like &ldquo;timer&rdquo;, &ldquo;flashcard&rdquo;, &ldquo;OMR&rdquo;, &ldquo;Schulte&rdquo;, or &ldquo;backup&rdquo;.
              </p>
              <button
                type="button"
                onClick={() => {
                  setSearchQuery('');
                  setSelectedCategory('ALL');
                }}
                className="px-3 py-1.5 rounded-lg text-xs font-bold border"
                style={{ borderColor: colors.teal, color: colors.teal }}
              >
                Reset Filters
              </button>
            </div>
          ) : (
            filteredEntries.map((entry) => {
              const expanded = Boolean(expandedIds[entry.id]);
              return (
                <div
                  key={entry.id}
                  className="p-4 rounded-xl border space-y-2.5"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <div className="text-sm font-black" style={{ color: colors.inkBold }}>
                        {entry.title}
                      </div>
                      <div className="text-[11px] font-bold" style={{ color: colors.teal }}>
                        {GUIDE_CATEGORIES.find((c) => c.id === entry.category)?.label}
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() =>
                        setExpandedIds((prev) => ({ ...prev, [entry.id]: !prev[entry.id] }))
                      }
                      className="p-1.5 rounded-lg border"
                      style={{
                        backgroundColor: colors.surface,
                        borderColor: colors.border,
                        color: colors.muted,
                      }}
                      aria-label={expanded ? 'Hide details' : 'Show details'}
                    >
                      {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                    </button>
                  </div>

                  <p className="text-xs leading-relaxed" style={{ color: colors.ink }}>
                    {entry.summary}
                  </p>

                  <div
                    className="p-2.5 rounded-lg border flex items-start gap-2 text-[11px]"
                    style={{ backgroundColor: colors.surface, borderColor: colors.borderLight }}
                  >
                    <Lightbulb className="w-4 h-4 flex-shrink-0 mt-0.5" style={{ color: colors.amber }} />
                    <span style={{ color: colors.inkBold }}>{entry.quickTip}</span>
                  </div>

                  {expanded && (
                    <p className="text-xs leading-relaxed pt-1" style={{ color: colors.muted }}>
                      {entry.details}
                    </p>
                  )}

                  <div className="flex justify-end pt-1">
                    <button
                      type="button"
                      onClick={() => {
                        onClose();
                        onNavigateDestination(entry.destination);
                      }}
                      className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-black text-white"
                      style={{ backgroundColor: colors.teal }}
                    >
                      <span>{entry.actionLabel}</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};

export default AppGuideModal;
