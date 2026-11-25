import React, { useEffect, useState } from 'react';
import { RefreshCw, Save, Sliders, X } from 'lucide-react';
import { FolderItem, MsqScoringPolicy, PaperCategory, QuestionPaper } from '../types/exam';
import { buildUserFolderSuggestions } from '../utils/folderPathHelper';
import { useTheme } from '../theme/ThemeProvider';
import { ExamNumberGenerator } from '../utils/examNumberGenerator';

interface EditMetadataModalProps {
  isOpen: boolean;
  paper: QuestionPaper;
  folders: FolderItem[];
  existingPapers: QuestionPaper[];
  onSaveMetadata: (updated: QuestionPaper) => void;
  onClose: () => void;
}

export const EditMetadataModal: React.FC<EditMetadataModalProps> = ({
  isOpen,
  paper,
  folders,
  existingPapers,
  onSaveMetadata,
  onClose,
}) => {
  const { colors } = useTheme();
  const [title, setTitle] = useState(paper.metadata.title);
  const [subject, setSubject] = useState(paper.metadata.subject);
  const [topic, setTopic] = useState(paper.metadata.topic);
  const [targetExam, setTargetExam] = useState(paper.metadata.targetExam);
  const [setNumber, setSetNumber] = useState(paper.metadata.setNumber);
  const [rollNumber, setRollNumber] = useState(paper.metadata.rollNumber || '0000000001');
  const [bookletNumber, setBookletNumber] = useState(paper.metadata.bookletNumber || '0000001');
  const [durationMinutes, setDurationMinutes] = useState(paper.metadata.durationMinutes ?? 60);
  const [positiveMarks, setPositiveMarks] = useState(paper.metadata.positiveMarks ?? 2.0);
  const [negativeMarks, setNegativeMarks] = useState(paper.metadata.negativeMarks ?? 0.66);
  const [unattendedMarks, setUnattendedMarks] = useState(paper.metadata.unattendedMarks ?? 0.0);
  const [msqPolicy, setMsqPolicy] = useState<MsqScoringPolicy>(paper.metadata.msqScoringPolicy || 'ALL_OR_NOTHING');
  const [category, setCategory] = useState<PaperCategory | ''>(paper.metadata.manualCategory || '');
  const [folderId, setFolderId] = useState(paper.metadata.folderId || 'default');

  useEffect(() => {
    if (isOpen) {
      setTitle(paper.metadata.title);
      setSubject(paper.metadata.subject);
      setTopic(paper.metadata.topic);
      setTargetExam(paper.metadata.targetExam);
      setSetNumber(paper.metadata.setNumber);
      setRollNumber(paper.metadata.rollNumber || '0000000001');
      setBookletNumber(paper.metadata.bookletNumber || '0000001');
      setDurationMinutes(paper.metadata.durationMinutes ?? 60);
      setPositiveMarks(paper.metadata.positiveMarks ?? 2.0);
      setNegativeMarks(paper.metadata.negativeMarks ?? 0.66);
      setUnattendedMarks(paper.metadata.unattendedMarks ?? 0.0);
      setMsqPolicy(paper.metadata.msqScoringPolicy || 'ALL_OR_NOTHING');
      setCategory(paper.metadata.manualCategory || '');
      setFolderId(paper.metadata.folderId || 'default');
    }
  }, [isOpen, paper]);

  if (!isOpen) return null;

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    onSaveMetadata({
      ...paper,
      metadata: {
        ...paper.metadata,
        title: title.trim() || 'Untitled Paper',
        subject: subject.trim() || 'General',
        topic: topic.trim(),
        targetExam: targetExam.trim() || 'Competitive Exam',
        setNumber: ExamNumberGenerator.normalizeSetCode(setNumber),
        rollNumber: ExamNumberGenerator.normalizeRollNumber(rollNumber),
        bookletNumber: ExamNumberGenerator.normalizeBookletNumber(bookletNumber),
        durationMinutes: Math.max(1, durationMinutes),
        positiveMarks: Math.max(0.25, positiveMarks),
        negativeMarks: Math.max(0, negativeMarks),
        unattendedMarks: Math.max(0, unattendedMarks),
        msqScoringPolicy: msqPolicy,
        manualCategory: category || undefined,
        folderId: folderId || 'default',
        updatedAt: Date.now(),
      },
    });
    onClose();
  };

  const inputStyle = {
    backgroundColor: colors.surfaceVariant,
    border: `1px solid ${colors.border}`,
    color: colors.inkBold,
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.58)' }}
      onClick={onClose}
    >
      <form
        onSubmit={handleSave}
        className="w-full max-w-xl max-h-[90vh] rounded-2xl shadow-2xl flex flex-col overflow-hidden"
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
              className="w-8 h-8 rounded-lg flex items-center justify-center"
              style={{ backgroundColor: colors.tealLight, color: colors.teal }}
            >
              <Sliders className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold" style={{ color: colors.inkBold }}>
                Edit Examination Metadata & Marking
              </h3>
              <p className="text-[0.6875rem]" style={{ color: colors.muted }}>
                Applies to CBT Portal, Print Headers, and OMR Bubble Grids
              </p>
            </div>
          </div>
          <button type="button" onClick={onClose} className="p-1.5 rounded-lg" style={{ color: colors.muted }}>
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-5 space-y-3.5 text-xs">
          <div>
            <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
              Paper Title
            </label>
            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full px-3 py-2 rounded-lg focus:outline-none"
              style={inputStyle}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                Subject
              </label>
              <input
                type="text"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                className="w-full px-3 py-2 rounded-lg focus:outline-none"
                style={inputStyle}
              />
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                Topic / Focus
              </label>
              <input
                type="text"
                value={topic}
                onChange={(e) => setTopic(e.target.value)}
                className="w-full px-3 py-2 rounded-lg focus:outline-none"
                style={inputStyle}
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                Target Exam Series
              </label>
              <input
                type="text"
                value={targetExam}
                onChange={(e) => setTargetExam(e.target.value)}
                className="w-full px-3 py-2 rounded-lg focus:outline-none"
                style={inputStyle}
              />
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                Folder Assignment
              </label>
              <select
                value={folderId}
                onChange={(e) => setFolderId(e.target.value)}
                className="w-full px-3 py-2 rounded-lg focus:outline-none"
                style={inputStyle}
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

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                2-Digit Set Code
              </label>
              <input
                type="text"
                value={setNumber}
                onChange={(e) => setSetNumber(e.target.value)}
                className="w-full px-3 py-2 rounded-lg font-mono focus:outline-none"
                style={inputStyle}
              />
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                10-Digit Roll Number
              </label>
              <div className="flex gap-1">
                <input
                  type="text"
                  value={rollNumber}
                  onChange={(e) => setRollNumber(e.target.value)}
                  className="w-full px-2.5 py-2 rounded-lg font-mono focus:outline-none"
                  style={inputStyle}
                />
                <button
                  type="button"
                  title="Generate Next Roll No"
                  onClick={() =>
                    setRollNumber(ExamNumberGenerator.nextRollNumber(existingPapers.map((p) => p.metadata.rollNumber || '')))
                  }
                  className="px-2 rounded-lg border"
                  style={{ borderColor: colors.border, backgroundColor: colors.blueLight, color: colors.teal }}
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                7-Digit Booklet No
              </label>
              <div className="flex gap-1">
                <input
                  type="text"
                  value={bookletNumber}
                  onChange={(e) => setBookletNumber(e.target.value)}
                  className="w-full px-2.5 py-2 rounded-lg font-mono focus:outline-none"
                  style={inputStyle}
                />
                <button
                  type="button"
                  title="Generate Next Booklet No"
                  onClick={() =>
                    setBookletNumber(
                      ExamNumberGenerator.nextBookletNumber(existingPapers.map((p) => p.metadata.bookletNumber || ''))
                    )
                  }
                  className="px-2 rounded-lg border"
                  style={{ borderColor: colors.border, backgroundColor: colors.blueLight, color: colors.teal }}
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                Duration (Min)
              </label>
              <input
                type="number"
                min={1}
                value={durationMinutes}
                onChange={(e) => setDurationMinutes(Number(e.target.value) || 60)}
                className="w-full px-3 py-2 rounded-lg focus:outline-none"
                style={inputStyle}
              />
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                + Marks
              </label>
              <input
                type="number"
                step="0.25"
                min={0.25}
                value={positiveMarks}
                onChange={(e) => setPositiveMarks(Number(e.target.value) || 1)}
                className="w-full px-3 py-2 rounded-lg focus:outline-none"
                style={inputStyle}
              />
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                - Negative
              </label>
              <input
                type="number"
                step="0.01"
                min={0}
                value={negativeMarks}
                onChange={(e) => setNegativeMarks(Math.max(0, Number(e.target.value) || 0))}
                className="w-full px-3 py-2 rounded-lg focus:outline-none"
                style={inputStyle}
              />
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                Unattended Penalty
              </label>
              <input
                type="number"
                step="0.01"
                min={0}
                value={unattendedMarks}
                onChange={(e) => setUnattendedMarks(Math.max(0, Number(e.target.value) || 0))}
                className="w-full px-3 py-2 rounded-lg focus:outline-none"
                style={inputStyle}
              />
            </div>
          </div>

          <div
            className="p-2.5 rounded-xl border space-y-1.5"
            style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.border }}
          >
            <div className="text-[0.6875rem] font-bold" style={{ color: colors.inkMuted }}>
              Standard Marking Presets (Quick Fill)
            </div>
            <div className="flex flex-wrap gap-1.5">
              {[
                { label: '1 Pt (No Neg)', pos: 1, neg: 0 },
                { label: '1 Pt (1/4 Neg · -0.25)', pos: 1, neg: 0.25 },
                { label: '1 Pt (1/3 Neg · -0.33)', pos: 1, neg: 0.33 },
                { label: '2 Pts (No Neg)', pos: 2, neg: 0 },
                { label: '2 Pts (1/4 Neg · -0.50)', pos: 2, neg: 0.5 },
                { label: '2 Pts (1/3 Neg · -0.67)', pos: 2, neg: 0.67 },
                { label: '4 Pts (1/4 Neg · -1.0)', pos: 4, neg: 1 },
                { label: '5 Pts (No Neg)', pos: 5, neg: 0 },
              ].map((preset) => {
                const active =
                  Math.abs(positiveMarks - preset.pos) < 0.01 &&
                  Math.abs(negativeMarks - preset.neg) < 0.02;
                return (
                  <button
                    key={preset.label}
                    type="button"
                    onClick={() => {
                      setPositiveMarks(preset.pos);
                      setNegativeMarks(preset.neg);
                      setUnattendedMarks(0);
                    }}
                    className="px-2.5 py-1 rounded-md text-[0.6875rem] font-semibold border transition"
                    style={
                      active
                        ? { backgroundColor: colors.teal, color: colors.onAccentText, borderColor: colors.teal }
                        : { backgroundColor: colors.surface, color: colors.ink, borderColor: colors.border }
                    }
                  >
                    {preset.label}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                MSQ Scoring Policy
              </label>
              <select
                value={msqPolicy}
                onChange={(e) => setMsqPolicy(e.target.value as MsqScoringPolicy)}
                className="w-full px-3 py-2 rounded-lg focus:outline-none"
                style={inputStyle}
              >
                <option value="ALL_OR_NOTHING">All-or-Nothing (Standard Strict)</option>
                <option value="PARTIAL_STRICT">Partial Strict (+Proportional if no wrong choice)</option>
              </select>
            </div>
            <div>
              <label className="block font-bold mb-1" style={{ color: colors.inkMuted }}>
                Paper Category Badge
              </label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value as PaperCategory | '')}
                className="w-full px-3 py-2 rounded-lg focus:outline-none"
                style={inputStyle}
              >
                <option value="">Auto-classified</option>
                <option value="FULL_MOCK">Full Mock Test</option>
                <option value="SUBJECT_TEST">Subject Test</option>
                <option value="TOPIC_DRILL">Topic Drill</option>
                <option value="PYQ">Previous Year Questions (PYQ)</option>
                <option value="REVISION">Revision Set</option>
              </select>
            </div>
          </div>
        </div>

        <div
          className="px-5 py-3 flex justify-end gap-2"
          style={{ borderTop: `1px solid ${colors.border}`, backgroundColor: colors.surfaceVariant }}
        >
          <button type="button" onClick={onClose} className="px-3.5 py-2 text-xs font-semibold" style={{ color: colors.muted }}>
            Cancel
          </button>
          <button
            type="submit"
            className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-bold"
            style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
          >
            <Save className="w-3.5 h-3.5" />
            Save Metadata
          </button>
        </div>
      </form>
    </div>
  );
};

export default EditMetadataModal;
