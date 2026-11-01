import React, { useState, useMemo } from 'react';
import { 
  Sparkles, 
  Copy, 
  Check, 
  RefreshCw, 
  FileText, 
  CheckCircle2, 
  ArrowRight,
  Sliders,
  BookOpen
} from 'lucide-react';

interface PromptGeneratorTabProps {
  onSendToCreator?: (text: string) => void;
  onNavigateToCreator?: () => void;
}

export const PromptGeneratorTab: React.FC<PromptGeneratorTabProps> = ({
  onSendToCreator,
  onNavigateToCreator
}) => {
  const [examType, setExamType] = useState<string>('UPSC Civil Services Examination');
  const [subject, setSubject] = useState<string>('General Science & Environment');
  const [topic, setTopic] = useState<string>('Ecology, Biodiversity, Climate Change & Green Energy');
  const [questionCount, setQuestionCount] = useState<number>(20);
  const [difficulty, setDifficulty] = useState<'Standard' | 'Hard' | 'Mixed' | 'Assertion-Reason'>('Mixed');
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

  // Dynamically constructed AI prompt
  const generatedPrompt = useMemo(() => {
    const qLang = questionLanguage.trim() || 'English';

    if (targetFormat === 'JSON') {
      if (promptDensity === 'Concise') {
        return `Generate exactly ${questionCount} multiple-choice questions (MCQs) for ${examType} at ${difficulty} standard on ${subject}${topic ? ` (${topic})` : ''} in ${qLang}.
Output ONLY a strict, valid JSON array of objects without markdown fences, comments, or extra text. Format:
[
  {
    "num": 1,
    "question": "Question text...",
    "options": {
      "a": "Option A text",
      "b": "Option B text",
      "c": "Option C text",
      "d": "Option D text"
    },
    "correctAnswer": "A",
    "explanation": "Concise reasoning in ${effectiveExplanationLang}..."
  }
]
Requirements:
1. Exactly 4 mutually exclusive options (A, B, C, D).
2. "correctAnswer" must be a single uppercase letter "A", "B", "C", or "D".
3. Valid JSON only. Do not truncate.`;
      } else {
        return `Generate exactly ${questionCount} multiple-choice questions (MCQs) at ${difficulty} difficulty standard on ${subject}${topic ? ` (Focus Topics: ${topic})` : ''}, tailored specifically for ${examType} preparation in ${qLang}.
Output MUST be an unbroken, valid JSON array of objects without markdown fences or extraneous text:
[
  {
    "num": 1,
    "question": "Question text...",
    "options": {
      "a": "Option A text",
      "b": "Option B text",
      "c": "Option C text",
      "d": "Option D text"
    },
    "correctAnswer": "A",
    "explanation": "Core principle and breakdown of why A is right and distractors are wrong in ${effectiveExplanationLang}."
  }
]
Strict Rules:
- Universal Options: Exactly four distinct options: a, b, c, and d.
- Balanced Key Distribution: Distribute answers evenly across A, B, C, and D.
- Return raw JSON only so it parses immediately without preprocessing.`;
      }
    }

    if (targetFormat === 'Table') {
      return `Generate exactly ${questionCount} multiple-choice questions (MCQs) on ${subject}${topic ? ` (${topic})` : ''} for ${examType} at ${difficulty} standard in ${qLang}.
Output MUST be formatted as a Markdown Pipe Table with these exact column headers:
| Question | Option A | Option B | Option C | Option D | Answer | Explanation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Sample question text? | Choice A | Choice B | Choice C | Choice D | A | Detailed rationale in ${effectiveExplanationLang} |

Ensure each question is on its own row, with Answer being a single letter (A, B, C, or D).`;
    }

    // Default: Standard Text / Aiken
    if (promptDensity === 'Concise') {
      return `Generate ${questionCount} MCQs for ${examType} on ${subject}${topic ? ` (${topic})` : ''} in ${qLang}.
Output format:
Q1. Question text
(A) Option A
(B) Option B
(C) Option C
(D) Option D
ANSWER: A
EXPLANATION: Solution in ${effectiveExplanationLang}

(Repeat for all ${questionCount} questions sequentially)`;
    }

    return `Generate exactly ${questionCount} multiple-choice questions (MCQs) at ${difficulty} difficulty standard on ${subject}${topic ? ` (Focus Topics: ${topic})` : ''}, tailored specifically for ${examType} preparation, based on the latest authoritative syllabus and examination pattern.

Language & Notation Specification:
- Questions and options must be written entirely in ${qLang}.
- Explanations must be written in ${effectiveExplanationLang}.
- Universal Option Convention: Label options strictly as (A), (B), (C), and (D) across all languages.
- Plain Text Anti-List & Zero-Indentation: Every single line must start flush-left at the margin.

## Phase 1: Questions
1. Provide Questions 1 through ${questionCount} first, labeled sequentially as Q1., Q2. through Q${questionCount}.
2. Every question must have exactly four options: (A), (B), (C), and (D).

## Phase 2: Answer Key
Immediately after Question ${questionCount}, output the answer key:
## Answer Key
Q1: (C)
Q2: (A)
...

${includeExplanations ? `## Phase 3: Detailed Explanations
## Detailed Explanation
For each question Q1 to Q${questionCount}:
Q1.
Core Concept & Principle: [Explanation]
Option (A) [Correct/Incorrect]: [Reasoning]
Option (B) [Correct/Incorrect]: [Reasoning]
Option (C) [Correct/Incorrect]: [Reasoning]
Option (D) [Correct/Incorrect]: [Reasoning]
Memory Anchor: [Key mnemonic/rule]
` : ''}
Execute the instructions now, starting directly with Phase 1.`;
  }, [examType, subject, topic, questionCount, difficulty, questionLanguage, effectiveExplanationLang, includeExplanations, targetFormat, promptDensity]);

  const handleCopyPrompt = () => {
    navigator.clipboard.writeText(generatedPrompt);
    setCopied(true);
    setTimeout(() => setCopied(false), 2400);
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50 p-4 rounded-xl border border-slate-200">
        <div>
          <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-amber-500" />
            AI Prompt Builder for ExamSet Parser
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Architect strict, zero-drift prompts for ChatGPT, Gemini, or Claude to generate exam sets formatted perfectly for instant 1-click import.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleCopyPrompt}
            className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition shadow-sm"
          >
            {copied ? <Check className="w-4 h-4 text-emerald-300" /> : <Copy className="w-4 h-4" />}
            <span>{copied ? 'Copied Prompt!' : 'Copy AI Prompt'}</span>
          </button>
        </div>
      </div>

      {/* Main Grid: Parameters on Left, Generated Prompt on Right */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Form Controls */}
        <div className="lg:col-span-5 space-y-4">
          <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-sm space-y-4">
            <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
              <Sliders className="w-3.5 h-3.5" />
              Syllabus & Blueprint Parameters
            </h4>

            <div className="space-y-3.5 text-xs">
              <div>
                <label className="block text-slate-700 font-semibold mb-1">Target Examination</label>
                <select
                  value={examType}
                  onChange={(e) => setExamType(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500 text-slate-800"
                >
                  <option value="UPSC Civil Services Examination">UPSC Civil Services Examination</option>
                  <option value="SSC Combined Graduate Level (CGL)">SSC Combined Graduate Level (CGL)</option>
                  <option value="National Eligibility cum Entrance Test (NEET)">NEET Medical Entrance</option>
                  <option value="JEE Main & Advanced">JEE Engineering Entrance</option>
                  <option value="State Public Service Commission (PSC)">State PSC Exam</option>
                  <option value="Banking Probationary Officer (IBPS PO / SBI)">Banking PO / Clerk</option>
                  <option value="Central Board of Secondary Education (CBSE Class 12)">CBSE Class 12 Board</option>
                  <option value="All India National Talent Search / Olympiad">Olympiad & Talent Search</option>
                </select>
              </div>

              <div>
                <label className="block text-slate-700 font-semibold mb-1">Subject</label>
                <input
                  type="text"
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  placeholder="e.g. Modern Indian History"
                  className="w-full px-3 py-1.5 border border-slate-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-slate-700 font-semibold mb-1">Specific Topics & Sub-themes</label>
                <textarea
                  value={topic}
                  onChange={(e) => setTopic(e.target.value)}
                  rows={2}
                  placeholder="e.g. Revolt of 1857, Socio-religious reform movements..."
                  className="w-full px-3 py-1.5 border border-slate-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>

              {/* Target Format & Density Selector */}
              <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 space-y-2.5">
                <div className="flex items-center justify-between">
                  <label className="text-slate-700 font-semibold">Target Output Format</label>
                  <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-blue-100 text-blue-800">{targetFormat}</span>
                </div>
                <div className="grid grid-cols-3 gap-1.5">
                  {(['JSON', 'Text', 'Table'] as const).map((fmt) => (
                    <button
                      key={fmt}
                      type="button"
                      onClick={() => setTargetFormat(fmt)}
                      className={`px-2 py-1.5 rounded-md font-semibold text-xs border transition ${
                        targetFormat === fmt
                          ? 'bg-blue-600 text-white border-blue-600 shadow-sm'
                          : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-100'
                      }`}
                    >
                      {fmt === 'JSON' ? '{ } JSON' : fmt === 'Table' ? '⊞ Table' : '¶ Text'}
                    </button>
                  ))}
                </div>

                <div className="flex items-center justify-between pt-1 border-t border-slate-200">
                  <span className="text-slate-600 text-[11px] font-medium">Prompt Length:</span>
                  <div className="flex gap-1">
                    {(['Concise', 'Comprehensive'] as const).map((d) => (
                      <button
                        key={d}
                        type="button"
                        onClick={() => setPromptDensity(d)}
                        className={`px-2 py-0.5 rounded text-[10px] font-medium transition ${
                          promptDensity === d
                            ? 'bg-slate-800 text-white'
                            : 'bg-slate-200 text-slate-600 hover:bg-slate-300'
                        }`}
                      >
                        {d === 'Concise' ? 'Concise (Fast)' : 'Detailed'}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-slate-700 font-semibold mb-1">Question Count</label>
                  <select
                    value={questionCount}
                    onChange={(e) => setQuestionCount(Number(e.target.value))}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500 text-slate-800"
                  >
                    <option value={10}>10 Questions</option>
                    <option value={20}>20 Questions</option>
                    <option value={30}>30 Questions</option>
                    <option value={50}>50 Questions</option>
                    <option value={100}>100 Questions</option>
                  </select>
                </div>

                <div>
                  <label className="block text-slate-700 font-semibold mb-1">Difficulty</label>
                  <select
                    value={difficulty}
                    onChange={(e) => setDifficulty(e.target.value as any)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500 text-slate-800"
                  >
                    <option value="Mixed">Mixed Standard</option>
                    <option value="Standard">Standard / Moderate</option>
                    <option value="Hard">Advanced / Hard</option>
                    <option value="Assertion-Reason">Assertion-Reason Pattern</option>
                  </select>
                </div>
              </div>

              {/* Language Configuration */}
              <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 space-y-3">
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="text-slate-700 font-semibold">Question Language</label>
                    <span className="text-[10px] text-blue-600 font-medium">Any language supported</span>
                  </div>
                  <input
                    type="text"
                    value={questionLanguage}
                    onChange={(e) => setQuestionLanguage(e.target.value)}
                    placeholder="e.g. English, Hindi, Tamil, Bengali, French, German..."
                    className="w-full px-3 py-1.5 bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500 text-slate-800 text-xs"
                  />
                  <div className="flex flex-wrap gap-1 mt-1.5">
                    {['English', 'Hindi', 'Bengali', 'Marathi', 'Tamil', 'Telugu', 'Gujarati', 'Urdu', 'Spanish', 'French'].map((lng) => (
                      <button
                        key={lng}
                        type="button"
                        onClick={() => setQuestionLanguage(lng)}
                        className={`px-1.5 py-0.5 rounded text-[10px] border transition ${
                          questionLanguage.toLowerCase() === lng.toLowerCase()
                            ? 'bg-blue-600 text-white border-blue-600'
                            : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-100'
                        }`}
                      >
                        {lng}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3 pt-1 border-t border-slate-200">
                  <div>
                    <label className="block text-slate-700 font-semibold mb-1">Explanation Language</label>
                    <select
                      value={explanationChoice}
                      onChange={(e) => setExplanationChoice(e.target.value)}
                      className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500 text-slate-800 text-xs"
                    >
                      <option value="Same as Question">Same as Question</option>
                      <option value="English">English</option>
                      <option value="Hindi">Hindi</option>
                      <option value="Bilingual">
                        {questionLanguage.toLowerCase() === 'english' ? 'Bilingual (Eng & Hindi)' : `Bilingual (Eng & ${questionLanguage || 'Regional'})`}
                      </option>
                      <option value="Custom">Custom...</option>
                    </select>

                    {explanationChoice === 'Custom' && (
                      <input
                        type="text"
                        value={customExplanationLang}
                        onChange={(e) => setCustomExplanationLang(e.target.value)}
                        placeholder="Type custom explanation language..."
                        className="w-full mt-1.5 px-2.5 py-1 bg-white border border-slate-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500 text-slate-800 text-[11px]"
                      />
                    )}
                  </div>

                  <div className="flex flex-col justify-end">
                    <label className="flex items-center gap-2 cursor-pointer pb-2">
                      <input
                        type="checkbox"
                        checked={includeExplanations}
                        onChange={(e) => setIncludeExplanations(e.target.checked)}
                        className="rounded text-blue-600 focus:ring-0"
                      />
                      <span className="text-slate-700 font-medium">Include Solutions</span>
                    </label>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Quick Guide Card */}
          <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 text-xs text-blue-900 space-y-2">
            <h5 className="font-bold text-blue-950 flex items-center gap-1.5">
              <BookOpen className="w-4 h-4 text-blue-600" />
              Recommended Workflow:
            </h5>
            <ol className="list-decimal pl-4 space-y-1 text-blue-800/90 leading-relaxed">
              <li>Click <strong>Copy AI Prompt</strong> above.</li>
              <li>Paste into <strong>Gemini</strong>, <strong>ChatGPT</strong>, or <strong>Claude</strong>.</li>
              <li>Copy the LLM’s response text.</li>
              <li>Paste directly into the <strong>Question Creator</strong> tab to generate your test and single-sheet OMR!</li>
            </ol>
          </div>
        </div>

        {/* Right Output Text Box */}
        <div className="lg:col-span-7">
          <div className="bg-slate-900 rounded-xl p-5 shadow-lg border border-slate-800 flex flex-col h-full min-h-[480px]">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800 mb-3 text-xs text-slate-400">
              <span className="font-mono text-slate-300 flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
                Ready-to-Run LLM Prompt
              </span>
              <span>{generatedPrompt.length} chars</span>
            </div>

            <textarea
              readOnly
              value={generatedPrompt}
              className="flex-1 w-full bg-transparent text-slate-200 font-mono text-xs leading-relaxed border-0 resize-none focus:outline-none focus:ring-0"
            />

            <div className="pt-4 border-t border-slate-800 flex items-center justify-between">
              <span className="text-[11px] text-slate-400">
                Guaranteed zero-drift structure for ExamSet Parser
              </span>

              <button
                onClick={handleCopyPrompt}
                className="flex items-center gap-1.5 px-4 py-2 text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition shadow"
              >
                {copied ? <Check className="w-4 h-4 text-emerald-300" /> : <Copy className="w-4 h-4" />}
                <span>{copied ? 'Copied to Clipboard' : 'Copy Prompt'}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PromptGeneratorTab;
