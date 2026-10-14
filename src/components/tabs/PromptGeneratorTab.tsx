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
    return `Generate exactly ${questionCount} multiple-choice questions (MCQs) at ${difficulty} difficulty standard on ${subject}${topic ? ` (Focus Topics: ${topic})` : ''}, tailored specifically for ${examType} preparation, based on the latest authoritative syllabus and examination pattern.

Language & Notation Specification:
- Questions and options must be written entirely in ${qLang}.
- Explanations must be written in ${effectiveExplanationLang}.
- Universal Option Convention: Label options strictly as (A), (B), (C), and (D) across all languages to ensure uniform structural formatting.
- Plain Text Anti-List & Zero-Indentation: Every single line must start flush-left at the margin (Column 0). Never use bullet points (•, *), list hyphens (-), blockquotes (>), or markdown table alignments for options. Never use markdown numbered lists like '1. ' so copying into Microsoft Word or Google Docs never triggers auto-lists.

Execution Directives:
1. Start directly with Phase 1 without any preamble, personalization, greetings, corrections, or introductory remarks.
2. Output all phases in strict sequential order: Phase 1 (Questions) -> Phase 2 (Answer Key) -> Phase 3 (Detailed Explanations).
3. After completing Phase 3, do NOT ask follow-up questions, request additional information, suggest further tasks, or add any closing remarks.
4. No Markdown Code Fences: Do not wrap the output in ``` blocks.

## Phase 1: Questions
1. Provide Questions 1 through ${questionCount} first, labeled sequentially as Q1., Q2. through Q${questionCount}.
2. Every question must have exactly four options: (A), (B), (C), and (D), each on its own fresh line flush-left with exactly one space: '(A) [Option text]'.
3. Present ALL questions first without revealing answers, hints, explanations, or clues that make the correct option easy to guess.
4. Option Quality & Anti-Bias Constraints:
   - Ensure all four options are distinct, academically plausible, and mutually exclusive.
   - Keep all four options comparable in length and grammatical structure to eliminate length bias (the correct answer must NOT be noticeably longer or more detailed than distractors).
   - Strictly avoid 'All of the above', 'None of the above', repeated options, or obviously absurd distractors.
   - For numerical values, years, or quantities, arrange options in strictly ascending or descending numerical order.
   - Maintain a balanced distribution of correct answers across (A), (B), (C), and (D) without predictable patterns.

## Phase 2: Answer Key
Immediately after Question ${questionCount}, output the answer key in this exact format:

## Answer Key
Q1: (C)
Q2: (A)
Q3: (D)
...
Q${questionCount}: (B)

Do NOT provide any explanations, comments, or notes in Phase 2.

${includeExplanations ? `## Phase 3: Detailed Explanations
Immediately after the Answer Key, create:

## Detailed Explanation

Requirements for Phase 3:
- Explanations must be written in ${effectiveExplanationLang}.
- Do NOT repeat the correct answer as a separate standalone line (e.g., do NOT write 'Correct Answer: (B)'), since it is already provided in Phase 2.
- Do NOT use standalone markdown horizontal divider lines (---) anywhere in the explanations.
- Synchronization: Every question's explanation must strictly match the exact correct option letter specified in Phase 2.
- Keep all explanations concise, precise, analytical, non-repetitive, and easy to remember.
- Avoid unnecessary filler background information; include only what is necessary to understand the reasoning and distinguish the options.
- All lines must be flush-left with zero indentation and NO bullet points.

For each question (numbered sequentially Q1 to Q${questionCount}), follow this exact structure:

Q1.
Core Concept & Principle: [Concise, conceptually complete explanation of the underlying governing concept, theory, formula, or law]
Option (A) [Correct/Incorrect]: [Brief, non-repetitive explanation stating why this option is correct or incorrect, and the key distinction]
Option (B) [Correct/Incorrect]: [Why this option is correct or incorrect]
Option (C) [Correct/Incorrect]: [Why this option is correct or incorrect]
Option (D) [Correct/Incorrect]: [Why this option is correct or incorrect]
Memory Anchor: [Highlight the essential fact, formula, distinction, or rule needed to remember the answer]

Explanation Rules:
- Explain ALL FOUR options individually. Never skip an option.
- For every incorrect option, explain WHY it is not correct and what concept it actually represents.
- Never simply write 'Incorrect' or 'This is false'. Every option must have a meaningful pedagogical explanation.

` : ''}## Phase 4: Complete Unbroken Generation
1. Generate all ${questionCount} questions sequentially from 1 to ${questionCount} in one continuous, complete response.
2. Do NOT stop early, summarize, omit numbers, or split into artificial batches.
3. Strictly deliver all ${questionCount} questions first in Phase 1, then the full Answer Key in Phase 2${includeExplanations ? `, then the Detailed Explanations for all ${questionCount} questions in Phase 3` : ''}.

Execute the instructions now, starting directly with Phase 1.`;
  }, [examType, subject, topic, questionCount, difficulty, questionLanguage, effectiveExplanationLang, includeExplanations]);

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
