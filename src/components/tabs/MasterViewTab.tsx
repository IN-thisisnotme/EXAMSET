import React, { useState } from 'react';
import { 
  Eye, 
  Plus, 
  Trash2, 
  CheckCircle, 
  AlertCircle, 
  Edit3, 
  ArrowUp, 
  ArrowDown, 
  Copy, 
  Search, 
  Check, 
  Sparkles,
  Layers,
  Settings,
  Layout,
  Code
} from 'lucide-react';
import { QuestionPaper, QuestionItem, CorrectOption, QuestionType } from '../../types/exam';
import { parseAssertionReason, shouldStackOptions, isAssertionReason } from '../../utils/questionFormatter';
import { McqParser } from '../../utils/mcqParser';

interface MasterViewTabProps {
  currentPaper: QuestionPaper;
  onUpdatePaper: (updatedPaper: QuestionPaper) => void;
}

const ALL_QUESTION_TYPES: QuestionType[] = [
  'Direct Fact',
  'Assertion–Reason',
  'Match the Following',
  'Statement-Based',
  'Multiple Statements + Combination',
  'Pair-Based',
  'Multiple Correct Statements',
  'Incorrect / Not Correct',
  'Correctly Matched',
  'Chronological Order',
  'Ascending/Descending Order',
  'Sequence/Process',
  'Cause–Effect',
  'Conceptual/Application',
  'Case-Based',
  'Numerical/Calculation',
  'Data Interpretation',
  'Map/Geography-Based',
  'Image/Diagram-Based',
  'Pair of Statements',
  'Three/Four-Statement Trap',
  'Elimination-Based',
  'Negative-Logic Question',
  'Quantitative Comparison',
  'Legal/Constitutional Application',
  'Policy/Institution-Based',
  'Term–Definition',
  'Example-Based',
  'Classification',
  'Odd-One-Out'
];

function reconstructArQuestion(
  leadIn: string | undefined, 
  assertion: string, 
  reason: string, 
  leadOut: string | undefined
): string {
  const parts: string[] = [];
  if (leadIn && leadIn.trim()) {
    parts.push(leadIn.trim());
  }
  parts.push(`Assertion (A): ${assertion.trim()}`);
  parts.push(`Reason (R): ${reason.trim()}`);
  if (leadOut && leadOut.trim()) {
    parts.push(leadOut.trim());
  }
  return parts.join('\n\n');
}

