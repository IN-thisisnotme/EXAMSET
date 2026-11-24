import React, { useMemo, useState } from 'react';
import {
  ArrowRight,
  BookOpen,
  Check,
  Copy,
  FileText,
  Languages,
  Layers,
  ListChecks,
  Sliders,
  Sparkles,
} from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import {
  FlashcardPromptConfig,
  FlashcardPromptStyle,
  KNOWLEDGE_COLLECTION_META,
  KnowledgeCollectionType,
  KnowledgeDepth,
  KnowledgeLayerOrder,
  KnowledgePromptConfig,
  PromptPreset,
  PromptPresetMode,
  QuestionPromptConfig,
  VocabularyDeck,
  VocabularyLevelCode,
  VocabularyPromptConfig,
  VocabularyUtilityTier,
} from '../../types/phase4';
import { createFlashcardDeckStatsLabel, StudyNoteParser } from '../../utils/studyNoteParser';
import {
  getLevelDescriptor,
  PROMPT_LEVEL_CODES,
  VocabularyEngine,
  VocabularyVariants,
} from '../../utils/vocabularyEngine';
import { PromptPresetBar } from '../PromptPresetBar';

interface PromptGeneratorTabProps {
  presets: PromptPreset[];
  onSavePresets: (next: PromptPreset[]) => void;
  vocabDecks: VocabularyDeck[];
  defaultEnglishVariant?: string;
  onSendToCreator?: (text: string) => void;
  onNavigateToCreator?: () => void;
  onNavigateToKnowledge?: () => void;
  onCreateStudyNoteFromPrompt?: (collectionType: KnowledgeCollectionType, subject: string, topic: string) => void;
  onOpenVocabImport?: () => void;
  onOpenHeadwordInbox?: () => void;
}

const DEFAULT_QUESTION_CFG: QuestionPromptConfig = {
  examType: 'UPSC Civil Services Examination',
  subject: 'General Science & Environment',
  topic: 'Ecology, Biodiversity, Climate Change & Green Energy',
  questionCount: 20,
  difficulty: 'Mixed',
  questionLanguage: 'English',
  explanationChoice: 'Same as Question',
  customExplanationLanguage: '',
  optionCount: 4,
  includeExplanations: true,
  includeFormulasAndDiagrams: true,
  targetOutputFormat: 'JSON',
  promptDensity: 'Concise',
  enableQuantityCorrectionRule: true,
};

const DEFAULT_KNOWLEDGE_CFG: KnowledgePromptConfig = {
  examTarget: 'UPSC Civil Services / Competitive Exams',
  subject: 'Physics & Chemistry',
  topic: 'Thermodynamics, Electrochemistry & Optics High-Yield Laws',
  collectionType: 'MULTI_LINER',
  customCollectionLabel: '',
  itemCount: 15,
  depth: 'BALANCED',
  layerOrder: 'TERM_FIRST',
  language: 'English',
  includeBilingualTerms: true,
  preserveNestedBullets: true,
  includeLatexMath: true,
  includeCallouts: true,
  includeComparisonTable: true,
  includePyqTags: true,
  customInstruction: '',
};

const DEFAULT_FLASHCARD_CFG: FlashcardPromptConfig = {
  examTarget: 'NEET / JEE / UPSC Prelims',
  subject: 'Indian Polity & Physics',
  topic: 'Constitutional Articles, Writs & Core Equations',
  cardStyle: 'QA_RETRIEVAL',
  cardCount: 20,
  depth: 'CRISP',
  language: 'English',
  includeBilingualTerms: false,
  atomicSrsMode: true,
  includeMnemonics: true,
  includeExamples: true,
  includeLatexMath: true,
  includeExamTraps: true,
  customInstruction: '',
};

const DEFAULT_VOCAB_CFG: VocabularyPromptConfig = {
  language: 'English',
  languageVariant: 'UK',
  translationLanguage: '',
  levelCode: 'B2',
  customLevelLabel: '',
  domain: 'Competitive Exam Editorial',
  topic: 'Policy, Diplomacy, Finance & Analytical Arguments',
  wordCount: 15,
  examplesPerWord: 2,
  outputFormat: 'Strict JSON',
  includePronunciation: true,
  includeSynonymsAndCollocations: true,
  includeUsageNote: true,
  excludeAlreadySavedWords: true,
  isHeadwordIndexOnly: false,
  utilityTier: 'EXAM_EDITORIAL',
  customInstruction: '',
};

