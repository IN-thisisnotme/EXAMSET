import React, { useState } from 'react';
import { CalendarClock, Pin, Plus, Trash2, X } from 'lucide-react';
import { ExamCountdownTarget } from '../types/exam';
import { useTheme } from '../theme/ThemeProvider';

interface ExamCountdownsModalProps {
  isOpen: boolean;
  countdowns: ExamCountdownTarget[];
  onSaveCountdowns: (next: ExamCountdownTarget[]) => void;
  onClose: () => void;
}

export function getDaysRemaining(targetDateIso: string): number {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(`${targetDateIso}T00:00:00`);
  if (Number.isNaN(target.getTime())) return 0;
  return Math.ceil((target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
}

export const ExamCountdownsModal: React.FC<ExamCountdownsModalProps> = ({
  isOpen,
  countdowns,
  onSaveCountdowns,
  onClose,
}) => {
  const { colors } = useTheme();
  const [title, setTitle] = useState('');
  const [dateIso, setDateIso] = useState(() => {
    const d = new Date(Date.now() + 30 * 86400000);
    return d.toISOString().slice(0, 10);
  });
  const [examType, setExamType] = useState('UPSC Prelims');

  if (!isOpen) return null;

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !dateIso) return;
    const next: ExamCountdownTarget = {
      id: `cd-${Date.now()}`,
      title: title.trim(),
      targetDateIso: dateIso,
      examType,
      isPinned: true,
      color: colors.teal,
    };
    onSaveCountdowns([next, ...countdowns]);
    setTitle('');
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(15, 23, 42, 0.58)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[85vh]"
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
              <CalendarClock className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold" style={{ color: colors.inkBold }}>
                Exam Target Countdowns
              </h3>
              <p className="text-[0.6875rem]" style={{ color: colors.muted }}>
                Pin milestone dates to your Folder & Library dashboard
              </p>
            </div>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg" style={{ color: colors.muted }}>
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-5 overflow-y-auto space-y-4">
          <form onSubmit={handleAdd} className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Exam target (e.g. GATE CS 2027)"
              className="sm:col-span-2 px-3 py-2 rounded-lg focus:outline-none"
              style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
            />
            <input
              type="date"
              required
              value={dateIso}
              onChange={(e) => setDateIso(e.target.value)}
              className="px-3 py-2 rounded-lg focus:outline-none"
              style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
            />
            <select
              value={examType}
              onChange={(e) => setExamType(e.target.value)}
              className="sm:col-span-2 px-3 py-2 rounded-lg focus:outline-none"
              style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}`, color: colors.inkBold }}
            >
              <option value="UPSC Prelims">UPSC CSE</option>
              <option value="SSC CGL">SSC CGL</option>
              <option value="JEE / NEET">JEE / NEET</option>
              <option value="GATE">GATE</option>
              <option value="University Exam">University / Board Exam</option>
            </select>
            <button
              type="submit"
              className="flex items-center justify-center gap-1 px-3 py-2 rounded-lg font-bold"
              style={{ backgroundColor: colors.teal, color: colors.onAccentText }}
            >
              <Plus className="w-3.5 h-3.5" />
              Add Target
            </button>
          </form>

          <div className="space-y-2">
            {countdowns.length === 0 ? (
              <div
                className="p-5 rounded-xl text-center text-xs"
                style={{ backgroundColor: colors.surfaceVariant, color: colors.muted }}
              >
                No exam targets yet. Add one above to track remaining days.
              </div>
            ) : (
              countdowns.map((cd) => {
                const days = getDaysRemaining(cd.targetDateIso);
                return (
                  <div
                    key={cd.id}
                    className="p-3 rounded-xl flex items-center justify-between gap-3"
                    style={{ backgroundColor: colors.surfaceVariant, border: `1px solid ${colors.border}` }}
                  >
                    <div>
                      <div className="text-xs font-bold" style={{ color: colors.inkBold }}>
                        {cd.title}
                      </div>
                      <div className="text-[0.6875rem]" style={{ color: colors.muted }}>
                        {cd.examType} • {cd.targetDateIso}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <span
                        className="px-2.5 py-1 rounded-full text-xs font-black"
                        style={{
                          backgroundColor: days <= 7 ? colors.redLight : colors.tealLight,
                          color: days <= 7 ? colors.red : colors.tealDark,
                        }}
                      >
                        {days > 0 ? `${days}d left` : days === 0 ? 'Today!' : 'Completed'}
                      </span>
                      <button
                        type="button"
                        onClick={() =>
                          onSaveCountdowns(
                            countdowns.map((x) => (x.id === cd.id ? { ...x, isPinned: !x.isPinned } : x))
                          )
                        }
                        title={cd.isPinned ? 'Unpin' : 'Pin to dashboard'}
                        className="p-1.5 rounded-lg border"
                        style={{
                          borderColor: cd.isPinned ? colors.teal : colors.border,
                          backgroundColor: cd.isPinned ? colors.tealLight : colors.surface,
                          color: cd.isPinned ? colors.teal : colors.muted,
                        }}
                      >
                        <Pin className="w-3.5 h-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => onSaveCountdowns(countdowns.filter((x) => x.id !== cd.id))}
                        className="p-1.5 rounded-lg"
                        style={{ color: colors.red }}
                        aria-label="Delete countdown"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ExamCountdownsModal;
