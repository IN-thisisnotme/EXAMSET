import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  FilePlus2,
  FileText,
  Sparkles,
  Upload,
  X,
} from 'lucide-react';
import { FolderItem, QuestionPaper } from '../types/exam';
import { useTheme } from '../theme/ThemeProvider';
import { McqParser } from '../utils/mcqParser';
import { PaperLanguageDetector } from '../utils/paperLanguageDetector';
import { ExamNumberGenerator } from '../utils/examNumberGenerator';
import { buildUserFolderSuggestions } from '../utils/folderPathHelper';
import { UserFolderDropdownInput } from './UserFolderDropdown';
import {
  SAMPLE_BIOLOGY_ADVANCED,
  SAMPLE_KOREAN_EXAM,
  SAMPLE_PHYSICS_MATH_MSQ,
  SAMPLE_STARTER_PROMPT,
} from '../utils/sampleData';

interface InputModalProps {
  isOpen: boolean;
  folders: FolderItem[];
  existingPapers: QuestionPaper[];
  defaultFolderId?: string;
  initialText?: string;
  onCreatePaper: (paper: QuestionPaper) => void;
  onClose: () => void;
}

export const InputModal: React.FC<InputModalProps> = ({
  isOpen,
  folders,
  existingPapers,
  defaultFolderId = 'default',
  initialText = '',
  onCreatePaper,
  onClose,
}) => {
  const { colors } = useTheme();
  const [rawText, setRawText] = useState<string>(initialText);
  const [customTitle, setCustomTitle] = useState<string>('');
  const [customSubject, setCustomSubject] = useState<string>('');
  const [setNumber, setSetNumber] = useState<string>('01');
  const [folderId, setFolderId] = useState<string>(defaultFolderId);
  const [durationMinutes, setDurationMinutes] = useState<number>(60);
  const [positiveMarks, setPositiveMarks] = useState<number>(2.0);
  const [negativeMarks, setNegativeMarks] = useState<number>(0.66);

  const parseResult = useMemo(() => McqParser.parse(rawText), [rawText]);
  const detectedLangs = useMemo(
    () =>
      rawText.trim()
        ? PaperLanguageDetector.detectLanguages(rawText, customTitle || parseResult.paper.metadata.title)
        : ['English'],
    [rawText, customTitle, parseResult.paper.metadata.title]
  );

  const normalizedSet = ExamNumberGenerator.normalizeSetCode(setNumber);
  const duplicateSetWarning = useMemo(() => {
    const titleToCheck = (customTitle.trim() || parseResult.paper.metadata.title).toLowerCase();
    return existingPapers.some(
      (p) =>
        p.metadata.title.toLowerCase() === titleToCheck &&
        ExamNumberGenerator.normalizeSetCode(p.metadata.setNumber) === normalizedSet
    );
  }, [existingPapers, customTitle, parseResult.paper.metadata.title, normalizedSet]);

  if (!isOpen) return null;

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      const content = ev.target?.result as string;
      if (content) setRawText(content);
    };
    reader.readAsText(file);
  };

  const handleCreate = () => {
    if (parseResult.paper.questions.length === 0) return;

    const existingRolls = existingPapers.map((p) => p.metadata.rollNumber || '');
    const existingBooklets = existingPapers.map((p) => p.metadata.bookletNumber || '');

    const finalPaper: QuestionPaper = {
      metadata: {
        ...parseResult.paper.metadata,
        id: `paper-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        title: customTitle.trim() || parseResult.paper.metadata.title || 'New Examination Set',
        subject: customSubject.trim() || parseResult.paper.metadata.subject || 'General Assessment',
        setNumber: normalizedSet,
        folderId: folderId || 'default',
        rollNumber: ExamNumberGenerator.nextRollNumber(existingRolls),
        bookletNumber: ExamNumberGenerator.nextBookletNumber(existingBooklets),
        durationMinutes,
        positiveMarks,
        negativeMarks,
        unattendedMarks: 0,
        msqScoringPolicy: 'ALL_OR_NOTHING',
        createdAt: Date.now(),
        updatedAt: Date.now(),
      },
      questions: parseResult.paper.questions,
    };

    onCreatePaper(finalPaper);
    setRawText('');
    setCustomTitle('');
    setCustomSubject('');
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.6)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-4xl max-h-[92vh] rounded-2xl shadow-2xl flex flex-col overflow-hidden"
        style={{
          backgroundColor: colors.surface,
          border: `1px solid ${colors.border}`,
          color: colors.ink,
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          className="px-5 py-4 flex items-center justify-between"
          style={{ borderBottom: `1px solid ${colors.border}` }}
        >
          <div className="flex items-center gap-2.5">
            <div
              className="w-9 h-9 rounded-lg flex items-center justify-center"
              style={{ backgroundColor: colors.tealLight, color: colors.teal }}
            >
              <FilePlus2 className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm font-bold" style={{ color: colors.inkBold }}>
                Create New Question Set
              </h3>
              <p className="text-[0.6875rem]" style={{ color: colors.muted }}>
                Paste formatted MCQs, Markdown tables, or JSON — creates a brand new saved paper without modifying existing sets
              </p>
            </div>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg" style={{ color: colors.muted }} aria-label="Close">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-5 space-y-4">
          {/* Quick Sample Loaders & File Upload */}
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <div className="flex items-center gap-1.5 flex-wrap">
              <span className="text-[0.6875rem] font-bold uppercase tracking-wider mr-1" style={{ color: colors.muted }}>
                Samples:
              </span>
              {[
                { label: 'Science Starter', text: SAMPLE_STARTER_PROMPT },
                { label: 'Bio Advanced', text: SAMPLE_BIOLOGY_ADVANCED },
                { label: 'Physics/Math MSQ+E', text: SAMPLE_PHYSICS_MATH_MSQ },
                { label: 'Korean Hangul', text: SAMPLE_KOREAN_EXAM },
              ].map((s) => (
                <button
                  key={s.label}
                  type="button"
                  onClick={() => setRawText(s.text)}
                  className="px-2.5 py-1 rounded-lg text-[0.6875rem] font-semibold transition border"
                  style={{
                    backgroundColor: colors.surfaceVariant,
                    borderColor: colors.border,
                    color: colors.ink,
                  }}
                >
                  <Sparkles className="w-3 h-3 inline mr-1" style={{ color: colors.teal }} />
                  {s.label}
                </button>
              ))}
            </div>

            <label
              className="cursor-pointer inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border"
              style={{
                backgroundColor: colors.blueLight,
                borderColor: colors.border,
                color: colors.teal,
              }}
            >
              <Upload className="w-3.5 h-3.5" />
              <span>Import .txt / .json</span>
              <input type="file" accept=".txt,.json,.md" onChange={handleFileUpload} className="hidden" />
            </label>
          </div>

          {/* Textarea */}
          <textarea
            value={rawText}
            onChange={(e) => setRawText(e.target.value)}
            rows={10}
            placeholder="Paste questions, options (A)–(D) or (E), MSQ keys (e.g. A, C), Markdown tables, or ExamSet JSON..."
            className="w-full min-h-[12rem] resize-y p-3.5 rounded-xl font-mono text-xs leading-relaxed focus:outline-none"
            style={{
              backgroundColor: colors.surfaceVariant,
              border: `1px solid ${colors.border}`,
              color: colors.inkBold,
            }}
          />

          {/* Live Detection Summary Strip */}
          {rawText.trim().length > 0 && (
            <div
              className="p-3 rounded-xl flex flex-wrap items-center justify-between gap-2 text-xs"
              style={{
                backgroundColor: parseResult.rawCount > 0 ? colors.greenLight : colors.redLight,
                border: `1px solid ${parseResult.rawCount > 0 ? colors.green : colors.red}`,
              }}
            >
              <div className="flex items-center gap-2 font-bold" style={{ color: parseResult.rawCount > 0 ? colors.green : colors.red }}>
                {parseResult.rawCount > 0 ? <CheckCircle2 className="w-4 h-4" /> : <AlertTriangle className="w-4 h-4" />}
                <span>
                  {parseResult.rawCount > 0
                    ? `Detected ${parseResult.rawCount} question(s)`
                    : 'No valid questions detected yet'}
                </span>
              </div>
              <div className="flex items-center gap-1.5 flex-wrap text-[0.6875rem]">
                <span className="px-2 py-0.5 rounded font-semibold" style={{ backgroundColor: colors.surface, color: colors.ink }}>
                  Lang: {detectedLangs.join(' + ')}
                </span>
                {parseResult.hasAnswerKeys && (
                  <span className="px-2 py-0.5 rounded font-semibold" style={{ backgroundColor: colors.surface, color: colors.green }}>
                    ✓ Keys Found
                  </span>
                )}
                {parseResult.hasExplanations && (
                  <span className="px-2 py-0.5 rounded font-semibold" style={{ backgroundColor: colors.surface, color: colors.teal }}>
                    ★ Explanations Found
                  </span>
                )}
                {(parseResult.detectedMsqCount || 0) > 0 && (
                  <span className="px-2 py-0.5 rounded font-semibold" style={{ backgroundColor: colors.purpleLight, color: colors.purple }}>
                    {parseResult.detectedMsqCount} MSQ
                  </span>
                )}
                {(parseResult.detectedOptionECount || 0) > 0 && (
                  <span className="px-2 py-0.5 rounded font-semibold" style={{ backgroundColor: colors.amberLight, color: colors.amber }}>
                    {parseResult.detectedOptionECount} Option E
                  </span>
                )}
              </div>
            </div>
          )}

          {/* Metadata Override Inputs */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                Title Override (Optional)
              </label>
              <input
                type="text"
                value={customTitle}
                onChange={(e) => setCustomTitle(e.target.value)}
                placeholder={parseResult.paper.metadata.title || 'Auto-detected from text'}
                className="w-full px-3 py-2 rounded-lg focus:outline-none"
                style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
              />
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                Subject
              </label>
              <UserFolderDropdownInput
                value={customSubject}
                onChange={setCustomSubject}
                onSelectEntry={(entry) => {
                  setCustomSubject(entry.fullPath);
                  setFolderId(entry.id);
                }}
                folders={folders}
                placeholder={parseResult.paper.metadata.subject || 'General Studies'}
                buttonAriaLabel="Choose Subject from your folders"
                inputClassName="w-full px-3 py-2 pr-9 rounded-lg focus:outline-none"
                inputStyle={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
              />
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                Destination Folder
              </label>
              <select
                value={folderId}
                onChange={(e) => setFolderId(e.target.value)}
                className="w-full px-3 py-2 rounded-lg focus:outline-none"
                style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
              >
                {folders
                  .filter((f) => f.id === 'default')
                  .map((f) => (
                    <option key={f.id} value={f.id}>
                      {f.name}
                    </option>
                  ))}
                {buildUserFolderSuggestions(folders).map((entry) => (
                  <option key={entry.id} value={entry.id}>
                    [{entry.levelLabel}] {entry.fullPath}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                2-Digit Set Code
              </label>
              <input
                type="text"
                value={setNumber}
                onChange={(e) => setSetNumber(e.target.value)}
                className="w-full px-3 py-1.5 rounded-lg font-mono focus:outline-none"
                style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
              />
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                Duration (Min)
              </label>
              <input
                type="number"
                min={5}
                max={360}
                value={durationMinutes}
                onChange={(e) => setDurationMinutes(Number(e.target.value) || 60)}
                className="w-full px-3 py-1.5 rounded-lg focus:outline-none"
                style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
              />
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                + Marks / Q
              </label>
              <input
                type="number"
                step="0.25"
                min={0.5}
                value={positiveMarks}
                onChange={(e) => setPositiveMarks(Number(e.target.value) || 1)}
                className="w-full px-3 py-1.5 rounded-lg focus:outline-none"
                style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
              />
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                - Negative / Q
              </label>
              <input
                type="number"
                step="0.01"
                min={0}
                value={negativeMarks}
                onChange={(e) => setNegativeMarks(Math.max(0, Number(e.target.value) || 0))}
                className="w-full px-3 py-1.5 rounded-lg focus:outline-none"
                style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
              />
            </div>
          </div>

          {duplicateSetWarning && (
            <div
              className="p-2.5 rounded-lg text-xs flex items-center gap-2"
              style={{ backgroundColor: colors.amberLight, color: colors.amber, border: `1px solid ${colors.amber}` }}
            >
              <AlertTriangle className="w-4 h-4 flex-shrink-0" />
              <span>
                A paper with title "{customTitle || parseResult.paper.metadata.title}" and Set {normalizedSet} already exists. Consider incrementing the Set code.
              </span>
            </div>
          )}
        </div>

        <div
          className="px-5 py-3 flex items-center justify-between gap-2"
          style={{ borderTop: `1px solid ${colors.border}`, backgroundColor: colors.surfaceVariant }}
        >
          <span className="text-[0.6875rem] flex items-center gap-1" style={{ color: colors.muted }}>
            <FileText className="w-3.5 h-3.5" />
            Non-destructive save into Library
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-2 rounded-lg text-xs font-semibold"
              style={{ color: colors.muted }}
            >
              Cancel
            </button>
            <button
              type="button"
              disabled={parseResult.rawCount === 0}
              onClick={handleCreate}
              className="px-4 py-2 rounded-lg text-xs font-bold disabled:opacity-40 transition"
              style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
            >
              Create & Load Set ({parseResult.rawCount} Qs)
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default InputModal;