export const PromptGeneratorTab: React.FC<PromptGeneratorTabProps> = ({
  presets,
  onSavePresets,
  vocabDecks,
  defaultEnglishVariant = 'UK',
  onSendToCreator,
  onNavigateToCreator,
  onNavigateToKnowledge,
  onCreateStudyNoteFromPrompt,
  onOpenVocabImport,
  onOpenHeadwordInbox,
}) => {
  const { colors } = useTheme();
  const [mode, setMode] = useState<PromptPresetMode>('QUESTION_SET');
  const [activePresetIds, setActivePresetIds] = useState<Record<PromptPresetMode, string | null>>({
    QUESTION_SET: null,
    KNOWLEDGE_SET: null,
    FLASHCARD_SET: null,
    VOCABULARY: null,
  });

  const [qCfg, setQCfg] = useState<QuestionPromptConfig>(DEFAULT_QUESTION_CFG);
  const [kCfg, setKCfg] = useState<KnowledgePromptConfig>(DEFAULT_KNOWLEDGE_CFG);
  const [fCfg, setFCfg] = useState<FlashcardPromptConfig>(DEFAULT_FLASHCARD_CFG);
  const [vCfg, setVCfg] = useState<VocabularyPromptConfig>(() => ({
    ...DEFAULT_VOCAB_CFG,
    languageVariant: defaultEnglishVariant || 'UK',
  }));
  const [copied, setCopied] = useState<boolean>(false);

  const savedWordsForTargetLang = useMemo(() => {
    const langLower = vCfg.language.trim().toLowerCase();
    const out: string[] = [];
    for (const d of vocabDecks) {
      if (d.language.trim().toLowerCase() === langLower) {
        for (const e of d.entries) {
          out.push(e.word);
        }
      }
    }
    return Array.from(new Set(out));
  }, [vocabDecks, vCfg.language]);

  const effectiveExplanationLang = useMemo(() => {
    const q = qCfg.questionLanguage.trim() || 'English';
    switch (qCfg.explanationChoice) {
      case 'Same as Question':
        return `${q} (strictly matching question language)`;
      case 'Bilingual':
        return q.toLowerCase() === 'english' ? 'Bilingual (English & Hindi)' : `Bilingual (English & ${q})`;
      case 'English':
        return 'English';
      case 'Hindi':
        return 'Hindi';
      case 'Custom':
        return qCfg.customExplanationLanguage.trim() || q;
      default:
        return qCfg.explanationChoice;
    }
  }, [qCfg]);

  const questionPrompt = useMemo(() => {
    const qLang = qCfg.questionLanguage.trim() || 'English';
    const isMsq = qCfg.difficulty === 'MSQ';
    const optRule =
      qCfg.optionCount === 5
        ? 'Include 5 mutually exclusive options (a, b, c, d, e).'
        : 'STRICT 4-OPTION CONSTRAINT: Exactly 4 mutually exclusive options (a, b, c, d). Never include option E.';
    const keyRule = isMsq
      ? '"correctAnswer" may contain comma-separated keys like "A,C" for Multiple-Select (MSQ) items.'
      : `"correctAnswer" must be a single uppercase letter ${qCfg.optionCount === 5 ? '"A".."E"' : '"A".."D"'}.`;
    const mathDirective = qCfg.includeFormulasAndDiagrams
      ? 'Wrap formulas cleanly in inline $...$ LaTeX.'
      : 'Use plain ASCII/Unicode without LaTeX delimiters.';
    const strictQtyRule = qCfg.enableQuantityCorrectionRule
      ? `\n- STRICT QUANTITY RULE: Output EVERY single item 1..${qCfg.questionCount} in full; never summarise or write "etc.".`
      : '';

    if (qCfg.targetOutputFormat === 'JSON') {
      const optEJson = qCfg.optionCount === 5 ? ',\n      "e": "Option E text"' : '';
      if (qCfg.promptDensity === 'Concise') {
        return `Generate exactly ${qCfg.questionCount} multiple-choice questions for ${qCfg.examType} at ${qCfg.difficulty} standard on ${qCfg.subject}${qCfg.topic ? ` (${qCfg.topic})` : ''} in ${qLang}.
Output ONLY a strict, valid JSON array of objects without markdown fences or extra text. Format:
[
  {
    "num": 1,
    "question": "Question text...",
    "options": {
      "a": "Option A text",
      "b": "Option B text",
      "c": "Option C text",
      "d": "Option D text"${optEJson}
    },
    "correctAnswer": "${isMsq ? 'A,C' : 'A'}",
    "explanation": "Concise reasoning in ${effectiveExplanationLang}..."
  }
]
Requirements:
1. ${optRule}
2. ${keyRule}
3. ${mathDirective}
4. Valid JSON only. Do not truncate.${strictQtyRule}`;
      }

      return `Generate exactly ${qCfg.questionCount} rigorous questions at ${qCfg.difficulty} difficulty standard on ${qCfg.subject}${qCfg.topic ? ` (Focus Topics: ${qCfg.topic})` : ''}, tailored for ${qCfg.examType} in ${qLang}.
Output MUST be an unbroken, valid JSON array:
[
  {
    "num": 1,
    "question": "Question text...",
    "options": {
      "a": "Option A text",
      "b": "Option B text",
      "c": "Option C text",
      "d": "Option D text"${optEJson}
    },
    "correctAnswer": "${isMsq ? 'A,C' : 'A'}",
    "explanation": "Core principle and distractor analysis in ${effectiveExplanationLang}."
  }
]
Strict Rules:
- ${optRule}
- ${keyRule}
- ${mathDirective}
- Balance answer key distribution evenly across options.
- Return raw JSON only so it parses immediately.${strictQtyRule}`;
    }

    if (qCfg.targetOutputFormat === 'Table') {
      const colEHeader = qCfg.optionCount === 5 ? ' Option E |' : '';
      const colESep = qCfg.optionCount === 5 ? ' :--- |' : '';
      const colEVal = qCfg.optionCount === 5 ? ' Choice E |' : '';
      return `Generate exactly ${qCfg.questionCount} questions on ${qCfg.subject}${qCfg.topic ? ` (${qCfg.topic})` : ''} for ${qCfg.examType} at ${qCfg.difficulty} standard in ${qLang}.
Output MUST be a Markdown Pipe Table with these exact columns:
| Question | Option A | Option B | Option C | Option D |${colEHeader} Answer | Explanation |
| :--- | :--- | :--- | :--- | :--- |${colESep} :--- | :--- |
| Sample question text? | Choice A | Choice B | Choice C | Choice D |${colEVal} ${isMsq ? 'A,C' : 'A'} | Rationale in ${effectiveExplanationLang} |

Rules:
1. ${optRule}
2. ${keyRule}
3. ${mathDirective}${strictQtyRule}`;
    }

    const optELine = qCfg.optionCount === 5 ? '\n(E) Option E' : '';
    if (qCfg.promptDensity === 'Concise') {
      return `Generate ${qCfg.questionCount} questions for ${qCfg.examType} on ${qCfg.subject}${qCfg.topic ? ` (${qCfg.topic})` : ''} in ${qLang}.
Output format:
Q1. Question text
(A) Option A
(B) Option B
(C) Option C
(D) Option D${optELine}
ANSWER: ${isMsq ? 'A,C' : 'A'}
EXPLANATION: Solution in ${effectiveExplanationLang}

(${mathDirective} — Repeat sequentially for Q1 through Q${qCfg.questionCount})${strictQtyRule}`;
    }

    return `Generate exactly ${qCfg.questionCount} questions at ${qCfg.difficulty} standard on ${qCfg.subject}${qCfg.topic ? ` (${qCfg.topic})` : ''} for ${qCfg.examType} in ${qLang}.

## Phase 1: Questions
Label sequentially Q1. through Q${qCfg.questionCount}. Each with options (A) through (${qCfg.optionCount === 5 ? 'E' : 'D'}).
Every line must start flush-left. ${mathDirective}

## Phase 2: Answer Key
Q1: (${isMsq ? 'A,C' : 'C'})
...

${
  qCfg.includeExplanations
    ? `## Phase 3: Detailed Explanations
For each question Q1 to Q${qCfg.questionCount}, explain core concept and distractors in ${effectiveExplanationLang}.`
    : ''
}${strictQtyRule}`;
  }, [qCfg, effectiveExplanationLang]);

  const generatedPrompt = useMemo(() => {
    switch (mode) {
      case 'QUESTION_SET':
        return questionPrompt;
      case 'KNOWLEDGE_SET':
        return StudyNoteParser.compileKnowledgePrompt(kCfg);
      case 'FLASHCARD_SET':
        return StudyNoteParser.compileFlashcardPrompt(fCfg);
      case 'VOCABULARY':
        return VocabularyEngine.compileVocabularyPrompt(vCfg, savedWordsForTargetLang);
    }
  }, [mode, questionPrompt, kCfg, fCfg, vCfg, savedWordsForTargetLang]);

  const handleCopyPrompt = () => {
    navigator.clipboard?.writeText(generatedPrompt);
    setCopied(true);
    setTimeout(() => setCopied(false), 2400);
  };

  // Preset Handlers
  const handleSelectPreset = (preset: PromptPreset) => {
    setActivePresetIds((prev) => ({ ...prev, [preset.mode]: preset.id }));
    if (preset.mode === 'QUESTION_SET' && preset.questionConfig) setQCfg(preset.questionConfig);
    if (preset.mode === 'KNOWLEDGE_SET' && preset.knowledgeConfig) setKCfg(preset.knowledgeConfig);
    if (preset.mode === 'FLASHCARD_SET' && preset.flashcardConfig) setFCfg(preset.flashcardConfig);
    if (preset.mode === 'VOCABULARY' && preset.vocabularyConfig) setVCfg(preset.vocabularyConfig);
  };

  const handleSaveNewPreset = (name: string) => {
    const now = Date.now();
    const newPreset: PromptPreset = {
      id: `preset-${now}-${Math.random().toString(36).slice(2, 6)}`,
      name,
      mode,
      isPinned: false,
      createdAt: now,
      updatedAt: now,
      lastUsedAt: now,
      ...(mode === 'QUESTION_SET' ? { questionConfig: qCfg } : {}),
      ...(mode === 'KNOWLEDGE_SET' ? { knowledgeConfig: kCfg } : {}),
      ...(mode === 'FLASHCARD_SET' ? { flashcardConfig: fCfg } : {}),
      ...(mode === 'VOCABULARY' ? { vocabularyConfig: vCfg } : {}),
    };
    const next = [newPreset, ...presets];
    onSavePresets(next);
    setActivePresetIds((prev) => ({ ...prev, [mode]: newPreset.id }));
  };

  const handleUpdateActivePreset = () => {
    const id = activePresetIds[mode];
    if (!id) return;
    const now = Date.now();
    const next = presets.map((p) =>
      p.id === id
        ? {
            ...p,
            updatedAt: now,
            lastUsedAt: now,
            ...(mode === 'QUESTION_SET' ? { questionConfig: qCfg } : {}),
            ...(mode === 'KNOWLEDGE_SET' ? { knowledgeConfig: kCfg } : {}),
            ...(mode === 'FLASHCARD_SET' ? { flashcardConfig: fCfg } : {}),
            ...(mode === 'VOCABULARY' ? { vocabularyConfig: vCfg } : {}),
          }
        : p
    );
    onSavePresets(next);
  };

  const handleRenamePreset = (id: string, newName: string) => {
    onSavePresets(presets.map((p) => (p.id === id ? { ...p, name: newName, updatedAt: Date.now() } : p)));
  };

  const handleDuplicatePreset = (id: string) => {
    const orig = presets.find((p) => p.id === id);
    if (!orig) return;
    const now = Date.now();
    const copy: PromptPreset = {
      ...orig,
      id: `preset-${now}-${Math.random().toString(36).slice(2, 6)}`,
      name: `${orig.name} (Copy)`,
      isPinned: false,
      createdAt: now,
      updatedAt: now,
    };
    onSavePresets([copy, ...presets]);
    setActivePresetIds((prev) => ({ ...prev, [orig.mode]: copy.id }));
  };

  const handleTogglePinPreset = (id: string) => {
    onSavePresets(presets.map((p) => (p.id === id ? { ...p, isPinned: !p.isPinned } : p)));
  };

  const handleDeletePreset = (id: string) => {
    onSavePresets(presets.filter((p) => p.id !== id));
    if (activePresetIds[mode] === id) {
      setActivePresetIds((prev) => ({ ...prev, [mode]: null }));
    }
  };

  const MODE_TABS: { id: PromptPresetMode; label: string; sub: string; icon: React.ReactNode }[] = [
    { id: 'QUESTION_SET', label: 'Question Set', sub: 'McqParser JSON/Text', icon: <FileText className="w-4 h-4" /> },
    { id: 'KNOWLEDGE_SET', label: 'Knowledge Set', sub: 'Notes, Codex, Tables', icon: <BookOpen className="w-4 h-4" /> },
    { id: 'FLASHCARD_SET', label: 'Flashcard Set', sub: 'Atomic SM-2 Cards', icon: <Layers className="w-4 h-4" /> },
    { id: 'VOCABULARY', label: 'Vocabulary Hub', sub: 'Lexicon & Headwords', icon: <Languages className="w-4 h-4" /> },
  ];

  const isVocabEnglish = VocabularyVariants.isEnglish(vCfg.language);

  return (
    <div className="space-y-5">
      {/* Header Banner + Mode Switcher */}
      <div
        className="p-4 rounded-xl border space-y-4"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h3 className="text-base font-bold flex items-center gap-2" style={{ color: colors.inkBold }}>
              <Sparkles className="w-5 h-5" style={{ color: colors.amber }} />
              Multi-Mode AI Prompt Architect
            </h3>
            <p className="text-xs mt-0.5" style={{ color: colors.muted }}>
              Zero-drift prompt blueprints synced with ExamSet Question Parser, Knowledge Studio, SM-2 Flashcards, and Vocabulary Hub.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {mode === 'QUESTION_SET' && onSendToCreator && (
              <button
                type="button"
                onClick={() => onSendToCreator(generatedPrompt)}
                className="flex items-center gap-1.5 px-3 py-2 text-xs font-bold rounded-lg border transition"
                style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
              >
                <ArrowRight className="w-3.5 h-3.5" />
                Open in Paper Importer
              </button>
            )}
            {mode === 'KNOWLEDGE_SET' && onCreateStudyNoteFromPrompt && (
              <button
                type="button"
                onClick={() => onCreateStudyNoteFromPrompt(kCfg.collectionType, kCfg.subject, kCfg.topic)}
                className="flex items-center gap-1.5 px-3 py-2 text-xs font-bold rounded-lg border transition"
                style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
              >
                <ArrowRight className="w-3.5 h-3.5" />
                Create Study Note
              </button>
            )}
            {mode === 'FLASHCARD_SET' && onCreateStudyNoteFromPrompt && (
              <button
                type="button"
                onClick={() => onCreateStudyNoteFromPrompt('FLASHCARDS', fCfg.subject, fCfg.topic)}
                className="flex items-center gap-1.5 px-3 py-2 text-xs font-bold rounded-lg border transition"
                style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
              >
                <ArrowRight className="w-3.5 h-3.5" />
                Create Flashcard Deck
              </button>
            )}
            {mode === 'VOCABULARY' && (vCfg.isHeadwordIndexOnly ? onOpenHeadwordInbox : onOpenVocabImport) && (
              <button
                type="button"
                onClick={() => (vCfg.isHeadwordIndexOnly ? onOpenHeadwordInbox?.() : onOpenVocabImport?.())}
                className="flex items-center gap-1.5 px-3 py-2 text-xs font-bold rounded-lg border transition"
                style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
              >
                <ArrowRight className="w-3.5 h-3.5" />
                {vCfg.isHeadwordIndexOnly ? 'Open Headword Inbox' : 'Import Vocabulary JSON'}
              </button>
            )}

            <button
              type="button"
              onClick={handleCopyPrompt}
              className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold rounded-lg shadow-sm transition"
              style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
            >
              {copied ? <Check className="w-4 h-4 text-emerald-300" /> : <Copy className="w-4 h-4" />}
              <span>{copied ? 'Copied!' : 'Copy Prompt'}</span>
            </button>
          </div>
        </div>

        {/* 4-Mode Selector Grid */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-2">
          {MODE_TABS.map((mt) => {
            const active = mode === mt.id;
            return (
              <button
                key={mt.id}
                type="button"
                onClick={() => setMode(mt.id)}
                className="flex items-center gap-2.5 p-2.5 rounded-xl border text-left transition"
                style={
                  active
                    ? {
                        backgroundColor: colors.primaryButtonBg,
                        color: colors.primaryButtonText,
                        borderColor: colors.primaryButtonBg,
                      }
                    : {
                        backgroundColor: colors.surface,
                        color: colors.ink,
                        borderColor: colors.border,
                      }
                }
              >
                <div
                  className="p-2 rounded-lg"
                  style={{
                    backgroundColor: active ? 'rgba(255,255,255,0.18)' : colors.surfaceVariant,
                  }}
                >
                  {mt.icon}
                </div>
                <div className="min-w-0">
                  <div className="text-xs font-bold truncate">{mt.label}</div>
                  <div
                    className="text-[10px] truncate"
                    style={{ color: active ? 'rgba(255,255,255,0.82)' : colors.muted }}
                  >
                    {mt.sub}
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Saved Presets CRUD Bar */}
      <PromptPresetBar
        mode={mode}
        presets={presets}
        activePresetId={activePresetIds[mode]}
        onSelectPreset={handleSelectPreset}
        onSaveNewPreset={handleSaveNewPreset}
        onUpdateActivePreset={handleUpdateActivePreset}
        onRenamePreset={handleRenamePreset}
        onDuplicatePreset={handleDuplicatePreset}
        onTogglePinPreset={handleTogglePinPreset}
        onDeletePreset={handleDeletePreset}
      />

      {/* Configurator + Output */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Parameters */}
        <div className="lg:col-span-5 space-y-4">
          <div
            className="rounded-xl border p-5 space-y-4 text-xs"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <h4 className="font-bold uppercase tracking-wider flex items-center gap-1.5" style={{ color: colors.muted }}>
              <Sliders className="w-3.5 h-3.5" />
              {mode === 'QUESTION_SET' && 'Question Blueprint Parameters'}
              {mode === 'KNOWLEDGE_SET' && 'Knowledge Note Blueprint'}
              {mode === 'FLASHCARD_SET' && 'SM-2 Flashcard Blueprint'}
              {mode === 'VOCABULARY' && 'Lexicon & Proficiency Parameters'}
            </h4>

            {/* QUESTION SET FORM */}
            {mode === 'QUESTION_SET' && (
              <div className="space-y-3">
                <div>
                  <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                    Target Examination
                  </label>
                  <select
                    value={qCfg.examType}
                    onChange={(e) => setQCfg({ ...qCfg, examType: e.target.value })}
                    className="w-full px-3 py-2 rounded-lg border"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                  >
                    <option value="UPSC Civil Services Examination">UPSC Civil Services Examination</option>
                    <option value="SSC Combined Graduate Level (CGL)">SSC Combined Graduate Level (CGL)</option>
                    <option value="National Eligibility cum Entrance Test (NEET)">NEET Medical Entrance</option>
                    <option value="JEE Main & Advanced">JEE Engineering Entrance (SCQ + MSQ)</option>
                    <option value="State Public Service Commission (PSC)">State PSC Exam (5 Options Supported)</option>
                    <option value="Banking Probationary Officer (IBPS PO / SBI)">Banking PO / Clerk</option>
                    <option value="Central Board of Secondary Education (CBSE Class 12)">CBSE Class 12 Board</option>
                  </select>
                </div>

                <div>
                  <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                    Subject
                  </label>
                  <input
                    type="text"
                    value={qCfg.subject}
                    onChange={(e) => setQCfg({ ...qCfg, subject: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                  />
                </div>

                <div>
                  <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                    Specific Topics
                  </label>
                  <textarea
                    value={qCfg.topic}
                    onChange={(e) => setQCfg({ ...qCfg, topic: e.target.value })}
                    rows={2}
                    className="w-full px-3 py-1.5 rounded-lg border"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                  />
                </div>

                <div
                  className="p-3 rounded-lg border space-y-2.5"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-semibold" style={{ color: colors.ink }}>
                      Target Format
                    </span>
                    <div className="flex gap-1">
                      {([4, 5] as const).map((optN) => (
                        <button
                          key={optN}
                          type="button"
                          onClick={() => setQCfg({ ...qCfg, optionCount: optN })}
                          className="px-2 py-0.5 rounded text-[10px] font-bold border"
                          style={
                            qCfg.optionCount === optN
                              ? { backgroundColor: colors.teal, color: '#fff', borderColor: colors.teal }
                              : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
                          }
                        >
                          {optN} Opts
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="grid grid-cols-3 gap-1.5">
                    {(['JSON', 'Text', 'Table'] as const).map((fmt) => (
                      <button
                        key={fmt}
                        type="button"
                        onClick={() => setQCfg({ ...qCfg, targetOutputFormat: fmt })}
                        className="px-2 py-1.5 rounded-md font-bold border transition"
                        style={
                          qCfg.targetOutputFormat === fmt
                            ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText, borderColor: colors.primaryButtonBg }
                            : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
                        }
                      >
                        {fmt}
                      </button>
                    ))}
                  </div>

                  <div className="flex items-center justify-between pt-1 border-t" style={{ borderColor: colors.borderLight }}>
                    <span className="text-[11px]" style={{ color: colors.muted }}>
                      Prompt Length:
                    </span>
                    <div className="flex gap-1">
                      {(['Concise', 'Comprehensive'] as const).map((d) => (
                        <button
                          key={d}
                          type="button"
                          onClick={() => setQCfg({ ...qCfg, promptDensity: d })}
                          className="px-2 py-0.5 rounded text-[10px] font-bold"
                          style={
                            qCfg.promptDensity === d
                              ? { backgroundColor: colors.navyBanner, color: colors.navyBannerText }
                              : { backgroundColor: colors.surface, color: colors.muted }
                          }
                        >
                          {d}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Question Count
                    </label>
                    <select
                      value={qCfg.questionCount}
                      onChange={(e) => setQCfg({ ...qCfg, questionCount: Number(e.target.value) })}
                      className="w-full px-3 py-2 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    >
                      {[10, 20, 30, 50, 100].map((n) => (
                        <option key={n} value={n}>
                          {n} Questions
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Difficulty / Style
                    </label>
                    <select
                      value={qCfg.difficulty}
                      onChange={(e) => setQCfg({ ...qCfg, difficulty: e.target.value })}
                      className="w-full px-3 py-2 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    >
                      <option value="Mixed">Mixed Standard</option>
                      <option value="Standard">Standard / Moderate</option>
                      <option value="Hard">Advanced / Hard</option>
                      <option value="Assertion-Reason">Assertion–Reason</option>
                      <option value="MSQ">Multiple-Select (MSQ)</option>
                    </select>
                  </div>
                </div>

                <div
                  className="p-3 rounded-lg border space-y-2.5"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                >
                  <label className="block font-semibold" style={{ color: colors.ink }}>
                    Question Language
                  </label>
                  <input
                    type="text"
                    value={qCfg.questionLanguage}
                    onChange={(e) => setQCfg({ ...qCfg, questionLanguage: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border"
                    style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                  />
                  <div className="flex flex-wrap gap-1">
                    {['English', 'Hindi', 'Bengali', 'Marathi', 'Tamil', 'Korean', 'French'].map((lng) => (
                      <button
                        key={lng}
                        type="button"
                        onClick={() => setQCfg({ ...qCfg, questionLanguage: lng })}
                        className="px-1.5 py-0.5 rounded text-[10px] border font-semibold"
                        style={
                          qCfg.questionLanguage.toLowerCase() === lng.toLowerCase()
                            ? { backgroundColor: colors.teal, color: '#fff', borderColor: colors.teal }
                            : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
                        }
                      >
                        {lng}
                      </button>
                    ))}
                  </div>

                  <div className="grid grid-cols-2 gap-2 pt-2 border-t" style={{ borderColor: colors.borderLight }}>
                    <div>
                      <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                        Explanation Language
                      </label>
                      <select
                        value={qCfg.explanationChoice}
                        onChange={(e) => setQCfg({ ...qCfg, explanationChoice: e.target.value })}
                        className="w-full px-2 py-1.5 rounded-lg border"
                        style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                      >
                        <option value="Same as Question">Same as Question</option>
                        <option value="English">English</option>
                        <option value="Hindi">Hindi</option>
                        <option value="Bilingual">Bilingual</option>
                        <option value="Custom">Custom...</option>
                      </select>
                      {qCfg.explanationChoice === 'Custom' && (
                        <input
                          type="text"
                          value={qCfg.customExplanationLanguage}
                          onChange={(e) => setQCfg({ ...qCfg, customExplanationLanguage: e.target.value })}
                          placeholder="Language..."
                          className="w-full mt-1 px-2 py-1 rounded border"
                          style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                        />
                      )}
                    </div>

                    <div className="space-y-1.5 pt-4">
                      <label className="flex items-center gap-2 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={qCfg.includeExplanations}
                          onChange={(e) => setQCfg({ ...qCfg, includeExplanations: e.target.checked })}
                        />
                        <span className="font-semibold" style={{ color: colors.ink }}>
                          Include Solutions
                        </span>
                      </label>
                      <label className="flex items-center gap-2 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={qCfg.includeFormulasAndDiagrams}
                          onChange={(e) => setQCfg({ ...qCfg, includeFormulasAndDiagrams: e.target.checked })}
                        />
                        <span className="font-semibold" style={{ color: colors.ink }}>
                          LaTeX Math
                        </span>
                      </label>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* KNOWLEDGE SET FORM */}
            {mode === 'KNOWLEDGE_SET' && (
              <div className="space-y-3">
                <div>
                  <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                    Knowledge Collection Type
                  </label>
                  <select
                    value={kCfg.collectionType}
                    onChange={(e) =>
                      setKCfg({ ...kCfg, collectionType: e.target.value as KnowledgeCollectionType })
                    }
                    className="w-full px-3 py-2 rounded-lg border"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                  >
                    {(Object.keys(KNOWLEDGE_COLLECTION_META) as KnowledgeCollectionType[]).map((key) => (
                      <option key={key} value={key}>
                        {KNOWLEDGE_COLLECTION_META[key].label}
                      </option>
                    ))}
                  </select>
                  <p className="text-[11px] mt-1" style={{ color: colors.muted }}>
                    {KNOWLEDGE_COLLECTION_META[kCfg.collectionType].description}
                  </p>
                </div>

                {kCfg.collectionType === 'CUSTOM' && (
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Custom Collection Name
                    </label>
                    <input
                      type="text"
                      value={kCfg.customCollectionLabel}
                      onChange={(e) => setKCfg({ ...kCfg, customCollectionLabel: e.target.value })}
                      placeholder="e.g., Case Law Briefs"
                      className="w-full px-3 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    />
                  </div>
                )}

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Target Exam
                    </label>
                    <input
                      type="text"
                      value={kCfg.examTarget}
                      onChange={(e) => setKCfg({ ...kCfg, examTarget: e.target.value })}
                      className="w-full px-3 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    />
                  </div>
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Subject
                    </label>
                    <input
                      type="text"
                      value={kCfg.subject}
                      onChange={(e) => setKCfg({ ...kCfg, subject: e.target.value })}
                      className="w-full px-3 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    />
                  </div>
                </div>

                <div>
                  <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                    Chapter / Topic Scope
                  </label>
                  <textarea
                    value={kCfg.topic}
                    onChange={(e) => setKCfg({ ...kCfg, topic: e.target.value })}
                    rows={2}
                    className="w-full px-3 py-1.5 rounded-lg border"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                  />
                </div>

                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Item Count
                    </label>
                    <select
                      value={kCfg.itemCount}
                      onChange={(e) => setKCfg({ ...kCfg, itemCount: Number(e.target.value) })}
                      className="w-full px-2 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    >
                      {[8, 12, 15, 20, 30, 50].map((n) => (
                        <option key={n} value={n}>
                          {n} items
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Depth
                    </label>
                    <select
                      value={kCfg.depth}
                      onChange={(e) => setKCfg({ ...kCfg, depth: e.target.value as KnowledgeDepth })}
                      className="w-full px-2 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    >
                      <option value="CRISP">Crisp</option>
                      <option value="BALANCED">Balanced</option>
                      <option value="DEEP">Deep Codex</option>
                    </select>
                  </div>
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Language
                    </label>
                    <input
                      type="text"
                      value={kCfg.language}
                      onChange={(e) => setKCfg({ ...kCfg, language: e.target.value })}
                      className="w-full px-2 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    />
                  </div>
                </div>

                <div>
                  <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                    Layer / Line Order
                  </label>
                  <select
                    value={kCfg.layerOrder}
                    onChange={(e) => setKCfg({ ...kCfg, layerOrder: e.target.value as KnowledgeLayerOrder })}
                    className="w-full px-3 py-1.5 rounded-lg border"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                  >
                    <option value="TERM_FIRST">Term First (**Term** -&gt; Detail)</option>
                    <option value="BACKWARDS_ANSWER_FIRST">Scenario Clue First (Clue -&gt; **Target**)</option>
                    <option value="QA_SEPARATE_LINES">Q / A Separate Lines (Q: ... / A: ...)</option>
                    <option value="AUTO_DETECT">Auto-Detect Best Order</option>
                  </select>
                </div>

                <div className="grid grid-cols-2 gap-2 pt-1">
                  {[
                    { key: 'includeLatexMath', label: 'LaTeX Equations ($...$)' },
                    { key: 'includeCallouts', label: 'Exam Tip Callouts (> ...)' },
                    { key: 'includeComparisonTable', label: 'Comparison Table' },
                    { key: 'includePyqTags', label: '[PYQ Focus] Badges' },
                    { key: 'includeBilingualTerms', label: 'English Term Glossary' },
                    { key: 'preserveNestedBullets', label: 'Nested Sub-Points' },
                  ].map((chk) => (
                    <label key={chk.key} className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={Boolean((kCfg as unknown as Record<string, unknown>)[chk.key])}
                        onChange={(e) => setKCfg({ ...kCfg, [chk.key]: e.target.checked })}
                      />
                      <span style={{ color: colors.ink }}>{chk.label}</span>
                    </label>
                  ))}
                </div>

                <div>
                  <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                    Custom Instruction (Optional)
                  </label>
                  <input
                    type="text"
                    value={kCfg.customInstruction}
                    onChange={(e) => setKCfg({ ...kCfg, customInstruction: e.target.value })}
                    placeholder="e.g., Focus on landmark SC judgments after 2015..."
                    className="w-full px-3 py-1.5 rounded-lg border"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                  />
                </div>
              </div>
            )}

            {/* FLASHCARD SET FORM */}
            {mode === 'FLASHCARD_SET' && (
              <div className="space-y-3">
                <div>
                  <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                    Card Presentation Style
                  </label>
                  <div className="grid grid-cols-2 gap-1.5">
                    {(
                      [
                        'QA_RETRIEVAL',
                        'TERM_DEFINITION',
                        'CLOZE_SCENARIO',
                        'FORMULA_TRAP',
                      ] as FlashcardPromptStyle[]
                    ).map((st) => {
                      const active = fCfg.cardStyle === st;
                      return (
                        <button
                          key={st}
                          type="button"
                          onClick={() => setFCfg({ ...fCfg, cardStyle: st })}
                          className="p-2 rounded-lg border text-left font-bold text-[11px] transition"
                          style={
                            active
                              ? {
                                  backgroundColor: colors.primaryButtonBg,
                                  color: colors.primaryButtonText,
                                  borderColor: colors.primaryButtonBg,
                                }
                              : {
                                  backgroundColor: colors.surfaceVariant,
                                  color: colors.ink,
                                  borderColor: colors.border,
                                }
                          }
                        >
                          {createFlashcardDeckStatsLabel(st)}
                        </button>
                      );
                    })}
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Target Exam
                    </label>
                    <input
                      type="text"
                      value={fCfg.examTarget}
                      onChange={(e) => setFCfg({ ...fCfg, examTarget: e.target.value })}
                      className="w-full px-3 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    />
                  </div>
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Subject
                    </label>
                    <input
                      type="text"
                      value={fCfg.subject}
                      onChange={(e) => setFCfg({ ...fCfg, subject: e.target.value })}
                      className="w-full px-3 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    />
                  </div>
                </div>

                <div>
                  <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                    Deck Topics
                  </label>
                  <textarea
                    value={fCfg.topic}
                    onChange={(e) => setFCfg({ ...fCfg, topic: e.target.value })}
                    rows={2}
                    className="w-full px-3 py-1.5 rounded-lg border"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Card Count
                    </label>
                    <select
                      value={fCfg.cardCount}
                      onChange={(e) => setFCfg({ ...fCfg, cardCount: Number(e.target.value) })}
                      className="w-full px-2 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    >
                      {[10, 15, 20, 30, 40, 60].map((n) => (
                        <option key={n} value={n}>
                          {n} Flashcards
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Language
                    </label>
                    <input
                      type="text"
                      value={fCfg.language}
                      onChange={(e) => setFCfg({ ...fCfg, language: e.target.value })}
                      className="w-full px-2 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-2 pt-1">
                  {[
                    { key: 'atomicSrsMode', label: 'Atomic SRS (<= 22 words)' },
                    { key: 'includeExamTraps', label: 'Include Exam Trap' },
                    { key: 'includeMnemonics', label: 'Include Mnemonics' },
                    { key: 'includeLatexMath', label: 'LaTeX Equations' },
                    { key: 'includeExamples', label: 'Brief Examples' },
                  ].map((chk) => (
                    <label key={chk.key} className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={Boolean((fCfg as unknown as Record<string, unknown>)[chk.key])}
                        onChange={(e) => setFCfg({ ...fCfg, [chk.key]: e.target.checked })}
                      />
                      <span style={{ color: colors.ink }}>{chk.label}</span>
                    </label>
                  ))}
                </div>
              </div>
            )}

            {/* VOCABULARY HUB FORM */}
            {mode === 'VOCABULARY' && (
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Target Language
                    </label>
                    <input
                      type="text"
                      value={vCfg.language}
                      onChange={(e) => setVCfg({ ...vCfg, language: e.target.value })}
                      className="w-full px-3 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    />
                    <div className="flex flex-wrap gap-1 mt-1">
                      {['English', 'Japanese', 'Korean', 'French', 'German', 'Spanish'].map((lng) => (
                        <button
                          key={lng}
                          type="button"
                          onClick={() => setVCfg({ ...vCfg, language: lng })}
                          className="px-1.5 py-0.5 rounded text-[10px] border font-semibold"
                          style={
                            vCfg.language.toLowerCase() === lng.toLowerCase()
                              ? { backgroundColor: colors.teal, color: '#fff', borderColor: colors.teal }
                              : { backgroundColor: colors.surfaceVariant, color: colors.ink, borderColor: colors.border }
                          }
                        >
                          {lng}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Proficiency Level
                    </label>
                    <select
                      value={vCfg.levelCode}
                      onChange={(e) => setVCfg({ ...vCfg, levelCode: e.target.value as VocabularyLevelCode })}
                      className="w-full px-3 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    >
                      {PROMPT_LEVEL_CODES.map((code) => (
                        <option key={code} value={code}>
                          {getLevelDescriptor(code, vCfg.language, vCfg.customLevelLabel).title}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                {isVocabEnglish && (
                  <div
                    className="p-2.5 rounded-lg border flex items-center justify-between gap-2"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                  >
                    <span className="font-semibold" style={{ color: colors.ink }}>
                      English Variant:
                    </span>
                    <div className="flex gap-1">
                      {(['UK', 'US', 'Neutral'] as const).map((vr) => (
                        <button
                          key={vr}
                          type="button"
                          onClick={() => setVCfg({ ...vCfg, languageVariant: vr })}
                          className="px-2.5 py-1 rounded-md text-[11px] font-bold border"
                          style={
                            vCfg.languageVariant.toUpperCase() === vr.toUpperCase()
                              ? { backgroundColor: colors.teal, color: '#fff', borderColor: colors.teal }
                              : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
                          }
                        >
                          {vr}
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Domain / Exam
                    </label>
                    <input
                      type="text"
                      value={vCfg.domain}
                      onChange={(e) => setVCfg({ ...vCfg, domain: e.target.value })}
                      className="w-full px-3 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    />
                  </div>
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Bridge Translation Language
                    </label>
                    <input
                      type="text"
                      value={vCfg.translationLanguage}
                      onChange={(e) => setVCfg({ ...vCfg, translationLanguage: e.target.value })}
                      placeholder="Optional (e.g., Hindi)"
                      className="w-full px-3 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    />
                  </div>
                </div>

                <div>
                  <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                    Topic Focus
                  </label>
                  <input
                    type="text"
                    value={vCfg.topic}
                    onChange={(e) => setVCfg({ ...vCfg, topic: e.target.value })}
                    className="w-full px-3 py-1.5 rounded-lg border"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                  />
                </div>

                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Words
                    </label>
                    <select
                      value={vCfg.wordCount}
                      onChange={(e) => setVCfg({ ...vCfg, wordCount: Number(e.target.value) })}
                      className="w-full px-2 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    >
                      {[10, 15, 20, 25, 40].map((n) => (
                        <option key={n} value={n}>
                          {n} words
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Examples / Word
                    </label>
                    <select
                      value={vCfg.examplesPerWord}
                      onChange={(e) => setVCfg({ ...vCfg, examplesPerWord: Number(e.target.value) })}
                      className="w-full px-2 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    >
                      {[1, 2, 3].map((n) => (
                        <option key={n} value={n}>
                          {n}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                      Format
                    </label>
                    <select
                      value={vCfg.outputFormat}
                      onChange={(e) =>
                        setVCfg({ ...vCfg, outputFormat: e.target.value as 'Strict JSON' | 'Structured Text' })
                      }
                      className="w-full px-2 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                    >
                      <option value="Strict JSON">Strict JSON</option>
                      <option value="Structured Text">Structured Text</option>
                    </select>
                  </div>
                </div>

                {/* Headword Stage-1 Toggle + Exclusion */}
                <div
                  className="p-2.5 rounded-lg border space-y-2"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
                >
                  <label className="flex items-center justify-between gap-2 cursor-pointer font-bold">
                    <span className="flex items-center gap-1.5" style={{ color: colors.inkBold }}>
                      <ListChecks className="w-3.5 h-3.5" style={{ color: colors.teal }} />
                      Stage-1 Headword Index-Only Mode
                    </span>
                    <input
                      type="checkbox"
                      checked={vCfg.isHeadwordIndexOnly}
                      onChange={(e) => setVCfg({ ...vCfg, isHeadwordIndexOnly: e.target.checked })}
                    />
                  </label>
                  {vCfg.isHeadwordIndexOnly && (
                    <select
                      value={vCfg.utilityTier}
                      onChange={(e) => setVCfg({ ...vCfg, utilityTier: e.target.value as VocabularyUtilityTier })}
                      className="w-full px-2 py-1 rounded border"
                      style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                    >
                      <option value="EXAM_EDITORIAL">Competitive Exam Editorial Tier</option>
                      <option value="DAILY_HIGH_FREQUENCY">Daily High-Frequency Tier</option>
                      <option value="LITERARY_PRECISION">Literary & Formal Precision Tier</option>
                      <option value="GENERAL_BALANCED">Balanced High-Yield Mix</option>
                    </select>
                  )}

                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={vCfg.excludeAlreadySavedWords}
                      onChange={(e) => setVCfg({ ...vCfg, excludeAlreadySavedWords: e.target.checked })}
                    />
                    <span style={{ color: colors.ink }}>
                      Exclude {savedWordsForTargetLang.length} already-saved {vCfg.language} word(s)
                    </span>
                  </label>
                </div>

                {!vCfg.isHeadwordIndexOnly && (
                  <div className="grid grid-cols-2 gap-2">
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={vCfg.includePronunciation}
                        onChange={(e) => setVCfg({ ...vCfg, includePronunciation: e.target.checked })}
                      />
                      <span style={{ color: colors.ink }}>IPA / Reading</span>
                    </label>
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={vCfg.includeSynonymsAndCollocations}
                        onChange={(e) => setVCfg({ ...vCfg, includeSynonymsAndCollocations: e.target.checked })}
                      />
                      <span style={{ color: colors.ink }}>Synonyms &amp; Collocations</span>
                    </label>
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={vCfg.includeUsageNote}
                        onChange={(e) => setVCfg({ ...vCfg, includeUsageNote: e.target.checked })}
                      />
                      <span style={{ color: colors.ink }}>Usage / Exam Traps</span>
                    </label>
                  </div>
                )}
              </div>
            )}
          </div>

          <div
            className="rounded-xl p-4 border text-xs space-y-1.5"
            style={{ backgroundColor: colors.tealLight, borderColor: colors.border, color: colors.ink }}
          >
            <div className="font-bold flex items-center gap-1.5" style={{ color: colors.tealDark }}>
              <BookOpen className="w-4 h-4" />
              Next Step After Running in AI
            </div>
            <p className="leading-relaxed">
              {mode === 'QUESTION_SET' && (
                <>
                  Paste the AI output into{' '}
                  <button onClick={() => onNavigateToCreator?.()} className="underline font-bold" style={{ color: colors.tealDark }}>
                    Exam Workbench
                  </button>{' '}
                  or the Paper Importer.
                </>
              )}
              {(mode === 'KNOWLEDGE_SET' || mode === 'FLASHCARD_SET') && (
                <>
                  Paste the structured markdown into{' '}
                  <button onClick={() => onNavigateToKnowledge?.()} className="underline font-bold" style={{ color: colors.tealDark }}>
                    Knowledge Studio
                  </button>{' '}
                  for Active Recall masking, Dictation, or SM-2 review.
                </>
              )}
              {mode === 'VOCABULARY' && (
                <>
                  Paste the result into{' '}
                  <button
                    onClick={() => (vCfg.isHeadwordIndexOnly ? onOpenHeadwordInbox?.() : onOpenVocabImport?.())}
                    className="underline font-bold"
                    style={{ color: colors.tealDark }}
                  >
                    {vCfg.isHeadwordIndexOnly ? 'Headword Inbox' : 'Vocabulary Hub Importer'}
                  </button>{' '}
                  for deduplicated storage &amp; SM-2 training.
                </>
              )}
            </p>
          </div>
        </div>

        {/* Right Output Box */}
        <div className="lg:col-span-7 min-w-0">
          <div className="bg-slate-900 rounded-xl p-4 sm:p-5 shadow-lg border border-slate-800 flex flex-col h-full min-h-[300px] lg:min-h-[500px]">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-3 text-xs text-slate-400">
              <span className="font-mono text-slate-200 flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-400" />
                Ready-to-Run LLM Prompt ({mode.replace('_', ' ')})
              </span>
              <span>{generatedPrompt.length} chars</span>
            </div>

            <textarea
              readOnly
              value={generatedPrompt}
              className="flex-1 w-full bg-transparent text-slate-200 font-mono text-xs leading-relaxed border-0 resize-none focus:outline-none"
            />

            <div className="pt-4 border-t border-slate-800 flex items-center justify-between">
              <span className="text-[11px] text-slate-400">
                Engineered for zero-drift offline parsing
              </span>
              <button
                type="button"
                onClick={handleCopyPrompt}
                className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold bg-teal-600 hover:bg-teal-500 text-white rounded-lg transition shadow"
              >
                {copied ? <Check className="w-4 h-4 text-emerald-200" /> : <Copy className="w-4 h-4" />}
                <span>{copied ? 'Copied' : 'Copy Prompt'}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PromptGeneratorTab;
