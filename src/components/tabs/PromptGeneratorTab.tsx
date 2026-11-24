import React, { useMemo, useState } from 'react';
import {
  ArrowRight,
  BookOpen,
  Check,
  Copy,
  Sliders,
  Sparkles,
} from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';

interface PromptGeneratorTabProps {
  onSendToCreator?: (text: string) => void;
  onNavigateToCreator?: () => void;
}

export const PromptGeneratorTab: React.FC<PromptGeneratorTabProps> = ({
  onSendToCreator,
  onNavigateToCreator,
}) => {
  const { colors } = useTheme();
  const [examType, setExamType] = useState<string>('UPSC Civil Services Examination');
  const [subject, setSubject] = useState<string>('General Science & Environment');
  const [topic, setTopic] = useState<string>('Ecology, Biodiversity, Climate Change & Green Energy');
  const [questionCount, setQuestionCount] = useState<number>(20);
  const [difficulty, setDifficulty] = useState<'Standard' | 'Hard' | 'Mixed' | 'Assertion-Reason' | 'MSQ'>('Mixed');
  const [optionMode, setOptionMode] = useState<4 | 5>(4);
  const [questionLanguage, setQuestionLanguage] = useState<string>('English');
  const [explanationChoice, setExplanationChoice] = useState<string>('Same as Question');
  const [customExplanationLang, setCustomExplanationLang] = useState<string>('');
  const [includeExplanations, setIncludeExplanations] = useState<boolean>(true);
  const [targetFormat, setTargetFormat] = useState<'JSON' | 'Text' | 'Table'>('JSON');
  const [promptDensity, setPromptDensity] = useState<'Concise' | 'Comprehensive'>('Concise');
  const [copied, setCopied] = useState<boolean>(false);

  const effectiveExplanationLang = useMemo(() => {
    const q = questionLanguage.trim() || 'English';
    switch (explanationChoice) {
      case 'Same as Question':
        return `${q} (strictly matching question language)`;
      case 'Bilingual':
        return q.toLowerCase() === 'english' ? 'Bilingual (English & Hindi)' : `Bilingual (English & ${q})`;
      case 'English':
        return 'English';
      case 'Hindi':
        return 'Hindi';
      case 'Custom':
        return customExplanationLang.trim() || q;
      default:
        return explanationChoice;
    }
  }, [questionLanguage, explanationChoice, customExplanationLang]);

  const generatedPrompt = useMemo(() => {
    const qLang = questionLanguage.trim() || 'English';
    const isMsq = difficulty === 'MSQ';
    const optRule =
      optionMode === 5
        ? 'Include 5 mutually exclusive options (a, b, c, d, e).'
        : 'STRICT 4-OPTION CONSTRAINT: Exactly 4 mutually exclusive options (a, b, c, d). Never include option E.';
    const keyRule = isMsq
      ? '"correctAnswer" may contain comma-separated keys like "A,C" for Multiple-Select (MSQ) items.'
      : `"correctAnswer" must be a single uppercase letter ${optionMode === 5 ? '"A".."E"' : '"A".."D"'}.`;

    if (targetFormat === 'JSON') {
      const optEJson = optionMode === 5 ? ',\n      "e": "Option E text"' : '';
      if (promptDensity === 'Concise') {
        return `Generate exactly ${questionCount} multiple-choice questions for ${examType} at ${difficulty} standard on ${subject}${topic ? ` (${topic})` : ''} in ${qLang}.
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
3. Valid JSON only. Do not truncate.`;
      }

      return `Generate exactly ${questionCount} rigorous questions at ${difficulty} difficulty standard on ${subject}${topic ? ` (Focus Topics: ${topic})` : ''}, tailored for ${examType} in ${qLang}.
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
- Balance answer key distribution evenly across options.
- Return raw JSON only so it parses immediately.`;
    }

    if (targetFormat === 'Table') {
      const colEHeader = optionMode === 5 ? ' Option E |' : '';
      const colESep = optionMode === 5 ? ' :--- |' : '';
      const colEVal = optionMode === 5 ? ' Choice E |' : '';
      return `Generate exactly ${questionCount} questions on ${subject}${topic ? ` (${topic})` : ''} for ${examType} at ${difficulty} standard in ${qLang}.
Output MUST be a Markdown Pipe Table with these exact columns:
| Question | Option A | Option B | Option C | Option D |${colEHeader} Answer | Explanation |
| :--- | :--- | :--- | :--- | :--- |${colESep} :--- | :--- |
| Sample question text? | Choice A | Choice B | Choice C | Choice D |${colEVal} ${isMsq ? 'A,C' : 'A'} | Rationale in ${effectiveExplanationLang} |

Rules:
1. ${optRule}
2. ${keyRule}`;
    }

    const optELine = optionMode === 5 ? '\n(E) Option E' : '';
    if (promptDensity === 'Concise') {
      return `Generate ${questionCount} questions for ${examType} on ${subject}${topic ? ` (${topic})` : ''} in ${qLang}.
Output format:
Q1. Question text
(A) Option A
(B) Option B
(C) Option C
(D) Option D${optELine}
ANSWER: ${isMsq ? 'A,C' : 'A'}
EXPLANATION: Solution in ${effectiveExplanationLang}

(Repeat sequentially for Q1 through Q${questionCount})`;
    }

    return `Generate exactly ${questionCount} questions at ${difficulty} standard on ${subject}${topic ? ` (${topic})` : ''} for ${examType} in ${qLang}.

## Phase 1: Questions
Label sequentially Q1. through Q${questionCount}. Each with options (A) through (${optionMode === 5 ? 'E' : 'D'}).
Every line must start flush-left.

## Phase 2: Answer Key
Q1: (${isMsq ? 'A,C' : 'C'})
...

${
  includeExplanations
    ? `## Phase 3: Detailed Explanations
For each question Q1 to Q${questionCount}, explain core concept and distractors in ${effectiveExplanationLang}.`
    : ''
}`;
  }, [
    examType,
    subject,
    topic,
    questionCount,
    difficulty,
    optionMode,
    questionLanguage,
    effectiveExplanationLang,
    includeExplanations,
    targetFormat,
    promptDensity,
  ]);

  const handleCopyPrompt = () => {
    navigator.clipboard?.writeText(generatedPrompt);
    setCopied(true);
    setTimeout(() => setCopied(false), 2400);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div
        className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-4 rounded-xl border"
        style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
      >
        <div>
          <h3 className="text-base font-bold flex items-center gap-2" style={{ color: colors.inkBold }}>
            <Sparkles className="w-5 h-5" style={{ color: colors.amber }} />
            AI Prompt Architect for ExamSet Parser
          </h3>
          <p className="text-xs mt-0.5" style={{ color: colors.muted }}>
            Build zero-drift prompts supporting 4/5 options, MSQ, Assertion–Reason, and multilingual rationales for instant import.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {onSendToCreator && (
            <button
              onClick={() => onSendToCreator(generatedPrompt)}
              className="flex items-center gap-1.5 px-3.5 py-2 text-xs font-bold rounded-lg border transition"
              style={{ backgroundColor: colors.tealLight, color: colors.tealDark, borderColor: colors.teal }}
            >
              <ArrowRight className="w-3.5 h-3.5" />
              New Set with Prompt
            </button>
          )}
          <button
            onClick={handleCopyPrompt}
            className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold rounded-lg shadow-sm transition"
            style={{ backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText }}
          >
            {copied ? <Check className="w-4 h-4 text-emerald-300" /> : <Copy className="w-4 h-4" />}
            <span>{copied ? 'Copied Prompt!' : 'Copy AI Prompt'}</span>
          </button>
        </div>
      </div>

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-5 space-y-4">
          <div
            className="rounded-xl border p-5 space-y-4 text-xs"
            style={{ backgroundColor: colors.surface, borderColor: colors.border }}
          >
            <h4 className="font-bold uppercase tracking-wider flex items-center gap-1.5" style={{ color: colors.muted }}>
              <Sliders className="w-3.5 h-3.5" />
              Syllabus &amp; Blueprint Parameters
            </h4>

            <div className="space-y-3">
              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                  Target Examination
                </label>
                <select
                  value={examType}
                  onChange={(e) => setExamType(e.target.value)}
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
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  className="w-full px-3 py-1.5 rounded-lg border"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>

              <div>
                <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                  Specific Topics
                </label>
                <textarea
                  value={topic}
                  onChange={(e) => setTopic(e.target.value)}
                  rows={2}
                  className="w-full px-3 py-1.5 rounded-lg border"
                  style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                />
              </div>

              {/* Format + Density */}
              <div
                className="p-3 rounded-lg border space-y-2.5"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                <div className="flex items-center justify-between">
                  <span className="font-semibold" style={{ color: colors.ink }}>Target Format</span>
                  <div className="flex gap-1">
                    {([4, 5] as const).map((optN) => (
                      <button
                        key={optN}
                        type="button"
                        onClick={() => setOptionMode(optN)}
                        className="px-2 py-0.5 rounded text-[10px] font-bold border"
                        style={
                          optionMode === optN
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
                      onClick={() => setTargetFormat(fmt)}
                      className="px-2 py-1.5 rounded-md font-bold border transition"
                      style={
                        targetFormat === fmt
                          ? { backgroundColor: colors.primaryButtonBg, color: colors.primaryButtonText, borderColor: colors.primaryButtonBg }
                          : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
                      }
                    >
                      {fmt}
                    </button>
                  ))}
                </div>

                <div className="flex items-center justify-between pt-1 border-t" style={{ borderColor: colors.borderLight }}>
                  <span className="text-[11px]" style={{ color: colors.muted }}>Prompt Length:</span>
                  <div className="flex gap-1">
                    {(['Concise', 'Comprehensive'] as const).map((d) => (
                      <button
                        key={d}
                        type="button"
                        onClick={() => setPromptDensity(d)}
                        className="px-2 py-0.5 rounded text-[10px] font-bold"
                        style={
                          promptDensity === d
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
                    value={questionCount}
                    onChange={(e) => setQuestionCount(Number(e.target.value))}
                    className="w-full px-3 py-2 rounded-lg border"
                    style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border, color: colors.inkBold }}
                  >
                    {[10, 20, 30, 50, 100].map((n) => (
                      <option key={n} value={n}>{n} Questions</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block font-semibold mb-1" style={{ color: colors.ink }}>
                    Difficulty / Style
                  </label>
                  <select
                    value={difficulty}
                    onChange={(e) => setDifficulty(e.target.value as any)}
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

              {/* Language */}
              <div
                className="p-3 rounded-lg border space-y-2.5"
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
              >
                <label className="block font-semibold" style={{ color: colors.ink }}>
                  Question Language
                </label>
                <input
                  type="text"
                  value={questionLanguage}
                  onChange={(e) => setQuestionLanguage(e.target.value)}
                  className="w-full px-3 py-1.5 rounded-lg border"
                  style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                />
                <div className="flex flex-wrap gap-1">
                  {['English', 'Hindi', 'Bengali', 'Marathi', 'Tamil', 'Korean', 'French'].map((lng) => (
                    <button
                      key={lng}
                      type="button"
                      onClick={() => setQuestionLanguage(lng)}
                      className="px-1.5 py-0.5 rounded text-[10px] border font-semibold"
                      style={
                        questionLanguage.toLowerCase() === lng.toLowerCase()
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
                      value={explanationChoice}
                      onChange={(e) => setExplanationChoice(e.target.value)}
                      className="w-full px-2 py-1.5 rounded-lg border"
                      style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                    >
                      <option value="Same as Question">Same as Question</option>
                      <option value="English">English</option>
                      <option value="Hindi">Hindi</option>
                      <option value="Bilingual">Bilingual</option>
                      <option value="Custom">Custom...</option>
                    </select>
                    {explanationChoice === 'Custom' && (
                      <input
                        type="text"
                        value={customExplanationLang}
                        onChange={(e) => setCustomExplanationLang(e.target.value)}
                        placeholder="Language..."
                        className="w-full mt-1 px-2 py-1 rounded border"
                        style={{ backgroundColor: colors.surface, borderColor: colors.border, color: colors.inkBold }}
                      />
                    )}
                  </div>

                  <div className="flex items-end pb-1">
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={includeExplanations}
                        onChange={(e) => setIncludeExplanations(e.target.checked)}
                      />
                      <span className="font-semibold" style={{ color: colors.ink }}>Include Solutions</span>
                    </label>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div
            className="rounded-xl p-4 border text-xs space-y-1.5"
            style={{ backgroundColor: colors.tealLight, borderColor: colors.border, color: colors.ink }}
          >
            <div className="font-bold flex items-center gap-1.5" style={{ color: colors.tealDark }}>
              <BookOpen className="w-4 h-4" />
              Recommended Workflow
            </div>
            <p className="leading-relaxed">
              Copy this prompt into your AI workspace, then paste the returned JSON or text into{' '}
              <button
                onClick={() => onNavigateToCreator?.()}
                className="underline font-bold"
                style={{ color: colors.tealDark }}
              >
                Question Creator
              </button>{' '}
              or the Batch Importer.
            </p>
          </div>
        </div>

        {/* Right Output Box */}
        <div className="lg:col-span-7">
          <div className="bg-slate-900 rounded-xl p-5 shadow-lg border border-slate-800 flex flex-col h-full min-h-[480px]">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-3 text-xs text-slate-400">
              <span className="font-mono text-slate-200 flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-400" />
                Ready-to-Run LLM Prompt
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
                Engineered for McqParser v2 (MSQ + 5-Option + Bilingual)
              </span>
              <button
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
