import React, { useState, useEffect, useTransition } from 'react';
import { 
  FileText, 
  Trash2, 
  Clipboard, 
  Maximize2, 
  Minimize2, 
  CheckCircle, 
  AlertCircle, 
  Sparkles, 
  ArrowRight, 
  Info, 
  Layers, 
  Save, 
  Check, 
  RotateCw,
  Layout,
  List,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import { QuestionPaper, ParseResult } from '../../types/exam';
import { McqParser } from '../../utils/mcqParser';
import { StructuredQuestionView } from '../StructuredQuestionView';
import {
  SAMPLE_STARTER_PROMPT,
  SAMPLE_BIOLOGY_ADVANCED,
  SAMPLE_NEXT_PATTERN,
  SAMPLE_KOREAN_EXAM
} from '../../utils/sampleData';

interface CreatorTabProps {
  currentPaper: QuestionPaper;
  onSavePaper: (paper: QuestionPaper, silent?: boolean) => void;
  onNavigateToMaster: () => void;
}

export const CreatorTab: React.FC<CreatorTabProps> = ({
  currentPaper,
  onSavePaper,
  onNavigateToMaster
}) => {
  const [rawInput, setRawInput] = useState<string>(() => {
    const saved = localStorage.getItem('examset_raw_creator_input');
    return saved || SAMPLE_STARTER_PROMPT;
  });

  const [isExpanded, setIsExpanded] = useState<boolean>(false);
  const [parseResult, setParseResult] = useState<ParseResult>(() => McqParser.parse(rawInput || SAMPLE_STARTER_PROMPT));
  const [isPending, startTransition] = useTransition();
  const [autoSaveStatus, setAutoSaveStatus] = useState<'saved' | 'saving' | 'idle'>('saved');
  const [lastSavedTime, setLastSavedTime] = useState<string>('Just now');
  const [autoSaveEnabled, setAutoSaveEnabled] = useState<boolean>(true);
  const [previewMode, setPreviewMode] = useState<'live' | 'list'>('live');
  const [selectedPreviewIndex, setSelectedPreviewIndex] = useState<number>(0);

  // Debounced auto-save and parsing on text changes
  useEffect(() => {
    setAutoSaveStatus('saving');

    const timer = setTimeout(() => {
      startTransition(() => {
        const result = McqParser.parse(rawInput);
        setParseResult(result);
        
        // Auto-save raw text
        localStorage.setItem('examset_raw_creator_input', rawInput);

        // Auto-save parsed paper to workspace and localStorage if valid questions exist
        if (autoSaveEnabled && result.paper.questions.length > 0) {
          onSavePaper(result.paper, true);
          localStorage.setItem('examset_active_paper', JSON.stringify(result.paper));
        }

        setAutoSaveStatus('saved');
        const now = new Date();
        const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        setLastSavedTime(timeStr);
      });
    }, 500);

    return () => clearTimeout(timer);
  }, [rawInput, autoSaveEnabled, onSavePaper]);

  const handlePasteClipboard = async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (text) {
        setRawInput(text);
      }
    } catch (err) {
      console.warn('Unable to read clipboard automatically:', err);
    }
  };

  const handleClear = () => {
    if (window.confirm('Clear all input text from the question creator?')) {
      setRawInput('');
    }
  };

  const handleApplyToWorkspace = () => {
    if (parseResult.paper.questions.length === 0) {
      alert('Cannot save: No valid questions could be detected. Please ensure questions are numbered (e.g. 1., 2.) with options (A, B, C, D).');
      return;
    }
    onSavePaper(parseResult.paper);
    localStorage.setItem('examset_active_paper', JSON.stringify(parseResult.paper));
    onNavigateToMaster();
  };

  const charCount = rawInput.length;
  const lineCount = rawInput ? rawInput.split('\n').length : 0;
  const detectedQuestions = parseResult.paper.questions;
  const currentPreviewQuestion = detectedQuestions[selectedPreviewIndex] || detectedQuestions[0];

  return (
    <div className="space-y-6">
      {/* Top Action Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50 p-4 rounded-xl border border-slate-200">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
              <FileText className="w-5 h-5 text-blue-600" />
              MCQ Importer & Real-Time Parser
            </h3>
            {/* Auto-save status badge */}
            <div 
              className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold border ${
                autoSaveStatus === 'saving'
                  ? 'bg-amber-50 text-amber-700 border-amber-200'
                  : 'bg-emerald-50 text-emerald-700 border-emerald-200'
              }`}
              title="Continuous debounced auto-save to localStorage"
            >
              {autoSaveStatus === 'saving' ? (
                <>
                  <RotateCw className="w-3 h-3 animate-spin text-amber-600" />
                  <span>Auto-saving...</span>
                </>
              ) : (
                <>
                  <Check className="w-3 h-3 text-emerald-600" />
                  <span>Auto-saved to localStorage • {lastSavedTime}</span>
                </>
              )}
            </div>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            Type or paste questions in any format. Progress is automatically preserved and synced to the workspace.
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <button
            onClick={handlePasteClipboard}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-white text-slate-700 border border-slate-300 rounded-lg hover:bg-slate-50 transition shadow-sm"
            title="Paste from clipboard"
          >
            <Clipboard className="w-3.5 h-3.5 text-slate-500" />
            Paste
          </button>

          <button
            onClick={handleClear}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-rose-700 bg-rose-50 border border-rose-200 rounded-lg hover:bg-rose-100 transition"
          >
            <Trash2 className="w-3.5 h-3.5" />
            Clear
          </button>

          <button
            onClick={handleApplyToWorkspace}
            disabled={parseResult.rawCount === 0}
            className={`flex items-center gap-1.5 px-4 py-1.5 text-xs font-bold rounded-lg transition shadow-sm ${
              parseResult.rawCount > 0
                ? 'bg-blue-600 text-white hover:bg-blue-700 cursor-pointer'
                : 'bg-slate-300 text-slate-500 cursor-not-allowed'
            }`}
          >
            <span>Open in Master Booklet</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Preset Quick Loader Buttons */}
      <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Sparkles className="w-4 h-4 text-amber-500" />
          <span className="text-xs font-bold text-slate-700">Exam Question Presets:</span>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          <button
            onClick={() => { setRawInput(SAMPLE_STARTER_PROMPT); setSelectedPreviewIndex(0); }}
            className="px-2.5 py-1 text-xs font-medium bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-md border border-slate-200 transition"
          >
            Science & Tech (5 MCQs)
          </button>
          <button
            onClick={() => { setRawInput(SAMPLE_BIOLOGY_ADVANCED); setSelectedPreviewIndex(0); }}
            className="px-2.5 py-1 text-xs font-medium bg-emerald-50 hover:bg-emerald-100 text-emerald-800 rounded-md border border-emerald-200 transition"
            title="Questions 1, 2, 3, 4, 5, 6 (Meiosis, Hormones, Natural Selection, Assertion-Reason, PCR)"
          >
            Bio & Genetics (Q1, 2, 3, 4, 5, 6)
          </button>
          <button
            onClick={() => { setRawInput(SAMPLE_NEXT_PATTERN); setSelectedPreviewIndex(0); }}
            className="px-2.5 py-1 text-xs font-medium bg-purple-50 hover:bg-purple-100 text-purple-800 rounded-md border border-purple-200 transition"
            title="Assertion-Reason, Match the Following Markdown Table, Multiple Statements, Sequence Arrows, Hardy-Weinberg"
          >
            Multi-Pattern Exam (Patterns 1–5)
          </button>
          <button
            onClick={() => { setRawInput(SAMPLE_KOREAN_EXAM); setSelectedPreviewIndex(0); }}
            className="px-2.5 py-1 text-xs font-medium bg-blue-50 hover:bg-blue-100 text-blue-800 rounded-md border border-blue-200 transition"
            title="Korean Hangul 1-5 with 2x2 horizontal option grids"
          >
            한국어 모의고사 (Hangul 1–5)
          </button>
        </div>
      </div>

      {/* Main Grid: Input Area + Parsing Diagnostics */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Text Input */}
        <div className={isExpanded ? 'lg:col-span-12' : 'lg:col-span-7'}>
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
            {/* Input Header & Status Bar */}
            <div className="flex items-center justify-between px-4 py-2.5 bg-slate-100 border-b border-slate-200 text-xs text-slate-600">
              <div className="flex items-center gap-3">
                <span className="font-semibold text-slate-800">Source Text Editor</span>
                <span>{lineCount} lines</span>
                <span>•</span>
                <span>{charCount.toLocaleString()} chars</span>
                {isPending && (
                  <span className="text-blue-600 font-medium animate-pulse flex items-center gap-1">
                    <span className="w-2 h-2 rounded-full bg-blue-600"></span>
                    Parsing in background...
                  </span>
                )}
              </div>

              <button
                onClick={() => setIsExpanded(!isExpanded)}
                className="flex items-center gap-1 text-slate-600 hover:text-slate-900 font-medium p-1 rounded hover:bg-slate-200"
                title={isExpanded ? 'Collapse' : 'Expand full width'}
              >
                {isExpanded ? (
                  <>
                    <Minimize2 className="w-3.5 h-3.5" />
                    <span>Collapse</span>
                  </>
                ) : (
                  <>
                    <Maximize2 className="w-3.5 h-3.5" />
                    <span>Expand</span>
                  </>
                )}
              </button>
            </div>

            {/* Input Textarea */}
            <textarea
              value={rawInput}
              onChange={(e) => setRawInput(e.target.value)}
              placeholder="Paste or type questions here...&#10;&#10;8. Assertion–Reason&#10;Assertion (A): Genetic drift can cause fixation of a neutral allele.&#10;Reason (R): Random fluctuations in allele frequencies occur in finite populations.&#10;(a) Both (A) and (R) are true and (R) is the correct explanation of (A)&#10;(b) Both (A) and (R) are true, but (R) is not the correct explanation of (A)&#10;(c) (A) is true, but (R) is false&#10;(d) (A) is false, but (R) is true&#10;&#10;Answer Key&#10;8. (a)"
              className={`w-full p-4 font-mono text-sm leading-relaxed text-slate-800 bg-white border-0 resize-y focus:outline-none focus:ring-0 ${
                isExpanded ? 'min-h-[580px]' : 'min-h-[460px]'
              }`}
              spellCheck={false}
            />
          </div>
        </div>

        {/* Right Column: Live Parse Metrics & Inspection */}
        {!isExpanded && (
          <div className="lg:col-span-5 space-y-4">
            {/* Summary Metrics Card */}
            <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-sm space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                  Live Parser Telemetry
                </h4>
                <span className="text-[10px] font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-md border border-emerald-200">
                  Real-Time Synced
                </span>
              </div>

              <div className="grid grid-cols-3 gap-2 text-center">
                <div className="p-2.5 bg-slate-50 rounded-lg border border-slate-100">
                  <div className="text-xl font-black text-slate-900">
                    {parseResult.rawCount}
                  </div>
                  <div className="text-[10.5px] font-semibold text-slate-500">
                    Questions
                  </div>
                </div>

                <div className="p-2.5 bg-slate-50 rounded-lg border border-slate-100">
                  <div className={`text-xl font-black ${parseResult.hasAnswerKeys ? 'text-emerald-600' : 'text-amber-500'}`}>
                    {parseResult.paper.questions.filter(q => !!q.correctAnswer).length}
                  </div>
                  <div className="text-[10.5px] font-semibold text-slate-500">
                    Answer Keys
                  </div>
                </div>

                <div className="p-2.5 bg-slate-50 rounded-lg border border-slate-100">
                  <div className={`text-xl font-black ${parseResult.hasExplanations ? 'text-emerald-600' : 'text-slate-400'}`}>
                    {parseResult.paper.questions.filter(q => !!q.explanation && q.explanation.trim().length > 0).length}
                  </div>
                  <div className="text-[10.5px] font-semibold text-slate-500">
                    Explanations
                  </div>
                </div>
              </div>

              {/* Warnings List */}
              {parseResult.warnings.length > 0 && (
                <div className="bg-amber-50 border border-amber-200 p-3 rounded-lg text-xs text-amber-800 space-y-1">
                  <div className="font-bold flex items-center gap-1 text-amber-900">
                    <AlertCircle className="w-3.5 h-3.5 text-amber-600" />
                    <span>Parser Diagnostics</span>
                  </div>
                  <ul className="list-disc list-inside space-y-0.5 text-[11px]">
                    {parseResult.warnings.slice(0, 3).map((w, i) => (
                      <li key={i}>{w}</li>
                    ))}
                    {parseResult.warnings.length > 3 && (
                      <li>...and {parseResult.warnings.length - 3} more warnings</li>
                    )}
                  </ul>
                </div>
              )}

              {parseResult.warnings.length === 0 && parseResult.rawCount > 0 && (
                <div className="bg-emerald-50 border border-emerald-200 p-2.5 rounded-lg text-xs text-emerald-800 flex items-center gap-2">
                  <CheckCircle className="w-4 h-4 text-emerald-600 shrink-0" />
                  <span>All {parseResult.rawCount} questions, options, and keys formatted cleanly.</span>
                </div>
              )}
            </div>

            {/* Live Question Layout Preview with Structured Assertion-Reason & Stacked Options */}
            {detectedQuestions.length > 0 && (
              <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-sm space-y-3">
                <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-slate-800">
                      Live Layout Preview
                    </span>
                    <span className="text-[11px] font-semibold text-blue-600 bg-blue-50 px-2 py-0.5 rounded-full border border-blue-200">
                      {detectedQuestions.length} Items
                    </span>
                  </div>

                  {/* View Mode Toggle */}
                  <div className="flex items-center bg-slate-100 p-0.5 rounded-lg border border-slate-200">
                    <button
                      type="button"
                      onClick={() => setPreviewMode('live')}
                      className={`flex items-center gap-1 px-2 py-1 rounded text-[11px] font-semibold transition ${
                        previewMode === 'live'
                          ? 'bg-white text-blue-700 shadow-2xs'
                          : 'text-slate-600 hover:text-slate-900'
                      }`}
                      title="Structured Visual Layout Preview"
                    >
                      <Layout className="w-3 h-3" /> Live
                    </button>
                    <button
                      type="button"
                      onClick={() => setPreviewMode('list')}
                      className={`flex items-center gap-1 px-2 py-1 rounded text-[11px] font-semibold transition ${
                        previewMode === 'list'
                          ? 'bg-white text-blue-700 shadow-2xs'
                          : 'text-slate-600 hover:text-slate-900'
                      }`}
                      title="Compact List Overview"
                    >
                      <List className="w-3 h-3" /> List
                    </button>
                  </div>
                </div>

                {previewMode === 'live' ? (
                  /* Rich Structured Live Preview */
                  <div className="space-y-3">
                    {/* Question Carousel Selector */}
                    <div className="flex items-center justify-between gap-1 bg-slate-50 p-1.5 rounded-lg border border-slate-200">
                      <button
                        type="button"
                        onClick={() => setSelectedPreviewIndex(Math.max(0, selectedPreviewIndex - 1))}
                        disabled={selectedPreviewIndex === 0}
                        className="p-1 text-slate-500 hover:text-slate-900 disabled:opacity-30 disabled:cursor-not-allowed"
                        title="Previous Question"
                      >
                        <ChevronLeft className="w-4 h-4" />
                      </button>

                      <div className="flex items-center gap-1 overflow-x-auto py-0.5 px-1 max-w-[220px] sm:max-w-[280px]">
                        {detectedQuestions.map((q, idx) => (
                          <button
                            key={q.id}
                            type="button"
                            onClick={() => setSelectedPreviewIndex(idx)}
                            className={`px-2 py-1 rounded text-xs font-bold transition shrink-0 ${
                              selectedPreviewIndex === idx
                                ? 'bg-blue-600 text-white shadow-2xs'
                                : 'bg-white text-slate-700 hover:bg-slate-200 border border-slate-200'
                            }`}
                          >
                            Q{q.num}
                          </button>
                        ))}
                      </div>

                      <button
                        type="button"
                        onClick={() => setSelectedPreviewIndex(Math.min(detectedQuestions.length - 1, selectedPreviewIndex + 1))}
                        disabled={selectedPreviewIndex === detectedQuestions.length - 1}
                        className="p-1 text-slate-500 hover:text-slate-900 disabled:opacity-30 disabled:cursor-not-allowed"
                        title="Next Question"
                      >
                        <ChevronRight className="w-4 h-4" />
                      </button>
                    </div>

                    {/* Render Structured Question */}
                    {currentPreviewQuestion && (
                      <div className="bg-slate-50/50 p-3.5 rounded-xl border border-slate-200 max-h-[360px] overflow-y-auto">
                        <StructuredQuestionView 
                          question={currentPreviewQuestion}
                          showAnswerIndicator={true}
                          showExplanation={true}
                        />
                      </div>
                    )}
                  </div>
                ) : (
                  /* Compact List View */
                  <div className="space-y-1.5 max-h-[320px] overflow-y-auto pr-1">
                    {detectedQuestions.map((q, idx) => (
                      <div 
                        key={q.id}
                        onClick={() => { setSelectedPreviewIndex(idx); setPreviewMode('live'); }}
                        className={`p-2.5 rounded-xl border text-xs flex items-start justify-between gap-2 cursor-pointer transition ${
                          selectedPreviewIndex === idx
                            ? 'bg-blue-50/80 border-blue-300 ring-1 ring-blue-200'
                            : 'bg-slate-50 hover:bg-slate-100 border-slate-200'
                        }`}
                      >
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5">
                            <span className="font-bold text-slate-900">Q{q.num}.</span>
                            <span className="text-[10px] font-semibold text-purple-700 bg-purple-50 px-1.5 py-0.5 rounded border border-purple-200">
                              {q.questionType || 'Direct Fact'}
                            </span>
                          </div>
                          <p className="text-slate-600 truncate mt-1">
                            {q.question.replace(/\n+/g, ' ')}
                          </p>
                        </div>
                        <div className="shrink-0 flex items-center gap-1">
                          {q.correctAnswer ? (
                            <span className="w-5 h-5 rounded-full bg-emerald-100 text-emerald-800 text-[11px] font-bold flex items-center justify-center">
                              {q.correctAnswer}
                            </span>
                          ) : (
                            <span className="w-5 h-5 rounded-full bg-slate-200 text-slate-500 text-[11px] font-bold flex items-center justify-center">
                              -
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