export const MasterViewTab: React.FC<MasterViewTabProps> = ({
  currentPaper,
  onUpdatePaper
}) => {
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [filterMode, setFilterMode] = useState<'all' | 'missing-key' | 'missing-exp'>('all');
  const [editingMetadata, setEditingMetadata] = useState<boolean>(false);
  const [metadataForm, setMetadataForm] = useState(currentPaper.metadata);
  const [rawEditMap, setRawEditMap] = useState<Record<string, boolean>>({});

  const questions = currentPaper.questions;

  // Filter questions based on search and filters
  const filteredQuestions = questions.filter((q) => {
    const matchesSearch = searchQuery.trim() === '' || 
      q.question.toLowerCase().includes(searchQuery.toLowerCase()) ||
      q.options.a.toLowerCase().includes(searchQuery.toLowerCase()) ||
      q.options.b.toLowerCase().includes(searchQuery.toLowerCase()) ||
      q.options.c.toLowerCase().includes(searchQuery.toLowerCase()) ||
      q.options.d.toLowerCase().includes(searchQuery.toLowerCase());

    if (!matchesSearch) return false;
    if (filterMode === 'missing-key') return !q.correctAnswer;
    if (filterMode === 'missing-exp') return !q.explanation || !q.explanation.trim();
    return true;
  });

  const handleUpdateQuestion = (updatedQ: QuestionItem) => {
    const updatedList = questions.map((q) => (q.id === updatedQ.id ? updatedQ : q));
    onUpdatePaper({
      ...currentPaper,
      questions: updatedList
    });
  };

  const handleDeleteQuestion = (id: string) => {
    if (window.confirm('Are you sure you want to delete this question?')) {
      const updatedList = questions.filter((q) => q.id !== id).map((q, idx) => ({
        ...q,
        num: idx + 1
      }));
      onUpdatePaper({
        ...currentPaper,
        questions: updatedList
      });
    }
  };

  const handleDuplicateQuestion = (q: QuestionItem) => {
    const newQ: QuestionItem = {
      ...q,
      id: `q-${Date.now().toString(36)}`,
      num: questions.length + 1,
      question: `${q.question} (Copy)`
    };
    onUpdatePaper({
      ...currentPaper,
      questions: [...questions, newQ]
    });
  };

  const handleMoveQuestion = (index: number, direction: 'up' | 'down') => {
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= questions.length) return;

    const reordered = [...questions];
    const [moved] = reordered.splice(index, 1);
    reordered.splice(targetIndex, 0, moved);

    // Renumber sequentially
    const renumbered = reordered.map((item, idx) => ({
      ...item,
      num: idx + 1
    }));

    onUpdatePaper({
      ...currentPaper,
      questions: renumbered
    });
  };

  const handleAddNewQuestion = () => {
    const nextNum = questions.length + 1;
    const newQ: QuestionItem = {
      id: `q-${Date.now().toString(36)}`,
      num: nextNum,
      question: 'Enter the new question text here...',
      options: {
        a: 'First option',
        b: 'Second option',
        c: 'Third option',
        d: 'Fourth option'
      },
      correctAnswer: 'A',
      explanation: 'Detailed explanation for the correct answer.'
    };

    onUpdatePaper({
      ...currentPaper,
      questions: [...questions, newQ]
    });
  };

  const handleSaveMetadata = (e: React.FormEvent) => {
    e.preventDefault();
    onUpdatePaper({
      ...currentPaper,
      metadata: metadataForm
    });
    setEditingMetadata(false);
  };

  const questionsWithKeys = questions.filter((q) => !!q.correctAnswer).length;
  const questionsWithExplanations = questions.filter((q) => !!q.explanation && q.explanation.trim().length > 0).length;

  return (
    <div className="space-y-6">
      {/* Top Action Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50 p-4 rounded-xl border border-slate-200">
        <div>
          <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <Eye className="w-5 h-5 text-blue-600" />
            Master Assessment & Answer Key Inspector
          </h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Review, edit options, renumber, and verify answer keys and pedagogical explanations for every item.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setEditingMetadata(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-white text-slate-700 border border-slate-300 rounded-lg hover:bg-slate-50 transition shadow-sm"
          >
            <Settings className="w-3.5 h-3.5 text-slate-500" />
            Edit Paper Info
          </button>

          <button
            onClick={handleAddNewQuestion}
            className="flex items-center gap-1.5 px-4 py-1.5 text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition shadow-sm"
          >
            <Plus className="w-4 h-4" />
            Add Question
          </button>
        </div>
      </div>

      {/* Assessment Telemetry Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm text-center">
          <div className="text-xl font-black text-slate-900">{questions.length}</div>
          <div className="text-[11px] font-semibold text-slate-500 mt-0.5">Total Questions</div>
        </div>

        <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm text-center">
          <div className={`text-xl font-black ${questionsWithKeys === questions.length ? 'text-emerald-600' : 'text-amber-600'}`}>
            {questionsWithKeys} / {questions.length}
          </div>
          <div className="text-[11px] font-semibold text-slate-500 mt-0.5">Answer Keys Assigned</div>
        </div>

        <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm text-center">
          <div className={`text-xl font-black ${questionsWithExplanations === questions.length ? 'text-emerald-600' : 'text-blue-600'}`}>
            {questionsWithExplanations} / {questions.length}
          </div>
          <div className="text-[11px] font-semibold text-slate-500 mt-0.5">Explanations Ready</div>
        </div>

        <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm text-center">
          <div className="text-xl font-black text-blue-700">{currentPaper.metadata.setNumber}</div>
          <div className="text-[11px] font-semibold text-slate-500 mt-0.5">Active Series Code</div>
        </div>
      </div>

      {/* Search & Filter Controls */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-white p-3 rounded-xl border border-slate-200">
        <div className="relative w-full sm:w-72">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search questions or options..."
            className="w-full pl-9 pr-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>

        <div className="flex items-center gap-1.5 w-full sm:w-auto overflow-x-auto">
          {[
            { id: 'all', label: `All (${questions.length})` },
            { id: 'missing-key', label: `Missing Key (${questions.length - questionsWithKeys})` },
            { id: 'missing-exp', label: `Missing Solution (${questions.length - questionsWithExplanations})` }
          ].map(f => (
            <button
              key={f.id}
              onClick={() => setFilterMode(f.id as any)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold whitespace-nowrap transition ${
                filterMode === f.id
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {/* Questions Editing List */}
      <div className="space-y-4">
        {filteredQuestions.length === 0 ? (
          <div className="text-center py-12 bg-white rounded-xl border border-slate-200 text-slate-400 text-xs">
            No questions match your filter criteria.
          </div>
        ) : (
          filteredQuestions.map((q) => {
            const actualIndex = questions.findIndex(item => item.id === q.id);
            const arParts = parseAssertionReason(q.question);
            const isAr = arParts !== null || isAssertionReason(q.question, q.options, q.questionType);
            const effectiveType = (q.questionType || (isAr ? 'Assertion–Reason' : McqParser.classifyQuestionType(q.question, q.options))) as QuestionType;
            const isStacked = shouldStackOptions(effectiveType, q.options);
            const isRawMode = !!rawEditMap[q.id];

            return (
              <div 
                key={q.id}
                className="bg-white rounded-xl border border-slate-200 shadow-sm p-5 space-y-4 hover:border-slate-300 transition"
              >
                {/* Question Item Header */}
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="w-7 h-7 rounded-lg bg-blue-600 text-white font-black text-xs flex items-center justify-center shadow-sm">
                      {q.num}
                    </span>
                    <span className="text-xs font-bold text-slate-700">
                      Question Item #{q.num}
                    </span>

                    {/* Question Type Selector Dropdown */}
                    <div className="flex items-center gap-1.5">
                      <select
                        value={effectiveType}
                        onChange={(e) => handleUpdateQuestion({ ...q, questionType: e.target.value as QuestionType })}
                        className={`text-[11px] font-semibold px-2.5 py-1 rounded-full border cursor-pointer focus:outline-none focus:ring-1 focus:ring-purple-500 shadow-2xs ${
                          effectiveType.includes('Assertion')
                            ? 'bg-purple-50 text-purple-700 border-purple-200'
                            : effectiveType.includes('Match')
                            ? 'bg-amber-50 text-amber-700 border-amber-200'
                            : effectiveType.includes('Statement')
                            ? 'bg-indigo-50 text-indigo-700 border-indigo-200'
                            : effectiveType.includes('Numerical')
                            ? 'bg-cyan-50 text-cyan-700 border-cyan-200'
                            : 'bg-slate-50 text-slate-700 border-slate-200'
                        }`}
                        title="Question Type Classification"
                      >
                        {ALL_QUESTION_TYPES.map((t) => (
                          <option key={t} value={t}>
                            {t}
                          </option>
                        ))}
                      </select>

                      <button
                        type="button"
                        onClick={() => {
                          const detected = McqParser.classifyQuestionType(q.question, q.options);
                          handleUpdateQuestion({ ...q, questionType: detected });
                        }}
                        className="text-[10px] text-slate-500 hover:text-blue-600 p-1 rounded hover:bg-slate-100"
                        title="Auto-Detect Question Type"
                      >
                        <Sparkles className="w-3 h-3" />
                      </button>
                    </div>
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => handleMoveQuestion(actualIndex, 'up')}
                      disabled={actualIndex === 0}
                      className="p-1.5 text-slate-500 hover:text-slate-900 rounded hover:bg-slate-100 disabled:opacity-30 disabled:cursor-not-allowed"
                      title="Move Up"
                    >
                      <ArrowUp className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => handleMoveQuestion(actualIndex, 'down')}
                      disabled={actualIndex === questions.length - 1}
                      className="p-1.5 text-slate-500 hover:text-slate-900 rounded hover:bg-slate-100 disabled:opacity-30 disabled:cursor-not-allowed"
                      title="Move Down"
                    >
                      <ArrowDown className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => handleDuplicateQuestion(q)}
                      className="p-1.5 text-slate-500 hover:text-slate-900 rounded hover:bg-slate-100"
                      title="Duplicate Question"
                    >
                      <Copy className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => handleDeleteQuestion(q.id)}
                      className="p-1.5 text-rose-500 hover:text-rose-700 rounded hover:bg-rose-50"
                      title="Delete Question"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>

                {/* Editable Question Stem with Assertion-Reason Structured View Support */}
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <label className="block text-[11px] font-bold uppercase tracking-wider text-slate-500">
                      Question Stem
                    </label>

                    {arParts && (
                      <button
                        type="button"
                        onClick={() => setRawEditMap(prev => ({ ...prev, [q.id]: !prev[q.id] }))}
                        className="inline-flex items-center gap-1 text-[11px] font-semibold text-blue-600 hover:text-blue-700 bg-blue-50 px-2 py-0.5 rounded border border-blue-200 transition"
                      >
                        {isRawMode ? (
                          <>
                            <Layout className="w-3 h-3" /> Structured View
                          </>
                        ) : (
                          <>
                            <Code className="w-3 h-3" /> Raw Text Editor
                          </>
                        )}
                      </button>
                    )}
                  </div>

                  {arParts && !isRawMode ? (
                    /* Structured Assertion-Reason Editable Fields */
                    <div className="space-y-2.5 bg-slate-50/50 p-3 rounded-xl border border-slate-200">
                      {/* Lead-in if present or can be added */}
                      {arParts.leadIn && (
                        <div>
                          <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-1">
                            Instruction / Context (Lead-in)
                          </label>
                          <input
                            type="text"
                            value={arParts.leadIn}
                            onChange={(e) => {
                              const newLeadIn = e.target.value;
                              const newQ = reconstructArQuestion(newLeadIn, arParts.assertion, arParts.reason, arParts.leadOut);
                              handleUpdateQuestion({ ...q, question: newQ });
                            }}
                            className="w-full px-3 py-1.5 text-xs text-slate-800 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500"
                          />
                        </div>
                      )}

                      {/* Assertion (A) Card with proper vertical alignment and line breaks */}
                      <div className="rounded-xl border border-sky-200 bg-sky-50/70 p-3 space-y-1.5">
                        <div className="flex items-center justify-between">
                          <span className="px-2 py-0.5 rounded-md text-[10.5px] font-extrabold uppercase tracking-wider bg-sky-600 text-white shadow-2xs">
                            Assertion (A)
                          </span>
                        </div>
                        <textarea
                          value={arParts.assertion}
                          onChange={(e) => {
                            const newAssertion = e.target.value;
                            const newQ = reconstructArQuestion(arParts.leadIn, newAssertion, arParts.reason, arParts.leadOut);
                            handleUpdateQuestion({ ...q, question: newQ });
                          }}
                          rows={Math.max(2, Math.min(5, Math.ceil(arParts.assertion.length / 55)))}
                          className="w-full p-2.5 text-xs sm:text-sm text-slate-900 font-medium bg-white border border-sky-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-sky-500 resize-y whitespace-pre-wrap leading-relaxed"
                          placeholder="Enter Assertion statement..."
                        />
                      </div>

                      {/* Reason (R) Card with proper vertical alignment and line breaks */}
                      <div className="rounded-xl border border-amber-200 bg-amber-50/70 p-3 space-y-1.5">
                        <div className="flex items-center justify-between">
                          <span className="px-2 py-0.5 rounded-md text-[10.5px] font-extrabold uppercase tracking-wider bg-amber-600 text-white shadow-2xs">
                            Reason (R)
                          </span>
                        </div>
                        <textarea
                          value={arParts.reason}
                          onChange={(e) => {
                            const newReason = e.target.value;
                            const newQ = reconstructArQuestion(arParts.leadIn, arParts.assertion, newReason, arParts.leadOut);
                            handleUpdateQuestion({ ...q, question: newQ });
                          }}
                          rows={Math.max(2, Math.min(5, Math.ceil(arParts.reason.length / 55)))}
                          className="w-full p-2.5 text-xs sm:text-sm text-slate-900 font-medium bg-white border border-amber-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-amber-500 resize-y whitespace-pre-wrap leading-relaxed"
                          placeholder="Enter Reason statement..."
                        />
                      </div>

                      {/* Lead-out prompt */}
                      {arParts.leadOut && (
                        <div>
                          <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-1">
                            Closing Prompt (Lead-out)
                          </label>
                          <input
                            type="text"
                            value={arParts.leadOut}
                            onChange={(e) => {
                              const newLeadOut = e.target.value;
                              const newQ = reconstructArQuestion(arParts.leadIn, arParts.assertion, arParts.reason, newLeadOut);
                              handleUpdateQuestion({ ...q, question: newQ });
                            }}
                            className="w-full px-3 py-1.5 text-xs text-slate-800 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500"
                          />
                        </div>
                      )}
                    </div>
                  ) : (
                    /* Standard Question Textarea with line break preservation */
                    <textarea
                      value={q.question}
                      onChange={(e) => handleUpdateQuestion({ ...q, question: e.target.value })}
                      rows={Math.max(3, Math.min(8, q.question.split('\n').length + 1))}
                      placeholder="Enter question stem (supports multiple lines, tables, and assertion-reason formatting)..."
                      className="w-full p-3 text-xs sm:text-sm text-slate-800 border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500 resize-y whitespace-pre-wrap leading-relaxed font-medium"
                    />
                  )}
                </div>

                {/* 4 Editable Options: Automatically Stacked for Assertion-Reason & Multi-line Options */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <label className="block text-[11px] font-bold uppercase tracking-wider text-slate-500">
                      Options & Official Key Selection {isStacked && <span className="text-blue-600 font-normal lowercase">(stacked layout)</span>}
                    </label>
                    <span className="text-[10px] text-slate-400 font-medium">
                      Click option letter (A, B, C, D) to set as official key
                    </span>
                  </div>

                  <div className={isStacked ? 'flex flex-col space-y-2.5' : 'grid grid-cols-1 sm:grid-cols-2 gap-2.5'}>
                    {(['A', 'B', 'C', 'D'] as CorrectOption[]).map((optKey) => {
                      const lowerKey = optKey.toLowerCase() as 'a' | 'b' | 'c' | 'd';
                      const isCorrect = q.correctAnswer === optKey;

                      return (
                        <div 
                          key={optKey}
                          className={`p-3 rounded-xl border flex items-start gap-3 transition ${
                            isCorrect 
                              ? 'bg-emerald-50/80 border-emerald-300 ring-1 ring-emerald-300 shadow-2xs' 
                              : 'bg-slate-50/60 border-slate-200 hover:border-slate-300'
                          }`}
                        >
                          <button
                            type="button"
                            onClick={() => handleUpdateQuestion({ ...q, correctAnswer: optKey })}
                            className={`w-7 h-7 rounded-full flex items-center justify-center font-bold text-xs shrink-0 mt-1 transition shadow-2xs ${
                              isCorrect
                                ? 'bg-emerald-600 text-white'
                                : 'bg-white text-slate-600 border border-slate-300 hover:bg-slate-100'
                            }`}
                            title={`Set (${optKey}) as correct answer`}
                          >
                            {optKey}
                          </button>

                          <div className="flex-1 min-w-0">
                            <textarea
                              value={q.options[lowerKey]}
                              onChange={(e) => handleUpdateQuestion({
                                ...q,
                                options: {
                                  ...q.options,
                                  [lowerKey]: e.target.value
                                }
                              })}
                              rows={Math.max(1, Math.min(5, Math.ceil((q.options[lowerKey] || '').length / 50) + ((q.options[lowerKey] || '').split('\n').length - 1)))}
                              placeholder={`Option (${optKey}) text...`}
                              className="w-full bg-white px-3 py-1.5 text-xs sm:text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500 text-slate-800 font-medium leading-relaxed resize-y whitespace-pre-wrap"
                            />
                          </div>

                          {isCorrect && (
                            <span className="shrink-0 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-emerald-600 text-white flex items-center gap-1 shadow-2xs mt-1">
                              <Check className="w-3 h-3 stroke-[2.5]" /> Key
                            </span>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Editable Detailed Explanation with line break preservation */}
                <div>
                  <label className="block text-[11px] font-bold uppercase tracking-wider text-slate-500 mb-1">
                    Pedagogical Explanation
                  </label>
                  <textarea
                    value={q.explanation}
                    onChange={(e) => handleUpdateQuestion({ ...q, explanation: e.target.value })}
                    rows={Math.max(2, Math.min(6, (q.explanation || '').split('\n').length + 1))}
                    placeholder="Provide detailed explanation, reasoning, or formulas used..."
                    className="w-full p-3 text-xs sm:text-sm text-slate-800 border border-slate-200 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500 resize-y bg-slate-50/50 leading-relaxed whitespace-pre-wrap font-medium"
                  />
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Edit Metadata Modal */}
      {editingMetadata && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-slate-200 space-y-4">
            <h3 className="text-lg font-bold text-slate-900">Edit Question Paper Metadata</h3>

            <form onSubmit={handleSaveMetadata} className="space-y-3 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 mb-1">Examination Title</label>
                <input
                  type="text"
                  value={metadataForm.title}
                  onChange={(e) => setMetadataForm({ ...metadataForm, title: e.target.value })}
                  className="w-full px-3 py-1.5 border border-slate-300 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Subject</label>
                <input
                  type="text"
                  value={metadataForm.subject}
                  onChange={(e) => setMetadataForm({ ...metadataForm, subject: e.target.value })}
                  className="w-full px-3 py-1.5 border border-slate-300 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Series / Set</label>
                  <input
                    type="text"
                    value={metadataForm.setNumber}
                    onChange={(e) => setMetadataForm({ ...metadataForm, setNumber: e.target.value })}
                    className="w-full px-3 py-1.5 border border-slate-300 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                </div>

                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Target Exam</label>
                  <input
                    type="text"
                    value={metadataForm.targetExam}
                    onChange={(e) => setMetadataForm({ ...metadataForm, targetExam: e.target.value })}
                    className="w-full px-3 py-1.5 border border-slate-300 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setEditingMetadata(false)}
                  className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-lg transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition shadow"
                >
                  Save Changes
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default MasterViewTab;
